// Renderer, quality tiers, physics world, shared materials and the static town.
window.G = window.G || {};

(function () {
  const view = document.getElementById('view');

  // ---------- Quality tier: phones get fewer pixels, smaller shadows, fewer trees ----------
  const isTouch = matchMedia('(pointer: coarse)').matches;
  const smallScreen = Math.max(screen.width, screen.height) < 1000;
  const cores = navigator.hardwareConcurrency || 4;
  const mem = navigator.deviceMemory || 4;
  let tier = 'high';
  if (isTouch || smallScreen) tier = 'mid';
  if ((isTouch && (cores <= 4 || mem <= 3)) || /Android [4-8]\b/.test(navigator.userAgent)) tier = 'low';
  try { const forced = localStorage.getItem('goat.tier'); if (forced) tier = forced; } catch (_) { /* ignore */ }
  const Q = G.quality = {
    tier,
    pixelRatio: tier === 'high' ? Math.min(devicePixelRatio, 2) : tier === 'mid' ? Math.min(devicePixelRatio, 1.5) : 1,
    shadowSize: tier === 'high' ? 2048 : tier === 'mid' ? 1024 : 512,
    shadows: tier !== 'low',
    aa: tier === 'high',
    trees: tier === 'high' ? 46 : tier === 'mid' ? 30 : 18,
    particles: tier === 'high' ? 160 : tier === 'mid' ? 90 : 45,
    farFog: tier === 'high' ? 260 : 190
  };

  const renderer = new THREE.WebGLRenderer({ antialias: Q.aa, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Q.pixelRatio);
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.shadowMap.enabled = Q.shadows;
  renderer.shadowMap.type = THREE.PCFShadowMap;
  view.appendChild(renderer.domElement);

  const scene = new THREE.Scene();
  const skyCol = new THREE.Color(0x8fd3ff);
  scene.background = skyCol;
  scene.fog = new THREE.Fog(0xbfe6ff, 70, Q.farFog);

  const camera = new THREE.PerspectiveCamera(65, window.innerWidth / window.innerHeight, 0.1, 600);
  camera.position.set(0, 8, 14);

  scene.add(new THREE.HemisphereLight(0xdff4ff, 0x7aa65a, 0.9));
  const sun = new THREE.DirectionalLight(0xfff1cc, 1.2);
  sun.position.set(40, 70, 25);
  sun.castShadow = Q.shadows;
  sun.shadow.mapSize.set(Q.shadowSize, Q.shadowSize);
  Object.assign(sun.shadow.camera, { left: -42, right: 42, top: 42, bottom: -42, near: 1, far: 200 });
  sun.shadow.bias = -0.0006;
  sun.shadow.normalBias = 0.02;
  scene.add(sun, sun.target);

  function resize() {
    const w = window.innerWidth, h = window.innerHeight;
    camera.aspect = w / h;
    camera.fov = h > w ? 78 : 65; // portrait: wider lens so you can see what you are about to hit
    camera.updateProjectionMatrix();
    renderer.setSize(w, h);
  }
  window.addEventListener('resize', resize);
  window.addEventListener('orientationchange', () => setTimeout(resize, 120));

  // ---------- Toon look: 3-band gradient shared by every material ----------
  const grad = new THREE.DataTexture(new Uint8Array([90, 170, 255]), 3, 1, THREE.RedFormat);
  grad.minFilter = grad.magFilter = THREE.NearestFilter;
  grad.needsUpdate = true;
  const mats = {};
  function mat(color, opts) {
    const key = color + JSON.stringify(opts || {});
    if (!mats[key]) {
      const o = Object.assign({ color, gradientMap: grad }, opts || {});
      if (o.flatShading) { delete o.gradientMap; mats[key] = new THREE.MeshLambertMaterial(o); }
      else mats[key] = new THREE.MeshToonMaterial(o);
    }
    return mats[key];
  }
  const geos = {};
  function boxGeo(w, h, d) {
    const k = w + ',' + h + ',' + d;
    return geos[k] || (geos[k] = new THREE.BoxGeometry(w, h, d));
  }
  // Rounded box for the "soft toy" silhouette on characters and cars.
  function softBox(w, h, d, r) {
    const k = 's' + w + ',' + h + ',' + d + ',' + r;
    if (geos[k]) return geos[k];
    const g = new THREE.BoxGeometry(w, h, d, 2, 2, 2);
    const p = g.attributes.position, hw = w / 2 - r, hh = h / 2 - r, hd = d / 2 - r;
    const v = new THREE.Vector3();
    for (let i = 0; i < p.count; i++) {
      v.fromBufferAttribute(p, i);
      const cx = Math.max(-hw, Math.min(hw, v.x)), cy = Math.max(-hh, Math.min(hh, v.y)), cz = Math.max(-hd, Math.min(hd, v.z));
      const d2 = v.clone().sub(new THREE.Vector3(cx, cy, cz));
      if (d2.length() > 1e-6) d2.setLength(r);
      p.setXYZ(i, cx + d2.x, cy + d2.y, cz + d2.z);
    }
    g.computeVertexNormals();
    return (geos[k] = g);
  }

  // ---------- Physics ----------
  const world = new CANNON.World();
  world.gravity.set(0, -22, 0); // heavier than Earth: snappier, sillier arcs
  world.broadphase = new CANNON.SAPBroadphase(world);
  world.solver.iterations = tier === 'low' ? 6 : 8;
  world.allowSleep = true;
  world.defaultContactMaterial.friction = 0.35;
  world.defaultContactMaterial.restitution = 0.25;

  const groundBody = new CANNON.Body({ mass: 0 });
  groundBody.addShape(new CANNON.Plane());
  groundBody.quaternion.setFromAxisAngle(new CANNON.Vec3(1, 0, 0), -Math.PI / 2);
  groundBody.tag = { kind: 'ground', mat: 'thud' };
  world.addBody(groundBody);

  const dyn = [];  // {body, mesh} pairs synced each frame

  function boxMesh(w, h, d, color, soft) {
    const m = new THREE.Mesh(soft ? softBox(w, h, d, soft) : boxGeo(w, h, d), mat(color));
    m.castShadow = m.receiveShadow = true;
    return m;
  }

  function addStatic(w, h, d, x, y, z, color, rotY, tag) {
    const body = new CANNON.Body({ mass: 0 });
    body.addShape(new CANNON.Box(new CANNON.Vec3(w / 2, h / 2, d / 2)));
    body.position.set(x, y, z);
    if (rotY) body.quaternion.setFromAxisAngle(new CANNON.Vec3(0, 1, 0), rotY);
    body.tag = tag || { kind: 'static', mat: 'wood' };
    world.addBody(body);
    if (color !== null) {
      const m = boxMesh(w, h, d, color);
      m.position.set(x, y, z);
      m.quaternion.set(body.quaternion.x, body.quaternion.y, body.quaternion.z, body.quaternion.w);
      scene.add(m);
      body.mesh = m;
    }
    return body;
  }

  function addBox(w, h, d, x, y, z, color, mass, tag, mesh) {
    const body = new CANNON.Body({ mass, linearDamping: 0.05, angularDamping: 0.1 });
    body.addShape(new CANNON.Box(new CANNON.Vec3(w / 2, h / 2, d / 2)));
    body.position.set(x, y, z);
    body.tag = tag || { kind: 'prop', pts: 10, mat: 'wood' };
    body.sleepSpeedLimit = 0.3;
    world.addBody(body);
    const m = mesh || boxMesh(w, h, d, color);
    scene.add(m);
    body.mesh = m;
    dyn.push(body);
    return body;
  }

  function addSphere(r, x, y, z, color, mass, tag) {
    const body = new CANNON.Body({ mass, linearDamping: 0.02, angularDamping: 0.05 });
    body.addShape(new CANNON.Sphere(r));
    body.position.set(x, y, z);
    body.tag = tag || { kind: 'prop', pts: 10, mat: 'rubber' };
    world.addBody(body);
    const m = new THREE.Mesh(new THREE.IcosahedronGeometry(r, 2), mat(color, { flatShading: true }));
    m.castShadow = true;
    scene.add(m);
    body.mesh = m;
    dyn.push(body);
    return body;
  }

  function removeBody(body) {
    world.removeBody(body);
    if (body.mesh) scene.remove(body.mesh);
    const i = dyn.indexOf(body);
    if (i >= 0) dyn.splice(i, 1);
  }

  function sync() {
    for (const b of dyn) {
      if (b.sleepState === CANNON.Body.SLEEPING && b.synced) continue;
      b.mesh.position.set(b.position.x, b.position.y, b.position.z);
      b.mesh.quaternion.set(b.quaternion.x, b.quaternion.y, b.quaternion.z, b.quaternion.w);
      b.synced = b.sleepState === CANNON.Body.SLEEPING;
    }
  }

  Object.assign(G, { renderer, scene, camera, sun, world, mat, dyn, boxMesh, softBox, boxGeo, addStatic, addBox, addSphere, removeBody, sync, groundBody, isTouch });

  // ---------- Ground with a painted grass texture ----------
  const gc = document.createElement('canvas'); gc.width = gc.height = 256;
  const g2 = gc.getContext('2d');
  g2.fillStyle = '#5cc85a'; g2.fillRect(0, 0, 256, 256);
  for (let i = 0; i < 900; i++) {
    g2.fillStyle = ['#55bd53', '#66d262', '#4fb14e', '#72d96c'][i % 4];
    g2.fillRect(Math.random() * 256, Math.random() * 256, 3 + Math.random() * 9, 2 + Math.random() * 4);
  }
  const grassTex = new THREE.CanvasTexture(gc);
  grassTex.wrapS = grassTex.wrapT = THREE.RepeatWrapping;
  grassTex.repeat.set(60, 60);
  grassTex.colorSpace = THREE.SRGBColorSpace;
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(600, 600), new THREE.MeshToonMaterial({ map: grassTex, gradientMap: grad }));
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  scene.add(ground);

  function flat(w, d, x, z, color, y) {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(w, d), mat(color));
    m.rotation.x = -Math.PI / 2;
    m.position.set(x, y || 0.02, z);
    m.receiveShadow = true;
    scene.add(m);
    return m;
  }
  // Roads: a ring road the cars loop on plus a cross street.
  const ringR = 58;
  const ring = new THREE.Mesh(new THREE.RingGeometry(ringR - 5, ringR + 5, 96), mat(0x4a4458));
  ring.rotation.x = -Math.PI / 2; ring.position.y = 0.03; ring.receiveShadow = true;
  scene.add(ring);
  const dash = new THREE.Mesh(new THREE.RingGeometry(ringR - 0.25, ringR + 0.25, 96), mat(0xffd23f));
  dash.rotation.x = -Math.PI / 2; dash.position.y = 0.04;
  scene.add(dash);
  flat(8, 110, 0, 0, 0x4a4458, 0.025);
  for (let i = -52; i < 52; i += 6) flat(0.4, 3, 0, i, 0xfff7e0, 0.03);
  flat(30, 30, 0, 0, 0xe8d9b5, 0.035); // plaza
  for (let i = 0; i < 12; i++) flat(2.4, 2.4, -13 + (i % 6) * 5.2, i < 6 ? -13 : 13, 0xd9c6a0, 0.04);
  G.ringR = ringR;

  // ---------- Clouds ----------
  const clouds = [];
  const cloudMat = new THREE.MeshToonMaterial({ color: 0xffffff, gradientMap: grad, fog: false });
  for (let i = 0; i < (tier === 'low' ? 5 : 9); i++) {
    const c = new THREE.Group();
    for (let k = 0; k < 4; k++) {
      const m = new THREE.Mesh(new THREE.IcosahedronGeometry(4 + Math.random() * 4, 1), cloudMat);
      m.position.set(k * 5 - 7, Math.random() * 2, Math.random() * 3);
      m.scale.y = 0.6; c.add(m);
    }
    const a = Math.random() * Math.PI * 2, r = 80 + Math.random() * 120;
    c.position.set(Math.cos(a) * r, 55 + Math.random() * 25, Math.sin(a) * r);
    scene.add(c); clouds.push(c);
  }
  G.updateClouds = dt => { for (const c of clouds) { c.position.x += dt * 1.2; if (c.position.x > 240) c.position.x = -240; } };

  // ---------- Houses ----------
  const wallColors = [0xff8fa3, 0x8fb8ff, 0xffd23f, 0xb48fff, 0x7fe0c8, 0xff9f5a, 0xf6e7c1, 0xa5d6a7];
  const roofColors = [0xc0392b, 0x6d4c41, 0x37474f, 0xd35400];
  function house(x, z, w, d, h, rot, i) {
    const body = addStatic(w, h, d, x, h / 2, z, wallColors[i % wallColors.length], rot, { kind: 'house', mat: 'wood' });
    const m = body.mesh;
    const roof = new THREE.Mesh(new THREE.ConeGeometry(Math.max(w, d) * 0.8, h * 0.65, 4), mat(roofColors[i % 4], { flatShading: true }));
    roof.position.set(0, h / 2 + h * 0.32, 0); roof.rotation.y = Math.PI / 4; roof.castShadow = true; m.add(roof);
    const chimney = boxMesh(0.8, 1.6, 0.8, 0x8d6e63); chimney.position.set(w * 0.3, h / 2 + h * 0.3, -d * 0.2); m.add(chimney);
    // flat physics lid so you can stand on the roof (and fall off it)
    addStatic(w * 0.9, 0.3, d * 0.9, x, h + 0.15, z, null, rot);
    const door = boxMesh(1.4, 2.4, 0.12, 0x6b3e26); door.position.set(0, -h / 2 + 1.2, d / 2 + 0.04); m.add(door);
    const knob = new THREE.Mesh(new THREE.SphereGeometry(0.08, 6, 6), mat(0xffd23f)); knob.position.set(0.45, -h / 2 + 1.1, d / 2 + 0.12); m.add(knob);
    const step = boxMesh(2, 0.3, 0.8, 0xb8c2d6); step.position.set(0, -h / 2 + 0.15, d / 2 + 0.4); m.add(step);
    const winGeo = boxGeo(1.3, 1.3, 0.1), frameGeo = boxGeo(1.5, 1.5, 0.08);
    for (const face of [[0, 0, d / 2 + 0.04, 0], [0, 0, -d / 2 - 0.04, Math.PI], [w / 2 + 0.04, 0, 0, Math.PI / 2], [-w / 2 - 0.04, 0, 0, -Math.PI / 2]]) {
      const across = face[3] === 0 || face[3] === Math.PI ? w : d;
      for (const sx of [-across / 4, across / 4]) {
        const g = new THREE.Group();
        const fr = new THREE.Mesh(frameGeo, mat(0xfff7e0)), win = new THREE.Mesh(winGeo, mat(0x9adcff));
        win.position.z = 0.03; g.add(fr, win);
        const shade = boxMesh(1.7, 0.25, 0.5, roofColors[i % 4]); shade.position.set(0, 0.85, 0.2); g.add(shade);
        g.rotation.y = face[3];
        const off = new THREE.Vector3(sx, h / 6, 0).applyAxisAngle(new THREE.Vector3(0, 1, 0), face[3]);
        g.position.set(face[0] + off.x, h / 6, face[2] + off.z);
        m.add(g);
      }
    }
    return body;
  }
  const houses = [
    [-28, -28, 9, 8, 7, 0.3], [28, -30, 10, 9, 9, -0.2], [-32, 22, 8, 10, 6, 0.1],
    [34, 26, 11, 8, 8, 0.4], [-14, -42, 8, 7, 6, 0], [16, 44, 9, 9, 10, 0.6],
    [-85, 10, 12, 12, 14, 0], [85, -15, 10, 14, 8, 0.2], [-48, -58, 9, 9, 7, 0.8], [62, 48, 8, 8, 6, -0.5]
  ];
  houses.forEach((h, i) => house(h[0], h[1], h[2], h[3], h[4], h[5], i));

  // Shop with striped awning (lickable, obviously)
  (function shop() {
    const body = addStatic(12, 6, 8, -16, 3, -14, 0xfff7e0, 0, { kind: 'house', mat: 'wood' });
    const m = body.mesh;
    const sign = boxMesh(9, 1.2, 0.3, 0xff4fa3); sign.position.set(0, 2.2, 4.1); m.add(sign);
    for (let i = 0; i < 6; i++) { const s = boxMesh(1.6, 0.2, 2.2, i % 2 ? 0xff4fa3 : 0xfff7e0); s.position.set(-4 + i * 1.6, 0.9, 5.1); s.rotation.x = 0.25; m.add(s); }
    const glass = boxMesh(8, 2.4, 0.1, 0x9adcff); glass.position.set(0, -0.6, 4.05); m.add(glass);
    addStatic(10, 0.3, 2.4, -16, 3.9, -9, null); // awning top you can land on
  })();

  // ---------- Trees ----------
  const leafColors = [0x2f9e44, 0x3fb950, 0x239a3b, 0x56c45c];
  function tree(x, z, s) {
    addStatic(0.8 * s, 4 * s, 0.8 * s, x, 2 * s, z, 0x7a4b2a, 0, { kind: 'static', mat: 'wood' });
    const g = new THREE.Group();
    for (let k = 0; k < 3; k++) {
      const top = new THREE.Mesh(new THREE.IcosahedronGeometry((2.6 - k * 0.5) * s, 0), mat(leafColors[(k + Math.abs(Math.round(x))) % 4], { flatShading: true }));
      top.position.set(k === 1 ? 1.1 * s : k === 2 ? -0.9 * s : 0, (4.3 + k * 1.3) * s, k === 1 ? 0.6 * s : 0);
      top.castShadow = true; g.add(top);
    }
    g.position.set(x, 0, z); scene.add(g);
  }
  for (let i = 0; i < Q.trees; i++) {
    const a = i * 2.39996, r = 75 + (i * 37 % 60);
    tree(Math.cos(a) * r, Math.sin(a) * r, 0.8 + (i % 5) * 0.15);
  }
  [[-12, 18], [14, -16], [-46, -6], [44, 4], [8, -36], [-30, 36]].forEach(p => tree(p[0], p[1], 1));

  // Street lamps and benches around the plaza
  for (const [x, z] of [[-16, -16], [16, -16], [-16, 16], [16, 16]]) {
    addStatic(0.3, 5, 0.3, x, 2.5, z, 0x37474f, 0, { kind: 'static', mat: 'metal' });
    const lamp = new THREE.Mesh(new THREE.SphereGeometry(0.5, 8, 6), new THREE.MeshBasicMaterial({ color: 0xfff1a8 }));
    lamp.position.set(x, 5.2, z); scene.add(lamp);
  }

  // Fountain in the plaza
  addStatic(7, 1, 7, 0, 0.5, 0, 0xb8c2d6, 0, { kind: 'static', mat: 'metal' });
  addStatic(1.2, 3, 1.2, 0, 2, 0, 0xdfe6f3, 0, { kind: 'static', mat: 'metal' });
  const water = flat(6, 6, 0, 0, 0x4fc3f7, 1.02);
  G.water = water;

  // Big launch ramps
  function tilt(body, ex, ey, ez) {
    body.quaternion.setFromEuler(ex, ey, ez);
    body.mesh.quaternion.set(body.quaternion.x, body.quaternion.y, body.quaternion.z, body.quaternion.w);
    return body;
  }
  const rampTag = { kind: 'ramp', mat: 'wood' };
  tilt(addStatic(8, 1, 22, -55, 4.2, -60, 0xff9f5a, 0, rampTag), -0.42, 0, 0);
  tilt(addStatic(7, 1, 16, 46, 2.6, 4, 0xff9f5a, 0, rampTag), -0.33, 0, 0);
  tilt(addStatic(7, 1, 16, -4, 2.6, 60, 0xff9f5a, Math.PI, rampTag), -0.33, Math.PI, 0);
  for (const b of [[-55, 7.9, -51.5], [46, 5.1, 10.3], [-4, 5.1, 53.7]]) {
    for (const s of [-1, 1]) { const st = boxMesh(0.3, 0.3, 1, 0xff4fa3); st.position.set(b[0] + s * 3.4, b[1], b[2]); scene.add(st); }
  }
  G.tilt = tilt;

  // Scaffold tower (climb with trampolines, golden goat on top)
  const tx = 50, tz = -62;
  for (const [ox, oz] of [[-3, -3], [3, -3], [-3, 3], [3, 3]]) addStatic(0.6, 24, 0.6, tx + ox, 12, tz + oz, 0x9aa5b8, 0, { kind: 'static', mat: 'metal' });
  addStatic(8, 0.6, 8, tx, 12, tz, 0xc9a26b);
  addStatic(8, 0.6, 8, tx, 24.3, tz, 0xc9a26b);
  for (let y = 4; y < 24; y += 4) addStatic(0.3, 0.3, 6, tx - 3, y, tz, 0x9aa5b8, 0, { kind: 'static', mat: 'metal' });

  // Pond: decorative, like real game water
  const pond = flat(22, 14, 70, 60, 0x4fc3f7, 0.05);
  G.pond = pond;
  G.spawn = new CANNON.Vec3(0, 3, 12);
})();
