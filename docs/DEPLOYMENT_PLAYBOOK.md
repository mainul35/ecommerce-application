# P1 Deployment Playbook — Docker Compose on WSL2 / Hyper-V

A follow-it-yourself runbook to stand up the whole ecosystem as containers on your
current machine (WSL2 or a Hyper-V Linux VM), with **real Cloudflare TLS** and a
layout that lifts-and-shifts unchanged to Proxmox or AWS later.

Companion docs: big-picture [INFRASTRUCTURE.md](INFRASTRUCTURE.md), secrets
[POST_DEPLOYMENT_SECRETS.md](POST_DEPLOYMENT_SECRETS.md).

**At the end you will have:** `https://shop.<domain>`, `https://admin.<domain>`,
`https://api.<domain>` served by Traefik with trusted certs, backed by Postgres +
MinIO, all defined in one portable `deploy/` folder.

---

## 0. The portability rules (obey these and moving hosts is trivial)

1. **Everything is a container** defined in one `docker-compose.yml`. No software
   installed on the host except Docker.
2. **All config comes from a single `.env` file** — hostnames, ports, secrets,
   image tags. No value is hardcoded in compose or images. Moving = new `.env`.
3. **State lives in named volumes** (Postgres data, MinIO data, Traefik certs), never
   in host bind-mounts to Windows paths. Named volumes are portable and backup-able.
4. **No `localhost` anywhere in the app** — always the real hostname
   (`api.<domain>`), so the same images work on WSL, Proxmox, and AWS.
5. **Images are built once and referenced by tag.** Locally you can `build:`; when you
   add Harbor/GHCR you just change the `image:` reference.

Keep the whole `deploy/` folder in git. That folder *is* your infrastructure.

---

## 1. Prerequisites

- **Docker** in WSL2 (Docker Desktop with WSL2 backend, or Docker Engine installed
  inside an Ubuntu WSL distro). Verify: `docker version && docker compose version`.
- **A Cloudflare-managed domain** (you have this). You'll use DNS-01 for certs.
- **A Cloudflare API token** scoped to *Zone → DNS → Edit* for your zone
  (Cloudflare dashboard → My Profile → API Tokens → Create → "Edit zone DNS"). This
  lets Traefik prove domain ownership **without opening any inbound port**.
- Pick your hostnames (used everywhere below):
  - `shop.<domain>` → storefront
  - `admin.<domain>` → admin console
  - `api.<domain>` → backend API

> **WSL2 networking note.** WSL2 forwards `localhost` to Windows, so from the same PC
> you reach the stack fine. Reaching it from *other* LAN devices via WSL2 is awkward
> (needs `netsh portproxy`). If you want LAN access now, run the stack in a **Hyper-V
> Ubuntu VM** (bridged network) instead — the compose files are identical. For "just
> me on this machine," WSL2 is fine.

---

## 2. Create the deploy folder

```
ecommerce-application/
  backend/            # existing
  frontend/           # existing
  deploy/             # NEW - all infra lives here
    docker-compose.yml
    .env              # gitignored (real secrets)
    .env.example      # committed (documented template)
    traefik/          # cert storage volume mount target
    nginx/
      storefront.conf
      admin.conf
  backend/Dockerfile        # NEW
  frontend/Dockerfile       # NEW (multi-target: storefront + admin)
```

Add to `.gitignore`: `deploy/.env`, `deploy/traefik/acme.json`.

---

## 3. Backend image — `backend/Dockerfile`

Multi-stage: build the jar with Maven, run on a slim JRE. (No `mvnw` needed — the
build image provides Maven.)

```dockerfile
# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

> KYC OCR needs Tesseract traineddata (the gitignored ~25 MB files). For containers,
> mount them as a volume (see compose `backend.volumes`) and set `KYC_TESSDATA_PATH`
> to the mount path — don't bake PII-adjacent blobs into the image.

---

## 4. Frontend image — `frontend/Dockerfile` (one file, two targets)

Both SPAs build from the same source with different commands and nginx roots. Vite
reads `VITE_*` from the environment at **build time**, so the API/admin URLs are
passed as build args.

```dockerfile
# ---- shared build base ----
FROM node:22-alpine AS base
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL
ARG VITE_ADMIN_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_ADMIN_URL=$VITE_ADMIN_URL

# ---- storefront ----
FROM base AS storefront-build
RUN npm run build           # -> dist/

FROM nginx:alpine AS storefront
COPY --from=storefront-build /app/dist /usr/share/nginx/html
COPY deploy/nginx/storefront.conf /etc/nginx/conf.d/default.conf

# ---- admin ----
FROM base AS admin-build
RUN npm run build:admin     # -> dist-admin/

