// Кира: аниме-модель VRM (основа — Seed-san © VirtualCast, Inc., VRM Public License 1.0),
// перекрашенная в стиль Киры, с её аксессуарами. Позы, походка, мимика, взгляд,
// физика волос (spring bones).
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';

// ---------- Эмоции -> выражения VRM ----------
// blush — румянец (0..1), tears — слёзы
const EMOTIONS = {
  normal:    { e: { relaxed: 0.15 }, blush: 0.1 },
  happy:     { e: { happy: 1 }, blush: 0.35 },
  laugh:     { e: { happy: 1, aa: 0.45 }, blush: 0.5 },
  wink:      { e: { blinkRight: 1, relaxed: 0.45, aa: 0.2 }, blush: 0.35 },
  smile:     { e: { relaxed: 0.85 }, blush: 0.3 },
  shy:       { e: { relaxed: 0.55, sad: 0.2 }, blush: 1, look: [0.6, -0.3] },
  love:      { e: { happy: 0.85, aa: 0.15 }, blush: 1, hearts: true },
  sad:       { e: { sad: 1 }, blush: 0.05, look: [0, -0.4] },
  cry:       { e: { sad: 1, ih: 0.25 }, blush: 0.5, tears: true },
  surprised: { e: { surprised: 1 }, blush: 0.2 },
  pout:      { e: { angry: 0.55, ou: 0.5 }, blush: 0.7 },
  angry:     { e: { angry: 1 }, blush: 0.3 },
  smug:      { e: { relaxed: 0.55, angry: 0.18, blinkLeft: 0.25, blinkRight: 0.25 }, blush: 0.3 },
  worried:   { e: { sad: 0.55, surprised: 0.15 }, blush: 0.05 },
  sleepy:    { e: { blink: 0.6, relaxed: 0.3 }, blush: 0.2 },
  think:     { e: { relaxed: 0.2, ou: 0.15 }, blush: 0.05, look: [-0.6, 0.6] },
  glitch:    { e: { surprised: 0.8 }, blush: 0, glitch: true },
  closed:    { e: { blink: 1, relaxed: 0.5 }, blush: 0.45 },
};
const EXPR_NAMES = ['happy', 'angry', 'sad', 'relaxed', 'surprised', 'aa', 'ih', 'ou', 'ee', 'oh', 'blink', 'blinkLeft', 'blinkRight'];

