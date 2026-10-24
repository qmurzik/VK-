"""Original phonk-style soundtrack for the QVK promo, synthesised from scratch (no samples, no licences).

120 BPM: one beat = 0.5 s, one bar = 2 s. Section boundaries match timeline.js:
  0.0  hook (drone, glitches, riser)      2.0  bass hit + logo
  3.5  filtered groove, build to 8.0      8.0  full drop, feature cuts every 2 s
 20.0  impact, breakdown                  22.0 final drop      26.0 last hit, tail to 27.0
"""
import sys
import numpy as np

SR = 48000
DUR = 27.0
N = int(SR * DUR)
rng = np.random.default_rng(7)

L = np.zeros(N)
R = np.zeros(N)
REV_SEND = np.zeros(N)  # mono send into the reverb


def place(sig, t0, gain=1.0, pan=0.0, rev=0.0):
    i = int(t0 * SR)
    if i >= N:
        return
    sig = sig[: N - i]
    lg = np.cos((pan + 1) * np.pi / 4) * np.sqrt(2)
    rg = np.sin((pan + 1) * np.pi / 4) * np.sqrt(2)
    L[i:i + len(sig)] += sig * gain * lg
    R[i:i + len(sig)] += sig * gain * rg
    if rev:
        REV_SEND[i:i + len(sig)] += sig * gain * rev


def tt(d):
    return np.arange(int(d * SR)) / SR


