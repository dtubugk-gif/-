"""Converts a subset of SVG (as used by the design canvas) into Lottie shape items.

Supported: <g>, <path d>, <circle>, <ellipse>, <rect rx>, fill / stroke / stroke-width / opacity /
stroke-linecap / stroke-linejoin / fill-opacity / stroke-opacity, radialGradient and linearGradient in
objectBoundingBox units, and transform="rotate(a cx cy)" / "translate(x y)" / "scale(s)".

Every SVG element becomes a Lottie group so its transform/opacity can be animated independently.
"""

from __future__ import annotations

import math
import re
import xml.etree.ElementTree as ET

from lottie_builder import ellipse, fill, group, path as bezier_path, rect, rgba, static, stroke, transform

NS = "{http://www.w3.org/2000/svg}"
_NUM = re.compile(r"[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?")


# ------------------------------------------------------------------ path data


def _tokenize(d: str):
    for m in re.finditer(r"[MmLlHhVvCcSsQqTtZz]|" + _NUM.pattern, d):
        tok = m.group(0)
        yield tok if tok.isalpha() else float(tok)


def parse_path(d: str):
    """Returns a list of subpaths: (vertices, in_tangents, out_tangents, closed), all absolute."""
    tokens = list(_tokenize(d))
    i = 0
    subpaths = []
    cur = (0.0, 0.0)
    start = (0.0, 0.0)
    cmd = None
    last_ctrl = None
    verts: list = []
    ins: list = []
    outs: list = []
    closed = False

    def flush():
        nonlocal verts, ins, outs, closed
        if verts:
            subpaths.append((verts, ins, outs, closed))
        verts, ins, outs, closed = [], [], [], False

    def add_vertex(p, c1=None, c2=None):
        """Append segment from current last vertex to p with cubic controls c1, c2 (absolute)."""
        if not verts:
            verts.append(list(p))
            ins.append([0.0, 0.0])
            outs.append([0.0, 0.0])
            return
        prev = verts[-1]
        if c1 is None:
            outs[-1] = [0.0, 0.0]
            verts.append(list(p))
            ins.append([0.0, 0.0])
            outs.append([0.0, 0.0])
        else:
            outs[-1] = [c1[0] - prev[0], c1[1] - prev[1]]
            verts.append(list(p))
            ins.append([c2[0] - p[0], c2[1] - p[1]])
            outs.append([0.0, 0.0])

    def read(n):
        nonlocal i
        vals = tokens[i : i + n]
        i += n
        return vals

    while i < len(tokens):
        tok = tokens[i]
        if isinstance(tok, str):
            cmd = tok
            i += 1
            if cmd in "Zz":
                if verts and len(verts) > 1 and math.dist(verts[-1], verts[0]) < 1e-6:
                    ins[0] = ins[-1]
                    verts.pop()
                    ins.pop()
                    outs.pop()
                closed = True
                flush()
                cur = start
                continue
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            x, y = read(2)
            p = (cur[0] + x, cur[1] + y) if rel else (x, y)
            flush()
            verts, ins, outs, closed = [list(p)], [[0.0, 0.0]], [[0.0, 0.0]], False
            cur = start = p
            last_ctrl = None
            cmd = "l" if rel else "L"
        elif c == "L":
            x, y = read(2)
            p = (cur[0] + x, cur[1] + y) if rel else (x, y)
            add_vertex(p)
            cur = p
            last_ctrl = None
        elif c == "H":
            (x,) = read(1)
            p = (cur[0] + x if rel else x, cur[1])
            add_vertex(p)
            cur = p
            last_ctrl = None
        elif c == "V":
            (y,) = read(1)
            p = (cur[0], cur[1] + y if rel else y)
            add_vertex(p)
            cur = p
            last_ctrl = None
        elif c == "C":
            x1, y1, x2, y2, x, y = read(6)
            if rel:
                x1, y1, x2, y2, x, y = x1 + cur[0], y1 + cur[1], x2 + cur[0], y2 + cur[1], x + cur[0], y + cur[1]
            add_vertex((x, y), (x1, y1), (x2, y2))
            last_ctrl = (x2, y2)
            cur = (x, y)
        elif c == "S":
            x2, y2, x, y = read(4)
            if rel:
                x2, y2, x, y = x2 + cur[0], y2 + cur[1], x + cur[0], y + cur[1]
            c1 = (2 * cur[0] - last_ctrl[0], 2 * cur[1] - last_ctrl[1]) if last_ctrl else cur
            add_vertex((x, y), c1, (x2, y2))
            last_ctrl = (x2, y2)
            cur = (x, y)
        elif c == "Q":
            qx, qy, x, y = read(4)
            if rel:
                qx, qy, x, y = qx + cur[0], qy + cur[1], x + cur[0], y + cur[1]
            c1 = (cur[0] + 2 / 3 * (qx - cur[0]), cur[1] + 2 / 3 * (qy - cur[1]))
            c2 = (x + 2 / 3 * (qx - x), y + 2 / 3 * (qy - y))
            add_vertex((x, y), c1, c2)
            last_ctrl = (qx, qy)
            cur = (x, y)
        elif c == "T":
            x, y = read(2)
            if rel:
                x, y = x + cur[0], y + cur[1]
            q = (2 * cur[0] - last_ctrl[0], 2 * cur[1] - last_ctrl[1]) if last_ctrl else cur
            c1 = (cur[0] + 2 / 3 * (q[0] - cur[0]), cur[1] + 2 / 3 * (q[1] - cur[1]))
            c2 = (x + 2 / 3 * (q[0] - x), y + 2 / 3 * (q[1] - y))
            add_vertex((x, y), c1, c2)
            last_ctrl = q
            cur = (x, y)
        else:
            raise ValueError(f"unsupported path command {cmd}")
    flush()
    return subpaths


