---
name: db-migration
description: Use when the user wants to create or change database schema for the bookstore (new table, column, index, constraint) with a Flyway migration on PostgreSQL.
---

# Database migration

1. Read `docs/data-model.md` (if present) and the existing migrations in `backend/src/main/resources/db/migration` with `list_files`.
2. Never edit a migration that is already committed or applied. Always add a new one.
3. Name the file `V<next_version>__<snake_case_description>.sql` (next version = highest existing + 1). Reference data goes in versioned migrations; seed data does NOT (use the `seed-data` skill).
4. Follow the data conventions:
   - primary key: `UUID` on every table;
   - money: `NUMERIC(12,2)`, never floating point;
   - `created_at` and `updated_at` (`TIMESTAMPTZ NOT NULL`) on every table;
   - explicit foreign keys, NOT NULL and CHECK constraints (for example quantity > 0, status values);
   - indexes for catalog performance: category, brand, title.
5. Keep cross-domain references to IDs only; do not couple domains through shared tables beyond foreign keys required by the model.
6. Hibernate auto-DDL stays off (`spring.jpa.hibernate.ddl-auto=validate` or `none`). Update JPA entities to match the schema.
7. Verify by running the integration tests (Testcontainers PostgreSQL) with `execute_command`: `./mvnw test` from `backend/`. The migration must apply cleanly on an empty database.
8. Update `docs/data-model.md` if the model changed.
