#!/usr/bin/env python3
"""Generate authentic, story-accurate base layouts for all Star Wars Commander PvE missions.

For each story mission and planetary outpost where there is a battle, this script:
1. Reads story dialogue, mission goals, campaign title, and planet from game patches.
2. Identifies required encounter triggers (DefenseEncountersProfiles), battle scripts (BattleScripts),
   and victory conditions (Conditions).
3. Selects the correct faction (Tusken, Smuggler, Rebel, Empire, or Wildlife/Ruins).
4. Sets the proper HQ level and difficulty tier matching the story progression.
5. Builds a structured, non-overlapping base layout with walls, defenses, and required targets.
6. Saves the battle file to swcFiles/1541022029/battles/<assetName>.json and creates aliases.
"""

import json
import math
import os
import random
import re
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE_JSON = os.path.join(ROOT, "swcFiles", "1584443067", "patches", "base.json")
CAE_JSON = os.path.join(ROOT, "swcFiles", "1584443067", "patches", "cae.json")
BATTLES_DIR = os.path.join(ROOT, "swcFiles", "1541022029", "battles")
BATTLES_ROOT_DIR = os.path.join(ROOT, "swcFiles", "battles")

GRID_MIN, GRID_MAX = -22, 22
BUILD_LIMIT = 20

# Original files that survived and must never be overwritten
PRESERVED_FILES = {"Fue_rebShow_00.json", "Fue_impShow_00.json", "mis_utts_2.json"}


class Grid:
    def __init__(self):
        self.taken = set()

    def fits(self, x, z, w, h, margin=1):
        if x < GRID_MIN or z < GRID_MIN or x + w > GRID_MAX or z + h > GRID_MAX:
            return False
        for cx in range(x - margin, x + w + margin):
            for cz in range(z - margin, z + h + margin):
                if (cx, cz) in self.taken:
                    return False
        return True

    def occupy(self, x, z, w, h):
        for cx in range(x, x + w):
            for cz in range(z, z + h):
                self.taken.add((cx, cz))

    def place(self, w, h, prefer_radius=0, margin=0, limit=BUILD_LIMIT, rng=None, pool=1):
        candidates = []
        for x in range(-limit, limit - w + 1):
            for z in range(-limit, limit - h + 1):
                centre = max(abs(x + w / 2), abs(z + h / 2))
                candidates.append((abs(centre - prefer_radius), centre, x, z))
        candidates.sort()

        picks = []
        for _, _, x, z in candidates:
            if self.fits(x, z, w, h, margin):
                picks.append((x, z))
                if len(picks) >= pool:
                    break
        if not picks:
            return None
        x, z = rng.choice(picks) if rng and len(picks) > 1 else picks[0]
        self.occupy(x, z, w, h)
        return x, z

    def place_near(self, w, h, target_x, target_z, margin=0, limit=BUILD_LIMIT):
        candidates = []
        for x in range(-limit, limit - w + 1):
            for z in range(-limit, limit - h + 1):
                dx, dz = x + w / 2 - target_x, z + h / 2 - target_z
                candidates.append((dx * dx + dz * dz, x, z))
        candidates.sort()
        for _, x, z in candidates:
            if self.fits(x, z, w, h, margin):
                self.occupy(x, z, w, h)
                return x, z
        return None


def square_ring(radius):
    lo, hi = -radius, radius
    if lo < GRID_MIN or hi >= GRID_MAX:
        return []
    ring = [(x, lo) for x in range(lo, hi + 1)]
    ring += [(hi, z) for z in range(lo + 1, hi + 1)]
    ring += [(x, hi) for x in range(hi - 1, lo - 1, -1)]
    ring += [(lo, z) for z in range(hi - 1, lo, -1)]
    return ring


