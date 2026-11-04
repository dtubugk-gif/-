"""Generate text from a trained model.  Usage: python generate.py --prompt "ROMEO:" --length 500"""
import argparse

import torch

from model import TinyGPT

p = argparse.ArgumentParser()
p.add_argument("--ckpt", default="model.pt")
p.add_argument("--prompt", default="\n")
p.add_argument("--length", type=int, default=500)
p.add_argument("--temperature", type=float, default=0.8)
p.add_argument("--top_k", type=int, default=40)
p.add_argument("--seed", type=int, default=None)
args = p.parse_args()

if args.seed is not None:
    torch.manual_seed(args.seed)

ckpt = torch.load(args.ckpt, map_location="cpu")
chars = ckpt["chars"]
stoi = {c: i for i, c in enumerate(chars)}
model = TinyGPT(**ckpt["config"])
model.load_state_dict(ckpt["model"])
model.eval()

prompt = "".join(c for c in args.prompt if c in stoi) or "\n"
idx = torch.tensor([[stoi[c] for c in prompt]], dtype=torch.long)
out = model.generate(idx, args.length, temperature=args.temperature, top_k=args.top_k)
print("".join(chars[i] for i in out[0].tolist()))
