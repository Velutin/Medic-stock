import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Box, Button, Checkbox, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableContainer, TableHead,
  TablePagination, TableRow, TextField, Typography,
} from '@mui/material';
import LocalShippingOutlined from '@mui/icons-material/LocalShippingOutlined';
import PictureAsPdfOutlined from '@mui/icons-material/PictureAsPdfOutlined';
import { api } from '../../api/client';
import { downloadFile } from '../../api/download';
import { useNotify } from '../../notifications/NotificationProvider';
import { tokens } from '../../theme';

const PAGE = 10;
const mono = { fontFamily: tokens.mono, fontSize: 13 };
const pageLabel = ({ from, to, count }) => `${from}–${to} de ${count}`;

function usePaged(list) {
  const [page, setPage] = useState(0);
  useEffect(() => { if (page * PAGE >= list.length) setPage(0); }, [list.length, page]);
  return { page, setPage, rows: list.slice(page * PAGE, page * PAGE + PAGE) };
}

function Section({ title, subtitle, action, children }) {
  return (
    <Paper variant="outlined" component="section" sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1.5, mb: 2 }}>
        <Box>
          <Typography variant="h3" component="h2">{title}</Typography>
          <Typography variant="body2" color="text.secondary">{subtitle}</Typography>
        </Box>
        {action}
      </Box>
      {children}
    </Paper>
  );
}

const Empty = ({ children }) => (
  <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>{children}</Typography>
);

/** Lots suggested for one destination, in the shape the Transfer screen expects. */
const lotsOf = (rows) => {
  const lots = {};
  rows.forEach((r) => (r.storeroomLots || []).forEach((l) => { lots[l.lotId] = (lots[l.lotId] || 0) + l.quantity; }));
  return lots;
};

