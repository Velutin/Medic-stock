import { useState } from 'react';
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Stack, TextField, Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { api } from '../api/client';
import { useNotify } from '../notifications/NotificationProvider';
import { isStrongPassword, PASSWORD_RULE } from '../utils/documents';

const EMPTY = { currentPassword: '', newPassword: '', repeat: '' };

export default function ChangePasswordDialog({ open, onClose }) {
  const notify = useNotify();
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const close = () => {
    setForm(EMPTY);
    setErrors({});
    onClose();
  };

  const submit = async (e) => {
    e.preventDefault();
    const found = {};
    if (!form.currentPassword) found.currentPassword = 'Informe a senha atual.';
    if (!isStrongPassword(form.newPassword)) found.newPassword = `A nova senha deve ter ${PASSWORD_RULE}`;
    if (form.repeat !== form.newPassword) found.repeat = 'As senhas não coincidem.';
    setErrors(found);
    if (Object.keys(found).length) return;

    setSaving(true);
    try {
      await api('/users/me/password', {
        method: 'PUT',
        body: { currentPassword: form.currentPassword, newPassword: form.newPassword },
      });
      notify.success('Senha alterada. As sessões abertas em outros aparelhos foram encerradas.');
      close();
    } catch (err) {
      setErrors(err.fields || {});
      notify.error(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="xs" aria-labelledby="pwd-title">
      <Box component="form" onSubmit={submit} noValidate>
        <DialogTitle id="pwd-title" sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2 }}>
          <Box>
            <Typography variant="h2" component="span" sx={{ display: 'block' }}>Alterar senha</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Este aparelho continua conectado; os demais são desconectados.
            </Typography>
          </Box>
          <IconButton onClick={close} aria-label="Fechar"><CloseIcon /></IconButton>
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField label="Senha atual" type="password" autoComplete="current-password" value={form.currentPassword}
              onChange={set('currentPassword')} error={Boolean(errors.currentPassword)} helperText={errors.currentPassword} autoFocus />
            <TextField label="Nova senha" type="password" autoComplete="new-password" value={form.newPassword}
              onChange={set('newPassword')} error={Boolean(errors.newPassword)} helperText={errors.newPassword || PASSWORD_RULE} />
            <TextField label="Repetir nova senha" type="password" autoComplete="new-password" value={form.repeat}
              onChange={set('repeat')} error={Boolean(errors.repeat)} helperText={errors.repeat} />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button variant="outlined" onClick={close}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={saving}>Salvar nova senha</Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
