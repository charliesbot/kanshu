# Kanshu

Minimal Android ebook reader for eink tablets, backed by a Kavita server. Base package: `com.charliesbot.kanshu`.

Read `docs/PRD.md` before any design, UX, or feature decision — it defines e-ink constraints, animation rules, Phase 0 scope, and the north star.

Read `docs/KAVITA_API.md` before any Kavita networking work — it captures the verified auth flow, the endpoints we use, the ones we deliberately don't, and the footguns we've already paid for.

Read `docs/KINDLE_TYPOGRAPHY.md` before any reader typography work — it captures the layout-mine-fonts-yours model we model after Kindle and the `EpubPreferences` mapping.

Read `docs/READIUM_API.md` before any Readium navigator or streamer work — it captures the verified 3.1.2 surface (the two `Configuration` classes, the settings layering, what `servedAssets` actually does, and the `TransformingContainer` escape hatch) so we stop imagining APIs that don't exist.

## Stack Overrides

The android-dev skill covers architecture, Koin, StateFlow, Spotless, and scaffolding. These are Kanshu-specific deviations from its defaults:

| Concern    | Kanshu choice    | Skill default |
| ---------- | ---------------- | ------------- |
| UI         | compose-unstyled | Material 3    |
| Networking | Ktor             | Retrofit      |

**Navigation 3 transitions must be disabled.** E-ink screens ghost on animations. Use `NavDisplay` with no transition spec — no enter/exit animations, no shared element transitions, no crossfades.

## Module Layout Notes

The core layer follows the android-dev skill's four-module split (`:core:model`, `:core:domain`, `:core:data`, `:core:strings`) with two project-specific deltas:

- **`:core:designsystem` is a lazy-promoted module.** Holds `KanshuTheme`, the compose-unstyled wrappers (`KanshuButton`, `KanshuText`, `KanshuCover`, etc.), and the drawable icons. The trigger from the skill — "you deliberately break out of stock Material" — is permanent here, so the module is permanent too. Features depend on it directly instead of redeclaring tokens.
- **`:features:reader:app` depends on `:core:data` (architectural exception).** `ReaderResult.Success` carries a Readium `Publication`, and Readium 3.x is an AAR whose public surface uses `android.net.Uri`. Hosting the reader contract in `:core:domain` (kotlin-jvm) is impossible, so the reader-specific types (`ReaderSource`, `ReaderResult`, `OpenBookUseCase`, `KavitaReaderSource`) live in `:core:data` and the reader feature is allowed to consume them. The other two features stay strict — `:core:domain` + `:core:designsystem` + `:core:strings` only.

## Build Gate

`./gradlew build` is the canonical green-or-not check. It runs Spotless, lint (debug + `lintVitalRelease`), unit tests, and `assembleRelease`. The `.github/workflows/build.yml` workflow runs it on every push and PR to `main` — a red CI run blocks merging. Reproduce locally with the same one-liner. If lint flags a real false positive, suppress it with `tools:ignore` at the call site (see `MainActivity`'s `Instantiatable` suppression) or a feature-local `lint {}` block; do not disable checks project-wide.
