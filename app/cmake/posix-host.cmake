# app/cmake/posix-host.cmake
set(CMAKE_SYSTEM_NAME Linux)

# Compiler host (clang -> gcc)
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

# Ninja host: cari di sistem & di SDK Android cmake
find_program(HOST_NINJA
  NAMES ninja ninja-build
  HINTS /usr/bin /usr/local/bin
        $ENV{ANDROID_HOME}/cmake/3.22.1/bin
        /home/codespace/android-sdk/cmake/3.22.1/bin
)
if (NOT HOST_NINJA)
  message(FATAL_ERROR "Ninja tidak ditemukan. Install 'ninja-build'.")
endif()

# Paksa generator & make program untuk semua sub-configure & try_compile
set(CMAKE_GENERATOR "Ninja" CACHE STRING "" FORCE)
set(CMAKE_MAKE_PROGRAM ${HOST_NINJA} CACHE FILEPATH "" FORCE)
set(CMAKE_TRY_COMPILE_GENERATOR "Ninja" CACHE STRING "" FORCE)

# (opsional) tetapkan glslc eksplisit jika diperlukan
if (NOT DEFINED GGML_VULKAN_GLSLC_EXECUTABLE)
  find_program(GLSLC glslc)
  if (GLSLC)
    set(GGML_VULKAN_GLSLC_EXECUTABLE ${GLSLC} CACHE FILEPATH "" FORCE)
  endif()
endif()
