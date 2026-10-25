import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js';
import { GTAOPass } from 'three/examples/jsm/postprocessing/GTAOPass.js';
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js';
import { World } from './world.js';
import { Kira } from './kira.js';
import { Player } from './player.js';
import { UI } from './ui.js';
import { Audio } from './audio.js';
import { MG } from './minigames.js';
import { DAYS } from './story.js';

const $ = (s) => document.querySelector(s);
const SAVE_KEY = 'kira_save_v1';
const ALBUM_KEY = 'kira_album_v1';
const SET_KEY = 'kira_settings_v1';

// Точки, куда может встать/сесть Кира
export const SPOTS = {
  sofaK: { x: 3.55, z: 2.6, f: -Math.PI / 2, pose: 'sit' },
  sofaP: { x: 3.55, z: 3.45, yaw: Math.PI / 2 },
  living: { x: 3.0, z: 3.0, f: 0 },
  livingTV: { x: 1.3, z: 3.0, f: Math.PI / 2 },
  window: { x: 1.6, z: 0.6, f: Math.PI },
  kitchenStove: { x: 10.95, z: 1.4, f: Math.PI / 2, pose: 'cook' },
  kitchenFridge: { x: 10.6, z: 5.0, f: Math.PI / 2 },
  tableK: { x: 9.2, z: 3.95, f: Math.PI, pose: 'sit' },
  tableP: { x: 9.2, z: 2.05, yaw: Math.PI },
  kitchen: { x: 9.6, z: 1.5, f: 0 },
  bedroom: { x: 3.0, z: 8.3, f: Math.PI },
  bedSit: { x: 1.05, z: 8.3, f: Math.PI / 2, pose: 'sit' },
  bedLie: { x: 1.05, z: 8.4, f: 0, pose: 'lie' },
  desk: { x: 3.0, z: 7.25, f: Math.PI, pose: 'sit' },
  wardrobe: { x: 3.7, z: 9.2, f: Math.PI / 2 },
  mirror: { x: 11.0, z: 7.6, f: Math.PI / 2 },
  bath: { x: 10.0, z: 7.9, f: 0 },
  hall: { x: 6.5, z: 7.6, f: Math.PI },
  door: { x: 6.5, z: 9.3, f: 0 },
  balcony: { x: 3.4, z: -1.6, f: Math.PI },
  balconySit: { x: 3.2, z: -1.45, f: Math.PI / 2, pose: 'sit' },
  bean: { x: 5.6, z: 2.6, f: -Math.PI / 2 },
};

