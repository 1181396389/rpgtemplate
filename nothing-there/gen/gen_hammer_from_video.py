#!/usr/bin/env python3
"""Hammer traced from the reference video (video-review/nt_video.mp4, frame 0, t = 0.0 s).

The hammer hangs from the monster's right arm (viewer-left) in the frame.

Silhouette: non-green pixels in the head region, cut flat at the neck, flood-filled from a seed inside the head
(hammer_mask.png). The head is inflated into a thin pillow (thickness falls to 0 at the outline), so the front view
is a 1:1 copy of the drawing and the back is closed.
Spikes: the cream blobs inside the silhouette become small pyramids on the front, mirrored on the back.
Colour: sampled from the frame with a mask-aware blur.

Scale: the whole figure in frame 0 is 2.258 blocks tall (body bounds y -0.044..2.214), so the hammer keeps the
video's own proportions.

Coordinates: blocks, origin = neck centre (grip, y up, plate in the x-y plane).
Image left maps to local +x, image up to local +y, same convention as gen_blade_from_video.py.
"""
import math
import os
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_nt_mesh_blocky import Part  # noqa: E402

VIDEO = '/home/user/video-review/nt_video.mp4'
FRAME_INDEX = 0
MASK_OUT = '/home/user/video-review/hammer_mask.png'
BODY_HEIGHT = 2.258           # blocks, from body bounds in body.bin
HALF_MAX = 0.10               # max half-thickness of the head (blocks)
D_PX = 14.0                   # px over which the pillow reaches full thickness
GRID = 8                      # interior sample spacing (px)
NECK_UV = (278.0, 436.0)      # centre of the flat cut at the neck (grip)
HEAD_BOX = (255, 436, 356, 560)   # x0, y0, x1, y1 search region for the head

SPIKE_BASE_R = 0.012
SPIKE_SIDES = 6


def load_frame():
    cap = cv2.VideoCapture(VIDEO)
    cap.set(cv2.CAP_PROP_POS_FRAMES, FRAME_INDEX)
    ok, f = cap.read()
    cap.release()
    if not ok:
        raise RuntimeError('cannot read frame %d' % FRAME_INDEX)
    return f


def figure_scale(img):
    """blocks per pixel, from the whole figure's silhouette height (watermark excluded)."""
    b, g, r = [img[:, :, i].astype(int) for i in range(3)]
    fig = (g - np.maximum(r, b)) <= 80
    fig[:70, :240] = False
    ys = np.nonzero(fig.any(axis=1))[0]
    h_px = float(ys.max() - ys.min())
    return BODY_HEIGHT / h_px


def head_mask(img):
    b, g, r = [img[:, :, i].astype(int) for i in range(3)]
    sil = np.where((g - np.maximum(r, b)) > 80, 0, 255).astype(np.uint8)
    x0, y0, x1, y1 = HEAD_BOX
    m = np.zeros_like(sil)
    m[y0:y1, x0:x1] = sil[y0:y1, x0:x1]
    n, lab, st, _ = cv2.connectedComponentsWithStats(m)
    seed = (315, 500)
    lbl = lab[seed[1], seed[0]]
    if lbl == 0:
        raise RuntimeError('seed is outside the head')
    hm = (lab == lbl).astype(np.uint8) * 255
    ff = hm.copy()
    ffm = np.zeros((ff.shape[0] + 2, ff.shape[1] + 2), np.uint8)
    cv2.floodFill(ff, ffm, (0, 0), 255)
    hm = cv2.bitwise_or(hm, cv2.bitwise_not(ff))
    # round the corners of the flat neck cut: blur and re-threshold the top part only (spikes below stay sharp)
    soft = cv2.GaussianBlur(hm.astype(np.float32), (0, 0), 6) > 127
    top = np.zeros(hm.shape, bool)
    top[:480] = True
    hm = np.where(top & soft, 255, np.where(top, 0, hm)).astype(np.uint8)
    cv2.imwrite(MASK_OUT, hm)
    return hm > 0


def spike_blobs(img, mask):
    """Cream spike pixels inside the silhouette: high value, low saturation."""
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    v = hsv[:, :, 2].astype(int)
    s = hsv[:, :, 1].astype(int)
    cream = ((v > 150) & (s < 90) & mask).astype(np.uint8)
    n, lab, st, cen = cv2.connectedComponentsWithStats(cream)
    out = []
    for i in range(1, n):
        if st[i, cv2.CC_STAT_AREA] >= 5:
            out.append((cen[i][0], cen[i][1], int(st[i, cv2.CC_STAT_AREA])))
    return out


