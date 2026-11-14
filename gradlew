#!/usr/bin/env sh
set -eu
VERSION=8.7
DIST="${GRADLE_USER_HOME:-$HOME/.gradle}/counterapp-wrapper/gradle-$VERSION"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$(dirname "$DIST")"
  ZIP="$(dirname "$DIST")/gradle-$VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    curl -fL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
  fi
  unzip -q -o "$ZIP" -d "$(dirname "$DIST")"
fi
exec "$DIST/bin/gradle" "$@"
