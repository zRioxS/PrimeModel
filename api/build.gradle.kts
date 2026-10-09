plugins {
    alias(libs.plugins.convention.publish)
    alias(libs.plugins.convention.bukkit)
}

dependencies {
    compileOnly(libs.bundles.minecraft)
    compileOnly(fileTree(rootProject.file("libs")) { include("*.jar") })
}