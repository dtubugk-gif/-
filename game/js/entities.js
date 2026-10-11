// Props, trampolines, windmill, barrels, explosions and the pooled particle system.
(function () {
  const { scene, world, mat, addBox, addSphere, addStatic, boxMesh, boxGeo, softBox, quality: Q } = G;
  const V3 = CANNON.Vec3;
  G.after = [];      // callbacks to run after the physics step (never mutate mid-step)
  G.humans = [];
  G.cars = [];
  G.statues = [];
  G.shake = 0;
  G.mayhem = false;

  function impactSpeed(e) {
    return e.contact && e.contact.getImpactVelocityAlongNormal ? Math.abs(e.contact.getImpactVelocityAlongNormal()) : 0;
  }
  G.impactSpeed = impactSpeed;

  // Every prop knocks with its own material when it lands on something.
  function noisy(b) {
    b.addEventListener('collide', e => {
      const v = impactSpeed(e);
      if (v < 2.5) return;
      const other = e.body;
      if (other === G.goat?.body) return; // the goat's own handler covers that
      G.sfx.hit(v, b.tag.mat, b.position);
    });
    return b;
  }
  function prop(w, h, d, x, y, z, color, mass, tag, mesh) {
    return noisy(addBox(w, h, d, x, y, z, color, mass, tag, mesh));
  }

  // ---------- Props ----------
  const crateColors = [0xd99a4e, 0xc7843b, 0xe6b06a];
  function crateMesh(size, c) {
    const m = boxMesh(size, size, size, c);
    const edge = mat(0x8a5a2b);
    for (const [rx, ry] of [[0, 0], [0, Math.PI / 2], [Math.PI / 2, 0]]) {
      const plank = new THREE.Mesh(boxGeo(size * 1.02, size * 0.14, size * 1.02), edge);
      plank.rotation.set(rx, ry, 0); m.add(plank);
    }
    return m;
  }
  function pyramid(cx, cz, layers, size) {
    for (let l = 0; l < layers; l++) {
      const n = layers - l;
      for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
        const x = cx + (i - (n - 1) / 2) * size * 1.01, z = cz + (j - (n - 1) / 2) * size * 1.01;
        const c = crateColors[(i + j + l) % 3];
        prop(size, size, size, x, size / 2 + l * size, z, c, 6, { kind: 'prop', pts: 15, name: 'CRATE', mat: 'wood' }, crateMesh(size, c)).sleep();
      }
    }
  }
  pyramid(22, -6, 5, 1.2);
  pyramid(-22, 8, 4, 1.2);

  for (let i = 0; i < 30; i++) {
    prop(0.3, 2.4, 1.4, -46 + i * 1.3, 1.2, -14, i % 2 ? 0xff4fa3 : 0xfff7e0, 4, { kind: 'prop', pts: 20, name: 'DOMINO', mat: 'wood' }).sleep();
  }

  // Bowling lane
  const pinMesh = () => {
    const m = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.32, 1.3, 10), mat(0xffffff));
    const band = new THREE.Mesh(new THREE.CylinderGeometry(0.235, 0.235, 0.12, 10), mat(0xe53935));
    band.position.y = 0.3; m.add(band); m.castShadow = true;
    return m;
  };
  for (let r = 0; r < 4; r++) for (let k = 0; k <= r; k++) {
    prop(0.5, 1.3, 0.5, -10 + (k - r / 2) * 1.0, 0.65, 38 + r * 0.9, 0, 3, { kind: 'prop', pts: 30, name: 'STRIKE?', mat: 'wood' }, pinMesh()).sleep();
  }
  noisy(addSphere(1.1, -10, 1.2, 24, 0x3949ab, 60, { kind: 'prop', pts: 25, name: 'BOWLING', mat: 'thud' }));

  // Fence by the pond
  for (let i = 0; i < 16; i++) {
    const m = boxMesh(1.9, 1.1, 0.15, 0xfff7e0);
    for (const sx of [-0.7, 0.7]) { const post = boxMesh(0.2, 1.3, 0.22, 0xf0e6cc); post.position.set(sx, 0, 0); m.add(post); }
    prop(1.9, 1.1, 0.15, 58 + i * 2, 0.55, 50, 0, 5, { kind: 'prop', pts: 10, name: 'FENCE', mat: 'wood' }, m).sleep();
  }
  // Beach balls
  for (let i = 0; i < 6; i++) noisy(addSphere(0.7, -6 + i * 3, 0.8, 10 + (i % 2) * 3, [0xff4fa3, 0xffd23f, 0x4fc3f7][i % 3], 1, { kind: 'prop', pts: 5, name: 'BALL', mat: 'rubber' })).sleep();

  // Hay bales (crunchy)
  const hayGeo = new THREE.CylinderGeometry(0.7, 0.7, 1.4, 12);
  for (const [x, z, r] of [[-30, 50, 0], [-28.6, 50, 0.4], [-29.3, 51.3, 0.2], [-27.2, 50.4, 1.1], [40, -20, 0.3], [41.5, -20.4, 0.9], [40.7, -18.8, 0.5]]) {
    const m = new THREE.Mesh(hayGeo, mat(0xe3c060)); m.castShadow = true;
    const band = new THREE.Mesh(new THREE.CylinderGeometry(0.72, 0.72, 0.1, 12), mat(0xb28a2e)); m.add(band);
    const b = prop(1.4, 1.4, 1.4, x, 0.7, z, 0, 8, { kind: 'prop', pts: 15, name: 'HAY DAY', mat: 'hay' }, m);
    b.quaternion.setFromEuler(Math.PI / 2, 0, r); b.sleep();
  }

  // Trash cans (clangy) and a picnic table
  const canGeo = new THREE.CylinderGeometry(0.45, 0.4, 1.1, 12);
  for (const [x, z] of [[-13, -9], [13, 9], [-20, -30], [31, -25], [12, 36], [-50, 25]]) {
    const m = new THREE.Mesh(canGeo, mat(0x607d8b)); m.castShadow = true;
    const lid = new THREE.Mesh(new THREE.CylinderGeometry(0.5, 0.48, 0.15, 12), mat(0x455a64)); lid.position.y = 0.6; m.add(lid);
    prop(0.9, 1.1, 0.9, x, 0.55, z, 0, 7, { kind: 'prop', pts: 20, name: 'GARBAGE', mat: 'metal' }, m).sleep();
  }
  for (const [x, z] of [[-6, -22], [24, 30]]) {
    const top = prop(3, 0.2, 1.4, x, 0.9, z, 0xb07a45, 14, { kind: 'prop', pts: 25, name: 'PICNIC RUINED', mat: 'wood' });
    top.sleep();
    for (const s of [-1, 1]) prop(3, 0.15, 0.5, x, 0.5, z + s * 1.2, 0xb07a45, 6, { kind: 'prop', pts: 10, name: 'BENCH', mat: 'wood' }).sleep();
    for (const k of [-1, 1]) prop(0.2, 0.8, 0.2, x + k * 1.2, 0.4, z, 0x8a5a2b, 2, { kind: 'prop', pts: 5, name: 'LEG', mat: 'wood' }).sleep();
  }
  // Tire stack
  const tireGeo = new THREE.TorusGeometry(0.55, 0.25, 8, 16);
  for (let i = 0; i < 5; i++) {
    const m = new THREE.Mesh(tireGeo, mat(0x263238)); m.rotation.x = Math.PI / 2; m.castShadow = true;
    const g = new THREE.Group(); g.add(m);
    prop(1.6, 0.5, 1.6, 36, 0.25 + i * 0.5, 14, 0, 9, { kind: 'prop', pts: 15, name: 'TIRED', mat: 'rubber' }, g).sleep();
  }

  // ---------- Trampolines ----------
  function trampoline(x, z) {
    const b = addStatic(4, 0.5, 4, x, 0.25, z, 0x3b82f6, 0, { kind: 'tramp', mat: 'rubber' });
    const rim = new THREE.Mesh(new THREE.TorusGeometry(2.1, 0.18, 6, 20), mat(0xffd23f));
    rim.rotation.x = Math.PI / 2; rim.position.y = 0.28;
    b.mesh.add(rim);
    for (let k = 0; k < 4; k++) { const leg = boxMesh(0.2, 0.6, 0.2, 0x37474f); leg.position.set(k % 2 ? 1.7 : -1.7, -0.3, k < 2 ? 1.7 : -1.7); b.mesh.add(leg); }
    b.addEventListener('collide', e => {
      const o = e.body;
      if (!o || o.mass === 0) return;
      G.after.push(() => {
        const up = o === G.goat?.body ? 25 : 16;
        o.velocity.y = Math.max(o.velocity.y, up);
        o.wakeUp();
        G.sfx.boing(o.position);
        if (o === G.goat?.body) { G.goat.squash = 1; G.sfx.buzz(15); }
        G.puff(o.position, 0x9adcff, 5);
      });
    });
  }
  [[44, -62], [18, -14], [-6, 22], [60, 30], [-40, -40], [-48, 30], [0, -44]].forEach(p => trampoline(p[0], p[1]));

  // ---------- Windmill (kinematic spinning death machine) ----------
  addStatic(1.4, 12, 1.4, -62, 6, 46, 0xfff7e0, 0, { kind: 'static', mat: 'wood' });
  const cap = new THREE.Mesh(new THREE.ConeGeometry(1.4, 1.6, 4), mat(0xc0392b, { flatShading: true })); cap.position.set(-62, 12.8, 46); cap.rotation.y = Math.PI / 4; scene.add(cap);
  const blades = new CANNON.Body({ mass: 0, type: CANNON.Body.KINEMATIC });
  blades.addShape(new CANNON.Box(new V3(7, 0.6, 0.3)));
  blades.addShape(new CANNON.Box(new V3(0.6, 7, 0.3)));
  blades.position.set(-62, 11, 47.2);
  blades.angularVelocity.set(0, 0, 1.6);
  blades.tag = { kind: 'windmill', mat: 'wood' };
  blades.allowSleep = false;
  world.addBody(blades);
  const bladeMesh = new THREE.Group();
  for (const [w, h] of [[14, 1.2], [1.2, 14]]) {
    const frame = boxMesh(w, h, 0.5, 0x8a5a2b); bladeMesh.add(frame);
    const sail = boxMesh(w * 0.9, h * 0.9, 0.2, 0xff4fa3); sail.position.z = 0.2; bladeMesh.add(sail);
  }
  const hub = new THREE.Mesh(new THREE.CylinderGeometry(0.8, 0.8, 0.8, 12), mat(0xffd23f)); hub.rotation.x = Math.PI / 2; bladeMesh.add(hub);
  scene.add(bladeMesh);
  blades.mesh = bladeMesh;
  G.dyn.push(blades);
  blades.addEventListener('collide', e => {
    const o = e.body;
    if (!o || o.mass === 0) return;
    G.after.push(() => {
      const d = o.position.vsub(blades.position); d.normalize();
      o.velocity.set(d.x * 30, Math.abs(d.y) * 20 + 12, d.z * 30 + 10);
      G.sfx.hit(20, 'wood', o.position);
      G.sfx.whoosh(o.position);
      if (o === G.goat?.body) { G.goat.ragdoll(2.5); G.addScore('WINDMILLED', 300); G.sfx.baa(o.position); }
      else if (o.human) { o.human.knock(true); G.sfx.scream(o.position, true); }
    });
  });

  // ---------- Explosive barrels ----------
  const barrelGeo = new THREE.CylinderGeometry(0.48, 0.48, 1.2, 14);
  function barrel(x, y, z, falling) {
    const m = new THREE.Mesh(barrelGeo, mat(0xe53935));
    const stripe = new THREE.Mesh(new THREE.CylinderGeometry(0.5, 0.5, 0.25, 14), mat(0xffd23f));
    const skull = new THREE.Mesh(boxGeo(0.3, 0.3, 0.05), mat(0x111111)); skull.position.set(0, 0.25, 0.49);
    m.add(stripe, skull); m.castShadow = true;
    const b = addBox(0.9, 1.2, 0.9, x, y, z, 0, 15, { kind: 'barrel', pts: 0, mat: 'metal' }, m);
    b.addEventListener('collide', e => {
      const v = impactSpeed(e);
      if (v > 7.5) G.after.push(() => explode(b));
      else if (v > 2.5 && e.body !== G.goat?.body) G.sfx.hit(v, 'metal', b.position);
    });
    if (falling) G.sfx.bombWhistle(b.position, 0);
    return b;
  }
  G.barrel = barrel;
  [[30, -45], [31.2, -45], [30.6, -46.1], [29.4, -46], [8, 20], [-20, -5], [60, -5], [-44, 38], [-46, 39], [5, -30], [-60, -20], [70, 20], [-16, -6], [50, -70]]
    .forEach(p => barrel(p[0], 0.6, p[1]).sleep());

  function explode(b) {
    if (b.dead) return;
    b.dead = true;
    const p = b.position.clone();
    G.removeBody(b);
    G.explosion(p, 9, 70);
  }
  G.explodeBarrel = explode;

  // ---------- Particle pool ----------
  const POOL = Q.particles;
  const puffGeo = new THREE.IcosahedronGeometry(0.3, 0);
  const debrisGeo = boxGeo(0.3, 0.3, 0.3);
  const pool = [], active = [];
  for (let i = 0; i < POOL; i++) {
    const m = new THREE.Mesh(puffGeo, new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true }));
    m.visible = false; scene.add(m);
    pool.push(m);
  }
  function spawn(geo, color, x, y, z, o) {
    const m = pool.pop() || active.shift()?.m; // steal the oldest if the pool is dry
    if (!m) return null;
    m.geometry = geo; m.material.color.setHex(color); m.material.opacity = 1;
    m.position.set(x, y, z); m.rotation.set(0, 0, 0); m.scale.setScalar(o.size || 1); m.visible = true;
    o.m = m; o.max = o.life;
    active.push(o);
    return o;
  }
  G.puff = function (p, color, n, speed) {
    const s = speed || 1;
    for (let i = 0; i < (n || 6); i++) {
      spawn(puffGeo, color || 0xffffff, p.x, p.y, p.z, {
        life: 0.5 + Math.random() * 0.3, size: 0.6 + Math.random() * 0.7,
        vel: new THREE.Vector3((Math.random() - 0.5) * 5 * s, Math.random() * 4 * s, (Math.random() - 0.5) * 5 * s), fade: true, grow: 1.6
      });
    }
  };
  function debris(p, color) {
    spawn(debrisGeo, color, p.x, p.y + 0.5, p.z, {
      life: 1.4, size: 0.7 + Math.random() * 0.8,
      vel: new THREE.Vector3((Math.random() - 0.5) * 22, 8 + Math.random() * 14, (Math.random() - 0.5) * 22), grav: true, spin: true
    });
  }
  // Sparkle: a tiny bright puff that doesn't fall.
  G.sparkle = function (p, color, n) {
    for (let i = 0; i < n; i++) spawn(puffGeo, color, p.x + (Math.random() - 0.5) * 1.5, p.y + Math.random() * 1.5, p.z + (Math.random() - 0.5) * 1.5, { life: 0.6, size: 0.25, vel: new THREE.Vector3(0, 1.5, 0), fade: true, grow: 0.8 });
  };

  // A single reusable flash light and a ring for the shockwave; both cheap.
  const flash = new THREE.PointLight(0xffa040, 0, 40); scene.add(flash);
  let flashT = 0;
  const ringMat = new THREE.MeshBasicMaterial({ color: 0xfff1a8, transparent: true, side: THREE.DoubleSide });
  const ring = new THREE.Mesh(new THREE.RingGeometry(0.8, 1, 32), ringMat); ring.rotation.x = -Math.PI / 2; ring.visible = false; scene.add(ring);
  let ringT = 0;
  const scorchMat = new THREE.MeshBasicMaterial({ color: 0x1a1210, transparent: true, opacity: 0.6 });
  const scorches = [];
  for (let i = 0; i < 6; i++) { const s = new THREE.Mesh(new THREE.CircleGeometry(3, 16), scorchMat.clone()); s.rotation.x = -Math.PI / 2; s.visible = false; scene.add(s); scorches.push({ m: s, life: 0 }); }

  G.explosion = function (p, radius, power) {
    G.sfx.boom(p);
    G.shake = Math.min(1.8, G.shake + 1.2);
    G.addScore('KABOOM', 500, 'big');
    G.wanted = Math.min(3, (G.wanted || 0) + 1);
    for (let i = 0; i < 3; i++) {
      spawn(puffGeo, [0xffe066, 0xff8c1a, 0xff3d00][i], p.x, p.y + i * 0.4, p.z, { life: 0.6 + i * 0.15, size: 1, vel: new THREE.Vector3(0, 2, 0), fade: true, grow: radius * (0.9 - i * 0.2) * 3 });
    }
    for (let i = 0; i < 8; i++) spawn(puffGeo, 0x4a4458, p.x, p.y + 1, p.z, { life: 1.6 + Math.random(), size: 1.5, vel: new THREE.Vector3((Math.random() - 0.5) * 4, 4 + Math.random() * 4, (Math.random() - 0.5) * 4), fade: true, grow: 5 });
    for (let i = 0; i < (Q.tier === 'low' ? 8 : 16); i++) debris(p, i % 3 ? 0x333333 : 0xe53935);
    flash.position.set(p.x, p.y + 1, p.z); flash.intensity = 14; flashT = 0.3;
    ring.position.set(p.x, 0.12, p.z); ring.scale.setScalar(1); ring.visible = true; ringMat.opacity = 0.9; ringT = 0.5;
    const sc = scorches.reduce((a, b) => (a.life < b.life ? a : b));
    sc.m.position.set(p.x, 0.045, p.z); sc.m.visible = true; sc.m.material.opacity = 0.6; sc.life = 25;

    for (const o of world.bodies) {
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
      if (o.human) { o.human.knock(true); G.sfx.scream(o.position, true); }
      if (o.car) o.car.wreck();
      if (o === G.goat?.body) G.goat.ragdoll(3);
    }
  };

  G.updateParticles = function (dt) {
    for (let i = active.length - 1; i >= 0; i--) {
      const q = active[i], m = q.m;
      q.life -= dt;
      const t = 1 - q.life / q.max;
      if (q.vel) {
        if (q.grav) q.vel.y -= 25 * dt;
        m.position.addScaledVector(q.vel, dt);
        if (q.grav && m.position.y < 0.15) { m.position.y = 0.15; q.vel.multiplyScalar(0.5); q.vel.y = Math.abs(q.vel.y); }
      }
      if (q.spin) { m.rotation.x += dt * 9; m.rotation.z += dt * 7; }
      if (q.grow) m.scale.setScalar(q.size * (0.3 + t * q.grow));
      if (q.fade) m.material.opacity = 1 - t;
      if (q.life <= 0) { m.visible = false; pool.push(m); active.splice(i, 1); }
    }
    if (flashT > 0) { flashT -= dt; flash.intensity = Math.max(0, 14 * flashT / 0.3); }
    if (ringT > 0) { ringT -= dt; const k = 1 - ringT / 0.5; ring.scale.setScalar(1 + k * 14); ringMat.opacity = 0.9 * (1 - k); if (ringT <= 0) ring.visible = false; }
    for (const s of scorches) if (s.life > 0) { s.life -= dt; if (s.life < 5) s.m.material.opacity = 0.6 * s.life / 5; if (s.life <= 0) s.m.visible = false; }
  };
})();
