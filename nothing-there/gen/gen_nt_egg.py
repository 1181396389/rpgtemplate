#!/usr/bin/env python3
"""Cocoon / egg (茧 / 蛋) for 一无所有, traced from the reference image uploads/image-1.png.

The egg is a red, lumpy body with two eyes on its sides (blue on the viewer's left, green on the right).
  body : a closed pillow over the red silhouette (the dark "619%" overlay inside it is inpainted first),
         rounded at the rim, colours sampled from the image. Built with gen_hammer_from_video.build_pillow.
  eyes : each eye is a fitted ellipsoid (sclera), with a flat iris disc on its front. Colours come from the image.

Scale: the egg is EGG_HEIGHT blocks tall, from the ground (y = 0) to the top. Its width is set by the reference.
Parts use frame FRAME_EGG (5). The hand code never draws frame 5; NtBodyMesh.drawEgg draws it in the layer pose.
Origin is (0, 1.5, 0): the layer pose puts the ground at 1.5 in model space (see gen_nt_mesh_blocky.to_model_space).

Output is written by gen_nt_hands.py (egg parts are appended to hands.bin).
"""
import math
import os
import sys

import cv2
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_nt_mesh_blocky import Part  # noqa: E402
from gen_hammer_from_video import build_pillow  # noqa: E402

REF = '/home/user/uploads/image-1.png'
FRAME_EGG = 5
EGG_HEIGHT = 1.8          # blocks, ground to top (same as the cocoon hitbox height)
BODY_HALF_DEPTH = 0.45    # blocks, half thickness at the middle of the body
DEPTH_PX = 60.0           # px from the rim over which the body reaches full thickness
CELL = 3                  # grid cell size (px) of the body
DETAIL_AMP = 0.0003       # blocks of relief per grey level of the wrinkle detail (see h_at)
HIGHLIGHT_CAP = 140.0     # max luminance of the body colour (the drawing's pale wrinkles are capped)
DETAIL_SIGMA = 3.0        # px: the detail is the image minus this blur (the folds and creases)
EGG_ORIGIN = np.array([0.0, 1.5, 0.0])
ELLIPSOID_LAT = 8
ELLIPSOID_LON = 12
SCLERA = (226, 224, 228)  # eyeball colour, shaded by the lighting


def _load():
    return np.asarray(Image.open(REF).convert('RGB'))


