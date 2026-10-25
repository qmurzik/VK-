// Мини-игры на 2D-canvas поверх сцены
const $ = (s) => document.querySelector(s);
const W = 960, H = 560;
const rnd = (a, b) => a + Math.random() * (b - a);
const pick = (a) => a[Math.floor(Math.random() * a.length)];

class MiniGame {
  constructor(game, title, foot) {
    this.g = game;
    this.title = title;
    this.foot = foot;
    this.cv = $('#mgc');
    this.cx = this.cv.getContext('2d');
    this.keys = {};
    this.mouse = { x: W / 2, y: H / 2, down: false };
    this.t = 0;
  }
  run() {
    $('#mg').classList.remove('hidden');
    $('.mg-title').textContent = this.title;
    $('.mg-foot').textContent = this.foot;
    $('.mg-score').textContent = '';
    this.g.player.unlock();
    this.g.inMinigame = true;
    this.onKey = (e) => {
      if (e.repeat) return;
      this.keys[e.code] = true;
      this.keyDown && this.keyDown(e.code, e);
      if (['Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.code)) e.preventDefault();
    };
    this.onKeyUp = (e) => { this.keys[e.code] = false; this.keyUp && this.keyUp(e.code); };
    this.onMove = (e) => {
      const r = this.cv.getBoundingClientRect();
      this.mouse.x = ((e.clientX - r.left) / r.width) * W;
      this.mouse.y = ((e.clientY - r.top) / r.height) * H;
    };
    this.onDown = (e) => { this.onMove(e); this.mouse.down = true; this.click && this.click(this.mouse.x, this.mouse.y); };
    this.onUp = () => { this.mouse.down = false; this.release && this.release(); };
    addEventListener('keydown', this.onKey);
    addEventListener('keyup', this.onKeyUp);
    this.cv.addEventListener('mousemove', this.onMove);
    this.cv.addEventListener('mousedown', this.onDown);
    addEventListener('mouseup', this.onUp);
    return new Promise((res) => {
      this.resolve = res;
      let last = performance.now();
      const loop = (now) => {
        if (this.finished) return;
        const dt = Math.min(0.05, (now - last) / 1000);
        last = now;
        this.t += dt;
        this.update(dt);
        this.draw(this.cx);
        requestAnimationFrame(loop);
      };
      this.init && this.init();
      requestAnimationFrame(loop);
    });
  }
  finish(result, delay = 1200) {
    if (this.finished) return;
    this.finished = true;
    setTimeout(() => {
      removeEventListener('keydown', this.onKey);
      removeEventListener('keyup', this.onKeyUp);
      this.cv.removeEventListener('mousemove', this.onMove);
      this.cv.removeEventListener('mousedown', this.onDown);
      removeEventListener('mouseup', this.onUp);
      $('#mg').classList.add('hidden');
      $('.mg-bubble').classList.add('hidden');
      this.g.inMinigame = false;
      this.resolve(result);
    }, delay);
  }
  bubble(text, ms = 1800) {
    const b = $('.mg-bubble');
    b.querySelector('span').textContent = text;
    b.classList.remove('hidden');
    const sp = b.querySelector('span');
    sp.style.animation = 'none'; void sp.offsetWidth; sp.style.animation = '';
    clearTimeout(this.bubT);
    this.bubT = setTimeout(() => b.classList.add('hidden'), ms);
    for (let i = 0; i < Math.min(8, text.length); i++) setTimeout(() => this.g.audio.voice(text[i * 2] || 'a', 'k'), i * 55);
  }
  score(t) { $('.mg-score').textContent = t; }
  bg(x, c1 = '#120f22', c2 = '#2a1a4a') {
    const g = x.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, c1); g.addColorStop(1, c2);
    x.fillStyle = g; x.fillRect(0, 0, W, H);
  }
  text(x, s, px, py, size = 32, color = '#fff', align = 'center') {
    x.fillStyle = color;
    x.font = `900 ${size}px Nunito, Arial`;
    x.textAlign = align;
    x.textBaseline = 'middle';
    x.fillText(s, px, py);
  }
}

// ---------------- ПОНГ ----------------
export class Pong extends MiniGame {
  constructor(g, opts = {}) {
    super(g, 'PIXEL PONG — ты против Киры', 'Мышь или W/S — двигать ракетку · до ' + (opts.target || 5) + ' очков');
    this.target = opts.target || 5;
    this.skill = opts.skill || 0.75;
    this.p = { y: H / 2, s: 0 };
    this.k = { y: H / 2, s: 0 };
    this.reset(1);
    this.useMouse = true;
  }
  reset(dir) {
    this.ball = { x: W / 2, y: H / 2, vx: 340 * dir, vy: rnd(-180, 180) };
    this.wait = 1;
  }
  update(dt) {
    if (this.over) return;
    if (this.keys.KeyW || this.keys.ArrowUp) { this.p.y -= 520 * dt; this.useMouse = false; }
    else if (this.keys.KeyS || this.keys.ArrowDown) { this.p.y += 520 * dt; this.useMouse = false; }
    else if (this.useMouse || this.mouse.down) this.p.y += (this.mouse.y - this.p.y) * Math.min(1, dt * 14);
    if (Math.abs(this.mouse.y - (this.lastMy || 0)) > 4) { this.useMouse = true; this.lastMy = this.mouse.y; }
    this.p.y = Math.max(60, Math.min(H - 60, this.p.y));
    // ИИ Киры
    const b = this.ball;
    const aim = b.vx > 0 ? b.y + Math.sin(this.t * 2) * 30 : H / 2;
    const sp = 300 + this.skill * 200;
    const d = aim - this.k.y;
    this.k.y += Math.sign(d) * Math.min(Math.abs(d), sp * dt);
    this.k.y = Math.max(60, Math.min(H - 60, this.k.y));
    if (this.wait > 0) { this.wait -= dt; return; }
    b.x += b.vx * dt; b.y += b.vy * dt;
    if (b.y < 10 || b.y > H - 10) { b.vy *= -1; b.y = Math.max(10, Math.min(H - 10, b.y)); this.g.audio.sfx('hit'); }
    const hitPad = (px, py, dir) => {
      if (Math.abs(b.x - px) < 14 && Math.abs(b.y - py) < 62 && Math.sign(b.vx) === -dir) {
        b.vx = -b.vx * 1.06;
        b.vy += (b.y - py) * 6;
        b.x = px + dir * 14;
        this.g.audio.sfx('hit');
        return true;
      }
      return false;
    };
    if (hitPad(40, this.p.y, 1)) { this.rally = (this.rally || 0) + 1; if (this.rally === 6) this.bubble('Ого, долгий розыгрыш!'); }
    if (hitPad(W - 40, this.k.y, -1)) this.rally = (this.rally || 0) + 1;
    b.vx = Math.sign(b.vx) * Math.min(900, Math.abs(b.vx));
    if (b.x < -20) { this.k.s++; this.rally = 0; this.g.audio.sfx('point'); this.bubble(pick(['Хе-хе, моё!', 'Слишком медленно~', 'Кира — чемпион!', 'Ну же, соберись!', 'Опять я!'])); this.reset(1); }
    if (b.x > W + 20) { this.p.s++; this.rally = 0; this.g.audio.sfx('point'); this.bubble(pick(['Эй! Нечестно!', 'Ах ты ж…', 'Это я поддалась!', 'Ммм, неплохо…', 'Я просто моргнула!'])); this.reset(-1); this.skill = Math.min(0.95, this.skill + 0.04); }
    this.score(`${this.p.s} : ${this.k.s}`);
    if (this.p.s >= this.target || this.k.s >= this.target) {
      this.over = true;
      const win = this.p.s > this.k.s;
      this.g.audio.sfx(win ? 'success' : 'fail');
      this.bubble(win ? 'Ты выиграл… Требую реванш!' : 'Победа! Хи-хи~', 2500);
      this.finish({ win, you: this.p.s, kira: this.k.s }, 2200);
    }
  }
  draw(x) {
    this.bg(x, '#0b0916', '#1a1030');
    x.strokeStyle = 'rgba(201,168,255,0.25)';
    x.setLineDash([12, 14]); x.lineWidth = 4;
    x.beginPath(); x.moveTo(W / 2, 0); x.lineTo(W / 2, H); x.stroke(); x.setLineDash([]);
    this.text(x, this.p.s, W / 2 - 80, 60, 64, 'rgba(120,170,255,0.6)');
    this.text(x, this.k.s, W / 2 + 80, 60, 64, 'rgba(255,143,192,0.6)');
    this.text(x, 'ТЫ', 40, 20, 16, '#7aa8ff');
    this.text(x, 'КИРА', W - 40, 20, 16, '#ff8fc0');
    x.shadowBlur = 16;
    x.shadowColor = '#7aa8ff'; x.fillStyle = '#7aa8ff'; x.fillRect(28, this.p.y - 55, 12, 110);
    x.shadowColor = '#ff8fc0'; x.fillStyle = '#ff8fc0'; x.fillRect(W - 40, this.k.y - 55, 12, 110);
    x.shadowColor = '#fff'; x.fillStyle = '#fff';
    x.beginPath(); x.arc(this.ball.x, this.ball.y, 9, 0, Math.PI * 2); x.fill();
    x.shadowBlur = 0;
    if (this.wait > 0 && !this.over) this.text(x, 'Готов?', W / 2, H / 2 + 80, 28, '#c9a8ff');
  }
}

// ---------------- ГОТОВКА ----------------
// steps: [{type:'timing'|'mash'|'sequence'|'hold', label, need, speed, icon}]
export class Cooking extends MiniGame {
  constructor(g, title, steps) {
    super(g, title, '');
    this.steps = steps;
    this.si = -1;
    this.total = 0;
    this.max = 0;
    this.next();
  }
  next() {
    this.si++;
    if (this.si >= this.steps.length) {
      const pct = Math.round((this.total / this.max) * 100);
      this.done = pct;
      this.g.audio.sfx('success');
      this.bubble(pct >= 80 ? 'Идеально! Ты прирождённый повар!' : pct >= 50 ? 'Вкусненько выйдет!' : 'Ну… главное — с любовью!', 2600);
      this.finish({ score: pct }, 2600);
      return;
    }
    const s = this.st = { ...this.steps[this.si], prog: 0, miss: 0, t: 0, pos: 0, dir: 1, seq: [], si: 0, heat: 0, last: null };
    this.max += 100;
    const foot = {
      timing: 'Пробел / клик — когда маркер в зелёной зоне',
      mash: 'Быстро чередуй A и D (или ← →)!',
      sequence: 'Повтори стрелками последовательность',
      hold: 'Держи Пробел / ЛКМ — отпусти в зелёной зоне',
    }[s.type];
    $('.mg-foot').textContent = foot;
    if (s.type === 'timing') { s.zone = rnd(0.3, 0.7); s.zw = s.zw || 0.14; }
    if (s.type === 'sequence') { const a = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight']; for (let i = 0; i < (s.len || 6); i++) s.seq.push(pick(a)); }
    if (s.type === 'mash') s.time = s.time || 6;
    if (s.type === 'hold') { s.zone = rnd(0.55, 0.8); s.zw = 0.12; }
    if (s.say) this.bubble(s.say, 2200);
  }
  stepScore(v) { this.total += Math.max(0, Math.min(100, v)); }
  keyDown(code) {
    const s = this.st;
    if (!s || this.done !== undefined) return;
    if (s.type === 'timing' && code === 'Space') this.hitTiming();
    if (s.type === 'mash' && ['KeyA', 'KeyD', 'ArrowLeft', 'ArrowRight'].includes(code)) {
      const side = code === 'KeyA' || code === 'ArrowLeft' ? 'L' : 'R';
      if (side !== s.last) { s.prog += 100 / (s.need || 30); s.last = side; this.g.audio.sfx('flip'); }
      if (s.prog >= 100) { this.stepScore(100 - s.t * 6); this.next(); }
    }
    if (s.type === 'sequence' && code.startsWith('Arrow')) {
      if (code === s.seq[s.si]) { s.si++; this.g.audio.sfx('click'); }
      else { s.miss++; this.g.audio.sfx('fail'); }
      if (s.si >= s.seq.length) { this.stepScore(100 - s.miss * 20); this.next(); }
    }
    if (s.type === 'hold' && code === 'Space') s.holding = true;
  }
  keyUp(code) { if (code === 'Space' && this.st && this.st.type === 'hold') this.releaseHold(); }
  click() {
    const s = this.st;
    if (!s || this.done !== undefined) return;
    if (s.type === 'timing') this.hitTiming();
    if (s.type === 'hold') s.holding = true;
  }
  release() { if (this.st && this.st.type === 'hold') this.releaseHold(); }
  releaseHold() {
    const s = this.st;
    if (!s.holding) return;
    s.holding = false;
    const d = Math.abs(s.heat - s.zone);
    if (d < s.zw / 2) { this.g.audio.sfx('success'); this.stepScore(100 - d * 300); this.bubble(pick(['Золотистая корочка!', 'Идеальная прожарка!'])); this.next(); }
    else { s.miss++; this.g.audio.sfx('fail'); this.bubble(s.heat > s.zone ? 'Горит, горит!' : 'Ещё сыровато…'); s.heat = 0; if (s.miss >= 3) { this.stepScore(30); this.next(); } }
  }
  hitTiming() {
    const s = this.st;
    if (Math.abs(s.pos - s.zone) < s.zw / 2) {
      s.prog++;
      this.g.audio.sfx('chop');
      s.zone = rnd(0.2, 0.8);
      if (s.prog >= (s.need || 5)) { this.stepScore(100 - s.miss * 15); this.next(); }
    } else {
      s.miss++;
      this.g.audio.sfx('fail');
      if (s.miss === 2) this.bubble(pick(['Осторожнее с ножом!', 'Мимо~', 'Целься лучше!']));
    }
  }
  update(dt) {
    const s = this.st;
    if (!s || this.done !== undefined) return;
    s.t += dt;
    if (s.type === 'timing') {
      s.pos += s.dir * dt * (s.speed || 0.9);
      if (s.pos > 1) { s.pos = 1; s.dir = -1; }
      if (s.pos < 0) { s.pos = 0; s.dir = 1; }
    }
    if (s.type === 'mash') {
      s.prog = Math.max(0, s.prog - dt * 6);
      if (s.t > s.time) { this.stepScore(s.prog); this.bubble('Устала рука? Хи-хи'); this.next(); }
    }
    if (s.type === 'hold') {
      if (s.holding) s.heat = Math.min(1.05, s.heat + dt * 0.45);
      if (s.heat >= 1.05) { s.holding = false; s.miss++; s.heat = 0; this.g.audio.sfx('fail'); this.bubble('Сгорело! Давай ещё раз'); if (s.miss >= 3) { this.stepScore(30); this.next(); } }
    }
    this.score(`Шаг ${Math.min(this.si + 1, this.steps.length)}/${this.steps.length}`);
  }
  draw(x) {
    this.bg(x, '#2a1a24', '#4a2a3a');
    const s = this.st;
    if (!s) return;
    if (this.done !== undefined) {
      this.text(x, `Готово!  ${this.done}%`, W / 2, H / 2 - 20, 56, '#ffd0e6');
      this.text(x, this.done >= 80 ? '★★★' : this.done >= 50 ? '★★☆' : '★☆☆', W / 2, H / 2 + 50, 48, '#ffd27a');
      return;
    }
    this.text(x, s.icon || '🍳', W / 2, 150, 110);
    this.text(x, s.label, W / 2, 260, 34, '#fff');
    if (s.type === 'timing') {
      const bx = 180, bw = 600, by = 360;
      x.fillStyle = '#1d1b24'; x.fillRect(bx, by, bw, 36);
      x.fillStyle = '#5ad48a'; x.fillRect(bx + (s.zone - s.zw / 2) * bw, by, s.zw * bw, 36);
      x.fillStyle = '#fff'; x.fillRect(bx + s.pos * bw - 4, by - 10, 8, 56);
      this.text(x, `${s.prog} / ${s.need || 5}`, W / 2, 440, 26, '#c9a8ff');
    }
    if (s.type === 'mash') {
      const bx = 180, bw = 600, by = 360;
      x.fillStyle = '#1d1b24'; x.fillRect(bx, by, bw, 36);
      x.fillStyle = '#ff8fc0'; x.fillRect(bx, by, (s.prog / 100) * bw, 36);
      this.text(x, `Время: ${Math.max(0, s.time - s.t).toFixed(1)}`, W / 2, 440, 26, '#c9a8ff');
      this.text(x, s.last === 'L' ? '→ D' : 'A ←', W / 2, 490, 30, '#fff');
    }
    if (s.type === 'sequence') {
      const arrows = { ArrowUp: '↑', ArrowDown: '↓', ArrowLeft: '←', ArrowRight: '→' };
      s.seq.forEach((a, i) => {
        const px = W / 2 + (i - (s.seq.length - 1) / 2) * 80;
        x.fillStyle = i < s.si ? '#5ad48a' : i === s.si ? '#8c4dff' : '#2a2733';
        x.beginPath(); x.roundRect(px - 32, 340, 64, 64, 12); x.fill();
        this.text(x, arrows[a], px, 373, 40, '#fff');
      });
    }
    if (s.type === 'hold') {
      const bx = 180, bw = 600, by = 360;
      const g = x.createLinearGradient(bx, 0, bx + bw, 0);
      g.addColorStop(0, '#3a6ad8'); g.addColorStop(0.6, '#ffb84a'); g.addColorStop(1, '#ff3a3a');
      x.fillStyle = g; x.fillRect(bx, by, bw, 36);
      x.strokeStyle = '#5ad48a'; x.lineWidth = 5; x.strokeRect(bx + (s.zone - s.zw / 2) * bw, by - 4, s.zw * bw, 44);
      x.fillStyle = '#fff'; x.fillRect(bx + Math.min(1, s.heat) * bw - 4, by - 10, 8, 56);
      this.text(x, 'Огонь', bx - 50, by + 18, 20, '#ffb84a');
    }
  }
}

// ---------------- МЕМОРИ ----------------
export class Memory extends MiniGame {
  constructor(g) {
    super(g, 'Мемори — кто найдёт больше пар?', 'Кликай по карточкам · Найдёшь пару — ходишь ещё раз');
    const icons = ['♥', '★', '☾', '✿', '♪', '✕', '☀', '♣'];
    const cols = ['#ff8fc0', '#ffd27a', '#c9a8ff', '#7ad8a8', '#7ab8ff', '#ff6a6a', '#ffb84a', '#5ad48a'];
    const deck = [];
    icons.forEach((ic, i) => { deck.push({ ic, c: cols[i] }, { ic, c: cols[i] }); });
    deck.sort(() => Math.random() - 0.5);
    this.cards = deck.map((d, i) => ({ ...d, i, open: false, gone: false, by: null, flip: 0 }));
    this.turn = 'p';
    this.sel = [];
    this.mem = new Map();
    this.you = 0; this.kira = 0;
    this.lock = 0;
  }
  rect(i) { const c = i % 4, r = Math.floor(i / 4); return { x: 240 + c * 125, y: 30 + r * 128, w: 110, h: 118 }; }
  click(mx, my) {
    if (this.turn !== 'p' || this.lock > 0 || this.over) return;
    for (const c of this.cards) {
      const r = this.rect(c.i);
      if (!c.gone && !c.open && mx > r.x && mx < r.x + r.w && my > r.y && my < r.y + r.h) { this.flipCard(c); break; }
    }
  }
  flipCard(c) {
    c.open = true;
    this.g.audio.sfx('flip');
    this.sel.push(c);
    this.mem.set(c.i, c.ic);
    if (this.sel.length === 2) this.lock = 0.9;
  }
  resolvePair() {
    const [a, b] = this.sel;
    this.sel = [];
    if (a.ic === b.ic) {
      a.gone = b.gone = true; a.by = b.by = this.turn;
      this.mem.delete(a.i); this.mem.delete(b.i);
      if (this.turn === 'p') { this.you++; this.g.audio.sfx('success'); if (Math.random() < 0.5) this.bubble(pick(['Эй, это была моя пара!', 'Везунчик!', 'Хм, хорошая память~'])); }
      else { this.kira++; this.g.audio.sfx('point'); this.bubble(pick(['Ха! Я помнила!', 'Кира гений!', 'Ещё одна моя~'])); }
    } else {
      a.open = b.open = false;
      this.turn = this.turn === 'p' ? 'k' : 'p';
      if (this.turn === 'k') this.kDelay = 0.8;
    }
    if (this.cards.every((c) => c.gone)) {
      this.over = true;
      const win = this.you > this.kira;
      this.bubble(win ? 'Ты победил! Ну ладно, заслуженно ♥' : this.you === this.kira ? 'Ничья! Мы идеальная команда' : 'Я выиграла! Хи-хи~', 2600);
      this.finish({ win, you: this.you, kira: this.kira }, 2600);
    }
  }
  kiraMove() {
    const hidden = this.cards.filter((c) => !c.gone && !c.open);
    const first = this.sel[0];
    let choice = null;
    if (!first) {
      // ищем известную пару
      const seen = {};
      for (const [i, ic] of this.mem) {
        const c = this.cards[i];
        if (c.gone || c.open) continue;
        if (seen[ic] !== undefined && Math.random() < 0.7) { choice = this.cards[seen[ic]]; this.kTarget = c; break; }
        seen[ic] = i;
      }
      if (!choice) choice = pick(hidden);
    } else {
      if (this.kTarget && !this.kTarget.open && !this.kTarget.gone) choice = this.kTarget;
      else {
        for (const [i, ic] of this.mem) if (ic === first.ic && i !== first.i && !this.cards[i].gone && Math.random() < 0.65) choice = this.cards[i];
        if (!choice) choice = pick(hidden.filter((c) => c !== first));
      }
      this.kTarget = null;
    }
    if (choice) this.flipCard(choice);
  }
  update(dt) {
    if (this.over) return;
    if (this.lock > 0) { this.lock -= dt; if (this.lock <= 0) this.resolvePair(); return; }
    if (this.turn === 'k') {
      this.kDelay -= dt;
      if (this.kDelay <= 0) { this.kiraMove(); this.kDelay = 0.7; }
    }
    this.score(`Ты ${this.you} : ${this.kira} Кира`);
  }
  draw(x) {
    this.bg(x, '#141022', '#2a1a40');
    this.text(x, this.turn === 'p' ? 'Твой ход' : 'Ход Киры…', 120, 40, 26, this.turn === 'p' ? '#7ab8ff' : '#ff8fc0');
    for (const c of this.cards) {
      const r = this.rect(c.i);
      if (c.gone) { x.globalAlpha = 0.25; }
      x.fillStyle = c.open || c.gone ? '#f6f0ff' : '#5b3d8f';
      x.beginPath(); x.roundRect(r.x, r.y, r.w, r.h, 14); x.fill();
      x.strokeStyle = c.gone ? (c.by === 'p' ? '#7ab8ff' : '#ff8fc0') : '#8c4dff'; x.lineWidth = 3; x.stroke();
      if (c.open || c.gone) this.text(x, c.ic, r.x + r.w / 2, r.y + r.h / 2, 60, c.c);
      else this.text(x, 'K', r.x + r.w / 2, r.y + r.h / 2, 34, 'rgba(255,255,255,0.3)');
      x.globalAlpha = 1;
    }
  }
}

// ---------------- КРЕСТИКИ-НОЛИКИ ----------------
export class TicTac extends MiniGame {
  constructor(g) {
    super(g, 'Крестики-нолики · 3 раунда', 'Ты — ✕, Кира — ○ · кликни по клетке');
    this.round = 1; this.res = { w: 0, l: 0, d: 0 };
    this.newBoard();
  }
  newBoard() { this.b = Array(9).fill(null); this.turn = this.round % 2 ? 'p' : 'k'; this.kDelay = 0.9; this.end = 0; this.cheated = false; }
  winner(b) {
    const L = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]];
    for (const [a, c, d] of L) if (b[a] && b[a] === b[c] && b[a] === b[d]) return b[a];
    return b.every(Boolean) ? 'd' : null;
  }
  minimax(b, me) {
    const w = this.winner(b);
    if (w === 'k') return { s: 1 }; if (w === 'p') return { s: -1 }; if (w === 'd') return { s: 0 };
    let best = { s: me === 'k' ? -2 : 2 };
    for (let i = 0; i < 9; i++) if (!b[i]) {
      b[i] = me;
      const r = this.minimax(b, me === 'k' ? 'p' : 'k');
      b[i] = null;
      if ((me === 'k' && r.s > best.s) || (me === 'p' && r.s < best.s)) best = { s: r.s, i };
    }
    return best;
  }
  cell(i) { return { x: 300 + (i % 3) * 120, y: 100 + Math.floor(i / 3) * 120 }; }
  click(mx, my) {
    if (this.turn !== 'p' || this.end || this.over) return;
    for (let i = 0; i < 9; i++) {
      const c = this.cell(i);
      if (!this.b[i] && mx > c.x && mx < c.x + 115 && my > c.y && my < c.y + 115) { this.b[i] = 'p'; this.g.audio.sfx('click'); this.turn = 'k'; this.kDelay = 0.8; this.check(); break; }
    }
  }
  check() {
    const w = this.winner(this.b);
    if (!w) return;
    this.end = 1.8;
    if (w === 'p') { this.res.w++; this.bubble(pick(['Не может быть!', 'Ты меня подловил!'])); this.g.audio.sfx('success'); }
    else if (w === 'k') { this.res.l++; this.bubble(this.cheated ? 'Победа! …Что? Ничего я не жульничала!' : 'Кира побеждает!', 2600); this.g.audio.sfx('fail'); }
    else { this.res.d++; this.bubble('Ничья. Мы слишком похожи~'); }
  }
  update(dt) {
    if (this.over) return;
    this.score(`Раунд ${this.round}/3 · ${this.res.w}-${this.res.l}-${this.res.d}`);
    if (this.end) {
      this.end -= dt;
      if (this.end <= 0) {
        this.end = 0;
        if (this.round >= 3) { this.over = true; this.finish({ ...this.res, cheated: this.didCheat }, 300); return; }
        this.round++; this.newBoard();
        if (this.round === 3) this.bubble('Последний раунд! Сейчас я серьёзно.');
      }
      return;
    }
    if (this.turn === 'k') {
      this.kDelay -= dt;
      if (this.kDelay > 0) return;
      const empty = this.b.map((v, i) => (v ? -1 : i)).filter((i) => i >= 0);
      let mv;
      if (Math.random() < (this.round === 1 ? 0.45 : 0.2)) mv = pick(empty);
      else mv = this.minimax(this.b.slice(), 'k').i ?? pick(empty);
      this.b[mv] = 'k';
      this.g.audio.sfx('click');
      // в третьем раунде Кира жульничает
      if (this.round === 3 && !this.cheated && empty.length > 3) {
        const empty2 = this.b.map((v, i) => (v ? -1 : i)).filter((i) => i >= 0);
        const extra = this.minimax(this.b.slice(), 'k').i ?? pick(empty2);
        this.b[extra] = 'k';
        this.cheated = true; this.didCheat = true;
        this.bubble('Ой! Рука соскользнула~ Два нолика, бывает!', 3000);
      }
      this.turn = 'p';
      this.check();
    }
  }
  draw(x) {
    this.bg(x, '#101a22', '#1a2a40');
    for (let i = 0; i < 9; i++) {
      const c = this.cell(i);
      x.fillStyle = '#1d1b2e'; x.beginPath(); x.roundRect(c.x, c.y, 115, 115, 14); x.fill();
      if (this.b[i] === 'p') this.text(x, '✕', c.x + 57, c.y + 60, 80, '#7ab8ff');
      if (this.b[i] === 'k') this.text(x, '○', c.x + 57, c.y + 60, 80, '#ff8fc0');
    }
    this.text(x, this.turn === 'p' ? 'Твой ход' : 'Кира думает…', W / 2, 60, 26, '#c9a8ff');
  }
}

