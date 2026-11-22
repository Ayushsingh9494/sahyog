# Sahyog: Architecture

Cooperative Gig Services Platform (SIH26089). Android client: Kotlin, Jetpack Compose, Material 3.

## Layers

```
UI (Compose)  --actions-->  ViewModel  -->  UseCase (only when real logic)  -->  Repository
     ^                          |                                                 |
     +--- StateFlow<UiState> <--+                     Room cache <-- sync --> Retrofit (Spring Boot)
```

- **UI**: stateless composables receive one `UiState` and an `onAction` callback. Screens collect state with `collectAsStateWithLifecycle()`.
- **ViewModel**: owns screen state as `StateFlow`. One-off effects (navigate, snackbar) go through a `Channel`.
- **Domain**: use cases only where logic spans repositories or is non-trivial. No pass-through use cases.
- **Data**: repositories are the single source of truth. Reads are cache-first (Room). Writes that change shared state (bookings, payments) are online-only and server-confirmed.
- **Errors**: data layer maps exceptions to `AppError` once; results are `AppResult<T>` (`core:common`).

## Modules

| Module | Type | Purpose | Added |
|---|---|---|---|
| `build-logic/convention` | Gradle plugins | `sahyog.android.application`, `.android.library`, `.android.compose`, `.jvm.library`, `.hilt` | M1 |
| `app` | Android app | Application, MainActivity, root NavHost, role routing | M0 |
| `core:common` | JVM | `AppError`, `AppResult`, dispatcher injection | M1 |
| `core:designsystem` | Android lib | `SahyogTheme`, tokens, reusable `Sahyog*` components | M1 |
| `core:model` | JVM | Domain models and pure rules (User, UserRole, SessionState, ...) | M3 |
| `core:ui` | Android lib | Shared non-visual UI helpers: error messages, event collection, formatters | M3 |
| `core:datastore` | Android lib | DataStore: theme, active mode; temporary local profiles (until M10) | M3 |
| `core:data` | Android lib | Repository interfaces and implementations (Firebase Auth from M4) | M3 |
| `core:database` | Android lib | Room: offline catalog cache (entities, DAOs, schemas) | M5 |
| `core:network` | Android lib | Retrofit/OkHttp, DTOs, auth interceptor | M10 |
| `core:testing` | Android lib | Shared fakes and test rules | M3 |
| `feature:auth`, `:onboarding`, `:account` | Android lib | Sign-in, first-run profile, account settings | M3 |
| `feature:discovery` | Android lib | Customer Home, Search, Category, Worker profile | M5 |
| `feature:booking` | Android lib | Book a service, Bookings tab, Booking detail | M6 |
| `feature:worker` | Android lib | Worker profile setup, ID verification, Jobs/Schedule/Earnings | M7 |
| `feature:*` | Android lib | One per feature; never depend on each other (`sahyog.android.feature`) | M3+ |

Modules are created when they first get real content, not as empty shells.

**Dependency rules**
- `feature` → `core` only.
- `core:data` → `network`/`database`/`datastore`.
- `app` → everything.

## Navigation and sessions

```
MainActivity ── splash held while MainActivityUiState.Loading
   └─ SahyogApp(session)          one section per SessionState (AnimatedContent)
        ├─ SignedOut           → AuthNavHost: Welcome → Phone → Otp(phone)
        ├─ NeedsOnboarding     → Onboarding
        ├─ SignedIn(Customer)  → CustomerShell [Home · Search · Bookings · Account]
        └─ SignedIn(Worker)    → WorkerShell   [Jobs · Schedule · Earnings · Account]
```

- **State drives sections; nothing navigates across them.** Verifying an OTP, finishing
  onboarding, switching mode and signing out only change data. `SessionState.resolve`
  maps (user, preferences) to a section, and the root swaps the section together with its
  back stack. Back can never return to a signed-out user's previous screens.
- **Inside a section**, Navigation Compose with `@Serializable` routes. Arguments are typed
  constructor properties. ViewModels needing route arguments use Hilt assisted injection.
- **ViewModel scope per section** (`SectionViewModelScope`): cleared when the section leaves,
  kept across configuration changes.
- **Tabs** keep their own state (`saveState`/`restoreState`). The bottom bar is hidden on pushed screens.
- **Screen pattern:** `XxxScreen` (gets the ViewModel and collects state/events) wraps a stateless
  `XxxContent(uiState, callbacks)` used by previews and tests. One `StateFlow<UiState>` per
  ViewModel; one-off events go through a `Channel`, collected by `ObserveAsEvents`.

