"""Generates generic cartoon faces used when no real photos are supplied.
Real photos go in .local-faces/hero.png and .local-faces/enemy.png (git-ignored);
tools/build-apk.sh copies them over these placeholders for the local build."""
from PIL import Image, ImageDraw

S = 4  # supersample

def make(path, w, h, skin, hair, angry):
    im = Image.new('RGBA', (w * S, h * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    W, H = w * S, h * S
    d.ellipse((0, 0, W - 1, H - 1), fill=skin, outline=(40, 25, 20, 255), width=3 * S)
    d.pieslice((0, 0, W - 1, H - 1), 180, 360, fill=hair)
    d.rectangle((0, H * 0.28, W, H * 0.32), fill=skin)
    d.ellipse((0, 0, W - 1, H - 1), outline=(40, 25, 20, 255), width=3 * S)
    ey = H * 0.5
    for ex in (W * 0.32, W * 0.68):
        d.ellipse((ex - 9 * S, ey - 9 * S, ex + 9 * S, ey + 9 * S), fill='white', outline=(40, 25, 20, 255), width=2 * S)
        d.ellipse((ex - 4 * S, ey - 4 * S, ex + 4 * S, ey + 4 * S), fill=(30, 20, 15, 255))
    if angry:
        d.line((W * 0.18, H * 0.36, W * 0.42, H * 0.44), fill=(40, 25, 20, 255), width=4 * S)
        d.line((W * 0.82, H * 0.36, W * 0.58, H * 0.44), fill=(40, 25, 20, 255), width=4 * S)
        d.arc((W * 0.3, H * 0.72, W * 0.7, H * 0.95), 200, 340, fill=(40, 25, 20, 255), width=4 * S)
    else:
        d.arc((W * 0.28, H * 0.55, W * 0.72, H * 0.85), 20, 160, fill=(40, 25, 20, 255), width=4 * S)
    m = Image.new('L', (W, H), 0)
    ImageDraw.Draw(m).ellipse((0, 0, W - 1, H - 1), fill=255)
    im.putalpha(m)
    im.resize((w, h), Image.LANCZOS).save(path)

make('web/assets/hero.png', 96, 143, (255, 205, 160, 255), (70, 45, 30, 255), False)
make('web/assets/enemy.png', 96, 135, (255, 185, 150, 255), (200, 150, 70, 255), True)
