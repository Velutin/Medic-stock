# MSS — Medic Stuff Stock

Surgical materials inventory control: storeroom, hospitals, surgery withdrawals, loans,
minimum-based replenishment, supplier orders and PDF reports.

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

## Billing

- `GET /billing?months=2026-09&months=2026-10[&hospitalId=]` (+ `/billing/xlsx`, `/billing/pdf`): completed surgeries
  by completion month. Commission = total x commission rate; share = commission x share rate.
- `GET/PUT/DELETE /billing-rates/{yyyy-MM}`: rates valid from a month until the next one; only the current or
  future months can be changed, so past billing never changes.
- `GET /dashboard[?hospitalId=]`: stock indicators, closing week (Saturday to Friday), replenishment alerts and
  latest movements.

Production environment variables for `mss`: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`,
`STORAGE_DIR`, `LOGO_PATH` and `COMPANY_NAME` (report header), `FRONTEND_URL`, `RABBITMQ_HOST`, `EUREKA_URL`.

Report logo: `backend/mss/branding/logo.png`. To change it, replace the file keeping the same name.

## Data model

- `hospital` (+ `hospital_product_line`): served hospitals, price table type (SIGTAP/TENDER) and product lines (HIP, KNEE, SHOULDER).
- `material`: catalog by REF, with GTIN (GS1 AI 01, identifies the REF when scanning), component, size and identification color.
- `hospital_price`: REF value at each hospital; used on surgery withdrawals.
- `lot`: number and mandatory expiry date (GS1 AI 10 and 17). A lot is identified by material + number + expiry date: units of the same lot sterilized on different days have different expiry dates and are separate records, each with its own balance.
- `stock`: balance per lot + hospital + location (`STOREROOM` = in the storeroom, assigned to the hospital; `HOSPITAL` = inside the hospital).
- `minimum_stock`: hospital "Ideal" and "Ideal total" (hospital + storeroom) per REF.
- `surgery`, `surgery_item`, `pending_issue`: material withdrawals and scans that need review.
- `loan`, `delivery`, `supplier_order`: loans, storeroom-to-hospital deliveries and supplier orders.
- `stock_movement`: ledger of every balance change.
- `user_hospital`: hospitals each surgical tech works at.

## Business rules

- Scanning: the GTIN (AI 01) identifies the REF and the lot number (AI 10) identifies the lot. A code with no lot is rejected; an unknown GTIN is learned the first time its lot is matched unambiguously.
- Surgery withdrawals debit the stock **inside** the hospital and use that hospital's price table.
- Unknown, ambiguous, expired or out-of-balance lots become **pending issues** without touching balances.
- Each consumption sheet label counts as 1 item. Sheet photos are converted to PDF.
- Loans can come from the hospital or the storeroom and stay pending until the supplier is notified.
- Replenishment: below Ideal → replenish from the storeroom; below Ideal total → order from the supplier. Expired lots are ignored.
- Surgical techs only see the hospitals they work at; ADMIN/MASTER see all of them.
- Weekly surgery report: Saturday to Friday.

## Conventions

- Code, database, API routes and API messages are in English; the frontend translates them for users.
- Documents delivered to third parties (PDF reports, Excel export, emails) stay in Portuguese.
- Spreadsheet imports read the Portuguese column headers used in the current files
  (REF, DESCRIÇÃO, GTIN, LOTE, VALIDADE, QUANTIDADE, LOCAL, IDEAL, IDEAL TOTAL).

## Data migration

Suggested order, using the `/imports` endpoints (.xls or .xlsx):

1. `POST /imports/materials` — catalog (REF, DESCRIÇÃO, LINHA, GTIN, COMPONENTE, TAMANHO, COR). LINHA accepts several lines separated by comma.
2. `POST /imports/hospital/{id}/prices?mode=REPLACE` — each hospital's complete table (REF in column A, value in column B); `mode=UPDATE` changes only the REFs in the spreadsheet.
3. `POST /imports/hospital/{id}/stock?mode=REPLACE` — current balance (REF, LOTE, VALIDADE, QUANTIDADE, LOCAL).
4. `POST /imports/hospital/{id}/minimums` — REF, IDEAL, IDEAL TOTAL.
5. `PUT /users/{id}/hospitals` — assign each surgical tech to their hospitals.

Each import returns the rows with errors for correction and reload.
