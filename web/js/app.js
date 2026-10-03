'use strict';
/* ===== App: menus, player setup, tournament flow, results ===== */

const $ = (s) => document.querySelector(s);

const App = {
  slots: null, settings: null,
  mode: 'free', cups: [0, 0, 0, 0], wins: [0, 0, 0, 0],
  bag: [], lastDef: null, screen: 'home', overlay: null,

  init() {
    const s = Store.get('slots', null);
    this.slots = Array.isArray(s) && s.length === 4 ? s : ['human', 'human', 'cpu', 'off'];
    if (this.slots.filter((x) => x !== 'off').length < 2) this.slots = ['human', 'human', 'off', 'off'];
    this.settings = Object.assign({ sound: true, vibe: true, target: 5 }, Store.get('settings', {}));
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
  },
  openOverlay(id) { this.closeOverlay(); this.overlay = id; $('#' + id).classList.add('active'); },
  closeOverlay() { if (this.overlay) $('#' + this.overlay).classList.remove('active'); this.overlay = null; },

  bind() {
    const tap = (sel, fn) => $(sel).addEventListener('click', (e) => { Sfx.click(); fn(e); });
    tap('#btn-tour', () => this.startTournament());
    tap('#btn-free', () => { this.mode = 'free'; this.show('picker'); });
    tap('#btn-settings', () => this.openSettings());
    document.querySelectorAll('[data-back]').forEach((b) => b.addEventListener('click', () => { Sfx.click(); this.back(); }));
    tap('#pause-btn', () => this.pause());
    tap('#btn-resume', () => this.resume());
    tap('#btn-restart', () => { this.closeOverlay(); this.play(this.lastDef); });
    tap('#btn-quit', () => this.quit());
    tap('#res-menu', () => this.quit());
    tap('#res-next', () => this.next());
    tap('#set-close', () => this.closeOverlay());
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
    return this.slots
      .map((st, i) => ({ slot: i, human: st === 'human', state: st }))
      .filter((p) => p.state !== 'off')
      .map((p) => ({ slot: p.slot, human: p.human, cups: this.mode === 'tour' ? this.cups[p.slot] : null, cupsTarget: this.mode === 'tour' ? this.settings.target : 0 }));
  },

  /* ---------- game picker ---------- */
  renderGrid() {
    const grid = $('#grid');
    grid.innerHTML = '';
    Games.forEach((def, i) => {
      const el = document.createElement('button');
      el.className = 'card';
      el.style.setProperty('--g', `linear-gradient(140deg, ${def.grad[0]}, ${def.grad[1]})`);
      el.style.setProperty('--gd', def.gradDark);
      el.style.animationDelay = `${i * 0.04}s`;
      el.innerHTML = `<span class="ctl">${def.control}</span><div class="art">${def.icon}</div><b>${def.name}</b>`;
      el.addEventListener('click', () => { Sfx.click(); this.mode = 'free'; this.play(def); });
      grid.appendChild(el);
    });
  },

  /* ---------- flow ---------- */
  startTournament() {
    this.mode = 'tour';
    this.cups = [0, 0, 0, 0];
    this.bag = [];
    this.nextTourGame();
  },
  nextTourGame() {
    if (!this.bag.length) {
      this.bag = shuffle(Games.slice());
      if (this.lastDef && this.bag[this.bag.length - 1] === this.lastDef) this.bag.unshift(this.bag.pop());
    }
    this.play(this.bag.pop());
  },
  play(def) {
    this.lastDef = def;
    this.closeOverlay();
    document.body.classList.add('playing');
    Engine.start(def, this.roster(), (res) => this.onResult(res));
  },
  pause() {
    if (!Engine.running || Engine.phase === 'over' || this.overlay) return;
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
    if (this.mode === 'tour') {
      if (this.champion != null) { this.champion = null; this.startTournament(); }
      else this.nextTourGame();
    } else this.play(this.lastDef);
  },

  onResult(res) {
    const ranking = res.ranking;
    const w = !res.draw && ranking[0] ? ranking[0] : null;
    const prevCups = this.cups.slice();
    if (w) { this.wins[w.slot]++; if (this.mode === 'tour') this.cups[w.slot]++; }
    this.champion = null;
    if (this.mode === 'tour' && w && this.cups[w.slot] >= this.settings.target) this.champion = w.slot;

    const col = w ? COLORS[w.slot] : null;
    $('#res-crown').innerHTML = w ? CROWN_SVG(this.champion != null ? '#FFBE0B' : col.main)
      : `<svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="24" fill="#8E95C2"/><path d="M20 30h24M20 38h24" stroke="#fff" stroke-width="5" stroke-linecap="round"/></svg>`;
    const title = $('#res-title');
    if (this.champion != null) { title.textContent = `${col.name.toUpperCase()} IS CHAMPION!`; Sfx.champion(); this.confetti(); }
    else title.textContent = w ? `${col.name.toUpperCase()} WINS!` : 'DRAW!';
    title.style.color = col ? col.main : '#fff';
    $('#res-sub').textContent = this.mode === 'tour'
      ? (this.champion != null ? 'Tournament complete' : `${this.lastDef.name} · first to ${this.settings.target} cups`)
      : this.lastDef.name;

    const board = $('#res-board');
    board.innerHTML = '';
    const rows = this.mode === 'tour'
      ? [...ranking].sort((a, b) => this.cups[b.slot] - this.cups[a.slot])
      : ranking;
    for (const p of rows) {
      const c = COLORS[p.slot];
      const row = document.createElement('div');
      row.className = 'brow' + (w && p.slot === w.slot ? ' win' : '');
      row.style.setProperty('--col', c.main);
      let right;
      if (this.mode === 'tour') {
        right = '<div class="cups">' + Array.from({ length: this.settings.target }, (_, k) => {
          const on = k < this.cups[p.slot], isNew = on && k >= prevCups[p.slot];
          return `<i class="${on ? 'on' : ''}${isNew ? ' new' : ''}"></i>`;
        }).join('') + '</div>';
      } else right = `<span class="score">${this.wins[p.slot]}</span>`;
      row.innerHTML = `<span class="dot">${faceSVG(p.human ? 'human' : 'cpu').replace('class="face"', '')}</span>
        <span class="nm">${c.name}${p.human ? '' : '<small>CPU</small>'}</span>${right}`;
      board.appendChild(row);
    }
    $('#res-next').textContent = this.mode === 'tour' ? (this.champion != null ? 'New Cup' : 'Next Game') : 'Rematch';
    $('#res-menu').textContent = this.mode === 'tour' ? 'Menu' : 'Games';
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
    if (this.screen === 'picker') { this.show('home'); return true; }
    return false;
  },
};

(document.fonts && document.fonts.load ? document.fonts.load(`700 20px ${FONT}`).catch(() => {}) : Promise.resolve())
  .finally(() => App.init());
