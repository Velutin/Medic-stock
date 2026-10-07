import {
  Alert, Box, Button, IconButton, LinearProgress, Link, Paper, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Typography,
} from '@mui/material';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import { API_BASE } from '../../config';
import { formatDate, formatMoney } from '../../utils/format';
import { tokens } from '../../theme';

export const materialName = (i) => (i.component ? `${i.component}${i.size ? ` · ${i.size}` : ''}` : i.description);
const mono = { fontFamily: tokens.mono, fontSize: 13 };
const REASONS = {
  LOT_NOT_FOUND: 'lote não encontrado',
  AMBIGUOUS_LOT: 'lote com mais de uma validade',
  EXPIRED_LOT: 'lote vencido',
  NO_HOSPITAL_BALANCE: 'sem saldo no hospital',
};

/** Surgeries left open, to continue. */
export function OpenSurgeries({ surgeries, onResume }) {
  if (!surgeries.length) return null;
  return (
    <Paper variant="outlined" sx={{ p: 2, mb: 2.5 }}>
      <Typography variant="h3" component="h2" sx={{ mb: 1 }}>Cirurgias em aberto</Typography>
      <Stack spacing={1}>
        {surgeries.map((s) => (
          <Box key={s.id} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1.5, flexWrap: 'wrap' }}>
            <Typography variant="body2">
              <strong>{s.patientName}</strong> · {s.hospital} · {formatDate(s.surgeryDate)}
              {!s.hasSheet && <Typography component="span" variant="caption" color="warning.main"> · sem ficha</Typography>}
            </Typography>
            <Button size="small" variant="outlined" onClick={() => onResume(s.id)}>Continuar</Button>
          </Box>
        ))}
      </Stack>
    </Paper>
  );
}

/** Reading progress of the sheet. */
export function ReadingProgress({ reading }) {
  if (!reading) return null;
  const text = reading.step === 'upload' ? 'Anexando a ficha…'
    : reading.step === 'check' ? 'Conferindo os lotes no estoque do hospital…'
      : `Lendo as etiquetas${reading.page ? ` · ${reading.page} ${reading.page === 1 ? 'página lida' : 'páginas lidas'}` : '…'}`;
  return (
    <Box role="status" aria-live="polite">
      <Typography variant="body2" color="text.secondary" sx={{ mb: 0.75 }}>{text}</Typography>
      <LinearProgress />
    </Box>
  );
}

/** Attached sheet: link to open it. */
export function SheetStatus({ surgery }) {
  if (!surgery?.hasSheet) return null;
  return (
    <Alert severity="success" sx={{ py: 0.25 }}
      action={<Link href={`${API_BASE}/surgeries/${surgery.id}/sheet`} target="_blank" rel="noopener" sx={{ mr: 1 }}>Ver</Link>}>
      Ficha anexada. Enviar outra substitui a atual.
    </Alert>
  );
}

/** Pending issues of the surgery: reviewed by an administrator before the surgery can be completed. */
export function PendingIssues({ surgery }) {
  const open = (surgery?.pendingIssues || []).filter((p) => p.status === 'OPEN');
  if (!open.length) return null;
  return (
    <Alert severity="warning">
      <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>
        {open.length} {open.length === 1 ? 'pendência' : 'pendências'} para o administrador revisar
      </Typography>
      {open.map((p) => (
        <Typography key={p.id} variant="body2">
          <span style={mono}>{p.enteredCode}</span>{p.enteredRef ? ` (REF ${p.enteredRef})` : ''}: {REASONS[p.reason] || p.reason}
        </Typography>
      ))}
      <Typography variant="caption" sx={{ display: 'block', mt: 0.5 }}>
        A cirurgia só pode ser concluída depois que elas forem resolvidas ou descartadas.
      </Typography>
    </Alert>
  );
}

