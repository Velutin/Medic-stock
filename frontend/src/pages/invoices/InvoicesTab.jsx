import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Button, Collapse, IconButton, LinearProgress, MenuItem, Pagination, Paper, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import ExpandMoreOutlined from '@mui/icons-material/ExpandMoreOutlined';
import ExpandLessOutlined from '@mui/icons-material/ExpandLessOutlined';
import PictureAsPdfOutlined from '@mui/icons-material/PictureAsPdfOutlined';
import HistoryOutlined from '@mui/icons-material/HistoryOutlined';
import StatusChip from '../../components/StatusChip';
import ColorSwatch from '../../components/ColorSwatch';
import { api } from '../../api/client';
import { API_BASE } from '../../config';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDate, formatMoney } from '../../utils/format';
import { tokens } from '../../theme';
import InvoiceActionsDialog from './InvoiceActionsDialog';

const PAGE_SIZE = 10;
const mono = { fontFamily: tokens.mono, fontSize: 13 };

const STATUS = {
  AWAITING_XML: ['Sem XML', 'warning'],
  OPEN: ['Em transporte', 'info'],
  PARTIAL: ['Parcial', 'warning'],
  RECEIVED: ['Recebida', 'success'],
  CLOSED: ['Encerrada', 'neutral'],
  NOT_SHIPMENT: ['Não é remessa', 'neutral'],
};

const CATEGORIES = [
  { value: '', label: 'Todas as notas' },
  { value: 'IMPLANT', label: 'Notas com implantes' },
  { value: 'INSTRUMENT', label: 'Notas com instrumental' },
];

/**
 * Invoices and their items.
 *
 * The category filter runs on the server and keeps only the invoices that carry that kind of item: looking
 * for instruments and getting invoices without a single one back is noise. It filters invoices, never lines
 * - an invoice that matches is shown whole, because what the supplier is charged for is the invoice.
 *
 * The search box also looks into the lots, which is how "in how many invoices did this lot come" is answered.
 */
