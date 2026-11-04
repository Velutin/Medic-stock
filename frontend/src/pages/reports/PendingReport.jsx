import { useEffect, useState } from 'react';
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, InputAdornment, List, ListItemButton,
  ListItemText, Radio, Stack, TableCell, TextField, ToggleButton, ToggleButtonGroup, Typography, useMediaQuery,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import StatusChip from '../../components/StatusChip';
import { formatDate } from '../../utils/format';
import { tokens } from '../../theme';
import { Empty, Loading, PagedTable, useReport } from './reportUtils';
import { lotContains } from '../../utils/lot';

const mono = { fontFamily: tokens.mono, fontSize: 13 };
export const REASONS = {
  LOT_NOT_FOUND: 'Lote não encontrado', AMBIGUOUS_LOT: 'Lote com mais de uma validade',
  EXPIRED_LOT: 'Lote vencido', NO_HOSPITAL_BALANCE: 'Sem saldo no hospital',
  WITHOUT_INVOICE: 'Entrou sem nota', NOT_DELIVERED: 'Não chegou na remessa',
};

/** Pendência de entrada: o material já está no estoque, o que falta é a nota. */
const isWithoutInvoice = (issue) => issue?.reason === 'WITHOUT_INVOICE';

/** Pendência de remessa: as peças saíram, não estavam na caixa e o saldo continua a caminho. */
const isNotDelivered = (issue) => issue?.reason === 'NOT_DELIVERED';

/**
 * Os três destinos possíveis da peça que não chegou, cada um com a sua movimentação no backend.
 *
 * Não existe "descartar" aqui: fechar a pendência sem dizer o que aconteceu deixaria o saldo na estrada
 * para sempre, contando como estoque que ninguém tem.
 */
const OUTCOMES = [
  { value: 'ARRIVED', label: 'Apareceu depois',
    help: 'A peça chegou depois da conferência. Entra no estoque do hospital agora.',
    note: 'Apareceu depois da conferência.' },
  { value: 'RETURNED', label: 'Nunca saiu da origem',
    help: 'Ficou para trás na hora de montar a caixa. O saldo volta para onde estava.',
    note: 'Não foi enviada; o saldo voltou para a origem.' },
  { value: 'LOST', label: 'Extraviada',
    help: 'Perdida no caminho. Sai do estoque e fica registrada como extravio.', note: null },
];
const STATUS = { OPEN: ['warning', 'Em aberto'], RESOLVED: ['success', 'Resolvida'], DISCARDED: ['neutral', 'Descartada'] };

const MAX_RESULTS = 15;
const materialName = (r) => (r.component ? `${r.component}${r.size ? ` · ${r.size}` : ''}` : r.description);

/** Selectable lots: REF, material, lot, expiry date and units inside the hospital. */
function LotChoices({ options, value, onChange }) {
  return (
    <List dense disablePadding sx={{ border: `1px solid ${tokens.border}`, borderRadius: '6px', overflow: 'hidden' }}>
      {options.map((o, i) => (
        <ListItemButton key={o.lotId} selected={value === o.lotId} onClick={() => onChange(o.lotId)}
          sx={{ gap: 1.5, alignItems: 'flex-start', ...(i ? { borderTop: `1px solid ${tokens.divider}` } : {}) }}>
          <Radio size="small" checked={value === o.lotId} tabIndex={-1} sx={{ p: 0, mt: 0.25 }}
            inputProps={{ 'aria-label': `${o.ref} lote ${o.lot}` }} />
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography sx={{ fontSize: 14, fontWeight: 600 }} noWrap>{o.material}</Typography>
            <Typography variant="body2" color="text.secondary" sx={mono}>
              {o.ref} · {o.lot} · Val. {formatDate(o.expiryDate)}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 0.5 }}>
            <Typography variant="body2" sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>{o.available} no hospital</Typography>
            {o.sameLot && <StatusChip tone="success">Mesmo lote lido</StatusChip>}
            {o.expired && <StatusChip tone="error">Vencido</StatusChip>}
          </Box>
        </ListItemButton>
      ))}
    </List>
  );
}

