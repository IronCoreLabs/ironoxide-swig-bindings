#!/bin/sh

# Stop our docker build container if it's running.

set -ex

if [ -z "${IMAGE}" ] ; then
    exit 0
fi

docker stop target
docker rm target