class Game {
  constructor() {
    this.audio = new Audio();
    this.state = this.freshState();
    this.settings = { master: 80, music: 60, sfx: 80, voice: 70, text: 55, sens: 100, quality: 'high' };
    try { Object.assign(this.settings, JSON.parse(localStorage.getItem(SET_KEY) || '{}')); } catch (e) { /* пусто */ }

    const hq = this.settings.quality !== 'low';
    this.renderer = new THREE.WebGLRenderer({ antialias: hq, preserveDrawingBuffer: true });
    this.renderer.setPixelRatio(hq ? Math.min(2, devicePixelRatio) : 1);
    this.renderer.setSize(innerWidth, innerHeight);
    this.renderer.shadowMap.enabled = hq;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 0.95;
    this.renderer.domElement.className = 'three';
    $('#app').prepend(this.renderer.domElement);

    this.scene = new THREE.Scene();
    // мягкие отражения окружения для всех материалов
    const pmrem = new THREE.PMREMGenerator(this.renderer);
    this.scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
    this.scene.environmentIntensity = 0.5;
    this.camera = new THREE.PerspectiveCamera(70, innerWidth / innerHeight, 0.05, 400);
    // мягкий заполняющий свет от камеры — лицо Киры не уходит в тень в диалогах
    this.fill = new THREE.PointLight(0xfff0f4, 0.9, 4, 1.6);
    this.camera.add(this.fill);
    this.scene.add(this.camera);
    this.world = new World(this.scene);
    this.kira = new Kira(this.scene);
    this.player = new Player(this.camera, this.renderer.domElement, this.world);
    this.ui = new UI(this);
    this.raycaster = new THREE.Raycaster();
    this.raycaster.far = 2.6;
    this.handlers = {};
    this.roaming = false;
    this.busy = false;
    this.inMinigame = false;
    this.glitchT = 0;
    this.glitchAmp = 0;
    this.glitchBase = 0;
    this.applySettings();
    this.setQuality(this.settings.quality || 'high');

    addEventListener('resize', () => {
      this.camera.aspect = innerWidth / innerHeight;
      this.camera.updateProjectionMatrix();
      this.renderer.setSize(innerWidth, innerHeight);
      if (this.composer) this.composer.setSize(innerWidth, innerHeight);
      const gc = $('#glitch'); gc.width = innerWidth / 3; gc.height = innerHeight / 3;
    });
    const gc = $('#glitch'); gc.width = innerWidth / 3; gc.height = innerHeight / 3;
    this.glitchCtx = gc.getContext('2d');

    this.renderer.domElement.addEventListener('mousedown', (e) => {
      if (e.button !== 0) return;
      // клик продолжает диалог только если реплика действительно ждёт клика
      if (this.ui.waiting || this.ui.typing) { this.ui.advance(); return; }
      if (this.ui.choiceState) return;
      this.ui.hideDialog();
      if (this.photoMode) { if (this.player.locked) this.photoMode.snap(); else this.player.lock(); return; }
      if (!this.roaming && !this.freeLook) return;
      if (!this.player.locked) { this.player.lock(); return; }
      this.tryInteract();
    });
    addEventListener('keydown', (e) => {
      if (e.code === 'KeyE' && this.roaming && !this.busy && this.player.locked) this.tryInteract();
      if (e.code === 'Escape' && !$('#pause').classList.contains('hidden')) this.resume();
      // F9 — отладочный переход на следующий день (только в браузере с ?debug)
      if (e.code === 'F9' && location.search.includes('debug')) { this.debugSkip = true; }
    });
    document.addEventListener('pointerlockchange', () => {
      const locked = this.player.locked;
      $('#clickhint').style.display = !locked && (this.roaming || this.freeLook || this.photoMode) && !this.busy ? 'block' : 'none';
      if (!locked && this.roaming && !this.busy && !this.inMinigame && !this.paused && !this.photoMode && !this.expectUnlock) this.pause();
      this.expectUnlock = false;
    });

    this.bindMenus();
    this.clock = new THREE.Clock();
    this.loop = this.loop.bind(this);
    requestAnimationFrame(this.loop);
  }

  // Графика: high — AO + свечение + MSAA, medium — свечение + MSAA, low — без пост-обработки
  setQuality(q) {
    this.quality = q;
    const r = this.renderer;
    r.setPixelRatio(q === 'low' ? 1 : Math.min(2, devicePixelRatio));
    r.shadowMap.enabled = q !== 'low';
    r.shadowMap.needsUpdate = true;
    this.scene.traverse((o) => { if (o.material) [].concat(o.material).forEach((m) => (m.needsUpdate = true)); });
    if (this.composer) { this.composer.dispose(); this.composer = null; }
    if (q === 'low') return;
    const size = r.getDrawingBufferSize(new THREE.Vector2());
    const rt = new THREE.WebGLRenderTarget(size.x, size.y, { type: THREE.HalfFloatType, samples: 4 });
    const c = new EffectComposer(r, rt);
    c.setPixelRatio(r.getPixelRatio());
    c.setSize(innerWidth, innerHeight);
    c.addPass(new RenderPass(this.scene, this.camera));
    if (q === 'high') {
      const ao = new GTAOPass(this.scene, this.camera, innerWidth, innerHeight);
      ao.updateGtaoMaterial({ radius: 0.35, distanceExponent: 1.5, thickness: 1.5, scale: 1.2, samples: 12 });
      ao.blendIntensity = 0.85;
      c.addPass(ao);
    }
    c.addPass(new UnrealBloomPass(new THREE.Vector2(innerWidth, innerHeight), 0.32, 0.55, 0.88));
    c.addPass(new OutputPass());
    this.composer = c;
  }

  renderFrame() {
    if (this.composer) this.composer.render();
    else this.renderer.render(this.scene, this.camera);
  }

  freshState() {
    return { name: '', day: 0, scene: 0, aff: 30, flags: {}, playTime: 0 };
  }

