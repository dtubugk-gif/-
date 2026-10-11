// Townsfolk who panic, cars that drive in circles, golden goats and the jetpack.
(function () {
  const { scene, world, mat, boxMesh } = G;
  const V3 = CANNON.Vec3;
  const shirts = [0xff4fa3, 0x4fc3f7, 0xffd23f, 0x7fe0c8, 0xb48fff, 0xff9f5a];
  const skins = [0xf1c27d, 0xc68642, 0x8d5524, 0xffdbac];

  // ---------- Humans ----------
  function Human(x, z, i) {
    const body = new CANNON.Body({ mass: 60, fixedRotation: true, linearDamping: 0.1, angularDamping: 0.2 });
    body.addShape(new CANNON.Box(new V3(0.35, 0.9, 0.25)));
    body.position.set(x, 1, z);
    body.tag = { kind: 'human' };
    body.human = this;
    body.allowSleep = false;
    world.addBody(body);

    const root = new THREE.Group(), rig = new THREE.Group();
    root.add(rig);
    const skin = skins[i % skins.length];
    const torso = boxMesh(0.7, 0.8, 0.45, shirts[i % shirts.length]); torso.position.y = 0.2;
    const head = boxMesh(0.45, 0.45, 0.45, skin); head.position.y = 0.85;
    const hair = boxMesh(0.48, 0.14, 0.48, [0x3b2417, 0xffd23f, 0x222222, 0xc0392b][i % 4]); hair.position.y = 0.3; head.add(hair);
    for (const sx of [-0.1, 0.1]) { const eye = boxMesh(0.07, 0.07, 0.02, 0x111111); eye.position.set(sx, 0.05, 0.23); head.add(eye); }
    const mouth = boxMesh(0.14, 0.05, 0.02, 0x7a1f1f); mouth.position.set(0, -0.1, 0.23); head.add(mouth);
    const limb = (w, h, c, px, py) => { const g = new THREE.Group(); const m = boxMesh(w, h, w, c); m.position.y = -h / 2; g.add(m); g.position.set(px, py, 0); rig.add(g); return g; };
    this.armL = limb(0.2, 0.7, skin, -0.47, 0.55);
    this.armR = limb(0.2, 0.7, skin, 0.47, 0.55);
    this.legL = limb(0.25, 0.75, 0x2b3a67, -0.18, -0.2);
    this.legR = limb(0.25, 0.75, 0x2b3a67, 0.18, -0.2);
    rig.add(torso, head);
    this.head = head; this.mouth = mouth;
    scene.add(root);
    body.mesh = root;
    G.dyn.push(body);

    Object.assign(this, { body, root, rig, state: 'walk', t: 0, face: Math.random() * 6, anim: Math.random() * 10, home: new V3(x, 0, z), target: null, downT: 0 });
    this.pickTarget();
  }
  Human.prototype.pickTarget = function () {
    const a = Math.random() * Math.PI * 2, r = 4 + Math.random() * 18;
    this.target = new V3(this.home.x + Math.cos(a) * r, 0, this.home.z + Math.sin(a) * r);
  };
  Human.prototype.knock = function (silent) {
    if (this.state === 'down') { this.downT = Math.max(this.downT, 2); return; }
    this.state = 'down';
    this.downT = 4 + Math.random() * 3;
    this.body.fixedRotation = false;
    this.body.updateMassProperties();
    this.body.angularVelocity.set((Math.random() - 0.5) * 12, (Math.random() - 0.5) * 6, (Math.random() - 0.5) * 12);
    G.sfx.scream();
    if (!silent) G.addScore(['RUDE', 'BULLY', 'ASSAULT ON PEDESTRIAN', 'NOT SORRY'][Math.floor(Math.random() * 4)], 150, 'pink');
    else if (this.body.position.distanceTo(G.goat.body.position) < 30) G.addScore('COLLATERAL', 100);
  };
  Human.prototype.update = function (dt) {
    const b = this.body, goat = G.goat.body.position;
    this.anim += dt;
    if (this.state === 'down') {
      // flail like a dropped action figure
      const f = Math.sin(this.anim * 22) * 1.4;
      this.armL.rotation.x = f; this.armR.rotation.x = -f; this.legL.rotation.x = -f * 0.6; this.legR.rotation.x = f * 0.6;
      this.mouth.scale.set(1, 3, 1);
      this.downT -= dt;
      if (this.downT <= 0 && b.velocity.length() < 2) {
        // pop back up like nothing happened
        b.fixedRotation = true; b.updateMassProperties();
        b.quaternion.set(0, 0, 0, 1); b.angularVelocity.set(0, 0, 0);
        b.position.y = Math.max(b.position.y, 1.05);
        this.state = 'panic'; this.t = 4; this.mouth.scale.set(1, 1, 1);
      }
      if (b.position.y < -20) { b.position.set(this.home.x, 2, this.home.z); b.velocity.set(0, 0, 0); }
      return;
    }
    const dx = b.position.x - goat.x, dz = b.position.z - goat.z;
    const dGoat = Math.hypot(dx, dz);
    if (dGoat < 7 && this.state === 'walk') { this.state = 'panic'; this.t = 3 + Math.random() * 2; if (Math.random() < 0.5) G.sfx.scream(); }
    let vx = 0, vz = 0, speed;
    if (this.state === 'panic') {
      this.t -= dt;
      speed = 6.5;
      vx = dx / (dGoat || 1); vz = dz / (dGoat || 1);
      // zig-zag sprint, arms in the air
      const wob = Math.sin(this.anim * 7) * 0.6;
      [vx, vz] = [vx * Math.cos(wob) - vz * Math.sin(wob), vx * Math.sin(wob) + vz * Math.cos(wob)];
      this.armL.rotation.x = Math.PI + Math.sin(this.anim * 25) * 0.4;
      this.armR.rotation.x = Math.PI + Math.cos(this.anim * 25) * 0.4;
      if (this.t <= 0 && dGoat > 10) { this.state = 'walk'; this.pickTarget(); }
    } else {
      speed = 1.8;
      const tx = this.target.x - b.position.x, tz = this.target.z - b.position.z, d = Math.hypot(tx, tz);
      if (d < 1) this.pickTarget();
      vx = tx / (d || 1); vz = tz / (d || 1);
      this.armL.rotation.x = Math.sin(this.anim * 6) * 0.5;
      this.armR.rotation.x = -Math.sin(this.anim * 6) * 0.5;
    }
    b.velocity.x += (vx * speed - b.velocity.x) * Math.min(1, dt * 6);
    b.velocity.z += (vz * speed - b.velocity.z) * Math.min(1, dt * 6);
    const sw = Math.sin(this.anim * speed * 3.2) * 0.7;
    this.legL.rotation.x = sw; this.legR.rotation.x = -sw;
    const want = Math.atan2(vx, vz);
    let dA = want - this.face; dA = Math.atan2(Math.sin(dA), Math.cos(dA));
    this.face += dA * Math.min(1, dt * 8);
    this.rig.rotation.y = this.face;
    if (b.position.y < -20) { b.position.set(this.home.x, 2, this.home.z); b.velocity.set(0, 0, 0); }
  };

  const spots = [[4, 6], [-8, -4], [10, -10], [-16, 12], [20, 16], [-24, -18], [36, -8], [-38, 0], [6, 30], [-4, -26], [46, 46], [-50, -50]];
  spots.forEach((p, i) => {
    const h = new Human(p[0], p[1], i);
    h.body.addEventListener('collide', e => {
      if (e.body === G.groundBody || h.state === 'down') return;
      if (G.impactSpeed(e) > 6) G.after.push(() => h.knock(e.body !== G.goat?.body));
    });
    G.humans.push(h);
  });

  // ---------- Cars ----------
  const carColors = [0xe53935, 0x1e88e5, 0xfdd835];
  function Car(a0, i) {
    const body = new CANNON.Body({ mass: 900, linearDamping: 0.05, angularDamping: 0.3 });
    body.addShape(new CANNON.Box(new V3(1.1, 0.55, 2.2)), new V3(0, 0.15, 0));
    body.addShape(new CANNON.Box(new V3(0.95, 0.4, 1.1)), new V3(0, 1.05, -0.2));
    body.tag = { kind: 'car' };
    body.car = this;
    body.allowSleep = false;
    const R = G.ringR + (i % 2 ? 2.5 : -2.5);
    body.position.set(Math.cos(a0) * R, 0.9, Math.sin(a0) * R);
    world.addBody(body);
    const g = new THREE.Group();
    const shell = boxMesh(2.2, 1.1, 4.4, carColors[i % 3]); shell.position.y = 0.15;
    const cab = boxMesh(1.9, 0.8, 2.2, 0xdff4ff); cab.position.set(0, 1.05, -0.2);
    g.add(shell, cab);
    for (const [wx, wz] of [[-1.15, 1.4], [1.15, 1.4], [-1.15, -1.4], [1.15, -1.4]]) {
      const w = new THREE.Mesh(new THREE.CylinderGeometry(0.45, 0.45, 0.3, 12), mat(0x222222));
      w.rotation.z = Math.PI / 2; w.position.set(wx, -0.35, wz); g.add(w);
    }
    for (const lx of [-0.6, 0.6]) { const l = boxMesh(0.4, 0.25, 0.05, 0xfff7e0); l.position.set(lx, 0.3, 2.21); g.add(l); }
    g.traverse(o => { o.castShadow = true; });
    scene.add(g);
    body.mesh = g;
    G.dyn.push(body);
    Object.assign(this, { body, a: a0, R, dir: i % 2 ? 1 : -1, speed: 13 + i * 2, wrecked: false, wreckT: 0 });
    body.addEventListener('collide', e => {
      if (e.body === G.groundBody) return;
      if (e.body === G.goat?.body && !this.wrecked) {
        G.after.push(() => {
          const gb = G.goat.body, v = this.body.velocity;
          gb.velocity.set(v.x * 1.8 + (Math.random() - 0.5) * 10, 18 + Math.random() * 8, v.z * 1.8 + (Math.random() - 0.5) * 10);
          G.goat.ragdoll(3);
          G.sfx.honk(); G.sfx.bonk(10);
          G.addScore('HIT AND RUN', 400, 'big');
        });
      } else if (G.impactSpeed(e) > 14) G.after.push(() => this.wreck());
    });
  }
  Car.prototype.wreck = function () {
    if (this.wrecked) return;
    this.wrecked = true; this.wreckT = 15;
    if (this.body.position.distanceTo(G.goat.body.position) < 30) G.addScore('TOTALLED', 350);
  };
  Car.prototype.update = function (dt) {
    const b = this.body;
    if (this.wrecked) {
      this.wreckT -= dt;
      if (this.wreckT <= 0) {
        this.wrecked = false;
        b.position.set(Math.cos(this.a) * this.R, 2, Math.sin(this.a) * this.R);
        b.velocity.set(0, 0, 0); b.angularVelocity.set(0, 0, 0);
      }
      return;
    }
    // honk at goats on the road and keep driving anyway
    const gp = G.goat.body.position;
    if (Math.hypot(gp.x - b.position.x, gp.z - b.position.z) < 10) G.sfx.honk();
    this.a += this.dir * (this.speed / this.R) * dt;
    const tx = Math.cos(this.a) * this.R, tz = Math.sin(this.a) * this.R;
    const tanX = -Math.sin(this.a) * this.dir, tanZ = Math.cos(this.a) * this.dir;
    b.velocity.x = tanX * this.speed + (tx - b.position.x) * 2;
    b.velocity.z = tanZ * this.speed + (tz - b.position.z) * 2;
    if (b.position.y > 1.6 || Math.hypot(tx - b.position.x, tz - b.position.z) > 6) { this.wreck(); return; }
    b.quaternion.setFromAxisAngle(new V3(0, 1, 0), Math.atan2(tanX, tanZ));
    b.angularVelocity.set(0, 0, 0);
  };
  [0, 2.1, 4.2].forEach((a, i) => G.cars.push(new Car(a, i)));

  // ---------- Golden goats ----------
  const gold = new THREE.MeshLambertMaterial({ color: 0xffc400, emissive: 0x6b4a00 });
  function statueMesh() {
    const g = new THREE.Group();
    const add = (w, h, d, x, y, z) => { const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), gold); m.position.set(x, y, z); m.castShadow = true; g.add(m); };
    add(0.5, 0.4, 0.9, 0, 0, 0); add(0.3, 0.35, 0.35, 0, 0.3, 0.5);
    for (const [x, z] of [[-0.18, 0.3], [0.18, 0.3], [-0.18, -0.3], [0.18, -0.3]]) add(0.1, 0.4, 0.1, x, -0.35, z);
    add(0.06, 0.25, 0.06, -0.1, 0.6, 0.45); add(0.06, 0.25, 0.06, 0.1, 0.6, 0.45);
    g.scale.setScalar(1.6);
    return g;
  }
  const statueSpots = [[50, 26, -62], [0, 4.6, 0], [70, 1.6, 60], [-55, 10.5, -43], [-62, 1.6, 41], [22, 7.6, -6]];
  statueSpots.forEach((p, i) => {
    const m = statueMesh();
    m.position.set(p[0], p[1], p[2]);
    const glow = new THREE.PointLight(0xffd23f, 2, 7);
    m.add(glow);
    scene.add(m);
    G.statues.push({ m, p: new THREE.Vector3(p[0], p[1], p[2]), got: false, i });
  });

  // ---------- Jetpack pickup ----------
  const jp = new THREE.Group();
  const tank = (x) => { const t = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.25, 0.9, 10), mat(0x9aa5b8)); t.position.x = x; t.castShadow = true; jp.add(t); };
  tank(-0.28); tank(0.28);
  const nose = new THREE.Mesh(new THREE.ConeGeometry(0.25, 0.4, 10), mat(0xe53935)); nose.position.set(-0.28, 0.65, 0); jp.add(nose);
  const nose2 = nose.clone(); nose2.position.x = 0.28; jp.add(nose2);
  jp.position.set(50, 13.5, -62);
  scene.add(jp);
  G.jetpackPickup = jp;

  G.updatePeople = function (dt, time) {
    for (const h of G.humans) h.update(dt);
    for (const c of G.cars) c.update(dt);
    for (const s of G.statues) {
      if (s.got) continue;
      s.m.rotation.y = time * 1.5;
      s.m.position.y = s.p.y + Math.sin(time * 2 + s.i) * 0.25;
    }
    if (jp.visible) { jp.rotation.y = time * 2; jp.position.y = 13.5 + Math.sin(time * 3) * 0.2; }
  };
})();
