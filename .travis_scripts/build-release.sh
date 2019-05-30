#!/usr/bin/env bash

set -e
set -x

# JAVA_HOME isn't set on OSX for some reason, so manually set it
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=$(/usr/libexec/java_home)
fi

cargo b --release

# copy out just the library to upload
mkdir release_artifacts
shopt -s extglob
cp target/release/libironoxide_java.*(so|dylib) release_artifacts/
shopt -u extglob