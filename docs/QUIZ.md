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
in the same transaction that sets the sprint to `COMPLETED`.

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

### Why CORS needed a filter, not just the config bean

A `CorsConfigurationSource` bean does nothing on its own in plain Spring MVC — only Spring
Security's `http.cors()` looks one up, and there is no security chain until M2. A `CorsFilter`
registered as a `FilterRegistrationBean` applies the rules today. The bean stays, because M2's
`SecurityFilterChain` will consume that exact bean. The standalone filter registration has to be
**removed** at M2 — two filters would both write `Access-Control-Allow-Origin` and the browser
rejects the duplicate. Tracked in `docs/BACKLOG.md`.

Also: the parameter had to be qualified by name (`@Qualifier("corsConfigurationSource")`).
Spring MVC's own `mvcHandlerMappingIntrospector` bean also implements `CorsConfigurationSource`,
so the type alone was ambiguous and the app refused to start.

### Why work-service runs on 8081, not 8080

A local Apache install already holds 8080 on this machine. Discovered when the app failed with
"port already in use" while the actual cause was a different process, not a bug in the app.

### Why the issue detail panel does not let you change status

Only the board's drag changes status, using the transition map. If the panel also offered a
status control, the two could disagree about what is allowed — the panel would need its own copy
of the rule, or worse, no rule at all. It shows status as a read-only badge instead.

### Why assigning someone in the panel updates the board's cached `version`, not just `assigneeId`

`PUT /issues/{id}/assignee` saves the row, and `@Version` increments on every save — including
this one. The board's local copy of the issue had a `version` field that assigning left stale.
The next drag on that card would send the old version and get a false 409, exactly the same bug
class as the `save` vs `saveAndFlush` one above. Fixed by reading `version` back from the assign
response and updating it in board state alongside `assigneeId`. Verified with curl: assigning
bumped `version` 3→4, a drag using the stale value 3 got 409, the same drag using 4 succeeded.

---

# M2 — Auth and the edge

## Why auth-service gets its own database instead of sharing `app_user`

`docs/microservices-architecture.mermaid` draws a separate Auth DB from the Core DB. Two real
options existed: share one Postgres instance between work-service and auth-service, or split it
as drawn.

Sharing is simpler but silently breaks the diagram that is the documented source of truth, and
invites the exact question a jury would ask: "your diagram shows two databases, your code has
one — why?"

Splitting is what got built. auth-service owns its own table: `id`, `username`, `password_hash`,
`role`, `is_active`. It has nothing to do with work-service's `app_user` table beyond sharing a
username. On registration, auth-service calls work-service's existing `POST /users` to create
the matching profile row — no new work-service code, that endpoint was already built and tested.

**The honest cost, stated rather than hidden:** if work-service is down when someone registers,
registration fails outright. There is no retry and no queue yet. A saga or an async event
(`user.registered`, the same shape as `issue.created` at M3) is the real fix, and RabbitMQ is not
on the classpath until M3 — so this is deferred, not missed. If asked, the one-line answer is:
"registration is synchronous today because the message broker isn't wired in until M3; it is the
first candidate for becoming an event once it is."

## Why discovery-service was built first

auth-service and the gateway both register with Eureka on boot. Building them before the
registry exists means their startup logs show connection-refused warnings until it does — noisy
and confusing to debug. Discovery-service has no dependencies of its own, so it goes first.

## Why `eureka.client.register-with-eureka=false` and `fetch-registry=false` on discovery-service

Both flags describe what a **client** does: register itself, and pull down a copy of the
registry. The registry server is neither — it does not need to appear in its own list, and it
does not need to fetch what it already holds. Left at the defaults (`true`), a standalone Eureka
server would try to register with and query itself, which works but adds pointless traffic and
a confusing self-referential entry in the dashboard.

---

# M2 â€” what was actually built

## Why the token is verified twice, at the gateway and again at work-service

Because the gateway is not a wall around anything. work-service listens on 8081 and anything on
the network can call it directly, so it verifies the token itself. The gateway is where a bad
request fails cheaply, before it costs a service a thread or a database connection.

Demonstrable rather than asserted: `curl localhost:8081/projects` with no token returns 401.

