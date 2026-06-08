---
name: marketplace-escrow-feature
description: "Marketplace escrow / buyer protection implemented 2026-06-07 — business policies, key decisions, current status (UNCOMMITTED), and agreed follow-ups"
metadata: 
  node_type: memory
  type: project
  originSessionId: 0fc4d7f4-3030-4208-a38a-0d56bb2a26b7
---

Full marketplace escrow system implemented 2026-06-07 (backend + frontend + integration test). **Status: COMMITTED and pushed 2026-06-08** as 6 conventional commits (402369d fix(build) → 5b3ec3a chore(dev), feature in 164a39b) on origin/main.

## Business policies (decided with the user, not all obvious from code)

- **Model**: marketplace escrow (not single-seller buyer-protection). Funds captured at checkout are journaled HELD per **(order, seller) group** (`escrow_transactions`); released to the **seller's in-app wallet** — real bank payouts / Stripe Connect deliberately deferred, wallet is the payout boundary.
- **Protection window**: starts at DELIVERED, `escrow.protection-days` (default 7). Silence = satisfied → auto-release job. Buyer "confirm receipt" releases early. DISPUTED rows are frozen — staff resolution is the only exit.
- **Coupon proration**: order-level coupon discount is split across seller groups proportionally to group subtotal; rounding remainder goes to the last group so rows sum exactly to order total. Same proration applies to per-item return refunds.
- **Disputes**: buyer opens against one escrow group → buyer↔seller message thread (image ≤10MB / video ≤100MB evidence) → either side can ESCALATE (forwards whole thread to admin queue) → staff verdict RELEASE or REFUND (full/partial). **A partial dispute refund releases the remainder to the seller immediately** — dispute concludes either way. One active dispute per escrow row (partial unique index).
- **Returns**: per-item with quantity, only while that seller's escrow is HELD (window open, no active dispute). Approved by ADMIN/MANAGER **or the group's seller**. Refund clawed from escrow before release; `order_items.returned_quantity` tracks units.
- **Refund routing**: try the capturing gateway first (`PaymentGateway.refund()` — implemented for Stripe + Mock); cash/one-time methods or refund-incapable gateways fall back to **buyer wallet credit**. Destination recorded (GATEWAY/WALLET). Stripe refunds go to the original card (~5–10 business days).
- **Order payment status**: total escrow refunds drive `PARTIALLY_REFUNDED` / `REFUNDED` automatically.

## Conventions adopted (user mandate)

- **All status enums system-wide implement `NumericEnum` and are stored as SMALLINT codes** (JSON still uses names). Codes are a persisted contract — never renumber, only append. Existing `orders.status`/`payment_status` were converted in V16; new enum = coded enum + reader/writer pair in `config/converter/NumericEnumConverters` + entry in `all()`. `User.role` deliberately left as VARCHAR (it's a role, not a status; JWT coupling).
- Raw SQL touching order status must use numeric codes (e.g. ReservationCleanupJob: 0=PENDING, 5=CANCELLED).

## Agreed follow-ups (not yet done)

- Commit the work (split commits, no AI trailer per repo rules).
- Wire `refund()` for SSLCommerz / PayPay / LINE Pay / Omise (currently wallet-fallback).
- Wallet spend-at-checkout + withdrawals (`WITHDRAWAL` ref type reserved); Stripe Connect for real seller payouts.
- Dispute evidence at `/uploads/disputes/**` is authenticated-only but not party-checked (unguessable double-UUID URLs accepted as v1).
- V2 seed admin hash was fixed in-place (2026-06-07) + local `flyway repair`; if other environments exist, they need repair too — or revert and ship a V17 UPDATE instead.
- Repo has no `mvnw` wrapper though CLAUDE.md references it — worth committing one.

Related: [[dev-environment-toolchain]] (ports, DB container, build commands).