  // ---------- Настройки ----------
  applySettings() {
    const s = this.settings;
    this.audio.vol = { master: s.master / 100, music: s.music / 100, sfx: s.sfx / 100, voice: s.voice / 100 };
    this.audio.applyVolume();
    this.ui.textSpeed = s.text / 100;
    this.player.sens = s.sens / 100;
    try { localStorage.setItem(SET_KEY, JSON.stringify(s)); } catch (e) { /* пусто */ }
  }

  bindMenus() {
    const menu = $('#menu');
    menu.addEventListener('click', async (e) => {
      const act = e.target.dataset && e.target.dataset.act;
      if (!act) return;
      this.audio.init();
      this.audio.sfx('click');
      if (act === 'new') this.newGame();
      if (act === 'continue') this.continueGame();
      if (act === 'settings') this.openSettings();
      if (act === 'gallery') this.openGallery();
      if (act === 'quit') { if (window.kiraApp) window.kiraApp.quit(); else window.close(); }
    });
    menu.addEventListener('mouseover', (e) => { if (e.target.tagName === 'BUTTON' && this.audio.ctx) this.audio.sfx('hover'); });
    $('#pause').addEventListener('click', (e) => {
      const act = e.target.dataset.act;
      if (act === 'resume') this.resume();
      if (act === 'settings') this.openSettings();
      if (act === 'tomenu') { this.save(); location.reload(); }
    });
    const st = $('#settings');
    st.querySelectorAll('input,select').forEach((el) => {
      const k = el.dataset.k;
      el.value = this.settings[k];
      el.addEventListener('input', () => {
        this.settings[k] = el.tagName === 'SELECT' ? el.value : +el.value;
        this.applySettings();
        if (k === 'quality') this.setQuality(el.value);
      });
    });
    st.querySelector('[data-act=close]').addEventListener('click', () => st.classList.add('hidden'));
    $('#gallery [data-act=close]').addEventListener('click', () => $('#gallery').classList.add('hidden'));
  }

  openSettings() { $('#settings').classList.remove('hidden'); }
  openGallery() {
    const grid = $('#gallery .grid');
    const album = this.album();
    grid.innerHTML = album.length ? '' : '<div class="empty">Здесь появятся ваши совместные фото и рисунки.</div>';
    for (const p of album) {
      const d = document.createElement('div');
      d.className = 'ph';
      d.innerHTML = `<img src="${p.img}"><div></div>`;
      d.querySelector('div').textContent = p.cap;
      grid.appendChild(d);
    }
    $('#gallery').classList.remove('hidden');
  }

  album() { try { return JSON.parse(localStorage.getItem(ALBUM_KEY) || '[]'); } catch (e) { return []; } }
  addToAlbum(img, cap) {
    const a = this.album();
    a.push({ img, cap });
    while (a.length > 24) a.shift();
    try { localStorage.setItem(ALBUM_KEY, JSON.stringify(a)); } catch (e) { /* переполнено */ }
  }

  showMenu() {
    if (this.started) return;
    $('#loading').classList.add('hidden');
    $('#menu').classList.remove('hidden');
    const has = !!localStorage.getItem(SAVE_KEY);
    $('#menu [data-act=continue]').classList.toggle('hidden', !has);
    // фон меню: гостиная, Кира машет
    this.world.setPhase('evening');
    this.kira.placeAt(3.0, 2.2, Math.PI * 0.9);
    this.kira.setPose('wave');
    this.kira.setEmotion('happy');
    this.menuCam = true;
  }

  async newGame() {
    $('#menu').classList.add('hidden');
    this.menuCam = false;
    if (localStorage.getItem(SAVE_KEY)) localStorage.removeItem(SAVE_KEY);
    this.state = this.freshState();
    this.state.name = await this.ui.askName();
    this.start();
  }

  continueGame() {
    try { this.state = { ...this.freshState(), ...JSON.parse(localStorage.getItem(SAVE_KEY)) }; } catch (e) { this.state = this.freshState(); }
    $('#menu').classList.add('hidden');
    this.menuCam = false;
    this.start();
  }

  save() {
    try { localStorage.setItem(SAVE_KEY, JSON.stringify(this.state)); } catch (e) { /* пусто */ }
  }

