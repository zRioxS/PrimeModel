import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import kotlin.io.encoding.Base64

plugins {
    id("standard-conventions")
    id("com.vanniktech.maven.publish")
    signing
}

rootProject.dependencies.dokka(project)

val artifactBaseId = name
val artifactVersion = project.version.toString().run {
    BUILD_NUMBER?.let { substringBeforeLast("-$it") } ?: this
}

signing {
    val key = System.getenv("SIGNING_KEY")?.let {
        Base64.decode(it.toByteArray()).toString(Charsets.UTF_8)
    }
    val password = System.getenv("SIGNING_PASSWORD")
    if (!key.isNullOrEmpty() && !password.isNullOrEmpty()) {
        useInMemoryPgpKeys(
            key,
            password
        )
    } else useGpgCmd()
}

dependencies {
    api(libs.bundles.library) {
        exclude(module = "jspecify")
    }

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.RioxS", artifactBaseId, artifactVersion)
    configure(JavaLibrary(
        javadocJar = JavadocJar.Javadoc(),
        sourcesJar = SourcesJar.Sources(),
    ))
    pom {
        name = artifactBaseId
        description = "Modern Bedrock model engine for Minecraft Java Edition"
        inceptionYear = "2024"
    }
}

