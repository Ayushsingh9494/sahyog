# Sahyog: Implementation Checklist

Each milestone ends with `./gradlew assembleDebug test` passing.

## M0: Repo hygiene ✅
- [x] Dedicated git repo in `SAHYOG/` (previously the whole home folder was the repo root)
- [x] `.gitignore` covers build output, IDE files, `local.properties`, keystores, secrets
- [x] applicationId / namespace `com.example.sahyog` → `com.sahyog.app`
- [x] minSdk 24 → 26, Java/Kotlin target 11 → 17
- [x] Template `com.example` sources, example tests, template colors removed
- [x] `docs/ARCHITECTURE.md`, `docs/CHECKLIST.md`

## M1: Build foundation ✅
- [x] `build-logic` convention plugins (application, library, compose, jvm, hilt)
- [x] Hilt + KSP wired; `SahyogApplication` (@HiltAndroidApp), `MainActivity` (@AndroidEntryPoint)
- [x] `core:common`: `AppError`, `AppResult` (+ unit tests), injectable dispatchers
- [x] `core:designsystem`: theme moved from `:app`, renamed `SahyogTheme`, dynamic color off
- [x] Gradle parallel + build cache enabled
- [x] Verified: `assembleDebug` OK, `core:common:test` 3/3 passing, Hilt graph includes cross-module bindings

## M2: Design system ✅
- [x] Brand palette: teal-green primary, marigold accent, green-tinted neutrals (light + dark)
- [x] Contrast: 42/42 text/background pairs pass WCAG AA across both themes
- [x] Extended semantic colors: success, warning, rating, card (`SahyogTheme.extendedColors`)
- [x] Plus Jakarta Sans variable font (OFL, `docs/licenses/`); Devanagari falls back to system Noto
- [x] Type scale, shapes, spacing / elevation / size tokens
- [x] Components: Button (filled/outlined/text, loading), TextField (prefix, error), Card, FilterChip,
      TopAppBar, NavigationBar, Loading/Empty/Error views, RatingBar (fractional, RTL), RatingSummary,
      Avatar (initials), StatusBadge (6 tones), Logo + LogoMark
- [x] `SahyogIcons`: single icon entry point (material-icons-core, rounded)
- [x] Launcher icon: adaptive + monochrome (themed icons); legacy bitmap mipmaps removed (minSdk 26)
- [x] Window background matches theme (no white flash in dark mode)
- [x] English + Hindi strings for design-system text; plural-aware review count
- [x] Catalog screen with live light/dark toggle (temporary app content)
- [x] Verified: build OK, 8/8 unit tests, lint 0 errors
- [x] On-device visual review: Motorola Edge 50 Fusion, Android 16 (API 36), light + dark, launcher icon
- [x] Fixed on device: status-bar icons now follow the app theme, not the system theme
- [ ] Native-speaker review of Hindi strings

## M3: Navigation shell ✅
- [x] `core:model`: User, UserRole, WorkerVerificationStatus, UserPreferences, `SessionState.resolve` (+ tests)
- [x] `core:common`: Phone / OTP / Name validators (+ tests; strips pasted +91 / 0 prefixes)
- [x] `core:datastore`: Preferences DataStore (theme, active mode, cached profile; corrupt file → defaults)
- [x] `core:data`: Auth / User / UserPreferences repositories; fakes bound in `DataModule` (demo OTP 123456)
- [x] `core:ui`: AppError → message mapping (EN/HI), `ObserveAsEvents`, shared phone formatting
- [x] `core:testing`: MainDispatcherRule, controllable test repositories
- [x] `sahyog.android.feature` convention plugin
- [x] `feature:auth`: Welcome → Phone → OTP (auto-verify, 30 s resend, SMS-code autofill hint)
- [x] `feature:onboarding`: name + role selection (one or both)
- [x] `feature:account`: profile, mode switch, theme (persisted), sign-out confirmation, debug catalog link
- [x] Splash (core-splashscreen) held until the session is loaded
- [x] Session-driven root: sections swap on state change; per-section ViewModel scope
- [x] Customer / Worker shells with bottom nav (state-saving tabs); placeholder tabs; worker verification banner
- [x] Design catalog moved behind a debug-only entry (Account → Developer)
- [x] Design system: `SahyogOtpField`, `SahyogSelectableCard`, `SahyogSegmentedControl`
- [x] Per-app language (Android 13+): English / Hindi via `locales_config`
- [x] Verified: 43 unit tests, lint 0 errors on all modules, debug + release builds
- [x] Verified on device: full sign-up flow, mode switch, theme persistence across restart,
      sign-out + Back exits app (no return to signed-in screens), Hindi UI

