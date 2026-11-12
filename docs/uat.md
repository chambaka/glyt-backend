# GoLyt UAT script (Phase 6)

Run against a seeded pilot database with demo staff (`Golyt#2026`) and at least one customer account. Mark each step Pass / Fail / Blocked.

## Staff console (Vue)

| # | Scenario | Steps | Expected |
| --- | --- | --- | --- |
| U1 | Sign-in & password gate | Login as `lead@golyt.co.tz` | Dashboard loads; forced password change if flagged |
| U2 | Workload / SLA | Open Workload | Counts and SLA queue match open exceptions; overdue rows highlighted |
| U3 | Bag intake → collection | Create bag at KIA, seal, manifest, receive ZNZ, store, ready, collect | Status trail and storage occupancy update |
| U4 | Exception SLA | Raise CRITICAL exception | Due ≈ 2h; appears overdue after due time; resolves with notes |
| U5 | Support triage | Create ticket via customer API; assign & resolve in Support | Status RESOLVED; resolution notes stored; audit row written |
| U6 | Facilities admin | ADMIN creates location + storage slot; deactivate empty slot | Lists refresh; occupied slot cannot deactivate |
| U7 | Catalog / pricing | ADMIN creates service with price; update price | Public `/api/v1/services` shows new active price |
| U8 | Payments | Customer pays booking; ops refunds as ADMIN | Booking OPEN after success; reconcile CSV downloads |
| U9 | Audits export | Open Audits; Export CSV | JSON list and `audits.csv` download |

## Customer website

| # | Scenario | Expected |
| --- | --- | --- |
| C1 | EN/SW quote → booking → pay (sandbox) | Booking PENDING_PAYMENT then OPEN; receipt available |
| C2 | Track bag by code/token | Public track page shows status without login |
| C3 | Support ticket from website/customer API | Ticket appears in ops Support inbox |

## Officer app (Flutter — `glyt-app`)

| # | Scenario | Expected |
| --- | --- | --- |
| O1 | KIA receive / seal / photo | Bag progresses; photo stage recorded |
| O2 | Manifest handoff | Bags linked; airline custody status |
| O3 | ZNZ receive / store / ready / collect | Storage board and readiness match console |

## Exit criteria

- Zero Blocked on U1–U9, C1–C3, O1–O3
- All CRITICAL/HIGH defects closed or waived in writing
- Training guide walked with one lead + one officer
- Cutover checklist signed by ops lead
