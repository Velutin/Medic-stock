# MSS — Medic Stuff Stock

Surgical implant inventory control: stock entries, storeroom-to-hospital transfers, surgery withdrawals,
loans and supplier returns, minimum-based replenishment, supplier orders, billing and PDF reports.

The system follows the physical material: it arrives from the supplier into a **storeroom**, is transferred
**into a hospital**, and leaves when it is **withdrawn in a surgery** — always identified by lot, so an
implant can be traced from the invoice to the patient.

## Contents

- [Architecture](#architecture)
- [Running locally](#running-locally)
- [Production configuration](#production-configuration)
- [Users and sessions](#users-and-sessions)
- [Profiles and visibility](#profiles-and-visibility)
- [Screens](#screens)
- [Data model](#data-model)
- [Code reading (scanner and sheets)](#code-reading-scanner-and-sheets)
- [Business rules](#business-rules)
- [Reports, billing and dashboard](#reports-billing-and-dashboard)
- [Data migration (imports)](#data-migration-imports)
- [Conventions](#conventions)
- [Troubleshooting](#troubleshooting)

## Architecture

| Service       | Port | Purpose                                                   |
|---------------|------|-----------------------------------------------------------|
| eureka-server | 8082 | Service registry                                          |
| gateway       | 8090 | Single API entry point; routes by service id (`/mss/**`, `/mail/**`) |
| mss           | 8085 | Inventory business rules (PostgreSQL `mssdb`, port 5440)  |
| mail          | 8081 | Email delivery through RabbitMQ (PostgreSQL `mssemaildb`) |

- **Backend**: Spring Boot (REST), JPA/Hibernate, Flyway, OpenPDF (PDF), Apache POI (spreadsheets),
  RabbitMQ (email queue `default.email`, declared by the mail service).
- **Frontend**: React 18 + MUI 6 + Vite 5, `react-router-dom`, `zxing-wasm` (code reading) and
  `pdfjs-dist` (consumption sheet pages).
- The database schema is created exclusively by **Flyway**
  (`backend/mss/src/main/resources/db/migration`, V1 to V12). Hibernate never changes tables
  (`ddl-auto=none`).

### Migrations

| Migration | What it adds |
|---|---|
| V1, V2 | Security tables; default roles and the initial administrator |
| V3, V4 | Inventory tables; the served hospitals |
| V5 | Distribution centers, surgery cancellation, lot expiry date |
| V6 | Login by e-mail, product lines per material |
| V7 | Surgery completion and billing rates |
| V8 | Stock entries grouped per receipt |
| V9 | Catalog sections (`product_section`) |
| V10 | Returns to the supplier |
| V11 | Loan without notification status |
| V12 | Lot correction (`LOT_CORRECTION` movement type) |

## Running locally

```bash
docker compose up -d          # PostgreSQL (mss and mail) + RabbitMQ
cd backend/eureka-server && ./mvnw spring-boot:run
cd backend/mss && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

On Windows PowerShell use `.\mvnw.cmd` and run each command on its own line.

- Frontend: <https://localhost:5173> — the Vite proxy sends `/api` to `http://127.0.0.1:8085`, so the
  session cookie works without CORS.
- Swagger: <http://localhost:8085/swagger-ui.html>
- Initial user: `admin@mss.local` / `admin123` — change the password on the first login and fill in CPF and
  mobile phone.

**HTTPS in development is on purpose**: the browser only gives a web page access to the camera over a secure
origin, and the scanner is used from a phone. The certificate is self-signed, so Chrome marks the page as
"not secure" — that is expected and does not block the camera. To read codes from a phone on the same
network, open `https://<the computer's IP>:5173` and accept the warning.

## Production configuration

Environment variables for `mss`:

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL `mssdb` |
| `JWT_SECRET` | **Required**; long and random |
| `STORAGE_DIR` | Consumption sheets and uploaded files |
| `LOGO_PATH`, `COMPANY_NAME` | Report header |
| `FRONTEND_URL` | Base of the links sent by e-mail; must be **https** |
| `RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | Email queue |
| `EUREKA_URL` | Service registry |

Report logo: `backend/mss/branding/logo.png`. To change it, replace the file keeping the same name — no code
change and no rebuild. Without the file, the company name is printed instead.

Frontend: `npm run build` produces static files in `frontend/dist`, served by any web server. `VITE_API_TARGET`
and `VITE_COMPANY_NAME` come from `.env` (see `.env.example`).

The `npm audit` warnings of the frontend are all in **development** dependencies (`vite`/`esbuild`,
`nanoid` via `postcss`): they do not reach the server, which receives only the built static files.

## Users and sessions

- Login by e-mail (`POST /auth/session`). The name is not unique; e-mail and CPF are.
- CPF (valid and unique) and mobile phone (DDD + 9 digits) are required for every new or edited user.
- New users have no password: they receive an e-mail link (valid for 48 hours, single use) to create it.
  Administrators can resend it (`POST /users/{id}/invitation`).
- Sessions: 30 minutes without use (renewed on every action, ended when the browser closes); with
  "keep me signed in", 7 days counted from the login. Changing the password ends the sessions on other
  devices; deactivating a user blocks access immediately.
- After login the user goes to the page their profile starts on (dashboard for administrators, stock for the
  others). The page the user was trying to reach is only honoured when it is an **internal path**
  (`safePath` in `LoginPage.jsx`), so a crafted link cannot turn the login into a redirect to another site.
- API errors always use the format `{ "status", "message", "fields" }`.

## Profiles and visibility

| Profile | What it sees |
|---|---|
| MASTER, ADMIN | Everything |
| SURGICAL_TECH | Surgeries of their hospitals and the stock summary without lots (`GET /stock/summary`); never prices or surgery values |
| USER | Read-only, lot-level stock of their hospitals |

- Surgical techs only see the hospitals they work at (`user_hospital`); ADMIN/MASTER see all of them.
- Surgery items recorded while the REF had no value receive it automatically when the value is registered;
  values already recorded never change (audit).
- Administrators can add or remove items of completed surgeries; cancelled surgeries cannot be changed.

## Screens

| Screen | Path | Profiles |
|---|---|---|
| Painel | `/painel` | ADMIN, MASTER |
| Estoque (by lot and by material) | `/estoque` | all |
| Entrada | `/entrada` | ADMIN, MASTER |
| Transferência | `/transferencia` | ADMIN, MASTER |
| Saída em cirurgia | `/saida` | ADMIN, MASTER, SURGICAL_TECH |
| Reposição | `/reposicao` | ADMIN, MASTER |
| Empréstimos e devoluções | `/emprestimos` | ADMIN, MASTER |
| Faturamento | `/faturamento` | ADMIN, MASTER |
| Relatórios | `/relatorios` | ADMIN, MASTER |
| Cadastros | `/cadastros` | ADMIN, MASTER |
| Usuários | `/usuarios` | ADMIN, MASTER |

Entrada, Transferência, Saída em cirurgia and Empréstimos each have a **computer layout and a phone layout**:
on the phone the camera is the main input and every reading adds one unit.

Searching for a material — in the catalog, hospital values, stock by lot, stock by material, minimum levels,
loans and transfers — matches **REF, component (name) and description**; the catalog and the stock by lot also
match the GTIN.

## Data model

- `hospital` (+ `hospital_product_line`): served hospitals, price table type (SIGTAP/TENDER) and product
  lines (HIP, KNEE, SHOULDER).
- `product_section`: catalog sections registered by the user (e.g. "Quadril não cimentada", "Bipolar"), with
  display order; they group the hospital stock by material.
- `material`: catalog by REF, with GTIN (GS1 AI 01, identifies the REF when scanning), section, component,
  size and identification color.
- `hospital_price`: REF value at each hospital; used on surgery withdrawals.
- `lot`: number and mandatory expiry date (GS1 AI 10 and 17). A lot is identified by **material + number +
  expiry date**: units of the same lot sterilized on different days have different expiry dates and are
  separate records, each with its own balance.
- `stock`: balance per lot + hospital + location (`STOREROOM` = in the storeroom, assigned to the hospital;
  `HOSPITAL` = inside the hospital).
- `minimum_stock`: hospital "Ideal" and "Ideal total" (hospital + storeroom) per REF.
- `surgery`, `surgery_item`, `pending_issue`: material withdrawals and scans that need review.
- `stock_entry`, `stock_entry_item`: material received from the supplier, grouped per receipt so it can be
  corrected as a whole.
- `loan`, `delivery`, `supplier_order`: loans and returns, storeroom-to-hospital deliveries (transfers) and
  supplier orders.
- `stock_movement`: ledger of every balance change — `ENTRY`, `ENTRY_CORRECTION`, `INVENTORY_ADJUSTMENT`,
  `REPLENISHMENT`, `SURGERY_WITHDRAWAL`, `SURGERY_REVERSAL`, `LOAN`, `SUPPLIER_RETURN`, `LOT_CORRECTION`.
  Movements created by an entry point to it (`stock_entry_id`).
- `user_hospital`: hospitals each surgical tech works at.

### Lot numbers

- Compared **without leading zeros and ignoring case** (`Lot.comparableNumber`, `LotRepository.SAME_NUMBER`,
  `frontend/src/utils/lot.js`), because the same lot is printed in different widths on label and invoice.
- **A lot number belongs to a single REF.** The same REF may have that number with several expiry dates.

## Code reading (scanner and sheets)

`frontend/src/components/BarcodeScanner.jsx` is the **only** place in the system that opens the camera: it
serves the entry, the transfer, the surgery withdrawal, the loan and the return.

- The 2D code on an implant label is a **Data Matrix**, decoded with `zxing-wasm` (`tryHarder`, `tryRotate`,
  `tryDownscale`), from a **crop of the aiming square** (70% of the shorter side of the frame). Cropping
  replaces zoom with an advantage: digital zoom is a crop with interpolation.
- The camera is requested at 1920×1080 ideal, with continuous focus when the device exposes the capability;
  tapping the image focuses on the center of the aim.
- **Camera switch button** when the device has more than one rear camera, with the choice kept per device in
  `localStorage` (`mss.camera`). It exists because the rear camera Chrome opens by default on some phones
  (the Galaxy S24 among them) is the **ultra-wide**: fixed focus and a field of view about three times
  wider, so the code occupies a third of the pixels and no software setting compensates for it. The field of
  view is not exposed in the camera capabilities, and choosing by minimum focus distance, by maximum
  resolution or by label does not identify the main lens — which is why the choice is the user's, once per
  device.
- On the phone entry, while a scanned item is waiting for its expiry date the camera is **paused**, with a
  caption on screen. Without it, any code read during typing replaced the item being filled in.
- The consumption sheet is read from its PDF pages with `pdfjs-dist` + the same WASM decoder
  (`utils/zxingReader.js`, shared with `pages/surgery/sheetReader.js`).

Scanning rules:

- The GTIN (AI 01) identifies the REF and the lot number (AI 10) identifies the lot; AI 17 carries the expiry
  date (day `00` means the last day of the month). A code with no lot number is rejected.
- `GET /materials/scan?code=` reads a QR code, barcode, labeled text (`REF: ... LOTE: ...`) or a typed REF
  **without requiring the lot to exist** (used by entries, transfers and deliveries).
- An unknown GTIN is **learned** the first time a lot is matched unambiguously **and the REF was confirmed on
  its own** — typed by the user or read from the label's REF text. Matching a single lot by its number alone
  is not proof that the label belongs to that REF: without this restriction a GTIN could be written onto the
  wrong material permanently and silently, sending every later scan of that code to the wrong REF. Outside
  the withdrawal, the catalog spreadsheet import also fills the GTIN, where it is declared next to the REF.

## Business rules

### Stock entry (`/stock-entries`, ADMIN/MASTER)

- Material received from the supplier always enters the **storeroom**, assigned to the destination; it reaches
  the hospital through a transfer.
- Destinations: active regular hospitals and distribution centers (SESAB). Hospitals supplied by a
  distribution center are refused: their material enters through the center.
- The receipt date is the entry date and the date of its `ENTRY` movements; it cannot be in the future.
- Expired lots are refused. The same lot (material + number + expiry date) repeated is summed. A new REF can
  be registered on the spot.
- `PUT /stock-entries/{id}` corrects the whole entry (destination, date, REF, lot, expiry date, quantity) and
  applies only the differences as `ENTRY_CORRECTION`; it is refused when the material to remove already left
  the storeroom.
- **One REF per lot number.** When a number already registered with another REF enters, the screen lists those
  lots (`POST /stock-entries/lot-conflicts`) and, after confirmation (`changeRef` on the item), they become
  the new REF with their balance and history. A lot already withdrawn in a surgery **cannot** change REF —
  and in that case the dialog lists those surgeries (date, hospital, patient, status, quantity) with a link
  to each attached consumption sheet, so the administrator can see what happened before deciding. Cancelled
  surgeries are left out of the list, for the same reason they do not block the change. Applies to the entry
  only (computer, phone and entry spreadsheet).
- `POST /stock-entries/preview` reads an entry spreadsheet (REF, LOTE, VALIDADE, QUANTIDADE, optional
  DESCRIÇÃO and GTIN) for review, without saving.
- Scanning on the entry screen: a GS1 QR code fills REF, lot and expiry date. A GTIN barcode identifies only
  the REF; the next reading is taken as the lot barcode (the same GTIN read again is refused), and the expiry
  date is typed as MM/AAAA, stored as the first day of the month (DD/MM/AAAA is also accepted).
- `POST /stock/entry` (single item, without grouping) still exists for compatibility.

### Transfer (storeroom → hospital, `POST /deliveries`)

- Destinations: active regular hospitals. For a hospital supplied by a distribution center, the lots come
  from the center's storeroom. Expired lots are not listed and are refused.
- The quantities can be filled with the replenishment suggestion. On the phone, each reading adds one unit and
  a lot that is not in the storeroom shows an error.
- Each transfer generates the delivery report PDF (`GET /deliveries/{id}/pdf`).

### Stock by lot (`GET /stock/lots`)

- Without `hospitalId`, searches every hospital the user can see, which locates a lot anywhere; `term` matches
  lot number, REF, name, description or GTIN. Sorted by REF, expiry date and hospital.
- **Editing an item** (`PUT /materials/lots/{lotId}`, ADMIN): the pencil edits the lot number, the expiry date
  and the counted balance of the line, all in one call and one transaction. It exists for the lot typed wrong
  at the entry, which had no other way back.
  - The lot is a single record, so correcting the number or the date corrects it **in every hospital and in
    the storeroom** — it is the same physical lot. The screen warns before saving.
  - If the corrected identity already exists in the same REF, the two lots are the same thing and are
    **merged**: balances summed and history moved (entries, movements, loans, deliveries, surgery items).
  - Unlike the REF change, a lot **already withdrawn in a surgery can still be corrected** — otherwise a
    typing error would be permanent. The history of those surgeries then shows the corrected number, which is
    the real one.
  - If the corrected number belongs to **another REF**, the correction is refused: a lot number belongs to a
    single REF, and changing REF has its own flow at the entry.
  - Each correction records a `LOT_CORRECTION` movement: quantity 0, no hospital, and the notes describe what
    the lot was, what it became and the reason given. Nothing moved — what changed is the identity of the lot.
    Changing the quantity still records `INVENTORY_ADJUSTMENT`.

### Stock by material (`GET /stock/summary`)

- Grouped by section, then by item name (component) and size; 10 sizes per page on screen.
- Lists the REFs with an **ideal** (hospital ideal or ideal total) greater than zero in the hospital, showing 0
  when there is no balance, plus any REF with **balance inside the hospital** even without an ideal. Expired
  lots are ignored, and the storeroom is excluded.
- Only the current state counts: history does not keep an item on the screen. So replacing a product line at a
  hospital (zeroing the ideal and clearing the balance — returning the remainder to the storeroom, lending it
  to another hospital or returning it to the supplier) removes the item from the screen the same day.
  Deactivating the item in the catalog also removes it, but that is **global** and does not serve per-hospital
  control: that goes through the list of ideals of each hospital.
- Sections are managed in `/product-sections`; a section with items cannot be removed, only deactivated.

### Surgery withdrawal (`/surgeries`)

- Withdrawals debit the stock **inside** the hospital and use that hospital's price table.
- Unknown, ambiguous, expired or out-of-balance lots become **pending issues** (`LOT_NOT_FOUND`,
  `AMBIGUOUS_LOT`, `EXPIRED_LOT`, `NO_HOSPITAL_BALANCE`) without touching balances. Resolving one
  (`PATCH /pending-issues/{id}`) requires a lot and records it in the surgery;
  `GET /pending-issues/{id}/suggestions` offers the valid lots of that material inside the hospital first,
  then a search by lot, REF or material.
- The consumption sheet is attached as a PDF or as photos converted to PDF. Each label on the sheet counts as
  **1 item**; `POST /surgeries/{id}/sheet/preview` checks every label before recording anything.
- `COMPLETED` requires the consumption sheet attached and no open pending issues. `CANCELLED` requires a
  reason and freezes the surgery.

### Loans and supplier returns (`/loans`)

- A loan (`LOAN`) can come from the hospital or the storeroom and credits the destination hospital. It is
  printed as a delivery to the destination hospital ("Entrega de materiais", number `E-{id}`). There is no
  notification status: the supplier is informed when the loan is made.
- A return to the supplier (`RETURN`) takes the material out of every stock: reason required, expired lots
  accepted, its own PDF with the items sent to the company.

### Replenishment and supplier orders

- Below **Ideal** → replenish from the storeroom; below **Ideal total** (hospital + storeroom) → order from the
  supplier. Expired lots are ignored. `onlyWithShortage=false` also lists materials at their ideal level.
- `GET /hospitals/{id}/stock-levels` lists the minimum levels and valid balances of every REF the hospital may
  work with (catalog items of its product lines, REFs with minimum levels and REFs with balance).
- `/supplier-orders` generates the reviewed order and its PDF.

### Distribution center (SESAB)

A distribution center keeps material only in the **storeroom** and supplies several hospitals. The entry goes
to the center's storeroom and distributing to a hospital is a transfer. A hospital can belong to only one
center (`PUT /hospitals/{id}/covered-hospitals`).

## Reports, billing and dashboard

Each report returns the data for the on-screen preview and has a `/pdf` variant with the same filters
(`start`, `end` inclusive dates, optional `hospitalId`):

- `weekly-closing?date=`: week from **Saturday to Friday** containing the date; open and completed surgeries by
  surgery date per hospital, items consumed and the surgical techs assigned to each hospital.
- `cancellations`: cancelled surgeries (by surgery date) grouped by who recorded them, with the share over
  everything that person recorded.
- `pending-issues[?status=]`: pending issues of surgery withdrawals.
- `deliveries[?lot=]`: delivery documents already generated in the period — transfers and loans delivered to
  hospitals. With `hospitalId`, a loan is listed only for the hospital that received it; `lot` keeps only the
  documents with that lot (within the chosen period). PDF again at `/deliveries/{id}/pdf`; no `/pdf` variant.
- `consumption[?patient=]`: completed surgeries with items, lots and table values.
- `validity?days=`: lots expired or expiring within the window, inside the hospitals and in the storerooms.
- `loans?type=LOAN|RETURN`: loans between hospitals or returns to the supplier.
- `movements[?type=]`: stock movements in the period (at most 3000, newest first).

Billing:

- `GET /billing?months=2026-09&months=2026-10[&hospitalId=]` (+ `/billing/xlsx`, `/billing/pdf`): completed
  surgeries by the **month of the surgery date**, with the rates of that month. Commission = total × commission
  rate; share = commission × share rate. A past month can still grow when one of its surgeries is completed
  later.
- `GET/PUT/DELETE /billing-rates/{yyyy-MM}`: rates valid from a month until the next one; only the current or
  future months can be changed, so the rates of past months never change.

Dashboard: `GET /dashboard[?hospitalId=]` — items in stock, expiring within 30 days, expired, surgeries of the
closing week (Saturday to Friday), open pending issues (the card opens Relatórios › Pendências already
filtered), replenishment alerts and the latest movements.

PDF layouts: movement documents in the classic layout with the system colors; Relatórios and Faturamento in
the "highlighted summary" layout. Palette `#12302C`, `#0F5C55`, `#EEF6F3`, `#C9D6D2`, `#E3F1EC`.

## Data migration (imports)

Suggested order, using the `/imports` endpoints (`.xls` or `.xlsx`):

1. `POST /product-sections` (or Cadastros → Seções) — sections used by the catalog. Migration V9 creates the
   five sections of the hospitals' spreadsheet, which can be renamed.
2. `POST /imports/materials` — catalog (REF, DESCRIÇÃO, LINHA, GTIN, COMPONENTE, TAMANHO, COR, SEÇÃO). LINHA
   accepts several lines separated by comma; SEÇÃO must be an existing section.
3. `POST /imports/hospital/{id}/prices?mode=REPLACE` — each hospital's complete table (REF in column A, value
   in column B); `mode=UPDATE` changes only the REFs in the spreadsheet.
4. `POST /imports/hospital/{id}/stock?mode=REPLACE` — current balance (REF, LOTE, VALIDADE, QUANTIDADE, LOCAL).
5. `POST /imports/hospital/{id}/minimums` — REF, IDEAL, IDEAL TOTAL. The REFs with an ideal define what each
   hospital shows in the stock by material.
6. `PUT /users/{id}/hospitals` — assign each surgical tech to their hospitals.

Each import returns the rows with errors for correction and reload.

## Conventions

- **Code, database, API routes and API messages are in English**; the frontend translates them for users.
  Every new API message needs its Portuguese translation in `frontend/src/api/messages.js` (`EXACT` or
  `PATTERNS`): no error may reach the user in English. An unknown message falls back to a generic Portuguese
  sentence.
- Documents delivered to third parties (PDF reports, Excel exports, e-mails) stay in Portuguese.
- Spreadsheet imports read the Portuguese column headers used in the current files (REF, DESCRIÇÃO, GTIN,
  LINHA, SEÇÃO, LOTE, VALIDADE, QUANTIDADE, LOCAL, IDEAL, IDEAL TOTAL).
- **An endpoint either returns JSON or returns 204.** A plain `String` body is served as `text/plain`, and the
  screens read every answer as JSON — a plain sentence broke the screen after the operation had already been
  recorded. The HTTP client also tolerates a successful answer that is not JSON.
- Dates and times use `America/Bahia`.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| The camera does not focus and nothing is read | The phone opened the **ultra-wide** lens. Use the camera switch button in the reader; the choice is remembered for that device. |
| The page is marked "not secure" in development | The self-signed HTTPS certificate. Expected, and it does not block the camera. |
| Links sent by e-mail do not open the system | `FRONTEND_URL` / `app.frontend-url` must be the **https** address of the frontend. |
| E-mails are not delivered | RabbitMQ must be running and the mail service must be up: it is the service that declares the `default.email` queue. |
| A lot was typed wrong at the entry | Estoque › Por lote, the pencil on the line. See [Stock by lot](#stock-by-lot-get-stocklots). |
| The entry refuses a lot that exists | The number is registered with another REF. The screen offers the REF change; if the lot already went out in a surgery, the change is blocked and the surgeries are listed. |
