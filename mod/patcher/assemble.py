#!/usr/bin/env python3
"""Assembles the final unsigned APK.

apktool/aapt2 cannot reproduce VK's resource ids (about 1400 `$`-named drawables are renumbered), and smali refers to
resources by number, so the rebuilt resources are NOT used. The result is the ORIGINAL apk with only these entries replaced:
  - classes*.dex          -> from the apktool build of the patched smali, plus the mod core as the last dex
  - AndroidManifest.xml   -> from the apktool build (only after checking that every resource it references has the same
                             id and name in the original resources.arsc)
  - META-INF signatures   -> dropped (the result is signed again)

Usage: assemble.py ORIGINAL.apk REBUILT.apk CORE.dex OUT.apk AAPT2
"""
import re
import subprocess
import sys
import zipfile

orig_apk, rebuilt_apk, core_dex, out_apk, aapt2 = sys.argv[1:6]


def res_map(apk):
    out = subprocess.run([aapt2, "dump", "resources", apk], capture_output=True, text=True).stdout
    m = {}
    for line in out.splitlines():
        mm = re.match(r"\s*resource (0x7f[0-9a-f]+) (\S+)", line)
        if mm:
            m[mm.group(1)] = mm.group(2)
    return m


def manifest_ids(apk):
    out = subprocess.run([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk], capture_output=True, text=True).stdout
    return set(re.findall(r"=@(0x7f[0-9a-f]{6})", out))


orig_res = res_map(orig_apk)
rebuilt_res = res_map(rebuilt_apk)
ids = manifest_ids(rebuilt_apk)
bad = [(i, rebuilt_res.get(i), orig_res.get(i)) for i in sorted(ids) if rebuilt_res.get(i) != orig_res.get(i)]
if bad:
    print("FAIL: the rebuilt manifest references resources whose id/name differ from the original arsc:")
    for b in bad[:20]:
        print("   %s rebuilt=%s original=%s" % b)
    sys.exit(1)
print("manifest resource references: %d, all match the original resources.arsc" % len(ids))

SIG = re.compile(r"META-INF/([^/]+\.(SF|RSA|DSA|EC)|MANIFEST\.MF)$")
DEX = re.compile(r"classes(\d*)\.dex$")

with zipfile.ZipFile(orig_apk) as zo, zipfile.ZipFile(rebuilt_apk) as zr, zipfile.ZipFile(out_apk, "w") as zout:
    kept = 0
    for info in zo.infolist():
        n = info.filename
        if SIG.match(n) or DEX.match(n) or n == "AndroidManifest.xml":
            continue
        zi = zipfile.ZipInfo(n, info.date_time)
        zi.compress_type = info.compress_type
        zi.external_attr = info.external_attr
        zout.writestr(zi, zo.read(info), compresslevel=6 if info.compress_type == zipfile.ZIP_DEFLATED else None)
        kept += 1
    nums = []
    for n in zr.namelist():
        m = DEX.match(n)
        if m:
            nums.append(int(m.group(1) or 1))
            zi = zipfile.ZipInfo(n, (2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zout.writestr(zi, zr.read(n), compresslevel=6)
    core_name = "classes%d.dex" % (max(nums) + 1)
    zi = zipfile.ZipInfo(core_name, (2026, 1, 1, 0, 0, 0))
    zi.compress_type = zipfile.ZIP_DEFLATED
    with open(core_dex, "rb") as f:
        zout.writestr(zi, f.read(), compresslevel=6)
    zi = zipfile.ZipInfo("AndroidManifest.xml", (2026, 1, 1, 0, 0, 0))
    zi.compress_type = zipfile.ZIP_DEFLATED
    zout.writestr(zi, zr.read("AndroidManifest.xml"), compresslevel=6)
print("assembled: %d original entries kept, %d dex from apktool + %s, manifest replaced" % (kept, len(nums), core_name))
