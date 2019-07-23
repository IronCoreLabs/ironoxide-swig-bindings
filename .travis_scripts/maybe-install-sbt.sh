#!/bin/sh

# Install sbt if it isn't already present.

set -ex

cd tests
if ! ~/bin/sbt --version 2>/dev/null ; then
    mkdir -p ~/bin
    curl -Ls https://git.io/sbt > ~/bin/sbt
    chmod 0755 ~/bin/sbt
fi
