// Car jacking and driving: the goat climbs onto a car's roof and drives it like it stole it.
// Arcade handling on top of the physics body: we own speed and heading, physics owns gravity and crashes.
// Also: boost pads that fling goats and cars.
(function () {
  const { world, scene, mat } = G;
  const V3 = CANNON.Vec3;
  const goat = G.goat;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const ROOF = new V3(0, 1.67, -0.35);

  const D = G.drive = { car: null, yaw: 0, speed: 0, steer: 0, boostT: 0, nitro: 1, airT: 0, grounded: true, slip: 0, driftT: 0, hopCd: 0, hitCd: new Map(), dist: 0 };

  D.nearest = function () {
    if (D.car || goat.ragActive) return null;
    let best = null, bd = 6.5;
    for (const c of G.cars) {
      if (c.wrecked) continue;
      const d = c.body.position.distanceTo(goat.body.position);
      if (d < bd) { bd = d; best = c; }
    }
    return best;
  };

  function yawOf(body) {
    const f = body.quaternion.vmult(new V3(0, 0, 1));
    return Math.atan2(f.x, f.z);
  }

  D.enter = function (car) {
    car = car || D.nearest();
    if (!car || D.car) return false;
    goat.release && goat.release();
    D.car = car; car.driven = true; car.parkedT = 0;
    D.yaw = yawOf(car.body);
    const v = car.body.velocity;
    D.speed = v.x * Math.sin(D.yaw) + v.z * Math.cos(D.yaw);
    D.nitro = Math.max(D.nitro, 0.5);
    world.removeBody(goat.body);
    goat.riding = true;
    G.stats.carjacks = (G.stats.carjacks || 0) + 1;
    G.sfx.carDoor(); G.sfx.baa(true);
    G.addScore('CARJACKED', 300, 'big');
    G.vibrate?.([20, 30, 20]);
    D.onChange && D.onChange();
    return true;
  };

  // crash: thrown off in a ragdoll. quiet: used by respawn.
  D.exit = function (crash, quiet) {
    const car = D.car;
    if (!car) return;
    D.car = null; goat.riding = false;
    car.driven = false;
    if (!car.wrecked) car.parkedT = 20;
    const b = car.body;
    // Thrown out to the side and up (world-up: a wrecked car may be upside down), clear of the car so a
    // tumbling 900 kg wreck can't land on the goat and squash it through the ground.
    const sx = Math.cos(D.yaw), sz = -Math.sin(D.yaw);
    goat.body.position.set(b.position.x + sx * 2.6, Math.max(1.6, b.position.y + 1.8), b.position.z + sz * 2.6);
    const k = crash ? 0.8 : 0.4;
    goat.body.velocity.set(b.velocity.x * k + sx * 6, crash ? 12 : 7, b.velocity.z * k + sz * 6);
    world.addBody(goat.body);
    goat.rideEnd && goat.rideEnd();
    G.sfx.drive(false, 0, 0, 0);
    if (!quiet) {
      if (crash) { goat.ragdoll(2.5); G.addScore('EJECTED', 300, 'big'); G.sfx.baa(true); }
      else { G.sfx.carDoor(); G.sfx.whoosh(); }
    }
    D.onChange && D.onChange();
  };

  // Bring a car to the goat: the nearest free one pulls up alongside, parked.
  D.summon = function () {
    if (D.car || goat.ragActive) return false;
    const gp = goat.body.position;
    let best = null, bd = Infinity;
    for (const c of G.cars) {
      if (c.wrecked) continue;
      const d = c.body.position.distanceTo(gp) - (c.spot ? 1000 : 0);
      if (d < bd) { bd = d; best = c; }
    }
    if (!best) return false;
    // Find a clear spot around the goat (right, left, behind, ahead; near then farther) so the car never
    // appears inside a wall, a tree or another car and gets flung out by the physics.
    // Everything counts as in the way, loose props too: dropping a car on a barrel is a bad day.
    const blockers = world.bodies.filter(o => o !== G.groundBody && o !== best.body && o !== goat.body && !o.goatPart);
    for (const o of blockers) if (o.aabbNeedsUpdate) o.computeAABB();
    const clear = (x, z) => blockers.every(o => o.aabb.upperBound.x < x - 2.6 || o.aabb.lowerBound.x > x + 2.6 || o.aabb.upperBound.z < z - 2.6 || o.aabb.lowerBound.z > z + 2.6 || o.aabb.lowerBound.y > 2.2);
    const fx = Math.sin(goat.yaw), fz = Math.cos(goat.yaw), rx = Math.cos(goat.yaw), rz = -Math.sin(goat.yaw);
    let spot = null;
    for (const dist of [3.6, 5.5, 8, 11]) {
      for (const [dx, dz] of [[rx, rz], [-rx, -rz], [-fx, -fz], [fx, fz]]) {
        const x = gp.x + dx * dist, z = gp.z + dz * dist;
        if (clear(x, z)) { spot = [x, z]; break; }
      }
      if (spot) break;
    }
    if (!spot) return false;
    const b = best.body;
    b.position.set(spot[0], 1.2, spot[1]);
    b.velocity.set(0, 0, 0); b.angularVelocity.set(0, 0, 0);
    b.quaternion.setFromAxisAngle(new V3(0, 1, 0), goat.yaw);
    best.parkedT = 25;
    G.puff(b.position, 0xfff7e0, 10);
    G.sfx.honk(b.position); G.sfx.whoosh(b.position);
    return true;
  };

  D.hop = function () {
    if (!D.car || !D.grounded || D.hopCd > 0) return;
    D.hopCd = 0.6;
    D.car.body.velocity.y = 10;
    G.sfx.boing(D.car.body.position);
  };
  D.boost = function () {
    if (!D.car || D.nitro < 0.25) return;
    D.nitro -= 0.25; D.boostT = 1.4;
    D.speed = Math.max(D.speed, 24);
    G.sfx.nitro();
    G.shake = Math.min(1, G.shake + 0.3);
    G.addScore('NITRO', 40);
  };
  D.honk = function () {
    if (!D.car) return;
    G.sfx.honk(D.car.body.position);
    let n = 0;
    for (const h of G.humans) if (h.body.position.distanceTo(D.car.body.position) < 18 && h.state === 'walk') { h.scare(); n++; }
    if (n) G.addScore(n > 2 ? 'ROAD RAGE' : 'BEEP BEEP', 25 * n);
  };

  // Points for running into things while driving.
  for (const c of G.cars) {
    c.body.addEventListener('collide', e => {
      if (!c.driven) return;
      const o = e.body, v = G.impactSpeed(e);
      if (!o || o === G.groundBody || v < 4) return;
      const now = performance.now();
      if ((D.hitCd.get(o.id) || 0) > now) return;
      D.hitCd.set(o.id, now + 1500);
      if (o.human) { G.after.push(() => { o.human.knock(true); G.addScore(['HIT AND RUN', 'NO BRAKES', 'PEDESTRIAN BOWLING', 'INSURANCE FRAUD'][Math.floor(Math.random() * 4)], 250, 'pink'); }); }
      else if (o.car) G.addScore('FENDER BENDER', 200);
      else if (o.tag?.pts) G.addScore(o.tag.name || 'SMASH', o.tag.pts * 2);
      if (o.mass > 0 && o.mass < 300) { o.wakeUp(); o.velocity.y += 4 + v * 0.3; }
    });
  }

  // ---------- Per-frame driving ----------
  const fwd = new V3();
  D.update = function (dt, input) {
    const car = D.car;
    if (!car) return;
    if (car.wrecked) { D.exit(true); return; }
    const b = car.body;
    D.hopCd -= dt;
    b.wakeUp();

    let g = false;
    for (const c of world.contacts) {
      if ((c.bi === b && -c.ni.y > 0.5) || (c.bj === b && c.ni.y > 0.5)) { g = true; break; }
    }
    const wasGrounded = D.grounded;
    D.grounded = g;

    const throttle = clamp(input.z, -1, 1), steerIn = clamp(input.x, -1, 1);
    D.boostT -= dt;
    D.nitro = Math.min(1, D.nitro + dt * 0.06);
    const max = D.boostT > 0 ? 38 : 27;

    if (g) {
      if (!wasGrounded && D.airT > 0.6) {
        G.addScore('CAR AIR ' + D.airT.toFixed(1) + 's', Math.round(D.airT * 150), D.airT > 1.5 ? 'big' : '');
        G.stats.carAir = Math.max(G.stats.carAir || 0, D.airT);
        G.shake = Math.min(1.2, G.shake + 0.4);
        G.sfx.hit(16, 'metal', b.position);
        G.puff(b.position.vadd(new V3(0, -0.6, 0)), 0xe8d9b5, 6);
      }
      D.airT = 0;
      if (throttle > 0.1) D.speed += (D.speed < 0 ? 40 : 16) * throttle * dt;
      else if (throttle < -0.1) D.speed -= (D.speed > 0.5 ? 34 : 12) * -throttle * dt;
      else D.speed *= Math.pow(0.45, dt);
      if (D.boostT > 0) D.speed += 30 * dt;
      D.speed = clamp(D.speed, -11, max);

      // Steering scales with speed (no spinning on the spot) and flips in reverse.
      const turn = 2.3 * clamp(Math.abs(D.speed) / 7, 0, 1) * Math.sign(D.speed || 1);
      D.steer += (steerIn - D.steer) * Math.min(1, dt * 8);
      D.yaw += D.steer * turn * dt;

      // Grip: hard turns at speed let the back end slide out.
      const hard = Math.abs(D.steer) > 0.7 && Math.abs(D.speed) > 15;
      const grip = hard ? 3.2 : 9;
      const tx = Math.sin(D.yaw) * D.speed, tz = Math.cos(D.yaw) * D.speed;
      b.velocity.x += (tx - b.velocity.x) * Math.min(1, grip * dt);
      b.velocity.z += (tz - b.velocity.z) * Math.min(1, grip * dt);
      fwd.set(Math.sin(D.yaw), 0, Math.cos(D.yaw));
      D.slip = Math.abs(b.velocity.x * fwd.z - b.velocity.z * fwd.x);
      if (D.slip > 4) {
        D.driftT += dt;
        if (Math.random() < dt * 25) G.puff(b.pointToWorldFrame(new V3((Math.random() - 0.5) * 2, -0.7, -2)), 0xdddddd, 1, 0.3);
      } else {
        if (D.driftT > 0.8) G.addScore('DRIFT ' + D.driftT.toFixed(1) + 's', Math.round(D.driftT * 90));
        D.driftT = 0;
      }
    } else {
      D.airT += dt;
      D.slip = 0;
      D.yaw += steerIn * 1.2 * dt; // a little air steering, because goats
    }

    // Upright, heading-locked body with a little nose pitch in the air.
    const pitch = g ? 0 : clamp(-b.velocity.y * 0.025, -0.4, 0.4);
    const qy = new CANNON.Quaternion(); qy.setFromAxisAngle(new V3(0, 1, 0), D.yaw);
    const qx = new CANNON.Quaternion(); qx.setFromAxisAngle(new V3(1, 0, 0), pitch);
    b.quaternion.copy(qy.mult(qx));
    b.angularVelocity.set(0, 0, 0);
    if (b.position.y < -20) { b.position.set(b.position.x * 0.5, 3, b.position.z * 0.5); b.velocity.set(0, 0, 0); }

    for (const w of car.wheels) w.rotation.x += D.speed / 0.45 * dt;
    D.dist += Math.abs(D.speed) * dt;
    G.sfx.drive(true, D.speed, throttle, D.slip);
    if (D.boostT > 0 && Math.random() < 0.6) G.puff(b.pointToWorldFrame(new V3((Math.random() - 0.5) * 0.8, -0.2, -2.4)), Math.random() < 0.5 ? 0xffa040 : 0x6b6470, 1, 0.3);
  };

  // ---------- Boost pads ----------
  const padMat = new THREE.MeshBasicMaterial({ color: 0x40e0ff });
  const padArrow = new THREE.Shape();
  padArrow.moveTo(0, 1); padArrow.lineTo(1, -0.2); padArrow.lineTo(0.45, -0.2); padArrow.lineTo(0.45, -1); padArrow.lineTo(-0.45, -1); padArrow.lineTo(-0.45, -0.2); padArrow.lineTo(-1, -0.2); padArrow.closePath();
  const arrowGeo = new THREE.ShapeGeometry(padArrow);
  const pads = [];
  function pad(x, z, yaw) {
    const g = new THREE.Group();
    const base = new THREE.Mesh(new THREE.BoxGeometry(4, 0.08, 4), mat(0x2b1d3a)); base.position.y = 0.05; g.add(base);
    for (let k = 0; k < 2; k++) {
      const a = new THREE.Mesh(arrowGeo, padMat); a.rotation.x = -Math.PI / 2; a.position.set(0, 0.11, -0.8 + k * 1.5); a.scale.setScalar(0.9); g.add(a);
    }
    g.position.set(x, 0, z); g.rotation.y = yaw;
    g.userData.noBake = true;
    scene.add(g);
    pads.push({ g, x, z, dir: new V3(Math.sin(yaw), 0, Math.cos(yaw)), cd: 0 });
  }
  // In front of both ramps, and two on the ring road (one each way round).
  pad(-55, -76, 0);
  pad(46, -9, 0);
  pad(58, 0, Math.PI);
  pad(-58, 0, 0);
  G.pads = pads;

  D.updatePads = function (dt, time) {
    const glow = 0.6 + Math.sin(time * 8) * 0.4;
    padMat.color.setRGB(0.25 * glow, 0.88 * glow, 1);
    for (const p of pads) {
      p.cd -= dt;
      if (p.cd > 0) continue;
      if (D.car) {
        const cp = D.car.body.position;
        if (Math.hypot(cp.x - p.x, cp.z - p.z) < 2.8 && cp.y < 2) {
          p.cd = 1; D.boostT = 1.6; D.speed = Math.max(D.speed, 32);
          G.sfx.nitro(); G.addScore('ZOOM', 80); G.shake = Math.min(1, G.shake + 0.3);
        }
      } else if (!goat.ragActive) {
        const gp = goat.body.position;
        if (Math.hypot(gp.x - p.x, gp.z - p.z) < 2.3 && gp.y < 1.5) {
          p.cd = 1;
          const v = goat.body.velocity;
          const sp = Math.hypot(v.x, v.z);
          const dir = sp > 2 ? new V3(v.x / sp, 0, v.z / sp) : p.dir;
          goat.body.velocity.set(dir.x * 30, 9, dir.z * 30);
          G.sfx.nitro(); G.addScore('ZOOM', 80);
        }
      }
    }
  };
})();
