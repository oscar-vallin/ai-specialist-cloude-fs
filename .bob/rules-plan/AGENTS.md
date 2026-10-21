# Project Architecture Rules (Non-Obvious Only)
- Workflow order is fixed: data model (manual, `docs/data-model.md`) -> `api/openapi.yaml` -> Spring Boot code. Do not plan code before the spec is reviewed.
- Modular monolith: domains interact only via service interfaces; no cross-domain repository access.
- Cloud deployment is explicitly out of scope (requirements.md 13.1); plan only Podman/local.
- Plan against the **[ASSUMPTION]** items in requirements.md; they are binding until changed in section 14.
