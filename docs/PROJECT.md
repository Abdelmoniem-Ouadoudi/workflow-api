# PROJECT.md — Work Management Platform (PFA)

## What this is
Year-end academic project (PFA): a Jira-like work management platform with an AI layer that classifies incoming issues (type, priority, team, effort, sentiment), detects duplicates, and flags missing information. Solo developer. Defended orally before a jury — every design decision must be explainable.

## Source of truth
- `docs/work-management-class-diagram-v2.mermaid` — domain model. Follow entities and relationships exactly; model changes happen in this file first.
- `docs/microservices-architecture.mermaid` — target architecture (gateway, auth, work, classification services; RabbitMQ; Eureka; PostgreSQL + pgvector; Groq API).

## Principles
- Features before infrastructure: a piece of infrastructure enters only at the milestone that needs it.
- The app is demoable at the end of every milestone.
- AI suggests, humans confirm: suggested values live in `AIClassification`, confirmed values on `Issue`.

## Milestones — work strictly in order

### M0 — Skeleton
Mono-repo layout: `services/work-service`, `frontend/`, `docs/`, `docker-compose.yml` (PostgreSQL only).
**Done when:** `docker compose up` runs Postgres; work-service boots; `/actuator/health` reports UP.

### M1 — Core domain (single service)
All entities from the class diagram **except** `AIClassification`, with JPA + REST CRUD. React Kanban board: columns by `Status`, drag & drop calling `PATCH /issues/{id}/status`.
**Done when:** create a project → add issues → drag them across To Do / In Progress / Done; data survives a restart.

### M2 — Auth + edge (this is where it becomes microservices)
`auth-service` (register/login, JWT via Spring Security), Spring Cloud Gateway (routing + JWT validation), Eureka registry.
**Done when:** unauthenticated requests are rejected at the gateway; login from the React app works end-to-end.

### M3 — AI classification (async)
RabbitMQ. `classification-service` consumes `issue.created`, calls Groq (Spring AI, OpenAI-compatible base URL, JSON mode), publishes `issue.classified`; work-service persists `AIClassification` and auto-applies above the confidence threshold. UI: suggestion chip with accept / override. Resilience4j retry + circuit breaker on the Groq call.
**Done when:** creating a ticket returns instantly and the suggestion appears seconds later; with the Groq key removed, tickets still get created and classification catches up once the key is restored.

### M4 — Similarity + insights
Embeddings + pgvector. "Possible duplicate" panel at ticket creation. Dashboard: type/component distribution, team load, and AI agreement rate computed from `ReviewStatus`.
**Done when:** a near-duplicate ticket surfaces the existing one before submit; the dashboard renders live metrics.

### M5 — Access control: who exists, and who sees what
Two levels of role, because one cannot express both questions. **Global** (`app_user.role`, in the
JWT): what you are on the platform — ADMIN approves accounts, MANAGER may create a project,
DEVELOPER joins them. **Per project** (`project_member.role`, in the database, never in a token):
`PROJECT_MANAGER` — the *chef de projet* — or `MEMBER`.

Registration loses its `role` field and becomes a request: the account is `PENDING` until an
administrator approves it and says what it is. Whoever creates a project is its first project
manager, and manages its people themselves — so the administrator is not the bottleneck for every
new person on every project. A project carries a secret `join_code` its manager can mail; the
holder can ask to join, and the manager decides. React gains a router, because a mailed link is a
URL.

**Done when:** a new person registers, cannot log in, is approved by the admin, and sees no
projects at all; they paste a join code, are accepted by that project's manager, and see exactly
that one — and `/projects/{someone-elses-id}/board` answers 403 rather than rendering.

### Stretch (only after M5)
Notification service (WebSocket/email), missing-info bot, RAG-drafted first responses.
