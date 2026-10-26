# What we changed

This is a fork of darthRebel's Star Wars Commander server. This file lists
everything we changed from the original to get the game running on a phone.
The dated working log, including dead ends, is in `NOTES.md`. The author's
original instructions are in `README.md`.

**State on 2026-10-02:** the game runs on a phone (Samsung SM-S918B). Login
works, all 12 core patches load, the Empire HQ11 base renders in 3D, and
campaign and attack missions are playable.

## Running it

```bash
cd ~/claude_console/swc-server
./run.sh
```

`run.sh` starts MongoDB in Docker (container `swc-mongo`, replica set `rs0`)
and the server on port 8080. On every start it writes the Mac's current `en0`
address into `cdnRoots` and `event2BiLoggingIpAddress` in `swc.properties`.
Docker Desktop must be running first.

To check that the server answers:

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  http://localhost:8080/swcFiles/manifests/__manifest_zyngaswc_prod.02045.json
```

200 means it is serving.

## The client APK

Current file: `client/swc-patched-192.168.0.110.apk`. Older builds for other
addresses have been deleted.

The server address is baked into the client, so **the APK has to be rebuilt
whenever the network changes**. The game code itself is untouched. Only the
addresses in `assets/bin/Data/Managed/Metadata/global-metadata.dat` change.
The manifest also gets `android:usesCleartextTraffic="true"`, because the
server speaks plain HTTP.

To rebuild for a new address:

1. Extract `global-metadata.dat` from the original
   `~/claude_console/star-wars-commander-7-8-1-253.apk`.
2. Run `python3 tools/patch_metadata.py global-metadata.dat <ip>:8080`.
3. Take the current patched APK, `zip` the new `global-metadata.dat` into it,
   and delete the old signature (`META-INF/SWC.*`, `META-INF/MANIFEST.MF`).
4. Run `zipalign -f -p 4`, then `apksigner sign` with `client/swc-dev.keystore`
   (password `swcserver`, alias `swc`).

The build tools are in `/opt/homebrew/share/android-commandlinetools/build-tools/36.1.0`.
The signing key never changes, so a new APK installs over the old one without
an uninstall and the player's progress is kept.

`patch_metadata.py` rewrites each address in two places: the string literals
the running code reads, and the C# field default values. Patching only the
defaults looks successful and changes nothing. The script also points the
Swrve and Bugsnag analytics hosts at the closed port `127.0.0.1:1`. Without
that, the client stalled retrying HTTPS servers that no longer exist.

## Server code changes

| File | Problem | Fix |
|---|---|---|
| `Config.java` | Read only `Integer` and `String`, so every `boolean` and `long` in the properties became null and startup crashed. | Handles primitives and their boxed forms, and fails loudly on an unknown type. |
| `GameDataManagerImpl.java` | Crashed when the patches folder was missing. | A missing folder means no patches. |
| `ObjectiveManagerImpl.java` | `planetSeries` stayed null, so every login after the first died with an NPE. | Starts as an empty map, meaning no objectives. |
| `SessionManagerImpl.java` | Campaigns and missions were filled in only when the object was null, but it was an empty object, so the client hung on the loading bar. | Also checks the two maps inside the object. |
| `RaidManagerImpl.java` | NPE, because no raid data is loaded; `planet1` was counted twice. | Skips planets with no raids and drops the duplicate. |
| `SparkMain.java` | A failure gave no record of what the client had asked for. | Logs every request and saves request and response bodies to `$TMPDIR/swc-batches/`. |
| `SparkMain.java` | Attack missions got a 404 for the enemy base. | A missing `battles/*.json` falls back to `swcFiles/battles/_default_mission.json`. |
| `Config.java`, `GeneratePlayer.java`, `GetAuthToken.java` | New players were created from the same template as the login, which was the maxed HQ11 Empire base. | New players are created from `newPlayerTemplate`: HQ1, no faction, tutorial. |
| `PlayerLogin.java` | Login always used one template for the fields it does not read from the player's own data. | A player still in the tutorial gets the new-player template; everyone else keeps `playerLoginTemplate`. |
| `PlayerDatasourceImpl.java` | A new player started with 0 resources, because a fresh settings object has an empty inventory rather than a null one, so the template's resources were never copied. | Settings are cleared before the template is applied, the same way an account reset already does. |
| `session/AutoRefill.java`, `PlayerSessionImpl.java`, `PlayerLogin.java` | Spent troops had to be refilled by hand. | Players listed in `refill.json` get their army topped up after every battle and at login. |

## Configuration changes (`swc.properties`)

- New players start from scratch: `newPlayerTemplate=newPlayer/playerLogin.json`
  gives a smuggler HQ1 base, 1000 credits and 1000 materials, and the tutorial,
  where the player picks Empire or Rebels. The base then becomes an HQ1 base of
  that faction with its first campaign mission.
- The login template stays `existingPlayer`, so players who already finished
  the tutorial, such as the restored HQ11 base, load exactly as before.
- Switching faction in-game creates a second account from
  `templates/factionFlipTemplate<Faction>.json`, an HQ2 starter base. This was
  already the case.
- `coreJsonPatches` cut to the three files we have:
  `base.json;cae.json;trp.json`.
- `cdnRoots` and `event2BiLoggingIpAddress` point at this Mac. `run.sh` keeps
  them current.
- `loadDevBases=true` and `freeResources=true` are on.

## Game data we added

- **Asset bundles** from a community member's Unity cache export (mediafire
  `albhe59f5aen4uy`, 351 MB):
  - all 12 core patches, in `swcFiles/<v>/patches/`;
  - 1215 of the 2187 art bundles, in `swcFiles/<v>/assetbundles/android/`;
  - the English UI strings, in `swcFiles/<v>/strings/`. Without them every
    label shows `LANG: ... missing`. The client always loads en-us, even on a
    phone set to Russian.
- **The manifest** `swcFiles/manifests/__manifest_zyngaswc_prod.02045.json`:
  each entry's `crc` was recalculated to match the file we actually serve. The
  original is kept next to it as `.orig`.
- **Attack mission maps:** 217 `mis_*.json` files in
  `swcFiles/1541022029/battles/`, generated from `cae.json`. Each enemy base
  belongs to the opposite faction, at HQ 2 to 5 depending on the mission's
  difficulty. They were lowered from higher levels after the first set proved
  too hard. The original mission maps are lost, so these are generic bases.
- **PvP bases** in `layouts/`: 15 bases, HQ 5 to 11 for both factions, written
  by `tools/gen_layouts.py` and checked with `--check`. Buildings come only
  from `base.json`, with no overlaps and nothing off the grid (-22..21).
- `connection_test.txt`, the first file the client requests.

## Player database edits

These were made by hand in MongoDB and apply only to the current player:

- **Campaign missions.** The player template held a single mission (the
  tutorial), so every campaign chapter was empty. We added entries for all 109
  Empire missions from `cae.json` to the player.
- **Resources** are set in `playerSettings.inventoryStorage.<resource>.amount`.

The server keeps the player's session in memory. After editing the database,
restart the server, then fully close and reopen the game. Reinstalling the APK
or clearing its data creates a **new** player. The old progress stays in the
database, but the phone can no longer reach it.

## Fortified base (2026-10-02)

The player's base was rebuilt by `tools/fortify_base.py` as three closed wall
rings around the HQ, with spokes that split the outer two bands into
compartments. The core holds the HQ, shields and turrets. The second band holds
the storages, the Droideka platforms, turrets and shields. The third band holds
a turret belt, shields and the small buildings. Starports, factories, barracks
and resource generators sit outside as a buffer, with traps along the outer wall.

No building was removed. All 231 existing buildings kept their keys and stored
fields and were only moved. Added past the game's limits: 144 walls (284 in
total), 24 turrets (46) and 4 shield generators (8), all copies of uids the
base already had, so no new art is requested.

Layouts live in `base_backups/`:

- `dad436c8_2026-10-02_original.json`, the base as it was;
- `dad436c8_2026-10-02_fortified.json`, the fortified one;
- `dad436c8_2026-10-02_original.ejson`, the full original `baseMap` with Mongo types.
- `dad436c8_2026-10-02_fortified_your_moves.json`, the fortified base after the
  player rearranged 49 buildings in-game;
- `dad436c8_2026-10-02_fortified_v2.json`, the current one: the player's
  rearrangement with the starports fixed (see below).

The first fortified layout packed the starports against factories, walls and
the board edge, with 2 to 9 free tiles around them against 17 to 25 on the
original base. Troops had nowhere to stand and the transport animation broke.
The player then placed the heavy Droideka platform on top of a starport, which
the client allowed and the server stored. `fortified_v2` keeps the player's own
moves, puts the platform back inside the middle ring, and gives every starport
a fully free tile all round by moving the buffer buildings, traps and turrets
that were in the way. The third starport moved next to the two the player had
already put at the bottom.

To switch between them, close the game, run one of these, then reopen it:

```bash
tools/base_layout.sh dad436c8-761c-4579-8a76-0f910850510d base_backups/dad436c8_2026-10-02_original.json
tools/base_layout.sh dad436c8-761c-4579-8a76-0f910850510d base_backups/dad436c8_2026-10-02_fortified.json
```

`base_layout.sh` stops the server, saves the current player to `player_backup`,
moves every building to its position in the file, adds the ones the file has and
the base lacks, removes the ones the base has and the file lacks, and restarts
the server. Going back to the original therefore removes the added walls,
turrets and shields. Anything built in-game after the switch is not in either
file and would be removed too, though it stays in the `player_backup` copy.

## Automatic army refill

`refill.json` in the project root lists players and an amount. For those
players the server brings every listed troop, hero and starship up to that
amount (20 now), and each Droideka champion up to its own number (1, a platform
holds one). It never removes units.

It runs inside the server, on the player's session in memory:

- at the end of every battle (`processBattleComplete`, which campaign, PvP,
  war and raid battles all go through), and the battle's own save stores it;
- at login, because the client only learns its army when it logs in. After a
  battle the game shows the full army once it is reopened.

The file is read on every call, so changing the amount or the unit list needs
no restart. `autoRefillFile=refill.json` in `swc.properties` switches it on; an
empty value switches it off. `tools/refill_army.sh` uses the same file for a
refill by hand.

## Heavy Undead Trooper in the barracks (2026-10-05)

`EmpireHeavyStormDeath`, the Halloween-event heavy undead trooper, was in the
game data but hidden from the barracks by `playerFacing=false`. The client reads
unit data from the `trp.json.android.assetbundle` patch, not from the server, so
the bundle itself was changed: `playerFacing` is now true for all ten levels
and nothing else differs (10 bytes in the 1 MB data file). Level 1 needs
Barracks 10, higher levels the matching Research Lab, as for the normal heavy
trooper; training takes 120 s and 920 credits per unit.

The data inside the bundle is in the client's binary JOE format;
`tools/joe/joe.py` reads and writes it exactly. The bundle was rebuilt with
UnityPy and published as a new version, `swcFiles/1601000000/patches/`, so the
phone downloads it instead of using its cached copy. Only that one manifest
entry changed (version and CRC).

Its model is on the server, but two of its bundles are not: `audio_deathtrooper`
(sounds, also missing for the Death Trooper hero, which works anyway) and
`unittransport_event_troop_deathtrooper` (the event transport that delivers
trained units).

To undo, put the old manifest back:

```bash
cp swcFiles/manifests/__manifest_zyngaswc_prod.02045.json.before-undead \
   swcFiles/manifests/__manifest_zyngaswc_prod.02045.json
```

## Not done yet

- 962 art bundles are missing from the cache and return 404, mostly
  `*_fillstates` and campaign icons. The game tolerates them, but counts them
  as critical errors during loading.
- Campaign missions were added to one player in the database. The proper fix
  is for the server to build them from `cae.json` at login, so every player and
  both factions get them.
- `PlayerSessionImpl.buildingUpgradeAll` throws `IndexOutOfBoundsException` on
  an empty list.
- The Mac's address changes on every new network (DHCP). A DHCP reservation in
  the router would end the APK rebuilds.
- The changes are deliberately not committed to git.

If a reinstall creates a fresh player and the old base is gone, the old save
is still in the `player` collection. Stop the server, copy `playerSettings` and
`receivedDonations` from the old document into the new one, restart, then fully
reopen the game. See the 2026-10-02 entry in `NOTES.md`.
