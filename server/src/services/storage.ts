import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { join, resolve } from "node:path";

export type MediaKind = "recordings" | "greetings" | "reports";
const KINDS: readonly string[] = ["recordings", "greetings", "reports"];

/** Local-disk media store. Mount DATA_DIR on a persistent volume in production. */
export class MediaStore {
  readonly root: string;

  constructor(dataDir: string) {
    this.root = resolve(dataDir, "media");
  }

  private path(kind: MediaKind, name: string): string {
    if (!/^[\w.-]+$/.test(name)) throw new Error("invalid media name");
    return join(this.root, kind, name);
  }

  async put(kind: MediaKind, name: string, data: Buffer): Promise<string> {
    await mkdir(join(this.root, kind), { recursive: true });
    await writeFile(this.path(kind, name), data);
    return `${kind}/${name}`;
  }

  async get(relative: string): Promise<Buffer> {
    const [kind, name] = relative.split("/");
    if (!kind || !KINDS.includes(kind) || !name) throw new Error("invalid media path");
    return readFile(this.path(kind as MediaKind, name));
  }

  async remove(relative: string | null | undefined): Promise<void> {
    if (!relative) return;
    const [kind, name] = relative.split("/");
    if (!kind || !KINDS.includes(kind) || !name) return;
    await rm(this.path(kind as MediaKind, name), { force: true });
  }
}
