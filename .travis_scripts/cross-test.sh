#!/usr/bin/env bash

set -e
set -x

# JAVA_HOME isn't set on OSX for some reason, so manually set it
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=$(/usr/libexec/java_home)
fi

cargo fmt -- --check
cargo build
pushd tests && ~/bin/sbt test && popd