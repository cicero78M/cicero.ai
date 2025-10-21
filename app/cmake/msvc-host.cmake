# msvc-host.cmake — HOST toolchain untuk Windows, dipanggil dari Gradle
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_BUILD_TYPE Release)

# Path MSVC & Windows SDK (cek versinya!)
set(_VSTOOL "C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.44.35207")
set(_WINSDK "C:/Program Files (x86)/Windows Kits/10")
set(_SDKVER "10.0.22621.0")

# Compiler / tools absolut
set(CMAKE_C_COMPILER   "${_VSTOOL}/bin/Hostx64/x64/cl.exe")
set(CMAKE_CXX_COMPILER "${_VSTOOL}/bin/Hostx64/x64/cl.exe")
set(CMAKE_LINKER       "${_VSTOOL}/bin/Hostx64/x64/link.exe")
set(CMAKE_RC_COMPILER  "${_WINSDK}/bin/${_SDKVER}/x64/rc.exe")
set(CMAKE_MT           "${_WINSDK}/bin/${_SDKVER}/x64/mt.exe")

# ENV untuk header & libs (mengganti vcvars64.bat)
set(ENV{INCLUDE}
  "${_VSTOOL}/include;
   ${_WINSDK}/Include/${_SDKVER}/ucrt;
   ${_WINSDK}/Include/${_SDKVER}/um;
   ${_WINSDK}/Include/${_SDKVER}/shared"
)
set(ENV{LIB}
  "${_VSTOOL}/lib/x64;
   ${_WINSDK}/Lib/${_SDKVER}/ucrt/x64;
   ${_WINSDK}/Lib/${_SDKVER}/um/x64"
)

# Hindari try-compile link exe
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# Flags rilis (MSVC)
set(CMAKE_C_FLAGS_RELEASE   "/O2 /DNDEBUG /MD")
set(CMAKE_CXX_FLAGS_RELEASE "/O2 /DNDEBUG /MD /std:c++17")

# Jangan nyasar ke root-path Android
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY  NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE  NEVER)