/**
 * Resolves a pending issue: records a lot inside the hospital in the surgery (RESOLVED) or closes it with a
 * justification (DISCARDED), e.g. a patient label read by mistake. First come the lots of the item read, as on the
 * surgery screen; when the right one is not there, a search by lot or REF looks at every lot inside the hospital.
 */
function ResolveDialog({ issue, onClose, onDone }) {
  const notify = useNotify();
  const [mode, setMode] = useState('RESOLVED');
  const [suggested, setSuggested] = useState(null);
  const [inside, setInside] = useState(null);
  const [search, setSearch] = useState('');
  const [lotId, setLotId] = useState('');
  const [invoiceNumber, setInvoiceNumber] = useState('');
  const [invoiceOptions, setInvoiceOptions] = useState(null);
  const [outcome, setOutcome] = useState('');
  const [text, setText] = useState('');
  const [saving, setSaving] = useState(false);
  const fullScreen = useMediaQuery('(max-width:599.95px)');

  useEffect(() => {
    if (!issue) return;
    setMode('RESOLVED');
    setLotId('');
    setInvoiceNumber('');
    setOutcome('');
    setText('');
    setSearch('');
    setSuggested(null);
    setInside(null);
    // A pendência de entrada não se resolve escolhendo lote: o lote já entrou, falta dizer de que nota ele veio
    // A pendência de remessa também não se resolve escolhendo lote: o lote dela já é conhecido
    if (isNotDelivered(issue)) {
      setSuggested([]);
      setInvoiceOptions(null);
      return;
    }
    if (isWithoutInvoice(issue)) {
      setSuggested([]);
      setInvoiceOptions(null);
      api(`/pending-issues/${issue.id}/invoice-options`).then(setInvoiceOptions).catch(() => setInvoiceOptions([]));
      return;
    }
    api(`/pending-issues/${issue.id}/suggestions`).then(setSuggested).catch(() => setSuggested([]));
  }, [issue]);

  // Every lot inside the hospital, loaded only when a search is typed
  const term = search.trim().toUpperCase();
  useEffect(() => {
    if (!issue || isWithoutInvoice(issue) || isNotDelivered(issue) || term.length < 2 || inside) return;
    api(`/stock/hospital/${issue.hospitalId}`)
      .then((rows) => setInside(rows.filter((r) => r.hospitalQuantity > 0).map((r) => ({
        lotId: r.lotId, ref: r.ref, material: materialName(r), lot: r.lot, expiryDate: r.expiryDate,
        available: r.hospitalQuantity, expired: r.expired,
      }))))
      .catch((err) => { notify.error(err); setInside([]); });
  }, [issue, term, inside, notify]);

  const found = term.length < 2 || !inside ? null
    : inside.filter((o) => lotContains(o.lot, term)
        || [o.ref, o.material].some((v) => (v || '').toUpperCase().includes(term)));

  const withoutInvoice = isWithoutInvoice(issue);
  const notDelivered = isNotDelivered(issue);
  const chosen = OUTCOMES.find((o) => o.value === outcome);

  const body = () => {
    if (notDelivered) {
      return { status: 'RESOLVED', lotId: null, outcome, resolution: text.trim() || chosen?.note || '' };
    }
    if (withoutInvoice) {
      return mode === 'RESOLVED'
        ? { status: 'RESOLVED', lotId: null, invoiceNumber: invoiceNumber.trim(),
            resolution: text.trim() || `Vinculado à nota ${invoiceNumber.trim()}` }
        : { status: 'DISCARDED', lotId: null, resolution: text.trim() };
    }
    return mode === 'RESOLVED'
      ? { status: 'RESOLVED', lotId, resolution: text.trim() || 'Lote identificado na conferência' }
      : { status: 'DISCARDED', lotId: null, resolution: text.trim() };
  };

  const done = () => {
    if (notDelivered) {
      return {
        ARRIVED: 'Registrado: a peça chegou e entrou no estoque do hospital.',
        RETURNED: 'Registrado: a peça não saiu e o saldo voltou para a origem.',
        LOST: 'Registrado como extravio. A peça saiu do estoque.',
      }[outcome];
    }
    if (withoutInvoice) {
      return mode === 'RESOLVED'
        ? `Lote vinculado à nota ${invoiceNumber.trim()}.`
        : 'Registrado: o lote entrou sem nota.';
    }
    return mode === 'RESOLVED' ? 'Pendência resolvida: o lote foi lançado na cirurgia.' : 'Pendência descartada.';
  };

  const save = async () => {
    setSaving(true);
    try {
      await api(`/pending-issues/${issue.id}`, { method: 'PATCH', body: body() });
      notify.success(done());
      onDone();
    } catch (err) {
      notify.error(err);
    } finally {
      setSaving(false);
    }
  };

  // O extravio tira peça do estoque sem ninguém ter usado: esse exige justificativa, como o descarte
  const valid = notDelivered ? Boolean(outcome) && (outcome !== 'LOST' || text.trim().length > 0)
    : mode === 'DISCARDED' ? text.trim().length > 0
    : withoutInvoice ? invoiceNumber.trim().length > 0 : Boolean(lotId);

  return (
    <Dialog open={Boolean(issue)} onClose={onClose} fullWidth maxWidth="sm" fullScreen={fullScreen}>
      <DialogTitle sx={{ typography: 'h2' }}>Resolver pendência</DialogTitle>
      <DialogContent dividers>
        {issue && (
          <Stack spacing={2}>
            <Typography variant="body2">
              {notDelivered ? (
                <>
                  <strong>Remessa</strong> · {issue.hospital} · {formatDate(issue.createdAt)}<br />
                  Lote: <span style={mono}>{issue.enteredCode}</span>
                  {issue.enteredRef ? ` (REF ${issue.enteredRef})` : ''} · {issue.quantity}
                  {issue.quantity === 1 ? ' peça' : ' peças'} · {REASONS[issue.reason]}
                </>
              ) : withoutInvoice ? (
                <>
                  <strong>Entrada{issue.entryId ? ` #${issue.entryId}` : ''}</strong> · {issue.hospital}
                  {' · '}{formatDate(issue.createdAt)}<br />
                  Lote: <span style={mono}>{issue.enteredCode}</span>
                  {issue.enteredRef ? ` (REF ${issue.enteredRef})` : ''} · {issue.quantity}
                  {issue.quantity === 1 ? ' unidade' : ' unidades'} · {REASONS[issue.reason]}
                </>
              ) : (
                <>
                  <strong>{issue.patientName}</strong> · {issue.hospital} · {formatDate(issue.surgeryDate)}<br />
                  Item lido: <span style={mono}>{issue.enteredCode}</span>
                  {issue.enteredRef ? ` (REF ${issue.enteredRef})` : ''} · {REASONS[issue.reason]}
                </>
              )}
            </Typography>
            {withoutInvoice && (
              <Typography variant="caption" color="text.secondary">
                O material já está no estoque: o que falta é a nota. Informe o número dela, ou registre que
                este lote veio sem nota mesmo.
              </Typography>
            )}
            {notDelivered && (
              <>
                <Typography variant="caption" color="text.secondary">
                  Estas peças saíram e não estavam na caixa. O saldo delas está a caminho desde a conferência
                  da chegada, e sai de lá quando você disser o que aconteceu.
                </Typography>
                <List dense disablePadding>
                  {OUTCOMES.map((o) => (
                    <ListItemButton key={o.value} selected={outcome === o.value}
                      onClick={() => setOutcome(o.value)}
                      sx={{ border: `1px solid ${tokens.border}`, borderRadius: '8px', mb: 0.75, alignItems: 'flex-start' }}>
                      <Radio size="small" checked={outcome === o.value} tabIndex={-1} sx={{ p: 0, mt: 0.25, mr: 1 }} />
                      <ListItemText
                        primary={<Typography variant="body2" sx={{ fontWeight: 500 }}>{o.label}</Typography>}
                        secondary={<Typography variant="caption" color="text.secondary">{o.help}</Typography>} />
                    </ListItemButton>
                  ))}
                </List>
              </>
            )}
            {!notDelivered && (
              <ToggleButtonGroup exclusive fullWidth color="primary" value={mode} onChange={(_, v) => v && setMode(v)}>
                <ToggleButton value="RESOLVED">{withoutInvoice ? 'Informar a nota' : 'Lançar um lote do hospital'}</ToggleButton>
                <ToggleButton value="DISCARDED">{withoutInvoice ? 'Veio sem nota' : 'Descartar'}</ToggleButton>
              </ToggleButtonGroup>
            )}
            {withoutInvoice && mode === 'RESOLVED' && (
              <Stack spacing={1.25}>
                {issue.declaredInvoice && (
                  <Typography variant="caption" color="text.secondary">
                    Declarada na entrada: <span style={mono}>{issue.declaredInvoice}</span>
                  </Typography>
                )}

                {invoiceOptions === null ? (
                  <Typography variant="body2" color="text.secondary">Procurando notas com este lote…</Typography>
                ) : invoiceOptions.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    Nenhuma nota aberta está devendo este lote. Normalmente é o XML que ainda não chegou:
                    peça à empresa para reenviar por e-mail e volte aqui depois.
                  </Typography>
                ) : (
                  <List dense disablePadding>
                    {invoiceOptions.map((o) => (
                      <ListItemButton key={o.id} selected={invoiceNumber === o.number}
                        onClick={() => setInvoiceNumber(o.number)}
                        sx={{ border: `1px solid ${tokens.border}`, borderRadius: '8px', mb: 0.75, alignItems: 'flex-start' }}>
                        <Radio size="small" checked={invoiceNumber === o.number} tabIndex={-1} sx={{ p: 0, mt: 0.25, mr: 1 }} />
                        <ListItemText
                          primary={(
                            <Typography variant="body2" sx={{ fontWeight: 500 }}>
                              Nota {o.number} · {o.emitter}
                              {o.declared && (
                                <Typography component="span" variant="caption" color="success.main">
                                  {' '}· lida na entrada
                                </Typography>
                              )}
                            </Typography>
                          )}
                          secondary={(
                            <Typography variant="caption" color="text.secondary" sx={mono}>
                              {o.productCode}{o.description ? ` · ${o.description}` : ''} · faltam {o.missing}
                            </Typography>
                          )} />
                      </ListItemButton>
                    ))}
                  </List>
                )}

                <TextField size="small" label="Número da nota" value={invoiceNumber} autoComplete="off"
                  onChange={(e) => setInvoiceNumber(e.target.value.replace(/\D/g, ''))}
                  helperText={invoiceOptions && invoiceOptions.length > 0
                    ? 'Escolha acima ou digite outro número.'
                    : 'A nota já precisa estar no sistema para ser vinculada.'} />
              </Stack>
            )}
            {mode === 'RESOLVED' && !withoutInvoice && !notDelivered && (
              <Stack spacing={1.25}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase' }}>
                  Lotes do item lido no hospital
                </Typography>
                {suggested === null ? (
                  <Typography variant="body2" color="text.secondary">Carregando…</Typography>
                ) : suggested.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    Não foi possível identificar o item lido no hospital. Pesquise o lote ou a REF abaixo.
                  </Typography>
                ) : (
                  <LotChoices options={suggested} value={lotId} onChange={setLotId} />
                )}

                <TextField size="small" label="Não está na lista? Pesquise por lote, REF ou material" value={search} autoComplete="off"
                  onChange={(e) => setSearch(e.target.value)}
                  InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
                  helperText={term.length === 1 ? 'Digite ao menos 2 caracteres.' : ' '} />
                {term.length >= 2 && (found === null ? (
                  <Typography variant="body2" color="text.secondary">Pesquisando…</Typography>
                ) : found.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">Nenhum lote com saldo dentro do hospital para “{search.trim()}”.</Typography>
                ) : (
                  <>
                    <LotChoices options={found.slice(0, MAX_RESULTS)} value={lotId} onChange={setLotId} />
                    {found.length > MAX_RESULTS && (
                      <Typography variant="caption" color="text.secondary">
                        Mostrando {MAX_RESULTS} de {found.length}. Digite mais do lote, da REF ou do material para refinar.
                      </Typography>
                    )}
                  </>
                ))}
              </Stack>
            )}
            <TextField
              label={notDelivered
                ? (outcome === 'LOST' ? 'Justificativa' : 'Observação (opcional)')
                : mode === 'RESOLVED' ? 'Observação (opcional)' : 'Justificativa'}
              required={notDelivered ? outcome === 'LOST' : mode === 'DISCARDED'}
              value={text} onChange={(e) => setText(e.target.value)} multiline minRows={2}
              placeholder={notDelivered
                ? (outcome === 'LOST' ? 'Ex.: caixa chegou aberta, correios abriram processo' : '')
                : mode === 'DISCARDED'
                  ? (withoutInvoice ? 'Ex.: reposição de cirurgia, a empresa não emitiu nota' : 'Ex.: etiqueta do paciente lida por engano')
                  : ''} />
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button variant="outlined" onClick={onClose}>Cancelar</Button>
        <Button variant="contained" onClick={save} disabled={!valid || saving}>
          {notDelivered ? 'Registrar'
            : mode === 'DISCARDED' ? (withoutInvoice ? 'Registrar' : 'Descartar')
            : withoutInvoice ? 'Vincular à nota' : 'Lançar lote'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/**
 * Pendências das três origens: item lido na saída em cirurgia, lote que entrou sem nota e peça que saiu
 * numa remessa e não estava na caixa.
 *
 * Ficam na mesma tela de propósito. São a mesma pergunta para quem administra - "o que ficou por resolver" -
 * e separar em telas é como se perde de vista a que não é a primeira.
 */
export default function PendingReport({ query }) {
  const { data, reload } = useReport('/reports/pending-issues', query);
  const [resolving, setResolving] = useState(null);
  if (!data) return <Loading />;
  const open = data.filter((r) => r.status === 'OPEN').length;
  return (
    <Stack spacing={1.5}>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <StatusChip tone="warning">{open} em aberto</StatusChip>
        <StatusChip tone="success">{data.length - open} resolvidas ou descartadas</StatusChip>
      </Box>
      {data.length === 0 ? <Empty>Nenhuma pendência no período.</Empty> : (
        <PagedTable items={data} rowKey={(r) => r.id} minWidth={980}
          columns={[{ label: 'Data' }, { label: 'Origem' }, { label: 'Hospital' }, { label: 'Item' }, { label: 'Pendência' }, { label: 'Lançado por' }, { label: 'Situação' }, { label: '' }]}
          row={(r) => (<>
            <TableCell>
              {formatDate(r.date)}
              {r.patientName && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{r.patientName}</Typography>}
            </TableCell>
            <TableCell>
              {r.surgeryId ? 'Cirurgia' : isNotDelivered(r) ? 'Remessa' : 'Entrada'}
              {!r.surgeryId && r.entryId && (
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>#{r.entryId}</Typography>
              )}
            </TableCell>
            <TableCell>{r.hospital}</TableCell>
            <TableCell><span style={mono}>{r.enteredCode}</span>{r.enteredRef && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>REF {r.enteredRef}</Typography>}</TableCell>
            <TableCell>{REASONS[r.reason] || r.reason}</TableCell>
            <TableCell>{r.createdBy || '—'}</TableCell>
            <TableCell>
              <StatusChip tone={STATUS[r.status][0]}>{STATUS[r.status][1]}</StatusChip>
              {r.resolution && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', maxWidth: 220 }}>{r.resolution}</Typography>}
            </TableCell>
            <TableCell align="right">{r.status === 'OPEN' && <Button size="small" variant="outlined" onClick={() => setResolving(r)} sx={{ minHeight: 36 }}>Resolver</Button>}</TableCell>
          </>)} />
      )}
      <ResolveDialog issue={resolving} onClose={() => setResolving(null)} onDone={() => { setResolving(null); reload(); }} />
    </Stack>
  );
}
