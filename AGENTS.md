# Kanshu

Minimal Android ebook reader for eink tablets, backed by a Kavita server. Base package: `com.charliesbot.kanshu`.

## Project documentation

- `docs/PRD.md`, when present, defines the project direction and north star.
- `docs/ARCHITECTURE.md`, when present, describes the current system.
- `docs/design/`, when present, contains focused design documents.
- `docs/research/`, when present, contains external API, platform, and technical research.
- `docs/archive/`, when present, contains non-current documents and is historical context only.

Other project documents can live directly under `docs/`. Use lowercase kebab-case
filenames except for the fixed `PRD.md` and `ARCHITECTURE.md` names.

## Stack Overrides

The android-dev skill covers architecture, Koin, StateFlow, Spotless, and scaffolding. These are Kanshu-specific deviations from its defaults:

| Concern    | Kanshu choice    | Skill default |
| ---------- | ---------------- | ------------- |
| UI         | compose-unstyled | Material 3    |
| Networking | Ktor             | Retrofit      |

**Navigation 3 transitions must be disabled.** E-ink screens ghost on animations. Use `NavDisplay` with no transition spec — no enter/exit animations, no shared element transitions, no crossfades.

## E-ink Interaction Rules

E-ink ghosts on animations and is unforgiving to small touch targets.

- **Don't import `MaterialTheme` or `androidx.compose.material.ripple`.** `KanshuTheme` sets `LocalIndication = NoIndication` so `Modifier.clickable` produces no ripple anywhere in the app. Wrapping a subtree in `MaterialTheme` re-installs ripples and breaks this. If a component needs feedback, use a visible state change (border, color), not an animated indication.
- **Touch targets ≥ 48dp.** Material's 44dp minimum isn't enough — refresh latency makes users re-tap. Default to 48dp for any tappable surface.

The Nav3 transitions ban (above) is the screen-level form of the same rule.

## Module Layout Notes

The core layer follows the android-dev skill's four-module split (`:core:model`, `:core:domain`, `:core:data`, `:core:strings`) with project-specific deltas:

- **`:reader-navigator` is a top-level Android library module.** Owns the native text rendering engine: XHTML parser, block model, `StaticLayout` layout engine, Canvas page renderer, and selection/hit-testing. Exposes `ReaderPageViewer` as its public composable. Depends only on `:core:model` for preference types. See `docs/design/native-reader.md` for full architecture.

- **`:core:designsystem` is a lazy-promoted module.** Holds `KanshuTheme`, the compose-unstyled wrappers (`KanshuButton`, `KanshuText`, `KanshuCover`, etc.), and the drawable icons. The trigger from the skill — "you deliberately break out of stock Material" — is permanent here, so the module is permanent too. Features depend on it directly instead of redeclaring tokens.
- **`:features:reader:app` depends on `:core:data` (architectural exception).** `ReaderResult.Success` carries a Readium `Publication`, and Readium 3.x is an AAR whose public surface uses `android.net.Uri`. Hosting the reader contract in `:core:domain` (kotlin-jvm) is impossible, so the reader-specific types (`ReaderSource`, `ReaderResult`, `OpenBookUseCase`, `KavitaReaderSource`) live in `:core:data` and the reader feature is allowed to consume them. The other two features stay strict — `:core:domain` + `:core:designsystem` + `:core:strings` only.
- **Strings in `:core:strings` go in both `values/` and `values-es/`.** Spanish is a shipped locale; missing translations fall back silently and look like a bug in production.

## Build Gate

`./gradlew build` is the canonical green-or-not check. It runs Spotless, lint (debug + `lintVitalRelease`), unit tests, and `assembleRelease`. The `.github/workflows/build.yml` workflow runs it on every push and PR to `main` — a red CI run blocks merging. Reproduce locally with the same one-liner. If lint flags a real false positive, suppress it with `tools:ignore` at the call site (see `MainActivity`'s `Instantiatable` suppression) or a feature-local `lint {}` block; do not disable checks project-wide.

## Agent skills

### Issue tracker

Issues and specs are tracked in GitHub Issues. See `docs/agents/issue-tracker.md`.

### Domain docs

This repository uses a single-context domain-doc layout. See `docs/agents/domain.md`.
