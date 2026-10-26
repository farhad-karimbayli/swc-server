#!/usr/bin/env python3
"""Rebuild a player's base as a fortress, keeping every building it already has.

Nothing is removed: every existing building keeps its key and is only moved.
Extra walls, turrets and shield generators are added on top, past the game's
own limits, using only building uids the base already has so the client is
never asked for art it might not have.

The layout is three closed wall rings around the HQ, with spokes that cut the
two outer bands into compartments:

    core      HQ, shield generators, turrets
    band 2    storages, champion platforms, turrets, shields
    band 3    turret belt, shields, small buildings
    outside   starports, factories, barracks, generators as a buffer the
              attacker has to chew through, traps along the outer wall

    python3 tools/fortify_base.py <base.json> <out.json>

<base.json> is a player's baseMap ({"next", "planet", "buildings"}).
"""

import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_layouts  # noqa: E402

# Every official base and template stays within -20..21 on both axes, so that
# is the area used here; the tiles beyond it have never been seen to load.
LO, HI = -20, 21
CENTRE = 0.5  # the middle of -20..21

# Ring half-sizes, measured as Chebyshev distance from CENTRE to a tile's centre.
RING_A, RING_B, RING_C = 5.5, 10.5, 14.5

# What is added past the existing buildings.
EXTRA_TURRETS = 24
EXTRA_SHIELDS = 4

# How many turrets and shields each zone gets, outermost last.
TURRETS = {"core": 6, "band2": 12, "band3": 20, "outside": 8}
SHIELDS = {"core": 2, "band2": 2, "band3": 4}


def dist(x, z):
    return max(abs(x - CENTRE), abs(z - CENTRE))


def ring(d):
    """Wall tiles of a closed square ring at Chebyshev distance d."""
    return [(x, z) for x in range(LO, HI + 1) for z in range(LO, HI + 1) if dist(x, z) == d]


def spokes(inner, outer, offsets):
    """Wall tiles joining two rings, on both sides of each axis at each offset."""
    tiles = []
    lo, hi = int(CENTRE - outer + 1), int(CENTRE + outer - 1)
    for off in offsets:
        for z in range(LO, HI + 1):
            if inner < dist(off, z) < outer:
                tiles.append((off, z))
        for x in range(LO, HI + 1):
            if inner < dist(x, off) < outer:
                tiles.append((x, off))
    return [t for t in tiles if lo <= t[0] <= hi and lo <= t[1] <= hi]


ZONES = {
    "core": lambda d: d < RING_A,
    "band2": lambda d: RING_A < d < RING_B,
    "band3": lambda d: RING_B < d < RING_C,
    "outside": lambda d: d > RING_C,
}


class Board:
    def __init__(self):
        self.taken = {}

    def free(self, x, z, w, h, zone):
        for cx in range(x, x + w):
            for cz in range(z, z + h):
                if not (LO <= cx <= HI and LO <= cz <= HI):
                    return False
                if (cx, cz) in self.taken or not ZONES[zone](dist(cx, cz)):
                    return False
        return True

    def take(self, x, z, w, h, key):
        for cx in range(x, x + w):
            for cz in range(z, z + h):
                self.taken[(cx, cz)] = key

    def near(self, w, h, tx, tz, zone):
        """Free spot in a zone whose centre is closest to (tx, tz)."""
        best = None
        for x in range(LO, HI - w + 2):
            for z in range(LO, HI - h + 2):
                d = (x + w / 2 - tx) ** 2 + (z + h / 2 - tz) ** 2
                if (best is None or d < best[0]) and self.free(x, z, w, h, zone):
                    best = (d, x, z)
        return None if best is None else best[1:]


def zone_radius(zone):
    return {"core": 3.0, "band2": 8.0, "band3": 12.5, "outside": 18.0}[zone]


