import { useEffect, useMemo, useState } from 'react';
import { Box, Chip, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import HospitalSelect from '../../components/HospitalSelect';
import ColorSwatch from '../../components/ColorSwatch';
import { PRODUCT_LINES } from '../../utils/format';
import { tokens } from '../../theme';

const sizeCompare = (a, b) => (a.size || '').localeCompare(b.size || '', 'pt-BR', { numeric: true });

/**
 * Stock summary without lots (surgical tech's view, also a tab for administrators and read-only users):
 * quantity inside the hospital, valid lots only, grouped by product line and by item name.
 */
export default function SummaryView({ hospitals, hospitalId, onHospitalChange, reloadKey }) {
  const notify = useNotify();
  const [rows, setRows] = useState(null);
  const [term, setTerm] = useState('');
  const [line, setLine] = useState('ALL');
  const [loadedAt, setLoadedAt] = useState(null);

  useEffect(() => {
    if (!hospitalId) return;
    setRows(null);
    api('/stock/summary', { query: { hospitalId } })
      .then((data) => { setRows(data); setLoadedAt(new Date()); })
      .catch((err) => { notify.error(err); setRows([]); });
  }, [hospitalId, reloadKey, notify]);

  const groups = useMemo(() => {
    if (!rows) return [];
    const t = term.trim().toLowerCase();
    const visible = rows.filter((r) => !t || [r.ref, r.component, r.description, r.size]
      .some((v) => (v || '').toLowerCase().includes(t)));
    const lines = line === 'ALL' ? PRODUCT_LINES : PRODUCT_LINES.filter((l) => l.value === line);
    return lines.map((l) => {
      const inLine = visible.filter((r) => r.productLines.includes(l.value));
      const byName = new Map();
      inLine.forEach((r) => {
        const name = r.component || r.description;
        if (!byName.has(name)) byName.set(name, []);
        byName.get(name).push(r);
      });
      const blocks = [...byName.entries()]
        .map(([name, items]) => ({ name, items: items.sort(sizeCompare), total: items.reduce((s, i) => s + i.quantity, 0) }))
        .sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));
      return { ...l, blocks, units: inLine.reduce((s, r) => s + r.quantity, 0), count: inLine.length };
    }).filter((g) => g.count > 0);
  }, [rows, term, line]);

  const hospitalName = hospitals.find((h) => h.id === hospitalId)?.name;

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 } }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 1.5 }}>
        {hospitals.length > 1 && (
          <HospitalSelect hospitals={hospitals} value={hospitalId} onChange={onHospitalChange} size="small"
            sx={{ minWidth: { xs: '100%', sm: 260 } }} />
        )}
        <TextField type="search" size="small" label="Buscar" placeholder="REF, nome ou tamanho" value={term}
          onChange={(e) => setTerm(e.target.value)} sx={{ flex: 1, minWidth: { xs: '100%', sm: 240 } }} />
      </Box>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 2 }} role="group" aria-label="Filtrar por linha">
        {[{ value: 'ALL', label: 'Todas' }, ...PRODUCT_LINES].map((l) => (
          <Chip key={l.value} label={l.label} clickable color={line === l.value ? 'primary' : 'default'}
            variant={line === l.value ? 'filled' : 'outlined'} onClick={() => setLine(l.value)} aria-pressed={line === l.value} />
        ))}
      </Box>
      {hospitalName && loadedAt && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
          {hospitalName} · atualizado às {loadedAt.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}.
          Considera só o que está dentro do hospital, sem lotes vencidos.
        </Typography>
      )}

      {rows === null ? (
        <Stack spacing={1}>{[1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={72} />)}</Stack>
      ) : groups.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
          {term ? 'Nenhum item encontrado para esta busca.' : 'Nenhum item em estoque dentro deste hospital.'}
        </Typography>
      ) : (
        <Stack spacing={3}>
          {groups.map((g) => (
            <Box key={g.value} component="section" aria-label={g.label}>
              <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 1,
                borderBottom: `2px solid ${tokens.primary}`, pb: 0.75, mb: 1.5 }}>
                <Typography variant="h3" component="h3">{g.label}</Typography>
                <Typography variant="caption" color="text.secondary">{g.count} itens · {g.units} unidades</Typography>
              </Box>
              <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)', xl: 'repeat(3, 1fr)' } }}>
                {g.blocks.map((b) => (
                  <Paper key={b.name} variant="outlined" sx={{ p: 1.5 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, mb: 0.5 }}>
                      <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{b.name}</Typography>
                      <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{b.total}</Typography>
                    </Box>
                    <Table size="small">
                      <TableHead>
                        <TableRow><TableCell>Tamanho</TableCell><TableCell align="right">Qtd.</TableCell><TableCell>REF</TableCell></TableRow>
                      </TableHead>
                      <TableBody>
                        {b.items.map((r) => (
                          <TableRow key={r.materialId}>
                            <TableCell>
                              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}><ColorSwatch color={r.color} />{r.size || '—'}</Box>
                            </TableCell>
                            <TableCell align="right" sx={{ fontWeight: 600 }}>{r.quantity}</TableCell>
                            <TableCell sx={{ fontFamily: tokens.mono, fontSize: 13 }}>{r.ref}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </Paper>
                ))}
              </Box>
            </Box>
          ))}
        </Stack>
      )}
    </Paper>
  );
}
