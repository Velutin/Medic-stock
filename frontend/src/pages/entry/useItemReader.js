import { useCallback, useState } from 'react';
import { readCode } from '../../api/scan';
import { useNotify } from '../../notifications/NotificationProvider';
import { isProductCode, validateItem } from './useEntryDraft';

const EMPTY = { code: '', gtin: '', material: null, lot: '', expiryDate: '', monthOnly: false, quantity: 1, expectLot: false };

/** Codes that identify a product (GS1 QR/Data Matrix/GS1-128 or a bare GTIN), not a lot. */
const looksLikeProductCode = (v) => /^[(\]]/.test(v) || v.includes('\u001D') || /^\d{8}$|^\d{12,14}$/.test(v)
  || /^01\d{14}/.test(v);

/**
 * The item being read for the entry: code (QR, barcode or REF) -> material, plus lot, expiry date and quantity.
 *  - QR code (GS1): material, lot and expiry date at once.
 *  - GTIN barcode: only the material; the next reading is taken as the LOT barcode, and the same product
 *    code read again is refused. The expiry date is then typed.
 * A code that is not in the catalog sets `unknown` so the screen can offer to register the new REF on the spot.
 */
export default function useItemReader() {
  const notify = useNotify();
  const [item, setItem] = useState(EMPTY);
  const [unknown, setUnknown] = useState(null);
  const [reading, setReading] = useState(false);
  const [errors, setErrors] = useState({});

  const set = useCallback((field, value) => setItem((it) => ({ ...it, [field]: value })), []);

  /** Expiry date typed on screen; MM/AAAA is flagged so the API can reuse the lot of the same month. */
  const setExpiry = useCallback((expiryDate, meta) => setItem((it) => ({ ...it, expiryDate, monthOnly: Boolean(meta?.monthOnly) })), []);

  /** Typing a new code discards what the previous code had identified. */
  const setCode = useCallback((code) => {
    setUnknown(null);
    setItem((it) => ({ ...it, code, gtin: '', material: null, expectLot: false }));
  }, []);

  const apply = (value, result) => {
    setItem({
      ...EMPTY, code: value, gtin: result.gtin || '', material: result.material,
      lot: result.lot || '', expiryDate: result.expiryDate || '',
      expectLot: Boolean(result.gtin) && !result.lot,
    });
    setUnknown(result.material ? null : {
      ref: result.ref || (result.gtin ? '' : value.toUpperCase()),
      gtin: result.gtin || '',
    });
  };

  /** Reads a product code (QR, GTIN barcode or REF) as a new item. */
  const read = useCallback(async (code) => {
    const value = code.trim();
    if (!value) return null;
    setReading(true);
    setErrors({});
    try {
      const result = await readCode(value);
      apply(value, result);
      return result;
    } catch (err) {
      notify.error(err);
      return null;
    } finally {
      setReading(false);
    }
  }, [notify]);

  const takeLot = (value) => {
    const lot = value.toUpperCase();
    setItem((it) => ({ ...it, lot, expectLot: false }));
    setErrors((e) => ({ ...e, lot: undefined }));
    notify.success(`Lote ${lot} lido. Agora digite a validade.`);
    return { lot };
  };

  /**
   * Camera or typed code. While the lot is expected (after a GTIN barcode), the reading is the lot,
   * unless it is the same product code again (refused) or another product (starts a new item).
   * Plain function so it always sees the current item. Returns { lot } when a lot was taken.
   */
  const scan = async (code) => {
    const value = code.trim();
    if (!value) return null;
    if (!(item.expectLot && !item.lot)) return read(value);

    if (isProductCode(value, item)) {
      notify.warning('Esse é o código do produto, já lido. Leia o código de barras do LOTE.');
      return null;
    }
    if (!looksLikeProductCode(value)) return takeLot(value);

    // Digits with the shape of a product code: another product only if the catalog knows it
    setReading(true);
    try {
      const result = await readCode(value);
      if (result.lot || result.material) {
        apply(value, result);
        return result;
      }
      return takeLot(value);
    } catch (err) {
      notify.error(err);
      return null;
    } finally {
      setReading(false);
    }
  };

  /** Material registered on the spot for the code that was not in the catalog. */
  const applyMaterial = useCallback((material) => {
    setUnknown(null);
    setItem((it) => ({ ...it, material }));
  }, []);

  /** Puts a line of the list back into the reader, to fix it. */
  const load = useCallback((line) => {
    setUnknown(null);
    setErrors({});
    setItem({
      ...EMPTY,
      code: line.ref,
      material: { id: line.materialId, ref: line.ref, description: line.description, component: line.component,
        size: line.size, color: line.color },
      lot: line.lot, expiryDate: line.expiryDate, quantity: line.quantity,
    });
  }, []);

  const validate = useCallback(() => {
    const found = validateItem(item);
    setErrors(found);
    return Object.keys(found).length === 0;
  }, [item]);

  const clear = useCallback(() => {
    setItem(EMPTY);
    setUnknown(null);
    setErrors({});
  }, []);

  const awaitingLot = item.expectLot && !item.lot;
  return { item, set, setExpiry, setCode, read, scan, reading, unknown, applyMaterial, load, validate, errors, clear, awaitingLot };
}