export default function InvoicesTab({ status, reload }) {
  const navigate = useNavigate();
  const notify = useNotify();
  const [data, setData] = useState(null);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [expanded, setExpanded] = useState(null);
  const [acting, setActing] = useState(null);
  // A moldura da tabela é desenhada desde o começo e a lista continua na tela enquanto a próxima chega:
  // nada troca de lugar quando os dados chegam, nem na primeira carga. O que indica o carregamento é uma
  // barra fina no topo, que não ocupa espaço nenhum - o antigo esqueleto cinza entrava e saía, e era isso
  // que piscava.
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api('/invoices', {
        query: { status, search: search || undefined, category: category || undefined, page: page - 1, size: PAGE_SIZE },
      }));
    } catch (err) {
      notify.error(err);
    } finally {
      setLoading(false);
    }
  }, [status, search, category, page, notify]);

  useEffect(() => { load(); }, [load, reload]);
  useEffect(() => { setPage(1); }, [status, category]);

  const invoices = data?.content || [];
  const pages = data?.totalPages || 1;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
        <TextField size="small" label="Nota, lote, rastreio ou empresa" value={search} sx={{ minWidth: 280 }}
          onChange={(e) => { setPage(1); setSearch(e.target.value); }} />
        <TextField select size="small" label="Itens" value={category} sx={{ minWidth: 240 }}
          onChange={(e) => setCategory(e.target.value)}>
          {CATEGORIES.map((c) => <MenuItem key={c.value} value={c.value}>{c.label}</MenuItem>)}
        </TextField>
      </Box>

      <TableContainer component={Paper} variant="outlined" sx={{ position: 'relative', minHeight: 180 }}>
        <Box sx={{ position: 'absolute', top: 0, left: 0, right: 0, height: 2 }}>
          {loading && <LinearProgress sx={{ height: 2 }} />}
        </Box>
        <Box sx={{ transition: 'opacity .15s', opacity: loading && data ? 0.55 : 1 }}>
          {/* Larguras fixas: sem elas a tabela se mede pelo conteúdo, então as colunas mudavam de tamanho
              quando as linhas chegavam e o cabeçalho "pulava" para o lado */}
          <Table size="small" sx={{ tableLayout: 'fixed', minWidth: 880 }}>
            <TableHead>
              <TableRow>
                <TableCell width={48} />
                <TableCell width="12%">Nota</TableCell>
                <TableCell width="20%">Empresa</TableCell>
                <TableCell width="12%">Emissão</TableCell>
                <TableCell width="16%">Rastreio</TableCell>
                <TableCell width="9%" align="right">Itens</TableCell>
                <TableCell width="16%">Situação</TableCell>
                <TableCell width="13%" align="right">Ações</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {invoices.map((invoice) => {
                const [label, tone] = STATUS[invoice.status] || [invoice.status, 'neutral'];
                const open = expanded === invoice.id;
                const lines = invoice.items;
                return [
                  <TableRow key={invoice.id} hover sx={invoice.overdue ? { backgroundColor: tokens.warningBg } : undefined}>
                    <TableCell>
                      <IconButton size="small" onClick={() => setExpanded(open ? null : invoice.id)}
                        aria-label={open ? 'Ocultar itens' : 'Ver itens'}>
                        {open ? <ExpandLessOutlined fontSize="small" /> : <ExpandMoreOutlined fontSize="small" />}
                      </IconButton>
                    </TableCell>
                    <TableCell sx={mono}>
                      {invoice.number}{invoice.series ? `/${invoice.series}` : ''}
                    </TableCell>
                    <TableCell>{invoice.emitterName}</TableCell>
                    <TableCell>{formatDate(invoice.issuedAt)}</TableCell>
                    <TableCell sx={mono}>
                      {invoice.trackingCode ? (
                        <a href={`https://rastreamento.correios.com.br/app/index.php?objeto=${invoice.trackingCode}`}
                          target="_blank" rel="noreferrer">{invoice.trackingCode}</a>
                      ) : <Typography variant="caption" color="text.secondary">—</Typography>}
                    </TableCell>
                    <TableCell align="right" sx={mono}>
                      {invoice.receivedUnits}/{invoice.announcedUnits}
                    </TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={0.5} alignItems="center">
                        <StatusChip tone={tone}>{label}</StatusChip>
                        {invoice.overdue && (
                          <Tooltip title={`Aberta há ${invoice.daysOpen} dias`}>
                            <span><StatusChip tone="error">{invoice.daysOpen} dias</StatusChip></span>
                          </Tooltip>
                        )}
                      </Stack>
                    </TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={0.5} justifyContent="flex-end" alignItems="center">
                        {invoice.hasPdf ? (
                          <Tooltip title="Abrir a nota em PDF">
                            <IconButton size="small" aria-label="Abrir PDF"
                              onClick={() => window.open(`${API_BASE}/invoices/${invoice.id}/pdf`, '_blank', 'noopener')}>
                              <PictureAsPdfOutlined fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        ) : invoice.pdfExpired ? (
                          <Tooltip title="PDF apagado após o prazo de guarda; a nota continua aqui">
                            <span><IconButton size="small" disabled aria-label="PDF expirado">
                              <PictureAsPdfOutlined fontSize="small" />
                            </IconButton></span>
                          </Tooltip>
                        ) : null}
                        <Tooltip title="Ver as movimentações dos lotes desta nota">
                          <IconButton size="small" aria-label={`Ver movimentações da nota ${invoice.number}`}
                            onClick={() => navigate('/relatorios', {
                              state: { report: 'movements', filters: { invoice: invoice.number, invoiceId: invoice.id } },
                            })}>
                            <HistoryOutlined fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Button size="small" onClick={() => setActing(invoice)}>Tratar</Button>
                      </Stack>
                    </TableCell>
                  </TableRow>,
                  <TableRow key={`${invoice.id}-items`}>
                    <TableCell colSpan={8} sx={{ py: 0, border: 0 }}>
                      <Collapse in={open} unmountOnExit>
                        <Box sx={{ py: 2 }}>
                          {invoice.surgeryDate && (
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                              Cirurgia em {formatDate(invoice.surgeryDate)}
                              {invoice.recipientName ? ` · nota emitida para ${invoice.recipientName}` : ''}
                            </Typography>
                          )}
                          <Table size="small">
                            <TableHead>
                              <TableRow>
                                <TableCell>REF</TableCell>
                                <TableCell>Descrição</TableCell>
                                <TableCell>Lotes na nota</TableCell>
                                <TableCell>Tipo</TableCell>
                                <TableCell align="right">Recebido</TableCell>
                              </TableRow>
                            </TableHead>
                            <TableBody>
                              {lines.map((line) => (
                                <TableRow key={line.id}>
                                  <TableCell sx={mono}>
                                    <Stack direction="row" spacing={0.75} alignItems="center">
                                      {line.color && <ColorSwatch color={line.color} />}
                                      <span>{line.ref || line.productCode}</span>
                                    </Stack>
                                  </TableCell>
                                  <TableCell>{line.materialDescription || line.description}</TableCell>
                                  <TableCell sx={mono}>
                                    {line.lots.length === 0 ? '—' : line.lots.map((l) => l.number).join(', ')}
                                    {line.lots.length > 1 && (
                                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                                        a confirmar na entrada
                                      </Typography>
                                    )}
                                  </TableCell>
                                  <TableCell>
                                    <StatusChip tone={line.category === 'IMPLANT' ? 'info' : 'neutral'}>
                                      {line.category === 'IMPLANT' ? 'Implante' : 'Instrumental'}
                                    </StatusChip>
                                  </TableCell>
                                  <TableCell align="right" sx={mono}>
                                    {line.receivedQuantity}/{line.quantity}
                                  </TableCell>
                                </TableRow>
                              ))}
                              {lines.length === 0 && (
                                <TableRow>
                                  <TableCell colSpan={5}>
                                    <Typography variant="body2" color="text.secondary">
                                      Nota ainda sem itens: o XML não foi lido.
                                    </Typography>
                                  </TableCell>
                                </TableRow>
                              )}
                            </TableBody>
                          </Table>
                          {invoice.closeReason && (
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                              Encerrada: {invoice.closeReason}
                            </Typography>
                          )}
                          {invoice.totalValue != null && (
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                              Valor da nota: {formatMoney(invoice.totalValue)}
                            </Typography>
                          )}
                        </Box>
                      </Collapse>
                    </TableCell>
                  </TableRow>,
                ];
              })}
              {data && invoices.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} sx={{ py: 5, textAlign: 'center', border: 0 }}>
                    <Typography variant="body2" color="text.secondary">
                      {search ? 'Nenhuma nota encontrada para esta busca.'
                        : category === 'INSTRUMENT' ? 'Nenhuma nota com instrumental neste filtro.'
                          : category === 'IMPLANT' ? 'Nenhuma nota com implantes neste filtro.'
                            : status === 'OPEN' ? 'Nenhuma nota aguardando material.' : 'Nenhuma nota neste filtro.'}
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Box>
      </TableContainer>

      {pages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center' }}>
          <Pagination count={pages} page={page} onChange={(_, p) => setPage(p)} size="small" />
        </Box>
      )}

      <InvoiceActionsDialog invoice={acting} onClose={() => setActing(null)}
        onChanged={() => { setActing(null); load(); }} />
    </Stack>
  );
}
