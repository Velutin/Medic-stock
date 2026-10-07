import { createTheme } from '@mui/material/styles';

/** Palette and type from the approved prototypes. */
export const tokens = {
  primary: '#0F5C55',
  primaryDark: '#0A3F3A',
  sidebar: '#12302C',
  sidebarActive: '#1E4A44',
  sidebarBorder: '#2A554F',
  sidebarText: '#B9CCC7',
  sidebarTextStrong: '#E6EFEC',
  sidebarMuted: '#8FA9A3',
  background: '#F4F6F5',
  surface: '#FFFFFF',
  text: '#17201D',
  textMuted: '#4D5A56',
  border: '#C9D2CF',
  borderLight: '#DDE3E1',
  divider: '#EDF0EF',
  success: '#0F5C45',
  successBg: '#E3F1EC',
  warning: '#8A4206',
  warningBg: '#FDF0E1',
  error: '#A3231A',
  errorBg: '#FBE6E4',
  info: '#1F4C8F',
  infoBg: '#E4ECF7',
  mono: "'IBM Plex Mono', monospace",
};

const theme = createTheme({
  palette: {
    primary: { main: tokens.primary, dark: tokens.primaryDark, contrastText: '#FFFFFF' },
    error: { main: tokens.error, light: tokens.errorBg },
    warning: { main: tokens.warning, light: tokens.warningBg },
    success: { main: tokens.success, light: tokens.successBg },
    info: { main: tokens.info, light: tokens.infoBg },
    background: { default: tokens.background, paper: tokens.surface },
    text: { primary: tokens.text, secondary: tokens.textMuted },
    divider: tokens.borderLight,
  },
  typography: {
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif",
    h1: { fontSize: 26, fontWeight: 600, letterSpacing: '-0.02em' },
    h2: { fontSize: 19, fontWeight: 600 },
    h3: { fontSize: 16, fontWeight: 600 },
    body1: { fontSize: 15 },
    body2: { fontSize: 14 },
    caption: { fontSize: 12 },
    button: { textTransform: 'none', fontWeight: 600, fontSize: 14 },
  },
  shape: { borderRadius: 6 },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: { margin: 0 },
        a: { color: tokens.primary },
        'a:hover': { color: tokens.primaryDark },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { minHeight: 44, paddingInline: 18 },
        sizeLarge: { minHeight: 50, fontSize: 16, borderRadius: 8 },
        outlined: { borderColor: tokens.border, color: tokens.text, backgroundColor: tokens.surface },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: { backgroundColor: tokens.surface, minHeight: 44 },
        notchedOutline: { borderColor: tokens.border },
      },
    },
    MuiInputLabel: { styleOverrides: { root: { fontSize: 14 } } },
    MuiDialog: { styleOverrides: { paper: { borderRadius: 10 } } },
    MuiPaper: { styleOverrides: { outlined: { borderColor: tokens.borderLight } } },
    MuiTableCell: {
      styleOverrides: {
        head: { fontSize: 12, fontWeight: 500, color: tokens.textMuted, backgroundColor: tokens.background },
        root: { borderColor: tokens.divider },
      },
    },
    MuiAlert: {
      styleOverrides: {
        root: { fontSize: 14, alignItems: 'center' },
        standardError: { backgroundColor: tokens.errorBg, color: tokens.error },
        standardWarning: { backgroundColor: tokens.warningBg, color: tokens.warning },
        standardSuccess: { backgroundColor: tokens.successBg, color: tokens.success },
        standardInfo: { backgroundColor: tokens.infoBg, color: tokens.info },
      },
    },
  },
});

export default theme;
