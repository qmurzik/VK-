#!/usr/bin/env python3
"""Applies the VK Mod hooks to an apktool-decoded VK tree.

Every hook point is located by *signature* (structure of the code, strings, framework calls), never by the
obfuscated class name, so the script keeps working on new VK versions. Points that cannot be found are
reported and skipped: the matching features then simply do nothing.

Usage: patch.py DECODED_DIR [--report report.json]
"""
import argparse
import glob
import json
import os
import re
import subprocess
import sys

HOOKS = "Lcom/vkmod/Hooks;"


class NotFound(Exception):
    pass


class Tree:
    def __init__(self, root):
        self.root = root
        self.smali_dirs = sorted(
            [d for d in glob.glob(os.path.join(root, "smali*")) if os.path.isdir(d)],
            key=lambda d: (len(os.path.basename(d)), os.path.basename(d)),
        )
        self.manifest_path = os.path.join(root, "AndroidManifest.xml")

    def grep_files(self, pattern, fixed=True, only_under=None):
        """Paths of smali files containing the pattern (uses grep: the tree has ~100k files)."""
        args = ["grep", "-rlZ", "--include=*.smali"] + (["-F"] if fixed else ["-E"]) + ["--", pattern]
        dirs = [os.path.join(d, only_under) if only_under else d for d in self.smali_dirs]
        dirs = [d for d in dirs if os.path.isdir(d)]
        if not dirs:
            return []
        p = subprocess.run(args + dirs, capture_output=True)
        return [x.decode() for x in p.stdout.split(b"\0") if x]

    def find_class_file(self, internal_name):
        for d in self.smali_dirs:
            p = os.path.join(d, internal_name + ".smali")
            if os.path.exists(p):
                return p
        return None


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with open(p, "w", encoding="utf-8") as f:
        f.write(s)


METHOD_RE = re.compile(r"^\.method (?P<mods>[^\n]*?)(?P<name>[^\s(]+)\((?P<args>[^)]*)\)(?P<ret>\S+)\n(?P<body>.*?)^\.end method\n",
                       re.S | re.M)


def methods(src):
    return list(METHOD_RE.finditer(src))


def class_name(src):
    m = re.search(r"^\.class [^\n]*?(L[^;\s]+;)\s*$", src, re.M)
    return m.group(1) if m else None


def wrap_method(src, m, hook_body_fn):
    """Renames method m to NAME$vkmod and appends a wrapper with the same signature built by hook_body_fn."""
    cls = class_name(src)
    name = m.group("name")
    mods = m.group("mods")
    args, ret = m.group("args"), m.group("ret")
    orig_text = m.group(0)
    renamed = orig_text.replace(".method %s%s(" % (mods, name), ".method %s%s$vkmod(" % (mods, name), 1)
    wrapper = ".method %s%s(%s)%s\n%s.end method\n" % (mods, name, args, ret, hook_body_fn(cls, name + "$vkmod", mods, args, ret))
    return src.replace(orig_text, renamed + "\n" + wrapper, 1)


# ----------------------------------------------------------------------------------------------------------------
# Patches
# ----------------------------------------------------------------------------------------------------------------

def patch_app_attach(t):
    """VKApplication.attachBaseContext(Context): call Hooks.attach(this) right after super."""
    man = read(t.manifest_path)
    m = re.search(r"<application\b[^>]*?android:name=\"([^\"]+)\"", man)
    if not m:
        raise NotFound("android:name of <application> not found in manifest")
    cname = m.group(1)
    path = t.find_class_file(cname.replace(".", "/"))
    if not path:
        raise NotFound("Application class %s not found in smali" % cname)
    src = read(path)
    if "Lcom/vkmod/Hooks;->attach" in src:
        return "already patched: %s" % cname
    for mm in methods(src):
        if mm.group("name") == "attachBaseContext" and mm.group("args") == "Landroid/content/Context;":
            body = mm.group("body")
            sm = re.search(r"^(\s*invoke-super \{p0, p1\}, L[^;]+;->attachBaseContext\(Landroid/content/Context;\)V)\n", body, re.M)
            if not sm:
                raise NotFound("super.attachBaseContext call not found in %s" % cname)
            new_body = body.replace(sm.group(0), sm.group(0) + "\n    invoke-static {p0}, %s->attach(Landroid/app/Application;)V\n" % HOOKS, 1)
            write(path, src.replace(mm.group(0), mm.group(0).replace(body, new_body, 1), 1))
            return "%s.attachBaseContext" % cname
    # not overridden: add the override
    sup = re.search(r"^\.super (L[^;]+;)", src, re.M).group(1)
    add = (".method protected attachBaseContext(Landroid/content/Context;)V\n    .locals 0\n"
           "    invoke-super {p0, p1}, %s->attachBaseContext(Landroid/content/Context;)V\n"
           "    invoke-static {p0}, %s->attach(Landroid/app/Application;)V\n    return-void\n.end method\n" % (sup, HOOKS))
    write(path, src.rstrip("\n") + "\n\n" + add)
    return "%s.attachBaseContext (added override)" % cname


