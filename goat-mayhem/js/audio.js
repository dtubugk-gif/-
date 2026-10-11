// Every sound is synthesized with WebAudio, so the game ships with no audio files.
window.G = window.G || {};

G.sfx = (function () {
  let ctx = null, master = null, noiseBuf = null;
  const last = {};

  function init() {
    if (ctx) { ctx.resume(); return; }
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    ctx = new AC();
    master = ctx.createGain();
    master.gain.value = 0.55;
    master.connect(ctx.destination);
    noiseBuf = ctx.createBuffer(1, ctx.sampleRate * 1.5, ctx.sampleRate);
    const d = noiseBuf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
  }

  // Throttle so a pile of crates doesn't make 400 sounds in one frame.
  function ok(name, gap) {
    if (!ctx) return false;
    const t = ctx.currentTime;
    if (last[name] && t - last[name] < gap) return false;
    last[name] = t;
    return true;
  }

  function env(g, t, a, peak, dec) {
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(peak, t + a);
    g.gain.exponentialRampToValueAtTime(0.0001, t + a + dec);
  }

  function osc(type, f0, f1, dur, vol, delay) {
    const t = ctx.currentTime + (delay || 0);
    const o = ctx.createOscillator(), g = ctx.createGain();
    o.type = type;
    o.frequency.setValueAtTime(f0, t);
    o.frequency.exponentialRampToValueAtTime(Math.max(20, f1), t + dur);
    env(g, t, 0.01, vol, dur);
    o.connect(g).connect(master);
    o.start(t); o.stop(t + dur + 0.05);
    return o;
  }

  function noise(dur, vol, freq, q, delay) {
    const t = ctx.currentTime + (delay || 0);
    const s = ctx.createBufferSource(), f = ctx.createBiquadFilter(), g = ctx.createGain();
    s.buffer = noiseBuf;
    f.type = 'bandpass'; f.frequency.value = freq; f.Q.value = q;
    env(g, t, 0.005, vol, dur);
    s.connect(f).connect(g).connect(master);
    s.start(t); s.stop(t + dur + 0.05);
  }

  return {
    init,
    baa() {
      if (!ok('baa', 0.35)) return;
      // A goat bleat is a buzzy tone with a fast, wide vibrato.
      const t = ctx.currentTime, dur = 0.55 + Math.random() * 0.3;
      const o = ctx.createOscillator(), lfo = ctx.createOscillator(), lg = ctx.createGain();
      const f = ctx.createBiquadFilter(), g = ctx.createGain();
      const base = 330 + Math.random() * 160;
      o.type = 'sawtooth';
      o.frequency.setValueAtTime(base, t);
      o.frequency.linearRampToValueAtTime(base * 0.8, t + dur);
      lfo.frequency.value = 26 + Math.random() * 10; lg.gain.value = base * 0.09;
      lfo.connect(lg).connect(o.frequency);
      f.type = 'bandpass'; f.frequency.value = 1300; f.Q.value = 1.4;
      env(g, t, 0.04, 0.5, dur);
      o.connect(f).connect(g).connect(master);
      o.start(t); lfo.start(t); o.stop(t + dur + 0.1); lfo.stop(t + dur + 0.1);
    },
    bonk(power) {
      if (!ok('bonk', 0.06)) return;
      const v = Math.min(0.6, 0.12 + power * 0.03);
      osc('triangle', 220 + Math.random() * 120, 60, 0.18, v);
      noise(0.08, v * 0.6, 900, 0.8);
    },
    boing() {
      if (!ok('boing', 0.12)) return;
      const t = ctx.currentTime;
      const o = ctx.createOscillator(), g = ctx.createGain(), lfo = ctx.createOscillator(), lg = ctx.createGain();
      o.type = 'sine';
      o.frequency.setValueAtTime(140, t);
      o.frequency.exponentialRampToValueAtTime(520, t + 0.35);
      lfo.frequency.value = 18; lg.gain.value = 40;
      lfo.connect(lg).connect(o.frequency);
      env(g, t, 0.01, 0.45, 0.5);
      o.connect(g).connect(master);
      o.start(t); lfo.start(t); o.stop(t + 0.6); lfo.stop(t + 0.6);
    },
    boom() {
      if (!ok('boom', 0.08)) return;
      osc('sine', 120, 28, 1.1, 0.9);
      noise(1.2, 0.9, 180, 0.4);
      noise(0.4, 0.5, 2000, 0.6);
    },
    scream() {
      if (!ok('scream', 0.25)) return;
      const f = 500 + Math.random() * 400;
      const o = osc('square', f, f * 1.6, 0.5, 0.12);
      osc('sawtooth', f * 1.01, f * 1.5, 0.5, 0.08);
      return o;
    },
    honk() {
      if (!ok('honk', 0.4)) return;
      osc('square', 392, 380, 0.25, 0.18);
      osc('square', 311, 300, 0.25, 0.14);
    },
    pickup() {
      if (!ctx) return;
      [523, 659, 784, 1046].forEach((n, i) => osc('triangle', n, n, 0.18, 0.3, i * 0.07));
    },
    combo(n) {
      if (!ok('combo', 0.05)) return;
      const f = 400 + Math.min(n, 12) * 60;
      osc('triangle', f, f * 1.5, 0.15, 0.2);
    },
    slurp() {
      if (!ok('slurp', 0.1)) return;
      osc('sine', 900, 180, 0.22, 0.3);
      noise(0.15, 0.2, 3000, 2);
    },
    whoosh() {
      if (!ok('whoosh', 0.15)) return;
      noise(0.35, 0.35, 700, 0.7);
    },
    jet() {
      if (!ok('jet', 0.07)) return;
      noise(0.12, 0.18, 400 + Math.random() * 300, 0.5);
    },
    glitch() {
      if (!ok('glitch', 0.3)) return;
      for (let i = 0; i < 5; i++) osc('square', 200 + Math.random() * 1600, 100 + Math.random() * 800, 0.05, 0.12, i * 0.04);
    }
  };
})();
