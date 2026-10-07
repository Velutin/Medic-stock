import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, List, ListItem, Typography } from '@mui/material';
import { translateMessage } from '../api/messages';

/** Result of a spreadsheet import: rows read, imported, ignored and the errors by row. */
export default function ImportResultDialog({ result, onClose }) {
  if (!result) return null;
  const errors = (result.errors || []).map(translateMessage);
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ typography: 'h2' }}>Resultado da importação</DialogTitle>
      <DialogContent dividers>
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 1.5, mb: 2 }}>
          {[['Linhas lidas', result.rowsRead], ['Importadas', result.imported], ['Ignoradas', result.ignored]].map(([label, value]) => (
            <Box key={label} sx={{ border: 1, borderColor: 'divider', borderRadius: '6px', p: 1.5 }}>
              <Typography variant="caption" color="text.secondary">{label}</Typography>
              <Typography sx={{ fontSize: 22, fontWeight: 600 }}>{value}</Typography>
            </Box>
          ))}
        </Box>
        {errors.length === 0 ? (
          <Alert severity="success">Planilha importada sem erros.</Alert>
        ) : (
          <>
            <Alert severity="warning" sx={{ mb: 1 }}>Algumas linhas não foram importadas. Corrija e importe novamente.</Alert>
            <List dense sx={{ maxHeight: 280, overflow: 'auto' }}>
              {errors.map((e, i) => <ListItem key={i} sx={{ fontSize: 13 }}>{e}</ListItem>)}
            </List>
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}><Button variant="contained" onClick={onClose}>Fechar</Button></DialogActions>
    </Dialog>
  );
}
