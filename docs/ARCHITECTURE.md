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
| `core:model` | JVM | Domain models (User, Role, Booking, ...) | M3 |
| `core:ui` | Android lib | Domain-aware shared UI (model-bound cards, formatters) | M5 |
| `core:datastore` | Android lib | Preferences (theme, language, onboarding) | M3 |
| `core:data` | Android lib | Repository interfaces, implementations, fakes | M3 |
| `core:database` | Android lib | Room | M5 |
| `core:network` | Android lib | Retrofit/OkHttp, DTOs, auth interceptor | M10 |
| `core:testing` | Android lib | Shared fakes and test rules | M3 |
| `feature:*` | Android lib | One per feature; never depend on each other | M3+ |

Modules are created when they first get real content, not as empty shells.

**Dependency rules**
- `feature` → `core` only.
- `core:data` → `network`/`database`/`datastore`.
- `app` → everything.

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

## Decisions (defaults pending product confirmation)

| Decision | Choice | Status |
|---|---|---|
| applicationId | `com.sahyog.app` | Default, must be final before first Play upload |
| minSdk / targetSdk | 26 / 37 | Decided |
| Admin | Web dashboard (not in the Android app) | Default |
| Roles | One account can hold Customer and Worker roles; active mode switch | Default |
| Role source of truth | Backend (never client) | Decided |
| Login | Firebase phone OTP | Default |
| Navigation | Navigation Compose, type-safe `@Serializable` routes | Decided |
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
