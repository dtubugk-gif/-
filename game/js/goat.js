// The goat: model, controller, headbutt, sticky tongue, ragdoll, jetpack and mayhem mode.
(function () {
  const { scene, world, mat, boxMesh, softBox, boxGeo } = G;
  const V3 = CANNON.Vec3;
  const TAU = Math.PI * 2;

  // ---------- Model ----------
  const root = new THREE.Group();   // follows the physics body
  const rig = new THREE.Group();    // yaw + wobble
  root.add(rig);
  scene.add(root);
  const fur = 0xf3ede0, dark = 0x8a7d6b, pinkish = 0xe9c8b5;
  const torso = new THREE.Mesh(softBox(0.78, 0.66, 1.3, 0.22), mat(fur)); torso.position.y = 0.02; rig.add(torso);
  const belly = new THREE.Mesh(softBox(0.7, 0.3, 1.0, 0.14), mat(0xfaf6ea)); belly.position.set(0, -0.2, 0); rig.add(belly);
  const spot = new THREE.Mesh(softBox(0.8, 0.3, 0.55, 0.12), mat(dark)); spot.position.set(0, 0.2, -0.2); rig.add(spot);
  const spot2 = new THREE.Mesh(softBox(0.4, 0.3, 0.4, 0.1), mat(dark)); spot2.position.set(0.25, 0.1, 0.35); rig.add(spot2);
  const legs = [];
  for (const [x, z] of [[-0.26, 0.45], [0.26, 0.45], [-0.26, -0.45], [0.26, -0.45]]) {
    const hip = new THREE.Group(); hip.position.set(x, -0.28, z);
    const thigh = new THREE.Mesh(softBox(0.2, 0.26, 0.22, 0.07), mat(fur)); thigh.position.y = -0.1; hip.add(thigh);
    const shin = new THREE.Mesh(softBox(0.14, 0.3, 0.14, 0.05), mat(fur)); shin.position.y = -0.3; hip.add(shin);
    const hoof = new THREE.Mesh(softBox(0.18, 0.1, 0.2, 0.04), mat(0x3a2f28)); hoof.position.y = -0.46; hip.add(hoof);
    rig.add(hip); legs.push(hip);
  }
  const tail = new THREE.Group(); tail.position.set(0, 0.3, -0.65);
  const tailM = new THREE.Mesh(softBox(0.16, 0.32, 0.14, 0.06), mat(fur)); tailM.position.y = 0.14; tail.add(tailM); tail.rotation.x = -0.6;
  rig.add(tail);

  const neck = new THREE.Group(); neck.position.set(0, 0.2, 0.55); neck.rotation.x = -0.75; rig.add(neck);
  const neckM = new THREE.Mesh(softBox(0.3, 0.3, 0.62, 0.1), mat(fur)); neckM.position.z = 0.28; neck.add(neckM);
  const collar = new THREE.Mesh(new THREE.TorusGeometry(0.21, 0.05, 6, 14), mat(0xe53935)); collar.position.z = 0.1; collar.rotation.x = Math.PI / 2 - 0.1; neck.add(collar);
  const bell = new THREE.Mesh(new THREE.SphereGeometry(0.08, 8, 6), mat(0xffd23f)); bell.position.set(0, -0.26, 0.12); neck.add(bell);
  const head = new THREE.Group(); head.position.z = 0.55; head.rotation.x = 0.85; neck.add(head);
  const skull = new THREE.Mesh(softBox(0.42, 0.42, 0.62, 0.14), mat(fur)); skull.position.z = 0.15; head.add(skull);
  const snout = new THREE.Mesh(softBox(0.34, 0.28, 0.24, 0.1), mat(pinkish)); snout.position.set(0, -0.06, 0.52); head.add(snout);
  for (const s of [-1, 1]) { const n = new THREE.Mesh(new THREE.SphereGeometry(0.035, 6, 6), mat(0x3a2f28)); n.position.set(s * 0.07, -0.02, 0.64); head.add(n); }
  const beard = new THREE.Mesh(new THREE.ConeGeometry(0.09, 0.3, 6), mat(0xd8cfc0)); beard.position.set(0, -0.36, 0.42); beard.rotation.x = Math.PI; head.add(beard);
  const horns = [];
  for (const s of [-1, 1]) {
    const horn = new THREE.Mesh(new THREE.TorusGeometry(0.22, 0.05, 6, 10, Math.PI * 0.85), mat(0x6b5a48));
    horn.position.set(s * 0.13, 0.28, 0.05); horn.rotation.set(0, Math.PI / 2, 0.35); head.add(horn); horns.push(horn);
    const ear = new THREE.Mesh(softBox(0.34, 0.08, 0.16, 0.03), mat(fur)); ear.position.set(s * 0.33, 0.1, 0.05); ear.rotation.z = s * -0.5; head.add(ear);
    const inner = new THREE.Mesh(boxGeo(0.2, 0.02, 0.08), mat(pinkish)); inner.position.set(0, -0.045, 0); ear.add(inner);
  }
  const pupils = [], eyes = [];
  for (const s of [-1, 1]) {
    const eye = new THREE.Mesh(new THREE.SphereGeometry(0.12, 12, 8), mat(0xffffff));
    eye.position.set(s * 0.2, 0.08, 0.3); head.add(eye); eyes.push(eye);
    const p = new THREE.Mesh(new THREE.SphereGeometry(0.06, 8, 6), new THREE.MeshBasicMaterial({ color: 0x111111 }));
    p.position.set(s * 0.04, 0, 0.085); eye.add(p); pupils.push(p);
  }
  root.traverse(o => { if (o.isMesh) o.castShadow = true; });

  const tongueMesh = new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.09, 1, 8), mat(0xff6f9d));
  tongueMesh.visible = false;
  scene.add(tongueMesh);
  const tongueTip = new THREE.Mesh(new THREE.SphereGeometry(0.11, 8, 6), mat(0xff6f9d)); tongueTip.visible = false; scene.add(tongueTip);
  const flame = new THREE.Mesh(new THREE.ConeGeometry(0.22, 0.9, 8), new THREE.MeshBasicMaterial({ color: 0xffa040 }));
  flame.rotation.x = Math.PI; flame.position.set(0, -0.2, -0.75); flame.visible = false; rig.add(flame);
  const flame2 = new THREE.Mesh(new THREE.ConeGeometry(0.12, 0.6, 8), new THREE.MeshBasicMaterial({ color: 0xfff1a8 })); flame2.position.y = 0.1; flame.add(flame2);
  const pack = new THREE.Group();
  for (const x of [-0.18, 0.18]) {
    const t = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.16, 0.6, 8), mat(0x9aa5b8)); t.position.set(x, 0.45, -0.2); pack.add(t);
    const n = new THREE.Mesh(new THREE.ConeGeometry(0.16, 0.25, 8), mat(0xe53935)); n.position.set(x, 0.87, -0.2); pack.add(n);
  }
  pack.visible = false; rig.add(pack);
  // Mayhem aura
  const aura = new THREE.Mesh(new THREE.IcosahedronGeometry(1.1, 1), new THREE.MeshBasicMaterial({ color: 0xff3d00, transparent: true, opacity: 0.25, wireframe: true }));
  aura.visible = false; rig.add(aura);

  // ---------- Physics ----------
  const body = new CANNON.Body({ mass: 40, fixedRotation: true, linearDamping: 0.01, angularDamping: 0.15 });
  body.addShape(new CANNON.Sphere(0.6));
  body.position.copy(G.spawn);
  body.tag = { kind: 'goat', mat: 'flesh' };
  body.allowSleep = false;
  world.addBody(body);

  const goat = G.goat = {
    body, root, head, squash: 0, yaw: Math.PI, rag: 0, ragManual: false,
    grounded: false, coyote: 0, airT: 0, spin: 0, peakY: 0, butt: 0, buttCd: 0,
    tongue: null, jetpack: false, fuel: 1, jetting: false, launchT: 0,
    neckV: 0, neckA: 0, eyeV: [0, 0], eye: [0, 0], stepPhase: 0, blink: 0, eatT: 0, fallV: 0
  };

  body.addEventListener('collide', e => {
    const v = G.impactSpeed(e);
    const other = e.body;
    if (v > 3) {
      const m = other?.tag?.mat || 'thud';
      if (other === G.groundBody || (other.mass === 0 && Math.abs(e.contact.ni.y) > 0.5)) { if (v > 6) G.sfx.land(v, body.position); }
      else G.sfx.hit(v, m, body.position);
      if (v > 9 && other !== G.groundBody) { G.sfx.baa(body.position); if (v > 16 && goat.rag <= 0) G.after.push(() => goat.ragdoll(1.5)); }
      if (v > 11) G.sfx.buzz(Math.min(60, v * 2));
    }
    if (other?.tag?.kind === 'ramp') goat.onRamp = 0.25;
  });

  goat.ragdoll = function (t) {
    if (t === 'toggle') {
      if (goat.rag > 0 && goat.ragManual) { goat.rag = 0.01; goat.ragManual = false; return; }
      goat.ragManual = true; t = 9999;
    }
    if (goat.rag <= 0) {
      body.fixedRotation = false;
      body.updateMassProperties();
      body.angularVelocity.set((Math.random() - 0.5) * 14, (Math.random() - 0.5) * 8, (Math.random() - 0.5) * 14);
      if (Math.random() < 0.7) G.sfx.baa(body.position);
      G.sfx.jingle(body.position);
    }
    goat.rag = Math.max(goat.rag, t);
    goat.release();
  };

  function standUp() {
    body.fixedRotation = true;
    body.updateMassProperties();
    body.quaternion.set(0, 0, 0, 1);
    body.angularVelocity.set(0, 0, 0);
    body.velocity.y = Math.max(body.velocity.y, 5);
    goat.ragManual = false;
    G.sfx.jingle(body.position);
  }

  goat.respawn = function () {
    goat.release();
    if (goat.rag > 0) { goat.rag = 0; standUp(); }
    body.position.copy(G.spawn);
    body.velocity.set(0, 0, 0);
    G.sfx.baa(body.position);
    G.sfx.whoosh(body.position);
    G.puff(body.position, 0xfff7e0, 10);
  };

  function facing() { return new V3(Math.sin(goat.yaw), 0, Math.cos(goat.yaw)); }
  function mouthPos() {
    const v = new THREE.Vector3(0, -0.05, 0.64);
    head.localToWorld(v);
    return new V3(v.x, v.y, v.z);
  }

  // ---------- Headbutt ----------
  goat.headbutt = function () {
    if (goat.buttCd > 0 || goat.rag > 0) return;
    goat.buttCd = 0.45; goat.butt = 1;
    G.sfx.swing();
    const f = facing();
    const reach = G.mayhem ? 2.2 : 1.4, radius = G.mayhem ? 3.4 : 2.4, punch = G.mayhem ? 30 : 22;
    const c = body.position.vadd(f.scale(reach));
    let hits = 0, hardest = null;
    for (const o of world.bodies) {
      if (o === body || o.mass === 0) continue;
      const d = o.position.vsub(c);
      if (d.length() > radius) continue;
      hits++;
      o.wakeUp();
      // Mass is mostly ignored on purpose. Cars should fly too.
      const k = o.mass > 300 ? 0.6 : 1;
      o.velocity.set(o.velocity.x + f.x * punch * k, o.velocity.y + 9 * k + Math.random() * 4, o.velocity.z + f.z * punch * k);
      o.angularVelocity.set((Math.random() - 0.5) * 16, (Math.random() - 0.5) * 16, (Math.random() - 0.5) * 16);
      if (!hardest || o.mass > hardest.mass) hardest = o;
      if (o.human) { o.human.knock(); G.sfx.scream(o.position, true); continue; }
      if (o.car) { o.car.wreck(); G.addScore('CAR FLIP', 450, 'big'); continue; }
      if (o.tag?.kind === 'barrel') { const b = o; G.after.push(() => G.explodeBarrel(b)); continue; }
      if (o.tag?.pts) G.addScore(o.tag.name || 'SMASH', o.tag.pts);
    }
    G.puff(c, 0xfff7e0, 4);
    if (hits) {
      G.sfx.hit(16, hardest.tag?.mat || 'thud', c);
      G.sfx.buzz(hits > 3 ? [20, 20, 40] : 25);
      G.shake = Math.min(1.2, G.shake + 0.25 + hits * 0.05);
      body.velocity.x -= f.x * 4; body.velocity.z -= f.z * 4;
      if (hits >= 6) { G.addScore('MULTI-BONK x' + hits, hits * 40, 'big'); G.sfx.airhorn(); }
    } else G.sfx.baa(body.position);
  };

  // ---------- Tongue ----------
  goat.lick = function () {
    if (goat.tongue) { goat.release(true); return; }
    if (goat.rag > 0) return;
    const from = mouthPos(), f = facing();
    let best = null, bestD = 4.2;
    for (const o of world.bodies) {
      if (o === body || o === G.groundBody) continue;
      const d = o.position.vsub(from);
      const ahead = d.x * f.x + d.z * f.z;
      if (ahead < -0.3) continue;
      const dist = d.length();
      const isBig = o.mass === 0;
      if (!isBig && dist < bestD) { bestD = dist; best = { o, p: o.position.clone() }; }
    }
    if (!best) {
      const res = new CANNON.RaycastResult();
      const to = from.vadd(f.scale(3.5)).vadd(new V3(0, 0.3, 0));
      world.raycastClosest(from, to, { skipBackfaces: true }, res);
      if (res.hasHit && res.body !== body && res.body !== G.groundBody) best = { o: res.body, p: res.hitPointWorld.clone() };
    }
    if (!best) { G.sfx.slurp(body.position); return; }
    const local = best.o.pointToLocalFrame(best.p);
    const len = Math.max(1.6, Math.min(3.2, best.p.distanceTo(from)));
    goat.tongue = { o: best.o, local, len, t: 0 };
    goat.eatT = 0;
    best.o.wakeUp();
    G.sfx.slurp(body.position);
    G.sfx.buzz(12);
    G.addScore(best.o.mass === 0 ? 'LICKED A WALL' : best.o.human ? 'UNWANTED LICK' : 'LICK', best.o.mass === 0 ? 50 : 25);
    if (best.o.human) G.sfx.scream(best.o.position);
  };
  goat.release = function (yeet) {
    if (!goat.tongue) return;
    const o = goat.tongue.o;
    if (yeet && o.mass > 0 && o.velocity.length() > 8) { G.addScore('YEET', 120, 'pink'); G.sfx.yeet(); G.sfx.buzz(20); }
    else if (yeet) G.sfx.slurp(body.position);
    goat.tongue = null;
    tongueMesh.visible = false; tongueTip.visible = false;
  };

  // Spring between the mouth and the licked point. Stretchy on purpose.
  goat.prePhysics = function () {
    const t = goat.tongue;
    if (!t) return;
    const o = t.o;
    if (!world.bodies.includes(o)) { goat.release(); return; }
    const a = mouthPos(), b = o.pointToWorldFrame(t.local);
    const d = b.vsub(a), dist = d.length();
    if (dist > 14) { goat.release(); G.addScore('TONGUE SNAPPED', 60); G.sfx.yeet(); return; }
    if (dist <= t.len) return;
    d.normalize();
    const relV = o.velocity.vsub(body.velocity).dot(d);
    let fmag = 900 * (dist - t.len) + 60 * relV;
    fmag = Math.max(0, Math.min(fmag, 26000));
    const F = d.scale(fmag);
    if (o.mass > 0) { o.applyForce(F.negate(), b); o.wakeUp(); }
    body.applyForce(F.scale(o.mass > 0 ? Math.min(1, o.mass / 120) : 1), a);
  };

  // ---------- Per-frame control ----------
  goat.update = function (dt, input, camYaw, time) {
    goat.buttCd -= dt; goat.butt = Math.max(0, goat.butt - dt * 4);
    goat.launchT -= dt; goat.onRamp = (goat.onRamp || 0) - dt;

    // Ground check from current contacts
    let g = false;
    for (const c of world.contacts) {
      if (c.bi === body && -c.ni.y > 0.5) g = true;
      else if (c.bj === body && c.ni.y > 0.5) g = true;
    }
    if (g) goat.coyote = 0.12; else goat.coyote -= dt;
    const wasGrounded = goat.grounded;
    goat.grounded = goat.coyote > 0;

    // Trick tracking
    if (!goat.grounded) {
      goat.airT += dt;
      goat.peakY = Math.max(goat.peakY, body.position.y);
      goat.fallV = body.velocity.y;
      if (goat.rag > 0) goat.spin += Math.min(9, body.angularVelocity.length()) * dt;
      if (goat.airT > 1.4 && goat.fallV < -12 && !goat.whistled) { goat.whistled = true; G.sfx.slideDown(); }
    } else if (!wasGrounded || goat.airT > 0) {
      const fall = goat.peakY - body.position.y;
      const flips = Math.floor(goat.spin / TAU);
      if (flips >= 1) { G.addScore(flips > 3 ? 'SPIN CYCLE x' + flips : 'FLIP x' + flips, 100 * flips, flips > 2 ? 'big' : ''); G.sfx.flip(flips); }
      if (goat.airT > 1.6) G.addScore('AIR TIME ' + goat.airT.toFixed(1) + 's', Math.round(goat.airT * 80));
      if (fall > 12) { G.addScore('GRAVITY WORKS', Math.round(fall * 10)); G.shake = Math.min(1.5, G.shake + 0.5); }
      if (goat.airT > 0.4) { goat.squash = Math.min(1, goat.airT * 0.6); G.puff(body.position.vadd(new V3(0, -0.5, 0)), 0xe8d9b5, 3 + Math.floor(goat.airT * 2)); G.sfx.land(Math.abs(goat.fallV), body.position); }
      goat.airT = 0; goat.spin = 0; goat.peakY = body.position.y; goat.whistled = false;
    }

    // Ragdoll timer
    if (goat.rag > 0) {
      goat.rag -= dt;
      if (goat.rag <= 0) {
        if (body.velocity.length() > 4 && !goat.ragManual) goat.rag = 0.2;
        else standUp();
      }
    }

    // Movement
    const mx = input.x, mz = input.z;
    const len = Math.hypot(mx, mz);
    if (goat.rag <= 0) {
      const sprint = (input.sprint ? 13 : 7.5) * (G.mayhem ? 1.25 : 1);
      const sx = mx * Math.cos(camYaw) + mz * Math.sin(camYaw);
      const sz = -mx * Math.sin(camYaw) + mz * Math.cos(camYaw);
      const dirX = len ? sx / len : 0, dirZ = len ? sz / len : 0;
      const k = goat.grounded ? 12 : 2.5;
      if (len > 0.1 || goat.grounded) {
        const tvx = dirX * sprint * Math.min(1, len), tvz = dirZ * sprint * Math.min(1, len);
        const kk = goat.launchT > 0 ? 0.4 : k;
        body.velocity.x += (tvx - body.velocity.x) * Math.min(1, kk * dt);
        body.velocity.z += (tvz - body.velocity.z) * Math.min(1, kk * dt);
      }
      if (len > 0.1) {
        let dA = Math.atan2(dirX, dirZ) - goat.yaw;
        dA = Math.atan2(Math.sin(dA), Math.cos(dA));
        goat.yaw += dA * Math.min(1, dt * 12);
      }
      if (input.jump && goat.grounded) {
        body.velocity.y = 11.5; goat.coyote = 0; goat.grounded = false;
        goat.squash = -0.6;
        G.sfx.jingle(body.position);
        if (Math.random() < 0.3) G.sfx.baa(body.position);
        G.puff(body.position.vadd(new V3(0, -0.5, 0)), 0xe8d9b5, 2);
      }
      // Ramps add a speed boost so you actually get airborne
      if (goat.onRamp > 0 && len > 0.1) {
        body.velocity.x *= 1 + dt * 2.2; body.velocity.z *= 1 + dt * 2.2;
        body.velocity.y = Math.max(body.velocity.y, 3);
      }
    }

    // Jetpack
    goat.jetting = goat.jetpack && input.jet && goat.fuel > 0;
    if (goat.jetting) {
      body.velocity.y = Math.min(body.velocity.y + 48 * dt, 15);
      const f = facing();
      body.velocity.x += f.x * 14 * dt; body.velocity.z += f.z * 14 * dt;
      goat.fuel = Math.max(0, goat.fuel - dt * 0.18);
      if (Math.random() < 0.6) G.puff(body.position.vadd(new V3(-f.x, -0.3, -f.z)), Math.random() < 0.5 ? 0xffa040 : 0x4a4458, 1, 0.5);
      if (goat.fuel <= 0) G.sfx.glitch();
    } else if (goat.jetpack && goat.grounded) goat.fuel = Math.min(1, goat.fuel + dt * 0.35);
    flame.visible = goat.jetting;
    flame.scale.y = 0.7 + Math.random() * 0.8;

    // Eating: hold a small prop on the tongue for a while and it's lunch.
    if (goat.tongue && goat.tongue.o.mass > 0 && goat.tongue.o.mass <= 10 && !goat.tongue.o.human) {
      const d = goat.tongue.o.position.distanceTo(mouthPos());
      if (d < 2.2) {
        goat.eatT += dt;
        if (goat.eatT > 1.3) {
          const o = goat.tongue.o;
          goat.release();
          G.puff(o.position, 0xfff7e0, 6);
          G.removeBody(o);
          G.sfx.chomp(body.position);
          G.sfx.buzz([15, 30, 15]);
          G.addScore('ATE IT', 80, 'pink');
        }
      } else goat.eatT = 0;
    }

    // Pickups
    const P = body.position;
    const pv = new THREE.Vector3(P.x, P.y, P.z);
    if (G.jetpackPickup.visible && G.jetpackPickup.position.distanceTo(pv) < 2) {
      G.jetpackPickup.visible = false; goat.jetpack = true; pack.visible = true;
      G.sfx.powerup(); G.sfx.buzz([20, 40, 80]); G.addScore(G.isTouch ? 'JETPACK GET! HOLD JUMP' : 'JETPACK GET! HOLD J', 1000, 'big');
      G.sparkle(pv, 0xfff1a8, 20);
    }
    for (const s of G.statues) {
      if (!s.got && s.m.position.distanceTo(pv) < 2.3) {
        s.got = true; scene.remove(s.m);
        G.sfx.golden(pv); G.onStatue();
        G.sparkle(pv, 0xffc400, 30);
      }
    }
    if (P.y < -30) goat.respawn();

    // Mayhem aura
    aura.visible = G.mayhem;
    if (G.mayhem) {
      aura.rotation.x += dt * 3; aura.rotation.y += dt * 2; aura.scale.setScalar(1 + Math.sin(time * 12) * 0.08);
      if (Math.random() < dt * 25) G.puff(body.position.vadd(new V3((Math.random() - 0.5), -0.3, (Math.random() - 0.5))), Math.random() < 0.5 ? 0xff3d00 : 0xffd23f, 1, 0.4);
    }
    for (const p of pupils) p.material.color.setHex(G.mayhem ? 0xff2020 : 0x111111);

    animate(dt, input, time, len);
  };

  // ---------- Animation (gallop, squash, flailing legs, floppy neck, googly eyes) ----------
  function animate(dt, input, time, len) {
    root.position.set(body.position.x, body.position.y, body.position.z);
    const sp = Math.hypot(body.velocity.x, body.velocity.z);
    if (goat.rag > 0) {
      root.quaternion.set(body.quaternion.x, body.quaternion.y, body.quaternion.z, body.quaternion.w);
      rig.rotation.set(0, goat.yaw, 0);
    } else {
      root.quaternion.set(0, 0, 0, 1);
      rig.rotation.set(Math.min(0.5, -body.velocity.y * 0.025) + Math.sin(time * 20) * Math.min(sp, 13) * 0.004, goat.yaw, Math.sin(time * 9) * Math.min(sp, 13) * 0.01);
    }
    goat.squash += (0 - goat.squash) * Math.min(1, dt * 8);
    const s = goat.squash;
    rig.scale.set(1 + s * 0.35, 1 - s * 0.4, 1 + s * 0.2);

    // Gallop cycle: front pair and back pair alternate, body bobs, bell jingles on each stride.
    const flail = goat.rag > 0 || (!goat.grounded && input.jumpHeld);
    if (goat.grounded && sp > 1 && goat.rag <= 0) {
      const prev = goat.stepPhase;
      goat.stepPhase += dt * (5 + sp * 1.2);
      if (Math.floor(goat.stepPhase / Math.PI) !== Math.floor(prev / Math.PI)) {
        if (sp > 6) { G.sfx.jingle(body.position); if (Math.random() < 0.5) G.puff(body.position.vadd(new V3(0, -0.5, 0)), 0xe8d9b5, 1, 0.4); }
      }
    }
    const ph = goat.stepPhase;
    rig.position.y = goat.grounded && sp > 2 ? Math.abs(Math.sin(ph)) * Math.min(0.14, sp * 0.012) : 0;
    for (let i = 0; i < 4; i++) {
      const front = i < 2, side = i % 2 ? 1 : -1;
      if (flail) {
        legs[i].rotation.x = Math.sin(time * 34 + i * 1.7) * 1.4;
        legs[i].rotation.z = Math.cos(time * 29 + i) * 0.9;
      } else if (!goat.grounded) {
        legs[i].rotation.x = front ? -0.9 : 0.9;
        legs[i].rotation.z = 0;
      } else {
        const amp = Math.min(1, sp / 6) * 0.95;
        legs[i].rotation.x = Math.sin(ph + (front ? 0 : Math.PI) + side * 0.25) * amp;
        legs[i].rotation.z = 0;
      }
    }
    tail.rotation.z = Math.sin(time * 18) * 0.6 * (sp > 1 ? 1 : 0.4);

    // Neck is a damped spring driven by vertical speed: big bounce, big wobble
    const target = -0.75 - body.velocity.y * 0.03 + (goat.rag > 0 ? Math.sin(time * 15) * 0.8 : 0) + (goat.grounded ? Math.sin(ph) * Math.min(0.12, sp * 0.01) : 0);
    goat.neckV += ((target - neck.rotation.x) * 120 - goat.neckV * 7) * dt;
    neck.rotation.x += goat.neckV * dt;
    neck.rotation.y = goat.rag > 0 ? Math.sin(time * 11) * 0.8 : Math.sin(time * 3) * 0.05;
    const lunge = Math.sin(goat.butt * Math.PI);
    neck.scale.z = 1 + lunge * 1.4 + (goat.tongue ? 0.3 : 0);
    neck.position.z = 0.55 + lunge * 0.3;
    bell.position.x = Math.sin(time * 12) * Math.min(0.06, sp * 0.006);

    // Googly eyes lag behind the head; occasional blink
    for (let k = 0; k < 2; k++) {
      const drive = k === 0 ? -body.velocity.x * 0.004 : body.velocity.y * 0.004;
      goat.eyeV[k] += ((drive - goat.eye[k]) * 300 - goat.eyeV[k] * 6) * dt;
      goat.eye[k] = Math.max(-0.06, Math.min(0.06, goat.eye[k] + goat.eyeV[k] * dt));
    }
    for (const p of pupils) { p.position.y = goat.eye[1]; p.position.x = (p === pupils[0] ? -0.03 : 0.03) + goat.eye[0]; }
    goat.blink -= dt;
    if (goat.blink < -3 - Math.random() * 3) goat.blink = 0.12;
    const open = goat.blink > 0 ? 0.15 : 1;
    for (const e of eyes) e.scale.y = open;

    // Tongue
    if (goat.tongue) {
      const a = mouthPos(), b = goat.tongue.o.pointToWorldFrame(goat.tongue.local);
      const av = new THREE.Vector3(a.x, a.y, a.z), bv = new THREE.Vector3(b.x, b.y, b.z);
      const mid = av.clone().add(bv).multiplyScalar(0.5);
      const len = av.distanceTo(bv);
      tongueMesh.visible = true; tongueTip.visible = true;
      tongueMesh.position.copy(mid);
      tongueMesh.scale.set(1 / Math.max(1, len * 0.3), len, 1 / Math.max(1, len * 0.3));
      tongueMesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), bv.clone().sub(av).normalize());
      tongueTip.position.copy(bv);
    }
  }
})();
