#!/usr/bin/env bash
# Stage-0 helper: scans a TikTok APK for the anchors TokSkip's hooks rely on.
#
#   tools/research.sh TikTok.apk [out-dir]
#
# Works without jadx (greps the dex string pools); if `jadx` is on PATH it also decompiles and
# lists the classes/methods that use each anchor. Nothing is uploaded anywhere.
set -euo pipefail
apk=${1:?usage: research.sh TikTok.apk [out-dir]}
out=${2:-research-out}
mkdir -p "$out/dex"
unzip -oq "$apk" 'classes*.dex' -d "$out/dex"
report="$out/report.txt"
: > "$report"

say() { echo "$*" | tee -a "$report"; }

say "# TokSkip research: $(basename "$apk")"
say "dex files: $(ls "$out"/dex/*.dex | wc -l)"

anchor() { # $1 = label, $2 = literal searched in dex string pools
    local hits
    hits=$(grep -l -a -F -- "$2" "$out"/dex/*.dex 2>/dev/null | xargs -r -n1 basename | tr '\n' ' ')
    say "$(printf '%-34s %-45s %s' "$1" "\"$2\"" "${hits:-—}")"
}

say ""
say "## Model / feed"
anchor "Aweme model (kept name)"     "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"
anchor "FeedItemList"                "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;"
anchor "getAid()"                    "getAid"
anchor "aweme_id json key"           "aweme_id"
anchor "caption info json key"       "cla_info"
anchor "caption list json key"       "caption_infos"
anchor "feed endpoint v1"            "/aweme/v1/feed/"
anchor "feed endpoint v2"            "/aweme/v2/feed/"
say ""
say "## Subtitles"
anchor "WebVTT parser literal"       "WEBVTT"
anchor "vtt mime/ext"                "text/vtt"
say ""
say "## Player"
anchor "TTVideoEngine (kept name)"   "Lcom/ss/ttvideoengine/TTVideoEngine;"
anchor "getCurrentPlaybackTime"      "getCurrentPlaybackTime"
anchor "seekTo"                      "seekTo"
anchor "setVideoID"                  "setVideoID"
anchor "setDirectUrlUseDataLoader"   "setDirectUrlUseDataLoader"

# Obfuscation estimate: share of short (≤2 chars) simple class names in the descriptor pool.
total=$(strings -a "$out"/dex/*.dex | grep -cE '^L[a-zA-Z0-9_/$]+;$' || true)
short=$(strings -a "$out"/dex/*.dex | grep -cE '^L([a-zA-Z0-9_]+/)*[a-zA-Z0-9]{1,2};$' || true)
say ""
say "## Obfuscation"
say "class descriptors: $total, with ≤2-char simple names: $short"

if command -v jadx >/dev/null; then
    say ""
    say "## jadx (this takes a while)"
    jadx -q -d "$out/jadx" --no-res "$apk" >/dev/null 2>&1 || true
    for s in WEBVTT getCurrentPlaybackTime "aweme_id" "cla_info"; do
        say "### $s"
        grep -rl --include=*.java -F "$s" "$out/jadx/sources" 2>/dev/null | head -20 | sed "s|$out/jadx/sources/||" | tee -a "$report"
    done
fi
say ""
say "report: $report"
