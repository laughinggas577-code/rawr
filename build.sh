#!/bin/bash
# Quick build script for Rawr Automation
# Requires: Java 21 (JDK 21)

set -e

echo "=== Rawr Automation Build ==="
echo ""

# Check Java version
java_version=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "Detected Java: $java_version"

if [ "$java_version" != "21" ]; then
    echo "WARNING: Java 21 is required for NeoForge 1.21.1"
    echo "Current Java may not work. Set JAVA_HOME to a Java 21 JDK."
    echo ""
fi

echo "Building mod..."
./gradlew build

echo ""
echo "=== Build complete! ==="
echo "Output: build/libs/RawrAutomation-2.0.0.jar"
echo "Copy this jar to your .minecraft/mods/ folder."
