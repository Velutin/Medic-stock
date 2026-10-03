import { useEffect, useMemo, useState } from 'react';
import {
  Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, FormControlLabel, FormHelperText,
  FormLabel, MenuItem, Paper, Radio, RadioGroup, Stack, Switch, Table, TableBody, TableCell, TableContainer, TableHead,
  TableRow, TextField, Typography, useMediaQuery,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditOutlined from '@mui/icons-material/EditOutlined';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import StatusChip from '../../components/StatusChip';
import { PRICE_TABLES, PRODUCT_LINES, hospitalTypeLabel, lineLabel, priceTableLabel } from '../../utils/format';

const EMPTY = { name: '', acronym: '', type: 'HOSPITAL', priceTableType: '', active: true, productLines: [], coveredHospitalIds: [] };

function HospitalDialog({ open, hospital, hospitals, onClose, onSaved }) {
  const notify = useNotify();
  const fullScreen = useMediaQuery('(max-width:599.95px)');
  const editing = Boolean(hospital);
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setErrors({});
    setForm(hospital ? {
      name: hospital.name, acronym: hospital.acronym || '', type: hospital.type, priceTableType: hospital.priceTableType || '',
      active: hospital.active, productLines: hospital.productLines || [], coveredHospitalIds: hospital.coveredHospitalIds || [],
    } : EMPTY);
  }, [open, hospital]);

  const isCenter = form.type === 'DISTRIBUTION_CENTER';
  const coverable = hospitals.filter((h) => h.type === 'HOSPITAL'
    && (!h.distributionCenterId || h.distributionCenterId === hospital?.id));
  const toggle = (field, value) => setForm((f) => ({
    ...f, [field]: f[field].includes(value) ? f[field].filter((v) => v !== value) : [...f[field], value],
  }));

  const submit = async (e) => {
    e.preventDefault();
    if (!form.name.trim()) {
      setErrors({ name: 'Informe o nome.' });
      return;
    }
    const body = {
      name: form.name.trim(), acronym: form.acronym.trim() || null, type: form.type,
      priceTableType: form.priceTableType || null, active: form.active,
      productLines: isCenter ? [] : form.productLines,
    };
    setSaving(true);
    try {
      const saved = editing
        ? await api(`/hospitals/${hospital.id}`, { method: 'PUT', body })
        : await api('/hospitals', { method: 'POST', body });
      if (isCenter) {
        await api(`/hospitals/${saved.id}/covered-hospitals`, { method: 'PUT', body: { hospitalIds: form.coveredHospitalIds } });
      }
      notify.success(editing ? 'Cadastro atualizado.' : `${isCenter ? 'Centro de distribuição' : 'Hospital'} cadastrado.`);
      onSaved();
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
        <DialogTitle>
          <Typography variant="h2" component="span" sx={{ display: 'block' }}>{editing ? 'Editar cadastro' : 'Novo hospital'}</Typography>
          {!editing && <Typography variant="body2" color="text.secondary">Também serve para centros de distribuição, como a SESAB.</Typography>}
        </DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.25}>
            <Box sx={{ display: 'grid', gap: 2.25, gridTemplateColumns: { xs: '1fr', sm: '2fr 1fr' } }}>
              <TextField label="Nome" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
                error={Boolean(errors.name)} helperText={errors.name} autoFocus />
              <TextField label="Sigla" value={form.acronym} onChange={(e) => setForm({ ...form, acronym: e.target.value })}
                error={Boolean(errors.acronym)} helperText={errors.acronym} />
            </Box>
            <FormControl disabled={editing}>
              <FormLabel sx={{ fontSize: 13, fontWeight: 500 }}>Tipo</FormLabel>
              <RadioGroup row value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
                <FormControlLabel value="HOSPITAL" control={<Radio />} label="Hospital" />
                <FormControlLabel value="DISTRIBUTION_CENTER" control={<Radio />} label="Centro de distribuição" />
              </RadioGroup>
              {editing && <FormHelperText>O tipo é definido no cadastro e não pode ser alterado.</FormHelperText>}
            </FormControl>
            <TextField select label="Tabela de preços" value={form.priceTableType}
              onChange={(e) => setForm({ ...form, priceTableType: e.target.value })}>
              <MenuItem value="">Não definida</MenuItem>
              {PRICE_TABLES.map((p) => <MenuItem key={p.value} value={p.value}>{p.label}</MenuItem>)}
            </TextField>
            {!isCenter ? (
              <FormControl>
                <FormLabel sx={{ fontSize: 13, fontWeight: 500 }}>Linhas atendidas</FormLabel>
                <Box sx={{ display: 'flex', flexWrap: 'wrap' }}>
                  {PRODUCT_LINES.map((l) => (
                    <FormControlLabel key={l.value} label={l.label}
                      control={<Checkbox checked={form.productLines.includes(l.value)} onChange={() => toggle('productLines', l.value)} />} />
                  ))}
                </Box>
              </FormControl>
            ) : (
              <FormControl>
                <FormLabel sx={{ fontSize: 13, fontWeight: 500 }}>Hospitais atendidos</FormLabel>
                <FormHelperText sx={{ mx: 0 }}>Um hospital pode ser atendido por apenas um centro de distribuição.</FormHelperText>
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
                  {coverable.map((h) => (
                    <FormControlLabel key={h.id} label={h.name}
                      control={<Checkbox checked={form.coveredHospitalIds.includes(h.id)} onChange={() => toggle('coveredHospitalIds', h.id)} />} />
                  ))}
                </Box>
              </FormControl>
            )}
            <FormControlLabel control={<Switch checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />}
              label={form.active ? 'Ativo' : 'Inativo'} />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button variant="outlined" onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={saving}>{editing ? 'Salvar alterações' : 'Cadastrar'}</Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

/** Hospitals and distribution centers. */
export default function HospitalsTab({ hospitals, reload }) {
  const [term, setTerm] = useState('');
  const [dialog, setDialog] = useState({ open: false, hospital: null });
  const isMobile = useMediaQuery('(max-width:899.95px)');

  const filtered = useMemo(() => {
    const t = term.trim().toLowerCase();
    return hospitals.filter((h) => !t || h.name.toLowerCase().includes(t) || (h.acronym || '').toLowerCase().includes(t));
  }, [hospitals, term]);

  const linesText = (h) => (h.type === 'DISTRIBUTION_CENTER'
    ? `Atende ${h.coveredHospitalIds.length} hospital(is)`
    : h.productLines.map(lineLabel).join(', ') || '—');

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 } }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, justifyContent: 'space-between', mb: 2 }}>
        <TextField type="search" size="small" label="Buscar" placeholder="Nome ou sigla" value={term}
          onChange={(e) => setTerm(e.target.value)} sx={{ width: { xs: '100%', sm: 320 } }} />
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ open: true, hospital: null })}>Novo hospital</Button>
      </Box>
      {isMobile ? (
        <Stack spacing={1.25}>
          {filtered.map((h) => (
            <Paper key={h.id} variant="outlined" sx={{ p: 1.5 }}>
              <Typography sx={{ fontWeight: 600 }}>{h.name}{h.acronym ? ` (${h.acronym})` : ''}</Typography>
              <Typography variant="body2" color="text.secondary">
                {hospitalTypeLabel(h.type)} · {priceTableLabel(h.priceTableType)} · {linesText(h)}
              </Typography>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 1 }}>
                <StatusChip tone={h.active ? 'success' : 'neutral'}>{h.active ? 'Ativo' : 'Inativo'}</StatusChip>
                <Button size="small" variant="outlined" startIcon={<EditOutlined />} onClick={() => setDialog({ open: true, hospital: h })}>Editar</Button>
              </Box>
            </Paper>
          ))}
        </Stack>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Nome</TableCell><TableCell>Sigla</TableCell><TableCell>Tipo</TableCell>
                <TableCell>Tabela de preços</TableCell><TableCell>Linhas</TableCell><TableCell>Situação</TableCell><TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {filtered.map((h) => (
                <TableRow key={h.id} hover>
                  <TableCell sx={{ fontWeight: 500 }}>{h.name}</TableCell>
                  <TableCell>{h.acronym}</TableCell>
                  <TableCell>{hospitalTypeLabel(h.type)}</TableCell>
                  <TableCell>{priceTableLabel(h.priceTableType)}</TableCell>
                  <TableCell>{linesText(h)}</TableCell>
                  <TableCell><StatusChip tone={h.active ? 'success' : 'neutral'}>{h.active ? 'Ativo' : 'Inativo'}</StatusChip></TableCell>
                  <TableCell align="right">
                    <Button size="small" variant="outlined" startIcon={<EditOutlined />} onClick={() => setDialog({ open: true, hospital: h })}
                      sx={{ minHeight: 36 }}>Editar</Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      <HospitalDialog open={dialog.open} hospital={dialog.hospital} hospitals={hospitals}
        onClose={() => setDialog({ open: false, hospital: null })}
        onSaved={() => { setDialog({ open: false, hospital: null }); reload(); }} />
    </Paper>
  );
}
