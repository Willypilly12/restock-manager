# Restock Manager (Stage 1)
Client-side Fabric mod for 1.21.11. Alerts only - never moves items, never sends /rg.

Build: `./gradlew build` (needs internet + JDK 21; add the Gradle wrapper with `gradle wrapper` if absent).
Jar: build/libs/restock-manager-0.1.0.jar -> put in .minecraft/mods with Fabric API.
Logic tests (no Minecraft needed): `./gradlew coreTests`.
If a dependency version is not found, update gradle.properties from https://fabricmc.net/develop.
