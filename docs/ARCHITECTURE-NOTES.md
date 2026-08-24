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

## 1. Correlation ID across every service — **M2 · BUILT**

**Build:** the Gateway generates `X-Correlation-Id` if the request has none, and passes it on.
Every service reads it into the logging MDC so it appears on every log line. It must survive the
RabbitMQ hop at M3 — carry it in the message header, not just the HTTP header.

**Built as:** `CorrelationIdFilter` in all three services, at `HIGHEST_PRECEDENCE` so it runs
ahead of Spring Security and a rejected request is logged with an id too. The gateway wraps the
request in an `HttpServletRequestWrapper` that reports the header as if the caller had sent it,
which is how the id reaches the services rather than only the response. auth-service puts it back
on its outbound call to work-service through a `RestClient` interceptor.
Logging pattern: `%5p [${spring.application.name},%X{corrId:-no-corr-id}]`.

One thing it cost, worth saying because it is the same mistake three times: at first every service
also set the header on its *response*, so a call through the gateway came back with
`X-Correlation-Id` twice. A service now stamps the response only when it generated the id itself —
that is, when the call did not come through the gateway.

The other two were the M1 `CorsFilter`, and then `Access-Control-Allow-Origin` arriving twice
because work-service still configured CORS after the gateway existed — which broke every request
in the browser while curl saw a clean 200.

**The general rule:** a gateway merges two sets of response headers, so any header both ends set
is a duplicate waiting to happen. Decide which end owns each one. CORS and the correlation id are
the gateway's.

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

## 4. Circuit breaker on gateway → internal services — **M2 · BUILT**

**Build:** Resilience4j on the Gateway routes, not only on the Groq call.

**Built as:** a `CircuitBreaker` filter on each of the two routes, with `fallbackPath` pointing at
`FallbackController`, which answers 503 in the same `ApiError` envelope every service uses — so
the React error handler cannot tell whether the refusal came from a service or from the gateway
standing in for one. A 5-second time limiter turns a hung call into a counted failure; the breaker
opens at a 50% failure rate over 5 calls and half-opens after 10 seconds.

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

## 6. The token is verified twice — **M2 · BUILT**

**Build:** the gateway verifies the JWT, and work-service verifies it again.

**Why:** this is the question a jury asks the moment a gateway appears.

> The gateway is not the security boundary. work-service listens on 8081 and anything on the
> network can call it directly, so it validates the token itself. The gateway is where a bad
> request fails cheaply, before it costs a service anything.

Demonstrable, not asserted: `curl localhost:8081/projects` with no token returns 401.

Two things follow from it. Registration happens before the person has a token, so auth-service
mints a 60-second token for itself with `role=SERVICE` and work-service requires `ROLE_SERVICE`
on `POST /users` — one authentication scheme, no permit-all hole punched for registration. And
`reporterId` and `authorId` now come from the `uid` claim rather than the request body, which
before M2 meant any caller could file an issue in someone else's name.

---

## 7. HS256 with one shared secret — **M2, stated not fixed**

**Build:** nothing more. Say it before it is found.

> Symmetric, because all four services deploy together from one repository. The weakness is real:
> any service holding the secret could also mint a token, not just verify one. Production splits
> this into RS256 — the private key stays in auth-service, everyone else gets the public half —
> and not one line of the verifying code changes, because Spring Security validates a JWT the same
> way either way.

The reason it was not built that way now: the keypair would be generated at startup, so restarting
auth-service would invalidate every token in existence. That is a live hazard during a defence for
a property nobody is attacking.

---

## Deliberately not doing

Say these as decisions if asked, not as gaps:

| Not doing | Reason |
|---|---|
| Kubernetes | out of scope in `PROJECT.md`; Docker Compose matches the deployment target |
| Config server | four services with local profiles; a config server adds a component and solves nothing here |
| Splitting the database further | Auth DB / Core DB / pgvector is already the right seam — auth data has a different lifecycle and blast radius |
| Saga / distributed transactions | the only cross-service write is classification, which is eventually consistent by design — the issue exists whether or not it gets classified |
