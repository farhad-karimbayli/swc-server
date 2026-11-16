#!/usr/bin/env python3
"""Activate live Objectives, Shard Shop, Planetary Conflicts, and Episodes.

Patches olc.json, cae.json, and epi.json (both .android.assetbundle JOE binaries
and plain JSON files) into swcFiles/1611000000/patches/ with active 2026-2030
schedules so both the Android client and the Java server treat them as live.
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
from extract_joe_patches import decode_joe_bundle

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SWC_FILES = os.path.join(ROOT, "swcFiles")
MANIFEST_DIR = os.path.join(SWC_FILES, "manifests")
MAIN_MANIFEST = os.path.join(MANIFEST_DIR, "__manifest_zyngaswc_prod.02045.json")
NEW_VERSION = 1611000000
DEST_DIR = os.path.join(SWC_FILES, str(NEW_VERSION), "patches")


def load_joe_from_bundle(bundle_path: str) -> dict:
    env = UnityPy.load(bundle_path)
    for obj in env.objects:
        if obj.type.name == "TextAsset":
            raw = obj.get_raw_data()
            nl = struct.unpack_from("<I", raw, 0)[0]
            off = 4 + nl + ((4 - (4 + nl) % 4) % 4)
            sl = struct.unpack_from("<I", raw, off)[0]
            script_bytes = raw[off + 4 : off + 4 + sl]
            parsed = joe.parse(script_bytes)
            parsed["tail"] = script_bytes[parsed["end"] :]
            return parsed
    raise ValueError(f"No TextAsset in {bundle_path}")


def save_joe_bundle(source_bundle_path: str, target_bundle_path: str, target_asset_name: str, joe_bytes: bytes) -> None:
    env = UnityPy.load(source_bundle_path)
    target_bundle_name = os.path.basename(target_bundle_path)
    for obj in env.objects:
        if obj.type.name == "TextAsset":
            data = obj.read()
            data.m_Name = target_asset_name
            data.m_Script = joe_bytes.decode("utf-8", "surrogateescape")
            data.save()
        elif obj.type.name == "AssetBundle":
            data = obj.read()
            data.m_Name = target_bundle_name
            data.m_AssetBundleName = target_bundle_name
            data.save()
    os.makedirs(os.path.dirname(target_bundle_path), exist_ok=True)
    with open(target_bundle_path, "wb") as f:
        f.write(env.file.save(packer="original"))


def add_str(j: dict, text: str) -> int:
    b = text.encode("utf-8")
    j["S"].append(b)
    return len(j["S"])


def get_sheet(j: dict, name: str):
    for sh in j["sheets"]:
        if j["S"][sh["name"]].decode("utf-8", "replace") == name:
            cols = [j["S"][c[1]].decode("utf-8", "replace") for c in sh["cols"]]
            return sh, cols
    raise KeyError(f"Sheet {name} not found")


def get_str(j: dict, sh: dict, cols: list, row: int, col_name: str) -> str:
    idx = cols.index(col_name)
    cell = sh["cells"][row * len(cols) + idx]
    return j["S"][cell - 1].decode("utf-8", "replace") if cell > 0 else ""


def set_str(j: dict, sh: dict, cols: list, row: int, col_name: str, value: str) -> None:
    idx = cols.index(col_name)
    sh["cells"][row * len(cols) + idx] = add_str(j, value)


def patch_olc(src_bundle: str) -> bytes:
    j = load_joe_from_bundle(src_bundle)
    sh, cols = get_sheet(j, "ObjSeries")
    nrows = len(sh["cells"]) // len(cols)
    updated_obj = 0
    for r in range(nrows):
        uid = get_str(j, sh, cols, r, "uid")
        if uid.endswith("_series22"):
            set_str(j, sh, cols, r, "startDate", "08:00,01-01-2026")
            set_str(j, sh, cols, r, "endDate", "08:00,01-01-2030")
            updated_obj += 1

    sh_ss, cols_ss = get_sheet(j, "ShardShopSeries")
    for r in range(len(sh_ss["cells"]) // len(cols_ss)):
        set_str(j, sh_ss, cols_ss, r, "startDate", "00:00,01-01-2026")
        set_str(j, sh_ss, cols_ss, r, "endDate", "00:00,01-01-2030")

    print(f"Patched olc.json: {updated_obj} ObjSeries + ShardShopSeries -> 2026..2030")
    return joe.write(j, j["tail"])


def patch_cae(src_bundle: str) -> bytes:
    j = load_joe_from_bundle(src_bundle)
    sh, cols = get_sheet(j, "TournamentData")
    nrows = len(sh["cells"]) // len(cols)
    last_by_planet = {}
    for r in range(nrows):
        planet = get_str(j, sh, cols, r, "planetId")
        if planet:
            last_by_planet[planet] = r

    for planet, r in sorted(last_by_planet.items()):
        set_str(j, sh, cols, r, "startDate", "00:00,01-01-2026")
        set_str(j, sh, cols, r, "endDate", "00:00,01-01-2030")
        if "bonusEndTime" in cols:
            set_str(j, sh, cols, r, "bonusEndTime", "00:00,02-01-2030")

    print(f"Patched cae.json: {len(last_by_planet)} planetary conflicts -> 2026..2030")
    return joe.write(j, j["tail"])


def patch_epi(src_bundle: str) -> bytes:
    j = load_joe_from_bundle(src_bundle)
    sh, cols = get_sheet(j, "EpisodeData")
    nrows = len(sh["cells"]) // len(cols)
    tasks_idx = cols.index("tasks")
    target_row = None
    for r in range(nrows):
        tasks_cell = sh["cells"][r * len(cols) + tasks_idx]
        if tasks_cell > 0 and len(j["arrays"][tasks_cell - 1]) > 0:
            target_row = r
    if target_row is not None:
        uid = get_str(j, sh, cols, target_row, "uid")
        set_str(j, sh, cols, target_row, "startDate", "00:00,01-01-2026")
        set_str(j, sh, cols, target_row, "duration", "P1800D")
        print(f"Patched epi.json: active episode {uid} -> 2026..2030")
    return joe.write(j, j["tail"])


def crc32_file(path: str) -> str:
    with open(path, "rb") as f:
        return f"{zlib.crc32(f.read()) & 0xFFFFFFFF:08X}"


def main() -> None:
    os.makedirs(DEST_DIR, exist_ok=True)
    specs = [
        ("olc.json", os.path.join(SWC_FILES, "1584443067", "patches", "olc.json.android.assetbundle"), patch_olc),
        ("cae.json", os.path.join(SWC_FILES, "1584443067", "patches", "cae.json.android.assetbundle"), patch_cae),
        ("epi.json", os.path.join(SWC_FILES, "1579097498", "patches", "epi.json.android.assetbundle"), patch_epi),
    ]

    updates = {}
    for patch_name, src_bundle, patch_fn in specs:
        joe_bytes = patch_fn(src_bundle)
        dest_bundle = os.path.join(DEST_DIR, f"{patch_name}.android.assetbundle")
        save_joe_bundle(src_bundle, dest_bundle, patch_name, joe_bytes)
        decoded = decode_joe_bundle(dest_bundle)
        dest_json = os.path.join(DEST_DIR, patch_name)
        with open(dest_json, "wb") as f:
            f.write(json.dumps(decoded, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))

        updates[f"patches/{patch_name}.android.assetbundle"] = {"crc": crc32_file(dest_bundle), "v": NEW_VERSION}
        updates[f"patches/{patch_name}"] = {"crc": crc32_file(dest_json), "v": NEW_VERSION}

    for mpath in sorted(glob.glob(os.path.join(MANIFEST_DIR, "__manifest_zyngaswc_prod.*.json"))):
        with open(mpath, "r", encoding="utf-8") as f:
            manifest = json.load(f)
        for key, meta in updates.items():
            manifest.setdefault("paths", {})[key] = dict(meta)
        with open(mpath, "w", encoding="utf-8") as f:
            json.dump(manifest, f, separators=(",", ":"))
        print(f"Updated manifest {os.path.basename(mpath)} -> version v={NEW_VERSION} for olc/cae/epi")


if __name__ == "__main__":
    main()
