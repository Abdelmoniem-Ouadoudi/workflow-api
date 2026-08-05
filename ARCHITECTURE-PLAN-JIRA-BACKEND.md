# Jira-style Work Management API — Architecture & Build Plan

A Spring Boot backend for the domain in `work-management-class-diagram.mermaid`, built on the `ziara` architecture, designed to be consumed by a React SPA.

**Working mode:** **I write the code. You review it.** Each step tells you what I'm building, *what the dependency in focus actually does inside the running application*, and exactly what to look at when you read the result. You are learning by reading and interrogating, not by typing.

**Ask me "why" at any step.** The plan is written so that every dependency is introduced at the moment its absence hurts, never as a checkbox at the start.

> Supersedes `ARCHITECTURE-PLAN-WORK-MANAGEMENT.md` (which was written in the older "you write the code" mode and had no React contract).

---

# Part 0 — Spring Initializr

Go to <https://start.spring.io/>.

| Field | Value | Why |
|---|---|---|
| Project | **Maven** | ziara is Maven; Gradle would break every pom snippet here |
| Language | **Java** | — |
| Spring Boot | **3.5.x** (latest 3.5 patch) | ziara runs 3.5.14 — same minor |
| Group | `ma.dev` | mirrors ziara's `ma.gov.dgapr` |
| Artifact | `workflow-api` | — |
| Name | `workflow-api` | — |
| Description | `Work management and issue tracking API` | — |
| Package name | `ma.dev.workflow` | **every path in this doc assumes this** |
| Packaging | **Jar** | ziara uses jar + `SpringBootServletInitializer` for optional WAR deploy |
| Java | **17** | you have 17.0.1; matches ziara |

### Select these five

| Dependency | Maven artifact | What it drags onto the classpath |
|---|---|---|
| **Spring Web** | `spring-boot-starter-web` | `spring-webmvc`, embedded **Tomcat**, **Jackson** (`jackson-databind` + `jackson-datatype-jsr310`) |
| **Spring Data JPA** | `spring-boot-starter-data-jpa` | **Hibernate ORM 6**, `spring-data-jpa`, `spring-orm`, `spring-tx`, **HikariCP** connection pool |
| **PostgreSQL Driver** | `postgresql` (runtime scope) | the JDBC driver only — no Spring code at all |
| **Liquibase Migration** | `liquibase-core` | migration engine + its Spring Boot auto-configuration |
| **Lombok** | `lombok` (provided scope) | a **compile-time annotation processor**. Nothing at runtime |

### Deliberately NOT selected — and exactly when each arrives

| Dependency | Arrives at | Why not now |
|---|---|---|
| **Validation** | Step 5 | So you can watch a bad request produce an ugly 500 *first*, then see what `@Valid` changes |
| **springdoc-openapi** | Step 6 | Not on Initializr. Added by hand. It's the contract your React app codegens from |
| **MapStruct** | Step 3 | **Not on Initializr at all** — hand-added to `pom.xml` |
| **Spring Security** | Step 7 | Added at init it locks every endpoint immediately; you'd spend Steps 2–6 fighting 401s instead of seeing the layering |
| **Spring AOP** | Step 12 | — |
| **DevTools** | never | Auto-restart hides *when* the app reloads — bad while learning the boot sequence |

Download the zip, extract to `C:\Users\a.ouadoudi\Desktop\workflow-api`, and put this file in the project root.

---

# Part 1 — The architecture

## Shape: modular monolith, package-by-feature, layered inside each feature

There is **no** global `controllers/` folder. Every feature owns its whole stack:

```
src/main/java/ma/dev/workflow/
├── project/                        ← one feature = one vertical slice
│   ├── controller/ProjectController.java
│   ├── service/IProjectService.java            ← interface
│   ├── service/impl/ProjectService.java        ← implementation
│   ├── repositories/ProjectRepository.java
│   ├── models/Project.java                     ← JPA entity
│   ├── dto/ProjectDTO.java
│   ├── dto/mapper/ProjectMapper.java           ← MapStruct
│   └── exception/ProjectValidationException.java
├── board/  sprint/  issue/  issue_comment/
├── issue_attachment/  issue_resolution/  user/  ui_label/
└── common/                         ← the ONLY cross-cutting package
    ├── controllers/AbstractBaseRestController.java
    ├── service/IBaseService.java + AbstractBaseService.java
    ├── dto/BaseDTO.java + PageDTO.java + dto/mapper/IBaseMapper.java
    ├── config/  security/  i18n/  aspect/  exception/
```

Package names in ziara are `snake_case` (`appointment_rejection`, `ui_label`, `meeting_setting`). We keep that — consistency with the reference codebase beats Java convention here.

## The six rules that hold it together

| # | Rule | How it's enforced |
|---|---|---|
| 1 | Controller depends on an **interface** (`IXxxService`), never the impl | folder split: `service/` = interfaces, `service/impl/` = classes |
| 2 | **Entities never cross the HTTP boundary** — DTOs do | `dto/` + MapStruct `@Mapper(componentModel = "spring")` |
| 3 | **CRUD is inherited, not retyped** | `AbstractBaseRestController<T extends BaseDTO>` |
| 4 | **Role authorization is data, not annotations** | `routes_configuration.yml` maps URL pattern + method → roles |
| 5 | **Schema is migrations only** — `ddl-auto` is never used | Liquibase master changelog → `changes/<sprint>/<version>-<desc>.yml` |
| 6 | **The API contract is generated, not documented by hand** | springdoc-openapi → React codegens its client from `/v3/api-docs` |

Rule 6 is new — ziara has no SPA contract. It exists because a hand-maintained API doc and a React app drift apart within two weeks.

## Three things in ziara we deliberately do NOT copy

1. **Field injection.** ziara uses `@Autowired` on fields. We use **constructor injection** (`private final`). Field-injected classes can't be built in a plain unit test without a Spring context, and they hide that a class has twelve dependencies.
2. **`RoutesConfig` with 25 hand-maintained role lists.** ziara declares `List<RouteConfig> adminURLs`, `agentURLs`, `economeURLs`… then a 60-line method stitching them into a map. We use one `Map<String, List<RouteConfig>>` — same behaviour, adding a role costs zero Java changes.
3. **`BaseDTO` importing `OrderViews`.** ziara's shared base DTO imports a Jackson view from the *orders* feature, coupling every DTO in the app to one feature. **Shared kernel depends on nothing; everything depends on it.**

---

# Part 2 — Reading the class diagram into this architecture

Your `.mermaid` is a **domain model sketch, not an implementation spec.** Three systematic translations happen before any code. This is the section to argue with me about.

## 2.1 — Methods on the diagram are NOT methods on the entity

The diagram puts `login()`, `addMember()`, `moveIssue()`, `start()` on the classes. In this architecture entities are **anemic data holders**: fields, JPA annotations, nothing else. No repository access, no rules, no `@Autowired`.

