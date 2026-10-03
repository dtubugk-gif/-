'use strict';
/* Laser Jump — a spinning laser sweeps the floor. Jump over it or get knocked out. */
Games.push({
  id: 'jump', name: 'Laser Jump', control: 'TAP TO JUMP',
  desc: 'A laser spins around the arena, faster and faster. Tap to jump over it. Watch out, it can reverse!',
  color: '#3A86FF', grad: ['#60A5FA', '#4F46E5'], gradDark: '#312E81',
  icon: `<svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="6" fill="#fff"/><rect x="32" y="29" width="28" height="6" rx="3" fill="#fff" transform="rotate(25 32 32)"/><circle cx="20" cy="18" r="8" fill="#fff" opacity=".8"/><ellipse cx="20" cy="34" rx="7" ry="2.5" fill="#fff" opacity=".35"/><path d="M14 52a22 22 0 0 0 34-4" stroke="#fff" stroke-width="3" fill="none" stroke-linecap="round" stroke-dasharray="2 6"/></svg>`,

  create(ctx) {
    const { players, S, cx, cy } = ctx;
    const RC = S * 0.33, LB = S * 0.45, HUB = S * 0.06, PR = S * 0.065, AIR = 0.62;
    const C = players.map((p) => ({
      p, ang: p.lay.dir, air: 0, gone: 0, fx: 0, fy: 0, vx: 0, vy: 0, spin: 0,
      jumpAt: rand(0.12, 0.28),
    }));
    let theta = (() => {
      let best = 0, bd = -1;
      for (let k = 0; k < 24; k++) {
        const a = (k / 24) * TAU;
        const d = Math.min(...C.map((c) => Math.abs(angNorm(c.ang - a))));
        if (d > bd) { bd = d; best = a; }
      }
      return best;
    })();
    let sign = Math.random() < 0.5 ? 1 : -1;
    let speed = 1.6, nextRev = rand(4, 7), warn = 0;

    function botPlan(c) {
      const r = Math.random();
      c.jumpAt = r < 0.05 ? 0.005 : r < 0.09 ? 0.8 : rand(0.1, 0.3);
    }
    C.forEach(botPlan);

    return {
      update(dt) {
        const t = ctx.t;
        speed = Math.min(5.2, 1.6 + t * 0.075);
        if (t >= nextRev - 0.5 && t < nextRev) warn = 1;
        if (t >= nextRev) { sign = -sign; nextRev = t + rand(3.5, 7); warn = 0; Sfx.tone(220, 0.25, { type: 'sawtooth', vol: 0.08, slide: 2 }); }
        const prevTheta = theta;
        theta += sign * speed * dt;
        const hits = [];
        for (const c of C) {
          const p = c.p;
          if (p.out) {
            if (c.gone > 0) { c.gone += dt; c.fx += c.vx * dt; c.fy += c.vy * dt; c.spin += dt * 12; }
            continue;
          }
          if (!p.human && !ctx.over && c.air <= 0) {
            const d = ((((c.ang - theta) * sign) % TAU) + TAU) % TAU;
            if (d / speed < c.jumpAt) ctx.botTap(p);
          }
          if (p.pressed && c.air <= 0) { c.air = AIR; Sfx.jump(); }
          if (c.air > 0) c.air = Math.max(0, c.air - dt);
          const r0 = angNorm(c.ang - prevTheta), r1 = angNorm(c.ang - theta);
          const crossed = Math.abs(r0) < 1 && Math.abs(r1) < 1 && (r0 === 0 || Math.sign(r0) !== Math.sign(r1));
          if (crossed) {
            const z = c.air > 0 ? Math.sin(Math.PI * (1 - c.air / AIR)) : 0;
            if (z < 0.3) {
              const x = cx + Math.cos(c.ang) * RC, y = cy + Math.sin(c.ang) * RC;
              c.gone = 0.001; c.fx = x; c.fy = y;
              const tx = -Math.sin(c.ang) * sign, ty = Math.cos(c.ang) * sign;
              c.vx = (tx * 1.2 + Math.cos(c.ang) * 0.6) * S * 1.4; c.vy = (ty * 1.2 + Math.sin(c.ang) * 0.6) * S * 1.4;
              FX.burst(x, y, COLORS[p.slot].main, 24, 340, 6, 0.8); FX.burst(x, y, '#fff', 10, 240, 3, 0.5);
              FX.addShake(9); Sfx.hit();
              hits.push(p);
            } else { Sfx.coin(); FX.ring(cx + Math.cos(c.ang) * RC, cy + Math.sin(c.ang) * RC, '#fff', PR * 2, 0.35, 4); }
            botPlan(c);
          }
        }
        if (hits.length) ctx.eliminate(hits);
      },

      draw(g) {
        // floor
        const fg = g.createRadialGradient(cx, cy, HUB, cx, cy, LB * 1.08);
        fg.addColorStop(0, '#22285C'); fg.addColorStop(1, '#161A3D');
        g.fillStyle = fg; g.beginPath(); g.arc(cx, cy, LB * 1.08, 0, TAU); g.fill();
        g.strokeStyle = 'rgba(255,255,255,.08)'; g.lineWidth = 2; g.setLineDash([3, 9]);
        g.beginPath(); g.arc(cx, cy, RC, 0, TAU); g.stroke(); g.setLineDash([]);
        g.strokeStyle = 'rgba(96,165,250,.35)'; g.lineWidth = 3;
        g.beginPath(); g.arc(cx, cy, LB * 1.08, 0, TAU); g.stroke();
        // sweep trail
        for (let k = 0; k < 10; k++) {
          const a0 = theta - sign * (k + 1) * 0.06, a1 = theta - sign * k * 0.06;
          g.fillStyle = `rgba(255,77,140,${0.16 * (1 - k / 10)})`;
          g.beginPath(); g.moveTo(cx, cy);
          g.arc(cx, cy, LB, Math.min(a0, a1), Math.max(a0, a1)); g.closePath(); g.fill();
        }
        // ground shadows + characters (behind laser if grounded, above if airborne)
        const drawChar = (c) => {
          const p = c.p, col = COLORS[p.slot];
          if (p.out) {
            if (c.gone > 0 && c.gone < 0.9) {
              g.globalAlpha = 1 - c.gone / 0.9;
              g.save(); g.translate(c.fx, c.fy); g.rotate(c.spin); g.translate(-c.fx, -c.fy);
              drawBlob(g, c.fx, c.fy, PR, col, { dead: true });
              g.restore(); g.globalAlpha = 1;
            }
            return;
          }
          const x = cx + Math.cos(c.ang) * RC, y = cy + Math.sin(c.ang) * RC;
          const z = c.air > 0 ? Math.sin(Math.PI * (1 - c.air / AIR)) : 0;
          g.fillStyle = `rgba(0,0,0,${0.35 - z * 0.2})`;
          g.beginPath(); g.ellipse(x, y + PR * 0.5, PR * (1 - z * 0.3), PR * 0.45 * (1 - z * 0.3), 0, 0, TAU); g.fill();
          const s = 1 + z * 0.4;
          g.save(); g.translate(x, y - z * PR * 1.2); g.scale(s, s);
          const look = Math.atan2(cy + Math.sin(theta) * RC - y, cx + Math.cos(theta) * RC - x);
          drawBlob(g, 0, 0, PR, col, { ang: look, squash: z > 0 ? -0.06 : 0 });
          g.restore();
        };
        const airborne = C.filter((c) => c.air > 0 && !c.p.out);
        C.filter((c) => !airborne.includes(c)).forEach(drawChar);
        // laser
        const ex = cx + Math.cos(theta) * LB, ey = cy + Math.sin(theta) * LB;
        g.lineCap = 'round';
        g.shadowColor = '#FF4D8C'; g.shadowBlur = 24;
        g.strokeStyle = '#FF4D8C'; g.lineWidth = S * 0.035;
        g.beginPath(); g.moveTo(cx, cy); g.lineTo(ex, ey); g.stroke();
        g.shadowBlur = 0;
        g.strokeStyle = '#FFE4EF'; g.lineWidth = S * 0.012;
        g.beginPath(); g.moveTo(cx, cy); g.lineTo(ex, ey); g.stroke();
        // hub
        const wf = warn && Math.sin(ctx.t * 40) > 0;
        g.fillStyle = wf ? '#FFE27A' : '#2B3270';
        g.beginPath(); g.arc(cx, cy, HUB, 0, TAU); g.fill();
        g.strokeStyle = wf ? '#fff' : '#FF4D8C'; g.lineWidth = 4;
        g.beginPath(); g.arc(cx, cy, HUB, 0, TAU); g.stroke();
        g.fillStyle = '#fff';
        g.save(); g.translate(cx, cy); g.rotate(theta + sign * Math.PI / 2);
        g.beginPath(); g.moveTo(HUB * 0.5, 0); g.lineTo(-HUB * 0.25, -HUB * 0.35); g.lineTo(-HUB * 0.25, HUB * 0.35); g.fill();
        g.restore();
        airborne.forEach(drawChar);
      },
    };
  },
});
