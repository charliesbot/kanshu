plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.charliesbot.kanshu.features.connection"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }

  buildFeatures { compose = true }
}

dependencies {
  implementation(project(":core:domain"))
  implementation(project(":core:designsystem"))
  implementation(project(":core:strings"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.koin.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.koin.compose)
  implementation(libs.koin.compose.viewmodel)

  debugImplementation(libs.androidx.compose.ui.tooling)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
}
