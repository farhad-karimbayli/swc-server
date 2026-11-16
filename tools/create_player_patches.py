#!/usr/bin/env python3
"""
Generate custom patches for player dad436c8-761c-4579-8a76-0f910850510d:
- Empire Turrets & Walls: kept at authentic pristine stats
- Empire Shield Generators (L1-L11): Generator Health x15, L11 Shield Capacity (shield_health_per_point[10]) x15 (1,500,000 HP)
- Empire Squad Building: activationRadius = 60 (entire base coverage)

Generates:
  swcFiles/1610000000/patches/base.json.android.assetbundle
  swcFiles/1610000000/patches/trp.json.android.assetbundle
  swcFiles/1610000000/patches/base.json
  swcFiles/1610000000/patches/trp.json
  swcFiles/manifests/__manifest_zyngaswc_prod.dad436c8-761c-4579-8a76-0f910850510d.json
"""

import os
import sys
import json
import zlib
import struct
import copy
import UnityPy

sys.path.append(os.path.join(os.path.dirname(__file__), "joe"))
import joe

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SWC_FILES = os.path.join(ROOT, "swcFiles")
PLAYER_ID = "dad436c8-761c-4579-8a76-0f910850510d"
NEW_VERSION = 1610000000

# Value 11 in shield_health_per_point is 15x normal (1,500,000 HP).
# User's base has 8x empireShieldGenerator11, so all 8 shield fields get 1,500,000 HP.
# Values 1..10 remain 100% authentic so enemy story bases (e.g. Chapter 7 Mission 8 with L5 generator) have normal 40,000 HP shields.
CUSTOM_SHIELD_HEALTH_PER_POINT = (
    "5000 10000 20000 30000 40000 50000 63300 73600 83900 94500 1500000 "
    "110000 120000 130000 140000 150000 160000 170000 180000 190000 200000"
)

SRC_BASE_BUNDLE = os.path.join(SWC_FILES, "1584443067", "patches", "base.json.android.assetbundle")
SRC_TRP_BUNDLE = os.path.join(SWC_FILES, "1601000000", "patches", "trp.json.android.assetbundle")
SRC_BASE_JSON = os.path.join(SWC_FILES, "1584443067", "patches", "base.json")
SRC_TRP_JSON = os.path.join(SWC_FILES, "1584443067", "patches", "trp.json")

DEST_DIR = os.path.join(SWC_FILES, str(NEW_VERSION), "patches")
MANIFEST_SRC = os.path.join(SWC_FILES, "manifests", "__manifest_zyngaswc_prod.02045.json")
MANIFEST_DEST = os.path.join(SWC_FILES, "manifests", f"__manifest_zyngaswc_prod.{PLAYER_ID}.json")


def extract_joe_from_bundle(bundle_path):
    env = UnityPy.load(bundle_path)
    for obj in env.objects:
        if obj.type.name == "TextAsset":
            raw = obj.get_raw_data()
            name_len = struct.unpack_from("<I", raw, 0)[0]
            offset = 4 + name_len
            if offset % 4 != 0:
                offset += 4 - (offset % 4)
            script_len = struct.unpack_from("<I", raw, offset)[0]
            offset += 4
            script_bytes = raw[offset : offset + script_len]
            parsed = joe.parse(script_bytes)
            parsed["tail"] = script_bytes[parsed["end"] :]
            return parsed
    raise ValueError(f"No TextAsset in {bundle_path}")


def save_bundle(source_bundle_path, target_bundle_path, target_asset_name, joe_bytes):
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


def is_empire_turret(uid):
    uid_l = uid.lower()
    return (
        uid_l.startswith("empireburstturret")
        or uid_l.startswith("empiremortar")
        or uid_l.startswith("empirerapidfireturret")
        or uid_l.startswith("empirerocketturret")
        or uid_l.startswith("empiresonicturret")
        or uid_l.startswith("empire_ab_")
    )


