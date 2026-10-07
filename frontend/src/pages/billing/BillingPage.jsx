import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Box, Button, MenuItem, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableContainer, TableHead,
  TablePagination, TableRow, TextField, Typography,
} from '@mui/material';
import FileDownloadOutlined from '@mui/icons-material/FileDownloadOutlined';
import PictureAsPdfOutlined from '@mui/icons-material/PictureAsPdfOutlined';
import PercentOutlined from '@mui/icons-material/PercentOutlined';
import PageHeader from '../../components/PageHeader';
import { api } from '../../api/client';
import { downloadFile } from '../../api/download';
import useHospitals from '../../hooks/useHospitals';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDate, formatMoney, priceTableLabel } from '../../utils/format';
import MonthPicker, { currentMonth, periodLabel } from './MonthPicker';
import RatesDialog from './RatesDialog';
import SurgeryItemsDialog from './SurgeryItemsDialog';

const PAGE = 10;
const pct = (v) => `${Number(v).toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`;

function Kpi({ label, value, hint }) {
  return (
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography sx={{ fontSize: 26, fontWeight: 700, my: 0.5 }}>{value}</Typography>
      <Typography variant="caption" color="text.secondary">{hint}</Typography>
    </Paper>
  );
}

/**
 * Billing of completed surgeries (by the month of the surgery date), with the values of each hospital's price table:
 * the total, the commission over the total and the share over the commission, with the rates in effect
 * in that month. Current month by default; several months can be chosen.
 */
