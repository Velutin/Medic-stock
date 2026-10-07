import { Box, Typography } from '@mui/material';

/** Page title with an optional subtitle and actions on the right. */
export default function PageHeader({ title, subtitle, actions }) {
  return (
    <Box component="header" sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-end', justifyContent: 'space-between',
      gap: 2, mb: 3 }}>
      <Box>
        <Typography variant="h1">{title}</Typography>
        {subtitle && <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{subtitle}</Typography>}
      </Box>
      {actions && <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>{actions}</Box>}
    </Box>
  );
}
