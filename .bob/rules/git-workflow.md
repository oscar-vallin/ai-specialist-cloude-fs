# Git workflow

- Single monorepo `bookstore`: `backend/`, `frontend/`, `api/`, `docs/`.
- Work on feature branches (first one: `feature/api-implementation`); never commit directly to the main branch.
- Small commits with English messages in the imperative mood, one logical change each.
- Open a Pull Request with API documentation (link to the OpenAPI changes).
- Never commit `.env`, secrets, `target/`, `node_modules/` or IDE files. `.env.example` is committed.
- Flyway migrations are append-only: never edit a migration that was already committed.
- Do not modify `requirements.md` unless asked.
- Do not add cloud (Kubernetes/OpenShift) files unless explicitly asked.
