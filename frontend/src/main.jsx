import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { CssBaseline, GlobalStyles, ThemeProvider } from '@mui/material';
import theme from './theme';
import App from './App';
import { AuthProvider } from './auth/AuthProvider';
import { NotificationProvider } from './notifications/NotificationProvider';

const globalStyles = {
  '.visually-hidden': {
    position: 'absolute', width: 1, height: 1, padding: 0, margin: -1, overflow: 'hidden',
    clip: 'rect(0, 0, 0, 0)', whiteSpace: 'nowrap', border: 0,
  },
  '@media (prefers-reduced-motion: reduce)': {
    '*, *::before, *::after': { animationDuration: '0.01ms !important', transitionDuration: '0.01ms !important' },
  },
};

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <GlobalStyles styles={globalStyles} />
      <BrowserRouter>
        <NotificationProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </NotificationProvider>
      </BrowserRouter>
    </ThemeProvider>
  </React.StrictMode>,
);
