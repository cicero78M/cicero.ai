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
                    "-DGGML_VULKAN=ON",
                    "-DGGML_VULKAN_GLSLC_EXECUTABLE=/usr/bin/glslc",
                    // Batasi CMake hanya membangkitkan ABI yang masih didukung
                    "-DANDROID_ABI=arm64-v8a"
                )
            }
        }

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Paksa Gradle mengeluarkan APK release langsung, termasuk varian universal
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
            // Atau batasi ke satu ABI saja
            // include("arm64-v8a")
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
 * ====== PATCH host-toolchain untuk konfigurasi toolchain host Vulkan shaders ======
 * Pastikan kamu sudah membuat file: app/cmake/host-toolchain.cmake
 * beserta dependensinya (msvc-host.cmake, posix-host.cmake).
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

        val hostTcTemplate = file("cmake/host-toolchain.cmake").absoluteFile
        require(hostTcTemplate.exists()) {
            "Missing app/cmake/host-toolchain.cmake — buat dulu sesuai instruksi."
        }

        val redirect = """
            # Patched by Gradle: redirect to shared host toolchain configuration
            include("${hostTcTemplate.toString().replace("\\", "/")}")
        """.trimIndent()

        hostTc.writeText(redirect)
        println("Patched host toolchain: ${hostTc.absolutePath} -> ${hostTcTemplate.absolutePath}")
    }
}

// Jalankan patch sebelum tugas build CMake apa pun
tasks.matching { it.name.startsWith("buildCMake") || it.name.contains("ExternalNativeBuild") }
    .configureEach { dependsOn("patchHostToolchain") }
