# Kanshu

Minimal Android ebook reader for eink tablets, backed by a Kavita server. Base package: `com.charliesbot.kanshu`.

Read `docs/PRD.md` before any design, UX, or feature decision — it defines e-ink constraints, animation rules, Phase 0 scope, and the north star.

Read `docs/KAVITA_API.md` before any Kavita networking work — it captures the verified auth flow, the endpoints we use, the ones we deliberately don't, and the footguns we've already paid for.

Read `docs/KINDLE_TYPOGRAPHY.md` before any reader typography work — it captures the layout-mine-fonts-yours model we model after Kindle and the `EpubPreferences` mapping.

## Stack Overrides

The android-dev skill covers architecture, Koin, StateFlow, Spotless, and scaffolding. These are Kanshu-specific deviations from its defaults:

| Concern    | Kanshu choice    | Skill default |
| ---------- | ---------------- | ------------- |
| UI         | compose-unstyled | Material 3    |
| Networking | Ktor             | Retrofit      |

**Navigation 3 transitions must be disabled.** E-ink screens ghost on animations. Use `NavDisplay` with no transition spec — no enter/exit animations, no shared element transitions, no crossfades.
