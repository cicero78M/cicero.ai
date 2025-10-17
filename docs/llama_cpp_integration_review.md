# llama.cpp Android Integration Review

## Overview
This document captures a deep dive into how the Android application integrates the native
[llama.cpp](https://github.com/ggml-org/llama.cpp) inference runtime through JNI and CMake. It
summarizes the existing build flow, evaluates the JNI surface, and highlights risks and potential
improvements for both application builds and reusable AAR packaging.

## Build & Dependency Integration
- The native library is configured in `app/src/main/cpp/CMakeLists.txt`, which pins llama.cpp to
  commit `1bb4f43380944e94c9a86e305789ba103f5e62bd` via `FetchContent_Declare`. 【F:app/src/main/cpp/CMakeLists.txt†L11-L27】
- Build-time options explicitly disable optional llama.cpp components (tests, server, GUI, shared
  libs) to reduce build time and artifacts. 【F:app/src/main/cpp/CMakeLists.txt†L13-L19】
- CMake exposes a shared library target `cicero_llama` that links the core `llama` static library
  from llama.cpp together with Android's `log` system library. 【F:app/src/main/cpp/CMakeLists.txt†L29-L41】
- For an AAR workflow, replacing `FetchContent` with a Git submodule (e.g., `third_party/llama.cpp`)
  would eliminate network fetches during Gradle syncs and allow reproducible offline builds. The
  current setup requires internet access when the CMake cache is missing.
- Because `BUILD_SHARED_LIBS` is forced `OFF`, the produced `libcicero_llama.so` only bundles the
  bridge logic while statically linking llama.cpp; this is compatible with both app modules and
  Android library/AAR modules.

## JNI Bridge (`llama_bridge.cpp`)
- The bridge exposes three JNI entry points matching the Kotlin facade: `nativeInit`,
  `nativeCompletion`, and `nativeRelease`. 【F:app/src/main/cpp/llama_bridge.cpp†L198-L283】
- Backend initialisation is reference counted (`retainBackend` / `releaseBackend`) to ensure GGML and
  llama backends are loaded exactly once, mirroring llama.cpp recommendations. 【F:app/src/main/cpp/llama_bridge.cpp†L39-L67】
- `nativeInit` loads a model from the provided path, constructs a llama context, and stores thread /
  context configuration inside a heap-allocated `LlamaSession`. Errors propagate back as
  `IllegalStateException`s with logcat tracing. 【F:app/src/main/cpp/llama_bridge.cpp†L210-L262】
- `nativeCompletion` reuses the cached session, tokenises the prompt, and performs greedy sampling
  with llama.cpp's sampler chain API, enforcing context-length checks and backend thread counts.
  【F:app/src/main/cpp/llama_bridge.cpp†L84-L194】
- `nativeRelease` frees both the llama context and model, then decrements the backend reference
  count. 【F:app/src/main/cpp/llama_bridge.cpp†L285-L310】

## Kotlin Facade & Lifecycle
- `LlamaBridge` statically loads `libcicero_llama.so` and exposes the JNI signatures to Kotlin.
  【F:app/src/main/java/com/cicero/ciceroai/llama/LlamaBridge.kt†L3-L10】
- `LlamaController` orchestrates session creation, inference, and teardown on a background dispatcher
  while exposing convenience helpers to prepare models from assets or downloads. 【F:app/src/main/java/com/cicero/ciceroai/llama/LlamaController.kt†L8-L48】
- `ModelAssetManager` copies bundled GGUF assets on demand and supports HTTP(S) downloads into the
  app's private storage, enabling runtime model management. 【F:app/src/main/java/com/cicero/ciceroai/llama/ModelAssetManager.kt†L12-L72】

## Packaging Considerations for AAR Distribution
1. **Resource Stripping** – The Gradle packaging configuration marks `.gguf` files as uncompressed to
   avoid loading penalties when bundled inside an AAR. 【F:app/build.gradle.kts†L44-L47】
2. **ABI Coverage** – Currently restricted to `arm64-v8a`, `armeabi-v7a`, and `x86_64`. Consider
   whether an AAR should also ship `x86` for emulators or restrict to 64-bit only, depending on
   distribution goals. 【F:app/build.gradle.kts†L24-L27】
3. **CMake Toolchain** – Exporting the bridge as an Android library module will require consumers to
   run the same externalNativeBuild. Providing prebuilt `.so` binaries per ABI inside the AAR or a
   prefab package could streamline adoption.
4. **Threading Defaults** – `LlamaController.prepareSession` trusts caller-supplied thread and context
   values. For a generic AAR, exposing configuration defaults (e.g., based on available CPU cores) or
   validation guards would improve safety.
5. **Model Storage** – Current asset manager writes to internal storage. When embedded in an AAR, the
   host app must supply context-specific directories or override this behaviour for multi-process
   apps.

## Recommendations
- Promote llama.cpp to a checked-in Git submodule to guarantee deterministic builds and enable
  patching without depending on upstream availability at build time.
- Add instrumentation or unit tests covering JNI lifecycles (init → infer → release) using the
  Android NDK testing APIs or Robolectric with JNI mocking to detect regressions.
- Consider exposing Kotlin suspend functions that accept structured parameters (e.g. sampling
  strategies) rather than hardcoding greedy sampling to align with future llama.cpp features.
- Document required llama.cpp commit updates, including verifying API changes, before bumping the
  pinned revision to prevent JNI breakages.
