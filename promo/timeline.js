'use strict';
/*
 * QVK promo — deterministic timeline. window.seek(t) renders the exact frame for time t (seconds),
 * so the page can be captured frame by frame (render.js) or scrubbed in a browser (?t=12.3).
 * Beat grid: 120 BPM, bar = 2 s; music cues are documented in audio.py.
 */

const $ = (id) => document.getElementById(id);
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lin = (t, a, b) => clamp((t - a) / (b - a));
const mix = (a, b, k) => a + (b - a) * k;
const eOutExpo = (x) => (x >= 1 ? 1 : 1 - Math.pow(2, -10 * x));
const eOutCubic = (x) => 1 - Math.pow(1 - x, 3);
const eInCubic = (x) => x * x * x;
const eInOutCubic = (x) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
const eInOutQuart = (x) => (x < 0.5 ? 8 * x ** 4 : 1 - Math.pow(-2 * x + 2, 4) / 2);
const eInOutSine = (x) => -(Math.cos(Math.PI * x) - 1) / 2;
const eOutBack = (x) => { const c1 = 1.5, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); };
const hash = (n) => { const x = Math.sin(n * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };
const W = 1080, H = 1920;

// ------------------------------------------------------------------ beat grid (mirrors audio.py)
const KICK_A = [0, 7, 10], KICK_B = [0, 6, 11, 14], S16 = 2 / 16;
const KICKS = [];
const groove = (t0, idx) => (idx % 2 === 0 ? KICK_A : KICK_B).forEach((s) => KICKS.push(t0 + s * S16));
[0, 1, 2].forEach((b) => groove(2 + b * 2, b));
[0, 1, 2, 3, 4, 5].forEach((b) => groove(8 + b * 2, b));
groove(22, 2); groove(24, 3); groove(24, 0);
KICKS.sort((a, b) => a - b);
const kickPulse = (t, rate = 9) => {
  let v = 0;
  for (const k of KICKS) { if (k > t) break; v = Math.max(v, Math.exp(-(t - k) * rate)); }
  return v;
};
const HITS = [[2.0, 0.9], [8.0, 0.55], [10.0, 0.16], [12.0, 0.16], [14.0, 0.16], [16.0, 0.16], [18.0, 0.16],
  [20.0, 0.5], [22.0, 0.85], [24.0, 0.25], [26.0, 0.3]];
const GLITCHES = [0.10, 0.22, 0.31, 0.62, 0.70, 1.28, 1.36, 1.52];
const glitchAt = (t) => { let g = 0; for (const s of GLITCHES) if (t >= s && t < s + 0.075) g = 1 - (t - s) / 0.075 * 0.4; return g; };

// ------------------------------------------------------------------ palettes (dark variants from ThemePalette.kt)
const VK_DARK = { bg: '#141414', card: '#222222', header: '#222222', headerText: '#E1E3E6', tabbar: '#222222', text: '#E1E3E6',
  text2: '#76787A', icon: '#AAAEB3', sep: '#363738', field: '#2C2D2E', bout: '#2B4566', bin: '#2C2D2E', accent: '#71AAEB' };
const th = (o) => Object.assign({}, VK_DARK, o);
const THEMES = {
  amoled: { name: 'AMOLED', p: th({ bg: '#000000', card: '#000000', header: '#000000', tabbar: '#000000', field: '#141414', sep: '#1F1F1F', bin: '#1A1A1A', bout: '#262A33' }) },
  telegram: { name: 'Telegram', p: th({ accent: '#8774E1', bg: '#0F0F0F', card: '#212121', header: '#212121', tabbar: '#212121', field: '#2C2C2C', sep: '#2F2F2F', bout: '#766AC8', bin: '#212121', text2: '#AAAAAA' }) },
  mint: { name: 'Мята', p: th({ accent: '#3DDC97', bg: '#0F1A15', card: '#15241D', header: '#15241D', tabbar: '#15241D', field: '#1C2F26', sep: '#20352B', bout: '#1F4D38', bin: '#1C2F26' }) },
  sakura: { name: 'Сакура', p: th({ accent: '#FF7AA8', bg: '#1A0F14', card: '#24141B', header: '#24141B', tabbar: '#24141B', field: '#301B24', sep: '#3A2230', bout: '#5A2340', bin: '#301B24' }) },
  dracula: { name: 'Dracula', p: th({ accent: '#BD93F9', bg: '#21222C', card: '#282A36', header: '#282A36', tabbar: '#21222C', field: '#343746', sep: '#44475A', text: '#F8F8F2', text2: '#9EA3C2', icon: '#9EA3C2', bout: '#4B3A78', bin: '#343746' }) },
  lavender: { name: 'Лаванда', p: th({ accent: '#B39DDB', bg: '#120F1A', card: '#1B1726', header: '#1B1726', tabbar: '#1B1726', field: '#241E33', sep: '#2C2540', bout: '#3D2E63', bin: '#241E33' }) },
  nord: { name: 'Nord', p: th({ accent: '#88C0D0', bg: '#2E3440', card: '#3B4252', header: '#3B4252', tabbar: '#3B4252', field: '#434C5E', sep: '#4C566A', text: '#ECEFF4', text2: '#B4BCCB', icon: '#A5ADBA', bout: '#5E81AC', bin: '#434C5E' }) },
};
const CYCLE = ['amoled', 'telegram', 'mint', 'sakura'];
const applyTheme = (el, p) => { for (const k in p) el.style.setProperty('--' + k, p[k]); };

// ------------------------------------------------------------------ icons
const ic = {
  phone: '<svg class="hic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"/></svg>',
  dots: '<svg class="hic" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></svg>',
  clip: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 11.5l-8.6 8.6a5.5 5.5 0 0 1-7.8-7.8l8.6-8.6a3.7 3.7 0 0 1 5.2 5.2l-8.6 8.6a1.8 1.8 0 0 1-2.6-2.6l7.9-7.9"/></svg>',
  send: '<svg width="20" height="20" viewBox="0 0 24 24" fill="#fff"><path d="M3 20.5 21 12 3 3.5 3 10l12 2-12 2z"/></svg>',
  home: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/></svg>',
  grid: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></svg>',
  msg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M4 5h16v11H9l-5 4z"/></svg>',
  play: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="4"/><path d="M10 9v6l5-3z" fill="currentColor"/></svg>',
  user: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M4 21c1-4 4.5-6 8-6s7 2 8 6"/></svg>',
};
const statusBar = () => '<div class="sb"><span>12:30</span><span class="ic"><i class="sig"></i><span class="bat"><i></i></span></span></div>';

// ------------------------------------------------------------------ screens
const MSGS = [
  ['in', 'Привет! Как тебе новый клиент?', '12:29'],
  ['out', 'Огонь. Поставил вчера 💜', '12:30'],
  ['in', 'Скинь скрин', '12:30'],
  ['in', 'Слушай, что у тебя с ВК? 👀', '12:31'],
  ['out', 'Это QVK 😎', '12:31'],
  ['in', 'Выглядит как другое приложение', '12:32'],
  ['out', 'Темы, цвета чатов, шрифты — всё настраивается', '12:32'],
  ['in', 'А реклама?', '12:33'],
  ['out', 'Её больше нет 🔥', '12:33'],
];
const chatHTML = () => `
  <div class="top">${statusBar()}
    <div class="hdr"><span class="back-ic">‹</span><div class="ava">А</div>
      <div class="who"><b>Аня</b><span>в сети</span></div>${ic.phone}${ic.dots}</div></div>
  <div class="msgs"><div class="day">сегодня</div>
    ${MSGS.map(([d, txt, tm]) => `<div class="b ${d}">${txt}<span class="tm">${tm}</span></div>`).join('')}</div>
  <div class="wb"><div class="clip">${ic.clip}</div><div class="fld">Сообщение</div><div class="send">${ic.send}</div></div>
  <div class="gesture"></div>`;

// Sections.kt, with a sample "active / total" state for the badges
const SECTIONS = [
  ['🛑', 'Реклама', 'Посты, истории, видео, промо, серверы', 7],
  ['📰', 'Лента', 'Рекомендации, клипы, истории, репосты', 5],
  ['🧹', 'Лента: что ещё скрыть', 'Трансляции, статьи, дайджесты и ещё 19', 9],
  ['🔒', 'Приватность', 'Онлайн, «прочитано», аналитика, VPN', 6],
  ['📡', 'Сеть и телеметрия', 'Блокировка трекинга и статистики', 4],
  ['🎛', 'Интерфейс', 'Вкладки, вибрация, экран', 1],
  ['🙈', 'Скрыть элементы', 'Строка историй, пометки, кнопки', 2],
  ['🎨', 'Оформление и цвета', '15 тем, AMOLED, 16 цветовых слотов', 1],
  ['🔤', 'Шрифты', 'Семейство и размер текста', null],
  ['⚙️', 'Дополнительно', 'Отладка, кэш, о модуле', 0],
];
const HERO_TOTAL = SECTIONS.reduce((a, s) => a + (s[3] || 0), 0);
const dashHTML = () => `${statusBar()}
  <div class="body" id="dashBody">
    <div class="mcard hero"><h1>QVK</h1><div class="cnt"><span id="heroN">0</span> функций активно</div>
      <div class="row"><span>Модуль включён</span><div class="msw"><i></i></div></div>
      <div class="st">✓ Модуль активен (LSPosed) — изменения применяются сразу</div></div>
    <div class="hd">Разделы</div>
    ${SECTIONS.map(([e, t, s, n], i) => `<div class="mcard" id="sec${i}"><div class="em">${e}</div><div class="tx"><b>${t}</b><span>${s}</span></div>
      ${n === null ? '<div class="chev">›</div>' : `<div class="badge ${n ? 'on' : 'off'}" data-n="${n}">0</div>`}</div>`).join('')}
    <div class="mcard"><div class="em" style="font-size:22px">🔎</div><div class="tx"><b>Все настройки и поиск</b></div><div class="chev">›</div></div>
  </div>
  <div class="gesture" style="color:#e2e2e6"></div>`;

const art = {
  city: 'background: linear-gradient(180deg,#1e1b4b 0%,#6d28d9 45%,#f472b6 75%,#fb923c 100%)',
  sale: 'background: radial-gradient(circle at 30% 40%,#fde047 0 18%,transparent 19%), linear-gradient(135deg,#f97316,#ef4444)',
  wall: 'background: radial-gradient(80% 60% at 70% 30%,#c4b5fd 0%,#7c3aed 35%,#1e1b4b 80%)',
};
const skyline = '<svg viewBox="0 0 360 190" preserveAspectRatio="none" style="position:absolute;left:0;bottom:0;width:100%;height:60%"><path fill="#0b0716" d="M0 190V120h20V90h18v30h14V60h22v60h10V100h16V70h12V40h18v80h12V95h20v95h8V80h16v40h14V55h20v65h12V85h18v105h10V110h14V75h20v115z"/><circle cx="268" cy="34" r="16" fill="#fde7f3" opacity=".9"/></svg>';
const feedHTML = () => `
  <div class="top">${statusBar()}<div class="hdr"><span class="ul">Новости</span><span class="tab2">Обсуждаемое</span></div></div>
  <div class="list" id="feedList">
    <div class="post"><div class="ph"><div class="ava" style="background:linear-gradient(135deg,#22d3ee,#6366f1)">Т</div><div><b>Техно Daily</b><span>2 часа назад</span></div></div>
      <p>Тёмная тема и ничего лишнего. Так и должна выглядеть лента.</p><div class="img" style="${art.city}">${skyline}</div>
      <div class="acts"><span>♥ 248</span><span>💬 31</span><span>↗ 12</span></div></div>
    <div class="post" id="adPost"><div class="ph"><div class="ava" style="background:linear-gradient(135deg,#fb923c,#ef4444)">М</div><div><b>Маркетплейс</b><span class="adl">Реклама</span></div></div>
      <p>Скидки до −90% только сегодня! Успей забрать 🔥</p><div class="img" style="${art.sale};height:150px"></div><div class="cta">Подробнее</div>
      <div class="scan"></div><div class="stamp">Реклама скрыта</div></div>
    <div class="post" id="recoPost"><div class="ph"><div><b>Вам может быть интересно</b><span>Рекомендации</span></div></div>
      <div class="reco"><div style="background:linear-gradient(160deg,#0ea5e9,#1e3a8a)"></div><div style="background:linear-gradient(160deg,#a3e635,#15803d)"></div><div style="background:linear-gradient(160deg,#f9a8d4,#9d174d)"></div></div>
      <div class="scan"></div><div class="stamp">Рекомендации скрыты</div></div>
    <div class="post"><div class="ph"><div class="ava" style="background:linear-gradient(135deg,#a78bfa,#6d28d9)">Д</div><div><b>Дизайн-сообщество</b><span>вчера</span></div></div>
      <p>Новая подборка обоев в фиолетовых тонах 💜</p><div class="img" style="${art.wall}"></div>
      <div class="acts"><span>♥ 1,2K</span><span>💬 96</span><span>↗ 210</span></div></div>
  </div>
  <div class="tabbar"><span>${ic.home}</span><span>${ic.grid}</span><span class="act">${ic.msg}</span><span>${ic.play}</span><span>${ic.user}</span></div>
  <div class="gesture"></div>`;

// SettingsFragment.kt, privacy category
const PRIV = [
  ['Не отправлять «прочитано»', 'Читайте сообщения незаметно', 0, 16.35],
  ['Скрыть «печатает…»', 'Не показывать набор текста', 0, 16.6],
  ['Невидимый онлайн', 'Не отправлять setOnline', 0, 16.85],
  ['Истории инкогнито', 'Не отмечать истории просмотренными', 0, 17.1],
  ['Блокировать аналитику', 'myTracker, AppMetrica, Crashlytics, stats.*', 1, 0],
  ['Скрывать VPN от ВК', 'Не давать приложению блокироваться при включённом VPN', 1, 0],
  ['Разрешить скриншоты', 'Снимать защищённые экраны (чаты, истории)', 0, 0],
];
const privHTML = () => `${statusBar()}
  <div class="tb"><span style="font-size:26px">←</span><span>Приватность</span></div>
  <div class="cat">Приватность</div>
  ${PRIV.map(([t, s], i) => `<div class="prow" id="pr${i}"><div class="tx"><b>${t}</b><span>${s}</span></div><div class="msw"><i></i></div></div>`).join('')}
  <div class="gesture" style="color:#e2e2e6"></div>`;

// ------------------------------------------------------------------ text helpers
function splitInto(el, text, cls = '') {
  const chars = [];
  text.split('\n').forEach((line, li) => {
    if (li) el.appendChild(document.createElement('br'));
    line.split(' ').forEach((word, wi) => {
      if (wi) el.appendChild(document.createTextNode(' '));
      const w = document.createElement('span');
      w.className = 'w';
      for (const c of word) {
        const s = document.createElement('span');
        s.className = 'ch ' + cls;
        s.textContent = c;
        w.appendChild(s);
        chars.push(s);
      }
      el.appendChild(w);
    });
  });
  return chars;
}
function animChars(chars, t, t0, t1, o = {}) {
  const st = o.stagger ?? 0.02, d = o.dur ?? 0.55, y = o.y ?? 70, bl = o.blur ?? 16;
  chars.forEach((c, i) => {
    const p = eOutExpo(lin(t, t0 + i * st, t0 + i * st + d));
    const q = t1 == null ? 0 : eInCubic(lin(t, t1 - 0.3 + i * 0.004, t1 - 0.02 + i * 0.004));
    const op = p * (1 - q);
    c.style.opacity = op.toFixed(3);
    c.style.transform = `translateY(${((1 - p) * y - q * 46).toFixed(2)}px) scale(${(1 + (1 - p) * 0.12).toFixed(3)})`;
    const b = (1 - p) * bl + q * 12;
    c.style.filter = b > 0.15 ? `blur(${b.toFixed(2)}px)` : 'none';
  });
}

// ------------------------------------------------------------------ build DOM
const E = {};
let flipT = 7.9;
const CAPS = [
  { t0: 4.45, t1: 7.55, top: 418, s: 'Твой ВКонтакте. Твои правила.' },
  { t0: 8.02, t1: 9.95, top: 176, h: '90+ функций', s: 'Одна панель — 10 разделов' },
  { t0: 10.02, t1: 11.95, top: 176, h: '15 готовых тем', s: 'Для светлой и тёмной темы' },
  { t0: 12.02, t1: 13.95, top: 176, h: 'Цвета чатов', s: '16 слотов: пузыри, фон, шапка, акцент' },
  { t0: 14.02, t1: 15.95, top: 176, h: 'Без рекламы', s: 'Посты, промо и рекомендации — скрыты' },
  { t0: 16.02, t1: 17.95, top: 176, h: 'Приватность', s: 'Невидимый онлайн · без «прочитано»' },
  { t0: 18.02, t1: 19.95, top: 176, h: 'Шрифты', s: 'Семейство и масштаб 50–200%' },
  { t0: 22.14, t1: 23.88, top: 1000, h: 'QVK — больше,\nчем просто ВК.', size: 100 },
];

function build() {
  $('sChat').className = 'scr chat'; $('sChat').innerHTML = chatHTML();
  $('sChatB').className = 'scr chat'; $('sChatB').innerHTML = chatHTML();
  $('sDash').innerHTML = dashHTML();
  $('sFeed').className = 'scr feed'; $('sFeed').innerHTML = feedHTML();
  $('sPriv').innerHTML = privHTML();
  applyTheme($('sFeed'), THEMES.lavender.p);

  // phone thickness: stacked rim layers between the front plate and the back plate
  const rims = $('rims');
  for (let i = 1; i <= 10; i++) {
    const d = document.createElement('div');
    d.className = 'rim';
    const l = 58 - i * 2.4;
    d.style.transform = `translateZ(${-i * 2}px)`;
    d.style.background = `linear-gradient(90deg, hsl(262 12% ${l}%), hsl(262 10% 12%) 9%, hsl(262 10% 9%) 91%, hsl(262 12% ${l}%))`;
    rims.appendChild(d);
  }

  // hook text
  E.hk1 = splitInto($('hk1'), 'Твой ВК может');
  E.hk2 = splitInto($('hk2'), 'выглядеть иначе.');
  E.hk2.slice(10).forEach((c) => c.classList.add('accent'));
  E.hkAll = E.hk1.concat(E.hk2);
  E.hkAll.forEach((c) => { c.dataset.c = c.textContent; });

  // captions
  E.caps = CAPS.map((c) => {
    const el = document.createElement('div');
    el.className = 'cap';
    el.style.top = c.top + 'px';
    const o = { c, el, h: [], s: [] };
    if (c.h) {
      const h = document.createElement('div'); h.className = 'h';
      if (c.size) h.style.fontSize = c.size + 'px';
      o.h = splitInto(h, c.h); el.appendChild(h);
    }
    if (c.s) { const s = document.createElement('div'); s.className = 's'; o.s = splitInto(s, c.s); el.appendChild(s); }
    $('caps').appendChild(el);
    return o;
  });
  const ct = document.createElement('div'); ct.className = 'h'; ct.style.fontSize = '84px';
  E.ctaChars = splitInto(ct, 'Присоединяйся к QVK');
  $('ctaTitle').appendChild(ct);

  // measurements for layout-driven animation
  E.logoW = $('logo').offsetWidth; E.logoH = $('logo').offsetHeight;
  E.chipH = $('chip').offsetHeight;
  E.adH = $('adPost').offsetHeight; E.recoH = $('recoPost').offsetHeight;
  E.badges = [...document.querySelectorAll('#sDash .badge')];
  E.picker = [...document.querySelectorAll('#picker .sl')].map((s) => [s.querySelector('i'), s.querySelector('u')]);
  E.fcTrack = [$('fcTrack').querySelector('i'), $('fcTrack').querySelector('u')];
  E.ripple = document.createElement('div'); E.ripple.className = 'ripple';
  $('sec7').appendChild(E.ripple);
  E.privRipple = document.createElement('div'); E.privRipple.className = 'ripple';
  $('sPriv').appendChild(E.privRipple);
  E.bgx = $('bg').getContext('2d'); E.fxx = $('fx').getContext('2d');

  // soft particle sprite
  const sp = document.createElement('canvas'); sp.width = sp.height = 64;
  const sx = sp.getContext('2d'); const g = sx.createRadialGradient(32, 32, 0, 32, 32, 32);
  g.addColorStop(0, 'rgba(255,255,255,1)'); g.addColorStop(0.25, 'rgba(221,214,254,.75)'); g.addColorStop(1, 'rgba(139,92,246,0)');
  sx.fillStyle = g; sx.fillRect(0, 0, 64, 64); E.sprite = sp;

  // moment the spinning phone shows its back exactly edge-on at 180° → swap content there
  for (let t = 7.45; t < 8.3; t += 0.001) { if (rawPose(t).ry >= 180) { flipT = t; break; } }
}

// ------------------------------------------------------------------ phone choreography
const REST = [
  { x: 0, ry: -9, rx: 4 }, { x: 0, ry: 9, rx: 4 }, { x: -170, ry: 13, rx: 3 },
  { x: 0, ry: -9, rx: 4 }, { x: 0, ry: 9, rx: 4 }, { x: 190, ry: -13, rx: 3 },
];
const FEAT_S = 1.3, FEAT_Y = 40;
function featurePose(t) {
  const i = clamp(Math.floor((t - 8) / 2), 0, 5), u = t - (8 + 2 * i);
  const r = REST[i];
  let x = r.x, ry = r.ry, rx = r.rx, s = FEAT_S, y = FEAT_Y;
  if (i > 0) {
    const pr = REST[i - 1], e = eOutExpo(lin(u, 0, 0.6)), dir = i % 2 ? 1 : -1;
    x = mix(pr.x, r.x, e) + dir * 60 * (1 - e);
    ry = mix(pr.ry, r.ry, e) + dir * 30 * (1 - e);
    s *= 1 + 0.06 * (1 - e);
  }
  ry += 2.2 * Math.sin(u * 1.5);
  s *= 1 + 0.03 * clamp(u / 2);
  y -= 8 * clamp(u / 2);
  return { x, y, s, rx, ry, rz: 0, op: 1 };
}
function rawPose(t) {
  if (t < 3.3) return { x: 0, y: 1600, s: 0.9, rx: 50, ry: -55, rz: -12, op: 0 };
  if (t < 4.5) {
    const p = eOutExpo(lin(t, 3.35, 4.5));
    return { x: 0, y: mix(1500, 90, p), s: mix(0.92, 1.36, p), rx: mix(50, 6, p), ry: mix(-55, -16, p), rz: mix(-12, 0, p), op: 1 };
  }
  if (t < 7.45) {
    const q = eInOutSine(lin(t, 4.5, 7.45));
    return { x: 0, y: mix(90, 70, q), s: mix(1.36, 1.42, q), rx: mix(6, 2, q), ry: mix(-16, 12, q), rz: 0, op: 1 };
  }
  if (t < 8.3) {
    const k = lin(t, 7.45, 8.3);
    const end = featurePose(8.3);
    return { x: 0, y: mix(70, end.y, k), s: mix(1.42, end.s, eInOutCubic(k)) * (1 - 0.16 * Math.sin(Math.PI * k)),
      rx: mix(2, end.rx, k), ry: mix(12, 360 + end.ry, eInOutQuart(k)), rz: 0, op: 1 };
  }
  if (t < 20) return featurePose(t);
  const b = featurePose(19.999), p = eInOutCubic(lin(t, 20, 21.45));
  return { x: mix(b.x, 0, p), y: mix(b.y, -150, p), s: mix(b.s, 0.42, p), rx: mix(b.rx, 22, p), ry: mix(b.ry, 48, p), rz: mix(0, -6, p),
    op: 1 - eInCubic(lin(t, 20.8, 21.45)) };
}

// ------------------------------------------------------------------ backgrounds
const PN = 240;
const PART = Array.from({ length: PN }, (_, i) => ({ x: hash(i * 3.1) * W, y: hash(i * 7.7) * 2100, z: 0.15 + 0.85 * hash(i * 1.3), ph: hash(i * 9.9) * 6.28 }));
const BURST = Array.from({ length: 170 }, (_, i) => ({ u: hash(i * 2.3 + 1), v: hash(i * 5.1 + 2), a: hash(i * 8.7 + 3) * 6.283, sp: 0.4 + hash(i * 4.4) * 0.8, z: 0.3 + hash(i * 6.2) * 0.7 }));

function drawBg(t) {
  const c = E.bgx;
  c.globalCompositeOperation = 'source-over';
  c.fillStyle = '#050507'; c.fillRect(0, 0, W, H);
  if (t < 1.98) return;
  const I = eOutCubic(lin(t, 2.0, 3.2)) * (t > 26.6 ? 1 - lin(t, 26.6, 27) : 1);
  const kp = kickPulse(t);
  const fin = lin(t, 21.6, 22.4);
  const g1 = c.createLinearGradient(0, 0, 0, H);
  g1.addColorStop(0, '#050507'); g1.addColorStop(1, `rgba(20,12,34,${I})`);
  c.fillStyle = g1; c.fillRect(0, 0, W, H);
  c.globalCompositeOperation = 'lighter';
  const blob = (x, y, r, col, a) => {
    const g = c.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, `rgba(${col},${a})`); g.addColorStop(1, `rgba(${col},0)`);
    c.fillStyle = g; c.fillRect(x - r, y - r, 2 * r, 2 * r);
  };
  blob(540 + 220 * Math.sin(t * 0.31), 820 + 160 * Math.cos(t * 0.23), 900, '109,40,217', (0.30 + 0.12 * kp + 0.15 * fin) * I);
  blob(160 + 80 * Math.sin(t * 0.4), 1520, 700, '76,29,149', 0.28 * I);
  blob(930, 330 + 90 * Math.sin(t * 0.5), 620, '168,85,247', (0.13 + 0.06 * kp) * I);
  blob(540, 1180, 520, '139,92,246', 0.12 * I * (t > 3.4 && t < 21.2 ? 1 : 0));

  // perspective floor grid
  const hy = 1330, ga = 0.16 * I * (1 - 0.6 * fin);
  c.strokeStyle = `rgba(167,139,250,${ga})`; c.lineWidth = 1.5;
  c.beginPath();
  for (let i = -14; i <= 14; i++) { c.moveTo(540 + i * 40, hy); c.lineTo(540 + i * 330, H); }
  c.stroke();
  for (let k = 0; k < 14; k++) {
    const z = ((k + t * 1.1) % 14) / 14;
    const yy = hy + (H - hy) * z * z;
    c.strokeStyle = `rgba(167,139,250,${ga * z * 1.4})`;
    c.beginPath(); c.moveTo(0, yy); c.lineTo(W, yy); c.stroke();
  }
  const fadeG = c.createLinearGradient(0, hy - 10, 0, hy + 260);
  fadeG.addColorStop(0, 'rgba(5,5,7,0)');
  c.globalCompositeOperation = 'source-over';
  const hz = c.createLinearGradient(0, hy - 2, 0, hy + 140);
  hz.addColorStop(0, `rgba(20,12,34,${0.9 * I})`); hz.addColorStop(1, 'rgba(20,12,34,0)');
  c.fillStyle = hz; c.fillRect(0, hy - 2, W, 142);
  c.globalCompositeOperation = 'lighter';

  // vertical tech lines with travelling light
  [70, 150, 930, 1010].forEach((x, i) => {
    c.fillStyle = `rgba(167,139,250,${0.07 * I})`; c.fillRect(x, 0, 1.5, H);
    const yy = ((t * (420 + i * 90) + i * 600) % 2600) - 300;
    const lg = c.createLinearGradient(0, yy - 260, 0, yy);
    lg.addColorStop(0, 'rgba(196,181,253,0)'); lg.addColorStop(1, `rgba(221,214,254,${0.65 * I})`);
    c.fillStyle = lg; c.fillRect(x - 0.5, yy - 260, 2.5, 260);
  });

  // drifting particles
  for (let i = 0; i < PN; i++) {
    const p = PART[i];
    const y = ((p.y - t * (25 + 70 * p.z) * (1 + 1.5 * fin)) % 2100 + 2100) % 2100 - 90;
    const x = p.x + Math.sin(t * 0.6 + p.ph) * 14 * p.z;
    const tw = 0.6 + 0.4 * Math.sin(t * 3 + p.ph * 3);
    const a = (0.15 + 0.6 * p.z) * tw * I * (1 + 0.5 * fin);
    const sz = (6 + 22 * p.z * p.z) * (1 + 0.3 * kp * p.z);
    c.globalAlpha = clamp(a);
    c.drawImage(E.sprite, x - sz / 2, y - sz / 2, sz, sz);
  }
  c.globalAlpha = 1;
  c.globalCompositeOperation = 'source-over';
}

