package com.charliesbot.kanshu.core.reader

import java.io.Closeable
import org.readium.r2.shared.publication.Publication

// Thin wrapper around a Readium Publication. Exists so the reader feature can hold the
// publication for navigator construction while the ViewModel owns its lifecycle (close()
// runs on onCleared()).
class BookHandle internal constructor(val publication: Publication) : Closeable {
  override fun close() {
    publication.close()
  }
}
