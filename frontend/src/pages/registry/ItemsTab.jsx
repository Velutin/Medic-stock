import { useCallback, useEffect, useState } from 'react';
import {
  Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, FormControlLabel, FormHelperText,
  FormLabel, Link, MenuItem, Paper, Skeleton, Stack, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination,
  TableRow, TextField, Typography, useMediaQuery,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditOutlined from '@mui/icons-material/EditOutlined';
import UploadFileOutlined from '@mui/icons-material/UploadFileOutlined';
import { api } from '../../api/client';
import { uploadSpreadsheet } from '../../api/download';
import { useNotify } from '../../notifications/NotificationProvider';
import StatusChip from '../../components/StatusChip';
import ColorSwatch from '../../components/ColorSwatch';
import FileDropZone from '../../components/FileDropZone';
import ImportResultDialog from '../../components/ImportResultDialog';
import { PRODUCT_LINES, lineLabel } from '../../utils/format';
import { tokens } from '../../theme';

const EMPTY = { ref: '', description: '', productLines: [], component: '', size: '', color: '', gtin: '', active: true, sectionId: '' };
const mono = { fontFamily: tokens.mono, fontSize: 13 };

/**
 * Create or edit a catalog item. Exported so other screens (e.g. the stock entry) can register a new REF on the spot.
 * initial: pre-filled fields for a new item (e.g. { ref, gtin, description }); onSaved receives the saved item.
 */
export function ItemDialog({ open, item, initial, onClose, onSaved }) {
  const notify = useNotify();
  const fullScreen = useMediaQuery('(max-width:599.95px)');
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [sections, setSections] = useState([]);

  useEffect(() => {
    if (!open) return;
    api('/product-sections').then(setSections).catch(() => setSections([]));
  }, [open]);

  useEffect(() => {
    if (!open) return;
    setErrors({});
    setForm(item ? {
      ref: item.ref, description: item.description, productLines: item.productLines || [], component: item.component || '',
      size: item.size || '', color: item.color || '', gtin: item.gtin || '', active: item.active !== false,
      sectionId: item.sectionId || '',
    } : { ...EMPTY, ...initial });
    // initial is only read when the dialog opens
  }, [open, item]);

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));
  const toggleLine = (value) => setForm((f) => ({
    ...f, productLines: f.productLines.includes(value) ? f.productLines.filter((v) => v !== value) : [...f.productLines, value],
  }));

  const submit = async (e) => {
    e.preventDefault();
    const found = {};
    if (!form.ref.trim()) found.ref = 'Informe a REF.';
    if (!form.description.trim()) found.description = 'Informe a descrição.';
    if (form.productLines.length === 0) found.productLines = 'Escolha ao menos uma linha.';
    if (form.gtin && !/^(\d{8}|\d{12,14})$/.test(form.gtin.trim())) found.gtin = 'O GTIN deve ter 8, 12, 13 ou 14 dígitos.';
    setErrors(found);
    if (Object.keys(found).length) return;
    const body = {
      ref: form.ref.trim(), description: form.description.trim(), productLines: form.productLines,
      component: form.component.trim() || null, size: form.size.trim() || null, color: form.color || null,
      gtin: form.gtin.trim() || null, active: form.active, sectionId: form.sectionId || null,
    };
    setSaving(true);
    try {
      const saved = item
        ? await api(`/materials/${item.id}`, { method: 'PUT', body })
        : await api('/materials', { method: 'POST', body });
      notify.success(item ? 'Item atualizado.' : 'Item cadastrado.');
      onSaved(saved);
    } catch (err) {
      setErrors(err.fields || {});
      notify.error(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" fullScreen={fullScreen}>
      <Box component="form" onSubmit={submit} noValidate>
        <DialogTitle sx={{ typography: 'h2' }}>{item ? 'Editar item' : 'Novo item'}</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.25}>
            <Box sx={{ display: 'grid', gap: 2.25, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
              <TextField label="REF" value={form.ref} onChange={set('ref')} error={Boolean(errors.ref)} helperText={errors.ref} autoFocus />
              <TextField label="GTIN (código de barras)" value={form.gtin} onChange={set('gtin')} inputMode="numeric"
                error={Boolean(errors.gtin)} helperText={errors.gtin || 'Opcional. Identifica a REF na leitura do código.'} />
            </Box>
            <TextField label="Descrição" value={form.description} onChange={set('description')} error={Boolean(errors.description)}
              helperText={errors.description} />
            <Box sx={{ display: 'grid', gap: 2.25, gridTemplateColumns: { xs: '1fr', sm: '2fr 1fr' } }}>
              <TextField label="Nome curto (componente)" placeholder="Ex.: Haste Cygnus SP" value={form.component} onChange={set('component')} />
              <TextField label="Tamanho" value={form.size} onChange={set('size')} />
            </Box>
            <TextField select label="Seção" value={form.sectionId} onChange={set('sectionId')}
              helperText="Agrupa o item no estoque por material. Cadastre as seções na aba Seções.">
              <MenuItem value=""><em>Sem seção</em></MenuItem>
              {sections.filter((s) => s.active || s.id === form.sectionId).map((s) => (
                <MenuItem key={s.id} value={s.id}>{s.name}{s.active ? '' : ' (inativa)'}</MenuItem>
              ))}
            </TextField>
            <FormControl error={Boolean(errors.productLines)}>
              <FormLabel sx={{ fontSize: 13, fontWeight: 500 }}>Linhas</FormLabel>
              <Box sx={{ display: 'flex', flexWrap: 'wrap' }}>
                {PRODUCT_LINES.map((l) => (
                  <FormControlLabel key={l.value} label={l.label}
                    control={<Checkbox checked={form.productLines.includes(l.value)} onChange={() => toggleLine(l.value)} />} />
                ))}
              </Box>
              {errors.productLines && <FormHelperText>{errors.productLines}</FormHelperText>}
            </FormControl>
            <FormControl>
              <FormLabel sx={{ fontSize: 13, fontWeight: 500 }}>Cor do tamanho</FormLabel>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 0.5 }}>
                <input type="color" aria-label="Cor do tamanho" value={form.color || '#ffffff'} disabled={!form.color}
                  onChange={(e) => setForm((f) => ({ ...f, color: e.target.value.toUpperCase() }))}
                  style={{ width: 48, height: 40, border: `1px solid ${tokens.border}`, borderRadius: 6, background: 'none' }} />
                <FormControlLabel label="Sem cor" control={(
                  <Checkbox checked={!form.color} onChange={(e) => setForm((f) => ({ ...f, color: e.target.checked ? '' : '#BB7611' }))} />
                )} />
                {form.color && <Typography variant="body2" sx={mono}>{form.color}</Typography>}
              </Box>
            </FormControl>
            <FormControlLabel control={<Switch checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />}
              label={form.active ? 'Ativo' : 'Inativo'} />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button variant="outlined" onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={saving}>{item ? 'Salvar alterações' : 'Cadastrar'}</Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

function CatalogImportDialog({ open, onClose, onImported }) {
  const notify = useNotify();
  const [file, setFile] = useState(null);
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState(null);

  useEffect(() => { if (open) setFile(null); }, [open]);

  const submit = async () => {
    setSending(true);
    try {
      const res = await uploadSpreadsheet('/imports/materials', file);
      setResult(res);
      if (res.imported > 0) onImported();
    } catch (err) {
      notify.error(err);
    } finally {
      setSending(false);
    }
  };

  return (
    <>
      <Dialog open={open && !result} onClose={onClose} fullWidth maxWidth="sm">
        <DialogTitle sx={{ typography: 'h2' }}>Importar catálogo</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              Colunas: REF, DESCRIÇÃO e LINHA (QUADRIL, JOELHO e/ou OMBRO, separadas por vírgula). Opcionais: GTIN, COMPONENTE,
              TAMANHO, COR (#RRGGBB) e SEÇÃO (nome de uma seção já cadastrada). REFs já cadastradas são atualizadas.
            </Typography>
            <Link href="/modelos/catalogo.xlsx" download variant="body2">Baixar planilha modelo</Link>
            <FileDropZone title="Arraste a planilha do catálogo aqui" hint=".xlsx ou .xls" file={file} onFile={setFile} />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button variant="outlined" onClick={onClose}>Cancelar</Button>
          <Button variant="contained" onClick={submit} disabled={!file || sending}>Importar</Button>
        </DialogActions>
      </Dialog>
      <ImportResultDialog result={result} onClose={() => { setResult(null); onClose(); }} />
    </>
  );
}

/** Material catalog. */
export default function ItemsTab() {
  const notify = useNotify();
  const isMobile = useMediaQuery('(max-width:899.95px)');
  const [term, setTerm] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const [data, setData] = useState(null);
  const [dialog, setDialog] = useState({ open: false, item: null });
  const [importOpen, setImportOpen] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => { setQuery(term.trim()); setPage(0); }, 350);
    return () => clearTimeout(t);
  }, [term]);

  const load = useCallback(async () => {
    try {
      setData(await api('/materials', { query: { term: query, page, size } }));
    } catch (err) {
      notify.error(err);
      setData({ content: [], totalElements: 0 });
    }
  }, [query, page, size, notify]);

  useEffect(() => { load(); }, [load]);
  const rows = data?.content || [];
  const open = (item) => setDialog({ open: true, item });

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 } }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, justifyContent: 'space-between', mb: 2 }}>
        <TextField type="search" size="small" label="Buscar" placeholder="REF, nome ou descrição" value={term}
          onChange={(e) => setTerm(e.target.value)} sx={{ width: { xs: '100%', sm: 320 } }} />
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
          <Button variant="outlined" startIcon={<UploadFileOutlined />} onClick={() => setImportOpen(true)}>Importar planilha .xlsx</Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => open(null)}>Novo item</Button>
        </Box>
      </Box>

      {data === null ? (
        <Stack spacing={1}>{[1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={44} />)}</Stack>
      ) : rows.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
          {query ? 'Nenhum item encontrado para esta busca.' : 'Nenhum item cadastrado. Importe o catálogo ou cadastre um item.'}
        </Typography>
      ) : isMobile ? (
        <Stack spacing={1.25}>
          {rows.map((m) => (
            <Paper key={m.id} variant="outlined" sx={{ p: 1.5 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <ColorSwatch color={m.color} />
                <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{m.component || m.description}{m.size ? ` · ${m.size}` : ''}</Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">REF <span style={mono}>{m.ref}</span></Typography>
              <Typography variant="body2" color="text.secondary">{m.section || 'Sem seção'} · {m.productLines.map(lineLabel).join(', ')}</Typography>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 1 }}>
                <StatusChip tone={m.active ? 'success' : 'neutral'}>{m.active ? 'Ativo' : 'Inativo'}</StatusChip>
                <Button size="small" variant="outlined" startIcon={<EditOutlined />} onClick={() => open(m)}>Editar</Button>
              </Box>
            </Paper>
          ))}
        </Stack>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>REF</TableCell><TableCell>Descrição</TableCell><TableCell>Componente</TableCell>
                <TableCell>Tamanho</TableCell><TableCell>Cor</TableCell><TableCell>Seção</TableCell><TableCell>Linhas</TableCell><TableCell>GTIN</TableCell>
                <TableCell>Situação</TableCell><TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((m) => (
                <TableRow key={m.id} hover>
                  <TableCell sx={mono}>{m.ref}</TableCell>
                  <TableCell sx={{ maxWidth: 320 }}>{m.description}</TableCell>
                  <TableCell>{m.component}</TableCell>
                  <TableCell>{m.size}</TableCell>
                  <TableCell>{m.color ? <ColorSwatch color={m.color} /> : <Typography variant="caption" color="text.secondary">Sem cor</Typography>}</TableCell>
                  <TableCell>{m.section || <Typography variant="caption" color="warning.main">Sem seção</Typography>}</TableCell>
                  <TableCell>{m.productLines.length ? m.productLines.map(lineLabel).join(', ')
                    : <Typography variant="caption" color="warning.main">Sem linha</Typography>}</TableCell>
                  <TableCell sx={mono}>{m.gtin}</TableCell>
                  <TableCell><StatusChip tone={m.active ? 'success' : 'neutral'}>{m.active ? 'Ativo' : 'Inativo'}</StatusChip></TableCell>
                  <TableCell align="right">
                    <Button size="small" variant="outlined" startIcon={<EditOutlined />} onClick={() => open(m)} sx={{ minHeight: 36 }}>Editar</Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {data && data.totalElements > 0 && (
        <TablePagination component="div" count={data.totalElements} page={page} rowsPerPage={size}
          onPageChange={(_, p) => setPage(p)} onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}
          rowsPerPageOptions={[25, 50, 100]} labelRowsPerPage="Por página"
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
      )}

      <ItemDialog open={dialog.open} item={dialog.item} onClose={() => setDialog({ open: false, item: null })}
        onSaved={() => { setDialog({ open: false, item: null }); load(); }} />
      <CatalogImportDialog open={importOpen} onClose={() => setImportOpen(false)} onImported={load} />
    </Paper>
  );
}
