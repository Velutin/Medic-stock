import { useEffect, useState } from 'react';
import {
  Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, FormControlLabel, FormHelperText,
  FormLabel, IconButton, Radio, RadioGroup, Stack, TextField, Typography, useMediaQuery,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import { ASSIGNABLE_ROLES } from '../../auth/roles';
import { digits, isValidCpf, isValidMobile, maskCpf, maskPhone } from '../../utils/documents';

const EMPTY = { name: '', cpf: '', email: '', phone: '', role: 'SURGICAL_TECH', hospitalIds: [] };

/** Create (sends the first-access invitation) or edit a user. */
export default function UserDialog({ open, user, hospitals, onClose, onSaved }) {
  const notify = useNotify();
  const fullScreen = useMediaQuery('(max-width:599.95px)');
  const editing = Boolean(user);
  const isMaster = user?.role === 'MASTER';
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setErrors({});
    setForm(user ? {
      name: user.name || '',
      cpf: maskCpf(user.cpf || ''),
      email: user.email || '',
      phone: maskPhone(user.phone || ''),
      role: user.role === 'MASTER' ? 'ADMIN' : user.role,
      hospitalIds: user.hospitals.map((h) => h.id),
    } : EMPTY);
  }, [open, user]);

  const set = (field, transform = (v) => v) => (e) => setForm((f) => ({ ...f, [field]: transform(e.target.value) }));
  const toggleHospital = (id) => setForm((f) => ({
    ...f,
    hospitalIds: f.hospitalIds.includes(id) ? f.hospitalIds.filter((h) => h !== id) : [...f.hospitalIds, id],
  }));
  // Administrators see every hospital: no link needed.
  const needsHospitals = !isMaster && form.role !== 'ADMIN';

  const validate = () => {
    const found = {};
    if (!form.name.trim()) found.name = 'Informe o nome completo.';
    if (!isValidCpf(form.cpf)) found.cpf = 'CPF inválido.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) found.email = 'E-mail inválido.';
    if (!isValidMobile(form.phone)) found.phone = 'Celular inválido. Use DDD + 9 dígitos.';
    if (needsHospitals && form.hospitalIds.length === 0) found.hospitalIds = 'Selecione ao menos um hospital.';
    setErrors(found);
    return Object.keys(found).length === 0;
  };

  const submit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    const payload = {
      name: form.name.trim(),
      email: form.email.trim(),
      cpf: digits(form.cpf),
      phone: digits(form.phone),
      role: isMaster ? undefined : form.role,
    };
    const hospitalIds = needsHospitals ? form.hospitalIds : [];
    setSaving(true);
    try {
      if (editing) {
        await api(`/users/${user.id}`, { method: 'PATCH', body: payload });
        if (!isMaster) await api(`/users/${user.id}/hospitals`, { method: 'PUT', body: { hospitalIds } });
        notify.success('Usuário atualizado.');
      } else {
        await api('/users', { method: 'POST', body: { ...payload, hospitalIds } });
        notify.success(`Usuário criado. O convite de primeiro acesso foi enviado para ${payload.email}.`);
      }
      onSaved();
    } catch (err) {
      setErrors(err.fields || {});
      notify.error(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" fullScreen={fullScreen} aria-labelledby="user-title">
      <Box component="form" onSubmit={submit} noValidate sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <DialogTitle id="user-title" sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h2" component="span">{editing ? 'Editar usuário' : 'Novo usuário'}</Typography>
          <IconButton onClick={onClose} aria-label="Fechar"><CloseIcon /></IconButton>
        </DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.25}>
            <TextField label="Nome completo" value={form.name} onChange={set('name')} error={Boolean(errors.name)}
              helperText={errors.name} autoFocus fullWidth />
            <Box sx={{ display: 'grid', gap: 2.25, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
              <TextField label="CPF" value={form.cpf} onChange={set('cpf', maskCpf)} placeholder="000.000.000-00"
                inputMode="numeric" error={Boolean(errors.cpf)} helperText={errors.cpf} />
              <TextField label="Celular (com DDD)" value={form.phone} onChange={set('phone', maskPhone)}
                placeholder="(71) 99999-9999" inputMode="tel" error={Boolean(errors.phone)} helperText={errors.phone} />
            </Box>
            <TextField label="E-mail" type="email" value={form.email} onChange={set('email')} error={Boolean(errors.email)}
              helperText={errors.email || (editing ? 'É o login do usuário.' : 'O convite de primeiro acesso será enviado para este e-mail.')}
              fullWidth />

            <FormControl>
              <FormLabel sx={{ fontSize: 13, fontWeight: 500, mb: 1 }}>Perfil</FormLabel>
              {isMaster ? (
                <Typography variant="body2" color="text.secondary">Usuário master: o perfil não pode ser alterado.</Typography>
              ) : (
                <RadioGroup value={form.role} onChange={set('role')} sx={{ gap: 1 }}>
                  {ASSIGNABLE_ROLES.map((r) => (
                    <FormControlLabel key={r.value} value={r.value} control={<Radio />}
                      sx={{ alignItems: 'flex-start', mx: 0, border: 1, borderRadius: '6px', p: 1,
                        borderColor: form.role === r.value ? 'primary.main' : 'divider' }}
                      label={(
                        <Box sx={{ pt: 0.75 }}>
                          <Typography sx={{ fontSize: 14, fontWeight: 600 }}>{r.label}</Typography>
                          <Typography variant="body2" color="text.secondary">{r.description}</Typography>
                        </Box>
                      )} />
                  ))}
                </RadioGroup>
              )}
            </FormControl>

            {needsHospitals && (
              <FormControl error={Boolean(errors.hospitalIds)}>
                <FormLabel sx={{ fontSize: 13, fontWeight: 500, mb: 0.5 }}>Hospitais vinculados</FormLabel>
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
                  {hospitals.map((h) => (
                    <FormControlLabel key={h.id} label={h.name}
                      control={<Checkbox checked={form.hospitalIds.includes(h.id)} onChange={() => toggleHospital(h.id)} />} />
                  ))}
                </Box>
                {errors.hospitalIds && <FormHelperText>{errors.hospitalIds}</FormHelperText>}
              </FormControl>
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button variant="outlined" onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={saving}>{editing ? 'Salvar alterações' : 'Criar usuário'}</Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
