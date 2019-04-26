# JNI Bindings

Generating the JNI bindings for `ironrust` is done with this project. To do this you'll need `clang` installed and need to have `JAVA_HOME` set.

## Example

```bash
JAVA_HOME="/usr/lib/jvm/java-8-openjdk/" cargo build
```

## What's produced

The `java` directory will have the JNI binding code for the java side and `target/debug` or `target/release` will have the dynamic lib file you're going to need. It will be `libironrust_java.so` or `libironrust_java.dylib` depending on your environment.

## Testing

To test this we've produced a test harness that uses `sbt`. We used sbt because Scala is more familiar to us than Java, but rest assured that the bindings are bare Java with JNI.

The Scala tests are full integration tests for the SDK. As such, some tests need full, valid JWTs as we're hitting the staging environment for our tests. So the first step necessary to run the tests is to decrypt the JWT private key and associated project/segment/service key IDs. This configuration should be in a `./java/scala/src/test/resources/service-keys.conf` file. We've checked in an IronHide encrypted file which you can decrypted via `ironhide file:decrypt service-keys.conf.iron`.

Once you have the decrypted JWT config, from the `scala` dir just run `sbt test`. If you get an error about the library missing be sure you've built the Rust binding code using the above instructions.

### NoClassDefFoundError

`java.lang.BootstrapMethodError: java.lang.NoClassDefFoundError:` will happen if you don't have the generated java in your tree. Make sure you do the cargo build.
