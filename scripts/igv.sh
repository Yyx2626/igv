#!/bin/sh

#This script is intended for launch on *nix machines

#-Xmx8g indicates 8 gb of memory.
#To adjust this (or other Java options), edit the "$HOME/.igv/java_arguments" 
#file.  For more info, see the README at 
#https://raw.githubusercontent.com/igvteam/igv/master/scripts/readme.txt 
#Add the flag -Ddevelopment = true to use features still in development
#Add the flag -Dsun.java2d.uiScale=2 for HiDPI displays
prefix="$(cd "$(dirname "$0")" && pwd)"

# Select a bundled JDK only when its OS and architecture match.
case "$(uname -s):$(uname -m)" in
    Darwin:arm64) JDK_DIR="jdk-21-macos-arm64" ;;
    Darwin:x86_64) JDK_DIR="jdk-21-macos-x86_64" ;;
    *) JDK_DIR="" ;;
esac

if [ -n "$JDK_DIR" ] && [ -x "${prefix}/${JDK_DIR}/bin/java" ]; then
    JAVA_HOME="${prefix}/${JDK_DIR}"
    PATH="${JAVA_HOME}/bin:${PATH}"
    export JAVA_HOME PATH
    echo "Using bundled ${JDK_DIR} JDK." >&2
else
    echo "Using system JDK. IGV requires Java 21." >&2
fi

# Report on Java version
java -version

# Build classpath including all jars in lib (non-recursive)
CP="${prefix}/lib/*"

# Check if there is a user-specified Java arguments file
if [ -e "$HOME/.igv/java_arguments" ]; then
    java -Xmx8g \
        @"${prefix}/igv.args" \
        -Dsamjdk.snappy.disable=true \
        -Dapple.laf.useScreenMenuBar=true \
        -Djava.net.preferIPv4Stack=true \
        -Djava.net.useSystemProxies=true \
        @"$HOME/.igv/java_arguments" \
        -cp "$CP" \
        org.igv.ui.Main "$@"
else
    java -Xmx8g \
        @"${prefix}/igv.args" \
        -Dsamjdk.snappy.disable=true \
        -Dapple.laf.useScreenMenuBar=true \
        -Djava.net.preferIPv4Stack=true \
        -Djava.net.useSystemProxies=true \
        -cp "$CP" \
        org.igv.ui.Main "$@"
fi
