#!/usr/bin/env python3
"""Appends core.dex to an APK as the next classesN.dex without touching the other entries."""
import re
import sys
import zipfile

apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk) as z:
    nums = [int(m.group(1) or 1) for n in z.namelist() if (m := re.fullmatch(r"classes(\d*)\.dex", n))]
nxt = max(nums) + 1
name = "classes%d.dex" % nxt
with zipfile.ZipFile(apk, "a", compression=zipfile.ZIP_DEFLATED) as z:
    z.write(dex, name)
print("added %s (existing: %d dex files)" % (name, len(nums)))
