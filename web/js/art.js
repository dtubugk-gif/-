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
  under: { sky: ['#04060f', '#0e1a3a'], g: { top: '#4a90ea', topd: '#2457a0', body: '#2c63b5', bodyd: '#17376e', bodyl: '#6aa8f8' }, br: ['#3a74d8', '#142a5a', '#7ab0ff'], hd: ['#5a5f78', '#1e2133', '#9aa0bf'], pipe: ['#35c27a', '#177a47', '#9cf5c4'], spk: ['#ff5a5a', '#ffd0d0'], castle: ['#3a74d8', '#142a5a'] },
  sea: { sky: ['#0a3f8f', '#33c8ff'], g: { top: '#f6e39a', topd: '#c9a95a', body: '#e0c075', bodyd: '#a98a45', bodyl: '#fff0b0' }, br: ['#ff7a9a', '#8a2246', '#ffc1d0'], hd: ['#7a9ab8', '#2a4660', '#b8d4ea'], pipe: ['#c04aa8', '#6a1f60', '#f09ee0'], spk: ['#8a3acb', '#d9a0ff'], castle: ['#ff7a9a', '#8a2246'] },
  night: { sky: ['#070b24', '#2e2c6a'], g: { top: '#35a67e', topd: '#1b5f48', body: '#4a3a6a', bodyd: '#2a2040', bodyl: '#6a5a90' }, br: ['#5a4a8a', '#221a3a', '#8a7ac0'], hd: ['#4a4a70', '#1a1a30', '#7a7aa8'], pipe: ['#7a4ad8', '#3c1f80', '#c0a0ff'], spk: ['#ff5ad0', '#ffd0f4'], castle: ['#5a4a8a', '#221a3a'] },
  ash: { sky: ['#2a0f0c', '#d0642c'], g: { top: '#f07a34', topd: '#a0401c', body: '#4a3a36', bodyd: '#2a201e', bodyl: '#6a5a54' }, br: ['#7a4a3a', '#2a1410', '#b07a62'], hd: ['#5a4a46', '#1e1614', '#8a7a74'], pipe: ['#8a8f9a', '#3a3f48', '#d0d6e0'], spk: ['#ff6a2a', '#ffe08a'], castle: ['#7a4a3a', '#2a1410'] },
  castle: { sky: ['#0d0d18', '#34344f'], g: { top: '#9aa0ba', topd: '#5a5f78', body: '#6a6f88', bodyd: '#3a3e55', bodyl: '#aab0cc' }, br: ['#7a7f98', '#2a2d42', '#b0b6d0'], hd: ['#555a72', '#1d2033', '#8a90ac'], pipe: ['#a0a6c0', '#4a4f68', '#e0e4f4'], spk: ['#d0d6f0', '#ffffff'], castle: ['#7a7f98', '#2a2d42'] },
  void: { sky: ['#06021a', '#3a1470'], g: { top: '#d050ff', topd: '#7a1fc0', body: '#2a1a4a', bodyd: '#150a2a', bodyl: '#4a3a7a' }, br: ['#4a2a7a', '#1a0f30', '#8a5ac0'], hd: ['#3a2a60', '#150d28', '#6a58a0'], pipe: ['#40e0d0', '#1a7a70', '#a0fff4'], spk: ['#ff40a0', '#ffd0e8'], castle: ['#4a2a7a', '#1a0f30'] },
  tree: { sky: ['#5cc8ff', '#e2f7ff'], g: { top: '#6fdc5a', topd: '#2f9a45', body: '#b8693a', bodyd: '#7c4222', bodyl: '#dc9462' }, br: ['#c8652f', '#4a1e0c', '#eb9a62'], hd: ['#a0603a', '#5a2c14', '#d99566'], pipe: ['#3fbf4a', '#1f7a2c', '#8df08a'], spk: ['#7a5a3a', '#cdb08a'], castle: ['#b8532a', '#4a1e0c'] },
  coast: { sky: ['#ff9a6a', '#ffe6c0'], g: { top: '#f6e39a', topd: '#c9a95a', body: '#d9b56a', bodyd: '#a0803e', bodyl: '#fff0b8' }, br: ['#d9763a', '#5a2a0c', '#ffb07a'], hd: ['#8a8f9a', '#3a3f48', '#c8ced8'], pipe: ['#3fbf4a', '#1f7a2c', '#8df08a'], spk: ['#7a5a3a', '#cdb08a'], castle: ['#d9763a', '#5a2a0c'] },
  dusk: { sky: ['#5a3a9a', '#ffb08a'], g: { top: '#7ad06a', topd: '#3a8a4a', body: '#8a5a4a', bodyd: '#5a3a30', bodyl: '#b07a66' }, br: ['#b85a4a', '#4a1a14', '#e08a7a'], hd: ['#8a6a7a', '#3a2a34', '#c0a0b0'], pipe: ['#5aba6a', '#2a6a3a', '#a0f0b0'], spk: ['#6a4a6a', '#c0a0c0'], castle: ['#b85a4a', '#4a1a14'] },
  autumn: { sky: ['#6ab0e8', '#f8e8c8'], g: { top: '#e8a83a', topd: '#a8641a', body: '#9a5a32', bodyd: '#5a3018', bodyl: '#c88a5a' }, br: ['#c86a2a', '#4a1e0c', '#f0a060'], hd: ['#8a6a4a', '#3a2a1a', '#c0a080'], pipe: ['#3fbf4a', '#1f7a2c', '#8df08a'], spk: ['#7a5a3a', '#cdb08a'], castle: ['#c86a2a', '#4a1e0c'] }
};

