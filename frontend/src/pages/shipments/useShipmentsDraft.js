import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';

/** Hoje, em AAAA-MM-DD, que é o formato do input de data e o que a API espera. */
const todayIso = () => new Date().toLocaleDateString('sv-SE');

/**
 * Remessas despachadas e ainda não confirmadas, de transferência e de empréstimo na mesma lista.
 *
 * As duas viajam igual e são confirmadas igual, então separá-las em duas telas só faria você procurar em
 * dois lugares a mesma caixa. O que muda é o endereço da API e o texto de origem, e isso o `kind` resolve.
 */
export default function useShipmentsDraft() {
  const notify = useNotify();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [transfers, loans] = await Promise.all([
        api('/deliveries/dispatched'),
        api('/loans/dispatched'),
      ]);
      const all = [
        ...transfers.map((d) => ({
          ...d,
          kind: 'TRANSFER',
          path: `/deliveries/${d.id}`,
          label: `Transferência nº ${d.id}`,
          from: d.sourceHospital ? `sala de ${d.sourceHospital}` : `sala de ${d.hospital}`,
          to: d.hospital,
        })),
        ...loans.map((l) => ({
          ...l,
          kind: 'LOAN',
          path: `/loans/${l.id}`,
          label: `Empréstimo nº ${l.id}`,
          from: `${l.sourceHospital} (${l.sourceLocation === 'STOREROOM' ? 'sala' : 'hospital'})`,
          to: l.destinationHospital,
        })),
      ];
      all.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));
      setRows(all);
    } catch (err) {
      notify.error(err);
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [notify]);

  useEffect(() => { load(); }, [load]);

  const totals = useMemo(() => ({
    shipments: rows.length,
    units: rows.reduce((sum, r) => sum + r.items.reduce((s, i) => s + i.quantity, 0), 0),
  }), [rows]);

  /**
   * Confirma a chegada. `received` traz só as linhas que vieram a menos: linha não mencionada chegou
   * inteira, que é o caso comum e o motivo de a tela não obrigar a conferir item por item.
   */
  const confirmArrival = async (shipment, arrivedOn, received) => {
    setSending(true);
    try {
      const items = Object.entries(received || {})
        .map(([itemId, quantity]) => ({ itemId: Number(itemId), quantity }))
        .filter((i) => Number.isInteger(i.quantity));
      await api(shipment.path, {
        method: 'PATCH',
        body: { status: 'ARRIVED', arrivedOn: arrivedOn || todayIso(), items },
      });
      const faltou = items.reduce((sum, i) => {
        const line = shipment.items.find((x) => x.itemId === i.itemId);
        return sum + (line ? line.quantity - i.quantity : 0);
      }, 0);
      notify.success(faltou > 0
        ? `${shipment.label} confirmada. ${faltou} ${faltou === 1 ? 'peça ficou' : 'peças ficaram'} como pendência.`
        : `${shipment.label} confirmada.`);
      await load();
      return true;
    } catch (err) {
      notify.error(err);
      return false;
    } finally {
      setSending(false);
    }
  };

  const cancel = async (shipment) => {
    setSending(true);
    try {
      await api(shipment.path, { method: 'PATCH', body: { status: 'CANCELLED' } });
      notify.success(`${shipment.label} cancelada. O material voltou para a origem.`);
      await load();
      return true;
    } catch (err) {
      notify.error(err);
      return false;
    } finally {
      setSending(false);
    }
  };

  return { rows, loading, sending, totals, confirmArrival, cancel, reload: load, todayIso };
}
