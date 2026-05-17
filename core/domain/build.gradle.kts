plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(11) }

dependencies {
  api(project(":core:model"))
  api(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
}