def _is_color_resolver(body):
    """static (ILandroid/content/Context;)I: resolves a theme attribute and returns TypedValue.data unchanged."""
    if "Landroid/content/res/Resources$Theme;->resolveAttribute(ILandroid/util/TypedValue;Z)Z" not in body:
        return False
    if not re.search(r"iget p\d+, p\d+, Landroid/util/TypedValue;->data:I", body):
        return False
    invokes = re.findall(r"^\s*invoke-\w+ [^\n]*?, ([^\n]+)$", body, re.M)
    if len(invokes) > 5:
        return False
    for call in invokes:
        ok = ("Landroid/content/Context;->getTheme()" in call or "Resources$Theme;->resolveAttribute" in call
              or re.search(r"->\w+\(\)Landroid/util/TypedValue;", call))
        if not ok:
            return False
    return True


def patch_color_resolver(t):
    """The VK helper that turns a colour attribute into a colour: wrap it so the theme engine can substitute."""
    cands = t.grep_files("Landroid/content/res/Resources$Theme;->resolveAttribute(ILandroid/util/TypedValue;Z)Z")
    done = []
    for path in cands:
        src = read(path)
        for m in methods(src):
            if m.group("args") != "ILandroid/content/Context;" or m.group("ret") != "I" or "static" not in m.group("mods"):
                continue
            if m.group("name").endswith("$vkmod") or not _is_color_resolver(m.group("body")):
                continue

            def body(cls, orig, mods, args, ret, _n=m.group("name")):
                return ("    .locals 1\n"
                        "    invoke-static {p0, p1}, %s->%s(ILandroid/content/Context;)I\n    move-result v0\n"
                        "    invoke-static {p0, p1, v0}, %s->color(ILandroid/content/Context;I)I\n    move-result v0\n    return v0\n" % (cls, orig, HOOKS))

            src = wrap_method(src, m, body)
            write(path, src)
            done.append("%s.%s" % (class_name(src), m.group("name")))
            src = read(path)
    if not done:
        raise NotFound("no (ILandroid/content/Context;)I helper resolving a theme attribute to TypedValue.data")
    return ", ".join(done)


def patch_okhttp(t):
    """OkHttpClient(Builder): route the application-interceptor list through Hooks.okInterceptors."""
    files = [p for p in t.grep_files('"Null interceptor: "', only_under="okhttp3") if "/okhttp3/" in p.replace("\\", "/")]
    if not files:
        raise NotFound('class with "Null interceptor: " not found under okhttp3/')
    patched = []
    for path in files:
        src = read(path)
        cls = class_name(src)
        if "->okInterceptors(" in src:
            patched.append(cls + " (already)")
            continue
        for m in methods(src):
            if m.group("name") != "<init>" or not re.fullmatch(r"L%s\$\w+;" % re.escape(cls[1:-1]), m.group("args")):
                continue
            body = m.group("body")
            im = re.search(r"^(\s*)iput-object (v\d+), p0, %s->(\w+):Ljava/util/List;\n" % re.escape(cls), body, re.M)
            if not im:
                raise NotFound("iput-object of an interceptor List not found in %s.<init>(Builder)" % cls)
            reg = im.group(2)
            ins = ("%sinvoke-static {%s}, %s->okInterceptors(Ljava/util/List;)Ljava/util/List;\n"
                   "%smove-result-object %s\n\n") % (im.group(1), reg, HOOKS, im.group(1), reg)
            new_body = body.replace(im.group(0), ins + im.group(0), 1)
            write(path, src.replace(m.group(0), m.group(0).replace(body, new_body, 1), 1))
            patched.append("%s.%s" % (cls, im.group(3)))
            break
        else:
            raise NotFound("OkHttpClient(Builder) constructor not found in %s" % cls)
    return ", ".join(patched)


