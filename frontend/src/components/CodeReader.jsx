import { useState } from 'react';
import { Box, Button, Stack, TextField } from '@mui/material';
import KeyboardOutlined from '@mui/icons-material/KeyboardOutlined';
import BarcodeScanner from './BarcodeScanner';

/**
 * Camera reader with a typed fallback ("Câmera não leu? Digitar código"), for the phone screens.
 * onCode(text) receives what was read or typed; busy pauses the camera while a reading is processed.
 */
export default function CodeReader({ onCode, busy, typedLabel = 'Código, REF ou lote' }) {
  const [typing, setTyping] = useState(false);
  const [text, setText] = useState('');

  const submit = (e) => {
    e.preventDefault();
    const value = text.trim();
    if (!value) return;
    onCode(value);
    setText('');
  };

  return (
    <Stack spacing={1.5}>
      <BarcodeScanner onCode={onCode} paused={busy} />
      {typing ? (
        <Box component="form" onSubmit={submit} sx={{ display: 'flex', gap: 1 }}>
          <TextField label={typedLabel} value={text} onChange={(e) => setText(e.target.value)} autoFocus fullWidth
            autoCapitalize="characters" autoComplete="off" />
          <Button type="submit" variant="contained" disabled={busy || !text.trim()}>Ler</Button>
        </Box>
      ) : (
        <Button variant="text" startIcon={<KeyboardOutlined />} onClick={() => setTyping(true)} sx={{ alignSelf: 'center' }}>
          Câmera não leu? Digitar código
        </Button>
      )}
    </Stack>
  );
}
