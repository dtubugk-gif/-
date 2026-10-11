(() => {
'use strict';
const { Engine, Bodies, Body, Composite, Constraint, Events, Sleeping } = Matter;

// ======================================================================= constants
const GROUND = 900;
const SLING_X = 320;
const SLING_Y = GROUND - 160;
const VMAX = 30;                       // launch speed at full pull (px per 1/60 s)
const STEP = 1000 / 60;                // one game tick
const SUBSTEPS = 2;                    // physics runs at 120 Hz for stable stacks and no tunnelling
const G_STEP = 0.001 * STEP * STEP;    // velocity gained from gravity per tick
const PULL_LEN = 120;
const REDUCED = matchMedia('(prefers-reduced-motion: reduce)').matches;
const CAT_GROUND = 0x0002, CAT_FRAG = 0x0004;

const KINDS = {
  face:  { shape: 'circle', r: 34, density: 0.0055, ring: '#ff7a1a', trail: '#ffd0a8', rest: 0.32 },
  split: { shape: 'circle', r: 29, density: 0.005,  ring: '#2f9bff', trail: '#bfe0ff', rest: 0.32 },
  mini:  { shape: 'circle', r: 22, density: 0.0055, ring: '#2f9bff', trail: '#bfe0ff', rest: 0.3 },
  bomb:  { shape: 'circle', r: 40, density: 0.006,  ring: '#ff3b3b', trail: '#ffc2c2', rest: 0.2 },
  foot:  { shape: 'rect', w: 46, h: 88, density: 0.0085, trail: '#ffffff', rest: 0.1 },
};
const MATS = {
  wood:  { density: 0.0018, friction: 0.8, hp: 240,  points: 500,  fill: '#c98b4a', dark: '#7a4a1e', light: '#e2a96a' },
  glass: { density: 0.0012, friction: 0.5, hp: 80,   points: 300,  fill: '#bfeaff', dark: '#4aa3cf', light: '#ffffff' },
  stone: { density: 0.004,  friction: 0.9, hp: 900,  points: 1000, fill: '#a3a6ad', dark: '#5d6068', light: '#c9ccd2' },
  tnt:   { density: 0.0016, friction: 0.7, hp: 45,   points: 300,  fill: '#e8402b', dark: '#7a1608', light: '#ffd23f' },
  iron:  { density: 0.012,  friction: 0.6, hp: 1e9,  points: 0,    fill: '#5a5f6b', dark: '#2a2d33', light: '#9aa1ad' },
};
const THEMES = [
  { name: 'יום',   sky: ['#5fbff5', '#bfe8ff', '#f2fbff'], far: '#9fd9a0', near: '#7cc77a', grass: '#5cc93b', grass2: '#48a82b', dirt: '#8a5a2b', dirt2: '#6f4520', sun: '#fff3a8', glow: 'rgba(255,240,170,0.5)', cloud: 'rgba(255,255,255,0.92)', stars: false },
  { name: 'שקיעה', sky: ['#ff8a5c', '#ffc27a', '#ffe8b8'], far: '#d99a7a', near: '#b97a5a', grass: '#9cc93b', grass2: '#7ea82b', dirt: '#7a4a2b', dirt2: '#5f3520', sun: '#ffdb6e', glow: 'rgba(255,200,120,0.55)', cloud: 'rgba(255,230,210,0.9)', stars: false },
  { name: 'לילה',  sky: ['#0b1440', '#23306b', '#3d4a8a'], far: '#2c3b6e', near: '#22305a', grass: '#3e8f3a', grass2: '#2f6e2b', dirt: '#4a3420', dirt2: '#3a2716', sun: '#f4f1d0', glow: 'rgba(220,230,255,0.25)', cloud: 'rgba(150,165,220,0.35)', stars: true },
];

const rnd = Math.random;
const rf = (a, b) => a + rnd() * (b - a);
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
const V = (b) => Body.getVelocity(b);      // velocity in per-1/60s units, independent of substeps
const SPD = (b) => Body.getSpeed(b);

// ======================================================================= lines
const LINES = {
  face: ['יאללה!', 'בלההה!', 'תפסו אותי!', 'ווווי!', 'אני עף!'],
  split: ['שלושה זה יותר!', 'אחד בשביל כולם!', 'יאללה צוות!'],
  bomb: ['אני הולך להתפוצץ!', 'תתרחקו!', 'בום בום!'],
  foot: ['הגבס יוצא לקרב!', 'לא לדרוך עליי!', 'מי צריך נעליים?'],
  taunt: ['פספסת!', 'זה כל מה שיש לך?', 'חה חה חה!', 'לך תצחצח שיניים!', 'איכס, לשון!', 'אפילו לא התקרבת!', 'עוד פעם?'],
  idle: ['משעמם...', 'אנחנו מחכים!', 'מישהו מריח גבס?', 'שמענו שאתה גרוע בזה', 'פה חם, ככה?'],
  scared: ['אוי לא!', 'אמאלה!', 'הצילו!', 'רק לא הלשון!'],
};

// ======================================================================= images
const imgFace = new Image(); imgFace.src = '__FACE__';
const imgFoot = new Image(); imgFoot.src = '__FOOT__';

// ======================================================================= storage
const store = {
  get(k, d) { try { const v = localStorage.getItem('facefling2_' + k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem('facefling2_' + k, JSON.stringify(v)); } catch (e) { /* unavailable */ } },
};
const save = { stars: store.get('stars', []), best: store.get('best', []), sound: store.get('sound', true) };

// ======================================================================= audio (all synthesised)
const RATE = 22050;
let actx = null, master = null, buffers = {}, musicSrc = null, active = 0;
const synth = (() => {
  const buf = (s) => new Float32Array(Math.floor(s * RATE));
  const noise = () => Math.random() * 2 - 1;
  const fr = (p) => p - Math.floor(p);
  const saw = (p) => 2 * fr(p) - 1;
  const tri = (p) => 1 - 4 * Math.abs(fr(p) - 0.5);
  const { exp, sin, PI, min, pow, tanh } = Math;
  const svf = () => { let low = 0, band = 0; return (x, fc, q) => { const f = 2 * sin(PI * Math.min(fc, RATE / 4) / RATE); low += f * band; const high = x - low - q * band; band += f * high; return band; }; };
  const voice = (d, f0fn, formants, gain) => {
    const b = buf(d), fs = formants.map(() => svf()); let ph = 0;
    for (let i = 0; i < b.length; i++) {
      const t = i / RATE; ph += f0fn(t) / RATE;
      const src = saw(ph);
      let v = 0; for (let k = 0; k < formants.length; k++) v += fs[k](src, formants[k][0], 0.25) * formants[k][1];
      b[i] = tanh(v * 1.4) * gain(t);
    }
    return b;
  };
  return {
    stretch() { const d = 0.35, b = buf(d); let ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; ph += (90 + 260 * t / d) / RATE; b[i] = saw(ph) * 0.18 * min(1, t * 30) * (1 - t / d) * (0.6 + 0.4 * sin(t * 90)); } return b; },
    launch() { const d = 0.5, b = buf(d), f = svf(); let ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; ph += (500 + 1100 * t / d) / RATE; b[i] = (f(noise(), 600 + 2600 * t / d, 0.6) * 0.5 + sin(ph * 2 * PI) * 0.12) * min(1, t * 20) * pow(1 - t / d, 1.4); } return b; },
    bleh() { const d = 0.5; return voice(d, (t) => (230 - 90 * t / d) * (1 + 0.06 * sin(t * 2 * PI * 7)), [[650, 0.9], [1750, 0.5]], (t) => 0.5 * min(1, t * 25) * min(1, (d - t) * 6)); },
    wee() { const d = 0.6; return voice(d, (t) => 300 + 260 * Math.sin(Math.min(1, t / d) * PI * 0.9), [[320, 0.9], [2300, 0.4]], (t) => 0.45 * min(1, t * 20) * min(1, (d - t) * 5)); },
    laugh() {
      const d = 0.9, b = buf(d), f1 = svf(), f2 = svf(); let ph = 0;
      for (let i = 0; i < b.length; i++) {
        const t = i / RATE, k = Math.floor(t / 0.15), lt = t - k * 0.15;
        ph += (520 + k * 40 - lt * 400) / RATE;
        const env = lt < 0.1 ? min(1, lt * 60) * (1 - lt / 0.1) : 0;
        b[i] = tanh((f1(saw(ph), 900, 0.3) + f2(noise() * 0.4, 2400, 0.4) * 0.6 * exp(-lt * 40)) * 1.6) * env * 0.5;
      }
      return b;
    },
    scream() { const d = 0.55; return voice(d, (t) => 700 + 200 * sin(t * 2 * PI * 9) + 300 * t / d, [[900, 0.9], [2600, 0.5]], (t) => 0.32 * min(1, t * 30) * min(1, (d - t) * 6)); },
    ghost() { const d = 1.0, b = buf(d); let ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; ph += (520 - 260 * t / d + 25 * sin(t * 2 * PI * 6)) / RATE; b[i] = sin(ph * 2 * PI) * 0.28 * min(1, t * 10) * min(1, (d - t) * 4); } return b; },
    boing() { const d = 0.45, b = buf(d); let ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; ph += (180 + 260 * exp(-t * 6) * (1 + 0.4 * sin(t * 2 * PI * 22))) / RATE; b[i] = (sin(ph * 2 * PI) * 0.7 + tri(ph * 2) * 0.2) * exp(-t * 7) * 0.6; } return b; },
    twang() { const d = 0.5, b = buf(d); let ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; ph += (330 * (1 + 0.05 * exp(-t * 10))) / RATE; b[i] = (saw(ph) * 0.5 + noise() * 0.3 * exp(-t * 60)) * exp(-t * 9) * 0.5; } return b; },
    balloon() { const d = 0.18, b = buf(d); for (let i = 0; i < b.length; i++) { const t = i / RATE; b[i] = noise() * exp(-t * 40) * 0.8 + sin(2 * PI * 140 * t) * exp(-t * 30) * 0.4; } return b; },
    ouch() { const d = 0.16, b = buf(d); let ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; ph += (900 + 900 * t / d) / RATE; b[i] = (fr(ph) < 0.5 ? 1 : -1) * 0.18 * min(1, t * 80) * (1 - t / d); } return b; },
    boom() { const d = 1.3, b = buf(d); let lp = 0, ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; lp += (noise() - lp) * (0.55 * exp(-t * 5) + 0.02); ph += (28 + 120 * exp(-t * 9)) / RATE; b[i] = tanh((lp * 2.2 + sin(ph * 2 * PI) * 1.3) * exp(-t * 2.6) * min(1, t * 400)) * 0.85; } return b; },
    stomp() { const d = 1.0, b = buf(d); let lp = 0, ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; lp += (noise() - lp) * (0.4 * exp(-t * 6) + 0.02); ph += (30 + 110 * exp(-t * 10)) / RATE; b[i] = tanh((lp * 1.8 + sin(ph * 2 * PI) * 1.2) * exp(-t * 3.2) * min(1, t * 300)) * 0.8; } return b; },
    wood() { const d = 0.16, b = buf(d); let ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; ph += (210 - 120 * t / d) / RATE; b[i] = (sin(ph * 2 * PI) * 0.7 + noise() * 0.35 * exp(-t * 60)) * exp(-t * 28) * 0.7; } return b; },
    woodBreak() { const d = 0.5, b = buf(d); let lp = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; lp += (noise() - lp) * 0.28; b[i] = lp * ((Math.floor(t * 42) % 3 === 0) ? 1 : 0.35) * exp(-t * 6); } return b; },
    glass() { const d = 0.7, b = buf(d), pings = [2350, 3120, 4180, 2790, 3600]; for (let i = 0; i < b.length; i++) { const t = i / RATE; let s = noise() * exp(-t * 22) * 0.45; for (let k = 0; k < pings.length; k++) { const st = t - k * 0.035; if (st > 0) s += sin(2 * PI * pings[k] * st) * exp(-st * 12) * 0.16; } b[i] = s; } return b; },
    stone() { const d = 0.35, b = buf(d); let lp = 0, ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; lp += (noise() - lp) * 0.08; ph += (90 - 40 * t / d) / RATE; b[i] = (lp * 1.5 + sin(ph * 2 * PI) * 0.7) * exp(-t * 12) * 0.85; } return b; },
    pop() { const d = 0.35, b = buf(d); let ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; ph += (90 + 700 * exp(-t * 18)) / RATE; b[i] = (sin(ph * 2 * PI) * 0.7 + noise() * 0.25 * exp(-t * 30)) * exp(-t * 9) * 0.75; } return b; },
    win() { const d = 1.4, b = buf(d), n = [523.25, 659.25, 783.99, 1046.5, 783.99, 1046.5, 1318.5]; let ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE, k = Math.min(n.length - 1, Math.floor(t / 0.12)), nt = t - k * 0.12; ph += n[k] / RATE; b[i] = (tri(ph) * 0.6 + (fr(ph) < 0.5 ? 0.2 : -0.2)) * exp(-nt * (k === n.length - 1 ? 2.5 : 9)) * 0.5; } return b; },
    lose() { const d = 1.2, b = buf(d), f = svf(); let ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE; ph += (330 * pow(0.5, t / d * 1.2)) / RATE; b[i] = f(saw(ph), 500 + 900 * (0.5 + 0.5 * sin(t * 2 * PI * 3)), 0.3) * min(1, (d - t) * 4) * 0.5; } return b; },
    star() { const d = 0.5, b = buf(d); for (let i = 0; i < b.length; i++) { const t = i / RATE; b[i] = (sin(2 * PI * 1568 * t) * 0.5 + sin(2 * PI * 2349 * t) * 0.3) * exp(-t * 7) * 0.5; } return b; },
    combo() { const d = 0.6, b = buf(d), n = [784, 988, 1175, 1568]; let ph = 0; for (let i = 0; i < b.length; i++) { const t = i / RATE, k = Math.min(3, Math.floor(t / 0.08)); ph += n[k] / RATE; b[i] = (fr(ph) < 0.5 ? 0.25 : -0.25) * exp(-(t - k * 0.08) * 12) * 0.7; } return b; },
    click() { const d = 0.05, b = buf(d); for (let i = 0; i < b.length; i++) { const t = i / RATE; b[i] = sin(2 * PI * 900 * t) * exp(-t * 80) * 0.5; } return b; },
    music() {
      const step = 60 / 112 / 4, steps = 64, b = buf(step * steps);
      const roots = [261.63, 196.0, 220.0, 174.61], third = [4, 4, 3, 4];
      let bassPh = 0, arpPh = 0, lastBeat = -1, kickPh = 0;
      for (let i = 0; i < b.length; i++) {
        const t = i / RATE, s = Math.min(Math.floor(t / step), steps - 1), st = t - s * step, bar = Math.floor(s / 16), root = roots[bar];
        const tones = [0, third[bar], 7, 12];
        arpPh += root * 2 * pow(2, tones[s % 4] / 12) / RATE;
        const arp = (tri(arpPh) * 0.7 + (fr(arpPh) < 0.5 ? 0.15 : -0.15)) * exp(-st * 14) * 0.16;
        const e8 = Math.floor(s / 2), st8 = t - e8 * 2 * step;
        bassPh += root / 2 * (e8 % 2 ? 1.5 : 1) / RATE;
        const bass = tri(bassPh) * exp(-st8 * 6) * 0.35;
        const beat = Math.floor(s / 4), stb = t - beat * 4 * step;
        if (beat !== lastBeat) { lastBeat = beat; kickPh = 0; }
        kickPh += (50 + 90 * exp(-stb * 30)) / RATE;
        const kick = beat % 2 === 0 ? sin(kickPh * 2 * PI) * exp(-stb * 12) * 0.5 : 0;
        const shaker = s % 2 === 1 ? noise() * exp(-st * 45) * 0.06 : 0;
        const clap = beat % 2 === 1 ? noise() * exp(-stb * 22) * 0.16 : 0;
        b[i] = tanh((arp + bass + kick + shaker + clap) * 1.1) * 0.8;
      }
      return b;
    },
  };
})();
function initAudio() {
  if (actx) return;
  const AC = window.AudioContext || window.webkitAudioContext;
  if (!AC) return;
  try {
    actx = new AC();
    master = actx.createGain(); master.gain.value = 0.9; master.connect(actx.destination);
    for (const k of Object.keys(synth)) {
      const data = synth[k]();
      const ab = actx.createBuffer(1, data.length, RATE);
      ab.getChannelData(0).set(data);
      buffers[k] = ab;
    }
  } catch (e) { actx = null; }
}
function unlockAudio() {
  initAudio();
  try { if (navigator.audioSession) navigator.audioSession.type = 'playback'; } catch (e) { /* unsupported */ }
  if (!actx) return;
  if (actx.state !== 'running') actx.resume().then(() => { if (save.sound) startMusic(); }).catch(() => {});
  else if (save.sound) startMusic();
}
function sfx(name, vol = 0.7, rate = 1) {
  if (!save.sound || !actx || actx.state !== 'running' || active > 22 || !buffers[name]) return;
  const s = actx.createBufferSource(); s.buffer = buffers[name]; s.playbackRate.value = clamp(rate, 0.5, 2);
  const g = actx.createGain(); g.gain.value = clamp(vol, 0, 1);
  s.connect(g); g.connect(master); active++;
  s.onended = () => { active--; s.disconnect(); g.disconnect(); };
  s.start();
}
function startMusic() {
  if (!save.sound || !actx || musicSrc || actx.state !== 'running') return;
  musicSrc = actx.createBufferSource(); musicSrc.buffer = buffers.music; musicSrc.loop = true;
  const g = actx.createGain(); g.gain.value = 0.25;
  musicSrc.connect(g); g.connect(master); musicSrc.start();
}
function stopMusic() { if (musicSrc) { try { musicSrc.stop(); } catch (e) { /* stopped */ } musicSrc = null; } }
let lastVib = 0;
function buzz(ms) { if (!navigator.vibrate) return; const n = performance.now(); if (n - lastVib < 60) return; lastVib = n; try { navigator.vibrate(ms); } catch (e) { /* blocked */ } }

