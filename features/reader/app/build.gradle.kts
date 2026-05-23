plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.charliesbot.kanshu.features.reader"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = true
  }
}

dependencies {
  coreLibraryDesugaring(libs.android.desugar.jdk.libs)
  // :core:data is a deliberate exception. ReaderResult.Success carries a Readium Publication and
  // Readium 3.x is an AAR whose public surface uses android.net.Uri, so the reader contract
  // can't live in :core:domain (kotlin-jvm). The other features stay strict. See CLAUDE.md.
  implementation(project(":core:model"))
  implementation(project(":core:data"))
  implementation(project(":core:designsystem"))
  implementation(project(":core:strings"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.koin.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)

  implementation(libs.readium.shared)
  implementation(libs.jsoup)
  implementation(libs.koin.compose)
  implementation(libs.koin.compose.viewmodel)

  debugImplementation(libs.androidx.compose.ui.tooling)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
}
