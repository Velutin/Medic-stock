# MSS — Medic Stuff Stock

Surgical materials inventory control: stock entries, storeroom-to-hospital transfers, surgery withdrawals,
loans, minimum-based replenishment, supplier orders and PDF reports.

## Architecture

| Service         | Port | Purpose                                                   |
|-----------------|------|-----------------------------------------------------------|
| eureka-server   | 8082 | Service registry                                          |
| gateway         | 8090 | Single API entry point (`/mss/**`, `/mail/**`)            |
| mss             | 8085 | Inventory business rules (PostgreSQL `mssdb`, port 5440)  |
| mail            | 8081 | Email delivery through RabbitMQ (PostgreSQL `mssemaildb`) |

The database schema is created by **Flyway** (`backend/mss/src/main/resources/db/migration`).
Hibernate never changes tables.

## Running locally

```bash
docker compose up -d
cd backend/eureka-server
./mvnw spring-boot:run
cd backend/mss
./mvnw spring-boot:run
```

On Windows PowerShell use `.\mvnw.cmd` and run each command on its own line.

Swagger: http://localhost:8085/swagger-ui.html — initial user `admin@mss.local` / `admin123` (change it on first login and fill in CPF and mobile phone).

## Users and sessions

- Login by e-mail (`POST /auth/session`). The name is not unique; e-mail and CPF are.
- CPF (valid and unique) and mobile phone (DDD + 9 digits) are required for every new or edited user.
- New users have no password: they receive an e-mail link (valid for 48 hours, single use) to create it.
  Administrators can resend it (`POST /users/{id}/invitation`).
- Sessions: 30 minutes without use (renewed on every action, ended when the browser closes); with
  "keep me signed in", 7 days counted from the login. Changing the password ends the sessions on other
  devices; deactivating a user blocks access immediately.
- API errors always use the format `{ "status", "message", "fields" }`.

## Profiles and values

- ADMIN/MASTER: everything. SURGICAL_TECH: surgeries of their hospitals and the stock summary without lots
  (`GET /stock/summary`); never sees prices or surgery values. USER (read-only): lot-level stock of their hospitals.
- Surgery items recorded while the REF had no value receive it automatically when the value is registered;
  values already recorded never change (audit).
- Administrators can add or remove items of completed surgeries; cancelled surgeries cannot be changed.

## Reports (`/reports`, ADMIN)

Each report returns the data for the on-screen preview and has a `/pdf` variant with the same filters
(`start`, `end` inclusive dates, optional `hospitalId`):

- `weekly-closing?date=`: week from Saturday to Friday containing the date; open and completed surgeries by surgery
  date per hospital, items consumed and the surgical techs assigned to each hospital.
- `cancellations`: cancelled surgeries (by surgery date) grouped by who recorded them, with the share over everything
  that person recorded.
- `pending-issues[?status=]`: pending issues of surgery withdrawals (resolved through `PATCH /pending-issues/{id}`).
- `deliveries`: delivery reports already generated (PDF again at `/deliveries/{id}/pdf`; no `/pdf` variant).
- `consumption[?patient=]`: completed surgeries with items, lots and table values.
- `validity?days=`: lots expired or expiring within the window, inside the hospitals and in the storerooms.
- `loans?type=LOAN|RETURN`: loans between hospitals or returns to Baumer.
- `movements[?type=]`: stock movements in the period (at most 3000, newest first).

## Billing

- `GET /billing?months=2026-09&months=2026-10[&hospitalId=]` (+ `/billing/xlsx`, `/billing/pdf`): completed surgeries
  by the month of the surgery date, with the rates of that month. Commission = total x commission rate;
  share = commission x share rate. A past month can still grow when one of its surgeries is completed later.
- `GET/PUT/DELETE /billing-rates/{yyyy-MM}`: rates valid from a month until the next one; only the current or
  future months can be changed, so the rates of past months never change.
- `GET /dashboard[?hospitalId=]`: stock indicators, closing week (Saturday to Friday), replenishment alerts and
  latest movements.

