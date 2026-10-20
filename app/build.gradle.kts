import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

/**
 * Release signing is driven by CI secrets (see .github/workflows/build.yml) or a
 * local keystore.properties. If no keystore is present the release variant still
 * builds — unsigned — so a fresh clone or a fork without secrets does not go red.
 * A debug build is never published as the operational release.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}
val releaseStoreFile: String? =
    (keystoreProperties.getProperty("storeFile") ?: System.getenv("KEYSTORE_FILE"))
        ?.takeIf { it.isNotBlank() && rootProject.file(it).exists() }

android {
    namespace = "com.sovereignops.timetable"
    compileSdk = 35
    // Pinned to what scripts/bootstrap-android-sdk.sh installs; AGP would otherwise
    // auto-download its own default build-tools into the SDK.
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.sovereignops.timetable"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: System.getenv("KEYSTORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: System.getenv("KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    lint {
        // New problems fail the build; the ones that already existed are tracked in
        // lint-baseline.xml. Do not edit the baseline to hide a new problem.
        abortOnError = true
        checkReleaseBuilds = false
        baseline = file("lint-baseline.xml")
        // Dependency freshness is Dependabot's job; these checks only add churn
        // to the baseline every time a version moves.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Robolectric needs merged resources and the manifest to inflate the real UI.
            isIncludeAndroidResources = true
            all { test ->
                // Local override for the Robolectric android-all download, for networks
                // where Maven Central is rate limited; CI leaves it unset.
                System.getenv("ROBOLECTRIC_DEPENDENCY_REPO_URL")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { test.systemProperty("robolectric.dependency.repo.url", it) }
            }
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.roborazzi.accessibility.check)
}
