# QUIZ.md — questions and answers, for revision

One section per milestone. Questions I was asked, with the correct answer.
Useful before the jury: these are the "why" questions, not the "what".

---

# M0 — Skeleton

## Round 1 — the first boot failure

The app failed with `Failed to determine a suitable driver class`.

### Q1. Where did `HikariDataSource` come from? Hikari was never added to `pom.xml`.

From `spring-boot-starter-data-jpa`, through auto-configuration.

Inside `spring-boot-jdbc-4.0.7.jar` there is a plain text file:

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Its first line is `DataSourceAutoConfiguration`. The annotation `@EnableAutoConfiguration`
(part of `@SpringBootApplication`) reads this file in **every jar** on the classpath and loads
each class listed. `DataSourceAutoConfiguration` contains `DataSourceConfiguration$Hikari`,
which builds the `HikariDataSource`.

Hikari is a **connection pool**. It keeps a set of open TCP connections to Postgres and hands
them out on demand. Opening a new connection costs ~50 ms, so reusing them matters.

### Q2. Adding H2 would make the app start. Why is that bad here?

H2 is a database written in Java that runs inside the app. No install needed. Spring can
auto-configure it with zero settings, which is why the error message suggests it.

Three reasons not to:

- H2 runs in memory. Restart the app and the data is gone. M1's acceptance test is
  "data survives a restart".
- H2 SQL is not Postgres SQL. A migration that passes on H2 can fail on Postgres.
- M4 needs **pgvector**. That extension does not exist in H2 at all.

H2 makes the error disappear without solving anything.

### Q3. Tomcat started before the datasource failed. Why start a web server for an app that was about to die?

Spring does not check everything first and then start. It builds in phases:

```
1. read config
2. start the web server        ← Tomcat up on port 8080
3. create the remaining beans  ← dataSource fails HERE
4. failure → shut Tomcat back down
```

Tomcat started because nothing it needed was missing. It has no link to the database.
Phase 4 is visible in the log: `StandardService : Stopping service [Tomcat]`.

---

## Round 2 — after the M0 files were written

### Q1. The database is empty and there are zero entities. Why does `ddl-auto=validate` still let the app start?

`validate` compares **entity classes ↔ existing tables**. It never creates or changes anything.

With zero entities there is nothing to compare, so the check passes trivially.

(Note: the database does exist — Postgres is running. What is missing is tables.)

### Q2. Which line in `docker-compose.yml` makes the data survive `docker compose down`?

```yaml
volumes:
  - workflow_pgdata:/var/lib/postgresql/data
```

`workflow_pgdata` is a **named volume**. It lives outside the container, so deleting the
container does not delete the data.

Without it, Docker still stores the data, but in an **anonymous** volume with a random ID.
You cannot find it, back it up, or reconnect it to a new container. In practice the data is lost.

### Q3. `open-in-view=false` is not needed yet — there are no entities and no lazy fields. Why set it now instead of when the first bug appears?

**Because turning it off later breaks code that already works.**

With `true`, the Hibernate session stays open until the JSON response is written. A lazy field
then loads **during serialization** — Jackson walks the object graph and each getter silently
fires a query.

If you develop with `true`, you write code that accidentally depends on that open session.
Switch to `false` a month later and it throws `LazyInitializationException` everywhere.

Two more reasons:

- The bug it causes is **silent**. No error, no warning, just a slow endpoint. Nothing in the
  log points at the cause.
- It holds a database connection for the whole request instead of just the query.

---

# M1 — Core domain

## Round 3 — the Project slice

### Q1. `ProjectController` depends on `IProjectService`, not `ProjectService`. There is only one implementation, so it changes nothing today. What does it buy you?

Today: almost nothing. Be honest about that.

It starts paying in two places:

- **Testing.** You can pass the controller a fake object implementing `IProjectService` that
  returns canned data. No Spring context, no database, milliseconds. Not possible against a
  concrete class without mocking tools.
- **Swapping the implementation.** At M2 some calls go to another service over HTTP. A second
  implementation of the same interface, and the controller does not change one line.

### Q2. What does `@Transactional(readOnly = true)` actually do, and what happens if you write inside a read-only method?

It is a **database** flag. Nothing to do with HTTP methods.

Two effects:

1. Hibernate stops **dirty checking**. Normally it keeps a copy of every loaded entity and
   compares at commit to find changes. `readOnly` skips that — less memory, faster.
2. Spring calls `connection.setReadOnly(true)`. Postgres honours it and refuses writes.

If you write anyway:

| What you do | Result |
|---|---|
| Change a field on a loaded entity | **Silently lost.** No flush, no error. |
| Call `save()` on a new entity | Postgres error: *cannot execute INSERT in a read-only transaction* |

The first is the dangerous one — no exception, no log, the change just does not happen.

### Q3. `create()` checks `existsByKey` then saves. Two users post the key `PFA` at the same moment. What happens?

```
Request A          Request B
existsByKey("PFA") → false
                   existsByKey("PFA") → false
INSERT PFA  ✅
                   INSERT PFA  ❌ unique constraint violation
```

Both checks ran **before** either insert. Neither transaction sees the other's uncommitted row —
that is what `READ_COMMITTED` isolation means. So both believe the key is free.

The check and the write are two separate moments. Anything can happen in between.

What protects you is the **`unique: true` in the migration** — the database constraint, not the
Java check.

Why keep the Java check then? For the message. It produces *"Project key already used: PFA"*
instead of a raw Postgres error. It is for the user, not for correctness.

**The rule: the check is for the message, the constraint is for the truth.**
And because the constraint can still fire, `DataIntegrityViolationException` must be handled.

---

## M1 decisions — the rest of the backend

