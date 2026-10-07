import { useEffect, useState } from 'react';
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TableCell, TextField,
  ToggleButton, ToggleButtonGroup, Typography,
} from '@mui/material';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import StatusChip from '../../components/StatusChip';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';
import { Empty, Loading, PagedTable, useReport } from './reportUtils';

const mono = { fontFamily: tokens.mono, fontSize: 13 };
export const REASONS = {
  LOT_NOT_FOUND: 'Lote não encontrado', AMBIGUOUS_LOT: 'Lote com mais de uma validade',
  EXPIRED_LOT: 'Lote vencido', NO_HOSPITAL_BALANCE: 'Sem saldo no hospital',
};
const STATUS = { OPEN: ['warning', 'Em aberto'], RESOLVED: ['success', 'Resolvida'], DISCARDED: ['neutral', 'Descartada'] };

/**
 * Resolves a pending issue: records one of the hospital lots in the surgery (RESOLVED) or closes it with a
 * justification (DISCARDED), e.g. a patient label read by mistake.
 */
function ResolveDialog({ issue, onClose, onDone }) {
  const notify = useNotify();
  const [mode, setMode] = useState('RESOLVED');
  const [lots, setLots] = useState(null);
  const [lotId, setLotId] = useState('');
  const [text, setText] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!issue) return;
    setMode('RESOLVED');
    setLotId('');
    setText('');
    setLots(null);
    api(`/stock/hospital/${issue.hospitalId}`).then((rows) => {
      const inside = rows.filter((r) => r.hospitalQuantity > 0);
      const ref = issue.enteredRef?.toUpperCase();
      setLots(ref ? inside.filter((r) => r.ref.toUpperCase() === ref).concat(inside.filter((r) => r.ref.toUpperCase() !== ref)) : inside);
    }).catch(() => setLots([]));
  }, [issue]);

  const save = async () => {
    setSaving(true);
    try {
      await api(`/pending-issues/${issue.id}`, {
        method: 'PATCH',
        body: mode === 'RESOLVED'
          ? { status: 'RESOLVED', lotId, resolution: text.trim() || 'Lote identificado na conferência' }
          : { status: 'DISCARDED', lotId: null, resolution: text.trim() },
      });
      notify.success(mode === 'RESOLVED' ? 'Pendência resolvida: o lote foi lançado na cirurgia.' : 'Pendência descartada.');
      onDone();
    } catch (err) {
      notify.error(err);
    } finally {
      setSaving(false);
    }
  };

  const valid = mode === 'RESOLVED' ? Boolean(lotId) : text.trim().length > 0;

  return (
    <Dialog open={Boolean(issue)} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ typography: 'h2' }}>Resolver pendência</DialogTitle>
      <DialogContent dividers>
        {issue && (
          <Stack spacing={2}>
            <Typography variant="body2">
              <strong>{issue.patientName}</strong> · {issue.hospital} · {formatDate(issue.surgeryDate)}<br />
              Item lido: <span style={mono}>{issue.enteredCode}</span>{issue.enteredRef ? ` (REF ${issue.enteredRef})` : ''} · {REASONS[issue.reason]}
            </Typography>
            <ToggleButtonGroup exclusive fullWidth color="primary" value={mode} onChange={(_, v) => v && setMode(v)}>
              <ToggleButton value="RESOLVED">Lançar um lote do hospital</ToggleButton>
              <ToggleButton value="DISCARDED">Descartar</ToggleButton>
            </ToggleButtonGroup>
            {mode === 'RESOLVED' && (
              <TextField select label="Lote dentro do hospital" value={lotId} onChange={(e) => setLotId(e.target.value)}
                disabled={lots === null} helperText={lots && lots.length === 0 ? 'Nenhum lote com saldo dentro do hospital.' : ' '}>
                {(lots || []).map((r) => (
                  <MenuItem key={r.lotId} value={r.lotId}>
                    {r.ref} · {r.lot} · Val. {formatDate(r.expiryDate)} · {r.hospitalQuantity} no hospital{r.expired ? ' · vencido' : ''}
                  </MenuItem>
                ))}
              </TextField>
            )}
            <TextField label={mode === 'RESOLVED' ? 'Observação (opcional)' : 'Justificativa'} required={mode === 'DISCARDED'}
              value={text} onChange={(e) => setText(e.target.value)} multiline minRows={2}
              placeholder={mode === 'DISCARDED' ? 'Ex.: etiqueta do paciente lida por engano' : ''} />
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button variant="outlined" onClick={onClose}>Cancelar</Button>
        <Button variant="contained" onClick={save} disabled={!valid || saving}>{mode === 'RESOLVED' ? 'Lançar lote' : 'Descartar'}</Button>
      </DialogActions>
    </Dialog>
  );
}

/** Items left as pending issues at surgery withdrawals; the administrator resolves them here. */
export default function PendingReport({ query }) {
  const { data, reload } = useReport('/reports/pending-issues', query);
  const [resolving, setResolving] = useState(null);
  if (!data) return <Loading />;
  const open = data.filter((r) => r.status === 'OPEN').length;
  return (
    <Stack spacing={1.5}>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <StatusChip tone="warning">{open} em aberto</StatusChip>
        <StatusChip tone="success">{data.length - open} resolvidas ou descartadas</StatusChip>
      </Box>
      {data.length === 0 ? <Empty>Nenhuma pendência no período.</Empty> : (
        <PagedTable items={data} rowKey={(r) => r.id} minWidth={900}
          columns={[{ label: 'Data da cirurgia' }, { label: 'Hospital' }, { label: 'Item lido' }, { label: 'Pendência' }, { label: 'Lançado por' }, { label: 'Situação' }, { label: '' }]}
          row={(r) => (<>
            <TableCell>{formatDate(r.surgeryDate)}<Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{r.patientName}</Typography></TableCell>
            <TableCell>{r.hospital}</TableCell>
            <TableCell><span style={mono}>{r.enteredCode}</span>{r.enteredRef && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>REF {r.enteredRef}</Typography>}</TableCell>
            <TableCell>{REASONS[r.reason] || r.reason}</TableCell>
            <TableCell>{r.createdBy}</TableCell>
            <TableCell>
              <StatusChip tone={STATUS[r.status][0]}>{STATUS[r.status][1]}</StatusChip>
              {r.resolution && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', maxWidth: 220 }}>{r.resolution}</Typography>}
            </TableCell>
            <TableCell align="right">{r.status === 'OPEN' && <Button size="small" variant="outlined" onClick={() => setResolving(r)} sx={{ minHeight: 36 }}>Resolver</Button>}</TableCell>
          </>)} />
      )}
      <ResolveDialog issue={resolving} onClose={() => setResolving(null)} onDone={() => { setResolving(null); reload(); }} />
    </Stack>
  );
}
