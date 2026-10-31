#!/usr/bin/env bash
# Refills a player's army by hand, using the same refill.json the server applies
# by itself after every battle and at login: every listed unit up to "amount",
# each champion up to its own number. Units are only added, never taken away.
#
#   tools/refill_army.sh [player id]
#
# The server keeps the player in memory and would write its old copy back, so it
# is stopped first and restarted after. Close the game before, reopen it after.
# Every run stores the player as it was in the player_backup collection.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ID="${1:-dad436c8-761c-4579-8a76-0f910850510d}"

pkill -f swcnoops.server.SparkMain || true
sleep 2
docker cp "$ROOT/refill.json" swc-mongo:/tmp/refill.json
docker cp "$ROOT/swcFiles/1584443067/patches/trp.json" swc-mongo:/tmp/trp.json
docker exec swc-mongo mongosh swc --quiet --eval "
const ID='$ID';
const spec = JSON.parse(require('fs').readFileSync('/tmp/refill.json', 'utf8'));
const all = a => Object.fromEntries((a || []).map(u => [u, spec.amount]));
const target = {
  troop: all(spec.troop),
  hero: all(spec.hero),
  specialAttack: all(spec.specialAttack),
  champion: spec.champion || {},
};
const p = db.player.findOne({ _id: ID });
const now = p.playerSettings.deployableTroops;
const added = {};
for (const c in target) {
  now[c] = now[c] || {};
  for (const u in target[c]) {
    const have = now[c][u] || 0;
    if (have < target[c][u]) {
      added[c] = (added[c] || 0) + target[c][u] - have;
      now[c][u] = target[c][u];
    }
  }
}

if (spec.squad && spec.squad.length > 0) {
  const trp = JSON.parse(require('fs').readFileSync('/tmp/trp.json', 'utf8')).content.objects.TroopData;
  const donated = p.playerSettings.donatedTroops || {};
  const squadAmount = spec.squadAmount || 30;
  let squadAdded = 0;
  for (const u of spec.squad) {
    let direct = trp.filter(t => t.uid === u);
    let uid = null;
    if (direct.length > 0) {
      uid = direct[0].uid;
    } else {
      let matches = trp.filter(t => t.unitID === u);
      if (matches.length > 0) {
        matches.sort((a,b) => (parseInt(b.lvl)||1) - (parseInt(a.lvl)||1));
        uid = matches[0].uid;
      }
    }
    if (uid) {
      donated[uid] = donated[uid] || {};
      const current = Object.values(donated[uid]).reduce((a, b) => a + b, 0);
      if (current < squadAmount) {
        const diff = squadAmount - current;
        donated[uid][ID] = (donated[uid][ID] || 0) + diff;
        squadAdded += diff;
      }
    }
  }
  p.playerSettings.donatedTroops = donated;
  added['squad'] = squadAdded;
}

db.player_backup.insertOne(Object.assign({}, p, { _id: ID + '@' + new Date().toISOString().replace(/[:.]/g, '-') + '-before-refill' }));
db.player.updateOne({ _id: ID }, { \$set: { 'playerSettings.deployableTroops': now, 'playerSettings.donatedTroops': p.playerSettings.donatedTroops } });
print('refilled, added: ' + JSON.stringify(added));
"

cd "$ROOT"
nohup ./run.sh > run.log 2>&1 &
for _ in $(seq 1 30); do
  curl -s -m 2 -o /dev/null http://localhost:8080/swcFiles/1541022029/connection_test.txt && break
  sleep 2
done
echo "server restarted; close and reopen the game"
