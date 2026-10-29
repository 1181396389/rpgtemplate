#!/usr/bin/env python3
"""Blocky low-poly 3D body for 一无所有 (Minecraft-style boxes, with the hammer and blade modelled in detail).

Figure space: units are blocks, y up, feet at y=0, front = -z, monster's right = -x.
Every part is built from flat-shaded triangles (boxes, spikes, discs, a swept blade).
Outputs:
  body.bin  (NTM1) for NtBodyMesh.java. Each part is relative to its vanilla pivot, in model space (y down).
  nt_body_full.obj  figure space with vertex colours, for the preview and 3D viewers.
Usage: python3 gen_nt_mesh_blocky.py
"""
import math
import os
import struct

import numpy as np

OUT_BIN = '/home/user/mod-repo/build0414-resources/assets/rpgtemplate/models/nothing_there/body.bin'
OUT_DIR = '/home/user/workspace/nt-body-3d'

# Palette (sRGB 0-255)
RED = (170, 30, 46)
RED_D = (118, 20, 36)
RED_L = (205, 44, 62)
PURPLE = (107, 42, 110)
MASK = (233, 228, 214)
WHITE = (250, 250, 246)
BONE = (222, 214, 196)
BONE_D = (138, 127, 112)
PINK = (240, 122, 138)
GREEN = (63, 176, 74)
BLUE = (58, 127, 208)
CREST = (46, 127, 214)
BLADE_FACE = (122, 20, 36)
BLADE_EDGE = (217, 212, 208)
HAMMER_CORE = (207, 198, 176)
HAMMER_RED = (150, 24, 40)


def _basis(d):
    h = np.array([0.0, 1.0, 0.0]) if abs(d[1]) < 0.9 else np.array([1.0, 0.0, 0.0])
    u = np.cross(d, h)
    u /= np.linalg.norm(u)
    v = np.cross(d, u)
    return u, v


class Part:
    """A named set of flat triangles. Each triangle stores its own outward normal."""

    def __init__(self, name, frame, origin, goodbye=False):
        self.name = name
        self.frame = frame
        self.goodbye = goodbye
        self.origin = np.asarray(origin, float)
        self.tris = []  # (a, b, c, normal, colour)

    def tri(self, a, b, c, col, hint):
        a, b, c = (np.asarray(v, float) for v in (a, b, c))
        n = np.cross(b - a, c - a)
        ln = np.linalg.norm(n)
        if ln < 1e-12:
            return
        n = n / ln
        mid = (a + b + c) / 3.0
        if np.dot(n, mid - np.asarray(hint, float)) < 0:
            n = -n
        self.tris.append((a, b, c, n, tuple(int(v) for v in col)))

    def quad(self, p0, p1, p2, p3, col, hint):
        self.tri(p0, p1, p2, col, hint)
        self.tri(p0, p2, p3, col, hint)

    def box(self, c, size, col):
        c = np.asarray(c, float)
        h = np.asarray(size, float) / 2.0

        def P(sx, sy, sz):
            return c + h * np.array([sx, sy, sz], float)

        faces = [
            ((-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)),
            ((1, -1, -1), (-1, -1, -1), (-1, 1, -1), (1, 1, -1)),
            ((1, -1, 1), (1, -1, -1), (1, 1, -1), (1, 1, 1)),
            ((-1, -1, -1), (-1, -1, 1), (-1, 1, 1), (-1, 1, -1)),
            ((-1, 1, 1), (1, 1, 1), (1, 1, -1), (-1, 1, -1)),
            ((-1, -1, 1), (-1, -1, -1), (1, -1, -1), (1, -1, 1)),
        ]
        for f in faces:
            p = [P(*v) for v in f]
            self.quad(p[0], p[1], p[2], p[3], col, c)

    def spike(self, base, d, length, radius, col, sides=6):
        """Open-based pyramid/cone pointing along d from base."""
        base = np.asarray(base, float)
        d = np.asarray(d, float)
        d = d / np.linalg.norm(d)
        u, v = _basis(d)
        tip = base + d * length
        ring = [base + radius * (math.cos(2 * math.pi * k / sides) * u + math.sin(2 * math.pi * k / sides) * v)
                for k in range(sides)]
        for k in range(sides):
            self.tri(ring[k], ring[(k + 1) % sides], tip, col, base)

    def disc(self, c, n, radius, col, sides=8):
        """Flat polygon (an eye) facing +n."""
        c = np.asarray(c, float)
        n = np.asarray(n, float)
        n = n / np.linalg.norm(n)
        u, v = _basis(n)
        ring = [c + radius * (math.cos(2 * math.pi * k / sides) * u + math.sin(2 * math.pi * k / sides) * v)
                for k in range(sides)]
        for k in range(sides):
            self.tri(c, ring[k], ring[(k + 1) % sides], col, c - n)


