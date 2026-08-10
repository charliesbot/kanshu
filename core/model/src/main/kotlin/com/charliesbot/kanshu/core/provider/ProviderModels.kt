package com.charliesbot.kanshu.core.provider

@JvmInline value class ProviderInstanceId(val value: String)

@JvmInline value class BookId(val value: String)

data class ProviderBookKey(
  val providerId: ProviderInstanceId,
  val providerItemId: String,
)

data class ProviderBook(
  val key: ProviderBookKey,
  val title: String,
  val cover: ProviderCover?,
  val mediaType: String,
  val revisionToken: String?,
)

sealed interface ProviderCover {
  data object Available : ProviderCover

  data class RemoteUrl(val value: String) : ProviderCover
}

enum class ProviderType {
  KAVITA,
  LOCAL,
  BOOK_ORBIT,
}

data class ProviderCapabilities(
  val progressSync: Boolean,
  val highlightSync: Boolean,
)

data class ProviderDescriptor(
  val id: ProviderInstanceId,
  val type: ProviderType,
  val displayName: String,
  val enabled: Boolean,
  val capabilities: ProviderCapabilities,
)

sealed interface ProviderResult<out T> {
  data class Success<T>(val value: T) : ProviderResult<T>

  data class Failure(val error: ProviderError) : ProviderResult<Nothing>
}

sealed interface ProviderError {
  data object NoCredentials : ProviderError

  data object Unauthorized : ProviderError

  data object Network : ProviderError

  data object MalformedResponse : ProviderError

  data class Unknown(val message: String?) : ProviderError
}
