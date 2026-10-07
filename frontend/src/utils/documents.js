/** Masks and validation for CPF and mobile phone (same rules as the API). */

export const digits = (value) => (value || '').replace(/\D/g, '');

export function maskCpf(value) {
  const d = digits(value).slice(0, 11);
  return d
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d{1,2})$/, '.$1-$2');
}

export function maskPhone(value) {
  const d = digits(value).slice(0, 11);
  if (d.length <= 2) return d ? `(${d}` : '';
  if (d.length <= 7) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

export function isValidCpf(value) {
  const d = digits(value);
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) return false;
  const check = (len) => {
    let sum = 0;
    for (let i = 0; i < len; i++) sum += Number(d[i]) * (len + 1 - i);
    const rest = (sum * 10) % 11;
    return rest === 10 ? 0 : rest;
  };
  return check(9) === Number(d[9]) && check(10) === Number(d[10]);
}

export const isValidMobile = (value) => /^[1-9][1-9]9\d{8}$/.test(digits(value));

export const isStrongPassword = (value) =>
  typeof value === 'string' &&
  value.length >= 8 &&
  value.length <= 20 &&
  /\d/.test(value) &&
  /[a-z]/.test(value) &&
  /[A-Z]/.test(value) &&
  /[^A-Za-z0-9]/.test(value);

export const PASSWORD_RULE = '8 a 20 caracteres, com número, letra minúscula, letra maiúscula e caractere especial.';

export const initials = (name) =>
  (name || '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('');
