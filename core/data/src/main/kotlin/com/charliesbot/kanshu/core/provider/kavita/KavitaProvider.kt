package com.charliesbot.kanshu.core.provider.kavita

import com.charliesbot.kanshu.core.provider.Provider
import com.charliesbot.kanshu.core.provider.ProviderCapabilities
import com.charliesbot.kanshu.core.provider.ProviderDescriptor
import com.charliesbot.kanshu.core.provider.ProviderInstanceId
import com.charliesbot.kanshu.core.provider.ProviderType

class KavitaProvider : Provider {
  override val descriptor =
    ProviderDescriptor(
      id = ID,
      type = ProviderType.KAVITA,
      displayName = "Kavita",
      enabled = true,
      capabilities = ProviderCapabilities(progressSync = true, highlightSync = false),
    )

  companion object {
    val ID = ProviderInstanceId("kavita")
  }
}
