package com.charliesbot.kanshu.core.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderRegistryTest {
  @Test
  fun `routes providers by instance ID and filters disabled providers`() {
    val enabled = provider("enabled", enabled = true)
    val disabled = provider("disabled", enabled = false)
    val registry = ProviderRegistryImpl(listOf(enabled, disabled))

    assertEquals(listOf(enabled), registry.enabledProviders())
    assertEquals(disabled, registry.provider(ProviderInstanceId("disabled")))
  }

  @Test
  fun `rejects duplicate provider instance IDs`() {
    assertThrows(IllegalArgumentException::class.java) {
      ProviderRegistryImpl(listOf(provider("same"), provider("same")))
    }
  }

  @Test
  fun `fails lookup for an unknown provider instance`() {
    val registry = ProviderRegistryImpl(emptyList())

    assertThrows(NoSuchElementException::class.java) {
      registry.provider(ProviderInstanceId("missing"))
    }
  }

  private fun provider(id: String, enabled: Boolean = true): Provider =
    object : Provider {
      override val descriptor =
        ProviderDescriptor(
          id = ProviderInstanceId(id),
          type = ProviderType.KAVITA,
          displayName = id,
          enabled = enabled,
          capabilities = ProviderCapabilities(progressSync = false, highlightSync = false),
        )
    }
}
