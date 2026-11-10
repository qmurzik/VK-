#!/usr/bin/env python3
"""Воспроизводимая сборка QVK 3.0 поверх QVK 2.6.18 (VK 8.100).

Шаги:
  1. fetch     — скачать базовый APK и сверить SHA-256 (qvk/base.json)
  2. decode    — apktool d (кэшируется по SHA базы)
  3. classpath — dex2jar базового APK, чтобы новый Java-код компилировался против реальных классов
  4. compile   — javac (src/main/java) → d8 → classesN.dex
  5. patch     — smali-патчи (patches/*.patch), манифест (manifest/additions.xml), версия (release.json)
  6. build     — apktool b, добавление нового dex, zipalign
  7. sign      — подпись тем же ключом, что и у базы (публичный AOSP testkey), иначе обновление не встанет поверх
  8. verify    — apksigner verify, сверка сертификата/пакета/версии, LinkCheck (ссылки smali-патчей и нового кода)

Использование:
  python3 qvk/build.py            # полная сборка → qvk/out/QVK-<version>.apk
  python3 qvk/build.py test       # только юнит-тесты (JVM)
"""
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
CACHE = os.environ.get("QVK_CACHE", os.path.join(ROOT, ".cache"))
OUT = os.path.join(ROOT, "out")
WORK = os.path.join(CACHE, "work")

TOOLS = {
    "apktool.jar": ("https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar",
                    "c0350abbab5314248dfe2ee0c907def4edd14f6faef1f5d372d3d4abd28f0431"),
    "dex-tools.zip": ("https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip", None),
    "junit.jar": ("https://maven-central.storage-download.googleapis.com/maven2/junit/junit/4.13.2/junit-4.13.2.jar",
                  "8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3"),
    "hamcrest.jar": ("https://maven-central.storage-download.googleapis.com/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar",
                     "66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9"),
    "json.jar": ("https://maven-central.storage-download.googleapis.com/maven2/org/json/json/20240303/json-20240303.jar",
                 "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"),
    # full dexlib2 for LinkCheck (apktool bundles a stripped copy)
    "dexlib2.jar": ("https://dl.google.com/android/maven2/com/android/tools/smali/smali-dexlib2/3.0.9/smali-dexlib2-3.0.9.jar",
                    "8b547506f62f91d74b70f4beb4989b99e1ff61fc50b2525e9796ca35f0a351dc"),
    "guava.jar": ("https://maven-central.storage-download.googleapis.com/maven2/com/google/guava/guava/31.1-android/guava-31.1-android.jar",
                  "32ac2ed709d96d278b5d2e3e5cea178fa4939939c525fb647532f013308db309"),
}
# Публичный тестовый ключ AOSP. Базовый QVK подписан именно им (см. base.json → signerSha256),
# поэтому и обновление должно быть подписано им же, иначе Android откажет в установке поверх.
AOSP_KEY = "https://android.googlesource.com/platform/build/+/refs/heads/main/target/product/security/"
KEYS = {
    "testkey.pk8": "495675d32e89a149d5abe191f4e9c0e218b9068714e9b53a7c91e164a0741a23",
    "testkey.x509.pem": "a4384ba815b9499a5ce349b4e33c1755278873fe2eac150a068823f526e6dbde",
}
BUILD_TOOLS = "35.0.0"
PLATFORM = "android-34"


def log(msg):
    print(f"[qvk] {msg}", flush=True)


def run(cmd, **kw):
    log("$ " + " ".join(cmd if isinstance(cmd, list) else [cmd]))
    subprocess.run(cmd, check=True, **kw)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url, dest, expected=None, b64=False):
    if os.path.exists(dest) and (expected is None or sha256(dest) == expected):
        return dest
    log(f"download {url}")
    tmp = dest + ".part"
    req = urllib.request.Request(url, headers={"User-Agent": "qvk-build"})
    with urllib.request.urlopen(req, timeout=600) as r, open(tmp, "wb") as f:
        data = r.read()
        if b64:
            import base64
            data = base64.b64decode(data)
        f.write(data)
    if expected and sha256(tmp) != expected:
        os.remove(tmp)
        raise SystemExit(f"SHA-256 mismatch for {url}")
    os.replace(tmp, dest)
    return dest