function drawFx(t) {
  const c = E.fxx;
  c.clearRect(0, 0, W, H);
  // hook glitch: slices and scanlines
  const g = glitchAt(t);
  if (g > 0 && t < 1.9) {
    const f = Math.floor(t * 120);
    for (let i = 0; i < 9; i++) {
      const y = hash(f * 13 + i) * H * 0.5 + H * 0.25, h = 4 + hash(f * 7 + i) * 46;
      const x = (hash(f * 3 + i) - 0.5) * 300;
      c.fillStyle = i % 3 === 0 ? `rgba(139,92,246,${0.5 * g})` : i % 3 === 1 ? `rgba(34,211,238,${0.28 * g})` : `rgba(0,0,0,${0.85 * g})`;
      c.fillRect(x + (i % 2 ? 0 : 200), y, W * (0.3 + hash(f + i) * 0.7), h);
    }
    c.fillStyle = `rgba(255,255,255,${0.05 * g})`;
    for (let y = 0; y < H; y += 6) c.fillRect(0, y, W, 2);
  }
  // CRT-off line closing the hook
  if (t > 1.74 && t < 1.9) {
    const k = lin(t, 1.74, 1.9), w = W * (1 - eInCubic(k));
    c.fillStyle = `rgba(237,233,254,${1 - k * 0.6})`;
    c.fillRect(540 - w / 2, 868, w, 4 + 10 * (1 - k));
    c.shadowColor = '#a78bfa'; c.shadowBlur = 30;
    c.fillRect(540 - w / 2, 870, w, 2);
    c.shadowBlur = 0;
  }
  c.globalCompositeOperation = 'lighter';
  // anamorphic flares + shockwaves on hits
  const FL = [[2.0, 900, 1], [8.0, 1140, 0.7], [20.0, 1100, 0.6], [22.0, 760, 1], [24.0, 640, 0.45], [26.0, 700, 0.5]];
  for (const [ht, y, a0] of FL) {
    if (t < ht || t > ht + 1.2) continue;
    const d = t - ht, a = a0 * Math.exp(-d * 4.5);
    const lg = c.createLinearGradient(0, 0, W, 0);
    lg.addColorStop(0, 'rgba(139,92,246,0)'); lg.addColorStop(0.5, `rgba(237,233,254,${a})`); lg.addColorStop(1, 'rgba(139,92,246,0)');
    c.fillStyle = lg; c.fillRect(0, y - 2, W, 4);
    const rg = c.createRadialGradient(540, y, 0, 540, y, 520);
    rg.addColorStop(0, `rgba(167,139,250,${0.55 * a})`); rg.addColorStop(1, 'rgba(167,139,250,0)');
    c.save(); c.translate(540, y); c.scale(1, 0.12); c.translate(-540, -y);
    c.fillStyle = rg; c.fillRect(0, y - 520, W, 1040); c.restore();
    if (a0 >= 0.6) {
      const r = eOutCubic(clamp(d / 0.7)) * 950;
      c.strokeStyle = `rgba(196,181,253,${0.55 * (1 - clamp(d / 0.7))})`;
      c.lineWidth = 2 + 18 * (1 - clamp(d / 0.7));
      c.beginPath(); c.arc(540, y, r, 0, 6.283); c.stroke();
    }
  }
  // phone dissolving into particles, converging into the final logo
  if (t > 20.55 && t < 22.1) {
    for (let i = 0; i < BURST.length; i++) {
      const p = BURST[i];
      const sx = 540 + (p.u - 0.5) * 300, sy = 980 + (p.v - 0.5) * 560;
      const out = eOutCubic(lin(t, 20.55, 21.4));
      let x = sx + Math.cos(p.a) * 420 * p.sp * out, y = sy + Math.sin(p.a) * 420 * p.sp * out - 120 * out;
      const conv = eInCubic(lin(t, 21.3, 22.0));
      const ang = p.a + conv * 2.2;
      const rr = 380 * p.sp * (1 - conv);
      x = mix(x, 540 + Math.cos(ang) * rr, conv); y = mix(y, 760 + Math.sin(ang) * rr * 0.6, conv);
      const a = clamp(lin(t, 20.55, 20.8)) * (1 - lin(t, 21.95, 22.05));
      const sz = (8 + 26 * p.z) * (1 + conv);
      c.globalAlpha = a * (0.4 + 0.6 * p.z);
      c.drawImage(E.sprite, x - sz / 2, y - sz / 2, sz, sz);
    }
    c.globalAlpha = 1;
  }
  // orbiting particles around the final logo
  if (t > 22.0) {
    const a = eOutCubic(lin(t, 22.0, 22.6)) * (1 - lin(t, 26.6, 27));
    const cy = t < 24 ? 760 : mix(760, 640, eInOutCubic(lin(t, 24, 24.6)));
    for (let i = 0; i < 90; i++) {
      const p = BURST[i];
      const ang = p.a + t * (0.35 + 0.4 * p.sp) * (i % 2 ? 1 : -1);
      const rr = 330 + 260 * p.u;
      const x = 540 + Math.cos(ang) * rr, y = cy + Math.sin(ang) * rr * 0.32 + (p.v - 0.5) * 60;
      const sz = 6 + 20 * p.z;
      c.globalAlpha = a * (0.25 + 0.5 * p.z) * (0.6 + 0.4 * Math.sin(t * 4 + i));
      c.drawImage(E.sprite, x - sz / 2, y - sz / 2, sz, sz);
    }
    c.globalAlpha = 1;
  }
  c.globalCompositeOperation = 'source-over';
}

