// Процедурный звук: музыка, эффекты и «голос» Киры. Без внешних файлов.

const NOTE = (n) => 440 * Math.pow(2, (n - 69) / 12);

// Аккорды задаются MIDI-нотами
const TRACKS = {
  home: {
    bpm: 78, swing: 0.12,
    chords: [[53, 57, 60, 64], [52, 55, 59, 62], [50, 53, 57, 60], [48, 52, 55, 59]],
    bass: [41, 40, 38, 36],
    melody: [72, null, 71, 69, null, 67, 69, null, 71, null, 72, 74, null, 72, 71, null],
    drums: true, pad: 0.05, lead: 0.035,
  },
  night: {
    bpm: 62, swing: 0,
    chords: [[57, 60, 64, 67], [53, 57, 60, 64], [55, 59, 62, 65], [52, 55, 59, 64]],
    bass: [45, 41, 43, 40],
    melody: [76, null, null, 74, null, null, 72, null, 71, null, null, 69, null, null, null, null],
    drums: false, pad: 0.06, lead: 0.03,
  },
  game: {
    bpm: 128, swing: 0,
    chords: [[60, 64, 67], [57, 60, 64], [53, 57, 60], [55, 59, 62]],
    bass: [36, 33, 29, 31],
    melody: [72, 76, 79, 76, 72, 76, 81, 79, 77, 76, 74, 72, 74, 76, 72, null],
    drums: true, pad: 0.025, lead: 0.04, square: true,
  },
  love: {
    bpm: 70, swing: 0.08,
    chords: [[53, 57, 60, 64], [55, 59, 62, 65], [52, 55, 59, 64], [57, 60, 64, 67]],
    bass: [41, 43, 40, 45],
    melody: [69, null, 72, null, 76, null, 74, 72, null, 71, null, 72, 74, null, null, null],
    drums: true, pad: 0.065, lead: 0.04,
  },
  glitch: {
    bpm: 54, swing: 0,
    chords: [[48, 51, 54], [47, 50, 53], [46, 49, 52], [47, 50, 54]],
    bass: [36, 35, 34, 35],
    melody: [null, null, 63, null, null, null, 62, null, null, 66, null, null, 61, null, null, null],
    drums: false, pad: 0.05, lead: 0.025, detune: 40,
  },
  dance: {
    bpm: 110, swing: 0,
    chords: [[57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62]],
    bass: [33, 29, 36, 31],
    melody: [76, 76, 74, 72, 74, null, 72, 69, 72, 72, 74, 76, 79, null, 76, null],
    drums: true, pad: 0.03, lead: 0.045, square: true, four: true,
  },
};

export class Audio {
  constructor() {
    this.ctx = null;
    this.vol = { master: 0.8, music: 0.6, sfx: 0.8, voice: 0.7 };
    this.track = null;
    this.step = 0;
    this.nextTime = 0;
    this.timer = null;
  }

  init() {
    if (this.ctx) return;
    const AC = window.AudioContext || window.webkitAudioContext;
    this.ctx = new AC();
    const c = this.ctx;
    this.master = c.createGain();
    this.master.connect(c.destination);
    this.musicGain = c.createGain();
    this.sfxGain = c.createGain();
    this.voiceGain = c.createGain();
    // мягкий «ревер» для музыки
    const delay = c.createDelay(1);
    delay.delayTime.value = 0.28;
    const fb = c.createGain();
    fb.gain.value = 0.28;
    const lp = c.createBiquadFilter();
    lp.type = 'lowpass';
    lp.frequency.value = 2200;
    this.musicGain.connect(this.master);
    this.musicGain.connect(delay);
    delay.connect(lp);
    lp.connect(fb);
    fb.connect(delay);
    lp.connect(this.master);
    this.sfxGain.connect(this.master);
    this.voiceGain.connect(this.master);
    this.noiseBuf = c.createBuffer(1, c.sampleRate, c.sampleRate);
    const d = this.noiseBuf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    this.applyVolume();
    this.timer = setInterval(() => this.schedule(), 50);
  }

  applyVolume() {
    if (!this.ctx) return;
    this.master.gain.value = this.vol.master;
    this.musicGain.gain.setTargetAtTime(this.vol.music * (this.duck ? 0.35 : 1), this.ctx.currentTime, 0.3);
    this.sfxGain.gain.value = this.vol.sfx;
    this.voiceGain.gain.value = this.vol.voice;
  }

