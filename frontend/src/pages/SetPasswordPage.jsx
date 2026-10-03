import { useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { Alert, Box, Button, Link, TextField, Typography } from '@mui/material';
import AuthShell from './AuthShell';
import { api } from '../api/client';
import { isStrongPassword, PASSWORD_RULE } from '../utils/documents';

/** Password creation from the e-mail link: first access (invitation) or password reset. */
export default function SetPasswordPage({ firstAccess }) {
  const [params] = useSearchParams();
  const token = params.get('token');
  const [password, setPassword] = useState('');
  const [repeat, setRepeat] = useState('');
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);
  const [done, setDone] = useState(false);
  const [sending, setSending] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const found = {};
    if (!isStrongPassword(password)) found.password = `A senha deve ter ${PASSWORD_RULE}`;
    if (repeat !== password) found.repeat = 'As senhas não coincidem.';
    setErrors(found);
    if (Object.keys(found).length) return;

    setSending(true);
    setError(null);
    try {
      await api('/auth/password-resets', { method: 'POST', body: { token, newPassword: password }, skipAuthRedirect: true });
      setDone(true);
    } catch (err) {
      setError(err.message);
    } finally {
      setSending(false);
    }
  };

  const title = firstAccess ? 'Criar senha de acesso' : 'Criar nova senha';

  return (
    <AuthShell>
      <Box>
        <Typography variant="h1" sx={{ fontSize: 28 }}>{title}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
          {firstAccess ? 'Defina a senha que você usará para entrar no sistema.' : 'Defina a nova senha da sua conta.'}
        </Typography>
      </Box>
      {!token ? (
        <Alert severity="error">Link incompleto. Abra novamente o link recebido por e-mail.</Alert>
      ) : done ? (
        <>
          <Alert severity="success">Senha criada. Você já pode entrar com o seu e-mail e a nova senha.</Alert>
          <Button component={RouterLink} to="/login" variant="contained" size="large">Ir para o login</Button>
        </>
      ) : (
        <Box component="form" onSubmit={submit} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="Senha" type="password" autoComplete="new-password" value={password}
            onChange={(e) => setPassword(e.target.value)} error={Boolean(errors.password)}
            helperText={errors.password || PASSWORD_RULE} autoFocus fullWidth />
          <TextField label="Repetir senha" type="password" autoComplete="new-password" value={repeat}
            onChange={(e) => setRepeat(e.target.value)} error={Boolean(errors.repeat)} helperText={errors.repeat} fullWidth />
          <Button type="submit" variant="contained" size="large" disabled={sending}>Salvar senha</Button>
        </Box>
      )}
      {!done && (
        <Link component={RouterLink} to="/esqueci-senha" variant="body2" sx={{ alignSelf: 'center' }}>
          Link expirado? Peça um novo
        </Link>
      )}
    </AuthShell>
  );
}
