-- The User entity is mapped to schema "auth"; Hibernate's schema export creates tables, not
-- schemas, so the container needs this before ddl-auto can run.
CREATE SCHEMA IF NOT EXISTS auth;