/** Storeroom -> hospital table. With a distribution center, one line per supplied hospital and REF. */
function StoreroomTable({ rows, showHospital }) {
  const { page, setPage, rows: visible } = usePaged(rows);
  return (
    <>
      <TableContainer>
        <Table size="small" sx={{ minWidth: 640 }}>
          <TableHead>
            <TableRow>
              {showHospital && <TableCell>Hospital</TableCell>}
              <TableCell>REF</TableCell><TableCell>Material</TableCell><TableCell align="right">No hospital</TableCell>
              <TableCell align="right">Ideal</TableCell><TableCell align="right">Na sala</TableCell><TableCell align="right">Repor</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {visible.map((r) => (
              <TableRow key={`${r.hospitalId}-${r.materialId}`} hover>
                {showHospital && <TableCell>{r.hospitalName}</TableCell>}
                <TableCell sx={mono}>{r.ref}</TableCell>
                <TableCell>{r.description}</TableCell>
                <TableCell align="right">{r.hospitalBalance}</TableCell>
                <TableCell align="right">{r.hospitalIdeal}</TableCell>
                <TableCell align="right">{r.storeroomBalance}</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>{r.replenishFromStoreroom}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {rows.length > PAGE && (
        <TablePagination component="div" count={rows.length} page={page} rowsPerPage={PAGE} rowsPerPageOptions={[PAGE]}
          onPageChange={(_, p) => setPage(p)} labelDisplayedRows={pageLabel} />
      )}
    </>
  );
}

/**
 * Replenishment tab. Regular hospital: storeroom -> hospital and supplier order. Hospital supplied by a
 * distribution center: storeroom of the center -> hospital (orders are placed by the center). Distribution center:
 * distribution to the hospitals it supplies and the supplier order (ideal total vs. its storeroom + those hospitals).
 */
export default function ReplenishmentTab({ hospital, hospitals }) {
  const notify = useNotify();
  const navigate = useNavigate();
  const isCenter = hospital.type === 'DISTRIBUTION_CENTER';
  const center = hospital.distributionCenterId ? hospitals.find((h) => h.id === hospital.distributionCenterId) : null;
  const supplied = useMemo(
    () => (isCenter ? hospitals.filter((h) => h.distributionCenterId === hospital.id) : []),
    [isCenter, hospitals, hospital.id],
  );
  const [own, setOwn] = useState(null);
  const [distribution, setDistribution] = useState([]);
  const [order, setOrder] = useState({});
  const [sending, setSending] = useState(false);

  const load = useCallback(async () => {
    setOwn(null);
    try {
      const list = await api(`/hospitals/${hospital.id}/replenishment-suggestions`);
      const perHospital = await Promise.all(supplied.map(async (h) => (
        await api(`/hospitals/${h.id}/replenishment-suggestions`)).map((r) => ({ ...r, hospitalId: h.id, hospitalName: h.name }))));
      setOwn(list.map((r) => ({ ...r, hospitalId: hospital.id, hospitalName: hospital.name })));
      setDistribution(perHospital.flat().filter((r) => r.replenishFromStoreroom > 0));
      const initial = {};
      list.filter((r) => r.orderFromSupplier > 0).forEach((r) => { initial[r.materialId] = { include: true, quantity: r.orderFromSupplier }; });
      setOrder(initial);
    } catch (err) {
      notify.error(err);
      setOwn([]);
    }
  }, [hospital.id, hospital.name, supplied, notify]);

  useEffect(() => { load(); }, [load]);

  const fromStoreroom = isCenter ? distribution : (own || []).filter((r) => r.replenishFromStoreroom > 0);
  const toOrder = (own || []).filter((r) => r.orderFromSupplier > 0);
  const destinations = [...new Map(fromStoreroom.map((r) => [r.hospitalId, r.hospitalName])).entries()];
  const included = toOrder.filter((r) => order[r.materialId]?.include && Number(order[r.materialId]?.quantity) > 0);
  const orderPage = usePaged(toOrder);

  const transfer = (hospitalId) => {
    navigate('/transferencia', {
      state: { prefill: { hospitalId, lots: lotsOf(fromStoreroom.filter((r) => r.hospitalId === hospitalId)) } },
    });
  };

  const generateOrder = async () => {
    setSending(true);
    try {
      const created = await api('/supplier-orders', {
        method: 'POST',
        body: {
          hospitalId: hospital.id,
          items: included.map((r) => ({ materialId: r.materialId, quantity: Number(order[r.materialId].quantity), urgent: true })),
        },
      });
      notify.success(`Pedido #${created.id} gerado.`);
      await downloadFile(`/supplier-orders/${created.id}/pdf`, `pedido-${hospital.name}-${created.id}.pdf`);
    } catch (err) {
      notify.error(err);
    } finally {
      setSending(false);
    }
  };

  const setOrderField = (materialId, patch) => setOrder((o) => ({ ...o, [materialId]: { ...o[materialId], ...patch } }));

  if (own === null) {
    return <Stack spacing={1.5}>{[1, 2].map((i) => <Skeleton key={i} variant="rounded" height={180} />)}</Stack>;
  }

  return (
    <Stack spacing={2.5}>
      <Section
        title={isCenter ? `Distribuir da sala de ${hospital.name}` : 'Repor da sala para o hospital'}
        subtitle={isCenter
          ? 'Hospitais atendidos abaixo do ideal, com saldo na sala do centro de distribuição'
          : `Abaixo do ideal no hospital, com saldo disponível na sala${center ? ` de ${center.name}` : ''}`}
        action={!isCenter && fromStoreroom.length > 0 && (
          <Button variant="contained" startIcon={<LocalShippingOutlined />} onClick={() => transfer(hospital.id)}>
            Transferir para o hospital
          </Button>
        )}>
        {fromStoreroom.length === 0 ? (
          <Empty>{isCenter ? 'Nenhum hospital atendido precisa de reposição agora.' : 'Nada a repor da sala: o hospital está no ideal ou a sala não tem o material.'}</Empty>
        ) : (
          <Stack spacing={1.5}>
            <StoreroomTable rows={fromStoreroom} showHospital={isCenter} />
            {isCenter && (
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                {destinations.map(([id, name]) => (
                  <Button key={id} variant="outlined" startIcon={<LocalShippingOutlined />} onClick={() => transfer(id)}>
                    Transferir para {name}
                  </Button>
                ))}
              </Box>
            )}
          </Stack>
        )}
      </Section>

      {center ? (
        <Alert severity="info">
          {hospital.name} é atendido por {center.name}: o pedido à empresa é feito na Reposição de {center.name}.
        </Alert>
      ) : (
        <Section title="Solicitar à empresa"
          subtitle={isCenter
            ? 'Abaixo do ideal total (sala do centro + hospitais atendidos). Desmarque o que não for urgente.'
            : 'Abaixo do ideal total (hospital + sala). Desmarque o que não for urgente.'}
          action={toOrder.length > 0 && (
            <Button variant="contained" startIcon={<PictureAsPdfOutlined />} onClick={generateOrder}
              disabled={sending || included.length === 0}>
              Gerar PDF para WhatsApp
            </Button>
          )}>
          {toOrder.length === 0 ? <Empty>Nada a pedir: tudo está no ideal total.</Empty> : (
            <>
              <TableContainer>
                <Table size="small" sx={{ minWidth: 640 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell padding="checkbox">Incluir</TableCell><TableCell>REF</TableCell><TableCell>Material</TableCell>
                      <TableCell align="right">Total atual</TableCell><TableCell align="right">Ideal total</TableCell>
                      <TableCell align="right">Pedir</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {orderPage.rows.map((r) => {
                      const o = order[r.materialId] || {};
                      return (
                        <TableRow key={r.materialId} hover selected={Boolean(o.include)}>
                          <TableCell padding="checkbox">
                            <Checkbox checked={Boolean(o.include)} onChange={(e) => setOrderField(r.materialId, { include: e.target.checked })}
                              inputProps={{ 'aria-label': `Incluir ${r.ref} no pedido` }} />
                          </TableCell>
                          <TableCell sx={mono}>{r.ref}</TableCell>
                          <TableCell>{r.description}</TableCell>
                          <TableCell align="right">{r.hospitalBalance + r.storeroomBalance}</TableCell>
                          <TableCell align="right">{r.idealTotal}</TableCell>
                          <TableCell align="right" sx={{ py: 0.5 }}>
                            <TextField type="number" size="small" value={o.quantity ?? ''} disabled={!o.include} sx={{ width: 84 }}
                              onChange={(e) => setOrderField(r.materialId, { quantity: e.target.value })}
                              inputProps={{ min: 1, step: 1, style: { textAlign: 'right' }, 'aria-label': `Quantidade a pedir de ${r.ref}` }} />
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
              {toOrder.length > PAGE && (
                <TablePagination component="div" count={toOrder.length} page={orderPage.page} rowsPerPage={PAGE}
                  rowsPerPageOptions={[PAGE]} onPageChange={(_, p) => orderPage.setPage(p)} labelDisplayedRows={pageLabel} />
              )}
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                {included.length} de {toOrder.length} {toOrder.length === 1 ? 'item' : 'itens'} no pedido ·{' '}
                {included.reduce((s, r) => s + Number(order[r.materialId].quantity || 0), 0)} unidades
              </Typography>
            </>
          )}
        </Section>
      )}
    </Stack>
  );
}
