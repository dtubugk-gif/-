'use strict';
/* Paint Fight — roll around painting the floor your color. Most paint after 30 seconds wins. */
Games.push({
  id: 'paint', name: 'Paint Fight', control: 'HOLD TO MOVE',
  desc: 'You spin. Hold to roll and paint the floor your color. Grab paint bombs for big splashes! Most paint in 20 seconds wins.',
  color: '#EC4899', grad: ['#F9A8D4', '#DB2777'], gradDark: '#831843',
  icon: `<svg viewBox="0 0 64 64"><rect x="8" y="8" width="22" height="22" rx="5" fill="#fff"/><rect x="34" y="8" width="22" height="22" rx="5" fill="#fff" opacity=".45"/><rect x="8" y="34" width="22" height="22" rx="5" fill="#fff" opacity=".45"/><rect x="34" y="34" width="22" height="22" rx="5" fill="#fff"/><circle cx="32" cy="32" r="9" fill="#DB2777" stroke="#fff" stroke-width="3"/></svg>`,

  create(ctx) {
    const { players, S, arena: A } = ctx;
    const TIME = 20;
    const c = S / 11;
    const cols = Math.floor((A.w - 16) / c), rows = Math.floor((A.h - 16) / c);
    const gx = A.x + (A.w - cols * c) / 2, gy = A.y + (A.h - rows * c) / 2;
    const own = new Int8Array(cols * rows).fill(-1), popT = new Float32Array(cols * rows);
    const count = new Map(players.map((p) => [p, 0]));
    const PR = c * 0.42;
    const P = players.map((p) => ({ p, x: p.lay.home.x, y: p.lay.home.y, vx: 0, vy: 0, ang: p.lay.dir + Math.PI, think: 0, want: false, tx: 0, ty: 0 }));
    const bombs = [];
    let bombT = 3, left = TIME, lastSec = TIME;

    function paintAt(x, y, rad, q) {
      const i0 = Math.floor((x - gx - rad) / c), i1 = Math.floor((x - gx + rad) / c);
      const j0 = Math.floor((y - gy - rad) / c), j1 = Math.floor((y - gy + rad) / c);
      let fresh = 0;
      for (let j = Math.max(0, j0); j <= Math.min(rows - 1, j1); j++) for (let i = Math.max(0, i0); i <= Math.min(cols - 1, i1); i++) {
        const ccx = gx + (i + 0.5) * c, ccy = gy + (j + 0.5) * c;
        if (dist(x, y, ccx, ccy) > rad + c * 0.2) continue;
        const k = j * cols + i, prev = own[k];
        if (prev === q.p.i) continue;
        if (prev >= 0) { const o = players[prev]; count.set(o, count.get(o) - 1); }
        own[k] = q.p.i; popT[k] = 1; count.set(q.p, count.get(q.p) + 1); fresh++;
      }
      return fresh;
    }
    function ai(q, dt) {
      q.think -= dt;
      if (q.think <= 0) {
        q.think = rand(0.12, 0.22);
        let best = null, bd = 1e9;
        for (const b of bombs) { const d = dist(q.x, q.y, b.x, b.y) * 0.5; if (d < bd) { bd = d; best = b; } }
        for (let k = 0; k < 60; k++) {
          const i = randi(0, cols - 1), j = randi(0, rows - 1);
          if (own[j * cols + i] === q.p.i) continue;
          const x = gx + (i + 0.5) * c, y = gy + (j + 0.5) * c;
          const d = dist(q.x, q.y, x, y) - (Math.cos(Math.atan2(y - q.y, x - q.x) - q.ang) * c * 1.5);
          if (d < bd) { bd = d; best = { x, y }; }
        }
        if (best) { q.tx = best.x; q.ty = best.y; }
      }
      const diff = Math.abs(angNorm(Math.atan2(q.ty - q.y, q.tx - q.x) - q.ang));
      q.want = diff < 0.45;
    }

    for (const q of P) paintAt(q.x, q.y, c * 1.3, q);

    return {
      where(p) { const q = P.find((x) => x.p === p); return { x: q.x, y: q.y, r: PR }; },
      update(dt) {
        left = Math.max(0, TIME - ctx.t);
        if (Math.ceil(left) < lastSec) { lastSec = Math.ceil(left); if (lastSec <= 5 && lastSec > 0) Sfx.count(); }
        bombT -= dt;
        if (bombT <= 0 && bombs.length < 2) {
          bombT = rand(3, 5);
          bombs.push({ x: gx + rand(1, cols - 1) * c, y: gy + rand(1, rows - 1) * c, t: 0 });
        }
        for (const b of bombs) b.t += dt;
        for (const q of P) {
          const p = q.p;
          if (!p.human && !ctx.over) { ai(q, dt); ctx.botHold(p, q.want); }
          Move.aim(q, p, dt, 2.7);
          Move.drive(q, p, dt, S * 0.55, 9, 7);
          const sp = Math.hypot(q.vx, q.vy), mx = S * 0.55;
          if (sp > mx) { q.vx *= mx / sp; q.vy *= mx / sp; }
          q.x = clamp(q.x + q.vx * dt, gx + PR, gx + cols * c - PR);
          q.y = clamp(q.y + q.vy * dt, gy + PR, gy + rows * c - PR);
          if (paintAt(q.x, q.y, c * 0.75, q) && p.human) Sfx.tap(p.slot);
          for (let i = bombs.length - 1; i >= 0; i--) {
            const b = bombs[i];
            if (dist(q.x, q.y, b.x, b.y) < PR + c * 0.4) {
              bombs.splice(i, 1);
              paintAt(b.x, b.y, c * 2.4, q);
              Sfx.boom(); FX.addShake(6); ctx.buzz(40);
              FX.burst(b.x, b.y, COLORS[p.slot].main, 34, 380, 7, 0.8); FX.ring(b.x, b.y, COLORS[p.slot].light, c * 3, 0.5, 8);
            }
          }
        }
        for (let i = 0; i < P.length; i++) for (let j = i + 1; j < P.length; j++) {
          const a = P[i], b = P[j], d = dist(a.x, a.y, b.x, b.y);
          if (d < PR * 2 && d > 0) {
            const nx = (b.x - a.x) / d, ny = (b.y - a.y) / d, o = (PR * 2 - d) / 2;
            a.x -= nx * o; a.y -= ny * o; b.x += nx * o; b.y += ny * o;
            const vr = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny;
            if (vr > 0) { a.vx -= vr * nx * 1.2; a.vy -= vr * ny * 1.2; b.vx += vr * nx * 1.2; b.vy += vr * ny * 1.2; Sfx.bounce(); }
          }
        }
        for (let k = 0; k < popT.length; k++) if (popT[k] > 0) popT[k] = Math.max(0, popT[k] - dt * 4);
        if (left <= 0) {
          const rank = [...players].sort((a, b) => count.get(b) - count.get(a));
          ctx.end(rank, rank.length > 1 && count.get(rank[0]) === count.get(rank[1]));
        }
      },

      draw(g) {
        for (let j = 0; j < rows; j++) for (let i = 0; i < cols; i++) {
          const k = j * cols + i, o = own[k];
          const x = gx + i * c, y = gy + j * c;
          if (o < 0) { g.fillStyle = (i + j) % 2 ? '#1D2246' : '#20264E'; g.fillRect(x, y, c, c); continue; }
          const col = COLORS[players[o].slot];
          const s = 1 + popT[k] * 0.25;
          g.fillStyle = col.dark; g.fillRect(x, y, c, c);
          g.fillStyle = col.main;
          const w = (c - 3) * s;
          rrect(g, x + c / 2 - w / 2, y + c / 2 - w / 2, w, w, c * 0.25); g.fill();
        }
        for (const b of bombs) {
          const s = Math.min(1, b.t * 4), bob = Math.sin(b.t * 6) * 3;
          g.save(); g.translate(b.x, b.y + bob); g.scale(s, s);
          g.shadowColor = '#fff'; g.shadowBlur = 16;
          const grd = g.createConicGradient ? g.createConicGradient(b.t * 3, 0, 0) : null;
          if (grd) { grd.addColorStop(0, '#FF4D6D'); grd.addColorStop(0.33, '#3A86FF'); grd.addColorStop(0.66, '#2DD881'); grd.addColorStop(1, '#FF4D6D'); }
          g.fillStyle = grd || '#fff';
          g.beginPath(); g.arc(0, 0, c * 0.4, 0, TAU); g.fill();
          g.shadowBlur = 0;
          g.fillStyle = '#fff'; g.beginPath(); g.arc(0, 0, c * 0.16, 0, TAU); g.fill();
          g.restore();
        }
        for (const q of P) {
          const col = COLORS[q.p.slot];
          drawAim(g, q.x, q.y, PR, q.ang, q.spinDir, col.light, q.p.down);
          g.strokeStyle = '#fff'; g.lineWidth = 3;
          g.beginPath(); g.arc(q.x, q.y, PR + 2, 0, TAU); g.stroke();
          drawBlob(g, q.x, q.y, PR, col, { ang: q.ang });
        }
        const total = cols * rows;
        for (const q of P) ctx.hudText(q.p, `${Math.round((count.get(q.p) / total) * 100)}%`, null, -16, 18);
        ctx.mirror((g) => {
          g.globalAlpha = left < 5 ? 0.9 : 0.5;
          text(g, String(Math.ceil(left)), 0, 26, left < 5 ? 34 : 26, left < 5 ? '#FF6B6B' : '#fff');
          g.globalAlpha = 1;
        });
      },
    };
  },
});
