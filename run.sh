#!/usr/bin/env bash
# Starts MongoDB and the Star Wars Commander private server.
#
# MongoDB runs in Docker because the server needs a replica set for
# transactions, and a one-node replica set is two commands in a container
# against a page of instructions on the host.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

# Detect Java 17+ binary
JAVA_BIN=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_BIN="$(command -v java)"
elif [ -d "/Users/karimbaylifarhad/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home/bin" ]; then
  JAVA_BIN="/Users/karimbaylifarhad/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home/bin/java"
else
  echo "Error: Java was not found. Please install Java 17+ or set JAVA_HOME." >&2
  exit 1
fi

# Ensure MongoDB is running in Docker
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

# Detect network IP for client access (can be explicitly set via SWC_HOST=<ip>)
IP="${SWC_HOST:-}"
if [ -z "$IP" ]; then
  if command -v ipconfig >/dev/null 2>&1; then
    IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
  fi
  if [ -z "$IP" ] && command -v hostname >/dev/null 2>&1; then
    IP="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  fi
  if [ -z "$IP" ] && command -v ip >/dev/null 2>&1; then
    IP="$(ip -4 addr show scope global 2>/dev/null | awk '/inet /{print $2}' | cut -d/ -f1 | head -n1 || true)"
  fi
  IP="${IP:-127.0.0.1}"
fi

echo "Server address for the client: http://${IP}:8080"
if [[ "$OSTYPE" == "darwin"* ]]; then
  sed -i '' -E "s|^(cdnRoots=http://)[^:/]+|\1${IP}|" "$ROOT/swc.properties" 2>/dev/null || true
  sed -i '' -E "s|^(event2BiLoggingIpAddress=http://)[^:/]+|\1${IP}|" "$ROOT/swc.properties" 2>/dev/null || true
else
  sed -i -E "s|^(cdnRoots=http://)[^:/]+|\1${IP}|" "$ROOT/swc.properties" 2>/dev/null || true
  sed -i -E "s|^(event2BiLoggingIpAddress=http://)[^:/]+|\1${IP}|" "$ROOT/swc.properties" 2>/dev/null || true
fi

cd "$ROOT"
[ -f cp.txt ] || mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
[ -d target/classes ] || mvn -q -DskipTests package

exec "$JAVA_BIN" -cp "target/classes:$(cat cp.txt)" \
  swcnoops.server.SparkMain swc.properties
