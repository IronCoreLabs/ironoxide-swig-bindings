# IronOxide-Android Example Application

This application provides a simple UI for encrypting/decrypting data with IronCore's Android SDK. Its purpose is to give an example of depending on ironoxide-android, loading the native library, and making various SDK calls.

<img src="Screenshot.png" alt="Screenshot" width="300"/>

## Running locally

### Prerequisites

- Environment variable `ANDROID_SDK_ROOT` set to the Android Sdk install location
- Android SDK 29. You can get the command line SDK [here](https://developer.android.com/studio) (scroll down to "Command line tools only") and use `sdkmanager` (found in tools/bin) to install additional prerequisites:
  ```bash
    ./sdkmanager --sdk_root=PATH_TO_SDK_INSTALL_LOCATION --install "build-tools;29.0.3" "platform-tools" "platforms;android-29" "emulator"
  ```
- An Android Virtual Device (AVD)

### Directions

1. Start the AVD.
1. From the Example_Application root, run `./gradlew installDebug`.
1. From the emulator's app drawer, select the `ICL Demo` app.
1. Fill out the `Name` and `Data` fields and press the `Encrypt` button. A new entry will be added for this data below.
1. To decrypt an entry, select it from the list. A dialog will display the document's decrypted data as well as relevant metadata.
