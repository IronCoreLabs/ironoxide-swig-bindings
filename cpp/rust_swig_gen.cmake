message(STATUS "Generate rust swig headers")

execute_process(
  COMMAND cargo check --release  
  WORKING_DIRECTORY ${RUST_BUILD_CWD}
  RESULT_VARIABLE retcode
  )
if(NOT "${retcode}" STREQUAL "0")
  message(FATAL_ERROR "cargo: cargo check --release   failed")
endif()

set(RUST_SWIG_OUTPUT_DIR "${SRCDIR}/generated/sdk")
file(GLOB RUST_SWIG_CPP_HEADERS "${RUST_SWIG_OUTPUT_DIR}/*.h*")
set(RUST_SWIG_SRCS /home/colt/src/coltfred/ironoxide-java/common/lib.rs.in)
set(CARGO_CMD cargo)
set(CARGO_ADDON_ARGS )
set(RUST_TARGET )
configure_file(${SRCDIR}/cmake/rust_swig_regen_headers.cmake.in
               ${BINDIR}/rust_swig_regen_headers.cmake @ONLY)
