#!/usr/bin/env node
/*
 * Render the player's landscapes into an MP4 with the guided ASMR relaxation mix.
 *
 * The scenes in player.html are pure functions of time, so each frame is drawn
 * in headless Chromium, grabbed as JPEG and piped to ffmpeg. Frames are split
 * across a few browser pages in parallel, then the segments are joined and the
 * audio is added. The visual swell uses the same 0.1 Hz curve as the audio.
 *
 * Usage:
 *   npm install playwright   (or a global install)
 *   pip install imageio-ffmpeg && python3 generate_noise.py
 *   node render_video.js [--minutes 10] [--fps 30] [--width 1280] [--height 720]
 */
const { execFileSync, spawn } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

let chromium;
try { ({ chromium } = require("playwright")); }
catch { ({ chromium } = require(path.join(execFileSync("npm", ["root", "-g"]).toString().trim(), "playwright"))); }

const arg = (name, def) => {
  const i = process.argv.indexOf("--" + name);
  return i > 0 ? Number(process.argv[i + 1]) : def;
};
const MINUTES = arg("minutes", 10), FPS = arg("fps", 30), W = arg("width", 1280), H = arg("height", 720);
const WORKERS = Math.max(1, Math.min(4, os.cpus().length));
const BATCH = 20;
const ROOT = __dirname;
const AUDIO = path.join(ROOT, "audio", "asmr_relaxation.mp3");
const OUT = path.join(ROOT, "video", "relaxing_scenes.mp4");
const FFMPEG = process.env.FFMPEG ||
  execFileSync("python3", ["-c", "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())"]).toString().trim();

async function renderSegment(browser, first, last, file) {
  const page = await browser.newPage();
  await page.goto("file://" + path.join(ROOT, "player.html"));
  await page.evaluate(([w, h]) => {
    window.__cv = document.createElement("canvas");
    window.__cv.width = w; window.__cv.height = h;
  }, [W, H]);
  const ff = spawn(FFMPEG, [
    "-y", "-loglevel", "error",
    "-f", "image2pipe", "-framerate", String(FPS), "-c:v", "mjpeg", "-i", "-",
    "-c:v", "libx264", "-preset", "slow", "-crf", "23", "-tune", "film",
    "-x264-params", "aq-mode=3", "-pix_fmt", "yuv420p", file
  ], { stdio: ["pipe", "inherit", "inherit"] });
  const done = new Promise((ok, fail) => ff.on("close", (c) => (c ? fail(new Error("ffmpeg " + c)) : ok())));

  for (let f = first; f < last; f += BATCH) {
    const frames = await page.evaluate(([from, to, fps]) => {
      const out = [];
      for (let i = from; i < to; i++) {
        const t = i / fps;
        const s = 0.5 - 0.5 * Math.cos(2 * Math.PI * 0.1 * t);
        window.__scenes.render(window.__cv, "auto", t, s);
        out.push(window.__cv.toDataURL("image/jpeg", 0.95).split(",")[1]);
      }
      return out;
    }, [f, Math.min(f + BATCH, last), FPS]);
    for (const b64 of frames) {
      if (!ff.stdin.write(Buffer.from(b64, "base64"))) await new Promise((r) => ff.stdin.once("drain", r));
    }
  }
  ff.stdin.end();
  await done;
  await page.close();
}

(async () => {
  const total = Math.round(MINUTES * 60 * FPS);
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "scenes-"));
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  const browser = await chromium.launch();
  const per = Math.ceil(total / WORKERS), parts = [];
  const started = Date.now();
  await Promise.all(Array.from({ length: WORKERS }, (_, k) => {
    const file = path.join(tmp, `part${k}.mp4`);
    parts.push(file);
    return renderSegment(browser, k * per, Math.min(total, (k + 1) * per), file);
  }));
  await browser.close();
  console.log(`rendered ${total} frames in ${((Date.now() - started) / 1000).toFixed(0)} s`);

  const list = path.join(tmp, "list.txt");
  fs.writeFileSync(list, parts.map((p) => `file '${p}'`).join("\n"));
  execFileSync(FFMPEG, [
    "-y", "-loglevel", "error",
    "-f", "concat", "-safe", "0", "-i", list, "-i", AUDIO,
    "-map", "0:v", "-map", "1:a", "-c:v", "copy", "-c:a", "aac", "-b:a", "160k",
    "-shortest", "-movflags", "+faststart", OUT
  ]);
  fs.rmSync(tmp, { recursive: true, force: true });
  console.log(`${OUT}: ${(fs.statSync(OUT).size / 1e6).toFixed(1)} MB`);
})();
