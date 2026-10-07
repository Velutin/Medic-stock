import { memo, useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Box, Button, Chip, IconButton, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableHead, TableRow,
  TextField, Typography,
} from '@mui/material';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';
import UploadFileOutlined from '@mui/icons-material/UploadFileOutlined';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import ColorSwatch from '../../components/ColorSwatch';
import StatusChip from '../../components/StatusChip';
import { tokens } from '../../theme';
import MinimumsImportDialog from './MinimumsImportDialog';

const PAGE = 10;
const sizeCompare = (a, b) => (a.size || '').localeCompare(b.size || '', 'pt-BR', { numeric: true });

/** Situation of a REF against its levels (with the values being edited). */
function situation(r, ideal, total) {
  if (ideal === 0 && total === 0) return { tone: 'neutral', label: 'Sem ideal' };
  if (r.hospitalBalance < ideal && r.storeroomBalance > 0) return { tone: 'warning', label: 'Repor da sala' };
  if (r.hospitalBalance + r.storeroomBalance < total) return { tone: 'error', label: 'Pedir à empresa' };
  if (r.hospitalBalance < ideal) return { tone: 'error', label: 'Abaixo do ideal' };
  return { tone: 'success', label: 'OK' };
}

/** Light numeric input: the table may have hundreds of them, so it avoids the cost of a full MUI TextField. */
const levelInput = (value, onChange, label, disabled) => (
  <Box component="input" type="number" min={0} step={1} value={value} disabled={disabled} aria-label={label}
    onChange={(e) => onChange(e.target.value)}
    sx={{ width: 64, height: 34, px: 1, textAlign: 'right', font: 'inherit', fontSize: 14, color: 'text.primary',
      border: `1px solid ${tokens.border}`, borderRadius: '6px', backgroundColor: disabled ? tokens.background : '#FFFFFF',
      '&:focus': { outline: `2px solid ${tokens.primary}`, outlineOffset: -1, borderColor: tokens.primary } }} />
);

/** One item (e.g. "Haste Cygnus SP") with its sizes, 10 per page. */
const Block = memo(function Block({ block, edits, setEdit, isCenter }) {
  const [page, setPage] = useState(0);
  const pages = Math.max(1, Math.ceil(block.items.length / PAGE));
  useEffect(() => { if (page >= pages) setPage(0); }, [page, pages]);
  const rows = block.items.slice(page * PAGE, page * PAGE + PAGE);

  return (
    <Paper variant="outlined" sx={{ p: 1.5, minWidth: 0 }}>
      <Typography sx={{ fontWeight: 600, fontSize: 14, mb: 0.5 }}>{block.name}</Typography>
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small" sx={{ minWidth: 640 }}>
          <TableHead>
            <TableRow>
              <TableCell>REF</TableCell><TableCell>Tamanho</TableCell><TableCell align="right">No hospital</TableCell>
              <TableCell align="right">Ideal</TableCell><TableCell align="right">Hospital + sala</TableCell>
              <TableCell align="right">Ideal total</TableCell><TableCell>Situação</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((r) => {
              const e = edits[r.materialId];
              const ideal = e ? e.hospitalIdeal : String(r.hospitalIdeal);
              const total = e ? e.idealTotal : String(r.idealTotal);
              const invalid = Number(total) < Number(ideal);
              const st = situation(r, Number(ideal) || 0, Number(total) || 0);
              return (
                <TableRow key={r.materialId} hover selected={Boolean(e)}>
                  <TableCell sx={{ fontFamily: tokens.mono, fontSize: 13 }}>{r.ref}</TableCell>
                  <TableCell><Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}><ColorSwatch color={r.color} />{r.size || '—'}</Box></TableCell>
                  <TableCell align="right">{r.hospitalBalance}</TableCell>
                  <TableCell align="right" sx={{ py: 0.5 }}>
                    {levelInput(ideal, (v) => setEdit(r, { hospitalIdeal: v, idealTotal: total }), `Ideal de ${r.ref}`, isCenter)}
                  </TableCell>
                  <TableCell align="right">{r.hospitalBalance + r.storeroomBalance}</TableCell>
                  <TableCell align="right" sx={{ py: 0.5 }}>
                    {levelInput(total, (v) => setEdit(r, { hospitalIdeal: ideal, idealTotal: v }), `Ideal total de ${r.ref}`)}
                  </TableCell>
                  <TableCell>
                    {invalid ? <StatusChip tone="error">Total menor que o ideal</StatusChip>
                      : <StatusChip tone={st.tone}>{st.label}</StatusChip>}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Box>
      {pages > 1 && (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 1 }}>
          <Typography variant="caption" color="text.secondary">
            Página {page + 1} de {pages} · itens {page * PAGE + 1}–{page * PAGE + rows.length}
          </Typography>
          <Box>
            <IconButton size="small" aria-label={`Página anterior de ${block.name}`} disabled={page === 0} onClick={() => setPage(page - 1)}><ChevronLeft /></IconButton>
            <IconButton size="small" aria-label={`Próxima página de ${block.name}`} disabled={page >= pages - 1} onClick={() => setPage(page + 1)}><ChevronRight /></IconButton>
          </Box>
        </Box>
      )}
    </Paper>
  );
}, (prev, next) => prev.block === next.block && prev.isCenter === next.isCenter && prev.setEdit === next.setEdit
  // re-renders only when one of its own REFs was edited
  && prev.block.items.every((i) => prev.edits[i.materialId] === next.edits[i.materialId]));

