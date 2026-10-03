import { Paper, Typography } from '@mui/material';
import PageHeader from '../components/PageHeader';

/** Screens of the next development phases. */
export default function ComingSoon({ title }) {
  return (
    <>
      <PageHeader title={title} />
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="body2" color="text.secondary">
          Esta tela será conectada na próxima fase do desenvolvimento.
        </Typography>
      </Paper>
    </>
  );
}
