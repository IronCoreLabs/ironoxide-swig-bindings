set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED True)
set(CMAKE_CXX_EXTENSIONS False)
project(sdk)
cmake_minimum_required(VERSION 3.9)
include_directories("${PROJECT_SOURCE_DIR}/include")
include_directories("${PROJECT_SOURCE_DIR}/../generated/sdk")
find_library(IRONOXIDE_LIB
 NAMES ironoxide_shared
 PATHS  "${PROJECT_SOURCE_DIR}/../../target/debug/")
add_executable(cpp-tests test.cpp include/acutest.h "${PROJECT_SOURCE_DIR}/../../target/debug/libironoxide_shared.so")
target_link_libraries(cpp-tests ${IRONOXIDE_LIB})

