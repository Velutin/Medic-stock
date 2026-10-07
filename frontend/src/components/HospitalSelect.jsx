import { MenuItem, TextField } from '@mui/material';

/** Hospital picker. allLabel: adds an "every hospital" option (value ''). */
export default function HospitalSelect({ hospitals, value, onChange, allLabel, label = 'Hospital', sx, ...props }) {
  return (
    <TextField select label={label} value={value ?? ''} onChange={(e) => onChange(e.target.value === '' ? '' : Number(e.target.value))}
      sx={{ minWidth: 220, ...sx }} {...props}>
      {allLabel && <MenuItem value="">{allLabel}</MenuItem>}
      {hospitals.map((h) => <MenuItem key={h.id} value={h.id}>{h.name}</MenuItem>)}
    </TextField>
  );
}
