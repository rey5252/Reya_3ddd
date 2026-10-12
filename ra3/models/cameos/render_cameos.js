#!/usr/bin/env node
// Fortress Protocol — cameo (build-menu icon) renderer, step 1 of 2.
//
// Renders every tower / nanocore .glb from models/glb/ (plus a procedural
// "reinforced plating" armor plate for Upgrade_FortressReinforcedPlating) at a
// heroic 3/4 angle into transparent 768x768 PNGs, and writes manifest.json with
// the tight bounding box of each model (shadow excluded). Step 2,
// compose_cameos.py, adds the faction background, frame and vignette.
//
// Usage (from the repo root):
//   NODE_PATH=$(npm root -g) node ra3/models/cameos/render_cameos.js [--three=DIR] [--out=DIR] [Id ...]
//
//   --three=DIR / env FP_THREE_DIR   directory with three.js r147 UMD builds
//       (three.min.js, GLTFLoader.js, RoomEnvironment.js — either flat in DIR
//       or in an npm package layout: DIR/build/, DIR/examples/js/...).
//       Default: the local render harness directory (see DEFAULT_THREE_DIR).
//   --out=DIR   / env FP_CAMEO_RENDER_DIR   where raw renders + manifest.json go
//       (default: <os tmpdir>/fortress_cameo_renders). compose_cameos.py reads
//       the same env var / default.
//   Id ...      optional subset of ids to render (default: all).
//
// Requires playwright (chromium). WebGL runs on SwiftShader.
// Models re-exported with textures work as-is: relative texture URIs
// (../textures/<set>/<name>_dif.png) are served from models/.
'use strict';
const { chromium } = require('playwright');
const fs = require('fs');
const os = require('os');
const path = require('path');

const DEFAULT_THREE_DIR =
  '/tmp/claude-0/-home-user-Reya-3ddd/cf985bb1-9be0-5a13-8897-26af1ae9c4a5/scratchpad/render3d';
const MODELS_DIR = path.resolve(__dirname, '..');
const GLB_DIR = path.join(MODELS_DIR, 'glb');
const SIZE = 768;

const args = process.argv.slice(2);
const opt = (k) => { const a = args.find((x) => x.startsWith(`--${k}=`)); return a ? a.slice(k.length + 3) : undefined; };
const THREE_DIR = path.resolve(opt('three') || process.env.FP_THREE_DIR || DEFAULT_THREE_DIR);
const OUT_DIR = path.resolve(opt('out') || process.env.FP_CAMEO_RENDER_DIR || path.join(os.tmpdir(), 'fortress_cameo_renders'));
const only = args.filter((a) => !a.startsWith('--'));

function findThree(name) {
  const cands = {
    'three.min.js': ['three.min.js', 'build/three.min.js'],
    'GLTFLoader.js': ['GLTFLoader.js', 'examples/js/loaders/GLTFLoader.js'],
    'RoomEnvironment.js': ['RoomEnvironment.js', 'examples/js/environments/RoomEnvironment.js'],
  }[name];
  for (const c of cands) { const p = path.join(THREE_DIR, c); if (fs.existsSync(p)) return p; }
  throw new Error(`${name} not found in ${THREE_DIR} (set --three=DIR or FP_THREE_DIR)`);
}

// Per-id camera tweaks: yaw (deg, around +Y from +Z towards +X), pitch (deg).
const TOWER_VIEW = { yaw: 38, pitch: 22 };
const CORE_VIEW = { yaw: 40, pitch: 28 };
const VIEW_OVERRIDES = {};

function jobs() {
  const list = [];
  for (const f of fs.readdirSync(GLB_DIR).filter((f) => f.endsWith('.glb')).sort()) {
    const id = f.slice(0, -4);
    const faction = id.startsWith('Allied') ? 'allied' : id.startsWith('Soviet') ? 'soviet' : id.startsWith('Japan') ? 'empire' : 'neutral';
    const view = { ...(id.endsWith('Nanocore') ? CORE_VIEW : TOWER_VIEW), ...(VIEW_OVERRIDES[id] || {}) };
    list.push({ id, faction, kind: id.endsWith('Nanocore') ? 'nanocore' : 'tower', url: `/models/glb/${f}`, view });
  }
  list.push({ id: 'Upgrade_FortressReinforcedPlating', faction: 'neutral', kind: 'upgrade', url: null, view: { yaw: 0, pitch: 0 } });
  return only.length ? list.filter((j) => only.includes(j.id)) : list;
}

