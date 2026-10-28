import { useCallback, useEffect, useState } from 'react';
import {
  Alert, Box, Button, LinearProgress, Pagination, Paper, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Typography,
} from '@mui/material';
import UploadFileOutlined from '@mui/icons-material/UploadFileOutlined';
import StatusChip from '../../components/StatusChip';
import ConfirmDialog from '../../components/ConfirmDialog';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDateTime } from '../../utils/format';

const PAGE_SIZE = 10;

const STATUS = {
  IMPORTED: ['Importado', 'success'],
  TRACKING: ['Rastreio aplicado', 'info'],
  DUPLICATE: ['Já registrado', 'neutral'],
  NOT_SHIPMENT: ['Não é remessa', 'neutral'],
  UNRECOGNISED: ['Não reconhecido', 'warning'],
  FAILED: ['Falhou', 'error'],
};

const OUTCOME = {
  IMPORTED: 'Nota registrada.',
  DUPLICATE: 'Esta nota já estava registrada.',
  NOT_SHIPMENT: 'Esta nota não é material vindo para a sala.',
};

/**
 * Every message read from the mailbox, including the ones with nothing usable inside.
 *
 * A message that is opened and dropped in silence is invisible, and invisible is exactly the problem this
 * screen exists to remove. So the ones that could not be used are listed with the reason, and the XML can be
 * imported here by hand.
 */
export default function MailboxTab({ reload }) {
  const notify = useNotify();
  const [data, setData] = useState(null);
  const [page, setPage] = useState(1);
  const [busy, setBusy] = useState(false);
  // Como na lista de notas: a tabela fica na tela enquanto a próxima página chega, em vez de sumir e voltar
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState([]);
  const [confirming, setConfirming] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [messages, waiting] = await Promise.all([
        api('/invoices/mailbox', { query: { page: page - 1, size: PAGE_SIZE } }),
        api('/invoices/pending-tracking'),
      ]);
      setPending(waiting);
      setData(messages);
    } catch (err) {
      notify.error(err);
    } finally {
      setLoading(false);
    }
  }, [page, notify]);

  useEffect(() => { load(); }, [load, reload]);

  /**
   * Notas emitidas antes de a caixa existir foram citadas por um e-mail de rastreio e o XML delas nunca vai
   * chegar. Sem poder descartar, elas ficariam no aviso para sempre — e aviso que nunca sai de lá deixa de
   * ser lido.
   */
  const discard = async () => {
    const item = confirming;
    setBusy(true);
    try {
      await api(`/invoices/pending-tracking/${item.id}`, { method: 'DELETE' });
      notify.success(`Rastreio da nota ${item.number} excluído.`);
      setConfirming(null);
      load();
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };

  const upload = async (event) => {
    const files = Array.from(event.target.files || []);
    event.target.value = '';
    if (!files.length) return;
    setBusy(true);
    try {
      for (const file of files) {
        const body = new FormData();
        body.append('file', file);
        const result = await api('/invoices/xml', { method: 'POST', body });
        notify.success(`${file.name}: ${OUTCOME[result.outcome] || result.detail || 'Lido.'}`);
      }
      load();
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };

  const messages = data?.content || [];
  const pages = data?.totalPages || 1;

  return (
    <Stack spacing={2}>
      <Box>
        <Button component="label" variant="outlined" startIcon={<UploadFileOutlined />} disabled={busy}>
          Importar XML
          <input type="file" hidden multiple accept=".xml,text/xml,application/xml" onChange={upload} />
        </Button>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
          Para as notas antigas e para o que a leitura automática não entender.
        </Typography>
      </Box>

      {pending.length > 0 && (
        <Alert severity="info" variant="outlined">
          <Typography variant="body2" sx={{ fontWeight: 500, mb: 0.5 }}>
            Rastreios aguardando a nota chegar
          </Typography>
          {pending.map((item) => (
            <Stack key={item.id} direction="row" spacing={1} alignItems="center">
              <Typography variant="caption">
                Nota {item.number} · {item.trackingCode} · desde {formatDateTime(item.createdAt)}
              </Typography>
              <Button size="small" sx={{ minWidth: 0, px: 0.5, fontSize: 12 }} disabled={busy}
                onClick={() => setConfirming(item)}>
                excluir
              </Button>
            </Stack>
          ))}
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
            O código entra na nota sozinho quando o XML dela for lido. Exclua o que não vai chegar —
            notas emitidas antes de a caixa existir, por exemplo.
          </Typography>
        </Alert>
      )}

      <TableContainer component={Paper} variant="outlined" sx={{ position: 'relative', minHeight: 180 }}>
        <Box sx={{ position: 'absolute', top: 0, left: 0, right: 0, height: 2 }}>
          {loading && <LinearProgress sx={{ height: 2 }} />}
        </Box>
        <Box sx={{ transition: 'opacity .15s', opacity: loading && data ? 0.55 : 1 }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Lido em</TableCell>
                <TableCell>De</TableCell>
                <TableCell>Assunto</TableCell>
                <TableCell align="right">Anexos</TableCell>
                <TableCell align="right">Notas</TableCell>
                <TableCell>Resultado</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {messages.map((message) => {
                const [label, tone] = STATUS[message.status] || [message.status, 'neutral'];
                return (
                  <TableRow key={message.id} hover>
                    <TableCell>{formatDateTime(message.readAt)}</TableCell>
                    <TableCell>{message.sender}</TableCell>
                    <TableCell>{message.subject}</TableCell>
                    <TableCell align="right">{message.attachments}</TableCell>
                    <TableCell align="right">{message.invoices}</TableCell>
                    <TableCell>
                      <StatusChip tone={tone}>{label}</StatusChip>
                      {message.detail && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                          {message.detail}
                        </Typography>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
              {data && messages.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} sx={{ py: 5, textAlign: 'center', border: 0 }}>
                    <Typography variant="body2" color="text.secondary">
                      Nenhuma mensagem lida ainda. A leitura roda uma vez por dia, no horário configurado.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Box>
      </TableContainer>

      {pages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center' }}>
          <Pagination count={pages} page={page} onChange={(_, p) => setPage(p)} size="small" />
        </Box>
      )}
      <ConfirmDialog open={Boolean(confirming)} title="Excluir o rastreio?"
        message={confirming
          ? `O código ${confirming.trackingCode} deixa de esperar a nota ${confirming.number}. `
            + 'Se o XML dessa nota chegar depois, ela entra normalmente, só sem o rastreio.'
          : ''}
        confirmLabel="Excluir" danger busy={busy}
        onConfirm={discard} onClose={() => setConfirming(null)} />

    </Stack>
  );
}
