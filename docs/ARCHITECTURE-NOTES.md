# ARCHITECTURE-NOTES.md — what to build, and what to say

Rules for M2 and M3. Each item says what to build, when, and the one sentence to answer with
if a jury asks. Nothing here is optional once its milestone starts.

---

## 0. Why microservices at all

**Asked first, always. Answer before they ask.**

> The brief requires demonstrating the microservices pattern — gateway, service discovery,
> async messaging, independent deployability. It is not solving a scale problem. Two users
> would run fine on the monolith that M1 already is.

Do not defend microservices on performance or scale. That argument loses.
`docs/PROJECT.md` already backs this up: M1 is deliberately one service, and the split happens
at M2 only because auth arrives.

---

## 1. Correlation ID across every service — **M2**

**Build:** the Gateway generates `X-Correlation-Id` if the request has none, and passes it on.
Every service reads it into the logging MDC so it appears on every log line. It must survive the
RabbitMQ hop at M3 — carry it in the message header, not just the HTTP header.

**Why:** a request crosses Gateway → work-service → RabbitMQ → classification-service → back.
Without one id tying those logs together, "why was this classification slow" is unanswerable.

> Every log line carries the correlation id set at the gateway, so one request is one grep
> across all services, including across the queue.

Cost: a filter in the gateway, a filter in each service, one line in the logging pattern.

---

## 2. Dead-letter queue on RabbitMQ — **M3**

**Build:** `issue.created` gets a DLQ with a bounded retry count. A message that fails N times
goes to the dead-letter queue, not back onto the main queue.

**Why:** `PROJECT.md` M3 covers "Groq key removed, tickets still get created". It does **not**
cover "Groq key present but every call throws". Without a DLQ that message either requeues
forever or vanishes. Both are wrong, and both are invisible.

> Resilience4j retries the transient failures. After the retries are exhausted the message goes
> to a dead-letter queue instead of poisoning the main one, so one bad issue cannot stop
> classification for everyone else.

This is the sharpest technical gap in the current plan. Do not skip it.

---

## 3. Single Eureka instance — **M2, stated not fixed**

**Build:** nothing. One instance is correct for this project.

**Why it is written down:** it is a single point of failure. Say it yourself, as a decision.
If the jury finds it first, it looks like an oversight.

> Eureka runs as a single instance because this is a demo environment, not production HA.
> Clustering it is a known extension, not something I missed.

---

## 4. Circuit breaker on gateway → internal services — **M2**

**Build:** Resilience4j on the Gateway routes, not only on the Groq call.

**Why:** M3 already puts a circuit breaker on classification → Groq because Groq is external and
flaky. Internal services also fail. Protecting one and not the other is an inconsistency a jury
will notice the moment resilience is mentioned.

> The same reasoning that protects the Groq call protects the internal calls: any remote call can
> hang, and a hung call upstream should fail fast rather than exhaust the gateway's threads.

---

## 5. API versioning — **answer ready, build only if time**

**Build:** nothing yet. Have the answer.

> The contract is generated from the code into `/v3/api-docs`, so the frontend regenerates its
> client rather than drifting. A breaking change to a DTO would go out as `/v2` alongside `/v1`;
> until there is a second consumer, versioning would be ceremony with no reader.

---

## Deliberately not doing

Say these as decisions if asked, not as gaps:

| Not doing | Reason |
|---|---|
| Kubernetes | out of scope in `PROJECT.md`; Docker Compose matches the deployment target |
| Config server | four services with local profiles; a config server adds a component and solves nothing here |
| Splitting the database further | Auth DB / Core DB / pgvector is already the right seam — auth data has a different lifecycle and blast radius |
| Saga / distributed transactions | the only cross-service write is classification, which is eventually consistent by design — the issue exists whether or not it gets classified |
