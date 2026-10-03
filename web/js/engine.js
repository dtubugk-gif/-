'use strict';
/* ===== Engine: layout, multi-touch input, game loop, effects, phases ===== */

const Games = []; // each game file pushes its definition here

const FX = {
  parts: [], rings: [], floats: [], shake: 0,
  reset() { this.parts.length = 0; this.rings.length = 0; this.floats.length = 0; this.shake = 0; },
  burst(x, y, color, n = 14, spd = 240, size = 5, life = 0.7, grav = 0) {
    for (let i = 0; i < n; i++) {
      const a = Math.random() * TAU, s = spd * rand(0.3, 1);
      this.parts.push({ x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s, life: life * rand(0.6, 1), max: life, color, size: size * rand(0.6, 1.3), grav });
    }
  },
  trail(x, y, color, size = 4, life = 0.4, vx = 0, vy = 0) {
    this.parts.push({ x, y, vx: vx + rand(-20, 20), vy: vy + rand(-20, 20), life, max: life, color, size, grav: 0 });
  },
  ring(x, y, color, r1 = 60, life = 0.45, w = 6) { this.rings.push({ x, y, color, r1, life, max: life, w }); },
  float(x, y, str, color, rot = 0, size = 26) { this.floats.push({ x, y, str, color, rot, size, life: 0.9, max: 0.9 }); },
  addShake(v) { this.shake = Math.max(this.shake, v); },
  update(dt) {
    for (let i = this.parts.length - 1; i >= 0; i--) {
      const p = this.parts[i];
      p.life -= dt;
      if (p.life <= 0) { this.parts.splice(i, 1); continue; }
      p.vx *= 1 - 2.2 * dt; p.vy = p.vy * (1 - 2.2 * dt) + p.grav * dt;
      p.x += p.vx * dt; p.y += p.vy * dt;
    }
    for (let i = this.rings.length - 1; i >= 0; i--) { const r = this.rings[i]; r.life -= dt; if (r.life <= 0) this.rings.splice(i, 1); }
    for (let i = this.floats.length - 1; i >= 0; i--) { const f = this.floats[i]; f.life -= dt; if (f.life <= 0) this.floats.splice(i, 1); }
    this.shake = Math.max(0, this.shake - dt * 30);
  },
  draw(g) {
    for (const p of this.parts) {
      const k = p.life / p.max;
      g.globalAlpha = Math.min(1, k * 1.5);
      g.fillStyle = p.color;
      g.beginPath(); g.arc(p.x, p.y, p.size * (0.4 + 0.6 * k), 0, TAU); g.fill();
    }
    g.globalAlpha = 1;
    for (const r of this.rings) {
      const k = 1 - r.life / r.max;
      g.globalAlpha = 1 - k;
      g.strokeStyle = r.color; g.lineWidth = r.w * (1 - k) + 1;
      g.beginPath(); g.arc(r.x, r.y, r.r1 * easeOutCubic(k), 0, TAU); g.stroke();
    }
    g.globalAlpha = 1;
    for (const f of this.floats) {
      const k = 1 - f.life / f.max;
      g.save();
      g.translate(f.x, f.y); g.rotate(f.rot); g.translate(0, -k * 40);
      g.globalAlpha = Math.min(1, (1 - k) * 2);
      const s = k < 0.15 ? easeOutBack(k / 0.15) : 1;
      g.scale(s, s);
      text(g, f.str, 0, 0, f.size, f.color);
      g.restore();
    }
    g.globalAlpha = 1;
  },
};

