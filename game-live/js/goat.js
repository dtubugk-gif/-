// The goat: controller, headbutt, sticky tongue, bleat, jetpack and a real multi-part ragdoll.
(function () {
  const { scene, world, mat, outline } = G;
  const V3 = CANNON.Vec3;
  const TAU = Math.PI * 2;
  const L = G.goatLayout;

  // ---------- Assembled goat (normal mode) ----------
  const root = new THREE.Group();
  const P = G.makeGoatParts();
  const rig = G.assembleGoat(P);
  outline(rig, 0.022);
  G.bakeRig(rig, [rig, P.torso, ...P.legs, ...P.knees, P.neck, P.head, P.tail, ...P.ears], [...P.eyes, P.mouth]);
  root.add(rig);
  root.userData.noBake = true;
  scene.add(root);

  const pack = G.makeJetpack();
  pack.position.set(0, 0.42, -0.12); pack.rotation.x = -1.25; pack.scale.setScalar(0.85); pack.visible = false;
  G.bakeRig(pack, [pack], []);
  P.torso.add(pack);
  const flames = [];
  for (const x of [-0.17, 0.17]) {
    const f = new THREE.Mesh(new THREE.ConeGeometry(0.1, 0.6, 8), new THREE.MeshBasicMaterial({ color: 0xffa040 }));
    f.position.set(x, -0.7, 0); f.rotation.x = Math.PI; f.visible = false; pack.add(f); flames.push(f);
  }

  const tongueMesh = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.08, 1, 8), mat(0xff6f9d));
  tongueMesh.visible = false;
  tongueMesh.userData.noBake = true;
  scene.add(tongueMesh);

  // Dizzy stars orbiting the head after a hard landing
  const stars = new THREE.Group();
  const starShape = new THREE.Shape();
  for (let k = 0; k < 10; k++) { const r = k % 2 ? 0.05 : 0.12, a = k / 10 * TAU; k ? starShape.lineTo(Math.cos(a) * r, Math.sin(a) * r) : starShape.moveTo(r, 0); }
  const starGeo = new THREE.ExtrudeGeometry(starShape, { depth: 0.03, bevelEnabled: false });
  for (let k = 0; k < 4; k++) { const s = new THREE.Mesh(starGeo, mat(0xffd23f, { emissive: 0x806000 })); stars.add(s); }
  stars.position.set(0, 0.45, 0.1); stars.visible = false;
  P.head.add(stars);

  // ---------- Ragdoll pieces (separate model instance) ----------
  const R = G.makeGoatParts();
  function wrap(part, ox, oy, oz) {
    const w = new THREE.Group();
    part.position.set(ox, oy, oz);
    w.add(part); outline(part, 0.022);
    w.visible = false; w.userData.noBake = true; scene.add(w);
    return w;
  }
  const rv = {
    torso: wrap(R.torso, 0, 0, 0),
    neck: wrap(R.neck, 0, 0, -0.25),
    head: wrap(R.head, 0, 0, -0.25),
    legs: R.legs.map(l => wrap(l, 0, 0.29, 0))
  };
  G.bakeRig(R.torso, [R.torso, R.tail], []);
  G.bakeRig(R.neck, [R.neck], []);
  G.bakeRig(R.head, [R.head, ...R.ears], [...R.eyes, R.mouth]);
  R.legs.forEach((l, i) => G.bakeRig(l, [l, R.knees[i]], []));
  // straighten ragdoll legs (knees stay as modelled)
  R.legs.forEach(l => l.rotation.set(0, 0, 0));

  // ---------- Main physics body ----------
  const body = new CANNON.Body({ mass: 40, fixedRotation: true, linearDamping: 0.01 });
  body.addShape(new CANNON.Sphere(0.6));
  body.position.copy(G.spawn);
  body.tag = { kind: 'goat' };
  body.allowSleep = false;
  world.addBody(body);

  const goat = G.goat = {
    body, root, head: P.head, squash: 0, yaw: Math.PI, rag: 0, ragManual: false, ragActive: false, ragLen: 0,
    grounded: false, coyote: 0, airT: 0, spin: 0, peakY: 0, butt: 0, buttCd: 0, bleatT: 0, dustT: 0, dizzy: 0,
    tongue: null, jetpack: false, fuel: 1, jetting: false,
    neckV: 0, earV: 0, earA: 0, eyeV: [0, 0], eye: [0, 0]
  };
  let rb = null; // ragdoll bodies {torso, neck, head, legs[], all[], joints[]}

  body.addEventListener('collide', e => {
    const v = G.impactSpeed(e);
    if (v > 9) {
      G.sfx.bonk(v, body.position);
      if (v > 16) G.after.push(() => goat.ragdoll(1.5));
    }
    if (e.body?.tag?.kind === 'ramp') goat.onRamp = 0.25;
  });

  function boxBody(hx, hy, hz, mass) {
    const b = new CANNON.Body({ mass, linearDamping: 0.05, angularDamping: 0.25 });
    b.addShape(new CANNON.Box(new V3(hx, hy, hz)));
    b.collisionFilterGroup = 4; b.collisionFilterMask = 1;
    b.goatPart = true; b.allowSleep = false;
    b.tag = { kind: 'goatpart' };
    b.addEventListener('collide', e => { const v = G.impactSpeed(e); if (v > 8) G.sfx.bonk(v * 0.7); });
    return b;
  }
  // Place a body at a model part's current world pose (+ local offset to the body's centre)
  const tmpP = new THREE.Vector3(), tmpQ = new THREE.Quaternion();
  function placeFrom(b, obj, ox, oy, oz) {
    obj.getWorldPosition(tmpP); obj.getWorldQuaternion(tmpQ);
    const off = new THREE.Vector3(ox, oy, oz).applyQuaternion(tmpQ);
    b.position.set(tmpP.x + off.x, tmpP.y + off.y, tmpP.z + off.z);
    b.quaternion.set(tmpQ.x, tmpQ.y, tmpQ.z, tmpQ.w);
  }

  function buildRagdoll() {
    goat.release();
    root.updateMatrixWorld(true);
    const torso = boxBody(0.3, 0.32, 0.62, 22), neck = boxBody(0.12, 0.12, 0.26, 3), head = boxBody(0.18, 0.2, 0.3, 5);
    const legs = [0, 1, 2, 3].map(() => boxBody(0.07, 0.29, 0.07, 1.5));
    placeFrom(torso, P.torso, 0, 0, 0);
    placeFrom(neck, P.neck, 0, 0, 0.25);
    placeFrom(head, P.head, 0, 0, 0.25);
    legs.forEach((l, i) => placeFrom(l, P.legs[i], 0, -0.29, 0));
    const all = [torso, neck, head, ...legs];
    for (const b of all) {
      b.velocity.copy(body.velocity);
      b.velocity.x += (Math.random() - 0.5) * 3; b.velocity.z += (Math.random() - 0.5) * 3;
      world.addBody(b);
    }
    torso.angularVelocity.set((Math.random() - 0.5) * 14, (Math.random() - 0.5) * 8, (Math.random() - 0.5) * 14);
    const j = (a, pa, b, pb) => { const c = new CANNON.PointToPointConstraint(a, pa, b, pb); world.addConstraint(c); return c; };
    const joints = [
      j(torso, new V3(L.NECK_AT.x, L.NECK_AT.y, L.NECK_AT.z), neck, new V3(0, 0, -0.25)),
      j(neck, new V3(0, 0, 0.25), head, new V3(0, 0, -0.25)),
      ...legs.map((l, i) => j(torso, new V3(L.HIPS[i][0], L.HIP_Y, L.HIPS[i][1]), l, new V3(0, 0.29, 0)))
    ];
    rb = { torso, neck, head, legs, all, joints };
    world.removeBody(body);
    goat.ragActive = true; goat.ragLen = 0;
    rig.visible = false;
    for (const w of [rv.torso, rv.neck, rv.head, ...rv.legs]) w.visible = true;
    syncRagdoll();
    if (Math.random() < 0.7) G.sfx.baa();
    G.vibrate?.(30);
  }

  function syncRagdoll() {
    const pairs = [[rv.torso, rb.torso], [rv.neck, rb.neck], [rv.head, rb.head], ...rv.legs.map((w, i) => [w, rb.legs[i]])];
    for (const [w, b] of pairs) { w.position.set(b.position.x, b.position.y, b.position.z); w.quaternion.set(b.quaternion.x, b.quaternion.y, b.quaternion.z, b.quaternion.w); }
    body.position.copy(rb.torso.position);
    body.velocity.copy(rb.torso.velocity);
  }

  function endRagdoll() {
    const t = rb.torso;
    const f = t.quaternion.vmult(new V3(0, 0, 1));
    if (Math.abs(f.x) + Math.abs(f.z) > 0.1) goat.yaw = Math.atan2(f.x, f.z);
    body.position.set(t.position.x, Math.max(t.position.y + 0.5, 0.7), t.position.z);
    body.velocity.set(0, 6, 0);
    for (const c of rb.joints) world.removeConstraint(c);
    for (const b of rb.all) world.removeBody(b);
    world.addBody(body);
    rb = null;
    goat.ragActive = false; goat.ragManual = false; goat.rag = 0;
    rig.visible = true;
    for (const w of [rv.torso, rv.neck, rv.head, ...rv.legs]) w.visible = false;
    goat.squash = -0.8;
    if (goat.ragLen > 1.2) goat.dizzy = 2.4;
  }

  goat.ragdoll = function (t) {
    if (t === 'toggle') {
      if (goat.ragActive && goat.ragManual) { goat.ragManual = false; goat.rag = 0.01; return; }
      goat.ragManual = true; t = 9999;
    }
    goat.rag = Math.max(goat.rag, t);
    if (!goat.ragActive) goat.pendingRag = true;
  };
  goat.fling = function (v) {
    if (rb) for (const b of rb.all) b.velocity.set(v.x + (Math.random() - 0.5) * 4, v.y + (Math.random() - 0.5) * 4, v.z + (Math.random() - 0.5) * 4);
    else body.velocity.copy(v);
  };
  goat.bounce = function (vy) {
    if (rb) for (const b of rb.all) b.velocity.y = Math.max(b.velocity.y, vy * 0.8);
    else { body.velocity.y = Math.max(body.velocity.y, vy); goat.squash = 1; }
  };

  goat.respawn = function () {
    goat.release();
    if (rb) endRagdoll();
    goat.pendingRag = false; goat.rag = 0;
    body.position.copy(G.spawn);
    body.velocity.set(0, 0, 0);
    G.sfx.baa();
  };

  function facing() { return new V3(Math.sin(goat.yaw), 0, Math.cos(goat.yaw)); }
  function mouthPos() {
    const v = new THREE.Vector3(0, -0.1, 0.6);
    (rb ? R.head : P.head).localToWorld(v);
    return new V3(v.x, v.y, v.z);
  }

  // ---------- Headbutt ----------
  goat.headbutt = function () {
    if (goat.buttCd > 0 || goat.ragActive) return;
    goat.buttCd = 0.45; goat.butt = 1;
    const f = facing();
    const c = body.position.vadd(f.scale(1.4));
    let hits = 0;
    for (const o of world.bodies.slice()) {
      if (o === body || o.mass === 0 || o.goatPart) continue;
      if (o.position.vsub(c).length() > 2.4) continue;
      hits++;
      o.wakeUp();
      // Mass is mostly ignored on purpose. Cars should fly too.
      const k = o.mass > 300 ? 0.6 : 1;
      o.velocity.set(o.velocity.x + f.x * 22 * k, o.velocity.y + 9 * k + Math.random() * 4, o.velocity.z + f.z * 22 * k);
      o.angularVelocity.set((Math.random() - 0.5) * 16, (Math.random() - 0.5) * 16, (Math.random() - 0.5) * 16);
      if (o.human) { o.human.knock(); continue; }
      if (o.car) { o.car.wreck(); G.addScore('CAR FLIP', 450, 'big'); continue; }
      if (o.tag?.kind === 'barrel') { G.after.push(() => G.explodeBarrel(o)); continue; }
      if (o.tag?.duck) G.sfx.quack?.();
      if (o.tag?.pts) G.addScore(o.tag.name || 'SMASH', o.tag.pts);
    }
    G.puff(c, 0xfff7e0, 4);
    if (hits) { G.sfx.bonk(14); G.shake = Math.min(1.2, G.shake + 0.25); G.vibrate?.(25); body.velocity.x -= f.x * 4; body.velocity.z -= f.z * 4; }
    else G.sfx.whoosh();
  };

  // ---------- Bleat: scares people, shoves small things ----------
  goat.bleat = function () {
    if (goat.bleatT > 0) return;
    goat.bleatT = 0.6;
    G.sfx.baa(true);
    const p = body.position;
    let scared = 0;
    for (const h of G.humans) if (h.body.position.distanceTo(p) < 16 && h.state === 'walk') { h.scare(); scared++; }
    for (const o of world.bodies) {
      if (o.mass === 0 || o.mass > 10 || o.goatPart) continue;
      const d = o.position.vsub(p), dist = d.length();
      if (dist > 5 || dist < 0.01) continue;
      d.normalize(); o.wakeUp();
      o.velocity.vadd(d.scale((5 - dist) * 2), o.velocity); o.velocity.y += 2;
    }
    if (scared) G.addScore(scared > 2 ? 'MASS PANIC' : 'BAAA', 30 * scared);
  };

  // ---------- Tongue ----------
  goat.lick = function () {
    if (goat.tongue) { goat.release(true); return; }
    if (goat.ragActive) return;
    const from = mouthPos(), f = facing();
    let best = null, bestD = 4.2;
    for (const o of world.bodies) {
      if (o === body || o === G.groundBody || o.mass === 0 || o.goatPart) continue;
      const d = o.position.vsub(from);
      if (d.x * f.x + d.z * f.z < -0.3) continue;
      const dist = d.length();
      if (dist < bestD) { bestD = dist; best = { o, p: o.position.clone() }; }
    }
    if (!best) {
      // lick a wall, tree or house straight ahead and hang off it
      const res = new CANNON.RaycastResult();
      world.raycastClosest(from, from.vadd(f.scale(3.5)).vadd(new V3(0, 0.3, 0)), { skipBackfaces: true, collisionFilterMask: 1 }, res);
      if (res.hasHit && res.body !== body && res.body !== G.groundBody) best = { o: res.body, p: res.hitPointWorld.clone() };
    }
    if (!best) { G.sfx.slurp(); return; }
    goat.tongue = { o: best.o, local: best.o.pointToLocalFrame(best.p), len: Math.max(1.6, Math.min(3.2, best.p.distanceTo(from))) };
    best.o.wakeUp();
    G.sfx.slurp();
    G.addScore(best.o.mass === 0 ? 'LICKED A WALL' : best.o.human ? 'UNWANTED LICK' : best.o.car ? 'LICKED A CAR' : 'LICK', best.o.mass === 0 ? 50 : 25);
  };
  goat.release = function (yeet) {
    if (!goat.tongue) return;
    const o = goat.tongue.o;
    if (yeet && o.mass > 0 && o.velocity.length() > 8) { G.addScore('YEET', 120, 'pink'); G.sfx.whoosh(); }
    goat.tongue = null;
    tongueMesh.visible = false;
  };

  // Spring between the mouth and the licked point. Stretchy on purpose.
  goat.prePhysics = function () {
    const t = goat.tongue;
    if (!t || rb) return;
    const o = t.o;
    if (!world.bodies.includes(o)) { goat.release(); return; }
    const a = mouthPos(), b = o.pointToWorldFrame(t.local);
    const d = b.vsub(a), dist = d.length();
    if (dist > 14) { goat.release(); G.addScore('TONGUE SNAPPED', 60); return; }
    if (dist <= t.len) return;
    d.normalize();
    const relV = o.velocity.vsub(body.velocity).dot(d);
    const fmag = Math.max(0, Math.min(900 * (dist - t.len) + 60 * relV, 26000));
    const F = d.scale(fmag);
    if (o.mass > 0) { o.applyForce(F.negate(), b); o.wakeUp(); }
    body.applyForce(F.scale(o.mass > 0 ? Math.min(1, o.mass / 120) : 1), a);
  };

  // ---------- Per-frame update ----------
  goat.update = function (dt, input, camYaw, time) {
    goat.buttCd -= dt; goat.butt = Math.max(0, goat.butt - dt * 4);
    goat.bleatT -= dt; goat.onRamp = (goat.onRamp || 0) - dt;
    if (goat.pendingRag) { goat.pendingRag = false; if (!rb) buildRagdoll(); }

    // Ground contact (main body, or any ragdoll piece)
    let g = false;
    for (const c of world.contacts) {
      if (rb) { if ((c.bi.goatPart && !c.bj.goatPart) || (c.bj.goatPart && !c.bi.goatPart)) g = true; }
      else if ((c.bi === body && -c.ni.y > 0.5) || (c.bj === body && c.ni.y > 0.5)) g = true;
    }
    if (g) goat.coyote = 0.12; else goat.coyote -= dt;
    const wasGrounded = goat.grounded;
    goat.grounded = goat.coyote > 0;

    // Trick tracking
    if (!goat.grounded) {
      goat.airT += dt;
      goat.peakY = Math.max(goat.peakY, body.position.y);
      if (rb) goat.spin += Math.min(9, rb.torso.angularVelocity.length()) * dt;
    } else if (!wasGrounded || goat.airT > 0) {
      const fall = goat.peakY - body.position.y;
      const flips = Math.floor(goat.spin / TAU);
      if (flips >= 1) { G.addScore(flips > 3 ? 'SPIN CYCLE x' + flips : 'FLIP x' + flips, 100 * flips, flips > 2 ? 'big' : ''); G.stats.maxFlips = Math.max(G.stats.maxFlips, flips); }
      if (goat.airT > 1.6) G.addScore('AIR TIME ' + goat.airT.toFixed(1) + 's', Math.round(goat.airT * 80));
      if (fall > 12) G.addScore('GRAVITY WORKS', Math.round(fall * 10));
      if (goat.airT > 0.4) { goat.squash = Math.min(1, goat.airT * 0.6); G.puff(body.position.vadd(new V3(0, -0.5, 0)), 0xe8d9b5, 3); }
      goat.airT = 0; goat.spin = 0; goat.peakY = body.position.y;
    }

    // Ragdoll timer
    if (rb) {
      goat.ragLen += dt;
      goat.rag -= dt;
      if (goat.rag <= 0) {
        if (rb.torso.velocity.length() > 4 && !goat.ragManual && goat.ragLen < 8) goat.rag = 0.2;
        else endRagdoll();
      }
    }

    // Movement
    const len = Math.hypot(input.x, input.z);
    if (!rb) {
      const top = input.sprint ? 13 : 7.5;
      const sx = input.x * Math.cos(camYaw) + input.z * Math.sin(camYaw);
      const sz = -input.x * Math.sin(camYaw) + input.z * Math.cos(camYaw);
      const dirX = len ? sx / len : 0, dirZ = len ? sz / len : 0;
      if (len > 0.1 || goat.grounded) {
        const k = goat.grounded ? 12 : 2.5, m = Math.min(1, len);
        body.velocity.x += (dirX * top * m - body.velocity.x) * Math.min(1, k * dt);
        body.velocity.z += (dirZ * top * m - body.velocity.z) * Math.min(1, k * dt);
      }
      if (len > 0.1) {
        let dA = Math.atan2(dirX, dirZ) - goat.yaw;
        dA = Math.atan2(Math.sin(dA), Math.cos(dA));
        goat.yaw += dA * Math.min(1, dt * 12);
      }
      if (input.jump && goat.grounded) {
        body.velocity.y = 11.5; goat.coyote = 0; goat.grounded = false;
        goat.squash = -0.6;
        if (Math.random() < 0.25) G.sfx.baa();
      }
      if (goat.onRamp > 0 && len > 0.1) {
        body.velocity.x *= 1 + dt * 2.2; body.velocity.z *= 1 + dt * 2.2;
        body.velocity.y = Math.max(body.velocity.y, 3);
      }
      // dust when sprinting
      goat.dustT -= dt;
      if (goat.grounded && input.sprint && len > 0.5 && goat.dustT <= 0) { goat.dustT = 0.09; G.puff(body.position.vadd(new V3(0, -0.5, 0)), 0xe8d9b5, 1, 0.15); }
    }

    // Jetpack (works in ragdoll too, which is worse and therefore better)
    goat.jetting = goat.jetpack && input.jet && goat.fuel > 0;
    if (goat.jetting) {
      const f = facing();
      const tgt = rb ? rb.torso : body;
      tgt.velocity.y = Math.min(tgt.velocity.y + 48 * dt, 15);
      tgt.velocity.x += f.x * 14 * dt; tgt.velocity.z += f.z * 14 * dt;
      goat.fuel = Math.max(0, goat.fuel - dt * 0.18);
      G.sfx.jet();
      if (Math.random() < 0.5) G.puff(body.position.vadd(new V3(-f.x * 0.6, 0, -f.z * 0.6)), 0xffa040, 1);
    } else if (goat.jetpack && goat.grounded) goat.fuel = Math.min(1, goat.fuel + dt * 0.35);
    for (const fl of flames) { fl.visible = goat.jetting; fl.scale.y = 0.7 + Math.random() * 0.8; }

    // Pickups
    const p = new THREE.Vector3(body.position.x, body.position.y, body.position.z);
    if (G.jetpackPickup.visible && G.jetpackPickup.position.distanceTo(p) < 2.2) {
      G.jetpackPickup.visible = false; goat.jetpack = true; pack.visible = true;
      G.sfx.pickup(); G.addScore('JETPACK GET!', 1000, 'big');
      G.onJetpack?.();
    }
    for (const s of G.statues) {
      if (!s.got && s.m.position.distanceTo(p) < 2.4) { s.got = true; scene.remove(s.m); G.onStatue(); }
    }
    if (body.position.y < -30) goat.respawn();

    if (rb) syncRagdoll();
    animate(dt, input, time, len);
  };

  // ---------- Animation: squash, gait, floppy neck and ears, googly eyes, tongue ----------
  function animate(dt, input, time, len) {
    root.position.set(body.position.x, body.position.y, body.position.z);
    const sp = Math.hypot(body.velocity.x, body.velocity.z);
    if (!rb) {
      rig.rotation.set(Math.min(0.5, -body.velocity.y * 0.025) + Math.sin(time * 20) * Math.min(sp, 13) * 0.004, goat.yaw, Math.sin(time * 9) * Math.min(sp, 13) * 0.01);
      goat.squash += (0 - goat.squash) * Math.min(1, dt * 8);
      const s = goat.squash;
      rig.scale.set(1 + s * 0.35, 1 - s * 0.4, 1 + s * 0.2);
      const flail = !goat.grounded && input.jumpHeld;
      const freq = 6 + sp * 1.3, amp = Math.min(1, sp / 6);
      for (let i = 0; i < 4; i++) {
        const ph = i === 0 || i === 3 ? 0 : Math.PI;
        const leg = P.legs[i], knee = P.knees[i];
        if (flail) { leg.rotation.x = Math.sin(time * 34 + i * 1.7) * 1.4; leg.rotation.z = Math.cos(time * 29 + i) * 0.9; knee.rotation.x = 0.5; }
        else if (!goat.grounded) { leg.rotation.x = i < 2 ? -0.9 : 0.9; leg.rotation.z = 0; knee.rotation.x = i < 2 ? 0.9 : -0.6; }
        else {
          const sw = Math.sin(time * freq + ph);
          leg.rotation.x = sw * amp * 0.8; leg.rotation.z = 0;
          knee.rotation.x = Math.max(0, -Math.cos(time * freq + ph)) * amp * (i < 2 ? 1.1 : -0.9);
        }
      }
      rig.position.y = goat.grounded ? Math.abs(Math.sin(time * freq)) * amp * 0.07 : 0;
      P.tail.rotation.z = Math.sin(time * 18) * 0.6;

      // Neck is a damped spring driven by vertical speed
      const target = -0.75 - body.velocity.y * 0.03;
      goat.neckV += ((target - P.neck.rotation.x) * 120 - goat.neckV * 7) * dt;
      P.neck.rotation.x += goat.neckV * dt;
      P.neck.rotation.y = Math.sin(time * 3) * 0.05;
      const lunge = Math.sin(goat.butt * Math.PI);
      P.neck.scale.z = 1 + lunge * 1.4 + (goat.tongue ? 0.3 : 0);
      P.neck.position.z = L.NECK_AT.z + lunge * 0.3;
      // Ears flap with vertical speed
      goat.earV += ((body.velocity.y * 0.06 - goat.earA) * 90 - goat.earV * 5) * dt;
      goat.earA += goat.earV * dt;
      P.ears.forEach((e, k) => { e.rotation.z = (k ? 1 : -1) * (-0.6 + Math.max(-0.8, Math.min(0.9, goat.earA))); });
      P.mouth.scale.y = goat.bleatT > 0 || goat.tongue ? 5 : 1;
    } else {
      // ragdoll legs and tail keep kicking
      R.knees.forEach((k, i) => { k.rotation.x = Math.sin(time * 30 + i * 2) * 0.9; });
      R.tail.rotation.z = Math.sin(time * 25) * 0.8;
      R.mouth.scale.y = 5;
    }

    // Googly eyes lag behind the head
    for (let k = 0; k < 2; k++) {
      const drive = k === 0 ? -body.velocity.x * 0.004 : body.velocity.y * 0.004;
      goat.eyeV[k] += ((drive - goat.eye[k]) * 300 - goat.eyeV[k] * 6) * dt;
      goat.eye[k] = Math.max(-0.05, Math.min(0.05, goat.eye[k] + goat.eyeV[k] * dt));
    }
    for (const set of [P.pupils, R.pupils]) for (const p of set) {
      p.position.y = goat.eye[1]; p.position.x = goat.eye[0];
      p.rotation.z = rb || goat.dizzy > 0 ? time * 12 : 0;
    }

    goat.dizzy -= dt;
    stars.visible = goat.dizzy > 0 && !rb;
    if (stars.visible) stars.children.forEach((s, k) => { const a = time * 5 + k * TAU / 4; s.position.set(Math.cos(a) * 0.35, Math.sin(time * 7 + k) * 0.05, Math.sin(a) * 0.35); s.rotation.y = a; });

    if (goat.tongue) {
      const a = mouthPos(), b = goat.tongue.o.pointToWorldFrame(goat.tongue.local);
      const av = new THREE.Vector3(a.x, a.y, a.z), bv = new THREE.Vector3(b.x, b.y, b.z);
      const len2 = av.distanceTo(bv);
      tongueMesh.visible = true;
      tongueMesh.position.copy(av).add(bv).multiplyScalar(0.5);
      const thin = 1 / Math.max(1, len2 * 0.3);
      tongueMesh.scale.set(thin, len2, thin);
      tongueMesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), bv.sub(av).normalize());
    }
  }
})();
