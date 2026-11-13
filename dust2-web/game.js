/* game.js – Scene, lighting, game loop, minimap, compass */

var scene, camera, renderer, player, clock, mapData;
var minimapCtx, compassCtx;

function init() {
  // ---- scene ----
  scene = new THREE.Scene();
  scene.background = new THREE.Color(0x7CB8DC);
  scene.fog = new THREE.FogExp2(0xC8B898, 0.002);

  // ---- camera ----
  camera = new THREE.PerspectiveCamera(75, innerWidth / innerHeight, 0.1, 500);

  // ---- renderer ----
  renderer = new THREE.WebGLRenderer({antialias: true});
  renderer.setSize(innerWidth, innerHeight);
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.2;
  if (renderer.outputColorSpace !== undefined) renderer.outputColorSpace = THREE.SRGBColorSpace;
  else renderer.outputEncoding = THREE.sRGBEncoding;
  document.body.insertBefore(renderer.domElement, document.body.firstChild);
  renderer.domElement.id = 'game';

  // ---- lighting ----
  var ambient = new THREE.AmbientLight(0xFFEEDD, 0.3);
  scene.add(ambient);

  var hemi = new THREE.HemisphereLight(0x88BBDD, 0xB09060, 0.4);
  scene.add(hemi);

  var sun = new THREE.DirectionalLight(0xFFF4D6, 1.7);
  sun.position.set(40, 70, -25);
  sun.castShadow = true;
  sun.shadow.mapSize.width = 4096;
  sun.shadow.mapSize.height = 4096;
  var sc = sun.shadow.camera;
  sc.left = -85; sc.right = 85; sc.top = 85; sc.bottom = -85;
  sc.near = 0.5; sc.far = 220;
  sun.shadow.bias = -0.0006;
  sun.shadow.normalBias = 0.02;
  scene.add(sun);

  var fill = new THREE.DirectionalLight(0xD4C8B0, 0.3);
  fill.position.set(-30, 40, 20);
  scene.add(fill);

  // Bounce light from ground
  var bounce = new THREE.DirectionalLight(0xD4B888, 0.15);
  bounce.position.set(0, -10, 0);
  scene.add(bounce);

  // ---- sky dome with sun disc ----
  var skyGeo = new THREE.SphereGeometry(380, 32, 24);
  var skyMat = new THREE.ShaderMaterial({
    uniforms: {
      top:     {value: new THREE.Color(0x2A68A0)},
      horizon: {value: new THREE.Color(0xD4C0A0)},
      ex:      {value: 0.45},
      sunDir:  {value: new THREE.Vector3(40, 70, -25).normalize()},
      sunCol:  {value: new THREE.Color(0xFFF8E0)},
      sunSize: {value: 0.04}
    },
    vertexShader: [
      'varying vec3 vWP;',
      'void main(){',
      '  vWP = (modelMatrix * vec4(position,1.0)).xyz;',
      '  gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0);',
      '}'
    ].join('\n'),
    fragmentShader: [
      'uniform vec3 top, horizon, sunDir, sunCol;',
      'uniform float ex, sunSize;',
      'varying vec3 vWP;',
      'void main(){',
      '  vec3 dir = normalize(vWP + vec3(0.0, 15.0, 0.0));',
      '  float h = max(dir.y, 0.0);',
      '  vec3 sky = mix(horizon, top, pow(h, ex));',
      '  float sd = dot(dir, sunDir);',
      '  float sunDisc = smoothstep(1.0 - sunSize, 1.0 - sunSize * 0.3, sd);',
      '  float glow = pow(max(sd, 0.0), 16.0) * 0.3;',
      '  sky += sunCol * (sunDisc + glow);',
      '  gl_FragColor = vec4(sky, 1.0);',
      '}'
    ].join('\n'),
    side: THREE.BackSide,
    depthWrite: false
  });
  scene.add(new THREE.Mesh(skyGeo, skyMat));

  // ---- build map ----
  var fill1 = document.getElementById('load-fill');
  fill1.style.width = '30%';
  mapData = buildDust2Map(scene);
  fill1.style.width = '80%';

  // ---- player ----
  player = new PlayerController(camera, renderer.domElement);
  player.colliders = mapData.colliders;
  player.floors = mapData.floors;
  player.teleport(2, 0, 72);
  fill1.style.width = '100%';

  // ---- minimap ----
  minimapCtx = document.getElementById('minimap').getContext('2d');

  // ---- compass ----
  compassCtx = document.getElementById('compass').getContext('2d');

  // ---- clock ----
  clock = new THREE.Clock();

  window.addEventListener('resize', onResize);

  setTimeout(function(){
    document.getElementById('loading').style.display = 'none';
    document.getElementById('instructions').style.display = 'flex';
  }, 400);

  animate();
}

