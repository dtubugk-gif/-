// Townsfolk who panic, cars that drive in circles, golden goats and the jetpack.
(function () {
  const { scene, world, mat, shade, boxMesh, outline } = G;
  const V3 = CANNON.Vec3;
  const shirts = [0xff4fa3, 0x4fc3f7, 0xffd23f, 0x7fe0c8, 0xb48fff, 0xff9f5a, 0xffffff];
  const pants = [0x2b3a67, 0x4a4458, 0x6b4a2b, 0x2e7d5b];
  const skins = [0xf1c27d, 0xc68642, 0x8d5524, 0xffdbac, 0xe0ac69];
  const hairs = [0x3b2417, 0xffd23f, 0x222222, 0xc0392b, 0xdddddd];
  const cap = (r, l, c) => shade(new THREE.Mesh(new THREE.CapsuleGeometry(r, l, 4, 10), mat(c)));
  const ball = (r, c) => shade(new THREE.Mesh(new THREE.SphereGeometry(r, 14, 10), mat(c)));

  // ---------- Humans ----------
  function buildHuman(i) {
    const rig = new THREE.Group();
    const skin = skins[i % skins.length], shirt = shirts[i % shirts.length], pant = pants[i % pants.length];
    const torso = cap(0.3, 0.45, shirt); torso.position.y = 0.28; torso.scale.z = 0.75; rig.add(torso);
    if (i % 3 === 0) { const tie = boxMesh(0.1, 0.45, 0.04, 0xe53935); tie.position.set(0, 0.35, 0.23); rig.add(tie); }
    const belt = shade(new THREE.Mesh(new THREE.CylinderGeometry(0.31, 0.31, 0.1, 14), mat(0x2b1d3a))); belt.position.y = -0.05; belt.scale.z = 0.75; rig.add(belt);
    const head = new THREE.Group(); head.position.y = 0.98; rig.add(head);
    const skull = ball(0.3, skin); head.add(skull);
    const nose = ball(0.07, skin); nose.position.set(0, -0.02, 0.3); head.add(nose);
    const eyes = [];
    for (const s of [-1, 1]) {
      const e = ball(0.075, 0xffffff); e.position.set(s * 0.11, 0.07, 0.25); head.add(e);
      const p = ball(0.04, 0x111111); p.position.z = 0.05; e.add(p); eyes.push(e);
      const brow = boxMesh(0.13, 0.03, 0.03, hairs[i % hairs.length]); brow.position.set(s * 0.11, 0.18, 0.27); brow.rotation.z = s * -0.15; head.add(brow);
    }
    const mouth = boxMesh(0.14, 0.04, 0.04, 0x7a1f1f); mouth.position.set(0, -0.14, 0.27); head.add(mouth);
    const hc = hairs[i % hairs.length], style = i % 5;
    if (style === 0) { const h = ball(0.31, hc); h.scale.y = 0.6; h.position.y = 0.1; head.add(h); }
    if (style === 1) for (let k = 0; k < 6; k++) { const s = shade(new THREE.Mesh(new THREE.ConeGeometry(0.08, 0.3, 5), mat(hc))); s.position.set(Math.cos(k) * 0.15, 0.3, Math.sin(k) * 0.12 - 0.04); s.rotation.z = Math.cos(k) * -0.5; head.add(s); }
    if (style === 2) { const c = shade(new THREE.Mesh(new THREE.CylinderGeometry(0.31, 0.31, 0.16, 14), mat(shirts[(i + 2) % shirts.length]))); c.position.y = 0.22; head.add(c); const brim = boxMesh(0.36, 0.04, 0.25, shirts[(i + 2) % shirts.length]); brim.position.set(0, 0.16, 0.3); head.add(brim); }
    if (style === 3) { const t = shade(new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.22, 0.5, 14), mat(0x2b1d3a))); t.position.y = 0.45; head.add(t); const b = shade(new THREE.Mesh(new THREE.CylinderGeometry(0.4, 0.4, 0.04, 14), mat(0x2b1d3a))); b.position.y = 0.22; head.add(b); }
    if (style === 4) { const bun = ball(0.14, hc); bun.position.set(0, 0.28, -0.15); head.add(bun); const h = ball(0.31, hc); h.scale.y = 0.55; h.position.y = 0.1; head.add(h); }
    if (i % 4 === 1) { const gl = boxMesh(0.42, 0.04, 0.04, 0x2b1d3a); gl.position.set(0, 0.08, 0.31); head.add(gl); }
    const limb = (c, px, py, len, r, end, endC) => {
      const g = new THREE.Group(); g.position.set(px, py, 0);
      const m = cap(r, len, c); m.position.y = -len / 2 - r * 0.5; g.add(m);
      const e = end(endC); e.position.y = -len - r * 1.2; g.add(e);
      rig.add(g); return g;
    };
    const hand = c => ball(0.1, c), shoe = c => { const s = boxMesh(0.2, 0.12, 0.32, c); s.position.z = 0.06; return s; };
    const parts = {
      armL: limb(shirt, -0.4, 0.55, 0.5, 0.09, hand, skin),
      armR: limb(shirt, 0.4, 0.55, 0.5, 0.09, hand, skin),
      legL: limb(pant, -0.15, -0.08, 0.6, 0.11, shoe, 0x2b1d3a),
      legR: limb(pant, 0.15, -0.08, 0.6, 0.11, shoe, 0x2b1d3a)
    };
    outline(rig, 0.025);
    G.bakeRig(rig, [rig, head, parts.armL, parts.armR, parts.legL, parts.legR], [mouth, ...eyes]);
    return Object.assign(parts, { rig, head, mouth, eyes });
  }

  function Human(x, z, i) {
    const body = new CANNON.Body({ mass: 60, fixedRotation: true, linearDamping: 0.1, angularDamping: 0.2 });
    body.addShape(new CANNON.Box(new V3(0.35, 0.9, 0.25)));
    body.position.set(x, 1, z);
    body.tag = { kind: 'human' };
    body.human = this;
    body.allowSleep = false;
    world.addBody(body);
    const root = new THREE.Group(), m = buildHuman(i);
    root.add(m.rig);
    scene.add(root);
    body.mesh = root;
    G.dyn.push(body);
    Object.assign(this, m, { body, root, state: 'walk', t: 0, face: Math.random() * 6, anim: Math.random() * 10, home: new V3(x, 0, z), target: null, downT: 0 });
    this.pickTarget();
  }
  Human.prototype.pickTarget = function () {
    const a = Math.random() * Math.PI * 2, r = 4 + Math.random() * 18;
    this.target = new V3(this.home.x + Math.cos(a) * r, 0, this.home.z + Math.sin(a) * r);
  };
  Human.prototype.scare = function () {
    if (this.state !== 'walk') return;
    this.state = 'panic'; this.t = 3 + Math.random() * 2;
    if (Math.random() < 0.5) G.sfx.scream(this.body.position);
  };
  Human.prototype.knock = function (silent) {
    if (this.state === 'down') { this.downT = Math.max(this.downT, 2); return; }
    this.state = 'down';
    this.downT = 4 + Math.random() * 3;
    this.body.fixedRotation = false;
    this.body.updateMassProperties();
    this.body.angularVelocity.set((Math.random() - 0.5) * 12, (Math.random() - 0.5) * 6, (Math.random() - 0.5) * 12);
    G.sfx.scream(this.body.position, true);
    const near = this.body.position.distanceTo(G.goat.body.position) < 30;
    if (near) G.stats.peopleDown++;
    if (!silent) G.addScore(['RUDE', 'BULLY', 'ASSAULT ON PEDESTRIAN', 'NOT SORRY', 'GOAT RAGE'][Math.floor(Math.random() * 5)], 150, 'pink');
    else if (near) G.addScore('COLLATERAL', 100);
  };
  Human.prototype.update = function (dt) {
    const b = this.body, goat = G.goat.body.position;
    this.anim += dt;
    if (b.position.y < -20) { b.position.set(this.home.x, 2, this.home.z); b.velocity.set(0, 0, 0); }
    if (this.state === 'down') {
      // flail like a dropped action figure
      const f = Math.sin(this.anim * 22) * 1.4;
      this.armL.rotation.x = f; this.armR.rotation.x = -f; this.legL.rotation.x = -f * 0.6; this.legR.rotation.x = f * 0.6;
      this.armL.rotation.z = -1; this.armR.rotation.z = 1;
      this.mouth.scale.set(1.2, 4, 1);
      for (const e of this.eyes) e.scale.setScalar(1.4);
      this.downT -= dt;
      if (this.downT <= 0 && b.velocity.length() < 2) {
        // pop back up like nothing happened
        b.fixedRotation = true; b.updateMassProperties();
        b.quaternion.set(0, 0, 0, 1); b.angularVelocity.set(0, 0, 0);
        b.position.y = Math.max(b.position.y, 1.05);
        this.state = 'panic'; this.t = 4;
        this.armL.rotation.z = this.armR.rotation.z = 0;
      }
      return;
    }
    const dx = b.position.x - goat.x, dz = b.position.z - goat.z;
    const dGoat = Math.hypot(dx, dz);
    if (dGoat < 7) this.scare();
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
      this.mouth.scale.set(1, 3 + Math.sin(this.anim * 30), 1);
      for (const e of this.eyes) e.scale.setScalar(1.3);
      if (this.t <= 0 && dGoat > 10) { this.state = 'walk'; this.pickTarget(); }
    } else {
      speed = 1.8;
      const tx = this.target.x - b.position.x, tz = this.target.z - b.position.z, d = Math.hypot(tx, tz);
      if (d < 1) this.pickTarget();
      vx = tx / (d || 1); vz = tz / (d || 1);
      this.armL.rotation.x = Math.sin(this.anim * 6) * 0.5;
      this.armR.rotation.x = -Math.sin(this.anim * 6) * 0.5;
      this.mouth.scale.set(1, 1, 1);
      for (const e of this.eyes) e.scale.setScalar(1);
    }
    b.velocity.x += (vx * speed - b.velocity.x) * Math.min(1, dt * 6);
    b.velocity.z += (vz * speed - b.velocity.z) * Math.min(1, dt * 6);
    const sw = Math.sin(this.anim * speed * 3.2) * 0.7;
    this.legL.rotation.x = sw; this.legR.rotation.x = -sw;
    this.rig.position.y = Math.abs(Math.sin(this.anim * speed * 3.2)) * 0.06;
    const want = Math.atan2(vx, vz);
    let dA = want - this.face; dA = Math.atan2(Math.sin(dA), Math.cos(dA));
    this.face += dA * Math.min(1, dt * 8);
    this.rig.rotation.y = this.face;
    this.head.rotation.y = Math.sin(this.anim * 0.7) * 0.4;
  };

  const spots = [[4, 6], [-8, -4], [10, -10], [-16, 12], [20, 16], [-24, -18], [36, -8], [-38, 0], [6, 30], [-4, -26], [46, 46], [-50, -50]];
  const crowd = G.mobile ? spots.slice(0, 10) : spots;
  crowd.forEach((p, i) => {
    const h = new Human(p[0], p[1], i);
    h.body.addEventListener('collide', e => {
      if (e.body === G.groundBody || h.state === 'down') return;
      if (G.impactSpeed(e) > 6) G.after.push(() => h.knock(!(e.body === G.goat?.body || e.body.goatPart)));
    });
    G.humans.push(h);
  });

  // ---------- Cars ----------
  const carColors = [0xe53935, 0x1e88e5, 0xfdd835, 0x43a047];
  function buildCar(i) {
    const g = new THREE.Group();
    const s = new THREE.Shape();
    [[-2.2, -0.45], [2.2, -0.45], [2.25, 0.1], [1.95, 0.35], [0.9, 0.45], [0.35, 1.05], [-1.2, 1.05], [-1.75, 0.45], [-2.25, 0.35]].forEach((p, k) => k ? s.lineTo(p[0], p[1]) : s.moveTo(p[0], p[1]));
    s.closePath();
    const geo = new THREE.ExtrudeGeometry(s, { depth: 2.0, bevelEnabled: true, bevelSize: 0.08, bevelThickness: 0.08, bevelSegments: 2 });
    geo.translate(0, 0, -1.0); geo.rotateY(-Math.PI / 2);
    const shell = shade(new THREE.Mesh(geo, mat(carColors[i % carColors.length])));
    g.add(shell);
    const win = mat(0x2b3a67, { emissive: 0x1d2a4a });
    for (const sx of [-1.1, 1.1]) { const w = new THREE.Mesh(new THREE.BoxGeometry(0.04, 0.45, 1.4), win); w.position.set(sx, 0.72, -0.35); g.add(w); }
    const ws = new THREE.Mesh(new THREE.BoxGeometry(1.85, 0.62, 0.04), win); ws.position.set(0, 0.74, 0.64); ws.rotation.x = -0.82; g.add(ws);
    const bw = new THREE.Mesh(new THREE.BoxGeometry(1.85, 0.5, 0.04), win); bw.position.set(0, 0.74, -1.48); bw.rotation.x = 0.85; g.add(bw);
    for (const lx of [-0.65, 0.65]) {
      const l = new THREE.Mesh(new THREE.BoxGeometry(0.45, 0.2, 0.06), mat(0xfff7c0, { emissive: 0xffe680 })); l.position.set(lx, 0.05, 2.26); g.add(l);
      const t = new THREE.Mesh(new THREE.BoxGeometry(0.4, 0.18, 0.06), mat(0xff3030, { emissive: 0x801010 })); t.position.set(lx, 0.1, -2.28); g.add(t);
    }
    const bumper = boxMesh(2.1, 0.18, 0.15, 0xdddddd); bumper.position.set(0, -0.32, 2.3); g.add(bumper);
    const bumper2 = bumper.clone(); bumper2.position.z = -2.3; g.add(bumper2);
    if (i === 2) { const sign = boxMesh(0.8, 0.3, 0.25, 0xffffff); sign.position.set(0, 1.25, -0.4); g.add(sign); }
    // Details: grille, plates, mirrors, door seams and handles, wheel arches, exhaust, per-car extras
    const dark = 0x2b1d3a, chrome = 0xcfd6e0, body = carColors[i % carColors.length];
    const grille = boxMesh(1.1, 0.22, 0.06, dark); grille.position.set(0, -0.1, 2.27); g.add(grille);
    for (let k = -2; k <= 2; k++) { const bar = boxMesh(0.04, 0.2, 0.08, chrome); bar.position.set(k * 0.2, -0.1, 2.28); g.add(bar); }
    for (const z of [2.4, -2.4]) { const plate = boxMesh(0.5, 0.16, 0.04, 0xfff7e0); plate.position.set(0, -0.3, z); g.add(plate); }
    for (const sx of [-1, 1]) {
      const mirror = boxMesh(0.24, 0.14, 0.1, body); mirror.position.set(sx * 1.2, 0.5, 0.45); g.add(mirror);
      for (const z of [0.25, -0.95]) { const seam = boxMesh(0.02, 0.55, 0.03, dark); seam.position.set(sx * 1.09, 0.05, z); g.add(seam); }
      for (const z of [-0.05, -1.25]) { const handle = boxMesh(0.04, 0.05, 0.2, chrome); handle.position.set(sx * 1.1, 0.22, z); g.add(handle); }
      for (const wz of [1.4, -1.4]) {
        const arch = new THREE.Mesh(new THREE.TorusGeometry(0.52, 0.07, 5, 12, Math.PI), mat(dark));
        arch.position.set(sx * 1.08, -0.35, wz); arch.rotation.y = Math.PI / 2; g.add(arch);
      }
    }
    const pipe = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.06, 0.3, 8), mat(chrome)); pipe.rotation.x = Math.PI / 2; pipe.position.set(0.6, -0.38, -2.35); g.add(pipe);
    if (i === 0) { // spoiler
      const wing = boxMesh(1.9, 0.07, 0.42, dark); wing.position.set(0, 0.72, -2.05); g.add(wing);
      for (const sx of [-0.7, 0.7]) { const post = boxMesh(0.07, 0.3, 0.1, dark); post.position.set(sx, 0.55, -2.05); g.add(post); }
    }
    if (i === 1) { // roof rack with a surfboard
      for (const sx of [-0.6, 0.6]) { const rail = boxMesh(0.06, 0.06, 1.7, dark); rail.position.set(sx, 1.12, -0.35); g.add(rail); }
      const board = new THREE.Mesh(new THREE.CapsuleGeometry(0.22, 1.5, 4, 8), mat(0xffd23f)); board.rotation.z = Math.PI / 2; board.rotation.y = Math.PI / 2; board.scale.set(1, 1, 0.25); board.position.set(0.85, 1.25, -0.35); g.add(board);
    }
    if (i === 2) { // taxi checkers
      for (const sx of [-1, 1]) for (let k = 0; k < 8; k++) { const c = boxMesh(0.03, 0.12, 0.25, k % 2 ? dark : 0xffffff); c.position.set(sx * 1.1, 0.42, 1.0 - k * 0.27); g.add(c); }
    }
    const wheels = [];
    for (const [wx, wz] of [[-1.05, 1.4], [1.05, 1.4], [-1.05, -1.4], [1.05, -1.4]]) {
      const w = new THREE.Group(); w.position.set(wx, -0.35, wz);
      const tire = shade(new THREE.Mesh(new THREE.CylinderGeometry(0.45, 0.45, 0.32, 16), mat(0x222222))); tire.rotation.z = Math.PI / 2; w.add(tire);
      const hubc = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.22, 0.34, 6), mat(0xcfd6e0)); hubc.rotation.z = Math.PI / 2; w.add(hubc);
      g.add(w); wheels.push(w);
    }
    outline(shell, 0.04);
    G.bakeRig(g, [g, ...wheels], []);
    return { g, wheels };
  }
  // spot: {x, z, yaw} for a car that waits parked instead of driving the ring road.
  function Car(a0, i, spot) {
    const body = new CANNON.Body({ mass: 900, linearDamping: 0.05, angularDamping: 0.3 });
    body.addShape(new CANNON.Box(new V3(1.1, 0.55, 2.25)), new V3(0, -0.25, 0));
    body.addShape(new CANNON.Box(new V3(0.95, 0.3, 1.0)), new V3(0, 0.75, -0.35));
    body.tag = { kind: 'car' };
    body.car = this;
    body.allowSleep = false;
    const R = G.ringR + (i % 2 ? 2.5 : -2.5);
    body.position.set(Math.cos(a0) * R, 0.9, Math.sin(a0) * R);
    world.addBody(body);
    const m = buildCar(i);
    scene.add(m.g);
    body.mesh = m.g;
    G.dyn.push(body);
    Object.assign(this, { body, wheels: m.wheels, a: a0, R, dir: i % 2 ? 1 : -1, speed: 13 + i * 2, wrecked: false, wreckT: 0, spot: spot || null });
    if (spot) this.toSpot();
    body.addEventListener('collide', e => {
      if (e.body === G.groundBody) return;
      if ((e.body === G.goat?.body || e.body.goatPart) && !this.wrecked) {
        G.after.push(() => {
          const v = this.body.velocity;
          G.goat.fling(new V3(v.x * 1.8 + (Math.random() - 0.5) * 10, 18 + Math.random() * 8, v.z * 1.8 + (Math.random() - 0.5) * 10));
          G.goat.ragdoll(3);
          G.sfx.honk(this.body.position); G.sfx.hit(18, 'metal', G.goat.body.position); G.sfx.baa(G.goat.body.position);
          G.addScore('HIT AND RUN', 400, 'big');
        });
      } else if (G.impactSpeed(e) > (this.driven ? (e.body.mass > 0 && e.body.mass < 400 ? 999 : 19) : 14)) G.after.push(() => this.wreck());
    });
  }
  Car.prototype.wreck = function () {
    if (this.wrecked) return;
    this.wrecked = true; this.wreckT = 15;
    G.sfx.crash?.(this.body.position);
    if (this.body.position.distanceTo(G.goat.body.position) < 30) { G.stats.carsWrecked++; G.addScore('TOTALLED', 350); }
  };
  Car.prototype.toSpot = function () {
    const b = this.body, s = this.spot;
    b.position.set(s.x, 0.9, s.z); b.velocity.set(0, 0, 0); b.angularVelocity.set(0, 0, 0);
    b.quaternion.setFromAxisAngle(new V3(0, 1, 0), s.yaw);
  };
  Car.prototype.update = function (dt) {
    const b = this.body;
    if (this.driven) return; // the goat has the wheel
    if (this.spot) {         // parked car: waits for the goat, goes home when abandoned out of sight
      if (this.wrecked) { this.wreckT -= dt; if (this.wreckT <= 0) { this.wrecked = false; this.toSpot(); } return; }
      if (this.parkedT > 0) {
        this.parkedT -= dt;
        if (this.parkedT <= 0) { if (b.position.distanceTo(G.goat.body.position) > 25) this.toSpot(); else this.parkedT = 3; }
      }
      return;
    }
    if (this.parkedT > 0) {   // abandoned by the goat: sits there, then a tow truck (offscreen) returns it
      this.parkedT -= dt;
      const gp = G.goat.body.position;
      if (this.parkedT <= 0 && b.position.distanceTo(gp) > 25) {
        b.position.set(Math.cos(this.a) * this.R, 2, Math.sin(this.a) * this.R);
        b.velocity.set(0, 0, 0); b.angularVelocity.set(0, 0, 0);
      } else if (this.parkedT <= 0) this.parkedT = 3;
      return;
    }
    if (this.wrecked) {
      this.wreckT -= dt;
      if (this.wreckT <= 0) {
        this.wrecked = false;
        b.position.set(Math.cos(this.a) * this.R, 2, Math.sin(this.a) * this.R);
        b.velocity.set(0, 0, 0); b.angularVelocity.set(0, 0, 0);
      }
      return;
    }
    const gp = G.goat.body.position;
    if (Math.hypot(gp.x - b.position.x, gp.z - b.position.z) < 10) G.sfx.honk(b.position);
    this.a += this.dir * (this.speed / this.R) * dt;
    const tx = Math.cos(this.a) * this.R, tz = Math.sin(this.a) * this.R;
    const tanX = -Math.sin(this.a) * this.dir, tanZ = Math.cos(this.a) * this.dir;
    b.velocity.x = tanX * this.speed + (tx - b.position.x) * 2;
    b.velocity.z = tanZ * this.speed + (tz - b.position.z) * 2;
    if (b.position.y > 1.6 || Math.hypot(tx - b.position.x, tz - b.position.z) > 6) { this.wreck(); return; }
    b.quaternion.setFromAxisAngle(new V3(0, 1, 0), Math.atan2(tanX, tanZ));
    b.angularVelocity.set(0, 0, 0);
    for (const w of this.wheels) w.rotation.x += this.speed / 0.45 * dt;
  };
  [0, 2.1, 4.2].forEach((a, i) => G.cars.push(new Car(a, i)));
  // A green car parked right next to where you start, ready to steal.
  G.cars.push(new Car(0, 3, { x: 5.5, z: 22, yaw: Math.PI }));

  // ---------- Golden goats (statues built from the goat model) ----------
  const statueSpots = [[50, 26, -62], [0, 4.9, 0], [70, 1.6, 60], [-55, 10.5, -43], [-62, 1.6, 41], [22, 7.6, -6]];
  statueSpots.forEach((p, i) => {
    const m = G.makeGoatStatue();
    m.position.set(p[0], p[1], p[2]);
    const glow = new THREE.PointLight(0xffd23f, 6, 8);
    m.add(glow);
    scene.add(m);
    G.statues.push({ m, p: new THREE.Vector3(p[0], p[1], p[2]), got: false, i });
  });

  // ---------- Jetpack pickup ----------
  const jp = G.makeJetpack();
  jp.scale.setScalar(1.6);
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
