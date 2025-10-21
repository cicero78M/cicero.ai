# === Versi & lokasi toolset / SDK ===
set(_SDKVER "10.0.22621.0")
set(_MSVC   "C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.44.35207")
set(_WINSDK "C:/Program Files (x86)/Windows Kits/10")

# === Compiler & tools MSVC untuk host ===
set(CMAKE_C_COMPILER   "${_MSVC}/bin/Hostx64/x64/cl.exe")
set(CMAKE_CXX_COMPILER "${_MSVC}/bin/Hostx64/x64/cl.exe")
set(CMAKE_AR           "${_MSVC}/bin/Hostx64/x64/lib.exe")
set(CMAKE_RC_COMPILER  "${_WINSDK}/bin/${_SDKVER}/x64/rc.exe")
set(CMAKE_MT           "${_WINSDK}/bin/${_SDKVER}/x64/mt.exe")

# Hindari try-compile bikin exe (biar gak perlu link kernel32 saat deteksi)
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# === Inject include path untuk header standar & Windows SDK ===
set(_INC_MSVC   "/I\"${_MSVC}/include\"")
set(_INC_UCRT   "/I\"${_WINSDK}/Include/${_SDKVER}/ucrt\"")
set(_INC_UM     "/I\"${_WINSDK}/Include/${_SDKVER}/um\"")
set(_INC_SHARED "/I\"${_WINSDK}/Include/${_SDKVER}/shared\"")

set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}   ${_INC_MSVC} ${_INC_UCRT} ${_INC_UM} ${_INC_SHARED}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${_INC_MSVC} ${_INC_UCRT} ${_INC_UM} ${_INC_SHARED} /std:c++17 /EHsc")

# === Inject lib path untuk link step ===
set(_LIB_MSVC "/LIBPATH:\"${_MSVC}/lib/x64\"")
set(_LIB_UCRT "/LIBPATH:\"${_WINSDK}/Lib/${_SDKVER}/ucrt/x64\"")
set(_LIB_UM   "/LIBPATH:\"${_WINSDK}/Lib/${_SDKVER}/um/x64\"")

set(CMAKE_EXE_LINKER_FLAGS    "${CMAKE_EXE_LINKER_FLAGS}    ${_LIB_MSVC} ${_LIB_UCRT} ${_LIB_UM}")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${_LIB_MSVC} ${_LIB_UCRT} ${_LIB_UM}")

# (Opsional) Samakan runtime: /MDd di Debug, /MD di Release
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL")
