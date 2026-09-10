#!/usr/bin/env python3
"""Point a Star Wars Commander client at a private server.

The game's addresses live in IL2CPP's global-metadata.dat, in two different
places, and both matter:

* **String literals.** These are the `"https://..."` constants the code
  actually uses at runtime. Each has an entry in a table of
  (length, dataIndex) pairs, so the length is edited alongside the bytes.
* **Field and parameter default values.** These are C# field initialisers.
  Patching only these looks like it worked and changes nothing, because the
  running code reads the literals. Both are rewritten to keep them consistent.

Replacements are always shorter than the originals, so every string is
rewritten where it already sits. Nothing moves, which matters because both
regions are addressed by fixed offsets recorded elsewhere in the file.

Usage:
    python3 patch_metadata.py global-metadata.dat 192.168.1.10:8080
"""

from __future__ import annotations

import re
import shutil
import struct
import sys
from pathlib import Path

SANITY = 0xFAB11BAF
SUPPORTED_VERSION = 24

# Header index of each (offset, size) pair we care about, for metadata v24.
STRING_LITERAL_TABLE = 0
STRING_LITERAL_DATA = 1
DEFAULT_VALUE_DATA = 8

API_HOST = "swc-app-prod.apps.starwarscommander.com"
CDN_HOST = "zynga-swc-prod-1-seed.akamaized.net"

# Third-party services that no longer matter but still stall the client. Their
# certificates fail to validate, and each attempt blocks for the length of a
# TLS handshake before giving up, over and over. Pointing them at a closed
# local port turns a slow failure into an instant one. The address is also
# short, which matters because a replacement can never be longer than the
# string it overwrites.
DEAD_END = "http://127.0.0.1:1"
# Some of these hosts also appear as a bare name, with no scheme, because an
# SDK builds its own URL from them. Those need a replacement that is a host
# too, and short enough to fit.
DEAD_HOST = "127.0.0.1"
ABANDONED_HOSTS = (
    "api.swrve.com",
    "content.swrve.com",
    "notify.bugsnag.com",
    "sessions.bugsnag.com",
)


def rewrite(url: str, host: str) -> str:
    """Rebuild one original address against the private server.

    The API keeps its path. The CDN gains a "swcFiles" prefix, which is how the
    server recognises a request for the manifest or an asset bundle.
    """
    if API_HOST in url:
        tail = url.split(API_HOST, 1)[1]
        return f"http://{host}{tail}"
    if CDN_HOST in url:
        tail = url.split(CDN_HOST, 1)[1]
        return f"http://{host}/swcFiles{tail}"
    if any(dead in url for dead in ABANDONED_HOSTS):
        return DEAD_END
    raise ValueError(f"No rule for {url}")


def section(data: bytes, index: int) -> tuple[int, int]:
    return struct.unpack_from("<II", data, 8 + index * 8)


def check_header(data: bytes) -> None:
    sanity, version = struct.unpack_from("<Ii", data, 0)
    if sanity != SANITY:
        raise SystemExit(f"Not a global-metadata.dat: sanity is 0x{sanity:08X}")
    if version != SUPPORTED_VERSION:
        raise SystemExit(f"Expected metadata version {SUPPORTED_VERSION}, found {version}")


def write_string(data: bytearray, start: int, capacity: int, new: bytes) -> None:
    if len(new) > capacity:
        raise SystemExit(
            f"Replacement needs {len(new)} bytes but only {capacity} are available; "
            "use a shorter address"
        )
    data[start : start + len(new)] = new
    # Blank the tail so no stale suffix can be read as content.
    data[start + len(new) : start + capacity] = b"\x00" * (capacity - len(new))


def patch_literals(data: bytearray, host: str) -> int:
    table_off, table_size = section(data, STRING_LITERAL_TABLE)
    data_off, data_size = section(data, STRING_LITERAL_DATA)
    patched = 0

    for i in range(table_size // 8):
        entry = table_off + i * 8
        length, index = struct.unpack_from("<Ii", data, entry)
        if length == 0:
            continue
        start = data_off + index
        value = bytes(data[start : start + length])
        if not any(h.encode() in value for h in (API_HOST, CDN_HOST, *ABANDONED_HOSTS)):
            continue

        text = value.rstrip(b"\x00").decode("ascii", "replace")
        new = rewrite(text, host).encode()
        if len(new) > length and text.startswith(("http://", "https://")) is False:
            new = DEAD_HOST.encode()
        if len(new) > length:
            print(f"  literal #{i}: {text}\n      -> skipped, needs {len(new)} bytes of {length}")
            continue
        write_string(data, start, length, new)
        struct.pack_into("<I", data, entry, len(new))
        print(f"  literal #{i}: {text}\n      -> {new.decode()}")
        patched += 1

    return patched


def patch_defaults(data: bytearray, host: str) -> int:
    off, size = section(data, DEFAULT_VALUE_DATA)
    blob = bytes(data[off : off + size])
    pattern = re.compile(
        rb"https?://[A-Za-z0-9._~:/?#@!$&'()*+,;=%-]{6,120}"
    )
    patched = 0

    for match in pattern.finditer(blob):
        start, end = match.start(), match.end()
        if start < 4:
            continue
        declared = struct.unpack_from("<i", blob, start - 4)[0]
        # Only touch a match that is a whole entry, not a fragment.
        if declared not in (end - start, end - start + 1):
            continue
        text = match.group().decode()
        if not any(h in text for h in (API_HOST, CDN_HOST, *ABANDONED_HOSTS)):
            continue

        new = rewrite(text, host).encode()
        if len(new) > declared:
            # Field defaults are belt and braces; the literals are what the
            # running code reads. One that cannot be rewritten in place is
            # worth a warning, not a failed patch.
            print(f"  default: {text}\n      -> skipped, needs {len(new)} bytes of {declared}")
            continue
        write_string(data, off + start, declared, new)
        struct.pack_into("<i", data, off + start - 4, len(new))
        print(f"  default: {text}\n      -> {new.decode()}")
        patched += 1

    return patched


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    path, host = Path(sys.argv[1]), sys.argv[2]
    if not path.is_file():
        raise SystemExit(f"No such file: {path}")

    data = bytearray(path.read_bytes())
    check_header(data)
    print(f"Patching {path.name} to use {host}")

    total = patch_literals(data, host) + patch_defaults(data, host)
    if total:
        backup = path.with_suffix(path.suffix + ".orig")
        if not backup.exists():
            shutil.copy2(path, backup)
            print(f"  original kept at {backup.name}")
        path.write_bytes(bytes(data))
    print(f"Done, {total} address(es) rewritten")


if __name__ == "__main__":
    main()
