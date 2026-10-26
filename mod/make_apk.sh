#!/bin/bash
# Builds the modded APK from an original VK APK.
#   make_apk.sh path/to/vk.apk [out.apk]
# Needs: java 17+, python3, apktool.jar, Android build-tools (zipalign, apksigner, d8), keytool.
set -euo pipefail
export JAVA_TOOL_OPTIONS=
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$(realpath "$1")"
WORK="${VKMOD_WORK:-/home/user/work}"
OUT_APK="${2:-$WORK/vkmod-signed.apk}"
SDK="${ANDROID_HOME:-$WORK/sdk}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
APKTOOL="${APKTOOL:-$WORK/tools/apktool.jar}"
DEC="$WORK/decoded"          # pristine decode (cached between runs)
PAT="$WORK/patched"          # patched copy
KS="${VKMOD_KEYSTORE:-$WORK/vkmod-test.jks}"
KSPASS="${VKMOD_KSPASS:-vkmodtest}"

echo "== 1/7 decode ($SRC)"
if [ ! -f "$DEC/.src-sha" ] || [ "$(cat "$DEC/.src-sha")" != "$(sha256sum "$SRC" | cut -d' ' -f1)" ]; then
  rm -rf "$DEC"; java -Xmx8g -jar "$APKTOOL" d -f -o "$DEC" "$SRC" >/dev/null
  sha256sum "$SRC" | cut -d' ' -f1 > "$DEC/.src-sha"
fi
echo "== 2/7 core: test + dex"
"$HERE/build.sh" all
echo "== 3/7 patch"
rm -rf "$PAT"; cp -r "$DEC" "$PAT"; rm -f "$PAT/.src-sha"
python3 "$HERE/patcher/patch.py" "$PAT" --report "$WORK/patch_report.json"
echo "== 4/7 apktool build"
java -Xmx8g -jar "$APKTOOL" b -o "$WORK/unsigned.apk" "$PAT" >/dev/null
echo "== 5/7 add core dex"
python3 "$HERE/patcher/adddex.py" "$WORK/unsigned.apk" "${VKMOD_OUT:-$WORK/build}/core.dex"
echo "== 6/7 zipalign + sign"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass "$KSPASS" -keypass "$KSPASS" -alias vkmod -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=VK Mod Test, O=VK Mod" >/dev/null 2>&1
fi
rm -f "$WORK/aligned.apk"
"$BT/zipalign" -p -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KSPASS" --key-pass "pass:$KSPASS" --ks-key-alias vkmod \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "$OUT_APK" "$WORK/aligned.apk"
echo "== 7/7 verify"
"$BT/apksigner" verify --verbose "$OUT_APK" | head -6
"$BT/zipalign" -c -p 4 "$OUT_APK" && echo "zipalign: ok"
ls -la "$OUT_APK"
