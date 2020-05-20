#!/bin/sh

# Set the version in various files, and then "git add" them.
# If called with -j, modify only the Java-related files. (E.g., setting "0.14.1-SNAPSHOT".)
# If called with -n, don't actually change any files; just verify.

set -e
set -x

# Parse args
case "$1" in
"-j")
    JAVAONLY=1
    shift
    ;;
"-n")
    VERIFY=".bak"
    shift
    ;;
esac

if [ $# -ne 1 ] ; then
    echo "Usage: $0 [-j|-n] version" >&2
    exit 1
fi
VERS="$1"

TEMPFILE=$(mktemp)
trap "rm -f ${TEMPFILE}" EXIT

# Only edit these files if "-j" wasn't given.
if [ -z "${JAVAONLY}" ] ; then
    for TOMLDIR in android cpp java ; do
        TOMLFILE="${TOMLDIR}/Cargo.toml"
        # Edit the file.
        sed -i"${VERIFY}" -e 's/^version = ".*"$/version = "'"${VERS}"'"/' "${TOMLFILE}"
        EDITEDFILES="${EDITEDFILES} ${TOMLFILE}"
    done
fi

sed -i"${VERIFY}" -e 's/^VERSION_NAME=.*/VERSION_NAME='"${VERS}"'/' android/gradle.properties
EDITEDFILES="${EDITEDFILES} android/gradle.properties"

sed -i"${VERIFY}" -e 's/com.ironcorelabs:ironoxide-android:.*@aar/com.ironcorelabs:ironoxide-android:'"${VERS}"'@aar/' android/examples/Example_Application/app/build.gradle
EDITEDFILES="${EDITEDFILES} android/examples/Example_Application/app/build.gradle"

sed -i"${VERIFY}" -e 's/^version in ThisBuild := ".*"$/version in ThisBuild := "'"${VERS}"'"/' java/tests/version.sbt
EDITEDFILES="${EDITEDFILES} java/tests/version.sbt"

for FILE in ${EDITEDFILES} ; do
    # Add it to git.
    git add "${FILE}"
    # Show the user the diff of what's changed.
    git diff --cached "${FILE}"
    # Verify that we've only changed one line.
    git diff --cached --numstat "${FILE}" > "${TEMPFILE}"
    read ADDED REMOVED FILENAME < "${TEMPFILE}"
    if [ "${ADDED}" -ne 1 -o "${REMOVED}" -ne 1 ] ; then
        echo "Changes to ${FILE} must be exactly one line; aborting." >&2
        exit 1
    fi
    # If we're in verify mode, put the file back.
    if [ -n "${VERIFY}" ] ; then
        mv "${FILE}${VERIFY}" "${FILE}"
        git add "${FILE}"
    fi
done
