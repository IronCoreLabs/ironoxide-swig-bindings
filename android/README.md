# IronOxide-Android

[![javadoc.io](https://javadoc.io/badge2/com.ironcorelabs/ironoxide-java/javadoc.io.svg)](https://javadoc.io/doc/com.ironcorelabs/ironoxide-java)

## Using IronOxide-Android

In order to make calls to the IronCore service, you must add a dependency for ironoxide-android, load the library, and provide authentication.

### Adding a dependency

Add the following to your module's "build.gradle":

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

To make calls, you must create a project and segment in the IronCore [Admin Console](https://admin.ironcorelabs.com). These are necessary to create an IronCore JWT that can be used to create users and generate devices.
The JWT must use either the ES256 or RS256 algorithm and have a payload similar to IronOxide's [JwtClaims](https://docs.rs/ironoxide/0.23.0/ironoxide/user/struct.JwtClaims.html).

Alternatively, once you have created a project and a segment, you can use a tool such as [ironoxide-cli](https://github.com/IronCoreLabs/ironoxide-cli) to create users and devices directly.
You can then read in a device with the `DeviceContext.fromJsonString()` function and use it to initialize an `IronOxide` instance. An example of this is available in the
[Example Application](/android/examples/Example_Application/) in "MainActivity.java".

## Build from Source

### Prerequisites

> There's a `shell.nix` that can be used via `nix-shell` that'll bootstrap an environment where you can test with a plugged in local Android device.

- Install [Rust toolchain](https://www.rust-lang.org/tools/install).
- Install Android SDK 36. You can get the command line SDK [here](https://developer.android.com/studio) (scroll down to "Command line tools only").
  - Note: **The extracted `tools` folder must follow a specific folder hierarchy. We recommend `AndroidCLI/cmdline-tools/tools`**.
- Install Android 36 build and platform tools. This can be done with `sdkmanager` (found in `tools/bin`):

  ```bash
  ./sdkmanager "build-tools;36.0.0" "platform-tools" "platforms;android-36"
  ```

- Create the file `$HOME/.gradle/gradle.properties` and add the line `sdk.dir=PATH_TO_ANDROID_CLI_FOLDER`.

### Building

From the repository root, run `android/build.sh`.

This will compile IronOxide-Android for `x86_64`, `arm64-v8a`, and `armeabi-v7a`, covering both emulators and physical devices. The compiled library and generated Java files will be put into `android/ironoxide-android/src/main`.

## Testing

### Prerequisites

- Successfully run `build.sh` by following the steps in [Build from Source](#build-from-source-1).
- An Android emulator running, or a compatible Android phone connected.

#### Emulator Testing

To start an emulator using the command line tools, follow these steps from the folder `AndroidCLI/cmdline-tools/tools/bin`:

1. `./sdkmanager "emulator" "system-images;android-36;google_apis;x86_64"`
2. `./avdmanager create avd -n pixel -k "system-images;android-36;google_apis;x86_64" -d pixel`
3. `../../../emulator/emulator -avd pixel -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim`

The emulator may take some time to boot, but the output will include `emulator: INFO: boot completed` when it has completed. You will need to use a different terminal to run the tests.

#### Physical Device Testing

You can also run tests on a physical Android device connected via USB or Wi-Fi debugging. The `nix-shell` environment is set up to support connected device testing. Ensure USB debugging is enabled on the device and it appears in `adb devices`.

### Running the Connected Android Tests

Run the tests from the `android` folder with:

```bash
./gradlew connectedAndroidTest
```