Production environment variables for `mss`: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`,
`STORAGE_DIR`, `LOGO_PATH` and `COMPANY_NAME` (report header), `FRONTEND_URL`, `RABBITMQ_HOST`, `EUREKA_URL`.

Report logo: `backend/mss/branding/logo.png`. To change it, replace the file keeping the same name.

## Data model

- `hospital` (+ `hospital_product_line`): served hospitals, price table type (SIGTAP/TENDER) and product lines (HIP, KNEE, SHOULDER).
- `product_section`: catalog sections registered by the user (e.g. "Quadril não cimentada", "Bipolar"), with display order; group the hospital stock by material.
- `material`: catalog by REF, with GTIN (GS1 AI 01, identifies the REF when scanning), section, component, size and identification color.
- `hospital_price`: REF value at each hospital; used on surgery withdrawals.
- `lot`: number and mandatory expiry date (GS1 AI 10 and 17). A lot is identified by material + number + expiry date: units of the same lot sterilized on different days have different expiry dates and are separate records, each with its own balance.
- `stock`: balance per lot + hospital + location (`STOREROOM` = in the storeroom, assigned to the hospital; `HOSPITAL` = inside the hospital).
- `minimum_stock`: hospital "Ideal" and "Ideal total" (hospital + storeroom) per REF.
- `surgery`, `surgery_item`, `pending_issue`: material withdrawals and scans that need review.
- `stock_entry`, `stock_entry_item`: material received from the supplier, grouped per receipt so it can be corrected as a whole.
- `loan`, `delivery`, `supplier_order`: loans, storeroom-to-hospital deliveries (transfers) and supplier orders.
- `stock_movement`: ledger of every balance change (`ENTRY`, `ENTRY_CORRECTION`, `INVENTORY_ADJUSTMENT`, `REPLENISHMENT`,
  `SURGERY_WITHDRAWAL`, `SURGERY_REVERSAL`, `LOAN`); movements created by an entry point to it (`stock_entry_id`).
- `user_hospital`: hospitals each surgical tech works at.

## Business rules

- Scanning: the GTIN (AI 01) identifies the REF and the lot number (AI 10) identifies the lot. A code with no lot is rejected; an unknown GTIN is learned the first time its lot is matched unambiguously.
- `GET /materials/scan?code=` reads a QR code, barcode, labeled text or typed REF without requiring the lot to exist
  (used by entries and transfers).
### Stock entry (`/stock-entries`, ADMIN/MASTER)

- Material received from the supplier always enters the **storeroom**, assigned to the destination; it reaches the
  hospital through a transfer.
- Destinations: active regular hospitals and distribution centers (SESAB). Hospitals supplied by a distribution
  center are refused: their material enters through the center.
- The receipt date is the entry date and the date of its `ENTRY` movements; it cannot be in the future.
- Expired lots are refused. The same lot (material + number + expiry date) repeated is summed. A new REF can be
  registered on the spot.
- `PUT /stock-entries/{id}` corrects the whole entry (destination, date, REF, lot, expiry date, quantity) and applies
  only the differences as `ENTRY_CORRECTION`; it is refused when the material to remove already left the storeroom.
- `POST /stock-entries/preview` reads an entry spreadsheet (REF, LOTE, VALIDADE, QUANTIDADE) for review, without saving.
- Scanning on the entry screen: a GS1 QR code fills REF, lot and expiry date. A GTIN barcode identifies only the REF;
  the next reading is taken as the lot barcode (the same GTIN read again is refused), and the expiry date is typed
  as MM/AAAA, stored as the first day of the month (DD/MM/AAAA is also accepted).
- `POST /stock/entry` (single item, without grouping) still exists for compatibility.

### Transfer (storeroom → hospital, `POST /deliveries`)

- Destinations: active regular hospitals. For a hospital supplied by a distribution center, lots come from the
  center's storeroom. Expired lots are not listed and are refused.
- The quantities can be filled with the replenishment suggestion. On the phone, each reading adds one unit and
  a lot that is not in the storeroom shows an error.
- Each transfer generates the delivery report PDF (`GET /deliveries/{id}/pdf`).

### Stock by material (`GET /stock/summary`)

- Grouped by section, then by item name (component) and size; 10 sizes per page on screen.
- Lists the REFs with an ideal (hospital ideal or ideal total) greater than zero in the hospital, showing 0 when
  there is no balance, plus any REF with balance inside the hospital even without an ideal. Expired lots are ignored.
- Sections are managed in `/product-sections`; a section with items cannot be removed, only deactivated.

### Other rules

- Surgery withdrawals debit the stock **inside** the hospital and use that hospital's price table.
- Unknown, ambiguous, expired or out-of-balance lots become **pending issues** without touching balances.
- Each consumption sheet label counts as 1 item. Sheet photos are converted to PDF.
- Loans can come from the hospital or the storeroom; the supplier is informed when the loan is made (no pending status). A return to the supplier (Baumer) takes the material out of every stock: reason required, expired lots accepted, no PDF.
- Replenishment: below Ideal → replenish from the storeroom; below Ideal total → order from the supplier. Expired lots are ignored.
- Surgical techs only see the hospitals they work at; ADMIN/MASTER see all of them.
- Weekly surgery report: Saturday to Friday.

## Conventions

- Code, database, API routes and API messages are in English; the frontend translates them for users.
- Documents delivered to third parties (PDF reports, Excel export, emails) stay in Portuguese.
- Spreadsheet imports read the Portuguese column headers used in the current files
  (REF, DESCRIÇÃO, GTIN, LINHA, SEÇÃO, LOTE, VALIDADE, QUANTIDADE, LOCAL, IDEAL, IDEAL TOTAL).

## Data migration

Suggested order, using the `/imports` endpoints (.xls or .xlsx):

1. `POST /product-sections` (or Cadastros → Seções) — sections used by the catalog. Migration V9 creates the five
   sections of the hospitals' spreadsheet, which can be renamed.
2. `POST /imports/materials` — catalog (REF, DESCRIÇÃO, LINHA, GTIN, COMPONENTE, TAMANHO, COR, SEÇÃO). LINHA accepts several lines separated by comma; SEÇÃO must be an existing section.
3. `POST /imports/hospital/{id}/prices?mode=REPLACE` — each hospital's complete table (REF in column A, value in column B); `mode=UPDATE` changes only the REFs in the spreadsheet.
4. `POST /imports/hospital/{id}/stock?mode=REPLACE` — current balance (REF, LOTE, VALIDADE, QUANTIDADE, LOCAL).
5. `POST /imports/hospital/{id}/minimums` — REF, IDEAL, IDEAL TOTAL. The REFs with an ideal define what each hospital
   shows in the stock by material.
6. `PUT /users/{id}/hospitals` — assign each surgical tech to their hospitals.

Each import returns the rows with errors for correction and reload.
