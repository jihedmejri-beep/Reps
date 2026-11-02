plugins {
    // AGP 9 compiles Kotlin itself; applying kotlin-android on top is an error.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.reps.app"
    // androidx (core 1.19, lifecycle 2.11, hilt-navigation 1.4) requires 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.reps.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Arabic and French ship at v1.0 alongside English.
        resourceConfigurations += setOf("en", "ar", "fr")
    }

    // The RepsAI backend address. There is no production deployment yet, so a
    // release build ships blank (the assistant reports "not configured") unless
    // the property is set, e.g. `-Preps.ai.baseUrl=https://api.example.com` or in
    // gradle.properties / ~/.gradle/gradle.properties. Never put API keys here:
    // anything in BuildConfig is readable from the APK.
    val aiBaseUrlOverride = providers.gradleProperty("reps.ai.baseUrl").orNull

    buildTypes {
        debug {
            buildConfigField("String", "REPS_AI_BASE_URL", "\"${aiBaseUrlOverride ?: "http://10.0.2.2:8000"}\"")
        }
        release {
            buildConfigField("String", "REPS_AI_BASE_URL", "\"${aiBaseUrlOverride.orEmpty()}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        // The exercise catalogue ships as a prebuilt SQLite file. AAPT compresses
        // assets by default, and Room cannot open a compressed asset - it has to
        // copy it out byte-for-byte on first launch.
        noCompress += "db"
    }
}

// Room's exported schema is what tools/build_exercise_db.py reads to generate
// assets/reps_exercises.db with the exact DDL and identity hash Room will
// validate against at open time. Without this the generated file drifts.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

composeCompiler {
    // Lets the compiler treat the domain models as stable, so composables taking
    // one can skip recomposition. See the file itself for why this is not done
    // with @Immutable on the classes.
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_stability.conf"),
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.work.compiler)

    implementation(libs.androidx.work.runtime)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)

    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    implementation(libs.gson)
    implementation(libs.okhttp)
}
