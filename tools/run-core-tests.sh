#!/usr/bin/env bash
# Local harness: compiles the pure-Kotlin core with kotlinc (no Gradle / network needed) and runs the suite.
# Usage: KOTLINC=/path/to/kotlinc/bin/kotlinc tools/run-core-tests.sh [demo args...]
set -euo pipefail
cd "$(dirname "$0")/.."
K=${KOTLINC:-kotlinc}
OUT=build/harness; mkdir -p "$OUT"
LIB=$(dirname "$(dirname "$(command -v "$K" || echo "$K")")")/lib
[ -f "$OUT/astronomy.jar" ] || "$K" core/src/main/kotlin/io/github/cosinekitty/astronomy/astronomy.kt -d "$OUT/astronomy.jar" 2>/dev/null
"$K" -Xjdk-release=1.8 -jvm-target 1.8 core/src/main/kotlin/app/astra/core/*.kt core/src/test/kotlin/app/astra/core/CoreSuite.kt core/src/test/kotlin/app/astra/core/Demo.kt \
    -cp "$OUT/astronomy.jar" -d "$OUT/core-new.jar" > "$OUT/compile.log" 2>&1 || { grep -E "error" "$OUT/compile.log"; echo "COMPILE FAILED"; exit 1; }
mv "$OUT/core-new.jar" "$OUT/core.jar"
CP="$OUT/core.jar:$OUT/astronomy.jar:$LIB/kotlin-stdlib.jar"
if [ "${1:-}" = "demo" ]; then shift; java -Dstdout.encoding=UTF-8 -cp "$CP" app.astra.core.DemoMain "$@"; else java -Dstdout.encoding=UTF-8 -cp "$CP" app.astra.core.CoreSuiteKt core/src/test/resources/fixtures; fi