def bez(t, s, c, e):
    return (1 - t) ** 2 * s + 2 * (1 - t) * t * c + t * t * e


def bez_d(t, s, c, e):
    return 2 * (1 - t) * (c - s) + 2 * t * (e - c)


def build_blade(part):
    """Sickle blade held by the monster's right hand (-x). Flat plate, facing +-z, with bone cutting edges."""
    S = np.array([-0.42, 0.42, 0.0])   # hilt, at the fist
    C = np.array([-1.05, 1.00, 0.0])   # control point (the blade bows out to -x)
    E = np.array([-0.60, 1.98, 0.0])   # tip, above the head
    TH = 0.03                          # half thickness along z
    B = np.array([0.0, 0.0, 1.0])
    steps = 48

    def width(t):
        return 0.02 + 0.2 * math.sin(math.pi * (0.05 + 0.9 * t)) ** 0.8 * (1 - t) ** 0.4

    stations = []
    for i in range(steps + 1):
        t = i / steps
        P = bez(t, S, C, E)
        d = bez_d(t, S, C, E)
        d = d / np.linalg.norm(d)
        N = np.array([-d[1], d[0], 0.0])
        stations.append((t, P, N, width(t), d))

    def corners(P, N, w):
        return [P + w * N + TH * B, P - w * N + TH * B, P - w * N - TH * B, P + w * N - TH * B]

    for i in range(steps):
        _, P0, N0, w0, _ = stations[i]
        _, P1, N1, w1, _ = stations[i + 1]
        c0 = corners(P0, N0, w0)
        c1 = corners(P1, N1, w1)
        hint = (P0 + P1) / 2.0
        for k in range(4):
            k1 = (k + 1) % 4
            col = BLADE_FACE if k in (0, 2) else BLADE_EDGE   # k=0 top (+B), k=2 bottom (-B)
            part.quad(c0[k], c0[k1], c1[k1], c1[k], col, hint)

    # Eyes on both flat faces
    for t, sign in ((0.25, 1), (0.5, 1), (0.72, 1)):
        P = bez(t, S, C, E)
        w = width(t)
        col = GREEN if t < 0.6 else BLUE
        part.disc(P + sign * (TH + 0.002) * B, sign * B, min(0.035, w * 0.45), col, 8)
        part.disc(P - (TH + 0.002) * B, -B, min(0.035, w * 0.45), col, 8)

    # Bone spikes on the convex (outer) edge
    for t in (0.12, 0.3, 0.48, 0.66, 0.84):
        P = bez(t, S, C, E)
        d = bez_d(t, S, C, E)
        d = d / np.linalg.norm(d)
        N = np.array([-d[1], d[0], 0.0])
        w = width(t)
        outward = -N if np.dot(N, C - P) > 0 else N
        part.spike(P + w * outward, outward, 0.07, 0.022, BONE, 4)

    part.box(S, (0.1, 0.1, 0.07), BONE_D)   # hilt


