plugins {
    id("com.android.application") version "9.3.1" apply false
    // Compose compiler; its version must equal the Kotlin (KGP) version AGP's built-in Kotlin uses
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
