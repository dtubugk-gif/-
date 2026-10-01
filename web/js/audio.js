// WebAudio chiptune: sound effects + a small generated loop per world.
(function () {
'use strict';
let AC = null, master = null, muted = false;
const A = { on: false, tempoMul: 1, track: 'grass', step: 0, next: 0 };

function init() {
  if (AC) { if (AC.state === 'suspended') AC.resume(); return; }
  try {
    AC = new (window.AudioContext || window.webkitAudioContext)();
    master = AC.createGain(); master.gain.value = muted ? 0 : 0.5; master.connect(AC.destination);
  } catch (e) { AC = null; }
}
function tone(freq, dur, type, vol, slide, delay) {
  if (!AC) return;
  const t = AC.currentTime + (delay || 0);
  const o = AC.createOscillator(), g = AC.createGain();
  o.type = type || 'square'; o.frequency.setValueAtTime(freq, t);
  if (slide) o.frequency.exponentialRampToValueAtTime(slide, t + dur);
  g.gain.setValueAtTime(vol || 0.1, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
  o.connect(g); g.connect(master); o.start(t); o.stop(t + dur + 0.02);
}
function noise(dur, vol) {
  if (!AC) return;
  const n = Math.floor(AC.sampleRate * dur), buf = AC.createBuffer(1, n, AC.sampleRate), d = buf.getChannelData(0);
  for (let i = 0; i < n; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / n);
  const s = AC.createBufferSource(), g = AC.createGain(); g.gain.value = vol;
  s.buffer = buf; s.connect(g); g.connect(master); s.start();
}
const seq = (notes, step, type, vol) => notes.forEach((f, i) => f && tone(f, step * 1.1, type || 'square', vol || 0.08, null, i * step));
const SFX = {
  jump: () => tone(330, 0.18, 'square', 0.08, 760),
  coin: () => { tone(988, 0.07, 'square', 0.08); tone(1319, 0.25, 'square', 0.08, null, 0.07); },
  stomp: () => tone(260, 0.12, 'square', 0.1, 70),
  bump: () => tone(150, 0.08, 'triangle', 0.25, 90),
  brk: () => { noise(0.18, 0.25); tone(220, 0.12, 'square', 0.06, 60); },
  sprout: () => seq([392, 440, 494, 523, 587, 659], 0.04),
  power: () => seq([523, 659, 784, 1047, 784, 1047, 1319], 0.055),
  hurt: () => tone(700, 0.4, 'square', 0.08, 120),
  die: () => seq([660, 0, 622, 587, 554, 0, 494, 440, 392, 330], 0.11, 'square', 0.09),
  flag: () => tone(1400, 0.9, 'square', 0.06, 220),
  win: () => seq([523, 659, 784, 1047, 0, 880, 1047, 1319, 0, 0, 1568], 0.1, 'square', 0.08),
  over: () => seq([392, 0, 330, 0, 262, 247, 220], 0.16, 'triangle', 0.18),
  oneup: () => seq([660, 784, 1319, 1047, 1175, 1568], 0.07),
  tick: () => tone(1600, 0.03, 'square', 0.03),
  hurry: () => seq([880, 0, 880, 0, 880], 0.07),
  kick: () => { tone(180, 0.08, 'triangle', 0.25, 90); noise(0.05, 0.1); },
  star: () => seq([784, 988, 1175, 1568, 1175, 988, 784, 988, 1175, 1568], 0.045),
  boss: () => { tone(120, 0.35, 'sawtooth', 0.12, 60); noise(0.2, 0.2); },
  select: () => tone(880, 0.06, 'square', 0.06),
  go: () => seq([523, 659, 784], 0.07),
};

// ---- music ----
const midi = m => 440 * Math.pow(2, (m - 69) / 12);
const SCALES = { major: [0, 2, 4, 5, 7, 9, 11], minor: [0, 2, 3, 5, 7, 8, 10], phryg: [0, 1, 3, 5, 7, 8, 10], penta: [0, 2, 4, 7, 9] };
const TRACKS = {
  grass:  { root: 72, scale: 'major', bass: [48, 53, 50, 48, 45, 43, 43, 48], sp: 0.2, seed: 11, lead: 'square' },
  desert: { root: 69, scale: 'phryg', bass: [45, 46, 45, 43, 45, 46, 48, 45], sp: 0.19, seed: 23, lead: 'square' },
  cave:   { root: 64, scale: 'minor', bass: [40, 43, 41, 40, 38, 36, 38, 40], sp: 0.24, seed: 37, lead: 'triangle' },
  snow:   { root: 76, scale: 'penta', bass: [48, 52, 55, 52, 45, 50, 53, 50], sp: 0.21, seed: 41, lead: 'triangle' },
  sky:    { root: 74, scale: 'major', bass: [50, 55, 52, 57, 50, 55, 52, 47], sp: 0.17, seed: 53, lead: 'square' },
  lava:   { root: 67, scale: 'minor', bass: [43, 43, 46, 41, 43, 43, 48, 46], sp: 0.16, seed: 67, lead: 'sawtooth' },
  boss:   { root: 65, scale: 'phryg', bass: [41, 42, 41, 39, 41, 42, 44, 41], sp: 0.13, seed: 79, lead: 'sawtooth' },
};
const melCache = {};
function melody(name) {
  if (melCache[name]) return melCache[name];
  const tr = TRACKS[name], sc = SCALES[tr.scale];
  let s = tr.seed; const rnd = () => (s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
  const out = []; let deg = 2;
  for (let i = 0; i < 64; i++) {
    const beat = i % 8;
    if (i % 32 >= 28) { out.push(0); continue; }
    if (beat % 2 === 1 && rnd() < 0.55) { out.push(0); continue; }
    deg += Math.round((rnd() - 0.5) * 4);
    if (i % 8 === 0 && rnd() < 0.5) deg = [0, 2, 4][Math.floor(rnd() * 3)];
    deg = Math.max(-2, Math.min(8, deg));
    const oct = Math.floor(deg / sc.length), idx = ((deg % sc.length) + sc.length) % sc.length;
    out.push(tr.root + sc[idx] + oct * 12);
  }
  return (melCache[name] = out);
}
function note(m, t, dur, type, vol) {
  const o = AC.createOscillator(), g = AC.createGain();
  o.type = type; o.frequency.value = midi(m);
  g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
  o.connect(g); g.connect(master); o.start(t); o.stop(t + dur + 0.02);
}
setInterval(() => {
  if (!AC || !A.on || AC.state !== 'running') { if (AC) A.next = AC.currentTime + 0.05; return; }
  const tr = TRACKS[A.track] || TRACKS.grass, mel = melody(A.track in TRACKS ? A.track : 'grass');
  const sp = tr.sp * A.tempoMul;
  if (A.next < AC.currentTime) A.next = AC.currentTime + 0.05;
  while (A.next < AC.currentTime + 0.3) {
    const s = A.step % 64, m = mel[s];
    if (m) note(m, A.next, sp * 0.9, tr.lead, 0.03);
    if (s % 4 === 0) { const r = tr.bass[s >> 3]; note(s % 8 === 0 ? r : r + 7, A.next, sp * 1.8, 'triangle', 0.11); }
    A.step++; A.next += sp;
  }
}, 60);

window.SFB = window.SFB || {};
window.SFB.Audio = {
  init, SFX, music: A,
  setMuted(m) { muted = m; if (master) master.gain.value = m ? 0 : 0.5; },
  get muted() { return muted; },
  suspend() { if (AC && AC.state === 'running') AC.suspend(); },
  resume() { if (AC && AC.state === 'suspended') AC.resume(); },
};
})();
