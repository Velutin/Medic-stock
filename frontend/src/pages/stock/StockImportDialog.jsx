import { useEffect, useState } from 'react';
import {
  Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, FormLabel, Radio, RadioGroup, Stack, Typography,
} from '@mui/material';
import FileDropZone from '../../components/FileDropZone';
import HospitalSelect from '../../components/HospitalSelect';
import ImportResultDialog from '../../components/ImportResultDialog';
import { uploadSpreadsheet } from '../../api/download';
import { useNotify } from '../../notifications/NotificationProvider';

/**
 * Stock spreadsheet import that ADDS the quantities to the current balance (incoming material).
 * The initial load (which replaces the balance) is in Cadastros.
 */
export default function StockImportDialog({ open, hospitals, defaultHospitalId, onClose, onImported }) {
  const notify = useNotify();
  const [hospitalId, setHospitalId] = useState('');
  const [location, setLocation] = useState('STOREROOM');
  const [file, setFile] = useState(null);
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState(null);

  useEffect(() => {
    if (open) {
      setHospitalId(defaultHospitalId || hospitals[0]?.id || '');
      setLocation('STOREROOM');
      setFile(null);
    }
  }, [open, defaultHospitalId, hospitals]);

  const center = hospitals.find((h) => h.id === hospitalId)?.type === 'DISTRIBUTION_CENTER';

  const submit = async () => {
    if (!hospitalId || !file) return;
    setSending(true);
    try {
      const res = await uploadSpreadsheet(`/imports/hospital/${hospitalId}/stock`, file,
        { mode: 'ADD', defaultLocation: center ? 'STOREROOM' : location });
      setResult(res);
      if (res.imported > 0) onImported();
    } catch (err) {
      notify.error(err);
    } finally {
      setSending(false);
    }
  };

  return (
    <>
      <Dialog open={open && !result} onClose={onClose} fullWidth maxWidth="sm">
        <DialogTitle sx={{ typography: 'h2' }}>Importar planilha de entrada</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5}>
            <Typography variant="body2" color="text.secondary">
              As quantidades da planilha são somadas ao saldo atual. Colunas: REF, LOTE, VALIDADE e QUANTIDADE; LOCAL é opcional.
            </Typography>
            <HospitalSelect hospitals={hospitals} value={hospitalId} onChange={setHospitalId} label="Direcionado a" />
            {!center && (
              <div>
                <FormLabel sx={{ fontSize: 13, fontWeight: 500 }}>Sem a coluna LOCAL, os itens ficam</FormLabel>
                <RadioGroup row value={location} onChange={(e) => setLocation(e.target.value)}>
                  <FormControlLabel value="STOREROOM" control={<Radio />} label="Na sala" />
                  <FormControlLabel value="HOSPITAL" control={<Radio />} label="Dentro do hospital" />
                </RadioGroup>
              </div>
            )}
            <FileDropZone title="Arraste a planilha aqui" hint=".xlsx ou .xls" file={file} onFile={setFile} />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button variant="outlined" onClick={onClose}>Cancelar</Button>
          <Button variant="contained" onClick={submit} disabled={!file || !hospitalId || sending}>Importar</Button>
        </DialogActions>
      </Dialog>
      <ImportResultDialog result={result} onClose={() => { setResult(null); onClose(); }} />
    </>
  );
}
