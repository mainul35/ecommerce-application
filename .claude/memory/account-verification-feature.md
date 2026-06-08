---
name: account-verification-feature
description: "Mandatory email + phone account verification implemented 2026-06-09 (V18) — email real via Mailpit, phone dummy OTP, gates checkout/orders/KYC. UNCOMMITTED."
metadata: 
  node_type: memory
  type: project
  originSessionId: 0fc4d7f4-3030-4208-a38a-0d56bb2a26b7
---

Mandatory account verification implemented 2026-06-09 (V18, backend + frontend). **Status: COMMITTED 2026-06-09 as 6d64d83 `feat(verification)`** (pushed). Backend compiles, all 3 integration tests green, frontend builds, e2e flow verified through Mailpit.

## Policies (user decisions)

- **Email = real**, **phone = dummy OTP** for now (real bulk SMS needs a paid subscription). Both **mandatory**.
- Enforcement = **gate sensitive actions**: registration still logs you in immediately, but `OrderService.create` (self-checkout) and `KycService.openCase` (start seller KYC) throw `VerificationRequiredException` (→ HTTP 403) until `emailVerified && phoneVerified`. Admin create-on-behalf is NOT gated. Browsing/cart are free.
- **Phone collected on the verify page** (`/verify`), not on the signup form. Signup form unchanged.

## How it works

- Register → `UserService.register` auto-sends the email link (best-effort; mail failure never fails signup).
- Email: `verification_tokens` (channel EMAIL, opaque token), link `FRONTEND_URL/verify-email?token=...`. `POST /api/verification/email/verify` is PUBLIC (token authenticates). Resend = authed.
- Phone: `POST /api/verification/phone/send {phone}` saves phone + issues OTP; **dummy** = OTP logged (`[DUMMY SMS]`) and fixed code `000000` always accepted (`verification.phone-dummy=true`, `dummy-phone-otp=000000`). `phone/verify {code}`.
- `verification_tokens.channel` is a numeric enum (EMAIL=0, PHONE=1) per the system convention. `users.phone` + `users.phone_verified` added in V18 (email_verified existed since V1).
- Frontend: `UnverifiedBanner` in Layout, `VerifyAccountPage` (/verify), `VerifyEmailPage` (/verify-email), checkout shows a verify prompt; after verifying, `fetchCurrentUser` refreshes flags. UserDto/User type now expose emailVerified/phone/phoneVerified/idVerified.

## Infra

- **Mailpit** dev mail server: added to docker-compose AND run standalone (`ecommerce-mailpit`, SMTP 1025, web/API 8025). spring.mail defaults to localhost:1025 no-auth. Read sent mail at http://localhost:8025 (REST: /api/v1/search?query=to:..., /api/v1/message/{id}).
- Config: `verification.*` (TTLs, phone-dummy, dummy-otp) + `spring.mail.*` in application.yml.

## To go live on phone

Set `verification.phone-dummy=false` and implement real SMS send in `VerificationService.sendPhoneOtp` (swap the `[DUMMY SMS]` log for a provider call). Everything else already works.

Related: [[seller-kyc-feature]] (KYC openCase is gated on this), [[marketplace-escrow-feature]] (checkout gated), [[dev-environment-toolchain]].
