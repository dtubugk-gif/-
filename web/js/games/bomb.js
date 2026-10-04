'use strict';
/* Hot Bomb — pass the ticking bomb before it blows up in your hands. */
Games.push({
  id: 'bomb', name: 'Hot Bomb', control: 'TAP TO PASS',
  desc: 'Got the bomb? Tap to throw it away fast! Whoever holds it when it explodes is out.',
  color: '#FF4D6D', grad: ['#FB7185', '#E11D48'], gradDark: '#9F1239',
  icon: `<svg viewBox="0 0 64 64"><circle cx="28" cy="38" r="20" fill="#fff"/><rect x="33" y="14" width="10" height="9" rx="2" fill="#fff" transform="rotate(35 38 18)"/><path d="M42 14q4-8 12-6" stroke="#fff" stroke-width="3" fill="none" stroke-linecap="round"/><path d="M55 4l1 4 4 1-4 1-1 4-1-4-4-1 4-1z" fill="#FFE27A"/><circle cx="21" cy="31" r="5" fill="#E11D48" opacity=".35"/></svg>`,

  create(ctx) {
    const { players, S, cx, cy } = ctx;
    const BR = S * 0.075, PR = S * 0.085;
    let holder, fuse, fuseMax, flight = null, pause = 0, botT = 0, boomAt = null, spin = 0;
    const seat = (p) => ({ x: lerp(p.lay.home.x, cx, 0.08), y: lerp(p.lay.home.y, cy, 0.08) });
    const bombSpot = (p) => { const s = seat(p); return { x: lerp(s.x, cx, 0.3), y: lerp(s.y, cy, 0.3) }; };

    function newRound() {
      const alive = ctx.alive();
      holder = pick(alive);
      fuseMax = fuse = rand(2.5, 5.5);
      flight = null; botT = rand(0.25, 0.9); boomAt = null;
    }
    newRound();

    function bombPos() {
      if (!flight) return bombSpot(holder);
      const a = bombSpot(flight.from), b = bombSpot(flight.to), t = easeOutCubic(flight.t);
      const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
      const dx = b.x - a.x, dy = b.y - a.y, L = Math.hypot(dx, dy) || 1;
      const h = Math.sin(t * Math.PI) * L * 0.25;
      return { x: lerp(a.x, b.x, t) - (dy / L) * h, y: lerp(a.y, b.y, t) + (dx / L) * h, mx, my };
    }

    return {
      where(p) { const s = seat(p); return { x: s.x, y: s.y, r: PR }; },
      label(p) { return p === holder && !flight && pause <= 0 ? 'PASS!' : 'WAIT'; },
      update(dt) {
        spin += dt;
        if (pause > 0) { pause -= dt; if (pause <= 0 && !ctx.over) newRound(); return; }
        if (flight) {
          flight.t += dt / 0.38;
          spin += dt * 10;
          if (flight.t >= 1) { holder = flight.to; flight = null; botT = rand(0.22, 0.85); Sfx.tone(300, 0.08, { type: 'triangle', vol: 0.15 }); }
          return;
        }
        fuse -= dt;
        const bp = bombSpot(holder);
        if (Math.random() < 0.6) FX.trail(bp.x + BR * 0.55, bp.y - BR * 1.05, pick(['#FFE27A', '#FF9F1C', '#fff']), rand(2, 3.5), 0.35, rand(-40, 40), -60);
        if (Sfx.throttle('tick', 120 + 500 * (fuse / fuseMax))) Sfx.tone(1200, 0.03, { type: 'square', vol: 0.04 });
        if (!holder.human && !ctx.over) { botT -= dt; if (botT <= 0) ctx.botTap(holder); }
        if (holder.pressed) {
          const others = ctx.alive().filter((p) => p !== holder);
          if (others.length) { flight = { from: holder, to: pick(others), t: 0 }; Sfx.whoosh(); }
          return;
        }
        if (fuse <= 0) {
          boomAt = bp;
          Sfx.boom(); ctx.buzz([80, 40, 120]); FX.addShake(16);
          FX.burst(bp.x, bp.y, '#FFB703', 40, 520, 8, 1); FX.burst(bp.x, bp.y, '#FF4D6D', 30, 420, 7, 0.9); FX.burst(bp.x, bp.y, '#fff', 16, 300, 5, 0.6);
          FX.ring(bp.x, bp.y, '#FFE27A', S * 0.5, 0.6, 14);
          const victim = holder;
          pause = 1.6;
          ctx.eliminate([victim]);
        }
      },

      draw(g) {
        // table
        g.fillStyle = 'rgba(255,255,255,.03)';
        g.beginPath(); g.arc(cx, cy, S * 0.3, 0, TAU); g.fill();
        g.strokeStyle = 'rgba(255,255,255,.06)'; g.lineWidth = 2; g.setLineDash([4, 10]);
        g.beginPath(); g.arc(cx, cy, S * 0.3, 0, TAU); g.stroke(); g.setLineDash([]);
        const danger = pause <= 0 ? 1 - fuse / fuseMax : 0;
        const bp = pause > 0 ? null : bombPos();
        for (const p of players) {
          const s = seat(p);
          const look = bp ? Math.atan2(bp.y - s.y, bp.x - s.x) : p.lay.dir + Math.PI;
          let jx = 0, jy = 0;
          if (p === holder && !flight && pause <= 0) { const k = 1 + danger * 4; jx = rand(-k, k); jy = rand(-k, k); }
          g.globalAlpha = p.out ? 0.35 : 1;
          drawBlob(g, s.x + jx, s.y + jy, PR, p.out ? { main: '#555B85', dark: '#3A3F66', light: '#7A80AD' } : COLORS[p.slot], { ang: look, dead: p.out });
          g.globalAlpha = 1;
        }
        if (!bp) return;
        const pulse = 1 + Math.sin(spin * (6 + danger * 22)) * (0.04 + danger * 0.07);
        const r = BR * pulse;
        g.save(); g.translate(bp.x, bp.y);
        g.fillStyle = 'rgba(0,0,0,.3)'; g.beginPath(); g.ellipse(0, r * 0.9, r * 0.9, r * 0.35, 0, 0, TAU); g.fill();
        g.rotate(flight ? spin * 2 : Math.sin(spin * 3) * 0.08);
        const hot = danger > 0.7 && Math.sin(spin * 30) > 0;
        const grd = g.createRadialGradient(-r * 0.35, -r * 0.35, r * 0.1, 0, 0, r);
        grd.addColorStop(0, hot ? '#FF8FA3' : '#7A82B8'); grd.addColorStop(1, hot ? '#C9184A' : '#2A2F58');
        g.shadowColor = hot ? '#FF4D6D' : 'rgba(255,190,11,.55)'; g.shadowBlur = 18 + danger * 20;
        g.fillStyle = grd; g.beginPath(); g.arc(0, 0, r, 0, TAU); g.fill();
        g.shadowBlur = 0;
        g.strokeStyle = hot ? '#FFD1DC' : '#8890C8'; g.lineWidth = 2.5; g.stroke();
        g.fillStyle = '#2B3160';
        g.save(); g.rotate(0.6); rrect(g, -r * 0.25, -r * 1.22, r * 0.5, r * 0.4, 3); g.fill(); g.restore();
        g.strokeStyle = '#C8A26B'; g.lineWidth = 3; g.lineCap = 'round';
        g.beginPath(); g.moveTo(r * 0.45, -r * 0.95); g.quadraticCurveTo(r * 0.9, -r * 1.5, r * 0.6, -r * 1.1); g.stroke();
        g.fillStyle = 'rgba(255,255,255,.35)'; g.beginPath(); g.ellipse(-r * 0.4, -r * 0.4, r * 0.25, r * 0.14, -0.7, 0, TAU); g.fill();
        g.restore();
      },
    };
  },
});