def patch_inflater(t):
    """VK's LayoutInflater subclass: post-process every inflate(int, ViewGroup, boolean) result."""
    files = t.grep_files(".super Landroid/view/LayoutInflater;")
    done = []
    for path in files:
        src = read(path)
        if "inflate$vkmod" in src:
            done.append(class_name(src) + " (already)")
            continue
        for m in methods(src):
            if m.group("name") == "inflate" and m.group("args") == "ILandroid/view/ViewGroup;Z" and m.group("ret") == "Landroid/view/View;":
                def body(cls, orig, mods, args, ret):
                    return ("    .locals 1\n"
                            "    invoke-virtual {p0, p1, p2, p3}, %s->%s(ILandroid/view/ViewGroup;Z)Landroid/view/View;\n    move-result-object v0\n"
                            "    invoke-static {v0, p1, p2, p3}, %s->inflated(Landroid/view/View;ILandroid/view/ViewGroup;Z)Landroid/view/View;\n"
                            "    move-result-object v0\n    return-object v0\n" % (cls, orig, HOOKS))
                src = wrap_method(src, m, body)
                write(path, src)
                done.append(class_name(src))
                break
    if not done:
        raise NotFound("no LayoutInflater subclass overriding inflate(int, ViewGroup, boolean)")
    return ", ".join(done)


def res_id(t, rtype, name):
    pub = os.path.join(t.root, "res", "values", "public.xml")
    m = re.search(r'<public type="%s" name="%s" id="(0x[0-9a-f]+)"' % (rtype, re.escape(name)), read(pub))
    return m.group(1) if m else None


def patch_exact_date(t):
    """TimeUtils.h(int ts, Resources, boolean withTime): the shared relative-date formatter."""
    rid = res_id(t, "string", "vk_date_ago_now")
    if not rid:
        raise NotFound("resource string/vk_date_ago_now not found in public.xml")
    files = t.grep_files(rid)
    done = []
    for path in files:
        src = read(path)
        for m in methods(src):
            if (m.group("args") == "ILandroid/content/res/Resources;Z" and m.group("ret") == "Ljava/lang/String;"
                    and "static" in m.group("mods") and rid in m.group("body") and not m.group("name").endswith("$vkmod")):
                def body(cls, orig, mods, args, ret):
                    return ("    .locals 1\n"
                            "    invoke-static {p0, p1, p2}, %s->%s(ILandroid/content/res/Resources;Z)Ljava/lang/String;\n    move-result-object v0\n"
                            "    invoke-static {p0, p1, p2, v0}, %s->exactDate(ILandroid/content/res/Resources;ZLjava/lang/String;)Ljava/lang/String;\n"
                            "    move-result-object v0\n    return-object v0\n" % (cls, orig, HOOKS))
                src = wrap_method(src, m, body)
                write(path, src)
                done.append("%s.%s" % (class_name(src), m.group("name")))
                break
    if not done:
        raise NotFound("no static (ILandroid/content/res/Resources;Z)Ljava/lang/String; formatter using vk_date_ago_now")
    return ", ".join(done)


MOD_ACTIVITIES = """
        <activity android:exported="true" android:icon="@mipmap/mp_launcher_constellation" android:label="VK Mod" android:name="com.vkmod.ui.SettingsActivity" android:taskAffinity="com.vkmod.settings" android:theme="@android:style/Theme.Material.NoActionBar" android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
        <activity android:exported="false" android:name="com.vkmod.ui.ThemeEditorActivity" android:taskAffinity="com.vkmod.settings" android:theme="@android:style/Theme.Material.NoActionBar"/>
        <activity android:exported="false" android:name="com.vkmod.ui.MediaLogActivity" android:taskAffinity="com.vkmod.settings" android:theme="@android:style/Theme.Material.NoActionBar"/>
        <activity android:exported="false" android:name="com.vkmod.ui.LogActivity" android:taskAffinity="com.vkmod.settings" android:theme="@android:style/Theme.Material.NoActionBar"/>
        <activity android:excludeFromRecents="true" android:exported="false" android:launchMode="singleTop" android:name="com.vkmod.feat.LockActivity" android:theme="@android:style/Theme.Material.NoActionBar"/>
"""


