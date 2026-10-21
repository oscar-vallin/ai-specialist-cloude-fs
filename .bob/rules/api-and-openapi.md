# API and OpenAPI

- Contract-first: `api/openapi.yaml` (OpenAPI 3.x) is written and reviewed before code. Code must match it.
- All endpoints under `/api/v1`. JSON only.
- Choose `snake_case` or `camelCase` once for JSON fields and apply it everywhere (record the choice in `docs/`).
- Pagination: `page`, `size`, `sort`; list responses include total counts.
- Errors follow RFC 7807 (`application/problem+json`) with a stable error `code`.
- Status codes: 201 create, 204 delete, 400, 401, 403, 404, 409, 422.
- Every endpoint documents its required role.
- Idempotency key on payment and order placement.
- Resource map: see requirements.md section 8 (auth, users/me, stores, categories, brands, products, cart, orders, payments, shipping, shipments).
- Swagger UI via springdoc; health at `/actuator/health`.
- Books are served by our own `/products`, `/categories`, `/brands` endpoints from PostgreSQL.
