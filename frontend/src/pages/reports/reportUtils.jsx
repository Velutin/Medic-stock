import { useCallback, useEffect, useState } from 'react';
import {
  Box, Skeleton, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, Typography,
} from '@mui/material';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';

export const PAGE = 10;
export const todayIso = () => new Date().toLocaleDateString('sv-SE');
export const monthStartIso = () => `${todayIso().slice(0, 8)}01`;
export const pct = (part, total) => (total ? `${((part / total) * 100).toFixed(1).replace('.', ',')}%` : '0%');

/** Loads a report for the preview whenever the filters change. */
export function useReport(path, query, enabled = true) {
  const notify = useNotify();
  const [data, setData] = useState(null);
  const key = JSON.stringify(query);
  const load = useCallback(async () => {
    if (!enabled) return;
    setData(null);
    try {
      setData(await api(path, { query }));
    } catch (err) {
      notify.error(err);
      setData([]);
    }
  }, [path, key, enabled, notify]); // query is compared through its JSON (key)
  useEffect(() => { load(); }, [load]);
  return { data, reload: load };
}

export const Loading = () => <Stack spacing={1}>{[1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={44} />)}</Stack>;
export const Empty = ({ children }) => (
  <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>{children}</Typography>
);

/** Table with 10 rows per page. columns: [{ label, align }]; row(item) returns the cells. */
export function PagedTable({ columns, items, row, rowKey, minWidth = 720, footer }) {
  const [page, setPage] = useState(0);
  useEffect(() => { if (page * PAGE >= items.length) setPage(0); }, [items.length, page]);
  const visible = items.slice(page * PAGE, page * PAGE + PAGE);
  return (
    <Box>
      <TableContainer>
        <Table size="small" sx={{ minWidth }}>
          <TableHead>
            <TableRow>{columns.map((c, i) => <TableCell key={i} align={c.align}>{c.label}</TableCell>)}</TableRow>
          </TableHead>
          <TableBody>
            {visible.map((item) => <TableRow key={rowKey(item)} hover>{row(item)}</TableRow>)}
            {footer}
          </TableBody>
        </Table>
      </TableContainer>
      {items.length > PAGE && (
        <TablePagination component="div" count={items.length} page={page} rowsPerPage={PAGE} rowsPerPageOptions={[PAGE]}
          onPageChange={(_, p) => setPage(p)} labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
      )}
    </Box>
  );
}
