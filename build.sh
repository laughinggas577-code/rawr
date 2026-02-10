#!/bin/bash
# Quick build script for Rawr Automation
# Requires: Java 8 (JDK 1.8)

set -e

echo "=== Rawr Automation Build ==="
echo ""

# Check Java version
java_version=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1-2)
echo "Detected Java: $java_version"

if [ "$java_version" != "1.8" ]; then
    echo "WARNING: Java 8 (1.8) is required for ForgeGradle 2.1"
    echo "Current Java may not work. Set JAVA_HOME to a Java 8 JDK."
    echo ""
fi

echo "Step 1/2: Setting up Forge workspace..."
./gradlew setupDecompWorkspace

echo ""
echo "Step 2/2: Building mod..."
./gradlew build

echo ""
echo "=== Build complete! ==="
echo "Output: build/libs/RawrAutomation-1.0.0.jar"
echo "Copy this jar to your .minecraft/mods/ folder."
