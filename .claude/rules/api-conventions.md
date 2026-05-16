# Backend API Conventions

## Reactive / Non-blocking

- Every controller and service method must return `Mono<T>` or `Flux<T>`.
- Never use `.block()`, `.subscribe()`, standard JDBC/JPA, or `ThreadLocal`.
  `ThreadLocal` is incompatible with the reactive event loop.

## Architecture

- Strict layering: Controller → Service → Repository.
- Keep all business logic in Services; Controllers only route and validate.

## Database

- Use Spring Data R2DBC for standard queries.
- Use `R2DBCEntityTemplate` for complex dynamic queries.
- Never write raw SQL strings — prevents SQL injection.
- Always paginate: never fetch unbounded result sets.
- Use `JSONB` for the product `attributes` column so new categories need no migration.

## Security

- Spring Security Reactive with JWT for authentication.
- `BCrypt` for password hashing.
- Map entities to DTOs via MapStruct or manual mappers — never expose DB models directly.
- Apply Bean Validation (JSR 380) on all incoming request objects.

## Error Handling & Docs

- Use a `@ControllerAdvice` / `WebExceptionHandler` to return consistent JSON errors.
- Document all endpoints with SpringDoc OpenAPI (WebFlux variant).

## Config

- Never hardcode credentials or URLs — use `application.yml` or `.env` files.
