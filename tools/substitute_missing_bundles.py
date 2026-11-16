#!/usr/bin/env python3
"""Intelligently substitute missing critical Android asset bundles in Star Wars Commander.

Solves the missing art/audio gap by mapping missing bundles to their closest
counterparts (same building nearby levels, opposite-faction chapter buttons,
matching fillstates, character holos, and audio).

For each substituted bundle:
1. Links or copies the source asset to swcFiles/<v>/assetbundles/android/<target>.assetbundle
2. Calculates CRC32 of the file
3. Updates the manifest (__manifest_zyngaswc_prod.02045.json) with the new CRC
"""

import glob
import json
import os
import re
import shutil
import tempfile
import zlib
import UnityPy

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SWC_FILES = os.path.join(ROOT, "swcFiles")
MANIFEST_DIR = os.path.join(SWC_FILES, "manifests")
MANIFEST_PATH = os.path.join(MANIFEST_DIR, "__manifest_zyngaswc_prod.02045.json")


def load_manifest(path=MANIFEST_PATH):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_manifest(manifest, path=MANIFEST_PATH):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, separators=(",", ":"))


def compute_crc(filepath):
    with open(filepath, "rb") as f:
        return f"{zlib.crc32(f.read()) & 0xFFFFFFFF:08X}"


def find_present_and_missing(manifest):
    paths = manifest.get("paths", {})
    present = {}
    missing = {}

    for p, meta in paths.items():
        if not p.startswith("assetbundles/android/"):
            continue
        v = meta.get("v")
        p1 = os.path.join(SWC_FILES, str(v), p)
        p2 = os.path.join(SWC_FILES, p)
        if os.path.exists(p1):
            present[p] = (p1, meta)
        elif os.path.exists(p2):
            present[p] = (p2, meta)
        else:
            missing[p] = meta

    return present, missing


