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

import json
import os
import re
import shutil
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SWC_FILES = os.path.join(ROOT, "swcFiles")
MANIFEST_PATH = os.path.join(SWC_FILES, "manifests", "__manifest_zyngaswc_prod.02045.json")


def load_manifest():
    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def save_manifest(manifest):
    with open(MANIFEST_PATH, "w", encoding="utf-8") as f:
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

        # 6. Faction counterpart for same model (_emp <-> _rbl)
        for f1, f2 in [("_emp", "_rbl"), ("_rbl", "_emp"), ("-emp", "-rbl"), ("-rbl", "-emp")]:
            if f1 in fn:
                opp_p = f"assetbundles/android/{fn.replace(f1, f2)}"
                if opp_p in present:
                    subs.append((p, opp_p, f"Faction counterpart: {f2}"))
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

    return subs


def main():
    print("=== Star Wars Commander Asset Bundle Fallback Generator ===")
    manifest = load_manifest()
    present, missing = find_present_and_missing(manifest)
    print(f"Total Android bundles: {len(present) + len(missing)}")
    print(f"Present: {len(present)}, Missing: {len(missing)}")

    subs = resolve_substitutions(present, missing)
    print(f"\nFound {len(subs)} valid substitutions.")

    applied = 0
    for target_p, src_p, reason in subs:
        meta = manifest["paths"][target_p]
        v = meta.get("v")

        # Destination path
        dest_dir = os.path.join(SWC_FILES, str(v), os.path.dirname(target_p))
        dest_path = os.path.join(SWC_FILES, str(v), target_p)
        os.makedirs(dest_dir, exist_ok=True)

        # Source path
        src_path = present[src_p][0]

        # Copy or hardlink
        if not os.path.exists(dest_path):
            try:
                os.link(src_path, dest_path)
            except OSError:
                shutil.copyfile(src_path, dest_path)

        # Calculate CRC32 of target
        new_crc = compute_crc(dest_path)
        manifest["paths"][target_p]["crc"] = new_crc
        applied += 1
        print(f"  [SUB] {os.path.basename(target_p)}  <--  {os.path.basename(src_p)} (CRC: {new_crc}, {reason})")

    # Save manifest with updated CRCs
    save_manifest(manifest)
    print(f"\nSuccessfully applied {applied} substitutions and updated manifest CRCs!")


if __name__ == "__main__":
    main()
