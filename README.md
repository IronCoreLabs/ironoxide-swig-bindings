# IronCore Labs IronOxide Java SDK

SDK for using IronCore Labs from your Java server side applications. This library is a thin Rust shim that wraps the [Rust SDK](https://github.com/IronCoreLabs/ironoxide) and uses the [Rust Swig](https://github.com/Dushistov/rust_swig) toolset to generate JNI bindings to make it callable from the JVM.

## Generating the JNI Bindings

Prerequisites:

+ [Rust toolchain installed](https://www.rust-lang.org/tools/install)
+ `JAVA_HOME` environment variable set

From the root of this repository run `cargo build`. The resulting `java` directory will have the JNI binding code for the Java side and `target/debug` or `target/release` will have the dynamic library file you need to pull into your Java code. It will be `libironoxide_java.so` or `libironoxide_java.dylib` depending on your environment. This library will only work on the architecture from which it was built.

## Integration Testing

To test this we've produced a test harness that uses `sbt`. We used sbt because Scala is more familiar to us than Java, but rest assured that the bindings are bare Java with JNI.

The Scala tests are full integration tests for the SDK. As such, some tests need full, valid JWTs to validate against the hosted environment they're testing against. The first step necessary to run the tests is to decrypt the JWT private key and associated project/segment/service key IDs. This configuration should be in a `tests/src/test/resources/service-keys.conf` file. We've checked in an IronHide encrypted file which you can decrypted via `ironhide file:decrypt service-keys.conf.iron`.

Once you have the decrypted JWT config, from the `tests` directory run `sbt test`. If you get an error (either about the missing binary or a `NoClassDefFoundError` error) be sure you've built the Rust binding code using the above instructions.
