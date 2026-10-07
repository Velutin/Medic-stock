import { useCallback, useMemo, useState } from 'react';
import { api } from '../../api/client';
import useHospitals from '../../hooks/useHospitals';
import { daysUntil, formatDate } from '../../utils/format';
import { sameLot } from '../../utils/lot';

/** Today in the local time zone, as yyyy-mm-dd. */
export const todayIso = () => new Date().toLocaleDateString('sv-SE');

const emptyDraft = () => ({ hospitalId: '', entryDate: todayIso(), notes: '', items: [] });
let nextKey = 1;

/**
 * True when the value is the product code (GTIN barcode) of the item and not its lot: the GTIN barcode is
 * larger and easy to read again by accident while aiming at the lot barcode.
 */
export function isProductCode(value, { code, gtin }) {
  const v = (value || '').trim();
  if (!v) return false;
  if (code && v.toUpperCase() === code.trim().toUpperCase()) return true;
  const strip = (s) => s.replace(/^0+/, '');
  return Boolean(gtin) && /^\d+$/.test(v) && strip(v) === strip(gtin);
}

/** Same rules as the API: lot, expiry date (not expired) and a positive quantity are required. */
export function validateItem(item) {
  const { material, lot, expiryDate, quantity } = item;
  const errors = {};
  if (!material) errors.code = 'Leia ou digite o código do item.';
  if (!lot?.trim()) errors.lot = 'Informe o lote.';
  else if (isProductCode(lot, item)) errors.lot = 'Esse é o código do produto (GTIN), não o lote.';
  if (!expiryDate) errors.expiryDate = 'A validade é obrigatória.';
  else if (expiryDate === 'invalid') errors.expiryDate = 'Use MM/AAAA (ex.: 01/2030) ou DD/MM/AAAA.';
  else if (daysUntil(expiryDate) < 0) errors.expiryDate = 'Lote vencido: não pode entrar no estoque.';
  if (!(Number(quantity) >= 1) || !Number.isInteger(Number(quantity))) errors.quantity = 'Quantidade inválida.';
  return errors;
}

/**
 * State of the entry being registered (or corrected), shared by the computer and phone versions.
 * Destinations: active regular hospitals and distribution centers (SESAB); hospitals supplied by a
 * distribution center do not appear, because their material enters through the center.
 */
