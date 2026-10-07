import { Box } from '@mui/material';
import { tokens } from '../theme';

const TONES = {
  success: [tokens.success, tokens.successBg],
  warning: [tokens.warning, tokens.warningBg],
  error: [tokens.error, tokens.errorBg],
  info: [tokens.info, tokens.infoBg],
  neutral: [tokens.textMuted, tokens.divider],
};

/** Small status label with the prototype colors. */
export default function StatusChip({ tone = 'neutral', children }) {
  const [color, bg] = TONES[tone] || TONES.neutral;
  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', px: 1, py: 0.25, borderRadius: '999px',
      fontSize: 12, fontWeight: 500, color, backgroundColor: bg, whiteSpace: 'nowrap' }}>
      {children}
    </Box>
  );
}
