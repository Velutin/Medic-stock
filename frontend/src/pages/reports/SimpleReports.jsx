import { Box, IconButton, Stack, TableCell, TableRow, Typography } from '@mui/material';
import PictureAsPdfOutlined from '@mui/icons-material/PictureAsPdfOutlined';
import { API_BASE } from '../../config';
import { downloadFile } from '../../api/download';
import { useNotify } from '../../notifications/NotificationProvider';
import StatusChip from '../../components/StatusChip';
import { formatDate, formatDateTime } from '../../utils/format';
import { tokens } from '../../theme';
import { Empty, Loading, PagedTable, pct, useReport } from './reportUtils';

const mono = { fontFamily: tokens.mono, fontSize: 13 };
const where = (hospital, location) => (hospital ? `${hospital}${location === 'STOREROOM' ? ' (sala)' : location === 'HOSPITAL' ? ' (hospital)' : ''}` : '—');

/** Weekly closing (Saturday to Friday): open and completed surgeries per hospital. */
export function WeeklyReport({ query }) {
  const { data } = useReport('/reports/weekly-closing', query);
  if (!data) return <Loading />;
  return (
    <Stack spacing={1.5}>
      <Typography variant="body2" color="text.secondary">
        Semana de {formatDate(data.start)} (sábado) a {formatDate(data.end)} (sexta) · pagamento em {formatDate(data.paymentDate)}.
        Contam as cirurgias em aberto e concluídas, pela data da cirurgia.
      </Typography>
      {data.byHospital.length === 0 ? <Empty>Nenhuma cirurgia nesta semana.</Empty> : (
        <PagedTable items={data.byHospital} rowKey={(r) => r.hospitalId}
          columns={[{ label: 'Hospital' }, { label: 'Cirurgias', align: 'right' }, { label: 'Itens consumidos', align: 'right' }, { label: 'Instrumentadores' }]}
          row={(r) => (<>
            <TableCell>{r.hospital}</TableCell><TableCell align="right">{r.surgeries}</TableCell>
            <TableCell align="right">{r.items}</TableCell>
            <TableCell>{r.surgicalTechs.length ? r.surgicalTechs.join(', ') : <Typography variant="caption" color="warning.main">Nenhum instrumentador vinculado</Typography>}</TableCell>
          </>)}
          footer={(
            <TableRow sx={{ '& td': { fontWeight: 700 } }}>
              <TableCell>Total</TableCell><TableCell align="right">{data.surgeries}</TableCell><TableCell align="right">{data.items}</TableCell><TableCell />
            </TableRow>
          )} />
      )}
    </Stack>
  );
}

