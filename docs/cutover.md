# GoLyt production cutover checklist

Use with `docs/production.md`. Sign each block. Do not skip backups.

## T-7 days

- [ ] Production host, DNS, TLS cert ready
- [ ] MySQL `golyt` database and user with strong password
- [ ] Cloudflare R2 bucket + API token ready (`APP_STORAGE_TYPE=r2`)
- [ ] `APP_JWT_SECRET` ≥ 32 random bytes generated and stored offline
- [ ] CORS allow-list updated for the real admin UI origin
- [ ] Pickup desk copy (name, place, hours, phone, WhatsApp) finalized
- [ ] UAT script signed Pass for exit criteria
- [ ] Staff training completed for lead + at least one officer per site

## T-1 day

- [ ] Final pilot data export / backup of MySQL and photo storage
- [ ] Confirm Nginx proxies to host API; only HTTPS public
- [ ] Payment provider sandbox keys swapped for live keys (or confirm sandbox stay for soft launch)
- [ ] Website build pointed at production API base URL
- [ ] Admin UI production build deployed behind HTTPS
- [ ] Freeze non-essential schema changes

## Cutover window

1. [ ] Announce maintenance / pause new customer bookings if required
2. [ ] Stop API process; take MySQL dump to `backups/`
3. [ ] Start API with production env so Hibernate updates schema (`ddl-auto=update`; `APP_SEED_ENABLED=true` only if users table empty)
4. [ ] First ADMIN login → change all seeded passwords → set `APP_SEED_ENABLED=false` → restart
5. [ ] Set `APP_MAIL_EXPOSE_RESET_TOKEN=false`
6. [ ] Smoke: login, workload, create/list bag path, quotation, sandbox/live payment, support ticket, audit CSV
7. [ ] Officer app login at KIA and ZNZ
8. [ ] Website EN/SW quote page loads
9. [ ] Resume bookings; monitor Workload for 2 hours

## Rollback

- [ ] Keep previous API jar / image tagged
- [ ] Restore MySQL dump if migration or data corruption blocks ops
- [ ] Re-point Nginx / DNS only after smoke Pass
- [ ] Document incident and reopen UAT items that failed

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Ops lead | | | |
| Tech owner | | | |
| Finance (payments) | | | |
