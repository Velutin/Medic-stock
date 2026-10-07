import { useEffect, useState } from 'react';
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Link, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import Close from '@mui/icons-material/Close';
import { api } from '../../api/client';
import { translateMessage } from '../../api/messages';
import { useNotify } from '../../notifications/NotificationProvider';
import FileDropZone from '../../components/FileDropZone';
import StatusChip from '../../components/StatusChip';
import { tokens } from '../../theme';

const mono = { fontFamily: tokens.mono, fontSize: 13 };

/** Reads a minimums spreadsheet for review (nothing is saved until "Aplicar"). */
export default function MinimumsImportDialog({ open, hospital, saving, onClose, onApply }) {
  const notify = useNotify();
  const [file, setFile] = useState(null);
  const [rows, setRows] = useState(null);
  const [reading, setReading] = useState(false);

  useEffect(() => { if (open) { setFile(null); setRows(null); } }, [open]);

  const read = async () => {
    const form = new FormData();
    form.append('file', file);
    setReading(true);
    try {
      setRows(await api(`/hospitals/${hospital.id}/minimums/preview`, { method: 'POST', body: form }));
    } catch (err) {
      notify.error(err);
    } finally {
      setReading(false);
    }
  };

  const valid = (rows || []).filter((r) => !r.error);
  const changes = valid.filter((r) => r.hospitalIdeal !== r.currentHospitalIdeal || r.idealTotal !== r.currentIdealTotal);
  const errors = (rows || []).length - valid.length;

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth={rows ? 'md' : 'sm'}>
      <DialogTitle sx={{ typography: 'h2' }}>Importar mínimos · {hospital.name}</DialogTitle>
      <DialogContent dividers>
        {rows === null ? (
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              Colunas: REF, IDEAL e IDEAL TOTAL. Nada é gravado agora: você confere as mudanças antes de aplicar.
              REFs fora da planilha continuam como estão; ideal e ideal total 0 retiram a REF da lista.
            </Typography>
            <Link href="/modelos/minimos.xlsx" download variant="body2">Baixar planilha modelo</Link>
            <FileDropZone title="Arraste a planilha de mínimos aqui" hint=".xlsx ou .xls" file={file} onFile={setFile} />
          </Stack>
        ) : (
          <Stack spacing={2}>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              <StatusChip tone="success">{changes.length} com mudança</StatusChip>
              <StatusChip tone="neutral">{valid.length - changes.length} sem mudança</StatusChip>
              {errors > 0 && <StatusChip tone="error">{errors} com erro (não serão aplicadas)</StatusChip>}
            </Box>
            <TableContainer sx={{ maxHeight: 440 }}>
              <Table size="small" stickyHeader sx={{ minWidth: 680 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>Linha</TableCell><TableCell>REF</TableCell><TableCell>Material</TableCell>
                    <TableCell align="right">Atual</TableCell><TableCell align="right">Novo</TableCell><TableCell>Situação</TableCell><TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((r) => {
                    const same = !r.error && r.hospitalIdeal === r.currentHospitalIdeal && r.idealTotal === r.currentIdealTotal;
                    return (
                      <TableRow key={r.row} hover>
                        <TableCell>{r.row}</TableCell>
                        <TableCell sx={mono}>{r.ref}</TableCell>
                        <TableCell sx={{ maxWidth: 240 }}>{r.description}</TableCell>
                        <TableCell align="right" sx={mono}>{r.currentHospitalIdeal} / {r.currentIdealTotal}</TableCell>
                        <TableCell align="right" sx={{ ...mono, fontWeight: 700 }}>{r.error ? '—' : `${r.hospitalIdeal} / ${r.idealTotal}`}</TableCell>
                        <TableCell>
                          {r.error ? <Typography variant="body2" color="error.main">{translateMessage(r.error)}</Typography>
                            : <StatusChip tone={same ? 'neutral' : 'success'}>{same ? 'Sem mudança' : 'Será aplicada'}</StatusChip>}
                        </TableCell>
                        <TableCell align="right">
                          <IconButton size="small" aria-label={`Remover linha ${r.row}`}
                            onClick={() => setRows((list) => list.filter((x) => x.row !== r.row))}>
                            <Close fontSize="small" />
                          </IconButton>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
            <Typography variant="caption" color="text.secondary">Valores no formato ideal / ideal total.</Typography>
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button variant="outlined" onClick={onClose}>Cancelar</Button>
        {rows === null ? (
          <Button variant="contained" onClick={read} disabled={!file || reading}>Ler planilha</Button>
        ) : (
          <Button variant="contained" disabled={saving || changes.length === 0}
            onClick={() => onApply(changes.map((r) => ({ materialId: r.materialId, hospitalIdeal: r.hospitalIdeal, idealTotal: r.idealTotal })))}>
            Aplicar {changes.length} {changes.length === 1 ? 'mudança' : 'mudanças'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
