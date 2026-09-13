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


---

# M3 â€” AI classification, asynchronously

## Why classification-service has no database

It consumes a message, calls a model, publishes the answer, and forgets. The `AIClassification`
row is written by work-service, next to the issue it describes â€” composition in the class diagram
(`Issue "1" *-- "0..1" AIClassification`), which is `ON DELETE CASCADE` in the schema.

The payoff is that this service holds no state, so you could run five of them behind the same queue
and nothing would have to change. A classifier with its own database would need the issue copied
into it, and then two copies of the same ticket could disagree.

## Why `issue.created` is published after the transaction commits, not inside it

`IssueService.create` publishes a plain Spring event; `IssueEventPublisher` listens with
`@TransactionalEventListener(phase = AFTER_COMMIT)` and only then sends to RabbitMQ.

Publishing inside the transaction would let the classifier receive an issue id that is not visible
to anyone else yet â€” or worse, one from a transaction that then rolled back, producing a
classification for a ticket that never existed.

It also keeps AMQP out of the service class: `IssueService` does not know a broker exists, which is
why creating an issue does not wait for one.

## The gap in that, said out loud

Commit and publish are still two steps. A crash in between loses the message and the issue is never
classified. That is the same dual-write problem as registration at M2, and the same real answer: a
transactional outbox.

What makes it survivable is worth stating: an unclassified issue is **degraded, not corrupt**. The
ticket exists, the board shows it, someone can work it. `PROJECT.md` says as much â€” the issue exists
whether or not it gets classified. The failed publish is logged with the issue key so it can be
found. `BACKLOG.md`.

## Two layers of failure handling, and why one is not enough

- **Resilience4j** wraps the Groq call. It is for a call that would probably work if tried again:
  a 429, a 503, a timeout. The circuit breaker matters more than the retry â€” once Groq is failing,
  further calls fail instantly instead of each one waiting for its own timeout, which is what stops
  an outage from tying up every listener thread the service has.
- **RabbitMQ retry, then the dead-letter queue** is for a *message*. After the retries, it is parked
  so one bad ticket cannot block classification for everyone else.

Protecting only one of them is the mistake. A perfect breaker still leaves a poison message looping
forever; a perfect DLQ still lets a Groq outage exhaust the thread pool.

## The one property that decides whether a DLQ works at all

`spring.rabbitmq.listener.simple.default-requeue-rejected=false`.

Its default is **true**, which puts a failed message straight back on the queue and retries it
immediately, forever. The queue never drains, the dead-letter queue stays empty, and nothing
anywhere reports a problem. It is the single most common way a dead-letter queue is configured
perfectly and does nothing.

## Why the retry policy is written in Java instead of properties

`spring.rabbitmq.listener.simple.retry.*` applies one policy to every exception. The distinction
that matters cannot be expressed there: a rate limit deserves three attempts with backoff, while a
rejected API key or a message with no issue id should be parked immediately.

`ListenerRetryConfig` builds the interceptor with
`excludes(AmqpRejectAndDontRequeueException.class)` â€” the exception the listener throws once it has
decided a failure is permanent. Before that, a wrong Groq key cost three failed API calls per
ticket to prove twice more what was already known.

`ClassificationFailedException` carries the decision as a single `permanent` boolean, made where the
cause is known rather than guessed at later.

## Why a dead letter is parked, not binned

`GET /admin/classification/dead-letters` counts them; `POST /admin/classification/replay` puts them
back. Both ADMIN-only, on the same JWT as everything else.

A queue you can only fall into is a bin. Everything in there failed for a reason that was true at
the time â€” a missing key, an outage â€” and when the reason goes away the work is still worth doing.
This is what makes the second half of M3's acceptance test demonstrable rather than asserted:
remove the key, watch tickets still get created and their classifications park, restore the key,
replay, watch the suggestions arrive.

## Why replay moves the raw message instead of re-serializing it

Two reasons, and the first was a bug before it was a principle.

`receiveAndConvert` has no idea what type to produce outside a listener â€” a `@RabbitListener` gets
that from its method signature, a bare receive does not â€” so the JSON came back as a
`LinkedHashMap` and the cast threw a `ClassCastException`. The smoke test caught it.

The better fix was not a type hint but moving the raw `Message`: the body goes back byte-identical
and **the headers survive**, including the correlation id. A replayed message stays attached to the
request that originally created the issue, which is precisely when someone is trying to work out
what happened.

