import { useCallback, useEffect, useState } from 'react';
import {
  Alert, Box, Button, IconButton, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableContainer, TableHead,
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
 * Stock by lot, in one of the two locations. "Every hospital" locates a lot anywhere.
 *
 *  - location HOSPITAL (administrators and read-only users): what is inside each hospital, with the
 *    storeroom balance alongside for reference. The pencil edits the item and the hospital balance.
 *  - location STOREROOM (administrators): what is in the storerooms waiting to be transferred, including
 *    the storeroom of a distribution center. The pencil edits the item and the storeroom balance.
 */
export default function LotView({ hospitals, hospitalId, onHospitalChange, canAdjust, reloadKey,
                                  location = 'HOSPITAL', term, onTermChange, misplaced = false, onMisplacedChange }) {
  const notify = useNotify();
  const isMobile = useMediaQuery('(max-width:899.95px)');
  const storeroom = location === 'STOREROOM';
  // The search lives in the page so the export button of the header can send the same filters
  const debouncedTerm = useDebounced(term);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [data, setData] = useState(null);
  const [adjusting, setAdjusting] = useState(null);
  const [misplacedCount, setMisplacedCount] = useState(0);

  const load = useCallback(async () => {
    try {
      setData(await api(storeroom ? '/stock/storeroom' : '/stock/lots',
        { query: { term: debouncedTerm, hospitalId, page, size, misplaced: misplaced || undefined } }));
    } catch (err) {
      notify.error(err);
      setData({ content: [], totalElements: 0 });
    }
  }, [storeroom, debouncedTerm, hospitalId, page, size, misplaced, notify]);

  useEffect(() => { load(); }, [load, reloadKey]);
  useEffect(() => { setPage(0); }, [debouncedTerm, hospitalId, misplaced]);

  /*
   * A hospital supplied by a distribution center has no storeroom of its own. If some balance is sitting in
   * one anyway (an initial-stock import, say), it is reported here instead of being hidden - hidden material
   * is material lost. Normally this count is zero and nothing shows up.
   */
  useEffect(() => {
    if (!storeroom) return;
    let active = true;
    api('/stock/storeroom', { query: { misplaced: true, size: 1 } })
      .then((r) => { if (active) setMisplacedCount(r.totalElements); })
      .catch(() => { /* the warning is a courtesy: failing to count it must not break the screen */ });
    return () => { active = false; };
  }, [storeroom, reloadKey]);

  const allHospitals = !hospitalId;
  const rows = data?.content || [];

  // A distribution center keeps no balance inside the hospital, so there is nothing to edit in that column;
  // in the storeroom view its storeroom is exactly where its stock is, and the pencil belongs there.
  const editable = (r) => canAdjust
    && (storeroom || hospitals.find((h) => h.id === r.hospitalId)?.type !== 'DISTRIBUTION_CENTER');

  const quantityCell = (r) => (
    <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
      <Typography component="span" sx={{ fontWeight: 600, fontSize: 14 }}>
        {storeroom ? r.storeroomQuantity : r.hospitalQuantity}
      </Typography>
      {editable(r) && (
        <Tooltip title="Editar item">
          <IconButton size="small"
            aria-label={`Editar o lote ${r.lot} ${storeroom ? `na sala de ${r.hospital}` : `em ${r.hospital}`}`}
            onClick={() => setAdjusting(r)}>
            <EditOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
    </Box>
  );

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 } }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
        <HospitalSelect hospitals={hospitals} value={hospitalId} onChange={onHospitalChange}
          allLabel={storeroom ? 'Todas as salas' : 'Todos os hospitais'}
          size="small" sx={{ minWidth: { xs: '100%', sm: 260 } }} />
        <TextField type="search" size="small" label="Buscar" placeholder="Lote, REF, nome, descrição ou GTIN" value={term}
          onChange={(e) => onTermChange(e.target.value)} sx={{ flex: 1, minWidth: { xs: '100%', sm: 280 } }} />
      </Box>

      {storeroom && misplaced && (
        <Alert severity="warning" sx={{ mb: 2 }}
          action={<Button size="small" onClick={() => onMisplacedChange(false)}>Voltar</Button>}>
          Saldo na sala de hospital atendido por centro de distribuição. A sala desses hospitais é a do centro,
          então este saldo não deveria estar aqui — transfira para dentro do hospital ou devolva à sala do centro.
        </Alert>
      )}
      {storeroom && !misplaced && misplacedCount > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}
          action={<Button size="small" onClick={() => onMisplacedChange(true)}>Ver</Button>}>
          {misplacedCount === 1 ? 'Há 1 lote com saldo' : `Há ${misplacedCount} lotes com saldo`} na sala de
          hospital atendido por centro de distribuição, fora desta lista.
        </Alert>
      )}

      {data === null ? (
        <Stack spacing={1}>{[1, 2, 3, 4].map((i) => <Skeleton key={i} variant="rounded" height={44} />)}</Stack>
      ) : rows.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
          {debouncedTerm ? 'Nenhum lote encontrado para esta busca.'
            : storeroom ? 'Nenhum item na sala.' : 'Nenhum item em estoque neste local.'}
        </Typography>
      ) : isMobile ? (
        <Stack spacing={1.25}>
          {rows.map((r) => (
            <Paper key={`${r.lotId}-${r.hospitalId}`} variant="outlined" sx={{ p: 1.5 }}>
              <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{r.component || r.description}{r.size ? ` · ${r.size}` : ''}</Typography>
              <Typography variant="body2" color="text.secondary">
                REF <span style={mono}>{r.ref}</span> · lote <span style={mono}>{r.lot}</span>
              </Typography>
              {allHospitals && (
                <Typography variant="body2" color="text.secondary">{storeroom ? `Sala de ${r.hospital}` : r.hospital}</Typography>
              )}
              <Box sx={{ mt: 1 }}><ExpiryCell row={r} /></Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 1 }}>
                <Typography variant="body2" component="div">{storeroom ? 'Na sala' : 'No hospital'}: {quantityCell(r)}</Typography>
                {!storeroom && <Typography variant="body2" color="text.secondary">Na sala: {r.storeroomQuantity}</Typography>}
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
                {allHospitals && <TableCell>{storeroom ? 'Sala' : 'Hospital'}</TableCell>}
                <TableCell align="right">{storeroom ? 'Na sala' : 'No hospital'}</TableCell>
                {!storeroom && <TableCell align="right">Na sala</TableCell>}
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
                  {!storeroom && <TableCell align="right">{r.storeroomQuantity}</TableCell>}
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

      <AdjustDialog row={adjusting} location={location} onClose={() => setAdjusting(null)}
        onSaved={() => { setAdjusting(null); load(); }} />
    </Paper>
  );
}
