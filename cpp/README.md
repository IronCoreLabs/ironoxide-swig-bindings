# IronOxide-Cpp

## Build from Source

### Prerequisites

- C++ 17 or greater installed. Most modern gcc or clang compilers should do.
- CMake >= 3.9.
- Make

### Building

From the `cpp` directory, run:

```
cmake .
make
```

This will output all of the header files to `ironoxide-swig-bindings/cpp/generated/sdk` and the dynamic library `libironoxide` to `ironoxide-swig-bindings/target/release`.
The file extension of the dynamic library will depend on your operating system (`.so` for Linux, `.dylib` for OSX, etc.).

## Testing

After running the steps in [Building](#building), the C++ tests can be run from the `cpp` directory with `./cpp-tests`.
