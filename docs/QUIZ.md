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
