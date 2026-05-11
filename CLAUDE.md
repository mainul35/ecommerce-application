# Development Guidelines - E-Commerce Platform (Reactive & Extensible)

## 🏗 Tech Stack
- **Frontend:** React 19 (TypeScript) + React Compiler + Bootstrap 5.
- **Backend:** Spring Boot 4 (WebFlux) on **JDK 21** - Reactive, Non-blocking REST API.
- **Admin/Backoffice:** Spring Boot 4 (WebFlux) + React 19 Dashboard.
- **Database:** PostgreSQL with **R2DBC** (Reactive driver).
- **Dynamic Schema:** PostgreSQL `JSONB` for flexible product attributes.

## ✅ Claude MUST Do (Strict Requirements)
- **TypeScript Only:** All frontend code must use strict TypeScript. Define interfaces for all API responses and component props.
- **React Compiler Optimization:** Write standard React; do not manually use `useMemo` or `useCallback` unless the compiler specifically fails to optimize.
- **Mobile-First Design:** Use Bootstrap's grid system (`col-12 col-md-6`) for all UI. Ensure the management panel is fully usable on tablets/phones.
- **Non-Blocking Logic:** Every backend operation must be reactive. Controllers/Services must return `Mono<T>` or `Flux<T>`.
- **Dynamic Attributes:** Use `JSONB` for the product `attributes` column to allow new product categories without DB migrations.
- **Clean Architecture:** Strictly follow the Controller -> Service -> Repository pattern. Keep business logic out of the Controller.
- **Security:** Use Spring Security Reactive with JWT. Use `BCrypt` for password hashing.
- **DTO Mapping:** Map Entities to DTOs using MapStruct or manual mappers to avoid exposing internal DB structures.
- **Validation:** Use Bean Validation (JSR 380) on all incoming request objects.

## ❌ Claude MUST NOT Do (Forbidden Practices)
- **No Blocking Calls:** Never use `.block()` or `.subscribe()` in the backend code. Never use standard JDBC/JPA (blocking).
- **No Manual Memoization:** Avoid `React.memo`, `useMemo`, and `useCallback` to let the **React Compiler** handle optimizations automatically.
- **No Hardcoding:** Never hardcode credentials or URLs. Use `application.yml` or `.env` files.
- **No Inline CSS:** Do not use `style={{...}}` in React. Use Bootstrap utility classes or SCSS.
- **No Monolithic Queries:** Do not fetch all records; always implement Reactive Pagination and Sorting.
- **No Raw SQL:** Use Spring Data R2DBC or QueryDSL to prevent SQL Injection.
- **No ThreadLocal:** Do not rely on `ThreadLocal` (e.g., standard SecurityContext) as it is incompatible with the Reactive event-loop.
- **No New Flyway Migrations Per Change:** Do not generate a new Flyway migration SQL file for every schema change. Update the existing migration file instead, and only create a new versioned migration when the previous one has already been applied to a shared/production environment.

## 🎨 Frontend & Design Standards
- **Responsiveness:** Test all components across breakpoints (xs, sm, md, lg, xl).
- **State Management:** Use Redux Toolkit for complex global state (Cart, Authentication, User Profile).
- **Customization:** Override Bootstrap defaults via SCSS variables in `src/assets/scss`.
- **Components:** Reusable UI logic must live in `src/components`, separate from page-level logic in `src/pages`.

## ⚙️ Backend & DB Standards
- **Database:** Use `R2DBCEntityTemplate` for complex dynamic queries.
- **Error Handling:** Use a `@ControllerAdvice` or `WebExceptionHandler` to return consistent JSON error objects.
- **Documentation:** Use SpringDoc OpenAPI (WebFlux version) for API documentation.

## 🚀 Build & Run Commands
- **Frontend:** `npm install` && `npm run dev`
- **Backend:** `./mvnw spring-boot:run`
- **Tests:** `npm test` (Frontend) & `./mvnw test` (Backend)