## How the correlation id survives the broker

An HTTP header stops at the queue. The id is copied into a message header on publish and read back
into the MDC by every listener, so one grep still covers the gateway, work-service and the
classifier after the hop. `ARCHITECTURE-NOTES.md` Â§1 asked for exactly this at M2 and it came due
here.

Both listeners clear the MDC in a `finally`. A listener thread returns to a pool, and inheriting the
previous message's id is worse than having no id at all â€” it attributes one request's work to
another.

## Why the auto-apply threshold is 0.85, and why it is in config

Above it, the suggestion is written onto the issue with nobody watching; below it, it waits for a
person.

It is high on purpose. A wrong auto-apply means somebody quietly works the wrong ticket at the wrong
priority; not applying costs one click. The asymmetry says the number should be high.

It is a property rather than a constant because it is a **policy, not a fact** â€” a team that finds
the model too eager lowers it without a rebuild.

The confidence is also clamped to 0..1 on arrival. A model returning 1.4 would otherwise auto-apply
everything.

## Why the same classification arriving twice is harmless

RabbitMQ guarantees at-least-once delivery, so a message can arrive again after a network hiccup or
a redelivery.

The listener looks the row up by `issue_id` and updates it if it is there, so a redelivery
overwrites instead of colliding. The unique constraint on `issue_id` is the backstop, not the
strategy â€” relying on it alone would send every duplicate to the dead-letter queue as a constraint
violation.

## Why only type and priority are applied to the issue

The model suggests five things. `Issue` has fields for two of them.

Team, effort and sentiment have nowhere to go â€” the class diagram gives `Issue` no such fields â€” so
they stay as evidence rather than being invented onto the entity. M4's dashboard reads them for team
load and distribution.

Status is never suggested at all. An issue is born `TO_DO` and only the transition map moves it;
letting a model skip that would make the workflow rules negotiable.

## Why the suggestion endpoint answers 204 and not 404 while it is waiting

The chip polls `GET /issues/{id}/classification` every two seconds. A 404 means "no such URL", and a
client cannot tell that apart from a typo in the path or a service that was never deployed. 204 says
the URL is right and the answer is not here yet, which is a different thing and the only honest one
while a queue is still working.

The chip gives up after 30 seconds and says "no suggestion came back" with a retry, rather than
spinning forever. A suggestion that never arrives usually means the message is parked in the DLQ,
and a spinner that never stops is a lie about that.

## Why there is a stub classifier, and why it cannot be mistaken for the real thing

The whole pipeline â€” queue, retry, dead-letter queue, persistence, endpoints, UI â€” can be built and
demonstrated on a machine with no API key. The classifier is an interface with two implementations
chosen by `app.classification.provider`.

A stub that could pass for AI would be the worst thing to discover mid-defence, so it says what it
is three ways: a warning block on startup, `modelVersion=stub-v1` stored on every row it produces,
and that version printed on the chip in the UI. The screen itself says `stub-v1`.

`@ConditionalOnProperty` rather than a Spring profile, because a property shows up in
`/actuator/env` and in the startup log, where a profile is a word on a command line nobody remembers
typing.

## Why the OpenAI starter is the Groq client

Groq speaks OpenAI's protocol. Only the base URL differs, so there is no Groq library, and adding
one would be a dependency that does nothing.

Temperature is 0.2 rather than the default 0.8: this is a classification, and the same ticket should
get the same answer twice. Creativity is the opposite of what is wanted.

The output shape is never written into the prompt by hand. Spring AI derives the JSON schema from
the `Suggestion` record and its `@JsonPropertyDescription` annotations, so the record and the prompt
cannot drift apart â€” renaming a field there changes what the model is asked for.

## The RabbitAdmin bug: queues that exist in Java and not on the broker

Declaring a `RabbitTemplate` bean makes Spring Boot's whole AMQP auto-configuration block back off,
and its `RabbitAdmin` lives in that same block. Losing it is quiet in the worst way: `RabbitAdmin` is
what walks the `Queue`, `Exchange` and `Binding` beans and actually creates them on the broker.
Without it they are objects in a context that never reach RabbitMQ, and the first symptom is a
listener waiting on a queue that does not exist.

Both services now declare `RabbitAdmin` explicitly.

## Why the message is not the DTO

`IssueCreatedEvent` carries `issueId`, `issueKey`, `title`, `description`, `projectKey` â€” not
`IssueDTO`.

