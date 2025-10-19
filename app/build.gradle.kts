plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cicero.ciceroai"
    compileSdk = 34
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 33
        targetSdk = 34

        // ARGUMEN CMAKE taruh di SINI (bukan di blok cmake luar)
        externalNativeBuild {
            cmake {
                // Aktifkan backend Vulkan di ggml/llama.cpp
                arguments += listOf(
                    "-DCICERO_ENABLE_VULKAN=ON",
                    "-DGGML_VULKAN=ON"
                )
            }
        }

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Path & versi CMake (jangan taruh arguments di sini)
    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += "gguf"
    }

    sourceSets {
        getByName("main") {
            assets.srcDir("src/main/assets")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.jvm.coroutines.core)
    implementation(libs.jvm.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.documentfile)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

/**
 * ====== PATCH host-toolchain untuk paksa MSVC di vulkan-shaders-gen ======
 * Pastikan kamu sudah membuat file: app/cmake/msvc-host.cmake
 * (isinya sesuai yang sudah kuberikan sebelumnya).
 */
fun findHostToolchainFile(root: File): File? =
    root.walkTopDown().maxDepth(6).firstOrNull {
        it.isFile && it.name == "host-toolchain.cmake"
    }

tasks.register("patchHostToolchain") {
    doLast {
        val cxxRoot = file("$projectDir/.cxx")
        if (!cxxRoot.exists()) {
            println("No .cxx dir yet; it will be created on first CMake run.")
            return@doLast
        }

        val hostTc = findHostToolchainFile(cxxRoot) ?: run {
            println("host-toolchain.cmake not found yet; will try again in next run.")
            return@doLast
        }

        val msvcTc = file("app/cmake/msvc-host.cmake").absoluteFile
        require(msvcTc.exists()) {
            "Missing app/cmake/msvc-host.cmake — buat dulu sesuai instruksi."
        }

        hostTc.writeText(
            """
            # Patched by Gradle: redirect to MSVC host toolchain
            include("${msvcTc.toString().replace("\\", "/")}")
            """.trimIndent()
        )
        println("Patched host toolchain: ${hostTc.absolutePath}")
    }
}

// Jalankan patch sebelum tugas build CMake apa pun
tasks.matching { it.name.startsWith("buildCMake") || it.name.contains("ExternalNativeBuild") }
    .configureEach { dependsOn("patchHostToolchain") }
