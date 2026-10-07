import { useState } from 'react';
import { Box, Button, IconButton, Popover, Stack, Typography } from '@mui/material';
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';
import { tokens } from '../../theme';

const SHORT = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
export const currentMonth = () => new Date().toLocaleDateString('sv-SE').slice(0, 7);
const key = (year, m) => `${year}-${String(m + 1).padStart(2, '0')}`;

/** Readable label of the selected months: "out/2026", "set a out/2026" (consecutive) or "3 meses". */
export function periodLabel(months) {
  const sorted = [...months].sort();
  if (sorted.length === 0) return 'Escolha o período';
  const fmt = (k) => `${SHORT[Number(k.slice(5)) - 1]}/${k.slice(0, 4)}`;
  if (sorted.length === 1) return fmt(sorted[0]);
  const idx = (k) => Number(k.slice(0, 4)) * 12 + Number(k.slice(5));
  const consecutive = sorted.every((k, i) => i === 0 || idx(k) === idx(sorted[i - 1]) + 1);
  return consecutive ? `${fmt(sorted[0])} a ${fmt(sorted[sorted.length - 1])}` : `${sorted.length} meses`;
}

/** Several months can be chosen; future months are disabled. */
export default function MonthPicker({ value, onChange }) {
  const [anchor, setAnchor] = useState(null);
  const [draft, setDraft] = useState(value);
  const [year, setYear] = useState(Number((value[0] || currentMonth()).slice(0, 4)));
  const now = currentMonth();

  const open = (e) => {
    setDraft(value);
    setYear(Number(([...value].sort().pop() || now).slice(0, 4)));
    setAnchor(e.currentTarget);
  };
  const toggle = (k) => setDraft((d) => (d.includes(k) ? d.filter((x) => x !== k) : [...d, k]));
  const apply = () => {
    if (draft.length) onChange([...draft].sort());
    setAnchor(null);
  };

  return (
    <>
      <Button variant="outlined" startIcon={<CalendarMonthOutlined />} onClick={open} sx={{ minHeight: 40 }}
        aria-haspopup="dialog" aria-label={`Período: ${periodLabel(value)}`}>
        {periodLabel(value)}
      </Button>
      <Popover open={Boolean(anchor)} anchorEl={anchor} onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}>
        <Box sx={{ p: 2, width: 300 }} role="dialog" aria-label="Escolher meses">
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
            <IconButton size="small" aria-label="Ano anterior" onClick={() => setYear(year - 1)}><ChevronLeft /></IconButton>
            <Typography sx={{ fontWeight: 600 }}>{year}</Typography>
            <IconButton size="small" aria-label="Próximo ano" onClick={() => setYear(year + 1)}
              disabled={year >= Number(now.slice(0, 4))}><ChevronRight /></IconButton>
          </Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 0.75 }}>
            {SHORT.map((m, i) => {
              const k = key(year, i);
              const selected = draft.includes(k);
              const future = k > now;
              return (
                <Button key={k} size="small" disabled={future} onClick={() => toggle(k)} aria-pressed={selected}
                  variant={selected ? 'contained' : 'outlined'}
                  sx={{ minWidth: 0, minHeight: 36, textTransform: 'none', ...(selected ? {} : { borderColor: tokens.border }) }}>
                  {m}
                </Button>
              );
            })}
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            Clique para marcar ou desmarcar meses. É possível escolher vários.
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 1.5, justifyContent: 'flex-end' }}>
            <Button size="small" onClick={() => { setDraft([now]); setYear(Number(now.slice(0, 4))); }}>Mês atual</Button>
            <Button size="small" variant="contained" onClick={apply} disabled={draft.length === 0}>Aplicar</Button>
          </Stack>
        </Box>
      </Popover>
    </>
  );
}
