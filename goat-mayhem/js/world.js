// Renderer, physics world and the static town layout.
window.G = window.G || {};

(function () {
  const view = document.getElementById('view');
  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  view.appendChild(renderer.domElement);

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x8fd3ff);
  scene.fog = new THREE.Fog(0x8fd3ff, 90, 260);

  const camera = new THREE.PerspectiveCamera(65, window.innerWidth / window.innerHeight, 0.1, 600);
  camera.position.set(0, 8, 14);

  scene.add(new THREE.HemisphereLight(0xdff4ff, 0x6a8f4a, 0.85));
  const sun = new THREE.DirectionalLight(0xfff1cc, 1.15);
  sun.position.set(40, 70, 25);
  sun.castShadow = true;
  sun.shadow.mapSize.set(2048, 2048);
  Object.assign(sun.shadow.camera, { left: -45, right: 45, top: 45, bottom: -45, near: 1, far: 200 });
  sun.shadow.bias = -0.0005;
  scene.add(sun, sun.target);

  window.addEventListener('resize', () => {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
  });

  // Physics
  const world = new CANNON.World();
  world.gravity.set(0, -22, 0); // heavier than Earth: snappier, sillier arcs
  world.broadphase = new CANNON.SAPBroadphase(world);
  world.solver.iterations = 8;
  world.allowSleep = true;
  world.defaultContactMaterial.friction = 0.35;
  world.defaultContactMaterial.restitution = 0.25;

  const groundBody = new CANNON.Body({ mass: 0 });
  groundBody.addShape(new CANNON.Plane());
  groundBody.quaternion.setFromAxisAngle(new CANNON.Vec3(1, 0, 0), -Math.PI / 2);
  groundBody.tag = { kind: 'ground' };
  world.addBody(groundBody);

  const mats = {};
  function mat(color, opts) {
    const key = color + JSON.stringify(opts || {});
    if (!mats[key]) mats[key] = new THREE.MeshLambertMaterial(Object.assign({ color }, opts || {}));
    return mats[key];
  }

  const dyn = [];  // {body, mesh} pairs synced each frame

  function boxMesh(w, h, d, color) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat(color));
    m.castShadow = m.receiveShadow = true;
    return m;
  }

  function addStatic(w, h, d, x, y, z, color, rotY, tag) {
    const body = new CANNON.Body({ mass: 0 });
    body.addShape(new CANNON.Box(new CANNON.Vec3(w / 2, h / 2, d / 2)));
    body.position.set(x, y, z);
    if (rotY) body.quaternion.setFromAxisAngle(new CANNON.Vec3(0, 1, 0), rotY);
    body.tag = tag || { kind: 'static' };
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
    body.tag = tag || { kind: 'prop', pts: 10 };
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
    body.tag = tag || { kind: 'prop', pts: 10 };
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
      b.mesh.position.set(b.position.x, b.position.y, b.position.z);
      b.mesh.quaternion.set(b.quaternion.x, b.quaternion.y, b.quaternion.z, b.quaternion.w);
    }
  }

  Object.assign(G, { renderer, scene, camera, sun, world, mat, dyn, boxMesh, addStatic, addBox, addSphere, removeBody, sync, groundBody });

  // ---------- Town ----------
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(600, 600), mat(0x5cc85a));
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  scene.add(ground);

  function flat(w, d, x, z, color, y) {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(w, d), mat(color));
    m.rotation.x = -Math.PI / 2;
    m.position.set(x, y || 0.02, z);
    m.receiveShadow = true;
    scene.add(m);
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
  flat(30, 30, 0, 0, 0xe8d9b5, 0.035); // plaza
  G.ringR = ringR;

  // Houses
  const wallColors = [0xff8fa3, 0x8fb8ff, 0xffd23f, 0xb48fff, 0x7fe0c8, 0xff9f5a];
  function house(x, z, w, d, h, rot, i) {
    const body = addStatic(w, h, d, x, h / 2, z, wallColors[i % wallColors.length], rot, { kind: 'house' });
    const roof = new THREE.Mesh(new THREE.ConeGeometry(Math.max(w, d) * 0.78, h * 0.7, 4), mat(0xc0392b, { flatShading: true }));
    roof.position.set(x, h + h * 0.35, z);
    roof.rotation.y = Math.PI / 4 + (rot || 0);
    roof.castShadow = true;
    scene.add(roof);
    // flat physics lid so you can stand on the roof (and fall off it)
    addStatic(w * 0.9, 0.3, d * 0.9, x, h + 0.15, z, null, rot);
    const door = new THREE.Mesh(new THREE.PlaneGeometry(1.4, 2.4), mat(0x6b3e26));
    door.position.set(0, -h / 2 + 1.2, d / 2 + 0.02);
    body.mesh.add(door);
    for (const sx of [-w / 4, w / 4]) {
      const win = new THREE.Mesh(new THREE.PlaneGeometry(1.2, 1.2), mat(0xdff4ff));
      win.position.set(sx, h / 4, d / 2 + 0.02);
      body.mesh.add(win);
    }
    return body;
  }
  const houses = [
    [-28, -28, 9, 8, 7, 0.3], [28, -30, 10, 9, 9, -0.2], [-32, 22, 8, 10, 6, 0.1],
    [34, 26, 11, 8, 8, 0.4], [-14, -42, 8, 7, 6, 0], [16, 44, 9, 9, 10, 0.6],
    [-85, 10, 12, 12, 14, 0], [85, -15, 10, 14, 8, 0.2]
  ];
  houses.forEach((h, i) => house(h[0], h[1], h[2], h[3], h[4], h[5], i));

  // Trees
  function tree(x, z, s) {
    addStatic(0.8 * s, 4 * s, 0.8 * s, x, 2 * s, z, 0x7a4b2a);
    const top = new THREE.Mesh(new THREE.IcosahedronGeometry(2.6 * s, 0), mat(0x2f9e44, { flatShading: true }));
    top.position.set(x, 5 * s, z);
    top.castShadow = true;
    scene.add(top);
  }
  for (let i = 0; i < 46; i++) {
    const a = i * 2.39996, r = 75 + (i * 37 % 60);
    tree(Math.cos(a) * r, Math.sin(a) * r, 0.8 + (i % 5) * 0.15);
  }
  [[-12, 18], [14, -16], [-46, -6], [44, 4]].forEach(p => tree(p[0], p[1], 1));

  // Fountain in the plaza
  addStatic(7, 1, 7, 0, 0.5, 0, 0xb8c2d6);
  addStatic(1.2, 3, 1.2, 0, 2, 0, 0xdfe6f3);
  flat(6, 6, 0, 0, 0x4fc3f7, 1.02);

  // Big launch ramp
  function tilt(body, ex, ey, ez) {
    body.quaternion.setFromEuler(ex, ey, ez);
    body.mesh.quaternion.set(body.quaternion.x, body.quaternion.y, body.quaternion.z, body.quaternion.w);
    return body;
  }
  tilt(addStatic(8, 1, 22, -55, 4.2, -60, 0xff9f5a, 0, { kind: 'ramp' }), -0.42, 0, 0);
  tilt(addStatic(7, 1, 16, 46, 2.6, 4, 0xff9f5a, 0, { kind: 'ramp' }), -0.33, 0, 0);
  G.tilt = tilt;

  // Scaffold tower (climb with trampolines, golden goat on top)
  const tx = 50, tz = -62;
  for (const [ox, oz] of [[-3, -3], [3, -3], [-3, 3], [3, 3]]) addStatic(0.6, 24, 0.6, tx + ox, 12, tz + oz, 0x9aa5b8);
  addStatic(8, 0.6, 8, tx, 12, tz, 0xc9a26b);
  addStatic(8, 0.6, 8, tx, 24.3, tz, 0xc9a26b);

  // Little pond of "water" that does nothing physically, like real game water
  flat(22, 14, 70, 60, 0x4fc3f7, 0.05);
  G.spawn = new CANNON.Vec3(0, 3, 12);
})();
