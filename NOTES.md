# Local setup notes

State on 2026-09-10: **a patched client logs in successfully.** The player id
printed on the game's loading screen matches the document created in MongoDB,
which is proof the client is talking to this server and not to anything else.
The game then stops on the loading bar, waiting for asset bundles that do not
exist here.

## What was changed in the upstream code

Two startup bugs, both fixed in place:

- `Config.convertToObject` understood only `Integer` and `String`. Every
  `boolean` and `long` in a properties file became null, and the server died on
  a reflection error before printing anything useful. It now handles primitives
  and their boxed forms, and refuses an unknown type loudly instead of writing
  null.
- `GameDataManagerImpl.listFiles` iterated the result of `File.listFiles()`
  without a null check, so a missing patches folder crashed startup. Patches are
  optional content, so an absent folder now means "no patches".

## What is running

- MongoDB 7 in Docker as a one-node replica set named `rs0`, needed for
  transaction support. Container `swc-mongo`, port 27017.
- The server on port 8080, bound to all interfaces.
- `./run.sh` starts both and rewrites the client-facing address in
  `swc.properties` to this machine's current address.

## Pointing a client at this server

`tools/patch_metadata.py` rewrites the addresses inside a client's
`global-metadata.dat`. They live in two places and both must change: the string
literals the running code reads, and the C# field default values. Patching only
the defaults looks successful and changes nothing, which cost an entire
build-and-test cycle to discover.

The rebuilt package also needs `android:usesCleartextTraffic="true"` in its
manifest, because the client targets API 28 and this server speaks plain HTTP.

## What is still missing

- **Asset bundles.** The largest gap. The upstream author deliberately does not
  ship them. Without them a client connects but has nothing to draw.

  Every original source is gone, checked on 2026-09-10:
  `zynga-swc-prod-1-seed.akamaized.net` is no longer configured on Akamai, the
  bucket behind it answers `NoSuchBucket`, the Disney-era CDN
  (`starts0.content.disney.io/cloud-cms/`, the `{root}{codename}/{environment}/{relativePath}/{hash}`
  scheme described in the swcommander.com tutorial) resolves to an unmapped
  Google host, and the Wayback Machine has no captures of either. No mirror,
  archive, fork or repack anywhere holds them.

  The upstream README does **not** offer them, contrary to what this file said
  until 2026-09-29. Its section "Obtaining game asset bundle and modding" says
  the author is *undecided* about handing them out, because some of his own
  copies came off a random link and he will not vouch for a binary. The same
  section holds the only two leads he gives: that "some modelling site has
  provided some", and that a pre-shutdown install may still have assets in its
  Unity cache, which he thinks could be turned back into bundles but does not
  know how to do. No email or contact address appears anywhere in the repo.
- **`arc.json` and `olc.json`.** Two of the four core data files. The author
  ships `base.json`, `cae.json` and `trp.json` in `src/main/resources/patches`,
  copied here into the folders the manifest points at. `coreJsonPatches` in
  `swc.properties` was reduced to the three that exist. The manifest lists
  twelve core patches in total (`arc base cae epi fue holo olc prk reserved trp
  war wts`), so the three on hand are less of the game data than the four the
  default config asks for suggests.

## Dev bases

`layouts/` holds fifteen bases, so the `devBase` collection has rows and PVP has
something to match against: HQ 5 to 11 for both factions, plus
`empire_hq11_stock.json`, which is the hand built maxed base out of
`src/main/resources/existingPlayer/playerLogin.json`.

`tools/gen_layouts.py` writes them and `--check` verifies them. It reads
`BuildingData` out of `base.json`, so a base only ever contains buildings the
client knows, at the levels and quantities an HQ of that level is allowed, and
the checker rejects overlaps, buildings off the grid, a missing or wrong level
HQ, and any uid without a counterpart in the other faction. That last one
matters: `setupDevBases` stores a layout with "empire"/"rebel" replaced by
"neutral" and `getDevBaseMap` puts the attacker's faction back in, so a
building that exists in only one faction would come back as a uid that is in no
game data at all.

The buildable area is -22..21 on both axes. The real bases that ship with the
server occupy -19..21, which is where that figure comes from; no constant in
the game data states it.

## Where the client actually stops (emulator, 2026-09-11)

Tested on an Android 11 emulator (`system-images;android-30;google_apis;arm64-v8a`,
AVD `swc11`, started with `-gpu host`). The API 36 image is useless for this: the
app's process never even starts, and that image's own `system_server` spends its
time in debuggerd dumps. The `pitwall` AVD also has `hw.gpu.enabled=no`, which no
Unity game survives.