A DTO exists to serve the React app and changes whenever a screen changes. This is a contract with
another service. Sending the DTO would mean a field added for a form quietly becomes part of an
integration nobody re-read.

The consumer's copy is annotated `@JsonIgnoreProperties(ignoreUnknown = true)`, which is the
versioning strategy: work-service can add a field without the classifier being redeployed first.
Without it, one new field on the producer would send every message to the dead-letter queue.


---

# M4 â€” Similarity and insights

## Groq has no embeddings API, and the architecture diagram said it did

Found at the end of M3. `microservices-architecture.mermaid` labelled Groq as "LLM + embeddings";
it has no embeddings endpoint at all. The diagram now says LLM only â€” a source of truth that is
quietly wrong is worse than no diagram.

The replacement is better than what it replaced: `spring-ai-starter-model-transformers` runs
`all-MiniLM-L6-v2` as ONNX inside classification-service. No API key, no cost, no rate limit, and
no network call while somebody is waiting for the answer. The same ticket always produces the same
vector, which a remote model does not guarantee.

The one cost, stated: the first start downloads about 80MB. Every start after that reads the cache
and works offline. Run it once before a demo.

## Why the vectors are in a third database

`vectordb`, owned by classification-service. Same seam as `authdb`: each service owns its data, and
work-service has no connection to this one.

It is also the only database here that can be thrown away. Every vector is derived from an issue
and can be rebuilt by replaying `issue.created`, which is what `POST /admin/issues/reindex` does.
The other two hold the record; this one holds a working memory.

## Why Liquibase creates the vector table and not Spring AI

`spring.ai.vectorstore.pgvector.initialize-schema` is false. Left true, `PgVectorStore` builds its
own table at startup.

Every other table in this system comes from a reviewed, versioned migration. A table that appears
by itself when an application boots is the one nobody can account for six months later, and it
means the schema has two owners. The migration describes a table whose column names the library
owns â€” which is exactly why it is worth having in writing.

## Why 384, and why it is load-bearing

It is `all-MiniLM-L6-v2`'s output size, and the column is `vector(384)`. A mismatch fails on the
first insert with an error that mentions neither the model nor the dimension, so the number is
written next to the reason in both the migration and the properties file.

## Why HNSW and cosine

**HNSW rather than IVFFlat**: IVFFlat has to be trained on rows that already exist to build useful
clusters, and this table starts empty. HNSW needs no training.

**Cosine rather than Euclidean**: sentence embeddings encode meaning in direction, not magnitude. A
long ticket and a short one saying the same thing should still match, and Euclidean distance would
separate them by length.

## The threshold was measured, and the first guess was wrong

0.75 was a guess. Measured against a ticket reading "Login page crashes with a 500 error":

| Query | Score | Same bug? |
|---|---|---|
| identical wording | 0.85 | yes |
| "Login page fails with 500 for all users" | 0.75 | yes |
| "Login screen throws a 500 when signing in" | 0.72 | yes |
| "Sign-in screen returns a server error every time" | 0.54 | yes |
| "Users report the login is broken" | 0.49 | yes |
| "Add CSV export to the monthly reports page" | <0.20 | no |
| "Repaint the bicycle shed a nicer shade of green" | <0.20 | no |

0.75 would have caught only near-identical wording and missed most real duplicates â€” the exact
failure the feature exists to prevent. Genuine rephrasings bottom out around 0.49; unrelated
tickets never reach 0.20. The gap between them is wide, and the threshold belongs in it: **0.45**.

**The asymmetry runs the opposite way from auto-apply, deliberately.** That threshold is high
because it changes a ticket with nobody watching. This one is low because it only offers a
suggestion: a false positive costs a glance, a false negative costs a duplicate ticket.

## Why similarity search is synchronous when everything else is a queue

M3 put everything on RabbitMQ because nobody was waiting for it â€” an issue is created, and the
suggestion arrives when it arrives.

Here somebody is waiting. The answer is worthless once they have pressed submit, so this is the one
call in the system where a queue would be the wrong tool. It is also the first synchronous route
the gateway has to classification-service.

## Why an issue is embedded on the same message that classifies it

The classifier already receives every issue over `issue.created`. Embedding it there means one
pipeline rather than two, and no second path that can silently fall behind the first.

