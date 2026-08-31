package com.charliesbot.kanshu.navigator.model

/**
 * Publisher-declared block alignment. `null` on a block means "no publisher signal" — the reader's
 * justification default applies. See docs/design/publisher-styles-engine.md § Ownership boundary.
 */
enum class BlockAlignment {
  Start,
  Center,
  End,
}
