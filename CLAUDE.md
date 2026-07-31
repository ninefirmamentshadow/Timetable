# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A personal transit planning utility for a rider with a fixed evening deadline (a curfew). Android app, package `com.clocktools.timetable`, launcher label "Clock Tools". Single user, sideloaded, no backend. The entire app is one calculation — given a zone's last return bus time and a few configured deductions, find the latest a trip can start and still get back in time — surrounded by just enough UI to enter and audit that math.

## Build / test / CI

There is no local build workflow — everything ships through GitHub Actions (`.github/workflows/android-ci.yml`): push → unit tests → debug APK → signed release APK, all uploaded as workflow artifacts. Release signing reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` from the environment (set from repo secrets `KEYSTORE_BASE64` + the other three in CI); without them the release build still succeeds but is unsigned.

If you need to run things yourself in an environment with a full Android SDK:

```
./gradlew testDebugUnitTest              # all unit tests
./gradlew testDebugUnitTest --tests "com.clocktools.timetable.calc.CurfewCalculatorTest"   # one class
./gradlew assembleDebug
./gradlew assembleRelease                # unsigned unless KEYSTORE_PATH etc. are set
```

Unit tests live under `app/src/test/` and are plain JUnit (no Robolectric) — the calc package is pure Kotlin with no Android dependency, so it runs on the JVM directly. `app/src/androidTest/` doesn't exist yet; nothing currently requires an emulator.

## Architecture

**The math lives in `calc/`, independent of everything else.** `CurfewCalculator.lastSafeBookingStart()` is a pure function (no Room, no Android types beyond `java.time.LocalTime`) that takes a return time and four minute-counts and returns a `CalculationResult` (`Safe` or `NoSafeStart`), always carrying the `Breakdown` that produced it so the UI can print the line-by-line math rather than just the answer. It deliberately does integer minute-of-day arithmetic instead of `LocalTime.minusMinutes()`, because that method wraps past midnight — which would turn "the appointment is too long for the window" into a bogus late-night time instead of a detectable negative number. Read the comment on `CurfewCalculator` before changing it; it documents why `curfewTime` itself never appears in the formula (the return-leg travel time isn't modeled, so `zone.lastReturnTime` is trusted as already curfew-checked by the user).

`ZoneTimeValidation` is the companion boundary check: it rejects zone times in the 00:00–03:59 range on entry, because the minute-of-day math above assumes every relevant time falls within one day.

**Everything else is Room + one Activity + three fragments.** `Zone` (one row per place with its own bus schedule) and `AppConfig` (single-row app-wide settings, `id` always 0) are the only two entities — see `data/`. `TimetableDatabase.MIGRATIONS` is empty scaffolding; schema changes must ship as additive `Migration` objects appended there, never as `fallbackToDestructiveMigration()`. `TimetableApplication` seeds a default `AppConfig` row on first launch and owns the two repositories (`ZoneRepository`, `ConfigRepository`) as lazily-built singletons — fragments reach them via `(requireActivity().application as TimetableApplication)`.

Navigation is a single `NavHostFragment` + `BottomNavigationView` in `MainActivity`, graph in `res/navigation/nav_graph.xml`. The three bottom-nav destinations (Calculator, Zones, Settings) are top-level; `ZoneEditFragment` is a fourth, non-nav-bar destination pushed from Zones for both add and edit, distinguished by a `zoneId` long arg (`-1L` = new). There's no Safe Args plugin in play — nav args are passed as a plain `Bundle`/`bundleOf` and read back with `requireArguments().getLong(...)`. ViewModels are wired with the `viewModelFactory { initializer { ... } }` DSL (`androidx.lifecycle.viewmodel`) rather than hand-written `Factory` classes, and each fragment reads repositories off `TimetableApplication` inside that initializer block.

Each screen's ViewModel exposes one `StateFlow` combining its Room `Flow`s into a sealed `UiState` (see `CalculatorViewModel.uiState` combining zones + config + current selection into `Loading`/`NoZones`/`Ready`), collected in the fragment via `repeatOnLifecycle(STARTED)`. All UI uses ViewBinding, no synthetic accessors, no Compose.

## Conventions specific to this codebase

- **No permissions, ever.** No `INTERNET`, no location, no notification/alarm permissions in the manifest. This isn't a placeholder to fill in later — schedule data is entered by hand from the printed/online transit schedule, and there is no reminder/alarm feature to justify one. If a change seems to need a permission, that's a sign it's out of scope, not a TODO.
- **The word "session" does not appear anywhere in this repo** — code, comments, strings, or README — and shouldn't. The UI and code both call an appointment's duration `appointmentMinutes`/"appointment length". Don't reintroduce "session" as a variable name, string, or comment even in a generic sense.
- **Dark theme only** (`Theme.ClockTools` in `themes.xml`), not a `DayNight` theme with a light variant — this isn't an oversight, don't add one.
- The launcher icon is adaptive-only (`mipmap-anydpi-v26`, vector background/foreground) with no legacy raster mipmaps, which works because `minSdk = 26` guarantees adaptive icon support on every supported device.
