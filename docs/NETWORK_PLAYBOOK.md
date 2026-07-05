# Network Configuration Playbook — Containers → VMs → Devices → Internet

The end-to-end network path for the whole ecosystem, using a Cloudflare-managed
domain and **Cloudflare Tunnel** so nothing is exposed by opening router ports.
Works identically on WSL2, Hyper-V, and Proxmox — only the VM addressing changes.

Companion docs: [DEPLOYMENT_PLAYBOOK.md](DEPLOYMENT_PLAYBOOK.md) (the stack),
[INFRASTRUCTURE.md](INFRASTRUCTURE.md) (the topology).

---

## 0. The full path at a glance

```
 Browser (anywhere)                              Laptop + Cloudflare WARP
   │ https://shop.example.com                      │ (Zero Trust private route)
   ▼                                               │
 ┌──────────────────────────────────────────────┐ │
 │ CLOUDFLARE EDGE                               │ │
 │  DNS · TLS · WAF · rate-limit · Access(admin) │ │
 └───────────────┬──────────────────────────────┘ │
                 │ encrypted tunnel (OUTBOUND only, UDP/TCP 7844)
                 │ NO inbound ports on the home router
                 ▼                                 ▼
        ┌──────────────────────── VM1 (apps) ───────────────────────┐
        │  cloudflared ──► Traefik ──► storefront / admin / backend │
        │  (edge net)      (edge net)     │                         │
        │                                 ▼ (internal "data" net)   │
        │                         Postgres · App Vault · MinIO      │
        └───────────────┬───────────────────────────────────────────┘
                        │ private VM↔VM net (10.10.10.0/24)
                        ▼
        ┌──────────────────────── VM2 (infra) ──────────────────────┐
        │  Harbor · Jenkins · Infra Vault · (their own cloudflared)  │
        └───────────────────────────────────────────────────────────┘
```

**Key property:** the only connection leaving your network is `cloudflared`
dialling **out** to Cloudflare. No port-forwarding, no static public IP, no
inbound firewall holes. Your home IP is never advertised.

---

## 1. Layer 1 — container networking (inside each VM)

