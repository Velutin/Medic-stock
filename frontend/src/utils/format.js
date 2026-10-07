/** Display helpers (pt-BR). */

export const PRODUCT_LINES = [
  { value: 'HIP', label: 'Quadril' },
  { value: 'KNEE', label: 'Joelho' },
  { value: 'SHOULDER', label: 'Ombro' },
];
export const lineLabel = (value) => PRODUCT_LINES.find((l) => l.value === value)?.label || value;

export const PRICE_TABLES = [
  { value: 'SIGTAP', label: 'SIGTAP' },
  { value: 'TENDER', label: 'Licitação' },
];
export const priceTableLabel = (value) => PRICE_TABLES.find((p) => p.value === value)?.label || 'Não definida';

export const hospitalTypeLabel = (type) => (type === 'DISTRIBUTION_CENTER' ? 'Centro de distribuição' : 'Hospital');

/** "2027-01-31" -> "31/01/2027" (no time zone shift). */
export function formatDate(value) {
  if (!value) return '';
  const [y, m, d] = String(value).slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
}

export function formatDateTime(value) {
  if (!value) return '';
  const date = new Date(value);
  return date.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export const formatMoney = (value) =>
  value === null || value === undefined ? '' : Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

/** Days until the expiry date (negative when expired). */
export function daysUntil(dateValue) {
  if (!dateValue) return null;
  const [y, m, d] = String(dateValue).slice(0, 10).split('-').map(Number);
  const target = Date.UTC(y, m - 1, d);
  const now = new Date();
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((target - today) / 86400000);
}
