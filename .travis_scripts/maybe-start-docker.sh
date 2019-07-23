#!/bin/sh

# Starts our build image inside docker, if we're doing a docker build.

set -ex

if [ -z "${IMAGE}" ] ; then
    exit 0
fi

docker run --detach --name target -v $(pwd):/src -v ${HOME}:/root -w /src ${IMAGE} sleep 999999999

docker exec target yum group install -y "Development Tools"
docker exec target yum install -y java-1.8.0-openjdk openssl
