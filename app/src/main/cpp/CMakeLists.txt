cmake_minimum_required(VERSION 3.22.1)

project("spatializer")

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
