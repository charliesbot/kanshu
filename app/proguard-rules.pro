# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Readium: when isMinifyEnabled is turned on for release, Readium relies on reflection in
# several places that need keep rules — kotlinx.serialization @Serializable models in
# readium-shared, FragmentManager.fragmentFactory instantiating EpubNavigatorFragment by
# class name, JavascriptInterface methods in the EPUB WebView bridge, and the internal
# R2ViewPager / R2RTLViewPager subclasses. Pull the consumer rules from the upstream
# project (https://github.com/readium/kotlin-toolkit/tree/main/test-app) before flipping
# minify on.