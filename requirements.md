# Requirements: Online Bookstore E-Commerce Backend

> Source of truth for the project. Humans own this file; agents read it and must not change it without being asked. Items marked **[ASSUMPTION]** are defaults chosen to fill gaps in the original brief; they are binding unless changed in this file (see section 14).
>
> **Language rule:** everything is written in English: code, comments, identifiers, API documentation, commit messages, UI text, seed data and project documentation.

## 1. Objective

Build the **backend** of an e-commerce platform to browse and purchase **books**, using an agentic IDE (IBM Bob) throughout the development workflow. The capstone covers: data model design, OpenAPI specification, Spring Boot services, AI-generated tests, local deployment and optionally cloud deployment.

The UI is produced by vibe coding with IBM Bob and is secondary to the backend; it consumes the backend API.

### 1.1 Project identity and repository layout

| Item | Value |
|---|---|
| Project name | `bookstore` |
| Maven `groupId` | `com.bookstore` |
| Maven `artifactId` | `bookstore-backend` |
| Base Java package | `com.bookstore.ecommerce` |
| Domain packages | `com.bookstore.ecommerce.{member,store,catalog,order,payment,shipping}` |
| Currency | USD (single currency, configurable via `app.currency`, default `USD`) |
| Git hosting | Personal GitHub account of the author, repository `bookstore` |

Single repository (monorepo) with this layout:

```
bookstore/
├── backend/    # Spring Boot application (generated with IBM Bob)
├── frontend/   # React + Carbon Design System UI
├── api/        # OpenAPI specification (api/openapi.yaml)
├── docs/       # Architecture image, data model, project documentation
├── compose.yaml
├── .env.example
├── requirements.md
└── AGENTS.md
```

The `Containerfile` for each service lives inside its own folder (`backend/Containerfile`, `frontend/Containerfile`).

## 2. Use Case

An online store where a customer can browse, select and buy books.

### 2.1 User journeys

**E-store Home**
1. Login page (registered users) or continue as guest.
2. Order history with a **Buy Again** feature.
3. Access the product catalogue for each category.

**Catalogue**
4. Select a product category.
5. Browse brands (publishers or imprints).
6. Select a product. Each product shows a **tentative delivery date**, and **related products** are offered.
7. Add the product to the basket.

**Payment and purchase confirmation**
8. Select the delivery address.
9. Initiate payment with the right option, including **redeeming gift points**.
10. Payment confirmation.
11. Purchase confirmation. The customer can **cancel the order within 48 hours**.

### 2.2 Actors

| Actor | Description |
|---|---|
| Guest user | Can browse the catalogue. Must log in or register to check out. **[ASSUMPTION]** |
| Registered user | Can browse, buy, view order history, manage addresses, use gift points and wallet. |
| Admin | Creates stores, catalogs and store policies; manages products and users. |

## 3. Architecture (functional domains)

The platform is split into six domains. Flow: `User Login -> Home Page -> Browse -> Add to Cart -> Payment Processing -> Shipment`. Guests enter at Home Page directly.

| Domain | Responsibilities |
|---|---|
| **Member** | Guest and registered users; login and logout; roles and entitlements. |
| **Store** | Create stores; create catalogs; create store policies. |
| **Catalog** | Browse catalog by entitlement; browse products by category and brand; recommended products based on order history; up-sell and cross-sell products. |
| **Order** | Create and modify order; order checkout; confirm, cancel and return order; order history; redeem gift points and coupons. |
| **Payment** | Payment processing through a gateway; refund processing; payment confirmation; gift and wallet. |
| **Shipping** | Shipping rate calculation; approximate delivery time; return shipment. |

Implementation style: a single Spring Boot application organized as a **modular monolith**, one package per domain, with no cross-domain database access except through service interfaces. **[ASSUMPTION]**

## 4. Functional Requirements

### 4.1 Member
- FR-M1: Register, log in and log out. Authentication via JWT. **[ASSUMPTION]**
- FR-M2: Roles: `GUEST`, `CUSTOMER`, `ADMIN`. Entitlements control which catalogs a user can see.
- FR-M3: Manage delivery addresses (create, update, delete, set default).
- FR-M4: View gift point balance and wallet balance.