const NIGHT_STARS = []; { let sd = 7; const rnd = () => (sd = (sd * 16807) % 2147483647) / 2147483647; for (let i = 0; i < 70; i++) NIGHT_STARS.push([rnd() * 600, rnd() * 170, rnd() < 0.2 ? 2 : 1, rnd() * 6.28]); }
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
    case TILE.BRK: case TILE.BRKM: {
      const b = T_.br; R(x, y, 16, 16, b[0]); R(x, y, 16, 1, b[2]);
      for (let i = 0; i < 4; i++) { R(x, y + i * 4 + 3, 16, 1, b[1]); const o = i % 2 ? 3 : 7; R(x + o, y + i * 4, 1, 3, b[1]); R(x + o + 8, y + i * 4, 1, 3, b[1]); }
      break;
    }
    case TILE.QC: case TILE.QP: case TILE.QS: case TILE.QU: case TILE.QV: {
      const ph = ((frame >> 3) % 6) < 3, base = ph ? '#ffb02e' : '#e8901a';
      R(x, y, 16, 16, '#4a2208'); R(x + 1, y + 1, 14, 14, base); R(x + 1, y + 1, 14, 1, '#ffe29a'); R(x + 1, y + 1, 1, 14, '#ffe29a');
      R(x + 1, y + 14, 14, 1, '#b8620f'); R(x + 14, y + 1, 1, 14, '#b8620f');
      for (const [a, b] of [[3, 3], [12, 3], [3, 12], [12, 12]]) R(x + a, y + b, 1, 1, '#7a3d0e');
      const col = t === TILE.QV ? '#1f7a2c' : t === TILE.QU ? '#2fae4a' : t === TILE.QS ? '#ff5aa8' : '#7a3d0e';
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
    case TILE.CANNON: {
      R(x, y, 16, 16, '#07070b'); R(x + 1, y + 1, 14, 14, '#2d303c'); R(x + 1, y + 1, 14, 2, '#6a6f84'); R(x + 1, y + 1, 2, 14, '#6a6f84'); R(x + 13, y + 3, 2, 12, '#1a1c25');
      blob(x + 8, y + 8, 5, '#050508'); blob(x + 8, y + 8, 3, '#1c1e28'); R(x + 3, y + 3, 1, 1, '#9aa0b8'); R(x + 12, y + 3, 1, 1, '#9aa0b8'); R(x + 3, y + 12, 1, 1, '#9aa0b8'); R(x + 12, y + 12, 1, 1, '#9aa0b8');
      break;
    }
    case TILE.HID: break;
    case TILE.TREE: {
      const lc = th === 'autumn' ? ['#e8a83a', '#a8641a', '#ffd07a'] : ['#5fd35f', '#2f9a45', '#a8f0a0'];
      R(x, y, 16, 16, lc[1]); R(x, y, 16, 12, lc[0]); R(x, y, 16, 2, lc[2]);
      for (let i = 2; i < 16; i += 5) R(x + i, y + 5 + (i % 3), 2, 2, lc[2]);
      if (!above) { blob(x + 4, y + 1, 3, lc[0]); blob(x + 12, y + 1, 3, lc[0]); }
      break;
    }
    case TILE.TRUNK: R(x + 2, y, 12, 16, '#5a3418'); R(x + 3, y, 10, 16, '#8a5428'); R(x + 6, y, 2, 16, '#a86a38'); R(x + 10, y + 3, 1, 6, '#5a3418'); break;
    case TILE.MUSH: {
      R(x, y, 16, 16, '#1a0f0c'); R(x, y + 1, 16, 13, '#e63946'); R(x, y + 1, 16, 2, '#ff8a8a');
      blob(x + 5, y + 7, 3, '#fff'); blob(x + 13, y + 5, 2, '#fff'); R(x, y + 14, 16, 2, '#a52530');
      break;
    }
    case TILE.STEM: R(x + 3, y, 10, 16, '#1a0f0c'); R(x + 4, y, 8, 16, '#f3e3c0'); R(x + 9, y, 2, 16, '#d8c098'); break;
    case TILE.BRIDGE: {
      R(x, y, 16, 6, '#1a0f0c'); R(x, y + 1, 16, 4, '#b8742e'); R(x, y + 1, 16, 1, '#e8a860');
      R(x + 7, y + 1, 1, 4, '#6a3a12'); R(x + 15, y + 1, 1, 4, '#6a3a12');
      R(x + 1, y - 5, 2, 6, '#6a3a12'); R(x, y - 6, 16, 1.4, '#e8d8b0');
      break;
    }
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
  } else if (th === 'under') {
    const o = (cx * 0.4) % 32; C.fillStyle = '#101c3c';
    for (let y = 0; y < VH; y += 16) for (let x = -32 - o + ((y / 16) % 2 ? 16 : 0); x < VW + 32; x += 32) C.fillRect(x + 1, y + 1, 30, 14);
    tile(cx, 0.55, 120, (x, k) => { const hh = 16 + ((k * 7) & 3) * 8; C.fillStyle = '#060a18'; C.beginPath(); C.moveTo(x + 10, 0); C.lineTo(x + 34, 0); C.lineTo(x + 22, hh); C.fill(); });
    ridge(cx, 0.6, 232, 14, 0.03, '#050914', false, 2);
  } else if (th === 'sea') {
    for (let i = 0; i < 6; i++) { const x = ((i * 110 + frame * 0.2 - cx * 0.1) % (VW + 160)) - 60; C.fillStyle = 'rgba(255,255,255,0.07)'; C.beginPath(); C.moveTo(x, 0); C.lineTo(x + 26, 0); C.lineTo(x + 90, VH); C.lineTo(x + 50, VH); C.fill(); }
    ridge(cx, 0.12, 200, 60, 0.011, '#0b4f9c', true, 3);
    tile(cx, 0.35, 110, (x, k) => { for (let j = 0; j < 3; j++) { C.fillStyle = j === 1 ? '#1aa069' : '#12875a'; for (let y = 0; y < 50 + j * 10; y += 3) { const sx = x + 20 + j * 9 + Math.sin((y + frame * 1.5) / 11 + j + k) * 3; C.fillRect(sx, 208 - y, 3, 3); } } });
    tile(cx, 0.5, 170, (x, k) => { blob(x + 30, 204, 9, '#ff7ab0'); blob(x + 42, 208, 7, '#ff9a5a'); blob(x + 22, 208, 6, '#d94a8a'); });
    ridge(cx, 0.6, 230, 12, 0.03, '#c9a95a99', false, 5);
  } else if (th === 'night') {
    for (const st of NIGHT_STARS) { C.globalAlpha = 0.5 + 0.5 * Math.sin(frame / 25 + st[3]); C.fillStyle = '#fff'; C.fillRect(st[0] * VW / 600, st[1], st[2], st[2]); } C.globalAlpha = 1;
    blob(VW - 70, 46, 18, '#fff7d0'); blob(VW - 64, 42, 16, '#2a2c68');
    ridge(cx, 0.1, 190, 70, 0.01, '#16164a', true, 2);
    ridge(cx, 0.25, 214, 40, 0.018, '#10143a');
    tile(cx, 0.45, 76, (x, k) => { const hh = 30 + ((k * 5) & 3) * 8; C.fillStyle = '#0a0f2a'; for (let j = 0; j < 3; j++) { C.beginPath(); C.moveTo(x + 8, 210 - j * hh * 0.28); C.lineTo(x + 24, 210 - j * hh * 0.28 - hh * 0.45); C.lineTo(x + 40, 210 - j * hh * 0.28); C.fill(); } });
    ridge(cx, 0.6, 228, 18, 0.028, '#081028', false, 4);
  } else if (th === 'ash') {
    const glow = C.createRadialGradient(VW * 0.5, 120, 10, VW * 0.5, 120, 220); glow.addColorStop(0, '#ff9a4a66'); glow.addColorStop(1, '#ff9a4a00'); C.fillStyle = glow; C.fillRect(0, 0, VW, VH);
    tile(cx, 0.12, 520, (x, k) => { C.fillStyle = '#3a1612'; C.beginPath(); C.moveTo(x + 40, 210); C.lineTo(x + 200, 78); C.lineTo(x + 230, 78); C.lineTo(x + 400, 210); C.fill(); blob(x + 215, 76, 14, '#ff8a34'); blob(x + 215, 74, 9, '#ffe08a'); for (let j = 0; j < 4; j++) blob(x + 215 + j * 8 + Math.sin(frame / 40 + j) * 5, 60 - j * 14, 9 + j * 3, 'rgba(60,45,45,' + (0.55 - j * 0.1) + ')'); });
    ridge(cx, 0.3, 214, 44, 0.016, '#2a100e', true, 1);
    ridge(cx, 0.55, 230, 18, 0.03, '#1a0a08', false, 3);
  } else if (th === 'castle') {
    C.fillStyle = '#1a1a2e'; const o = (cx * 0.3) % 48;
    for (let y = 0; y < VH; y += 24) for (let x = -48 - o + ((y / 24) % 2 ? 24 : 0); x < VW + 48; x += 48) C.fillRect(x + 1, y + 1, 46, 22);
    tile(cx, 0.3, 190, (x, k) => { R(x + 70, 60, 28, 56, '#0a0a16'); blob(x + 84, 60, 14, '#0a0a16'); R(x + 74, 64, 20, 48, '#2c3a6a'); blob(x + 84, 64, 10, '#2c3a6a'); R(x + 83, 52, 2, 64, '#0a0a16'); });
    tile(cx, 0.6, 200, (x, k) => { R(x + 100, 150, 4, 58, '#3a2a1a'); const f = Math.sin(frame / 4 + k * 2) * 1.5; blob(x + 102, 144, 7, '#ff7a1a'); blob(x + 102, 142 + f, 5, '#ffd24a'); blob(x + 102, 141 + f, 2, '#fff7c0'); });
  } else if (th === 'tree' || th === 'autumn') {
    ridge(cx, 0.1, 200, 60, 0.011, th === 'tree' ? '#a8dcf2' : '#d8c8b0', true, 2);
    tile(cx, 0.22, 380, (x, k) => { cloud(x + 40, 40 + (k & 1) * 18, 1, '#fff', '#d8e8f8'); });
    tile(cx, 0.35, 150, (x, k) => { const h = 70 + ((k * 7) & 3) * 18; R(x + 60, 230 - h, 10, h, th === 'tree' ? '#7a4a2a' : '#6a3a1a'); blob(x + 65, 230 - h, 26, th === 'tree' ? '#3fae4a' : (k & 1 ? '#e8743a' : '#d8a03a')); blob(x + 50, 236 - h, 16, th === 'tree' ? '#58c85a' : '#f0b04a'); });
    ridge(cx, 0.55, 232, 18, 0.03, th === 'tree' ? '#3f9a4a' : '#b0602a', false, 5);
  } else if (th === 'coast') {
    blob(VW - 90, 150, 30, '#fff0b0'); blob(VW - 90, 150, 24, '#ffb04a');
    C.fillStyle = '#3a8ad0'; C.fillRect(0, 160, VW, VH - 160); C.fillStyle = '#5aaaf0';
    for (let y = 166; y < VH; y += 10) for (let x = -((cx * 0.3 + frame * 0.3 + y * 3) % 40); x < VW; x += 40) C.fillRect(x, y, 18, 1);
    tile(cx, 0.2, 420, (x, k) => { cloud(x + 60, 46 + (k & 1) * 20, 1.1, '#fff4e8', '#ffd0b0'); });
  } else if (th === 'dusk') {
    blob(VW * 0.3, 170, 34, '#ffd09a'); blob(VW * 0.3, 170, 26, '#ff8a5a');
    ridge(cx, 0.12, 196, 70, 0.011, '#7a4a8a', true, 2);
    ridge(cx, 0.3, 214, 40, 0.02, '#5a3a6a');
    tile(cx, 0.24, 360, (x, k) => { cloud(x + 50, 40 + (k & 1) * 16, 1, '#ffd8e8', '#e0a0c0'); });
    ridge(cx, 0.55, 228, 20, 0.03, '#3a6a4a', false, 5);
  } else if (th === 'void') {
    for (const st of NIGHT_STARS) { C.globalAlpha = 0.4 + 0.6 * Math.sin(frame / 18 + st[3] * 2); C.fillStyle = st[2] > 1 ? '#ffb0ff' : '#c0f0ff'; C.fillRect(st[0] * VW / 600, st[1], st[2], st[2]); } C.globalAlpha = 1;
    const g1 = C.createRadialGradient(VW * 0.3, 90, 10, VW * 0.3, 90, 170); g1.addColorStop(0, '#c040ff55'); g1.addColorStop(1, '#c040ff00'); C.fillStyle = g1; C.fillRect(0, 0, VW, VH);
    const g2 = C.createRadialGradient(VW * 0.8, 150, 10, VW * 0.8, 150, 150); g2.addColorStop(0, '#40e0d044'); g2.addColorStop(1, '#40e0d000'); C.fillStyle = g2; C.fillRect(0, 0, VW, VH);
    tile(cx, 0.22, 260, (x, k) => { const y = 60 + ((k * 37) & 63); C.strokeStyle = '#d050ff88'; C.lineWidth = 1.5; C.strokeRect(x + 40, y + Math.sin(frame / 40 + k) * 4, 34, 34); C.fillStyle = '#1a0a30'; C.fillRect(x + 41, y + 1 + Math.sin(frame / 40 + k) * 4, 32, 32); });
    ridge(cx, 0.35, 220, 36, 0.018, '#1a0a30', true, 2);
    ridge(cx, 0.6, 232, 14, 0.03, '#100620', false, 4);
  }
}

