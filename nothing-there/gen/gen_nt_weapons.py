#!/usr/bin/env python3
"""Weapon-only models for 一无所有 (design §12: right hand = spiked oval hammer, left arm = giant blade on goodbye).

Local space: units are blocks, origin = grip point, y up.
  hammer: egg-shaped red head on a thin handle. Grip = middle of the handle, head below it.
  blade:  scythe-like crescent (reference image-3). The arm holds it at the middle of the blade, which bows
          toward the convex (silver) side. Grip = origin on the spine.
Outputs (preview only, nothing is written to the game resources):
  /home/user/workspace/nt-weapons/nt_hammer.obj
  /home/user/workspace/nt-weapons/nt_blade.obj
  /home/user/workspace/nt-weapons-preview.png
Usage: python3 gen_nt_weapons.py
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_nt_mesh_blocky import BONE, BONE_D, Part, write_obj  # noqa: E402

OUT_DIR = '/home/user/workspace/nt-weapons'
PREVIEW = '/home/user/workspace/nt-weapons-preview.png'
FONT = '/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc'

# Palette (sRGB 0-255)
HAMMER_RED = (172, 26, 28)
HAMMER_HANDLE = (128, 20, 26)
HAMMER_SPIKE = (226, 214, 196)
SILVER = (214, 218, 224)
FLAME_DARK = (118, 14, 20)
FLAME_BLACK = (40, 8, 12)
FLAME_EDGE = (150, 30, 20)
FLAME_TIP = (232, 72, 28)
EYE_GREEN = (63, 176, 74)
EYE_BLUE = (58, 127, 208)


def build_hammer():
    """Hammer in local space (grip at origin). Egg-shaped head, thin handle, six small spikes placed on the
    surface using the real surface normal (so they sit on the head and point outward)."""
    HEAD_C = np.array([0.0, -0.2, 0.0])   # raw centre of the head
    HH = 0.16                             # half height
    RX, RZ = 0.13, 0.11                   # half width (x) and half depth (z)
    HANDLE_BOT, HANDLE_TOP = -0.06, 0.16  # raw handle span
    MID = (HANDLE_BOT + HANDLE_TOP) / 2   # grip = middle of the handle

    def surface(v, phi):
        """Point on the egg at fraction v (0 = top, 1 = bottom) and angle phi. Bottom is wider."""
        y = HEAD_C[1] + HH * math.cos(math.pi * v)
        r = (math.sin(math.pi * v) ** 0.9) * (0.85 + 0.25 * v)
        return np.array([RX * r * math.cos(phi), y, RZ * r * math.sin(phi)])

    def normal(v, phi):
        e = 1e-3
        dv = surface(v + e, phi) - surface(v - e, phi)
        dp = surface(v, phi + e) - surface(v, phi - e)
        n = np.cross(dp, dv)
        if np.dot(n, surface(v, phi) - HEAD_C) < 0:
            n = -n
        return n / np.linalg.norm(n)

    hm = Part('hammer', 0, (0.0, 0.0, 0.0))
    seg, rings = 12, 9
    for i in range(rings):
        v0, v1 = i / rings, (i + 1) / rings
        for k in range(seg):
            p0, p1 = 2 * math.pi * k / seg, 2 * math.pi * (k + 1) / seg
            a, b = surface(v0, p0), surface(v0, p1)
            c, d = surface(v1, p1), surface(v1, p0)
            hm.quad(a, b, c, d, HAMMER_RED, HEAD_C)

    # Thin tapered handle rising from the head
    hs = 8
    for k in range(hs):
        p0, p1 = 2 * math.pi * k / hs, 2 * math.pi * (k + 1) / hs
        r_bot, r_top = 0.05, 0.035
        bA = np.array([r_bot * math.cos(p0), HANDLE_BOT, r_bot * math.sin(p0)])
        bB = np.array([r_bot * math.cos(p1), HANDLE_BOT, r_bot * math.sin(p1)])
        tA = np.array([r_top * math.cos(p0), HANDLE_TOP, r_top * math.sin(p0)])
        tB = np.array([r_top * math.cos(p1), HANDLE_TOP, r_top * math.sin(p1)])
        hm.quad(bA, bB, tB, tA, HAMMER_HANDLE, np.array([0.0, MID, 0.0]))

    # Six small light spikes on the head, placed on the surface along the normal
    for v, phi in ((0.20, 0.9), (0.34, 2.9), (0.46, 4.6), (0.58, 1.7), (0.70, 3.9), (0.82, 0.4)):
        n = normal(v, phi)
        base = surface(v, phi) - n * 0.01
        hm.spike(base, n, 0.05, 0.02, HAMMER_SPIKE, 4)

    # Shift so the grip (handle middle) is the origin
    shifted = Part('hammer', 0, (0.0, 0.0, 0.0))
    off = np.array([0.0, -MID, 0.0])
    for a, b, c, n, col in hm.tris:
        shifted.tris.append((a + off, b + off, c + off, n, col))
    return shifted


def build_blade_local():
    """Blade, sharpened and flowing (after image-1). Diamond cross-section: a sharp silver outer edge, a
    sharp inner edge, and two ridge facets. Charcoal facets carry blood streaks that flow diagonally toward
    the tip, the inner edge runs with blood, three triangular claws hook off the inner edge, and thin blood
    rivulets run down from the edge. The arm holds the middle of the blade (origin)."""
    A = np.array([0.12, -1.0, 0.0])     # lower tip
    C = np.array([-0.95, 0.0, 0.0])     # control point (the bow)
    B = np.array([0.22, 1.0, 0.0])      # upper tip
    TH = 0.05                           # ridge height (half thickness) along z
    BZ = np.array([0.0, 0.0, 1.0])
    STEPS = 80
    T_GRIP = 0.45
    CHAR = (30, 26, 28)
    BLOOD = (150, 12, 16)
    BLOOD_HI = (214, 36, 30)
    SILVER = (222, 226, 232)

    def P(t):
        return (1 - t) ** 2 * A + 2 * (1 - t) * t * C + t * t * B

    def D(t):
        d = 2 * (1 - t) * (C - A) + 2 * t * (B - C)
        return d / np.linalg.norm(d)

    def W(t):
        # wide upper wedge, the tip and the lower tip come to a point
        return 0.004 + 0.21 * math.sin(math.pi * (0.03 + 0.94 * t)) ** 0.55

    def blood(t, u):
        """Blood level 0..1 on the blade surface, continuous. t runs along the blade, u (0..1) runs around the
        cross-section. Low frequency diagonal bands flow toward the tip; the inner edge carries more blood."""
        x = 2 * math.pi * (1.4 * t + 0.8 * u) + 0.6 * math.sin(2 * math.pi * (2.1 * t - 0.5 * u))
        v = 0.5 + 0.5 * math.sin(x) + 0.3 * u
        return min(1.0, max(0.0, (v - 0.35) / 0.75))

    def mix(a, b, f):
        return tuple(int(round(a[j] + (b[j] - a[j]) * f)) for j in range(3))

    bl = Part('blade', 0, (0.0, 0.0, 0.0))
    stations = []
    for i in range(STEPS + 1):
        t = i / STEPS
        p, d, w = P(t), D(t), W(t)
        N = np.array([-d[1], d[0], 0.0])
        if np.dot(N, C - p) < 0:        # outer (convex) side faces the bow point C
            N = -N
        stations.append((t, p, d, N, w))

    def loop(p, N, w):
        """Diamond cross-section: O (outer edge), T (top ridge), I (inner edge), Bt (bottom ridge)."""
        O = p + w * N
        T = p + TH * BZ
        I_ = p - w * N
        Bt = p - TH * BZ
        return [O, (O + T) / 2, T, (T + I_) / 2, I_, (I_ + Bt) / 2, Bt, (Bt + O) / 2]

    seg_u = [i / 8 for i in range(8)]   # perimeter parameter of each loop point (0 = O)
    for i in range(STEPS):
        t_mid = (i + 0.5) / STEPS
        _, p0, _, N0, w0 = stations[i]
        _, p1, _, N1, w1 = stations[i + 1]
        L0, L1 = loop(p0, N0, w0), loop(p1, N1, w1)
        hint = (p0 + p1) / 2.0
        for j in range(8):
            j1 = (j + 1) % 8
            u_mid = (seg_u[j] + seg_u[j1] + (1 / 8 if j1 == 0 else 0)) / 2
            if j in (0, 7):                 # the two facets next to the sharp outer edge: silver
                col = SILVER
            else:
                b = blood(t_mid, u_mid)
                col = mix(CHAR, BLOOD, min(1.0, b * 1.3))
                if b > 0.88:
                    col = mix(BLOOD, BLOOD_HI, (b - 0.88) / 0.12)
            bl.quad(L0[j], L0[j1], L1[j1], L1[j], col, hint)

    # Three sharp triangular claws hooking off the inner edge
    for i, t in enumerate((0.16, 0.44, 0.72)):
        p, d, N, w = stations[round(t * STEPS)][1:5]
        inner = -N
        base = p + inner * w
        direction = inner * 0.3 - d * 0.95
        bl.spike(base, direction, 0.24 + 0.05 * (i % 2), 0.045, BLOOD, 3)

    # Blood rivulets running down from the inner edge (flat ribbons, width in the blade plane)
    for t, length in ((0.36, 0.42), (0.58, 0.32), (0.80, 0.22)):
        p, d, N, w = stations[round(t * STEPS)][1:5]
        top = p - N * w
        steps = 4
        rw = 0.036
        side = np.array([1.0, 0.0, 0.0])
        for k in range(steps):
            a = top + np.array([0.0, -length * k / steps, 0.0])
            b = top + np.array([0.0, -length * (k + 1) / steps, 0.0])
            wa = rw * (1 - k / steps)
            wb = rw * (1 - (k + 1) / steps)
            col = BLOOD_HI if k < 2 else BLOOD
            bl.quad(a - side * wa, a + side * wa, b + side * wb, b - side * wb, col, a - BZ * 0.1)

    # Grip: a small charcoal block on the spine (hidden in the fist)
    grip = P(T_GRIP)
    bl.box(grip, (0.1, 0.12, 0.08), CHAR)

    # Shift so the grip is the origin
    shifted = Part('blade', 0, (0.0, 0.0, 0.0))
    for a, b, c, n, col in bl.tris:
        shifted.tris.append((a - grip, b - grip, c - grip, n, col))
    return shifted


def bounds(parts):
    pts = np.array([p for part in parts for t in part.tris for p in t[:3]])
    return pts.min(axis=0), pts.max(axis=0)


def project(parts, theta_deg):
    th = np.radians(theta_deg)
    R = np.array([[np.cos(th), 0, np.sin(th)], [0, 1, 0], [-np.sin(th), 0, np.cos(th)]])
    out = []
    for part in parts:
        for a, b, c, n, col in part.tris:
            out.append((a @ R.T, b @ R.T, c @ R.T, n @ R.T, col))
    return out


def render_panel(parts, theta_deg, W=560, H=760):
    tris = project(parts, theta_deg)
    allp = np.array([p for t in tris for p in t[:3]])
    lo, hi = allp.min(axis=0), allp.max(axis=0)
    cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    S = min(W, H) * 0.78 / span

    def to_screen(p):
        return -(p[0] - cx) * S + W / 2, -(p[1] - cy) * S + H / 2

    zbuf = np.full((H, W), -np.inf)
    img = np.zeros((H, W, 3))
    L = np.array([-0.4, 0.6, -0.7])
    L = L / np.linalg.norm(L)
    for va, vb, vc, nr, col in tris:
        sa, sb, sc = to_screen(va), to_screen(vb), to_screen(vc)
        x = np.array([sa[0], sb[0], sc[0]])
        y = np.array([sa[1], sb[1], sc[1]])
        d_ = np.array([-va[2], -vb[2], -vc[2]])
        x0, x1 = max(int(np.floor(x.min())), 0), min(int(np.ceil(x.max())), W - 1)
        y0, y1 = max(int(np.floor(y.min())), 0), min(int(np.ceil(y.max())), H - 1)
        if x0 > x1 or y0 > y1:
            continue
        den = (y[1] - y[2]) * (x[0] - x[2]) + (x[2] - x[1]) * (y[0] - y[2])
        if abs(den) < 1e-12:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        w0 = ((y[1] - y[2]) * (gx - x[2]) + (x[2] - x[1]) * (gy - y[2])) / den
        w1 = ((y[2] - y[0]) * (gx - x[2]) + (x[0] - x[2]) * (gy - y[2])) / den
        w2 = 1 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not inside.any():
            continue
        d = w0 * d_[0] + w1 * d_[1] + w2 * d_[2]
        fn = nr / (np.linalg.norm(nr) + 1e-12)
        shade = 0.32 + 0.68 * max(0.0, float(fn @ L)) + 0.10 * max(0.0, float(-fn @ L))
        c = np.array(col, float) / 255.0 * shade
        zs = zbuf[y0:y1 + 1, x0:x1 + 1]
        upd = inside & (d > zs)
        zs[upd] = d[upd]
        img[y0:y1 + 1, x0:x1 + 1][upd] = c
    base = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    panel = Image.new('RGB', (W, H), (22, 20, 28))
    panel.paste(base, (0, 0), Image.fromarray(((zbuf > -np.inf) * 255).astype(np.uint8)))
    d = ImageDraw.Draw(panel)
    gx, gy = to_screen(np.zeros(3))
    d.line((gx - 14, gy, gx + 14, gy), fill=(255, 220, 60), width=2)
    d.line((gx, gy - 14, gx, gy + 14), fill=(255, 220, 60), width=2)
    d.ellipse((gx - 4, gy - 4, gx + 4, gy + 4), outline=(255, 220, 60), width=2)
    return panel


def main():
    from gen_hammer_from_video import build_traced_hammer
    from gen_blade_from_video import build_traced_blade
    hammer, _hinfo = build_traced_hammer()
    blade = build_traced_blade()
    os.makedirs(OUT_DIR, exist_ok=True)
    write_obj(os.path.join(OUT_DIR, 'nt_hammer.obj'), [hammer])
    write_obj(os.path.join(OUT_DIR, 'nt_blade.obj'), [blade])

    hlo, hhi = bounds([hammer])
    blo, bhi = bounds([blade])
    print('hammer tris', len(hammer.tris), 'size', np.round(hhi - hlo, 3))
    print('blade  tris', len(blade.tris), 'size', np.round(bhi - blo, 3))

    W, H = 560, 760
    gap = 24
    panels = [
        ('锤 · 与视频同角度', render_panel([hammer], 180, W, H), '描摹自视频开头帧，握点为手臂接入处（十字标记）'),
        ('锤 · 另一侧', render_panel([hammer], 0, W, H), '同一把锤从另一侧看，尖刺镜像，头部朝向身体中心'),
        ('利刃 · 与视频同角度', render_panel([blade], 0, W, H), '描摹自视频 2 秒帧，握点为手臂接入处（十字标记）'),
        ('利刃 · 背面', render_panel([blade], 145, W, H), '背面同样取自画面颜色'),
    ]
    canvas = Image.new('RGB', (W * 4 + gap * 3 + 40, H + 280), (12, 11, 16))
    d = ImageDraw.Draw(canvas)
    f1 = ImageFont.truetype(FONT, 36, index=2)
    f2 = ImageFont.truetype(FONT, 24, index=2)
    d.text((24, 18), '一无所有 · 武器预览：锤（右手）与弯刃（左臂，goodbye）· 均描摹自视频', font=f1, fill=(235, 226, 200))
    d.text((24, 70),
           f'锤（描摹自视频）：{len(hammer.tris)} 个三角形，高 {hhi[1]-hlo[1]:.2f} 格，宽 {hhi[0]-hlo[0]:.2f} 格。'
           f'弯刃（描摹自视频）：{len(blade.tris)} 个三角形，长 {bhi[1]-blo[1]:.2f} 格，宽 {bhi[0]-blo[0]:.2f} 格。',
           font=f2, fill=(170, 160, 140))
    d.text((24, 110), '这是武器局部坐标预览，不是游戏内画面。修改意见请直接告诉我。', font=f2, fill=(170, 160, 140))
    x = 20
    for label, img, note in panels:
        canvas.paste(img, (x, 180))
        d.text((x + 10, 146), label, font=f2, fill=(230, 120, 110))
        d.text((x + 10, 180 + H + 12), note, font=f2, fill=(170, 160, 140))
        x += W + gap
    canvas.save(PREVIEW)
    print('saved', PREVIEW, canvas.size)


if __name__ == '__main__':
    main()