/** Items of the surgery as a table (computer). Values only for administrators. */
export function ItemsTable({ surgery, showValues, onRemove }) {
  const items = surgery?.items || [];
  if (!items.length) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
        Nenhum item lançado. Leia o código, digite o lote ou envie a ficha.
      </Typography>
    );
  }
  return (
    <TableContainer>
      <Table size="small" sx={{ minWidth: 640 }}>
        <TableHead>
          <TableRow>
            <TableCell>Material</TableCell><TableCell>REF</TableCell><TableCell>Lote</TableCell><TableCell>Validade</TableCell>
            <TableCell align="right">Qtd.</TableCell>{showValues && <TableCell align="right">Valor</TableCell>}<TableCell />
          </TableRow>
        </TableHead>
        <TableBody>
          {items.map((i) => (
            <TableRow key={i.id} hover>
              <TableCell>{materialName(i)}</TableCell>
              <TableCell sx={mono}>{i.ref}</TableCell>
              <TableCell sx={mono}>{i.lot}</TableCell>
              <TableCell sx={mono}>{formatDate(i.expiryDate)}</TableCell>
              <TableCell align="right">{i.quantity}</TableCell>
              {showValues && (
                <TableCell align="right">
                  {i.unitValue == null ? <Typography variant="caption" color="warning.main">Sem valor</Typography>
                    : formatMoney(i.unitValue * i.quantity)}
                </TableCell>
              )}
              <TableCell align="right" sx={{ py: 0.5 }}>
                <IconButton aria-label={`Remover ${i.ref} lote ${i.lot}`} onClick={() => onRemove(i.id)} sx={{ color: tokens.error }}>
                  <DeleteOutline fontSize="small" />
                </IconButton>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

/** Items of the surgery as cards (phone). */
export function ItemsCards({ surgery, showValues, onRemove }) {
  const items = surgery?.items || [];
  if (!items.length) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
        Nenhum item lido ainda.
      </Typography>
    );
  }
  return (
    <Stack spacing={1}>
      {items.map((i) => (
        <Paper key={i.id} variant="outlined" sx={{ p: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography sx={{ fontWeight: 600, fontSize: 14 }} noWrap>{materialName(i)}</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ fontFamily: tokens.mono, fontSize: 12 }}>
              {i.ref} · {i.lot}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Val. {formatDate(i.expiryDate)}{i.quantity > 1 ? ` · ${i.quantity} un.` : ''}
            </Typography>
          </Box>
          {showValues && (
            <Typography sx={{ fontWeight: 600, whiteSpace: 'nowrap', fontSize: 14 }}>
              {i.unitValue == null ? '—' : formatMoney(i.unitValue * i.quantity)}
            </Typography>
          )}
          <IconButton aria-label={`Remover ${i.ref} lote ${i.lot}`} onClick={() => onRemove(i.id)} sx={{ color: tokens.error }}>
            <DeleteOutline />
          </IconButton>
        </Paper>
      ))}
    </Stack>
  );
}

/** Total of the surgery in the hospital price table (administrators only). */
export function SurgeryTotal({ surgery, showValues }) {
  if (!showValues || !surgery?.items?.length) return null;
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
      <Typography variant="body2" color="text.secondary">Total (tabela do hospital)</Typography>
      <Typography sx={{ fontWeight: 700, fontSize: 18 }}>{formatMoney(surgery.totalValue || 0)}</Typography>
    </Box>
  );
}

/** Why the surgery cannot be completed yet, or null. */
export function completeBlocker(surgery) {
  if (!surgery) return 'Preencha a cirurgia e lance os itens.';
  if (surgery.status === 'COMPLETED') return 'Cirurgia já concluída: as alterações de itens são gravadas na hora.';
  if (!surgery.items?.length) return 'Lance ao menos um item.';
  if (!surgery.hasSheet) return 'Anexe a ficha da cirurgia (obrigatória).';
  if ((surgery.pendingIssues || []).some((p) => p.status === 'OPEN')) return 'Há pendências para o administrador resolver.';
  return null;
}
