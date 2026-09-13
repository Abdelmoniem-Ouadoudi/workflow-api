-- Runs once, only when the data volume is empty.
-- POSTGRES_DB already created "workflow" (the core database). auth-service owns a second one.
-- Two databases in one Postgres process: work-service physically cannot join to auth data,
-- which is the point. Sharing the process is a deployment cost, not a coupling.
CREATE DATABASE authdb OWNER workflow;

-- M4. classification-service's working memory: one vector per issue, used to answer "has somebody
-- already reported this". Its own database for the same reason authdb is: each service owns its
-- data, and work-service has no connection to this one. The vectors are not part of the issue
-- record - they are derived, and they can be rebuilt from scratch by replaying issue.created.
CREATE DATABASE vectordb OWNER workflow;