The client now gets all the way to asset loading. The exchange is:

```
auth.preauth.generatePlayer, auth.getAuthToken, config.endpoints.get,
player.login (46 KB), player.content.get, player.preferences.set
GET /swcFiles/manifests/__manifest_zyngaswc_prod.02045.json      200
GET /swcFiles/1584443067/patches/base.json.android.assetbundle   404
GET /swcFiles/1547730728/patches/arc.json.android.assetbundle    404
GET /swcFiles/1547730728/patches/fue.json.android.assetbundle    404
```

then "Connection Error! Unable to connect to the server."

Two things matter for what is still missing:

- The client asks for the patches **as asset bundles**, not as the JSON we have.
  `base.json` is on disk here and is still a 404, because what it requests is
  `patches/base.json.android.assetbundle`. So the three core patches we have are
  in the wrong shape for the client, and the other nine are missing outright.
- `player.content.get` is what drives all of this: it hands the client the CDN
  root (from `cdnRoots` in `swc.properties`), the manifest path and the list of
  13 patches. The templates in `src/main/resources/*/playerContentGet.json` have
  someone else's LAN address baked in, but the command overrides it from config,
  so that address never reaches the client.

Two server bugs were found and fixed getting here, both the same shape - a
value that is empty rather than null, against a check that only tests for null:

- `ObjectiveManagerImpl.planetSeries` was null, because the objective tables
  (`ObjSeriesData`, `ObjTableData`) live in a core patch we do not have, so
  `setup()` was never called. Every `player.login` after the first died with an
  NPE and the route answered 404, and the client retried forever. The field now
  starts as an empty map, which is the same "no objectives" case the code
  already handles per planet.
- `SessionManagerImpl.setFromModel` only filled in campaigns and missions when
  `playerCampaignMission` was null, but `PlayerSettings` initialises it to an
  empty object, so every player got `"campaigns": null, "missions": null` where
  the real server sent 13 campaigns and a mission. The client accepted the login
  and then sat on the loading bar without ever calling `player.content.get`. It
  now checks the two maps inside as well.

A player saved before that second fix keeps the empty object, so testing it
needs `db.player.deleteMany({})` and `adb shell pm clear com.lucasarts.starts_goo`.

The repeated `java.net.ConnectException: Failed to connect to /127.0.0.1:1` in
logcat is not a fault: `patch_metadata.py` points Swrve and Bugsnag there on
purpose. Redirecting that port to the server inside the emulator
(`adb root; adb shell iptables -t nat -A OUTPUT -p tcp --dport 1 -j DNAT --to-destination <host>:8080`)
stops the retries and changes nothing else, so those SDKs are not the gate
either.

## Checking it works

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  http://localhost:8080/swcFiles/manifests/__manifest_zyngaswc_prod.02045.json
```

200 means the manifest is being served, which is the first thing a client asks
for.

## The 2018 tablet, checked 2026-09-29

A dead end, recorded so it is not chased twice. An old tablet still held the
game's data directory from a live-game install (`files/Swrve_JoinedDate…` =
1525342331621, so 2018-05-03), copied to `~/claude_console/com.lucasarts.starts_goo`.

`files/UnityCache/Shared` has 143 bundle folders and every one of them is empty,
both in the copy and on the tablet itself, checked there with a file manager.
The folder names are all that survived, and they do say what that install had
cached: all twelve core patches (`arc base cae epi fue holo olc prk reserved trp
war wts`), `strings_ru-ru`, eight `ab_*` A/B configs and 122 content bundles.
No `__data`, no `__info`, no hash subfolder anywhere.

The rest of that upload is chrome: `files/il2cpp/global-metadata.dat` is 5.67 MB
against the 8.65 MB in the 7.8.1 APK, so it belongs to an older build, and it is
unpatched (`zynga-swc-prod-1-seed.akamaized.net/1541022029/connection_test.txt`,
`swc-integration-prod.apps.starwarscommander.com/starts` and the shipped default
`http://localhost:8080/starts` are all still in it). Plus 102 compiled shaders
and Swrve junk.

Scale, for whenever a source does turn up: the manifest lists 2187 bundles under
`assetbundles/android/` and 103 under `patches/`. A player's cache was only ever
what they looked at, so no single device is going to carry the content.

## Building our own patch bundles

