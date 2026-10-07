import { useEffect, useMemo, useState } from 'react';
import {
  Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Paper, Stack, TextField,
  Typography, useMediaQuery,
} from '@mui/material';
import StatusChip from '../../components/StatusChip';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';
import { lotKey } from '../../utils/lot';

const STATUS = {
  OK: { tone: 'success', label: 'Pronto' },
  NOT_FOUND: { tone: 'warning', label: 'Não está no estoque · vira pendência' },
  AMBIGUOUS: { tone: 'warning', label: 'Mais de uma validade · escolha ou vira pendência' },
  EXPIRED: { tone: 'error', label: 'Vencido · vira pendência' },
  NO_BALANCE: { tone: 'warning', label: 'Sem saldo no hospital · vira pendência' },
  NO_LOT: { tone: 'info', label: 'Lote não lido · escolha ou digite' },
};
const mono = { fontFamily: tokens.mono, fontSize: 13 };

/**
 * Review of the labels read from the sheet before recording them. Every label comes checked (labels that are not
 * in the hospital stock become pending issues), except the ones already recorded in this surgery.
 * Labels without a readable lot need a lot: one of the hospital lots of the REF, or typed.
 */
export default function SheetReview({ rows, recordedByLot, busy, onCancel, onConfirm }) {
  const fullScreen = useMediaQuery('(max-width:899.95px)');
  const [state, setState] = useState({});

  useEffect(() => {
    if (!rows) return;
    const seen = {};
    const initial = {};
    rows.forEach((r) => {
      const lot = lotKey(r.preview?.lot || r.lot);
      seen[lot] = (seen[lot] || 0) + 1;
      const already = lot && (recordedByLot[lot] || 0) >= seen[lot];
      initial[r.key] = { include: !already, already, lotId: '', typed: '' };
    });
    setState(initial);
    // recordedByLot is read when the review opens
  }, [rows]);

  const set = (key, patch) => setState((s) => ({ ...s, [key]: { ...s[key], ...patch } }));

  const problems = useMemo(() => (rows || []).filter((r) => {
    const st = state[r.key];
    return st?.include && r.preview?.status === 'NO_LOT' && !st.lotId && !st.typed.trim();
  }), [rows, state]);

  const included = (rows || []).filter((r) => state[r.key]?.include);

  const confirm = () => {
    onConfirm(included.map((r) => {
      const st = state[r.key];
      if (st.lotId) return { lotId: st.lotId, lot: null, gtin: r.gtin, code: null, ref: r.preview?.ref || null };
      if (st.typed.trim()) return { lot: st.typed.trim().toUpperCase(), gtin: r.gtin, code: null, ref: r.preview?.ref || null, lotId: null };
      return { lot: r.lot, gtin: r.gtin, code: r.code, ref: null, lotId: null };
    }));
  };

  const pages = new Set((rows || []).map((r) => r.page)).size;

  return (
    <Dialog open={Boolean(rows)} onClose={busy ? undefined : onCancel} fullWidth maxWidth="md" fullScreen={fullScreen}>
      <DialogTitle sx={{ typography: 'h2' }}>Conferir etiquetas da ficha</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={1.5}>
          <Typography variant="body2" color="text.secondary">
            {rows?.length} {rows?.length === 1 ? 'etiqueta lida' : 'etiquetas lidas'}
            {pages > 1 ? ` em ${pages} páginas` : ''}. Cada etiqueta conta como 1 item. Desmarque o que não deve ser lançado.
          </Typography>
          {(rows || []).map((r) => {
            const p = r.preview || {};
            const st = state[r.key] || {};
            const status = STATUS[p.status] || STATUS.NOT_FOUND;
            const name = p.ref ? (p.component ? `${p.component}${p.size ? ` · ${p.size}` : ''}` : p.description) : 'Material não identificado';
            const needsChoice = (p.status === 'NO_LOT' || p.status === 'AMBIGUOUS' || p.status === 'NOT_FOUND')
              && (p.options || []).length > 0;
            return (
              <Paper key={r.key} variant="outlined" sx={{ p: 1.5, opacity: st.include ? 1 : 0.6 }}>
                <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
                  <Checkbox checked={Boolean(st.include)} onChange={(e) => set(r.key, { include: e.target.checked })}
                    inputProps={{ 'aria-label': `Lançar ${name}, lote ${p.lot || r.lot || 'não lido'}` }} sx={{ mt: -0.75, ml: -0.75 }} />
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{name}</Typography>
                    <Typography variant="body2" color="text.secondary" sx={mono}>
                      {p.ref ? `${p.ref} · ` : ''}{p.lot || r.lot || 'lote não lido'}
                      {p.expiryDate ? ` · Val. ${formatDate(p.expiryDate)}` : ''}
                    </Typography>
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mt: 0.75 }}>
                      <StatusChip tone={status.tone}>{status.label}</StatusChip>
                      {st.already && <StatusChip tone="neutral">Já lançado nesta cirurgia</StatusChip>}
                      {pages > 1 && <StatusChip tone="neutral">Página {r.page}</StatusChip>}
                    </Box>
                    {st.include && (needsChoice || p.status === 'NO_LOT') && (
                      <Box sx={{ display: 'grid', gap: 1, mt: 1.25, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
                        {needsChoice && (
                          <TextField select size="small" label="Lote no hospital" value={st.lotId}
                            onChange={(e) => set(r.key, { lotId: e.target.value, typed: '' })}>
                            <MenuItem value=""><em>Não escolher</em></MenuItem>
                            {p.options.map((o) => (
                              <MenuItem key={o.lotId} value={o.lotId}>
                                {o.lot} · Val. {formatDate(o.expiryDate)} · {o.available} no hospital
                              </MenuItem>
                            ))}
                          </TextField>
                        )}
                        {p.status === 'NO_LOT' && (
                          <TextField size="small" label="Ou digite o lote" value={st.typed} autoComplete="off"
                            onChange={(e) => set(r.key, { typed: e.target.value.toUpperCase(), lotId: '' })} />
                        )}
                      </Box>
                    )}
                  </Box>
                </Box>
              </Paper>
            );
          })}
          {problems.length > 0 && (
            <Alert severity="warning">
              Escolha ou digite o lote das etiquetas sem lote lido, ou desmarque-as.
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button variant="outlined" onClick={onCancel} disabled={busy}>Não lançar agora</Button>
        <Button variant="contained" onClick={confirm} disabled={busy || problems.length > 0 || included.length === 0}>
          Lançar {included.length} {included.length === 1 ? 'item' : 'itens'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
