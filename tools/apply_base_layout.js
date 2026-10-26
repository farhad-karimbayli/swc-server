// Applies a layout file to a player's base inside MongoDB.
// Run through tools/base_layout.sh, which sets ID and LAYOUT and restarts the server.
//
// A layout is {"next": n, "buildings": [{"key", "x", "z", "uid"}, ...]}. Buildings
// already on the base keep every stored field and only move. A key the base does
// not have yet is cloned from a building with the same uid. A key the base has
// but the layout lacks is removed, which is how the original layout undoes the
// fortified one. Positions are written as int32, as the server stored them.
const fs = require('fs');
const layout = JSON.parse(fs.readFileSync(LAYOUT, 'utf8'));
const player = db.player.findOne({ _id: ID });
if (!player) throw new Error('no player ' + ID);
const current = player.playerSettings.baseMap;

const stamp = new Date().toISOString().replace(/[:.]/g, '-');
db.player_backup.insertOne(Object.assign({}, player, { _id: ID + '@' + stamp + '-before-layout' }));

const byKey = {}, byUid = {};
current.buildings.forEach(b => { byKey[b.key] = b; if (!byUid[b.uid]) byUid[b.uid] = b; });

let moved = 0, added = 0;
const buildings = layout.buildings.map(l => {
  let b = byKey[l.key];
  if (b) {
    if (b.uid !== l.uid) throw new Error(l.key + ' is ' + b.uid + ' on the base but ' + l.uid + ' in the layout');
    moved++;
  } else {
    const twin = byUid[l.uid];
    if (!twin) throw new Error('no building with uid ' + l.uid + ' to copy ' + l.key + ' from');
    added++;
  }
  const src = b || byUid[l.uid];
  return {
    key: l.key,
    x: NumberInt(l.x),
    z: NumberInt(l.z),
    uid: l.uid,
    lastCollectTime: src.lastCollectTime,
    currentStorage: b ? b.currentStorage : NumberInt(0),
  };
});

const kept = new Set(layout.buildings.map(l => l.key));
const removed = current.buildings.filter(b => !kept.has(b.key));
const removedByUid = {};
removed.forEach(b => removedByUid[b.uid] = (removedByUid[b.uid] || 0) + 1);

const next = Math.max(layout.next, current.next);
db.player.updateOne({ _id: ID }, { $set: {
  'playerSettings.baseMap.buildings': buildings,
  'playerSettings.baseMap.next': NumberInt(next),
} });
print('backup: ' + ID + '@' + stamp + '-before-layout');
print('moved ' + moved + ', added ' + added + ', removed ' + removed.length + ' ' + JSON.stringify(removedByUid));
print('buildings now ' + buildings.length + ', next ' + next);