def mirror(src, name, frame, origin, goodbye=False):
    """Copy of a part mirrored across x=0 (positions and normals), re-attached to another frame."""
    out = Part(name, frame, origin, goodbye)
    for a, b, c, n, col in src.tris:
        fa, fb, fc = (np.array([-p[0], p[1], p[2]]) for p in (a, b, c))
        out.tris.append((fa, fb, fc, np.array([-n[0], n[1], n[2]]), col))
    return out


def build_parts():
    HEAD_O = (0.0, 1.5, 0.0)
    BODY_O = (0.0, 1.5, 0.0)
    RA_O = (-0.3125, 1.375, 0.0)
    LA_O = (0.3125, 1.375, 0.0)
    RL_O = (-0.11875, 0.75, 0.0)
    LL_O = (0.11875, 0.75, 0.0)

    # Head: grinning mask, eyes, and blue crest
    head = Part('head', 0, HEAD_O)
    head.box((0, 1.75, 0), (0.5, 0.5, 0.5), RED)
    head.box((0, 1.73, -0.255), (0.42, 0.34, 0.02), MASK)
    head.box((0, 1.66, -0.27), (0.34, 0.14, 0.02), PURPLE)
    for i in range(6):
        x = -0.14 + 0.056 * i
        head.spike((x, 1.73, -0.28), (0, -1, 0), 0.05, 0.02, WHITE, 4)
        head.spike((x, 1.59, -0.28), (0, 1, 0), 0.05, 0.02, WHITE, 4)
    for sx, iris in ((-1, GREEN), (1, BLUE)):
        head.box((0.12 * sx, 1.84, -0.265), (0.11, 0.09, 0.02), WHITE)
        head.box((0.12 * sx, 1.84, -0.28), (0.05, 0.05, 0.01), iris)
    head.spike((0, 1.99, 0.0), (0, 1, -0.2), 0.12, 0.06, CREST, 6)
    head.spike((0, 2.02, 0.03), (0, 1, -0.5), 0.1, 0.045, CREST, 6)
    head.spike((0, 2.05, 0.06), (0, 1, -0.8), 0.08, 0.035, CREST, 6)
    for sx in (-1, 1):
        head.spike((0.25 * sx, 1.9, 0.0), (sx, 0.4, 0.2), 0.1, 0.035, BONE, 5)

    # Torso: ribcage with a purple cavity, hanging organ, pecs, back spines
    body = Part('body', 1, BODY_O)
    body.box((0, 1.12, 0), (0.5, 0.7, 0.3), RED)
    body.box((0, 0.8, 0), (0.44, 0.1, 0.26), RED_D)
    body.box((0, 1.2, -0.155), (0.3, 0.24, 0.02), PURPLE)
    for y in (1.12, 1.2, 1.28):
        body.box((0, y, -0.17), (0.3, 0.025, 0.02), BONE)
    body.box((0, 1.2, -0.175), (0.03, 0.26, 0.02), BONE)
    for sx in (-1, 1):
        body.box((0.13 * sx, 1.36, -0.16), (0.16, 0.1, 0.02), RED_L)
    body.box((-0.17, 1.05, -0.16), (0.1, 0.09, 0.02), WHITE)
    body.box((-0.17, 1.05, -0.172), (0.05, 0.05, 0.01), GREEN)
    body.box((0, 0.96, -0.17), (0.07, 0.28, 0.03), PINK)
    for sx in (-1, 1):
        body.spike((0.25 * sx, 1.42, -0.05), (0.6 * sx, 0.8, -0.2), 0.2, 0.05, BONE, 6)
    for y in (1.42, 1.3, 1.18):
        body.spike((0, y, 0.15), (0, 0.4, 1), 0.14, 0.04, BONE, 5)

    # Right arm (monster's right, -x): fist and spiked claws. The blade is attached to it (goodbye only).
    ra = Part('rightArm', 2, RA_O)
    ra.box((-0.36, 1.14, 0), (0.18, 0.5, 0.2), RED)
    ra.box((-0.37, 0.6, 0), (0.16, 0.4, 0.18), RED_D)
    ra.box((-0.455, 0.6, 0), (0.02, 0.08, 0.06), GREEN)
    ra.spike((-0.45, 1.0, 0.0), (-1, 0.1, -0.3), 0.12, 0.03, BONE, 5)
    ra.box((-0.37, 0.28, 0), (0.22, 0.2, 0.22), RED)
    for z in (-0.07, -0.025, 0.02, 0.065):
        ra.spike((-0.37, 0.18, z), (0, -1, -0.25), 0.09, 0.02, BONE, 4)

    # Blade (goodbye only), built on the right arm
    blade = Part('blade', 2, RA_O, goodbye=True)
    build_blade(blade)

    # Left arm (monster's left, +x): fist that holds the hammer
    la = Part('leftArm', 3, LA_O)
    la.box((0.36, 1.14, 0), (0.18, 0.5, 0.2), RED)
    la.box((0.37, 0.6, 0), (0.16, 0.4, 0.18), RED_D)
    la.box((0.455, 0.62, 0), (0.02, 0.08, 0.06), BLUE)
    la.spike((0.45, 1.0, 0.0), (1, 0.1, -0.3), 0.12, 0.03, BONE, 5)
    la.box((0.37, 0.38, 0), (0.2, 0.16, 0.2), RED)

    # Hammer (left hand): bone core with red rims, 12 spikes
    hm = Part('hammer', 3, LA_O)
    hm.box((0.37, 0.23, 0), (0.07, 0.16, 0.07), BONE_D)
    hm.box((0.37, 0.11, 0), (0.28, 0.22, 0.28), HAMMER_CORE)
    hm.box((0.37, 0.2, 0), (0.34, 0.04, 0.34), HAMMER_RED)
    hm.box((0.37, 0.02, 0), (0.34, 0.04, 0.34), HAMMER_RED)
    for sx in (-1, 1):
        for sz in (-1, 1):
            hm.box((0.37 + 0.19 * sx, 0.2, 0.09 * sz), (0.04, 0.04, 0.04), BONE)   # rim studs
            hm.box((0.37 + 0.09 * sz, 0.2, 0.19 * sx), (0.04, 0.04, 0.04), BONE)
        hm.spike((0.37 + 0.17 * sx, 0.11, 0), (sx, 0, 0), 0.13, 0.045, BONE, 8)
        hm.spike((0.37, 0.11, 0.17 * sx), (0, 0, sx), 0.13, 0.045, BONE, 8)
        for sz in (-1, 1):
            hm.spike((0.37 + 0.15 * sx, 0.21, 0.15 * sz), (sx, 0.7, sz), 0.1, 0.035, BONE, 6)
            hm.spike((0.37 + 0.15 * sx, 0.0, 0.15 * sz), (sx, -0.8, sz), 0.09, 0.035, BONE, 6)

    def leg(name, frame, origin, sx, eye):
        p = Part(name, frame, origin)
        cx = 0.12 * sx
        p.box((cx, 0.56, 0), (0.2, 0.36, 0.22), RED)
        p.box((cx + 0.105 * sx, 0.58, 0), (0.02, 0.08, 0.06), eye)
        p.box((cx, 0.37, -0.115), (0.12, 0.12, 0.04), BONE)
        p.box((cx, 0.18, 0), (0.18, 0.3, 0.2), RED_D)
        p.box((cx, 0.05, -0.03), (0.2, 0.1, 0.26), RED)
        for dx in (-0.06, 0.0, 0.06):
            p.spike((cx + dx, 0.04, -0.16), (0, -0.35, -1), 0.1, 0.025, BONE, 4)
        return p

    # Reference layout (viewer's left/right when facing the monster): the blade is on the monster's left (+x),
    # the hammer and its fist are on the right (-x). Mirror the arm builds to match; the frames follow the side.
    right_arm = mirror(la, 'rightArm', 2, RA_O)
    hammer = mirror(hm, 'hammer', 2, RA_O)
    left_arm = mirror(ra, 'leftArm', 3, LA_O)
    blade_out = mirror(blade, 'blade', 3, LA_O, goodbye=True)
    parts = [head, body, right_arm, hammer, left_arm, blade_out,
             leg('rightLeg', 4, RL_O, -1, BLUE),
             leg('leftLeg', 5, LL_O, 1, GREEN)]
    return parts


