#!/usr/bin/env bash
#
# Start mosh4j as a test server. Prints "MOSH CONNECT <port> <key>" to stderr.
# A client can connect using that port and key (e.g. mosh4j client or manual test).
#
# Usage:
#   ./scripts/run-mosh4j-server.sh [port]
#   MOSH_PORT=60002 ./scripts/run-mosh4j-server.sh
#
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

PORT="${MOSH_PORT:-60001}"
if [[ -n "${1:-}" ]]; then
  PORT="$1"
fi

echo "Building mosh4j (mosh4j-core and dependencies)..." >&2
mvn -q package -DskipTests -pl mosh4j-core -am >&2

echo "Starting mosh4j server on port $PORT (key will be generated)..." >&2
exec mvn -q exec:java -pl mosh4j-core \
  -Dexec.mainClass="org.mosh4j.core.MoshServerMain" \
  -Dexec.args="$PORT"
