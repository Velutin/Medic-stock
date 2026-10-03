import { useState } from 'react';
import {
  Alert, Box, Button, Dialog, DialogTitle, IconButton, List, ListItemButton, ListItemText, MenuItem, Paper, Stack,
  TextField, Typography,
} from '@mui/material';
import RemoveCircleOutline from '@mui/icons-material/RemoveCircleOutline';
import PictureAsPdfOutlined from '@mui/icons-material/PictureAsPdfOutlined';
import CodeReader from '../../components/CodeReader';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';

const materialName = (r) => (r.component ? `${r.component}${r.size ? ` · ${r.size}` : ''}` : r.description);

/** Phone version: each reading of the label adds one unit of the storeroom lot. */
export default function TransferMobile({ transfer }) {
  const [busy, setBusy] = useState(false);
  const [choices, setChoices] = useState(null);
  const { hospital, source, selected, totals } = transfer;

  const onCode = async (code) => {
    if (busy) return;
    setBusy(true);
    try {
      const result = await transfer.addScanned(code);
      if (result?.candidates) setChoices(result.candidates);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Stack spacing={2} sx={{ pb: 10 }}>
      <Box>
        <Typography variant="h1">Transferência</Typography>
        <Typography variant="body2" color="text.secondary">Sala → hospital</Typography>
      </Box>

      <TextField select label="Hospital de destino" value={transfer.hospitalId} fullWidth
        onChange={(e) => transfer.changeHospital(Number(e.target.value))} disabled={transfer.loadingHospitals}>
        {transfer.destinations.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
      </TextField>
      {hospital && source && source.id !== hospital.id && (
        <Alert severity="info">Os itens saem da sala de {source.name}.</Alert>
      )}

      {transfer.lastDelivery && (
        <Alert severity="success" action={(
          <IconButton color="inherit" aria-label="Baixar PDF novamente" onClick={() => transfer.downloadPdf(transfer.lastDelivery)}>
            <PictureAsPdfOutlined />
          </IconButton>
        )}>
          Transferência #{transfer.lastDelivery.id} registrada.
        </Alert>
      )}

      <CodeReader onCode={onCode} busy={busy || Boolean(choices) || !hospital || transfer.loadingRows} typedLabel="Código ou lote" />

      <Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 1 }}>
          <Typography variant="h3" component="h2">Itens a enviar</Typography>
          <Typography variant="body2" color="text.secondary">
            {totals.lots} {totals.lots === 1 ? 'lote' : 'lotes'} · {totals.units} un.
          </Typography>
        </Box>
        {selected.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
            {hospital ? 'Leia a etiqueta de cada item: cada leitura soma uma unidade.' : 'Escolha o hospital de destino.'}
          </Typography>
        ) : (
          <Stack spacing={1}>
            {selected.map((r) => (
              <Paper key={r.lotId} variant="outlined" sx={{ p: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography sx={{ fontWeight: 600, fontSize: 14 }} noWrap>{materialName(r)}</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ fontFamily: tokens.mono, fontSize: 12 }}>
                    {r.ref} · {r.lot}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">Val. {formatDate(r.expiryDate)}</Typography>
                </Box>
                <Typography sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>{r.quantity} un.</Typography>
                <IconButton aria-label={`Tirar uma unidade de ${r.ref} lote ${r.lot}`}
                  onClick={() => transfer.setQuantity(r.lotId, r.quantity - 1)} sx={{ color: tokens.error }}>
                  <RemoveCircleOutline />
                </IconButton>
              </Paper>
            ))}
          </Stack>
        )}
      </Box>

      <Box sx={{ position: 'fixed', left: 0, right: 0, bottom: 0, p: 2, zIndex: 10, backgroundColor: tokens.surface,
        borderTop: `1px solid ${tokens.borderLight}`, paddingBottom: 'calc(16px + env(safe-area-inset-bottom, 0px))' }}>
        <Button variant="contained" fullWidth size="large" onClick={transfer.submit} sx={{ minHeight: 48 }}
          disabled={transfer.sending || totals.units === 0}>
          Transferir e gerar PDF
        </Button>
      </Box>

      <Dialog open={Boolean(choices)} onClose={() => setChoices(null)} fullWidth>
        <DialogTitle sx={{ typography: 'h3' }}>Qual validade?</DialogTitle>
        <List sx={{ pt: 0 }}>
          {(choices || []).map((r) => (
            <ListItemButton key={r.lotId} onClick={() => { transfer.addOne(r); setChoices(null); }}>
              <ListItemText primary={`${r.ref} · lote ${r.lot}`}
                secondary={`Validade ${formatDate(r.expiryDate)} · ${r.storeroomQuantity} na sala`} />
            </ListItemButton>
          ))}
        </List>
      </Dialog>
    </Stack>
  );
}
