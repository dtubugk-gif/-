// Renderer, physics world, cel-shaded materials and body helpers.
window.G = window.G || {};

(function () {
  // Phones get a lighter quality profile so the game stays smooth
  const mobile = matchMedia('(pointer: coarse)').matches || /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
  G.mobile = mobile;
  const view = document.getElementById('view');
  const renderer = new THREE.WebGLRenderer({ antialias: !mobile, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, mobile ? 1.5 : 2));
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = mobile ? THREE.PCFShadowMap : THREE.PCFSoftShadowMap;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  view.appendChild(renderer.domElement);

  const scene = new THREE.Scene();
  scene.fog = new THREE.Fog(0xbfe6ff, 110, 330);

  const camera = new THREE.PerspectiveCamera(62, window.innerWidth / window.innerHeight, 0.1, 900);
  camera.position.set(0, 8, 14);

  scene.add(new THREE.HemisphereLight(0xe6f6ff, 0x7aa35a, 1.1));
  const sun = new THREE.DirectionalLight(0xfff0d0, 2.1);
  sun.position.set(40, 70, 25);
  sun.castShadow = true;
  const sh = mobile ? 1024 : 2048, ext = mobile ? 34 : 50;
  sun.shadow.mapSize.set(sh, sh);
  Object.assign(sun.shadow.camera, { left: -ext, right: ext, top: ext, bottom: -ext, near: 1, far: 220 });
  sun.shadow.bias = -0.0006;
  sun.shadow.normalBias = 0.02;
  scene.add(sun, sun.target);

  window.addEventListener('resize', () => {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
  });

  // Sky dome: vertical gradient that fades into the fog colour at the horizon
  const sky = new THREE.Mesh(new THREE.SphereGeometry(800, 32, 16), new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false,
    uniforms: { top: { value: new THREE.Color(0x3d8fe0) }, mid: { value: new THREE.Color(0x8fd3ff) }, bot: { value: new THREE.Color(0xbfe6ff) } },
    vertexShader: 'varying float h; void main(){ h = normalize(position).y; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
    fragmentShader: 'uniform vec3 top; uniform vec3 mid; uniform vec3 bot; varying float h; void main(){ vec3 c = h > 0.15 ? mix(mid, top, smoothstep(0.15, 0.7, h)) : mix(bot, mid, smoothstep(-0.05, 0.15, h)); gl_FragColor = vec4(c, 1.0); }'
  }));
  scene.add(sky);
  G.sky = sky;

  // Physics
  const world = new CANNON.World();
  world.gravity.set(0, -22, 0); // heavier than Earth: snappier, sillier arcs
  world.broadphase = new CANNON.SAPBroadphase(world);
  world.solver.iterations = 10;
  world.allowSleep = true;
  world.defaultContactMaterial.friction = 0.35;
  world.defaultContactMaterial.restitution = 0.25;

  const groundBody = new CANNON.Body({ mass: 0 });
  groundBody.addShape(new CANNON.Plane());
  groundBody.quaternion.setFromAxisAngle(new CANNON.Vec3(1, 0, 0), -Math.PI / 2);
  groundBody.tag = { kind: 'ground' };
  world.addBody(groundBody);

  // Cel shading: a 3-step ramp gives everything the cartoon look
  const ramp = new THREE.DataTexture(new Uint8Array([110, 110, 110, 255, 185, 185, 185, 255, 255, 255, 255, 255]), 3, 1);
  ramp.minFilter = ramp.magFilter = THREE.NearestFilter;
  ramp.needsUpdate = true;
  const mats = {};
  function mat(color, opts) {
    const key = color + JSON.stringify(opts || {});
    if (!mats[key]) {
      const o = Object.assign({}, opts || {});
      delete o.flatShading;
      mats[key] = new THREE.MeshToonMaterial(Object.assign({ color, gradientMap: ramp }, o));
    }
    return mats[key];
  }

  // Inverted-hull outline for characters
  const inkMat = new THREE.MeshBasicMaterial({ color: 0x2b1d3a, side: THREE.BackSide });
  function outline(obj, k) {
    const list = [];
    obj.traverse(o => { if (o.isMesh && !o.userData.noOutline && !o.userData.isOutline) list.push(o); });
    for (const m of list) {
      m.geometry.computeBoundingSphere();
      const r = m.geometry.boundingSphere.radius || 0.3;
      // Small details (eyes, brows, nostrils) skip the outline and the shadow pass: phones thank us
      if (r < 0.13) { m.castShadow = false; continue; }
      const h = new THREE.Mesh(m.geometry, inkMat);
      h.userData.isOutline = true;
      h.scale.setScalar(1 + (k || 0.035) / Math.max(0.08, r));
      h.position.copy(m.geometry.boundingSphere.center).multiplyScalar(1 - h.scale.x);
      m.add(h);
    }
    return obj;
  }

  const dyn = [];  // bodies whose .mesh is synced each frame

  function shade(m) { m.castShadow = m.receiveShadow = true; return m; }
  function boxMesh(w, h, d, color) { return shade(new THREE.Mesh(new THREE.BoxGeometry(w, h, d), typeof color === 'object' ? color : mat(color))); }

  function syncMesh(body) {
    body.mesh.position.set(body.position.x, body.position.y, body.position.z);
    body.mesh.quaternion.set(body.quaternion.x, body.quaternion.y, body.quaternion.z, body.quaternion.w);
  }

  // Static box with arbitrary rotation (quaternion) and optional visible mesh
  function addStaticQ(w, h, d, pos, quat, color, tag) {
    const body = new CANNON.Body({ mass: 0 });
    body.addShape(new CANNON.Box(new CANNON.Vec3(w / 2, h / 2, d / 2)));
    body.position.copy(pos);
    if (quat) body.quaternion.copy(quat);
    body.tag = tag || { kind: 'static' };
    world.addBody(body);
    if (color !== null && color !== undefined) {
      body.mesh = boxMesh(w, h, d, color);
      syncMesh(body);
      scene.add(body.mesh);
    }
    return body;
  }
  function addStatic(w, h, d, x, y, z, color, rotY, tag) {
    let q = null;
    if (rotY) { q = new CANNON.Quaternion(); q.setFromAxisAngle(new CANNON.Vec3(0, 1, 0), rotY); }
    return addStaticQ(w, h, d, new CANNON.Vec3(x, y, z), q, color, tag);
  }
  function tilt(body, ex, ey, ez) {
    body.quaternion.setFromEuler(ex, ey, ez);
    if (body.mesh) syncMesh(body);
    return body;
  }

  function addBox(w, h, d, x, y, z, color, mass, tag, mesh) {
    const body = new CANNON.Body({ mass, linearDamping: 0.05, angularDamping: 0.1 });
    body.addShape(new CANNON.Box(new CANNON.Vec3(w / 2, h / 2, d / 2)));
    body.position.set(x, y, z);
    body.tag = tag || { kind: 'prop', pts: 10 };
    body.sleepSpeedLimit = 0.3;
    world.addBody(body);
    body.mesh = mesh || boxMesh(w, h, d, color);
    scene.add(body.mesh);
    dyn.push(body);
    return body;
  }

  function addSphere(r, x, y, z, color, mass, tag, mesh) {
    const body = new CANNON.Body({ mass, linearDamping: 0.02, angularDamping: 0.05 });
    body.addShape(new CANNON.Sphere(r));
    body.position.set(x, y, z);
    body.tag = tag || { kind: 'prop', pts: 10 };
    world.addBody(body);
    body.mesh = mesh || shade(new THREE.Mesh(new THREE.SphereGeometry(r, 18, 12), mat(color)));
    scene.add(body.mesh);
    dyn.push(body);
    return body;
  }

  function removeBody(body) {
    world.removeBody(body);
    if (body.mesh) scene.remove(body.mesh);
    const i = dyn.indexOf(body);
    if (i >= 0) dyn.splice(i, 1);
  }

  function sync() { for (const b of dyn) syncMesh(b); }

  // Canvas textures, generated once
  function canvasTex(w, h, draw) {
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    draw(c.getContext('2d'), w, h);
    const t = new THREE.CanvasTexture(c);
    t.colorSpace = THREE.SRGBColorSpace;
    t.anisotropy = 4;
    return t;
  }

  Object.assign(G, { renderer, scene, camera, sun, world, mat, outline, dyn, shade, boxMesh, addStatic, addStaticQ, tilt, addBox, addSphere, removeBody, sync, syncMesh, groundBody, canvasTex, ramp });
  G.ringR = 58;
  G.spawn = new CANNON.Vec3(0, 3, 12);
})();
