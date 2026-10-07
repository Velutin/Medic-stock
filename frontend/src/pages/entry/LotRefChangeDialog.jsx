import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Paper, Stack, Typography, useMediaQuery,
} from '@mui/material';
import StatusChip from '../../components/StatusChip';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';

const mono = { fontFamily: tokens.mono, fontSize: 13 };
const where = (b) => `${b.hospital}${b.location === 'STOREROOM' ? ' (sala)' : ''}`;

/**
 * A lot number belongs to only one REF. Shows the lots that already have the number with another REF and asks to
 * change them to the REF being received (balance and history go with them). Lots already used in a surgery
 * cannot change: their item stays out of the entry.
 */
export default function LotRefChangeDialog({ conflict, onConfirm, onCancel }) {
  const fullScreen = useMediaQuery('(max-width:599.95px)');
  if (!conflict) return null;
  const groups = conflict.items.map((item) => ({
    item,
    lots: conflict.conflicts.find((c) => c.materialId === item.material.id && c.lot === item.lot.trim().toUpperCase())?.lots || [],
  }));
  const blocked = (g) => g.lots.some((l) => l.usedInSurgery);
  const allowed = groups.filter((g) => !blocked(g)).length;

  return (
    <Dialog open onClose={onCancel} fullWidth maxWidth="md" fullScreen={fullScreen} aria-labelledby="lot-ref-title">
      <DialogTitle id="lot-ref-title" sx={{ typography: 'h2' }}>Lote já cadastrado com outra REF</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Cada lote pertence a uma única REF. Para dar esta entrada, os lotes abaixo passam para a nova REF,
          com o saldo e o histórico. Lotes que já saíram em cirurgia não podem mudar de REF.
        </Typography>
        <Stack spacing={2}>
          {groups.map(({ item, lots }) => (
            <Paper key={`${item.material.id}-${item.lot}`} variant="outlined" sx={{ p: 2 }}>
              <Typography sx={{ fontWeight: 600, mb: 0.5 }}>
                Lote <span style={mono}>{item.lot.trim().toUpperCase()}</span> entrando com a REF{' '}
                <span style={mono}>{item.material.ref}</span>
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>{item.material.description}</Typography>
              {blocked({ lots }) && (
                <Alert severity="error" sx={{ mb: 1.5 }}>
                  Este lote já saiu em cirurgia com a REF atual e não pode mudar de REF. Confira a REF do item que está
                  entrando; ele não será adicionado.
                </Alert>
              )}
              <Stack divider={<Box sx={{ borderTop: `1px solid ${tokens.divider}` }} />}>
                {lots.map((l) => (
                  <Box key={l.lotId} sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', gap: 1, py: 1 }}>
                    <Box sx={{ minWidth: 0 }}>
                      <Typography variant="body2">
                        REF atual <span style={{ ...mono, fontWeight: 600 }}>{l.ref}</span>
                        {' → '}<span style={{ ...mono, fontWeight: 600, color: tokens.primary }}>{item.material.ref}</span>
                      </Typography>
                      <Typography variant="body2" color="text.secondary">{l.description} · Val. {formatDate(l.expiryDate)}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {l.balances.length ? l.balances.map((b) => `${where(b)}: ${b.quantity}`).join(' · ') : 'Sem saldo'}
                      </Typography>
                    </Box>
                    <Box>
                      {l.usedInSurgery
                        ? <StatusChip tone="error">Já saiu em cirurgia</StatusChip>
                        : <StatusChip tone="warning">{l.units} {l.units === 1 ? 'unidade' : 'unidades'}</StatusChip>}
                    </Box>
                  </Box>
                ))}
              </Stack>
            </Paper>
          ))}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
        <Button variant="outlined" onClick={onCancel}>Cancelar</Button>
        <Button variant="contained" onClick={onConfirm} disabled={allowed === 0}>
          {groups.length > 1 && allowed < groups.length ? `Confirmar troca (${allowed} de ${groups.length})` : 'Confirmar troca de REF'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
