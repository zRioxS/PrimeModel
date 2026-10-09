plugins {
    alias(libs.plugins.convention.publish)
    alias(libs.plugins.convention.bukkit)
}

dependencies {
    // API
    shade(project(":api")) { isTransitive = false }

    // Minecraft / Bukkit
    compileOnly(libs.bundles.minecraft)
    compileOnly("com.mojang:authlib:7.0.61")

    // Core libraries
    compileOnly(libs.bundles.core)
    compileOnly(libs.cloud.core)

    // NMS
    rootProject.project("nms").subprojects.forEach {
        compileOnly(it)
    }

    // Shaded libraries
    shade(libs.bundles.shadedLibrary) {
        exclude("net.kyori")
        exclude("org.ow2.asm")
        exclude("io.leangen.geantyref")
    }

    // Manifest
    compileOnly(libs.bundles.manifestLibrary)

    // Local libraries (libs/ klasorundeki jar'lar)
    shade(fileTree(rootProject.file("libs")) { include("*.jar") })

    // Compatibility
    compileOnly("net.citizensnpcs:citizens-main:2.0.44-SNAPSHOT") {
        exclude("net.byteflux")
    }
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.12.6")
    compileOnly("io.lumine:Mythic-Dist:5.13.0")
    compileOnly("com.nexomc:nexo:1.28.0")
}