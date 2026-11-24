#!/usr/bin/env sh
set -e
FOLDER="$(cd "$(dirname "$0")" && pwd)"
JAR="$FOLDER/target/api-tester.jar"
if [ ! -f "$JAR" ]; then
  echo "Building api-tester.jar for the first time..."
  (cd "$FOLDER" && mvn -q package -DskipTests)
fi
java -jar "$JAR" "$@"
