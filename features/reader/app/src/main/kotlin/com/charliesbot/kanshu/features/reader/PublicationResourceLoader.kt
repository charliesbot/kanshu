package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.navigator.ReaderResourceLoader
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

internal class PublicationResourceLoader(private val publication: Publication) :
  ReaderResourceLoader {
  override suspend fun read(resourceHref: String): ByteArray? {
    val url = Url.fromDecodedPath(resourceHref) ?: return null
    val resource = publication.get(url) ?: return null
    return resource.read().getOrNull()
  }
}
