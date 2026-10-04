'use strict';
/* ===== Core utilities, audio, haptics, drawing helpers ===== */

const TAU = Math.PI * 2;
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
const lerp = (a, b, t) => a + (b - a) * t;
const rand = (a, b) => a + Math.random() * (b - a);
const randi = (a, b) => Math.floor(rand(a, b + 1));
const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
const dist = (ax, ay, bx, by) => Math.hypot(bx - ax, by - ay);
const angNorm = (a) => { a = (a + Math.PI) % TAU; if (a < 0) a += TAU; return a - Math.PI; };
const easeOutBack = (t) => { const c = 1.70158; return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2); };
const easeOutCubic = (t) => 1 - Math.pow(1 - t, 3);
function shuffle(a) { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; }

const FONT = "'Fredoka', system-ui, sans-serif";

const COLORS = [
  { name: 'Red',    main: '#FF4D6D', dark: '#C9184A', light: '#FF8FA3', glow: 'rgba(255,77,109,.55)' },
  { name: 'Blue',   main: '#3A86FF', dark: '#1F5FCC', light: '#8DB8FF', glow: 'rgba(58,134,255,.55)' },
  { name: 'Green',  main: '#2DD881', dark: '#14A85C', light: '#86F0B8', glow: 'rgba(45,216,129,.55)' },
  { name: 'Yellow', main: '#FFBE0B', dark: '#D99A00', light: '#FFDB70', glow: 'rgba(255,190,11,.55)' },
];

