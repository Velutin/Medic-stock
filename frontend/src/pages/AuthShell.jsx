import { Box, Typography } from '@mui/material';
import { tokens } from '../theme';
import { COMPANY_NAME } from '../config';

/** Two-column frame of the login pages (brand panel + form); stacks on mobile. */
export default function AuthShell({ children }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexWrap: 'wrap', backgroundColor: tokens.background }}>
      <Box component="section" sx={{ flex: '1 1 420px', minHeight: { xs: 220, md: 320 }, boxSizing: 'border-box',
        backgroundColor: tokens.sidebar, color: tokens.sidebarTextStrong, p: { xs: 3, md: 6 }, display: 'flex',
        flexDirection: 'column', justifyContent: 'space-between', gap: 4 }}>
        <Box>
          <Typography sx={{ fontSize: 20, fontWeight: 600, letterSpacing: '-0.01em' }}>Estoque Cirúrgico</Typography>
          <Typography sx={{ fontSize: 13, color: tokens.sidebarText }}>{COMPANY_NAME}</Typography>
        </Box>
        <Box sx={{ maxWidth: 440, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          <Typography component="h2" sx={{ fontSize: { xs: 26, md: 34 }, lineHeight: 1.15, fontWeight: 600, letterSpacing: '-0.02em' }}>
            Do lote na sala ao implante na cirurgia.
          </Typography>
          <Typography sx={{ fontSize: 15, lineHeight: 1.55, color: tokens.sidebarText }}>
            Estoque por hospital, saída lida pelo código do item e reposição calculada a partir dos mínimos de cada local.
          </Typography>
        </Box>
        <Typography sx={{ fontSize: 12, color: tokens.sidebarMuted }}>Acesso restrito a usuários cadastrados.</Typography>
      </Box>
      <Box component="main" sx={{ flex: '1 1 480px', boxSizing: 'border-box', p: { xs: '32px 20px', md: '48px 24px' },
        display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Box sx={{ width: '100%', maxWidth: 380, display: 'flex', flexDirection: 'column', gap: 2.75 }}>{children}</Box>
      </Box>
    </Box>
  );
}