  setDuck(on) { this.duck = on; this.applyVolume(); }

  play(name) {
    if (!this.ctx) return;
    if (this.trackName === name) return;
    this.trackName = name;
    this.track = name ? TRACKS[name] : null;
    this.step = 0;
    this.nextTime = this.ctx.currentTime + 0.1;
  }

  schedule() {
    if (!this.ctx || !this.track) return;
    const t = this.track;
    const stepDur = 60 / t.bpm / 4; // шестнадцатые
    while (this.nextTime < this.ctx.currentTime + 0.25) {
      this.playStep(t, this.step, this.nextTime, stepDur);
      let dur = stepDur;
      if (t.swing) dur *= this.step % 2 === 0 ? 1 + t.swing : 1 - t.swing;
      this.nextTime += dur;
      this.step++;
    }
  }

  playStep(t, step, time, sd) {
    const bar = Math.floor(step / 16) % t.chords.length;
    const s = step % 16;
    const chord = t.chords[bar];
    if (s === 0) {
      for (const n of chord) this.tone(NOTE(n), time, sd * 16, t.pad, 'triangle', this.musicGain, 0.4, t.detune);
      this.tone(NOTE(t.bass[bar]), time, sd * 6, 0.12, 'sine', this.musicGain, 0.02);
    }
    if (s === 8) this.tone(NOTE(t.bass[bar] + (t.four ? 12 : 7)), time, sd * 6, 0.09, 'sine', this.musicGain, 0.02);
    const m = t.melody[(s + (bar % 2) * 3) % 16];
    if (m && (step >> 4) % 4 !== 3) {
      this.tone(NOTE(m + (bar === 2 ? -2 : 0)), time, sd * 2.5, t.lead, t.square ? 'square' : 'sine', this.musicGain, 0.01, t.detune);
    }
    if (t.drums) {
      if (s === 0 || s === 8 || (t.four && (s === 4 || s === 12))) this.kick(time);
      if (s === 4 || s === 12) this.snare(time, t.four ? 0.08 : 0.04);
      if (s % 2 === 0) this.hat(time, s % 4 === 2 ? 0.03 : 0.015);
    }
  }

  tone(freq, time, dur, vol, type = 'sine', dest, attack = 0.01, detune = 0) {
    const c = this.ctx;
    const o = c.createOscillator();
    const g = c.createGain();
    o.type = type;
    o.frequency.value = freq;
    if (detune) o.detune.value = (Math.random() - 0.5) * detune;
    g.gain.setValueAtTime(0, time);
    g.gain.linearRampToValueAtTime(vol, time + attack);
    g.gain.exponentialRampToValueAtTime(0.0008, time + dur);
    o.connect(g);
    g.connect(dest || this.sfxGain);
    o.start(time);
    o.stop(time + dur + 0.05);
  }

  noise(time, dur, vol, freq = 8000, type = 'highpass', dest) {
    const c = this.ctx;
    const s = c.createBufferSource();
    s.buffer = this.noiseBuf;
    const f = c.createBiquadFilter();
    f.type = type;
    f.frequency.value = freq;
    const g = c.createGain();
    g.gain.setValueAtTime(vol, time);
    g.gain.exponentialRampToValueAtTime(0.0008, time + dur);
    s.connect(f);
    f.connect(g);
    g.connect(dest || this.sfxGain);
    s.start(time, Math.random() * 0.5);
    s.stop(time + dur + 0.05);
  }

  kick(time) {
    const c = this.ctx;
    const o = c.createOscillator();
    const g = c.createGain();
    o.frequency.setValueAtTime(120, time);
    o.frequency.exponentialRampToValueAtTime(40, time + 0.15);
    g.gain.setValueAtTime(0.25, time);
    g.gain.exponentialRampToValueAtTime(0.001, time + 0.25);
    o.connect(g);
    g.connect(this.musicGain);
    o.start(time);
    o.stop(time + 0.3);
  }
  snare(time, v) { this.noise(time, 0.15, v, 1500, 'bandpass', this.musicGain); }
  hat(time, v) { this.noise(time, 0.04, v, 7000, 'highpass', this.musicGain); }