// ------------------------------------------------------------------ per-scene updaters
const hex2rgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const rgb2hex = (r) => '#' + r.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('').toUpperCase();
const mixHex = (a, b, k) => { const A = hex2rgb(a), B = hex2rgb(b); return rgb2hex(A.map((v, i) => mix(v, B[i], k))); };

function pickerColor(t) {
  const k1 = eInOutCubic(lin(t, 12.45, 13.05)), k2 = eInOutCubic(lin(t, 13.15, 13.65));
  return mixHex(mixHex('#4B3A78', '#8B5CF6', k1), '#DB2777', k2);
}

function chatState(t) {
  // returns [baseTheme, overlayTheme|null, revealRadius, extra overrides]
  if (t < 10) return ['dracula', null, 0];
  if (t < 12) {
    const k = clamp(Math.floor((t - 10) / 0.5), 0, 3), ts = 10 + k * 0.5;
    const prev = k === 0 ? 'dracula' : CYCLE[k - 1];
    const r = eOutCubic(lin(t, ts, ts + 0.34)) * 1000;
    return r >= 999 ? [CYCLE[k], null, 0] : [prev, CYCLE[k], r];
  }
  if (t < 18) return ['dracula', null, 0];
  return ['nord', null, 0];
}

const FONTS = [['Без засечек', 'Inter'], ['С засечками', '"DejaVu Serif"'], ['Моноширинный', '"JetBrains Mono"'], ['Без засечек', 'Inter']];
function fontState(t) {
  const fi = t < 18.55 ? 0 : t < 19.05 ? 1 : t < 19.45 ? 2 : 3;
  const sc = mix(100, 125, eInOutCubic(lin(t, 19.45, 19.85)));
  return { fi, sc };
}

