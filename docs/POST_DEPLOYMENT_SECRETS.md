# Post-Deployment Secrets — Operator Runbook

**Purpose.** Replace the repository's dev placeholder/seed secrets with real, vault-managed
values before any shared, staging, or production deploy. Follow the sections in order:
inventory → where to store → how to configure → policy → utilization → go-live checklist.

> **⚠️ Warning:** The repo ships with placeholder/seed secrets so the app boots on a fresh
> dev machine with zero config. These defaults are intentional for local development and
> **must never reach a shared, staging, or production environment unchanged.**

> **Note:** Nothing in the seed data or `application.yml` was removed — the defaults remain
> for dev. Everything below is about **overriding** them safely in real environments.

---

## 1. Inventory of secrets that MUST be rotated before go-live

Every secret below resolves from an environment variable with a **committed fallback**.
The fallback is fine for `localhost`; it is a takeover risk anywhere else.

| Secret (env var) | Committed default (dev only) | Where it lives | Severity if left as-is |
|---|---|---|---|
| `JWT_SECRET` | `your-256-bit-secret-key-for-jwt-signing-must-be-at-least-32-characters` | `application.yml` → `jwt.secret` | **CRITICAL** — anyone can forge a token for any user, including ADMIN |
| Seed admin login | `admin@ecommerce.com` / `admin123` | `db/migration/V2__seed_data.sql` | **CRITICAL** — known-password admin auto-created by Flyway |
| `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` | `admin` / `secret` | `AdminBootstrap.java` | **CRITICAL** — second guessable admin on first boot |
| `DB_PASSWORD` | `postgres` | `application.yml` → `spring.r2dbc` / `spring.flyway` | HIGH |
| `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` | `sk_test_PLACEHOLDER…` / `whsec_PLACEHOLDER…` | `application.yml` → `stripe.*` | HIGH (payments) |
| `SSLCOMMERZ_*`, `PAYPAY_*`, `OMISE_*`, `LINEPAY_*` | `PLACEHOLDER` | `application.yml` → `payment.*` | HIGH (payments, per region) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | empty (Mailpit, no auth) | `application.yml` → `spring.mail` | MEDIUM |
| `OLLAMA_URL` | `http://localhost:11434` | `application.yml` → `ollama.url` | LOW (internal service URL) |

> **Note:** The seed admin (`V2__seed_data.sql`) and the bootstrap admin
> (`AdminBootstrap.java`) are **left in place on purpose** — they let a developer log in
> immediately. Section 3 covers how to neutralise them in production without editing the
> committed files.

---

## 2. Where to store the secrets — a secret vault

> **⚠️ Warning:** **Never** put real secrets in `application.yml`, a `.env` committed to
> git, container images, or CI logs.

Use a managed secret store. Recommended options, in order of preference:

1. **HashiCorp Vault** (self-hosted or HCP) — dynamic DB creds, leasing, and audit.
   Integrate with `spring-cloud-vault` so secrets are pulled at startup into the same
   `${JWT_SECRET}` / `${DB_PASSWORD}` placeholders already in `application.yml`.
2. **Cloud-native managers** — AWS Secrets Manager / SSM Parameter Store (SecureString),
   GCP Secret Manager, or Azure Key Vault. Inject at runtime as env vars via the
   platform (ECS task definition `secrets:`, K8s `secretKeyRef`, etc.).
3. **Kubernetes Secrets sealed with SOPS or Sealed-Secrets** — acceptable if the cluster
   has encryption-at-rest enabled and RBAC is tight.

> **⚠️ Warning:** Plain (unsealed) K8s Secrets are base64, *not* encryption — do not treat
> them as a vault.

**Hard rules**

- One vault path/namespace **per environment** (dev / staging / prod) — never share a
  `JWT_SECRET` across environments (a leak in staging must not compromise prod).
