import { useEffect, useState } from 'react';
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography,
} from '@mui/material';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import { tokens } from '../../theme';

/**
 * Edits an item of the stock by lot (administrators): the lot number and the expiry date, typed wrong at the
 * entry, and the counted balance of the line. Everything is saved in one call.
 *
 * The lot is one record shared by every hospital, so correcting the number or the date corrects it wherever
 * the lot appears - it is the same physical lot. The balance, on the other hand, belongs to the line, which
 * is why location says which balance is being counted: inside the hospital or in its storeroom.
 */
export default function AdjustDialog({ row, location = 'HOSPITAL', onClose, onSaved }) {
  const notify = useNotify();
  const storeroom = location === 'STOREROOM';
  const currentQuantity = row ? (storeroom ? row.storeroomQuantity : row.hospitalQuantity) : 0;
  const placeLabel = storeroom ? 'na sala' : 'no hospital';
  const [lot, setLot] = useState('');
  const [expiryDate, setExpiryDate] = useState('');
  const [quantity, setQuantity] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (row) {
      setLot(row.lot || '');
      setExpiryDate((row.expiryDate || '').slice(0, 10));
      setQuantity(String(storeroom ? row.storeroomQuantity : row.hospitalQuantity));
      setReason('');
      setErrors({});
    }
  }, [row, storeroom]);

  if (!row) return null;

  const newLot = lot.trim().toUpperCase();
  const lotChanged = newLot !== (row.lot || '').toUpperCase() || expiryDate !== (row.expiryDate || '').slice(0, 10);
  const counted = Number(quantity);
  const quantityChanged = quantity !== '' && Number.isInteger(counted) && counted !== currentQuantity;

  const submit = async (e) => {
    e.preventDefault();
    const found = {};
    if (!newLot) found.lot = 'Informe o lote.';
    if (!expiryDate) found.expiryDate = 'Informe a validade.';
    if (quantity === '' || !Number.isInteger(counted) || counted < 0) found.quantity = 'Informe a quantidade contada (0 ou mais).';
    if (!reason.trim()) found.reason = 'Informe o motivo.';
    setErrors(found);
    if (Object.keys(found).length) return;
    if (!lotChanged && !quantityChanged) {
      notify.info('Nada foi alterado.');
      onClose();
      return;
    }
    setSaving(true);
    try {
      await api(`/materials/lots/${row.lotId}`, {
        method: 'PUT',
        body: {
          lot: newLot,
          expiryDate,
          hospitalId: row.hospitalId,
          location,
          countedQuantity: quantityChanged ? counted : null,
          reason: reason.trim(),
        },
      });
      notify.success(lotChanged ? `Lote corrigido para ${newLot}.` : `Saldo ${placeLabel} ajustado de ${currentQuantity} para ${counted}.`);
      onSaved();
    } catch (err) {
      setErrors(err.fields || {});
      notify.error(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <Box component="form" onSubmit={submit} noValidate>
        <DialogTitle sx={{ typography: 'h2' }}>Editar item do estoque</DialogTitle>
        <DialogContent>
          <Box sx={{ backgroundColor: tokens.background, borderRadius: '6px', p: 1.5, mb: 2 }}>
            <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{row.component || row.description}</Typography>
            <Typography variant="body2" color="text.secondary">
              REF <span style={{ fontFamily: tokens.mono }}>{row.ref}</span> · {storeroom ? `sala de ${row.hospital}` : row.hospital}
              {' '}· saldo atual: {currentQuantity}
            </Typography>
          </Box>
          <Stack spacing={2}>
            <TextField label="Lote" value={lot} autoFocus onChange={(e) => setLot(e.target.value.toUpperCase())}
              error={Boolean(errors.lot)} helperText={errors.lot} autoComplete="off"
              slotProps={{ htmlInput: { style: { fontFamily: tokens.mono } } }} />
            <TextField label="Validade" type="date" value={expiryDate} onChange={(e) => setExpiryDate(e.target.value)}
              error={Boolean(errors.expiryDate)} helperText={errors.expiryDate} InputLabelProps={{ shrink: true }} />
            <TextField label={storeroom ? 'Quantidade na sala' : 'Quantidade no hospital'} type="number" inputMode="numeric" value={quantity}
              onChange={(e) => setQuantity(e.target.value)} error={Boolean(errors.quantity || errors.countedQuantity)}
              helperText={errors.quantity || errors.countedQuantity} slotProps={{ htmlInput: { min: 0, step: 1 } }} />
            <TextField label="Motivo" placeholder="Ex.: lote digitado errado na entrada" value={reason}
              onChange={(e) => setReason(e.target.value)} error={Boolean(errors.reason)}
              helperText={errors.reason || 'Fica registrado no histórico de movimentações.'} multiline minRows={2} />
            {lotChanged && (
              <Alert severity="warning">
                O lote é o mesmo em todos os hospitais e na sala: a correção vale em todos eles. Se este lote já
                saiu em cirurgia, o histórico dessas cirurgias passa a mostrar o número corrigido. E se já
                existir um lote igual nesta REF, com o mesmo número e a mesma validade, os dois serão unidos.
              </Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button variant="outlined" onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={saving}>Salvar</Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
