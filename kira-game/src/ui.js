// Интерфейс: диалоги, выборы, задания, уведомления, затемнения
const $ = (s) => document.querySelector(s);

const EMOTE = { love: '♥', happy: '♪', laugh: '♪', angry: '💢', pout: '💢', surprised: '!', sad: '💧', cry: '💧', shy: '///', think: '?', worried: '…', sleepy: 'z', wink: '☆', smug: '~', glitch: '▓' };
const PORTRAIT_FX = { sad: 'sad', cry: 'sad', worried: 'sad', angry: 'shake', pout: 'shake', surprised: 'hop', happy: 'hop', laugh: 'hop', love: 'hop', glitch: 'glitch' };

export class UI {
  constructor(game) {
    this.g = game;
    this.textSpeed = 0.55; // 0..1
    this.dlg = $('#dialog');
    this.dName = this.dlg.querySelector('.name');
    this.dText = this.dlg.querySelector('.text');
    this.portrait = this.dlg.querySelector('.portrait');
    this.emote = this.dlg.querySelector('.emote');
    this.choicesEl = $('#choices');
    this.waiting = null;
    this.typing = false;
    this.choiceState = null;

    this.dlg.addEventListener('mousedown', (e) => { e.stopPropagation(); this.advance(); });
    addEventListener('keydown', (e) => {
      if (this.choiceState) {
        const n = parseInt(e.key, 10);
        if (n >= 1 && n <= this.choiceState.opts.length) this.pick(n - 1);
        if (e.code === 'ArrowDown' || e.code === 'KeyS') this.moveSel(1);
        if (e.code === 'ArrowUp' || e.code === 'KeyW') this.moveSel(-1);
        if ((e.code === 'Enter' || e.code === 'Space') && this.choiceState.sel >= 0) this.pick(this.choiceState.sel);
        return;
      }
      if (!this.dlg.classList.contains('hidden') && (e.code === 'Space' || e.code === 'Enter' || e.code === 'KeyE')) {
        e.preventDefault();
        this.advance();
      }
    });
  }

  advance() {
    if (this.typing) { this.skipType = true; return; }
    if (this.waiting) {
      const w = this.waiting;
      this.waiting = null;
      this.g.audio.sfx('click');
      w();
    }
  }