## M4: Authentication ✅
- [x] Firebase project `sahyog-f6bad`; `google-services.json` kept out of git (each dev downloads it)
- [x] `FirebaseAuthRepository` replaces the fake: phone OTP (+91), resend with force-resend token,
      instant verification / auto-retrieval, 75 s safety timeout, Firebase errors → `AppError`
- [x] Session = Firebase auth state (`AuthStateListener` flow); persists across restarts
- [x] `LocalProfileDataSource`: TEMPORARY per-uid profile store standing in for the backend
      (survives sign-out, so re-signing in restores the profile). Removed in M10
- [x] Sign-out clears Firebase session + active mode
- [x] New errors: rate limited, expired code (EN/HI)
- [x] Verified: 53 unit tests, lint 0 errors on all modules
- [x] Verified on device with Firebase test numbers: wrong code rejected, correct code → onboarding,
      session survives app restart, sign-out → sign-in restores profile, second number as worker
      (worker shell + verification banner). Play Integrity passed
- [ ] Real-SMS test (needs Blaze plan; Firebase test numbers send no SMS)
- [ ] Add release SHA-1/SHA-256 to Firebase when Play App Signing is set up (M14)
- [ ] Account deletion entry point (Play requirement): moved to M10, it needs the backend

## M5: Customer discovery ✅
- [x] `core:model`: ServiceCategory, Worker, WorkerService, `Paise` (money as integer paise), PriceUnit
- [x] `core:database` (Room 2.8.5): categories, workers, worker↔category, services; schema exported
      to `core/database/schemas`; atomic catalog replace; LIKE search with escaping
- [x] `core:data`: `CatalogRepository` (offline-first: screens read Room, `refresh()` downloads),
      `CatalogNetworkDataSource` + TEMPORARY `DemoCatalogDataSource` (18 fictional workers, 8 categories)
- [x] Search covers worker name, area, category, service and Hindi keywords (e.g. "बिजली", "नल")
- [x] `core:ui`: category icons (Material Symbols, Apache 2.0) + EN/HI labels, Indian rupee
      formatting (₹1,23,456), shared `WorkerCard`
- [x] `feature:discovery`: Home (greeting, search entry, category grid, top-rated, pull-to-refresh,
      loading / error / empty states), Category ("Available now" filter), Search (debounced, survives
      process death), Worker profile (stats, services and prices, details)
- [x] Verified: 66 unit tests, 4 Room DAO tests on device, lint 0 errors on all modules
- [x] Verified on device: Home → category → filter → profile, search results and no-results,
      Hindi UI, dark theme
- [ ] Server-side localisation of service names / about text (needs backend, M10)
- [ ] Distance and "near you" sorting (needs location, M8)
- [ ] Worker photos (needs image upload, M7 + an image loader)

## M6: Booking flow ✅
- [x] `core:model`: Booking, BookingStatus (state machine helpers), TimeSlot (2-hour windows 9 AM–9 PM,
      7-day window, 60-min lead time), BookingRequest limits
- [x] `core:database` v2: `bookings` table via AutoMigration 1→2 (MigrationTest on device)
- [x] `core:data`: `BookingRepository` (reads cached, writes server-confirmed), `apiCall`/`ApiException`
      error mapping, TEMPORARY `DemoBookingDataSource` (per-user JSON store, validation, double-booking
      check, auto-accept after 20 s with a 4-digit start code, expiry)