// ---- teleport ----
window.teleportTo = function(team) {
  if (!player || !mapData) return;
  var s = mapData.spawns[team];
  if (s) player.teleport(s.x, s.y, s.z);
};

// ---- resize ----
function onResize() {
  camera.aspect = innerWidth / innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(innerWidth, innerHeight);
}

// ---- minimap ----
function drawMinimap() {
  var c = minimapCtx, w = 360, h = 360;
  c.clearRect(0, 0, w, h);

  var bx1 = -48, bx2 = 38, bz1 = -42, bz2 = 88;
  var s = Math.min(w, h) / Math.max(bx2-bx1, bz2-bz1) * 0.88;
  var ox = w/2, oz = h/2;
  var mx = (bx1+bx2)/2, mz = (bz1+bz2)/2;

  function tx(x){ return ox + (x - mx) * s; }
  function tz(z){ return oz + (z - mz) * s; }

  // floor areas
  c.globalAlpha = 0.5;
  for (var i = 0; i < mapData.floors.length; i++) {
    var f = mapData.floors[i];
    if (f.y < -1.5) c.fillStyle = '#4A3E2A';
    else if (f.y > 0.5) c.fillStyle = '#8A7E64';
    else c.fillStyle = '#6E6450';
    var fx = tx(f.x1), fz = tz(f.z1);
    c.fillRect(fx, fz, (f.x2-f.x1)*s, (f.z2-f.z1)*s);
  }
  c.globalAlpha = 1;

  // walls outline
  c.strokeStyle = 'rgba(180,170,140,0.6)';
  c.lineWidth = 1;
  for (var k = 0; k < mapData.colliders.length; k++) {
    var cl = mapData.colliders[k];
    if (cl.y2 - cl.y1 < 1.5) continue;
    var cx1 = tx(cl.x1), cz1 = tz(cl.z1);
    var cw = (cl.x2-cl.x1)*s, ch = (cl.z2-cl.z1)*s;
    if (cw < 1 && ch < 1) continue;
    c.strokeRect(cx1, cz1, cw, ch);
  }

  // bombsites
  for (var j = 0; j < mapData.bombsites.length; j++) {
    var b = mapData.bombsites[j];
    var bpx = tx(b.x), bpz = tz(b.z);
    c.fillStyle = 'rgba(220,50,50,0.5)';
    c.beginPath(); c.arc(bpx, bpz, 10, 0, Math.PI*2); c.fill();
    c.fillStyle = '#fff'; c.font = 'bold 12px sans-serif'; c.textAlign = 'center';
    c.fillText(b.name, bpx, bpz + 4);
  }

  // player
  if (player) {
    var px = tx(player.pos.x), pz = tz(player.pos.z);
    // direction cone
    var dir = new THREE.Vector3(0,0,-1).applyQuaternion(camera.quaternion);
    var angle = Math.atan2(dir.z, dir.x);
    c.fillStyle = 'rgba(60,255,60,0.15)';
    c.beginPath();
    c.moveTo(px, pz);
    c.arc(px, pz, 20, angle - 0.4, angle + 0.4);
    c.closePath();
    c.fill();

    // player dot
    c.fillStyle = '#3F3'; c.strokeStyle = '#1A1'; c.lineWidth = 1.5;
    c.beginPath(); c.arc(px, pz, 4, 0, Math.PI*2); c.fill(); c.stroke();

    // direction line
    c.strokeStyle = '#3F3'; c.lineWidth = 2;
    c.beginPath(); c.moveTo(px, pz);
    c.lineTo(px + dir.x * 14, pz + dir.z * 14);
    c.stroke();
  }
}

