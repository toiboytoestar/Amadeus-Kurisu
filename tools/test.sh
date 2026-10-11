#!/usr/bin/env bash
# Runs the pure-Java unit tests on the JVM (no Android SDK / Gradle needed).
# Needs: JDK 8+, junit4, hamcrest-core and an org.json implementation (apt: junit4 libandroid-json-org-java).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)
CP=${TEST_CP:-/usr/share/java/junit4.jar:/usr/share/java/hamcrest-core.jar:/usr/share/java/json-android.jar}
export JAVA_TOOL_OPTIONS=
javac -nowarn -encoding UTF-8 -cp "$CP" -d "$OUT" $(find app/src/main/java/com/toiboytoestar/kurisu/core app/src/test -name '*.java')
CLASSES=$(cd app/src/test/java && find . -name '*Test.java' | sed 's|^\./||; s|\.java$||; s|/|.|g')
java -cp "$OUT:$CP" org.junit.runner.JUnitCore $CLASSES