def load_game_data():
    with open(BASE_JSON, encoding="utf-8") as f:
        base_data = json.load(f)
    with open(CAE_JSON, encoding="utf-8") as f:
        cae_data = json.load(f)

    bld_objs = base_data["content"]["objects"]["BuildingData"]
    bld_map = {b["uid"]: b for b in bld_objs}

    missions = cae_data["content"]["objects"]["CampaignMissionData"]
    bd_map = {b["uid"]: b for b in cae_data["content"]["objects"]["BattleData"]}
    dep_map = {d["uid"]: d for d in cae_data["content"]["objects"]["DefenseEncountersProfiles"]}
    bscr_map = {s["uid"]: s for s in cae_data["content"]["objects"]["BattleScripts"]}
    cond_map = {c["uid"]: c for c in cae_data["content"]["objects"]["Conditions"]}

    return bld_map, missions, bd_map, dep_map, bscr_map, cond_map


def analyze_battle_assets(missions, bd_map, dep_map, bscr_map, cond_map):
    needed = {}
    for m in missions:
        map_uid = m.get("map")
        bd = bd_map.get(map_uid)
        asset = bd.get("assetName") if bd else None
        if not asset or asset == "emptymap":
            continue

        ep = bd.get("encounterProfile")
        ep_data = dep_map.get(ep, {}).get("groups") if ep else None

        bscr = bd.get("BattleScript")
        bscr_data = bscr_map.get(bscr, {}).get("scripts") if bscr else None

        vc_reqs = set()
        for vc in m.get("victoryConditions", []):
            c = cond_map.get(vc)
            if c:
                pt = c.get("prepare_string", "")
                if c.get("condition_type") in ("DestroyBuildingId", "DestroyBuildingType"):
                    vc_reqs.add(pt)

        needed.setdefault(asset, {
            "asset": asset,
            "planet": bd.get("planet") or "planet1",
            "tier": m.get("bi_enemy_tier"),
            "defender_name": bd.get("defenderName"),
            "camps": set(),
            "mission_uids": [],
            "enc_buildings": set(),
            "script_buildings": set(),
            "vc_reqs": set(),
            "map_uids": set()
        })

        info = needed[asset]
        info["camps"].add(m.get("campaignUid"))
        info["mission_uids"].append(m["uid"])
        info["map_uids"].add(map_uid)
        info["vc_reqs"].update(vc_reqs)

        if ep_data:
            for part in ep_data.split("|"):
                tokens = part.split(",")
                if tokens:
                    info["enc_buildings"].add(tokens[0].strip())

        if bscr_data:
            for part in bscr_data.split("|"):
                tokens = part.split(",")
                if tokens:
                    info["script_buildings"].add(tokens[0].strip())

    return needed