## Authentication (M4)

- **Identity = Firebase Auth (phone OTP).** `FirebaseAuthRepository` (`core:data/auth`) exposes
  `session: Flow<AuthSession?>` from Firebase's `AuthStateListener`, so the session survives restarts
  and sign-out is just `FirebaseAuth.signOut()`.
- `requestOtp` returns `CodeSent` or `SignedIn` (instant verification / SMS auto-retrieval).
  The verification ID and resend token live in memory only. If the process dies, `verifyOtp`
  reports "code expired" and the user requests a new one.
- Firebase needs an Activity for its reCAPTCHA/Play Integrity fallback: `ForegroundActivityTracker`
  (weak reference, registered in `SahyogApplication`) supplies it, so ViewModels never touch Activities.
- Firebase exceptions are mapped to `AppError` in one place (`FirebaseErrors.kt`).
- **Profiles (name, roles) are not Firebase's job.** Until the backend exists (M10),
  `LocalProfileUserRepository` stores them per uid in a separate DataStore that survives sign-out.
  In M10 it is replaced by an API-backed repository; the client sends the Firebase ID token and the
  server verifies it.
- Setup: `app/google-services.json` is gitignored; download it from the Firebase console. Debug
  SHA-1/SHA-256 must be registered there. Use Firebase test phone numbers during development.

## Service catalog (M5)

```
HomeScreen / SearchScreen / CategoryScreen / WorkerProfileScreen
        │  (Flow from Room, always)
        ▼
OfflineFirstCatalogRepository ── refresh() ──► CatalogNetworkDataSource
        │  replaceCatalog() in one transaction      └ DemoCatalogDataSource (until M10 → Retrofit)
        ▼
Room: categories · workers · worker_categories · worker_services
```

- Screens never wait on the network: they observe Room and show what's cached. Home triggers a
  refresh each time it opens; a failed refresh shows a snackbar if content is cached, or a
  full-screen error with retry if nothing is.
- `refresh()` replaces the whole catalog atomically and is de-duplicated with a mutex. Links to
  categories missing from a response are dropped rather than failing the refresh.
- Category look (icon, translated label) is chosen on the client by stable category id; unknown
  ids fall back to the server's name and a generic icon, so new categories need no app update.
- Money is `Paise` (Long). Formatting with Indian grouping lives in `core:ui` (`formatRupees`).
- The database is a cache: everything in it can be downloaded again. Schema changes bump the
  version and add a migration (exported schemas in `core/database/schemas`).

## Bookings (M6)

- **Writes are server-confirmed.** `createBooking` / `cancelBooking` call the network first and
  cache only the server's answer; the client never invents a booking id, price or status.
- **Reads are cached** (Room `bookings`), refreshed on opening the Bookings tab or a booking, and
  every 15 s while an active booking is on screen (until FCM in M11).
- **Errors:** network sources throw `ApiException(AppError)` for refusals; `apiCall {}` maps every
  exception to `AppError` once. Validation fields live in `BookingErrorFields`.
- **Sign-out wipes personal caches:** each cache implements `UserDataCleaner` and is bound
  `@IntoSet`; `FirebaseAuthRepository.signOut()` runs them all (via `dagger.Lazy`, which breaks the
  auth ↔ booking dependency cycle).
- **Time** comes from an injected `java.time.Clock`, so slot rules are testable.
- **Demo server** (`core:data/demo/DemoServer`): one JSON state file in app storage (survives
  sign-out, like a server DB) shared by the demo catalog, booking and worker sources. Users' names
  and roles stay in `LocalProfileDataSource`. It validates like the real API should, prevents
  double-booking, auto-accepts requests for *sample* workers after 20 s (real workers answer
  themselves), and auto-verifies a worker 30 s after they submit an ID. Replaced by Retrofit in M10.
- **Identity documents** are never stored by the app or the demo: the picked image is read once,
  checked, and discarded. Real storage must be server-side, encrypted and access-controlled (M10).
- **Database migrations:** bump the version, add an `AutoMigration` (or a manual `Migration`),
  and extend `MigrationTest`. Exported schemas live in `core/database/schemas`. The new schema
  JSON is generated by the build itself, so the first `connectedAndroidTest` run after a version
  bump can miss it; run it again.

## Worker jobs (M7)

