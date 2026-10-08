import { useEffect, useState } from 'react';
import { Link as RouterLink, Navigate, useLocation, useNavigate } from 'react-router-dom';
import {
  Alert, Box, Button, Checkbox, FormControlLabel, IconButton, InputAdornment, Link, TextField, Typography,
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import AuthShell from './AuthShell';
import { useAuth } from '../auth/AuthProvider';
import { homeFor } from '../layout/menu';

/**
 * Page the user was trying to open before the login, used to send them back to it afterwards.
 * Only an internal path is accepted: it must start with a single "/" and carry no backslash, which some
 * parsers read as the second slash of "//another-site". Anything else falls back to the home page of the
 * profile, so a prepared link cannot send the user out of the system right after signing in.
 */
const safePath = (path, fallback) =>
  (typeof path === 'string' && /^\/(?!\/)[^\\\s]*$/.test(path) ? path : fallback);

export default function LoginPage() {
  const { user, login, expiredMessage, clearExpired } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState(null);
  const [fields, setFields] = useState({});
  const [sending, setSending] = useState(false);

  useEffect(() => () => clearExpired(), [clearExpired]);

  if (user) return <Navigate to={safePath(location.state?.from, homeFor(user))} replace />;

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    const found = {};
    if (!email.trim()) found.email = 'Informe o e-mail.';
    if (!password) found.password = 'Informe a senha.';
    setFields(found);
    if (Object.keys(found).length) return;

    setSending(true);
    try {
      const logged = await login(email.trim(), password, rememberMe);
      navigate(safePath(location.state?.from, homeFor(logged)), { replace: true });
    } catch (err) {
      setFields(err.fields || {});
      setError(err.message);
    } finally {
      setSending(false);
    }
  };

  return (
    <AuthShell>
      <Box>
        <Typography variant="h1" sx={{ fontSize: 28 }}>Entrar</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
          Use o e-mail e a senha cadastrados pelo administrador.
        </Typography>
      </Box>

      {(error || expiredMessage) && <Alert severity={error ? 'error' : 'warning'}>{error || expiredMessage}</Alert>}

      <Box component="form" onSubmit={submit} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
        <TextField label="E-mail" type="email" autoComplete="username" placeholder="nome@empresa.com.br" value={email}
          onChange={(e) => setEmail(e.target.value)} error={Boolean(fields.email)} helperText={fields.email} autoFocus fullWidth />
        <TextField label="Senha" type={showPassword ? 'text' : 'password'} autoComplete="current-password" value={password}
          onChange={(e) => setPassword(e.target.value)} error={Boolean(fields.password)} helperText={fields.password} fullWidth
          slotProps={{ input: { endAdornment: (
            <InputAdornment position="end">
              <IconButton onClick={() => setShowPassword((v) => !v)} aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                aria-pressed={showPassword} edge="end">
                {showPassword ? <VisibilityOff /> : <Visibility />}
              </IconButton>
            </InputAdornment>
          ) } }} />
        <FormControlLabel control={<Checkbox checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} />}
          label="Manter conectado neste aparelho" />
        <Button type="submit" variant="contained" size="large" disabled={sending}>Entrar</Button>
        <Link component={RouterLink} to="/esqueci-senha" variant="body2" sx={{ alignSelf: 'center' }}>
          Esqueci minha senha
        </Link>
      </Box>
    </AuthShell>
  );
}
