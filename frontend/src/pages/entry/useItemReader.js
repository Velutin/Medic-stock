import { useCallback, useState } from 'react';
import { readCode } from '../../api/scan';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import { isProductCode, validateItem } from './useEntryDraft';

const EMPTY = { code: '', gtin: '', material: null, lot: '', expiryDate: '', monthOnly: false, quantity: 1,
  expectLot: false, invoiceNumber: '' };

/** Codes that identify a product (GS1 QR/Data Matrix/GS1-128 or a bare GTIN), not a lot. */
const looksLikeProductCode = (v) => /^[(\]]/.test(v) || v.includes('\u001D') || /^\d{8}$|^\d{12,14}$/.test(v)
  || /^01\d{14}/.test(v);

/**
 * The item being read for the entry: code (QR, barcode or REF) -> material, plus lot, expiry date and quantity.
 *  - QR code (GS1): material, lot and expiry date at once.
 *  - GTIN barcode: only the material; the next reading is taken as the LOT barcode, and the same product
 *    code read again is refused. The expiry date is then typed.
 *  - lote de nota aberta: um passo só. O número do lote basta, porque a REF e a validade já vieram
 *    declaradas pelo fabricante na nota fiscal; o que era digitado deixa de ser digitado.
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

  /**
   * Lote que alguma nota aberta ainda deve. Responde vazio quando nenhuma deve, ou quando as notas
   * discordam da REF ou da validade — nesses casos o fluxo normal continua valendo.
   */
  const invoiceLot = async (lot) => {
    const value = (lot || '').trim();
    if (!value) return null;
    try {
      return await api(`/stock-entries/invoice-lots/${encodeURIComponent(value)}`);
    } catch {
      // A entrada não pode depender disto: falhando a consulta, segue-se o caminho de sempre
      return null;
    }
  };

  /** Preenche o item inteiro a partir do que a nota declarou para aquele lote. */
  const applyInvoiceLot = (value, found) => {
    setUnknown(null);
    setItem({
      ...EMPTY,
      code: found.ref,
      material: { id: found.materialId, ref: found.ref, description: found.description,
        component: found.component, size: found.size, color: found.color },
      lot: found.lot,
      expiryDate: found.expiryDate,
      invoiceNumber: found.invoiceNumber,
      // Dia 1 vindo da nota é provisório, como a validade digitada em MM/AAAA: uma data cheia lida depois
      // por QR substitui esta no mesmo lote, em vez de criar outro
      monthOnly: found.monthOnly,
    });
    notify.success(`Lote ${found.lot} da nota ${found.invoiceNumber}: ${found.ref}, validade preenchida.`);
  };

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
      if (!result.material) {
        // Não é REF nem GTIN conhecido: pode ser o número do lote, que a nota já identifica por inteiro
        const found = await invoiceLot(result.lot || value);
        if (found) {
          applyInvoiceLot(value, found);
          // A validade vai no retorno para a tela saber que não há nada a digitar e pular para a quantidade
          return { ...result, material: { id: found.materialId, ref: found.ref },
            lot: found.lot, expiryDate: found.expiryDate, fromInvoice: true };
        }
      }
      apply(value, result);
      return result;
    } catch (err) {
      notify.error(err);
      return null;
    } finally {
      setReading(false);
    }
  }, [notify]);

  const takeLot = async (value) => {
    const lot = value.toUpperCase();
    setItem((it) => ({ ...it, lot, expectLot: false }));
    setErrors((e) => ({ ...e, lot: undefined }));

    // Lendo o lote depois do GTIN, a validade ainda pode vir da nota: ela foi declarada pelo fabricante
    const found = await invoiceLot(lot);
    if (found && found.materialId === item.material?.id) {
      setItem((it) => ({ ...it, lot, expectLot: false, expiryDate: found.expiryDate,
        monthOnly: found.monthOnly, invoiceNumber: found.invoiceNumber }));
      notify.success(`Lote ${lot} da nota ${found.invoiceNumber}: validade preenchida.`);
      return { lot, expiryDate: found.expiryDate };
    }
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
    if (!looksLikeProductCode(value)) return await takeLot(value);

    // Digits with the shape of a product code: another product only if the catalog knows it
    setReading(true);
    try {
      const result = await readCode(value);
      if (result.lot || result.material) {
        apply(value, result);
        return result;
      }
      return await takeLot(value);
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
