/* map.js – Desert tactical map geometry
   Original layout inspired by competitive two-site tactical shooter design.
   All geometry, textures, and code are original work. */

// ──────────── procedural textures ────────────

function _tex(base, opts) {
  var s = (opts && opts.size) || 256;
  var c = document.createElement('canvas');
  c.width = c.height = s;
  var g = c.getContext('2d');
  g.fillStyle = base;
  g.fillRect(0, 0, s, s);
  var id = g.getImageData(0, 0, s, s), d = id.data;
  var n = (opts && opts.noise) || 12;
  for (var i = 0; i < d.length; i += 4) {
    var v = (Math.random() - 0.5) * n;
    d[i] = Math.max(0, Math.min(255, d[i] + v));
    d[i+1] = Math.max(0, Math.min(255, d[i+1] + v));
    d[i+2] = Math.max(0, Math.min(255, d[i+2] + v * 0.8));
  }
  g.putImageData(id, 0, 0);
  if (opts && opts.bricks) {
    var bh = opts.bh || 32, bw = opts.bw || 64;
    g.strokeStyle = 'rgba(60,45,30,0.18)'; g.lineWidth = 1;
    for (var y = 0; y < s; y += bh) {
      g.beginPath(); g.moveTo(0, y); g.lineTo(s, y); g.stroke();
      var off = (Math.floor(y / bh) & 1) * (bw >> 1);
      for (var x = off; x < s; x += bw) {
        g.beginPath(); g.moveTo(x, y); g.lineTo(x, y + bh); g.stroke();
      }
    }
  }
  if (opts && opts.grain) {
    g.strokeStyle = 'rgba(80,50,20,0.13)'; g.lineWidth = 0.7;
    for (var y2 = 0; y2 < s; y2 += 3 + Math.random() * 4) {
      g.beginPath(); g.moveTo(0, y2 + Math.random()*2);
      for (var x2 = 0; x2 < s; x2 += 16) g.lineTo(x2, y2 + Math.random()*3);
      g.stroke();
    }
  }
  var t = new THREE.CanvasTexture(c);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  return t;
}

function _mats() {
  var ss = _tex('#C4A872', {noise:22, bricks:true, bh:20, bw:44});
  ss.repeat.set(3,3);
  var pl = _tex('#D8CCAE', {noise:10});
  pl.repeat.set(2,2);
  var cn = _tex('#9A8E7C', {noise:16});
  cn.repeat.set(3,3);
  var dt = _tex('#B49A6E', {noise:28});
  dt.repeat.set(2,2);
  var wd = _tex('#7A5A1C', {noise:8, grain:true});
  wd.repeat.set(1,1);
  var dk = _tex('#6B5B46', {noise:14, bricks:true, bh:16, bw:32});
  dk.repeat.set(2,2);
  var rf = _tex('#8B7458', {noise:16});
  rf.repeat.set(3,3);
  return {
    sandstone: new THREE.MeshStandardMaterial({map:ss, roughness:0.88, metalness:0.04}),
    plaster:   new THREE.MeshStandardMaterial({map:pl, roughness:0.92, metalness:0.02}),
    concrete:  new THREE.MeshStandardMaterial({map:cn, roughness:0.90, metalness:0.05}),
    dirt:      new THREE.MeshStandardMaterial({map:dt, roughness:0.96, metalness:0.01}),
    wood:      new THREE.MeshStandardMaterial({map:wd, roughness:0.78, metalness:0.04}),
    darkstone: new THREE.MeshStandardMaterial({map:dk, roughness:0.86, metalness:0.06}),
    roof:      new THREE.MeshStandardMaterial({map:rf, roughness:0.90, metalness:0.04}),
    boundary:  new THREE.MeshStandardMaterial({color:0x221E18, roughness:1, metalness:0}),
    bombA:     new THREE.MeshStandardMaterial({color:0xCC3333, roughness:0.6, metalness:0.2, emissive:0x661111}),
    bombB:     new THREE.MeshStandardMaterial({color:0xCC3333, roughness:0.6, metalness:0.2, emissive:0x661111}),
    tSpawn:    new THREE.MeshStandardMaterial({color:0xCC8833, roughness:0.6, metalness:0.2, emissive:0x553311}),
    ctSpawn:   new THREE.MeshStandardMaterial({color:0x3355CC, roughness:0.6, metalness:0.2, emissive:0x112255})
  };
}