FROM nginx:alpine AS admin
COPY --from=admin-build /app/dist-admin /usr/share/nginx/html
COPY deploy/nginx/admin.conf /etc/nginx/conf.d/default.conf
```

`deploy/nginx/storefront.conf`:
```nginx
server {
  listen 80;
  root /usr/share/nginx/html;
  location / { try_files $uri $uri/ /index.html; }
}
```

`deploy/nginx/admin.conf` (admin entry is `admin.html`, not `index.html`):
```nginx
server {
  listen 80;
  root /usr/share/nginx/html;
  location / { try_files $uri $uri/ /admin.html; }
}
```

---

## 5. The `.env` — single source of config (`deploy/.env.example`)

```dotenv
# --- domain / hostnames ---
DOMAIN=example.com
ACME_EMAIL=you@example.com
CF_DNS_API_TOKEN=cloudflare-scoped-dns-edit-token

# --- frontend build args (baked into the SPA bundles) ---
VITE_API_BASE_URL=https://api.example.com/api
VITE_ADMIN_URL=https://admin.example.com/admin

# --- backend runtime ---
SERVER_PORT=8080
DB_HOST=postgres
DB_PORT=5432
DB_NAME=ecommerce
DB_USERNAME=postgres
DB_PASSWORD=CHANGE_ME_STRONG
JWT_SECRET=CHANGE_ME_64_BYTES_BASE64
JWT_EXPIRATION=3600000
CORS_ORIGINS=https://shop.example.com,https://admin.example.com
CORS_ADMIN_ORIGINS=https://admin.example.com
FRONTEND_URL=https://shop.example.com
BACKEND_URL=https://api.example.com
ADMIN_BOOTSTRAP_PASSWORD=CHANGE_ME_STRONG

# --- MinIO (S3-compatible object storage) ---
MINIO_ROOT_USER=ecom-minio
MINIO_ROOT_PASSWORD=CHANGE_ME_STRONG
S3_ENDPOINT=http://minio:9000
S3_BUCKET=ecommerce-media
S3_ACCESS_KEY=ecom-minio
S3_SECRET_KEY=CHANGE_ME_STRONG

# --- AI (KYC vision + embeddings) - hosted, no local GPU ---
OLLAMA_URL=https://ollama.com
OLLAMA_API_KEY=your-ollama-cloud-key
```

Copy to `.env` and fill real values. `openssl rand -base64 64` for `JWT_SECRET`.
**This file is the only thing that changes between WSL, Proxmox, and AWS.**

---

## 6. `deploy/docker-compose.yml`

```yaml
name: ecommerce

services:
  traefik:
    image: traefik:v3.1
    restart: unless-stopped
    command:
      - --providers.docker=true
      - --providers.docker.exposedbydefault=false
      - --entrypoints.web.address=:80
      - --entrypoints.websecure.address=:443
      # Redirect HTTP -> HTTPS
      - --entrypoints.web.http.redirections.entrypoint.to=websecure
      - --entrypoints.web.http.redirections.entrypoint.scheme=https
      # Cloudflare DNS-01 - no inbound port needed to prove ownership
      - --certificatesresolvers.cf.acme.dnschallenge=true
      - --certificatesresolvers.cf.acme.dnschallenge.provider=cloudflare
      - --certificatesresolvers.cf.acme.email=${ACME_EMAIL}
      - --certificatesresolvers.cf.acme.storage=/letsencrypt/acme.json
    environment:
      - CF_DNS_API_TOKEN=${CF_DNS_API_TOKEN}
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./traefik:/letsencrypt
      - /var/run/docker.sock:/var/run/docker.sock:ro   # see note below

  postgres:
    image: pgvector/pgvector:pg16
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME} -d ${DB_NAME}"]
      interval: 10s
      timeout: 5s
      retries: 5

  minio:
    image: minio/minio
    restart: unless-stopped
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    volumes:
      - miniodata:/data
    # Console only exposed via Traefik if you want it; API is internal.

  backend:
    build:
      context: ../backend
    restart: unless-stopped
    env_file: .env
    depends_on:
      postgres:
        condition: service_healthy
    volumes:
      - ./tessdata:/tessdata:ro          # KYC OCR models
    environment:
      KYC_TESSDATA_PATH: /tessdata
    labels:
      - traefik.enable=true
      - traefik.http.routers.api.rule=Host(`api.${DOMAIN}`)
      - traefik.http.routers.api.entrypoints=websecure
      - traefik.http.routers.api.tls.certresolver=cf
      - traefik.http.services.api.loadbalancer.server.port=8080

  storefront:
    build:
      context: ..
      dockerfile: frontend/Dockerfile
      target: storefront
      args:
        VITE_API_BASE_URL: ${VITE_API_BASE_URL}
        VITE_ADMIN_URL: ${VITE_ADMIN_URL}
    restart: unless-stopped
    labels:
      - traefik.enable=true
      - traefik.http.routers.shop.rule=Host(`shop.${DOMAIN}`)
      - traefik.http.routers.shop.entrypoints=websecure
      - traefik.http.routers.shop.tls.certresolver=cf
      - traefik.http.services.shop.loadbalancer.server.port=80

  admin:
    build:
      context: ..
      dockerfile: frontend/Dockerfile
      target: admin
      args:
        VITE_API_BASE_URL: ${VITE_API_BASE_URL}
        VITE_ADMIN_URL: ${VITE_ADMIN_URL}
    restart: unless-stopped
    labels:
      - traefik.enable=true
      - traefik.http.routers.admin.rule=Host(`admin.${DOMAIN}`)
      - traefik.http.routers.admin.entrypoints=websecure
      - traefik.http.routers.admin.tls.certresolver=cf
      - traefik.http.services.admin.loadbalancer.server.port=80

