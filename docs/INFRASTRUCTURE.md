# Self-Hosted Infrastructure Plan

Local-first, self-hosted deployment of the e-commerce ecosystem — the source of truth
for topology, networking, TLS, CI/CD, secrets, AI, and scaling. It evolves in three
isolation phases toward a Proxmox home lab exposed globally via Cloudflare Tunnel, and
is intentionally incremental: start at Phase 1, grow as needed.

> **Note:** This plan ties into the app's security model — the admin dashboard is a
> separate app/origin (see [POST_DEPLOYMENT_SECRETS.md](POST_DEPLOYMENT_SECRETS.md) and
> CLAUDE.md). It maps that isolation onto real hosts, TLS, and network boundaries.

> **Note: Update 2026-07-04 — current direction.**
> - Proxmox is temporarily unavailable, so **Phase 1 starts on WSL2 / Hyper-V with
>   Docker Compose**, built portability-first to lift-and-shift to Proxmox/AWS later —
>   see the step-by-step [DEPLOYMENT_PLAYBOOK.md](DEPLOYMENT_PLAYBOOK.md).
> - **VM3 (local GPU) is dropped for now**: with no affordable local GPU, KYC vision
>   uses **hosted inference (Ollama Cloud)** and embeddings use a cheap hosted API.
>   §9's "keep KYC local" is deferred until volume/compliance justify a GPU box.
> - Cloudflare domains are already owned (TLS via DNS-01; public exposure via
>   Cloudflare Tunnel).

---

## 1. Goals & principles

- **Local-first.** Everything runs on hardware you own; no hard cloud dependency.
- **Blast-radius isolation.** Apps and infra tooling live on separate VMs with
  separate secret vaults, so a compromise of one doesn't hand over the other.
- **Reproducible.** Code on GitHub → CI builds immutable images → registry → deploy.
  No hand-edited servers.
- **TLS everywhere**, even on the LAN.
- **Grow without rework.** Docker Compose now; k3s/Kubernetes when traffic needs it.

---

## 2. Phased rollout

| Phase | Where it runs | Access | TLS | Exposure |
|---|---|---|---|---|
| **P1 — Single host** | Proxmox (or Hyper-V) VMs on your current machine | From the same machine / LAN | Internal CA or DNS-01 | LAN only |
| **P2 — Dedicated lab** | Separate Proxmox box on the home router | From your laptop over LAN | Same, plus proper local DNS | LAN only |
| **P3 — Public** | Same Proxmox box | From anywhere | Cloudflare edge certs | Cloudflare Tunnel + Access |

Nothing about the app or containers changes between phases — only the hypervisor
location, DNS, and the ingress edge change. Build P1 to be P3-ready (real hostnames,
TLS, no reliance on `localhost`).

**Hypervisor choice:** use **Proxmox VE** from the start.

- It's free, Linux-native, and does snapshots/backup/clustering.
- It's exactly your Phase-2 target, so standardizing now avoids a Hyper-V → Proxmox
  migration later.
- Hyper-V works for P1 if that's what you have today, but treat it as throwaway.

---

## 3. VM topology

```
                        home router / LAN  (P2+)                Cloudflare edge (P3)
                               │                                       │
                     ┌─────────┴─────────┐                    (cloudflared tunnels)
                     │  reverse proxy /  │                             │
                     │  cloudflared      │◄────────────────────────────┘
                     └───┬───────────┬───┘
        ┌────────────────┘           └─────────────────┐
        ▼                                               ▼
┌───────────────────────────┐            ┌───────────────────────────┐
│ VM1 — APPLICATIONS         │            │ VM2 — INFRA / CI-CD        │
│  Traefik (TLS, ingress)    │            │  Harbor (registry+scan+sign)│
│  storefront (nginx SPA)    │            │  Jenkins (pipelines)        │
│  admin (nginx SPA)         │            │  Infra Vault (infra secrets)│
│  backend (Spring Boot)     │  deploy    │  Observability (Prom/Graf/  │
│  Postgres (pgvector)       │◄───────────│    Loki) [optional]         │
│  App Vault (app secrets)   │  SSH/Helm  │  Backup (PBS) [optional]    │
│  MinIO (private uploads)   │            └───────────────────────────┘
└───────────┬───────────────┘
            │ OLLAMA_URL (internal)
            ▼
┌───────────────────────────┐
│ VM3 — AI (optional/ded.)   │
│  Ollama + GPU passthrough  │
│  (llava vision, embeddings)│
└───────────────────────────┘
```

