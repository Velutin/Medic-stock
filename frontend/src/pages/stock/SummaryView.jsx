import { useEffect, useMemo, useState } from 'react';
import { Box, Chip, IconButton, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import HospitalSelect from '../../components/HospitalSelect';
import ColorSwatch from '../../components/ColorSwatch';
import { tokens } from '../../theme';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';

const ROWS_PER_PAGE = 10;

/**
 * One item (e.g. "Haste Cygnus SP"): sizes with quantity and REF, 10 per page.
 *
 * O que está a caminho aparece ao lado da quantidade, nunca somado a ela. A pergunta que esta tela responde
 * é "o que eu tenho na prateleira agora": um número só, juntando prateleira e estrada, mandaria o
 * instrumentador buscar material que não chegou.
 */
function Block({ block }) {
  const [page, setPage] = useState(0);
  const pages = Math.max(1, Math.ceil(block.items.length / ROWS_PER_PAGE));
  useEffect(() => { if (page >= pages) setPage(0); }, [page, pages]);
  const from = page * ROWS_PER_PAGE;
  const rows = block.items.slice(from, from + ROWS_PER_PAGE);

  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, mb: 0.5 }}>
        <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{block.name}</Typography>
        <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.75, flexShrink: 0 }}>
          <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{block.total}</Typography>
          {block.transit > 0 && (
            <Typography variant="caption" sx={{ color: tokens.primary, fontWeight: 600 }}>
              +{block.transit}
            </Typography>
          )}
        </Box>
      </Box>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Tamanho</TableCell><TableCell align="right">Qtd.</TableCell>
            {block.transit > 0 && <TableCell align="right">A caminho</TableCell>}
            <TableCell>REF</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((r) => (
            <TableRow key={r.materialId}>
              <TableCell>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}><ColorSwatch color={r.color} />{r.size || '—'}</Box>
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 600, color: r.quantity === 0 ? tokens.error : undefined }}>{r.quantity}</TableCell>
              {block.transit > 0 && (
                <TableCell align="right" sx={{ color: r.inTransit > 0 ? tokens.primary : tokens.textMuted, fontWeight: r.inTransit > 0 ? 600 : 400 }}>
                  {r.inTransit > 0 ? r.inTransit : '—'}
                </TableCell>
              )}
              <TableCell sx={{ fontFamily: tokens.mono, fontSize: 13 }}>{r.ref}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {pages > 1 && (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, mt: 1 }}>
          <Typography variant="caption" color="text.secondary">
            Página {page + 1} de {pages} · itens {from + 1}–{from + rows.length}
          </Typography>
          <Box>
            <IconButton size="small" aria-label={`Página anterior de ${block.name}`} disabled={page === 0}
              onClick={() => setPage(page - 1)}><ChevronLeft /></IconButton>
            <IconButton size="small" aria-label={`Próxima página de ${block.name}`} disabled={page >= pages - 1}
              onClick={() => setPage(page + 1)}><ChevronRight /></IconButton>
          </Box>
        </Box>
      )}
    </Paper>
  );
}

const sizeCompare = (a, b) => (a.size || '').localeCompare(b.size || '', 'pt-BR', { numeric: true });

/**
 * Stock summary without lots (surgical tech's view, also a tab for administrators and read-only users):
 * quantity inside the hospital, valid lots only, grouped by section and by item name.
 * Lists the REFs with an ideal in the hospital (zero shown in red) and any REF with balance.
 */
