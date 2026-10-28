import { useState } from 'react';
import { Box, Button, Tab, Tabs } from '@mui/material';
import RefreshOutlined from '@mui/icons-material/RefreshOutlined';
import PageHeader from '../../components/PageHeader';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import InvoicesTab from './InvoicesTab';
import MailboxTab from './MailboxTab';
import InvoiceSettingsTab from './InvoiceSettingsTab';

/**
 * Material announced by the supplier and still on its way.
 *
 * It is not stock and never counts as one: what it answers is the question the stock cannot answer on its
 * own, which is whether material that left the factory actually arrived. An invoice still open after the
 * configured number of days is what gets charged to the supplier.
 */
export default function InvoicesPage() {
  const notify = useNotify();
  const [tab, setTab] = useState('open');
  const [reload, setReload] = useState(0);
  const [reading, setReading] = useState(false);

  const readNow = async () => {
    setReading(true);
    try {
      await api('/invoices/mailbox/readings', { method: 'POST' });
      notify.success('Leitura solicitada. As notas aparecem aqui conforme forem lidas.');
      setTimeout(() => setReload((n) => n + 1), 4000);
    } catch (err) {
      notify.error(err);
    } finally {
      setReading(false);
    }
  };

  return (
    <>
      <PageHeader title="Em transporte"
        subtitle="Notas enviadas pela empresa, com o que ainda não chegou"
        actions={(
          <Button variant="outlined" startIcon={<RefreshOutlined />} onClick={readNow} disabled={reading}>
            Ler e-mails agora
          </Button>
        )} />

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2.5 }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile>
          <Tab value="open" label="Em transporte" />
          <Tab value="received" label="Recebidas" />
          <Tab value="all" label="Todas" />
          <Tab value="mailbox" label="Caixa de e-mail" />
          <Tab value="settings" label="Configurações" />
        </Tabs>
      </Box>

      {tab === 'open' && <InvoicesTab status="OPEN" reload={reload} />}
      {tab === 'received' && <InvoicesTab status="RECEIVED" reload={reload} />}
      {tab === 'all' && <InvoicesTab status="ALL" reload={reload} />}
      {tab === 'mailbox' && <MailboxTab reload={reload} />}
      {tab === 'settings' && <InvoiceSettingsTab />}
    </>
  );
}