const Engine = {
  canvas: null, g: null, W: 0, H: 0, dpr: 1,
  players: [], def: null, inst: null,
  phase: 'idle', phaseT: 0, playT: 0, running: false, paused: false,
  arena: { x: 0, y: 0, w: 0, h: 0 }, S: 0, barH: 0,
  onEnd: null, result: null, outOrder: [], bg: null, last: 0, lastCount: 0,
  pointers: new Map(),

  init() {
    this.canvas = document.getElementById('game');
    this.g = this.canvas.getContext('2d');
    window.addEventListener('resize', () => this.resize());
    const c = this.canvas;
    const opts = { passive: false };
    c.addEventListener('pointerdown', (e) => this.onDown(e), opts);
    c.addEventListener('pointerup', (e) => this.onUp(e), opts);
    c.addEventListener('pointercancel', (e) => this.onUp(e), opts);
    c.addEventListener('pointerleave', (e) => this.onUp(e), opts);
    c.addEventListener('contextmenu', (e) => e.preventDefault());
    this.resize();
    this.loop = this.loop.bind(this);
  },

  resize() {
    this.W = window.innerWidth; this.H = window.innerHeight;
    this.dpr = Math.min(window.devicePixelRatio || 1, 2.5);
    this.canvas.width = Math.round(this.W * this.dpr);
    this.canvas.height = Math.round(this.H * this.dpr);
    this.layout();
    this.bg = null;
  },

  layout() {
    const W = this.W, H = this.H, n = this.players.length;
    const barH = this.barH = clamp(H * 0.135, 78, 128);
    const a = this.arena = { x: 0, y: barH, w: W, h: H - barH * 2 };
    this.S = Math.min(a.w, a.h);
    const m = 8;
    const full = { w: W - m * 2, h: barH - m * 1.5 };
    const half = { w: W / 2 - m * 1.5, h: barH - m * 1.5 };
    const yb = H - barH + m * 0.5, yt = m;
    let lays;
    if (n <= 2) {
      lays = [
        { side: 'bottom', rot: 0, btn: { x: m, y: yb, ...full }, home: { x: a.x + a.w / 2, y: a.y + a.h * 0.84 } },
        { side: 'top', rot: Math.PI, btn: { x: m, y: yt, ...full }, home: { x: a.x + a.w / 2, y: a.y + a.h * 0.16 } },
      ];
    } else {
      const BL = { side: 'bottom', rot: 0, btn: { x: m, y: yb, ...half }, home: { x: a.x + a.w * 0.24, y: a.y + a.h * 0.82 } };
      const TR = { side: 'top', rot: Math.PI, btn: { x: W / 2 + m * 0.5, y: yt, ...half }, home: { x: a.x + a.w * 0.76, y: a.y + a.h * 0.18 } };
      const TL = { side: 'top', rot: Math.PI, btn: { x: m, y: yt, ...half }, home: { x: a.x + a.w * 0.24, y: a.y + a.h * 0.18 } };
      const BR = { side: 'bottom', rot: 0, btn: { x: W / 2 + m * 0.5, y: yb, ...half }, home: { x: a.x + a.w * 0.76, y: a.y + a.h * 0.82 } };
      lays = [BL, TR, TL, BR];
    }
    const cx = a.x + a.w / 2, cy = a.y + a.h / 2;
    this.players.forEach((p, i) => {
      p.lay = lays[i];
      p.lay.dir = Math.atan2(p.lay.home.y - cy, p.lay.home.x - cx);
    });
  },

  /* ---------- input ---------- */
  onDown(e) {
    e.preventDefault();
    Sfx.init();
    if (this.paused || !this.running) return;
    const x = e.clientX, y = e.clientY;
    for (const p of this.players) {
      const b = p.lay.btn;
      if (x >= b.x - 4 && x <= b.x + b.w + 4 && y >= b.y - 4 && y <= b.y + b.h + 4) {
        if (!p.human) return;
        this.pointers.set(e.pointerId, p);
        p.ptrs++;
        if (p.ptrs === 1) { p.down = true; p.pressed = true; }
        p.taps++; p.flash = 1;
        return;
      }
    }
  },
  onUp(e) {
    const p = this.pointers.get(e.pointerId);
    if (!p) return;
    this.pointers.delete(e.pointerId);
    p.ptrs = Math.max(0, p.ptrs - 1);
    if (p.ptrs === 0) { p.down = false; p.released = true; }
  },
  releaseAll() {
    this.pointers.clear();
    for (const p of this.players) { p.ptrs = 0; if (p.down) { p.down = false; p.released = true; } }
  },

  botTap(p) {
    if (p.down) return;
    p.down = true; p.pressed = true; p.taps++; p.flash = 1; p.botTapT = 0.07;
  },
  botHold(p, v) {
    if (v && !p.down) { p.down = true; p.pressed = true; p.taps++; p.flash = 1; }
    else if (!v && p.down) { p.down = false; p.released = true; }
  },

  /* ---------- lifecycle ---------- */
  start(def, players, onEnd) {
    this.def = def; this.onEnd = onEnd;
    this.players = players.map((p, i) => ({
      ...p, i, ptrs: 0, down: false, pressed: false, released: false, taps: 0,
      flash: 0, pv: 0, out: false, ready: false, botTapT: 0, botReadyAt: rand(0.5, 1.4),
    }));
    this.layout();
    this.result = null; this.outOrder = [];
    this.pointers.clear();
    FX.reset();
    this.inst = def.create(this.makeCtx());
    this.phase = 'intro'; this.phaseT = 0; this.playT = 0; this.lastCount = 4;
    this.paused = false;
    if (!this.running) { this.running = true; this.last = performance.now(); requestAnimationFrame(this.loop); }
  },
  stop() { this.running = false; this.inst = null; this.phase = 'idle'; this.releaseAll(); },
  pause() { if (!this.running) return; this.paused = true; this.releaseAll(); },
  resume() { this.paused = false; this.last = performance.now(); },

  makeCtx() {
    const E = this;
    const a = E.arena;
    return {
      players: E.players, arena: a, S: E.S, cx: a.x + a.w / 2, cy: a.y + a.h / 2,
      fx: FX, sfx: Sfx,
      get t() { return E.playT; },
      get over() { return E.phase === 'over'; },
      buzz: (ms) => Haptics.buzz(ms),
      end: (r, d) => E.end(r, d),
      endTeam: (winners, rest) => E.end([...winners, ...rest], false, winners),
      hudPips: (p, value, total, y = -14) => E.atPlayer(p, (g) => {
        const s = total > 6 ? 6 : 8, gap = total > 6 ? 5 : 7, tw = total * s * 2 + (total - 1) * gap;
        for (let i = 0; i < total; i++) {
          const x = -tw / 2 + s + i * (s * 2 + gap);
          g.fillStyle = i < value ? COLORS[p.slot].main : 'rgba(255,255,255,.1)';
          g.beginPath(); g.arc(x, y, s, 0, TAU); g.fill();
          if (i < value) { g.fillStyle = 'rgba(255,255,255,.4)'; g.beginPath(); g.arc(x - s * 0.3, y - s * 0.3, s * 0.35, 0, TAU); g.fill(); }
        }
      }),
      hudText: (p, str, color, y = -16, size = 17) => E.atPlayer(p, (g) => text(g, str, 0, y, size, color || COLORS[p.slot].light)),
      eliminate: (ps) => E.eliminate(ps),
      alive: () => E.players.filter((p) => !p.out),
      botTap: (p) => E.botTap(p),
      botHold: (p, v) => E.botHold(p, v),
      mirror: (fn) => E.mirror(fn),
      atPlayer: (p, fn) => E.atPlayer(p, fn),
    };
  },

  end(ranking, draw = false, winners = null) {
    if (this.phase !== 'play') return;
    this.phase = 'over'; this.phaseT = 0;
    winners = draw ? [] : (winners || (ranking[0] ? [ranking[0]] : []));
    this.result = { ranking, draw, winners };
    this.releaseAll();
    if (winners.length) {
      Sfx.win(); Haptics.buzz([30, 60, 30]);
      for (const w of winners) {
        for (let i = 0; i < 3; i++) FX.burst(w.lay.home.x, w.lay.home.y, i === 1 ? '#fff' : COLORS[w.slot].main, 26, 380, 6, 1.1, 300);
        FX.ring(w.lay.home.x, w.lay.home.y, COLORS[w.slot].main, 140, 0.8, 10);
      }
    } else { Sfx.out(); }
  },

  eliminate(ps) {
    if (this.phase !== 'play') return;
    ps = ps.filter((p) => !p.out);
    if (!ps.length) return;
    for (const p of ps) { p.out = true; }
    this.outOrder.push(ps);
    Sfx.out(); Haptics.buzz(60);
    const alive = this.players.filter((p) => !p.out);
    const fallen = [];
    for (let i = this.outOrder.length - 1; i >= 0; i--) fallen.push(...this.outOrder[i]);
    if (alive.length === 1) this.end([alive[0], ...fallen]);
    else if (alive.length === 0) this.end(fallen, ps.length > 1);
  },

  /* ---------- loop ---------- */
  loop(ts) {
    if (!this.running) return;
    let dt = (ts - this.last) / 1000; this.last = ts;
    if (dt > 0.05) dt = 0.05; if (dt < 0) dt = 0;
    if (!this.paused) this.update(dt);
    this.draw();
    requestAnimationFrame(this.loop);
  },

  update(dt) {
    this.phaseT += dt;
    for (const p of this.players) {
      if (p.botTapT > 0) { p.botTapT -= dt; if (p.botTapT <= 0 && p.down) { p.down = false; p.released = true; } }
    }
    if (this.phase === 'intro') {
      for (const p of this.players) {
        if (p.ready) continue;
        if ((p.human && p.pressed) || (!p.human && this.phaseT > p.botReadyAt)) {
          p.ready = true; Sfx.ready();
          const b = p.lay.btn; FX.ring(b.x + b.w / 2, b.y + b.h / 2, COLORS[p.slot].light, b.w * 0.45, 0.5, 8);
        }
      }
      if (this.players.every((p) => p.ready) && this.phaseT > 0.6) { this.phase = 'count'; this.phaseT = 0; }
    } else if (this.phase === 'count') {
      const n = 3 - Math.floor(this.phaseT / 0.75);
      if (n !== this.lastCount && n > 0) { this.lastCount = n; Sfx.count(); }
      if (this.phaseT >= 2.25) { this.phase = 'play'; this.phaseT = 0; Sfx.go(); Haptics.buzz(40); }
    } else if (this.phase === 'play') {
      this.playT += dt;
      this.inst.update(dt);
      if (this.playT > 150 && this.phase === 'play') this.end([...this.players], true); // safety net
    } else if (this.phase === 'over') {
      this.inst.update(dt * 0.35);
      if (this.phaseT > 2.0 && this.onEnd) {
        const cb = this.onEnd; this.onEnd = null;
        cb(this.result);
      }
    }
    FX.update(dt);
    for (const p of this.players) {
      p.flash = Math.max(0, p.flash - dt * 6);
      p.pv = lerp(p.pv, p.down ? 1 : 0, Math.min(1, dt * 30));
      p.pressed = false; p.released = false; p.taps = 0;
    }
  },

  /* ---------- drawing ---------- */
  buildBg() {
    const c = document.createElement('canvas');
    c.width = this.canvas.width; c.height = this.canvas.height;
    const g = c.getContext('2d');
    g.scale(this.dpr, this.dpr);
    const { W, H } = this, a = this.arena;
    g.fillStyle = '#11152C'; g.fillRect(0, 0, W, H);
    const grd = g.createRadialGradient(a.x + a.w / 2, a.y + a.h / 2, 10, a.x + a.w / 2, a.y + a.h / 2, Math.max(a.w, a.h) * 0.7);
    grd.addColorStop(0, '#171C3C'); grd.addColorStop(1, '#0A0D1E');
    g.fillStyle = grd; g.fillRect(a.x, a.y, a.w, a.h);
    g.fillStyle = 'rgba(255,255,255,.045)';
    const step = 24;
    for (let y = a.y + step / 2; y < a.y + a.h; y += step) for (let x = step / 2; x < W; x += step) { g.beginPath(); g.arc(x, y, 1.2, 0, TAU); g.fill(); }
    g.fillStyle = 'rgba(255,255,255,.06)';
    g.fillRect(0, a.y - 1, W, 1); g.fillRect(0, a.y + a.h, W, 1);
    return c;
  },

  draw() {
    const g = this.g, { W, H, dpr } = this;
    g.setTransform(1, 0, 0, 1, 0, 0);
    if (!this.bg) this.bg = this.buildBg();
    g.drawImage(this.bg, 0, 0);
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    if (!this.inst) return;

    const a = this.arena;
    g.save();
    if (FX.shake > 0) g.translate(rand(-1, 1) * FX.shake, rand(-1, 1) * FX.shake);
    g.beginPath(); g.rect(a.x, a.y, a.w, a.h); g.clip();
    this.inst.draw(g);
    g.restore();

    for (const p of this.players) this.drawButton(g, p);
    FX.draw(g);
    if (this.inst.where && (this.phase === 'count' || (this.phase === 'play' && this.playT < 1.2))) this.drawMarkers(g);

    if (this.phase === 'intro') this.drawIntro(g);
    else if (this.phase === 'count') this.drawCount(g);
    else if (this.phase === 'over') this.drawOver(g);
  },

  drawButton(g, p) {
    const b = p.lay.btn, col = COLORS[p.slot];
    const out = p.out;
    const press = p.pv, depth = 7;
    g.save();
    g.translate(b.x + b.w / 2, b.y + b.h / 2);
    g.rotate(p.lay.rot);
    const w = b.w, h = b.h, r = 22;
    const top = -h / 2 + press * (depth - 1);
    g.fillStyle = out ? '#1D2140' : col.dark;
    rrect(g, -w / 2, -h / 2 + depth, w, h - depth, r); g.fill();
    const grd = g.createLinearGradient(0, top, 0, top + h - depth);
    if (out) { grd.addColorStop(0, '#2C3260'); grd.addColorStop(1, '#262B52'); }
    else { grd.addColorStop(0, col.light); grd.addColorStop(0.35, col.main); grd.addColorStop(1, col.main); }
    g.fillStyle = grd;
    rrect(g, -w / 2, top, w, h - depth, r); g.fill();
    if (p.flash > 0 && !out) {
      g.fillStyle = `rgba(255,255,255,${p.flash * 0.35})`;
      rrect(g, -w / 2, top, w, h - depth, r); g.fill();
    }
    g.fillStyle = 'rgba(255,255,255,.22)';
    rrect(g, -w / 2 + 10, top + 5, w - 20, 5, 3); g.fill();

    const cyT = top + (h - depth) / 2;
    let label, sub = p.human ? '' : 'CPU';
    if (this.phase === 'intro') {
      if (p.ready) label = 'READY!';
      else label = p.human ? 'TAP TO READY' : '...';
    } else if (out) label = 'OUT';
    else label = (this.inst.label && this.inst.label(p)) || this.def.control;
    const big = Math.min(h * 0.3, w * 0.12, 30);
    g.globalAlpha = out ? 0.5 : 1;
    text(g, label, 0, cyT + (sub ? -big * 0.18 : 0), big * (label.length > 8 ? 0.72 : 1), '#fff');
    if (sub) text(g, sub, 0, cyT + big * 0.72, big * 0.42, 'rgba(255,255,255,.75)', { shadow: false });
    // cups (tournament)
    if (p.cups != null && p.cupsTarget) {
      const n = p.cupsTarget, s = 7, gap = 4, tw = n * s * 2 + (n - 1) * gap;
      for (let i = 0; i < n; i++) {
        g.fillStyle = i < p.cups ? '#FFE27A' : 'rgba(0,0,0,.18)';
        g.beginPath(); g.arc(-tw / 2 + s + i * (s * 2 + gap), top + (h - depth) - 11, s * 0.62, 0, TAU); g.fill();
      }
    }
    g.globalAlpha = 1;
    g.restore();
  },

  /* "This is you" arrow over each player's character at the start of a game */
  drawMarkers(g) {
    const fade = this.phase === 'play' ? 1 - this.playT / 1.2 : 1;
    const bob = Math.sin(performance.now() / 120) * 4;
    for (const p of this.players) {
      const w = this.inst.where(p);
      if (!w) continue;
      const col = COLORS[p.slot];
      g.save();
      g.globalAlpha = fade;
      g.translate(w.x, w.y); g.rotate(p.lay.rot);
      const r = (w.r || 22) + 10;
      g.strokeStyle = col.light; g.lineWidth = 3; g.setLineDash([6, 6]);
      g.beginPath(); g.arc(0, 0, r, 0, TAU); g.stroke(); g.setLineDash([]);
      g.translate(0, r + 14 + bob);
      g.fillStyle = col.main;
      g.beginPath(); g.moveTo(0, -10); g.lineTo(-10, 4); g.lineTo(10, 4); g.closePath(); g.fill();
      text(g, 'YOU', 0, 18, 14, '#fff');
      g.restore();
    }
  },

  mirror(fn) {
    const a = this.arena, g = this.g;
    const cx = a.x + a.w / 2, cy = a.y + a.h / 2;
    g.save(); g.translate(cx, cy); fn(g, false); g.restore();
    g.save(); g.translate(cx, cy); g.rotate(Math.PI); fn(g, true); g.restore();
  },
  atPlayer(p, fn) {
    const b = p.lay.btn, g = this.g;
    g.save();
    g.translate(b.x + b.w / 2, b.y + b.h / 2);
    g.rotate(p.lay.rot);
    g.translate(0, -b.h / 2 - 6);
    fn(g);
    g.restore();
  },

  drawIntro(g) {
    const a = this.arena, def = this.def;
    g.fillStyle = 'rgba(8,10,24,.6)'; g.fillRect(a.x, a.y, a.w, a.h);
    const k = easeOutBack(Math.min(1, this.phaseT / 0.45));
    this.mirror((g) => {
      const cw = Math.min(a.w - 32, 360), ch = Math.min(a.h / 2 - 22, 176);
      g.save(); g.translate(0, 12 + ch / 2); g.scale(k, k); g.translate(0, -ch / 2);
      g.fillStyle = 'rgba(0,0,0,.3)'; rrect(g, -cw / 2, 6, cw, ch, 24); g.fill();
      g.fillStyle = '#1E2448'; rrect(g, -cw / 2, 0, cw, ch, 24); g.fill();
      g.fillStyle = def.color || '#A66CFF'; rrect(g, -cw / 2, 0, cw, 6, 3); g.fill();
      const ts = Math.min(34, ch * 0.22);
      text(g, def.name, 0, ch * 0.24, ts, '#fff');
      g.font = font(Math.min(17, ch * 0.105), 500);
      const lines = wrapLines(g, def.desc, cw - 36).slice(0, 3);
      lines.forEach((l, i) => text(g, l, 0, ch * 0.47 + i * Math.min(21, ch * 0.13), Math.min(17, ch * 0.105), '#B8BEE6', { weight: 500, shadow: false }));
      const chip = def.control, cs = Math.min(15, ch * 0.09);
      g.font = font(cs, 700);
      const chw = g.measureText(chip).width + 28;
      g.fillStyle = def.color || '#A66CFF'; rrect(g, -chw / 2, ch - cs * 2.6, chw, cs * 1.9, cs); g.fill();
      text(g, chip, 0, ch - cs * 1.65, cs, '#fff', { shadow: false });
      g.restore();
    });
  },

  drawCount(g) {
    const n = 3 - Math.floor(this.phaseT / 0.75);
    const t = (this.phaseT % 0.75) / 0.75;
    const s = t < 0.3 ? easeOutBack(t / 0.3) : 1;
    const a = this.arena;
    this.mirror((g) => {
      g.save(); g.translate(0, a.h * 0.2); g.scale(s, s);
      g.globalAlpha = t > 0.75 ? 1 - (t - 0.75) / 0.25 : 1;
      text(g, String(Math.max(1, n)), 0, 0, Math.min(a.h * 0.16, 110), '#fff');
      g.restore();
    });
  },

  drawOver(g) {
    const r = this.result; if (!r) return;
    const a = this.arena;
    const k = easeOutBack(Math.min(1, this.phaseT / 0.5));
    const ws = r.winners;
    const str = !ws.length ? 'DRAW!' : ws.length > 1 ? ws.map((w) => COLORS[w.slot].name.toUpperCase()).join(' & ') + ' WIN!' : `${COLORS[ws[0].slot].name.toUpperCase()} WINS!`;
    const col = !ws.length ? '#fff' : COLORS[ws[0].slot].main;
    this.mirror((g) => {
      g.save(); g.translate(0, a.h * 0.22); g.scale(k, k);
      const bw = Math.min(a.w - 40, 320), bh = Math.min(70, a.h * 0.12);
      g.fillStyle = 'rgba(0,0,0,.35)'; rrect(g, -bw / 2, -bh / 2 + 6, bw, bh, bh / 2); g.fill();
      g.fillStyle = '#fff'; rrect(g, -bw / 2, -bh / 2, bw, bh, bh / 2); g.fill();
      g.font = font(bh * 0.5);
      const fit = Math.min(1, (bw - 30) / g.measureText(str).width);
      text(g, str, 0, 2, bh * 0.5 * fit, col === '#fff' ? '#1E2448' : col, { shadow: false });
      g.restore();
    });
  },
};
