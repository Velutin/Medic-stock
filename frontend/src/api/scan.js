import { api } from './client';

/**
 * What a scanned or typed code identifies: { code, gtin, ref, lot, expiryDate, material }.
 * The lot does not need to be registered (entries usually bring new lots).
 */
export const readCode = (code) => api('/materials/scan', { query: { code } });
