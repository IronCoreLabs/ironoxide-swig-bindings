#!/usr/bin/env bash

set -e
set -x

# JAVA_HOME isn't set on OSX for some reason, so manually set it
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=$(/usr/libexec/java_home)
fi

# If no special target provided, use default cargo arch for building and run all unit tests
if [ -z "$TARGET" ]; then
    cargo fmt -- --check
    cargo build
    pushd tests && ~/bin/sbt test && popd
# Cross doesn't have support for iOS builds, so use cargo to add the target and compile for it
elif [ "$IOS" = 1 ]; then
    rustup target add "$TARGET"
    cargo build --target "$TARGET"
# For everything else, use cross to compile for the specified target
else
    cross build --target "$TARGET"
fi