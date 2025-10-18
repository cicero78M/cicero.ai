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
set(GGML_VULKAN ON CACHE BOOL "" FORCE)

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
    llama
    ${log-lib}
    ${vulkan_lib}
)

target_include_directories(
    cicero_llama
    PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
)
