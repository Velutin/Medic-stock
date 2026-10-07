import { useCallback, useEffect, useState } from 'react';
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDateTime } from '../../utils/format';
import { tokens } from '../../theme';
import { currentMonth, periodLabel } from './MonthPicker';

const pct = (v) => `${Number(v).toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`;
const parseRate = (text) => {
  const n = Number(String(text).replace(',', '.'));
  return Number.isFinite(n) && n >= 0 && n <= 100 ? n : null;
};

/**
 * Billing rates (commission over the total and share over the commission). Each rate applies from its start month
 * until the next one; only the current or a future month can be created, changed or removed (past billing never changes).
 */
export default function RatesDialog({ open, onClose, onChanged }) {
  const notify = useNotify();
  const [rates, setRates] = useState([]);
  const [form, setForm] = useState({ month: currentMonth(), commission: '', share: '' });
  const [saving, setSaving] = useState(false);
  const now = currentMonth();

  const load = useCallback(async () => {
    try {
      const list = await api('/billing-rates');
      setRates(list);
      const latest = list[0];
      setForm({ month: now, commission: latest ? String(latest.commissionRate) : '20', share: latest ? String(latest.shareRate) : '8.5' });
    } catch (err) {
      notify.error(err);
    }
  }, [notify, now]);

  useEffect(() => { if (open) load(); }, [open, load]);

  const commission = parseRate(form.commission);
  const share = parseRate(form.share);
  const valid = form.month >= now && commission !== null && share !== null;

  const save = async () => {
    setSaving(true);
    try {
      await api(`/billing-rates/${form.month}`, { method: 'PUT', body: { commissionRate: commission, shareRate: share } });
      notify.success(`Percentuais a partir de ${periodLabel([form.month])} salvos.`);
      await load();
      onChanged();
    } catch (err) {
      notify.error(err);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (month) => {
    try {
      await api(`/billing-rates/${month}`, { method: 'DELETE' });
      notify.success('Percentuais removidos. Os anteriores voltam a valer.');
      await load();
      onChanged();
    } catch (err) {
      notify.error(err);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ typography: 'h2' }}>Percentuais do faturamento</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2.5}>
          <Typography variant="body2" color="text.secondary">
            Cada percentual vale a partir do mês informado até o próximo cadastrado. Meses passados não mudam.
          </Typography>
          <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr 1fr' } }}>
            <TextField type="month" label="A partir de" value={form.month} InputLabelProps={{ shrink: true }}
              inputProps={{ min: now }} onChange={(e) => setForm({ ...form, month: e.target.value })}
              error={form.month < now} helperText={form.month < now ? 'Só mês atual ou futuro.' : ' '} />
            <TextField label="% sobre o total" value={form.commission} inputProps={{ inputMode: 'decimal' }}
              onChange={(e) => setForm({ ...form, commission: e.target.value })} error={commission === null} helperText=" " />
            <TextField label="% sobre a comissão" value={form.share} inputProps={{ inputMode: 'decimal' }}
              onChange={(e) => setForm({ ...form, share: e.target.value })} error={share === null} helperText=" " />
          </Box>
          <Table size="small">
            <TableHead>
              <TableRow><TableCell>A partir de</TableCell><TableCell align="right">Sobre o total</TableCell>
                <TableCell align="right">Sobre a comissão</TableCell><TableCell>Cadastrado</TableCell><TableCell /></TableRow>
            </TableHead>
            <TableBody>
              {rates.map((r) => (
                <TableRow key={r.startMonth}>
                  <TableCell>{periodLabel([r.startMonth])}</TableCell>
                  <TableCell align="right">{pct(r.commissionRate)}</TableCell>
                  <TableCell align="right">{pct(r.shareRate)}</TableCell>
                  <TableCell><Typography variant="caption" color="text.secondary">{r.createdBy || '—'} · {formatDateTime(r.createdAt)}</Typography></TableCell>
                  <TableCell align="right">
                    {r.startMonth >= now && (
                      <IconButton size="small" aria-label={`Remover percentuais de ${periodLabel([r.startMonth])}`}
                        onClick={() => remove(r.startMonth)} sx={{ color: tokens.error }}>
                        <DeleteOutline fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button variant="outlined" onClick={onClose}>Fechar</Button>
        <Button variant="contained" onClick={save} disabled={!valid || saving}>Salvar percentuais</Button>
      </DialogActions>
    </Dialog>
  );
}