The one route that needs nobody else. The client is Unity **2017.4.27f1**
(`assets/bin/Data/globalgamemanagers`), and the bundles that ship inside the APK
are `UnityRaw` tagged 5.x.x, so an editor of the client's own version can build
something it will load. `base.json`, `cae.json` and `trp.json` are on disk as
raw JSON, and what the client asks for is those same files wrapped as
`patches/<name>.json.android.assetbundle`. The manifest is ours, so the `crc`
and `v` of an entry can be rewritten to match whatever we build.

Unknowns: the asset name and type expected inside the bundle, and whether the
nine patches we do not have are optional. It would not restore any art, but it
is the only way to find out what the client does after the 404s stop.

## Asset bundles obtained, 2026-09-30 — the big unblock

A community member (same scene as darthRebel) shared a Unity cache export that
actually has bundle data in it, unlike the empty tablet:
`mediafire.com/file/albhe59f5aen4uy` ("Star Wars Commander cache.zip", 351 MB).
Every folder holds hash subfolders with `__data` + `__info`; `__data` is a raw
UnityFS assetbundle. Rename it to the folder name and serve it.

Extracted to a scratchpad; 3205 asset folders. What went into `swcFiles`:

- **All 12 core patches** (`base arc fue cae trp holo olc war prk wts epi
  reserved`). Each patch folder had several cached builds; picked the newest by
  `__info` line 2 (a build timestamp), all from 1584484313/14 = the last version
  before shutdown, which matches the late manifest 02045.
- **1215 of 2187 content bundles.** 962 are simply not in this cache (the sharer
  warned some are missing; he substituted e.g. a L6 Sonic Cannon for the missing
  L5). Missing ones are art that will just not draw.

Placement: `swcFiles/<v>/patches/<name>.json.android.assetbundle` and
`swcFiles/<v>/assetbundles/android/<name>.assetbundle`, where `<v>` is the
per-entry version from the manifest (that is the path the client builds from
`player.content.get` + manifest).

**CRC:** the manifest crc did not match any cached build (these are different
builds than manifest 02045 described), so the manifest is ours and each entry's
`crc` was rewritten to the CRC32 of the file actually served. If the client
turns out not to verify crc this was harmless; if it does (Unity passes the
manifest crc to the bundle loader, which CRC32s the downloaded bytes), this is
what makes it pass. `__manifest_...json.orig` is the untouched backup.
`swcFiles` is now 251 MB, 12 + 1215 bundles, all serving 200.

## The machine IP drifts (DHCP)

Bit us once here: the box moved 192.168.0.101 -> .105 mid-session, so the phone
APK and the running server's `cdnRoots` were both stale and everything answered
000/404. `run.sh` rewrites `cdnRoots`/`event2Bi...` in swc.properties to the
current en0 address at each start, so a restart fixes the server, but the APK
has the address baked into global-metadata and must be rebuilt to match. Client
APKs are per-address: `client/swc-patched-<ip>.apk`. A DHCP reservation for this
machine would end the churn.

## Rebuilding a client APK for a new address

Reproducible now, keystore no longer a blocker (kept at `client/swc-dev.keystore`,
storepass/keypass `swcserver`, alias `swc`):

1. Extract `assets/bin/Data/Managed/Metadata/global-metadata.dat` from the
   pristine `star-wars-commander-7-8-1-253.apk`.
2. `python3 tools/patch_metadata.py <that file> <ip>:8080`.
3. Copy any already-patched apk (has the cleartext manifest + rebuilt res),
   `zip` the new metadata over it, `zip -d` the old `META-INF/SWC.*` + MANIFEST.MF.
4. `zipalign -f -p 4`, then `apksigner sign --ks client/swc-dev.keystore ...`.

Build-tools live at `/opt/homebrew/share/android-commandlinetools/build-tools/36.1.0`.
Signing with swc-dev.keystore keeps the signature stable, so a rebuilt APK
installs over the previous one without an uninstall.

## 2026-09-30 23:17 — IT RUNS. Base loads on the phone, no error.

The whole thing works end to end on a real phone (Samsung SM-S918B, Android):
patched APK -> login -> player.content.get -> manifest -> all 12 patch bundles
200 -> base art streams in -> Empire HQ11 home base renders in 3D -> playable.
The Connection Error that showed at first cleared after a Reload once strings
were in place.

Two things learned here, both about how the client treats a load-time 404:

- **The client tags every failed asset fetch during ApplicationLoadState as a
  "Critical error"** (seen in its own `client event` telemetry, t5). Enough of
  them and it throws the "Connection Error! Unable to connect" dialog even though
  login and the base loaded fine. So a missing bundle is not cosmetic during
  load - it can gate the session. After a Reload with everything in place it
  cleared, so it tolerates a few, but this is the thing to watch.
