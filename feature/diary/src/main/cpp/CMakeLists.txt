cmake_minimum_required(VERSION 3.22.1)
project(diary_vulkan LANGUAGES CXX)
add_library(diary_vulkan SHARED VulkanBook.cpp)
target_compile_features(diary_vulkan PRIVATE cxx_std_17)
# Vulkan structs initialize sType first; all remaining aggregate fields are zeroed.
target_compile_options(diary_vulkan PRIVATE -Wall -Wextra -Wno-missing-field-initializers -Werror=return-type)
# The distributed APK is debuggable for local installation, but its per-frame
# Vulkan command path must not run as an unoptimized (-O0) C++ build.
target_compile_options(diary_vulkan PRIVATE $<$<CONFIG:Debug>:-O2>)
target_compile_definitions(diary_vulkan PRIVATE VK_USE_PLATFORM_ANDROID_KHR)
target_link_libraries(diary_vulkan PRIVATE vulkan android jnigraphics log)
# 16 KiB alignment for Android 15+ devices; also works with 4 KiB pages.
target_link_options(diary_vulkan PRIVATE "-Wl,-z,max-page-size=16384")