def sdk():
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or os.path.join(CACHE, "sdk")
    bt = os.path.join(home, "build-tools", BUILD_TOOLS)
    jar = os.path.join(home, "platforms", PLATFORM, "android.jar")
    if not (os.path.isdir(bt) and os.path.isfile(jar)):
        mgr = os.path.join(home, "cmdline-tools", "latest", "bin", "sdkmanager")
        if not os.path.exists(mgr):
            z = download("https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip",
                         os.path.join(CACHE, "cmdline-tools.zip"))
            tmp = os.path.join(CACHE, "cmdtmp")
            shutil.rmtree(tmp, ignore_errors=True)
            zipfile.ZipFile(z).extractall(tmp)
            os.makedirs(os.path.join(home, "cmdline-tools"), exist_ok=True)
            shutil.move(os.path.join(tmp, "cmdline-tools"), os.path.join(home, "cmdline-tools", "latest"))
            os.chmod(mgr, 0o755)
        subprocess.run([mgr, f"--sdk_root={home}", f"build-tools;{BUILD_TOOLS}", f"platforms;{PLATFORM}"],
                       input=b"y\n" * 20, check=True, stdout=subprocess.DEVNULL)
    for f in os.listdir(bt):
        p = os.path.join(bt, f)
        if os.path.isfile(p):
            os.chmod(p, 0o755)
    return bt, jar


def tools():
    os.makedirs(CACHE, exist_ok=True)
    paths = {}
    for name, (url, digest) in TOOLS.items():
        paths[name] = download(url, os.path.join(CACHE, name), digest)
    d2j = os.path.join(CACHE, "dex-tools")
    if not os.path.isdir(d2j):
        zipfile.ZipFile(paths["dex-tools.zip"]).extractall(d2j)
    paths["d2j"] = next(os.path.join(dp, f) for dp, _, fs in os.walk(d2j) for f in fs if f == "d2j-dex2jar.sh")
    for dp, _, fs in os.walk(d2j):
        for f in fs:
            if f.endswith(".sh"):
                os.chmod(os.path.join(dp, f), 0o755)
    for name, digest in KEYS.items():
        paths[name] = download(AOSP_KEY + name + "?format=TEXT", os.path.join(CACHE, name), digest, b64=True)
    return paths


def base_apk():
    meta = json.load(open(os.path.join(ROOT, "base.json")))
    dest = os.path.join(CACHE, f"base-{meta['sha256'][:12]}.apk")
    local = os.environ.get("QVK_BASE_APK")
    if local:
        if sha256(local) != meta["sha256"]:
            raise SystemExit("QVK_BASE_APK: SHA-256 не совпадает с base.json")
        return local, meta
    for url in meta["urls"]:
        try:
            return download(url, dest, meta["sha256"]), meta
        except Exception as e:  # noqa: BLE001 — try the next mirror
            log(f"mirror failed: {e}")
    raise SystemExit("Базовый APK недоступен ни по одному адресу из base.json")


def decode(apk, meta, t):
    dec = os.path.join(CACHE, f"decoded-{meta['sha256'][:12]}")
    if not os.path.isfile(os.path.join(dec, "apktool.yml")):
        shutil.rmtree(dec, ignore_errors=True)
        run(["java", "-Xmx8g", "-jar", t["apktool.jar"], "d", "-f", "-o", dec, apk])
    return dec


def classpath(apk, meta, t):
    jar = os.path.join(CACHE, f"classpath-{meta['sha256'][:12]}.jar")
    if not os.path.exists(jar):
        env = dict(os.environ, JAVA_OPTS="-Xmx6g")
        run([t["d2j"], "-f", "--skip-exceptions", "-nc", "-o", jar, apk], env=env)
    return jar


def java_sources(d):
    return [os.path.join(dp, f) for dp, _, fs in os.walk(d) for f in fs if f.endswith(".java")]


