---
name: openapi-first
description: Use when the user wants to create, extend or review the API contract (api/openapi.yaml) before writing any Spring Boot code, or asks to design endpoints, schemas or error responses for the bookstore.
---

# OpenAPI first

Contract-first workflow: the spec is the source of truth, code follows it.

1. Read `requirements.md` (sections 4, 5, 8) and `docs/data-model.md` if it exists. Read `.bob/rules/api-and-openapi.md`.
2. Read the current `api/openapi.yaml` (if any) with `read_file` before editing; never overwrite it blindly.
3. If the JSON naming style (`snake_case` or `camelCase`) is not yet decided, use `ask_followup_question` once, then record the decision in `docs/` and apply it everywhere.
4. Define or update, under the `/api/v1` server base path:
   - paths and operations per domain (member, store, catalog, cart, order, payment, shipping);
   - request/response schemas in `components/schemas`, separate from entity naming;
   - shared `Page` parameters (`page`, `size`, `sort`) and paginated response with totals;
   - a shared RFC 7807 `application/problem+json` error schema with a stable `code`;
   - `bearerAuth` JWT security scheme, and the required role per operation (description or `x-required-role`);
   - `Idempotency-Key` header on payment and order placement.
5. Use correct status codes: 201 create, 204 delete, 400, 401, 403, 404, 409, 422.
6. Validate the spec (for example with `execute_command` and an OpenAPI linter if available) and fix all errors.
7. Stop and ask the user to review the spec. Do not write controllers or DTOs until it is approved.
8. Never include external URLs to book APIs; books are served by our own `/products`, `/categories`, `/brands`.
