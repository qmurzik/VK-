#!/usr/bin/env python3
"""Checks that every com/vkmod reference written into patched smali exists in the compiled core classes.

Usage: verify_refs.py PATCHED_DIR CLASSES_DIR
A missing target would crash VK with NoSuchMethodError at runtime, so the build fails instead.
"""
import glob
import os
import re
import subprocess
import sys

patched, classes = sys.argv[1], sys.argv[2]
refs = set()
pat = re.compile(r"invoke-\w+(?:/range)? \{[^}]*\}, L(com/vkmod/[^;]+);->([^(]+)(\([^)]*\)\S+)")
p = subprocess.run(["grep", "-rhoE", "--include=*.smali", r"L(com/vkmod/[^;]+);->[^ ]+", ] + glob.glob(os.path.join(patched, "smali*")), capture_output=True, text=True)
for line in p.stdout.splitlines():
    m = re.match(r"L(com/vkmod/[^;]+);->([^(]+)(\(.*)", line)
    if m:
        refs.add(m.groups())
bad = []
cache = {}
for cls, name, desc in sorted(refs):
    if cls not in cache:
        out = subprocess.run(["javap", "-s", "-p", "-cp", classes, cls.replace("/", ".")], capture_output=True, text=True).stdout
        cache[cls] = out
    out = cache[cls]
    ok = re.search(r"\b%s\(.*\n\s+descriptor: %s" % (re.escape(name), re.escape(desc)), out)
    print("%-4s %s.%s%s" % ("ok" if ok else "MISS", cls, name, desc))
    if not ok:
        bad.append((cls, name, desc))
sys.exit(1 if bad else 0)