def fft_filter(x, lo=None, hi=None, order=2):
    """Static zero-phase band-limiting in the frequency domain (smooth Butterworth-like magnitude)."""
    n = len(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    X = np.fft.rfft(x)
    g = np.ones_like(f)
    if hi:
        g *= 1 / np.sqrt(1 + (f / hi) ** (2 * order))
    if lo:
        g *= 1 / np.sqrt(1 + (lo / np.maximum(f, 1e-6)) ** (2 * order))
    return np.fft.irfft(X * g, n)


def lp_sweep(x, f_start, f_end):
    """Time-varying one-pole x2 lowpass, cutoff moves exponentially from f_start to f_end."""
    n = len(x)
    fc = f_start * (f_end / f_start) ** (np.arange(n) / max(n - 1, 1))
    a = 1 - np.exp(-2 * np.pi * fc / SR)
    y1 = np.empty(n)
    y2 = np.empty(n)
    s1 = s2 = 0.0
    for i in range(n):
        s1 += a[i] * (x[i] - s1)
        s2 += a[i] * (s1 - s2)
        y2[i] = s2
    return y2


def note(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


# ---------------------------------------------------------------- instruments

def kick(freq=55.0, dur=0.9, punch=1.0):
    t = tt(dur)
    f = freq + (freq * 5) * np.exp(-t * 38)
    ph = 2 * np.pi * np.cumsum(f) / SR
    env = np.exp(-t * (2.2 / dur)) * np.minimum(1, t * 800)
    body = np.sin(ph) * env
    click = rng.standard_normal(len(t)) * np.exp(-t * 300) * 0.25 * punch
    return np.tanh((body + click) * 2.4) * 0.8


def bass808(freq, dur):
    t = tt(dur)
    f = freq * (1 + 0.9 * np.exp(-t * 30))
    ph = 2 * np.pi * np.cumsum(f) / SR
    env = np.minimum(1, t * 400) * np.exp(-t * 1.1)
    rel = np.clip((dur - t) / 0.05, 0, 1)
    s = np.sin(ph) * env * rel
    # saturation adds the harmonics that make the 808 audible on phone speakers
    return np.tanh(s * 3.2) * 0.55


def clap():
    d = 0.32
    t = tt(d)
    n = rng.standard_normal(len(t))
    env = np.zeros(len(t))
    for k, off in enumerate([0, 0.011, 0.022]):
        m = t >= off
        env[m] += np.exp(-(t[m] - off) * (160 if k < 2 else 18))
    s = fft_filter(n * env, lo=900, hi=5200)
    return s / (np.abs(s).max() + 1e-9) * 0.6


def snare_roll_hit():
    d = 0.12
    t = tt(d)
    s = fft_filter(rng.standard_normal(len(t)) * np.exp(-t * 40), lo=1500, hi=9000)
    return s / (np.abs(s).max() + 1e-9) * 0.45


def hat(open_=False):
    d = 0.22 if open_ else 0.05
    t = tt(d)
    s = fft_filter(rng.standard_normal(len(t)), lo=7500, order=3) * np.exp(-t * (14 if open_ else 95))
    return s / (np.abs(s).max() + 1e-9) * 0.28


def cowbell(freq):
    d = 0.42
    t = tt(d)
    sq = lambda f: np.sign(np.sin(2 * np.pi * f * t))
    s = (sq(freq) + 0.7 * sq(freq * 1.4836)) * (0.75 * np.exp(-t * 26) + 0.25 * np.exp(-t * 6))
    s = fft_filter(s, lo=freq * 0.8, hi=freq * 4.0)
    return s / (np.abs(s).max() + 1e-9) * 0.33


def pad(midis, dur, bright=1800):
    t = tt(dur)
    s = np.zeros(len(t))
    for m in midis:
        for det in (-0.11, 0.0, 0.13):
            f = note(m + det)
            ph = rng.uniform(0, 1)
            s += 2 * ((f * t + ph) % 1) - 1
    s = fft_filter(s, lo=90, hi=bright)
    env = np.minimum(1, t / 0.35) * np.minimum(1, (dur - t) / 0.4)
    return s / (np.abs(s).max() + 1e-9) * env * 0.16


def riser(dur, f0=300, f1=11000):
    t = tt(dur)
    n = rng.standard_normal(len(t)) * (t / dur) ** 1.6
    s = lp_sweep(n, f0, f1) - lp_sweep(n, f0 * 0.25, f1 * 0.25) * 0.6
    tone_f = 180 * (8 ** (t / dur))
    tone = np.sin(2 * np.pi * np.cumsum(tone_f) / SR) * (t / dur) ** 2 * 0.12
    s = s / (np.abs(s).max() + 1e-9) * 0.4 + tone
    return s


def impact(big=1.0):
    d = 2.6
    t = tt(d)
    f = 48 + 70 * np.exp(-t * 9)
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 1.6) * np.minimum(1, t * 600)
    crash = fft_filter(rng.standard_normal(len(t)), lo=400, hi=9000) * np.exp(-t * 3.2)
    crash = crash / (np.abs(crash).max() + 1e-9)
    return np.tanh(boom * 2.2) * 0.75 * big + crash * 0.22 * big


def whoosh(dur=0.45):
    t = tt(dur)
    env = np.sin(np.pi * (t / dur) ** 0.7) ** 2
    n = rng.standard_normal(len(t)) * env
    s = lp_sweep(n, 600, 9000) - lp_sweep(n, 150, 1200)
    return s / (np.abs(s).max() + 1e-9) * 0.32


def glitch(dur=0.07):
    t = tt(dur)
    f = rng.uniform(200, 2400)
    sq = np.sign(np.sin(2 * np.pi * f * t + 3 * np.sin(2 * np.pi * rng.uniform(20, 90) * t)))
    crush = np.round(sq * 3) / 3
    noise = rng.standard_normal(len(t)) * 0.5
    s = (crush * 0.6 + noise) * np.minimum(1, (dur - t) / 0.005)
    return fft_filter(s, lo=300, hi=7000) * 0.35


def tick(freq=2200):
    t = tt(0.05)
    return np.sin(2 * np.pi * freq * t) * np.exp(-t * 120) * 0.25


def sub_drop(dur=1.2):
    t = tt(dur)
    f = 70 * np.exp(-t * 1.4) + 28
    return np.tanh(np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 2.0) * 2) * 0.6


