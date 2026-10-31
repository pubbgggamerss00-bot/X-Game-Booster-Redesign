#!/usr/bin/env python3
"""Generate launcher icon PNGs (mipmap) for X Game Booster redesign."""
import os
from PIL import Image, ImageDraw

BASE = "/home/z/my-project/XGameBooster/res"

def make(sz, path):
    img = Image.new("RGBA", (sz, sz), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    m = sz * 0.03
    # dark rounded background
    d.rounded_rectangle([m, m, sz - m, sz - m], radius=sz * 0.22, fill=(11, 15, 20, 255))
    # cyan ring
    w = max(2, sz // 22)
    d.arc([sz * 0.09, sz * 0.09, sz * 0.91, sz * 0.91], 0, 360,
          fill=(56, 189, 248, 255), width=w)
    # green lightning bolt
    b = [(0.56, 0.16), (0.30, 0.55), (0.46, 0.55), (0.40, 0.86),
         (0.70, 0.44), (0.52, 0.44)]
    d.polygon([(x * sz, y * sz) for x, y in b], fill=(34, 197, 94, 255))
    img.save(path)

for q, s in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    out = os.path.join(BASE, "mipmap-" + q)
    os.makedirs(out, exist_ok=True)
    make(s, os.path.join(out, "ic_launcher.png"))
    print("wrote", out + "/ic_launcher.png")
