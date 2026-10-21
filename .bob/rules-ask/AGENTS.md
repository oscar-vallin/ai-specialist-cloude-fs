# Project Documentation Rules (Non-Obvious Only)
- [`requirements.md`](../../requirements.md) is the only source of truth; `docs/architecture.png` is the functional-domain flow (Member, Store, Catalog, Order, Payment, Shipping).
- The "books API" is this project's own API served from PostgreSQL, not an external service.
- The brief's MySQL/H2 mentions do not apply: PostgreSQL only. Containers are Podman, not Docker.
- No code exists yet; answers about implementation should refer to planned design in requirements.md.
