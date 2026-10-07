import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { Alert, Box, Button, Link, TextField, Typography } from '@mui/material';
import AuthShell from './AuthShell';
import { api } from '../api/client';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState(null);
  const [sending, setSending] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!email.trim()) {
      setError('Informe o e-mail.');
      return;
    }
    setSending(true);
    setError(null);
    try {
      await api('/auth/password-reset-requests', { method: 'POST', body: { email: email.trim() }, skipAuthRedirect: true });
      setSent(true);
    } catch (err) {
      setError(err.message);
    } finally {
      setSending(false);
    }
  };

  return (
    <AuthShell>
      <Box>
        <Typography variant="h1" sx={{ fontSize: 28 }}>Recuperar senha</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
          Informe o e-mail cadastrado para receber um link de criação de nova senha.
        </Typography>
      </Box>
      {sent ? (
        <Alert severity="success">
          Se o e-mail estiver cadastrado, você receberá o link em instantes. Ele vale por 30 minutos.
        </Alert>
      ) : (
        <Box component="form" onSubmit={submit} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="E-mail" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)}
            autoFocus fullWidth />
          <Button type="submit" variant="contained" size="large" disabled={sending}>Enviar link</Button>
        </Box>
      )}
      <Link component={RouterLink} to="/login" variant="body2" sx={{ alignSelf: 'center' }}>Voltar para o login</Link>
    </AuthShell>
  );
}
