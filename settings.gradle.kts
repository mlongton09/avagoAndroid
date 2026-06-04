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
include(":core:docscan")
include(":core:ocr")
include(":core:reports")
include(":core:csv")
include(":core:pdf")
include(":core:ai")

// Macrobenchmark module
include(":macrobenchmark")

// Feature modules
include(":feature:auth")
include(":feature:assets")
include(":feature:log")
include(":feature:workorders")
include(":feature:inventory")
include(":feature:schedule")
include(":feature:docs")
include(":feature:chat")
include(":feature:settings")
include(":feature:scout")