// ---------------- РИТМ / ТАНЕЦ ----------------
export class Rhythm extends MiniGame {
  constructor(g, opts = {}) {
    super(g, 'Танцуем с Кирой!', '← ↓ ↑ → (или D F J K) — жми, когда стрелка у линии');
    this.bpm = 110;
    this.len = opts.len || 52;
    this.lanes = ['ArrowLeft', 'ArrowDown', 'ArrowUp', 'ArrowRight'];
    this.alt = { KeyD: 0, KeyF: 1, KeyJ: 2, KeyK: 3 };
    this.notes = [];
    const beat = 60 / this.bpm;
    let tm = 3;
    while (tm < this.len) {
      const lane = Math.floor(Math.random() * 4);
      this.notes.push({ t: tm, lane, hit: null });
      if (tm > 15 && Math.random() < 0.25) this.notes.push({ t: tm, lane: (lane + 2) % 4, hit: null });
      tm += beat * (tm > 25 ? pick([0.5, 1, 1]) : pick([1, 1, 2]));
    }
    this.combo = 0; this.best = 0; this.hits = 0; this.perfect = 0;
    this.fx = [];
    this.speed = 380;
  }
  keyDown(code) {
    let lane = this.lanes.indexOf(code);
    if (lane < 0 && code in this.alt) lane = this.alt[code];
    if (lane < 0 || this.over) return;
    this.flash = this.flash || [0, 0, 0, 0];
    this.flash[lane] = 0.15;
    let best = null, bd = 1;
    for (const n of this.notes) if (n.lane === lane && !n.hit) { const d = Math.abs(n.t - this.t); if (d < bd) { bd = d; best = n; } }
    if (best && bd < 0.16) {
      best.hit = bd < 0.07 ? 'perfect' : 'good';
      this.hits++; if (best.hit === 'perfect') this.perfect++;
      this.combo++; this.best = Math.max(this.best, this.combo);
      this.fx.push({ lane, txt: best.hit === 'perfect' ? 'ИДЕАЛЬНО' : 'ХОРОШО', t: 0.6 });
      this.g.audio.sfx('click');
      if (this.combo === 15) this.bubble('Вау, ты отлично двигаешься!');
      if (this.combo === 30) this.bubble('Мы — идеальный дуэт!!');
    } else { this.combo = 0; }
  }
  update(dt) {
    if (this.over) return;
    for (const n of this.notes) if (!n.hit && this.t - n.t > 0.18) { n.hit = 'miss'; if (this.combo > 8) this.bubble(pick(['Ой, сбился!', 'Не отставай~'])); this.combo = 0; }
    for (const f of this.fx) f.t -= dt;
    this.fx = this.fx.filter((f) => f.t > 0);
    if (this.flash) this.flash = this.flash.map((v) => Math.max(0, v - dt));
    if (Math.abs(this.t - 20) < dt) this.bubble('Сейчас ускоримся!');
    const pct = Math.round((this.hits / this.notes.length) * 100);
    this.score(`Комбо ${this.combo} · ${pct}%`);
    if (this.t > this.len + 1.5) {
      this.over = true;
      this.g.audio.sfx('success');
      this.bubble(pct >= 80 ? 'Это было потрясающе!' : pct >= 50 ? 'Классно потанцевали!' : 'Главное — веселье!', 2500);
      this.finish({ score: pct, best: this.best }, 2500);
    }
  }
  draw(x) {
    this.bg(x, '#1a0b2a', '#3a1a5a');
    const cx = W / 2 - 200, ly = 470;
    const arrows = ['←', '↓', '↑', '→'];
    const cols = ['#ff8fc0', '#7ab8ff', '#7ad8a8', '#ffd27a'];
    for (let i = 0; i < 4; i++) {
      const lx = cx + i * 130;
      x.fillStyle = 'rgba(255,255,255,0.05)'; x.fillRect(lx - 50, 0, 100, H);
      x.strokeStyle = cols[i]; x.lineWidth = 4;
      x.globalAlpha = this.flash && this.flash[i] > 0 ? 1 : 0.5;
      x.beginPath(); x.roundRect(lx - 40, ly - 40, 80, 80, 16); x.stroke();
      this.text(x, arrows[i], lx, ly, 46, cols[i]);
      x.globalAlpha = 1;
    }
    for (const n of this.notes) {
      if (n.hit && n.hit !== 'miss') continue;
      const y = ly - (n.t - this.t) * this.speed;
      if (y < -60 || y > H + 60) continue;
      const lx = cx + n.lane * 130;
      x.globalAlpha = n.hit === 'miss' ? 0.3 : 1;
      x.fillStyle = cols[n.lane];
      x.beginPath(); x.roundRect(lx - 36, y - 36, 72, 72, 16); x.fill();
      this.text(x, arrows[n.lane], lx, y, 44, '#1a0b2a');
      x.globalAlpha = 1;
    }
    for (const f of this.fx) this.text(x, f.txt, cx + f.lane * 130, ly - 90 - (0.6 - f.t) * 60, 22, '#fff');
    if (this.t < 3) this.text(x, Math.ceil(3 - this.t), W / 2, H / 2 - 60, 90, '#fff');
    // пульсирующие сердечки по бокам
    const pulse = 1 + Math.abs(Math.sin(this.t * Math.PI * this.bpm / 60)) * 0.2;
    this.text(x, '♥', 80, H / 2, 70 * pulse, '#ff8fc0');
    this.text(x, '♥', W - 80, H / 2, 70 * pulse, '#c9a8ff');
  }
}

