// 3D-модель Киры, собранная из примитивов: toon-шейдинг, контур, рисованное
// анимешное лицо с эмоциями, процедурные позы, ходьба и взгляд на игрока.
import * as THREE from 'three';

const C = {
  skin: 0xffd9c9,
  skinShade: 0xf2b8a8,
  hair: 0x1d1622,
  purple: 0x8c4dff,
  purpleLight: 0xb48cff,
  cloth: 0x1d1b24,
  cloth2: 0x2a2733,
  silver: 0xc5c3d3,
};

let gradientMap;
function toon(color, extra = {}) {
  if (!gradientMap) {
    const data = new Uint8Array([90, 90, 90, 255, 175, 175, 175, 255, 255, 255, 255, 255]);
    gradientMap = new THREE.DataTexture(data, 3, 1, THREE.RGBAFormat);
    gradientMap.minFilter = gradientMap.magFilter = THREE.NearestFilter;
    gradientMap.needsUpdate = true;
  }
  return new THREE.MeshToonMaterial({ color, gradientMap, ...extra });
}

const outlineMat = new THREE.ShaderMaterial({
  uniforms: { thickness: { value: 0.0055 }, color: { value: new THREE.Color(0x150f1c) } },
  vertexShader: `uniform float thickness;
    void main(){ vec3 p = position + normal * thickness;
      gl_Position = projectionMatrix * modelViewMatrix * vec4(p,1.0); }`,
  fragmentShader: `uniform vec3 color; void main(){ gl_FragColor = vec4(color,1.0); }`,
  side: THREE.BackSide,
});

function addOutline(mesh, k = 1) {
  const m = k === 1 ? outlineMat : outlineMat.clone();
  if (k !== 1) m.uniforms.thickness.value *= k;
  const o = new THREE.Mesh(mesh.geometry, m);
  o.raycast = () => {};
  mesh.add(o);
  return mesh;
}

function mesh(geo, mat, parent, pos, rot, scale, outline = true) {
  const m = new THREE.Mesh(geo, mat);
  if (pos) m.position.set(...pos);
  if (rot) m.rotation.set(...rot);
  if (scale) m.scale.set(...scale);
  m.castShadow = true;
  if (outline) addOutline(m);
  parent.add(m);
  return m;
}

function bone(parent, x, y, z) {
  const b = new THREE.Group();
  b.position.set(x, y, z);
  parent.add(b);
  return b;
}

// Конус-прядь волос с вертикальным градиентом цвета (чёрный -> фиолетовый)
function strandGeo(rTop, rBot, len, purpleFrom = 0.55, seg = 8) {
  const g = new THREE.CylinderGeometry(rTop, rBot, len, seg, 6, false);
  g.translate(0, -len / 2, 0);
  const col = [];
  const a = new THREE.Color(C.hair);
  const b = new THREE.Color(C.purple);
  const pos = g.attributes.position;
  for (let i = 0; i < pos.count; i++) {
    const t = -pos.getY(i) / len; // 0 сверху, 1 снизу
    const k = THREE.MathUtils.smoothstep(t, purpleFrom, 1.0);
    const c = a.clone().lerp(b, k);
    col.push(c.r, c.g, c.b);
  }
  g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
  return g;
}

// ---------- Лицо ----------
const FW = 512, FH = 336;

function drawFace(ctx, st) {
  ctx.clearRect(0, 0, FW, FH);
  const eyeY = 150, ex = 100, cx = 256;
  const e = st.eyes, m = st.mouth;
  // румянец
  if (st.blush > 0) {
    for (const s of [-1, 1]) {
      const g = ctx.createRadialGradient(cx + s * 128, 212, 2, cx + s * 128, 212, 42);
      g.addColorStop(0, `rgba(255,110,140,${0.55 * st.blush})`);
      g.addColorStop(1, 'rgba(255,110,140,0)');
      ctx.fillStyle = g;
      ctx.fillRect(cx + s * 128 - 50, 165, 100, 95);
      ctx.strokeStyle = `rgba(230,80,110,${0.6 * st.blush})`;
      ctx.lineWidth = 2.5;
      for (let i = 0; i < 3; i++) {
        ctx.beginPath();
        ctx.moveTo(cx + s * 128 - 16 + i * 12, 220);
        ctx.lineTo(cx + s * 128 - 8 + i * 12, 205);
        ctx.stroke();
      }
    }
  }
  // брови
  ctx.strokeStyle = '#2a1d2e';
  ctx.lineWidth = 6;
  ctx.lineCap = 'round';
  for (const s of [-1, 1]) {
    const tilt = st.brow * s; // >0 злость, <0 грусть
    ctx.beginPath();
    ctx.moveTo(cx + s * (ex - 38), 92 + st.browY - tilt * 8);
    ctx.quadraticCurveTo(cx + s * ex, 82 + st.browY, cx + s * (ex + 38), 92 + st.browY + tilt * 8);
    ctx.stroke();
  }
  // глаза
  for (const s of [-1, 1]) {
    const x = cx + s * ex;
    let state = e;
    if (st.blink) state = 'closed';
    if (e === 'wink' && !st.blink) state = s === 1 ? 'closed' : 'open';
    drawEye(ctx, x, eyeY, state, s, st);
  }
  // слёзы
  if (st.tears) {
    ctx.fillStyle = 'rgba(140,200,255,0.85)';
    for (const s of [-1, 1]) {
      ctx.beginPath();
      ctx.ellipse(cx + s * (ex + 20), eyeY + 55, 7, 16, 0, 0, Math.PI * 2);
      ctx.fill();
    }
  }
  drawMouth(ctx, cx, 238, st.talkOpen ? openVariant(m) : m);
  if (st.glitch) {
    for (let i = 0; i < 6; i++) {
      ctx.fillStyle = `rgba(${Math.random() > 0.5 ? 255 : 0},0,${200 + Math.random() * 55},0.5)`;
      ctx.fillRect(Math.random() * FW, Math.random() * FH, 40 + Math.random() * 200, 4 + Math.random() * 12);
    }
  }
}

