import { useEffect, useState } from 'react';
import {
  Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Stack,
  Typography,
} from '@mui/material';
import StatusChip from '../../components/StatusChip';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import { tokens } from '../../theme';

const mono = { fontFamily: tokens.mono, fontSize: 13 };

const STATUS = {
  OPEN: ['Em transporte', 'info'],
  PARTIAL: ['Parcial', 'warning'],
  RECEIVED: ['Recebida', 'success'],
  AWAITING_XML: ['Sem XML', 'warning'],
};

/**
 * Conferência da entrada: o que as notas ainda devem, mostrado logo depois de gravar.
 *
 * É aqui que a auditoria deixa de depender de memória. Com a caixa recém-aberta, a falta é um fato apurado
 * — e não uma diferença descoberta meses depois numa contagem, quando já ninguém sabe se o material não veio
 * ou se veio e se perdeu.
 */
export default function EntryInvoiceDialog({ summary, onClose }) {
  const notify = useNotify();
  const [checked, setChecked] = useState({});
  const [busy, setBusy] = useState(false);

  useEffect(() => { setChecked({}); }, [summary]);
  if (!summary) return null;

  const instruments = summary.instrumentsAwaiting || [];
  const unlinked = summary.unlinked || [];
  const invoices = summary.invoices || [];
  const pendingIssues = summary.pendingIssues || 0;
  const unlinkedUnits = unlinked.reduce((sum, line) => sum + line.quantity, 0);
  const selected = Object.entries(checked).filter(([, v]) => v).map(([id]) => Number(id));

  const confirm = async () => {
    setBusy(true);
    try {
      await api('/stock-entries/instrument-confirmations', { method: 'POST', body: selected });
      notify.success(selected.length === 1
        ? 'Instrumental confirmado como recebido.'
        : `${selected.length} instrumentais confirmados como recebidos.`);
      onClose();
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ typography: 'h2' }}>Conferência da entrada</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>

          {invoices.map((invoice) => {
            const [label, tone] = STATUS[invoice.status] || [invoice.status, 'neutral'];
            return (
              <Box key={invoice.id}>
                <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                  <Typography variant="body2" sx={{ fontWeight: 500 }}>
                    Nota {invoice.number} · {invoice.emitter}
                  </Typography>
                  <StatusChip tone={tone}>{label}</StatusChip>
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  {invoice.receivedUnits} de {invoice.announcedUnits} itens recebidos
                </Typography>
                {invoice.missing.length > 0 && (
                  <Box sx={{ mt: 0.75 }}>
                    {invoice.missing.map((line) => (
                      <Typography key={line.invoiceItemId} variant="caption" sx={{ display: 'block', ...mono }}>
                        falta {line.missing} · {line.productCode} · {line.description}
                        {line.category === 'INSTRUMENT' ? ' (instrumental)' : ''}
                      </Typography>
                    ))}
                  </Box>
                )}
              </Box>
            );
          })}

          {pendingIssues > 0 ? (
            <Alert severity="warning">
              <Typography variant="body2" sx={{ fontWeight: 500, mb: 0.5 }}>
                {pendingIssues === 1 ? 'Um lote entrou sem nota' : `${pendingIssues} lotes entraram sem nota`}
              </Typography>
              <Typography variant="caption" sx={{ display: 'block' }}>
                Entraram no estoque normalmente. Como a nota está sendo exigida, {pendingIssues === 1
                  ? 'ele virou uma pendência' : 'eles viraram pendências'} em Relatórios &gt; Pendências,
                para você informar a nota ou registrar que veio sem ela.
              </Typography>
            </Alert>
          ) : unlinked.length > 0 && invoices.length > 0 && (
            // Só a contagem: a lista inteira, numa entrada de centenas de itens, é uma parede que ninguém lê
            <Typography variant="caption" color="text.secondary">
              {unlinkedUnits === 1 ? '1 item entrou sem nota' : `${unlinkedUnits} itens entraram sem nota`}
              {unlinked.length === 1 ? ` (lote ${unlinked[0].lot})` : ` em ${unlinked.length} lotes`}.
              Nenhuma nota aberta estava devendo {unlinked.length === 1 ? 'esse lote' : 'esses lotes'}.
            </Typography>
          )}

          {instruments.length > 0 && (
            <Box>
              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                Instrumental no mesmo rastreio
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                Estes itens vieram no mesmo código de rastreio das notas desta entrada. Eles não têm etiqueta
                para ler: marque os que estavam na caixa.
              </Typography>
              {instruments.map((line) => (
                <FormControlLabel key={line.invoiceItemId}
                  control={(
                    <Checkbox size="small" checked={Boolean(checked[line.invoiceItemId])}
                      onChange={(e) => setChecked((c) => ({ ...c, [line.invoiceItemId]: e.target.checked }))} />
                  )}
                  label={(
                    <Typography variant="body2" sx={mono}>
                      {line.missing} × {line.productCode} · {line.description}
                      <Typography component="span" variant="caption" color="text.secondary">
                        {` (nota ${line.invoiceNumber})`}
                      </Typography>
                    </Typography>
                  )} />
              ))}
            </Box>
          )}

          {invoices.length === 0 && pendingIssues === 0 && (
            <Typography variant="body2" color="text.secondary">
              Nenhuma nota aberta correspondia a esta entrada.
            </Typography>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button variant="outlined" onClick={onClose}>
          {instruments.length > 0 ? 'Não chegou / fechar' : 'Fechar'}
        </Button>
        {instruments.length > 0 && (
          <Button variant="contained" disabled={busy || selected.length === 0} onClick={confirm}>
            Confirmar recebimento
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
