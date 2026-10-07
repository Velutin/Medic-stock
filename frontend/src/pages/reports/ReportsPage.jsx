import { useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  Box, Button, ButtonBase, IconButton, MenuItem, Paper, Stack, TextField, Typography,
} from '@mui/material';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';
import PictureAsPdfOutlined from '@mui/icons-material/PictureAsPdfOutlined';
import PageHeader from '../../components/PageHeader';
import { downloadFile } from '../../api/download';
import useHospitals from '../../hooks/useHospitals';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';
import { monthStartIso, todayIso } from './reportUtils';
import {
  CancellationsReport, DeliveriesReport, LoansReport, MOVEMENT_TYPES, MovementsReport, ValidityReport, WeeklyReport,
} from './SimpleReports';
import PendingReport from './PendingReport';
import ConsumptionReport from './ConsumptionReport';

const TYPES = [
  { id: 'weekly', name: 'Cirurgias da semana', desc: 'Contagem por hospital no fechamento de sábado a sexta.', pdf: '/reports/weekly-closing/pdf' },
  { id: 'cancellations', name: 'Cancelamentos', desc: 'Cirurgias canceladas por quem lançou, com a proporção sobre o total lançado.', pdf: '/reports/cancellations/pdf' },
  { id: 'pending', name: 'Pendências', desc: 'Itens com pendência na saída, como lote não encontrado.', pdf: '/reports/pending-issues/pdf' },
  { id: 'deliveries', name: 'Entregas', desc: 'Entregas já geradas (transferências e empréstimos), para baixar o PDF novamente.' },
  { id: 'consumption', name: 'Consumo por cirurgia', desc: 'Itens usados em cada cirurgia, com lote e valor da tabela do hospital.', pdf: '/reports/consumption/pdf' },
  { id: 'validity', name: 'Validade', desc: 'Lotes vencidos e que vencem nos próximos 30, 60 ou 90 dias.', pdf: '/reports/validity/pdf' },
  { id: 'loans', name: 'Empréstimos', desc: 'Movimentações entre hospitais.', pdf: '/reports/loans/pdf' },
  { id: 'returns', name: 'Devoluções', desc: 'Itens devolvidos à Baumer, com o motivo.', pdf: '/reports/loans/pdf' },
  { id: 'movements', name: 'Movimentações', desc: 'Entradas, transferências, saídas, empréstimos e devoluções no período.', pdf: '/reports/movements/pdf' },
];

const saturdayOf = (iso) => {
  const d = new Date(`${iso}T12:00:00`);
  d.setDate(d.getDate() - ((d.getDay() + 1) % 7));
  return d.toLocaleDateString('sv-SE');
};
const addDays = (iso, n) => {
  const d = new Date(`${iso}T12:00:00`);
  d.setDate(d.getDate() + n);
  return d.toLocaleDateString('sv-SE');
};
const initialFilters = () => ({
  start: monthStartIso(), end: todayIso(), hospitalId: '', week: saturdayOf(todayIso()),
  patient: '', status: 'OPEN', days: 30, movementType: '', lot: '',
});