def is_empire_turret_weapon(uid):
    uid_l = uid.lower()
    return (
        uid_l.startswith("t_empireburstturret")
        or uid_l.startswith("t_empiremortar")
        or uid_l.startswith("t_empirerapidfireturret")
        or uid_l.startswith("t_empirerocketturret")
        or uid_l.startswith("t_empiresonicturret")
        or uid_l.startswith("t_empire_ab_")
    )


def modify_base_joe(base_joe):
    # 1. BuildingData
    sh = [s for s in base_joe["sheets"] if base_joe["S"][s["name"]].decode() == "BuildingData"][0]
    cols = [base_joe["S"][c[1]].decode() for c in sh["cols"]]
    ncols = len(cols)
    idx_uid = cols.index("uid")
    idx_health = cols.index("health")
    idx_act = cols.index("activationRadius")
    idx_shp = cols.index("shieldHealthPoints")

    shield_count = 0
    squad_count = 0

    for r in range(len(sh["cells"]) // ncols):
        uid_cell = sh["cells"][r * ncols + idx_uid]
        uid = base_joe["S"][uid_cell - 1].decode() if uid_cell > 0 else ""
        if not uid:
            continue

        uid_l = uid.lower()
        # Shield Generator: Building Health x15
        if uid_l.startswith("empireshieldgenerator"):
            curr_h = sh["cells"][r * ncols + idx_health] - 1
            if curr_h > 0:
                new_h = curr_h * 15
                sh["cells"][r * ncols + idx_health] = new_h + 1
            shield_count += 1

        # Squad Building: coverage entire base (activationRadius = 60)
        elif uid_l.startswith("empiresquadbuilding"):
            # Set activationRadius to 60 (cell val = 60 + 1 = 61)
            sh["cells"][r * ncols + idx_act] = 61
            squad_count += 1

    print(f"Modified BuildingData: {shield_count} empire shield generators (Building HP x15, standard shieldHealthPoints 1..11), "
          f"{squad_count} squad buildings (radius=60); rebel buildings and walls/turrets kept pristine")

    # 2. GameConstants: update shield_health_per_point
    sh_gc = [s for s in base_joe["sheets"] if base_joe["S"][s["name"]].decode() == "GameConstants"][0]
    cols_gc = [base_joe["S"][c[1]].decode() for c in sh_gc["cols"]]
    idx_gc_uid = cols_gc.index("uid")
    idx_gc_val = cols_gc.index("value")
    ncols_gc = len(cols_gc)

    base_joe["S"].append(CUSTOM_SHIELD_HEALTH_PER_POINT.encode())
    new_str_idx = len(base_joe["S"])

    for r in range(len(sh_gc["cells"]) // ncols_gc):
        u_c = sh_gc["cells"][r * ncols_gc + idx_gc_uid]
        u = base_joe["S"][u_c - 1].decode() if u_c > 0 else ""
        if u == "shield_health_per_point":
            sh_gc["cells"][r * ncols_gc + idx_gc_val] = new_str_idx
            print("GameConstants: updated shield_health_per_point (L11 shield field = 1,500,000 HP, Rebel L1..11 authentic)")
            break


def modify_trp_joe(trp_joe):
    # Turret damage and DPS returned to normal values as requested
    print("TurretData: keeping normal authentic damage and DPS values")


def modify_base_json(base_json_path, dest_path):
    with open(base_json_path, "r") as f:
        data = json.load(f)

    # BuildingData: empire shield generators and squad building
    for b in data.get("content", {}).get("objects", {}).get("BuildingData", []):
        uid = b.get("uid", "")
        uid_l = uid.lower()
        if uid_l.startswith("empireshieldgenerator"):
            if "health" in b and b["health"]:
                b["health"] = str(int(b["health"]) * 15)
        elif uid_l.startswith("empiresquadbuilding"):
            b["activationRadius"] = "60"

    # GameConstants: shield_health_per_point
    for c in data.get("content", {}).get("objects", {}).get("GameConstants", []):
        if c.get("uid") == "shield_health_per_point":
            c["value"] = CUSTOM_SHIELD_HEALTH_PER_POINT
            break

    os.makedirs(os.path.dirname(dest_path), exist_ok=True)
    with open(dest_path, "w") as f:
        json.dump(data, f, separators=(',', ':'))
    print(f"Saved modified base.json to {dest_path}")


def modify_trp_json(trp_json_path, dest_path):
    with open(trp_json_path, "r") as f:
        data = json.load(f)

    # Turret damage and DPS remain normal authentic values

    os.makedirs(os.path.dirname(dest_path), exist_ok=True)
    with open(dest_path, "w") as f:
        json.dump(data, f, separators=(',', ':'))
    print(f"Saved trp.json (normal damage) to {dest_path}")


def calc_crc32(file_path):
    with open(file_path, "rb") as f:
        buf = f.read()
    crc = zlib.crc32(buf) & 0xFFFFFFFF
    return f"{crc:08X}"


def main():
    print(f"=== Generating Custom Patches for Player {PLAYER_ID} ===")
    os.makedirs(DEST_DIR, exist_ok=True)

    # 1. Modify base.json.joe and save bundle
    base_joe = extract_joe_from_bundle(SRC_BASE_BUNDLE)
    modify_base_joe(base_joe)
    base_joe_bytes = joe.write(base_joe, base_joe.get("tail", b"\x00"))

    dest_base_bundle = os.path.join(DEST_DIR, "base.json.android.assetbundle")
    save_bundle(SRC_BASE_BUNDLE, dest_base_bundle, "base.json.joe", base_joe_bytes)
    base_crc = calc_crc32(dest_base_bundle)
    print(f"Generated {dest_base_bundle} (CRC32: {base_crc})")

    # 2. Modify trp.json.joe and save bundle
    trp_joe = extract_joe_from_bundle(SRC_TRP_BUNDLE)
    modify_trp_joe(trp_joe)
    trp_joe_bytes = joe.write(trp_joe, trp_joe.get("tail", b"\x00"))

    dest_trp_bundle = os.path.join(DEST_DIR, "trp.json.android.assetbundle")
    save_bundle(SRC_TRP_BUNDLE, dest_trp_bundle, "trp.json.joe", trp_joe_bytes)
    trp_crc = calc_crc32(dest_trp_bundle)
    print(f"Generated {dest_trp_bundle} (CRC32: {trp_crc})")

    # 3. Plain JSON files
    dest_base_json = os.path.join(DEST_DIR, "base.json")
    modify_base_json(SRC_BASE_JSON, dest_base_json)

    dest_trp_json = os.path.join(DEST_DIR, "trp.json")
    modify_trp_json(SRC_TRP_JSON, dest_trp_json)

    # 4. Generate custom manifest
    with open(MANIFEST_SRC, "r") as f:
        manifest = json.load(f)

    manifest["paths"]["patches/base.json.android.assetbundle"] = {
        "crc": base_crc,
        "v": NEW_VERSION
    }
    manifest["paths"]["patches/trp.json.android.assetbundle"] = {
        "crc": trp_crc,
        "v": NEW_VERSION
    }
    if "patches/base.json" in manifest["paths"]:
        manifest["paths"]["patches/base.json"]["v"] = NEW_VERSION
        manifest["paths"]["patches/base.json"]["crc"] = calc_crc32(dest_base_json)
    if "patches/trp.json" in manifest["paths"]:
        manifest["paths"]["patches/trp.json"]["v"] = NEW_VERSION
        manifest["paths"]["patches/trp.json"]["crc"] = calc_crc32(dest_trp_json)

    manifest["version"] = "02053"

    with open(MANIFEST_DEST, "w") as f:
        json.dump(manifest, f, separators=(',', ':'))
    print(f"Generated custom player manifest at {MANIFEST_DEST}")

    print("=== Done! ===")


if __name__ == "__main__":
    main()
