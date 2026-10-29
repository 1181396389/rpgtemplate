#!/usr/bin/env python3
"""Blade traced from the reference video frame (video-review/frame_2s_full.png, t = 2.0 s).

Silhouette: blade_mask.png (green screen removed, arm and figure cut away).
Mesh: one quad per 10 px grid cell that lies in the silhouette, front and back faces, with the cell colour
sampled from the frame, plus side walls on the silhouette edge. The result is a 1:1 copy of the drawn
blade's outline and colours (silver edge, blood streaks, hooks), extruded to a thin plate.

Coordinates: blocks, origin = where the arm meets the blade (grip), y up, plate in the x-y plane.
Image left maps to local +x and image up maps to local +y, so the front view matches the frame.
"""
import os
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_nt_mesh_blocky import Part  # noqa: E402

FRAME = '/home/user/video-review/frame_2s_full.png'
MASK = '/home/user/video-review/blade_mask.png'
CELL = 10            # px per grid cell
LENGTH = 2.1         # blade diameter in blocks (same scale as the earlier blade)
HALF_T = 0.03        # half thickness of the plate (blocks)


def build_traced_blade():
    """Smooth outline from the contour, interior points on a 14 px grid, Delaunay triangles kept inside the
    silhouette. Colours come from a mask-aware blur of the frame, so edges stay clean (no green fringe)."""
    from scipy.spatial import Delaunay
    img = cv2.imread(FRAME).astype(float)        # BGR
    mask = cv2.imread(MASK, cv2.IMREAD_GRAYSCALE) > 127
    H, W = mask.shape

    # Grip: where the arm joins the blade (lowest blade pixel in the arm's column band)
    band = mask[:, 405:452]
    ys = np.nonzero(band.any(axis=1))[0]
    grip_v = float(ys.max())
    grip_u = 428.0

    # Scale: blade tip-to-tip diameter (farthest pair on the outer contour)
    cnts, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    contour = max(cnts, key=cv2.contourArea)[:, 0, :].astype(float)
    sub = contour[::4]                           # ~every 4 px along the outline
    d = np.sqrt(((sub[:, None, :] - sub[None, :, :]) ** 2).sum(-1))
    diam_px = float(d.max())
    s = LENGTH / diam_px

    def to_local(u, v):
        return np.array([-(u - grip_u) * s, -(v - grip_v) * s, 0.0])

    # Mask-aware colour: blur(img * mask) / blur(mask)
    mf = mask.astype(float)
    num = np.stack([cv2.GaussianBlur(img[:, :, c] * mf, (0, 0), 3) for c in range(3)], -1)
    den = cv2.GaussianBlur(mf, (0, 0), 3)[:, :, None] + 1e-6
    smooth = num / den                           # BGR

    def colour_at(u, v):
        iu = int(np.clip(round(u), 0, W - 1))
        iv = int(np.clip(round(v), 0, H - 1))
        c = smooth[iv, iu][::-1]                 # RGB
        return np.clip(c, 0, 255)

    # Outline points (simplified) and interior grid points away from the edge
    outline = cv2.approxPolyDP(contour.astype(np.float32).reshape(-1, 1, 2), 1.2, True)[:, 0, :].astype(float)
    dist = cv2.distanceTransform(mask.astype(np.uint8), cv2.DIST_L2, 5)
    inner = []
    for v in range(0, H, 14):
        for u in range(0, W, 14):
            if mask[v, u] and dist[v, u] > 7:
                inner.append((u, v))
    pts = np.vstack([outline, np.array(inner, float)]) if inner else outline
    tri = Delaunay(pts).simplices

    part = Part('blade', 3, (0.0, 0.0, 0.0), goodbye=True)
    part.origin = np.zeros(3)
    fz = np.array([0.0, 0.0, HALF_T])
    bz = np.array([0.0, 0.0, -HALF_T])
    kept = 0
    for a, b, c in tri:
        ua, va = pts[a]; ub, vb = pts[b]; uc, vc = pts[c]
        cu, cv_ = (ua + ub + uc) / 3, (va + vb + vc) / 3
        if not mask[int(np.clip(round(cv_), 0, H - 1)), int(np.clip(round(cu), 0, W - 1))]:
            continue
        kept += 1
        col = tuple(int(round(x)) for x in (colour_at(ua, va) + colour_at(ub, vb) + colour_at(uc, vc)) / 3)
        A, B_, C_ = to_local(ua, va), to_local(ub, vb), to_local(uc, vc)
        cen = (A + B_ + C_) / 3
        part.tri(A + fz, B_ + fz, C_ + fz, col, cen - np.array([0, 0, 1.0]))
        part.tri(A + bz, B_ + bz, C_ + bz, col, cen + np.array([0, 0, 1.0]))

    # Side walls along the outline; the outward direction is found from the mask
    n_out = len(outline)
    for i in range(n_out):
        p0 = outline[i]
        p1 = outline[(i + 1) % n_out]
        e = p1 - p0
        L = np.linalg.norm(e)
        if L < 1e-6:
            continue
        nrm = np.array([e[1], -e[0]]) / L        # one perpendicular in image coords
        mid = (p0 + p1) / 2
        probe = mid + nrm * 4
        pu, pv = int(round(probe[0])), int(round(probe[1]))
        inside_probe = 0 <= pu < W and 0 <= pv < H and mask[pv, pu]
        out_img = -nrm if inside_probe else nrm   # outward in image coords
        out_local = np.array([-out_img[0], -out_img[1], 0.0])  # local = -(image), see to_local
        c0 = colour_at(*p0)
        c1 = colour_at(*p1)
        col = tuple(int(round(x)) for x in (c0 + c1) / 2)
        A, B_ = to_local(*p0), to_local(*p1)
        mid3 = (A + B_) / 2
        hint = mid3 - out_local
        part.quad(A + fz, B_ + fz, B_ + bz, A + bz, col, hint)
    return part


if __name__ == '__main__':
    p = build_traced_blade()
    print('traced blade tris', len(p.tris))
