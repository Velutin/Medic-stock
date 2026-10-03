import { useEffect, useState } from 'react';
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Link, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import Close from '@mui/icons-material/Close';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import FileDropZone from '../../components/FileDropZone';
import StatusChip from '../../components/StatusChip';
import { ItemDialog } from '../registry/ItemsTab';
import { translateMessage } from '../../api/messages';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';

const mono = { fontFamily: tokens.mono, fontSize: 13 };

/**
 * Reads an entry spreadsheet for review (nothing is saved): rows with problems are shown with the reason,
 * REFs not in the catalog can be registered here, and only the valid rows go to the entry list.
 */
export default function EntryImportDialog({ open, onClose, onAdd }) {
  const notify = useNotify();
  const [file, setFile] = useState(null);
  const [rows, setRows] = useState(null);
  const [reading, setReading] = useState(false);
  const [register, setRegister] = useState(null);

  useEffect(() => {
    if (open) {
      setFile(null);
      setRows(null);
    }
  }, [open]);

  const readFile = async () => {
    const form = new FormData();
    form.append('file', file);
    setReading(true);
    try {
      const result = await api('/stock-entries/preview', { method: 'POST', body: form });
      setRows(result.map((r) => ({ ...r, key: r.row, material: r.materialId ? { id: r.materialId } : null })));
    } catch (err) {
      notify.error(err);
    } finally {
      setReading(false);
    }
  };

  /** Registered REF: every row with it becomes usable. */
  const registered = (material) => {
    setRows((list) => list.map((r) => (r.ref === material.ref ? { ...r, material, description: material.description } : r)));
    setRegister(null);
  };

  const valid = (rows || []).filter((r) => !r.error && r.material);
  const pending = (rows || []).filter((r) => !r.error && !r.material);
  const invalid = (rows || []).filter((r) => r.error);

  /** Lines in the format of the entry list. Rows read from the API only carry the material id. */
  const confirm = () => {
    onAdd(valid.map((r) => ({
      material: { ref: r.ref, description: r.description, ...r.material },
      lot: r.lot, expiryDate: r.expiryDate, quantity: r.quantity,
    })));
  };

  return (
    <>
      <Dialog open={open && !register} onClose={onClose} fullWidth maxWidth={rows ? 'lg' : 'sm'}>
        <DialogTitle sx={{ typography: 'h2' }}>Importar planilha de entrada</DialogTitle>
        <DialogContent dividers>
          {rows === null ? (
            <Stack spacing={2}>
              <Typography variant="body2" color="text.secondary">
                Colunas: REF, LOTE, VALIDADE e QUANTIDADE. Nada é gravado agora: você confere as linhas antes de incluí-las
                na entrada.
              </Typography>
              <Link href="/modelos/estoque-inicial.xlsx" download variant="body2">Baixar planilha modelo</Link>
              <FileDropZone title="Arraste a planilha da entrada aqui" hint=".xlsx ou .xls" file={file} onFile={setFile} />
            </Stack>
          ) : (
            <Stack spacing={2}>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                <StatusChip tone="success">{valid.length} prontas</StatusChip>
                {pending.length > 0 && <StatusChip tone="warning">{pending.length} com REF não cadastrada</StatusChip>}
                {invalid.length > 0 && <StatusChip tone="error">{invalid.length} com erro (não serão incluídas)</StatusChip>}
              </Box>
              {pending.length > 0 && (
                <Alert severity="warning">Cadastre as REFs novas para incluir essas linhas, ou remova-as.</Alert>
              )}
              <TableContainer sx={{ maxHeight: 460 }}>
                <Table size="small" stickyHeader sx={{ minWidth: 820 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Linha</TableCell><TableCell>REF</TableCell><TableCell>Material</TableCell>
                      <TableCell>Lote</TableCell><TableCell>Validade</TableCell><TableCell align="right">Qtd.</TableCell>
                      <TableCell>Situação</TableCell><TableCell />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.map((r) => (
                      <TableRow key={r.key} hover>
                        <TableCell>{r.row}</TableCell>
                        <TableCell sx={mono}>{r.ref}</TableCell>
                        <TableCell sx={{ maxWidth: 260 }}>{r.description}</TableCell>
                        <TableCell sx={mono}>{r.lot}</TableCell>
                        <TableCell sx={mono}>{formatDate(r.expiryDate)}</TableCell>
                        <TableCell align="right">{r.quantity}</TableCell>
                        <TableCell>
                          {r.error ? (
                            <Typography variant="body2" color="error.main">{translateMessage(r.error)}</Typography>
                          ) : r.material ? (
                            <StatusChip tone="success">Pronta</StatusChip>
                          ) : (
                            <Button size="small" variant="outlined" onClick={() => setRegister(r)}>Cadastrar REF</Button>
                          )}
                        </TableCell>
                        <TableCell align="right">
                          <IconButton size="small" aria-label={`Remover linha ${r.row}`}
                            onClick={() => setRows((list) => list.filter((x) => x.key !== r.key))}>
                            <Close fontSize="small" />
                          </IconButton>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Stack>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button variant="outlined" onClick={onClose}>Cancelar</Button>
          {rows === null ? (
            <Button variant="contained" onClick={readFile} disabled={!file || reading}>Ler planilha</Button>
          ) : (
            <Button variant="contained" onClick={confirm} disabled={valid.length === 0}>
              Incluir {valid.length} {valid.length === 1 ? 'linha' : 'linhas'} na entrada
            </Button>
          )}
        </DialogActions>
      </Dialog>
      <ItemDialog open={Boolean(register)} item={null}
        initial={register ? { ref: register.ref, description: register.description || '', gtin: register.gtin || '' } : undefined}
        onClose={() => setRegister(null)} onSaved={registered} />
    </>
  );
}