def build_traced_hammer():
    from scipy.spatial import Delaunay

    img = load_frame().astype(float)
    mask = head_mask(img.astype(np.uint8))
    H, W = mask.shape
    s = figure_scale(img.astype(np.uint8))
    gu, gv = NECK_UV

    def to_local(u, v):
        # image right -> +x (upright frame; body convention: right hand at -x, head inboard toward +x)
        return np.array([(u - gu) * s, -(v - gv) * s, 0.0])

    # erode for colour sampling so the green anti-alias fringe at the outline does not leak into the colour
    core = cv2.erode(mask.astype(np.uint8), np.ones((3, 3), np.uint8), iterations=2) > 0
    mf = core.astype(float)
    num = np.stack([cv2.GaussianBlur(img[:, :, c] * mf, (0, 0), 2) for c in range(3)], -1)
    den = cv2.GaussianBlur(mf, (0, 0), 2)[:, :, None] + 1e-6
    smooth = num / den

    def colour_at(u, v):
        iu = int(np.clip(round(u), 0, W - 1))
        iv = int(np.clip(round(v), 0, H - 1))
        return np.clip(smooth[iv, iu][::-1], 0, 255)   # BGR -> RGB

    cnts, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    contour = max(cnts, key=cv2.contourArea)[:, 0, :].astype(float)
    outline = cv2.approxPolyDP(contour.astype(np.float32).reshape(-1, 1, 2), 0.8, True)[:, 0, :].astype(float)

    dist_out = cv2.distanceTransform(mask.astype(np.uint8), cv2.DIST_L2, 5)

    def h_at(u, v):
        iu = int(np.clip(round(u), 0, W - 1))
        iv = int(np.clip(round(v), 0, H - 1))
        d = dist_out[iv, iu]
        return HALF_MAX * math.sqrt(min(1.0, d / D_PX))

    inner = []
    for v in range(0, H, GRID):
        for u in range(0, W, GRID):
            if mask[v, u] and dist_out[v, u] > 4:
                inner.append((u, v))
    pts = np.vstack([outline, np.array(inner, float)])
    tri = Delaunay(pts).simplices

    ys, xs = np.nonzero(mask)
    cu, cv_c = float(xs.mean()), float(ys.mean())
    centre_local = to_local(cu, cv_c)

    part = Part('hammer', 2, (0.0, 0.0, 0.0), goodbye=False)
    part.origin = np.zeros(3)

    kept = 0
    for a, b, c in tri:
        ua, va = pts[a]
        ub, vb = pts[b]
        uc, vc = pts[c]
        mu, mv = (ua + ub + uc) / 3, (va + vb + vc) / 3
        if not mask[int(np.clip(round(mv), 0, H - 1)), int(np.clip(round(mu), 0, W - 1))]:
            continue
        kept += 1
        col = tuple(int(round(x)) for x in (colour_at(ua, va) + colour_at(ub, vb) + colour_at(uc, vc)) / 3)
        A = to_local(ua, va)
        B = to_local(ub, vb)
        C = to_local(uc, vc)
        Af = A + np.array([0, 0, h_at(ua, va)])
        Bf = B + np.array([0, 0, h_at(ub, vb)])
        Cf = C + np.array([0, 0, h_at(uc, vc)])
        Ab = A - np.array([0, 0, h_at(ua, va)])
        Bb = B - np.array([0, 0, h_at(ub, vb)])
        Cb = C - np.array([0, 0, h_at(uc, vc)])
        hint = np.array([centre_local[0], centre_local[1], 0.0])
        part.tri(Af, Bf, Cf, col, hint)
        part.tri(Ab, Bb, Cb, col, hint)

    # Side walls along the outline (small, because the pillow is thin at the edge)
    n_out = len(outline)
    for i in range(n_out):
        p0 = outline[i]
        p1 = outline[(i + 1) % n_out]
        if np.linalg.norm(p1 - p0) < 1e-6:
            continue
        A = to_local(*p0)
        B = to_local(*p1)
        hA = h_at(*p0)
        hB = h_at(*p1)
        col = tuple(int(round(x)) for x in (colour_at(*p0) + colour_at(*p1)) / 2)
        mid3 = (A + B) / 2
        hint = np.array([centre_local[0], centre_local[1], 0.0])
        part.quad(A + [0, 0, hA], B + [0, 0, hB], B - [0, 0, hB], A - [0, 0, hA], col, hint)

    # Spikes: cream blobs, placed on the front surface and mirrored on the back
    spikes = spike_blobs(img.astype(np.uint8), mask)
    spike_col = (232, 224, 208)
    for (u, v, area) in spikes:
        base_local = to_local(u, v)
        hz = h_at(u, v)
        radial = np.array([base_local[0] - centre_local[0], base_local[1] - centre_local[1]])
        if np.linalg.norm(radial) < 1e-6:
            radial = np.array([0.0, 1.0])
        radial = radial / np.linalg.norm(radial)
        length = float(np.clip(3.0 * math.sqrt(area) * s, 0.03, 0.07))
        d_front = np.array([radial[0], radial[1], 0.6])
        d_back = np.array([radial[0], radial[1], -0.6])
        part.spike(base_local + [0, 0, hz * 0.6], d_front, length, SPIKE_BASE_R, spike_col, SPIKE_SIDES)
        part.spike(base_local - [0, 0, hz * 0.6], d_back, length, SPIKE_BASE_R, spike_col, SPIKE_SIDES)

    return part, {'scale_blocks_per_px': s, 'head_px': int(mask.sum()), 'spikes': len(spikes),
                  'tris': len(part.tris), 'kept_tris': kept}


if __name__ == '__main__':
    p, info = build_traced_hammer()
    print(info)
    ys = np.array([v for t in p.tris for v in t[:3]])
    print('bbox min', ys.min(axis=0), 'max', ys.max(axis=0))
