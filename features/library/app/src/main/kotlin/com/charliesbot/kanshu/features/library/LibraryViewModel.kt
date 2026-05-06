package com.charliesbot.kanshu.features.library

import androidx.lifecycle.ViewModel
import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.KavitaCredentials
import kotlinx.coroutines.flow.Flow

class LibraryViewModel(credentialsRepository: CredentialsRepository) : ViewModel() {
  val credentials: Flow<KavitaCredentials?> = credentialsRepository.credentials
}
