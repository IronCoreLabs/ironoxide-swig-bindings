#!/bin/sh

# Compiles ironoxide-android in debug mode for `arm64-v8a` Android phones.
# Copies the library and Java files to `android/ironoxide-android/src/main`.

set -e

cd $(dirname $(readlink -f $0))

rm -rf ironoxide-android/src/main/java
rm -rf ironoxide-android/src/main/jniLibs/arm64-v8a
cd ../

cargo clean
cargo install cargo-ndk
rustup target install aarch64-linux-android
cargo ndk -t aarch64-linux-android build -p ironoxide-android

cp -r target/aarch64-linux-android/debug/build/ironoxide-android*/out/java android/ironoxide-android/src/main/
mkdir -p android/ironoxide-android/src/main/jniLibs/arm64-v8a/
cp -r target/aarch64-linux-android/debug/libironoxide_android.so android/ironoxide-android/src/main/jniLibs/arm64-v8a/