def to_model_space(part):
    """Positions and normals relative to the vanilla pivot, in model space (y down). Returns float arrays."""
    o = part.origin
    pos, nrm, col = [], [], []
    for a, b, c, n, colour in part.tris:
        for p in (a, b, c):
            q = p - o
            pos.append((q[0], -q[1], q[2]))
            nrm.append((n[0], -n[1], n[2]))
            col.append(colour)
    return np.array(pos, np.float32), np.array(nrm, np.float32), np.array(col, np.uint8)


def write_bin(path, parts):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    vdt = np.dtype([('p', '>f4', (3,)), ('n', '>f4', (3,)), ('c', 'u1', (3,))])
    with open(path, 'wb') as f:
        f.write(b'NTM1')
        f.write(struct.pack('>i', len(parts)))
        for part in parts:
            pos, nrm, col = to_model_space(part)
            nv = len(pos)
            nf = nv // 3
            name = part.name.encode('utf-8')
            f.write(struct.pack('>B', len(name)) + name)
            f.write(struct.pack('>BB', part.frame, 1 if part.goodbye else 0))
            f.write(struct.pack('>ii', nv, nf))
            v = np.zeros(nv, dtype=vdt)
            v['p'] = pos
            v['n'] = nrm
            v['c'] = col
            f.write(v.tobytes())
            f.write(np.arange(nv, dtype='>i4').tobytes())