// ======================================================================= levels
// Builders are bottom-anchored: helpers take the y of the surface they stand on and return their own top.
const F = 'face', S = 'split', B = 'bomb', T = 'foot';
const LEVELS = [
  // ---- world 1: day
  { name: 'ברוכים הבאים', birds: [F, F, F], build({ g, frame, germ }) {
    const t = frame(1450, g, 140, 120); germ(1450, g); germ(1450, t);
  } },
  { name: 'בית זכוכית', birds: [F, S, F], build({ g, b, frame, germ }) {
    const t1 = frame(1380, g, 120, 110, 'glass', 'wood'); germ(1380, g); b(1380, t1, 50, 50, 'glass');
    const t2 = frame(1600, g, 120, 110, 'glass', 'wood'); germ(1600, g); germ(1600, t2);
  } },
  { name: 'פיצול', birds: [S, S, F], build({ g, b, germ }) {
    for (const x of [1350, 1560, 1770]) { let t = b(x, g, 50, 50, 'wood'); t = b(x, t, 50, 50, 'glass'); germ(x, t, 24); }
  } },
  { name: 'בום!', birds: [F, F, B], build({ g, b, germ, tnt }) {
    b(1390, g, 22, 110, 'wood'); b(1610, g, 22, 110, 'wood');
    const roof = b(1500, g - 110, 260, 20, 'wood');
    tnt(1500, g); germ(1435, g, 24); germ(1565, g, 24); germ(1500, roof);
  } },
  { name: 'המגדל', birds: [F, T, F], build({ g, b, germ }) {
    b(1440, g, 22, 120, 'wood'); b(1560, g, 22, 120, 'wood');
    let t = b(1500, g - 120, 170, 20, 'wood');
    b(1455, t, 22, 100, 'wood'); b(1545, t, 22, 100, 'wood');
    germ(1500, t, 24);
    t = b(1500, t - 100, 140, 20, 'wood');
    b(1465, t, 20, 80, 'glass'); b(1535, t, 20, 80, 'glass');
    t = b(1500, t - 80, 120, 20, 'wood');
    germ(1500, g); germ(1500, t);
  } },
  { name: 'הבונקר', birds: [T, B, T], build({ g, b, germ }) {
    b(1400, g, 30, 100, 'stone'); b(1600, g, 30, 100, 'stone');
    const roof = b(1500, g - 100, 250, 20, 'glass');
    b(1430, roof, 40, 40, 'wood'); b(1570, roof, 40, 40, 'wood');
    germ(1500, g, 28); germ(1500, roof, 24);
    b(1730, g, 20, 140, 'wood'); b(1810, g, 20, 140, 'wood');
    germ(1770, b(1770, g - 140, 110, 20, 'wood'), 24);
  } },
  { name: 'על הגבעה', birds: [F, S, T, F], build({ g, b, germ, step }) {
    const top = step(1600, g - 150, 440);
    for (let row = 0; row < 3; row++) {
      const n = 5 - row;
      for (let i = 0; i < n; i++) b(1600 - (n - 1) * 25.5 + i * 51, top - row * 50, 50, 50, row === 1 ? 'glass' : 'wood');
    }
    germ(1600, top - 150); germ(1415, top, 24); germ(1785, top, 24);
  } },
  { name: 'שרשרת', birds: [F, F, B], build({ g, b, frame, germ, tnt }) {
    for (const x of [1250, 1330, 1410, 1490]) b(x, g, 20, 130, 'wood');
    germ(1290, g, 22); germ(1450, g, 22);
    tnt(1570, g);
    const t = frame(1700, g, 120, 110); germ(1700, g); germ(1700, t);
  } },
  // ---- world 2: sunset
  { name: 'מגדלי קרח', birds: [T, S, T], build({ g, frame, germ }) {
    for (const x of [1400, 1660]) {
      let t = frame(x, g, 100, 100, 'glass', 'glass');
      germ(x, g, 24);
      t = frame(x, t, 76, 90, 'glass', 'wood');
      germ(x, t, 24);
    }
  } },
  { name: 'מבצר אבן', birds: [B, T, F], build({ g, frame, germ, tnt }) {
    const t = frame(1500, g, 230, 130, 'stone', 'stone', 30);
    germ(1450, g); tnt(1560, g);
    const t2 = frame(1500, t, 120, 90, 'wood', 'stone');
    germ(1500, t, 24); germ(1500, t2);
  } },
  { name: 'שתי גבעות', birds: [F, S, B, T], w: 2650, build({ frame, germ, tnt, step, g }) {
    const h1 = step(1400, g - 120, 300), h2 = step(2020, g - 180, 380);
    const t1 = frame(1400, h1, 120, 100); germ(1400, h1, 24); germ(1400, t1, 24);
    const t2 = frame(2040, h2, 140, 110, 'stone', 'glass'); germ(2040, h2); germ(2040, t2);
    tnt(1890, h2);
  } },
  { name: 'בלונים', birds: [F, S, F, S], build({ g, frame, germ, airGerm, balloon }) {
    const t = frame(1450, g, 120, 100); germ(1450, g, 24); germ(1450, t, 24);
    balloon(airGerm(1300, g - 330, 24), 90);
    balloon(airGerm(1640, g - 420, 26), 100);
    balloon(airGerm(1800, g - 260, 24), 80);
  } },
  { name: 'כדור הריסה', birds: [F, F, T], build({ g, b, frame, germ, wreck }) {
    let t = frame(1640, g, 130, 140); germ(1640, g);
    const mid = t;
    t = frame(1640, t, 100, 120); germ(1640, mid, 24); germ(1640, t, 24);
    b(1800, g, 40, 40, 'wood'); germ(1800, g - 40, 22);
    wreck({ px: 1430, py: g - 600, len: 320, ang: -55, hookX: 1110, hookY: g - 560 });
  } },
  { name: 'מדרגות', birds: [S, F, B, S], build({ frame, germ, step, g }) {
    [[1300, 80], [1470, 170], [1640, 260], [1810, 350]].forEach(([x, h]) => {
      const top = step(x, g - h, 160);
      const t = frame(x, top, 64, 64, 'wood', 'glass');
      germ(x, top, 22); if (h >= 350) germ(x, t, 22);
    });
  } },
  { name: 'הסלע', birds: [F, T, F], build({ g, frame, germ, step, ramp, boulder }) {
    const top = step(1240, g - 220, 220);
    boulder(1315, top, 40);
    ramp(1350, top, 1620, g);
    const t1 = frame(1720, g, 120, 120); germ(1720, g); germ(1720, t1, 24);
    const t2 = frame(1900, g, 100, 100, 'glass', 'wood'); germ(1900, g, 24); germ(1900, t2, 24);
  } },
  { name: 'גורד שחקים', birds: [T, S, F, B], build({ g, frame, germ }) {
    let t = frame(1500, g, 150, 100); germ(1500, g);
    t = frame(1500, t, 124, 90, 'glass', 'wood');
    const mid = t; t = frame(1500, t, 100, 90); germ(1500, mid, 24);
    t = frame(1500, t, 80, 80, 'glass', 'wood');
    germ(1500, t, 24);
  } },
  // ---- world 3: night
  { name: 'איים באוויר', birds: [F, S, B, F, S], w: 2450, build({ g, frame, germ, platform, b }) {
    const p1 = platform(1350, g - 300, 170); const t1 = frame(1350, p1, 90, 90); germ(1350, p1, 24); germ(1350, t1, 24);
    const p2 = platform(1660, g - 430, 190); b(1610, p2, 40, 40, 'wood'); b(1710, p2, 40, 40, 'wood'); germ(1660, p2);
    const p3 = platform(1940, g - 240, 150); const t3 = frame(1940, p3, 80, 80, 'glass', 'wood'); germ(1940, p3, 22); germ(1940, t3, 22);
    germ(1660, g, 24);
  } },
  { name: 'שדה מוקשים', birds: [F, F, F], build({ g, germ, tnt, b }) {
    for (const x of [1280, 1380, 1480, 1580, 1680, 1780]) tnt(x, g);
    for (const x of [1330, 1530, 1730]) germ(x, g, 22);
    b(1430, g - 46, 200, 18, 'wood'); b(1630, g - 46, 200, 18, 'wood');
    germ(1430, g - 64, 24); germ(1630, g - 64, 24);
  } },
  { name: 'הטירה', birds: [B, T, F, S], build({ g, b, frame, germ, tnt }) {
    const a = frame(1380, g, 100, 160, 'stone', 'stone', 28);
    frame(1820, g, 100, 160, 'stone', 'stone', 28);
    tnt(1380, g); germ(1820, g);
    const bridge = b(1600, a, 520, 20, 'wood');
    germ(1600, bridge); germ(1380, bridge, 24); germ(1820, bridge, 24);
  } },
  { name: 'מלך בבלון', birds: [S, F, B, T], build({ g, b, germ, airGerm, balloon, frame }) {
    const t = frame(1520, g, 160, 110, 'stone', 'glass', 26); germ(1520, g); germ(1520, t, 24);
    balloon(airGerm(1520, g - 470, 40, true), 110);
    balloon(airGerm(1330, g - 300, 22), 80);
    balloon(airGerm(1740, g - 330, 22), 80);
    b(1740, g, 30, 60, 'wood');
  } },
  { name: 'דומינו ענק', birds: [F, F, B], build({ g, b, germ, frame, tnt }) {
    [1220, 1310, 1400, 1490, 1580, 1670, 1760].forEach((x) => b(x, g, 20, 165, 'wood'));
    for (const x of [1265, 1445, 1625]) germ(x, g, 22);
    tnt(1850, g);
    const t = frame(1980, g, 110, 100); germ(1980, g, 24); germ(1980, t, 24);
  } },
  { name: 'המבוך', birds: [B, B, T, F], build({ g, b, frame, germ, tnt }) {
    const outer = frame(1520, g, 320, 190, 'stone', 'stone', 30);
    const inner = frame(1520, g, 150, 100, 'glass', 'wood');
    germ(1520, g, 26); germ(1520, inner, 24);
    tnt(1410, g); tnt(1630, g);
    germ(1520, outer, 24);
    b(1460, outer, 30, 60, 'wood'); b(1580, outer, 30, 60, 'wood');
  } },
  { name: 'מלך הלילה', birds: [S, B, T, F], build({ g, b, germ, frame, step, tnt }) {
    const top = step(1640, g - 140, 360);
    const t = frame(1640, top, 180, 130, 'stone', 'stone', 30);
    germ(1640, top, 44, true);
    const t2 = frame(1640, t, 120, 90, 'glass', 'wood'); germ(1640, t, 24); germ(1640, t2, 24);
    tnt(1500, g); germ(1430, g, 24);
    b(1880, g, 22, 120, 'wood');
  } },
  { name: 'הקרב האחרון', birds: [F, S, B, T, F], w: 2600, build({ g, b, germ, frame, tnt, wreck, airGerm, balloon }) {
    b(1350, g, 30, 160, 'stone'); b(1650, g, 30, 160, 'stone');
    const beam = b(1500, g - 160, 340, 24, 'wood');
    b(1420, beam, 20, 100, 'glass'); b(1580, beam, 20, 100, 'glass');
    const roof = b(1500, beam - 100, 210, 24, 'stone');
    germ(1510, g, 44, true); tnt(1400, g);
    germ(1500, beam); germ(1500, roof);
    const t = frame(1880, g, 120, 120); germ(1880, g, 24); germ(1880, t, 24);
    balloon(airGerm(2050, g - 380, 24), 90);
    wreck({ px: 1240, py: g - 620, len: 300, ang: -50, hookX: 950, hookY: g - 560 });
  } },
];

