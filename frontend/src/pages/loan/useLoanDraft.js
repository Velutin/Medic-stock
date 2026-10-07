import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client';
import { downloadFile } from '../../api/download';
import { readCode } from '../../api/scan';
import useHospitals from '../../hooks/useHospitals';
import { useNotify } from '../../notifications/NotificationProvider';
import { sameLot } from '../../utils/lot';

const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

/**
 * Loan between hospitals or return to Baumer, shared by the computer and phone versions.
 *  - LOAN: from the stock inside a hospital or its storeroom to inside another hospital (no distribution centers);
 *    expired lots are not listed. The supplier is informed when the loan is made (no pending status).
 *  - RETURN: the lots leave the source and go back to Baumer (no destination); expired lots can be returned and the
 *    reason is required. A distribution center (SESAB) can return from its storeroom. The return PDF (items sent to
 *    the company) is downloaded right after confirming.
 */
export default function useLoanDraft() {
  const notify = useNotify();
  const { hospitals, loading: loadingHospitals } = useHospitals();
  const [kind, setKind] = useState('LOAN');
  const [sourceId, setSourceId] = useState('');
  const [location, setLocation] = useState('HOSPITAL');
  const [destinationId, setDestinationId] = useState('');
  const [notes, setNotes] = useState('');
  const [reason, setReason] = useState('');
  const [rows, setRows] = useState([]);
  const [loadingRows, setLoadingRows] = useState(false);
  const [selection, setSelection] = useState({});
  const [sending, setSending] = useState(false);
  const [history, setHistory] = useState(null);

  const active = useMemo(() => hospitals.filter((h) => h.active !== false), [hospitals]);
  const sources = useMemo(
    () => (kind === 'RETURN' ? active : active.filter((h) => h.type === 'HOSPITAL')),
    [active, kind],
  );
  const source = sources.find((h) => h.id === sourceId) || null;
  const isCenter = source?.type === 'DISTRIBUTION_CENTER';
  const destinations = useMemo(
    () => active.filter((h) => h.type === 'HOSPITAL' && h.id !== sourceId),
    [active, sourceId],
  );

  /** Changing the type, source or location starts the item list again. */
  const changeKind = useCallback((value) => {
    setKind(value);
    setSelection({});
    setDestinationId('');
    if (value === 'LOAN' && active.find((h) => h.id === sourceId)?.type !== 'HOSPITAL') setSourceId('');
  }, [active, sourceId]);
  const changeSource = useCallback((id) => {
    setSourceId(id);
    setSelection({});
    if (active.find((h) => h.id === id)?.type === 'DISTRIBUTION_CENTER') setLocation('STOREROOM');
    setDestinationId((d) => (d === id ? '' : d));
  }, [active]);
  const changeLocation = useCallback((value) => { setLocation(value); setSelection({}); }, []);

  const loadRows = useCallback(async () => {
    if (!sourceId) {
      setRows([]);
      return;
    }
    setLoadingRows(true);
    try {
      const all = await api(`/stock/hospital/${sourceId}`, { query: { includeExpired: true } });
      setRows(all
        .map((r) => ({ ...r, available: location === 'HOSPITAL' ? r.hospitalQuantity : r.storeroomQuantity }))
        .filter((r) => r.available > 0 && (kind === 'RETURN' || !r.expired)));
    } catch (err) {
      notify.error(err);
      setRows([]);
    } finally {
      setLoadingRows(false);
    }
  }, [sourceId, location, kind, notify]);
  useEffect(() => { loadRows(); }, [loadRows]);

  const loadHistory = useCallback(async () => {
    try {
      setHistory(await api('/loans'));
    } catch (err) {
      notify.error(err);
      setHistory([]);
    }
  }, [notify]);
  useEffect(() => { loadHistory(); }, [loadHistory]);

  const setQuantity = useCallback((lotId, value) => {
    const row = rows.find((r) => r.lotId === lotId);
    const qty = Math.max(0, Math.min(row ? row.available : 0, Math.floor(Number(value) || 0)));
    setSelection((s) => {
      const next = { ...s };
      if (qty > 0) next[lotId] = qty;
      else delete next[lotId];
      return next;
    });
  }, [rows]);

  const selected = useMemo(
    () => rows.filter((r) => selection[r.lotId] > 0).map((r) => ({ ...r, quantity: selection[r.lotId] })),
    [rows, selection],
  );
  const totals = useMemo(() => ({ lots: selected.length, units: selected.reduce((s, r) => s + r.quantity, 0) }), [selected]);

  /** Adds one unit of a source lot; refuses when every unit is already in the list. */
  const addOne = (row) => {
    const current = selection[row.lotId] || 0;
    if (current >= row.available) {
      notify.error(`Todas as ${row.available} unidades do lote ${row.lot} já estão na lista.`);
      return false;
    }
    setSelection((s) => ({ ...s, [row.lotId]: (s[row.lotId] || 0) + 1 }));
    notify.success(`${row.ref} · lote ${row.lot}: ${current + 1} un.`);
    return true;
  };

  /** Phone: each reading adds one unit. Returns { candidates } when the code does not tell the expiry date. */
  const addScanned = async (code) => {
    if (!source) {
      notify.warning('Escolha o hospital de origem antes de ler os itens.');
      return null;
    }
    let scan;
    try {
      scan = await readCode(code);
    } catch (err) {
      notify.error(err);
      return null;
    }
    const typed = code.trim().toUpperCase();
    const candidates = scan.lot
      ? rows.filter((r) => sameLot(r.lot, scan.lot) && (!scan.material || r.materialId === scan.material.id)
          && (!scan.expiryDate || r.expiryDate === scan.expiryDate))
      : rows.filter((r) => sameLot(r.lot, typed));
    if (candidates.length === 0) {
      const where = location === 'HOSPITAL' ? `dentro de ${source.name}` : `na sala de ${source.name}`;
      notify.error(!scan.lot && scan.material
        ? `O código identifica a REF ${scan.material.ref}, mas não traz o lote. Leia a etiqueta do lote ou digite o lote.`
        : `Lote ${scan.lot || typed} não está ${where}${kind === 'LOAN' ? ' (ou está vencido)' : ''}.`);
      return null;
    }
    if (candidates.length > 1) return { candidates };
    return { added: addOne(candidates[0]) };
  };

  const problem = () => {
    if (!source) return 'Escolha o hospital de origem.';
    if (kind === 'LOAN' && !destinationId) return 'Escolha o hospital de destino.';
    if (kind === 'RETURN' && !reason.trim()) return 'Informe o motivo da devolução.';
    if (selected.length === 0) return 'Escolha ao menos um item.';
    return null;
  };

  const submit = async () => {
    const p = problem();
    if (p) {
      notify.warning(p);
      return null;
    }
    setSending(true);
    try {
      const saved = await api('/loans', {
        method: 'POST',
        body: {
          type: kind, sourceHospitalId: sourceId, sourceLocation: location,
          destinationHospitalId: kind === 'LOAN' ? destinationId : null,
          items: selected.map((r) => ({ lotId: r.lotId, quantity: r.quantity })),
          notes: kind === 'LOAN' ? (notes.trim() || null) : null,
          returnReason: kind === 'RETURN' ? reason.trim() : null,
        },
      });
      notify.success(kind === 'LOAN'
        ? `Empréstimo #${saved.id} registrado: ${plural(totals.units, 'unidade', 'unidades')} para ${saved.destinationHospital}.`
        : `Devolução #${saved.id} registrada: ${plural(totals.units, 'unidade', 'unidades')} saíram do estoque.`);
      setSelection({});
      setNotes('');
      setReason('');
      loadRows();
      loadHistory();
      if (kind === 'RETURN') {
        downloadFile(`/loans/${saved.id}/pdf`, `devolucao-${saved.id}.pdf`)
          .catch((err) => notify.error(`A devolução foi registrada, mas o PDF não foi baixado: ${err.message}`));
      }
      return saved;
    } catch (err) {
      notify.error(err);
      return null;
    } finally {
      setSending(false);
    }
  };

  return {
    loadingHospitals, kind, changeKind, sources, source, isCenter, sourceId, changeSource, location, changeLocation,
    destinations, destinationId, setDestinationId, notes, setNotes, reason, setReason, rows, loadingRows,
    selection, setQuantity, selected, totals, addOne, addScanned, submit, sending, history,
  };
}
