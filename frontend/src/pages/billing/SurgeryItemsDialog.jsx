import { useEffect, useState } from 'react';
import {
  Button, Dialog, DialogActions, DialogContent, DialogTitle, Skeleton, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Typography,
} from '@mui/material';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import { formatDate, formatMoney } from '../../utils/format';
import { tokens } from '../../theme';

const mono = { fontFamily: tokens.mono, fontSize: 13 };

/** Items of a completed surgery with the hospital table values. */
export default function SurgeryItemsDialog({ surgeryId, onClose }) {
  const notify = useNotify();
  const [surgery, setSurgery] = useState(null);

  useEffect(() => {
    if (!surgeryId) return;
    setSurgery(null);
    api(`/surgeries/${surgeryId}`).then(setSurgery).catch((err) => { notify.error(err); onClose(); });
  }, [surgeryId]); // onClose and notify are stable for this dialog

  return (
    <Dialog open={Boolean(surgeryId)} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle sx={{ typography: 'h2' }}>
        {surgery ? `${surgery.patientName} · ${surgery.hospital} · ${formatDate(surgery.surgeryDate)}` : 'Itens da cirurgia'}
      </DialogTitle>
      <DialogContent dividers>
        {!surgery ? <Skeleton variant="rounded" height={160} /> : (
          <TableContainer>
            <Table size="small" sx={{ minWidth: 600 }}>
              <TableHead>
                <TableRow>
                  <TableCell>REF</TableCell><TableCell>Material</TableCell><TableCell>Lote</TableCell>
                  <TableCell align="right">Qtd.</TableCell><TableCell align="right">Valor unitário</TableCell><TableCell align="right">Valor</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {surgery.items.map((i) => (
                  <TableRow key={i.id}>
                    <TableCell sx={mono}>{i.ref}</TableCell>
                    <TableCell>{i.component ? `${i.component}${i.size ? ` · ${i.size}` : ''}` : i.description}</TableCell>
                    <TableCell sx={mono}>{i.lot}</TableCell>
                    <TableCell align="right">{i.quantity}</TableCell>
                    <TableCell align="right">{i.unitValue == null ? <Typography variant="caption" color="warning.main">Sem valor</Typography> : formatMoney(i.unitValue)}</TableCell>
                    <TableCell align="right">{i.unitValue == null ? '—' : formatMoney(i.unitValue * i.quantity)}</TableCell>
                  </TableRow>
                ))}
                <TableRow>
                  <TableCell colSpan={5} sx={{ fontWeight: 700 }}>Total</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>{formatMoney(surgery.totalValue || 0)}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}><Button variant="outlined" onClick={onClose}>Fechar</Button></DialogActions>
    </Dialog>
  );
}