// ======================================================================= canvas & camera
const canvas = document.getElementById('stage');
const ctx = canvas.getContext('2d');
let dpr = 1, cssW = 0, cssH = 0;
const cam = { x: 0, z: 0.6 }, camT = { x: 0, z: 0.6 };
let camMode = 'overview', camManualT = 0, focus = null, punch = 0;
let viewW = 1000, viewH = 700, camY = 0;
let skyCache = null, skyKey = '';

function resize() {
  cssW = window.innerWidth; cssH = window.innerHeight;
  dpr = Math.min(window.devicePixelRatio || 1, 2);
  canvas.width = Math.round(cssW * dpr); canvas.height = Math.round(cssH * dpr);
  skyKey = '';
  applyCam();
}
function zoomNow() { return cam.z * (1 + punch); }
function applyCam() {
  const z = zoomNow();
  viewW = cssW / z; viewH = cssH / z;
  camY = GROUND + 120 - viewH;
}
function levelW() { return (level && level.w) || 2300; }
function overviewZoom() {
  const right = level ? level.right : 1900, top = level ? level.top : GROUND - 600;
  const needH = Math.max(760, GROUND + 120 - (top - 140));
  return clamp(Math.min(cssW / (right + 160 - (SLING_X - 300)), cssH / needH), 0.16, 1.2);
}
function clampCamX(x, z) {
  const vw = cssW / z, lo = SLING_X - 330, hi = levelW() + 120 - vw;
  return hi < lo ? lo : clamp(x, lo, hi);
}
function theme() { return THEMES[Math.min(2, Math.floor(levelIdx / 8))]; }

// ======================================================================= game state
let engine = null;
let levelIdx = 0, level = null, loadId = 0;
let state = 'menu';        // menu | intro | aim | flying | settling | bonus | won | lost
let paused = false;
let score = 0, shownScore = -1, shotScore = 0, shotKills = 0;
let queue = [];
let flock = [];
let leader = null;
const pull = { x: -1, y: 0, p: 0 };
let aimPointer = null, aimAnchor = null, aimFinger = null, aimMaxed = false;
let panPointer = null, panLastX = 0;
let germs = [], blocks = [], frags = [], removeQ = [], boomQ = [], balloons = [], ropes = [];
let particles = [], popups = [], bubbles = [], trail = [], prevTrail = [];
let timer = 0, settleT = 0, grace = 0, shake = 0, hitStop = 0, slowmo = 0, introT = 0, bonusQ = 0, bonusT = 0, totalPoints = 0;
let idleT = 0, summaryDone = false, winT = 0, alpha = 1;
let clouds = [], starsBg = [];

function newEngine() {
  engine = Engine.create({ enableSleeping: true, positionIterations: 10, velocityIterations: 8 });
  engine.gravity.y = 1;
  Events.on(engine, 'collisionStart', onCollide);
  Events.on(engine, 'beforeUpdate', liftBalloons);
}

function loadLevel(i) {
  levelIdx = i; level = LEVELS[i]; loadId++;
  newEngine();
  germs = []; blocks = []; frags = []; removeQ = []; boomQ = []; balloons = []; ropes = [];
  particles = []; popups = []; bubbles = []; trail = []; prevTrail = [];
  flock = []; leader = null; score = 0; shotScore = 0; shotKills = 0;
  aimPointer = null; panPointer = null; pull.p = 0; slowmo = 0; hitStop = 0; focus = null; punch = 0;
  engine.timing.timeScale = 1;
  queue = level.birds.slice();
  level.statics = [];
  const groundOpts = { isStatic: true, friction: 0.9, label: 'ground', collisionFilter: { category: CAT_GROUND, mask: 0xffff } };
  Composite.add(engine.world, Bodies.rectangle(levelW() / 2, GROUND + 200, levelW() + 2400, 400, groundOpts));
  totalPoints = 0;
  const addStatic = (body, kind) => { Composite.add(engine.world, body); level.statics.push({ body, kind }); return body; };
  const b = (x, bottom, w, h, mat) => {
    const m = MATS[mat];
    const body = Bodies.rectangle(x, bottom - h / 2, w, h, { density: m.density, friction: m.friction, frictionStatic: 1, restitution: 0.05, chamfer: { radius: 2 }, label: 'block', sleepThreshold: 60 });
    body.game = { kind: 'block', mat, w, h, hp: m.hp, maxHp: m.hp };
    blocks.push(body); totalPoints += m.points;
    Composite.add(engine.world, body);
    return bottom - h;
  };
  const frame = (cx, bottom, span, height, mat = 'wood', roof = 'wood', postW = 22) => {
    b(cx - span / 2, bottom, postW, height, mat); b(cx + span / 2, bottom, postW, height, mat);
    return b(cx, bottom - height, span + postW * 2, 20, roof);
  };
  const tnt = (x, bottom) => b(x, bottom, 46, 46, 'tnt');
  const makeGerm = (x, y, r, king) => {
    const body = Bodies.circle(x, y, r, { density: 0.0015, friction: 0.6, restitution: 0.2, label: 'germ', sleepThreshold: 60 });
    const hp = king ? 320 : r >= 26 ? 45 : 35;
    body.game = { kind: 'germ', r, hp, maxHp: hp, king, blink: rf(1, 4), hurtT: 0, squash: 0, fear: 0, immuneT: 0 };
    germs.push(body); totalPoints += king ? 15000 : 5000;
    Composite.add(engine.world, body);
    return body;
  };
  const germ = (x, bottom, r = 26, king = false) => makeGerm(x, bottom - r, r, king);
  const airGerm = (x, y, r = 24, king = false) => makeGerm(x, y, r, king);
  const balloon = (target, len) => {
    const r = 26 + (target.game.king ? 10 : 0);
    const by = target.position.y - target.game.r - len - r;
    const body = Bodies.circle(target.position.x, by, r, { density: 0.0004, frictionAir: 0.03, restitution: 0.5, label: 'balloon', sleepThreshold: Infinity });
    const c = Constraint.create({ bodyA: body, pointA: { x: 0, y: r }, bodyB: target, pointB: { x: 0, y: -target.game.r }, length: len, stiffness: 0.7, damping: 0.1 });
    Composite.add(engine.world, [body, c]);
    target.sleepThreshold = Infinity; Sleeping.set(target, false);
    const bl = { body, c, target, home: { x: body.position.x, y: by }, r, color: pick(['#ff4d6d', '#ffb020', '#2fb7ff', '#a05bff']), alive: true, phase: rf(0, 6) };
    body.balloon = bl;
    balloons.push(bl);
  };
  const platform = (x, top, w, h = 34) => { addStatic(Bodies.rectangle(x, top + h / 2, w, h, { ...groundOpts, label: 'platform' }), 'platform'); return top; };
  const step = (x, top, w) => { addStatic(Bodies.rectangle(x, (top + GROUND) / 2, w, GROUND - top, { ...groundOpts, label: 'hill' }), 'hill'); return top; };
  const ramp = (x1, y1, x2, y2, th = 26) => {
    const len = Math.hypot(x2 - x1, y2 - y1), ang = Math.atan2(y2 - y1, x2 - x1);
    const nx = -Math.sin(ang), ny = Math.cos(ang);
    addStatic(Bodies.rectangle((x1 + x2) / 2 + nx * th / 2, (y1 + y2) / 2 + ny * th / 2, len, th, { ...groundOpts, angle: ang, label: 'ramp', friction: 0.4 }), 'ramp');
  };
  const boulder = (x, bottom, r) => {
    const body = Bodies.circle(x, bottom - r, r, { density: 0.006, friction: 0.5, frictionStatic: 0.8, restitution: 0.05, label: 'block', sleepThreshold: 60 });
    body.game = { kind: 'block', mat: 'stone', round: true, w: r * 2, h: r * 2, hp: 2600, maxHp: 2600 };
    blocks.push(body); totalPoints += 1000;
    Composite.add(engine.world, body);
  };
  const wreck = ({ px, py, len, ang, hookX, hookY, r = 46 }) => {
    const a = ang * Math.PI / 180;
    const bx = px + Math.sin(a) * len, by = py + Math.cos(a) * len;
    const ball = Bodies.circle(bx, by, r, { density: MATS.iron.density, friction: 0.4, restitution: 0.1, frictionAir: 0.002, label: 'block', sleepThreshold: Infinity });
    ball.game = { kind: 'block', mat: 'iron', round: true, w: r * 2, h: r * 2, hp: 1e9, maxHp: 1e9 };
    blocks.push(ball);
    const pivot = Constraint.create({ pointA: { x: px, y: py }, bodyB: ball, length: len, stiffness: 1 });
    const hold = Constraint.create({ pointA: { x: hookX, y: hookY }, bodyB: ball, length: Math.hypot(bx - hookX, by - hookY), stiffness: 1 });
    Composite.add(engine.world, [ball, pivot, hold]);
    addStatic(Bodies.rectangle(px, py - 14, 90, 28, { ...groundOpts, label: 'platform' }), 'beam');
    addStatic(Bodies.rectangle(hookX, hookY - 14, 60, 28, { ...groundOpts, label: 'platform' }), 'beam');
    ropes.push({ c: pivot, a: { x: px, y: py }, ball, breakable: false, alive: true });
    ropes.push({ c: hold, a: { x: hookX, y: hookY }, ball, breakable: true, alive: true });
  };
  level.build({ g: GROUND, b, frame, germ, airGerm, balloon, tnt, platform, step, ramp, boulder, wreck });
  const dyn = engine.world.bodies.filter((x) => !x.isStatic);
  level.right = Math.max(...dyn.map((x) => x.bounds.max.x), ...level.statics.map((s) => s.body.bounds.max.x)) + 40;
  level.top = Math.min(...dyn.map((x) => x.bounds.min.y), ...level.statics.map((s) => s.body.bounds.min.y), ...ropes.map((r) => r.a.y));
  for (const body of dyn) if (body.sleepThreshold !== Infinity) Sleeping.set(body, true);
  grace = 1.2;
  clouds = Array.from({ length: 10 }, (_, k) => ({ x: rf(-200, levelW() + 400), y: rf(80, 520), s: rf(0.6, 1.4), f: rf(0.2, 0.55), k }));
  starsBg = Array.from({ length: 90 }, () => ({ x: rf(0, 1), y: rf(0, 0.7), r: rf(0.6, 1.8), tw: rf(0, 6) }));
  cam.z = Math.min(1.1, overviewZoom() * 1.6); applyCam();
  cam.x = clampCamX((level.right + 1250) / 2 - viewW / 2, cam.z);
  state = 'intro'; introT = 0; camMode = 'fortress'; idleT = rf(3, 6);
  skyKey = '';
  document.getElementById('levelName').textContent = `שלב ${i + 1} · ${level.name}`;
  showOnly('hud');
}