// ──────────── main build ────────────

function buildDust2Map(scene) {
  var M = _mats();
  var colliders = [];
  var floors = [];
  var locations = [];

  /* ---- helpers ---- */

  function addFloor(x1,z1,x2,z2,y,mk,ramp) {
    var fx1=Math.min(x1,x2), fx2=Math.max(x1,x2);
    var fz1=Math.min(z1,z2), fz2=Math.max(z1,z2);
    var w=fx2-fx1, d=fz2-fz1;
    if(w<0.05||d<0.05) return;
    var geo=new THREE.BoxGeometry(w,0.2,d);
    var mesh=new THREE.Mesh(geo, M[mk]||M.concrete);
    mesh.position.set((fx1+fx2)/2, y-0.1, (fz1+fz2)/2);
    mesh.receiveShadow=true;
    scene.add(mesh);
    var entry={x1:fx1,z1:fz1,x2:fx2,z2:fz2,y:y};
    if(ramp) entry.ramp=ramp;
    floors.push(entry);
  }

  function addWall(x1,z1,x2,z2,by,h,mk) {
    var dx=x2-x1, dz=z2-z1;
    var len=Math.sqrt(dx*dx+dz*dz);
    if(len<0.05) return;
    var th=0.4;
    var geo=new THREE.BoxGeometry(len,h,th);
    var mesh=new THREE.Mesh(geo, M[mk]||M.sandstone);
    mesh.position.set((x1+x2)/2, by+h/2, (z1+z2)/2);
    mesh.rotation.y=Math.atan2(dx,dz);
    mesh.castShadow=true; mesh.receiveShadow=true;
    scene.add(mesh);
    var hl=len/2, ht=th/2;
    var adx=Math.abs(dx)/len, adz=Math.abs(dz)/len;
    var hx=hl*adx+ht*adz, hz=hl*adz+ht*adx;
    var cx=(x1+x2)/2, cz=(z1+z2)/2;
    colliders.push({x1:cx-hx,z1:cz-hz,x2:cx+hx,z2:cz+hz,y1:by,y2:by+h});
  }

  function addBox(x,y,z,w,h,d,mk,noColl) {
    var geo=new THREE.BoxGeometry(w,h,d);
    var mesh=new THREE.Mesh(geo, M[mk]||M.sandstone);
    mesh.position.set(x,y+h/2,z);
    mesh.castShadow=true; mesh.receiveShadow=true;
    scene.add(mesh);
    if(!noColl){
      colliders.push({x1:x-w/2,z1:z-d/2,x2:x+w/2,z2:z+d/2,y1:y,y2:y+h});
    }
    // Also make it a walkable surface on top
    floors.push({x1:x-w/2,z1:z-d/2,x2:x+w/2,z2:z+d/2,y:y+h});
  }

  function addCeil(x1,z1,x2,z2,y,mk) {
    var w=Math.abs(x2-x1), d=Math.abs(z2-z1);
    var geo=new THREE.BoxGeometry(w,0.3,d);
    var mesh=new THREE.Mesh(geo, M[mk]||M.roof);
    mesh.position.set((x1+x2)/2, y, (z1+z2)/2);
    mesh.receiveShadow=true; mesh.castShadow=true;
    scene.add(mesh);
  }

  function addMarker(x,z,y,r,mk) {
    var geo=new THREE.CylinderGeometry(r,r,0.06,32);
    var mesh=new THREE.Mesh(geo, M[mk]);
    mesh.position.set(x,y+0.04,z);
    mesh.receiveShadow=true;
    scene.add(mesh);
  }

  function loc(name,x1,z1,x2,z2) {
    locations.push({name:name,x1:Math.min(x1,x2),z1:Math.min(z1,z2),x2:Math.max(x1,x2),z2:Math.max(z1,z2)});
  }

  // ═══════════════════════════════════════════════════════
  //  FLOOR AREAS
  // ═══════════════════════════════════════════════════════

  // -- T SPAWN --
  addFloor(-10, 65, 16, 82, 0, 'dirt');
  loc('T Spawn', -10, 65, 16, 82);

  // -- T to Long connector --
  addFloor(14, 60, 20, 72, 0, 'dirt');

  // -- East Gate / Outside Long --
  addFloor(18, 50, 30, 68, 0, 'dirt');
  loc('Outside Long', 18, 50, 30, 68);

  // -- Long Doors corridor --
  addFloor(22, 44, 30, 50, 0, 'concrete');
  loc('Long Doors', 22, 44, 30, 50);

  // -- Long Alley --
  addFloor(22, 4, 30, 44, 0, 'concrete');
  loc('Long', 22, 4, 30, 44);

  // -- Long Corner --
  addFloor(16, -2, 30, 6, 0, 'concrete');
  loc('Long Corner', 16, -2, 30, 6);

  // -- Pit (sunken) --
  addFloor(20, -1, 28, 5, -1.2, 'concrete');
  loc('Pit', 20, -1, 28, 5);
  // Pit ramp back up (south side)
  addFloor(20, 4, 28, 7, 0, 'concrete', {ax:'z', a:-1.2, b:0});

  // -- A Cross --
  addFloor(10, -6, 22, 0, 0, 'concrete');
  loc('A Cross', 10, -6, 22, 0);

  // -- A Ramp (south side of A, going up) --
  addFloor(6, -8, 22, -4, 0, 'concrete', {ax:'z', a:1.0, b:0});
  loc('A Ramp', 6, -8, 22, -4);

  // -- Bombsite A (elevated platform) --
  addFloor(2, -24, 24, -8, 1.0, 'concrete');
  loc('Bombsite A', 2, -24, 24, -8);

  // -- Goose corner --
  addFloor(2, -24, 8, -20, 1.0, 'concrete');
  loc('Goose', 2, -24, 8, -20);

  // -- A default area (A to CT connector) --
  addFloor(4, -8, 10, 0, 0, 'concrete');

  // -- Catwalk to A stairs --
  addFloor(8, 0, 14, 6, 0, 'concrete', {ax:'z', a:2.2, b:1.0});
  loc('Short', 8, 0, 14, 6);

  // -- Catwalk / A Short (elevated) --
  addFloor(6, 6, 14, 22, 2.2, 'concrete');
  loc('Catwalk', 6, 6, 14, 22);

  // -- Short Stairs (Mid up to Catwalk) --
  addFloor(4, 18, 8, 26, 0, 'concrete', {ax:'z', a:2.2, b:0});
  loc('Short Stairs', 4, 18, 8, 26);

  // -- Mid --
  addFloor(-4, 18, 6, 48, 0, 'concrete');
  loc('Mid', -4, 22, 6, 48);

  // -- Mid Doors area --
  loc('Mid Doors', -4, 16, 6, 22);

  // -- Top Mid --
  addFloor(-4, 4, 6, 18, 0, 'concrete');
  loc('Top Mid', -4, 4, 6, 18);

  // -- CT Mid --
  addFloor(-6, -22, 6, 4, 0, 'concrete');
  loc('CT Mid', -6, -22, 6, 4);

  // -- Alley (T to Mid connector) --
  addFloor(-3, 48, 5, 65, 0, 'concrete');
  loc('Alley', -3, 48, 5, 65);

  // -- CT Spawn --
  addFloor(-12, -38, 16, -22, 0, 'concrete');
  loc('CT Spawn', -12, -38, 16, -22);

  // -- CT to A --
  addFloor(8, -28, 20, -10, 0, 'concrete');
  loc('CT to A', 8, -28, 20, -10);
  // Ramp portion leading up to A
  addFloor(8, -10, 18, -6, 0, 'concrete', {ax:'z', a:0, b:1.0});

  // -- CT to B corridor --
  addFloor(-30, -38, -10, -22, 0, 'concrete');
  loc('CT to B', -30, -38, -10, -22);

  // -- CT-B to B Back connector --
  addFloor(-32, -24, -26, -18, 0, 'concrete');
  loc('B Halls', -32, -24, -26, -18);

  // -- T Ramp (going down to tunnels) --
  addFloor(-18, 58, -4, 68, 0, 'concrete', {ax:'x', a:-2, b:0});
  loc('T Ramp', -18, 58, -4, 68);
  // Floor at bottom connecting to tunnels
  addFloor(-18, 55, -4, 60, -2, 'concrete');

  // -- T Ramp approach from T Spawn --
  addFloor(-10, 62, -2, 68, 0, 'dirt');

  // -- Lower Tunnels --
  addFloor(-32, 46, -16, 62, -2, 'concrete');
  loc('Lower Tunnels', -32, 46, -16, 62);

  // -- Tunnel Stairs (going up from lower to upper) --
  addFloor(-32, 36, -16, 46, 0, 'concrete', {ax:'z', a:0, b:-2});
  loc('Tunnel Stairs', -32, 36, -16, 46);

  // -- Upper Tunnels --
  addFloor(-32, 20, -16, 36, 0, 'concrete');
  loc('Upper Tunnels', -32, 20, -16, 36);

  // -- B Approach --
  addFloor(-28, 8, -16, 20, 0, 'concrete');
  loc('B Approach', -28, 8, -16, 20);

  // -- B Tunnels --
  addFloor(-24, 2, -16, 10, 0, 'concrete');
  loc('B Tunnels', -24, 2, -16, 10);

  // -- Window Ledge (elevated) --
  addFloor(-28, 2, -22, 10, 1.5, 'concrete');
  loc('Window', -28, 2, -22, 10);

  // -- Bombsite B --
  addFloor(-44, -18, -16, 4, 0, 'concrete');
  loc('Bombsite B', -44, -18, -16, 4);

  // -- B Platform (elevated) --
  addFloor(-44, -12, -36, 0, 1.0, 'concrete');
  loc('B Platform', -44, -12, -36, 0);
  // step up to platform
  addFloor(-44, -2, -36, 2, 0, 'concrete', {ax:'z', a:1.0, b:0});

  // -- B Back --
  addFloor(-44, -28, -26, -18, 0, 'concrete');
  loc('B Back', -44, -28, -26, -18);

  // ═══════════════════════════════════════════════════════
  //  WALLS
  // ═══════════════════════════════════════════════════════
  var H = 7;   // standard wall height
  var HM = 5.5; // mid-height walls

  // --- outer boundary ---
  addWall(-48, -42, 36, -42, 0, 16, 'boundary');
  addWall(36, -42, 36, 88, 0, 16, 'boundary');
  addWall(-48, 88, 36, 88, 0, 16, 'boundary');
  addWall(-48, -42, -48, 88, 0, 16, 'boundary');

  // ---- T SPAWN ----
  addWall(-10, 82, 16, 82, 0, H, 'sandstone');    // back (south)
  addWall(16, 72, 16, 82, 0, H, 'sandstone');     // east-south
  addWall(-10, 68, -10, 82, 0, H, 'sandstone');   // west-south
  addWall(-10, 65, -4, 65, 0, H, 'sandstone');    // north-wall west of alley
  addWall(6, 65, 16, 65, 0, H, 'sandstone');      // north-wall east of alley
  addWall(16, 60, 16, 68, 0, H, 'sandstone');     // east-north segment
  // opening from x=-4 to x=6 at z=65 = alley entrance (8m wide)
  // opening from z=60 to z=72 at x=16 = approach to Long (east side)

  // ---- ALLEY (T → Mid) ----
  addWall(5, 48, 5, 65, 0, HM, 'plaster');
  addWall(-3, 48, -3, 65, 0, HM, 'plaster');

  // ---- EAST GATE / OUTSIDE LONG ----
  addWall(18, 68, 30, 68, 0, H, 'sandstone');     // south wall
  addWall(30, 50, 30, 68, 0, H, 'sandstone');     // east wall
  addWall(18, 50, 22, 50, 0, H, 'sandstone');     // north wall west of Long Doors
  addWall(18, 56, 18, 68, 0, H, 'sandstone');     // west wall (between East Gate and T connector)

  // ---- LONG DOORS ----
  addWall(22, 44, 22, 50, 0, HM, 'sandstone');
  addWall(30, 44, 30, 50, 0, HM, 'sandstone');
  // Door frame at z=47 (visual only)
  addBox(23.2, 0, 47, 0.4, 4.5, 0.4, 'darkstone');
  addBox(28.8, 0, 47, 0.4, 4.5, 0.4, 'darkstone');
  addBox(26, 4.5, 47, 6, 0.4, 0.6, 'darkstone', true);
  // Door panels (visual, no collision)
  addBox(24.5, 0, 47, 2.2, 4.2, 0.15, 'wood', true);
  addBox(27.5, 0, 47, 2.2, 4.2, 0.15, 'wood', true);

  // ---- LONG ALLEY ----
  addWall(30, 4, 30, 44, 0, H, 'sandstone');      // east
  addWall(22, 6, 22, 44, 0, H, 'sandstone');      // west
  // Barrel in long
  addBox(29, 0, 30, 1.2, 1.4, 1.2, 'darkstone');
  addBox(29, 0, 24, 1, 1.0, 1, 'wood');

  // ---- LONG CORNER ----
  addWall(30, -4, 30, 4, 0, H, 'sandstone');      // east extension
  addWall(24, -6, 30, -6, 0, H, 'sandstone');     // south wall of corner area
  // Buildings above/around
  addWall(16, -4, 16, 6, 0, H, 'plaster');        // west side of corner

  // ---- PIT ----
  // Pit walls (retaining walls around the sunken area)
  addWall(20, -1, 20, 5, -1.2, 1.4, 'darkstone');  // west
  addWall(28, -1, 28, 5, -1.2, 1.4, 'darkstone');  // east
  addWall(20, -1, 28, -1, -1.2, 1.4, 'darkstone'); // north

  // ---- A CROSS ----
  // Open area, mostly formed by surrounding walls
  addWall(10, -6, 10, 0, 0, H, 'plaster');         // west wall
  addWall(10, -6, 22, -6, 0, H, 'sandstone');      // south wall (below A site)

  // ---- A SITE ----
  addWall(24, -24, 24, -6, 1, H, 'sandstone');     // east
  addWall(2, -24, 24, -24, 1, H, 'sandstone');     // north (back)
  addWall(2, -24, 2, -4, 1, H, 'sandstone');       // west
  addWall(2, -4, 6, -4, 1, H, 'sandstone');        // south-west partial
  // South side mostly open (ramp + cross connection)
  // Crates on A site
  addBox(18, 1.0, -16, 2.4, 1.6, 2.4, 'wood');       // default box
  addBox(15.5, 1.0, -17, 1.6, 0.9, 1.6, 'wood');     // smaller crate
  addBox(6, 1.0, -12, 2.0, 1.2, 2.0, 'wood');        // goose-side crate
  // A site bombsite marker
  addMarker(14, -14, 1.0, 2.0, 'bombA');

  // ---- GOOSE ----
  // Already part of A Site floor, enclosed by A Site walls

  // ---- CATWALK / A SHORT ----
  addWall(14, 6, 14, 22, 2.2, 2.5, 'plaster');    // east wall (parapet)
  addWall(6, 6, 6, 22, 0, 5, 'plaster');           // west wall (full height from ground)
  addWall(6, 22, 14, 22, 0, 5, 'plaster');         // south wall (above stairs)
  addWall(6, 6, 14, 6, 2.2, 2.5, 'plaster');      // north wall (toward A site)
  // Catwalk underside (visual, as supporting structure)
  addCeil(6, 6, 14, 22, 2.1, 'concrete');

  // ---- SHORT STAIRS ----
  addWall(4, 18, 4, 26, 0, 5, 'plaster');          // west wall
  addWall(8, 18, 8, 26, 0, 5, 'plaster');          // east wall (shared with catwalk)

  // ---- MID ----
  addWall(6, 26, 6, 48, 0, HM, 'plaster');         // east wall
  addWall(-4, 26, -4, 48, 0, HM, 'plaster');       // west wall
  // Xbox (crate in mid for boosting)
  addBox(1, 0, 24, 2.0, 1.3, 2.0, 'wood');
  // Barrel in mid
  addBox(-2.5, 0, 32, 1.0, 1.2, 1.0, 'darkstone');

  // ---- MID DOORS ----
  addWall(6, 18, 6, 26, 0, HM, 'plaster');         // east wall
  addWall(-4, 18, -4, 26, 0, HM, 'plaster');       // west wall
  // Door frames at z=20
  addBox(-2, 0, 20, 0.4, 4, 0.4, 'darkstone');
  addBox(4, 0, 20, 0.4, 4, 0.4, 'darkstone');
  addBox(1, 4, 20, 6.5, 0.4, 0.5, 'darkstone', true);
  // Door panels (gaps between doors for sightlines)
  addBox(-0.5, 0, 20, 2.4, 3.6, 0.15, 'wood', true);
  addBox(2.5, 0, 20, 2.4, 3.6, 0.15, 'wood', true);

  // ---- TOP MID ----
  addWall(6, 4, 6, 18, 0, HM, 'plaster');          // east (with opening for short stairs)
  addWall(-4, 4, -4, 18, 0, HM, 'plaster');        // west

  // ---- CT MID ----
  addWall(6, -22, 6, 4, 0, HM, 'plaster');         // east
  addWall(-6, -22, -6, 4, 0, HM, 'plaster');       // west

  // ---- CT SPAWN ----
  addWall(-12, -38, 16, -38, 0, H, 'sandstone');   // north (back)
  addWall(16, -38, 16, -22, 0, H, 'sandstone');    // east
  addWall(-12, -38, -12, -22, 0, H, 'sandstone');  // west
  addWall(-12, -22, -6, -22, 0, H, 'sandstone');   // south-west of CT Mid opening
  addWall(6, -22, 16, -22, 0, H, 'sandstone');     // south-east of CT Mid opening
  // opening from x=-6 to x=6 at z=-22 = CT Mid entrance

  // ---- CT TO A ----
  addWall(20, -28, 20, -6, 0, H, 'sandstone');     // east
  addWall(8, -28, 8, -10, 0, H, 'sandstone');      // west
  addWall(8, -28, 20, -28, 0, H, 'sandstone');     // south (connects at CT Spawn opening)
  // north opens to A ramp / A site area

  // ---- CT TO B ----
  addWall(-30, -38, -12, -38, 0, H, 'sandstone');  // north
  addWall(-30, -22, -10, -22, 0, H, 'sandstone');  // south
  addWall(-30, -38, -30, -22, 0, H, 'sandstone');  // west (opening to B Back connector)

  // B Halls connector walls
  addWall(-32, -24, -32, -16, 0, H, 'sandstone');  // west
  addWall(-26, -24, -26, -16, 0, H, 'sandstone');  // east (partial)

  // ---- T RAMP ----
  addWall(-4, 58, -4, 68, 0, HM, 'plaster');       // east
  addWall(-18, 55, -18, 68, -2, 8, 'sandstone');   // west
  addWall(-18, 68, -4, 68, 0, HM, 'plaster');      // south
  // Ceiling over ramp
  addCeil(-18, 58, -4, 68, 4, 'roof');

  // ---- LOWER TUNNELS ----
  addWall(-32, 46, -32, 62, -2, 6, 'sandstone');   // west
  addWall(-16, 46, -16, 56, -2, 6, 'sandstone');   // east (south section)
  addWall(-32, 62, -16, 62, -2, 6, 'sandstone');   // south
  // Ceiling
  addCeil(-32, 46, -16, 62, 4, 'roof');

  // ---- TUNNEL STAIRS / CONNECTOR ----
  addWall(-32, 36, -32, 46, 0, 6, 'sandstone');
  addWall(-16, 36, -16, 46, 0, 6, 'sandstone');
  addCeil(-32, 36, -16, 46, 5, 'roof');

  // ---- UPPER TUNNELS ----
  addWall(-32, 20, -32, 36, 0, 6, 'sandstone');
  addWall(-16, 20, -16, 36, 0, 6, 'sandstone');
  addCeil(-32, 20, -16, 36, 5.5, 'roof');
  // Crate in upper tunnels
  addBox(-24, 0, 28, 1.8, 1.2, 1.8, 'wood');

  // ---- B APPROACH ----
  addWall(-28, 8, -28, 20, 0, 6, 'sandstone');
  addWall(-16, 8, -16, 20, 0, 6, 'sandstone');
  addCeil(-28, 8, -16, 20, 5, 'roof');

  // ---- B TUNNELS / B GATE ----
  addWall(-24, 2, -24, 8, 0, 6, 'sandstone');      // west
  addWall(-16, 2, -16, 8, 0, 6, 'sandstone');      // east
  // Door frame at z=4
  addBox(-22.2, 0, 4, 0.4, 4, 0.4, 'darkstone');
  addBox(-17.8, 0, 4, 0.4, 4, 0.4, 'darkstone');
  addBox(-20, 4, 4, 4.8, 0.4, 0.5, 'darkstone', true);
  // Door panels
  addBox(-20.8, 0, 4, 2.8, 3.6, 0.15, 'wood', true);
  addBox(-19.2, 0, 4, 2.8, 3.6, 0.15, 'wood', true);
  addCeil(-24, 2, -16, 8, 5, 'roof');

  // ---- WINDOW LEDGE ----
  // Elevated position, east wall of B has a window opening
  addWall(-28, 2, -28, 10, 0, 6, 'sandstone');     // west wall of window room
  addWall(-22, 2, -22, 10, 0, 1.2, 'sandstone');   // east wall (low - window sill)
  addWall(-22, 2, -22, 10, 3, 3, 'sandstone');     // east wall (above window)
  addCeil(-28, 2, -22, 10, 5, 'roof');
  // stairs up to window from B Approach
  addFloor(-28, 10, -22, 14, 0, 'concrete', {ax:'z', a:1.5, b:0});

  // ---- B SITE ----
  addWall(-44, -18, -44, 4, 0, H, 'sandstone');    // west
  addWall(-44, -18, -16, -18, 0, H, 'sandstone');  // north
  addWall(-16, -18, -16, 2, 0, H, 'sandstone');    // east (with opening for B Gate)
  addWall(-44, 4, -24, 4, 0, H, 'sandstone');      // south (with openings for B Gate & tunnels)
  // Crates at B site
  addBox(-32, 0, -8, 2.4, 1.6, 2.4, 'wood');
  addBox(-30, 0, -6, 1.6, 0.9, 1.6, 'wood');
  addBox(-20, 0, -10, 2.0, 2.0, 2.0, 'wood');
  addBox(-20, 2.0, -10, 2.0, 1.0, 2.0, 'wood');   // stacked
  // Barrel
  addBox(-38, 0, 2, 1.2, 1.4, 1.2, 'darkstone');
  // B bombsite marker
  addMarker(-30, -6, 0, 2.0, 'bombB');

  // ---- B PLATFORM ----
  // Elevated floor already added, retaining wall on front
  addWall(-36, 0, -36, -12, 0, 1.2, 'darkstone'); // front edge (east)
  addWall(-44, 0, -36, 0, 0, 1.2, 'darkstone');   // front edge (north)
  addWall(-44, -12, -36, -12, 0, 1.2, 'darkstone');// south edge

  // ---- B BACK ----
  addWall(-44, -28, -44, -18, 0, H, 'sandstone');  // west
  addWall(-44, -28, -26, -28, 0, H, 'sandstone');  // north
  addWall(-26, -28, -26, -18, 0, H, 'sandstone');  // east (opening to B Halls connector)

  // ═══════════════════════════════════════════════════════
  //  ARCHITECTURAL DETAILS
  // ═══════════════════════════════════════════════════════

  // -- Arches and decorative elements in A site --
  // Arch over A ramp entrance
  addBox(14, 4.5, -6, 8, 0.5, 0.6, 'darkstone', true);

  // -- Wall-top decorations (parapets, ledges) --
  // Long alley parapets
  addBox(30.3, 7, 24, 0.5, 0.8, 40, 'darkstone', true);
  addBox(21.7, 7, 24, 0.5, 0.8, 40, 'darkstone', true);

  // -- Accent walls / trims on buildings --
  addBox(6.3, 5, 36, 0.5, 0.6, 22, 'darkstone', true);    // mid east building top
  addBox(-4.3, 5, 36, 0.5, 0.6, 22, 'darkstone', true);   // mid west building top

  // -- Ground details around T Spawn --
  addBox(-6, 0, 74, 1.4, 0.5, 2.5, 'darkstone');   // low wall / bench
  addBox(10, 0, 76, 2, 0.8, 1.5, 'wood');           // crates at T spawn
  addBox(12, 0, 76, 1.4, 0.5, 1.4, 'wood');

  // -- CT Spawn details --
  addBox(0, 0, -30, 1.5, 0.6, 1.5, 'darkstone');   // fountain / center piece
  addBox(-8, 0, -34, 2, 0.9, 1.5, 'wood');          // supply crates

  // ═══════════════════════════════════════════════════════
  //  SPAWN & BOMBSITE MARKERS
  // ═══════════════════════════════════════════════════════

  addMarker(2, 72, 0, 1.5, 'tSpawn');
  addMarker(2, -30, 0, 1.5, 'ctSpawn');

  // ═══════════════════════════════════════════════════════
  //  EXTRA BUILDINGS / SKY STRUCTURES (visual only)
  // ═══════════════════════════════════════════════════════

  // Buildings visible above walls (skyline)
  function addBuilding(x,z,w,d,h,mk) {
    var geo = new THREE.BoxGeometry(w,h,d);
    var mesh = new THREE.Mesh(geo, M[mk]||M.sandstone);
    mesh.position.set(x, h/2, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    scene.add(mesh);
  }

  // Buildings east of Long
  addBuilding(36, 25, 8, 30, 12, 'sandstone');
  addBuilding(38, 55, 6, 16, 10, 'sandstone');
  // Buildings west of Mid
  addBuilding(-10, 36, 6, 20, 10, 'plaster');
  // Buildings behind A site
  addBuilding(14, -30, 18, 8, 9, 'sandstone');
  // Buildings around B
  addBuilding(-48, -8, 4, 20, 11, 'sandstone');
  addBuilding(-48, -24, 5, 10, 9, 'sandstone');
  // Building behind CT
  addBuilding(2, -44, 24, 6, 8, 'plaster');
  // Tower near T spawn
  addBuilding(-16, 78, 8, 8, 14, 'sandstone');
  // Building south of long
  addBuilding(34, 72, 6, 12, 11, 'sandstone');

  // ═══════════════════════════════════════════════════════
  //  GROUND PLANE (large dirt ground beyond playable area)
  // ═══════════════════════════════════════════════════════
  var groundGeo = new THREE.PlaneGeometry(200, 200);
  var groundMat = new THREE.MeshStandardMaterial({color:0x9E8A64, roughness:1});
  var ground = new THREE.Mesh(groundGeo, groundMat);
  ground.rotation.x = -Math.PI/2;
  ground.position.y = -0.15;
  ground.receiveShadow = true;
  scene.add(ground);

  // ═══════════════════════════════════════════════════════

  return {
    colliders: colliders,
    floors: floors,
    locations: locations,
    spawns: {
      t:  {x:2, y:0, z:72},
      ct: {x:2, y:0, z:-30}
    },
    bombsites: [
      {name:'A', x:14, z:-14},
      {name:'B', x:-30, z:-6}
    ]
  };
}
