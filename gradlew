#!/usr/bin/env bash
#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses "$JAVA_HOME/jre/sh/java" as the actual executable
        JAVA_CMD="$JAVA_HOME/jre/sh/java"
    else
        JAVA_CMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVA_CMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME\n\nPlease set the JAVA_HOME environment variable to the root directory of your Java installation."
    fi
else
    JAVA_CMD="java"
fi

APP_HOME=$(dirname "$0")
APP_NAME=$(basename "$0")
APP_BASE_NAME=$(basename "$0" .sh)

# Add default JVM options here. You can also use the GRADLE_OPTS environment variable.
DEFAULT_JVM_OPTS=""

# Use the maximum available, or set MAX_MEM if you want to limit the maximum memory usage
if [ -z "$MAX_MEM" ] ; then
    MAX_MEM="512m"
fi

# Determine the script directory.
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Determine the Gradle distribution.
GRADLE_DISTRIBUTION_URL=$(grep "distributionUrl" "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.properties" | cut -d'=' -f2)
GRADLE_DISTRIBUTION_SHA256=$(grep "distributionSha256Sum" "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.properties" | cut -d'=' -f2)

# Download and unpack Gradle if not already present.
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
GRADLE_WRAPPER_DIR="$GRADLE_USER_HOME/wrapper/dists"
GRADLE_VERSION=$(echo "$GRADLE_DISTRIBUTION_URL" | sed -n 's/.*gradle-\(.*\)-bin.zip/\1/p')
GRADLE_INSTALL_DIR="$GRADLE_WRAPPER_DIR/gradle-$GRADLE_VERSION-bin/$GRADLE_DISTRIBUTION_SHA256"

if [ ! -d "$GRADLE_INSTALL_DIR" ]; then
    echo "Downloading Gradle distribution from $GRADLE_DISTRIBUTION_URL"
    mkdir -p "$GRADLE_INSTALL_DIR"
    curl -L "$GRADLE_DISTRIBUTION_URL" -o "$GRADLE_INSTALL_DIR/gradle.zip"
    unzip -q "$GRADLE_INSTALL_DIR/gradle.zip" -d "$GRADLE_INSTALL_DIR"
    rm "$GRADLE_INSTALL_DIR/gradle.zip"
fi

# Set GRADLE_HOME to the unpacked distribution.
GRADLE_HOME="$GRADLE_INSTALL_DIR/gradle-$GRADLE_VERSION"

# Set GRADLE_OPTS to pass to the JVM.
GRADLE_OPTS="-Xmx$MAX_MEM $DEFAULT_JVM_OPTS $GRADLE_OPTS"

# Execute Gradle.
exec "$JAVA_CMD" $GRADLE_OPTS -classpath "$GRADLE_HOME/lib/gradle-launcher-$GRADLE_VERSION.jar" org.gradle.launcher.GradleMain "$@"
