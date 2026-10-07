import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Link, Paper, Stack, Table, TableBody, TableCell,
  TableHead, TablePagination, TableRow, TextField, Typography,
} from '@mui/material';
import { api } from '../../api/client';
import { API_BASE } from '../../config';
import { useNotify } from '../../notifications/NotificationProvider';
import StatusChip from '../../components/StatusChip';
import { formatDate, formatMoney } from '../../utils/format';
import { tokens } from '../../theme';
import { Empty, Loading, PAGE, useReport } from './reportUtils';

const mono = { fontFamily: tokens.mono, fontSize: 13 };

/** Cancels a surgery: the items go back to the hospital stock; the reason is required. */
function CancelDialog({ surgery, onClose, onDone }) {
  const notify = useNotify();
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const save = async () => {
    setSaving(true);
    try {
      await api(`/surgeries/${surgery.id}`, { method: 'PATCH', body: { status: 'CANCELLED', cancellationReason: reason.trim() } });
      notify.success(`Cirurgia de ${surgery.patientName} cancelada. Os itens voltaram ao estoque do hospital.`);
      setReason('');
      onDone();
    } catch (err) {
      notify.error(err);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Dialog open={Boolean(surgery)} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ typography: 'h2' }}>Cancelar cirurgia</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Typography variant="body2">
            {surgery && <><strong>{surgery.patientName}</strong> · {surgery.hospital} · {formatDate(surgery.surgeryDate)}. </>}
            Os itens voltam ao estoque do hospital e a cirurgia sai do faturamento.
          </Typography>
          <TextField label="Motivo do cancelamento" required multiline minRows={2} value={reason}
            onChange={(e) => setReason(e.target.value)} placeholder="Ex.: cirurgia lançada em duplicidade" />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button variant="outlined" onClick={onClose}>Voltar</Button>
        <Button variant="contained" color="error" onClick={save} disabled={!reason.trim() || saving}>Cancelar cirurgia</Button>
      </DialogActions>
    </Dialog>
  );
}

/** Completed surgeries with items, lots and table values; cancelled ones are in the Cancellations report. */
export default function ConsumptionReport({ query }) {
  const navigate = useNavigate();
  const { data, reload } = useReport('/reports/consumption', query);
  const [page, setPage] = useState(0);
  const [cancelling, setCancelling] = useState(null);
  if (!data) return <Loading />;
  const visible = data.slice(page * PAGE, page * PAGE + PAGE);
  return (
    <Stack spacing={1.5}>
      <Typography variant="body2" color="text.secondary">
        {data.length} {data.length === 1 ? 'cirurgia concluída' : 'cirurgias concluídas'} · canceladas ficam em Cancelamentos
      </Typography>
      {data.length === 0 ? <Empty>Nenhuma cirurgia concluída no período.</Empty> : visible.map((c) => (
        <Paper key={c.id} variant="outlined" sx={{ p: 2 }}>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', gap: 1, mb: 1 }}>
            <Box>
              <Typography sx={{ fontWeight: 600 }}>{c.patientName}</Typography>
              <Typography variant="body2" color="text.secondary">{c.hospital} · {formatDate(c.surgeryDate)} · lançada por {c.createdBy || '—'}</Typography>
            </Box>
            <Box sx={{ textAlign: 'right' }}>
              <StatusChip tone="success">Concluída</StatusChip>
              <Typography sx={{ fontWeight: 700, mt: 0.5 }}>Valor total: {formatMoney(c.totalValue || 0)}</Typography>
            </Box>
          </Box>
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small" sx={{ minWidth: 620 }}>
              <TableHead>
                <TableRow><TableCell>Material</TableCell><TableCell>REF</TableCell><TableCell>Lote</TableCell><TableCell align="right">Qtd.</TableCell><TableCell align="right">Valor</TableCell></TableRow>
              </TableHead>
              <TableBody>
                {c.items.map((i) => (
                  <TableRow key={i.id}>
                    <TableCell>{i.component ? `${i.component}${i.size ? ` · ${i.size}` : ''}` : i.description}</TableCell>
                    <TableCell sx={mono}>{i.ref}</TableCell><TableCell sx={mono}>{i.lot}</TableCell>
                    <TableCell align="right">{i.quantity}</TableCell>
                    <TableCell align="right">{i.unitValue == null ? <Typography variant="caption" color="warning.main">Sem valor</Typography> : formatMoney(i.unitValue * i.quantity)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mt: 1.5, alignItems: 'center' }}>
            {c.hasSheet && <Link href={`${API_BASE}/surgeries/${c.id}/sheet`} target="_blank" rel="noopener" variant="body2" sx={{ mr: 1 }}>Ver PDF</Link>}
            <Button size="small" variant="outlined" onClick={() => navigate('/saida', { state: { surgeryId: c.id } })}>Editar itens</Button>
            <Button size="small" variant="outlined" color="error" onClick={() => setCancelling(c)}>Cancelar cirurgia</Button>
          </Box>
        </Paper>
      ))}
      {data.length > PAGE && (
        <TablePagination component="div" count={data.length} page={page} rowsPerPage={PAGE} rowsPerPageOptions={[PAGE]}
          onPageChange={(_, p) => setPage(p)} labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
      )}
      <CancelDialog surgery={cancelling} onClose={() => setCancelling(null)} onDone={() => { setCancelling(null); reload(); }} />
    </Stack>
  );
}
