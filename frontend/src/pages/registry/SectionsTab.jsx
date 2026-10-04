import { useCallback, useEffect, useState } from 'react';
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton, Paper, Skeleton, Stack,
  Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import EditOutlined from '@mui/icons-material/EditOutlined';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import StatusChip from '../../components/StatusChip';
import ConfirmDialog from '../../components/ConfirmDialog';
import { tokens } from '../../theme';

const EMPTY = { name: '', displayOrder: '', active: true };

function SectionDialog({ open, section, nextOrder, onClose, onSaved }) {
  const notify = useNotify();
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setErrors({});
    setForm(section
      ? { name: section.name, displayOrder: String(section.displayOrder), active: section.active }
      : { ...EMPTY, displayOrder: String(nextOrder) });
  }, [open, section, nextOrder]);

  const submit = async (e) => {
    e.preventDefault();
    const found = {};
    if (!form.name.trim()) found.name = 'Informe o nome da seção.';
    if (!/^\d+$/.test(String(form.displayOrder).trim())) found.displayOrder = 'Informe um número inteiro.';
    setErrors(found);
    if (Object.keys(found).length) return;
    const body = { name: form.name.trim(), displayOrder: Number(form.displayOrder), active: form.active };
    setSaving(true);
    try {
      if (section) await api(`/product-sections/${section.id}`, { method: 'PUT', body });
      else await api('/product-sections', { method: 'POST', body });
      notify.success(section ? 'Seção atualizada.' : 'Seção cadastrada.');
      onSaved();
    } catch (err) {
      setErrors(err.fields || {});
      notify.error(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <Box component="form" onSubmit={submit} noValidate>
        <DialogTitle sx={{ typography: 'h2' }}>{section ? 'Editar seção' : 'Nova seção'}</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.25}>
            <TextField label="Nome" placeholder="Ex.: Quadril não cimentada" value={form.name} autoFocus
              onChange={(e) => setForm({ ...form, name: e.target.value })} error={Boolean(errors.name)} helperText={errors.name} />
            <TextField label="Ordem na tela" type="number" value={form.displayOrder} inputProps={{ min: 0, step: 1 }}
              onChange={(e) => setForm({ ...form, displayOrder: e.target.value })}
              error={Boolean(errors.displayOrder)} helperText={errors.displayOrder || 'Menor número aparece primeiro.'} />
            <FormControlLabel control={<Switch checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />}
              label={form.active ? 'Ativa' : 'Inativa'} />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button variant="outlined" onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={saving}>{section ? 'Salvar alterações' : 'Cadastrar'}</Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

/** Catalog sections (e.g. "Quadril não cimentada"), which group the hospital stock by material. */
export default function SectionsTab() {
  const notify = useNotify();
  const [rows, setRows] = useState(null);
  const [dialog, setDialog] = useState({ open: false, section: null });
  const [removing, setRemoving] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setRows(await api('/product-sections'));
    } catch (err) {
      notify.error(err);
      setRows([]);
    }
  }, [notify]);

  useEffect(() => { load(); }, [load]);

  const nextOrder = rows && rows.length ? Math.max(...rows.map((r) => r.displayOrder)) + 1 : 1;

  const remove = async () => {
    setBusy(true);
    try {
      await api(`/product-sections/${removing.id}`, { method: 'DELETE' });
      notify.success('Seção removida.');
      setRemoving(null);
      load();
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 } }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 560 }}>
          As seções agrupam o estoque por material, como na planilha dos hospitais. Defina a seção de cada item em Itens.
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ open: true, section: null })}>Nova seção</Button>
      </Box>

      {rows === null ? (
        <Stack spacing={1}>{[1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={44} />)}</Stack>
      ) : rows.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>Nenhuma seção cadastrada.</Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 80 }}>Ordem</TableCell><TableCell>Nome</TableCell>
                <TableCell align="right">Itens</TableCell><TableCell>Situação</TableCell><TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((s) => (
                <TableRow key={s.id} hover>
                  <TableCell sx={{ fontFamily: tokens.mono, fontSize: 13 }}>{s.displayOrder}</TableCell>
                  <TableCell>{s.name}</TableCell>
                  <TableCell align="right">{s.materials}</TableCell>
                  <TableCell><StatusChip tone={s.active ? 'success' : 'neutral'}>{s.active ? 'Ativa' : 'Inativa'}</StatusChip></TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Button size="small" variant="outlined" startIcon={<EditOutlined />} sx={{ minHeight: 36 }}
                      onClick={() => setDialog({ open: true, section: s })}>Editar</Button>
                    <Tooltip title={s.materials > 0 ? 'Seção com itens: desative em vez de remover' : 'Remover'}>
                      <span>
                        <IconButton aria-label={`Remover ${s.name}`} disabled={s.materials > 0} onClick={() => setRemoving(s)}
                          sx={{ color: tokens.error, ml: 0.5 }}>
                          <DeleteOutline fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <SectionDialog open={dialog.open} section={dialog.section} nextOrder={nextOrder}
        onClose={() => setDialog({ open: false, section: null })}
        onSaved={() => { setDialog({ open: false, section: null }); load(); }} />
      <ConfirmDialog open={Boolean(removing)} title="Remover seção" danger busy={busy} confirmLabel="Remover"
        message={removing ? `A seção "${removing.name}" será removida.` : ''} onConfirm={remove} onClose={() => setRemoving(null)} />
    </Paper>
  );
}
