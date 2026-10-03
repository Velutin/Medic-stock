import { Box, Typography } from '@mui/material';
import ColorSwatch from '../../components/ColorSwatch';
import { tokens } from '../../theme';

/** Material name (short name and size when registered) with its REF and size color. */
export default function MaterialLabel({ material, line, dense }) {
  const m = material || line;
  if (!m) return null;
  const name = m.component ? `${m.component}${m.size ? ` · ${m.size}` : ''}` : m.description;
  return (
    <Box sx={{ minWidth: 0 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
        <ColorSwatch color={m.color} />
        <Typography sx={{ fontWeight: 600, fontSize: dense ? 14 : 15 }} noWrap={dense}>{name}</Typography>
      </Box>
      <Typography variant="body2" color="text.secondary">
        REF <span style={{ fontFamily: tokens.mono, fontSize: 13 }}>{m.ref}</span>
      </Typography>
    </Box>
  );
}