export default function BillingPage() {
  const notify = useNotify();
  const { hospitals } = useHospitals();
  const options = useMemo(() => hospitals.filter((h) => h.type === 'HOSPITAL'), [hospitals]);
  const [hospitalId, setHospitalId] = useState('');
  const [months, setMonths] = useState([currentMonth()]);
  const [data, setData] = useState(null);
  const [rates, setRates] = useState([]);
  const [page, setPage] = useState(0);
  const [itemsOf, setItemsOf] = useState(null);
  const [ratesOpen, setRatesOpen] = useState(false);

  const query = useMemo(() => ({ months, hospitalId: hospitalId || undefined }), [months, hospitalId]);

  const load = useCallback(async () => {
    setData(null);
    try {
      const [billing, rateList] = await Promise.all([api('/billing', { query }), api('/billing-rates')]);
      setData(billing);
      setRates(rateList);
      setPage(0);
    } catch (err) {
      notify.error(err);
      setData({ surgeries: [], byHospital: [], surgeryCount: 0, itemCount: 0, totalValue: 0, commission: 0, share: 0, averagePerSurgery: 0 });
    }
  }, [query, notify]);

  useEffect(() => { load(); }, [load]);

  /** Labels with the rates of the period ("20%"), or generic when the months use different rates. */
  const labels = useMemo(() => {
    const used = (data?.surgeries || []);
    const commissions = [...new Set(used.map((s) => Number(s.commissionRate)))];
    const shares = [...new Set(used.map((s) => Number(s.shareRate)))];
    const latest = rates[0];
    const c = commissions.length === 1 ? commissions[0] : commissions.length === 0 && latest ? Number(latest.commissionRate) : null;
    const s = shares.length === 1 ? shares[0] : shares.length === 0 && latest ? Number(latest.shareRate) : null;
    return {
      commission: c === null ? 'Comissão (percentuais variados)' : `${pct(c)} do valor total`,
      share: s === null ? 'Parte sobre a comissão' : `${pct(s)} sobre os ${c === null ? 'valores da comissão' : pct(c)}`,
      commissionShort: c === null ? 'Comissão' : pct(c),
      shareShort: s === null ? 'Parte' : `${pct(s)} s/ ${c === null ? 'comissão' : pct(c)}`,
    };
  }, [data, rates]);

  const download = async (kind) => {
    const name = `faturamento-${[...months].sort().join('_')}${hospitalId ? `-${options.find((h) => h.id === hospitalId)?.name}` : ''}`;
    try {
      await downloadFile(`/billing/${kind}`, `${name}.${kind}`, query);
    } catch (err) {
      notify.error(err);
    }
  };

  const surgeries = data?.surgeries || [];
  const visible = surgeries.slice(page * PAGE, page * PAGE + PAGE);

  return (
    <>
      <PageHeader title="Faturamento" subtitle="Cirurgias concluídas, com valores pela tabela de cada hospital"
        actions={(
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
            <Button variant="outlined" startIcon={<PercentOutlined />} onClick={() => setRatesOpen(true)}>Percentuais</Button>
            <Button variant="outlined" startIcon={<FileDownloadOutlined />} onClick={() => download('xlsx')} disabled={!data}>Exportar planilha</Button>
            <Button variant="contained" startIcon={<PictureAsPdfOutlined />} onClick={() => download('pdf')} disabled={!data}>Gerar PDF</Button>
          </Box>
        )} />

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'center', mb: 2.5 }}>
        <TextField select size="small" label="Hospital" value={hospitalId} sx={{ minWidth: 240 }}
          SelectProps={{ displayEmpty: true }} InputLabelProps={{ shrink: true }}
          onChange={(e) => setHospitalId(e.target.value === '' ? '' : Number(e.target.value))}>
          <MenuItem value="">Todos</MenuItem>
          {options.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
        </TextField>
        <MonthPicker value={months} onChange={setMonths} />
        <Typography variant="caption" color="text.secondary">Período pela data da cirurgia (só cirurgias concluídas).</Typography>
      </Box>

      {data === null ? (
        <Stack spacing={2}><Skeleton variant="rounded" height={110} /><Skeleton variant="rounded" height={240} /></Stack>
      ) : (
        <Stack spacing={2.5}>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' } }}>
            <Kpi label="Valor total do período" value={formatMoney(data.totalValue)}
              hint={`${data.surgeryCount} ${data.surgeryCount === 1 ? 'cirurgia concluída' : 'cirurgias concluídas'} · ${periodLabel(months)}`} />
            <Kpi label={labels.commission} value={formatMoney(data.commission)} hint="Sobre o valor total do período" />
            <Kpi label={labels.share} value={formatMoney(data.share)} hint="Calculado sobre o valor da comissão" />
            <Kpi label="Média por cirurgia" value={formatMoney(data.averagePerSurgery)} hint="No período" />
          </Box>

          {data.itemsWithoutValue > 0 && (
            <Alert severity="warning">
              {data.itemsWithoutValue} {data.itemsWithoutValue === 1 ? 'item está' : 'itens estão'} sem valor na tabela do hospital e
              {data.itemsWithoutValue === 1 ? ' não entra' : ' não entram'} no total até o valor ser cadastrado em Cadastros → Valores.
            </Alert>
          )}

          <Paper variant="outlined" component="section" sx={{ p: 3 }}>
            <Typography variant="h3" component="h2" sx={{ mb: 2 }}>Por hospital</Typography>
            {data.byHospital.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>Nenhuma cirurgia concluída no período.</Typography>
            ) : (
              <TableContainer>
                <Table size="small" sx={{ minWidth: 760 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Hospital</TableCell><TableCell>Tabela</TableCell><TableCell align="right">Cirurgias</TableCell>
                      <TableCell align="right">Itens</TableCell><TableCell align="right">Valor total</TableCell>
                      <TableCell align="right">{labels.commissionShort}</TableCell><TableCell align="right">{labels.shareShort}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.byHospital.map((h) => (
                      <TableRow key={h.hospitalId} hover>
                        <TableCell>{h.hospital}</TableCell>
                        <TableCell>{priceTableLabel(h.priceTableType)}</TableCell>
                        <TableCell align="right">{h.surgeryCount}</TableCell>
                        <TableCell align="right">{h.itemCount}</TableCell>
                        <TableCell align="right">{formatMoney(h.totalValue)}</TableCell>
                        <TableCell align="right">{formatMoney(h.commission)}</TableCell>
                        <TableCell align="right">{formatMoney(h.share)}</TableCell>
                      </TableRow>
                    ))}
                    <TableRow sx={{ '& td': { fontWeight: 700 } }}>
                      <TableCell colSpan={2}>Total</TableCell>
                      <TableCell align="right">{data.surgeryCount}</TableCell>
                      <TableCell align="right">{data.itemCount}</TableCell>
                      <TableCell align="right">{formatMoney(data.totalValue)}</TableCell>
                      <TableCell align="right">{formatMoney(data.commission)}</TableCell>
                      <TableCell align="right">{formatMoney(data.share)}</TableCell>
                    </TableRow>
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Paper>

          <Paper variant="outlined" component="section" sx={{ p: 3 }}>
            <Typography variant="h3" component="h2" sx={{ mb: 2 }}>Cirurgias do período</Typography>
            {surgeries.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>Nenhuma cirurgia concluída no período.</Typography>
            ) : (
              <>
                <TableContainer>
                  <Table size="small" sx={{ minWidth: 860 }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>Data</TableCell><TableCell>Paciente</TableCell><TableCell>Hospital</TableCell>
                        <TableCell align="right">Itens</TableCell><TableCell align="right">Valor</TableCell>
                        <TableCell align="right">{labels.commissionShort}</TableCell><TableCell align="right">{labels.shareShort}</TableCell><TableCell />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {visible.map((s) => (
                        <TableRow key={s.surgeryId} hover>
                          <TableCell>{formatDate(s.surgeryDate)}</TableCell>
                          <TableCell>{s.patientName}</TableCell>
                          <TableCell>{s.hospital}</TableCell>
                          <TableCell align="right">
                            {s.itemCount}
                            {s.itemsWithoutValue > 0 && <Typography component="span" variant="caption" color="warning.main"> ({s.itemsWithoutValue} sem valor)</Typography>}
                          </TableCell>
                          <TableCell align="right">{formatMoney(s.totalValue)}</TableCell>
                          <TableCell align="right">{formatMoney(s.commission)}</TableCell>
                          <TableCell align="right">{formatMoney(s.share)}</TableCell>
                          <TableCell align="right"><Button size="small" onClick={() => setItemsOf(s.surgeryId)}>Ver itens</Button></TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
                {surgeries.length > PAGE && (
                  <TablePagination component="div" count={surgeries.length} page={page} rowsPerPage={PAGE} rowsPerPageOptions={[PAGE]}
                    onPageChange={(_, p) => setPage(p)} labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
                )}
              </>
            )}
          </Paper>
        </Stack>
      )}

      <SurgeryItemsDialog surgeryId={itemsOf} onClose={() => setItemsOf(null)} />
      <RatesDialog open={ratesOpen} onClose={() => setRatesOpen(false)} onChanged={load} />
    </>
  );
}
