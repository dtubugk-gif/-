'use strict';
/* Tank Battle — rotate, hold to drive, release to fire bouncing shots. */
Games.push({
  id: 'tanks', name: 'Tank Battle', control: 'HOLD + RELEASE',
  desc: 'Your tank spins. Hold to drive, let go to fire. Shots bounce off walls. 3 hits and you are out!',
  color: '#2DD881', grad: ['#34D399', '#059669'], gradDark: '#065F46',
  icon: `<svg viewBox="0 0 64 64"><rect x="10" y="30" width="44" height="18" rx="6" fill="#fff"/><rect x="8" y="44" width="48" height="8" rx="4" fill="#fff" opacity=".7"/><circle cx="30" cy="30" r="10" fill="#fff"/><rect x="30" y="25" width="26" height="7" rx="3" fill="#fff" transform="rotate(-20 30 28)"/><circle cx="58" cy="14" r="3" fill="#fff"/></svg>`,

  create(ctx) {
    const { players, S, cx, cy, arena: A } = ctx;
    const m = 10;
    const F = { x: A.x + m, y: A.y + m, w: A.w - m * 2, h: A.h - m * 2 };
    const hh = F.h / 2;
    const rel = [
      [-0.06 * S, -0.06 * S, 0.12 * S, 0.12 * S],
      [-0.38 * S, -0.42 * hh, 0.22 * S, 0.045 * S],
      [0.31 * S, -0.15 * hh - 0.09 * S, 0.045 * S, 0.18 * S],
    ];
    const walls = [];
    for (const [x, y, w, h] of rel) {
      walls.push({ x: cx + x, y: cy + y, w, h });
      if (x !== -w / 2 || y !== -h / 2) walls.push({ x: cx - x - w, y: cy - y - h, w, h });
    }
    const TR = S * 0.055, BR = S * 0.013, SPEED = S * 0.32, SPIN = 2.8, BSPD = S * 0.85;
    const T = players.map((p) => ({
      p, x: p.lay.home.x, y: p.lay.home.y, ang: p.lay.dir + Math.PI, hp: 3, cd: 0, hitT: 0, recoil: 0,
      think: 0, mode: 'aim', modeT: 0, aimTol: rand(0.08, 0.16), tread: 0,
    }));
    const bullets = [];

    const inRect = (x, y, r, w) => x > w.x - r && x < w.x + w.w + r && y > w.y - r && y < w.y + w.h + r;
    function los(ax, ay, bx, by) {
      const d = dist(ax, ay, bx, by), n = Math.ceil(d / 8);
      for (let i = 1; i < n; i++) {
        const x = lerp(ax, bx, i / n), y = lerp(ay, by, i / n);
        for (const w of walls) if (inRect(x, y, BR, w)) return false;
      }
      return true;
    }
    function free(x, y, r) {
      if (x < F.x + r || x > F.x + F.w - r || y < F.y + r || y > F.y + F.h - r) return false;
      for (const w of walls) if (inRect(x, y, r, w)) return false;
      return true;
    }
    function fire(t) {
      if (t.cd > 0 || bullets.filter((b) => b.o === t).length >= 3) return;
      t.cd = 0.3; t.recoil = 1;
      const bx = t.x + Math.cos(t.ang) * TR * 1.5, by = t.y + Math.sin(t.ang) * TR * 1.5;
      bullets.push({ o: t, x: bx, y: by, vx: Math.cos(t.ang) * BSPD, vy: Math.sin(t.ang) * BSPD, age: 0, bounces: 0 });
      Sfx.shoot();
      FX.burst(bx, by, COLORS[t.p.slot].light, 5, 120, 3, 0.25);
    }
    function ai(t, dt) {
      t.think -= dt; t.modeT -= dt;
      let best = null, bd = 1e9;
      for (const o of T) { if (o === t || o.p.out) continue; const d = dist(t.x, t.y, o.x, o.y); if (d < bd) { bd = d; best = o; } }
      if (!best) { ctx.botHold(t.p, false); return; }
      if (t.mode === 'drive') {
        const ahead = free(t.x + Math.cos(t.ang) * TR * 2.2, t.y + Math.sin(t.ang) * TR * 2.2, TR);
        if (t.modeT <= 0 || !ahead) { t.mode = 'aim'; ctx.botHold(t.p, false); }
        return;
      }
      if (t.think > 0) return;
      t.think = rand(0.03, 0.08);
      const want = Math.atan2(best.y - t.y, best.x - t.x);
      const diff = Math.abs(angNorm(want - t.ang));
      const see = los(t.x, t.y, best.x, best.y);
      if (see && diff < t.aimTol && t.cd <= 0) { ctx.botTap(t.p); return; }
      const ahead = free(t.x + Math.cos(t.ang) * TR * 3, t.y + Math.sin(t.ang) * TR * 3, TR);
      if (ahead && ((!see && Math.random() < 0.08) || (bd > S * 0.6 && diff < 0.4))) {
        t.mode = 'drive'; t.modeT = rand(0.3, 0.7); ctx.botHold(t.p, true);
      }
    }

    return {
      where(p) { const t = T.find((x) => x.p === p); return { x: t.x, y: t.y, r: TR }; },
      update(dt) {
        for (const t of T) {
          const p = t.p;
          if (p.out) continue;
          t.cd -= dt; t.hitT = Math.max(0, t.hitT - dt); t.recoil = Math.max(0, t.recoil - dt * 5);
          if (!p.human && !ctx.over) ai(t, dt);
          if (p.released) fire(t);
          if (p.down) {
            const nx = t.x + Math.cos(t.ang) * SPEED * dt, ny = t.y + Math.sin(t.ang) * SPEED * dt;
            if (free(nx, t.y, TR)) t.x = nx;
            if (free(t.x, ny, TR)) t.y = ny;
            t.tread += dt * 20;
          } else t.ang += SPIN * dt;
        }
        // tank vs tank
        for (let i = 0; i < T.length; i++) for (let j = i + 1; j < T.length; j++) {
          const a = T[i], b = T[j];
          if (a.p.out || b.p.out) continue;
          const d = dist(a.x, a.y, b.x, b.y);
          if (d < TR * 2 && d > 0) {
            const nx = (b.x - a.x) / d, ny = (b.y - a.y) / d, o = (TR * 2 - d) / 2;
            if (free(a.x - nx * o, a.y - ny * o, TR)) { a.x -= nx * o; a.y -= ny * o; }
            if (free(b.x + nx * o, b.y + ny * o, TR)) { b.x += nx * o; b.y += ny * o; }
          }
        }
        // bullets
        const hits = [];
        for (let i = bullets.length - 1; i >= 0; i--) {
          const b = bullets[i];
          b.age += dt;
          const px = b.x, py = b.y;
          b.x += b.vx * dt; b.y += b.vy * dt;
          let bounced = false;
          if (b.x < F.x + BR || b.x > F.x + F.w - BR) { b.vx *= -1; b.x = px; bounced = true; }
          if (b.y < F.y + BR || b.y > F.y + F.h - BR) { b.vy *= -1; b.y = py; bounced = true; }
          for (const w of walls) {
            if (inRect(b.x, b.y, BR, w)) {
              if (px > w.x - BR && px < w.x + w.w + BR) b.vy *= -1; else b.vx *= -1;
              b.x = px; b.y = py; bounced = true; break;
            }
          }
          if (bounced) { b.bounces++; Sfx.bounce(); }
          if (b.bounces > 2 || b.age > 3) { FX.burst(b.x, b.y, '#fff', 4, 80, 2, 0.25); bullets.splice(i, 1); continue; }
          for (const t of T) {
            if (t.p.out || t.hitT > 0 || (t === b.o && b.age < 0.3)) continue;
            if (dist(b.x, b.y, t.x, t.y) < TR + BR) {
              bullets.splice(i, 1);
              t.hp--; t.hitT = 0.6;
              Sfx.hit(); ctx.buzz(40); FX.addShake(5);
              FX.burst(b.x, b.y, COLORS[b.o.p.slot].main, 14, 260, 4, 0.5);
              if (t.hp <= 0) {
                Sfx.boom(); FX.addShake(12);
                FX.burst(t.x, t.y, '#FFB703', 30, 380, 7, 0.9); FX.burst(t.x, t.y, COLORS[t.p.slot].main, 20, 300, 6, 0.8);
                FX.ring(t.x, t.y, '#fff', TR * 4, 0.5, 8);
                hits.push(t.p);
              }
              break;
            }
          }
        }
        if (hits.length) ctx.eliminate(hits);
      },

      draw(g) {
        g.strokeStyle = 'rgba(123,140,255,.35)'; g.lineWidth = 3;
        rrect(g, F.x, F.y, F.w, F.h, 16); g.stroke();
        for (const w of walls) {
          g.fillStyle = '#151936'; rrect(g, w.x, w.y + 5, w.w, w.h, 7); g.fill();
          g.fillStyle = '#323A7A'; rrect(g, w.x, w.y, w.w, w.h, 7); g.fill();
          g.fillStyle = 'rgba(255,255,255,.12)'; rrect(g, w.x + 3, w.y + 3, w.w - 6, Math.min(4, w.h / 3), 2); g.fill();
        }
        for (const b of bullets) {
          const c = COLORS[b.o.p.slot];
          g.shadowColor = c.main; g.shadowBlur = 14;
          g.fillStyle = c.main; g.beginPath(); g.arc(b.x, b.y, BR * 1.3, 0, TAU); g.fill();
          g.shadowBlur = 0;
          g.fillStyle = '#fff'; g.beginPath(); g.arc(b.x, b.y, BR * 0.6, 0, TAU); g.fill();
        }
        for (const t of T) {
          if (t.p.out) continue;
          const c = COLORS[t.p.slot];
          g.save(); g.translate(t.x, t.y);
          g.fillStyle = 'rgba(0,0,0,.3)'; g.beginPath(); g.ellipse(0, 5, TR * 1.15, TR * 1.05, 0, 0, TAU); g.fill();
          g.rotate(t.ang);
          const fl = t.hitT > 0 && Math.floor(t.hitT * 30) % 2 === 0;
          // treads
          g.fillStyle = '#1B1F3B';
          rrect(g, -TR * 1.05, -TR * 1.05, TR * 2.1, TR * 0.55, 4); g.fill();
          rrect(g, -TR * 1.05, TR * 0.5, TR * 2.1, TR * 0.55, 4); g.fill();
          g.fillStyle = 'rgba(255,255,255,.18)';
          for (let k = 0; k < 5; k++) {
            const x = -TR + ((k * TR * 0.45 + t.tread) % (TR * 2.1));
            g.fillRect(x, -TR * 1.0, 2, TR * 0.45); g.fillRect(x, TR * 0.55, 2, TR * 0.45);
          }
          // hull
          g.fillStyle = fl ? '#fff' : c.main;
          rrect(g, -TR * 0.9, -TR * 0.62, TR * 1.8, TR * 1.24, 7); g.fill();
          g.fillStyle = 'rgba(255,255,255,.25)'; rrect(g, -TR * 0.8, -TR * 0.55, TR * 1.6, TR * 0.3, 4); g.fill();
          // barrel
          const rc = t.recoil * TR * 0.3;
          g.fillStyle = c.dark; rrect(g, TR * 0.1 - rc, -TR * 0.17, TR * 1.35, TR * 0.34, 3); g.fill();
          g.fillStyle = fl ? '#fff' : c.light; g.beginPath(); g.arc(-rc * 0.3, 0, TR * 0.45, 0, TAU); g.fill();
          g.restore();
          // hp pips
          for (let k = 0; k < 3; k++) {
            g.fillStyle = k < t.hp ? '#fff' : 'rgba(255,255,255,.18)';
            g.beginPath(); g.arc(t.x - 9 + k * 9, t.y - TR * 1.6, 3, 0, TAU); g.fill();
          }
        }
      },
    };
  },
});