// ---------- Позы (углы для нормализованных костей VRM, в радианах) ----------
// armL/armR — плечевая кость (L смотрит в +X), foreL/foreR — предплечье.
const POSES = {
  idle: { armL: [0, 0, -1.25], armR: [0, 0, 1.25], foreL: [0, -0.2, 0], foreR: [0, 0.2, 0] },
  behind: { armL: [0.45, 0, -1.2], armR: [0.45, 0, 1.2], foreL: [0, 1.2, 0], foreR: [0, -1.2, 0], chest: [-0.06, 0, 0] },
  wave: { armL: [0, 0, -1.25], armR: [0, 0, -0.35], foreR: [0, 0, -1.3], foreL: [0, -0.2, 0], head: [0, 0, 0.1], wave: 1 },
  point: { armL: [0, 0, -1.25], armR: [-0.63, 0, 1.21], foreR: [0, 2.2, -0.05], foreL: [0, -0.2, 0], fingerR: 'point', head: [0.05, 0, 0.16], chest: [0.1, 0, 0.04] },
  peace: { armL: [0, 0, -1.25], armR: [-0.4, 0, 0.9], foreR: [0, 2.15, -0.3], foreL: [0, -0.2, 0], fingerR: 'peace', head: [0, 0, 0.18] },
  think: { armL: [-0.71, 0, -1.25], armR: [-0.4, 0, 1.25], foreL: [0, -2.25, 0.1], foreR: [0, 1.25, 0], head: [0.08, 0, -0.14] },
  shy: { armL: [-0.25, 0, -1.3], armR: [-0.25, 0, 1.3], foreL: [0, -1.15, -0.15], foreR: [0, 1.15, 0.15], head: [0.2, 0, 0.12], chest: [0.08, 0, 0] },
  sad: { armL: [-0.15, 0, -1.3], armR: [-0.15, 0, 1.3], foreL: [0, -0.9, -0.1], foreR: [0, 0.9, 0.1], head: [0.38, 0, 0], chest: [0.14, 0, 0] },
  cheer: { armL: [0, 0, 1.0], armR: [0, 0, -1.0], foreL: [0, 0, 0.3], foreR: [0, 0, -0.3], head: [-0.1, 0, 0] },
  hug: { armL: [-1.3, 0, -1.15], armR: [-1.3, 0, 1.15], foreL: [0, -0.8, 0], foreR: [0, 0.8, 0], chest: [0.1, 0, 0] },
  hold: { armL: [-0.5, 0, -1.25], armR: [-0.5, 0, 1.25], foreL: [0, -1.35, 0], foreR: [0, 1.35, 0], head: [0.25, 0, 0] },
  cook: { armL: [-0.75, 0, -1.2], armR: [-0.75, 0, 1.2], foreL: [0, -1.0, 0], foreR: [0, 1.0, 0], head: [0.35, 0, 0], chest: [0.1, 0, 0] },
  pout: { armL: [-0.55, 0, -1.25], armR: [-0.55, 0, 1.25], foreL: [0, -1.75, 0.1], foreR: [0, 1.75, -0.1], head: [-0.05, 0, 0.1] },
  tpose: { armL: [0, 0, 0], armR: [0, 0, 0], foreL: [0, 0, 0], foreR: [0, 0, 0], head: [0, 0, 0] },
  sit: { sit: 1, armL: [-0.45, 0, -1.3], armR: [-0.45, 0, 1.3], foreL: [0, -0.7, -0.2], foreR: [0, 0.7, 0.2] },
  sitHold: { sit: 1, armL: [-0.5, 0, -1.25], armR: [-0.5, 0, 1.25], foreL: [0, -1.35, 0], foreR: [0, 1.35, 0], head: [0.25, 0, 0] },
  sitLean: { sit: 1, armL: [-0.45, 0, -1.3], armR: [-0.45, 0, 1.3], foreL: [0, -0.7, -0.2], foreR: [0, 0.7, 0.2], head: [0.1, 0, -0.4], chest: [0, 0, -0.22] },
  sitFloor: { sit: 1, floor: 1, armL: [0.3, 0, -1.3], armR: [0.3, 0, 1.3], foreL: [0, 0, 0], foreR: [0, 0, 0] },
  sitFloorLean: { sit: 1, floor: 1, armL: [0.3, 0, -1.3], armR: [-0.45, 0, 1.3], foreL: [0, 0, 0], foreR: [0, 0.7, 0.2], head: [0.1, 0, -0.45], chest: [0, 0, -0.25] },
  crouch: { crouch: 1, armL: [-1.0, 0, -1.2], armR: [-1.0, 0, 1.2], foreL: [0, -0.9, 0], foreR: [0, 0.9, 0], head: [0.1, 0, 0] },
  dance: { dance: 1, armL: [0, 0, -0.6], armR: [0, 0, 0.6], foreL: [0, 0, 0.8], foreR: [0, 0, -0.8] },
  lie: { lie: 1, armL: [0, 0, -1.4], armR: [0, 0, 1.4], foreL: [0, 0, 0], foreR: [0, 0, 0] },
};

const FINGERS = ['Index', 'Middle', 'Ring', 'Little'];

function overlayTex(draw, w = 256, h = 128) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.userData.ctx = c.getContext('2d');
  t.userData.draw = draw;
  return t;
}