def drone(dur):
    t = tt(dur)
    s = np.zeros(len(t))
    for m, a in ((36, 1.0), (43, 0.6), (48, 0.5), (51, 0.35)):
        for det in (-0.08, 0.09):
            s += a * (2 * ((note(m + det) * t) % 1) - 1)
    s = fft_filter(s, lo=40, hi=520)
    env = np.minimum(1, t / 1.2)
    return s / (np.abs(s).max() + 1e-9) * env * 0.22


# ---------------------------------------------------------------- arrangement

BEAT = 0.5
BAR = 2.0
S16 = BAR / 16

# 0–2: hook
place(drone(1.9), 0.0, gain=2.2, pan=0, rev=0.4)
for gt in (0.10, 0.22, 0.31, 0.62, 0.70, 1.28, 1.36, 1.52):
    place(glitch(rng.uniform(0.04, 0.09)), gt, gain=2.0, pan=rng.uniform(-0.6, 0.6), rev=0.2)
place(riser(1.05), 0.78, gain=2.0, rev=0.3)
# short silence 1.83–2.0 is the "pause" before the hit

# 2.0: hit + logo
place(impact(1.2), 2.0, rev=0.5)
place(sub_drop(1.6), 2.0, gain=0.9)

# groove helpers --------------------------------------------------------------
ROOTS = [36, 36, 32, 31]          # C, C, Ab, G  (808, one per bar)
CHORDS = [[60, 63, 67], [60, 63, 67], [56, 60, 63], [55, 58, 62]]
KICK_A = [0, 7, 10]
KICK_B = [0, 6, 11, 14]
COW_A = [(0, 0), (3, 0), (6, 3), (8, 7), (10, 5), (12, 3), (14, 2)]
COW_B = [(0, 0), (3, 0), (6, 3), (8, 10), (10, 8), (11, 7), (14, 3)]


def groove_bar(t0, bar_idx, drums=True, claps=True, hats=True, cow=True, padg=1.0, cow_bus=None, hat_rolls=True):
    root = ROOTS[bar_idx % 4]
    kp = KICK_A if bar_idx % 2 == 0 else KICK_B
    if drums:
        for k, s16 in enumerate(kp):
            nxt = kp[k + 1] if k + 1 < len(kp) else 16
            dur = (nxt - s16) * S16
            place(kick(52, 0.35), t0 + s16 * S16, gain=0.9)
            place(bass808(note(root - 12) * (2 if s16 == 14 else 1), dur), t0 + s16 * S16, gain=1.0)
    if claps:
        for s16 in (4, 12):
            place(clap(), t0 + s16 * S16, gain=0.85, rev=0.25)
    if hats:
        for s16 in range(0, 16, 2):
            place(hat(open_=(s16 == 14 and bar_idx % 2 == 1)), t0 + s16 * S16, gain=0.8, pan=0.25)
        if hat_rolls and bar_idx % 2 == 1:
            for k in range(6):
                place(hat(), t0 + (12 + k * (2 / 3)) * S16, gain=0.55, pan=-0.2)
    if cow:
        pat = COW_A if bar_idx % 2 == 0 else COW_B
        for s16, semi in pat:
            sig = cowbell(note(72 + semi))
            if cow_bus is not None:
                i = int((t0 + s16 * S16) * SR)
                cow_bus[i:i + len(sig)] += sig[: max(0, N - i)]
            else:
                place(sig, t0 + s16 * S16, gain=0.9, pan=-0.15, rev=0.3)
    if padg:
        place(pad(CHORDS[bar_idx % 4], BAR), t0, gain=padg, rev=0.5)


# 2.0–8.0: groove, filtered then opening up (cowbell + pad go through a sweeping lowpass)
cow_bus = np.zeros(N)
for b in range(3):
    t0 = 2.0 + b * BAR
    groove_bar(t0, b, drums=(b >= 0), claps=(b >= 1), hats=True, cow=True, padg=0.8, cow_bus=cow_bus,
               hat_rolls=False)
