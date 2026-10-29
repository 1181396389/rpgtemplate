#!/usr/bin/env python3
"""一无所有 形态 4（脊骨鞭）与 形态 5（全收）的右手显示部件，写入 hands.bin 的第 2 帧（右臂帧）。

做法（不重新生成已有部件，原字节保持不变）：
1. 读取现有 hands.bin 的 NTM1 记录；
2. 把锤子（hammer）的 flag 从 0 改为 6：形态 4/5 时隐藏锤子，其余形态不变；
3. 把两个新部件（whip flag 8、muscle flag 10）追加到末尾，并更新计数；
4. 写回前备份原文件。

新部件的几何来自 workspace 中的 Java 物品模型（nt_form_*.json）和贴图（nt_form_*.png）：
- 元素按 from/to 和单轴旋转（origin/axis/angle）得到 8 个角点，面用双线性插值参数化；
- 每个面按贴图像素（每单位 4 像素）切成小格，颜色取贴图像素；同一行内相邻同色格合并成一个四边形；
- 坐标：物品坐标（16 单位 = 1 方块）→ 手部挂点。物品 x 轴沿武器长轴指向 D（右手侧，向上外侧，与刀形态同角度），物品 y 轴指向 t2（近似朝上）。
  映射 P = GRIP + R·((p − A)/16)，A 为物品锚点（手的位置）。

用法：python3 gen_nt_forms_parts.py [--dry-run]
"""
import math
import os
import shutil
import struct
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_nt_mesh_blocky import Part, to_model_space  # noqa: E402

HANDS_BIN = "/home/user/mod-repo/build0414-resources/assets/rpgtemplate/models/nothing_there/hands.bin"
BACKUP_DIR = "/home/user/archive/nt-forms-2026-10-29"
HANDS_BIN_BASE = os.path.join(BACKUP_DIR, "hands.bin.before-forms")
WORK = "/home/user/workspace/nt-weapon-forms"

RA_O = np.array([-0.3125, 1.375, 0.0])
GRIP = np.array([-0.49, 0.98, 0.0])   # same right-hand grip as the hammer (HAMMER_GRIP in gen_nt_hands)
# Long-axis direction from the grip, body space (y up). Whip hangs down from the hand (倒过来, tip down);
# muscle stays straight up (掰正).
WHIP_DIR = (0.0, -1.0, 0.0)
MUSCLE_DIR = (0.0, 1.0, 0.0)

FLAG_HAMMER_OFF = 6   # hammer: hidden in form 4 and 5
FLAG_WHIP = 8         # 脊骨鞭 (form 4)
FLAG_MUSCLE = 10      # 全收 (form 5)

FORMS = [
    # name, item model json, texture png, anchor in item units (the hand), flag
    ("nt_form_whip", "whip", (1.0, 8.0, 8.0), FLAG_WHIP),
    ("nt_form_muscle", "muscle", (1.0, 8.0, 8.0), FLAG_MUSCLE),
]
ITEM_ROOT = os.path.join(WORK, "{}/assets/rpgtemplate")  # {} = whip | muscle


def basis(direction):
    D = np.asarray(direction, float)
    D = D / np.linalg.norm(D)                              # long axis
    outward = np.array([-1.0, 0.0, 0.0])                  # right side: outward is -x
    t2 = outward - np.dot(outward, D) * D                  # item +y points outward
    t2 = t2 / np.linalg.norm(t2)
    t3 = np.cross(D, t2)
    R = np.column_stack([D, t2, t3])
    assert np.linalg.det(R) > 0.99
    return R


def rotate_corner(p, rot):
    if not rot:
        return np.asarray(p, float)
    axis = rot["axis"]
    ang = math.radians(rot["angle"])
    o = np.asarray(rot["origin"], float)
    x, y, z = np.asarray(p, float) - o
    c, s = math.cos(ang), math.sin(ang)
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return np.array([x, y, z]) + o


