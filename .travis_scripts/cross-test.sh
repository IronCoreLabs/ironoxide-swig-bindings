#!/usr/bin/env bash

set -e
set -x

. .travis_scripts/java-home.sh

"${JAVA_HOME}/bin/java" -version

ulimit -n 10000
cargo fmt -- --check
cargo b
pushd tests && ~/bin/sbt test && popd
