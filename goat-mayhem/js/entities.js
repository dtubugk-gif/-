// Props, people, cars, barrels, trampolines, collectibles and explosions.
(function () {
  const { scene, world, mat, addBox, addSphere, addStatic, boxMesh } = G;
  const V3 = CANNON.Vec3;
  G.after = [];      // callbacks to run after the physics step (never mutate mid-step)
  G.particles = [];
  G.humans = [];
  G.cars = [];
  G.statues = [];
  G.shake = 0;

  function impactSpeed(e) {
    return e.contact && e.contact.getImpactVelocityAlongNormal ? Math.abs(e.contact.getImpactVelocityAlongNormal()) : 0;
  }
  G.impactSpeed = impactSpeed;

  // ---------- Props ----------
  const crateColors = [0xd99a4e, 0xc7843b, 0xe6b06a];
  function pyramid(cx, cz, layers, size) {
    for (let l = 0; l < layers; l++) {
      const n = layers - l;
      for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
        const x = cx + (i - (n - 1) / 2) * size * 1.01, z = cz + (j - (n - 1) / 2) * size * 1.01;
        const b = addBox(size, size, size, x, size / 2 + l * size, z, crateColors[(i + j + l) % 3], 6, { kind: 'prop', pts: 15, name: 'CRATE' });
        b.allowSleep = true; b.sleep();
      }
    }
  }
  pyramid(22, -6, 5, 1.2);
  pyramid(-22, 8, 4, 1.2);

  for (let i = 0; i < 30; i++) {
    const b = addBox(0.3, 2.4, 1.4, -46 + i * 1.3, 1.2, -14, i % 2 ? 0xff4fa3 : 0xfff7e0, 4, { kind: 'prop', pts: 20, name: 'DOMINO' });
    b.sleep();
  }

  // Bowling lane
  const pinMesh = () => {
    const m = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.32, 1.3, 10), mat(0xffffff));
    const band = new THREE.Mesh(new THREE.CylinderGeometry(0.235, 0.235, 0.12, 10), mat(0xe53935));
    band.position.y = 0.3; m.add(band); m.castShadow = true;
    return m;
  };
  for (let r = 0; r < 4; r++) for (let k = 0; k <= r; k++) {
    addBox(0.5, 1.3, 0.5, -10 + (k - r / 2) * 1.0, 0.65, 38 + r * 0.9, 0, 3, { kind: 'prop', pts: 30, name: 'STRIKE?' }, pinMesh()).sleep();
  }
  addSphere(1.1, -10, 1.2, 24, 0x3949ab, 60, { kind: 'prop', pts: 25, name: 'BOWLING' });

  // Fence by the pond
  for (let i = 0; i < 16; i++) addBox(1.9, 1.1, 0.15, 58 + i * 2, 0.55, 50, 0xfff7e0, 5, { kind: 'prop', pts: 10, name: 'FENCE' }).sleep();
  // Scattered beach balls, because why not
  for (let i = 0; i < 6; i++) addSphere(0.7, -6 + i * 3, 0.8, 10 + (i % 2) * 3, [0xff4fa3, 0xffd23f, 0x4fc3f7][i % 3], 1, { kind: 'prop', pts: 5, name: 'BALL' }).sleep();

  // ---------- Trampolines ----------
  function trampoline(x, z) {
    const b = addStatic(4, 0.5, 4, x, 0.25, z, 0x3b82f6, 0, { kind: 'tramp' });
    const rim = new THREE.Mesh(new THREE.TorusGeometry(2.1, 0.18, 6, 20), mat(0xffd23f));
    rim.rotation.x = Math.PI / 2; rim.position.y = 0.28;
    b.mesh.add(rim);
    b.addEventListener('collide', e => {
      const o = e.body;
      if (!o || o.mass === 0) return;
      G.after.push(() => {
        const up = o === G.goat?.body ? 25 : 16;
        o.velocity.y = Math.max(o.velocity.y, up);
        o.wakeUp();
        if (o === G.goat?.body) { G.sfx.boing(); G.goat.squash = 1; }
      });
    });
  }
  [[44, -62], [18, -14], [-6, 22], [60, 30], [-40, -40], [-48, 30]].forEach(p => trampoline(p[0], p[1]));

  // ---------- Windmill (kinematic spinning death machine) ----------
  addStatic(1.2, 12, 1.2, -62, 6, 46, 0xfff7e0);
  const blades = new CANNON.Body({ mass: 0, type: CANNON.Body.KINEMATIC });
  blades.addShape(new CANNON.Box(new V3(7, 0.6, 0.3)));
  blades.addShape(new CANNON.Box(new V3(0.6, 7, 0.3)));
  blades.position.set(-62, 11, 47.2);
  blades.angularVelocity.set(0, 0, 1.6);
  blades.tag = { kind: 'windmill' };
  world.addBody(blades);
  const bladeMesh = new THREE.Group();
  bladeMesh.add(boxMesh(14, 1.2, 0.6, 0xff4fa3), boxMesh(1.2, 14, 0.6, 0xff4fa3));
  scene.add(bladeMesh);
  blades.mesh = bladeMesh;
  G.dyn.push(blades);
  blades.addEventListener('collide', e => {
    const o = e.body;
    if (!o || o.mass === 0) return;
    G.after.push(() => {
      const d = o.position.vsub(blades.position); d.normalize();
      o.velocity.set(d.x * 30, Math.abs(d.y) * 20 + 12, d.z * 30 + 10);
      if (o === G.goat?.body) { G.goat.ragdoll(2.5); G.addScore('WINDMILLED', 300); G.sfx.whoosh(); }
    });
  });

  // ---------- Explosive barrels ----------
  const barrelGeo = new THREE.CylinderGeometry(0.48, 0.48, 1.2, 14);
  function barrel(x, y, z) {
    const m = new THREE.Mesh(barrelGeo, mat(0xe53935));
    const stripe = new THREE.Mesh(new THREE.CylinderGeometry(0.5, 0.5, 0.25, 14), mat(0xffd23f));
    m.add(stripe); m.castShadow = true;
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
  G.explosion = function (p, radius, power) {
    G.sfx.boom();
    G.shake = Math.min(1.6, G.shake + 1.1);
    G.addScore('KABOOM', 500, 'big');
    // fireball + smoke + debris
    for (let i = 0; i < 3; i++) {
      const m = new THREE.Mesh(new THREE.IcosahedronGeometry(1, 1), fireMat.clone());
      m.material.color.setHex([0xffe066, 0xff8c1a, 0xff3d00][i]);
      m.position.set(p.x, p.y + i * 0.4, p.z);
      scene.add(m);
      G.particles.push({ m, life: 0.6 + i * 0.15, max: 0.6 + i * 0.15, grow: radius * (0.9 - i * 0.2), vel: new THREE.Vector3(0, 2, 0), fade: true });
    }
    for (let i = 0; i < 18; i++) debris(p, i % 3 ? 0x333333 : 0xe53935);
    const flash = new THREE.PointLight(0xffa040, 8, radius * 4);
    flash.position.set(p.x, p.y + 1, p.z);
    scene.add(flash);
    G.particles.push({ m: flash, life: 0.3, max: 0.3, light: true });

    const all = world.bodies;
    for (const o of all) {
      if (o.mass === 0) continue;
      const d = o.position.vsub(p);
      const dist = d.length();
      if (dist > radius) continue;
      const f = (1 - dist / radius) * power;
      d.normalize();
      d.y = Math.max(d.y, 0.55);
      const k = o === G.goat?.body ? 1.2 : 1;
      o.wakeUp();
      const kick = Math.min(34, f * k * Math.min(1, 30 / Math.max(o.mass, 1)) + f * 0.25);
      o.velocity.vadd(d.scale(kick), o.velocity);
      o.angularVelocity.set((Math.random() - 0.5) * 20, (Math.random() - 0.5) * 20, (Math.random() - 0.5) * 20);
      if (o.tag?.kind === 'barrel' && !o.dead) setTimeout(() => G.after.push(() => explode(o)), 120 + Math.random() * 200);
      if (o.human) o.human.knock(true);
      if (o.car) o.car.wreck();
      if (o === G.goat?.body) G.goat.ragdoll(3);
    }
  };

  function debris(p, color) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(0.3, 0.3, 0.3), mat(color));
    m.position.set(p.x, p.y + 0.5, p.z);
    scene.add(m);
    G.particles.push({ m, life: 1.4, max: 1.4, vel: new THREE.Vector3((Math.random() - 0.5) * 22, 8 + Math.random() * 14, (Math.random() - 0.5) * 22), grav: true, spin: true });
  }
  G.puff = function (p, color, n) {
    for (let i = 0; i < (n || 6); i++) {
      const m = new THREE.Mesh(new THREE.IcosahedronGeometry(0.25 + Math.random() * 0.2, 0), new THREE.MeshBasicMaterial({ color: color || 0xffffff, transparent: true }));
      m.position.set(p.x, p.y, p.z);
      scene.add(m);
      G.particles.push({ m, life: 0.6, max: 0.6, vel: new THREE.Vector3((Math.random() - 0.5) * 5, Math.random() * 4, (Math.random() - 0.5) * 5), fade: true, grow: 0.6 });
    }
  };

  G.updateParticles = function (dt) {
    for (let i = G.particles.length - 1; i >= 0; i--) {
      const q = G.particles[i];
      q.life -= dt;
      const t = 1 - q.life / q.max;
      if (q.vel) {
        if (q.grav) q.vel.y -= 25 * dt;
        q.m.position.addScaledVector(q.vel, dt);
        if (q.m.position.y < 0.1) { q.m.position.y = 0.1; q.vel.multiplyScalar(0.5); q.vel.y = Math.abs(q.vel.y); }
      }
      if (q.spin) q.m.rotation.x += dt * 9, q.m.rotation.z += dt * 7;
      if (q.grow) q.m.scale.setScalar(0.3 + t * q.grow);
      if (q.fade) q.m.material.opacity = 1 - t;
      if (q.light) q.m.intensity = 8 * (1 - t);
      if (q.life <= 0) {
        scene.remove(q.m);
        if (q.m.material && q.fade) q.m.material.dispose();
        G.particles.splice(i, 1);
      }
    }
  };
})();
