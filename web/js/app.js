'use strict';
/* ===== App: menus, player setup, Party Cup flow, results ===== */

const $ = (s) => document.querySelector(s);

const App = {
  slots: null, settings: null, picks: null,
  mode: 'free', cups: [0, 0, 0, 0], wins: [0, 0, 0, 0],
  bag: [], pool: [], upcoming: null, round: 0, lastDef: null, screen: 'home', overlay: null, champion: null,

  init() {
    const s = Store.get('slots', null);
    this.slots = Array.isArray(s) && s.length === 4 ? s : ['human', 'human', 'cpu', 'off'];
    if (this.slots.filter((x) => x !== 'off').length < 2) this.slots = ['human', 'human', 'off', 'off'];
    this.settings = Object.assign({ sound: true, vibe: true, target: 5 }, Store.get('settings', {}));
    if (![3, 5, 7, 10].includes(this.settings.target)) this.settings.target = 5;
    const picks = Store.get('picks', null);
    this.picks = new Set(Array.isArray(picks) ? picks.filter((id) => Games.some((g) => g.id === id)) : Games.map((g) => g.id));
    if (this.picks.size < 2) this.picks = new Set(Games.map((g) => g.id));
    this.applySettings();
    Engine.init();
    this.renderSlots();
    this.renderGrid();
    this.bind();
    window.handleBack = () => this.back();
    document.addEventListener('visibilitychange', () => { if (document.hidden) this.autoPause(); });
    document.addEventListener('pointerdown', () => Sfx.init(), { capture: true });
  },

  applySettings() {
    Sfx.enabled = this.settings.sound;
    Haptics.enabled = this.settings.vibe;
    $('#target-lbl').textContent = this.settings.target;
    Store.set('settings', this.settings);
  },

  /* ---------- screens & overlays ---------- */
  show(id) {
    this.screen = id;
    document.querySelectorAll('.screen').forEach((el) => el.classList.toggle('active', el.id === id));
    if (id === 'picker') this.renderGrid();
    if (id === 'cupsetup') this.renderCupGrid();
  },
  openOverlay(id) { this.closeOverlay(); this.overlay = id; $('#' + id).classList.add('active'); },
  closeOverlay() { if (this.overlay) $('#' + this.overlay).classList.remove('active'); this.overlay = null; },

  bind() {
    const tap = (sel, fn) => $(sel).addEventListener('click', (e) => { Sfx.click(); fn(e); });
    tap('#btn-tour', () => this.show('cupsetup'));
    tap('#btn-free', () => { this.mode = 'free'; this.show('picker'); });
    tap('#btn-settings', () => this.openSettings());
    document.querySelectorAll('[data-back]').forEach((b) => b.addEventListener('click', () => { Sfx.click(); this.back(); }));
    tap('#pause-btn', () => this.pause());
    tap('#btn-resume', () => this.resume());
    tap('#btn-restart', () => { this.closeOverlay(); this.playRound(this.lastDef); });
    tap('#btn-quit', () => this.quit());
    tap('#res-menu', () => this.quit());
    tap('#res-next', () => this.next());
    tap('#set-close', () => this.closeOverlay());
    tap('#cup-all', () => { this.picks = new Set(Games.map((g) => g.id)); this.savePicks(); });
    tap('#cup-none', () => { this.picks = new Set(); this.savePicks(); });
    tap('#btn-startcup', () => this.startCup());
    $('#set-sound').addEventListener('change', (e) => { this.settings.sound = e.target.checked; this.applySettings(); Sfx.click(); });
    $('#set-vibe').addEventListener('change', (e) => { this.settings.vibe = e.target.checked; this.applySettings(); Haptics.buzz(30); });
    document.querySelectorAll('#set-target button').forEach((b) => b.addEventListener('click', () => {
      Sfx.click(); this.settings.target = +b.dataset.v; this.applySettings(); this.syncSettings();
    }));
  },

  openSettings() { this.syncSettings(); this.openOverlay('settings'); },
  syncSettings() {
    $('#set-sound').checked = this.settings.sound;
    $('#set-vibe').checked = this.settings.vibe;
    document.querySelectorAll('#set-target button').forEach((b) => b.classList.toggle('on', +b.dataset.v === this.settings.target));
  },

  /* ---------- players ---------- */
  renderSlots() {
    const box = $('#slots');
    box.innerHTML = '';
    this.slots.forEach((st, i) => {
      const c = COLORS[i];
      const el = document.createElement('button');
      el.className = 'slot' + (st === 'off' ? ' off' : '');
      el.style.setProperty('--col', c.main);
      el.style.setProperty('--cold', c.dark);
      el.innerHTML = faceSVG(st) + `<span class="tag">${st === 'human' ? 'PLAYER' : st === 'cpu' ? 'CPU' : 'OFF'}</span>`;
      el.addEventListener('click', () => { Sfx.click(); this.cycle(i); });
      box.appendChild(el);
    });
  },
  cycle(i) {
    const order = ['human', 'cpu', 'off'];
    let n = order[(order.indexOf(this.slots[i]) + 1) % 3];
    const active = this.slots.filter((x, k) => x !== 'off' && k !== i).length;
    if (n === 'off' && active < 2) n = 'human';
    this.slots[i] = n;
    Store.set('slots', this.slots);
    this.wins = [0, 0, 0, 0];
    this.renderSlots();
    $('#slots').children[i].classList.add('pop');
  },
  roster() {
    const tour = this.mode === 'tour';
    return this.slots
      .map((st, i) => ({ slot: i, human: st === 'human', state: st }))
      .filter((p) => p.state !== 'off')
      .map((p) => ({ slot: p.slot, human: p.human, cups: tour ? this.cups[p.slot] : 0, cupsTarget: tour ? this.settings.target : 0 }));
  },

  /* ---------- game cards ---------- */
  card(def, i) {
    const el = document.createElement('button');
    el.className = 'card';
    el.style.setProperty('--g', `linear-gradient(140deg, ${def.grad[0]}, ${def.grad[1]})`);
    el.style.setProperty('--gd', def.gradDark);
    el.style.animationDelay = `${i * 0.03}s`;
    el.innerHTML = `<span class="ctl">${def.control}</span><div class="art">${def.icon}</div><b>${def.name}</b>`;
    return el;
  },
  renderGrid() {
    const grid = $('#grid');
    grid.innerHTML = '';
    Games.forEach((def, i) => {
      const el = this.card(def, i);
      el.addEventListener('click', () => { Sfx.click(); this.mode = 'free'; this.playRound(def); });
      grid.appendChild(el);
    });
  },
  renderCupGrid() {
    const grid = $('#cupgrid');
    grid.innerHTML = '';
    Games.forEach((def, i) => {
      const el = this.card(def, i);
      el.classList.add('pickable');
      el.dataset.id = def.id;
      el.insertAdjacentHTML('beforeend', '<span class="check"><svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="currentColor" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"/></svg></span>');
      el.addEventListener('click', () => {
        Sfx.click();
        if (this.picks.has(def.id)) this.picks.delete(def.id); else this.picks.add(def.id);
        this.savePicks();
      });
      grid.appendChild(el);
    });
    this.syncPicks();
  },
  savePicks() { Store.set('picks', [...this.picks]); this.syncPicks(); },
  syncPicks() {
    document.querySelectorAll('#cupgrid .card').forEach((el) => el.classList.toggle('off', !this.picks.has(el.dataset.id)));
    const n = this.picks.size, btn = $('#btn-startcup');
    $('#cup-count').textContent = `${n} of ${Games.length} games`;
    btn.disabled = n < 2;
    btn.textContent = n < 2 ? 'Pick at least 2 games' : `Start Cup · first to ${this.settings.target}`;
  },

  /* ---------- Party Cup: a different game every round ---------- */
  startCup() {
    if (this.picks.size < 2) return;
    this.mode = 'tour';
    this.cups = [0, 0, 0, 0];
    this.round = 0;
    this.pool = Games.filter((g) => this.picks.has(g.id));
    this.bag = [];
    this.lastDef = null;
    this.upcoming = this.drawGame();
    this.nextCupRound();
  },
  drawGame() {
    if (!this.bag.length) {
      this.bag = shuffle(this.pool.slice());
      if (this.lastDef && this.bag.length > 1 && this.bag[this.bag.length - 1] === this.lastDef) this.bag.unshift(this.bag.pop());
    }
    return this.bag.pop();
  },
  nextCupRound() {
    const def = this.upcoming;
    this.round++;
    this.playRound(def);
  },
  playRound(def) {
    this.lastDef = def;
    this.closeOverlay();
    document.body.classList.add('playing');
    const tour = this.mode === 'tour';
    Engine.start(def, this.roster(), (res) => (tour ? this.onCupRound(res) : this.onResult(res)), tour ? { round: this.round, target: this.settings.target } : {});
  },
  onCupRound(res) {
    const prev = this.cups.slice();
    for (const w of res.winners) this.cups[w.slot]++;
    const champs = res.winners.filter((w) => this.cups[w.slot] >= this.settings.target);
    if (!champs.length) this.upcoming = this.drawGame();
    Engine.showScore({
      round: this.round, winners: res.winners, draw: !res.winners.length, wins: this.cups.slice(), prev,
      target: this.settings.target, final: champs.length > 0, next: champs.length ? null : this.upcoming.name,
    }, () => {
      if (champs.length) this.onResult({ winners: champs, ranking: [...res.ranking].sort((a, b) => this.cups[b.slot] - this.cups[a.slot]) });
      else this.nextCupRound();
    });
  },

  pause() {
    if (!Engine.running || Engine.phase === 'over' || Engine.phase === 'score' || this.overlay) return;
    Engine.pause();
    this.openOverlay('pause');
  },
  autoPause() { if (document.body.classList.contains('playing') && !this.overlay) this.pause(); },
  resume() { this.closeOverlay(); Engine.resume(); },
  quit() {
    Engine.stop();
    this.closeOverlay();
    document.body.classList.remove('playing');
    this.show(this.mode === 'free' ? 'picker' : 'home');
  },
  next() {
    if (this.mode === 'tour') this.startCup();
    else this.playRound(this.lastDef);
  },

  /* Result sheet: end of a single game (Mini Games) or the end of a Party Cup */
  onResult(res) {
    const tour = this.mode === 'tour';
    const winners = res.winners || [];
    const w = winners[0] || null;
    const isWin = (p) => winners.some((x) => x.slot === p.slot);
    if (!tour) for (const x of winners) this.wins[x.slot]++;
    this.champion = tour && w ? w.slot : null;
    const names = (list) => list.map((x) => COLORS[x.slot].name.toUpperCase()).join(' & ');

    const col = w ? COLORS[w.slot] : null;
    $('#res-crown').innerHTML = w ? CROWN_SVG(tour ? '#FFBE0B' : col.main)
      : `<svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="24" fill="#8E95C2"/><path d="M20 30h24M20 38h24" stroke="#fff" stroke-width="5" stroke-linecap="round"/></svg>`;
    const title = $('#res-title');
    if (tour) {
      title.textContent = winners.length > 1 ? `${names(winners)} ARE CHAMPIONS!` : `${names(winners)} IS CHAMPION!`;
      Sfx.champion(); this.confetti();
    } else title.textContent = !w ? 'DRAW!' : winners.length > 1 ? `${names(winners)} WIN!` : `${names(winners)} WINS!`;
    title.style.color = col ? col.main : '#fff';
    $('#res-sub').textContent = tour ? `Party Cup · ${this.round} rounds played` : this.lastDef.name;

    const board = $('#res-board');
    board.innerHTML = '';
    for (const p of res.ranking) {
      const c = COLORS[p.slot];
      const row = document.createElement('div');
      row.className = 'brow' + (isWin(p) ? ' win' : '');
      row.style.setProperty('--col', c.main);
      const right = tour
        ? '<div class="stars">' + Array.from({ length: this.settings.target }, (_, k) => `<i class="${k < this.cups[p.slot] ? 'on' : ''}"></i>`).join('') + '</div>'
        : `<span class="score">${this.wins[p.slot]}</span>`;
      row.innerHTML = `<span class="dot">${faceSVG(p.human ? 'human' : 'cpu').replace('class="face"', '')}</span>
        <span class="nm">${c.name}${p.human ? '' : '<small>CPU</small>'}</span>${right}`;
      board.appendChild(row);
    }
    $('#res-next').textContent = tour ? 'New Cup' : 'Rematch';
    $('#res-menu').textContent = tour ? 'Menu' : 'Games';
    this.openOverlay('result');
  },

  confetti() {
    const box = $('#result');
    for (let i = 0; i < 60; i++) {
      const d = document.createElement('i');
      const s = rand(6, 11);
      d.style.cssText = `position:absolute;top:-20px;left:${rand(0, 100)}%;width:${s}px;height:${s * 1.6}px;border-radius:2px;` +
        `background:${pick(COLORS).main};opacity:.95;pointer-events:none;z-index:-1;` +
        `transform:rotate(${rand(0, 360)}deg);transition:transform ${rand(2, 3.5)}s cubic-bezier(.3,.6,.6,1), top ${rand(2, 3.5)}s cubic-bezier(.3,.6,.6,1);`;
      box.appendChild(d);
      requestAnimationFrame(() => requestAnimationFrame(() => {
        d.style.top = '110%';
        d.style.transform = `translateX(${rand(-80, 80)}px) rotate(${rand(360, 1080)}deg)`;
      }));
      setTimeout(() => d.remove(), 3800);
    }
  },

  /* Android back button: returns true if handled */
  back() {
    if (this.overlay === 'settings') { this.closeOverlay(); return true; }
    if (this.overlay === 'pause') { this.resume(); return true; }
    if (this.overlay === 'result') { this.quit(); return true; }
    if (document.body.classList.contains('playing')) { this.pause(); return true; }
    if (this.screen === 'picker' || this.screen === 'cupsetup') { this.show('home'); return true; }
    return false;
  },
};

(document.fonts && document.fonts.load ? document.fonts.load(`700 20px ${FONT}`).catch(() => {}) : Promise.resolve())
  .finally(() => App.init());
