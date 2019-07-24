#!/bin/sh

# Install rust if necessary. Should only be necessary in a Docker build.

set -ex

if ! "${HOME}/.cargo/bin/rustc" --version 2> /dev/null ; then
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
fi


# This hack lets us run rustc from .travis.yml when the build is inside Docker.
ln -s "${HOME}"/.cargo/bin/* /usr/local/bin || true