volumes:
  pgdata:
  miniodata:
```

> **Docker-socket note:** mounting `docker.sock` into Traefik is the standard (and
> convenient) way it discovers services, but it's a privilege. For a hardened setup
> later, use Traefik's file provider or a socket proxy. Fine for P1.

---

## 7. Bring it up + verify

```bash
cd deploy
cp .env.example .env         # then edit .env with real values
mkdir -p tessdata traefik nginx
# copy eng.traineddata (+ ben.traineddata) into deploy/tessdata/
touch traefik/acme.json && chmod 600 traefik/acme.json

docker compose build
docker compose up -d
docker compose ps
docker compose logs -f backend    # watch Flyway migrate + "KYC OCR ready"
```

Point DNS at Traefik:
- **Same-PC access:** add to `C:\Windows\System32\drivers\etc\hosts`:
  `127.0.0.1 shop.example.com admin.example.com api.example.com`.
  DNS-01 gave you a *real* cert, so the browser trusts it even pointing at localhost.
- **LAN / public:** create Cloudflare DNS records for the three hostnames (see §9/§10).

Check: `https://api.example.com/swagger-ui.html`, `https://shop.example.com`,
`https://admin.example.com`. Admin login: `admin` / your `ADMIN_BOOTSTRAP_PASSWORD`.

---

## 8. MinIO wiring (shared object storage)

MinIO is up; two things remain:

**a) Create the bucket** (one-time):
```bash
docker compose exec minio sh -c \
  "mc alias set local http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD && \
   mc mb -p local/ecommerce-media"
```

**b) Point the backend at S3 — IMPLEMENTED, just configure it.**
The backend now has a `StorageService` abstraction (`service/storage/`) with a
`local` (default) and an `s3` implementation (AWS SDK v2 `S3AsyncClient`; MinIO speaks
S3). To use MinIO, set in `.env`:
```dotenv
STORAGE_PROVIDER=s3
S3_ENDPOINT=http://minio:9000
S3_BUCKET=ecommerce-media
S3_ACCESS_KEY=${MINIO_ROOT_USER}
S3_SECRET_KEY=${MINIO_ROOT_PASSWORD}
S3_PATH_STYLE=true
S3_PUBLIC_BASE_URL=https://media.<domain>   # public serving base for product media
```
- **Product media (public)** → stored in the bucket; the media `url` becomes
  `${S3_PUBLIC_BASE_URL}/products/…`. Make that prefix public-read (or front it with a
  CDN/Traefik route). The frontend `mediaUrl()` already passes absolute URLs through.
- **Dispute evidence (private)** → stored under `disputes/…` and streamed only through
  the existing party-checked endpoint — never public.
- **KYC evidence still uses local disk** (`kyc.storage-dir`): its OCR/vision engines
  read local file paths, so S3 needs a download-to-temp step — a documented follow-up.
  For multi-replica KYC, share that volume (NFS) until then.

> The S3 path is compile-verified but pending a live MinIO smoke test; `local` (default)
> is unchanged. This is what unblocks running >1 backend replica.

---

## 9. Access from your LAN (when on Hyper-V / Proxmox)

- Create Cloudflare **DNS A records** `shop`/`admin`/`api` → the VM's LAN IP, set to
  **DNS-only (grey cloud)** so they resolve to the private IP on your network. Certs
  still validate (DNS-01 + wildcard). Now any LAN device reaches the stack over HTTPS.
- Or run a small local resolver (AdGuard/dnsmasq) mapping `*.<domain>` to the VM IP.

---

## 10. Public access from anywhere — Cloudflare Tunnel (P3)

No router port-forwarding. Add a `cloudflared` container that dials **out** to
Cloudflare:

```yaml
  cloudflared:
    image: cloudflare/cloudflared:latest
    restart: unless-stopped
    command: tunnel run
    environment:
      - TUNNEL_TOKEN=${CF_TUNNEL_TOKEN}
```

