import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val releaseSigningNames = listOf(
    "TIENDATECH_KEYSTORE_PATH", "TIENDATECH_KEYSTORE_PASSWORD",
    "TIENDATECH_KEY_ALIAS", "TIENDATECH_KEY_PASSWORD"
)
val releaseSigningValues = releaseSigningNames.associateWith {
    providers.environmentVariable(it).orNull
}
val releaseSigningReady = releaseSigningValues.values.all { !it.isNullOrBlank() }

abstract class RequireReleaseSigning : DefaultTask() {
    @get:Input
    abstract val missingNames: org.gradle.api.provider.ListProperty<String>

    @get:Input
    abstract val keystorePath: org.gradle.api.provider.Property<String>

    @TaskAction
    fun verifySigningInputs() {
        check(missingNames.get().isEmpty()) {
            "Firma release pendiente. Configurar: ${missingNames.get().joinToString()}."
        }
        check(File(keystorePath.get()).isFile) {
            "El archivo de firma release no existe."
        }
    }
}

val requireReleaseSigning = tasks.register<RequireReleaseSigning>("requireReleaseSigning") {
    group = "verification"
    description = "Exige la clave del equipo antes de empaquetar una versión release."
    missingNames.set(releaseSigningNames.filter { releaseSigningValues[it].isNullOrBlank() })
    keystorePath.set(releaseSigningValues["TIENDATECH_KEYSTORE_PATH"]?.let { file(it).absolutePath } ?: "")
}

tasks.configureEach {
    if (name in setOf("packageRelease", "packageReleaseBundle", "assembleRelease", "bundleRelease")) {
        dependsOn(requireReleaseSigning)
    }
}

android {
    namespace = "com.tiendatech.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tiendatech.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("distribution") {
                storeFile = file(releaseSigningValues.getValue("TIENDATECH_KEYSTORE_PATH")!!)
                storePassword = releaseSigningValues["TIENDATECH_KEYSTORE_PASSWORD"]
                keyAlias = releaseSigningValues["TIENDATECH_KEY_ALIAS"]
                keyPassword = releaseSigningValues["TIENDATECH_KEY_PASSWORD"]
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            val debugApiBaseUrl = providers.gradleProperty("TIENDATECH_DEBUG_API_BASE_URL")
                .getOrElse("http://10.0.2.2:8180/")
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("distribution")
            }
            val releaseApiBaseUrl = providers.gradleProperty("TIENDATECH_API_BASE_URL")
                .getOrElse("https://18-221-94-105.sslip.io/")
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlin.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.compose.ui)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
