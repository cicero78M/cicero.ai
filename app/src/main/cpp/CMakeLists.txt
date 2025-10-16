cmake_minimum_required(VERSION 3.22.1)
project(cicero_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

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
    ${log-lib}
)

# TODO: Tambahkan sumber asli llama.cpp serta dependensinya di sini
# ketika Anda siap mengintegrasikannya dengan native build.
