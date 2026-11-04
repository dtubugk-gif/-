"""Train TinyGPT on a text file.  Usage: python train.py [--data data/input.txt] [--steps 3000]"""
import argparse
import math
import time

import torch

from model import TinyGPT

p = argparse.ArgumentParser()
p.add_argument("--data", default="data/input.txt")
p.add_argument("--out", default="model.pt")
p.add_argument("--steps", type=int, default=3000)
p.add_argument("--batch_size", type=int, default=32)
p.add_argument("--block_size", type=int, default=128)
p.add_argument("--lr", type=float, default=1e-3)
p.add_argument("--eval_every", type=int, default=250)
args = p.parse_args()

torch.manual_seed(1337)
torch.set_num_threads(torch.get_num_threads())

# --- Data: character-level tokenizer ---
text = open(args.data, encoding="utf-8").read()
chars = sorted(set(text))
stoi = {c: i for i, c in enumerate(chars)}
data = torch.tensor([stoi[c] for c in text], dtype=torch.long)
n = int(0.9 * len(data))
train_data, val_data = data[:n], data[n:]
print(f"{len(text):,} characters, vocab size {len(chars)}")


def get_batch(split):
    d = train_data if split == "train" else val_data
    ix = torch.randint(len(d) - args.block_size - 1, (args.batch_size,))
    x = torch.stack([d[i:i + args.block_size] for i in ix])
    y = torch.stack([d[i + 1:i + args.block_size + 1] for i in ix])
    return x, y


@torch.no_grad()
def estimate_loss(model, iters=40):
    model.eval()
    out = {}
    for split in ("train", "val"):
        losses = torch.stack([model(*get_batch(split))[1] for _ in range(iters)])
        out[split] = losses.mean().item()
    model.train()
    return out


# --- Model ---
config = dict(vocab_size=len(chars), block_size=args.block_size)
model = TinyGPT(**config)
print(f"{sum(p.numel() for p in model.parameters()) / 1e6:.2f}M parameters")
opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)


def lr_at(step, warmup=100):
    if step < warmup:
        return args.lr * (step + 1) / warmup
    progress = (step - warmup) / max(1, args.steps - warmup)
    return args.lr * (0.1 + 0.9 * 0.5 * (1 + math.cos(math.pi * progress)))


def save():
    torch.save({"model": model.state_dict(), "config": config, "chars": chars}, args.out)


# --- Training loop ---
best_val = float("inf")
t0 = time.time()
for step in range(args.steps + 1):
    if step % args.eval_every == 0 or step == args.steps:
        losses = estimate_loss(model)
        print(f"step {step:5d} | train {losses['train']:.3f} | val {losses['val']:.3f} | {time.time() - t0:.0f}s", flush=True)
        if losses["val"] < best_val:
            best_val = losses["val"]
            save()
    if step == args.steps:
        break
    for g in opt.param_groups:
        g["lr"] = lr_at(step)
    _, loss = model(*get_batch("train"))
    opt.zero_grad(set_to_none=True)
    loss.backward()
    torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
    opt.step()

print(f"done. best val loss {best_val:.3f}, saved to {args.out}")
