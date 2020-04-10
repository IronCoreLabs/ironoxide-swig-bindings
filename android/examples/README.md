# IronOxide-Android Example Application

This application provides a simple UI for encrypting/decrypting data with the IronCore service. Its purpose is to give an example of depending on ironoxide-android, loading the native library, and making various SDK calls.

## Running locally

### Prerequisites

- Environment variable `ANDROID_SDK_ROOT` set to the Android Sdk install location
- Android SDK 29. You can get the command line SDK [here](https://developer.android.com/studio) (scroll down to "Command line tools only") and use `sdkmanager` (found in tools/bin) to install additional prerequisites:
  ```bash
  ./sdkmanager --install "build-tools;29.0.3" "platform-tools" "platforms;android-29" "emulator"
  ```
- An Android Virtual Device (AVD). This can be created from either the [command line](https://developer.android.com/studio/command-line/avdmanager) or [Android Studio](https://developer.android.com/studio/run/managing-avds#createavd). Example:
  ```bash
  ./avdmanager create avd -n pixel_3 -k "system-images;android-29;google_apis_playstore;x86_64" -d pixel_3
  ```

### Directions

1. Start the AVD.
   - Example: `./emulator -avd pixel_3 -noaudio`
1. From the Example_Application root, run `./gradlew installDebug`.
1. From the emulator's app drawer, select the `ICL Demo` app.
1. Enter data into the `Name` and `Data` fields and press the `Encrypt` button. A new entry will be added for this data below.
1. To decrypt an entry, select it from the list. A dialog will display the document's decrypted data as well as relevant metadata.