- **Strings are a third path**, `strings/<name>.json.android.assetbundle`, not
  `patches/` or `assetbundles/android/`. The client always loads base `en-us`
  (`strings_en-us`, `strings-hn_en-us`) regardless of device locale (device was
  ru_RU). Missing them showed every label as `LANG: <KEY> missing` on screen and
  looped the loader. Both en-us bundles were in the cache; placed under
  `swcFiles/1576242239/strings/` and `swcFiles/1584443067/strings/`.

Remaining 404s are the 962 art bundles this cache lacks: `*_fillstates` (100 in
manifest, 71 in cache, 29 missing) and some `chap_btn_cmp_*` campaign icons.
Substituting a nearby level (e.g. tradecenter up8 for the missing up11
fillstates), the way the cache's donor did for the L5 Sonic Cannon, is the way
to silence them.

## Editing a player's resources (works, 2026-09-30)

Resources live in the player's Mongo doc at
`playerSettings.inventoryStorage.<res>.amount` for
credits, materials, contraband, crystals, droids (droids.amount = number of
build/repair droid workers). Set them with a `db.player.updateOne` in the
`swc-mongo` container. credits/materials/contraband are capped in-game by the
player's storage-building capacity (7.2M / 7.2M / 930k on the stock HQ11 base),
so setting amount to capacity is the sane max; crystals have no storage cap.

Two gotchas cost a few rounds here:

- **The server keeps the PlayerSession in memory.** A DB edit does not reach a
  client that is already connected, and the in-memory session (with the old
  numbers) will save back over the DB. An in-game "Reload" is not enough. The
  reliable sequence is: edit the DB, hard-restart the server (`pkill -f
  swcnoops.server.SparkMain` then `./run.sh`/boot.sh — a kill does not flush, so
  the DB keeps the new values), then have the client **fully close and reopen**
  so login reads the DB fresh.
- **A reinstall makes a brand-new player.** The client is a guest account with
  no stored credentials, so reinstalling the APK (or clearing its data) creates
  a fresh empty player document with a new id; the old edited doc is orphaned.
  The active player is the last `guest.ply` in the server log. Reopen, don't
  reinstall, to keep progress. After several reinstalls the DB had four `player`
  docs; the live one is whichever id the phone last logged in as.

## Story/campaign missions were empty — fixed 2026-10-01

Symptom: playing as Empire, the story-mission chapters showed but had no
missions to play. Cause: the player's `playerSettings.playerCampaignMission`
seeds `campaigns` (13, incl. all 12 empire chapters) but `missions` from the
template `existingPlayer/playerLogin.json`, and that template holds exactly ONE
mission (`fue_defendBaseMission_00`, the tutorial). So every chapter was empty
for everyone, both factions. The mission definitions do exist: `cae.json`
(`config.caeJson = patches/manifest45/cae.json`) has 495 `CampaignMissionData`,
193 of them under empire campaigns, and GameDataManager loads them into
`CampaignSet`/`CampaignMissionSet` at startup — the server has the data, it just
never put it in the player's map. The `player.missions.missionMap` command
handler is a stub (returns only a battleId).

Fix applied (data, per-player): generated a mission-status entry for every
empire-campaign mission from cae.json and merged it into the player's
`playerCampaignMission.missions` in Mongo (109 added, 110 total). Entry shape,
copied from the working tutorial mission:

  {activated:false, campaignUid, collected:false, completed:false, counters:{},
   earnedStars:0, grindMissionRetries:0, locked:false, lootRemaining:{},
   status:"Default", uid, goals:<len(victoryConditions) or 3>,
   lastBattleId:null, satisfiedGoals:[]}

Generator + mongo merge script were built in the scratchpad (set_missions.js).
Confirmed working on the phone after a full close/reopen.

The permanent fix (not done yet) is server-side: build the missions map from the
loaded CampaignMissionSets at login instead of from the sparse template, so every
player and both factions get it without a per-player DB edit.

## Attack (PvE campaign) missions wouldn't load — fixed 2026-10-01

