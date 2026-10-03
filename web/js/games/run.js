'use strict';
/* Hurdle Dash — auto-run down your lane and tap to jump the hurdles. Tripping costs time. */
Games.push({
  id: 'run', name: 'Hurdle Dash', control: 'TAP TO JUMP',
  desc: 'You run on your own. Tap to jump over hurdles. Hit one and you trip! Watch for double hurdles. First to the finish wins.',
  color: '#F97316', grad: ['#FDBA74', '#EA580C'], gradDark: '#9A3412',
  icon: `<svg viewBox="0 0 64 64"><circle cx="38" cy="12" r="6" fill="#fff"/><path d="M36 20l-8 14 10 6-4 14M28 34l-10 4M36 22l10 8 8-2" stroke="#fff" stroke-width="5" fill="none" stroke-linecap="round" stroke-linejoin="round"/><path d="M6 56h20M10 46v10M22 46v10M8 46h16" stroke="#fff" stroke-width="3.5" stroke-linecap="round" opacity=".75"/></svg>`,

  create(ctx) {
    const { players, arena: A } = ctx;
    const n = players.length;
    const laneW = A.w / n, pad = 18, L = A.h - pad * 2, OFF = 0.2;
    const D = 5.2, VR = 0.5, AIR = 0.42;
    const hurdles = [];
    for (let x = 0.7; x < D - 0.3;) {
      hurdles.push(x);
      if (Math.random() < 0.22) { hurdles.push(x + 0.16); x += 0.16; }
      x += rand(0.34, 0.6);
    }
    const order = [...players].sort((p, q) => (p.lay.btn.x - q.lay.btn.x) || (p.lay.side === 'bottom' ? -1 : 1));
    const R = players.map((p) => ({
      p, lane: order.indexOf(p), d: 0, v: 0, air: 0, stun: 0, knocked: new Set(), step: 0, done: false,
      lead: rand(0.12, 0.24),
    }));
    const finished = [];
    const plan = (r) => { const m = Math.random(); r.lead = m < 0.05 ? 0.42 : m < 0.08 ? 0.03 : rand(0.11, 0.25); };
    R.forEach(plan);

    const toY = (r, pos) => {
      const up = r.p.lay.side === 'bottom';
      const k = pos - r.d + OFF;
      return up ? A.y + A.h - pad - k * L : A.y + pad + k * L;
    };
    const zOf = (r) => (r.air > 0 ? Math.sin(Math.PI * (1 - r.air / AIR)) : 0);

    return {
      where(p) { const r = R.find((x) => x.p === p); return { x: A.x + laneW * (r.lane + 0.5), y: toY(r, r.d), r: 20 }; },
      update(dt) {
        for (const r of R) {
          const p = r.p;
          if (r.done) continue;
          const next = hurdles.find((h) => h > r.d + 0.005 && !r.knocked.has(h));
          if (!p.human && !ctx.over && r.air <= 0 && r.stun <= 0 && next != null && r.v > 0.05) {
            if ((next - r.d) / r.v < r.lead) { ctx.botTap(p); plan(r); }
          }
          if (r.stun > 0) { r.stun -= dt; continue; }
          if (p.pressed && r.air <= 0) { r.air = AIR; Sfx.jump(); }
          if (r.air > 0) r.air = Math.max(0, r.air - dt);
          r.v = Math.min(VR, r.v + 1.3 * dt);
          const prev = r.d;
          r.d += r.v * dt;
          r.step += r.v * dt * 30;
          for (const h of hurdles) {
            if (h > prev && h <= r.d && !r.knocked.has(h)) {
              if (zOf(r) < 0.3) {
                r.knocked.add(h); r.stun = 0.65; r.v = 0; r.air = 0;
                const x = A.x + laneW * (r.lane + 0.5), y = toY(r, h);
                Sfx.hit(); ctx.buzz(50); FX.burst(x, y, '#fff', 12, 200, 3, 0.5);
                FX.float(x, toY(r, r.d), 'OOPS!', '#FF8FA3', p.lay.rot, 18);
              } else if (p.human) Sfx.tone(700, 0.05, { type: 'triangle', vol: 0.06 });
            }
          }
          if (r.d >= D) {
            r.d = D; r.done = true; finished.push(p);
            const x = A.x + laneW * (r.lane + 0.5);
            FX.burst(x, toY(r, D), COLORS[p.slot].main, 30, 300, 6, 0.9);
            if (finished.length === 1) ctx.end([p, ...R.filter((x) => !x.done).sort((a, b) => b.d - a.d).map((x) => x.p)]);
          }
        }
      },

      draw(g) {
        for (const r of R) {
          const col = COLORS[r.p.slot];
          const x0 = A.x + laneW * r.lane, xc = x0 + laneW / 2;
          const tw = Math.min(laneW - 14, 110);
          g.fillStyle = '#8A3B2C'; g.fillRect(xc - tw / 2, A.y, tw, A.h);
          g.fillStyle = 'rgba(0,0,0,.12)';
          for (let k = Math.floor(r.d * 8) / 8 - 0.5; k < r.d + 1.2; k += 0.125) {
            const y = toY(r, k); g.fillRect(xc - tw / 2, y, tw, 2);
          }
          g.fillStyle = 'rgba(255,255,255,.75)';
          g.fillRect(xc - tw / 2, A.y, 3, A.h); g.fillRect(xc + tw / 2 - 3, A.y, 3, A.h);
          // finish line
          const fy = toY(r, D);
          if (fy > A.y - 10 && fy < A.y + A.h + 10) {
            const cs = 7;
            for (let k = 0; k * cs < tw; k++) for (let j = 0; j < 2; j++) { g.fillStyle = (k + j) % 2 ? '#fff' : '#111'; g.fillRect(xc - tw / 2 + k * cs, fy - cs + j * cs, Math.min(cs, tw - k * cs), cs); }
          }
          // hurdles
          for (const h of hurdles) {
            const k = h - r.d + OFF;
            if (k < -0.1 || k > 1.1) continue;
            const y = toY(r, h), down = r.knocked.has(h);
            const hw = tw * 0.72;
            g.fillStyle = 'rgba(0,0,0,.3)'; g.fillRect(xc - hw / 2, y + 3, hw, 6);
            if (down) { g.globalAlpha = 0.45; g.save(); g.translate(xc, y); g.rotate(0.25); g.fillStyle = '#ccc'; g.fillRect(-hw / 2, -3, hw, 6); g.restore(); g.globalAlpha = 1; continue; }
            g.fillStyle = '#fff'; g.fillRect(xc - hw / 2, y - 4, hw, 8);
            g.fillStyle = '#E63946';
            for (let s = 0; s < 4; s++) g.fillRect(xc - hw / 2 + s * (hw / 4) + hw / 16, y - 4, hw / 8, 8);
            g.fillStyle = '#ddd'; g.fillRect(xc - hw / 2, y - 2, 4, 10); g.fillRect(xc + hw / 2 - 4, y - 2, 4, 10);
          }
          // runner
          const y = toY(r, r.d), z = zOf(r), up = r.p.lay.side === 'bottom';
          const rr = Math.min(laneW * 0.18, 20);
          g.fillStyle = `rgba(0,0,0,${0.35 - z * 0.2})`; g.beginPath(); g.ellipse(xc, y + 4, rr * (1 - z * 0.3), rr * 0.5, 0, 0, TAU); g.fill();
          const fwd = up ? -1 : 1;
          if (z < 0.1 && r.stun <= 0) {
            const sw = Math.sin(r.step) * rr * 0.6;
            g.fillStyle = col.dark;
            g.beginPath(); g.ellipse(xc - rr * 0.45, y + fwd * sw, rr * 0.28, rr * 0.38, 0, 0, TAU); g.fill();
            g.beginPath(); g.ellipse(xc + rr * 0.45, y - fwd * sw, rr * 0.28, rr * 0.38, 0, 0, TAU); g.fill();
          }
          g.save(); g.translate(xc, y - z * rr * 1.3 * (up ? 1 : -1)); const s = 1 + z * 0.4; g.scale(s, s);
          if (r.stun > 0) g.rotate(Math.sin(r.stun * 30) * 0.3);
          drawBlob(g, 0, 0, rr, col, { ang: up ? -Math.PI / 2 : Math.PI / 2, dead: r.stun > 0 });
          g.restore();
          // progress bar along the lane edge
          const bx = xc + tw / 2 + 4, prog = r.d / D;
          g.fillStyle = 'rgba(255,255,255,.1)'; g.fillRect(bx, A.y + pad, 4, L);
          g.fillStyle = col.main;
          if (up) g.fillRect(bx, A.y + pad + L * (1 - prog), 4, L * prog); else g.fillRect(bx, A.y + pad, 4, L * prog);
        }
      },
    };
  },
});