### VM1 — Applications (`app` VM)
Runs the customer-facing ecosystem and is **the only VM whose services are exposed**:
- **Traefik** (or Caddy) — TLS termination + reverse proxy. Routes by hostname:
  `shop.example.com` → storefront, `admin.example.com` → admin app,
  `api.example.com` → backend.
- **storefront** — built Vite SPA served by nginx.
- **admin** — the separate admin SPA (its own image + hostname; never bundled with storefront).
- **backend** — Spring Boot; only reachable through Traefik at `api.example.com`.
- **Postgres (pgvector)** — internal only, never exposed.
- **App Vault** — app secrets (JWT key, DB password, Stripe/gateway keys, mail creds).
- **MinIO** — S3-compatible object store for uploads and **private** KYC/dispute
  evidence (replaces local-disk storage; mandatory once the backend scales >1 replica).

Only `shop`, `admin`, and `api` (via Traefik) leave the VM. Everything else is
compose-network-internal.

### VM2 — Infra / CI-CD (`infra` VM)
Tooling that builds and ships to VM1, plus its own secrets:
- **Harbor** — private container registry with Trivy vulnerability scanning and
  Cosign image signing. Holds the app images and a robot pull-account for VM1.
- **Jenkins** — pipelines. Builds on push, pushes to Harbor, deploys to VM1.
- **Infra Vault** — infra secrets (Harbor admin, Jenkins creds, registry robot token,
  SSH deploy key / kubeconfig, ACME/DNS API token, Cloudflare tunnel token).
- **Observability (optional, add when useful):** Prometheus + Grafana + Loki + Tempo.
- **Proxmox Backup Server (optional):** VM-level backups.

### VM3 — AI (optional, dedicated)
Ollama with GPU passthrough for KYC face-match (llava) and search embeddings.
See §9. Backend reaches it via internal `OLLAMA_URL`; never exposed publicly.

---

## 4. Secrets topology — two vaults

App-vs-infra separation is honored with **two Vault instances**:

| Vault | Host | Holds | Consumed by |
|---|---|---|---|
| **App Vault** | VM1 | `jwt.secret`, DB password, Stripe/SSLCommerz/PayPay/Omise/LINE Pay keys, mail creds, MinIO keys | the backend (Spring Cloud Vault, AppRole) |
| **Infra Vault** | VM2 | Harbor admin + robot token, Jenkins creds, SSH deploy key, kubeconfig, Cloudflare tunnel token, ACME DNS token | Jenkins, Harbor, cloudflared, Traefik |

- Backend auth to App Vault via **AppRole** (role-id in env, secret-id delivered by
  Vault Agent / response-wrapping). Vault KV keys are named as Spring property names
  so they override `application.yml` with **zero app-code change** (see
  [POST_DEPLOYMENT_SECRETS.md](POST_DEPLOYMENT_SECRETS.md) §3).
- **Simpler alternative** (if two vaults feel heavy early on): one Vault on VM2 with
  two KV mounts (`app/`, `infra/`) and separate policies. You lose "vault dies with
  its VM" isolation but cut unseal/ops in half. Start here if you want, split later.
- **Unsealing:** file storage + manual unseal at first; graduate to **Transit
  auto-unseal** (a small always-up Vault unseals the others) so reboots don't need you.
- **OpenBao** (MPL-licensed Vault fork) is a drop-in if you want fully-OSS licensing.

---

## 5. CI/CD pipeline (GitHub → Jenkins → Harbor → VM1)

```
GitHub push ─webhook→ Jenkins (VM2)
   ├─ build backend   : mvn -DskipTests package  → docker build → Harbor
   ├─ build storefront: npm ci && npm run build   → nginx image → Harbor
   ├─ build admin     : npm ci && npm run build:admin → nginx image → Harbor
   ├─ scan (Trivy in Harbor) + sign (Cosign)
   └─ deploy to VM1:
        Compose:  ssh app-vm 'docker compose pull && docker compose up -d'
        k3s:      helm upgrade --install ecommerce ./chart --set image.tag=$GIT_SHA
```

