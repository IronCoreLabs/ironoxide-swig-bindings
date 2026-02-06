#!/bin/sh

# Compiles ironoxide-android in debug mode for `x86_64`, `arm64-v8a`, and `armeabi-v7a`.
# Copies the library and Java files to `android/ironoxide-android/src/main`.

set -e

cd $(dirname $(readlink -f $0))

rm -rf ironoxide-android/src/main/java
rm -rf ironoxide-android/src/main/jniLibs/*
cd ../

cargo clean
cargo install cargo-ndk
rustup target install x86_64-linux-android aarch64-linux-android armv7-linux-androideabi
cargo ndk -t x86_64-linux-android build -p ironoxide-android
cargo ndk -t aarch64-linux-android build -p ironoxide-android
cargo ndk -t armv7-linux-androideabi build -p ironoxide-android

cp -r target/x86_64-linux-android/debug/build/ironoxide-android*/out/java android/ironoxide-android/src/main/
mkdir -p android/ironoxide-android/src/main/jniLibs/x86_64/
cp -r target/x86_64-linux-android/debug/libironoxide_android.so android/ironoxide-android/src/main/jniLibs/x86_64/
mkdir -p android/ironoxide-android/src/main/jniLibs/arm64-v8a/
cp -r target/aarch64-linux-android/debug/libironoxide_android.so android/ironoxide-android/src/main/jniLibs/arm64-v8a/
mkdir -p android/ironoxide-android/src/main/jniLibs/armeabi-v7a/
cp -r target/armv7-linux-androideabi/debug/libironoxide_android.so android/ironoxide-android/src/main/jniLibs/armeabi-v7a/
