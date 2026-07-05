---
name: infrastructure-plan
description: "Self-hosted infra plan (2026-07-04): 3-phase rollout (single host → Proxmox → Cloudflare Tunnel); VM1=apps+app-vault, VM2=infra (Harbor+Jenkins+infra-vault). REVISED: no local GPU (VM3 dropped) → hosted inference incl. Ollama Cloud for KYC; starting on WSL/Hyper-V Docker Compose with portability to Proxmox/AWS; Cloudflare domains in hand. Docs: INFRASTRUCTURE.md + DEPLOYMENT_PLAYBOOK.md."
metadata:
  node_type: memory
  type: project
---

User's self-hosted deployment plan for the e-commerce ecosystem, captured 2026-07-04. Full documentation lives in **docs/INFRASTRUCTURE.md** — this note is the quick map + decisions.

## Topology (user's design)
- **VM1 = Applications** (+ app secret vault): Traefik ingress, storefront (nginx SPA), admin (nginx SPA, separate origin), backend (Spring Boot), Postgres/pgvector, App Vault, MinIO. **Only the frontends + API are exposed** (via Traefik); everything else is compose-internal.
- **VM2 = Infra/CI-CD** (+ infra secret vault): Harbor (registry+Trivy+Cosign), Jenkins, Infra Vault, optional observability (Prom/Grafana/Loki) + Proxmox Backup Server.
- **VM3 = AI (optional/dedicated)**: Ollama + GPU passthrough for KYC llava vision; backend reaches it via internal OLLAMA_URL. Never exposed.
- Codebase stays on user's GitHub. Jenkins (VM2) builds → pushes to Harbor → deploys to VM1 (SSH+Compose now, Helm+k3s later). Deploy creds + Harbor robot token in Infra Vault.

## Three isolation phases
1. **P1** — Proxmox (or Hyper-V) VMs on current machine; LAN access; internal-CA / DNS-01 TLS.
2. **P2** — dedicated Proxmox box on home router; laptop access over LAN.
3. **P3** — Cloudflare Tunnel (cloudflared, outbound-only, no router port-forward) + Cloudflare Access on admin; global access.

Build P1 to be P3-ready: real domain + hostnames (shop./admin./api.), TLS, no localhost reliance. Recommended hypervisor = **Proxmox from the start** (Phase-2 target anyway; avoids Hyper-V→Proxmox migration).

## Key recommendations given
- **Orchestration:** Docker Compose now; migrate VM1 to **k3s** when HA/autoscaling needed (images unchanged).
- **Two vaults** per user's ask (app on VM1, infra on VM2); simpler fallback = one Vault + two KV mounts. Transit auto-unseal later. OpenBao if fully-OSS licensing wanted. Backend consumes via Spring Cloud Vault + AppRole; Vault keys named as Spring property names → overrides application.yml with zero code change (see [[security-hardening]] + POST_DEPLOYMENT_SECRETS.md).
- **AI cost (REVISED 2026-07-04):** user can't afford a local GPU now, so **VM3 dropped**. Use **hosted inference**: KYC face-match via **Ollama Cloud** (set `OLLAMA_URL` to the cloud endpoint + `OLLAMA_API_KEY`; `OllamaVisionFaceMatchProvider` needs a small change to send `Authorization: Bearer` — local Ollama needs none). Embeddings via same or cheaper hosted API (DeepInfra/Together). **PII caveat:** KYC selfies/IDs go to a 3rd party — fine for dev/low volume, but get a DPA + confirm residency/retention before regulated traffic; missing engine → auto manual-review (existing behaviour). Local GPU box deferred until volume/compliance justifies it.
- **Scaling order (higher traffic):** (1) **MinIO shared object storage FIRST** — backend currently writes uploads/KYC/dispute evidence to local disk, which breaks with >1 replica; (2) horizontal backend replicas + HPA (reactive/stateless-friendly); (3) Postgres = real bottleneck → PgBouncer → read replica → Patroni HA on its own host; (4) **Redis** cache + JWT `jti` revocation denylist (the missing piece from stateless auth); (5) Cloudflare CDN; (6) RabbitMQ/Kafka async workers (email/KYC/escrow); (7) Qdrant/Milvus if vector search outgrows pgvector; (8) observability; (9) multi-node Proxmox+k3s HA.

