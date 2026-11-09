plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Room genera el DAO en tiempo de compilacion.
    alias(libs.plugins.kotlin.kapt)
}

// El compilador de Compose se acopla a la version de Kotlin:
// Kotlin 1.9.22 <-> Compose Compiler 1.5.8
val composeCompilerVersion = libs.versions.composeCompiler.get()

android {
    namespace = "com.nexora.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nexora.android"
        minSdk = 26
        targetSdk = 34

        versionCode = 8
        versionName = "0.8.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = composeCompilerVersion
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Acceso a documentos y arboles de carpetas (Storage Access Framework).
    // Es la via principal de escaneo: no requiere ningun permiso.
    implementation(libs.androidx.documentfile)

    // Corrutinas: el escaneo de almacenamiento es cancellable y se ejecuta
    // fuera del hilo principal.
    implementation(libs.kotlinx.coroutines.android)

    // Persistencia local: la biblioteca de juegos sobrevive al reinicio.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Pruebas
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
