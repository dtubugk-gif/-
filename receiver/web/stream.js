// Shared playback engine: reads ShakeLight packets from /stream, decodes H.264 with the browser's
// hardware decoder (WebCodecs), draws the newest frame, and plays 48 kHz 16-bit stereo PCM.
"use strict";

export const PKT = { HELLO: 0, CONFIG: 1, KEY: 2, DELTA: 3, AUDIO: 4, STATUS: 5, APPS: 6, CLIPBOARD: 7, BYE: 127 };
const HEADER = 13;

export class Player {
  /** @param {HTMLCanvasElement} canvas */
  constructor(canvas, { onPacket, onFirstFrame, onStats } = {}) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d", { alpha: false, desynchronized: true });
    this.onPacket = onPacket || (() => {});
    this.onFirstFrame = onFirstFrame || (() => {});
    this.onStats = onStats || (() => {});
    this.decoder = null;
    this.config = null;
    this.codec = null;
    this.needKey = true;
    this.frame = null;
    this.pending = false;
    this.rect = null;      // where the picture is drawn, in CSS pixels
    this.sound = true;
    this.audio = null;
    this.nextAudio = 0;
    this.frames = 0;
    this.shown = false;
    setInterval(() => { this.onStats({ fps: this.frames }); this.frames = 0; }, 1000);
    new ResizeObserver(() => this.resize()).observe(canvas);
    this.resize();
  }

  resize() {
    const dpr = window.devicePixelRatio || 1;
    const w = Math.round(this.canvas.clientWidth * dpr), h = Math.round(this.canvas.clientHeight * dpr);
    if (w && h && (this.canvas.width !== w || this.canvas.height !== h)) {
      this.canvas.width = w;
      this.canvas.height = h;
    }
    this.draw();
  }

  /** Converts a mouse position to 0..1 of the phone picture, or null when outside it. */
  toPicture(clientX, clientY, clamp = false) {
    if (!this.rect) return null;
    const r = this.canvas.getBoundingClientRect();
    let u = (clientX - r.left - this.rect.x) / this.rect.w;
    let v = (clientY - r.top - this.rect.y) / this.rect.h;
    if (clamp) { u = Math.min(1, Math.max(0, u)); v = Math.min(1, Math.max(0, v)); }
    else if (u < 0 || u > 1 || v < 0 || v > 1) return null;
    return [u, v];
  }

  draw() {
    const { ctx, canvas } = this;
    const f = this.frame;
    ctx.fillStyle = "#000";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    if (!f) { this.rect = null; return; }
    const fw = f.displayWidth, fh = f.displayHeight;
    const scale = Math.min(canvas.width / fw, canvas.height / fh);
    const w = Math.round(fw * scale), h = Math.round(fh * scale);
    const x = Math.round((canvas.width - w) / 2), y = Math.round((canvas.height - h) / 2);
    const dpr = window.devicePixelRatio || 1;
    this.rect = { x: x / dpr, y: y / dpr, w: w / dpr, h: h / dpr };
    ctx.imageSmoothingEnabled = scale !== 1;
    ctx.imageSmoothingQuality = "high";
    ctx.drawImage(f, x, y, w, h);
  }

  get videoSize() { return this.frame ? [this.frame.displayWidth, this.frame.displayHeight] : null; }

  // ---- video ----
  onFrame(frame) {
    if (this.frame) this.frame.close();
    this.frame = frame;
    this.frames++;
    if (!this.shown) { this.shown = true; this.onFirstFrame(); }
    if (!this.pending) {
      this.pending = true;
      requestAnimationFrame(() => { this.pending = false; this.draw(); });
    }
  }

  /** A new configuration is always followed by a key frame, so only ask for one after an error. */
  createDecoder(requestKey = false) {
    if (this.decoder && this.decoder.state !== "closed") this.decoder.close();
    this.decoder = new VideoDecoder({
      output: f => this.onFrame(f),
      error: e => { console.warn("decoder", e); this.decoder = null; this.needKey = true; },
    });
    // No hardware preference: the browser uses the GPU when it can and falls back to software otherwise
    // ("prefer-hardware" fails outright on computers without a suitable GPU decoder).
    this.decoder.configure({ codec: this.codec, optimizeForLatency: true });
    this.needKey = true;
    if (requestKey) this.requestKey();
  }

  requestKey() {
    const now = performance.now();
    if (now - (this.lastKeyRequest || 0) < 1500) return;
    this.lastKeyRequest = now;
    fetch("/keyframe", { method: "POST" }).catch(() => {});
  }

  handleConfig(payload) {
    const annexB = payload.slice(4);
    const sps = findNal(annexB, 7);
    if (!sps || sps.length < 4) return;
    const codec = "avc1." + hex(sps[1]) + hex(sps[2]) + hex(sps[3]);
    const same = this.config && this.codec === codec && sameBytes(this.config, annexB) &&
      this.decoder && this.decoder.state === "configured";
    this.config = annexB;
    this.codec = codec;
    if (!same) this.createDecoder();
  }

  handleVideo(pts, payload, key) {
    if (!this.config) return;
    if (!this.decoder || this.decoder.state === "closed") this.createDecoder(true);
    if (this.needKey && !key) return;
    // Falling behind (slow computer or network burst): skip to the next key frame instead of lagging.
    if (this.decoder.decodeQueueSize > 4 && !key) {
      this.needKey = true;
      this.requestKey();
      return;
    }
    this.needKey = false;
    let data = payload;
    if (key) {
      data = new Uint8Array(this.config.length + payload.length);
      data.set(this.config, 0);
      data.set(payload, this.config.length);
    }
    try {
      this.decoder.decode(new EncodedVideoChunk({ type: key ? "key" : "delta", timestamp: pts, data }));
    } catch (e) {
      console.warn(e);
      this.needKey = true;
    }
  }

  // ---- audio ----
  resumeAudio() { if (this.audio && this.audio.state === "suspended") this.audio.resume(); }

  handleAudio(payload) {
    if (!this.sound) return;
    if (!this.audio) this.audio = new AudioContext({ sampleRate: 48000, latencyHint: "interactive" });
    if (this.audio.state === "suspended") return;
    const frames = payload.length >> 2;
    if (!frames) return;
    const buffer = this.audio.createBuffer(2, frames, 48000);
    const l = buffer.getChannelData(0), r = buffer.getChannelData(1);
    const dv = new DataView(payload.buffer, payload.byteOffset, payload.byteLength);
    for (let i = 0; i < frames; i++) {
      l[i] = dv.getInt16(i * 4, true) / 32768;
      r[i] = dv.getInt16(i * 4 + 2, true) / 32768;
    }
    const src = this.audio.createBufferSource();
    src.buffer = buffer;
    src.connect(this.audio.destination);
    const now = this.audio.currentTime;
    if (this.nextAudio < now + 0.02 || this.nextAudio > now + 0.25) this.nextAudio = now + 0.05;
    src.start(this.nextAudio);
    this.nextAudio += buffer.duration;
  }

  reset() {
    this.config = null;
    if (this.decoder && this.decoder.state !== "closed") this.decoder.close();
    this.decoder = null;
    if (this.frame) { this.frame.close(); this.frame = null; }
    this.shown = false;
    this.draw();
  }

  // ---- stream ----
  handle(type, pts, payload) {
    switch (type) {
      case PKT.CONFIG: this.handleConfig(payload); break;
      case PKT.KEY: this.handleVideo(pts, payload, true); break;
      case PKT.DELTA: this.handleVideo(pts, payload, false); break;
      case PKT.AUDIO: this.handleAudio(payload); break;
      case PKT.BYE: this.reset(); break;
    }
    this.onPacket(type, payload);
  }

  async run() {
    for (;;) {
      try {
        const res = await fetch("/stream", { cache: "no-store" });
        const reader = res.body.getReader();
        const packets = new PacketReader((t, p, d) => this.handle(t, p, d));
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          packets.push(value);
        }
      } catch (e) {
        console.warn("stream", e);
      }
      this.handle(PKT.BYE, 0, new Uint8Array());
      await new Promise(r => setTimeout(r, 1500));
    }
  }
}

