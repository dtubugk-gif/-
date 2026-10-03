'use strict';
/* Sumo Bump — spin, aim and dash to knock everyone off the shrinking ring. */
Games.push({
  id: 'sumo', name: 'Sumo Bump', control: 'HOLD',
  desc: 'You spin in place. Hold to dash where you face. Push everyone off the ring!',
  color: '#A66CFF', grad: ['#A66CFF', '#6D28D9'], gradDark: '#4C1D95',
  icon: `<svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="27" fill="none" stroke="#fff" stroke-width="4" opacity=".6"/><circle cx="24" cy="34" r="10" fill="#fff"/><circle cx="42" cy="28" r="8" fill="#fff" opacity=".75"/><path d="M8 40l6-2M8 30l7 1" stroke="#fff" stroke-width="3" stroke-linecap="round"/></svg>`,

  create(ctx) {
    const { players, S, cx, cy } = ctx;
    const R0 = S * 0.44;
    let platR = R0;
    const br = R0 * 0.105;
    const B = players.map((p) => ({
      p, x: cx + Math.cos(p.lay.dir) * R0 * 0.6, y: cy + Math.sin(p.lay.dir) * R0 * 0.6,
      vx: 0, vy: 0, ang: p.lay.dir + Math.PI + rand(-0.4, 0.4), fall: 0, squash: 0,
      aim: rand(0.22, 0.4), think: 0, wantHold: false,
    }));
    const ACC = R0 * 2.5, MAXV = R0 * 1.35, SPIN = 3.4;
    let warn = 0;

    function ai(b, dt) {
      b.think -= dt;
      if (b.think > 0) return;
      b.think = rand(0.04, 0.1);
      const d = dist(b.x, b.y, cx, cy);
      let tx = cx, ty = cy;
      if (d < platR * 0.6) {
        let best = null, bd = 1e9;
        for (const o of B) {
          if (o === b || o.fall || o.p.out) continue;
          const dd = dist(b.x, b.y, o.x, o.y);
          if (dd < bd) { bd = dd; best = o; }
        }
        if (best) { tx = best.x + best.vx * 0.15; ty = best.y + best.vy * 0.15; }
      }
      const want = Math.atan2(ty - b.y, tx - b.x);
      b.wantHold = Math.abs(angNorm(want - b.ang)) < b.aim;
    }

    return {
      where(p) { const b = B.find((x) => x.p === p); return { x: b.x, y: b.y, r: br }; },
      update(dt) {
        const t = ctx.t;
        const prev = platR;
        platR = R0 * lerp(1, 0.3, clamp((t - 6) / 45, 0, 1));
        warn = platR < prev ? warn + dt : 0;
        const falling = [];
        for (const b of B) {
          const p = b.p;
          if (p.out) continue;
          if (b.fall > 0) {
            b.fall += dt;
            b.x += b.vx * dt * 0.5; b.y += b.vy * dt * 0.5;
            if (b.fall > 0.55) falling.push(p);
            continue;
          }
          if (!p.human && !ctx.over) { ai(b, dt); ctx.botHold(p, b.wantHold); }
          if (p.pressed) { b.vx += Math.cos(b.ang) * R0 * 0.35; b.vy += Math.sin(b.ang) * R0 * 0.35; Sfx.tone(260, 0.08, { type: 'triangle', vol: 0.08, slide: 1.6 }); }
          if (p.down) {
            b.vx += Math.cos(b.ang) * ACC * dt; b.vy += Math.sin(b.ang) * ACC * dt;
            if (Math.random() < 0.5) FX.trail(b.x - Math.cos(b.ang) * br, b.y - Math.sin(b.ang) * br, COLORS[p.slot].light, rand(2, 4), 0.35);
          } else b.ang += SPIN * dt;
          const f = Math.exp(-1.1 * dt); b.vx *= f; b.vy *= f;
          const sp = Math.hypot(b.vx, b.vy);
          if (sp > MAXV) { b.vx *= MAXV / sp; b.vy *= MAXV / sp; }
          b.x += b.vx * dt; b.y += b.vy * dt;
          b.squash = Math.max(0, b.squash - dt * 2);
        }
        // collisions
        for (let i = 0; i < B.length; i++) for (let j = i + 1; j < B.length; j++) {
          const a = B[i], c = B[j];
          if (a.fall || c.fall || a.p.out || c.p.out) continue;
          const dx = c.x - a.x, dy = c.y - a.y, d = Math.hypot(dx, dy);
          if (d < br * 2 && d > 0) {
            const nx = dx / d, ny = dy / d, ov = br * 2 - d;
            a.x -= nx * ov / 2; a.y -= ny * ov / 2; c.x += nx * ov / 2; c.y += ny * ov / 2;
            const vrel = (a.vx - c.vx) * nx + (a.vy - c.vy) * ny;
            if (vrel > 0) {
              const jm = vrel * 1.1;
              a.vx -= jm * nx; a.vy -= jm * ny; c.vx += jm * nx; c.vy += jm * ny;
              if (vrel > R0 * 0.25) {
                Sfx.bounce(); FX.burst(a.x + nx * br, a.y + ny * br, '#fff', 8, 200, 3, 0.4);
                FX.addShake(Math.min(8, vrel / R0 * 6)); a.squash = c.squash = 0.18;
              }
            }
          }
        }
        // ring out
        for (const b of B) {
          if (b.p.out || b.fall) continue;
          if (dist(b.x, b.y, cx, cy) > platR + br * 0.2) {
            b.fall = 0.001; Sfx.whoosh(); ctx.buzz(50);
          }
        }
        if (falling.length) ctx.eliminate(falling);
      },

      draw(g) {
        // platform
        g.save();
        g.shadowColor = warn > 0 ? 'rgba(255,90,120,.7)' : 'rgba(123,140,255,.6)'; g.shadowBlur = 40;
        g.fillStyle = '#20265A'; g.beginPath(); g.arc(cx, cy, platR, 0, TAU); g.fill();
        g.restore();
        const grd = g.createRadialGradient(cx, cy - platR * 0.3, platR * 0.1, cx, cy, platR);
        grd.addColorStop(0, '#343C86'); grd.addColorStop(1, '#1E2352');
        g.fillStyle = grd; g.beginPath(); g.arc(cx, cy, platR, 0, TAU); g.fill();
        g.strokeStyle = 'rgba(255,255,255,.07)'; g.lineWidth = 2;
        for (const k of [0.33, 0.66]) { g.beginPath(); g.arc(cx, cy, platR * k, 0, TAU); g.stroke(); }
        const pulse = warn > 0 ? 0.6 + Math.sin(ctx.t * 12) * 0.4 : 1;
        g.strokeStyle = warn > 0 ? `rgba(255,107,139,${pulse})` : '#7B8CFF'; g.lineWidth = 6;
        g.beginPath(); g.arc(cx, cy, platR - 3, 0, TAU); g.stroke();
        g.strokeStyle = 'rgba(255,255,255,.1)'; g.lineWidth = 2; g.setLineDash([6, 10]);
        g.beginPath(); g.arc(cx, cy, R0, 0, TAU); g.stroke(); g.setLineDash([]);

        for (const b of B) {
          const p = b.p, col = COLORS[p.slot];
          if (p.out) continue;
          const s = b.fall ? Math.max(0, 1 - b.fall / 0.55) : 1;
          g.save();
          g.globalAlpha = s;
          g.translate(b.x, b.y); g.scale(s, s); g.translate(-b.x, -b.y);
          // aim arrow
          if (!b.fall) {
            const ax = b.x + Math.cos(b.ang) * br * 1.6, ay = b.y + Math.sin(b.ang) * br * 1.6;
            g.fillStyle = p.down ? '#fff' : col.light;
            g.save(); g.translate(ax, ay); g.rotate(b.ang);
            g.beginPath(); g.moveTo(br * 0.45, 0); g.lineTo(-br * 0.2, -br * 0.38); g.lineTo(-br * 0.2, br * 0.38); g.closePath(); g.fill();
            g.restore();
          }
          drawBlob(g, b.x, b.y, br, col, { ang: b.ang, squash: b.squash, dead: b.fall > 0 });
          g.restore();
        }
      },
    };
  },
});