// ---------------- СОЗВЕЗДИЯ ----------------
export class Stars extends MiniGame {
  constructor(g) {
    super(g, 'Созвездия', 'Соединяй звёзды по порядку — кликай на пульсирующую звезду');
    this.cons = [
      { name: 'Сердце', pts: [[480, 210], [420, 150], [350, 170], [340, 240], [400, 320], [480, 400], [560, 320], [620, 240], [610, 170], [540, 150], [480, 210]] },
      { name: 'Котик Кира', pts: [[360, 380], [360, 220], [400, 150], [440, 220], [520, 220], [560, 150], [600, 220], [600, 380], [480, 420], [360, 380]] },
      { name: 'Буква «К»', pts: [[380, 120], [380, 420], [380, 270], [560, 120], [380, 270], [570, 420]] },
    ];
    this.ci = 0; this.si = 1;
    this.bgStars = Array.from({ length: 220 }, () => ({ x: rnd(0, W), y: rnd(0, H), r: rnd(0.5, 1.8), p: rnd(0, 6) }));
    this.doneT = 0;
  }
  click(mx, my) {
    if (this.doneT > 0 || this.over) return;
    const c = this.cons[this.ci];
    const [px, py] = c.pts[this.si];
    if (Math.hypot(mx - px, my - py) < 28) {
      this.si++;
      this.g.audio.tone(523 * Math.pow(2, this.si / 12), this.g.audio.ctx.currentTime, 0.4, 0.08, 'sine');
      if (this.si >= c.pts.length) {
        this.doneT = 2.4;
        this.g.audio.sfx('chime');
        this.bubble(['Сердце… как мило ♥', 'Это же я! Мяу~', '«К» — как Кира. Ты нарисовал моё имя на небе…'][this.ci], 2400);
      }
    }
  }
  update(dt) {
    if (this.doneT > 0) {
      this.doneT -= dt;
      if (this.doneT <= 0) {
        this.ci++; this.si = 1;
        if (this.ci >= this.cons.length) { this.over = true; this.finish({ done: true }, 600); this.ci--; this.si = this.cons[this.ci].pts.length; }
      }
    }
    this.score(`${Math.min(this.ci + 1, 3)}/3 · ${this.cons[this.ci].name}`);
  }
  draw(x) {
    this.bg(x, '#03020a', '#141030');
    for (const s of this.bgStars) {
      x.globalAlpha = 0.4 + Math.sin(this.t * 2 + s.p) * 0.3;
      x.fillStyle = '#fff'; x.beginPath(); x.arc(s.x, s.y, s.r, 0, Math.PI * 2); x.fill();
    }
    x.globalAlpha = 1;
    const c = this.cons[this.ci];
    // подсказка контура
    x.strokeStyle = 'rgba(201,168,255,0.12)'; x.setLineDash([6, 8]); x.lineWidth = 2;
    x.beginPath(); c.pts.forEach(([px, py], i) => (i ? x.lineTo(px, py) : x.moveTo(px, py))); x.stroke(); x.setLineDash([]);
    // проведённые линии
    x.strokeStyle = '#c9a8ff'; x.lineWidth = 3; x.shadowBlur = 12; x.shadowColor = '#c9a8ff';
    x.beginPath();
    for (let i = 0; i < this.si; i++) { const [px, py] = c.pts[i]; i ? x.lineTo(px, py) : x.moveTo(px, py); }
    if (this.si < c.pts.length && this.doneT <= 0) x.lineTo(this.mouse.x, this.mouse.y);
    x.stroke(); x.shadowBlur = 0;
    c.pts.forEach(([px, py], i) => {
      const next = i === this.si && this.doneT <= 0;
      const r = next ? 9 + Math.sin(this.t * 6) * 3 : 6;
      x.fillStyle = i < this.si ? '#ffd27a' : next ? '#ff8fc0' : '#fff';
      x.shadowBlur = 18; x.shadowColor = x.fillStyle;
      x.beginPath(); x.arc(px, py, r, 0, Math.PI * 2); x.fill();
    });
    x.shadowBlur = 0;
    if (this.doneT > 0) this.text(x, c.name, W / 2, 500, 40, '#ffd27a');
  }
}

