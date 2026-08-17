package com.charliesbot.kanshu.core.provider

import java.io.File

interface Provider {
  val descriptor: ProviderDescriptor

  suspend fun fetchCatalog(): ProviderResult<List<ProviderBook>>

  suspend fun resolveCover(book: ProviderBookKey, revisionToken: String?): ProviderCover?

  suspend fun acquire(
    book: ProviderBookKey,
    target: File,
    onProgress: (downloaded: Long, total: Long?) -> Unit,
  ): ProviderResult<AcquiredBook>
}

data class AcquiredBook(val byteSize: Long)
