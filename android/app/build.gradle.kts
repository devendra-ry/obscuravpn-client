import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
}

extensions.configure<ApplicationExtension> {
    buildToolsVersion = "37.0.0"

    namespace = "net.obscura.vpnclientapp"
    compileSdk = 36

    defaultConfig {
        // Fork build: its own package, so it installs next to the official app (a different signing key cannot
        // update it). -Pobscura.applicationIdSuffix= (empty) gives the official package name.
        applicationId =
            "net.obscura.vpnclientapp" + (providers.gradleProperty("obscura.applicationIdSuffix").orNull ?: ".fork")
        // Fork build: Android 15 and newer only.
        minSdk = 35
        // API 37 needs compileSdk 37, which needs AGP 9.1+; AGP is pinned to what nixpkgs ships (see lint.xml).
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = project.getVersionCode(project.rootDir)
        versionName = project.getVersionName(project.rootDir)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only the arm64-v8a Rust library is built and shipped: phones only, no x86_64 emulators or ChromeOS devices.
        //noinspection ChromeOsAbiSupport
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
            resValue("string", "app_name", "Obscura VPN (Debug)")

            // Android Studio builds need to be signed to work properly, but don't sign Nix builds to make reproducible
            // and avoid using a random key each time.
            if (providers.gradleProperty("obscura.unsigned").orNull.toBoolean()) {
                signingConfig = null
            }
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            vcsInfo {
                // We include our own version and this creates a non-reproducible build.
                include = false
            }
        }
    }

    flavorDimensions += listOf("billing")

    productFlavors {
        create("foss") {
            dimension = "billing"
            isDefault = true
        }

        create("play") { dimension = "billing" }
    }
}

kotlin {
    compilerOptions { freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi") }
    jvmToolchain(21)
}

dependencies {
    implementation(libs.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.material)
    implementation(project(":lib:util"))

    "playImplementation"(project(":lib:billing"))

    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
