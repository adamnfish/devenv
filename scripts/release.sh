#!/usr/bin/env bash
set -e

# Function to detect architecture
detect_architecture() {
  local os=$(uname -s | tr '[:upper:]' '[:lower:]')
  local arch=$(uname -m)

  case "$os" in
    darwin)
      case "$arch" in
        arm64|aarch64)
          echo "macos-arm64"
          ;;
        x86_64|amd64)
          echo "macos-amd64"
          ;;
        *)
          echo "macos-unknown"
          ;;
      esac
      ;;
    linux)
      case "$arch" in
        arm64|aarch64)
          echo "linux-arm64"
          ;;
        x86_64|amd64)
          echo "linux-amd64"
          ;;
        *)
          echo "linux-unknown"
          ;;
      esac
      ;;
    *)
      echo "unknown"
      ;;
  esac
}

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
  echo "Usage: $0 <release> [architecture]"
  echo "Example: $0 1.0.0"
  echo "Example: $0 1.0.0 macos-arm64"
  echo ""
  echo "If architecture is not provided, it will be auto-detected."
  echo "Detected architecture: $(detect_architecture)"
  exit 1
fi

RELEASE=$1
ARCHITECTURE=${2:-$(detect_architecture)}

export DEVENV_RELEASE="$RELEASE"
export DEVENV_ARCHITECTURE="$ARCHITECTURE"

echo "Building with DEVENV_RELEASE=$DEVENV_RELEASE and DEVENV_ARCHITECTURE=$DEVENV_ARCHITECTURE"

echo sbt "cli/GraalVMNativeImage/packageBin"

BINARY_NAME="devenv-$RELEASE-$ARCHITECTURE"
echo "Binary name in GitHub release: $BINARY_NAME"