// ---------------- ТРУБЫ / ПОЧИНКА КОДА ----------------
export class Pipes extends MiniGame {
  constructor(g, n = 5, label = 'Восстановление сектора') {
    super(g, label, 'Кликай по плиткам, чтобы поворачивать. Соедини источник ▶ с ядром ♥');
    this.n = n;
    this.gen();
  }
  gen() {
    const n = this.n;
    // направления: 0 верх, 1 право, 2 низ, 3 лево
    this.grid = Array.from({ length: n }, () => Array.from({ length: n }, () => ({ c: [0, 0, 0, 0], rot: 0 })));
    this.sr = Math.floor(Math.random() * n);
    this.er = Math.floor(Math.random() * n);
    // случайный путь DFS
    const seen = new Set();
    const path = [];
    const dfs = (x, y) => {
      seen.add(x + ',' + y); path.push([x, y]);
      if (x === n - 1 && y === this.er) return true;
      const dirs = [[1, 0, 1], [0, -1, 0], [0, 1, 2], [-1, 0, 3]].sort(() => Math.random() - 0.5);
      for (const [dx, dy] of dirs) {
        const nx = x + dx, ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= n || ny >= n || seen.has(nx + ',' + ny)) continue;
        if (dfs(nx, ny)) return true;
      }
      path.pop();
      return false;
    };
    dfs(0, this.sr);
    const conn = (a, b) => { const [ax, ay] = a, [bx, by] = b; if (bx > ax) return 1; if (bx < ax) return 3; if (by > ay) return 2; return 0; };
    path.forEach((p, i) => {
      const t = this.grid[p[1]][p[0]];
      if (i === 0) t.c[3] = 1; else t.c[conn(p, path[i - 1])] = 1;
      if (i === path.length - 1) t.c[1] = 1; else t.c[conn(p, path[i + 1])] = 1;
    });
    // прочие плитки — случайные
    for (const row of this.grid) for (const t of row) {
      if (t.c.some(Boolean)) continue;
      const shapes = [[1, 0, 1, 0], [1, 1, 0, 0], [1, 1, 1, 0]];
      t.c = pick(shapes).slice();
    }
    // перемешать повороты
    for (const row of this.grid) for (const t of row) { const r = Math.floor(Math.random() * 4); for (let k = 0; k < r; k++) this.rotate(t); }
    if (this.check()) this.gen();
  }
  rotate(t) { t.c = [t.c[3], t.c[0], t.c[1], t.c[2]]; t.rot = (t.rot + 1) % 4; }
  cellSize() { return Math.min(460 / this.n, 90); }
  origin() { const s = this.cellSize(); return { x: W / 2 - (s * this.n) / 2, y: H / 2 - (s * this.n) / 2 }; }
  click(mx, my) {
    if (this.over) return;
    const s = this.cellSize(), o = this.origin();
    const cx = Math.floor((mx - o.x) / s), cy = Math.floor((my - o.y) / s);
    if (cx < 0 || cy < 0 || cx >= this.n || cy >= this.n) return;
    this.rotate(this.grid[cy][cx]);
    this.g.audio.sfx('click');
    if (this.check()) {
      this.over = true;
      this.g.audio.sfx('success');
      this.finish({ done: true }, 1500);
    }
  }
  check() {
    const n = this.n;
    this.lit = new Set();
    const st = this.grid[this.sr][0];
    if (!st.c[3]) return false;
    const q = [[0, this.sr]];
    this.lit.add('0,' + this.sr);
    const D = [[0, -1], [1, 0], [0, 1], [-1, 0]];
    let ok = false;
    while (q.length) {
      const [x, y] = q.shift();
      const t = this.grid[y][x];
      if (x === n - 1 && y === this.er && t.c[1]) ok = true;
      for (let d = 0; d < 4; d++) {
        if (!t.c[d]) continue;
        const nx = x + D[d][0], ny = y + D[d][1];
        if (nx < 0 || ny < 0 || nx >= n || ny >= n) continue;
        if (!this.grid[ny][nx].c[(d + 2) % 4]) continue;
        const k = nx + ',' + ny;
        if (!this.lit.has(k)) { this.lit.add(k); q.push([nx, ny]); }
      }
    }
    return ok;
  }
  update() { this.score(this.over ? 'СОЕДИНЕНО' : ''); }
  draw(x) {
    this.bg(x, '#0a0012', '#200a2a');
    // глитч-полосы
    for (let i = 0; i < 6; i++) { x.fillStyle = `rgba(255,40,120,${Math.random() * 0.08})`; x.fillRect(0, Math.random() * H, W, rnd(2, 14)); }
    const s = this.cellSize(), o = this.origin();
    if (!this.lit) this.check();
    for (let y = 0; y < this.n; y++) for (let xx = 0; xx < this.n; xx++) {
      const t = this.grid[y][xx];
      const px = o.x + xx * s, py = o.y + y * s;
      x.fillStyle = '#1a1028'; x.fillRect(px + 2, py + 2, s - 4, s - 4);
      const lit = this.lit.has(xx + ',' + y);
      x.strokeStyle = lit ? '#ff8fc0' : '#5b3d8f'; x.lineWidth = s * 0.18; x.lineCap = 'round';
      x.shadowBlur = lit ? 14 : 0; x.shadowColor = '#ff8fc0';
      const c = [px + s / 2, py + s / 2];
      const ends = [[0, -1], [1, 0], [0, 1], [-1, 0]];
      x.beginPath();
      t.c.forEach((v, d) => { if (v) { x.moveTo(...c); x.lineTo(c[0] + ends[d][0] * s / 2, c[1] + ends[d][1] * s / 2); } });
      x.stroke();
      x.shadowBlur = 0;
    }
    this.text(x, '▶', o.x - 30, o.y + this.sr * s + s / 2, 34, '#7ad8a8');
    this.text(x, '♥', o.x + this.n * s + 30, o.y + this.er * s + s / 2, 40, this.over ? '#ff8fc0' : '#5b3d8f');
  }
}