// ======================================================================= physics events
function liftBalloons() {
  for (const bl of balloons) {
    if (!bl.alive) continue;
    const b = bl.body, carried = bl.target && !bl.target.game.dead ? bl.target.mass : 0;
    const m = b.mass + carried;
    const sway = Math.sin(timer * 1.3 + bl.phase) * 18;
    const fx = (bl.home.x + sway - b.position.x) * 0.0000035 * m;
    const fy = -m * 0.001 + (bl.home.y - b.position.y) * 0.0000035 * m;
    Body.applyForce(b, b.position, { x: fx, y: fy });
  }
}
function onCollide(ev) {
  for (const pair of ev.pairs) {
    const a = pair.bodyA, b = pair.bodyB, n = pair.collision.normal;
    const va = V(a), vb = V(b);
    const v = Math.abs((va.x - vb.x) * n.x + (va.y - vb.y) * n.y);
    if (a.balloon || b.balloon) {
      const bl = a.balloon || b.balloon, other = a.balloon ? b : a;
      if (other !== bl.target && (v > 1.2 || other.bird)) popBalloon(bl);
      continue;
    }
    if (a.bird) birdHit(a.bird, v, b);
    if (b.bird) birdHit(b.bird, v, a);
    if (v < 1.8 || grace > 0) continue;
    const ma = a.isStatic ? Infinity : a.mass, mb = b.isStatic ? Infinity : b.mass;
    const mu = !isFinite(ma) ? mb : !isFinite(mb) ? ma : (ma * mb) / (ma + mb);
    const energy = 0.5 * mu * v * v * (a.bird || b.bird ? 1.6 : 1);
    if (a.game) hurt(a, energy, v);
    if (b.game) hurt(b, energy, v);
    if (energy > 2600 && (a.bird || b.bird)) { hitStop = Math.max(hitStop, 0.05); shake = Math.max(shake, 0.45); }
  }
}
function popBalloon(bl) {
  if (!bl.alive) return;
  bl.alive = false;
  Composite.remove(engine.world, [bl.body, bl.c]);
  sfx('balloon', 0.7, rf(0.9, 1.2));
  for (let i = 0; i < 10; i++) particles.push({ t: 'chip', x: bl.body.position.x, y: bl.body.position.y, vx: rf(-6, 6), vy: rf(-6, 2), r: rf(4, 8), rot: rf(0, 6), vr: rf(-0.4, 0.4), life: rf(0.4, 0.8), max: 0.8, c: bl.color });
  popups.push({ x: bl.body.position.x, y: bl.body.position.y - 30, text: 'POP!', c: bl.color, life: 0.7, big: true, word: true });
  if (bl.target && !bl.target.game.dead) { Sleeping.set(bl.target, false); say(bl.target, pick(LINES.scared), 1.2); }
}
function hurt(body, energy, v) {
  const g = body.game;
  if (g.dead || energy < 6) return;
  if (g.immuneT > 0 && energy < 250) return;
  g.hp -= energy;
  if (g.kind === 'germ') {
    if (g.hp <= 0) { killGerm(body); return; }
    if (g.hurtT <= 0) sfx('ouch', 0.35, rf(0.9, 1.2));
    g.hurtT = 0.6; g.squash = 1;
  } else {
    if (v > 3.5) {
      const m = g.mat;
      sfx(m === 'glass' ? 'glass' : m === 'stone' || m === 'iron' ? 'stone' : 'wood', clamp(v / 16, 0.15, 0.6), rf(0.9, 1.15));
      if (m !== 'iron' && (m !== 'stone' || v > 8)) chips(body.position.x, body.position.y, m, 3);
    }
    if (g.hp <= 0) breakBlock(body);
  }
}
function birdHit(bd, v, other) {
  if (!bd.hit) {
    bd.hit = true;
    if (bd.kind === 'bomb' && !bd.used) bd.fuse = 1.3;
    if (bd.stomping) { bd.stomping = false; boomQ.push({ x: bd.body.position.x, y: bd.body.position.y + 30, R: 250, power: 0.8, kind: 'stomp' }); }
  }
  if (v > 4) {
    bd.squash = Math.min(1, v / 14);
    if (bd.kind !== 'foot' && v > 6 && bd.boingT <= 0) { sfx('boing', 0.45, rf(0.9, 1.25)); bd.boingT = 0.4; }
  }
  if (v > 5 && (other.label === 'ground' || other.label === 'hill' || other.label === 'platform')) puff(bd.body.position.x, bd.body.position.y + 20, 4, '#d9c39a');
}
function killGerm(body) {
  const g = body.game;
  if (g.dead) return;
  g.dead = true;
  removeQ.push(body);
  shotKills++;
  addScore(g.king ? 15000 : 5000, body.position.x, body.position.y - g.r - 12, '#3a9c1f', true);
  sfx('pop', 0.85, g.king ? 0.7 : rf(0.9, 1.15));
  const id = loadId;
  setTimeout(() => { if (id === loadId) sfx('ghost', 0.35, rf(0.9, 1.2)); }, 250);
  buzz(30);
  for (let i = 0; i < 22; i++) particles.push({ t: 'drop', x: body.position.x, y: body.position.y, vx: rf(-9, 9), vy: rf(-12, -2), r: rf(4, 10) * (g.r / 26), life: rf(0.5, 1.1), max: 1.1, c: i % 3 ? '#74d64b' : '#c8f7a8' });
  particles.push({ t: 'ring', x: body.position.x, y: body.position.y, r: g.r, R: g.r * 3, life: 0.35, max: 0.35, c: '#c8f7a8' });
  particles.push({ t: 'ghost', x: body.position.x, y: body.position.y, r: g.r, vy: -1.4, life: 1.8, max: 1.8, ph: rf(0, 6), king: g.king });
  puff(body.position.x, body.position.y, 7, '#e8ffd9');
  shake = Math.max(shake, g.king ? 0.7 : 0.3);
  for (const bl of balloons) if (bl.alive && bl.target === body) { Composite.remove(engine.world, bl.c); bl.target = null; bl.home.y -= 2000; }
  if (shotKills >= 2) {
    const words = { 2: 'דאבל!', 3: 'טריפל!' };
    popups.push({ x: body.position.x, y: body.position.y - 110, text: words[shotKills] || 'מגה הרס!', c: '#ffc828', life: 1.2, big: true, word: true });
    sfx('combo', 0.5, 0.9 + shotKills * 0.1);
  }
  const alive = germs.filter((x) => !x.game.dead).length;
  if (alive === 0 && !REDUCED) { slowmo = 1.1; focus = { x: body.position.x, y: body.position.y }; }
}
function breakBlock(body) {
  const g = body.game;
  if (g.dead) return;
  g.dead = true;
  removeQ.push(body);
  addScore(MATS[g.mat].points || 1000, body.position.x, body.position.y, '#7a4a1e');
  if (g.mat === 'tnt') { boomQ.push({ x: body.position.x, y: body.position.y, R: 270, power: 1, kind: 'tnt' }); return; }
  sfx(g.mat === 'glass' ? 'glass' : g.mat === 'stone' ? 'stone' : 'woodBreak', 0.65, rf(0.85, 1.1));
  chips(body.position.x, body.position.y, g.mat, Math.round(6 + (g.w * g.h) / 500));
  puff(body.position.x, body.position.y, 3, '#efe3cc');
  if (g.mat !== 'glass' && !g.round) shatter(body);
}
function shatter(body) {
  const g = body.game, along = g.w >= g.h;
  const n = Math.max(2, Math.min(3, Math.round(Math.max(g.w, g.h) / 45)));
  const pw = along ? g.w / n : g.w, ph = along ? g.h : g.h / n;
  const ca = Math.cos(body.angle), sa = Math.sin(body.angle), v = V(body);
  for (let i = 0; i < n; i++) {
    if (frags.length > 70) break;
    const off = (i - (n - 1) / 2) * (along ? pw : ph);
    const lx = along ? off : 0, ly = along ? 0 : off;
    const x = body.position.x + lx * ca - ly * sa, y = body.position.y + lx * sa + ly * ca;
    const f = Bodies.rectangle(x, y, pw * 0.92, ph * 0.92, { density: 0.002, friction: 0.6, restitution: 0.25, angle: body.angle, collisionFilter: { category: CAT_FRAG, mask: CAT_GROUND } });
    Composite.add(engine.world, f);
    Body.setVelocity(f, { x: v.x + rf(-3, 3) + lx * 0.04, y: v.y + rf(-5, -1) });
    Body.setAngularVelocity(f, rf(-0.25, 0.25));
    frags.push({ body: f, w: pw * 0.92, h: ph * 0.92, mat: g.mat, life: 2.4 });
  }
}
function addScore(n, x, y, c, big = false) { score += n; shotScore += n; popups.push({ x, y, text: n.toLocaleString('en-US'), c, life: 1.2, big }); }
function segDist(px, py, ax, ay, bx, by) {
  const dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
  const t = l2 ? clamp(((px - ax) * dx + (py - ay) * dy) / l2, 0, 1) : 0;
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}
function cutRope(r) {
  if (!r.alive) return;
  r.alive = false;
  Composite.remove(engine.world, r.c);
  Sleeping.set(r.ball, false);
  sfx('twang', 0.7);
  popups.push({ x: (r.a.x + r.ball.position.x) / 2, y: (r.a.y + r.ball.position.y) / 2 - 40, text: 'SNAP!', c: '#ffc828', life: 0.9, big: true, word: true });
  for (let i = 0; i < 8; i++) particles.push({ t: 'chip', x: r.a.x, y: r.a.y, vx: rf(-4, 4), vy: rf(-4, 2), r: rf(3, 6), rot: rf(0, 6), vr: rf(-0.4, 0.4), life: 0.7, max: 0.7, c: '#8a5a2b' });
}

function explode(e) {
  const { x, y, R, power, kind } = e;
  const isStomp = kind === 'stomp';
  sfx(isStomp ? 'stomp' : 'boom', 0.95, rf(0.9, 1.05));
  buzz(isStomp ? 120 : 160);
  shake = 1; hitStop = Math.max(hitStop, isStomp ? 0.06 : 0.09);
  if (!REDUCED) punch = Math.max(punch, isStomp ? 0.05 : 0.08);
  if (isStomp) popups.push({ x, y: y - 80, text: 'STOMP!', c: '#ff7a1a', life: 1.1, big: true, word: true });
  particles.push({ t: 'ring', x, y, r: 10, R, life: 0.45, max: 0.45, c: isStomp ? '#fff6df' : '#ffd23f' });
  if (!isStomp) {
    particles.push({ t: 'flash', x, y, r: R * 0.55, life: 0.18, max: 0.18 });
    for (let i = 0; i < 26; i++) { const a = rf(0, Math.PI * 2), s = rf(4, 14); particles.push({ t: 'drop', x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s - 3, r: rf(5, 12), life: rf(0.3, 0.7), max: 0.7, c: i % 2 ? '#ffb020' : '#ff5a1f' }); }
    puff(x, y, 14, '#5b5048');
  } else puff(x, y, 14, '#e8dcc4');
  for (const body of [...blocks, ...germs]) {
    if (body.game.dead) continue;
    const dx = body.position.x - x, dy = body.position.y - y, d = Math.hypot(dx, dy);
    if (d > R) continue;
    const f = 1 - d / R, v = V(body);
    Sleeping.set(body, false);
    const k = body.game.mat === 'iron' ? 0.2 : 1;
    Body.setVelocity(body, { x: v.x + (dx / (d || 1)) * 13 * f * power * k, y: v.y + ((dy / (d || 1)) * 6 - 7) * f * power * k });
    Body.setAngularVelocity(body, Body.getAngularVelocity(body) + rf(-0.2, 0.2) * f);
    hurt(body, (body.game.kind === 'germ' ? 140 : 520) * f * power, 10);
  }
  for (const bl of balloons) if (bl.alive && Math.hypot(bl.body.position.x - x, bl.body.position.y - y) < R) popBalloon(bl);
  for (const r of ropes) if (r.breakable && r.alive && segDist(x, y, r.a.x, r.a.y, r.ball.position.x, r.ball.position.y) < R * 0.6) cutRope(r);
  for (const bd of flock) {
    if (bd.kind === 'bomb') continue;
    const dx = bd.body.position.x - x, dy = bd.body.position.y - y, d = Math.hypot(dx, dy);
    if (d < R && !isStomp) { const v = V(bd.body); Body.setVelocity(bd.body, { x: v.x + (dx / (d || 1)) * 8 * (1 - d / R), y: v.y - 5 * (1 - d / R) }); }
  }
}

// ======================================================================= characters
function makeBirdBody(kind, x, y) {
  const k = KINDS[kind];
  const opts = { density: k.density, friction: 0.6, restitution: k.rest, frictionAir: 0.004, label: 'bird' };
  return k.shape === 'circle' ? Bodies.circle(x, y, k.r, opts) : Bodies.rectangle(x, y, k.w, k.h, { ...opts, chamfer: { radius: 12 } });
}
function spawnBird(kind, x, y, vx, vy) {
  const body = makeBirdBody(kind, x, y);
  Composite.add(engine.world, body);
  Body.setVelocity(body, { x: vx, y: vy });
  Body.setAngularVelocity(body, kind === 'foot' ? 0.08 : 0.05);
  const bd = { kind, body, hit: false, used: false, stomping: false, t: 0, still: 0, fuse: 0, squash: 0, boingT: 0, afterHit: 0 };
  body.bird = bd;
  flock.push(bd);
  return bd;
}
function pouch() { const len = pull.p * PULL_LEN; return { x: SLING_X + pull.x * len, y: SLING_Y + pull.y * len }; }
function launch() {
  const kind = queue.shift();
  const p = pouch(), sp = pull.p * VMAX;
  leader = spawnBird(kind, p.x, p.y, -pull.x * sp, -pull.y * sp);
  prevTrail = trail; trail = [];
  shotScore = 0; shotKills = 0; summaryDone = false; winT = 0;
  sfx('launch', 0.7);
  sfx(kind === 'foot' ? 'stretch' : 'wee', 0.5, kind === 'bomb' ? 0.8 : kind === 'split' ? 1.2 : 1);
  say(leader.body, pick(LINES[kind]), 1.3);
  state = 'flying'; camMode = 'follow'; camManualT = 0;
  pull.p = 0;
}
function useAbility() {
  const bd = leader;
  if (!bd || bd.used || (bd.hit && bd.kind !== 'bomb') || !flock.includes(bd)) return false;
  bd.used = true;
  const b = bd.body, p = b.position, v = V(b);
  if (bd.kind === 'face') {
    const sp = Math.max(Math.hypot(v.x, v.y) * 2, 28), a = Math.atan2(v.y, v.x);
    Body.setVelocity(b, { x: Math.cos(a) * sp, y: Math.sin(a) * sp });
    sfx('bleh', 0.9);
    popups.push({ x: p.x, y: p.y - 70, text: 'BLEH!', c: '#ff5f8f', life: 0.9, big: true, word: true });
    for (let i = 0; i < 12; i++) particles.push({ t: 'drop', x: p.x, y: p.y + 24, vx: -Math.cos(a) * rf(2, 7) + rf(-2, 2), vy: -Math.sin(a) * rf(2, 7) + rf(-2, 2), r: rf(3, 7), life: rf(0.4, 0.8), max: 0.8, c: '#ffb3c7' });
    particles.push({ t: 'ring', x: p.x, y: p.y, r: 20, R: 90, life: 0.3, max: 0.3, c: '#ff9ab8' });
    if (!REDUCED) punch = Math.max(punch, 0.04);
  } else if (bd.kind === 'split') {
    const sp = Math.hypot(v.x, v.y), a = Math.atan2(v.y, v.x);
    Composite.remove(engine.world, b);
    flock = flock.filter((x) => x !== bd);
    const kids = [-0.22, 0, 0.22].map((da) => spawnBird('mini', p.x, p.y, Math.cos(a + da) * sp * 1.05, Math.sin(a + da) * sp * 1.05));
    kids.forEach((k) => { k.used = true; });
    leader = kids[1];
    sfx('wee', 0.7, 1.5);
    puff(p.x, p.y, 6, '#ffffff');
    popups.push({ x: p.x, y: p.y - 60, text: 'x3', c: '#2f9bff', life: 0.8, big: true, word: true });
  } else if (bd.kind === 'bomb') {
    detonate(bd);
  } else {
    Body.setAngle(b, 0); Body.setAngularVelocity(b, 0);
    Body.setVelocity(b, { x: v.x * 0.15, y: 36 });
    Body.setDensity(b, 0.016);
    bd.stomping = true;
    sfx('launch', 0.6, 0.6);
    popups.push({ x: p.x, y: p.y - 70, text: '!!!', c: '#ff7a1a', life: 0.6, big: true, word: true });
  }
  buzz(40);
  return true;
}
function detonate(bd) {
  const p = bd.body.position;
  boomQ.push({ x: p.x, y: p.y, R: 300, power: 1.15, kind: 'bomb' });
  popups.push({ x: p.x, y: p.y - 90, text: 'BOOM!', c: '#ff3b3b', life: 1.1, big: true, word: true });
  Composite.remove(engine.world, bd.body);
  flock = flock.filter((x) => x !== bd);
}
function say(body, text, life = 1.6) {
  bubbles = bubbles.filter((b) => b.body !== body);
  bubbles.push({ body, text, life, max: life });
}

