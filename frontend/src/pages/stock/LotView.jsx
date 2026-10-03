import { useCallback, useEffect, useState } from 'react';
import {
  Box, Button, IconButton, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableContainer, TableHead,
  TablePagination, TableRow, TextField, Tooltip, Typography, useMediaQuery,
} from '@mui/material';
import EditOutlined from '@mui/icons-material/EditOutlined';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import HospitalSelect from '../../components/HospitalSelect';
import StatusChip from '../../components/StatusChip';
import AdjustDialog from './AdjustDialog';
import { daysUntil, formatDate } from '../../utils/format';
import { tokens } from '../../theme';

const mono = { fontFamily: tokens.mono, fontSize: 13 };

function ExpiryCell({ row }) {
  const days = daysUntil(row.expiryDate);
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
      <span>{formatDate(row.expiryDate)}</span>
      {row.expired ? <StatusChip tone="error">Vencido</StatusChip>
        : days !== null && days <= 30 ? <StatusChip tone="warning">{days === 0 ? 'Vence hoje' : `Vence em ${days} d`}</StatusChip> : null}
    </Box>
  );
}

function useDebounced(value, delay = 350) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

/**
 * Stock by lot and hospital (administrators and read-only users). "Every hospital" locates a lot anywhere.
 * Administrators adjust the quantity inside the hospital with the pencil.
 */
export default function LotView({ hospitals, hospitalId, onHospitalChange, canAdjust, reloadKey }) {
  const notify = useNotify();
  const isMobile = useMediaQuery('(max-width:899.95px)');
  const [term, setTerm] = useState('');
  const debouncedTerm = useDebounced(term);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [data, setData] = useState(null);
  const [adjusting, setAdjusting] = useState(null);

  const load = useCallback(async () => {
    try {
      setData(await api('/stock/lots', { query: { term: debouncedTerm, hospitalId, page, size } }));
    } catch (err) {
      notify.error(err);
      setData({ content: [], totalElements: 0 });
    }
  }, [debouncedTerm, hospitalId, page, size, notify]);

  useEffect(() => { load(); }, [load, reloadKey]);
  useEffect(() => { setPage(0); }, [debouncedTerm, hospitalId]);

  const allHospitals = !hospitalId;
  const rows = data?.content || [];

  const quantityCell = (r) => (
    <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
      <Typography component="span" sx={{ fontWeight: 600, fontSize: 14 }}>{r.hospitalQuantity}</Typography>
      {canAdjust && hospitals.find((h) => h.id === r.hospitalId)?.type !== 'DISTRIBUTION_CENTER' && (
        <Tooltip title="Ajustar saldo">
          <IconButton size="small" aria-label={`Ajustar saldo do lote ${r.lot} em ${r.hospital}`} onClick={() => setAdjusting(r)}>
            <EditOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
    </Box>
  );

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 } }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
        <HospitalSelect hospitals={hospitals} value={hospitalId} onChange={onHospitalChange} allLabel="Todos os hospitais"
          size="small" sx={{ minWidth: { xs: '100%', sm: 260 } }} />
        <TextField type="search" size="small" label="Buscar" placeholder="Lote, REF, nome, descrição ou GTIN" value={term}
          onChange={(e) => setTerm(e.target.value)} sx={{ flex: 1, minWidth: { xs: '100%', sm: 280 } }} />
      </Box>

      {data === null ? (
        <Stack spacing={1}>{[1, 2, 3, 4].map((i) => <Skeleton key={i} variant="rounded" height={44} />)}</Stack>
      ) : rows.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
          {debouncedTerm ? 'Nenhum lote encontrado para esta busca.' : 'Nenhum item em estoque neste local.'}
        </Typography>
      ) : isMobile ? (
        <Stack spacing={1.25}>
          {rows.map((r) => (
            <Paper key={`${r.lotId}-${r.hospitalId}`} variant="outlined" sx={{ p: 1.5 }}>
              <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{r.component || r.description}{r.size ? ` · ${r.size}` : ''}</Typography>
              <Typography variant="body2" color="text.secondary">
                REF <span style={mono}>{r.ref}</span> · lote <span style={mono}>{r.lot}</span>
              </Typography>
              {allHospitals && <Typography variant="body2" color="text.secondary">{r.hospital}</Typography>}
              <Box sx={{ mt: 1 }}><ExpiryCell row={r} /></Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 1 }}>
                <Typography variant="body2" component="div">No hospital: {quantityCell(r)}</Typography>
                <Typography variant="body2" color="text.secondary">Na sala: {r.storeroomQuantity}</Typography>
              </Box>
            </Paper>
          ))}
        </Stack>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>REF</TableCell>
                <TableCell>Material</TableCell>
                <TableCell>Tam.</TableCell>
                <TableCell>Lote</TableCell>
                <TableCell>Validade</TableCell>
                {allHospitals && <TableCell>Hospital</TableCell>}
                <TableCell align="right">No hospital</TableCell>
                <TableCell align="right">Na sala</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={`${r.lotId}-${r.hospitalId}`} hover sx={r.expired ? { backgroundColor: tokens.errorBg } : undefined}>
                  <TableCell sx={mono}>{r.ref}</TableCell>
                  <TableCell>{r.component || r.description}</TableCell>
                  <TableCell>{r.size}</TableCell>
                  <TableCell sx={mono}>{r.lot}</TableCell>
                  <TableCell><ExpiryCell row={r} /></TableCell>
                  {allHospitals && <TableCell>{r.hospital}</TableCell>}
                  <TableCell align="right">{quantityCell(r)}</TableCell>
                  <TableCell align="right">{r.storeroomQuantity}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {data && data.totalElements > 0 && (
        <TablePagination component="div" count={data.totalElements} page={page} rowsPerPage={size}
          onPageChange={(_, p) => setPage(p)} onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}
          rowsPerPageOptions={[25, 50, 100]} labelRowsPerPage="Por página"
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
      )}

      <AdjustDialog row={adjusting} onClose={() => setAdjusting(null)} onSaved={() => { setAdjusting(null); load(); }} />
    </Paper>
  );
}
