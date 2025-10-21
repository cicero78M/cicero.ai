# msvc-host.cmake — toolchain host (Windows) untuk membangun binary host (vulkan-shaders-gen)
set(CMAKE_SYSTEM_NAME Windows)

# ==== SESUAIKAN VERSI YANG ADA DI MESINMU ====
set(_VSTOOL "C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.44.35207")
set(_WINSDK "C:/Program Files (x86)/Windows Kits/10")
set(_SDKVER "10.0.22621.0")
# =============================================

# Alat MSVC/WinSDK absolut
set(CMAKE_C_COMPILER   "${_VSTOOL}/bin/Hostx64/x64/cl.exe")
set(CMAKE_CXX_COMPILER "${_VSTOOL}/bin/Hostx64/x64/cl.exe")
set(CMAKE_LINKER       "${_VSTOOL}/bin/Hostx64/x64/link.exe")
set(CMAKE_RC_COMPILER  "${_WINSDK}/bin/${_SDKVER}/x64/rc.exe")
set(CMAKE_MT           "${_WINSDK}/bin/${_SDKVER}/x64/mt.exe")

# Include & Lib
set(_MSVC_INC   "${_VSTOOL}/include")
set(_UCRT_INC   "${_WINSDK}/Include/${_SDKVER}/ucrt")
set(_UM_INC     "${_WINSDK}/Include/${_SDKVER}/um")
set(_SHARED_INC "${_WINSDK}/Include/${_SDKVER}/shared")

set(_MSVC_LIB   "${_VSTOOL}/lib/x64")
set(_UCRT_LIB   "${_WINSDK}/Lib/${_SDKVER}/ucrt/x64")
set(_UM_LIB     "${_WINSDK}/Lib/${_SDKVER}/um/x64")

# ======= INI PENTING: pakai *_FLAGS_INIT + CACHE FORCE =======
# Masukkan /I ... ke FLAGS INIT lalu FORCE ke cache agar tertulis di command cl.exe
set(CMAKE_C_FLAGS_INIT   "/MD /I\"${_MSVC_INC}\" /I\"${_UCRT_INC}\" /I\"${_UM_INC}\" /I\"${_SHARED_INC}\"")
set(CMAKE_CXX_FLAGS_INIT "/MD /std:c++17 /I\"${_MSVC_INC}\" /I\"${_UCRT_INC}\" /I\"${_UM_INC}\" /I\"${_SHARED_INC}\"")

set(CMAKE_EXE_LINKER_FLAGS_INIT    "/LIBPATH:\"${_MSVC_LIB}\" /LIBPATH:\"${_UCRT_LIB}\" /LIBPATH:\"${_UM_LIB}\"")
set(CMAKE_SHARED_LINKER_FLAGS_INIT "/LIBPATH:\"${_MSVC_LIB}\" /LIBPATH:\"${_UCRT_LIB}\" /LIBPATH:\"${_UM_LIB}\"")

# FORCE ke cache supaya tidak ditimpa
set(CMAKE_C_FLAGS                "${CMAKE_C_FLAGS_INIT}"                CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS              "${CMAKE_CXX_FLAGS_INIT}"              CACHE STRING "" FORCE)
set(CMAKE_EXE_LINKER_FLAGS       "${CMAKE_EXE_LINKER_FLAGS_INIT}"       CACHE STRING "" FORCE)
set(CMAKE_SHARED_LINKER_FLAGS    "${CMAKE_SHARED_LINKER_FLAGS_INIT}"    CACHE STRING "" FORCE)

# Hindari try-compile membuat EXE (cukup lib)
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# Jangan nyasar ke root-path Android
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY  NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE  NEVER)
