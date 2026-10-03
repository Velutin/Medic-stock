import { useEffect, useState } from 'react';
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography,
} from '@mui/material';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';

/** Inventory adjustment of the quantity inside the hospital (administrators). */
export default function AdjustDialog({ row, onClose, onSaved }) {
  const notify = useNotify();
  const [quantity, setQuantity] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (row) {
      setQuantity(String(row.hospitalQuantity));
      setReason('');
      setErrors({});
    }
  }, [row]);

  if (!row) return null;

  const submit = async (e) => {
    e.preventDefault();
    const found = {};
    const counted = Number(quantity);
    if (quantity === '' || !Number.isInteger(counted) || counted < 0) found.quantity = 'Informe a quantidade contada (0 ou mais).';
    if (!reason.trim()) found.reason = 'Informe o motivo do ajuste.';
    setErrors(found);
    if (Object.keys(found).length) return;
    if (counted === row.hospitalQuantity) {
      notify.info('A quantidade informada é igual à atual; nada foi alterado.');
      onClose();
      return;
    }
    setSaving(true);
    try {
      await api('/stock/adjustment', {
        method: 'POST',
        body: { hospitalId: row.hospitalId, location: 'HOSPITAL', lotId: row.lotId, countedQuantity: counted, reason: reason.trim() },
      });
      notify.success(`Saldo ajustado de ${row.hospitalQuantity} para ${counted}.`);
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
        <DialogTitle sx={{ typography: 'h2' }}>Ajustar saldo no hospital</DialogTitle>
        <DialogContent>
          <Box sx={{ backgroundColor: tokens.background, borderRadius: '6px', p: 1.5, mb: 2 }}>
            <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{row.component || row.description}</Typography>
            <Typography variant="body2" color="text.secondary">
              REF <span style={{ fontFamily: tokens.mono }}>{row.ref}</span> · lote <span style={{ fontFamily: tokens.mono }}>{row.lot}</span>
              {' '}· validade {formatDate(row.expiryDate)}
            </Typography>
            <Typography variant="body2" color="text.secondary">{row.hospital} · saldo atual: {row.hospitalQuantity}</Typography>
          </Box>
          <Stack spacing={2}>
            <TextField label="Quantidade contada" type="number" inputMode="numeric" value={quantity} autoFocus
              onChange={(e) => setQuantity(e.target.value)} error={Boolean(errors.quantity || errors.countedQuantity)}
              helperText={errors.quantity || errors.countedQuantity} slotProps={{ htmlInput: { min: 0, step: 1 } }} />
            <TextField label="Motivo" placeholder="Ex.: contagem física de 01/10" value={reason} onChange={(e) => setReason(e.target.value)}
              error={Boolean(errors.reason)} helperText={errors.reason || 'Fica registrado no histórico de movimentações.'} multiline minRows={2} />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button variant="outlined" onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={saving}>Salvar ajuste</Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
