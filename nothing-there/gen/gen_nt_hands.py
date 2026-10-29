#!/usr/bin/env python3
"""Hand models for 一无所有: the hammer is fused to the right hand, the left hand is a plain fist and
becomes the traced blade in blade form (技能 4 变形) or during goodbye.

Only these parts are written (no body). Each part is attached to a vanilla arm pivot, so the vanilla arm
animation moves it. Both arms are flesh (red) arm stubs; the vanilla arms are hidden:
  rightArm  (frame 2, pivot RA_O)  arm stub + hammer (always visible)
  leftArm   (frame 3, pivot LA_O)  arm stub (always visible), blade (in the left hand, blade form and goodbye).
            The blade is turned to stand more upright (UPRIGHT_DEG from vertical, outward), then flipped
            180 deg about its long axis (FLIP_BLADE). The flesh arms are 8-sided tubes (ARM_RINGS).

Frame 5 holds the cocoon egg (gen_nt_egg.py): body and two eyes with irises.

Visibility flags (see NtBodyMesh.java):
  0 always, 2 blade (blade form or goodbye), 4 never (the old fist, not drawn)

Output: mod-repo .../nothing_there/hands.bin (NTM1), and a preview PNG in workspace.
Usage: python3 gen_nt_hands.py
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_nt_mesh_blocky import RED, RED_D, Part, translated, write_bin  # noqa: E402
from gen_hammer_from_video import build_traced_hammer  # noqa: E402
from gen_blade_from_video import build_traced_blade  # noqa: E402
from gen_nt_egg import build_egg_parts  # noqa: E402

OUT_BIN = '/home/user/mod-repo/build0414-resources/assets/rpgtemplate/models/nothing_there/hands.bin'
PREVIEW = '/home/user/workspace/nt-hands-preview.png'

RA_O = (-0.3125, 1.375, 0.0)   # rightArm pivot (vanilla shoulder)
LA_O = (0.3125, 1.375, 0.0)    # leftArm pivot
HAMMER_GRIP = (-0.49, 0.98, 0.0)   # wrist of the right hand (video frame 0)
WRIST_L = (0.40, 0.88, 0.0)        # left wrist / fist: the blade grows out of this point (checked in the preview)
FIST_L = (0.40, 0.90, 0.0)
UPRIGHT_DEG = 35.0   # the blade's angle from vertical after standing it up (was about 64 deg)
FLIP_BLADE = True    # turned 180 deg about its own long axis: the curve side and the face are reversed (v5)


# Flesh arm as a lofted 8-sided tube (v5, fuller than the old box stub).
# (absolute body y, radius) rings from the wrist (bottom) to the shoulder (top).
ARM_RINGS = [(0.92, 0.09), (0.98, 0.10), (1.06, 0.12), (1.16, 0.135),
             (1.26, 0.128), (1.34, 0.12), (1.40, 0.11)]
ARM_SIDES = 8
ARM_CX = 0.4   # stub centre (x); the shoulder pivot is at x = 0.3125


def arm_stub(name, frame, pivot, x):
    """Flesh arm: an 8-sided tube along y, 0.92 (wrist) to 1.40 (shoulder), centred at x."""
    p = Part(name, frame, pivot)
    cx = math.copysign(ARM_CX, x)
    rings = []
    for y, r in ARM_RINGS:
        ring = [np.array([cx + r * math.cos(2 * math.pi * k / ARM_SIDES), y,
                          r * math.sin(2 * math.pi * k / ARM_SIDES)]) for k in range(ARM_SIDES)]
        rings.append((y, ring))
    for (y0, ring0), (y1, ring1) in zip(rings, rings[1:]):
        for k in range(ARM_SIDES):
            k1 = (k + 1) % ARM_SIDES
            mid_y = 0.5 * (y0 + y1)
            hint = (cx, mid_y, 0.0)   # the axis is inside the tube
            p.quad(ring0[k], ring0[k1], ring1[k1], ring1[k], RED, hint)
    # caps: wrist (bottom) and shoulder (top), fans around the centre of each ring
    # the wrist cap faces down (hint above it), the shoulder cap faces up (hint below it)
    for (y, ring), hint_y in (((rings[0]), rings[0][0] + 1.0), ((rings[-1]), rings[-1][0] - 1.0)):
        c = np.array([cx, y, 0.0])
        for k in range(ARM_SIDES):
            k1 = (k + 1) % ARM_SIDES
            p.tri(c, ring[k], ring[k1], RED_D, (cx, hint_y, 0.0))
    p.flag = 0
    return p


FLAG_NEVER = 4   # kept in the file for reference, not drawn


def upright_rotation(pts):
    """Rotation (local blade coords, y up, grip at origin) that stands the blade up, outward and forward-facing.
    The long axis goes to D (up and outward, +x), the thickness axis stays across the blade (z)."""
    c = pts.mean(axis=0)
    cov = np.cov((pts - pts.mean(axis=0)).T)
    w, v = np.linalg.eigh(cov)
    order = np.argsort(w)[::-1]
    a1, a3 = v[:, order[0]], v[:, order[2]]
    if np.dot(a1, c) < 0:          # the grip is the origin: the long axis points from it into the blade
        a1 = -a1
    a2 = np.cross(a3, a1)
    a2 = a2 / np.linalg.norm(a2)
    a3 = np.cross(a1, a2)
    local = np.column_stack([a1, a2, a3])
    rad = np.radians(UPRIGHT_DEG)
    D = np.array([np.sin(rad), np.cos(rad), 0.0])
    n = np.array([0.0, 0.0, 1.0])
    t3 = n - np.dot(n, D) * D
    t3 = t3 / np.linalg.norm(t3)
    t2 = np.cross(t3, D)
    target = np.column_stack([D, t2, t3])
    R = target @ local.T
    if np.linalg.det(R) < 0:
        local[:, 1] = -local[:, 1]
        R = target @ local.T
    if FLIP_BLADE:
        # 180 deg about the upright long axis D: reverses the curve side and the face
        R = (2.0 * np.outer(D, D) - np.eye(3)) @ R
    return R


def rotated(src, name, R):
    """Copy of a part with every vertex and normal turned by R about the origin (the grip)."""
    out = Part(name, src.frame, src.origin, src.goodbye)
    for a, b, c, n, col in src.tris:
        out.tris.append((R @ a, R @ b, R @ c, R @ n, col))
    return out


def build_hands():
    hammer_local, _ = build_traced_hammer()
    blade_src = build_traced_blade()
    pts = np.array([np.asarray(p, float) for t in blade_src.tris for p in t[:3]])
    blade_local = rotated(blade_src, 'blade_local', upright_rotation(pts))

    arm_r = arm_stub('rightArm', 2, RA_O, -0.4)
    hammer = translated(hammer_local, 'hammer', 2, RA_O, HAMMER_GRIP)
    hammer.flag = 0

    arm_l = arm_stub('leftArm', 3, LA_O, 0.4)
    arm_l.flag = 0   # the left arm is flesh too (the vanilla left arm is hidden)
    fist = Part('fist', 3, LA_O)
    fist.box(FIST_L, (0.22, 0.2, 0.22), RED_D)
    fist.flag = FLAG_NEVER
    blade = translated(blade_local, 'blade', 3, LA_O, WRIST_L, goodbye=True)
    blade.flag = 2   # blade form and goodbye: in the left hand

    # cocoon egg (frame 5): drawn by NtBodyMesh.drawEgg, never by the hand frames
    return [arm_r, hammer, arm_l, fist, blade] + build_egg_parts()


def rotate_about_pivot(part, pivot, theta_deg):
    """Copy of a part with its arm swung forward (pitch about the x axis through the pivot).
    theta 90 points the arm straight toward -z (the monster's front)."""
    th = np.radians(theta_deg)
    c, s_ = np.cos(th), np.sin(th)
    pv = np.asarray(pivot, float)

    def R(p):
        q = np.asarray(p, float) - pv
        y, z = q[1], q[2]
        return np.array([q[0], y * c - z * s_, y * s_ + z * c]) + pv

    def Rn(n):
        n = np.asarray(n, float)
        return np.array([n[0], n[1] * c - n[2] * s_, n[1] * s_ + n[2] * c])

    out = Part(part.name, part.frame, part.origin)
    for a, b, c_, n, col in part.tris:
        out.tris.append((R(a), R(b), R(c_), Rn(n), col))
    return out


def render_firstperson(parts_by_name, pose_theta, blade):
    """Model-space preview of the hold pose: the left arm swung forward (blade or fist), the right arm
    hanging with the hammer. Two views: from the front and three-quarter."""
    from gen_nt_weapons import render_panel
    W, H = 560, 760
    arm_r = rotate_about_pivot(parts_by_name['rightArm'], RA_O, 0)
    hammer = rotate_about_pivot(parts_by_name['hammer'], RA_O, 0)
    arm_l = rotate_about_pivot(parts_by_name['leftArm'], LA_O, pose_theta)
    left_item = (rotate_about_pivot(parts_by_name['blade'], LA_O, pose_theta) if blade
                 else rotate_about_pivot(parts_by_name['fist'], LA_O, pose_theta))
    parts = [arm_r, hammer, arm_l, left_item]
    return [render_panel(parts, 0, W, H), render_panel(parts, 70, W, H)]


def render_preview(parts):
    from gen_nt_weapons import render_panel  # same renderer as the weapon preview
    W, H = 560, 760
    panels = [render_panel(parts, 0, W, H), render_panel(parts, 180, W, H)]
    canvas = Image.new('RGB', (W * 2 + 40, H + 20), (12, 11, 16))
    x = 0
    for img in panels:
        canvas.paste(img, (x, 10))
        x += W + 40
    canvas.save(PREVIEW)
    return canvas.size


def main():
    parts = build_hands()
    write_bin(OUT_BIN, parts)
    total = sum(len(p.tris) for p in parts)
    print('parts', [(p.name, p.frame, p.flag, len(p.tris)) for p in parts])
    print('total triangles', total)
    print('wrote', OUT_BIN, os.path.getsize(OUT_BIN), 'bytes')
    print('preview', render_preview(parts))
    by_name = {p.name: p for p in parts}
    for blade, path in ((True, '/home/user/workspace/nt-hands-blade-pose.png'),):
        views = render_firstperson(by_name, 75, blade)
        canvas = Image.new('RGB', (560 * 2 + 40, 780), (12, 11, 16))
        for i, img in enumerate(views):
            canvas.paste(img, (i * 600, 10))
        canvas.save(path)
        print('saved', path)


if __name__ == '__main__':
    main()