// bands are drawn in world coordinates, so they must span the visible window [cx, cx + VW]
function drawWaterBand(frame, cx) {
  const y = 12 * T + 8, x0 = Math.floor(cx / 8) * 8;
  C.fillStyle = '#2a6ac8cc'; C.fillRect(cx, y, VW, VH - y);
  C.fillStyle = '#7ac0ff';
  for (let x = x0; x < cx + VW + 8; x += 8) { const h = 2 + Math.round(Math.sin((x + frame) / 10) * 1.5); C.fillRect(x, y - 1, 8, h); }
  C.fillStyle = '#ffffffaa'; for (let x = Math.floor(cx / 30) * 30 + 6; x < cx + VW; x += 30) C.fillRect(x + ((frame >> 2) % 8), y + 6, 6, 1);
}
function drawLavaBand(frame, cx) {
  const y = 14 * T + 2, x0 = Math.floor(cx / 8) * 8;
  C.fillStyle = '#ff4a1a'; C.fillRect(cx, y, VW, VH - y);
  C.fillStyle = '#ffb02e';
  for (let x = x0; x < cx + VW + 8; x += 8) { const h = 2 + Math.round(Math.sin((x + frame) / 9) * 1.5); C.fillRect(x, y, 8, h + 1); }
  C.fillStyle = '#fff2a0'; for (let x = Math.floor(cx / 26) * 26 + 4; x < cx + VW; x += 26) C.fillRect(x + ((frame >> 2) % 6), y + 4, 4, 1);
}

