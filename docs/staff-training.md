# GoLyt staff training guide

Audience: operations lead, KIA officers, Zanzibar officers, read-only observers. Duration: ~90 minutes classroom + 30 minutes shadow.

## Roles

| Role | Console | Officer app | Typical work |
| --- | --- | --- | --- |
| ADMIN | Full | Optional | Users, facilities, catalog, refunds, notifications process |
| KIA_OFFICER | Read + exceptions/support write | Yes | Intake, seal, photos, exceptions |
| ZNZ_OFFICER | Read + exceptions/support write | Yes | Receive, store, ready, collect |
| READONLY | View only | No | Monitoring, reports |

Customers use the public website only. Ops staff do not book for themselves in the officer app (`glyt-app`).

## Daily rhythm

1. **Open of day** — Workload tab: overdue SLA, open support, pending payments, KIA vs ZNZ custody counts.
2. **KIA shift** — Receive bags for OPEN bookings only; seal; capture photos; raise exceptions for damage/mismatch.
3. **Airline handoff** — Build / close manifests; confirm bag counts.
4. **ZNZ shift** — Receive from airline; assign storage; mark ready; collect with ID check.
5. **Support** — Assign tickets; resolve with notes the customer can understand.
6. **Close of day** — Reports (movements, exceptions); Audits export if required by compliance.

## SLA priorities (exceptions)

| Priority | Target due | Use when |
| --- | --- | --- |
| CRITICAL | 2 hours | Lost bag, safety, VIP escalation |
| HIGH | 8 hours | Misroute, seal break, customer waiting |
| NORMAL | 24 hours | Documentation, minor damage follow-up |

Overdue rows highlight on Workload and Exceptions. Resolve with clear notes; do not leave OPEN overnight without an assignee.

## Console map

- **Dashboard** — snapshot KPIs and alerts
- **Workload** — SLA queue and staff load
- **Bags / Manifests** — operational lists (lead view)
- **Exceptions / Support** — triage
- **Payments** — status, refunds (ADMIN), reconcile CSV
- **Facilities** — locations and storage slots (ADMIN edits)
- **Catalog** — services and prices (ADMIN)
- **Reports / Audits** — CSV exports
- **Users** — ADMIN only

## Payments reminder

Bookings stay `PENDING_PAYMENT` until payment succeeds, then become `OPEN`. Do not intake bags for unpaid bookings. Refunds are ADMIN-only and are audited.

## Password & access hygiene

Change seeded passwords on first login. Never share accounts. Sign out on shared desks. Forgot-password links come from API logs when mail is not configured — hand the link to the staff member in person or via a secure channel.
