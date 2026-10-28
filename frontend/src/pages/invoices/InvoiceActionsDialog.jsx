import { useEffect, useState } from 'react';
import {
  Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography,
} from '@mui/material';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDate } from '../../utils/format';

/**
 * What can be done to an invoice by hand: record the tracking code while the supplier does not send it, and
 * close an invoice that will not arrive.
 *
 * Closing asks for a reason because an invoice that leaves the alert without one is the blind spot coming
 * back: months later nobody remembers whether that material was settled, lost or simply forgotten.
 */
export default function InvoiceActionsDialog({ invoice, onClose, onChanged }) {
  const notify = useNotify();
  const [tracking, setTracking] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setTracking(invoice?.trackingCode || '');
    setReason('');
  }, [invoice]);

  if (!invoice) return null;
  const closed = invoice.status === 'CLOSED';

  const saveTracking = async () => {
    setBusy(true);
    try {
      await api(`/invoices/${invoice.id}/tracking-code`, { method: 'PATCH', body: { trackingCode: tracking } });
      notify.success('Rastreio salvo.');
      onChanged();
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };

  const changeClosure = async (reopen) => {
    setBusy(true);
    try {
      await api(`/invoices/${invoice.id}/closure`, {
        method: 'PATCH',
        body: reopen ? { reopen: 'true' } : { reason },
      });
      notify.success(reopen ? 'Nota reaberta.' : 'Nota encerrada.');
      onChanged();
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ typography: 'h2' }}>
        Nota {invoice.number}{invoice.series ? `/${invoice.series}` : ''}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {invoice.emitterName} · emitida em {formatDate(invoice.issuedAt)} ·
            {' '}{invoice.receivedUnits} de {invoice.announcedUnits} itens recebidos
          </Typography>

          <Stack direction="row" spacing={1.5} alignItems="flex-start">
            <TextField label="Código de rastreio" size="small" fullWidth value={tracking}
              onChange={(e) => setTracking(e.target.value.toUpperCase())}
              helperText="Enquanto a empresa não enviar o código por e-mail, ele pode ser lançado aqui." />
            <Button variant="outlined" onClick={saveTracking} disabled={busy} sx={{ mt: 0.25 }}>Salvar</Button>
          </Stack>

          {closed ? (
            <Stack spacing={1}>
              <Typography variant="body2">
                Encerrada{invoice.closeReason ? `: ${invoice.closeReason}` : ''}.
              </Typography>
              <Button variant="outlined" onClick={() => changeClosure(true)} disabled={busy} sx={{ alignSelf: 'flex-start' }}>
                Reabrir nota
              </Button>
            </Stack>
          ) : invoice.status !== 'RECEIVED' && (
            <Stack spacing={1}>
              <TextField label="Motivo do encerramento" size="small" fullWidth multiline minRows={2}
                value={reason} onChange={(e) => setReason(e.target.value)}
                helperText="Para tirar a nota da cobrança: resolvida com a empresa, extraviada, cancelada." />
              <Button variant="contained" color="error" disabled={busy || !reason.trim()}
                onClick={() => changeClosure(false)} sx={{ alignSelf: 'flex-start' }}>
                Encerrar nota
              </Button>
            </Stack>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button variant="outlined" onClick={onClose}>Fechar</Button>
      </DialogActions>
    </Dialog>
  );
}