def write_obj(path, parts):
    """One 'o' block per part; v/vn/f indices are global and 1-based (one vn per triangle)."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write('# one-block-per-part body, figure space (y up, feet at 0, front -z, monster right -x), flat-shaded\n')
        v_index = 0
        n_index = 0
        for part in parts:
            f.write(f'o {part.name}\n')
            for a, b, c, n, colour in part.tris:
                r, g, bb = (x / 255.0 for x in colour)
                n_index += 1
                for p in (a, b, c):
                    f.write(f'v {p[0]:.5f} {p[1]:.5f} {p[2]:.5f} {r:.4f} {g:.4f} {bb:.4f}\n')
                f.write(f'vn {n[0]:.4f} {n[1]:.4f} {n[2]:.4f}\n')
                i = v_index
                f.write(f'f {i + 1}//{n_index} {i + 2}//{n_index} {i + 3}//{n_index}\n')
                v_index += 3


def main():
    parts = build_parts()
    total = 0
    print(f'{"part":10s} {"frame":5s} {"gb":3s} {"tris":>6s}')
    for p in parts:
        total += len(p.tris)
        print(f'{p.name:10s} {p.frame:<5d} {"y" if p.goodbye else "-":3s} {len(p.tris):6d}')
    print('total triangles', total)
    all_pts = np.array([t[k] for p in parts for t in p.tris for k in range(3)])
    print('figure bounds x[%.3f,%.3f] y[%.3f,%.3f] z[%.3f,%.3f]' % (
        all_pts[:, 0].min(), all_pts[:, 0].max(), all_pts[:, 1].min(), all_pts[:, 1].max(),
        all_pts[:, 2].min(), all_pts[:, 2].max()))
    write_bin(OUT_BIN, parts)
    write_obj(os.path.join(OUT_DIR, 'nt_body_full.obj'), parts)
    print('wrote', OUT_BIN, os.path.getsize(OUT_BIN), 'bytes')
    print('wrote', os.path.join(OUT_DIR, 'nt_body_full.obj'))


if __name__ == '__main__':
    main()
