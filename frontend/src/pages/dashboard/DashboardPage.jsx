import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box, Link, MenuItem, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  TextField, Typography,
} from '@mui/material';
import PageHeader from '../../components/PageHeader';
import StatusChip from '../../components/StatusChip';
import { api } from '../../api/client';
import useHospitals from '../../hooks/useHospitals';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDateTime } from '../../utils/format';
import { tokens } from '../../theme';
import { MOVEMENT_TYPES } from '../reports/SimpleReports';

const ALERTS_SHOWN = 8;
const mono = { fontFamily: tokens.mono, fontSize: 13 };
const number = (n) => Number(n || 0).toLocaleString('pt-BR');
const weekDay = (iso, label) => {
  const [, m, d] = iso.split('-');
  return `${label} ${d}/${m}`;
};
const place = (hospital, location) => (hospital
  ? `${hospital}${location === 'STOREROOM' ? ' (sala)' : ''}` : null);

function Kpi({ label, value, hint, tone, to }) {
  const color = tone === 'error' ? tokens.error : tone === 'warning' ? tokens.warning : tokens.text;
  const content = (
    <Paper variant="outlined" sx={{ p: 2.5, height: '100%', transition: 'border-color .15s', ...(to ? { '&:hover': { borderColor: tokens.primary } } : {}) }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography sx={{ fontSize: 30, fontWeight: 700, lineHeight: 1.2, my: 0.5, color }}>{value}</Typography>
      <Typography variant="caption" color="text.secondary">{hint}</Typography>
    </Paper>
  );
  return to ? <Link component={RouterLink} to={to} underline="none" color="inherit" sx={{ display: 'block' }}>{content}</Link> : content;
}

function Card({ title, action, children }) {
  return (
    <Paper variant="outlined" component="section" sx={{ p: 2.5, minWidth: 0 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 1, mb: 1.5 }}>
        <Typography variant="h3" component="h2">{title}</Typography>
        {action}
      </Box>
      {children}
    </Paper>
  );
}

/**
 * Administrator's dashboard: stock in the hospitals and storerooms (valid lots), lots to prioritize, surgeries of the
 * closing week (Saturday to Friday), replenishment alerts and the latest movements. One hospital or all of them.
 */
export default function DashboardPage() {
  const notify = useNotify();
  const { hospitals } = useHospitals();
  const active = useMemo(() => hospitals.filter((h) => h.active !== false), [hospitals]);
  const [hospitalId, setHospitalId] = useState('');
  const [data, setData] = useState(null);

  const load = useCallback(async () => {
    setData(null);
    try {
      setData(await api('/dashboard', { query: { hospitalId: hospitalId || undefined } }));
    } catch (err) {
      notify.error(err);
    }
  }, [hospitalId, notify]);
  useEffect(() => { load(); }, [load]);

  const week = data ? `${weekDay(data.weekStart, 'sáb')} a ${weekDay(data.weekEnd, 'sex')}` : '';
  const maxSurgeries = Math.max(1, ...(data?.surgeriesByHospital || []).map((h) => h.surgeries));
  const alerts = data?.replenishmentAlerts || [];

  return (
    <>
      <PageHeader title="Painel" subtitle={data ? `Semana de fechamento: ${week}` : 'Carregando…'}
        actions={(
          <TextField select size="small" label="Hospital" value={hospitalId} sx={{ minWidth: 240 }}
            SelectProps={{ displayEmpty: true }} InputLabelProps={{ shrink: true }}
            onChange={(e) => setHospitalId(e.target.value === '' ? '' : Number(e.target.value))}>
            <MenuItem value="">Todos os hospitais</MenuItem>
            {active.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
          </TextField>
        )} />

      {!data ? (
        <Stack spacing={2}>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' } }}>
            {[1, 2, 3, 4].map((i) => <Skeleton key={i} variant="rounded" height={118} />)}
          </Box>
          <Skeleton variant="rounded" height={260} />
        </Stack>
      ) : (
        <Stack spacing={2.5}>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' } }}>
            <Kpi label="Itens em estoque" value={number(data.stockUnits)} hint="Hospitais + sala, lotes dentro da validade" to="/estoque" />
            <Kpi label="Vencem em 30 dias" value={number(data.lotsExpiringIn30Days)} hint="Lotes a priorizar" tone={data.lotsExpiringIn30Days ? 'warning' : undefined} to="/relatorios" />
            <Kpi label="Vencidos" value={number(data.expiredLots)} hint="Lotes fora da contagem de reposição" tone={data.expiredLots ? 'error' : undefined} to="/relatorios" />
            <Kpi label="Cirurgias na semana" value={number(data.weekSurgeries)} hint="Fechamento na sexta (em aberto e concluídas)" to="/relatorios" />
          </Box>

          <Box sx={{ display: 'grid', gap: 2.5, alignItems: 'start', gridTemplateColumns: { xs: '1fr', lg: '3fr 2fr' } }}>
            <Card title="Reposição necessária"
              action={<Link component={RouterLink} to="/reposicao" variant="body2" sx={{ fontWeight: 500 }}>Ver reposição</Link>}>
              {alerts.length === 0 ? (
                <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>Tudo no ideal: nada a repor ou pedir.</Typography>
              ) : (
                <Stack divider={<Box sx={{ borderTop: `1px solid ${tokens.divider}` }} />}>
                  {alerts.slice(0, ALERTS_SHOWN).map((a) => (
                    <Box key={`${a.hospitalId}-${a.materialId}`} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1.5, py: 1 }}>
                      <Box sx={{ minWidth: 0 }}>
                        <Typography sx={{ fontWeight: 500, fontSize: 14 }} noWrap>{a.description}</Typography>
                        <Typography variant="body2" color="text.secondary"><span style={mono}>{a.ref}</span> · {a.hospital}</Typography>
                      </Box>
                      <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                        {a.replenishFromStoreroom > 0 && <StatusChip tone="warning">Repor {a.replenishFromStoreroom} da sala</StatusChip>}
                        {a.orderFromSupplier > 0 && <StatusChip tone="error">Pedir {a.orderFromSupplier} à empresa</StatusChip>}
                      </Box>
                    </Box>
                  ))}
                  {alerts.length > ALERTS_SHOWN && (
                    <Typography variant="body2" color="text.secondary" sx={{ pt: 1 }}>
                      E mais {alerts.length - ALERTS_SHOWN} {alerts.length - ALERTS_SHOWN === 1 ? 'item' : 'itens'} na Reposição.
                    </Typography>
                  )}
                </Stack>
              )}
            </Card>

            <Card title="Cirurgias por hospital" action={<Typography variant="caption" color="text.secondary">{week}</Typography>}>
              {data.surgeriesByHospital.length === 0 ? (
                <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>Nenhuma cirurgia nesta semana.</Typography>
              ) : (
                <Stack spacing={1.25}>
                  {data.surgeriesByHospital.map((h) => (
                    <Box key={h.hospitalId}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                        <Typography variant="body2">{h.hospital}</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{h.surgeries}</Typography>
                      </Box>
                      <Box sx={{ height: 8, borderRadius: 4, backgroundColor: tokens.divider }}>
                        <Box sx={{ height: 8, borderRadius: 4, width: `${(h.surgeries / maxSurgeries) * 100}%`, backgroundColor: tokens.primary }} />
                      </Box>
                    </Box>
                  ))}
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', pt: 1, borderTop: `1px solid ${tokens.divider}` }}>
                    <Typography variant="body2" color="text.secondary">Total da semana</Typography>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                      {data.weekSurgeries} {data.weekSurgeries === 1 ? 'cirurgia' : 'cirurgias'}
                    </Typography>
                  </Box>
                </Stack>
              )}
            </Card>
          </Box>

          <Card title="Últimas movimentações"
            action={<Link component={RouterLink} to="/relatorios" variant="body2" sx={{ fontWeight: 500 }}>Ver todas</Link>}>
            {data.recentMovements.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>Nenhuma movimentação.</Typography>
            ) : (
              <TableContainer>
                <Table size="small" sx={{ minWidth: 760 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Data</TableCell><TableCell>Tipo</TableCell><TableCell>Origem → Destino</TableCell>
                      <TableCell>Item</TableCell><TableCell align="right">Qtd.</TableCell><TableCell>Responsável</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.recentMovements.map((m) => {
                      const from = place(m.sourceHospital, m.sourceLocation);
                      const to = m.type === 'SUPPLIER_RETURN' ? 'Baumer' : place(m.destinationHospital, m.destinationLocation);
                      return (
                        <TableRow key={m.id} hover>
                          <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(m.date)}</TableCell>
                          <TableCell>{MOVEMENT_TYPES[m.type] || m.type}</TableCell>
                          <TableCell>{[from, to].filter(Boolean).join(' → ') || '—'}</TableCell>
                          <TableCell sx={mono}>{m.ref} · {m.lot}</TableCell>
                          <TableCell align="right">{m.quantity}</TableCell>
                          <TableCell>{m.user || '—'}</TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Card>
        </Stack>
      )}
    </>
  );
}