Steps: Cloudflare Zero Trust → Networks → Tunnels → create tunnel → copy the token
into `.env` (`CF_TUNNEL_TOKEN`) → add public hostnames in the tunnel UI:
`shop.<domain>` → `http://traefik:80`, same for `admin`/`api`. Then **put
`admin.<domain>` behind Cloudflare Access** (email OTP / SSO) — layered over the app's
admin-audience JWT + `/api/admin/**` CORS lock. Turn on the WAF + rate limiting.

---

## 11. Secrets: `.env` now, Vault later (portable either way)

For P1, `.env` (gitignored, `chmod 600`) is acceptable. When the infra VM/Proxmox is
back, front it with Vault per [POST_DEPLOYMENT_SECRETS.md](POST_DEPLOYMENT_SECRETS.md)
§3 — because the app reads `${VAR:default}`, moving a value from `.env` to Vault is
transparent to the code. Keep the *names* identical and the migration is a no-op.

---

## 12. Moving to Proxmox / AWS — portability matrix

| Component | WSL/Hyper-V (P1) | Proxmox (P2) | AWS (cloud) |
|---|---|---|---|
| Orchestration | Docker Compose | Docker Compose or k3s | ECS Fargate / EKS |
| Reverse proxy/TLS | Traefik + DNS-01 | same | ALB + ACM, or Traefik |
| Postgres | `pgvector` container | container or dedicated VM | **RDS/Aurora Postgres** (+ pgvector) |
| Object storage | MinIO container | MinIO VM | **S3** (drop MinIO; same SDK) |
| Secrets | `.env` | Vault VM | Secrets Manager / Vault |
| Registry | local build / GHCR | Harbor VM | ECR |
| Images | identical | identical | identical |
| Move procedure | — | copy `deploy/` + `.env` + volume dumps | swap `.env` endpoints (RDS/S3), push to ECR |

Because MinIO and RDS both speak their standard protocols (S3, Postgres wire), moving
to AWS is mostly **changing endpoints in `.env`** — no code changes.

---

## 13. Postgres scaling ladder (when it becomes the bottleneck)

Do **not** switch databases — scale Postgres in this order:
1. **PgBouncer** in front (transaction pooling) — cheapest, biggest early win.
2. **Read replica** — route catalog/search reads to it; writes to primary.
3. **Tune** — indexes, `work_mem`, partition large tables (orders, escrow, wallet_txn).
4. **Managed HA** — AWS **Aurora Postgres** (auto read-scaling, failover) when on cloud,
   or **Patroni** for self-hosted HA on its own fast-NVMe host.
5. **Only at extreme write scale:** distributed SQL (Citus extension / CockroachDB) —
   you'd re-validate pgvector + migrations first, so treat this as a last resort.

Pair with **Redis** (cache hot catalog/search + host the JWT `jti` revocation denylist)
to take read pressure off Postgres entirely.

---

## 14. AI / KYC via Ollama Cloud (no local GPU)

- Set `OLLAMA_URL` to the Ollama Cloud endpoint and provide `OLLAMA_API_KEY` in `.env`.
- **IMPLEMENTED:** `WebClientConfig` attaches `Authorization: Bearer ${OLLAMA_API_KEY}`
  to the shared Ollama client whenever the key is set (blank = local server, no auth).
  Covers both KYC vision and search embeddings — no further code change needed.
- **PII reminder:** KYC selfies/IDs go to a third party. Fine for dev/low volume; get a
  DPA and confirm retention/residency before real regulated traffic. Non-PII search
  **embeddings** can use the same or a cheaper hosted API (DeepInfra/Together) freely.
- If unavailable, cases route to manual review automatically (existing behaviour) — no
  hard dependency.

---

## 15. Troubleshooting

- **Cert not issued:** check Traefik logs for the ACME/Cloudflare step; verify the CF
  token has *Zone:DNS:Edit* on the right zone; delete `traefik/acme.json` and retry.
- **Backend won't start:** `docker compose logs backend` — usually DB not healthy yet
  (compose waits on the healthcheck) or a bad `.env` value.
- **Admin 404s on refresh:** confirm `admin.conf` falls back to `/admin.html`.
- **CORS errors on admin:** `CORS_ADMIN_ORIGINS` must equal `https://admin.<domain>`.
- **KYC "OCR unavailable":** `deploy/tessdata/` empty or `KYC_TESSDATA_PATH` wrong.

---

## 16. Suggested order to execute

1. §2–§6: create `deploy/` + Dockerfiles + compose + `.env`.
2. §7: `docker compose up`, verify via hosts-file entries.
3. §8: MinIO bucket (+ ask me to implement the S3 backend integration).
4. §9: LAN DNS when you move to a Hyper-V/Proxmox VM.
5. §10: Cloudflare Tunnel + Access when you want public access.
6. §11: Vault when the infra VM is available.
7. §13/§14 as scale/AI needs grow.