- **Image tags** = git SHA (immutable) + a moving `stable` tag. Never `latest` in prod.
- **Deploy credential** (SSH key or kubeconfig) lives in **Infra Vault**; Jenkins
  pulls it at deploy time. VM1 pulls images with a **Harbor robot account** (read-only).
- **Migrations:** Flyway runs on backend startup (already wired) — deploy = rolling
  restart. For k3s, gate the rollout on a readiness probe.
- **Frontend env at build time:** `VITE_API_BASE_URL`, `VITE_ADMIN_URL`,
  `CORS_ORIGINS`, `CORS_ADMIN_ORIGINS` are set per-environment in the pipeline so the
  admin-origin isolation holds in every phase.

---

## 6. Container orchestration — Compose now, k3s later

- **Now (P1/P2, single VM):** Docker Compose per VM. Simple, matches dev, easy to reason
  about. One `docker-compose.yml` on VM1 (app + Postgres + Vault + MinIO + Traefik),
  one on VM2 (Harbor + Jenkins + Vault + observability).
- **When you need HA / autoscaling:** migrate VM1 to **k3s** (lightweight Kubernetes,
  perfect for a home lab). Package the app as a Helm chart; Harbor is already your
  registry. k3s gives you rolling deploys, HPA, self-healing, and multi-node scale-out
  across several Proxmox VMs. This is the "clustered like Kubernetes" path you mentioned —
  the container images are identical; only the orchestrator changes.

---

## 7. Networking & DNS

- **Get a real domain** (even for local use) — it makes TLS trusted everywhere and is
  required for Cloudflare (P3). Use subdomains: `shop.`, `admin.`, `api.` (public);
  `harbor.`, `jenkins.`, `vault.` (infra, LAN-only).
- **P1:** VMs on a host-only/NAT bridge; resolve hostnames via a hosts file or a small
  local DNS (dnsmasq/AdGuard). Keep karimen's ports clear (host 5432/8080 taken →
  Postgres 5433, backend behind Traefik on 443).
- **P2:** dedicated Proxmox box on the router; put the lab on its own subnet/VLAN;
  run local DNS (AdGuard/Pi-hole or Proxmox) so `*.example.com` resolves on the LAN.
- **P3:** no inbound ports on the router at all — `cloudflared` makes **outbound**
  tunnels to Cloudflare; public DNS is Cloudflare-managed.

---

## 8. TLS strategy

| Phase | Cert source | Notes |
|---|---|---|
| P1/P2 | **Let's Encrypt DNS-01** (via Traefik + your DNS provider's API token) OR **Vault PKI / step-ca** internal CA | DNS-01 gives *trusted* wildcard certs with no public inbound. Internal CA works too but you must trust its root on each device. |
| P3 | **Cloudflare edge certs** | Cloudflare terminates TLS at its edge; origin uses a Cloudflare Origin cert or the tunnel's mTLS. |

Terminate TLS at **Traefik** on each VM. Admin (`admin.example.com`) additionally sits
behind **Cloudflare Access** in P3 (email OTP / SSO) — defense in depth over the
app's own admin-audience token + CORS isolation.

---

## 9. AI / inference architecture

Two AI workloads today: **KYC face-match** (llava vision — PII: selfies + ID photos)
and **search embeddings** (product text — not PII). Treat them differently:

- **KYC vision → keep LOCAL.** These images are sensitive identity documents; sending
  them to a third-party API creates privacy/compliance exposure. Run this on **VM3**,
  a dedicated box with a GPU (Proxmox GPU passthrough). Good value GPUs: RTX 4060 Ti
  16GB or a used 3090 24GB; or a Mac mini (Apple Silicon runs Ollama efficiently).
  Also: llava is *advisory* — for real volume, move to a dedicated **face-embedding**
  model (cosine similarity) rather than an LLM (already noted as a KYC follow-up).
- **Embeddings → cheap to offload.** Product-search embeddings carry no PII, so a
  hosted open-model inference API is fine and far cheaper than owning a GPU at low
  volume: **DeepInfra, Together AI, Fireworks, Groq, OpenRouter, Replicate** all serve
  open embedding models (BGE, nomic, e5) pay-per-token. Point `OLLAMA_*`/embedding
  config at one of these, or keep them on VM3 too if you want zero external calls.

