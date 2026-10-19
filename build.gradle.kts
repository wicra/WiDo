// Tous les plugins sont déclarés ici, une seule fois, avec leur version : le plugin Kotlin doit
// être chargé avec le plugin Android, dans le même chargeur de classes (sinon Gradle refuse
// de les appliquer). Les modules les utilisent sans version.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