Symptom: offensive story missions failed to load. Cause: the client fetches the
enemy base layout for a mission directly from
`swcFiles/<v>/battles/<name>.json` (name like `mis_tbft_4_e`, i.e.
mis_<campaignShort>_<n>_<factionLetter>), and the server shipped only 3 sample
battle layouts (`Fue_impShow_00`, `Fue_rebShow_00`, `mis_utts_2`). Every other
mission's map 404'd. `PlayerPveStart` only mints a battleId; it does not serve
the layout, so the missing file is the whole problem. The layouts are a base
design: `{"map":{"next":0,"planet":"planet1","buildings":[{key,x,z,uid},...]}}`
— the same shape as our `layouts/*.json` (which are just the buildings array).

Fix: `SparkMain.GetFile` now falls back to `swcFiles/battles/_default_mission.json`
for any missing `/battles/*.json` instead of 404. That default was built by
wrapping `layouts/empire_hq8a.json` in the battle envelope (159 buildings). One
change makes every attack mission load and be playable, both factions. It is a
generic empire base, not the original per-mission design (those are lost), and
the enemy faction isn't matched to the mission yet — cosmetic, can be refined by
generating per-mission/per-faction bases later.

Separate, unrelated bug seen in the same logs (not blocking missions):
`PlayerSessionImpl.buildingUpgradeAll` (line ~1325) throws
`IndexOutOfBoundsException: Index 0 out of bounds for length 0` on the
`player.building.upgradeAll` path — reads index 0 of an empty list. Left for
later.

## Per-mission enemy bases, correct faction + scaled difficulty (2026-10-01)

Replaced the single generic fallback base with real per-mission battle files.
The client requests `mis_<short>_<num>_<fac>.json` (fac = the campaign/player
faction). Built 217 of them into `swcFiles/1541022029/battles/` from cae.json's
CampaignMissionData: `<short>`/`<num>`/`<fac>` parsed from each mission's `map`
field (naming is inconsistent across campaigns — `bat_1_utts_e_1` vs
`bat_11_tbft_1_e` — so parse tokens: the e/r token is faction, the alpha token
is short, the last number is the mission number), and `bi_enemy_tier` gives
difficulty.

Each file's enemy base is the OPPOSITE faction (empire mission -> rebel base,
`_e` -> rebel) at HQ = clamp(tier+1, 3, 9), which keeps it beatable for a maxed
HQ11 player. Bases are generated with `tools/gen_layouts.py`'s `generate()`
(which main() only ever called for empire) — it also handles rebel; every base
passes the same on-grid / valid-uid / both-faction-parity checks. Generation
caches one base per (faction, hq), so only 14 distinct bases back all 217 files.
The `GetFile` `_default_mission.json` fallback stays as a safety net for any
name not covered.

Still generic layouts, not the original hand-designed mission maps (those are
lost), but now faction-correct and difficulty-scaled. Regenerate with the inline
script in this session's history if cae or the generator changes.


## Battle difficulty lowered (2026-10-01)
User found the generated enemy bases too hard. Enemy HQ mapping changed from
clamp(tier+1, 3, 9) to clamp(tier-1, 2, 5); default fallback base from rebel HQ6
to rebel HQ4. All 217 battle files regenerated. Bump back up by rerunning the
generation script with a higher hq_for() if it becomes too easy.

## 2026-10-01 16:55 — client rebuilt for 192.168.226.210

New network, en0 = 192.168.226.210. Built `client/swc-patched-192.168.226.210.apk`
with the four-step pipeline above (base: the .105 apk), apksigner verify passes,
4 occurrences of the new address in global-metadata. The server was up but
Docker was not, so every Mongo call failed until `docker start swc-mongo`; the
driver reconnected on its own, no server restart needed.

## 2026-10-02 — client rebuilt for 192.168.0.108, old APKs removed

New network again, en0 = 192.168.0.108. Built `client/swc-patched-192.168.0.108.apk`
(base: the .105 apk, then deleted). All older client APKs and .idsig files were
removed at the user's request; the .108 one is now the base for the next rebuild.
Server restarted via `./run.sh` so cdnRoots follows the new address. Summary of
every change vs upstream is in `README_CHANGES.md`.

## 2026-10-02 20:10 — restored the user's base after a fresh-player login

After installing the .108 APK the phone logged in as a brand-new guest
(`dad436c8-...`, template base, 1 mission), so the app's data had been wiped.
The real save was `533f2c14-...` (last active 2026-10-01, 111 missions, 231
buildings). Since the phone only holds the new player's id and secret, the fix
was to copy `playerSettings` + `receivedDonations` from the old doc into the new
one with the server stopped, then restart. Both docs were backed up first to
the `player_backup` collection (`<id>@2026-10-02-...`). This is the general
recipe whenever a reinstall orphans a save.

## 2026-10-02 20:20 — new players start from zero again

