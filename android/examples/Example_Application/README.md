# IronOxide-Android Example Application

This application provides a simple UI for encrypting/decrypting data with IronCore's Android SDK. Its purpose is to give an example of depending on ironoxide-android, loading the native library, and making various SDK calls.

<img src="Screenshot.png" alt="Screenshot" width="300"/>

## Running locally

### Prerequisites

- Install Android SDK 36. You can get the command line SDK [here](https://developer.android.com/studio) (scroll down to "Command line tools only").
  - Note: **The extracted `tools` folder must follow a specific folder hierarchy. We recommend `AndroidCLI/cmdline-tools/tools`**.
- Install Android 36 build and platform tools. This can be done with `sdkmanager` (found in `tools/bin`):
  ```bash
  ./sdkmanager "build-tools;36.0.0" "platform-tools" "platforms;android-36"
  ```
- Set `ANDROID_HOME` to point to your Android SDK root, or create/edit `$HOME/.gradle/gradle.properties` and add the line `sdk.dir=PATH_TO_ANDROID_CLI_FOLDER`.
  - If using the Nix dev shell from the `android/` directory, `ANDROID_HOME` is set automatically.
- An Android emulator running, or a compatible Android phone connected (minSdkVersion 31).
  - To start an emulator using the command line tools, follow these steps from the folder `AndroidCLI/cmdline-tools/tools/bin`:
    1. `./sdkmanager "emulator" "system-images;android-36;google_apis;x86_64"`
    1. `./avdmanager create avd -n pixel_8 -k "system-images;android-36;google_apis;x86_64" -d pixel_8`
    1. `../../../emulator/emulator -avd pixel_8 -no-snapshot -noaudio -no-boot-anim`

### Running the app

1. From the Example_Application root, run `./gradlew installDebug`.
1. From the emulator's app drawer, select the `ICL Demo` app.
1. Fill out the `Name` and `Data` fields and press the `Encrypt` button. A new entry will be added for this data below.
1. To decrypt an entry, select it from the list. A dialog will display the document's decrypted data as well as relevant metadata.

### Running instrumented tests

This app includes a `connectedAndroidTest` that verifies the `ironoxide-android` AAR correctly bundles its `rustls-platform-verifier` dependency. This dependency isn't published to any public Maven repository, so it must be embedded in the AAR itself. The test confirms the classes are present and the native library loads without error.

```bash
./gradlew connectedAndroidTest
```
