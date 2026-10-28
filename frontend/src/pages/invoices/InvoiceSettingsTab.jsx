import { useEffect, useState } from 'react';
import {
  Alert, Box, Button, FormControlLabel, LinearProgress, MenuItem, Paper, Stack, Switch, TextField, Typography,
} from '@mui/material';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';

const HOURS = Array.from({ length: 24 }, (_, h) => h);

/**
 * The three decisions that change with use: when the mailbox is read, how long an invoice may stay open
 * before it is charged, and whether the entry requires an invoice.
 *
 * The last one starts off on purpose. In the first weeks material arrives from invoices nobody read yet, and
 * an entry refused for that reason would stop the stock from being registered at all.
 */
export default function InvoiceSettingsTab() {
  const notify = useNotify();
  const [settings, setSettings] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        setSettings(await api('/invoices/settings'));
      } catch (err) {
        notify.error(err);
      }
    })();
  }, [notify]);

  const save = async (changes) => {
    setBusy(true);
    try {
      setSettings(await api('/invoices/settings', { method: 'PUT', body: changes }));
      notify.success('Configuração salva.');
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };

  // Sem esqueleto: a tela só aparece quando há o que mostrar, em vez de desenhar um bloco cinza e trocá-lo
  if (!settings) return <Box sx={{ minHeight: 260 }}><LinearProgress sx={{ height: 2 }} /></Box>;

  const required = settings['invoice.required.on.entry'] === 'true';

  return (
    <Stack spacing={2.5} sx={{ maxWidth: 680 }}>
      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2}>
          <Typography variant="h3" component="h2">Leitura do e-mail</Typography>
          <TextField select size="small" label="Horário da leitura diária" sx={{ maxWidth: 220 }}
            value={settings['invoice.reading.hour']} disabled={busy}
            onChange={(e) => save({ 'invoice.reading.hour': e.target.value })}>
            {HOURS.map((h) => (
              <MenuItem key={h} value={String(h)}>{String(h).padStart(2, '0')}:00</MenuItem>
            ))}
          </TextField>
          <Typography variant="caption" color="text.secondary">
            A leitura também pode ser disparada a qualquer momento pelo botão &quot;Ler e-mails agora&quot;.
          </Typography>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2}>
          <Typography variant="h3" component="h2">Cobrança</Typography>
          <TextField size="small" type="number" label="Dias para alertar" sx={{ maxWidth: 220 }}
            defaultValue={settings['invoice.alert.days']} disabled={busy}
            inputProps={{ min: 1, max: 90 }}
            onBlur={(e) => {
              if (e.target.value !== settings['invoice.alert.days']) save({ 'invoice.alert.days': e.target.value });
            }} />
          <Typography variant="caption" color="text.secondary">
            Nota aberta há mais dias que isso entra no cartão &quot;Notas não recebidas&quot; do Painel. Notas
            parcialmente recebidas continuam contando enquanto faltar item.
          </Typography>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2}>
          <Typography variant="h3" component="h2">Guarda das notas em PDF</Typography>
          <TextField size="small" type="number" label="Dias para guardar o PDF" sx={{ maxWidth: 220 }}
            defaultValue={settings['invoice.pdf.retention.days']} disabled={busy}
            inputProps={{ min: 30, max: 1825 }}
            onBlur={(e) => {
              if (e.target.value !== settings['invoice.pdf.retention.days']) {
                save({ 'invoice.pdf.retention.days': e.target.value });
              }
            }} />
          <Typography variant="caption" color="text.secondary">
            Passado esse prazo o sistema apaga apenas o arquivo, de madrugada. A nota e todos os seus dados
            continuam aqui para sempre; só o PDF some, e a tela passa a dizer que ele expirou.
          </Typography>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2}>
          <Typography variant="h3" component="h2">Entrada</Typography>
          <FormControlLabel
            control={(
              <Switch checked={required} disabled={busy}
                onChange={(e) => save({ 'invoice.required.on.entry': String(e.target.checked) })} />
            )}
            label="Exigir nota vinculada na entrada" />
          {required ? (
            <Alert severity="warning">
              Entradas sem nota vinculada serão recusadas. Deixe desligado enquanto houver material chegando de
              notas que ainda não foram lidas.
            </Alert>
          ) : (
            <Typography variant="caption" color="text.secondary">
              Hoje a entrada sem nota passa, apenas com aviso, e fica marcada como &quot;sem nota&quot;.
            </Typography>
          )}
        </Stack>
      </Paper>
    </Stack>
  );
}
