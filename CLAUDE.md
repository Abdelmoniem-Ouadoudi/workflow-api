# CLAUDE.md

## How to talk to me
- Short answers. Simple words. No filler.
- Stay on the subject I asked about. Do not add side topics.
- No long lists of options. Give one recommendation.
- Do not repeat what I already know.

## Context
PFA (year-end school project), defended in front of a jury.
I must be able to explain every line and every choice.
Simple code that I understand beats clever code.

## Stack (locked — never change without asking)
- Java 21, Maven
- Spring Boot 4.0.7
- Spring Data JPA + PostgreSQL
- Liquibase for the schema (`ddl-auto=validate`, never `update`)
- MapStruct for entity <-> DTO
- Spring Cloud 2025.1.1 (Gateway + Eureka) — from M2
- Spring AI 2.0.x with Groq (OpenAI-compatible URL, JSON mode) — from M3
- RabbitMQ — from M3
- pgvector — from M4
- React + Vite frontend
- Docker Compose for local infra. No Kubernetes.

Note: Spring AI 1.x and Spring Cloud 2025.0.x do NOT work with Boot 4. Ignore tutorials that use them.

## Repo layout
```
workflow-api/
├── docker-compose.yml
├── CLAUDE.md
├── docker/postgres-init/  ← creates the second database, authdb (runs only on an empty volume)
├── docs/
├── frontend/              ← React + Vite, dev server on 5173
├── scripts/smoke-test.sh  ← runs against the GATEWAY on 8090, not a service directly
└── services/
    ├── work-service/           ← Maven commands run from HERE, not the root
    ├── discovery-service/      ← Eureka registry
    ├── auth-service/           ← accounts, passwords, JWT
    ├── gateway/                ← the one address the browser knows
    └── classification-service/ ← reads a ticket, suggests what it is
```

Ports: work-service **8081**, discovery-service (Eureka) **8761**, auth-service **8082**,
classification-service **8083**, gateway **8090**, Postgres **5433**,
RabbitMQ **5672** (management UI **15672**, workflow/workflow), Vite **5173**.
8080 and 5432 are avoided because a local Apache and a local Postgres already use them.

Databases, all in one Postgres process (image `pgvector/pgvector:pg16` since M4):
`workflow` (work-service), `authdb` (auth-service), `vectordb` (classification-service).
`vectordb` is the only one that can be thrown away — rebuild it with `POST /admin/issues/reindex`.

**Start order:** discovery → work → auth → classification → gateway. Registration takes up to
~30s to propagate; `http://localhost:8761` must list all four before the gateway can route.

**No Groq key?** `app.classification.provider` defaults to `stub`, which is keyword rules, not AI.
It says so on startup and stamps `modelVersion=stub-v1` on every suggestion. For the real thing:

```
$env:GROQ_API_KEY = "gsk_..."        # never in a file
$env:CLASSIFICATION_PROVIDER = "groq"
```

Model is `openai/gpt-oss-120b` with `reasoning-effort=low`. `llama-3.3-70b-versatile` is NOT on
Groq — check `GET /v1/models` before believing any tutorial. The free tier allows **8000 tokens
per minute**; a burst of tickets exceeds it, the 429s retry, and anything that outlives its
retries parks in the DLQ for replay.

**Embeddings need no key.** `all-MiniLM-L6-v2` runs in-process as ONNX — Groq has no embeddings
endpoint. The **first** start of classification-service downloads ~80MB and looks like a hang;
every start after that is cached and offline. Run it once with internet before a demo.

## Source of truth
- `docs/PROJECT.md` — milestones. Work ONLY on the current one.
- `docs/work-management-class-diagram-v2.mermaid` — the domain model. Follow it exactly.
- `docs/microservices-architecture.mermaid` — target architecture.
- `docs/DATA-MODEL.md` — the tables, their columns and every foreign key.
- `docs/ARCHITECTURE-NOTES.md` — binding rules for M2/M3 (correlation id, DLQ, circuit breakers)
  and the decisions to state out loud at the defence. Apply these when building each service.
- `docs/services/` — one page per service, plain English, with diagrams. Revision material for the
  defence, not a spec. If a service changes, its page changes too.

## Who writes code
**You write the code. Do not stop to teach me.**
- Say what you will do in 3-5 lines, then build it. Do not wait for approval on small steps.
- No quizzes. No comprehension questions. I will learn it later from the docs.
- Ask before adding any dependency, and before anything that changes the stack.

## Code rules
- Package per feature: `controller / dto / service / service.impl / repositories / models`
- Controller depends on the interface `IXxxService`, never the impl
- Constructor injection with `private final`. No `@Autowired` on fields.
- DTOs at the API boundary. Never return a JPA entity.
- Entities hold data only. No methods, no logic.
- Every `@ManyToOne` must be `FetchType.LAZY` (the default is EAGER)
- No `@OneToMany` until a query needs it
- Enums stored as `@Enumerated(EnumType.STRING)`, never ORDINAL
- Validation on inputs. One error shape via `@RestControllerAdvice`.
- Liquibase: never edit a changeSet that already ran. Add a new file.
- Liquibase master uses explicit `include:`, never `includeAll` (it sorts alphabetically).
- Table/column names in `snake_case`. Table `app_user`, not `user` (reserved in Postgres).
- Column `project_key`, not `key` (reserved in H2).
- Secrets from config, never in source.
- Small commits, one thing each.

## Quality bar — clean, no gaps
The backend must be finished at every step. Not a demo, not a sketch.
- Every endpoint validates its input. Every error returns the same JSON shape.
- No `TODO`, no commented-out code, no stub that returns null or an empty list.
- No endpoint left unhandled: a bad id, a duplicate, a bad enum value all return a proper status.
- Every entity has its migration, its constraints and its indexes in the same task.
- Constraints live in the database, not only in Java.
- If something must be deferred, write it in `docs/BACKLOG.md` with the reason. Never leave it silent.
- Do not move to the next entity until the current slice is complete and compiles.

## Tests
`mvn test` runs the unit tests and needs nothing running — no database, no broker. Seconds.
`mvn test -Pall-tests` also boots the application, which needs the whole stack up.

They cover the rules the schema and the framework cannot enforce: the transition map, the
auto-apply threshold, the agreement-rate arithmetic, which Groq failures are permanent, what goes
into a token. Not getters, not mappers.

`scripts/smoke-test.sh` is the other half — 153 cases through the gateway against a running system.

## After every task
1. Two or three lines: what was built, and the one decision that mattered.
2. Append the decision and its reason to `docs/QUIZ.md` so I can revise later.
3. Do not ask me questions. Move to the next task.