const Store = {
  get(k, d) { try { const v = localStorage.getItem('cp_' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem('cp_' + k, JSON.stringify(v)); } catch (e) { /* storage unavailable */ } },
};

/* ---------- Audio: tiny synth, no asset files ---------- */
const Sfx = {
  ctx: null, master: null, enabled: true, noiseBuf: null,
  init() {
    if (this.ctx) { if (this.ctx.state === 'suspended') this.ctx.resume(); return; }
    try {
      this.ctx = new (window.AudioContext || window.webkitAudioContext)();
      this.master = this.ctx.createGain();
      this.master.gain.value = 0.55;
      const comp = this.ctx.createDynamicsCompressor();
      this.master.connect(comp); comp.connect(this.ctx.destination);
      const len = this.ctx.sampleRate;
      this.noiseBuf = this.ctx.createBuffer(1, len, this.ctx.sampleRate);
      const d = this.noiseBuf.getChannelData(0);
      for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
    } catch (e) { this.ctx = null; }
  },
  tone(freq, dur, o = {}) {
    if (!this.enabled || !this.ctx) return;
    const t = this.ctx.currentTime + (o.delay || 0);
    const osc = this.ctx.createOscillator(), g = this.ctx.createGain();
    osc.type = o.type || 'sine';
    osc.frequency.setValueAtTime(freq, t);
    if (o.slide) osc.frequency.exponentialRampToValueAtTime(Math.max(30, freq * o.slide), t + dur);
    const v = o.vol == null ? 0.25 : o.vol;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(v, t + 0.008);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    osc.connect(g); g.connect(this.master);
    osc.start(t); osc.stop(t + dur + 0.03);
  },
  noise(dur, o = {}) {
    if (!this.enabled || !this.ctx) return;
    const t = this.ctx.currentTime + (o.delay || 0);
    const src = this.ctx.createBufferSource(); src.buffer = this.noiseBuf;
    const f = this.ctx.createBiquadFilter(); f.type = 'lowpass';
    f.frequency.setValueAtTime(o.freq || 1200, t);
    f.frequency.exponentialRampToValueAtTime(Math.max(60, (o.freq || 1200) * (o.slide || 0.2)), t + dur);
    const g = this.ctx.createGain();
    g.gain.setValueAtTime(o.vol == null ? 0.5 : o.vol, t);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    src.connect(f); f.connect(g); g.connect(this.master);
    src.start(t); src.stop(t + dur + 0.05);
  },
  _last: {},
  throttle(name, ms) { const n = performance.now(); if (this._last[name] && n - this._last[name] < ms) return false; this._last[name] = n; return true; },

  click()   { this.tone(520, 0.07, { type: 'triangle', vol: 0.18 }); this.tone(780, 0.05, { type: 'sine', vol: 0.1, delay: 0.03 }); },
  tap(i = 0){ if (this.throttle('tap' + i, 45)) this.tone(440 + i * 90 + Math.random() * 30, 0.06, { type: 'triangle', vol: 0.11 }); },
  ready()   { this.tone(660, 0.09, { type: 'triangle', vol: 0.18 }); this.tone(990, 0.12, { type: 'triangle', vol: 0.14, delay: 0.06 }); },
  count()   { this.tone(587, 0.16, { type: 'square', vol: 0.09 }); },
  go()      { this.tone(880, 0.32, { type: 'square', vol: 0.1 }); this.tone(1320, 0.32, { type: 'triangle', vol: 0.12, delay: 0.02 }); },
  hit()     { this.noise(0.18, { freq: 2200, vol: 0.35 }); this.tone(180, 0.15, { type: 'square', vol: 0.12, slide: 0.4 }); },
  boom()    { this.noise(0.8, { freq: 900, vol: 0.8, slide: 0.08 }); this.tone(90, 0.6, { type: 'sine', vol: 0.5, slide: 0.3 }); },
  shoot()   { if (this.throttle('shoot', 40)) this.tone(900, 0.1, { type: 'square', vol: 0.06, slide: 0.35 }); },
  bounce()  { if (this.throttle('bounce', 50)) this.tone(320, 0.08, { type: 'sine', vol: 0.15, slide: 1.8 }); },
  whoosh()  { this.noise(0.25, { freq: 3000, vol: 0.18, slide: 0.3 }); },
  jump()    { this.tone(380, 0.18, { type: 'sine', vol: 0.16, slide: 2.2 }); },
  coin()    { this.tone(988, 0.08, { type: 'square', vol: 0.08 }); this.tone(1318, 0.18, { type: 'square', vol: 0.08, delay: 0.07 }); },
  wrong()   { this.tone(200, 0.25, { type: 'sawtooth', vol: 0.1, slide: 0.7 }); },
  out()     { this.tone(500, 0.45, { type: 'triangle', vol: 0.18, slide: 0.25 }); },
  win() {
    [523, 659, 784, 1047].forEach((f, i) => this.tone(f, 0.22, { type: 'triangle', vol: 0.16, delay: i * 0.1 }));
    this.tone(1568, 0.5, { type: 'sine', vol: 0.12, delay: 0.42 });
  },
  champion() {
    [523, 523, 523, 659, 784, 659, 784, 1047].forEach((f, i) => this.tone(f, 0.2, { type: 'square', vol: 0.07, delay: i * 0.12 }));
  },
};

const Haptics = {
  enabled: true,
  buzz(ms) { if (!this.enabled) return; try { navigator.vibrate && navigator.vibrate(ms); } catch (e) { /* not supported */ } },
};

/* ---------- Canvas drawing helpers ---------- */
function rrect(g, x, y, w, h, r) {
  r = Math.min(r, w / 2, h / 2);
  g.beginPath();
  g.moveTo(x + r, y);
  g.arcTo(x + w, y, x + w, y + h, r);
  g.arcTo(x + w, y + h, x, y + h, r);
  g.arcTo(x, y + h, x, y, r);
  g.arcTo(x, y, x + w, y, r);
  g.closePath();
}

function drawStar(g, x, y, r, fill) {
  g.fillStyle = fill;
  g.beginPath();
  for (let k = 0; k < 10; k++) {
    const rr = k % 2 ? r * 0.45 : r, a = (k / 10) * TAU - Math.PI / 2;
    g.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
  }
  g.closePath(); g.fill();
}

function font(size, weight = 700) { return `${weight} ${Math.round(size)}px ${FONT}`; }

function text(g, str, x, y, size, color = '#fff', o = {}) {
  g.font = font(size, o.weight || 700);
  g.textAlign = o.align || 'center';
  g.textBaseline = o.base || 'middle';
  if (o.shadow !== false) { g.fillStyle = 'rgba(0,0,0,.28)'; g.fillText(str, x, y + size * 0.08); }
  g.fillStyle = color;
  g.fillText(str, x, y);
}

function wrapLines(g, str, maxW) {
  const words = str.split(' '); const lines = []; let cur = '';
  for (const w of words) {
    const t = cur ? cur + ' ' + w : w;
    if (g.measureText(t).width > maxW && cur) { lines.push(cur); cur = w; } else cur = t;
  }
  if (cur) lines.push(cur);
  return lines;
}

/* Cute blob character used by several games. Faces direction `ang`. */
function drawBlob(g, x, y, r, color, o = {}) {
  const ang = o.ang || 0;
  g.save();
  g.translate(x, y);
  if (o.squash) g.scale(1 + o.squash, 1 - o.squash);
  // shadow
  g.fillStyle = 'rgba(0,0,0,.25)';
  g.beginPath(); g.ellipse(0, r * 0.28, r * 1.02, r * 0.9, 0, 0, TAU); g.fill();
  // body
  const grd = g.createRadialGradient(-r * 0.35, -r * 0.4, r * 0.1, 0, 0, r * 1.05);
  grd.addColorStop(0, color.light); grd.addColorStop(0.55, color.main); grd.addColorStop(1, color.dark);
  g.fillStyle = grd;
  g.beginPath(); g.arc(0, 0, r, 0, TAU); g.fill();
  // eyes look toward ang
  const ex = Math.cos(ang) * r * 0.32, ey = Math.sin(ang) * r * 0.32;
  const px = -Math.sin(ang) * r * 0.3, py = Math.cos(ang) * r * 0.3;
  const dead = o.dead;
  for (const s of [-1, 1]) {
    const cx = ex + px * s, cy = ey + py * s;
    if (dead) {
      g.strokeStyle = '#1b1b2f'; g.lineWidth = r * 0.12; g.lineCap = 'round';
      const k = r * 0.13;
      g.beginPath(); g.moveTo(cx - k, cy - k); g.lineTo(cx + k, cy + k); g.moveTo(cx + k, cy - k); g.lineTo(cx - k, cy + k); g.stroke();
    } else {
      g.fillStyle = '#fff';
      g.beginPath(); g.arc(cx, cy, r * 0.22, 0, TAU); g.fill();
      g.fillStyle = '#1b1b2f';
      g.beginPath(); g.arc(cx + Math.cos(ang) * r * 0.08, cy + Math.sin(ang) * r * 0.08, r * 0.11, 0, TAU); g.fill();
    }
  }
  // gloss
  g.fillStyle = 'rgba(255,255,255,.35)';
  g.beginPath(); g.ellipse(-r * 0.42, -r * 0.48, r * 0.22, r * 0.13, -0.6, 0, TAU); g.fill();
  g.restore();
}

/* SVG face for menus (matches canvas blob look) */
function faceSVG(state) {
  if (state === 'off') {
    return `<svg class="face" viewBox="0 0 64 64"><circle cx="32" cy="32" r="26" fill="none" stroke="currentColor" stroke-width="4" stroke-dasharray="7 7"/><path d="M24 24l16 16M40 24L24 40" stroke="currentColor" stroke-width="5" stroke-linecap="round"/></svg>`;
  }
  if (state === 'cpu') {
    return `<svg class="face" viewBox="0 0 64 64"><rect x="10" y="14" width="44" height="38" rx="12" fill="rgba(0,0,0,.18)"/><rect x="10" y="12" width="44" height="38" rx="12" fill="#fff"/><line x1="32" y1="12" x2="32" y2="4" stroke="#fff" stroke-width="4"/><circle cx="32" cy="4" r="4" fill="#fff"/><rect x="18" y="24" width="10" height="10" rx="3" fill="#1b1b2f"/><rect x="36" y="24" width="10" height="10" rx="3" fill="#1b1b2f"/><rect x="22" y="40" width="20" height="4" rx="2" fill="#1b1b2f" opacity=".5"/></svg>`;
  }
  return `<svg class="face" viewBox="0 0 64 64"><circle cx="32" cy="35" r="26" fill="rgba(0,0,0,.18)"/><circle cx="32" cy="32" r="26" fill="#fff"/><circle cx="23" cy="29" r="6.5" fill="#1b1b2f"/><circle cx="41" cy="29" r="6.5" fill="#1b1b2f"/><circle cx="25" cy="27" r="2.2" fill="#fff"/><circle cx="43" cy="27" r="2.2" fill="#fff"/><path d="M24 41q8 7 16 0" stroke="#1b1b2f" stroke-width="4" fill="none" stroke-linecap="round"/></svg>`;
}

const CROWN_SVG = (fill = '#FFBE0B') => `<svg viewBox="0 0 64 64"><path d="M8 22l12 10 12-18 12 18 12-10-5 28H13z" fill="${fill}" stroke="rgba(0,0,0,.2)" stroke-width="2" stroke-linejoin="round"/><rect x="13" y="46" width="38" height="8" rx="3" fill="${fill}"/><circle cx="8" cy="20" r="4" fill="${fill}"/><circle cx="32" cy="12" r="4" fill="${fill}"/><circle cx="56" cy="20" r="4" fill="${fill}"/><circle cx="32" cy="36" r="4" fill="#fff" opacity=".85"/></svg>`;
