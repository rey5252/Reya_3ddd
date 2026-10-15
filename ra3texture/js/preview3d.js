(function (RA3) { 'use strict';
  const meshes = RA3.meshes || require('./meshes.js');

  // Параметри шейдера RA3 (ті самі, що в maps.js для вигляду «У грі»).
  const GAMMA = 2.2, BUMP_SCALE = 1.5, SPECULAR = 0.8, ALPHA_TEST = 96, AMBIENT = 0.35;
  // Сонце у світовому просторі (y вгору, камера дивиться в -z): зверху-зліва-спереду.
  // Це той самий напрям (-0.5, -0.6, 0.62) з 2D-вигляду, лише з віссю y вгору замість «донизу по зображенню».
  const SUN = (function () { const x = -0.5, y = 0.6, z = 0.62, l = Math.sqrt(x * x + y * y + z * z); return [x / l, y / l, z / l]; })();

  const FOV_Y = 35 * Math.PI / 180;
  const PITCH_MAX = 1.35;          // обмеження нахилу, рад (~77°)
  const DRAG_SPEED = 0.01;         // рад на піксель
  const AUTO_SPEED = 0.6;          // рад/с
  const AUTO_DELAY = 3000;         // мс без руху миші до відновлення автообертання
  const MAX_DT = 0.1;              // с, щоб після паузи вкладки модель не «стрибала»
  const MAX_DPR = 2;
  const BG = [0.13, 0.14, 0.16];

  // ---------- Шейдери ----------

  const VERT = [
    'attribute vec3 aPos; attribute vec3 aNormal; attribute vec3 aTangent; attribute vec2 aUV;',
    'uniform mat4 uMVP; uniform mat4 uModel; uniform mat3 uNormalMat;',
    'varying vec3 vPos; varying vec3 vN; varying vec3 vT; varying vec2 vUV;',
    'void main() {',
    '  vPos = (uModel * vec4(aPos, 1.0)).xyz;',
    '  vN = uNormalMat * aNormal;',
    '  vT = uNormalMat * aTangent;',
    '  vUV = aUV;',
    '  gl_Position = uMVP * vec4(aPos, 1.0);',
    '}'
  ].join('\n');

  const FRAG = [
    '#ifdef GL_FRAGMENT_PRECISION_HIGH',
    'precision highp float;',
    '#else',
    'precision mediump float;',
    '#endif',
    'uniform sampler2D uDiffuse; uniform sampler2D uNrm; uniform sampler2D uSpm;',
    'uniform vec3 uTeam; uniform float uInvertY; uniform vec3 uCam;',
    'varying vec3 vPos; varying vec3 vN; varying vec3 vT; varying vec2 vUV;',
    'const vec3 SUN = vec3(' + SUN.map(v => v.toFixed(6)).join(', ') + ');',
    'const float GAMMA = ' + GAMMA.toFixed(1) + ';',
    'const float BUMP_SCALE = ' + BUMP_SCALE.toFixed(1) + ';',
    'const float SPECULAR = ' + SPECULAR.toFixed(1) + ';',
    'const float AMBIENT = ' + AMBIENT.toFixed(2) + ';',
    // alpha < 96/255 відсікається; порівняння в «байтах» з запасом пів рівня, щоб 96 не губилося через округлення float
    'const float ALPHA_CUTOFF = ' + (ALPHA_TEST - 0.5).toFixed(1) + ';',
    // Небо навколо моделі в лінійному просторі: земля, горизонт, зеніт і сонячна пляма.
    'vec3 environment(vec3 r) {',
    '  float up = clamp(r.y, -1.0, 1.0);',
    '  vec3 sky = mix(vec3(0.80, 0.85, 0.90), vec3(0.58, 0.72, 1.00), pow(max(up, 0.0), 0.6));',
    '  vec3 col = mix(vec3(0.30, 0.28, 0.25), sky, smoothstep(-0.25, 0.15, up));',
    '  float sun = pow(max(dot(r, SUN), 0.0), 48.0);',
    '  return col + vec3(1.0, 0.95, 0.85) * (sun * 1.2);',
    '}',
    'void main() {',
    '  vec4 d = texture2D(uDiffuse, vUV);',
    '  if (d.a * 255.0 < ALPHA_CUTOFF) discard;',
    '  vec4 s = texture2D(uSpm, vUV);',
    // recolor у гамма-просторі, потім лінеаризація
    '  vec3 c = mix(d.rgb, d.rgb * uTeam * 2.0, s.b);',
    '  vec3 lin = pow(max(c, vec3(0.0)), vec3(GAMMA));',
    // нормаль з NRM: y зображення йде донизу (DirectX); для OpenGL-NRM знак y інвертується
    '  vec3 n = texture2D(uNrm, vUV).rgb * 2.0 - 1.0;',
    '  n.y *= 1.0 - 2.0 * uInvertY;',
    '  n.xy *= BUMP_SCALE;',
    '  float l2 = dot(n, n);',
    '  n = l2 > 1e-6 ? n * inversesqrt(l2) : vec3(0.0, 0.0, 1.0);',
    // TBN з атрибутів: T вздовж +x зображення, бітангенс cross(N, T) вздовж «донизу по зображенню»
    '  vec3 N = normalize(vN);',
    '  vec3 T = normalize(vT - N * dot(N, vT));',
    '  vec3 B = cross(N, T);',
    '  vec3 Nw = normalize(T * n.x + B * n.y + N * n.z);',
    '  vec3 V = normalize(uCam - vPos);',
    '  vec3 R = reflect(-V, Nw);',
    '  float light = AMBIENT + max(dot(Nw, SUN), 0.0);',
    '  vec3 col = lin * light + SPECULAR * s.r * environment(R);',
    '  gl_FragColor = vec4(pow(clamp(col, 0.0, 1.0), vec3(1.0 / GAMMA)), 1.0);',
    '}'
  ].join('\n');

  // ---------- Матриці (column-major, як у WebGL) ----------

  function mat4Mul(a, b) {
    const o = new Float32Array(16);
    for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) {
      o[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
    }
    return o;
  }
  function mat4Translate(x, y, z) { return new Float32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1]); }
  function mat4RotX(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1]); }
  function mat4RotY(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1]); }
  function mat4Perspective(fovY, aspect, near, far) {
    const f = 1 / Math.tan(fovY / 2), nf = 1 / (near - far);
    return new Float32Array([f / aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, (far + near) * nf, -1, 0, 0, 2 * far * near * nf, 0]);
  }
  function mat3FromMat4(m) { return new Float32Array([m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10]]); }

  function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }
  function nowMs() { return (typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now(); }

  // ---------- Прев'ю ----------

  class Preview3D {
    // canvas: HTMLCanvasElement. opts.background: [r, g, b] у 0..1.
    constructor(canvas, opts) {
      const o = opts || {};
      this.canvas = canvas;
      this.ok = false;
      this.error = '';
      this.autoRotate = true;      // false вимикає автообертання (для знімків екрана й тестів)
      this.yaw = 0.6;
      this.pitch = 0.35;
      this._bg = o.background || BG;
      this._modelName = 'tank';
      this._team = [230 / 255, 20 / 255, 20 / 255];
      this._tex = null;            // останні буфери: {diffuse, nrm, spm, size, invertY}
      this._invertY = 0;
      this._raf = 0;
      this._last = 0;
      this._lastMove = -Infinity;
      this._drag = null;
      this._lost = false;
      this._gpu = null;

      let gl = null;
      try {
        const attrs = { alpha: false, antialias: true, depth: true, preserveDrawingBuffer: true };
        gl = canvas.getContext('webgl', attrs) || canvas.getContext('experimental-webgl', attrs);
      } catch (e) { gl = null; }
      if (!gl) { this.error = 'WebGL недоступний у цьому браузері'; return; }
      this.gl = gl;
      try { this._initGL(); } catch (e) { this.error = e.message; return; }
      this.ok = true;
      this._bindEvents();
    }

    // ----- GL-ресурси -----

    _initGL() {
      const gl = this.gl;
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);        // рядок 0 = верх зображення, v росте донизу
      gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
      if (gl.UNPACK_COLORSPACE_CONVERSION_WEBGL) gl.pixelStorei(gl.UNPACK_COLORSPACE_CONVERSION_WEBGL, gl.NONE);

      const compile = (type, src) => {
        const sh = gl.createShader(type);
        gl.shaderSource(sh, src); gl.compileShader(sh);
        if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) {
          const log = gl.getShaderInfoLog(sh); gl.deleteShader(sh);
          throw new Error('Помилка компіляції шейдера: ' + log);
        }
        return sh;
      };
      const vs = compile(gl.VERTEX_SHADER, VERT), fs = compile(gl.FRAGMENT_SHADER, FRAG);
      const prog = gl.createProgram();
      gl.attachShader(prog, vs); gl.attachShader(prog, fs);
      gl.bindAttribLocation(prog, 0, 'aPos'); gl.bindAttribLocation(prog, 1, 'aNormal');
      gl.bindAttribLocation(prog, 2, 'aTangent'); gl.bindAttribLocation(prog, 3, 'aUV');
      gl.linkProgram(prog);
      if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
        const log = gl.getProgramInfoLog(prog);
        throw new Error('Помилка збирання шейдерної програми: ' + log);
      }
      gl.deleteShader(vs); gl.deleteShader(fs);

      const names = ['uMVP', 'uModel', 'uNormalMat', 'uCam', 'uTeam', 'uInvertY', 'uDiffuse', 'uNrm', 'uSpm'], loc = {};
      for (const n of names) loc[n] = gl.getUniformLocation(prog, n);

      const aniso = gl.getExtension('EXT_texture_filter_anisotropic') || gl.getExtension('MOZ_EXT_texture_filter_anisotropic') ||
        gl.getExtension('WEBKIT_EXT_texture_filter_anisotropic');
      const maxAniso = aniso ? Math.min(8, gl.getParameter(aniso.MAX_TEXTURE_MAX_ANISOTROPY_EXT)) : 1;

      const makeTexture = rgba => {
        const t = gl.createTexture();
        gl.bindTexture(gl.TEXTURE_2D, t);
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, 1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(rgba));
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.REPEAT);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.REPEAT);
        if (aniso && maxAniso > 1) gl.texParameterf(gl.TEXTURE_2D, aniso.TEXTURE_MAX_ANISOTROPY_EXT, maxAniso);
        return t;
      };
      // Поки немає текстур: сірий колір, плоска нормаль, без блиску й кольору гравця.
      this._gpu = {
        prog, loc, models: {},
        maxSize: gl.getParameter(gl.MAX_TEXTURE_SIZE),
        diffuse: makeTexture([128, 128, 128, 255]),
        nrm: makeTexture([128, 128, 255, 255]),
        spm: makeTexture([0, 0, 0, 255])
      };
    }

    _freeGL() {
      const gl = this.gl, g = this._gpu;
      if (!g || this._lost) { this._gpu = null; return; }
      for (const k of Object.keys(g.models)) { gl.deleteBuffer(g.models[k].vbo); gl.deleteBuffer(g.models[k].ibo); }
      gl.deleteTexture(g.diffuse); gl.deleteTexture(g.nrm); gl.deleteTexture(g.spm);
      gl.deleteProgram(g.prog);
      this._gpu = null;
    }

    _uploadTexture(tex, data, size) {
      const gl = this.gl;
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, size, size, 0, gl.RGBA, gl.UNSIGNED_BYTE,
        new Uint8Array(data.buffer, data.byteOffset, data.byteLength));
      gl.generateMipmap(gl.TEXTURE_2D);
    }

    _uploadAll() {
      const t = this._tex;
      if (!t || !this._gpu || this._lost) return;
      this._uploadTexture(this._gpu.diffuse, t.diffuse, t.size);
      this._uploadTexture(this._gpu.nrm, t.nrm, t.size);
      this._uploadTexture(this._gpu.spm, t.spm, t.size);
    }

    // Меш у GPU: інтерліварний буфер (pos3, normal3, tangent3, uv2) + Uint16-індекси; центр і радіус для кадрування.
    _model(name) {
      const g = this._gpu;
      if (g.models[name]) return g.models[name];
      const gl = this.gl, m = meshes.get(name), n = m.positions.length / 3;
      const data = new Float32Array(n * 11);
      const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
      for (let i = 0; i < n; i++) {
        data.set(m.positions.subarray(i * 3, i * 3 + 3), i * 11);
        data.set(m.normals.subarray(i * 3, i * 3 + 3), i * 11 + 3);
        data.set(m.tangents.subarray(i * 3, i * 3 + 3), i * 11 + 6);
        data.set(m.uvs.subarray(i * 2, i * 2 + 2), i * 11 + 9);
        for (let k = 0; k < 3; k++) { min[k] = Math.min(min[k], m.positions[i * 3 + k]); max[k] = Math.max(max[k], m.positions[i * 3 + k]); }
      }
      const center = [0, 1, 2].map(k => (min[k] + max[k]) / 2);
      let radius = 0;
      for (let i = 0; i < n; i++) {
        const dx = m.positions[i * 3] - center[0], dy = m.positions[i * 3 + 1] - center[1], dz = m.positions[i * 3 + 2] - center[2];
        radius = Math.max(radius, Math.sqrt(dx * dx + dy * dy + dz * dz));
      }
      const vbo = gl.createBuffer(), ibo = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, vbo); gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW);
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ibo); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, m.indices, gl.STATIC_DRAW);
      return (g.models[name] = { vbo, ibo, count: m.indices.length, center, radius });
    }

    // ----- Публічний API -----

    // diffuse/nrm/spm: RGBA Uint8ClampedArray розміром size × size × 4 (як з maps.buildDiffuse/buildNRM/buildSPM).
    // opts.invertY: NRM в OpenGL-конвенції (G = 0.5 - 0.5·n.y); за замовчуванням DirectX (y донизу по зображенню).
    setTextures(diffuse, nrm, spm, size, opts) {
      if (!this.ok) return;
      const o = opts || {};
      if (!(size > 0) || (size & (size - 1)) !== 0) throw new Error('Розмір текстури має бути степенем двійки: ' + size);
      const need = size * size * 4;
      for (const b of [diffuse, nrm, spm]) {
        if (!b || b.length !== need) throw new Error('Розмір буфера текстури не відповідає ' + size + '×' + size + '×4');
      }
      if (this._gpu && size > this._gpu.maxSize) throw new Error('Текстура ' + size + '×' + size + ' завелика для цього пристрою (максимум ' + this._gpu.maxSize + ')');
      this._tex = { diffuse, nrm, spm, size };
      this._invertY = o.invertY ? 1 : 0;
      this._uploadAll();
    }

    setTeamColor(rgb) {
      if (!this.ok) return;
      if (!rgb || !(rgb.length >= 3)) throw new Error('Для кольору гравця потрібне значення [r, g, b]');
      this._team = [rgb[0] / 255, rgb[1] / 255, rgb[2] / 255];
    }

    setModel(name) {
      if (!this.ok) return;
      if (meshes.NAMES.indexOf(name) < 0) throw new Error('Невідома модель: ' + name);
      this._modelName = name;
    }

    // Задає кут огляду (рад); нахил обмежується.
    setView(yaw, pitch) {
      if (!this.ok) return;
      this.yaw = +yaw;
      this.pitch = clamp(+pitch, -PITCH_MAX, PITCH_MAX);
    }

    start() {
      if (!this.ok || this._raf) return;
      this._last = 0;
      const tick = () => {
        this._raf = requestAnimationFrame(tick);
        try { this._frame(nowMs()); } catch (e) { this.stop(); throw e; }
      };
      this._raf = requestAnimationFrame(tick);
    }

    stop() {
      if (this._raf) { cancelAnimationFrame(this._raf); this._raf = 0; }
    }

    // Знімає слухачів і звільняє GL-ресурси; після цього прев'ю непридатне.
    dispose() {
      if (!this.ok) return;
      this.stop();
      this._unbindEvents();
      this._freeGL();
      this.ok = false;
    }

    // ----- Кадр -----

    _frame(t) {
      const dt = this._last ? Math.min((t - this._last) / 1000, MAX_DT) : 0;
      this._last = t;
      if (this.autoRotate && !this._drag && t - this._lastMove > AUTO_DELAY) this.yaw += AUTO_SPEED * dt;
      this.render();
    }

    // Підганяє розмір полотна під CSS-розмір з урахуванням devicePixelRatio; false, якщо полотно сховане.
    _resize() {
      const c = this.canvas, dpr = Math.min((typeof window !== 'undefined' && window.devicePixelRatio) || 1, MAX_DPR);
      const cw = c.clientWidth, ch = c.clientHeight;
      if (!(cw > 0 && ch > 0)) return false;
      const w = Math.max(1, Math.round(cw * dpr)), h = Math.max(1, Math.round(ch * dpr));
      if (c.width !== w || c.height !== h) {
        c.width = w; c.height = h;
        // Полотно без CSS-розміру росло б услід за атрибутами: фіксуємо його видимий розмір.
        if (c.clientWidth !== cw || c.clientHeight !== ch) { c.style.width = cw + 'px'; c.style.height = ch + 'px'; }
      }
      return true;
    }

    // Малює один кадр з поточним станом (викликається з циклу start(); можна й вручну).
    render() {
      if (!this.ok || this._lost || !this._gpu || !this._resize()) return;
      const gl = this.gl, g = this._gpu, loc = g.loc, c = this.canvas, w = c.width, h = c.height;
      const model = this._model(this._modelName);

      // Камера на осі z на відстані, з якої модель повністю вміщається і по вертикалі, і по горизонталі.
      const aspect = w / h, halfY = FOV_Y / 2, halfX = Math.atan(Math.tan(halfY) * aspect);
      const dist = model.radius / Math.sin(Math.min(halfY, halfX)) * 1.08;
      const rot = mat4Mul(mat4RotX(this.pitch), mat4RotY(this.yaw));
      const modelM = mat4Mul(rot, mat4Translate(-model.center[0], -model.center[1], -model.center[2]));
      const proj = mat4Perspective(FOV_Y, aspect, Math.max(0.05, dist - model.radius * 1.5), dist + model.radius * 1.5);
      const mvp = mat4Mul(proj, mat4Mul(mat4Translate(0, 0, -dist), modelM));

      gl.viewport(0, 0, w, h);
      gl.clearColor(this._bg[0], this._bg[1], this._bg[2], 1);
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      gl.enable(gl.DEPTH_TEST);
      gl.enable(gl.CULL_FACE); gl.cullFace(gl.BACK); // меші обходяться проти годинникової стрілки зовні

      gl.useProgram(g.prog);
      gl.uniformMatrix4fv(loc.uMVP, false, mvp);
      gl.uniformMatrix4fv(loc.uModel, false, modelM);
      gl.uniformMatrix3fv(loc.uNormalMat, false, mat3FromMat4(rot));
      gl.uniform3f(loc.uCam, 0, 0, dist);
      gl.uniform3f(loc.uTeam, this._team[0], this._team[1], this._team[2]);
      gl.uniform1f(loc.uInvertY, this._invertY);
      gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, g.diffuse); gl.uniform1i(loc.uDiffuse, 0);
      gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, g.nrm); gl.uniform1i(loc.uNrm, 1);
      gl.activeTexture(gl.TEXTURE2); gl.bindTexture(gl.TEXTURE_2D, g.spm); gl.uniform1i(loc.uSpm, 2);

      gl.bindBuffer(gl.ARRAY_BUFFER, model.vbo);
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, model.ibo);
      const stride = 11 * 4;
      for (let a = 0; a < 4; a++) gl.enableVertexAttribArray(a);
      gl.vertexAttribPointer(0, 3, gl.FLOAT, false, stride, 0);
      gl.vertexAttribPointer(1, 3, gl.FLOAT, false, stride, 12);
      gl.vertexAttribPointer(2, 3, gl.FLOAT, false, stride, 24);
      gl.vertexAttribPointer(3, 2, gl.FLOAT, false, stride, 36);
      gl.drawElements(gl.TRIANGLES, model.count, gl.UNSIGNED_SHORT, 0);
    }

    // ----- Миша -----

    _bindEvents() {
      const c = this.canvas;
      this._onDown = e => {
        if (e.pointerType === 'mouse' && e.button !== 0) return;
        this._drag = { id: e.pointerId, x: e.clientX, y: e.clientY };
        this._lastMove = nowMs();
        try { c.setPointerCapture(e.pointerId); } catch (err) { /* не критично */ }
        e.preventDefault();
      };
      this._onMove = e => {
        this._lastMove = nowMs();
        const d = this._drag;
        if (!d || e.pointerId !== d.id) return;
        this.yaw += (e.clientX - d.x) * DRAG_SPEED;
        this.pitch = clamp(this.pitch + (e.clientY - d.y) * DRAG_SPEED, -PITCH_MAX, PITCH_MAX);
        d.x = e.clientX; d.y = e.clientY;
      };
      this._onUp = e => {
        if (this._drag && e.pointerId === this._drag.id) {
          this._drag = null;
          this._lastMove = nowMs();
          try { c.releasePointerCapture(e.pointerId); } catch (err) { /* вже відпущено */ }
        }
      };
      this._onLost = e => { e.preventDefault(); this._lost = true; };
      this._onRestored = () => {
        this._lost = false;
        try { this._initGL(); this._uploadAll(); } catch (err) { this.ok = false; this.error = err.message; }
      };
      c.style.touchAction = 'none'; // перетягування на дотик обертає модель, а не прокручує сторінку
      c.addEventListener('pointerdown', this._onDown);
      c.addEventListener('pointermove', this._onMove);
      c.addEventListener('pointerup', this._onUp);
      c.addEventListener('pointercancel', this._onUp);
      c.addEventListener('webglcontextlost', this._onLost);
      c.addEventListener('webglcontextrestored', this._onRestored);
    }

    _unbindEvents() {
      const c = this.canvas;
      c.removeEventListener('pointerdown', this._onDown);
      c.removeEventListener('pointermove', this._onMove);
      c.removeEventListener('pointerup', this._onUp);
      c.removeEventListener('pointercancel', this._onUp);
      c.removeEventListener('webglcontextlost', this._onLost);
      c.removeEventListener('webglcontextrestored', this._onRestored);
    }
  }

  Preview3D.MODELS = meshes.NAMES;

  RA3.Preview3D = Preview3D;
  if (typeof module !== 'undefined' && module.exports) module.exports = Preview3D;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
