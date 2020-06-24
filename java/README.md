# IronOxide-Java

[![javadoc.io](https://javadoc.io/badge2/com.ironcorelabs/ironoxide-java/javadoc.io.svg)](https://javadoc.io/doc/com.ironcorelabs/ironoxide-java)

## Installation

### Library

Download the appropriate binary for your operating system on the [releases](https://github.com/IronCoreLabs/ironoxide-swig-bindings/releases) page:

- `.so` for (default or Debian-based) Linux
- `.dylib` for OSX

(Optional) Verify the PGP signature by running `gpg --verify ${file}.asc`. Our fingerprint is `83F9 49C6 E7E2 F0A2 9564 2DEE 62F5 7B1B 8792 8CAC`.

If you encounter issues related to linked libraries, you may be able to get a working library built for your system by [building from source](#build-from-source).

This binary should be placed in your Java library path, either by adding it to your `LD_LIBRARY_PATH` or the `-Djava.library.path` JVM option,
and can then be loaded by calling:

```java
java.lang.System.loadLibrary("ironoxide_java")
```

### Java SDK

The SDK is [published to Maven](https://search.maven.org/artifact/com.ironcorelabs/ironoxide-java).

## Usage

All the SDK classes can be imported from the `com.ironcorelabs.sdk` namespace.

## Documentation

Further documentation is available on [our docs site](https://ironcorelabs.com/docs/java/).

## Build from Source

### Prerequisites

- [Rust toolchain](https://www.rust-lang.org/tools/install) installed
- `JAVA_HOME` environment variable set
- `clang` installed

### Building

From the root of this repository run `cargo build -p ironoxide-java`. The resulting `target/debug/build/ironoxide-java-*/out/java` directory will have the JNI binding code for the Java side and `target/debug` will have the dynamic library file you need to pull into your Java code. It will be named `libironoxide_java.so` or `libironoxide_java.dylib` depending on your environment. This library will only work on the architecture from which it was built.