| Diagram method | Where it actually lives | Step |
|---|---|---|
| `User.login()` | `common/security/filter/JWTAuthenticationFilter` — not a domain method at all | 7 |
| `User.logout()` | **nowhere.** Stateless JWT has no server-side logout. Decision in Step 7 | 7 |
| `Project.addMember/removeMember` | `IProjectService.addMember(Long projectId, Long userId)` | 7 |
| `Board.addIssue(issue)` | `IIssueService.assignToBoard(Long issueId, Long boardId)` | 8 |
| `Board.moveIssue(issue, status)` | `IIssueService.updateStatus(Long issueId, Status)` | 11 |
| `Sprint.start()` / `complete()` | `ISprintService.start(Long id)` / `complete(Long id)` | 11 |
| `Issue.assignTo(user)` | `IIssueService.assign(Long issueId, Long userId)` | 8 |
| `Issue.updateStatus(newStatus)` | same method as `moveIssue` — the diagram lists it twice because two *actors* trigger it, not because there are two operations | 11 |
| `Comment.edit(newContent)` | `IIssueCommentService.update(...)` | 8 |

## 2.2 — Multiplicities and arrow types are schema decisions

`*--` composition → child cannot exist without parent → `NOT NULL` FK + `ON DELETE CASCADE`.
`o--` aggregation → child survives parent → `NULL`able FK + `ON DELETE SET NULL`.

| Diagram association | JPA mapping (owning side) | Schema |
|---|---|---|
| `User "1..*" -- "0..*" Project : member of` | join table, no entity required | `project_member(project_id, user_id, joined_at)`, PK on both |
| `Project "1" *-- "0..*" Issue : contains` | `Issue.project` `@ManyToOne(optional=false)` | `issue.project_id NOT NULL`, FK `ON DELETE CASCADE` |
| `Project "1" -- "1..*" Board : has` | `Board.project` `@ManyToOne(optional=false)` | `board.project_id NOT NULL`. **`1..*` is not expressible in SQL** — "at least one board" is a service invariant: `ProjectService.create` also creates a default board |
| `Board "1" o-- "0..*" Issue : organizes` | `Issue.board` `@ManyToOne(optional=true)` | `issue.board_id NULL`, `ON DELETE SET NULL` |
| `Board "1" -- "0..*" Sprint : plans` | `Sprint.board` `@ManyToOne(optional=false)` | `sprint.board_id NOT NULL` |
| `Sprint "0..1" o-- "0..*" Issue : includes` | `Issue.sprint` `@ManyToOne(optional=true)` | `issue.sprint_id NULL` — **NULL is the backlog**, not a missing value |
| `User "0..1" -- "0..*" Issue : assignee` | `Issue.assignee` `@ManyToOne(optional=true)` | `issue.assignee_id NULL` |
| `User "1" -- "0..*" Issue : reporter` | `Issue.reporter` `@ManyToOne(optional=false)` | `issue.reporter_id NOT NULL` |
| `Issue "1" *-- "0..*" Comment` | `IssueComment.issue` `@ManyToOne(optional=false)` | `NOT NULL`, `ON DELETE CASCADE` |
| `Issue "1" *-- "0..*" Attachment` | `IssueAttachment.issue` `@ManyToOne(optional=false)` | `NOT NULL`, `ON DELETE CASCADE` |
| `User "1" -- "0..*" Comment : writes` | `IssueComment.author` `@ManyToOne(optional=false)` | `NOT NULL` |

**The trap to check me on:** `User` has **two** associations to `Issue` (assignee, reporter) plus one to `Comment`. Two FK columns to the same table. If I ever map the inverse `@OneToMany` on `User`, each needs its own `mappedBy` — omit it and Hibernate silently invents a third join table.

## 2.3 — Fields the diagram gets wrong or omits

| Diagram says | What I write | Why |
|---|---|---|
| `User.password: String` | column `password_hash`, BCrypt, **never in any DTO or response** | a password field on a DTO leaks the moment someone adds `GET /users/{id}` |
| `Attachment.fileSize: int` | `Long` / `bigint` | `int` caps at 2 GB |
| `Attachment.fileUrl` | metadata only — bytes go to disk/object storage | never a `bytea` column; it destroys backups and query performance |
| `*.createdAt: Date` | `LocalDateTime` + `@CreationTimestamp`, plus `updatedAt` on every mutable table | `java.util.Date` is mutable and timezone-ambiguous, and serializes badly to a React client |
| `Sprint` has dates, **no state** | add `state: SprintState { PLANNED, ACTIVE, COMPLETED }` | `start()`/`complete()` are meaningless without it — the diagram implies a state machine then omits the state |
| `Project.key` exists, `Issue` has no key | add `Issue.issueKey` (`PROJ-123`) | otherwise `Project.key` has no purpose; and it forces a real concurrency lesson (Step 8) |
| no optimistic locking | `Issue.version` (`@Version`) | two users dragging the same card is the normal case in this UI, not an edge case |
| no soft delete | `isActive` on `Project`, `Board`, `User` via `BaseDTO.active` | matches ziara and the Step 4 base classes |

## 2.4 — Reserved-word collisions: renamed now, not later

| Diagram name | Table/column I write | Where the original breaks |
|---|---|---|
| `User` | table **`app_user`** | `user` is reserved in PostgreSQL — `SELECT * FROM user` returns the session user, not your table |
| `Project.key` | column **`project_key`** | `KEY` is reserved in **H2**, which Step 13 adds for tests. Postgres migration passes, test profile fails |
| `Comment` | table **`issue_comment`**, class `IssueComment` | `COMMENT` is a DDL keyword; and a Java class named `Comment` collides with too many imports |

Java class names stay `User` and `Project`; only `@Table`/`@Column` names change.

---

# Part 3 — What "ready for a React frontend" actually means

This is the part the ziara booking plan never had. Ten concrete obligations. Every step below serves this contract.

| # | Obligation | Delivered in |
|---|---|---|
| 1 | **CORS configured on the security chain**, not with scattered `@CrossOrigin` | Step 6, corrected in Step 7 |
| 2 | **Preflight `OPTIONS` is permitted without a token** | Step 7 |
| 3 | **Stable error envelope** every React error handler can parse | Step 5 |
| 4 | **ISO-8601 dates**, not numeric arrays | Step 6 |
| 5 | **OpenAPI 3 spec at `/v3/api-docs`** so React codegens a typed client | Step 6 |
| 6 | **Paginated list endpoints** with a stable page shape | Step 9 |
| 7 | **Filter + sort query params** on the endpoints a table renders | Step 9 |
| 8 | **Multipart upload** for attachments | Step 8 |
| 9 | **409 on concurrent edit**, so the UI can say "reload, someone changed this" | Step 11 |
| 10 | **A declared token-storage strategy**, with its risks written down | Step 7 |

### The React dev-server assumption

Vite defaults to `http://localhost:5173`; Create React App to `http://localhost:3000`. Both are allowed origins in the dev profile. Backend runs on `http://localhost:8080` with `server.servlet.context-path=/api`, so every URL is `http://localhost:8080/api/...`.

> **The single most common bug in this stack, stated now so you recognise it in Step 7:** when Spring Security rejects a request *before* the CORS filter runs, no `Access-Control-Allow-Origin` header is written, and the browser reports a **CORS error** — hiding the fact that it is really a 401. You will chase a CORS misconfiguration that does not exist. The fix is that CORS must be registered *inside* the `SecurityFilterChain` (`http.cors(...)`), not as a standalone `WebMvcConfigurer`.

---

# Part 4 — Build steps