export default function SummaryView({ hospitals, hospitalId, onHospitalChange, reloadKey }) {
  const notify = useNotify();
  const [rows, setRows] = useState(null);
  const [term, setTerm] = useState('');
  const [section, setSection] = useState('ALL');
  const [loadedAt, setLoadedAt] = useState(null);

  useEffect(() => {
    if (!hospitalId) return;
    setRows(null);
    api('/stock/summary', { query: { hospitalId } })
      .then((data) => { setRows(data); setLoadedAt(new Date()); })
      .catch((err) => { notify.error(err); setRows([]); });
  }, [hospitalId, reloadKey, notify]);

  /** Sections present in the hospital list, in display order; items without a section go last. */
  const sections = useMemo(() => {
    if (!rows) return [];
    const map = new Map();
    rows.forEach((r) => {
      const key = r.sectionId ?? 'NONE';
      if (!map.has(key)) {
        map.set(key, { value: key, label: r.section || 'Sem seção', order: r.sectionId ? (r.sectionOrder ?? 0) : Infinity });
      }
    });
    return [...map.values()].sort((a, b) => a.order - b.order || a.label.localeCompare(b.label, 'pt-BR'));
  }, [rows]);

  useEffect(() => {
    if (section !== 'ALL' && !sections.some((s) => s.value === section)) setSection('ALL');
  }, [sections, section]);

  const groups = useMemo(() => {
    if (!rows) return [];
    const t = term.trim().toLowerCase();
    const visible = rows.filter((r) => !t || [r.ref, r.component, r.description, r.size]
      .some((v) => (v || '').toLowerCase().includes(t)));
    const shown = section === 'ALL' ? sections : sections.filter((s) => s.value === section);
    return shown.map((sec) => {
      const inSection = visible.filter((r) => (r.sectionId ?? 'NONE') === sec.value);
      const byName = new Map();
      inSection.forEach((r) => {
        const name = r.component || r.description;
        if (!byName.has(name)) byName.set(name, []);
        byName.get(name).push(r);
      });
      const blocks = [...byName.entries()]
        .map(([name, items]) => ({
          name,
          items: items.sort(sizeCompare),
          total: items.reduce((s, i) => s + i.quantity, 0),
          transit: items.reduce((s, i) => s + (i.inTransit || 0), 0),
        }))
        .sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));
      return {
        ...sec,
        blocks,
        units: inSection.reduce((s, r) => s + r.quantity, 0),
        transit: inSection.reduce((s, r) => s + (r.inTransit || 0), 0),
        count: blocks.length,
      };
    }).filter((g) => g.count > 0);
  }, [rows, term, section, sections]);

  const hospitalName = hospitals.find((h) => h.id === hospitalId)?.name;
  // Zero para o perfil Consulta, que não vê movimentação interna: a frase e as colunas somem com ele
  const transitTotal = (rows || []).reduce((s, r) => s + (r.inTransit || 0), 0);

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
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 2 }} role="group" aria-label="Filtrar por seção">
        {[{ value: 'ALL', label: 'Todas' }, ...sections].map((sec) => (
          <Chip key={sec.value} label={sec.label} clickable color={section === sec.value ? 'primary' : 'default'}
            variant={section === sec.value ? 'filled' : 'outlined'} onClick={() => setSection(sec.value)}
            aria-pressed={section === sec.value} />
        ))}
      </Box>
      {hospitalName && loadedAt && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
          {hospitalName} · atualizado às {loadedAt.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}.
          Mostra o que tem estoque ideal no hospital e o que está dentro dele, sem lotes vencidos.
          {transitTotal > 0 && ' A coluna “A caminho” é material já despachado que ainda não foi confirmado na chegada; ele não entra na quantidade.'}
        </Typography>
      )}

      {rows === null ? (
        <Stack spacing={1}>{[1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={72} />)}</Stack>
      ) : groups.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
          {term ? 'Nenhum item encontrado para esta busca.' : 'Nenhum item com estoque ideal ou saldo neste hospital.'}
        </Typography>
      ) : (
        <Stack spacing={3}>
          {groups.map((g) => (
            <Box key={g.value} component="section" aria-label={g.label}>
              <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 1,
                borderBottom: `2px solid ${tokens.primary}`, pb: 0.75, mb: 1.5 }}>
                <Typography variant="h3" component="h3">{g.label}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {g.count} {g.count === 1 ? 'item' : 'itens'} · {g.units} unidades
                  {g.transit > 0 && (
                    <Typography component="span" variant="caption" sx={{ color: tokens.primary, fontWeight: 600 }}>
                      {' '}· {g.transit} a caminho
                    </Typography>
                  )}
                </Typography>
              </Box>
              <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)', xl: 'repeat(3, 1fr)' } }}>
                {g.blocks.map((b) => <Block key={b.name} block={b} />)}
              </Box>
            </Box>
          ))}
        </Stack>
      )}
    </Paper>
  );
}
