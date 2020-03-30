rm -rf android/ironoxide-android/src/main/java
rm -rf android/ironoxide-android/src/main/jniLibs/*

cross build --target i686-linux-android -p ironoxide-android
cross build --target aarch64-linux-android -p ironoxide-android

cp -r target/i686-linux-android/debug/build/ironoxide-android*/out/java android/ironoxide-android/src/main/
mkdir -p android/ironoxide-android/src/main/jniLibs/x86/
cp -r target/i686-linux-android/debug/libironoxide_android.so android/ironoxide-android/src/main/jniLibs/x86/
mkdir -p android/ironoxide-android/src/main/jniLibs/arm64-v8a/
cp -r target/aarch64-linux-android/debug/libironoxide_android.so android/ironoxide-android/src/main/jniLibs/arm64-v8a/

android/gradlew build