## Security ties
Admin = own hostname behind Cloudflare Access (P3), layered on the app's admin-audience JWT + /api/admin/** CORS lock. Private evidence → MinIO signed URLs. CORS_ORIGINS / CORS_ADMIN_ORIGINS / VITE_ADMIN_URL set per phase to preserve admin-origin isolation off localhost.

## 2026-07-04 revisions (current direction)
- **Start now on WSL2 (or a Hyper-V Ubuntu VM) with Docker Compose** — Proxmox temporarily unavailable. **Portability is a first-class constraint**: everything containerized, all config in one `.env`, state in named volumes, no `localhost`/hardcoded hosts → lift-and-shift to Proxmox or AWS by copying `deploy/` + `.env` + volumes (or swapping endpoints: MinIO→S3, Postgres→RDS/Aurora, `.env`→Secrets Manager).
- **Cloudflare domains already owned** → TLS via Traefik + Cloudflare **DNS-01** wildcard (no inbound port needed); public exposure via **Cloudflare Tunnel + Access** (P3).
- **Follow-it-yourself runbooks written:** `docs/DEPLOYMENT_PLAYBOOK.md` (P1 Docker Compose stack: Traefik + backend + Postgres/pgvector + MinIO + both SPAs, with Dockerfiles, compose, `.env`, verification, portability matrix) and `docs/NETWORK_PLAYBOOK.md` (end-to-end networking: Docker net segmentation edge/data, inter-VM, host/hypervisor, **Cloudflare Tunnel** outbound-only (no router ports), DNS/hostname matrix, TLS hops, **Cloudflare Access** for admin + **WARP private routes** for infra Vault/Harbor/Jenkins, webhook bypass exceptions, WAF, per-phase mapping).
- **MinIO/S3: IMPLEMENTED 2026-07-04** (compiles; S3 path pending live MinIO test). New `StorageService` abstraction (`service/storage/`): `LocalStorageService` (default, behaviour-preserving) + `S3StorageService` (aws-sdk v2 `S3AsyncClient`, `@ConditionalOnProperty storage.provider=s3`). **ProductMediaService** (PUBLIC area, `publicUrl` → `/uploads/..` local or absolute S3 URL) and **DisputeMediaService** (PRIVATE area, streamed via party-checked endpoint) migrated. Config `storage.*` (provider/local.private-dir/s3.*), env `STORAGE_PROVIDER`, `S3_*`. Frontend `mediaUrl()` passes absolute (S3) URLs through. **KYC evidence still on local FS** — deferred: OCR (Tesseract) + vision (`Files.readAllBytes(Path)`) need local file paths, so S3 needs a download-to-temp materialize layer (documented follow-up). aws-sdk `s3`+`netty-nio-client` 2.28.16 added to pom.
- **Ollama Cloud auth: IMPLEMENTED 2026-07-04** — `ollama.api-key` (`OLLAMA_API_KEY`) → `WebClientConfig` adds `Authorization: Bearer` to `ollamaWebClient` when set (blank = local, no auth). Point `OLLAMA_URL` at Ollama Cloud for KYC vision + embeddings without a local GPU.
- **Postgres**: don't replace — scale ladder PgBouncer → read replica → tune/partition → Aurora/Patroni HA → (last resort) Citus/Cockroach. Pair with Redis (cache + JWT `jti` denylist).

## Open decisions
one-vault-with-mounts vs two vaults; Compose vs k3s at launch; observability now vs later; which hosted embeddings provider. Domain: resolved (Cloudflare). Not yet built — planning/doc stage; MinIO + Ollama-Cloud-auth are the first code changes queued.

Related: [[security-hardening]] (admin isolation, secrets, MinIO for the evidence files), [[dev-environment-toolchain]] (current ports 5173/5174/8081, DB 5433), [[marketplace-escrow-feature]] / [[seller-kyc-feature]] (evidence storage → MinIO).