- Grant read access by **workload identity**, not a shared human account.
- Turn on the vault's **audit log**; alert on secret reads outside deploy windows.
- Keep an offline **break-glass** copy of the vault unseal/root credentials in a separate
  system (e.g. a physical safe or a second org's password manager).

---

## 3. How to configure — wiring the vault into this app

The application already reads every secret from an env var with the pattern
`${ENV_VAR:default}`. So **no code change is required** — you only provide the env vars
from the vault at runtime. Do the following per environment.

### 3.1 Generate strong values

Generate cryptographically random values to replace the committed placeholders:

```bash
# JWT signing key: 256-bit (32-byte) minimum for HS256. Prefer 64 bytes.
openssl rand -base64 64        # -> JWT_SECRET

# DB password, service passwords, etc.
openssl rand -base64 32
```

### 3.2 Store them in the vault (example: AWS Secrets Manager)

Write each generated value into the vault under a per-environment name:

```bash
aws secretsmanager create-secret --name prod/ecommerce/jwt-secret \
  --secret-string "$(openssl rand -base64 64)"
aws secretsmanager create-secret --name prod/ecommerce/db-password \
  --secret-string "…"
# …repeat for STRIPE_SECRET_KEY, payment gateway keys, MAIL_PASSWORD, etc.
```

### 3.3 Inject at runtime (never bake into the image)

Map each vault value to the env var the app expects. Kubernetes example — env sourced from
a Secret that an external-secrets operator syncs from the vault:

```yaml
env:
  - name: JWT_SECRET
    valueFrom: { secretKeyRef: { name: ecommerce-secrets, key: jwt-secret } }
  - name: DB_PASSWORD
    valueFrom: { secretKeyRef: { name: ecommerce-secrets, key: db-password } }
  - name: ADMIN_BOOTSTRAP_PASSWORD
    valueFrom: { secretKeyRef: { name: ecommerce-secrets, key: admin-bootstrap-password } }
  - name: STRIPE_SECRET_KEY
    valueFrom: { secretKeyRef: { name: ecommerce-secrets, key: stripe-secret-key } }
  # …one entry per row in the Section 1 table
```

### 3.4 Neutralise the seed / bootstrap admins (without editing committed files)

- **Bootstrap admin:** set `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` to real,
  vault-managed values on **first** boot. The seeder is idempotent (skips if the email
  exists), so it will create the admin with your strong password, not `admin`/`secret`.
- **Seed admin (`admin@ecommerce.com` / `admin123`):** it is inserted by Flyway `V2`.
  Immediately after the first production migration, **rotate or disable it** with one of
  the statements below:
  ```sql
  -- Option A: disable it entirely and rely on the bootstrap admin.
  UPDATE users SET is_active = false WHERE email = 'admin@ecommerce.com';
  -- Option B: rotate to a strong BCrypt hash generated out-of-band.
  UPDATE users SET password = '$2a$10$…newhash…' WHERE email = 'admin@ecommerce.com';
  ```
  Automate this as a one-shot post-deploy job so no environment ever runs with `admin123`.

### 3.5 Fail-fast guard (recommended follow-up)

Add a startup check (e.g. in a `@PostConstruct` or an `ApplicationRunner` gated on a
non-`dev` Spring profile) that **refuses to boot** if:

- `JWT_SECRET` still equals the committed placeholder, or
- `ADMIN_BOOTSTRAP_PASSWORD` is `secret`.

This converts a silent misconfiguration into a loud deploy failure.

> **Note:** This guard is deliberately not enabled by default so local dev keeps working
> with zero config.

---

## 4. Password / secret policy

Applies to every value in the Section 1 inventory.

**Machine secrets (JWT key, DB/service passwords, API keys)**

- **Length/entropy:** ≥ 32 bytes of CSPRNG output (`openssl rand`). JWT HS256 key ≥ 256 bits.
- **Uniqueness:** distinct per secret and per environment. No reuse across dev/staging/prod.
- **No human-memorable values.** These are never typed; they live only in the vault.
- **Rotation cadence:**
  - `JWT_SECRET`: rotate every 90 days and on any suspected compromise. Support overlap
    (accept the previous key for the access-token lifetime) to avoid mass logout — or
    rotate during a low-traffic window and accept a forced re-login.
  - DB and payment-gateway keys: rotate every 90 days or per the provider's guidance;
    immediately on staff offboarding or leak.
  - Bootstrap/seed admin password: rotate on first deploy, then treat as a normal
    privileged human credential (below).

**Human admin credentials (seed admin, bootstrap admin, real staff logins)**

- Minimum 14 characters; enforce a passphrase or password-manager-generated value.
- Enforce uniqueness (not reused from any other system).
- **Require MFA** for all admin-console logins before exposing the panel publicly.
- Change immediately on first login; rotate on staff turnover.
- Never transmit in plaintext (chat, email, tickets) — share via the password manager.

**Storage & handling (all secrets)**

- At rest: only in the vault (encrypted) or as hashes in the DB (BCrypt is already used
  for user passwords — never store a reversible admin password anywhere).
- In transit: TLS everywhere; secrets injected as env vars, not command-line args
  (args leak via `ps`/process listings).
- In logs: never log secret values. Verify log scrubbing before go-live.

---

## 5. How the secrets are utilised in the system

A map of each secret to the component that consumes it, so operators know the blast radius
of a rotation.

| Secret | Consumed by | Effect of rotation |
|---|---|---|
| `JWT_SECRET` | `JwtTokenProvider` signs/validates every access & refresh token | All existing tokens become invalid → users must re-login (unless overlap is implemented). Rotate in a maintenance window. |
| `DB_PASSWORD` | `spring.r2dbc` (runtime queries) **and** `spring.flyway` (migrations) | Update in vault + restart; both R2DBC pool and Flyway read the same var. |
| `STRIPE_SECRET_KEY` | `StripeService` (Checkout Session create, refunds) | New key takes effect on restart; ensure the key matches the Stripe account/mode. |
| `STRIPE_WEBHOOK_SECRET` | `StripeWebhookController` signature verification | Must match the endpoint's signing secret in the Stripe dashboard, or webhooks 400. |
| `SSLCOMMERZ_*` / `PAYPAY_*` / `OMISE_*` / `LINEPAY_*` | Region payment gateways in `service/payment/*` | Region-scoped; a bad key only breaks that region's checkout. |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | `spring.mail` → account-verification & notification email | Wrong creds → verification emails silently fail (send is best-effort). |
| `ADMIN_BOOTSTRAP_*` | `AdminBootstrap` (first-boot admin seed) | Only read when the admin email does not yet exist. |
| `OLLAMA_URL` | KYC face-match + semantic search embeddings | If unreachable, KYC routes to manual review and search degrades — non-fatal. |

**Token specifics (post token-hardening):**

- Access tokens are short-lived and carry an `aud` (audience) claim of `storefront` or
  `admin`; the admin API only accepts `aud=admin` tokens.
- Refresh tokens (longer-lived) mint new access tokens via `/api/auth/refresh` and
  `/api/admin/auth/refresh`.
- Auth is stateless (signature + expiry only), so a **leaked token cannot be individually
  revoked.** The mitigations are:
  - the short access TTL,
  - rotating `JWT_SECRET`, and
  - disabling the account (`users.is_active = false`, enforced on every request).

> **Tip:** A shared-store `jti` denylist for true single-token revocation is a recommended
> future enhancement.

---

## 6. Go-live secrets checklist

- [ ] Vault provisioned, one namespace per environment, audit logging on.
- [ ] `JWT_SECRET` generated (≥ 64 bytes), stored, injected; placeholder no longer resolvable.
- [ ] `DB_PASSWORD` rotated off `postgres`.
- [ ] All payment-gateway keys set to real values for the enabled regions.
- [ ] `ADMIN_BOOTSTRAP_PASSWORD` set strong on first boot.
- [ ] Seed admin (`admin@ecommerce.com`) disabled or rotated post-migration.
- [ ] MFA enforced on the admin console; admin panel not publicly reachable before this.
- [ ] Log scrubbing verified — no secret appears in application or access logs.
- [ ] Rotation runbook + cadence documented and owned by a named team.
- [ ] (Recommended) Fail-fast startup guard rejecting placeholder secrets in non-dev profiles.
