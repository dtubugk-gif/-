// Every sound and the music are synthesized with WebAudio at runtime: no audio files ship with the game.
// Mix: sfx bus + music bus -> shell-shock filter -> tape (slow-mo) filter -> limiter -> out.
// Sounds are panned and attenuated around the goat, voices are capped, and low-priority cues drop first.
window.G = window.G || {};

G.sfx = (function () {
  let ctx = null, sfxBus, musicBus, shockLP, tapeLP, limiter, verbIn, drive, crush, noiseBuf;
  const opts = { sfx: true, music: true, haptics: true };
  try { Object.assign(opts, JSON.parse(localStorage.getItem('goat.audio') || '{}')); } catch (_) { /* defaults */ }
  const last = {};
  const ear = { x: 0, y: 0, z: 0, rx: 1, rz: 0 };
  const MAX_VOICES = 34;
  let voices = 0, slow = false;
  const R = Math.random;

  function save() { try { localStorage.setItem('goat.audio', JSON.stringify(opts)); } catch (_) { /* private mode */ } }

  function curve(fn) {
    const c = new Float32Array(1024);
    for (let i = 0; i < 1024; i++) c[i] = fn(i / 511.5 - 1);
    return c;
  }

  function init() {
    if (ctx) { if (ctx.state !== 'running') ctx.resume(); return; }
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    ctx = new AC({ latencyHint: 'interactive' });
    limiter = ctx.createDynamicsCompressor();
    limiter.threshold.value = -12; limiter.knee.value = 8; limiter.ratio.value = 14;
    limiter.attack.value = 0.002; limiter.release.value = 0.2;
    const out = ctx.createGain(); out.gain.value = 0.95;
    shockLP = ctx.createBiquadFilter(); shockLP.type = 'lowpass'; shockLP.frequency.value = 20000; shockLP.Q.value = 0.8;
    tapeLP = ctx.createBiquadFilter(); tapeLP.type = 'lowpass'; tapeLP.frequency.value = 20000; tapeLP.Q.value = 1.2;
    sfxBus = ctx.createGain(); sfxBus.gain.value = opts.sfx ? 1 : 0;
    musicBus = ctx.createGain(); musicBus.gain.value = opts.music ? 0.3 : 0;
    sfxBus.connect(shockLP); musicBus.connect(shockLP);
    shockLP.connect(tapeLP); tapeLP.connect(limiter); limiter.connect(out); out.connect(ctx.destination);

    // Generated stereo impulse response: a cheap "town square" reverb.
    const verb = ctx.createConvolver();
    const len = Math.floor(ctx.sampleRate * 2), ir = ctx.createBuffer(2, len, ctx.sampleRate);
    for (let c = 0; c < 2; c++) {
      const d = ir.getChannelData(c);
      for (let i = 0; i < len; i++) d[i] = (R() * 2 - 1) * Math.pow(1 - i / len, 3.4);
    }
    verb.buffer = ir;
    verbIn = ctx.createGain(); verbIn.gain.value = 0.55;
    verbIn.connect(verb); verb.connect(sfxBus);

    noiseBuf = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate);
    const nd = noiseBuf.getChannelData(0);
    for (let i = 0; i < nd.length; i++) nd[i] = R() * 2 - 1;
    drive = curve(x => Math.tanh(x * 6));
    crush = curve(x => Math.round(x * 4) / 4);

    buildLoops();
    music.start();
  }

  const now = () => ctx.currentTime;
  function gate(name, gap) {
    if (!ctx || ctx.state !== 'running') return false;
    const t = ctx.currentTime;
    if (last[name] && t - last[name] < gap) return false;
    last[name] = t;
    return true;
  }

  // Distance gain and stereo pan relative to the goat, oriented by the camera.
  function place(pos) {
    if (!pos) return { g: 1, pan: 0 };
    const dx = pos.x - ear.x, dy = (pos.y || 0) - ear.y, dz = pos.z - ear.z;
    const d = Math.hypot(dx, dy, dz);
    if (d > 110) return null;
    const g = 1 / (1 + Math.max(0, d - 5) / 13);
    const pan = d > 0.5 ? Math.max(-0.85, Math.min(0.85, (dx * ear.rx + dz * ear.rz) / d)) : 0;
    return { g, pan };
  }

  // One voice = gain -> panner -> sfx bus (+ reverb send). prio 2+ ignores the voice cap.
  function voice(pos, vol, wet, dur, prio) {
    if (!ctx || ctx.state !== 'running' || !opts.sfx) return null;
    const p = place(pos);
    if (!p || p.g < 0.04) return null;
    if (voices >= MAX_VOICES && !(prio > 1)) return null;
    voices++;
    setTimeout(() => { voices--; }, (dur + 0.25) * 1000);
    const g = ctx.createGain(); g.gain.value = vol * p.g;
    let tail = g;
    if (ctx.createStereoPanner) { const sp = ctx.createStereoPanner(); sp.pan.value = p.pan; g.connect(sp); tail = sp; }
    tail.connect(sfxBus);
    if (wet) { const s = ctx.createGain(); s.gain.value = wet; tail.connect(s); s.connect(verbIn); }
    return g;
  }

  function env(param, t0, a, peak, dur, hold) {
    peak = Math.max(0.0002, peak);
    param.setValueAtTime(0.0001, t0);
    param.exponentialRampToValueAtTime(peak, t0 + a);
    if (hold) param.setValueAtTime(peak, t0 + Math.max(a, dur * hold));
    param.exponentialRampToValueAtTime(0.0001, t0 + Math.max(a + 0.01, dur));
  }

  function osc(dest, type, f0, f1, t0, dur, vol, o) {
    o = o || {};
    const n = ctx.createOscillator(), g = ctx.createGain();
    n.type = type;
    n.frequency.setValueAtTime(f0, t0);
    if (f1 && f1 !== f0) {
      if (o.lin) n.frequency.linearRampToValueAtTime(f1, t0 + dur);
      else n.frequency.exponentialRampToValueAtTime(Math.max(20, f1), t0 + dur);
    }
    if (o.vib) {
      const l = ctx.createOscillator(), lg = ctx.createGain();
      l.frequency.value = o.vib[0]; lg.gain.value = o.vib[1];
      l.connect(lg); lg.connect(n.frequency);
      l.start(t0); l.stop(t0 + dur + 0.05);
    }
    env(g.gain, t0, o.a || 0.004, vol, dur, o.hold);
    n.connect(g); g.connect(dest);
    n.start(t0); n.stop(t0 + dur + 0.05);
    return n;
  }

  function nz(dest, t0, dur, vol, type, f0, f1, q, a) {
    const s = ctx.createBufferSource(), f = ctx.createBiquadFilter(), g = ctx.createGain();
    s.buffer = noiseBuf; s.loop = true;
    f.type = type; f.Q.value = q || 1;
    f.frequency.setValueAtTime(f0, t0);
    if (f1 && f1 !== f0) f.frequency.exponentialRampToValueAtTime(f1, t0 + dur);
    env(g.gain, t0, a || 0.003, vol, dur);
    s.connect(f); f.connect(g); g.connect(dest);
    s.start(t0, R() * 1.5); s.stop(t0 + dur + 0.05);
    return f;
  }

  function shaper(dest, c) {
    const w = ctx.createWaveShaper(); w.curve = c || drive; w.oversample = '2x';
    w.connect(dest);
    return w;
  }

  // Vowel filter bank: a buzzy source through a few resonant bands reads as a voice.
  function formants(dest, src, list) {
    for (const [f, q, gn] of list) {
      const bp = ctx.createBiquadFilter(), g = ctx.createGain();
      bp.type = 'bandpass'; bp.frequency.value = f; bp.Q.value = q; g.gain.value = gn;
      src.connect(bp); bp.connect(g); g.connect(dest);
    }
  }

  function buzz(p) {
    if (!opts.haptics || !navigator.vibrate) return;
    try { navigator.vibrate(p); } catch (_) { /* not allowed in this frame */ }
  }

  // ---------- Continuous loops: wind rush, jetpack, police siren ----------
  const loops = {};
  function buildLoops() {
    const mk = (src, filterType, freq, q) => {
      const f = ctx.createBiquadFilter(), g = ctx.createGain();
      f.type = filterType; f.frequency.value = freq; f.Q.value = q;
      g.gain.value = 0;
      src.connect(f); f.connect(g);
      return { f, g };
    };
    const noiseSrc = () => { const s = ctx.createBufferSource(); s.buffer = noiseBuf; s.loop = true; s.start(); return s; };

    const wind = mk(noiseSrc(), 'bandpass', 500, 0.7);
    wind.g.connect(sfxBus);
    loops.wind = wind;

    const jet = mk(noiseSrc(), 'lowpass', 900, 1.5);
    const rumble = ctx.createOscillator(); rumble.type = 'sawtooth'; rumble.frequency.value = 48; rumble.start();
    const rg = ctx.createGain(); rg.gain.value = 0.35; rumble.connect(rg); rg.connect(jet.f);
    const jd = shaper(sfxBus); jet.g.connect(jd);
    loops.jet = jet;

    const siren = ctx.createOscillator(); siren.type = 'triangle';
    const sl = ctx.createOscillator(); sl.type = 'square'; sl.frequency.value = 1.4;
    const sd = ctx.createGain(); sd.gain.value = 140; siren.frequency.value = 820;
    sl.connect(sd); sd.connect(siren.frequency); siren.start(); sl.start();
    const sg = ctx.createGain(); sg.gain.value = 0;
    const sp = ctx.createStereoPanner ? ctx.createStereoPanner() : null;
    siren.connect(sg);
    if (sp) { sg.connect(sp); sp.connect(sfxBus); } else sg.connect(sfxBus);
    loops.siren = { g: sg, sp };
  }

  // ---------- Music: an oom-pah goat polka that heats up with your combo ----------
  const music = (function () {
    const F2 = 87.31, F3 = 174.61, F4 = 349.23;
    const MEL = [
      [0, 4, 7, 12, 9, 7, 4, -1, 5, 9, 12, 17, 14, 12, 9, -1, 7, 11, 14, 19, 17, 14, 11, 7, 12, -1, 9, 7, 4, 2, 0, -1],
      [12, 12, 9, 12, 16, -1, 12, -1, 14, 14, 12, 9, 5, -1, 9, -1, 11, 14, 19, 14, 11, 7, 11, 14, 12, 9, 7, 4, 0, -1, 0, -1]
    ];
    const ROOT = [0, 5, 7, 0];
    let step = 0, next = 0, timer = null, layers = null, level = 0, pitch = 1, rate = 1;

    function start() {
      if (timer) return;
      layers = [0, 1, 2, 3].map(() => { const g = ctx.createGain(); g.gain.value = 0; g.connect(musicBus); return g; });
      setLevel(0, true);
      next = ctx.currentTime + 0.1;
      timer = setInterval(tick, 30);
    }
    function setLevel(n, instant) {
      level = n;
      if (!layers) return;
      // bass, chords, lead, drums
      const want = [[1, 0.8, 0, 0], [1, 0.8, 0.8, 0.5], [1, 0.9, 1, 1], [1.1, 1, 1.1, 1.2]][n];
      layers.forEach((g, i) => {
        if (instant) g.gain.value = want[i];
        else g.gain.setTargetAtTime(want[i], ctx.currentTime, 0.4);
      });
    }
    function setSlow(on) { pitch = on ? 0.75 : 1; rate = on ? 0.55 : 1; }
    function tick() {
      if (!ctx || ctx.state !== 'running') return;
      if (next < ctx.currentTime - 0.2) next = ctx.currentTime + 0.05;
      const stepDur = 60 / (level === 3 ? 152 : 138) / 4 / rate;
      while (next < ctx.currentTime + 0.14) { play(step, next); next += stepDur; step++; }
    }
    function play(st, t) {
      const bar = Math.floor(st / 16) % 4, s = st % 16, loop = Math.floor(st / 64) % 2;
      const root = ROOT[bar], [bass, chord, lead, drum] = layers;
      if (s === 0 || s === 8) {
        const f = F2 * Math.pow(2, (s === 0 ? root : root + 7 - (root >= 5 ? 12 : 0)) / 12) * pitch;
        osc(bass, 'triangle', f, f, t, 0.28, 0.55);
        osc(bass, 'sine', f / 2, f / 2, t, 0.3, 0.35);
      }
      if (s === 4 || s === 12) {
        const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 1700; lp.connect(chord);
        for (const iv of [0, 4, 7]) { const f = F3 * Math.pow(2, (root + iv) / 12) * pitch; osc(lp, 'sawtooth', f, f, t, 0.14, 0.07); }
      }
      if (s % 2 === 0) {
        const n = MEL[loop][bar * 8 + s / 2];
        if (n >= 0) {
          const f = F4 * Math.pow(2, n / 12) * pitch * (level === 3 ? 2 : 1);
          const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.Q.value = 3;
          lp.frequency.setValueAtTime(4200, t); lp.frequency.exponentialRampToValueAtTime(500, t + 0.16);
          lp.connect(lead);
          osc(lp, 'sawtooth', f, f, t, 0.2, 0.13);
          osc(lp, 'square', f * 2, f * 2, t, 0.08, 0.05);
        }
      }
      if (level >= 1) {
        const four = level === 3;
        if (s === 0 || s === 8 || (four && s % 4 === 0)) osc(drum, 'sine', 150, 42, t, 0.14, 0.7);
        if (s === 4 || s === 12) { nz(drum, t, 0.1, 0.35, 'highpass', 1500, 1500, 0.7); osc(drum, 'triangle', 210, 160, t, 0.05, 0.25); }
        if (s % 4 === 2 || (four && s % 2 === 1)) nz(drum, t, 0.03, 0.14, 'highpass', 7500, 7500, 0.7);
      }
    }
    return { start, setLevel, setSlow, get level() { return level; } };
  })();

  // ---------- The sounds ----------
  const api = {
    init, opts, buzz,
    get ready() { return !!ctx; },
    setOpt(name, val) {
      opts[name] = val; save();
      if (!ctx) return;
      if (name === 'sfx') sfxBus.gain.setTargetAtTime(val ? 1 : 0, ctx.currentTime, 0.05);
      if (name === 'music') musicBus.gain.setTargetAtTime(val ? 0.3 : 0, ctx.currentTime, 0.1);
    },
    suspend(on) { if (!ctx) return; if (on) ctx.suspend(); else ctx.resume(); },
    listen(pos, rx, rz) { ear.x = pos.x; ear.y = pos.y; ear.z = pos.z; ear.rx = rx; ear.rz = rz; },
    setIntensity(n) { if (ctx && n !== music.level) music.setLevel(n); },

    // Continuous loops, updated each frame.
    loops(speed, jetting, sirenPos) {
      if (!ctx || !loops.wind) return;
      const t = ctx.currentTime;
      const w = Math.max(0, Math.min(1, (speed - 9) / 26));
      loops.wind.g.gain.setTargetAtTime(w * 0.32, t, 0.15);
      loops.wind.f.frequency.setTargetAtTime(350 + speed * 45, t, 0.2);
      loops.jet.g.gain.setTargetAtTime(jetting ? 0.22 : 0, t, jetting ? 0.03 : 0.12);
      loops.jet.f.frequency.setTargetAtTime(jetting ? 700 + R() * 500 : 300, t, 0.05);
      const p = sirenPos ? place(sirenPos) : null;
      loops.siren.g.gain.setTargetAtTime(p ? p.g * 0.09 : 0, t, 0.2);
      if (p && loops.siren.sp) loops.siren.sp.pan.setTargetAtTime(p.pan, t, 0.1);
    },

    baa(pos, mega) {
      if (typeof pos === 'boolean') { mega = pos; pos = null; }
      if (!gate(mega ? 'mbaa' : 'baa', mega ? 0.6 : 0.3)) return;
      const t = now(), dur = mega ? 1.4 : 0.5 + R() * 0.35;
      const v = voice(pos, mega ? 0.9 : 0.6, mega ? 0.7 : 0.2, dur, mega ? 2 : 1);
      if (!v) return;
      const base = mega ? 190 : 300 + R() * 170;
      const o = ctx.createOscillator(); o.type = 'sawtooth';
      o.frequency.setValueAtTime(base * 0.9, t);
      o.frequency.linearRampToValueAtTime(base * 1.1, t + dur * 0.22);
      o.frequency.linearRampToValueAtTime(base * 0.8, t + dur);
      // A goat bleat is a fast amplitude flutter ("meh-eh-eh-eh") over a wobbling pitch.
      const am = ctx.createGain(); am.gain.value = 0.55;
      const fl = ctx.createOscillator(); fl.frequency.value = mega ? 13 : 18 + R() * 9;
      const fg = ctx.createGain(); fg.gain.value = 0.45; fl.connect(fg); fg.connect(am.gain);
      const vb = ctx.createOscillator(); vb.frequency.value = 6;
      const vg = ctx.createGain(); vg.gain.value = base * 0.04; vb.connect(vg); vg.connect(o.frequency);
      const g = ctx.createGain(); env(g.gain, t, 0.05, 1, dur, 0.5);
      o.connect(am); am.connect(g);
      const dest = mega ? shaper(v) : v;
      formants(dest, g, [[760, 5, 1.3], [1280, 6, 0.9], [2650, 8, 0.4]]);
      const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 900;
      const lg = ctx.createGain(); lg.gain.value = 0.25; g.connect(lp); lp.connect(lg); lg.connect(dest);
      for (const n of [o, fl, vb]) { n.start(t); n.stop(t + dur + 0.1); }
      if (mega) osc(v, 'sine', 70, 40, t, 0.6, 0.6);
    },

    // Impact with a material: wood knock, metal clang, flesh thud, rubber bop, hay crunch.
    hit(power, material, pos) {
      material = material || 'thud';
      if (!gate('hit' + material, material === 'wood' ? 0.035 : 0.05)) return;
      const t = now(), k = Math.min(1, 0.22 + power / 20);
      const v = voice(pos, 0.95 * k, 0.12, material === 'metal' ? 0.8 : 0.3, 0);
      if (!v) return;
      if (material === 'wood') {
        osc(v, 'triangle', 300 + R() * 120, 110, t, 0.1, 0.7);
        osc(v, 'sine', 700 + R() * 260, 640, t, 0.05, 0.3);
        nz(v, t, 0.06, 0.55, 'bandpass', 1600, 800, 2.5);
      } else if (material === 'metal') {
        const b = 170 + R() * 60;
        [1, 2.76, 5.4, 8.93].forEach((m, i) => osc(v, 'sine', b * m, b * m * 0.98, t, 0.7 - i * 0.12, 0.3 / (i + 1)));
        nz(v, t, 0.04, 0.6, 'highpass', 3500, 3500, 0.8);
        osc(v, 'triangle', 140, 55, t, 0.14, 0.6);
      } else if (material === 'flesh') {
        osc(v, 'sine', 150, 50, t, 0.17, 0.95);
        nz(v, t, 0.05, 0.55, 'highpass', 1900, 1900, 0.7);
      } else if (material === 'rubber') {
        osc(v, 'sine', 280, 120, t, 0.14, 0.8);
        osc(v, 'sine', 560, 240, t, 0.09, 0.25);
      } else if (material === 'hay') {
        nz(v, t, 0.18, 0.7, 'bandpass', 2600, 1200, 1.5);
        osc(v, 'sine', 120, 60, t, 0.1, 0.5);
      } else {
        osc(v, 'sine', 170, 45, t, 0.2, 1);
        nz(v, t, 0.07, 0.5, 'lowpass', 1400, 300, 1);
      }
    },

    swing() {
      if (!gate('swing', 0.12)) return;
      const v = voice(null, 0.4, 0, 0.25, 0); if (!v) return;
      nz(v, now(), 0.2, 0.6, 'bandpass', 500, 2400, 1.4, 0.04);
    },

    boom(pos) {
      if (!gate('boom', 0.06)) return;
      const t = now(), v = voice(pos, 1, 0.6, 2.6, 3);
      if (!v) return;
      const d = shaper(v);
      osc(v, 'sine', 115, 26, t, 1.4, 1, { a: 0.002 });               // sub thump
      osc(d, 'square', 85, 30, t, 0.28, 0.32);                         // chest punch
      nz(v, t, 0.05, 1, 'highpass', 2600, 2600, 0.5, 0.001);           // crack
      nz(d, t, 1.6, 0.75, 'lowpass', 3400, 110, 0.8, 0.004);           // fireball roar
      nz(v, t + 0.05, 2.3, 0.35, 'lowpass', 420, 60, 0.5, 0.25);       // rolling tail
      for (let i = 0; i < 16; i++) {                                   // debris crackle
        const tt = t + 0.12 + Math.pow(R(), 1.6) * 1.7;
        nz(v, tt, 0.02 + R() * 0.03, 0.28 * (1 - (tt - t) / 1.9), 'bandpass', 1500 + R() * 3800, null, 3);
      }
      const p = place(pos);
      if (p && p.g > 0.62) { api.shellshock(p.g); buzz([60, 40, 120]); }
      else if (p && p.g > 0.3) buzz(40);
    },

    // Close explosions muffle the whole mix and leave a ringing in your goat ears.
    shellshock(k) {
      const t = now();
      shockLP.frequency.cancelScheduledValues(t);
      shockLP.frequency.setValueAtTime(shockLP.frequency.value, t);
      shockLP.frequency.exponentialRampToValueAtTime(450, t + 0.04);
      shockLP.frequency.setTargetAtTime(20000, t + 0.5, 0.45);
      const o = ctx.createOscillator(), g = ctx.createGain();
      o.frequency.value = 3700 + R() * 400;
      env(g.gain, t + 0.05, 0.1, 0.035 * k, 2.4);
      o.connect(g); g.connect(limiter);
      o.start(t); o.stop(t + 2.6);
    },

    crash(pos) {
      if (!gate('crash', 0.2)) return;
      const t = now(), v = voice(pos, 0.9, 0.3, 1.2, 2);
      if (!v) return;
      const d = shaper(v);
      for (let i = 0; i < 6; i++) nz(d, t + i * 0.035 + R() * 0.02, 0.09, 0.5, 'bandpass', 600 + R() * 1800, null, 1.5);
      [1, 2.76, 5.4].forEach((m, i) => osc(v, 'sine', 230 * m, 215 * m, t, 0.9 - i * 0.2, 0.22 / (i + 1)));
      for (let i = 0; i < 9; i++) { const f = 3000 + R() * 4500; osc(v, 'sine', f, f, t + 0.08 + R() * 0.45, 0.08, 0.1); }
      osc(v, 'sine', 95, 35, t, 0.4, 0.9);
    },

    scream(pos, launch) {
      if (!gate('scream', 0.16)) return;
      const t = now(), dur = launch ? 1.05 : 0.55 + R() * 0.3;
      const v = voice(pos, 0.42, 0.25, dur, 1);
      if (!v) return;
      const f = 360 + R() * 400;
      const o = ctx.createOscillator(); o.type = 'sawtooth';
      if (launch) {
        o.frequency.setValueAtTime(f, t);
        o.frequency.linearRampToValueAtTime(f * 1.55, t + 0.12);
        o.frequency.exponentialRampToValueAtTime(f * 0.5, t + dur);
      } else {
        o.frequency.setValueAtTime(f * 0.9, t);
        o.frequency.linearRampToValueAtTime(f * 1.25, t + 0.1);
        o.frequency.linearRampToValueAtTime(f * 1.08, t + dur);
      }
      const vb = ctx.createOscillator(), vg = ctx.createGain();
      vb.frequency.value = 7 + R() * 3; vg.gain.value = f * 0.06; vb.connect(vg); vg.connect(o.frequency);
      const g = ctx.createGain(); env(g.gain, t, 0.03, 1, dur, 0.6);
      o.connect(g);
      formants(v, g, [[850, 6, 1], [1250, 7, 0.7], [2900, 9, 0.35]]);
      for (const n of [o, vb]) { n.start(t); n.stop(t + dur + 0.1); }
    },

    oof(pos) {
      if (!gate('oof', 0.12)) return;
      const t = now(), v = voice(pos, 0.5, 0.1, 0.25, 0);
      if (!v) return;
      const o = ctx.createOscillator(), g = ctx.createGain(); o.type = 'sawtooth';
      o.frequency.setValueAtTime(150 + R() * 60, t); o.frequency.exponentialRampToValueAtTime(85, t + 0.2);
      env(g.gain, t, 0.01, 1, 0.2); o.connect(g);
      formants(v, g, [[520, 6, 1.2], [1000, 6, 0.5]]);
      o.start(t); o.stop(t + 0.25);
    },

    honk(pos) {
      if (!gate('honk', 0.5)) return;
      const t = now(), v = voice(pos, 0.5, 0.12, 0.7, 1);
      if (!v) return;
      const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 1900; lp.connect(shaper(v));
      for (const b of (R() < 0.5 ? [0] : [0, 0.2])) {
        osc(lp, 'square', 415, 410, t + b, 0.16, 0.35, { a: 0.01, hold: 0.8 });
        osc(lp, 'square', 330, 328, t + b, 0.16, 0.3, { a: 0.01, hold: 0.8 });
      }
    },

    boing(pos) {
      if (!gate('boing', 0.1)) return;
      const t = now(), v = voice(pos, 0.7, 0.2, 0.7, 1);
      if (!v) return;
      osc(v, 'sine', 110, 640, t, 0.55, 0.8, { vib: [16, 60] });
      osc(v, 'triangle', 220, 1280, t, 0.4, 0.18, { vib: [16, 90] });
      osc(v, 'sine', 90, 40, t, 0.12, 0.6);
    },

    slurp(pos) {
      if (!gate('slurp', 0.1)) return;
      const t = now(), v = voice(pos, 0.6, 0.1, 0.5, 1);
      if (!v) return;
      const f = nz(v, t, 0.35, 0.7, 'bandpass', 2800, 500, 9);
      const l = ctx.createOscillator(), lg = ctx.createGain();
      l.frequency.value = 28; lg.gain.value = 400; l.connect(lg); lg.connect(f.frequency);
      l.start(t); l.stop(t + 0.4);
      osc(v, 'sine', 1100, 220, t, 0.25, 0.35);
      osc(v, 'sine', 300, 1500, t + 0.22, 0.05, 0.3);
    },

    chomp(pos) {
      if (!gate('chomp', 0.3)) return;
      const t = now(), v = voice(pos, 0.6, 0.05, 0.5, 0);
      if (!v) return;
      for (let i = 0; i < 3; i++) { nz(v, t + i * 0.13, 0.07, 0.5, 'bandpass', 1000, 600, 2); osc(v, 'sine', 210, 120, t + i * 0.13, 0.06, 0.4); }
    },

    yeet() {
      if (!gate('yeet', 0.2)) return;
      const t = now(), v = voice(null, 0.5, 0.25, 0.5, 1);
      if (!v) return;
      osc(v, 'sine', 420, 2600, t, 0.38, 0.4, { vib: [9, 30] });
      nz(v, t, 0.4, 0.45, 'bandpass', 400, 3200, 1.2, 0.05);
    },

    slideDown() {
      if (!gate('slide', 0.5)) return;
      const t = now(), v = voice(null, 0.35, 0.2, 0.8, 0);
      if (!v) return;
      osc(v, 'sine', 1900, 260, t, 0.7, 0.6, { vib: [7, 40], a: 0.02 });
    },

    land(power, pos) {
      if (!gate('land', 0.12)) return;
      const t = now(), v = voice(pos, Math.min(1, 0.3 + power / 25), 0.08, 0.4, 0);
      if (!v) return;
      osc(v, 'sine', 120, 40, t, 0.2, 1);
      nz(v, t, 0.15, 0.5, 'lowpass', 900, 200, 1);
      if (power > 14) { nz(v, t + 0.02, 0.25, 0.35, 'bandpass', 1400, 400, 3); buzz(30); }
    },

    whoosh(pos) {
      if (!gate('whoosh', 0.15)) return;
      const v = voice(pos, 0.55, 0.15, 0.5, 0); if (!v) return;
      nz(v, now(), 0.45, 0.7, 'bandpass', 300, 1800, 0.9, 0.08);
    },

    // Spin whoosh plus a ding that climbs with every flip.
    flip(n) {
      const t = now(), v = voice(null, 0.5, 0.3, 0.6, 1);
      if (!v) return;
      nz(v, t, 0.3, 0.4, 'bandpass', 600, 2500, 2);
      const f = 660 * Math.pow(2, Math.min(n, 8) * 2 / 12);
      osc(v, 'triangle', f, f, t + 0.05, 0.4, 0.3);
      osc(v, 'sine', f * 2, f * 2, t + 0.05, 0.3, 0.12);
    },

    combo(n) {
      if (!gate('combo', 0.05)) return;
      const t = now(), v = voice(null, 0.32, 0.3, 0.4, 1);
      if (!v) return;
      const scale = [0, 2, 4, 7, 9];
      const semis = scale[n % 5] + 12 * Math.min(2, Math.floor(n / 5));
      const f = 523.25 * Math.pow(2, semis / 12);
      osc(v, 'square', f, f, t, 0.12, 0.25);
      osc(v, 'triangle', f * 2, f * 2, t, 0.1, 0.18);
      osc(v, 'square', f, f, t + 0.13, 0.1, 0.08);
    },

    // The MLG air horn. Reserved for real mayhem.
    airhorn() {
      if (!gate('airhorn', 1)) return;
      const t = now(), v = voice(null, 0.55, 0.35, 1.3, 3);
      if (!v) return;
      const bp = ctx.createBiquadFilter(); bp.type = 'bandpass'; bp.frequency.value = 1400; bp.Q.value = 0.6;
      bp.connect(shaper(v));
      for (const [s, d] of [[0, 0.12], [0.16, 0.12], [0.32, 0.75]]) {
        for (const f of [466, 470, 932, 699]) osc(bp, 'sawtooth', f, f * 0.985, t + s, d, 0.18, { a: 0.01, hold: 0.85 });
      }
      buzz([40, 40, 40, 40, 160]);
    },

    // MAYHEM MODE: a wobble bass drop.
    rage() {
      const t = now(), v = voice(null, 0.7, 0.2, 1.6, 3);
      if (!v) return;
      const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.Q.value = 9; lp.frequency.value = 400;
      const w = ctx.createOscillator(), wg = ctx.createGain(); w.frequency.value = 6; wg.gain.value = 700;
      w.connect(wg); wg.connect(lp.frequency); w.start(t); w.stop(t + 1.6);
      lp.connect(shaper(v));
      osc(lp, 'sawtooth', 55, 52, t, 1.5, 0.7, { hold: 0.7 });
      osc(lp, 'square', 55.5, 52, t, 1.5, 0.4, { hold: 0.7 });
      osc(v, 'sine', 60, 30, t, 1.2, 0.8);
      nz(v, t, 0.3, 0.5, 'highpass', 800, 6000, 1);
    },

    golden(pos) {
      const t = now(), v = voice(pos, 0.6, 0.6, 2.2, 3);
      if (!v) return;
      [1046.5, 1318.5, 1568, 2093, 2637].forEach((f, i) => osc(v, 'triangle', f, f, t + i * 0.07, 0.5, 0.3));
      for (let i = 0; i < 14; i++) { const f = 3000 + R() * 5000; osc(v, 'sine', f, f, t + 0.2 + R() * 1.1, 0.12, 0.08); }
      const pad = ctx.createGain(); env(pad.gain, t + 0.1, 0.35, 1, 2, 0.5);
      for (const f of [261.6, 329.6, 392, 523.3]) { const o = ctx.createOscillator(); o.type = 'sawtooth'; o.frequency.value = f; o.connect(pad); o.start(t + 0.1); o.stop(t + 2.2); }
      formants(v, pad, [[400, 4, 0.5], [900, 5, 0.2]]);
      buzz([30, 30, 30]);
    },

    powerup() {
      const t = now(), v = voice(null, 0.5, 0.35, 1.2, 3);
      if (!v) return;
      const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.Q.value = 8;
      lp.frequency.setValueAtTime(300, t); lp.frequency.exponentialRampToValueAtTime(6000, t + 0.6);
      lp.connect(v);
      osc(lp, 'sawtooth', 150, 1200, t, 0.7, 0.4, { hold: 0.6 });
      [523, 659, 784, 1046, 1318].forEach((f, i) => osc(v, 'square', f, f, t + 0.3 + i * 0.06, 0.12, 0.12));
    },

    challenge() {
      const t = now(), v = voice(null, 0.45, 0.3, 1.2, 3);
      if (!v) return;
      [[523, 0, 0.12], [659, 0.12, 0.12], [784, 0.24, 0.12], [1046, 0.36, 0.6]].forEach(([f, s, d]) => {
        osc(v, 'square', f, f, t + s, d, 0.18, { hold: 0.7 });
        osc(v, 'sawtooth', f * 1.003, f, t + s, d, 0.08, { hold: 0.7 });
      });
    },

    // Bit-crushed stutter for the "physics bugs".
    glitch() {
      if (!gate('glitch', 0.3)) return;
      const t = now(), v = voice(null, 0.4, 0.1, 0.6, 1);
      if (!v) return;
      const c = shaper(v, crush);
      for (let i = 0; i < 14; i++) { const f = 120 + R() * 1800; osc(c, 'square', f, f, t + i * 0.032, 0.03, 0.4); }
      nz(c, t + 0.45, 0.06, 0.5, 'highpass', 4000, 4000, 1);
    },

    // Tape stop into slow motion, tape start back out. The whole mix gets darker while slow.
    slowmo(on) {
      slow = on;
      music.setSlow(on);
      if (!ctx) return;
      const t = now();
      tapeLP.frequency.cancelScheduledValues(t);
      tapeLP.frequency.setTargetAtTime(on ? 1300 : 20000, t, on ? 0.25 : 0.15);
      const v = voice(null, 0.5, 0.3, 0.9, 3);
      if (!v) return;
      osc(v, 'sawtooth', on ? 320 : 40, on ? 35 : 320, t, 0.8, 0.35, { hold: 0.5 });
      nz(v, t, 0.8, 0.3, 'lowpass', on ? 2400 : 200, on ? 150 : 2400, 2, 0.05);
    },

    moon(on) {
      const t = now(), v = voice(null, 0.4, 0.5, 1.3, 3);
      if (!v) return;
      const o = ctx.createOscillator(), g = ctx.createGain();
      o.frequency.setValueAtTime(on ? 300 : 900, t);
      o.frequency.linearRampToValueAtTime(on ? 950 : 500, t + 0.5);
      o.frequency.linearRampToValueAtTime(on ? 520 : 200, t + 1.1);
      const vb = ctx.createOscillator(), vg = ctx.createGain();
      vb.frequency.value = 5.5; vg.gain.value = 18; vb.connect(vg); vg.connect(o.frequency);
      env(g.gain, t, 0.08, 0.6, 1.2, 0.6);
      o.connect(g); g.connect(v);
      for (const n of [o, vb]) { n.start(t); n.stop(t + 1.3); }
    },

    scratch() {
      const t = now(), v = voice(null, 0.55, 0.05, 0.5, 2);
      if (!v) return;
      const f = nz(v, t, 0.45, 0.8, 'bandpass', 300, null, 4);
      const pts = [[0, 300], [0.08, 3000], [0.16, 400], [0.27, 2600], [0.42, 300]];
      f.frequency.cancelScheduledValues(t);
      pts.forEach(([s, hz]) => f.frequency.linearRampToValueAtTime(hz, t + s));
    },

    bombWhistle(pos, delay) {
      const t = now() + (delay || 0), v = voice(pos, 0.25, 0.2, 1.4 + (delay || 0), 0);
      if (!v) return;
      osc(v, 'sine', 2300 + R() * 300, 520, t, 1.3, 0.5, { a: 0.1, hold: 0.7 });
    },

    jingle(pos) {
      if (!gate('jingle', 0.18)) return;
      const t = now(), v = voice(pos, 0.18, 0.15, 0.4, 0);
      if (!v) return;
      osc(v, 'sine', 2093, 2093, t, 0.3, 0.5);
      osc(v, 'sine', 2637, 2637, t + 0.04, 0.25, 0.3);
      osc(v, 'sine', 5800, 5800, t, 0.06, 0.15);
    },

    tick() {
      if (!gate('tick', 0.03)) return;
      const v = voice(null, 0.25, 0, 0.1, 2); if (!v) return;
      osc(v, 'square', 1400, 900, now(), 0.04, 0.4);
    },

    // Compatibility with the older, smaller sound API.
    bonk(power, pos) { api.hit(power || 8, 'thud', pos); },
    pickup() { api.powerup(); },
    jet() { jetHold = (ctx ? ctx.currentTime : 0) + 0.15; },
    quack(pos) {
      if (!gate('quack', 0.2)) return;
      const t = now(), v = voice(pos, 0.45, 0.1, 0.3, 0);
      if (!v) return;
      const o = ctx.createOscillator(), g = ctx.createGain(); o.type = 'sawtooth';
      o.frequency.setValueAtTime(640, t); o.frequency.exponentialRampToValueAtTime(380, t + 0.16);
      env(g.gain, t, 0.01, 1, 0.18); o.connect(g);
      formants(v, g, [[900, 7, 1.1], [1700, 7, 0.6]]);
      o.start(t); o.stop(t + 0.25);
    }
  };
  let jetHold = 0;
  const loopsRaw = api.loops;
  api.loops = (speed, jetting, sirenPos) => loopsRaw(speed, jetting || (ctx && ctx.currentTime < jetHold), sirenPos);
  return api;
})();
