set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED True)
set(CMAKE_CXX_EXTENSIONS False)
set(CMAKE_VERBOSE_MAKEFILE ON)
project(sdk)
cmake_minimum_required(VERSION 3.9)
include_directories("${PROJECT_SOURCE_DIR}/include")
include_directories("${PROJECT_SOURCE_DIR}/../generated/sdk")
find_library(IRONOXIDE_LIB
 NAMES ironoxide_shared
 PATHS  "${PROJECT_SOURCE_DIR}/../../target/debug/")
add_executable(cpp-tests test.cpp include/acutest.h)
target_link_libraries(cpp-tests ${IRONOXIDE_LIB})



macro(print_all_variables)
    message(STATUS "print_all_variables------------------------------------------{")
    get_cmake_property(_variableNames VARIABLES)
    foreach (_variableName ${_variableNames})
        message(STATUS "${_variableName}=${${_variableName}}")
    endforeach()
    message(STATUS "print_all_variables------------------------------------------}")
endmacro()
print_all_variables()