If asked "so is the gateway pointless": no â€” it is the single address the browser knows, the one
place CORS is configured, where the correlation id is born, and where a circuit breaker keeps one
failing service from taking down the rest.

## Why HS256 with a shared secret, and not RS256

Symmetric, one secret in config, auth-service signs and the other two verify. The weakness is
real and worth stating first: any service holding that secret could mint a token, not just check
one. It is acceptable because all four services deploy together from one repository.

RS256 is the production answer â€” the private key stays in auth-service, everyone else gets the
public half through a JWK set â€” and the verifying code does not change, only where the decoder
gets its key material. It was not built now because the keypair would be generated at startup, so
restarting auth-service would invalidate every token in existence: a live hazard during a defence,
for a property nobody here is attacking. Written down in `BACKLOG.md` item 12.

## Why auth-service has its own database

Auth data has a different lifecycle and a different blast radius. It lives in `authdb` with its
own Liquibase changelog, so work-service physically cannot join a credential to an issue â€” there
is no connection from one to the other.

They share one Postgres process. That is a deployment cost in a demo, not a coupling: moving auth
to its own instance is a change to one connection string, because nothing in the code assumes they
are together.

## Why `account.work_user_id` is unique but is not a foreign key

It holds the `app_user` id in work-service. It cannot be a foreign key: it points into another
database and no constraint can reach across. The uniqueness is enforced here, the existence is
enforced by the registration flow, and the absence of the constraint is the service boundary made
visible in the schema.

## Why registration writes to two databases, and what happens when half of it fails

Order: check the username locally, call work-service `POST /users` to create the profile, save the
account with the id that came back, issue the token.

If the second write fails, work-service keeps a profile nobody can log in as. That is the
dual-write problem and it has no local fix â€” the real answers are an outbox table or an idempotent
retry that reuses an existing profile. The failed call is logged with the orphaned id so it can be
found, and it is in `BACKLOG.md` item 13 rather than hidden.

The method is deliberately **not** `@Transactional`: a transaction there would hold a pooled
database connection open across an HTTP call to another service, so a slow work-service would drain
this service's connection pool. There is one write, and `save` is atomic on its own.

## How auth-service authenticates itself when it calls work-service

Registration happens before the person has a token, so that one call needs its own identity.
auth-service mints a 60-second token with `role=SERVICE`, and work-service requires
`hasRole("SERVICE")` on `POST /users`.

The point is what it avoids: no second authentication scheme, and no permit-all hole punched in
work-service so that registration can work. It also means there is no other way to create a person
â€” a profile can never exist without a login behind it.

## Why `reporterId` and `authorId` no longer come from the request body

Because before M2 they did, which meant any caller could file an issue or post a comment in someone
else's name. They now come from the `uid` claim, which the caller cannot alter without breaking the
signature. The DTO fields are `READ_ONLY`, so sending one is ignored rather than rejected.

The smoke test proves it: it posts an issue with `"reporterId": 999999` and asserts the stored
reporter is the caller.

## Why the gateway is servlet-based and not reactive

Spring Cloud Gateway ships both. The reactive variant wins on connection-heavy workloads. At four
services and one user, one programming model across the whole system is worth more than the
throughput â€” the same `SecurityFilterChain`, the same `OncePerRequestFilter`, the same
`@RestController` as everywhere else.

## The Eureka bug: "No instances available for localhost"

auth-service needs a load-balanced HTTP client to call `http://work-service`. The first version
declared `@LoadBalanced RestClient.Builder` as the only builder bean â€” and Spring Cloud builds
**Eureka's own client** from whatever `RestClient.Builder` is in the context. So the registry client
became load-balanced too and tried to resolve the literal host `localhost` as a service name,
through the registry it had not managed to reach yet.

The fix is two beans: a plain `@Primary` one that Eureka picks up, and a `@LoadBalanced` one
injected by name into `WorkServiceClient`. The rule underneath it: **infrastructure calls go to an
address, service calls go to a name.** Only the second kind wants a load balancer.

## The duplicated `X-Correlation-Id`

Every service set the header on its response, so a call through the gateway came back with the
header twice. A service now stamps the response only when it generated the id itself â€” meaning the
call did not come through the gateway. Exactly the same mistake the temporary `CorsFilter` had to
be removed for, which is why it was worth writing down twice.

