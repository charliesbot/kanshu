package com.charliesbot.kanshu.navigator.model

/**
 * Publisher-declared block alignment. `null` on a block means "no publisher signal" — the reader's
 * justification default applies. See docs/PRD_PUBLISHER_STYLES.md § Ownership Model.
 */
enum class BlockAlignment {
  Start,
  Center,
  End,
}
