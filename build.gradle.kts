// Les plugins Kotlin sont déclarés ici, une seule fois, avec leur version (sinon Gradle refuse
// de les charger deux fois). Le plugin Android est déclaré dans :app, pour que :core se
// construise et se teste sans SDK Android (voir docs/DEVELOPMENT.md).
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