def compile_mod(android_jar, cp, bt):
    out = os.path.join(CACHE, "mod-classes")
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out)
    srcs = java_sources(os.path.join(ROOT, "src", "main", "java"))
    # core-lambda-stubs provides java.lang.invoke.LambdaMetafactory for javac; d8 desugars lambdas.
    boot = android_jar + os.pathsep + os.path.join(bt, "core-lambda-stubs.jar")
    run(["javac", "-nowarn", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options",
         "-bootclasspath", boot, "-cp", cp, "-d", out] + srcs)
    classes = [os.path.join(dp, f) for dp, _, fs in os.walk(out) for f in fs if f.endswith(".class")]
    dexdir = os.path.join(CACHE, "mod-dex")
    shutil.rmtree(dexdir, ignore_errors=True)
    os.makedirs(dexdir)
    run([os.path.join(bt, "d8"), "--release", "--min-api", "28", "--lib", android_jar,
         "--classpath", cp, "--output", dexdir] + classes)
    return os.path.join(dexdir, "classes.dex"), [os.path.relpath(c, out) for c in classes]


def prepare_work(dec):
    shutil.rmtree(WORK, ignore_errors=True)
    # Hard links keep the 2+ GB decoded tree cheap to copy; patch(1) and our writers replace files
    # instead of editing them in place, so the cached original is never modified.
    run(["cp", "-al", dec, WORK])
    shutil.rmtree(os.path.join(WORK, "build"), ignore_errors=True)


def replace_file(path, text):
    tmp = path + ".qvktmp"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(text)
    os.replace(tmp, path)


def apply_patches():
    pdir = os.path.join(ROOT, "patches")
    touched = []
    for name in sorted(os.listdir(pdir)):
        if not name.endswith(".patch"):
            continue
        text = open(os.path.join(pdir, name), encoding="utf-8").read()
        touched += re.findall(r"^\+\+\+ b/(\S+)", text, re.M)
        run(["patch", "-p1", "--forward", "--batch", "--no-backup-if-mismatch", "-d", WORK,
             "-i", os.path.join(pdir, name)])
    return touched


def patch_manifest_and_version():
    release = json.load(open(os.path.join(ROOT, "release.json")))
    man = os.path.join(WORK, "AndroidManifest.xml")
    text = open(man, encoding="utf-8").read()
    add = open(os.path.join(ROOT, "manifest", "additions.xml"), encoding="utf-8").read()
    perms = re.findall(r"<uses-permission[^>]*/>", add)
    comps = re.sub(r"<uses-permission[^>]*/>", "", add)
    for p in perms:
        name = re.search(r'android:name="([^"]+)"', p).group(1)
        if f'android:name="{name}"' not in text:
            text = text.replace("<queries>", p + "\n    <queries>", 1)
    if "</application>" not in text:
        raise SystemExit("AndroidManifest.xml: нет </application>")
    text = text.replace("</application>", comps.strip() + "\n    </application>", 1)
    replace_file(man, text)
    yml = os.path.join(WORK, "apktool.yml")
    y = open(yml, encoding="utf-8").read()
    y = re.sub(r"versionCode: .*", f"versionCode: {release['versionCode']}", y)
    y = re.sub(r"versionName: .*", f"versionName: {release['versionName']}", y)
    replace_file(yml, y)
    return release


def add_assets():
    adir = os.path.join(ROOT, "assets")
    for dp, _, fs in os.walk(adir):
        for f in fs:
            src = os.path.join(dp, f)
            dst = os.path.join(WORK, "assets", os.path.relpath(src, adir))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            if os.path.exists(dst):
                os.remove(dst)
            shutil.copy(src, dst)


def build_apk(t, bt, mod_dex, release):
    unsigned = os.path.join(CACHE, "unsigned.apk")
    env = dict(os.environ, PATH=bt + os.pathsep + os.environ["PATH"])
    run(["java", "-Xmx8g", "-jar", t["apktool.jar"], "b", "-j", "4", "--use-aapt2", "-o", unsigned, WORK], env=env)
    with zipfile.ZipFile(unsigned) as z:
        n = max(int(m.group(1) or 1) for m in (re.match(r"classes(\d*)\.dex$", x) for x in z.namelist()) if m)
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as z:
        z.write(mod_dex, f"classes{n + 1}.dex")
    aligned = os.path.join(CACHE, "aligned.apk")
    run([os.path.join(bt, "zipalign"), "-f", "-p", "4", unsigned, aligned])
    os.makedirs(OUT, exist_ok=True)
    final = os.path.join(OUT, f"QVK-{release['versionName']}.apk")
    run([os.path.join(bt, "apksigner"), "sign", "--key", t["testkey.pk8"], "--cert", t["testkey.x509.pem"],
         "--v1-signing-enabled", "true", "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
         "--out", final, aligned])
    return final, n + 1


