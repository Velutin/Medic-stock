import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client';
import { downloadFile } from '../../api/download';
import { readCode } from '../../api/scan';
import useHospitals from '../../hooks/useHospitals';
import { useNotify } from '../../notifications/NotificationProvider';
import { matchExpiry, sameLot } from '../../utils/lot';
import { formatDate } from '../../utils/format';

/**
 * Hospital -> storeroom withdrawal, shared by the computer and phone versions. The opposite way of the
 * transfer, and deliberately a hook of its own: the transfer is storeroom-specific from end to end
 * (storeroom balances, the replenishment suggestion, the prefill coming from the Replenishment screen) and
 * it is the screen used every day, so a direction switch in there would put it at risk for something rare.
 *
 * Two rules differ from the transfer:
 *  - expired lots ARE listed, because clearing them out of a hospital is the main reason to withdraw;
 *  - where the material goes back to is not chosen on the screen. A hospital supplied by a distribution
 *    center has no storeroom of its own, so its material can only return to the center; the API resolves it
 *    and the screen only says where it went.
 */
export default function useStoreroomReturnDraft() {
  const notify = useNotify();
  const { hospitals, loading: loadingHospitals } = useHospitals();
  const sources = useMemo(
    () => hospitals.filter((h) => h.type === 'HOSPITAL' && h.active !== false),
    [hospitals],
  );
  const [hospitalId, setHospitalId] = useState('');
  const [reason, setReason] = useState('');
  const [notes, setNotes] = useState('');
  const [rows, setRows] = useState([]);
  const [loadingRows, setLoadingRows] = useState(false);
  const [selection, setSelection] = useState({});
  const [sending, setSending] = useState(false);
  const [lastReturn, setLastReturn] = useState(null);

  const hospital = sources.find((h) => h.id === hospitalId) || null;
  /** Where the material will go back to: the center that supplies the hospital, or the hospital itself. */
  const storeroom = hospital?.distributionCenterId
    ? hospitals.find((h) => h.id === hospital.distributionCenterId)
      || { id: hospital.distributionCenterId, name: 'centro de distribuição' }
    : hospital;

  const loadRows = useCallback(async () => {
    if (!hospitalId) {
      setRows([]);
      return;
    }
    setLoadingRows(true);
    try {
      // includeExpired: expired material inside the hospital is exactly what this screen takes out
      const all = await api(`/stock/hospital/${hospitalId}`, { query: { includeExpired: true } });
      setRows(all.filter((r) => r.hospitalQuantity > 0));
    } catch (err) {
      notify.error(err);
      setRows([]);
    } finally {
      setLoadingRows(false);
    }
  }, [hospitalId, notify]);

  useEffect(() => { loadRows(); }, [loadRows]);

  const changeHospital = useCallback((id) => {
    setHospitalId(id);
    setSelection({});
    setLastReturn(null);
  }, []);

  /** Quantity to withdraw of a lot, limited to what is inside the hospital. */
  const setQuantity = useCallback((lotId, value) => {
    const row = rows.find((r) => r.lotId === lotId);
    const max = row ? row.hospitalQuantity : 0;
    const qty = Math.max(0, Math.min(max, Math.floor(Number(value) || 0)));
    setSelection((s) => {
      const next = { ...s };
      if (qty > 0) next[lotId] = qty;
      else delete next[lotId];
      return next;
    });
  }, [rows]);

  /** Lines selected, in the list order, with the quantity to withdraw. */
  const selected = useMemo(
    () => rows.filter((r) => selection[r.lotId] > 0).map((r) => ({ ...r, quantity: selection[r.lotId] })),
    [rows, selection],
  );
  const totals = useMemo(() => ({
    lots: selected.length,
    units: selected.reduce((sum, r) => sum + r.quantity, 0),
    expiredLots: selected.filter((r) => r.expired).length,
  }), [selected]);

  /** Fills the quantities with every expired unit inside the hospital, the usual reason to withdraw. */
  const fillExpired = useCallback(() => {
    const next = {};
    rows.forEach((r) => { if (r.expired && r.hospitalQuantity > 0) next[r.lotId] = r.hospitalQuantity; });
    if (Object.keys(next).length === 0) {
      notify.info('Nenhum lote vencido dentro deste hospital.');
      return;
    }
    setSelection(next);
  }, [rows, notify]);

  /**
   * Phone: each reading adds one unit. The code is matched against the lots inside this hospital; returns
   * { candidates } when the same lot number has several expiry dates and the code does not say which.
   * Plain function (not memoized) so it always sees the current list.
   */
  const addScanned = async (code) => {
    if (!hospitalId) {
      notify.warning('Escolha o hospital antes de ler os itens.');
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
    const sameNumber = scan.lot
      ? rows.filter((r) => sameLot(r.lot, scan.lot) && (!scan.material || r.materialId === scan.material.id))
      : rows.filter((r) => sameLot(r.lot, typed));
    // Same tolerance as the loan and the transfer: the QR brings the full date and the lot may have been
    // registered on day 1 of the month, so the same month counts as the same lot.
    const candidates = scan.lot ? matchExpiry(sameNumber, scan.expiryDate) : sameNumber;

    if (candidates.length === 0) {
      if (!scan.lot && scan.material) {
        notify.error(`O código identifica a REF ${scan.material.ref}, mas não traz o lote. Leia a etiqueta do lote ou digite o lote.`);
      } else if (sameNumber.length > 0) {
        // The lot is inside the hospital, in another month: refused on the date, not on the number
        const dates = [...new Set(sameNumber.map((r) => formatDate(r.expiryDate)))].join(', ');
        notify.error(`Lote ${scan.lot} está dentro de ${hospital.name} com validade ${dates}, e a etiqueta lida diz `
          + `${formatDate(scan.expiryDate)}. Confira a etiqueta ou corrija a validade do lote no estoque.`);
      } else {
        notify.error(`Lote ${scan.lot || typed} não está dentro de ${hospital.name}.`);
      }
      return null;
    }
    if (candidates.length > 1) return { candidates };
    return { added: addOne(candidates[0]) };
  };

  /** Adds one unit of a lot inside the hospital; refuses when every unit is already in the list. */
  const addOne = (row) => {
    const current = selection[row.lotId] || 0;
    if (current >= row.hospitalQuantity) {
      notify.error(`Todas as ${row.hospitalQuantity} unidades do lote ${row.lot} no hospital já estão na lista.`);
      return false;
    }
    setSelection((s) => ({ ...s, [row.lotId]: (s[row.lotId] || 0) + 1 }));
    notify.success(`${row.ref} · lote ${row.lot}: ${current + 1} un.`);
    return true;
  };

  /** Records the withdrawal and downloads its report (Classic layout). */
  const submit = async () => {
    if (!hospitalId || selected.length === 0) return;
    if (!reason.trim()) {
      notify.warning('Informe o motivo da retirada.');
      return;
    }
    setSending(true);
    try {
      const created = await api('/storeroom-returns', {
        method: 'POST',
        body: {
          hospitalId,
          reason: reason.trim(),
          notes: notes.trim() || null,
          items: selected.map((r) => ({ lotId: r.lotId, quantity: r.quantity })),
        },
      });
      setLastReturn(created);
      setSelection({});
      setReason('');
      setNotes('');
      notify.success(`Retirada #${created.id} registrada: ${created.hospital} → sala de ${created.storeroomHospital}.`);
      loadRows();
      await downloadPdf(created);
    } catch (err) {
      notify.error(err);
    } finally {
      setSending(false);
    }
  };

  const downloadPdf = async (created) => {
    try {
      await downloadFile(`/storeroom-returns/${created.id}/pdf`, `retirada-${created.id}.pdf`);
    } catch (err) {
      notify.error(`A retirada foi registrada, mas o PDF não foi baixado: ${err.message}`);
    }
  };

  return {
    sources, loadingHospitals, hospitalId, changeHospital, hospital, storeroom, rows, loadingRows,
    selection, setQuantity, selected, totals, reason, setReason, notes, setNotes, fillExpired,
    addScanned, addOne, submit, sending, lastReturn, downloadPdf,
  };
}
