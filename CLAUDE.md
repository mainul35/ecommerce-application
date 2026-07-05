# E-Commerce Platform

## Stack

- **Frontend:** React 19 (TypeScript) + React Compiler + Bootstrap 5
- **Backend:** Spring Boot 4 (WebFlux) on JDK 21 — Reactive, Non-blocking
- **Database:** PostgreSQL via R2DBC

## Build & Run

```bash
# Frontend — two SEPARATE apps (isolated origins/ports):
npm install
npm run dev          # storefront  -> http://localhost:5173
npm run dev:admin    # admin console -> http://localhost:5174 (independent server)

# Backend
./mvnw spring-boot:run

# Tests
npm test          # frontend
./mvnw test       # backend
```

The admin dashboard is a standalone app (`admin.html` / `src/admin-main.tsx`,
`vite.admin.config.ts`) that cannot be reached from the storefront. The backend
restricts `/api/admin/**` to the admin origin via CORS and rejects non-admin-
audience tokens. Secrets: see [docs/POST_DEPLOYMENT_SECRETS.md](docs/POST_DEPLOYMENT_SECRETS.md).

## Rules

Detailed coding rules live in `.claude/rules/`:

- [code-style.md](.claude/rules/code-style.md) — TypeScript, React, Bootstrap conventions
- [api-conventions.md](.claude/rules/api-conventions.md) — Reactive backend, DB, security
- [testing.md](.claude/rules/testing.md) — Test standards
- [git.md](.claude/rules/git.md) — Commit and migration policy
