import { api } from './client';

/** Downloads a file returned by the API (PDF, spreadsheet) with the given name. */
export async function downloadFile(path, filename, query) {
  const blob = await api(path, { blob: true, query });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Sends a spreadsheet to an import endpoint. */
export function uploadSpreadsheet(path, file, query) {
  const form = new FormData();
  form.append('file', file);
  return api(path, { method: 'POST', body: form, query });
}
