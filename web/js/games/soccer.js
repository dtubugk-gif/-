'use strict';
/* Soccer Clash — bottom team vs top team. Spin, hold to run, release next to the ball to kick. */
Games.push({
  id: 'soccer', name: 'Soccer Clash', control: 'HOLD + RELEASE',
  desc: 'Bottom team vs top team! You spin. Hold to run, let go next to the ball to kick. Score a goal to win the round!',
  color: '#22C55E', grad: ['#4ADE80', '#15803D'], gradDark: '#14532D',
  icon: `<svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="22" fill="#fff"/><path d="M32 22l8 6-3 9h-10l-3-9z" fill="#15803D"/><path d="M32 10v12M40 28l11-4M37 37l7 9M27 37l-7 9M24 28l-11-4" stroke="#15803D" stroke-width="3"/></svg>`,

  create(ctx) {
    const { players, S, arena: A, cx, cy } = ctx;
    const m = 12;
    const F = { x: A.x + m, y: A.y + m, w: A.w - m * 2, h: A.h - m * 2 };
    const GW = F.w * 0.5, GD = 11, GOAL = 1, LIMIT = 50, CR = S * 0.09;
    const PR = S * 0.055, BR = S * 0.03;
    const teamOf = (p) => (p.lay.side === 'bottom' ? 0 : 1);
    const size = [0, 0];
    players.forEach((p) => size[teamOf(p)]++);
    const P = players.map((p) => {
      const team = teamOf(p);
      return { p, team, x: 0, y: 0, vx: 0, vy: 0, ang: 0, boost: size[team] < size[1 - team] ? 1.3 : 1, kickCd: 0, think: 0, want: false, squash: 0 };
    });
    const ball = { x: cx, y: cy, vx: 0, vy: 0, roll: 0 };
    const score = [0, 0];
    let pause = 0, goalTeam = -1, stuckT = 0, ref = { x: cx, y: cy };
    const attackY = (team) => (team === 0 ? F.y : F.y + F.h);
    const ownY = (team) => (team === 0 ? F.y + F.h : F.y);
    const teamColor = (team) => COLORS[(P.find((q) => q.team === team) || P[0]).p.slot];

    function reset() {
      for (const q of P) {
        const h = q.p.lay.home;
        q.x = h.x; q.y = lerp(h.y, cy, 0.15); q.vx = q.vy = 0;
        q.ang = q.team === 0 ? -Math.PI / 2 : Math.PI / 2;
      }
      ball.x = cx; ball.y = cy; ball.vx = rand(-20, 20); ball.vy = 0;
    }
    reset();

    function kick(q) {
      if (q.kickCd > 0) return;
      const d = dist(q.x, q.y, ball.x, ball.y);
      if (d > PR + BR + 16) return;
      const toBall = Math.atan2(ball.y - q.y, ball.x - q.x);
      if (Math.abs(angNorm(toBall - q.ang)) > 1.0) return;
      const pw = S * 1.4;
      ball.vx = Math.cos(q.ang) * pw + q.vx * 0.3; ball.vy = Math.sin(q.ang) * pw + q.vy * 0.3;
      q.kickCd = 0.35;
      Sfx.tone(140, 0.12, { type: 'sine', vol: 0.4, slide: 0.5 }); Sfx.noise(0.06, { freq: 2500, vol: 0.2 });
      FX.burst(ball.x, ball.y, '#fff', 10, 220, 3, 0.35); FX.addShake(3);
    }

    function ai(q, dt) {
      q.think -= dt;
      if (q.think > 0) return;
      q.think = rand(0.04, 0.09);
      const mates = P.filter((o) => o.team === q.team);
      const attacker = mates.reduce((a, b) => (dist(a.x, a.y, ball.x, ball.y) < dist(b.x, b.y, ball.x, ball.y) ? a : b));
      let tx, ty;
      const gy = attackY(q.team);
      if (q === attacker) {
        const dx = cx - ball.x, dy = gy - ball.y, L = Math.hypot(dx, dy) || 1;
        const bx = ball.x - (dx / L) * (PR + BR) * 1.25, by = ball.y - (dy / L) * (PR + BR) * 1.25;
        if (dist(q.x, q.y, bx, by) < PR * 0.9) { tx = ball.x + ball.vx * 0.1; ty = ball.y + ball.vy * 0.1; } else { tx = bx; ty = by; }
      } else {
        tx = lerp(cx, ball.x, 0.6); ty = lerp(ownY(q.team), ball.y, 0.32);
      }
      tx = clamp(tx, F.x + PR, F.x + F.w - PR); ty = clamp(ty, F.y + PR, F.y + F.h - PR);
      const want = Math.atan2(ty - q.y, tx - q.x);
      const diff = Math.abs(angNorm(want - q.ang));
      const near = dist(q.x, q.y, ball.x, ball.y) < PR + BR + 14;
      const toGoal = Math.abs(angNorm(Math.atan2(gy - q.y, cx - q.x) - q.ang));
      const toOwn = Math.abs(angNorm(Math.atan2(ownY(q.team) - q.y, cx - q.x) - q.ang));
      const ballIn = Math.abs(angNorm(Math.atan2(ball.y - q.y, ball.x - q.x) - q.ang)) < 0.9;
      if (q.p.down && near && ballIn && q.kickCd <= 0 && (toGoal < 1.0 || toOwn > 2.0)) { q.want = false; return; }
      const far = dist(q.x, q.y, tx, ty);
      q.want = far > PR * 0.6 && diff < (far > PR * 2.5 ? 0.35 : 0.7);
    }

    function goal(team) {
      score[team]++; goalTeam = team; pause = 1.7;
      const gy = attackY(team);
      Sfx.coin(); Sfx.noise(0.6, { freq: 1500, vol: 0.25, slide: 0.5 }); ctx.buzz([40, 40, 40]);
      for (let i = 0; i < 3; i++) FX.burst(cx + rand(-GW / 2, GW / 2), gy, pick(['#fff', teamColor(team).main, '#FFE27A']), 18, 340, 5, 1, 200);
      FX.addShake(8);
      if (score[team] >= GOAL) {
        ctx.endTeam(P.filter((q) => q.team === team).map((q) => q.p), P.filter((q) => q.team !== team).map((q) => q.p));
      }
    }

    return {
      where(p) { const q = P.find((x) => x.p === p); return { x: q.x, y: q.y, r: PR }; },
      update(dt) {
        if (pause > 0) {
          pause -= dt;
          ball.x += ball.vx * dt * 0.3; ball.y += ball.vy * dt * 0.3; ball.vx *= 0.9; ball.vy *= 0.9;
          if (pause <= 0 && !ctx.over) reset();
          return;
        }
        for (const q of P) {
          const p = q.p;
          q.kickCd -= dt; q.squash = Math.max(0, q.squash - dt * 2);
          if (!p.human && !ctx.over) { ai(q, dt); ctx.botHold(p, q.want); }
          if (p.released) kick(q);
          const maxv = S * 0.8 * q.boost;
          Move.aim(q, p, dt, 2.7);
          Move.drive(q, p, dt, S * 0.75 * q.boost, 9, 7);
          const sp = Math.hypot(q.vx, q.vy);
          if (sp > maxv) { q.vx *= maxv / sp; q.vy *= maxv / sp; }
          q.x = clamp(q.x + q.vx * dt, F.x + PR, F.x + F.w - PR);
          q.y = clamp(q.y + q.vy * dt, F.y + PR, F.y + F.h - PR);
        }
        for (let i = 0; i < P.length; i++) for (let j = i + 1; j < P.length; j++) {
          const a = P[i], b = P[j], d = dist(a.x, a.y, b.x, b.y);
          if (d < PR * 2 && d > 0) {
            const nx = (b.x - a.x) / d, ny = (b.y - a.y) / d, o = (PR * 2 - d) / 2;
            a.x -= nx * o; a.y -= ny * o; b.x += nx * o; b.y += ny * o;
            const vr = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny;
            if (vr > 0) { a.vx -= vr * nx; a.vy -= vr * ny; b.vx += vr * nx; b.vy += vr * ny; if (vr > S * 0.3) { a.squash = b.squash = 0.15; Sfx.bounce(); } }
          }
        }
        // player ↔ ball (dribbling)
        for (const q of P) {
          const d = dist(q.x, q.y, ball.x, ball.y);
          if (d < PR + BR && d > 0) {
            const nx = (ball.x - q.x) / d, ny = (ball.y - q.y) / d;
            ball.x = q.x + nx * (PR + BR); ball.y = q.y + ny * (PR + BR);
            const vr = (q.vx - ball.vx) * nx + (q.vy - ball.vy) * ny;
            if (vr > 0) { ball.vx += nx * vr * 1.35; ball.vy += ny * vr * 1.35; }
          }
        }
        const bf = Math.exp(-0.9 * dt); ball.vx *= bf; ball.vy *= bf;
        const bs = Math.hypot(ball.vx, ball.vy), cap = S * 1.7;
        if (bs > cap) { ball.vx *= cap / bs; ball.vy *= cap / bs; }
        ball.x += ball.vx * dt; ball.y += ball.vy * dt;
        ball.roll += bs * dt / BR;
        if (ball.x < F.x + BR) { ball.x = F.x + BR; ball.vx = Math.abs(ball.vx) * 0.8; Sfx.bounce(); }
        if (ball.x > F.x + F.w - BR) { ball.x = F.x + F.w - BR; ball.vx = -Math.abs(ball.vx) * 0.8; Sfx.bounce(); }
        const inMouth = ball.x > cx - GW / 2 + BR && ball.x < cx + GW / 2 - BR;
        if (ball.y < F.y + BR) {
          if (inMouth) { if (ball.y < F.y - BR * 0.3) goal(0); }
          else { ball.y = F.y + BR; ball.vy = Math.abs(ball.vy) * 0.8; Sfx.bounce(); }
        }
        if (ball.y > F.y + F.h - BR) {
          if (inMouth) { if (ball.y > F.y + F.h + BR * 0.3) goal(1); }
          else { ball.y = F.y + F.h - BR; ball.vy = -Math.abs(ball.vy) * 0.8; Sfx.bounce(); }
        }
        // angled corners keep the ball from getting trapped
        for (const [kx, ky] of [[F.x, F.y], [F.x + F.w, F.y], [F.x, F.y + F.h], [F.x + F.w, F.y + F.h]]) {
          const sx = kx === F.x ? 1 : -1, sy = ky === F.y ? 1 : -1;
          const depth = CR - ((ball.x - kx) * sx + (ball.y - ky) * sy) / Math.SQRT2 - BR;
          if (depth > 0) {
            const nx = sx / Math.SQRT2, ny = sy / Math.SQRT2;
            ball.x += nx * depth; ball.y += ny * depth;
            const vn = ball.vx * nx + ball.vy * ny;
            if (vn < 0) { ball.vx -= 1.8 * vn * nx; ball.vy -= 1.8 * vn * ny; }
          }
        }
        // ball pinned or idle in one spot for too long: pop it back into play
        if (dist(ball.x, ball.y, ref.x, ref.y) < PR * 0.7) stuckT += dt; else { stuckT = 0; ref = { x: ball.x, y: ball.y }; }
        if (stuckT > 1.8) {
          stuckT = 0;
          for (const q of P) if (dist(q.x, q.y, ball.x, ball.y) < PR * 3) { const a = Math.atan2(q.y - ball.y, q.x - ball.x); q.vx += Math.cos(a) * S * 0.6; q.vy += Math.sin(a) * S * 0.6; }
          const a = Math.atan2(cy - ball.y, cx - ball.x) + rand(-0.4, 0.4);
          ball.vx = Math.cos(a) * S * 0.6; ball.vy = Math.sin(a) * S * 0.6;
          FX.ring(ball.x, ball.y, '#fff', 30, 0.3, 3);
        }
        if (ctx.t > LIMIT && !ctx.over) {
          const t0 = P.filter((q) => q.team === 0).map((q) => q.p), t1 = P.filter((q) => q.team === 1).map((q) => q.p);
          if (score[0] === score[1]) ctx.end([...t0, ...t1], true);
          else if (score[0] > score[1]) ctx.endTeam(t0, t1); else ctx.endTeam(t1, t0);
        }
        if (bs > S * 0.9 && Math.random() < 0.5) FX.trail(ball.x, ball.y, 'rgba(255,255,255,.6)', 2.5, 0.25);
      },

      draw(g) {
        // pitch
        g.fillStyle = '#145A33'; g.fillRect(A.x, A.y, A.w, A.h);
        const bands = 10, bh = F.h / bands;
        for (let i = 0; i < bands; i++) { g.fillStyle = i % 2 ? '#1B7543' : '#1E8049'; g.fillRect(F.x, F.y + i * bh, F.w, bh + 1); }
        g.strokeStyle = 'rgba(255,255,255,.75)'; g.lineWidth = 3;
        g.strokeRect(F.x, F.y, F.w, F.h);
        g.beginPath(); g.moveTo(F.x, cy); g.lineTo(F.x + F.w, cy); g.stroke();
        g.beginPath(); g.arc(cx, cy, S * 0.13, 0, TAU); g.stroke();
        g.fillStyle = '#fff'; g.beginPath(); g.arc(cx, cy, 4, 0, TAU); g.fill();
        g.fillStyle = '#145A33';
        for (const [kx, ky, sx, sy] of [[F.x, F.y, 1, 1], [F.x + F.w, F.y, -1, 1], [F.x, F.y + F.h, 1, -1], [F.x + F.w, F.y + F.h, -1, -1]]) {
          const d = CR * Math.SQRT2;
          g.beginPath(); g.moveTo(kx, ky); g.lineTo(kx + sx * d, ky); g.lineTo(kx, ky + sy * d); g.closePath(); g.fill();
          g.beginPath(); g.moveTo(kx + sx * d, ky); g.lineTo(kx, ky + sy * d); g.stroke();
        }
        const pbw = Math.min(F.w * 0.8, GW * 1.5), pbh = F.h * 0.12;
        g.strokeRect(cx - pbw / 2, F.y, pbw, pbh); g.strokeRect(cx - pbw / 2, F.y + F.h - pbh, pbw, pbh);
        g.beginPath(); g.arc(cx, F.y + pbh, S * 0.07, 0, Math.PI); g.stroke();
        g.beginPath(); g.arc(cx, F.y + F.h - pbh, S * 0.07, Math.PI, TAU); g.stroke();
        // score (each half shows its own team first)
        ctx.mirror((g, flipped) => {
          const my = flipped ? 1 : 0;
          g.globalAlpha = 0.22;
          text(g, `${score[my]} : ${score[1 - my]}`, 0, F.h * 0.25, S * 0.16, '#fff', { shadow: false });
          g.globalAlpha = 0.5;
          text(g, `${Math.max(0, Math.ceil(LIMIT - ctx.t))}s`, 0, F.h * 0.25 + S * 0.12, 14, '#fff', { shadow: false });
          g.globalAlpha = 1;
        });
        // goals
        for (const top of [true, false]) {
          const y = top ? F.y - GD : F.y + F.h;
          g.fillStyle = 'rgba(255,255,255,.12)'; g.fillRect(cx - GW / 2, y, GW, GD);
          g.strokeStyle = 'rgba(255,255,255,.35)'; g.lineWidth = 1;
          for (let x = cx - GW / 2; x <= cx + GW / 2; x += 8) { g.beginPath(); g.moveTo(x, y); g.lineTo(x, y + GD); g.stroke(); }
          g.strokeStyle = '#fff'; g.lineWidth = 5; g.lineCap = 'round';
          g.beginPath(); g.moveTo(cx - GW / 2, top ? F.y : F.y + F.h); g.lineTo(cx - GW / 2, y + (top ? 0 : GD)); g.stroke();
          g.beginPath(); g.moveTo(cx + GW / 2, top ? F.y : F.y + F.h); g.lineTo(cx + GW / 2, y + (top ? 0 : GD)); g.stroke();
        }
        // players
        for (const q of P) {
          const col = COLORS[q.p.slot];
          drawAim(g, q.x, q.y, PR, q.ang, q.spinDir, col.light, q.p.down);
          drawBlob(g, q.x, q.y, PR, col, { ang: q.ang, squash: q.squash });
        }
        // ball
        g.fillStyle = 'rgba(0,0,0,.3)'; g.beginPath(); g.ellipse(ball.x + 2, ball.y + BR * 0.6, BR, BR * 0.6, 0, 0, TAU); g.fill();
        g.save(); g.translate(ball.x, ball.y); g.rotate(ball.roll);
        g.fillStyle = '#fff'; g.beginPath(); g.arc(0, 0, BR, 0, TAU); g.fill();
        g.fillStyle = '#1B1F3B';
        g.beginPath(); g.arc(0, 0, BR * 0.32, 0, TAU); g.fill();
        for (let k = 0; k < 5; k++) { const a = (k / 5) * TAU; g.beginPath(); g.arc(Math.cos(a) * BR * 0.78, Math.sin(a) * BR * 0.78, BR * 0.18, 0, TAU); g.fill(); }
        g.restore();
        if (pause > 0 && goalTeam >= 0) {
          const k = easeOutBack(Math.min(1, (1.7 - pause) / 0.4));
          ctx.mirror((g) => { g.save(); g.translate(0, F.h * 0.12); g.scale(k, k); text(g, 'GOAL!', 0, 0, S * 0.14, teamColor(goalTeam).main); g.restore(); });
        }
      },
    };
  },
});
