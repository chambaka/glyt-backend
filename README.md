# GoLyt

Staff operations system for the Kilimanjaro (KIA) → Zanzibar luggage storage and transfer pilot.

This folder is the API and the local deployment: Spring Boot, Compose, Nginx, and backups.

- **Ops phone app:** Flutter in `/home/lupo/projects/tz.co.chambaka/glyt-app` for KIA and Zanzibar officers. White app bar, navy text, sky blue `#638DB5`, background `#F7FAFC`.
- **Ops console:** Vue app in `/home/lupo/projects/tz.co.chambaka/glyt-admin-ui` for the operations lead and read-only accounts.
- **Public website:** Vue app in `/home/lupo/projects/tz.co.chambaka/glyt-website` (marketing / book / track). Customers use the website only — there is no traveler Flutter app.
- **API:** Spring Boot 3.5, Java 21 in this folder. Schema is managed by Hibernate (`spring.jpa.hibernate.ddl-auto=update`).
- **Database:** the existing MySQL container named `mysql` on host port 3306, database `golyt`.
- **Files:** switchable object storage — `local` (default) or Cloudflare **R2** via `APP_STORAGE_TYPE=r2` (S3 API).
- **HTTPS:** Nginx on `https://localhost:8443` proxies to the API. Certificates are generated into `deploy/certs`.

Customers do not get an app. They receive a tracking link, for example `http://localhost:8080/track/BG-000001/<token>`.

Local steps below use the pilot passwords and a self-signed certificate. For a public host, follow [docs/production.md](docs/production.md).

## Run

```bash
cd /home/lupo/projects/tz.co.chambaka/glyt-backend
mkdir -p deploy/certs backups
openssl req -x509 -nodes -newkey rsa:2048 -keyout deploy/certs/golyt.key -out deploy/certs/golyt.crt -days 365 -subj "/CN=localhost"
docker compose up -d
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn spring-boot:run
```

In other terminals:

```bash
cd /home/lupo/projects/tz.co.chambaka/glyt-app
flutter run
```

```bash
cd /home/lupo/projects/tz.co.chambaka/glyt-admin-ui
npm install
npm run dev
```

The console opens at `http://localhost:5180`.

On a phone, set the login **Server** field to your computer’s address, for example `http://192.168.1.20:8080`. The Android emulator can use `http://10.0.2.2:8080`.

Access tokens last 15 minutes. The app refreshes them and signs out after 30 minutes of inactivity. A new user created in the console must change password on first sign-in. Demo accounts do not.

```bash
./scripts/backup.sh
python3 scripts/acceptance.py
```

## Pilot accounts

Password for all of them: `Golyt#2026`

| Email | Where to sign in |
| --- | --- |
| kia@golyt.co.tz | Phone app |
| znz@golyt.co.tz | Phone app |
| lead@golyt.co.tz | Web console |
| board@golyt.co.tz | Web console |

API docs: `http://localhost:8080/swagger-ui.html`

## Object storage (local ↔ Cloudflare R2)

Photos go through `ObjectStore`. Switch with `APP_STORAGE_TYPE`:

| Type | Behaviour |
| --- | --- |
| `local` (default) | Files under `APP_STORAGE_UPLOAD_DIR` (default `data/uploads`) |
| `r2` | Cloudflare R2 (S3-compatible) — production default |
| `s3` / `minio` | Other S3-compatible endpoints |

**Local (pilot default):**

```bash
export APP_STORAGE_TYPE=local
```

**Cloudflare R2 (production):**

1. Create an R2 bucket (e.g. `golyt`) in the Cloudflare dashboard.
2. Create an R2 API token with Object Read & Write (Admin if the API should create the bucket).
3. Copy the S3 API endpoint: `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`.

```bash
export APP_STORAGE_TYPE=r2
export APP_STORAGE_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
export APP_STORAGE_BUCKET=golyt
export APP_STORAGE_ACCESS_KEY='<r2-access-key-id>'
export APP_STORAGE_SECRET_KEY='<r2-secret-access-key>'
export APP_STORAGE_REGION=auto
```

Startup logs `Object storage: r2 ...`. `/actuator/health` reports `storage.backend=r2`.

## Production

Deploy steps, the settings that must replace the pilot secrets, and backup restore are in [docs/production.md](docs/production.md). Publish Nginx only. The API uses the existing MySQL container on port 3306. Keep port 8080 on the host. Photos go to Cloudflare R2.
