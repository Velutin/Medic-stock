import { useEffect, useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert, Box, Button, Checkbox, Link, MenuItem, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TablePagination, TableRow, TextField, Typography,
} from '@mui/material';
import AutorenewOutlined from '@mui/icons-material/AutorenewOutlined';
import PictureAsPdfOutlined from '@mui/icons-material/PictureAsPdfOutlined';
import PageHeader from '../../components/PageHeader';
import ColorSwatch from '../../components/ColorSwatch';
import { useAuth } from '../../auth/AuthProvider';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';

const mono = { fontFamily: tokens.mono, fontSize: 13 };
const PAGE_SIZE = 25;
const materialName = (r) => (r.component ? `${r.component}${r.size ? ` · ${r.size}` : ''}` : r.description);

function SummaryRow({ label, children }) {
  return (
    <>
      <Typography component="dt" variant="body2" color="text.secondary">{label}</Typography>
      <Typography component="dd" variant="body2" sx={{ m: 0, fontWeight: 500 }}>{children}</Typography>
    </>
  );
}

/** Computer version: storeroom lots of the destination with the quantity to send, and the delivery summary. */
export default function TransferDesktop({ transfer }) {
  const { user } = useAuth();
  const [term, setTerm] = useState('');
  const [page, setPage] = useState(0);
  const { hospital, source, rows, selection } = transfer;

  useEffect(() => { setPage(0); }, [term, transfer.hospitalId]);

  const filtered = useMemo(() => {
    const t = term.trim().toUpperCase();
    if (!t) return rows;
    return rows.filter((r) => [r.ref, r.lot, r.description, r.component].some((v) => v && v.toUpperCase().includes(t)));
  }, [rows, term]);
  const visible = filtered.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);

  return (
    <>
      <PageHeader title="Transferência para o hospital" subtitle="Envio de itens da sala ao hospital de destino"
        actions={<Link component={RouterLink} to="/reposicao" variant="body2" sx={{ fontWeight: 500 }}>Ver sugestão de reposição</Link>} />

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start', mb: 2.5 }}>
        <TextField select label="Hospital de destino" value={transfer.hospitalId} sx={{ minWidth: 260 }}
          onChange={(e) => transfer.changeHospital(Number(e.target.value))} disabled={transfer.loadingHospitals}>
          {transfer.destinations.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
        </TextField>
        <TextField type="search" label="Buscar na sala" placeholder="REF, material ou lote" value={term}
          onChange={(e) => setTerm(e.target.value)} sx={{ flex: '1 1 260px' }} disabled={!hospital} />
        <Button variant="outlined" startIcon={<AutorenewOutlined />} onClick={transfer.fillSuggestion}
          disabled={!hospital || rows.length === 0} sx={{ minHeight: 56 }}>
          Preencher com a reposição sugerida
        </Button>
      </Box>

      {transfer.lastDelivery && (
        <Alert severity="success" sx={{ mb: 2.5 }} action={(
          <Button color="inherit" size="small" startIcon={<PictureAsPdfOutlined />}
            onClick={() => transfer.downloadPdf(transfer.lastDelivery)}>
            Baixar PDF novamente
          </Button>
        )}>
          Transferência #{transfer.lastDelivery.id} registrada para {transfer.lastDelivery.hospital}.
        </Alert>
      )}

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2.5, alignItems: 'flex-start' }}>
        <Paper variant="outlined" component="section" sx={{ p: 3, flex: '2 1 620px', minWidth: 0 }}>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'baseline', gap: 1.5, mb: 2 }}>
            <Typography variant="h3" component="h2">Itens na sala para este hospital</Typography>
            <Typography variant="caption" color="text.secondary">Lotes vencidos não aparecem</Typography>
          </Box>
          {hospital && source && source.id !== hospital.id && (
            <Alert severity="info" sx={{ mb: 2 }}>
              {hospital.name} é atendido por {source.name}: os itens saem da sala de {source.name}.
            </Alert>
          )}

          {!hospital ? (
            <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
              Escolha o hospital de destino para ver os itens da sala.
            </Typography>
          ) : transfer.loadingRows ? (
            <Stack spacing={1}>{[1, 2, 3, 4].map((i) => <Skeleton key={i} variant="rounded" height={44} />)}</Stack>
          ) : filtered.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
              {term ? 'Nenhum item da sala corresponde à busca.' : 'Não há itens na sala para este hospital.'}
            </Typography>
          ) : (
            <>
              <TableContainer>
                <Table size="small" sx={{ minWidth: 760 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell padding="checkbox">Enviar</TableCell><TableCell>REF</TableCell><TableCell>Material</TableCell>
                      <TableCell>Lote</TableCell><TableCell>Validade</TableCell><TableCell align="right">Na sala</TableCell>
                      <TableCell align="right">Enviar</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {visible.map((r) => {
                      const qty = selection[r.lotId] || 0;
                      return (
                        <TableRow key={r.lotId} hover selected={qty > 0}>
                          <TableCell padding="checkbox">
                            <Checkbox checked={qty > 0} inputProps={{ 'aria-label': `Enviar ${materialName(r)}, lote ${r.lot}` }}
                              onChange={(e) => transfer.setQuantity(r.lotId, e.target.checked ? 1 : 0)} />
                          </TableCell>
                          <TableCell sx={mono}>{r.ref}</TableCell>
                          <TableCell>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                              <ColorSwatch color={r.color} />{materialName(r)}
                            </Box>
                          </TableCell>
                          <TableCell sx={mono}>{r.lot}</TableCell>
                          <TableCell sx={mono}>{formatDate(r.expiryDate)}</TableCell>
                          <TableCell align="right">{r.storeroomQuantity}</TableCell>
                          <TableCell align="right" sx={{ py: 0.5 }}>
                            <TextField type="number" size="small" value={qty} sx={{ width: 84 }}
                              onChange={(e) => transfer.setQuantity(r.lotId, e.target.value)}
                              inputProps={{ min: 0, max: r.storeroomQuantity, step: 1, style: { textAlign: 'right' },
                                'aria-label': `Quantidade a enviar de ${materialName(r)}, lote ${r.lot}` }} />
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
              {filtered.length > PAGE_SIZE && (
                <TablePagination component="div" count={filtered.length} page={page} rowsPerPage={PAGE_SIZE}
                  onPageChange={(_, p) => setPage(p)} rowsPerPageOptions={[PAGE_SIZE]}
                  labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
              )}
            </>
          )}
        </Paper>

        <Paper variant="outlined" component="section" sx={{ p: 3, flex: '1 1 320px', minWidth: 0, position: 'sticky', top: 24 }}>
          <Stack spacing={2}>
            <Typography variant="h3" component="h2">Resumo da entrega</Typography>
            <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: '12px 16px' }}>
              <SummaryRow label="Destino">{hospital?.name || '—'}</SummaryRow>
              {source && hospital && source.id !== hospital.id && <SummaryRow label="Sai da sala de">{source.name}</SummaryRow>}
              <SummaryRow label="Lotes">{transfer.totals.lots}</SummaryRow>
              <SummaryRow label="Unidades">{transfer.totals.units}</SummaryRow>
              <SummaryRow label="Responsável">{user?.name}</SummaryRow>
            </Box>
            <Box sx={{ p: 1.5, borderRadius: '6px', backgroundColor: tokens.background, fontSize: 13, color: tokens.textMuted }}>
              Gera o relatório de entrega no modelo Clássico, com a logo no topo e campos de assinatura.
            </Box>
            <Button variant="contained" size="large" onClick={transfer.submit} sx={{ minHeight: 48 }}
              disabled={transfer.sending || transfer.totals.units === 0}>
              Transferir e gerar PDF
            </Button>
          </Stack>
        </Paper>
      </Box>
    </>
  );
}
