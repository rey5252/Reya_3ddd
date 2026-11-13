/* player.js – First-person controller with AABB collision */

class PlayerController {
  constructor(camera, domElement) {
    this.camera = camera;
    this.dom = domElement;

    this.pos = new THREE.Vector3(2, 1.7, 72);
    this.vel = new THREE.Vector3();
    this.euler = new THREE.Euler(0, 0, 0, 'YXZ');

    this.WALK  = 5.6;
    this.SPRINT = 8.8;
    this.JUMP  = 7.0;
    this.GRAV  = 20;
    this.EYE   = 1.65;
    this.RAD   = 0.35;
    this.STEP  = 0.55;          // max step-up height

    this.onGround = true;
    this.locked   = false;
    this.keys = {};

    this.colliders = [];        // [{x1,z1,x2,z2,y1,y2}]
    this.floors    = [];        // [{x1,z1,x2,z2,y, ramp?}]

    this._initEvents();
  }

  /* ---- events ---- */
  _initEvents() {
    const self = this;
    document.addEventListener('keydown', e => {
      self.keys[e.code] = true;
      if (e.code === 'Digit1') window.teleportTo('t');
      if (e.code === 'Digit2') window.teleportTo('ct');
    });
    document.addEventListener('keyup', e => { self.keys[e.code] = false; });

    document.addEventListener('mousemove', e => {
      if (!self.locked) return;
      self.euler.y -= e.movementX * 0.002;
      self.euler.x -= e.movementY * 0.002;
      self.euler.x = Math.max(-1.5, Math.min(1.5, self.euler.x));
      self.camera.quaternion.setFromEuler(self.euler);
    });

    document.addEventListener('pointerlockchange', () => {
      self.locked = (document.pointerLockElement === self.dom);
      document.getElementById('instructions').style.display = self.locked ? 'none' : 'flex';
      document.getElementById('team-btns').style.display = self.locked ? 'block' : 'none';
    });

    document.getElementById('instructions').addEventListener('click', () => {
      self.dom.requestPointerLock();
    });
  }

  /* ---- floor height at (x,z) ---- */
  floorAt(x, z) {
    let best = -Infinity;
    const eyeY = this.pos.y;
    for (let i = 0; i < this.floors.length; i++) {
      const f = this.floors[i];
      if (x < f.x1 || x > f.x2 || z < f.z1 || z > f.z2) continue;
      let fy = f.y;
      if (f.ramp) {
        const t = f.ramp.ax === 'x'
          ? (x - f.x1) / (f.x2 - f.x1)
          : (z - f.z1) / (f.z2 - f.z1);
        fy = f.ramp.a + t * (f.ramp.b - f.ramp.a);
      }
      if (fy > best && fy <= eyeY - this.EYE + this.STEP + 0.01) best = fy;
    }
    return best;
  }

  /* ---- wall collision (circle vs AABB) ---- */
  _hit(nx, nz) {
    const r = this.RAD;
    const pBot = this.pos.y - this.EYE;
    const pTop = this.pos.y + 0.15;
    for (let i = 0; i < this.colliders.length; i++) {
      const c = this.colliders[i];
      if (pTop <= c.y1 || pBot >= c.y2) continue;
      const cx = Math.max(c.x1, Math.min(nx, c.x2));
      const cz = Math.max(c.z1, Math.min(nz, c.z2));
      const dx = nx - cx, dz = nz - cz;
      if (dx * dx + dz * dz < r * r) return true;
    }
    return false;
  }

  _slide(ox, oz, nx, nz) {
    if (!this._hit(nx, nz)) return { x: nx, z: nz };
    if (!this._hit(nx, oz)) return { x: nx, z: oz };
    if (!this._hit(ox, nz)) return { x: ox, z: nz };
    return { x: ox, z: oz };
  }

  /* ---- main update ---- */
  update(dt) {
    if (!this.locked) return;
    const sprint = this.keys['ShiftLeft'] || this.keys['ShiftRight'];
    const speed = sprint ? this.SPRINT : this.WALK;

    const fwd = new THREE.Vector3(0, 0, -1).applyQuaternion(this.camera.quaternion);
    fwd.y = 0; fwd.normalize();
    const rt = new THREE.Vector3(1, 0, 0).applyQuaternion(this.camera.quaternion);
    rt.y = 0; rt.normalize();

    let mx = 0, mz = 0;
    if (this.keys['KeyW']) { mx += fwd.x; mz += fwd.z; }
    if (this.keys['KeyS']) { mx -= fwd.x; mz -= fwd.z; }
    if (this.keys['KeyA']) { mx -= rt.x;  mz -= rt.z;  }
    if (this.keys['KeyD']) { mx += rt.x;  mz += rt.z;  }
    const len = Math.sqrt(mx * mx + mz * mz);
    if (len > 0) { mx = mx / len * speed * dt; mz = mz / len * speed * dt; }

    if (this.keys['Space'] && this.onGround) {
      this.vel.y = this.JUMP;
      this.onGround = false;
    }
    this.vel.y -= this.GRAV * dt;

    const ox = this.pos.x, oz = this.pos.z;
    const res = this._slide(ox, oz, ox + mx, oz + mz);
    this.pos.x = res.x;
    this.pos.z = res.z;

    this.pos.y += this.vel.y * dt;

    const fy = this.floorAt(this.pos.x, this.pos.z);
    if (fy > -Infinity) {
      const feet = this.pos.y - this.EYE;
      if (feet <= fy + 0.02) {
        this.pos.y = fy + this.EYE;
        if (this.vel.y < 0) this.vel.y = 0;
        this.onGround = true;
      }
    } else if (this.pos.y < -15) {
      this.teleport(2, 0, 72);
    }

    this.camera.position.copy(this.pos);
  }

  teleport(x, y, z) {
    this.pos.set(x, y + this.EYE, z);
    this.vel.set(0, 0, 0);
    this.onGround = false;
    this.camera.position.copy(this.pos);
  }
}
