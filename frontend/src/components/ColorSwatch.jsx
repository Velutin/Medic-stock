import { Box } from '@mui/material';
import { tokens } from '../theme';

/** Size identification color of a material. */
export default function ColorSwatch({ color, size = 14 }) {
  if (!color) return null;
  return (
    <Box component="span" aria-hidden sx={{ display: 'inline-block', width: size, height: size, borderRadius: '50%',
      backgroundColor: color, border: `1px solid ${tokens.border}`, verticalAlign: 'middle', flexShrink: 0 }} />
  );
}