// ======================================================================= update (one 1/60 s tick)
function update(dt) {
  if (grace > 0) grace -= dt;
  timer += dt;
  shake = Math.max(0, shake - dt * 2.2);
  punch = Math.max(0, punch - dt * 0.5);

  if (boomQ.length) { const q = boomQ; boomQ = []; for (const e of q) explode(e); }
  if (removeQ.length) {
    for (const body of removeQ) Composite.remove(engine.world, body);
    for (const body of engine.world.bodies) if (!body.isStatic) Sleeping.set(body, false);
    blocks = blocks.filter((b) => !b.game.dead);
    germs = germs.filter((g) => !g.game.dead);
    removeQ = [];
  }
  for (const g of germs) if (g.position.y > GROUND + 400 || g.position.x > levelW() + 500 || g.position.x < -500) killGerm(g);
  for (const b of blocks) if (!b.game.dead && (b.position.y > GROUND + 400 || b.position.x > levelW() + 500)) { b.game.dead = true; removeQ.push(b); }
  const threat = leader && flock.includes(leader) ? leader.body.position : null;
  for (const g of germs) {
    const gg = g.game;
    gg.hurtT = Math.max(0, gg.hurtT - dt); gg.squash = Math.max(0, gg.squash - dt * 3); gg.immuneT = Math.max(0, gg.immuneT - dt);
    gg.blink -= dt; if (gg.blink < -0.12) gg.blink = rf(1.5, 4);
    const near = threat && Math.hypot(threat.x - g.position.x, threat.y - g.position.y) < 360;
    if (near && gg.fear <= 0.05 && rnd() < 0.5) { say(g, pick(LINES.scared), 1); sfx('scream', 0.25, rf(0.9, 1.3)); }
    gg.fear = clamp(gg.fear + (near ? dt * 6 : -dt * 2), 0, 1);
  }
  for (const bl of balloons) if (bl.alive && !bl.target && bl.body.position.y < camY - 600) { bl.alive = false; Composite.remove(engine.world, bl.body); }
  for (const r of ropes) {
    if (!r.breakable || !r.alive) continue;
    for (const bd of flock) {
      const rr = KINDS[bd.kind].shape === 'circle' ? KINDS[bd.kind].r : 30;
      if (segDist(bd.body.position.x, bd.body.position.y, r.a.x, r.a.y, r.ball.position.x, r.ball.position.y) < rr + 6) { cutRope(r); break; }
    }
  }
  for (let i = frags.length - 1; i >= 0; i--) {
    const f = frags[i]; f.life -= dt;
    if (f.life <= 0) { Composite.remove(engine.world, f.body); frags.splice(i, 1); }
  }
  for (let i = bubbles.length - 1; i >= 0; i--) { const b = bubbles[i]; b.life -= dt; if (b.life <= 0 || (b.body.game && b.body.game.dead) || (b.body.bird && !flock.includes(b.body.bird))) bubbles.splice(i, 1); }
  if (slowmo > 0) {
    slowmo -= dt;
    engine.timing.timeScale = slowmo > 0 ? 0.3 : 1;
    if (slowmo <= 0) focus = null;
  }

  switch (state) {
    case 'intro':
      introT += dt;
      if (introT > 1.4) camMode = 'overview';
      if (introT > 2.0) state = 'aim';
      break;
    case 'aim':
      idleT -= dt;
      if (idleT <= 0 && germs.length && !aimPointer) { say(pick(germs), pick(LINES.idle), 1.8); idleT = rf(5, 9); }
      break;
    case 'flying': {
      for (let i = flock.length - 1; i >= 0; i--) {
        const bd = flock[i];
        if (!bd) continue;
        const b = bd.body;
        bd.t += dt; bd.squash = Math.max(0, bd.squash - dt * 4); bd.boingT -= dt;
        if (bd.hit) bd.afterHit += dt;
        if (bd === leader && !bd.hit && Math.floor(bd.t * 30) % 2 === 0) trail.push({ x: b.position.x, y: b.position.y, big: trail.length % 4 === 0, c: KINDS[bd.kind].trail });
        if (bd.fuse > 0) { bd.fuse -= dt; if (bd.fuse <= 0 && !bd.used) { bd.used = true; detonate(bd); continue; } }
        // rolling resistance so faces stop instead of rolling forever
        if (bd.hit && b.position.y > GROUND - (KINDS[bd.kind].r || 44) - 6) {
          const v = V(b);
          Body.setVelocity(b, { x: v.x * 0.965, y: v.y });
          Body.setAngularVelocity(b, Body.getAngularVelocity(b) * 0.95);
        }
        if (SPD(b) < 0.45) bd.still += dt; else bd.still = 0;
        const out = b.position.y > GROUND + 300 || b.position.x > levelW() + 400 || b.position.x < -400;
        if ((bd.still > 0.6 && bd.fuse <= 0) || out || bd.t > 10 || bd.afterHit > 4.5) {
          if (!out) puff(b.position.x, b.position.y, 10, '#ffffff');
          Composite.remove(engine.world, b);
          flock = flock.filter((x) => x !== bd);
        }
      }
      if (!germs.length && flock.length) {
        winT += dt;
        if (winT > 1.0 && slowmo <= 0) { for (const bd of flock) { puff(bd.body.position.x, bd.body.position.y, 8, '#ffffff'); Composite.remove(engine.world, bd.body); } flock = []; }
      }
      if (!flock.length) { leader = null; state = 'settling'; settleT = 0; camMode = 'fortress'; }
      break;
    }
    case 'settling': {
      settleT += dt;
      const moving = engine.world.bodies.some((b) => !b.isStatic && !b.isSleeping && SPD(b) > 0.3 && b.collisionFilter.category !== CAT_FRAG && !b.balloon && !(b.game && b.game.mat === 'iron') && b.sleepThreshold !== Infinity);
      if (!germs.length && settleT > 0.6 && slowmo <= 0) { finishWin(); break; }
      if ((!moving && settleT > 0.6) || settleT > 4) {
        if (!summaryDone && shotScore >= 8000) popups.push({ x: (level.right + 1250) / 2, y: GROUND - 380, text: `הרס! +${shotScore.toLocaleString('en-US')}`, c: '#ff7a1a', life: 1.4, big: true, word: true });
        summaryDone = true;
        if (!germs.length) finishWin();
        else if (queue.length) {
          if (shotKills === 0) taunt();
          state = 'aim'; camMode = 'overview'; idleT = rf(5, 8);
        } else if (settleT > 1.4) finishLose();
      }
      break;
    }
    case 'bonus':
      bonusT -= dt;
      if (bonusT <= 0) {
        if (bonusQ > 0) {
          bonusQ--; queue.shift();
          addScore(10000, SLING_X - 70, GROUND - 140, '#ff7a1a', true);
          sfx('star', 0.5, 1.2); bonusT = 0.4;
        } else { state = 'won'; showResult(true); }
      }
      break;
  }

  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt;
    if (p.life <= 0) { particles.splice(i, 1); continue; }
    if (p.t === 'ring') { p.r += (p.R - p.r) * Math.min(1, dt * 10); continue; }
    if (p.t === 'flash') continue;
    if (p.t === 'ghost') { p.y += p.vy; p.x += Math.sin(timer * 4 + p.ph) * 0.8; continue; }
    if (p.t === 'puff') { p.r += dt * 40; p.x += p.vx; p.y += p.vy; continue; }
    p.vy += 0.5; p.x += p.vx; p.y += p.vy;
    if (p.rot !== undefined) p.rot += p.vr;
    if (p.y > GROUND - 2 && p.vy > 0) { p.y = GROUND - 2; p.vy *= -0.3; p.vx *= 0.6; }
  }
  for (let i = popups.length - 1; i >= 0; i--) { const p = popups[i]; p.life -= dt; p.y -= 40 * dt; if (p.life <= 0) popups.splice(i, 1); }
}
function taunt() {
  sfx('laugh', 0.6, rf(0.95, 1.1));
  for (const g of germs) { if (g.sleepThreshold === Infinity) continue; Sleeping.set(g, false); const v = V(g); Body.setVelocity(g, { x: v.x, y: -rf(3, 5) }); g.game.squash = 1; g.game.immuneT = 1.5; }
  say(pick(germs), pick(LINES.taunt), 1.8);
}
function updateCamera(dt) {
  let tz = overviewZoom(), tx;
  const ovX = SLING_X - 300;
  if (focus) { tz = Math.min(1.3, tz * 1.8); tx = focus.x - cssW / tz / 2; }
  else if (camMode === 'fortress') { tz = Math.min(1.1, tz * 1.25); tx = (level.right + 1250) / 2 - cssW / tz / 2; }
  else if (camMode === 'follow' && leader && flock.includes(leader)) {
    const b = leader.body.position;
    const needH = GROUND + 120 - (b.y - 160);
    tz = Math.min(tz, cssH / Math.max(needH, 760));
    tx = Math.max(ovX, b.x - cssW / tz * 0.5);
  } else tx = ovX;
  if (camManualT > 0) { camManualT -= dt; tx = cam.x; }
  camT.z = tz; camT.x = clampCamX(tx, tz);
  const k = 1 - Math.exp(-dt * (camMode === 'follow' ? 6 : 3));
  cam.z += (camT.z - cam.z) * k;
  applyCam();
  cam.x += (clampCamX(camT.x, cam.z) - cam.x) * k;
  cam.x = clampCamX(cam.x, cam.z);
}

function finishWin() {
  if (state === 'bonus' || state === 'won') return;
  state = 'bonus'; bonusQ = queue.length; bonusT = 0.8; camMode = 'overview';
  sfx('win', 0.7);
}
function finishLose() { state = 'lost'; sfx('lose', 0.6); showResult(false); }
function starCount() {
  const n = level.birds.length;
  const s3 = totalPoints * 0.55 + 10000 * Math.floor((n - 1) / 2);
  const s2 = totalPoints * 0.35;
  return score >= s3 ? 3 : score >= s2 ? 2 : 1;
}

// ======================================================================= render (interpolated between physics ticks)
function P(b) {
  if (b.px === undefined) return { x: b.position.x, y: b.position.y, a: b.angle };
  return { x: b.px + (b.position.x - b.px) * alpha, y: b.py + (b.position.y - b.py) * alpha, a: b.pa + (b.angle - b.pa) * alpha };
}
function render() {
  const z = zoomNow(), k = z * dpr;
  let sx = 0, sy = 0;
  if (shake > 0) { const m = shake * shake * (REDUCED ? 3 : 16); sx = rf(-m, m); sy = rf(-m, m); }
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  drawSky();
  ctx.setTransform(k, 0, 0, k, (-cam.x + sx) * k, (-camY + sy) * k);
  drawBackdrop();
  drawGround();
  if (!level) return;
  for (const s of level.statics) drawStatic(s);
  drawSlingBack();
  drawTrail(prevTrail, 0.3); drawTrail(trail, 0.9);
  for (const r of ropes) drawRope(r);
  for (const bl of balloons) if (bl.alive) drawBalloon(bl);
  for (const f of frags) drawBlockShape(f.body, f.w, f.h, f.mat, 1, Math.min(1, f.life / 0.6), false);
  for (const b of blocks) drawBlock(b);
  for (const g of germs) drawGerm(g);
  drawQueue();
  for (const bd of flock) { const p = P(bd.body); drawBird(bd.kind, p.x, p.y, p.a, bd); }
  drawSlingFront();
  drawAimGuide();
  drawParticles();
  drawBubbles();
  drawPopups();
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  drawAimTouch();
  if (levelIdx === 0 && state === 'aim' && !aimPointer && queue.length === level.birds.length) drawPullHint();
  if (state === 'flying' && leader && !leader.used && !(leader.hit && leader.kind !== 'bomb') && flock.includes(leader) && leader.t < 1.6) drawTapHint();
}

