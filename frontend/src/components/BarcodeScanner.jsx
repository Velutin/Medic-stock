import { useCallback, useEffect, useRef, useState } from 'react';
import { Box, IconButton, Typography } from '@mui/material';
import CameraswitchOutlined from '@mui/icons-material/CameraswitchOutlined';
import { LABEL_FORMATS, readBarcodes } from '../utils/zxingReader';
import { tokens } from '../theme';

/**
 * Same settings the consumption sheet reading uses, which reads these labels well (the Data Matrix of the
 * implant labels is small and often glossy). One symbol per frame: the camera reads one label at a time.
 */
const READER = {
  formats: LABEL_FORMATS, tryHarder: true, tryRotate: true, tryDownscale: true, maxNumberOfSymbols: 1,
};
/** Minimum time between two accepted readings. */
const COOLDOWN_MS = 800;
/** Attempts without any code in front of the camera before the same code can be accepted again (~0.6 s). */
const MISSES_TO_REARM = 3;
/** Pause between two attempts, on top of the time the decoding itself takes. */
const INTERVAL_MS = 120;
/**
 * Side of the square that is decoded, as a fraction of the shorter side of the camera frame, matching the aim
 * square drawn on screen. Decoding only what is inside the aim means the code fills far more of the decoded
 * pixels and each attempt is cheaper - it does for the reader what moving the phone closer cannot, since a
 * rear camera stops focusing at about 10 cm and the browser cannot switch to the macro lens.
 */
const AIM = 0.7;
/**
 * Rear camera at the highest resolution the phone offers, as an "ideal" (a phone that cannot do it gives what
 * it can, and the camera still opens). Without asking, Android gives around 640x480: too few pixels on a small
 * code, and the user ends up moving the phone closer than the lens can focus.
 */
const CAMERA = {
  audio: false,
  video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
};
/** Same resolution, on one specific camera of the phone. */
const cameraById = (deviceId) => ({
  audio: false,
  video: { deviceId: { exact: deviceId }, width: { ideal: 1920 }, height: { ideal: 1080 } },
});

/**
 * Which camera this phone reads with, kept between sessions.
 *
 * The rear camera a browser opens by default is not always the main one: on a Galaxy S24 it opens the
 * ultra-wide, whose field of view is about three times wider, so the code lands on a third of the pixels and
 * barely decodes however close or far the phone is held. The browser does not say which lens is the main one
 * - that information simply is not in the camera capabilities - and guessing it from focus distance or from
 * the maximum resolution gets it wrong. So the person chooses once, looking at the picture (the ultra-wide is
 * obviously wider), and the choice is remembered.
 */
const STORED_CAMERA = 'mss.camera';

const storedCamera = () => {
  try {
    return window.localStorage.getItem(STORED_CAMERA) || null;
  } catch (unavailable) {
    return null; // private window or storage blocked: the browser default is used
  }
};

const storeCamera = (deviceId) => {
  try {
    window.localStorage.setItem(STORED_CAMERA, deviceId);
  } catch (unavailable) {
    // Not being able to remember only means choosing again next time.
  }
};

/** Cameras to offer: the rear ones. Without a label nothing can be told apart, so the camera is kept. */
const rearCameras = (devices) => devices
  .filter((d) => d.kind === 'videoinput' && !/front|user|frontal/i.test(d.label || ''));

/**
 * Asks for continuous autofocus when the phone exposes it. Usually it is already the default; this covers a
 * camera left in manual focus. Everything here is optional in the browsers, so a failure is ignored: the
 * reader keeps working with the focus the phone chose.
 */
function keepFocusContinuous(stream) {
  const track = stream.getVideoTracks()[0];
  if (!track?.getCapabilities) return;
  try {
    if (!track.getCapabilities().focusMode?.includes('continuous')) return;
    track.applyConstraints({ advanced: [{ focusMode: 'continuous' }] }).catch(() => {});
  } catch (unsupported) {
    // getCapabilities is not available in every browser (e.g. older iOS): nothing to do.
  }
}