## Why `/error` is explicitly permitted in all three security configs

Spring forwards an unhandled error to `/error` as a second, internal dispatch, and the security
filters run on it too. Without permitting it, an authenticated caller asking for an issue that does
not exist gets `401 "a valid token is required"` instead of `404` â€” the error message actively lies
about what went wrong. The smoke test asserts an authenticated 404 stays a 404.

## Why the gateway route list missed `/sprints` at first

The route predicate is an allow-list of top-level paths. `/sprints/**` was left out, so the gateway
answered 404 for a service that was up and healthy. That is the standing cost of an allow-list: it
is safe when incomplete, and silent about it. Comments and attachments need no entry because they
are nested under `/issues/**`.

## Why there is only one role rule

`DELETE /users/**` requires ADMIN. Nothing else checks a role.

The mechanism is fully built â€” the claim travels, `JwtAuthenticationConverter` maps it to
`ROLE_<value>`, and the smoke test proves a DEVELOPER gets 403 where an ADMIN gets 200. What is
missing is the domain knowledge: the class diagram does not say who may close a sprint or delete a
project, and inventing a rule it does not state would be guessing. A wrong rule is worse than a
missing one. `BACKLOG.md` item 15.

## Why MapStruct is in work-service but not auth-service

work-service maps wide entities to DTOs in seven slices. auth-service has one three-field response,
built in the service. An annotation processor for that would be ceremony, and it would mean editing
two `maven-compiler-plugin` executions to keep the Lombok-before-MapStruct ordering correct.

## Why the error envelope is copied into each service instead of shared

A shared library would couple the deployments: changing the envelope would force all four services
to be rebuilt and released together, which is the coupling the split was meant to remove. Two copies
of a sixty-line class is the cheaper trade at four services. It stops being the right trade when the
envelope is stable and there are more consumers than copies.

## Where `ErrorController` lives in Boot 4

`org.springframework.boot.webmvc.error.ErrorController`, not
`org.springframework.boot.web.servlet.error.ErrorController` where every Boot 3 tutorial puts it.
Implementing that interface is what switches off Boot's own `BasicErrorController`, which is
`@ConditionalOnMissingBean` on it â€” without it, both map `/error` and the context refuses to start
on an ambiguous mapping.


## The bug the whole test suite missed: two `Access-Control-Allow-Origin` headers

Login worked. Everything after it failed with "cannot reach the server", and the 102-case smoke
suite was green the entire time.

What happened: the gateway adds CORS headers, and it also forwards the response headers the
service sent back. work-service still had the CORS config it needed at M1, when the browser called
it directly on 8081. So a browser calling `/projects` received:

```
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Origin: http://localhost:5173
```

The spec allows exactly one. A browser refuses the response outright, and `fetch` rejects with a
network error â€” indistinguishable in JavaScript from the server being down, which is why the app
reported the gateway as unreachable while the gateway was answering 200.

`/auth/login` worked because auth-service has no CORS config, so only the gateway set the header.
That is why sign-in succeeded and every request after it failed.

**Why no test caught it:** curl does not enforce CORS. It read a clean 200 with a correct body and
counted a pass. The suite was testing the API and the browser was testing something else.

**The fix, in three parts:**
1. work-service no longer does CORS at all. CORS is a browser concern and the browser only talks to
   the gateway. Swagger UI on 8081 needs nothing, because it is served from that same origin and
   its calls are not cross-origin â€” which was the faulty reason the config had been kept.
2. The gateway carries `DedupeResponseHeader ... RETAIN_FIRST` on both routes. The cause is fixed
   at the source; this exists because the gateway owns the browser contract and should not be
   breakable by what a downstream service puts in a header.
3. `smoke-test.sh` now **counts** `Access-Control-Allow-Origin` headers rather than trusting the
   status code, including on a 401 â€” an error response is still something the browser must be able
   to read.

**The lesson worth saying out loud:** this is the third time the same mistake appeared â€” the M1
`CorsFilter` that had to be removed, the duplicated `X-Correlation-Id`, and this. A gateway
combines two sets of response headers, so any header both ends set is a duplicate waiting to
happen, and the only headers that matter here are the ones the browser reads and no test client
does.

