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
├── docs/
└── services/
    └── work-service/      ← Maven commands run from HERE, not the root
```

## Source of truth
- `docs/PROJECT.md` — milestones. Work ONLY on the current one.
- `docs/work-management-class-diagram-v2.mermaid` — the domain model. Follow it exactly.
- `docs/microservices-architecture.mermaid` — target architecture.

## Who writes code
**I write the code. You scope it and quiz me.**
- Give me a plan in 3-5 lines. Wait for my OK before I start.
- Do not write code for me. Give me the commands and the file content, I type them.
- Ask before adding any dependency.

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

## After every task (mandatory)
1. Say what was built and why, in plain words
2. List the 2-3 choices made and what was rejected
3. Ask me 3 questions to check I understood. Wait for my answers.
