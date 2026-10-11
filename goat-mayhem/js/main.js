// Input, camera, scoring and the main loop.
(function () {
  const { renderer, scene, camera, world, sun } = G;
  const goat = G.goat;
  const $ = id => document.getElementById(id);

  // ---------- Scoring ----------
  let score = 0, combo = 0, comboT = 0, statues = 0;
  const feed = $('feed');
  G.scoreLog = [];
  G.addScore = function (label, pts, style) {
    if (!playing) return;
    G.scoreLog.push(label);
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
    while (feed.children.length > 4) feed.lastChild.remove();
    setTimeout(() => el.remove(), 1600);
  };
  G.onStatue = function () {
    statues++;
    $('statueCount').textContent = statues + '/' + G.statues.length;
    G.addScore('GOLDEN GOAT ' + statues + '/' + G.statues.length, 2000, 'big');
    if (statues === G.statues.length) setTimeout(() => G.addScore('YOU ARE THE GOAT', 10000, 'big'), 900);
  };

  // ---------- Toggles ----------
  const modes = { moon: false, slow: false };
  function renderModes() {
    const m = [];
    m.push('<span class="' + (modes.moon ? 'on' : '') + '">G moon gravity</span>');
    m.push('<span class="' + (modes.slow ? 'on' : '') + '">T slow-mo</span>');
    if (goat.jetpack) m.push('<span class="on">J jetpack ' + Math.round(goat.fuel * 100) + '%</span>');
    if (goat.ragManual) m.push('<span class="on">R ragdolling</span>');
    $('mode').innerHTML = m.join('');
  }
  function toggleMoon() {
    modes.moon = !modes.moon;
    world.gravity.set(0, modes.moon ? -5 : -22, 0);
    G.addScore(modes.moon ? 'MOON GOAT' : 'EARTH GOAT', 10);
    renderModes();
  }
  function toggleSlow() { modes.slow = !modes.slow; G.addScore(modes.slow ? 'BULLET TIME' : 'REAL TIME', 10); renderModes(); }
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

  // ---------- Input ----------
  const keys = {};
  const input = { x: 0, z: 0, jump: false, jumpHeld: false, sprint: false, jet: false };
  let camYaw = Math.PI, camPitch = 0.35, camDist = 9;
  let playing = false;

  window.addEventListener('keydown', e => {
    if (!playing) return;
    const k = e.key.toLowerCase();
    if (!keys[k]) {
      if (k === ' ') input.jump = true;
      if (k === 'f') goat.headbutt();
      if (k === 'e') goat.lick();
      if (k === 'r') { goat.ragdoll('toggle'); renderModes(); }
      if (k === 'g') toggleMoon();
      if (k === 't') toggleSlow();
      if (k === 'b') barrelRain();
      if (k === 'enter') goat.respawn();
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
    if (!playing) return;
    if (!locked()) {
      try { const r = canvas.requestPointerLock(); if (r && r.catch) r.catch(() => {}); } catch (_) { /* fall back to drag */ }
      dragging = true; lastX = e.clientX; lastY = e.clientY;
    }
    if (locked()) { if (e.button === 0) goat.headbutt(); if (e.button === 2) goat.lick(); }
  });
  window.addEventListener('mouseup', () => { dragging = false; });
  window.addEventListener('mousemove', e => {
    let dx = 0, dy = 0;
    if (locked()) { dx = e.movementX; dy = e.movementY; }
    else if (dragging) { dx = e.clientX - lastX; dy = e.clientY - lastY; lastX = e.clientX; lastY = e.clientY; }
    camYaw -= dx * 0.0035;
    camPitch = Math.max(-0.2, Math.min(1.2, camPitch + dy * 0.003));
  });
  window.addEventListener('wheel', e => { camDist = Math.max(4, Math.min(22, camDist + e.deltaY * 0.01)); }, { passive: true });

  // Touch: joystick on the left, look by dragging anywhere else, buttons on the right
  const isTouch = matchMedia('(pointer: coarse)').matches;
  const touchIn = { x: 0, z: 0 };
  if (isTouch) {
    const stick = $('stick'), nub = $('nub');
    let stickId = null, lookId = null, lx = 0, ly = 0;
    stick.addEventListener('pointerdown', e => { stickId = e.pointerId; stick.setPointerCapture(e.pointerId); moveStick(e); });
    stick.addEventListener('pointermove', e => { if (e.pointerId === stickId) moveStick(e); });
    const endStick = e => { if (e.pointerId !== stickId) return; stickId = null; touchIn.x = touchIn.z = 0; nub.style.transform = ''; };
    stick.addEventListener('pointerup', endStick); stick.addEventListener('pointercancel', endStick);
    function moveStick(e) {
      const r = stick.getBoundingClientRect();
      let dx = (e.clientX - r.left - r.width / 2) / (r.width / 2), dy = (e.clientY - r.top - r.height / 2) / (r.height / 2);
      const l = Math.hypot(dx, dy); if (l > 1) { dx /= l; dy /= l; }
      touchIn.x = -dx; touchIn.z = -dy;
      nub.style.transform = 'translate(' + dx * 40 + 'px,' + dy * 40 + 'px)';
    }
    canvas.addEventListener('pointerdown', e => { lookId = e.pointerId; lx = e.clientX; ly = e.clientY; });
    canvas.addEventListener('pointermove', e => {
      if (e.pointerId !== lookId) return;
      camYaw -= (e.clientX - lx) * 0.006; camPitch = Math.max(-0.2, Math.min(1.2, camPitch + (e.clientY - ly) * 0.005));
      lx = e.clientX; ly = e.clientY;
    });
    canvas.addEventListener('pointerup', () => { lookId = null; });
    const tap = (id, fn) => $(id).addEventListener('pointerdown', e => { e.preventDefault(); fn(); });
    tap('tJump', () => { input.jump = true; keys[' '] = true; setTimeout(() => { keys[' '] = false; }, 350); });
    tap('tButt', () => goat.headbutt());
    tap('tLick', () => goat.lick());
    tap('tRag', () => { goat.ragdoll('toggle'); renderModes(); });
  }

  function readInput() {
    let x = 0, z = 0;
    if (keys.w || keys.arrowup) z += 1;
    if (keys.s || keys.arrowdown) z -= 1;
    if (keys.a || keys.arrowleft) x += 1;
    if (keys.d || keys.arrowright) x -= 1;
    input.x = x + touchIn.x; input.z = z + touchIn.z;
    input.sprint = !!keys.shift || Math.hypot(touchIn.x, touchIn.z) > 0.85;
    input.jumpHeld = !!keys[' '];
    input.jet = !!keys.j || (isTouch && goat.jetpack && input.jumpHeld && !goat.grounded);
  }

  // ---------- Camera ----------
  const camTarget = new THREE.Vector3();
  function updateCamera(dt) {
    const p = goat.body.position;
    camTarget.lerp(new THREE.Vector3(p.x, p.y + 1.2, p.z), Math.min(1, dt * 10));
    const d = camDist + Math.min(6, goat.body.velocity.length() * 0.15);
    const off = new THREE.Vector3(Math.sin(camYaw) * Math.cos(camPitch), Math.sin(camPitch), Math.cos(camYaw) * Math.cos(camPitch)).multiplyScalar(-d);
    camera.position.copy(camTarget).add(off);
    // Pull the camera in front of walls so it never ends up inside a house
    let best = 1;
    const from = new CANNON.Vec3(camTarget.x, camTarget.y, camTarget.z), to = new CANNON.Vec3(camera.position.x, camera.position.y, camera.position.z);
    world.raycastAll(from, to, { skipBackfaces: true }, r => {
      if (r.body.mass === 0 && r.body !== G.groundBody && r.body.tag?.kind !== 'windmill') best = Math.min(best, r.distance / d);
    });
    if (best < 1) camera.position.copy(camTarget).addScaledVector(off, Math.max(0.08, best - 0.06));
    if (camera.position.y < 0.6) camera.position.y = 0.6;
    if (G.shake > 0) {
      camera.position.x += (Math.random() - 0.5) * G.shake;
      camera.position.y += (Math.random() - 0.5) * G.shake;
      G.shake = Math.max(0, G.shake - dt * 2.5);
    }
    camera.lookAt(camTarget);
    sun.position.set(p.x + 40, 70, p.z + 25);
    sun.target.position.set(p.x, 0, p.z);
  }

  // ---------- Occasional "physics bugs" for flavor ----------
  let glitchT = 12 + Math.random() * 10;
  function maybeGlitch(dt) {
    glitchT -= dt;
    if (glitchT > 0) return;
    glitchT = 14 + Math.random() * 16;
    const p = goat.body.position;
    const near = G.dyn.filter(b => b.mass > 0 && b.mass < 100 && b.position.distanceTo(p) < 25);
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
  let acc = 0, last = performance.now(), time = 0, baaT = 6, hudT = 0;
  function frame(now) {
    requestAnimationFrame(frame);
    let dt = Math.min(0.05, (now - last) / 1000);
    last = now;
    if (!playing) { time += dt; camYaw += dt * 0.15; updateCamera(dt); G.updatePeople(dt, time); stepPhysics(dt); G.sync(); renderer.render(scene, camera); return; }
    const scale = modes.slow ? 0.3 : 1;
    dt *= scale;
    time += dt;
    readInput();
    goat.update(dt, input, camYaw, time);
    input.jump = false;
    G.updatePeople(dt, time);
    stepPhysics(dt);
    G.sync();
    G.updateParticles(dt);
    updateCamera(dt / scale);
    maybeGlitch(dt);
    comboT -= dt;
    if (comboT <= 0 && combo) { combo = 0; $('combo').textContent = ''; }
    baaT -= dt;
    if (baaT <= 0) { baaT = 5 + Math.random() * 8; G.sfx.baa(); }
    rainCd -= dt;
    hudT -= dt;
    if (hudT <= 0 && goat.jetpack) { hudT = 0.25; renderModes(); }
    renderer.render(scene, camera);
  }
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

  $('play').addEventListener('click', () => {
    G.sfx.init();
    G.sfx.baa();
    $('title').hidden = true;
    playing = true;
    if (isTouch) $('touch').hidden = false;
    else try { const r = canvas.requestPointerLock(); if (r && r.catch) r.catch(() => {}); } catch (_) { /* drag to look instead */ }
    renderModes();
  });

  // Keep score across live updates of the page
  window.claude?.hot?.snapshot?.(() => ({ score }));
  const boot = data => { if (data && data.score) { score = data.score; $('score').textContent = score.toLocaleString(); } };
  window.claude?.hot?.ready ? window.claude.hot.ready(boot) : boot(window.claude?.hot?.data ?? {});

  renderModes();
  requestAnimationFrame(frame);
  G.debug = { input, keys, get playing() { return playing; } };
})();