/**
 * Minimum levels of the hospital: Ideal (inside the hospital; below it, replenish from the storeroom) and
 * Ideal total (hospital + storeroom; below it, order from the supplier). An ideal greater than 0 also makes the
 * REF appear in the hospital stock by material. Grouped by section and item, like the stock by material.
 */
export default function MinimumsTab({ hospital }) {
  const notify = useNotify();
  const isCenter = hospital.type === 'DISTRIBUTION_CENTER';
  const [rows, setRows] = useState(null);
  const [edits, setEdits] = useState({});
  const [term, setTerm] = useState('');
  const [section, setSection] = useState('ALL');
  const [saving, setSaving] = useState(false);
  const [importOpen, setImportOpen] = useState(false);

  const load = useCallback(async () => {
    setRows(null);
    try {
      setRows(await api(`/hospitals/${hospital.id}/stock-levels`));
    } catch (err) {
      notify.error(err);
      setRows([]);
    }
  }, [hospital.id, notify]);

  useEffect(() => { setEdits({}); setSection('ALL'); load(); }, [load]);

  /** Keeps the change only when it differs from the saved levels. */
  const setEdit = useCallback((r, next) => setEdits((all) => {
    const copy = { ...all };
    if (Number(next.hospitalIdeal) === r.hospitalIdeal && Number(next.idealTotal) === r.idealTotal) delete copy[r.materialId];
    else copy[r.materialId] = next;
    return copy;
  }), []);

  const sections = useMemo(() => {
    const map = new Map();
    (rows || []).forEach((r) => {
      const key = r.sectionId ?? 'NONE';
      if (!map.has(key)) map.set(key, { value: key, label: r.section || 'Sem seção', order: r.sectionId ? (r.sectionOrder ?? 0) : Infinity });
    });
    return [...map.values()].sort((a, b) => a.order - b.order || a.label.localeCompare(b.label, 'pt-BR'));
  }, [rows]);

  const groups = useMemo(() => {
    const t = term.trim().toLowerCase();
    const visible = (rows || []).filter((r) => !t || [r.ref, r.component, r.description, r.size]
      .some((v) => (v || '').toLowerCase().includes(t)));
    return (section === 'ALL' ? sections : sections.filter((s) => s.value === section)).map((sec) => {
      const byName = new Map();
      visible.filter((r) => (r.sectionId ?? 'NONE') === sec.value).forEach((r) => {
        const name = r.component || r.description;
        if (!byName.has(name)) byName.set(name, []);
        byName.get(name).push(r);
      });
      const blocks = [...byName.entries()].map(([name, items]) => ({ name, items: items.sort(sizeCompare) }))
        .sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));
      return { ...sec, blocks };
    }).filter((g) => g.blocks.length);
  }, [rows, term, section, sections]);

  const changed = Object.entries(edits);
  const invalid = changed.some(([, e]) => !/^\d+$/.test(String(e.hospitalIdeal)) || !/^\d+$/.test(String(e.idealTotal))
    || Number(e.idealTotal) < Number(e.hospitalIdeal));

  const save = async (items) => {
    setSaving(true);
    try {
      await api(`/hospitals/${hospital.id}/minimums`, { method: 'PATCH', body: { items } });
      notify.success(`${items.length} ${items.length === 1 ? 'REF atualizada' : 'REFs atualizadas'}.`);
      setEdits({});
      await load();
      return true;
    } catch (err) {
      notify.error(err);
      return false;
    } finally {
      setSaving(false);
    }
  };

  const saveEdits = () => save(changed.map(([materialId, e]) => ({
    materialId: Number(materialId), hospitalIdeal: Number(e.hospitalIdeal), idealTotal: Number(e.idealTotal),
  })));

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 } }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'center', mb: 2 }}>
        <TextField type="search" size="small" label="Buscar" placeholder="REF ou material" value={term}
          onChange={(e) => setTerm(e.target.value)} sx={{ flex: '1 1 240px' }} />
        <Button variant="outlined" startIcon={<UploadFileOutlined />} onClick={() => setImportOpen(true)}>
          Importar planilha de mínimos
        </Button>
        <Button variant="contained" onClick={saveEdits} disabled={saving || changed.length === 0 || invalid}>
          Salvar alterações{changed.length ? ` (${changed.length})` : ''}
        </Button>
      </Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
        {isCenter
          ? <><strong>Ideal total</strong>: mínimo na sala do centro + hospitais atendidos. Abaixo dele, solicita à empresa. Centros de distribuição usam só o ideal total.</>
          : <><strong>Ideal</strong>: mínimo no hospital. Abaixo dele, repõe da sala. <strong>Ideal total</strong>: mínimo no hospital + sala. Abaixo dele, solicita à empresa. Ideal maior que 0 faz o item aparecer no estoque por material.</>}
      </Typography>
      {invalid && <Alert severity="warning" sx={{ mb: 1.5 }}>O ideal total não pode ser menor que o ideal, e os valores devem ser números inteiros.</Alert>}
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 2 }} role="group" aria-label="Filtrar por seção">
        {[{ value: 'ALL', label: 'Todas' }, ...sections].map((s) => (
          <Chip key={s.value} label={s.label} clickable color={section === s.value ? 'primary' : 'default'}
            variant={section === s.value ? 'filled' : 'outlined'} onClick={() => setSection(s.value)} aria-pressed={section === s.value} />
        ))}
      </Box>

      {rows === null ? (
        <Stack spacing={1}>{[1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={120} />)}</Stack>
      ) : groups.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
          {term ? 'Nenhum item encontrado para esta busca.' : 'Nenhum item nas linhas deste hospital. Confira as linhas no cadastro do hospital.'}
        </Typography>
      ) : (
        <Stack spacing={2.5}>
          {groups.map((g) => (
            <Box key={g.value} component="section">
              <Typography variant="h3" component="h2" sx={{ borderBottom: `1.5px solid ${tokens.primary}`, pb: 0.5, mb: 1.5 }}>
                {g.label}
              </Typography>
              <Stack spacing={1.5}>
                {g.blocks.map((b) => <Block key={b.name} block={b} edits={edits} setEdit={setEdit} isCenter={isCenter} />)}
              </Stack>
            </Box>
          ))}
        </Stack>
      )}

      <MinimumsImportDialog open={importOpen} hospital={hospital} onClose={() => setImportOpen(false)}
        onApply={async (items) => { if (await save(items)) setImportOpen(false); }} saving={saving} />
    </Paper>
  );
}
