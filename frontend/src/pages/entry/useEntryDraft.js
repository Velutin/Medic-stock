import { useCallback, useMemo, useState } from 'react';
import { api } from '../../api/client';
import useHospitals from '../../hooks/useHospitals';
import { daysUntil, formatDate } from '../../utils/format';

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

  const setField = useCallback((field, value) => setDraft((d) => ({ ...d, [field]: value })), []);

  /** Adds a lot; the same material + lot + expiry date already in the list is summed (as the API does). */
  const addItem = useCallback(({ material, lot, expiryDate, quantity, monthOnly = false }) => {
    const lotNumber = lot.trim().toUpperCase();
    const qty = Number(quantity);
    setDraft((d) => {
      const same = d.items.find((i) => i.materialId === material.id && i.lot === lotNumber && i.expiryDate === expiryDate);
      if (same) {
        return { ...d, items: d.items.map((i) => (i === same ? { ...i, quantity: i.quantity + qty } : i)) };
      }
      return {
        ...d,
        items: [...d.items, {
          key: nextKey++, materialId: material.id, ref: material.ref, description: material.description,
          component: material.component, size: material.size, color: material.color,
          lot: lotNumber, expiryDate, quantity: qty, monthOnly,
        }],
      };
    });
  }, []);

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
      items: draft.items.map(({ materialId, lot, expiryDate, quantity, monthOnly }) => ({
        materialId, lot, expiryDate, quantity, monthOnly: Boolean(monthOnly),
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
    destinations, loadingHospitals, draft, setField, addItem, removeItem, totals,
    editing, startEdit, reset, headerError, save, saving, historyKey,
  };
}
