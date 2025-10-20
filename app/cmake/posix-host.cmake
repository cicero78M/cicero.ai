# app/cmake/posix-host.cmake
set(CMAKE_SYSTEM_NAME Linux)

# Compiler host (clang lalu fallback gcc)
find_program(HOST_CC  clang)
find_program(HOST_CXX clang++)
if (NOT HOST_CC OR NOT HOST_CXX)
  find_program(HOST_CC  gcc)
  find_program(HOST_CXX g++)
endif()
if (NOT HOST_CC OR NOT HOST_CXX)
  message(FATAL_ERROR "Host compiler tidak ditemukan (clang/gcc).")
endif()
set(CMAKE_C_COMPILER   ${HOST_CC})
set(CMAKE_CXX_COMPILER ${HOST_CXX})

# Ninja host: cari di sistem & Android SDK CMake
find_program(HOST_NINJA
  NAMES ninja ninja-build
  HINTS /usr/bin /usr/local/bin
        $ENV{ANDROID_HOME}/cmake/3.22.1/bin
        /home/codespace/android-sdk/cmake/3.22.1/bin
)
if (NOT HOST_NINJA)
  message(FATAL_ERROR "Ninja tidak ditemukan. Install 'ninja-build' atau tambahkan ke PATH.")
endif()
set(CMAKE_MAKE_PROGRAM ${HOST_NINJA})

# (opsional) jika perlu glslc eksplisit
if (NOT DEFINED GGML_VULKAN_GLSLC_EXECUTABLE)
  find_program(GLSLC glslc)
  if (GLSLC)
    set(GGML_VULKAN_GLSLC_EXECUTABLE ${GLSLC})
  endif()
endif()
