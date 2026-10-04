'use strict';
/* Snake Arena — eat stars to grow; don't let your head touch anyone's body. */
Games.push({
  id: 'snake', name: 'Snake Arena', control: 'HOLD TO TURN',
  desc: 'Hold to turn left. Eat stars to grow longer. If your head hits a body or a wall, you are out. Corner your rivals!',
  color: '#84CC16', grad: ['#BEF264', '#4D7C0F'], gradDark: '#365314',
  icon: `<svg viewBox="0 0 64 64"><path d="M10 50c10 0 10-14 20-14s10 14 20 14" stroke="#fff" stroke-width="8" fill="none" stroke-linecap="round"/><circle cx="50" cy="50" r="7" fill="#fff"/><path d="M40 12l3 6 6 1-4.5 4 1 6-5.5-3-5.5 3 1-6-4.5-4 6-1z" fill="#FFE27A"/></svg>`,

  create(ctx) {
    const { players, S, arena: A } = ctx;
    const m = 8;
    const F = { x: A.x + m, y: A.y + m, w: A.w - m * 2, h: A.h - m * 2 };
    const W = S * 0.034, SEG = 4, TURN = 3.2, LIMIT = 45;
    const Sn = players.map((p) => ({
      p, x: p.lay.home.x, y: p.lay.home.y, ang: p.lay.dir + Math.PI / 2 + 0.25, pts: [], len: S * 0.3, dead: false,
      think: 0, hold: false, grow: 0,
    }));
    for (const s of Sn) s.pts.push({ x: s.x, y: s.y });
    const stars = [];
    const addStar = (x, y) => stars.push({ x: x ?? rand(F.x + 20, F.x + F.w - 20), y: y ?? rand(F.y + 20, F.y + F.h - 20), t: rand(0, 6), born: 0 });
    for (let i = 0; i < 4; i++) addStar();

    const speed = () => S * (0.26 + Math.min(0.08, ctx.t * 0.002));
    function hits(s, x, y, ignoreOwn) {
      if (x < F.x + W / 2 || x > F.x + F.w - W / 2 || y < F.y + W / 2 || y > F.y + F.h - W / 2) return true;
      for (const o of Sn) {
        if (o.dead) continue;
        const pts = o.pts, end = o === s ? pts.length - ignoreOwn : pts.length;
        for (let i = 0; i < end; i++) if (Math.abs(pts[i].x - x) < W && Math.abs(pts[i].y - y) < W && dist(pts[i].x, pts[i].y, x, y) < W * 0.9) return true;
      }
      return false;
    }
    const ownSkip = Math.ceil((W * 2.2) / SEG) + 2;
    function freeRun(s, turning) {
      let x = s.x, y = s.y, a = s.ang;
      const v = speed(), step = W * 0.9, nSteps = Math.ceil((v * 0.7) / step);
      for (let k = 1; k <= nSteps; k++) {
        if (turning) a -= TURN * (step / v);
        x += Math.cos(a) * step; y += Math.sin(a) * step;
        if (hits(s, x, y, ownSkip + k * 2)) return k / nSteps;
        const tk = (k * step) / v;
        for (const o of Sn) {
          if (o === s || o.dead) continue;
          const ox = o.x + Math.cos(o.ang) * v * tk, oy = o.y + Math.sin(o.ang) * v * tk;
          if (dist(x, y, ox, oy) < W * 2.2) return k / nSteps;
        }
      }
      return 1;
    }
    function ai(s, dt) {
      s.think -= dt;
      if (s.think > 0) return;
      s.think = rand(0.04, 0.08);
      const fs = freeRun(s, false), ft = freeRun(s, true);
      if (fs < 1 || ft < 1) { s.hold = ft > fs; return; }
      let best = null, bd = 1e9;
      for (const st of stars) { const d = dist(s.x, s.y, st.x, st.y); if (d < bd) { bd = d; best = st; } }
      if (!best) { s.hold = false; return; }
      const diff = angNorm(Math.atan2(best.y - s.y, best.x - s.x) - s.ang);
      s.hold = diff < -0.15 || diff > 2.2;
    }

    return {
      where(p) { const s = Sn.find((x) => x.p === p); return { x: s.x, y: s.y, r: W }; },
      update(dt) {
        const v = speed();
        const dead = [];
        for (const s of Sn) {
          if (s.dead) continue;
          if (!s.p.human && !ctx.over) { ai(s, dt); ctx.botHold(s.p, s.hold); }
          if (s.p.down) s.ang -= TURN * dt;
          s.x += Math.cos(s.ang) * v * dt; s.y += Math.sin(s.ang) * v * dt;
          const last = s.pts[s.pts.length - 1];
          if (dist(last.x, last.y, s.x, s.y) >= SEG) s.pts.push({ x: s.x, y: s.y });
          if (s.grow > 0) { const g = Math.min(s.grow, S * 0.25 * dt); s.len += g; s.grow -= g; }
          while (s.pts.length * SEG > s.len && s.pts.length > 2) s.pts.shift();
          for (let i = stars.length - 1; i >= 0; i--) {
            const st = stars[i];
            if (dist(s.x, s.y, st.x, st.y) < W * 1.4) {
              stars.splice(i, 1); s.grow += S * 0.09;
              Sfx.coin(); FX.burst(st.x, st.y, '#FFE27A', 10, 180, 3, 0.5);
              if (stars.length < 3) addStar();
            }
          }
        }
        for (const s of Sn) {
          if (s.dead) continue;
          if (hits(s, s.x, s.y, ownSkip)) dead.push(s);
        }
        for (const s of dead) {
          s.dead = true;
          const col = COLORS[s.p.slot];
          FX.burst(s.x, s.y, col.main, 26, 300, 5, 0.8); FX.addShake(6); Sfx.hit();
          for (let k = 0; k < s.pts.length; k += Math.max(6, Math.floor(s.pts.length / 5))) {
            FX.burst(s.pts[k].x, s.pts[k].y, col.light, 4, 120, 3, 0.5);
            if (stars.length < 9) addStar(s.pts[k].x, s.pts[k].y);
          }
          s.pts = [];
        }
        if (dead.length) ctx.eliminate(dead.map((s) => s.p));
        if (ctx.t > LIMIT && !ctx.over) {
          const alive = Sn.filter((s) => !s.dead).sort((a, b) => b.len - a.len);
          ctx.end([...alive.map((s) => s.p), ...Sn.filter((s) => s.dead).map((s) => s.p)]);
        }
        for (const st of stars) { st.t += dt; st.born += dt; }
        if (stars.length < 3) addStar();
      },

      draw(g) {
        g.strokeStyle = 'rgba(132,204,22,.4)'; g.lineWidth = 3;
        rrect(g, F.x, F.y, F.w, F.h, 12); g.stroke();
        for (const st of stars) {
          const s = Math.min(1, st.born * 4) * (1 + Math.sin(st.t * 5) * 0.1);
          g.save(); g.translate(st.x, st.y); g.rotate(st.t); g.scale(s, s);
          g.shadowColor = '#FFE27A'; g.shadowBlur = 14; g.fillStyle = '#FFD23F';
          g.beginPath();
          for (let k = 0; k < 10; k++) { const r = k % 2 ? W * 0.45 : W * 1.05, a = (k / 10) * TAU - Math.PI / 2; g.lineTo(Math.cos(a) * r, Math.sin(a) * r); }
          g.closePath(); g.fill(); g.shadowBlur = 0;
          g.restore();
        }
        for (const s of Sn) {
          if (s.dead) continue;
          const col = COLORS[s.p.slot];
          if (s.pts.length < 2) { drawBlob(g, s.x, s.y, W * 0.8, col, { ang: s.ang }); continue; }
          g.lineCap = 'round'; g.lineJoin = 'round';
          g.beginPath(); g.moveTo(s.pts[0].x, s.pts[0].y);
          for (const q of s.pts) g.lineTo(q.x, q.y);
          g.lineTo(s.x, s.y);
          g.strokeStyle = col.dark; g.lineWidth = W * 1.25; g.stroke();
          g.strokeStyle = col.main; g.lineWidth = W * 0.9; g.stroke();
          g.strokeStyle = 'rgba(255,255,255,.25)'; g.lineWidth = W * 0.25; g.stroke();
          drawBlob(g, s.x, s.y, W * 0.8, col, { ang: s.ang });
        }
        for (const s of Sn) if (!s.dead) ctx.hudText(s.p, `${Math.round(s.len / (S * 0.09))}`, null, -16, 17);
      },
    };
  },
});
