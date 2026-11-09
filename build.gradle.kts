plugins {
    id("com.android.application") version "9.2.0" apply false
    // AGP 9 has built-in Kotlin support; declaring KGP here pins its version (Miuix needs Kotlin 2.4).
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}
