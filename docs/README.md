# Documentation

Operator and design docs for deploying and running the e-commerce platform on
self-hosted infrastructure. Start with the reading order below.

## Read in this order

| # | Doc | What it's for | Type |
|---|-----|---------------|------|
| 1 | [INFRASTRUCTURE.md](INFRASTRUCTURE.md) | The big picture: 3-phase rollout, VM topology, CI/CD, secrets, AI, scaling. Read this first to understand *what* you're building. | Design / reference |
| 2 | [DEPLOYMENT_PLAYBOOK.md](DEPLOYMENT_PLAYBOOK.md) | Hands-on, follow-top-to-bottom: stand up the whole stack with Docker Compose + Cloudflare TLS. **Start here to actually deploy.** | Step-by-step runbook |
| 3 | [NETWORK_PLAYBOOK.md](NETWORK_PLAYBOOK.md) | How traffic flows container → VM → device → internet via Cloudflare Tunnel; where each hostname is exposed. | Step-by-step runbook |
| 4 | [POST_DEPLOYMENT_SECRETS.md](POST_DEPLOYMENT_SECRETS.md) | Replace dev placeholder secrets with real vault-managed ones before go-live. | Operator runbook |

## In a hurry? — just deploy

Go straight to **[DEPLOYMENT_PLAYBOOK.md](DEPLOYMENT_PLAYBOOK.md)**: do §1 (Prerequisites)
→ §2–§6 (create the files) → §7 (bring it up). That gets you a working stack on your
machine. Come back for networking (public access) and secrets (go-live) when you need them.

## Conventions used across all four docs

- **Domain:** written as `example.com` everywhere — swap in your real Cloudflare-managed
  domain wherever you see it.
- **`<...>`** = a fill-in variable that isn't a domain (e.g. `<UUID>`, `<port>`).
- **Callouts** always mean the same thing:
  - > **Note:** — context or a clarification worth knowing.
  - > **⚠️ Warning:** — do this / don't do this, or you'll get hurt.
  - > **Tip:** — an optional improvement or shortcut.
- **Command blocks** are preceded by a plain-language line describing what they do, so you
  can read the intent before running anything.

## How the docs relate

```
INFRASTRUCTURE  ── the plan ──►  DEPLOYMENT_PLAYBOOK ── you run this ──►  a working stack
      │                                  │
      │                                  ├─►  NETWORK_PLAYBOOK      (expose it safely)
      └──────────────────────────────────┴─►  POST_DEPLOYMENT_SECRETS  (secure it for go-live)
```
