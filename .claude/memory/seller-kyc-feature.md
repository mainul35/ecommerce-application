---
name: seller-kyc-feature
description: "Seller self-registration with e-KYC implemented 2026-06-08 — policies, engine decisions, privacy contract, status (UNCOMMITTED), setup steps for OCR/vision"
metadata: 
  node_type: memory
  type: project
  originSessionId: 0fc4d7f4-3030-4208-a38a-0d56bb2a26b7
---

Seller e-KYC implemented 2026-06-08 (V17, backend + frontend + 2 integration tests). **Status: COMMITTED 2026-06-09 as 63ecdf0 `feat(kyc)`** (pushed). Builds green, all 3 backend tests pass.

## Policies (user decisions, 2026-06-08)

- **Anyone can sell**: no role change — sellers are users with `id_verified=true` + a `seller_profiles` row (`seller_type`: BUSINESS / INDIVIDUAL for used-item sellers). Storefront login was widened to accept VENDOR (was CUSTOMER-only).
- **Engine**: local Tesseract OCR (tess4j) + Ollama vision model (`llava`) for ADVISORY face match. Both pluggable via `OcrProvider` / `FaceMatchProvider` strategy interfaces (PaymentGateway pattern) for future OpenCV/cloud swap.
- **Auto-approve** only when EVERY signal is green: idDocumentOk + billDocumentOk + nameMatch ≥0.80 + addressMatch ≥0.70 + faceVerdict==MATCH. Anything uncertain → admin queue (`/admin/kyc`, IN_REVIEW oldest-first). Pipeline NEVER auto-rejects; missing engines = signals unknown → human review, never user-blame.
- **Privacy contract**: evidence (ID photos, selfies, bill + OCR extracts/face notes) purged on decision OR at 72h (`kyc.retention-hours`), whichever first; `KycPurgeJob` sweeps every 10 min and EXPIREs undecided cases. Durable trace = `users.id_verified(+_at)` boolean + non-PII signal scores + profile data. Files live in `kyc-private/` (NOT under public /uploads), served only via owner-or-staff party-checked streaming endpoint.
- **Selfies**: in-browser camera only (getUserMedia, front/left/right guided, 640x480 min) — no file upload fallback, deliberate anti-spoof policy. ID_BACK not required for PASSPORT.

## Engine setup (DONE on this machine, 2026-06-08/09)

- OCR: `eng.traineddata` + `ben.traineddata` live in `backend/src/main/resources/test_data/` (~25MB, **gitignored** — user placed them; not committed). Wired in machine-local `backend/config/application.yml`: `kyc.ocr.tessdata-path` = absolute path to that dir, `languages: eng+ben`. Verified — boot logs "KYC OCR ready". Packaging caveat: classpath resources aren't a real dir inside a JAR; for prod, extract on startup or mount external `KYC_TESSDATA_PATH`.
- Vision: `llava:latest` is pulled in Ollama (shared 11434 server). faceVerdict works; unavailable → UNKNOWN → human review.
- Test image fixtures (id/selfies/bill, with matching name+address text) are in `backend/src/test/resources/kyc/` and loaded by KycLifecycleIntegrationTest via classpath.

## Follow-ups

- Commit (with escrow-style split). Note: getUserMedia needs HTTPS in production (localhost is fine for dev).
- Gate product listing / escrow payout on `id_verified` (currently informational only — vendors are still admin-assigned via products.vendor_id; a seller portal is future work).
- Consider hashing the ID number to block duplicate registrations (currently not extracted/stored at all).
- Threshold tuning once real documents flow; vision model is advisory — upgrade to face embeddings (Option B) if volume grows.

Related: [[marketplace-escrow-feature]] (sellers receive escrow payouts), [[dev-environment-toolchain]].
