import { useMemo, useState } from 'react';
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Paper, Skeleton, Stack,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import LocalShippingOutlined from '@mui/icons-material/LocalShippingOutlined';
import FileDownloadOutlined from '@mui/icons-material/FileDownloadOutlined';
import PageHeader from '../../components/PageHeader';
import ConfirmDialog from '../../components/ConfirmDialog';
import useShipmentsDraft from './useShipmentsDraft';
import { downloadFile } from '../../api/download';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDate, formatDateTime } from '../../utils/format';
import { tokens } from '../../theme';

const mono = { fontFamily: tokens.mono, fontSize: 13 };

/** Dias entre o despacho e hoje, para destacar a remessa que está demorando. */
const daysOnRoad = (createdAt) => {
  const sent = new Date(createdAt);
  return Math.max(0, Math.floor((Date.now() - sent.getTime()) / 86400000));
};

/**
 * Remessas a caminho: o que saiu e ainda não foi confirmado no destino.
 *
 * É daqui que o material entra no estoque do hospital. Enquanto a remessa está nesta lista, o saldo existe
 * mas não está em lugar nenhum utilizável — e é por isso que a tela mostra há quantos dias cada uma está na
 * estrada: remessa esquecida aqui é material que o hospital não tem e o sistema não cobra de ninguém.
 */
