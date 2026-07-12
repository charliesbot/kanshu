package com.charliesbot.kanshu.navigator

/**
 * Access to publication resources for the reader engine.
 *
 * The engine consumes raw bytes so this module stays free of any EPUB I/O dependency; the feature
 * module implements it over its publication container.
 */
interface ReaderResourceLoader {
  /** Returns the raw bytes of a publication-root-relative resource, or null when unavailable. */
  suspend fun read(resourceHref: String): ByteArray?
}