def resolve_substitutions(present, missing):
    subs = []

    # Map of chapter numbers to existing chapter buttons
    chap_buttons = {}
    for p in present:
        fn = os.path.basename(p)
        m = re.match(r"^chap_btn_cmp_(\d+)_[a-z]+_[er]\.assetbundle$", fn)
        if m:
            chap_buttons[int(m.group(1))] = p

    for p, meta in missing.items():
        fn = os.path.basename(p)

        # 1. Chapter buttons
        m = re.match(r"^chap_btn_cmp_(\d+)_[a-z]+_([er])\.assetbundle$", fn)
        if m:
            chap_num = int(m.group(1))
            if chap_num in chap_buttons:
                subs.append((p, chap_buttons[chap_num], f"Chapter {chap_num} counterpart"))
                continue
            # Fallback to any chapter button
            for pres_p in present:
                if "chap_btn_" in pres_p:
                    subs.append((p, pres_p, "Chapter button fallback"))
                    break
            continue

        # 2. Fillstates (_fillstates.assetbundle)
        if fn.endswith("_fillstates.assetbundle"):
            base = fn.replace("_fillstates.assetbundle", "")
            bld_match = re.match(r"^(.*?)_(emp|rbl|con|smg|tkn|dth)-mod(?:-up(\d+))?$", base)
            if bld_match:
                b_name, fac, lvl = bld_match.group(1), bld_match.group(2), int(bld_match.group(3) or 1)
                cands = []
                for pres_p in present:
                    if pres_p.endswith("_fillstates.assetbundle"):
                        p_fn = os.path.basename(pres_p).replace("_fillstates.assetbundle", "")
                        m2 = re.match(r"^(.*?)_(emp|rbl|con|smg|tkn|dth)-mod(?:-up(\d+))?$", p_fn)
                        if m2 and m2.group(1) == b_name:
                            p_fac, p_lvl = m2.group(2), int(m2.group(3) or 1)
                            # Same faction prioritized, closest level
                            priority = (0 if p_fac == fac else 1, abs(p_lvl - lvl))
                            cands.append((priority, pres_p))
                if cands:
                    cands.sort()
                    subs.append((p, cands[0][1], f"Fillstate: {os.path.basename(cands[0][1])}"))
                    continue
            # Generic fillstate fallback
            for pres_p in present:
                if "tradecenter_emp-mod" in pres_p and "_fillstates" in pres_p:
                    subs.append((p, pres_p, "Generic fillstate fallback"))
                    break
            continue

        # 3. Holos (*holo*)
        if "holo" in fn:
            # Opposite faction holo
            opp_fn = None
            if "_emp" in fn: opp_fn = fn.replace("_emp", "_rbl")
            elif "_rbl" in fn: opp_fn = fn.replace("_rbl", "_emp")
            elif "-emp" in fn: opp_fn = fn.replace("-emp", "-rbl")
            elif "-rbl" in fn: opp_fn = fn.replace("-rbl", "-emp")
            if opp_fn and f"assetbundles/android/{opp_fn}" in present:
                subs.append((p, f"assetbundles/android/{opp_fn}", "Holo: Opposite faction"))
                continue

            # Non-holo base model
            non_holo_fn = fn.replace("holo", "")
            if f"assetbundles/android/{non_holo_fn}" in present:
                subs.append((p, f"assetbundles/android/{non_holo_fn}", "Holo: Base unit model"))
                continue

            # Dialogue character transmission holos
            if fn.startswith("holo_"):
                # Try finding closest character transmission
                char_cands = [pres_p for pres_p in present if os.path.basename(pres_p).startswith("holo_")]
                if char_cands:
                    # Choose hansolo, leia, or veers as good neutral transmissions
                    preferred = [c for c in char_cands if any(k in c for k in ("hansolo", "leia", "veers", "bib"))]
                    src = preferred[0] if preferred else char_cands[0]
                    subs.append((p, src, f"Character Holo transmission fallback ({os.path.basename(src)})"))
                    continue

            # Tactical table 3D holos
            for pres_p in present:
                if "spiderdroidholo_rbl" in pres_p or "holo_bib" in pres_p:
                    subs.append((p, pres_p, "Holo: Tactical display fallback"))
                    break
            continue

        # 4. Transports & Shuttles
        if "healingshipcargo" in fn:
            for pres_p in present:
                if "healingshipcargo_rbl-mod" in pres_p:
                    subs.append((p, pres_p, "Transport: Rebel cargo counterpart"))
                    break
            if p in [s[0] for s in subs]: continue

        if "healingship" in fn:
            for pres_p in present:
                if "healingship_emp-mod" in pres_p:
                    subs.append((p, pres_p, "Transport: Base healing ship"))
                    break
            if p in [s[0] for s in subs]: continue

        # 5. Building 3D models with levels (-up<lvl>)
        m = re.match(r"^(.*?)[-_]up(\d+)\.assetbundle$", fn)
        if m:
            prefix, lvl = m.group(1), int(m.group(2))
            cands = []
            for pres_p in present:
                pres_fn = os.path.basename(pres_p)
                if pres_fn.endswith("_fillstates.assetbundle"):
                    continue
                m2 = re.match(r"^(.*?)[-_]up(\d+)\.assetbundle$", pres_fn)
                if m2 and m2.group(1) == prefix:
                    cands.append((abs(int(m2.group(2)) - lvl), pres_p))
            if cands:
                cands.sort()
                subs.append((p, cands[0][1], f"Building level {lvl} -> {os.path.basename(cands[0][1])}"))
                continue

        # 6. Faction counterpart for same model (_emp, _rbl, _smg, _con, _neu, _tkn, _dth, _empire, _rebel)
        factions = ["_emp", "_rbl", "_smg", "_con", "_neu", "_tkn", "_dth"]
        for f1 in factions:
            if f1 in fn:
                for f2 in factions:
                    if f1 == f2:
                        continue
                    opp_p = f"assetbundles/android/{fn.replace(f1, f2)}"
                    if opp_p in present:
                        subs.append((p, opp_p, f"Faction counterpart: {f1} -> {f2}"))
                        break
            if p in [s[0] for s in subs]:
                break
        if p in [s[0] for s in subs]:
            continue

        for f1, f2 in [("-emp", "-rbl"), ("-rbl", "-emp"), ("_empire", "_rebel"), ("_rebel", "_empire")]:
            if f1 in fn:
                opp_p = f"assetbundles/android/{fn.replace(f1, f2)}"
                if opp_p in present:
                    subs.append((p, opp_p, f"Faction counterpart: {f1} -> {f2}"))
                    break
        if p in [s[0] for s in subs]: continue

        # 6b. Event troop transports
        if fn.startswith("unittransport_event_troop_"):
            for cand in ("assetbundles/android/unittransport_event_troop_soldier.assetbundle",
                         "assetbundles/android/unittransport_event_troop_storm.assetbundle"):
                if cand in present:
                    subs.append((p, cand, "Transport: Event troop transport fallback"))
                    break
            if p in [s[0] for s in subs]: continue

        # 6c. Planet PvP maps
        if fn.startswith("planetpvp-"):
            for pres_p in sorted(present):
                if os.path.basename(pres_p).startswith("planetpvp-"):
                    subs.append((p, pres_p, "Planet PvP map fallback"))
                    break
            if p in [s[0] for s in subs]: continue

        # 6d. Neutral / planet walls
        if "wall_neu-mod" in fn:
            for cand in ("assetbundles/android/wall_tkn-mod.assetbundle",
                         "assetbundles/android/wall_emp-mod-up1.assetbundle"):
                if cand in present:
                    subs.append((p, cand, "Wall fallback"))
                    break
            if p in [s[0] for s in subs]: continue

        # 7. Skin variants (halfskin)
        if "halfskin" in fn:
            base_p = f"assetbundles/android/{fn.replace('halfskin', '')}"
            if base_p in present:
                subs.append((p, base_p, "Skin: Base unit model"))
                continue

        # 8. Audio
        if fn.startswith("audio_"):
            if "droid" in fn and "assetbundles/android/audio_securitydroid.assetbundle" in present:
                subs.append((p, "assetbundles/android/audio_securitydroid.assetbundle", "Audio: Droid sound fallback"))
                continue
            elif "assetbundles/android/audio_betaunit.assetbundle" in present:
                subs.append((p, "assetbundles/android/audio_betaunit.assetbundle", "Audio: Unit sound fallback"))
                continue

        # 9. Squad War crates -> objective crates of matching tier
        if fn.startswith("cratesqwar"):
            cand = f"assetbundles/android/{fn.replace('cratesqwar', 'crateobjec')}"
            if cand in present:
                subs.append((p, cand, "Crate: Squad war crate fallback"))
                continue

        # 10. Holonet / Command Center banners & character transmissions
        if fn.startswith(("hn_", "hh_", "hc_")):
            is_rebel = fn.endswith("_r.assetbundle") or "_rbl" in fn or "_rebel" in fn
            preferred = [
                "assetbundles/android/hn_cc_1_hondo_ohnaka_r.assetbundle" if is_rebel else "assetbundles/android/hn_cc_1_hondo_ohnaka_e.assetbundle",
                "assetbundles/android/hn_cc_1_hondo_ohnaka_e.assetbundle",
                "assetbundles/android/hc_cc_1_transportvehicle_e.assetbundle",
            ]
            for cand in preferred:
                if cand in present:
                    subs.append((p, cand, "Holonet banner fallback"))
                    break
            if p in [s[0] for s in subs]: continue

        # 11. Episode UI backgrounds & HUD icons
        if fn.startswith(("episode_", "epsiode_")):
            if "hudbg" in fn and "assetbundles/android/episode_hudbg_epspacebear1.assetbundle" in present:
                subs.append((p, "assetbundles/android/episode_hudbg_epspacebear1.assetbundle", "Episode HUD bg fallback"))
                continue
            if "_bg" in fn and "assetbundles/android/episode_epspacebear1_bg.assetbundle" in present:
                subs.append((p, "assetbundles/android/episode_epspacebear1_bg.assetbundle", "Episode bg fallback"))
                continue
            for pres_p in sorted(present):
                if os.path.basename(pres_p).startswith("episode_hudicon_") or os.path.basename(pres_p).startswith("epsiode_hudicon_"):
                    subs.append((p, pres_p, "Episode icon fallback"))
                    break
            if p in [s[0] for s in subs]: continue

        # 12. Perk, SquadWars, Store, HUD & unit icons
        icon_fallbacks = [
            ("perk_", "perk_"),
            ("squadwars_", "squadwars_info_images.assetbundle"),
            ("storeicon_", "storeicon_crystal_badge.assetbundle"),
            ("hudicon_", "hudicon_crate_starship.assetbundle"),
            ("ico_", "ico_prestige_droid.assetbundle"),
            ("icon_", "icon_atmosmig_emp.assetbundle"),
        ]
        matched_icon = False
        for pref, cand_name in icon_fallbacks:
            if fn.startswith(pref):
                if cand_name.endswith(".assetbundle") and f"assetbundles/android/{cand_name}" in present:
                    subs.append((p, f"assetbundles/android/{cand_name}", f"UI icon fallback ({pref})"))
                    matched_icon = True
                    break
                for pres_p in sorted(present):
                    if os.path.basename(pres_p).startswith(cand_name):
                        subs.append((p, pres_p, f"UI icon fallback ({pref})"))
                        matched_icon = True
                        break
                if matched_icon:
                    break
        if matched_icon:
            continue

        # 13. FX fallback
        if fn.startswith("fx_") and "assetbundles/android/fx_aura.assetbundle" in present:
            subs.append((p, "assetbundles/android/fx_aura.assetbundle", "VFX fallback"))
            continue

    return subs


