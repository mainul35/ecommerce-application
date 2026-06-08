---
name: dev-environment-toolchain
description: "No system JDK/Maven — use IntelliJ bundled JBR+Maven; karimen project owns ports 5432 AND 8080, ecommerce runs on DB 5433 / HTTP 8081 via gitignored local overrides"
metadata: 
  node_type: memory
  type: project
  originSessionId: 0fc4d7f4-3030-4208-a38a-0d56bb2a26b7
---

This Windows machine has NO system-wide `java` or `mvn` (and the repo has no `mvnw` wrapper despite CLAUDE.md referencing it). Build the backend with IntelliJ's bundled tools:

- `$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\IntelliJ IDEA\jbr"` (JBR 25)
- Maven: `"$env:LOCALAPPDATA\Programs\IntelliJ IDEA\plugins\maven\lib\maven3\bin\mvn.cmd"`

**Port collisions with the user's other project (karimen):** `karimen-db` (postgres:16, user/db `karimen`) owns **5432**, and `karimen-honmen-server` (a jar run, 401s on /api/*) owns **8080**. Do NOT kill these — they are an actively used separate project.

**Ecommerce local setup (decided 2026-06-07):** DB = `ecommerce-it-db` container on **5433** (`pgvector/pgvector:pg16`, postgres/postgres, restart unless-stopped, no named volume — Flyway recreates schema). Backend HTTP = **8081**. Wired via gitignored machine-local overrides:

- `config/application.yml` AND `backend/config/application.yml` (both, because the IDE run's working dir is the repo root, not backend/): r2dbc+flyway URLs → :5433, `server.port: 8081`, `app.backend-url`
- `frontend/.env.local`: `VITE_API_BASE_URL=http://localhost:8081/api`

Recreate the DB if removed:
`docker run -d --restart unless-stopped --name ecommerce-it-db -e POSTGRES_DB=ecommerce -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5433:5432 pgvector/pgvector:pg16`

Postgres image must be **pgvector/pgvector:pg16** (plain postgres lacks `vector` needed by V15). The compose stack is unused on this machine.
