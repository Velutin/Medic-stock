import { useState } from 'react';
import { Box, Tab, Tabs } from '@mui/material';
import PageHeader from '../../components/PageHeader';
import useHospitals from '../../hooks/useHospitals';
import HospitalsTab from './HospitalsTab';
import ItemsTab from './ItemsTab';
import PricesTab from './PricesTab';
import InitialStockTab from './InitialStockTab';

/** Registry (administrators): hospitals, items, prices and initial stock load. */
export default function RegistryPage() {
  const [tab, setTab] = useState('hospitals');
  const { hospitals, reload } = useHospitals({ includeInactive: true });
  const active = hospitals.filter((h) => h.active);

  return (
    <>
      <PageHeader title="Cadastros" subtitle="Hospitais, itens, valores e carga inicial de estoque" />
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile aria-label="Cadastros">
          <Tab value="hospitals" label="Hospitais" />
          <Tab value="items" label="Itens" />
          <Tab value="prices" label="Valores" />
          <Tab value="stock" label="Estoque inicial" />
        </Tabs>
      </Box>
      {tab === 'hospitals' && <HospitalsTab hospitals={hospitals} reload={reload} />}
      {tab === 'items' && <ItemsTab />}
      {tab === 'prices' && <PricesTab hospitals={active} />}
      {tab === 'stock' && <InitialStockTab hospitals={active} />}
    </>
  );
}
