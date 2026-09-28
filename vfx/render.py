"""Doctor-Strange style VFX pass over a phone clip.

Pipeline: MediaPipe tracking (analyze.py) -> this script -> ffmpeg.
Effects are keyed to the performer's hands and composited with a person mask,
so the portal opens *behind* him and the sparks stay in front.

Usage: python3 render.py SRC.mp4 WORKDIR OUT_FRAMES.mp4
WORKDIR must contain track.json and masks.npy from analyze.py.
"""
import json
import math
import subprocess
import sys

import cv2
import numpy as np

SRC, WORK, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
W, H, FPS = 1080, 1920, 30
rng = np.random.default_rng(11)

track = json.load(open(f"{WORK}/track.json"))
masks = np.load(f"{WORK}/masks.npy").astype(np.float32)
N = len(track)


# ----------------------------------------------------------------- helpers
def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def ease_out_back(t, s=1.2):
    t = min(max(t, 0.0), 1.0) - 1
    return 1 + (s + 1) * t ** 3 + s * t ** 2


def ease_in_out(t):
    t = min(max(t, 0.0), 1.0)
    return 0.5 - 0.5 * math.cos(math.pi * t)


def gauss1d(a, sigma):
    r = int(3 * sigma) + 1
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma) ** 2)
    k /= k.sum()
    pad = np.pad(a, ((r, r), (0, 0)), mode="edge")
    return np.stack([np.convolve(pad[:, c], k, "valid") for c in range(a.shape[1])], 1)


def fill_nan(a):
    idx = np.arange(len(a))
    out = a.copy()
    for c in range(a.shape[1]):
        ok = ~np.isnan(a[:, c])
        out[:, c] = np.interp(idx, idx[ok], a[ok, c])
    return out


# ----------------------------------------------------------- hand tracks
# Screen-left hand raises the palm (shield); screen-right hand circles (sling ring).
LP, RP, LRING, RTIP = (np.full((N, 2), np.nan) for _ in range(4))
for i, f in enumerate(track):
    hs = sorted(f["hands"], key=lambda h: h["pts"][0][0])
    left = right = None
    if len(hs) >= 2:
        left, right = hs[0], hs[-1]
    elif len(hs) == 1:
        if hs[0]["pts"][0][0] < 600:
            left = hs[0]
        else:
            right = hs[0]
    if left:
        p = np.array(left["pts"])
        LP[i] = p[[0, 5, 9, 13, 17]].mean(0)
        LRING[i] = p[[13, 14]].mean(0)
    if right:
        p = np.array(right["pts"])
        RP[i] = p[[0, 5, 9, 13, 17]].mean(0)
        RTIP[i] = p[[8, 12]].mean(0)
    po = f["pose"]
    if po is not None:  # pose wrists fill frames where the hand model blinked
        if np.isnan(LP[i, 0]) and po[16][2] > 0.5:
            LP[i] = [po[16][0], po[16][1] - 60]
        if np.isnan(RP[i, 0]) and po[15][2] > 0.5:
            RP[i] = [po[15][0], po[15][1] - 60]
LP, RP, LRING, RTIP = (gauss1d(fill_nan(a), 1.3) for a in (LP, RP, LRING, RTIP))
RVEL = np.gradient(RP, axis=0)

# ----------------------------------------------------------------- mask
YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)


def guided(I, p, r, eps):
    m = lambda x: cv2.boxFilter(x, -1, (r, r))
    mI, mp = m(I), m(p)
    a = (m(I * p) - mI * mp) / (m(I * I) - mI * mI + eps)
    b = mp - a * mI
    return m(a) * I + m(b)


def person_mask(i, frame):
    small = cv2.resize(frame, (540, 960), interpolation=cv2.INTER_AREA)
    gray = cv2.cvtColor(small, cv2.COLOR_RGB2GRAY).astype(np.float32) / 255
    m = cv2.resize(masks[i], (540, 960), interpolation=cv2.INTER_LINEAR)
    m = np.clip((m - 0.3) / 0.4, 0, 1)
    m = np.clip(guided(gray, m, 9, 2e-3), 0, 1)
    return cv2.resize(m, (W, H), interpolation=cv2.INTER_LINEAR)


