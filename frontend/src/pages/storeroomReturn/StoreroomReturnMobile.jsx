import { useState } from 'react';
import {
  Alert, Box, Button, Chip, Dialog, DialogTitle, IconButton, List, ListItemButton, ListItemText, MenuItem, Paper,
  Stack, TextField, Typography,
} from '@mui/material';
import RemoveCircleOutline from '@mui/icons-material/RemoveCircleOutline';
import PictureAsPdfOutlined from '@mui/icons-material/PictureAsPdfOutlined';
import CodeReader from '../../components/CodeReader';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';

const materialName = (r) => (r.component ? `${r.component}${r.size ? ` · ${r.size}` : ''}` : r.description);

/** Phone version: each reading of the label adds one unit of the lot inside the hospital. */
export default function StoreroomReturnMobile({ draft }) {
  const [busy, setBusy] = useState(false);
  const [choices, setChoices] = useState(null);
  const { hospital, storeroom, selected, totals } = draft;

  const onCode = async (code) => {
    if (busy) return;
    setBusy(true);
    try {
      const result = await draft.addScanned(code);
      if (result?.candidates) setChoices(result.candidates);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Stack spacing={2} sx={{ pb: 10 }}>
      <Box>
        <Typography variant="h1">Retirada</Typography>
        <Typography variant="body2" color="text.secondary">Hospital → sala</Typography>
      </Box>

      <TextField select label="Hospital de origem" value={draft.hospitalId} fullWidth
        onChange={(e) => draft.changeHospital(Number(e.target.value))} disabled={draft.loadingHospitals}>
        {draft.sources.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
      </TextField>
      {hospital && storeroom && storeroom.id !== hospital.id && (
        <Alert severity="info">Os itens voltam para a sala de {storeroom.name}.</Alert>
      )}

      {draft.lastReturn && (
        <Alert severity="success" action={(
          <IconButton color="inherit" aria-label="Baixar PDF novamente" onClick={() => draft.downloadPdf(draft.lastReturn)}>
            <PictureAsPdfOutlined />
          </IconButton>
        )}>
          Retirada #{draft.lastReturn.id} registrada.
        </Alert>
      )}

      <CodeReader onCode={onCode} busy={busy || Boolean(choices) || !hospital || draft.loadingRows} typedLabel="Código ou lote" />

      <Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 1 }}>
          <Typography variant="h3" component="h2">Itens a retirar</Typography>
          <Typography variant="body2" color="text.secondary">
            {totals.lots} {totals.lots === 1 ? 'lote' : 'lotes'} · {totals.units} un.
          </Typography>
        </Box>
        {selected.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
            {hospital ? 'Leia a etiqueta de cada item: cada leitura soma uma unidade.' : 'Escolha o hospital de origem.'}
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
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                    <Typography variant="caption" color="text.secondary">Val. {formatDate(r.expiryDate)}</Typography>
                    {r.expired && <Chip label="Vencido" size="small" color="error" variant="outlined" />}
                  </Box>
                </Box>
                <Typography sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>{r.quantity} un.</Typography>
                <IconButton aria-label={`Tirar uma unidade de ${r.ref} lote ${r.lot}`}
                  onClick={() => draft.setQuantity(r.lotId, r.quantity - 1)} sx={{ color: tokens.error }}>
                  <RemoveCircleOutline />
                </IconButton>
              </Paper>
            ))}
          </Stack>
        )}
      </Box>

      <TextField label="Motivo da retirada" value={draft.reason} required multiline minRows={2} fullWidth
        onChange={(e) => draft.setReason(e.target.value)} helperText="Obrigatório; sai impresso no relatório." />

      <Box sx={{ position: 'fixed', left: 0, right: 0, bottom: 0, p: 2, zIndex: 10, backgroundColor: tokens.surface,
        borderTop: `1px solid ${tokens.borderLight}`, paddingBottom: 'calc(16px + env(safe-area-inset-bottom, 0px))' }}>
        <Button variant="contained" fullWidth size="large" onClick={draft.submit} sx={{ minHeight: 48 }}
          disabled={draft.sending || totals.units === 0 || !draft.reason.trim()}>
          Retirar e gerar PDF
        </Button>
      </Box>

      <Dialog open={Boolean(choices)} onClose={() => setChoices(null)} fullWidth>
        <DialogTitle sx={{ typography: 'h3' }}>Qual validade?</DialogTitle>
        <List sx={{ pt: 0 }}>
          {(choices || []).map((r) => (
            <ListItemButton key={r.lotId} onClick={() => { draft.addOne(r); setChoices(null); }}>
              <ListItemText primary={`${r.ref} · lote ${r.lot}`}
                secondary={`Validade ${formatDate(r.expiryDate)}${r.expired ? ' (vencido)' : ''} · ${r.hospitalQuantity} no hospital`} />
            </ListItemButton>
          ))}
        </List>
      </Dialog>
    </Stack>
  );
}
