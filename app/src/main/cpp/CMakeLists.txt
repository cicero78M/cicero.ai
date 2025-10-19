cmake_minimum_required(VERSION 3.22.1)
project(cicero_llama LANGUAGES C CXX)

# ====== Sanity: tolak path dengan ';' (pecah manifest mt.exe) ======
string(FIND "${CMAKE_SOURCE_DIR}" ";" _src_has_sc)
string(FIND "${CMAKE_BINARY_DIR}" ";" _bin_has_sc)
if (_src_has_sc GREATER -1 OR _bin_has_sc GREATER -1)
    message(FATAL_ERROR
            "Project/build path mengandung ';' yang memecah manifest tool (mt.exe).\n"
            "Pindahkan project & build folder ke path tanpa ';', mis. D:/dev/cicero_ai")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ==== Prefer /EHsc pada MSVC (hilangkan warning C4530 pada tools host) ====
if (MSVC)
    add_compile_options(/EHsc)
endif()

include(FetchContent)

# ==== Opsi build ====
option(CICERO_ENABLE_VULKAN "Enable the GGML Vulkan backend" ON)   # default ON
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER_GUI OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON    OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS     ON  CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE        OFF CACHE BOOL "" FORCE)

# ==== Cari Ninja & paksa dipakai untuk shader generator (host) ====
set(_cicero_ninja_hints)
get_filename_component(_cicero_cmake_dir "${CMAKE_COMMAND}" DIRECTORY)
if (_cicero_cmake_dir)
    list(APPEND _cicero_ninja_hints "${_cicero_cmake_dir}")
    get_filename_component(_cicero_cmake_parent "${_cicero_cmake_dir}" DIRECTORY)
    if (_cicero_cmake_parent)
        list(APPEND _cicero_ninja_hints "${_cicero_cmake_parent}")
    endif()
endif()

find_program(CICERO_NINJA_EXECUTABLE
        NAMES ninja ninja.exe
        HINTS ${_cicero_ninja_hints}
        DOC "Path to Ninja for Vulkan shader generator"
)

unset(_cicero_ninja_hints)
unset(_cicero_cmake_dir)
unset(_cicero_cmake_parent)

if (CICERO_ENABLE_VULKAN)
    if (NOT CICERO_NINJA_EXECUTABLE)
        message(WARNING "Ninja tidak ditemukan; Vulkan akan dimatikan.")
        set(CICERO_ENABLE_VULKAN OFF)
    else()
        set(_cicero_ninja_path "${CICERO_NINJA_EXECUTABLE}")
        if (WIN32)
            file(TO_NATIVE_PATH "${CICERO_NINJA_EXECUTABLE}" _cicero_ninja_path)
        endif()
        # Paksa nested CMake memakai Ninja ini
        set(CMAKE_MAKE_PROGRAM "${_cicero_ninja_path}" CACHE FILEPATH
                "Ninja executable for shader generation" FORCE)
        set(Ninja_EXECUTABLE   "${_cicero_ninja_path}" CACHE FILEPATH
                "Ninja executable for shader generation" FORCE)
        # Tambah folder Ninja ke PATH proses ini (untuk proses CMake baru)
        get_filename_component(_cicero_ninja_dir "${_cicero_ninja_path}" DIRECTORY)
        if (_cicero_ninja_dir)
            list(APPEND CMAKE_PROGRAM_PATH "${_cicero_ninja_dir}")
            list(REMOVE_DUPLICATES CMAKE_PROGRAM_PATH)
            set(CMAKE_PROGRAM_PATH "${CMAKE_PROGRAM_PATH}" CACHE STRING
                    "Program search path containing Ninja" FORCE)
            set(_cicero_env_ninja_dir "${_cicero_ninja_dir}")
            if (WIN32)
                file(TO_NATIVE_PATH "${_cicero_ninja_dir}" _cicero_env_ninja_dir)
            endif()
            string(FIND "$ENV{PATH}" "${_cicero_env_ninja_dir}" _cicero_path_index)
            if (_cicero_path_index EQUAL -1)
                if (WIN32)
                    set(ENV{PATH} "${_cicero_env_ninja_dir};$ENV{PATH}")
                else()
                    set(ENV{PATH} "${_cicero_env_ninja_dir}:$ENV{PATH}")
                endif()
            endif()
            message(STATUS "Ninja: ${_cicero_ninja_path}")
        endif()
        unset(_cicero_env_ninja_dir)
        unset(_cicero_ninja_path)
    endif()
endif()

# Sinkronkan flag Vulkan ke ggml/llama.cpp
set(GGML_VULKAN ${CICERO_ENABLE_VULKAN} CACHE BOOL "" FORCE)
if (ANDROID AND CICERO_ENABLE_VULKAN)
    add_compile_definitions(VK_USE_PLATFORM_ANDROID_KHR=1)
endif()

message(STATUS "CICERO_ENABLE_VULKAN = ${CICERO_ENABLE_VULKAN}")
message(STATUS "GGML_VULKAN          = ${GGML_VULKAN}")

# ==== Ambil Vulkan-Hpp ====
FetchContent_Declare(
        vulkan_hpp
        GIT_REPOSITORY https://github.com/KhronosGroup/Vulkan-Hpp.git
        GIT_TAG v1.3.268
        GIT_SHALLOW TRUE
        GIT_SUBMODULES ""
        GIT_SUBMODULES_RECURSE FALSE
)
FetchContent_MakeAvailable(vulkan_hpp)

# ==== Ambil llama.cpp (GGML) ====
FetchContent_Declare(
        llama_cpp
        GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
        GIT_TAG 1bb4f43380944e94c9a86e305789ba103f5e62bd
        GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(llama_cpp)

# ==== Library bridge kamu ====
add_library(
        cicero_llama
        SHARED
        llama_bridge.cpp
)

# Android log
find_library(log-lib log)

target_link_libraries(
        cicero_llama
        PRIVATE
        llama
        ${log-lib}
)

# ==== Vulkan (Android) ====
if (ANDROID AND CICERO_ENABLE_VULKAN)
    # NDK sysroot menyediakan libvulkan.so; cukup temukan dan tautkan
    find_library(vulkan_lib vulkan)
    if (NOT vulkan_lib)
        message(FATAL_ERROR "libvulkan.so tidak ditemukan dari NDK sysroot")
    endif()

    target_link_libraries(
            cicero_llama
            PRIVATE
            ${vulkan_lib}
    )

    # Jika proyekmu membuat target ggml Vulkan terpisah (nama bisa beda),
    # kamu bisa tambahkan baris berikut bila diperlukan:
    # target_link_libraries(cicero_llama PRIVATE ggml-vulkan)
else()
    message(STATUS "Building WITHOUT Vulkan backend")
endif()

target_include_directories(
        cicero_llama
        PRIVATE
        ${vulkan_hpp_SOURCE_DIR}/include
        ${llama_cpp_SOURCE_DIR}/include
)