seg = cow_bus[int(2.0 * SR):int(8.0 * SR)]
cow_bus[int(2.0 * SR):int(8.0 * SR)] = lp_sweep(seg, 700, 6000) * 1.4
L += cow_bus * 0.85
R += cow_bus * 1.0
REV_SEND += cow_bus * 0.3

# build into 8.0
for k in range(16):
    tk = 7.0 + 1.0 * (1 - (1 - k / 16) ** 1.4)
    place(snare_roll_hit(), tk, gain=0.35 + 0.5 * k / 16, rev=0.2)
place(riser(1.5, 400, 13000), 6.45, gain=0.9, rev=0.3)

# 8.0–20.0: full drop, a cut every bar
place(impact(1.0), 8.0, rev=0.5)
for b in range(6):
    groove_bar(8.0 + b * BAR, b)
for tb in (10.0, 12.0, 14.0, 16.0, 18.0):
    place(whoosh(0.42), tb - 0.36, pan=rng.uniform(-0.5, 0.5), rev=0.25)
    place(impact(0.35), tb, rev=0.2)
# UI ticks on the privacy toggles (scene 16–18)
for k, tt0 in enumerate((16.35, 16.6, 16.85, 17.1)):
    place(tick(1800 + 220 * k), tt0, gain=0.9, rev=0.2)
place(riser(1.0, 500, 12000), 19.0, gain=0.7)

# 20.0: impact + breakdown
place(impact(1.3), 20.0, rev=0.6)
place(pad([48, 51, 55, 60], 2.0, bright=1200), 20.0, gain=1.2, rev=0.7)
for s16, semi in COW_A:
    place(cowbell(note(72 + semi)), 20.0 + s16 * S16, gain=0.45, rev=0.7)
place(whoosh(0.5), 21.55, rev=0.3)

# 22.0: final drop under "больше, чем просто ВК" and the CTA
place(impact(1.1), 22.0, rev=0.5)
for b in range(2):
    groove_bar(22.0 + b * BAR, b + 2)
place(impact(0.6), 24.0, rev=0.4)
place(pad([48, 51, 55], 3.0, bright=1500), 24.0, gain=0.8, rev=0.7)
for b in range(1):
    groove_bar(24.0, 0, claps=True, hats=True, cow=True, padg=0)
place(impact(1.0), 26.0, rev=0.8)
place(sub_drop(1.0), 26.0, gain=0.7)

# ---------------------------------------------------------------- reverb + master

ir_len = int(2.2 * SR)
ti = np.arange(ir_len) / SR
ir_env = np.exp(-ti * 2.6) * np.minimum(1, ti / 0.01)
irL = fft_filter(rng.standard_normal(ir_len) * ir_env, lo=200, hi=7000)
irR = fft_filter(rng.standard_normal(ir_len) * ir_env, lo=200, hi=7000)
M = 1 << int(np.ceil(np.log2(N + ir_len)))
S = np.fft.rfft(REV_SEND, M)
wetL = np.fft.irfft(S * np.fft.rfft(irL, M), M)[:N]
wetR = np.fft.irfft(S * np.fft.rfft(irR, M), M)[:N]
wn = max(np.abs(wetL).max(), np.abs(wetR).max()) + 1e-9
L += wetL / wn * 0.35
R += wetR / wn * 0.35

mix = np.stack([L, R])
mix = np.stack([fft_filter(ch, lo=28, order=3) for ch in mix])
mix /= np.abs(mix).max() + 1e-9
mix = np.tanh(mix * 2.0) / np.tanh(2.0)         # glue / loudness
# fade the tail
fade = np.clip((DUR - np.arange(N) / SR) / 0.9, 0, 1)
mix *= fade
mix *= 10 ** (-1.0 / 20) / (np.abs(mix).max() + 1e-9)

out = sys.argv[1] if len(sys.argv) > 1 else "music.wav"
pcm = (mix.T * 32767).astype("<i2")
import wave

with wave.open(out, "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print("wrote", out)