function setVis(el, on) { el.style.display = on ? '' : 'none'; }

function updateScreens(t) {
  const sChat = $('sChat'), sB = $('sChatB');
  const chatOn = t < flipT || (t >= 10 && t < 14) || t >= 18;
  setVis(sChat, chatOn); setVis($('sDash'), t >= flipT && t < 10); setVis($('sFeed'), t >= 14 && t < 16); setVis($('sPriv'), t >= 16 && t < 18);

  // inner slide-in on every cut (on top of the phone whip)
  const cuts = [10, 12, 14, 16, 18];
  let enter = 1;
  for (const c of cuts) if (t >= c && t < c + 0.3) enter = eOutCubic(lin(t, c, c + 0.3));
  $('screen').style.filter = enter < 1 ? `brightness(${1 + (1 - enter) * 0.5})` : '';

  if (chatOn) {
    const [base, over, r] = chatState(t);
    applyTheme(sChat, THEMES[base].p);
    if (t >= 12 && t < 14) sChat.style.setProperty('--bout', pickerColor(t));
    setVis(sB, !!over);
    if (over) { applyTheme(sB, THEMES[over].p); sB.style.clipPath = `circle(${r.toFixed(1)}px at 352px 806px)`; }
    const fs = t >= 18 ? fontState(t) : { fi: 0, sc: 100 };
    sChat.style.setProperty('--msgFont', FONTS[fs.fi][1]);
    sChat.style.setProperty('--msgScale', (fs.sc / 100).toFixed(3));
  }

  if (t >= flipT && t < 10) {
    const c = eOutCubic(lin(t, 8.15, 8.95));
    E.badges.forEach((b) => { b.textContent = Math.round(+b.dataset.n * c); });
    $('heroN').textContent = Math.round(HERO_TOTAL * c);
    const sc = eInOutCubic(lin(t, 8.85, 9.6)) * 300;
    $('dashBody').style.transform = `translateY(${-sc}px)`;
    const rp = lin(t, 9.5, 9.95);
    E.ripple.style.display = rp > 0 ? '' : 'none';
    if (rp > 0) {
      const R = eOutCubic(rp) * 420;
      Object.assign(E.ripple.style, { left: 300 - R + 'px', top: 35 - R + 'px', width: 2 * R + 'px', height: 2 * R + 'px', opacity: (1 - rp * 0.7).toFixed(2) });
    }
  }

  if (t >= 14 && t < 16) {
    $('feedList').style.transform = `translateY(${-30 * eInOutCubic(lin(t, 14, 16))}px)`;
    const fx = (el, h, ts) => {
      const scan = el.querySelector('.scan'), stamp = el.querySelector('.stamp');
      const sk = lin(t, ts, ts + 0.32);
      scan.style.background = sk > 0 && sk < 1
        ? `linear-gradient(180deg, rgba(139,92,246,0) ${sk * 100 - 30}%, rgba(167,139,250,.55) ${sk * 100}%, rgba(139,92,246,0) ${sk * 100 + 2}%), rgba(76,29,149,${0.35 * sk})`
        : (sk >= 1 ? 'rgba(76,29,149,.35)' : 'none');
      const st = eOutBack(lin(t, ts + 0.12, ts + 0.36));
      stamp.style.opacity = clamp(st).toFixed(2);
      stamp.style.transform = `translate(-50%,-50%) scale(${mix(1.6, 1, st).toFixed(3)})`;
      const col = eInOutCubic(lin(t, ts + 0.42, ts + 0.72));
      el.style.height = (h * (1 - col)).toFixed(1) + 'px';
      el.style.marginBottom = (8 * (1 - col)).toFixed(1) + 'px';
      el.style.paddingTop = el.style.paddingBottom = (12 * (1 - col)).toFixed(1) + 'px';
      el.style.opacity = (1 - col).toFixed(3);
    };
    fx($('adPost'), E.adH, 14.33);
    fx($('recoPost'), E.recoH, 15.08);
  }

  if (t >= 16 && t < 18) {
    PRIV.forEach(([, , on0, ts], i) => {
      const p = on0 ? 1 : ts ? eOutCubic(lin(t, ts, ts + 0.18)) : 0;
      const row = $('pr' + i), sw = row.querySelector('.msw'), th = sw.querySelector('i');
      sw.style.background = mixHex('#2B2E33', '#2787F5', p);
      sw.style.borderColor = mixHex('#8C9199', '#2787F5', p);
      const sz = mix(16, 24, p);
      Object.assign(th.style, { width: sz + 'px', height: sz + 'px', left: mix(6, 22, p) + 'px', background: mixHex('#8C9199', '#FFFFFF', p) });
    });
    let rr = null;
    PRIV.forEach(([, , , ts], i) => { if (ts && t >= ts - 0.05 && t < ts + 0.35) rr = [i, lin(t, ts - 0.05, ts + 0.35)]; });
    E.privRipple.style.display = rr ? '' : 'none';
    if (rr) {
      const row = $('pr' + rr[0]); const R = eOutCubic(rr[1]) * 200;
      Object.assign(E.privRipple.style, { left: 340 - R + 'px', top: row.offsetTop + row.offsetHeight / 2 - R + 'px', width: 2 * R + 'px', height: 2 * R + 'px', opacity: (0.9 - rr[1] * 0.9).toFixed(2) });
    }
  }
}