/**
 * Focuses on the middle of the aim, where the code is. Used when the screen is tapped: a camera that lost
 * focus on a glossy label goes back to work without the user having to move the phone away and back.
 */
function focusOnAim(stream) {
  const track = stream?.getVideoTracks()[0];
  if (!track?.getCapabilities) return;
  try {
    const capabilities = track.getCapabilities();
    const advanced = [];
    if (capabilities.pointsOfInterest) advanced.push({ pointsOfInterest: [{ x: 0.5, y: 0.5 }] });
    if (capabilities.focusMode?.includes('continuous')) advanced.push({ focusMode: 'continuous' });
    if (advanced.length) track.applyConstraints({ advanced }).catch(() => {});
  } catch (unsupported) {
    // Focusing on demand is optional: without it the camera keeps its own focus.
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
 * Live camera reader (rear camera) for Data Matrix, QR codes and barcodes. Calls onCode(text) once per
 * reading: the same code is only accepted again after it leaves the camera, so holding the phone over a label
 * does not count it twice. paused: ignores readings (e.g. while the previous one is being processed).
 */
export default function BarcodeScanner({ onCode, paused = false }) {
  const frame = useRef(null);
  const streamRef = useRef(null);
  const onCodeRef = useRef(onCode);
  const pausedRef = useRef(paused);
  const state = useRef({ last: '', at: 0, misses: 0, failures: 0 });
  const [error, setError] = useState(null);
  const [resolution, setResolution] = useState(null);
  const [cameras, setCameras] = useState([]);
  // deviceId: the camera asked for (null = whichever the browser picks). activeId: the one actually open.
  const [deviceId, setDeviceId] = useState(storedCamera);
  const [activeId, setActiveId] = useState(null);

  onCodeRef.current = onCode;
  pausedRef.current = paused;

  /** Moves to the next rear camera and remembers it on this phone. */
  const switchCamera = useCallback((event) => {
    event.stopPropagation();
    if (cameras.length < 2) return;
    const current = cameras.findIndex((c) => c.deviceId === activeId);
    const next = cameras[(current + 1) % cameras.length];
    storeCamera(next.deviceId);
    setResolution(null);
    setDeviceId(next.deviceId);
  }, [cameras, activeId]);

  useEffect(() => {
    let cancelled = false;
    let stream;
    let timer;
    // Each start gets its own <video>: stopping a previous reader clears the element it used,
    // so sharing one element made the image disappear right after opening.
    const video = document.createElement('video');
    video.muted = true;
    video.playsInline = true;
    video.setAttribute('playsinline', '');
    video.setAttribute('aria-label', 'Imagem da câmera');
    Object.assign(video.style, { position: 'absolute', inset: '0', width: '100%', height: '100%', objectFit: 'cover' });
    frame.current.prepend(video);

    const canvas = document.createElement('canvas');
    const context = canvas.getContext('2d', { willReadFrequently: true });

    const accept = (result) => {
      const s = state.current;
      if (!result) {
        s.misses += 1;
        return;
      }
      const now = Date.now();
      const sameAsLast = result.text === s.last && s.misses < MISSES_TO_REARM;
      s.misses = 0;
      if (pausedRef.current || sameAsLast || now - s.at < COOLDOWN_MS) return;
      s.last = result.text;
      s.at = now;
      if (navigator.vibrate) navigator.vibrate(60);
      onCodeRef.current(result.text);
    };

    /** Decodes the aim square of the current frame and schedules the next attempt. */
    const read = async () => {
      if (cancelled) return;
      const width = video.videoWidth;
      const height = video.videoHeight;
      if (!pausedRef.current && width && height) {
        const side = Math.round(Math.min(width, height) * AIM);
        canvas.width = side;
        canvas.height = side;
        context.drawImage(video, Math.round((width - side) / 2), Math.round((height - side) / 2), side, side,
          0, 0, side, side);
        try {
          const results = await readBarcodes(context.getImageData(0, 0, side, side), READER);
          if (cancelled) return;
          state.current.failures = 0;
          accept(results.find((r) => r.isValid !== false && r.text));
        } catch (readFailure) {
          // A frame that cannot be decoded is just the next attempt; a reader that never starts is an error.
          state.current.failures += 1;
          if (state.current.failures === 5) setError('Não foi possível iniciar o leitor de códigos. Recarregue a página.');
        }
      }
      timer = setTimeout(read, INTERVAL_MS);
    };

    // Starts on the next tick: when React mounts, unmounts and mounts again (StrictMode in development),
    // the first start is cancelled before opening the camera, so the camera is never opened twice.
    const start = setTimeout(async () => {
      try {
        // A camera chosen before is opened straight away; otherwise the browser picks, and the person can
        // change it with the button (the default is not always the main lens).
        const opened = await navigator.mediaDevices.getUserMedia(deviceId ? cameraById(deviceId) : CAMERA)
          .catch((err) => {
            // A remembered camera that no longer exists (another phone, revoked permission) falls back.
            if (!deviceId) throw err;
            storeCamera('');
            return navigator.mediaDevices.getUserMedia(CAMERA);
          });
        if (cancelled) {
          opened.getTracks().forEach((t) => t.stop());
          return;
        }
        stream = opened;
        streamRef.current = opened;
        video.srcObject = opened;
        keepFocusContinuous(opened);
        await video.play();
        if (cancelled) return;
        const settings = opened.getVideoTracks()[0]?.getSettings?.() || {};
        setResolution({
          width: settings.width || video.videoWidth,
          height: settings.height || video.videoHeight,
        });
        // Labels (and therefore the list of cameras) only exist once permission has been given, which the
        // opening above just did.
        navigator.mediaDevices.enumerateDevices?.()
          .then((devices) => { if (!cancelled) setCameras(rearCameras(devices)); })
          .catch(() => {});
        if (settings.deviceId) setActiveId(settings.deviceId);
        read();
      } catch (err) {
        if (!cancelled) setError(cameraMessage(err));
      }
    }, 0);

    return () => {
      cancelled = true;
      clearTimeout(start);
      clearTimeout(timer);
      stream?.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
      video.remove();
    };
    // Reopens when the person switches cameras; the previous one is stopped by the cleanup above.
  }, [deviceId]);

  return (
    <Box ref={frame} onClick={() => focusOnAim(streamRef.current)}
      sx={{ position: 'relative', width: '100%', aspectRatio: '4 / 3', borderRadius: '8px', overflow: 'hidden',
        backgroundColor: tokens.sidebar, display: 'grid', placeItems: 'center' }}>
      {error ? (
        <Typography role="alert" sx={{ position: 'relative', color: '#FFFFFF', fontSize: 14, textAlign: 'center', px: 3 }}>
          {error}
        </Typography>
      ) : (
        <>
          {/* The aim square is exactly the area that gets decoded (AIM of the frame's shorter side). */}
          <Box aria-hidden sx={{ position: 'relative', height: `${AIM * 100}%`, aspectRatio: '1 / 1',
            border: '2px solid rgba(255, 255, 255, 0.85)', borderRadius: '10px', boxShadow: '0 0 0 2000px rgba(0, 0, 0, 0.25)' }} />
          {cameras.length > 1 && (
            <IconButton onClick={switchCamera} aria-label="Trocar de câmera"
              sx={{ position: 'absolute', top: 8, right: 8, color: '#FFFFFF', backgroundColor: 'rgba(0, 0, 0, 0.45)',
                '&:hover': { backgroundColor: 'rgba(0, 0, 0, 0.6)' } }}>
              <CameraswitchOutlined fontSize="small" />
            </IconButton>
          )}
          <Typography sx={{ position: 'absolute', bottom: 10, left: 0, right: 0, textAlign: 'center', color: '#FFFFFF',
            fontSize: 13, textShadow: '0 1px 2px rgba(0,0,0,0.6)' }}>
            Aponte para o código e toque na tela para focar
            {resolution ? ` · ${resolution.width}×${resolution.height}` : ''}
            {cameras.length > 1 ? ` · câmera ${Math.max(0, cameras.findIndex((c) => c.deviceId === activeId)) + 1} de ${cameras.length}` : ''}
          </Typography>
        </>
      )}
    </Box>
  );
}