- [x] Sign-out clears cached personal data via `UserDataCleaner` multibinding
- [x] Injectable `java.time.Clock` for time-dependent logic
- [x] `feature:booking`: Book a service (service, day, time, address pre-filled from last booking, note),
      Bookings tab (Upcoming / Past, pull-to-refresh, empty states), Booking detail (status, start code,
      progress timeline, details, cancel with confirmation, 15 s refresh while visible)
- [x] "Book now" on the worker profile; booking form replaced by the new booking on success
- [x] Verified: 120 unit tests, 8 device tests (DAO + migration), lint 0 errors
- [x] Verified on device: book → auto-accept + start code → cancel → Past; second account sees no
      bookings; first account's bookings return after signing back in
- [ ] Worker-side status changes (on the way, start with code, complete): M7
- [ ] Push notifications instead of polling: M11
- [ ] Payment after completion: M12

## M7: Worker side ✅
### M7a: Worker profile and verification ✅
- [x] `DemoServer`: one shared pretend backend (single JSON state) behind the demo catalog, booking
      and worker sources; time-based behaviour (sample workers auto-accept, real workers don't;
      auto-verify 30 s after an ID is submitted; unaccepted requests expire)
- [x] `core:model`: WorkerProfile, WorkerProfileInput (validation shared by form and server), IdDocumentType
- [x] `WorkerProfileRepository` (+ `UserDataCleaner`), `WorkerNetworkDataSource`, `DemoWorkerDataSource`
- [x] ID photo via system Photo Picker (no storage permission); validated (image, ≤ 10 MB) then
      discarded — the demo never stores identity documents
- [x] Verified real workers appear in the customer catalog; unreviewed workers show "New", not 0.0
- [x] Workers can't book themselves
- [x] `feature:worker`: Jobs tab (setup / verification states, auto-refresh while pending, "Accepting
      jobs" switch), profile form (categories, services and prices, area, experience, languages,
      about), ID verification
- [x] Verified: 136 unit tests, lint 0 errors
- [x] Verified on device: set up profile → ID via Photo Picker → pending → verified (auto) →
      found in customer search with "New" badge
