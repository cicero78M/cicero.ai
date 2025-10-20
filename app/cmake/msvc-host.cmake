# msvc-host.cmake
# Host tool (vulkan-shaders-gen) dibangun sebagai aplikasi Windows (MSVC)

# Pastikan dianggap host-Windows, bukan Android
set(CMAKE_SYSTEM_NAME Windows)

# Paksa pakai tool MSVC yang sudah diset oleh "x64 Native Tools Command Prompt"
set(CMAKE_C_COMPILER cl)
set(CMAKE_CXX_COMPILER cl)
set(CMAKE_LINKER link)
set(CMAKE_RC_COMPILER rc)
set(CMAKE_MT mt)

# Hindari pencarian ke root-path Android
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE NEVER)

# Supaya try_compile tidak bikin exe (yang sebelumnya gagal link)
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# Release flags untuk MSVC (jangan pakai -O2)
set(CMAKE_C_FLAGS_RELEASE   "/O2 /DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "/O2 /DNDEBUG")
