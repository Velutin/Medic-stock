import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../../api/client';
import useHospitals from '../../hooks/useHospitals';
import { useAuth } from '../../auth/AuthProvider';
import { isManager } from '../../auth/roles';
import { useNotify } from '../../notifications/NotificationProvider';
import { translateMessage } from '../../api/messages';
import { readSheet } from './sheetReader';

export const todayIso = () => new Date().toLocaleDateString('sv-SE');
const emptyForm = () => ({ hospitalId: '', patientName: '', surgeryDate: todayIso() });

/** How a code was read: GS1 2D code, barcode, or typed. */
const readSourceOf = (code, typed) => {
  if (typed) return 'MANUAL';
  return code.startsWith('(') || code.includes('\u001D') || code.startsWith(']') ? 'QR_CODE' : 'BARCODE';
};

/**
 * Surgery being recorded (computer and phone share it). The surgery is created on the first item or sheet,
 * with the hospital, patient and date filled in; after that those fields are fixed.
 * Values are only shown to administrators (the API does not send them to surgical techs).
 */
export default function useSurgeryDraft() {
  const notify = useNotify();
  const { user } = useAuth();
  const showValues = isManager(user);
  const { hospitals, loading: loadingHospitals } = useHospitals();
  const destinations = useMemo(() => hospitals.filter((h) => h.type === 'HOSPITAL' && h.active !== false), [hospitals]);
  const [form, setForm] = useState(emptyForm);
  const [surgery, setSurgery] = useState(null);
  const [openSurgeries, setOpenSurgeries] = useState([]);
  const [busy, setBusy] = useState(false);
  const [reading, setReading] = useState(null);
  const [review, setReview] = useState(null);
  const creating = useRef(null);

  // a single hospital (surgical tech) comes already chosen
  useEffect(() => {
    if (!form.hospitalId && destinations.length === 1) setForm((f) => ({ ...f, hospitalId: destinations[0].id }));
  }, [destinations, form.hospitalId]);

  const loadOpen = useCallback(async () => {
    try {
      const page = await api('/surgeries', { query: { size: 50 } });
      setOpenSurgeries((page.content || []).filter((s) => s.status === 'OPEN'));
    } catch {
      setOpenSurgeries([]);
    }
  }, []);
  useEffect(() => { loadOpen(); }, [loadOpen]);

  const setField = useCallback((field, value) => setForm((f) => ({ ...f, [field]: value })), []);

  const refresh = useCallback(async (id) => {
    const data = await api(`/surgeries/${id}`);
    setSurgery(data);
    return data;
  }, []);

  /** Continues a surgery left open. */
  const resume = useCallback(async (id) => {
    try {
      const data = await refresh(id);
      setForm({ hospitalId: data.hospitalId, patientName: data.patientName, surgeryDate: data.surgeryDate });
    } catch (err) {
      notify.error(err);
    }
  }, [refresh, notify]);

  const formError = () => {
    if (!form.hospitalId) return 'Escolha o hospital.';
    if (!form.patientName.trim()) return 'Informe o nome do paciente.';
    if (!form.surgeryDate) return 'Informe a data da cirurgia.';
    if (form.surgeryDate > todayIso()) return 'A data da cirurgia não pode ser futura.';
    return null;
  };

  /** Creates the surgery on the first item or sheet (only once, even with simultaneous readings). */
  const ensureSurgery = async () => {
    if (surgery) return surgery;
    if (creating.current) return creating.current;
    const problem = formError();
    if (problem) {
      notify.warning(problem);
      return null;
    }
    creating.current = api('/surgeries', {
      method: 'POST',
      body: { hospitalId: form.hospitalId, patientName: form.patientName.trim(), surgeryDate: form.surgeryDate },
    }).then((created) => { setSurgery(created); loadOpen(); return created; })
      .catch((err) => { notify.error(err); return null; })
      .finally(() => { creating.current = null; });
    return creating.current;
  };

  /** Camera, reader or typed code: 1 unit (or the typed quantity). */
  const addCode = async (code, { typed = false, quantity = 1 } = {}) => {
    const value = code.trim();
    if (!value) return false;
    const s = await ensureSurgery();
    if (!s) return false;
    setBusy(true);
    try {
      const result = await api(`/surgeries/${s.id}/items`, {
        method: 'POST', body: { code: value, readSource: readSourceOf(value, typed), quantity },
      });
      if (result.recorded) {
        const i = result.item;
        notify.success(`${i.ref} · lote ${i.lot} lançado.`);
        if (result.warning) notify.warning(translateMessage(result.warning));
      } else {
        notify.warning(`Lote ${value} virou pendência: ${translateMessage(result.warning || '')}`);
      }
      await refresh(s.id);
      return true;
    } catch (err) {
      notify.error(err);
      return false;
    } finally {
      setBusy(false);
    }
  };

  const removeItem = async (itemId) => {
    try {
      setSurgery(await api(`/surgeries/${surgery.id}/items/${itemId}`, { method: 'DELETE' }));
    } catch (err) {
      notify.error(err);
    }
  };

  /**
   * Sheet (PDF or photos): attaches it to the surgery and reads the labels for review.
   * The sheet is attached even when no label can be read.
   */
  const sendSheet = async (files) => {
    const list = Array.isArray(files) ? files : [files];
    if (list.length === 0) return;
    const s = await ensureSurgery();
    if (!s) return;
    setReading({ step: 'upload', page: 0 });
    try {
      const form = new FormData();
      list.forEach((f) => form.append('files', f));
      setSurgery(await api(`/surgeries/${s.id}/sheet`, { method: 'POST', body: form }));
    } catch (err) {
      notify.error(err);
      setReading(null);
      return;
    }
    try {
      setReading({ step: 'read', page: 0 });
      const labels = await readSheet(list, (page) => setReading({ step: 'read', page }));
      if (labels.length === 0) {
        notify.warning('Ficha anexada, mas nenhuma etiqueta foi lida. Lance os itens pela leitura ou digitando o lote.');
        return;
      }
      setReading({ step: 'check', page: 0 });
      const body = { labels: labels.map(({ lot, gtin, code }) => ({ lot, gtin, code, ref: null, lotId: null })) };
      const preview = await api(`/surgeries/${s.id}/sheet/preview`, { method: 'POST', body });
      setReview(labels.map((l, i) => ({ ...l, key: i, preview: preview[i] })));
    } catch (err) {
      notify.error(err?.status ? err : 'Ficha anexada, mas não foi possível ler as etiquetas. Lance os itens manualmente.');
    } finally {
      setReading(null);
    }
  };

  /** Records the labels confirmed on the review. */
  const recordLabels = async (labels) => {
    if (labels.length === 0) {
      setReview(null);
      return;
    }
    setBusy(true);
    try {
      const results = await api(`/surgeries/${surgery.id}/sheet/items`, { method: 'POST', body: { labels } });
      const recorded = results.filter((r) => r.recorded).length;
      const pending = results.length - recorded;
      notify[pending ? 'warning' : 'success'](
        `${recorded} ${recorded === 1 ? 'item lançado' : 'itens lançados'}`
        + (pending ? `, ${pending} ${pending === 1 ? 'pendência' : 'pendências'} para o administrador revisar.` : '.'),
      );
      setReview(null);
      await refresh(surgery.id);
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };

  const complete = async () => {
    setBusy(true);
    try {
      await api(`/surgeries/${surgery.id}`, { method: 'PATCH', body: { status: 'COMPLETED' } });
      notify.success(`Cirurgia de ${surgery.patientName} concluída.`);
      setSurgery(null);
      setForm((f) => ({ ...emptyForm(), hospitalId: destinations.length === 1 ? f.hospitalId : '' }));
      loadOpen();
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };

  /** Leaves the surgery open (to continue later) and starts a new one. */
  const startNew = () => {
    setSurgery(null);
    setForm((f) => ({ ...emptyForm(), hospitalId: destinations.length === 1 ? f.hospitalId : '' }));
    loadOpen();
  };

  /** Units already recorded in the surgery per lot number (to flag sheet labels already scanned). */
  const recordedByLot = useMemo(() => {
    const map = {};
    (surgery?.items || []).forEach((i) => { map[i.lot] = (map[i.lot] || 0) + i.quantity; });
    return map;
  }, [surgery]);

  return {
    destinations, loadingHospitals, form, setField, surgery, openSurgeries, resume, startNew, showValues,
    addCode, removeItem, sendSheet, reading, review, setReview, recordLabels, recordedByLot, complete, busy,
  };
}
