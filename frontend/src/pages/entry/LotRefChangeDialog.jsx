import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Link, Paper, Stack, Typography, useMediaQuery,
} from '@mui/material';
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined';
import StatusChip from '../../components/StatusChip';
import { API_BASE } from '../../config';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';

const mono = { fontFamily: tokens.mono, fontSize: 13 };
const where = (b) => `${b.hospital}${b.location === 'STOREROOM' ? ' (sala)' : ''}`;
const SURGERY_STATUS = { OPEN: 'Em aberto', COMPLETED: 'Concluída' };

/**
 * Surgeries where the lot already went out (cancelled ones are not listed: they do not block the change).
 * Each one links to the consumption sheet attached to it, so the administrator can check what happened
 * before fixing the REF of the item being received.
 */
function SurgeryUses({ surgeries }) {
  if (!surgeries?.length) return null;
  return (
    <Box sx={{ mt: 1, pl: 1.5, borderLeft: `2px solid ${tokens.border}` }}>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
        {surgeries.length === 1 ? 'Saiu nesta cirurgia:' : `Saiu em ${surgeries.length} cirurgias:`}
      </Typography>
      <Stack spacing={0.75}>
        {surgeries.map((s) => (
          <Box key={s.surgeryId} sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1 }}>
            <Typography variant="body2">
              {formatDate(s.surgeryDate)} · {s.hospital} · {s.patient}
              {s.quantity > 1 ? ` · ${s.quantity} unidades` : ''}
            </Typography>
            <StatusChip tone={s.status === 'COMPLETED' ? 'success' : 'warning'}>
              {SURGERY_STATUS[s.status] || s.status}
            </StatusChip>
            {s.hasSheet ? (
              <Link href={`${API_BASE}/surgeries/${s.surgeryId}/sheet`} target="_blank" rel="noopener"
                variant="body2" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.25 }}>
                <DescriptionOutlined fontSize="inherit" /> Ver ficha
              </Link>
            ) : (
              <Typography variant="caption" color="text.secondary">Sem ficha anexada</Typography>
            )}
          </Box>
        ))}
      </Stack>
    </Box>
  );
}

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
                  entrando; ele não será adicionado. As cirurgias estão listadas abaixo, com a ficha de cada uma.
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
                    <Box sx={{ flexBasis: '100%' }}><SurgeryUses surgeries={l.surgeries} /></Box>
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
