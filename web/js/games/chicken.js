'use strict';
/* Chicken Flip — run along the walls; tap to flip gravity to the other side before a gap reaches you. */
Games.push({
  id: 'chicken', name: 'Chicken Flip', control: 'TAP TO FLIP',
  desc: 'Your chicken runs on the walls. Tap to flip gravity and jump to the other wall. Fall through a gap and you are out!',
  color: '#FB923C', grad: ['#FED7AA', '#F97316'], gradDark: '#9A3412',
  icon: `<svg viewBox="0 0 64 64"><rect x="6" y="6" width="8" height="52" rx="3" fill="#fff" opacity=".6"/><rect x="50" y="6" width="8" height="20" rx="3" fill="#fff" opacity=".6"/><rect x="50" y="40" width="8" height="18" rx="3" fill="#fff" opacity=".6"/><circle cx="26" cy="34" r="10" fill="#fff"/><path d="M22 23q4-6 8 0" fill="#EF4444"/><path d="M26 20l4 4h-8z" fill="#F97316"/><path d="M38 34h8" stroke="#fff" stroke-width="3" stroke-linecap="round" stroke-dasharray="2 4"/></svg>`,

  create(ctx) {
    const { players, S, arena: A } = ctx;
    const WT = 14, CR = S * 0.045, G = S * 7.5;
    const xL = A.x + WT + CR, xR = A.x + A.w - WT - CR;
    let d = 0, speed = S * 0.42;
    // gaps in world coordinates (world y grows upward; screen y = bottom - (wy - d))
    const gaps = { '-1': [], '1': [] };
    let genY = A.h * 1.1, side = Math.random() < 0.5 ? -1 : 1, hard = 0;
    function generate(upTo) {
      while (genY < upTo) {
        const len = (rand(0.13, 0.2) + hard * 0.12) * A.h;
        gaps[side].push({ y0: genY, y1: genY + len });
        const spacing = len + S * Math.max(0.42, rand(0.55, 0.95) - hard * 0.35);
        genY += spacing;
        side = Math.random() < 0.75 ? -side : side;
      }
    }
    generate(A.h * 4);
    const toScreen = (wy) => A.y + A.h - (wy - d);
    const solidAt = (s, wy0, wy1) => !gaps[s].some((gp) => gp.y1 > wy0 && gp.y0 < wy1);

    const order = [...players].sort((p, q) => (p.lay.side === 'bottom' ? -1 : 1) - (q.lay.side === 'bottom' ? -1 : 1));
    const n = players.length;
    const Ch = players.map((p) => {
      const k = order.indexOf(p);
      const sy = A.y + A.h * (0.78 - (k / Math.max(1, n - 1)) * 0.56);
      const s = p.lay.btn.x < A.w / 2 ? -1 : 1;
      return { p, sy, side: s, x: s < 0 ? xL : xR, vx: 0, grounded: true, dead: false, run: 0, think: 0, react: rand(0.05, 0.15) };
    });
    // keep each chicken's starting wall solid for a moment
    for (const c of Ch) { const wy = A.y + A.h - c.sy + d; gaps[c.side] = gaps[c.side].filter((gp) => gp.y0 > wy + A.h * 0.5); }

    function ai(c, dt) {
      c.think -= dt;
      if (c.think > 0 || !c.grounded) return;
      c.think = rand(0.04, 0.08);
      const wy = A.y + A.h - c.sy + d;
      const lookT = 0.32 + c.react;
      // a gap is coming on my wall?
      const danger = !solidAt(c.side, wy - CR, wy + CR + speed * lookT);
      if (!danger) { c.facing = false; return; }
      // like a person, a bot sometimes reacts too late, more often as the game speeds up
      if (!c.facing) { c.facing = true; c.blunder = Math.random() < 0.06 + hard * 0.3; }
      if (c.blunder) return;
      const tf = Math.sqrt((2 * (xR - xL)) / G);
      const land = wy + speed * tf;
      const safe = solidAt(-c.side, land - CR * 1.5, land + CR * 1.5 + speed * 0.25);
      if (safe || Math.random() < 0.05) { ctx.botTap(c.p); c.react = rand(0.04, 0.16) + hard * 0.1; }
    }

    return {
      where(p) { const c = Ch.find((x) => x.p === p); return { x: c.x, y: c.sy, r: CR }; },
      update(dt) {
        hard = Math.min(1, ctx.t / 40);
        speed = S * Math.min(1.1, 0.42 + ctx.t * 0.018);
        d += speed * dt;
        generate(d + A.h * 3);
        for (const s of ['-1', '1']) gaps[s] = gaps[s].filter((gp) => gp.y1 > d - 50);
        const dead = [];
        for (const c of Ch) {
          if (c.dead) { c.x += c.vx * dt; c.vx *= 1.02; continue; }
          const p = c.p;
          if (!p.human && !ctx.over) ai(c, dt);
          if (p.pressed && c.grounded) { c.side = -c.side; c.grounded = false; c.vx = c.side * S * 0.5; Sfx.jump(); }
          const wy = A.y + A.h - c.sy + d;
          const wallX = c.side < 0 ? xL : xR;
          const hasWall = solidAt(c.side, wy - CR * 0.6, wy + CR * 0.6);
          if (c.grounded && !hasWall) c.grounded = false;
          if (!c.grounded) {
            c.vx += c.side * G * dt;
            c.x += c.vx * dt;
            const past = c.side < 0 ? c.x <= wallX : c.x >= wallX;
            if (past && hasWall && Math.abs(c.x - wallX) < CR * 1.2) { c.x = wallX; c.vx = 0; c.grounded = true; Sfx.tone(260, 0.05, { type: 'triangle', vol: 0.08 }); FX.burst(wallX + c.side * CR, c.sy, '#fff', 5, 100, 2, 0.3); }
            if (c.x < A.x - CR * 2 || c.x > A.x + A.w + CR * 2) {
              c.dead = true; c.vx = c.side * S;
              FX.burst(clamp(c.x, A.x, A.x + A.w), c.sy, '#fff', 20, 260, 4, 0.7); FX.burst(clamp(c.x, A.x, A.x + A.w), c.sy, COLORS[p.slot].main, 14, 220, 4, 0.6);
              dead.push(p);
            }
          } else c.run += dt * 14;
        }
        if (dead.length) ctx.eliminate(dead);
      },

      draw(g) {
        g.fillStyle = '#1A1533'; g.fillRect(A.x, A.y, A.w, A.h);
        // scrolling background stripes for a sense of speed
        g.fillStyle = 'rgba(255,255,255,.03)';
        const step = 60, off = d % step;
        for (let y = A.y + A.h + off - step * 2; y > A.y - step; y -= step) g.fillRect(A.x + WT, y, A.w - WT * 2, step / 2);
        for (const s of [-1, 1]) {
          const x = s < 0 ? A.x : A.x + A.w - WT;
          g.fillStyle = '#C2410C'; g.fillRect(x, A.y, WT, A.h);
          g.fillStyle = '#EA580C';
          for (let y = A.y + A.h + (d % 24) - 24; y > A.y - 24; y -= 24) g.fillRect(x + 2, y, WT - 4, 10);
          for (const gp of gaps[s]) {
            const y0 = toScreen(gp.y1), y1 = toScreen(gp.y0);
            if (y1 < A.y || y0 > A.y + A.h) continue;
            g.fillStyle = '#0B0820'; g.fillRect(x - 1, y0, WT + 2, y1 - y0);
            g.fillStyle = '#FFD23F';
            for (let y = y0; y < y1; y += 12) g.fillRect(s < 0 ? x + WT : x - 4, y, 4, 6);
          }
        }
        for (const c of Ch) {
          if (c.dead && (c.x < A.x - 60 || c.x > A.x + A.w + 60)) continue;
          const col = COLORS[c.p.slot];
          g.save(); g.translate(c.x, c.sy);
          g.rotate(c.side < 0 ? Math.PI / 2 : -Math.PI / 2);
          if (c.dead) g.rotate(c.vx * 0.01);
          // feet
          if (c.grounded) {
            const sw = Math.sin(c.run) * CR * 0.4;
            g.fillStyle = '#F97316';
            g.fillRect(-CR * 0.5 + sw, CR * 0.75, 5, CR * 0.4); g.fillRect(CR * 0.25 - sw, CR * 0.75, 5, CR * 0.4);
          }
          g.fillStyle = '#fff'; g.beginPath(); g.arc(0, 0, CR, 0, TAU); g.fill();
          g.fillStyle = col.main; g.beginPath(); g.ellipse(0, CR * 0.25, CR * 0.9, CR * 0.55, 0, 0, Math.PI); g.fill();
          g.fillStyle = '#EF4444';
          g.beginPath(); g.arc(-CR * 0.2, -CR * 0.95, CR * 0.25, 0, TAU); g.arc(CR * 0.15, -CR * 1.0, CR * 0.22, 0, TAU); g.fill();
          g.restore();
          // beak + eye face the running direction (up the screen)
          g.fillStyle = '#F97316';
          g.beginPath(); g.moveTo(c.x - 5, c.sy - CR * 0.7); g.lineTo(c.x + 5, c.sy - CR * 0.7); g.lineTo(c.x, c.sy - CR * 1.25); g.fill();
          g.fillStyle = '#111'; g.beginPath(); g.arc(c.x - c.side * CR * 0.3, c.sy - CR * 0.25, 2.6, 0, TAU); g.fill();
        }
      },
    };
  },
});