function updateFloats(t) {
  // theme chip
  const chip = $('chip');
  const ci = eOutExpo(lin(t, 10.08, 10.5)), co = eInCubic(lin(t, 11.7, 11.95));
  setVis(chip, t > 10.05 && t < 11.96);
  if (t > 10.05 && t < 11.96) {
    const k = clamp(Math.floor((t - 10) / 0.5), 0, 3), ts = 10 + k * 0.5;
    const th = THEMES[CYCLE[k]];
    $('chipName').textContent = th.name;
    $('chipDots').innerHTML = [th.p.bg, th.p.card, th.p.bout, th.p.accent].map((c) => `<i style="background:${c}"></i>`).join('');
    const pop = 1 + 0.08 * (1 - eOutCubic(lin(t, ts, ts + 0.25)));
    const w = chip.offsetWidth;
    chip.style.transform = `translate(${540 - w / 2}px, ${mix(500, 452, ci) - co * 30}px) scale(${pop * mix(0.85, 1, ci)})`;
    chip.style.opacity = (ci * (1 - co)).toFixed(3);
  }

  // colour picker
  const pk = $('picker');
  setVis(pk, t > 12.1 && t < 13.97);
  if (t > 12.1 && t < 13.97) {
    const pi = eOutExpo(lin(t, 12.12, 12.6)), po = eInCubic(lin(t, 13.68, 13.95));
    pk.style.opacity = (pi * (1 - po)).toFixed(3);
    pk.style.transform = `translateX(${(1 - pi) * 160 + po * 60}px) rotateY(${(1 - pi) * -25}deg)`;
    pk.style.filter = (1 - pi) * 14 + po * 10 > 0.2 ? `blur(${((1 - pi) * 14 + po * 10).toFixed(2)}px)` : 'none';
    const col = pickerColor(t), ch = [255].concat(hex2rgb(col));
    $('pkPrev').style.background = col;
    $('pkHex').textContent = '#FF' + col.slice(1);
    E.picker.forEach(([fill, knob], i) => { const v = ch[i] / 255 * 100; fill.style.width = v + '%'; knob.style.left = v + '%'; });
  }

  // font card
  const fc = $('fontcard');
  setVis(fc, t > 18.1 && t < 19.97);
  if (t > 18.1 && t < 19.97) {
    const fi = eOutExpo(lin(t, 18.12, 18.6)), fo = eInCubic(lin(t, 19.7, 19.96));
    fc.style.opacity = (fi * (1 - fo)).toFixed(3);
    fc.style.transform = `translateX(${-(1 - fi) * 160 - fo * 60}px)`;
    fc.style.filter = (1 - fi) * 14 + fo * 10 > 0.2 ? `blur(${((1 - fi) * 14 + fo * 10).toFixed(2)}px)` : 'none';
    const fs = fontState(t);
    $('fcFam').textContent = FONTS[fs.fi][0];
    [...$('fcOpts').children].forEach((s, i) => s.classList.toggle('on', i === Math.min(fs.fi, 2) && fs.fi < 3 || (fs.fi === 3 && i === 0)));
    $('fcSize').textContent = Math.round(fs.sc / 5) * 5;
    const v = (fs.sc - 50) / 150 * 100;
    E.fcTrack[0].style.width = v + '%'; E.fcTrack[1].style.left = v + '%';
  }
}