export const decodeJson = payload => { try { return JSON.parse(new TextDecoder().decode(payload)); } catch (e) { return null; } };

function findNal(data, type) {
  for (let i = 0; i + 3 < data.length; i++) {
    if (data[i] === 0 && data[i + 1] === 0 && data[i + 2] === 1) {
      if ((data[i + 3] & 0x1f) === type) return data.subarray(i + 3);
      i += 2;
    }
  }
  return null;
}
const hex = b => b.toString(16).padStart(2, "0");
function sameBytes(a, b) {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

class PacketReader {
  constructor(onPacket) { this.onPacket = onPacket; this.buf = new Uint8Array(1 << 20); this.len = 0; }
  push(chunk) {
    if (this.len + chunk.length > this.buf.length) {
      const bigger = new Uint8Array(Math.max(this.buf.length * 2, this.len + chunk.length));
      bigger.set(this.buf.subarray(0, this.len));
      this.buf = bigger;
    }
    this.buf.set(chunk, this.len);
    this.len += chunk.length;
    let off = 0;
    while (this.len - off >= HEADER) {
      const b = this.buf;
      const n = ((b[off + 1] << 24) | (b[off + 2] << 16) | (b[off + 3] << 8) | b[off + 4]) >>> 0;
      if (this.len - off < HEADER + n) break;
      const pts = Number(new DataView(b.buffer, b.byteOffset + off + 5, 8).getBigInt64(0));
      this.onPacket(b[off], pts, b.slice(off + HEADER, off + HEADER + n));
      off += HEADER + n;
    }
    if (off > 0) { this.buf.copyWithin(0, off, this.len); this.len -= off; }
  }
}

// ---- keyboard: browser KeyboardEvent.code → Android keycode ----
const KEYCODES = {
  Escape: 111, Backspace: 67, Tab: 61, Enter: 66, NumpadEnter: 160, Space: 62, Delete: 112, Insert: 124,
  ArrowLeft: 21, ArrowUp: 19, ArrowRight: 22, ArrowDown: 20, Home: 122, End: 123, PageUp: 92, PageDown: 93,
  ShiftLeft: 59, ShiftRight: 60, ControlLeft: 113, ControlRight: 114, AltLeft: 57, AltRight: 58,
  MetaLeft: 117, MetaRight: 118, CapsLock: 115, ContextMenu: 82, PrintScreen: 120, ScrollLock: 116, Pause: 121,
  Minus: 69, Equal: 70, BracketLeft: 71, BracketRight: 72, Backslash: 73, Semicolon: 74, Quote: 75,
  Backquote: 68, Comma: 55, Period: 56, Slash: 76,
  NumLock: 143, NumpadDivide: 154, NumpadMultiply: 155, NumpadSubtract: 156, NumpadAdd: 157, NumpadDecimal: 158,
  AudioVolumeUp: 24, AudioVolumeDown: 25, AudioVolumeMute: 164, MediaPlayPause: 85, MediaTrackNext: 87, MediaTrackPrevious: 88,
};
for (let i = 0; i < 26; i++) KEYCODES["Key" + String.fromCharCode(65 + i)] = 29 + i;
for (let i = 0; i < 10; i++) { KEYCODES["Digit" + i] = 7 + i; KEYCODES["Numpad" + i] = 144 + i; }
for (let i = 1; i <= 12; i++) KEYCODES["F" + i] = 130 + i;

export function androidKeycode(code) { return KEYCODES[code] || 0; }

// Android KeyEvent meta-state bits.
export function androidMeta(e) {
  let m = 0;
  if (e.shiftKey) m |= 0x1 | 0x40;
  if (e.altKey) m |= 0x2 | 0x10;
  if (e.ctrlKey) m |= 0x1000 | 0x2000;
  if (e.metaKey) m |= 0x10000 | 0x20000;
  if (e.getModifierState && e.getModifierState("CapsLock")) m |= 0x100000;
  return m;
}