User wanted a fresh base by default for both factions instead of the maxed HQ11
one. New config `newPlayerTemplate=newPlayer/playerLogin.json` is now what
`auth.preauth.generatePlayer` and the missing-account path in `getAuthToken`
create players from: smuggler HQ1, `currentQuest=fue_init_00`, tutorial on.
`player.faction.set` (upstream code) then converts the base to empireHQ1 or
rebelHQ1 and adds `camp_1_utts_e_1` / `camp_1_utts_r_1`.

`playerLoginTemplate` stays `existingPlayer`, because login copies the template's
unmapped fields (activeArmory capacity, shardShopData, sharedPrefs tutorial
flags...) into every response. `PlayerLogin` now picks the new-player template
only while `currentQuest` is non-empty, so finished players are untouched.

Also fixed: new players got 0 credits/materials. `PlayerSettings` initialises
`inventoryStorage` to an empty object, so `defaultInventoryIfNull` never fired
(same empty-not-null shape as the campaignMission bug). `newPlayer` now calls
`resetPlayerSettings` before `setFromModel`.

Tested over HTTP with a script that generates a player, signs the auth token,
logs in and sets each faction: both come back HQ1 with 1000 credits and the
right first mission. Test players were deleted afterwards. The tutorial itself
has not been played through on the phone yet.

## 2026-10-02 20:30 — army filled: 10 of every Empire infantry and vehicle unit

Set `playerSettings.deployableTroops.troop.<unitID> = 10` on the user's player
(`dad436c8-...`) for every `playerFacing=true` Empire unit of type `infantry`
(15) or `vehicle` (12) in trp.json; samples, trap and event variants left out.
Keys are unitIDs; login turns them into `troop<unitID><level>` from the player's
upgrade levels (all 11, transport 10). That is ~2000 housing, far over camp
capacity; the server does not clamp it, same as the 10-each starships.
Backup in `player_backup` as `<id>@2026-10-02-before-troops`. Verified with a
signed player.login: 27 troop types, 10 each.

## 2026-10-02 20:35 — heroes set to 10 each

Same edit for `deployableTroops.hero`: all 8 `playerFacing` Empire heroes
(EmpireJohhar, HeroATAT, HeroATMP, HeroATST, HeroCrimsonGuard, HeroDarthVader,
HeroDeathTrooper, HeroEmpireSpiderDroid) from 1 to 10. SummonedDeathTrooper left
out (summon-only). Backup `<id>@2026-10-02-before-heroes`. Signed login confirms
8 heroes x10 at level 11, troops still 27 x10.

## 2026-10-02 20:45 — army refilled after battles

User spent troops, heroes and starships in campaign battles and asked for the
army back. Reset `deployableTroops` to the post-fill state: troop 27 x10,
hero 8 x10, specialAttack 22 x10 (from the `before-heroes` backup, which still
had the full starship set incl. both ImperialDropship variants), champions 1
each. Resources, base and mission progress left as they were. Backup of the
spent state: `<id>@2026-10-02-before-refill`. Signed login confirms the counts.

## 2026-10-02 20:40 — fortified base, reversible

`tools/fortify_base.py` lays out the user's base as three closed wall rings
(Chebyshev d = 5.5 / 10.5 / 14.5 around 0.5) plus spokes, using only the -20..21
area seen in official data. Keeps all 231 buildings (keys and fields), adds 144
walls, 24 turrets (round robin over the uids already present: Burst, Rocket,
Sonic 11) and 4 ShieldGenerator11. Total 403, next 820 -> 992.

Applying is done inside Mongo by `tools/apply_base_layout.js` (via
`tools/base_layout.sh <id> <layout>`), not by rewriting JSON: lastCollectTime is
a BSON long and x/z/currentStorage int32, and a JSON round trip would turn them
into doubles or {low,high} objects. Round-trip test on a copy (fortified ->
original) gave a byte-identical baseMap except `next`, which stays at the
higher value. Signed login after applying: 403 buildings, status 0, no
exceptions. Not yet seen on the phone.

## 2026-10-02 20:47 — starports had no room; droideka platform inside one

Complaint: "transports have no room, a droideka is inside one, the animation
breaks". Two causes. fortify_base.py packed the starports with 2-9 free ring
tiles (the original base had 17-25 of 28), so housed troops had nowhere to
stand. And the user's own multimove put empirePlatformHeavyDroideka50 (bld_429)
at (-3,17), entirely inside starport bld_91 at (-4,16): the client allowed it
and the server stores whatever it is sent, no overlap check.

