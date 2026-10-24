#!/usr/bin/env bash
# Builds qvk_promo.mp4 (1080x1920, 60 fps, H.264 + AAC) from scratch.
# Needs: node + playwright (Chromium), ffmpeg, python3 + numpy.
set -euo pipefail
cd "$(dirname "$0")"
WORK=${WORK:-build}
FPS=120             # rendered rate; pairs are blended into 60 fps for real motion blur
FRAMES=$((27 * FPS))
JOBS=${JOBS:-4}
mkdir -p "$WORK"

python3 audio.py "$WORK/music.wav"

pids=()
for ((k = 0; k < JOBS; k++)); do
  node render.js video "$WORK/s$k.mkv" $FPS $((k * FRAMES / JOBS)) $(((k + 1) * FRAMES / JOBS)) &
  pids+=($!)
done
for p in "${pids[@]}"; do wait "$p"; done

: > "$WORK/list.txt"
for ((k = 0; k < JOBS; k++)); do echo "file 's$k.mkv'" >> "$WORK/list.txt"; done

ffmpeg -hide_banner -y -f concat -safe 0 -i "$WORK/list.txt" -i "$WORK/music.wav" \
  -filter_complex "[0:v]tmix=frames=2,fps=60,noise=alls=5:allf=t,format=yuv420p[v]" \
  -map "[v]" -map 1:a \
  -c:v libx264 -profile:v high -level 4.2 -preset slow -crf 17 -maxrate 12M -bufsize 24M \
  -g 120 -color_primaries bt709 -color_trc bt709 -colorspace bt709 \
  -c:a aac -b:a 320k -ar 48000 -shortest -movflags +faststart \
  qvk_promo.mp4
