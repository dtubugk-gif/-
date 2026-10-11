// Props, trampolines, windmill, explosive barrels, explosions and particles.
(function () {
  const { scene, world, mat, shade, boxMesh, addBox, addSphere, addStatic, canvasTex, outline } = G;
  const V3 = CANNON.Vec3;
  G.after = [];      // callbacks to run after the physics step (never mutate mid-step)
  G.particles = [];
  G.humans = [];
  G.cars = [];
  G.statues = [];
  G.shake = 0;
  G.stats = { booms: [], dominoes: [], pins: [], carsWrecked: 0, peopleDown: 0, maxFlips: 0 };

  function impactSpeed(e) {
    return e.contact && e.contact.getImpactVelocityAlongNormal ? Math.abs(e.contact.getImpactVelocityAlongNormal()) : 0;
  }
  G.impactSpeed = impactSpeed;
  const isGoat = o => o === G.goat?.body || o.goatPart;

  // ---------- Crates (planks + X brace texture) ----------
  const crateTex = canvasTex(128, 128, (c, w, h) => {
    c.fillStyle = '#d99a4e'; c.fillRect(0, 0, w, h);
    c.fillStyle = '#c07a35';
    for (let y = 0; y < h; y += 32) c.fillRect(0, y + 29, w, 3);
    c.strokeStyle = '#8a5424'; c.lineWidth = 14; c.strokeRect(7, 7, w - 14, h - 14);
    c.lineWidth = 12; c.beginPath(); c.moveTo(10, 10); c.lineTo(w - 10, h - 10); c.stroke();
  });
  const crateMat = mat(0xffffff, { map: crateTex });
  function pyramid(cx, cz, layers, size) {
    for (let l = 0; l < layers; l++) {
      const n = layers - l;
      for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
        const x = cx + (i - (n - 1) / 2) * size * 1.01, z = cz + (j - (n - 1) / 2) * size * 1.01;
        addBox(size, size, size, x, size / 2 + l * size, z, crateMat, 6, { kind: 'prop', pts: 15, name: 'CRATE' }).sleep();
      }
    }
  }
  pyramid(22, -6, 5, 1.2);
  pyramid(-22, 8, 4, 1.2);

  // ---------- Dominoes ----------
  const pip = mat(0x2b1d3a);
  for (let i = 0; i < 30; i++) {
    const m = boxMesh(0.3, 2.4, 1.4, i % 2 ? 0xff4fa3 : 0xfff7e0);
    for (const y of [-0.6, 0.6]) { const d = new THREE.Mesh(new THREE.SphereGeometry(0.12, 6, 4), pip); d.position.set(0.15, y, 0); m.add(d); }
    const b = addBox(0.3, 2.4, 1.4, -46 + i * 1.3, 1.2, -14, 0, 4, { kind: 'prop', pts: 20, name: 'DOMINO' }, m);
    b.sleep();
    G.stats.dominoes.push(b);
  }

  // ---------- Bowling lane ----------
  const lane = boxMesh(5, 0.05, 20, 0xe6b06a); lane.position.set(-10, 0.03, 32); lane.castShadow = false; scene.add(lane);
  const pinGeo = new THREE.LatheGeometry([[0, 0], [0.22, 0], [0.3, 0.3], [0.26, 0.65], [0.14, 0.85], [0.16, 1.05], [0.12, 1.2], [0, 1.28]].map(p => new THREE.Vector2(p[0], p[1])), 12);
  pinGeo.translate(0, -0.64, 0);
  for (let r = 0; r < 4; r++) for (let k = 0; k <= r; k++) {
    const m = shade(new THREE.Mesh(pinGeo, mat(0xffffff)));
    const band = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.16, 0.08, 12), mat(0xe53935)); band.position.y = 0.3; m.add(band);
    const b = addBox(0.5, 1.28, 0.5, -10 + (k - r / 2) * 1.0, 0.65, 38 + r * 0.9, 0, 3, { kind: 'prop', pts: 30, name: 'PIN' }, m);
    b.sleep();
    G.stats.pins.push(b);
  }
  addSphere(1.1, -10, 1.2, 24, 0x3949ab, 60, { kind: 'prop', pts: 25, name: 'BOWLING' });

  // Fence by the pond and beach balls near spawn
  for (let i = 0; i < 16; i++) {
    const g = new THREE.Group();
    for (const y of [-0.25, 0.25]) { const rail = boxMesh(1.9, 0.18, 0.1, 0xfff7e0); rail.position.y = y; g.add(rail); }
    for (const x of [-0.85, 0.85]) { const post = boxMesh(0.16, 1.1, 0.16, 0xfff7e0); post.position.x = x; g.add(post); }
    addBox(1.9, 1.1, 0.16, 58 + i * 2, 0.55, 50, 0, 5, { kind: 'prop', pts: 10, name: 'FENCE' }, g).sleep();
  }
  const ballTex = canvasTex(128, 64, (c, w, h) => {
    ['#ff4fa3', '#fff7e0', '#ffd23f', '#fff7e0', '#4fc3f7', '#fff7e0'].forEach((col, i) => { c.fillStyle = col; c.fillRect(i * w / 6, 0, w / 6 + 1, h); });
  });
  for (let i = 0; i < 6; i++) {
    const m = shade(new THREE.Mesh(new THREE.SphereGeometry(0.7, 18, 12), mat(0xffffff, { map: ballTex })));
    addSphere(0.7, -8 + i * 3, 0.8, 14 + (i % 2) * 3, 0, 1, { kind: 'prop', pts: 5, name: 'BALL' }, m).sleep();
  }

  // ---------- Trampolines ----------
  function trampoline(x, z) {
    const b = addStatic(4, 0.6, 4, x, 0.6, z, null, 0, { kind: 'tramp' });
    const g = new THREE.Group();
    const pad = new THREE.Mesh(new THREE.CylinderGeometry(1.9, 1.9, 0.12, 24), mat(0x2b5fd9)); pad.position.y = 0.85; g.add(pad);
    const rim = shade(new THREE.Mesh(new THREE.TorusGeometry(2.05, 0.2, 8, 28), mat(0xffd23f))); rim.rotation.x = Math.PI / 2; rim.position.y = 0.88; g.add(rim);
    for (let k = 0; k < 6; k++) { const a = k / 6 * Math.PI * 2; const l = boxMesh(0.12, 0.9, 0.12, 0x2b1d3a); l.position.set(Math.cos(a) * 1.8, 0.42, Math.sin(a) * 1.8); g.add(l); }
    g.position.set(x, 0, z);
    scene.add(g);
    b.pad = pad;
    b.addEventListener('collide', e => {
      const o = e.body;
      if (!o || o.mass === 0) return;
      G.after.push(() => {
        pad.scale.y = 3; pad.position.y = 0.6;
        if (isGoat(o)) { G.goat.bounce(25); G.sfx.boing(); }
        else { o.velocity.y = Math.max(o.velocity.y, 16); o.wakeUp(); }
      });
    });
    G.tramps.push(pad);
  }
  G.tramps = [];
  [[44, -62], [18, -14], [-6, 26], [60, 30], [-40, -40], [-48, 30]].forEach(p => trampoline(p[0], p[1]));

  // ---------- Windmill (kinematic spinning goat swatter) ----------
  addStatic(3, 8, 3, -62, 4, 44, 0xfff7e0);
  const cap = shade(new THREE.Mesh(new THREE.ConeGeometry(2.6, 3, 4), mat(0xd2453a))); cap.position.set(-62, 9.4, 44); cap.rotation.y = Math.PI / 4; scene.add(cap);
  const blades = new CANNON.Body({ mass: 0, type: CANNON.Body.KINEMATIC });
  blades.addShape(new CANNON.Box(new V3(7, 0.6, 0.3)));
  blades.addShape(new CANNON.Box(new V3(0.6, 7, 0.3)));
  blades.position.set(-62, 8, 46);
  blades.angularVelocity.set(0, 0, 1.6);
  blades.tag = { kind: 'windmill' };
  world.addBody(blades);
  const bladeMesh = new THREE.Group();
  for (let k = 0; k < 4; k++) {
    const arm = new THREE.Group(); arm.rotation.z = k * Math.PI / 2;
    const sail = boxMesh(5.5, 1.2, 0.2, k % 2 ? 0xff4fa3 : 0xfff7e0); sail.position.x = 4; arm.add(sail);
    const spar = boxMesh(7, 0.2, 0.3, 0x7a4426); spar.position.x = 3.5; arm.add(spar);
    bladeMesh.add(arm);
  }
  const hub = shade(new THREE.Mesh(new THREE.SphereGeometry(0.6, 10, 8), mat(0x2b1d3a))); bladeMesh.add(hub);
  scene.add(bladeMesh);
  blades.mesh = bladeMesh;
  G.dyn.push(blades);
  blades.addEventListener('collide', e => {
    const o = e.body;
    if (!o || o.mass === 0) return;
    G.after.push(() => {
      const d = o.position.vsub(blades.position); d.normalize();
      const v = new V3(d.x * 30, Math.abs(d.y) * 20 + 12, d.z * 30 + 10);
      if (isGoat(o)) { G.goat.fling(v); G.goat.ragdoll(2.5); G.addScore('WINDMILLED', 300); G.sfx.whoosh(); }
      else o.velocity.copy(v);
    });
  });

  // ---------- Explosive barrels ----------
  const hazTex = canvasTex(256, 64, (c, w, h) => {
    c.fillStyle = '#e53935'; c.fillRect(0, 0, w, h);
    for (let k = 0; k < 2; k++) {
      const cx = w * (0.25 + k * 0.5);
      c.fillStyle = '#ffd23f';
      c.beginPath(); c.moveTo(cx, 8); c.lineTo(cx + 26, h - 8); c.lineTo(cx - 26, h - 8); c.closePath(); c.fill();
      c.fillStyle = '#2b1d3a'; c.font = 'bold 34px sans-serif'; c.textAlign = 'center'; c.fillText('!', cx, h - 14);
    }
  });
  const barrelGeo = new THREE.CylinderGeometry(0.48, 0.48, 1.2, 16);
  const barrelMat = mat(0xffffff, { map: hazTex }), rimGeo = new THREE.TorusGeometry(0.49, 0.05, 6, 16), rimMat = mat(0x8e1b1b);
  function barrel(x, y, z) {
    const m = shade(new THREE.Mesh(barrelGeo, barrelMat));
    for (const ry of [-0.5, 0, 0.5]) { const r = new THREE.Mesh(rimGeo, rimMat); r.rotation.x = Math.PI / 2; r.position.y = ry; m.add(r); }
    const b = addBox(0.9, 1.2, 0.9, x, y, z, 0, 15, { kind: 'barrel', pts: 0 }, m);
    b.addEventListener('collide', e => { if (impactSpeed(e) > 7.5) G.after.push(() => explode(b)); });
    return b;
  }
  G.barrel = barrel;
  [[30, -45], [31.2, -45], [30.6, -46.1], [29.4, -46], [8, 20], [-20, -5], [60, -5], [-44, 38], [-46, 39], [5, -30], [-60, -20], [70, 20]]
    .forEach(p => barrel(p[0], 0.6, p[1]).sleep());

  const fireMat = new THREE.MeshBasicMaterial({ color: 0xffb02e, transparent: true });
  function explode(b) {
    if (b.dead) return;
    b.dead = true;
    const p = b.position.clone();
    G.removeBody(b);
    G.explosion(p, 9, 70);
  }
  G.explodeBarrel = explode;
  G.explosion = function (p, radius, power) {
    G.sfx.boom();
    G.shake = Math.min(1.6, G.shake + 1.1);
    G.addScore('KABOOM', 500, 'big');
    G.stats.booms.push(performance.now());
    G.vibrate?.(60);
    for (let i = 0; i < 3; i++) {
      const m = new THREE.Mesh(new THREE.IcosahedronGeometry(1, 1), fireMat.clone());
      m.material.color.setHex([0xffe066, 0xff8c1a, 0xff3d00][i]);
      m.position.set(p.x, p.y + i * 0.4, p.z);
      scene.add(m);
      G.particles.push({ m, life: 0.6 + i * 0.15, max: 0.6 + i * 0.15, grow: radius * (0.9 - i * 0.2), vel: new THREE.Vector3(0, 2, 0), fade: true });
    }
    for (let i = 0; i < 4; i++) {
      const m = new THREE.Mesh(new THREE.IcosahedronGeometry(1.2, 1), new THREE.MeshBasicMaterial({ color: 0x6b6470, transparent: true, opacity: 0.8 }));
      m.position.set(p.x + (Math.random() - 0.5) * 2, p.y + 1, p.z + (Math.random() - 0.5) * 2);
      scene.add(m);
      G.particles.push({ m, life: 1.6, max: 1.6, grow: 4, vel: new THREE.Vector3(0, 4 + Math.random() * 3, 0), fade: true });
    }
    for (let i = 0; i < (G.mobile ? 10 : 18); i++) debris(p, i % 3 ? 0x333333 : 0xe53935);
    const flash = new THREE.PointLight(0xffa040, 30, radius * 4);
    flash.position.set(p.x, p.y + 1, p.z);
    scene.add(flash);
    G.particles.push({ m: flash, life: 0.3, max: 0.3, light: true });

    for (const o of world.bodies.slice()) {
      if (o.mass === 0) continue;
      const d = o.position.vsub(p);
      const dist = d.length();
      if (dist > radius) continue;
      const f = (1 - dist / radius) * power;
      d.normalize();
      d.y = Math.max(d.y, 0.55);
      const k = isGoat(o) ? 1.2 : 1;
      o.wakeUp();
      const kick = Math.min(34, f * k * Math.min(1, 30 / Math.max(o.mass, 1)) + f * 0.25);
      o.velocity.vadd(d.scale(kick), o.velocity);
      o.angularVelocity.set((Math.random() - 0.5) * 20, (Math.random() - 0.5) * 20, (Math.random() - 0.5) * 20);
      if (o.tag?.kind === 'barrel' && !o.dead) setTimeout(() => G.after.push(() => explode(o)), 120 + Math.random() * 200);
      if (o.human) o.human.knock(true);
      if (o.car) o.car.wreck();
      if (isGoat(o)) G.goat.ragdoll(3);
    }
  };

  function debris(p, color) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(0.3, 0.3, 0.3), mat(color));
    m.position.set(p.x, p.y + 0.5, p.z);
    scene.add(m);
    G.particles.push({ m, life: 1.4, max: 1.4, vel: new THREE.Vector3((Math.random() - 0.5) * 22, 8 + Math.random() * 14, (Math.random() - 0.5) * 22), grav: true, spin: true });
  }
  G.puff = function (p, color, n, size) {
    for (let i = 0; i < (n || 6); i++) {
      const m = new THREE.Mesh(new THREE.IcosahedronGeometry((size || 0.25) + Math.random() * 0.2, 0), new THREE.MeshBasicMaterial({ color: color || 0xffffff, transparent: true }));
      m.position.set(p.x, p.y, p.z);
      scene.add(m);
      G.particles.push({ m, life: 0.6, max: 0.6, vel: new THREE.Vector3((Math.random() - 0.5) * 5, Math.random() * 4, (Math.random() - 0.5) * 5), fade: true, grow: 0.6 });
    }
  };

  G.updateParticles = function (dt, time) {
    for (let i = G.particles.length - 1; i >= 0; i--) {
      const q = G.particles[i];
      q.life -= dt;
      const t = 1 - q.life / q.max;
      if (q.vel) {
        if (q.grav) q.vel.y -= 25 * dt;
        q.m.position.addScaledVector(q.vel, dt);
        if (q.m.position.y < 0.1) { q.m.position.y = 0.1; q.vel.multiplyScalar(0.5); q.vel.y = Math.abs(q.vel.y); }
      }
      if (q.spin) { q.m.rotation.x += dt * 9; q.m.rotation.z += dt * 7; }
      if (q.grow) q.m.scale.setScalar(0.3 + t * q.grow);
      if (q.fade) q.m.material.opacity = (1 - t) * (q.m.material.userData.o0 || 1);
      if (q.light) q.m.intensity = 30 * (1 - t);
      if (q.life <= 0) {
        scene.remove(q.m);
        if (q.m.material && q.fade) q.m.material.dispose();
        G.particles.splice(i, 1);
      }
    }
    // Ambient animation: trampolines spring back, clouds drift, fountain sprays, flag waves
    for (const pad of G.tramps) { pad.scale.y += (1 - pad.scale.y) * Math.min(1, dt * 10); pad.position.y += (0.85 - pad.position.y) * Math.min(1, dt * 10); }
    for (const c of G.clouds || []) { c.position.x += c.userData.v * dt; if (c.position.x > 300) c.position.x = -300; }
    if (G.fountainSpray) G.fountainSpray.scale.set(1, 0.8 + Math.sin(time * 9) * 0.25, 1);
    if (G.flag) G.flag.rotation.y = Math.sin(time * 4) * 0.35;
  };
})();
