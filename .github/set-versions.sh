#!/bin/sh

# Set the version in various files, and then "git add" them.
# If called with -j, modify only the Java-related files. (E.g., setting "0.14.1-SNAPSHOT".)
# If called with -n, don't actually change any files; just verify.

set -e

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
# shellcheck disable=SC2064
trap "rm -f ${TEMPFILE}" EXIT

# Only edit these files if "-j" wasn't given.
if [ -z "${JAVAONLY}" ] ; then
    for TOMLDIR in android cpp java ; do
        TOMLFILE="${TOMLDIR}/Cargo.toml"
        # Edit the file.
        sed -i"${VERIFY}" -e 's/^version = ".*"$/version = "'"${VERS}"'"/' "${TOMLFILE}"
        EDITEDFILES="${EDITEDFILES} ${TOMLFILE}"
    done

    sed -i"${VERIFY}" -e 's/^Version: .*$/Version: '"${VERS}"'/' cpp/ironoxide.pc.in
    EDITEDFILES="${EDITEDFILES} cpp/ironoxide.pc.in"
fi

sed -i"${VERIFY}" -e 's/^VERSION_NAME=.*/VERSION_NAME='"${VERS}"'/' android/gradle.properties
EDITEDFILES="${EDITEDFILES} android/gradle.properties"

sed -i"${VERIFY}" -e 's/^version in ThisBuild := ".*"$/version in ThisBuild := "'"${VERS}"'"/' java/tests/version.sbt
EDITEDFILES="${EDITEDFILES} java/tests/version.sbt"

for FILE in ${EDITEDFILES} ; do
    # Add it to git.
    git add "${FILE}"
    # Show the user the diff of what's changed.
    git diff --cached "${FILE}"
    # Verify that we've changed exactly one line.
    git diff --cached --numstat "${FILE}" > "${TEMPFILE}"
    # shellcheck disable=SC2034
    read -r ADDED REMOVED FILENAME < "${TEMPFILE}"
    if [ "${ADDED}" -ne 1 ] || [ "${REMOVED}" -ne 1 ] ; then
        echo "Changes to ${FILE} must be exactly one line; aborting." >&2
        exit 1
    fi
    # If we're in verify mode, put the file back.
    if [ -n "${VERIFY}" ] ; then
        mv "${FILE}${VERIFY}" "${FILE}"
        git add "${FILE}"
    fi
done

# Look for files that have been changed, but that we haven't told git about.
echo "Checking for modified but untracked files:"
if git status -s | grep -Ev '^M ' ; then
    echo "This probably means $0 modified a file but forgot to 'git add' it."
    exit 1
fi
