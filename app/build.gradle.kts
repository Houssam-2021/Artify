plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/*
 * Release signing.
 *
 * Signing material is NEVER stored in this repository. It is read from
 * environment variables (CI secrets) or from Gradle properties defined in
 * ~/.gradle/gradle.properties on the maintainer's machine. When none are
 * present, release builds are produced unsigned. See docs/RELEASING.md.
 */
fun secret(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }

val releaseStoreFile = secret("ARTIFY_KEYSTORE_FILE")
val hasReleaseSigning = releaseStoreFile != null &&
    secret("ARTIFY_KEYSTORE_PASSWORD") != null &&
    secret("ARTIFY_KEY_ALIAS") != null &&
    secret("ARTIFY_KEY_PASSWORD") != null

if (releaseStoreFile != null) {
    val f = file(releaseStoreFile)
    val repoRoot = rootProject.projectDir.canonicalFile
    if (f.canonicalFile.startsWith(repoRoot)) {
        throw GradleException(
            "Refusing to sign with a keystore located inside the repository ($f). " +
                "Move it outside the project directory so it can never be committed."
        )
    }
}

android {
    namespace = "io.github.houssam2021.artify"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.houssam2021.artify"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = secret("ARTIFY_KEYSTORE_PASSWORD")
                keyAlias = secret("ARTIFY_KEY_ALIAS")
                keyPassword = secret("ARTIFY_KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.input:input-motionprediction:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
