# Security and payments

- Passwords hashed with BCrypt. Authentication via JWT (Spring Security).
- Role check on every protected endpoint. Roles: `GUEST`, `CUSTOMER`, `ADMIN`. Guests may browse and hold a cart, but cannot check out.
- Secrets only through environment variables. `.env` is git-ignored; `.env.example` holds placeholders only. Never put secrets in code, seed data, `compose.yaml`, logs or `.bob/mcp.json`.
- Never log secrets, tokens or personal data.
- Server-side price validation: recompute item prices, shipping and discounts on the server; never trust client-sent prices.
- Payment goes through a gateway abstraction. Only a MOCK gateway exists; never process or store real card data.
- Payment methods: card (mock), wallet, gift points, or a combination. Gift points default to 100 points = 1 USD (configurable, no expiry).
- Refunds return to the original method; gift points and wallet are credited back.
- Idempotency key supported on payment and order placement.
- Seeded dev users (ADMIN, CUSTOMER) are for local development only and the seed must not run in a production profile.
