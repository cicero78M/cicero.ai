# app/cmake/posix-host.cmake
set(CMAKE_SYSTEM_NAME Linux)

# Prefer clang/clang++, fallback to gcc/g++
find_program(HOST_CC clang)
find_program(HOST_CXX clang++)
if(NOT HOST_CC OR NOT HOST_CXX)
  find_program(HOST_CC gcc)
  find_program(HOST_CXX g++)
endif()
if(NOT HOST_CC OR NOT HOST_CXX)
  message(FATAL_ERROR "Host compiler tidak ditemukan (clang/gcc).")
endif()

set(CMAKE_C_COMPILER   ${HOST_CC})
set(CMAKE_CXX_COMPILER ${HOST_CXX})

find_program(HOST_NINJA ninja)
if(NOT HOST_NINJA)
  message(FATAL_ERROR "Ninja tidak ditemukan. Install ninja-build.")
endif()
set(CMAKE_MAKE_PROGRAM ${HOST_NINJA})
