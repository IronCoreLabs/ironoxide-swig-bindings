#!/usr/bin/env bash

set -e
set -x

# Set JAVA_HOME inside our CentOS Docker container.
if [ -z "${JAVA_HOME}" -a -e /usr/lib/jvm/java-openjdk ] ; then
    export JAVA_HOME=/usr/lib/jvm/java-openjdk
fi

# JAVA_HOME isn't set on OSX for some reason, so manually set it
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=$(/usr/libexec/java_home)
fi

cargo build --release

# Copy out just the library to upload. In order to just grab the .dylib or .so we just use extglob, allowing use of |.
# We did this instead of 2 cp lines so we could leave -e enabled and it would succeed as long as we have at least one of them.
mkdir release_artifacts
shopt -s extglob
cp target/release/libironoxide_java.*(so|dylib) release_artifacts/
shopt -u extglob
