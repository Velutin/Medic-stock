import { useEffect, useState } from 'react';
import { Alert, Box, Button, FormControlLabel, FormLabel, Link, Paper, Radio, RadioGroup, Stack, Typography } from '@mui/material';
import { uploadSpreadsheet } from '../../api/download';
import { useNotify } from '../../notifications/NotificationProvider';
import HospitalSelect from '../../components/HospitalSelect';
import FileDropZone from '../../components/FileDropZone';
import ImportResultDialog from '../../components/ImportResultDialog';
import ConfirmDialog from '../../components/ConfirmDialog';

/**
 * Initial stock load: the spreadsheet REPLACES the balance of each lot in the chosen place
 * (used once per place when the system starts being used, or for a full inventory).
 */
export default function InitialStockTab({ hospitals }) {
  const notify = useNotify();
  const [hospitalId, setHospitalId] = useState('');
  const [location, setLocation] = useState('STOREROOM');
  const [file, setFile] = useState(null);
  const [confirm, setConfirm] = useState(false);
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState(null);

  useEffect(() => {
    if (!hospitalId && hospitals.length) setHospitalId(hospitals[0].id);
  }, [hospitals, hospitalId]);

  const hospital = hospitals.find((h) => h.id === hospitalId);
  const center = hospital?.type === 'DISTRIBUTION_CENTER';
  const place = center || location === 'STOREROOM' ? 'na sala' : 'dentro do hospital';

  const submit = async () => {
    setSending(true);
    try {
      const res = await uploadSpreadsheet(`/imports/hospital/${hospitalId}/stock`, file,
        { mode: 'REPLACE', defaultLocation: center ? 'STOREROOM' : location });
      setResult(res);
      setFile(null);
      setConfirm(false);
    } catch (err) {
      notify.error(err);
    } finally {
      setSending(false);
    }
  };

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 }, maxWidth: 760 }}>
      <Typography variant="h2" component="h2">Carregar estoque inicial</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 2 }}>
        Use uma vez por local, ao começar a usar o sistema. O saldo de cada lote passa a ser o da planilha.
      </Typography>
      <Stack spacing={2.5}>
        <HospitalSelect hospitals={hospitals} value={hospitalId} onChange={setHospitalId} label="Hospital" sx={{ maxWidth: 360 }} />
        {center ? (
          <Alert severity="info">{hospital.name} é um centro de distribuição: os itens ficam na sala.</Alert>
        ) : (
          <div>
            <FormLabel sx={{ fontSize: 13, fontWeight: 500 }}>Os itens da planilha estão</FormLabel>
            <RadioGroup row value={location} onChange={(e) => setLocation(e.target.value)}>
              <FormControlLabel value="STOREROOM" control={<Radio />} label="Na sala" />
              <FormControlLabel value="HOSPITAL" control={<Radio />} label="Dentro do hospital" />
            </RadioGroup>
          </div>
        )}
        <FileDropZone title="Arraste a planilha de estoque aqui"
          hint="Colunas: REF · LOTE · VALIDADE · QUANTIDADE. A validade é obrigatória." file={file} onFile={setFile} />
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
          <Link href="/modelos/estoque-inicial.xlsx" download variant="body2">Baixar planilha modelo</Link>
          <Button variant="contained" disabled={!file || !hospitalId} onClick={() => setConfirm(true)}>Importar estoque inicial</Button>
        </Box>
      </Stack>
      <ConfirmDialog open={confirm} busy={sending} title="Importar estoque inicial"
        message={`Os lotes da planilha terão o saldo ${place} de ${hospital?.name || ''} substituído pela quantidade informada. Deseja continuar?`}
        confirmLabel="Importar" onConfirm={submit} onClose={() => setConfirm(false)} />
      <ImportResultDialog result={result} onClose={() => setResult(null)} />
    </Paper>
  );
}
