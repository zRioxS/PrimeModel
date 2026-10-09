pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()

        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.org/repository/maven-public/")
        maven("https://repo.alessiodp.com/releases/")
        maven("https://maven.blamejared.com/")
        maven("https://maven.citizensnpcs.co/repo/")
        maven("https://mvn.lumine.io/repository/maven-public/")
        maven("https://maven.nucleoid.xyz/")
        maven("https://repo.nexomc.com/releases/")
        maven(url = "https://central.sonatype.com/repository/maven-snapshots/") {
            name = "central-snapshots"
            mavenContent { snapshotsOnly() }
        }
    }
}

rootProject.name = "PrimeModel"

val published = setOf(
    "api",

    "core",

    "platform:spigot",
    "platform:paper",
)

include(published)

include(
    "nms:v1_21_R6", //1.21.9-1.21.10
    "nms:v1_21_R7", //1.21.11
    "nms:v26_R1",   //26.1
    "nms:v26_R2",   //26.2
    "nms:v26_R3",   //26.3
)