export class Kira {
  constructor(scene) {
    this.scene = scene;
    this.root = new THREE.Group();
    this.root.name = 'Kira';
    scene.add(this.root);
    this.body = new THREE.Group();
    this.root.add(this.body);
    this.head = new THREE.Object3D(); // заменится костью головы после загрузки
    this.emotion = 'normal';
    this.pose = 'idle';
    this.time = 0;
    this.blinkT = 2;
    this.blinkV = 0;
    this.talking = false;
    this.path = [];
    this.speed = 1.5;
    this.moving = false;
    this.lookTarget = null;
    this.headYaw = 0;
    this.headPitch = 0;
    this.facing = 0;
    this.targetFacing = null;
    this.walkBlend = 0;
    this.expr = {};
    this.exprTarget = {};
    this.lookObj = new THREE.Object3D();
    scene.add(this.lookObj);

    const shadow = new THREE.Mesh(new THREE.CircleGeometry(0.3, 24), new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.28, depthWrite: false }));
    shadow.rotation.x = -Math.PI / 2;
    shadow.position.y = 0.012;
    this.root.add(shadow);
    this.blob = shadow;
    const hit = new THREE.Mesh(new THREE.CylinderGeometry(0.3, 0.3, 1.6, 8), new THREE.MeshBasicMaterial({ visible: false }));
    hit.position.y = 0.8;
    this.root.add(hit);
    this.hitMesh = hit;

    this.ready = this.load();
  }

  async load() {
    const b64 = window.KIRA_VRM_B64;
    const bin = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    const loader = new GLTFLoader();
    loader.register((parser) => new VRMLoaderPlugin(parser));
    const gltf = await loader.parseAsync(bin.buffer, '');
    const vrm = (this.vrm = gltf.userData.vrm);
    VRMUtils.removeUnnecessaryVertices(gltf.scene);
    VRMUtils.combineSkeletons ? VRMUtils.combineSkeletons(gltf.scene) : VRMUtils.removeUnnecessaryJoints(gltf.scene);
    vrm.scene.traverse((o) => { if (o.isMesh) { o.castShadow = true; o.frustumCulled = false; } });
    this.body.add(vrm.scene);
    const H = vrm.humanoid;
    const n = (name) => H.getNormalizedBoneNode(name);
    this.b = {
      hips: n('hips'), spine: n('spine'), chest: n('chest'), upperChest: n('upperChest'), neck: n('neck'), head: n('head'),
      armL: n('leftUpperArm'), armR: n('rightUpperArm'), foreL: n('leftLowerArm'), foreR: n('rightLowerArm'),
      handL: n('leftHand'), handR: n('rightHand'), shL: n('leftShoulder'), shR: n('rightShoulder'),
      thL: n('leftUpperLeg'), thR: n('rightUpperLeg'), shinL: n('leftLowerLeg'), shinR: n('rightLowerLeg'), footL: n('leftFoot'), footR: n('rightFoot'),
    };
    this.fingers = {};
    for (const side of ['left', 'right']) for (const f of FINGERS) for (const seg of ['Proximal', 'Intermediate', 'Distal']) {
      const node = n(`${side}${f}${seg}`);
      if (node) this.fingers[`${side}${f}${seg}`] = node;
    }
    this.head = this.b.head;
    this.restHipsY = this.b.hips.position.y;
    vrm.lookAt.target = this.lookObj;
    vrm.lookAt.autoUpdate = true;
    this.addAccessories();
    this.setEmotion(this.emotion);
    vrm.update(0);
    if (vrm.springBoneManager) vrm.springBoneManager.reset();
  }

  // Заколки-крестики, чокер, нашивка QVK, румянец и слёзы
  addAccessories() {
    const head = this.b.head;
    const silver = new THREE.MeshStandardMaterial({ color: 0xd6d4e2, metalness: 0.8, roughness: 0.3 });
    this.clipMat = silver;
    const crossGeo = new THREE.BoxGeometry(0.042, 0.008, 0.006);
    const mkCross = (x, y, z, s, ry) => {
      const g = new THREE.Group();
      const a = new THREE.Mesh(crossGeo, silver); a.rotation.z = 0.785;
      const b = new THREE.Mesh(crossGeo, silver); b.rotation.z = -0.785;
      g.add(a, b);
      g.position.set(x, y, z);
      g.rotation.set(-0.2, ry, 0);
      g.scale.setScalar(s);
      head.add(g);
    };
    mkCross(-0.078, 0.145, 0.07, 1, -0.6);
    mkCross(-0.092, 0.118, 0.06, 0.75, -0.7);
    mkCross(-0.066, 0.17, 0.06, 0.55, -0.5);
    // чокер
    const neck = this.b.neck;
    const choker = new THREE.Mesh(new THREE.TorusGeometry(0.047, 0.008, 8, 28), new THREE.MeshStandardMaterial({ color: 0x15131a, roughness: 0.5 }));
    choker.rotation.x = Math.PI / 2;
    choker.scale.set(1, 0.92, 1);
    choker.position.set(0, 0.035, 0.005);
    neck.add(choker);
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.008, 0.0025, 6, 14), silver);
    ring.position.set(0, 0.02, 0.05);
    neck.add(ring);
    const cr = new THREE.Group();
    const c1 = new THREE.Mesh(new THREE.BoxGeometry(0.022, 0.006, 0.003), silver); c1.rotation.z = 0.785;
    const c2 = new THREE.Mesh(new THREE.BoxGeometry(0.022, 0.006, 0.003), silver); c2.rotation.z = -0.785;
    cr.add(c1, c2);
    cr.position.set(0, 0.003, 0.052);
    neck.add(cr);
    // нашивка QVK на груди
    const c = document.createElement('canvas');
    c.width = 128; c.height = 44;
    const x = c.getContext('2d');
    x.fillStyle = '#d8c8ff'; x.fillRect(0, 0, 128, 44);
    x.fillStyle = '#16141c'; x.fillRect(4, 4, 120, 36);
    x.fillStyle = '#fff'; x.font = 'bold 28px Arial'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText('QVK', 64, 23);
    const lt = new THREE.CanvasTexture(c); lt.colorSpace = THREE.SRGBColorSpace;
    const label = new THREE.Mesh(new THREE.PlaneGeometry(0.06, 0.021), new THREE.MeshBasicMaterial({ map: lt, transparent: true, polygonOffset: true, polygonOffsetFactor: -2 }));
    const ch = this.b.upperChest || this.b.chest;
    label.position.set(0.045, 0.03, 0.115);
    label.rotation.set(-0.15, 0.3, 0);
    ch.add(label);
    this.label = label;
    // чёрные ботинки с фиолетовой подошвой
    const bootMat = new THREE.MeshStandardMaterial({ color: 0x17151c, roughness: 0.45 });
    const soleMat = new THREE.MeshStandardMaterial({ color: 0x7a4fd6, roughness: 0.6 });
    for (const f of [this.b.footL, this.b.footR]) {
      if (!f) continue;
      const boot = new THREE.Mesh(new THREE.CapsuleGeometry(0.052, 0.17, 6, 14), bootMat);
      boot.rotation.x = Math.PI / 2;
      boot.position.set(0, -0.035, 0.07);
      boot.scale.set(1.08, 1, 0.82);
      f.add(boot);
      const sole = new THREE.Mesh(new THREE.BoxGeometry(0.105, 0.024, 0.28), soleMat);
      sole.position.set(0, -0.075, 0.07);
      f.add(sole);
      const cuff = new THREE.Mesh(new THREE.CylinderGeometry(0.052, 0.048, 0.12, 14), bootMat);
      cuff.position.set(0, 0.03, -0.005);
      f.add(cuff);
    }
    // румянец/слёзы — полупрозрачная наклейка на лице
    this.faceFx = overlayTex(null, 256, 128);
    const fx = new THREE.Mesh(new THREE.PlaneGeometry(0.15, 0.075), new THREE.MeshBasicMaterial({ map: this.faceFx, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -4 }));
    fx.position.set(0, 0.075, 0.1);
    fx.renderOrder = 3;
    head.add(fx);
    this.faceFxMesh = fx;
  }

  drawFaceFx(blush, tears) {
    const x = this.faceFx.userData.ctx;
    x.clearRect(0, 0, 256, 128);
    if (blush > 0.02) {
      for (const s of [-1, 1]) {
        const cx = 128 + s * 74, cy = 70;
        const g = x.createRadialGradient(cx, cy, 2, cx, cy, 36);
        g.addColorStop(0, `rgba(255,105,140,${0.55 * blush})`);
        g.addColorStop(1, 'rgba(255,105,140,0)');
        x.fillStyle = g;
        x.fillRect(cx - 40, cy - 40, 80, 80);
        if (blush > 0.6) {
          x.strokeStyle = `rgba(225,70,110,${0.7 * blush})`;
          x.lineWidth = 3;
          for (let i = 0; i < 3; i++) { x.beginPath(); x.moveTo(cx - 14 + i * 10, cy + 8); x.lineTo(cx - 6 + i * 10, cy - 6); x.stroke(); }
        }
      }
    }
    if (tears) {
      x.fillStyle = 'rgba(150,205,255,0.9)';
      for (const s of [-1, 1]) { x.beginPath(); x.ellipse(128 + s * 52, 58, 6, 15, 0, 0, Math.PI * 2); x.fill(); }
    }
    this.faceFx.needsUpdate = true;
  }

  setClipColor(hex) { if (this.clipMat) this.clipMat.color.setHex(hex); else this.pendingClip = hex; }

  setEmotion(e) {
    const em = EMOTIONS[e] || EMOTIONS.normal;
    this.emotion = e;
    this.exprTarget = {};
    for (const k of EXPR_NAMES) this.exprTarget[k] = em.e[k] || 0;
    this.lookOffset = em.look || null;
    this.glitchFace = !!em.glitch;
    if (this.faceFx) this.drawFaceFx(em.blush || 0, em.tears);
    if (this.pendingClip !== undefined && this.clipMat) { this.clipMat.color.setHex(this.pendingClip); this.pendingClip = undefined; }
  }

  setPose(p) { this.pose = POSES[p] ? p : 'idle'; }

  placeAt(x, z, facing = 0) {
    this.root.position.set(x, 0, z);
    this.facing = facing;
    this.targetFacing = null;
    this.root.rotation.y = facing;
    this.path = [];
    this.moving = false;
    this.snapPose = true;
    this.root.updateMatrixWorld(true);
    if (this.vrm && this.vrm.springBoneManager) { this.vrm.update(0); this.vrm.springBoneManager.reset(); }
  }

  faceTo(x, z) { this.targetFacing = Math.atan2(x - this.root.position.x, z - this.root.position.z); }

  walkPath(points) {
    this.path = points.map((p) => new THREE.Vector3(p.x, 0, p.z));
    this.moving = this.path.length > 0;
    return new Promise((res) => { this.onArrive = res; if (!this.moving) res(); });
  }

  update(dt) {
    this.time += dt;
    const t = this.time;
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
          const cb = this.onArrive; this.onArrive = null;
          if (cb) cb();
        }
      } else {
        p.x += (dx / d) * step; p.z += (dz / d) * step;
        this.targetFacing = Math.atan2(dx, dz);
      }
      walkAmt = 1;
    }
    this.walkBlend += (walkAmt - this.walkBlend) * Math.min(1, dt * 8);
    if (this.targetFacing !== null) {
      let diff = this.targetFacing - this.facing;
      while (diff > Math.PI) diff -= Math.PI * 2;
      while (diff < -Math.PI) diff += Math.PI * 2;
      this.facing += diff * Math.min(1, dt * 8);
      if (Math.abs(diff) < 0.01) this.targetFacing = null;
    }
    this.root.rotation.y = this.facing;
    if (!this.vrm) return;

    const B = this.b;
    const pose = POSES[this.pose] || POSES.idle;
    const k = this.snapPose ? 1 : Math.min(1, dt * 7);
    this.snapPose = false;
    const lerpRot = (obj, arr, ex = 0, ey = 0, ez = 0) => {
      if (!obj) return;
      const [x, y, z] = arr || [0, 0, 0];
      obj.rotation.x += (x + ex - obj.rotation.x) * k;
      obj.rotation.y += (y + ey - obj.rotation.y) * k;
      obj.rotation.z += (z + ez - obj.rotation.z) * k;
    };
    const wb = this.walkBlend;
    const ph = t * 7.5;
    const swing = Math.sin(ph) * 0.5 * wb;
    const breathe = Math.sin(t * 2.2) * 0.015;

    lerpRot(B.armL, pose.armL, swing * 0.7, 0, Math.sin(t * 1.1) * 0.02);
    lerpRot(B.armR, pose.armR, -swing * 0.7, 0, -Math.sin(t * 1.1) * 0.02);
    lerpRot(B.foreL, pose.foreL);
    lerpRot(B.foreR, pose.foreR);
    if (pose.wave) B.foreR.rotation.z = -1.3 + Math.sin(t * 9) * 0.35;

    // пальцы
    const curl = (side, finger, amt) => {
      const sgn = side === 'left' ? -1 : 1;
      for (const seg of ['Proximal', 'Intermediate', 'Distal']) {
        const f = this.fingers[`${side}${finger}${seg}`];
        if (f) f.rotation.z += (sgn * amt - f.rotation.z) * k;
      }
    };
    for (const f of FINGERS) {
      const fr = pose.fingerR;
      let a = 0.25;
      if (fr === 'point') a = f === 'Index' ? 0 : 1.3;
      if (fr === 'peace') a = f === 'Index' || f === 'Middle' ? 0 : 1.3;
      if (pose.armR[0] < -0.4 && !fr) a = 0.6;
      curl('right', f, a);
      curl('left', f, pose.armL[0] < -0.4 ? 0.6 : 0.25);
    }

    // ноги и таз
    let hipsY = this.restHipsY, legX = [0, 0], shinX = [0, 0], spineX = 0, hipsZ = 0, hipsYaw = 0;
    if (pose.sit) {
      hipsY = pose.floor ? 0.12 : 0.47;
      legX = [-1.5, -1.5];
      shinX = pose.floor ? [0.12, 0.22] : [1.45, 1.45];
    } else if (pose.crouch) {
      hipsY = 0.42; legX = [-1.9, -1.65]; shinX = [2.3, 2.15]; spineX = 0.35;
    } else {
      legX = [swing, -swing];
      shinX = [Math.max(0, -Math.sin(ph - 1)) * 0.8 * wb, Math.max(0, Math.sin(ph - 1)) * 0.8 * wb];
      hipsY = this.restHipsY + Math.abs(Math.sin(ph)) * 0.02 * wb;
    }
    if (pose.dance) {
      const bb = t * 7.33;
      hipsY = this.restHipsY - 0.02 + Math.abs(Math.sin(bb)) * 0.05;
      hipsZ = Math.sin(bb * 0.5) * 0.12;
      hipsYaw = Math.sin(bb * 0.25) * 0.45;
      B.armL.rotation.z = -0.5 + Math.sin(bb) * 0.6;
      B.armR.rotation.z = 0.5 + Math.sin(bb + Math.PI) * 0.6;
      B.foreL.rotation.z = 1.1; B.foreR.rotation.z = -1.1;
      legX = [Math.sin(bb) * 0.25, -Math.sin(bb) * 0.25];
    }
    if (pose.lie) hipsY = this.restHipsY;
    B.hips.position.y += (hipsY - B.hips.position.y) * k;
    B.hips.rotation.z = hipsZ;
    B.hips.rotation.y = hipsYaw;
    const kk = Math.min(1, dt * 12);
    B.thL.rotation.x += (legX[0] - B.thL.rotation.x) * kk;
    B.thR.rotation.x += (legX[1] - B.thR.rotation.x) * kk;
    B.shinL.rotation.x += (shinX[0] - B.shinL.rotation.x) * kk;
    B.shinR.rotation.x += (shinX[1] - B.shinR.rotation.x) * kk;
    if (B.footL) B.footL.rotation.x = pose.sit && !pose.floor ? 0.1 : 0;
    if (B.footR) B.footR.rotation.x = pose.sit && !pose.floor ? 0.1 : 0;
    // лёжа — поворачиваем всё тело
    const lieK = pose.lie ? 1 : 0;
    this.body.rotation.x += (-Math.PI / 2 * lieK - this.body.rotation.x) * k;
    this.body.position.y += ((pose.lie ? 0.56 : 0) - this.body.position.y) * k;
    this.body.position.z += ((pose.lie ? this.restHipsY : 0) - this.body.position.z) * k;
    this.blob.visible = !pose.lie;

    const ch = pose.chest || [0, 0, 0];
    lerpRot(B.spine, [spineX + ch[0] * 0.5, ch[1] * 0.5 + Math.sin(ph) * 0.06 * wb, ch[2] * 0.5]);
    lerpRot(B.chest, [ch[0] * 0.5 + breathe, ch[1] * 0.5, ch[2] * 0.5 + Math.sin(t * 0.9) * 0.012]);

    // голова и взгляд
    const hp = pose.head || [0, 0, 0];
    let yaw = 0, pitch = 0;
    if (this.lookTarget && !pose.lie && this.pose !== 'tpose') {
      const hw = new THREE.Vector3();
      B.head.getWorldPosition(hw);
      const dx = this.lookTarget.x - hw.x, dz = this.lookTarget.z - hw.z, dy = this.lookTarget.y - (hw.y + 0.08);
      let a = Math.atan2(dx, dz) - this.facing - hipsYaw;
      while (a > Math.PI) a -= Math.PI * 2;
      while (a < -Math.PI) a += Math.PI * 2;
      if (Math.abs(a) < 1.9) {
        yaw = THREE.MathUtils.clamp(a, -1.0, 1.0);
        pitch = THREE.MathUtils.clamp(-Math.atan2(dy, Math.hypot(dx, dz)), -0.4, 0.5);
      }
    }
    this.headYaw += (yaw - this.headYaw) * Math.min(1, dt * 5);
    this.headPitch += (pitch - this.headPitch) * Math.min(1, dt * 5);
    B.neck.rotation.y = this.headYaw * 0.4;
    B.neck.rotation.x = this.headPitch * 0.3;
    B.head.rotation.order = 'YXZ';
    B.head.rotation.y = this.headYaw * 0.6 + hp[1];
    B.head.rotation.x += (this.headPitch * 0.6 + hp[0] - B.head.rotation.x) * k;
    B.head.rotation.z += (hp[2] + Math.sin(t * 1.3) * 0.025 - B.head.rotation.z) * k;
    if (this.pose === 'tpose') { B.head.rotation.set(0, 0, 0); B.neck.rotation.set(0, 0, 0); }

    // цель для глаз
    if (this.lookTarget) {
      this.lookObj.position.copy(this.lookTarget);
      if (this.lookOffset) {
        const hw = new THREE.Vector3();
        B.head.getWorldPosition(hw);
        const right = new THREE.Vector3(Math.cos(this.facing), 0, -Math.sin(this.facing));
        this.lookObj.position.copy(hw).add(new THREE.Vector3(Math.sin(this.facing), 0, Math.cos(this.facing)).multiplyScalar(1.5))
          .addScaledVector(right, -this.lookOffset[0]).add(new THREE.Vector3(0, 0.08 + this.lookOffset[1], 0));
      }
    }

    // --- мимика: эмоция + моргание + речь ---
    const E = this.vrm.expressionManager;
    this.blinkT -= dt;
    if (this.blinkT <= 0) { this.blinkV = this.blinkV > 0 ? 0 : 1; this.blinkT = this.blinkV ? 0.11 : 2 + Math.random() * 3.5; }
    const ek = Math.min(1, dt * 10);
    const talkOpen = this.talking ? (0.35 + 0.35 * Math.sin(t * 19) + 0.2 * Math.sin(t * 7.3)) : 0;
    for (const name of EXPR_NAMES) {
      let target = this.exprTarget[name] || 0;
      if (name === 'blink') target = Math.max(target, this.blinkV && !this.exprTarget.happy ? 1 : 0);
      if (name === 'aa') target = Math.max(target, Math.max(0, talkOpen) * (this.exprTarget.happy ? 0.6 : 0.9));
      if (name === 'oh' && this.talking) target = Math.max(0, Math.sin(t * 11)) * 0.3;
      if (this.glitchFace && Math.random() < 0.15) target = Math.random();
      const cur = this.expr[name] || 0;
      const v = cur + (target - cur) * (name === 'blink' ? Math.min(1, dt * 30) : ek);
      this.expr[name] = v;
      E.setValue(name, v);
    }
    // румянец покачивается при «любви»
    if (this.faceFxMesh) this.faceFxMesh.visible = !pose.lie || true;

    this.vrm.update(dt);
  }
}

export { EMOTIONS, POSES };
