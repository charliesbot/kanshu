plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.charliesbot.kanshu.core"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }

  buildFeatures { compose = true }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.koin.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.composeunstyled)
  implementation(libs.koin.android)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.kotlinx.serialization.json)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
