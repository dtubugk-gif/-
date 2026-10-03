'use strict';
/* Turbo Racers — hold for gas, let go before the corners or you spin out. 4 laps. */
Games.push({
  id: 'racing', name: 'Turbo Racers', control: 'HOLD = GAS',
  desc: 'Hold for gas, let go to brake. Take a corner too fast and you crash! First to finish 4 laps wins.',
  color: '#EF4444', grad: ['#F87171', '#B91C1C'], gradDark: '#7F1D1D',
  icon: `<svg viewBox="0 0 64 64"><rect x="20" y="8" width="24" height="48" rx="9" fill="#fff"/><rect x="24" y="18" width="16" height="10" rx="3" fill="#B91C1C"/><rect x="24" y="40" width="16" height="6" rx="2" fill="#B91C1C" opacity=".6"/><rect x="14" y="14" width="6" height="12" rx="2" fill="#fff" opacity=".8"/><rect x="44" y="14" width="6" height="12" rx="2" fill="#fff" opacity=".8"/><rect x="14" y="40" width="6" height="12" rx="2" fill="#fff" opacity=".8"/><rect x="44" y="40" width="6" height="12" rx="2" fill="#fff" opacity=".8"/></svg>`,

  create(ctx) {
    const { players, S, arena: A, cx, cy } = ctx;
    const LAPS = 4;
    const n = players.length;
    const tw = A.w - 24, th = A.h - 24;
    const lw = clamp(S * 0.07, 20, 30), curb = 6;
    const trackW = curb * 2 + n * lw;
    const ROUT = Math.max(Math.min(tw, th) * 0.3, trackW + 26);
    const VMAX = S * 1.3, VC = S * 0.62, ACC = S * 1.15, BRAKE = S * 1.7;

    function buildLane(inset) {
      const hw = tw / 2 - inset, hh = th / 2 - inset, r = ROUT - inset;
      const segs = [];
      const line = (x0, y0, x1, y1) => segs.push({ arc: false, x0, y0, x1, y1, len: Math.hypot(x1 - x0, y1 - y0) });
      const arc = (ox, oy, a0) => segs.push({ arc: true, ox, oy, r, a0, len: (Math.PI / 2) * r });
      line(cx, cy + hh, cx - hw + r, cy + hh);
      arc(cx - hw + r, cy + hh - r, Math.PI / 2);
      line(cx - hw, cy + hh - r, cx - hw, cy - hh + r);
      arc(cx - hw + r, cy - hh + r, Math.PI);
      line(cx - hw + r, cy - hh, cx + hw - r, cy - hh);
      arc(cx + hw - r, cy - hh + r, Math.PI * 1.5);
      line(cx + hw, cy - hh + r, cx + hw, cy + hh - r);
      arc(cx + hw - r, cy + hh - r, 0);
      line(cx + hw - r, cy + hh, cx, cy + hh);
      let acc = 0;
      for (const s of segs) { s.start = acc; acc += s.len; }
      return { segs, len: acc };
    }
    const lanes = players.map((_, i) => buildLane(curb + lw * (i + 0.5)));
    const LREF = lanes.reduce((a, l) => a + l.len, 0) / n;

    function at(lane, dref) {
      const L = lanes[lane];
      let s = ((dref / LREF) * L.len) % L.len;
      for (const g of L.segs) {
        if (s <= g.start + g.len) {
          const k = (s - g.start) / g.len;
          if (!g.arc) return { x: lerp(g.x0, g.x1, k), y: lerp(g.y0, g.y1, k), ang: Math.atan2(g.y1 - g.y0, g.x1 - g.x0), corner: false };
          const a = g.a0 + k * (Math.PI / 2);
          return { x: g.ox + Math.cos(a) * g.r, y: g.oy + Math.sin(a) * g.r, ang: a + Math.PI / 2, corner: true };
        }
      }
      return { x: cx, y: cy, ang: 0, corner: false };
    }
    function toNextCorner(lane, dref) {
      const L = lanes[lane];
      const s = ((dref / LREF) * L.len) % L.len;
      for (const g of L.segs) if (g.arc && g.start >= s) return ((g.start - s) / L.len) * LREF;
      return ((L.segs[1].start + L.len - s) / L.len) * LREF;
    }

    const C = players.map((p, i) => ({
      p, lane: i, d: 0, v: 0, over: 0, crash: 0, cx: 0, cy: 0, cvx: 0, cvy: 0, spin: 0, done: false,
      skill: rand(0.86, 0.96), mistake: false, lastCorner: false, drift: 0,
    }));
    const finished = [];

    return {
      where(p) { const c = C.find((x) => x.p === p); const q = at(c.lane, c.d); return { x: q.x, y: q.y, r: lw * 0.6 }; },
      update(dt) {
        for (const c of C) {
          const p = c.p;
          if (c.done) { c.v *= Math.exp(-2 * dt); c.d += c.v * dt; continue; }
          if (c.crash > 0) {
            c.crash -= dt; c.cx += c.cvx * dt; c.cy += c.cvy * dt; c.cvx *= Math.exp(-3 * dt); c.cvy *= Math.exp(-3 * dt); c.spin += dt * 14;
            if (Math.random() < 0.4) FX.trail(c.cx, c.cy, 'rgba(180,180,200,.5)', rand(3, 6), 0.6);
            if (c.crash <= 0) { c.v = 0; c.over = 0; }
            continue;
          }
          const q = at(c.lane, c.d);
          if (q.corner && !c.lastCorner) c.mistake = Math.random() < 0.07;
          c.lastCorner = q.corner;
          if (!p.human && !ctx.over) {
            const lim = VC * (c.mistake ? 1.1 : c.skill);
            let hold;
            if (q.corner) hold = c.v < lim;
            else {
              const bd = Math.max(0, (c.v * c.v - lim * lim) / (2 * BRAKE));
              hold = toNextCorner(c.lane, c.d) > bd + c.v * 0.06;
            }
            ctx.botHold(p, hold);
          }
          if (p.down) c.v = Math.min(VMAX, c.v + ACC * dt);
          else c.v = Math.max(0, c.v - BRAKE * dt);
          c.d += c.v * dt;
          c.drift = lerp(c.drift, q.corner ? clamp((c.v - VC * 0.8) / (VC * 0.4), 0, 1) : 0, Math.min(1, dt * 8));
          if (q.corner && c.v > VC) {
            c.over += dt;
            if (Math.random() < 0.6) FX.trail(q.x, q.y, 'rgba(220,220,235,.55)', rand(3, 5), 0.5);
            if (c.over > 0.16) {
              c.crash = 1.1; c.cx = q.x; c.cy = q.y; c.spin = q.ang;
              c.cvx = Math.cos(q.ang) * c.v * 0.6; c.cvy = Math.sin(q.ang) * c.v * 0.6;
              Sfx.hit(); Sfx.noise(0.4, { freq: 1800, vol: 0.25 }); FX.addShake(6); ctx.buzz(70);
              FX.burst(q.x, q.y, '#FFB703', 14, 240, 4, 0.6);
              FX.float(q.x, q.y, 'CRASH!', '#FF6B6B', c.p.lay.rot, 18);
            }
          } else c.over = Math.max(0, c.over - dt);
          if (c.d >= LAPS * LREF) {
            c.done = true; finished.push(p);
            FX.burst(q.x, q.y, COLORS[p.slot].main, 30, 300, 6, 0.9);
            if (finished.length === 1) ctx.end([p, ...C.filter((x) => !x.done).sort((a, b) => b.d - a.d).map((x) => x.p)]);
          } else if (Math.floor((c.d - c.v * dt) / LREF) < Math.floor(c.d / LREF)) {
            Sfx.tone(880, 0.1, { type: 'triangle', vol: 0.12 });
          }
        }
      },

      draw(g) {
        g.fillStyle = '#123A26'; g.fillRect(A.x, A.y, A.w, A.h);
        g.fillStyle = 'rgba(255,255,255,.025)';
        for (let y = A.y; y < A.y + A.h; y += 28) g.fillRect(A.x, y, A.w, 14);
        const box = (inset) => rrect(g, cx - tw / 2 + inset, cy - th / 2 + inset, tw - inset * 2, th - inset * 2, Math.max(4, ROUT - inset));
        g.fillStyle = 'rgba(0,0,0,.35)'; box(-4); g.fill();
        g.fillStyle = '#2A2E45'; box(0); g.fill();
        // lane tints
        players.forEach((p, i) => {
          g.strokeStyle = COLORS[p.slot].main; g.globalAlpha = 0.07; g.lineWidth = lw * 0.9;
          box(curb + lw * (i + 0.5)); g.stroke(); g.globalAlpha = 1;
        });
        // curbs
        for (const inset of [curb / 2, trackW - curb / 2]) {
          g.lineWidth = curb; g.strokeStyle = '#fff'; g.setLineDash([]); box(inset); g.stroke();
          g.strokeStyle = '#E63946'; g.setLineDash([12, 12]); box(inset); g.stroke();
        }
        g.setLineDash([10, 12]); g.strokeStyle = 'rgba(255,255,255,.3)'; g.lineWidth = 2;
        for (let k = 1; k < n; k++) { box(curb + lw * k); g.stroke(); }
        g.setLineDash([]);
        // infield
        g.fillStyle = '#16452D'; box(trackW); g.fill();
        // start / finish line
        const y0 = cy + th / 2 - trackW + curb, cs = (trackW - curb * 2) / Math.max(4, n * 2);
        for (let r = 0; r * cs < trackW - curb * 2 - 0.5; r++) for (let k = 0; k < 2; k++) {
          g.fillStyle = (r + k) % 2 ? '#fff' : '#111';
          g.fillRect(cx - cs + k * cs, y0 + r * cs, cs, cs);
        }
        // leader lap in the infield
        const lead = Math.max(...C.map((c) => c.d));
        ctx.mirror((g) => {
          text(g, `LAP ${Math.min(LAPS, Math.floor(lead / LREF) + 1)}/${LAPS}`, 0, Math.min(th * 0.16, 70), 22, 'rgba(255,255,255,.45)', { shadow: false });
        });
        // cars
        for (const c of C) {
          const col = COLORS[c.p.slot];
          let x, y, ang;
          if (c.crash > 0) { x = c.cx; y = c.cy; ang = c.spin; }
          else { const q = at(c.lane, c.d); x = q.x; y = q.y; ang = q.ang + c.drift * 0.35; }
          const L = lw * 1.25, W = lw * 0.68;
          g.save(); g.translate(x, y); g.rotate(ang);
          g.fillStyle = 'rgba(0,0,0,.35)'; rrect(g, -L / 2 + 2, -W / 2 + 3, L, W, 5); g.fill();
          g.fillStyle = '#111';
          for (const sx of [-L * 0.32, L * 0.28]) for (const sy of [-W / 2 - 1.5, W / 2 - 2.5]) g.fillRect(sx - 3.5, sy, 7, 4);
          g.fillStyle = c.crash > 0 && Math.floor(c.crash * 12) % 2 ? '#fff' : col.main;
          rrect(g, -L / 2, -W / 2, L, W, 5); g.fill();
          g.fillStyle = col.dark; rrect(g, -L * 0.05, -W * 0.36, L * 0.26, W * 0.72, 3); g.fill();
          g.fillStyle = '#9EE7FF'; rrect(g, L * 0.08, -W * 0.3, L * 0.12, W * 0.6, 2); g.fill();
          g.fillStyle = 'rgba(255,255,255,.75)'; g.fillRect(-L / 2 + 2, -1.5, L * 0.35, 3);
          g.fillStyle = '#FFE27A'; g.fillRect(L / 2 - 3, -W / 2 + 2, 2, 3); g.fillRect(L / 2 - 3, W / 2 - 5, 2, 3);
          g.restore();
        }
        for (const c of C) ctx.hudText(c.p, c.done ? 'FINISH!' : `LAP ${Math.min(LAPS, Math.floor(c.d / LREF) + 1)}/${LAPS}`, null, -16, 16);
      },
    };
  },
});
