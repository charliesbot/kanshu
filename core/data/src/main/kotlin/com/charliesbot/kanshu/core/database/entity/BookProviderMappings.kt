package com.charliesbot.kanshu.core.database.entity

import com.charliesbot.kanshu.core.provider.ProviderBookContext
import com.charliesbot.kanshu.core.provider.ProviderBookKey
import com.charliesbot.kanshu.core.provider.ProviderInstanceId
import com.charliesbot.kanshu.core.provider.ProviderMetadata
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.readium.r2.shared.publication.Publication

internal fun BookEntity.toProviderBookKey(): ProviderBookKey =
  ProviderBookKey(ProviderInstanceId(providerInstanceId), providerItemId)

internal fun BookEntity.toProviderBookContext(
  file: File,
  publication: Publication,
): ProviderBookContext =
  ProviderBookContext(
    book = toProviderBookKey(),
    file = file,
    publication = publication,
    providerMetadata = providerMetadata.decodeProviderMetadata(),
  )

internal fun ProviderMetadata.encodeProviderMetadata(): String? =
  takeIf { it.isNotEmpty() }?.let(Json::encodeToString)

internal fun String?.decodeProviderMetadata(): ProviderMetadata =
  this?.let { runCatching { Json.decodeFromString<ProviderMetadata>(it) }.getOrNull() }.orEmpty()