function updateHook(t) {
  const hook = $('hook');
  setVis(hook, t < 1.8);
  if (t >= 1.8) return;
  const f = Math.floor(t * 60);
  const GL = '▓▒░<>/\\#01ΔΣЖЯ';
  E.hkAll.forEach((c, i) => {
    const ta = 0.42 + i * 0.024, tr = ta + 0.13;
    let op = t < ta ? 0 : 1;
    c.textContent = t >= ta && t < tr ? GL[Math.floor(hash(f * 31 + i) * GL.length)] : c.dataset.c;
    if (t >= ta && t < tr) op = 0.6;
    c.style.opacity = op;
  });
  const g = glitchAt(t);
  const sq = t > 1.7 ? eInCubic(lin(t, 1.7, 1.8)) : 0;
  [['hk1', 0], ['hk2', 1]].forEach(([id, k]) => {
    const el = $(id);
    const dx = g ? (hash(f * 5 + k) - 0.5) * 60 * g : 0;
    const sh = g ? 6 + 14 * g : 1.5;
    el.style.transform = `translateX(${dx.toFixed(1)}px) skewX(${(g * (hash(f + k) - 0.5) * 20).toFixed(1)}deg)`;
    el.style.textShadow = `${sh}px 0 rgba(255,45,85,${g ? 0.9 : 0.35}), ${-sh}px 0 rgba(34,211,238,${g ? 0.9 : 0.35}), 0 0 40px rgba(139,92,246,.5)`;
  });
  hook.style.transform = `scale(${1 + 0.04 * lin(t, 0.4, 1.7)}, ${1 - sq * 0.97})`;
  hook.style.filter = `brightness(${1 + sq * 2})`;
}

