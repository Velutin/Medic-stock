import { useEffect, useMemo, useState } from 'react';
import {
  Box, Button, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination,
  TableRow, TextField, Typography,
} from '@mui/material';
import PageHeader from '../../components/PageHeader';
import ColorSwatch from '../../components/ColorSwatch';
import StatusChip from '../../components/StatusChip';
import ConfirmDialog from '../../components/ConfirmDialog';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';
import LoanForm from './LoanForm';
import LoanHistory from './LoanHistory';
import { lotContains } from '../../utils/lot';

const PAGE = 25;
const mono = { fontFamily: tokens.mono, fontSize: 13 };
const materialName = (r) => (r.component ? `${r.component}${r.size ? ` · ${r.size}` : ''}` : r.description);

/** Computer version: data of the loan or return on the left, source lots with quantities on the right. */
export default function LoanDesktop({ loan }) {
  const [term, setTerm] = useState('');
  const [page, setPage] = useState(0);
  const [confirming, setConfirming] = useState(false);
  const isReturn = loan.kind === 'RETURN';

  useEffect(() => { setPage(0); }, [term, loan.sourceId, loan.location, loan.kind]);
  const filtered = useMemo(() => {
    const t = term.trim().toUpperCase();
    return t ? loan.rows.filter((r) => lotContains(r.lot, t) || [r.ref, r.description, r.component].some((v) => v && v.toUpperCase().includes(t))) : loan.rows;
  }, [loan.rows, term]);
  const visible = filtered.slice(page * PAGE, page * PAGE + PAGE);

  const confirm = async () => {
    setConfirming(false);
    await loan.submit();
  };

  return (
    <>
      <PageHeader title={isReturn ? 'Devolução à Baumer' : 'Novo empréstimo'}
        subtitle={isReturn ? 'Devolve itens à empresa: eles saem de todos os estoques' : 'Transfere itens entre hospitais'} />
      <Box sx={{ display: 'grid', gap: 2.5, alignItems: 'start', gridTemplateColumns: { md: 'minmax(300px, 1fr) minmax(0, 2fr)' } }}>
        <Paper variant="outlined" component="section" sx={{ p: 3 }}>
          <Stack spacing={2}>
            <Typography variant="h3" component="h2">{isReturn ? 'Dados da devolução' : 'Dados do empréstimo'}</Typography>
            <LoanForm loan={loan} />
            <Box sx={{ pt: 1.5, borderTop: `1px solid ${tokens.divider}` }}>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                {loan.totals.lots} {loan.totals.lots === 1 ? 'lote' : 'lotes'} · {loan.totals.units} {loan.totals.units === 1 ? 'unidade' : 'unidades'}
              </Typography>
              <Button variant="contained" fullWidth size="large" disabled={loan.sending || loan.totals.units === 0}
                onClick={() => (isReturn ? setConfirming(true) : loan.submit())}>
                {isReturn ? 'Confirmar devolução' : 'Confirmar empréstimo'}
              </Button>
            </Box>
          </Stack>
        </Paper>

        <Paper variant="outlined" component="section" sx={{ p: 3, minWidth: 0 }}>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 1.5, mb: 2 }}>
            <Typography variant="h3" component="h2">
              Itens {loan.location === 'STOREROOM' ? 'na sala' : 'no hospital'}{loan.source ? ` · ${loan.source.name}` : ''}
            </Typography>
            <TextField type="search" size="small" label="Buscar" placeholder="REF, material ou lote" value={term}
              onChange={(e) => setTerm(e.target.value)} disabled={!loan.source} sx={{ minWidth: 240 }} />
          </Box>
          {!loan.source ? (
            <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>Escolha o hospital de origem.</Typography>
          ) : loan.loadingRows ? (
            <Stack spacing={1}>{[1, 2, 3, 4].map((i) => <Skeleton key={i} variant="rounded" height={44} />)}</Stack>
          ) : filtered.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
              {term ? 'Nenhum item corresponde à busca.' : 'Nenhum item disponível nesta origem.'}
            </Typography>
          ) : (
            <>
              <TableContainer>
                <Table size="small" sx={{ minWidth: 700 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>REF</TableCell><TableCell>Material</TableCell><TableCell>Lote</TableCell><TableCell>Validade</TableCell>
                      <TableCell align="right">Disponível</TableCell><TableCell align="right">{isReturn ? 'Devolver' : 'Emprestar'}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {visible.map((r) => (
                      <TableRow key={r.lotId} hover selected={(loan.selection[r.lotId] || 0) > 0}>
                        <TableCell sx={mono}>{r.ref}</TableCell>
                        <TableCell><Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}><ColorSwatch color={r.color} />{materialName(r)}</Box></TableCell>
                        <TableCell sx={mono}>{r.lot}</TableCell>
                        <TableCell sx={mono}>
                          {formatDate(r.expiryDate)}{r.expired && <Box component="span" sx={{ ml: 1 }}><StatusChip tone="error">Vencido</StatusChip></Box>}
                        </TableCell>
                        <TableCell align="right">{r.available}</TableCell>
                        <TableCell align="right" sx={{ py: 0.5 }}>
                          <TextField type="number" size="small" value={loan.selection[r.lotId] || 0} sx={{ width: 84 }}
                            onChange={(e) => loan.setQuantity(r.lotId, e.target.value)}
                            inputProps={{ min: 0, max: r.available, step: 1, style: { textAlign: 'right' },
                              'aria-label': `Quantidade de ${materialName(r)}, lote ${r.lot}` }} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              {filtered.length > PAGE && (
                <TablePagination component="div" count={filtered.length} page={page} rowsPerPage={PAGE} rowsPerPageOptions={[PAGE]}
                  onPageChange={(_, p) => setPage(p)} labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
              )}
            </>
          )}
        </Paper>
      </Box>

      <LoanHistory history={loan.history} />
      <ConfirmDialog open={confirming} title="Confirmar devolução à Baumer" confirmLabel="Devolver" danger busy={loan.sending}
        message={`${loan.totals.units} ${loan.totals.units === 1 ? 'unidade sai' : 'unidades saem'} do estoque de ${loan.source?.name || ''} e não entram em nenhum outro. Não é possível desfazer.`}
        onConfirm={confirm} onClose={() => setConfirming(false)} />
    </>
  );
}