const PAGE = `<!doctype html><html><body style="margin:0;background:transparent">
<canvas id="c" width="${SIZE}" height="${SIZE}"></canvas>
<script src="/three/three.min.js"></script><script src="/three/GLTFLoader.js"></script><script src="/three/RoomEnvironment.js"></script>
<script>
const S=${SIZE};
const r=new THREE.WebGLRenderer({canvas:document.getElementById('c'),antialias:true,alpha:true,preserveDrawingBuffer:true});
r.setPixelRatio(1); r.setClearColor(0x000000,0);
r.outputEncoding=THREE.sRGBEncoding; r.toneMapping=THREE.ACESFilmicToneMapping; r.toneMappingExposure=0.92;
r.shadowMap.enabled=true; r.shadowMap.type=THREE.PCFSoftShadowMap;
const pmrem=new THREE.PMREMGenerator(r);
const envTex=pmrem.fromScene(new THREE.RoomEnvironment(),0.04).texture;

function makeScene(){
  const scene=new THREE.Scene(); scene.environment=envTex;
  scene.add(new THREE.HemisphereLight(0xe4ecff,0x2c2620,0.45));
  return scene;
}
// key (front-left, warm), rim (behind, cool, strong — silhouette), fill (front-right)
function addLights(scene,cam,c,R){
  const fwd=new THREE.Vector3().subVectors(cam.position,c).setY(0).normalize();
  const side=new THREE.Vector3().crossVectors(new THREE.Vector3(0,1,0),fwd).normalize();
  const at=(f,s,u,d)=>c.clone().addScaledVector(fwd,f*d).addScaledVector(side,s*d).add(new THREE.Vector3(0,u*d,0));
  const key=new THREE.DirectionalLight(0xfff1dc,1.7); key.position.copy(at(0.75,-0.75,1.25,R*3)); key.target.position.copy(c);
  key.castShadow=true; key.shadow.mapSize.set(2048,2048); key.shadow.bias=-0.0004; key.shadow.normalBias=0.02;
  Object.assign(key.shadow.camera,{left:-R*1.6,right:R*1.6,top:R*1.6,bottom:-R*1.6,near:0.1,far:R*8});
  const rim=new THREE.DirectionalLight(0xdfeaff,2.6); rim.position.copy(at(-1.0,0.55,0.8,R*3)); rim.target.position.copy(c);
  const rim2=new THREE.DirectionalLight(0xfff6e8,1.4); rim2.position.copy(at(-0.9,-0.8,0.5,R*3)); rim2.target.position.copy(c);
  const fill=new THREE.DirectionalLight(0xcfdcff,0.55); fill.position.copy(at(0.8,1.0,0.25,R*3)); fill.target.position.copy(c);
  [key,rim,rim2,fill].forEach(l=>{scene.add(l);scene.add(l.target);});
}
function makeCam(c,size,view){
  const cam=new THREE.PerspectiveCamera(26,1,0.05,500);
  const d=size*2.6, p=THREE.MathUtils.degToRad(view.pitch), y=THREE.MathUtils.degToRad(view.yaw);
  cam.position.set(c.x+d*Math.cos(p)*Math.sin(y), c.y+d*Math.sin(p), c.z+d*Math.cos(p)*Math.cos(y));
  cam.lookAt(c); cam.updateProjectionMatrix(); return cam;
}
function alphaBBox(){
  const cv=document.createElement('canvas'); cv.width=S; cv.height=S; const g=cv.getContext('2d');
  g.drawImage(r.domElement,0,0); const a=g.getImageData(0,0,S,S).data;
  let x0=S,y0=S,x1=-1,y1=-1;
  for(let y=0;y<S;y++)for(let x=0;x<S;x++){ if(a[(y*S+x)*4+3]>10){ if(x<x0)x0=x; if(x>x1)x1=x; if(y<y0)y0=y; if(y>y1)y1=y; } }
  return x1<0?null:[x0,y0,x1+1,y1+1];
}
// Two passes: without shadow catcher (tight bbox of the model), then with it (final image).
function shoot(scene,cam,ground){
  ground.visible=false; r.render(scene,cam); const bbox=alphaBBox();
  ground.visible=true; r.render(scene,cam);
  return {png:r.domElement.toDataURL('image/png'),bbox};
}
function shadowCatcher(y,R){
  const g=new THREE.Mesh(new THREE.PlaneGeometry(R*8,R*8),new THREE.ShadowMaterial({opacity:0.42}));
  g.rotation.x=-Math.PI/2; g.position.y=y+0.002; g.receiveShadow=true; return g;
}

window.renderModel=(url,view)=>new Promise((res,rej)=>{
  new THREE.GLTFLoader().load(url,gltf=>{
    const scene=makeScene(), root=gltf.scene;
    root.traverse(o=>{ if(o.isMesh){ o.castShadow=true; o.receiveShadow=true; } });
    // FX_* bone markers are tiny helper cubes — hide them in cameos
    root.traverse(o=>{ if(o.isMesh&&/^FX_/.test(o.name||'')) o.visible=false;
      if(o.isMesh&&o.parent&&/^FX_/.test(o.parent.name||'')&&o.parent.children.length===1) o.visible=false; });
    scene.add(root);
    const box=new THREE.Box3().setFromObject(root), c=box.getCenter(new THREE.Vector3()), sz=box.getSize(new THREE.Vector3());
    const R=sz.length()/2;
    const cam=makeCam(c,sz.length(),view); addLights(scene,cam,c,R);
    const g=shadowCatcher(box.min.y,R); scene.add(g);
    res(shoot(scene,cam,g));
  },undefined,e=>rej(String(e&&e.message||e)));
});

// Upgrade_FortressReinforcedPlating: beveled armor plate, raised shield boss, rivets.
window.renderPlate=()=>{
  const scene=makeScene();
  const C=h=>new THREE.Color(h).convertSRGBToLinear(); // r147 legacy mode: hex is linear otherwise
  const steel=new THREE.MeshStandardMaterial({color:C(0x5a6573),metalness:0.35,roughness:0.5});
  const steelDark=new THREE.MeshStandardMaterial({color:C(0x2a3039),metalness:0.4,roughness:0.6});
  const gold=new THREE.MeshStandardMaterial({color:C(0xc98a12),metalness:0.7,roughness:0.32,emissive:C(0x2a1400)});
  const rivetM=new THREE.MeshStandardMaterial({color:C(0x9aa4b0),metalness:0.6,roughness:0.3});
  const grp=new THREE.Group(); scene.add(grp);
  const rr=(w,h,cut)=>{ const s=new THREE.Shape(), x=w/2, y=h/2;
    s.moveTo(-x+cut,-y); s.lineTo(x-cut,-y); s.lineTo(x,-y+cut); s.lineTo(x,y-cut); s.lineTo(x-cut,y);
    s.lineTo(-x+cut,y); s.lineTo(-x,y-cut); s.lineTo(-x,-y+cut); s.closePath(); return s; };
  const ext=(shape,depth,bev)=>new THREE.ExtrudeGeometry(shape,{depth,bevelEnabled:true,bevelThickness:bev,bevelSize:bev,bevelSegments:2,curveSegments:24});
  // back plate + front plate (stacked, chamfered corners)
  const back=new THREE.Mesh(ext(rr(4.4,4.4,0.9),0.25,0.12),steelDark); back.position.z=-0.35; grp.add(back);
  const plate=new THREE.Mesh(ext(rr(4.0,4.0,0.75),0.22,0.16),steel); grp.add(plate);
  // seams: grooves as dark thin boxes
  for(const [x,y,w,h] of [[0,1.25,3.0,0.06],[0,-1.25,3.0,0.06]]){ const m=new THREE.Mesh(new THREE.BoxGeometry(w,h,0.05),steelDark); m.position.set(x,y,0.4); grp.add(m); }
  // shield emblem
  const sh=new THREE.Shape(); sh.moveTo(0,1.05); sh.quadraticCurveTo(0.55,0.95,0.85,1.0); sh.lineTo(0.85,0.15);
  sh.quadraticCurveTo(0.8,-0.75,0,-1.15); sh.quadraticCurveTo(-0.8,-0.75,-0.85,0.15); sh.lineTo(-0.85,1.0);
  sh.quadraticCurveTo(-0.55,0.95,0,1.05);
  const shield=new THREE.Mesh(ext(sh,0.18,0.1),gold); shield.position.z=0.36; grp.add(shield);
  const inner=new THREE.Mesh(ext(sh,0.08,0.05),new THREE.MeshStandardMaterial({color:C(0x1d4fb0),metalness:0.3,roughness:0.45})); inner.scale.set(0.66,0.66,1); inner.position.set(0,0.02,0.58); grp.add(inner);
  // "+" on the shield (reinforcement)
  for(const [w,h] of [[0.18,0.75],[0.6,0.18]]){ const m=new THREE.Mesh(new THREE.BoxGeometry(w,h,0.12),gold); m.position.set(0,0.0,0.7); grp.add(m); }
  // rivets around the rim
  const rg=new THREE.SphereGeometry(0.13,20,12,0,Math.PI*2,0,Math.PI/2);
  const pts=[]; for(const s of [-1,1]){ for(const t of [-1.2,-0.4,0.4,1.2]){ pts.push([t,s*1.72],[s*1.72,t]); } }
  for(const [x,y] of pts){ const m=new THREE.Mesh(rg,rivetM); m.rotation.x=Math.PI/2; m.position.set(x,y,0.38); grp.add(m); }
  grp.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true;}});
  grp.rotation.set(-0.35,0.45,0.05);
  const box=new THREE.Box3().setFromObject(grp), c=box.getCenter(new THREE.Vector3()), sz=box.getSize(new THREE.Vector3());
  const cam=new THREE.PerspectiveCamera(26,1,0.05,500); cam.position.set(c.x,c.y+0.6,c.z+sz.length()*2.6); cam.lookAt(c);
  addLights(scene,cam,c,sz.length()/2);
  const g=new THREE.Mesh(new THREE.PlaneGeometry(1,1),new THREE.MeshBasicMaterial()); g.visible=false; scene.add(g);
  return shoot(scene,cam,g);
};
document.title='ready';
</script></body></html>`;

