cmake_minimum_required(VERSION 3.22.1)
project(cicero_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable the native llama.cpp implementation and its dependencies.

include(FetchContent)

# Fetch a known-good revision of llama.cpp.  We disable optional
# components that are not needed for the Android bridge to keep build
# times and binary size manageable.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER_GUI OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
option(CICERO_ENABLE_VULKAN "Enable the GGML Vulkan backend" ON)

find_program(CICERO_NINJA_EXECUTABLE
    NAMES ninja ninja.exe
    DOC "Path to the Ninja build tool required for the Vulkan shader generator"
)

if (NOT CICERO_NINJA_EXECUTABLE)
    message(WARNING "Ninja build tool not found; disabling GGML Vulkan backend")
    set(CICERO_ENABLE_VULKAN OFF)
else()
    # Propagate the Ninja path so nested CMake invocations (such as the
    # ggml/vulkan shader generator) can locate the executable even when it is
    # not present on the user's PATH – a common scenario on Windows Android
    # Studio installations.
    set(CMAKE_MAKE_PROGRAM "${CICERO_NINJA_EXECUTABLE}" CACHE FILEPATH
        "Ninja executable used for Vulkan shader generation" FORCE)
    set(Ninja_EXECUTABLE "${CICERO_NINJA_EXECUTABLE}" CACHE FILEPATH
        "Ninja executable used for Vulkan shader generation" FORCE)

    # Ensure the directory that contains the Ninja executable is visible to
    # downstream configure steps that run in a fresh CMake process.  Without
    # this, the shader code generator invoked from llama.cpp fails to locate
    # the build tool on Windows where the Android Studio bundled ninja is not
    # on the global PATH.
    get_filename_component(_cicero_ninja_dir "${CICERO_NINJA_EXECUTABLE}" DIRECTORY)
    if (_cicero_ninja_dir)
        list(APPEND CMAKE_PROGRAM_PATH "${_cicero_ninja_dir}")
        list(REMOVE_DUPLICATES CMAKE_PROGRAM_PATH)
        set(CMAKE_PROGRAM_PATH "${CMAKE_PROGRAM_PATH}" CACHE STRING
            "Program search path containing Ninja" FORCE)

        string(FIND "$ENV{PATH}" "${_cicero_ninja_dir}" _cicero_path_index)
        if (_cicero_path_index EQUAL -1)
            if (WIN32)
                set(ENV{PATH} "${_cicero_ninja_dir};$ENV{PATH}")
            else()
                set(ENV{PATH} "${_cicero_ninja_dir}:$ENV{PATH}")
            endif()
        endif()
    endif()
endif()

set(GGML_VULKAN ${CICERO_ENABLE_VULKAN} CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG 1bb4f43380944e94c9a86e305789ba103f5e62bd
    GIT_SHALLOW TRUE
)

FetchContent_MakeAvailable(llama_cpp)

add_library(
    cicero_llama
    SHARED
    llama_bridge.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    cicero_llama
    PRIVATE
    llama
    ${log-lib}
)

if (CICERO_ENABLE_VULKAN)
    find_library(
        vulkan_lib
        vulkan
    )

    if (NOT vulkan_lib)
        message(FATAL_ERROR "Failed to locate the Vulkan loader library")
    endif()

    target_link_libraries(
        cicero_llama
        PRIVATE
        ${vulkan_lib}
    )
else()
    message(STATUS "Building without GGML Vulkan backend")
endif()

target_include_directories(
    cicero_llama
    PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
)
