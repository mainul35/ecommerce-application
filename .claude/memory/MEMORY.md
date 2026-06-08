# Memory Index

- [Account verification feature](account-verification-feature.md) — mandatory email (real/Mailpit) + phone (dummy OTP) verification, V18, gates checkout/orders/KYC; UNCOMMITTED 2026-06-09
- [Seller KYC feature](seller-kyc-feature.md) — e-KYC self-registration implemented 2026-06-08; Tesseract+Ollama engines, auto-approve policy, 72h evidence purge contract, UNCOMMITTED, engine setup steps
- [Marketplace escrow feature](marketplace-escrow-feature.md) — escrow/disputes/wallet/returns implemented 2026-06-07 (committed 5b3ec3a); business policies, numeric-enum convention, follow-up list
- [Dev environment toolchain](dev-environment-toolchain.md) — no system JDK/Maven (use IntelliJ bundled JBR+Maven); karimen project owns 5432+8080, ecommerce runs DB 5433 / HTTP 8081 via gitignored config/ overrides + frontend/.env.local
