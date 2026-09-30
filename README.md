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

Swagger: http://localhost:8085/swagger-ui.html — initial user `admin` / `admin123` (change it on first login).

Production environment variables for `mss`: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`,
`STORAGE_DIR`, `LOGO_PATH` (Baumer logo, PNG/JPG), `FRONTEND_URL`, `RABBITMQ_HOST`, `EUREKA_URL`.

## Data model

- `hospital` (+ `hospital_product_line`): served hospitals, price table type (SIGTAP/TENDER) and product lines (HIP, KNEE, SHOULDER).
- `material`: catalog by REF, with GTIN (GS1 AI 01, identifies the REF when scanning), component, size and identification color.
- `hospital_price`: REF value at each hospital; used on surgery withdrawals.
- `lot`: number and mandatory expiry date (GS1 AI 10 and 17).
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

1. `POST /imports/materials` — catalog (REF, DESCRIÇÃO, GTIN, COMPONENTE, TAMANHO, COR).
2. `POST /imports/hospital/{id}/prices` — each hospital's table (REF in column A, value in column B).
3. `POST /imports/hospital/{id}/stock?mode=REPLACE` — current balance (REF, LOTE, VALIDADE, QUANTIDADE, LOCAL).
4. `POST /imports/hospital/{id}/minimums` — REF, IDEAL, IDEAL TOTAL.
5. `PUT /hospitals/users` — assign each surgical tech to their hospitals.

Each import returns the rows with errors for correction and reload.
