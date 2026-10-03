import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { Box, Button, Tab, Tabs } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import FileDownloadOutlined from '@mui/icons-material/FileDownloadOutlined';
import UploadFileOutlined from '@mui/icons-material/UploadFileOutlined';
import PageHeader from '../../components/PageHeader';
import LotView from './LotView';
import SummaryView from './SummaryView';
import StockImportDialog from './StockImportDialog';
import useHospitals from '../../hooks/useHospitals';
import { useAuth } from '../../auth/AuthProvider';
import { isManager } from '../../auth/roles';
import { downloadFile } from '../../api/download';
import { useNotify } from '../../notifications/NotificationProvider';

/**
 * Stock screen. Surgical techs: summary without lots. Administrators and read-only users: tabs
 * "by lot" (with every hospital) and "by material" (summary).
 */
export default function StockPage() {
  const { user } = useAuth();
  const notify = useNotify();
  const manager = isManager(user);
  const surgicalTech = user.role === 'SURGICAL_TECH';
  const { hospitals: all } = useHospitals();
  // Distribution centers only appear in the lot view (their storeroom stock).
  const hospitals = all.filter((h) => h.type === 'HOSPITAL');
  const [tab, setTab] = useState('lots');
  const [lotHospital, setLotHospital] = useState('');
  const [summaryHospital, setSummaryHospital] = useState('');
  const [importOpen, setImportOpen] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!summaryHospital && hospitals.length) setSummaryHospital(hospitals[0].id);
  }, [hospitals, summaryHospital]);

  const exportSpreadsheet = async () => {
    try {
      await downloadFile(`/stock/hospital/${lotHospital}/xlsx`, `estoque-${lotHospital}.xlsx`);
    } catch (err) {
      notify.error(err);
    }
  };

  if (surgicalTech) {
    return (
      <>
        <PageHeader title="Estoque no hospital" subtitle="Quantidade disponível por item e tamanho" />
        <SummaryView hospitals={hospitals} hospitalId={summaryHospital} onHospitalChange={setSummaryHospital} reloadKey={reloadKey} />
      </>
    );
  }

  return (
    <>
      <PageHeader title="Estoque" subtitle="Itens por local, com lote e validade"
        actions={tab === 'lots' && (
          <>
            <Button variant="outlined" startIcon={<FileDownloadOutlined />} onClick={exportSpreadsheet} disabled={!lotHospital}
              title={lotHospital ? undefined : 'Escolha um hospital para exportar'}>
              Exportar planilha
            </Button>
            {manager && (
              <>
                <Button variant="outlined" startIcon={<UploadFileOutlined />} onClick={() => setImportOpen(true)}>Importar planilha</Button>
                <Button variant="contained" startIcon={<AddIcon />} component={RouterLink} to="/entrada">Nova entrada</Button>
              </>
            )}
          </>
        )} />

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} aria-label="Visões do estoque">
          <Tab value="lots" label="Por lote" />
          <Tab value="summary" label="Por material" />
        </Tabs>
      </Box>

      {tab === 'lots' ? (
        <LotView hospitals={all} hospitalId={lotHospital} onHospitalChange={setLotHospital} canAdjust={manager} reloadKey={reloadKey} />
      ) : (
        <SummaryView hospitals={hospitals} hospitalId={summaryHospital} onHospitalChange={setSummaryHospital} reloadKey={reloadKey} />
      )}

      {manager && (
        <StockImportDialog open={importOpen} hospitals={all} defaultHospitalId={lotHospital}
          onClose={() => setImportOpen(false)} onImported={() => setReloadKey((k) => k + 1)} />
      )}
    </>
  );
}
