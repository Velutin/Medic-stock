import { useRef, useState } from 'react';
import { Box, Button, Typography } from '@mui/material';
import UploadFileOutlined from '@mui/icons-material/UploadFileOutlined';
import { tokens } from '../theme';

const ACCEPT = '.xlsx,.xls';

/** Drag-and-drop area for a spreadsheet, with a button for phones and keyboard users. */
export default function FileDropZone({ title, hint, file, onFile, disabled }) {
  const input = useRef(null);
  const [over, setOver] = useState(false);

  const pick = (files) => {
    const f = files?.[0];
    if (f) onFile(f);
  };

  return (
    <Box
      onDragOver={(e) => { e.preventDefault(); if (!disabled) setOver(true); }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => { e.preventDefault(); setOver(false); if (!disabled) pick(e.dataTransfer.files); }}
      sx={{ border: `1px dashed ${over ? tokens.primary : tokens.border}`, borderRadius: '8px', p: 2.5, textAlign: 'center',
        backgroundColor: over ? tokens.successBg : tokens.background, display: 'flex', flexDirection: 'column',
        alignItems: 'center', gap: 1 }}>
      <UploadFileOutlined sx={{ color: tokens.textMuted }} />
      <Typography sx={{ fontSize: 14, fontWeight: 500 }}>{file ? file.name : title}</Typography>
      {hint && <Typography variant="caption" color="text.secondary">{hint}</Typography>}
      <input ref={input} type="file" accept={ACCEPT} hidden onChange={(e) => { pick(e.target.files); e.target.value = ''; }} />
      <Button variant="outlined" size="small" onClick={() => input.current?.click()} disabled={disabled} sx={{ mt: 0.5 }}>
        {file ? 'Trocar arquivo' : 'Escolher arquivo .xlsx'}
      </Button>
    </Box>
  );
}
