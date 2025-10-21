# msvc-host.cmake
# Host tool (vulkan-shaders-gen) dibangun sebagai aplikasi Windows (MSVC)

# Pastikan dianggap host-Windows, bukan Android
set(CMAKE_SYSTEM_NAME Windows)

# Paksa pakai tool MSVC. Secara default file ini diasumsikan dijalankan dari
# "x64 Native Tools Command Prompt", tetapi Android Studio seringkali tidak
# mewariskan PATH tersebut. Di bawah ini kita coba menemukan instalasi Visual
# Studio secara otomatis sehingga host tool dapat dibangun tanpa menyiapkan
# shell khusus terlebih dahulu.

# Cari direktori instalasi Visual Studio/MSVC yang mengandung cl.exe.
set(_cicero_msvc_bin_dir "")

macro(_cicero_set_msvc_bin_dir candidate)
  if(NOT _cicero_msvc_bin_dir AND EXISTS "${candidate}/cl.exe")
    set(_cicero_msvc_bin_dir "${candidate}" CACHE PATH "MSVC bin directory" FORCE)
  endif()
endmacro()

if(DEFINED ENV{VCToolsInstallDir})
  file(TO_CMAKE_PATH "$ENV{VCToolsInstallDir}" _cicero_vc_tools_dir)
  _cicero_set_msvc_bin_dir("${_cicero_vc_tools_dir}/bin/Hostx64/x64")
  _cicero_set_msvc_bin_dir("${_cicero_vc_tools_dir}/bin/HostX64/x64")
endif()

if(NOT _cicero_msvc_bin_dir AND DEFINED ENV{VCINSTALLDIR})
  file(TO_CMAKE_PATH "$ENV{VCINSTALLDIR}" _cicero_vc_install_dir)
  file(GLOB _cicero_msvc_versions
    LIST_DIRECTORIES TRUE
    "${_cicero_vc_install_dir}/Tools/MSVC/*")
  if(_cicero_msvc_versions)
    list(SORT _cicero_msvc_versions)
    list(REVERSE _cicero_msvc_versions)
    foreach(_cicero_msvc_version ${_cicero_msvc_versions})
      _cicero_set_msvc_bin_dir("${_cicero_msvc_version}/bin/Hostx64/x64")
      _cicero_set_msvc_bin_dir("${_cicero_msvc_version}/bin/HostX64/x64")
      if(_cicero_msvc_bin_dir)
        break()
      endif()
    endforeach()
  endif()
endif()

if(NOT _cicero_msvc_bin_dir)
  set(_cicero_vswhere "$ENV{ProgramFiles(x86)}/Microsoft Visual Studio/Installer/vswhere.exe")
  if(EXISTS "${_cicero_vswhere}")
    execute_process(
      COMMAND "${_cicero_vswhere}" -latest -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
      OUTPUT_VARIABLE _cicero_vs_installation
      OUTPUT_STRIP_TRAILING_WHITESPACE
      ERROR_QUIET)
    if(_cicero_vs_installation)
      file(TO_CMAKE_PATH "${_cicero_vs_installation}" _cicero_vs_install_dir)
      file(GLOB _cicero_msvc_versions
        LIST_DIRECTORIES TRUE
        "${_cicero_vs_install_dir}/VC/Tools/MSVC/*")
      if(_cicero_msvc_versions)
        list(SORT _cicero_msvc_versions)
        list(REVERSE _cicero_msvc_versions)
        foreach(_cicero_msvc_version ${_cicero_msvc_versions})
          _cicero_set_msvc_bin_dir("${_cicero_msvc_version}/bin/Hostx64/x64")
          _cicero_set_msvc_bin_dir("${_cicero_msvc_version}/bin/HostX64/x64")
          if(_cicero_msvc_bin_dir)
            break()
          endif()
        endforeach()
      endif()
    endif()
  endif()
endif()

if(NOT _cicero_msvc_bin_dir)
  find_program(_cicero_cl cl.exe)
  if(_cicero_cl)
    get_filename_component(_cicero_msvc_bin_dir "${_cicero_cl}" DIRECTORY)
  endif()
endif()

if(NOT _cicero_msvc_bin_dir)
  message(FATAL_ERROR "Tidak dapat menemukan instalasi MSVC (cl.exe). Pastikan Visual Studio Build Tools terpasang atau set PATH/VC environment sebelum membangun.")
endif()

file(TO_CMAKE_PATH "${_cicero_msvc_bin_dir}" _cicero_msvc_bin_dir)

set(_cicero_cl "${_cicero_msvc_bin_dir}/cl.exe")
set(_cicero_link "${_cicero_msvc_bin_dir}/link.exe")
set(_cicero_lib "${_cicero_msvc_bin_dir}/lib.exe")

set(CMAKE_C_COMPILER   "${_cicero_cl}"   CACHE FILEPATH "" FORCE)
set(CMAKE_CXX_COMPILER "${_cicero_cl}"   CACHE FILEPATH "" FORCE)
set(CMAKE_LINKER       "${_cicero_link}" CACHE FILEPATH "" FORCE)
set(CMAKE_AR           "${_cicero_lib}"  CACHE FILEPATH "" FORCE)

# rc.exe dan mt.exe biasanya berada di Windows SDK. Cari berdasarkan environment
# variable yang umum diset oleh instalasi Visual Studio/Windows SDK.
if(DEFINED ENV{WindowsSdkDir} AND DEFINED ENV{WindowsSDKVersion})
  file(TO_CMAKE_PATH "$ENV{WindowsSdkDir}" _cicero_sdk_dir)
  set(_cicero_sdk_bin "${_cicero_sdk_dir}/bin/$ENV{WindowsSDKVersion}/x64")
  if(EXISTS "${_cicero_sdk_bin}/rc.exe")
    set(CMAKE_RC_COMPILER "${_cicero_sdk_bin}/rc.exe" CACHE FILEPATH "" FORCE)
  endif()
  if(EXISTS "${_cicero_sdk_bin}/mt.exe")
    set(CMAKE_MT "${_cicero_sdk_bin}/mt.exe" CACHE FILEPATH "" FORCE)
  endif()
endif()

if(NOT CMAKE_RC_COMPILER)
  find_program(_cicero_rc rc.exe)
  if(_cicero_rc)
    set(CMAKE_RC_COMPILER "${_cicero_rc}" CACHE FILEPATH "" FORCE)
  endif()
endif()

if(NOT CMAKE_MT)
  find_program(_cicero_mt mt.exe)
  if(_cicero_mt)
    set(CMAKE_MT "${_cicero_mt}" CACHE FILEPATH "" FORCE)
  endif()
endif()

# Hindari pencarian ke root-path Android
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE NEVER)

# Supaya try_compile tidak bikin exe (yang sebelumnya gagal link)
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# Release flags untuk MSVC (jangan pakai -O2)
set(CMAKE_C_FLAGS_RELEASE   "/O2 /DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "/O2 /DNDEBUG")