Indexing runs **before** classifying, on purpose: indexing is local and cannot fail for an external
reason, while the Groq call can. Doing it first means a provider outage costs the suggestion but not
the duplicate detection. Two features arriving on the same message should not share one failure.

## Why deleting an issue needs an event

The classification row disappears with its issue through `ON DELETE CASCADE`. The vector cannot: it
is in another database and there is no foreign key to cascade along.

So work-service publishes `issue.deleted` and the classifier removes the row. Without it the
duplicate panel would keep offering tickets that no longer exist, and the failure would be silent
and would get steadily worse.

Its own queue rather than sharing `issue.created.q`: they carry different payloads and fail for
different reasons, and a deletion should not sit behind a backlog of classifications waiting on an
external model.

## Why reindexing is replaying `issue.created`

Issues created before M4 have no vector, and a model change would invalidate the ones that do.

Rather than a backfill that walks the table and talks to the classifier directly, `POST
/admin/issues/reindex` republishes the creation event for every issue. It is the same path a new
issue takes, so there is no second code path to keep correct â€” and it is safe to run twice, because
the classification listener updates by issue id and the index deletes before it inserts.

## Why the dashboard is one endpoint

Six endpoints would mean six round trips, six loading states, a screen that can render
half-populated while somebody watches, and numbers from six slightly different moments. The last
one is the real problem: a dashboard whose totals do not add up is worse than a slow dashboard.

## Why the agreement rate excludes PENDING

`(AUTO_APPLIED + CONFIRMED) / (AUTO_APPLIED + CONFIRMED + OVERRIDDEN)`.

A suggestion nobody has looked at is not a disagreement. Including PENDING would make this number
fall every time somebody files a ticket â€” it would measure how busy the team is, not how right the
model is.

**The weakest part, said before anybody asks:** AUTO_APPLIED counts as agreement, and nobody
confirmed those. It means the model was confident and was not contradicted. Silence is being read
as assent, so the honest reading is "not overridden" rather than "verified correct".

Null rather than zero when nothing has been judged: zero would read as "the AI is always wrong",
which is a very different claim from "nobody has checked yet".

## Why there is no `component` on the dashboard, when PROJECT.md asks for one

`PROJECT.md` says "type/component distribution". `Issue` has no `component` field and the class
diagram never gives it one.

Adding a column to serve a chart is the wrong order â€” the diagram is the source of truth and model
changes go there first. The nearest real axis is the team the AI reads out of each ticket, and that
is on the screen, labelled as inferred rather than entered. `BACKLOG.md` item 25.

## Why the charts are CSS and not a library

Five distributions and a percentage. A charting library would be the first dependency in this
project that could not be explained line by line, and the bars are a div with a width.

They are sized relative to the **largest bar, not the total**: relative to the total, a healthy
spread across six categories renders as six slivers and communicates nothing.

## Why the duplicate panel says nothing most of the time

Three rules keep it from being the kind of panel people learn to scroll past:

- it renders nothing at all when there is no match, which is the common case
- it waits 500ms for a pause in typing, and ignores anything under 10 characters
- it never reports its own errors. A duplicate check that could not run is not the writer's
  problem, and an error box over a form somebody is filling in is worse than silence

Each request is aborted when the text changes, so a slow answer for old text can never arrive after
a fast answer for new text and overwrite it. That also meant teaching the API client that an
`AbortError` is not a failure â€” otherwise cancelling a request would have put "cannot reach the
server" on screen while somebody typed.

## Why the search is scoped to a project

A duplicate in somebody else's project is not a duplicate. The filter runs in the database rather
than over the results, so a busy project cannot push another project's matches out of the top five.

## Why the score is shown to the reader

"84% alike" lets somebody judge a borderline match for themselves. A bare list asks them to trust
the model, and the whole principle of this project is that the AI suggests and a person decides.


## The orphaned vectors: why a database cascade needs its own event

Deleting an issue publishes `issue.deleted` and the classifier forgets its vector. That was built
first and it works.

Deleting a **project** does not. `issue.project_id` is `ON DELETE CASCADE`, so the database removes
every issue in that project without `IssueService.deleteById` ever running — and therefore without
a single `issue.deleted` being published. Every one of those vectors stayed behind, and the
duplicate panel would have gone on suggesting tickets from a project that no longer existed.

Found by counting rows after a test, not by reading the code: three test projects were deleted and
their vectors were still there.