# ------------------------------------------------------ the other world
def make_world(S=1200, seed=5):
    r = np.random.default_rng(seed)
    y = np.linspace(0, 1, S, dtype=np.float32)[:, None]
    x = np.linspace(0, 1, S, dtype=np.float32)[None, :]
    stops = [(0.00, (0.04, 0.06, 0.15)), (0.26, (0.13, 0.15, 0.33)), (0.42, (0.50, 0.33, 0.50)),
             (0.53, (0.96, 0.60, 0.47)), (0.60, (1.00, 0.80, 0.58)), (1.00, (1.00, 0.86, 0.68))]
    ys = [s[0] for s in stops]
    img = np.zeros((S, S, 3), np.float32)
    for c in range(3):
        img[..., c] = np.interp(y[:, 0], ys, [s[1][c] for s in stops])[:, None]
    # stars fading into the dusk
    n = 420
    sx, sy = r.integers(0, S, n), (r.random(n) ** 1.6 * S * 0.4).astype(int)
    img[sy, sx] += (r.random(n) * (1 - sy / (S * 0.4)))[:, None] * 0.9
    # soft cloud band lit by the sun
    noise = np.zeros((S, S), np.float32)
    for o in range(5):
        g = r.random((3 * 2 ** o, 8 * 2 ** o)).astype(np.float32)
        noise += cv2.resize(g, (S, S), interpolation=cv2.INTER_CUBIC) / 2 ** o
    noise /= noise.max()
    band = np.exp(-((y - 0.37) / 0.07) ** 2)
    cl = np.clip((noise - 0.5) * 3.2, 0, 1) * band
    img = img * (1 - cl[..., None] * 0.55) + np.array([1.0, 0.62, 0.55]) * cl[..., None] * 0.55
    # sun
    d2 = (x - 0.62) ** 2 + (y - 0.505) ** 2
    img += np.exp(-d2 / (2 * 0.011 ** 2))[..., None] * np.array([1.0, 0.95, 0.86]) * 1.4
    img += np.exp(-d2 / (2 * 0.09 ** 2))[..., None] * np.array([1.0, 0.68, 0.42]) * 0.35

    def ridge(base, amp, rough):
        n2 = 1025
        h = np.zeros(n2)
        h[0], h[-1] = base + r.uniform(-amp, amp) * 0.4, base + r.uniform(-amp, amp) * 0.4
        step, a = n2 - 1, amp
        while step > 1:
            half = step // 2
            for k in range(half, n2, step):
                h[k] = (h[k - half] + h[k + half]) / 2 + r.uniform(-a, a)
            a *= rough
            step = half
        return np.interp(np.linspace(0, 1, S), np.linspace(0, 1, n2), h).astype(np.float32)

    layers = [(0.50, 0.12, (0.62, 0.52, 0.66), True), (0.57, 0.10, (0.45, 0.37, 0.54), True),
              (0.64, 0.08, (0.29, 0.23, 0.38), True), (0.72, 0.06, (0.16, 0.12, 0.22), False),
              (0.83, 0.05, (0.07, 0.05, 0.10), False)]
    for i, (b, a, col, snow) in enumerate(layers):
        h = ridge(b, a, 0.55)[None, :]
        below = y > h
        depth = y - h
        colr = np.array(col, np.float32) * (1 + 0.35 * np.exp(-depth / 0.012))[..., None]
        if snow:
            peak = np.clip((b - h) / a, 0, 1) ** 1.3
            sm = (depth < 0.006 + 0.05 * peak) * peak
            sm = cv2.GaussianBlur(sm.astype(np.float32), (0, 0), 1.5)
            snow_c = np.array([1.0, 0.9, 0.88]) * (1.0 - 0.18 * i)
            colr = colr * (1 - sm[..., None]) + snow_c * sm[..., None]
        img = np.where(below[..., None], colr, img)
        if i == 3:  # a mountain monastery, lights on
            tx = int(S * 0.30)
            ty = int(h[0, tx] * S) + 4
            dark = tuple(float(v) for v in col)
            for k in range(4):
                w = int(S * 0.07 * (1 - k * 0.2))
                yy = ty - k * int(S * 0.028)
                roof = np.array([[tx - w // 2 - 10, yy - 6], [tx, yy - int(S * 0.022)], [tx + w // 2 + 10, yy - 6],
                                 [tx + int(w * 0.36), yy], [tx - int(w * 0.36), yy]], np.int32)
                cv2.fillPoly(img, [roof], dark, cv2.LINE_AA)
                cv2.rectangle(img, (tx - int(w * 0.3), yy), (tx + int(w * 0.3), yy + int(S * 0.012)), dark, -1)
                for wx in range(tx - int(w * 0.22), tx + int(w * 0.22), 9):
                    cv2.rectangle(img, (wx, yy + 3), (wx + 3, yy + 8), (1.0, 0.72, 0.36), -1)
            cv2.line(img, (tx, ty - 4 * int(S * 0.028) - 10), (tx, ty - 4 * int(S * 0.028) - 40), dark, 3)
        if i < len(layers) - 1:  # aerial haze between ranges
            hz = 0.42 * np.exp(-((y - (b + a * 0.55)) / 0.045) ** 2)
            img = img * (1 - hz[..., None]) + np.array([0.98, 0.72, 0.62]) * hz[..., None]
    return np.clip(img, 0, 1.6).astype(np.float32)


WORLD = make_world()
cv2.imwrite(f"{WORK}/world_preview.jpg", (np.clip(WORLD[..., ::-1], 0, 1) * 255).astype(np.uint8))


# --------------------------------------------------------------- mandala
def build_mandala(R=230):
    S = int(R * 2 * 1.18) | 1
    c = S // 2
    r2 = np.random.default_rng(3)
    cen = (c, c)
    th = 3

    def circ(img, rad, t=th):
        cv2.circle(img, cen, int(rad), 255, t, cv2.LINE_AA)

    def poly(img, n, rad, rot):
        a = rot + np.arange(n + 1) * 2 * np.pi / n
        pts = np.stack([c + np.cos(a) * rad, c + np.sin(a) * rad], 1).astype(np.int32)
        cv2.polylines(img, [pts], False, 255, th, cv2.LINE_AA)

    layers = []
    L = np.zeros((S, S), np.uint8)  # outer rune band
    circ(L, R)
    circ(L, R * 0.87)
    grid = [(-1, -1), (0, -1), (1, -1), (-1, 0), (0, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]
    for k in range(30):
        a = k * 2 * np.pi / 30
        gx, gy = c + np.cos(a) * R * 0.935, c + np.sin(a) * R * 0.935
        s = R * 0.03
        ca, sa = np.cos(a + np.pi / 2), np.sin(a + np.pi / 2)
        pts = [grid[i] for i in r2.integers(0, 9, r2.integers(3, 6))]
        q = np.array([[gx + (px * ca - py * sa) * s, gy + (px * sa + py * ca) * s] for px, py in pts], np.int32)
        cv2.polylines(L, [q], False, 255, 2, cv2.LINE_AA)
    layers.append((L, 22.0))
    L = np.zeros((S, S), np.uint8)  # octagram
    poly(L, 4, R * 0.84, 0)
    poly(L, 4, R * 0.84, np.pi / 4)
    circ(L, R * 0.6)
    layers.append((L, -34.0))
    L = np.zeros((S, S), np.uint8)  # dial
    circ(L, R * 0.54)
    for k in range(72):
        a = k * 2 * np.pi / 72
        ln = R * (0.075 if k % 6 == 0 else 0.035)
        p0 = (int(c + np.cos(a) * R * 0.54), int(c + np.sin(a) * R * 0.54))
        p1 = (int(c + np.cos(a) * (R * 0.54 - ln)), int(c + np.sin(a) * (R * 0.54 - ln)))
        cv2.line(L, p0, p1, 255, 2, cv2.LINE_AA)
    for k in range(8):
        a = k * 2 * np.pi / 8 + np.pi / 8
        cv2.circle(L, (int(c + np.cos(a) * R * 0.71), int(c + np.sin(a) * R * 0.71)), int(R * 0.045), 255, 2, cv2.LINE_AA)
    layers.append((L, 58.0))
    L = np.zeros((S, S), np.uint8)  # hexagram core
    poly(L, 3, R * 0.44, -np.pi / 2)
    poly(L, 3, R * 0.44, np.pi / 2)
    circ(L, R * 0.24)
    circ(L, R * 0.09)
    layers.append((L, -90.0))
    L = np.zeros((S, S), np.uint8)  # dashed halo
    for k in range(48):
        a0 = k * 2 * np.pi / 48
        cv2.ellipse(L, cen, (int(R * 1.07), int(R * 1.07)), 0, np.degrees(a0), np.degrees(a0) + 3.2, 255, 2, cv2.LINE_AA)
    layers.append((L, 8.0))
    return S, [(l.astype(np.float32) / 255, sp) for l, sp in layers]


MANDALA_S, MANDALA = build_mandala()
GOLD = np.array([1.0, 0.66, 0.24], np.float32)


def add_patch(buf, patch, cx, cy):
    h, w = patch.shape[:2]
    x0, y0 = int(round(cx - w / 2)), int(round(cy - h / 2))
    xa, ya, xb, yb = max(x0, 0), max(y0, 0), min(x0 + w, W), min(y0 + h, H)
    if xa >= xb or ya >= yb:
        return
    buf[ya:yb, xa:xb] += patch[ya - y0:yb - y0, xa - x0:xb - x0]


def draw_mandala(buf, cx, cy, t_local, scale, alpha):
    if alpha <= 0.01 or scale <= 0.02:
        return
    S = MANDALA_S
    out = int(S * scale) | 1
    acc = np.zeros((out, out), np.float32)
    for k, (lay, speed) in enumerate(MANDALA):
        M = cv2.getRotationMatrix2D((S / 2, S / 2), speed * t_local + k * 17, scale)
        M[:, 2] += (out - S) / 2
        flick = 0.85 + 0.15 * math.sin(t_local * 11 + k * 1.7)
        acc += cv2.warpAffine(lay, M, (out, out), flags=cv2.INTER_LINEAR) * flick
    add_patch(buf, acc[..., None] * GOLD * (1.7 * alpha), cx, cy)


# -------------------------------------------------------------- particles
class Sparks:
    RAMP_T = np.array([0.0, 0.12, 0.4, 0.75, 1.0])
    RAMP_C = np.array([[1.0, 0.97, 0.9], [1.0, 0.82, 0.45], [1.0, 0.52, 0.14], [0.85, 0.24, 0.05], [0.4, 0.08, 0.02]])
    RAMP_I = np.array([2.4, 1.9, 1.3, 0.7, 0.0])

    def __init__(self):
        self.a = np.zeros((0, 10), np.float32)  # x y vx vy age life grav drag layer thick

    def emit(self, x, y, vx, vy, life, grav=0.5, drag=0.93, layer=1, thick=2):
        n = len(np.atleast_1d(x))
        if n == 0:
            return
        b = np.zeros((n, 10), np.float32)
        for j, v in enumerate((x, y, vx, vy, 0, life, grav, drag, layer, thick)):
            b[:, j] = v
        self.a = np.concatenate([self.a, b])

    def step(self):
        a = self.a
        a[:, 2] *= a[:, 7]
        a[:, 3] = a[:, 3] * a[:, 7] + a[:, 6]
        a[:, 0] += a[:, 2]
        a[:, 1] += a[:, 3]
        a[:, 4] += 1
        self.a = a[a[:, 4] < a[:, 5]]

    def draw(self, back, front):
        a = self.a
        if not len(a):
            return
        f = a[:, 4] / a[:, 5]
        inten = np.interp(f, self.RAMP_T, self.RAMP_I)
        col = np.stack([np.interp(f, self.RAMP_T, self.RAMP_C[:, c]) for c in range(3)], 1) * inten[:, None]
        x0 = (a[:, 0] - a[:, 2] * 1.7).astype(np.int32)
        y0 = (a[:, 1] - a[:, 3] * 1.7).astype(np.int32)
        x1, y1 = a[:, 0].astype(np.int32), a[:, 1].astype(np.int32)
        for k in range(len(a)):
            buf = front if a[k, 8] > 0.5 else back
            cv2.line(buf, (int(x0[k]), int(y0[k])), (int(x1[k]), int(y1[k])), tuple(col[k].tolist()), int(a[k, 9]))


SP = Sparks()


def rim_sparks(cx, cy, r, a_from, a_to, n, direction, layer, onscreen_only=False, life_k=1.0):
    if n <= 0:
        return
    ang = a_from + (a_to - a_from) * rng.random(n)
    px, py = cx + np.cos(ang) * r, cy + np.sin(ang) * r
    if onscreen_only:
        ok = (px > -40) & (px < W + 40) & (py > -40) & (py < H + 40)
        ang, px, py = ang[ok], px[ok], py[ok]
        n = len(ang)
        if n == 0:
            return
    tx, ty = -np.sin(ang) * direction, np.cos(ang) * direction
    drip = rng.random(n) < 0.12
    sp = np.where(drip, rng.uniform(2, 6, n), rng.uniform(7, 21, n))
    out = rng.uniform(0.3, 4.0, n)
    SP.emit(px, py, tx * sp + np.cos(ang) * out, ty * sp + np.sin(ang) * out,
            np.where(drip, rng.uniform(24, 42, n), rng.uniform(9, 22, n)) * life_k,
            np.where(drip, 1.0, 0.55), np.where(drip, 0.975, 0.925), layer,
            np.where(drip | (rng.random(n) < 0.3), 3, 2))


def burst(x, y, n, smin, smax, layer=1, grav=0.45, life=(12, 30)):
    a = rng.uniform(0, 2 * np.pi, n)
    s = rng.uniform(smin, smax, n)
    SP.emit(np.full(n, x), np.full(n, y), np.cos(a) * s, np.sin(a) * s, rng.uniform(*life, n), grav, 0.93, layer,
            np.where(rng.random(n) < 0.3, 3, 2))


def draw_ring(buf, cx, cy, r, t, a_from, a_to, gain=1.0, seed=0.0):
    if r < 2:
        return
    n = max(48, int(r * 0.45 * abs(a_to - a_from) / (2 * np.pi)))
    ang = np.linspace(a_from, a_to, n)
    for thick, col, jit in ((16, (1.0, 0.38, 0.07), 6.0), (7, (1.0, 0.6, 0.18), 3.5), (2, (1.0, 0.93, 0.78), 1.6)):
        wob = (np.sin(ang * 3 + t * 13 + seed) * 0.5 + np.sin(ang * 7 - t * 23 + seed * 2.1) * 0.3
               + np.sin(ang * 17 + t * 31 + seed * 0.7) * 0.2)
        rr = r + wob * jit
        pts = np.stack([cx + np.cos(ang) * rr, cy + np.sin(ang) * rr], 1).astype(np.int32)
        k = {16: 0.32, 7: 0.9, 2: 2.3}[thick] * gain
        cv2.polylines(buf, [pts], False, tuple(c * k for c in col), thick)


# --------------------------------------------------------------- timeline
T_SHIELD_IN, T_SHIELD_OUT, T_SHIELD_END = 0.55, 1.78, 2.05
T_TRACE0, T_TRACE1, T_GROW1 = 2.35, 2.95, 3.9
T_HANDS_END = 6.3
T_IRIS0, T_IRIS1, T_END = 7.1, 7.8, N / FPS
C_PORTAL, R_PORTAL = np.array([560.0, 1000.0]), 470.0
R_IRIS = 1180.0

i_trace = int(T_TRACE0 * FPS)
mid0 = (LP[i_trace] + RP[i_trace]) / 2
r_trace = 0.45 * np.linalg.norm(RP[i_trace] - LP[i_trace])
a_trace0 = math.atan2(RP[i_trace][1] - mid0[1], RP[i_trace][0] - mid0[0])

VIG = ((XX - W / 2) / (W * 0.62)) ** 2 + ((YY - H / 2) / (H * 0.62)) ** 2
LUMA = np.array([0.299, 0.587, 0.114], np.float32)


def grade(f, m, g):
    lum = f @ LUMA
    f = lum[..., None] + (f - lum[..., None]) * (1 - 0.3 * g)
    f = f * (1 - g * (0.6 * (1 - m) + 0.28 * m))[..., None]
    f = np.power(np.clip(f, 0, 1), 1 + 0.2 * g)
    lum = f @ LUMA
    f = f + g * (1 - lum)[..., None] * np.array([-0.018, 0.008, 0.03], np.float32)
    f = f * (1 - g * 0.5 * np.clip(VIG, 0, 1.4))[..., None]
    return np.clip(f, 0, 1)


def portal_state(t):
    """centre, radius, arc, occlusion blend (1 = in front of him), interior opacity"""
    if t < T_TRACE0 or t > T_IRIS1:
        return None
    if t < T_TRACE1:
        p = ease_in_out((t - T_TRACE0) / (T_TRACE1 - T_TRACE0))
        i = min(int(t * FPS), N - 1)
        mid = (LP[i] + RP[i]) / 2 * 0.5 + mid0 * 0.5
        return mid, r_trace, (a_trace0, a_trace0 + 2 * np.pi * p), 1.0, 0.0
    p = (t - T_TRACE1) / (T_GROW1 - T_TRACE1)
    e = ease_out_back(p, 0.9)
    c = mid0 + (C_PORTAL - mid0) * ease_in_out(p)
    r = r_trace + (R_PORTAL - r_trace) * e
    if p >= 1:
        r = R_PORTAL * (1 + 0.012 * math.sin(t * 2.7))
    front = float(1 - smoothstep(150, 290, r))
    return c, r, (0.0, 2 * np.pi), front, float(smoothstep(170, 340, r))


# ----------------------------------------------------------------- render
dec = subprocess.Popen(["ffmpeg", "-loglevel", "error", "-i", SRC, "-vf", "fps=30", "-f", "rawvideo",
                        "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
enc = subprocess.Popen(["ffmpeg", "-loglevel", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24",
                        "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-preset", "slow",
                        "-crf", "16", "-pix_fmt", "yuv420p", OUT], stdin=subprocess.PIPE)
prev_mask = None
for i in range(N):
    buf = dec.stdout.read(W * H * 3)
    if len(buf) < W * H * 3:
        break
    t = i / FPS
    frame = np.frombuffer(buf, np.uint8).reshape(H, W, 3)
    f = frame.astype(np.float32) / 255
    m = person_mask(i, frame)
    if prev_mask is not None:
        m = 0.75 * m + 0.25 * prev_mask
    prev_mask = m

    g = float(smoothstep(0.25, 1.0, t))
    base = grade(f, m, g)
    ringB = np.zeros((H, W, 3), np.float32)
    ringF = np.zeros_like(ringB)
    partB = np.zeros_like(ringB)
    partF = np.zeros_like(ringB)

    # 1. palm raise: sparks trail the rising hand, then the shield blooms
    if 0.32 < t < T_SHIELD_IN + 0.1:
        n = 22
        SP.emit(LP[i, 0] + rng.normal(0, 25, n), LP[i, 1] + rng.normal(0, 25, n), rng.normal(0, 3, n),
                rng.normal(-2, 3, n), rng.uniform(8, 18, n), 0.25, 0.92, 1, 2)
    if T_SHIELD_IN <= t < T_SHIELD_END:
        tl = t - T_SHIELD_IN
        sc = ease_out_back(tl / 0.32, 1.4)
        al = min(1.0, tl / 0.12)
        if t > T_SHIELD_OUT:
            q = (t - T_SHIELD_OUT) / (T_SHIELD_END - T_SHIELD_OUT)
            sc *= 1 + 0.5 * q
            al *= 1 - q
        draw_mandala(partF, LP[i, 0], LP[i, 1], tl, sc * 0.95, al)
        rim_sparks(LP[i, 0], LP[i, 1], 230 * sc * 0.95, 0, 2 * np.pi, int(34 * al), -1, 1)
        if abs(t - (T_SHIELD_OUT + 0.1)) < 0.5 / FPS:
            burst(LP[i, 0], LP[i, 1], 260, 6, 24)

    # 2. sling-ring hand: grinding sparks and a glowing ring finger
    if 2.15 < t < T_HANDS_END:
        k = float(smoothstep(2.15, 2.4, t) * (1 - smoothstep(T_HANDS_END - 0.3, T_HANDS_END, t)))
        n = int(26 * k)
        v = RVEL[i]
        perp = np.array([-v[1], v[0]])
        SP.emit(RTIP[i, 0] + rng.normal(0, 10, n), RTIP[i, 1] + rng.normal(0, 10, n),
                rng.normal(0, 7, n) + perp[0] * 0.6, rng.normal(-1, 7, n) + perp[1] * 0.6,
                rng.uniform(7, 17, n), 0.5, 0.92, 1, 2)
        cv2.circle(partF, (int(LRING[i, 0]), int(LRING[i, 1])), 7, tuple((GOLD * 2.2 * k).tolist()), -1)

    # 3. portal: traced between the hands, then thrown open behind him
    ps = portal_state(t)
    if ps is not None:
        (cx, cy), r, (a0, a1), front, interior = ps
        if interior > 0:
            d = np.sqrt((XX - cx) ** 2 + (YY - cy) ** 2)
            inside = np.clip((r - d) / 3 + 0.5, 0, 1) * interior * (1 - m)
            zoom = 1.0 + 0.05 * max(0.0, t - T_TRACE1) / 4
            sc = (R_PORTAL * 2 * 1.2 / WORLD.shape[1]) * zoom
            M = np.array([[sc, 0, C_PORTAL[0] - WORLD.shape[1] * sc / 2 + 6 * math.sin(t * 0.7)],
                          [0, sc, C_PORTAL[1] - WORLD.shape[0] * sc / 2 - 12 * min(1, t - T_TRACE1)]], np.float32)
            world = cv2.warpAffine(WORLD, M, (W, H), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT)
            shade = 0.5 + 0.5 * np.clip((r - d) / (0.22 * r), 0, 1)
            world = world * shade[..., None]
            base = base * (1 - inside[..., None]) + world * inside[..., None]
        head = a1 if a1 - a0 < 2 * np.pi - 1e-3 else None
        rb = ringF if front > 0.5 else ringB
        draw_ring(rb, cx, cy, r, t, a0, a1, 1.0)
        layer = 1 if front > 0.5 else 0
        rim_sparks(cx, cy, r, a0, a1, int(0.085 * r * (a1 - a0)), 1, layer)
        if head is not None:
            hx, hy = cx + math.cos(head) * r, cy + math.sin(head) * r
            cv2.circle(ringF, (int(hx), int(hy)), 10, (2.6, 2.3, 1.8), -1)
            burst(hx, hy, 30, 4, 16)
        if abs(t - T_TRACE1) < 0.5 / FPS:
            burst(cx, cy, 180, 8, 30, layer=1)

    # 4. iris out: a sling-ring circle closes the frame
    iris = None
    if t >= T_IRIS0:
        p = min(1.0, (t - T_IRIS0) / (T_IRIS1 - T_IRIS0))
        ri = R_IRIS * (1 - p ** 2.2)
        iris = ri
        if ri > 3:
            draw_ring(ringF, W / 2, H / 2, ri, t, 0, 2 * np.pi, 1.25, seed=4.0)
            rim_sparks(W / 2, H / 2, ri, 0, 2 * np.pi, int(0.05 * ri * 2 * np.pi), -1, 1, onscreen_only=True, life_k=0.45)
        if abs(t - T_IRIS1) < 0.5 / FPS:
            burst(W / 2, H / 2, 700, 6, 42, life=(14, 40))
        if T_IRIS1 <= t < T_IRIS1 + 0.35:
            fl = (1 - (t - T_IRIS1) / 0.35) ** 2
            partF += np.exp(-((XX - W / 2) ** 2 + (YY - H / 2) ** 2) / (2 * 260 ** 2))[..., None] * (np.array([2.5, 1.6, 0.8], np.float32) * fl)

    # 5. ambient embers once the magic starts
    if 0.8 < t < T_IRIS1:
        n = rng.poisson(1.2)
        SP.emit(rng.uniform(0, W, n), rng.uniform(H * 0.3, H, n), rng.normal(0, 0.6, n), rng.uniform(-3, -1, n),
                rng.uniform(40, 90, n), -0.01, 0.995, 1, 2)

    SP.step()
    SP.draw(partB, partF)

    # ---- composite: light behind him is occluded, then everything blooms
    if t >= T_IRIS0:
        p_i = min(1.0, (t - T_IRIS0) / (T_IRIS1 - T_IRIS0))
        ri_now = R_IRIS * (1 - p_i ** 2.2)
        keep = np.clip((ri_now - np.sqrt((XX - W / 2) ** 2 + (YY - H / 2) ** 2)) / 4 + 0.5, 0, 1)[..., None]
        ringB *= keep
        partB *= keep
    back = (ringB + partB) * (1 - m)[..., None]
    light = cv2.GaussianBlur(ringF + partF + back, (0, 0), 0.9)
    q = cv2.resize(light, (W // 4, H // 4), interpolation=cv2.INTER_AREA)
    bloom = cv2.GaussianBlur(q, (0, 0), 2.5) * 0.9 + cv2.GaussianBlur(q, (0, 0), 10) * 0.75 + cv2.GaussianBlur(q, (0, 0), 38) * 0.55
    light += cv2.resize(bloom, (W, H), interpolation=cv2.INTER_LINEAR)
    # rim light: the portal behind him wraps around his silhouette
    qb = cv2.resize(ringB + partB, (W // 4, H // 4), interpolation=cv2.INTER_AREA)
    spill = cv2.resize(cv2.GaussianBlur(qb, (0, 0), 14), (W, H), interpolation=cv2.INTER_LINEAR)
    edge = np.clip(m - cv2.GaussianBlur(m, (0, 0), 10), 0, 1) * 2.2
    light += spill * edge[..., None] * 2.5
    out = base + (1 - base) * (1 - np.exp(-light))
    if iris is not None:
        dc = np.sqrt((XX - W / 2) ** 2 + (YY - H / 2) ** 2)
        outside = np.clip((dc - iris) / 4 + 0.5, 0, 1)
        out = out * (1 - outside[..., None]) + (1 - np.exp(-light)) * outside[..., None]
    enc.stdin.write((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8).tobytes())
    if i % 30 == 0:
        print(f"frame {i}/{N}  sparks={len(SP.a)}", flush=True)

enc.stdin.close()
enc.wait()
print("done")
