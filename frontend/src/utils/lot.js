/**
 * Lot numbers are compared ignoring case, spaces and leading zeros: the labels print the lot with 9 digits
 * (e.g. 005706061) and the same lot may be registered without the zeros (5706061). Same rule as the API.
 */
export function lotKey(value) {
  const upper = (value || '').trim().toUpperCase();
  const stripped = upper.replace(/^0+/, '');
  return upper && !stripped ? '0' : stripped;
}

/** True when both texts are the same lot number. */
export const sameLot = (a, b) => lotKey(a) === lotKey(b);

/** True when the lot number contains the search (both without leading zeros). */
export const lotContains = (lot, search) => lotKey(lot).includes(lotKey(search));

/**
 * Narrows lot rows by the expiry date read from the code, with the same rule as the API
 * (LotScanService.choose): the exact date wins and, failing it, the dates of the same month.
 *
 * A lot whose expiry was typed as MM/AAAA is registered on day 1 of the month, so the full date printed on
 * the label never equals it. Comparing the dates exactly refused the reading with the material sitting in
 * the hospital. Another month stays refused: those units come from a different sterilization batch and the
 * expiry on the consumption sheet would not be the one on the label in the patient's record.
 */
export function matchExpiry(rows, expiryDate) {
  if (!expiryDate) return rows;
  const exact = rows.filter((r) => r.expiryDate === expiryDate);
  if (exact.length > 0) return exact;
  const month = String(expiryDate).slice(0, 7);
  return rows.filter((r) => String(r.expiryDate || '').slice(0, 7) === month);
}