def _analyse(rgb):
    R, G, B = (rgb[..., i].astype(int) for i in range(3))
    mx = np.maximum(np.maximum(R, G), B)
    mn = np.minimum(np.minimum(R, G), B)
    red = (R - np.maximum(G, B) > 50) & (R > 80)

    # body: the largest red component, closed, then holes filled (the overlay sits inside it)
    # body seed: anything darker than the white background and eyes, except the coloured irises.
    # (the shadowed lower-right of the body is dark red, so the red test alone would cut it off)
    sat = mx - mn
    # dark pixels count only inside the convex hull of the red body, so the eyes' dark outlines stay out
    hull = cv2.convexHull(cv2.findNonZero(red.astype(np.uint8)))
    hull_mask = np.zeros(red.shape, np.uint8)
    cv2.fillConvexPoly(hull_mask, hull, 1)
    dark = (mn < 170) & ~(sat > 70) & (hull_mask > 0)
    seed = red | dark
    closed = cv2.morphologyEx(seed.astype(np.uint8), cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    n, lab, st, _ = cv2.connectedComponentsWithStats(closed)
    big = 1 + int(np.argmax(st[1:, cv2.CC_STAT_AREA]))
    body = (lab == big).astype(np.uint8)
    ff = (body * 255).astype(np.uint8)
    cv2.floodFill(ff, np.zeros((ff.shape[0] + 2, ff.shape[1] + 2), np.uint8), (0, 0), 128)
    body_filled = (body > 0) | (ff == 0)

    # dark overlay inside the body: inpaint it so the colours are not dark
    # the pill is the dark blob inside the body; its white text is a hole in it, so fill the pill's holes too
    pill = ((mx < 110) & body_filled).astype(np.uint8)
    pn, plab, pst, _ = cv2.connectedComponentsWithStats(pill)
    pill_id = 1 + int(np.argmax(pst[1:, cv2.CC_STAT_AREA]))
    pill_m = (plab == pill_id).astype(np.uint8)
    pf = (pill_m * 255).astype(np.uint8)
    cv2.floodFill(pf, np.zeros((pf.shape[0] + 2, pf.shape[1] + 2), np.uint8), (int(pst[pill_id, 0]), int(pst[pill_id, 1])), 128)
    pill_full = ((pill_m > 0) | (pf == 0)).astype(np.uint8)
    overlay = cv2.dilate(pill_full, np.ones((5, 5), np.uint8)) > 0
    overlay &= body_filled
    bgr = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
    inpainted = cv2.cvtColor(cv2.inpaint(bgr, overlay.astype(np.uint8) * 255, 7, cv2.INPAINT_TELEA),
                             cv2.COLOR_BGR2RGB)
    # mask-aware colour: only pixels well inside the body contribute (the light anti-aliased rim is excluded)
    core = cv2.erode(body_filled.astype(np.uint8), np.ones((3, 3), np.uint8), iterations=3).astype(float)
    num = np.stack([cv2.GaussianBlur(inpainted[:, :, c].astype(float) * core, (0, 0), 2) for c in range(3)], -1)
    den = cv2.GaussianBlur(core, (0, 0), 2)[:, :, None] + 1e-6
    inpainted = np.clip(num / den, 0, 255)
    # keep the hue of the light wrinkle highlights but cap their brightness: flat shading would turn them white
    lum_c = 0.30 * inpainted[..., 0] + 0.59 * inpainted[..., 1] + 0.11 * inpainted[..., 2]
    inpainted = inpainted * np.minimum(1.0, HIGHLIGHT_CAP / np.maximum(lum_c, 1.0))[..., None]

    # eyes: iris = saturated pixels outside the body, one component per eye (left / right of the body centre)
    ys, xs = np.nonzero(body_filled)
    v_top, v_bot = int(ys.min()), int(ys.max())
    cx = 0.5 * (xs.min() + xs.max())
    iris_px = (sat > 70) & (~red) & (~body_filled)
    n, lab, st, cen = cv2.connectedComponentsWithStats(iris_px.astype(np.uint8))
    irises = [(i, st[i, cv2.CC_STAT_AREA], cen[i]) for i in range(1, n) if st[i, cv2.CC_STAT_AREA] >= 15]
    irises.sort(key=lambda t: -t[1])
    eyes = []
    not_body = ~cv2.dilate(body_filled.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool)
    for label, area, c in irises[:2]:
        iris_mask = lab == label
        ic = np.array(c, float)
        half = 40
        box = np.zeros_like(body_filled)
        u0, u1 = int(max(0, ic[0] - half)), int(min(rgb.shape[1], ic[0] + half))
        v0, v1 = int(max(0, ic[1] - half)), int(min(rgb.shape[0], ic[1] + half))
        box[v0:v1, u0:u1] = True
        eye_px = ((mn <= 236) & not_body & box).astype(np.uint8)
        en, elab, est, _ = cv2.connectedComponentsWithStats(eye_px)
        # the eye component that contains the iris
        best = max(range(1, en), key=lambda i: int(((elab == i) & iris_mask).sum()))
        pix = np.argwhere(elab == best)   # (v, u)
        ev, eu = pix[:, 0].astype(float), pix[:, 1].astype(float)
        ec = np.array([eu.mean(), ev.mean()])
        ax = 2.0 * math.sqrt(max(eu.var(), 1.0))   # uniform ellipse: var = a^2 / 4
        ay = 2.0 * math.sqrt(max(ev.var(), 1.0))
        ivals = rgb[iris_mask]
        eyes.append({
            'centre': ec, 'axes': (ax, ay), 'iris': np.array(c, float),
            'iris_r': math.sqrt(area / math.pi), 'iris_rgb': np.median(ivals, axis=0),
        })
    eyes.sort(key=lambda e: e['centre'][0])   # left (image) first
    return body_filled, inpainted, rgb, (v_top, v_bot, cx), eyes


def _detail(inpainted, body_filled):
    """Wrinkle relief from the drawing itself: luminance minus its blur, inside the body. Positive = a ridge
    (light), negative = a crease (dark). Zero outside the body and toward the rim."""
    lum = 0.30 * inpainted[..., 0] + 0.59 * inpainted[..., 1] + 0.11 * inpainted[..., 2]
    lum = lum.astype(np.float32)
    core = body_filled.astype(np.float32)
    num = cv2.GaussianBlur(lum * core, (0, 0), DETAIL_SIGMA)
    den = cv2.GaussianBlur(core, (0, 0), DETAIL_SIGMA) + 1e-6
    blur = num / den
    return (lum - blur) * core


def _ellipsoid(part, c, axes, col):
    """UV ellipsoid with semi-axes (blocks), one flat colour (sclera). The iris is a separate disc."""
    ax, ay, az = axes
    cxb = np.asarray(c, float)

    def point(th, ph):
        return cxb + np.array([ax * math.sin(th) * math.cos(ph), ay * math.cos(th), az * math.sin(th) * math.sin(ph)])

    for i in range(ELLIPSOID_LAT):
        th0 = math.pi * i / ELLIPSOID_LAT
        th1 = math.pi * (i + 1) / ELLIPSOID_LAT
        for j in range(ELLIPSOID_LON):
            ph0 = 2 * math.pi * j / ELLIPSOID_LON
            ph1 = 2 * math.pi * (j + 1) / ELLIPSOID_LON
            p00, p01, p11, p10 = point(th0, ph0), point(th0, ph1), point(th1, ph1), point(th1, ph0)
            mid = (p00 + p01 + p11 + p10) / 4.0
            part.quad(p00, p10, p11, p01, col, cxb)


def build_egg_parts():
    rgb = _load()
    body_filled, inpainted, rgb_orig, (v_top, v_bot, cx), eyes = _analyse(rgb)
    s = EGG_HEIGHT / float(v_bot - v_top + 1)

    def to_local(u, v):
        return np.array([(u - cx) * s, (v_bot - v) * s, 0.0])

    H, W = body_filled.shape

    def colour_at(u, v):
        iu = int(np.clip(round(u), 0, W - 1))
        iv = int(np.clip(round(v), 0, H - 1))
        return np.clip(inpainted[iv, iu], 0, 255)

    dist = cv2.distanceTransform(body_filled.astype(np.uint8), cv2.DIST_L2, 5)
    detail = _detail(inpainted, body_filled)

    def h_at(u, v):
        iu = int(np.clip(round(u), 0, W - 1))
        iv = int(np.clip(round(v), 0, H - 1))
        d = dist[iv, iu]
        t = min(1.0, d / DEPTH_PX)
        profile = math.sqrt(max(0.0, 1.0 - (1.0 - t) ** 2))   # round rim, full thickness inside
        lump = 1.0 + 0.02 * math.sin(u / 14.0 + 0.7) * math.cos(v / 18.0)
        # wrinkles from the drawing: faded out within a few px of the rim so the outline stays clean
        fade = min(1.0, d / 6.0)
        relief = DETAIL_AMP * float(detail[iv, iu]) * fade
        return max(0.0, BODY_HALF_DEPTH * profile * lump + relief)

    body = Part('egg_body', FRAME_EGG, EGG_ORIGIN)
    build_pillow(body, body_filled, h_at, colour_at, to_local, S=CELL)
    # the rim and side walls take their colour from the edge pixels, which are paler than the body.
    # Pull them toward the body's median red so the silhouette reads as flesh, not as a pale outline.
    lum_all = np.array([0.3 * t[4][0] + 0.59 * t[4][1] + 0.11 * t[4][2] for t in body.tris])
    median = np.median(np.array([t[4] for t in body.tris], float), axis=0)
    tris = []
    for a, b, c, n, col in body.tris:
        if abs(n[2]) < 0.7:
            col = tuple(int(round(v)) for v in (0.35 * np.array(col, float) + 0.65 * median))
        tris.append((a, b, c, n, col))
    body.tris = tris
    parts = [body]

    for side, e in zip(('l', 'r'), eyes):
        eu, ev = e['centre']
        ax_px, ay_px = e['axes']
        c = to_local(eu, ev)
        axes = (ax_px * s, ay_px * s, 0.8 * ax_px * s)
        eye = Part('egg_eye_' + side, FRAME_EGG, EGG_ORIGIN)
        _ellipsoid(eye, c, axes, SCLERA)
        parts.append(eye)

        # iris: a flat disc on the ellipsoid's front, at the iris position
        iu, iv = e['iris']
        dx, dy = (iu - eu) * s, -(iv - ev) * s
        nx, ny = dx / axes[0], dy / axes[1]
        r2 = nx * nx + ny * ny
        if r2 > 0.8:
            k = math.sqrt(0.8 / r2)
            dx, dy, nx, ny = dx * k, dy * k, nx * k, ny * k
            r2 = 0.8
        dz = axes[2] * math.sqrt(max(0.0, 1.0 - r2))
        p = c + np.array([dx, dy, dz])
        normal = np.array([dx / axes[0] ** 2, dy / axes[1] ** 2, dz / axes[2] ** 2])
        normal = normal / np.linalg.norm(normal)
        iris = Part('egg_iris_' + side, FRAME_EGG, EGG_ORIGIN)
        radius = min(e['iris_r'] * s, 0.7 * min(axes[0], axes[1]))
        iris.disc(p + normal * 0.004, normal, radius, tuple(int(round(v)) for v in e['iris_rgb']), sides=12)
        parts.append(iris)
        pupil = Part('egg_pupil_' + side, FRAME_EGG, EGG_ORIGIN)
        pupil.disc(p + normal * 0.006, normal, radius * 0.45, (18, 14, 16), sides=10)
        parts.append(pupil)
    for p in parts:
        p.flag = 0   # always visible (the layer decides when to draw frame 5)
    return parts


def main():
    parts = build_egg_parts()
    for p in parts:
        print(p.name, len(p.tris), 'tris')
    allp = np.array([v for p in parts for t in p.tris for v in t[:3]])
    print('bbox min', allp.min(axis=0).round(3), 'max', allp.max(axis=0).round(3))


if __name__ == '__main__':
    main()