The user had also moved 49 buildings in-game (two starports to the bottom
corners, at z=-21: the client accepts z=-21, so the board reaches at least that
far). Kept all of that. `tools/clear_starports.py <live.json> <out.json> <next>`
moved the platform back into the middle ring, moved bld_91 to (-3,-21) next to
the other two, and evicted buffer buildings, one trap and turrets from each
starport's 1-tile ring; walls, shields, HQ and platforms are never moved by it.
All three starports now have every in-board ring tile free. Applied via
base_layout.sh as `fortified_v2`; signed login matches the file.

To do: fortify_base.py itself should reserve that ring around starports
(and arguably factories) before it packs the outside zone.

## 2026-10-02 20:55 — army refilled again

Same reset of deployableTroops to the full set (troop 27x10, hero 8x10,
specialAttack 22x10, champions 1). The DB only showed 11 starships spent;
troops and heroes were already at 10 there. Backup `<id>@<time>-before-refill`.

## 2026-10-02 — `tools/refill_army.sh`

Third refill request, so the refill is now a script: stops the server, backs the
player up to `player_backup`, sets deployableTroops to the full set (troop 27x10,
hero 8x10, specialAttack 22x10, champions 1 each), restarts. This run was
missing 260 troops, 1 hero, 50 starships and both champions.

## 2026-10-02 — starships raised to 20 each

User asked for 20 of every starship. `refill_army.sh` now targets specialAttack
x20 (troops, heroes x10 and champions x1 unchanged), so later refills keep 20.

## 2026-10-03 00:15 — automatic army refill after every battle

User asked for every unit to be topped up to 20 after each battle. An external
script cannot do it (it has to stop the server), so it is server-side:
`session/AutoRefill.java`, called at the end of
`PlayerSessionImpl.processBattleComplete` (pve, pvp, war, raids all pass through
it and save right after) and in `PlayerLogin` after `playerLogin(time)` (the
client only syncs its army at login). It tops up the TrainingManager's
DeployableQueues via `initialiseDeployableUnits`, which also recounts housing.
Config: `autoRefillFile=refill.json` (players, amount 20, unit lists, champions
1). `refill_army.sh` now reads the same file.

Tested on a copy of the player (zz-refill-test, deleted after): login raised
everything to 20; pve.start, deployable.spend of 9 units (DB showed Storm 15,
ATAT 19, Vader 19, TieFighter 18), pve.complete -> DB back to 20, log line
"auto refill after battle ... 9 units added". Real player then logged in at 20.

## 2026-10-03 20:25 — client rebuilt for 192.168.0.101

DHCP moved the Mac from .108 to .101. Built `client/swc-patched-192.168.0.101.apk`
(base: the .108 apk, then deleted, so only the newest is kept), verify passes,
4 occurrences of the new address. Server restarted so cdnRoots follows; manifest
200 on the new address.

## 2026-10-05 00:15 — client rebuilt for 192.168.0.110; base apk came from git

Mac moved to .110. Every patched apk had been moved out of client/ (only .idsig
left), so the base for step 3 was `git show HEAD:client/swc-patched-10.124.18.139.apk`
(has usesCleartextTraffic=true). Output is the same 89117581 bytes as earlier
builds, signed with swc-dev.keystore, so it installs over the phone's copy.
The Mac's MAC (be:61:89:...) is a macOS private Wi-Fi address, which is why the
IP keeps drifting; the user was told to switch it off and reserve an IP.

## 2026-10-05 — IP pinned: 192.168.0.110

User switched off the macOS private Wi-Fi address (MAC now the hardware
bc:d0:74:b1:bc:11) and reserved 192.168.0.110 for it in the router; the lease
is now infinite (0xffffffff). The .110 client apk should no longer need rebuilds.

## 2026-10-05 10:00 — Heavy Undead Trooper made trainable

`EmpireHeavyStormDeath` (10 levels) had playerFacing=false; everything else is
like HeavyStorm (lvl1 needs empireBarracks10, then OffenseLab N). The client
takes TroopData from the trp patch bundle, whose TextAsset `trp.json.joe` is in
Zynga's JOE format. Reverse-engineered it against trp.json (format in
tools/joe/joe.py; round trip is byte-identical; bool cells 1=false 2=true,
string cells = index+1). Flipped the 10 playerFacing cells, re-saved the bundle
with UnityPy (`packer="original"`, same UnityFS v6 / 2017.4.27f1 / LZ4 flags,
283265 vs 335216 bytes because of block packing), published as
swcFiles/1601000000/patches/trp.json.android.assetbundle, and pointed the
manifest entry at v=1601000000 with crc = CRC32 of the file (same rule as every
other entry). Backup: `__manifest_...json.before-undead`. Server side needs
nothing: trainTroops takes any uid; tested on a copy of the player, 2 units
queued (120 s, 920 credits each). Not yet seen on the phone.

