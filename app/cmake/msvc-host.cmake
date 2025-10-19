# app/cmake/msvc-host.cmake — toolchain untuk *host* Windows (bukan Android)
cmake_minimum_required(VERSION 3.18)
set(CMAKE_SYSTEM_NAME Windows)

# Jangan biarkan CMake melakukan try-compile executable (yang sering nyasar ke -lkernel32 ala MinGW)
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# === Lokasi VS Tools (cl/link) ===
# Bisa dioverride lewat -DCICERO_VCTOOLS_BIN=... bila mau.
# Akan coba juga beberapa environment variable umum dari VS dev prompt.
if(NOT CICERO_VCTOOLS_BIN)
    if(DEFINED ENV{CICERO_VCTOOLS_BIN})
        file(TO_CMAKE_PATH "$ENV{CICERO_VCTOOLS_BIN}" _ENV_VCTOOLS_BIN)
        set(CICERO_VCTOOLS_BIN "${_ENV_VCTOOLS_BIN}")
    endif()
endif()

if(NOT CICERO_VCTOOLS_BIN)
    if(DEFINED ENV{VCToolsInstallDir})
        set(_ENV_VCTOOLS "$ENV{VCToolsInstallDir}")
        file(TO_CMAKE_PATH "${_ENV_VCTOOLS}" _ENV_VCTOOLS_NORM)
        if(EXISTS "${_ENV_VCTOOLS_NORM}/bin/Hostx64/x64/cl.exe")
            set(CICERO_VCTOOLS_BIN "${_ENV_VCTOOLS_NORM}/bin/Hostx64/x64")
        endif()
    endif()
endif()

if(NOT CICERO_VCTOOLS_BIN)
    # Root default VS Build Tools 2022
    set(_VCTOOLS_ROOT "C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC")
    file(GLOB _MSVC_VERS LIST_DIRECTORIES TRUE "${_VCTOOLS_ROOT}/*")
    list(SORT _MSVC_VERS)
    list(REVERSE _MSVC_VERS)
    if(_MSVC_VERS)
        list(GET _MSVC_VERS 0 _MSVC_LATEST)
        set(CICERO_VCTOOLS_BIN "${_MSVC_LATEST}/bin/Hostx64/x64")
    endif()
endif()

# === Lokasi Windows 10 SDK (rc/mt) ===
# Bisa dioverride lewat -DCICERO_WINSDK_BIN=... bila mau.
# Akan coba juga environment variable dari VS dev prompt.
if(NOT CICERO_WINSDK_BIN)
    if(DEFINED ENV{CICERO_WINSDK_BIN})
        file(TO_CMAKE_PATH "$ENV{CICERO_WINSDK_BIN}" _ENV_WINSDK_BIN_DIRECT)
        set(CICERO_WINSDK_BIN "${_ENV_WINSDK_BIN_DIRECT}")
    endif()
endif()

if(NOT CICERO_WINSDK_BIN)
    if(DEFINED ENV{WindowsSdkVerBinPath})
        set(_ENV_WINSDK_BIN "$ENV{WindowsSdkVerBinPath}")
        file(TO_CMAKE_PATH "${_ENV_WINSDK_BIN}" _ENV_WINSDK_BIN_NORM)
        # WindowsSdkVerBinPath biasanya sudah menunjuk ke .../bin/<versi>/
        if(EXISTS "${_ENV_WINSDK_BIN_NORM}/x64/rc.exe")
            set(CICERO_WINSDK_BIN "${_ENV_WINSDK_BIN_NORM}/x64")
        endif()
    endif()
endif()

if(NOT CICERO_WINSDK_BIN)
    if(DEFINED ENV{WindowsSdkDir} AND DEFINED ENV{WindowsSDKVersion})
        set(_ENV_WINSDK_DIR "$ENV{WindowsSdkDir}")
        file(TO_CMAKE_PATH "${_ENV_WINSDK_DIR}" _ENV_WINSDK_DIR_NORM)
        set(_ENV_WINSDK_VER "$ENV{WindowsSDKVersion}")
        string(REGEX REPLACE "/$" "" _ENV_WINSDK_VER_TRIM "${_ENV_WINSDK_VER}")
        set(_ENV_WINSDK_BIN "${_ENV_WINSDK_DIR_NORM}/bin/${_ENV_WINSDK_VER_TRIM}")
        if(EXISTS "${_ENV_WINSDK_BIN}/x64/rc.exe")
            set(CICERO_WINSDK_BIN "${_ENV_WINSDK_BIN}/x64")
        endif()
    endif()
endif()

if(NOT CICERO_WINSDK_BIN)
    set(_WINSDK_ROOT "C:/Program Files (x86)/Windows Kits/10/bin")
    file(GLOB _WINSDK_VERS LIST_DIRECTORIES TRUE "${_WINSDK_ROOT}/10.*")
    list(SORT _WINSDK_VERS)
    list(REVERSE _WINSDK_VERS)
    if(_WINSDK_VERS)
        list(GET _WINSDK_VERS 0 _WINSDK_LATEST)
        set(CICERO_WINSDK_BIN "${_WINSDK_LATEST}/x64")
    endif()
endif()

# Fallback (kalau detection gagal, masih bisa di-set manual via -D)
if(NOT EXISTS "${CICERO_VCTOOLS_BIN}/cl.exe")
    message(FATAL_ERROR "cl.exe tidak ditemukan. Set -DCICERO_VCTOOLS_BIN ke folder bin/Hostx64/x64 VS Tools.")
endif()
if(NOT EXISTS "${CICERO_WINSDK_BIN}/rc.exe")
    message(FATAL_ERROR "rc.exe tidak ditemukan. Set -DCICERO_WINSDK_BIN ke folder Windows Kits .../bin/<versi>/x64.")
endif()

# Pakai MSVC untuk compile/link host tool
set(CMAKE_C_COMPILER   "${CICERO_VCTOOLS_BIN}/cl.exe")
set(CMAKE_CXX_COMPILER "${CICERO_VCTOOLS_BIN}/cl.exe")
set(CMAKE_LINKER       "${CICERO_VCTOOLS_BIN}/link.exe")
set(CMAKE_RC_COMPILER  "${CICERO_WINSDK_BIN}/rc.exe")
set(CMAKE_MT           "${CICERO_WINSDK_BIN}/mt.exe")

# Runtime CRT dinamis (sesuai default Android Studio untuk host tools)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL")

# Flag C++ yang aman
add_compile_options(/EHsc /permissive- /Zc:__cplusplus)

# Pastikan Ninja yang dipakai jelas (opsional tapi dianjurkan)
# Akan otomatis set jika path Ninja bawaan Android SDK ada.
set(_ASDK_NINJA "$ENV{LOCALAPPDATA}/Android/Sdk/cmake/3.22.1/bin/ninja.exe")
file(TO_CMAKE_PATH "${_ASDK_NINJA}" _ASDK_NINJA_NORM)
if(EXISTS "${_ASDK_NINJA_NORM}" AND NOT CMAKE_MAKE_PROGRAM)
    set(CMAKE_MAKE_PROGRAM "${_ASDK_NINJA_NORM}" CACHE FILEPATH "Ninja for host tools" FORCE)
endif()

# Hindari injeksi lib gaya MinGW
set(CMAKE_C_STANDARD_LIBRARIES  "" CACHE STRING "" FORCE)
set(CMAKE_CXX_STANDARD_LIBRARIES "" CACHE STRING "" FORCE)