  // ---- эффекты ----
  sfx(name) {
    if (!this.ctx) return;
    const t = this.ctx.currentTime;
    switch (name) {
      case 'click': this.tone(880, t, 0.06, 0.08, 'triangle'); break;
      case 'hover': this.tone(1200, t, 0.03, 0.03, 'sine'); break;
      case 'pickup':
        this.tone(660, t, 0.1, 0.12, 'triangle');
        this.tone(990, t + 0.07, 0.15, 0.12, 'triangle');
        break;
      case 'heart':
        [72, 76, 79, 84].forEach((n, i) => this.tone(NOTE(n), t + i * 0.07, 0.3, 0.08, 'sine'));
        break;
      case 'sad':
        [67, 63, 60].forEach((n, i) => this.tone(NOTE(n), t + i * 0.12, 0.35, 0.07, 'triangle'));
        break;
      case 'success':
        [60, 64, 67, 72, 76].forEach((n, i) => this.tone(NOTE(n), t + i * 0.08, 0.3, 0.09, 'square'));
        break;
      case 'fail':
        [64, 60, 55].forEach((n, i) => this.tone(NOTE(n), t + i * 0.12, 0.25, 0.07, 'square'));
        break;
      case 'hit': this.tone(440 + Math.random() * 200, t, 0.06, 0.1, 'square'); break;
      case 'point': this.tone(1046, t, 0.12, 0.08, 'square'); break;
      case 'glitch':
        this.noise(t, 0.4, 0.25, 600, 'bandpass');
        for (let i = 0; i < 6; i++) this.tone(100 + Math.random() * 900, t + i * 0.05, 0.05, 0.06, 'sawtooth');
        break;
      case 'bigglitch':
        this.noise(t, 1.4, 0.35, 300, 'lowpass');
        for (let i = 0; i < 20; i++) this.tone(60 + Math.random() * 1500, t + i * 0.06, 0.06, 0.05, 'sawtooth');
        break;
      case 'shutter':
        this.noise(t, 0.05, 0.3, 3000);
        this.noise(t + 0.09, 0.08, 0.25, 2000);
        break;
      case 'door':
        this.tone(90, t, 0.4, 0.2, 'sine');
        this.noise(t, 0.2, 0.1, 400, 'lowpass');
        break;
      case 'knock':
        for (let i = 0; i < 3; i++) { this.tone(110, t + i * 0.18, 0.1, 0.3, 'sine'); this.noise(t + i * 0.18, 0.05, 0.12, 500, 'lowpass'); }
        break;
      case 'step': this.noise(t, 0.06, 0.03, 900, 'lowpass'); break;
      case 'sizzle': this.noise(t, 1.2, 0.08, 4000); break;
      case 'chop': this.noise(t, 0.05, 0.25, 2500, 'bandpass'); this.tone(200, t, 0.05, 0.1, 'square'); break;
      case 'flip': this.tone(300, t, 0.15, 0.1, 'sine'); this.tone(500, t + 0.05, 0.1, 0.06, 'sine'); break;
      case 'kiss':
        this.tone(1400, t, 0.08, 0.06, 'sine');
        this.tone(1800, t + 0.03, 0.06, 0.04, 'sine');
        break;
      case 'type': this.tone(1600 + Math.random() * 300, t, 0.02, 0.025, 'square'); break;
      case 'chime':
        [84, 88, 91].forEach((n, i) => this.tone(NOTE(n), t + i * 0.15, 0.8, 0.05, 'sine'));
        break;
      case 'phone':
        for (let i = 0; i < 2; i++) { this.tone(1318, t + i * 0.25, 0.12, 0.06, 'sine'); this.tone(1046, t + i * 0.25 + 0.12, 0.12, 0.06, 'sine'); }
        break;
    }
  }

  // «Голос» Киры — короткие мелодичные бипы, как в Animal Crossing
  voice(ch, who) {
    if (!this.ctx) return;
    if (/[\s.,!?…—-]/.test(ch)) return;
    const t = this.ctx.currentTime;
    const base = who === 'k' ? 620 : who === 'g' ? 160 : 330;
    const v = (ch.charCodeAt(0) * 37) % 12;
    const f = base * Math.pow(2, v / 24);
    const o = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    o.type = who === 'g' ? 'sawtooth' : 'triangle';
    o.frequency.setValueAtTime(f, t);
    o.frequency.exponentialRampToValueAtTime(f * 1.15, t + 0.05);
    g.gain.setValueAtTime(0, t);
    g.gain.linearRampToValueAtTime(0.06, t + 0.01);
    g.gain.exponentialRampToValueAtTime(0.001, t + 0.07);
    o.connect(g);
    g.connect(this.voiceGain);
    o.start(t);
    o.stop(t + 0.1);
  }
}
