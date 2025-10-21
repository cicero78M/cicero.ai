# --- Lokasi MSVC & Windows SDK (cek versi, sesuaikan jika beda)
set(_VSTOOL "C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.44.35207")
set(_WINSDK "C:/Program Files (x86)/Windows Kits/10")
set(_SDKVER "10.0.22621.0")

# --- Alat kompilasi host (MSVC)
set(CMAKE_C_COMPILER   "${_VSTOOL}/bin/Hostx64/x64/cl.exe")
set(CMAKE_CXX_COMPILER "${_VSTOOL}/bin/Hostx64/x64/cl.exe")
set(CMAKE_AR           "${_VSTOOL}/bin/Hostx64/x64/lib.exe")
set(CMAKE_MT           "${_WINSDK}/bin/${_SDKVER}/x64/mt.exe")
set(CMAKE_RC_COMPILER  "${_WINSDK}/bin/${_SDKVER}/x64/rc.exe")

# Hindari try-compile membuat exe (cukup static lib)
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# Runtime & standar C++
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL")
add_compile_options(/std:c++17 /EHsc)

# --- INJECT search path untuk MSVC (KRUSIAL)
#   MSVC mencari headers via ENV INCLUDE (pakai ';'),
#   dan libraries via ENV LIB (pakai ';').
set(ENV{INCLUDE} "${_VSTOOL}/include;${_WINSDK}/Include/${_SDKVER}/ucrt;${_WINSDK}/Include/${_SDKVER}/um;${_WINSDK}/Include/${_SDKVER}/shared")
set(ENV{LIB}     "${_VSTOOL}/lib/x64;${_WINSDK}/Lib/${_SDKVER}/ucrt/x64;${_WINSDK}/Lib/${_SDKVER}/um/x64")

# (Opsional) Tambahkan juga LIBPATH di flag linker untuk jaga-jaga:
set(CMAKE_EXE_LINKER_FLAGS_INIT
  "/LIBPATH:\"${_VSTOOL}/lib/x64\" /LIBPATH:\"${_WINSDK}/Lib/${_SDKVER}/ucrt/x64\" /LIBPATH:\"${_WINSDK}/Lib/${_SDKVER}/um/x64\"")
set(CMAKE_SHARED_LINKER_FLAGS_INIT "${CMAKE_EXE_LINKER_FLAGS_INIT}")
