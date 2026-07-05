---
name: security-hardening
description: "Security hardening pass (2026-07-04): dispute-evidence IDOR fix, JWT audience+refresh tokens, bootstrap-admin guard, orderItemId validation, admin dashboard split to its own app/port, post-deploy secrets doc. Builds green; runtime E2E not yet done. UNCOMMITTED."
metadata:
  node_type: memory
  type: project
---

Security hardening implemented 2026-07-04 off the `/security-review`-style audit findings. Backend compiles (mvn) and BOTH frontend apps build (storefront + admin). **Status: UNCOMMITTED. Runtime E2E NOT yet performed** — verify the items in "Needs runtime testing" before shipping.

## What changed

1. **Dispute evidence IDOR fixed.** Evidence files moved OUT of public `/uploads/disputes/**` (was `.authenticated()` static-served → any logged-in user could read any dispute's files by URL). Now stored in a PRIVATE dir `dispute.storage-dir` (default `dispute-private/`, gitignored) and streamed only via a party-checked endpoint `GET /api/disputes/{disputeId}/attachments/{attachmentId}/file` → `DisputeService.attachmentForViewer` (requireParty + attachment-belongs-to-dispute check), mirroring KYC `documentForViewer`. Stored attachment `url` now points at that API path. `AccessRules` line for `/uploads/disputes/**` changed to `.denyAll()` (belt-and-suspenders). Frontend `AuthMedia`/`AdminAuthMedia` already fetch with Bearer token → work unchanged.

2. **JWT hardening** (`JwtTokenProvider` rewritten). Tokens now carry `aud` (STOREFRONT|ADMIN), `type` (access|refresh), and `jti`. **Audience isolation**: `JwtAuthenticationFilter` rejects any non-ADMIN-audience token on `/api/admin/**` (except `/api/admin/auth/**`) — so a stolen storefront token can't reach admin APIs even for an ADMIN account. Access TTL cut 24h→1h (`jwt.expiration` default 3600000); refresh 7d. New endpoints `POST /api/auth/refresh` + `POST /api/admin/auth/refresh` (`UserService.refreshStorefront/refreshAdmin`, audience+role+active checked, rotation). `AuthResponse` gained `refreshToken`. Frontend: `authSlice` stores `token`+`refreshToken`; `api.ts` has single-flight refresh-on-401 → retry, surface-aware (`/admin/auth/refresh` vs `/auth/refresh`). Auth is still stateless — **no per-token revocation** (documented as a future `jti` denylist).

3. **Bootstrap admin guard** (`AdminBootstrap`). Added `admin.bootstrap.enabled` (default true) to disable the fallback seed in prod; loud WARN when the default password `secret` is in use. Defaults kept for dev per user's instruction (item 1). Config surfaced under `admin.bootstrap.*` in application.yml.

4. **orderItemId validation.** `DisputeService.open` now verifies a supplied `orderItemId` belongs to the disputed order (`validateOrderItem`, injects `OrderItemRepository`).

5. **Admin dashboard = independent app.** Split the single SPA: storefront `router.tsx` (no admin routes) on :5173; NEW admin app `adminRouter.tsx` + `src/admin-main.tsx` + `admin.html` + `vite.admin.config.ts` on **:5174** (`npm run dev:admin` / `build:admin` → `dist-admin/`; SPA-fallback plugin serves admin.html). Storefront `/admin/*` now 404s. Backend CORS split: `cors.admin-origins` (default :5174) restricts `/api/admin/**` at the browser layer (both `SecurityConfig` and `WebFluxConfig`, admin pattern registered FIRST). Storefront navbar "Admin Console" is now an external `<a href={VITE_ADMIN_URL}>` full-page link (default http://localhost:5174/admin).

6. **Post-deploy secrets doc**: `docs/POST_DEPLOYMENT_SECRETS.md` — vault storage, config wiring, password policy, per-secret utilisation, go-live checklist. Seed/placeholder secrets (JWT default, V2 `admin123`, bootstrap `admin`/`secret`) intentionally LEFT in place for dev; doc covers overriding them.

## Needs runtime testing (not yet verified)
- Dev admin SPA-fallback (`vite.admin.config.ts` plugin) serving `/admin/*` on :5174.
- Refresh-on-401 flow end-to-end (access expiry → silent refresh → retry) on both surfaces.
- CORS: storefront-origin (5173) browser call to `/api/admin/**` is blocked; admin-origin (5174) works.
- Audience rejection: a STOREFRONT token returns 401/403 on an admin endpoint.
- Existing DB rows with legacy `/uploads/disputes/...` attachment URLs won't resolve (dev reset expected).

Related: [[marketplace-escrow-feature]] (dispute evidence + orderItemId), [[account-verification-feature]], [[seller-kyc-feature]] (party-checked file pattern reused), [[dev-environment-toolchain]] (ports: storefront 5173 / admin 5174 / backend 8081).
