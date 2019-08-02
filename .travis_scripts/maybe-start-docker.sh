#!/bin/sh

# Starts our build image inside docker, if we're doing a docker build.

set -ex

if [ -z "${IMAGE}" ] ; then
    exit 0
fi

if [ -n "${DOCKERFILE}" ] ; then
    docker build -t centos:"${IMAGE}" -f "${DOCKERFILE}" .
fi

docker run --detach --name target -v "$(pwd)":/src -w /src -e IMAGE="${IMAGE}" centos:"${IMAGE}" sleep 999999999

docker exec target yum group install -y "Development Tools"
docker exec target yum install -y java-sdk openssl-devel clang
