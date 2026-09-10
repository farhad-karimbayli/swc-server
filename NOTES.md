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
- **`arc.json` and `olc.json`.** Two of the four core data files. The author
  ships `base.json`, `cae.json` and `trp.json` in `src/main/resources/patches`,
  copied here into the folders the manifest points at. `coreJsonPatches` in
  `swc.properties` was reduced to the three that exist.
- **Base layouts.** `layouts/` is empty, so the `devBase` collection has no
  rows and there is nothing to attack yet.

## Checking it works

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  http://localhost:8080/swcFiles/manifests/__manifest_zyngaswc_prod.02045.json
```

200 means the manifest is being served, which is the first thing a client asks
for.
