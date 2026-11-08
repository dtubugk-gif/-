import math, random
import numpy as np, cv2
from PIL import Image, ImageDraw, ImageFont, ImageOps, ImageEnhance, ImageFilter

random.seed(7); np.random.seed(7)
D = __import__('os').path.dirname(__import__('os').path.abspath(__file__)) + '/'
ANTON = D + 'fonts/SecularOne-Regular.ttf'
MARKER = D + 'fonts/Karantina-Bold.ttf'
EMOJI = '/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf'

# ---------- source: clean cutout ----------
cut = Image.open(D + 'cut.png').convert('RGBA')
a = np.array(cut)
m = (a[..., 3] > 128).astype(np.uint8)
n, lab, stats, _ = cv2.connectedComponentsWithStats(m)
big = 1 + np.argmax(stats[1:, cv2.CC_STAT_AREA])
keep = (lab == big).astype(np.uint8)
keep = cv2.morphologyEx(keep, cv2.MORPH_OPEN, np.ones((25, 25), np.uint8))
n, lab, stats, _ = cv2.connectedComponentsWithStats(keep)
keep = (lab == 1 + np.argmax(stats[1:, cv2.CC_STAT_AREA])).astype(np.uint8)
a[..., 3] = np.minimum(a[..., 3], keep * 255)
BASE = Image.fromarray(a)

EYE_L, EYE_R = (670, 790), (1130, 816)
NOSE = (920, 1050)
MOUTH = (900, 1320)


# ---------- helpers ----------
def remap(img, fx, fy):
    arr = np.array(img).astype(np.float32)
    out = cv2.remap(arr, fx.astype(np.float32), fy.astype(np.float32), cv2.INTER_LINEAR,
                    borderMode=cv2.BORDER_CONSTANT, borderValue=0)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def grid(img):
    h, w = img.height, img.width
    return np.meshgrid(np.arange(w, dtype=np.float32), np.arange(h, dtype=np.float32))


def bulge(img, c, R, a):
    """a>0 magnify, a<0 pinch"""
    X, Y = grid(img)
    dx, dy = X - c[0], Y - c[1]
    d = np.sqrt(dx * dx + dy * dy) + 1e-6
    f = np.where(d < R, (d / R) ** a, 1.0)
    return remap(img, c[0] + dx * f, c[1] + dy * f)


def stretch(img, c, rx, ry, sx, sy):
    """local elliptical scale around c (sx,sy>1 enlarge) with smooth falloff"""
    X, Y = grid(img)
    dx, dy = X - c[0], Y - c[1]
    r = np.sqrt((dx / rx) ** 2 + (dy / ry) ** 2)
    w = np.clip(1 - r, 0, 1) ** 2 * (3 - 2 * np.clip(1 - r, 0, 1))
    return remap(img, c[0] + dx / (1 + (sx - 1) * w), c[1] + dy / (1 + (sy - 1) * w))


def shift(img, c, R, vx, vy):
    """push pixels near c by (vx,vy)"""
    X, Y = grid(img)
    d = np.sqrt((X - c[0]) ** 2 + (Y - c[1]) ** 2)
    w = np.clip(1 - d / R, 0, 1) ** 2
    return remap(img, X - vx * w, Y - vy * w)


def lofi(img, posterize=4, sat=1.35, con=1.25, grain=14, tint=None):
    rgb = img.convert('RGB')
    rgb = ImageEnhance.Color(rgb).enhance(sat)
    rgb = ImageEnhance.Contrast(rgb).enhance(con)
    if tint:
        rgb = Image.blend(rgb, Image.new('RGB', rgb.size, tint), 0.22)
    rgb = ImageOps.posterize(rgb, posterize)
    arr = np.array(rgb).astype(np.int16)
    arr += np.random.randint(-grain, grain + 1, arr.shape[:2])[..., None]
    out = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).convert('RGBA')
    out.putalpha(img.getchannel('A'))
    return out


def emoji(ch, size):
    f = ImageFont.truetype(EMOJI, 109)
    im = Image.new('RGBA', (160, 160))
    ImageDraw.Draw(im).text((10, 10), ch, font=f, embedded_color=True)
    im = im.crop(im.getbbox())
    s = size / max(im.size)
    return im.resize((max(1, int(im.width * s)), max(1, int(im.height * s))), Image.LANCZOS)


