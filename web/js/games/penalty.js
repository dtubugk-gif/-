'use strict';
/* Penalty Kicks — your aim swings left and right; tap to shoot past the keeper. */
Games.push({
  id: 'penalty', name: 'Penalty Kicks', control: 'TAP TO SHOOT',
  desc: 'Your aim swings left and right. Tap to shoot past the goalkeeper. Bank shots off the walls work too! First to 2 goals.',
  color: '#10B981', grad: ['#34D399', '#0F766E'], gradDark: '#134E4A',
  icon: `<svg viewBox="0 0 64 64"><path d="M8 14h48v20" stroke="#fff" stroke-width="5" fill="none" stroke-linejoin="round"/><path d="M8 14v20" stroke="#fff" stroke-width="5"/><path d="M12 18h40M12 24h40M12 30h40M18 14v20M28 14v20M38 14v20M48 14v20" stroke="#fff" stroke-width="1.5" opacity=".5"/><circle cx="40" cy="48" r="9" fill="#fff"/><path d="M40 44l3 2-1 4h-4l-1-4z" fill="#0F766E"/></svg>`,

  create(ctx) {
    const { players, S, arena: A, cx } = ctx;
    const GOAL = 2;
    const GW = A.w * 0.56, BR = S * 0.028, KW = GW * 0.24, KH = 14;
    const goals = [
      { top: true, line: A.y + 16, kx: cx, kv: 0, tgt: cx, retarget: 0 },
      { top: false, line: A.y + A.h - 16, kx: cx, kv: 0, tgt: cx, retarget: 0 },
    ];
    for (const gl of goals) gl.ky = gl.top ? gl.line + 18 : gl.line - 18;
    const score = new Map(players.map((p) => [p, 0]));
    const Sh = players.map((p, i) => {
      const bottom = p.lay.side === 'bottom';
      return {
        p, x: p.lay.home.x, y: bottom ? A.y + A.h * 0.74 : A.y + A.h * 0.26,
        base: bottom ? -Math.PI / 2 : Math.PI / 2, goal: bottom ? goals[0] : goals[1],
        phase: rand(0, TAU), spd: 2.3 + i * 0.17, aim: 0, cd: 0.4, ball: null, botWait: rand(0.2, 0.8), kick: 0,
      };
    });
    const balls = [];

    function predictX(x, y, ang, gy) {
      const t = (gy - y) / Math.sin(ang);
      return x + Math.cos(ang) * t;
    }
    function shoot(s) {
      if (s.ball || s.cd > 0) return;
      const b = { s, x: s.x, y: s.y, vx: Math.cos(s.aim) * S * 1.15, vy: Math.sin(s.aim) * S * 1.15, res: null, t: 0, roll: 0, kerr: rand(-1, 1) * KW * 0.75 };
      balls.push(b); s.ball = b; s.kick = 1;
      Sfx.tone(130, 0.12, { type: 'sine', vol: 0.4, slide: 0.5 }); Sfx.noise(0.06, { freq: 2500, vol: 0.2 });
    }
    function resolve(b, res) {
      b.res = res; b.t = 0;
      const s = b.s, col = COLORS[s.p.slot];
      if (res === 'GOAL') {
        score.set(s.p, score.get(s.p) + 1);
        Sfx.coin(); ctx.buzz(30);
        FX.burst(b.x, b.y, col.main, 24, 300, 5, 0.8); FX.burst(b.x, b.y, '#fff', 10, 200, 3, 0.5);
        FX.float(b.x, b.goalY, 'GOAL!', col.light, s.p.lay.rot, 24);
        if (score.get(s.p) >= GOAL) ctx.end([...players].sort((a, c) => score.get(c) - score.get(a)));
      } else {
        Sfx.tone(res === 'SAVED' ? 240 : 180, 0.2, { type: 'triangle', vol: 0.14, slide: 0.6 });
        FX.float(b.x, b.y, res, '#fff', s.p.lay.rot, 18);
      }
    }

    return {
      where(p) { const s = Sh.find((x) => x.p === p); return { x: s.x, y: s.y, r: S * 0.06 }; },
      update(dt) {
        if (ctx.t > 35 && !ctx.over) { const r = [...players].sort((a, c) => score.get(c) - score.get(a)); ctx.end(r, score.get(r[0]) === score.get(r[1])); }
        // keepers: patrol, then rush to block incoming shots
        for (const gl of goals) {
          let target = null, soon = 1e9, tb = null;
          for (const b of balls) {
            if (b.res || b.s.goal !== gl) continue;
            const t = (gl.ky - b.y) / b.vy;
            if (t > 0 && t < soon) { soon = t; target = b.x + b.vx * t; tb = b; }
          }
          gl.retarget -= dt;
          if (gl.retarget <= 0) { gl.tgt = cx + rand(-GW * 0.32, GW * 0.32); gl.retarget = rand(0.5, 1.3); }
          const rushing = target != null && soon < 0.38;
          const want = rushing ? target + tb.kerr : gl.tgt;
          const ks = S * (rushing ? 0.4 + Math.min(0.12, ctx.t * 0.003) : 0.28);
          gl.kx += clamp(want - gl.kx, -ks * dt, ks * dt);
          gl.kx = clamp(gl.kx, cx - GW / 2 + KW / 2, cx + GW / 2 - KW / 2);
        }
        for (const s of Sh) {
          s.cd -= dt; s.kick = Math.max(0, s.kick - dt * 4);
          s.phase += dt * s.spd;
          s.aim = s.base + Math.sin(s.phase) * 0.55;
          if (!s.p.human && !ctx.over && !s.ball && s.cd <= 0) {
            s.botWait -= dt;
            const gy = s.goal.line;
            const lx = predictX(s.x, s.y, s.aim, gy);
            const inGoal = lx > cx - GW / 2 + BR * 3 && lx < cx + GW / 2 - BR * 3;
            if (s.botWait <= 0 && inGoal && Math.abs(lx - s.goal.kx) > KW * rand(0.5, 0.9)) { ctx.botTap(s.p); s.botWait = rand(0.15, 0.6); }
          }
          if (s.p.pressed) shoot(s);
        }
        for (let i = balls.length - 1; i >= 0; i--) {
          const b = balls[i];
          if (b.res) {
            b.t += dt; b.x += b.vx * dt * 0.4; b.y += b.vy * dt * 0.4;
            if (b.t > 0.5) { balls.splice(i, 1); b.s.ball = null; b.s.cd = 0.45; }
            continue;
          }
          b.x += b.vx * dt; b.y += b.vy * dt; b.roll += S * dt / BR;
          if (b.x < A.x + BR) { b.x = A.x + BR; b.vx = Math.abs(b.vx); Sfx.bounce(); }
          if (b.x > A.x + A.w - BR) { b.x = A.x + A.w - BR; b.vx = -Math.abs(b.vx); Sfx.bounce(); }
          const gl = b.s.goal;
          if (Math.abs(b.y - gl.ky) < KH / 2 + BR && Math.abs(b.x - gl.kx) < KW / 2 + BR) {
            b.vy = -b.vy * 0.5; b.vx = rand(-1, 1) * S * 0.3;
            FX.burst(b.x, b.y, '#fff', 10, 200, 3, 0.4); FX.addShake(3);
            resolve(b, 'SAVED');
            continue;
          }
          const crossed = gl.top ? b.y < gl.line : b.y > gl.line;
          if (crossed) {
            b.goalY = gl.line + (gl.top ? 26 : -26);
            const inside = b.x > cx - GW / 2 && b.x < cx + GW / 2;
            resolve(b, inside ? 'GOAL' : 'MISS');
            if (inside) { b.vx *= 0.2; b.vy *= 0.25; }
          }
          if (Math.random() < 0.4) FX.trail(b.x, b.y, 'rgba(255,255,255,.5)', 2.5, 0.2);
        }
      },

      draw(g) {
        g.fillStyle = '#176A3C'; g.fillRect(A.x, A.y, A.w, A.h);
        const bands = 9, bh = A.h / bands;
        for (let i = 0; i < bands; i++) if (i % 2) { g.fillStyle = 'rgba(255,255,255,.04)'; g.fillRect(A.x, A.y + i * bh, A.w, bh); }
        g.strokeStyle = 'rgba(255,255,255,.55)'; g.lineWidth = 3;
        for (const gl of goals) {
          const bw = GW * 1.4, bh2 = A.h * 0.16;
          g.strokeRect(cx - bw / 2, gl.top ? gl.line : gl.line - bh2, bw, bh2);
          g.beginPath(); g.moveTo(A.x, gl.line); g.lineTo(A.x + A.w, gl.line); g.stroke();
        }
        g.beginPath(); g.moveTo(A.x, A.y + A.h / 2); g.lineTo(A.x + A.w, A.y + A.h / 2); g.stroke();
        for (const gl of goals) {
          const y0 = gl.top ? A.y : gl.line, h = 16;
          g.fillStyle = 'rgba(255,255,255,.14)'; g.fillRect(cx - GW / 2, y0, GW, h);
          g.strokeStyle = 'rgba(255,255,255,.35)'; g.lineWidth = 1;
          for (let x = cx - GW / 2; x <= cx + GW / 2; x += 9) { g.beginPath(); g.moveTo(x, y0); g.lineTo(x, y0 + h); g.stroke(); }
          for (let y = y0; y <= y0 + h; y += 6) { g.beginPath(); g.moveTo(cx - GW / 2, y); g.lineTo(cx + GW / 2, y); g.stroke(); }
          g.strokeStyle = '#fff'; g.lineWidth = 5; g.lineCap = 'round';
          g.beginPath(); g.moveTo(cx - GW / 2, y0); g.lineTo(cx - GW / 2, y0 + h); g.moveTo(cx + GW / 2, y0); g.lineTo(cx + GW / 2, y0 + h); g.stroke();
          // keeper
          g.fillStyle = 'rgba(0,0,0,.3)'; rrect(g, gl.kx - KW / 2 + 2, gl.ky - KH / 2 + 4, KW, KH, 7); g.fill();
          const kg = g.createLinearGradient(0, gl.ky - KH / 2, 0, gl.ky + KH / 2);
          kg.addColorStop(0, '#C4B5FD'); kg.addColorStop(1, '#7C3AED');
          g.fillStyle = kg; rrect(g, gl.kx - KW / 2, gl.ky - KH / 2, KW, KH, 7); g.fill();
          g.fillStyle = '#FFE27A';
          g.beginPath(); g.arc(gl.kx - KW / 2, gl.ky, 6, 0, TAU); g.arc(gl.kx + KW / 2, gl.ky, 6, 0, TAU); g.fill();
          g.fillStyle = '#EDE9FE'; g.beginPath(); g.arc(gl.kx, gl.ky, 7, 0, TAU); g.fill();
        }
        // shooters + aim
        for (const s of Sh) {
          const col = COLORS[s.p.slot];
          if (!s.ball && s.cd <= 0) {
            g.strokeStyle = col.light; g.lineWidth = 3; g.setLineDash([2, 8]); g.lineCap = 'round';
            const L = Math.min(S * 0.42, Math.abs((s.goal.line - s.y) / Math.sin(s.aim)) * 0.6);
            g.beginPath(); g.moveTo(s.x, s.y); g.lineTo(s.x + Math.cos(s.aim) * L, s.y + Math.sin(s.aim) * L); g.stroke();
            g.setLineDash([]);
            g.fillStyle = col.light;
            g.beginPath(); g.arc(s.x + Math.cos(s.aim) * L, s.y + Math.sin(s.aim) * L, 5, 0, TAU); g.fill();
            // waiting ball at feet
            const bx = s.x + Math.cos(s.aim) * S * 0.075, by = s.y + Math.sin(s.aim) * S * 0.075;
            g.fillStyle = '#fff'; g.beginPath(); g.arc(bx, by, BR, 0, TAU); g.fill();
          }
          drawBlob(g, s.x - Math.cos(s.aim) * s.kick * 6, s.y - Math.sin(s.aim) * s.kick * 6, S * 0.055, col, { ang: s.aim, squash: s.kick * 0.12 });
        }
        for (const b of balls) {
          g.globalAlpha = b.res ? Math.max(0, 1 - b.t / 0.5) : 1;
          g.fillStyle = 'rgba(0,0,0,.3)'; g.beginPath(); g.ellipse(b.x + 2, b.y + BR * 0.6, BR, BR * 0.6, 0, 0, TAU); g.fill();
          g.save(); g.translate(b.x, b.y); g.rotate(b.roll);
          g.fillStyle = '#fff'; g.beginPath(); g.arc(0, 0, BR, 0, TAU); g.fill();
          g.fillStyle = '#1B1F3B'; g.beginPath(); g.arc(0, 0, BR * 0.35, 0, TAU); g.fill();
          for (let k = 0; k < 5; k++) { const a = (k / 5) * TAU; g.beginPath(); g.arc(Math.cos(a) * BR * 0.78, Math.sin(a) * BR * 0.78, BR * 0.18, 0, TAU); g.fill(); }
          g.restore(); g.globalAlpha = 1;
        }
        for (const s of Sh) ctx.hudPips(s.p, score.get(s.p), GOAL);
      },
    };
  },
});
