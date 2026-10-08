# Frontend — Estoque Cirúrgico (MSS)

React 18 + Vite 5 + MUI 6. The visual follows the approved screen prototypes (IBM Plex Sans, teal palette;
tokens in `src/theme.js`). The business rules live in the backend — see the README at the root of the
repository.

## Running

```bash
npm install
npm run dev
```

Open <https://localhost:5173>. The `mss` service must be running (default `http://127.0.0.1:8085`).
In development every API call goes through `/api` (Vite proxy), so the session cookie works without CORS.
Copy `.env.example` to `.env` to change the backend URL (`VITE_API_TARGET`) or the company name
(`VITE_COMPANY_NAME`).

**The development server runs over HTTPS on purpose** (`@vitejs/plugin-basic-ssl`): the browser only gives a
web page access to the camera over a secure origin, and the code reader is used from a phone. The certificate
is self-signed, so Chrome marks the page as "not secure" — expected, and it does not block the camera. To read
codes from a phone on the same network, open `https://<the computer's IP>:5173` and accept the warning
(`server.host` is already enabled).

`npm run build` produces the static files in `dist`. The `npm audit` warnings are all in development
dependencies (`vite`/`esbuild`, `nanoid` via `postcss`) and do not reach the server, which receives only the
built files.

## Structure

- `src/api` — HTTP client (`client.js`), Portuguese translation of API messages (`messages.js`), file
  downloads (`download.js`) and code reading through the API (`scan.js`).
- `src/auth` — session (login, logout, current user), route protection by profile and the profile helpers.
- `src/notifications` — success/error alerts (snackbar).
- `src/layout` — retractable side menu (drawer on mobile), user menu, change-password dialog, and `menu.js`
  with the routes and the profiles that see each one.
- `src/components` — shared pieces, among them `BarcodeScanner.jsx` (the camera) and `CodeReader.jsx`.
- `src/pages` — one folder per screen: `dashboard`, `stock`, `entry`, `transfer`, `surgery`, `replenishment`,
  `loan`, `billing`, `reports`, `registry`, `users`, plus the login and password pages.
- `src/utils` — date and label formatting (`format.js`), CPF and phone masks (`documents.js`), lot number
  comparison (`lot.js`) and the WASM decoder setup (`zxingReader.js`).

Each screen that can be used in the operating room or in the storeroom has a **computer layout and a phone
layout** (`*Desktop.jsx` / `*Mobile.jsx`), sharing the state through a `use*Draft.js` hook.

In `pages/stock`, one component serves two of the three tabs: `LotView` takes a `location` prop
(`HOSPITAL`, the default, or `STOREROOM`), so a fix in it reaches both tabs. Its filters — the chosen hospital
and the search — live in `StockPage`, not inside the view, because the export button of the page header has to
send the same filters the screen is using; that is what keeps the file and the screen from disagreeing.
`AdjustDialog` takes the same `location`, which decides whether the counted quantity is the hospital's or the
storeroom's.

## Reading codes

`src/components/BarcodeScanner.jsx` is the **only** place that opens the camera: it serves the entry, the
transfer, the surgery withdrawal, the loan and the return. The consumption sheet is read from its PDF pages by
`src/pages/surgery/sheetReader.js`. Both share the WASM setup in `src/utils/zxingReader.js`.

- The 2D code on an implant label is a **Data Matrix**, decoded with `zxing-wasm` from a crop of the aiming
  square (70% of the shorter side of the frame). The JavaScript decoder (`@zxing/library`) is much weaker on
  Data Matrix, which is why the WASM build is used.
- **Camera switch button** when the device has more than one rear camera, with the choice kept per device in
  `localStorage` (`mss.camera`). The rear camera Chrome opens by default on some phones (the Galaxy S24 among
  them) is the **ultra-wide**, with fixed focus and a field of view about three times wider — the code then
  occupies a third of the pixels and no software setting compensates for it. The field of view is not exposed
  in the camera capabilities, and choosing by minimum focus distance, by maximum resolution or by label does
  not identify the main lens, so the choice is the user's, once per device.
- On the phone entry, while a scanned item waits for its expiry date the camera is **paused**, with a caption
  on screen: otherwise any code read during typing replaced the item being filled in.

If reading is still poor **with the right lens**, the knobs are `INTERVAL_MS`, the `AIM` crop fraction and the
requested resolution.

## Conventions

- Code and comments in English; everything the user reads is in Portuguese.
- **No API error may reach the user in English.** Every new message from the API needs its translation in
  `src/api/messages.js` — `EXACT` for a fixed sentence, `PATTERNS` for one with values in it. An unknown
  message falls back to a generic Portuguese sentence.
- Dependencies are pinned. `@zxing/browser` and `@zxing/library` are kept even though the camera now uses
  `zxing-wasm`.
- `client.js` treats a successful answer that is not JSON as `null`: endpoints with nothing to return answer
  204, and a plain-text body used to break the screen after the operation had already been recorded.
