# Frontend — Estoque Cirúrgico

React 18 + Vite + MUI. The visual follows the approved screen prototypes (IBM Plex Sans, teal palette).

## Running

```bash
npm install
npm run dev
```

Open http://localhost:5173. The `mss` service must be running (default `http://127.0.0.1:8085`).
In development every API call goes through `/api` (Vite proxy), so the session cookie works without CORS.
Copy `.env.example` to `.env` to change the backend URL or the company name.

## Structure

- `src/api` — HTTP client, error handling and Portuguese translation of API messages.
- `src/auth` — session (login, logout, current user) and route protection by profile.
- `src/notifications` — success/error alerts (snackbar).
- `src/layout` — retractable side menu (drawer on mobile), user menu and change-password dialog.
- `src/pages` — screens.
- `src/utils` — CPF/phone masks and validation.