Segment the Docker networks so the database and secrets are unreachable from the
web tier, and **only the edge publishes ports**. This is **not a separate compose
file** — it's the `edge`/`data` split baked into the one
[`deploy/docker-compose.yml`](DEPLOYMENT_PLAYBOOK.md#6-deploydocker-composeyml) from the
Deployment Playbook (§6). The full, ready-to-copy file lives there (§6.7); the
essentials:

```yaml
networks:
  edge:                 # web tier - internet-facing via cloudflared/Traefik
    name: ecommerce_edge
  data:                 # DB / secrets / storage - NEVER internet-facing
    name: ecommerce_data
    internal: true      # no route to the outside; only same-network containers reach it
```
Service → network attachment (all in that same file):
```yaml
services:
  cloudflared: { networks: [edge] }         # + profiles: ["public"]
  traefik:     { networks: [edge] }
  storefront:  { networks: [edge] }
  admin:       { networks: [edge] }
  backend:     { networks: [edge, data] }   # the only bridge between tiers
  postgres:    { networks: [data] }
  minio:       { networks: [data] }
  # vault:     { networks: [data] }         # add when you introduce Vault (P2+)
```
Rules:
- **Do not** publish host ports (`ports:`) for postgres/vault/minio. They talk to
  the backend over the `data` network by service name (`postgres:5432`).
- Only Traefik binds host ports (80/443) for LAN access; `cloudflared` needs **no**
  published ports at all (it dials out).
- `internal: true` on `data` means even a compromised web container can't reach the
  internet *from* the DB tier.

---

## 2. Layer 2 — inter-VM networking (VM1 ↔ VM2)

Put both VMs on a private network (Proxmox: a Linux bridge / VLAN; Hyper-V: an
internal or private vSwitch; WSL2: they share the host). Example `10.10.10.0/24`:
`VM1 = 10.10.10.11`, `VM2 = 10.10.10.12`.

| From → To | Port | Purpose |
|---|---|---|
| VM1 backend → VM2 Harbor | 443 | pull images (robot account) |
| VM2 Jenkins → VM1 | 22 (SSH) or 2376 (Docker API/TLS) | deploy |
| VM2 Jenkins → VM2 Harbor | 443 | push images |
| VM1/VM2 apps → their Vault | 8200 | secrets (intra-VM, over `data` net) |
| Both VMs → Cloudflare | 443/7844 **outbound** | tunnel |
| Both VMs → Docker Hub/Maven/npm | 443 outbound | builds/pulls |

Firewall posture per VM:
- **Inbound from internet:** none (default deny).
- **Inbound from LAN:** only what the table needs; ideally only 443 (Traefik) + SSH.
- **Inbound from the other VM:** the specific ports above.
- **Outbound:** 443 (Cloudflare + registries). Lock down the rest if you want.

---

## 3. Layer 3 — host / hypervisor networking

| Host | How VMs/containers get on the net | Reaching from LAN | Notes |
|---|---|---|---|
| **WSL2** | NAT behind Windows; `localhost` forwards to Windows | Awkward (needs `netsh portproxy`) | **Doesn't matter** — cloudflared gives public access without LAN reachability. |
| **Hyper-V** | External vSwitch = bridged (VM gets a LAN IP) | Direct via VM IP | Cleanest for LAN + tunnel. |
| **Proxmox** | Linux bridge `vmbr0` (bridged) + optional VLANs | Direct via VM IP | Target state; put the lab on its own VLAN/subnet. |

**Insight for WSL2:** you do **not** need to solve WSL2's inbound networking to go
public. `cloudflared` runs as a container inside WSL2 and makes only *outbound*
connections, so `https://shop.example.com` works from anywhere even though the WSL2
VM isn't reachable on your LAN. LAN access (Traefik on `localhost`) is a separate,
optional convenience via a hosts-file entry.

---

## 4. Layer 4 — Cloudflare Tunnel (`cloudflared`)

### 4.1 Create the tunnel (once)
```bash
cloudflared tunnel login                       # browser auth to your CF account
cloudflared tunnel create ecommerce            # -> tunnel UUID + credentials json
cloudflared tunnel route dns ecommerce shop.example.com
cloudflared tunnel route dns ecommerce api.example.com
cloudflared tunnel route dns ecommerce admin.example.com
```
Each `route dns` creates a proxied CNAME `→ <UUID>.cfargotunnel.com` in your zone.

### 4.2 Run it as a container (VM1)
```yaml
  cloudflared:
    image: cloudflare/cloudflared:latest
    restart: unless-stopped
    command: tunnel --no-autoupdate run
    environment:
      - TUNNEL_TOKEN=${CF_TUNNEL_TOKEN}   # dashboard-managed tunnel token (store in Vault)
    networks: [edge]
```
Two config styles — pick one:
- **Dashboard-managed (recommended for ease):** create the tunnel in Zero Trust →
  Networks → Tunnels, copy the **token** into `.env`/Vault as `CF_TUNNEL_TOKEN`, and
  define ingress (public hostnames) in the dashboard UI.
- **Config-file (GitOps-friendly):** mount `config.yml` + the credentials file:
  ```yaml
  # cloudflared/config.yml
  tunnel: <UUID>
  credentials-file: /etc/cloudflared/<UUID>.json
  ingress:
    - hostname: shop.example.com
      service: https://traefik:443
      originRequest: { noTLSVerify: true }   # Traefik holds the real cert; internal hop
    - hostname: api.example.com
      service: https://traefik:443
      originRequest: { noTLSVerify: true }
    - hostname: admin.example.com
      service: https://traefik:443
      originRequest: { noTLSVerify: true }
    - service: http_status:404               # catch-all (required last rule)
  ```

**Why point at Traefik, not the containers directly?** So all routing, security
headers, and middleware stay in one place (Traefik) regardless of whether traffic
arrived via the tunnel or the LAN. `noTLSVerify` is safe here — it's an internal
Docker-network hop; Cloudflare already did real TLS at the edge.

### 4.3 Infra VM
Run a **second** `cloudflared` on VM2 (its own tunnel) for Harbor/Jenkins, **or**
let VM1's connector reach VM2 over the private net via ingress
`service: http://10.10.10.12:<port>`. Prefer a per-VM connector for isolation.

---

## 5. Layer 5 — DNS & hostnames

| Hostname | Exposure | Tunnel ingress → | Protection |
|---|---|---|---|
| `shop.example.com` | **Public** | `https://traefik:443` | none (public storefront) |
| `api.example.com` | **Public** | `https://traefik:443` | app JWT; webhooks are signature-verified |
| `admin.example.com` | **Public** | `https://traefik:443` | **Cloudflare Access** + admin-audience JWT + CORS |
| `harbor.example.com` | **Private** | (WARP only) | Zero Trust private route / Access |
| `jenkins.example.com` | **Private** | (WARP) + webhook bypass | Access + path bypass for GitHub webhook |
| `vault.example.com` | **Private** | (WARP only) | never public — WARP only |
| `minio.example.com` (console) | **Private** | (WARP only) | WARP only |

- Public hostnames = **proxied** (orange cloud) so Cloudflare fronts TLS/WAF.
- Private infra: **do not** create public DNS. Reach them via **WARP** (§7.2).
- **Split-horizon (optional, LAN performance):** run a local resolver
  (AdGuard/dnsmasq) that answers `*.example.com` with the VM's LAN IP so on-LAN
  traffic hits Traefik directly instead of hair-pinning out to Cloudflare and back.

---

## 6. Layer 6 — TLS end-to-end

Two encrypted hops, no plaintext on any network you don't control:
1. **Browser → Cloudflare edge:** Cloudflare's public cert (automatic).
2. **Cloudflare → cloudflared → Traefik:** the tunnel is encrypted; Traefik then
   presents its **Let's Encrypt DNS-01** cert (from [DEPLOYMENT_PLAYBOOK.md](DEPLOYMENT_PLAYBOOK.md) §5).
3. **Traefik → app container:** internal Docker network.

Set the Cloudflare zone SSL/TLS mode to **Full (strict)** if Traefik presents a
publicly-trusted cert (it does, via DNS-01), otherwise **Full**. Never "Flexible".

---

## 7. Layer 7 — Cloudflare Access (Zero Trust identity)

### 7.1 Protect the admin app and infra UIs (browser)
Zero Trust → Access → Applications → **Add a self-hosted application**:
- Application domain: `admin.example.com` (and `harbor.`, `jenkins.` if you expose
  their UIs publicly).
- Policy: **Allow** where email is in your list / matches your IdP (Google, GitHub,
  Entra) / one-time-PIN. Everyone else gets an identity challenge before the app is
  even reachable.

This is defense-in-depth **on top of** the app's admin-audience JWT + `/api/admin/**`
CORS lock — an attacker must pass Cloudflare identity *and* hold a valid admin token.

### 7.2 Reach private infra (Vault/Harbor/Jenkins) without public DNS — WARP
The clean way to admin infra remotely without exposing it:
1. Zero Trust → Networks → Tunnels → your tunnel → **Private Networks** → add the
   lab CIDR: `10.10.10.0/24` (`cloudflared tunnel route ip add 10.10.10.0/24 ecommerce`).
2. Install the **Cloudflare WARP** client on your laptop, enroll it in your Zero
   Trust org, and split-tunnel-include `10.10.10.0/24`.
3. Now `https://10.10.10.12:8200` (Vault) etc. resolve **through the tunnel** from
   your laptop anywhere — never published to the internet. Gate with a Gateway/device
   posture policy.

### 7.3 The webhook exception (critical)
Some endpoints are hit by **machines**, not humans, and must NOT sit behind Access:
- **Payment callbacks / webhooks** on `api.example.com`: `/api/webhooks/stripe`,
  `/api/webhooks/omise`, `/api/payment/sslcommerz|paypay|linepay/**`. Keep
  `api.example.com` **public (no Access)** — the app verifies signatures/JWT itself.
- **GitHub → Jenkins:** if `jenkins.example.com` is behind Access, GitHub can't POST
  webhooks. Options: (a) an Access **Bypass** policy scoped to `/github-webhook/`
  validated by the GitHub secret; (b) restrict that path to GitHub's published IP
  ranges via a WAF rule; or (c) **avoid inbound entirely** — use SCM polling or a
  GitHub App so Jenkins pulls, needing no public endpoint. (c) is simplest and most
  secure for a home lab.

---

## 8. Layer 8 — WAF, rate limiting, egress

- **WAF (Cloudflare):** enable managed rules; add a rule to challenge/bad-bot the
  login and checkout paths. Rate-limit `/api/auth/*` and `/api/admin/*`.
- **Firewall / egress:** allow VMs outbound 443 to Cloudflare + package registries;
  deny other egress if you want a tight posture. No inbound rules needed at the
  router (tunnel is outbound).
- **DDoS/anonymity:** because only Cloudflare sees your origin (via the tunnel), your
  home IP stays hidden and Cloudflare absorbs volumetric attacks at the edge.

---

## 9. Phase mapping (what changes as you move)

| Concern | P1 (WSL2/Hyper-V) | P2 (Proxmox LAN) | P3 (public) |
|---|---|---|---|
| VM addressing | NAT / bridged | bridged + VLAN | same as P2 |
| LAN access | hosts-file → `127.0.0.1` | local DNS → VM IP | via Cloudflare or WARP |
| Public access | cloudflared (works on WSL2!) | cloudflared | cloudflared + Access + WAF |
| Infra access | localhost | LAN | WARP private routes |
| Router changes | **none** | **none** | **none** |

The tunnel means P1 → P3 needs **no router or ISP changes** at any step — you're
adding Cloudflare policy, not opening ports.

---

## 10. Security checklist

- [ ] No `ports:` on postgres/vault/minio; `data` network `internal: true`.
- [ ] Router has **zero** inbound port-forwards; only outbound 443 to Cloudflare.
- [ ] Public hostnames proxied (orange cloud); SSL mode Full (strict).
- [ ] `admin.example.com` behind Cloudflare Access (IdP/OTP).
- [ ] Infra (Vault/Harbor/Jenkins) has **no public DNS** — WARP private routes only.
- [ ] Webhook paths (`/api/webhooks/*`, `/api/payment/*`, `/github-webhook/`) reachable
      without Access; everything else identity-gated or app-secured.
- [ ] Tunnel token / tunnel credentials stored in Vault, not in git.
- [ ] WAF managed rules on; rate-limit auth + admin paths.
- [ ] `CORS_ADMIN_ORIGINS = https://admin.example.com` (matches the split admin app).

---

## 11. Troubleshooting

- **502 via the hostname:** cloudflared can't reach the origin. Check the ingress
  `service:` target resolves on the tunnel's Docker network (`traefik:443`) and that
  Traefik has a router for that Host.
- **Cert warning through the tunnel:** you're serving Traefik's cert for a name it
  wasn't issued for — set `originRequest.originServerName` to the public hostname or
  keep `noTLSVerify: true` for the internal hop (edge TLS is what users see).
- **Admin redirect loop / blocked XHR:** Access is protecting `api.example.com` too —
  it shouldn't; only protect `admin.example.com`. Keep the API public + app-secured.
- **GitHub webhook 403:** Jenkins is behind Access with no bypass — add the
  `/github-webhook/` bypass or switch to SCM polling.
- **Can't reach Vault via WARP:** private network CIDR not routed on the tunnel, or
  the laptop's WARP split-tunnel doesn't include `10.10.10.0/24`.
