# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Purpose
Backend of an online bookstore e-commerce platform (browse, cart, checkout, pay, ship, cancel/return), built with IBM Bob. A React + Carbon UI consumes the REST API and is secondary. Functional domains and flow are in [`docs/architecture.png`](docs/architecture.png): Member, Store, Catalog, Order, Payment, Shipping.

## State of the repo
- `backend/` holds the Spring Boot 3.5 skeleton (no business endpoints yet), the Flyway `V1__initial_schema.sql` built from [`docs/data-model.md`](docs/data-model.md) and the `Containerfile`. `api/openapi.yaml` and `frontend/` do not exist yet. `requirements.md` is the source of truth: do not edit it unless asked. Items tagged **[ASSUMPTION]** are binding.
- Everything (code, comments, identifiers, API docs, commits, seed data, docs) MUST be in English.

## Agent configuration map
- [`.bob/rules/`](.bob/rules): one short file per topic (`java-style`, `testing`, `security-and-payments`, `api-and-openapi`, `git-workflow`). Read the relevant one before working on that topic.
- [`.bob/skills/`](.bob/skills): `openapi-first`, `new-endpoint`, `db-migration`, `seed-data`, `checkout-flow`. Use the matching skill instead of improvising the workflow.
- [`.bob/custom_modes.yaml`](.bob/custom_modes.yaml): `bookstore-backend-dev` (read, edit, execute, mcp) and `bookstore-code-review` (read only).
- [`.bob/mcp.json`](.bob/mcp.json): empty template; never put credentials in it. [`.bobignore`](.bobignore) excludes build outputs, dependencies, `.env` and IDE files.
- `.bob/rules-agent|ask|plan/AGENTS.md`: mode-specific non-obvious notes.

## Non-negotiable rules
1. Contract-first: `api/openapi.yaml` is updated and reviewed before any endpoint code.
2. Schema changes only via new Flyway migrations; never edit an applied migration.
3. Money is `BigDecimal` / `NUMERIC(12,2)`; prices are always recomputed server-side.
4. No secrets, real credentials or card data in the repo, logs, seed data or `.bob/mcp.json`; secrets only through env vars.
5. Domains talk only through service interfaces; no cross-domain repository access.
6. Books come from our own API and PostgreSQL; no external book API at runtime.
7. Podman and `Containerfile`/`compose.yaml` only; no Docker-specific files, no cloud manifests.
8. Do not modify `requirements.md` unless asked.

## Target layout (monorepo)
`backend/` (Spring Boot), `frontend/` (React + `@carbon/react` only, no custom design system), `api/openapi.yaml`, `docs/` (`data-model.md`), `compose.yaml`, `.env.example`. Each `Containerfile` lives in its own service folder (not the root).

## Commands (verified on macOS with Podman 5.8)
- Container tool is **Podman**, not Docker: `cp .env.example .env && podman compose up --build`. Never generate Docker-specific files (`Dockerfile`, `docker-compose.yml`); use `Containerfile` and `compose.yaml`.
- If host port 8080 is taken, set `BACKEND_PORT` in `.env` (for example `8081`); the container always listens on 8080.
- Backend (run from `backend/`): `./mvnw clean package` (always the wrapper; runs the tests), `./mvnw spring-boot:run` (needs a PostgreSQL and `SPRING_DATASOURCE_PASSWORD`).
- Tests need Testcontainers to reach Podman. On macOS: `export DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}') TESTCONTAINERS_RYUK_DISABLED=true`.
- Single test: `./mvnw test -Dtest=ClassName#methodName`.
- Health: `/actuator/health` (public). Unauthenticated calls to any other path return 401. No H2 anywhere; tests use Testcontainers PostgreSQL.
- Spring Boot is pinned to 3.5.x because springdoc 3.x and start.spring.io target Spring Boot 4. Keep springdoc on 2.8.x.

## Stack and package rules
- Java 21, Spring Boot 3.x, PostgreSQL, Flyway, MapStruct, springdoc, Spring Security + JWT.
- `groupId` `com.bookstore`, `artifactId` `bookstore-backend`, base package `com.bookstore.ecommerce.{member,store,catalog,order,payment,shipping}`.
- Modular monolith: no cross-domain DB/repository access; call other domains only through their service interfaces.
- Layers: controller -> service -> repository; DTOs separate from entities (MapStruct); Bean Validation on inputs.

## Contract-first
- Write/update `api/openapi.yaml` BEFORE implementing endpoints; code must match it. All paths under `/api/v1`.
- Pick `snake_case` or `camelCase` once and use it everywhere. Errors: RFC 7807 `application/problem+json` with a stable error code. Status codes: 201 create, 204 delete, 400/401/403/404/409/422. Pagination via `page`, `size`, `sort`. Idempotency key on payment and order placement. Document required role per endpoint.

## Data conventions
- UUID primary keys on every table; money is `NUMERIC(12,2)` USD (never float/double); every table has `created_at` and `updated_at`.
- Schema ONLY via Flyway migrations (no Hibernate auto-DDL). Currency via `app.currency` (default `USD`).
- Seed (30-50 books, "Main Store", ADMIN + CUSTOMER dev users) loads only in local/dev profiles, never production. Seed must be reproducible offline (script + committed sample JSON); never call Open Library/Google Books at runtime.

## Business rules easy to get wrong
- Order lifecycle: `CART -> PENDING_PAYMENT -> CONFIRMED -> SHIPPED -> DELIVERED`; also `CONFIRMED -> CANCELLED`, `DELIVERED -> RETURN_REQUESTED -> RETURNED`, `PENDING_PAYMENT -> PAYMENT_FAILED`.
- Cancel window comes from the store policy (default 48h): 47h succeeds, after 48h returns 409/422 with a clear error code.
- Guests may hold a cart (merged on login) but cannot check out. Roles: `GUEST`, `CUSTOMER`, `ADMIN`.
- Gift points: configurable, default 100 points = 1 USD, no expiry. Refunds go back to the original method (points/wallet credited back).
- Payment uses a gateway abstraction with a MOCK gateway only; never store card data. Server-side price validation; never trust client prices. Buy Again revalidates current price and stock.
- Multi-store data model, but only one seeded store.

## Security
BCrypt passwords; secrets only through env vars (`.env` git-ignored, `.env.example` committed); never log secrets or personal data.

## Testing
JUnit 5, Mockito, Testcontainers, REST-assured/MockMvc. Min 70% line coverage on the service layer. Record in the README which tests were generated by the ICA "API Testcase generator" agent.

## Out of scope
No cloud/Kubernetes/OpenShift files unless explicitly asked. No real payments, carriers, email/SMS, multi-currency, multi-language.
