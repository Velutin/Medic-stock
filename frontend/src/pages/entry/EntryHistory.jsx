import { Fragment, useCallback, useEffect, useState } from 'react';
import {
  Box, Button, Collapse, IconButton, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableContainer, TableHead,
  TablePagination, TableRow, Typography,
} from '@mui/material';
import EditOutlined from '@mui/icons-material/EditOutlined';
import KeyboardArrowDown from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUp from '@mui/icons-material/KeyboardArrowUp';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDate, formatDateTime } from '../../utils/format';
import { tokens } from '../../theme';

const mono = { fontFamily: tokens.mono, fontSize: 13 };
const PAGE_SIZE = 10;

/** Items received in one entry. */
function EntryItems({ entry }) {
  return (
    <Box sx={{ py: 1.5, px: { md: 2 } }}>
      <Table size="small" aria-label={`Itens da entrada ${entry.id}`}>
        <TableHead>
          <TableRow>
            <TableCell>REF</TableCell><TableCell>Material</TableCell><TableCell>Lote</TableCell>
            <TableCell>Validade</TableCell><TableCell align="right">Qtd.</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {entry.items.map((i) => (
            <TableRow key={i.lotId}>
              <TableCell sx={mono}>{i.ref}</TableCell>
              <TableCell>{i.component ? `${i.component}${i.size ? ` · ${i.size}` : ''}` : i.description}</TableCell>
              <TableCell sx={mono}>{i.lot}</TableCell>
              <TableCell sx={mono}>{formatDate(i.expiryDate)}</TableCell>
              <TableCell align="right">{i.quantity}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {(entry.notes || entry.updatedAt) && (
        <Stack spacing={0.5} sx={{ mt: 1.5 }}>
          {entry.notes && <Typography variant="body2"><strong>Observação:</strong> {entry.notes}</Typography>}
          {entry.updatedAt && (
            <Typography variant="caption" color="text.secondary">
              Corrigida por {entry.updatedBy || '—'} em {formatDateTime(entry.updatedAt)}
            </Typography>
          )}
        </Stack>
      )}
    </Box>
  );
}

/** Latest entries, newest first; each one opens its item list and can be corrected. */
export default function EntryHistory({ reloadKey, editingId, onEdit }) {
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [open, setOpen] = useState(null);

  const load = useCallback(async () => {
    try {
      setData(await api('/stock-entries', { query: { page, size: PAGE_SIZE } }));
    } catch (err) {
      notify.error(err);
      setData({ content: [], totalElements: 0 });
    }
  }, [page, notify]);

  useEffect(() => { load(); }, [load, reloadKey]);
  const rows = data?.content || [];

  return (
    <Paper variant="outlined" component="section" sx={{ p: 3, mt: 2.5 }}>
      <Typography variant="h3" component="h2" sx={{ mb: 2 }}>Últimas entradas</Typography>
      {data === null ? (
        <Stack spacing={1}>{[1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={44} />)}</Stack>
      ) : rows.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
          Nenhuma entrada registrada.
        </Typography>
      ) : (
        <TableContainer>
          <Table size="small" sx={{ minWidth: 720 }}>
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 48 }} /><TableCell>Data</TableCell><TableCell>Destino</TableCell>
                <TableCell align="right">Lotes</TableCell><TableCell align="right">Unidades</TableCell>
                <TableCell>Responsável</TableCell><TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((e) => {
                const expanded = open === e.id;
                return (
                  <Fragment key={e.id}>
                    <TableRow hover selected={editingId === e.id} sx={{ '& > td': { borderBottom: expanded ? 'none' : undefined } }}>
                      <TableCell>
                        <IconButton size="small" aria-label={expanded ? 'Ocultar itens' : 'Ver itens'} aria-expanded={expanded}
                          onClick={() => setOpen(expanded ? null : e.id)}>
                          {expanded ? <KeyboardArrowUp /> : <KeyboardArrowDown />}
                        </IconButton>
                      </TableCell>
                      <TableCell>{formatDate(e.entryDate)}</TableCell>
                      <TableCell>{e.hospital}</TableCell>
                      <TableCell align="right">{e.lots}</TableCell>
                      <TableCell align="right">{e.units}</TableCell>
                      <TableCell>{e.createdBy}</TableCell>
                      <TableCell align="right">
                        <Button size="small" variant="outlined" startIcon={<EditOutlined />} onClick={() => onEdit(e)}
                          disabled={editingId === e.id} sx={{ minHeight: 36 }}>
                          Corrigir
                        </Button>
                      </TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell colSpan={7} sx={{ py: 0, backgroundColor: tokens.background }}>
                        <Collapse in={expanded} unmountOnExit><EntryItems entry={e} /></Collapse>
                      </TableCell>
                    </TableRow>
                  </Fragment>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {data && data.totalElements > PAGE_SIZE && (
        <TablePagination component="div" count={data.totalElements} page={page} rowsPerPage={PAGE_SIZE}
          onPageChange={(_, p) => setPage(p)} rowsPerPageOptions={[PAGE_SIZE]}
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
      )}
    </Paper>
  );
}