  async start() {
    this.started = true;
    $('#menu').classList.add('hidden');
    $('#loading').classList.add('hidden');
    this.audio.init();
    $('#hud').classList.remove('hidden');
    this.ui.setAffection(this.state.aff);
    this.kira.setPose('idle');
    this.kira.lookTarget = this.camera.position;
    try {
      await this.runStory();
    } catch (e) {
      console.error(e);
      this.ui.toast('Ошибка: ' + e.message);
    }
  }

  async runStory() {
    const st = this.state;
    for (let d = st.day; d < DAYS.length; d++) {
      const day = DAYS[d];
      const from = d === st.day ? st.scene : 0;
      for (let s = from; s < day.scenes.length; s++) {
        st.day = d;
        st.scene = s;
        this.save();
        this.setDayLabel();
        await day.scenes[s](this);
      }
      // день пройден
      st.day = d + 1;
      st.scene = 0;
      this.save();
    }
    localStorage.removeItem(SAVE_KEY);
  }

  setDayLabel() {
    const day = DAYS[this.state.day];
    this.ui.setDayLabel(day ? `${day.label}` : '');
  }

  // ---------- Пауза ----------
  pause() {
    this.paused = true;
    $('#pause').classList.remove('hidden');
  }
  resume() {
    this.paused = false;
    $('#pause').classList.add('hidden');
    if (this.roaming || this.photoMode) this.player.lock();
  }

  // ---------- API для сценария ----------
  k(text, e = 'normal', pose) { return this.ui.say('k', text, { e, pose }); }
  p(text) { return this.ui.say('p', text); }
  n(text) { return this.ui.say('n', text); }
  sys(text) { return this.ui.say('g', text, { name: 'SYSTEM' }); }
  wait(s) { return new Promise((r) => setTimeout(r, s * 1000)); }

  // Выбор с влиянием на привязанность: [[текст, Δ], ...]
  async ask(opts) {
    this.ui.hideDialog();
    if (this.player.locked) { this.expectUnlock = true; this.player.unlock(); }
    const i = await this.ui.choose(opts.map((o) => ({ t: Array.isArray(o) ? o[0] : o })));
    const o = opts[i];
    if (Array.isArray(o) && o[1]) this.aff(o[1]);
    return i;
  }

  aff(d, silent = false) {
    this.state.aff = Math.max(0, Math.min(100, this.state.aff + d));
    this.ui.setAffection(this.state.aff);
    if (silent) return;
    if (d >= 3) { this.ui.toast('Кире это очень понравилось ♥♥', 'love'); this.audio.sfx('heart'); }
    else if (d > 0) { this.ui.toast('Кире это понравилось ♥', 'love'); this.audio.sfx('heart'); }
    else if (d < 0) { this.ui.toast('Кира это запомнит…', 'sad'); this.audio.sfx('sad'); }
  }

  flag(k, v) { if (v === undefined) return this.state.flags[k]; this.state.flags[k] = v; }

  // Кира идёт к точке (имя из SPOTS или {x,z,f,pose})
  async kiraGo(spot, opts = {}) {
    const s = typeof spot === 'string' ? SPOTS[spot] : spot;
    const k = this.kira;
    k.setPose('idle');
    const path = this.world.findPath(k.root.position.x, k.root.position.z, s.x, s.z);
    k.speed = opts.run ? 2.8 : 1.5;
    await k.walkPath(path);
    if (s.f !== undefined) k.targetFacing = s.f;
    if (s.pose) k.setPose(s.pose); else k.setPose(opts.pose || 'idle');
    await this.wait(0.3);
  }
  kiraPlace(spot) {
    const s = typeof spot === 'string' ? SPOTS[spot] : spot;
    this.kira.placeAt(s.x, s.z, s.f || 0);
    this.kira.setPose(s.pose || 'idle');
  }
  playerPlace(x, z, yaw = 0) { this.player.stand(); this.player.teleport(x, z, yaw); }
  playerSit(spot) { const s = SPOTS[spot]; this.player.sit(s.x, s.z, s.yaw); }
  lookKira(dur = 0.6) {
    this.focusKira = true;
    const h = new THREE.Vector3();
    this.kira.head.getWorldPosition(h);
    h.y += 0.08;
    this.player.lookAt(h, dur);
  }
  lookAtPoint(x, y, z, dur = 0.6) { this.focusKira = false; this.player.lookAt(new THREE.Vector3(x, y, z), dur); }