The fix is a second event, `project.deleted`, and the classifier deletes by `projectKey`. **One
message for the whole cascade**, because the cascade is one act — modelling it as a hundred
deletions would be a hundred chances to lose one.

The general lesson is worth more than the fix: **a foreign key cascade is invisible outside its own
database.** Anything holding derived data about rows in another service cannot see them disappear,
so every cascade that crosses a service boundary has to be announced deliberately. It is the same
shape as the correlation id stopping at the queue, and the same shape as `ai_classification`
cascading while `issue_vector` cannot.

## The PowerShell array trap in the check scripts

`scripts/check-similarity.ps1` reported "0% alike" for two tickets that had genuinely returned no
match, which looked exactly like the similarity threshold being too low.

It was not. Windows PowerShell 5.1's `ConvertFrom-Json` passes a parsed array through the pipeline
as **one object** rather than unrolling it, so `@(...)` and `Measure-Object` both report 1 for an
empty array and 1 for a ten-element one. An empty result counted as one match with a null score,
which rounded to zero.

The script now counts off the assigned variable — `$parsed -is [array]` then `.Count` — which is
version-proof. Worth knowing because the wrong answer was plausible: it pointed at the feature
rather than at the tool measuring it.

---

# The test suite

## What is tested, and what deliberately is not

78 unit tests. They cover one thing: **the rules that neither the schema nor the framework can
enforce, and that a reader cannot verify by looking.**

| Tested | Why it earns a test |
|---|---|
| `ALLOWED_TRANSITIONS` | `status` is a varchar; nothing in the database stops TO_DO jumping to DONE |
| the auto-apply threshold | a judgement about when to trust a model unattended, invisible in the data |
| classification idempotency | RabbitMQ delivers at least once, and the lookup-then-update is the only thing making a redelivery harmless |
| the agreement rate | one arithmetic slip from being a lie, and the PENDING exclusion is a rule rather than a formula |
| which Groq failures are permanent | this is the dead-letter queue's brain, and it was already wrong once |
| the stub's confidence | it must stay below the auto-apply threshold, or keyword matching starts rewriting tickets |
| JWT claims | three other services depend on those exact claim names |
| registration's write order | the order has a consequence, documented in BACKLOG 13 |

Not tested, and on purpose: getters, mappers, and anything where the test would restate the code.
A suite that asserts `setName` sets the name is a suite nobody reads.

## Why `mvn test` needs nothing running

Every one of the 78 is a plain unit test with Mockito. No database, no broker, no Spring context —
seconds on a clean checkout.

The one test that boots the whole application, `contextLoads`, is tagged `integration` and excluded
by default. It genuinely needs Postgres, RabbitMQ and Eureka, so on a fresh machine it would fail
because nothing is up rather than because anything is wrong. **A suite that fails for the wrong
reason is one people stop running**, and a suite people stop running is worse than none, because it
looks like coverage.

```
mvn test               the unit tests, no infrastructure
mvn test -Pall-tests   everything, with the stack up first
```

It is a Maven profile rather than `-Dgroups=integration` because surefire's `excludedGroups` beats
`-Dgroups` on the command line — the exclusion could not otherwise be lifted at all.

## Why the failure-routing test names the SDK's exceptions explicitly

`GroqFailureRoutingTest` exists because that logic was already wrong once and nothing caught it.

The first version checked Spring's `HttpClientErrorException`, but Spring AI 2.0 is built on the
official openai-java SDK and throws `com.openai.errors.*`. Nothing matched, every failure fell
through to "unexpected, retry", and a rejected API key was retried three times per ticket instead
of being parked. The code read correctly and did nothing.

Naming `UnauthorizedException`, `RateLimitException` and the rest in a test means the same silent
mismatch would fail a build instead of a defence.

## Two tests that were wrong before the code was

Worth admitting, because both were caught by running them rather than by reading them.

The stub's missing-information test used a description containing "when I use it" — and "when i" is
one of the phrases the stub reads as an attempt to describe reproduction steps. The test asserted
the opposite of what it claimed. Fixed by choosing wording that avoids every trigger phrase, with a
comment saying why, plus a second test for the case where the steps genuinely are there.

The JWT test called `decoded.getIssuer()`, which insists on a URL, while this issuer is the plain
name `auth-service`. Read as a string instead. That is fine — the resource servers compare it as a
string too — but it is worth knowing before somebody "fixes" the issuer into a URL and breaks three
services.

---

# Running against the real Groq

