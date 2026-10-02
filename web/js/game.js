// Engine: physics, enemies, items, menus, input. Rendering lives in art.js.
(function () {
'use strict';
const S = window.SFB, T = S.T, ROWS = S.ROWS, TILE = S.TILE, LEVELS = S.LEVELS, Art = S.Art, Audio = S.Audio, SFX = Audio.SFX;
const VH = 240, SC = 3;
let VW = 400;
const cv = document.getElementById('game'), ctx = cv.getContext('2d');
const PIX = '"Press Start 2P", ui-monospace, monospace', HEB = 'Rubik, "Segoe UI", Arial, sans-serif';
Art.bind(ctx);

function resize() {
  const a = innerWidth / Math.max(1, innerHeight);
  VW = Math.max(360, Math.min(540, Math.round(VH * a / 2) * 2));
  cv.width = VW * SC; cv.height = VH * SC; Art.setVW(VW);
}
addEventListener('resize', resize); resize();

// ---------- save ----------
const save = { unlocked: 1, best: LEVELS.map(() => 0) };
try { const s = JSON.parse(localStorage.getItem('sfb-save') || 'null'); if (s) { save.unlocked = Math.max(1, Math.min(LEVELS.length, s.unlocked | 0)); (s.best || []).forEach((v, i) => { if (i < save.best.length) save.best[i] = v | 0; }); save.hardUnlocked = !!s.hardUnlocked; save.hardOn = !!(s.hardOn && s.hardUnlocked); } } catch (e) {}
function persist() { try { localStorage.setItem('sfb-save', JSON.stringify(save)); } catch (e) {} }

// ---------- state ----------
const COMBO = [100, 200, 400, 500, 800, 1000, 2000, 4000, 8000];
const BUBBLES = ['בוא הנה!', 'תפסו אותו!', 'אי אפשר לעבור!', 'חה חה חה!', 'אני הכי חזק!', 'זה הטריטוריה שלי!', 'לא תעבור!', 'הגעת למקום הלא נכון!'];
const STOMP_LINES = ['איי!', 'לא הוגן!', 'שוב פעם?!', 'אוף!', 'בום!'];
const NOSTOMP = { spiky: 1, plant: 1, thwomp: 1, pod: 1, shot: 1, spegg: 1, hammerp: 1 };   // touching these hurts, never stompable
const INVULN = { thwomp: 1, pod: 1, shot: 1, hammerp: 1 };                         // not even a star / ball kills them
const TALK = { walk: 1, fast: 1, spiky: 1, shell: 1, fly: 1, rshell: 1, buzzy: 1, para: 1, hammer: 1 };
const SHELLS = { shell: 1, rshell: 1, buzzy: 1 };                 // stomp -> shell -> kick
const NOBALL = { bullet: 1, buzzy: 1 };                           // the soccer ball bounces off these
const KNOCKSTOMP = { bullet: 1, fish: 1, jfish: 1, lakitu: 1, hammer: 1 };
const LONER = { fly: 1, boss: 1, plant: 1, thwomp: 1, pod: 1, fish: 1, bullet: 1, shot: 1, lakitu: 1, hammer: 1, blooper: 1, hammerp: 1, jfish: 1, spegg: 1, rpara: 1 };
const LAVA = { lava: 1, ash: 1, castle: 1, void: 1 };
const DEFS = { walk: [16, 22, 0.45], fast: [16, 22, 0.95], spiky: [16, 22, 0.55], fly: [16, 22, 0.55], boss: [44, 52, 0.8], shell: [16, 26, 0.55], plant: [14, 26, 0], thwomp: [32, 32, 0], pod: [12, 14, 0], fish: [16, 14, 0.7], bullet: [16, 12, 1.7], shot: [14, 10, 2.3], rshell: [16, 26, 0.55], buzzy: [16, 18, 0.5], para: [16, 26, 0.55], rpara: [16, 26, 0], lakitu: [20, 26, 0], spegg: [12, 12, 0], hammer: [16, 27, 0], hammerp: [12, 12, 0], blooper: [16, 20, 0], jfish: [16, 14, 0] };
let state = 'title', frame = 0, score = 0, coinCount = 0, lives = 3, time = 300, timeTick = 0;
let lvl = 0, L = null, map = null, cols = 0, theme = 'grass';
let enemies = [], items = [], parts = [], pops = [], bumps = [], coins = [], balls = [], plats = [], weather = [], springs = [], fbars = [], cannons = [], bubs = [];
let vines = [], fws = [], lakCD = 0, fishCD = 60, axeT = 0, rescueT = 0, fwN = 0;
let hidMap = {}, mcoin = {}, water = false, inRoom = false, roomIdx = -1, mainSnap = null, curWarp = null, warpSt = null, fade = 0, areaWarps = [];
let cam = 0, P = null, jumpBuf = 0, coyote = 0, dieT = 0, growT = 0, deathMsg = '', introT = 0, levelScore0 = 0;
let flagY = 0, flagState = null, flagT = 0, castleFlag = 0, bubbleCD = 120, checkTx = -1, shake = 0;
let boss = null, bossActive = false, bossDead = false, bannerT = 0, bannerText = '', menuSel = 0, prevState = 'play';
const keys = { left: false, right: false, jump: false, run: false, down: false };
const dbg = { god: false };
const UI = { buttons: [] };

function lvlScore() { return score - levelScore0; }

function mkEnemy(k, x, y) {
  const d = DEFS[k] || DEFS.walk;
  return { k, x, y, w: d[0], h: d[1], vx: -d[2], spd: d[2], vy: 0, state: 'walk', t: 0, active: false, dead: false, bubble: null, onGround: false, baseY: y, ph: 0, shell: 0, kg: 0, dir: -1 };
}
function makeEnemies(list, startTx) {
  return list.filter(e => e.tx > startTx + 8).map(e0 => {
    const e = save.hardOn && e0.k === 'walk' ? Object.assign({}, e0, { k: 'buzzy' }) : e0;   // hard mode: beetles instead of walkers
    const d = DEFS[e.k] || DEFS.walk;
    let x = e.tx * T, y = (e.row + 1) * T - d[1];
    if (e.k === 'thwomp') y = e.row * T;
    if (e.k === 'pod') { x = e.tx * T + 2; y = VH + 20; }
    const m = mkEnemy(e.k, x, y); m.t = (e.tx * 7) % 40; m.ph = e.tx;
    if (e.k === 'plant') { m.y = y + 26; m.visible = false; }
    if (save.hardOn) { m.vx *= 1.3; m.spd *= 1.3; }          // baseY = fully raised, hidden = 26px lower (inside the pipe)
    return m;
  });
}
// load one area (the main level or a bonus room) into the live state variables
function loadArea(a, startTx, th) {
  map = a.map; cols = a.cols; theme = th;
  enemies = makeEnemies(a.en, startTx);
  coins = a.coins.filter(([x]) => x > startTx).map(([x, r]) => ({ x: x * T + 3, y: r * T + 1, w: 10, h: 14, taken: false }));
  plats = a.plats.map(p => ({ bx: p.x, by: p.y, x: p.x, y: p.y, w: p.w, axis: p.axis, range: p.range || 0, speed: p.speed || 0, off: Math.min(p.range || 0, (p.phase || 0) * T), dir: 1, dx: 0, dy: 0, type: p.type || 'move', pair: p.pair, vy: 0, t: 0, trig: false, broken: false }));
  vines = [];
  springs = a.springs.map(s => ({ x: s.tx * T, y: s.row * T, t: 0 }));
  fbars = a.fbars.map(f => ({ tx: f.tx, row: f.row, len: f.len, speed: f.speed, a: f.ang }));
  hidMap = Object.assign({}, a.hidden); mcoin = {}; water = !!a.water; areaWarps = a.warps;
  cannons = []; for (let ty = 0; ty < ROWS; ty++) for (let tx = 0; tx < cols; tx++) if (map[ty][tx] === TILE.CANNON) cannons.push({ tx, ty, cd: 70 + (tx * 17) % 90 });
  items = []; parts = []; pops = []; bumps = []; balls = []; bubs = [];
}
function pack() { return { vines, map, cols, theme, enemies, items, coins, plats, springs, fbars, cannons, bubs, hidMap, mcoin, water, areaWarps, bumps, parts, pops, balls }; }
function unpack(o) { ({ vines, map, cols, theme, enemies, items, coins, plats, springs, fbars, cannons, bubs, hidMap, mcoin, water, areaWarps, bumps, parts, pops, balls } = o); }

function seedWeather(th) {
  weather = [];
  const wn = { snow: 70, lava: 34, desert: 22, ash: 46, night: 22, sea: 34, void: 26 }[th] || 0;
  for (let i = 0; i < wn; i++) {
    const r = Math.random(); let vx = -0.2 - r * 0.5, vy = 0.4 + Math.random() * 0.7;
    if (th === 'desert') vx = -1.4 - r;
    else if (th === 'lava' || th === 'void') vy = -0.3 - Math.random() * 0.6;
    else if (th === 'sea') { vx = (r - 0.5) * 0.3; vy = -0.3 - Math.random() * 0.5; }
    else if (th === 'night') { vx = (r - 0.5) * 0.6; vy = (Math.random() - 0.5) * 0.4; }
    else if (th === 'ash') { vx = -0.15 - r * 0.2; vy = 0.3 + Math.random() * 0.5; }
    weather.push({ x: Math.random() * 600, y: Math.random() * VH, vx, vy, r: Math.random() < 0.3 ? 2 : 1 });
  }
}

function startLevel(i, keepCheck) {
  lvl = i; L = S.buildLevel(i); inRoom = false; roomIdx = -1; mainSnap = null; curWarp = null; warpSt = null; fade = 0;
  if (!keepCheck) checkTx = -1;
  const startTx = checkTx >= 0 ? checkTx : 3;
  loadArea(L, startTx, L.def.theme);
  boss = null; bossActive = false; bossDead = false;
  if (L.arena) {
    const a = L.arena; boss = mkEnemy('boss', a.bossTx * T, 13 * T - 52);
    Object.assign(boss, { hp: a.hp, hpMax: a.hp, inv: 0, jt: 0, sc: 0, shotN: 0, jump: a.jump, spd: a.spd, vx: -a.spd });
    enemies.push(boss);
  }
  P = { x: startTx * T, y: 0, w: 12, h: 28, vx: 0, vy: 0, big: false, fire: false, star: 0, face: 1, onGround: true, inv: 0, dist: 0, combo: 0, hidden: false, dead: false, ride: null, onIce: false, kickCD: 0, swimCD: 0, warping: false };
  P.y = L.groundRow(startTx) * T - P.h;
  cam = Math.max(0, P.x - 80);
  time = L.def.time - (save.hardOn ? 60 : 0); timeTick = 0; lakCD = 0; fishCD = 60; fws = []; fwN = 0; jumpBuf = 0; coyote = 0;
  flagY = 3 * T + 8; flagState = null; castleFlag = 0; bubbleCD = 120; shake = 0;
  seedWeather(theme);
  state = 'intro'; introT = L.def.hint ? 170 : 110;
  Audio.music.track = theme; Audio.music.tempoMul = 1;
}
function newRun(i) { score = 0; coinCount = 0; lives = 3; levelScore0 = 0; checkTx = -1; startLevel(i); SFX.go(); }
function retryLevel() { score = levelScore0; lives = 3; coinCount = 0; checkTx = -1; startLevel(lvl); SFX.go(); }
function nextLevel() { levelScore0 = score; startLevel(lvl + 1); SFX.go(); }

// ---------- warp pipes ----------
function warpUnderFeet() {
  if (!P.onGround) return null;
  for (const w of areaWarps) if (Math.abs(P.y + P.h - w.top * T) <= 2 && P.x + P.w / 2 > w.tx * T + 3 && P.x + P.w / 2 < w.tx * T + 29) return w;
  return null;
}
function startWarp(w) {
  warpSt = { w, ph: 'sink', t: 0, rise: false, riseTop: 0 }; state = 'warp';
  P.vx = 0; P.vy = 0; P.ride = null; P.x = w.tx * T + 16 - P.w / 2; P.warping = true; SFX.warp();
}
// swaps the live area while the screen is black; returns false when the warp left the level
function curRoom() { return L.rooms[roomIdx]; }
function startClimb(v) {
  warpSt = { w: v.def, ph: 'climb', t: 0, rise: false }; state = 'warp';
  P.vx = 0; P.vy = 0; P.ride = null; P.x = v.x + 8 - P.w / 2; P.warping = false; SFX.vine();
}
function startSkyReturn() { warpSt = { w: curWarp, ph: 'out', t: 0, rise: false }; state = 'warp'; P.vy = 0; }
function startLoop(lp) { warpSt = { w: null, loop: lp, ph: 'out', t: 0, rise: false }; state = 'warp'; P.vx = 0; SFX.warp(); }
// swaps the live area while the screen is black; returns false when the warp left the level
function switchArea() {
  const w = warpSt.w;
  if (warpSt.loop) {                                   // castle maze: wrong road, back to its start
    const lp = warpSt.loop, tx = lp.back + 2;
    P.x = tx * T; P.y = L.groundRow(tx) * T - P.h; P.vx = P.vy = 0; P.onGround = true; lp.hit = false;
    cam = Math.max(0, Math.min(P.x - VW * 0.42, camLimit())); bannerText = 'טעית בדרך!'; bannerT = 110;
    return true;
  }
  if (w.level != null) {
    if (w.level < LEVELS.length) { levelScore0 = score; save.unlocked = Math.max(save.unlocked, w.level + 1); persist(); checkTx = -1; startLevel(w.level); }
    return false;
  }
  if (!inRoom) {
    mainSnap = pack(); curWarp = w; roomIdx = w.room; const R = L.rooms[w.room];
    if (R.rt) unpack(R.rt); else loadArea(R, -999, R.theme);
    theme = R.theme; inRoom = true; cam = 0; weather = [];
    P.x = R.spawnTx * T; P.y = w.viaVine ? 13 * T - P.h : -34; P.vx = 0; P.vy = 0; P.warping = false; P.ride = null; P.onGround = !!w.viaVine; warpSt.rise = false;
  } else {
    L.rooms[roomIdx].rt = pack(); unpack(mainSnap); theme = L.def.theme; inRoom = false; seedWeather(theme);
    if (curWarp.viaVine) {                             // dropped back from the sky further ahead
      P.x = curWarp.retTx * T; P.y = -34; P.vx = 0; P.vy = 0; P.warping = false; warpSt.rise = false;
      cam = Math.max(0, Math.min(P.x - VW * 0.42, cols * T - VW));
      return true;
    }
    const tx = curWarp.retTx, top = curWarp.retTop;
    P.x = tx * T + 16 - P.w / 2; P.y = top * T; P.vx = 0; P.vy = 0; P.warping = true;
    cam = Math.max(0, Math.min(P.x - VW * 0.42, cols * T - VW)); warpSt.rise = true; warpSt.riseTop = top * T;
  }
  return true;
}
function updateWarp() {
  const W = warpSt;
  if (W.ph === 'sink') { P.y += 0.9; if (++W.t >= 34) { W.ph = 'out'; W.t = 0; } }
  else if (W.ph === 'climb') { P.y -= 1.3; P.dist += 0.5; if (P.y + P.h < 6) { W.ph = 'out'; W.t = 0; } }
  else if (W.ph === 'out') { fade = Math.min(1, ++W.t / 16); if (W.t >= 16) { if (!switchArea()) return; W.ph = 'in'; W.t = 0; } }
  else if (W.ph === 'in') { fade = Math.max(0, 1 - ++W.t / 16); if (W.t >= 16) { fade = 0; W.ph = W.rise ? 'rise' : 'done'; } }
  else if (W.ph === 'rise') { P.y -= 0.9; if (P.y + P.h <= W.riseTop) { P.y = W.riseTop - P.h; P.onGround = true; W.ph = 'done'; } }
  if (W.ph === 'done') { P.warping = false; warpSt = null; state = 'play'; }
}

function addScore(n, x, y) { score += n; pops.push({ x, y, text: String(n), t: 45 }); }
function addCoin() {
  coinCount++; score += 200; SFX.coin();
  if (coinCount >= 100) { coinCount -= 100; lives++; SFX.oneup(); pops.push({ x: P.x, y: P.y - 8, text: '1UP', t: 60 }); }
}
function buzz(ms) { try { if (navigator.vibrate) navigator.vibrate(ms); } catch (e) {} }

// ---------- tiles & collisions ----------
const isSolid = S.isSolid;
function solid(tx, ty) {
  if (ty < 0 || ty >= ROWS) return false;
  if (tx < 0 || tx >= cols) return true;
  return isSolid(map[ty][tx]);
}
function collide(o, axis) {
  const x0 = Math.floor(o.x / T), x1 = Math.floor((o.x + o.w - 0.01) / T);
  const y0 = Math.floor(o.y / T), y1 = Math.floor((o.y + o.h - 0.01) / T);
  const res = { hit: false, heads: [], row: y0 };
  for (let ty = y0; ty <= y1; ty++) for (let tx = x0; tx <= x1; tx++) {
    if (!solid(tx, ty)) continue;
    res.hit = true;
    if (axis === 'x') { if (o.vx > 0) o.x = tx * T - o.w; else if (o.vx < 0) o.x = (tx + 1) * T; }
    else if (o.vy > 0) { o.y = ty * T - o.h; o.onGround = true; o.vy = 0; }
    else if (o.vy < 0) { o.y = (ty + 1) * T; res.heads.push(tx); }
  }
  return res;
}
const overlap = (a, b) => a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;

// ---------- player ----------
function setBig(b) { if (b === P.big) return; const dh = 36 - 28; P.big = b; P.h = b ? 36 : 28; P.y += b ? -dh : dh; }
function uncrouch() { if (P.crouch) { P.crouch = false; P.y -= 14; P.h = 36; } }
function headFree() { for (const x of [P.x + 1, P.x + P.w - 1]) for (const y of [P.y - 13, P.y - 1]) if (solid(Math.floor(x / T), Math.floor(y / T))) return false; return true; }
function hurt(spike) {
  if (dbg.god) return;
  uncrouch();
  if (P.big) { setBig(false); P.fire = false; P.inv = 120; SFX.hurt(); buzz(40); if (spike) P.vy = -5; }
  else die(false);
}
function die(fell) {
  if (state === 'dying') return;
  state = 'dying'; P.dead = true; P.vx = 0; P.vy = fell ? 0 : -6.5; P.fell = fell; dieT = 170;
  deathMsg = time <= 0 ? 'נגמר הזמן!' : fell ? 'נפלת!' : 'אופס!';
  Audio.music.on = false; SFX.die(); buzz(120);
}
function grabFlag() {
  state = 'flag'; flagState = 'slide';
  P.vx = 0; P.vy = 0; P.x = L.poleTx * T + 7 - P.w + 1; P.star = 0; P.ride = null;
  if (P.y + P.h > 192) P.y = 192 - P.h;
  const dy = 192 - (P.y + P.h);
  addScore(dy >= 128 ? 5000 : dy >= 96 ? 2000 : dy >= 64 ? 800 : dy >= 32 ? 400 : 100, P.x + 16, P.y);
  fwN = { 1: 1, 3: 3, 6: 6 }[time % 10] || 0;          // fireworks when the clock's last digit is 1, 3 or 6
  uncrouch();
  SFX.flag();
}
function kick() {
  if (!P.fire || P.kickCD > 0 || balls.length >= 2) return;
  P.kickCD = 14; SFX.kick();
  balls.push({ x: P.x + (P.face > 0 ? P.w : -8), y: P.y + P.h - 10, w: 8, h: 8, vx: 3.4 * P.face, vy: 0, t: 150, rot: 0 });
}
function spawnItem(type, tx, ty) {
  items.push({ type, x: tx * T + 1, y: ty * T, w: 14, h: 14, vx: 0, vy: 0, state: 'rise', t: 32, onGround: false });
  SFX.sprout();
}
function revealHidden(tx, ty) {
  const kind = hidMap[tx + ',' + ty] || 'U';
  map[ty][tx] = TILE.USED; bumps.push({ tx, ty, t: 10 });
  if (kind === 'Q') { items.push({ type: 'coinpop', x: tx * T, y: ty * T - 16, vy: -5, t: 26 }); addCoin(); }
  else spawnItem(kind === 'S' ? 'star' : kind === 'P' ? (P.big ? 'ball' : 'bolt') : 'heart', tx, ty);
}
function bumpTile(tx, ty) {
  const t = map[ty][tx];
  const used = () => { map[ty][tx] = TILE.USED; bumps.push({ tx, ty, t: 10 }); };
  if (t === TILE.QC) { used(); items.push({ type: 'coinpop', x: tx * T, y: ty * T - 16, vy: -5, t: 26 }); addCoin(); }
  else if (t === TILE.QP) { used(); spawnItem(P.big ? 'ball' : 'bolt', tx, ty); }
  else if (t === TILE.QS) { used(); spawnItem('star', tx, ty); }
  else if (t === TILE.QU) { used(); spawnItem('heart', tx, ty); }
  else if (t === TILE.QV) { used(); vines.push({ x: tx * T, top: ty * T, bottom: ty * T, def: Object.assign({ viaVine: true }, L.vines[tx + ',' + ty] || {}) }); SFX.vine(); }
  else if (t === TILE.BRKM) {
    const key = tx + ',' + ty; mcoin[key] = (mcoin[key] === undefined ? 6 : mcoin[key]) - 1;
    items.push({ type: 'coinpop', x: tx * T, y: ty * T - 16, vy: -5, t: 26 }); addCoin(); bumps.push({ tx, ty, t: 10 });
    if (mcoin[key] <= 0) map[ty][tx] = TILE.USED;
  }
  else if (t === TILE.BRK) {
    if (P.big) {
      map[ty][tx] = 0; score += 50; SFX.brk();
      for (const [vx, vy, ox, oy] of [[-1.2, -5, 0, 0], [1.2, -5, 8, 0], [-1, -3, 0, 8], [1, -3, 8, 8]]) parts.push({ x: tx * T + ox, y: ty * T + oy, vx, vy, t: 80 });
    } else { bumps.push({ tx, ty, t: 10 }); SFX.bump(); }
  } else SFX.bump();
  for (const e of enemies) {
    if (e.state !== 'walk' || !e.active || e.k === 'boss' || INVULN[e.k] || LONER[e.k]) continue;
    if (e.y + e.h >= ty * T - 3 && e.y + e.h <= ty * T + 2 && e.x + e.w > tx * T && e.x < (tx + 1) * T) knock(e);
  }
  for (const it of items) if (it.type !== 'coinpop' && it.state === 'move' && Math.abs(it.y + it.h - ty * T) < 3 && it.x + it.w > tx * T && it.x < (tx + 1) * T) it.vy = -4;
}
function knock(e) { e.state = 'knock'; e.vy = -4; e.vx = e.x > P.x ? 1 : -1; addScore(100, e.x, e.y); SFX.stomp(); }

function groundTileUnder() {
  const ty = Math.floor((P.y + P.h + 1) / T);
  for (const tx of [Math.floor((P.x + 2) / T), Math.floor((P.x + P.w - 2) / T)]) if (ty < ROWS && tx >= 0 && tx < cols && map[ty][tx] === TILE.ICE) return true;
  return false;
}
function hazards() {
  if (P.star > 0 || P.inv > 0 || dbg.god) return;
  const x0 = Math.floor((P.x + 2) / T), x1 = Math.floor((P.x + P.w - 3) / T), y0 = Math.floor((P.y + 4) / T), y1 = Math.floor((P.y + P.h - 1) / T);
  for (let ty = Math.max(0, y0); ty <= Math.min(ROWS - 1, y1); ty++) for (let tx = Math.max(0, x0); tx <= Math.min(cols - 1, x1); tx++) {
    if (map[ty][tx] !== TILE.SPK) continue;
    const r = { x: tx * T + 3, y: ty * T + 6, w: 10, h: 10 };
    if (overlap({ x: P.x + 2, y: P.y + 2, w: P.w - 4, h: P.h - 3 }, r)) { hurt(true); return; }
  }
  const r = { x: P.x + 2, y: P.y + 3, w: P.w - 4, h: P.h - 5 };
  for (const f of fbars) {
    const px = f.tx * T + 8, py = f.row * T + 8;
    if (px < cam - 90 || px > cam + VW + 90) continue;
    for (let i = 0; i < f.len; i++) {
      const d = (i + 0.6) * 8, bx = px + Math.cos(f.a) * d, by = py + Math.sin(f.a) * d;
      const cx = Math.max(r.x, Math.min(bx, r.x + r.w)), cy = Math.max(r.y, Math.min(by, r.y + r.h));
      if ((cx - bx) * (cx - bx) + (cy - by) * (cy - by) < 14) { hurt(true); return; }
    }
  }
}

function updatePlatforms() {
  for (const p of plats) {
    const ox = p.x, oy = p.y;
    if (p.type === 'fall') {                           // drops a moment after you step on it
      if (P.ride === p) p.trig = true;
      if (p.trig && ++p.t > 14) { p.vy = Math.min(p.vy + 0.25, 5); p.y += p.vy; }
    } else if (p.type === 'bal') {                     // see-saw pair on a pulley
      const q = plats[p.pair];
      if (p.broken) { p.vy = Math.min(p.vy + 0.3, 6); p.y += p.vy; }
      else if (P.ride === p && q) { p.y += 1.1; q.y -= 1.1; if (q.y < 3 * T) { p.broken = q.broken = true; p.vy = q.vy = 0; SFX.collapse(); } }
    } else {
      p.off += p.dir * p.speed; if (p.off >= p.range) { p.off = p.range; p.dir = -1; } else if (p.off <= 0) { p.off = 0; p.dir = 1; }
      if (p.axis === 'x') p.x = p.bx + p.off; else p.y = p.by + p.off;
    }
    p.dx = p.x - ox; p.dy = p.y - oy;
  }
}

// hidden block: becomes real the moment the head would hit it from below
function checkHidden() {
  if (P.vy >= 0) return;
  const ty = Math.floor((P.y + P.vy) / T);
  if (ty < 0 || ty >= ROWS || P.y < (ty + 1) * T - 0.01) return;
  for (const tx of [Math.floor((P.x + 2) / T), Math.floor((P.x + P.w - 3) / T)]) if (map[ty][tx] === TILE.HID) revealHidden(tx, ty);
}

function updatePlayer() {
  if (++timeTick >= 30) {
    timeTick = 0; time--;
    if (time === 100) { SFX.hurry(); Audio.music.tempoMul = 0.78; }
    if (time <= 0) { time = 0; die(false); return; }
  }
  if (P.kickCD > 0) P.kickCD--;
  if (P.swimCD > 0) P.swimCD--;
  if (P.star > 0 && --P.star === 0) Audio.music.tempoMul = time <= 100 ? 0.78 : 1;
  if (P.ride) { P.x += P.ride.dx; P.y += P.ride.dy; }
  P.onIce = P.onGround && groundTileUnder();
  if (keys.down && P.big && P.onGround && !water && !P.crouch && !warpUnderFeet()) { P.crouch = true; P.y += 14; P.h = 22; }
  else if (P.crouch && !keys.down && headFree()) uncrouch();
  const max = water ? (keys.run ? 1.9 : 1.25) : (keys.run ? 2.5 : 1.45);
  const dir = P.crouch && P.onGround ? 0 : (keys.right ? 1 : 0) - (keys.left ? 1 : 0);
  if (dir) {
    P.face = dir;
    const acc = water ? 0.07 : (P.onGround ? 0.08 : 0.06) * (P.onIce ? 0.32 : 1);
    const skid = P.onGround && !P.onIce && P.vx * dir < 0 ? 0.14 : 0;
    P.vx += dir * (acc + skid);
    if (P.vx * dir > max) P.vx = dir * Math.max(max, Math.abs(P.vx) - acc - 0.04);
  } else if (P.onGround) {
    P.vx *= P.onIce ? 0.975 : 0.84; if (Math.abs(P.vx) < 0.06) P.vx = 0;
  } else if (water) P.vx *= 0.985;
  if (jumpBuf > 0) jumpBuf--;
  if (water) {
    // swimming: every press of the jump button is one stroke
    if (jumpBuf > 0 && P.swimCD <= 0) {
      P.vy = -2.8; P.swimCD = 9; jumpBuf = 0; P.onGround = false; P.ride = null; SFX.swim();
      for (let i = 0; i < 2; i++) bubs.push({ x: P.x + P.w / 2 + (i ? 4 : -4), y: P.y + 6, vy: -0.5 - Math.random() * 0.4, t: 50 });
    }
    P.vy += 0.13; if (P.vy > 1.7) P.vy = 1.7;
  } else {
    if (P.onGround) coyote = 6; else if (coyote > 0) coyote--;
    if (jumpBuf > 0 && coyote > 0) { P.vy = -(6.4 + Math.abs(P.vx) * 0.25); jumpBuf = 0; coyote = 0; P.onGround = false; P.ride = null; SFX.jump(); }
    P.vy += (keys.jump && P.vy < 0) ? 0.27 : 0.7;
    if (P.vy > 6.5) P.vy = 6.5;
  }

  P.x += P.vx;
  if (P.x < cam) { P.x = cam; if (P.vx < 0) P.vx = 0; }
  if (collide(P, 'x').hit) P.vx = 0;
  const prevBottom = P.y + P.h;
  checkHidden();
  P.y += P.vy; P.onGround = false;
  const cy = collide(P, 'y');
  if (cy.heads.length) {
    const c = (P.x + P.w / 2) / T; let pick = cy.heads[0];
    for (const tx of cy.heads) if (Math.abs(tx + 0.5 - c) < Math.abs(pick + 0.5 - c)) pick = tx;
    bumpTile(pick, cy.row); P.vy = 1;
  }
  // one-way moving platforms
  P.ride = null;
  if (P.vy >= 0 && !P.onGround) {
    for (const p of plats) {
      if (P.x + P.w > p.x + 1 && P.x < p.x + p.w - 1 && P.y + P.h >= p.y && prevBottom <= p.y + Math.max(2, p.dy) + 2) {
        P.y = p.y - P.h; P.vy = 0; P.onGround = true; P.ride = p; break;
      }
    }
  }
  // springboards: land on one from above (holding jump = much higher)
  if (P.vy >= 0 && !P.onGround) {
    for (const sp of springs) {
      if (P.x + P.w > sp.x + 2 && P.x < sp.x + 14 && P.y + P.h >= sp.y + 3 && prevBottom <= sp.y + 7) {
        P.y = sp.y + 4 - P.h; P.vy = keys.jump ? -9.4 : -6.8; P.onGround = false; sp.t = 12; P.ride = null; SFX.spring(); break;
      }
    }
  }
  if (P.onGround) P.combo = 0;
  P.dist += Math.abs(P.vx);
  if (P.inv > 0) P.inv--;
  hazards();
  if (state !== 'play') return;
  if (P.y > VH + 8) { if (inRoom && curRoom().fallExit) { startSkyReturn(); return; } die(true); return; }
  if (keys.down && P.onGround) { const w = warpUnderFeet(); if (w) { uncrouch(); startWarp(w); return; } }
  for (const v of vines) if (P.x + P.w > v.x + 3 && P.x < v.x + 13 && P.y < v.bottom && P.y + P.h > v.top + 6) { uncrouch(); startClimb(v); return; }
  if (inRoom) return;
  if (L.arena && L.arena.axe) { const a = L.arena.axe; if (overlap(P, { x: a.tx * T + 3, y: a.row * T - 4, w: 12, h: 20 })) { startAxe(); return; } }
  for (const lp of L.loops) {
    if (P.x < lp.tx * T - 64) lp.hit = false;
    if (!lp.hit && P.x > lp.tx * T) { lp.hit = true; const feet = P.y + P.h; if (lp.need === 'high' ? feet > 10 * T : feet < 11 * T) { startLoop(lp); return; } }
  }
  // checkpoint
  for (const c of L.checks) if (c > checkTx && P.x >= c * T) { checkTx = c; pops.push({ x: c * T, y: 13 * T - 40, text: 'CHECKPOINT', t: 70 }); SFX.coin(); }
  // boss arena trigger
  if (L.arena && !bossActive && !bossDead && P.x > L.arena.trigger * T) {
    bossActive = true; boss.active = true;
    for (let r = 5; r <= 12; r++) map[r][L.arena.x0] = TILE.GATE;
    Audio.music.track = 'boss'; bannerText = 'הבוס הגדול!'; bannerT = 120; SFX.boss(); shake = 14;
  }
  if (L.poleTx > 0 && P.x + P.w >= L.poleTx * T - 2) grabFlag();
}

// ---------- enemies ----------
function killBoss() {
  bossDead = true; boss.state = 'knock'; boss.vy = -6; boss.vx = 0.6; addScore(5000, boss.x, boss.y - 10); SFX.boss(); shake = 24; buzz(200);
  for (let r = 5; r <= 12; r++) { const x = L.arena.x1; if (map[r][x] === TILE.GATE) { map[r][x] = 0; for (let k = 0; k < 2; k++) parts.push({ x: x * T + k * 8, y: r * T, vx: Math.random() * 2 - 1, vy: -Math.random() * 3, t: 80 }); } }
  for (const e of enemies) if (e !== boss && e.state === 'walk') knock(e);
  Audio.music.track = theme; bannerText = 'ניצחת!'; bannerT = 120;
}
function hitBoss() {
  if (boss.inv > 0) return false;
  boss.hp--; boss.inv = 90; SFX.boss(); shake = 10; buzz(60);
  if (boss.hp <= 0) killBoss();
  else {
    boss.spd = L.arena.spd + (boss.hpMax - boss.hp) * 0.25; boss.vx = Math.sign(boss.vx || 1) * boss.spd;
    const m = mkEnemy(L.arena.minion || 'walk', cam + VW - 24, 13 * T - DEFS[L.arena.minion || 'walk'][1]); m.active = true; m.vx = -0.7; enemies.push(m);
  }
  return true;
}
function fireShot(b) {
  const dir = P.x < b.x ? -1 : 1, low = (b.shotN++ % 2) === 0;
  const s = mkEnemy('shot', dir < 0 ? b.x - 14 : b.x + b.w, low ? 13 * T - 10 : 13 * T - 60); s.vx = dir * 2.3; s.active = true; enemies.push(s); SFX.fire();
}
function groundAhead(e) {
  const tx = Math.floor((e.vx > 0 ? e.x + e.w + 2 : e.x - 2) / T), ty = Math.floor((e.y + e.h + 2) / T);
  return solid(tx, ty);
}
function updatePlant(e) {
  const p = e.t % 230;
  const near = Math.abs((P.x + P.w / 2) - (e.x + e.w / 2)) < 34 && Math.abs(P.y + P.h - (e.baseY + 26)) < 44;
  if (p >= 150 && near) e.t--;                       // stays hidden while the hero stands close
  const q = e.t % 230;
  e.y = q < 30 ? e.baseY + 26 * (1 - q / 30) : q < 120 ? e.baseY : q < 150 ? e.baseY + 26 * ((q - 120) / 30) : e.baseY + 26;
  e.visible = e.y < e.baseY + 14;
}
function updateThwomp(e) {
  if (e.mode === undefined) { e.mode = 0; e.rt = 0; }
  if (e.mode === 0) { if (Math.abs((P.x + P.w / 2) - (e.x + 16)) < 30 && P.y + P.h > e.y + 32) { e.mode = 1; e.vy = 0; } }
  else if (e.mode === 1) {
    e.vy = Math.min(e.vy + 0.9, 10); e.y += e.vy; e.onGround = false; collide(e, 'y');
    if (e.onGround) { e.mode = 2; e.rt = 50; shake = 8; SFX.boss(); }
    else if (e.y > VH) e.dead = true;
  } else if (e.mode === 2) { if (--e.rt <= 0) e.mode = 3; }
  else { e.y -= 1.2; if (e.y <= e.baseY) { e.y = e.baseY; e.mode = 0; } }
}
function updatePod(e) {
  if (e.wait === undefined) { e.wait = (e.ph * 37) % 90 + 30; e.y = VH + 20; e.vy = 0; }
  if (e.wait > 0) { if (--e.wait === 0) e.vy = -6.2; return; }
  e.vy += 0.25; e.y += e.vy;
  if (e.y > VH + 20) { e.y = VH + 20; e.vy = 0; e.wait = 70 + ((frame * 7) % 70); }
}
function comboHit(e) {
  if (P.combo >= COMBO.length) { lives++; SFX.oneup(); pops.push({ x: e.x, y: e.y - 8, text: '1UP', t: 60 }); }
  else addScore(COMBO[P.combo], e.x, e.y - 4);
  P.combo++;
}
function updateLakitu(e) {
  const tx = P.x / T, inZone = !inRoom && L.lakZones.some(z => tx >= z[0] - 4 && tx <= z[1]);
  if (!inZone) { e.x += 3; if (e.x > cam + VW + 60) e.dead = true; return; }
  const target = P.x + P.vx * 30 + Math.sin(e.t / 60) * 70, dx = target - e.x;
  e.vx = Math.max(-2.6, Math.min(2.6, e.vx + Math.sign(dx) * 0.08)); if (Math.abs(dx) < 30) e.vx *= 0.95;
  e.x += e.vx; e.y = 30 + Math.sin(e.t / 25) * 3;
  e.duck = (e.t % 150) > 115;                         // hides in the cloud, then throws
  if (e.t % 150 === 149 && enemies.filter(o => (o.k === 'spiky' || o.k === 'spegg') && !o.dead).length < 5) {
    const g = mkEnemy('spegg', e.x + 4, e.y + 6); g.vy = -3; g.vx = (P.x < e.x ? -1 : 1) * 0.8; g.active = true; enemies.push(g);
  }
}
function updateHammer(e) {
  if (e.home === undefined) { e.home = e.x; e.hd = -1; e.ht = 40 + (e.ph * 13) % 50; e.jt = 90 + (e.ph * 7) % 60; e.drop = 0; e.throwT = 0; }
  e.x += e.hd * 0.4; if (Math.abs(e.x - e.home) > 20) e.hd = -e.hd;
  if (e.onGround && --e.jt <= 0) {                    // hop up through the bricks, or drop down a row
    e.jt = 100 + ((Math.random() * 80) | 0);
    if (Math.random() < 0.5 && e.y > 5 * T) e.vy = -7.6; else { e.vy = -2.5; e.drop = 14; }
  }
  const prev = e.y + e.h; e.vy = Math.min(e.vy + 0.32, 6); e.y += e.vy; e.onGround = false;
  if (e.drop > 0) e.drop--;
  else if (e.vy > 0) {
    const ty = Math.floor((e.y + e.h) / T);
    for (const tx of [Math.floor((e.x + 2) / T), Math.floor((e.x + e.w - 2) / T)]) if (solid(tx, ty) && prev <= ty * T + 1) { e.y = ty * T - e.h; e.vy = 0; e.onGround = true; break; }
  }
  if (e.throwT > 0) e.throwT--;
  if (--e.ht <= 0 && e.x < cam + VW && e.x > cam) {
    e.ht = 35 + ((Math.random() * 60) | 0); e.throwT = 10;
    const h = mkEnemy('hammerp', e.x + 2, e.y - 4); h.vx = (P.x < e.x ? -1 : 1) * (1.1 + Math.random() * 1.3); h.vy = -5 - Math.random() * 1.6; h.active = true; enemies.push(h); SFX.hammer();
  }
  e.face = P.x < e.x ? -1 : 1;
  if (e.y > VH + 16) e.dead = true;
}
function updateBlooper(e) {
  const cyc = e.t % 70;
  if (cyc === 0) { const dx = (P.x + P.w / 2) - (e.x + e.w / 2); e.vx = Math.sign(dx || 1) * 1.6; e.vy = P.y < e.y ? -2.2 : -1.2; }
  e.vx *= 0.97; e.vy = Math.min(e.vy + 0.035, 0.7);
  e.x += e.vx; e.y = Math.max(20, Math.min(12 * T - e.h, e.y + e.vy)); e.squish = cyc < 20;
  if (e.x < cam - 80) e.dead = true;
}
function spawners() {
  if (inRoom) return;
  const tx = P.x / T;
  if (L.lakZones.length) {
    if (lakCD > 0) lakCD--;
    if (lakCD <= 0 && L.lakZones.some(z => tx >= z[0] && tx <= z[1]) && !enemies.some(e => e.k === 'lakitu' && !e.dead)) { const l = mkEnemy('lakitu', cam + VW + 10, 30); l.active = true; enemies.push(l); lakCD = 60; }
  }
  if (L.fishZones.some(z => tx >= z[0] && tx <= z[1]) && --fishCD <= 0) {
    fishCD = 55 + (frame * 13) % 50;
    const f = mkEnemy('jfish', cam + VW * (0.35 + Math.random() * 0.7), VH + 8); f.vx = -(0.6 + Math.random() * 1.2); f.vy = -(7.2 + Math.random() * 1.4); f.active = true; enemies.push(f);
  }
}
function shellHit(e, above) {
  if (e.shell === 0) {
    if (above) {
      e.shell = 1; e.y += e.h - 16; e.h = 16; e.vx = 0;
      comboHit(e); SFX.stomp(); buzz(15); P.vy = keys.jump ? -6 : -3.8;
    } else if (P.inv <= 0) hurt();
  } else if (e.shell === 1) {                       // idle shell: kick it away
    e.shell = 2; e.dir = (P.x + P.w / 2 < e.x + e.w / 2) ? 1 : -1; e.kg = 14; addScore(400, e.x, e.y - 4); SFX.kick(); if (above) P.vy = -4;
  } else if (above) { e.shell = 1; e.vx = 0; P.vy = -4; addScore(100, e.x, e.y - 4); SFX.stomp(); }
  else if (e.kg <= 0 && P.inv <= 0) hurt();
}
function updateCannons() {
  for (const c of cannons) {
    const sx = c.tx * T;
    if (sx < cam - 16 || sx > cam + VW) continue;
    if (--c.cd > 0) continue;
    if (Math.abs(P.x - sx) < 48) { c.cd = 30; continue; }
    c.cd = 170 + (c.tx * 13) % 40;
    const dir = P.x < sx ? -1 : 1, b = mkEnemy('bullet', dir < 0 ? sx - 16 : sx + 16, c.ty * T + 2);
    b.vx = dir * 1.7; b.active = true; enemies.push(b); SFX.bullet();
  }
}
function updateEnemies() {
  for (const e of enemies) {
    if (e.dead) continue;
    if (!e.active) { if (e.k !== 'boss' && e.x < cam + VW + 32) e.active = true; else continue; }
    if (e.bubble && --e.bubble.t <= 0) e.bubble = null;
    if (e.state === 'walk') {
      e.t++;
      switch (e.k) {
        case 'plant': updatePlant(e); continue;
        case 'thwomp': updateThwomp(e); continue;
        case 'pod': updatePod(e); continue;
        case 'fly': case 'fish': e.x += e.vx; e.y = e.baseY + Math.sin(e.t / 22 + e.ph) * 18; if (e.x < cam - 64) e.dead = true; continue;
        case 'bullet': case 'shot': e.x += e.vx; if (e.x < cam - 80 || e.x > cam + VW + 140) e.dead = true; continue;
        case 'rpara': e.y = e.baseY + Math.sin(e.t / 40 + e.ph) * 28; if (e.x < cam - 64) e.dead = true; continue;
        case 'lakitu': updateLakitu(e); continue;
        case 'hammer': updateHammer(e); continue;
        case 'blooper': updateBlooper(e); continue;
        case 'hammerp': e.vy += 0.28; e.x += e.vx; e.y += e.vy; e.rot = (e.rot || 0) + 0.3 * Math.sign(e.vx); if (e.y > VH + 20) e.dead = true; continue;
        case 'jfish': e.vy += 0.17; e.x += e.vx; e.y += e.vy; if (e.y > VH + 30 && e.vy > 0) e.dead = true; continue;
        case 'spegg':
          e.vy = Math.min(e.vy + 0.3, 6); e.x += e.vx; e.y += e.vy; e.onGround = false; collide(e, 'y');
          if (e.onGround) Object.assign(e, { k: 'spiky', w: 16, h: 22, y: e.y + e.h - 22, vx: (P.x < e.x ? -1 : 1) * 0.55, spd: 0.55 });   // hatches
          else if (e.y > VH + 20) e.dead = true;
          continue;
      }
      if (SHELLS[e.k]) { if (e.shell === 1) e.vx = 0; else if (e.shell === 2) { e.vx = e.dir * 4.2; if (e.kg > 0) e.kg--; } }
      e.vy = Math.min(e.vy + (e.k === 'boss' ? 0.5 : 0.45), 7);
      e.x += e.vx; if (collide(e, 'x').hit) { e.vx = -e.vx; if (SHELLS[e.k] && e.shell === 2) e.dir = -e.dir; }
      if (e.onGround && (e.k === 'spiky' || e.k === 'fast' || (e.k === 'rshell' && e.shell === 0)) && !groundAhead(e)) e.vx = -e.vx;
      e.y += e.vy; e.onGround = false; collide(e, 'y');
      if (e.k === 'para' && e.onGround) e.vy = -5.2;
      if (e.k === 'boss') {
        if (e.inv > 0) e.inv--;
        if (e.onGround && ++e.jt > e.jump) { e.jt = 0; e.vy = -7.2; e.vx = (P.x < e.x ? -1 : 1) * e.spd; }
        if (bossActive && L.arena.shots && ++e.sc > L.arena.shots) { e.sc = 0; fireShot(e); }
        if (bossActive && L.arena.hammers && e.t % 45 === 0) { const h = mkEnemy('hammerp', e.x + e.w / 2, e.y + 4); h.vx = (P.x < e.x ? -1 : 1) * (1.2 + Math.random() * 1.4); h.vy = -5.5 - Math.random() * 1.5; h.active = true; enemies.push(h); SFX.hammer(); }
      }
      if (e.y > VH + 16 || (e.k !== 'boss' && e.x < cam - 64)) e.dead = true;
    } else if (e.state === 'flat') { if (--e.t <= 0) e.dead = true; }
    else if (e.state === 'knock') { e.vy += 0.3; e.y += e.vy; e.x += e.vx; if (e.y > VH + 60) e.dead = true; }
  }
  // enemies bounce off each other; a sliding shell flattens everything it touches
  for (let i = 0; i < enemies.length; i++) {
    const a = enemies[i]; if (a.dead || !a.active || a.state !== 'walk') continue;
    if (SHELLS[a.k] && a.shell === 2) {
      for (const o of enemies) if (o !== a && !o.dead && o.active && o.state === 'walk' && o.k !== 'boss' && !INVULN[o.k] && overlap(a, o)) knock(o);
      continue;
    }
    if (LONER[a.k] || a.shell > 0 || !DEFS[a.k]) continue;
    for (let j = i + 1; j < enemies.length; j++) {
      const b = enemies[j]; if (b.dead || !b.active || b.state !== 'walk' || LONER[b.k] || b.shell > 0) continue;
      if (overlap(a, b)) { if (a.x < b.x) { a.vx = -Math.abs(a.vx); b.vx = Math.abs(b.vx); } else { a.vx = Math.abs(a.vx); b.vx = -Math.abs(b.vx); } }
    }
  }
  if (state === 'play') for (const e of enemies) {
    if (e.dead || !e.active || e.state !== 'walk') continue;
    if (e.k === 'plant' && !e.visible) continue;
    if (!overlap(P, e)) continue;
    if (P.star > 0) { if (INVULN[e.k]) continue; if (e.k === 'boss') { if (hitBoss()) P.vx = -P.face * 2; } else knock(e); continue; }
    const top = P.y + P.h - e.y, above = !water && P.vy > 0 && top < (e.k === 'boss' ? 16 : 13);   // no stomping underwater
    if (SHELLS[e.k]) { shellHit(e, above); continue; }
    if (above && (e.k === 'para' || e.k === 'rpara')) { e.k = e.k === 'para' ? 'shell' : 'rshell'; e.spd = 0.55; e.vx = -0.55; e.vy = 0; comboHit(e); SFX.stomp(); P.vy = keys.jump ? -6 : -3.8; continue; }
    if (e.k === 'thwomp') { if (above && top < 10) { P.vy = -4; P.y = e.y - P.h - 1; } else if (P.inv <= 0) hurt(); continue; }
    if (above && !NOSTOMP[e.k]) {
      if (e.k === 'boss') { hitBoss(); P.vy = -6; P.y = e.y - P.h - 1; }
      else if (KNOCKSTOMP[e.k]) { if (e.k === 'lakitu') lakCD = 420; knock(e); P.vy = keys.jump ? -6 : -4; P.combo++; buzz(15); }
      else {
        e.state = 'flat'; e.t = 40; e.bubble = Math.random() < 0.5 ? { text: STOMP_LINES[(Math.random() * STOMP_LINES.length) | 0], t: 40 } : null;
        comboHit(e); SFX.stomp(); buzz(15);
        P.vy = keys.jump ? -6 : -3.8;
      }
    } else if (P.inv <= 0) hurt();
  }
  enemies = enemies.filter(e => !e.dead || e === boss);
  if (--bubbleCD <= 0) {
    bubbleCD = 140 + Math.random() * 160;
    const near = enemies.filter(e => e.active && e.state === 'walk' && !e.bubble && TALK[e.k] && e.shell !== 2 && e.x > cam + 8 && e.x < cam + VW - 24);
    if (near.length) { const e = near[(Math.random() * near.length) | 0]; e.bubble = { text: BUBBLES[(Math.random() * BUBBLES.length) | 0], t: 110 }; }
  }
}

function updateBalls() {
  for (const b of balls) {
    b.vy += 0.35; b.x += b.vx; b.rot += b.vx * 0.12;
    if (collide(b, 'x').hit) b.t = 0;
    b.y += b.vy; b.onGround = false; collide(b, 'y'); if (b.onGround) b.vy = -3.1;
    if (--b.t <= 0 || b.y > VH + 10 || b.x < cam - 20 || b.x > cam + VW + 20) { b.dead = true; continue; }
    for (const e of enemies) {
      if (e.dead || !e.active || e.state !== 'walk' || !overlap(b, e)) continue;
      if (e.k === 'plant' && !e.visible) continue;
      b.dead = true; if (INVULN[e.k] || NOBALL[e.k]) break; if (e.k === 'lakitu') lakCD = 420; if (e.k === 'boss') hitBoss(); else knock(e); break;
    }
  }
  balls = balls.filter(b => !b.dead);
}

function updateItems() {
  for (const it of items) {
    if (it.type === 'coinpop') { it.y += it.vy; it.vy += 0.35; if (--it.t <= 0) { it.dead = true; pops.push({ x: it.x, y: it.y, text: '200', t: 40 }); } continue; }
    if (it.state === 'rise') { it.y -= 0.5; if (--it.t <= 0) { it.state = 'move'; it.vx = it.type === 'star' ? 1.2 : 1; if (it.type === 'star') it.vy = -4; } }
    else {
      it.vy = Math.min(it.vy + 0.4, 6);
      it.x += it.vx; if (collide(it, 'x').hit) it.vx = -it.vx;
      it.y += it.vy; it.onGround = false; collide(it, 'y');
      if (it.type === 'star' && it.onGround) it.vy = -5;
      if (it.y > VH + 16) it.dead = true;
    }
    if (!it.dead && state === 'play' && overlap(P, it)) {
      it.dead = true;
      if (it.type === 'bolt') { addScore(1000, it.x, it.y); SFX.power(); if (!P.big) { setBig(true); state = 'grow'; growT = 48; } }
      else if (it.type === 'ball') { addScore(1000, it.x, it.y); SFX.power(); P.fire = true; if (!P.big) { setBig(true); state = 'grow'; growT = 48; } pops.push({ x: P.x - 6, y: P.y - 10, text: 'KICK!', t: 60 }); }
      else if (it.type === 'star') { addScore(1000, it.x, it.y); SFX.star(); P.star = 900; Audio.music.tempoMul = 0.7; }
      else if (it.type === 'heart') { lives++; SFX.oneup(); pops.push({ x: it.x, y: it.y, text: '1UP', t: 60 }); }
    }
  }
  items = items.filter(i => !i.dead);
  for (const c of coins) if (!c.taken && overlap(P, c)) { c.taken = true; addCoin(); }
}

function updateFx() {
  for (const p of parts) { p.vy += 0.3; p.x += p.vx; p.y += p.vy; p.t--; }
  parts = parts.filter(p => p.t > 0 && p.y < VH + 16);
  for (const p of pops) { p.y -= 0.6; p.t--; }
  pops = pops.filter(p => p.t > 0);
  for (const b of bumps) b.t--;
  bumps = bumps.filter(b => b.t > 0);
  for (const w of weather) {
    w.x += w.vx; w.y += w.vy;
    if (w.y > VH) { w.y = -2; w.x = Math.random() * 600; } if (w.y < -4) { w.y = VH; w.x = Math.random() * 600; }
    if (w.x < -4) w.x = VW + 4; if (w.x > 620) w.x = -2;
  }
  for (const f of fbars) f.a += f.speed;
  for (const v of vines) if (v.top > -40) v.top -= 1.6;
  for (const f of fws) { f.r += 0.9; f.t--; }
  fws = fws.filter(f => f.t > 0);
  for (const s of springs) if (s.t > 0) s.t--;
  for (const b of bubs) { b.y += b.vy; b.x += Math.sin(b.t / 6) * 0.2; b.t--; }
  bubs = bubs.filter(b => b.t > 0);
  if (shake > 0) shake--;
  if (bannerT > 0) bannerT--;
}

function camLimit() { return (L.arena && !bossDead && !inRoom ? (L.arena.x1 + 1) * T : cols * T) - VW; }
function updateCamera() {
  const target = P.x - VW * 0.42;
  if (target > cam) cam = target;
  cam = Math.max(0, Math.min(cam, camLimit()));
}

function updateFlag() {
  if (flagState === 'slide') {
    if (P.y + P.h < 192) P.y = Math.min(P.y + 2, 192 - P.h);
    if (flagY < 180) flagY = Math.min(flagY + 2, 180);
    if (P.y + P.h >= 192 && flagY >= 180) { flagState = 'hold'; flagT = 20; }
  } else if (flagState === 'hold') {
    if (--flagT <= 0) { flagState = 'walk'; P.x = L.poleTx * T + 9; P.face = 1; SFX.win(); }
  } else if (flagState === 'walk') {
    P.vx = 1.1; P.vy = Math.min(P.vy + 0.6, 6);
    P.x += P.vx; collide(P, 'x'); P.y += P.vy; P.onGround = false; collide(P, 'y'); P.dist += P.vx;
    if (P.x + P.w / 2 >= L.castleX + 40) { P.hidden = true; flagState = 'tally'; }
  } else if (flagState === 'tally') {
    if (time > 0) { const n = Math.min(time, 4); time -= n; score += n * 50; if (frame % 3 === 0) SFX.tick(); }
    else { flagState = 'raise'; flagT = 0; }
  } else if (flagState === 'raise') {
    castleFlag = Math.min(castleFlag + 0.5, 26);
    if (castleFlag >= 26) { ++flagT; if (fwN > 0 && flagT % 36 === 0) { launchFirework(); fwN--; } if (fwN === 0 && flagT > 60 && !fws.length) finishLevel(); }
  }
  updateCamera();
}
function startAxe() {
  state = 'axe'; axeT = 0; P.vx = 0; P.vy = 0; SFX.flag();
  for (const e of enemies) if (e !== boss && e.state === 'walk' && !INVULN[e.k]) knock(e);
  enemies = enemies.filter(e => e === boss || e.state === 'knock');
}
function updateAxe() {
  axeT++;
  const [a, b] = L.arena.br;
  if (axeT % 3 === 0) for (let x = b; x >= a; x--) if (map[13][x] === TILE.BRIDGE) { map[13][x] = 0; SFX.collapse(); parts.push({ x: x * T, y: 13 * T, vx: 0, vy: 1, t: 60 }); break; }
  if (boss && !bossDead) {
    const under = Math.floor((boss.x + boss.w / 2) / T);
    if (map[13][under] !== TILE.BRIDGE) { boss.vy = Math.min((boss.vy || 0) + 0.4, 8); boss.y += boss.vy; if (boss.y > VH + 60) { bossDead = true; addScore(5000, boss.x, 120); SFX.boss(); } }
  }
  updateFx();
  const left = map[13].slice(a, b + 1).some(t => t === TILE.BRIDGE);
  if (!left && (bossDead || !boss) && axeT > 40) { state = 'rescue'; rescueT = 0; score += time * 50; time = 0; SFX.win(); }
}
function launchFirework() {
  const cols = ['#ffe14a', '#ff5a8a', '#5af0ff', '#9aff6a', '#ffffff'];
  fws.push({ x: L.castleX + 40 + (Math.random() - 0.5) * 120, y: 30 + Math.random() * 50, r: 2, t: 50, col: cols[(Math.random() * cols.length) | 0] });
  addScore(500, L.castleX + 30, 80); SFX.firework(); shake = 4;
}
function finishLevel() {
  if (L.arena && L.arena.final) save.hardUnlocked = true;
  save.best[lvl] = Math.max(save.best[lvl], lvlScore());
  if (lvl + 1 < LEVELS.length) save.unlocked = Math.max(save.unlocked, lvl + 2);
  persist(); state = lvl + 1 >= LEVELS.length ? 'complete' : 'win'; menuSel = 0;
}

function update() {
  frame++;
  if (state === 'play') {
    updatePlatforms(); updatePlayer();
    if (state === 'play' || state === 'flag') { updateEnemies(); updateCannons(); spawners(); updateItems(); updateBalls(); }
    updateFx(); if (state === 'play') updateCamera();
  } else if (state === 'warp') {
    updateWarp(); updateFx();
  } else if (state === 'axe') { updateAxe();
  } else if (state === 'rescue') { rescueT++; updateFx(); if (rescueT > 330) finishLevel();
  } else if (state === 'intro') {
    if (--introT <= 0) state = 'play';
    updateFx();
  } else if (state === 'grow') { if (--growT <= 0) state = 'play'; updatePlatforms(); }
  else if (state === 'dying') {
    if (!P.fell && dieT < 145) { P.vy += 0.3; P.y += P.vy; }
    updateFx();
    if (--dieT <= 0) {
      lives--;
      if (lives <= 0) { state = 'gameover'; menuSel = 0; Audio.music.on = false; SFX.over(); }
      else startLevel(lvl, true);
    }
  } else if (state === 'flag') { updatePlatforms(); updateFlag(); updateFx(); }
  else if (state === 'title' || state === 'select') { updateFx(); cam += 0.4; }
  Audio.music.on = (state === 'play' || state === 'grow' || state === 'intro' || state === 'warp');
  if (state === 'flag' || state === 'axe' || state === 'rescue') Audio.music.on = false;
}

// ---------- drawing helpers ----------
function txt(s, x, y, size, col, align, font, sh) {
  ctx.font = (font === HEB ? '800 ' : '') + size + 'px ' + (font || PIX); ctx.direction = font === HEB ? 'rtl' : 'ltr'; ctx.textAlign = align || 'left'; ctx.textBaseline = 'top';
  const o = Math.max(1, Math.round(size / 9)); ctx.fillStyle = sh || '#1a0f0c'; ctx.fillText(s, x + o, y + o); ctx.fillStyle = col || '#fff'; ctx.fillText(s, x, y);
}
function rrect(x, y, w, h, r, fill, stroke, lw) {
  ctx.beginPath(); ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r); ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
  if (fill) { ctx.fillStyle = fill; ctx.fill(); } if (stroke) { ctx.lineWidth = lw || 1; ctx.strokeStyle = stroke; ctx.stroke(); }
}
function button(x, y, w, h, label, fn, sel, col) {
  UI.buttons.push({ x, y, w, h, fn });
  rrect(x + 1, y + 2, w, h, 5, '#00000066'); rrect(x, y, w, h, 5, col || '#ffb02e', sel ? '#fff' : '#1a0f0c', sel ? 2 : 1.4);
  rrect(x + 2, y + 2, w - 4, h * 0.4, 3, '#ffffff33');
  txt(label, x + w / 2, y + h / 2 - 7, 13, '#1a0f0c', 'center', HEB, '#ffffff55');
}
function panel(x, y, w, h) { rrect(x, y, w, h, 8, '#140c1ae0', '#ffb02e', 2); }

function drawHUD() {
  const pill = (x, w) => rrect(x, 5, w, 17, 8, '#00000070');
  pill(6, 62); Art.head(Art.IMG.hero, 17, 14, 14, 0); txt('x' + lives, 28, 10, 8, '#fff');
  pill(74, 64); Art.drawCoin(70, 5, (frame >> 3) & 3); txt('x' + String(coinCount).padStart(2, '0'), 94, 10, 8, '#fff');
  pill(144, 76); txt(String(score).padStart(6, '0'), 150, 10, 8, '#ffe14a');
  const tw = 62; pill(VW - 6 - tw - 56, tw + 0);
  txt(String(time).padStart(3, '0'), VW - 6 - tw - 56 + 25, 10, 8, time <= 100 && (frame >> 4) & 1 ? '#ff8a6a' : '#fff');
  ctx.strokeStyle = '#fff'; ctx.lineWidth = 1.2; ctx.beginPath(); ctx.arc(VW - 6 - tw - 56 + 13, 13.5, 4, 0, 7); ctx.moveTo(VW - 6 - tw - 56 + 13, 13.5); ctx.lineTo(VW - 6 - tw - 56 + 13, 10.5); ctx.moveTo(VW - 6 - tw - 56 + 13, 13.5); ctx.lineTo(VW - 6 - tw - 56 + 16, 13.5); ctx.stroke();
  if (P.fire) { Art.drawBall(230, 13, 5, frame / 10); }
  if (save.hardOn) txt('HARD', 258, 10, 7, '#ff6a6a');
  if (P.star > 0) { txt(String(Math.ceil(P.star / 60)), 246, 10, 8, '#ffe14a'); Art.star5(238, 13.5, 5, '#ffe14a'); }
  if (boss && bossActive && !bossDead) { rrect(VW / 2 - 50, 26, 100, 8, 4, '#00000099'); const sw = 94 / boss.hpMax; for (let i = 0; i < boss.hpMax; i++) rrect(VW / 2 - 47 + i * sw, 28, sw - 3, 4, 2, i < boss.hp ? '#ff4a4a' : '#442222'); }
}

function drawBubble(e) {
  const s = e.bubble.text; ctx.font = '800 7px ' + HEB; ctx.direction = 'rtl';
  const w = Math.ceil(ctx.measureText(s).width) + 10, h = 12;
  const ax = e.x + e.w / 2 - cam; let x = Math.round(ax - w / 2), y = Math.round(e.y + (e.state === 'flat' ? 8 : 0)) - h - 12;
  if (e.k === 'boss') y -= 22;
  x = Math.max(3, Math.min(VW - w - 3, x)); y = Math.max(40, y);
  rrect(x - 1, y - 1, w + 2, h + 2, 4, '#1a0f0c'); rrect(x, y, w, h, 3, '#fff');
  ctx.fillStyle = '#1a0f0c'; ctx.beginPath(); ctx.moveTo(ax - 3, y + h); ctx.lineTo(ax + 1, y + h + 5); ctx.lineTo(ax + 4, y + h); ctx.fill();
  ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.moveTo(ax - 2, y + h - 0.5); ctx.lineTo(ax + 1, y + h + 3.5); ctx.lineTo(ax + 3, y + h - 0.5); ctx.fill();
  ctx.fillStyle = '#26214a'; ctx.textAlign = 'center'; ctx.textBaseline = 'top'; ctx.fillText(s, x + w / 2, y + 2);
}

function drawHeroNow() {
  if (!P || P.hidden || (P.inv > 0 && (frame >> 2) % 2)) return;
  let mode = 'stand';
  if (P.dead) mode = 'dead'; else if (state === 'flag' && flagState !== 'walk') mode = 'jump'; else if (!P.onGround) mode = 'jump'; else if (Math.abs(P.vx) > 0.1) mode = 'walk';
  Art.drawHero(P, mode, Math.floor(P.dist / 5), frame);
}
const WEATHER_COL = { lava: '#ff7a2a', void: '#e090ff', desert: '#f5d9a0aa', night: '#fff59a', ash: '#d8c8c0aa', sea: '#ffffffaa' };
function drawWorld() {
  const cx = Math.round(cam), th = theme;
  Art.drawSky(th, cx, frame);
  ctx.save();
  if (shake > 0) ctx.translate(Math.round((Math.random() - 0.5) * shake * 0.6), Math.round((Math.random() - 0.5) * shake * 0.6));
  ctx.translate(-cx, 0);
  if (LAVA[th] && !inRoom) Art.drawLavaBand(frame, cx);
  if (th === 'coast' && !inRoom) Art.drawWaterBand(frame, cx);
  if (!inRoom) {
    if (L.poleTx > 0 && cx + VW > L.castleX - 40) Art.drawCastle(L.castleX, th, castleFlag);
    if (L.poleTx > 0 && cx + VW > L.poleTx * T - 30) Art.drawPole(L.poleTx * T + 7, flagY, frame);
    for (const c of L.checks) if (c * T > cx - 32 && c * T < cx + VW) Art.drawCheck(c * T, checkTx >= c, frame);
  }
  for (const e of enemies) if (e.k === 'plant' && e.active && !e.dead && e.x > cx - 32 && e.x < cx + VW + 32) Art.drawPlant(e, frame);
  if (P && P.warping) drawHeroNow();
  const tx0 = Math.max(0, Math.floor(cx / T)), tx1 = Math.min(cols - 1, tx0 + Math.ceil(VW / T) + 1);
  for (let ty = 0; ty < ROWS; ty++) for (let tx = tx0; tx <= tx1; tx++) {
    const t = map[ty][tx]; if (!t) continue;
    let oy = 0; for (const b of bumps) if (b.tx === tx && b.ty === ty) oy = -Math.round(Math.sin((10 - b.t) / 10 * Math.PI) * 5);
    const above = ty > 0 && isSolid(map[ty - 1][tx]) && map[ty - 1][tx] !== TILE.PTL;
    Art.drawTile(t, tx * T, ty * T + oy, ty, tx, th, frame, above);
  }
  for (const w of areaWarps) if (!P.warping && Math.abs(P.x - w.tx * T) < 150) { Art.drawWarpArrow(w.tx * T + 16, w.top * T, frame); if (w.level != null) txt(String(w.level + 1), w.tx * T + 16, w.top * T - 28, 8, '#fff', 'center'); }
  for (const v of vines) Art.drawVine(v, frame);
  if (!inRoom && L.arena && L.arena.axe && state !== 'axe' && state !== 'rescue') Art.drawAxe(L.arena.axe.tx, L.arena.axe.row, frame);
  plats.forEach((p, i) => { if (p.type === 'bal' && p.pair > i) Art.drawPulley(p, plats[p.pair]); });
  for (const p of plats) if (p.x + p.w > cx && p.x < cx + VW) Art.drawPlat(p);
  for (const s of springs) if (s.x > cx - 16 && s.x < cx + VW) Art.drawSpring(s);
  for (const f of fbars) { const px = f.tx * T; if (px > cx - 90 && px < cx + VW + 90) Art.drawFirebar(f); }
  const ph = (frame >> 3) & 3;
  for (const c of coins) if (!c.taken && c.x > cx - 16 && c.x < cx + VW) Art.drawCoin(c.x - 3, c.y - 1, ph);
  for (const it of items) { if (it.type === 'coinpop') Art.drawCoin(Math.round(it.x), Math.round(it.y), (frame >> 2) & 3); else Art.drawItem(it, frame); }
  for (const e of enemies) { if (e.dead && e !== boss) continue; if (!e.active && e.x > cx + VW) continue; if (e === boss && boss.inv > 0 && (frame >> 2) & 1 && boss.state === 'walk') continue; Art.drawEnemy(e, frame); }
  for (const b of balls) Art.drawBall(Math.round(b.x + 4), Math.round(b.y + 4), 4, b.rot);
  if (!(P && P.warping)) drawHeroNow();
  for (const p of parts) { Art.R(Math.round(p.x), Math.round(p.y), 6, 6, '#1a0f0c'); Art.R(Math.round(p.x) + 1, Math.round(p.y) + 1, 4, 4, Art.THEMES[th].br[0]); }
  for (const f of fws) Art.drawFirework(f);
  for (const b of bubs) { ctx.strokeStyle = '#ffffffcc'; ctx.lineWidth = 0.8; ctx.beginPath(); ctx.arc(Math.round(b.x), Math.round(b.y), 1.6 + (50 - b.t) / 40, 0, 7); ctx.stroke(); }
  ctx.restore();
  const wc = WEATHER_COL[th] || '#ffffffdd';
  for (const w of weather) { ctx.fillStyle = th === 'lava' ? (w.r > 1 ? '#ffb02e' : '#ff6a2a') : wc; ctx.fillRect(Math.round(w.x % (VW + 8)), Math.round(w.y), w.r, w.r); }
  if (water) { ctx.fillStyle = 'rgba(30,130,240,0.17)'; ctx.fillRect(0, 0, VW, VH); }
  if (th === 'cave') {
    const hx = P ? P.x + P.w / 2 - cx : VW / 2, hy = P ? P.y + P.h / 2 : 120;
    const g = ctx.createRadialGradient(hx, hy, 26, hx, hy, 150); g.addColorStop(0, 'rgba(6,3,20,0)'); g.addColorStop(1, 'rgba(6,3,20,0.8)');
    ctx.fillStyle = g; ctx.fillRect(0, 0, VW, VH);
  }
  for (const p of pops) txt(p.text, Math.round(p.x - cx), Math.round(p.y), 7, '#fff');
  for (const e of enemies) if (e.bubble && e.active && !e.dead) drawBubble(e);
}

function drawTitle() {
  Art.drawSky('grass', cam, frame);
  const bob = Math.sin(frame / 18) * 2, cxm = VW / 2;
  for (let i = 0; i < Math.ceil(VW / T) + 1; i++) { Art.drawTile(TILE.GND, i * T - (Math.round(cam) % T), 13 * T, 13, i, 'grass', frame, false); Art.drawTile(TILE.GND, i * T - (Math.round(cam) % T), 14 * T, 14, i, 'grass', frame, true); }
  const fake = { x: cxm - 110, y: 13 * T - 36, w: 12, h: 36, big: true, face: 1, dist: frame * 1.2, star: 0, fire: false, inv: 0, dead: false };
  Art.drawHero(fake, 'walk', Math.floor(frame / 6), frame);
  Art.drawEnemy({ k: 'walk', x: cxm + 78, y: 13 * T - 22, w: 14, h: 22, state: 'walk', t: frame, hp: 3 }, frame);
  Art.drawEnemy({ k: 'fast', x: cxm + 108, y: 13 * T - 22, w: 14, h: 22, state: 'walk', t: frame + 20, hp: 3 }, frame);
  txt('SUPER', cxm, 14 + bob, 26, '#ffe14a', 'center', PIX, '#7a2a0c');
  txt('FACE BROS.', cxm, 48 + bob, 26, '#ff5a4a', 'center', PIX, '#4a0c0c');
  Art.head(Art.IMG.hero, cxm - 156, 48 + bob, 52, -0.15); Art.head(Art.IMG.enemy, cxm + 156, 48 - bob, 50, 0.15);
  txt('קפצו על האויבים, אספו מטבעות, והגיעו לדגל!', cxm, 100, 10, '#fff', 'center', HEB);
  const touch = document.body.classList.contains('touch');
  txt(touch ? 'חצים: תנועה  ·  A: קפיצה  ·  B: ריצה ובעיטה' : 'חיצים: תנועה  ·  רווח: קפיצה  ·  Shift: ריצה  ·  C: בעיטה  ·  P: השהיה', cxm, 170, 8, '#fff', 'center', HEB);
  button(cxm - 64, 128, 128, 30, 'שחקו', () => { SFX.select(); state = 'select'; menuSel = Math.min(save.unlocked, LEVELS.length) - 1; }, true);
}

function fitTxt(s, x, y, maxW, size, col, align, font) {
  ctx.font = '800 ' + size + 'px ' + HEB; let sz = size; const w = ctx.measureText(s).width; if (w > maxW) sz = Math.max(5, size * maxW / w);
  txt(s, x, y, sz, col, align, font);
}
const SEL_COLS = 4;
const PAGE = 12;
function drawSelect() {
  Art.drawSky('grass', cam, frame);
  rrect(0, 0, VW, VH, 0, '#0a0614b0');
  const pages = Math.ceil(LEVELS.length / PAGE), pg = Math.min(pages - 1, (menuSel / PAGE) | 0);
  txt('בחרו שלב', VW / 2, 6, 16, '#ffe14a', 'center', HEB);
  if (pages > 1) txt((pg + 1) + '/' + pages, VW / 2, 26, 6, '#c9bcc4', 'center');
  const cw = 84, ch = 50, gap = 6, gx = (VW - (cw * SEL_COLS + gap * (SEL_COLS - 1))) / 2, gy = 36;
  for (let i = pg * PAGE; i < Math.min(LEVELS.length, (pg + 1) * PAGE); i++) {
    const j = i - pg * PAGE, c = j % SEL_COLS, r = (j / SEL_COLS) | 0, x = gx + c * (cw + gap), y = gy + r * (ch + gap), lv = LEVELS[i], open = i < save.unlocked, sel = menuSel === i;
    const th = Art.THEMES[lv.theme];
    UI.buttons.push({ x, y, w: cw, h: ch, fn: () => { menuSel = i; if (open) { SFX.select(); newRun(i); } else SFX.bump(); } });
    rrect(x, y, cw, ch, 6, '#000000aa'); const g = ctx.createLinearGradient(0, y, 0, y + ch); g.addColorStop(0, th.sky[0]); g.addColorStop(1, th.sky[1]);
    rrect(x + 2, y + 2, cw - 4, ch - 4, 5, g, sel ? '#fff' : '#1a0f0c', sel ? 2.5 : 1.5);
    rrect(x + 2, y + ch - 17, cw - 4, 15, 5, th.g.body); ctx.fillStyle = th.g.top; ctx.fillRect(x + 3, y + ch - 17, cw - 6, 4);
    if (!open) { rrect(x + 2, y + 2, cw - 4, ch - 4, 5, '#000000a0'); ctx.fillStyle = '#ddd'; ctx.fillRect(x + cw / 2 - 6, y + 17, 12, 9); ctx.strokeStyle = '#ddd'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(x + cw / 2, y + 17, 4.5, Math.PI, 0); ctx.stroke(); }
    txt(String(lv.id), x + 6, y + 5, 11, '#fff');
    fitTxt(lv.name, x + cw / 2, y + ch - 15, cw - 8, 8, open ? '#fff' : '#bbb', 'center', HEB);
    if (open && save.best[i] > 0) txt(String(save.best[i]), x + cw - 6, y + 7, 5, '#ffe14a', 'right');
    if (open && i + 1 < save.unlocked) { Art.star5(x + cw - 12, y + 23, 6, '#1a0f0c'); Art.star5(x + cw - 12, y + 23, 4.8, '#ffe14a'); }
  }
  if (pages > 1) {
    if (pg > 0) button(4, VH / 2 - 14, 22, 28, '◀', () => { SFX.select(); menuSel = Math.max(0, (pg - 1) * PAGE); }, false, '#cfd8ee');
    if (pg < pages - 1) button(VW - 26, VH / 2 - 14, 22, 28, '▶', () => { SFX.select(); menuSel = (pg + 1) * PAGE; }, false, '#cfd8ee');
  }
  button(10, VH - 26, 64, 20, 'חזרה', () => { SFX.select(); state = 'title'; }, false, '#cfd8ee');
  if (save.hardUnlocked) button(VW - 120, VH - 26, 110, 20, save.hardOn ? 'מצב קשה: פעיל' : 'מצב קשה: כבוי', () => { save.hardOn = !save.hardOn; persist(); SFX.select(); }, false, save.hardOn ? '#ff8a6a' : '#cfd8ee');
  else txt('SUPER FACE BROS.', VW - 8, VH - 18, 7, '#fff', 'right');
}

function drawIntro() {
  rrect(0, 0, VW, VH, 0, '#0a0614');
  const th = Art.THEMES[theme], g = ctx.createLinearGradient(0, 0, 0, VH); g.addColorStop(0, th.sky[0]); g.addColorStop(1, th.sky[1]); ctx.globalAlpha = 0.35; ctx.fillStyle = g; ctx.fillRect(0, 0, VW, VH); ctx.globalAlpha = 1;
  txt('שלב ' + (lvl + 1), VW / 2, 52, 24, '#ffe14a', 'center', HEB);
  txt(L.def.name, VW / 2, 88, 20, '#fff', 'center', HEB);
  txt(L.def.en, VW / 2, 118, 8, '#c9bcc4', 'center');
  if (L.def.hint) fitTxt(L.def.hint, VW / 2, 138, VW - 30, 10, '#ffe9a8', 'center', HEB);
  Art.head(Art.IMG.hero, VW / 2 - 24, 184, 28, 0); txt('x ' + lives, VW / 2 + 2, 178, 14, '#fff');
}

function drawOverlay() {
  const W = VW;
  if (state === 'paused') {
    rrect(0, 0, W, VH, 0, '#00000099'); panel(W / 2 - 80, 50, 160, 142);
    txt('מושהה', W / 2, 60, 20, '#fff', 'center', HEB);
    button(W / 2 - 60, 94, 120, 26, 'המשך', togglePause, menuSel === 0);
    button(W / 2 - 60, 126, 120, 26, 'שלב מחדש', () => { retryLevel(); }, menuSel === 1, '#cfd8ee');
    button(W / 2 - 60, 158, 120, 26, 'תפריט שלבים', () => { state = 'select'; btnPauseEl.textContent = '⏸'; }, menuSel === 2, '#cfd8ee');
  } else if (state === 'dying' && dieT < 150) {
    txt(deathMsg, W / 2, 96, 22, '#ff8a6a', 'center', HEB);
  } else if (state === 'gameover') {
    rrect(0, 0, W, VH, 0, '#000000aa'); panel(W / 2 - 100, 40, 200, 150);
    txt('נגמרו החיים', W / 2, 52, 20, '#ff8a6a', 'center', HEB);
    txt(String(lvlScore()).padStart(6, '0'), W / 2, 82, 12, '#fff', 'center');
    button(W / 2 - 64, 108, 128, 28, 'נסו שוב', retryLevel, menuSel === 0);
    button(W / 2 - 64, 142, 128, 28, 'תפריט שלבים', () => { state = 'select'; }, menuSel === 1, '#cfd8ee');
  } else if (state === 'win' || state === 'complete') {
    rrect(0, 0, W, VH, 0, '#00000088'); panel(W / 2 - 112, 30, 224, 170);
    txt(state === 'complete' ? 'סיימתם את המשחק!' : 'השלב הושלם!', W / 2, 40, 18, '#ffe14a', 'center', HEB);
    txt(L.def.name, W / 2, 66, 11, '#fff', 'center', HEB);
    txt('SCORE ' + String(lvlScore()).padStart(6, '0'), W / 2, 90, 9, '#fff', 'center');
    txt('BEST  ' + String(save.best[lvl]).padStart(6, '0'), W / 2, 106, 8, '#c9bcc4', 'center');
    if (state === 'win') { button(W / 2 - 70, 126, 140, 28, 'השלב הבא', nextLevel, menuSel === 0); button(W / 2 - 70, 160, 140, 28, 'תפריט שלבים', () => { state = 'select'; }, menuSel === 1, '#cfd8ee'); }
    else { button(W / 2 - 70, 134, 140, 28, 'תפריט שלבים', () => { state = 'select'; }, true); }
  }
  if (state === 'rescue') {
    const fin = L.arena && L.arena.final, a = Math.min(1, rescueT / 30);
    ctx.globalAlpha = a; rrect(0, 0, W, VH, 0, '#000000aa'); panel(W / 2 - 130, 40, 260, 150);
    Art.drawFriend(W / 2, 112, frame);
    if (fin) Art.head(Art.IMG.hero, W / 2 - 40, 96, 26, 0), Art.head(Art.IMG.enemy, W / 2 + 40, 96, 24, 0);
    txt(fin ? 'כל הכבוד! הצלת את כולם!' : 'תודה רבה שהצלת אותי!', W / 2, 128, 15, '#ffe14a', 'center', HEB);
    txt(fin ? 'אתה הגיבור הכי גדול!' : 'אבל החבר שלנו נמצא בטירה אחרת...', W / 2, 152, 11, '#fff', 'center', HEB);
    if (rescueT > 60 && (frame >> 5) % 2 === 0) txt('הקישו להמשך', W / 2, 172, 8, '#c9bcc4', 'center', HEB);
    ctx.globalAlpha = 1;
  }
  if (bannerT > 0 && (state === 'play')) { const a = Math.min(1, bannerT / 20); ctx.globalAlpha = a; txt(bannerText, W / 2, 70, 22, '#ff5a4a', 'center', HEB); ctx.globalAlpha = 1; }
}

function draw() {
  ctx.setTransform(SC, 0, 0, SC, 0, 0); ctx.imageSmoothingEnabled = false;
  UI.buttons = [];
  if (state === 'title') drawTitle();
  else if (state === 'select') drawSelect();
  else if (state === 'intro') drawIntro();
  else { drawWorld(); drawHUD(); drawOverlay(); }
  if (fade > 0) { ctx.fillStyle = 'rgba(0,0,0,' + fade + ')'; ctx.fillRect(0, 0, VW, VH); }
}

// ---------- input ----------
const KEYMAP = { ArrowLeft: 'left', KeyA: 'left', ArrowRight: 'right', KeyD: 'right', ArrowUp: 'jump', KeyW: 'jump', Space: 'jump', KeyZ: 'jump', KeyK: 'jump', ShiftLeft: 'run', ShiftRight: 'run', KeyX: 'run', KeyJ: 'run', ArrowDown: 'down', KeyS: 'down' };
const btnPauseEl = document.getElementById('btnPause'), btnMuteEl = document.getElementById('btnMute');
function menuCount() { return { paused: 3, gameover: 2, win: 2, complete: 1 }[state] || 0; }
function menuActivate() {
  if (state === 'rescue') { if (rescueT > 60) finishLevel(); return true; }
  if (state === 'title') { UI.buttons[0] && UI.buttons[0].fn(); return true; }
  if (state === 'select') { const lv = menuSel; if (lv < save.unlocked) { SFX.select(); newRun(lv); } return true; }
  const n = menuCount(); if (!n) return false;
  const b = UI.buttons[menuSel]; if (b) b.fn(); return true;
}
function menuMove(dx, dy) {
  if (state === 'select') { let m = menuSel + dx + dy * SEL_COLS; if (m >= 0 && m < LEVELS.length) { menuSel = m; SFX.select(); } return true; }
  const n = menuCount(); if (!n) return false; menuSel = (menuSel + dx + dy + n) % n; SFX.select(); return true;
}
function togglePause() {
  if (state === 'play') { state = 'paused'; menuSel = 0; Audio.music.on = false; }
  else if (state === 'paused') state = 'play';
  btnPauseEl.textContent = state === 'paused' ? '▶' : '⏸';
}
function toggleMute() { Audio.setMuted(!Audio.muted); btnMuteEl.textContent = Audio.muted ? '🔇' : '🔊'; }
function press(k) {
  if (state === 'rescue') { if (rescueT > 60) finishLevel(); return; }
  if (k === 'jump' && !keys.jump) { if (state === 'play') jumpBuf = 8; }
  if (k === 'run' && !keys.run && state === 'play') kick();
  keys[k] = true;
}
addEventListener('keydown', e => {
  const k = KEYMAP[e.code];
  if (k || e.code === 'Enter') e.preventDefault();
  Audio.init();
  if (state !== 'play' && state !== 'grow') {
    if (e.code === 'Enter' || e.code === 'Space') { if (state === 'paused' && e.code === 'Enter') { togglePause(); return; } menuActivate(); return; }
    if (e.code === 'ArrowLeft') { menuMove(state === 'select' ? -1 : 0, 0); return; }
    if (e.code === 'ArrowRight') { menuMove(state === 'select' ? 1 : 0, 0); return; }
    if (e.code === 'ArrowUp') { menuMove(0, state === 'select' ? -1 : -1); return; }
    if (e.code === 'ArrowDown') { menuMove(0, 1); return; }
  }
  if (e.code === 'Enter') { togglePause(); return; }
  if (e.code === 'KeyP' || e.code === 'Escape') { if (!window.__back()) { /* nothing */ } return; }
  if (e.code === 'KeyM') { toggleMute(); return; }
  if (e.code === 'KeyC' || e.code === 'KeyF') { if (state === 'play') kick(); return; }
  if (k && !e.repeat) press(k); else if (k) keys[k] = true;
});
addEventListener('keyup', e => { const k = KEYMAP[e.code]; if (k) keys[k] = false; });
function releaseAll() { for (const k in keys) keys[k] = false; }
addEventListener('blur', () => { releaseAll(); if (state === 'play') togglePause(); });
document.addEventListener('visibilitychange', () => { if (document.hidden) { releaseAll(); if (state === 'play') togglePause(); Audio.suspend(); } else Audio.resume(); });

function toGame(ev) {
  const r = cv.getBoundingClientRect(), sc = Math.min(r.width / VW, r.height / VH), ox = (r.width - VW * sc) / 2, oy = (r.height - VH * sc) / 2;
  return { x: (ev.clientX - r.left - ox) / sc, y: (ev.clientY - r.top - oy) / sc };
}
cv.addEventListener('pointerdown', ev => {
  Audio.init(); const p = toGame(ev);
  for (let i = UI.buttons.length - 1; i >= 0; i--) { const b = UI.buttons[i]; if (p.x >= b.x && p.x <= b.x + b.w && p.y >= b.y && p.y <= b.y + b.h) { b.fn(); return; } }
  if (state === 'title') { SFX.select(); state = 'select'; menuSel = Math.min(save.unlocked, LEVELS.length) - 1; }
  if (state === 'rescue' && rescueT > 60) finishLevel();
});
btnPauseEl.addEventListener('click', () => { Audio.init(); if (state === 'play' || state === 'paused') togglePause(); });
btnMuteEl.addEventListener('click', () => { Audio.init(); toggleMute(); });

// virtual gamepad (touch): sliding d-pad + B (run/kick) + A (jump)
const dpad = document.getElementById('dpad');
function dpadSet(ev) {
  const r = dpad.getBoundingClientRect(), x = (ev.clientX - r.left) / r.width;
  keys.left = x < 0.46; keys.right = x >= 0.54;
  dpad.dataset.dir = keys.left ? 'l' : keys.right ? 'r' : '';
}
dpad.addEventListener('pointerdown', e => { e.preventDefault(); Audio.init(); try { dpad.setPointerCapture(e.pointerId); } catch (x) {} dpadSet(e); });
dpad.addEventListener('pointermove', e => { if (e.buttons || e.pressure > 0) dpadSet(e); });
for (const ev of ['pointerup', 'pointercancel', 'lostpointercapture']) dpad.addEventListener(ev, () => { keys.left = keys.right = false; dpad.dataset.dir = ''; });
for (const b of document.querySelectorAll('#pad [data-k]')) {
  const k = b.dataset.k;
  b.addEventListener('pointerdown', e => { e.preventDefault(); Audio.init(); press(k); b.classList.add('on'); try { b.setPointerCapture(e.pointerId); } catch (x) {} });
  for (const ev of ['pointerup', 'pointercancel', 'lostpointercapture']) b.addEventListener(ev, () => { keys[k] = false; b.classList.remove('on'); });
}
addEventListener('contextmenu', e => e.preventDefault());

// Android back button / Escape: returns true when handled
window.__back = function () {
  if (state === 'play') { togglePause(); return true; }
  if (state === 'paused') { togglePause(); return true; }
  if (state === 'select') { state = 'title'; return true; }
  if (state === 'gameover' || state === 'win' || state === 'complete') { state = 'select'; return true; }
  if (state === 'intro' || state === 'dying' || state === 'flag' || state === 'warp' || state === 'axe') { fade = 0; state = 'select'; return true; }
  if (state === 'rescue') { finishLevel(); return true; }
  return false;
};

function syncUi() {
  document.body.dataset.state = state;
  const playing = state === 'play' || state === 'grow' || state === 'paused' || state === 'dying' || state === 'flag' || state === 'warp' || state === 'axe';
  document.body.classList.toggle('playing', playing);
}

// ---------- boot ----------
window.__sfb = { get state() { return state; }, get P() { return P; }, get L() { return L; }, get enemies() { return enemies; }, get items() { return items; }, get springs() { return springs; }, get fbars() { return fbars; }, get cannons() { return cannons; }, get inRoom() { return inRoom; }, get water() { return water; }, get warpSt() { return warpSt; }, get vines() { return vines; }, get boss() { return boss; }, get areaWarps() { return areaWarps; }, get map() { return map; }, startWarp, get balls() { return balls; }, get score() { return score; }, get coinCount() { return coinCount; }, get save() { return save; }, get plats() { return plats; }, get bossHp() { return boss ? boss.hp : -1; }, get cam() { return cam; }, get time() { return time; }, get lives() { return lives; }, get checkTx() { return checkTx; }, get theme() { return theme; }, dbg, keys, start: i => { newRun(i); introT = 0; }, press, update, draw };
const STEP = 1000 / 60;
let last = performance.now(), acc = 0, started = false;
function loop(now) {
  acc += Math.min(100, now - last); last = now;
  while (acc >= STEP) { update(); acc -= STEP; }
  draw(); syncUi();
  requestAnimationFrame(loop);
}
function boot() {
  if (started) return; started = true;
  lvl = 0; L = S.buildLevel(0); map = L.map; cols = L.cols; theme = 'grass'; enemies = []; coins = []; plats = [];
  P = { x: 0, y: 0, w: 12, h: 28, big: false, face: 1, dist: 0, star: 0, fire: false, inv: 0 };
  last = performance.now(); requestAnimationFrame(loop);
}
const fontsReady = document.fonts && document.fonts.load ? Promise.all([document.fonts.load('10px "Press Start 2P"'), document.fonts.load('800 10px Rubik'), document.fonts.load('800 10px Rubik', 'אבג')]).catch(() => {}) : Promise.resolve();
let imgOk = false; Art.loadImages(() => { imgOk = true; });
Promise.race([fontsReady, new Promise(r => setTimeout(r, 1500))]).then(() => { const w = () => (imgOk ? boot() : setTimeout(w, 30)); w(); });
})();
