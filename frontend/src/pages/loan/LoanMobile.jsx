import { useState } from 'react';
import { Box, Button, Dialog, DialogTitle, IconButton, List, ListItemButton, ListItemText, Paper, Stack, Typography } from '@mui/material';
import RemoveCircleOutline from '@mui/icons-material/RemoveCircleOutline';
import CodeReader from '../../components/CodeReader';
import ConfirmDialog from '../../components/ConfirmDialog';
import StatusChip from '../../components/StatusChip';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';
import LoanForm from './LoanForm';
import LoanHistory from './LoanHistory';

const materialName = (r) => (r.component ? `${r.component}${r.size ? ` · ${r.size}` : ''}` : r.description);

/** Phone version: each reading of the label adds one unit of the source lot. */
export default function LoanMobile({ loan }) {
  const [busy, setBusy] = useState(false);
  const [choices, setChoices] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const isReturn = loan.kind === 'RETURN';

  const onCode = async (code) => {
    if (busy) return;
    setBusy(true);
    try {
      const result = await loan.addScanned(code);
      if (result?.candidates) setChoices(result.candidates);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Stack spacing={2} sx={{ pb: 10 }}>
      <Box>
        <Typography variant="h1">{isReturn ? 'Devolução à Baumer' : 'Empréstimo'}</Typography>
        <Typography variant="body2" color="text.secondary">{isReturn ? 'Sai de todos os estoques' : 'Entre hospitais'}</Typography>
      </Box>
      <LoanForm loan={loan} />
      <CodeReader onCode={onCode} busy={busy || Boolean(choices) || !loan.source || loan.loadingRows} typedLabel="Código ou lote" />

      <Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 1 }}>
          <Typography variant="h3" component="h2">{isReturn ? 'Itens a devolver' : 'Itens a emprestar'}</Typography>
          <Typography variant="body2" color="text.secondary">{loan.totals.lots} {loan.totals.lots === 1 ? 'lote' : 'lotes'} · {loan.totals.units} un.</Typography>
        </Box>
        {loan.selected.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
            {loan.source ? 'Leia a etiqueta de cada item: cada leitura soma uma unidade.' : 'Escolha o hospital de origem.'}
          </Typography>
        ) : (
          <Stack spacing={1}>
            {loan.selected.map((r) => (
              <Paper key={r.lotId} variant="outlined" sx={{ p: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography sx={{ fontWeight: 600, fontSize: 14 }} noWrap>{materialName(r)}</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ fontFamily: tokens.mono, fontSize: 12 }}>{r.ref} · {r.lot}</Typography>
                  <Typography variant="caption" color="text.secondary">Val. {formatDate(r.expiryDate)}</Typography>
                  {r.expired && <Box component="span" sx={{ ml: 1 }}><StatusChip tone="error">Vencido</StatusChip></Box>}
                </Box>
                <Typography sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>{r.quantity} un.</Typography>
                <IconButton aria-label={`Tirar uma unidade de ${r.ref} lote ${r.lot}`} onClick={() => loan.setQuantity(r.lotId, r.quantity - 1)}
                  sx={{ color: tokens.error }}>
                  <RemoveCircleOutline />
                </IconButton>
              </Paper>
            ))}
          </Stack>
        )}
      </Box>

      <LoanHistory history={loan.history} />

      <Box sx={{ position: 'fixed', left: 0, right: 0, bottom: 0, p: 2, zIndex: 10, backgroundColor: tokens.surface,
        borderTop: `1px solid ${tokens.borderLight}`, paddingBottom: 'calc(16px + env(safe-area-inset-bottom, 0px))' }}>
        <Button variant="contained" fullWidth size="large" sx={{ minHeight: 48 }} disabled={loan.sending || loan.totals.units === 0}
          onClick={() => (isReturn ? setConfirming(true) : loan.submit())}>
          {isReturn ? 'Confirmar devolução' : 'Confirmar empréstimo'}
        </Button>
      </Box>

      <Dialog open={Boolean(choices)} onClose={() => setChoices(null)} fullWidth>
        <DialogTitle sx={{ typography: 'h3' }}>Qual validade?</DialogTitle>
        <List sx={{ pt: 0 }}>
          {(choices || []).map((r) => (
            <ListItemButton key={r.lotId} onClick={() => { loan.addOne(r); setChoices(null); }}>
              <ListItemText primary={`${r.ref} · lote ${r.lot}`} secondary={`Validade ${formatDate(r.expiryDate)} · ${r.available} disponíveis`} />
            </ListItemButton>
          ))}
        </List>
      </Dialog>
      <ConfirmDialog open={confirming} title="Confirmar devolução à Baumer" confirmLabel="Devolver" danger busy={loan.sending}
        message={`${loan.totals.units} ${loan.totals.units === 1 ? 'unidade sai' : 'unidades saem'} do estoque e não entram em nenhum outro. Não é possível desfazer.`}
        onConfirm={async () => { setConfirming(false); await loan.submit(); }} onClose={() => setConfirming(false)} />
    </Stack>
  );
}
