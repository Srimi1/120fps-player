plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Declared here even though only :interp-core applies it. The jvm and android
    // Kotlin plugins ship in one jar, so a subproject requesting a version for a
    // plugin already on the classpath fails plugin resolution; declaring every
    // plugin once at the root resolves the version a single time.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
