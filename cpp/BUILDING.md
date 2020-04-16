# C++ Binding instructions

The building of the CPP library is currently not done by CI, so if you're wanting to produce C/C++ bindings and the associated
library you'll need to follow the following steps.

## Prerequisites

- C++ 17 or greater installed. Most modern gcc or clang compilers should do
- C++ Boost installed. Search up directions for your distribution if you're not sure.
- CMake >= 3.9.
- Make

## Building

- Run `cmake .` in this directory.
- Run `make`.

This should produce a binary `cpp-tests` as well as all the header files in `generated/sdk`.

The dynamic library will be in `../target/release`.
