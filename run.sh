#!/usr/bin/env bash
# Starts MongoDB and the Star Wars Commander private server.
#
# MongoDB runs in Docker because the server needs a replica set for
# transactions, and a one-node replica set is two commands in a container
# against a page of instructions on the host.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME="${JAVA_HOME:-/Users/karimbaylifarhad/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home}"

if ! docker ps --format '{{.Names}}' | grep -qx swc-mongo; then
  echo "Starting MongoDB..."
  docker start swc-mongo >/dev/null 2>&1 || \
    docker run -d --name swc-mongo -p 27017:27017 mongo:7 --replSet rs0 --bind_ip_all >/dev/null
  until docker exec swc-mongo mongosh --quiet --eval 'db.runCommand({ping:1}).ok' 2>/dev/null | grep -q 1; do
    sleep 2
  done
  docker exec swc-mongo mongosh --quiet --eval \
    'try { rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]}) } catch(e) {}' >/dev/null
fi

# The address the game client will be told to fetch assets from has to be one
# the phone or emulator can reach, so it follows this machine's current address.
IP="$(ipconfig getifaddr en0 2>/dev/null || hostname -I | awk '{print $1}')"
echo "Server address for the client: http://${IP}:8080"
sed -i '' -E "s|^(cdnRoots=http://)[^:]+|\1${IP}|" "$ROOT/swc.properties" 2>/dev/null || true
sed -i '' -E "s|^(event2BiLoggingIpAddress=http://)[^:]+|\1${IP}|" "$ROOT/swc.properties" 2>/dev/null || true

cd "$ROOT"
[ -f cp.txt ] || mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
[ -d target/classes ] || mvn -q -DskipTests package

exec "$JAVA_HOME/bin/java" -cp "target/classes:$(cat cp.txt)" \
  swcnoops.server.SparkMain swc.properties