// ---------- characters ----------
function hurtBlink(P, frame) { return P.inv > 0 && ((frame >> 2) & 1); }

function drawHero(P, mode, step, frame, opts) {
  if (!IMG.ready) return;
  const s = P.big ? 36 / 28 : 1;
  const cx = P.x + P.w / 2, fy = P.y + P.h;
  const walk = mode === 'walk', jump = mode === 'jump', dead = mode === 'dead';
  C.save(); C.translate(Math.round(cx), Math.round(fy)); C.scale(P.face * s, s * (P.crouch ? 0.62 : 1));
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
  if (k === 'plant') return; // drawn behind the pipes by drawPlant
  if (k === 'shell' || k === 'rshell' || k === 'buzzy' || k === 'para' || k === 'rpara') return drawShell(e, frame);
  if (k === 'lakitu') return drawLakitu(e, frame);
  if (k === 'spegg') { drawSpiky(Math.round(e.x + 6), Math.round(e.y + 6), 5); return; }
  if (k === 'hammer') return drawHammerBro(e, frame);
  if (k === 'hammerp') { drawHammerShape(Math.round(e.x + 6), Math.round(e.y + 6), e.rot || 0); return; }
  if (k === 'blooper') return drawBlooper(e, frame);
  if (k === 'jfish') return drawFish(e, frame);
  if (k === 'thwomp') return drawThwomp(e, frame);
  if (k === 'bullet') return drawBullet(e, frame);
  if (k === 'shot') return drawShot(e, frame);
  if (k === 'pod') return drawPod(e, frame);
  if (k === 'fish') return drawFish(e, frame);
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


// ---------- more enemies & hazards ----------
function dk() { return '#1a0f0c'; }
const SHELL_PAL = { shell: ['#2fae4a', '#6fe08a', '#1b7a31'], para: ['#2fae4a', '#6fe08a', '#1b7a31'], rshell: ['#e63946', '#ff9a9a', '#9a1f2a'], rpara: ['#e63946', '#ff9a9a', '#9a1f2a'], buzzy: ['#2a3a7a', '#6a88d8', '#141e4a'] };
function wings(ox, oy, frame) {
  const fl = Math.sin(frame / 2.4) * 0.7;
  for (const sd of [-1, 1]) { C.save(); C.translate(ox + sd * 6, oy); C.rotate(sd * (0.4 + fl * 0.5)); C.fillStyle = '#1a0f0c'; C.beginPath(); C.ellipse(sd * 5, 0, 7, 3.4, 0, 0, 7); C.fill(); C.fillStyle = '#fff'; C.beginPath(); C.ellipse(sd * 5, 0, 6, 2.6, 0, 0, 7); C.fill(); C.restore(); }
}
function drawShell(e, frame) {
  const mode = e.shell | 0, face = e.vx < 0 ? -1 : 1, cx = e.x + e.w / 2, fy = e.y + e.h, pal = SHELL_PAL[e.k] || SHELL_PAL.shell;
  C.save(); C.translate(Math.round(cx), Math.round(fy));
  if (e.state === 'knock') C.rotate(Math.PI);
  const dome = (ox, oy, rx, ry) => {
    C.fillStyle = dk(); C.beginPath(); C.ellipse(ox, oy, rx + 1, ry + 1, 0, 0, 7); C.fill();
    C.fillStyle = pal[0]; C.beginPath(); C.ellipse(ox, oy, rx, ry, 0, 0, 7); C.fill();
    C.fillStyle = pal[1]; C.beginPath(); C.ellipse(ox - rx * 0.25, oy - ry * 0.35, rx * 0.5, ry * 0.3, 0, 0, 7); C.fill();
    C.strokeStyle = pal[2]; C.lineWidth = 1; C.beginPath(); C.moveTo(ox - rx, oy); C.lineTo(ox + rx, oy); C.moveTo(ox, oy - ry); C.lineTo(ox, oy + ry); C.stroke();
  };
  if (mode === 0) {
    const sw = (((e.t / 8) | 0) % 2) ? 1 : -1;
    if (e.k === 'para' || e.k === 'rpara') wings(-face * 3, -12, frame);
    R(-5 + sw, -2.4, 4.4, 2.4, '#27211e'); R(0.6 - sw, -2.4, 4.4, 2.4, '#27211e');
    if (e.k === 'buzzy') { dome(0, -8, 9, 8); head(IMG.enemy, face * 4, -6, 11, 0); }
    else { dome(-face * 3, -8.5, 8, 6.8); head(IMG.enemy, face * 2, -18.5, 17, sw * 0.07); }
  } else {
    dome(0, -8, 9, 8);
    C.fillStyle = '#f3e3b0'; C.fillRect(-9, -2.5, 18, 2.5); C.strokeStyle = dk(); C.lineWidth = 0.8; C.strokeRect(-9, -2.5, 18, 2.5);
    if (mode === 2) { C.strokeStyle = '#ffffff99'; C.lineWidth = 1; const o = (frame * 2) % 8; for (let i = -1; i <= 1; i++) { C.beginPath(); C.arc(0, -8, 5 + i * 2, 0.6 + o * 0.1, 2.2 + o * 0.1); C.stroke(); } }
    else head(IMG.enemy, 0, -6.5, 10, 0);
  }
  C.restore();
}
function drawLakitu(e, frame) {
  const cx = Math.round(e.x + e.w / 2), cy = Math.round(e.y + e.h - 6), b = Math.sin(frame / 10) * 1.5;
  C.save(); C.translate(cx, cy + b); if (e.state === 'knock') C.rotate(Math.PI);
  head(IMG.enemy, 0, -12 + (e.duck ? 6 : 0), 17, Math.sin(frame / 20) * 0.1);
  C.strokeStyle = dk(); C.lineWidth = 1.2; C.beginPath(); C.arc(-3.2, -12.5 + (e.duck ? 6 : 0), 2.6, 0, 7); C.arc(3.2, -12.5 + (e.duck ? 6 : 0), 2.6, 0, 7); C.stroke();
  blob(-8, 2, 6, dk()); blob(0, 0, 8, dk()); blob(8, 2, 6, dk());
  blob(-8, 1, 5, '#fff'); blob(0, -1, 7, '#fff'); blob(8, 1, 5, '#fff');
  C.fillStyle = dk(); C.fillRect(-4, -1, 2, 3); C.fillRect(2, -1, 2, 3);
  C.restore();
}
function drawSpiky(cx, cy, r) {
  C.fillStyle = dk(); C.beginPath(); for (let i = 0; i < 16; i++) { const a = i * Math.PI / 8, rr = i % 2 ? r * 0.7 : r * 1.25; C.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr); } C.fill();
  C.fillStyle = '#e63946'; C.beginPath(); C.arc(cx, cy, r * 0.8, 0, 7); C.fill(); C.fillStyle = '#ffd0d0'; C.beginPath(); C.arc(cx - 1.5, cy - 1.5, r * 0.25, 0, 7); C.fill();
}
function drawHammerBro(e, frame) {
  const face = e.face || -1, cx = Math.round(e.x + e.w / 2), fy = Math.round(e.y + e.h);
  C.save(); C.translate(cx, fy); if (e.state === 'knock') C.rotate(Math.PI); C.scale(face, 1);
  R(-5, -3, 4, 3, '#27211e'); R(1, -3, 4, 3, '#27211e');
  R(-5, -11, 10, 8, '#2fae4a'); R(-5, -5, 10, 2, '#1b7a31');
  const arm = (e.throwT | 0) > 0 ? -8 : -2; R(3, -12 + arm * 0.4, 3, 6, '#f3dcb0');
  if ((e.throwT | 0) <= 0) drawHammerShape(5, -16, 0);
  head(IMG.enemy, 0, -19, 17, 0);
  C.fillStyle = dk(); C.beginPath(); C.ellipse(0, -25, 8.6, 5.4, 0, Math.PI, 0); C.fill();
  C.fillStyle = '#4a5a7a'; C.beginPath(); C.ellipse(0, -25, 7.6, 4.6, 0, Math.PI, 0); C.fill();
  C.restore();
}
function drawHammerShape(x, y, rot) {
  C.save(); C.translate(x, y); C.rotate(rot);
  R(-1, -1, 2, 9, '#8a5a2a'); R(-4, -4, 8, 4, dk()); R(-3.4, -3.4, 6.8, 2.8, '#a8b0c0');
  C.restore();
}
function drawBlooper(e, frame) {
  const cx = Math.round(e.x + e.w / 2), cy = Math.round(e.y + e.h / 2), sq = e.squish ? 0.8 : 1.1;
  C.save(); C.translate(cx, cy); if (e.state === 'knock') C.rotate(Math.PI);
  C.fillStyle = dk(); C.beginPath(); C.ellipse(0, -2, 7.6, 9 * sq, 0, 0, 7); C.fill();
  C.fillStyle = '#f4f4f8'; C.beginPath(); C.ellipse(0, -2, 6.6, 8 * sq, 0, 0, 7); C.fill();
  for (let i = -3; i <= 3; i += 2) { const w = Math.sin(frame / 4 + i) * 1.5; R(i - 0.7 + w, 5 * sq, 1.6, 7, '#f4f4f8'); }
  head(IMG.enemy, 0, -1, 10, 0);
  C.restore();
}
function drawVine(v, frame) {
  const x = v.x + 8;
  for (let y = v.bottom; y > v.top; y -= 4) { const w = Math.sin((y + frame) / 9) * 1.2; R(x - 1 + w, y - 4, 2.4, 4, '#2f9a45'); if (((y / 4) | 0) % 4 === 0) { C.fillStyle = '#5fd35f'; C.beginPath(); C.ellipse(x + (((y / 16) | 0) % 2 ? 5 : -5) + w, y - 2, 4, 2, 0, 0, 7); C.fill(); } }
}
function drawAxe(tx, row, frame) {
  const x = tx * T, y = row * T, b = Math.sin(frame / 10) * 1;
  R(x + 7, y + 2, 2.4, 14, '#6a3a12'); R(x + 7.6, y + 2, 1, 14, '#a8743a');
  C.fillStyle = dk(); C.beginPath(); C.moveTo(x + 9, y + 1 + b); C.quadraticCurveTo(x + 18, y + 2, x + 16, y + 10 + b); C.lineTo(x + 9, y + 7 + b); C.fill();
  C.fillStyle = '#c8d0e0'; C.beginPath(); C.moveTo(x + 9.6, y + 2 + b); C.quadraticCurveTo(x + 16.6, y + 3, x + 15, y + 9 + b); C.lineTo(x + 9.6, y + 6.4 + b); C.fill();
}
function drawFirework(f) {
  const n = 14, r = f.r;
  for (let i = 0; i < n; i++) { const a = i * Math.PI * 2 / n; C.fillStyle = f.col; C.globalAlpha = Math.max(0, f.t / 50); C.fillRect(f.x + Math.cos(a) * r - 1, f.y + Math.sin(a) * r - 1, 2.4, 2.4); }
  C.globalAlpha = 1;
}
function drawFriend(x, y, frame) {
  const b = Math.abs(Math.sin(frame / 8)) * -2;
  C.save(); C.translate(x, y + b);
  R(-4, -10, 8, 10, '#3a5ac8'); R(-4, -2, 3, 2, '#5a3a2a'); R(1, -2, 3, 2, '#5a3a2a');
  C.fillStyle = '#f3dcb0'; C.beginPath(); C.arc(0, -14, 5, 0, 7); C.fill(); C.fillStyle = dk(); C.fillRect(-2.5, -15, 1.4, 2.4); C.fillRect(1.2, -15, 1.4, 2.4);
  C.fillStyle = dk(); C.beginPath(); C.ellipse(0, -18, 10, 7, 0, Math.PI, 0); C.fill();
  C.fillStyle = '#fff'; C.beginPath(); C.ellipse(0, -18, 9, 6, 0, Math.PI, 0); C.fill();
  C.fillStyle = '#3a8ae6'; C.beginPath(); C.arc(-4, -21, 2.2, 0, 7); C.arc(4, -21, 2.2, 0, 7); C.arc(0, -23, 1.8, 0, 7); C.fill();
  C.restore();
}
function drawPlant(e, frame) {
  const cx = e.x + e.w / 2, pipeTop = e.baseY + 26, y = e.y;
  if (y >= pipeTop - 1) return;
  C.save(); C.translate(Math.round(cx), 0);
  R(-2, y + 14, 4, pipeTop - y, '#1a0f0c'); R(-1, y + 14, 2, pipeTop - y, '#2f9a45');
  R(-9, y + 20, 7, 3, '#1a0f0c'); R(-8, y + 21, 5, 1.5, '#3fbf4a'); R(2, y + 24, 7, 3, '#1a0f0c'); R(3, y + 25, 5, 1.5, '#3fbf4a');
  const open = (Math.sin(frame / 5) + 1) / 2 * 4;
  C.fillStyle = dk(); C.beginPath(); C.arc(0, y + 8, 9, 0, 7); C.fill();
  C.fillStyle = '#e63946'; C.beginPath(); C.arc(0, y + 8, 8, 0, 7); C.fill();
  C.fillStyle = '#fff'; C.beginPath(); C.arc(-3.5, y + 4, 1.8, 0, 7); C.arc(3.5, y + 3, 1.5, 0, 7); C.arc(-1, y + 11, 1.5, 0, 7); C.fill();
  C.fillStyle = dk(); C.fillRect(-7, y + 8 - open / 2, 14, 1.6 + open); C.fillStyle = '#fff';
  for (let i = -6; i <= 4; i += 4) { C.beginPath(); C.moveTo(i, y + 8 - open / 2); C.lineTo(i + 1.6, y + 8 + 1.6 - open / 2); C.lineTo(i + 3.2, y + 8 - open / 2); C.fill(); }
  C.restore();
}
function drawThwomp(e, frame) {
  const x = Math.round(e.x), y = Math.round(e.y), ang = e.mode === 1;
  R(x, y, 32, 32, '#14161e'); R(x + 1, y + 1, 30, 30, '#8c92aa'); R(x + 1, y + 1, 30, 3, '#c0c6dc'); R(x + 1, y + 1, 3, 30, '#c0c6dc'); R(x + 27, y + 4, 4, 27, '#5c6280'); R(x + 4, y + 27, 27, 4, '#5c6280');
  for (const [a, b] of [[3, 3], [26, 3], [3, 26], [26, 26]]) R(x + a, y + b, 3, 3, '#4a506c');
  for (const sx of [7, 18]) { R(x + sx, y + 11, 8, 6, '#fff'); R(x + sx + (sx < 12 ? 3 : 2), y + 12, 3, 4, ang ? '#ff2a2a' : '#1a0f0c'); }
  C.strokeStyle = dk(); C.lineWidth = 2; C.beginPath(); C.moveTo(x + 5, y + 8); C.lineTo(x + 15, y + 11); C.moveTo(x + 27, y + 8); C.lineTo(x + 17, y + 11); C.stroke();
  R(x + 8, y + 21, 16, 6, dk()); R(x + 9, y + 21, 3, 3, '#fff'); R(x + 20, y + 21, 3, 3, '#fff'); R(x + 14, y + 24, 3, 3, '#fff');
}
function drawBullet(e, frame) {
  const d = e.vx < 0 ? -1 : 1, cx = e.x + e.w / 2, cy = e.y + e.h / 2;
  C.save(); C.translate(Math.round(cx), Math.round(cy)); C.scale(d, 1);
  if (e.state === 'knock') C.rotate(Math.PI / 2);
  C.fillStyle = 'rgba(255,255,255,0.35)'; for (let i = 1; i <= 3; i++) { C.beginPath(); C.arc(-9 - i * 5, Math.sin(frame / 3 + i) * 1.5, 4 - i, 0, 7); C.fill(); }
  C.fillStyle = dk(); C.beginPath(); C.ellipse(0, 0, 9.5, 7, 0, 0, 7); C.fill();
  C.fillStyle = '#2c2c34'; C.beginPath(); C.ellipse(0, 0, 8.5, 6, 0, 0, 7); C.fill();
  C.fillStyle = '#44444f'; C.beginPath(); C.ellipse(1, -2, 5, 2, 0, 0, 7); C.fill();
  C.fillStyle = '#fff'; C.beginPath(); C.arc(3, -1, 2.2, 0, 7); C.arc(-1.5, -1, 2, 0, 7); C.fill(); C.fillStyle = '#c00'; C.beginPath(); C.arc(3.6, -1, 1, 0, 7); C.arc(-0.9, -1, 1, 0, 7); C.fill();
  C.strokeStyle = '#fff'; C.lineWidth = 1.2; C.beginPath(); C.moveTo(0, -4.5); C.lineTo(5.5, -2.5); C.stroke();
  C.fillStyle = '#44444f'; C.fillRect(-9, -7, 3, 4); C.fillRect(-9, 3, 3, 4);
  C.restore();
}
function drawShot(e, frame) {
  const d = e.vx < 0 ? -1 : 1, cx = e.x + e.w / 2, cy = e.y + e.h / 2;
  C.save(); C.translate(Math.round(cx), Math.round(cy)); C.scale(d, 1);
  C.fillStyle = '#ff5a1a'; C.beginPath(); C.moveTo(-12, 0); C.quadraticCurveTo(-4, -6 + Math.sin(frame / 2) * 1.5, 5, -4); C.arc(3, 0, 4.5, -1.5, 1.5); C.quadraticCurveTo(-4, 6 - Math.sin(frame / 2) * 1.5, -12, 0); C.fill();
  C.fillStyle = '#ffd24a'; C.beginPath(); C.arc(3, 0, 3.2, 0, 7); C.fill(); C.fillStyle = '#fff7c0'; C.beginPath(); C.arc(3.6, 0, 1.5, 0, 7); C.fill();
  C.restore();
}
function drawPod(e, frame) {
  const cx = e.x + e.w / 2, cy = e.y + e.h / 2, fall = e.vy > 0;
  C.save(); C.translate(Math.round(cx), Math.round(cy)); if (fall) C.scale(1, -1);
  C.fillStyle = '#ff4a1a'; C.beginPath(); C.moveTo(-5, 4); C.quadraticCurveTo(-7, 14 + Math.sin(frame / 2) * 2, 0, 18); C.quadraticCurveTo(7, 14 + Math.sin(frame / 2) * 2, 5, 4); C.fill();
  C.fillStyle = dk(); C.beginPath(); C.arc(0, 0, 7.4, 0, 7); C.fill();
  C.fillStyle = '#ff7a1a'; C.beginPath(); C.arc(0, 0, 6.4, 0, 7); C.fill(); C.fillStyle = '#ffd24a'; C.beginPath(); C.arc(0, 0.5, 4.2, 0, 7); C.fill();
  C.fillStyle = '#fff'; C.fillRect(-4, -2, 3, 3); C.fillRect(1, -2, 3, 3); C.fillStyle = dk(); C.fillRect(-3, -1, 1.6, 2); C.fillRect(2, -1, 1.6, 2);
  C.restore();
}
function drawFish(e, frame) {
  const d = e.vx < 0 ? -1 : 1, cx = e.x + e.w / 2, cy = e.y + e.h / 2;
  C.save(); C.translate(Math.round(cx), Math.round(cy)); C.scale(d, 1);
  if (e.state === 'knock') C.rotate(Math.PI);
  const tw = Math.sin(frame / 3) * 2;
  C.fillStyle = dk(); C.beginPath(); C.moveTo(-6, 0); C.lineTo(-14, -6 + tw); C.lineTo(-14, 6 + tw); C.closePath(); C.fill();
  C.fillStyle = '#ff7a2a'; C.beginPath(); C.moveTo(-6.5, 0); C.lineTo(-12.5, -4.5 + tw); C.lineTo(-12.5, 4.5 + tw); C.closePath(); C.fill();
  C.fillStyle = dk(); C.beginPath(); C.ellipse(-1, 0, 9, 7, 0, 0, 7); C.fill();
  C.fillStyle = e.k === 'jfish' ? '#ff4a5a' : '#ff9a3a'; C.beginPath(); C.ellipse(-1, 0, 8, 6, 0, 0, 7); C.fill(); C.fillStyle = '#ffd08a'; C.beginPath(); C.ellipse(-1, 2.5, 6, 2.5, 0, 0, 7); C.fill();
  C.fillStyle = '#e04a1a'; C.fillRect(-6, -7, 6, 3);
  head(IMG.enemy, 4, -0.5, 12, 0);
  C.restore();
}
function drawFirebar(fb) {
  const px = fb.tx * T + 8, py = fb.row * T + 8;
  for (let i = 0; i < fb.len; i++) {
    const d = (i + 0.6) * 8, x = px + Math.cos(fb.a) * d, y = py + Math.sin(fb.a) * d;
    C.fillStyle = 'rgba(255,140,40,0.35)'; C.beginPath(); C.arc(x, y, 5.5, 0, 7); C.fill();
    C.fillStyle = dk(); C.beginPath(); C.arc(x, y, 4.2, 0, 7); C.fill();
    C.fillStyle = '#ff7a1a'; C.beginPath(); C.arc(x, y, 3.4, 0, 7); C.fill(); C.fillStyle = '#ffe08a'; C.beginPath(); C.arc(x, y, 1.8, 0, 7); C.fill();
  }
}
function drawSpring(sp) {
  const x = Math.round(sp.x), y = Math.round(sp.y), h = sp.t > 0 ? 7 : 15;
  R(x + 1, y + 16 - 3, 14, 3, dk()); R(x + 2, y + 16 - 2, 12, 1.5, '#8a90a8');
  C.strokeStyle = dk(); C.lineWidth = 3; C.beginPath(); const n = 4; for (let i = 0; i <= n; i++) { const yy = y + 14 - (h - 5) * i / n, xx = x + 8 + (i % 2 ? 5 : -5); C.lineTo(xx, yy); } C.stroke();
  C.strokeStyle = '#d0d6e8'; C.lineWidth = 1.4; C.beginPath(); for (let i = 0; i <= n; i++) { const yy = y + 14 - (h - 5) * i / n, xx = x + 8 + (i % 2 ? 5 : -5); C.lineTo(xx, yy); } C.stroke();
  R(x, y + 16 - h - 2, 16, 4, dk()); R(x + 1, y + 16 - h - 1, 14, 2.4, '#ff4a4a'); R(x + 1, y + 16 - h - 1, 14, 1, '#ff9a9a');
}
function drawWarpArrow(x, y, frame) {
  const b = Math.sin(frame / 8) * 2;
  C.fillStyle = dk(); C.beginPath(); C.moveTo(x - 5, y - 12 + b); C.lineTo(x + 5, y - 12 + b); C.lineTo(x, y - 4 + b); C.fill();
  C.fillStyle = '#ffe14a'; C.beginPath(); C.moveTo(x - 3.6, y - 10.8 + b); C.lineTo(x + 3.6, y - 10.8 + b); C.lineTo(x, y - 5.6 + b); C.fill();
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
  if (p.type === 'bal' && !p.broken) { R(x + p.w / 2 - 0.6, 2 * T, 1.2, y - 2 * T, '#d8d8e0'); }
  const c = p.type === 'fall' ? ['#e8743a', '#ffb07a', '#9a4a1a'] : p.type === 'bal' ? ['#c8c8d8', '#ffffff', '#7a7a90'] : ['#f2b84a', '#ffe08a', '#b8780f'];
  R(x, y, p.w, 8, '#1a0f0c'); R(x + 1, y + 1, p.w - 2, 6, c[0]); R(x + 1, y + 1, p.w - 2, 2, c[1]);
  for (let i = 6; i < p.w - 4; i += 12) R(x + i, y + 4, 4, 2, c[2]);
  if (p.type === 'bal') return;
}
function drawPulley(a, b) {
  if (a.broken) return;
  const x1 = a.x + a.w / 2, x2 = b.x + b.w / 2, y = 2 * T;
  R(Math.min(x1, x2), y - 1, Math.abs(x2 - x1), 2, '#d8d8e0'); blob(Math.round(x1), y, 3, '#7a7a90'); blob(Math.round(x2), y, 3, '#7a7a90');
}

window.SFB.Art = { THEMES, bind, setVW, R, blob, IMG, loadImages, drawTile, drawSky, drawLavaBand, drawHero, drawEnemy, drawPlant, drawFirebar, drawSpring, drawWarpArrow, drawCoin, drawItem, drawPole, drawCastle, drawCheck, drawPlat, drawPulley, drawBall, drawWaterBand, drawVine, drawAxe, drawFirework, drawFriend, drawHammerShape, star5, heart, head, hurtBlink, glyph };
})();
