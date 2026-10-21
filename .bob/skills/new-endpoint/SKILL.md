---
name: new-endpoint
description: Use when the user wants to add or implement a REST endpoint in the bookstore backend (controller, service, repository, DTO, mapper, tests) from an operation already defined in api/openapi.yaml.
---

# New endpoint

1. Confirm the operation exists in `api/openapi.yaml`. If not, activate the `openapi-first` skill first and stop here until the spec is approved.
2. Read `.bob/rules/java-style.md`, `.bob/rules/security-and-payments.md` and `.bob/rules/testing.md`.
3. Identify the owning domain package under `com.bookstore.ecommerce.{member,store,catalog,order,payment,shipping}`. Use `grep` / `FindSymbol` to reuse existing classes.
4. If a table or column is needed, activate the `db-migration` skill first.
5. Implement, in this order:
   - request/response DTOs matching the spec schemas (Bean Validation on inputs);
   - MapStruct mapper between DTO and entity;
   - repository method (only if needed, within the owning domain);
   - service method with the business logic; call other domains only through their service interfaces;
   - controller method under `/api/v1` with the exact path, status code and role check from the spec.
6. Errors: throw domain exceptions mapped to RFC 7807 `application/problem+json` with the stable `code` from the spec.
7. Never trust client-sent prices or totals; recompute on the server.
8. Add tests: service unit tests (Mockito) and an integration test (Testcontainers PostgreSQL + MockMvc/REST-assured) covering success and each documented error status.
9. Run `./mvnw test` from `backend/` with `execute_command` (single test: `./mvnw test -Dtest=Class#method`) and fix failures.
10. Verify the implementation matches the spec (paths, schemas, status codes). Report any spec deviation instead of silently changing behavior.
