'use strict';
/* Grab the Fish — time your claw to snatch goldfish swimming past. Avoid the pufferfish! */
Games.push({
  id: 'fish', name: 'Grab the Fish', control: 'TAP TO GRAB',
  desc: 'Fish swim across the pond. Tap to shoot your claw and grab a GOLDFISH. Grab a pufferfish and you lose one! First to 3.',
  color: '#F59E0B', grad: ['#FCD34D', '#0EA5E9'], gradDark: '#0C4A6E',
  icon: `<svg viewBox="0 0 64 64"><ellipse cx="30" cy="38" rx="16" ry="10" fill="#fff"/><path d="M44 38l12-9v18z" fill="#fff"/><circle cx="22" cy="35" r="2.5" fill="#0C4A6E"/><path d="M32 4v18" stroke="#fff" stroke-width="3"/><path d="M24 26l8-4 8 4M24 26v5M40 26v5" stroke="#fff" stroke-width="3.5" fill="none" stroke-linecap="round"/></svg>`,

  create(ctx) {
    const { players, S, arena: A } = ctx;
    const GOAL = 3;
    const band = { y0: A.y + A.h * 0.3, y1: A.y + A.h * 0.7 };
    const FR = S * 0.045, CLAW = S * 1.5;
    const score = new Map(players.map((p) => [p, 0]));
    const C = players.map((p) => {
      const up = p.lay.side === 'bottom';
      const y0 = up ? A.y + A.h * 0.86 : A.y + A.h * 0.14;
      return { p, up, x: p.lay.home.x, y0, ext: 0, state: 'idle', fish: null, stun: 0, cd: 0, think: 0, err: rand(-0.06, 0.06) };
    });
    const fish = [];
    let spawnT = 0;
    function spawn() {
      const dir = Math.random() < 0.5 ? 1 : -1;
      const r = Math.random(), type = r < 0.45 ? 'gold' : r < 0.78 ? 'blue' : 'puff';
      fish.push({
        type, dir, x: dir > 0 ? A.x - FR * 2 : A.x + A.w + FR * 2, y: rand(band.y0 + FR, band.y1 - FR),
        v: S * rand(0.22, 0.42) * (type === 'gold' ? 1.15 : 1), t: rand(0, 6), caught: null,
      });
    }
    for (let i = 0; i < 3; i++) { spawn(); fish[i].x = rand(A.x + 40, A.x + A.w - 40); }
    const tipY = (c) => c.y0 + (c.up ? -1 : 1) * c.ext;
    const reach = (c) => Math.abs((c.up ? band.y0 - FR : band.y1 + FR) - c.y0);

    function ai(c, dt) {
      c.think -= dt;
      if (c.think > 0 || c.state !== 'idle' || c.cd > 0 || c.stun > 0) return;
      c.think = rand(0.03, 0.06);
      for (const f of fish) {
        if (f.caught || f.type !== 'gold') continue;
        const tx = (c.x - f.x) / (f.v * f.dir);
        if (tx <= 0) continue;
        const ty = Math.abs(f.y - c.y0) / CLAW;
        if (Math.abs(tx - ty - c.err) < 0.035) {
          // make sure a pufferfish will not be in the way first
          const blocked = fish.some((o) => o.type === 'puff' && !o.caught && Math.abs(o.y - c.y0) < Math.abs(f.y - c.y0) && Math.abs(o.x + o.v * o.dir * (Math.abs(o.y - c.y0) / CLAW) - c.x) < FR * 1.4);
          if (!blocked || Math.random() < 0.15) { ctx.botTap(c.p); c.err = rand(-0.07, 0.07); }
          return;
        }
      }
    }

    return {
      where(p) { const c = C.find((x) => x.p === p); return { x: c.x, y: c.y0, r: 20 }; },
      update(dt) {
        spawnT -= dt;
        if (spawnT <= 0 && fish.filter((f) => !f.caught).length < 7) { spawn(); spawnT = rand(0.45, 0.9); }
        for (let i = fish.length - 1; i >= 0; i--) {
          const f = fish[i];
          f.t += dt;
          if (f.caught) continue;
          f.x += f.dir * f.v * dt;
          if (f.x < A.x - FR * 3 || f.x > A.x + A.w + FR * 3) fish.splice(i, 1);
        }
        for (const c of C) {
          const p = c.p;
          c.stun = Math.max(0, c.stun - dt); c.cd -= dt;
          if (!p.human && !ctx.over) ai(c, dt);
          if (p.pressed && c.state === 'idle' && c.stun <= 0 && c.cd <= 0) { c.state = 'out'; Sfx.whoosh(); }
          if (c.state === 'out') {
            c.ext += CLAW * dt;
            const ty = tipY(c);
            const hit = fish.find((f) => !f.caught && Math.abs(f.x - c.x) < FR * 1.1 && Math.abs(f.y - ty) < FR * 0.9);
            if (hit) { hit.caught = c; c.fish = hit; c.state = 'back'; Sfx.tone(500, 0.06, { type: 'square', vol: 0.08 }); }
            else if (c.ext >= reach(c)) c.state = 'back';
          } else if (c.state === 'back') {
            c.ext -= CLAW * 1.1 * dt;
            if (c.fish) { c.fish.x = c.x; c.fish.y = tipY(c); }
            if (c.ext <= 0) {
              c.ext = 0; c.state = 'idle'; c.cd = 0.25;
              const f = c.fish; c.fish = null;
              if (f) {
                fish.splice(fish.indexOf(f), 1);
                const col = COLORS[p.slot];
                if (f.type === 'gold') {
                  score.set(p, score.get(p) + 1); Sfx.coin(); ctx.buzz(30);
                  FX.burst(c.x, c.y0, '#FFD23F', 18, 260, 5, 0.7);
                  FX.float(c.x, c.y0 + (c.up ? -40 : 40), '+1', col.light, p.lay.rot, 30);
                  if (score.get(p) >= GOAL) ctx.end([...players].sort((a, b) => score.get(b) - score.get(a)));
                } else if (f.type === 'puff') {
                  score.set(p, Math.max(0, score.get(p) - 1)); c.stun = 1.2;
                  Sfx.wrong(); ctx.buzz(90); FX.addShake(5);
                  FX.burst(c.x, c.y0, '#A3E635', 16, 240, 4, 0.6);
                  FX.float(c.x, c.y0 + (c.up ? -40 : 40), 'OUCH! -1', '#FF6B6B', p.lay.rot, 22);
                } else {
                  Sfx.tone(300, 0.15, { type: 'triangle', vol: 0.1 });
                  FX.float(c.x, c.y0 + (c.up ? -40 : 40), 'NOPE', '#B8BEE6', p.lay.rot, 18);
                }
              }
            }
          }
        }
      },

      draw(g) {
        // pond
        const grd = g.createLinearGradient(0, band.y0, 0, band.y1);
        grd.addColorStop(0, '#0E7490'); grd.addColorStop(0.5, '#155E75'); grd.addColorStop(1, '#0E7490');
        g.fillStyle = '#123A2B'; g.fillRect(A.x, A.y, A.w, A.h);
        g.fillStyle = grd; rrect(g, A.x + 6, band.y0 - 14, A.w - 12, band.y1 - band.y0 + 28, 30); g.fill();
        g.strokeStyle = 'rgba(255,255,255,.08)'; g.lineWidth = 2;
        const t = ctx.t;
        for (let k = 0; k < 6; k++) {
          const y = band.y0 + ((k + 0.5) / 6) * (band.y1 - band.y0);
          g.beginPath();
          for (let x = A.x + 20; x < A.x + A.w - 20; x += 8) g.lineTo(x, y + Math.sin(x * 0.05 + t * 2 + k) * 3);
          g.stroke();
        }
        g.fillStyle = '#2F7D4A';
        for (const [fx, fy] of [[0.1, 0.33], [0.9, 0.66], [0.85, 0.36]]) { g.beginPath(); g.arc(A.x + A.w * fx, A.y + A.h * fy, 14, 0.4, TAU - 0.4); g.lineTo(A.x + A.w * fx, A.y + A.h * fy); g.fill(); }
        // fish
        for (const f of fish) {
          g.save(); g.translate(f.x, f.y);
          if (!f.caught) g.scale(f.dir, 1);
          else g.rotate(Math.sin(f.t * 30) * 0.3 + Math.PI / 2);
          const w = Math.sin(f.t * 10) * 0.25;
          if (f.type === 'puff') {
            g.fillStyle = '#84CC16'; g.beginPath(); g.arc(0, 0, FR * 0.85, 0, TAU); g.fill();
            g.strokeStyle = '#D9F99D'; g.lineWidth = 2.5;
            for (let k = 0; k < 10; k++) { const a = (k / 10) * TAU; g.beginPath(); g.moveTo(Math.cos(a) * FR * 0.8, Math.sin(a) * FR * 0.8); g.lineTo(Math.cos(a) * FR * 1.15, Math.sin(a) * FR * 1.15); g.stroke(); }
            g.fillStyle = '#fff'; g.beginPath(); g.arc(FR * 0.35, -FR * 0.2, FR * 0.22, 0, TAU); g.fill();
            g.fillStyle = '#111'; g.beginPath(); g.arc(FR * 0.4, -FR * 0.2, FR * 0.1, 0, TAU); g.fill();
          } else {
            const gold = f.type === 'gold';
            if (gold) { g.shadowColor = '#FFD23F'; g.shadowBlur = 14; }
            g.fillStyle = gold ? '#FFB703' : '#60A5FA';
            g.beginPath(); g.moveTo(-FR * 0.8, 0); g.lineTo(-FR * 1.4, -FR * (0.5 + w)); g.lineTo(-FR * 1.4, FR * (0.5 - w)); g.closePath(); g.fill();
            g.fillStyle = gold ? '#FFD23F' : '#93C5FD';
            g.beginPath(); g.ellipse(0, 0, FR, FR * 0.6, 0, 0, TAU); g.fill();
            g.shadowBlur = 0;
            g.fillStyle = 'rgba(255,255,255,.5)'; g.beginPath(); g.ellipse(-FR * 0.1, -FR * 0.25, FR * 0.5, FR * 0.15, 0, 0, TAU); g.fill();
            g.fillStyle = '#111'; g.beginPath(); g.arc(FR * 0.5, -FR * 0.12, FR * 0.12, 0, TAU); g.fill();
          }
          g.restore();
        }
        // claws
        for (const c of C) {
          const col = COLORS[c.p.slot], ty = tipY(c), dir = c.up ? -1 : 1;
          g.strokeStyle = 'rgba(255,255,255,.75)'; g.lineWidth = 3;
          g.beginPath(); g.moveTo(c.x, c.y0); g.lineTo(c.x, ty); g.stroke();
          g.strokeStyle = col.main; g.lineWidth = 4.5; g.lineCap = 'round';
          const open = c.state === 'out' ? 1 : 0.4;
          g.beginPath();
          g.moveTo(c.x - 10 * open - 4, ty + dir * 8); g.lineTo(c.x - 6, ty); g.lineTo(c.x + 6, ty); g.lineTo(c.x + 10 * open + 4, ty + dir * 8);
          g.moveTo(c.x, ty); g.lineTo(c.x, ty + dir * 10);
          g.stroke();
          g.save(); if (c.stun > 0) { g.translate(c.x, c.y0); g.rotate(Math.sin(c.stun * 30) * 0.2); g.translate(-c.x, -c.y0); }
          drawBlob(g, c.x, c.y0 - dir * 4, S * 0.05, col, { ang: c.up ? -Math.PI / 2 : Math.PI / 2, dead: c.stun > 0 });
          g.restore();
          ctx.hudPips(c.p, score.get(c.p), GOAL);
        }
      },
    };
  },
});
