# BACKLOG.md — deliberately deferred work

Nothing here is forgotten. Each line says what is missing, why it was deferred, and when it lands.

| # | Missing | Why deferred | Lands at |
|---|---|---|---|
| 1 | `GET /issues` returns every match, no pagination | filters cover the current volume; paging changes the response shape, so it must land with the React table that consumes it | M1, with the frontend |
| 2 | No multipart upload — `POST /issues/{id}/attachments` stores metadata only | the bytes belong on disk or in object storage; the row never holds them. Needs a storage root and a size limit decision | M1, with the frontend |
| 3 | No length limit on `project.description`, `sprint.goal`, `issue.description` | the columns are `text`; Tomcat's request size cap is the practical bound | only if it becomes a problem |
| 4 | No authentication or authorization — every endpoint is open | `auth-service` and the gateway are M2 by design in `PROJECT.md` | M2 |
| 5 | No `password_hash` on `app_user` | there is no login at M1, so the column would sit empty and unvalidated | M2, new migration |
| 6 | No CORS configuration | no frontend yet; config nothing calls is config nothing tested | M1, with the React app |
| ~~7~~ | ~~No OpenAPI / Swagger UI~~ | **Done.** springdoc 3.0.0 (the Boot 4 line; 2.x is Boot 3 only). `/swagger-ui.html` and `/v3/api-docs` | — |
| 8 | `IssueDTO` has `assigneeId` but no `assigneeUsername` | resolving the name per issue is an N+1. `IssueSummaryDTO` already carries it via a constructor projection, which is what the board renders | when a screen needs it outside the board |
| 9 | No JUnit tests beyond `contextLoads` | `scripts/smoke-test.sh` covers all 83 request/response cases against a running app, but it is not a unit test suite and does not run in `mvn verify` | next task after the frontend |
| 10 | Deleting a user is impossible, only deactivation | `issue.reporter_id` is `ON DELETE RESTRICT` on purpose — deleting the reporter would destroy history | by design, not a gap |
