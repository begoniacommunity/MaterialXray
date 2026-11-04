cmake_minimum_required(VERSION 3.22.1)

project(xray_launcher)

# Debug info names absolute paths, and the linker hashes it into the build ID before the APK strips
# it; mapping them away keeps the libraries identical wherever the project and the NDK live.
add_compile_options(
    "-ffile-prefix-map=${CMAKE_SOURCE_DIR}=."
    "-ffile-prefix-map=${CMAKE_BINARY_DIR}=."
    "-ffile-prefix-map=${ANDROID_NDK}=/ndk"
)

add_library(xray_launcher SHARED xray_launcher.c)

target_link_libraries(xray_launcher log)

# Root mode execs this from nativeLibraryDir, where the installer only extracts lib*.so files.
add_executable(xrayroot xray_root_exec.c)
set_target_properties(xrayroot PROPERTIES OUTPUT_NAME "libxrayroot.so")