/** Reports: choose the report, filter, preview on screen and generate the PDF with the same filters. */
export default function ReportsPage() {
  const notify = useNotify();
  const { hospitals } = useHospitals();
  const active = useMemo(() => hospitals.filter((h) => h.active !== false), [hospitals]);
  // The dashboard can open a report already chosen and filtered (e.g. the open pending issues).
  const { state } = useLocation();
  const [type, setType] = useState(() => (TYPES.some((t) => t.id === state?.report) ? state.report : 'consumption'));
  const [filters, setFilters] = useState(() => ({ ...initialFilters(), ...(state?.filters || {}) }));
  const [patientTyped, setPatientTyped] = useState('');
  const [lotTyped, setLotTyped] = useState('');
  const current = TYPES.find((t) => t.id === type);
  const set = (field, value) => setFilters((f) => ({ ...f, [field]: value }));

  const hospitalId = filters.hospitalId || undefined;
  const period = { start: filters.start, end: filters.end, hospitalId };
  const query = {
    weekly: { date: filters.week, hospitalId },
    cancellations: period,
    pending: { ...period, status: filters.status || undefined },
    deliveries: { ...period, lot: filters.lot || undefined },
    consumption: { ...period, patient: filters.patient || undefined },
    validity: { hospitalId, days: filters.days },
    loans: period,
    returns: period,
    movements: { ...period, type: filters.movementType || undefined },
  }[type];
  const usesPeriod = !['weekly', 'validity'].includes(type);
  const periodError = usesPeriod && filters.start > filters.end;

  const pdf = async () => {
    const q = type === 'loans' ? { ...query, type: 'LOAN' } : type === 'returns' ? { ...query, type: 'RETURN' } : query;
    try {
      await downloadFile(current.pdf, `${current.name.toLowerCase().replace(/\s+/g, '-')}.pdf`, q);
    } catch (err) {
      notify.error(err);
    }
  };

  const clear = () => {
    setFilters(initialFilters());
    setPatientTyped('');
    setLotTyped('');
  };

  return (
    <>
      <PageHeader title="Relatórios" subtitle="Gere, consulte e baixe relatórios em PDF" />
      <Box sx={{ display: 'grid', gap: 1.5, mb: 2.5, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)', xl: 'repeat(5, 1fr)' } }}>
        {TYPES.map((t) => {
          const on = t.id === type;
          return (
            <ButtonBase key={t.id} onClick={() => setType(t.id)} aria-pressed={on}
              sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 0.75, textAlign: 'left', p: 2, borderRadius: '8px',
                backgroundColor: on ? tokens.successBg : '#FFFFFF', border: on ? `2px solid ${tokens.primary}` : `1px solid ${tokens.border}` }}>
              <Typography sx={{ fontWeight: 600, fontSize: 15 }}>{t.name}</Typography>
              <Typography variant="body2" color="text.secondary">{t.desc}</Typography>
            </ButtonBase>
          );
        })}
      </Box>

      <Paper variant="outlined" component="section" sx={{ p: 3 }}>
        <Typography variant="h3" component="h2" sx={{ mb: 2 }}>{current.name}</Typography>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'flex-start', mb: 2.5 }}>
          {type === 'weekly' && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <IconButton aria-label="Semana anterior" onClick={() => set('week', addDays(filters.week, -7))}><ChevronLeft /></IconButton>
              <TextField size="small" type="date" label="Semana que contém" value={filters.week} InputLabelProps={{ shrink: true }}
                onChange={(e) => e.target.value && set('week', saturdayOf(e.target.value))} sx={{ width: 190 }}
                helperText={`${formatDate(filters.week)} a ${formatDate(addDays(filters.week, 6))}`} />
              <IconButton aria-label="Próxima semana" onClick={() => set('week', addDays(filters.week, 7))}
                disabled={addDays(filters.week, 7) > todayIso()}><ChevronRight /></IconButton>
            </Box>
          )}
          {usesPeriod && (
            <>
              <TextField size="small" type="date" label="De" value={filters.start} InputLabelProps={{ shrink: true }}
                onChange={(e) => set('start', e.target.value)} error={periodError} />
              <TextField size="small" type="date" label="Até" value={filters.end} InputLabelProps={{ shrink: true }}
                onChange={(e) => set('end', e.target.value)} error={periodError} helperText={periodError ? 'Data final antes da inicial.' : ''} />
            </>
          )}
          <TextField select size="small" label="Hospital" value={filters.hospitalId} sx={{ minWidth: 220 }}
            SelectProps={{ displayEmpty: true }} InputLabelProps={{ shrink: true }}
            onChange={(e) => set('hospitalId', e.target.value === '' ? '' : Number(e.target.value))}>
            <MenuItem value="">Todos</MenuItem>
            {active.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
          </TextField>
          {type === 'consumption' && (
            <TextField size="small" label="Nome do paciente" value={patientTyped} onChange={(e) => setPatientTyped(e.target.value)}
              onBlur={() => set('patient', patientTyped.trim())}
              onKeyDown={(e) => { if (e.key === 'Enter') set('patient', patientTyped.trim()); }} />
          )}
          {type === 'deliveries' && (
            <TextField size="small" label="Lote" value={lotTyped} autoComplete="off"
              onChange={(e) => setLotTyped(e.target.value.toUpperCase())}
              onBlur={() => set('lot', lotTyped.trim())}
              onKeyDown={(e) => { if (e.key === 'Enter') set('lot', lotTyped.trim()); }}
              helperText={filters.lot ? 'Entregas com este lote no período' : 'Tecle Enter para pesquisar'} />
          )}
          {type === 'pending' && (
            <TextField select size="small" label="Situação" value={filters.status} sx={{ minWidth: 180 }}
              SelectProps={{ displayEmpty: true }} InputLabelProps={{ shrink: true }} onChange={(e) => set('status', e.target.value)}>
              <MenuItem value="OPEN">Em aberto</MenuItem>
              <MenuItem value="RESOLVED">Resolvidas</MenuItem>
              <MenuItem value="DISCARDED">Descartadas</MenuItem>
              <MenuItem value="">Todas</MenuItem>
            </TextField>
          )}
          {type === 'validity' && (
            <TextField select size="small" label="Vencem em até" value={filters.days} sx={{ minWidth: 160 }} onChange={(e) => set('days', Number(e.target.value))}>
              {[0, 30, 60, 90].map((d) => <MenuItem key={d} value={d}>{d === 0 ? 'Só vencidos' : `${d} dias`}</MenuItem>)}
            </TextField>
          )}
          {type === 'movements' && (
            <TextField select size="small" label="Tipo" value={filters.movementType} sx={{ minWidth: 200 }}
              SelectProps={{ displayEmpty: true }} InputLabelProps={{ shrink: true }} onChange={(e) => set('movementType', e.target.value)}>
              <MenuItem value="">Todos</MenuItem>
              {Object.entries(MOVEMENT_TYPES).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
            </TextField>
          )}
          <Box sx={{ flex: 1 }} />
          <Stack direction="row" spacing={1}>
            <Button variant="text" onClick={clear}>Limpar filtros</Button>
            {current.pdf && (
              <Button variant="contained" startIcon={<PictureAsPdfOutlined />} onClick={pdf} disabled={periodError}>Gerar PDF</Button>
            )}
          </Stack>
        </Box>

        {periodError ? null : (
          <>
            {type === 'weekly' && <WeeklyReport query={query} />}
            {type === 'cancellations' && <CancellationsReport query={query} />}
            {type === 'pending' && <PendingReport query={query} />}
            {type === 'deliveries' && <DeliveriesReport query={query} />}
            {type === 'consumption' && <ConsumptionReport query={query} />}
            {type === 'validity' && <ValidityReport query={query} />}
            {type === 'loans' && <LoansReport query={query} />}
            {type === 'returns' && <LoansReport query={query} returns />}
            {type === 'movements' && <MovementsReport query={query} />}
          </>
        )}
      </Paper>
    </>
  );
}
