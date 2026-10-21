# Java style

- Java 21, Spring Boot 3.x, Maven via `./mvnw` only.
- Base package `com.bookstore.ecommerce`; one sub-package per domain: `member`, `store`, `catalog`, `order`, `payment`, `shipping`.
- Layers per domain: controller -> service -> repository. Controllers never touch repositories.
- DTOs are separate from JPA entities. Map with MapStruct; never expose entities in the API.
- Cross-domain calls go through the other domain's service interface only, never its repository or entities' tables.
- Validate inputs with Bean Validation on DTOs.
- Money is `BigDecimal` (column `NUMERIC(12,2)`), never `float`/`double`. IDs are `UUID`.
- Every entity has `created_at` and `updated_at`.
- Configuration through `application.properties` with environment overrides; no hard-coded secrets, URLs or currency (`app.currency`).
- Everything in English: identifiers, comments, messages, logs.
- Do not log secrets or personal data. Include a request ID in structured logs.
