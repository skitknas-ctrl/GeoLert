plugins {
    id("com.android.application")
}

android {
    namespace = "se.dick.geolert"
    compileSdk = 36
    defaultConfig {
        applicationId = "se.dick.geolert"
        minSdk = 30
        targetSdk = 36
        // CI passes -PbuildNumber=<run number>; the version then becomes 0.1.<n>
        val baseVersion = "0.1"
        val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull()
        versionCode = buildNumber ?: 1
        versionName = if (buildNumber != null) "$baseVersion.$buildNumber" else baseVersion
    }

    // foss = no Google Play Services (F-Droid / GitHub). A "play" flavor is added later.
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
        }
    }

    signingConfigs {
        // Key kept in the repo so every CI build can update the previous one (Obtainium).
        // Replace with a secret key before publishing widely.
        create("ci") {
            storeFile = file("ci.keystore")
            storePassword = "android"
            keyAlias = "geolert"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ci")
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // OpenStreetMap map view, no Google services
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
