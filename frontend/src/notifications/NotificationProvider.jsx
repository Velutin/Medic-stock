import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { Alert, Snackbar } from '@mui/material';

const NotificationContext = createContext(null);

/**
 * Success/error/warning/info alerts shown one at a time (bottom on mobile, top-right on desktop).
 * Errors stay longer on screen than confirmations.
 */
export function NotificationProvider({ children }) {
  const [queue, setQueue] = useState([]);
  const current = queue[0];

  const notify = useCallback((severity, message) => {
    setQueue((q) => [...q, { key: Date.now() + Math.random(), severity, message }]);
  }, []);

  const api = useMemo(
    () => ({
      success: (message) => notify('success', message),
      error: (messageOrError) => notify('error', messageOrError?.message || messageOrError),
      warning: (message) => notify('warning', message),
      info: (message) => notify('info', message),
    }),
    [notify],
  );

  const close = (_, reason) => {
    if (reason === 'clickaway') return;
    setQueue((q) => q.slice(1));
  };

  return (
    <NotificationContext.Provider value={api}>
      {children}
      <Snackbar
        key={current?.key}
        open={Boolean(current)}
        autoHideDuration={current?.severity === 'error' ? 8000 : 4000}
        onClose={close}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        sx={{ '@media (min-width: 900px)': { top: 24, bottom: 'auto', right: 24, left: 'auto' } }}
      >
        {current ? (
          <Alert onClose={close} severity={current.severity} variant="filled" sx={{ width: '100%', maxWidth: 480 }}>
            {current.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </NotificationContext.Provider>
  );
}

export function useNotify() {
  const ctx = useContext(NotificationContext);
  if (!ctx) throw new Error('useNotify must be used inside NotificationProvider');
  return ctx;
}
