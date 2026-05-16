# E-Commerce Platform

## Stack

- **Frontend:** React 19 (TypeScript) + React Compiler + Bootstrap 5
- **Backend:** Spring Boot 4 (WebFlux) on JDK 21 — Reactive, Non-blocking
- **Database:** PostgreSQL via R2DBC

## Build & Run

```bash
# Frontend
npm install && npm run dev

# Backend
./mvnw spring-boot:run

# Tests
npm test          # frontend
./mvnw test       # backend
```

## Rules

Detailed coding rules live in `.claude/rules/`:

- [code-style.md](.claude/rules/code-style.md) — TypeScript, React, Bootstrap conventions
- [api-conventions.md](.claude/rules/api-conventions.md) — Reactive backend, DB, security
- [testing.md](.claude/rules/testing.md) — Test standards
- [git.md](.claude/rules/git.md) — Commit and migration policy