No questions here, just the decision and the reason. Read this before the defence.

### Why `project_key`, `app_user`, `issue_comment` and not `key`, `user`, `comment`

`user` is reserved in PostgreSQL, `KEY` is reserved in H2, `COMMENT` is a DDL keyword.
Java class names stay `User`, `Project`; only the `@Table` / `@Column` names change.

### Why a project auto-creates a "Main board"

The class diagram says `Project "1" -- "1..*" Board`. SQL cannot express "at least one".
So `ProjectService.create` creates the first board, and `BoardService.deleteById` refuses to
remove the last one. The invariant lives in the service because it cannot live in the schema.

### Why issue keys use a row lock

`SELECT MAX(counter)+1` races: two requests read 7, both write `WORK-8`, one dies on the unique
constraint. `ProjectRepository.findByIdForUpdate` issues `SELECT ... FOR UPDATE`, which locks the
project row until the transaction ends, so issue creation is serialised **per project**.
The unique constraint on `issue_key` stays anyway — it turns a silent bug into a loud one.

### Why one active sprint per board is a partial unique index, not just a Java check

```sql
CREATE UNIQUE INDEX uk_sprint_one_active_per_board ON sprint (board_id) WHERE state = 'ACTIVE';
```

Same shape as the duplicate-key race. Two concurrent `start` calls both read "none active",
both write, and the board has two active sprints forever with no error anywhere.
The Java check exists for the message; the index exists for the truth.

### Why `saveAndFlush` and not `save` on issues

`save()` only queues the UPDATE. `@Version` increments at flush, which happens at commit —
**after** the method returns and after the DTO is built. The response carried a stale version,
so the client's next request would be rejected with a false 409.
`saveAndFlush` forces the write before mapping.

### Why priorities are sorted in Java, not in SQL

`priority` is `@Enumerated(STRING)`, so the column is a varchar. `ORDER BY priority DESC` sorts
alphabetically: `MEDIUM, LOW, HIGH, CRITICAL`. The board showed LOW above HIGH.
`Priority` now carries an explicit `rank`, and `BoardService` sorts on it. Java's sort is stable,
so the query's `createdAt` ordering survives as the tie-break.

### Why the board view uses a constructor projection

```java
select new IssueSummaryDTO(i.id, ..., a.id, a.username, ...)
from Issue i left join i.assignee a
```

A 200-issue board rendering the assignee name with lazy `@ManyToOne` is 201 queries.
The projection builds the DTO inside the query, so no `Issue` entity is created at all.
`LEFT JOIN` matters: the implicit join in `i.assignee.username` is an inner join and would
silently drop every unassigned issue.

### Why `NULL` sprint is the backlog

There is no `Backlog` entity and there should not be one. An issue with `sprint_id IS NULL` on a
board *is* the backlog. Completing a sprint sets `sprint_id = NULL` on every unfinished issue,
in the same transaction that sets the sprint to `COMPLETED` — both or neither.

### Why `ON DELETE` differs per foreign key

| Column | Rule | Why |
|---|---|---|
| `issue.project_id` | CASCADE | composition — the issue cannot exist without its project |
| `issue.board_id`, `issue.sprint_id` | SET NULL | aggregation — the issue survives its board and its sprint |
| `issue.reporter_id` | RESTRICT | deleting the reporter would destroy history, hence deactivation |
| `issue.assignee_id` | SET NULL | the work stays, it just becomes unassigned |
| `issue_comment.issue_id` | CASCADE | composition |

### Why nested resources get plain controllers

Comments and attachments live at `/issues/{issueId}/comments`, not `/comments`. A shared CRUD
base class assumes one flat path with a single `{id}`. Two nested resources is not enough
duplication to justify a second abstraction.

---

# Things I asked, and the answer

### What is H2 and what is it for?

| | H2 | PostgreSQL |
|---|---|---|
| Install | none — just a jar | separate server (Docker) |
| Where it runs | inside your app | its own process |
| Where data lives | RAM by default | disk |
| After restart | data gone | data stays |
| Used for | tests, demos, learning | real apps |

Its role in this project: none.

### Which file is triggered first when a Spring Boot app starts?

`WorkflowApiApplication.java` — a normal Java `main`. `mvnw spring-boot:run` just calls it.

```java
@SpringBootApplication
public class WorkflowApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkflowApiApplication.class, args);
    }
}
```

`@SpringBootApplication` is three annotations in one:

- `@ComponentScan` — scan this package and below for `@RestController`, `@Service`, etc.
- `@EnableAutoConfiguration` — read the `.imports` files inside the Spring jars
- `@Configuration` — this class can itself define beans

The class has 3 useful lines. The real work happens in the `.imports` files inside the jars.

### Why the folder `services/work-service/`? Is that microservices?

Yes — it is the folder shape for the services built later:

```
services/
├── work-service/            ← M0
├── auth-service/            ← M2
├── gateway/                 ← M2
├── discovery/               ← M2 (Eureka)
└── classification-service/  ← M3
```

Each folder is a separate Spring Boot app: its own `pom.xml`, jar, port, container.

Created at M0 and not M2 because moving it later costs more — today it was 5 files and one
`git mv`; at M2 it would be 40+ files plus IDE config, Docker paths, and the frontend API URL.

The folder does not make it a microservice yet. It is still one app and one database.

### Two Liquibase tables — what is the difference?

| Table | What it does |
|---|---|
| `databasechangelog` | History of every changeSet that ran, **with its md5 checksum**. Edit a changeSet that already ran → checksum no longer matches → app refuses to start. |
| `databasechangeloglock` | One row, a boolean. Stops two app instances running migrations at the same time. If the app crashes mid-migration the lock stays `true` and the app will not start until that row is cleared. |
