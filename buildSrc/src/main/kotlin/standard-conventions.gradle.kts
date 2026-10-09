plugins {
    java
    kotlin("jvm")
    id("org.jetbrains.dokka")

}

group = "kr.rioxs.primemodel"
version = property("project_version").toString() + (BUILD_NUMBER?.let { "-SNAPSHOT-$it" } ?: "")

val shade = configurations.create("shade")

configurations.implementation {
    extendsFrom(shade)
}

dependencies {
    testImplementation(kotlin("test"))

    compileOnly(libs.bundles.library) {
        exclude(module = "jspecify")
    }
    testImplementation(libs.bundles.library)
}

tasks {
    test {
        useJUnitPlatform()
    }
    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }
}
java {
    disableAutoTargetJvm()
    toolchain.languageVersion = JavaLanguageVersion.of(JAVA_VERSION)
}

kotlin {
    jvmToolchain(JAVA_VERSION)
}

dokka {
    moduleName = project.name
    dokkaSourceSets.configureEach {
        displayName = project.name
    }
}
