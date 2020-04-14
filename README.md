# IronCore Labs IronOxide Bindings

[![javadoc.io](https://javadoc.io/badge2/com.ironcorelabs/ironoxide-java/javadoc.io.svg)](https://javadoc.io/doc/com.ironcorelabs/ironoxide-java)

This library is a thin Rust shim that wraps the [Rust SDK](https://github.com/IronCoreLabs/ironoxide) and uses the [Rust Swig](https://github.com/Dushistov/rust_swig) toolset to generate bindings. It currently contains SDKs for Java and Android.

# IronOxide-Java

## Installation

### Library

Download the appropriate binary for your operating system on the [releases](https://github.com/IronCoreLabs/ironoxide-java/releases) page:

- `.so` for (default or Debian-based) Linux
- `.dylib` for OSX
- `.tar.gz` for CentOS, RHEL, or similar Linux

(Optional) Verify the PGP signature by running `gpg --verify ${file}.asc`. Our fingerprint is `83F9 49C6 E7E2 F0A2 9564 2DEE 62F5 7B1B 8792 8CAC`.

> If you encounter issues related to linked libraries, you may be able to get a working library built for your system by [building from source](#build-from-source).

This binary should be placed in your Java library path,
either by adding it to your `LD_LIBRARY_PATH` or the `-Djava.library.path` JVM option,
and can then be loaded by calling:

```java
java.lang.System.loadLibrary("ironoxide_java")
```

### Java SDK

The SDK is [published to Maven](https://search.maven.org/artifact/com.ironcorelabs/ironoxide-java).

## Usage

All the SDK classes can be imported from the `com.ironcorelabs.sdk` namespace.

## Documentation

Further documentation is available on [our docs site](https://docs.ironcorelabs.com/ironoxide-java-sdk/).

## Build from Source

### Prerequisites

- [Rust toolchain](https://www.rust-lang.org/tools/install) installed
- `JAVA_HOME` environment variable set
- `clang` installed

### Building

From the root of this repository run `cargo build -p ironoxide-java`. The resulting `target/debug/build/ironoxide-java-*/out/java` directory will have the JNI binding code for the Java side and `target/debug` will have the dynamic library file you need to pull into your Java code. It will be named `libironoxide_java.so` or `libironoxide_java.dylib` depending on your environment. This library will only work on the architecture from which it was built.

# IronOxide-Android

## Using IronOxide-Android

In order to make calls to the IronCore service, you must add a dependency for ironoxide-android, load the library, and provide authentication.

### Adding a dependency

IronOxide-Android is published to a custom maven repository. To pull from this repository, add the following to your project's "build.gradle":

```java
allprojects {
    repositories {
        maven {
            url "https://oss.sonatype.org/content/repositories/snapshots"
        }
    }
}
```

Then add the following to your module's "build.gradle":

```java
dependencies {
    implementation 'com.ironcorelabs:ironoxide-android:<VERSION>@aar'
}
```

### Loading the library

To load the `ironoxide-android` library in Java, use the following:

```java
java.lang.System.loadLibrary("ironoxide_android");
```

### Authenticating

To make calls, you must create a project and segment in the IronCore Admin Console. Once created, you can use a tool such as [ironoxide-cli](https://github.com/IronCoreLabs/ironoxide-cli) to create users and devices. You can then read in a device with the `DeviceContext.fromJsonString()` function and use it to initialize an IronOxide instance. An example of this is available in the [Example Application](/android/examples/Example_Application/) in "MainActivity.java".

## Build from Source

### Prerequisites

- [Rust toolchain](https://www.rust-lang.org/tools/install) installed
- [cross](https://github.com/rust-embedded/cross) installed.
  - We currently only support `cross` v0.1.16. This can be installed with `cargo install cross --version 0.1.16`
- Android SDK 29. You can get the command line SDK [here](https://developer.android.com/studio) (scroll down to "Command line tools only") and then use `sdkmanager` (found in tools/bin) to install additional prerequisites:

  ```bash
  ./sdkmanager --sdk_root=PATH_TO_SDK_INSTALL_LOCATION --install "build-tools;29.0.3" platform-tools "platforms;android-29"
  ```

- Edit `android/local.properties` to point the `sdk.dir` to the location of the Android SDK.

### Building

From `ironoxide-java/android`, run `build.sh`. The output AAR file will be in `android/ironoxide-android/build/outputs/aar`.

## Running Connected Tests

To run Android connected tests, you must either have an emulator running or a compatible Android phone connected. The tests will require artifacts from the `cross` build, so begin by running the steps in [Build from Source](#build-from-source-1). This will create the java files and the `.so` files required for the `x86`, `x86_64`, and `arm64-v8a` architectures. If testing on a different architecture, you can find the Rust target to compile to [here](https://forge.rust-lang.org/release/platform-support.html). Run the tests from the repository root with this command:

```bash
android/gradlew connectedCheck
```

# License

Copyright (c) 2020 IronCore Labs, Inc.
All rights reserved.
