// All drawing code: themes, tiles, parallax backgrounds, characters (photo heads on pixel bodies), items.
(function () {
'use strict';
const S = window.SFB, T = S.T, TILE = S.TILE;
let C = null, VW = 400;
const VH = 240;

const THEMES = {
  grass: { sky: ['#3d9bff', '#c4ecff'], g: { top: '#5fd35f', topd: '#2f9a45', body: '#b8693a', bodyd: '#7c4222', bodyl: '#dc9462' }, br: ['#c8652f', '#4a1e0c', '#eb9a62'], hd: ['#a0603a', '#5a2c14', '#d99566'], pipe: ['#3fbf4a', '#1f7a2c', '#8df08a'], spk: ['#7a5a3a', '#cdb08a'], castle: ['#b8532a', '#4a1e0c'] },
  desert: { sky: ['#ff8a3d', '#ffe6a8'], g: { top: '#f3d28a', topd: '#d9a85a', body: '#e0b366', bodyd: '#a97c35', bodyl: '#f8dfa0' }, br: ['#d9a04e', '#6e4413', '#f2c27a'], hd: ['#b97c3a', '#5f3a14', '#e0a860'], pipe: ['#d4673f', '#8a3418', '#f59a72'], spk: ['#3aa04a', '#8bdc7a'], castle: ['#d9a04e', '#6e4413'] },
  cave: { sky: ['#14092b', '#3c2268'], g: { top: '#7d5fc4', topd: '#4a3585', body: '#41306f', bodyd: '#261a47', bodyl: '#5a46a0' }, br: ['#5e4c96', '#241a45', '#8f7bd0'], hd: ['#4d3d80', '#1d1438', '#7a66b8'], pipe: ['#2fb3aa', '#17706a', '#8ff0e8'], spk: ['#58e3ff', '#d5fbff'], castle: ['#5e4c96', '#241a45'] },
  snow: { sky: ['#8cc4ff', '#f2fbff'], g: { top: '#ffffff', topd: '#cfe6f7', body: '#8ec5e8', bodyd: '#5e9ec8', bodyl: '#c6e6fa' }, br: ['#9fd3f2', '#3f7ba0', '#e0f5ff'], hd: ['#7fb4d6', '#35688c', '#bfe4f8'], pipe: ['#3d9ec7', '#1d5f80', '#9fe3ff'], spk: ['#cfefff', '#ffffff'], castle: ['#9fd3f2', '#3f7ba0'] },
  sky: { sky: ['#2f8dff', '#bfe6ff'], g: { top: '#ffffff', topd: '#d6e6ff', body: '#f4f8ff', bodyd: '#b9ccf0', bodyl: '#ffffff' }, br: ['#f2b84a', '#7a4e0c', '#ffe08a'], hd: ['#d8def0', '#7886b0', '#ffffff'], pipe: ['#ff7aa8', '#b73a69', '#ffc1d8'], spk: ['#ff7aa8', '#ffc1d8'], castle: ['#f2b84a', '#7a4e0c'] },
  lava: { sky: ['#1c0610', '#8a2418'], g: { top: '#ff7a33', topd: '#c9431a', body: '#43302f', bodyd: '#241a1c', bodyl: '#6a4a46' }, br: ['#6a3a3a', '#241012', '#9a5a54'], hd: ['#4a3a44', '#1c1418', '#7a6670'], pipe: ['#5a5f6a', '#2a2d34', '#a0a8b8'], spk: ['#ff5a2a', '#ffd27a'], castle: ['#6a3a3a', '#241012'] },
};

function bind(ctx) { C = ctx; }
function setVW(w) { VW = w; }
function R(x, y, w, h, c) { C.fillStyle = c; C.fillRect(x, y, w, h); }
function blob(cx, cy, r, c) {
  C.fillStyle = c;
  for (let dy = -r; dy <= r; dy++) { const hw = Math.round(Math.sqrt(r * r - dy * dy)); C.fillRect(cx - hw, cy + dy, hw * 2, 1); }
}

// ---------- images ----------
const IMG = { hero: null, enemy: null, enemyRed: null, ready: false };
function tint(img, color) {
  const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
  const g = c.getContext('2d'); g.drawImage(img, 0, 0); g.globalCompositeOperation = 'source-atop'; g.fillStyle = color; g.fillRect(0, 0, c.width, c.height);
  return c;
}
function loadImages(cb) {
  let n = 0;
  const done = () => { if (++n === 2) { IMG.enemyRed = tint(IMG.enemy, 'rgba(255,30,30,.42)'); IMG.enemyBlue = tint(IMG.enemy, 'rgba(60,120,255,.35)'); IMG.ready = true; cb(); } };
  for (const k of ['hero', 'enemy']) { const im = new Image(); im.onload = done; im.onerror = done; im.src = 'assets/' + k + '.png'; IMG[k] = im; }
}

// draw an oval face centred at (cx,cy) with height h; keeps aspect; dark sticker outline
function head(img, cx, cy, h, rot, outline) {
  if (!img || !img.width) return;
  const w = h * img.width / img.height;
  C.save(); C.translate(cx, cy); if (rot) C.rotate(rot);
  C.imageSmoothingEnabled = true; C.imageSmoothingQuality = 'high';
  C.drawImage(img, -w / 2, -h / 2, w, h);
  C.imageSmoothingEnabled = false;
  C.strokeStyle = outline || '#1a0f0c'; C.lineWidth = 1.1; C.beginPath(); C.ellipse(0, 0, w / 2, h / 2, 0, 0, Math.PI * 2); C.stroke();
  C.restore();
}

// ---------- tiles ----------
function drawTile(t, x, y, ty, tx, th, frame, above) {
  const T_ = THEMES[th], g = T_.g;
  switch (t) {
    case TILE.GND: {
      R(x, y, 16, 16, g.body);
      R(x, y + 15, 16, 1, g.bodyd); R(x + 15, y, 1, 16, g.bodyd);
      R(x + 3, y + 6, 4, 2, g.bodyd); R(x + 9, y + 11, 4, 2, g.bodyd); R(x + 10, y + 4, 2, 1, g.bodyl); R(x + 2, y + 12, 2, 1, g.bodyl);
      if (!above) { R(x, y, 16, 5, g.top); R(x, y + 5, 16, 1, g.topd); for (let i = 0; i < 16; i += 4) R(x + i, y + 5, 2, 2, g.topd); R(x, y, 16, 1, '#ffffff33'); }
      break;
    }
    case TILE.ICE: {
      R(x, y, 16, 16, '#9fdcf7'); R(x, y, 16, 1, '#ffffff'); R(x, y, 1, 16, '#d8f4ff'); R(x + 15, y, 1, 16, '#5fa8d0'); R(x, y + 15, 16, 1, '#5fa8d0');
      R(x + 3, y + 4, 5, 1, '#d8f4ff'); R(x + 4, y + 5, 1, 4, '#d8f4ff'); R(x + 9, y + 10, 4, 1, '#7dbfe3');
      if (!above) { R(x, y, 16, 3, '#ffffff'); R(x + 2, y + 3, 4, 2, '#ffffff'); R(x + 10, y + 3, 3, 1, '#ffffff'); }
      break;
    }
    case TILE.BRK: {
      const b = T_.br; R(x, y, 16, 16, b[0]); R(x, y, 16, 1, b[2]);
      for (let i = 0; i < 4; i++) { R(x, y + i * 4 + 3, 16, 1, b[1]); const o = i % 2 ? 3 : 7; R(x + o, y + i * 4, 1, 3, b[1]); R(x + o + 8, y + i * 4, 1, 3, b[1]); }
      break;
    }
    case TILE.QC: case TILE.QP: case TILE.QS: case TILE.QU: {
      const ph = ((frame >> 3) % 6) < 3, base = ph ? '#ffb02e' : '#e8901a';
      R(x, y, 16, 16, '#4a2208'); R(x + 1, y + 1, 14, 14, base); R(x + 1, y + 1, 14, 1, '#ffe29a'); R(x + 1, y + 1, 1, 14, '#ffe29a');
      R(x + 1, y + 14, 14, 1, '#b8620f'); R(x + 14, y + 1, 1, 14, '#b8620f');
      for (const [a, b] of [[3, 3], [12, 3], [3, 12], [12, 12]]) R(x + a, y + b, 1, 1, '#7a3d0e');
      const col = t === TILE.QU ? '#2fae4a' : t === TILE.QS ? '#ff5aa8' : '#7a3d0e';
      glyph(QG, x + 6, y + 5, col); glyph(QG, x + 5, y + 4, '#fff3d6');
      break;
    }
    case TILE.USED: R(x, y, 16, 16, '#3a2010'); R(x + 1, y + 1, 14, 14, '#8a5a35'); for (const [a, b] of [[3, 3], [12, 3], [3, 12], [12, 12]]) R(x + a, y + b, 1, 1, '#3a2010'); break;
    case TILE.HARD: {
      const h = T_.hd; R(x, y, 16, 16, h[1]); R(x + 1, y + 1, 14, 14, h[0]); R(x + 1, y + 1, 14, 2, h[2]); R(x + 1, y + 1, 2, 14, h[2]); R(x + 4, y + 4, 8, 8, h[1] + 'aa'); R(x + 5, y + 5, 6, 6, h[0]);
      break;
    }
    case TILE.GATE: R(x, y, 16, 16, '#2a1a1a'); R(x + 1, y + 1, 14, 14, '#7a2a2a'); R(x + 3, y + 3, 10, 10, '#a83a3a'); R(x + 7, y + 3, 2, 10, '#2a1a1a'); break;
    case TILE.CLOUD: {
      R(x, y, 16, 16, '#e4eeff'); R(x, y + 14, 16, 2, '#b9ccf0'); R(x + 15, y, 1, 16, '#c9d9f7');
      if (!above) { R(x, y, 16, 4, '#ffffff'); blob(x + 3, y + 2, 3, '#fff'); blob(x + 11, y + 1, 4, '#fff'); }
      break;
    }
    case TILE.PTL: { const p = T_.pipe; R(x, y, 16, 16, p[1]); R(x + 1, y + 1, 15, 14, p[0]); R(x + 3, y + 1, 3, 14, p[2]); R(x + 11, y + 1, 3, 14, p[1] + 'cc'); break; }
    case TILE.PTR: { const p = T_.pipe; R(x, y, 16, 16, p[1]); R(x, y + 1, 15, 14, p[0]); R(x + 9, y + 1, 3, 14, p[1] + 'cc'); R(x + 13, y + 1, 1, 14, p[1]); break; }
    case TILE.PL: { const p = T_.pipe; R(x + 2, y, 14, 16, p[1]); R(x + 3, y, 13, 16, p[0]); R(x + 5, y, 3, 16, p[2]); R(x + 12, y, 3, 16, p[1] + 'cc'); break; }
    case TILE.PR: { const p = T_.pipe; R(x, y, 14, 16, p[1]); R(x, y, 13, 16, p[0]); R(x + 7, y, 3, 16, p[1] + 'cc'); R(x + 11, y, 1, 16, p[1]); break; }
    case TILE.SPK: drawSpikes(x, y, th, frame); break;
  }
}
const QG = ['.###.', '#...#', '....#', '..##.', '..#..', '.....', '..#..'];
function glyph(g, x, y, c) { for (let r = 0; r < g.length; r++) for (let i = 0; i < g[r].length; i++) if (g[r][i] === '#') R(x + i, y + r, 1, 1, c); }

function drawSpikes(x, y, th, frame) {
  const s = THEMES[th].spk;
  if (th === 'desert') { // cactus
    R(x + 6, y + 3, 4, 13, '#2f8a3c'); R(x + 7, y + 3, 1, 13, s[1]); R(x + 2, y + 7, 4, 2, '#2f8a3c'); R(x + 2, y + 4, 2, 5, '#2f8a3c'); R(x + 10, y + 9, 4, 2, '#2f8a3c'); R(x + 12, y + 6, 2, 5, '#2f8a3c');
    R(x + 8, y + 5, 1, 1, '#fff'); R(x + 3, y + 5, 1, 1, '#fff'); R(x + 12, y + 8, 1, 1, '#fff');
    return;
  }
  for (let i = 0; i < 2; i++) {
    const bx = x + i * 8;
    C.fillStyle = '#111'; C.beginPath(); C.moveTo(bx - 0.5, y + 16); C.lineTo(bx + 4, y + 3.5); C.lineTo(bx + 8.5, y + 16); C.fill();
    C.fillStyle = s[0]; C.beginPath(); C.moveTo(bx + 0.5, y + 16); C.lineTo(bx + 4, y + 5); C.lineTo(bx + 7.5, y + 16); C.fill();
    C.fillStyle = s[1]; C.beginPath(); C.moveTo(bx + 2.5, y + 16); C.lineTo(bx + 4, y + 7); C.lineTo(bx + 4.8, y + 16); C.fill();
  }
  if (th === 'lava' && (frame >> 3) % 2) R(x + 4, y + 4, 1, 1, '#fff7c0');
}

// ---------- background ----------
function ridge(cx, f, base, amp, freq, col, sharp, ph) {
  C.fillStyle = col; C.beginPath(); C.moveTo(0, VH);
  for (let x = 0; x <= VW + 4; x += 4) {
    const wx = (x + cx * f) * freq + (ph || 0);
    let h = Math.sin(wx) * 0.5 + Math.sin(wx * 2.3 + 1.7) * 0.3 + Math.sin(wx * 4.1 + 0.4) * 0.2;
    h = sharp ? 1 - Math.abs(Math.sin(wx * 0.9 + 1) * 0.75 + Math.sin(wx * 2.1) * 0.25) : h * 0.5 + 0.5;
    C.lineTo(x, base - amp * h);
  }
  C.lineTo(VW + 4, VH); C.closePath(); C.fill();
}
function cloud(x, y, k, c1, c2) { blob(x, y + 2, 9 * k, c2); blob(x + 13 * k, y - 3 * k, 11 * k, c2); blob(x + 27 * k, y + 2, 9 * k, c2); blob(x, y, 9 * k, c1); blob(x + 13 * k, y - 5 * k, 11 * k, c1); blob(x + 27 * k, y, 9 * k, c1); }
function tile(cx, f, period, fn) {
  const o = (cx * f) % period;
  for (let k = -1; k <= Math.ceil(VW / period) + 1; k++) fn(k * period - o, k);
}

function drawSky(th, cx, frame) {
  const t = THEMES[th], gr = C.createLinearGradient(0, 0, 0, VH);
  gr.addColorStop(0, t.sky[0]); gr.addColorStop(1, t.sky[1]);
  C.fillStyle = gr; C.fillRect(0, 0, VW, VH);
  if (th === 'grass') {
    ridge(cx, 0.12, 190, 60, 0.012, '#9bd8f5', true, 2);
    ridge(cx, 0.3, 210, 40, 0.02, '#5cc06a');
    tile(cx, 0.22, 380, (x, k) => { cloud(x + 40, 40 + (k & 1) * 18, 1, '#fff', '#c9defb'); cloud(x + 230, 26, 0.8, '#fff', '#c9defb'); });
    ridge(cx, 0.5, 224, 30, 0.03, '#3da356', false, 5);
  } else if (th === 'desert') {
    blob(VW - 70, 52, 26, '#fff3b0'); blob(VW - 70, 52, 20, '#ffd54a');
    ridge(cx, 0.1, 190, 50, 0.01, '#e8913f', true, 1);
    ridge(cx, 0.25, 214, 28, 0.018, '#e0a44f');
    tile(cx, 0.45, 220, (x, k) => { const hh = 22 + (k & 3) * 6; R(x + 40, 208 - hh, 5, hh, '#b97d3a'); R(x + 33, 208 - hh + 7, 4, 3, '#b97d3a'); R(x + 33, 208 - hh + 3, 3, 7, '#b97d3a'); R(x + 48, 208 - hh + 10, 4, 3, '#b97d3a'); R(x + 51, 208 - hh + 5, 3, 8, '#b97d3a'); });
    ridge(cx, 0.55, 226, 20, 0.025, '#cf9440', false, 4);
  } else if (th === 'cave') {
    ridge(cx, 0.15, 200, 90, 0.011, '#2b1950', true, 3);
    ridge(cx, 0.35, 220, 50, 0.02, '#201240', true, 1);
    tile(cx, 0.4, 150, (x, k) => { const hh = 18 + ((k * 7) & 3) * 10; C.fillStyle = '#16092e'; C.beginPath(); C.moveTo(x + 10, 0); C.lineTo(x + 30, 0); C.lineTo(x + 20, hh); C.fill(); });
    tile(cx, 0.55, 190, (x, k) => { const c = ['#58e3ff', '#d77dff', '#7dffb0'][k & 1 ? 1 : (k & 2 ? 2 : 0)]; C.fillStyle = c; C.globalAlpha = 0.55 + 0.25 * Math.sin(frame / 20 + k); C.beginPath(); C.moveTo(x + 60, 208); C.lineTo(x + 66, 188); C.lineTo(x + 72, 208); C.fill(); C.beginPath(); C.moveTo(x + 70, 208); C.lineTo(x + 76, 196); C.lineTo(x + 80, 208); C.fill(); C.globalAlpha = 1; });
  } else if (th === 'snow') {
    ridge(cx, 0.1, 180, 90, 0.009, '#a9cdee', true, 2);
    ridge(cx, 0.1, 180, 90, 0.009, '#ffffff22', true, 2);
    ridge(cx, 0.25, 205, 55, 0.016, '#d6ecfb', true, 5);
    tile(cx, 0.22, 400, (x, k) => { cloud(x + 60, 36 + (k & 1) * 14, 0.9, '#fff', '#dcecff'); });
    tile(cx, 0.5, 120, (x, k) => { const hh = 26 + ((k * 5) & 3) * 5; C.fillStyle = '#5a8fa8'; C.beginPath(); C.moveTo(x + 20, 208); C.lineTo(x + 30, 208 - hh); C.lineTo(x + 40, 208); C.fill(); C.fillStyle = '#fff'; C.beginPath(); C.moveTo(x + 25, 208 - hh * 0.6); C.lineTo(x + 30, 208 - hh); C.lineTo(x + 35, 208 - hh * 0.6); C.fill(); });
    ridge(cx, 0.6, 226, 16, 0.03, '#e8f6ff', false, 7);
  } else if (th === 'sky') {
    blob(70, 56, 24, '#fff7c0'); blob(70, 56, 17, '#ffe35a');
    tile(cx, 0.15, 500, (x, k) => { cloud(x + 80, 150 + (k & 1) * 20, 1.8, '#ffffffcc', '#d8eaffcc'); cloud(x + 330, 90, 1.4, '#ffffffcc', '#d8eaffcc'); });
    tile(cx, 0.3, 330, (x, k) => { cloud(x + 30, 40 + (k & 1) * 22, 1.1, '#fff', '#cfe3ff'); cloud(x + 200, 180, 1.5, '#ffffffdd', '#cfe3ffdd'); });
    tile(cx, 0.5, 600, (x, k) => { cloud(x + 140, 215, 2.4, '#ffffff', '#d3e5ff'); });
  } else if (th === 'lava') {
    const glow = C.createRadialGradient(VW * 0.7, 225, 10, VW * 0.7, 225, 190); glow.addColorStop(0, '#ff7a2a99'); glow.addColorStop(1, '#ff7a2a00'); C.fillStyle = glow; C.fillRect(0, 0, VW, VH);
    ridge(cx, 0.1, 190, 100, 0.008, '#3a0c14', true, 4);
    tile(cx, 0.22, 360, (x, k) => { R(x + 60, 118, 70, 90, '#2a0a12'); for (let i = 0; i < 4; i++) R(x + 60 + i * 20, 108, 12, 10, '#2a0a12'); R(x + 80, 70, 30, 48, '#2a0a12'); for (let i = 0; i < 3; i++) R(x + 80 + i * 12, 62, 8, 8, '#2a0a12'); R(x + 90, 130, 8, 14, '#ffb24a'); R(x + 108, 130, 6, 12, '#ffb24a'); });
    ridge(cx, 0.4, 218, 40, 0.017, '#240810', true, 1);
    ridge(cx, 0.6, 232, 16, 0.03, '#1a050b', false, 2);
  }
}

function drawLavaBand(frame) {
  const y = 14 * T + 2;
  C.fillStyle = '#ff4a1a'; C.fillRect(0, y, VW, VH - y);
  C.fillStyle = '#ffb02e';
  for (let x = 0; x < VW; x += 8) { const h = 2 + Math.round(Math.sin((x + frame) / 9) * 1.5); C.fillRect(x, y - 0 + 0, 8, h + 1); }
  C.fillStyle = '#fff2a0'; for (let x = 4; x < VW; x += 26) C.fillRect(x + ((frame >> 2) % 6), y + 4, 4, 1);
}

// ---------- characters ----------
function hurtBlink(P, frame) { return P.inv > 0 && ((frame >> 2) & 1); }

function drawHero(P, mode, step, frame, opts) {
  if (!IMG.ready) return;
  const s = P.big ? 36 / 28 : 1;
  const cx = P.x + P.w / 2, fy = P.y + P.h;
  const walk = mode === 'walk', jump = mode === 'jump', dead = mode === 'dead';
  C.save(); C.translate(Math.round(cx), Math.round(fy)); C.scale(P.face * s, s);
  const sw = walk ? (step % 2 ? 1 : -1) : 0;
  const shirt = P.fire ? '#f5f5f5' : '#e63946', shirtd = P.fire ? '#c9c9c9' : '#a52530';
  if (P.star) { C.shadowColor = 'hsl(' + ((frame * 12) % 360) + ',100%,60%)'; C.shadowBlur = 6; }
  const l1 = walk ? sw * 1.6 : jump ? -1.6 : 0, l2 = walk ? -sw * 1.6 : jump ? 1.6 : 0;
  R(-4.5 + l1, -4.5, 3.2, 3.6, '#26468f'); R(1.3 + l2, -4.5, 3.2, 3.6, '#26468f');
  R(-5.8 + l1, -1.7, 5, 1.9, '#1a0f0c'); R(0.8 + l2, -1.7, 5, 1.9, '#1a0f0c'); R(-5.3 + l1, -1.5, 4, 1.2, '#f5f5f5'); R(1.3 + l2, -1.5, 4, 1.2, '#f5f5f5');
  R(-4.8, -11.5, 9.6, 7.5, shirt); R(-4.8, -5.6, 9.6, 1.6, shirtd);
  if (P.fire) { C.fillStyle = '#222'; C.beginPath(); C.arc(0, -8, 1.8, 0, 7); C.fill(); }
  const up = jump ? -4 : 0, as = walk ? sw * 1.6 : 0;
  R(-7.4, -11 + up + as, 2.6, 5.5, '#f1c18f'); R(4.8, -11 + up - as, 2.6, 5.5, '#f1c18f');
  C.shadowBlur = 0;
  const bob = walk ? Math.abs(Math.sin(P.dist / 4)) * -1.2 : 0;
  head(IMG.hero, 0, -17 + bob + (dead ? -2 : 0), 22, dead ? frame / 5 : (walk ? sw * 0.06 : jump ? -0.1 : 0), P.star ? 'hsl(' + ((frame * 14) % 360) + ',100%,55%)' : null);
  C.restore();
}

function drawEnemy(e, frame) {
  if (!IMG.ready) return;
  const k = e.k, big = k === 'boss';
  const w = e.w, h = e.h, cx = e.x + w / 2, fy = e.y + h;
  const img = (k === 'fast' || (big && e.hp <= 2)) ? IMG.enemyRed : IMG.enemy;
  const sc = big ? 2.9 : 1;
  C.save(); C.translate(Math.round(cx), Math.round(fy));
  if (e.state === 'knock') { C.rotate(Math.PI); C.translate(0, h / 2 * 0); }
  const flat = e.state === 'flat';
  if (flat) C.scale(1, 0.4);
  const wd = e.t / 8 | 0, sw = (wd % 2) ? 1 : -1;
  if (k === 'fly') { // wings
    const fl = Math.sin(frame / 2.2) * 0.7;
    for (const sd of [-1, 1]) { C.save(); C.translate(sd * 7, -15); C.rotate(sd * (0.3 + fl * 0.5)); C.fillStyle = '#1a0f0c'; C.beginPath(); C.ellipse(sd * 5, 0, 7, 3.4, 0, 0, 7); C.fill(); C.fillStyle = '#fff'; C.beginPath(); C.ellipse(sd * 5, 0, 6, 2.6, 0, 0, 7); C.fill(); C.restore(); }
  }
  if (!big) {
    if (k !== 'fly') { R(-5 + (e.state === 'walk' ? sw : 0), -2.4, 4.4, 2.4, '#27211e'); R(0.6 - (e.state === 'walk' ? sw : 0), -2.4, 4.4, 2.4, '#27211e'); }
    const bob = e.state === 'walk' && k !== 'fly' ? Math.abs(Math.sin(e.t / 5)) * -1.2 : 0;
    head(img, 0, -12.5 + bob, 21, e.state === 'walk' ? sw * 0.08 : 0);
    if (k === 'fast' || k === 'spiky') { C.strokeStyle = '#1a0f0c'; C.lineWidth = 1.4; C.beginPath(); C.moveTo(-6, -18.4); C.lineTo(-1.6, -15.8); C.moveTo(6, -18.4); C.lineTo(1.6, -15.8); C.stroke(); }
    if (k === 'spiky') { // helmet
      C.fillStyle = '#1a0f0c'; C.beginPath(); C.ellipse(0, -21.5, 8.2, 5.6, 0, Math.PI, 0); C.fill();
      C.fillStyle = '#8f98ad'; C.beginPath(); C.ellipse(0, -21.5, 7.3, 4.8, 0, Math.PI, 0); C.fill();
      C.fillStyle = '#dfe6f2';
      for (let i = -2; i <= 2; i++) { C.beginPath(); C.moveTo(i * 2.9 - 1.5, -24); C.lineTo(i * 2.9, -29); C.lineTo(i * 2.9 + 1.5, -24); C.fill(); }
    }
  } else {
    R(-16, -6, 12, 6, '#27211e'); R(4, -6, 12, 6, '#27211e');
    head(img, 0, -29, 50, e.state === 'walk' ? sw * 0.05 : 0, '#1a0f0c');
    C.save(); C.scale(1.55, 1);
    C.fillStyle = '#1a0f0c'; C.beginPath(); C.moveTo(-14, -52); C.lineTo(-14, -64); C.lineTo(-7, -57); C.lineTo(0, -66); C.lineTo(7, -57); C.lineTo(14, -64); C.lineTo(14, -52); C.fill();
    C.fillStyle = '#ffcf33'; C.beginPath(); C.moveTo(-12.6, -52.5); C.lineTo(-12.6, -61.5); C.lineTo(-6.5, -56); C.lineTo(0, -63.5); C.lineTo(6.5, -56); C.lineTo(12.6, -61.5); C.lineTo(12.6, -52.5); C.fill();
    C.strokeStyle = '#1a0f0c'; C.lineWidth = 2.4; C.beginPath(); C.moveTo(-14, -42); C.lineTo(-3, -36); C.moveTo(14, -42); C.lineTo(3, -36); C.stroke();
    C.restore();
  }
  C.restore();
}

// ---------- items / collectibles ----------
function drawCoin(x, y, ph) {
  const w = [10, 7, 3, 7][ph & 3], l = x + 8 - w / 2;
  C.fillStyle = '#7a4e0c'; C.fillRect(l - 1, y, w + 2, 15);
  R(l, y + 1, w, 13, '#ffd23a'); R(l, y + 1, 1, 13, '#c98a14'); R(l + w - 1, y + 1, 1, 13, '#c98a14'); R(l, y + 1, w, 1, '#fff2a8');
  if (w >= 7) R(x + 7, y + 4, 2, 7, '#c98a14');
}
function drawItem(it, frame) {
  const x = Math.round(it.x), y = Math.round(it.y), b = Math.sin(frame / 6) * 0.8;
  if (it.type === 'bolt') {
    C.save(); C.translate(x + 7, y + 7 + b);
    C.fillStyle = '#1a0f0c'; C.beginPath(); C.moveTo(2, -8); C.lineTo(-6, 1); C.lineTo(-1, 1); C.lineTo(-3, 8); C.lineTo(6, -2); C.lineTo(1, -2); C.lineTo(4, -8); C.closePath(); C.fill();
    C.fillStyle = '#ffe14a'; C.beginPath(); C.moveTo(2, -6.6); C.lineTo(-4.4, 0.4); C.lineTo(0.4, 0.4); C.lineTo(-1.6, 6); C.lineTo(4.4, -0.6); C.lineTo(-0.4, -0.6); C.lineTo(2.2, -6.6); C.closePath(); C.fill();
    C.restore();
  } else if (it.type === 'ball') {
    drawBall(x + 7, y + 8, 7, frame / 8);
  } else if (it.type === 'star') {
    C.save(); C.translate(x + 7, y + 7 + b); star5(0, 0, 8.2, '#1a0f0c'); star5(0, 0, 6.6, 'hsl(' + ((frame * 10) % 360) + ',100%,62%)'); C.restore();
  } else if (it.type === 'heart') {
    C.save(); C.translate(x + 7, y + 7 + b); heart(0, 0, 8, '#1a0f0c'); heart(0, 0, 6.4, '#ff4a6e'); C.restore();
  }
}
function star5(cx, cy, r, col) { C.fillStyle = col; C.beginPath(); for (let i = 0; i < 10; i++) { const a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 ? r * 0.45 : r; C.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr); } C.closePath(); C.fill(); }
function heart(cx, cy, r, col) { C.fillStyle = col; C.beginPath(); C.moveTo(cx, cy + r * 0.9); C.bezierCurveTo(cx - r * 1.5, cy - r * 0.2, cx - r * 0.7, cy - r * 1.1, cx, cy - r * 0.3); C.bezierCurveTo(cx + r * 0.7, cy - r * 1.1, cx + r * 1.5, cy - r * 0.2, cx, cy + r * 0.9); C.fill(); }
function drawBall(cx, cy, r, rot) {
  C.save(); C.translate(cx, cy); C.rotate(rot);
  C.fillStyle = '#1a0f0c'; C.beginPath(); C.arc(0, 0, r + 0.9, 0, 7); C.fill();
  C.fillStyle = '#fff'; C.beginPath(); C.arc(0, 0, r, 0, 7); C.fill();
  C.fillStyle = '#1a0f0c'; C.beginPath(); for (let i = 0; i < 5; i++) { const a = i * Math.PI * 2 / 5 - Math.PI / 2; C.lineTo(Math.cos(a) * r * 0.42, Math.sin(a) * r * 0.42); } C.fill();
  for (let i = 0; i < 5; i++) { const a = i * Math.PI * 2 / 5 - Math.PI / 2; C.beginPath(); C.arc(Math.cos(a) * r * 0.85, Math.sin(a) * r * 0.85, r * 0.22, 0, 7); C.fill(); }
  C.restore();
}

// ---------- goal & checkpoint ----------
function drawPole(poleX, flagY, frame) {
  R(poleX, 3 * T + 6, 2, 192 - (3 * T + 6), '#eee'); R(poleX + 1, 3 * T + 6, 1, 192 - (3 * T + 6), '#b5b5b5');
  blob(poleX + 1, 3 * T + 3, 3, '#ffd23a');
  const fx = poleX - 17, fy = Math.round(flagY);
  R(fx, fy, 17, 12, '#1a0f0c'); R(fx + 1, fy + 1, 15, 10, '#e63946');
  head(IMG.hero, fx + 8, fy + 6, 9, 0, '#1a0f0c');
}
function drawCastle(x, th, castleFlag) {
  const c = THEMES[th].castle, base = 13 * T;
  const brick = (bx, by, w, h) => { R(bx, by, w, h, c[0]); for (let yy = 0; yy < h; yy += 4) { R(bx, by + yy + 3, w, 1, c[1]); for (let xx = (yy / 4) % 2 ? 0 : 4; xx < w; xx += 8) R(bx + xx, by + yy, 1, 3, c[1]); } };
  R(x + 39, base - 112, 2, 32, '#eee');
  const fy = base - 90 - castleFlag; R(x + 41, fy, 18, 13, '#1a0f0c'); R(x + 42, fy + 1, 16, 11, '#e63946'); head(IMG.hero, x + 50, fy + 6.5, 9, 0);
  brick(x + 16, base - 80, 48, 32); for (const mx of [16, 36, 56]) brick(x + mx, base - 88, 8, 8);
  R(x + 26, base - 72, 4, 10, '#120a08'); R(x + 50, base - 72, 4, 10, '#120a08');
  brick(x, base - 48, 80, 48); for (const mx of [0, 32, 64]) brick(x + mx, base - 58, 16, 10);
  R(x + 32, base - 24, 16, 24, '#120a08'); R(x + 34, base - 26, 12, 2, '#120a08'); R(x + 36, base - 28, 8, 2, '#120a08');
}
function drawCheck(x, taken, frame) {
  const y = 13 * T;
  R(x + 7, y - 26, 2, 26, '#ddd'); blob(x + 8, y - 27, 2, '#ffd23a');
  const w = Math.sin(frame / 8) * 1.5;
  C.fillStyle = '#1a0f0c'; C.beginPath(); C.moveTo(x + 9, y - 25); C.lineTo(x + 22 + w, y - 20); C.lineTo(x + 9, y - 14); C.fill();
  C.fillStyle = taken ? '#3fdc6a' : '#ff5a5a'; C.beginPath(); C.moveTo(x + 9.5, y - 23.8); C.lineTo(x + 20 + w, y - 20); C.lineTo(x + 9.5, y - 15.4); C.fill();
}

// ---------- platforms ----------
function drawPlat(p) {
  const x = Math.round(p.x), y = Math.round(p.y);
  R(x, y, p.w, 8, '#1a0f0c'); R(x + 1, y + 1, p.w - 2, 6, '#f2b84a'); R(x + 1, y + 1, p.w - 2, 2, '#ffe08a');
  for (let i = 6; i < p.w - 4; i += 12) R(x + i, y + 4, 4, 2, '#b8780f');
}

window.SFB.Art = { THEMES, bind, setVW, R, blob, IMG, loadImages, drawTile, drawSky, drawLavaBand, drawHero, drawEnemy, drawCoin, drawItem, drawPole, drawCastle, drawCheck, drawPlat, drawBall, star5, heart, head, hurtBlink, glyph };
})();