function placeLogo(t) {
  const lg = $('logo');
  let cx = 540, cy = 900, s = 0, op = 0, bl = 0, glow = 1;
  if (t >= 2.0 && t < 7.8) {
    const p = eOutExpo(lin(t, 2.0, 2.6));
    s = mix(1.4, 1.0, p) * (1 + 0.03 * lin(t, 2.6, 3.3)); op = clamp(lin(t, 2.0, 2.05)); bl = (1 - p) * 26;
    glow = 1 + 1.6 * Math.exp(-(t - 2.0) * 3);
    const m = eInOutCubic(lin(t, 3.3, 3.95));
    cy = mix(900, 296, m); s *= mix(1, 0.62, m);
    const o = eInCubic(lin(t, 7.4, 7.75));
    op *= 1 - o; cy -= o * 50; bl += o * 16;
    glow += 0.35 * kickPulse(t);
  } else if (t >= 22.0) {
    const p = eOutExpo(lin(t, 22.0, 22.7));
    s = mix(1.6, 1.08, p); op = clamp(lin(t, 22.0, 22.04)); bl = (1 - p) * 30; cy = 760;
    glow = 1.2 + 1.8 * Math.exp(-(t - 22) * 2.5) + 0.4 * kickPulse(t);
    const m = eInOutCubic(lin(t, 24.0, 24.6));
    cy = mix(760, 640, m); s *= mix(1, 0.86, m);
    op *= 1 - lin(t, 26.7, 27);
  }
  setVis(lg, op > 0.001);
  if (op <= 0.001) return;
  lg.style.transform = `translate(${cx - (E.logoW * s) / 2}px, ${cy - (E.logoH * s) / 2}px) scale(${s})`;
  lg.style.opacity = op.toFixed(3);
  lg.style.filter = bl > 0.2 ? `blur(${bl.toFixed(2)}px)` : 'none';
  const word = lg.querySelector('.word'), mark = lg.querySelector('.mark');
  word.style.filter = `drop-shadow(0 0 ${(22 * glow).toFixed(1)}px rgba(139,92,246,${clamp(0.55 * glow, 0, 1).toFixed(2)}))`;
  mark.style.filter = `drop-shadow(0 0 ${(26 * glow).toFixed(1)}px rgba(139,92,246,${clamp(0.6 * glow, 0, 1).toFixed(2)}))`;
}

