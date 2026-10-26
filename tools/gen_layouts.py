#!/usr/bin/env python3
"""Generate dev base layouts for the layouts/ folder.

A layout is the same JSON the LayoutManager tool wrote and the same shape
GameDataManagerImpl.setupDevBases reads: a flat array of buildings, each with a
key, a lower-left corner (x, z) and a uid.

Composition comes from BuildingData in base.json, so a generated base only ever
contains buildings the client already knows about, at levels an HQ of that level
is allowed to own. The server stores these maps with "empire"/"rebel" swapped
for "neutral" and swaps the attacker's faction back in when it serves them, so
every building used here must exist in both factions.

    python3 tools/gen_layouts.py            # writes layouts/*.json
    python3 tools/gen_layouts.py --check    # verify existing layouts only
"""

import argparse
import json
import math
import os
import random
import re
import sys
from itertools import zip_longest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE_JSON = os.path.join(ROOT, "swcFiles", "1584443067", "patches", "base.json")
LAYOUTS_DIR = os.path.join(ROOT, "layouts")
# A real, hand built HQ11 base: the maxed out player the server hands to a
# returning client. Worth having among the dev bases for the variety.
TEMPLATE_BASE = os.path.join(ROOT, "src", "main", "resources", "existingPlayer", "playerLogin.json")

# Buildable area, in tiles. The maxed out base in existingPlayer/playerLogin.json
# occupies -19..21 on both axes, so the real area is at least that big; -22..21
# is that with a tile to spare on each side.
GRID_MIN, GRID_MAX = -22, 22

# Buildings stay inside this radius so the wall rings have somewhere to go.
BUILD_LIMIT = 20

FACTIONS = ("empire", "rebel")
HQ_LEVELS = (5, 6, 7, 8, 9, 10, 11)

# Three differently laid out bases per HQ level. The faction a layout is written
# in does not survive loading - setupDevBases neutralises the uids and skips any
# base whose checksum it has already seen - so an empire base and its rebel
# mirror are the same base to the server, and only varied geometry adds targets.
VARIANTS = ("a", "b", "c")

# Families placed at their allowed quantity, in placement order (big first).
# "turret" members share one pool, see TURRET_FAMILIES below.
SINGLE_FAMILIES = [
    "Starport",
    "Factory",
    "SquadBuilding",
    "TacticalCommand",
    "FleetCommand",
    "Armory",
    "ShieldGenerator",
    "CreditGenerator",
    "MaterialsGenerator",
    "ContrabandGenerator",
    "CreditStorage",
    "MaterialsStorage",
    "ContrabandStorage",
    "Barracks",
    "OffenseLab",
    "ContrabandCantina",
    "NavigationCenter",
    "ScoutTower",
    "DroidHut",
]

TURRET_FAMILIES = ["BurstTurret", "RapidFireTurret", "RocketTurret", "Mortar", "SonicTurret"]

TRAP_FAMILIES = ["TrapStrikeGeneric", "TrapStrikeAOE", "TrapStrikeHeavy", "TrapDropship"]

CHAMPION_FAMILY = "PlatformDroideka"

# Type HQ is not only the headquarters: mining complexes, munition plants and
# the like carry it too, and setupDevBases would read their level as the base's
# HQ level. They are never part of a player base, so they are excluded.
EXCLUDED_TYPES = {"housing"}


def load_buildings():
    with open(BASE_JSON, encoding="utf-8") as fh:
        data = json.load(fh)
    return data["content"]["objects"]["BuildingData"]


def family_of(uid):
    return re.sub(r"\d+$", "", uid)


def hq_requirement(building):
    """Lowest HQ level that unlocks this building, 1 when it is ungated."""
    req = building.get("requirements") or []
    levels = [int(m.group(1)) for m in (re.match(r"\w+?HQ(\d+)$", r) for r in req) if m]
    return max(levels) if levels else 1


def build_catalogue(buildings):
    """faction -> family -> list of records sorted by level."""
    catalogue = {}
    for b in buildings:
        faction = b.get("faction")
        if faction not in FACTIONS or b.get("type") in EXCLUDED_TYPES:
            continue
        record = {
            "uid": b["uid"],
            "lvl": int(b.get("lvl") or 0),
            "max": int(b.get("maxQuantity") or 0),
            "w": int(b["sizex"]),
            "h": int(b["sizey"]),
            "type": b.get("type"),
            "hq_req": hq_requirement(b),
        }
        family = family_of(b["uid"])
        catalogue.setdefault(faction, {}).setdefault(family, []).append(record)
    for families in catalogue.values():
        for records in families.values():
            records.sort(key=lambda r: r["lvl"])
    return catalogue


