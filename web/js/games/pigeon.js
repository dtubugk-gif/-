'use strict';
/* Feed the Pigeon — aim your slingshot, hold to pull back, let go to lob bread right into the pigeon. */
Games.push({
  id: 'pigeon', name: 'Feed the Pigeon', control: 'HOLD + RELEASE',
  desc: 'Your slingshot aim swings. Hold to pull back (more power), let go to throw bread. Land it on the hungry pigeon! First to 3.',
  color: '#A78BFA', grad: ['#DDD6FE', '#7C3AED'], gradDark: '#4C1D95',
  icon: `<svg viewBox="0 0 64 64"><ellipse cx="34" cy="38" rx="16" ry="11" fill="#fff"/><circle cx="20" cy="28" r="8" fill="#fff"/><path d="M12 28l-6 2 6 2z" fill="#FCD34D"/><circle cx="18" cy="26" r="1.8" fill="#4C1D95"/><path d="M48 36l10-4-6 10z" fill="#fff" opacity=".8"/><circle cx="44" cy="12" r="4" fill="#FDE68A"/><path d="M40 16q-6 6-12 6" stroke="#fff" stroke-width="2" fill="none" stroke-dasharray="2 4"/></svg>`,

  create(ctx) {
    const { players, S, arena: A, cx, cy } = ctx;
    const GOAL = 3, PR = S * 0.07, FLY = 0.55;
    const score = new Map(players.map((p) => [p, 0]));
    const zone = { x0: A.x + A.w * 0.18, x1: A.x + A.w * 0.82, y0: A.y + A.h * 0.32, y1: A.y + A.h * 0.68 };
    const pig = { x: cx, y: cy, tx: cx, ty: cy, face: 1, step: 0, peck: 0, happy: 0, retarget: 0 };
    const Sl = players.map((p, i) => {
      const bottom = p.lay.side === 'bottom';
      const x = p.lay.home.x, y = bottom ? A.y + A.h * 0.86 : A.y + A.h * 0.14;
      return {
        p, x, y, base: Math.atan2(cy - y, cx - x), aim: 0, phase: rand(0, TAU), spd: 1.9 + i * 0.13,
        power: 0, pdir: 1, cd: 0.3, think: 0, plan: null,
      };
    });
    const crumbs = [];
    const range = (s) => ({ lo: S * 0.18, hi: Math.hypot(A.w, A.h) * 0.62 });
    const landing = (s) => { const r = range(s), D = lerp(r.lo, r.hi, s.power); return { x: s.x + Math.cos(s.aim) * D, y: s.y + Math.sin(s.aim) * D }; };

    function ai(s, dt) {
      if (s.cd > 0) return;
      s.think -= dt;
      if (s.think > 0) return;
      s.think = rand(0.02, 0.05);
      const lead = FLY + 0.35;
      const px = pig.x + (pig.tx - pig.x) * 0.25 * lead, py = pig.y + (pig.ty - pig.y) * 0.25 * lead;
      if (!s.p.down) {
        const want = Math.atan2(py - s.y, px - s.x);
        if (Math.abs(angNorm(want - s.aim)) < 0.07) { ctx.botHold(s.p, true); s.plan = rand(-PR * 1.1, PR * 1.1); }
      } else {
        const r = range(s), D = lerp(r.lo, r.hi, s.power);
        if (D >= dist(s.x, s.y, px, py) + s.plan || s.power > 0.98) ctx.botHold(s.p, false);
      }
    }

    return {
      where(p) { const s = Sl.find((x) => x.p === p); return { x: s.x, y: s.y, r: 22 }; },
      update(dt) {
        // pigeon wanders, and walks over to eat bread lying on the ground
        pig.retarget -= dt; pig.happy = Math.max(0, pig.happy - dt); pig.peck = Math.max(0, pig.peck - dt);
        const ground = crumbs.filter((c) => c.t >= FLY);
        if (ground.length && Math.random() < 0.02) { const c = pick(ground); pig.tx = c.x; pig.ty = c.y; pig.retarget = 2; }
        if (pig.retarget <= 0) { pig.tx = rand(zone.x0, zone.x1); pig.ty = rand(zone.y0, zone.y1); pig.retarget = rand(1.2, 2.6); }
        const dx = pig.tx - pig.x, dy = pig.ty - pig.y, dd = Math.hypot(dx, dy), sp = S * (0.16 + Math.min(0.12, ctx.t * 0.005));
        if (dd > 2 && pig.peck <= 0) { pig.x += (dx / dd) * Math.min(dd, sp * dt); pig.y += (dy / dd) * Math.min(dd, sp * dt); pig.step += dt * 12; pig.face = dx >= 0 ? 1 : -1; }
        for (const s of Sl) {
          const p = s.p;
          s.cd -= dt;
          if (!p.human && !ctx.over) ai(s, dt);
          if (p.down && s.cd <= 0) {
            s.power += s.pdir * dt / 0.9;
            if (s.power > 1) { s.power = 1; s.pdir = -1; } else if (s.power < 0) { s.power = 0; s.pdir = 1; }
          } else {
            s.phase += dt * s.spd;
            s.aim = s.base + Math.sin(s.phase) * 0.62;
          }
          if (p.released && s.cd <= 0 && s.power > 0.02) {
            const L = landing(s);
            crumbs.push({ s, x0: s.x, y0: s.y, x: L.x, y: L.y, t: 0, life: 2.5 });
            s.power = 0; s.pdir = 1; s.cd = 0.5;
            Sfx.tone(700, 0.08, { type: 'triangle', vol: 0.1, slide: 0.6 });
          }
        }
        for (let i = crumbs.length - 1; i >= 0; i--) {
          const c = crumbs[i];
          const was = c.t; c.t += dt;
          if (was < FLY && c.t >= FLY) {
            if (dist(c.x, c.y, pig.x, pig.y) < PR * 1.15) {
              crumbs.splice(i, 1);
              const p = c.s.p, col = COLORS[p.slot];
              score.set(p, score.get(p) + 1);
              pig.happy = 0.8; pig.peck = 0.3;
              Sfx.coin(); ctx.buzz(30);
              FX.burst(pig.x, pig.y, col.main, 16, 220, 4, 0.6);
              FX.float(pig.x, pig.y - PR, '+1', col.light, p.lay.rot, 30);
              if (score.get(p) >= GOAL) ctx.end([...players].sort((a, b) => score.get(b) - score.get(a)));
              continue;
            }
            FX.burst(c.x, c.y, '#E9C46A', 5, 80, 2, 0.3);
          }
          if (c.t >= FLY) {
            c.life -= dt;
            if (dist(c.x, c.y, pig.x, pig.y) < PR * 0.8) { crumbs.splice(i, 1); pig.peck = 0.35; Sfx.tone(900, 0.04, { type: 'square', vol: 0.05 }); continue; }
            if (c.life <= 0 || c.x < A.x || c.x > A.x + A.w || c.y < A.y || c.y > A.y + A.h) crumbs.splice(i, 1);
          }
        }
      },

      draw(g) {
        g.fillStyle = '#2D4A3A'; g.fillRect(A.x, A.y, A.w, A.h);
        g.fillStyle = '#5B5F73'; rrect(g, zone.x0 - 30, zone.y0 - 30, zone.x1 - zone.x0 + 60, zone.y1 - zone.y0 + 60, 40); g.fill();
        g.fillStyle = 'rgba(255,255,255,.05)';
        for (let x = zone.x0 - 20; x < zone.x1 + 20; x += 28) for (let y = zone.y0 - 20; y < zone.y1 + 20; y += 28) { rrect(g, x, y, 22, 22, 4); g.fill(); }
        // crumbs on the ground / in flight
        for (const c of crumbs) {
          const k = Math.min(1, c.t / FLY);
          const x = lerp(c.x0, c.x, k), y = lerp(c.y0, c.y, k), h = Math.sin(k * Math.PI) * S * 0.18;
          g.fillStyle = 'rgba(0,0,0,.25)'; g.beginPath(); g.ellipse(x, y + 3, 5, 3, 0, 0, TAU); g.fill();
          g.globalAlpha = c.t >= FLY ? Math.min(1, c.life) : 1;
          g.fillStyle = '#E9C46A'; g.beginPath(); g.arc(x, y - h, 5 + (k < 1 ? h * 0.03 : 0), 0, TAU); g.fill();
          g.fillStyle = '#F4E3B1'; g.beginPath(); g.arc(x - 1.5, y - h - 1.5, 2, 0, TAU); g.fill();
          g.globalAlpha = 1;
        }
        // pigeon
        const bob = Math.sin(pig.step) * 2, f = pig.face;
        g.save(); g.translate(pig.x, pig.y);
        g.fillStyle = 'rgba(0,0,0,.25)'; g.beginPath(); g.ellipse(0, PR * 0.7, PR * 1.1, PR * 0.35, 0, 0, TAU); g.fill();
        g.scale(f, 1);
        g.fillStyle = '#F97316';
        g.fillRect(-PR * 0.25 + Math.sin(pig.step) * 3, PR * 0.4, 3, PR * 0.35); g.fillRect(PR * 0.1 - Math.sin(pig.step) * 3, PR * 0.4, 3, PR * 0.35);
        g.fillStyle = '#9CA3AF'; g.beginPath(); g.ellipse(-PR * 0.1, bob, PR, PR * 0.62, 0, 0, TAU); g.fill();
        g.fillStyle = '#6B7280'; g.beginPath(); g.ellipse(-PR * 0.35, bob - PR * 0.05, PR * 0.55, PR * 0.32, -0.2, 0, TAU); g.fill();
        g.beginPath(); g.moveTo(-PR * 1.0, bob); g.lineTo(-PR * 1.5, bob - PR * 0.2); g.lineTo(-PR * 1.45, bob + PR * 0.25); g.fill();
        const hy = bob - PR * 0.55 + (pig.peck > 0 ? PR * 0.45 : 0);
        g.fillStyle = '#7C8597'; g.beginPath(); g.arc(PR * 0.7, hy, PR * 0.42, 0, TAU); g.fill();
        g.fillStyle = 'rgba(52,211,153,.55)'; g.beginPath(); g.arc(PR * 0.55, hy + PR * 0.3, PR * 0.3, 0, TAU); g.fill();
        g.fillStyle = '#FCD34D'; g.beginPath(); g.moveTo(PR * 1.05, hy - 3); g.lineTo(PR * 1.4, hy + 2); g.lineTo(PR * 1.05, hy + 5); g.fill();
        g.fillStyle = '#fff'; g.beginPath(); g.arc(PR * 0.82, hy - PR * 0.1, PR * 0.13, 0, TAU); g.fill();
        g.fillStyle = '#111'; g.beginPath(); g.arc(PR * 0.86, hy - PR * 0.1, PR * 0.07, 0, TAU); g.fill();
        g.restore();
        if (pig.happy > 0) { g.globalAlpha = Math.min(1, pig.happy * 2); text(g, '♥', pig.x, pig.y - PR * 1.4 - (0.8 - pig.happy) * 30, 22, '#FF4D6D'); g.globalAlpha = 1; }
        // slingshots
        for (const s of Sl) {
          const col = COLORS[s.p.slot];
          const pull = s.p.down && s.cd <= 0 ? s.power : 0;
          if (s.cd <= 0) {
            const L = landing(s);
            if (s.p.down) {
              g.strokeStyle = col.light; g.lineWidth = 2.5; g.setLineDash([3, 7]);
              g.beginPath(); g.moveTo(s.x, s.y);
              const mx = (s.x + L.x) / 2, my = (s.y + L.y) / 2;
              g.quadraticCurveTo(mx, my - S * 0.25, L.x, L.y); g.stroke(); g.setLineDash([]);
              g.strokeStyle = col.main; g.lineWidth = 3; g.beginPath(); g.arc(L.x, L.y, 9, 0, TAU); g.stroke();
            } else {
              g.strokeStyle = col.light; g.lineWidth = 3; g.globalAlpha = 0.7;
              g.beginPath(); g.moveTo(s.x + Math.cos(s.aim) * 30, s.y + Math.sin(s.aim) * 30); g.lineTo(s.x + Math.cos(s.aim) * 58, s.y + Math.sin(s.aim) * 58); g.stroke();
              g.globalAlpha = 1;
            }
          }
          g.save(); g.translate(s.x, s.y); g.rotate(s.aim + Math.PI / 2);
          g.strokeStyle = col.dark; g.lineWidth = 6; g.lineCap = 'round';
          g.beginPath(); g.moveTo(0, 18); g.lineTo(0, 4); g.lineTo(-10, -10); g.moveTo(0, 4); g.lineTo(10, -10); g.stroke();
          g.strokeStyle = '#FDE68A'; g.lineWidth = 2;
          g.beginPath(); g.moveTo(-10, -10); g.lineTo(0, -6 + pull * 26); g.lineTo(10, -10); g.stroke();
          if (s.cd <= 0) { g.fillStyle = '#E9C46A'; g.beginPath(); g.arc(0, -6 + pull * 26, 4.5, 0, TAU); g.fill(); }
          g.restore();
          if (s.p.down && s.cd <= 0) {
            g.fillStyle = 'rgba(0,0,0,.4)'; rrect(g, s.x - 22, s.y + (s.p.lay.side === 'bottom' ? 26 : -32), 44, 6, 3); g.fill();
            g.fillStyle = col.light; rrect(g, s.x - 22, s.y + (s.p.lay.side === 'bottom' ? 26 : -32), 44 * s.power, 6, 3); g.fill();
          }
          ctx.hudPips(s.p, score.get(s.p), GOAL);
        }
      },
    };
  },
});