def determine_category_and_hq(asset, info):
    camps = list(info["camps"])
    c0 = camps[0] if camps else ""
    def_name = info["defender_name"]
    tier_str = info.get("tier")
    tier = int(tier_str) if tier_str and tier_str.isdigit() else 3

    # Check explicit HQ from encounter / scripts
    explicit_hq = None
    for b in list(info["enc_buildings"]) + list(info["script_buildings"]):
        m = re.match(r"^(empire|rebel|smuggler|tusken)HQ(\d+)$", b)
        if m:
            explicit_hq = (m.group(1), int(m.group(2)))
            break

    # Categorize
    if asset.startswith("sw_"):
        cat = "outpost"
    elif "wri" in c0 or "womp" in asset or asset in ("mis_utts_2", "mis_utts_2a", "mis_hb_3a", "mis_hb_3c", "mis_bgh_2", "mis_bgh_13"):
        cat = "womprat"
    elif asset == "mis_bgh_11":
        cat = "krayt"
    elif asset in ("mis_bgh_7", "mis_bgh_9"):
        cat = "rancor"
    elif asset in ("mis_wos_1", "mis_wos_2"):
        cat = "shipwreck"
    elif "tsw" in c0 or "tjg" in c0 or any("tusken" in b.lower() for b in info["enc_buildings"]):
        cat = "tusken"
    elif any("smuggler" in b.lower() for b in info["enc_buildings"]) or def_name == "icoNeutral":
        cat = "smuggler"
    elif def_name == "icoRebel" or c0.endswith("_e"):
        cat = "rebel"
    else:
        cat = "empire"

    # Determine HQ level
    if explicit_hq:
        faction, hq_lvl = explicit_hq
    elif cat == "outpost":
        # sw_<planet>_t<tier>_<faction>
        m = re.search(r"_t(\d+)_([esr])", asset)
        t_num = int(m.group(1)) if m else 3
        fac_char = m.group(2) if m else "e"
        if fac_char == "s":
            faction = "smuggler"
            hq_lvl = 3 if t_num == 1 else 4
        elif fac_char == "e":
            faction = "rebel"
            hq_lvl = min(10, max(4, t_num * 2))
        else:
            faction = "empire"
            hq_lvl = min(10, max(4, t_num * 2))
    elif cat in ("womprat", "krayt", "shipwreck"):
        faction = "neutral"
        hq_lvl = tier
    elif cat == "rancor":
        faction = "smuggler"
        hq_lvl = 5
    elif cat == "tusken":
        faction = "tusken"
        hq_lvl = min(4, max(1, tier))
    elif cat == "smuggler":
        faction = "smuggler"
        hq_lvl = min(6, max(2, tier))
    elif cat == "rebel":
        faction = "rebel"
        hq_lvl = min(9, max(3, tier + 1))
        # Chapter 11 boss scaling
        if "tbft_10" in asset:
            hq_lvl = 9
        elif "tbft_8" in asset or "tbft_9" in asset:
            hq_lvl = 8
    else:
        faction = "empire"
        hq_lvl = min(9, max(3, tier + 1))
        if "tbft_10" in asset:
            hq_lvl = 9
        elif "tbft_8" in asset or "tbft_9" in asset:
            hq_lvl = 8

    return cat, faction, hq_lvl