def main():
    src, out = sys.argv[1], sys.argv[2]
    with open(src, encoding="utf-8") as fh:
        base = json.load(fh)
    data = {b["uid"]: b for b in gen_layouts.load_buildings()}

    def size(uid):
        return int(data[uid]["sizex"]), int(data[uid]["sizey"])

    def kind(uid):
        return data[uid].get("type")

    existing = base["buildings"]
    by_type = {}
    for b in existing:
        by_type.setdefault(kind(b["uid"]), []).append(b)

    next_key = base["next"]

    def new_building(uid):
        nonlocal next_key
        b = {"key": f"bld_{next_key}", "x": 0, "z": 0, "uid": uid}
        next_key += 1
        return b

    # Extra turrets copy the mix the base already has, round robin, so every
    # new one is a uid the client has already loaded art for.
    turret_uids = sorted({b["uid"] for b in by_type["turret"]})
    turrets = list(by_type["turret"]) + [new_building(turret_uids[i % len(turret_uids)])
                                         for i in range(EXTRA_TURRETS)]
    shield_uid = by_type["shield_generator"][0]["uid"]
    shields = list(by_type["shield_generator"]) + [new_building(shield_uid) for _ in range(EXTRA_SHIELDS)]
    wall_uid = by_type["wall"][0]["uid"]
    walls = list(by_type["wall"])

    board = Board()
    placed = []

    def put(b, x, z):
        w, h = size(b["uid"])
        board.take(x, z, w, h, b["key"])
        b["x"], b["z"] = x, z
        placed.append(b)

    def place_at_angles(items, zone, radius, offset=0.0):
        for i, b in enumerate(items):
            angle = offset + 2 * math.pi * i / max(1, len(items))
            w, h = size(b["uid"])
            tx = CENTRE + radius * math.cos(angle)
            tz = CENTRE + radius * math.sin(angle)
            spot = board.near(w, h, tx, tz, zone)
            if spot is None:
                raise SystemExit(f"no room in {zone} for {b['uid']} ({b['key']})")
            put(b, *spot)

    # Walls first, so nothing else can sit where a ring has to close.
    wall_tiles = ring(RING_A) + ring(RING_B) + ring(RING_C)
    wall_tiles += spokes(RING_A, RING_B, [0])
    wall_tiles += spokes(RING_B, RING_C, [-5, 6])
    wall_tiles = list(dict.fromkeys(wall_tiles))
    while len(walls) < len(wall_tiles):
        walls.append(new_building(wall_uid))
    if len(walls) > len(wall_tiles):
        raise SystemExit(f"{len(walls)} walls but only {len(wall_tiles)} wall tiles")
    for b, (x, z) in zip(walls, wall_tiles):
        put(b, x, z)

    # The HQ in the middle of the core.
    hq = by_type["HQ"][0]
    w, h = size(hq["uid"])
    put(hq, int(CENTRE - w / 2 + 0.5), int(CENTRE - h / 2 + 0.5))

    # Shields before turrets inside each zone: they are the long ones and need
    # first pick of the bands they fit in.
    i = 0
    for zone, count in SHIELDS.items():
        place_at_angles(shields[i:i + count], zone, zone_radius(zone), offset=math.pi / 4)
        i += count
    assert i == len(shields), (i, len(shields))

    # Champion platforms and storages are what an attacker comes for: band 2.
    place_at_angles(by_type["champion_platform"], "band2", 8.0, offset=math.pi / 2)
    place_at_angles(by_type["storage"], "band2", 8.0, offset=math.pi / 9)

    i = 0
    for zone, count in TURRETS.items():
        place_at_angles(turrets[i:i + count], zone, zone_radius(zone), offset=math.pi / count)
        i += count
    assert i == len(turrets), (i, len(turrets))

    # Small buildings fill the turret belt.
    small = [b for t in ("troop_research", "cantina", "navigation_center") for b in by_type.get(t, [])]
    place_at_angles(small, "band3", 12.5, offset=math.pi / 3)

    # Traps where troops walk: just outside the outer wall.
    traps = by_type.get("trap", [])
    place_at_angles(traps, "outside", RING_C + 1.5, offset=math.pi / 15)

    # Everything else is a buffer outside, biggest first so it still fits.
    done = {b["key"] for b in placed}
    rest = [b for b in existing if b["key"] not in done]
    rest.sort(key=lambda b: -(size(b["uid"])[0] * size(b["uid"])[1]))
    place_at_angles(rest, "outside", 18.5, offset=math.pi / 7)

    placed_keys = {b["key"] for b in placed}
    missing = [b["key"] for b in existing if b["key"] not in placed_keys]
    if missing:
        raise SystemExit(f"not placed: {missing}")

    result = dict(base)
    result["buildings"] = placed
    result["next"] = next_key
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(result, fh)

    added = len(placed) - len(existing)
    print(f"{len(existing)} existing buildings moved, {added} added, {len(placed)} total")
    counts = {}
    for b in placed:
        counts[kind(b["uid"])] = counts.get(kind(b["uid"]), 0) + 1
    print("  " + ", ".join(f"{k} {v}" for k, v in sorted(counts.items())))


if __name__ == "__main__":
    main()
