'use strict';
/* Quick Draw — wait for the orb to flash bright, then be the fastest finger.
   Tapping too early, or while the orb is PURPLE, costs you a point. */
Games.push({
  id: 'reflex', name: 'Quick Draw', control: 'TAP',
  desc: 'Tap first when the orb turns WHITE: +1. Tap too early or on PURPLE: -1. First to 3!',
  color: '#00C2FF', grad: ['#22D3EE', '#3B82F6'], gradDark: '#1E4FB3',
  icon: `<svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="24" fill="#fff" opacity=".25"/><circle cx="32" cy="32" r="17" fill="#fff"/><path d="M35 14L22 35h9l-3 15 14-22h-9z" fill="#3B82F6"/></svg>`,

  create(ctx) {
    const { players, S, cx, cy } = ctx;
    const GOAL = 3;
    const score = new Map(players.map((p) => [p, 0]));
    const locked = new Set();
    const hurt = new Map(players.map((p) => [p, 0]));
    let state, t, delay, fakes, fakeT = 0, fooled = new Map(), react, roundWinner, pulse = 0, flash = 0, shake = 0;
    const R = S * 0.2;

    function newRound() {
      state = 'wait'; t = 0; delay = rand(1.8, 4.6);
      // 0–2 purple fake-outs per round, never overlapping the real signal
      const n = Math.random() < 0.35 ? 0 : Math.random() < 0.6 ? 1 : 2;
      fakes = [];
      for (let i = 0; i < n; i++) {
        const at = rand(0.7, delay - 0.9);
        if (fakes.every((f) => Math.abs(f - at) > 0.9)) fakes.push(at);
      }
      fakes.sort((a, b) => a - b);
      locked.clear(); roundWinner = null;
      react = new Map(players.map((p) => [p, rand(0.25, 0.55)]));
    }
    newRound();

    function penalty(p, why) {
      if (locked.has(p)) return;
      locked.add(p);
      score.set(p, score.get(p) - 1);
      hurt.set(p, 1);
      Sfx.wrong(); ctx.buzz(90);
      const h = p.lay.home;
      FX.float(h.x, h.y, '-1', '#FF6B6B', p.lay.rot, 38);
      FX.float(h.x, h.y + (p.lay.side === 'bottom' ? 34 : -34), why, '#FF8FA3', p.lay.rot, 18);
      if (locked.size === players.length) { state = 'void'; t = 0; }
    }

    return {
      label(p) { return locked.has(p) ? 'WAIT...' : 'TAP'; },
      where(p) { return { x: p.lay.home.x, y: p.lay.home.y, r: 18 }; },
      update(dt) {
        t += dt; pulse += dt; flash = Math.max(0, flash - dt * 3); shake = Math.max(0, shake - dt * 4);
        for (const p of players) hurt.set(p, Math.max(0, hurt.get(p) - dt * 2));
        if (state === 'wait' || state === 'fake') {
          if (state === 'wait' && fakes.length && t >= fakes[0]) {
            fakes.shift();
            state = 'fake'; fakeT = 0; shake = 1;
            Sfx.tone(300, 0.14, { type: 'square', vol: 0.07 });
            fooled = new Map(players.map((p) => [p, Math.random() < 0.13 ? rand(0.2, 0.4) : -1]));
          }
          if (state === 'fake') {
            fakeT += dt;
            for (const p of players) {
              const f = fooled.get(p);
              if (!p.human && !locked.has(p) && f > 0 && fakeT >= f) ctx.botTap(p);
            }
          }
          for (const p of players) if (p.pressed) penalty(p, state === 'fake' ? 'PURPLE!' : 'TOO SOON!');
          if (state === 'fake' && fakeT > 0.6) state = 'wait';
          if (state === 'wait' && t >= delay) { state = 'go'; t = 0; flash = 1; Sfx.go(); FX.ring(cx, cy, '#fff', R * 2.2, 0.5, 10); }
        } else if (state === 'go') {
          for (const p of players) {
            if (locked.has(p)) continue;
            if (!p.human && t >= react.get(p)) ctx.botTap(p);
          }
          const tappers = players.filter((p) => p.pressed && !locked.has(p));
          if (tappers.length) {
            const p = pick(tappers);
            roundWinner = p; score.set(p, score.get(p) + 1);
            state = 'scored'; t = 0;
            Sfx.coin(); ctx.buzz(40);
            FX.burst(cx, cy, COLORS[p.slot].main, 30, 360, 6, 0.8);
            FX.float(p.lay.home.x, p.lay.home.y, '+1', COLORS[p.slot].light, p.lay.rot, 38);
            if (score.get(p) >= GOAL) ctx.end([...players].sort((x, y) => score.get(y) - score.get(x)));
          } else if (t > 2.5) { state = 'void'; t = 0; }
        } else if (state === 'scored' || state === 'void') {
          if (t > 1.2 && !ctx.over) newRound();
        }
      },

      draw(g) {
        // red flash behind players who just lost a point
        for (const p of players) {
          const h = hurt.get(p);
          if (h <= 0) continue;
          const grd = g.createRadialGradient(p.lay.home.x, p.lay.home.y, 0, p.lay.home.x, p.lay.home.y, S * 0.45);
          grd.addColorStop(0, `rgba(255,60,90,${0.35 * h})`); grd.addColorStop(1, 'rgba(255,60,90,0)');
          g.fillStyle = grd; g.fillRect(p.lay.home.x - S * 0.45, p.lay.home.y - S * 0.45, S * 0.9, S * 0.9);
        }
        let fill = '#252B55', ring = 'rgba(255,255,255,.12)', label = 'WAIT', lc = '#8E95C2';
        if (state === 'fake') { fill = '#9D4EDD'; ring = '#C77DFF'; label = "DON'T!"; lc = '#fff'; }
        if (state === 'go') { fill = '#F8FDFF'; ring = '#7DF9FF'; label = 'TAP!'; lc = '#1E4FB3'; }
        if (state === 'scored' && roundWinner) { fill = COLORS[roundWinner.slot].main; ring = COLORS[roundWinner.slot].light; label = COLORS[roundWinner.slot].name.toUpperCase(); lc = '#fff'; }
        if (state === 'void') { label = 'AGAIN'; }
        const pr = R * (1 + (state === 'wait' ? Math.sin(pulse * 4) * 0.025 : 0) + flash * 0.15);
        const ox = shake > 0 ? Math.sin(pulse * 70) * shake * 6 : 0;
        g.save(); g.translate(ox, 0);
        if (state === 'go') { g.shadowColor = '#7DF9FF'; g.shadowBlur = 50; }
        if (state === 'fake') { g.shadowColor = '#C77DFF'; g.shadowBlur = 40; }
        g.fillStyle = 'rgba(0,0,0,.3)'; g.beginPath(); g.arc(cx, cy + 8, pr, 0, TAU); g.fill();
        g.fillStyle = fill; g.beginPath(); g.arc(cx, cy, pr, 0, TAU); g.fill();
        g.shadowBlur = 0;
        g.strokeStyle = ring; g.lineWidth = 6;
        g.beginPath(); g.arc(cx, cy, pr + 12, 0, TAU); g.stroke();
        if (state === 'wait') {
          g.strokeStyle = 'rgba(255,255,255,.35)'; g.lineWidth = 6; g.lineCap = 'round';
          g.beginPath(); g.arc(cx, cy, pr + 12, pulse * 3, pulse * 3 + 0.9); g.stroke();
        }
        g.fillStyle = 'rgba(255,255,255,.18)'; g.beginPath(); g.ellipse(cx - pr * 0.35, cy - pr * 0.45, pr * 0.3, pr * 0.16, -0.6, 0, TAU); g.fill();
        g.restore();
        ctx.mirror((g) => text(g, label, 0, pr * 0.42, Math.min(pr * 0.32, 32), lc, { shadow: state !== 'go' }));
        // rules reminder under the orb
        ctx.mirror((g) => text(g, 'WHITE = +1   PURPLE = -1', 0, pr + 40, 13, 'rgba(255,255,255,.35)', { shadow: false, weight: 600 }));
        for (const p of players) {
          const sc = score.get(p);
          ctx.hudPips(p, Math.max(0, sc), GOAL);
          if (sc < 0) ctx.hudText(p, String(sc), '#FF6B6B', -40, 20);
          else if (locked.has(p)) ctx.hudText(p, 'LOCKED', '#FF6B6B', -40, 14);
        }
      },
    };
  },
});
