#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v javac >/dev/null || { echo "ERROR: Java JDK 17+ is required (javac missing)" >&2; exit 1; }
mkdir -p .build/classes
find src/main/java -name '*.java' > .build/sources.txt
javac -encoding UTF-8 -d .build/classes @.build/sources.txt
PORT="${T2T_PORT:-8080}"
echo "Starting Ticket2Test on http://127.0.0.1:${PORT} (press Ctrl+C to stop)"
exec java -cp '.build/classes:src/main/resources' za.co.ticket2test.web.QualityStudio
