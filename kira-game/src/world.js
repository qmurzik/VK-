// Квартира Киры: геометрия, свет, коллизии, интерактивные предметы, навигация.
import * as THREE from 'three';
import { T } from './textures.js';

const H = 2.8; // высота потолка
const WT = 0.16; // толщина стен

const lam = (color, map, extra = {}) => new THREE.MeshLambertMaterial(map ? { color, map, ...extra } : { color, ...extra });

export class World {
  constructor(scene) {
    this.scene = scene;
    this.colliders = [];
    this.interact = {}; // id -> {obj, label, enabled, handler, pos}
    this.hitList = [];
    this.lamps = [];
    this.glitchObjs = [];
    this.glitchLevel = 0;
    this.time = 0;
    this.group = new THREE.Group();
    scene.add(this.group);
    this.mats = {};
    this.build();
    this.setPhase('day');
  }

  // Бокс по min/max координатам
  box(x0, y0, z0, x1, y1, z1, mat, collide = false, parent = this.group) {
    const g = new THREE.BoxGeometry(x1 - x0, y1 - y0, z1 - z0);
    const m = new THREE.Mesh(g, mat);
    m.position.set((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2);
    m.receiveShadow = true;
    m.castShadow = true;
    parent.add(m);
    if (collide) this.colliders.push({ x0, z0, x1, z1, mesh: m });
    // корректируем UV под размер, чтобы текстуры не растягивались
    if (mat.map && mat.map.wrapS === THREE.RepeatWrapping) {
      const uv = g.attributes.uv;
      const sx = x1 - x0, sy = y1 - y0, sz = z1 - z0;
      const dims = [[sz, sy], [sz, sy], [sx, sz], [sx, sz], [sx, sy], [sx, sy]];
      for (let f = 0; f < 6; f++) for (let v = 0; v < 4; v++) {
        const i = f * 4 + v;
        uv.setXY(i, uv.getX(i) * dims[f][0], uv.getY(i) * dims[f][1]);
      }
    }
    return m;
  }

  cyl(x, y, z, rt, rb, h, mat, seg = 16, parent = this.group) {
    const m = new THREE.Mesh(new THREE.CylinderGeometry(rt, rb, h, seg), mat);
    m.position.set(x, y + h / 2, z);
    m.castShadow = true;
    m.receiveShadow = true;
    parent.add(m);
    return m;
  }

  sphere(x, y, z, r, mat, sx = 1, sy = 1, sz = 1, parent = this.group) {
    const m = new THREE.Mesh(new THREE.SphereGeometry(r, 16, 12), mat);
    m.position.set(x, y, z);
    m.scale.set(sx, sy, sz);
    m.castShadow = true;
    parent.add(m);
    return m;
  }

  plane(w, h, mat, x, y, z, ry = 0, parent = this.group) {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), mat);
    m.position.set(x, y, z);
    m.rotation.y = ry;
    parent.add(m);
    return m;
  }

  // Стена вдоль оси X (z = const) с проёмами [{a, b, y0, y1, collide}]
  wallX(z, x0, x1, mat, holes = []) {
    this.wallGeneric('x', z, x0, x1, mat, holes);
  }
  wallZ(x, z0, z1, mat, holes = []) {
    this.wallGeneric('z', x, z0, z1, mat, holes);
  }
  wallGeneric(axis, c, s0, s1, mat, holes) {
    const seg = (a, b, y0, y1, collide) => {
      if (b - a < 0.001 || y1 - y0 < 0.001) return;
      if (axis === 'x') this.box(a, y0, c - WT / 2, b, y1, c + WT / 2, mat, collide);
      else this.box(c - WT / 2, y0, a, c + WT / 2, y1, b, mat, collide);
    };
    holes = holes.slice().sort((p, q) => p.a - q.a);
    let cur = s0;
    for (const h of holes) {
      seg(cur, h.a, 0, H, true);
      seg(h.a, h.b, 0, h.y0, h.collide !== false);
      seg(h.a, h.b, h.y1, H, false);
      if (h.y0 > 0 && h.collide === false) { /* подоконник без коллизии не бывает */ }
      if (h.y0 > 0 || h.collideFull) {
        // окно: коллизия на всю ширину
        if (axis === 'x') this.colliders.push({ x0: h.a, z0: c - WT / 2, x1: h.b, z1: c + WT / 2 });
        else this.colliders.push({ x0: c - WT / 2, z0: h.a, x1: c + WT / 2, z1: h.b });
      }
      cur = h.b;
    }
    seg(cur, s1, 0, H, true);
  }

  addInteract(id, obj, label, pos) {
    const p = pos || (() => { const v = new THREE.Vector3(); obj.getWorldPosition(v); return v; })();
    this.interact[id] = { id, obj, label, enabled: false, handler: null, pos: p };
    obj.traverse((o) => { if (o.isMesh) o.userData.interactId = id; });
    this.hitList.push(obj);
    // мелкие предметы получают увеличенную невидимую зону попадания
    obj.updateWorldMatrix(true, true);
    const bb = new THREE.Box3().setFromObject(obj);
    const size = bb.getSize(new THREE.Vector3());
    if (Math.min(size.x, size.y, size.z) < 0.35 && Math.max(size.x, size.y, size.z) < 1.0) {
      const hit = new THREE.Mesh(new THREE.BoxGeometry(size.x + 0.25, size.y + 0.25, size.z + 0.25), new THREE.MeshBasicMaterial({ visible: false }));
      bb.getCenter(hit.position);
      hit.userData.interactId = id;
      this.group.add(hit);
    }
  }

  build() {
    const M = this.mats;
    M.floor = lam(0xffffff, T.wood([1, 1]));
    M.floor.map.repeat.set(0.5, 0.5);
    M.wall = lam(0xffffff, T.wallpaper([1, 1]));
    M.wall.map.repeat.set(1.2, 1.2);
    M.wallBed = lam(0xffffff, T.wallpaper([1, 1], 290));
    M.wallBed.map.repeat.set(1.2, 1.2);
    M.tile = lam(0xffffff, T.tiles([1, 1]));
    M.tile.map.repeat.set(1, 1);
    M.tileBlue = lam(0xffffff, T.tiles([1, 1], '#dfeaf7', '#a9b8cc', 6));
    M.checker = lam(0xffffff, T.checker([1, 1]));
    M.checker.map.repeat.set(1, 1);
    M.ceil = lam(0xf6f2fb);
    M.white = lam(0xf4f1f8);
    M.dark = lam(0x2a2733);
    M.black = lam(0x141218);
    M.purple = lam(0x7a4fd6);
    M.purpleSoft = lam(0xb49ae6);
    M.pink = lam(0xf3a6c8);
    M.woodDark = lam(0x6b4a33);
    M.woodLight = lam(0xc9a27a);
    M.metal = lam(0xb8bcc8);
    M.green = lam(0x4f9a5a);
    M.pot = lam(0xc8734f);
    M.glass = new THREE.MeshLambertMaterial({ color: 0xbfe0ff, transparent: true, opacity: 0.18, depthWrite: false });
    M.sofa = lam(0x6f6487, T.fabric('#6f6487', [2, 2]));

    // ---------- пол и потолок ----------
    const floorRegions = [
      [0, 0, 7, 6, M.floor], [7, 0, 12, 6, M.checker], [0, 6, 5, 10, M.floor],
      [5, 6, 8, 10, M.floor], [8, 6, 12, 10, M.tileBlue],
    ];
    for (const [x0, z0, x1, z1, mat] of floorRegions) {
      const f = this.box(x0, -0.1, z0, x1, 0, z1, mat);
      f.castShadow = false;
      const c = this.box(x0, H, z0, x1, H + 0.1, z1, M.ceil);
      c.castShadow = false;
    }

    // ---------- стены ----------
    // юг (z=0): окна гостиной, балконная дверь, окно кухни
    this.wallX(0, -WT / 2, 12 + WT / 2, M.wall, [
      { a: 0.7, b: 2.5, y0: 0.85, y1: 2.25 },
      { a: 3.0, b: 4.3, y0: 0, y1: 2.3 }, // балкон
      { a: 4.9, b: 6.5, y0: 0.85, y1: 2.25 },
      { a: 8.6, b: 10.4, y0: 1.0, y1: 2.25 },
    ]);
    // север (z=10)
    this.wallX(10, -WT / 2, 12 + WT / 2, M.wallBed, [
      { a: 2.7, b: 3.9, y0: 0.9, y1: 2.25 },
      { a: 6.0, b: 7.0, y0: 0, y1: 2.2, collideFull: true }, // входная дверь
    ]);
    this.wallZ(0, 0, 10, M.wall);
    this.wallZ(12, 0, 10, M.tile, [{ a: 1.6, b: 3.4, y0: 1.25, y1: 2.25 }]);
    // внутренние
    this.wallX(6, 0, 12, M.wallBed, [{ a: 5.6, b: 6.8, y0: 0, y1: 2.3 }]);
    this.wallZ(7, 0, 6, M.wall, [{ a: 0.8, b: 4.6, y0: 0, y1: 2.35 }]);
    this.wallZ(5, 6, 10, M.wallBed, [{ a: 7.4, b: 8.4, y0: 0, y1: 2.2 }]);
    this.wallZ(8, 6, 10, M.tile, [{ a: 7.4, b: 8.4, y0: 0, y1: 2.2 }]);

    // плинтусы
    const sk = M.white;
    this.box(0.08, 0, 0.08, 0.12, 0.08, 5.92, sk);

    // окна: стекло и рамы
    const win = (x0, z0, x1, z1, y0, y1) => {
      this.box(x0, y0, z0, x1, y1, z1, M.glass).castShadow = false;
      const fx = x1 - x0 > z1 - z0;
      if (fx) {
        this.box(x0, y0 - 0.05, z0 - 0.08, x1, y0, z1 + 0.12, M.white);
        this.box((x0 + x1) / 2 - 0.025, y0, z0, (x0 + x1) / 2 + 0.025, y1, z1, M.white);
      } else {
        this.box(x0 - 0.12, y0 - 0.05, z0, x1 + 0.08, y0, z1, M.white);
        this.box(x0, y0, (z0 + z1) / 2 - 0.025, x1, y1, (z0 + z1) / 2 + 0.025, M.white);
      }
    };
    win(0.7, -0.02, 2.5, 0.02, 0.85, 2.25);
    win(4.9, -0.02, 6.5, 0.02, 0.85, 2.25);
    win(8.6, -0.02, 10.4, 0.02, 1.0, 2.25);
    win(2.7, 9.98, 3.9, 10.02, 0.9, 2.25);
    win(11.98, 1.6, 12.02, 3.4, 1.25, 2.25);

    // шторы
    const curtainMat = lam(0x9b7fd4, T.fabric('#9b7fd4', [1, 1]));
    const curtain = (x, z, w, rotY, h = 2.4) => {
      const g = new THREE.Group();
      for (let i = 0; i < 5; i++) {
        const c = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.06, h, 8), curtainMat);
        c.position.set(i * (w / 5), h / 2, 0);
        c.castShadow = true;
        g.add(c);
      }
      g.position.set(x, 0.3, z);
      g.rotation.y = rotY;
      this.group.add(g);
      return g;
    };
    curtain(0.35, 0.15, 0.35, 0);
    curtain(2.55, 0.15, 0.35, 0);
    curtain(4.55, 0.15, 0.35, 0);
    curtain(6.55, 0.15, 0.35, 0);
    this.bedCurtain = curtain(2.3, 9.85, 0.35, 0);
    curtain(3.95, 9.85, 0.35, 0);

    // ---------- ГОСТИНАЯ ----------
    // ТВ-тумба и телевизор (на западной стене)
    this.box(0.1, 0, 2.1, 0.6, 0.5, 3.9, M.woodDark, true);
    this.box(0.12, 0.08, 2.15, 0.58, 0.45, 2.95, M.black);
    this.tvTex = T.screen();
    this.box(0.1, 0.62, 2.3, 0.2, 1.42, 3.7, M.black);
    const tvScreen = this.plane(1.3, 0.72, new THREE.MeshBasicMaterial({ map: this.tvTex }), 0.205, 1.02, 3.0, Math.PI / 2);
    this.tvScreen = tvScreen;
    this.addInteract('tv', tvScreen, 'Телевизор');
    // приставка
    const consoleBox = this.box(0.22, 0.5, 3.25, 0.5, 0.58, 3.7, M.white);
    this.box(0.5, 0.52, 3.3, 0.51, 0.56, 3.35, lam(0x9f6bff, null, { emissive: 0x9f6bff }));
    this.addInteract('console', consoleBox, 'Приставка');
    // геймпады
    this.pad1 = this.box(1.95, 0.42, 2.7, 2.1, 0.46, 2.85, M.purple);
    this.pad2 = this.box(1.95, 0.42, 3.2, 2.1, 0.46, 3.35, M.white);

    // ковёр
    const rug = new THREE.Mesh(new THREE.CircleGeometry(1.4, 40), lam(0xffffff, T.rug()));
    rug.rotation.x = -Math.PI / 2;
    rug.position.set(2.4, 0.005, 3);
    rug.receiveShadow = true;
    this.group.add(rug);

    // журнальный столик
    this.box(1.7, 0.36, 2.4, 2.5, 0.42, 3.6, M.woodLight, true);
    for (const [x, z] of [[1.75, 2.45], [2.45, 2.45], [1.75, 3.55], [2.45, 3.55]]) this.box(x - 0.03, 0, z - 0.03, x + 0.03, 0.36, z + 0.03, M.woodDark);
    // кружки на столике
    this.cyl(2.25, 0.42, 2.65, 0.04, 0.035, 0.09, M.pink);

    // диван (смотрит на -x)
    const sofa = new THREE.Group();
    this.group.add(sofa);
    this.box(3.2, 0.0, 1.8, 4.15, 0.42, 4.2, M.sofa, true, sofa);
    this.box(3.85, 0.42, 1.8, 4.15, 1.0, 4.2, M.sofa, false, sofa);
    this.box(3.2, 0.42, 1.8, 4.15, 0.68, 2.05, M.sofa, false, sofa);
    this.box(3.2, 0.42, 3.95, 4.15, 0.68, 4.2, M.sofa, false, sofa);
    // подушки
    this.sphere(3.7, 0.6, 2.25, 0.2, M.purpleSoft, 0.5, 1, 1);
    this.sphere(3.7, 0.6, 3.75, 0.2, M.pink, 0.5, 1, 1);
    this.addInteract('sofa', sofa, 'Диван');

    // кресло-мешок
    this.sphere(5.6, 0.3, 2.0, 0.5, M.purple, 1, 0.65, 1);
    this.colliders.push({ x0: 5.15, z0: 1.55, x1: 6.05, z1: 2.45 });

    // книжный шкаф
    const shelf = new THREE.Group();
    this.group.add(shelf);
    this.box(0.3, 0, 5.55, 1.6, 1.9, 5.92, M.woodDark, true, shelf);
    const bookTex = T.book();
    for (let i = 0; i < 4; i++) {
      this.box(0.36, 0.1 + i * 0.45, 5.57, 1.54, 0.12 + i * 0.45, 5.6, M.woodLight, false, shelf);
      const b = this.plane(1.18, 0.4, lam(0xffffff, bookTex), 0.95, 0.32 + i * 0.45, 5.545, 0, shelf);
      b.rotation.y = Math.PI;
    }
    this.addInteract('bookshelf', shelf, 'Книжная полка');

    // торшер
    this.cyl(4.6, 0, 5.4, 0.15, 0.18, 0.04, M.dark);
    this.cyl(4.6, 0.04, 5.4, 0.02, 0.02, 1.45, M.dark);
    const shade = this.cyl(4.6, 1.45, 5.4, 0.16, 0.24, 0.3, lam(0xfff1d6, null, { emissive: 0xffd9a0, emissiveIntensity: 0.3 }));
    this.lampShade = shade;

    // растения
    const plant = (x, z, s = 1) => {
      const g = new THREE.Group();
      this.group.add(g);
      this.cyl(x, 0, z, 0.18 * s, 0.13 * s, 0.35 * s, M.pot, 12, g);
      for (let i = 0; i < 7; i++) {
        const l = this.sphere(x + Math.cos(i) * 0.12 * s, 0.45 * s + (i % 3) * 0.12 * s, z + Math.sin(i * 1.7) * 0.12 * s, 0.13 * s, M.green, 1, 1.6, 0.6, g);
        l.rotation.z = Math.cos(i) * 0.6;
      }
      this.colliders.push({ x0: x - 0.2 * s, z0: z - 0.2 * s, x1: x + 0.2 * s, z1: z + 0.2 * s });
      return g;
    };
    plant(0.45, 0.45);
    plant(6.6, 0.5);
    plant(6.6, 5.5, 0.9);

    // постеры
    this.plane(0.7, 1.0, lam(0xffffff, T.poster('QVK', 'night club', '#7a4fd6', '#1d1b24')), 2.6, 1.6, 5.91, Math.PI);
    this.plane(0.6, 0.85, lam(0xffffff, T.poster('♥', 'kira', '#f3a6c8', '#7a4fd6')), 5.5, 1.6, 5.91, Math.PI);
    this.plane(0.55, 0.75, lam(0xffffff, T.poster('★', 'pixel dash', '#2f6d8c', '#7a4fd6')), 6.91, 1.6, 0.5, -Math.PI / 2);

    // часы на стене
    this.clockTex = T.label('12:00', '#1d1b24', '#c9a8ff', 256, 96, 'bold 64px monospace');
    this.plane(0.5, 0.19, new THREE.MeshBasicMaterial({ map: this.clockTex }), 0.09, 2.15, 3.0, Math.PI / 2);

    // ---------- КУХНЯ ----------
    // столешница вдоль восточной стены
    this.box(11.35, 0, 0.2, 11.95, 0.88, 4.4, M.white, true);
    this.box(11.33, 0.88, 0.18, 11.95, 0.92, 4.42, M.dark);
    // плита
    const stove = new THREE.Group();
    this.group.add(stove);
    this.box(11.4, 0.92, 0.95, 11.9, 0.93, 1.85, M.black, false, stove);
    for (const [dz, dx] of [[1.15, 11.52], [1.15, 11.78], [1.62, 11.52], [1.62, 11.78]]) {
      const r = new THREE.Mesh(new THREE.TorusGeometry(0.07, 0.01, 6, 16), M.metal);
      r.rotation.x = Math.PI / 2;
      r.position.set(dx, 0.94, dz);
      stove.add(r);
    }
    // сковорода
    this.pan = this.cyl(11.55, 0.94, 1.15, 0.13, 0.12, 0.04, M.dark, 16, stove);
    this.addInteract('stove', stove, 'Плита');
    // мойка
    this.box(11.42, 0.85, 2.8, 11.88, 0.93, 3.5, M.metal);
    this.cyl(11.88, 0.93, 3.15, 0.015, 0.015, 0.3, M.metal);
    // верхние шкафчики
    this.box(11.6, 1.55, 0.2, 11.95, 2.3, 1.45, M.purpleSoft);
    this.box(11.6, 1.55, 3.55, 11.95, 2.3, 4.4, M.purpleSoft);
    // холодильник
    const fridge = new THREE.Group();
    this.group.add(fridge);
    this.box(11.15, 0, 5.1, 11.95, 1.95, 5.9, M.white, true, fridge);
    this.box(11.12, 0.9, 5.15, 11.15, 1.9, 5.85, lam(0xe8e4ee), false, fridge);
    this.box(11.1, 1.2, 5.75, 11.13, 1.6, 5.8, M.metal, false, fridge);
    // магнитики
    this.sphere(11.11, 1.7, 5.3, 0.03, M.pink);
    this.sphere(11.11, 1.55, 5.45, 0.03, M.purple);
    this.addInteract('fridge', fridge, 'Холодильник');
    // столешница у северной стены кухни
    this.box(8.4, 0, 5.4, 10.8, 0.88, 5.92, M.white, true);
    this.box(8.4, 0.88, 5.38, 10.8, 0.92, 5.92, M.dark);
    // чайник и микроволновка
    this.cyl(8.8, 0.92, 5.65, 0.09, 0.11, 0.22, M.purple);
    this.box(9.6, 0.92, 5.45, 10.3, 1.3, 5.9, M.dark);
    this.box(9.65, 0.97, 5.44, 10.05, 1.25, 5.45, lam(0x223344));
    // обеденный стол
    const table = new THREE.Group();
    this.group.add(table);
    this.box(8.6, 0.72, 2.55, 9.8, 0.76, 3.45, M.woodLight, true, table);
    for (const [x, z] of [[8.65, 2.6], [9.75, 2.6], [8.65, 3.4], [9.75, 3.4]]) this.box(x - 0.03, 0, z - 0.03, x + 0.03, 0.72, z + 0.03, M.woodDark, false, table);
    this.addInteract('table', table, 'Обеденный стол');
    // стулья
    const chair = (x, z, rot) => {
      const g = new THREE.Group();
      this.box(-0.22, 0.43, -0.22, 0.22, 0.47, 0.22, M.purple, false, g);
      this.box(-0.22, 0.47, 0.18, 0.22, 0.95, 0.22, M.purple, false, g);
      for (const [a, b] of [[-0.19, -0.19], [0.19, -0.19], [-0.19, 0.19], [0.19, 0.19]]) this.box(a - 0.02, 0, b - 0.02, a + 0.02, 0.43, b + 0.02, M.dark, false, g);
      g.position.set(x, 0, z);
      g.rotation.y = rot;
      this.group.add(g);
    };
    chair(9.2, 2.05, 0);
    chair(9.2, 3.95, Math.PI);
    // тарелки на столе (скрытые до сцены)
    this.plates = new THREE.Group();
    this.group.add(this.plates);
    for (const z of [2.8, 3.2]) {
      this.cyl(9.2, 0.76, z, 0.14, 0.11, 0.02, M.white, 20, this.plates);
      this.sphere(9.2, 0.8, z, 0.08, lam(0xe0a85a), 1, 0.3, 1, this.plates);
    }
    this.plates.visible = false;

    // ---------- СПАЛЬНЯ ----------
    const bed = new THREE.Group();
    this.group.add(bed);
    this.box(0.2, 0, 7.6, 1.95, 0.32, 9.9, M.woodDark, true, bed);
    this.box(0.25, 0.32, 7.65, 1.9, 0.5, 9.85, M.white, false, bed);
    this.blanket = this.box(0.22, 0.5, 7.6, 1.93, 0.56, 9.1, lam(0x6a45b8, T.fabric('#6a45b8', [2, 2])), false, bed);
    this.box(0.2, 0.32, 9.85, 1.95, 1.15, 9.95, M.woodDark, false, bed);
    this.sphere(0.65, 0.62, 9.55, 0.22, M.white, 1.3, 0.45, 0.8, bed);
    this.sphere(1.5, 0.62, 9.55, 0.22, M.pink, 1.3, 0.45, 0.8, bed);
    this.addInteract('bed', bed, 'Кровать');
    // плюшевый кролик
    const bunny = new THREE.Group();
    const bm = lam(0xf2eaf8);
    this.sphere(0, 0.12, 0, 0.12, bm, 1, 1, 1, bunny);
    this.sphere(0, 0.3, 0, 0.09, bm, 1, 1, 1, bunny);
    this.sphere(-0.04, 0.44, 0, 0.025, bm, 1, 3, 1, bunny);
    this.sphere(0.04, 0.44, 0, 0.025, bm, 1, 3, 1, bunny);
    this.sphere(-0.03, 0.31, 0.08, 0.012, M.black, 1, 1, 1, bunny);
    this.sphere(0.03, 0.31, 0.08, 0.012, M.black, 1, 1, 1, bunny);
    bunny.position.set(1.65, 0.55, 9.2);
    bunny.rotation.y = Math.PI * 0.85;
    this.group.add(bunny);
    this.addInteract('bunny', bunny, 'Плюшевый кролик');
    // тумбочка, лампа, дневник
    const ns = new THREE.Group();
    this.group.add(ns);
    this.box(2.05, 0, 9.35, 2.55, 0.55, 9.9, M.woodLight, true, ns);
    this.box(2.08, 0.3, 9.33, 2.52, 0.32, 9.35, M.woodDark, false, ns);
    this.addInteract('nightstand', ns, 'Тумбочка');
    this.cyl(2.4, 0.55, 9.7, 0.06, 0.08, 0.03, M.dark);
    this.cyl(2.4, 0.58, 9.7, 0.015, 0.015, 0.22, M.dark);
    this.cyl(2.4, 0.78, 9.7, 0.08, 0.12, 0.14, lam(0xfff1d6, null, { emissive: 0xffd9a0, emissiveIntensity: 0.4 }));
    this.diary = this.box(2.12, 0.55, 9.42, 2.32, 0.585, 9.66, lam(0x8c3fc7));
    this.box(2.12, 0.585, 9.42, 2.32, 0.59, 9.66, lam(0xff8fc0));
    this.addInteract('diary', this.diary, 'Дневник');
    // гирлянда над кроватью
    this.fairy = new THREE.Group();
    this.group.add(this.fairy);
    const fairyCols = [0xffd27a, 0xff9ad5, 0xb48cff, 0x9ad7ff];
    for (let i = 0; i < 26; i++) {
      const x = 0.2 + i * 0.17;
      const y = 2.35 - Math.sin((i / 25) * Math.PI) * 0.25 - (i % 5 === 0 ? 0 : 0.02);
      const b = new THREE.Mesh(new THREE.SphereGeometry(0.025, 8, 6), new THREE.MeshBasicMaterial({ color: fairyCols[i % 4] }));
      b.position.set(x, y, 9.9);
      this.fairy.add(b);
    }
    // стол с компьютером
    const desk = new THREE.Group();
    this.group.add(desk);
    this.box(2.1, 0.72, 6.1, 3.9, 0.76, 6.75, M.white, true, desk);
    for (const x of [2.15, 3.85]) this.box(x - 0.03, 0, 6.12, x + 0.03, 0.72, 6.73, M.dark, false, desk);
    this.box(2.7, 0.76, 6.2, 3.3, 0.78, 6.4, M.dark, false, desk);
    this.box(2.98, 0.78, 6.25, 3.02, 1.0, 6.29, M.dark, false, desk);
    this.box(2.55, 0.95, 6.22, 3.45, 1.45, 6.27, M.black, false, desk);
    this.pcTex = T.label('KIRA OS', '#120f1a', '#9f6bff', 512, 280, 'bold 64px monospace');
    this.pcScreen = this.plane(0.86, 0.46, new THREE.MeshBasicMaterial({ map: this.pcTex }), 3.0, 1.2, 6.276, 0, desk);
    this.box(2.6, 0.76, 6.45, 3.2, 0.78, 6.6, M.dark, false, desk);
    this.addInteract('pc', desk, 'Компьютер');
    // кресло
    this.cyl(3.0, 0, 7.15, 0.25, 0.25, 0.05, M.dark);
    this.cyl(3.0, 0.05, 7.15, 0.03, 0.03, 0.38, M.dark);
    this.box(2.75, 0.43, 6.9, 3.25, 0.5, 7.4, M.purple);
    this.box(2.75, 0.5, 7.35, 3.25, 1.05, 7.42, M.purple);
    // шкаф
    const wardrobe = new THREE.Group();
    this.group.add(wardrobe);
    this.box(4.2, 0, 8.6, 4.92, 2.15, 9.92, M.woodLight, true, wardrobe);
    this.wardDoorL = this.box(4.17, 0.05, 8.65, 4.2, 2.1, 9.25, lam(0xd7b48b), false, wardrobe);
    this.wardDoorR = this.box(4.17, 0.05, 9.27, 4.2, 2.1, 9.87, lam(0xd7b48b), false, wardrobe);
    this.box(4.14, 1.0, 9.18, 4.17, 1.2, 9.21, M.metal, false, wardrobe);
    this.box(4.14, 1.0, 9.31, 4.17, 1.2, 9.34, M.metal, false, wardrobe);
    this.addInteract('wardrobe', wardrobe, 'Шкаф');
    this.plane(0.5, 0.7, lam(0xffffff, T.poster('X', 'qvk', '#1d1b24', '#9f6bff')), 0.09, 1.7, 7.0, Math.PI / 2);

    // ---------- ВАННАЯ ----------
    const tub = new THREE.Group();
    this.group.add(tub);
    this.box(9.7, 0, 9.0, 11.95, 0.55, 9.95, M.white, true, tub);
    this.box(9.8, 0.4, 9.1, 11.85, 0.56, 9.85, lam(0xbfe0ff));
    this.addInteract('bathtub', tub, 'Ванна');
    // раковина и зеркало
    const sink = new THREE.Group();
    this.group.add(sink);
    this.box(11.45, 0, 7.15, 11.95, 0.85, 8.05, M.white, true, sink);
    this.box(11.5, 0.82, 7.3, 11.85, 0.86, 7.9, M.metal, false, sink);
    this.mirrorMat = new THREE.MeshBasicMaterial({ color: 0x9fb6cc });
    const mirror = this.box(11.9, 1.15, 7.2, 11.94, 1.95, 8.0, this.mirrorMat, false, sink);
    this.addInteract('mirror', mirror, 'Зеркало');
    // унитаз
    this.box(8.25, 0, 9.4, 8.75, 0.42, 9.95, M.white, true);
    this.box(8.25, 0.42, 9.75, 8.75, 0.85, 9.95, M.white);
    // стиралка
    this.box(11.3, 0, 6.2, 11.95, 0.85, 6.85, M.white, true);
    const porthole = new THREE.Mesh(new THREE.CircleGeometry(0.2, 20), lam(0x334455));
    porthole.position.set(11.29, 0.45, 6.52);
    porthole.rotation.y = -Math.PI / 2;
    this.group.add(porthole);
    // полотенца
    this.box(8.1, 1.0, 6.4, 8.15, 1.6, 6.9, M.pink);
    this.box(8.1, 1.0, 6.95, 8.15, 1.5, 7.2, M.purpleSoft);

    // ---------- ПРИХОЖАЯ ----------
    const door = new THREE.Group();
    this.group.add(door);
    this.doorLeaf = this.box(6.02, 0, 9.9, 6.98, 2.18, 9.98, lam(0x3a3044), false, door);
    this.cyl(6.85, 1.0, 9.86, 0.02, 0.02, 0.06, M.metal, 8, door).rotation.x = Math.PI / 2;
    this.box(6.45, 1.55, 9.88, 6.55, 1.6, 9.9, M.metal, false, door);
    this.addInteract('door', door, 'Входная дверь');
    // половик
    this.box(6.05, 0, 9.0, 6.95, 0.01, 9.75, lam(0x5b3d8f));
    // обувница + вешалка
    this.box(7.4, 0, 8.8, 7.92, 0.5, 9.85, M.woodDark, true);
    this.box(5.08, 1.6, 8.9, 5.12, 1.65, 9.7, M.woodDark);
    this.sphere(5.25, 1.25, 9.1, 0.18, lam(0x1d1b24), 0.6, 1.6, 1);
    this.sphere(5.25, 1.3, 9.45, 0.16, lam(0x7a4fd6), 0.6, 1.5, 1);
    // коробка
    const box = this.box(7.35, 0, 6.25, 7.9, 0.4, 6.8, lam(0xc9a27a), true);
    this.addInteract('box', box, 'Коробка');
    this.box(5.08, 1.1, 6.4, 5.11, 1.9, 7.1, this.mirrorMat);

    // ---------- БАЛКОН ----------
    this.box(1, -0.15, -2.6, 6, 0, 0, lam(0xcfc8d8, T.tiles([1, 1], '#cfc8d8', '#9a94a6', 5)));
    const rail = M.dark;
    this.box(1, 0, -2.65, 6, 1.05, -2.55, M.glass, true);
    this.box(0.95, 0, -2.65, 1.05, 1.05, 0, M.glass, true);
    this.box(5.95, 0, -2.65, 6.05, 1.05, 0, M.glass, true);
    this.box(1, 1.0, -2.67, 6, 1.06, -2.53, rail);
    this.box(0.93, 1.0, -2.65, 1.07, 1.06, 0, rail);
    this.box(5.93, 1.0, -2.65, 6.07, 1.06, 0, rail);
    for (let x = 1; x <= 6; x += 0.5) this.box(x - 0.02, 0, -2.62, x + 0.02, 1.0, -2.58, rail);
    // балконная гирлянда
    this.balconyLights = new THREE.Group();
    this.group.add(this.balconyLights);
    for (let i = 0; i < 18; i++) {
      const b = new THREE.Mesh(new THREE.SphereGeometry(0.03, 8, 6), new THREE.MeshBasicMaterial({ color: fairyCols[i % 4] }));
      b.position.set(1.2 + i * 0.27, 2.35 - Math.sin((i / 17) * Math.PI) * 0.3, -0.12);
      this.balconyLights.add(b);
    }
    this.balconyLights.visible = false;
    // цветы в горшках
    const flowers = new THREE.Group();
    this.group.add(flowers);
    for (let i = 0; i < 3; i++) {
      const x = 1.4 + i * 0.4;
      this.box(x - 0.15, 0, -2.45, x + 0.15, 0.25, -2.15, M.pot, false, flowers);
      for (let j = 0; j < 4; j++) {
        this.cyl(x + (j - 1.5) * 0.06, 0.25, -2.3, 0.008, 0.008, 0.25, M.green, 4, flowers);
        this.sphere(x + (j - 1.5) * 0.06, 0.52, -2.3, 0.045, j % 2 ? M.pink : M.purpleSoft, 1, 1, 1, flowers);
      }
    }
    this.colliders.push({ x0: 1.2, z0: -2.5, x1: 2.4, z1: -2.1 });
    this.flowers = flowers;
    this.addInteract('flowers', flowers, 'Цветы');
    // столик для свидания (скрыт до 6 дня)
    this.dateSet = new THREE.Group();
    this.group.add(this.dateSet);
    this.box(3.2, 0.25, -1.75, 3.9, 0.3, -1.15, M.woodLight, false, this.dateSet);
    this.box(3.5, 0, -1.5, 3.6, 0.25, -1.4, M.woodDark, false, this.dateSet);
    this.cyl(3.35, 0.3, -1.6, 0.025, 0.025, 0.12, lam(0xfff5e0, null, { emissive: 0xffc070 }), 8, this.dateSet);
    this.cyl(3.75, 0.3, -1.3, 0.025, 0.025, 0.1, lam(0xfff5e0, null, { emissive: 0xffc070 }), 8, this.dateSet);
    this.box(2.6, 0, -1.9, 4.5, 0.06, -0.95, lam(0x6a45b8), false, this.dateSet);
    this.dateSet.visible = false;
    this.addInteract('balcony', this.box(3.0, 0, -0.05, 4.3, 2.3, 0.05, new THREE.MeshBasicMaterial({ visible: false })), 'Балкон');

    // ---------- Внешний мир ----------
    this.skyMats = {
      morning: T.sky('#9cc6ff', '#ffd6e8', '#ffeacc'),
      day: T.sky('#6fb3ff', '#a8d4ff', '#e2f0ff'),
      evening: T.sky('#3d2a6e', '#ff8a8a', '#ffc27a'),
      night: T.sky('#05040f', '#151035', '#2a1a4a', true),
      glitch: T.sky('#200010', '#4a0a3a', '#ff2a6a', true),
    };
    this.skyMat = new THREE.MeshBasicMaterial({ map: this.skyMats.day, side: THREE.BackSide, fog: false });
    const sky = new THREE.Mesh(new THREE.SphereGeometry(140, 32, 16), this.skyMat);
    sky.position.set(6, 0, 5);
    this.sky = sky;
    this.scene.add(sky);
    // город внизу
    const city = new THREE.Group();
    this.city = city;
    this.scene.add(city);
    const winTex = (() => {
      const c = document.createElement('canvas'); c.width = 64; c.height = 128;
      const x = c.getContext('2d');
      x.fillStyle = '#1a1626'; x.fillRect(0, 0, 64, 128);
      for (let i = 0; i < 8; i++) for (let j = 0; j < 16; j++) {
        x.fillStyle = Math.random() < 0.45 ? `hsl(${40 + Math.random() * 20},90%,${60 + Math.random() * 20}%)` : '#272235';
        x.fillRect(i * 8 + 2, j * 8 + 2, 4, 5);
      }
      const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace;
      t.wrapS = t.wrapT = THREE.RepeatWrapping; t.magFilter = THREE.NearestFilter;
      return t;
    })();
    this.cityMat = new THREE.MeshLambertMaterial({ color: 0x9a92b0, map: winTex, emissive: 0xffffff, emissiveMap: winTex, emissiveIntensity: 0 });
    for (let i = 0; i < 90; i++) {
      const a = Math.random() * Math.PI * 2;
      const r = 25 + Math.random() * 70;
      const x = 6 + Math.cos(a) * r, z = 5 + Math.sin(a) * r;
      const h = 15 + Math.random() * 30;
      const w = 4 + Math.random() * 8;
      const b = new THREE.Mesh(new THREE.BoxGeometry(w, h, w), this.cityMat);
      b.position.set(x, -28 + h / 2, z);
      b.material = this.cityMat;
      const uv = b.geometry.attributes.uv;
      for (let k = 0; k < uv.count; k++) uv.setXY(k, uv.getX(k) * w / 8, uv.getY(k) * h / 16);
      city.add(b);
    }
    const ground = new THREE.Mesh(new THREE.CircleGeometry(140, 32), new THREE.MeshLambertMaterial({ color: 0x2a2638 }));
    ground.rotation.x = -Math.PI / 2;
    ground.position.set(6, -28, 5);
    city.add(ground);
    // корпус здания снаружи
    this.box(-0.3, -28, -0.2, 12.3, -0.1, 10.3, lam(0xd8d0e0));

    // ---------- Свет ----------
    this.hemi = new THREE.HemisphereLight(0xffffff, 0x8a7a9a, 1.2);
    this.scene.add(this.hemi);
    this.sun = new THREE.DirectionalLight(0xfff0e0, 1.5);
    this.sun.position.set(14, 12, -16);
    this.sun.target.position.set(5, 0, 4);
    this.sun.castShadow = true;
    this.sun.shadow.mapSize.set(2048, 2048);
    const sc = this.sun.shadow.camera;
    sc.left = -12; sc.right = 12; sc.top = 12; sc.bottom = -12; sc.near = 1; sc.far = 60;
    this.sun.shadow.bias = -0.0008;
    this.scene.add(this.sun, this.sun.target);
    const lampPos = { living: [3.0, 2.55, 3.0], kitchen: [9.5, 2.55, 3.0], bedroom: [2.5, 2.55, 8.0], hall: [6.5, 2.55, 8.0], bath: [10, 2.55, 8.0], balcony: [3.6, 2.2, -0.6] };
    for (const [name, [x, y, z]] of Object.entries(lampPos)) {
      const l = new THREE.PointLight(0xffd9b0, 0, 9, 1.4);
      l.position.set(x, y, z);
      l.userData.name = name;
      this.scene.add(l);
      this.lamps.push(l);
      if (name !== 'balcony') {
        const fx = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.3, 0.08, 20), lam(0xfff5e8, null, { emissive: 0xffe8c8, emissiveIntensity: 0.6 }));
        fx.position.set(x, H - 0.04, z);
        this.group.add(fx);
      }
    }
    this.scene.fog = new THREE.Fog(0xbfd8ff, 30, 160);

    // ---------- Навигация (для Киры) ----------
    this.nav = {
      living: [3.0, 3.0], sofaFront: [2.9, 2.6], tvFront: [1.2, 3.0], balconyDoor: [3.65, 0.6],
      balconyIn: [3.65, -0.6], balcony: [3.6, -1.6], kitchen: [8.4, 1.5], kitchenMid: [10.2, 3.0], stove: [10.9, 1.4],
      fridge: [10.6, 5.0], hallSouth: [6.2, 5.2], hall: [6.5, 7.9], doorIn: [6.5, 9.3],
      bedDoor: [4.4, 7.9], bedroom: [3.0, 8.3], bedSide: [2.3, 8.7], deskFront: [3.0, 7.6], wardrobeFront: [3.6, 9.2],
      bathDoor: [8.6, 7.9], bath: [10.0, 7.9], mirrorFront: [11.0, 7.6], livingEast: [5.6, 3.6], window: [1.6, 0.8],
    };
    const E = [
      ['living', 'sofaFront'], ['living', 'tvFront'], ['living', 'balconyDoor'], ['balconyDoor', 'balconyIn'], ['balconyIn', 'balcony'],
      ['living', 'livingEast'], ['livingEast', 'kitchen'], ['livingEast', 'kitchenMid'], ['livingEast', 'hallSouth'], ['kitchen', 'kitchenMid'], ['kitchenMid', 'stove'], ['kitchenMid', 'fridge'],
      ['kitchen', 'stove'], ['hallSouth', 'hall'], ['hall', 'doorIn'], ['hall', 'bedDoor'], ['bedDoor', 'bedroom'], ['bedroom', 'bedSide'], ['bedroom', 'deskFront'],
      ['bedroom', 'wardrobeFront'], ['hall', 'bathDoor'], ['bathDoor', 'bath'], ['bath', 'mirrorFront'], ['living', 'window'], ['sofaFront', 'tvFront'], ['kitchenMid', 'hallSouth'],
    ];
    this.navEdges = {};
    for (const k of Object.keys(this.nav)) this.navEdges[k] = [];
    for (const [a, b] of E) { this.navEdges[a].push(b); this.navEdges[b].push(a); }
  }

  nearestNode(x, z) {
    let best = null, bd = Infinity;
    for (const [k, [nx, nz]] of Object.entries(this.nav)) {
      const d = (nx - x) ** 2 + (nz - z) ** 2;
      if (d < bd && this.clearLine(x, z, nx, nz)) { bd = d; best = k; }
    }
    if (!best) for (const [k, [nx, nz]] of Object.entries(this.nav)) {
      const d = (nx - x) ** 2 + (nz - z) ** 2;
      if (d < bd) { bd = d; best = k; }
    }
    return best;
  }

  clearLine(x0, z0, x1, z1) {
    const steps = Math.ceil(Math.hypot(x1 - x0, z1 - z0) / 0.2);
    for (let i = 1; i < steps; i++) {
      const x = x0 + ((x1 - x0) * i) / steps, z = z0 + ((z1 - z0) * i) / steps;
      for (const c of this.colliders) if (x > c.x0 - 0.05 && x < c.x1 + 0.05 && z > c.z0 - 0.05 && z < c.z1 + 0.05) return false;
    }
    return true;
  }

  // Путь от (x,z) до точки (tx,tz) через граф
  findPath(x, z, tx, tz) {
    if (this.clearLine(x, z, tx, tz)) return [{ x: tx, z: tz }];
    const s = this.nearestNode(x, z), t = this.nearestNode(tx, tz);
    const prev = { [s]: null };
    const q = [s];
    while (q.length) {
      const n = q.shift();
      if (n === t) break;
      for (const m of this.navEdges[n]) if (!(m in prev)) { prev[m] = n; q.push(m); }
    }
    const nodes = [];
    for (let n = t; n; n = prev[n]) nodes.unshift(n);
    const pts = nodes.map((n) => ({ x: this.nav[n][0], z: this.nav[n][1] }));
    pts.push({ x: tx, z: tz });
    // сглаживание: пропускаем узлы, если есть прямая видимость
    const out = [];
    let cx = x, cz = z, i = 0;
    while (i < pts.length) {
      let j = pts.length - 1;
      while (j > i && !this.clearLine(cx, cz, pts[j].x, pts[j].z)) j--;
      out.push(pts[j]);
      cx = pts[j].x; cz = pts[j].z;
      i = j + 1;
    }
    return out;
  }

  // Разрешение коллизий игрока (круг против AABB)
  collide(pos, r) {
    for (let iter = 0; iter < 2; iter++) {
      for (const c of this.colliders) {
        if (c.off) continue;
        const nx = Math.max(c.x0, Math.min(pos.x, c.x1));
        const nz = Math.max(c.z0, Math.min(pos.z, c.z1));
        const dx = pos.x - nx, dz = pos.z - nz;
        const d2 = dx * dx + dz * dz;
        if (d2 < r * r) {
          if (d2 > 1e-8) {
            const d = Math.sqrt(d2);
            pos.x = nx + (dx / d) * r;
            pos.z = nz + (dz / d) * r;
          } else {
            // центр внутри — выталкиваем по кратчайшей оси
            const pen = [pos.x - c.x0, c.x1 - pos.x, pos.z - c.z0, c.z1 - pos.z];
            const m = Math.min(...pen);
            if (m === pen[0]) pos.x = c.x0 - r; else if (m === pen[1]) pos.x = c.x1 + r;
            else if (m === pen[2]) pos.z = c.z0 - r; else pos.z = c.z1 + r;
          }
        }
      }
    }
    // за пределы квартиры не выходим
    pos.x = Math.max(-0.5, Math.min(12.5, pos.x));
    pos.z = Math.max(-2.5, Math.min(10.5, pos.z));
  }

  setPhase(p) {
    this.phase = p;
    const cfg = {
      morning: { sky: 'morning', hemi: [0xfff0f5, 0x9a8aa8, 1.1], sun: [0xffd0b0, 1.3, [14, 6, -16]], lamps: 0, fog: 0xffd6e8, cityGlow: 0.1 },
      day: { sky: 'day', hemi: [0xffffff, 0x9a8aa8, 1.25], sun: [0xfff4e8, 1.6, [10, 14, -14]], lamps: 0, fog: 0xbfd8ff, cityGlow: 0 },
      evening: { sky: 'evening', hemi: [0xffd0c0, 0x5a4a6a, 0.75], sun: [0xff9a6a, 0.9, [-14, 4, -12]], lamps: 1.1, fog: 0xff9a9a, cityGlow: 0.6 },
      night: { sky: 'night', hemi: [0x8a7ac0, 0x2a2238, 0.45], sun: [0x8a9aff, 0.25, [-6, 14, -10]], lamps: 1.6, fog: 0x151035, cityGlow: 1.2 },
      glitch: { sky: 'glitch', hemi: [0xff8ab0, 0x2a0a20, 0.55], sun: [0xff4a8a, 0.4, [-6, 14, -10]], lamps: 1.0, fog: 0x4a0a3a, cityGlow: 0.3 },
    }[p];
    this.skyMat.map = this.skyMats[cfg.sky];
    this.skyMat.needsUpdate = true;
    this.hemi.color.setHex(cfg.hemi[0]);
    this.hemi.groundColor.setHex(cfg.hemi[1]);
    this.hemi.intensity = cfg.hemi[2];
    this.sun.color.setHex(cfg.sun[0]);
    this.sun.intensity = cfg.sun[1];
    this.sun.position.set(...cfg.sun[2]).add(new THREE.Vector3(5, 0, 4));
    this.lampLevel = cfg.lamps;
    for (const l of this.lamps) l.intensity = l.userData.name === 'balcony' ? (this.balconyLights.visible ? cfg.lamps * 0.8 : 0) : cfg.lamps;
    this.scene.fog.color.setHex(cfg.fog);
    this.cityMat.emissiveIntensity = cfg.cityGlow;
    this.fairy.visible = p === 'evening' || p === 'night' || p === 'glitch';
  }

  setClock(text) {
    const c = this.clockTex.userData.canvas;
    const x = c.getContext('2d');
    x.fillStyle = '#1d1b24'; x.fillRect(0, 0, c.width, c.height);
    x.fillStyle = '#c9a8ff'; x.font = 'bold 64px monospace'; x.textAlign = 'center'; x.textBaseline = 'middle';
    x.fillText(text, c.width / 2, c.height / 2 + 2);
    this.clockTex.needsUpdate = true;
  }

  // Картинка на экране телевизора
  setTV(mode) {
    const c = this.tvTex.userData.canvas;
    const x = c.getContext('2d');
    const w = c.width, h = c.height;
    this.tvMode = mode;
    if (mode === 'off') { x.fillStyle = '#05040a'; x.fillRect(0, 0, w, h); }
    else if (mode === 'game') {
      x.fillStyle = '#120f22'; x.fillRect(0, 0, w, h);
      x.fillStyle = '#9f6bff'; x.fillRect(20, h / 2 - 40, 10, 80);
      x.fillStyle = '#ff8fc0'; x.fillRect(w - 30, h / 2 - 30, 10, 80);
      x.fillStyle = '#fff'; x.fillRect(w / 2 - 5, h / 2 - 5, 10, 10);
      x.font = 'bold 36px monospace'; x.textAlign = 'center'; x.fillText('PIXEL PONG', w / 2, 50);
    } else if (mode === 'menu') {
      const g = x.createLinearGradient(0, 0, w, h); g.addColorStop(0, '#2a1a4a'); g.addColorStop(1, '#7a4fd6');
      x.fillStyle = g; x.fillRect(0, 0, w, h);
      x.fillStyle = '#fff'; x.font = 'bold 48px Arial'; x.textAlign = 'center'; x.fillText('QVK  STATION', w / 2, h / 2);
      x.font = '22px Arial'; x.fillText('нажми любую кнопку', w / 2, h / 2 + 40);
    } else if (mode === 'movie') {
      const g = x.createLinearGradient(0, 0, 0, h); g.addColorStop(0, '#0b1a3a'); g.addColorStop(1, '#3a1a4a');
      x.fillStyle = g; x.fillRect(0, 0, w, h);
      x.fillStyle = '#ffeec8'; x.beginPath(); x.arc(380, 80, 36, 0, Math.PI * 2); x.fill();
      x.fillStyle = '#0a0812';
      x.beginPath(); x.moveTo(0, h); x.lineTo(120, 150); x.lineTo(220, 210); x.lineTo(330, 130); x.lineTo(512, h); x.fill();
      x.fillStyle = '#fff'; x.font = 'italic 20px serif'; x.textAlign = 'center'; x.fillText('«Когда звёзды гаснут»', w / 2, h - 20);
    } else if (mode === 'static' || mode === 'glitch') {
      const img = x.createImageData(w, h);
      for (let i = 0; i < img.data.length; i += 4) {
        const v = Math.random() * 255;
        img.data[i] = mode === 'glitch' ? v : v * 0.9; img.data[i + 1] = mode === 'glitch' ? v * 0.2 : v * 0.9; img.data[i + 2] = v; img.data[i + 3] = 255;
      }
      x.putImageData(img, 0, 0);
    } else if (typeof mode === 'string') {
      x.fillStyle = '#120f1a'; x.fillRect(0, 0, w, h);
      x.fillStyle = '#ff5a8a'; x.font = 'bold 30px monospace'; x.textAlign = 'center';
      mode.split('\n').forEach((l, i) => x.fillText(l, w / 2, 100 + i * 40));
    }
    this.tvTex.needsUpdate = true;
  }

  setPC(lines, color = '#9f6bff') {
    const c = this.pcTex.userData.canvas;
    const x = c.getContext('2d');
    x.fillStyle = '#120f1a'; x.fillRect(0, 0, c.width, c.height);
    x.fillStyle = color; x.font = 'bold 30px monospace'; x.textAlign = 'left';
    lines.forEach((l, i) => x.fillText(l, 24, 50 + i * 40));
    this.pcTex.needsUpdate = true;
  }

  // Уровень «сбоев» мира: 0 — норма, 3 — мир разваливается
  setGlitch(level) {
    this.glitchLevel = level;
    if (level >= 3 && !this.glitchCubes) {
      this.glitchCubes = new THREE.Group();
      const gm = new THREE.MeshBasicMaterial({ map: T.glitch(), transparent: true, opacity: 0.85 });
      for (let i = 0; i < 70; i++) {
        const s = 0.1 + Math.random() * 0.5;
        const m = new THREE.Mesh(new THREE.BoxGeometry(s, s, s), gm);
        m.position.set(Math.random() * 12, Math.random() * 2.6, Math.random() * 10);
        m.userData.spin = (Math.random() - 0.5) * 2;
        m.userData.base = m.position.y;
        this.glitchCubes.add(m);
      }
      this.scene.add(this.glitchCubes);
    }
    if (this.glitchCubes) this.glitchCubes.visible = level >= 3;
  }

  update(dt) {
    this.time += dt;
    const t = this.time;
    // мерцание гирлянды
    let i = 0;
    for (const b of this.fairy.children) { b.scale.setScalar(0.8 + Math.sin(t * 2 + i++) * 0.25); }
    if (this.glitchLevel > 0) {
      const lvl = this.glitchLevel;
      if (Math.random() < 0.02 * lvl) {
        for (const l of this.lamps) if (l.userData.name !== 'balcony') l.intensity = Math.random() < 0.5 ? 0 : this.lampLevel * 1.5;
      } else if (Math.random() < 0.1) {
        for (const l of this.lamps) if (l.userData.name !== 'balcony') l.intensity = this.lampLevel;
      }
      if (this.glitchCubes && this.glitchCubes.visible) {
        for (const c of this.glitchCubes.children) {
          c.rotation.x += c.userData.spin * dt;
          c.rotation.y += c.userData.spin * dt * 0.7;
          c.position.y = c.userData.base + Math.sin(t + c.userData.spin * 5) * 0.2;
        }
      }
      if (this.tvMode === 'glitch' && Math.random() < 0.2) this.setTV('glitch');
    }
    if (this.tvMode === 'static' && Math.random() < 0.3) this.setTV('static');
  }
}
