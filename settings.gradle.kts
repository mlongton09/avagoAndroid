pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "avagoAndroid"

include(":app")

// Core modules
include(":core:design")
include(":core:ui")
include(":core:data")
include(":core:network")
include(":core:sync")
include(":core:auth")
include(":core:push")
include(":core:permissions")
include(":core:seed")
include(":core:i18n")

// Feature modules
include(":feature:auth")
include(":feature:assets")
include(":feature:log")
include(":feature:workorders")
include(":feature:inventory")
include(":feature:schedule")
include(":feature:docs")
include(":feature:reports")
include(":feature:chat")
include(":feature:settings")