def path_shapes(d: str):
    """Lottie 'sh' items for every subpath in d."""
    items = []
    for verts, ins, outs, closed in parse_path(d):
        items.append({"ty": "sh", "ks": static({"c": closed, "v": verts, "i": ins, "o": outs}), "nm": "path"})
    return items


def path_bbox(d: str):
    xs, ys = [], []
    for verts, ins, outs, _ in parse_path(d):
        for v, i, o in zip(verts, ins, outs):
            xs += [v[0], v[0] + i[0], v[0] + o[0]]
            ys += [v[1], v[1] + i[1], v[1] + o[1]]
    return min(xs), min(ys), max(xs), max(ys)


# ------------------------------------------------------------------ colours / gradients


def _hex(color: str):
    return rgba(color)


class Gradients:
    def __init__(self):
        self.defs = {}

    def register(self, el):
        tag = el.tag.replace(NS, "")
        stops = []
        for s in el:
            if s.tag.replace(NS, "") != "stop":
                continue
            off = s.get("offset", "0").rstrip("%")
            off = float(off) / (100 if "%" in s.get("offset", "") else 1)
            color = _hex(s.get("stop-color", "#000"))
            alpha = float(s.get("stop-opacity", "1"))
            stops.append((off, color, alpha))
        attrs = {k: v for k, v in el.attrib.items() if k != "id"}
        self.defs[el.get("id")] = (tag, attrs, stops)

    def fill_for(self, ref: str, bbox):
        tag, attrs, stops = self.defs[ref]
        x0, y0, x1, y1 = bbox
        w, h = max(x1 - x0, 1e-6), max(y1 - y0, 1e-6)

        def pct(v, default):
            v = attrs.get(v, default)
            return float(str(v).rstrip("%")) / 100 if str(v).endswith("%") else float(v)

        colors = []
        for off, c, _ in stops:
            colors += [off, c[0], c[1], c[2]]
        alphas = []
        if any(a < 1 for _, _, a in stops):
            for off, _, a in stops:
                alphas += [off, a]
        if tag == "radialGradient":
            cx = x0 + w * pct("cx", "50%")
            cy = y0 + h * pct("cy", "50%")
            r = pct("r", "50%") * max(w, h)
            start, end, kind = [cx, cy], [cx + r, cy], 2
        else:
            start = [x0 + w * pct("x1", "0"), y0 + h * pct("y1", "0")]
            end = [x0 + w * pct("x2", "1"), y0 + h * pct("y2", "0")]
            kind = 1
        return {
            "ty": "gf",
            "t": kind,
            "s": static(start),
            "e": static(end),
            "g": {"p": len(stops), "k": static(colors + alphas)},
            "o": static(100),
            "r": 1,
            "h": static(0),
            "a": static(0),
            "nm": "gradient",
        }


# ------------------------------------------------------------------ elements


