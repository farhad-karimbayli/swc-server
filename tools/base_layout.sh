#!/usr/bin/env bash
# Puts a saved layout on a player's base, then restarts the server.
#
#   tools/base_layout.sh <player id> <layout.json>
#
# The server keeps the player in memory and would write its old copy back, so it
# is stopped first. Close the game fully before, and reopen it after.
# Every run stores the base as it was in the player_backup collection.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ID="$1"
LAYOUT="$2"

pkill -f swcnoops.server.SparkMain || true
sleep 2
docker cp "$LAYOUT" swc-mongo:/tmp/layout.json
docker cp "$ROOT/tools/apply_base_layout.js" swc-mongo:/tmp/apply_base_layout.js
docker exec swc-mongo mongosh swc --quiet \
  --eval "const ID='$ID'; const LAYOUT='/tmp/layout.json'; load('/tmp/apply_base_layout.js')"

cd "$ROOT"
nohup ./run.sh > run.log 2>&1 &
for _ in $(seq 1 30); do
  curl -s -m 2 -o /dev/null http://localhost:8080/swcFiles/1541022029/connection_test.txt && break
  sleep 2
done
echo "server restarted; close and reopen the game"
