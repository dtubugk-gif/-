'use strict';
/* Light Trails — neon snakes that leave walls behind them. Last one alive wins. */
Games.push({
  id: 'trails', name: 'Neon Snake', control: 'HOLD TO TURN',
  desc: 'Your snake leaves a glowing wall behind it. Hold to turn left. Do not crash. Last one alive wins!',
  color: '#F72585', grad: ['#F72585', '#7209B7'], gradDark: '#4A0A7A',
  icon: `<svg viewBox="0 0 64 64"><path d="M10 54V30q0-12 12-12h14q10 0 10 10v8q0 6 6 6h2" stroke="#fff" stroke-width="7" fill="none" stroke-linecap="round" stroke-linejoin="round"/><circle cx="54" cy="42" r="6" fill="#fff"/><path d="M50 10v14" stroke="#fff" stroke-width="7" stroke-linecap="round" opacity=".5"/></svg>`,

  create(ctx) {
    const { players, S, arena: A } = ctx;
    const m = 6;
    const F = { x: A.x + m, y: A.y + m, w: A.w - m * 2, h: A.h - m * 2 };
    const c = Math.max(4, S * 0.02);
    const cols = Math.floor(F.w / c), rows = Math.floor(F.h / c);
    const owner = new Int8Array(cols * rows), stamp = new Float32Array(cols * rows);
    const TURN = 3.0, V0 = S * 0.24;
    let now = 0;
    const dpr = Engine.dpr;
    const layer = document.createElement('canvas');
    layer.width = Math.ceil(A.w * dpr); layer.height = Math.ceil(A.h * dpr);
    const lg = layer.getContext('2d');
    lg.scale(dpr, dpr); lg.translate(-A.x, -A.y); lg.lineCap = 'round'; lg.lineJoin = 'round';

    const N = players.map((p) => {
      const ang = p.lay.dir + Math.PI + 0.45;
      return { p, x: p.lay.home.x, y: p.lay.home.y, ang, dead: false, cell: -1, think: 0, hold: false, holdT: 0 };
    });

    const cellOf = (x, y) => {
      const i = Math.floor((x - F.x) / c), j = Math.floor((y - F.y) / c);
      if (i < 0 || j < 0 || i >= cols || j >= rows) return -1;
      return j * cols + i;
    };
    const blocked = (idx, slot, t) => idx < 0 || (owner[idx] && !(owner[idx] === slot + 1 && t - stamp[idx] < 0.22));

    function simFree(s, turning, horizon) {
      let x = s.x, y = s.y, a = s.ang;
      const v = V0 * (1 + now * 0.015), step = c * 0.8, n = Math.ceil((v * horizon) / step);
      let last = s.cell;
      for (let k = 1; k <= n; k++) {
        if (turning) a -= TURN * (step / v);
        x += Math.cos(a) * step; y += Math.sin(a) * step;
        const idx = cellOf(x, y);
        if (idx === last) continue;
        if (idx < 0 || (owner[idx] && !(owner[idx] === s.p.slot + 1 && now - stamp[idx] < 0.3))) return k / n;
        last = idx;
      }
      return 1;
    }
    function ai(s, dt) {
      s.think -= dt; s.holdT -= dt;
      if (s.think > 0) return;
      s.think = rand(0.03, 0.06);
      const fs = simFree(s, false, 0.9), ft = simFree(s, true, 0.9);
      if (fs >= 1 && ft >= 1) { if (s.holdT <= 0) s.hold = Math.random() < 0.06 ? (s.holdT = rand(0.15, 0.45), true) : false; }
      else s.hold = ft > fs + 0.05 ? true : fs > ft + 0.05 ? false : s.hold;
    }

    return {
      where(p) { const s = N.find((x) => x.p === p); return { x: s.x, y: s.y, r: c * 1.5 }; },
      update(dt) {
        now += dt;
        const v = V0 * (1 + now * 0.015);
        const dead = [];
        for (const s of N) {
          if (s.dead) continue;
          const p = s.p, col = COLORS[p.slot];
          if (!p.human && !ctx.over) { ai(s, dt); ctx.botHold(p, s.hold); }
          const steps = Math.max(1, Math.ceil((v * dt) / (c * 0.45)));
          const sdt = dt / steps;
          const ox = s.x, oy = s.y;
          for (let k = 0; k < steps && !s.dead; k++) {
            if (p.down) s.ang -= TURN * sdt;
            const px = s.x, py = s.y;
            s.x += Math.cos(s.ang) * v * sdt; s.y += Math.sin(s.ang) * v * sdt;
            const idx = cellOf(s.x, s.y);
            if (idx === s.cell) continue;
            const checks = [idx];
            if (s.cell >= 0 && idx >= 0) {
              const oi = s.cell % cols, oj = (s.cell / cols) | 0, ni = idx % cols, nj = (idx / cols) | 0;
              if (oi !== ni && oj !== nj) checks.push(nj * cols + oi);
            }
            if (checks.some((q) => blocked(q, p.slot, now))) {
              s.dead = true; s.x = px; s.y = py; dead.push(p);
              FX.burst(s.x, s.y, col.main, 26, 300, 5, 0.8); FX.burst(s.x, s.y, '#fff', 10, 200, 3, 0.5);
              FX.ring(s.x, s.y, col.light, 60, 0.5, 6); FX.addShake(6); Sfx.hit();
              break;
            }
            for (const q of checks) { owner[q] = p.slot + 1; stamp[q] = now; }
            s.cell = idx;
          }
          lg.strokeStyle = col.glow; lg.lineWidth = c * 1.9;
          lg.beginPath(); lg.moveTo(ox, oy); lg.lineTo(s.x, s.y); lg.stroke();
          lg.strokeStyle = col.main; lg.lineWidth = c * 0.95;
          lg.beginPath(); lg.moveTo(ox, oy); lg.lineTo(s.x, s.y); lg.stroke();
        }
        if (dead.length) ctx.eliminate(dead);
      },

      draw(g) {
        g.strokeStyle = 'rgba(247,37,133,.45)'; g.lineWidth = 3;
        g.shadowColor = '#F72585'; g.shadowBlur = 12;
        rrect(g, F.x, F.y, F.w, F.h, 10); g.stroke();
        g.shadowBlur = 0;
        g.drawImage(layer, A.x, A.y, A.w, A.h);
        for (const s of N) {
          if (s.dead) continue;
          const col = COLORS[s.p.slot];
          g.shadowColor = col.main; g.shadowBlur = 18;
          g.fillStyle = col.light; g.beginPath(); g.arc(s.x, s.y, c * 1.05, 0, TAU); g.fill();
          g.shadowBlur = 0;
          g.fillStyle = '#fff'; g.beginPath(); g.arc(s.x, s.y, c * 0.5, 0, TAU); g.fill();
          // direction tick
          g.strokeStyle = 'rgba(255,255,255,.5)'; g.lineWidth = 2;
          g.beginPath(); g.moveTo(s.x + Math.cos(s.ang) * c * 1.6, s.y + Math.sin(s.ang) * c * 1.6);
          g.lineTo(s.x + Math.cos(s.ang) * c * 2.6, s.y + Math.sin(s.ang) * c * 2.6); g.stroke();
        }
      },
    };
  },
});
