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
const save = { unlocked: 1, best: [0, 0, 0, 0, 0, 0] };
try { const s = JSON.parse(localStorage.getItem('sfb-save') || 'null'); if (s) { save.unlocked = Math.max(1, Math.min(LEVELS.length, s.unlocked | 0)); save.best = (s.best || []).concat(save.best).slice(0, LEVELS.length).map(n => n | 0); } } catch (e) {}
function persist() { try { localStorage.setItem('sfb-save', JSON.stringify(save)); } catch (e) {} }

// ---------- state ----------
const COMBO = [100, 200, 400, 500, 800, 1000, 2000, 4000, 8000];
const BUBBLES = ['בוא הנה!', 'תפסו אותו!', 'אי אפשר לעבור!', 'חה חה חה!', 'אני הכי חזק!', 'זה הטריטוריה שלי!', 'לא תעבור!', 'הגעת למקום הלא נכון!'];
const STOMP_LINES = ['איי!', 'לא הוגן!', 'שוב פעם?!', 'אוף!', 'בום!'];
let state = 'title', frame = 0, score = 0, coinCount = 0, lives = 3, time = 300, timeTick = 0;
let lvl = 0, L = null, map = null, cols = 0, theme = 'grass';
let enemies = [], items = [], parts = [], pops = [], bumps = [], coins = [], balls = [], plats = [], weather = [];
let cam = 0, P = null, jumpBuf = 0, coyote = 0, dieT = 0, growT = 0, deathMsg = '', introT = 0, levelScore0 = 0;
let flagY = 0, flagState = null, flagT = 0, castleFlag = 0, bubbleCD = 120, checkTx = -1, shake = 0;
let boss = null, bossActive = false, bossDead = false, bannerT = 0, bannerText = '', menuSel = 0, prevState = 'play';
const keys = { left: false, right: false, jump: false, run: false };
const dbg = { god: false };
const UI = { buttons: [] };

function lvlScore() { return score - levelScore0; }

function startLevel(i, keepCheck) {
  lvl = i; L = S.buildLevel(i); map = L.map; cols = L.cols; theme = L.def.theme;
  if (!keepCheck) checkTx = -1;
  const startTx = checkTx >= 0 ? checkTx : 3;
  const defs = { walk: [16, 22, 0.45], fast: [16, 22, 0.95], spiky: [16, 22, 0.55], fly: [16, 22, 0.55], boss: [44, 52, 0.8] };
  enemies = L.en.filter(e => e.tx > startTx + 8 || e.k === 'boss').map(e => {
    const d = defs[e.k] || defs.walk;
    return { k: e.k, x: e.tx * T, y: (e.row + 1) * T - d[1], w: d[0], h: d[1], vx: -d[2], spd: d[2], vy: 0, state: 'walk', t: (e.tx * 7) % 40, active: false, dead: false, bubble: null, onGround: false, baseY: (e.row + 1) * T - d[1], ph: e.tx };
  });
  boss = null; bossActive = false; bossDead = false;
  if (L.arena) {
    const a = L.arena; boss = { k: 'boss', x: a.bossTx * T, y: 13 * T - 52, w: 44, h: 52, vx: -0.8, spd: 0.8, vy: 0, state: 'walk', t: 0, active: false, dead: false, bubble: null, onGround: false, hp: 3, inv: 0, jt: 0 };
    enemies.push(boss);
  }
  coins = L.coins.filter(([x]) => x > startTx).map(([x, r]) => ({ x: x * T + 3, y: r * T + 1, w: 10, h: 14, taken: false }));
  plats = L.plats.map(p => ({ bx: p.x, by: p.y, x: p.x, y: p.y, w: p.w, axis: p.axis, range: p.range, speed: p.speed, off: Math.min(p.range, (p.phase || 0) * T), dir: 1, dx: 0, dy: 0 }));
  items = []; parts = []; pops = []; bumps = []; balls = [];
  P = { x: startTx * T, y: 0, w: 12, h: 28, vx: 0, vy: 0, big: false, fire: false, star: 0, face: 1, onGround: true, inv: 0, dist: 0, combo: 0, hidden: false, dead: false, ride: null, onIce: false, kickCD: 0 };
  P.y = L.groundRow(startTx) * T - P.h;
  cam = Math.max(0, P.x - 80);
  time = L.def.time; timeTick = 0; jumpBuf = 0; coyote = 0;
  flagY = 3 * T + 8; flagState = null; castleFlag = 0; bubbleCD = 120; shake = 0;
  weather = []; const wn = theme === 'snow' ? 70 : theme === 'lava' ? 34 : theme === 'desert' ? 22 : 0;
  for (let i = 0; i < wn; i++) weather.push({ x: Math.random() * 600, y: Math.random() * VH, vx: theme === 'desert' ? -1.4 - Math.random() : -0.2 - Math.random() * 0.5, vy: theme === 'lava' ? -0.3 - Math.random() * 0.6 : 0.4 + Math.random() * 0.7, r: Math.random() < 0.3 ? 2 : 1 });
  state = 'intro'; introT = 110;
  Audio.music.track = theme; Audio.music.tempoMul = 1;
}
function newRun(i) { score = 0; coinCount = 0; lives = 3; levelScore0 = 0; checkTx = -1; startLevel(i); SFX.go(); }
function retryLevel() { score = levelScore0; lives = 3; coinCount = 0; checkTx = -1; startLevel(lvl); SFX.go(); }
function nextLevel() { levelScore0 = score; startLevel(lvl + 1); SFX.go(); }

