plugins {
    alias(libs.plugins.convention.standard)
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

val minecraft = property("minecraft_version").toString()
val versionString = version.toString()
val groupString = group.toString()

val javadocJar = tasks.register<Jar>("javadocJar") {
    description = "Makes javadoc."
    dependsOn(tasks.dokkaGenerate)
    archiveClassifier = "javadoc"
    from(layout.buildDirectory.dir("dokka/html").orNull?.asFile)
}

runPaper {
    disablePluginJarDetection()
}

val primeModel get() = project(":platform:paper").tasks.named<Jar>("shadowJar").flatMap {
    it.archiveFile
}
runPaper.folia.registerTask {
    pluginJars(primeModel)
    minecraftVersion(minecraft)
}

tasks {
    runServer {
        pluginJars(fileTree("plugins"))
        pluginJars(primeModel)
        minecraftVersion(minecraft)
        downloadPlugins {
        }
    }
    build {
        finalizedBy(
            javadocJar
        )
    }
    jar {
        enabled = false
    }
}


