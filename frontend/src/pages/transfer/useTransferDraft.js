import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../../api/client';
import { downloadFile } from '../../api/download';
import { readCode } from '../../api/scan';
import useHospitals from '../../hooks/useHospitals';
import { useNotify } from '../../notifications/NotificationProvider';
import { sameLot } from '../../utils/lot';

/**
 * Storeroom -> hospital transfer (delivery), shared by the computer and phone versions.
 * Destinations: active regular hospitals. For a hospital supplied by a distribution center (SESAB), the
 * lots come from the center's storeroom; otherwise from the storeroom assigned to the hospital itself.
 * Expired lots are not listed (the API also refuses them).
 */
/**
 * prefill (optional, from the Replenishment screen): { hospitalId, lots: { [lotId]: quantity } } — the destination
 * comes chosen and the quantities are filled once the storeroom lots are loaded (still editable).
 */
export default function useTransferDraft(prefill) {
  const notify = useNotify();
  const { hospitals, loading: loadingHospitals } = useHospitals();
  const destinations = useMemo(
    () => hospitals.filter((h) => h.type === 'HOSPITAL' && h.active !== false),
    [hospitals],
  );
  const [hospitalId, setHospitalId] = useState(prefill?.hospitalId || '');
  const pendingPrefill = useRef(prefill?.lots || null);
  const [rows, setRows] = useState([]);
  const [loadingRows, setLoadingRows] = useState(false);
  const [selection, setSelection] = useState({});
  const [sending, setSending] = useState(false);
  const [lastDelivery, setLastDelivery] = useState(null);

  const hospital = destinations.find((h) => h.id === hospitalId) || null;
  const source = hospital?.distributionCenterId
    ? hospitals.find((h) => h.id === hospital.distributionCenterId) || { id: hospital.distributionCenterId, name: 'centro de distribuição' }
    : hospital;

  const sourceId = source?.id;

  const loadRows = useCallback(async () => {
    if (!sourceId) {
      setRows([]);
      return;
    }
    setLoadingRows(true);
    try {
      const all = await api(`/stock/hospital/${sourceId}`);
      setRows(all.filter((r) => !r.expired && r.storeroomQuantity > 0));
    } catch (err) {
      notify.error(err);
      setRows([]);
    } finally {
      setLoadingRows(false);
    }
  }, [sourceId, notify]);

  useEffect(() => { loadRows(); }, [loadRows]);

  // Quantities suggested by the replenishment, limited to what is in the storeroom now
  useEffect(() => {
    if (!pendingPrefill.current || loadingRows || rows.length === 0) return;
    const next = {};
    Object.entries(pendingPrefill.current).forEach(([lotId, qty]) => {
      const row = rows.find((r) => r.lotId === Number(lotId));
      if (row && qty > 0) next[row.lotId] = Math.min(row.storeroomQuantity, qty);
    });
    pendingPrefill.current = null;
    setSelection(next);
  }, [rows, loadingRows]);

  const changeHospital = useCallback((id) => {
    setHospitalId(id);
    setSelection({});
    setLastDelivery(null);
  }, []);

  /** Quantity to send of a lot, limited to what is in the storeroom. */
  const setQuantity = useCallback((lotId, value) => {
    const row = rows.find((r) => r.lotId === lotId);
    const max = row ? row.storeroomQuantity : 0;
    const qty = Math.max(0, Math.min(max, Math.floor(Number(value) || 0)));
    setSelection((s) => {
      const next = { ...s };
      if (qty > 0) next[lotId] = qty;
      else delete next[lotId];
      return next;
    });
  }, [rows]);

  /** Lines selected, in the storeroom order, with the quantity to send. */
  const selected = useMemo(
    () => rows.filter((r) => selection[r.lotId] > 0).map((r) => ({ ...r, quantity: selection[r.lotId] })),
    [rows, selection],
  );
  const totals = useMemo(() => ({
    lots: selected.length,
    units: selected.reduce((sum, r) => sum + r.quantity, 0),
  }), [selected]);

  /** Fills the quantities with the replenishment suggestion (lots chosen by expiry date). */
  const fillSuggestion = useCallback(async () => {
    if (!hospitalId) return;
    try {
      const suggestions = await api(`/hospitals/${hospitalId}/replenishment-suggestions`);
      const next = {};
      suggestions.forEach((s) => (s.storeroomLots || []).forEach((l) => {
        const row = rows.find((r) => r.lotId === l.lotId);
        if (row && l.quantity > 0) next[l.lotId] = Math.min(row.storeroomQuantity, (next[l.lotId] || 0) + l.quantity);
      }));
      if (Object.keys(next).length === 0) {
        notify.info('Nenhuma reposição sugerida para este hospital: o estoque está no ideal ou falta material na sala.');
        return;
      }
      setSelection(next);
    } catch (err) {
      notify.error(err);
    }
  }, [hospitalId, rows, notify]);

  /**
   * Phone: each reading adds one unit. The code is matched against the storeroom lots of this hospital;
   * returns { candidates } when the same lot number has several expiry dates and the code does not say which.
   * Plain function (not memoized) so it always sees the current list.
   */
  const addScanned = async (code) => {
    if (!hospitalId) {
      notify.warning('Escolha o hospital de destino antes de ler os itens.');
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
      ? rows.filter((r) => sameLot(r.lot, scan.lot)
          && (!scan.material || r.materialId === scan.material.id)
          && (!scan.expiryDate || r.expiryDate === scan.expiryDate))
      : rows.filter((r) => sameLot(r.lot, typed));

    if (candidates.length === 0) {
      if (!scan.lot && scan.material) {
        notify.error(`O código identifica a REF ${scan.material.ref}, mas não traz o lote. Leia a etiqueta do lote ou digite o lote.`);
      } else {
        notify.error(`Lote ${scan.lot || typed} não está na sala de ${source.name} (ou está vencido).`);
      }
      return null;
    }
    if (candidates.length > 1) return { candidates };
    return { added: addOne(candidates[0]) };
  };

  /** Adds one unit of a storeroom lot; refuses when every unit in the storeroom is already in the list. */
  const addOne = (row) => {
    const current = selection[row.lotId] || 0;
    if (current >= row.storeroomQuantity) {
      notify.error(`Todas as ${row.storeroomQuantity} unidades do lote ${row.lot} na sala já estão na lista.`);
      return false;
    }
    setSelection((s) => ({ ...s, [row.lotId]: (s[row.lotId] || 0) + 1 }));
    notify.success(`${row.ref} · lote ${row.lot}: ${current + 1} un.`);
    return true;
  };

  /** Records the delivery and downloads the delivery report (Classic layout). */
  const submit = async () => {
    if (!hospitalId || selected.length === 0) return;
    setSending(true);
    try {
      const delivery = await api('/deliveries', {
        method: 'POST',
        body: { hospitalId, items: selected.map((r) => ({ lotId: r.lotId, quantity: r.quantity })) },
      });
      setLastDelivery(delivery);
      setSelection({});
      notify.success(`Transferência #${delivery.id} registrada para ${delivery.hospital}.`);
      loadRows();
      await downloadPdf(delivery);
    } catch (err) {
      notify.error(err);
    } finally {
      setSending(false);
    }
  };

  const downloadPdf = async (delivery) => {
    try {
      await downloadFile(`/deliveries/${delivery.id}/pdf`, `entrega-${delivery.id}.pdf`);
    } catch (err) {
      notify.error(`A transferência foi registrada, mas o PDF não foi baixado: ${err.message}`);
    }
  };

  return {
    destinations, loadingHospitals, hospitalId, changeHospital, hospital, source, rows, loadingRows,
    selection, setQuantity, selected, totals, fillSuggestion, addScanned, addOne, submit, sending,
    lastDelivery, downloadPdf,
  };
}