Each step: **Goal → Dependency in focus (what it actually does) → What I write → What to look at → Acceptance.**

---

## Step 0 — Boot the skeleton and read what auto-configured

**Goal:** see the machinery before adding anything to it.

**Dependency in focus: `spring-boot-starter-web`.**

What it actually does, in order, at startup:

1. `SpringApplication.run` detects `spring-webmvc` on the classpath → creates an `AnnotationConfigServletWebServerApplicationContext` instead of a plain one.
2. `ServletWebServerFactoryAutoConfiguration` finds `TomcatServletWebServerFactory` → **embedded Tomcat starts**. No `server.xml`, no WAR.
3. `DispatcherServletAutoConfiguration` registers a `DispatcherServlet` mapped to `/`. Every HTTP request now enters through this one servlet.
4. `WebMvcAutoConfiguration` registers `RequestMappingHandlerMapping` — the thing that scans `@RestController` classes and builds the URL→method table.
5. `JacksonAutoConfiguration` builds the `ObjectMapper`; `HttpMessageConvertersAutoConfiguration` wraps it in a `MappingJackson2HttpMessageConverter`. **That converter is the only reason returning a Java object from a controller produces JSON.**

**What I write:** nothing yet. We run it:

```
mvn spring-boot:run
```

**Expect it to FAIL** with a datasource error. You selected JPA + Postgres and there is no database. That failure is the lesson: `DataSourceAutoConfiguration` is *already* trying to build a connection pool, before you have written a single line.

Then we run it with the report on:

```
mvn spring-boot:run "-Dspring-boot.run.arguments=--debug"
```

This prints the **CONDITIONS EVALUATION REPORT** — every auto-configuration Spring considered, which matched, which didn't, and *why*. This report is the answer to "how does this dependency work" for the entire rest of the plan. We will come back to it at every step.

**What to look at:** find `DataSourceAutoConfiguration` in the "Positive matches" section and read its condition. Find one entry in "Negative matches" and explain to me why it didn't fire.

**Acceptance:** you can name which auto-configuration failed and why, without me telling you.

---

## Step 1 — Postgres, Liquibase, the first migration

**Goal:** database running, Liquibase owning the schema, `ddl-auto` disabled forever.

**Dependencies in focus: `postgresql`, `liquibase-core`.**

- **`postgresql`** contains *no Spring code*. It ships a `META-INF/services/java.sql.Driver` file; the JDK's `ServiceLoader` registers `org.postgresql.Driver` automatically. HikariCP picks it purely from the `jdbc:postgresql:` URL scheme. This is why the dependency is `runtime` scope — you never import from it.
- **`liquibase-core`** + `LiquibaseAutoConfiguration` creates a `SpringLiquibase` bean. The critical detail: Boot makes the `EntityManagerFactory` **depend on** that bean, so migrations always run *before* Hibernate validates the schema. `[Likely]` the class doing this is `LiquibaseAutoConfiguration.LiquibaseJpaDependencyConfiguration` — check it in the debug report and correct me if the name differs in 3.5.x.

**What I write:**

1. `docker-compose.yml`:
```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: workflow
      POSTGRES_USER: workflow
      POSTGRES_PASSWORD: workflow
    ports: ["5432:5432"]
    volumes: ["workflow_pgdata:/var/lib/postgresql/data"]
volumes:
  workflow_pgdata:
```

2. `src/main/resources/application.properties`:
```properties
server.port=8080
server.servlet.context-path=/api

spring.datasource.url=jdbc:postgresql://localhost:5432/workflow
spring.datasource.username=workflow
spring.datasource.password=workflow

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
```

> **`ddl-auto=validate` is the load-bearing line.** Hibernate *checks* the schema against the entities and refuses to start on mismatch, but never creates or alters anything. Liquibase is the only thing allowed to change the database. ziara does exactly this.
>
> **`open-in-view=false` matters more here than in ziara.** Left at its default `true`, Hibernate keeps a session open through JSON serialization, so lazy associations silently load *during* response writing — and Step 10's board view would fire 200 queries while Jackson walks the object graph, with nothing in your logs pointing at the cause.

3. `db/changelog/db.changelog-master.yaml` — an include list, nothing else.

4. `db/changelog/changes/1/1.0.1-create-project-table.yml`:

| Column | Type | Constraints |
|---|---|---|
| `id` | bigserial | PK |
| `project_key` | varchar(10) | not null, **unique** |
| `name` | varchar(150) | not null |
| `description` | text | |
| `is_active` | boolean | not null, default true |
| `created_at` / `updated_at` | timestamp | not null |