def _transform_of(el):
    """Maps an SVG transform attribute to a Lottie group transform."""
    t = el.get("transform")
    if not t:
        return None
    m = re.match(r"rotate\(([-\d.]+)(?:[ ,]+([-\d.]+)[ ,]+([-\d.]+))?\)", t)
    if m:
        a = float(m.group(1))
        cx = float(m.group(2) or 0)
        cy = float(m.group(3) or 0)
        return transform(p=(cx, cy), a=(cx, cy), r=a)
    m = re.match(r"translate\(([-\d.]+)[ ,]*([-\d.]*)\)", t)
    if m:
        return transform(p=(float(m.group(1)), float(m.group(2) or 0)))
    m = re.match(r"scale\(([-\d.]+)\)", t)
    if m:
        s = float(m.group(1)) * 100
        return transform(s=(s, s))
    raise ValueError(f"unsupported transform {t}")


def _paint_items(el, gradients: Gradients, bbox, default_fill="#000000"):
    items = []
    f = el.get("fill", default_fill)
    op = float(el.get("opacity", "1"))
    if f and f != "none":
        fo = float(el.get("fill-opacity", "1")) * 100
        m = re.match(r"url\(#([^)]+)\)", f)
        if m:
            g = gradients.fill_for(m.group(1), bbox)
            g["o"] = static(fo)
            items.append(g)
        else:
            items.append(fill(_hex(f), fo))
    s = el.get("stroke")
    if s and s != "none":
        w = float(el.get("stroke-width", "1"))
        so = float(el.get("stroke-opacity", "1")) * 100
        cap = {"butt": 1, "round": 2, "square": 3}[el.get("stroke-linecap", "butt")]
        join = {"miter": 1, "round": 2, "bevel": 3}[el.get("stroke-linejoin", "miter")]
        items.append(stroke(_hex(s), w, so, cap=cap, join=join))
    return items, op


def element_to_group(el, gradients: Gradients, name=None):
    """One SVG leaf element -> one Lottie group (or None for defs/unknown)."""
    tag = el.tag.replace(NS, "")
    if tag == "path":
        d = el.get("d")
        shapes = path_shapes(d)
        bbox = path_bbox(d)
    elif tag == "circle":
        cx, cy, r = float(el.get("cx")), float(el.get("cy")), float(el.get("r"))
        shapes = [ellipse(cx, cy, 2 * r, 2 * r)]
        bbox = (cx - r, cy - r, cx + r, cy + r)
    elif tag == "ellipse":
        cx, cy, rx, ry = (float(el.get(k)) for k in ("cx", "cy", "rx", "ry"))
        shapes = [ellipse(cx, cy, 2 * rx, 2 * ry)]
        bbox = (cx - rx, cy - ry, cx + rx, cy + ry)
    elif tag == "rect":
        x, y, w, h = (float(el.get(k)) for k in ("x", "y", "width", "height"))
        shapes = [rect(x + w / 2, y + h / 2, w, h, float(el.get("rx", "0")))]
        bbox = (x, y, x + w, y + h)
    else:
        return None
    paint, opacity = _paint_items(el, gradients, bbox)
    tr = _transform_of(el) or transform()
    tr["o"] = static(opacity * 100)
    return group(shapes + paint, name or tag, tr)


def parse_svg(svg_text: str):
    """Returns (gradients, top-level element list) for an <svg> string."""
    svg_text = re.sub(r'\s(aria-label|role)="[^"]*"', "", svg_text)
    root = ET.fromstring(svg_text.replace("<svg", '<svg xmlns="http://www.w3.org/2000/svg"', 1))
    gradients = Gradients()
    elements = []
    for child in root:
        tag = child.tag.replace(NS, "")
        if tag == "defs":
            for g in child:
                gradients.register(g)
        else:
            elements.append(child)
    return gradients, elements


def flatten(elements):
    """Depth-first leaves of <g> containers, keeping a group's opacity/transform on each leaf."""
    out = []
    for el in elements:
        tag = el.tag.replace(NS, "")
        if tag == "g":
            for leaf in flatten(list(el)):
                if el.get("opacity") is not None and leaf.get("opacity") is None:
                    leaf.set("opacity", el.get("opacity"))
                if el.get("transform") and not leaf.get("transform"):
                    leaf.set("transform", el.get("transform"))
                out.append(leaf)
        else:
            out.append(el)
    return out


def groups_from_svg(svg_text: str, select=None):
    """All leaf elements of the SVG as Lottie groups; `select(index, element) -> bool` filters."""
    gradients, elements = parse_svg(svg_text)
    leaves = flatten(elements)
    result = []
    for index, el in enumerate(leaves):
        if select and not select(index, el):
            continue
        g = element_to_group(el, gradients, name=f"{el.tag.replace(NS, '')}{index}")
        if g:
            result.append(g)
    return result


def leaves_of(svg_text: str):
    _, elements = parse_svg(svg_text)
    return flatten(elements)
