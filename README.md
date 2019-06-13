# IronCore Labs IronOxide Java SDK

SDK for using IronCore Labs from your Java server side applications. This library is a thin Rust shim that wraps the [Rust SDK](https://github.com/IronCoreLabs/ironoxide) and uses the [Rust Swig](https://github.com/Dushistov/rust_swig) toolset to generate JNI bindings to make it callable from the JVM.

## Installation
### Library 

Download the appropriate binary for your operating system on the [releases](https://github.com/IronCoreLabs/ironoxide-java/releases) page. `.so` for Linux or `.dylib` for OSX.

> If you encounter issues related to linked libraries, you may be able to get a working library built for your system by [building from source](#build-from-source).

This binary should be placed in your Java library path,
either by adding it to your `LD_LIBRARY_PATH` or the `-Djava.library.path` JVM option, 
and can then be loaded by calling:

```
java.lang.System.loadLibrary("ironoxide_java")
```

Then all of the SDK classes can be imported from the `com.ironcorelabs.sdk` namespace.


### Java SDK

The SDK is [published to Maven](https://search.maven.org/artifact/com.ironcorelabs/ironoxide-java).	

## Documentation

Further documentation is available on [our docs site](https://docs.ironcorelabs.com/ironoxide-java-sdk/).

## Build from Source

Prerequisites:

+ [Rust toolchain installed](https://www.rust-lang.org/tools/install)
+ `JAVA_HOME` environment variable set
+ `clang` installed

From the root of this repository run `cargo build`. The resulting `java` directory will have the JNI binding code for the Java side and `target/debug` or `target/release` will have the dynamic library file you need to pull into your Java code. It will be `libironoxide_java.so` or `libironoxide_java.dylib` depending on your environment. This library will only work on the architecture from which it was built.

Copyright (c)  2019  IronCore Labs, Inc.
All rights reserved.
