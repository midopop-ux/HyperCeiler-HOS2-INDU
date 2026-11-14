@file:Suppress("UnstableApiUsage")

data class GprCredentials(val user: String, val key: String)

fun loadGprCredentials(): GprCredentials {
    val envUser = System.getenv("GIT_ACTOR")?.takeIf { it.isNotBlank() }
    val envKey = System.getenv("GIT_TOKEN")?.takeIf { it.isNotBlank() }

    if (envUser != null && envKey != null) {
        return GprCredentials(envUser, envKey)
    }

    val propsFile = File(rootDir, "signing.properties")
    if (propsFile.exists()) {
        val props = java.util.Properties().apply {
            propsFile.inputStream().use { load(it) }
        }
        val user = props.getProperty("gpr.user")?.takeIf { it.isNotBlank() }
        val key = props.getProperty("gpr.key")?.takeIf { it.isNotBlank() }
        if (user != null && key != null) {
            return GprCredentials(user, key)
        }
    }

    return GprCredentials("dummy", "dummy")
}

val gprCredentials by lazy { loadGprCredentials() }

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://maven.pkg.github.com/ReChronoRain/HyperCeiler") {
            credentials {
                username = gprCredentials.user
                password = gprCredentials.key
            }
        }
        maven("https://jitpack.io")
        maven("https://api.xposed.info")
    }
}

rootProject.name = "HyperCeiler"

include(
    "app",
    // ":library:hook",
    ":library:libhook",
    ":library:core",
    ":library:provision",
    ":library:common",
    ":library:processor",
    ":library:hidden-api",
)