## The model name in every tutorial does not exist on Groq

`llama-3.3-70b-versatile` was the configured default for two milestones. It is not in Groq's
catalogue at all. The mistake was invisible until there was a key to try it with, which is the
whole hazard of a dependency you cannot exercise.

`GET /v1/models` with the key is the authoritative list. The chat models are `openai/gpt-oss-120b`,
`openai/gpt-oss-20b` and `qwen/qwen3.6-27b`.

## Why 120b and not 20b

Measured, on a ticket reading *"Production is broken, urgent, everyone affected... I am furious"*:

| | type | priority | sentiment |
|---|---|---|---|
| `gpt-oss-20b` | BUG | CRITICAL | **+0.8** |
| `gpt-oss-120b` | BUG | CRITICAL | **−0.9** |

Both read the type and the priority correctly. 20b read an outage as *cheerful* — it got the sign
backwards on the one field that needs actual reading comprehension. 120b costs about 350ms more,
which is nothing on an answer nobody is waiting for.

## Why reasoning effort is set to low

The gpt-oss models reason before answering, and that reasoning is billed. Same ticket, same model:

| effort | output tokens | answer |
|---|---|---|
| default | 306 | BUG / CRITICAL / −0.90 |
| **low** | **80** | BUG / CRITICAL / −0.92 |

Identical judgement for a quarter of the tokens. Triage does not reward deep reasoning; it rewards
reading, which the model does either way.

It is not a micro-optimisation. **Groq's free tier allows 8000 tokens per minute.** At 306 tokens a
ticket, a handful filed together exhausts it — the smoke test did exactly that and drove real 429s.
At 80 it takes four times as many.

## What the rate limit proved

The 429s were not a setback. They were the first time the resilience design met a real failure
instead of a simulated one, and every layer did what it was built to do:

- `RateLimitException` was classified as **transient**, not permanent — so the tickets were retried
  rather than discarded. That classification is the one that was silently wrong at M3 and is now
  covered by `GroqFailureRoutingTest`.
- Resilience4j retried with backoff, and the circuit breaker opened rather than letting every
  listener thread sit waiting.
- One message that outlived its retries landed in the dead-letter queue, where it could be replayed
  once the limit reset. Nothing was lost.
- Every ticket was still created. The API returned in ~240ms throughout, because classification was
  never on that path.

The honest summary for a jury: *the free tier rate-limited us during testing, the tickets were
still created, the classifications retried and the one that could not be retried was parked and
replayed.* That is the design working, and it is worth more than a demo where nothing ever fails.

## Why the smoke test no longer asserts `stub-v1` or `PENDING`

Those two assertions passed for four milestones because the stub was the only classifier. Against
the real model they failed — it reports its own name, and it is confident enough to auto-apply.

Both were the *test* being wrong, not the code: the assertions had encoded one provider's behaviour
as if it were the contract. They now assert that a model version is present at all, and that the
review status is one of the two outcomes a fresh classification can legitimately have. The
accept test captures whatever was suggested and checks that those values reached the issue, rather
than pinning one model's judgement.

## Why there is a page per service in `docs/services/`

The existing docs describe the system as a whole: the milestones, the data model, the architecture
diagram, the binding rules. None of them answer the question a jury actually asks, which is *"what
does this one service do, and why is it separate?"*

So each service gets one page in plain English with its own diagram. They are revision material,
not a second specification — every claim on them is already true in the code, and each page ends
with the one sentence to say out loud about that service.

The split is deliberate: a page per service matches how a defence is questioned, one box on the
architecture diagram at a time.

## M5 — why two levels of role, and not one

Because one cannot answer both questions. **Global** (`app_user.role`, in the JWT) says what you
are on the platform: ADMIN approves accounts, MANAGER may create a project, DEVELOPER joins them.
**Per project** (`project_member.role`) says what you are inside one: `PROJECT_MANAGER` — the chef
de projet — or `MEMBER`.

A global role cannot express "manager here, ordinary member there", which is the normal case. A
project role cannot exist before any project does, which is the situation the first administrator
is in. Neither is a subset of the other.

## Why the project role is not in the token

It changes the moment a project manager adds or removes somebody, and a token lives an hour with no
way to recall it. The global role is in the token because an administrator changes it rarely and
deliberately; the project role is read from the database on the request that needs it.