def face_corners(f, t, fn):
    """(u0v0, u1v0, u1v1, u0v1) corners of a face, as seen from outside (Minecraft face UV convention)."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if fn == "north":   # -z face: u runs from x1 to x0, v from top (y1) to bottom
        return [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)]
    if fn == "south":
        return [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)]
    if fn == "east":
        return [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)]
    if fn == "west":
        return [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)]
    if fn == "up":
        return [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]
    return [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]   # down


def bilerp(c, u, v):
    c00, c10, c11, c01 = c
    return ((1 - u) * (1 - v)) * c00 + (u * (1 - v)) * c10 + (u * v) * c11 + ((1 - u) * v) * c01


def build_part(name, frame, json_path, png_path, anchor, flag, R):
    import json
    model = json.load(open(json_path, encoding="utf-8"))
    atlas = np.asarray(Image.open(png_path).convert("RGB"))
    aw, ah = atlas.shape[1], atlas.shape[0]
    part = Part(name, frame, RA_O)
    part.flag = flag
    anchor = np.asarray(anchor, float)
    hint_pts = []
    for el in model["elements"]:
        f, t = el["from"], el["to"]
        rot = el.get("rotation")
        centre = rotate_corner(((f[0] + t[0]) / 2, (f[1] + t[1]) / 2, (f[2] + t[2]) / 2), rot)

        def to_body(p):
            return GRIP + R @ ((rotate_corner(p, rot) - anchor) / 16.0)
        hint = to_body(centre)
        hint_pts.append(hint)
        for fn, face in el["faces"].items():
            uv = face["uv"]               # Java uv in 0..16 space
            px0 = int(round(uv[0] * aw / 16.0))
            py0 = int(round(uv[1] * ah / 16.0))
            px1 = int(round(uv[2] * aw / 16.0))
            py1 = int(round(uv[3] * ah / 16.0))
            w, h = px1 - px0, py1 - py0
            if w <= 0 or h <= 0:
                continue
            corners = [np.asarray(c, float) for c in face_corners(f, t, fn)]
            corners = [rotate_corner(c, rot) for c in corners]
            # greedy rectangle merge of equal-colour texels (right first, then down)
            seen = np.zeros((h, w), bool)
            for j in range(h):
                for i in range(w):
                    if seen[j, i]:
                        continue
                    col = tuple(int(v) for v in atlas[py0 + j, px0 + i])
                    k = i + 1
                    while k < w and not seen[j, k] and tuple(int(v) for v in atlas[py0 + j, px0 + k]) == col:
                        k += 1
                    jj = j + 1
                    while jj < h and all((not seen[jj, q]) and tuple(int(v) for v in atlas[py0 + jj, px0 + q]) == col for q in range(i, k)):
                        jj += 1
                    seen[j:jj, i:k] = True
                    u0, u1 = i / w, k / w
                    v0, v1 = j / h, jj / h
                    p00 = to_body(bilerp(corners, u0, v0))
                    p10 = to_body(bilerp(corners, u1, v0))
                    p11 = to_body(bilerp(corners, u1, v1))
                    p01 = to_body(bilerp(corners, u0, v1))
                    part.quad(p00, p10, p11, p01, col, hint)
    return part


def mirror_part(src, name, frame, flag):
    """Left-side copy: reflect x (body space), keep outward normals, swap winding so faces stay outward."""
    S = np.array([-1.0, 1.0, 1.0])
    out = Part(name, frame, src.origin * S)
    out.flag = flag
    for a, b, c, n, col in src.tris:
        out.tris.append((a * S, c * S, b * S, n * S, col))
    return out


def rewrite_hands(new_parts, dry_run=False):
    # always build from the original base (before the forms were added), so the script can be re-run
    raw = open(HANDS_BIN_BASE, "rb").read()
    assert raw[:4] == b"NTM1", "bad hands.bin header"
    count = struct.unpack(">i", raw[4:8])[0]
    p = 8
    records = []
    hammer_offset = None
    for _ in range(count):
        start = p
        nl = raw[p]
        name = raw[p + 1:p + 1 + nl].decode("utf-8")
        p += 1 + nl
        frame, flag = raw[p], raw[p + 1]
        flag_pos = p + 1
        nv, nf = struct.unpack(">ii", raw[p + 2:p + 10])
        p += 10 + nv * (12 + 12 + 3) + nf * 12
        records.append((name, raw[start:p]))
        if name == "hammer":
            assert frame == 2 and flag == 0, (frame, flag)
            hammer_offset = (len(records) - 1, flag_pos - start)
    assert hammer_offset is not None, "hammer part not found"
    idx, rel = hammer_offset
    rec = bytearray(records[idx][1])
    rec[rel] = FLAG_HAMMER_OFF
    records[idx] = ("hammer", bytes(rec))
    body = bytearray()
    body += b"NTM1" + struct.pack(">i", len(records) + len(new_parts))
    for _, data in records:
        body += data
    for part in new_parts:
        pos, nrm, col = to_model_space(part)
        nv = len(pos)
        nf = nv // 3
        name = part.name.encode("utf-8")
        body += struct.pack(">B", len(name)) + name
        body += struct.pack(">BB", part.frame, part.flag)
        body += struct.pack(">ii", nv, nf)
        vdt = np.dtype([("p", ">f4", (3,)), ("n", ">f4", (3,)), ("c", "u1", (3,))])
        v = np.zeros(nv, dtype=vdt)
        v["p"] = pos
        v["n"] = nrm
        v["c"] = col
        body += v.tobytes()
        body += np.arange(nv, dtype=">i4").tobytes()
    if dry_run:
        print("dry run: would write", len(body), "bytes,", len(records) + len(new_parts), "parts")
        return
    os.makedirs(BACKUP_DIR, exist_ok=True)
    backup = HANDS_BIN_BASE
    if not os.path.exists(backup):
        shutil.copy2(HANDS_BIN, backup)
    tmp = HANDS_BIN + ".tmp"
    with open(tmp, "wb") as f:
        f.write(body)
    os.replace(tmp, HANDS_BIN)
    print("wrote", HANDS_BIN, len(body), "bytes; backup", backup)


def main():
    parts = []
    for name, key, anchor, flag in FORMS:
        R = basis(WHIP_DIR if key == "whip" else MUSCLE_DIR)
        root = ITEM_ROOT.format(key)
        jp = os.path.join(root, "models", "item", f"{name}.json")
        pp = os.path.join(root, "textures", "item", f"{name}.png")
        part = build_part(name.replace("nt_form_", ""), 2, jp, pp, anchor, flag, R)
        print(name, "tris", len(part.tris), "flag", flag)
        parts.append(part)
        if key == "muscle":
            # 全收 is in both hands: the left copy is the mirrored right one, on the leftArm frame (3)
            left = mirror_part(part, "muscle_l", 3, flag)
            print(name, "left copy tris", len(left.tris), "frame 3")
            parts.append(left)
    dry = "--dry-run" in sys.argv
    rewrite_hands(parts, dry_run=dry)


if __name__ == "__main__":
    main()