Missing for this unit: audio_deathtrooper, unittransport_event_troop_deathtrooper.
The Mac was on 10.124.18.139 at the time (away from the home network), so the
.110 apk could not reach it; nothing was rebuilt for that.

## 2026-10-28 — Authentic story mission battle maps regenerated

Previous battle generator had two problems:
1. It named files using an ad-hoc tokenized scheme (`mis_<short>_<num>_<fac>.json`) instead of `BattleData.assetName`. The client requests `assetName.json`, so almost every request hit the fallback generic `_default_mission.json` base.
2. It generated generic cookie-cutter HQ2-5 Rebel/Empire bases for every mission, completely ignoring story context (Tusken camps, Sand People sacred waters, Womp Rat nests, Krayt dragon graveyards, Rancor dens, Smuggler/Mercenary hideouts, Shipwrecks, and specific boss fights). Furthermore, mission encounter triggers (`DefenseEncountersProfiles`), scripts (`BattleScripts`), and objective targets (`Conditions`) were missing their required buildings.

`tools/gen_mission_battles.py`:
- Analyzes all 168 PvE battle missions across 147 unique battle assets from `cae.json`, `BattleData`, `StoryActions`, `LocalizedStrings`, `DefenseEncountersProfiles`, and `Conditions`.
- Categorizes missions into: Womp Rat Infestations, Tusken Raider Camps, Smuggler/Mercenary Bases, Krayt Dragon Graveyard, Rancor Dens, Atom Edge Shipwrecks, Rebel Military Outposts, Empire Garrisons, and Planetary Outposts across 6 planets.
- Correctly scales difficulty and HQ level to match the campaign curve (HQ 1-3 early, HQ 3-5 mid, HQ 5-7 late, HQ 8-9 bosses).
- Places all mandatory buildings required by encounter triggers and victory conditions (e.g. `storyKraytBones1`, `smugglerShieldGenerator5`, `tuskenHouseLarge2`, `rebelOffenseLab1`, `rebelDefenseGenerator`, `storyEmpShieldGen1`, `rebelTacticalCommand4`, boss HQs).
- Ensures valid non-overlapping coordinates within the grid (-22..21) and sets the authentic planet identifier.
- Generates all 147 primary `assetName.json` files and creates aliases (by map UID, mission UID, and legacy tokenized names) in both `swcFiles/1541022029/battles/` and `swcFiles/battles/`. Preserves the 3 original surviving maps (`Fue_impShow_00.json`, `Fue_rebShow_00.json`, `mis_utts_2.json`).

## 2026-10-28 — Intelligent substitution of missing critical Android asset bundles

Audited missing asset bundles: out of 2,187 Android bundles in the manifest, 972 were missing from disk.
Real game logs showed recurring 404s on specific assets during base load and campaign navigation (`tradecenter_emp-mod-up11_fillstates`, `chap_btn_cmp_11_tbft_e`, `spiderdroidholo_emp-mod`, `healingshipcargo_emp-mod`, `audio_empiredemodroid`, `audio_rebeldemodroid`).

`tools/substitute_missing_bundles.py`:
- Performs type-safe semantic substitution for 139 missing bundles:
  - All 30 missing `*_fillstates` (matched to closest level/opposite faction fillstate)
  - All 9 missing `chap_btn_cmp_*` chapter buttons (matched to opposite-faction chapter buttons)
  - All 46 missing `*holo*` (hologram miniatures and dialogue transmission portraits)
  - All 21 missing `audio_*` sound effects (matched to droid/unit sound banks)
  - 25 missing `*-up<lvl>` building upgrade models (matched to adjacent upgrade levels)
  - Starships, troop transports and skin variants
- Copies/links each asset into `swcFiles/<v>/assetbundles/android/`.
- Recalculates the CRC32 of each file and updates the corresponding entry in `__manifest_zyngaswc_prod.02045.json` so Unity's cache-and-download verification passes with zero CRC mismatches. Backup in `__manifest_...json.before-substitutions`.
- Android bundles on disk increased from 1,215 to 1,354. Verified 1,354 / 1,354 with zero CRC mismatches.