function drawSky() {
  const th = level ? theme() : THEMES[0];
  const key = th.name + canvas.height;
  if (key !== skyKey) {
    skyKey = key;
    skyCache = ctx.createLinearGradient(0, 0, 0, canvas.height);
    skyCache.addColorStop(0, th.sky[0]); skyCache.addColorStop(0.65, th.sky[1]); skyCache.addColorStop(1, th.sky[2]);
  }
  ctx.fillStyle = skyCache; ctx.fillRect(0, 0, canvas.width, canvas.height);
  if (th.stars) {
    ctx.fillStyle = '#fff';
    for (const s of starsBg) {
      ctx.globalAlpha = 0.5 + 0.5 * Math.sin(timer * 2 + s.tw);
      ctx.fillRect(s.x * canvas.width, s.y * canvas.height, s.r * dpr, s.r * dpr);
    }
    ctx.globalAlpha = 1;
  }
}
function drawBackdrop() {
  const th = level ? theme() : THEMES[0];
  const sunX = cam.x * 0.85 + viewW * 0.78, sunY = camY + 160;
  ctx.fillStyle = th.glow; ctx.beginPath(); ctx.arc(sunX, sunY, 110, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = th.sun; ctx.beginPath(); ctx.arc(sunX, sunY, 70, 0, Math.PI * 2); ctx.fill();
  if (th.stars) { ctx.fillStyle = 'rgba(200,205,180,0.5)'; ctx.beginPath(); ctx.arc(sunX - 20, sunY - 10, 14, 0, Math.PI * 2); ctx.arc(sunX + 22, sunY + 18, 10, 0, Math.PI * 2); ctx.fill(); }
  ctx.fillStyle = th.cloud;
  for (const c of clouds) {
    const x = c.x + cam.x * c.f + timer * 6 * c.s, y = Math.max(c.y, camY + 60 + c.k * 40), s = c.s;
    ctx.beginPath();
    ctx.arc(x, y, 34 * s, 0, Math.PI * 2); ctx.arc(x + 40 * s, y - 16 * s, 44 * s, 0, Math.PI * 2);
    ctx.arc(x + 88 * s, y, 32 * s, 0, Math.PI * 2); ctx.rect(x, y - 4 * s, 88 * s, 34 * s);
    ctx.fill();
  }
  hills(0.55, GROUND - 40, 170, th.far, 520);
  hills(0.3, GROUND - 10, 110, th.near, 360);
}
function hills(f, baseY, amp, color, period) {
  const off = cam.x * f, x0 = cam.x - 50, x1 = cam.x + viewW + 50;
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.moveTo(x0, GROUND + 50);
  for (let x = x0; x <= x1; x += 25) {
    const u = (x - off) / period;
    ctx.lineTo(x, baseY - amp * (0.55 + 0.45 * Math.sin(u * Math.PI * 2) * Math.sin(u * 2.3)));
  }
  ctx.lineTo(x1, GROUND + 50);
  ctx.fill();
}
function drawGround() {
  const th = level ? theme() : THEMES[0];
  const x0 = cam.x - 100, w = viewW + 200;
  ctx.fillStyle = th.dirt; ctx.fillRect(x0, GROUND, w, 600);
  ctx.fillStyle = th.dirt2;
  for (let x = Math.floor(x0 / 60) * 60; x < x0 + w; x += 60) { ctx.beginPath(); ctx.arc(x + 20, GROUND + 60 + (x % 120 ? 30 : 0), 8, 0, Math.PI * 2); ctx.fill(); }
  ctx.fillStyle = th.grass; ctx.fillRect(x0, GROUND - 4, w, 22);
  ctx.fillStyle = th.grass2; ctx.fillRect(x0, GROUND + 14, w, 6);
}
function drawStatic(s) {
  const th = theme(), b = s.body;
  const x = b.bounds.min.x, y = b.bounds.min.y, w = b.bounds.max.x - x;
  if (s.kind === 'hill') {
    ctx.fillStyle = th.dirt;
    ctx.beginPath(); ctx.moveTo(x - 30, GROUND); ctx.lineTo(x, y + 20); ctx.quadraticCurveTo(x, y, x + 20, y); ctx.lineTo(x + w - 20, y); ctx.quadraticCurveTo(x + w, y, x + w, y + 20); ctx.lineTo(x + w + 30, GROUND); ctx.fill();
    ctx.fillStyle = th.grass; ctx.fillRect(x + 6, y - 4, w - 12, 18);
  } else if (s.kind === 'platform') {
    ctx.fillStyle = th.dirt;
    ctx.beginPath(); ctx.moveTo(x, y + 10); ctx.lineTo(x + w, y + 10); ctx.lineTo(x + w * 0.75, y + 70); ctx.lineTo(x + w * 0.5, y + 95); ctx.lineTo(x + w * 0.25, y + 66); ctx.closePath(); ctx.fill();
    ctx.fillStyle = th.grass; roundRect(x, y - 4, w, 20, 8); ctx.fill();
  } else if (s.kind === 'beam') {
    ctx.fillStyle = '#6b3d16'; roundRect(x, y, w, b.bounds.max.y - y, 6); ctx.fill();
    ctx.fillStyle = '#8b5424'; roundRect(x + 4, y + 4, w - 8, 8, 4); ctx.fill();
  } else if (s.kind === 'ramp') {
    const v = b.vertices;
    ctx.fillStyle = th.dirt; ctx.beginPath(); ctx.moveTo(v[0].x, v[0].y); for (const p of v) ctx.lineTo(p.x, p.y); ctx.closePath(); ctx.fill();
    ctx.strokeStyle = th.grass; ctx.lineWidth = 10; ctx.beginPath(); ctx.moveTo(v[0].x, v[0].y); ctx.lineTo(v[1].x, v[1].y); ctx.stroke();
  }
}
function drawRope(r) {
  if (!r.alive) return;
  const p = P(r.ball);
  ctx.strokeStyle = r.breakable ? '#c08a4a' : '#5a3a1a'; ctx.lineWidth = r.breakable ? 5 : 6; ctx.lineCap = 'round';
  ctx.setLineDash(r.breakable ? [10, 6] : []);
  ctx.beginPath(); ctx.moveTo(r.a.x, r.a.y); ctx.lineTo(p.x, p.y); ctx.stroke();
  ctx.setLineDash([]);
  if (r.breakable && state === 'aim') {
    const mx = (r.a.x + p.x) / 2, my = (r.a.y + p.y) / 2;
    ctx.globalAlpha = 0.6 + 0.4 * Math.sin(timer * 5);
    ctx.font = '400 34px "Secular One", Rubik, sans-serif'; ctx.textAlign = 'center'; ctx.fillStyle = '#ffc828'; ctx.strokeStyle = '#3b2412'; ctx.lineWidth = 6; ctx.lineJoin = 'round';
    ctx.strokeText('✂', mx, my - 14); ctx.fillText('✂', mx, my - 14);
    ctx.globalAlpha = 1;
  }
}
function drawBalloon(bl) {
  const p = P(bl.body);
  if (bl.target) {
    const t = P(bl.target);
    ctx.strokeStyle = 'rgba(59,36,18,0.7)'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(p.x, p.y + bl.r); ctx.quadraticCurveTo(p.x + Math.sin(timer * 2 + bl.phase) * 12, (p.y + t.y) / 2, t.x, t.y - bl.target.game.r); ctx.stroke();
  }
  ctx.fillStyle = bl.color; ctx.strokeStyle = '#3b2412'; ctx.lineWidth = 3;
  ctx.beginPath(); ctx.ellipse(p.x, p.y, bl.r * 0.9, bl.r, 0, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(p.x - 6, p.y + bl.r + 6); ctx.lineTo(p.x, p.y + bl.r - 2); ctx.lineTo(p.x + 6, p.y + bl.r + 6); ctx.closePath(); ctx.fill();
  ctx.fillStyle = 'rgba(255,255,255,0.55)'; ctx.beginPath(); ctx.ellipse(p.x - bl.r * 0.35, p.y - bl.r * 0.4, bl.r * 0.18, bl.r * 0.3, -0.5, 0, Math.PI * 2); ctx.fill();
}
function slingReady() { return state === 'aim' && queue.length; }
function drawSlingBack() {
  ctx.lineCap = 'round';
  ctx.strokeStyle = '#6b3d16'; ctx.lineWidth = 20;
  ctx.beginPath(); ctx.moveTo(SLING_X, GROUND); ctx.lineTo(SLING_X, SLING_Y + 66); ctx.lineTo(SLING_X + 30, SLING_Y - 10); ctx.stroke();
  ctx.strokeStyle = '#8b5424'; ctx.lineWidth = 11;
  ctx.beginPath(); ctx.moveTo(SLING_X, GROUND); ctx.lineTo(SLING_X, SLING_Y + 66); ctx.lineTo(SLING_X + 30, SLING_Y - 10); ctx.stroke();
  if (slingReady()) {
    const p = pouch();
    ctx.strokeStyle = '#3b2412'; ctx.lineWidth = 10;
    ctx.beginPath(); ctx.moveTo(SLING_X + 30, SLING_Y - 6); ctx.lineTo(p.x + 10, p.y + 8); ctx.stroke();
    ctx.fillStyle = '#4a2c14'; ctx.beginPath(); ctx.ellipse(p.x - 2, p.y + 10, 26, 14, 0, 0, Math.PI * 2); ctx.fill();
  }
  ctx.strokeStyle = '#6b3d16'; ctx.lineWidth = 20;
  ctx.beginPath(); ctx.moveTo(SLING_X, SLING_Y + 66); ctx.lineTo(SLING_X - 28, SLING_Y - 10); ctx.stroke();
  ctx.strokeStyle = '#a0632b'; ctx.lineWidth = 11;
  ctx.beginPath(); ctx.moveTo(SLING_X, SLING_Y + 66); ctx.lineTo(SLING_X - 28, SLING_Y - 10); ctx.stroke();
}
function drawSlingFront() {
  if (!slingReady()) return;
  const p = pouch();
  const wob = pull.p > 0.05 ? 0 : Math.sin(timer * 3) * 0.05;
  drawBird(queue[0], p.x, p.y, wob, null);
  if (pull.p < 0.05) return;
  ctx.strokeStyle = '#3b2412'; ctx.lineWidth = 10; ctx.lineCap = 'round';
  ctx.beginPath(); ctx.moveTo(SLING_X - 28, SLING_Y - 6); ctx.lineTo(p.x - 14, p.y + 8); ctx.stroke();
}
function drawAimGuide() {
  if (!slingReady() || pull.p < 0.06) return;
  const p = pouch(), sp = pull.p * VMAX;
  let x = p.x, y = p.y, vx = -pull.x * sp, vy = -pull.y * sp;
  for (let i = 0; i < 60; i++) {
    for (let s = 0; s < 3; s++) { vy += G_STEP; vx *= 0.996; vy *= 0.996; x += vx; y += vy; }
    if (y > GROUND || x > levelW() + 200) break;
    const a = 1 - i / 60;
    ctx.fillStyle = `rgba(255,255,255,${(0.95 * a).toFixed(3)})`;
    ctx.beginPath(); ctx.arc(x, y, 3 + 5 * a, 0, Math.PI * 2); ctx.fill();
  }
}
function drawAimTouch() {
  if (!aimAnchor || !aimFinger) return;
  ctx.strokeStyle = 'rgba(59,36,18,0.35)'; ctx.lineWidth = 3;
  ctx.setLineDash([6, 8]);
  ctx.beginPath(); ctx.moveTo(aimAnchor.x, aimAnchor.y); ctx.lineTo(aimFinger.x, aimFinger.y); ctx.stroke();
  ctx.setLineDash([]);
  ctx.strokeStyle = aimMaxed ? 'rgba(255,122,26,0.9)' : 'rgba(59,36,18,0.45)'; ctx.lineWidth = 3;
  ctx.beginPath(); ctx.arc(aimAnchor.x, aimAnchor.y, 9, 0, Math.PI * 2); ctx.stroke();
  ctx.beginPath(); ctx.arc(aimFinger.x, aimFinger.y, 14, 0, Math.PI * 2); ctx.stroke();
  ctx.font = '700 15px Rubik, sans-serif'; ctx.textAlign = 'center'; ctx.lineJoin = 'round';
  const label = `${Math.round(pull.p * 100)}%`;
  ctx.lineWidth = 4; ctx.strokeStyle = '#fff'; ctx.strokeText(label, aimAnchor.x, aimAnchor.y - 20);
  ctx.fillStyle = aimMaxed ? '#c4520a' : '#3b2412'; ctx.fillText(label, aimAnchor.x, aimAnchor.y - 20);
}
function toScreen(wx, wy) { const z = zoomNow(); return [(wx - cam.x) * z, (wy - camY) * z]; }
function drawPullHint() {
  const [bx, by] = toScreen(SLING_X, SLING_Y);
  const t = (timer * 0.8) % 1, e = 1 - (1 - t) * (1 - t);
  const fx = bx - 20 - e * 90, fy = by + 20 + e * 45;
  ctx.globalAlpha = t < 0.85 ? 1 : (1 - t) / 0.15;
  ctx.fillStyle = 'rgba(255,255,255,0.9)'; ctx.strokeStyle = '#3b2412'; ctx.lineWidth = 3;
  ctx.beginPath(); ctx.arc(fx, fy, 15, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
  ctx.globalAlpha = 1;
  ctx.font = '400 22px "Secular One", Rubik, sans-serif'; ctx.textAlign = 'center'; ctx.lineJoin = 'round';
  ctx.lineWidth = 5; ctx.strokeStyle = '#3b2412';
  const msg = 'משוך אחורה ושחרר';
  ctx.strokeText(msg, bx + 40, by - 70); ctx.fillStyle = '#fff'; ctx.fillText(msg, bx + 40, by - 70);
}
function drawTapHint() {
  ctx.globalAlpha = 0.6 + 0.4 * Math.sin(timer * 10);
  ctx.font = '400 26px "Secular One", Rubik, sans-serif'; ctx.textAlign = 'center';
  ctx.lineWidth = 6; ctx.strokeStyle = '#3b2412'; ctx.lineJoin = 'round';
  ctx.strokeText('הקש!', cssW / 2, cssH * 0.3); ctx.fillStyle = '#fff'; ctx.fillText('הקש!', cssW / 2, cssH * 0.3);
  ctx.globalAlpha = 1;
}
function drawTrail(t, a) {
  for (const d of t) {
    ctx.globalAlpha = a; ctx.fillStyle = d.c || '#fff';
    ctx.beginPath(); ctx.arc(d.x, d.y, d.big ? 7 : 4, 0, Math.PI * 2); ctx.fill();
  }
  ctx.globalAlpha = 1;
}
function drawQueue() {
  const waiting = state === 'aim' ? queue.slice(1) : queue.slice();
  for (let i = 0; i < waiting.length; i++) {
    const kind = waiting[i], x = SLING_X - 165 - i * 64, hop = Math.max(0, Math.sin(timer * 5 + i * 1.7)) * 12;
    const lift = KINDS[kind].shape === 'circle' ? KINDS[kind].r : KINDS[kind].h / 2;
    drawBird(kind, x, GROUND - lift - hop, Math.sin(timer * 5 + i) * 0.06, null, 0.9);
  }
}
function drawBird(kind, x, y, angle, bd, scale = 1) {
  const k = KINDS[kind];
  ctx.save();
  ctx.translate(x, y);
  // squash & stretch: stretch along the direction of travel, squash on impact
  if (bd) {
    const v = V(bd.body), sp = Math.hypot(v.x, v.y);
    const st = 1 + Math.min(0.22, sp / 120), va = Math.atan2(v.y, v.x);
    const sq = bd.squash * 0.3;
    ctx.rotate(va); ctx.scale(st * (1 - sq), (1 / st) * (1 + sq)); ctx.rotate(-va);
  }
  ctx.rotate(angle);
  if (k.shape === 'circle') {
    const r = k.r * scale;
    ctx.fillStyle = k.ring; ctx.strokeStyle = '#3b2412'; ctx.lineWidth = 4;
    ctx.beginPath(); ctx.arc(0, 0, r, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
    if (imgFace.complete && imgFace.naturalWidth) {
      const sc = (r * 0.98) / 116;
      ctx.drawImage(imgFace, -116 * sc, -150 * sc, imgFace.naturalWidth * sc, imgFace.naturalHeight * sc);
    }
    if (kind === 'bomb') {
      ctx.strokeStyle = '#3b2412'; ctx.lineWidth = 5;
      ctx.beginPath(); ctx.moveTo(r * 0.3, -r * 0.9); ctx.quadraticCurveTo(r * 0.6, -r * 1.4, r * 0.95, -r * 1.25); ctx.stroke();
      const fl = bd && bd.fuse > 0 ? 1.6 : 1;
      ctx.fillStyle = Math.sin(timer * 30) > 0 ? '#ffd23f' : '#ff7a1a';
      ctx.beginPath(); ctx.arc(r * 0.95, -r * 1.25, 6 * fl, 0, Math.PI * 2); ctx.fill();
    }
  } else {
    if (bd && bd.stomping) { ctx.fillStyle = 'rgba(255,122,26,0.4)'; ctx.beginPath(); ctx.ellipse(0, -70, 20, 56, 0, 0, Math.PI * 2); ctx.fill(); }
    if (imgFoot.complete && imgFoot.naturalWidth) {
      const w = 50 * scale, h = w * imgFoot.naturalHeight / imgFoot.naturalWidth;
      ctx.drawImage(imgFoot, -w / 2, -h / 2, w, h);
    } else { ctx.fillStyle = '#f4f1ea'; ctx.fillRect(-23 * scale, -44 * scale, 46 * scale, 88 * scale); }
  }
  ctx.restore();
}
function drawBlock(b) { drawBlockShape(b, b.game.w, b.game.h, b.game.mat, b.game.hp / b.game.maxHp, 1, b.game.round); }
function drawBlockShape(b, w, h, mat, hpFrac, alpha, round) {
  const m = MATS[mat], p = P(b);
  ctx.save();
  ctx.globalAlpha = alpha;
  ctx.translate(p.x, p.y);
  ctx.rotate(p.a);
  if (round) {
    const r = w / 2;
    ctx.fillStyle = m.fill; ctx.strokeStyle = m.dark; ctx.lineWidth = 4;
    ctx.beginPath(); ctx.arc(0, 0, r, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
    ctx.fillStyle = m.light; ctx.beginPath(); ctx.arc(-r * 0.35, -r * 0.35, r * 0.28, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = m.dark; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.arc(r * 0.25, r * 0.15, r * 0.22, 0, Math.PI * 2); ctx.stroke();
    ctx.restore();
    return;
  }
  const x = -w / 2, y = -h / 2;
  ctx.fillStyle = m.fill; ctx.strokeStyle = m.dark; ctx.lineWidth = 3;
  roundRect(x, y, w, h, 4);
  if (mat === 'glass') { ctx.globalAlpha = alpha * 0.75; ctx.fill(); ctx.globalAlpha = alpha; } else ctx.fill();
  ctx.stroke();
  ctx.lineWidth = 2;
  if (mat === 'wood') {
    ctx.strokeStyle = m.light;
    const along = w > h;
    ctx.beginPath();
    for (let i = 1; i < 3; i++) {
      if (along) { ctx.moveTo(x + 6, y + (h * i) / 3); ctx.lineTo(x + w - 6, y + (h * i) / 3); }
      else { ctx.moveTo(x + (w * i) / 3, y + 6); ctx.lineTo(x + (w * i) / 3, y + h - 6); }
    }
    ctx.stroke();
  } else if (mat === 'glass') {
    ctx.strokeStyle = 'rgba(255,255,255,0.9)'; ctx.lineWidth = 3;
    ctx.beginPath(); ctx.moveTo(x + 5, y + h * 0.35); ctx.lineTo(x + w * 0.35, y + 5); ctx.stroke();
  } else if (mat === 'stone') {
    ctx.fillStyle = m.dark;
    for (let i = 0; i < 4; i++) { ctx.beginPath(); ctx.arc(x + w * (0.2 + 0.2 * i), y + h * (i % 2 ? 0.3 : 0.7), 2.5, 0, Math.PI * 2); ctx.fill(); }
  } else if (mat === 'tnt' && w > 30) {
    ctx.fillStyle = m.light; ctx.fillRect(x + 4, y + h * 0.36, w - 8, h * 0.28);
    ctx.fillStyle = m.dark; ctx.font = `400 ${h * 0.26}px "Secular One", Rubik, sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText('TNT', 0, 1);
    ctx.textBaseline = 'alphabetic';
  }
  const dmg = 1 - hpFrac;
  if (dmg > 0.33) {
    ctx.strokeStyle = 'rgba(40,20,5,0.7)'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(x + w * 0.2, y + h * 0.15); ctx.lineTo(x + w * 0.45, y + h * 0.5); ctx.lineTo(x + w * 0.3, y + h * 0.85);
    if (dmg > 0.66) { ctx.moveTo(x + w * 0.45, y + h * 0.5); ctx.lineTo(x + w * 0.8, y + h * 0.4); ctx.moveTo(x + w * 0.7, y + h * 0.1); ctx.lineTo(x + w * 0.6, y + h * 0.6); }
    ctx.stroke();
  }
  ctx.restore();
}
// Germ bodies are pre-rendered once per size/state; only the eyes and mouth are drawn live.
const germSprites = new Map();
function germSprite(r, king, hurtState) {
  const key = `${r}|${king}|${hurtState}`;
  let c = germSprites.get(key);
  if (c) return c;
  const pad = r * 0.35, half = r + pad, size = Math.ceil(half * 4);
  c = document.createElement('canvas'); c.width = c.height = size;
  const g = c.getContext('2d');
  g.scale(2, 2); g.translate(half, half);
  g.fillStyle = king ? '#4fae2c' : '#5bbd36';
  for (let i = 0; i < 10; i++) { const a = (i / 10) * Math.PI * 2; g.beginPath(); g.arc(Math.cos(a) * r * 1.02, Math.sin(a) * r * 1.02, r * 0.2, 0, Math.PI * 2); g.fill(); }
  const grd = g.createRadialGradient(-r * 0.35, -r * 0.4, r * 0.1, 0, 0, r);
  grd.addColorStop(0, '#c8f7a8'); grd.addColorStop(1, hurtState ? '#c9b844' : '#62c63c');
  g.fillStyle = grd; g.strokeStyle = '#2f6e17'; g.lineWidth = 3;
  g.beginPath(); g.arc(0, 0, r, 0, Math.PI * 2); g.fill(); g.stroke();
  c.half = half;
  germSprites.set(key, c);
  return c;
}
function drawGerm(b) {
  const g = b.game, r = g.r, p = P(b), x = p.x, y = p.y;
  const sq = g.squash * Math.sin(timer * 30) * 0.18 + g.fear * Math.sin(timer * 40) * 0.04;
  const spr = germSprite(r, g.king, g.hurtT > 0);
  ctx.save();
  ctx.translate(x, y);
  ctx.rotate(p.a + timer * 0.3);
  ctx.scale(1 + sq, 1 - sq);
  ctx.drawImage(spr, -spr.half, -spr.half, spr.half * 2, spr.half * 2);
  ctx.restore();
  const tgt = leader && flock.includes(leader) ? P(leader.body) : { x: SLING_X, y: SLING_Y };
  const la = Math.atan2(tgt.y - y, tgt.x - x);
  const hurtFace = g.hurtT > 0, fear = g.fear, blink = g.blink < 0 && fear < 0.3;
  for (const side of [-1, 1]) {
    const ex = x + side * r * 0.36, ey = y - r * 0.18, er = r * (0.26 + fear * 0.08);
    if (hurtFace) {
      ctx.strokeStyle = '#1b1b1b'; ctx.lineWidth = 3.5;
      ctx.beginPath(); ctx.moveTo(ex + side * er * 0.6, ey - er * 0.6); ctx.lineTo(ex - side * er * 0.6, ey); ctx.lineTo(ex + side * er * 0.6, ey + er * 0.6); ctx.stroke();
      continue;
    }
    ctx.fillStyle = '#fff'; ctx.strokeStyle = '#2f6e17'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.ellipse(ex, ey, er, blink ? er * 0.15 : er, 0, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
    if (!blink) { const pr = er * (0.45 - fear * 0.2); ctx.fillStyle = '#1b1b1b'; ctx.beginPath(); ctx.arc(ex + Math.cos(la) * er * 0.45, ey + Math.sin(la) * er * 0.45, pr, 0, Math.PI * 2); ctx.fill(); }
    if (fear < 0.5) {
      ctx.strokeStyle = '#2f6e17'; ctx.lineWidth = 3.5;
      ctx.beginPath(); ctx.moveTo(ex + er * 1.1 * side, ey - er * 1.5); ctx.lineTo(ex - er * 0.9 * side, ey - er * 1.0); ctx.stroke();
    }
  }
  ctx.strokeStyle = '#2f6e17'; ctx.lineWidth = 3;
  if (hurtFace) { ctx.beginPath(); ctx.arc(x, y + r * 0.5, r * 0.25, 1.1 * Math.PI, 1.9 * Math.PI); ctx.stroke(); }
  else if (fear > 0.5) { ctx.fillStyle = '#2f6e17'; ctx.beginPath(); ctx.ellipse(x, y + r * 0.42, r * 0.16, r * 0.22, 0, 0, Math.PI * 2); ctx.fill(); }
  else { ctx.beginPath(); ctx.arc(x, y + r * 0.2, r * 0.38, 0.15 * Math.PI, 0.85 * Math.PI); ctx.stroke(); }
  if (g.king) {
    ctx.fillStyle = '#ffc828'; ctx.strokeStyle = '#8a5a00'; ctx.lineWidth = 3;
    const cy = y - r - 4;
    ctx.beginPath();
    ctx.moveTo(x - r * 0.6, cy + 14); ctx.lineTo(x - r * 0.6, cy - 10); ctx.lineTo(x - r * 0.3, cy + 2); ctx.lineTo(x, cy - 16); ctx.lineTo(x + r * 0.3, cy + 2); ctx.lineTo(x + r * 0.6, cy - 10); ctx.lineTo(x + r * 0.6, cy + 14);
    ctx.closePath(); ctx.fill(); ctx.stroke();
  }
}
function drawParticles() {
  for (const p of particles) {
    const a = clamp(p.life / p.max, 0, 1);
    ctx.globalAlpha = a;
    if (p.t === 'ring') {
      ctx.strokeStyle = p.c; ctx.lineWidth = 12 * a + 2;
      ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx.stroke();
    } else if (p.t === 'flash') {
      const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.r);
      g.addColorStop(0, 'rgba(255,255,220,1)'); g.addColorStop(0.4, 'rgba(255,200,60,0.9)'); g.addColorStop(1, 'rgba(255,90,20,0)');
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx.fill();
    } else if (p.t === 'ghost') {
      const r = p.r * 0.9;
      ctx.globalAlpha = a * 0.85;
      ctx.fillStyle = '#f4fff0'; ctx.strokeStyle = '#2f6e17'; ctx.lineWidth = 2.5;
      ctx.beginPath(); ctx.arc(p.x, p.y, r, Math.PI, 0);
      for (let i = 0; i <= 4; i++) ctx.lineTo(p.x + r - (i * r) / 2, p.y + r * (i % 2 ? 0.7 : 1.1));
      ctx.closePath(); ctx.fill(); ctx.stroke();
      ctx.strokeStyle = '#2f6e17'; ctx.lineWidth = 2;
      for (const s of [-1, 1]) { const ex = p.x + s * r * 0.35, ey = p.y - r * 0.15, e = r * 0.15; ctx.beginPath(); ctx.moveTo(ex - e, ey - e); ctx.lineTo(ex + e, ey + e); ctx.moveTo(ex + e, ey - e); ctx.lineTo(ex - e, ey + e); ctx.stroke(); }
      ctx.strokeStyle = '#ffc828'; ctx.lineWidth = 3; ctx.beginPath(); ctx.ellipse(p.x, p.y - r - 8, r * 0.5, r * 0.15, 0, 0, Math.PI * 2); ctx.stroke();
    } else if (p.t === 'puff') {
      ctx.fillStyle = p.c; ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx.fill();
    } else if (p.t === 'chip') {
      ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.rot); ctx.fillStyle = p.c;
      if (p.shard) { ctx.beginPath(); ctx.moveTo(0, -p.r); ctx.lineTo(p.r * 0.7, p.r); ctx.lineTo(-p.r * 0.7, p.r * 0.6); ctx.fill(); }
      else ctx.fillRect(-p.r, -p.r * 0.4, p.r * 2, p.r * 0.8);
      ctx.restore();
    } else {
      ctx.fillStyle = p.c; ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx.fill();
    }
  }
  ctx.globalAlpha = 1;
}
function drawBubbles() {
  const z = Math.max(0.5, zoomNow());
  for (const b of bubbles) {
    const p = P(b.body), a = clamp(b.life / 0.25, 0, 1) * clamp((b.max - b.life) / 0.12, 0, 1);
    const fs = 22 / z, r = (b.body.game ? b.body.game.r : 40) + 14;
    ctx.font = `700 ${fs}px Rubik, sans-serif`; ctx.direction = 'rtl'; ctx.textAlign = 'center';
    const w = ctx.measureText(b.text).width + fs * 1.2, h = fs * 1.7;
    const bx = p.x, by = p.y - r - h / 2 - 12 / z;
    ctx.globalAlpha = a;
    ctx.fillStyle = '#fff'; ctx.strokeStyle = '#3b2412'; ctx.lineWidth = 3 / z;
    roundRect(bx - w / 2, by - h / 2, w, h, h / 2.4); ctx.fill(); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(bx - 8 / z, by + h / 2 - 1); ctx.lineTo(bx, by + h / 2 + 12 / z); ctx.lineTo(bx + 8 / z, by + h / 2 - 1); ctx.closePath(); ctx.fill();
    ctx.beginPath(); ctx.moveTo(bx - 8 / z, by + h / 2); ctx.lineTo(bx, by + h / 2 + 12 / z); ctx.lineTo(bx + 8 / z, by + h / 2); ctx.stroke();
    ctx.fillStyle = '#3b2412'; ctx.fillText(b.text, bx, by + fs * 0.35);
    ctx.direction = 'ltr';
  }
  ctx.globalAlpha = 1;
}
function drawPopups() {
  ctx.textAlign = 'center'; ctx.lineJoin = 'round';
  for (const p of popups) {
    const a = clamp(p.life / 0.4, 0, 1), grow = p.word ? 1 + Math.max(0, p.life - 0.8) * 1.5 : 1;
    const size = (p.word ? 60 : p.big ? 40 : 28) * grow / Math.max(0.6, zoomNow());
    ctx.globalAlpha = a;
    ctx.direction = /[֐-׿]/.test(p.text) ? 'rtl' : 'ltr';
    ctx.font = `400 ${size}px "Secular One", Rubik, sans-serif`;
    ctx.lineWidth = size * 0.16; ctx.strokeStyle = '#3b2412';
    ctx.strokeText(p.text, p.x, p.y);
    ctx.fillStyle = p.word ? p.c : '#fff';
    ctx.fillText(p.text, p.x, p.y);
  }
  ctx.direction = 'ltr';
  ctx.globalAlpha = 1;
}
function roundRect(x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
}
function chips(x, y, mat, n) {
  const m = MATS[mat];
  for (let i = 0; i < n; i++) particles.push({ t: 'chip', x: x + rf(-10, 10), y: y + rf(-10, 10), vx: rf(-8, 8), vy: rf(-11, -2), r: rf(4, 10), rot: rf(0, 6), vr: rf(-0.3, 0.3), life: rf(0.6, 1.2), max: 1.2, c: i % 2 ? m.fill : m.dark, shard: mat === 'glass' });
}
function puff(x, y, n, c) {
  for (let i = 0; i < n; i++) particles.push({ t: 'puff', x: x + rf(-14, 14), y: y + rf(-10, 10), vx: rf(-1.4, 1.4), vy: rf(-1.6, 0), r: rf(8, 20), life: rf(0.4, 0.9), max: 0.9, c });
}

// ======================================================================= input
canvas.addEventListener('pointerdown', (e) => {
  e.preventDefault();
  unlockAudio();
  if (paused || !level) return;
  if (state === 'flying') {
    // first tap fires the special move; a later tap skips the wait for a rolling character
    if (!useAbility() && leader && (leader.hit || leader.used)) for (const bd of flock) bd.afterHit = 99;
    return;
  }
  const [bx, by] = toScreen(SLING_X, SLING_Y);
  const onBird = Math.hypot(e.clientX - bx, e.clientY - by) < Math.max(60, 60 * cam.z);
  if ((state === 'aim' || state === 'intro') && queue.length && !aimPointer && (onBird || e.clientX < cssW * 0.6)) {
    if (state === 'intro') { state = 'aim'; camMode = 'overview'; }
    aimPointer = e.pointerId; camManualT = 0;
    aimAnchor = onBird ? { x: bx, y: by } : { x: e.clientX, y: e.clientY };
    aimFinger = { x: e.clientX, y: e.clientY }; aimMaxed = false;
    try { canvas.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
    sfx('stretch', 0.45);
    updatePull(e);
  } else if (!panPointer) {
    panPointer = e.pointerId; panLastX = e.clientX;
    try { canvas.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
  }
});
function updatePull(e) {
  aimFinger = { x: e.clientX, y: e.clientY };
  let dx = e.clientX - aimAnchor.x, dy = e.clientY - aimAnchor.y;
  const len = Math.hypot(dx, dy);
  const maxPx = clamp(Math.min(cssW, cssH) * 0.32, 90, 170);
  if (len < 4) { pull.p = 0; return; }
  dx /= len; dy /= len;
  // Pulling to the right would fire backwards: keep the shot pointed forward.
  if (dx > 0.2) { dx = 0.2; dy = Math.sign(dy || 1) * Math.sqrt(1 - dx * dx); }
  pull.x = dx; pull.y = dy;
  const p = Math.min(1, len / maxPx);
  if (p >= 1 && !aimMaxed) { aimMaxed = true; buzz(12); sfx('click', 0.3, 0.7); }
  if (p < 0.98) aimMaxed = false;
  pull.p = p;
}
canvas.addEventListener('pointermove', (e) => {
  if (e.pointerId === aimPointer) { e.preventDefault(); updatePull(e); }
  else if (e.pointerId === panPointer) {
    cam.x = clampCamX(cam.x - (e.clientX - panLastX) / zoomNow(), cam.z);
    panLastX = e.clientX; camManualT = 2.5;
  }
});
function endPointer(e) {
  if (e.pointerId === aimPointer) {
    aimPointer = null; aimAnchor = null; aimFinger = null;
    if (state === 'aim' && pull.p > 0.12) launch(); else pull.p = 0;
  }
  if (e.pointerId === panPointer) panPointer = null;
}
canvas.addEventListener('pointerup', endPointer);
canvas.addEventListener('pointercancel', endPointer);
canvas.addEventListener('contextmenu', (e) => e.preventDefault());
window.addEventListener('keydown', (e) => {
  if (e.key === ' ' && state === 'flying') { e.preventDefault(); useAbility(); }
  if (e.key === 'Escape' && level && !['won', 'lost'].includes(state)) togglePause();
});

// ======================================================================= DOM screens
const $ = (id) => document.getElementById(id);
const screens = ['menu', 'levelsScreen', 'pauseScreen', 'resultScreen'];
function showOnly(...ids) {
  for (const s of screens) $(s).hidden = !ids.includes(s);
  $('hud').hidden = !ids.includes('hud');
}
function soundLabel() { const t = save.sound ? 'צליל: פועל' : 'צליל: כבוי'; $('btnSound').textContent = t; $('btnSound2').textContent = t; }
function toggleSound() {
  save.sound = !save.sound; store.set('sound', save.sound);
  if (save.sound) { unlockAudio(); sfx('click'); } else stopMusic();
  soundLabel();
}
function openLevels() {
  const grid = $('levelGrid');
  grid.textContent = '';
  const total = save.stars.reduce((a, s) => a + (s || 0), 0);
  const sum = document.createElement('div');
  sum.className = 'world-title total'; sum.textContent = `★ ${total} / ${LEVELS.length * 3}`;
  grid.appendChild(sum);
  LEVELS.forEach((lv, i) => {
    if (i % 8 === 0) {
      const h = document.createElement('div');
      h.className = 'world-title';
      h.textContent = `עולם ${i / 8 + 1} · ${THEMES[i / 8].name}`;
      grid.appendChild(h);
    }
    const unlocked = i === 0 || (save.stars[i - 1] || 0) > 0;
    const st = save.stars[i] || 0;
    const btn = document.createElement('button');
    btn.className = 'lvl w' + Math.floor(i / 8); btn.type = 'button'; btn.disabled = !unlocked;
    btn.setAttribute('aria-label', `שלב ${i + 1}: ${lv.name}${unlocked ? '' : ' (נעול)'}`);
    btn.innerHTML = `<span>${unlocked ? i + 1 : '🔒'}</span><small>${lv.name}</small><span class="st">${'★'.repeat(st)}${'☆'.repeat(3 - st)}</span>`;
    btn.addEventListener('click', () => { unlockAudio(); sfx('click'); paused = false; loadLevel(i); });
    grid.appendChild(btn);
  });
  showOnly('levelsScreen');
}
function togglePause() {
  paused = !paused;
  if (paused) showOnly('hud', 'pauseScreen'); else showOnly('hud');
}
const STAR_SVG = '<svg class="star" viewBox="0 0 60 60" aria-hidden="true"><path d="M30 4l7.6 15.9 17.4 2.3-12.7 12.1 3.2 17.3L30 43.3 14.5 51.6l3.2-17.3L5 22.2l17.4-2.3z"/></svg>';
function showResult(won) {
  const st = won ? starCount() : 0;
  if (won) {
    save.stars[levelIdx] = Math.max(save.stars[levelIdx] || 0, st);
    save.best[levelIdx] = Math.max(save.best[levelIdx] || 0, score);
    store.set('stars', save.stars); store.set('best', save.best);
  }
  $('resultTitle').textContent = won ? (st === 3 ? 'מושלם!' : 'ניצחון!') : 'החיידקים שרדו';
  $('resultScore').textContent = score.toLocaleString('en-US');
  $('resultNote').textContent = won
    ? (levelIdx === LEVELS.length - 1 ? 'ניצחת את כל החיידקים! אתה אגדה.' : `שיא בשלב: ${(save.best[levelIdx] || 0).toLocaleString('en-US')}`)
    : `נשארו ${germs.length} חיידקים. נסה לפגוע בבסיס, ב-TNT, בחבלים או בבלונים.`;
  const stars = $('stars');
  stars.innerHTML = STAR_SVG.repeat(3);
  [...stars.children].forEach((s, i) => {
    if (i < st) setTimeout(() => { if (state !== 'won') return; s.classList.add('on'); sfx('star', 0.5, 1 + i * 0.12); }, 650 + i * 330);
  });
  $('btnNext').hidden = !(won && levelIdx < LEVELS.length - 1);
  const id = loadId;
  setTimeout(() => { if (id === loadId && (state === 'won' || state === 'lost')) showOnly('hud', 'resultScreen'); }, won ? 300 : 700);
}

$('btnPlay').addEventListener('click', () => { unlockAudio(); sfx('click'); openLevels(); });
$('btnSound').addEventListener('click', toggleSound);
$('btnSound2').addEventListener('click', toggleSound);
$('btnBackMenu').addEventListener('click', () => { sfx('click'); level = null; showOnly('menu'); });
$('btnPause').addEventListener('click', () => { unlockAudio(); sfx('click'); togglePause(); });
$('btnRestart').addEventListener('click', () => { sfx('click'); paused = false; loadLevel(levelIdx); });
$('btnResume').addEventListener('click', () => { sfx('click'); togglePause(); });
$('btnPauseRestart').addEventListener('click', () => { sfx('click'); paused = false; loadLevel(levelIdx); });
$('btnPauseLevels').addEventListener('click', () => { sfx('click'); paused = false; openLevels(); });
$('btnRetry').addEventListener('click', () => { sfx('click'); loadLevel(levelIdx); });
$('btnNext').addEventListener('click', () => { sfx('click'); loadLevel(levelIdx + 1); });
$('btnResultLevels').addEventListener('click', () => { sfx('click'); openLevels(); });
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') {
    if (level && ['aim', 'flying', 'settling', 'intro'].includes(state) && !paused) togglePause();
    if (actx) actx.suspend().catch(() => {});
  } else if (actx) actx.resume().catch(() => {});
});

// ======================================================================= main loop
let last = performance.now(), acc = 0;
function physicsStep() {
  for (const b of engine.world.bodies) { b.px = b.position.x; b.py = b.position.y; b.pa = b.angle; }
  for (let i = 0; i < SUBSTEPS; i++) Engine.update(engine, STEP / SUBSTEPS);
  update(STEP / 1000);
}
function frame(now) {
  const dt = Math.min(0.1, (now - last) / 1000);
  last = now;
  if (level && engine && !paused) {
    if (hitStop > 0) { hitStop -= dt; timer += dt; alpha = 1; }
    else {
      acc += dt;
      let n = 0;
      while (acc >= STEP / 1000 && n < 5) { physicsStep(); acc -= STEP / 1000; n++; }
      if (n === 5) acc = 0;
      alpha = clamp(acc / (STEP / 1000), 0, 1);
    }
    updateCamera(dt);
  } else { timer += dt; alpha = 1; }
  if (score !== shownScore) { const el = $('score'); el.textContent = score.toLocaleString('en-US'); shownScore = score; el.classList.remove('bump'); void el.offsetWidth; el.classList.add('bump'); }
  render();
  requestAnimationFrame(frame);
}
function start() {
  resize();
  window.addEventListener('resize', resize);
  clouds = Array.from({ length: 10 }, (_, k) => ({ x: rf(-200, 2700), y: rf(80, 520), s: rf(0.6, 1.4), f: rf(0.2, 0.55), k }));
  cam.z = Math.min(cssW / 1100, cssH / 640); applyCam(); cam.x = 0;
  soundLabel();
  initAudio();
  requestAnimationFrame(frame);
}
const hot = window.claude && window.claude.hot;
if (hot && hot.snapshot) hot.snapshot(() => ({ stars: save.stars }));
if (hot && hot.ready) hot.ready(start); else start();

// Test hook so an automated run can play levels deterministically.
window.__ff = {
  load: (i) => { paused = false; loadLevel(i); },
  get state() { return state; },
  get germs() { return germs.length; },
  get blocks() { return blocks.length; },
  get score() { return score; },
  get queue() { return queue.length; },
  get flock() { return flock.map((b) => { const v = V(b.body); return { kind: b.kind, x: b.body.position.x, y: b.body.position.y, vx: v.x, vy: v.y }; }); },
  levels: LEVELS.length,
  stars: () => starCount(),
  shoot(angleDeg, power) { const a = angleDeg * Math.PI / 180; pull.x = -Math.cos(a); pull.y = Math.sin(a); pull.p = power; launch(); },
  ability: () => useAbility(),
  step(n) { for (let i = 0; i < n; i++) { if (hitStop > 0) hitStop = 0; physicsStep(); updateCamera(STEP / 1000); } },
  render,
  germPos: () => germs.map((g) => [Math.round(g.position.x), Math.round(g.position.y)]),
  positions: () => engine.world.bodies.filter((b) => !b.isStatic && b.sleepThreshold !== Infinity).map((b) => [Math.round(b.position.x), Math.round(b.position.y)]),
};
})();