  // Подходит ли Кира к игроку
  async kiraToPlayer(dist = 1.1) {
    const p = this.player.pos, k = this.kira.root.position;
    const dx = k.x - p.x, dz = k.z - p.z;
    const d = Math.hypot(dx, dz) || 1;
    let tx = p.x + (dx / d) * dist, tz = p.z + (dz / d) * dist;
    const v = { x: tx, z: tz };
    this.world.collide(v, 0.25);
    await this.kiraGo({ x: v.x, z: v.z });
    this.kira.faceTo(p.x, p.z);
  }

  // Начало разговора: камера на Киру, движение выключено
  async talkStart() {
    this.busy = true;
    this.player.canMove = false;
    this.expectUnlock = true;
    this.player.unlock();
    this.ui.prompt(null);
    $('#clickhint').style.display = 'none';
    this.kira.faceTo(this.player.pos.x, this.player.pos.z);
    this.lookKira();
    this.audio.setDuck(true);
    await this.wait(0.3);
  }
  talkEnd() {
    this.ui.hideDialog();
    this.audio.setDuck(false);
    this.busy = false;
    if (this.roaming) {
      this.player.canMove = true;
      this.player.canLook = true;
      // клик/клавиша, закрывшие диалог, — это жест пользователя: можно сразу вернуть захват мыши
      if (!this.player.locked) { this.player.lock(); setTimeout(() => { if (!this.player.locked && this.roaming) $('#clickhint').style.display = 'block'; }, 300); }
    }
  }

  // Кат-сцена: всё заблокировано
  cutscene(on) {
    this.busy = on;
    this.player.canMove = !on && this.roaming;
    this.player.canLook = !on && this.roaming;
    if (on) { this.expectUnlock = true; this.player.unlock(); this.ui.prompt(null); $('#clickhint').style.display = 'none'; }
  }

  setTasks(tasks, title) { this.currentTasks = tasks; this.ui.setObjectives(tasks, title); }

  // Свободное перемещение с заданиями.
  // tasks: [{id, text, target, label, run}] — target: id интерактивного объекта
  // extra: {id: {label, run}} — необязательные объекты; talk: async () => {}
  // doneWhen: () => bool (по умолчанию все задания выполнены)
  roam({ tasks = [], extra = {}, talk = null, title, doneWhen, kiraLabel } = {}) {
    this.ui.hideDialog();
    this.audio.setDuck(false);
    this.focusKira = false;
    return new Promise((resolve) => {
      this.roaming = true;
      this.busy = false;
      this.player.canMove = true;
      this.player.canLook = true;
      this.handlers = {};
      tasks.forEach((t) => { t.done = !!t.done; });
      this.setTasks(tasks, title);
      for (const [id, h] of Object.entries(extra)) this.handlers[id] = h;
      if (talk) this.handlers.kira = { label: kiraLabel || 'Поговорить с Кирой', run: talk };
      for (const t of tasks) {
        if (t.done) continue;
        const prev = this.handlers[t.target];
        this.handlers[t.target] = {
          label: t.label,
          run: async () => {
            await t.run(this);
            t.done = true;
            this.setTasks(tasks, title);
            this.audio.sfx('pickup');
            if (prev) this.handlers[t.target] = prev; else delete this.handlers[t.target];
          },
          task: true,
        };
      }
      this.roamCheck = () => {
        const fin = doneWhen ? doneWhen() : tasks.every((t) => t.done);
        if (fin) {
          this.roaming = false;
          this.roamCheck = null;
          this.player.canMove = false;
          this.handlers = {};
          this.ui.prompt(null);
          this.setTasks([]);
          $('#clickhint').style.display = 'none';
          resolve();
        }
      };
      if (!this.player.locked) $('#clickhint').style.display = 'block';
      this.roamCheck();
    });
  }

  async tryInteract() {
    if (this.busy || !this.hover) return;
    const h = this.handlers[this.hover];
    if (!h) return;
    this.busy = true;
    this.player.canMove = false;
    this.ui.prompt(null);
    const ret = (async () => {
      try { await h.run(this); } catch (e) { console.error(e); }
    })();
    await ret;
    this.talkEnd();
    if (this.roamCheck) this.roamCheck();
  }

