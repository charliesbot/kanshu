plugins { alias(libs.plugins.android.library) }

android {
  namespace = "com.charliesbot.kanshu.strings"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
}
