# IronOxide-Android Example Application

This application provides a simple UI for encrypting/decrypting data with the IronCore service. Its purpose is to give an example of depending on ironoxide-android, loading the native library, and making various SDK calls.

## Running locally

### Prerequisites

- Android SDK 29.
  - You can get the command line SDK [here](https://developer.android.com/studio) (scroll down to "Command line tools only"). You can then use `sdkmanager` (found in tools/bin) to install additional prerequisites:
    ```bash
    ./sdkmanager --sdk_root=PATH_TO_SDK_INSTALL_LOCATION --install "build-tools;29.0.3" platform-tools "platforms;android-29"
    ```
- A running Android Virtual Device (AVD)
  - This can be done from the [command line](https://developer.android.com/studio/command-line/avdmanager) or from [Android Studio](https://developer.android.com/studio/run/managing-avds#createavd)

### Directions

1. From the application root, run `./gradlew installDebug`.
2. From the emulator's app drawer, select the `ICL Demo` app.
3. Enter data into the `Name` and `Data` fields and press the `Encrypt` button. A new entry will be added for this data below.
4. To decrypt an entry, select it from the list. A dialog will display the document's decrypted data as well as relevant metadata.
