1. Set the version name. The following files need to be updated:
    - `android/Cargo.toml`
    - `android/examples/example-instructions.md`
    - `android/gradle.properties`
    - `java/Cargo.toml`
    - `java/tests/version.sbt`
1. Use `sbt` to release the jar file.
    1. Make sure you have a sonatype account with write access to com.ironcorelabs. You'll need to type your username/password.
    1. Make sure you have the private key for the PGP account used to sign these releases, `E84BBF42`. You'll need the
        passphrase to unlock this keychain.
    1. Make sure you have push access to this GitHub repository, because sbt will commit changes and push them to origin.
    1. `sbt release` Sbt will build the Rust lib and Java bindings, then push the jar to Maven Central. Then it will create a new
        tag and push that to GitHub.
1. The new tag will cause GitHub Actions to run a release workflow, which will build the Rust lib and Android aar. The Rust lib
    will be uploaded to a GitHub release, and the aar will be uploaded to Maven Central.