**Convention (ziara's):** `changes/<sprint>/<major>.<minor>.<seq>-<kebab-desc>.yml`. Every changeSet has a unique `id` + `author`. **Never edit a changeSet that has already run** — Liquibase stores its MD5 checksum in `databasechangelog` and refuses to start on a mismatch. Always add a new file.

**What to look at:** after the first run, `SELECT id, author, filename, md5sum FROM databasechangelog;`. That table *is* the mechanism — there's no magic.

**Acceptance:** `mvn spring-boot:run` starts clean; `docker compose exec db psql -U workflow -d workflow -c '\dt'` lists `project`, `databasechangelog`, `databasechangeloglock`.

---

## Step 2 — First vertical slice, deliberately crude

**Goal:** one feature end to end, with **no abstractions**. We add those only once you've seen the duplication they remove.

**Dependency in focus: `spring-boot-starter-data-jpa`.**

The thing worth understanding: `ProjectRepository` is an **interface with no implementation**, and `findByKey` has no body. Here's what fills it in:

1. `JpaRepositoriesAutoConfiguration` scans for interfaces extending `Repository`.
2. For each, `JpaRepositoryFactory` creates a **JDK dynamic proxy**.
3. Calls to inherited methods (`findAll`, `save`, `findById`) route to **`SimpleJpaRepository`** — a real class you can open and read.
4. Calls to *derived* methods (`findByKey`) route to **`PartTreeJpaQuery`**, which parses the method **name** at startup — `findBy` + `Key` → property `key` → builds a Criteria query. It resolves at boot, not per call: **misspell a property and the application fails to start**, which is deliberate.

**What I write** in `ma/dev/workflow/project/`:

- `models/Project.java` — `@Entity`, `@Table(name="project")`, `@Data` + `@EqualsAndHashCode(callSuper=false)`, `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`, `@Column(name="project_key") private String key;`, `@CreationTimestamp`/`@UpdateTimestamp`. **No `@OneToMany`** — there is no query that needs one yet.
- `repositories/ProjectRepository.java` — `extends JpaRepository<Project, Long>` plus `Optional<Project> findByKey(String key)`.
- `service/IProjectService.java` — `List<Project> findAll()`, `Project findById(Long id)`, `Project create(Project p)`.
- `service/impl/ProjectService.java` — `@Service`, `@Transactional(readOnly = true)` **on the class**, `@Transactional` overriding it on `create`. Constructor injection.
- `controller/ProjectController.java` — `@RestController`, `@RequestMapping("/projects")`, returning the **entity directly**.

**Lombok, precisely:** it is an annotation processor that rewrites the AST during `javac`. `@Data` generates getters, setters, `equals`, `hashCode`, `toString` into the `.class` file. There is **no Lombok on the runtime classpath** — that's why its scope is `provided`. Decompile `target/classes/.../Project.class` and you'll see plain Java methods.

**What to look at:** interface/impl split; constructor injection (not `@Autowired` fields); class-level `readOnly = true` with write methods overriding; zero business logic in the controller.

**Acceptance:** `GET http://localhost:8080/api/projects` returns JSON; `POST` inserts a row.

**Then I'll show you what's wrong with it.** Returning the entity leaks your database schema to React — every column name becomes a frontend field name, so a migration renames a field in your UI. And when `Issue` arrives in Step 8, a lazy proxy will either explode during serialization or silently pull every issue in the project. That pain is Step 3's reason to exist.

---

## Step 3 — DTOs and MapStruct

**Goal:** close the entity leak. This is where `dto/` earns its place.

**Dependency in focus: MapStruct** (hand-added — not on Initializr).

```xml
<properties>
  <mapstruct.version>1.6.3</mapstruct.version>
</properties>

<dependency>
  <groupId>org.mapstruct</groupId>
  <artifactId>mapstruct</artifactId>
  <version>${mapstruct.version}</version>
</dependency>
```
and inside `maven-compiler-plugin`'s `<configuration>` — **order matters, Lombok first**:
```xml
<annotationProcessorPaths>
  <path>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>${lombok.version}</version>
  </path>
  <path>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>${mapstruct.version}</version>
  </path>
</annotationProcessorPaths>
```

**What MapStruct actually does:** it is a **compile-time** processor. It reads your `@Mapper` interface and writes a plain Java class `ProjectMapperImpl` into `target/generated-sources/annotations/`, annotated `@Component` (because of `componentModel = "spring"`), containing ordinary `dto.setName(entity.getName())` calls. **Zero reflection at runtime** — that is the entire argument for it over ModelMapper.

> **The ordering gotcha:** MapStruct reads *getters*. Lombok *creates* the getters. If MapStruct runs first, it sees a class with no getters and generates a mapper full of nothing. Symptom: every field comes back `null`. Fix: add a third path, `org.projectlombok:lombok-mapstruct-binding:0.2.0`.

**What I write:**
- `dto/ProjectDTO.java` — the fields the API exposes. Note what's *omitted* (internal timestamps, audit columns) — that omission is the whole point.
- `dto/mapper/ProjectMapper.java` — `@Mapper(componentModel = "spring")`, `ProjectDTO fromModel(Project m)`, `Project fromDTO(ProjectDTO d)`, plus `List` variants.
- Service interface and controller now speak `ProjectDTO` only. **The entity appears in no controller or service-interface signature.**

**What to look at:** open `target/generated-sources/annotations/ma/dev/workflow/project/dto/mapper/ProjectMapperImpl.java` and read it line by line. That generated file is exactly what you would otherwise hand-write and hand-maintain. Check how it handles `null` and how it handles the `List` overload.

**Acceptance:** `mvn compile` produces `ProjectMapperImpl`; the API response no longer contains `createdAt`/`updatedAt`.

---

## Step 4 — Extract `common/` (the shared kernel)

**Goal:** build a **second** slice, notice it's a retype of Steps 2–3, *then* extract the base classes. Abstraction arrives after the duplication, never before.

**What I write first — the `board/` slice, fully duplicated on purpose:**
- Migration `1.0.2-create-board-table.yml`: `id`, `name` varchar(150) not null, `type` varchar(20) not null, `project_id` bigint not null FK, `is_active`, timestamps.
- `models/enums/BoardType.java` — `KANBAN`, `SCRUM`, mapped `@Enumerated(EnumType.STRING)`. **Never `ORDINAL`** — reordering the enum silently corrupts every existing row.
- `models/Board.java` — `@ManyToOne(fetch = FetchType.LAZY, optional = false) private Project project;`
- `dto/BoardDTO.java` — exposes **`Long projectId`, not a nested `ProjectDTO`** (`@Mapping(source="project.id", target="projectId")`). First real DTO design decision: nesting forces a join on every read and lets React depend on your whole object graph.

**Then I extract into `common/`:**
- `dto/BaseDTO.java` — `implements Serializable`, `Long id`, `Boolean active`. **Imports nothing from any feature package.**
- `dto/mapper/IBaseMapper<D, M>` — `fromDTO`, `fromModel`, both `List` variants.
- `service/IBaseService<T>` — `findAll`, `findById`, `save`, `deleteById`.
- `service/AbstractBaseService<D extends BaseDTO, M>` — generic impl over `JpaRepository` + `IBaseMapper`.
- `controllers/AbstractBaseRestController<T extends BaseDTO>` — five CRUD endpoints.

Both slices then extend the base classes and the duplicated code is deleted.

**What to look at:** does `common/` import anything from a feature package? (rule violation). Are the generics right, or did I need an unchecked cast? Did I leave an override that the base already does?

**Acceptance:** both controllers shrink to a class declaration plus feature-specific endpoints. Both still respond identically.

---

## Step 5 — Validation and the error contract React parses

**Goal:** one consistent, documented error shape instead of Spring's whitelabel page.

**Dependency added now: `spring-boot-starter-validation`.**

What it does: brings **Hibernate Validator** (the reference implementation of Jakarta Bean Validation). `ValidationAutoConfiguration` registers a `LocalValidatorFactoryBean`. Then — and this is the part people don't know — **there are two separate mechanisms**:

| Where | Trigger | Mechanism | Exception thrown |
|---|---|---|---|
| `@Valid` on a controller `@RequestBody` | Spring MVC argument resolution | `RequestResponseBodyMethodProcessor` validates after deserialization | `MethodArgumentNotValidException` |
| `@Validated` on a class + constraints on method params | Spring AOP | `MethodValidationPostProcessor` creates a proxy | `ConstraintViolationException` |

They produce **different exception types**, so a handler for one does not catch the other. We use the first and I'll show you the second in Step 12.

**What I write:**
- `common/exception/ApiError.java` — the envelope: `timestamp`, `status`, `code`, `message`, `path`, `fieldErrors: [{field, message}]`.
- `common/exception/GlobalExceptionHandler.java` — `@RestControllerAdvice` mapping:

| Exception | Status | Why |
|---|---|---|
| `EntityNotFoundException` | 404 | |
| `MethodArgumentNotValidException` | 400 + `fieldErrors` | React binds these straight onto form fields |
| domain `*ValidationException` | 422 | business rule violated, request was well-formed |
| `DataIntegrityViolationException` | 409 | |
| `Exception` (catch-all) | 500 | **logs the stack trace, never returns it** |

- `project/exception/ProjectValidationException.java` — mirrors ziara's `VisitRoomValidationException`. First use: duplicate project key → 422 with a message the UI can show, not a raw Postgres constraint string.
- Constraints on the DTOs: `@NotBlank @Pattern(regexp="^[A-Z][A-Z0-9]{1,9}$")` on `key`, `@NotBlank @Size(max=150)` on `name`.

> **This `ApiError` shape is a contract.** Your React error interceptor will be written against it once. Changing its field names later is a frontend refactor, so we fix it now.

**What to look at:** does any response leak a stack trace, a SQL message, or a constraint name? Does the catch-all log before returning?

**Acceptance:** blank `key` → 400 with a per-field message. Duplicate `key` → 422 with our message. Neither is a 500.

---

## Step 6 — Making it callable from React: CORS, Jackson, OpenAPI

**Goal:** a React dev server can call this API and generate a typed client from it. Deliberately **before** security, so you see it work while the API is still open — then watch security break it in Step 7.

**Dependency added now: springdoc-openapi** (hand-added):
```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.7.0</version>   <!-- check for the latest 2.x -->
</dependency>
```

**What it actually does:** at startup it reads the same `RequestMappingHandlerMapping` that Spring MVC built in Step 0, walks every `@RestController` method, inspects the DTO types by reflection, and assembles an **OpenAPI 3 document** served at `/api/v3/api-docs`, plus a Swagger UI at `/api/swagger-ui.html`. It reads your `jakarta.validation` constraints too — `@NotBlank` becomes `required`, `@Size(max=150)` becomes `maxLength`. **Your Step 5 validation annotations are now frontend type information.**

That JSON is what React points `openapi-typescript` or `orval` at to generate a fully typed API client. This is Rule 6 in practice: the contract is generated, so it cannot drift.

**What I write:**

1. `common/config/CorsConfig.java` — a `CorsConfigurationSource` **bean** (not a `WebMvcConfigurer`, and not `@CrossOrigin` annotations):
```java
@Bean
CorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.allowed-origins}") List<String> origins) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(origins);
    c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    c.setAllowedHeaders(List.of("*"));
    c.setExposedHeaders(List.of("Authorization"));   // React must read the token
    c.setAllowCredentials(true);
    c.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return s;
}
```
> Registered as a **bean** specifically because Step 7's `SecurityFilterChain` will pick it up with `http.cors(Customizer.withDefaults())`. A `WebMvcConfigurer` would be invisible to Spring Security, which is the root of the bug described in Part 3.
>
> `setAllowCredentials(true)` and `setAllowedOrigins("*")` are **mutually exclusive** by spec — the browser rejects the combination. That's why origins come from config, per environment.

2. `application-dev.properties` — `app.cors.allowed-origins=http://localhost:5173,http://localhost:3000`.

3. Jackson configuration — verify rather than assume. Spring Boot **already** disables `WRITE_DATES_AS_TIMESTAMPS` and `FAIL_ON_UNKNOWN_PROPERTIES`, and auto-registers `JavaTimeModule` from `jackson-datatype-jsr310`. `[Likely]` — we'll confirm with a live response, then pin explicitly so a future Boot upgrade can't change your API shape:
```properties
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.default-property-inclusion=non_null
spring.jackson.time-zone=Africa/Casablanca
```
A `LocalDateTime` must serialize as `"2026-08-05T14:30:00"`. If you ever see `[2026,8,5,14,30]`, `JavaTimeModule` is missing and every date in React is broken.

4. `common/config/OpenApiConfig.java` — API title, version, and (in Step 7) the bearer security scheme.

**What to look at:** open `/api/swagger-ui.html` and execute a request from the browser. Then fetch `/api/v3/api-docs` and check that `ProjectDTO.key` carries `pattern` and `maxLength` — proof that Step 5's annotations reached the frontend contract.

**Acceptance:** a React app on `:5173` calls `GET /api/projects` with no CORS error; `/api/v3/api-docs` returns a complete spec; dates are ISO strings.

---

## Step 7 — Security: JWT chain + YAML authorization + the CORS collision

**Goal:** the distinctive ziara piece — authorization as configuration data, not annotations — plus everything an SPA needs to log in.

**Dependencies added:** `spring-boot-starter-security`, and `io.jsonwebtoken:jjwt-api` + `jjwt-impl` (runtime) + `jjwt-jackson` (runtime), version `0.12.6`.

**What Spring Security actually does when it appears on the classpath:**

1. `SecurityFilterAutoConfiguration` registers a servlet `Filter` named **`springSecurityFilterChain`**, wrapped in a `DelegatingFilterProxy`, ahead of the `DispatcherServlet`. **All security runs before Spring MVC ever sees the request** — that's the whole reason a 401 can be returned without any controller existing.
2. That proxy delegates to `FilterChainProxy`, which holds an **ordered list** of filters. Order is the entire game.
3. `SecurityAutoConfiguration` provides a default chain that secures everything. **Defining your own `SecurityFilterChain` bean replaces it.**

Relevant filter order for us:
```
CorsFilter  →  JWTAuthenticationFilter (/login)  →  JWTAuthorizationFilter  →  FilterSecurityInterceptor
```
`CorsFilter` **first** — that's what `http.cors(...)` installs, and why the CORS bean from Step 6 had to be a bean.

**jjwt** is not Spring at all — a plain library, split into three artifacts because of a service-provider pattern: `jjwt-api` is what you compile against; `jjwt-impl` and `jjwt-jackson` are loaded at runtime via `ServiceLoader`. Omit the runtime two and you get a confusing `ClassNotFoundException` at the first token operation, not at startup.

**What I write:**

1. Migrations `changes/2/2.0.1-create-app-user-table.yml` (`app_user`: `username` unique, `email` unique, `password_hash`, `role`, `is_active`, timestamps, seeded admin with a BCrypt literal) and `2.0.2-create-project-member-table.yml` (composite PK).

2. `user/` slice — `User`, `models/enums/Role.java` (`DEVELOPER`, `MANAGER`, `ADMIN`), `findByUsername`, `UserDTO` **with no password field**, `GET /users/me`.

3. `project/` gains `POST /projects/{id}/members` and `DELETE /projects/{id}/members/{userId}`.

4. `common/security/config/AppSecurityConfig.java`:
```java
http.cors(Customizer.withDefaults())          // ← picks up the Step 6 bean
    .csrf(csrf -> csrf.disable())             // safe ONLY because we use a header token, not a cookie
    .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
    .authorizeHttpRequests(...)               // driven by routes_configuration.yml
```

5. `common/security/filter/JWTAuthenticationFilter.java` — `POST /login`, verifies credentials, issues a signed JWT.
6. `common/security/filter/JWTAuthorizationFilter.java` — reads `Authorization: Bearer …`, validates, populates `SecurityContextHolder`.
7. `common/security/config/RouteConfig.java` (`pattern` + `method`, with `equals`/`hashCode`) and `RoutesConfig.java` holding `publicURLs`, `authenticatedURLs`, and **`Map<String, List<RouteConfig>> roleURLs`** — the corrected version of ziara's 25 hand-maintained lists.

8. `src/main/resources/security/routes_configuration.yml`:
```yaml
publicURLs:
  - { pattern: /**,          method: OPTIONS }   # ← see below. Non-negotiable.
  - { pattern: /login,       method: POST }
  - { pattern: /ui-labels,   method: GET }
  - { pattern: /v3/api-docs/**, method: GET }
  - { pattern: /swagger-ui/**,  method: GET }
authenticatedURLs:
  - { pattern: /users/me,      method: GET }
  - { pattern: /projects,      method: GET }
  - { pattern: /issues/**,     method: GET }
  - { pattern: /boards/*/view, method: GET }
roleURLs:
  ADMIN:
    - { pattern: /projects, method: POST }
    - { pattern: /users,    method: POST }
  MANAGER:
    - { pattern: /projects/*/members, method: POST }
    - { pattern: /boards,             method: POST }
    - { pattern: /sprints/*/start,    method: POST }
    - { pattern: /sprints/*/complete, method: POST }
  DEVELOPER:
    - { pattern: /issues,             method: POST }
    - { pattern: /issues/*/status,    method: PATCH }
    - { pattern: /issues/*/comments,  method: POST }
```

> ### The two things that will actually bite you here
>
> **1. Preflight.** Before any `PATCH`, or any request with an `Authorization` header, the browser sends an `OPTIONS` request **with no `Authorization` header**. If your YAML doesn't permit `OPTIONS`, Spring Security 401s it, no CORS headers get written, and Chrome reports *"blocked by CORS policy"*. You will spend an afternoon editing CORS config that was already correct. Hence the first line of `publicURLs`.
>
> **2. Role-level authorization cannot express membership.** The YAML answers *"can a MANAGER start sprints?"* It cannot answer *"can this MANAGER start **this** sprint?"* — that depends on a row in `project_member`, not on the URL. ziara's booking domain never needed this; yours needs it on every endpoint. **Row-level checks live in the service impl**, as the first statement of the method. Do not let me push membership into the YAML; that builds a second, half-working authorization system.

**Token storage — the decision, with its cost stated:**

| Option | Risk | Verdict |
|---|---|---|
| `localStorage` | any XSS reads the token | pragmatic, near-universal, **what we do** |
| in-memory + refresh token in `httpOnly` cookie | XSS can't read it; needs CSRF protection back on | correct, more work |
| `httpOnly` cookie only | needs CSRF tokens, and `csrf.disable()` above becomes wrong | not for this build |

We take option 1 with a short access-token expiry, and I will write the reasoning in a comment so it's a decision, not an accident. `User.logout()` from the diagram maps to **the client discarding the token** — the server cannot revoke a stateless JWT without a denylist, which we are not building.

**What to look at:** filter ordering; secret key from config not source; whether a missing YAML section NPEs at startup; token expiry; whether `password_hash` reaches a DTO, a log, or a `toString()` (Lombok `@Data` prints every field — it needs `@ToString.Exclude`).

**Acceptance:** `GET /api/projects` with no token → 401. `POST` with a DEVELOPER token → 403. With ADMIN → 200. React login stores a token and subsequent calls succeed. Adding a role is a YAML edit only.

---

## Step 8 — The issue aggregate: issues, comments, attachments, uploads

**Goal:** the core entity and its two composition children — plus the first place `AbstractBaseRestController` **stops fitting**.

**What I write:**

1. Migrations `2.0.3-create-sprint-table.yml`, `2.0.4-create-issue-table.yml`:

| Column | Type | Constraints |
|---|---|---|
| `id` | bigserial | PK |
| `issue_key` | varchar(20) | not null, unique |
| `title` | varchar(255) | not null |
| `description` | text | |
| `status` / `priority` | varchar(20) | not null |
| `project_id` | bigint | not null, FK `ON DELETE CASCADE` |
| `board_id` | bigint | **null**, FK `ON DELETE SET NULL` |
| `sprint_id` | bigint | **null**, FK `ON DELETE SET NULL` |
| `reporter_id` | bigint | not null, FK → `app_user` |
| `assignee_id` | bigint | **null**, FK → `app_user` |
| `due_date` | date | |
| `version` | bigint | not null, default 0 |
| `created_at` / `updated_at` | timestamp | not null |

Indexes on `(project_id, status)` and `(board_id, sprint_id)` — Step 10 reads on exactly these.

2. `issue/` slice — **five** `@ManyToOne(fetch = FetchType.LAZY)` associations. `Status` (`TO_DO`, `IN_PROGRESS`, `DONE`), `Priority` (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`). `IssueDTO` exposes the FKs as `Long`s **plus** a denormalised `assigneeUsername` — a conscious trade: one extra join server-side, one fewer round trip for React.

3. **Issue key generation** (`PROJ-1`, `PROJ-2`, …). The naive `SELECT MAX(seq)+1` races: two concurrent creates both read 7, both write `PROJ-8`, one dies on the unique constraint. I use `UPDATE project SET issue_counter = issue_counter + 1 ... RETURNING` — a row lock, correct, serialised per project. The unique constraint stays regardless: it's what makes the failure loud instead of silent.

4. `issue_comment/` and `issue_attachment/` slices. Both `issue_id NOT NULL ON DELETE CASCADE`. `file_size` is `bigint`.

5. **Multipart upload** — `POST /issues/{id}/attachments` taking `@RequestPart MultipartFile`. What handles this: `MultipartAutoConfiguration` registers `StandardServletMultipartResolver`, which parses `multipart/form-data` **before** the controller runs, streaming to a temp file. Configuration:
```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=12MB
```
> Exceeding the limit throws `MaxUploadSizeExceededException` **outside** normal controller flow — it needs its own handler in `GlobalExceptionHandler` or React gets a raw Tomcat error page instead of your `ApiError` envelope. Bytes go to disk under a configured root; the DB stores metadata only.

6. **The nested-resource problem.** These live at `/issues/{issueId}/comments`, not `/comments`. `AbstractBaseRestController<T>` assumes a flat path and one `{id}`. Three options: extend the base and tolerate a dead `/comments/{id}` route; write plain `@RestController`s; or build `AbstractNestedRestController<T, P>`.

   **I take option two.** Two nested resources is not enough duplication to justify a second abstraction — the same discipline as Step 4, applied to say *no*. Push back on me if you disagree; this is the most arguable call in the plan.

**What to look at:** any `EAGER` association (instant fail — `@ManyToOne` defaults to EAGER, so every one must be overridden); a `@OneToMany` I added without a query needing it; the two `User` FKs; whether cascade is declared in the DB *and* in JPA (`cascade = REMOVE` in JPA alone does not survive a direct SQL delete).

**Acceptance:** `POST /api/projects/{id}/issues` → `issueKey: "PROJ-1"`, next → `PROJ-2`. Deleting an issue removes its comments and attachments, leaves board and sprint intact. A 12 MB upload returns your `ApiError`, not a Tomcat page.

---

## Step 9 — Pagination, filtering, sorting

**Goal:** what a React data table actually needs — and an architecture decision the base controller forces.

**Dependency in focus: `spring-data-commons`** (already present via JPA).

How it works: `SpringDataWebAutoConfiguration` registers `PageableHandlerMethodArgumentResolver`. Declare `Pageable` as a controller parameter and Spring binds `?page=0&size=20&sort=priority,desc` into it — no code from you. `JpaRepository.findAll(Pageable)` then appends `LIMIT`/`OFFSET` **and** runs a second `COUNT` query for `totalElements`.

**What I write:**

1. `common/dto/PageDTO<T>` — an explicit page envelope: `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.
> **Why not return `Page<T>` directly:** `PageImpl`'s JSON structure is a serialization accident, not a designed contract, and Spring Data 3.3+ logs a warning about relying on it `[Likely]`. If it changes shape in a Boot upgrade, your React table breaks. An explicit DTO makes the contract ours.

2. **The base-class decision.** `AbstractBaseRestController.findAll()` returns `List<T>`. Options: change the base signature (breaks every slice), or add `findAll(Pageable)` alongside it. I **add** it — `GET /issues` paginates, `GET /ui-labels` (Step 12) does not and shouldn't. Not every collection is a table.

3. **Filtering** on `GET /issues`: `?projectId=&boardId=&sprintId=&status=&priority=&assigneeId=&q=`. Implemented with **JPA Specifications** (`JpaSpecificationExecutor` + composable `Specification<Issue>` predicates), not fifteen `findByAAndBAndC` methods, and not string-concatenated JPQL.

4. **Sort whitelist.** `?sort=` binds to entity property names, so an unmapped value throws `PropertyReferenceException` → a 500. Worse, it silently couples your React sort keys to your column names. I validate against an explicit allow-list and return 400 otherwise.

**What to look at:** does the count query run when React only needs a slice? Does filtering by `assigneeId` reintroduce N+1? Is the sort whitelist actually enforced, or did I only document it?

**Acceptance:** `GET /api/issues?projectId=1&status=IN_PROGRESS&page=0&size=20&sort=priority,desc` returns a correct `PageDTO`. `?sort=password` returns 400, not 500.

---

## Step 10 — The computed board view (the part that isn't CRUD)

**Goal:** real business logic. This is the analogue of ziara's slot-generation service: a **service, not a table**, producing a derived read that no single row in your diagram describes.

**What I write:**
- `sprint/` completed: `SprintState` enum (`PLANNED`, `ACTIVE`, `COMPLETED`) — the field the diagram forgot.
- `board/service/impl/BoardViewService.java` — `GET /boards/{id}/view` returns
  `BoardViewDTO { boardId, type, activeSprint (nullable), columns: [ { status, issues: [IssueSummaryDTO] } ] }`

| Board type | View contents |
|---|---|
| `KANBAN` | every issue with `board_id = {id}`, grouped by `status` |
| `SCRUM` | only issues with `board_id = {id}` **and** `sprint_id = <board's ACTIVE sprint>`. No active sprint → all columns present but empty, **HTTP 200, not 404** |

- `GET /boards/{id}/backlog` — issues on the board with `sprint_id IS NULL`, ordered by priority then `created_at`. **NULL sprint *is* the backlog**; no `Backlog` entity.
- Explicit decision, written in a comment: **can an issue sit on a board from a different project?** No — `IssueService.assignToBoard` validates `issue.project.id == board.project.id`. The schema cannot express it; the service must. (ziara has half-built compensation logic in its slot code that reads as if it works and doesn't — a lesson in leaving dead code paths behind.)

**The N+1, as a hard acceptance criterion.** A 200-issue board rendering `assigneeUsername` is 201 queries with lazy `@ManyToOne`. I use a constructor projection straight into `IssueSummaryDTO` — better than `JOIN FETCH` because it never materialises the entity at all.

**What to look at:** the KANBAN/SCRUM branch should be one `switch` in one place, not `if (type == SCRUM)` across three methods. `LocalDate` handling on sprint boundaries — is `endDate` inclusive? Query count.

**Acceptance:** with `spring.jpa.properties.hibernate.generate_statistics=true`, loading a 200-issue board logs **≤ 3 queries**. 201 means the step isn't done.

---

## Step 11 — State machines, resolutions, concurrent edits

**Goal:** transitions as data, the reference-table-backed-by-enum pattern, and the 409 your React UI needs.

**What I write:**

1. **Issue workflow** — allowed transitions declared as data:
```java
private static final Map<Status, Set<Status>> ALLOWED = Map.of(
    Status.TO_DO,       EnumSet.of(Status.IN_PROGRESS),
    Status.IN_PROGRESS, EnumSet.of(Status.TO_DO, Status.DONE),
    Status.DONE,        EnumSet.of(Status.IN_PROGRESS)     // reopen
);
```
`PATCH /issues/{id}/status` with an illegal transition → 422. Note what this forbids: `TO_DO → DONE` directly. That's a business rule I'm choosing — **tell me if you want it allowed**, and it's one line of the map, not a code change.

2. **Sprint lifecycle** — `POST /sprints/{id}/start` and `/complete`.
   - `start()` → 409 if another sprint on the same board is `ACTIVE`.
   - Enforced in the schema too: `CREATE UNIQUE INDEX uk_sprint_one_active_per_board ON sprint (board_id) WHERE state = 'ACTIVE';` — a Postgres **partial unique index**. Ask me why the service check alone is insufficient; the answer is that two requests both read "none active", both write, both succeed, and the board has two active sprints forever with no error anywhere.
   - `complete()` → sets `COMPLETED` **and** moves every non-`DONE` issue to `sprint_id = NULL`. One transaction, both or neither. The rejected alternative (move to the *next* sprint) requires an ordering your diagram doesn't have — written in a comment.

3. **Resolutions — the double representation** (ziara's `AppointmentRejectionReasonCode` pattern):
   - `issue_resolution` — a **table**, seeded by a changeSet: `code` unique, `label_fr`, `label_en`, `label_ar`, `is_active`.
   - `ResolutionCode` — a **Java enum** (`DONE`, `WONT_DO`, `DUPLICATE`, `CANNOT_REPRODUCE`) matching the seeded codes.
   - `issue_resolution_record` — `issue_id`, `resolution_id`, `resolved_by`, `resolved_at`, `note`.

   The enum gives compile-time safety where code branches on the reason; the table gives per-language labels and lets ops add a reason without a deploy. The cost is **drift**, so I add a startup check that warns when a seeded code has no enum constant.

4. **Optimistic locking** — `@Version private Long version;` on `Issue`. Hibernate appends `WHERE id = ? AND version = ?` to every update and checks the affected row count; zero rows → `OptimisticLockingFailureException` → **409** with a message React turns into *"someone else changed this — reload"*. Without it, last write wins silently, which in a drag-and-drop board means work disappears.

**What to look at:** transaction boundaries (the sprint check and the write must be one transaction — and you should be able to say why that still isn't enough and what the partial index is for); enum/table drift handling; whether the transition map leaked into the controller; whether the 409 handler distinguishes optimistic-lock conflict from constraint violation, since React shows different messages.

**Acceptance:** second sprint start → 409. Completing a sprint moves incomplete issues to backlog atomically. Two concurrent `PATCH` with the same stale version → one 200, one 409.

---

## Step 12 — Cross-cutting: AOP, i18n, UI labels

**Goal:** the things that touch every feature.

**Dependency added: `spring-boot-starter-aop`.**

What it actually does: brings `aspectjweaver` **for its annotations and pointcut parser only** — there is no bytecode weaving. `AopAutoConfiguration` enables `@EnableAspectJAutoProxy`, and Spring wraps your beans in **JDK dynamic proxies** (interface-based) or **CGLIB subclasses** (class-based).

> **The consequence you must internalise:** the proxy intercepts calls that arrive *from outside*. A method calling another method on `this` **bypasses the proxy entirely**. Your `@Around` aspect won't fire — and neither will `@Transactional`, for exactly the same reason. This is the single most common silent transaction bug in Spring, and Step 12 is where you get to see it demonstrated rather than described.

**What I write:**
- `common/aspect/LoggingAspect.java` — `@Around` on `execution(* ma.dev.workflow..service.impl..*(..))`, logging method + duration. Scoped to `service.impl` deliberately: widen it to `..*` and it logs every repository proxy call, drowning Step 10's board query in noise and measurably slowing the app.
- `common/i18n/` — `messages_fr/ar/en.properties`, a `LocaleResolver` reading `Accept-Language`, `MessageSource` wiring. Hooked into `GlobalExceptionHandler` so the 422s from Steps 5 and 11 arrive translated, and into `issue_resolution.label_*`.
- `ui_label/` slice — DB-overridable UI text: `GET /ui-labels?lang=ar` → `{key: text}` for board columns, statuses, priorities. Copy ziara's exact contract: **return an empty map on any error or unknown language**, so React silently falls back to its bundled translations. **Never throw here** — this endpoint is called before login, and a 500 blanks the entire UI.

**What to look at:** pointcut breadth; `/ui-labels` present in `publicURLs`; whether `Accept-Language` or an explicit `?lang=` wins when both are present (React sends both).

**Acceptance:** an illegal-transition error returns in Arabic with `Accept-Language: ar`; a row inserted into `ui_label` changes a column heading with no redeploy.

---

## Step 13 — Quality gates and tests

**Goal:** make the build enforce the conventions, so your review isn't the only defence.

**Added to `pom.xml`** (all four are in ziara):
- `maven-checkstyle-plugin` + `checkstyle.xml`
- `maven-pmd-plugin` with `failOnViolation=true` — **this one fails the build**
- `jacoco-maven-plugin` with a line-coverage floor (ziara sits at 47%)
- `dependency-check-maven` (OWASP)

**Tests, only now, and only where logic lives:**

| Target | Type | Why first |
|---|---|---|
| `BoardViewService` | slice test | KANBAN/SCRUM branch, empty active sprint, **query count** |
| `IssueService.updateStatus` | plain unit test | every legal and illegal transition in the map — no Spring context needed, because constructor injection |
| `SprintService.start/complete` | integration test | one-active-sprint rule, backlog move |
| `ProjectController` | `@WebMvcTest` | the `ApiError` envelope shape React depends on |

`com.h2database:h2` at test scope with a test profile. **This is where `project_key` pays off** — had we named the column `key`, H2 would reject the DDL and you'd be renaming a column across nine migration files.

> **Stated limitation, not hidden:** H2 does **not** support the partial unique index from Step 11. Either those tests run against Testcontainers Postgres, or that constraint is untested — and I'll say which, rather than let a green build imply coverage that doesn't exist.

**Acceptance:** `mvn verify` runs the gates; a deliberate violation fails the build.

---

# Part 5 — Conventions cheat sheet

### Naming
| Thing | Pattern | Example |
|---|---|---|
| Feature package | `snake_case` | `issue_comment`, `issue_resolution` |
| Service interface | `I` + name + `Service` | `IIssueService` |
| Service impl | name + `Service`, in `service/impl/` | `IssueService` |
| Repository | name + `Repository` | `IssueRepository` |
| DTO | name + `DTO` | `IssueDTO` |
| Mapper | name + `Mapper`, in `dto/mapper/` | `IssueMapper` |
| Migration file | `<maj>.<min>.<seq>-<kebab-desc>.yml` | `2.0.4-create-issue-table.yml` |
| DB table / column | `snake_case`, singular table | `issue_comment`, `is_active` |
| Enum column | `varchar` + `@Enumerated(EnumType.STRING)` | `status`, `priority`, `state` |

### Dependency map — where each one actually acts
| Dependency | Acts at | Key class to know |
|---|---|---|
| `starter-web` | request entry, JSON in/out | `DispatcherServlet`, `MappingJackson2HttpMessageConverter` |
| `starter-data-jpa` | repository proxying, SQL | `SimpleJpaRepository`, `PartTreeJpaQuery` |
| `postgresql` | JDBC only, via `ServiceLoader` | `org.postgresql.Driver` |
| `liquibase-core` | startup, **before** Hibernate validate | `SpringLiquibase` |
| Lombok | **compile time**, AST rewrite | none at runtime |
| MapStruct | **compile time**, code generation | `XxxMapperImpl` in `target/generated-sources` |
| `starter-validation` | after deserialization / via AOP | `MethodArgumentNotValidException` |
| springdoc | startup, reads the handler mapping | `/v3/api-docs` |
| `starter-security` | **before** `DispatcherServlet` | `FilterChainProxy` |
| jjwt | plain library, no Spring | `Jwts.builder()` / `Jwts.parser()` |
| `starter-aop` | proxy creation at bean init | JDK proxy / CGLIB — **self-invocation bypasses it** |

### Layering
```
controller  →  service (interface)  →  service.impl  →  repository  →  entity
     ↓                                       ↓
    DTO      ←────── mapper ──────────────  entity
```
- A controller never imports a repository or an entity.
- A service impl is the only place both DTO and entity may appear.
- `common/` imports **nothing** from a feature package. Features import `common/` freely.
- Cross-feature calls go **service interface → service interface**. `IssueService` may inject `IBoardService`; never `BoardRepository`.

### Hard rules
1. No `ddl-auto=update`. Ever. Liquibase owns the schema.
2. Never modify a changeSet that has already run — add a new one.
3. Constructor injection with `private final`. No `@Autowired` fields.
4. `@Transactional(readOnly = true)` on the service class; write methods override it.
5. No entity in a controller or service-interface signature.
6. No business logic in a controller — validate, delegate, return.
7. Secrets from config/environment, never source.
8. **Every association is `FetchType.LAZY`** — `@ManyToOne` defaults to `EAGER`; override every one.
9. **No `@OneToMany` until a query needs it.** Your diagram has nine `0..*` associations; mapping them all as collections is how the board view loads the entire database.
10. **DTOs carry `xxxId: Long`, not nested DTOs**, unless the endpoint's contract needs nesting (`BoardViewDTO` does; `IssueDTO` doesn't).
11. **Row-level authorization lives in the service impl.** `routes_configuration.yml` is role-level only.
12. Entities hold data and JPA annotations. No behaviour, regardless of what the class diagram draws.
13. **The `ApiError` and `PageDTO` shapes are frozen contracts.** Changing them is a frontend refactor.

---

# Part 6 — How you review each step

I'll tell you when a step is done. Read it in this order:

1. **Layering** — the dependency direction above
2. **Injection style** — constructor, `final` fields
3. **Transaction boundaries** — placement and read-only correctness
4. **Entity leakage** — anything past the service interface
5. **Fetch strategy and query count** — `EAGER`, stray `@OneToMany`, N+1
6. **Migration hygiene** — new file never an edit; entity matches schema; constraints in the DB, not only in Java
7. **The React contract** — does the response shape match what Part 3 promised
8. **Naming** — against the table above
9. **The logic itself** — correctness, edge cases, races

**Two things I want from you at every step**, because they're where the learning actually lands:

- **Ask "what happens if I delete this dependency?"** I'll answer with the specific failure — startup error, silent misbehaviour, or nothing at all. A surprising number are "nothing at all," and knowing which is which is the real skill.
- **Argue with one decision per step.** Several calls in this plan are judgment, not fact: the flat controllers in Step 8, the forbidden `TO_DO → DONE` in Step 11, `localStorage` in Step 7, `PageDTO` over `Page` in Step 9. If you don't push on those, you're memorising rather than learning.