  updateHover() {
    if (!this.roaming || this.busy || !this.player.locked) { this.hover = null; this.ui.prompt(null); return; }
    this.raycaster.setFromCamera({ x: 0, y: 0 }, this.camera);
    const targets = [this.world.group];
    if (this.kira.root.visible) targets.push(this.kira.root);
    const hits = this.raycaster.intersectObjects(targets, true);
    let id = null;
    for (const h of hits) {
      let o = h.object;
      if (o.material && o.material.transparent && o.material.opacity < 0.3 && !o.userData.interactId) continue; // стекло
      while (o && !o.userData.interactId && o !== this.kira.root) o = o.parent;
      if (o === this.kira.root || (h.object === this.kira.hitMesh)) id = 'kira';
      else if (o) id = o.userData.interactId;
      break;
    }
    if (id && this.handlers[id]) {
      this.hover = id;
      const h = this.handlers[id];
      this.ui.prompt(typeof h.label === 'function' ? h.label() : h.label || (this.world.interact[id] && this.world.interact[id].label) || '');
    } else { this.hover = null; this.ui.prompt(null); }
  }

  // ---------- Временные предметы (уборка, подарки) ----------
  spawnItem(id, x, y, z, make) {
    const g = new THREE.Group();
    make(g);
    // невидимая увеличенная зона, чтобы мелочь было легко подобрать
    const hit = new THREE.Mesh(new THREE.SphereGeometry(0.32, 8, 6), new THREE.MeshBasicMaterial({ visible: false }));
    g.add(hit);
    g.position.set(x, y, z);
    g.traverse((o) => { if (o.isMesh) { o.userData.interactId = id; o.castShadow = true; } });
    this.world.group.add(g);
    this.items = this.items || {};
    this.items[id] = g;
    return g;
  }
  removeItem(id) {
    const g = this.items && this.items[id];
    if (g) { g.parent.remove(g); delete this.items[id]; }
  }

  // ---------- Фото-режим ----------
  // shots: [{pose, emo, cap, want}] — Кира принимает позы, игрок фотографирует
  photoSession(shots) {
    return new Promise((resolve) => {
      $('#camframe').classList.remove('hidden');
      $('#crosshair').classList.add('hidden');
      this.player.canMove = true;
      this.player.canLook = true;
      this.roaming = false;
      let idx = 0;
      const results = [];
      const setShot = () => {
        const s = shots[idx];
        this.kira.setPose(s.pose);
        this.kira.setEmotion(s.emo);
        this.ui.setObjectives([{ text: `Фото ${idx + 1}/${shots.length}: ${s.want}` }], 'ФОТОСЕССИЯ');
      };
      setShot();
      this.photoMode = {
        snap: () => {
          if (this.photoBusy) return;
          const s = shots[idx];
          // проверяем, что Кира в кадре
          const head = new THREE.Vector3();
          this.kira.head.getWorldPosition(head);
          const body = this.kira.root.position.clone().setY(0.9);
          const dist = this.camera.position.distanceTo(head);
          const ph = head.clone().project(this.camera);
          const pb = body.project(this.camera);
          const inFrame = Math.abs(ph.x) < 0.75 && Math.abs(ph.y) < 0.8 && ph.z < 1 && Math.abs(pb.x) < 0.8;
          this.audio.sfx('shutter');
          const flash = $('#fade');
          flash.style.background = '#fff'; flash.style.transitionDuration = '0s'; flash.style.opacity = 0.8;
          setTimeout(() => { flash.style.transitionDuration = '0.4s'; flash.style.opacity = 0; setTimeout(() => (flash.style.background = '#000'), 450); }, 60);
          this.renderFrame();
          const img = this.capture();
          this.photoBusy = true;
          (async () => {
            if (!inFrame || dist > 5.5) {
              await this.ui.showPhoto(img, 'Кира не в кадре…', 1600);
              this.ui.toast(dist > 5.5 ? 'Подойди поближе!' : 'Наведи камеру на Киру!');
              this.audio.voice('э', 'k');
            } else {
              const good = dist < 3.2;
              await this.ui.showPhoto(img, s.cap, 2400);
              results.push({ img, cap: s.cap, good });
              this.addToAlbum(img, s.cap);
              idx++;
              if (idx >= shots.length) {
                $('#camframe').classList.add('hidden');
                $('#crosshair').classList.remove('hidden');
                this.photoMode = null;
                this.player.canMove = false;
                this.expectUnlock = true;
                this.player.unlock();
                this.ui.setObjectives([]);
                this.photoBusy = false;
                resolve(results);
                return;
              }
              setShot();
            }
            this.photoBusy = false;
          })();
        },
      };
      if (!this.player.locked) $('#clickhint').style.display = 'block';
    });
  }

