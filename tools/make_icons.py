"""Builds Android launcher icons into build/res from the staged hero/enemy faces."""
import os
from PIL import Image, ImageDraw

root = os.path.join(os.path.dirname(__file__), '..')
hero = Image.open(os.path.join(root, 'build/web/assets/hero.png')).convert('RGBA')
enemy = Image.open(os.path.join(root, 'build/web/assets/enemy.png')).convert('RGBA')
out = os.path.join(root, 'build/res')

def fit(im, h):
    return im.resize((round(im.width * h / im.height), h), Image.LANCZOS)

def outline(im):
    pad = max(2, im.height // 28)
    big = Image.new('RGBA', (im.width + pad * 2, im.height + pad * 2), (0, 0, 0, 0))
    ring = Image.new('RGBA', big.size, (0, 0, 0, 0)); d = ImageDraw.Draw(ring)
    d.ellipse((0, 0, big.width - 1, big.height - 1), fill=(26, 15, 12, 255))
    big.alpha_composite(ring); big.alpha_composite(im, (pad, pad)); return big

def art(size, bg):
    """Heads composition on a (possibly transparent) square of `size` px; kept inside the 66% safe zone."""
    im = Image.new('RGBA', (size, size), bg)
    h = outline(fit(hero, int(size * 0.50))); e = outline(fit(enemy, int(size * 0.30)))
    im.alpha_composite(h, (int(size * 0.20), int(size * 0.20)))
    im.alpha_composite(e, (int(size * 0.52), int(size * 0.46)))
    return im

def gradient(size):
    g = Image.new('RGBA', (size, size)); d = ImageDraw.Draw(g)
    for y in range(size):
        t = y / size; d.line((0, y, size, y), fill=(int(61 + 140 * t), int(155 + 75 * t), 255, 255))
    d.rectangle((0, int(size * 0.84), size, size), fill=(95, 211, 95, 255)); d.rectangle((0, int(size * 0.92), size, size), fill=(184, 105, 58, 255))
    return g

os.makedirs(os.path.join(out, 'drawable-xxxhdpi'), exist_ok=True)
art(432, (0, 0, 0, 0)).save(os.path.join(out, 'drawable-xxxhdpi/ic_launcher_foreground.png'))
for name, px in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    d = os.path.join(out, 'mipmap-' + name); os.makedirs(d, exist_ok=True)
    base = gradient(px * 4); base.alpha_composite(art(px * 4, (0, 0, 0, 0)))
    m = Image.new('L', (px * 4, px * 4), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, px * 4 - 1, px * 4 - 1), radius=px * 4 // 5, fill=255)
    base.putalpha(m); base.resize((px, px), Image.LANCZOS).save(os.path.join(d, 'ic_launcher.png'))
print('icons ok')