  // who: k — Кира, p — игрок, n — рассказчик, g — «система»/глюк
  async say(who, text, opts = {}) {
    const g = this.g;
    text = text.replace(/\{p\}/g, g.state.name || 'Ты');
    this.dlg.classList.remove('hidden', 'player', 'narr', 'glitchy', 'done', 'noportrait');
    const names = { k: 'Кира', p: g.state.name || 'Ты', n: '', g: '???' };
    this.dName.textContent = opts.name || names[who] || '';
    if (who !== 'k' || opts.noPortrait) this.dlg.classList.add('noportrait');
    if (who === 'p') this.dlg.classList.add('player');
    if (who === 'n') this.dlg.classList.add('narr');
    if (who === 'g') this.dlg.classList.add('glitchy');
    if (who === 'k') {
      const emo = opts.e || 'normal';
      if (g.kira) g.kira.setEmotion(emo);
      this.portrait.className = 'portrait ' + (PORTRAIT_FX[emo] || '');
      // перезапуск анимации
      void this.portrait.offsetWidth;
      this.emote.textContent = EMOTE[emo] || '';
      this.emote.style.animation = 'none';
      void this.emote.offsetWidth;
      this.emote.style.animation = '';
      if (opts.pose && g.kira) g.kira.setPose(opts.pose);
    }
    // текст печатается
    this.dText.textContent = '';
    this.typing = true;
    this.skipType = false;
    if (who === 'k') { this.dlg.classList.add('talking'); if (g.kira) g.kira.talking = true; }
    const cps = 15 + this.textSpeed * 85;
    let i = 0;
    await new Promise((res) => {
      let acc = 0, last = performance.now();
      const tick = (now) => {
        acc += ((now - last) / 1000) * cps;
        last = now;
        if (this.skipType) { i = text.length; }
        while (acc >= 1 && i < text.length) {
          const ch = text[i++];
          acc -= 1;
          if (i % 2 === 0) {
            if (who === 'k' || who === 'g') g.audio.voice(ch, who);
            else if (who === 'p') g.audio.voice(ch, 'p');
          }
          if (/[.!?…]/.test(ch)) acc -= cps * 0.12;
        }
        this.dText.textContent = text.slice(0, i);
        if (i >= text.length) return res();
        requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    });
    this.typing = false;
    this.dlg.classList.remove('talking');
    if (g.kira) g.kira.talking = false;
    this.dlg.classList.add('done');
    if (opts.auto) { await new Promise((r) => setTimeout(r, opts.auto)); return; }
    if (opts.noWait) return;
    await new Promise((res) => { this.waiting = res; });
  }

  hideDialog() {
    this.dlg.classList.add('hidden');
    if (this.g.kira) this.g.kira.talking = false;
  }

  // Варианты: [{t, ...}] -> индекс
  choose(opts) {
    return new Promise((res) => {
      this.choicesEl.innerHTML = '';
      this.choicesEl.classList.remove('hidden');
      this.choiceState = { opts, res, sel: -1 };
      opts.forEach((o, i) => {
        const b = document.createElement('button');
        b.innerHTML = `<span class="k">${i + 1}</span>`;
        b.appendChild(document.createTextNode(o.t.replace(/\{p\}/g, this.g.state.name)));
        b.addEventListener('mousedown', (e) => { e.stopPropagation(); this.pick(i); });
        b.addEventListener('mouseenter', () => this.g.audio.sfx('hover'));
        this.choicesEl.appendChild(b);
      });
    });
  }
  moveSel(d) {
    const s = this.choiceState;
    s.sel = (s.sel + d + s.opts.length) % s.opts.length;
    [...this.choicesEl.children].forEach((b, i) => b.classList.toggle('sel', i === s.sel));
  }
  pick(i) {
    const s = this.choiceState;
    if (!s) return;
    this.choiceState = null;
    this.choicesEl.classList.add('hidden');
    this.g.audio.sfx('click');
    s.res(i);
  }

  setObjectives(list, title = 'ЗАДАНИЯ') {
    const el = $('#objectives');
    if (!list || !list.length) { el.innerHTML = ''; return; }
    el.innerHTML = `<div class="head">${title}</div>` + list.map((o) => `<div class="obj ${o.done ? 'done' : ''}">${o.text}</div>`).join('');
  }

  toast(text, kind = '') {
    const t = document.createElement('div');
    t.className = 'toast ' + kind;
    t.textContent = text;
    $('#toasts').appendChild(t);
    setTimeout(() => t.remove(), 3200);
  }

  setAffection(v) {
    const lv = v >= 85 ? 'Любовь' : v >= 65 ? 'Влюблена' : v >= 45 ? 'Симпатия' : v >= 25 ? 'Дружба' : 'Знакомство';
    $('#affection .fill').style.width = Math.max(4, Math.min(100, v)) + '%';
    $('#affection .lvl').textContent = lv;
  }

  fade(to, dur = 1) {
    const f = $('#fade');
    f.style.transitionDuration = dur + 's';
    f.style.opacity = to;
    return new Promise((r) => setTimeout(r, dur * 1000 + 30));
  }

  async titleCard(t1, t2, hold = 2.6) {
    const el = $('#titlecard');
    el.querySelector('.t1').textContent = t1;
    el.querySelector('.t2').textContent = t2;
    el.classList.remove('hidden');
    el.style.opacity = 1;
    el.style.transition = 'opacity 1s';
    // перезапуск анимации
    for (const c of el.children) { c.style.animation = 'none'; void c.offsetWidth; c.style.animation = ''; }
    await new Promise((r) => setTimeout(r, hold * 1000));
    el.style.opacity = 0;
    await new Promise((r) => setTimeout(r, 1000));
    el.classList.add('hidden');
  }

  setDayLabel(t) { $('#daylabel').textContent = t; }

  prompt(text) {
    const p = $('#prompt');
    if (!text) { p.style.display = 'none'; $('#crosshair').classList.remove('active'); return; }
    p.innerHTML = `<b>[E]</b> ${text}`;
    p.style.display = 'block';
    $('#crosshair').classList.add('active');
  }

  showPhoto(src, caption, ms = 2600) {
    const el = $('#photo');
    el.querySelector('img').src = src;
    el.querySelector('.cap').textContent = caption;
    el.classList.remove('hidden');
    return new Promise((r) => setTimeout(() => { el.classList.add('hidden'); r(); }, ms));
  }

  askName() {
    return new Promise((res) => {
      const el = $('#nameinput');
      const inp = el.querySelector('input');
      el.classList.remove('hidden');
      inp.value = '';
      setTimeout(() => inp.focus(), 50);
      const done = () => {
        const v = inp.value.trim() || 'Макс';
        el.classList.add('hidden');
        btn.removeEventListener('click', done);
        inp.removeEventListener('keydown', kd);
        res(v.slice(0, 16));
      };
      const kd = (e) => { e.stopPropagation(); if (e.key === 'Enter') done(); };
      const btn = el.querySelector('button');
      btn.addEventListener('click', done);
      inp.addEventListener('keydown', kd);
    });
  }
}
