'use strict';
/* Reflex — wait for the orb to light up, then be the fastest finger. */
Games.push({
  id: 'reflex', name: 'Quick Draw', control: 'TAP',
  desc: 'Wait for the orb to flash bright. Tap first to score. Tap too early and you sit out!',
  color: '#00C2FF', grad: ['#22D3EE', '#3B82F6'], gradDark: '#1E4FB3',
  icon: `<svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="24" fill="#fff" opacity=".25"/><circle cx="32" cy="32" r="17" fill="#fff"/><path d="M35 14L22 35h9l-3 15 14-22h-9z" fill="#3B82F6"/></svg>`,

  create(ctx) {
    const { players, S, cx, cy } = ctx;
    const GOAL = 3;
    const score = new Map(players.map((p) => [p, 0]));
    const locked = new Set();
    let state, t, delay, fakeAt, fakeT = 0, fooled = new Map(), react, roundWinner, pulse = 0, flash = 0;
    const R = S * 0.2;

    function newRound() {
      state = 'wait'; t = 0; delay = rand(1.6, 4.2);
      fakeAt = Math.random() < 0.55 ? rand(0.6, delay - 0.8) : -1;
      locked.clear(); roundWinner = null;
      react = new Map(players.map((p) => [p, rand(0.26, 0.55)]));
    }
    newRound();

    function early(p) {
      if (locked.has(p)) return;
      locked.add(p); Sfx.wrong(); ctx.buzz(80);
      const h = p.lay.home; FX.float(h.x, h.y, 'TOO SOON!', '#FF6B6B', p.lay.rot, 22);
      if (locked.size === players.length) { state = 'void'; t = 0; }
    }

    return {
      label(p) { return locked.has(p) ? 'WAIT...' : 'TAP'; },
      update(dt) {
        t += dt; pulse += dt; flash = Math.max(0, flash - dt * 3);
        if (state === 'wait' || state === 'fake') {
          if (state === 'wait' && fakeAt > 0 && t >= fakeAt) {
            state = 'fake'; fakeT = 0; fakeAt = -1;
            Sfx.tone(300, 0.12, { type: 'square', vol: 0.06 });
            fooled = new Map(players.map((p) => [p, Math.random() < 0.12 ? rand(0.2, 0.4) : -1]));
          }
          if (state === 'fake') {
            fakeT += dt;
            for (const p of players) {
              const f = fooled.get(p);
              if (!p.human && !locked.has(p) && f > 0 && fakeT >= f) ctx.botTap(p);
            }
            if (fakeT > 0.55) state = 'wait';
          }
          for (const p of players) if (p.pressed) early(p);
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
            FX.float(p.lay.home.x, p.lay.home.y, '+1', COLORS[p.slot].light, p.lay.rot, 34);
            if (score.get(p) >= GOAL) {
              ctx.end([...players].sort((x, y) => score.get(y) - score.get(x)));
            }
          } else if (t > 2.5) { state = 'void'; t = 0; }
        } else if (state === 'scored' || state === 'void') {
          if (t > 1.3 && !ctx.over) newRound();
        }
      },

      draw(g) {
        // orb
        let fill = '#252B55', ring = 'rgba(255,255,255,.12)', label = 'WAIT', lc = '#8E95C2';
        if (state === 'fake') { fill = '#B5179E'; ring = '#F72585'; label = 'NOPE'; lc = '#fff'; }
        if (state === 'go') { fill = '#F8FDFF'; ring = '#7DF9FF'; label = 'TAP!'; lc = '#1E4FB3'; }
        if (state === 'scored' && roundWinner) { fill = COLORS[roundWinner.slot].main; ring = COLORS[roundWinner.slot].light; label = COLORS[roundWinner.slot].name.toUpperCase(); lc = '#fff'; }
        if (state === 'void') { label = 'AGAIN'; }
        const pr = R * (1 + (state === 'wait' ? Math.sin(pulse * 4) * 0.025 : 0) + flash * 0.15);
        if (state === 'go') { g.shadowColor = '#7DF9FF'; g.shadowBlur = 50; }
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
        // labels mirrored inside orb
        ctx.mirror((g) => text(g, label, 0, pr * 0.42, Math.min(pr * 0.34, 34), lc, { shadow: state !== 'go' }));
        // score pips by each player
        for (const p of players) {
          ctx.atPlayer(p, (g) => {
            const s = 9, gap = 8, tw = GOAL * s * 2 + (GOAL - 1) * gap;
            for (let i = 0; i < GOAL; i++) {
              const x = -tw / 2 + s + i * (s * 2 + gap);
              g.fillStyle = i < score.get(p) ? COLORS[p.slot].main : 'rgba(255,255,255,.1)';
              g.beginPath(); g.arc(x, -14, s, 0, TAU); g.fill();
              if (i < score.get(p)) { g.fillStyle = 'rgba(255,255,255,.4)'; g.beginPath(); g.arc(x - 3, -17, 3, 0, TAU); g.fill(); }
            }
            if (locked.has(p)) text(g, 'TOO SOON', 0, -40, 15, '#FF6B6B', { shadow: false });
          });
        }
      },
    };
  },
});
