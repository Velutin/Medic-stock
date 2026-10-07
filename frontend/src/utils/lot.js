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
