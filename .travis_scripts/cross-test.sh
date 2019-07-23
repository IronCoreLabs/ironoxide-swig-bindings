#!/usr/bin/env bash

set -e
set -x

# Set JAVA_HOME inside our CentOS Docker container.
if [ -z "${JAVA_HOME}" -a -e /usr/lib/jvm/jre-1.8.0-openjdk ] ; then
    export JAVA_HOME=/usr/lib/jvm/jre-1.8.0-openjdk
fi

# JAVA_HOME isn't set on OSX for some reason, so manually set it
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=$(/usr/libexec/java_home)
fi

${JAVA_HOME}/bin/java -version

cargo fmt -- --check
cargo b
pushd tests && ~/bin/sbt test && popd