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

## M3: Navigation shell
- [ ] Move `DesignSystemCatalog` behind a debug-only entry point
- [ ] `core:model` (User, Role, SessionState)
- [ ] `core:datastore`, `core:data` with fake `SessionRepository`
- [ ] Splash (SplashScreen API) resolving start destination
- [ ] Auth / Onboarding / Customer / Worker graphs with type-safe routes
- [ ] Bottom navigation per role; back stack cleared on role/session change

## M4: Authentication
- [ ] Firebase phone OTP
- [ ] Session persistence, logout
- [ ] Account deletion entry point (Play requirement)

## M5: Customer discovery
- [ ] Home, categories, search, worker profile
- [ ] Room cache

## M6: Booking flow
- [ ] Create → track → history, cancellation

## M7: Worker side
- [ ] Onboarding / skills, document upload, availability, job requests, active job, earnings

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

## Open questions
- [ ] Final applicationId (default `com.sahyog.app`)
- [ ] Admin: web dashboard (default) vs in-app
- [ ] Pricing model
- [ ] Launch languages (default English + Hindi)
- [ ] Backend repo/hosting, SIH timeline
