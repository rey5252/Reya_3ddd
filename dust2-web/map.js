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

  // Base noise pass
  var id = g.getImageData(0, 0, s, s), d = id.data;
  var n = (opts && opts.noise) || 12;
  for (var i = 0; i < d.length; i += 4) {
    var v = (Math.random() - 0.5) * n;
    d[i]   = Math.max(0, Math.min(255, d[i] + v));
    d[i+1] = Math.max(0, Math.min(255, d[i+1] + v));
    d[i+2] = Math.max(0, Math.min(255, d[i+2] + v * 0.8));
  }
  g.putImageData(id, 0, 0);

  // Brick pattern with per-brick color variation and mortar lines
  if (opts && opts.bricks) {
    var bh = opts.bh || 32, bw = opts.bw || 64;
    // Per-brick color variation
    for (var by = 0; by < s; by += bh) {
      var off = (Math.floor(by / bh) & 1) * (bw >> 1);
      for (var bx = off - bw; bx < s + bw; bx += bw) {
        var bx1 = Math.max(0, bx), by1 = by;
        var bx2 = Math.min(s, bx + bw), by2 = Math.min(s, by + bh);
        if (bx2 <= bx1 || by2 <= by1) continue;
        var shade = (Math.random() - 0.5) * 20;
        g.fillStyle = 'rgba(' +
          (shade > 0 ? '255,240,200' : '80,50,20') + ',' +
          (Math.abs(shade) / 120).toFixed(3) + ')';
        g.fillRect(bx1 + 1, by1 + 1, bx2 - bx1 - 2, by2 - by1 - 2);
        // Edge darkening
        g.fillStyle = 'rgba(40,25,10,0.06)';
        g.fillRect(bx1, by1, bx2 - bx1, 2);
        g.fillRect(bx1, by2 - 2, bx2 - bx1, 2);
        g.fillRect(bx1, by1, 2, by2 - by1);
        g.fillRect(bx2 - 2, by1, 2, by2 - by1);
      }
    }
    // Mortar lines
    g.strokeStyle = 'rgba(50,35,20,0.35)'; g.lineWidth = 2;
    for (var y = 0; y < s; y += bh) {
      g.beginPath(); g.moveTo(0, y); g.lineTo(s, y); g.stroke();
      var off2 = (Math.floor(y / bh) & 1) * (bw >> 1);
      for (var x = off2; x < s; x += bw) {
        g.beginPath(); g.moveTo(x, y); g.lineTo(x, y + bh); g.stroke();
      }
    }
    // Lighter mortar fill
    g.strokeStyle = 'rgba(180,165,140,0.2)'; g.lineWidth = 1;
    for (var y3 = 0; y3 < s; y3 += bh) {
      g.beginPath(); g.moveTo(0, y3 + 1); g.lineTo(s, y3 + 1); g.stroke();
    }
  }

  // Stucco / plaster cracks
  if (opts && opts.cracks) {
    g.strokeStyle = 'rgba(80,60,40,0.12)'; g.lineWidth = 0.8;
    for (var ci = 0; ci < 12; ci++) {
      var cx = Math.random() * s, cy = Math.random() * s;
      g.beginPath(); g.moveTo(cx, cy);
      for (var cj = 0; cj < 4 + Math.random() * 6; cj++) {
        cx += (Math.random() - 0.5) * 30;
        cy += (Math.random() - 0.3) * 20;
        g.lineTo(cx, cy);
      }
      g.stroke();
    }
    // Subtle stain patches
    for (var si = 0; si < 5; si++) {
      g.fillStyle = 'rgba(100,80,50,' + (0.02 + Math.random() * 0.04).toFixed(3) + ')';
      g.beginPath();
      g.arc(Math.random() * s, Math.random() * s, 10 + Math.random() * 30, 0, Math.PI * 2);
      g.fill();
    }
  }

  // Wood grain (pronounced)
  if (opts && opts.grain) {
    // Dark grain lines
    g.strokeStyle = 'rgba(50,25,5,0.22)'; g.lineWidth = 1.2;
    for (var y2 = 0; y2 < s; y2 += 2 + Math.random() * 3) {
      g.beginPath(); g.moveTo(0, y2 + Math.random() * 2);
      for (var x2 = 0; x2 < s; x2 += 8) g.lineTo(x2, y2 + Math.random() * 3);
      g.stroke();
    }
    // Light grain highlights
    g.strokeStyle = 'rgba(200,170,100,0.12)'; g.lineWidth = 0.6;
    for (var y4 = 0; y4 < s; y4 += 5 + Math.random() * 6) {
      g.beginPath(); g.moveTo(0, y4 + Math.random());
      for (var x4 = 0; x4 < s; x4 += 12) g.lineTo(x4, y4 + Math.random() * 2);
      g.stroke();
    }
    // Knot holes
    for (var ki = 0; ki < 2; ki++) {
      g.fillStyle = 'rgba(60,30,5,0.15)';
      g.beginPath();
      g.ellipse(Math.random() * s, Math.random() * s, 4 + Math.random() * 6, 2 + Math.random() * 3, Math.random(), 0, Math.PI * 2);
      g.fill();
    }
  }

  // Dirt/pebble pattern
  if (opts && opts.pebbles) {
    for (var pi = 0; pi < 60; pi++) {
      var pr = 1 + Math.random() * 3;
      var px = Math.random() * s, py = Math.random() * s;
      var pv = Math.floor(80 + Math.random() * 60);
      g.fillStyle = 'rgba(' + pv + ',' + Math.floor(pv * 0.85) + ',' + Math.floor(pv * 0.6) + ',0.3)';
      g.beginPath();
      g.ellipse(px, py, pr, pr * (0.6 + Math.random() * 0.4), Math.random() * Math.PI, 0, Math.PI * 2);
      g.fill();
    }
    // Subtle variation patches
    for (var di = 0; di < 8; di++) {
      g.fillStyle = 'rgba(' + (Math.random() > 0.5 ? '140,110,60' : '90,70,40') + ',0.06)';
      g.beginPath();
      g.arc(Math.random() * s, Math.random() * s, 15 + Math.random() * 25, 0, Math.PI * 2);
      g.fill();
    }
  }

  var t = new THREE.CanvasTexture(c);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  return t;
}

