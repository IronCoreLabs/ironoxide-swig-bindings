{
  description = "IronOxide Swig Bindings with Android SDK + NDK";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    rust-overlay.url = "github:oxalica/rust-overlay";
    flake-utils.url = "github:numtide/flake-utils";

    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, rust-overlay, flake-utils, android-nixpkgs, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        overlays = [ (import rust-overlay) ];
        pkgs = import nixpkgs { inherit system overlays; };

        rusttoolchain =
          pkgs.rust-bin.fromRustupToolchainFile ./rust-toolchain.toml;

        androidSdkWithNdk = android-nixpkgs.sdk.${system} (sdkPkgs: with sdkPkgs; [
          cmdline-tools-latest
          build-tools-36-0-0
          platform-tools
          platforms-android-36
          ndk-27-3-13750724
        ]);
      in
      {
        devShell = pkgs.mkShell {
          buildInputs = with pkgs; [
            rusttoolchain
            pkg-config
            openssl
            openjdk21
            sbt
            androidSdkWithNdk
          ];

          shellHook = ''
            export ANDROID_SDK_ROOT="${androidSdkWithNdk}"
            export ANDROID_HOME="${androidSdkWithNdk}"
            export ANDROID_NDK_ROOT="${androidSdkWithNdk}/share/android-sdk/ndk"
          '';
        };

        packages = {
          android-sdk-with-ndk = androidSdkWithNdk;
        };
      });
}
