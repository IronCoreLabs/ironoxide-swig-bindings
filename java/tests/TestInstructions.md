# Integration Testing

To test the JNI bindings we've produced a test harness that uses `sbt`. We used sbt because Scala is more familiar to us than Java, but rest assured that the bindings are bare Java with JNI.

The Scala tests are full integration tests for the SDK. As such, some tests need full, valid JWTs to validate against the hosted environment they're testing against. The first step necessary to run the tests is to decrypt the JWT private key and associated project/segment/service key IDs. This configuration should be in a `tests/src/test/resources/service-keys.conf` file. We've checked in an IronHide encrypted file which you can decrypted via `ironhide file:decrypt service-keys.conf.iron`.

Once you have the decrypted JWT config, from the `tests` directory run `sbt test`. If you get an error (either about the missing binary or a `NoClassDefFoundError` error) be sure you've built the Rust binding code using the above instructions.