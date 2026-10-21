# Project Coding Rules (Non-Obvious Only)
- Use `./mvnw` (never system `mvn`) and `podman compose` (never `docker`); name files `Containerfile`, not `Dockerfile`.
- Schema changes only through new Flyway migrations; never rely on Hibernate auto-DDL or H2.
- Money: `BigDecimal` / `NUMERIC(12,2)`; IDs: `UUID`.
- Endpoint changes require updating `api/openapi.yaml` first.
- Seed users/data must be gated to local/dev profiles.
- Do not modify `requirements.md` unless asked.
