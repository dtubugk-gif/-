import { spawn } from "node:child_process";

/** Converts any ffmpeg-readable audio (M4A/AAC/3GP/WAV/...) to mono 64 kbps MP3, max 60 s. */
export function transcodeToMp3(input: Buffer, ffmpegPath: string): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const proc = spawn(ffmpegPath, ["-hide_banner", "-loglevel", "error", "-i", "pipe:0", "-t", "60", "-ac", "1", "-ar", "22050", "-b:a", "64k", "-f", "mp3", "pipe:1"]);
    const out: Buffer[] = [];
    let err = "";
    const timer = setTimeout(() => proc.kill("SIGKILL"), 20_000);
    proc.stdout.on("data", (c: Buffer) => out.push(c));
    proc.stderr.on("data", (c: Buffer) => (err += c.toString()));
    proc.on("error", (e) => {
      clearTimeout(timer);
      reject(e);
    });
    proc.on("close", (code) => {
      clearTimeout(timer);
      const buf = Buffer.concat(out);
      if (code === 0 && buf.length > 0) resolve(buf);
      else reject(new Error(`ffmpeg exited with ${code}: ${err.slice(0, 500)}`));
    });
    proc.stdin.on("error", () => {});
    proc.stdin.end(input);
  });
}
