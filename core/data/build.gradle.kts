plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.ksp)
  alias(libs.plugins.androidx.room)
}

android {
  namespace = "com.charliesbot.kanshu.core.data"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = true
  }

  packaging { resources { excludes += setOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md") } }

  testOptions { unitTests { isReturnDefaultValues = true } }
}

// Schema JSONs are checked in. Bumping KanshuDatabase.version writes a new file here that future
// migrations are validated against.
room { schemaDirectory("$projectDir/schemas") }

dependencies {
  coreLibraryDesugaring(libs.android.desugar.jdk.libs)
  api(project(":core:model"))
  api(project(":core:domain"))
  implementation(libs.androidx.core.ktx)
  implementation(platform(libs.koin.bom))
  implementation(libs.koin.android)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  // Reader source returns a Readium Publication — `api` so consumers (the reader feature) see
  // the type without re-declaring readium-shared. Streamer is implementation-only; only the
  // KavitaReaderSource calls it.
  api(libs.readium.shared)
  implementation(libs.readium.streamer)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)

  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.mockk.android)
  androidTestImplementation(libs.androidx.room.testing)
  androidTestImplementation(libs.kotlinx.coroutines.test)
}