def patch_manifest(t):
    man = read(t.manifest_path)
    if "com.vkmod.ui.SettingsActivity" in man:
        return "already patched"
    if "</application>" not in man:
        raise NotFound("</application> not found in manifest")
    man = man.replace("</application>", MOD_ACTIVITIES + "    </application>", 1)
    if "android.permission.USE_BIOMETRIC" not in man:
        man = re.sub(r"(<uses-permission )", '<uses-permission android:name="android.permission.USE_BIOMETRIC"/>\n    \\1', man, count=1)
    if "android.permission.REQUEST_INSTALL_PACKAGES" in man and False:
        pass
    write(t.manifest_path, man)
    return "activities + USE_BIOMETRIC"


def patch_split_dex(t):
    """The primary dex of VK is at the 64K method limit; hooks add a few ids, so move library packages to a new dex."""
    import shutil
    primary = os.path.join(t.root, "smali")
    nums = [int(re.sub(r"\D", "", os.path.basename(d)) or 1) for d in t.smali_dirs]
    new_dir = os.path.join(t.root, "smali_classes%d" % (max(nums) + 1))
    if os.path.isdir(os.path.join(primary, "androidx")) is False and not os.path.isdir(os.path.join(primary, "com", "google")):
        return "nothing to move"
    moved = []
    for rel in ("androidx", os.path.join("com", "google")):
        src = os.path.join(primary, rel)
        if os.path.isdir(src):
            dst = os.path.join(new_dir, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.move(src, dst)
            moved.append(rel)
    t.smali_dirs = sorted([d for d in glob.glob(os.path.join(t.root, "smali*")) if os.path.isdir(d)],
                          key=lambda d: (len(os.path.basename(d)), os.path.basename(d)))
    return "moved %s to %s" % (", ".join(moved), os.path.basename(new_dir))


PATCHES = [
    ("split_dex", "Освободить место в основном dex", ["*"], patch_split_dex),
    ("app_attach", "Запуск ядра в VKApplication", ["*"], patch_app_attach),
    ("color_resolver", "Резолвер цветов темы", ["theme_engine", "theme_amoled", "material_you", "theme_preset", "bars_alpha", "chat_bg", "bubble_style"], patch_color_resolver),
    ("okhttp", "Перехватчик OkHttp", ["ads_feed", "promo_communities", "ads_stories", "ads_clips", "banners", "feed_reco", "friends_suggest",
                                      "hide_stories", "hide_reposts", "keywords", "custom_blocks", "no_read", "no_typing", "offline",
                                      "block_trackers", "dl_photo", "dl_video", "dl_clips", "dl_stories", "dl_voice"], patch_okhttp),
    ("inflater", "Пост-обработка XML-вёрстки", ["hide_view_ids", "no_haptics", "app_font", "select_text", "avatar_shape"], patch_inflater),
    ("exact_date", "Форматтер относительных дат", ["exact_dates"], patch_exact_date),
    ("manifest", "Манифест: экраны мода", ["*"], patch_manifest),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("decoded")
    ap.add_argument("--report", default=None)
    args = ap.parse_args()
    t = Tree(args.decoded)
    report = []
    ok = True
    for pid, title, feats, fn in PATCHES:
        try:
            detail = fn(t)
            report.append({"id": pid, "title": title, "status": "applied", "detail": detail, "features": feats})
            print("[ OK ] %-15s %s -> %s" % (pid, title, detail))
        except NotFound as e:
            ok = False
            report.append({"id": pid, "title": title, "status": "NOT FOUND", "detail": str(e), "features": feats})
            print("[MISS] %-15s %s -> %s" % (pid, title, e))
    if args.report:
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
    print("\nAll hook points found." if ok else "\nSome hook points were NOT found: the related features are inactive.")
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