def repack_bundle(src_path, dest_path, target_bundle_name):
    """Copy and rewrite Unity AssetBundle metadata so Unity finds the asset by target name."""
    target_stem = target_bundle_name.removesuffix(".assetbundle")
    is_audio = target_bundle_name.startswith("audio_")
    env = UnityPy.load(src_path)
    ab_objs = [o for o in env.objects if o.type.name == "AssetBundle"]

    if not ab_objs:
        os.makedirs(os.path.dirname(dest_path), exist_ok=True)
        if os.path.abspath(src_path) != os.path.abspath(dest_path):
            if os.path.exists(dest_path):
                os.unlink(dest_path)
            shutil.copyfile(src_path, dest_path)
        return

    ab = ab_objs[0].read()
    old_stem = ab.m_AssetBundleName.removesuffix(".assetbundle")
    ab.m_Name = target_bundle_name
    ab.m_AssetBundleName = target_bundle_name

    if not is_audio and ab.m_Container:
        new_container = []
        target_pids = {}
        rename_all = len(ab.m_Container) == 1
        for k, v in ab.m_Container:
            dir_part, fname = k.rsplit("/", 1) if "/" in k else ("", k)
            stem, ext = os.path.splitext(fname)
            new_stem = None
            if stem.lower() == old_stem.lower():
                new_stem = target_stem
            elif old_stem and stem.lower().startswith(old_stem.lower()):
                new_stem = target_stem + stem[len(old_stem):]
            elif rename_all:
                new_stem = target_stem

            if new_stem is not None:
                new_k = (f"{dir_part}/" if dir_part else "") + new_stem + ext
                new_container.append((new_k, v))
                if v.asset.path_id != 0:
                    target_pids[v.asset.path_id] = new_stem
            else:
                new_container.append((k, v))
        ab.m_Container = new_container
        for obj in env.objects:
            if obj.path_id in target_pids:
                d = obj.read()
                if hasattr(d, "m_Name"):
                    d.m_Name = target_pids[obj.path_id]
                    d.save()

    ab.save()
    dest_dir = os.path.dirname(dest_path)
    os.makedirs(dest_dir, exist_ok=True)
    fd, tmp_path = tempfile.mkstemp(prefix=".repack_", suffix=".assetbundle", dir=dest_dir)
    os.close(fd)
    try:
        with open(tmp_path, "wb") as f:
            f.write(env.file.save(packer="original"))
        os.replace(tmp_path, dest_path)
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)


