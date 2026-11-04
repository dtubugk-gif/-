# TinyGPT

A small GPT-style language model built from scratch in PyTorch. It reads text one
character at a time, learns to predict the next character, and can then write
new text in the same style.

- `model.py`: the transformer (4 layers, 6 attention heads, about 1.8M parameters)
- `train.py`: the training loop (character tokenizer, train/validation split, AdamW, cosine learning rate)
- `generate.py`: loads a trained model and generates text from a prompt

It runs on a CPU, with no GPU needed.

## Quick start

```bash
pip install numpy torch --index-url https://download.pytorch.org/whl/cpu

# Training data: ~1MB of Shakespeare (any .txt file works)
mkdir -p data
curl -L -o data/input.txt https://raw.githubusercontent.com/karpathy/char-rnn/master/data/tinyshakespeare/input.txt

python train.py --steps 3000          # ~20 min on 4 CPU cores; writes model.pt
python generate.py --prompt "ROMEO:" --length 500
```

## Train on your own text

Point `--data` at any UTF-8 text file (song lyrics, code, your own writing):

```bash
python train.py --data data/my_text.txt --steps 3000 --out my_model.pt
python generate.py --ckpt my_model.pt --prompt "Once upon a time"
```

Useful `generate.py` options: `--temperature` (lower is safer, higher is more
creative; default 0.8), `--top_k`, `--length`, `--seed`.
