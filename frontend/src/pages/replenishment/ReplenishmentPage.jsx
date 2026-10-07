import { useEffect, useMemo, useState } from 'react';
import { Box, MenuItem, Tab, Tabs, TextField } from '@mui/material';
import PageHeader from '../../components/PageHeader';
import useHospitals from '../../hooks/useHospitals';
import ReplenishmentTab from './ReplenishmentTab';
import MinimumsTab from './MinimumsTab';

/**
 * Replenishment: what to take from the storeroom to the hospital (below the hospital ideal) and what to order
 * from the supplier (below the ideal total), and the minimum levels of each hospital. Expired lots do not count.
 */
export default function ReplenishmentPage() {
  const { hospitals, loading } = useHospitals();
  const active = useMemo(() => hospitals.filter((h) => h.active !== false), [hospitals]);
  const [hospitalId, setHospitalId] = useState('');
  const [tab, setTab] = useState('replenish');

  useEffect(() => {
    if (!hospitalId && active.length) setHospitalId(active[0].id);
  }, [active, hospitalId]);

  const hospital = active.find((h) => h.id === hospitalId) || null;

  return (
    <>
      <PageHeader title="Reposição"
        subtitle={hospital ? `${hospital.name} · calculado sobre lotes dentro da validade` : 'Calculado sobre lotes dentro da validade'}
        actions={(
          <TextField select size="small" label="Hospital" value={hospitalId} sx={{ minWidth: 240 }} disabled={loading}
            onChange={(e) => setHospitalId(Number(e.target.value))}>
            {active.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
          </TextField>
        )} />
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2.5 }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)}>
          <Tab value="replenish" label="Reposição" />
          <Tab value="minimums" label="Mínimos (ideais)" />
        </Tabs>
      </Box>
      {hospital && tab === 'replenish' && <ReplenishmentTab hospital={hospital} hospitals={active} />}
      {hospital && tab === 'minimums' && <MinimumsTab hospital={hospital} />}
    </>
  );
}
