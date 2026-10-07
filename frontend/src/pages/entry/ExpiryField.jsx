import { useEffect, useState } from 'react';
import { TextField } from '@mui/material';

/** Keeps only digits and inserts the slashes: up to 6 digits -> MM/AAAA; 7 or 8 -> DD/MM/AAAA. */
function mask(raw) {
  const d = raw.replace(/\D/g, '').slice(0, 8);
  if (d.length <= 2) return d;
  if (d.length <= 6) return `${d.slice(0, 2)}/${d.slice(2)}`;
  return `${d.slice(0, 2)}/${d.slice(2, 4)}/${d.slice(4)}`;
}

/**
 * Typed text -> ISO date. MM/AAAA becomes the first day of the month (e.g. 01/2030 -> 2030-01-01);
 * DD/MM/AAAA is used as it is. Returns '' when empty and 'invalid' when incomplete or impossible.
 */
export function parseExpiry(text) {
  const d = text.replace(/\D/g, '');
  if (!d) return '';
  let day = 1;
  let month;
  let year;
  if (d.length === 6) {
    month = Number(d.slice(0, 2));
    year = Number(d.slice(2));
  } else if (d.length === 8) {
    day = Number(d.slice(0, 2));
    month = Number(d.slice(2, 4));
    year = Number(d.slice(4));
  } else {
    return 'invalid';
  }
  const date = new Date(year, month - 1, day);
  if (year < 2000 || year > 2099 || date.getMonth() !== month - 1 || date.getDate() !== day) return 'invalid';
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

/** ISO date -> text shown: MM/AAAA when it is the first day of the month, DD/MM/AAAA otherwise. */
function formatExpiry(iso) {
  const [y, m, d] = iso.split('-');
  return d === '01' ? `${m}/${y}` : `${d}/${m}/${y}`;
}

/**
 * Expiry date typed on the numeric keyboard: 012030 -> 01/2030 (first day of the month), or 8 digits for a full date.
 * value/onChange use the ISO date ('' when empty, 'invalid' while incomplete); a date read from a QR code
 * shows up here already formatted. onChange also receives { monthOnly: true } when the date was typed as MM/AAAA.
 */
export default function ExpiryField({ value, onChange, label = 'Validade (mês/ano)', ...props }) {
  const [text, setText] = useState(() => (value && value !== 'invalid' ? formatExpiry(value) : ''));

  // Follows changes made outside (date read from a QR code, item cleared or loaded for correction)
  useEffect(() => {
    if (value === 'invalid') return;
    if (!value) {
      setText((t) => (parseExpiry(t) === '' ? t : ''));
      return;
    }
    setText((t) => (parseExpiry(t) === value ? t : formatExpiry(value)));
  }, [value]);

  const change = (e) => {
    const next = mask(e.target.value);
    setText(next);
    onChange(parseExpiry(next), { monthOnly: next.replace(/\D/g, '').length === 6 });
  };

  return (
    <TextField label={label} value={text} onChange={change} placeholder="MM/AAAA" autoComplete="off"
      inputProps={{ inputMode: 'numeric', pattern: '[0-9/]*', maxLength: 10 }} {...props} />
  );
}
