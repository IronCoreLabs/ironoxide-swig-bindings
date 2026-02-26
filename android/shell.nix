# running nix-shell will give you an environment where you can run
# `./build.sh` and `./gradlew connectedAndroidTest` with a locally
# connected USB debugging device.
{ pkgs ? import <nixpkgs> {
    config = {
      allowUnfree = true;
      android_sdk.accept_license = true;
    };
  }
}:

let
  androidComposition = pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "36" ];
    buildToolsVersions = [ "36.0.0" ];
    includeEmulator = false;
    includeSystemImages = false;
    includeNDK = true;
  };
in
pkgs.mkShell {
  buildInputs = with pkgs; [
    rustup
    gradle
    jdk17
    android-tools
    androidComposition.androidsdk
  ];

  shellHook = ''
    export ANDROID_HOME="${androidComposition.androidsdk}/libexec/android-sdk"
    unset ANDROID_SDK_ROOT
    export PATH="$ANDROID_HOME/platform-tools:$PATH"

    echo "ANDROID_HOME=$ANDROID_HOME"
    echo "Android SDK ready!"
  '';
}