/** Cancelled surgeries by who recorded them, with the share over everything they recorded. */
export function CancellationsReport({ query }) {
  const { data } = useReport('/reports/cancellations', query);
  if (!data) return <Loading />;
  return (
    <Stack spacing={2.5}>
      <Typography variant="body2" color="text.secondary">O período considera a data da cirurgia. Agrupado por quem lançou.</Typography>
      {data.byUser.length === 0 ? <Empty>Nenhuma cirurgia lançada no período.</Empty> : (
        <PagedTable items={data.byUser} rowKey={(r) => r.user} minWidth={560}
          columns={[{ label: 'Lançado por' }, { label: 'Lançadas', align: 'right' }, { label: 'Canceladas', align: 'right' }, { label: 'Proporção' }]}
          row={(r) => {
            const share = r.launched ? (r.cancelled / r.launched) * 100 : 0;
            return (<>
              <TableCell>{r.user}</TableCell><TableCell align="right">{r.launched}</TableCell><TableCell align="right">{r.cancelled}</TableCell>
              <TableCell sx={{ minWidth: 180 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Box sx={{ flex: 1, height: 8, borderRadius: 4, backgroundColor: tokens.divider }}>
                    <Box sx={{ height: 8, borderRadius: 4, width: `${Math.min(100, share * 5)}%`, backgroundColor: share >= 5 ? tokens.warning : tokens.primary }} />
                  </Box>
                  <Typography variant="body2" sx={{ width: 48, textAlign: 'right' }}>{pct(r.cancelled, r.launched)}</Typography>
                </Box>
              </TableCell>
            </>);
          }}
          footer={(
            <TableRow sx={{ '& td': { fontWeight: 700 } }}>
              <TableCell colSpan={4}>{data.cancelled} de {data.launched} cirurgias canceladas ({pct(data.cancelled, data.launched)})</TableCell>
            </TableRow>
          )} />
      )}
      <Typography variant="h3" component="h3">Cirurgias canceladas no período</Typography>
      {data.surgeries.length === 0 ? <Empty>Nenhuma cirurgia cancelada.</Empty> : (
        <PagedTable items={data.surgeries} rowKey={(r) => r.surgeryId} minWidth={820}
          columns={[{ label: 'Data da cirurgia' }, { label: 'Hospital' }, { label: 'Paciente' }, { label: 'Lançado por' }, { label: 'Motivo do cancelamento' }, { label: 'PDF enviado' }]}
          row={(r) => (<>
            <TableCell>{formatDate(r.surgeryDate)}</TableCell><TableCell>{r.hospital}</TableCell><TableCell>{r.patientName}</TableCell>
            <TableCell>{r.createdBy}</TableCell><TableCell sx={{ maxWidth: 280 }}>{r.reason}</TableCell>
            <TableCell>{r.hasSheet
              ? <a href={`${API_BASE}/surgeries/${r.surgeryId}/sheet`} target="_blank" rel="noopener noreferrer">Ver PDF</a>
              : <Typography variant="caption" color="text.secondary">Sem PDF</Typography>}</TableCell>
          </>)} />
      )}
    </Stack>
  );
}

/**
 * Delivery documents already generated, to download again: transfers from the storeroom and loans, which are
 * delivered to the hospital as regular deliveries (numbered E-…).
 */
export function DeliveriesReport({ query }) {
  const notify = useNotify();
  const { data } = useReport('/reports/deliveries', query);
  if (!data) return <Loading />;
  const download = (d) => downloadFile(d.pdfPath, `entrega-${d.number}.pdf`).catch((err) => notify.error(err));
  if (data.length === 0) {
    return <Empty>{query.lot ? `Nenhuma entrega com o lote ${query.lot} no período. Se necessário, amplie o período.` : 'Nenhum relatório de entrega no período.'}</Empty>;
  }
  return (
    <PagedTable items={data} rowKey={(r) => `${r.loan ? 'L' : 'D'}${r.id}`} minWidth={860}
      columns={[{ label: 'Nº' }, { label: 'Data' }, { label: 'Hospital' }, { label: 'Tipo' }, { label: 'Lotes', align: 'right' }, { label: 'Unidades', align: 'right' }, { label: 'Gerado por' }, { label: '' }]}
      row={(r) => (<>
        <TableCell sx={mono}>
          {r.number}
          {r.matchedLots?.map((m) => (
            <Typography key={m} variant="caption" sx={{ display: 'block', color: tokens.primary, whiteSpace: 'nowrap' }}>{m}</Typography>
          ))}
        </TableCell>
        <TableCell>{formatDateTime(r.createdAt)}</TableCell>
        <TableCell>{r.hospital}</TableCell>
        <TableCell>
          {r.loan ? <StatusChip tone="info">Empréstimo</StatusChip> : <StatusChip tone="neutral">Transferência</StatusChip>}
          {r.sourceHospital && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            {r.loan ? `de ${r.sourceHospital}` : `da sala de ${r.sourceHospital}`}</Typography>}
        </TableCell>
        <TableCell align="right">{r.lots}</TableCell><TableCell align="right">{r.units}</TableCell><TableCell>{r.createdBy}</TableCell>
        <TableCell align="right"><IconButton aria-label={`Baixar PDF da entrega ${r.number}`} onClick={() => download(r)}><PictureAsPdfOutlined fontSize="small" /></IconButton></TableCell>
      </>)} />
  );
}

/** Lots expired or expiring within the window. */
export function ValidityReport({ query }) {
  const { data } = useReport('/reports/validity', query);
  if (!data) return <Loading />;
  return data.length === 0 ? <Empty>Nenhum lote vencido ou vencendo na janela escolhida.</Empty> : (
    <PagedTable items={data} rowKey={(r) => `${r.hospital}-${r.location}-${r.lot}-${r.expiryDate}-${r.ref}`} minWidth={820}
      columns={[{ label: 'Hospital' }, { label: 'Local' }, { label: 'REF' }, { label: 'Material' }, { label: 'Lote' }, { label: 'Validade' }, { label: 'Situação' }, { label: 'Qtd.', align: 'right' }]}
      row={(r) => (<>
        <TableCell>{r.hospital}</TableCell><TableCell>{r.location === 'STOREROOM' ? 'Sala' : 'Hospital'}</TableCell>
        <TableCell sx={mono}>{r.ref}</TableCell><TableCell>{r.description}</TableCell><TableCell sx={mono}>{r.lot}</TableCell>
        <TableCell sx={mono}>{formatDate(r.expiryDate)}</TableCell>
        <TableCell>{r.daysLeft < 0 ? <StatusChip tone="error">Vencido</StatusChip>
          : <StatusChip tone={r.daysLeft <= 30 ? 'warning' : 'neutral'}>{r.daysLeft} {r.daysLeft === 1 ? 'dia' : 'dias'}</StatusChip>}</TableCell>
        <TableCell align="right">{r.quantity}</TableCell>
      </>)} />
  );
}

/** Loans between hospitals, one line per item. */
export function LoansReport({ query, returns }) {
  if (returns) return <ReturnsReport query={query} />;
  return <LoanLines query={query} />;
}

function LoanLines({ query }) {
  const { data } = useReport('/reports/loans', { ...query, type: 'LOAN' });
  if (!data) return <Loading />;
  const lines = data.flatMap((l) => l.items.map((i, k) => ({ ...i, loan: l, key: `${l.id}-${k}` })));
  return lines.length === 0 ? <Empty>Nenhum empréstimo no período.</Empty> : (
    <PagedTable items={lines} rowKey={(r) => r.key} minWidth={860}
      columns={[{ label: 'Data' }, { label: 'Origem' }, { label: 'Destino' }, { label: 'REF' }, { label: 'Lote' }, { label: 'Validade' }, { label: 'Qtd.', align: 'right' }, { label: 'Registrado por' }]}
      row={(r) => (<>
        <TableCell>{formatDate(r.loan.createdAt)}</TableCell><TableCell>{where(r.loan.sourceHospital, r.loan.sourceLocation)}</TableCell>
        <TableCell>{r.loan.destinationHospital}</TableCell>
        <TableCell sx={mono}>{r.ref}</TableCell><TableCell sx={mono}>{r.lot}</TableCell><TableCell sx={mono}>{formatDate(r.expiryDate)}</TableCell>
        <TableCell align="right">{r.quantity}</TableCell><TableCell>{r.loan.createdBy || '—'}</TableCell>
      </>)} />
  );
}

/** Returns to Baumer, one line per return with its own PDF (items sent to the company). */
function ReturnsReport({ query }) {
  const notify = useNotify();
  const { data } = useReport('/reports/loans', { ...query, type: 'RETURN' });
  if (!data) return <Loading />;
  const download = (l) => downloadFile(`/loans/${l.id}/pdf`, `devolucao-${l.id}.pdf`).catch((err) => notify.error(err));
  return data.length === 0 ? <Empty>Nenhuma devolução no período.</Empty> : (
    <PagedTable items={data} rowKey={(l) => l.id} minWidth={900}
      columns={[{ label: 'Nº' }, { label: 'Data' }, { label: 'Origem' }, { label: 'Motivo' }, { label: 'Itens' }, { label: 'Unidades', align: 'right' }, { label: 'Registrado por' }, { label: 'PDF' }]}
      row={(l) => (<>
        <TableCell sx={mono}>#{l.id}</TableCell><TableCell>{formatDate(l.createdAt)}</TableCell>
        <TableCell>{where(l.sourceHospital, l.sourceLocation)}</TableCell><TableCell sx={{ maxWidth: 220 }}>{l.returnReason}</TableCell>
        <TableCell sx={{ maxWidth: 260 }}>
          {l.items.map((i, k) => <Typography key={k} variant="body2" sx={mono}>{i.ref} · {i.lot} × {i.quantity}</Typography>)}
        </TableCell>
        <TableCell align="right">{l.items.reduce((s, i) => s + i.quantity, 0)}</TableCell>
        <TableCell>{l.createdBy || '—'}</TableCell>
        <TableCell><IconButton aria-label={`Baixar PDF da devolução ${l.id}`} onClick={() => download(l)}><PictureAsPdfOutlined fontSize="small" /></IconButton></TableCell>
      </>)} />
  );
}

export const MOVEMENT_TYPES = {
  ENTRY: 'Entrada', ENTRY_CORRECTION: 'Correção de entrada', INVENTORY_ADJUSTMENT: 'Ajuste de inventário',
  REPLENISHMENT: 'Transferência', SURGERY_WITHDRAWAL: 'Saída em cirurgia', SURGERY_REVERSAL: 'Estorno de cirurgia',
  LOAN: 'Empréstimo', SUPPLIER_RETURN: 'Devolução à Baumer', LOT_CORRECTION: 'Correção de lote',
};

/** Every stock movement in the period. */
export function MovementsReport({ query }) {
  const { data } = useReport('/reports/movements', query);
  if (!data) return <Loading />;
  return data.length === 0 ? <Empty>Nenhuma movimentação no período.</Empty> : (
    <Stack spacing={1}>
      {data.length >= 3000 && <Typography variant="caption" color="warning.main">Mostrando as 3000 mais recentes: reduza o período para ver todas.</Typography>}
      <PagedTable items={data} rowKey={(r) => r.id} minWidth={960}
        columns={[{ label: 'Data' }, { label: 'Tipo' }, { label: 'REF' }, { label: 'Lote' }, { label: 'Qtd.', align: 'right' }, { label: 'Origem' }, { label: 'Destino' }, { label: 'Usuário' }]}
        row={(r) => (<>
          <TableCell>{formatDateTime(r.date)}</TableCell><TableCell>{MOVEMENT_TYPES[r.type] || r.type}</TableCell>
          <TableCell sx={mono}>{r.ref}</TableCell><TableCell sx={mono}>{r.lot}</TableCell><TableCell align="right">{r.quantity}</TableCell>
          <TableCell>{where(r.sourceHospital, r.sourceLocation)}</TableCell><TableCell>{where(r.destinationHospital, r.destinationLocation)}</TableCell>
          <TableCell>{r.user || '—'}</TableCell>
        </>)} />
    </Stack>
  );
}