def sync_custom_manifests(updated_crcs):
    pattern = os.path.join(MANIFEST_DIR, "__manifest_zyngaswc_prod.*.json")
    for mpath in sorted(glob.glob(pattern)):
        if mpath == MANIFEST_PATH:
            continue
        custom = load_manifest(mpath)
        changed = 0
        for p, (v, crc) in updated_crcs.items():
            entry = custom.get("paths", {}).get(p)
            if entry and entry.get("v") == v and entry.get("crc") != crc:
                entry["crc"] = crc
                changed += 1
        if changed:
            save_manifest(custom, mpath)
            print(f"  [SYNC] Updated {changed} CRCs in {os.path.basename(mpath)}")


def main():
    print("=== Star Wars Commander Asset Bundle Fallback Generator ===")
    manifest = load_manifest()
    present, missing = find_present_and_missing(manifest)
    print(f"Total Android bundles: {len(present) + len(missing)}")
    print(f"Present: {len(present)}, Missing: {len(missing)}")

    subs = resolve_substitutions(present, missing)
    print(f"\nFound {len(subs)} new missing-bundle substitutions.")

    updated_crcs = {}
    applied = 0
    for target_p, src_p, reason in subs:
        meta = manifest["paths"][target_p]
        v = meta.get("v")
        dest_path = os.path.join(SWC_FILES, str(v), target_p)
        src_path = present[src_p][0]
        target_fn = os.path.basename(target_p)

        repack_bundle(src_path, dest_path, target_fn)
        new_crc = compute_crc(dest_path)
        manifest["paths"][target_p]["crc"] = new_crc
        updated_crcs[target_p] = (v, new_crc)
        applied += 1
        print(f"  [SUB] {target_fn}  <--  {os.path.basename(src_p)} (CRC: {new_crc}, {reason})")

    # Also repack any previously substituted bundles whose internal AssetBundleName still mismatches
    repacked = 0
    for p, (fp, meta) in present.items():
        target_fn = os.path.basename(p)
        env = UnityPy.load(fp)
        ab_objs = [o for o in env.objects if o.type.name == "AssetBundle"]
        if not ab_objs:
            continue
        ab = ab_objs[0].read()
        if ab.m_AssetBundleName != target_fn:
            repack_bundle(fp, fp, target_fn)
            new_crc = compute_crc(fp)
            v = meta.get("v")
            manifest["paths"][p]["crc"] = new_crc
            updated_crcs[p] = (v, new_crc)
            repacked += 1

    # Save manifest with updated CRCs
    save_manifest(manifest)
    sync_custom_manifests(updated_crcs)
    print(f"\nSuccessfully applied {applied} new substitutions, repacked {repacked} existing substitutions, and updated manifest CRCs!")


if __name__ == "__main__":
    main()
