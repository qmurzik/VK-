// Управление от первого лица
import * as THREE from 'three';

export class Player {
  constructor(camera, dom, world) {
    this.camera = camera;
    this.dom = dom;
    this.world = world;
    this.pos = new THREE.Vector3(3, 0, 3);
    this.yaw = 0;
    this.pitch = 0;
    this.eye = 1.65;
    this.keys = {};
    this.canMove = false;
    this.canLook = false;
    this.sens = 1;
    this.bob = 0;
    this.stepT = 0;
    this.lookAnim = null;
    this.seated = false;
    camera.rotation.order = 'YXZ';

    addEventListener('keydown', (e) => { this.keys[e.code] = true; });
    addEventListener('keyup', (e) => { this.keys[e.code] = false; });
    addEventListener('blur', () => { this.keys = {}; });
    document.addEventListener('mousemove', (e) => {
      if (document.pointerLockElement !== this.dom || !this.canLook) return;
      this.yaw -= e.movementX * 0.0022 * this.sens;
      this.pitch -= e.movementY * 0.0022 * this.sens;
      this.pitch = Math.max(-1.45, Math.min(1.45, this.pitch));
      this.lookAnim = null;
    });
  }

  lock() {
    if (document.pointerLockElement !== this.dom) {
      try { const p = this.dom.requestPointerLock(); if (p && p.catch) p.catch(() => {}); } catch (e) { /* ignore */ }
    }
  }
  unlock() { if (document.pointerLockElement) document.exitPointerLock(); }
  get locked() { return document.pointerLockElement === this.dom; }

  teleport(x, z, yaw = this.yaw, pitch = 0) {
    this.pos.set(x, 0, z);
    this.yaw = yaw;
    this.pitch = pitch;
    this.lookAnim = null;
  }

  // Плавно повернуть камеру на точку
  lookAt(target, dur = 0.6) {
    const eye = new THREE.Vector3(this.pos.x, this.eyeTarget || this.eye, this.pos.z);
    const d = target.clone().sub(eye);
    let yaw = Math.atan2(-d.x, -d.z);
    const pitch = Math.atan2(d.y, Math.hypot(d.x, d.z));
    while (yaw - this.yaw > Math.PI) yaw -= Math.PI * 2;
    while (yaw - this.yaw < -Math.PI) yaw += Math.PI * 2;
    this.lookAnim = { fy: this.yaw, fp: this.pitch, ty: yaw, tp: pitch, t: 0, dur };
  }

  sit(x, z, yaw) {
    this.seated = true;
    this.teleport(x, z, yaw, -0.1);
    this.eyeTarget = 1.15;
    this.eye = 1.15;
  }

  // Плавно держать взгляд на точке (для диалогов)
  track(target, dt) {
    const eye = new THREE.Vector3(this.pos.x, this.eyeTarget || this.eye, this.pos.z);
    const d = target.clone().sub(eye);
    let yaw = Math.atan2(-d.x, -d.z);
    const pitch = Math.atan2(d.y, Math.hypot(d.x, d.z));
    while (yaw - this.yaw > Math.PI) yaw -= Math.PI * 2;
    while (yaw - this.yaw < -Math.PI) yaw += Math.PI * 2;
    const k = Math.min(1, dt * 4);
    this.yaw += (yaw - this.yaw) * k;
    this.pitch += (pitch - this.pitch) * k;
  }
  stand() {
    this.seated = false;
    this.eyeTarget = 1.65;
  }

  update(dt) {
    if (this.lookAnim) {
      const a = this.lookAnim;
      a.t += dt;
      const k = Math.min(1, a.t / a.dur);
      const e = k * k * (3 - 2 * k);
      this.yaw = a.fy + (a.ty - a.fy) * e;
      this.pitch = a.fp + (a.tp - a.fp) * e;
      if (k >= 1) this.lookAnim = null;
    }
    const eyeT = this.eyeTarget || 1.65;
    this.eye += (eyeT - this.eye) * Math.min(1, dt * 5);
    let moving = false;
    if (this.canMove && !this.seated) {
      const f = (this.keys.KeyW || this.keys.ArrowUp ? 1 : 0) - (this.keys.KeyS || this.keys.ArrowDown ? 1 : 0);
      const s = (this.keys.KeyD || this.keys.ArrowRight ? 1 : 0) - (this.keys.KeyA || this.keys.ArrowLeft ? 1 : 0);
      if (f || s) {
        const run = this.keys.ShiftLeft || this.keys.ShiftRight;
        const sp = (run ? 3.6 : 2.2) * dt;
        const len = Math.hypot(f, s);
        const sin = Math.sin(this.yaw), cos = Math.cos(this.yaw);
        this.pos.x += ((-sin * f + cos * s) / len) * sp;
        this.pos.z += ((-cos * f - sin * s) / len) * sp;
        moving = true;
        this.bob += dt * (run ? 13 : 9);
        this.stepT += dt * (run ? 1.6 : 1);
      }
    }
    if (!this.seated) this.world.collide(this.pos, 0.28);
    const bobY = moving ? Math.sin(this.bob) * 0.03 : 0;
    this.camera.position.set(this.pos.x, this.eye + bobY, this.pos.z);
    this.camera.rotation.set(this.pitch, this.yaw, 0);
    return moving;
  }
}