export default function useEntryDraft() {
  const { hospitals, loading: loadingHospitals } = useHospitals();
  const destinations = useMemo(
    () => hospitals.filter((h) => h.active !== false && !h.distributionCenterId),
    [hospitals],
  );
  const [draft, setDraft] = useState(emptyDraft);
  const [editing, setEditing] = useState(null);
  const [saving, setSaving] = useState(false);
  const [historyKey, setHistoryKey] = useState(0);
  const [refConflict, setRefConflict] = useState(null);

  const setField = useCallback((field, value) => setDraft((d) => ({ ...d, [field]: value })), []);

  /** Adds a lot; the same material + lot + expiry date already in the list is summed (as the API does). */
  const addItem = useCallback(({ material, lot, expiryDate, quantity, monthOnly = false, changeRef = false }) => {
    const lotNumber = lot.trim().toUpperCase();
    const qty = Number(quantity);
    setDraft((d) => {
      const same = d.items.find((i) => i.materialId === material.id && sameLot(i.lot, lotNumber) && i.expiryDate === expiryDate);
      if (same) {
        return { ...d, items: d.items.map((i) => (i === same ? { ...i, quantity: i.quantity + qty, changeRef: i.changeRef || changeRef } : i)) };
      }
      return {
        ...d,
        items: [...d.items, {
          key: nextKey++, materialId: material.id, ref: material.ref, description: material.description,
          component: material.component, size: material.size, color: material.color,
          lot: lotNumber, expiryDate, quantity: qty, monthOnly, changeRef,
        }],
      };
    });
  }, []);

  /**
   * Rule: a lot number belongs to only one REF. Before adding, checks the list and the API: a number already in the
   * list with another REF is refused; a number registered with another REF opens `refConflict` (LotRefChangeDialog),
   * and the items enter only after the user confirms the REF change.
   * Returns { problem } when refused, otherwise { added, waiting } (waiting: items held for the dialog).
   */
  const requestAdd = async (lines) => {
    const list = [...draft.items];
    const ready = [];
    const toCheck = [];
    for (const line of lines) {
      const lot = line.lot.trim().toUpperCase();
      const other = [...list, ...ready, ...toCheck].find((i) => sameLot(i.lot, lot)
        && (i.materialId ?? i.material?.id) !== line.material.id);
      if (other) {
        return { problem: `O lote ${lot} já está nesta entrada com a REF ${other.ref ?? other.material?.ref}. Um lote pertence a uma única REF: corrija um dos dois.` };
      }
      // REF change of this number already confirmed for this material in the list
      const confirmed = list.some((i) => sameLot(i.lot, lot) && i.materialId === line.material.id && i.changeRef);
      (confirmed ? ready : toCheck).push({ ...line, changeRef: confirmed || Boolean(line.changeRef) });
    }
    let conflicts = [];
    if (toCheck.length) {
      conflicts = await api('/stock-entries/lot-conflicts', {
        method: 'POST', body: toCheck.map((i) => ({ materialId: i.material.id, lot: i.lot.trim().toUpperCase() })),
      });
    }
    const conflictOf = (i) => conflicts.find((c) => c.materialId === i.material.id && c.lot === i.lot.trim().toUpperCase());
    [...ready, ...toCheck.filter((i) => !conflictOf(i))].forEach(addItem);
    const waiting = toCheck.filter(conflictOf);
    if (waiting.length) setRefConflict({ items: waiting, conflicts: conflicts.filter((c) => waiting.some((i) => conflictOf(i) === c)) });
    return { added: lines.length - waiting.length, waiting: waiting.length };
  };

  /** REF change confirmed: the items whose lots can change REF enter flagged; the blocked ones stay out. */
  const confirmRefChange = useCallback(() => {
    if (!refConflict) return 0;
    const allowed = refConflict.items.filter((i) => {
      const c = refConflict.conflicts.find((x) => x.materialId === i.material.id && x.lot === i.lot.trim().toUpperCase());
      return c && !c.lots.some((l) => l.usedInSurgery);
    });
    allowed.forEach((i) => addItem({ ...i, changeRef: true }));
    setRefConflict(null);
    return allowed.length;
  }, [refConflict, addItem]);

  const cancelRefChange = useCallback(() => setRefConflict(null), []);

  const removeItem = useCallback((key) => setDraft((d) => ({ ...d, items: d.items.filter((i) => i.key !== key) })), []);

  const totals = useMemo(() => ({
    lots: draft.items.length,
    units: draft.items.reduce((sum, i) => sum + i.quantity, 0),
  }), [draft.items]);

  /** Loads a recorded entry into the form to correct it. */
  const startEdit = useCallback((entry) => {
    setEditing({ id: entry.id, label: `#${entry.id} de ${formatDate(entry.entryDate)} (${entry.hospital})` });
    setDraft({
      hospitalId: entry.hospitalId,
      entryDate: entry.entryDate,
      notes: entry.notes || '',
      items: entry.items.map((i) => ({
        key: nextKey++, materialId: i.materialId, ref: i.ref, description: i.description, component: i.component,
        size: i.size, color: i.color, lot: i.lot, expiryDate: i.expiryDate, quantity: i.quantity,
      })),
    });
  }, []);

  const reset = useCallback(() => {
    setEditing(null);
    setDraft((d) => ({ ...emptyDraft(), hospitalId: d.hospitalId }));
  }, []);

  /** Checks the header of the entry; returns the message of the first problem or null. */
  const headerError = () => {
    if (!draft.hospitalId) return 'Escolha o destino do material.';
    if (!draft.entryDate) return 'Informe a data de recebimento.';
    if (draft.entryDate > todayIso()) return 'A data de recebimento não pode ser futura.';
    if (draft.items.length === 0) return 'Adicione ao menos um item.';
    return null;
  };

  /** Creates the entry (or saves the correction). Returns the saved entry. */
  const save = async () => {
    const body = {
      hospitalId: draft.hospitalId,
      entryDate: draft.entryDate,
      notes: draft.notes.trim() || null,
      items: draft.items.map(({ materialId, lot, expiryDate, quantity, monthOnly, changeRef }) => ({
        materialId, lot, expiryDate, quantity, monthOnly: Boolean(monthOnly), changeRef: Boolean(changeRef),
      })),
    };
    setSaving(true);
    try {
      const saved = editing
        ? await api(`/stock-entries/${editing.id}`, { method: 'PUT', body })
        : await api('/stock-entries', { method: 'POST', body });
      reset();
      setHistoryKey((k) => k + 1);
      return saved;
    } finally {
      setSaving(false);
    }
  };

  return {
    destinations, loadingHospitals, draft, setField, addItem, requestAdd, refConflict, confirmRefChange, cancelRefChange,
    removeItem, totals,
    editing, startEdit, reset, headerError, save, saving, historyKey,
  };
}