function openVariant(m) {
  return { smile: 'open', smileClosed: 'open', cat: 'catOpen', flat: 'o', sad: 'sadOpen', pout: 'o', o: 'oBig', open: 'smile', grin: 'open' }[m] || 'open';
}

function drawEye(ctx, x, y, state, s, st) {
  ctx.lineCap = 'round';
  if (state === 'closed' || state === 'happy') {
    ctx.strokeStyle = '#1b1220';
    ctx.lineWidth = 7;
    ctx.beginPath();
    if (state === 'happy') ctx.arc(x, y + 14, 30, Math.PI * 1.15, Math.PI * 1.85);
    else ctx.arc(x, y - 6, 32, Math.PI * 0.2, Math.PI * 0.8);
    ctx.stroke();
    // ресницы
    ctx.lineWidth = 4;
    ctx.beginPath();
    ctx.moveTo(x + s * 30, y + (state === 'happy' ? 0 : 12));
    ctx.lineTo(x + s * 42, y + (state === 'happy' ? -8 : 6));
    ctx.stroke();
    return;
  }
  const h = state === 'half' ? 0.55 : state === 'wide' ? 1.12 : 1;
  const w = 40, hh = 52 * h;
  // белок
  ctx.save();
  ctx.beginPath();
  ctx.ellipse(x, y + (1 - h) * 20, w, hh, 0, 0, Math.PI * 2);
  ctx.fillStyle = '#fffaff';
  ctx.fill();
  ctx.clip();
  // радужка
  const ix = x + st.lookX * 8, iy = y + 4 + st.lookY * 6;
  const g = ctx.createLinearGradient(0, iy - 36, 0, iy + 36);
  g.addColorStop(0, '#3a1d6e');
  g.addColorStop(0.55, '#8c4dff');
  g.addColorStop(1, '#d8b8ff');
  ctx.fillStyle = g;
  ctx.beginPath();
  ctx.ellipse(ix, iy, 30, 41, 0, 0, Math.PI * 2);
  ctx.fill();
  if (state === 'heart') {
    ctx.fillStyle = '#ff5fa8';
    heart(ctx, ix, iy + 2, 15);
  } else if (state === 'glitch') {
    ctx.fillStyle = '#ff2a5a';
    ctx.fillRect(ix - 20, iy - 3, 40, 6);
    ctx.fillRect(ix - 3, iy - 20, 6, 40);
  } else {
    ctx.fillStyle = '#1c0b33';
    ctx.beginPath();
    ctx.ellipse(ix, iy + 2, 13, 19, 0, 0, Math.PI * 2);
    ctx.fill();
  }
  // блики
  ctx.fillStyle = '#ffffff';
  ctx.beginPath();
  ctx.ellipse(ix - 9, iy - 14, 8, 10, -0.4, 0, Math.PI * 2);
  ctx.fill();
  ctx.beginPath();
  ctx.arc(ix + 10, iy + 14, 4, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
  // верхнее веко и ресницы
  ctx.strokeStyle = '#140c18';
  ctx.lineWidth = 8;
  ctx.beginPath();
  ctx.ellipse(x, y + (1 - h) * 20, w + 2, hh + 1, 0, Math.PI * 1.08, Math.PI * 1.92);
  ctx.stroke();
  ctx.lineWidth = 4;
  ctx.beginPath();
  ctx.moveTo(x + s * (w - 2), y - hh * 0.55);
  ctx.lineTo(x + s * (w + 13), y - hh * 0.75);
  ctx.stroke();
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.ellipse(x, y + (1 - h) * 20, w, hh, 0, Math.PI * 0.25, Math.PI * 0.75);
  ctx.stroke();
}

function heart(ctx, x, y, r) {
  ctx.beginPath();
  ctx.moveTo(x, y + r * 0.9);
  ctx.bezierCurveTo(x - r * 1.6, y - r * 0.2, x - r * 0.6, y - r * 1.4, x, y - r * 0.4);
  ctx.bezierCurveTo(x + r * 0.6, y - r * 1.4, x + r * 1.6, y - r * 0.2, x, y + r * 0.9);
  ctx.fill();
}

function drawMouth(ctx, x, y, m) {
  ctx.strokeStyle = '#5a2232';
  ctx.fillStyle = '#7a2a3e';
  ctx.lineWidth = 5;
  ctx.lineCap = 'round';
  ctx.beginPath();
  switch (m) {
    case 'smile': case 'smileClosed':
      ctx.arc(x, y - 14, 18, Math.PI * 0.2, Math.PI * 0.8); ctx.stroke(); break;
    case 'flat':
      ctx.moveTo(x - 12, y); ctx.lineTo(x + 12, y); ctx.stroke(); break;
    case 'sad':
      ctx.arc(x, y + 14, 16, Math.PI * 1.2, Math.PI * 1.8); ctx.stroke(); break;
    case 'sadOpen':
      ctx.ellipse(x, y + 4, 12, 9, 0, 0, Math.PI * 2); ctx.fill(); break;
    case 'pout':
      ctx.moveTo(x - 14, y + 2); ctx.quadraticCurveTo(x - 7, y - 6, x, y + 2);
      ctx.quadraticCurveTo(x + 7, y - 6, x + 14, y + 2); ctx.stroke(); break;
    case 'cat':
      ctx.moveTo(x - 20, y - 4); ctx.quadraticCurveTo(x - 10, y + 8, x, y - 2);
      ctx.quadraticCurveTo(x + 10, y + 8, x + 20, y - 4); ctx.stroke(); break;
    case 'catOpen':
      ctx.moveTo(x - 18, y - 4); ctx.quadraticCurveTo(x, y + 26, x + 18, y - 4); ctx.closePath(); ctx.fill(); ctx.stroke(); break;
    case 'o':
      ctx.ellipse(x, y + 2, 8, 10, 0, 0, Math.PI * 2); ctx.fill(); break;
    case 'oBig':
      ctx.ellipse(x, y + 4, 12, 15, 0, 0, Math.PI * 2); ctx.fill(); break;
    case 'grin':
      ctx.moveTo(x - 24, y - 8); ctx.quadraticCurveTo(x, y + 16, x + 24, y - 8); ctx.stroke(); break;
    case 'open': default: {
      ctx.moveTo(x - 24, y - 10);
      ctx.quadraticCurveTo(x, y - 6, x + 24, y - 10);
      ctx.quadraticCurveTo(x + 10, y + 26, x, y + 26);
      ctx.quadraticCurveTo(x - 10, y + 26, x - 24, y - 10);
      ctx.fill();
      ctx.fillStyle = '#ff8a9a';
      ctx.beginPath();
      ctx.ellipse(x, y + 16, 11, 7, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(x - 16, y - 9, 32, 5);
    }
  }
}

const EMOTIONS = {
  normal:   { eyes: 'open', mouth: 'smile', blush: 0.15, brow: 0, browY: 0 },
  happy:    { eyes: 'happy', mouth: 'open', blush: 0.35, brow: 0, browY: -4 },
  laugh:    { eyes: 'happy', mouth: 'open', blush: 0.5, brow: -0.2, browY: -6 },
  wink:     { eyes: 'wink', mouth: 'open', blush: 0.35, brow: 0, browY: 0 },
  smile:    { eyes: 'open', mouth: 'cat', blush: 0.3, brow: 0, browY: 0 },
  shy:      { eyes: 'half', mouth: 'smile', blush: 1, brow: -0.4, browY: 2, lookX: 1, lookY: 0.6 },
  love:     { eyes: 'heart', mouth: 'open', blush: 1, brow: -0.2, browY: -4 },
  sad:      { eyes: 'half', mouth: 'sad', blush: 0.1, brow: -1, browY: 4, lookY: 0.8 },
  cry:      { eyes: 'half', mouth: 'sadOpen', blush: 0.5, brow: -1, browY: 4, tears: true },
  surprised:{ eyes: 'wide', mouth: 'oBig', blush: 0.2, brow: 0, browY: -10 },
  pout:     { eyes: 'half', mouth: 'pout', blush: 0.6, brow: 0.8, browY: 0 },
  angry:    { eyes: 'open', mouth: 'flat', blush: 0.3, brow: 1, browY: 4 },
  smug:     { eyes: 'half', mouth: 'cat', blush: 0.3, brow: 0.3, browY: 0 },
  worried:  { eyes: 'open', mouth: 'flat', blush: 0.1, brow: -0.8, browY: 0 },
  sleepy:   { eyes: 'half', mouth: 'o', blush: 0.2, brow: -0.3, browY: 4 },
  think:    { eyes: 'open', mouth: 'flat', blush: 0.1, brow: 0.3, browY: -2, lookX: -0.8, lookY: -0.8 },
  glitch:   { eyes: 'glitch', mouth: 'flat', blush: 0, brow: 0, browY: 0, glitch: true },
  closed:   { eyes: 'closed', mouth: 'smile', blush: 0.5, brow: 0, browY: 0 },
};

// ---------- Позы (углы в радианах) ----------
const P = (o) => o;
const POSES = {
  idle: P({ armL: [0, 0, -1.3], armR: [0, 0, 1.3], foreL: [0, -0.15, -0.1], foreR: [0, 0.15, 0.1] }),
  behind: P({ armL: [0.45, 0, -1.25], armR: [0.45, 0, 1.25], foreL: [0, 1.0, 0], foreR: [0, -1.0, 0], chest: [-0.05, 0, 0] }),
  wave: P({ armL: [0, 0, -1.3], armR: [0, 0, -0.35], foreR: [0, 0, -1.3], foreL: [0, -0.15, -0.1], head: [0, 0, 0.12], wave: 1 }),
  point: P({ armL: [0, 0, -1.3], armR: [-0.63, 0, 1.21], foreR: [0, 2.26, -0.08], foreL: [0, -0.15, -0.1], finger: 1, head: [0.05, 0, 0.18], chest: [0.12, 0, 0.05] }),
  peace: P({ armL: [0, 0, -1.3], armR: [-0.4, 0, 0.9], foreR: [0, 2.2, -0.3], foreL: [0, -0.15, -0.1], finger: 2, head: [0, 0, 0.2] }),
  think: P({ armL: [-0.71, 0, -1.255], armR: [-0.4, 0, 1.25], foreL: [0, -2.3, 0.1], foreR: [0, 1.2, 0], head: [0.08, 0, -0.15] }),
  shy: P({ armL: [-0.25, 0, -1.3], armR: [-0.25, 0, 1.3], foreL: [0, -1.1, -0.15], foreR: [0, 1.1, 0.15], head: [0.2, 0, 0.14], chest: [0.08, 0, 0] }),
  sad: P({ armL: [-0.15, 0, -1.3], armR: [-0.15, 0, 1.3], foreL: [0, -0.9, -0.1], foreR: [0, 0.9, 0.1], head: [0.4, 0, 0], chest: [0.15, 0, 0] }),
  cheer: P({ armL: [0, 0, 1.0], armR: [0, 0, -1.0], foreL: [0, 0, 0.3], foreR: [0, 0, -0.3], head: [-0.1, 0, 0] }),
  hug: P({ armL: [-1.3, 0, -1.15], armR: [-1.3, 0, 1.15], foreL: [0, -0.8, 0], foreR: [0, 0.8, 0], chest: [0.1, 0, 0] }),
  hold: P({ armL: [-0.5, 0, -1.25], armR: [-0.5, 0, 1.25], foreL: [0, -1.35, 0], foreR: [0, 1.35, 0], head: [0.25, 0, 0] }),
  cook: P({ armL: [-0.75, 0, -1.2], armR: [-0.75, 0, 1.2], foreL: [0, -1.0, 0], foreR: [0, 1.0, 0], head: [0.35, 0, 0], chest: [0.1, 0, 0] }),
  pout: P({ armL: [-0.55, 0, -1.25], armR: [-0.55, 0, 1.25], foreL: [0, -1.75, 0.1], foreR: [0, 1.75, -0.1], head: [-0.05, 0, 0.1] }),
  tpose: P({ armL: [0, 0, 0], armR: [0, 0, 0], foreL: [0, 0, 0], foreR: [0, 0, 0], head: [0, 0, 0] }),
  sit: P({ sit: 1, armL: [-0.45, 0, -1.3], armR: [-0.45, 0, 1.3], foreL: [0, -0.7, -0.2], foreR: [0, 0.7, 0.2] }),
  sitHold: P({ sit: 1, armL: [-0.5, 0, -1.25], armR: [-0.5, 0, 1.25], foreL: [0, -1.35, 0], foreR: [0, 1.35, 0], head: [0.25, 0, 0] }),
  sitLean: P({ sit: 1, lean: 1, armL: [-0.45, 0, -1.3], armR: [-0.45, 0, 1.3], foreL: [0, -0.7, -0.2], foreR: [0, 0.7, 0.2], head: [0.1, 0, -0.4], chest: [0, 0, -0.22] }),
  sitFloor: P({ sit: 1, floor: 1, armL: [0.3, 0, -1.3], armR: [0.3, 0, 1.3], foreL: [0, 0, 0], foreR: [0, 0, 0] }),
  sitFloorLean: P({ sit: 1, floor: 1, armL: [0.3, 0, -1.3], armR: [-0.45, 0, 1.3], foreL: [0, 0, 0], foreR: [0, 0.7, 0.2], head: [0.1, 0, -0.45], chest: [0, 0, -0.25] }),
  crouch: P({ crouch: 1, armL: [-1.0, 0, -1.2], armR: [-1.0, 0, 1.2], foreL: [0, -0.9, 0], foreR: [0, 0.9, 0], head: [0.1, 0, 0] }),
  dance: P({ dance: 1, armL: [0, 0, -0.6], armR: [0, 0, 0.6], foreL: [0, 0, 0.8], foreR: [0, 0, -0.8] }),
  lie: P({ lie: 1, armL: [0, 0, -1.4], armR: [0, 0, 1.4], foreL: [0, 0, 0], foreR: [0, 0, 0] }),
};

export class Kira {
  constructor(scene) {
    this.scene = scene;
    this.root = new THREE.Group();
    this.root.name = 'Kira';
    scene.add(this.root);
    this.emotion = 'normal';
    this.face = { ...EMOTIONS.normal, lookX: 0, lookY: 0, blink: false, talkOpen: false, tears: false, glitch: false };
    this.pose = 'idle';
    this.poseT = 0;
    this.time = 0;
    this.blinkT = 2;
    this.talking = false;
    this.path = [];
    this.speed = 1.5;
    this.moving = false;
    this.lookTarget = null; // Vector3
    this.headYaw = 0;
    this.headPitch = 0;
    this.facing = 0;
    this.targetFacing = null;
    this.clipColor = C.silver;
    this.build();
    this.setEmotion('normal');
  }

  build() {
    const skin = toon(C.skin);
    const cloth = toon(C.cloth);
    const cloth2 = toon(C.cloth2);
    const purple = toon(C.purple);
    const hairMat = toon(0xffffff, { vertexColors: true });
    const hairSolid = toon(C.hair);
    const silver = toon(C.silver);
    this.mats = { skin, cloth, purple, hairMat, silver };

    const root = this.root;
    const body = this.body = bone(root, 0, 0, 0);
    const hips = this.hips = bone(body, 0, 0.86, 0);
    const spine = this.spine = bone(hips, 0, 0.04, 0);
    const chest = this.chest = bone(spine, 0, 0.2, 0);
    const neck = this.neck = bone(chest, 0, 0.24, 0);
    const head = this.head = bone(neck, 0, 0.1, 0);
    head.scale.setScalar(1.12);

    // --- таз / шорты ---
    mesh(new THREE.SphereGeometry(1, 20, 14), cloth, hips, [0, -0.02, 0], null, [0.155, 0.11, 0.115]);
    // ремень
    const belt = mesh(new THREE.TorusGeometry(0.152, 0.012, 6, 28), cloth2, hips, [0, 0.05, 0], [Math.PI / 2, 0, 0], [1, 0.76, 1], false);
    mesh(new THREE.BoxGeometry(0.035, 0.03, 0.01), silver, hips, [0.04, 0.05, 0.118], null, null, false);
    // талия (открытый живот)
    mesh(new THREE.CylinderGeometry(0.098, 0.13, 0.2, 18), skin, spine, [0, 0.05, 0], null, [1, 1, 0.78]);
    // грудная клетка + кроп-топ
    mesh(new THREE.CylinderGeometry(0.135, 0.105, 0.2, 20), skin, chest, [0, 0.11, 0], null, [1, 1, 0.75]);
    const top = mesh(new THREE.SphereGeometry(1, 22, 16), cloth, chest, [0, 0.07, 0.012], null, [0.138, 0.095, 0.112]);
    // бюст — аккуратно и сдержанно
    for (const s of [-1, 1]) mesh(new THREE.SphereGeometry(0.058, 16, 12), cloth, chest, [s * 0.055, 0.085, 0.068], null, [1, 0.9, 0.85]);
    // надпись QVK на топе
    const label = new THREE.Mesh(new THREE.PlaneGeometry(0.07, 0.022), new THREE.MeshBasicMaterial({ map: labelTex('QVK'), transparent: true }));
    label.position.set(0, 0.045, 0.111);
    chest.add(label);
    // бретели
    for (const s of [-1, 1]) {
      mesh(new THREE.BoxGeometry(0.012, 0.13, 0.012), cloth, chest, [s * 0.075, 0.18, 0.06], [0.25, 0, s * 0.18], null, false);
      mesh(new THREE.BoxGeometry(0.012, 0.13, 0.012), cloth, chest, [s * 0.075, 0.18, -0.06], [-0.25, 0, s * 0.18], null, false);
    }
    // шея, чокер
    mesh(new THREE.CylinderGeometry(0.038, 0.045, 0.12, 12), skin, neck, [0, 0.02, 0]);
    mesh(new THREE.TorusGeometry(0.042, 0.009, 6, 20), cloth, neck, [0, 0.0, 0], [Math.PI / 2, 0, 0], null, false);
    mesh(new THREE.TorusGeometry(0.009, 0.003, 6, 12), silver, neck, [0, -0.012, 0.047], null, null, false);
    const cross = new THREE.Group();
    cross.position.set(0, -0.035, 0.049);
    neck.add(cross);
    mesh(new THREE.BoxGeometry(0.028, 0.007, 0.004), silver, cross, null, [0, 0, 0.785], null, false);
    mesh(new THREE.BoxGeometry(0.028, 0.007, 0.004), silver, cross, null, [0, 0, -0.785], null, false);

    // --- голова ---
    const skull = mesh(new THREE.SphereGeometry(0.128, 32, 24), skin, head, [0, 0.11, 0], null, [1, 1.04, 1.0]);
    // подбородок
    mesh(new THREE.SphereGeometry(0.07, 18, 12), skin, head, [0, 0.05, 0.03], null, [1, 0.85, 0.85]);
    // лицо (канвас на сферическом «патче»)
    this.faceCanvas = document.createElement('canvas');
    this.faceCanvas.width = FW;
    this.faceCanvas.height = FH;
    this.faceCtx = this.faceCanvas.getContext('2d');
    this.faceTex = new THREE.CanvasTexture(this.faceCanvas);
    this.faceTex.colorSpace = THREE.SRGBColorSpace;
    this.faceTex.anisotropy = 4;
    const fg = new THREE.SphereGeometry(0.1305, 32, 24, Math.PI / 2 - 1.0, 2.0, Math.PI / 2 - 0.55, 1.3);
    const faceMesh = new THREE.Mesh(fg, new THREE.MeshBasicMaterial({ map: this.faceTex, transparent: true, depthWrite: false }));
    faceMesh.position.set(0, 0.11, 0.004);
    faceMesh.scale.set(1, 1.04, 1);
    faceMesh.renderOrder = 2;
    head.add(faceMesh);
    // уши + серьги
    for (const s of [-1, 1]) {
      mesh(new THREE.SphereGeometry(0.025, 10, 8), skin, head, [s * 0.126, 0.1, 0.0], null, [0.5, 1, 0.8]);
      mesh(new THREE.TorusGeometry(0.008, 0.0025, 6, 12), silver, head, [s * 0.13, 0.075, 0.005], [0, Math.PI / 2, 0], null, false);
    }

    // --- волосы ---
    const hair = this.hair = bone(head, 0, 0.11, 0);
    // «шапка» волос (макушка)
    mesh(new THREE.SphereGeometry(0.142, 28, 12, 0, Math.PI * 2, 0, Math.PI * 0.36), hairSolid, hair, [0, 0.0, -0.004], null, [1.04, 1.06, 1.06]);
    // бока и затылок (спереди вырез под лицо)
    mesh(new THREE.SphereGeometry(0.148, 28, 18, Math.PI / 2 + Math.PI * 0.3, Math.PI * 1.4, Math.PI * 0.18, Math.PI * 0.6), hairSolid, hair, [0, 0.0, -0.008], null, [1.05, 1.06, 1.06]);
    // чёлка
    const bangs = [[-0.09, 0.085], [-0.06, 0.1], [-0.025, 0.105], [0.012, 0.1], [0.048, 0.104], [0.08, 0.09], [0.1, 0.075]];
    bangs.forEach(([x, l], i) => {
      const g = strandGeo(0.03, 0.006, l, 2.0, 6);
      const z = 0.112 - Math.abs(x) * 0.25;
      const m = mesh(g, hairMat, hair, [x, 0.1, z], null, [1, 1, 0.5]);
      m.rotation.order = 'YXZ';
      m.rotation.set(-0.38 - (i % 2) * 0.06, x * 2.2, -x * 1.2);
    });
    // пряди по кругу с фиолетовыми кончиками
    for (let i = 0; i < 24; i++) {
      const a = (i / 24) * Math.PI * 2;
      if (Math.cos(a) > 0.45) continue; // лицо открыто
      const back = Math.cos(a) < -0.3;
      const len = 0.2 + (back ? 0.06 : 0.02) + Math.random() * 0.04;
      const g = strandGeo(0.058, 0.01, len, 0.45, 7);
      const x = Math.sin(a) * 0.128, z = Math.cos(a) * 0.12;
      const m = mesh(g, hairMat, hair, [x, 0.04, z - 0.01], null, [1, 1, 0.42]);
      m.rotation.order = 'YXZ';
      m.rotation.y = a;
      m.rotation.x = -0.28;
      m.userData.sway = Math.random() * 6;
    }
    // передние боковые локоны
    let i0 = 0;
    for (const s of [-1, 1]) {
      const g = strandGeo(0.035, 0.01, 0.26, 0.4, 7);
      const m = mesh(g, hairMat, hair, [s * 0.112, 0.05, 0.065], [-0.1, 0, s * 0.18], [1, 1, 0.55]);
      m.userData.sway = i0++;
    }
    // заколки-крестики
    this.clips = [];
    const clipMat = toon(this.clipColor);
    this.clipMat = clipMat;
    [[0.075, 0.115, 0.1, 1], [0.1, 0.075, 0.095, 0.7], [0.055, 0.15, 0.08, 0.5]].forEach(([x, y, z, k]) => {
      const g = new THREE.Group();
      g.position.set(x, y, z);
      g.rotation.set(-0.5, 0.5, 0);
      g.scale.setScalar(k);
      hair.add(g);
      mesh(new THREE.BoxGeometry(0.045, 0.009, 0.006), clipMat, g, null, [0, 0, 0.785], null, false);
      mesh(new THREE.BoxGeometry(0.045, 0.009, 0.006), clipMat, g, null, [0, 0, -0.785], null, false);
    });

    // --- руки ---
    this.arm = {};
    for (const side of ['L', 'R']) {
      const s = side === 'L' ? 1 : -1;
      const sh = bone(chest, s * 0.155, 0.19, -0.005);
      // плечо (кожа, куртка спущена)
      mesh(new THREE.SphereGeometry(0.048, 14, 10), skin, sh, [0, 0, 0]);
      const up = bone(sh, 0, 0, 0);
      const upG = new THREE.CylinderGeometry(0.04, 0.034, 0.27, 12);
      upG.rotateZ(Math.PI / 2);
      upG.translate(s * 0.135, 0, 0);
      mesh(upG, skin, up);
      // объёмный рукав куртки с локтя
      const fore = bone(up, s * 0.27, 0, 0);
      const slG = new THREE.CylinderGeometry(0.075, 0.068, 0.24, 14);
      slG.rotateZ(Math.PI / 2);
      slG.translate(s * 0.11, 0, 0);
      mesh(slG, cloth, fore);
      // фиолетовая полоса-зигзаг
      const stG = new THREE.CylinderGeometry(0.0765, 0.0745, 0.04, 14, 1, true);
      stG.rotateZ(Math.PI / 2);
      stG.translate(s * 0.08, 0, 0);
      mesh(stG, purple, fore, null, null, null, false);
      const cuffG = new THREE.CylinderGeometry(0.06, 0.06, 0.05, 12);
      cuffG.rotateZ(Math.PI / 2);
      cuffG.translate(s * 0.245, 0, 0);
      mesh(cuffG, cloth2, fore);
      // сборка куртки у локтя
      mesh(new THREE.SphereGeometry(0.08, 14, 10), cloth, fore, [s * 0.0, 0, 0], null, [0.8, 1.1, 1.1]);
      const hand = bone(fore, s * 0.28, 0, 0);
      mesh(new THREE.SphereGeometry(0.035, 12, 10), skin, hand, [s * 0.015, 0, 0], null, [1.2, 0.7, 1]);
      // указательный палец + средний (для позы «peace»)
      const fG = new THREE.CylinderGeometry(0.008, 0.007, 0.06, 6);
      fG.rotateZ(Math.PI / 2);
      fG.translate(s * 0.03, 0, 0);
      const finger = mesh(fG, skin, hand, [s * 0.03, 0.005, 0.012], null, null, false);
      const finger2 = mesh(fG, skin, hand, [s * 0.03, 0.005, -0.004], null, null, false);
      const nail = mesh(new THREE.SphereGeometry(0.008, 6, 6), purple, finger, [s * 0.058, 0, 0], null, null, false);
      finger.visible = false;
      finger2.visible = false;
      this.arm[side] = { sh, up, fore, hand, finger, finger2 };
    }
    // спина куртки (свисает с локтей позади)
    const jb = new THREE.CylinderGeometry(0.2, 0.25, 0.2, 20, 1, true, Math.PI * 0.62, Math.PI * 0.76);
    const jacketBack = mesh(jb, cloth, chest, [0, -0.1, -0.04], [0.15, 0, 0], [1, 1, 0.7], false);
    jacketBack.material = toon(C.cloth, { side: THREE.FrontSide });
    const jbIn = new THREE.Mesh(jb, toon(C.purple, { side: THREE.BackSide }));
    jacketBack.add(jbIn);
    this.jacketBack = jacketBack;

    // --- ноги ---
    this.leg = {};
    for (const side of ['L', 'R']) {
      const s = side === 'L' ? 1 : -1;
      const th = bone(hips, s * 0.082, -0.04, 0);
      const thG = new THREE.CylinderGeometry(0.072, 0.052, 0.42, 14);
      thG.translate(0, -0.21, 0);
      mesh(thG, skin, th);
      // шорты на бедре
      const shG = new THREE.CylinderGeometry(0.084, 0.08, 0.09, 14);
      shG.translate(0, -0.03, 0);
      mesh(shG, cloth, th);
      if (side === 'L') {
        // ремешок на бедре
        mesh(new THREE.TorusGeometry(0.064, 0.008, 6, 20), cloth2, th, [0, -0.2, 0], [Math.PI / 2, 0, 0], null, false);
        mesh(new THREE.BoxGeometry(0.02, 0.025, 0.01), silver, th, [0.0, -0.2, 0.068], null, null, false);
      }
      const shin = bone(th, 0, -0.42, 0);
      mesh(new THREE.SphereGeometry(0.052, 12, 10), skin, shin, [0, 0, 0]);
      const snG = new THREE.CylinderGeometry(0.05, 0.036, 0.38, 12);
      snG.translate(0, -0.19, 0);
      mesh(snG, skin, shin);
      // гольфы-ботинки
      const bootG = new THREE.CylinderGeometry(0.046, 0.042, 0.16, 12);
      bootG.translate(0, -0.31, 0);
      mesh(bootG, cloth, shin);
      const foot = bone(shin, 0, -0.38, 0);
      mesh(new THREE.BoxGeometry(0.085, 0.06, 0.16), cloth, foot, [0, -0.025, 0.035]);
      mesh(new THREE.BoxGeometry(0.09, 0.02, 0.17), purple, foot, [0, -0.055, 0.035], null, null, false);
      this.leg[side] = { th, shin, foot };
    }

    // тень-пятно под ногами
    const sh = new THREE.Mesh(new THREE.CircleGeometry(0.28, 20), new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.25, depthWrite: false }));
    sh.rotation.x = -Math.PI / 2;
    sh.position.y = 0.012;
    root.add(sh);
    this.blob = sh;

    // невидимая «капсула» для попадания лучом
    const hit = new THREE.Mesh(new THREE.CylinderGeometry(0.3, 0.3, 1.7, 8), new THREE.MeshBasicMaterial({ visible: false }));
    hit.position.y = 0.85;
    root.add(hit);
    this.hitMesh = hit;

    this.root.traverse((o) => { if (o.isMesh) o.castShadow = true; });
    this.hairLocks = [];
    hair.traverse((o) => { if (o.userData.sway !== undefined) this.hairLocks.push(o); });
  }

  setClipColor(hex) {
    this.clipMat.color.setHex(hex);
  }

  setEmotion(e) {
    const em = EMOTIONS[e] || EMOTIONS.normal;
    this.emotion = e;
    Object.assign(this.face, { lookX: 0, lookY: 0, tears: false, glitch: false }, em);
    this.redrawFace();
  }

  redrawFace() {
    drawFace(this.faceCtx, this.face);
    this.faceTex.needsUpdate = true;
  }

  setPose(p) {
    if (!POSES[p]) p = 'idle';
    this.pose = p;
  }

  placeAt(x, z, facing = 0) {
    this.root.position.set(x, 0, z);
    this.facing = facing;
    this.root.rotation.y = facing;
    this.path = [];
    this.moving = false;
  }

  // Плавный поворот
  faceTo(x, z) {
    this.targetFacing = Math.atan2(x - this.root.position.x, z - this.root.position.z);
  }

  walkPath(points) {
    this.path = points.map((p) => new THREE.Vector3(p.x, 0, p.z));
    this.moving = this.path.length > 0;
    return new Promise((res) => { this.onArrive = res; if (!this.moving) res(); });
  }

  update(dt, cameraPos) {
    this.time += dt;
    const t = this.time;
    const pose = POSES[this.pose] || POSES.idle;

    // --- перемещение ---
    let walkAmt = 0;
    if (this.moving && this.path.length) {
      const tgt = this.path[0];
      const p = this.root.position;
      const dx = tgt.x - p.x, dz = tgt.z - p.z;
      const d = Math.hypot(dx, dz);
      const step = this.speed * dt;
      if (d <= step) {
        p.x = tgt.x; p.z = tgt.z;
        this.path.shift();
        if (!this.path.length) {
          this.moving = false;
          const cb = this.onArrive;
          this.onArrive = null;
          if (cb) cb();
        }
      } else {
        p.x += (dx / d) * step;
        p.z += (dz / d) * step;
        this.targetFacing = Math.atan2(dx, dz);
      }
      walkAmt = 1;
    }
    this.walkBlend = THREE.MathUtils.lerp(this.walkBlend || 0, walkAmt, Math.min(1, dt * 8));
    if (this.targetFacing !== null) {
      let diff = this.targetFacing - this.facing;
      while (diff > Math.PI) diff -= Math.PI * 2;
      while (diff < -Math.PI) diff += Math.PI * 2;
      this.facing += diff * Math.min(1, dt * 8);
      if (Math.abs(diff) < 0.01) this.targetFacing = null;
    }
    this.root.rotation.y = this.facing;

    // --- позы ---
    const k = Math.min(1, dt * 7);
    const lerpRot = (obj, arr, extraX = 0) => {
      const [x, y, z] = arr || [0, 0, 0];
      obj.rotation.x += (x + extraX - obj.rotation.x) * k;
      obj.rotation.y += (y - obj.rotation.y) * k;
      obj.rotation.z += (z - obj.rotation.z) * k;
    };
    const wb = this.walkBlend;
    const ph = t * 7.5;
    const swing = Math.sin(ph) * 0.55 * wb;

    // Руки: L — левая сторона модели (+x)
    lerpRot(this.arm.L.up, pose.armL, swing * 0.6);
    lerpRot(this.arm.R.up, pose.armR, -swing * 0.6);
    lerpRot(this.arm.L.fore, pose.foreL);
    lerpRot(this.arm.R.fore, pose.foreR);
    if (pose.wave) this.arm.R.fore.rotation.z = -1.3 + Math.sin(t * 9) * 0.35;
    if (this.pose === 'cheer') { this.arm.L.up.rotation.z += Math.sin(t * 10) * 0.02; }
    this.arm.R.finger.visible = !!pose.finger;
    this.arm.R.finger2.visible = pose.finger === 2;

    // Ноги
    let hipsY = 0.86, legX = [0, 0], shinX = [0, 0], bodyX = 0, bodyZ = 0, bodyY = 0;
    if (pose.sit) {
      hipsY = pose.floor ? 0.13 : 0.48;
      legX = [-1.5, -1.5];
      shinX = pose.floor ? [0.15, 0.25] : [1.45, 1.45];
      bodyX = pose.lean ? 0 : -0.05;
    } else if (pose.crouch) {
      hipsY = 0.45;
      legX = [-1.9, -1.6];
      shinX = [2.3, 2.1];
      bodyX = 0.35;
    } else if (pose.lie) {
      hipsY = 0.86;
    } else {
      legX = [swing, -swing];
      shinX = [Math.max(0, -Math.sin(ph - 1)) * 0.8 * wb, Math.max(0, Math.sin(ph - 1)) * 0.8 * wb];
      hipsY = 0.86 + Math.abs(Math.sin(ph)) * 0.025 * wb;
    }
    if (pose.dance) {
      const b = t * 7.33; // ~ 110 bpm / 2
      hipsY = 0.84 + Math.abs(Math.sin(b)) * 0.05;
      bodyZ = Math.sin(b * 0.5) * 0.15;
      bodyY = Math.sin(b * 0.25) * 0.5;
      this.arm.L.up.rotation.z = -0.4 + Math.sin(b) * 0.6;
      this.arm.R.up.rotation.z = 0.4 + Math.sin(b + Math.PI) * 0.6;
      this.arm.L.fore.rotation.z = 1.2;
      this.arm.R.fore.rotation.z = -1.2;
      legX = [Math.sin(b) * 0.25, -Math.sin(b) * 0.25];
    }
    this.hips.position.y += (hipsY - this.hips.position.y) * k;
    for (let i = 0; i < 2; i++) {
      const L = this.leg[i ? 'R' : 'L'];
      L.th.rotation.x += (legX[i] - L.th.rotation.x) * Math.min(1, dt * 12);
      L.shin.rotation.x += (shinX[i] - L.shin.rotation.x) * Math.min(1, dt * 12);
      L.foot.rotation.x = pose.sit ? 0.1 : 0;
    }
    if (pose.lie) {
      this.body.rotation.x += (-Math.PI / 2 - this.body.rotation.x) * k;
      this.body.position.y += ((pose.lieY || 0.56) - this.body.position.y) * k;
      this.body.position.z += (0.86 - this.body.position.z) * k;
    } else {
      this.body.rotation.x += (bodyX * 0 - this.body.rotation.x) * k;
      this.body.position.y += (0 - this.body.position.y) * k;
      this.body.position.z += (0 - this.body.position.z) * k;
    }
    this.hips.rotation.z = bodyZ;
    this.hips.rotation.y = bodyY;

    // Корпус и дыхание
    const ch = pose.chest || [0, 0, 0];
    const breathe = Math.sin(t * 2.2) * 0.012;
    this.spine.rotation.x += (bodyX - this.spine.rotation.x) * k;
    this.chest.rotation.x += (ch[0] + breathe - this.chest.rotation.x) * k;
    this.chest.rotation.z += (ch[2] + Math.sin(t * 0.9) * 0.015 - this.chest.rotation.z) * k;
    this.chest.rotation.y += (ch[1] + Math.sin(ph) * 0.08 * wb - this.chest.rotation.y) * k;
    this.blob.visible = !pose.lie;

    // Голова + взгляд на игрока
    const hp = pose.head || [0, 0, 0];
    let yaw = 0, pitch = 0;
    if (this.lookTarget && !pose.lie && this.pose !== 'tpose') {
      const hw = new THREE.Vector3();
      this.head.getWorldPosition(hw);
      const dx = this.lookTarget.x - hw.x, dz = this.lookTarget.z - hw.z, dy = this.lookTarget.y - (hw.y + 0.1);
      let a = Math.atan2(dx, dz) - this.facing - this.hips.rotation.y;
      while (a > Math.PI) a -= Math.PI * 2;
      while (a < -Math.PI) a += Math.PI * 2;
      if (Math.abs(a) < 1.9) {
        yaw = THREE.MathUtils.clamp(a, -1.0, 1.0);
        pitch = THREE.MathUtils.clamp(-Math.atan2(dy, Math.hypot(dx, dz)), -0.4, 0.5);
      }
    }
    this.headYaw += (yaw - this.headYaw) * Math.min(1, dt * 5);
    this.headPitch += (pitch - this.headPitch) * Math.min(1, dt * 5);
    this.neck.rotation.y = this.headYaw * 0.4;
    this.head.rotation.order = 'YXZ';
    this.head.rotation.y = this.headYaw * 0.6 + hp[1];
    this.head.rotation.x += (this.headPitch * 0.8 + hp[0] - this.head.rotation.x) * k;
    this.head.rotation.z += (hp[2] + Math.sin(t * 1.3) * 0.03 - this.head.rotation.z) * k;
    if (this.pose === 'tpose') { this.head.rotation.set(0, 0, 0); this.neck.rotation.y = 0; }

    // Волосы покачиваются
    for (const l of this.hairLocks) l.rotation.z = Math.sin(t * 2 + l.userData.sway) * (0.03 + wb * 0.06);

    // --- лицо: моргание и речь ---
    let dirty = false;
    this.blinkT -= dt;
    if (this.blinkT <= 0) {
      if (!this.face.blink) { this.face.blink = true; this.blinkT = 0.12; }
      else { this.face.blink = false; this.blinkT = 2 + Math.random() * 3.5; }
      dirty = true;
    }
    if (this.talking) {
      const open = Math.sin(t * 18) > 0;
      if (open !== this.face.talkOpen) { this.face.talkOpen = open; dirty = true; }
    } else if (this.face.talkOpen) { this.face.talkOpen = false; dirty = true; }
    if (this.face.glitch) dirty = true;
    if (dirty) this.redrawFace();
  }
}

function labelTex(text) {
  const c = document.createElement('canvas');
  c.width = 128; c.height = 40;
  const x = c.getContext('2d');
  x.fillStyle = '#d8c8ff';
  x.fillRect(0, 0, 128, 40);
  x.fillStyle = '#1d1b24';
  x.fillRect(4, 4, 120, 32);
  x.fillStyle = '#ffffff';
  x.font = 'bold 26px Arial';
  x.textAlign = 'center';
  x.textBaseline = 'middle';
  x.fillText(text, 64, 21);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

export { toon, addOutline, EMOTIONS, POSES };
