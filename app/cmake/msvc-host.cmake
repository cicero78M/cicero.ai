# msvc-host.cmake — HOST toolchain untuk Windows (dipanggil dari Gradle/CMake)
# Jangan bergantung pada env VS; gunakan path absolut agar Gradle bisa menemukan tool.

set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_BUILD_TYPE Release)

# MSVC absolute paths (sesuaikan jika versinya beda)
set(_VSTOOL "C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.44.35207/bin/Hostx64/x64")
set(_WINSDK "C:/Program Files (x86)/Windows Kits/10/bin/10.0.22621.0/x64")  # rc.exe & mt.exe

set(CMAKE_C_COMPILER   "${_VSTOOL}/cl.exe")
set(CMAKE_CXX_COMPILER "${_VSTOOL}/cl.exe")
set(CMAKE_LINKER       "${_VSTOOL}/link.exe")
set(CMAKE_RC_COMPILER  "${_WINSDK}/rc.exe")
set(CMAKE_MT           "${_WINSDK}/mt.exe")

# Hindari try-compile bikin exe (linker host kadang gagal di env Gradle)
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# Jangan cari ke root-path Android
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY  NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE  NEVER)

# Flags rilis (MSVC)
set(CMAKE_C_FLAGS_RELEASE   "/O2 /DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "/O2 /DNDEBUG")

# Jika Ninja tidak ada di PATH, set manual:
# set(CMAKE_MAKE_PROGRAM "C:/Users/asus2/AppData/Local/Android/Sdk/cmake/3.22.1/bin/ninja.exe")
