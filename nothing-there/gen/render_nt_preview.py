"""Software z-buffer preview of nt_body_full.obj (figure space, y up), lit with vertex colours."""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

OBJ = '/home/user/workspace/nt-body-3d/nt_body_full.obj'
WIDTH_UNITS = 300
OUT = sys.argv[1] if len(sys.argv) > 1 else '/home/user/workspace/nt-body-model-preview-v2.png'
FONT = '/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc'

def load_obj(path):
    objects = {}
    cur = None
    verts, cols, norms = [], [], []
    for line in open(path):
        if line.startswith('o '):
            cur = line[2:].strip()
            objects.setdefault(cur, [])
        elif line.startswith('v '):
            p = line.split()
            verts.append([float(p[1]), float(p[2]), float(p[3])])
            cols.append([float(p[4]), float(p[5]), float(p[6])])
        elif line.startswith('vn '):
            p = line.split()
            norms.append([float(p[1]), float(p[2]), float(p[3])])
        elif line.startswith('f '):
            idx = [int(t.split('//')[0]) - 1 for t in line.split()[1:]]
            objects[cur].append(idx)
    V = np.array(verts); C = np.array(cols); N = np.array(norms)
    return objects, V, C, N

def render(objects, V, C, N, theta_deg, include, S=280, W=600, Hh=900):
    th = np.radians(theta_deg)
    R = np.array([[np.cos(th), 0, np.sin(th)], [0, 1, 0], [-np.sin(th), 0, np.cos(th)]])
    Vr = V @ R.T
    Nr = N @ R.T
    sx = -Vr[:, 0] * S + W / 2
    sy = -(Vr[:, 1] - 1.0) * S + Hh * 0.55
    depth = -Vr[:, 2]
    zbuf = np.full((Hh, W), -np.inf)
    img = np.zeros((Hh, W, 3))
    L = np.array([-0.4, 0.6, -0.7]); L = L / np.linalg.norm(L)
    base = 0
    tris = []
    for name, faces in objects.items():
        if name not in include:
            continue
        for f in faces:
            tris.append(f)
    for a, b, c in tris:
        x = np.array([sx[a], sx[b], sx[c]]); y = np.array([sy[a], sy[b], sy[c]])
        x0, x1 = max(int(np.floor(x.min())), 0), min(int(np.ceil(x.max())), W - 1)
        y0, y1 = max(int(np.floor(y.min())), 0), min(int(np.ceil(y.max())), Hh - 1)
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
        d = w0 * depth[a] + w1 * depth[b] + w2 * depth[c]
        fn = np.cross(Vr[b] - Vr[a], Vr[c] - Vr[a]); fn = fn / (np.linalg.norm(fn) + 1e-12)
        shade = 0.32 + 0.68 * max(0.0, float(fn @ L)) + 0.10 * max(0.0, float(-fn @ L))
        col = (C[a] + C[b] + C[c]) / 3.0 * shade
        zs = zbuf[y0:y1 + 1, x0:x1 + 1]
        zi = zs
        upd = inside & (d > zi)
        zs[upd] = d[upd]
        img[y0:y1 + 1, x0:x1 + 1][upd] = col
    out = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    bg = Image.new('RGB', (W, Hh), (22, 20, 28))
    mask = Image.fromarray(((zbuf > -np.inf) * 255).astype(np.uint8))
    bg.paste(out, (0, 0), mask)
    return bg

def main():
    objects, V, C, N = load_obj(OBJ)
    base = [k for k in objects if k != 'blade']
    names = list(objects.keys())
    panels = [
        ('正面 3/4（含利刃）', render(objects, V, C, N, -35, names), 'front'),
        ('利刃侧 3/4', render(objects, V, C, N, 70, names), 'blade'),
        ('背面', render(objects, V, C, N, 180, base), 'back'),
        ('锤侧', render(objects, V, C, N, -90, base), 'hammer'),
    ]
    W, Hh = panels[0][1].size
    gap = 24
    canvas = Image.new('RGB', (W * 4 + gap * 3 + 40, Hh + 150), (12, 11, 16))
    d = ImageDraw.Draw(canvas)
    f1 = ImageFont.truetype(FONT, 36, index=2)
    f2 = ImageFont.truetype(FONT, 24, index=2)
    d.text((24, 18), '一无所有 · 人形态 3D 模型（方块风格，低面数，锤与利刃重点建模）', font=f1, fill=(235, 226, 200))
    d.text((24, 70), '模型高约 2.2 格（游戏中再放大 1.6 倍，约 3 格）。以下为软件光照渲染，用于预览几何，不是游戏内画面。',
           font=f2, fill=(170, 160, 140))
    x = 20
    for label, img, _ in panels:
        canvas.paste(img, (x, 130))
        d.text((x + 10, 100), label, font=f2, fill=(230, 120, 110))
        x += W + gap
    canvas.save(OUT)
    print('saved', OUT, canvas.size)

main()