def text(s, size, font=ANTON, fill='white', stroke='black', sw=None, angle=0):
    f = ImageFont.truetype(font, size)
    sw = sw if sw is not None else max(4, size // 9)
    tmp = ImageDraw.Draw(Image.new('RGBA', (1, 1)))
    l, t, r, b = tmp.textbbox((0, 0), s, font=f, stroke_width=sw, direction='rtl', language='he')
    im = Image.new('RGBA', (r - l + 20, b - t + 20))
    ImageDraw.Draw(im).text((10 - l, 10 - t), s, font=f, fill=fill, stroke_width=sw, stroke_fill=stroke, direction='rtl', language='he')
    return im.rotate(angle, expand=True, resample=Image.BICUBIC) if angle else im


def paste(canvas, im, cx, cy, angle=0):
    if angle:
        im = im.rotate(angle, expand=True, resample=Image.BICUBIC)
    canvas.alpha_composite(im, (int(cx - im.width / 2), int(cy - im.height / 2)))


def subject(img, height, angle=0, crop=None):
    if crop:
        img = img.crop(crop)
    img = img.crop(img.getbbox())
    s = height / img.height
    img = img.resize((int(img.width * s), height), Image.LANCZOS)
    if angle:
        img = img.rotate(angle, expand=True, resample=Image.BICUBIC)
    return img, s


def scissor_border(canvas, width=26, eps=5.0):
    """white, slightly jagged hand-cut border around every element"""
    al = np.array(canvas.getchannel('A'))
    pad = width * 3
    al = np.pad(al, pad)
    m = (al > 40).astype(np.uint8)
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * width + 1, 2 * width + 1))
    m = cv2.dilate(m, k)
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (171, 171)))
    cs, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    shape = np.zeros_like(m)
    for c in cs:
        ap = cv2.approxPolyDP(c, eps, True)
        ap = ap + np.random.randint(-3, 4, ap.shape)
        cv2.fillPoly(shape, [ap], 255)
    # fill holes
    ff = shape.copy()
    hh, ww = ff.shape
    msk = np.zeros((hh + 2, ww + 2), np.uint8)
    cv2.floodFill(ff, msk, (0, 0), 255)
    shape = shape | cv2.bitwise_not(ff)
    out = Image.new('RGBA', (canvas.width + 2 * pad, canvas.height + 2 * pad), (0, 0, 0, 0))
    white = Image.new('RGBA', out.size, (255, 255, 255, 255))
    out.paste(white, (0, 0), Image.fromarray(shape))
    out.alpha_composite(canvas, (pad, pad))
    return out.crop(out.getbbox())


def tears(img, eye, length, side_spread=60):
    d = ImageDraw.Draw(img)
    for sgn in (-1, 1):
        x0 = eye[0] + sgn * side_spread
        y = eye[1] + 40
        pts = []
        for i in range(0, length, 10):
            wob = 18 * math.sin(i / 45.0 + sgn)
            pts.append((x0 + sgn * i * 0.22 + wob, y + i))
        for wdt, col in ((105, (30, 100, 220, 240)), (78, (100, 185, 255, 245)), (22, (235, 250, 255, 255))):
            d.line(pts, fill=col, width=wdt, joint='curve')
        ex, ey = pts[-1]
        d.ellipse((ex - 45, ey - 30, ex + 45, ey + 40), fill=(80, 165, 250, 240))
    return img


def googly(img, c, r, look):
    d = ImageDraw.Draw(img)
    d.ellipse((c[0] - r - 8, c[1] - r - 8, c[0] + r + 8, c[1] + r + 8), fill='black')
    d.ellipse((c[0] - r, c[1] - r, c[0] + r, c[1] + r), fill='white')
    pr = int(r * 0.48)
    px, py = c[0] + look[0] * (r - pr - 6), c[1] + look[1] * (r - pr - 6)
    d.ellipse((px - pr, py - pr, px + pr, py + pr), fill='black')
    d.ellipse((px - pr * 0.25 - pr * 0.4, py - pr * 0.5, px + pr * 0.25 - pr * 0.4, py), fill='white')