- A job is the worker's view of a booking (same id and status; customer details instead of worker's).
- Lifecycle: `Requested -(accept)-> Accepted -(start trip)-> WorkerEnRoute -(start job + customer's
  code)-> InProgress -(complete)-> Completed`, or `Requested -(decline)-> Rejected`. The allowed next
  steps live in `core:model` (`allowedJobActions`) and the server enforces the same rules, so a step
  refused because the customer cancelled shows "this job changed" and refreshes.
- The start code proves the worker is actually with the customer; trips and starts are refused more
  than 3 hours before the visit, so jobs can't be completed early.
- Earnings are sums of completed jobs' starting prices until payments exist (M12).

## Conventions

- Base package `com.sahyog`; module namespace `com.sahyog.<area>.<module>`.
- Sources in `src/main/kotlin`.
- All versions live in `gradle/libs.versions.toml`.
- SDK levels live in `build-logic/.../ProjectConfig.kt` (`SahyogSdk`).
- All user-facing text lives in string resources (English + Hindi planned).

## Design system (`core:designsystem`)

| Token | Value |
|---|---|
| Primary | Teal-green `#0B6B58` (dark: `#81D7BF`) |
| Accent (logo/highlights) | Marigold `#F4A340`; text-safe secondary `#8A5100` |
| Font | Plus Jakarta Sans (variable, bundled); Devanagari → system Noto |
| Shapes | 6 / 10 / 14 / 20 / 28 dp corners |
| Spacing | 4dp scale (`SahyogSpacing`), 20dp screen gutter |
| Touch targets | ≥ 48dp; buttons 52dp tall |

Rules:
- Screens use `SahyogTheme.*`, `Sahyog*` components and `SahyogIcons`. No raw colors, dp literals for spacing, or direct material-icons imports.
- Every color change must keep WCAG AA contrast in both themes.
- Dynamic color is off so the brand stays consistent.
- The logo is two interlocking rings (customer + worker, joined by the cooperative). Its geometry is shared by `SahyogLogoMark` (Compose) and `ic_launcher_foreground.xml`, so change both together.
- Generic state views (loading/empty/error) live in the design system. `core:ui` is reserved for domain-aware shared UI (e.g. a worker card bound to a model).

## Toolchain constraints

- **AGP 9.3.3 uses built-in Kotlin** and resolves KGP **2.2.10**.
  - Do not apply `org.jetbrains.kotlin.android`.
  - Kotlin-coupled plugins (`kotlin.plugin.compose`, `kotlin.jvm`, `kotlin.plugin.serialization`) must match `kotlin` in the catalog.
  - Libraries compiled with a much newer Kotlin may fail with metadata errors. Check before bumping.
- KSP 2.3.x is independent of the Kotlin version.
- Java/Kotlin bytecode target is 17. The Gradle daemon runs on JDK 25 (`gradle/gradle-daemon-jvm.properties`).
- **Firebase BOM pinned to 34.19.0.** 35.x ships firebase-auth 25, compiled with Kotlin 2.4, which
  the bundled Kotlin 2.2 compiler cannot read. Bump it together with the AGP/Kotlin upgrade.
- Command-line builds without a standalone JDK 25: point Gradle at Android Studio's JBR with
  `-Dorg.gradle.java.installations.paths="<Android Studio>/jbr" -Dorg.gradle.java.installations.auto-download=false`.
- Networks with TLS inspection (e.g. campus Wi-Fi) break dependency downloads (PKIX errors).
  Use another network or install the inspection CA in the JDK trust store.

## Decisions (defaults pending product confirmation)

| Decision | Choice | Status |
|---|---|---|
| applicationId | `com.sahyog.app` | Default, must be final before first Play upload |
| minSdk / targetSdk | 26 / 37 | Decided |
| Admin | Web dashboard (not in the Android app) | Default |
| Roles | One account can hold Customer and Worker roles; active mode switch | Default |
| Role source of truth | Backend (never client) | Decided |
| Login | Firebase phone OTP | Default |
| Navigation | Session-driven sections + Navigation Compose type-safe routes within | Decided |
| Modularization | Multi-module + convention plugins | Decided |
| Geo matching | Server-side PostGIS | Decided |
| Payments | Server-verified via gateway webhook; gateway TBD | Open |
| Pricing model | TBD (fixed / worker-set / quote) | Open |
| Languages | English + Hindi at launch | Default |

## Booking state machine (server-enforced)

```
REQUESTED -> ACCEPTED -> WORKER_EN_ROUTE -> IN_PROGRESS (start OTP) -> COMPLETED -> PAID -> REVIEWED
    |-> REJECTED / EXPIRED                 any pre-completion state -> CANCELLED (rules apply)
```
