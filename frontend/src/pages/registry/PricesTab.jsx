import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Box, Button, Link, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { api } from '../../api/client';
import { uploadSpreadsheet } from '../../api/download';
import { useNotify } from '../../notifications/NotificationProvider';
import HospitalSelect from '../../components/HospitalSelect';
import FileDropZone from '../../components/FileDropZone';
import ImportResultDialog from '../../components/ImportResultDialog';
import { formatDateTime, formatMoney, priceTableLabel } from '../../utils/format';
import { tokens } from '../../theme';

/** Hospital price tables: complete replacement, partial update and the current values. */
export default function PricesTab({ hospitals }) {
  const notify = useNotify();
  const [hospitalId, setHospitalId] = useState('');
  const [prices, setPrices] = useState(null);
  const [term, setTerm] = useState('');
  const [files, setFiles] = useState({ REPLACE: null, UPDATE: null });
  const [sending, setSending] = useState(null);
  const [result, setResult] = useState(null);

  useEffect(() => {
    if (!hospitalId && hospitals.length) setHospitalId(hospitals[0].id);
  }, [hospitals, hospitalId]);

  const hospital = hospitals.find((h) => h.id === hospitalId);

  const load = useCallback(async () => {
    if (!hospitalId) return;
    setPrices(null);
    try {
      setPrices(await api(`/hospitals/${hospitalId}/prices`));
    } catch (err) {
      notify.error(err);
      setPrices([]);
    }
  }, [hospitalId, notify]);

  useEffect(() => { load(); }, [load]);

  const send = async (mode) => {
    setSending(mode);
    try {
      const res = await uploadSpreadsheet(`/imports/hospital/${hospitalId}/prices`, files[mode], { mode });
      setResult(res);
      setFiles((f) => ({ ...f, [mode]: null }));
      load();
    } catch (err) {
      notify.error(err);
    } finally {
      setSending(null);
    }
  };

  const filtered = useMemo(() => {
    const t = term.trim().toLowerCase();
    return (prices || []).filter((p) => !t || p.ref.toLowerCase().includes(t) || (p.description || '').toLowerCase().includes(t));
  }, [prices, term]);

  const uploadCard = (mode, title, text, dropTitle) => (
    <Paper variant="outlined" sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Box>
        <Typography variant="h3">{title}</Typography>
        <Typography variant="body2" color="text.secondary">{text}</Typography>
      </Box>
      <FileDropZone title={dropTitle} hint="Coluna A: REF · Coluna B: valor" file={files[mode]}
        onFile={(f) => setFiles((s) => ({ ...s, [mode]: f }))} />
      <Button variant={mode === 'REPLACE' ? 'contained' : 'outlined'} disabled={!files[mode] || Boolean(sending)} onClick={() => send(mode)}>
        {mode === 'REPLACE' ? 'Substituir tabela' : 'Atualizar valores'}
      </Button>
    </Paper>
  );

  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 } }}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 2, mb: 2 }}>
          <HospitalSelect hospitals={hospitals} value={hospitalId} onChange={setHospitalId} size="small" />
          {hospital && (
            <Typography variant="body2" color="text.secondary">Tabela de preços deste hospital: {priceTableLabel(hospital.priceTableType)}</Typography>
          )}
          <Link href="/modelos/precos.xlsx" download variant="body2" sx={{ ml: { sm: 'auto' } }}>Baixar planilha modelo</Link>
        </Box>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
          {uploadCard('REPLACE', 'Importar tabela completa',
            'Substitui todos os valores do hospital. REFs que não estiverem na planilha ficam sem valor. Se alguma linha tiver erro, nada é alterado.',
            'Arraste a planilha completa aqui')}
          {uploadCard('UPDATE', 'Atualizar valores', 'Envie só os itens que mudaram de preço ou são novos. Os demais continuam como estão.',
            'Arraste a lista de atualização aqui')}
        </Box>
      </Paper>

      <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 } }}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', gap: 2, mb: 2 }}>
          <Typography variant="h2" component="h2">Valores atuais{hospital ? ` · ${hospital.name}` : ''}</Typography>
          <TextField type="search" size="small" label="Buscar" placeholder="REF ou descrição" value={term}
            onChange={(e) => setTerm(e.target.value)} sx={{ width: { xs: '100%', sm: 300 } }} />
        </Box>
        {prices === null ? (
          <Stack spacing={1}>{[1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={40} />)}</Stack>
        ) : filtered.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
            {term ? 'Nenhum valor encontrado para esta busca.' : 'Nenhum valor cadastrado para este hospital.'}
          </Typography>
        ) : (
          <TableContainer sx={{ maxHeight: 520 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow><TableCell>REF</TableCell><TableCell>Descrição</TableCell><TableCell align="right">Valor</TableCell>
                  <TableCell>Origem</TableCell><TableCell>Atualizado em</TableCell></TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((p) => (
                  <TableRow key={p.materialId} hover>
                    <TableCell sx={{ fontFamily: tokens.mono, fontSize: 13 }}>{p.ref}</TableCell>
                    <TableCell>{p.description}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontWeight: 500 }}>{formatMoney(p.value)}</TableCell>
                    <TableCell>{p.sourceHospitalId === hospitalId ? 'Própria' : p.sourceHospital}</TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(p.updatedAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>
      <ImportResultDialog result={result} onClose={() => setResult(null)} />
    </Stack>
  );
}
