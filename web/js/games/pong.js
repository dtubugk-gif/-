'use strict';
/* Ping Pong — your paddle slides by itself; tap to reverse it. Defend your edge. 3 lives. */
Games.push({
  id: 'pong', name: 'Ping Pong', control: 'TAP TO TURN',
  desc: 'Your paddle slides on its own. Tap to change direction. Don\'t let the ball past your edge. Last one standing wins the round!',
  color: '#8B5CF6', grad: ['#C4B5FD', '#7C3AED'], gradDark: '#4C1D95',
  icon: `<svg viewBox="0 0 64 64"><rect x="14" y="8" width="36" height="7" rx="3.5" fill="#fff"/><rect x="14" y="49" width="36" height="7" rx="3.5" fill="#fff" opacity=".7"/><circle cx="36" cy="32" r="6" fill="#fff"/><path d="M22 22l8 6" stroke="#fff" stroke-width="3" stroke-linecap="round" stroke-dasharray="2 5"/></svg>`,

  create(ctx) {
    const { players, S, arena: A, cx, cy } = ctx;
    const n = players.length, LIVES = players.length > 2 ? 1 : 2, BR = S * 0.022, PH = 10;
    const inset = 16;
    const segOf = (p) => {
      const top = p.lay.side === 'top';
      const y = top ? A.y + inset : A.y + A.h - inset;
      if (n === 2) return { x0: A.x, x1: A.x + A.w, y, top };
      const left = p.lay.btn.x < A.w / 2;
      return { x0: left ? A.x : A.x + A.w / 2, x1: left ? A.x + A.w / 2 : A.x + A.w, y, top };
    };
    const Pd = players.map((p, i) => {
      const s = segOf(p), w = s.x1 - s.x0;
      return { p, s, pw: n === 2 ? A.w * 0.27 : w * 0.38, px: (s.x0 + s.x1) / 2, dir: i % 2 ? 1 : -1, spd: n === 2 ? A.w * 0.75 : w * 1.05, lives: LIVES, think: 0, err: 0, hit: 0 };
    });
    const balls = [];
    let serveT = 0.6, extraAdded = false;

    function owner(top, x) {
      return Pd.find((d) => d.s.top === top && x >= d.s.x0 && x <= d.s.x1 && !d.p.out) || null;
    }
    function serve() {
      const alive = Pd.filter((d) => !d.p.out);
      const tgt = pick(alive);
      const ang = Math.atan2(tgt.s.y - cy, (tgt.s.x0 + tgt.s.x1) / 2 - cx) + rand(-0.25, 0.25);
      const sp = S * Math.min(1.2, 0.72 + ctx.t * 0.02);
      balls.push({ x: cx, y: cy, vx: Math.cos(ang) * sp, vy: Math.sin(ang) * sp, sp, born: 0 });
    }
    function predict(b, yLine) {
      const t = (yLine - b.y) / b.vy;
      let x = b.x + b.vx * t;
      const lo = A.x + BR, hi = A.x + A.w - BR, w = hi - lo;
      x -= lo; x = ((x % (2 * w)) + 2 * w) % (2 * w); if (x > w) x = 2 * w - x;
      return { x: x + lo, t };
    }

    return {
      where(p) { const d = Pd.find((x) => x.p === p); return { x: d.px, y: d.s.y, r: d.pw * 0.35 }; },
      label() { return 'TURN'; },
      update(dt) {
        const wantBalls = ctx.t > 10 && Pd.filter((d) => !d.p.out).length > 1 ? 2 : 1;
        if (balls.length < wantBalls) {
          serveT -= dt;
          if (serveT <= 0) { serve(); serveT = 0.9; if (!extraAdded && wantBalls === 2) { extraAdded = true; Sfx.tone(660, 0.2, { type: 'square', vol: 0.08 }); } }
        }
        for (const d of Pd) {
          if (d.p.out) continue;
          d.hit = Math.max(0, d.hit - dt * 4);
          if (!d.p.human && !ctx.over) {
            d.think -= dt;
            if (d.think <= 0) {
              d.think = rand(0.1, 0.2);
              let best = null;
              for (const b of balls) {
                if ((d.s.top && b.vy >= 0) || (!d.s.top && b.vy <= 0)) continue;
                const pr = predict(b, d.s.y);
                if (pr.x < d.s.x0 - BR || pr.x > d.s.x1 + BR) continue;
                if (!best || pr.t < best.t) best = pr;
              }
              if (best && d.lastT !== Math.round(best.t * 2)) { d.err = (Math.random() < 0.3 ? rand(-1, 1) : rand(-0.35, 0.35)) * d.pw; d.lastT = Math.round(best.t * 2); }
              const want = best ? clamp(best.x + d.err, d.s.x0, d.s.x1) : (d.s.x0 + d.s.x1) / 2 + Math.sin(ctx.t) * d.pw * 0.3;
              if ((want - d.px) * d.dir < -d.pw * 0.12) ctx.botTap(d.p);
            }
          }
          if (d.p.pressed) { d.dir *= -1; Sfx.tone(520, 0.04, { type: 'triangle', vol: 0.07 }); }
          d.px += d.dir * d.spd * dt;
          const lo = d.s.x0 + d.pw / 2, hi = d.s.x1 - d.pw / 2;
          if (d.px < lo) { d.px = lo; d.dir = 1; }
          if (d.px > hi) { d.px = hi; d.dir = -1; }
        }
        const losers = [];
        for (let i = balls.length - 1; i >= 0; i--) {
          const b = balls[i];
          b.born += dt;
          const px = b.x, py = b.y;
          b.x += b.vx * dt; b.y += b.vy * dt;
          if (b.x < A.x + BR) { b.x = A.x + BR; b.vx = Math.abs(b.vx); Sfx.bounce(); }
          if (b.x > A.x + A.w - BR) { b.x = A.x + A.w - BR; b.vx = -Math.abs(b.vx); Sfx.bounce(); }
          for (const top of [true, false]) {
            const lineY = top ? A.y + inset + PH / 2 + BR : A.y + A.h - inset - PH / 2 - BR;
            const crossing = top ? (b.vy < 0 && py >= lineY && b.y < lineY) : (b.vy > 0 && py <= lineY && b.y > lineY);
            if (!crossing) continue;
            const d = owner(top, b.x);
            if (!d) { b.y = lineY; b.vy = -b.vy; Sfx.bounce(); FX.burst(b.x, lineY, 'rgba(255,255,255,.6)', 5, 100, 2, 0.3); continue; }
            if (Math.abs(b.x - d.px) < d.pw / 2 + BR) {
              const off = (b.x - d.px) / (d.pw / 2);
              b.sp = Math.min(S * 1.9, b.sp * 1.07);
              let ang = off * 1.05;
              if (Math.abs(ang) < 0.18) ang = (Math.random() < 0.5 ? -1 : 1) * rand(0.18, 0.4);
              b.vx = Math.sin(ang) * b.sp; b.vy = (top ? 1 : -1) * Math.cos(ang) * b.sp;
              b.y = lineY; d.hit = 1;
              Sfx.tone(440 + Math.random() * 80, 0.07, { type: 'square', vol: 0.08 });
              FX.burst(b.x, lineY, COLORS[d.p.slot].light, 8, 160, 3, 0.35);
            }
          }
          const gone = b.y < A.y - BR || b.y > A.y + A.h + BR;
          if (gone) {
            const top = b.y < A.y;
            const d = owner(top, clamp(b.x, A.x, A.x + A.w - 1));
            balls.splice(i, 1);
            if (d) {
              d.lives--; Sfx.hit(); FX.addShake(7); ctx.buzz(60);
              FX.burst(clamp(b.x, A.x, A.x + A.w), top ? A.y : A.y + A.h, COLORS[d.p.slot].main, 24, 300, 5, 0.7);
              FX.float(d.px, d.s.y + (top ? 40 : -40), '-1', '#FF6B6B', d.p.lay.rot, 30);
              if (d.lives <= 0) losers.push(d.p);
            }
          } else if (b.sp > S * 0.9 && Math.random() < 0.5) FX.trail(b.x, b.y, 'rgba(255,255,255,.5)', 2.5, 0.25);
        }
        if (losers.length) ctx.eliminate(losers);
      },

      draw(g) {
        g.strokeStyle = 'rgba(255,255,255,.12)'; g.lineWidth = 3; g.setLineDash([10, 12]);
        g.beginPath(); g.moveTo(A.x, cy); g.lineTo(A.x + A.w, cy); g.stroke(); g.setLineDash([]);
        g.beginPath(); g.arc(cx, cy, S * 0.12, 0, TAU); g.stroke();
        // edges: colored goal strips, grey walls
        for (const top of [true, false]) {
          const y = top ? A.y : A.y + A.h - 5;
          g.fillStyle = '#4A5080'; g.fillRect(A.x, y, A.w, 5);
          for (const d of Pd) {
            if (d.s.top !== top || d.p.out) continue;
            const col = COLORS[d.p.slot];
            g.fillStyle = col.main; g.fillRect(d.s.x0, y, d.s.x1 - d.s.x0, 5);
            const grd = g.createLinearGradient(0, top ? A.y : A.y + A.h, 0, top ? A.y + 40 : A.y + A.h - 40);
            grd.addColorStop(0, col.glow); grd.addColorStop(1, 'rgba(0,0,0,0)');
            g.fillStyle = grd; g.globalAlpha = 0.35; g.fillRect(d.s.x0, top ? A.y : A.y + A.h - 40, d.s.x1 - d.s.x0, 40); g.globalAlpha = 1;
          }
        }
        for (const d of Pd) {
          if (d.p.out) continue;
          const col = COLORS[d.p.slot];
          g.shadowColor = col.main; g.shadowBlur = 12 + d.hit * 18;
          g.fillStyle = col.main; rrect(g, d.px - d.pw / 2, d.s.y - PH / 2, d.pw, PH, PH / 2); g.fill();
          g.shadowBlur = 0;
          g.fillStyle = 'rgba(255,255,255,.45)'; rrect(g, d.px - d.pw / 2 + 4, d.s.y - PH / 2 + 2, d.pw - 8, 3, 2); g.fill();
          g.fillStyle = '#fff'; g.beginPath(); g.arc(d.px + d.dir * (d.pw / 2 - 8), d.s.y, 2.5, 0, TAU); g.fill();
        }
        for (const b of balls) {
          const s = Math.min(1, b.born * 5);
          g.shadowColor = '#fff'; g.shadowBlur = 16;
          g.fillStyle = '#fff'; g.beginPath(); g.arc(b.x, b.y, BR * s, 0, TAU); g.fill();
          g.shadowBlur = 0;
        }
        for (const d of Pd) ctx.hudPips(d.p, Math.max(0, d.lives), LIVES);
      },
    };
  },
});
