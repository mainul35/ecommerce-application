# Memory Index

- [Account verification feature](account-verification-feature.md) — mandatory email (real/Mailpit) + phone (dummy OTP) verification, V18, gates checkout/orders/KYC; COMMITTED+pushed 2026-06-09 as 6d64d83
- [Seller KYC feature](seller-kyc-feature.md) — e-KYC self-registration implemented 2026-06-08; Tesseract+Ollama engines, auto-approve policy, 72h evidence purge contract, COMMITTED+pushed 2026-06-09 as 63ecdf0, engine setup steps
- [Marketplace escrow feature](marketplace-escrow-feature.md) — escrow/disputes/wallet/returns implemented 2026-06-07 (committed 5b3ec3a); business policies, numeric-enum convention, follow-up list
- [Dev environment toolchain](dev-environment-toolchain.md) — no system JDK/Maven (use IntelliJ bundled JBR+Maven); karimen project owns 5432+8080, ecommerce runs DB 5433 / HTTP 8081 via gitignored config/ overrides + frontend/.env.local
- [Security hardening](security-hardening.md) — 2026-07-04 pass: dispute-evidence IDOR fix, JWT audience+refresh, bootstrap-admin guard, orderItemId validation, admin dashboard split to its own app on :5174, post-deploy secrets doc; builds green, runtime E2E pending, UNCOMMITTED
- [Infrastructure plan](infrastructure-plan.md) — self-hosted 3-phase rollout (single host → Proxmox lab → Cloudflare Tunnel); VM1=apps+vault, VM2=infra (Harbor/Jenkins/vault), VM3=AI/GPU; full doc docs/INFRASTRUCTURE.md; planning stage
