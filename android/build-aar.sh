#!/bin/sh

# Builds the android release

# Compiles ironoxide-android in release mode for `x86_64`, `arm64-v8a`, and `armeabi-v7a` Android phones.
# Copies the library and Java files to `android/ironoxide-android/src/main`.
# Generates an AAR for the architectures in `android/ironoxide-android/build/outputs/aar`.

set -e

cd $(dirname $(readlink -f $0))

rm -rf ironoxide-android/src/main/java
rm -rf ironoxide-android/src/main/jniLibs/*
cd ../

cargo clean
cargo install cargo-ndk
rustup target install x86_64-linux-android aarch64-linux-android armv7-linux-androideabi
cargo ndk -t x86_64-linux-android build --release -p ironoxide-android
cargo ndk -t aarch64-linux-android build --release -p ironoxide-android
cargo ndk -t armv7-linux-androideabi build --release -p ironoxide-android

cp -r target/x86_64-linux-android/release/build/ironoxide-android*/out/java android/ironoxide-android/src/main/
mkdir -p android/ironoxide-android/src/main/jniLibs/x86_64/
cp -r target/x86_64-linux-android/release/libironoxide_android.so android/ironoxide-android/src/main/jniLibs/x86_64/
mkdir -p android/ironoxide-android/src/main/jniLibs/arm64-v8a/
cp -r target/aarch64-linux-android/release/libironoxide_android.so android/ironoxide-android/src/main/jniLibs/arm64-v8a/
mkdir -p android/ironoxide-android/src/main/jniLibs/armeabi-v7a/
cp -r target/armv7-linux-androideabi/release/libironoxide_android.so android/ironoxide-android/src/main/jniLibs/armeabi-v7a/

cd android
./gradlew clean 
# This is a separate command so that the configuration step runs again (needed for rustls classes)
./gradlew build