export default function ShipmentsPage() {
  const notify = useNotify();
  const shipments = useShipmentsDraft();
  const [arriving, setArriving] = useState(null);
  const [cancelling, setCancelling] = useState(null);
  const [arrivedOn, setArrivedOn] = useState('');
  const [received, setReceived] = useState({});

  const openArrival = (row) => {
    setArrivedOn(shipments.todayIso());
    setReceived(Object.fromEntries(row.items.map((i) => [i.itemId, i.quantity])));
    setArriving(row);
  };

  const shortfall = useMemo(() => {
    if (!arriving) return 0;
    return arriving.items.reduce((sum, i) => sum + Math.max(0, i.quantity - (received[i.itemId] ?? i.quantity)), 0);
  }, [arriving, received]);

  const confirm = async () => {
    // Só as linhas que vieram a menos: linha não mencionada chegou inteira.
    const short = Object.fromEntries(
      Object.entries(received).filter(([itemId, qty]) => {
        const line = arriving.items.find((i) => String(i.itemId) === String(itemId));
        return line && qty !== line.quantity;
      }).map(([k, v]) => [k, v]),
    );
    if (await shipments.confirmArrival(arriving, arrivedOn, short)) setArriving(null);
  };

  /**
   * Planilha de auditoria. Sai desta tela porque é aqui que o material a caminho mora, mas não é a lista da
   * tela: ela é o saldo em trânsito, lote a lote, mais as remessas que o explicam. O saldo pode ser maior
   * que as remessas abertas — peça de remessa já confirmada que não chegou continua na estrada — e é
   * exatamente essa diferença que a auditoria procura.
   */
  const exportSpreadsheet = async () => {
    const today = new Date().toLocaleDateString('sv-SE');
    try {
      await downloadFile('/stock/in-transit/xlsx', `estoque-a-caminho-${today}.xlsx`);
    } catch (err) {
      notify.error(err);
    }
  };

  const setQuantity = (item, value) => {
    const qty = Math.max(0, Math.min(item.quantity, Math.floor(Number(value) || 0)));
    setReceived((r) => ({ ...r, [item.itemId]: qty }));
  };

  return (
    <>
      <PageHeader title="Remessas a caminho" subtitle="Material despachado que ainda não foi confirmado no destino"
        actions={(
          <Button variant="outlined" startIcon={<FileDownloadOutlined />} onClick={exportSpreadsheet}
            title="Saldo em trânsito lote a lote, com as remessas que o explicam — para levantamento e auditoria">
            Exportar planilha
          </Button>
        )} />

      {shipments.loading ? (
        <Stack spacing={1.5}>{[1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={92} />)}</Stack>
      ) : shipments.rows.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}>
          <LocalShippingOutlined sx={{ fontSize: 40, color: tokens.textMuted, mb: 1 }} />
          <Typography variant="h3" component="p">Nenhuma remessa a caminho</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Tudo que foi despachado já foi confirmado no destino.
          </Typography>
        </Paper>
      ) : (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {shipments.totals.shipments} {shipments.totals.shipments === 1 ? 'remessa' : 'remessas'} ·{' '}
            {shipments.totals.units} {shipments.totals.units === 1 ? 'peça' : 'peças'} na estrada
          </Typography>
          <Stack spacing={1.5}>
            {shipments.rows.map((row) => {
              const days = daysOnRoad(row.createdAt);
              const units = row.items.reduce((s, i) => s + i.quantity, 0);
              return (
                <Paper key={`${row.kind}-${row.id}`} variant="outlined" sx={{ p: 2.5 }}>
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
                    <Box sx={{ flex: '1 1 320px', minWidth: 0 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                        <Typography sx={{ fontWeight: 600 }}>{row.label}</Typography>
                        <Chip size="small" variant="outlined"
                          label={row.kind === 'LOAN' ? 'Empréstimo' : 'Transferência'} />
                        {days >= 7 && (
                          <Chip size="small" color="warning" variant="outlined"
                            label={`${days} dias na estrada`} />
                        )}
                      </Box>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        {row.from} → {row.to}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        Despachada em {formatDateTime(row.createdAt)} · {row.items.length}{' '}
                        {row.items.length === 1 ? 'lote' : 'lotes'} · {units} {units === 1 ? 'peça' : 'peças'}
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
                      <Button variant="text" color="error" onClick={() => setCancelling(row)}
                        disabled={shipments.sending}>
                        Cancelar
                      </Button>
                      <Button variant="contained" onClick={() => openArrival(row)} disabled={shipments.sending}>
                        Confirmar chegada
                      </Button>
                    </Stack>
                  </Box>
                </Paper>
              );
            })}
          </Stack>
        </>
      )}

      <Dialog open={Boolean(arriving)} onClose={() => setArriving(null)} fullWidth maxWidth="md">
        <DialogTitle sx={{ typography: 'h3' }}>
          Confirmar chegada · {arriving?.label}
        </DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5}>
            <TextField type="date" label="Chegou em" value={arrivedOn} sx={{ maxWidth: 220 }}
              InputLabelProps={{ shrink: true }} onChange={(e) => setArrivedOn(e.target.value)}
              helperText="O dia em que o material chegou, não o dia de hoje se for diferente." />
            <Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                As quantidades já vêm preenchidas com o que foi despachado. Mexa só nas linhas que vieram a menos.
              </Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>REF</TableCell><TableCell>Material</TableCell><TableCell>Lote</TableCell>
                      <TableCell>Validade</TableCell><TableCell align="right">Enviado</TableCell>
                      <TableCell align="right">Chegou</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(arriving?.items || []).map((i) => {
                      const got = received[i.itemId] ?? i.quantity;
                      return (
                        <TableRow key={i.itemId} hover>
                          <TableCell sx={mono}>{i.ref}</TableCell>
                          <TableCell>{i.description}</TableCell>
                          <TableCell sx={mono}>{i.lot}</TableCell>
                          <TableCell sx={mono}>{formatDate(i.expiryDate)}</TableCell>
                          <TableCell align="right">{i.quantity}</TableCell>
                          <TableCell align="right" sx={{ py: 0.5 }}>
                            <TextField type="number" size="small" value={got} sx={{ width: 90 }}
                              onChange={(e) => setQuantity(i, e.target.value)}
                              inputProps={{ min: 0, max: i.quantity, step: 1, style: { textAlign: 'right' },
                                'aria-label': `Quantidade que chegou do lote ${i.lot}` }}
                              error={got < i.quantity} />
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            </Box>
            {shortfall > 0 && (
              <Alert severity="warning">
                {shortfall} {shortfall === 1 ? 'peça não chegou' : 'peças não chegaram'}. Vão virar pendência,
                e o saldo continua a caminho até você dizer o que aconteceu: apareceu, ficou na origem ou se
                extraviou.
              </Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setArriving(null)}>Voltar</Button>
          <Button variant="contained" onClick={confirm} disabled={shipments.sending || !arrivedOn}>
            Confirmar chegada
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog open={Boolean(cancelling)} busy={shipments.sending} title="Cancelar remessa"
        message={cancelling
          ? `${cancelling.label} volta inteira para ${cancelling.from}. Só faz sentido se a caixa não chegou a sair. Deseja continuar?`
          : ''}
        confirmLabel="Cancelar remessa" danger
        onConfirm={async () => { if (await shipments.cancel(cancelling)) setCancelling(null); }}
        onClose={() => setCancelling(null)} />
    </>
  );
}