def verify(final, meta, release, t, bt, touched, mod_classes):
    out = subprocess.run([os.path.join(bt, "apksigner"), "verify", "--print-certs", final],
                         check=True, capture_output=True, text=True).stdout
    cert = re.search(r"Signer #1 certificate SHA-256 digest: (\w+)", out).group(1)
    if cert != meta["signerSha256"]:
        raise SystemExit(f"Подпись {cert} не совпадает с базой {meta['signerSha256']}: обновление не встанет поверх")
    badging = subprocess.run([os.path.join(bt, "aapt"), "dump", "badging", final],
                             check=True, capture_output=True, text=True).stdout.splitlines()[0]
    exp = f"package: name='{meta['package']}' versionCode='{release['versionCode']}' versionName='{release['versionName']}'"
    if not badging.startswith(exp):
        raise SystemExit("aapt badging: " + badging)
    if release["versionCode"] <= meta["versionCode"]:
        raise SystemExit("versionCode должен быть больше базового")
    log("signature, package and version: OK")
    lc_dir = os.path.join(CACHE, "linkcheck")
    os.makedirs(lc_dir, exist_ok=True)
    lc_cp = os.pathsep.join([t["dexlib2.jar"], t["guava.jar"]])
    run(["javac", "-nowarn", "-cp", lc_cp, "-d", lc_dir, os.path.join(ROOT, "tools", "LinkCheck.java")])
    scopes = sorted({"L" + c[:-len(".class")].split("$")[0] for c in mod_classes})
    for p in touched:
        m = re.match(r"smali(?:_classes\d+)?/(.+)\.smali$", p)
        if m:
            scopes.append("L" + m.group(1))
    run(["java", "-Xmx6g", "-cp", lc_cp + os.pathsep + lc_dir, "LinkCheck", final] + scopes)


def unit_tests(t, android_jar):
    out = os.path.join(CACHE, "test-classes")
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out)
    # Only Android-free logic is unit-tested on the JVM; such classes are listed in src/test/pure.txt.
    pure = [l.strip() for l in open(os.path.join(ROOT, "src", "test", "pure.txt")) if l.strip() and not l.startswith("#")]
    srcs = [os.path.join(ROOT, "src", "main", "java", p) for p in pure]
    srcs += java_sources(os.path.join(ROOT, "src", "test", "java"))
    cp = os.pathsep.join([t["junit.jar"], t["hamcrest.jar"], t["json.jar"]])
    run(["javac", "-nowarn", "-encoding", "UTF-8", "-cp", cp, "-d", out] + srcs)
    tests = sorted(os.path.relpath(s, os.path.join(ROOT, "src", "test", "java"))[:-5].replace(os.sep, ".")
                   for s in java_sources(os.path.join(ROOT, "src", "test", "java")) if s.endswith("Test.java"))
    props = []
    feats = os.environ.get("QVK_FEATURES_JSON")
    if feats and os.path.isfile(feats):
        props.append("-Dqvk.features=" + feats)
    run(["java"] + props + ["-cp", out + os.pathsep + cp, "org.junit.runner.JUnitCore"] + tests)


def main():
    t = tools()
    bt, android_jar = sdk()
    if len(sys.argv) > 1 and sys.argv[1] == "test":
        unit_tests(t, android_jar)
        return
    apk, meta = base_apk()
    dec = decode(apk, meta, t)
    os.environ.setdefault("QVK_FEATURES_JSON", os.path.join(dec, "assets", "qmods_features.json"))
    unit_tests(t, android_jar)
    cp = classpath(apk, meta, t)
    mod_dex, mod_classes = compile_mod(android_jar, cp, bt)
    prepare_work(dec)
    touched = apply_patches()
    release = patch_manifest_and_version()
    add_assets()
    final, dex_index = build_apk(t, bt, mod_dex, release)
    verify(final, meta, release, t, bt, touched, mod_classes)
    log(f"QVK 3.0 module dex: classes{dex_index}.dex")
    log(f"done: {final} ({os.path.getsize(final) // (1 << 20)} MB, sha256 {sha256(final)})")


if __name__ == "__main__":
    main()
