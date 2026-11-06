plugins {
    id("com.android.application") version "8.9.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    // Annotation processing for Room (version must match the Kotlin version above).
    id("com.google.devtools.ksp") version "2.1.20-2.0.1" apply false
}