function addScore(n, x, y) { score += n; pops.push({ x, y, text: String(n), t: 45 }); }
function addCoin() {
  coinCount++; score += 200; SFX.coin();
  if (coinCount >= 100) { coinCount -= 100; lives++; SFX.oneup(); pops.push({ x: P.x, y: P.y - 8, text: '1UP', t: 60 }); }
}
function buzz(ms) { try { if (navigator.vibrate) navigator.vibrate(ms); } catch (e) {} }

// ---------- tiles & collisions ----------
const isSolid = t => t > 0 && t < 20;
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
function hurt(spike) {
  if (dbg.god) return;
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
function bumpTile(tx, ty) {
  const t = map[ty][tx];
  const used = () => { map[ty][tx] = TILE.USED; bumps.push({ tx, ty, t: 10 }); };
  if (t === TILE.QC) { used(); items.push({ type: 'coinpop', x: tx * T, y: ty * T - 16, vy: -5, t: 26 }); addCoin(); }
  else if (t === TILE.QP) { used(); spawnItem(P.big ? 'ball' : 'bolt', tx, ty); }
  else if (t === TILE.QS) { used(); spawnItem('star', tx, ty); }
  else if (t === TILE.QU) { used(); spawnItem('heart', tx, ty); }
  else if (t === TILE.BRK) {
    if (P.big) {
      map[ty][tx] = 0; score += 50; SFX.brk();
      for (const [vx, vy, ox, oy] of [[-1.2, -5, 0, 0], [1.2, -5, 8, 0], [-1, -3, 0, 8], [1, -3, 8, 8]]) parts.push({ x: tx * T + ox, y: ty * T + oy, vx, vy, t: 80 });
    } else { bumps.push({ tx, ty, t: 10 }); SFX.bump(); }
  } else SFX.bump();
  for (const e of enemies) {
    if (e.state !== 'walk' || !e.active || e.k === 'boss') continue;
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
}

function updatePlatforms() {
  for (const p of plats) {
    const ox = p.x, oy = p.y;
    p.off += p.dir * p.speed; if (p.off >= p.range) { p.off = p.range; p.dir = -1; } else if (p.off <= 0) { p.off = 0; p.dir = 1; }
    if (p.axis === 'x') p.x = p.bx + p.off; else p.y = p.by + p.off;
    p.dx = p.x - ox; p.dy = p.y - oy;
  }
}

function updatePlayer() {
  if (++timeTick >= 30) {
    timeTick = 0; time--;
    if (time === 100) { SFX.hurry(); Audio.music.tempoMul = 0.78; }
    if (time <= 0) { time = 0; die(false); return; }
  }
  if (P.kickCD > 0) P.kickCD--;
  if (P.star > 0 && --P.star === 0) Audio.music.tempoMul = time <= 100 ? 0.78 : 1;
  if (P.ride) { P.x += P.ride.dx; P.y += P.ride.dy; }
  P.onIce = P.onGround && groundTileUnder();
  const max = keys.run ? 2.5 : 1.45;
  const dir = (keys.right ? 1 : 0) - (keys.left ? 1 : 0);
  if (dir) {
    P.face = dir;
    const acc = (P.onGround ? 0.08 : 0.06) * (P.onIce ? 0.32 : 1);
    const skid = P.onGround && !P.onIce && P.vx * dir < 0 ? 0.14 : 0;
    P.vx += dir * (acc + skid);
    if (P.vx * dir > max) P.vx = dir * Math.max(max, Math.abs(P.vx) - acc - 0.04);
  } else if (P.onGround) {
    P.vx *= P.onIce ? 0.975 : 0.84; if (Math.abs(P.vx) < 0.06) P.vx = 0;
  }
  if (jumpBuf > 0) jumpBuf--;
  if (P.onGround) coyote = 6; else if (coyote > 0) coyote--;
  if (jumpBuf > 0 && coyote > 0) { P.vy = -(6.4 + Math.abs(P.vx) * 0.25); jumpBuf = 0; coyote = 0; P.onGround = false; P.ride = null; SFX.jump(); }
  P.vy += (keys.jump && P.vy < 0) ? 0.27 : 0.7;
  if (P.vy > 6.5) P.vy = 6.5;

  P.x += P.vx;
  if (P.x < cam) { P.x = cam; if (P.vx < 0) P.vx = 0; }
  if (collide(P, 'x').hit) P.vx = 0;
  const prevBottom = P.y + P.h;
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
  if (P.onGround) P.combo = 0;
  P.dist += Math.abs(P.vx);
  if (P.inv > 0) P.inv--;
  hazards();
  if (state !== 'play') return;
  if (P.y > VH + 8) { die(true); return; }
  // checkpoint
  for (const c of L.checks) if (c > checkTx && P.x >= c * T) { checkTx = c; pops.push({ x: c * T, y: 13 * T - 40, text: 'CHECKPOINT', t: 70 }); SFX.coin(); }
  // boss arena trigger
  if (L.arena && !bossActive && !bossDead && P.x > L.arena.trigger * T) {
    bossActive = true; boss.active = true;
    for (let r = 5; r <= 12; r++) map[r][L.arena.x0] = TILE.GATE;
    Audio.music.track = 'boss'; bannerText = 'הבוס הגדול!'; bannerT = 120; SFX.boss(); shake = 14;
  }
  if (P.x + P.w >= L.poleTx * T - 2) grabFlag();
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
    boss.spd = 0.8 + (3 - boss.hp) * 0.3; boss.vx = Math.sign(boss.vx || 1) * boss.spd;
    enemies.push({ k: 'walk', x: cam + VW - 24, y: 13 * T - 22, w: 16, h: 22, vx: -0.7, spd: 0.7, vy: 0, state: 'walk', t: 0, active: true, dead: false, bubble: null, onGround: false, baseY: 0, ph: 0 });
  }
  return true;
}
function groundAhead(e) {
  const tx = Math.floor((e.vx > 0 ? e.x + e.w + 2 : e.x - 2) / T), ty = Math.floor((e.y + e.h + 2) / T);
  return solid(tx, ty);
}
function updateEnemies() {
  for (const e of enemies) {
    if (e.dead) continue;
    if (!e.active) { if (e.k !== 'boss' && e.x < cam + VW + 32) e.active = true; else continue; }
    if (e.bubble && --e.bubble.t <= 0) e.bubble = null;
    if (e.state === 'walk') {
      e.t++;
      if (e.k === 'fly') {
        e.x += e.vx; e.y = e.baseY + Math.sin(e.t / 22 + e.ph) * 18;
        if (e.x < cam - 64) e.dead = true; continue;
      }
      e.vy = Math.min(e.vy + (e.k === 'boss' ? 0.5 : 0.45), 7);
      e.x += e.vx; if (collide(e, 'x').hit) e.vx = -e.vx;
      if (e.onGround && (e.k === 'spiky' || e.k === 'fast') && !groundAhead(e)) e.vx = -e.vx;
      e.y += e.vy; e.onGround = false; collide(e, 'y');
      if (e.k === 'boss') {
        if (e.inv > 0) e.inv--;
        if (e.onGround && ++e.jt > 70) { e.jt = 0; e.vy = -7.2; e.vx = (P.x < e.x ? -1 : 1) * e.spd; }
      }
      if (e.y > VH + 16 || (e.k !== 'boss' && e.x < cam - 64)) e.dead = true;
    } else if (e.state === 'flat') { if (--e.t <= 0) e.dead = true; }
    else if (e.state === 'knock') { e.vy += 0.3; e.y += e.vy; e.x += e.vx; if (e.y > VH + 60) e.dead = true; }
  }
  for (let i = 0; i < enemies.length; i++) {
    const a = enemies[i]; if (a.dead || !a.active || a.state !== 'walk' || a.k === 'fly' || a.k === 'boss') continue;
    for (let j = i + 1; j < enemies.length; j++) {
      const b = enemies[j]; if (b.dead || !b.active || b.state !== 'walk' || b.k === 'fly' || b.k === 'boss') continue;
      if (overlap(a, b)) { if (a.x < b.x) { a.vx = -Math.abs(a.vx); b.vx = Math.abs(b.vx); } else { a.vx = Math.abs(a.vx); b.vx = -Math.abs(b.vx); } }
    }
  }
  if (state === 'play') for (const e of enemies) {
    if (e.dead || !e.active || e.state !== 'walk') continue;
    if (!overlap(P, e)) continue;
    if (P.star > 0) { if (e.k === 'boss') { if (hitBoss()) P.vx = -P.face * 2; } else knock(e); continue; }
    const top = P.y + P.h - e.y;
    if (P.vy > 0 && top < (e.k === 'boss' ? 16 : 13) && e.k !== 'spiky') {
      if (e.k === 'boss') { if (!hitBoss()) { /* invulnerable: just bounce */ } P.vy = -6; P.y = e.y - P.h - 1; }
      else {
        e.state = 'flat'; e.t = 40; e.bubble = Math.random() < 0.5 ? { text: STOMP_LINES[(Math.random() * STOMP_LINES.length) | 0], t: 40 } : null;
        addScore(COMBO[Math.min(P.combo, COMBO.length - 1)], e.x, e.y - 4); P.combo++; SFX.stomp(); buzz(15);
        P.vy = keys.jump ? -6 : -3.8;
      }
    } else if (P.inv <= 0) hurt();
  }
  enemies = enemies.filter(e => !e.dead || e === boss);
  if (--bubbleCD <= 0) {
    bubbleCD = 140 + Math.random() * 160;
    const near = enemies.filter(e => e.active && e.state === 'walk' && !e.bubble && e.k !== 'boss' && e.x > cam + 8 && e.x < cam + VW - 24);
    if (near.length) { const e = near[(Math.random() * near.length) | 0]; e.bubble = { text: BUBBLES[(Math.random() * BUBBLES.length) | 0], t: 110 }; }
  }
}

function updateBalls() {
  for (const b of balls) {
    b.vy += 0.35; b.x += b.vx; b.rot += b.vx * 0.12;
    if (collide(b, 'x').hit) b.t = 0;
    b.y += b.vy; b.onGround = false; const r = collide(b, 'y'); if (b.onGround) b.vy = -3.1;
    if (--b.t <= 0 || b.y > VH + 10 || b.x < cam - 20 || b.x > cam + VW + 20) { b.dead = true; continue; }
    for (const e of enemies) {
      if (e.dead || !e.active || e.state !== 'walk' || !overlap(b, e)) continue;
      b.dead = true; if (e.k === 'boss') hitBoss(); else knock(e); break;
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
  if (shake > 0) shake--;
  if (bannerT > 0) bannerT--;
}

function camLimit() { return (L.arena && !bossDead ? (L.arena.x1 + 1) * T : cols * T) - VW; }
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
    if (castleFlag >= 26 && ++flagT > 60) finishLevel();
  }
  updateCamera();
}
function finishLevel() {
  save.best[lvl] = Math.max(save.best[lvl], lvlScore());
  if (lvl + 1 < LEVELS.length) save.unlocked = Math.max(save.unlocked, lvl + 2);
  persist(); state = lvl + 1 >= LEVELS.length ? 'complete' : 'win'; menuSel = 0;
}

function update() {
  frame++;
  if (state === 'play') {
    updatePlatforms(); updatePlayer();
    if (state === 'play' || state === 'flag') { updateEnemies(); updateItems(); updateBalls(); }
    updateFx(); updateCamera();
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
  Audio.music.on = (state === 'play' || state === 'grow' || state === 'intro');
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
  if (P.star > 0) { txt(String(Math.ceil(P.star / 60)), 246, 10, 8, '#ffe14a'); Art.star5(238, 13.5, 5, '#ffe14a'); }
  if (boss && bossActive && !bossDead) { rrect(VW / 2 - 50, 26, 100, 8, 4, '#00000099'); for (let i = 0; i < 3; i++) rrect(VW / 2 - 47 + i * 31, 28, 28, 4, 2, i < boss.hp ? '#ff4a4a' : '#442222'); }
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

function drawWorld() {
  const cx = Math.round(cam), th = theme;
  Art.drawSky(th, cx, frame);
  ctx.save();
  if (shake > 0) ctx.translate(Math.round((Math.random() - 0.5) * shake * 0.6), Math.round((Math.random() - 0.5) * shake * 0.6));
  ctx.translate(-cx, 0);
  if (th === 'lava') Art.drawLavaBand(frame);
  if (cx + VW > L.castleX - 40) Art.drawCastle(L.castleX, th, castleFlag);
  if (cx + VW > L.poleTx * T - 30) Art.drawPole(L.poleTx * T + 7, flagY, frame);
  for (const c of L.checks) if (c * T > cx - 32 && c * T < cx + VW) Art.drawCheck(c * T, checkTx >= c, frame);
  const tx0 = Math.max(0, Math.floor(cx / T)), tx1 = Math.min(cols - 1, tx0 + Math.ceil(VW / T) + 1);
  for (let ty = 0; ty < ROWS; ty++) for (let tx = tx0; tx <= tx1; tx++) {
    const t = map[ty][tx]; if (!t) continue;
    let oy = 0; for (const b of bumps) if (b.tx === tx && b.ty === ty) oy = -Math.round(Math.sin((10 - b.t) / 10 * Math.PI) * 5);
    const above = ty > 0 && isSolid(map[ty - 1][tx]) && map[ty - 1][tx] !== TILE.PTL;
    Art.drawTile(t, tx * T, ty * T + oy, ty, tx, th, frame, above);
  }
  for (const p of plats) if (p.x + p.w > cx && p.x < cx + VW) Art.drawPlat(p);
  const ph = (frame >> 3) & 3;
  for (const c of coins) if (!c.taken && c.x > cx - 16 && c.x < cx + VW) Art.drawCoin(c.x - 3, c.y - 1, ph);
  for (const it of items) { if (it.type === 'coinpop') Art.drawCoin(Math.round(it.x), Math.round(it.y), (frame >> 2) & 3); else Art.drawItem(it, frame); }
  for (const e of enemies) { if (e.dead && e !== boss) continue; if (!e.active && e.x > cx + VW) continue; if (e === boss && boss.inv > 0 && (frame >> 2) & 1 && boss.state === 'walk') continue; Art.drawEnemy(e, frame); }
  for (const b of balls) Art.drawBall(Math.round(b.x + 4), Math.round(b.y + 4), 4, b.rot);
  if (P && !P.hidden && !(P.inv > 0 && (frame >> 2) % 2)) {
    let mode = 'stand';
    if (P.dead) mode = 'dead'; else if (state === 'flag' && flagState !== 'walk') mode = 'jump'; else if (!P.onGround) mode = 'jump'; else if (Math.abs(P.vx) > 0.1) mode = 'walk';
    Art.drawHero(P, mode, Math.floor(P.dist / 5), frame);
  }
  for (const p of parts) { Art.R(Math.round(p.x), Math.round(p.y), 6, 6, '#1a0f0c'); Art.R(Math.round(p.x) + 1, Math.round(p.y) + 1, 4, 4, Art.THEMES[th].br[0]); }
  ctx.restore();
  for (const w of weather) { ctx.fillStyle = th === 'lava' ? (w.r > 1 ? '#ffb02e' : '#ff6a2a') : th === 'desert' ? '#f5d9a0aa' : '#ffffffdd'; ctx.fillRect(Math.round(w.x % (VW + 8)), Math.round(w.y), w.r, w.r); }
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

function drawSelect() {
  Art.drawSky('grass', cam, frame);
  rrect(0, 0, VW, VH, 0, '#0a0614b0');
  txt('בחרו שלב', VW / 2, 10, 18, '#ffe14a', 'center', HEB);
  const cw = 104, ch = 70, gx = (VW - (cw * 3 + 16)) / 2, gy = 42;
  for (let i = 0; i < LEVELS.length; i++) {
    const c = i % 3, r = (i / 3) | 0, x = gx + c * (cw + 8), y = gy + r * (ch + 10), lv = LEVELS[i], open = i < save.unlocked, sel = menuSel === i;
    const th = Art.THEMES[lv.theme];
    UI.buttons.push({ x, y, w: cw, h: ch, fn: () => { menuSel = i; if (open) { SFX.select(); newRun(i); } else SFX.bump(); } });
    rrect(x, y, cw, ch, 7, '#000000aa'); const g = ctx.createLinearGradient(0, y, 0, y + ch); g.addColorStop(0, th.sky[0]); g.addColorStop(1, th.sky[1]);
    rrect(x + 2, y + 2, cw - 4, ch - 4, 6, g, sel ? '#fff' : '#1a0f0c', sel ? 2.5 : 1.5);
    rrect(x + 2, y + ch - 20, cw - 4, 18, 6, th.g.body); ctx.fillStyle = th.g.top; ctx.fillRect(x + 3, y + ch - 20, cw - 6, 5);
    if (!open) { rrect(x + 2, y + 2, cw - 4, ch - 4, 6, '#000000a0'); ctx.fillStyle = '#ddd'; ctx.fillRect(x + cw / 2 - 7, y + 26, 14, 11); ctx.strokeStyle = '#ddd'; ctx.lineWidth = 2.5; ctx.beginPath(); ctx.arc(x + cw / 2, y + 26, 5, Math.PI, 0); ctx.stroke(); }
    txt(String(lv.id), x + 8, y + 7, 14, '#fff');
    txt(lv.name, x + cw / 2, y + ch - 18, 10, open ? '#fff' : '#bbb', 'center', HEB);
    if (open && save.best[i] > 0) txt(String(save.best[i]), x + cw - 8, y + 9, 6, '#ffe14a', 'right');
    if (open && i + 1 < save.unlocked) { Art.star5(x + cw - 14, y + 34, 7, '#1a0f0c'); Art.star5(x + cw - 14, y + 34, 5.6, '#ffe14a'); }
  }
  button(10, VH - 30, 70, 22, 'חזרה', () => { SFX.select(); state = 'title'; }, false, '#cfd8ee');
  txt('SUPER FACE BROS.', VW - 8, VH - 20, 7, '#fff', 'right');
}

function drawIntro() {
  rrect(0, 0, VW, VH, 0, '#0a0614');
  const th = Art.THEMES[theme], g = ctx.createLinearGradient(0, 0, 0, VH); g.addColorStop(0, th.sky[0]); g.addColorStop(1, th.sky[1]); ctx.globalAlpha = 0.35; ctx.fillStyle = g; ctx.fillRect(0, 0, VW, VH); ctx.globalAlpha = 1;
  txt('שלב ' + (lvl + 1), VW / 2, 62, 24, '#ffe14a', 'center', HEB);
  txt(L.def.name, VW / 2, 98, 20, '#fff', 'center', HEB);
  txt(L.def.en, VW / 2, 128, 8, '#c9bcc4', 'center');
  Art.head(Art.IMG.hero, VW / 2 - 24, 164, 28, 0); txt('x ' + lives, VW / 2 + 2, 158, 14, '#fff');
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
  if (bannerT > 0 && (state === 'play')) { const a = Math.min(1, bannerT / 20); ctx.globalAlpha = a; txt(bannerText, W / 2, 70, 22, '#ff5a4a', 'center', HEB); ctx.globalAlpha = 1; }
}

function draw() {
  ctx.setTransform(SC, 0, 0, SC, 0, 0); ctx.imageSmoothingEnabled = false;
  UI.buttons = [];
  if (state === 'title') drawTitle();
  else if (state === 'select') drawSelect();
  else if (state === 'intro') drawIntro();
  else { drawWorld(); drawHUD(); drawOverlay(); }
}

// ---------- input ----------
const KEYMAP = { ArrowLeft: 'left', KeyA: 'left', ArrowRight: 'right', KeyD: 'right', ArrowUp: 'jump', KeyW: 'jump', Space: 'jump', KeyZ: 'jump', KeyK: 'jump', ShiftLeft: 'run', ShiftRight: 'run', KeyX: 'run', KeyJ: 'run' };
const btnPauseEl = document.getElementById('btnPause'), btnMuteEl = document.getElementById('btnMute');
function menuCount() { return { paused: 3, gameover: 2, win: 2, complete: 1 }[state] || 0; }
function menuActivate() {
  if (state === 'title') { UI.buttons[0] && UI.buttons[0].fn(); return true; }
  if (state === 'select') { const lv = menuSel; if (lv < save.unlocked) { SFX.select(); newRun(lv); } return true; }
  const n = menuCount(); if (!n) return false;
  const b = UI.buttons[menuSel]; if (b) b.fn(); return true;
}
function menuMove(dx, dy) {
  if (state === 'select') { let m = menuSel + dx + dy * 3; if (m >= 0 && m < LEVELS.length) { menuSel = m; SFX.select(); } return true; }
  const n = menuCount(); if (!n) return false; menuSel = (menuSel + dx + dy + n) % n; SFX.select(); return true;
}
function togglePause() {
  if (state === 'play') { state = 'paused'; menuSel = 0; Audio.music.on = false; }
  else if (state === 'paused') state = 'play';
  btnPauseEl.textContent = state === 'paused' ? '▶' : '⏸';
}
function toggleMute() { Audio.setMuted(!Audio.muted); btnMuteEl.textContent = Audio.muted ? '🔇' : '🔊'; }
function press(k) {
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
  if (state === 'intro' || state === 'dying' || state === 'flag') { state = 'select'; return true; }
  return false;
};

function syncUi() {
  document.body.dataset.state = state;
  const playing = state === 'play' || state === 'grow' || state === 'paused' || state === 'dying' || state === 'flag';
  document.body.classList.toggle('playing', playing);
}

// ---------- boot ----------
window.__sfb = { get state() { return state; }, get P() { return P; }, get L() { return L; }, get enemies() { return enemies; }, get items() { return items; }, get balls() { return balls; }, get score() { return score; }, get coinCount() { return coinCount; }, get save() { return save; }, get plats() { return plats; }, get bossHp() { return boss ? boss.hp : -1; }, get cam() { return cam; }, get time() { return time; }, get lives() { return lives; }, get checkTx() { return checkTx; }, get theme() { return theme; }, dbg, keys, start: i => { newRun(i); introT = 0; }, press, update, draw };
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
