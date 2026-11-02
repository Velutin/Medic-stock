import { useEffect, useMemo, useState } from 'react';
import {
  Alert, Box, Button, Checkbox, Chip, MenuItem, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TablePagination, TableRow, TextField, Typography,
} from '@mui/material';
import EventBusyOutlined from '@mui/icons-material/EventBusyOutlined';
import PictureAsPdfOutlined from '@mui/icons-material/PictureAsPdfOutlined';
import PageHeader from '../../components/PageHeader';
import ColorSwatch from '../../components/ColorSwatch';
import { useAuth } from '../../auth/AuthProvider';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';
import { lotContains } from '../../utils/lot';

const mono = { fontFamily: tokens.mono, fontSize: 13 };
const PAGE_SIZE = 25;
const materialName = (r) => (r.component ? `${r.component}${r.size ? ` · ${r.size}` : ''}` : r.description);

function SummaryRow({ label, children }) {
  return (
    <>
      <Typography component="dt" variant="body2" color="text.secondary">{label}</Typography>
      <Typography component="dd" variant="body2" sx={{ m: 0, fontWeight: 500 }}>{children}</Typography>
    </>
  );
}

/** Computer version: lots inside the hospital with the quantity to withdraw, and the withdrawal summary. */
export default function StoreroomReturnDesktop({ draft }) {
  const { user } = useAuth();
  const [term, setTerm] = useState('');
  const [page, setPage] = useState(0);
  const { hospital, storeroom, rows, selection, totals } = draft;

  useEffect(() => { setPage(0); }, [term, draft.hospitalId]);

  const filtered = useMemo(() => {
    const t = term.trim().toUpperCase();
    if (!t) return rows;
    return rows.filter((r) => lotContains(r.lot, t) || [r.ref, r.description, r.component].some((v) => v && v.toUpperCase().includes(t)));
  }, [rows, term]);
  const visible = filtered.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);
  const expiredAvailable = useMemo(() => rows.some((r) => r.expired), [rows]);

  return (
    <>
      <PageHeader title="Retirada para a sala" subtitle="Tira itens de dentro do hospital e devolve ao estoque da sala" />

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start', mb: 2.5 }}>
        <TextField select label="Hospital de origem" value={draft.hospitalId} sx={{ minWidth: 260 }}
          onChange={(e) => draft.changeHospital(Number(e.target.value))} disabled={draft.loadingHospitals}>
          {draft.sources.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
        </TextField>
        <TextField type="search" label="Buscar no hospital" placeholder="REF, material ou lote" value={term}
          onChange={(e) => setTerm(e.target.value)} sx={{ flex: '1 1 260px' }} disabled={!hospital} />
        <Button variant="outlined" startIcon={<EventBusyOutlined />} onClick={draft.fillExpired}
          disabled={!hospital || !expiredAvailable} sx={{ minHeight: 56 }}>
          Marcar todos os vencidos
        </Button>
      </Box>

      {hospital && storeroom && storeroom.id !== hospital.id && (
        <Alert severity="info" sx={{ mb: 2.5 }}>
          {hospital.name} é atendido por {storeroom.name} e não tem sala própria: os itens voltam para a sala de {storeroom.name}.
        </Alert>
      )}

      {draft.lastReturn && (
        <Alert severity="success" sx={{ mb: 2.5 }} action={(
          <Button color="inherit" size="small" startIcon={<PictureAsPdfOutlined />}
            onClick={() => draft.downloadPdf(draft.lastReturn)}>
            Baixar PDF novamente
          </Button>
        )}>
          Retirada #{draft.lastReturn.id} registrada: {draft.lastReturn.hospital} → sala de {draft.lastReturn.storeroomHospital}.
        </Alert>
      )}

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2.5, alignItems: 'flex-start' }}>
        <Paper variant="outlined" component="section" sx={{ p: 3, flex: '2 1 620px', minWidth: 0 }}>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'baseline', gap: 1.5, mb: 2 }}>
            <Typography variant="h3" component="h2">Itens dentro deste hospital</Typography>
            <Typography variant="caption" color="text.secondary">Lotes vencidos aparecem e podem ser retirados</Typography>
          </Box>

          {!hospital ? (
            <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
              Escolha o hospital de origem para ver os itens que estão dentro dele.
            </Typography>
          ) : draft.loadingRows ? (
            <Stack spacing={1}>{[1, 2, 3, 4].map((i) => <Skeleton key={i} variant="rounded" height={44} />)}</Stack>
          ) : filtered.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
              {term ? 'Nenhum item do hospital corresponde à busca.' : 'Não há itens dentro deste hospital.'}
            </Typography>
          ) : (
            <>
              <TableContainer>
                <Table size="small" sx={{ minWidth: 780 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell padding="checkbox">Retirar</TableCell><TableCell>REF</TableCell><TableCell>Material</TableCell>
                      <TableCell>Lote</TableCell><TableCell>Validade</TableCell><TableCell align="right">No hospital</TableCell>
                      <TableCell align="right">Retirar</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {visible.map((r) => {
                      const qty = selection[r.lotId] || 0;
                      return (
                        <TableRow key={r.lotId} hover selected={qty > 0}>
                          <TableCell padding="checkbox">
                            <Checkbox checked={qty > 0} inputProps={{ 'aria-label': `Retirar ${materialName(r)}, lote ${r.lot}` }}
                              onChange={(e) => draft.setQuantity(r.lotId, e.target.checked ? 1 : 0)} />
                          </TableCell>
                          <TableCell sx={mono}>{r.ref}</TableCell>
                          <TableCell>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                              <ColorSwatch color={r.color} />{materialName(r)}
                            </Box>
                          </TableCell>
                          <TableCell sx={mono}>{r.lot}</TableCell>
                          <TableCell sx={mono}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                              {formatDate(r.expiryDate)}
                              {r.expired && <Chip label="Vencido" size="small" color="error" variant="outlined" />}
                            </Box>
                          </TableCell>
                          <TableCell align="right">{r.hospitalQuantity}</TableCell>
                          <TableCell align="right" sx={{ py: 0.5 }}>
                            <TextField type="number" size="small" value={qty} sx={{ width: 84 }}
                              onChange={(e) => draft.setQuantity(r.lotId, e.target.value)}
                              inputProps={{ min: 0, max: r.hospitalQuantity, step: 1, style: { textAlign: 'right' },
                                'aria-label': `Quantidade a retirar de ${materialName(r)}, lote ${r.lot}` }} />
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
              {filtered.length > PAGE_SIZE && (
                <TablePagination component="div" count={filtered.length} page={page} rowsPerPage={PAGE_SIZE}
                  onPageChange={(_, p) => setPage(p)} rowsPerPageOptions={[PAGE_SIZE]}
                  labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
              )}
            </>
          )}
        </Paper>

        <Paper variant="outlined" component="section" sx={{ p: 3, flex: '1 1 320px', minWidth: 0, position: 'sticky', top: 24 }}>
          <Stack spacing={2}>
            <Typography variant="h3" component="h2">Resumo da retirada</Typography>
            <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: '12px 16px' }}>
              <SummaryRow label="Sai do hospital">{hospital?.name || '—'}</SummaryRow>
              <SummaryRow label="Volta para a sala de">{storeroom?.name || '—'}</SummaryRow>
              <SummaryRow label="Lotes">{totals.lots}</SummaryRow>
              <SummaryRow label="Unidades">{totals.units}</SummaryRow>
              <SummaryRow label="Responsável">{user?.name}</SummaryRow>
            </Box>
            {totals.expiredLots > 0 && (
              <Alert severity="warning">
                {totals.expiredLots === 1 ? '1 lote vencido' : `${totals.expiredLots} lotes vencidos`} nesta retirada.
              </Alert>
            )}
            <TextField label="Motivo da retirada" value={draft.reason} required multiline minRows={2}
              onChange={(e) => draft.setReason(e.target.value)}
              placeholder="Ex.: tamanho que o hospital não usa mais"
              helperText="Obrigatório; sai impresso no relatório." />
            <TextField label="Observações" value={draft.notes} multiline minRows={2}
              onChange={(e) => draft.setNotes(e.target.value)} />
            <Box sx={{ p: 1.5, borderRadius: '6px', backgroundColor: tokens.background, fontSize: 13, color: tokens.textMuted }}>
              Gera o relatório de retirada no modelo Clássico. As assinaturas são o contrário da entrega: quem
              entrega é o hospital.
            </Box>
            <Button variant="contained" size="large" onClick={draft.submit} sx={{ minHeight: 48 }}
              disabled={draft.sending || totals.units === 0 || !draft.reason.trim()}>
              Retirar e gerar PDF
            </Button>
          </Stack>
        </Paper>
      </Box>
    </>
  );
}
