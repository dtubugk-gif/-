'use strict';
/* Brain Blitz — tap only when the equation is TRUE. */
Games.push({
  id: 'math', name: 'Brain Blitz', control: 'TAP IF TRUE',
  desc: 'Is the equation correct? Tap only if it is TRUE. Wrong taps cost a point. Score the most!',
  color: '#FFBE0B', grad: ['#FCD34D', '#F59E0B'], gradDark: '#B45309',
  icon: `<svg viewBox="0 0 64 64"><rect x="8" y="10" width="48" height="44" rx="10" fill="#fff"/><path d="M18 24h10M23 19v10M36 24h10M18 40h10M36 37h10M36 43h10" stroke="#F59E0B" stroke-width="4" stroke-linecap="round"/></svg>`,

  create(ctx) {
    const { players, arena: A, cx, cy } = ctx;
    const GOAL = players.length > 2 ? 2 : 3;
    const score = new Map(players.map((p) => [p, 0]));
    const locked = new Set();
    let q, state, t, limit, winner, plan, pop = 0;

    function make() {
      const op = pick(['+', '+', '-', '×']);
      let a, b, c;
      if (op === '+') { a = randi(2, 25); b = randi(2, 25); c = a + b; }
      else if (op === '-') { a = randi(8, 30); b = randi(1, a - 1); c = a - b; }
      else { a = randi(2, 9); b = randi(2, 9); c = a * b; }
      const truth = Math.random() < 0.6;
      let shown = c;
      if (!truth) {
        const offs = op === '×' ? [-a, a, -b, b, -1, 1, 2] : [-10, -2, -1, 1, 2, 10];
        do { shown = c + pick(offs); } while (shown === c || shown < 0);
      }
      return { str: `${a} ${op} ${b} = ${shown}`, truth };
    }
    function next() {
      q = make(); state = 'ask'; t = 0; winner = null; pop = 0;
      limit = q.truth ? 3.0 : 1.9;
      locked.clear();
      plan = new Map(players.map((p) => {
        const r = q.truth ? (Math.random() < 0.8 ? rand(0.8, 2.1) : -1) : (Math.random() < 0.07 ? rand(0.8, 2) : -1);
        return [p, r];
      }));
    }
    next();

    return {
      label(p) { return locked.has(p) ? 'LOCKED' : 'TRUE!'; },
      update(dt) {
        t += dt; pop = Math.min(1, pop + dt * 4);
        if (state === 'ask') {
          for (const p of players) if (!p.human && !locked.has(p) && plan.get(p) > 0 && t >= plan.get(p)) { plan.set(p, -1); ctx.botTap(p); }
          const tappers = players.filter((p) => p.pressed && !locked.has(p));
          if (q.truth && tappers.length) {
            const p = pick(tappers);
            winner = p; score.set(p, score.get(p) + 1);
            state = 'reveal'; t = 0;
            Sfx.coin(); ctx.buzz(30);
            FX.float(p.lay.home.x, p.lay.home.y, '+1', COLORS[p.slot].light, p.lay.rot, 34);
            FX.burst(cx, cy, COLORS[p.slot].main, 24, 300, 5, 0.7);
            if (score.get(p) >= GOAL) ctx.end([...players].sort((x, y) => score.get(y) - score.get(x)));
          } else if (!q.truth) {
            for (const p of tappers) {
              locked.add(p); score.set(p, Math.max(0, score.get(p) - 1));
              Sfx.wrong(); ctx.buzz(80);
              FX.float(p.lay.home.x, p.lay.home.y, '-1', '#FF6B6B', p.lay.rot, 34);
            }
          }
          if (state === 'ask' && t >= limit) { state = 'reveal'; t = 0; }
        } else if (state === 'reveal' && t > 0.8 && !ctx.over) next();
      },

      draw(g) {
        const cw = Math.min(A.w - 40, 330), ch = Math.min(A.h * 0.2, 96);
        const prog = state === 'ask' ? 1 - t / limit : 0;
        ctx.mirror((g) => {
          const y = Math.min(A.h * 0.22, A.h / 2 - ch / 2 - 14);
          const s = easeOutBack(pop);
          g.save(); g.translate(0, y); g.scale(s, s);
          let bg = '#F4F6FF', fg = '#1E2448';
          if (state === 'reveal') {
            if (winner) { bg = COLORS[winner.slot].main; fg = '#fff'; }
            else { bg = q.truth ? '#2DD881' : '#FF4D6D'; fg = '#fff'; }
          }
          g.fillStyle = 'rgba(0,0,0,.35)'; rrect(g, -cw / 2, -ch / 2 + 7, cw, ch, 22); g.fill();
          g.fillStyle = bg; rrect(g, -cw / 2, -ch / 2, cw, ch, 22); g.fill();
          text(g, q.str, 0, 2, ch * 0.44, fg, { shadow: false });
          if (state === 'reveal') {
            const tag = winner ? `${COLORS[winner.slot].name.toUpperCase()} +1` : (q.truth ? 'IT WAS TRUE' : 'FALSE!');
            text(g, tag, 0, ch / 2 + 22, 18, '#fff');
          } else {
            g.fillStyle = 'rgba(255,255,255,.12)'; rrect(g, -cw / 2 + 20, ch / 2 + 14, cw - 40, 8, 4); g.fill();
            g.fillStyle = prog < 0.3 ? '#FF6B6B' : '#A66CFF'; rrect(g, -cw / 2 + 20, ch / 2 + 14, (cw - 40) * prog, 8, 4); g.fill();
          }
          g.restore();
        });
        for (const p of players) {
          ctx.atPlayer(p, (g) => {
            const s = 7, gap = 6, tw = GOAL * s * 2 + (GOAL - 1) * gap;
            for (let i = 0; i < GOAL; i++) {
              const x = -tw / 2 + s + i * (s * 2 + gap);
              g.fillStyle = i < score.get(p) ? COLORS[p.slot].main : 'rgba(255,255,255,.1)';
              g.beginPath(); g.arc(x, -13, s, 0, TAU); g.fill();
            }
          });
        }
      },
    };
  },
});
