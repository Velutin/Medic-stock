import { useRef, useState } from 'react';
import {
  Alert, Box, Button, IconButton, MenuItem, Paper, Stack, TextField, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import CodeReader from '../../components/CodeReader';
import { ItemDialog } from '../registry/ItemsTab';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';
import { todayIso } from './useEntryDraft';
import useItemReader from './useItemReader';
import MaterialLabel from './MaterialLabel';
import ExpiryField from './ExpiryField';
import LotRefChangeDialog from './LotRefChangeDialog';

const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

/** Phone version: read the label with the camera, confirm lot/expiry/quantity and add; register at the end. */
export default function EntryMobile({ entry }) {
  const notify = useNotify();
  const reader = useItemReader();
  const [newItemOpen, setNewItemOpen] = useState(false);
  const { draft, setField, totals, editing } = entry;
  const { item, errors } = reader;
  const expiryInput = useRef(null);
  const hasRead = Boolean(item.material || reader.unknown);

  /** After the lot barcode, the numeric keyboard opens for the expiry date. */
  const onCode = async (code) => {
    const result = await reader.scan(code);
    if (result && !result.material && result.lot) setTimeout(() => expiryInput.current?.focus(), 100);
  };

  /** One REF per lot: a lot registered with another REF opens the REF change dialog before entering. */
  const add = async () => {
    if (!reader.validate()) return;
    try {
      const result = await entry.requestAdd([item]);
      if (result.problem) {
        notify.warning(result.problem);
        return;
      }
      if (result.waiting) return; // the REF change dialog answers; the item stays on screen meanwhile
    } catch (err) {
      notify.error(err);
      return;
    }
    notify.success(`${item.material.ref} · lote ${item.lot.toUpperCase()} adicionado.`);
    reader.clear();
  };

  const confirmRefChange = () => {
    const added = entry.confirmRefChange();
    if (added) {
      notify.success('REF dos lotes trocada: item adicionado.');
      reader.clear();
    } else {
      notify.warning('Item não adicionado: o lote já saiu em cirurgia com a REF atual. Confira a REF do item.');
    }
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
    } catch (err) {
      notify.error(err);
    }
  };

  return (
    <Stack spacing={2} sx={{ pb: 10 }}>
      <Box>
        <Typography variant="h1">{editing ? 'Corrigir entrada' : 'Nova entrada'}</Typography>
        <Typography variant="body2" color="text.secondary">Recebimento na sala</Typography>
      </Box>

      {editing && (
        <Alert severity="info" action={<Button color="inherit" size="small" onClick={entry.reset}>Cancelar</Button>}>
          Corrigindo a entrada {editing.label}.
        </Alert>
      )}

      <TextField select label="Material direcionado a" value={draft.hospitalId} fullWidth
        onChange={(e) => setField('hospitalId', Number(e.target.value))} disabled={entry.loadingHospitals}>
        {entry.destinations.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
      </TextField>
      <TextField type="date" label="Data de recebimento" value={draft.entryDate} fullWidth
        onChange={(e) => setField('entryDate', e.target.value)} InputLabelProps={{ shrink: true }}
        inputProps={{ max: todayIso() }} />

      <CodeReader onCode={onCode} busy={reader.reading || newItemOpen || Boolean(entry.refConflict)}
        typedLabel={reader.awaitingLot ? 'Código do lote' : 'Código ou REF'} />

      {hasRead && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1.5}>
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase' }}>
              Item lido
            </Typography>
            {item.material ? (
              <MaterialLabel material={item.material} />
            ) : (
              <Alert severity="warning" action={(
                <Button color="inherit" size="small" onClick={() => setNewItemOpen(true)}>Cadastrar</Button>
              )}>
                Código fora do catálogo{reader.unknown.ref ? ` (REF ${reader.unknown.ref})` : ''}. Cadastre a nova REF.
              </Alert>
            )}
            {reader.awaitingLot && (
              <Alert severity="info" icon={false} sx={{ fontWeight: 600 }}>
                Produto lido pelo código de barras. Agora leia o código de barras do LOTE.
              </Alert>
            )}
            {!reader.awaitingLot && item.lot && !item.expiryDate && (
              <Alert severity="info" icon={false}>Digite a validade: mês e ano (ex.: 01/2030).</Alert>
            )}
            <TextField label="Lote" value={item.lot} onChange={(e) => reader.set('lot', e.target.value.toUpperCase())}
              error={Boolean(errors.lot)} helperText={errors.lot} autoComplete="off" />
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 110px', gap: 1.5 }}>
              <ExpiryField label="Validade *" value={item.expiryDate} inputRef={expiryInput}
                onChange={reader.setExpiry} error={Boolean(errors.expiryDate)} helperText={errors.expiryDate} />
              <TextField type="number" label="Quantidade" value={item.quantity}
                onChange={(e) => reader.set('quantity', e.target.value)} inputProps={{ min: 1, step: 1, inputMode: 'numeric' }}
                error={Boolean(errors.quantity)} helperText={errors.quantity} />
            </Box>
            <Box sx={{ display: 'flex', gap: 1.5 }}>
              <Button variant="outlined" onClick={reader.clear} sx={{ flex: 1 }}>Descartar</Button>
              <Button variant="contained" startIcon={<AddIcon />} onClick={add} disabled={!item.material} sx={{ flex: 2 }}>
                Adicionar
              </Button>
            </Box>
          </Stack>
        </Paper>
      )}

      <Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 1 }}>
          <Typography variant="h3" component="h2">Itens desta entrada</Typography>
          <Typography variant="body2" color="text.secondary">
            {plural(totals.lots, 'lote', 'lotes')} · {totals.units} un.
          </Typography>
        </Box>
        {draft.items.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
            Nenhum item lido ainda.
          </Typography>
        ) : (
          <Stack spacing={1}>
            {draft.items.map((line) => (
              <Paper key={line.key} variant="outlined" sx={{ p: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography sx={{ fontWeight: 600, fontSize: 14 }} noWrap>
                    {line.component ? `${line.component}${line.size ? ` · ${line.size}` : ''}` : line.description}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ fontFamily: tokens.mono, fontSize: 12 }}>
                    {line.ref} · {line.lot}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">Val. {formatDate(line.expiryDate)}</Typography>
                </Box>
                <Typography sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>{line.quantity} un.</Typography>
                <IconButton aria-label={`Remover ${line.ref} lote ${line.lot}`} onClick={() => entry.removeItem(line.key)}
                  sx={{ color: tokens.error }}>
                  <DeleteOutline />
                </IconButton>
              </Paper>
            ))}
          </Stack>
        )}
      </Box>

      <Box sx={{ position: 'fixed', left: 0, right: 0, bottom: 0, p: 2, zIndex: 10, backgroundColor: tokens.surface,
        borderTop: `1px solid ${tokens.borderLight}`, paddingBottom: 'calc(16px + env(safe-area-inset-bottom, 0px))' }}>
        <Button variant="contained" fullWidth size="large" onClick={submit} disabled={entry.saving || draft.items.length === 0}
          sx={{ minHeight: 48 }}>
          {editing ? 'Salvar correção' : 'Registrar entrada na sala'}
        </Button>
      </Box>

      <ItemDialog open={newItemOpen} item={null} initial={reader.unknown || undefined}
        onClose={() => setNewItemOpen(false)}
        onSaved={(saved) => { setNewItemOpen(false); reader.applyMaterial(saved); }} />
      <LotRefChangeDialog conflict={entry.refConflict} onConfirm={confirmRefChange}
        onCancel={() => { entry.cancelRefChange(); notify.warning('Item não adicionado. Confira a REF do item.'); }} />
    </Stack>
  );
}
