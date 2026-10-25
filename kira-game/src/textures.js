// Процедурные текстуры на canvas
import * as THREE from 'three';

function tex(w, h, draw, repeat) {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  draw(c.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 8;
  if (repeat) {
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.repeat.set(...repeat);
  }
  t.userData.canvas = c;
  return t;
}

const rnd = (a, b) => a + Math.random() * (b - a);

export const T = {
  wood: (r) => tex(1024, 1024, (x, w, h) => {
    const rows = 8;
    const ph = h / rows;
    for (let i = 0; i < rows; i++) {
      const y = i * ph;
      const off = ((i * 377) % 5) * 90;
      for (let j = -1; j < 3; j++) {
        const px = j * 460 + off;
        const hue = 24 + rnd(-4, 4), sat = 40 + rnd(-8, 6), lit = 34 + rnd(-6, 6);
        const g = x.createLinearGradient(px, y, px + 460, y + ph);
        g.addColorStop(0, `hsl(${hue},${sat}%,${lit}%)`);
        g.addColorStop(1, `hsl(${hue + 2},${sat - 4}%,${lit - 4}%)`);
        x.fillStyle = g;
        x.fillRect(px, y, 460, ph);
        // волокна
        for (let k = 0; k < 40; k++) {
          x.strokeStyle = `rgba(${Math.random() < 0.5 ? '40,20,5' : '255,220,180'},${rnd(0.03, 0.09)})`;
          x.lineWidth = rnd(0.6, 2.2);
          x.beginPath();
          const gy = y + rnd(2, ph - 2);
          x.moveTo(px, gy);
          x.bezierCurveTo(px + 150, gy + rnd(-6, 6), px + 300, gy + rnd(-6, 6), px + 460, gy + rnd(-3, 3));
          x.stroke();
        }
        // сучок
        if (Math.random() < 0.35) {
          const kx = px + rnd(60, 400), ky = y + rnd(20, ph - 20);
          for (let r2 = 14; r2 > 2; r2 -= 3) { x.strokeStyle = `rgba(50,25,8,${0.12})`; x.lineWidth = 1.5; x.beginPath(); x.ellipse(kx, ky, r2 * 2.2, r2 * 0.7, 0, 0, Math.PI * 2); x.stroke(); }
        }
        x.fillStyle = 'rgba(25,12,3,0.55)';
        x.fillRect(px, y, 3, ph);
      }
      x.fillStyle = 'rgba(25,12,3,0.5)';
      x.fillRect(0, y, w, 3);
      x.fillStyle = 'rgba(255,230,200,0.08)';
      x.fillRect(0, y + 3, w, 2);
    }
  }, r),

  wallpaper: (r, hue = 265) => tex(512, 512, (x, w, h) => {
    x.fillStyle = `hsl(${hue},24%,86%)`;
    x.fillRect(0, 0, w, h);
    // мягкие вертикальные полосы
    for (let i = 0; i < w; i += 64) { x.fillStyle = `hsla(${hue},30%,70%,0.10)`; x.fillRect(i, 0, 24, h); }
    // мелкие сердечки и точки
    for (let i = 0; i < 8; i++) for (let j = 0; j < 8; j++) {
      const cx = i * 64 + (j % 2) * 32 + 44, cy = j * 64 + 32;
      x.fillStyle = `hsla(${hue},45%,62%,0.28)`;
      x.beginPath();
      x.moveTo(cx, cy + 5);
      x.bezierCurveTo(cx - 8, cy - 1, cx - 3, cy - 8, cx, cy - 3);
      x.bezierCurveTo(cx + 3, cy - 8, cx + 8, cy - 1, cx, cy + 5);
      x.fill();
    }
    x.globalAlpha = 0.05;
    for (let i = 0; i < 4000; i++) { x.fillStyle = Math.random() > 0.5 ? '#fff' : '#000'; x.fillRect(rnd(0, w), rnd(0, h), 1, 1); }
    x.globalAlpha = 1;
  }, r),

  tiles: (r, color = '#f4f2f8', grout = '#c8c4d4', n = 8) => tex(256, 256, (x, w, h) => {
    x.fillStyle = grout;
    x.fillRect(0, 0, w, h);
    const s = w / n;
    for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
      x.fillStyle = color;
      x.globalAlpha = 0.9 + Math.random() * 0.1;
      x.fillRect(i * s + 1.5, j * s + 1.5, s - 3, s - 3);
    }
    x.globalAlpha = 1;
  }, r),

  checker: (r) => tex(256, 256, (x, w, h) => {
    for (let i = 0; i < 8; i++) for (let j = 0; j < 8; j++) {
      x.fillStyle = (i + j) % 2 ? '#2a2733' : '#e9e4f2';
      x.fillRect(i * 32, j * 32, 32, 32);
    }
  }, r),

  rug: () => tex(512, 512, (x, w, h) => {
    x.fillStyle = '#5b3d8f';
    x.beginPath(); x.arc(256, 256, 250, 0, Math.PI * 2); x.fill();
    for (let i = 0; i < 6; i++) {
      x.strokeStyle = i % 2 ? '#7d5cc0' : '#3f2a66';
      x.lineWidth = 14;
      x.beginPath(); x.arc(256, 256, 220 - i * 36, 0, Math.PI * 2); x.stroke();
    }
    x.fillStyle = '#c9a8ff';
    x.font = 'bold 60px Arial';
    x.textAlign = 'center';
    x.fillText('♥', 256, 278);
  }),

  poster: (title, sub, c1, c2) => tex(256, 360, (x, w, h) => {
    const g = x.createLinearGradient(0, 0, w, h);
    g.addColorStop(0, c1); g.addColorStop(1, c2);
    x.fillStyle = g; x.fillRect(0, 0, w, h);
    x.strokeStyle = '#ffffff'; x.lineWidth = 6; x.strokeRect(10, 10, w - 20, h - 20);
    x.fillStyle = 'rgba(255,255,255,0.15)';
    for (let i = 0; i < 12; i++) { x.beginPath(); x.arc(rnd(0, w), rnd(0, h), rnd(5, 40), 0, Math.PI * 2); x.fill(); }
    x.fillStyle = '#fff';
    x.textAlign = 'center';
    x.font = 'bold 64px Arial';
    x.fillText(title, w / 2, h / 2);
    x.font = '22px Arial';
    x.fillText(sub, w / 2, h / 2 + 40);
  }),

  screen: () => tex(512, 288, (x, w, h) => { x.fillStyle = '#05040a'; x.fillRect(0, 0, w, h); }),

  book: () => tex(128, 128, (x, w, h) => {
    const cols = ['#6b4fa8', '#a33b5c', '#2f6d8c', '#d4a43a', '#3a8c5c', '#1d1b24', '#c36ad8'];
    let px = 0;
    while (px < w) {
      const bw = rnd(8, 16);
      x.fillStyle = cols[Math.floor(Math.random() * cols.length)];
      const bh = rnd(90, 120);
      x.fillRect(px, h - bh, bw - 1, bh);
      x.fillStyle = 'rgba(255,255,255,0.4)';
      x.fillRect(px + 2, h - bh + 10, bw - 5, 3);
      px += bw;
    }
  }),

  fabric: (color, r) => tex(256, 256, (x, w, h) => {
    x.fillStyle = color; x.fillRect(0, 0, w, h);
    // плетение
    for (let i = 0; i < w; i += 4) {
      x.fillStyle = `rgba(255,255,255,${i % 8 ? 0.04 : 0.07})`; x.fillRect(i, 0, 2, h);
      x.fillStyle = `rgba(0,0,0,${i % 8 ? 0.05 : 0.08})`; x.fillRect(0, i, w, 2);
    }
    x.globalAlpha = 0.07;
    for (let i = 0; i < 1500; i++) { x.fillStyle = Math.random() > 0.5 ? '#fff' : '#000'; x.fillRect(rnd(0, w), rnd(0, h), 1.5, 1.5); }
    x.globalAlpha = 1;
  }, r),

  sky: (top, mid, bot, stars) => tex(512, 512, (x, w, h) => {
    const g = x.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, top); g.addColorStop(0.55, mid); g.addColorStop(1, bot);
    x.fillStyle = g; x.fillRect(0, 0, w, h);
    if (stars) {
      for (let i = 0; i < 260; i++) {
        x.fillStyle = `rgba(255,255,255,${rnd(0.2, 1)})`;
        const s = Math.random() < 0.1 ? 2 : 1;
        x.fillRect(rnd(0, w), rnd(0, h * 0.6), s, s);
      }
    }
  }),

  label: (text, bg = '#1d1b24', fg = '#fff', w = 256, h = 64, font = 'bold 38px Arial') => tex(w, h, (x) => {
    x.fillStyle = bg; x.fillRect(0, 0, w, h);
    x.fillStyle = fg; x.font = font; x.textAlign = 'center'; x.textBaseline = 'middle';
    x.fillText(text, w / 2, h / 2 + 2);
  }),

  glitch: () => tex(256, 256, (x, w, h) => {
    for (let i = 0; i < 300; i++) {
      x.fillStyle = `hsl(${Math.random() > 0.5 ? 280 : 330},90%,${rnd(10, 60)}%)`;
      x.fillRect(rnd(0, w), rnd(0, h), rnd(4, 80), rnd(2, 10));
    }
  }),
};
