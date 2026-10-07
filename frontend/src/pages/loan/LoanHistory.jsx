import { Fragment, useEffect, useState } from 'react';
import {
  Box, Collapse, IconButton, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableContainer, TableHead,
  TablePagination, TableRow, Typography,
} from '@mui/material';
import KeyboardArrowDown from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUp from '@mui/icons-material/KeyboardArrowUp';
import PictureAsPdfOutlined from '@mui/icons-material/PictureAsPdfOutlined';
import { downloadFile } from '../../api/download';
import { useNotify } from '../../notifications/NotificationProvider';
import StatusChip from '../../components/StatusChip';
import { formatDate, formatDateTime } from '../../utils/format';
import { tokens } from '../../theme';

const PAGE = 10;
const mono = { fontFamily: tokens.mono, fontSize: 13 };
const whereLabel = (location) => (location === 'STOREROOM' ? 'sala' : 'hospital');

/** Latest loans and returns; each one opens its items. */
export default function LoanHistory({ history }) {
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState(null);
  const list = history || [];
  useEffect(() => { if (page * PAGE >= list.length) setPage(0); }, [list.length, page]);
  const rows = list.slice(page * PAGE, page * PAGE + PAGE);

  const pdf = async (l) => {
    try {
      await downloadFile(`/loans/${l.id}/pdf`, l.type === 'RETURN' ? `devolucao-${l.id}.pdf` : `entrega-E-${l.id}.pdf`);
    } catch (err) {
      notify.error(err);
    }
  };

  return (
    <Paper variant="outlined" component="section" sx={{ p: 3, mt: 2.5 }}>
      <Typography variant="h3" component="h2" sx={{ mb: 2 }}>Histórico de empréstimos e devoluções</Typography>
      {history === null ? (
        <Stack spacing={1}>{[1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={44} />)}</Stack>
      ) : list.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>Nenhum registro.</Typography>
      ) : (
        <>
          <TableContainer>
            <Table size="small" sx={{ minWidth: 720 }}>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 48 }} /><TableCell>Data</TableCell><TableCell>Tipo</TableCell><TableCell>Origem</TableCell>
                  <TableCell>Destino</TableCell><TableCell align="right">Itens</TableCell><TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((l) => {
                  const expanded = open === l.id;
                  const units = l.items.reduce((s, i) => s + i.quantity, 0);
                  const isReturn = l.type === 'RETURN';
                  return (
                    <Fragment key={l.id}>
                      <TableRow hover sx={{ '& > td': { borderBottom: expanded ? 'none' : undefined } }}>
                        <TableCell>
                          <IconButton size="small" aria-label={expanded ? 'Ocultar itens' : 'Ver itens'} aria-expanded={expanded}
                            onClick={() => setOpen(expanded ? null : l.id)}>
                            {expanded ? <KeyboardArrowUp /> : <KeyboardArrowDown />}
                          </IconButton>
                        </TableCell>
                        <TableCell>{formatDate(l.createdAt)}</TableCell>
                        <TableCell>
                          <StatusChip tone={isReturn ? 'info' : 'neutral'}>{isReturn ? 'Devolução' : 'Empréstimo'}</StatusChip>
                        </TableCell>
                        <TableCell>{l.sourceHospital} <Typography component="span" variant="caption" color="text.secondary">({whereLabel(l.sourceLocation)})</Typography></TableCell>
                        <TableCell>{isReturn ? 'Baumer' : l.destinationHospital}</TableCell>
                        <TableCell align="right">{units}</TableCell>
                        <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                          <IconButton aria-label={`PDF ${isReturn ? 'da devolução' : 'do empréstimo'} ${l.id}`} onClick={() => pdf(l)}>
                            <PictureAsPdfOutlined fontSize="small" />
                          </IconButton>
                        </TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell colSpan={7} sx={{ py: 0, backgroundColor: tokens.background }}>
                          <Collapse in={expanded} unmountOnExit>
                            <Box sx={{ py: 1.5, px: { md: 2 } }}>
                              <Table size="small">
                                <TableHead>
                                  <TableRow><TableCell>REF</TableCell><TableCell>Material</TableCell><TableCell>Lote</TableCell><TableCell>Validade</TableCell><TableCell align="right">Qtd.</TableCell></TableRow>
                                </TableHead>
                                <TableBody>
                                  {l.items.map((i, k) => (
                                    <TableRow key={k}>
                                      <TableCell sx={mono}>{i.ref}</TableCell><TableCell>{i.description}</TableCell>
                                      <TableCell sx={mono}>{i.lot}</TableCell><TableCell sx={mono}>{formatDate(i.expiryDate)}</TableCell>
                                      <TableCell align="right">{i.quantity}</TableCell>
                                    </TableRow>
                                  ))}
                                </TableBody>
                              </Table>
                              <Stack spacing={0.5} sx={{ mt: 1.5 }}>
                                {l.returnReason && <Typography variant="body2"><strong>Motivo:</strong> {l.returnReason}</Typography>}
                                {l.notes && <Typography variant="body2"><strong>Observação:</strong> {l.notes}</Typography>}
                                <Typography variant="caption" color="text.secondary">
                                  Registrado {l.createdBy ? `por ${l.createdBy} ` : ''}em {formatDateTime(l.createdAt)}
                                </Typography>
                              </Stack>
                            </Box>
                          </Collapse>
                        </TableCell>
                      </TableRow>
                    </Fragment>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
          {list.length > PAGE && (
            <TablePagination component="div" count={list.length} page={page} rowsPerPage={PAGE} rowsPerPageOptions={[PAGE]}
              onPageChange={(_, p) => setPage(p)} labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
          )}
        </>
      )}
    </Paper>
  );
}