function updatePhone(t) {
  const P = rawPose(t);
  const phone = $('phone'), stage = $('stage');
  setVis(stage, P.op > 0.001);
  if (P.op <= 0.001) return;
  const shake = kickPulse(t, 20) * (t >= 8 && t < 20 ? 4 : 2);
  const sy = shake * (hash(Math.floor(t * 60)) - 0.5) * 2;
  phone.style.transform = `translate3d(${P.x.toFixed(2)}px, ${(P.y + sy).toFixed(2)}px, 0) rotateX(${P.rx.toFixed(2)}deg) rotateY(${P.ry.toFixed(2)}deg) rotateZ(${P.rz.toFixed(2)}deg) scale(${P.s.toFixed(4)})`;
  stage.style.opacity = P.op.toFixed(3);
  // horizontal motion blur from angular + lateral velocity
  const a = rawPose(t - 1 / 120), b = rawPose(t + 1 / 120);
  const v = Math.abs(b.ry - a.ry) * 4.2 + Math.abs(b.x - a.x) + Math.abs(b.y - a.y) * 0.4;
  const std = clamp(v * 0.32, 0, 22);
  $('mblurStd').setAttribute('stdDeviation', `${std.toFixed(2)} ${(std * 0.08).toFixed(2)}`);
  stage.style.filter = std > 0.4 ? 'url(#mblur)' : 'none';
  const ry = ((P.ry % 360) + 360) % 360;
  $('glare').style.backgroundPosition = `${(50 + (ry > 180 ? ry - 360 : ry) * 2.2).toFixed(1)}% 0`;
  const sh = $('phoneShadow');
  sh.style.transform = `translateX(${P.x * 0.8}px) scale(${(P.s / 1.32).toFixed(3)}, 1)`;
  sh.style.opacity = (0.9 * clamp(1 - Math.abs(P.y - 40) / 600)).toFixed(3);
}

function updateCaptions(t) {
  E.caps.forEach(({ c, el, h, s }) => {
    const on = t > c.t0 - 0.01 && t < c.t1 + 0.05;
    setVis(el, on);
    if (!on) return;
    if (h.length) animChars(h, t, c.t0, c.t1, { stagger: 0.028, y: 80 });
    if (s.length) animChars(s, t, c.t0 + (h.length ? 0.16 : 0.05), c.t1, { stagger: 0.012, y: 34, blur: 10, dur: 0.5 });
  });
  const tag = $('tag');
  setVis(tag, t > 5.2 && t < 7.6);
  if (t > 5.2 && t < 7.6) {
    const p = eOutExpo(lin(t, 5.25, 5.8)), q = eInCubic(lin(t, 7.25, 7.55));
    tag.style.opacity = (p * (1 - q)).toFixed(3);
    tag.style.transform = `translate(-50%, ${(506 + (1 - p) * 30 - q * 30).toFixed(1)}px)`;
    tag.style.filter = (1 - p) * 10 + q * 10 > 0.2 ? `blur(${((1 - p) * 10 + q * 10).toFixed(1)}px)` : 'none';
  }
  const cta = $('cta');
  setVis(cta, t > 24.05);
  if (t > 24.05) {
    cta.style.transform = 'translateY(930px)';
    animChars(E.ctaChars, t, 24.1, null, { stagger: 0.025, y: 70 });
    const pill = $('pill'), p = eOutBack(lin(t, 24.45, 24.95));
    pill.style.opacity = clamp(lin(t, 24.45, 24.6)).toFixed(3);
    pill.style.transform = `scale(${mix(0.7, 1, p).toFixed(3)})`;
    pill.style.setProperty('--ang', ((t - 24) * 160).toFixed(1) + 'deg');
    pill.style.boxShadow = `0 0 ${(40 + 30 * kickPulse(t)).toFixed(0)}px rgba(139,92,246,.6), 0 20px 60px rgba(0,0,0,.6)`;
    cta.style.opacity = (1 - lin(t, 26.7, 27)).toFixed(3);
  }
}

function updateOverlays(t) {
  let f = 0;
  for (const [ht, a] of HITS) if (t >= ht) f = Math.max(f, a * Math.exp(-(t - ht) * (a > 0.3 ? 9 : 16)));
  $('flash').style.opacity = f.toFixed(3);
  $('fade').style.opacity = lin(t, 26.75, 27).toFixed(3);
}

window.seek = function (t) {
  drawBg(t);
  updateHook(t);
  placeLogo(t);
  updatePhone(t);
  updateScreens(t);
  updateFloats(t);
  updateCaptions(t);
  drawFx(t);
  updateOverlays(t);
};

window.ready = document.fonts.ready.then(() => {
  build();
  const q = new URLSearchParams(location.search).get('t');
  window.seek(q ? parseFloat(q) : 0);
  return true;
});
