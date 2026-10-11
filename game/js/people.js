// Townsfolk who panic, cars that drive in circles, a cop who hunts you, pigeons, golden goats and the jetpack.
(function () {
  const { scene, world, mat, boxMesh, softBox, boxGeo } = G;
  const V3 = CANNON.Vec3;
  const shirts = [0xff4fa3, 0x4fc3f7, 0xffd23f, 0x7fe0c8, 0xb48fff, 0xff9f5a, 0xffffff, 0x2b3a67];
  const pants = [0x2b3a67, 0x37474f, 0x6d4c41, 0x8d6e63];
  const skins = [0xf1c27d, 0xc68642, 0x8d5524, 0xffdbac, 0xe0ac69];
  const hairs = [0x3b2417, 0xffd23f, 0x222222, 0xc0392b, 0xd7ccc8];
  G.wanted = 0;

  // ---------- Humans ----------
  function Human(x, z, i) {
    const body = new CANNON.Body({ mass: 60, fixedRotation: true, linearDamping: 0.1, angularDamping: 0.2 });
    body.addShape(new CANNON.Box(new V3(0.35, 0.9, 0.25)));
    body.position.set(x, 1, z);
    body.tag = { kind: 'human', mat: 'flesh' };
    body.human = this;
    body.allowSleep = false;
    world.addBody(body);

    const root = new THREE.Group(), rig = new THREE.Group();
    root.add(rig);
    const skin = skins[i % skins.length], shirt = shirts[i % shirts.length], pant = pants[i % pants.length];
    const torso = new THREE.Mesh(softBox(0.72, 0.8, 0.46, 0.12), mat(shirt)); torso.position.y = 0.2; torso.castShadow = true;
    const headG = new THREE.Group(); headG.position.y = 0.88;
    const head = new THREE.Mesh(softBox(0.46, 0.46, 0.46, 0.14), mat(skin)); head.castShadow = true; headG.add(head);
    const hairStyle = i % 4;
    const hairC = hairs[i % hairs.length];
    if (hairStyle === 0) { const h = new THREE.Mesh(softBox(0.5, 0.18, 0.5, 0.06), mat(hairC)); h.position.y = 0.26; headG.add(h); }
    else if (hairStyle === 1) { const h = new THREE.Mesh(new THREE.SphereGeometry(0.3, 10, 8), mat(hairC)); h.position.set(0, 0.2, -0.04); h.scale.y = 0.75; headG.add(h); }
    else if (hairStyle === 2) { // cap
      const c = new THREE.Mesh(new THREE.SphereGeometry(0.27, 10, 8, 0, Math.PI * 2, 0, Math.PI / 2), mat(shirts[(i + 3) % shirts.length])); c.position.y = 0.2; headG.add(c);
      const peak = boxMesh(0.34, 0.05, 0.3, shirts[(i + 3) % shirts.length]); peak.position.set(0, 0.2, 0.3); headG.add(peak);
    } else { const h = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.2, 0.35, 8), mat(hairC)); h.position.y = 0.38; headG.add(h); }
    for (const sx of [-0.1, 0.1]) {
      const eye = new THREE.Mesh(new THREE.SphereGeometry(0.06, 6, 6), mat(0xffffff)); eye.position.set(sx, 0.05, 0.22); headG.add(eye);
      const pup = new THREE.Mesh(new THREE.SphereGeometry(0.03, 6, 6), mat(0x111111)); pup.position.set(0, 0, 0.045); eye.add(pup);
    }
    const mouth = boxMesh(0.14, 0.05, 0.02, 0x7a1f1f); mouth.position.set(0, -0.1, 0.24); headG.add(mouth);
    if (i % 3 === 0) { const nose = boxMesh(0.06, 0.08, 0.06, skin); nose.position.set(0, -0.02, 0.25); headG.add(nose); }
    const limb = (w, h, c, px, py, hand) => {
      const g = new THREE.Group();
      const m = new THREE.Mesh(softBox(w, h, w, w * 0.3), mat(c)); m.position.y = -h / 2; m.castShadow = true; g.add(m);
      if (hand) { const hm = new THREE.Mesh(new THREE.SphereGeometry(w * 0.6, 6, 6), mat(hand)); hm.position.y = -h; g.add(hm); }
      g.position.set(px, py, 0); rig.add(g); return g;
    };
    this.armL = limb(0.2, 0.7, shirt, -0.48, 0.55, skin);
    this.armR = limb(0.2, 0.7, shirt, 0.48, 0.55, skin);
    this.legL = limb(0.25, 0.75, pant, -0.18, -0.2, 0x222222);
    this.legR = limb(0.25, 0.75, pant, 0.18, -0.2, 0x222222);
    rig.add(torso, headG);
    // One in three carries something worth dropping.
    if (i % 3 === 1) {
      const bag = boxMesh(0.35, 0.4, 0.2, 0xfff7e0); bag.position.y = -0.85; this.armR.add(bag);
    } else if (i % 3 === 2) {
      const stick = boxMesh(0.03, 0.9, 0.03, 0x555555); stick.position.y = -0.3; this.armR.add(stick);
      const bal = new THREE.Mesh(new THREE.SphereGeometry(0.35, 10, 8), mat(shirts[(i + 1) % shirts.length])); bal.position.y = 0.3; bal.scale.y = 1.15; stick.add(bal);
      this.balloon = bal;
    }
    this.head = headG; this.mouth = mouth;
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
    G.sfx.oof(this.body.position);
    if (!silent) G.sfx.scream(this.body.position, Math.random() < 0.5);
    G.wanted = Math.min(3, G.wanted + 0.5);
    if (this.balloon && this.balloon.parent) { // balloon floats away
      const b = this.balloon; b.parent.remove(b); b.position.copy(this.root.position); b.position.y += 1.5; scene.add(b); G.balloons.push({ m: b, t: 6 });
      this.balloon = null;
    }
    if (!silent) G.addScore(['RUDE', 'BULLY', 'ASSAULT ON PEDESTRIAN', 'NOT SORRY', 'GOATED'][Math.floor(Math.random() * 5)], 150, 'pink');
    else if (this.body.position.distanceTo(G.goat.body.position) < 30) G.addScore('COLLATERAL', 100);
  };
  Human.prototype.update = function (dt) {
    const b = this.body, goat = G.goat.body.position;
    this.anim += dt;
    if (this.state === 'down') {
      const f = Math.sin(this.anim * 22) * 1.4;
      this.armL.rotation.x = f; this.armR.rotation.x = -f; this.legL.rotation.x = -f * 0.6; this.legR.rotation.x = f * 0.6;
      this.mouth.scale.set(1, 3, 1);
      this.downT -= dt;
      if (this.downT <= 0 && b.velocity.length() < 2) {
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
    if (dGoat < 7 && this.state === 'walk') { this.state = 'panic'; this.t = 3 + Math.random() * 2; if (Math.random() < 0.6) G.sfx.scream(b.position); }
    let vx = 0, vz = 0, speed;
    if (this.state === 'panic') {
      this.t -= dt;
      speed = 6.5;
      vx = dx / (dGoat || 1); vz = dz / (dGoat || 1);
      const wob = Math.sin(this.anim * 7) * 0.6;
      [vx, vz] = [vx * Math.cos(wob) - vz * Math.sin(wob), vx * Math.sin(wob) + vz * Math.cos(wob)];
      this.armL.rotation.x = Math.PI + Math.sin(this.anim * 25) * 0.4;
      this.armR.rotation.x = Math.PI + Math.cos(this.anim * 25) * 0.4;
      this.mouth.scale.set(1, 2.2, 1);
      if (this.t <= 0 && dGoat > 10) { this.state = 'walk'; this.pickTarget(); this.mouth.scale.set(1, 1, 1); }
    } else {
      speed = 1.8;
      const tx = this.target.x - b.position.x, tz = this.target.z - b.position.z, d = Math.hypot(tx, tz);
      if (d < 1) this.pickTarget();
      vx = tx / (d || 1); vz = tz / (d || 1);
      this.armL.rotation.x = Math.sin(this.anim * 6) * 0.5;
      this.armR.rotation.x = this.balloon ? -1.2 : -Math.sin(this.anim * 6) * 0.5;
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
    if (b.position.y < -20) { b.position.set(this.home.x, 2, this.home.z); b.velocity.set(0, 0, 0); }
  };

  G.balloons = [];
  const spots = [[4, 6], [-8, -4], [10, -10], [-16, 12], [20, 16], [-24, -18], [36, -8], [-38, 0], [6, 30], [-4, -26], [46, 46], [-50, -50], [-12, 40], [28, -40], [-60, 8]];
  const humanCount = G.quality.tier === 'low' ? 11 : spots.length;
  spots.slice(0, humanCount).forEach((p, i) => {
    const h = new Human(p[0], p[1], i);
    h.body.addEventListener('collide', e => {
      if (e.body === G.groundBody) { if (h.state === 'down' && G.impactSpeed(e) > 5) G.sfx.hit(8, 'flesh', h.body.position); return; }
      if (h.state === 'down') return;
      if (G.impactSpeed(e) > 6) G.after.push(() => h.knock(e.body !== G.goat?.body));
    });
    G.humans.push(h);
  });

  // ---------- Cars ----------
  const carColors = [0xe53935, 0x1e88e5, 0xfdd835, 0x43a047];
  function carMesh(color, police) {
    const g = new THREE.Group();
    const shell = new THREE.Mesh(softBox(2.2, 1.0, 4.4, 0.25), mat(police ? 0xffffff : color)); shell.position.y = 0.15; g.add(shell);
    const cab = new THREE.Mesh(softBox(1.9, 0.85, 2.2, 0.3), mat(police ? 0x1e2a44 : color)); cab.position.set(0, 1.0, -0.2); g.add(cab);
    const glass = new THREE.Mesh(softBox(1.8, 0.6, 2.3, 0.2), mat(0x9adcff)); glass.position.set(0, 1.05, -0.2); g.add(glass);
    for (const [wx, wz] of [[-1.15, 1.4], [1.15, 1.4], [-1.15, -1.4], [1.15, -1.4]]) {
      const w = new THREE.Mesh(new THREE.CylinderGeometry(0.45, 0.45, 0.32, 12), mat(0x222222));
      w.rotation.z = Math.PI / 2; w.position.set(wx, -0.35, wz);
      const rim = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.25, 0.34, 8), mat(0xb8c2d6)); w.add(rim);
      g.add(w); (g.wheels = g.wheels || []).push(w);
    }
    for (const lx of [-0.65, 0.65]) {
      const l = new THREE.Mesh(boxGeo(0.4, 0.25, 0.08), new THREE.MeshBasicMaterial({ color: 0xfff7d0 })); l.position.set(lx, 0.3, 2.21); g.add(l);
      const r = new THREE.Mesh(boxGeo(0.4, 0.2, 0.08), new THREE.MeshBasicMaterial({ color: 0xff3d3d })); r.position.set(lx, 0.3, -2.21); g.add(r);
    }
    const bumper = boxMesh(2.3, 0.25, 0.2, 0xb8c2d6); bumper.position.set(0, -0.15, 2.2); g.add(bumper);
    const bumper2 = bumper.clone(); bumper2.position.z = -2.2; g.add(bumper2);
    if (police) {
      const stripe = boxMesh(2.25, 0.3, 4.3, 0x1e2a44); stripe.position.y = 0.3; g.add(stripe);
      const red = new THREE.Mesh(boxGeo(0.5, 0.3, 0.5), new THREE.MeshBasicMaterial({ color: 0xff2020 })); red.position.set(-0.4, 1.6, -0.2);
      const blue = new THREE.Mesh(boxGeo(0.5, 0.3, 0.5), new THREE.MeshBasicMaterial({ color: 0x2060ff })); blue.position.set(0.4, 1.6, -0.2);
      g.add(red, blue); g.lights = [red, blue];
    }
    g.traverse(o => { o.castShadow = true; });
    return g;
  }
  function Car(a0, i) {
    const body = new CANNON.Body({ mass: 900, linearDamping: 0.05, angularDamping: 0.3 });
    body.addShape(new CANNON.Box(new V3(1.1, 0.55, 2.2)), new V3(0, 0.15, 0));
    body.addShape(new CANNON.Box(new V3(0.95, 0.4, 1.1)), new V3(0, 1.05, -0.2));
    body.tag = { kind: 'car', mat: 'metal' };
    body.car = this;
    body.allowSleep = false;
    const R = G.ringR + (i % 2 ? 2.5 : -2.5);
    body.position.set(Math.cos(a0) * R, 0.9, Math.sin(a0) * R);
    world.addBody(body);
    const g = carMesh(carColors[i % carColors.length]);
    scene.add(g);
    body.mesh = g;
    G.dyn.push(body);
    Object.assign(this, { body, a: a0, R, dir: i % 2 ? 1 : -1, speed: 13 + i * 2, wrecked: false, wreckT: 0, mesh: g });
    body.addEventListener('collide', e => {
      if (e.body === G.groundBody) return;
      const v = G.impactSpeed(e);
      if (e.body === G.goat?.body && !this.wrecked) {
        G.after.push(() => {
          const gb = G.goat.body, vel = this.body.velocity;
          gb.velocity.set(vel.x * 1.8 + (Math.random() - 0.5) * 10, 18 + Math.random() * 8, vel.z * 1.8 + (Math.random() - 0.5) * 10);
          G.goat.ragdoll(3);
          G.sfx.honk(this.body.position); G.sfx.hit(18, 'metal', gb.position); G.sfx.baa(gb.position);
          G.sfx.buzz([30, 30, 60]);
          G.addScore('HIT AND RUN', 400, 'big');
        });
      } else if (v > 14) G.after.push(() => this.wreck());
      else if (v > 4) G.sfx.hit(v, 'metal', this.body.position);
    });
  }
  Car.prototype.wreck = function () {
    if (this.wrecked) return;
    this.wrecked = true; this.wreckT = 15;
    G.sfx.crash(this.body.position);
    G.puff(this.body.position, 0x4a4458, 10, 1.5);
    G.wanted = Math.min(3, G.wanted + 1);
    if (this.body.position.distanceTo(G.goat.body.position) < 30) G.addScore(this.police ? 'COP TOTALLED' : 'TOTALLED', this.police ? 800 : 350, this.police ? 'big' : '');
  };
  Car.prototype.update = function (dt) {
    const b = this.body;
    if (this.wrecked) {
      this.wreckT -= dt;
      if (this.wreckT < 14 && Math.random() < dt * 6) G.puff(new V3(b.position.x, b.position.y + 1, b.position.z), 0x333333, 1, 0.6);
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
    for (const w of this.mesh.wheels) w.rotation.x += this.speed * dt * 2;
  };
  [0, 2.1, 4.2].forEach((a, i) => G.cars.push(new Car(a, i)));

  // ---------- Police: shows up when the town has had enough ----------
  function Police() {
    const body = new CANNON.Body({ mass: 1100, linearDamping: 0.3, angularDamping: 0.9 });
    body.addShape(new CANNON.Box(new V3(1.1, 0.55, 2.2)), new V3(0, 0.15, 0));
    body.addShape(new CANNON.Box(new V3(0.95, 0.4, 1.1)), new V3(0, 1.05, -0.2));
    body.tag = { kind: 'car', mat: 'metal' };
    body.car = this;
    body.allowSleep = false;
    body.position.set(0, -50, 0);
    world.addBody(body);
    const g = carMesh(0, true);
    scene.add(g); body.mesh = g; G.dyn.push(body);
    Object.assign(this, { body, mesh: g, active: false, wrecked: false, wreckT: 0, police: true, a: 0, R: 0, bumpCd: 0 });
    body.addEventListener('collide', e => {
      if (e.body === G.groundBody || !this.active) return;
      const v = G.impactSpeed(e);
      if (e.body === G.goat?.body && !this.wrecked && this.bumpCd <= 0) {
        this.bumpCd = 1.5;
        G.after.push(() => {
          const gb = G.goat.body, vel = this.body.velocity;
          gb.velocity.set(vel.x * 1.5 + (Math.random() - 0.5) * 8, 16 + Math.random() * 6, vel.z * 1.5 + (Math.random() - 0.5) * 8);
          G.goat.ragdoll(2.5);
          G.sfx.hit(18, 'metal', gb.position); G.sfx.baa(gb.position); G.sfx.buzz([30, 30, 60]);
          G.addScore('POLICE BRUTALITY', 300, 'pink');
        });
      } else if (v > 14) G.after.push(() => this.wreck());
      else if (v > 4) G.sfx.hit(v, 'metal', this.body.position);
    });
  }
  Police.prototype.wreck = Car.prototype.wreck;
  Police.prototype.update = function (dt) {
    const b = this.body;
    this.bumpCd -= dt;
    const gp = G.goat.body.position;
    if (!this.active) {
      if (G.wanted >= 2) {
        this.active = true;
        const a = Math.random() * Math.PI * 2;
        b.position.set(gp.x + Math.cos(a) * 50, 1, gp.z + Math.sin(a) * 50);
        b.velocity.set(0, 0, 0); b.angularVelocity.set(0, 0, 0); b.quaternion.set(0, 0, 0, 1);
        G.addScore('COPS CALLED', 200, 'pink');
        G.sfx.airhorn();
      }
      return;
    }
    if (this.wrecked) {
      this.wreckT -= dt;
      if (this.wreckT < 14 && Math.random() < dt * 6) G.puff(new V3(b.position.x, b.position.y + 1, b.position.z), 0x333333, 1, 0.6);
      if (this.wreckT <= 0) { this.wrecked = false; this.active = false; G.wanted = 0; b.position.set(0, -50, 0); b.velocity.set(0, 0, 0); }
      return;
    }
    if (G.wanted <= 0.2) { this.active = false; b.position.set(0, -50, 0); b.velocity.set(0, 0, 0); return; }
    const dx = gp.x - b.position.x, dz = gp.z - b.position.z, d = Math.hypot(dx, dz);
    if (d > 90) { b.position.set(gp.x - dx / d * 45, 1, gp.z - dz / d * 45); }
    const speed = 17;
    const want = Math.atan2(dx, dz);
    const cur = Math.atan2(b.velocity.x, b.velocity.z);
    let dA = want - (Math.hypot(b.velocity.x, b.velocity.z) > 1 ? cur : want);
    dA = Math.atan2(Math.sin(dA), Math.cos(dA));
    const heading = (Math.hypot(b.velocity.x, b.velocity.z) > 1 ? cur : want) + Math.max(-2.2 * dt, Math.min(2.2 * dt, dA));
    if (b.position.y < 1.8 && Math.abs(b.quaternion.x) < 0.3 && Math.abs(b.quaternion.z) < 0.3) {
      const s = d < 4 ? speed * 0.5 : speed;
      b.velocity.x += (Math.sin(heading) * s - b.velocity.x) * Math.min(1, dt * 3);
      b.velocity.z += (Math.cos(heading) * s - b.velocity.z) * Math.min(1, dt * 3);
      b.quaternion.setFromAxisAngle(new V3(0, 1, 0), heading);
      b.angularVelocity.set(0, 0, 0);
    } else if (b.velocity.length() < 1 && b.position.y < 1.8) { this.wreck(); }
    for (const w of this.mesh.wheels) w.rotation.x += speed * dt * 2;
    const blink = Math.floor(performance.now() / 120) % 2;
    this.mesh.lights[0].material.color.setHex(blink ? 0xff2020 : 0x550000);
    this.mesh.lights[1].material.color.setHex(blink ? 0x2060ff : 0x000055);
  };
  const cop = new Police();
  G.police = cop;

  // ---------- Pigeons ----------
  const pigeons = [];
  const pigeonGeo = softBox(0.3, 0.25, 0.45, 0.1);
  for (let i = 0; i < (G.quality.tier === 'low' ? 6 : 12); i++) {
    const g = new THREE.Group();
    const bodyM = new THREE.Mesh(pigeonGeo, mat(i % 3 ? 0x8d99ae : 0xfff7e0)); g.add(bodyM);
    const head = new THREE.Mesh(new THREE.SphereGeometry(0.11, 6, 6), mat(0x5c6b7a)); head.position.set(0, 0.17, 0.22); g.add(head);
    const beak = new THREE.Mesh(new THREE.ConeGeometry(0.03, 0.1, 4), mat(0xff9f5a)); beak.rotation.x = Math.PI / 2; beak.position.set(0, 0.15, 0.34); g.add(beak);
    const wings = [];
    for (const s of [-1, 1]) { const w = boxMesh(0.35, 0.03, 0.3, 0x6b7a8a); w.position.set(s * 0.25, 0.08, 0); g.add(w); wings.push(w); }
    const a = Math.random() * Math.PI * 2, r = 3 + Math.random() * 9;
    g.position.set(Math.cos(a) * r, 0.15, Math.sin(a) * r + 2);
    g.rotation.y = Math.random() * 6;
    scene.add(g);
    pigeons.push({ g, wings, state: 'ground', t: Math.random() * 3, home: g.position.clone(), vy: 0, dir: new THREE.Vector3() });
  }
  function updatePigeons(dt, time) {
    const gp = G.goat.body.position;
    for (const p of pigeons) {
      const d = Math.hypot(p.g.position.x - gp.x, p.g.position.z - gp.z);
      if (p.state === 'ground') {
        p.t -= dt;
        if (p.t <= 0) { p.t = 1 + Math.random() * 3; p.g.rotation.y += (Math.random() - 0.5) * 2; }
        p.g.position.y = 0.15 + (Math.sin(time * 14 + p.home.x) > 0.8 ? 0.04 : 0);
        p.g.translateZ(dt * 0.5);
        if (d < 5) {
          p.state = 'fly'; p.t = 3 + Math.random() * 2; p.vy = 5 + Math.random() * 3;
          p.dir.set(p.g.position.x - gp.x, 0, p.g.position.z - gp.z).normalize();
          G.sfx.whoosh(p.g.position);
          if (Math.random() < 0.3) G.addScore('PIGEON PANIC', 20);
        }
      } else {
        p.t -= dt;
        p.g.position.addScaledVector(p.dir, dt * 8);
        p.vy += (p.t > 1 ? 2 : -9) * dt;
        p.g.position.y = Math.max(0.15, p.g.position.y + p.vy * dt);
        p.g.rotation.y = Math.atan2(p.dir.x, p.dir.z);
        for (let i = 0; i < 2; i++) p.wings[i].rotation.z = (i ? -1 : 1) * Math.sin(time * 30) * 0.9;
        if (p.t <= 0 && p.g.position.y <= 0.16) { p.state = 'ground'; p.t = 2; p.wings.forEach(w => { w.rotation.z = 0; }); }
        if (p.g.position.length() > 120) { p.dir.negate(); }
      }
    }
  }

  // ---------- Golden goats ----------
  const gold = new THREE.MeshToonMaterial({ color: 0xffc400, emissive: 0x7a5200 });
  function statueMesh() {
    const g = new THREE.Group();
    const add = (geo, x, y, z) => { const m = new THREE.Mesh(geo, gold); m.position.set(x, y, z); m.castShadow = true; g.add(m); return m; };
    add(softBox(0.5, 0.42, 0.95, 0.12), 0, 0, 0);
    const hd = add(softBox(0.32, 0.36, 0.4, 0.1), 0, 0.32, 0.5);
    for (const [x, z] of [[-0.18, 0.3], [0.18, 0.3], [-0.18, -0.3], [0.18, -0.3]]) add(boxGeo(0.1, 0.4, 0.1), x, -0.35, z);
    for (const s of [-1, 1]) { const h = add(new THREE.TorusGeometry(0.16, 0.035, 6, 10, Math.PI * 0.9), s * 0.1, 0.6, 0.42); h.rotation.y = Math.PI / 2; }
    hd.rotation.x = 0.2;
    const ped = new THREE.Mesh(new THREE.CylinderGeometry(0.7, 0.9, 0.3, 10), mat(0xb8c2d6)); ped.position.y = -0.7; g.add(ped);
    g.scale.setScalar(1.6);
    return g;
  }
  const statueSpots = [[50, 26, -62], [0, 4.6, 0], [70, 1.6, 60], [-55, 10.5, -43], [-62, 1.6, 41], [22, 7.6, -6], [-16, 5.2, -9], [-4, 5.1, 59]];
  statueSpots.forEach((p, i) => {
    const m = statueMesh();
    m.position.set(p[0], p[1], p[2]);
    if (G.quality.tier !== 'low') { const glow = new THREE.PointLight(0xffd23f, 2.5, 8); m.add(glow); }
    scene.add(m);
    G.statues.push({ m, p: new THREE.Vector3(p[0], p[1], p[2]), got: false, i });
  });

  // ---------- Jetpack pickup ----------
  const jp = new THREE.Group();
  const tank = (x) => { const t = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.25, 0.9, 10), mat(0x9aa5b8)); t.position.x = x; t.castShadow = true; jp.add(t); };
  tank(-0.28); tank(0.28);
  const nose = new THREE.Mesh(new THREE.ConeGeometry(0.25, 0.4, 10), mat(0xe53935)); nose.position.set(-0.28, 0.65, 0); jp.add(nose);
  const nose2 = nose.clone(); nose2.position.x = 0.28; jp.add(nose2);
  const strap = boxMesh(0.9, 0.5, 0.1, 0x6b3e26); strap.position.z = 0.3; jp.add(strap);
  jp.position.set(50, 13.5, -62);
  scene.add(jp);
  G.jetpackPickup = jp;

  G.updatePeople = function (dt, time) {
    for (const h of G.humans) h.update(dt);
    for (const c of G.cars) c.update(dt);
    cop.update(dt);
    updatePigeons(dt, time);
    for (let i = G.balloons.length - 1; i >= 0; i--) {
      const b = G.balloons[i]; b.t -= dt; b.m.position.y += dt * 2.5; b.m.position.x += Math.sin(time * 2) * dt;
      if (b.t <= 0) { scene.remove(b.m); G.balloons.splice(i, 1); }
    }
    for (const s of G.statues) {
      if (s.got) continue;
      s.m.rotation.y = time * 1.5;
      s.m.position.y = s.p.y + Math.sin(time * 2 + s.i) * 0.25;
      if (Math.random() < dt * 2 && s.m.position.distanceTo(G.camera.position) < 50) G.sparkle(s.m.position, 0xfff1a8, 1);
    }
    if (jp.visible) { jp.rotation.y = time * 2; jp.position.y = 13.5 + Math.sin(time * 3) * 0.2; }
    G.wanted = Math.max(0, G.wanted - dt * 0.04);
    G.sirenPos = cop.active && !cop.wrecked ? cop.body.position : null;
  };
})();