def place_on(size=1100):
    return Image.new('RGBA', (size, size), (0, 0, 0, 0))


stickers = []

# 1 ── CRYING: sobbing, blue tint, tear waterfalls, frown warp
im = BASE.copy()
im = stretch(im, MOUTH, 330, 200, 1.15, 1.45)
im = shift(im, (700, 1300), 160, -10, 55)
im = shift(im, (1100, 1310), 160, 10, 55)
im = shift(im, ((EYE_L[0] + EYE_R[0]) // 2, 700), 380, 0, -40)  # raised worried brows
im = lofi(im, tint=(70, 120, 255))
tears(im, EYE_L, 720); tears(im, EYE_R, 680)
s1, _ = subject(im, 860, angle=-6, crop=(0, 0, 1871, 2200))
c = place_on(); paste(c, s1, 550, 470)
paste(c, text("זהו. נגמר.", 160, sw=14, angle=4), 540, 900)
paste(c, emoji('😭', 170), 165, 190, 15)
paste(c, emoji('💋', 140), 920, 200, -20)
stickers.append(c)

# 2 ── CONFUSION: tilted head, mismatched eyes, floating ???
im = BASE.copy()
im = bulge(im, EYE_R, 190, 0.55)
im = bulge(im, EYE_L, 150, -0.35)
im = shift(im, (1130, 690), 220, 0, -70)  # one eyebrow way up
im = shift(im, (1050, 1315), 170, 0, -30)  # lopsided mouth
im = lofi(im)
s2, _ = subject(im, 800, angle=24, crop=(0, 0, 1871, 2100))
c = place_on(); paste(c, s2, 520, 520)
paste(c, text('?', 260, sw=18, fill='#ffe600', angle=-15), 900, 210)
paste(c, text('?', 170, sw=14, fill='#ff4fa3', angle=20), 800, 90)
paste(c, text('?', 130, sw=12, fill='#4fd1ff', angle=-30), 975, 440)
paste(c, text('אהה??', 230, font=MARKER, sw=14, angle=6), 520, 915)
pk = emoji('💋', 120); pk2 = text('?', 90, sw=10, fill='white')
paste(c, pk, 140, 170, 25); paste(c, pk2, 200, 115)
stickers.append(c)

# 3 ── SHOCK: bug eyes, jaw drop, impact lines
im = BASE.copy()
im = bulge(im, EYE_L, 230, 0.75)
im = bulge(im, EYE_R, 230, 0.75)
im = stretch(im, (900, 1380), 420, 330, 1.25, 2.3)  # jaw drops
d = ImageDraw.Draw(im)
d.ellipse((780, 1290, 1020, 1560), fill=(55, 12, 18, 255))  # gaping mouth
d.ellipse((835, 1460, 965, 1545), fill=(205, 70, 90, 255))
im = lofi(im, sat=1.5, con=1.35)
s3, _ = subject(im, 800, crop=(0, 0, 1871, 2000))
c = place_on()
dd = ImageDraw.Draw(c)
for k in range(14):
    ang = k / 14 * 2 * math.pi + 0.2
    r0, r1 = 400, 520
    dd.polygon([(550 + r0 * math.cos(ang - .05), 470 + r0 * math.sin(ang - .05)),
                (550 + r1 * math.cos(ang), 470 + r1 * math.sin(ang)),
                (550 + r0 * math.cos(ang + .05), 470 + r0 * math.sin(ang + .05))], fill=(20, 20, 20, 255))
paste(c, s3, 550, 500)
paste(c, text('מההה?!', 200, sw=18, fill='#ff2b2b', angle=-5), 550, 900)
paste(c, emoji('😱', 150), 900, 150, -12)
paste(c, emoji('💋', 120), 180, 140, 30)
paste(c, text('!!', 120, sw=12, fill='#ffe600', angle=15), 260, 110)
stickers.append(c)

# 4 ── SMUGNESS: squint, smirk, pixel shades, blown kiss 💋
im = BASE.copy()
im = stretch(im, EYE_L, 200, 110, 1.05, 0.6)
im = stretch(im, EYE_R, 200, 110, 1.05, 0.6)
im = shift(im, (1110, 1310), 150, 25, -55)  # smirk corner up
im = shift(im, (700, 1300), 120, 0, 10)
im = lofi(im, sat=1.4)
d = ImageDraw.Draw(im)
px = 46
gl = ["XXXXXXXXXXXXXXXXXXXXXXXX",
      "X.WWXXXXXXXX..X.WWXXXXXX",
      "XW.XXXXXXXX....XW.XXXXX.",
      "..XXXXXXXX......XXXXXX.."]
ox, oy = 360, 700
for r, row in enumerate(gl):
    for q, ch in enumerate(row):
        if ch != '.':
            col = (255, 255, 255, 255) if ch == 'W' else (8, 8, 8, 255)
            d.rectangle((ox + q * px, oy + r * px, ox + q * px + px - 1, oy + r * px + px - 1), fill=col)
s4, _ = subject(im, 840, angle=-12, crop=(0, 0, 1871, 2150))
c = place_on(); paste(c, s4, 470, 500)
paste(c, emoji('💋', 230), 900, 300, -18)
paste(c, emoji('💋', 120), 1000, 110, 12)
paste(c, emoji('✨', 100), 820, 120, 0)
paste(c, text('מוואה.', 250, font=MARKER, sw=15, fill='#ff3d7f', angle=8), 600, 900)
stickers.append(c)

# 5 ── SIDE-EYE: googly eyes glancing right, flat mouth
im = BASE.copy()
im = stretch(im, MOUTH, 300, 120, 1.25, 0.5)
im = lofi(im, posterize=3, sat=1.2, grain=20)
googly(im, (EYE_L[0] + 15, EYE_L[1]), 150, (1, -0.15))
googly(im, (EYE_R[0] + 10, EYE_R[1]), 150, (1, -0.15))
d = ImageDraw.Draw(im)
d.line([(590, 610), (790, 580)], fill=(40, 25, 20, 255), width=45)  # judgy brows
d.line([(1020, 610), (1250, 650)], fill=(40, 25, 20, 255), width=45)
s5, _ = subject(im, 820, angle=8, crop=(0, 0, 1871, 2100))
c = place_on(); paste(c, s5, 460, 500)
paste(c, emoji('👀', 170), 870, 240, 0)
paste(c, text('אוקיי, ו...?', 220, font=MARKER, sw=15, angle=-5), 560, 905)
paste(c, emoji('💋', 115), 880, 560, 20)
stickers.append(c)

# 6 ── DEADPAN DISBELIEF: long-face stretch, mono, "bro."
im = BASE.copy()
im = stretch(im, EYE_L, 160, 90, 1.0, 0.55)
im = stretch(im, EYE_R, 160, 90, 1.0, 0.55)
im = stretch(im, MOUTH, 280, 110, 1.15, 0.55)
g = ImageOps.grayscale(im.convert('RGB'))
g = ImageOps.autocontrast(g, cutoff=2).convert('RGBA'); g.putalpha(im.getchannel('A'))
im = lofi(g, posterize=3, sat=1.0, con=1.4, grain=22)
im = im.crop((0, 0, 1871, 2150))
im = im.resize((int(im.width * 0.62), int(im.height * 1.15)), Image.LANCZOS)  # long face
s6, _ = subject(im, 870)
c = place_on(); paste(c, s6, 520, 470)
paste(c, text('אחי.', 230, sw=18, fill='white'), 560, 900)
k = emoji('💋', 130).convert('LA').convert('RGBA')
paste(c, k, 880, 230, -10)
paste(c, text('...', 140, sw=12), 880, 120)
stickers.append(c)

# 7 ── AWKWARD POSE: head popping out of a giant 💋 costume
im = BASE.copy()
im = shift(im, (900, 1310), 260, 0, 10)
im = stretch(im, MOUTH, 300, 120, 1.25, 0.6)  # tight awkward grin line
im = lofi(im)
head, _ = subject(im, 470, angle=-10, crop=(120, 0, 1500, 1720))
lips = emoji('💋', 660)
c = place_on()
paste(c, lips, 560, 660, 0)
paste(c, head, 560, 280)
paste(c, text('אני השפתיים עכשיו', 150, font=MARKER, sw=12, angle=-4), 560, 965)
paste(c, emoji('💦', 110), 820, 160, 0)
stickers.append(c)

# 8 ── ABSURD: upside-down head, brain.exe crashed
im = BASE.copy()
im = bulge(im, EYE_L, 170, 0.5)
im = bulge(im, EYE_R, 120, -0.4)
im = lofi(im, posterize=3, sat=1.6, grain=24)
s8, _ = subject(im, 700, angle=170, crop=(0, 0, 1871, 2000))
c = place_on()
win = Image.new('RGBA', (820, 210), (0, 0, 0, 0))
wd = ImageDraw.Draw(win)
wd.rectangle((0, 0, 819, 209), fill=(200, 200, 200, 255), outline='black', width=8)
wd.rectangle((8, 8, 811, 58), fill=(0, 0, 160, 255))
wd.text((795, 8), 'מוח', font=ImageFont.truetype(ANTON, 36), fill='white', anchor='ra', direction='rtl', language='he')
wd.text((410, 72), 'המוח הפסיק להגיב', font=ImageFont.truetype(ANTON, 72), fill='black', anchor='ma', direction='rtl', language='he')
paste(c, s8, 550, 430)
paste(c, win, 550, 870, 3)
paste(c, emoji('💋', 120), 140, 160, 160)
paste(c, emoji('😵', 130), 950, 170, 0)
stickers.append(c)

# 9 ── IT'S FINE: wide-boi stretch, fire, forced smile
im = BASE.copy()
im = shift(im, (700, 1300), 150, -20, -45)
im = shift(im, (1100, 1310), 150, 20, -45)
im = bulge(im, EYE_L, 150, -0.5)
im = bulge(im, EYE_R, 150, -0.5)  # tiny pupils of panic
im = lofi(im, tint=(255, 140, 40), sat=1.5)
im = im.crop((0, 0, 1871, 2150))
im = im.resize((int(im.width * 1.45), int(im.height * 0.78)), Image.LANCZOS)  # wide
s9, _ = subject(im, 640)
c = place_on()
for (x, y, sz, rot) in [(150, 760, 230, -8), (950, 760, 230, 10), (300, 800, 170, 5), (800, 800, 170, -5), (550, 840, 150, 0)]:
    paste(c, emoji('🔥', sz), x, y, rot)
paste(c, s9, 550, 470)
paste(c, text("הכל בסדר.", 220, font=MARKER, sw=14, fill='#ffe600', angle=3), 550, 960)
paste(c, emoji('💋', 120), 960, 150, -15)
paste(c, emoji('🙂', 110), 140, 140, 0)
stickers.append(c)

# ---------- assemble 3x3 sheet ----------
SHEET = 2048
CELL, GAP = 560, 120
margin = (SHEET - 3 * CELL - 2 * GAP) // 2
sheet = Image.new('RGBA', (SHEET, SHEET), (0, 0, 0, 0))
for i, st in enumerate(stickers):
    st = scissor_border(st, width=24, eps=5.5)
    s = min(CELL / st.width, CELL / st.height)
    st = st.resize((int(st.width * s), int(st.height * s)), Image.LANCZOS)
    st.save(D + f'sticker_{i + 1}.png')
    r, q = divmod(i, 3)
    x = margin + q * (CELL + GAP) + (CELL - st.width) // 2
    y = margin + r * (CELL + GAP) + (CELL - st.height) // 2
    sheet.alpha_composite(st, (x, y))
sheet.save(D + 'sticker_sheet.png')

prev = Image.new('RGBA', sheet.size, (60, 60, 60, 255))
ck = 64
pd = ImageDraw.Draw(prev)
for yy in range(0, SHEET, ck):
    for xx in range(0, SHEET, ck):
        if (xx // ck + yy // ck) % 2:
            pd.rectangle((xx, yy, xx + ck - 1, yy + ck - 1), fill=(80, 80, 80, 255))
prev.alpha_composite(sheet)
prev.convert('RGB').resize((1024, 1024), Image.LANCZOS).save(D + 'preview.jpg', quality=90)
print('done', margin)
