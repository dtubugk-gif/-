'use strict';
/* Rocket Race — mash your button to blast your rocket to the finish line. */
Games.push({
  id: 'rocket', name: 'Rocket Race', control: 'TAP FAST',
  desc: 'Smash your button as fast as you can. First rocket to the finish line wins!',
  color: '#FF6B35', grad: ['#FF8C42', '#FF3C38'], gradDark: '#B3261E',
  icon: `<svg viewBox="0 0 64 64"><path d="M32 4c10 8 14 20 12 34H20C18 24 22 12 32 4z" fill="#fff"/><circle cx="32" cy="24" r="6" fill="#FF3C38"/><path d="M20 30l-8 12 9 1zM44 30l8 12-9 1z" fill="#FFE1D6"/><path d="M24 40h16l-3 8h-10z" fill="#FFE1D6"/><path d="M27 50q5 12 5 12t5-12z" fill="#FFD23F"/></svg>`,

  create(ctx) {
    const { arena: a, players } = ctx;
    const n = players.length;
    const laneW = a.w / n;
    // order lanes left→right by the player's button position
    const order = [...players].sort((p, q) => (p.lay.btn.x - q.lay.btn.x) || (p.lay.side === 'bottom' ? -1 : 1));
    const pad = Math.min(46, a.h * 0.08);
    const stars = Array.from({ length: 70 }, () => ({ x: rand(a.x, a.x + a.w), y: rand(a.y, a.y + a.h), s: rand(0.6, 2), v: rand(0.3, 1) }));
    const R = players.map((p) => ({
      p, lane: order.indexOf(p), prog: 0, vel: 0, done: false, wob: rand(0, TAU),
      botRate: rand(6.4, 8.6), botNext: rand(0.1, 0.3),
    }));
    const finished = [];
    const pos = (r) => {
      const x = a.x + laneW * (r.lane + 0.5);
      const up = r.p.lay.side === 'bottom';
      const y0 = up ? a.y + a.h - pad : a.y + pad;
      const y1 = up ? a.y + pad : a.y + a.h - pad;
      return { x, y: lerp(y0, y1, r.prog), up };
    };

    return {
      update(dt) {
        for (const r of R) {
          const p = r.p;
          if (!p.human && !ctx.over) {
            r.botNext -= dt;
            if (r.botNext <= 0) { ctx.botTap(p); r.botNext = (1 / r.botRate) * rand(0.6, 1.4); }
          }
          if (p.taps && !r.done) {
            r.vel += 0.052 * p.taps;
            if (p.human) Sfx.tap(p.slot);
            const q = pos(r);
            FX.burst(q.x, q.y + (q.up ? 26 : -26), pick(['#FFD23F', '#FF8C42', '#fff']), 3, 120, 4, 0.4);
          }
          r.vel *= Math.exp(-3 * dt);
          if (!r.done) {
            r.prog += r.vel * dt;
            const q = pos(r);
            if (r.vel > 0.05) FX.trail(q.x + rand(-4, 4), q.y + (q.up ? 24 : -24), pick(['#FFD23F', '#FF8C42']), rand(3, 6), 0.45, 0, q.up ? 140 : -140);
            if (r.prog >= 1) {
              r.prog = 1; r.done = true; finished.push(p);
              FX.burst(q.x, q.y, COLORS[p.slot].main, 30, 320, 6, 0.9);
              FX.ring(q.x, q.y, '#fff', 80, 0.5, 8);
              if (finished.length === 1) {
                const rest = R.filter((x) => !x.done).sort((x, y) => y.prog - x.prog).map((x) => x.p);
                ctx.end([p, ...rest]);
              }
            }
          }
          r.wob += dt * (4 + r.vel * 40);
        }
        for (const s of stars) {
          s.y += s.v * 30 * dt; if (s.y > a.y + a.h) s.y = a.y;
        }
      },

      draw(g) {
        for (const s of stars) { g.fillStyle = `rgba(255,255,255,${0.25 * s.v})`; g.fillRect(s.x, s.y, s.s, s.s); }
        // lanes
        for (let i = 0; i < n; i++) {
          const x = a.x + laneW * i;
          if (i > 0) { g.fillStyle = 'rgba(255,255,255,.06)'; g.fillRect(x - 1, a.y, 2, a.h); }
        }
        for (const r of R) {
          const p = r.p, col = COLORS[p.slot];
          const x = a.x + laneW * (r.lane + 0.5);
          const up = p.lay.side === 'bottom';
          // track fill
          const y0 = up ? a.y + a.h - pad : a.y + pad;
          const q = pos(r);
          g.strokeStyle = 'rgba(255,255,255,.07)'; g.lineWidth = 6; g.lineCap = 'round';
          g.beginPath(); g.moveTo(x, a.y + pad); g.lineTo(x, a.y + a.h - pad); g.stroke();
          g.strokeStyle = col.main; g.globalAlpha = 0.5;
          g.beginPath(); g.moveTo(x, y0); g.lineTo(x, q.y); g.stroke();
          g.globalAlpha = 1;
          // finish line (checkered)
          const fy = up ? a.y + pad * 0.45 : a.y + a.h - pad * 0.45;
          const cw = Math.min(laneW * 0.7, 90), cs = 6;
          for (let k = 0; k < cw / cs; k++) for (let j = 0; j < 2; j++) {
            g.fillStyle = (k + j) % 2 ? '#fff' : '#1b1b2f';
            g.fillRect(x - cw / 2 + k * cs, fy - cs + j * cs, cs, cs);
          }
          // rocket
          const sc = Math.min(laneW * 0.32, 34) / 24;
          g.save();
          g.translate(q.x + Math.sin(r.wob) * Math.min(2, r.vel * 14), q.y);
          g.rotate(up ? 0 : Math.PI);
          g.scale(sc, sc);
          // flame
          const fl = 10 + r.vel * 120 + Math.sin(r.wob * 3) * 3;
          const fg = g.createLinearGradient(0, 18, 0, 18 + fl);
          fg.addColorStop(0, '#FFF3B0'); fg.addColorStop(0.4, '#FFB703'); fg.addColorStop(1, 'rgba(255,80,40,0)');
          g.fillStyle = fg;
          g.beginPath(); g.moveTo(-8, 16); g.quadraticCurveTo(0, 18 + fl * 1.2, 8, 16); g.fill();
          // fins
          g.fillStyle = col.dark;
          g.beginPath(); g.moveTo(-10, 4); g.lineTo(-20, 20); g.lineTo(-8, 18); g.fill();
          g.beginPath(); g.moveTo(10, 4); g.lineTo(20, 20); g.lineTo(8, 18); g.fill();
          // body
          const bg = g.createLinearGradient(-12, 0, 12, 0);
          bg.addColorStop(0, '#fff'); bg.addColorStop(1, '#C9CEF0');
          g.fillStyle = bg;
          g.beginPath(); g.moveTo(0, -26); g.bezierCurveTo(14, -14, 13, 8, 10, 18); g.lineTo(-10, 18); g.bezierCurveTo(-13, 8, -14, -14, 0, -26); g.fill();
          g.fillStyle = col.main;
          g.beginPath(); g.moveTo(0, -26); g.bezierCurveTo(8, -19, 10, -14, 11, -10); g.lineTo(-11, -10); g.bezierCurveTo(-10, -14, -8, -19, 0, -26); g.fill();
          g.fillStyle = col.dark; g.beginPath(); g.arc(0, -1, 6.5, 0, TAU); g.fill();
          g.fillStyle = '#9EE7FF'; g.beginPath(); g.arc(0, -1, 4.5, 0, TAU); g.fill();
          g.fillStyle = 'rgba(255,255,255,.8)'; g.beginPath(); g.arc(-1.5, -2.5, 1.5, 0, TAU); g.fill();
          g.restore();
        }
      },
    };
  },
});
