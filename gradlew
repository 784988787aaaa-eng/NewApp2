#!/bin/sh

# ----------------------------------------------------------------------------
# Gradle Start Up Script for SmartLedger Pro Android
# Compatible with GitHub Actions, Linux, macOS, and POSIX environments
# ----------------------------------------------------------------------------

set -e

APP_BASE_NAME=`basename "$0"`
APP_HOME="`pwd -P`"

# Always use the pinned Gradle Wrapper distribution. Never fall back to a
# globally installed Gradle version, which may be incompatible with this project.

# Fallback: check Java
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: JAVA_HOME is not set and no 'java' command was found in your PATH." >&2
    exit 1
fi

WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
    exec "$JAVACMD" "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
else
    # Fallback to direct gradle invocation
    exec gradle "$@"
fi