  capture(w = 480) {
    const src = this.renderer.domElement;
    const c = document.createElement('canvas');
    const h = Math.round((w * src.height) / src.width);
    c.width = w; c.height = h;
    c.getContext('2d').drawImage(src, 0, 0, w, h);
    return c.toDataURL('image/jpeg', 0.78);
  }

  // ---------- Мини-игры ----------
  async minigame(name, ...args) {
    this.cutscene(true);
    this.audio.setDuck(false);
    const prev = this.audio.trackName;
    this.audio.play(name === 'Rhythm' ? 'dance' : name === 'Stars' ? 'love' : name === 'Pipes' ? 'glitch' : 'game');
    this.activeMG = new MG[name](this, ...args);
    const r = await this.activeMG.run();
    this.activeMG = null;
    this.audio.play(prev);
    return r;
  }

  // ---------- Сбои ----------
  glitch(dur = 0.6, amp = 1) {
    this.glitchT = Math.max(this.glitchT, dur);
    this.glitchAmp = amp;
    this.audio.sfx(amp > 1.5 ? 'bigglitch' : 'glitch');
  }

  drawGlitch(dt) {
    const c = $('#glitch');
    const lvl = this.glitchT > 0 ? this.glitchAmp : this.glitchBase;
    if (this.glitchT > 0) this.glitchT -= dt;
    if (lvl <= 0) { c.classList.add('hidden'); this.renderer.domElement.style.filter = ''; return; }
    c.classList.remove('hidden');
    const x = this.glitchCtx;
    x.clearRect(0, 0, c.width, c.height);
    const n = Math.floor(lvl * 8 * (this.glitchT > 0 ? 1 : Math.random() < 0.08 ? 1 : 0));
    for (let i = 0; i < n; i++) {
      x.fillStyle = `hsla(${Math.random() > 0.5 ? 290 : 340},100%,${40 + Math.random() * 30}%,${0.15 + Math.random() * 0.35})`;
      x.fillRect(Math.random() * c.width, Math.random() * c.height, Math.random() * c.width * 0.6, 2 + Math.random() * 10 * lvl);
    }
    this.renderer.domElement.style.filter = this.glitchT > 0 && Math.random() < 0.5 ? `hue-rotate(${Math.random() * 90}deg) saturate(1.6)` : '';
  }

  // ---------- Главный цикл ----------
  loop() {
    requestAnimationFrame(this.loop);
    const dt = Math.min(0.05, this.clock.getDelta());
    if (!this.paused) {
      if (this.menuCam) {
        const t = performance.now() / 1000;
        this.camera.position.set(3.0 + Math.sin(t * 0.15) * 0.4, 1.45, 4.6 + Math.cos(t * 0.15) * 0.2);
        this.camera.lookAt(3.0, 1.2, 2.2);
        this.kira.lookTarget = this.camera.position;
      } else {
        // во время разговоров камера мягко держит лицо Киры в кадре
        if (this.focusKira && (this.busy || !this.roaming) && !this.photoMode && !this.player.lookAnim && this.kira.vrm) {
          const h = new THREE.Vector3();
          this.kira.head.getWorldPosition(h);
          h.y += 0.07;
          this.player.track(h, dt);
        }
        const moving = this.player.update(dt);
        if (moving && Math.floor(this.player.stepT * 2.2) !== this.lastStep) { this.lastStep = Math.floor(this.player.stepT * 2.2); this.audio.sfx('step'); }
        this.updateHover();
        if (this.state) this.state.playTime += dt;
      }
      this.kira.update(dt);
      this.world.update(dt);
    }
    this.drawGlitch(dt);
    this.renderFrame();
  }
}

window.addEventListener('load', () => {
  const g = new Game();
  window.game = g;
  g.kira.ready.then(() => g.showMenu()).catch((e) => { console.error(e); document.querySelector('#loading').textContent = 'Ошибка загрузки модели: ' + e.message; });
});
