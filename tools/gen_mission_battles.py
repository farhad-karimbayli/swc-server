#!/usr/bin/env python3
"""Generate authentic, diverse, story-accurate base layouts for all Star Wars Commander PvE missions.

For each story mission and planetary outpost:
1. Reads story dialogue, mission goals, campaign title, and planet from game patches.
2. Identifies required encounter triggers (DefenseEncountersProfiles), battle scripts (BattleScripts),
   and victory conditions (Conditions).
3. Guarantees 100% of required targets (Shield Generators, HQs, research labs, hero mobilizers,
   specific trigger buildings, storage, and turrets) are placed and never blocked.
4. Generates rich, diverse architectural layouts matching each mission's specific story:
   - Shipwreck Salvage (crashed starship bow/stern, shield generator, blast craters, salvage camp)
   - Jabba's Menagerie (beast pens, holding cells with monsters, central command)
   - Krayt Dragon Graveyard (ancient bones, crystal pillars, stone perimeter)
   - Womp Rat Warrens (organic burrow clusters, rocks, moisture vaporators, makeshift barricades)
   - Tusken Raider Encampments & Corrals (oval bantha corral, tribal huts, totems)
   - Ruined Settlements (Jawa Sandcrawler, desert houses, cantina, debris)
   - 4 Distinct Military Architectures (Citadel Compound, Compartmentalized Sectors, Bastion Wings, Staggered Outpost)
5. Enriches every base with authentic planet-specific environmental scenery.
6. Validates every generated map and writes aliases.
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

    def place_exact(self, w, h, x, z, margin=0):
        if self.fits(x, z, w, h, margin=margin):
            self.occupy(x, z, w, h)
            return x, z
        return None

    def place(self, w, h, prefer_radius=0, margin=0, limit=BUILD_LIMIT, rng=None, pool=1, fallback=True):
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
        if picks:
            x, z = rng.choice(picks) if rng and len(picks) > 1 else picks[0]
            self.occupy(x, z, w, h)
            return x, z

        if fallback and (margin > 0 or limit < BUILD_LIMIT):
            return self.place(w, h, prefer_radius=prefer_radius, margin=0, limit=BUILD_LIMIT, rng=rng, pool=pool, fallback=False)

        return None

    def place_near(self, w, h, target_x, target_z, margin=0, limit=BUILD_LIMIT, fallback=True):
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
        if fallback and (margin > 0 or limit < BUILD_LIMIT):
            return self.place_near(w, h, target_x, target_z, margin=0, limit=BUILD_LIMIT, fallback=False)
        return None


def rect_ring(x1, x2, z1, z2, openings=()):
    tiles = []
    op = set(openings)
    for x in range(x1, x2 + 1):
        if (x, z1) not in op: tiles.append((x, z1))
        if (x, z2) not in op: tiles.append((x, z2))
    for z in range(z1 + 1, z2):
        if (x1, z) not in op: tiles.append((x1, z))
        if (x2, z) not in op: tiles.append((x2, z))
    return tiles


def line_wall(x1, z1, x2, z2, openings=()):
    tiles = []
    op = set(openings)
    if x1 == x2:
        step = 1 if z2 >= z1 else -1
        for z in range(z1, z2 + step, step):
            if (x1, z) not in op: tiles.append((x1, z))
    elif z1 == z2:
        step = 1 if x2 >= x1 else -1
        for x in range(x1, x2 + step, step):
            if (x, z1) not in op: tiles.append((x, z1))
    return tiles


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
                ctype = c.get("condition_type", "")
                if ctype in ("DestroyBuildingId", "DestroyBuildingType", "DestroyBuildingsId"):
                    vc_reqs.add((vc, ctype, pt))

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
            "map_uids": set(),
            "goal_strings": set()
        })

        info = needed[asset]
        info["camps"].add(m.get("campaignUid"))
        info["mission_uids"].append(m["uid"])
        info["map_uids"].add(map_uid)
        info["vc_reqs"].update(vc_reqs)
        if m.get("goalString"):
            info["goal_strings"].add(m.get("goalString"))

        if ep_data:
            for part in ep_data.split("|"):
                tokens = part.split(",")
                if tokens and tokens[0].strip():
                    info["enc_buildings"].add(tokens[0].strip())

        if bscr_data:
            for part in bscr_data.split("|"):
                tokens = part.split(",")
                if tokens and tokens[0].strip():
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

    # Determine thematic category
    if asset.startswith("sw_"):
        cat = "outpost"
    elif "wri" in c0 or "womp" in asset or asset in ("mis_utts_2", "mis_utts_2a", "mis_hb_3a", "mis_hb_3c", "mis_bgh_2", "mis_bgh_13"):
        cat = "womprat"
    elif asset == "mis_bgh_11":
        cat = "krayt"
    elif asset == "mis_bgh_9":
        cat = "rancor"
    elif asset == "mis_bgh_8a":
        cat = "menagerie"
    elif asset == "mis_bgh_7":
        cat = "village"
    elif asset == "mis_bgh_4a":
        cat = "dewback"
    elif asset in ("mis_bgh_6", "mis_wos_1", "mis_wos_2"):
        cat = "shipwreck"
    elif "tsw" in c0 or "tjg" in c0 or asset == "mis_bgh_3" or any("tusken" in b.lower() for b in info["enc_buildings"]):
        cat = "tusken"
    elif any("smuggler" in b.lower() for b in info["enc_buildings"]) or def_name == "icoNeutral":
        cat = "smuggler"
    elif def_name == "icoRebel" or c0.endswith("_e"):
        cat = "rebel"
    else:
        cat = "empire"

    # Determine HQ level & faction
    if explicit_hq:
        faction, hq_lvl = explicit_hq
    elif cat == "outpost":
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
    elif cat in ("womprat", "krayt"):
        faction = "neutral"
        hq_lvl = tier
    elif cat in ("shipwreck", "menagerie", "rancor", "village", "dewback"):
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

    # Determine architectural style for bases
    hash_val = hash(asset) & 0xFFFFFFFF
    styles = ["citadel", "compartment", "bastion_wings", "staggered"]
    arch_style = styles[hash_val % len(styles)]

    return cat, faction, hq_lvl, arch_style


def build_layout(asset, info, cat, faction, hq_lvl, arch_style, bld_map):
    rng = random.Random(hash(asset) & 0xFFFFFFFF)
    grid = Grid()
    buildings = []
    bld_key = 1
    planet = info["planet"]

    def place_building(uid, prefer_radius=0, margin=1, limit=BUILD_LIMIT, fallback=True):
        nonlocal bld_key
        rec = bld_map.get(uid)
        if not rec:
            return False
        w, h = int(rec["sizex"]), int(rec["sizey"])
        spot = grid.place(w, h, prefer_radius=prefer_radius, margin=margin, limit=limit, rng=rng, pool=3, fallback=fallback)
        if spot is not None:
            buildings.append({"key": f"bld_{bld_key}", "x": spot[0], "z": spot[1], "uid": uid})
            bld_key += 1
            return True
        return False

    def place_exact(uid, x, z, margin=0):
        nonlocal bld_key
        rec = bld_map.get(uid)
        if not rec:
            return False
        w, h = int(rec["sizex"]), int(rec["sizey"])
        spot = grid.place_exact(w, h, x, z, margin=margin)
        if spot is not None:
            buildings.append({"key": f"bld_{bld_key}", "x": spot[0], "z": spot[1], "uid": uid})
            bld_key += 1
            return True
        return False

    def place_near(uid, tx, tz, margin=0, limit=BUILD_LIMIT, fallback=True):
        nonlocal bld_key
        rec = bld_map.get(uid)
        if not rec:
            return False
        w, h = int(rec["sizex"]), int(rec["sizey"])
        spot = grid.place_near(w, h, tx, tz, margin=margin, limit=limit, fallback=fallback)
        if spot is not None:
            buildings.append({"key": f"bld_{bld_key}", "x": spot[0], "z": spot[1], "uid": uid})
            bld_key += 1
            return True
        return False

    def place_walls(tiles, wall_uid):
        nonlocal bld_key
        for tile in tiles:
            x, z = tile[0], tile[1]
            if grid.fits(x, z, 1, 1, margin=0):
                grid.occupy(x, z, 1, 1)
                buildings.append({"key": f"bld_{bld_key}", "x": x, "z": z, "uid": wall_uid})
                bld_key += 1

    # =========================================================================
    # STEP 1: GUARANTEE PRIMARY ANCHOR / HQ FIRST
    # =========================================================================
    needs_hq = (cat in ("empire", "rebel", "smuggler", "outpost", "tusken", "menagerie", "rancor") or
                any("HQ" in pt for _, _, pt in info["vc_reqs"]))

    if needs_hq:
        hq_uid = f"{faction}HQ{hq_lvl}"
        if hq_uid not in bld_map:
            # Fallback to available HQ level
            for l in range(hq_lvl, 0, -1):
                if f"{faction}HQ{l}" in bld_map:
                    hq_uid = f"{faction}HQ{l}"
                    break
        rec = bld_map[hq_uid]
        w, h = int(rec["sizex"]), int(rec["sizey"])
        # Place HQ centered
        place_exact(hq_uid, -w // 2, -h // 2, margin=1)

    # =========================================================================
    # STEP 2: MANDATORY ENCOUNTER & SCRIPT TRIGGER BUILDINGS
    # =========================================================================
    # Sort triggers so large buildings / centers get placed first
    mandatory = list(info["script_buildings"]) + list(info["enc_buildings"])
    # Deduplicate preserving order
    dedup_mandatory = []
    seen_m = set()
    for b in mandatory:
        if b not in seen_m:
            seen_m.add(b)
            dedup_mandatory.append(b)

    for b in dedup_mandatory:
        if any(bld["uid"] == b for bld in buildings):
            continue
        if "HQ" in b:
            place_building(b, prefer_radius=0, margin=1, limit=5)
        elif "story" in b or "Bones" in b or "Ship" in b or "Shield" in b or "shield" in b:
            place_building(b, prefer_radius=2, margin=1, limit=9)
        elif "Barracks" in b:
            place_building(b, prefer_radius=4, margin=1, limit=12)
        else:
            place_building(b, prefer_radius=5, margin=1, limit=14)

    # =========================================================================
    # STEP 3: VICTORY CONDITION TARGET BUILDINGS
    # =========================================================================
    for vc, ctype, prep in info["vc_reqs"]:
        parts = prep.split("|")
        # Check condition type
        if ctype == "DestroyBuildingType":
            count_str = parts[0]
            btype = parts[1] if len(parts) > 1 else "any"
            needed_count = int(count_str.replace("%", "")) if not count_str.endswith("%") else 1
            if btype == "shield_generator":
                # Find appropriate shield generator
                current_sg = sum(1 for bld in buildings if bld_map.get(bld["uid"], {}).get("type") == "shield_generator")
                while current_sg < needed_count:
                    if cat in ("smuggler", "shipwreck", "menagerie", "rancor"):
                        sg_uid = "smugglerShieldGeneratorLarge1" if "smugglerShieldGeneratorLarge1" in bld_map else "smugglerShieldGenerator4"
                    elif faction == "rebel":
                        sg_uid = f"rebelShieldGenerator{min(11, max(1, hq_lvl))}"
                    else:
                        sg_uid = f"empireShieldGenerator{min(11, max(1, hq_lvl))}"
                    if not place_building(sg_uid, prefer_radius=3, margin=1, limit=10):
                        break
                    current_sg += 1

            elif btype == "squad":
                current_sq = sum(1 for bld in buildings if bld_map.get(bld["uid"], {}).get("type") == "squad")
                while current_sq < needed_count:
                    sq_uid = f"{faction}SquadBuilding{hq_lvl}" if f"{faction}SquadBuilding{hq_lvl}" in bld_map else f"{faction}SquadBuilding1"
                    if not place_building(sq_uid, prefer_radius=4, margin=1, limit=11):
                        break
                    current_sq += 1

            elif btype == "turret":
                current_tur = sum(1 for bld in buildings if bld_map.get(bld["uid"], {}).get("type") == "turret")
                t_pool = [f"{faction}RapidFireTurret{hq_lvl}", f"{faction}BurstTurret{hq_lvl}"]
                valid_t = [t for t in t_pool if t in bld_map]
                if not valid_t: valid_t = [f"{faction}RapidFireTurret1"]
                while current_tur < needed_count:
                    t_uid = valid_t[current_tur % len(valid_t)]
                    if not place_building(t_uid, prefer_radius=8, margin=1, limit=15):
                        break
                    current_tur += 1

        elif ctype in ("DestroyBuildingId", "DestroyBuildingsId"):
            needed_count = int(parts[0])
            target_id = parts[1]
            current_id_cnt = sum(1 for bld in buildings if bld["uid"].startswith(target_id) or bld["uid"] == target_id)
            while current_id_cnt < needed_count:
                # Find matching building in bld_map
                candidates = [u for u in bld_map if u.startswith(target_id) or u == target_id]
                cand = candidates[0] if candidates else target_id
                # Prefer level near hq_lvl
                for c_u in candidates:
                    if str(hq_lvl) in c_u or "1" in c_u:
                        cand = c_u
                        break
                if not place_building(cand, prefer_radius=3, margin=1, limit=12):
                    break
                current_id_cnt += 1

    # =========================================================================
    # STEP 4: THEMATIC ARCHITECTURE & EXPANSION
    # =========================================================================

    if cat == "shipwreck":
        # Crashed Starship salvage site (mis_bgh_6, mis_wos_1, mis_wos_2)
        if "blockerShipBow1" in bld_map and not any(b["uid"] == "blockerShipBow1" for b in buildings):
            place_exact("blockerShipBow1", -6, -2, margin=0)
        if "blockerShipStern1" in bld_map and not any(b["uid"] == "blockerShipStern1" for b in buildings):
            place_exact("blockerShipStern1", 2, -2, margin=0)

        # Shield generator protecting the crash site (critical for mis_bgh_6!)
        if not any(bld_map.get(b["uid"], {}).get("type") == "shield_generator" for b in buildings):
            sg = "smugglerShieldGenerator4" if "smugglerShieldGenerator4" in bld_map else "smugglerShieldGeneratorLarge1"
            place_building(sg, prefer_radius=2, margin=1, limit=8)

        # Scavenger & Mercenary settlement around the hull
        for b_name in ["smugglerBarracks4", "smugglerStarport5", "smugglerCreditStorage5",
                       "smugglerMaterialsStorage5", "smugglerCreditGenerator5", "smugglerMaterialsGenerator5"]:
            if b_name in bld_map:
                place_building(b_name, prefer_radius=rng.uniform(4, 8), margin=1, limit=12)

        # Defensive turrets
        turrets = ["smugglerRapidFireTurret5", "smugglerBurstTurret5", "smugglerMortar3", "smugglerRocketTurret3"]
        for t_uid in turrets:
            if t_uid in bld_map:
                place_building(t_uid, prefer_radius=rng.uniform(7, 11), margin=1, limit=14)

        # Craters and debris field
        craters = ["blockerBlastCraterTat1", "blockerBlastCraterTat2", "junkLarge1", "junkMedium1", "rebelRubble1"]
        for cr in craters:
            if cr in bld_map:
                place_building(cr, prefer_radius=rng.uniform(3, 9), margin=0, limit=15)

        # Perimeter salvage barricade
        wall_uid = "smugglerWall4" if "smugglerWall4" in bld_map else "smugglerWall1"
        walls = rect_ring(-14, 14, -13, 13, openings=[(-14, 0), (14, 0), (0, -13), (0, 13)])
        place_walls(walls, wall_uid)

    elif cat == "menagerie":
        # Jabba's Menagerie (mis_bgh_8a)
        # 5 distinct beast cages/barracks that release monsters onDeath!
        for i in range(1, 6):
            b_uid = f"smugglerBarracks{i}"
            if b_uid in bld_map and not any(b["uid"] == b_uid for b in buildings):
                place_building(b_uid, prefer_radius=rng.uniform(3, 7), margin=1, limit=11)

        place_building("smugglerStarport5", prefer_radius=6, margin=1, limit=12)
        place_building("smugglerCreditStorage5", prefer_radius=4, margin=1, limit=10)
        place_building("smugglerMaterialsStorage5", prefer_radius=4, margin=1, limit=10)
        place_building("smugglerCreditGenerator5", prefer_radius=5, margin=1, limit=11)
        place_building("smugglerMaterialsGenerator5", prefer_radius=5, margin=1, limit=11)

        for t_uid in ["smugglerRapidFireTurret5", "smugglerBurstTurret5", "smugglerMortar3", "smugglerRocketTurret3"]:
            if t_uid in bld_map:
                place_building(t_uid, prefer_radius=rng.uniform(7, 11), margin=1, limit=14)

        # Distinct holding pens layout: central court with 4 corner wall enclosures
        wall_uid = "smugglerWall4"
        # Outer ring with gate openings
        outer = rect_ring(-14, 14, -14, 14, openings=[(0, -14), (0, 14), (-14, 0), (14, 0)])
        place_walls(outer, wall_uid)
        # Inner pen partitions
        part1 = line_wall(-14, 0, -4, 0)
        part2 = line_wall(4, 0, 14, 0)
        part3 = line_wall(0, -14, 0, -4)
        part4 = line_wall(0, 4, 0, 14)
        place_walls(part1 + part2 + part3 + part4, wall_uid)

    elif cat == "rancor":
        # Rancor Den (mis_bgh_9)
        # smugglerShieldGenerator5 contains the Rancor!
        if "smugglerShieldGenerator5" in bld_map and not any(b["uid"] == "smugglerShieldGenerator5" for b in buildings):
            place_building("smugglerShieldGenerator5", prefer_radius=3, margin=1, limit=8)

        place_building("smugglerDroidHut1", prefer_radius=4, margin=1, limit=9)
        place_building("smugglerBarracks5", prefer_radius=5, margin=1, limit=11)
        place_building("smugglerStarport5", prefer_radius=5, margin=1, limit=11)
        place_building("smugglerCreditStorage5", prefer_radius=4, margin=1, limit=10)
        place_building("smugglerMaterialsStorage5", prefer_radius=4, margin=1, limit=10)

        for t_uid in ["smugglerRapidFireTurret5", "smugglerBurstTurret5", "smugglerMortar3", "smugglerRocketTurret3"]:
            if t_uid in bld_map:
                place_building(t_uid, prefer_radius=rng.uniform(7, 12), margin=1, limit=15)

        # Boulders and bone ruins around the lair
        for r in ["blockerRockLarge1", "blockerRockMedium1", "junkLarge1"]:
            if r in bld_map:
                place_building(r, prefer_radius=rng.uniform(4, 9), margin=0, limit=14)

        wall_uid = "smugglerWall4"
        walls = rect_ring(-13, 13, -13, 13, openings=[(-13, 0), (13, 0), (0, -13)])
        place_walls(walls, wall_uid)

    elif cat == "krayt":
        # Krayt Dragon Graveyard (mis_bgh_11)
        if "storyKraytBones1" in bld_map and not any(b["uid"] == "storyKraytBones1" for b in buildings):
            place_exact("storyKraytBones1", -2, -2, margin=0)

        for cr in ["blockerCrystalRockLarge1", "blockerCrystalRockMedium1", "blockerRockLarge1", "junkLarge1"]:
            if cr in bld_map:
                for _ in range(2):
                    place_building(cr, prefer_radius=rng.uniform(3, 10), margin=1, limit=15)

        for _ in range(4):
            place_building("rebelRubble1", prefer_radius=rng.uniform(5, 11), margin=0, limit=15)

        # Ancient desert stones forming an eerie circular ring
        wall_uid = "tuskenWall2" if "tuskenWall2" in bld_map else "smugglerWall1"
        for _ in range(26):
            ang = rng.random() * 2 * math.pi
            rad = rng.uniform(10, 16)
            place_near(wall_uid, rad * math.cos(ang), rad * math.sin(ang), margin=0)

    elif cat == "womprat":
        # Womp Rat Warrens (mis_bgh_2, mis_bgh_13, mis_utts_2a, etc.)
        lvl = max(1, min(6, hq_lvl))

        # In mis_bgh_2, large4, large5, large6 are critical triggers!
        if "bgh_2" in asset:
            for req_nest in ["wompRatNestLarge4", "wompRatNestLarge5", "wompRatNestLarge6", "wompRatNestSmall5"]:
                if req_nest in bld_map and not any(b["uid"] == req_nest for b in buildings):
                    place_building(req_nest, prefer_radius=rng.uniform(2, 6), margin=1, limit=11)

        # Clusters of nests
        for _ in range(rng.randint(3, 5)):
            place_building(f"wompRatNestLarge{lvl}", prefer_radius=rng.uniform(2, 6), margin=1, limit=12)
        for _ in range(rng.randint(5, 8)):
            place_building(f"wompRatNestSmall{lvl}", prefer_radius=rng.uniform(4, 9), margin=1, limit=14)

        # Scenery: boulders, moisture vaporators, rusted junk
        for r_uid in ["blockerRockSmall1", "blockerRockMedium1", "junkLarge1", "junkMedium1", "moistureVaporatorSmall"]:
            if r_uid in bld_map:
                place_building(r_uid, prefer_radius=rng.uniform(5, 12), margin=0, limit=16)

        # Barricades: scattered defensive barricades
        wall_uid = "tuskenWall1" if "tuskenWall1" in bld_map else "smugglerWall1"
        for _ in range(30):
            ang = rng.random() * 2 * math.pi
            rad = rng.uniform(8, 15)
            place_near(wall_uid, rad * math.cos(ang), rad * math.sin(ang), margin=0)

    elif cat == "village":
        # Ruined mercenary settlement (mis_bgh_7)
        place_building("smugglerStarport4", prefer_radius=5, margin=1, limit=10)
        place_building("smugglerDroidHut1", prefer_radius=3, margin=1, limit=8)
        place_building("blockerDesertHouseSmall1", prefer_radius=4, margin=1, limit=10)
        place_building("blockerDesertHouseLarge1", prefer_radius=6, margin=1, limit=12)
        place_building("blockerDesertCantina1", prefer_radius=7, margin=1, limit=13)
        if "tatooineJawaSandcrawler1" in bld_map:
            place_building("tatooineJawaSandcrawler1", prefer_radius=9, margin=1, limit=15)

        for _ in range(3):
            place_building("rebelRubble1", prefer_radius=rng.uniform(4, 10), margin=0, limit=14)
        for t_uid in ["smugglerRapidFireTurret5", "smugglerBurstTurret5"]:
            if t_uid in bld_map:
                place_building(t_uid, prefer_radius=rng.uniform(8, 12), margin=1, limit=15)

        wall_uid = "smugglerWall4"
        walls = rect_ring(-13, 13, -12, 12, openings=[(0, -12), (0, 12), (-13, 0), (13, 0)])
        place_walls(walls, wall_uid)

    elif cat == "dewback":
        # Dewback desert paddock (mis_bgh_4a)
        place_building("smugglerBarracks4", prefer_radius=3, margin=1, limit=8)
        place_building("smugglerStarport5", prefer_radius=5, margin=1, limit=11)
        place_building("smugglerCreditStorage5", prefer_radius=4, margin=1, limit=9)
        place_building("smugglerMaterialsStorage5", prefer_radius=4, margin=1, limit=9)
        place_building("blockerDesertHouseSmall1", prefer_radius=6, margin=1, limit=11)
        place_building("moistureVaporatorLarge", prefer_radius=7, margin=1, limit=12)

        for t_uid in ["smugglerRapidFireTurret5", "smugglerBurstTurret5", "smugglerMortar3"]:
            if t_uid in bld_map:
                place_building(t_uid, prefer_radius=rng.uniform(7, 11), margin=1, limit=14)

        # Enclosure with distinct Dewback corral
        wall_uid = "smugglerWall4"
        outer = rect_ring(-14, 14, -13, 13, openings=[(0, -13), (0, 13)])
        corral = rect_ring(-12, -2, -10, 2, openings=[(-2, -4)])
        place_walls(outer + corral, wall_uid)

    elif cat == "tusken":
        # Tusken Raider Tribal Camp with Bantha Corral
        for _ in range(rng.randint(2, 4)):
            place_building(f"tuskenHouseLarge{hq_lvl}", prefer_radius=rng.uniform(3, 7), margin=1, limit=10)
        for _ in range(rng.randint(3, 5)):
            place_building(f"tuskenHouseSmall{hq_lvl}", prefer_radius=rng.uniform(4, 9), margin=1, limit=12)

        if "tuskenFlag1" in bld_map:
            place_building("tuskenFlag1", prefer_radius=4, margin=1, limit=8)
            place_building("tuskenFlag1", prefer_radius=6, margin=1, limit=10)

        for _ in range(rng.randint(2, 4)):
            place_building(f"tuskenAutoTurret{hq_lvl}", prefer_radius=rng.uniform(6, 11), margin=1, limit=13)
        for _ in range(rng.randint(1, 3)):
            place_building(f"tuskenBurstTurret{hq_lvl}", prefer_radius=rng.uniform(7, 12), margin=1, limit=14)

        # Bantha corral & curved organic barricades
        wall_uid = f"tuskenWall{min(4, hq_lvl)}"
        # Corral oval on the eastern side
        corral_tiles = []
        for deg in range(0, 360, 12):
            rad = math.radians(deg)
            cx = int(8 + 5 * math.cos(rad))
            cz = int(4 * math.sin(rad))
            if deg not in (0, 12): # Gate opening
                corral_tiles.append((cx, cz))
        place_walls(corral_tiles, wall_uid)

        # Main tribal perimeter
        perimeter = rect_ring(-13, 14, -13, 13, openings=[(-13, 0), (0, -13), (0, 13)])
        place_walls(perimeter, wall_uid)

    else:
        # Military Bases (Empire, Rebel, Smuggler Syndicates, Outposts)
        # Primary military buildings
        for b_name in ["Barracks", "Starport", "Factory"]:
            uid = f"{faction}{b_name}{hq_lvl}"
            if uid in bld_map and not any(b["uid"] == uid for b in buildings):
                place_building(uid, prefer_radius=rng.uniform(4, 8), margin=1, limit=12)

        # Storages & Generators
        for b_name in ["CreditStorage", "MaterialsStorage", "CreditGenerator", "MaterialsGenerator"]:
            uid = f"{faction}{b_name}{hq_lvl}"
            if uid in bld_map and not any(b["uid"] == uid for b in buildings):
                place_building(uid, prefer_radius=rng.uniform(3, 7), margin=1, limit=11)

        # Support facilities
        for b_name in ["SquadBuilding", "OffenseLab", "ShieldGenerator", "TacticalCommand"]:
            uid = f"{faction}{b_name}{min(hq_lvl, 5)}"
            if uid in bld_map and not any(b["uid"] == uid for b in buildings):
                place_building(uid, prefer_radius=rng.uniform(3, 8), margin=1, limit=11)

        # Turrets
        turret_types = ["RapidFireTurret", "BurstTurret", "Mortar", "RocketTurret", "SonicTurret"]
        turret_pool = [f"{faction}{t}{hq_lvl}" for t in turret_types if f"{faction}{t}{hq_lvl}" in bld_map]
        if not turret_pool:
            turret_pool = [f"{faction}RapidFireTurret1"]

        num_turrets = min(12, max(5, hq_lvl + 2))
        for i in range(num_turrets):
            t_uid = turret_pool[i % len(turret_pool)]
            place_building(t_uid, prefer_radius=rng.uniform(7, 13), margin=1, limit=15)

        # Wall architecture selection based on arch_style
        wall_uid = f"{faction}Wall{min(8, hq_lvl)}"
        if planet == "planet6" and f"erkitWall{min(8, hq_lvl)}" in bld_map:
            wall_uid = f"erkitWall{min(8, hq_lvl)}"

        if arch_style == "citadel":
            # Style 1: Fortified Inner Keep + Outer Ward with corner bastions
            inner = rect_ring(-6, 6, -6, 6, openings=[(0, -6), (0, 6)])
            outer_rad = min(16, 10 + hq_lvl // 2)
            outer = rect_ring(-outer_rad, outer_rad, -outer_rad, outer_rad,
                              openings=[(-outer_rad, 0), (outer_rad, 0), (0, -outer_rad), (0, outer_rad)])
            place_walls(inner + outer, wall_uid)

        elif arch_style == "compartment":
            # Style 2: Compartmentalized 4-sector fortress with interior cross-walls
            rad = min(16, 11 + hq_lvl // 2)
            outer = rect_ring(-rad, rad, -rad, rad, openings=[(-rad, 0), (rad, 0), (0, -rad), (0, rad)])
            # Cross-walls with openings
            cross1 = line_wall(-rad, 0, rad, 0, openings=[(-5, 0), (5, 0), (-1, 0), (0, 0), (1, 0)])
            cross2 = line_wall(0, -rad, 0, rad, openings=[(0, -5), (0, 5), (0, -1), (0, 0), (0, 1)])
            place_walls(outer + cross1 + cross2, wall_uid)

        elif arch_style == "bastion_wings":
            # Style 3: Bastion wings with funnel kill-zones
            rad = min(16, 11 + hq_lvl // 2)
            outer = rect_ring(-rad, rad, -rad, rad, openings=[(0, -rad)])
            # Forward defensive wings funneling into the base
            wing1 = line_wall(-8, -rad, -3, -rad + 5)
            wing2 = line_wall(8, -rad, 3, -rad + 5)
            place_walls(outer + wing1 + wing2, wall_uid)

        else:
            # Style 4: Staggered asymmetrical compound
            rad1 = min(15, 10 + hq_lvl // 2)
            outer = rect_ring(-rad1, rad1, -rad1 + 2, rad1 - 2,
                              openings=[(-rad1, 0), (rad1, 0), (0, -rad1 + 2), (0, rad1 - 2)])
            # Interior baffle wall protecting command sector
            baffle = line_wall(-5, 4, 5, 4, openings=[(0, 4)])
            place_walls(outer + baffle, wall_uid)

    # =========================================================================
    # STEP 5: PLANET-SPECIFIC ENVIRONMENTAL SCENERY
    # =========================================================================
    props_to_add = []
    if planet == "planet1": # Tatooine
        props_to_add = ["moistureVaporatorSmall", "blockerRockSmall1", "blockerRockMedium1", "junkSmall1"]
    elif planet == "planet3": # Dandoran
        props_to_add = ["treeSmall1", "treeMedium1", "blockerDanRockSmall1", "blockerDanJunkSmall1"]
    elif planet == "planet6": # Er'Kit
        props_to_add = ["blockerErkRockSmall1", "blockerErkRockMedium1", "blockerErkJunkSmall1"]
    elif planet == "planet8": # Yavin 4
        props_to_add = ["blockerYavRockSmall1", "treeMedium1", "rebelRubble1", "blockerYavJunkSmall1"]
    elif planet == "planet21": # Hoth
        props_to_add = ["blockerHotRockSmall1", "blockerHotRockMedium1", "blockerHotJunkSmall1"]
    elif planet == "planet23": # Takodana
        props_to_add = ["blockerTakRockSmall1", "treeSmall1", "blockerTakJunkSmall1"]

    for prop in props_to_add:
        if prop in bld_map:
            place_building(prop, prefer_radius=rng.uniform(12, 18), margin=0, limit=BUILD_LIMIT, fallback=False)

    return {"map": {"next": 0, "planet": planet, "buildings": buildings}}


def main():
    print("=== Star Wars Commander Mission Base Generator (Story & Diversity Enhanced) ===")
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

        cat, faction, hq_lvl, arch_style = determine_category_and_hq(asset, info)
        base_data = build_layout(asset, info, cat, faction, hq_lvl, arch_style, bld_map)

        # Validation
        blds = base_data["map"]["buildings"]
        assert len(blds) >= 10, f"Too few buildings for {asset}: {len(blds)}"
        bld_uids = set(b["uid"] for b in blds)

        for b in blds:
            assert b["uid"] in bld_map, f"Invalid building {b['uid']} in {asset}"
            x, z = b["x"], b["z"]
            assert GRID_MIN <= x <= GRID_MAX and GRID_MIN <= z <= GRID_MAX, f"Out of bounds in {asset}: {b}"

        # Rigorous check: all script buildings present
        for req_b in info["script_buildings"]:
            assert req_b in bld_uids, f"Missing script trigger {req_b} in {asset}"

        # Rigorous check: all encounter buildings present
        for req_b in info["enc_buildings"]:
            assert req_b in bld_uids, f"Missing encounter trigger {req_b} in {asset}"

        # Rigorous check: HQ present if needed
        needs_hq = any("HQ" in pt for _, _, pt in info["vc_reqs"])
        if needs_hq:
            assert any("HQ" in b["uid"] for b in blds), f"Missing HQ in {asset} when required by VC!"

        # Rigorous check: shield generator present if needed
        needs_shield = any("shield_generator" in pt for _, _, pt in info["vc_reqs"])
        if needs_shield:
            assert any(bld_map.get(b["uid"], {}).get("type") == "shield_generator" for b in blds), f"Missing shield generator in {asset} when required by VC!"

        # Sever any previous hardlinks before writing
        for p in [target_path, root_path]:
            if os.path.exists(p):
                try:
                    os.unlink(p)
                except OSError:
                    pass

        # Write to primary battles dir
        with open(target_path, "w", encoding="utf-8") as f:
            json.dump(base_data, f)

        # Write to swcFiles/battles root dir as well
        with open(root_path, "w", encoding="utf-8") as f:
            json.dump(base_data, f)

        for m_uid in info["mission_uids"]:
            manifest_map[m_uid] = filename

        generated_count += 1
        print(f"[GENERATE] {filename:18}: {len(blds):3} blds, cat={cat:10}, fac={faction:8}, hq={hq_lvl}, style={arch_style:12}, planet={info['planet']}")

    # Generate aliases (by map uid, mission uid, and tokenized pattern) so any request style resolves
    print(f"\nCreating aliases for battle requests (Total generated: {generated_count}, Preserved: {preserved_count})...")
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

        target_names = [f"{map_uid}.json", f"{m['uid']}.json"]

        tokens = map_uid.split("_")
        alpha = [t for t in tokens if t.isalpha() and t not in ("bat", "e", "r", "s")]
        fac = [t for t in tokens if t in ("e", "r", "s")]
        nums = [t for t in tokens if t.isdigit()]
        if alpha and fac and nums:
            target_names.append(f"mis_{alpha[0]}_{nums[-1]}_{fac[0]}.json")
            target_names.append(f"mis_{alpha[0]}_{nums[-1]}{fac[0]}.json")

        for alias_name in target_names:
            clean_name = alias_name[:-5] if alias_name.endswith(".json") else alias_name
            if alias_name == f"{asset}.json" or alias_name in PRESERVED_FILES or clean_name in needed:
                continue
            for folder in [BATTLES_DIR, BATTLES_ROOT_DIR]:
                alias_path = os.path.join(folder, alias_name)
                # Overwrite or create link
                if os.path.exists(alias_path) and not os.path.samefile(src_file, alias_path):
                    try:
                        os.unlink(alias_path)
                    except OSError:
                        pass
                if not os.path.exists(alias_path):
                    try:
                        os.link(src_file, alias_path)
                        alias_count += 1
                    except OSError:
                        # Fallback to file copy if hardlink fails
                        with open(src_file, "r") as sf, open(alias_path, "w") as df:
                            df.write(sf.read())
                        alias_count += 1

    print(f"Aliases verified/created: {alias_count}")
    print("Base map generation complete!")


if __name__ == "__main__":
    main()
