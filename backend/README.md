# Sahyog server

The Sahyog API: Spring Boot 4.1 (Kotlin, Java 25) on PostgreSQL + PostGIS.
It is a separate Gradle build from the Android app (its own `gradlew`).

## Run it locally

Requirements: Docker Desktop running, and a JDK 25 (Android Studio's bundled JBR works).

```sh
cd backend
./gradlew bootRun
```

- Starts the database from `compose.yaml` (PostGIS on port **5433**) automatically, applies the
  migrations in `src/main/resources/db/migration`, and serves on **http://localhost:8080**.
- `GET /actuator/health` → `{"status":"UP"}`.
- The phone reaches the laptop over USB: run `adb reverse tcp:8080 tcp:8080` once per USB
  connection; the app's default API address is `http://127.0.0.1:8080/`.

Tests run against a throwaway PostGIS container (Testcontainers):

```sh
./gradlew test
```

Stop the server with Ctrl+C. The database keeps running in Docker for the next start;
`docker compose stop` stops it and `docker compose down -v` wipes it.

## How requests are authenticated

The app signs in with Firebase (phone OTP) and sends the Firebase **ID token** as
`Authorization: Bearer <token>`. The server checks its signature against Google's public keys,
plus expiry, issuer and audience (`https://securetoken.google.com/<project-id>`). Only the public
project id is configured (`FIREBASE_PROJECT_ID`, default `sahyog-f6bad`): no secrets.

## Errors

Every error is `{"code": "...", "field": "..."}` with a matching HTTP status
(400 validation, 401 unauthorized, 403 forbidden, 404 not found, 429 rate limited, 5xx server).
`field` uses the same names as the app's `*ErrorFields`, so forms can highlight the right input.

## API (v1)

| Method | Path | What |
|---|---|---|
| GET | `/api/v1/me` | The caller's profile; 404 until onboarding is done |
| PUT | `/api/v1/me` | `{"name", "roles"}`: complete onboarding / update. Roles can be added, not dropped |
| GET | `/api/v1/catalog` | Categories + every verified worker |
| GET | `/api/v1/workers/nearby?lat&lng&radiusKm&categoryId` | Verified workers within the radius, nearest first (PostGIS) |
| GET | `/api/v1/worker/profile` | The worker's own profile; 204 if not filled in yet |
| PUT | `/api/v1/worker/profile` | Save the profile (same rules as the app's form; location rounded to ~1 km) |
| PUT | `/api/v1/worker/profile/availability` | `{"available"}` |
| POST | `/api/v1/worker/profile/document` | Multipart `type` + `image` (JPEG/PNG/WebP/HEIC, ≤ 10 MB) → Pending |
| GET | `/api/v1/admin/verifications` | Admin: workers waiting for review, oldest first |
| GET | `/api/v1/admin/verifications/{uid}/document` | Admin: the latest ID image (`no-store`) |
| POST | `/api/v1/admin/verifications/{uid}/approve` | Admin: Pending → Verified |
| POST | `/api/v1/admin/verifications/{uid}/reject` | Admin: `{"reason"}` → Rejected (the worker may submit again) |
| GET | `/api/v1/admin/overview` | Admin: figures + recent decisions |
| GET | `/api/v1/admin/workers?status&q` | Admin: directory, filter by status, search name/phone/area |
| GET | `/api/v1/admin/workers/{uid}` | Admin: profile, services, document info, status history |
| POST | `/api/v1/admin/workers/{uid}/revoke` | Admin: `{"reason"}` Verified → Rejected (removed from catalog) |
| GET | `/api/v1/bookings` | The customer's bookings (with the start code once accepted) |
| POST | `/api/v1/bookings` | Book: worker, service, slot, address, note, optional map pin → 201 |
| POST | `/api/v1/bookings/{id}/cancel` | Only while Requested or Accepted |
| POST | `/api/v1/bookings/{id}/review` | `{"rating", "comment"}` once, within 14 days of completion |
| GET | `/api/v1/worker/jobs` | The worker's jobs (never includes the start code) |
| POST | `/api/v1/worker/jobs/{id}/accept` · `decline` · `start-trip` · `complete` | One lifecycle step |
| POST | `/api/v1/worker/jobs/{id}/start` | `{"startCode"}`: the code the customer reads out |
| GET | `/api/v1/workers/{id}/reviews` | Recent reviews, newest first, reviewers by first name |

## Booking rules (server-enforced)

- Slots: 9, 11, 1, 3, 5 and 7 o'clock (2 hours), at least 60 minutes ahead, up to 7 days ahead, in
  India time (`sahyog.time-zone`).
- One live booking per worker per slot, enforced by a unique index (no race between customers).
- Workers set off / start at most 3 hours before the visit; starting needs the customer's code.
- Requests nobody accepts expire when the visit time arrives (checked every minute and on every read).
- Ratings and completed-job counts are computed from bookings; clients never send them.

## Admins

Cooperative staff are configured on the server, never through the API: set `ADMIN_PHONE_NUMBERS`
(E.164, comma-separated, e.g. `+91XXXXXXXXXX`) and they become admins the next time they open the
app. Removing a number doesn't revoke an existing admin; do that in the database. Locally:

```sh
ADMIN_PHONE_NUMBERS="+919876543210" ./gradlew bootRun
```

Or mark someone directly:

```sh
docker exec backend-postgres-1 psql -U sahyog -d sahyog \
  -c "UPDATE users SET is_admin = true WHERE phone_number = '+91XXXXXXXXXX'"
```

An admin can't approve their own worker profile. Every decision is kept (who, when, why).

## Development data

`./gradlew bootRun` uses the `dev` profile, which also loads `db/devdata`: 18 **fictional**
sample workers around Jalandhar (the same ones the app's demo catalog had), with a few fictional
past jobs and reviews. Being fictional, they "accept" requests by themselves after 20 seconds
(`SampleWorkerAutoAccept`, dev only). None of this exists in production. ID photos uploaded
locally go to `backend/data/documents` (gitignored).

## Google Cloud (temporary test deployment)

`deploy/deploy.sh` creates, in Mumbai (asia-south1): a Cloud Run service (scales to zero), a Cloud
SQL PostgreSQL 17 instance (smallest size, no backups), a private Cloud Storage bucket for ID photos,
a random database password in Secret Manager, and a service account with only the roles it needs.
The server runs with the `prod` profile (`application-prod.yaml`); nothing secret is in the repo.

```sh
cd backend
ADMIN_PHONE_NUMBERS="+91XXXXXXXXXX" deploy/deploy.sh   # prints the https URL
```

Then set `SAHYOG_API_URL=<that URL>/` in the app's `local.properties` and rebuild the app.

**Cost:** Cloud SQL is billed every hour it exists, used or not. When finished:

```sh
deploy/teardown.sh   # deletes the service, database, bucket, secret, images, service account
```

To keep the data, first export it: `gcloud sql export sql sahyog-db gs://<bucket>/backup.sql
--database=sahyog` (or `pg_dump` through the Cloud SQL proxy) and download it before teardown.