// ---- compass ----
function drawCompass() {
  if (!compassCtx || !camera) return;
  var c = compassCtx, w = 200, h = 30;
  c.clearRect(0, 0, w, h);

  var dir = new THREE.Vector3(0, 0, -1).applyQuaternion(camera.quaternion);
  var yaw = Math.atan2(dir.x, -dir.z);

  var labels = [
    {a: 0, t: 'N'}, {a: Math.PI/4, t: 'NE'}, {a: Math.PI/2, t: 'E'},
    {a: 3*Math.PI/4, t: 'SE'}, {a: Math.PI, t: 'S'}, {a: -3*Math.PI/4, t: 'SW'},
    {a: -Math.PI/2, t: 'W'}, {a: -Math.PI/4, t: 'NW'}
  ];

  c.font = '11px sans-serif';
  c.textAlign = 'center';

  for (var i = 0; i < labels.length; i++) {
    var diff = labels[i].a - yaw;
    while (diff > Math.PI) diff -= 2*Math.PI;
    while (diff < -Math.PI) diff += 2*Math.PI;
    if (Math.abs(diff) > 1.2) continue;
    var x = w/2 + diff * (w/2.4);
    var isCard = labels[i].t.length === 1;
    c.fillStyle = isCard ? '#e8c872' : 'rgba(255,255,255,0.5)';
    c.font = isCard ? 'bold 13px sans-serif' : '10px sans-serif';
    c.fillText(labels[i].t, x, 18);
  }

  // tick marks
  c.strokeStyle = 'rgba(255,255,255,0.25)';
  c.lineWidth = 1;
  for (var d = -180; d <= 180; d += 15) {
    var a = d * Math.PI / 180;
    var diff2 = a - yaw;
    while (diff2 > Math.PI) diff2 -= 2*Math.PI;
    while (diff2 < -Math.PI) diff2 += 2*Math.PI;
    if (Math.abs(diff2) > 1.2) continue;
    var x2 = w/2 + diff2 * (w/2.4);
    var th = (d % 90 === 0) ? 8 : 4;
    c.beginPath(); c.moveTo(x2, 26); c.lineTo(x2, 26 - th); c.stroke();
  }

  // center marker
  c.strokeStyle = '#e8c872';
  c.lineWidth = 2;
  c.beginPath(); c.moveTo(w/2, 28); c.lineTo(w/2, 22); c.stroke();
}

// ---- location name ----
function updateLoc() {
  if (!player || !mapData) return;
  var px = player.pos.x, pz = player.pos.z;
  var best = '', bd = 1e9;
  for (var i = 0; i < mapData.locations.length; i++) {
    var l = mapData.locations[i];
    if (px >= l.x1 && px <= l.x2 && pz >= l.z1 && pz <= l.z2) {
      var cx = (l.x1+l.x2)/2, cz = (l.z1+l.z2)/2;
      var d = (px-cx)*(px-cx) + (pz-cz)*(pz-cz);
      if (d < bd) { bd = d; best = l.name; }
    }
  }
  document.getElementById('loc-name').textContent = best || '—';
  document.getElementById('pos-info').textContent =
    'x:'+player.pos.x.toFixed(0)+' z:'+player.pos.z.toFixed(0)+' y:'+(player.pos.y-player.EYE).toFixed(1);
}

// ---- loop ----
function animate() {
  requestAnimationFrame(animate);
  var dt = Math.min(clock.getDelta(), 0.05);
  if (player) {
    player.update(dt);
    drawMinimap();
    drawCompass();
    updateLoc();
  }
  renderer.render(scene, camera);
}

// ---- start ----
window.addEventListener('DOMContentLoaded', init);
