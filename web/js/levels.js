// Level definitions: a tiny DSL on top of a tile grid. Everything is deterministic data.
(function () {
'use strict';
const T = 16, ROWS = 15;
const TILE = { E: 0, GND: 1, BRK: 2, QC: 3, QP: 4, USED: 5, HARD: 6, PTL: 7, PTR: 8, PL: 9, PR: 10, ICE: 11, QS: 12, QU: 13, CLOUD: 14, GATE: 15, BRKM: 16, CANNON: 17, QV: 18, TREE: 19, SPK: 20, HID: 21, TRUNK: 22, STEM: 23, MUSH: 40, BRIDGE: 41, BRKS: 42, BRKU: 43, BRKP: 44 };
// solid tiles: 1..19 and 40+ ; 20..39 are see-through decorations / hazards
const isSolid = t => t > 0 && (t < 20 || t >= 40);
const CH = { '.': 0, B: TILE.BRK, Q: TILE.QC, P: TILE.QP, S: TILE.QS, U: TILE.QU, H: TILE.HARD, C: TILE.CLOUD, I: TILE.ICE, M: TILE.BRKM, V: TILE.QV, X: TILE.BRKS, Y: TILE.BRKU, Z: TILE.BRKP };   // X/Y/Z: bricks hiding a star / 1UP / power-up

class Sec {
  constructor(b, x0, n, base) { this.b = b; this.x0 = x0; this.n = n; this.base = base; }
  t(dx, r, t) { this.b.set(this.x0 + dx, r, t); return this; }
  row(dx, r, str) { for (let i = 0; i < str.length; i++) if (str[i] !== ' ') this.b.set(this.x0 + dx + i, r, CH[str[i]] || 0); return this; }
  mob(k, dx, row, opts) { this.b.en.push(Object.assign({ k, tx: this.x0 + dx, row: row == null ? (k === 'fly' || k === 'fish' ? this.base - 3 : this.base) : row }, opts || {})); return this; }
  coin(dx, r) { this.b.coins.push([this.x0 + dx, r]); return this; }
  coins(dx, r, n) { for (let i = 0; i < n; i++) this.coin(dx + i, r); return this; }
  arc(dx, r, n) { for (let i = 0; i < n; i++) this.coin(dx + i, r - Math.round(Math.sin(i / (n - 1) * Math.PI) * 2)); return this; }
  pipe(dx, h, key) { return this.pipeOn(dx, this.base + 1, h, key); }
  pipeOn(dx, g, h, key) {
    for (let r = g - h; r < g; r++) { const top = r === g - h; this.b.set(this.x0 + dx, r, top ? TILE.PTL : TILE.PL); this.b.set(this.x0 + dx + 1, r, top ? TILE.PTR : TILE.PR); }
    if (key) this.b.tags[key] = { tx: this.x0 + dx, top: g - h };
    return this;
  }
  // enterable pipe: Down on it -> room (then back to `ret` tag, default the same pipe) or straight to another level
  warp(dx, h, opts, g) { g = g || this.base + 1; this.pipeOn(dx, g, h); this.b.warps.push(Object.assign({ tx: this.x0 + dx, top: g - h }, opts)); return this; }
  plant(dx, h) { this.b.en.push({ k: 'plant', tx: this.x0 + dx, row: this.base - h }); return this; }
  up(dx, h) { for (let i = 0; i < h; i++) for (let k = 0; k <= i; k++) this.b.set(this.x0 + dx + i, 12 - k, TILE.HARD); return this; }
  down(dx, h) { for (let i = 0; i < h; i++) for (let k = 0; k < h - i; k++) this.b.set(this.x0 + dx + i, 12 - k, TILE.HARD); return this; }
  spikes(dx, n) { for (let i = 0; i < n; i++) this.b.set(this.x0 + dx + i, this.base, TILE.SPK); return this; }
  cannon(dx, h) { for (let k = 0; k < h; k++) this.b.set(this.x0 + dx, this.base - k, k === h - 1 ? TILE.CANNON : TILE.HARD); return this; }
  spring(dx) { this.b.springs.push({ tx: this.x0 + dx, row: this.base }); return this; }
  hid(dx, r, item) { this.b.set(this.x0 + dx, r, TILE.HID); this.b.hidden[(this.x0 + dx) + ',' + r] = item || 'U'; return this; }
  firebar(dx, r, len, speed, ang) { this.b.set(this.x0 + dx, r, TILE.HARD); this.b.fbars.push({ tx: this.x0 + dx, row: r, len, speed: speed || 0.03, ang: ang || 0 }); return this; }
  thwomp(dx, r) { this.b.en.push({ k: 'thwomp', tx: this.x0 + dx, row: r }); return this; }
  pod(dx) { this.b.en.push({ k: 'pod', tx: this.x0 + dx, row: 14 }); return this; }
  check(dx) { this.b.checks.push(this.x0 + dx); return this; }
  tag(dx, key) { this.b.tags[key] = { tx: this.x0 + dx, top: 0 }; return this; }
  // block that grows a climbable vine up into a bonus room in the sky
  vine(dx, r, opts) { this.b.set(this.x0 + dx, r, TILE.QV); this.b.vines[(this.x0 + dx) + ',' + r] = opts; return this; }
  // see-saw pair: standing on one lowers it and raises the other; too far and the rope snaps
  balance(dx1, dx2, r, w) { const i = this.b.plats.length; this.b.plats.push({ x: (this.x0 + dx1) * T, y: r * T, w: w * T, type: 'bal', pair: i + 1 }, { x: (this.x0 + dx2) * T, y: r * T, w: w * T, type: 'bal', pair: i }); return this; }
  fallLift(dx, r, w) { this.b.plats.push({ x: (this.x0 + dx) * T, y: r * T, w: w * T, type: 'fall' }); return this; }
  mover(dx, r, w, axis, range, speed, phase) {
    this.b.plats.push({ x: (this.x0 + dx) * T, y: r * T, w: w * T, axis, range: range * T, speed, phase: phase || 0 });
    return this;
  }
}

class LB {
  constructor() {
    this.map = []; for (let r = 0; r < ROWS; r++) this.map.push([]);
    this.x = 0; this.en = []; this.coins = []; this.plats = []; this.checks = []; this.arena = null; this.poleTx = 0; this.castleX = 0;
    this.warps = []; this.rooms = []; this.springs = []; this.fbars = []; this.hidden = {}; this.tags = {}; this.water = false;
    this.vines = {}; this.fishZones = []; this.lakZones = []; this.loops = []; this.currents = []; this.swimZones = [];
  }
  set(x, r, t) { if (x < 0 || r < 0 || r >= ROWS) return; const row = this.map[r]; while (row.length <= x) row.push(0); row[x] = t; }
  run(n, fn, tile) {
    const x0 = this.x; tile = tile || TILE.GND;
    for (let i = 0; i < n; i++) { this.set(x0 + i, 13, tile); this.set(x0 + i, 14, tile); }
    this.x += n; if (fn) fn(new Sec(this, x0, n, 12)); return this;
  }
  island(n, top, fn, tile) {
    const x0 = this.x; tile = tile || TILE.CLOUD;
    for (let i = 0; i < n; i++) { this.set(x0 + i, top, tile); this.set(x0 + i, top + 1, tile); }
    this.x += n; if (fn) fn(new Sec(this, x0, n, top - 1)); return this;
  }
  pit(n) { this.x += n; return this; }
  // treetop / mushroom / bridge platforms over a void
  tree(n, top, fn) { const x0 = this.x; for (let i = 0; i < n; i++) this.set(x0 + i, top, TILE.TREE); const c = x0 + (n >> 1) - 1; for (let r = top + 1; r < ROWS; r++) { this.set(c, r, TILE.TRUNK); this.set(c + 1, r, TILE.TRUNK); } this.x += n; if (fn) fn(new Sec(this, x0, n, top - 1)); return this; }
  mush(n, top, fn) { const x0 = this.x; for (let i = 0; i < n; i++) this.set(x0 + i, top, TILE.MUSH); const c = x0 + ((n - 1) >> 1); for (let r = top + 1; r < ROWS; r++) { this.set(c, r, TILE.STEM); if (n % 2 === 0) this.set(c + 1, r, TILE.STEM); } this.x += n; if (fn) fn(new Sec(this, x0, n, top - 1)); return this; }
  bridge(n, row, fn) { const x0 = this.x; for (let i = 0; i < n; i++) this.set(x0 + i, row, TILE.BRIDGE); this.x += n; if (fn) fn(new Sec(this, x0, n, row - 1)); return this; }
  fishZone(a, b) { this.fishZones.push([a, b]); return this; }
  lakituZone(a, b) { this.lakZones.push([a, b]); return this; }
  current(a, b) { this.currents.push([a, b]); return this; }          // underwater: strong pull downwards
  swimZone(a, b) { this.swimZones.push([a, b]); return this; }        // underwater: endless fish from the right
  // castle maze: reaching tx on the wrong route (high/low) sends the hero back to `back`
  mazeLoop(tx, back, need) { this.loops.push({ tx, back, need }); return this; }
  lavaPit(n) { this.en.push({ k: 'pod', tx: this.x + (n >> 1), row: 14 }); this.x += n; return this; }
  hole(a, b) { for (let x = a; x <= b; x++) { this.set(x, 13, 0); this.set(x, 14, 0); } return this; }
  at(x0, fn) { fn(new Sec(this, x0, 0, 12)); return this; }
  // solid ceiling strip (underground / castle / sea) over [a,b], `rows` thick
  ceiling(a, b, rows, tile) { for (let x = a; x <= b; x++) for (let r = 0; r < rows; r++) this.set(x, r, tile || TILE.HARD); return this; }
  // a self-contained bonus room reachable through a warp pipe; fn builds it with absolute coords (36 columns wide)
  room(theme, fn, opts) {
    const r = new LB(); r.theme = theme; r.isRoom = true; r.spawnTx = 4; Object.assign(r, opts || {});
    r.run(36);
    for (let y = 0; y <= 12; y++) { r.set(0, y, TILE.HARD); r.set(35, y, TILE.HARD); }
    fn(r); r.finish(); this.rooms.push(r); return this.rooms.length - 1;
  }
  goal() {
    const x0 = this.x; this.run(26);
    this.poleTx = x0 + 4; this.set(this.poleTx, 12, TILE.HARD); this.castleX = (x0 + 8) * T; return this;
  }
  bossArena(cfg) {
    const x0 = this.x, n = 34; cfg = cfg || {};
    if (cfg.bridge) {
      this.run(6);                                             // entry floor
      this.bridge(n - 12, 13);                                 // bridge over lava
      this.run(6);                                             // axe platform
    } else this.run(n);
    this.arena = Object.assign({ x0, x1: x0 + n - 1, trigger: x0 + 3, bossTx: x0 + n - 8 - (cfg.bridge ? 2 : 0), hp: 3, shots: 0, jump: 70, spd: 0.8 }, cfg);
    for (let r = 5; r <= 12; r++) this.set(x0 + n - 1, r, TILE.GATE);
    if (cfg.bridge) { this.arena.axe = { tx: x0 + n - 3, row: 12 }; this.arena.br = [x0 + 6, x0 + n - 7]; }
    if (cfg.lift) this.plats.push({ x: (x0 + 9) * T, y: 8 * T, w: 3 * T, axis: 'x', range: 10 * T, speed: 0.6, phase: 0 });
    return this;
  }
  finish() {
    let cols = 0; for (const row of this.map) cols = Math.max(cols, row.length);
    for (const row of this.map) while (row.length < cols) row.push(0);
    this.cols = cols;
    // resolve return pipes
    for (const k in this.vines) { const v = this.vines[k]; if (v.ret) { const t = this.tags[v.ret]; v.retTx = t.tx; } }
    for (const w of this.warps) { if (w.ret) { const t = this.tags[w.ret]; w.retTx = t.tx; w.retTop = t.top; } else { w.retTx = w.tx; w.retTop = w.top; } }
    return this;
  }
}

function oldLevel1Unused(b) {
  // faithful port of the original 1-1 (absolute coordinates), plus a few extras
  b.run(212).hole(69, 70).hole(86, 88).hole(149, 150);
  b.at(0, s => {
    s.t(16, 9, TILE.QC);
    s.t(20, 9, TILE.BRK).t(21, 9, TILE.QP).t(22, 9, TILE.BRK).t(23, 9, TILE.QC).t(24, 9, TILE.BRK).t(22, 5, TILE.QU);
    s.pipe(28, 2).pipe(38, 3).pipe(46, 4).pipe(57, 4);
    s.t(64, 9, TILE.QC);
    s.t(77, 9, TILE.BRK).t(78, 9, TILE.QP).t(79, 9, TILE.BRK);
    s.row(80, 5, 'BBBBBBBB'); s.row(91, 5, 'BBB'); s.t(94, 5, TILE.QC).t(94, 9, TILE.BRK);
    s.t(100, 9, TILE.BRK).t(101, 9, TILE.BRK);
    s.t(106, 9, TILE.QC).t(109, 9, TILE.QC).t(109, 5, TILE.QP).t(112, 9, TILE.QC);
    s.t(118, 9, TILE.BRK); s.row(121, 5, 'BBB');
    s.row(128, 5, 'BQQB'); s.row(129, 9, 'BB');
    s.up(134, 4).down(140, 4).up(145, 4).down(151, 4);
    s.pipe(160, 2).pipe(172, 2);
    s.row(166, 9, 'BBQB').t(103, 9, TILE.QS);
    s.up(178, 8); for (let k = 0; k < 8; k++) s.t(186, 12 - k, TILE.HARD);
    for (const [x, r] of [[22, 12], [40, 12], [51, 12], [53, 12], [81, 4], [83, 4], [97, 12], [99, 12], [114, 12], [116, 12], [124, 12], [126, 12], [129, 12], [131, 12], [163, 12], [165, 12], [175, 12], [177, 12]]) s.mob('walk', x, r);
    s.mob('fast', 105, 12).mob('fly', 132, 8);
    s.plant(38, 3).plant(172, 2).mob('shell', 62).mob('shell', 120);
    for (const [x, r] of [[57, 7], [58, 7], [69, 10], [70, 10], [86, 8], [87, 7], [88, 8], [121, 4], [122, 4], [123, 4], [138, 8], [139, 8], [149, 6], [150, 6], [10, 10], [11, 10], [12, 10], [195, 10]]) s.coin(x, r);
    s.check(100);
  });
  b.poleTx = 194; b.set(194, 12, TILE.HARD); b.castleX = 198 * T;
}

function level2(b) { // desert
  b.run(14, s => { s.mob('walk', 10).arc(3, 9, 6); });
  b.run(12, s => { s.row(2, 9, 'BQBQB').t(5, 5, TILE.QS); s.mob('fast', 8); });
  b.pit(3);
  b.run(11, s => { s.spikes(4, 2).arc(3, 8, 5); s.mob('walk', 8); });
  b.run(20, s => { s.pipe(3, 2).pipe(10, 3).plant(10, 3); s.mob('walk', 7).mob('fast', 13); s.coins(4, 8, 4); });
  b.pit(3);
  b.run(5);
  b.run(18, s => { s.up(3, 4).down(7, 4).t(5, 5, TILE.QP).coins(4, 7, 4); s.mob('walk', 13).mob('walk', 15); s.check(1); });
  b.run(14, s => { s.spikes(5, 3).arc(4, 9, 5); s.mob('fly', 10, 8); });
  b.pit(4);
  b.run(10, s => { s.row(2, 9, 'BPB'); s.mob('spiky', 8); });
  b.run(14, s => { s.spikes(4, 2).spikes(9, 2); s.arc(3, 9, 5).arc(8, 9, 5); s.mob('fly', 12, 8); });
  b.run(12, s => { s.row(1, 9, 'BQBQBQB'); s.mob('fast', 10).mob('walk', 8).mob('shell', 4); });
  b.pit(3);
  b.island(6, 10, s => { s.coins(1, 8, 4); s.mob('walk', 4); }, TILE.HARD);
  b.pit(3);
  b.run(14, s => { s.pipe(3, 3).pipe(9, 2).plant(3, 3); s.mob('spiky', 12).mob('walk', 6); s.t(6, 5, TILE.QU); });
  b.run(21, s => { s.up(2, 5).down(7, 5).t(5, 4, TILE.QC).t(6, 4, TILE.QC).t(7, 4, TILE.QC); s.mob('fast', 14); s.check(0); });
  b.pit(4);
  b.run(10, s => { s.mob('walk', 7); });
  b.run(20, s => { s.spikes(4, 3).spikes(11, 2); s.arc(3, 9, 6).arc(10, 9, 4); s.mob('fly', 8, 8); s.mob('fly', 14, 7); });
  b.run(14, s => { s.row(2, 9, 'BQB'); s.row(8, 5, 'BPB'); s.mob('fast', 10).mob('spiky', 12); });
  b.goal();
}

function level3(b) { // crystal cave
  b.run(12, s => { s.mob('walk', 9); s.coins(3, 10, 4); });
  b.run(18, s => { s.row(2, 9, 'BQB'); s.spikes(8, 2); s.mob('walk', 12); });
  b.pit(3);
  b.run(10, s => { s.row(1, 9, 'BPB').row(6, 5, 'QQ'); s.mob('spiky', 8); });
  b.run(21, s => { s.spikes(4, 2).spikes(10, 2); s.arc(3, 9, 5).arc(9, 9, 5); s.mob('fly', 13, 8); });
  b.pit(3);
  b.island(5, 10, s => { s.coins(1, 8, 3); }, TILE.HARD);
  b.pit(3);
  b.run(14, s => { s.pipe(3, 3).pipe(9, 4).plant(9, 4); s.mob('walk', 6).mob('fast', 12); s.check(0); });
  b.run(12, s => { s.row(1, 9, 'BBQBB'); s.row(3, 5, 'BSB'); s.mob('spiky', 9); });
  b.pit(4);
  b.run(10, s => { s.mob('walk', 7).mob('shell', 5); s.coins(2, 9, 3); });
  b.run(18, s => { s.spikes(4, 3).spikes(12, 3); s.arc(3, 9, 5).arc(11, 9, 5); s.mob('fly', 9, 7).mob('fly', 15, 8); });
  b.run(21, s => { s.up(2, 3).down(5, 3).up(9, 4).down(13, 1); s.mob('walk', 7).mob('fast', 12); });
  b.pit(3);
  b.island(4, 11, null, TILE.HARD); b.pit(2); b.island(4, 9, s => { s.coins(1, 7, 2); }, TILE.HARD); b.pit(2); b.island(5, 11, s => { s.t(2, 7, TILE.QP); }, TILE.HARD);
  b.pit(3);
  b.run(16, s => { s.row(2, 9, 'BQBQB'); s.mob('spiky', 8).mob('walk', 12); s.check(0); });
  b.run(12, s => { s.spikes(4, 2).spikes(8, 2); s.arc(3, 9, 4).arc(7, 9, 4); s.mob('fly', 10, 7); });
  b.run(16, s => { s.pipe(3, 2).pipe(8, 4).pipe(13, 2).plant(13, 2); s.mob('walk', 6).mob('spiky', 11); });
  b.goal();
}

function level4(b) { // snow peaks, ice floors
  const I = TILE.ICE;
  b.run(12, s => { s.mob('walk', 9); s.coins(3, 10, 4); });
  b.run(14, s => { s.row(3, 9, 'BQB'); s.mob('walk', 10).mob('shell', 6); }, I);
  b.run(12, s => { s.spikes(5, 2); s.arc(4, 9, 4); s.mob('walk', 9); }, I);
  b.pit(3);
  b.run(14, s => { s.row(2, 9, 'BPB').row(8, 9, 'BSB'); s.mob('fast', 11); }, I);
  b.run(20, s => { s.pipe(4, 3).pipe(10, 2).plant(10, 2); s.mob('walk', 7).mob('spiky', 13); s.check(0); });
  b.pit(3);
  b.run(10, s => { s.mob('fly', 6, 7); s.coins(2, 9, 4); }, I);
  b.run(20, s => { s.spikes(4, 2).spikes(11, 2); s.arc(3, 8, 5).arc(10, 8, 5); s.mob('walk', 8); }, I);
  b.pit(4);
  b.run(12, s => { s.up(3, 4).down(7, 4); s.mob('fast', 10); });
  b.run(14, s => { s.row(2, 9, 'BQBQB'); s.mob('spiky', 9).mob('fly', 12, 8); }, I);
  b.pit(3);
  b.island(6, 10, s => { s.coins(1, 8, 4); s.mob('walk', 4); }, TILE.ICE);
  b.pit(3);
  b.run(14, s => { s.pipe(3, 4).pipe(9, 3).plant(9, 3); s.mob('walk', 6).mob('spiky', 12); s.t(6, 5, TILE.QU); s.check(0); }, I);
  b.run(20, s => { s.spikes(4, 3).spikes(11, 2); s.arc(3, 9, 6).arc(10, 9, 4); s.mob('fly', 8, 7).mob('fly', 14, 8); }, I);
  b.pit(3);
  b.run(12, s => { s.row(1, 9, 'BPQB'); s.mob('fast', 9); });
  b.run(21, s => { s.up(2, 5).down(7, 5); s.mob('walk', 12); }, I);
  b.pit(4);
  b.run(14, s => { s.mob('spiky', 9).mob('fast', 12); s.row(3, 9, 'BQB'); }, I);
  b.goal();
}

function level5(b) { // sky islands
  b.run(10, s => { s.mob('walk', 8); s.coins(2, 10, 4); });
  b.pit(3);
  b.island(7, 12, s => { s.coins(1, 10, 5); s.mob('walk', 5); });
  b.pit(3);
  b.island(5, 10, s => { s.row(1, 6, 'QPQ'); s.coins(1, 8, 3); });
  b.pit(3);
  b.island(8, 11, s => { s.mob('fly', 7, 7); s.arc(1, 9, 6); s.check(0); });
  b.pit(3);
  b.island(4, 9, s => { s.coins(1, 7, 3); });
  b.pit(3);
  b.island(5, 11, s => { s.mob('spiky', 3).t(2, 7, TILE.QS); });
  b.pit(5);
  b.at(b.x - 4, s => { s.mover(0, 10, 3, 'x', 2, 0.7); s.coins(1, 8, 2); });
  b.island(6, 11, s => { s.mob('walk', 4); s.coins(1, 9, 3); });
  b.pit(3);
  b.island(5, 9, s => { s.row(1, 5, 'BQB'); s.mob('fly', 4, 6); });
  b.pit(3);
  b.island(4, 11, s => { s.check(1); });
  b.pit(3);
  b.island(5, 12, s => { s.mob('walk', 3); s.coins(1, 10, 3); });
  b.pit(5);
  b.at(b.x - 4, s => { s.mover(0, 9, 3, 'y', 3, 0.5); s.coins(1, 6, 3); });
  b.island(6, 10, s => { s.mob('spiky', 3); s.row(0, 6, 'QUQ'); });
  b.pit(3);
  b.island(5, 12, s => { s.mob('fast', 3); s.coins(1, 10, 3); });
  b.pit(3);
  b.island(4, 10, s => { s.mob('fly', 2, 7); });
  b.pit(3);
  b.island(6, 12, s => { s.coins(1, 10, 4); });
  b.pit(3);
  b.run(26);
  b.poleTx = b.x - 22; b.set(b.poleTx, 12, TILE.HARD); b.castleX = (b.poleTx + 4) * T;
}

function level6(b) { // lava castle + boss
  b.run(12, s => { s.mob('walk', 9); s.coins(3, 10, 4); });
  b.run(18, s => { s.row(2, 9, 'BQB'); s.spikes(8, 2); s.mob('walk', 12); });
  b.lavaPit(3);
  b.run(10, s => { s.row(1, 9, 'BPB'); s.mob('spiky', 8); });
  b.run(21, s => { s.spikes(4, 2).spikes(10, 2); s.arc(3, 9, 5).arc(9, 9, 5); s.mob('fly', 13, 8); s.check(0); });
  b.lavaPit(4);
  b.run(14, s => { s.pipe(3, 3).pipe(9, 4).plant(3, 3); s.mob('fast', 6).mob('spiky', 12); });
  b.run(18, s => { s.up(2, 4).down(6, 4); s.row(2, 6, 'QSQ'); s.mob('walk', 10); });
  b.lavaPit(3);
  b.island(4, 11, null, TILE.HARD); b.lavaPit(3); b.island(4, 10, s => { s.coins(1, 8, 2); }, TILE.HARD); b.lavaPit(3); b.island(5, 11, s => { s.t(2, 7, TILE.QP); }, TILE.HARD);
  b.lavaPit(3);
  b.run(16, s => { s.row(2, 9, 'BQBQB'); s.mob('fast', 8).mob('spiky', 12); s.check(0); });
  b.run(19, s => { s.spikes(4, 3).spikes(10, 2); s.arc(3, 9, 6).arc(9, 9, 4); s.mob('fly', 8, 7).mob('fly', 13, 8); });
  b.lavaPit(4);
  b.run(10, s => { s.mob('walk', 7); });
  b.run(22, s => { s.pipe(4, 3).pipe(11, 4).plant(11, 4); s.mob('spiky', 8).mob('fast', 15); s.t(7, 5, TILE.QU); });
  b.lavaPit(3);
  b.run(8);
  b.bossArena({ bridge: true });
}

function level7(b) { // underground: warp pipes to coin rooms, plants, shells
  const coinRoom = b.room('under', r => {
    r.at(0, s => {
      s.coins(6, 11, 20).coins(8, 8, 16).coins(12, 5, 8);
      s.t(3, 9, TILE.QP).t(30, 9, TILE.QU);
      s.warp(31, 3, { back: true });
    });
  });
  const shortcut = b.room('under', r => {
    r.at(0, s => {
      s.row(7, 9, 'BBBBBQBBBBB'); s.coins(8, 7, 9); s.coins(8, 11, 14);
      s.mob('walk', 20).mob('walk', 24);
      s.warp(31, 3, { back: true });
    });
  });
  b.run(14, s => { s.mob('walk', 10); s.coins(4, 10, 4); });
  b.run(14, s => { s.row(2, 9, 'BMBQB'); s.mob('shell', 9); });
  b.pit(3);
  b.run(10, s => { s.mob('walk', 7); s.coins(2, 9, 3); });
  b.run(22, s => { s.pipe(3, 2).plant(3, 2).warp(10, 3, { room: coinRoom }); s.mob('walk', 7).mob('shell', 16); s.check(0); });
  b.pit(3);
  b.run(8);
  b.run(16, s => { s.up(2, 3).down(5, 3); s.spikes(11, 2); s.mob('walk', 9); s.arc(10, 9, 4); });
  b.run(27, s => { s.row(2, 9, 'BQBPB').hid(6, 5, 'U'); s.mob('fast', 10).mob('shell', 15); s.warp(18, 3, { room: shortcut, ret: 'b' }); });
  b.pit(4);
  b.run(10, s => { s.mob('walk', 7); });
  b.run(20, s => { s.pipe(3, 3).plant(3, 3).pipe(11, 2, 'b'); s.mob('walk', 7).mob('shell', 15); s.check(0); });
  b.run(18, s => { s.row(2, 9, 'BMB'); s.spikes(8, 2); s.mob('fly', 11, 8); });
  b.pit(3);
  b.island(6, 10, s => { s.coins(1, 8, 4); s.mob('walk', 4); }, TILE.HARD);
  b.pit(3);
  b.run(21, s => { s.up(2, 4).down(6, 4); s.row(2, 6, 'QSQ'); s.mob('fast', 12).mob('shell', 16); });
  b.run(19, s => { s.row(2, 9, 'BQB'); s.spikes(8, 2).hid(11, 8, 'Q'); s.mob('walk', 12); });
  b.pit(4);
  b.run(10, s => { s.mob('walk', 7); });
  b.run(22, s => { s.pipe(3, 2).plant(3, 2).pipe(10, 3).plant(10, 3); s.mob('shell', 7).mob('spiky', 16); s.check(0); });
  b.goal();
  b.ceiling(0, b.x - 1, 3, TILE.HARD);
}

function level8(b) { // sea: swimming
  b.water = true;
  b.run(14, s => { s.mob('fish', 9); s.coins(3, 10, 5); });
  b.run(16, s => { s.up(3, 3).down(6, 3); s.mob('walk', 12); s.arc(9, 9, 5); });
  b.run(14, s => { s.row(2, 6, 'BPB'); s.mob('fish', 6, 7).mob('fish', 12, 9); s.coins(3, 9, 4); });
  b.run(18, s => { s.pipe(4, 3).plant(4, 3).pipe(11, 2); s.mob('walk', 8).mob('fish', 15, 8); });
  b.run(16, s => { s.up(2, 5).down(7, 5); s.coins(3, 6, 5); s.mob('fish', 13, 7); s.check(0); });
  b.run(14, s => { s.row(1, 9, 'BQBQB'); s.mob('fish', 8, 6).mob('walk', 11); });
  b.run(20, s => { s.spikes(4, 3).spikes(12, 3); s.arc(3, 8, 6).arc(11, 8, 6); s.mob('fish', 9, 9); s.mob('fish', 16, 7); });
  b.run(18, s => { s.up(2, 4).down(6, 4); s.pipe(12, 2); s.mob('shell', 15); s.row(3, 5, 'BSB'); });
  b.run(14, s => { s.mob('fish', 5, 8).mob('fish', 10, 6); s.coins(2, 10, 8); });
  b.run(16, s => { s.pipe(3, 4).plant(3, 4).pipe(10, 3); s.mob('walk', 7).mob('fast', 13); s.check(0); });
  b.run(20, s => { s.spikes(5, 3).spikes(13, 3); s.arc(4, 8, 6).arc(12, 8, 6); s.mob('fish', 10, 8).mob('fish', 17, 6); });
  b.run(14, s => { s.row(2, 9, 'BQBUB'); s.mob('walk', 10).mob('fish', 6, 7); });
  b.goal();
  b.ceiling(0, b.x - 1, 1, TILE.HARD);
}

function level9(b) { // night forest: cannons, springs, secret warp zone
  const zone = b.room('night', r => {
    r.at(0, s => { s.warp(8, 3, { level: 21 }); s.warp(15, 3, { level: 22 }); s.warp(22, 3, { level: 23 }); s.coins(5, 4, 20); });
  });
  b.run(14, s => { s.mob('walk', 10); s.coins(4, 10, 5); });
  b.run(16, s => { s.row(2, 9, 'BQBQB').hid(8, 5, 'U'); s.mob('shell', 11); });
  b.run(18, s => { s.cannon(8, 1); s.mob('walk', 13); s.arc(4, 9, 4); });
  b.pit(3);
  b.run(12, s => { s.mob('spiky', 8); s.coins(2, 9, 4); });
  b.run(24, s => { s.pipe(3, 2).plant(3, 2); s.cannon(12, 2); s.mob('walk', 8).mob('shell', 18); s.check(0); });
  b.pit(3);
  b.run(10, s => { s.mob('walk', 7); });
  b.run(26, s => { s.spring(4); s.row(10, 6, 'HHHHHHH'); s.row(10, 7, 'HHHHHHH'); s.warp(12, 2, { room: zone }, 6); s.coins(2, 5, 3); s.mob('walk', 20).mob('fast', 23); });
  b.run(16, s => { s.row(2, 9, 'BPQB'); s.cannon(9, 1); s.mob('shell', 6).mob('fly', 12, 8); });
  b.pit(4);
  b.run(10, s => { s.mob('walk', 7); });
  b.run(22, s => { s.pipe(3, 3).plant(3, 3); s.cannon(11, 3); s.cannon(15, 1); s.mob('shell', 7).mob('walk', 19); s.hid(6, 5, 'Q'); });
  b.run(14, s => { s.spring(3); s.coins(3, 3, 6); s.row(1, 9, 'BQB'); s.mob('fly', 9, 8); s.check(0); });
  b.pit(3);
  b.island(5, 10, s => { s.coins(1, 8, 3); }, TILE.HARD); b.pit(3); b.island(5, 12, s => { s.mob('walk', 3); }, TILE.HARD);
  b.pit(3);
  b.run(24, s => { s.cannon(5, 2); s.cannon(10, 1); s.row(13, 9, 'BQBQB'); s.mob('spiky', 9).mob('shell', 18); });
  b.run(16, s => { s.up(2, 4).down(6, 4); s.mob('fast', 12); });
  b.pit(4);
  b.run(10, s => { s.mob('walk', 6); });
  b.run(22, s => { s.pipe(3, 2).plant(3, 2).pipe(9, 3); s.cannon(15, 2); s.mob('shell', 12).mob('spiky', 19); s.check(0); });
  b.goal();
}

function level10(b) { // volcano: podoboos, fire bars, lava hops
  b.run(12, s => { s.mob('walk', 9); s.coins(3, 10, 4); });
  b.run(18, s => { s.row(2, 9, 'BQB'); s.spikes(8, 2); s.mob('shell', 12); });
  b.lavaPit(4);
  b.run(10, s => { s.row(1, 9, 'BPB'); s.mob('spiky', 8); });
  b.run(22, s => { s.firebar(8, 9, 4, 0.03, 1.2); s.mob('walk', 4).mob('fast', 15); s.arc(12, 9, 5); s.check(0); });
  b.lavaPit(3);
  b.island(5, 11, s => { s.mob('walk', 2); }, TILE.HARD); b.lavaPit(3); b.island(5, 10, s => { s.coins(1, 8, 3); }, TILE.HARD); b.lavaPit(3); b.island(5, 11, null, TILE.HARD);
  b.lavaPit(3);
  b.run(20, s => { s.pipe(3, 3).plant(3, 3); s.mob('spiky', 9).mob('shell', 14); s.row(10, 9, 'BQB'); });
  b.run(22, s => { s.firebar(6, 9, 4, -0.03, 0); s.firebar(15, 9, 4, 0.03, 2); s.mob('walk', 10); s.coins(3, 6, 4); });
  b.lavaPit(5);
  b.at(b.x - 4, s => { s.mover(0, 11, 3, 'x', 2, 0.8); s.coins(1, 8, 2); });
  b.run(14, s => { s.mob('walk', 4).mob('fast', 9); s.check(0); });
  b.run(21, s => { s.spikes(4, 3).spikes(10, 2); s.arc(3, 9, 6).arc(9, 9, 4); s.mob('fly', 8, 7).mob('fly', 14, 8); });
  b.lavaPit(4);
  b.run(10, s => { s.mob('walk', 7); });
  b.run(24, s => { s.up(2, 4).down(6, 4); s.firebar(15, 9, 5, 0.025, 0.5); s.mob('shell', 11).mob('fast', 19); s.t(4, 5, TILE.QS); });
  b.lavaPit(3);
  b.island(4, 11, null, TILE.HARD); b.lavaPit(3); b.island(4, 9, s => { s.coins(1, 7, 2); }, TILE.HARD); b.lavaPit(3); b.island(5, 11, s => { s.t(2, 7, TILE.QP); }, TILE.HARD);
  b.lavaPit(3);
  b.run(22, s => { s.pipe(3, 2).plant(3, 2).pipe(10, 3).plant(10, 3); s.mob('spiky', 15).mob('fast', 19); s.check(0); });
  b.goal();
}

function level11(b) { // thunder fortress: thwomps, fire bars, cannons, mini-boss
  b.run(12, s => { s.mob('walk', 9); s.coins(3, 10, 4); });
  b.run(20, s => { s.row(2, 9, 'BQB'); s.thwomp(10, 2); s.mob('shell', 14); });
  b.run(14, s => { s.cannon(5, 2); s.mob('walk', 10); s.arc(8, 9, 4); });
  b.lavaPit(3);
  b.run(12, s => { s.row(1, 9, 'BPB'); s.mob('spiky', 8); });
  b.run(24, s => { s.thwomp(6, 2); s.thwomp(14, 2); s.mob('fast', 10).mob('walk', 20); s.check(0); });
  b.lavaPit(4);
  b.run(10, s => { s.mob('walk', 7); });
  b.run(22, s => { s.firebar(7, 9, 4, 0.03, 0); s.firebar(16, 9, 4, -0.03, 1); s.mob('shell', 11); s.coins(3, 6, 3); });
  b.run(16, s => { s.up(2, 4).down(6, 4); s.cannon(12, 1); s.mob('spiky', 13); s.t(4, 5, TILE.QS); });
  b.lavaPit(3);
  b.island(5, 11, null, TILE.HARD); b.lavaPit(3); b.island(5, 10, s => { s.coins(1, 8, 3); }, TILE.HARD); b.lavaPit(3); b.island(5, 11, null, TILE.HARD);
  b.lavaPit(3);
  b.run(26, s => { s.thwomp(5, 2); s.cannon(10, 2); s.firebar(15, 9, 4, 0.03, 2); s.mob('fast', 19).mob('shell', 23); s.check(0); });
  b.run(14, s => { s.spikes(4, 3); s.arc(3, 9, 6); s.mob('fly', 9, 8); });
  b.lavaPit(4);
  b.run(10, s => { s.mob('walk', 7); });
  b.run(20, s => { s.thwomp(6, 2); s.pipe(12, 3).plant(12, 3); s.mob('spiky', 9); s.row(3, 9, 'BQB'); });
  b.run(8);
  b.bossArena({ hp: 4, shots: 150, jump: 60, spd: 0.9, bridge: true });
}

function level12(b) { // shadow fortress: everything + final boss
  b.run(12, s => { s.mob('walk', 9); s.coins(3, 10, 4); });
  b.run(20, s => { s.row(2, 9, 'BQB'); s.cannon(8, 1); s.mob('shell', 12).mob('fast', 16); });
  b.lavaPit(3);
  b.run(12, s => { s.row(1, 9, 'BPB'); s.mob('spiky', 8); });
  b.run(24, s => { s.thwomp(6, 2); s.firebar(13, 9, 4, 0.035, 0); s.mob('fast', 10).mob('walk', 20); s.check(0); });
  b.lavaPit(4);
  b.run(10, s => { s.mob('walk', 7); });
  b.run(24, s => { s.pipe(3, 3).plant(3, 3); s.cannon(10, 2); s.cannon(14, 1); s.mob('shell', 8).mob('spiky', 19); });
  b.lavaPit(3);
  b.island(4, 11, null, TILE.HARD); b.lavaPit(3); b.island(4, 9, s => { s.coins(1, 7, 2); s.mob('fly', 2, 6); }, TILE.HARD); b.lavaPit(3); b.island(5, 11, s => { s.t(2, 7, TILE.QS); }, TILE.HARD);
  b.lavaPit(3);
  b.run(26, s => { s.firebar(6, 9, 4, 0.03, 0); s.firebar(15, 9, 4, -0.03, 1); s.thwomp(21, 2); s.mob('shell', 11).mob('fast', 18); s.check(0); });
  b.lavaPit(5);
  b.at(b.x - 4, s => { s.mover(0, 11, 3, 'x', 2, 0.9); s.coins(1, 8, 2); });
  b.run(16, s => { s.mob('walk', 4).mob('spiky', 10); });
  b.run(22, s => { s.up(2, 4).down(6, 4); s.cannon(12, 2); s.cannon(17, 1); s.mob('fast', 14).mob('shell', 19); s.t(4, 5, TILE.QP); });
  b.lavaPit(4);
  b.run(10, s => { s.mob('walk', 7); });
  b.run(26, s => { s.thwomp(5, 2); s.thwomp(12, 2); s.firebar(19, 9, 5, 0.03, 1); s.mob('walk', 9).mob('shell', 16); s.check(0); });
  b.run(8);
  b.bossArena({ hp: 5, shots: 110, jump: 50, spd: 1.0, minion: 'shell', bridge: true });
}

function level13(b) { // treetops: red shells patrol platforms, winged shells, falling & balance lifts
  b.run(12, s => { s.mob('walk', 9); s.coins(3, 10, 4); });
  b.pit(2);
  b.tree(6, 11, s => { s.mob('rshell', 3); });
  b.pit(3);
  b.tree(8, 9, s => { s.coins(1, 7, 6); s.mob('para', 5); });
  b.pit(3);
  b.tree(5, 11, s => { s.t(2, 7, TILE.QP); });
  b.pit(5);
  b.at(b.x - 4, s => { s.fallLift(0, 10, 3); s.coins(1, 8, 2); });
  b.tree(7, 10, s => { s.mob('rshell', 4); s.check(1); });
  b.pit(3);
  b.mush(5, 9, s => { s.coins(1, 7, 3); });
  b.pit(2);
  b.mush(4, 11);
  b.pit(3);
  b.tree(9, 10, s => { s.mob('rshell', 3).mob('para', 7); s.row(2, 6, 'BQB'); });
  b.pit(6);
  b.at(b.x - 5, s => { s.balance(0, 3, 10, 2); s.coins(0, 8, 5); });
  b.tree(6, 10, s => { s.mob('para', 3); s.coins(1, 8, 4); });
  b.pit(3);
  b.mush(6, 10, s => { s.t(3, 6, TILE.QU); });
  b.pit(3);
  b.tree(5, 12, s => { s.mob('rshell', 2); });
  b.pit(5);
  b.at(b.x - 4, s => { s.fallLift(0, 11, 3); });
  b.tree(8, 10, s => { s.mob('rshell', 5); s.check(1); });
  b.pit(3); b.mush(4, 9); b.pit(3);
  b.tree(6, 11, s => { s.mob('para', 3); s.coins(1, 9, 4); });
  b.pit(3);
  b.goal();
}

function level14(b) { // bridges over the sea, fish leaping from below
  b.run(12, s => { s.mob('walk', 9); s.coins(3, 10, 4); });
  const z0 = b.x;
  b.pit(2); b.bridge(16, 10, s => { s.coins(3, 7, 8); s.mob('walk', 12); });
  b.pit(3); b.island(4, 9, s => { s.t(1, 5, TILE.QP); }, TILE.HARD);
  b.pit(3); b.bridge(18, 10, s => { s.mob('rpara', 9, 5); s.coins(2, 7, 5); s.check(1); });
  b.pit(3); b.bridge(12, 11, s => { s.mob('shell', 6); });
  b.pit(2); b.island(3, 9, null, TILE.HARD); b.pit(2);
  b.bridge(20, 10, s => { s.mob('rpara', 6, 4).mob('rpara', 14, 5); s.arc(3, 8, 6); s.t(10, 6, TILE.QS); });
  b.pit(3); b.bridge(14, 10, s => { s.mob('para', 8); s.check(1); });
  b.pit(3); b.island(5, 10, s => { s.mob('walk', 2); }, TILE.HARD); b.pit(3);
  b.bridge(18, 10, s => { s.coins(2, 7, 10); s.mob('rpara', 10, 5); });
  b.pit(2);
  b.fishZone(z0, b.x);
  b.run(12, s => { s.mob('rshell', 8); s.row(3, 9, 'BUB'); });
  b.goal();
}

function level15(b) { // dusk meadow: a cloud rider drops spiky eggs; vine up to a coin heaven
  const heaven = b.room('sky', r => {
    r.hole(29, 34);
    r.at(0, s => { s.coins(4, 11, 22).coins(6, 8, 18).coins(10, 5, 12); s.row(12, 9, 'QQQ'); });
  }, { fallExit: true, spawnTx: 3 });
  b.run(14, s => { s.mob('walk', 10); s.coins(3, 10, 4); });
  b.run(16, s => { s.row(2, 9, 'B').vine(3, 9, { room: heaven, ret: 'h' }).row(4, 9, 'BQB'); s.mob('walk', 12); });
  b.pit(3);
  b.run(20, s => { s.pipe(3, 3).plant(3, 3); s.mob('shell', 9).mob('walk', 13); s.check(0); });
  b.run(16, s => { s.up(2, 4).down(6, 4); s.mob('rshell', 12); });
  b.pit(3);
  b.run(10, s => { s.mob('walk', 6); });
  b.run(22, s => { s.row(2, 9, 'BQBUB'); s.pipe(12, 2).plant(12, 2); s.mob('shell', 17); s.tag(5, 'h'); });
  b.pit(4);
  b.run(12, s => { s.mob('walk', 8); s.hid(5, 9, 'U'); });
  b.run(20, s => { s.pipe(3, 2).pipe(10, 4).plant(10, 4); s.mob('rshell', 7).mob('fast', 16); s.check(0); });
  b.run(16, s => { s.row(3, 9, 'BMBQB'); s.mob('walk', 10).mob('shell', 13); });
  b.pit(3);
  b.run(14, s => { s.up(3, 3).down(6, 3); s.mob('walk', 11); });
  b.goal();
  b.lakituZone(12, b.x - 34);
}

function level16(b) { // autumn: pairs of hammer brothers on brick rows, armoured beetles, cannons
  b.run(14, s => { s.mob('walk', 10); s.coins(3, 10, 4); });
  b.run(22, s => { s.row(4, 9, 'BBBBBBBB'); s.row(6, 5, 'BBQBB'); s.mob('hammer', 6).mob('hammer', 10); });
  b.run(16, s => { s.cannon(6, 2); s.mob('buzzy', 11); s.coins(2, 9, 3); });
  b.pit(3);
  b.run(10, s => { s.mob('buzzy', 6); });
  b.run(24, s => { s.row(3, 9, 'BBBBBB'); s.row(13, 9, 'BBBBBB'); s.row(5, 5, 'BQBB'); s.mob('hammer', 5).mob('hammer', 15); s.check(0); });
  b.run(16, s => { s.up(2, 4).down(6, 4); s.mob('rshell', 12); });
  b.pit(4);
  b.run(10, s => { s.mob('walk', 6); });
  b.run(20, s => { s.cannon(4, 1); s.cannon(10, 3); s.mob('buzzy', 7).mob('buzzy', 15); });
  b.run(26, s => { s.row(3, 9, 'BBBBBBBB'); s.row(15, 9, 'BBBBBB'); s.row(5, 5, 'BPB'); s.mob('hammer', 6).mob('hammer', 17).mob('para', 22); s.check(0); });
  b.run(16, s => { s.pipe(4, 3).plant(4, 3); s.mob('buzzy', 10).mob('rshell', 13); });
  b.goal();
}

function level17(b) { // squid sea: chasing squids, hanging coral, narrow passages
  b.water = true;
  b.run(14, s => { s.mob('fish', 9); s.coins(3, 10, 5); });
  b.run(16, s => { s.up(3, 3).down(6, 3); s.mob('blooper', 11, 7); s.arc(9, 9, 5); });
  b.run(16, s => { s.row(2, 6, 'BPB'); s.mob('fish', 6, 7).mob('blooper', 13, 6); s.coins(3, 9, 4); });
  b.run(18, s => { s.pipe(4, 3).pipe(11, 2); s.mob('walk', 8).mob('fish', 15, 8); s.check(0); });
  b.run(16, s => { s.up(2, 4).down(6, 4); s.coins(3, 6, 5); s.mob('blooper', 12, 6); });
  b.run(14, s => { s.row(1, 9, 'BQBQB'); s.mob('fish', 8, 6).mob('blooper', 11, 8); });
  b.run(20, s => { s.spikes(4, 3).spikes(12, 3); s.arc(3, 8, 6).arc(11, 8, 6); s.mob('blooper', 9, 6); s.mob('fish', 16, 7); s.check(0); });
  b.run(18, s => { s.up(2, 4).down(6, 4); s.pipe(12, 2); s.mob('shell', 15); s.row(3, 5, 'BSB'); });
  b.run(16, s => { s.mob('blooper', 5, 7).mob('blooper', 12, 6); s.coins(2, 10, 10); });
  b.run(20, s => { s.spikes(5, 3).spikes(13, 3); s.arc(4, 8, 6).arc(12, 8, 6); s.mob('fish', 10, 8).mob('blooper', 17, 6); });
  b.goal();
  b.ceiling(0, b.x - 1, 1, TILE.HARD);
  // hanging coral columns from the ceiling
  for (const x of [40, 72, 120, 150]) for (let r = 1; r <= 3; r++) b.set(x, r, TILE.HARD);
}

function level18(b) { // final castle: maze corridors, fire bars, lava, the last bridge and axe
  b.run(12, s => { s.mob('walk', 9); s.coins(3, 10, 4); });
  b.run(20, s => { s.firebar(6, 9, 5, 0.035, 0); s.thwomp(13, 2); s.mob('shell', 17); });
  b.lavaPit(3);
  b.run(12, s => { s.row(1, 9, 'BPB'); s.mob('spiky', 8); });
  // maze 1: the upper corridor is the way
  const m1 = b.x;
  b.run(32, s => { s.up(1, 3); for (let i = 4; i < 30; i++) s.t(i, 9, TILE.HARD); s.coins(6, 7, 20); s.mob('walk', 12, 12).mob('walk', 20, 12); s.check(0); });
  b.mazeLoop(m1 + 26, m1, 'high');
  b.run(10);
  b.lavaPit(4);
  b.run(22, s => { s.firebar(6, 9, 4, -0.03, 0); s.firebar(15, 9, 4, 0.03, 2); s.mob('hammer', 10); s.cannon(19, 1); });
  // maze 2: stay low (the upper road is reached only by spring and loops)
  const m2 = b.x;
  b.run(32, s => { s.spring(2); for (let i = 5; i < 30; i++) s.t(i, 8, TILE.HARD); s.coins(6, 11, 20); s.mob('walk', 14).mob('rshell', 22); s.check(0); });
  b.mazeLoop(m2 + 26, m2, 'low');
  b.run(10);
  b.lavaPit(3);
  b.island(4, 11, null, TILE.HARD); b.lavaPit(3); b.island(4, 10, s => { s.coins(1, 8, 2); }, TILE.HARD); b.lavaPit(3); b.island(5, 11, s => { s.t(2, 7, TILE.QS); }, TILE.HARD);
  b.lavaPit(3);
  b.run(24, s => { s.thwomp(5, 2); s.firebar(12, 9, 5, 0.03, 1); s.mob('hammer', 18); s.check(0); });
  b.run(8);
  b.bossArena({ hp: 6, shots: 100, hammers: true, jump: 55, spd: 1.0, minion: 'shell', bridge: true, final: true });
}

// ---------------------------------------------------------------------------
// Classic run (levels 1-13): follows the order and contents of Super Mario Bros.
// 1-1 ... 4-1 (level type, enemies, hazards, secrets) on original maps.
// ---------------------------------------------------------------------------
function c11(b) { // 1-1: overworld basics, a bonus pipe, hidden 1UP, coin & star bricks, pyramids
  const bonus = b.room('under', r => { r.at(0, s => { s.row(6, 8, 'BBBBBBBBBB'); s.coins(6, 7, 10).coins(7, 11, 9); s.warp(31, 3, { back: true }); }); });
  b.run(16, s => { s.t(11, 9, TILE.QC); s.mob('walk', 14); });
  b.run(12, s => { s.row(1, 9, 'BPBQB'); s.t(3, 5, TILE.QC); s.mob('walk', 8); });
  b.run(32, s => { s.pipe(3, 2); s.pipe(12, 3); s.warp(21, 4, { room: bonus, ret: 'out' }); s.mob('walk', 8).mob('walk', 16).mob('walk', 17); s.hid(27, 9, 'U'); });
  b.pit(2);
  b.run(18, s => { s.row(2, 9, 'BPB'); s.row(6, 5, 'BBBBBBBB'); s.mob('walk', 8, 4).mob('walk', 10, 4); s.check(0); });
  b.pit(3);
  b.run(28, s => { s.row(2, 5, 'BBBQ'); s.t(5, 9, TILE.BRKM); s.row(10, 9, 'BX'); s.mob('walk', 8).mob('walk', 10); s.row(16, 9, 'Q.Q.Q'); s.t(18, 5, TILE.QP); s.mob('shell', 23); });
  b.run(20, s => { s.t(2, 9, TILE.BRK); s.row(5, 5, 'BBB'); s.row(11, 5, 'BQQB'); s.row(12, 9, 'BB'); s.mob('walk', 6).mob('walk', 8).mob('walk', 15).mob('walk', 17); });
  b.run(14, s => { s.up(1, 4).down(7, 4); });
  b.run(6, s => { s.up(2, 4); });
  b.pit(2);
  b.run(10, s => { s.down(0, 4); });
  b.run(24, s => { s.pipe(4, 2, 'out'); s.row(9, 9, 'BBQB'); s.mob('walk', 12).mob('walk', 14); s.pipe(18, 2); });
  b.run(14, s => { s.up(1, 8); for (let k = 0; k < 8; k++) s.t(9, 12 - k, TILE.HARD); });
  b.goal();
}

function c12(b) { // 1-2: underground, brick towers, piranha pipes with a coin room, lifts, roof path to a warp zone
  const coinRoom = b.room('under', r => { r.at(0, s => { s.coins(6, 11, 16).coins(8, 8, 12); s.t(20, 5, TILE.QC); s.warp(31, 3, { back: true }); }); });
  b.run(10, s => { s.mob('walk', 7); });
  b.run(16, s => { s.row(3, 9, 'QQQQQ'); s.mob('walk', 10).mob('walk', 12); });
  b.run(18, s => { s.row(4, 12, 'BBBBB').row(5, 11, 'BBBB').row(6, 10, 'BBB'); s.mob('shell', 6, 9).mob('rshell', 13); s.coins(5, 7, 4); });
  b.run(22, s => { s.row(2, 5, 'BBBBBBBBBBBBBBBBBB'); s.t(9, 5, TILE.BRKU); s.row(4, 9, 'BBBB'); s.row(12, 9, 'BMBB'); s.coins(4, 11, 6); s.mob('walk', 8).mob('walk', 16).mob('walk', 18); s.check(0); });
  b.pit(3);
  b.island(4, 10, s => { s.t(1, 6, TILE.QU); }, TILE.BRK);
  b.pit(3);
  b.run(28, s => { s.warp(3, 3, { room: coinRoom }); s.plant(3, 3); s.pipe(11, 4).plant(11, 4); s.pipe(19, 2).plant(19, 2); s.mob('walk', 8).mob('walk', 15); s.check(0); });
  const liftA = b.x;
  b.pit(9);
  b.at(b.x - 8, s => { s.mover(0, 5, 3, 'y', 6, 0.7); s.mover(4, 5, 3, 'y', 6, 0.7, 6); });
  const liftB = b.x;
  b.run(14, s => { s.mob('walk', 6).mob('walk', 8); s.up(9, 3); });
  b.run(10, s => { for (let x = 0; x < 10; x++) for (let r = 10; r <= 12; r++) s.t(x, r, TILE.HARD); s.warp(5, 2, { exit: true, ret: 'outside', theme: 'grass' }, 10); });
  b.run(1, s => { for (let r = 3; r <= 12; r++) s.t(0, r, TILE.HARD); });
  const pocket = b.x;                                          // warp zone: only reachable over the roof
  b.run(26, s => { s.warp(5, 3, { level: 4 }); s.warp(12, 3, { level: 8 }); s.warp(19, 3, { level: 12 }); s.coins(5, 6, 16); });
  b.run(1, s => { for (let r = 0; r <= 12; r++) s.t(0, r, TILE.HARD); });
  b.run(10, s => { s.pipe(4, 2, 'outside'); });
  b.goal();
  for (let x = 0; x < pocket; x++) if (x < liftA - 1 || x > liftB) b.set(x, 2, TILE.BRK);
}

function c13(b) { // 1-3: treetops, moving lifts with coins above, red shells and red winged shells
  b.run(12, s => { s.coins(4, 10, 3); });
  b.pit(2); b.tree(4, 11, s => { s.mob('walk', 2); });
  b.pit(2); b.tree(8, 8, s => { s.coins(2, 6, 4); s.mob('rshell', 5); });
  b.pit(3); b.tree(5, 10, s => { s.t(2, 6, TILE.QP); });
  b.pit(3); b.tree(6, 12, s => { s.mob('walk', 3); });
  b.pit(6); b.at(b.x - 5, s => { s.mover(0, 10, 3, 'x', 2, 0.6); s.coins(0, 8, 4); });
  b.tree(7, 9, s => { s.mob('rpara', 4, 5); s.check(1); });
  b.pit(3); b.tree(4, 11); b.pit(2); b.tree(6, 8, s => { s.mob('rshell', 3); s.coins(1, 6, 4); });
  b.pit(3); b.tree(9, 10, s => { s.mob('rshell', 3).mob('walk', 6); });
  b.pit(6); b.at(b.x - 5, s => { s.mover(0, 9, 3, 'x', 2, 0.7, 1); s.coins(1, 7, 3); });
  b.tree(5, 10, s => { s.mob('rpara', 2, 5); });
  b.pit(3); b.tree(6, 11, s => { s.mob('rshell', 3); s.coins(1, 9, 4); });
  b.pit(3);
  b.run(16, s => { s.up(3, 4); });
  b.goal();
}

function c14(b) { // 1-4: castle - stairs down to lava, fire bars on platforms, ceiling and floor, hidden coins, bridge & axe
  b.run(10, s => { for (let x = 0; x < 6; x++) for (let r = 10; r <= 12; r++) s.t(x, r, TILE.HARD); s.down(6, 3); });
  b.lavaPit(3);
  b.island(3, 11, s => { s.firebar(1, 11, 4, 0.03, 0); }, TILE.HARD);
  b.lavaPit(3);
  b.run(24, s => { for (let x = 0; x < 24; x++) for (let r = 3; r <= 6; r++) s.t(x, r, TILE.HARD); s.firebar(5, 7, 5, 0.03, 0); s.firebar(12, 7, 5, -0.03, 1.5); s.firebar(19, 7, 5, 0.03, 3); s.check(0); });
  b.lavaPit(3);
  b.run(26, s => { for (let x = 0; x < 26; x++) for (let r = 3; r <= 5; r++) s.t(x, r, TILE.HARD); s.firebar(6, 6, 5, 0.03, 0); s.firebar(13, 12, 4, -0.03, 1); s.firebar(20, 6, 5, 0.03, 2); });
  b.run(16, s => { for (const x of [3, 4, 5, 9, 10, 11]) s.hid(x, 9, 'Q'); s.t(7, 9, TILE.QP); });
  b.run(8);
  b.bossArena({ hp: 3, shots: 170, bridge: true, lift: true });
  b.ceiling(0, b.x - 1, 3, TILE.HARD);
}

function c21(b) { // 2-1: piranha pipes, star, winged shells, vine to coin heaven, bonus pipe, springboard over a brick wall
  const heaven = b.room('sky', r => { r.hole(6, 34); r.at(0, s => { s.mover(5, 10, 3, 'x', 22, 0.8); s.coins(8, 7, 20).coins(12, 5, 12); }); }, { fallExit: true, spawnTx: 2 });
  const bonus = b.room('under', r => { r.at(0, s => { s.coins(5, 11, 20).coins(7, 8, 16); s.warp(31, 3, { back: true }); }); });
  b.run(14, s => { s.row(4, 9, 'BBB'); s.hid(5, 5, 'U'); s.mob('walk', 10); });
  b.run(12, s => { s.mob('shell', 4).mob('walk', 8); s.row(3, 9, 'BQB'); });
  b.pit(2);
  b.run(26, s => { s.pipe(3, 3).plant(3, 3); s.row(7, 9, 'QQQQ'); s.pipe(13, 2).plant(13, 2); s.warp(20, 4, { room: bonus }); s.plant(20, 4); s.mob('walk', 10).mob('walk', 17); s.check(0); });
  b.run(20, s => { s.row(2, 9, 'BXB'); s.row(8, 9, 'QQQ'); s.pipe(14, 3).plant(14, 3); s.mob('walk', 6).mob('walk', 11); });
  b.run(28, s => { s.row(2, 9, 'BBQBQBB'); s.row(4, 5, 'BBB').vine(7, 5, { room: heaven, ret: 'cl' }); s.mob('walk', 10).mob('shell', 16); s.pipe(19, 2).plant(19, 2); });
  b.pit(3);
  b.run(14, s => { s.mob('para', 6).mob('para', 9); s.coins(3, 9, 4); });
  b.run(22, s => { for (const px of [4, 10, 16]) for (let k = 0; k < 3; k++) s.t(px, 12 - k, TILE.HARD); s.mob('para', 7).mob('para', 13); s.tag(19, 'cl'); s.check(0); });
  b.run(16, s => { s.row(2, 9, 'BQBQB'); s.mob('walk', 8).mob('walk', 10).mob('shell', 13); });
  b.run(18, s => { s.spring(6); s.hid(8, 8, 'Q'); for (let r = 6; r <= 12; r++) { s.t(10, r, TILE.BRK); s.t(11, r, TILE.BRK); } });
  b.goal();
}

function c22(b) { // 2-2: underwater - squids, endless fish from the right, whirlpools over trenches
  b.water = true;
  b.run(12, s => { s.coins(4, 9, 4); });
  b.run(16, s => { s.up(3, 3).down(6, 3); s.mob('blooper', 12, 6); s.coins(9, 7, 3); });
  b.run(10, s => { s.row(2, 7, 'HHHH'); s.coins(2, 5, 4); });
  b.pit(4); b.current(b.x - 4, b.x - 1);
  b.run(16, s => { s.row(4, 6, 'HHHHH'); s.mob('blooper', 10, 5); s.coins(5, 4, 4); s.check(0); });
  b.run(18, s => { s.up(2, 4).down(6, 4); s.row(11, 8, 'HHH'); s.mob('blooper', 14, 7); s.coins(3, 6, 5); });
  b.pit(4); b.current(b.x - 4, b.x - 1);
  b.run(14, s => { s.row(3, 5, 'HHHHH'); s.row(8, 9, 'HH'); s.mob('blooper', 11, 6); s.coins(4, 3, 3); });
  b.run(16, s => { s.up(3, 3).down(6, 3); s.mob('blooper', 12, 7); s.check(0); });
  b.pit(4); b.current(b.x - 4, b.x - 1);
  b.run(16, s => { s.row(2, 6, 'HHHH'); s.row(9, 9, 'HHH'); s.mob('blooper', 6, 4); s.coins(9, 7, 3); });
  b.run(12, s => { s.pipe(5, 2).plant(5, 2); });
  b.goal();
  b.ceiling(0, b.x - 1, 1, TILE.HARD);
  b.swimZone(10, b.x - 30);
}

function c23(b) { // 2-3: long bridges and small islands, fish leaping out of the sea the whole way
  b.run(10, s => { s.coins(3, 10, 3); });
  const z0 = b.x;
  b.pit(2); b.bridge(22, 10, s => { s.coins(4, 7, 6); s.coins(14, 6, 5); });
  b.pit(3); b.island(3, 9, s => { s.t(1, 5, TILE.QP); }, TILE.HARD);
  b.pit(3); b.bridge(18, 10, s => { s.arc(3, 7, 6); s.check(1); });
  b.pit(3); b.bridge(12, 11, s => { s.coins(2, 9, 4); });
  b.pit(2); b.island(3, 9, null, TILE.HARD); b.pit(2); b.island(3, 8, s => { s.coins(0, 6, 3); }, TILE.HARD); b.pit(2); b.island(3, 10, null, TILE.HARD);
  b.pit(3); b.bridge(16, 10, s => { s.coins(3, 7, 6); s.check(1); });
  b.pit(2); b.island(3, 9, null, TILE.HARD); b.pit(2); b.island(3, 10, null, TILE.HARD); b.pit(2); b.island(3, 9, s => { s.coins(0, 7, 3); }, TILE.HARD);
  b.pit(3); b.bridge(14, 10);
  b.pit(2);
  b.fishZone(z0, b.x);
  b.run(16, s => { s.up(4, 4); });
  b.goal();
}

function c24(b) { // 2-4: castle - two fire-bar roads, lava bubbles, lifts moving in opposite directions, bridge & axe
  b.run(12, s => { s.up(3, 3); for (let x = 6; x < 12; x++) for (let r = 10; r <= 12; r++) s.t(x, r, TILE.HARD); });
  b.lavaPit(3);
  b.island(4, 10, s => { s.firebar(1, 10, 4, 0.03, 0); }, TILE.HARD);
  b.lavaPit(3);
  b.run(26, s => { for (let x = 2; x < 24; x++) s.t(x, 7, TILE.HARD); s.firebar(7, 7, 4, 0.03, 0); s.firebar(13, 7, 4, -0.03, 1); s.firebar(19, 7, 4, 0.03, 2); s.firebar(12, 12, 4, 0.025, 3); s.coins(4, 5, 3); s.check(0); });
  b.lavaPit(6);
  b.at(b.x - 5, s => { s.mover(0, 8, 2, 'y', 3, 0.6); s.mover(3, 8, 2, 'y', 3, 0.6, 3); });
  b.run(14, s => { s.t(7, 8, TILE.QP); s.coins(3, 10, 3); });
  b.lavaPit(3); b.island(3, 11, null, TILE.HARD); b.lavaPit(3);
  b.run(8);
  b.bossArena({ hp: 3, shots: 150, bridge: true, lift: true });
  b.ceiling(0, b.x - 1, 3, TILE.HARD);
}

function c31(b) { // 3-1: night - winged shells, bonus pipe, bridge with hidden 1UP, hammer brothers + star, vine to coin heaven, stairs
  const heaven = b.room('sky', r => { r.hole(6, 34); r.at(0, s => { s.mover(5, 10, 3, 'x', 22, 0.8); s.coins(7, 7, 22).coins(9, 5, 18).coins(6, 3, 12); }); }, { fallExit: true, spawnTx: 2 });
  const bonus = b.room('under', r => { r.at(0, s => { s.coins(5, 11, 20).coins(8, 8, 12); s.warp(31, 3, { back: true }); }); });
  b.run(14, s => { s.row(4, 9, 'QQ'); s.mob('para', 9).mob('para', 12); });
  b.run(19, s => { s.row(2, 9, 'BQB'); s.warp(10, 3, { room: bonus }); s.mob('walk', 7); });
  b.pit(2);
  b.bridge(12, 10, s => { s.mob('walk', 4).mob('walk', 7); s.hid(6, 6, 'U'); });
  b.pit(2);
  b.run(28, s => { s.row(3, 9, 'BBBBBB'); s.row(14, 9, 'BBBBBB'); s.row(8, 5, 'BSB'); s.mob('hammer', 6).mob('hammer', 17); s.check(0); });
  b.run(22, s => { s.row(2, 9, 'BBBBBBBB'); s.row(4, 5, 'BBB').vine(7, 5, { room: heaven, ret: 'cl' }); s.spring(14); for (let r = 8; r <= 12; r++) s.t(18, r, TILE.BRK); });
  b.run(14, s => { s.mob('walk', 6).mob('walk', 8); s.tag(10, 'cl'); });
  b.pit(3);
  b.run(20, s => { s.up(8, 6); s.mob('shell', 13, 6).mob('shell', 11, 8); });
  b.goal();
}

function c32(b) { // 3-2: a long gauntlet of walkers and shells (use a kicked shell!)
  b.run(16, s => { s.mob('walk', 8).mob('walk', 10).mob('walk', 12); });
  b.run(16, s => { s.mob('shell', 6).mob('shell', 8).mob('shell', 10); s.coins(3, 9, 3); });
  b.pit(3);
  b.run(18, s => { s.row(3, 9, 'BQB'); s.mob('walk', 8).mob('walk', 10).mob('shell', 14); });
  b.run(16, s => { s.mob('shell', 4).mob('walk', 7).mob('walk', 9).mob('shell', 12); s.check(0); });
  b.run(14, s => { s.row(2, 9, 'BXB'); s.t(9, 9, TILE.BRKM); s.mob('walk', 11).mob('walk', 13); });
  b.pit(3);
  b.run(18, s => { s.mob('shell', 4).mob('shell', 6).mob('shell', 8).mob('walk', 12).mob('walk', 14); });
  b.run(16, s => { s.pipe(5, 3).plant(5, 3); s.mob('para', 10).mob('walk', 13); s.check(0); });
  b.run(18, s => { s.mob('shell', 4).mob('shell', 6).mob('walk', 9).mob('walk', 11).mob('shell', 14); s.coins(5, 9, 4); });
  b.pit(3);
  b.run(16, s => { s.mob('walk', 5).mob('walk', 7).mob('shell', 10).mob('shell', 12); });
  b.run(14, s => { s.up(4, 4); });
  b.goal();
}

function c33(b) { // 3-3: night treetops - moving, flimsy and balance lifts
  b.run(10);
  b.pit(2); b.tree(5, 11, s => { s.mob('walk', 2); });
  b.pit(6); b.at(b.x - 5, s => { s.mover(0, 10, 3, 'x', 2, 0.7); s.coins(0, 8, 4); });
  b.tree(6, 10, s => { s.mob('rshell', 3); });
  b.pit(5); b.at(b.x - 4, s => { s.fallLift(0, 10, 3); });
  b.tree(5, 9, s => { s.t(2, 5, TILE.QP); s.check(1); });
  b.pit(7); b.at(b.x - 6, s => { s.balance(0, 4, 9, 2); s.coins(1, 7, 4); });
  b.tree(7, 10, s => { s.mob('rshell', 3).mob('rshell', 5); });
  b.pit(3); b.tree(4, 8, s => { s.mob('rpara', 2, 4); });
  b.pit(6); b.at(b.x - 5, s => { s.mover(0, 9, 3, 'x', 2, 0.7, 1); });
  b.tree(6, 10, s => { s.mob('rshell', 3); s.coins(1, 8, 4); });
  b.pit(7); b.at(b.x - 6, s => { s.balance(0, 4, 10, 2); });
  b.tree(5, 10, s => { s.mob('rshell', 2); });
  b.pit(7); b.at(b.x - 6, s => { s.balance(0, 4, 10, 2); s.coins(0, 8, 6); });
  b.goal();
}

function c34(b) { // 3-4: castle - fire bars in the middle of every platform, lava bubbles between them, bridge & axe
  b.run(8);
  b.lavaPit(2); b.island(4, 11, s => { s.firebar(2, 11, 4, 0.03, 0); }, TILE.HARD);
  b.lavaPit(2); b.island(4, 10, s => { s.firebar(2, 10, 4, -0.03, 1); }, TILE.HARD);
  b.lavaPit(2); b.island(4, 11, s => { s.firebar(2, 11, 4, 0.03, 2); }, TILE.HARD);
  b.lavaPit(3);
  b.run(14, s => { s.row(4, 8, 'QPQ'); s.check(0); });
  b.lavaPit(2); b.island(5, 10, s => { s.firebar(2, 10, 5, 0.03, 0); }, TILE.HARD);
  b.lavaPit(3); b.island(5, 11, s => { s.firebar(2, 11, 5, -0.03, 1); }, TILE.HARD);
  b.lavaPit(3); b.island(5, 10, s => { s.firebar(2, 10, 5, 0.03, 2); }, TILE.HARD);
  b.lavaPit(3);
  b.run(12, s => { s.check(0); });
  b.lavaPit(3); b.island(4, 11, s => { s.firebar(1, 11, 4, 0.03, 0); }, TILE.HARD); b.lavaPit(3); b.island(4, 10, s => { s.firebar(2, 10, 4, -0.03, 1); }, TILE.HARD); b.lavaPit(3); b.island(4, 11, s => { s.firebar(2, 11, 4, 0.03, 2); }, TILE.HARD);
  b.lavaPit(3);
  b.run(8);
  b.bossArena({ hp: 3, shots: 130, bridge: true, lift: true });
  b.ceiling(0, b.x - 1, 3, TILE.HARD);
}

function c41(b) { // 4-1: the cloud rider throws spiky eggs all the way; piranha pipes, stone pillars, bonus pipe, hidden 1UP
  const bonus = b.room('under', r => { r.at(0, s => { s.coins(5, 11, 20).coins(7, 8, 14); s.t(20, 5, TILE.QP); s.warp(31, 3, { back: true }); }); });
  b.run(14, s => { s.pipe(9, 2).plant(9, 2); });
  b.run(16, s => { s.row(3, 9, 'QPQQ'); s.hid(4, 5, 'U'); });
  b.run(21, s => { s.row(4, 9, 'BPB'); s.pipe(12, 3).plant(12, 3); });
  b.pit(3);
  b.run(22, s => { for (const px of [4, 9]) for (let k = 0; k < 3; k++) s.t(px, 12 - k, TILE.HARD); s.warp(14, 3, { room: bonus }); s.coins(3, 8, 4); s.check(0); });
  b.run(20, s => { s.row(2, 9, 'BQBPB'); s.pipe(13, 4).plant(13, 4); });
  b.run(22, s => { for (const px of [3, 8, 13]) for (let k = 0; k < 3; k++) s.t(px, 12 - k, TILE.HARD); s.coins(5, 8, 6); s.check(0); });
  b.run(16, s => { s.row(5, 9, 'BBMBB'); });
  b.run(12, s => { s.up(3, 4); });
  b.goal();
  b.lakituZone(14, b.x - 30);
}

const LEVELS = [
  { id: 1, tag: '1-1', name: 'השדה הירוק', en: 'CLASSIC 1-1', theme: 'grass', time: 400, fn: c11 },
  { id: 2, tag: '1-2', name: 'המחילות', en: 'CLASSIC 1-2', theme: 'under', time: 400, hint: 'יש סוד מעל התקרה...', fn: c12 },
  { id: 3, tag: '1-3', name: 'צמרות העצים', en: 'CLASSIC 1-3', theme: 'tree', time: 300, fn: c13 },
  { id: 4, tag: '1-4', name: 'הטירה הראשונה', en: 'CLASSIC 1-4', theme: 'castle', time: 300, hint: 'בסוף הטירה: געו בגרזן!', fn: c14 },
  { id: 5, tag: '2-1', name: 'גבעות הגבעול', en: 'CLASSIC 2-1', theme: 'grass', time: 400, hint: 'קפיץ: החזיקו קפיצה כשנוחתים עליו', fn: c21 },
  { id: 6, tag: '2-2', name: 'מתחת לים', en: 'CLASSIC 2-2', theme: 'sea', time: 400, hint: 'זהירות מהמערבולות שמושכות למטה', fn: c22 },
  { id: 7, tag: '2-3', name: 'גשרי הדגים', en: 'CLASSIC 2-3', theme: 'coast', time: 300, hint: 'דגים מזנקים מהים', fn: c23 },
  { id: 8, tag: '2-4', name: 'הטירה השנייה', en: 'CLASSIC 2-4', theme: 'castle', time: 300, hint: 'שתי דרכים, שתיהן מסוכנות', fn: c24 },
  { id: 9, tag: '3-1', name: 'לילה של פטישים', en: 'CLASSIC 3-1', theme: 'night', time: 400, hint: 'אחי הפטישים! יש כוכב בקרבת מקום', fn: c31 },
  { id: 10, tag: '3-2', name: 'שדה השלג', en: 'CLASSIC 3-2', theme: 'snow', time: 400, hint: 'בעטו בקונכייה כדי לפנות את הדרך', fn: c32 },
  { id: 11, tag: '3-3', name: 'המאזניים בלילה', en: 'CLASSIC 3-3', theme: 'night', time: 300, hint: 'מעליות מאזניים: אל תישארו עליהן יותר מדי', fn: c33 },
  { id: 12, tag: '3-4', name: 'הטירה השלישית', en: 'CLASSIC 3-4', theme: 'castle', time: 300, hint: 'מוטות אש באמצע כל פלטפורמה', fn: c34 },
  { id: 13, tag: '4-1', name: 'ענן הקוצים', en: 'CLASSIC 4-1', theme: 'grass', time: 400, hint: 'הענן לא מפסיק לזרוק ביצי קוצים', fn: c41 },
  { id: 14, bonus: true, name: 'דיונות המדבר', en: 'DESERT DUNES', theme: 'desert', time: 320, fn: level2 },
  { id: 15, bonus: true, name: 'מערת הגבישים', en: 'CRYSTAL CAVE', theme: 'cave', time: 320, fn: level3 },
  { id: 16, bonus: true, name: 'פסגות השלג', en: 'SNOW PEAKS', theme: 'snow', time: 340, fn: level4 },
  { id: 17, bonus: true, name: 'איי השמיים', en: 'SKY ISLANDS', theme: 'sky', time: 300, fn: level5 },
  { id: 18, bonus: true, name: 'טירת הלבה', en: 'LAVA CASTLE', theme: 'lava', time: 360, fn: level6 },
  { id: 19, bonus: true, name: 'מחילות תת-קרקעיות', en: 'UNDERGROUND', theme: 'under', time: 380, hint: 'לחצו ▼ על צינור ירוק כדי להיכנס לחדר סודי', fn: level7 },
  { id: 20, bonus: true, name: 'מעמקי הים', en: 'DEEP SEA', theme: 'sea', time: 380, hint: 'לחצו על כפתור הקפיצה כדי לשחות למעלה', fn: level8 },
  { id: 21, bonus: true, name: 'יער הלילה', en: 'NIGHT FOREST', theme: 'night', time: 400, hint: 'קפצו על הקפיץ כדי להגיע גבוה. יש צינור סודי!', fn: level9 },
  { id: 22, bonus: true, name: 'הרי הגעש', en: 'VOLCANO', theme: 'ash', time: 400, hint: 'זהירות מכדורי אש שקופצים מהלבה', fn: level10 },
  { id: 23, bonus: true, name: 'מבצר הרעמים', en: 'THUNDER FORT', theme: 'castle', time: 420, hint: 'אבנים מרסקות ומוטות אש. הבוס מחכה בסוף', fn: level11 },
  { id: 24, bonus: true, name: 'מבצר הצללים', en: 'SHADOW FORT', theme: 'void', time: 440, hint: 'הקרב האחרון. בהצלחה!', fn: level12 },
  { id: 25, bonus: true, name: 'צמרות העצים', en: 'TREETOPS', theme: 'tree', time: 360, hint: 'צבים אדומים לא נופלים מהקצה. המעליות נופלות כשעומדים עליהן!', fn: level13 },
  { id: 26, bonus: true, name: 'גשר הדגים', en: 'FISH BRIDGE', theme: 'coast', time: 360, hint: 'דגים מזנקים מהים מעל הגשרים', fn: level14 },
  { id: 27, bonus: true, name: 'שדות השקיעה', en: 'SUNSET FIELDS', theme: 'dusk', time: 380, hint: 'הענן זורק ביצי קוצים. יש גבעול סודי לשמיים!', fn: level15 },
  { id: 28, bonus: true, name: 'אחי הפטישים', en: 'HAMMER BROTHERS', theme: 'autumn', time: 380, hint: 'אחי הפטישים קופצים בין הלבנים. החיפושיות חסינות לכדור!', fn: level16 },
  { id: 29, bonus: true, name: 'ממלכת הדיונונים', en: 'SQUID KINGDOM', theme: 'sea', time: 380, hint: 'במים אי אפשר לדרוך על אויבים. הדיונונים רודפים!', fn: level17 },
  { id: 30, bonus: true, name: 'הטירה האחרונה', en: 'THE LAST CASTLE', theme: 'castle', time: 480, hint: 'מבוך: דרך לא נכונה מחזירה אחורה. בסוף, געו בגרזן!', fn: level18 },
];

function buildLevel(i) {
  const def = LEVELS[i], b = new LB(); def.fn(b); b.finish();
  b.def = def; b.checks.sort((a, c) => a - c);
  // standing surface at a column: lowest solid tile, then up through the contiguous stack.
  // (scanning from the top would land on ceilings / floating blocks and put the hero off screen)
  b.groundRow = (tx) => {
    const solid = r => isSolid((b.map[r] && b.map[r][tx]) || 0);
    let r = ROWS - 1; while (r >= 4 && !solid(r)) r--;
    if (r < 4) return 13;
    while (r - 1 >= 4 && solid(r - 1)) r--;
    return r;
  };
  return b;
}

window.SFB = window.SFB || {};
Object.assign(window.SFB, { T, ROWS, TILE, LEVELS, buildLevel, isSolid });
})();
