#!/usr/bin/env bash
set -euo pipefail

# Build the Rust cdylib
echo "Building Rust cdylib..."
cargo build --release

# Copy DLL to Maven resources
echo "Copying DLL to resources..."
mkdir -p src/main/resources/win32-x86-64
cp target/release/flatmarkdown_java.dll src/main/resources/win32-x86-64/

# Build Maven JAR
echo "Building Maven JAR..."
mvn package -q

echo "Done! JAR is at target/flatmarkdown-1.0.0-SNAPSHOT.jar"