// ---------------- РИСОВАНИЕ ----------------
export class Drawing extends MiniGame {
  constructor(g) {
    super(g, 'Нарисуй Киру!', 'Рисуй мышкой · клавиши 1–6 — цвет · Пробел — готово (или кнопка справа)');
    this.cols = ['#1d1b24', '#8c4dff', '#ff8fc0', '#ffe3d6', '#ffd27a', '#7ab8ff'];
    this.col = 0;
    this.time = 75;
    this.layer = document.createElement('canvas');
    this.layer.width = W; this.layer.height = H;
    this.lx = this.layer.getContext('2d');
    this.lx.fillStyle = '#fffaf2'; this.lx.fillRect(0, 0, W - 120, H);
    this.last = null;
  }
  keyDown(code) {
    const n = parseInt(code.replace('Digit', ''), 10);
    if (n >= 1 && n <= 6) this.col = n - 1;
    if (code === 'Space') this.done();
  }
  click(mx, my) {
    if (mx > W - 110) {
      const i = Math.floor((my - 20) / 60);
      if (i >= 0 && i < 6) this.col = i;
      if (my > H - 80) this.done();
    }
    this.last = null;
  }
  release() { this.last = null; }
  done() {
    if (this.over) return;
    this.over = true;
    const url = this.layer.toDataURL('image/jpeg', 0.7);
    this.finish({ img: url }, 400);
  }
  update(dt) {
    if (this.over) return;
    this.time -= dt;
    if (this.time <= 0) this.done();
    if (this.mouse.down && this.mouse.x < W - 120) {
      const x = this.lx;
      x.strokeStyle = this.cols[this.col]; x.lineWidth = this.col === 3 ? 14 : 6; x.lineCap = 'round'; x.lineJoin = 'round';
      x.beginPath();
      if (this.last) x.moveTo(this.last.x, this.last.y); else x.moveTo(this.mouse.x, this.mouse.y);
      x.lineTo(this.mouse.x + 0.1, this.mouse.y);
      x.stroke();
      this.last = { x: this.mouse.x, y: this.mouse.y };
    }
    this.score(`${Math.ceil(Math.max(0, this.time))} с`);
    if (Math.abs(this.time - 40) < dt) this.bubble('Не забудь мои фиолетовые кончики!');
    if (Math.abs(this.time - 15) < dt) this.bubble('Можно я уже посмотрю?..');
  }
  draw(x) {
    x.drawImage(this.layer, 0, 0);
    x.fillStyle = '#1d1b24'; x.fillRect(W - 120, 0, 120, H);
    this.cols.forEach((c, i) => {
      x.fillStyle = c; x.beginPath(); x.arc(W - 60, 50 + i * 60, 22, 0, Math.PI * 2); x.fill();
      if (i === this.col) { x.strokeStyle = '#fff'; x.lineWidth = 4; x.stroke(); }
    });
    x.fillStyle = '#8c4dff'; x.beginPath(); x.roundRect(W - 110, H - 70, 100, 50, 12); x.fill();
    this.text(x, 'Готово', W - 60, H - 45, 20);
  }
}

