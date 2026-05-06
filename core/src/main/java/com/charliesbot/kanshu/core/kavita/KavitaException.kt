package com.charliesbot.kanshu.core.kavita

sealed class KavitaException(message: String? = null, cause: Throwable? = null) :
  Exception(message, cause) {
  data object Unauthorized : KavitaException()

  data object UnexpectedResponse : KavitaException()

  data object NetworkError : KavitaException()

  class Unknown(message: String?) : KavitaException(message)
}
