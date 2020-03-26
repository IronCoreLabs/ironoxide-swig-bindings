
rm -rf ironoxide-android/src/main/java
rm -rf ironoxide-android/src/main/jniLibs/*
cd ../
# sometimes this will be uncessary, but until we solve the duplicate ironoxide-android dirs in the target dir, probably safer to leave this here
cargo clean
cross rustc --target i686-linux-android --release -p ironoxide-android -- -C lto
cross rustc --target aarch64-linux-android --release -p ironoxide-android -- -C lto

# how can we handle multiple ironoxide-android directories at this level?
# Can we make cargo only ever generate one? Can we remove any old ones we build again?
# otherwise we will need to detect this cause and fail here
cp -r target/i686-linux-android/release/build/ironoxide-android*/out/java android/ironoxide-android/src/main/
mkdir -p android/ironoxide-android/src/main/jniLibs/x86/
cp -r target/i686-linux-android/release/libironoxide_android.so android/ironoxide-android/src/main/jniLibs/x86/
mkdir -p android/ironoxide-android/src/main/jniLibs/arm64-v8a/
cp -r target/i686-linux-android/release/libironoxide_android.so android/ironoxide-android/src/main/jniLibs/arm64-v8a/

cd android/
./gradlew build
ls ironoxide-android/build/outputs/aar