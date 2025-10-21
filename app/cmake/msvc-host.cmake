# msvc-host.cmake — host toolchain (Windows) untuk membangun binary host seperti vulkan-shaders-gen

set(CMAKE_SYSTEM_NAME Windows)

# ==== SESUAIKAN JIKA PERLU ====
set(_VSTOOL "C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.44.35207")
set(_WINSDK "C:/Program Files (x86)/Windows Kits/10")
set(_SDKVER "10.0.22621.0")
# ====================================

# Tools absolut
set(CMAKE_C_COMPILER   "${_VSTOOL}/bin/Hostx64/x64/cl.exe")
set(CMAKE_CXX_COMPILER "${_VSTOOL}/bin/Hostx64/x64/cl.exe")
set(CMAKE_LINKER       "${_VSTOOL}/bin/Hostx64/x64/link.exe")
set(CMAKE_RC_COMPILER  "${_WINSDK}/bin/${_SDKVER}/x64/rc.exe")
set(CMAKE_MT           "${_WINSDK}/bin/${_SDKVER}/x64/mt.exe")

# Include & Lib MSVC/WinSDK
set(_MSVC_INC   "${_VSTOOL}/include")
set(_UCRT_INC   "${_WINSDK}/Include/${_SDKVER}/ucrt")
set(_UM_INC     "${_WINSDK}/Include/${_SDKVER}/um")
set(_SHARED_INC "${_WINSDK}/Include/${_SDKVER}/shared")

set(_MSVC_LIB   "${_VSTOOL}/lib/x64")
set(_UCRT_LIB   "${_WINSDK}/Lib/${_SDKVER}/ucrt/x64")
set(_UM_LIB     "${_WINSDK}/Lib/${_SDKVER}/um/x64")

# PAKSA flags via ENV agar selalu menempel ke setiap panggilan cl/link
set(ENV{CL}   "/I\"${_MSVC_INC}\" /I\"${_UCRT_INC}\" /I\"${_UM_INC}\" /I\"${_SHARED_INC}\" /std:c++17 /MD")
set(ENV{LINK} "/LIBPATH:\"${_MSVC_LIB}\" /LIBPATH:\"${_UCRT_LIB}\" /LIBPATH:\"${_UM_LIB}\"")

# Hindari try-compile bikin exe
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# Tambahan aman
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} /MD")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /std:c++17 /MD")

# Jangan nyasar ke root-path Android
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY  NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE  NEVER)
