#!/usr/bin/env python3
"""Extract plain JSON patch files from UnityFS JOE .android.assetbundle patches.

Reads patches/olc, holo, prk, epi, war, arc from their .android.assetbundle files
in swcFiles/ and writes the decoded JSON to the versioned path expected by
the manifest (and updates plain JSON CRCs in the manifests).
"""

import glob
import json
import os
import struct
import sys
import zlib
import UnityPy

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "joe"))
import joe

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SWC_FILES = os.path.join(ROOT, "swcFiles")
MANIFEST_DIR = os.path.join(SWC_FILES, "manifests")
MAIN_MANIFEST = os.path.join(MANIFEST_DIR, "__manifest_zyngaswc_prod.02045.json")

PATCHES = ["olc.json", "holo.json", "prk.json", "epi.json", "war.json", "arc.json"]


def decode_joe_bundle(bundle_path: str) -> dict:
    env = UnityPy.load(bundle_path)
    for obj in env.objects:
        if obj.type.name == "TextAsset":
            raw = obj.get_raw_data()
            nl = struct.unpack_from("<I", raw, 0)[0]
            off = 4 + nl + ((4 - (4 + nl) % 4) % 4)
            sl = struct.unpack_from("<I", raw, off)[0]
            parsed = joe.parse(raw[off + 4 : off + 4 + sl])
            objects = {}
            for sh in parsed["sheets"]:
                sname = parsed["S"][sh["name"]].decode("utf-8", "replace")
                cols = [(t, parsed["S"][ni].decode("utf-8", "replace")) for t, ni in sh["cols"]]
                nc = len(cols)
                rows = []
                for r in range(len(sh["cells"]) // nc):
                    row = {}
                    for ci, (t, cname) in enumerate(cols):
                        v = sh["cells"][r * nc + ci]
                        if v == 0:
                            continue
                        if t == 0:
                            row[cname] = parsed["S"][v - 1].decode("utf-8", "replace")
                        elif t == 1:
                            row[cname] = "true" if v == 2 else "false"
                        elif t == 2:
                            row[cname] = str(v - 1)
                        elif t == 3:
                            row[cname] = str(parsed["ints"][v - 1])
                        elif t == 4:
                            row[cname] = str(parsed["floats"][v - 1])
                        elif t == 5:
                            row[cname] = [
                                parsed["S"][x].decode("utf-8", "replace")
                                for x in parsed["arrays"][v - 1]
                            ]
                        else:
                            row[cname] = str(v)
                    rows.append(row)
                objects[sname] = rows
            return {"content": {"objects": objects}}
    raise ValueError(f"No TextAsset found in {bundle_path}")


def find_bundle_for_patch(patch_name: str, manifest_paths: dict) -> str:
    bundle_key = f"patches/{patch_name}.android.assetbundle"
    meta = manifest_paths.get(bundle_key)
    if meta:
        cand = os.path.join(SWC_FILES, str(meta["v"]), bundle_key)
        if os.path.exists(cand):
            return cand
    matches = sorted(glob.glob(os.path.join(SWC_FILES, "*", "patches", f"{patch_name}.android.assetbundle")))
    if matches:
        return matches[-1]
    raise FileNotFoundError(f"Bundle not found for {patch_name}")


def main() -> None:
    with open(MAIN_MANIFEST, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    paths = manifest.get("paths", {})
    updated = {}

    for patch_name in PATCHES:
        key = f"patches/{patch_name}"
        meta = paths.get(key)
        if not meta:
            print(f"Skipping {patch_name}: not in manifest")
            continue
        bundle_path = find_bundle_for_patch(patch_name, paths)
        decoded = decode_joe_bundle(bundle_path)
        dest_path = os.path.join(SWC_FILES, str(meta["v"]), key)
        os.makedirs(os.path.dirname(dest_path), exist_ok=True)
        payload = json.dumps(decoded, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        with open(dest_path, "wb") as f:
            f.write(payload)
        crc = f"{zlib.crc32(payload) & 0xFFFFFFFF:08X}"
        meta["crc"] = crc
        updated[key] = (meta["v"], crc)
        sheets = ", ".join(f"{k}({len(v)})" for k, v in decoded["content"]["objects"].items())
        print(f"Extracted {patch_name} -> {dest_path} (CRC {crc}): {sheets}")

    with open(MAIN_MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, separators=(",", ":"))

    for mpath in sorted(glob.glob(os.path.join(MANIFEST_DIR, "__manifest_zyngaswc_prod.*.json"))):
        if mpath == MAIN_MANIFEST:
            continue
        with open(mpath, "r", encoding="utf-8") as f:
            custom = json.load(f)
        changed = 0
        for key, (v, crc) in updated.items():
            entry = custom.get("paths", {}).get(key)
            if entry and entry.get("v") == v:
                entry["crc"] = crc
                changed += 1
        if changed:
            with open(mpath, "w", encoding="utf-8") as f:
                json.dump(custom, f, separators=(",", ":"))
            print(f"Synced {changed} CRCs in {os.path.basename(mpath)}")


if __name__ == "__main__":
    main()