### 4.2 Store
- FR-S1: Admin creates stores.
- FR-S2: Admin creates catalogs and assigns them to a store.
- FR-S3: Admin defines store policies (for example cancellation window, return window, free-shipping threshold).
- FR-S4: The data model supports **multiple stores**, but the seed data creates a single default store named **"Main Store"** to keep the initial setup simple.

### 4.3 Catalog
- FR-C1: List categories.
- FR-C2: List brands, filterable by category.
- FR-C3: List and search products, filtered by category and brand, with pagination and sorting.
- FR-C4: Product detail includes price, stock status, cover image, author, ISBN and **tentative delivery date**.
- FR-C5: Related products per product (same category or author) for cross-sell and up-sell.
- FR-C6: Recommended products for a logged-in user, based on their order history.
- FR-C7: Catalog visibility is filtered by the user's entitlement.

### 4.4 Cart and Order
- FR-O1: Add, update quantity and remove items in the cart. Guests may hold a cart; it is merged on login. **[ASSUMPTION]**
- FR-O2: Checkout: select delivery address, review totals (items, shipping, discounts) and place the order.
- FR-O3: Apply a coupon and redeem gift points at checkout.
- FR-O4: Order confirmation with a confirmation number.
- FR-O5: **Cancel an order within 48 hours** of confirmation. After that, cancellation is rejected with a clear error. The window comes from the store policy and defaults to 48h.
- FR-O6: Return an order (after delivery), which creates a return shipment and a refund.
- FR-O7: Order history, paginated, with status.
- FR-O8: **Buy Again**: re-add the items of a past order to the cart, validating current price and stock.

### 4.5 Payment
- FR-P1: Initiate payment through a payment gateway abstraction. Use a **mock gateway** for the capstone; no real card data is processed or stored.
- FR-P2: Payment methods: card (mock), wallet, gift points, or a combination.
- FR-P3: Payment confirmation updates the order status.
- FR-P4: Refunds on cancellation or return, returned to the original method (gift points and wallet are credited back).
- FR-P5: Gift point conversion rate is configurable. Default: 100 points = 1 currency unit. **[ASSUMPTION]**

### 4.6 Shipping
- FR-SH1: Calculate the shipping rate from address, weight and store policy.
- FR-SH2: Estimate delivery time and return a tentative delivery date for a product and address.
- FR-SH3: Create and track a shipment per confirmed order; create return shipments.

### 4.7 Order lifecycle

`CART -> PENDING_PAYMENT -> CONFIRMED -> SHIPPED -> DELIVERED`
Alternative paths: `CONFIRMED -> CANCELLED` (within window), `DELIVERED -> RETURN_REQUESTED -> RETURNED`, `PENDING_PAYMENT -> PAYMENT_FAILED`.

## 5. Data Model (entities to design, manual step)

The data model is designed manually from the wireframes before code generation. Expected entities:

`User`, `Role`, `Address`, `Store`, `StorePolicy`, `Catalog`, `CatalogEntitlement`, `Category`, `Brand`, `Product` (book), `Author`, `Inventory`, `RelatedProduct`, `Cart`, `CartItem`, `Order`, `OrderItem`, `Payment`, `Refund`, `Wallet`, `WalletTransaction`, `GiftPointLedger`, `Coupon`, `Shipment`, `ShippingRate`, `ReturnShipment`.

Data conventions:
- Primary keys: **UUID** on every table.
- Monetary values: `NUMERIC(12,2)` in USD. Never floating point.
- All tables carry `created_at` and `updated_at`.
- Schema is managed by **Flyway** migrations, not by Hibernate auto-DDL. **[ASSUMPTION]**

### 5.1 Book data source

The books API is **this project's own API**: the OpenAPI spec generated in the workflow (Step 3) defines the book endpoints (`/products`, `/categories`, `/brands`) and the Spring Boot backend serves them from PostgreSQL. There is no external book API consumed at runtime.

The brief does not provide a book dataset, so the database must be seeded. Minimum: a Flyway or SQL seed with 30 to 50 sample books across several categories and brands. Optionally, a public API can be used **only once, to generate that seed**, never as a runtime dependency:

- **Open Library API** (search, subjects, covers): preferred. No API key.
- **Google Books API**: alternative.

Neither provides reliable prices or stock, so seed scripts generate price, stock and brand mappings. Seed data must be reproducible (a script plus a committed sample JSON) so the project runs offline. Check the terms of use of whichever source is used.

