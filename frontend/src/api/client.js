import { API_BASE } from '../config';
import { translateFields, translateMessage } from './messages';

/** Error raised for any non-2xx answer, already translated for display. */
export class ApiError extends Error {
  constructor(status, message, fields) {
    super(message);
    this.status = status;
    this.fields = fields || {};
  }
}

let onUnauthorized = () => {};

/** Called by the session provider: a 401 on an authenticated call ends the session in the screen. */
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

async function parseError(response) {
  let body = null;
  try {
    body = await response.json();
  } catch {
    // body without JSON
  }
  const message = translateMessage(body?.message || (response.status === 401 ? 'Not authenticated' : null));
  return new ApiError(response.status, message, translateFields(body?.fields));
}

/**
 * Calls the API under /api. body: object (sent as JSON) or FormData.
 * Returns the parsed JSON, a Blob (when blob=true) or null for empty answers.
 * Options: { method, body, query, blob, skipAuthRedirect }.
 */
export async function api(path, { method = 'GET', body, query, blob = false, skipAuthRedirect = false } = {}) {
  const url = new URL(API_BASE + path, window.location.origin);
  if (query) {
    Object.entries(query).forEach(([k, v]) => {
      if (v === undefined || v === null || v === '') return;
      if (Array.isArray(v)) v.forEach((item) => url.searchParams.append(k, item));
      else url.searchParams.set(k, v);
    });
  }
  const headers = { Accept: blob ? '*/*' : 'application/json' };
  let payload;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(url, { method, headers, body: payload, credentials: 'same-origin' });
  } catch {
    throw new ApiError(0, 'Não foi possível conectar ao servidor. Verifique a conexão e tente novamente.');
  }

  if (!response.ok) {
    const error = await parseError(response);
    if (response.status === 401 && !skipAuthRedirect) onUnauthorized(error);
    throw error;
  }
  if (blob) return response.blob();
  if (response.status === 204 || response.status === 202) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
