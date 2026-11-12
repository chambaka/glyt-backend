# GoLyt production deployment

This guide is for a single server that already runs MySQL in the container named `mysql`. Compose adds Nginx and a backup job. Object photos use **Cloudflare R2**. The Spring Boot API runs on the host. KIA and Zanzibar officers use the Flutter app. The operations lead and read-only accounts use the Vue console in `glyt-admin-ui`.

Customers only open the tracking page. They do not install an app.

## What must change before go-live

`glyt-backend/src/main/resources/application.yml` is the local pilot file. Override it with environment variables. Do not commit real secrets.

| Setting | Pilot value | Production |
| --- | --- | --- |
| `APP_JWT_SECRET` | a checked-in development secret | at least 32 random bytes, unique to this server |
| `SPRING_DATASOURCE_PASSWORD` | `golyt` on the existing MySQL container | a long unique password for the `golyt` user |
| `APP_SEED_ENABLED` | `true` | `true` only for the first start, then `false` |
| `APP_MAIL_EXPOSE_RESET_TOKEN` | `true` | `false` |
| `APP_OPS_PUBLIC_BASE_URL` | `http://localhost:8080` | the public HTTPS origin, with no trailing slash |
| `APP_STORAGE_TYPE` | `local` | `r2` (Cloudflare R2) |
| `APP_STORAGE_ENDPOINT` | empty / unused for local | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` |
| `APP_STORAGE_BUCKET` | `golyt` | R2 bucket name |
| `APP_STORAGE_ACCESS_KEY` / `APP_STORAGE_SECRET_KEY` | empty for local | R2 API token access key + secret |
| `APP_STORAGE_REGION` | `auto` | `auto` for R2 |
| TLS certificate | self-signed `deploy/certs` | a certificate for the public hostname |
| Pickup desk fields | placeholder Zanzibar desk | the real desk name, place, hours, phone, and WhatsApp number |

Demo accounts are created only when the users table is empty and seeding is enabled. Password for all four is `Golyt#2026`. After the first start, sign in as `lead@golyt.co.tz`, change every password, then set `APP_SEED_ENABLED=false` and restart. Seeding does not run again once any user exists.

Forgot-password does not send email. With `APP_MAIL_EXPOSE_RESET_TOKEN=false` the API logs the reset link and the HTTP response stays generic. An operator reads that link from the API log and gives it to the staff member.

## Network

Publish only HTTPS to the office network or the public internet.

| Port | Service | Exposure |
| --- | --- | --- |
| 443 or 8443 | Nginx | public |
| 8080 | API | localhost only |
| 3306 | Existing MySQL container `mysql` | localhost only |
Compose does not start MySQL. The API uses the container already named `mysql` on port 3306, in a database named `golyt`. On a production host, do not publish that port. Leave Nginx as the only public listener. Photos are stored in Cloudflare R2, not on the API host.

`deploy/nginx.conf` proxies to `http://host.docker.internal:8080`, which is the API process on the host. The phone app and the tracking links must use the Nginx URL, not port 8080.

The web console is the Vue app in `glyt-admin-ui`. Browser CORS in `SecurityConfig` allows `http://localhost` and `http://127.0.0.1` only, which covers the local dev server. A console hosted on another origin needs that allow-list updated. The Flutter app is not a browser, so that limit does not affect officers.

## Server preparation

- Java 21 (`JAVA_HOME` pointing at a 21 JDK; this machine uses `/usr/lib/jvm/java-21-openjdk-amd64`)
- Docker with Compose
- A public DNS name for the Nginx certificate
- Disk for MySQL and `backups/`
- Cloudflare R2 bucket + API token

Clock and the JDBC URL stay on `Africa/Dar_es_Salaam`. The API stores instants in UTC.

## Database and object storage

From the repository root:

Create the database on the existing MySQL container:

