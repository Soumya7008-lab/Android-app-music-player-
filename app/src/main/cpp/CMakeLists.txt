cmake_minimum_required(VERSION 3.22.1)

project("spatializer")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Os -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")

add_library(
        spatializer
        SHARED
        AudioEngine.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        spatializer
        ${log-lib}
)
