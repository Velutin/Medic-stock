import { useRef, useState } from 'react';
import { Box, Button, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import PhotoCameraOutlined from '@mui/icons-material/PhotoCameraOutlined';
import PictureAsPdfOutlined from '@mui/icons-material/PictureAsPdfOutlined';
import CodeReader from '../../components/CodeReader';
import { todayIso } from './useSurgeryDraft';
import SheetReview from './SheetReview';
import {
  ItemsCards, OpenSurgeries, PendingIssues, ReadingProgress, SheetStatus, SurgeryTotal, completeBlocker,
} from './SurgeryParts';

/** Phone version: read each label with the camera or send the sheet (PDF or photos) to read all of them. */
export default function SurgeryMobile({ draft }) {
  const { form, setField, surgery } = draft;
  const photoInput = useRef(null);
  const pdfInput = useRef(null);
  const [scanning, setScanning] = useState(false);
  const locked = Boolean(surgery);
  const blocker = completeBlocker(surgery);
  const count = (surgery?.items || []).reduce((s, i) => s + i.quantity, 0);

  const onCode = async (code) => {
    if (scanning) return;
    setScanning(true);
    try {
      await draft.addCode(code, { typed: false });
    } finally {
      setScanning(false);
    }
  };

  const pick = (e) => {
    const files = [...e.target.files];
    e.target.value = '';
    if (files.length) draft.sendSheet(files);
  };

  return (
    <Stack spacing={2} sx={{ pb: 10 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1 }}>
        <Typography variant="h1">Saída em cirurgia</Typography>
        {surgery && <Button size="small" variant="outlined" onClick={draft.startNew}>Nova</Button>}
      </Box>
      {!surgery && <OpenSurgeries surgeries={draft.openSurgeries} onResume={draft.resume} />}

      <TextField select label="Hospital" value={form.hospitalId} fullWidth disabled={locked || draft.loadingHospitals}
        onChange={(e) => setField('hospitalId', Number(e.target.value))}>
        {draft.destinations.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
      </TextField>
      <TextField label="Paciente" value={form.patientName} fullWidth disabled={locked}
        onChange={(e) => setField('patientName', e.target.value)} inputProps={{ maxLength: 200 }} />
      <TextField type="date" label="Data da cirurgia" value={form.surgeryDate} fullWidth disabled={locked}
        onChange={(e) => setField('surgeryDate', e.target.value)} InputLabelProps={{ shrink: true }} inputProps={{ max: todayIso() }} />

      <CodeReader onCode={onCode} busy={scanning || draft.busy || Boolean(draft.reading) || Boolean(draft.review)}
        typedLabel="Lote ou código" />

      <Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 1 }}>
          <Typography variant="h3" component="h2">Itens lidos</Typography>
          <Typography variant="body2" color="text.secondary">{count} {count === 1 ? 'item' : 'itens'}</Typography>
        </Box>
        <ItemsCards surgery={surgery} showValues={draft.showValues} onRemove={draft.removeItem} />
      </Box>
      <PendingIssues surgery={surgery} />
      <SurgeryTotal surgery={surgery} showValues={draft.showValues} />

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={1.5}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
            <Typography variant="h3" component="h2">Ficha da cirurgia</Typography>
            <Typography variant="caption" color="warning.main" sx={{ fontWeight: 600 }}>Obrigatória</Typography>
          </Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1 }}>
            <Button variant="outlined" startIcon={<PhotoCameraOutlined />} onClick={() => photoInput.current?.click()}
              disabled={Boolean(draft.reading)}>Fotografar</Button>
            <Button variant="outlined" startIcon={<PictureAsPdfOutlined />} onClick={() => pdfInput.current?.click()}
              disabled={Boolean(draft.reading)}>Escolher PDF</Button>
          </Box>
          <input ref={photoInput} type="file" accept="image/*" capture="environment" multiple hidden onChange={pick} />
          <input ref={pdfInput} type="file" accept=".pdf,application/pdf,image/*" multiple hidden onChange={pick} />
          <Typography variant="caption" color="text.secondary">
            As fotos são convertidas em PDF. As etiquetas da ficha são lidas e aparecem para você conferir antes de lançar.
          </Typography>
          <ReadingProgress reading={draft.reading} />
          <SheetStatus surgery={surgery} />
        </Stack>
      </Paper>

      <Box sx={{ position: 'fixed', left: 0, right: 0, bottom: 0, p: 2, zIndex: 10, backgroundColor: 'background.paper',
        borderTop: 1, borderColor: 'divider', paddingBottom: 'calc(16px + env(safe-area-inset-bottom, 0px))' }}>
        {blocker && surgery && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75, textAlign: 'center' }}>{blocker}</Typography>
        )}
        <Button variant="contained" fullWidth size="large" onClick={draft.complete} disabled={Boolean(blocker) || draft.busy}
          sx={{ minHeight: 48 }}>
          Concluir cirurgia
        </Button>
      </Box>

      <SheetReview rows={draft.review} recordedByLot={draft.recordedByLot} busy={draft.busy}
        onCancel={() => draft.setReview(null)} onConfirm={draft.recordLabels} />
    </Stack>
  );
}