// ---------------- ПРИСТАВКА: «Звёздный забег» (раннер) ----------------
export class Runner extends MiniGame {
  constructor(g) {
    super(g, 'STAR DASH — кто пробежит дальше?', 'Пробел / ↑ / клик — прыжок · Собирай сердечки');
    this.y = 0; this.vy = 0; this.obs = []; this.hearts = []; this.dist = 0; this.spawn = 1; this.hspawn = 2;
    this.kiraBest = 1400 + Math.floor(Math.random() * 400);
    this.lives = 3; this.inv = 0; this.got = 0;
  }
  jump() { if (this.y <= 0.01 && !this.over) { this.vy = 640; this.g.audio.sfx('flip'); } }
  keyDown(code) { if (code === 'Space' || code === 'ArrowUp' || code === 'KeyW') this.jump(); }
  click() { this.jump(); }
  update(dt) {
    if (this.over) return;
    const sp = 330 + this.dist * 0.12;
    this.dist += sp * dt * 0.1;
    this.vy -= 1700 * dt; this.y = Math.max(0, this.y + this.vy * dt);
    if (this.y === 0) this.vy = 0;
    this.spawn -= dt; this.hspawn -= dt;
    if (this.spawn <= 0) { this.obs.push({ x: W + 20, h: rnd(30, 60), w: rnd(26, 40) }); this.spawn = rnd(0.8, 1.6) * (500 / sp); }
    if (this.hspawn <= 0) { this.hearts.push({ x: W + 20, y: rnd(60, 170) }); this.hspawn = rnd(1.4, 2.6); }
    for (const o of this.obs) o.x -= sp * dt;
    for (const h of this.hearts) h.x -= sp * dt;
    this.obs = this.obs.filter((o) => o.x > -60);
    this.hearts = this.hearts.filter((h) => h.x > -60 && !h.got);
    if (this.inv > 0) this.inv -= dt;
    const px = 150, py = 440 - this.y;
    for (const o of this.obs) if (this.inv <= 0 && Math.abs(o.x + o.w / 2 - px) < o.w / 2 + 14 && py > 440 - o.h - 4) {
      this.lives--; this.inv = 1.2; this.g.audio.sfx('fail');
      if (this.lives > 0) this.bubble(pick(['Ай! Осторожнее!', 'Минус жизнь~', 'Прыгай раньше!']));
    }
    for (const h of this.hearts) if (Math.abs(h.x - px) < 30 && Math.abs((440 - h.y) - (py - 20)) < 40) { h.got = true; this.got++; this.dist += 50; this.g.audio.sfx('pickup'); }
    if (Math.floor(this.dist) > this.kiraBest && !this.passed) { this.passed = true; this.bubble('Ты побил мой рекорд?! Не может быть!', 2500); }
    this.score(`${Math.floor(this.dist)} м · ♥${this.got} · ${'❤'.repeat(this.lives)}`);
    if (this.lives <= 0) {
      this.over = true;
      const win = this.dist > this.kiraBest;
      this.bubble(win ? 'Новый рекорд! Ты лучший!' : `Мой рекорд — ${this.kiraBest} м. Попробуешь ещё?`, 2600);
      this.finish({ win, dist: Math.floor(this.dist), kira: this.kiraBest }, 2600);
    }
  }
  draw(x) {
    this.bg(x, '#1a1040', '#ff8fc0');
    for (let i = 0; i < 40; i++) { x.fillStyle = 'rgba(255,255,255,0.6)'; x.fillRect((i * 97 - this.dist * 2) % W + (i * 97 - this.dist * 2 < 0 ? W : 0), (i * 53) % 260, 2, 2); }
    x.fillStyle = '#2a1a4a'; x.fillRect(0, 440, W, H - 440);
    x.fillStyle = '#8c4dff'; for (let i = 0; i < 20; i++) x.fillRect((i * 60 - this.dist * 10) % W + (((i * 60 - this.dist * 10) % W) < 0 ? W : 0), 446, 30, 4);
    const kx = W - 120;
    this.text(x, `Рекорд Киры: ${this.kiraBest} м`, kx - 40, 30, 18, '#fff');
    for (const o of this.obs) { x.fillStyle = '#1d1b24'; x.fillRect(o.x, 440 - o.h, o.w, o.h); x.fillStyle = '#ff3a6a'; x.fillRect(o.x, 440 - o.h, o.w, 6); }
    for (const h of this.hearts) this.text(x, '♥', h.x, 440 - h.y, 34, '#ff5fa8');
    const py = 440 - this.y;
    if (this.inv <= 0 || Math.floor(this.t * 12) % 2) {
      x.fillStyle = '#7ab8ff'; x.beginPath(); x.roundRect(130, py - 46, 40, 46, 10); x.fill();
      x.fillStyle = '#fff'; x.fillRect(156, py - 36, 6, 8);
    }
  }
}

export const MG = { Pong, Cooking, Memory, TicTac, Rhythm, Stars, Pipes, Drawing, Runner };