function _mats() {
  var ss = _tex('#C4A872', {noise:22, bricks:true, bh:20, bw:44});
  ss.repeat.set(3,3);
  var pl = _tex('#D8CCAE', {noise:10, cracks:true});
  pl.repeat.set(2,2);
  var cn = _tex('#9A8E7C', {noise:16, bricks:true, bh:24, bw:48});
  cn.repeat.set(3,3);
  var dt = _tex('#B49A6E', {noise:28, pebbles:true});
  dt.repeat.set(2,2);
  var wd = _tex('#7A5A1C', {noise:8, grain:true});
  wd.repeat.set(1,1);
  var dk = _tex('#6B5B46', {noise:14, bricks:true, bh:16, bw:32});
  dk.repeat.set(2,2);
  var rf = _tex('#8B7458', {noise:16, cracks:true});
  rf.repeat.set(3,3);
  var trim = _tex('#4A3C2E', {noise:10, bricks:true, bh:12, bw:24});
  trim.repeat.set(4,4);
  return {
    sandstone: new THREE.MeshStandardMaterial({map:ss, roughness:0.88, metalness:0.04}),
    plaster:   new THREE.MeshStandardMaterial({map:pl, roughness:0.92, metalness:0.02}),
    concrete:  new THREE.MeshStandardMaterial({map:cn, roughness:0.90, metalness:0.05}),
    dirt:      new THREE.MeshStandardMaterial({map:dt, roughness:0.96, metalness:0.01}),
    wood:      new THREE.MeshStandardMaterial({map:wd, roughness:0.78, metalness:0.04}),
    darkstone: new THREE.MeshStandardMaterial({map:dk, roughness:0.86, metalness:0.06}),
    roof:      new THREE.MeshStandardMaterial({map:rf, roughness:0.90, metalness:0.04}),
    trim:      new THREE.MeshStandardMaterial({map:trim, roughness:0.84, metalness:0.06}),
    boundary:  new THREE.MeshStandardMaterial({color:0x221E18, roughness:1, metalness:0}),
    bombA:     new THREE.MeshStandardMaterial({color:0xCC3333, roughness:0.6, metalness:0.2, emissive:0x661111}),
    bombB:     new THREE.MeshStandardMaterial({color:0xCC3333, roughness:0.6, metalness:0.2, emissive:0x661111}),
    tSpawn:    new THREE.MeshStandardMaterial({color:0xCC8833, roughness:0.6, metalness:0.2, emissive:0x553311}),
    ctSpawn:   new THREE.MeshStandardMaterial({color:0x3355CC, roughness:0.6, metalness:0.2, emissive:0x112255}),
    metal:     new THREE.MeshStandardMaterial({color:0x556677, roughness:0.4, metalness:0.7}),
    bluemetal: new THREE.MeshStandardMaterial({color:0x3355AA, roughness:0.5, metalness:0.6})
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
    // Scale UV based on wall dimensions for consistent tiling
    var geo=new THREE.BoxGeometry(len,h,th);
    var uvAttr = geo.attributes.uv;
    if (uvAttr) {
      var arr = uvAttr.array;
      var tileW = len / 4.0;
      var tileH = h / 4.0;
      for (var fi = 0; fi < geo.groups.length; fi++) {
        var grp = geo.groups[fi];
        if (fi === 4 || fi === 5) {
          for (var vi = grp.start; vi < grp.start + grp.count; vi++) {
            arr[vi * 2] *= tileW;
            arr[vi * 2 + 1] *= tileH;
          }
        }
      }
    }
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

  // Dark trim strip along base of a wall segment (visual only)
  function addTrim(x1,z1,x2,z2,by,mk) {
    var dx=x2-x1, dz=z2-z1;
    var len=Math.sqrt(dx*dx+dz*dz);
    if(len<0.3) return;
    var geo=new THREE.BoxGeometry(len, 0.25, 0.5);
    var mesh=new THREE.Mesh(geo, M[mk||'trim']);
    mesh.position.set((x1+x2)/2, by+0.125, (z1+z2)/2);
    mesh.rotation.y=Math.atan2(dx,dz);
    mesh.receiveShadow=true;
    scene.add(mesh);
  }

  // Parapet cap on top of wall (visual only)
  function addParapet(x1,z1,x2,z2,topY) {
    var dx=x2-x1, dz=z2-z1;
    var len=Math.sqrt(dx*dx+dz*dz);
    if(len<0.3) return;
    var geo=new THREE.BoxGeometry(len, 0.3, 0.6);
    var mesh=new THREE.Mesh(geo, M.darkstone);
    mesh.position.set((x1+x2)/2, topY+0.15, (z1+z2)/2);
    mesh.rotation.y=Math.atan2(dx,dz);
    mesh.castShadow=true; mesh.receiveShadow=true;
    scene.add(mesh);
  }

  // Pillar (visual prop)
  function addPillar(x,z,by,h,r,mk) {
    var geo=new THREE.CylinderGeometry(r||0.3, r||0.3, h, 8);
    var mesh=new THREE.Mesh(geo, M[mk||'darkstone']);
    mesh.position.set(x, by+h/2, z);
    mesh.castShadow=true; mesh.receiveShadow=true;
    scene.add(mesh);
    colliders.push({x1:x-(r||0.3),z1:z-(r||0.3),x2:x+(r||0.3),z2:z+(r||0.3),y1:by,y2:by+h});
  }

  // Arch (two pillars + lintel, visual)
  function addArch(x,z,spanX,spanZ,by,h,mk) {
    var hw = spanX/2, hd = spanZ/2;
    if (spanX > spanZ) {
      // arch spanning X
      addBox(x-hw+0.25, by, z, 0.5, h, 0.5, mk||'darkstone');
      addBox(x+hw-0.25, by, z, 0.5, h, 0.5, mk||'darkstone');
      addBox(x, by+h-0.25, z, spanX, 0.5, 0.6, mk||'darkstone', true);
    } else {
      // arch spanning Z
      addBox(x, by, z-hd+0.25, 0.5, h, 0.5, mk||'darkstone');
      addBox(x, by, z+hd-0.25, 0.5, h, 0.5, mk||'darkstone');
      addBox(x, by+h-0.25, z, 0.6, 0.5, spanZ, mk||'darkstone', true);
    }
  }

  // Step-stairs helper (visual steps along Z)
  function addSteps(x1,z1,x2,z2,yBot,yTop,count,mk) {
    var stepH = (yTop - yBot) / count;
    var stepD = (z2 - z1) / count;
    for (var si = 0; si < count; si++) {
      var sz1 = z1 + si * stepD;
      var sz2 = z1 + (si+1) * stepD;
      var sy = yBot + si * stepH;
      var geo = new THREE.BoxGeometry(x2 - x1, stepH, stepD);
      var mesh = new THREE.Mesh(geo, M[mk||'concrete']);
      mesh.position.set((x1+x2)/2, sy + stepH/2, (sz1+sz2)/2);
      mesh.castShadow = true; mesh.receiveShadow = true;
      scene.add(mesh);
    }
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
  addFloor(-44, -2, -36, 2, 0, 'concrete', {ax:'z', a:1.0, b:0});

  // -- B Back --
  addFloor(-44, -28, -26, -18, 0, 'concrete');
  loc('B Back', -44, -28, -26, -18);

  // -- Connector floor patches (gap bridges) --
  addFloor(16, 56, 20, 60, 0, 'dirt');       // T connector to Outside Long
  addFloor(14, 48, 22, 52, 0, 'concrete');   // Outside Long to Long Doors gap
  addFloor(-4, 46, 6, 50, 0, 'concrete');    // Alley to Mid gap
  addFloor(-10, -22, -6, -18, 0, 'concrete'); // CT Spawn to CT Mid west
  addFloor(6, -22, 8, -18, 0, 'concrete');    // CT Spawn to CT-A
  addFloor(-16, 4, -16, 8, 0, 'concrete');    // B Tunnels to B Approach link
  addFloor(-30, -22, -26, -18, 0, 'concrete'); // CT-B to B Halls link

  // ═══════════════════════════════════════════════════════
  //  WALLS
  // ═══════════════════════════════════════════════════════
  var H = 7;
  var HM = 5.5;

  // --- outer boundary ---
  addWall(-48, -42, 36, -42, 0, 16, 'boundary');
  addWall(36, -42, 36, 88, 0, 16, 'boundary');
  addWall(-48, 88, 36, 88, 0, 16, 'boundary');
  addWall(-48, -42, -48, 88, 0, 16, 'boundary');

  // ---- T SPAWN ----
  addWall(-10, 82, 16, 82, 0, H, 'sandstone');
  addWall(16, 72, 16, 82, 0, H, 'sandstone');
  addWall(-10, 68, -10, 82, 0, H, 'sandstone');
  addWall(-10, 65, -4, 65, 0, H, 'sandstone');
  addWall(6, 65, 16, 65, 0, H, 'sandstone');
  addWall(16, 60, 16, 68, 0, H, 'sandstone');

  // T Spawn trim
  addTrim(-10, 82, 16, 82, 0);
  addTrim(16, 72, 16, 82, 0);
  addTrim(-10, 68, -10, 82, 0);
  addParapet(-10, 82, 16, 82, H);
  addParapet(16, 72, 16, 82, H);

  // T Spawn archway at alley entrance
  addArch(1, 65, 10, 1, 0, 5.5, 'darkstone');

  // T Spawn destroyed vehicle (cluster of boxes)
  addBox(8, 0, 70, 3.5, 1.0, 1.8, 'metal');
  addBox(8, 1.0, 70, 2.5, 0.6, 1.4, 'metal', true);
  addBox(9.5, 0, 70, 0.8, 1.6, 1.2, 'metal', true);

  // T Spawn low walls
  addBox(-6, 0, 74, 1.4, 0.5, 2.5, 'darkstone');
  addBox(-6, 0, 70, 4, 0.8, 0.4, 'sandstone');
  addBox(10, 0, 76, 2, 0.8, 1.5, 'wood');
  addBox(12, 0, 76, 1.4, 0.5, 1.4, 'wood');

  // ---- ALLEY (T -> Mid) ----
  addWall(5, 48, 5, 65, 0, HM, 'plaster');
  addWall(-3, 48, -3, 65, 0, HM, 'plaster');
  addTrim(5, 48, 5, 65, 0);
  addTrim(-3, 48, -3, 65, 0);
  addParapet(5, 48, 5, 65, HM);
  addParapet(-3, 48, -3, 65, HM);

  // ---- EAST GATE / OUTSIDE LONG ----
  addWall(18, 68, 30, 68, 0, H, 'sandstone');
  addWall(30, 50, 30, 68, 0, H, 'sandstone');
  addWall(18, 50, 22, 50, 0, H, 'sandstone');
  addWall(18, 56, 18, 68, 0, H, 'sandstone');
  addTrim(30, 50, 30, 68, 0);
  addTrim(18, 56, 18, 68, 0);
  // Wall cap at Long Doors north opening
  addBox(22, 0, 50, 0.5, H, 0.5, 'darkstone');
  addBox(30, 0, 50, 0.5, H, 0.5, 'darkstone');

  // ---- LONG DOORS ----
  addWall(22, 44, 22, 50, 0, HM, 'sandstone');
  addWall(30, 44, 30, 50, 0, HM, 'sandstone');
  // Door frame at z=47
  addBox(23.2, 0, 47, 0.4, 4.5, 0.4, 'darkstone');
  addBox(28.8, 0, 47, 0.4, 4.5, 0.4, 'darkstone');
  addBox(26, 4.5, 47, 6, 0.4, 0.6, 'darkstone', true);
  addBox(24.5, 0, 47, 2.2, 4.2, 0.15, 'wood', true);
  addBox(27.5, 0, 47, 2.2, 4.2, 0.15, 'wood', true);
  // Wall end caps at Long Doors entrance (z=44)
  addBox(22, 0, 44, 0.5, HM, 0.5, 'darkstone');
  addBox(30, 0, 44, 0.5, HM, 0.5, 'darkstone');

  // ---- LONG ALLEY ----
  addWall(30, 4, 30, 44, 0, H, 'sandstone');
  addWall(22, 6, 22, 44, 0, H, 'sandstone');
  addTrim(30, 4, 30, 44, 0);
  addTrim(22, 6, 22, 44, 0);
  addParapet(30, 4, 30, 44, H);
  addParapet(22, 6, 22, 44, H);
  // Barrel and crate in long
  addBox(29, 0, 30, 1.2, 1.4, 1.2, 'darkstone');
  addBox(29, 0, 24, 1, 1.0, 1, 'wood');
  // Blue dumpster near long corner
  addBox(23.5, 0, 8, 2.0, 1.3, 1.2, 'bluemetal');
  addBox(23.5, 1.3, 8, 2.0, 0.15, 1.2, 'metal', true);
  // Alcove indentations in Long walls (visual depth)
  addBox(22.3, 0, 18, 0.4, 3.5, 2.5, 'plaster', true);
  addBox(22.3, 0, 34, 0.4, 3.5, 2.5, 'plaster', true);
  addBox(29.7, 0, 14, 0.4, 3.5, 2.5, 'plaster', true);
  addBox(29.7, 0, 38, 0.4, 3.5, 2.5, 'plaster', true);

  // ---- LONG CORNER ----
  addWall(30, -4, 30, 4, 0, H, 'sandstone');
  addWall(24, -6, 30, -6, 0, H, 'sandstone');
  addWall(16, -4, 16, 6, 0, H, 'plaster');
  addTrim(30, -4, 30, 4, 0);
  addTrim(24, -6, 30, -6, 0);

  // ---- PIT ----
  addWall(20, -1, 20, 5, -1.2, 1.4, 'darkstone');
  addWall(28, -1, 28, 5, -1.2, 1.4, 'darkstone');
  addWall(20, -1, 28, -1, -1.2, 1.4, 'darkstone');

  // ---- A CROSS ----
  addWall(10, -6, 10, 0, 0, H, 'plaster');
  addWall(10, -6, 22, -6, 0, H, 'sandstone');
  addTrim(10, -6, 10, 0, 0);
  addTrim(10, -6, 22, -6, 0);

  // ---- A SITE ----
  addWall(24, -24, 24, -6, 1, H, 'sandstone');
  addWall(2, -24, 24, -24, 1, H, 'sandstone');
  addWall(2, -24, 2, -4, 1, H, 'sandstone');
  addWall(2, -4, 6, -4, 1, H, 'sandstone');
  addTrim(24, -24, 24, -6, 1);
  addTrim(2, -24, 24, -24, 1);
  addTrim(2, -24, 2, -4, 1);
  addParapet(24, -24, 24, -6, 1+H);
  addParapet(2, -24, 24, -24, 1+H);

  // Arch over A ramp entrance (expanded)
  addArch(14, -6, 12, 1, 0, 5, 'darkstone');

  // A Ramp side walls with trim
  addBox(6.2, 0, -6, 0.4, 2.5, 4, 'darkstone', true);
  addBox(21.8, 0, -6, 0.4, 2.5, 4, 'darkstone', true);

  // A site default elevated box position
  addBox(18, 1.0, -16, 2.4, 1.6, 2.4, 'wood');
  addBox(15.5, 1.0, -17, 1.6, 0.9, 1.6, 'wood');
  addBox(6, 1.0, -12, 2.0, 1.2, 2.0, 'wood');
  // Elevated default position (tall stack)
  addBox(18, 2.6, -16, 2.0, 1.0, 2.0, 'wood');

  // A site bombsite marker
  addMarker(14, -14, 1.0, 2.0, 'bombA');

  // ---- CATWALK / A SHORT ----
  addWall(14, 6, 14, 22, 2.2, 2.5, 'plaster');
  addWall(6, 6, 6, 22, 0, 5, 'plaster');
  addWall(6, 22, 14, 22, 0, 5, 'plaster');
  addWall(6, 6, 14, 6, 2.2, 2.5, 'plaster');
  addCeil(6, 6, 14, 22, 2.1, 'concrete');
  addTrim(6, 6, 6, 22, 0);
  addParapet(14, 6, 14, 22, 2.2+2.5);

  // ---- SHORT STAIRS ----
  addWall(4, 18, 4, 26, 0, 5, 'plaster');
  addWall(8, 18, 8, 26, 0, 5, 'plaster');
  addTrim(4, 18, 4, 26, 0);
  addTrim(8, 18, 8, 26, 0);
  // Visual steps on the short stairway
  addSteps(4.2, 18.5, 7.8, 25.5, 0, 2.2, 8, 'concrete');

  // ---- MID ----
  addWall(6, 26, 6, 48, 0, HM, 'plaster');
  addWall(-4, 26, -4, 48, 0, HM, 'plaster');
  addTrim(6, 26, 6, 48, 0);
  addTrim(-4, 26, -4, 48, 0);
  addParapet(6, 26, 6, 48, HM);
  addParapet(-4, 26, -4, 48, HM);

  // Xbox (crate in mid for boosting) with detail
  addBox(1, 0, 24, 2.0, 1.3, 2.0, 'wood');
  addBox(1, 1.3, 24, 1.8, 0.1, 1.8, 'darkstone', true); // lid detail
  // Barrel in mid
  addBox(-2.5, 0, 32, 1.0, 1.2, 1.0, 'darkstone');

  // ---- MID DOORS ----
  addWall(6, 18, 6, 26, 0, HM, 'plaster');
  addWall(-4, 18, -4, 26, 0, HM, 'plaster');
  // Door frame at z=20 (proper structure)
  addBox(-3.8, 0, 20, 0.5, 4.5, 0.6, 'darkstone');
  addBox(5.8, 0, 20, 0.5, 4.5, 0.6, 'darkstone');
  addBox(1, 4.5, 20, 10.2, 0.5, 0.7, 'darkstone', true);
  // Inner frame pillars
  addBox(-2, 0, 20, 0.4, 4, 0.4, 'darkstone');
  addBox(4, 0, 20, 0.4, 4, 0.4, 'darkstone');
  addBox(1, 4, 20, 6.5, 0.4, 0.5, 'darkstone', true);
  // Door panels
  addBox(-0.5, 0, 20, 2.4, 3.6, 0.15, 'wood', true);
  addBox(2.5, 0, 20, 2.4, 3.6, 0.15, 'wood', true);

  // ---- TOP MID ----
  addWall(6, 4, 6, 18, 0, HM, 'plaster');
  addWall(-4, 4, -4, 18, 0, HM, 'plaster');
  addTrim(6, 4, 6, 18, 0);
  addTrim(-4, 4, -4, 18, 0);

  // ---- CT MID ----
  addWall(6, -22, 6, 4, 0, HM, 'plaster');
  addWall(-6, -22, -6, 4, 0, HM, 'plaster');
  addTrim(6, -22, 6, 4, 0);
  addTrim(-6, -22, -6, 4, 0);

  // ---- CT SPAWN ----
  addWall(-12, -38, 16, -38, 0, H, 'sandstone');
  addWall(16, -38, 16, -22, 0, H, 'sandstone');
  addWall(-12, -38, -12, -22, 0, H, 'sandstone');
  addWall(-12, -22, -6, -22, 0, H, 'sandstone');
  addWall(6, -22, 16, -22, 0, H, 'sandstone');
  addTrim(-12, -38, 16, -38, 0);
  addTrim(16, -38, 16, -22, 0);
  addTrim(-12, -38, -12, -22, 0);
  addParapet(-12, -38, 16, -38, H);

  // CT Spawn fountain/well piece (center)
  addBox(0, 0, -30, 2.5, 0.3, 2.5, 'darkstone');
  addBox(0, 0.3, -30, 2.0, 0.7, 2.0, 'darkstone');
  addBox(0, 0, -30, 0.5, 1.4, 0.5, 'concrete');  // center column
  // CT supply crates
  addBox(-8, 0, -34, 2, 0.9, 1.5, 'wood');
  addBox(-8, 0.9, -34, 1.5, 0.7, 1.2, 'wood');
  // CT decorative low wall
  addBox(10, 0, -34, 0.4, 1.0, 6, 'sandstone');

  // ---- CT TO A ----
  addWall(20, -28, 20, -6, 0, H, 'sandstone');
  addWall(8, -28, 8, -10, 0, H, 'sandstone');
  addWall(8, -28, 20, -28, 0, H, 'sandstone');
  addTrim(20, -28, 20, -6, 0);
  addTrim(8, -28, 8, -10, 0);

  // ---- CT TO B ----
  addWall(-30, -38, -12, -38, 0, H, 'sandstone');
  addWall(-30, -22, -10, -22, 0, H, 'sandstone');
  addWall(-30, -38, -30, -22, 0, H, 'sandstone');
  addTrim(-30, -38, -12, -38, 0);
  addTrim(-30, -22, -10, -22, 0);

  // B Halls connector walls
  addWall(-32, -24, -32, -16, 0, H, 'sandstone');
  addWall(-26, -24, -26, -16, 0, H, 'sandstone');
  // Wall caps at B Halls opening
  addBox(-32, 0, -16, 0.5, H, 0.5, 'darkstone');
  addBox(-26, 0, -16, 0.5, H, 0.5, 'darkstone');

  // ---- T RAMP ----
  addWall(-4, 58, -4, 68, 0, HM, 'plaster');
  addWall(-18, 55, -18, 68, -2, 8, 'sandstone');
  addWall(-18, 68, -4, 68, 0, HM, 'plaster');
  addCeil(-18, 58, -4, 68, 4, 'roof');
  addTrim(-4, 58, -4, 68, 0);
  addTrim(-18, 55, -18, 68, -2);

  // ---- LOWER TUNNELS ----
  addWall(-32, 46, -32, 62, -2, 6, 'sandstone');
  addWall(-16, 46, -16, 56, -2, 6, 'sandstone');
  addWall(-32, 62, -16, 62, -2, 6, 'sandstone');
  addCeil(-32, 46, -16, 62, 4, 'roof');
  // Support pillars in lower tunnels
  addPillar(-24, 54, -2, 6, 0.35, 'darkstone');
  addPillar(-24, 49, -2, 6, 0.35, 'darkstone');
  // Connector wall between lower tunnels and T ramp
  addWall(-18, 55, -16, 55, -2, 6, 'sandstone');

  // ---- TUNNEL STAIRS / CONNECTOR ----
  addWall(-32, 36, -32, 46, 0, 6, 'sandstone');
  addWall(-16, 36, -16, 46, 0, 6, 'sandstone');
  addCeil(-32, 36, -16, 46, 5, 'roof');
  // Visual steps for tunnel stairs
  addSteps(-31.5, 36.5, -16.5, 45.5, -2, 0, 6, 'darkstone');
  // Support pillar in stair area
  addPillar(-24, 41, 0, 5, 0.3, 'darkstone');

  // ---- UPPER TUNNELS ----
  addWall(-32, 20, -32, 36, 0, 6, 'sandstone');
  addWall(-16, 20, -16, 36, 0, 6, 'sandstone');
  addCeil(-32, 20, -16, 36, 5.5, 'roof');
  addTrim(-32, 20, -32, 36, 0);
  addTrim(-16, 20, -16, 36, 0);
  // Support pillars
  addPillar(-24, 24, 0, 5.5, 0.35, 'darkstone');
  addPillar(-24, 32, 0, 5.5, 0.35, 'darkstone');
  // Crate in upper tunnels
  addBox(-24, 0, 28, 1.8, 1.2, 1.8, 'wood');

  // ---- B APPROACH ----
  addWall(-28, 8, -28, 20, 0, 6, 'sandstone');
  addWall(-16, 8, -16, 20, 0, 6, 'sandstone');
  addCeil(-28, 8, -16, 20, 5, 'roof');
  // Support pillar
  addPillar(-22, 14, 0, 5, 0.3, 'darkstone');

  // ---- B TUNNELS / B GATE ----
  addWall(-24, 2, -24, 8, 0, 6, 'sandstone');
  addWall(-16, 2, -16, 8, 0, 6, 'sandstone');
  // Door frame at z=4
  addBox(-22.2, 0, 4, 0.4, 4, 0.4, 'darkstone');
  addBox(-17.8, 0, 4, 0.4, 4, 0.4, 'darkstone');
  addBox(-20, 4, 4, 4.8, 0.4, 0.5, 'darkstone', true);
  addBox(-20.8, 0, 4, 2.8, 3.6, 0.15, 'wood', true);
  addBox(-19.2, 0, 4, 2.8, 3.6, 0.15, 'wood', true);
  addCeil(-24, 2, -16, 8, 5, 'roof');
  // Wall end caps at B gate
  addBox(-24, 0, 2, 0.5, 6, 0.5, 'darkstone');
  addBox(-16, 0, 2, 0.5, 6, 0.5, 'darkstone');

  // ---- WINDOW LEDGE ----
  addWall(-28, 2, -28, 10, 0, 6, 'sandstone');
  addWall(-22, 2, -22, 10, 0, 1.2, 'sandstone');
  addWall(-22, 2, -22, 10, 3, 3, 'sandstone');
  addCeil(-28, 2, -22, 10, 5, 'roof');
  addFloor(-28, 10, -22, 14, 0, 'concrete', {ax:'z', a:1.5, b:0});
  // Window frame detail
  addBox(-22, 1.2, 4, 0.15, 1.8, 0.4, 'darkstone', true);
  addBox(-22, 1.2, 8, 0.15, 1.8, 0.4, 'darkstone', true);
  addBox(-22, 3.0, 6, 0.15, 0.3, 8, 'darkstone', true);
  addBox(-22, 1.2, 6, 0.15, 0.3, 8, 'darkstone', true);

  // ---- B SITE ----
  addWall(-44, -18, -44, 4, 0, H, 'sandstone');
  addWall(-44, -18, -16, -18, 0, H, 'sandstone');
  addWall(-16, -18, -16, 2, 0, H, 'sandstone');
  addWall(-44, 4, -24, 4, 0, H, 'sandstone');
  addTrim(-44, -18, -44, 4, 0);
  addTrim(-44, -18, -16, -18, 0);
  addTrim(-16, -18, -16, 2, 0);
  addTrim(-44, 4, -24, 4, 0);
  addParapet(-44, -18, -44, 4, H);
  addParapet(-44, -18, -16, -18, H);

  // B site courtyard enclosure walls (extra)
  addWall(-16, 2, -16, 4, 0, H, 'sandstone');  // seal gap east wall to south
  // Window opening detail in B east wall
  addBox(-16, 2.5, -8, 0.3, 2.0, 3.0, 'plaster', true);  // window frame
  addBox(-16, 4.5, -8, 0.3, 0.2, 3.0, 'darkstone', true);  // window top
  addBox(-16, 2.5, -8, 0.3, 0.2, 3.0, 'darkstone', true);  // window sill

  // Crates at B site
  addBox(-32, 0, -8, 2.4, 1.6, 2.4, 'wood');
  addBox(-30, 0, -6, 1.6, 0.9, 1.6, 'wood');
  // Double-stack crate position
  addBox(-20, 0, -10, 2.0, 2.0, 2.0, 'wood');
  addBox(-20, 2.0, -10, 2.0, 1.2, 2.0, 'wood');
  addBox(-20, 3.2, -10, 1.6, 0.8, 1.6, 'wood');
  // Barrel
  addBox(-38, 0, 2, 1.2, 1.4, 1.2, 'darkstone');
  // B bombsite marker
  addMarker(-30, -6, 0, 2.0, 'bombB');

  // ---- B PLATFORM ----
  addWall(-36, 0, -36, -12, 0, 1.2, 'darkstone');
  addWall(-44, 0, -36, 0, 0, 1.2, 'darkstone');
  addWall(-44, -12, -36, -12, 0, 1.2, 'darkstone');

  // ---- B BACK ----
  addWall(-44, -28, -44, -18, 0, H, 'sandstone');
  addWall(-44, -28, -26, -28, 0, H, 'sandstone');
  addWall(-26, -28, -26, -18, 0, H, 'sandstone');
  addTrim(-44, -28, -44, -18, 0);
  addTrim(-44, -28, -26, -28, 0);

  // ═══════════════════════════════════════════════════════
  //  SPAWN & BOMBSITE MARKERS
  // ═══════════════════════════════════════════════════════

  addMarker(2, 72, 0, 1.5, 'tSpawn');
  addMarker(2, -30, 0, 1.5, 'ctSpawn');

  // ═══════════════════════════════════════════════════════
  //  EXTRA BUILDINGS / SKY STRUCTURES (visual only)
  // ═══════════════════════════════════════════════════════

  function addBuilding(x,z,w,d,h,mk) {
    var geo = new THREE.BoxGeometry(w,h,d);
    var mesh = new THREE.Mesh(geo, M[mk]||M.sandstone);
    mesh.position.set(x, h/2, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    scene.add(mesh);
  }

  addBuilding(36, 25, 8, 30, 12, 'sandstone');
  addBuilding(38, 55, 6, 16, 10, 'sandstone');
  addBuilding(-10, 36, 6, 20, 10, 'plaster');
  addBuilding(14, -30, 18, 8, 9, 'sandstone');
  addBuilding(-48, -8, 4, 20, 11, 'sandstone');
  addBuilding(-48, -24, 5, 10, 9, 'sandstone');
  addBuilding(2, -44, 24, 6, 8, 'plaster');
  addBuilding(-16, 78, 8, 8, 14, 'sandstone');
  addBuilding(34, 72, 6, 12, 11, 'sandstone');

  // ═══════════════════════════════════════════════════════
  //  GROUND PLANE
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