def best_variant(catalogue, faction, family, hq_level):
    """Highest level of a family an HQ of this level may own, or None."""
    records = catalogue.get(faction, {}).get(family)
    if not records:
        return None
    allowed = [r for r in records if r["lvl"] <= hq_level and r["hq_req"] <= hq_level]
    return allowed[-1] if allowed else None


def compose(catalogue, faction, hq_level):
    """The list of (record, count) that makes up a base of this HQ level."""
    wanted = []

    hq = best_variant(catalogue, faction, f"{faction}HQ", hq_level)
    if hq is None or hq["lvl"] != hq_level:
        raise SystemExit(f"no {faction}HQ{hq_level} in base.json")
    wanted.append((hq, 1))

    for family in SINGLE_FAMILIES:
        variant = best_variant(catalogue, faction, f"{faction}{family}", hq_level)
        if variant and variant["max"] > 0:
            wanted.append((variant, variant["max"]))

    # Every turret family reports the same number: it is one shared pool, not a
    # cap per family. Spread it over the families that are unlocked.
    turrets = [
        v
        for v in (best_variant(catalogue, faction, f"{faction}{f}", hq_level) for f in TURRET_FAMILIES)
        if v
    ]
    if turrets:
        pool = max(v["max"] for v in turrets)
        for i, variant in enumerate(turrets):
            share = pool // len(turrets) + (1 if i < pool % len(turrets) else 0)
            if share:
                wanted.append((variant, share))

    for family in TRAP_FAMILIES:
        variant = best_variant(catalogue, faction, f"{faction}{family}", hq_level)
        if variant and variant["max"] > 0:
            wanted.append((variant, variant["max"]))

    champion = best_variant(catalogue, faction, f"{faction}{CHAMPION_FAMILY}", hq_level)
    if champion:
        # The trailing number is the champion's level, not a building level;
        # level 1 is the one a base of any HQ level can field.
        first = catalogue[faction][f"{faction}{CHAMPION_FAMILY}"][0]
        wanted.append((first, 1))

    return wanted


class Grid:
    """Occupancy of the buildable area, with a one tile gap between buildings."""

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
        """Nearest free spot to a ring of the given radius around the centre.

        With a pool wider than one, the spot is drawn from that many of the best
        candidates, which is what makes two bases of the same HQ level differ.
        """
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
        """Free spot closest to a point, for laying buildings out along a ring."""
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
    """The tiles of a square ring of this half-size, centred on the origin."""
    lo, hi = -radius, radius
    if lo < GRID_MIN or hi >= GRID_MAX:
        return []
    ring = [(x, lo) for x in range(lo, hi + 1)]
    ring += [(hi, z) for z in range(lo + 1, hi + 1)]
    ring += [(x, hi) for x in range(hi - 1, lo - 1, -1)]
    ring += [(lo, z) for z in range(hi - 1, lo, -1)]
    return ring