Say the cost as well: a role change does not reach a token already issued, so somebody promoted
keeps their old role for up to an hour. That is stated on the admin screen rather than hidden, and
a revocation list is BACKLOG item 47.

## Why the membership check is in the service, not on the path

`GET /issues/{id}` has to load the issue before anyone knows which project it belongs to, and
therefore whether the caller was allowed to see it. A path rule or `@PreAuthorize` decides from
what is in the URL, and the URL does not say. So the check happens in the service, after the row is
in hand — the same place, and for the same reason, as `ALLOWED_TRANSITIONS`.

## Why scoping `GET /projects` was the easy half

On its own it is decoration: the project disappears from a list while `/issues/1`, `/issues/2`
still answer one at a time. Every entry point that resolves to a project checks membership —
boards, sprints, issues, comments, attachments, classifications, dashboard. The smoke test proves
it by asking **by id** with a valid token belonging to somebody else, because a filtered list
proves nothing.

## The vulnerability M5 closed

`POST /auth/register` is open at the gateway — it has to be — and `RegisterRequest` carried a
`role`. **Anyone on the internet could register themselves as an administrator.** It survived three
milestones because the smoke test used it to bootstrap, so the hole was load-bearing in the tests.

The field is gone; everybody registers as a PENDING DEVELOPER. Which forces the next question:
where does the first administrator come from? A migration seeds one — the only row in `account`
that nobody approved, because a chain of approvals has to start outside itself.

## The bug nobody noticed: the role that never changed

`PUT /users/{id}` on work-service wrote `app_user.role`. Tokens are minted from `account.role` in
`authdb`. Nothing kept them in step, so promoting somebody changed the profile and **every token
they were ever issued kept the old role, permanently**. The smoke test did exactly this at line 111
and asserted only the 200.

Two copies of a value with two writers and no owner. The role now changes in auth-service only and
is mirrored into work-service through a SERVICE-only endpoint, so there is one writer.

## Why a separate `join_code` instead of `project_key`

`project_key` is printed on every ticket — `WORK-12` is on every card on the board. If it were also
the way in, anybody who had ever seen a ticket could ask to join. The key is a display name; the
code is a secret, twelve random characters from an alphabet with `0 O 1 I` removed so it survives
being read off one screen and typed into another.

## Why the code does not admit anybody by itself

A code sent by mail gets forwarded, quoted in a reply-all and pasted into a chat. Treating
possession of one as permission would make a project as private as its most careless member's
inbox. The code earns the right to *ask*; a project manager still decides.

Rotating it is therefore useful and cheap: it stops the next person using an old code and touches
nobody who is already there.

## Why the admin list is built in auth-service

The screen needs a username and status (from `account` in `authdb`) next to an email (from
`app_user` in `workflow`). Assembling it in work-service would mean work-service calling
auth-service — and auth-service already calls work-service, so that would be a cycle between two
services that are supposed to be deployable separately. Every cross-service call in this system
points the same way, and there is no path back.

## Why `is_active` became a three-value status

A boolean could say "may not log in" but not why. "Waiting for approval" and "switched off" need
different sentences: telling somebody who signed up ten seconds ago that their account is
deactivated reads as a punishment for signing up.

## Why the router arrived now and not earlier

BACKLOG item 35 said it would land "at the next screen", and the argument settled itself rather
than being won: a join code is mailed as a link, and a link is a URL. Screens switched by a
`useState` string cannot be linked to, bookmarked, or refreshed into. `react-router-dom` is the
first runtime dependency added to the frontend since the project began.

The guards decide what is **rendered**, never what is **allowed** — every rule is enforced again in
work-service and auth-service, which is the only place it counts. A guard is one localStorage edit
away from being bypassed; a service is not.

## Why moving a card writes a second row

`issue.status` holds one value: where the card is now. An update overwrites it, and nothing
anywhere keeps what it was — so "who moved this to DONE, and when" is unanswerable from the issue
table no matter how it is queried. `issue_status_change` is that answer, written by the move
itself.

It is written **in the same transaction** as the status update, in `IssueService.updateStatus`,
not in an event listener. A listener could fail on its own, and then the board would show a state
nothing accounts for. Sharing the transaction makes the two facts inseparable: a move that was not
recorded never happened at all.

Only accepted moves leave a row. An illegal transition throws before the write; dropping a card
back on the column it came from returns early with no write and no version bump. So a fumbled drag
adds nothing — the history is what happened, not what was attempted.

