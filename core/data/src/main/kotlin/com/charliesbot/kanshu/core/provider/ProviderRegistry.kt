package com.charliesbot.kanshu.core.provider

interface ProviderRegistry {
  fun enabledProviders(): List<Provider>

  fun provider(id: ProviderInstanceId): Provider
}

class ProviderRegistryImpl(providers: List<Provider>) : ProviderRegistry {
  private val providersById =
    providers
      .associateBy { it.descriptor.id }
      .also { providersById ->
        require(providersById.size == providers.size) { "Provider instance IDs must be unique" }
      }

  override fun enabledProviders(): List<Provider> =
    providersById.values.filter { it.descriptor.enabled }

  override fun provider(id: ProviderInstanceId): Provider =
    providersById[id] ?: throw NoSuchElementException("Unknown provider instance: ${id.value}")
}
