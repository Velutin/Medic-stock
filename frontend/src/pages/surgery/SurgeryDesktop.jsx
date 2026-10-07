import { useRef, useState } from 'react';
import { Box, Button, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import PageHeader from '../../components/PageHeader';
import FileDropZone from '../../components/FileDropZone';
import { todayIso } from './useSurgeryDraft';
import SheetReview from './SheetReview';
import {
  ItemsTable, OpenSurgeries, PendingIssues, ReadingProgress, SheetStatus, SurgeryTotal, completeBlocker,
} from './SurgeryParts';

const SHEET_TYPES = '.pdf,application/pdf,image/jpeg,image/png';

/** Computer version: surgery and sheet on the left, items on the right. */
export default function SurgeryDesktop({ draft }) {
  const { form, setField, surgery } = draft;
  const [code, setCode] = useState('');
  const [quantity, setQuantity] = useState(1);
  const codeInput = useRef(null);
  const locked = Boolean(surgery);
  const blocker = completeBlocker(surgery);

  const add = async (e) => {
    e.preventDefault();
    if (!code.trim()) return;
    const ok = await draft.addCode(code, { typed: false, quantity: Math.max(1, Number(quantity) || 1) });
    if (ok) {
      setCode('');
      setQuantity(1);
    }
    codeInput.current?.focus();
  };

  return (
    <>
      <PageHeader title="Saída em cirurgia" subtitle="Lançamento dos itens usados na cirurgia"
        actions={surgery && <Button variant="outlined" onClick={draft.startNew}>Nova cirurgia</Button>} />
      {!surgery && <OpenSurgeries surgeries={draft.openSurgeries} onResume={draft.resume} />}

      <Box sx={{ display: 'grid', gap: 2.5, alignItems: 'start', gridTemplateColumns: { md: 'minmax(300px, 1fr) minmax(0, 2fr)' } }}>
        <Stack spacing={2.5}>
          <Paper variant="outlined" component="section" sx={{ p: 3 }}>
            <Stack spacing={2}>
              <Typography variant="h3" component="h2">Cirurgia</Typography>
              <TextField select label="Hospital" value={form.hospitalId} disabled={locked || draft.loadingHospitals}
                onChange={(e) => setField('hospitalId', Number(e.target.value))}>
                {draft.destinations.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
              </TextField>
              <TextField label="Nome do paciente" value={form.patientName} disabled={locked}
                onChange={(e) => setField('patientName', e.target.value)} inputProps={{ maxLength: 200 }} />
              <TextField type="date" label="Data da cirurgia" value={form.surgeryDate} disabled={locked}
                onChange={(e) => setField('surgeryDate', e.target.value)} InputLabelProps={{ shrink: true }}
                inputProps={{ max: todayIso() }} />
            </Stack>
          </Paper>

          <Paper variant="outlined" component="section" sx={{ p: 3 }}>
            <Stack spacing={1.5}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                <Typography variant="h3" component="h2">Ficha da cirurgia</Typography>
                <Typography variant="caption" color="warning.main" sx={{ fontWeight: 600 }}>Obrigatória</Typography>
              </Box>
              <FileDropZone title="Arraste o PDF da ficha aqui" hint="Fotos (JPG ou PNG) são convertidas em PDF. As etiquetas são lidas para conferência."
                accept={SHEET_TYPES} multiple buttonLabel="Escolher arquivo" disabled={Boolean(draft.reading)}
                onFile={(files) => draft.sendSheet(files)} />
              <ReadingProgress reading={draft.reading} />
              <SheetStatus surgery={surgery} />
            </Stack>
          </Paper>
        </Stack>

        <Paper variant="outlined" component="section" sx={{ p: 3, minWidth: 0 }}>
          <Stack spacing={2}>
            <Typography variant="h3" component="h2">Itens utilizados</Typography>
            <Box component="form" noValidate onSubmit={add}
              sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', lg: 'minmax(220px, 1fr) 90px auto' } }}>
              <TextField label="Lote, código de barras ou QR" placeholder="Leia ou digite e tecle Enter" value={code}
                inputRef={codeInput} onChange={(e) => setCode(e.target.value)} autoComplete="off" disabled={draft.busy} />
              <TextField type="number" label="Qtd." value={quantity} onChange={(e) => setQuantity(e.target.value)}
                inputProps={{ min: 1, step: 1 }} />
              <Button type="submit" variant="contained" startIcon={<AddIcon />} disabled={draft.busy} sx={{ minHeight: 56 }}>
                Adicionar
              </Button>
            </Box>
            <ItemsTable surgery={surgery} showValues={draft.showValues} onRemove={draft.removeItem} />
            <PendingIssues surgery={surgery} />
            <SurgeryTotal surgery={surgery} showValues={draft.showValues} />
            <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 1.5 }}>
              <Typography variant="body2" color="text.secondary">{blocker || 'Tudo pronto para concluir.'}</Typography>
              <Button variant="contained" size="large" onClick={draft.complete} disabled={Boolean(blocker) || draft.busy}>
                Concluir cirurgia
              </Button>
            </Box>
          </Stack>
        </Paper>
      </Box>

      <SheetReview rows={draft.review} recordedByLot={draft.recordedByLot} busy={draft.busy}
        onCancel={() => draft.setReview(null)} onConfirm={draft.recordLabels} />
    </>
  );
}
