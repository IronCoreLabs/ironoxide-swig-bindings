#!/usr/bin/env bash

set -e
set -x

. .travis_scripts/java-home.sh

cargo build --release

# Copy out just the library to upload. In order to just grab the .dylib or .so we just use extglob, allowing use of |.
# We did this instead of 2 cp lines so we could leave -e enabled and it would succeed as long as we have at least one of them.
mkdir release_artifacts
shopt -s extglob
cp target/release/libironoxide_java.*(so|dylib) release_artifacts/
shopt -u extglob

# If this build is for RedHat, package up the .so as an archive so we don't have a name collision.
if [ -n "${IMAGE}" ] ; then
    pushd release_artifacts
    tar czf "libironoxide_java-${IMAGE}.tar.gz" libironoxide_java.so
    rm libironoxide_java.so
    popd
fi