### M7b: Jobs ✅
- [x] `core:model`: Job, JobAction + `allowedJobActions()` (lifecycle rules), EarningsSummary,
      `JOB_START_WINDOW_HOURS` (can't set off or start more than 3 h before the visit)
- [x] Demo server job API: accept (issues start code), decline, start trip, start job (code must
      match), complete (records completion time); workers only see their own jobs
- [x] `core:database` v3: `jobs` table via AutoMigration 2→3; migration tests 1→3 and 2→3
- [x] `JobRepository` (server-confirmed writes, cached reads, cleared on sign-out)
- [x] Jobs tab: new requests with inline Accept, in progress, coming up; auto-refresh while visible
- [x] Job detail: one primary action per step, decline / complete confirmations, start-code dialog
      (wrong code stays in the dialog), status-changed and too-early messages
- [x] Schedule tab (by day) and Earnings tab (week / month / all time, completed jobs)
- [x] All placeholder tabs removed
- [x] Verified: 149 unit tests, 9 device tests (DAO + migrations), lint 0 errors
- [x] Verified on device with two accounts: customer books worker → worker accepts → on the way →
      wrong code refused → right code → complete → earnings ₹300 → customer sees full timeline;
      starting a next-day job early is refused
- [ ] Worker availability calendar (fixed 9 AM–9 PM slots for now)
- [ ] Rejected-verification flow needs the admin side (cooperative dashboard)

## M8: Location
### M8a: Location and distance ✅
- [x] `core:model` GeoPoint: haversine distance, `coarsened()` (~1 km) for anything stored or sent
- [x] `core:location`: one-shot fused location (balanced power, coarse is enough), never background;
      location-off detection with an Android 8 fallback
- [x] `LocationRepository`: last location kept coarsened, on device only, cleared on sign-out
- [x] Permission flow: our explanation first, system dialog on tap; handles refused, refused for good
      (→ app settings), location off, no fix; the app works fully without location
- [x] Home "Near you" (nearest within 25 km); distances on all worker cards, search and profile
- [x] Category: "Nearest first" and radius filter (2 / 5 / 10 / 25 km), shown only with a location;
      empty state explains "no one within X km"
- [x] Workers can set an optional work location (stored coarsened); sample workers placed around Jalandhar
- [x] Room v4 (worker coordinates) via AutoMigration
- [x] Verified: 160 unit tests, 9 device tests, lint 0 errors (caught an API 28-only call)
- [x] Verified on device: refuse → explanation; allow approximate → "Near you" with distances;
      nearest-first and radius filters
### M8b: Map address picker ✅
- [x] Maps key from `local.properties` / CI env var into the manifest (never committed); key restricted
      to `com.sahyog.app` + debug SHA-1 and to Maps SDK for Android only; no key → picker hidden
- [x] maps-compose 7.0.0 (newest built with Kotlin 2.2; 8.x+ waits for the toolchain upgrade)
- [x] Pin-drop picker: fixed centre pin, "My location", address under the pin via Android's built-in
      Geocoder (free, no key); confirm fills the address (a typed address is kept)
- [x] Pin stored with the booking at ~1 m precision (server validates), shared only with the assigned
      worker; worker gets "Directions" (Google Maps universal link, no key)
- [x] Room v6 (pin columns on bookings and jobs) via AutoMigration
- [x] Verified: 183 unit tests, 10 device tests (incl. v6 migration), lint 0 errors
- [x] Verified on device: map loads with the restricted key, jumps to current location, address
      updates while dragging, confirm fills the form, booking shows "Map pin", worker's Directions
      opens Google Maps navigation to the pin
- [ ] Release: add the release signing SHA-1 to the Maps key restriction (M14)
- [ ] Dark map style to match the dark theme

## M9: Reviews and ratings ✅
- [x] `core:model` BookingReview (on the booking/job), WorkerReview (public), ReviewInput rules
      (1–5 stars, comment ≤ 500), `Booking.canReview` (completed, not yet rated, within 14 days)
- [x] Server rules (demo server): only the booking's customer, only once, only within the window;
      worker rating = average of reviews (sample workers fold new ratings into their history)
- [x] Privacy: reviewers are shown by first name only; reviews can't be edited after submitting
- [x] Customer: "Rate" badge in Past bookings, rating card on the completed booking
      (stars → optional comment → submit), "Your rating" afterwards
- [x] Worker profile: recent reviews (3 shown, "Show all"), rating refreshed on open
- [x] Worker: "Your rating from customers" on the Jobs tab, customer's rating on the job
- [x] Design system: `SahyogRatingInput` (48dp stars, announced as "3 stars" by TalkBack)
- [x] Room v5 (review columns, `worker_reviews` cache) via AutoMigration
- [x] Verified: 174 unit tests, 10 device tests (incl. v5 migration and ReviewDao), lint 0 errors
- [x] Verified on device: Asha rates Ravi's completed job 4★ with a comment → Ravi's profile goes from
      "New" to 4.0 (1) with the review → Ravi sees the rating on his Jobs tab and on the job
- [ ] Reporting abusive reviews / cooperative moderation (needs the admin side)
- [ ] Accepted jobs whose visit time passed without starting stay "Coming up" forever: needs a
      server rule (e.g. mark missed, notify the cooperative) — found during M9 testing

## M10: Backend integration
Decided: server code in `backend/` (this repo), PostGIS in Docker, laptop-only for now (phone over
USB with `adb reverse`). Production hosting is chosen before real users (recommended: Cloud Run +
Cloud SQL, Mumbai).

### M10a: Server foundation ✅
- [x] `backend/`: Spring Boot 4.1.3, Kotlin 2.3.21, Java 25 (Android Studio's JBR); separate Gradle build
- [x] PostGIS 3.5 (PostgreSQL 17) via Docker Compose, started by `bootRun`; port 5433 (5432 is taken locally)
- [x] Flyway migrations; V1 `users` (roles and worker status are server-owned; admin flag only via SQL)
- [x] Firebase ID token verification as a JWT resource server (Google's public keys; issuer + audience
      = project id). No service-account secret
- [x] One error shape `{code, field}` + HTTP status, matching the app's `AppError` / `*ErrorFields`
- [x] `GET/PUT /api/v1/me` (onboarding; same name rule as the app; roles can be added, not dropped)
- [x] Verified: 5 integration tests on a real PostGIS (Testcontainers); manual run: health UP, missing
      and forged tokens → 401, migration applied
### M10b: Catalog, worker profiles, verification ✅
- [x] V2: categories (with Hindi keywords), worker profiles with a PostGIS `geography` point (GiST index),
      categories, services, and an `id_documents` audit trail (who decided, when, why)
- [x] Worker API: profile (same rules as the app's form + known categories/languages; location rounded
      to ~1 km server-side), availability, ID upload (checked by file content, ≤ 10 MB, private storage)
- [x] Admin API: pending queue, view document (`no-store`), approve / reject with reason; admins only
      via SQL; nobody approves themselves; deciding twice → 409. Replaces the demo's auto-verify
- [x] Catalog API (verified workers only) and PostGIS near-me (radius, category, nearest first)
- [x] Dev profile loads the 18 fictional sample workers (never in production)
- [x] Verified: 11 integration tests on real PostGIS; manual dev run: migrations + 18 workers loaded,
      PostGIS nearest query works
- [ ] Admin screen (web dashboard vs in-app is still an open question); the API is ready for either
- [ ] Production: Cloud Storage bucket for documents (private, encrypted) instead of a local folder
### M10c: Bookings, jobs, reviews ✅
- [x] V3 `bookings` (snapshots of names/service/price, map pin, start code, review columns); a partial
      unique index makes double booking impossible even under concurrent requests
- [x] Customer API: list, book (server price, slot rules in India time, pin rules), cancel, review
- [x] Worker API: jobs list and lifecycle steps under a row lock; 3-hour start window; start code
- [x] Fixed a demo leak: the worker's job list never includes the start code (the demo sent it)
- [x] Expiry of unanswered requests (every minute + on read); public reviews by first name;
      catalog ratings and completed-job counts computed from bookings
- [x] Dev only: fictional past reviews for the sample workers, and sample workers auto-accept
- [x] Tests use a movable clock and a temporary uploads folder
- [x] Verified: 20 integration tests on real PostGIS; manual dev run: V3 applied, sample ratings
      (e.g. 4.7 from 3), dev auto-accept issued a start code
### M10d: App switch-over ✅
- [x] Retrofit 3.0.0 + OkHttp 5.5.1 (Kotlin ≤ 2.2 builds); Firebase ID token interceptor, one retry
      with a fresh token on 401; server `{code, field}` → `AppError`
- [x] Base URL from `SAHYOG_API_URL` (default laptop via `adb reverse`); cleartext only to loopback
- [x] Demo server and demo data sources removed; Retrofit data sources for catalog, bookings,
      worker profile / ID upload / jobs, reviews; profile from `/me` with an on-device copy
- [x] New session states: loading profile (first sign-in on a device) and "couldn't load" (retry /
      sign out); worker approval updates the account badge
- [x] Verified: 165 app unit tests (incl. MockWebServer client tests and ApiUserRepository), lint 0
      errors, 20 server tests
- [x] Verified on device against the laptop server: real Firebase tokens accepted; onboarding saved
      to PostgreSQL; worker profile + ID upload (test image) → Pending → approved → "Verified" in the
      app by itself; catalog with server ratings and reviews; booking with map pin; sample worker
      auto-accept with start code; worker accept, no code on the worker side, "too early" refused;
      app opens from cache when the server is down; offline first sign-in → "Couldn't load your
      profile" → reconnect → Try again works
- [x] Fixed during testing: the "couldn't load" screen crashed (its ViewModel needs a NavHost entry)
- [ ] A full complete-and-review run on the device (only possible within 3 hours of a visit; the
      server tests cover it)
- [ ] OpenAPI contract published by the server (springdoc) for future clients

### M10e: Admin console (in-app) ✅
- [x] Decided: in-app, a third mode "Admin" (Account → Mode) for cooperative staff only
- [x] Server: admins from `ADMIN_PHONE_NUMBERS` (server config, never the API); `/me` reports `isAdmin`;
      V4 audit trail `worker_status_changes` (who, when, why) for submissions, approvals, rejections, revocations
- [x] Server: overview figures, workers directory (status filter + search), worker detail with history,
      revoke verification with reason; 25 server tests
- [x] App: `feature:admin` with Overview (needs-attention panel, next in line, figures, recent decisions),
      Verify queue (oldest first, overdue in red, re-submissions marked), Workers directory (search +
      filters), Worker review (profile, ID photo with full-screen zoom, approve checklist, services,
      history; Approve / Reject with reason chips / Revoke)
- [x] Privacy: admin data is never cached on the phone; the ID photo is decoded in memory only;
      screenshots and Recents are blocked (FLAG_SECURE) on the review screen and the photo viewer
- [x] Verified: 172 app unit tests (incl. admin ViewModels), lint 0 errors; on device: Admin mode via server
      config, dashboard, queue, approve (confirm dialog), reject with reason, directory search, revoke,
      audit rows in PostgreSQL; screenshots of the review screen come out black as intended
- [x] Fixed during testing: the decision confirmation never showed (cleared before display)
- [ ] Remove the test admin number before real launch; real staff numbers go in the cloud config

### Cloud test deployment (temporary, started 2026-11-26)
- [x] Mumbai: Cloud Run `sahyog-api` (scale to zero) + Cloud SQL `sahyog-db` (PostgreSQL 17, db-f1-micro,
      no backups) + private bucket for ID photos + DB password in Secret Manager + least-privilege
      service account; ₹500 budget alert. Scripts: `backend/deploy/deploy.sh`, `teardown.sh`
- [x] Verified over the internet (no laptop): HTTPS health, 401 without a token, prod profile, 4 migrations
      incl. PostGIS; app onboarding, catalog from Cloud SQL, worker profile, ID upload into the bucket;
      the photo URL is not public (HTTP 403)
- [ ] Add the cooperative admin's number (cloud config only), then approve a worker end to end
- [ ] Tear down with `deploy/teardown.sh` when the test run ends (Cloud SQL bills hourly)

## M11: Notifications (FCM)

## M12: Payments (server-verified)

## M13: Hardening
- [ ] Enable R8 (`optimization { enable = true }`) + keep rules
- [ ] Review backup rules (exclude session data)
- [ ] Security review, baseline profiles, UI tests, accessibility pass

## M14: Release
- [ ] Play App Signing, Data Safety form, privacy policy, store listing, internal → closed testing
- [ ] 512px Play Store icon + feature graphic (from the Sahyog mark)
- [ ] Open-source licenses screen (includes Plus Jakarta Sans OFL)

## Maintenance
- [ ] Toolchain upgrade as a dedicated step: AGP 9.3.3 → 9.5.x (changes bundled Kotlin), Compose BOM, coroutines
- [ ] Firebase BOM is pinned to 34.19.0: 35.x (firebase-auth 25) needs Kotlin 2.4 metadata. Bump together with the Kotlin/AGP upgrade

## Open questions
- [ ] Final applicationId (default `com.sahyog.app`)
- [ ] Admin: web dashboard (default) vs in-app
- [ ] Pricing model
- [ ] Launch languages (default English + Hindi)
- [ ] Backend repo/hosting, SIH timeline
