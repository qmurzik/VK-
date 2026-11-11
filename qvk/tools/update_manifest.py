#!/usr/bin/env python3
"""Publishing helper for the QVK 3 update channel (uses only the openssl CLI).

  keygen  <dir>                       create an EC P-256 key pair (keep private.pem secret!)
  make    <apk> <url> [<url>...]      print a signed manifest for the APK (mirrors = extra URLs)
          --key private.pem --notes "текст" [--merge old.json] [--channel alpha|stable]
  mark    <manifest.json> <version_code> stable|alpha --key private.pem   re-sign with a release moved to a channel

The public key printed by keygen goes to qvk/assets/qvk3_update_keys.json (or into
«Настройки QVK → Обновления QVK → Серверы обновлений»). Upload the manifest JSON to any HTTPS host(s)
and add its address(es) as update sources.
"""
import argparse, base64, datetime, hashlib, json, os, re, subprocess, sys, tempfile


def sh(*cmd, data=None):
    return subprocess.run(cmd, input=data, check=True, capture_output=True).stdout


def keygen(d):
    os.makedirs(d, exist_ok=True)
    priv, pub = os.path.join(d, "private.pem"), os.path.join(d, "public.der")
    sh("openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", priv)
    os.chmod(priv, 0o600)
    sh("openssl", "ec", "-in", priv, "-pubout", "-outform", "DER", "-out", pub)
    print("private key:", priv, "(never commit or share it)")
    print("public key (base64):", base64.b64encode(open(pub, "rb").read()).decode())


def badging(apk):
    out = sh("aapt", "dump", "badging", apk).decode().splitlines()[0]
    m = re.search(r"name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", out)
    return m.group(1), int(m.group(2)), m.group(3)


def signer(apk):
    out = sh("apksigner", "verify", "--print-certs", apk).decode()
    return re.search(r"Signer #1 certificate SHA-256 digest: (\w+)", out).group(1)


def make(a):
    pkg, code, name = badging(a.apk)
    if pkg != "ru.qmods.qvk":
        sys.exit("not a QVK APK: " + pkg)
    data = open(a.apk, "rb").read()
    rel = {"version_code": code, "version_name": name, "vk_base": "8.100", "min_sdk": 28,
           "apk_size": len(data), "sha256": hashlib.sha256(data).hexdigest(), "signer_sha256": signer(a.apk),
           "urls": a.urls, "notes": a.notes, "published": datetime.date.today().isoformat(), "channel": a.channel}
    releases = [rel]
    if a.merge:
        old = json.loads(json.load(open(a.merge))["payload"])
        releases += [r for r in old["releases"] if r["version_code"] != code]
    payload = json.dumps({"schema": 3, "package": "ru.qmods.qvk", "channel": "stable",
                          "releases": sorted(releases, key=lambda r: -r["version_code"])}, ensure_ascii=False)
    sig = sh("openssl", "dgst", "-sha256", "-sign", a.key, data=payload.encode())
    print(json.dumps({"payload": payload, "key_id": "main", "signature": base64.b64encode(sig).decode()}, ensure_ascii=False, indent=1))


def mark(a):
    old = json.load(open(a.manifest))
    payload = json.loads(old["payload"])
    hit = [r for r in payload["releases"] if r["version_code"] == a.code]
    if not hit:
        sys.exit("no release %d in the manifest" % a.code)
    hit[0]["channel"] = a.channel
    text = json.dumps(payload, ensure_ascii=False)
    sig = sh("openssl", "dgst", "-sha256", "-sign", a.key, data=text.encode())
    print(json.dumps({"payload": text, "key_id": old.get("key_id", "main"), "signature": base64.b64encode(sig).decode()}, ensure_ascii=False, indent=1))


p = argparse.ArgumentParser()
sub = p.add_subparsers(dest="cmd", required=True)
k = sub.add_parser("keygen"); k.add_argument("dir")
m = sub.add_parser("make"); m.add_argument("apk"); m.add_argument("urls", nargs="+")
m.add_argument("--key", required=True); m.add_argument("--notes", default=""); m.add_argument("--merge")
m.add_argument("--channel", choices=["alpha", "stable"], default="alpha")
k2 = sub.add_parser("mark"); k2.add_argument("manifest"); k2.add_argument("code", type=int); k2.add_argument("channel", choices=["alpha", "stable"])
k2.add_argument("--key", required=True)
a = p.parse_args()
{"keygen": lambda: keygen(a.dir), "make": lambda: make(a), "mark": lambda: mark(a)}[a.cmd]()
