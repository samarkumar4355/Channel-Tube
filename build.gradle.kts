plugins {
    id("com.android.application") version "9.1.1" apply false
    // AGP 9.1.1 bundles Kotlin 2.2.10. The Compose compiler plugin must match it.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
}
