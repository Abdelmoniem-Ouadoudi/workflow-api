-- Runs once, only when the data volume is empty.
-- POSTGRES_DB already created "workflow" (the core database). auth-service owns a second one.
-- Two databases in one Postgres process: work-service physically cannot join to auth data,
-- which is the point. Sharing the process is a deployment cost, not a coupling.
CREATE DATABASE authdb OWNER workflow;