> **Tip:** A dedicated GPU box is worth it only if (a) KYC/PII volume is real and must
> stay local, or (b) you're inferencing constantly. For a low-traffic launch, **keep
> KYC vision on your existing local Ollama and offload embeddings to a cheap hosted
> API** — buy the GPU box when KYC throughput justifies it.

> **⚠️ Warning:** Keep the AI tier behind the LAN; never expose Ollama publicly.

---

## 10. Scaling roadmap for higher traffic

In rough priority order as load grows:

1. **Shared object storage first (MinIO).** The backend currently writes uploads,
   KYC, and dispute evidence to **local disk** — that breaks the moment you run more
   than one backend instance. Move to MinIO (S3) and serve private evidence via signed
   URLs. *Do this before horizontal scaling.*
2. **Stateless backend + horizontal scale.** The app is reactive (WebFlux) and JWT-
   stateless — run N replicas behind Traefik/k3s and add **HPA** (CPU/RPS-based).
3. **Postgres is the bottleneck, not the app.** Add **PgBouncer** pooling, then a
   **read replica** for catalog/search reads, then HA (Patroni) — and give the DB its
   own VM/host with fast NVMe.
4. **Redis cache layer.** Cache hot catalog/search results, rate-limit, and host the
   **JWT `jti` revocation denylist** (the piece missing from stateless auth today).
5. **CDN + edge cache.** Cloudflare (from P3) caches the SPA bundles and static media
   globally; offloads the origin.
6. **Async workers + queue.** Move email sending, KYC pipeline, and escrow jobs onto
   **RabbitMQ/Kafka** workers so request latency is decoupled from slow work.
7. **Vector scale.** pgvector is fine to moderate scale; if semantic search grows, a
   dedicated **Qdrant/Milvus** node.
8. **Observability before you need it.** Prometheus + Grafana + Loki + Tempo (traces)
   on VM2 so you can *see* the bottleneck instead of guessing.
9. **Multi-node HA.** Proxmox cluster + k3s HA control plane + DB replication once
   uptime matters.

---

## 11. Backups & DR

- **Postgres:** nightly `pg_dump` + continuous WAL archiving to MinIO; test restores.
- **Vault:** `vault operator raft snapshot` (or file-storage snapshot); store the
  unseal keys offline (not on the same box).
- **Harbor:** back up its database + object store.
- **VM-level:** Proxmox Backup Server snapshots of all VMs.
- **Config as code:** compose files, Helm charts, Traefik/Jenkins config in git.

---

## 12. Security notes (ties to the app hardening)

- Admin gets its **own hostname** (`admin.example.com`), behind **Cloudflare Access** in
  P3 — layered over the app's admin-audience JWT + `/api/admin/**` CORS lock.
- Private evidence (KYC, disputes) → **MinIO with signed URLs**, never public buckets.
- `CORS_ORIGINS` / `CORS_ADMIN_ORIGINS` / `VITE_ADMIN_URL` set per phase so origin
  isolation survives the move from `localhost` to real hostnames.
- Secrets only from Vault; no secrets in images, compose files, or Jenkins logs.
- Cloudflare WAF + rate limiting in front of everything public (P3).

---

## 13. Open decisions (to firm up as we go)

- Domain name + DNS provider (needed for DNS-01 TLS and Cloudflare).
- One Vault-with-mounts vs two Vault instances (start simple, split later).
- Compose vs k3s for VM1 at launch (recommend Compose; k3s when scaling).
- Dedicated GPU box vs hosted embeddings (recommend hybrid: local KYC, hosted embeddings).
- Observability stack now vs later (recommend: minimal Prometheus/Grafana early).

---

## 14. Suggested build order

1. **P1 skeleton:** VM1 (Traefik + backend + Postgres + Vault + MinIO + both SPAs) and
   VM2 (Harbor + Jenkins + Vault) as Compose stacks; internal-CA TLS; deploy by hand.
2. **Wire CI/CD:** GitHub webhook → Jenkins → Harbor → SSH deploy to VM1.
3. **Real TLS + local DNS:** Traefik DNS-01 wildcard; AdGuard/dnsmasq for `*.example.com`.
4. **P2:** move VMs to dedicated Proxmox hardware on the router.
5. **P3:** cloudflared tunnels + Cloudflare Access on admin.
6. **Scale as needed** per §10, starting with MinIO (already required for >1 backend).
