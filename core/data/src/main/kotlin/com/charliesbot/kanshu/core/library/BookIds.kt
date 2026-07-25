package com.charliesbot.kanshu.core.library

/**
 * Builds the source-prefixed ids used as the `books.id` primary key.
 *
 * Reading state (progress, annotations) carries this id as a foreign key, so anything that writes
 * reading state has to derive it identically — a mismatch fails the FK constraint rather than
 * silently writing an orphan row. Shared here so the reader and the library repository can't drift.
 */
object BookIds {
  const val SOURCE_KAVITA = "kavita"

  fun forKavitaSeries(seriesId: Int): String = "$SOURCE_KAVITA:$seriesId"

  fun kavitaSeriesId(bookId: String): Int? =
    if (bookId.startsWith("$SOURCE_KAVITA:")) bookId.removePrefix("$SOURCE_KAVITA:").toIntOrNull()
    else null
}
