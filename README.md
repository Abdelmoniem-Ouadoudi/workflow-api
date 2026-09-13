# Workflow — work management platform

platform (projects, boards, sprints, issues) with an AI layer that classifies each new
ticket and spots duplicates. Year-end academic project (PFA).

Five Spring Boot services behind a gateway, a React frontend, PostgreSQL + pgvector and RabbitMQ.

| Part | Port | What it does |
|---|---|---|
| `frontend` | 5173 | React + Vite — the screens |
| `gateway` | 8090 | the one address the browser talks to; checks the JWT |
| `discovery-service` | 8761 | Eureka — where the services find each other |
| `work-service` | 8081 | projects, boards, sprints, issues, comments, history, dashboard |
| `auth-service` | 8082 | accounts, passwords, JWT |
| `classification-service` | 8083 | reads a ticket, suggests type/priority/team, finds duplicates |
| PostgreSQL (Docker) | 5433 | databases `workflow`, `authdb`, `vectordb` |
| RabbitMQ (Docker) | 5672 · UI 15672 | carries `issue.created` / `issue.classified` |

---

## 1. What you need installed

- **Java 21** (JDK) — `java -version`. Each service has a Maven wrapper, so Maven itself is not needed.
- **Node.js 22 LTS** (20.19+ also works) — `node -v`
- **Docker Desktop**, running
- Internet on the **first** run (Maven dependencies, npm packages, and an ~80MB embedding model)

Ports 5433, 5672, 8081–8083, 8090, 8761 and 5173 must be free.

---

## 2. First time only

```powershell
git clone https://github.com/Abdelmoniem-Ouadoudi/workflow-api.git
cd workflow-api\frontend
npm install
```

---

## 3. Start everything

Open **six terminals**, all in the project folder. Keep this order — every service registers with
Eureka, so it must be up first.

**0 · Infrastructure** — Postgres and RabbitMQ

```powershell
docker compose up -d
docker compose ps        # wait until workflow-mq shows (healthy)
```

**1 · discovery-service** — wait for `Started DiscoveryServiceApplication`, then check http://localhost:8761

```powershell
cd services\discovery-service
.\mvnw.cmd spring-boot:run
```

**2 · work-service**

```powershell
cd services\work-service
.\mvnw.cmd spring-boot:run
```

**3 · auth-service**

```powershell
cd services\auth-service
.\mvnw.cmd spring-boot:run
```

**4 · classification-service**

Without an API key it runs on keyword rules (`stub-v1`, not real AI):

```powershell
cd services\classification-service
.\mvnw.cmd spring-boot:run
```

With real AI through Groq — set the key **in that terminal**, never in a file:

```powershell
cd services\classification-service
$env:GROQ_API_KEY = "gsk_..."
$env:CLASSIFICATION_PROVIDER = "groq"
.\mvnw.cmd spring-boot:run
```

The first start downloads the embedding model (~80MB) and looks like it hangs. Later starts are
offline.

**5 · gateway**

```powershell
cd services\gateway
.\mvnw.cmd spring-boot:run
```

**6 · frontend**

```powershell
cd frontend
npm run dev
```

On macOS/Linux, use `./mvnw spring-boot:run` instead of `.\mvnw.cmd spring-boot:run`.

---

## 4. Use it

1. Open http://localhost:8761 and wait until it lists **WORK-SERVICE, AUTH-SERVICE,
   CLASSIFICATION-SERVICE and GATEWAY**. Registration takes up to ~30 seconds; before that the
   gateway answers 404 for a service that is actually up.
2. Open **http://localhost:5173**
3. Sign in with the seeded administrator: **`admin` / `admin12345`**

New accounts created from the sign-up screen stay **pending** until the admin approves them at
*Administration → Accounts*. Change the admin password before exposing the app anywhere.

---

## 5. Stop everything

`Ctrl+C` in each terminal, then:

```powershell
docker compose down
```

Do **not** add `-v` — that deletes the database volumes and every project, issue and account.

---

## 6. Tests

```powershell
cd services\work-service
.\mvnw.cmd test                    # unit tests, nothing needs to be running
```

```bash
bash scripts/smoke-test.sh         # end-to-end through the gateway; the whole stack must be up
```

---

## 7. When something goes wrong

| Symptom | Cause | Fix |
|---|---|---|
| `Port 8081 was already in use` | another app on the port (often phpMyAdmin in Docker) | stop it: `docker stop <container>`, or find the process with `Get-NetTCPConnection -LocalPort 8081` |
| Gateway won't start on 8090 | another program holds 8090 | stop that program |
| Gateway returns 404 for a service | Eureka has not propagated yet | wait ~30s, check http://localhost:8761 |
| work-service: `required a bean of type ...Mapper` | stale build without MapStruct classes | `.\mvnw.cmd clean compile`, then run again |
| classification-service: `onnxruntime.dll: A dynamic link library (DLL) initialization routine failed` | old JDK 21 build | install a recent JDK 21 (21.0.12+) and set `JAVA_HOME` to it |
| Frontend: `Cannot find native binding` | Node older than 20.19 | upgrade Node, then delete `node_modules` and run `npm install` |
| `docker compose up` fails pulling images | network hiccup | run it again |

---

## Deploy

Every service, the frontend and Postgres have a `Dockerfile`. **`docs/DEPLOY-RAILWAY.md`** walks
through deploying the whole stack to Railway: which services to create, their variables, and what
to expect on the first deploy.

---

## Documentation

- `docs/DEPLOY-RAILWAY.md` — deploying to Railway
- `docs/PROJECT.md` — milestones
- `docs/DATA-MODEL.md` — every table and foreign key
- `docs/services/` — one page per service, with diagrams
- `docs/work-management-class-diagram-v2.mermaid` — domain model
- `docs/workflow/` — screen mockups, UML and the project presentation
