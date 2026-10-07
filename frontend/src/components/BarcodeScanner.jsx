import { useEffect, useRef, useState } from 'react';
import { Box, Typography } from '@mui/material';
import { BrowserMultiFormatReader } from '@zxing/browser';
import { BarcodeFormat, DecodeHintType } from '@zxing/library';
import { tokens } from '../theme';

/** Formats found on implant labels: GS1 QR code / Data Matrix, GS1-128 and EAN/UPC barcodes. */
const HINTS = new Map([
  [DecodeHintType.POSSIBLE_FORMATS, [
    BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.CODE_128,
    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A,
  ]],
]);
/** Minimum time between two accepted readings. */
const COOLDOWN_MS = 800;
/** Attempts without any code in front of the camera before the same code can be accepted again (~0.6 s). */
const MISSES_TO_REARM = 3;
/**
 * Rear camera at the highest resolution the phone offers, as an "ideal" (a phone that cannot do it gives
 * what it can, and the camera still opens). Resolution is what makes a small QR code readable: without
 * asking, Android gives around 640x480, too few pixels on the code, and the user ends up moving the phone
 * closer than the lens can focus - a rear camera stops focusing at about 10 cm, and the browser cannot switch
 * to the macro lens the way the phone's own camera app does. With more pixels the code is read from a
 * distance where the lens focuses normally.
 */
const CAMERA = {
  audio: false,
  video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
};

/**
 * Asks for continuous autofocus when the phone exposes it. Usually it is already the default; this covers a
 * camera left in manual focus. Everything is optional in the browsers, so a failure here is ignored: the
 * reader keeps working with the focus the phone chose.
 */
function keepFocusContinuous(video) {
  const track = video.srcObject?.getVideoTracks?.()[0];
  if (!track?.getCapabilities) return;
  try {
    if (!track.getCapabilities().focusMode?.includes('continuous')) return;
    track.applyConstraints({ advanced: [{ focusMode: 'continuous' }] }).catch(() => {});
  } catch (unsupported) {
    // getCapabilities is not available in every browser (e.g. older iOS): nothing to do.
  }
}

function cameraMessage(err) {
  if (!window.isSecureContext) return 'A câmera só funciona com o sistema aberto em HTTPS.';
  if (err?.name === 'NotAllowedError') return 'Permissão da câmera negada. Libere o acesso à câmera nas configurações do navegador.';
  if (err?.name === 'NotFoundError' || err?.name === 'OverconstrainedError') return 'Nenhuma câmera encontrada neste aparelho.';
  if (err?.name === 'NotReadableError') return 'A câmera está sendo usada por outro aplicativo.';
  return 'Não foi possível abrir a câmera.';
}

/**
 * Live camera reader (rear camera) for QR codes, Data Matrix and barcodes. Calls onCode(text) once per reading:
 * the same code is only accepted again after it leaves the camera, so holding the phone over a label
 * does not count it twice. paused: ignores readings (e.g. while the previous one is being processed).
 */
export default function BarcodeScanner({ onCode, paused = false }) {
  const frame = useRef(null);
  const onCodeRef = useRef(onCode);
  const pausedRef = useRef(paused);
  const state = useRef({ last: '', at: 0, misses: 0 });
  const [error, setError] = useState(null);

  onCodeRef.current = onCode;
  pausedRef.current = paused;

  useEffect(() => {
    let controls;
    let cancelled = false;
    // Each start gets its own <video>: stopping a previous reader clears the element it used,
    // so sharing one element made the image disappear right after opening.
    const video = document.createElement('video');
    video.muted = true;
    video.playsInline = true;
    video.setAttribute('playsinline', '');
    video.setAttribute('aria-label', 'Imagem da câmera');
    Object.assign(video.style, { position: 'absolute', inset: '0', width: '100%', height: '100%', objectFit: 'cover' });
    frame.current.prepend(video);

    // Starts on the next tick: when React mounts, unmounts and mounts again (StrictMode in development),
    // the first start is cancelled before opening the camera, so the camera is never opened twice.
    const timer = setTimeout(() => {
      const reader = new BrowserMultiFormatReader(HINTS, { delayBetweenScanAttempts: 200 });
      reader.decodeFromConstraints(CAMERA, video,
        (result) => {
          const s = state.current;
          if (!result) {
            s.misses += 1;
            return;
          }
          const text = result.getText();
          const now = Date.now();
          const sameAsLast = text === s.last && s.misses < MISSES_TO_REARM;
          s.misses = 0;
          if (pausedRef.current || sameAsLast || now - s.at < COOLDOWN_MS) return;
          s.last = text;
          s.at = now;
          if (navigator.vibrate) navigator.vibrate(60);
          onCodeRef.current(text);
        })
        .then((c) => {
          if (cancelled) {
            c.stop();
            return;
          }
          controls = c;
          keepFocusContinuous(video);
        })
        .catch((err) => {
          if (!cancelled) setError(cameraMessage(err));
        });
    }, 0);

    return () => {
      cancelled = true;
      clearTimeout(timer);
      controls?.stop();
      video.remove();
    };
  }, []);

  return (
    <Box ref={frame} sx={{ position: 'relative', width: '100%', aspectRatio: '4 / 3', borderRadius: '8px', overflow: 'hidden',
      backgroundColor: tokens.sidebar, display: 'grid', placeItems: 'center' }}>
      {error ? (
        <Typography role="alert" sx={{ position: 'relative', color: '#FFFFFF', fontSize: 14, textAlign: 'center', px: 3 }}>
          {error}
        </Typography>
      ) : (
        <>
          <Box aria-hidden sx={{ position: 'relative', width: '62%', aspectRatio: '1 / 1', maxHeight: '70%',
            border: '2px solid rgba(255, 255, 255, 0.85)', borderRadius: '10px', boxShadow: '0 0 0 2000px rgba(0, 0, 0, 0.25)' }} />
          <Typography sx={{ position: 'absolute', bottom: 10, left: 0, right: 0, textAlign: 'center', color: '#FFFFFF',
            fontSize: 13, textShadow: '0 1px 2px rgba(0,0,0,0.6)' }}>
            Aponte para o QR code ou código de barras
          </Typography>
        </>
      )}
    </Box>
  );
}