(async () => {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const three = { 'three.min.js': findThree('three.min.js'), 'GLTFLoader.js': findThree('GLTFLoader.js'), 'RoomEnvironment.js': findThree('RoomEnvironment.js') };
  const b = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: SIZE, height: SIZE } });
  const logs = [];
  p.on('console', (m) => logs.push(m.text()));
  p.on('pageerror', (e) => logs.push('ERR ' + e));
  const types = { '.js': 'text/javascript', '.html': 'text/html', '.glb': 'model/gltf-binary', '.gltf': 'model/gltf+json', '.bin': 'application/octet-stream', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg' };
  await p.route('http://render.local/**', (rt) => {
    const u = decodeURIComponent(new URL(rt.request().url()).pathname);
    let f = null;
    if (u === '/index.html') return rt.fulfill({ body: PAGE, contentType: 'text/html' });
    if (u.startsWith('/three/')) f = three[u.slice(7)];
    else if (u.startsWith('/models/')) { f = path.resolve(MODELS_DIR, u.slice(8)); if (!f.startsWith(MODELS_DIR + path.sep)) f = null; }
    if (!f || !fs.existsSync(f)) { logs.push('404 ' + u); return rt.fulfill({ status: 404, body: '' }); }
    rt.fulfill({ body: fs.readFileSync(f), contentType: types[path.extname(f).toLowerCase()] || 'application/octet-stream' });
  });
  await p.goto('http://render.local/index.html');
  await p.waitForFunction(() => document.title === 'ready', null, { timeout: 60000 });

  const manifestPath = path.join(OUT_DIR, 'manifest.json');
  let manifest = {};
  try { manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8')); } catch (e) { /* fresh */ }
  let failed = 0;
  for (const j of jobs()) {
    try {
      const res = j.url
        ? await p.evaluate(([u, v]) => window.renderModel(u, v), [j.url, j.view])
        : await p.evaluate(() => window.renderPlate());
      if (!res.bbox) throw new Error('empty render');
      const file = path.join(OUT_DIR, `${j.id}.png`);
      fs.writeFileSync(file, Buffer.from(res.png.split(',')[1], 'base64'));
      manifest[j.id] = { file: `${j.id}.png`, faction: j.faction, kind: j.kind, bbox: res.bbox, size: SIZE };
      console.log('rendered', j.id, res.bbox.join(','));
    } catch (e) {
      failed++; console.error('FAILED', j.id, e.message || e);
    }
  }
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
  const bad = logs.filter((l) => !/GPU stall|WebGL|swiftshader/i.test(l));
  if (bad.length) console.log('page log:', bad.slice(0, 10).join(' | '));
  console.log(`renders + manifest in ${OUT_DIR}`);
  await b.close();
  process.exit(failed ? 1 : 0);
})();
