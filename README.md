# Local Artisan Cooperative ("ArtsyVibe")

A marketplace where local artisans sell handmade products and run auctions. Built with Spring Boot 3.3,
Thymeleaf, Spring Security, Spring Data JPA, Flyway and PostgreSQL.

Three kinds of accounts:

- **Customers** browse, buy, bid on auctions, review delivered purchases and request refunds.
- **Artisans** (approved by an admin) list products, run auctions, fulfil orders and answer refund requests.
- **Admins** approve artisans, moderate products and reviews, monitor auctions, and make the final call on refunds.

## Running locally

Requirements: JDK 17+ and a PostgreSQL database.

```bash
export PGHOST=localhost PGPORT=5432 PGDATABASE=artisan PGUSER=postgres PGPASSWORD=postgres
./gradlew bootRun          # http://localhost:5000
```

On startup Flyway creates or upgrades the schema (`src/main/resources/db/migration`). To get demo
accounts and products on an **empty** database, start the app once, then run `db/demo-data.sql`
(`psql -f db/demo-data.sql`). Every demo account uses the password `password123`:

| Role     | Email                 |
|----------|-----------------------|
| Admin    | alice@artsyvibe.com   |
| Artisan  | elena@artsyvibe.com   |
| Customer | john@example.com      |

Run the tests with `./gradlew test`. They use an in-memory H2 database in PostgreSQL mode, apply the real
Flyway migrations, and check that every page renders for every role.

### Configuration

| Setting | Default | Notes |
|---|---|---|
| `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` | — | Database connection (Replit provides these). |
| `SESSION_COOKIE_SECURE` | `false` | Set to `true` when the site is served over HTTPS. |
| `DEMO_REFRESH_STALE_AUCTIONS` | `false` | Demo only: re-opens ended LIVE auctions on startup. Never enable on real data. |

## How it is put together

- `controller/`: page controllers (Thymeleaf) and the JSON API under `/api/**`.
- `service/`: business rules (cart and checkout, auctions, products, accounts, dashboards).
- `security/`: Spring Security setup. `CurrentUser` is the only way code finds out who is calling.
- `model/`: JPA entities, plus the constants for roles and statuses (`Roles`, `UserStatus`, `OrderStatus`...).
- `src/main/resources/static/app-core.js`: loaded first on every page. It adds the CSRF header to `fetch`,
  handles expired sessions, and provides `escapeHtml` / `safeUrl`.

### Security model

- Logging in (`POST /api/auth/login`) creates a server-side session (HttpOnly `JSESSIONID` cookie).
  **Identity always comes from that session.** E-mails and ids sent by the browser
  (`X-User-Email`, `?email=`, `bidderId`, `artisanEmail`...) are ignored.
- Role rules per URL live in `SecurityConfig`. Ownership rules ("is this *your* order, product, address")
  live in the controllers and services.
- CSRF protection uses the `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header (`app-core.js` does this automatically).
- Passwords are BCrypt hashes and are never serialized to JSON. Any legacy plain-text passwords are hashed
  automatically at startup.
- A suspended or deleted account loses its session on its next request.

### Money

All amounts are `BigDecimal` / `numeric(12,3)` (the Bahraini dinar has 3 decimal places). Checkout locks
product rows, so two buyers cannot both get the last unit. Bids lock the auction row.

## Known limitations

- **Payments are simulated.** Checkout records a "completed" payment without charging anyone
  (`transaction_details = 'SIMULATED ...'`).
- **Images are stored in the database** as base64 data URLs, capped at about 2 MB each. Move them to file or object storage.
- Admin tables load every row and filter in the browser. There is no server-side pagination yet.
- "Forgot password" is UI only. No e-mail is sent.
- Reviews have an "auto-flagged" field, but nothing sets it yet (there is no content filter).
- An order has a single status even when it contains items from several artisans.
- Orders cannot be cancelled. A cancellation would need to restock items and refund the payment.

## Troubleshooting

- **App refuses to start with "Schema-validation: ..."**: the database schema differs from the entities.
  Usually it means an old database had manual changes. Fix the column the message names, or start once with
  `SPRING_JPA_HIBERNATE_DDL_AUTO=none` and add a Flyway migration.
- **Every POST returns 403**: the page is missing `<script src="/app-core.js"></script>` (CSRF header).
