pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx (task 27) publishes its Android AAR only on GitHub releases and JitPack.
        // JitPack serves the release AAR byte for byte (SHA-256 pinned in verification-metadata),
        // and only for this one group.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.k2-fsa.sherpa-onnx") }
        }
    }
}

rootProject.name = "android-ai-agent"
include(":app")
include(":catalog")
include(":tools-probe")
include(":benchmark")
include(":stubmessenger")
project(":stubmessenger").projectDir = file("testapps/stubmessenger")
