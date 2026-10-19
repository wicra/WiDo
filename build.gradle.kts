// Les plugins Android sont déclarés dans :app, pour que :core se construise et se teste
// sans SDK Android (voir docs/DEVELOPMENT.md).
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
