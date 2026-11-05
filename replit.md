# Local Artisan Cooperative

A Spring Boot web application for an artisan marketplace called "ArtsyVibe" — lets users browse handcrafted products, view auctions, manage a shopping cart, and access their profile.

## Run & Operate

- **Run**: `./gradlew bootRun` (via "Start application" workflow, port 5000)
- **Build**: `./gradlew build -x test`
- **Deploy run**: `java -jar build/libs/demo-0.0.1-SNAPSHOT.jar --server.port=5000`
- **Required env vars**: `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` (provided automatically by Replit PostgreSQL)

## Stack

- **Framework**: Spring Boot 3.3.5
- **Language**: Java 19 (GraalVM CE 22.3.1)
- **Build**: Gradle 9.4.1 (wrapper)
- **Template Engine**: Thymeleaf (server-side rendering, `.html` suffix)
- **Database**: PostgreSQL via Replit built-in integration
- **ORM**: Spring Data JPA / Hibernate (ddl-auto=update)

## Where things live

- `src/main/java/com/example/demo/` — Java source (controllers, entities)
- `src/main/resources/templates/` — Thymeleaf HTML templates
- `src/main/resources/static/` — Static assets (Logo.png, Benifitpay.png, etc.)
- `src/main/resources/application.properties` — Server port, DB config, JPA settings
- `build.gradle` — Dependencies and build config

## Architecture decisions

- Thymeleaf suffix set to lowercase `.html` to match actual template filenames
- `gradlew` CRLF line endings were converted to LF for Linux compatibility
- `MainController` maps `/` to the `index` template (homepage)
- JPA `ddl-auto=update` auto-creates/updates tables from entity classes — no manual migrations needed
- Port 5000 mapped to external port 80 in `.replit`

## Product

- Homepage with product search and category browsing
- Products listing page
- Auctions page with bidding UI
- Shopping cart and checkout flow
- User profile, orders, wishlist, notifications pages
- Login and forgot-password pages

## User preferences

_Populate as you build_

## Test Credentials (seed data, password: `password123`)

| Role     | Email                  |
|----------|------------------------|
| Admin    | alice@artsyvibe.com    |
| Artisan  | elena@artsyvibe.com    |
| Customer | john@example.com       |

## Gotchas

- Template filenames are mixed-case (e.g., `Login.html`, `Forget-Password.html`) — Thymeleaf suffix must remain lowercase `.html`
- The Gradle wrapper (`gradlew`) must have LF line endings — re-run `sed -i 's/\r//' gradlew` if it gets reset to CRLF

## Pointers

- [Spring Boot docs](https://docs.spring.io/spring-boot/docs/3.3.5/reference/html/)
- [Thymeleaf docs](https://www.thymeleaf.org/documentation.html)