```bash
docker exec -i mysql mysql -uroot -p <<'SQL'
CREATE DATABASE IF NOT EXISTS golyt CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'golyt'@'%' IDENTIFIED BY 'replace-me';
GRANT ALL PRIVILEGES ON golyt.* TO 'golyt'@'%';
FLUSH PRIVILEGES;
SQL
```

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/golyt?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Dar_es_Salaam'
export SPRING_DATASOURCE_USERNAME=golyt
export SPRING_DATASOURCE_PASSWORD='replace-me'
```

Hibernate creates and updates tables on startup (`spring.jpa.hibernate.ddl-auto=update`). There is no Flyway.

### Cloudflare R2 (production photos)

Create the bucket in the Cloudflare dashboard, then:

```bash
export APP_STORAGE_TYPE=r2
export APP_STORAGE_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
export APP_STORAGE_BUCKET=golyt
export APP_STORAGE_ACCESS_KEY='replace-me'
export APP_STORAGE_SECRET_KEY='replace-me'
export APP_STORAGE_REGION=auto
```

The API talks to R2 over the S3 API. Prefer creating the bucket in the dashboard; the API will try to create it only if the token allows Admin.

## API

```bash
cd /home/lupo/projects/tz.co.chambaka/glyt-backend
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -DskipTests package
```

The jar is `glyt-backend/target/golyt-0.1.0-SNAPSHOT.jar`.

```bash
export APP_JWT_SECRET='replace-with-32-bytes-or-more'
export APP_SEED_ENABLED=true
export APP_MAIL_EXPOSE_RESET_TOKEN=false
export APP_OPS_PUBLIC_BASE_URL='https://golyt.example.com'
export APP_OPS_PICKUP_NAME='GoLyt Zanzibar desk'
export APP_OPS_PICKUP_PLACE='Abeid Amani Karume International Airport, arrivals hall'
export APP_OPS_PICKUP_HOURS='06:00–22:00 daily'
export APP_OPS_PICKUP_PHONE='+255 ...'
export APP_OPS_PICKUP_WHATSAPP='255...'
java -jar target/golyt-0.1.0-SNAPSHOT.jar
```

A systemd unit can use the same variables in an `EnvironmentFile` that is not in git. Restart the service after changing them.

Check `GET /actuator/health`. It is the only actuator endpoint exposed.

Swagger stays on `/swagger-ui.html`. Do not publish port 8080 if that UI should stay internal. Officers do not need it.

## HTTPS

Put a real certificate and key in `deploy/certs/golyt.crt` and `deploy/certs/golyt.key`. Those paths are gitignored. Point `server_name` in `deploy/nginx.conf` at the public hostname, then:

```bash
docker compose up -d nginx
```

`APP_OPS_PUBLIC_BASE_URL` must be the same origin Nginx serves. Tracking links, WhatsApp shares, and password-reset links are built from it.

To listen on 443 instead of 8443, change the `listen` directive and the Compose port mapping together.

## Phone app

Build a release binary and install it on the KIA and Zanzibar devices:

```bash
cd /home/lupo/projects/tz.co.chambaka/glyt-app
flutter build apk --release
```

On first launch, set **Server** to the public HTTPS origin, for example `https://golyt.example.com`. The app remembers that address. KIA officers sign in as `kia@golyt.co.tz`. Zanzibar officers sign in as `znz@golyt.co.tz`. Admin and read-only accounts are rejected and told to use the web console.

Access tokens last 15 minutes and are refreshed with the 8-hour refresh token. The app signs out after 30 minutes without a touch. Five failed passwords lock the account for 15 minutes.

## Web console

Build and serve the Vue app from `glyt-admin-ui`:

```bash
cd /home/lupo/projects/tz.co.chambaka/glyt-admin-ui
VITE_API_URL=https://golyt.example.com npm run build
```

Host the `dist` folder on the public site, then open that site.

| Email | Role |
| --- | --- |
| lead@golyt.co.tz | Operations lead: dashboard, exceptions, reports, users |
| board@golyt.co.tz | Read only: dashboard, exceptions, reports |

KIA and Zanzibar officers are rejected here. New users created by the operations lead must change their password before they can work. Give KIA officers location `KIA` and Zanzibar officers location `ZNZ`. `ALL` is for admin and read-only.

## Backups

The `backup` Compose service runs `scripts/backup-loop.sh` every 24 hours and deletes dumps older than 14 days. Files land in `backups/`, which is gitignored.

A manual dump from the host:

```bash
./scripts/backup.sh
```

Restore onto an empty database, then start the API so Hibernate can validate/update against the restored schema:

```bash
docker exec -i mysql mysql -ugolyt -p golyt < backups/golyt-YYYYmmdd-HHMMSS.sql
```

Copy `glyt-backend/data/uploads` as well when `APP_STORAGE_TYPE` is `local`. With `r2`, photos live in the Cloudflare R2 bucket (not in the MySQL dump).

## After the first start

1. Open `/actuator/health` through Nginx.
2. Sign in to the Vue console as the operations lead and change the demo password.
3. Change the KIA, Zanzibar, and read-only passwords.
4. Set `APP_SEED_ENABLED=false` and restart the API.
5. Confirm a tracking link uses `APP_OPS_PUBLIC_BASE_URL` and loads over HTTPS.
6. From a staff phone, sign in, register one test bag, and confirm the photo and QR label.
7. Confirm `backups/` receives a dump within a day, or run `./scripts/backup.sh` once.

`python3 scripts/acceptance.py` is a local pilot check against `http://localhost:8080` and the demo password. Do not run it against production.
