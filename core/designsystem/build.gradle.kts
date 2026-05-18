plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.charliesbot.kanshu.core.designsystem"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui.tooling.preview)
  api(libs.composeunstyled.button)
  implementation(libs.coil.compose)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
