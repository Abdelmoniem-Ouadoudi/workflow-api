# BACKLOG.md — deliberately deferred work

Nothing here is forgotten. Each line says what is missing, why it was deferred, and when it lands.

| # | Missing | Why deferred | Lands at |
|---|---|---|---|
| 1 | `GET /issues` returns every match, no pagination | the Kanban reads `/boards/{id}/view`, which returns whole columns and never touches the paginated list. Paging changes the response shape, so it should land with the screen that needs it | when an issue table exists |
| 2 | No multipart upload — `POST /issues/{id}/attachments` stores metadata only | the bytes belong on disk or in object storage; the row never holds them. Needs a storage root and a size limit decision | M1, with the frontend |
| 3 | No length limit on `project.description`, `sprint.goal`, `issue.description` | the columns are `text`; Tomcat's request size cap is the practical bound | only if it becomes a problem |
| ~~4~~ | ~~No authentication or authorization~~ | **Done at M2.** Gateway + work-service both verify a JWT. Every route but `/auth/register`, `/auth/login` and health needs a token | — |
| ~~5~~ | ~~No `password_hash` on `app_user`~~ | **Done at M2**, but not where this line expected. The hash lives on `account` in `authdb`, owned by auth-service; `app_user` never gains a password column, because the profile and the credential have different owners | — |
| ~~6~~ | ~~No CORS configuration~~ | **Done.** `CorsConfig` allows `http://localhost:5173`. | — |
| ~~6b~~ | ~~Remove the standalone `CorsFilter` from work-service~~ | **Done at M2**, and then done properly: work-service has **no CORS configuration at all** now. Keeping the bean and wiring it through `SecurityFilterChain` still sent `Access-Control-Allow-Origin` twice, because the gateway adds its own and forwards the service's. CORS belongs to the gateway, the only address a browser calls | — |
| 8 | `IssueDTO` has `assigneeId` but no `assigneeUsername` | resolving the name per issue is an N+1. `IssueSummaryDTO` already carries it via a constructor projection, which is what the board renders | when a screen needs it outside the board |
| 9 | No JUnit tests beyond `contextLoads` | `scripts/smoke-test.sh` covers every request/response case against a running system, but it is not a unit test suite and does not run in `mvn verify` | next task after M2 |
| 10 | Deleting a user is impossible, only deactivation | `issue.reporter_id` is `ON DELETE RESTRICT` on purpose — deleting the reporter would destroy history | by design, not a gap |
| 11 | No client-side routing — the frontend switches screens by state, the URL never changes | three screens still do not justify a router dependency; you cannot refresh into a board or share a link to one | when a fourth screen arrives |

## Added at M2

| # | Missing | Why deferred | Lands at |
|---|---|---|---|
| 12 | **HS256, one shared secret.** auth-service signs and the other two verify with the same key, so any of them could in principle mint a token | acceptable while all four services deploy together from one repository. RS256 — private key in auth-service, public half published as a JWK set — is the production answer, and the verifying code does not change, only the decoder's source of key material. Weighed against it: an in-memory keypair dies on restart and invalidates every issued token, which is a live hazard during a defence | when the services deploy separately |
| 13 | **Registration is a dual write with no retry.** work-service gets the profile, then auth-service saves the credential. If the second write fails, a profile exists that nobody can log in as | the real fixes are an outbox table or an idempotent retry that reuses an existing profile, and both are a milestone of their own. The failure is logged with the orphaned id rather than swallowed, so it can be found | when a second cross-service write appears |
| 14 | **No refresh token.** A token lasts one hour and then you sign in again | a refresh token needs storage, rotation and a revocation list, which is the point at which sessions become a subsystem. One hour is longer than any demo and short enough that a leaked token expires by itself | if a real user complains |
| 15 | **One role rule only:** `DELETE /users/**` requires ADMIN | the domain model does not yet say who may close a sprint or delete a project. Inventing rules the class diagram does not state would be guessing, and a wrong rule is worse than a missing one. The mechanism is built and proven by this one rule | when the domain says who may do what |
| 16 | **No rate limit and no lockout on login** | brute force is a real attack on a real deployment. It needs a shared counter, which means Redis or a database table, and a lockout policy that can itself be used to lock someone out on purpose | before anything is exposed outside localhost |
| 17 | **auth-service does not store the email**, it forwards it to work-service and forgets it | keeping a copy would make two rows to change when someone updates their address, with no owner for the disagreement. The cost is that password reset by email would have to ask work-service first | when password reset is built |
| 18 | **The token is kept in `localStorage`** | any script on the origin can read it. The alternative, an HttpOnly cookie, cannot be read by JavaScript but is attached automatically, which is what makes CSRF possible and would mean bringing back the defences the stateless API deliberately does without. Short token lifetimes are the mitigation | if the app is deployed publicly |
| 19 | **One Eureka instance, one gateway instance** | both are single points of failure and both are stated as decisions in `ARCHITECTURE-NOTES.md`, not discovered as gaps. Clustering Eureka is a configuration change, not a redesign | never, for this project |
| 20 | **The gateway does not proxy Swagger UI** | routes are declared by resource path (`/projects/**`, `/issues/**`), and adding `/v3/api-docs` would put the contract behind the edge for no gain. The docs are read on 8081 directly | if the services stop being reachable directly |