### 5.2 Seed data

The seed (Flyway migration or SQL script, loaded automatically in local and dev profiles) creates:
- One store: **"Main Store"**, with a default catalog and default store policies (48h cancellation window).
- Categories, brands and 30 to 50 sample books.
- Two users for local development: one `ADMIN` and one `CUSTOMER`, each with a customer address, a wallet balance and some gift points.

The development credentials of the seeded users are documented in the README and marked **for local development only**. They must never be used outside a local environment, and the seed that creates them must not run in a production profile.

## 6. Technology Stack

| Area | Choice |
|---|---|
| Language / runtime | Java 21 (LTS), approved by the course material |
| Framework | Spring Boot 3.x (Web, Data JPA, Validation, Security) |
| Build | Maven (use the wrapper `./mvnw`) |
| Database | **PostgreSQL** (the brief's mention of MySQL/H2 does not apply; H2 is not used) |
| Migrations | Flyway |
| API contract | **OpenAPI 3.x**, written first (contract-first), kept in `api/openapi.yaml` |
| API docs | springdoc-openapi (Swagger UI) |
| Mapping | MapStruct |
| Auth | Spring Security + JWT |
| Tests | JUnit 5, Mockito, Testcontainers (PostgreSQL), REST-assured or MockMvc |
| UI | React with **Carbon Design System** (`@carbon/react`), generated through Bob |
| Containers | **Podman** and `podman compose` (no local installs of PostgreSQL or the JDK required to run the stack) |
| Dev tooling | IBM Bob (IDE), Insomnia for manual API testing, Git |

### 6.1 Container environment (Podman)
- `compose.yaml` runs `postgres` and `backend` (and `frontend` when present).
- One command brings the stack up: `podman compose up --build`.
- Configuration through environment variables with an `.env.example` committed. Real `.env` is git-ignored.
- Backend image built with a multi-stage `Containerfile` (JDK build, JRE runtime, non-root user).
- Health checks for PostgreSQL and the backend (`/actuator/health`).
- Testcontainers must work with Podman (document the socket configuration needed).

## 7. Workflow with IBM Bob

1. **Analyze wireframes**: identify entities and features (sections 4 and 5).
2. **Data model**: designed manually, documented in `docs/data-model.md` (ER diagram).
3. **OpenAPI spec**: Bob generates `api/openapi.yaml` from the wireframes, this document and the data model. Review and save before any code is written.
4. **Spring Boot code**: generated from the OpenAPI spec, matching the stack in section 6.
5. **Project setup**: review structure (controllers, services, repositories, entities, DTOs, mappers).
6. **Database**: PostgreSQL in Podman; connection settings via `application.properties` with environment overrides.
7. **Run**: `./mvnw clean install`, then `./mvnw spring-boot:run` or `podman compose up`.
8. **Test APIs**: every endpoint (GET, POST, PUT, DELETE) verified against the spec and the database.
9. **Git**: personal GitHub account, repository `bookstore`; feature branch `feature/api-implementation`, commits, push and Pull Request with API documentation.
10. **AI-augmented tests**: generate test cases with the ICA agent **API Testcase generator** (chosen because the deliverable is a backend API; the course material leaves the choice open), in addition to hand-written tests. Record in the README which tests were agent-generated.
11. **Deployment**: local with Podman. Cloud deployment is a separate optional requirement (section 13.1).

## 8. API Requirements (high level, to be detailed in OpenAPI)

All endpoints under `/api/v1`. Resources by domain:

| Domain | Resources |
|---|---|
| Member | `/auth/register`, `/auth/login`, `/auth/logout`, `/users/me`, `/users/me/addresses`, `/users/me/wallet`, `/users/me/gift-points` |
| Store | `/stores`, `/stores/{id}/catalogs`, `/stores/{id}/policies` |
| Catalog | `/categories`, `/brands`, `/products`, `/products/{id}`, `/products/{id}/related`, `/products/recommended` |
| Cart | `/cart`, `/cart/items`, `/cart/items/{id}` |
| Order | `/orders`, `/orders/{id}`, `/orders/{id}/cancel`, `/orders/{id}/return`, `/orders/{id}/buy-again`, `/orders/checkout` |
| Payment | `/payments`, `/payments/{id}`, `/payments/{id}/refund` |
| Shipping | `/shipping/rates`, `/shipping/estimate`, `/shipments/{id}`, `/shipments/{id}/return` |

API conventions:
- JSON only; `snake_case` or `camelCase` chosen once and applied everywhere.
- Pagination: `page`, `size`, `sort` parameters; list responses include total counts.
- Errors follow RFC 7807 (`application/problem+json`) with a stable error code.
- Correct HTTP status codes (201 on create, 204 on delete, 400, 401, 403, 404, 409, 422).
- Idempotency key supported on payment and order placement. **[ASSUMPTION]**
- Every endpoint documents its required role.

## 9. Non-Functional Requirements

- **Security**: BCrypt password hashing; never log secrets or personal data; no card data stored; server-side price validation (never trust client prices); secrets via environment only; role checks on every protected endpoint.
- **Quality**: layered architecture (controller, service, repository); DTOs separate from entities; Bean Validation on inputs.
- **Testing**: minimum 70% line coverage on service layer **[ASSUMPTION]**; integration tests against real PostgreSQL via Testcontainers; contract check that the running API matches `openapi.yaml`.
- **Performance**: catalog list endpoints respond under 500 ms for 10,000 books on a local machine (indexes on category, brand, title).
- **Observability**: Spring Actuator, structured logs with a request ID.
- **Portability**: the whole stack runs with one Podman command on any machine.
- **Documentation**: README with run instructions, `docs/` with architecture and data model, Swagger UI available.

## 10. UI Requirements (Carbon Design System)

- Use `@carbon/react` components and Carbon tokens and themes; no custom design system.
- Screens: Login, Home (with order history and Buy Again), Catalog by category and brand, Product detail (delivery date, related products), Cart, Checkout (address, payment, gift points), Order confirmation, Order history.
- Accessibility: follow Carbon accessibility defaults (keyboard navigation, labels, contrast).
- The UI consumes only the documented REST API.

## 11. Deliverables

1. `requirements.md` (this file).
2. `docs/data-model.md` with ER diagram.
3. `api/openapi.yaml`.
4. Spring Boot backend (modular monolith) with Flyway migrations and seed data.
5. `compose.yaml`, `Containerfile`, `.env.example`.
6. Automated tests (hand-written and AI-generated), with a short note on which agent generated what.
7. React and Carbon UI.
8. Git history on a feature branch and a Pull Request with API documentation.

## 12. Acceptance Criteria

- `podman compose up --build` starts a working system from a clean checkout.
- A user can: browse by category and brand, view a product with delivery date and related items, add to cart, check out with an address and gift points, and see the order in history.
- Cancelling at 47 hours succeeds; cancelling after 48 hours returns 409 or 422 with a clear error code.
- Buy Again re-adds items using current prices.
- Guests cannot place orders.
- Every endpoint in `openapi.yaml` is implemented and responses match the schema.
- All tests pass in CI or locally with one command.

## 13. Out of Scope

Real payment processing, real carrier integrations, email or SMS notifications, multi-currency conversion, multi-language content, admin UI beyond API endpoints, and cloud deployment (see 13.1).

### 13.1 Optional requirement: cloud deployment

The author has **no access** to AWS ROSA or IBM ROKS, so cloud deployment is **not part of the deliverables or acceptance criteria**. The project must remain deployable from container images so that it could be moved to a cloud later. If cloud access becomes available, a follow-up task may add Kubernetes or OpenShift manifests. Do not generate cloud-specific files unless explicitly asked.

## 14. Decisions Log

Confirmed by the author:
- Java 21 is approved by the course material.
- Project `bookstore`, `groupId` `com.bookstore`, base package `com.bookstore.ecommerce`.
- Single repository with `backend/`, `frontend/`, `api/`, `docs/`.
- Currency USD; UUID primary keys; all content in English.
- Multi-store model, one seeded store "Main Store".
- Seeded `ADMIN` and `CUSTOMER` development users, documented as local-only.
- Personal GitHub account for the repository and Pull Request.
- No cloud deployment (no ROSA or ROKS access); tracked as optional requirement 13.1.
- ICA agent for tests: API Testcase generator.

Remaining assumptions (kept unless changed here):
- Guests can build a cart but must log in to check out.
- Gift points convert at 100 points = 1 USD and do not expire.
- Spring Boot 3.x and a modular monolith architecture.