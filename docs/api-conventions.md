# API conventions

Decisions that apply to every endpoint in [`api/openapi.yaml`](../api/openapi.yaml). They close the open choices in `requirements.md` section 8 and `.bob/rules/api-and-openapi.md`.

| Topic | Decision |
|---|---|
| JSON field names | **camelCase** everywhere (request bodies, responses, query parameters, headers excluded). Chosen once because it is Jackson's default for Java, so no per-field mapping is needed. |
| Enum values | `UPPER_SNAKE_CASE`, identical to the values stored in the database (see `data-model.md` section 4). |
| Base path | `/api/v1`. All paths in the spec are relative to it. |
| Money | JSON **string** with exactly two decimals (`"12.50"`), never a JSON number, so no client can parse it as floating point. Currency is `USD` and is returned as `currency` on documents that carry totals. |
| Identifiers | UUID strings. |
| Dates and times | ISO 8601 in UTC. `date-time` for instants, `date` for delivery dates. |
| Countries | ISO 3166-1 alpha-2, upper case (`US`). Languages: ISO 639-1, lower case (`en`). |
| Pagination | `page` (0-based), `size` (default 20, max 100), `sort` (`field,asc` or `field,desc`). Paged responses contain `content`, `page`, `size`, `totalElements`, `totalPages`. |
| Errors | RFC 7807 `application/problem+json` with a stable machine-readable `code` (the `ErrorCode` enum in the spec) and a `requestId`. Clients must branch on `code`, never on `title` or `detail`. |
| Status codes | 201 on create, 204 on delete and logout, 400 malformed or invalid input, 401 not authenticated, 403 authenticated but wrong role, 404 not found (also used for resources owned by another user), 409 state or concurrency conflict, 422 request is well formed but violates a business rule. |
| Authentication | `Authorization: Bearer <JWT>`. Guests identify their cart with the `X-Guest-Token` header. |
| Roles | Every operation lists the roles allowed to call it in `x-required-role` (any of). `GUEST` means no authentication is required. |
| Idempotency | `Idempotency-Key` header on order placement (and later on payments). Replaying the same key and payload returns the original result. |
