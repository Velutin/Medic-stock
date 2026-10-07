import { useRef, useState } from 'react';
import {
  Alert, Box, Button, IconButton, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead,
  TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import EditOutlined from '@mui/icons-material/EditOutlined';
import UploadFileOutlined from '@mui/icons-material/UploadFileOutlined';
import PageHeader from '../../components/PageHeader';
import { ItemDialog } from '../registry/ItemsTab';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';
import { todayIso } from './useEntryDraft';
import useItemReader from './useItemReader';
import MaterialLabel from './MaterialLabel';
import EntryHistory from './EntryHistory';
import EntryImportDialog from './EntryImportDialog';
import ExpiryField from './ExpiryField';
import LotRefChangeDialog from './LotRefChangeDialog';

const mono = { fontFamily: tokens.mono, fontSize: 13 };
const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

/** Computer version: destination and date on the left, items on the right, latest entries below. */
export default function EntryDesktop({ entry }) {
  const notify = useNotify();
  const reader = useItemReader();
  const codeInput = useRef(null);
  const quantityInput = useRef(null);
  const lotInput = useRef(null);
  const expiryInput = useRef(null);
  const [newItemOpen, setNewItemOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const { draft, setField, totals, editing } = entry;
  const { item, errors } = reader;

  const readCode = async () => {
    const result = await reader.read(item.code);
    if (!result) return;
    // GTIN barcode: the lot barcode comes next (the USB reader types it in the Lote field)
    if (result.gtin && !result.lot) lotInput.current?.focus();
    else if (result.material && !result.expiryDate) expiryInput.current?.focus();
    else if (result.material) quantityInput.current?.focus();
  };

  /**
   * Adds the lines after the one-REF-per-lot check. A lot registered with another REF opens the REF change dialog;
   * the item typed stays in the form until the dialog is answered (cancel: fix the REF and add again).
   */
  const addLines = async (lines) => {
    try {
      const result = await entry.requestAdd(lines);
      if (result.problem) notify.warning(result.problem);
      return result;
    } catch (err) {
      notify.error(err);
      return { problem: true };
    }
  };

  const add = async () => {
    if (!reader.validate()) return;
    const result = await addLines([item]);
    if (result.problem || result.waiting) return;
    reader.clear();
    codeInput.current?.focus();
  };

  const confirmRefChange = () => {
    const added = entry.confirmRefChange();
    if (added) {
      notify.success(added === 1 ? 'REF dos lotes trocada: item adicionado à entrada.' : `REF dos lotes trocada: ${added} itens adicionados à entrada.`);
      reader.clear();
      codeInput.current?.focus();
    } else {
      notify.warning('Nenhum item foi adicionado: o lote já saiu em cirurgia com a REF atual. Confira a REF do item.');
    }
  };

  const fix = (line) => {
    entry.removeItem(line.key);
    reader.load(line);
  };

  const submit = async () => {
    const problem = entry.headerError();
    if (problem) {
      notify.warning(problem);
      return;
    }
    try {
      const saved = await entry.save();
      notify.success(editing
        ? `Entrada #${saved.id} corrigida.`
        : `Entrada registrada na sala: ${plural(saved.lots, 'lote', 'lotes')}, ${plural(saved.units, 'unidade', 'unidades')}.`);
      reader.clear();
    } catch (err) {
      notify.error(err);
    }
  };

  const startEdit = (recorded) => {
    entry.startEdit(recorded);
    reader.clear();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <>
      <PageHeader title={editing ? 'Corrigir entrada' : 'Nova entrada'} subtitle="Recebimento de material da empresa na sala"
        actions={(
          <Button variant="outlined" startIcon={<UploadFileOutlined />} onClick={() => setImportOpen(true)}>
            Importar planilha
          </Button>
        )} />

      {editing && (
        <Alert severity="info" sx={{ mb: 2.5 }}
          action={<Button color="inherit" size="small" onClick={entry.reset}>Cancelar correção</Button>}>
          Corrigindo a entrada {editing.label}. Ao salvar, só as diferenças são aplicadas ao estoque da sala.
        </Alert>
      )}

      <Box sx={{ display: 'grid', gap: 2.5, alignItems: 'start', gridTemplateColumns: { md: 'minmax(300px, 1fr) minmax(0, 2fr)' } }}>
        <Paper variant="outlined" component="section" sx={{ p: 3 }}>
          <Stack spacing={2}>
            <Typography variant="h3" component="h2">Destino</Typography>
            <TextField select label="Material direcionado a" value={draft.hospitalId}
              onChange={(e) => setField('hospitalId', Number(e.target.value))} disabled={entry.loadingHospitals}>
              {entry.destinations.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
            </TextField>
            <TextField type="date" label="Data de recebimento" value={draft.entryDate}
              onChange={(e) => setField('entryDate', e.target.value)} InputLabelProps={{ shrink: true }}
              inputProps={{ max: todayIso() }} />
            <TextField label="Observação" value={draft.notes} onChange={(e) => setField('notes', e.target.value)}
              multiline minRows={3} inputProps={{ maxLength: 1000 }} />
            <Box sx={{ p: 1.5, borderRadius: '6px', backgroundColor: tokens.background, fontSize: 13, color: tokens.textMuted }}>
              Os itens entram na sala e ficam reservados ao destino escolhido.
            </Box>
          </Stack>
        </Paper>

        <Paper variant="outlined" component="section" sx={{ p: 3, minWidth: 0 }}>
          <Stack spacing={2}>
            <Typography variant="h3" component="h2">Adicionar item</Typography>
            <Box component="form" noValidate onSubmit={(e) => { e.preventDefault(); add(); }}
              sx={{ display: 'grid', gap: 1.5, alignItems: 'start',
                gridTemplateColumns: { xs: '1fr', lg: 'minmax(200px, 2fr) minmax(120px, 1fr) 160px 90px auto' } }}>
              <TextField label="Código (QR, código de barras ou REF)" placeholder="Leia ou digite e tecle Enter"
                value={item.code} inputRef={codeInput} autoFocus autoComplete="off"
                onChange={(e) => reader.setCode(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); readCode(); } }}
                onBlur={() => { if (item.code.trim() && !item.material && !reader.unknown) readCode(); }}
                error={Boolean(errors.code)} helperText={errors.code} disabled={reader.reading} />
              <TextField label="Lote" value={item.lot} onChange={(e) => reader.set('lot', e.target.value.toUpperCase())}
                inputRef={lotInput} autoComplete="off"
                onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); expiryInput.current?.focus(); } }}
                error={Boolean(errors.lot)} helperText={errors.lot} />
              <ExpiryField label="Validade (mês/ano)" value={item.expiryDate} required inputRef={expiryInput}
                onChange={reader.setExpiry}
                onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); quantityInput.current?.focus(); } }}
                error={Boolean(errors.expiryDate)} helperText={errors.expiryDate} />
              <TextField type="number" label="Qtd." value={item.quantity} inputRef={quantityInput}
                onChange={(e) => reader.set('quantity', e.target.value)} inputProps={{ min: 1, step: 1 }}
                error={Boolean(errors.quantity)} helperText={errors.quantity} />
              <Button type="submit" variant="contained" startIcon={<AddIcon />} sx={{ minHeight: 56 }}>Adicionar</Button>
            </Box>

            {item.material && <MaterialLabel material={item.material} />}
            {reader.unknown && (
              <Alert severity="warning" action={(
                <Button color="inherit" size="small" onClick={() => setNewItemOpen(true)}>Cadastrar item</Button>
              )}>
                Código não encontrado no catálogo{reader.unknown.ref ? ` (REF ${reader.unknown.ref})` : ''}
                {reader.unknown.gtin ? ` · GTIN ${reader.unknown.gtin}` : ''}. Cadastre a nova REF para continuar.
              </Alert>
            )}

            {draft.items.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                Nenhum item adicionado. Leia o código, informe lote, validade e quantidade, ou importe uma planilha.
              </Typography>
            ) : (
              <TableContainer>
                <Table size="small" sx={{ minWidth: 640 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>REF</TableCell><TableCell>Material</TableCell><TableCell>Lote</TableCell>
                      <TableCell>Validade</TableCell><TableCell align="right">Qtd.</TableCell><TableCell />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {draft.items.map((line) => (
                      <TableRow key={line.key} hover>
                        <TableCell sx={mono}>{line.ref}</TableCell>
                        <TableCell>{line.component ? `${line.component}${line.size ? ` · ${line.size}` : ''}` : line.description}</TableCell>
                        <TableCell sx={mono}>{line.lot}</TableCell>
                        <TableCell sx={mono}>{formatDate(line.expiryDate)}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>{line.quantity}</TableCell>
                        <TableCell align="right" sx={{ whiteSpace: 'nowrap', py: 0.5 }}>
                          <Tooltip title="Corrigir este item">
                            <IconButton aria-label={`Corrigir ${line.ref} lote ${line.lot}`} onClick={() => fix(line)}>
                              <EditOutlined fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Remover">
                            <IconButton aria-label={`Remover ${line.ref} lote ${line.lot}`} onClick={() => entry.removeItem(line.key)}
                              sx={{ color: tokens.error }}>
                              <DeleteOutline fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}

            <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 1.5,
              pt: 1.5, borderTop: `1px solid ${tokens.divider}` }}>
              <Typography variant="body2" color="text.secondary">
                {plural(totals.lots, 'lote', 'lotes')} · {plural(totals.units, 'unidade', 'unidades')}
              </Typography>
              <Box sx={{ display: 'flex', gap: 1.5 }}>
                {editing && <Button variant="outlined" onClick={entry.reset}>Cancelar correção</Button>}
                <Button variant="contained" onClick={submit} disabled={entry.saving || draft.items.length === 0}>
                  {editing ? 'Salvar correção' : 'Registrar entrada na sala'}
                </Button>
              </Box>
            </Box>
          </Stack>
        </Paper>
      </Box>

      <EntryHistory reloadKey={entry.historyKey} editingId={editing?.id} onEdit={startEdit} />

      <ItemDialog open={newItemOpen} item={null} initial={reader.unknown || undefined}
        onClose={() => setNewItemOpen(false)}
        onSaved={(saved) => { setNewItemOpen(false); reader.applyMaterial(saved); quantityInput.current?.focus(); }} />
      <EntryImportDialog open={importOpen} onClose={() => setImportOpen(false)}
        onAdd={async (lines) => { if (!(await addLines(lines)).problem) setImportOpen(false); }} />
      <LotRefChangeDialog conflict={entry.refConflict} onConfirm={confirmRefChange}
        onCancel={() => { entry.cancelRefChange(); notify.warning('Item não adicionado. Confira a REF do item.'); }} />
    </>
  );
}