def build_layout(asset, info, cat, faction, hq_lvl, bld_map):
    rng = random.Random(hash(asset) & 0xFFFFFFFF)
    grid = Grid()
    buildings = []
    bld_key = 1

    def place_building(uid, prefer_radius=0, margin=1, limit=BUILD_LIMIT, fallback=None):
        nonlocal bld_key
        rec = bld_map.get(uid)
        if not rec:
            return False
        w, h = int(rec["sizex"]), int(rec["sizey"])
        spot = grid.place(w, h, prefer_radius=prefer_radius, margin=margin, limit=limit, rng=rng, pool=3)
        if spot is None and fallback is not None:
            spot = grid.place(w, h, prefer_radius=fallback, margin=0, limit=BUILD_LIMIT, rng=rng, pool=3)
        if spot is not None:
            buildings.append({"key": f"bld_{bld_key}", "x": spot[0], "z": spot[1], "uid": uid})
            bld_key += 1
            return True
        return False

    def place_near(uid, tx, tz, margin=0):
        nonlocal bld_key
        rec = bld_map.get(uid)
        if not rec:
            return False
        w, h = int(rec["sizex"]), int(rec["sizey"])
        spot = grid.place_near(w, h, tx, tz, margin=margin)
        if spot is not None:
            buildings.append({"key": f"bld_{bld_key}", "x": spot[0], "z": spot[1], "uid": uid})
            bld_key += 1
            return True
        return False

    # 1. Place specific encounter and script buildings first
    mandatory = list(info["enc_buildings"]) + list(info["script_buildings"])
    for b in mandatory:
        if "HQ" in b:
            place_building(b, prefer_radius=0, margin=1, limit=6)
        elif "story" in b or "Bones" in b or "Ship" in b:
            place_building(b, prefer_radius=1, margin=1, limit=8)
        else:
            place_building(b, prefer_radius=3, margin=1, limit=12, fallback=15)

    # 2. Place specific victory condition targets
    for vc in info["vc_reqs"]:
        if "1|rebelDefenseGenerator" in vc:
            place_building(f"rebelDefenseGenerator{min(5, max(1, hq_lvl - 4))}", prefer_radius=4, margin=1)
        elif "1|empireDefenseGenerator" in vc:
            place_building(f"empireDefenseGenerator{min(5, max(1, hq_lvl - 4))}", prefer_radius=4, margin=1)
        elif "1|smugglerDefenseGenerator" in vc:
            place_building(f"smugglerDefenseGenerator{min(5, max(1, hq_lvl - 2))}", prefer_radius=4, margin=1)
        elif "1|storyEmpShieldGen" in vc:
            place_building("storyEmpShieldGen1", prefer_radius=3, margin=1)
        elif "1|storyRebShieldGen" in vc:
            place_building("storyRebShieldGen1", prefer_radius=3, margin=1)
        elif "1|rebelTacticalCommand" in vc:
            place_building(f"rebelTacticalCommand{min(5, max(1, hq_lvl - 3))}", prefer_radius=4, margin=1)
        elif "1|empireTacticalCommand" in vc:
            place_building(f"empireTacticalCommand{min(5, max(1, hq_lvl - 3))}", prefer_radius=4, margin=1)
        elif "1|squad" in vc:
            squad_uid = f"{faction}SquadBuilding{hq_lvl}" if f"{faction}SquadBuilding{hq_lvl}" in bld_map else f"{faction}SquadBuilding1"
            place_building(squad_uid, prefer_radius=3, margin=1)

    # 3. Category-specific themed generation
    if cat == "womprat":
        # Womp Rat nest cluster
        lvl = max(1, min(6, hq_lvl))
        for _ in range(rng.randint(2, 4)):
            place_building(f"wompRatNestLarge{lvl}", prefer_radius=2, margin=1, limit=10)
        for _ in range(rng.randint(4, 7)):
            place_building(f"wompRatNestSmall{lvl}", prefer_radius=5, margin=1, limit=14)

        # Scenery: rocks and junk
        for r_uid in ["blockerRockSmall1", "blockerRockMedium1", "junkLarge1", "junkMedium1", "rebelRubble1"]:
            if r_uid in bld_map:
                place_building(r_uid, prefer_radius=7, margin=0, limit=16)

        # If it's a research lab mission (mis_utts_2a, mis_hb_3a, mis_hb_3c)
        if "2a" in asset or "hb_3" in asset:
            lab_uid = "rebelOffenseLab1" if "rebelOffenseLab1" in bld_map else "empireOffenseLab1"
            place_building(lab_uid, prefer_radius=0, margin=1, limit=5)
            place_building("smugglerCreditStorage1", prefer_radius=3, margin=1, limit=8)
            place_building("smugglerMaterialsStorage1", prefer_radius=3, margin=1, limit=8)
            place_building("smugglerBurstTurret1", prefer_radius=6, margin=1, limit=10)
            place_building("smugglerBurstTurret1", prefer_radius=6, margin=1, limit=10)

        # Surrounding makeshift barricades
        wall_uid = "tuskenWall1" if "tuskenWall1" in bld_map else "smugglerWall1"
        for _ in range(25):
            angle = rng.random() * 2 * math.pi
            rad = rng.uniform(8, 14)
            place_near(wall_uid, rad * math.cos(angle), rad * math.sin(angle), margin=0)

    elif cat == "krayt":
        # Krayt dragon graveyard
        if "storyKraytBones1" in bld_map and not any(b["uid"] == "storyKraytBones1" for b in buildings):
            place_building("storyKraytBones1", prefer_radius=0, margin=1, limit=5)
        for r_uid in ["blockerCrystalRockLarge1", "blockerCrystalRockMedium1", "blockerRockLarge1", "junkLarge1"]:
            if r_uid in bld_map:
                place_building(r_uid, prefer_radius=rng.uniform(4, 10), margin=1, limit=15)
        for _ in range(3):
            place_building("rebelRubble1", prefer_radius=6, margin=0, limit=12)
        for _ in range(20):
            angle = rng.random() * 2 * math.pi
            rad = rng.uniform(9, 15)
            place_near("tuskenWall2", rad * math.cos(angle), rad * math.sin(angle), margin=0)

    elif cat == "shipwreck":
        # Shipwreck Atom Edge
        bow_stern = "blockerShipBow1" if "1" in asset else "blockerShipStern1"
        if bow_stern in bld_map and not any(b["uid"] == bow_stern for b in buildings):
            place_building(bow_stern, prefer_radius=0, margin=1, limit=6)

        # Scenery & debris
        for s_uid in ["junkLarge1", "junkMedium1", "rockLarge1", "rebelRubble1", "rebelRubble2"]:
            if s_uid in bld_map:
                place_building(s_uid, prefer_radius=rng.uniform(4, 8), margin=0, limit=14)

        if "2" in asset:
            # mis_wos_2 requires 4 turrets
            for _ in range(4):
                place_building("tuskenAutoTurret2", prefer_radius=7, margin=1, limit=12)
            place_building("tatooineHouseLarge3", prefer_radius=4, margin=1, limit=10)
        else:
            place_building("empireBarracks2", prefer_radius=5, margin=1, limit=10)
            place_building("empireCreditStorage2", prefer_radius=5, margin=1, limit=10)
            place_building("empireRapidFireTurret2", prefer_radius=8, margin=1, limit=12)

        for _ in range(25):
            angle = rng.random() * 2 * math.pi
            rad = rng.uniform(9, 15)
            place_near("tuskenWall2", rad * math.cos(angle), rad * math.sin(angle), margin=0)

    elif cat == "tusken":
        # Tusken Raider settlement
        hq_uid = f"tuskenHQ{hq_lvl}"
        if not any("HQ" in b["uid"] for b in buildings):
            place_building(hq_uid, prefer_radius=0, margin=1, limit=6)

        # Huts/Housing
        for _ in range(rng.randint(2, 4)):
            place_building(f"tuskenHouseLarge{hq_lvl}", prefer_radius=3, margin=1, limit=9)
        for _ in range(rng.randint(3, 6)):
            place_building(f"tuskenHouseSmall{hq_lvl}", prefer_radius=5, margin=1, limit=12)

        # Defenses
        for _ in range(rng.randint(2, 4)):
            place_building(f"tuskenAutoTurret{hq_lvl}", prefer_radius=7, margin=1, limit=14)
        for _ in range(rng.randint(1, 3)):
            place_building(f"tuskenBurstTurret{hq_lvl}", prefer_radius=8, margin=1, limit=15)

        # Extras
        if "tuskenDroidHut1" in bld_map:
            place_building("tuskenDroidHut1", prefer_radius=4, margin=1, limit=8)
        if "tuskenFlag1" in bld_map:
            place_building("tuskenFlag1", prefer_radius=5, margin=1, limit=10)

        # Organic barricades
        wall_uid = f"tuskenWall{min(4, hq_lvl)}"
        for _ in range(35):
            angle = rng.random() * 2 * math.pi
            rad = rng.uniform(9, 16)
            place_near(wall_uid, rad * math.cos(angle), rad * math.sin(angle), margin=0)

    elif cat == "smuggler" or cat == "rancor":
        # Smuggler / Mercenary base
        hq_uid = f"smugglerHQ{hq_lvl}"
        if not any("HQ" in b["uid"] for b in buildings):
            place_building(hq_uid, prefer_radius=0, margin=1, limit=6)

        # Core buildings
        place_building(f"smugglerBarracks{hq_lvl}", prefer_radius=3, margin=1, limit=9)
        place_building(f"smugglerStarport{hq_lvl}", prefer_radius=4, margin=1, limit=11)
        place_building(f"smugglerCreditStorage{hq_lvl}", prefer_radius=3, margin=1, limit=8)
        place_building(f"smugglerMaterialsStorage{hq_lvl}", prefer_radius=3, margin=1, limit=8)
        place_building(f"smugglerCreditGenerator{hq_lvl}", prefer_radius=5, margin=1, limit=12)
        place_building(f"smugglerMaterialsGenerator{hq_lvl}", prefer_radius=5, margin=1, limit=12)

        # Special mission buildings
        if "mis_utts_6" in asset and "rebelOffenseLab1" in bld_map:
            place_building("rebelOffenseLab1", prefer_radius=4, margin=1, limit=8)
        if "mis_utts_9" in asset:
            mortar_uid = f"smugglerMortar{min(2, hq_lvl)}"
            place_building(mortar_uid, prefer_radius=2, margin=1, limit=6)

        # Turrets
        turret_pool = [
            f"smugglerRapidFireTurret{hq_lvl}",
            f"smugglerBurstTurret{hq_lvl}",
            f"smugglerMortar{min(3, hq_lvl)}",
            f"smugglerRocketTurret{min(3, hq_lvl)}"
        ]
        valid_turrets = [t for t in turret_pool if t in bld_map]
        for i in range(rng.randint(4, 7)):
            t_uid = valid_turrets[i % len(valid_turrets)]
            angle = 2 * math.pi * i / 6 + rng.uniform(-0.2, 0.2)
            place_near(t_uid, 10 * math.cos(angle), 10 * math.sin(angle), margin=1)

        # Wall perimeter
        wall_uid = f"smugglerWall{min(4, hq_lvl)}"
        wall_budget = 40 + hq_lvl * 5
        ring_rad = min(15, 8 + hq_lvl)
        perimeter = square_ring(ring_rad)
        for tile in perimeter:
            if grid.fits(tile[0], tile[1], 1, 1, margin=0):
                grid.occupy(tile[0], tile[1], 1, 1)
                buildings.append({"key": f"bld_{bld_key}", "x": tile[0], "z": tile[1], "uid": wall_uid})
                bld_key += 1

    else:
        # Military base (Rebel or Empire or Outpost)
        hq_uid = f"{faction}HQ{hq_lvl}"
        if not any("HQ" in b["uid"] for b in buildings):
            place_building(hq_uid, prefer_radius=0, margin=1, limit=6)

        # Primary military buildings
        for b_name in ["Barracks", "Starport", "Factory"]:
            uid = f"{faction}{b_name}{hq_lvl}"
            if uid in bld_map:
                place_building(uid, prefer_radius=4, margin=1, limit=10)

        # Storages & Generators
        for b_name in ["CreditStorage", "MaterialsStorage", "CreditGenerator", "MaterialsGenerator"]:
            uid = f"{faction}{b_name}{hq_lvl}"
            if uid in bld_map:
                place_building(uid, prefer_radius=rng.uniform(3, 6), margin=1, limit=10)

        # Support facilities
        for b_name in ["SquadBuilding", "OffenseLab", "ShieldGenerator", "TacticalCommand"]:
            uid = f"{faction}{b_name}{min(hq_lvl, 5)}"
            if uid in bld_map and not any(b["uid"] == uid for b in buildings):
                place_building(uid, prefer_radius=5, margin=1, limit=11)

        # Turrets belt
        turret_types = ["RapidFireTurret", "BurstTurret", "Mortar", "RocketTurret", "SonicTurret"]
        turret_pool = [f"{faction}{t}{hq_lvl}" for t in turret_types if f"{faction}{t}{hq_lvl}" in bld_map]
        if not turret_pool:
            turret_pool = [f"{faction}RapidFireTurret1"]

        num_turrets = min(12, max(4, hq_lvl + 2))
        belt_radius = min(16, 9 + hq_lvl // 2)
        for i in range(num_turrets):
            t_uid = turret_pool[i % len(turret_pool)]
            angle = 2 * math.pi * i / num_turrets + rng.uniform(-0.15, 0.15)
            place_near(t_uid, belt_radius * math.cos(angle), belt_radius * math.sin(angle), margin=1)

        # Defensive Wall Ring
        wall_uid = f"{faction}Wall{min(8, hq_lvl)}"
        if f"erkitWall{min(8, hq_lvl)}" in bld_map and info["planet"] == "planet3":
            wall_uid = f"erkitWall{min(8, hq_lvl)}"

        ring_rad = min(17, 8 + hq_lvl)
        perimeter = square_ring(ring_rad)
        for tile in perimeter:
            if grid.fits(tile[0], tile[1], 1, 1, margin=0):
                grid.occupy(tile[0], tile[1], 1, 1)
                buildings.append({"key": f"bld_{bld_key}", "x": tile[0], "z": tile[1], "uid": wall_uid})
                bld_key += 1

    return {"map": {"next": 0, "planet": info["planet"], "buildings": buildings}}


def main():
    print("=== Star Wars Commander Mission Base Generator ===")
    bld_map, missions, bd_map, dep_map, bscr_map, cond_map = load_game_data()
    needed = analyze_battle_assets(missions, bd_map, dep_map, bscr_map, cond_map)

    os.makedirs(BATTLES_DIR, exist_ok=True)
    os.makedirs(BATTLES_ROOT_DIR, exist_ok=True)

    generated_count = 0
    preserved_count = 0

    manifest_map = {}

    for asset, info in sorted(needed.items()):
        filename = f"{asset}.json"
        target_path = os.path.join(BATTLES_DIR, filename)
        root_path = os.path.join(BATTLES_ROOT_DIR, filename)

        if filename in PRESERVED_FILES and os.path.exists(target_path):
            print(f"[PRESERVE] {filename} (original authentic asset)")
            preserved_count += 1
            continue

        cat, faction, hq_lvl = determine_category_and_hq(asset, info)
        base_data = build_layout(asset, info, cat, faction, hq_lvl, bld_map)

        # Validate layout
        blds = base_data["map"]["buildings"]
        assert len(blds) >= 10, f"Too few buildings for {asset}: {len(blds)}"
        for b in blds:
            assert b["uid"] in bld_map, f"Invalid building {b['uid']} in {asset}"
            x, z = b["x"], b["z"]
            assert GRID_MIN <= x <= GRID_MAX and GRID_MIN <= z <= GRID_MAX, f"Out of bounds in {asset}: {b}"

        # Write to primary battles dir
        with open(target_path, "w", encoding="utf-8") as f:
            json.dump(base_data, f)

        # Write to swcFiles/battles root dir as well
        with open(root_path, "w", encoding="utf-8") as f:
            json.dump(base_data, f)

        # Also support tokenized old naming scheme mis_<short>_<num>_<fac>.json
        for m_uid in info["mission_uids"]:
            manifest_map[m_uid] = filename

        generated_count += 1
        print(f"[GENERATE] {filename}: {len(blds)} blds, cat={cat}, fac={faction}, hq={hq_lvl}, planet={info['planet']}")

    # Generate aliases (by map uid, mission uid, and tokenized pattern) so any request style resolves
    print("\nCreating aliases for battle requests...")
    alias_count = 0
    for m in missions:
        map_uid = m.get("map")
        bd = bd_map.get(map_uid)
        asset = bd.get("assetName") if bd else None
        if not asset or asset == "emptymap":
            continue

        src_file = os.path.join(BATTLES_DIR, f"{asset}.json")
        if not os.path.exists(src_file):
            continue

        # Aliases to point to src_file
        target_names = [f"{map_uid}.json", f"{m['uid']}.json"]

        tokens = map_uid.split("_")
        alpha = [t for t in tokens if t.isalpha() and t not in ("bat", "e", "r", "s")]
        fac = [t for t in tokens if t in ("e", "r", "s")]
        nums = [t for t in tokens if t.isdigit()]
        if alpha and fac and nums:
            target_names.append(f"mis_{alpha[0]}_{nums[-1]}_{fac[0]}.json")
            target_names.append(f"mis_{alpha[0]}_{nums[-1]}{fac[0]}.json")

        for alias_name in target_names:
            if alias_name == f"{asset}.json" or alias_name in PRESERVED_FILES:
                continue
            for folder in [BATTLES_DIR, BATTLES_ROOT_DIR]:
                alias_path = os.path.join(folder, alias_name)
                if not os.path.exists(alias_path):
                    try:
                        os.link(src_file, alias_path)
                    except OSError:
                        import shutil
                        shutil.copyfile(src_file, alias_path)
                    alias_count += 1

    print(f"Aliases created: {alias_count}")
    print(f"\nDone: {generated_count} generated, {preserved_count} preserved, total {len(needed)} battle assets.")


if __name__ == "__main__":
    main()
