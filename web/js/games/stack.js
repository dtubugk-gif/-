'use strict';
/* Tower Stack — drop the sliding block right on top of your tower. Overhang gets sliced off. */
Games.push({
  id: 'stack', name: 'Tower Stack', control: 'TAP TO DROP',
  desc: 'A block slides back and forth. Tap to drop it on your tower. Anything hanging over gets cut off! First to 10 blocks wins.',
  color: '#06B6D4', grad: ['#67E8F9', '#0E7490'], gradDark: '#164E63',
  icon: `<svg viewBox="0 0 64 64"><rect x="14" y="44" width="36" height="10" rx="3" fill="#fff"/><rect x="16" y="32" width="32" height="10" rx="3" fill="#fff" opacity=".85"/><rect x="18" y="20" width="26" height="10" rx="3" fill="#fff" opacity=".7"/><rect x="30" y="8" width="26" height="10" rx="3" fill="#fff"/><path d="M8 13h16" stroke="#fff" stroke-width="3" stroke-linecap="round" stroke-dasharray="2 5"/></svg>`,

  create(ctx) {
    const { players, arena: A } = ctx;
    const n = players.length, GOAL = 10;
    const laneW = A.w / n, tw = Math.min(laneW - 18, 140);
    const bh = clamp(A.h * 0.045, 16, 26);
    const visible = Math.floor((A.h - 70) / bh);
    const order = [...players].sort((p, q) => (p.lay.btn.x - q.lay.btn.x) || (p.lay.side === 'bottom' ? -1 : 1));
    const T = players.map((p) => ({
      p, lane: order.indexOf(p), blocks: [{ x: 0, w: tw }], mv: null, cam: 0, done: false, chunks: [], combo: 0, flash: 0,
      tol: 3, wait: 0,
    }));
    function spawn(t) {
      const top = t.blocks[t.blocks.length - 1], lvl = t.blocks.length;
      const fromLeft = lvl % 2 === 0;
      t.mv = { x: fromLeft ? -laneW / 2 + top.w / 2 : laneW / 2 - top.w / 2, w: top.w, dir: fromLeft ? 1 : -1, spd: tw * (1.0 + lvl * 0.07) * rand(0.95, 1.05) };
      const m = Math.random();
      t.tol = m < 0.42 ? rand(0.5, 3.5) : m < 0.9 ? rand(4, 13) : rand(15, 34);
      t.wait = 0;
    }
    T.forEach(spawn);
    const xc = (t) => A.x + laneW * (t.lane + 0.5);
    const yAt = (t, lvl) => {
      const up = t.p.lay.side === 'bottom';
      const k = lvl - t.cam;
      return up ? A.y + A.h - 40 - (k + 1) * bh : A.y + 40 + k * bh;
    };

    function finishCheck() {
      const best = Math.max(...T.map((t) => t.blocks.length));
      const winner = T.find((t) => t.blocks.length - 1 >= GOAL);
      if (winner) ctx.end([winner.p, ...T.filter((t) => t !== winner).sort((a, b) => b.blocks.length - a.blocks.length).map((t) => t.p)]);
      else if (T.every((t) => t.done)) {
        const rank = [...T].sort((a, b) => b.blocks.length - a.blocks.length).map((t) => t.p);
        ctx.end(rank, T.filter((t) => t.blocks.length === best).length > 1);
      }
    }
    function drop(t) {
      const mv = t.mv, top = t.blocks[t.blocks.length - 1];
      const lvl = t.blocks.length;
      const a0 = Math.max(mv.x - mv.w / 2, top.x - top.w / 2), a1 = Math.min(mv.x + mv.w / 2, top.x + top.w / 2);
      const X = xc(t), Y = yAt(t, lvl) + bh / 2;
      if (a1 - a0 <= 1) {
        t.chunks.push({ x: mv.x, w: mv.w, lvl, vy: 0, dy: 0, rot: 0, vr: rand(-3, 3) });
        t.mv = null; t.done = true;
        Sfx.out(); ctx.buzz(80);
        FX.float(X, Y, 'MISS!', '#FF6B6B', t.p.lay.rot, 22);
        finishCheck();
        return;
      }
      if (Math.abs(mv.x - top.x) < 3.5) {
        t.combo++;
        const w = Math.min(tw, top.w + (t.combo >= 3 ? 6 : 0));
        t.blocks.push({ x: top.x, w });
        t.flash = 1;
        Sfx.tone(600 + t.combo * 80, 0.12, { type: 'triangle', vol: 0.15 });
        FX.ring(X + top.x, Y, '#fff', top.w * 0.6, 0.4, 5);
        if (t.p.human || t.combo === 3) FX.float(X + top.x, Y - (t.p.lay.side === 'bottom' ? 20 : -20), t.combo >= 3 ? 'PERFECT x' + t.combo : 'PERFECT!', '#FFE27A', t.p.lay.rot, 16);
      } else {
        t.combo = 0;
        const cut = mv.x < top.x ? { x: (mv.x - mv.w / 2 + a0) / 2, w: a0 - (mv.x - mv.w / 2) } : { x: (a1 + mv.x + mv.w / 2) / 2, w: mv.x + mv.w / 2 - a1 };
        t.chunks.push({ ...cut, lvl, vy: 0, dy: 0, rot: 0, vr: (mv.x < top.x ? -1 : 1) * rand(1, 3) });
        t.blocks.push({ x: (a0 + a1) / 2, w: a1 - a0 });
        Sfx.tone(300, 0.08, { type: 'square', vol: 0.08 });
      }
      spawn(t);
      finishCheck();
    }

    return {
      where(p) { const t = T.find((x) => x.p === p); return { x: xc(t), y: yAt(t, 1) + bh / 2, r: 26 }; },
      update(dt) {
        for (const t of T) {
          t.flash = Math.max(0, t.flash - dt * 3);
          for (const c of t.chunks) { c.vy += 1600 * dt; c.dy += c.vy * dt; c.rot += c.vr * dt; }
          t.chunks = t.chunks.filter((c) => c.dy < A.h);
          const want = Math.max(0, t.blocks.length + 1 - visible * 0.6);
          t.cam = lerp(t.cam, want, Math.min(1, dt * 5));
          if (t.done || !t.mv) continue;
          const mv = t.mv;
          mv.x += mv.dir * mv.spd * dt;
          const lim = laneW / 2 - mv.w / 2 - 4;
          if (mv.x > lim) { mv.x = lim; mv.dir = -1; }
          if (mv.x < -lim) { mv.x = -lim; mv.dir = 1; }
          if (!t.p.human && !ctx.over) {
            t.wait += dt; t.tol += dt * 1.5;
            const top = t.blocks[t.blocks.length - 1];
            if (Math.abs(mv.x - top.x) < t.tol) ctx.botTap(t.p);
          }
          if (t.p.pressed) drop(t);
        }
      },

      draw(g) {
        for (const t of T) {
          const col = COLORS[t.p.slot], X = xc(t);
          g.fillStyle = 'rgba(255,255,255,.03)'; g.fillRect(A.x + laneW * t.lane + 4, A.y, laneW - 8, A.h);
          // goal line
          const gyy = yAt(t, GOAL + 1);
          if (gyy > A.y && gyy < A.y + A.h) {
            g.strokeStyle = 'rgba(255,226,122,.6)'; g.setLineDash([6, 6]); g.lineWidth = 2;
            g.beginPath(); g.moveTo(X - laneW / 2 + 8, gyy); g.lineTo(X + laneW / 2 - 8, gyy); g.stroke(); g.setLineDash([]);
          }
          const drawBlock = (x, w, lvl, alpha = 1) => {
            const y = yAt(t, lvl);
            if (y < A.y - bh || y > A.y + A.h) return;
            g.globalAlpha = alpha;
            g.fillStyle = col.dark; rrect(g, X + x - w / 2, y + 3, w, bh - 1, 4); g.fill();
            g.fillStyle = lvl % 2 ? col.main : col.light; rrect(g, X + x - w / 2, y, w, bh - 2, 4); g.fill();
            g.fillStyle = 'rgba(255,255,255,.3)'; g.fillRect(X + x - w / 2 + 3, y + 2, w - 6, 2);
            g.globalAlpha = 1;
          };
          t.blocks.forEach((b, i) => drawBlock(b.x, b.w, i));
          if (t.flash > 0) {
            const b = t.blocks[t.blocks.length - 1], y = yAt(t, t.blocks.length - 1);
            g.fillStyle = `rgba(255,255,255,${t.flash * 0.6})`; rrect(g, X + b.x - b.w / 2, y, b.w, bh - 2, 4); g.fill();
          }
          if (t.mv) drawBlock(t.mv.x, t.mv.w, t.blocks.length);
          const up = t.p.lay.side === 'bottom';
          for (const c of t.chunks) {
            const y = yAt(t, c.lvl) + (up ? c.dy : -c.dy);
            g.save(); g.translate(X + c.x, y + bh / 2); g.rotate(c.rot);
            g.globalAlpha = 0.85; g.fillStyle = col.main; rrect(g, -c.w / 2, -bh / 2, c.w, bh - 2, 4); g.fill(); g.globalAlpha = 1;
            g.restore();
          }
          ctx.hudText(t.p, t.done ? `${t.blocks.length - 1} - DONE` : `${t.blocks.length - 1}/${GOAL}`, null, -16, 17);
        }
      },
    };
  },
});
