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

## M7: Worker side
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
### M7b: Jobs
- [ ] Job requests (accept / decline), active job (on the way → start code → complete)
- [ ] Schedule tab, Earnings tab

## M8: Location
- [ ] Permission flow, Maps (key via secrets plugin, restricted), nearby matching

## M9: Reviews and ratings

## M10: Backend integration
- [ ] OpenAPI contract, Retrofit, Firebase ID token → Spring Boot verification

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
