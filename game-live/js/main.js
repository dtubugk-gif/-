// Input (keyboard, mouse, touch), camera, scoring, challenges and the main loop.
(function () {
  const { renderer, scene, camera, world, sun } = G;
  const goat = G.goat;
  const $ = id => document.getElementById(id);
  const V3 = CANNON.Vec3;

  let isTouch = G.mobile || 'ontouchstart' in window;
  document.body.classList.toggle('touch', isTouch);
  G.vibrate = ms => { if (isTouch && navigator.vibrate) try { navigator.vibrate(ms); } catch (_) { /* not allowed */ } };
  let playing = false;

  // ---------- Scoring ----------
  let score = 0, combo = 0, comboT = 0, statues = 0;
  const feed = $('feed');
  G.addScore = function (label, pts, style) {
    if (!playing) return;
    combo = comboT > 0 ? combo + 1 : 1;
    comboT = 3;
    const mult = Math.min(10, 1 + Math.floor(combo / 3));
    const gained = pts * mult;
    score += gained;
    $('score').textContent = score.toLocaleString();
    $('combo').textContent = mult > 1 ? 'COMBO x' + mult : '';
    if (combo > 1) G.sfx.combo(combo);
    const el = document.createElement('div');
    el.className = 'pop' + (style ? ' ' + style : '');
    el.textContent = '+' + gained + ' ' + label;
    feed.prepend(el);
    while (feed.children.length > (isTouch ? 3 : 4)) feed.lastChild.remove();
    setTimeout(() => el.remove(), 1600);
  };
  G.onStatue = function () {
    statues++;
    $('statueCount').textContent = statues + '/' + G.statues.length;
    G.addScore('GOLDEN GOAT ' + statues + '/' + G.statues.length, 2000, 'big');
    G.vibrate([40, 40, 80]); G.sfx.golden(goat.body.position);
    if (statues === G.statues.length) setTimeout(() => G.addScore('YOU ARE THE GOAT', 10000, 'big'), 900);
  };
  G.onJetpack = function () { $('tJet').hidden = false; renderModes(); };

  // ---------- Challenges ----------
  const fallen = list => list.filter(b => b.quaternion.vmult(new V3(0, 1, 0)).y < 0.6).length;
  let maxY = 0;
  const missions = [
    { name: 'Topple every domino', goal: 30, get: () => fallen(G.stats.dominoes) },
    { name: 'Bowl a strike', goal: 10, get: () => fallen(G.stats.pins) },
    { name: 'Wreck cars', goal: 3, get: () => G.stats.carsWrecked },
    { name: 'Knock down people', goal: 15, get: () => G.stats.peopleDown },
    { name: 'Chain 5 explosions', goal: 5, get: () => { const t = performance.now(); return G.stats.booms.filter(b => t - b < 4000).length; } },
    { name: 'Land a 4x flip', goal: 4, get: () => G.stats.maxFlips },
    { name: 'Reach 35 m high', goal: 35, get: () => Math.floor(maxY) },
    { name: 'Steal a car', goal: 1, get: () => G.stats.carjacks || 0 },
    { name: 'Drive 1 km', goal: 1000, get: () => Math.floor(G.drive.dist) },
    { name: 'Get 2 s of car air', goal: 2, get: () => Math.floor(G.stats.carAir || 0) }
  ];
  missions.forEach(m => { m.best = 0; m.done = false; });
  function renderMissions() {
    const list = isTouch ? missions.filter(m => !m.done).slice(0, 2) : missions;
    const done = missions.filter(m => m.done).length;
    $('missions').innerHTML = '<h2>CHALLENGES ' + done + '/' + missions.length + '</h2>' + list.map(m =>
      '<div class="' + (m.done ? 'done' : '') + '"><span>' + (m.done ? '✔' : '•') + '</span><span>' + m.name + '</span><span>' + Math.min(m.best, m.goal) + '/' + m.goal + '</span></div>').join('');
  }
  function checkMissions() {
    maxY = Math.max(maxY, goat.body.position.y);
    let changed = false;
    for (const m of missions) {
      if (m.done) continue;
      const v = m.get();
      if (v > m.best) { m.best = v; changed = true; }
      if (m.best >= m.goal) { m.done = true; changed = true; G.addScore('CHALLENGE: ' + m.name.toUpperCase(), 1500, 'big'); G.sfx.challenge(); G.vibrate([30, 30, 30]); }
    }
    if (changed) renderMissions();
  }

  // ---------- Toggles ----------
  const modes = { moon: false, slow: false };
  function renderModes() {
    const m = [];
    m.push('<span class="' + (modes.moon ? 'on' : '') + '">G moon gravity</span>');
    m.push('<span class="' + (modes.slow ? 'on' : '') + '">T slow-mo</span>');
    if (goat.jetpack) m.push('<span class="on">' + (isTouch ? '' : 'J ') + 'jetpack ' + Math.round(goat.fuel * 100) + '%</span>');
    if (goat.ragManual) m.push('<span class="on">ragdolling</span>');
    if (G.drive && G.drive.car) m.push('<span class="on">' + (isTouch ? '' : 'F ') + 'nitro ' + Math.round(G.drive.nitro * 100) + '%</span>');
    $('mode').innerHTML = m.join('');
    $('mMoon').classList.toggle('on', modes.moon);
    $('mSlow').classList.toggle('on', modes.slow);
  }
  function toggleMoon() {
    modes.moon = !modes.moon;
    world.gravity.set(0, modes.moon ? -5 : -22, 0);
    G.addScore(modes.moon ? 'MOON GOAT' : 'EARTH GOAT', 10);
    renderModes();
  }
  function toggleSlow() { modes.slow = !modes.slow; G.sfx.slowmo(modes.slow); G.addScore(modes.slow ? 'BULLET TIME' : 'REAL TIME', 10); renderModes(); }
  let rainCd = 0;
  function barrelRain() {
    if (rainCd > 0) return;
    rainCd = 2.5;
    const p = goat.body.position;
    for (let i = 0; i < 7; i++) {
      const a = Math.random() * Math.PI * 2, r = 4 + Math.random() * 10;
      G.barrel(p.x + Math.cos(a) * r, 18 + i * 3, p.z + Math.sin(a) * r);
    }
    G.addScore('IT IS RAINING BARRELS', 50, 'pink');
  }

  // ---------- Keyboard & mouse ----------
  const keys = {};
  const input = { x: 0, z: 0, jump: false, jumpHeld: false, sprint: false, jet: false };
  // Third-person camera that only turns left/right: the tilt is fixed, dragging changes heading only.
  const CAM_PITCH = 0.3;
  let camYaw = Math.PI, camPitch = CAM_PITCH, camDist = isTouch ? 10 : 9, lookIdle = 0;

  window.addEventListener('keydown', e => {
    if (!playing) return;
    const k = e.key.toLowerCase();
    if (!keys[k]) {
      const D = G.drive;
      if (k === 'c') { if (D.car) D.exit(); else D.enter(); }
      else if (k === 'v') D.summon();
      else if (D.car) {
        if (k === ' ') D.hop();
        if (k === 'f' || k === 'shift') D.boost();
        if (k === 'q' || k === 'e' || k === 'h') D.honk();
        if (k === 'r') D.exit(true);
      } else {
        if (k === ' ') input.jump = true;
        if (k === 'f') goat.headbutt();
        if (k === 'e') goat.lick();
        if (k === 'q') goat.bleat();
        if (k === 'r') { goat.ragdoll('toggle'); renderModes(); }
      }
      if (k === 'g') toggleMoon();
      if (k === 't') toggleSlow();
      if (k === 'b') barrelRain();
      if (k === 'enter') goat.respawn();
      if (k === 'm') { const on = !(G.sfx.opts.sfx || G.sfx.opts.music); G.sfx.setOpt('sfx', on); G.sfx.setOpt('music', on); }
    }
    keys[k] = true;
    if ([' ', 'arrowup', 'arrowdown', 'arrowleft', 'arrowright'].includes(k)) e.preventDefault();
  });
  window.addEventListener('keyup', e => { keys[e.key.toLowerCase()] = false; });
  window.addEventListener('blur', () => { for (const k in keys) keys[k] = false; });

  const canvas = renderer.domElement;
  let dragging = false, lastX = 0, lastY = 0;
  const locked = () => document.pointerLockElement === canvas;
  canvas.addEventListener('contextmenu', e => e.preventDefault());
  canvas.addEventListener('mousedown', e => {
    if (!playing || isTouch) return;
    if (!locked()) {
      try { const r = canvas.requestPointerLock(); if (r && r.catch) r.catch(() => {}); } catch (_) { /* drag to look instead */ }
      dragging = true; lastX = e.clientX; lastY = e.clientY;
    }
    if (locked()) { const D = G.drive; if (e.button === 0) (D.car ? D.boost() : goat.headbutt()); if (e.button === 2) (D.car ? D.honk() : goat.lick()); }
  });
  window.addEventListener('mouseup', () => { dragging = false; });
  window.addEventListener('mousemove', e => {
    if (isTouch) return;
    let dx = 0, dy = 0;
    if (locked()) { dx = e.movementX; dy = e.movementY; }
    else if (dragging) { dx = e.clientX - lastX; dy = e.clientY - lastY; lastX = e.clientX; lastY = e.clientY; }
    camYaw -= dx * 0.0035;
    if (dx || dy) lookIdle = 0;
  });
  window.addEventListener('wheel', e => { camDist = Math.max(4, Math.min(22, camDist + e.deltaY * 0.01)); }, { passive: true });

  // ---------- Touch: stick, drag-to-look, buttons ----------
  const touchIn = { x: 0, z: 0 }, held = { jump: false, jet: false };
  // Floating stick: it appears under the thumb wherever it lands on the left side,
  // and drags along if the thumb slides past the rim, so you never lose it.
  const stick = $('stick'), nub = $('nub'), zone = $('stickZone');
  let stickId = null, sx0 = 0, sy0 = 0;
  const looks = new Map();
  function placeStick() {
    const half = stick.offsetWidth / 2;
    stick.style.left = (sx0 - half) + 'px'; stick.style.top = (sy0 - half) + 'px'; stick.style.bottom = 'auto';
  }
  function moveStick(e) {
    const R = stick.offsetWidth * 0.36;
    let dx = (e.clientX - sx0) / R, dy = (e.clientY - sy0) / R;
    const l = Math.hypot(dx, dy);
    if (l > 1) { dx /= l; dy /= l; sx0 = e.clientX - dx * R; sy0 = e.clientY - dy * R; placeStick(); }
    touchIn.x = -dx; touchIn.z = -dy;
    nub.style.transform = 'translate(' + dx * R * 0.85 + 'px,' + dy * R * 0.85 + 'px)';
  }
  zone.addEventListener('pointerdown', e => {
    if (stickId !== null) return;
    e.preventDefault();
    stickId = e.pointerId;
    try { zone.setPointerCapture(e.pointerId); } catch (_) { /* ok */ }
    sx0 = e.clientX; sy0 = e.clientY; placeStick();
    stick.classList.add('live');
    moveStick(e);
  });
  zone.addEventListener('pointermove', e => { if (e.pointerId === stickId) moveStick(e); });
  const endStick = e => {
    if (e.pointerId !== stickId) return;
    stickId = null; touchIn.x = touchIn.z = 0; nub.style.transform = '';
    stick.classList.remove('live'); stick.style.left = stick.style.top = stick.style.bottom = '';
  };
  zone.addEventListener('pointerup', endStick); zone.addEventListener('pointercancel', endStick); zone.addEventListener('lostpointercapture', endStick);
  canvas.addEventListener('pointerdown', e => { if (e.pointerType !== 'mouse') looks.set(e.pointerId, { x: e.clientX, y: e.clientY }); });
  canvas.addEventListener('pointermove', e => {
    const l = looks.get(e.pointerId);
    if (!l) return;
    camYaw -= (e.clientX - l.x) * 0.007;
    l.x = e.clientX; l.y = e.clientY;
    lookIdle = 0;
  });
  const endLook = e => looks.delete(e.pointerId);
  canvas.addEventListener('pointerup', endLook); canvas.addEventListener('pointercancel', endLook);
  // First real touch on a laptop with a touchscreen switches the UI over
  window.addEventListener('touchstart', () => {
    if (isTouch) return;
    isTouch = true;
    document.body.classList.add('touch');
    if (playing) { $('touch').hidden = false; $('tMenu').hidden = false; }
    renderMissions();
  }, { passive: true });

  function hold(id, down, up) {
    const b = $(id);
    b.addEventListener('pointerdown', e => { e.preventDefault(); e.stopPropagation(); b.classList.add('down'); down(); });
    const off = () => { b.classList.remove('down'); if (up) up(); };
    b.addEventListener('pointerup', off); b.addEventListener('pointercancel', off); b.addEventListener('pointerleave', off);
  }
  const D = G.drive;
  hold('tJump', () => { if (D.car) D.hop(); else { input.jump = true; held.jump = true; } }, () => { held.jump = false; });
  hold('tButt', () => (D.car ? D.boost() : goat.headbutt()));
  hold('tLick', () => (D.car ? D.honk() : goat.lick()));
  hold('tRag', () => { if (D.car) D.exit(true); else { goat.ragdoll('toggle'); renderModes(); } });
  hold('tBaa', () => (D.car ? D.honk() : goat.bleat()));
  hold('tDrive', () => { if (D.car) D.exit(); else D.enter(); });
  // Buttons change jobs while driving; the DRIVE button only shows up next to a car.
  const labels = { tJump: ['JUMP', 'HOP'], tButt: ['BUTT', 'NITRO'], tLick: ['LICK', 'HONK'], tRag: ['FLOP', 'BAIL'], tBaa: ['BAA!', 'BEEP'], tDrive: ['DRIVE', 'EXIT'] };
  D.onChange = () => {
    for (const id in labels) $(id).textContent = labels[id][D.car ? 1 : 0];
    document.body.classList.toggle('driving', !!D.car);
    renderModes();
  };
  let nearT = 0, nearShown = null;
  function updateCarPrompt(dt) {
    nearT -= dt;
    if (nearT > 0) return;
    nearT = 0.2;
    const show = D.car ? 'exit' : D.nearest() ? 'enter' : '';
    if (show === nearShown) return;
    nearShown = show;
    $('tDrive').hidden = !show;
    $('carHint').hidden = isTouch || show !== 'enter';
  }
  hold('tJet', () => { held.jet = true; }, () => { held.jet = false; });

  // Phone menu
  const menu = $('menu');
  $('tMenu').addEventListener('click', () => { menu.hidden = false; });
  $('mMoon').addEventListener('click', toggleMoon);
  $('mSlow').addEventListener('click', toggleSlow);
  $('mRain').addEventListener('click', () => { barrelRain(); menu.hidden = true; });
  $('mSpawn').addEventListener('click', () => { goat.respawn(); menu.hidden = true; });
  $('mCar').addEventListener('click', () => { G.drive.summon(); menu.hidden = true; });
  $('mClose').addEventListener('click', () => { menu.hidden = true; });

  function readInput() {
    let x = 0, z = 0;
    if (keys.w || keys.arrowup) z += 1;
    if (keys.s || keys.arrowdown) z -= 1;
    if (keys.a || keys.arrowleft) x += 1;
    if (keys.d || keys.arrowright) x -= 1;
    input.x = x + touchIn.x; input.z = z + touchIn.z;
    input.sprint = !!keys.shift || Math.hypot(touchIn.x, touchIn.z) > 0.8;
    input.jumpHeld = !!keys[' '] || held.jump;
    input.jet = !!keys.j || held.jet;
  }

  // ---------- Camera ----------
  // Everything that moves the camera is smoothed: wall avoidance pulls in fast but eases back out,
  // speed zoom eases, and the auto-follow only turns while you steer forward (never sideways or back).
  const camTarget = new THREE.Vector3(), tmpV = new THREE.Vector3();
  let camK = 1, speedZoom = 0, driveZoom = 0;
  const thinPole = b => { const s = b.shapes[0]; return s && s.halfExtents && s.halfExtents.x < 0.6 && s.halfExtents.z < 0.6; };
  function updateCamera(dt) {
    const D = G.drive, driving = !!D.car;
    const tb = driving ? D.car.body : goat.body;
    const p = tb.position;
    const aspect = camera.aspect, fov = aspect < 1 ? 70 : 62;
    if (camera.fov !== fov) { camera.fov = fov; camera.updateProjectionMatrix(); }
    lookIdle += dt;
    const push = Math.hypot(touchIn.x, touchIn.z);
    if (driving) {
      // Chase cam: sits behind the car unless you're looking around or reversing.
      if (looks.size === 0 && lookIdle > 0.8 && D.speed > -1) {
        let dA = D.yaw - camYaw; dA = Math.atan2(Math.sin(dA), Math.cos(dA));
        camYaw += dA * Math.min(1, dt * (1.5 + Math.min(2.5, Math.abs(D.speed) * 0.12)));
      }
    } else if (isTouch && looks.size === 0 && lookIdle > 0.6 && push > 0.2 && !goat.ragActive && Math.hypot(goat.body.velocity.x, goat.body.velocity.z) > 2) {
      // On foot: follow only while steering mostly forward. Sideways would spin the view as the goat
      // turns; pulling back would flip which way "back" points.
      const fwd = touchIn.z / push;
      if (fwd > 0.35) {
        let dA = goat.yaw - camYaw; dA = Math.atan2(Math.sin(dA), Math.cos(dA));
        camYaw += dA * Math.min(1, dt * 2.2 * fwd * Math.min(1, push));
      }
    }
    camTarget.lerp(tmpV.set(p.x, p.y + (driving ? 1.6 : 1.2), p.z), Math.min(1, dt * (driving ? 14 : 10)));
    speedZoom += (Math.min(4, tb.velocity.length() * 0.12) - speedZoom) * Math.min(1, dt * 2);
    driveZoom += ((driving ? 2 : 0) - driveZoom) * Math.min(1, dt * 3);
    const d = camDist + speedZoom + driveZoom + (aspect < 1 ? -1.5 : 0);
    // Behind (opposite the heading) and above the target. The old code negated the height too, which
    // buried the camera and left it clamped at ground level.
    const off = tmpV.set(-Math.sin(camYaw) * Math.cos(camPitch) * d, Math.sin(camPitch) * d, -Math.cos(camYaw) * Math.cos(camPitch) * d);
    // Keep out of walls. Thin poles and tree trunks are ignored so they don't make the camera pop.
    let want = 1;
    const from = new V3(camTarget.x, camTarget.y, camTarget.z), to = new V3(camTarget.x + off.x, camTarget.y + off.y, camTarget.z + off.z);
    world.raycastAll(from, to, { skipBackfaces: true }, r => {
      const b = r.body;
      if (b.mass === 0 && b !== G.groundBody && b.tag?.kind !== 'windmill' && !thinPole(b)) want = Math.min(want, Math.max(0.12, r.distance / d - 0.06));
    });
    camK += (want - camK) * Math.min(1, dt * (want < camK ? 14 : 2.5));
    camera.position.copy(camTarget).addScaledVector(off, camK);
    if (camera.position.y < 0.6) camera.position.y = 0.6;
    if (G.shake > 0) {
      camera.position.x += (Math.random() - 0.5) * G.shake;
      camera.position.y += (Math.random() - 0.5) * G.shake;
      G.shake = Math.max(0, G.shake - dt * 2.5);
    }
    camera.lookAt(camTarget);
    G.sfx.listen(p, Math.cos(camYaw), -Math.sin(camYaw));
    sun.position.set(p.x + 40, 70, p.z + 25);
    sun.target.position.set(p.x, 0, p.z);
    G.sky.position.copy(camera.position);
  }

  // ---------- Occasional "physics bugs" for flavour ----------
  let glitchT = 12 + Math.random() * 10;
  function maybeGlitch(dt) {
    glitchT -= dt;
    if (glitchT > 0) return;
    glitchT = 14 + Math.random() * 16;
    const p = goat.body.position;
    const near = G.dyn.filter(b => b.mass > 0 && b.mass < 100 && !b.goatPart && b.position.distanceTo(p) < 25);
    if (!near.length) return;
    const b = near[Math.floor(Math.random() * near.length)];
    b.wakeUp();
    b.velocity.set((Math.random() - 0.5) * 8, 28, (Math.random() - 0.5) * 8);
    b.angularVelocity.set(30, 0, 30);
    G.sfx.glitch();
    G.addScore('PHYSICS BUG (FEATURE)', 75, 'pink');
  }

  // ---------- Loop ----------
  const STEP = 1 / 60;
  let acc = 0, last = performance.now(), time = 0, baaT = 6, hudT = 0, missionT = 0;
  function stepPhysics(dt) {
    acc += dt;
    let n = 0;
    while (acc >= STEP && n < 4) {
      goat.prePhysics();
      world.step(STEP);
      const q = G.after; G.after = [];
      for (const fn of q) fn();
      acc -= STEP; n++;
    }
    if (n === 4) acc = 0;
  }
  function frame(now) {
    requestAnimationFrame(frame);
    let dt = Math.min(0.05, (now - last) / 1000);
    last = now;
    if (!playing) {
      time += dt; camYaw += dt * 0.15; lookIdle = 0;
      G.updatePeople(dt, time); stepPhysics(dt); G.sync(); G.updateParticles(dt, time);
      updateCamera(dt); renderer.render(scene, camera);
      return;
    }
    const scale = modes.slow ? 0.3 : 1;
    dt *= scale;
    time += dt;
    readInput();
    goat.update(dt, input, camYaw, time);
    input.jump = false;
    G.drive.update(dt, input);
    G.drive.updatePads(dt, time);
    G.updatePeople(dt, time);
    stepPhysics(dt);
    G.sync();
    if (goat.riding) goat.ridePose(dt, time);
    updateCarPrompt(dt / scale);
    G.updateParticles(dt, time);
    updateCamera(dt / scale);
    maybeGlitch(dt);
    comboT -= dt;
    if (comboT <= 0 && combo) { combo = 0; $('combo').textContent = ''; }
    baaT -= dt;
    if (baaT <= 0) { baaT = 5 + Math.random() * 8; G.sfx.baa(); }
    rainCd -= dt;
    hudT -= dt;
    if (hudT <= 0 && (goat.jetpack || G.drive.car)) { hudT = 0.25; renderModes(); }
    missionT -= dt;
    if (missionT <= 0) { missionT = 0.4; checkMissions(); }
    G.perfTick(dt / scale);
    G.lodTick(dt / scale);
    // Audio follows the action: wind at speed, jet roar, music heats up with the combo.
    G.sfx.loops(goat.body.velocity.length(), !!goat.jetting, null);
    G.sfx.setIntensity(combo >= 9 ? 3 : combo >= 5 ? 2 : combo >= 2 ? 1 : 0);
    renderer.render(scene, camera);
  }
  document.addEventListener('visibilitychange', () => { last = performance.now(); acc = 0; G.sfx.suspend(document.hidden); });

  $('play').addEventListener('click', () => {
    G.sfx.init();
    G.sfx.baa();
    $('title').hidden = true;
    playing = true;
    camYaw = goat.yaw; lookIdle = 0; // start right behind the goat
    if (isTouch) { const tip = $('tip'); tip.hidden = false; setTimeout(() => { tip.hidden = true; }, 7000); }
    if (isTouch) {
      $('touch').hidden = false;
      $('tMenu').hidden = false;
      // Fullscreen + landscape where the browser allows it; fine if it doesn't
      try {
        const r = document.documentElement.requestFullscreen?.({ navigationUI: 'hide' });
        if (r && r.then) r.then(() => screen.orientation?.lock?.('landscape')).catch(() => {});
      } catch (_) { /* not available */ }
    } else try { const r = canvas.requestPointerLock(); if (r && r.catch) r.catch(() => {}); } catch (_) { /* drag to look instead */ }
    renderModes();
    renderMissions();
  });

  // Keep score across live updates of the page
  window.claude?.hot?.snapshot?.(() => ({ score }));
  const boot = data => { if (data && data.score) { score = data.score; $('score').textContent = score.toLocaleString(); } };
  window.claude?.hot?.ready ? window.claude.hot.ready(boot) : boot(window.claude?.hot?.data ?? {});

  renderModes();
  renderMissions();
  G.optimizeScene();
  requestAnimationFrame(frame);
  G.debug = { input, keys, missions, get playing() { return playing; } };
})();