def wall_radius(wall_budget):
    """Half-size of the widest closed ring the wall allowance pays for."""
    return max(4, min(BUILD_LIMIT - 3, wall_budget // 8))


def inner_walls(grid, wall_budget):
    """Spend walls left over from the perimeter on a second ring inside it."""
    placed = []
    radius = wall_radius(wall_budget) - 3
    while wall_budget >= 12 and radius >= 4:
        ring = square_ring(radius)
        if ring and len(ring) <= wall_budget and not any(t in grid.taken for t in ring):
            for tile in ring:
                grid.occupy(tile[0], tile[1], 1, 1)
            placed.extend(ring)
            wall_budget -= len(ring)
            radius -= 2  # leave a lane between this ring and the next one in
        else:
            radius -= 1
    return placed


def generate(catalogue, faction, hq_level, rng=None):
    """One base: a walled core, traps inside it, a turret belt outside."""
    rng = rng or random.Random(0)
    grid = Grid()
    buildings = []
    key = 1

    wanted = compose(catalogue, faction, hq_level)
    walls = best_variant(catalogue, faction, f"{faction}Wall", hq_level)

    # The perimeter is claimed before anything is built, otherwise the base
    # sprawls and no closed ring is affordable afterwards.
    budget = walls["max"] if walls else 0
    perimeter = square_ring(wall_radius(budget)) if budget else []
    for tile in perimeter:
        grid.occupy(tile[0], tile[1], 1, 1)
    inside = wall_radius(budget) - 1 if perimeter else BUILD_LIMIT

    def emit(record, count, radius, margin, limit=BUILD_LIMIT, fallback=None, pool=1):
        nonlocal key
        for _ in range(count):
            spot = grid.place(record["w"], record["h"], prefer_radius=radius, margin=margin,
                              limit=limit, rng=rng, pool=pool)
            if spot is None and fallback is not None:
                spot = grid.place(record["w"], record["h"], prefer_radius=fallback, margin=margin,
                                  rng=rng, pool=pool)
            if spot is None:
                print(f"  ! no room left for {record['uid']}", file=sys.stderr)
                break
            buildings.append({"key": f"bld_{key}", "x": spot[0], "z": spot[1], "uid": record["uid"]})
            key += 1

    # What a player walls in first: the HQ, then the loot, then the rest of the
    # core. Whatever no longer fits inside is built outside the wall, which is
    # what a real base at these wall counts looks like anyway.
    priority = {"HQ": 0, "storage": 1, "squad": 2, "champion_platform": 3, "resource": 4}
    core = [(r, c) for r, c in wanted if r["type"] not in ("turret", "trap")]
    rng.shuffle(core)  # breaks ties between buildings of the same rank and size
    core.sort(key=lambda item: (priority.get(item[0]["type"], 5), -(item[0]["w"] * item[0]["h"])))
    for record, count in core:
        emit(record, count, radius=0, margin=0, limit=inside, fallback=inside + 3, pool=3)

    # Traps sit where attackers walk, inside the base.
    for record, count in [(r, c) for r, c in wanted if r["type"] == "trap"]:
        emit(record, count, radius=inside * 0.6, margin=0, limit=inside, fallback=inside + 2, pool=6)

    # Turrets form a belt outside the wall. They are dealt out around a circle,
    # one family after another, so that every side of the base is covered by a
    # mix of turret types instead of one type holding one side.
    pools = [[r] * c for r, c in wanted if r["type"] == "turret"]
    belt = [record for group in zip_longest(*pools) for record in group if record]
    belt_radius = inside + 3 + rng.choice((0, 1))
    offset = rng.random() * 2 * math.pi
    for i, record in enumerate(belt):
        angle = offset + 2 * math.pi * i / len(belt)
        spot = grid.place_near(
            record["w"],
            record["h"],
            belt_radius * math.cos(angle),
            belt_radius * math.sin(angle),
            margin=1,
        )
        if spot is None:
            print(f"  ! no room left for {record['uid']}", file=sys.stderr)
            continue
        buildings.append({"key": f"bld_{key}", "x": spot[0], "z": spot[1], "uid": record["uid"]})
        key += 1

    for x, z in perimeter + (inner_walls(grid, budget - len(perimeter)) if budget else []):
        buildings.append({"key": f"bld_{key}", "x": x, "z": z, "uid": walls["uid"]})
        key += 1

    return buildings


def find_buildings(node):
    """The building list inside a player template, wherever it is nested."""
    if isinstance(node, dict):
        listed = node.get("buildings")
        if isinstance(listed, list) and listed and isinstance(listed[0], dict) and "uid" in listed[0]:
            return listed
        for value in node.values():
            found = find_buildings(value)
            if found:
                return found
    elif isinstance(node, list):
        for value in node:
            found = find_buildings(value)
            if found:
                return found
    return None


def export_template(raw_buildings):
    """The template base, stripped down to what a layout file holds."""
    return [
        {"key": b["key"], "x": b["x"], "z": b["z"], "uid": b["uid"]}
        for b in raw_buildings
    ]


def check(buildings, catalogue, faction, hq_level, source, index=None, other_uids=None):
    """Fail loudly on anything the server or the client would choke on."""
    errors = []
    if index is None:
        index = {}
        for records in catalogue[faction].values():
            for r in records:
                index[r["uid"]] = r
    other = "rebel" if faction == "empire" else "empire"
    if other_uids is None:
        other_uids = {r["uid"] for records in catalogue[other].values() for r in records}

    cells = {}
    hq_count = 0
    for b in buildings:
        record = index.get(b["uid"])
        if record is None:
            errors.append(f"{b['uid']} is not in base.json")
            continue
        if record["type"] == "HQ":
            hq_count += 1
            if record["lvl"] != hq_level:
                errors.append(f"{b['uid']} is not HQ level {hq_level}")
        # Scenery (rocks, junk) belongs to no faction, so the server's faction
        # swap leaves it alone and there is nothing to look for.
        if faction in b["uid"]:
            counterpart = b["uid"].replace(faction, other)
            if counterpart not in other_uids:
                errors.append(f"{b['uid']} has no {other} counterpart ({counterpart})")
        if not (GRID_MIN <= b["x"] and b["x"] + record["w"] <= GRID_MAX):
            errors.append(f"{b['key']} {b['uid']} is outside the grid on x")
        if not (GRID_MIN <= b["z"] and b["z"] + record["h"] <= GRID_MAX):
            errors.append(f"{b['key']} {b['uid']} is outside the grid on z")
        for x in range(b["x"], b["x"] + record["w"]):
            for z in range(b["z"], b["z"] + record["h"]):
                if (x, z) in cells:
                    errors.append(f"{b['key']} {b['uid']} overlaps {cells[(x, z)]} at {x},{z}")
                cells[(x, z)] = b["key"]

    if hq_count != 1:
        errors.append(f"expected exactly one HQ, found {hq_count}")
    if len({b["key"] for b in buildings}) != len(buildings):
        errors.append("duplicate building keys")

    for message in errors:
        print(f"  ! {source}: {message}", file=sys.stderr)
    return not errors


def neutral_signature(buildings):
    """What the server compares layouts by: the map with the faction taken out."""
    return tuple(
        (b["key"], b["x"], b["z"], b["uid"].replace("empire", "neutral").replace("rebel", "neutral"))
        for b in buildings
    )


def distinct(directory):
    """saveDevBase drops a base whose checksum it has seen, so two layouts that
    only differ by faction are one base. Catch that here instead of wondering
    later why the collection is short."""
    seen = {}
    ok = True
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".json"):
            continue
        with open(os.path.join(directory, name), encoding="utf-8") as fh:
            signature = neutral_signature(json.load(fh))
        if signature in seen:
            print(f"  ! {name} is the same base as {seen[signature]} once neutralised", file=sys.stderr)
            ok = False
        seen[signature] = name
    return ok


def validation_index(raw_buildings):
    """Every building in the game data, for checking layouts we did not write."""
    index = {}
    uids_by_faction = {}
    for b in raw_buildings:
        index[b["uid"]] = {
            "uid": b["uid"],
            "lvl": int(b.get("lvl") or 0),
            "w": int(b["sizex"]),
            "h": int(b["sizey"]),
            "type": b.get("type"),
        }
        uids_by_faction.setdefault(b.get("faction"), set()).add(b["uid"])
    return index, uids_by_faction


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify layouts/ instead of writing it")
    args = parser.parse_args()

    raw = load_buildings()
    catalogue = build_catalogue(raw)
    index, uids_by_faction = validation_index(raw)
    os.makedirs(LAYOUTS_DIR, exist_ok=True)
    ok = True

    def verify(buildings, faction, level, name):
        other = "rebel" if faction == "empire" else "empire"
        return check(buildings, catalogue, faction, level, name, index, uids_by_faction[other])

    if args.check:
        for name in sorted(os.listdir(LAYOUTS_DIR)):
            if not name.endswith(".json"):
                continue
            match = re.match(r"(empire|rebel)_hq(\d+)", name)
            if not match:
                print(f"  ! {name}: name should be <faction>_hq<level>.json", file=sys.stderr)
                ok = False
                continue
            faction, level = match.groups()
            with open(os.path.join(LAYOUTS_DIR, name), encoding="utf-8") as fh:
                buildings = json.load(fh)
            if verify(buildings, faction, int(level), name):
                print(f"  {name}: {len(buildings)} buildings, ok")
            else:
                ok = False
        return 0 if ok else 1

    def write(buildings, name, faction, level):
        nonlocal ok
        with open(os.path.join(LAYOUTS_DIR, name), "w", encoding="utf-8") as fh:
            json.dump(buildings, fh, indent=1)
            fh.write("\n")
        if not verify(buildings, faction, level, name):
            ok = False
        walls = sum(1 for b in buildings if "Wall" in b["uid"])
        print(f"  {name}: {len(buildings)} buildings ({walls} walls)")

    # Stale layouts from an earlier run would otherwise sit next to the new ones.
    for name in os.listdir(LAYOUTS_DIR):
        if name.endswith(".json"):
            os.remove(os.path.join(LAYOUTS_DIR, name))

    for level in HQ_LEVELS:
        for variant in VARIANTS:
            rng = random.Random(f"hq{level}{variant}")
            buildings = generate(catalogue, "empire", level, rng)
            write(buildings, f"empire_hq{level}{variant}.json", "empire", level)

    if os.path.exists(TEMPLATE_BASE):
        with open(TEMPLATE_BASE, encoding="utf-8") as fh:
            raw_template = find_buildings(json.load(fh))
        if raw_template:
            write(export_template(raw_template), "empire_hq11_stock.json", "empire", 11)

    if not distinct(LAYOUTS_DIR):
        ok = False

    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
