import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
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
        // Raise versionCode with every build handed to the phone; Android refuses a downgrade.
        versionCode = 2
        versionName = "1.0.0-rc.1"
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

    // Committed Room schema JSON (app/schemas) goes into the DEBUG build's assets only, which is
    // where Robolectric reads assets from, so migration tests can open every released schema.
    // Release builds do not carry it.
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

/**
 * Ships the repository's THIRD_PARTY_NOTICES.md inside the APK as an asset, so the
 * notices travel with every build (and the app shows them under Settings).
 */
abstract class SyncNoticesTask : DefaultTask() {
    @get:InputFile
    abstract val notices: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        val out = outputDir.get().asFile
        out.mkdirs()
        notices.get().asFile.copyTo(out.resolve("THIRD_PARTY_NOTICES.md"), overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        val sync = tasks.register<SyncNoticesTask>("sync${variant.name.replaceFirstChar { it.uppercase() }}Notices") {
            notices.set(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(sync, SyncNoticesTask::outputDir)
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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.calendar.view)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.roborazzi.accessibility.check)
}