## Why the history row stores the username and not just the user id

Comments store `author_id` and the browser resolves the name against the project's member list.
That works for as long as the author is still on the project. A history row has to outlive that:
the person who moved a card last March may have left, and the honest answer to "who moved it" is
still their name, not `user 7`. The row carries `changed_by_id` **and** `changed_by_username`, and
the foreign key is RESTRICT so deleting an account cannot quietly rewrite the board's past.

## Why POST /issues/{id}/history returns 405 and not 500

The endpoint is GET-only by design — a POST would let a caller record a move that never happened.
But Spring's `HttpRequestMethodNotSupportedException` was falling through to the catch-all handler
and coming back as a 500, which claims the server broke when the truth is the caller asked for
something that does not exist. It now maps to 405 with an `Allow` header naming the verbs that do.
Found by the smoke test, which asserts the code and not merely that the call failed.

## Why the issue became a page instead of a drawer

The mockups show an issue on its own page, and the router already existed, so the drawer went. The
real gain is not the look: a page has an address. `/projects/1/issues/42` can be pasted into a
comment, bookmarked, and refreshed into — a drawer over the board could do none of that.

It also removed a bug class. The drawer shared the board's cached copy of the card, so every change
made inside it (assigning, accepting an AI suggestion) had to push the new version back to the board,
or the next drag sent a stale version and got a false 409. Now the board reloads when you come back
to it, so it is never holding a version older than the server's.

## Why the redesign added no sprint tabs or profile page

The mockups include a Backlog tab, a Sprints tab, "Complete sprint" and a whole profile page. None
were built, on purpose: the profile needs data that does not exist (full name, avatar, a
self-service endpoint), and sprint planning is a feature, not a restyle. Drawing inputs that save
nothing is exactly the kind of stub the project's quality bar forbids. All three are BACKLOG items
53–55 with the reason.

## Why there is still no UI library

Every screen is plain React with one stylesheet of CSS variables. A component library would have
matched the mockups faster, but it is a dependency the jury can ask about, and every class in
`styles.css` can be explained line by line. Avatar colours are derived from the name with a small
hash, so the same person has the same colour everywhere without storing anything.

## Why the deployment reads its addresses from environment variables with local defaults

Every host, port and password in `application.properties` became `${NAME:local-value}`. On Railway
the variables are set and win; on a laptop none are set and the value after the colon is exactly
what the file said before. So one file serves both, and a fresh clone still runs with no setup. Two
files — a local one and a production profile — would be two places to keep in step, and the one not
in use is the one that goes stale.

## Why services register in Eureka by hostname on Railway but by IP locally

Locally, registering by IP was chosen because a Windows machine name often does not resolve from
another process. On Railway it is the opposite: a container's IP is not something other services are
meant to dial, while its private hostname (`work-service.railway.internal`) always resolves. So
`prefer-ip-address` is a variable, true by default and false on Railway, with
`EUREKA_INSTANCE_HOSTNAME=${{RAILWAY_PRIVATE_DOMAIN}}`. Checked both ways on a Docker network: with
the variables the registry shows the hostname, without them it shows the IP.

## Why each Dockerfile has two stages

The first stage has Maven and a full JDK and builds the jar; the second has only a JRE and copies
the jar in. The image that runs carries no compiler, no build tool and no source code — smaller, and
less to attack. The pom is copied before the source so Docker caches the dependency download: editing
a Java file does not re-download every library. The container runs as a non-root user, and
`-XX:MaxRAMPercentage=75` makes the JVM size its heap from the container's memory limit instead of
the host's.

## Why the frontend's API address is a build argument and not a runtime variable

Vite writes `import.meta.env.VITE_API_URL` into the JavaScript when it builds. The browser that runs
that JavaScript has no environment to read a variable from later. So the gateway's URL has to exist
before the frontend is built, and changing it means rebuilding the frontend — which is why the deploy
guide says to create the gateway's public domain first.

## Why Postgres got its own Dockerfile but RabbitMQ did not

Postgres needs the script that creates `authdb` and `vectordb`. Locally that script is mounted into
the container; Railway has no bind mounts, so it is copied into an image instead. The same image sets
`PGDATA` one directory below the volume, because a Railway volume arrives containing `lost+found`
and `initdb` refuses a non-empty directory. RabbitMQ needs nothing added — its queues are declared
by the services — so it runs from the stock image and there is no Dockerfile to maintain for it.
