import { MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';

/** Type, source and destination (or return reason): the same fields on the computer and on the phone. */
export default function LoanForm({ loan }) {
  const isReturn = loan.kind === 'RETURN';
  return (
    <Stack spacing={2}>
      <ToggleButtonGroup exclusive fullWidth color="primary" value={loan.kind} aria-label="Tipo"
        onChange={(_, v) => v && loan.changeKind(v)}>
        <ToggleButton value="LOAN">Empréstimo</ToggleButton>
        <ToggleButton value="RETURN">Devolução à Baumer</ToggleButton>
      </ToggleButtonGroup>
      <TextField select label="Hospital de origem" value={loan.sourceId} disabled={loan.loadingHospitals}
        onChange={(e) => loan.changeSource(Number(e.target.value))}>
        {loan.sources.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
      </TextField>
      <Stack spacing={0.75}>
        <Typography variant="body2" sx={{ fontWeight: 500 }}>Sair de</Typography>
        <ToggleButtonGroup exclusive fullWidth size="small" value={loan.location} aria-label="Sair de"
          onChange={(_, v) => v && loan.changeLocation(v)}>
          <ToggleButton value="HOSPITAL" disabled={loan.isCenter}>Estoque no hospital</ToggleButton>
          <ToggleButton value="STOREROOM">Sala</ToggleButton>
        </ToggleButtonGroup>
      </Stack>
      {isReturn ? (
        <TextField label="Motivo da devolução" required value={loan.reason} onChange={(e) => loan.setReason(e.target.value)}
          multiline minRows={2} inputProps={{ maxLength: 1000 }} placeholder="Ex.: lote vencido, material avariado" />
      ) : (
        <>
          <TextField select label="Hospital de destino" value={loan.destinationId}
            onChange={(e) => loan.setDestinationId(Number(e.target.value))}>
            {loan.destinations.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
          </TextField>
          <TextField label="Observação" value={loan.notes} onChange={(e) => loan.setNotes(e.target.value)}
            multiline minRows={2} inputProps={{ maxLength: 1000 }} />
        </>
      )}
      <Typography variant="caption" color="text.secondary">
        {isReturn
          ? 'O material volta para a Baumer e sai de todos os estoques. Lotes vencidos podem ser devolvidos.'
          : 'O material sai da origem e entra no estoque do hospital de destino.'}
      </Typography>
    </Stack>
  );
}
