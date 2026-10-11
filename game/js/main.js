// Input (keys, mouse, touch), camera, scoring, mayhem meter, challenges, pause and the main loop.
(function () {
  const { renderer, scene, camera, world, sun, isTouch } = G;
  const goat = G.goat;
  const $ = id => document.getElementById(id);

  // ---------- Scoring + mayhem meter ----------
  let score = 0, combo = 0, comboT = 0, statues = 0, meter = 0, mayhemT = 0, best = 0;
  try { best = +localStorage.getItem('goat.best') || 0; } catch (_) { /* fine */ }
  const feed = $('feed');
  G.scoreLog = [];
  G.addScore = function (label, pts, style) {
    if (!playing) return;
    G.scoreLog.push(label);
    combo = comboT > 0 ? combo + 1 : 1;
    comboT = 3;
    const mult = Math.min(10, 1 + Math.floor(combo / 3)) * (G.mayhem ? 2 : 1);
    const gained = pts * mult;
    score += gained;
    if (!G.mayhem) { meter = Math.min(1, meter + gained / 6000); if (meter >= 1) startMayhem(); }
    $('score').textContent = score.toLocaleString();
    $('combo').textContent = mult > 1 ? 'COMBO x' + mult : '';
    if (combo > 1) G.sfx.combo(combo);
    if (combo === 10) { G.sfx.airhorn(); G.addScore('UNSTOPPABLE', 500, 'big'); }
    const el = document.createElement('div');
    el.className = 'pop' + (style ? ' ' + style : '');
    el.textContent = '+' + gained + ' ' + label;
    feed.prepend(el);
    while (feed.children.length > 4) feed.lastChild.remove();
    setTimeout(() => el.remove(), 1600);
    checkChallenge(label);
    if (score > best) { best = score; try { localStorage.setItem('goat.best', best); } catch (_) { /* fine */ } }
  };
  function startMayhem() {
    G.mayhem = true; mayhemT = 10; meter = 1;
    G.sfx.rage(); G.sfx.buzz([80, 40, 80, 40, 200]);
    G.shake = 1.4;
    document.body.classList.add('mayhem');
    const el = document.createElement('div'); el.className = 'pop big'; el.textContent = 'MAYHEM MODE: 2x EVERYTHING'; feed.prepend(el); setTimeout(() => el.remove(), 1600);
  }
  G.onStatue = function () {
    statues++;
    $('statueCount').textContent = statues + '/' + G.statues.length;
    G.addScore('GOLDEN GOAT ' + statues + '/' + G.statues.length, 2000, 'big');
    if (statues === G.statues.length) setTimeout(() => { G.addScore('YOU ARE THE GOAT', 10000, 'big'); G.sfx.baa(goat.body.position, true); G.sfx.airhorn(); }, 900);
  };

  // ---------- Challenges: small rotating objectives ----------
  const CH = [
    { text: 'Knock over 4 people', n: 4, test: l => /RUDE|BULLY|ASSAULT|SORRY|GOATED|COLLATERAL|POLICE/.test(l) },
    { text: 'Blow up 3 barrels', n: 3, test: l => l === 'KABOOM' },
    { text: 'Smash 10 crates', n: 10, test: l => l === 'CRATE' },
    { text: 'Do a flip', n: 1, test: l => /^FLIP|^SPIN/.test(l) },
    { text: 'Get hit by a car', n: 1, test: l => l === 'HIT AND RUN' },
    { text: 'Lick 3 things', n: 3, test: l => /LICK/.test(l) },
    { text: 'Catch 2s of air', n: 1, test: l => l.startsWith('AIR TIME') },
    { text: 'Total a car', n: 1, test: l => /TOTALLED|CAR FLIP/.test(l) },
    { text: 'Eat something', n: 1, test: l => l === 'ATE IT' },
    { text: 'Ruin a picnic', n: 1, test: l => l === 'PICNIC RUINED' },
    { text: 'Scatter the pigeons', n: 2, test: l => l === 'PIGEON PANIC' },
    { text: 'Hit 3 things in one bonk', n: 1, test: l => l.startsWith('MULTI-BONK') }
  ];
  let chIdx = -1, chCount = 0, done = 0;
  function nextChallenge() {
    let i; do { i = Math.floor(Math.random() * CH.length); } while (i === chIdx);
    chIdx = i; chCount = 0; renderChallenge();
  }
  function renderChallenge() {
    const c = CH[chIdx];
    $('chText').textContent = c.text;
    $('chBar').style.width = (chCount / c.n * 100) + '%';
  }
  function checkChallenge(label) {
    if (chIdx < 0) return;
    const c = CH[chIdx];
    if (!c.test(label)) return;
    chCount = Math.min(c.n, chCount + 1);
    renderChallenge();
    G.sfx.tick();
    if (chCount >= c.n) {
      done++;
      G.sfx.challenge(); G.sfx.buzz([30, 50, 30]);
      setTimeout(() => G.addScore('CHALLENGE ' + done + ' DONE', 1000 + done * 250, 'big'), 50);
      $('challenge').classList.add('done');
      setTimeout(() => { $('challenge').classList.remove('done'); nextChallenge(); }, 1400);
    }
  }

  // ---------- Toggles ----------
  const modes = { moon: false, slow: false };
  function renderModes() {
    const m = [];
    m.push('<span class="' + (modes.moon ? 'on' : '') + '">' + (isTouch ? '' : 'G ') + 'moon</span>');
    m.push('<span class="' + (modes.slow ? 'on' : '') + '">' + (isTouch ? '' : 'T ') + 'slow-mo</span>');
    if (goat.jetpack) m.push('<span class="on">' + (isTouch ? 'jet ' : 'J jet ') + Math.round(goat.fuel * 100) + '%</span>');
    if (goat.ragManual) m.push('<span class="on">flop</span>');
    if (G.wanted >= 2) m.push('<span class="on cop">WANTED</span>');
    $('mode').innerHTML = m.join('');
    $('tMoon').classList.toggle('hot', modes.moon);
    $('tSlow').classList.toggle('hot', modes.slow);
  }
  function toggleMoon() {
    modes.moon = !modes.moon;
    world.gravity.set(0, modes.moon ? -5 : -22, 0);
    G.sfx.moon(modes.moon);
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
      G.barrel(p.x + Math.cos(a) * r, 18 + i * 3, p.z + Math.sin(a) * r, i < 3);
    }
    G.sfx.scratch();
    G.addScore('IT IS RAINING BARRELS', 50, 'pink');
  }

  // ---------- Input ----------
  const keys = {};
  const input = { x: 0, z: 0, jump: false, jumpHeld: false, sprint: false, jet: false };
  let camYaw = Math.PI, camPitch = 0.35, camDist = 9;
  let playing = false, paused = false;

  window.addEventListener('keydown', e => {
    const k = e.key.toLowerCase();
    if (k === 'escape' || k === 'p') { if (playing) togglePause(); return; }
    if (!playing || paused) return;
    if (!keys[k]) {
      if (k === ' ') input.jump = true;
      if (k === 'f') goat.headbutt();
      if (k === 'e') goat.lick();
      if (k === 'r') { goat.ragdoll('toggle'); renderModes(); }
      if (k === 'g') toggleMoon();
      if (k === 't') toggleSlow();
      if (k === 'b') barrelRain();
      if (k === 'enter') goat.respawn();
      if (k === 'm') setOpt('music', !G.sfx.opts.music);
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
  if (!isTouch) {
    canvas.addEventListener('mousedown', e => {
      if (!playing || paused) return;
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
  }

  // Touch: joystick on the left, look by dragging the right side, buttons bottom-right.
  // Hold JUMP in the air to flail (or fly with the jetpack). Camera auto-follows when you are not dragging.
  const touchIn = { x: 0, z: 0 };
  let lookT = 0, pinch0 = 0, pinchD0 = 0;
  if (isTouch) {
    const stick = $('stick'), nub = $('nub'), zone = $('stickZone');
    let stickId = null, lookId = null, lx = 0, ly = 0, sx0 = 0, sy0 = 0;
    const R = 52;
    // Floating stick: it appears where the thumb lands on the left side.
    zone.addEventListener('pointerdown', e => {
      if (stickId !== null) return;
      stickId = e.pointerId; zone.setPointerCapture(e.pointerId);
      sx0 = e.clientX; sy0 = e.clientY;
      stick.style.left = (sx0 - 65) + 'px'; stick.style.top = (sy0 - 65) + 'px'; stick.classList.add('live');
      moveStick(e);
    });
    zone.addEventListener('pointermove', e => { if (e.pointerId === stickId) moveStick(e); });
    const endStick = e => { if (e.pointerId !== stickId) return; stickId = null; touchIn.x = touchIn.z = 0; nub.style.transform = ''; stick.classList.remove('live'); };
    zone.addEventListener('pointerup', endStick); zone.addEventListener('pointercancel', endStick); zone.addEventListener('lostpointercapture', endStick);
    function moveStick(e) {
      let dx = (e.clientX - sx0) / R, dy = (e.clientY - sy0) / R;
      const l = Math.hypot(dx, dy);
      if (l > 1) { dx /= l; dy /= l; sx0 = e.clientX - dx * R; sy0 = e.clientY - dy * R; stick.style.left = (sx0 - 65) + 'px'; stick.style.top = (sy0 - 65) + 'px'; }
      touchIn.x = -dx; touchIn.z = -dy;
      nub.style.transform = 'translate(' + dx * 40 + 'px,' + dy * 40 + 'px)';
    }
    const look = $('lookZone');
    const pts = new Map();
    look.addEventListener('pointerdown', e => {
      pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (lookId === null) { lookId = e.pointerId; lx = e.clientX; ly = e.clientY; }
      if (pts.size === 2) { const [a, b] = [...pts.values()]; pinch0 = Math.hypot(a.x - b.x, a.y - b.y); pinchD0 = camDist; }
    });
    look.addEventListener('pointermove', e => {
      if (!pts.has(e.pointerId)) return;
      pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (pts.size === 2) { const [a, b] = [...pts.values()]; const d = Math.hypot(a.x - b.x, a.y - b.y); camDist = Math.max(4, Math.min(20, pinchD0 * pinch0 / Math.max(20, d))); return; }
      if (e.pointerId !== lookId) return;
      camYaw -= (e.clientX - lx) * 0.006; camPitch = Math.max(-0.2, Math.min(1.2, camPitch + (e.clientY - ly) * 0.005));
      lx = e.clientX; ly = e.clientY; lookT = 1.5;
    });
    const endLook = e => { pts.delete(e.pointerId); if (e.pointerId === lookId) lookId = null; };
    look.addEventListener('pointerup', endLook); look.addEventListener('pointercancel', endLook);

    const hold = (id, down, up) => {
      const el = $(id);
      el.addEventListener('pointerdown', e => { e.preventDefault(); el.classList.add('down'); G.sfx.buzz(8); down(); });
      const rel = () => { el.classList.remove('down'); if (up) up(); };
      el.addEventListener('pointerup', rel); el.addEventListener('pointercancel', rel); el.addEventListener('pointerleave', rel);
    };
    hold('tJump', () => { input.jump = true; keys[' '] = true; }, () => { keys[' '] = false; });
    hold('tButt', () => goat.headbutt());
    hold('tLick', () => goat.lick());
    hold('tRag', () => { goat.ragdoll('toggle'); renderModes(); });
    hold('tMoon', toggleMoon);
    hold('tSlow', toggleSlow);
    hold('tRain', barrelRain);
    hold('tSpawn', () => goat.respawn());
  }

  function readInput() {
    let x = 0, z = 0;
    if (keys.w || keys.arrowup) z += 1;
    if (keys.s || keys.arrowdown) z -= 1;
    if (keys.a || keys.arrowleft) x += 1;
    if (keys.d || keys.arrowright) x -= 1;
    input.x = x + touchIn.x; input.z = z + touchIn.z;
    input.sprint = !!keys.shift || Math.hypot(touchIn.x, touchIn.z) > 0.8;
    input.jumpHeld = !!keys[' '];
    input.jet = !!keys.j || (isTouch && goat.jetpack && input.jumpHeld && !goat.grounded && goat.airT > 0.25);
  }

  // ---------- Camera ----------
  const camTarget = new THREE.Vector3();
  function updateCamera(dt) {
    const p = goat.body.position;
    camTarget.lerp(new THREE.Vector3(p.x, p.y + 1.2, p.z), Math.min(1, dt * 10));
    // On touch, drift the camera behind the goat while the player is steering and not looking around.
    if (isTouch && playing) {
      lookT -= dt;
      const moving = Math.hypot(touchIn.x, touchIn.z) > 0.2;
      if (lookT <= 0 && moving && goat.rag <= 0) {
        let dA = (goat.yaw + Math.PI) - camYaw; dA = Math.atan2(Math.sin(dA), Math.cos(dA));
        camYaw += dA * Math.min(1, dt * 1.6);
        camPitch += (0.32 - camPitch) * Math.min(1, dt * 0.8);
      }
    }
    const d = camDist + Math.min(6, goat.body.velocity.length() * 0.15);
    const off = new THREE.Vector3(Math.sin(camYaw) * Math.cos(camPitch), Math.sin(camPitch), Math.cos(camYaw) * Math.cos(camPitch)).multiplyScalar(-d);
    camera.position.copy(camTarget).add(off);
    let bestK = 1;
    const from = new CANNON.Vec3(camTarget.x, camTarget.y, camTarget.z), to = new CANNON.Vec3(camera.position.x, camera.position.y, camera.position.z);
    world.raycastAll(from, to, { skipBackfaces: true }, r => {
      if (r.body.mass === 0 && r.body !== G.groundBody && r.body.tag?.kind !== 'windmill') bestK = Math.min(bestK, r.distance / d);
    });
    if (bestK < 1) camera.position.copy(camTarget).addScaledVector(off, Math.max(0.08, bestK - 0.06));
    if (camera.position.y < 0.6) camera.position.y = 0.6;
    if (G.shake > 0) {
      const s = reduceMotion ? G.shake * 0.3 : G.shake;
      camera.position.x += (Math.random() - 0.5) * s;
      camera.position.y += (Math.random() - 0.5) * s;
      G.shake = Math.max(0, G.shake - dt * 2.5);
    }
    camera.lookAt(camTarget);
    if (G.mayhem) camera.rotation.z += Math.sin(performance.now() * 0.02) * 0.01;
    sun.position.set(p.x + 40, 70, p.z + 25);
    sun.target.position.set(p.x, 0, p.z);
    // Audio listener: at the goat, facing the camera's right vector for panning.
    G.sfx.listen(p, Math.cos(camYaw), -Math.sin(camYaw));
  }
  const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;

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

  // ---------- Pause / settings ----------
  function setOpt(name, val) {
    G.sfx.setOpt(name, val);
    $('o' + name).setAttribute('aria-pressed', String(val));
    $('o' + name).textContent = { sfx: 'Sound effects', music: 'Music', haptics: 'Vibration' }[name] + ': ' + (val ? 'on' : 'off');
    $('tMute').textContent = G.sfx.opts.sfx || G.sfx.opts.music ? '🔊' : '🔇';
  }
  for (const n of ['sfx', 'music', 'haptics']) {
    setOpt(n, G.sfx.opts[n]);
    $('o' + n).addEventListener('click', () => setOpt(n, !G.sfx.opts[n]));
  }
  if (!navigator.vibrate) $('ohaptics').hidden = true;
  function togglePause(force) {
    paused = force === undefined ? !paused : force;
    $('pause').hidden = !paused;
    $('pBest').textContent = best.toLocaleString();
    $('pScore').textContent = score.toLocaleString();
    G.sfx.suspend(paused);
    if (!paused) last = performance.now();
    for (const k in keys) keys[k] = false;
  }
  $('tPause').addEventListener('click', () => togglePause());
  $('resume').addEventListener('click', () => togglePause(false));
  $('tMute').addEventListener('click', () => { const on = !(G.sfx.opts.sfx || G.sfx.opts.music); setOpt('sfx', on); setOpt('music', on); });
  document.addEventListener('visibilitychange', () => { if (document.hidden && playing) togglePause(true); });

  // ---------- Loop ----------
  const STEP = 1 / 60;
  let acc = 0, last = performance.now(), time = 0, baaT = 6, hudT = 0;
  let frames = 0, fpsT = 0, slowFrames = 0;
  function frame(now) {
    requestAnimationFrame(frame);
    if (paused) { last = now; return; }
    let dt = Math.min(0.05, (now - last) / 1000);
    last = now;
    // Adaptive quality: if a phone cannot hold 30fps for a while, drop the pixel ratio once.
    frames++; fpsT += dt;
    if (dt > 0.04) slowFrames++;
    if (fpsT > 3) { if (slowFrames > frames * 0.5 && renderer.getPixelRatio() > 0.75) renderer.setPixelRatio(Math.max(0.75, renderer.getPixelRatio() - 0.25)); frames = slowFrames = 0; fpsT = 0; }

    if (!playing) { time += dt; camYaw += dt * 0.15; updateCamera(dt); G.updatePeople(dt, time); G.updateClouds(dt); stepPhysics(dt); G.sync(); renderer.render(scene, camera); return; }
    const scale = modes.slow ? 0.3 : 1;
    dt *= scale;
    time += dt;
    readInput();
    goat.update(dt, input, camYaw, time);
    input.jump = false;
    G.updatePeople(dt, time);
    G.updateClouds(dt);
    stepPhysics(dt);
    G.sync();
    G.updateParticles(dt);
    updateCamera(dt / scale);
    maybeGlitch(dt);
    comboT -= dt;
    if (comboT <= 0 && combo) { combo = 0; $('combo').textContent = ''; }
    if (G.mayhem) {
      mayhemT -= dt; meter = mayhemT / 10;
      if (mayhemT <= 0) { G.mayhem = false; meter = 0; document.body.classList.remove('mayhem'); G.sfx.slideDown(); }
    } else meter = Math.max(0, meter - dt * 0.01);
    $('meterFill').style.transform = 'scaleX(' + meter.toFixed(3) + ')';
    baaT -= dt;
    if (baaT <= 0) { baaT = 5 + Math.random() * 8; G.sfx.baa(goat.body.position); }
    rainCd -= dt;
    hudT -= dt;
    if (hudT <= 0) { hudT = 0.25; renderModes(); }
    // Audio follows the action: speed wind, jet roar, siren position, music intensity.
    const sp = goat.body.velocity.length();
    G.sfx.loops(sp, goat.jetting, G.sirenPos);
    G.sfx.setIntensity(G.mayhem ? 3 : combo >= 6 ? 2 : combo >= 2 || G.wanted >= 2 ? 1 : 0);
    G.water.position.y = 1.02 + Math.sin(time * 2) * 0.02;
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

  function start() {
    G.sfx.init();
    G.sfx.baa(goat.body.position);
    G.sfx.buzz(20);
    $('title').hidden = true;
    playing = true;
    $('hud').hidden = false;
    if (isTouch) $('touch').hidden = false;
    else try { const r = canvas.requestPointerLock(); if (r && r.catch) r.catch(() => {}); } catch (_) { /* drag to look instead */ }
    renderModes();
    nextChallenge();
    last = performance.now();
  }
  $('play').addEventListener('click', start);
  $('best').textContent = best ? 'Best: ' + best.toLocaleString() : '';
  if (isTouch) { $('keysDesk').hidden = true; $('keysTouch').hidden = false; }
  // Lock the screen to landscape where allowed. Portrait still works, with a wider lens.
  $('play').addEventListener('click', () => { try { screen.orientation?.lock?.('landscape').catch(() => {}); } catch (_) { /* unsupported */ } });

  // Keep score across live updates of the page
  window.claude?.hot?.snapshot?.(() => ({ score }));
  const boot = data => { if (data && data.score) { score = data.score; $('score').textContent = score.toLocaleString(); } };
  window.claude?.hot?.ready ? window.claude.hot.ready(boot) : boot(window.claude?.hot?.data ?? {});

  renderModes();
  requestAnimationFrame(frame);
  G.debug = { input, keys, get playing() { return playing; }, get meter() { return meter; } };
})();
