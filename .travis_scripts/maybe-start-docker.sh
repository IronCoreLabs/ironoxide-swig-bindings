#!/bin/sh

# Starts our build image inside docker, if we're doing a docker build.

set -ex

if [ -z "${IMAGE}" ] ; then
    exit 0
fi

docker run --detach --name target -v "$(pwd)":/src -w /src -e IMAGE="${IMAGE}" centos:"${IMAGE}" sleep 999999999

# Note that yum will fail if these are run in an order that makes more sense.
docker exec target yum install -y java-sdk
docker exec target yum install -y openssl-devel
docker exec target yum install -y clang
docker exec target yum group install -y "Development Tools"
