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
- The phone reaches the laptop over USB with `adb reverse tcp:8080 tcp:8080` (set up in M10d).

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
