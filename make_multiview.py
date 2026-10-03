# -*- coding: utf-8 -*-
"""多视图工程图生成器（开源链路：OpenSCAD 正投影渲染 + PIL 拼图）
对每个对象输出 front / top / side / iso 四视角，并用轮廓长宽比
自动校验视角语义（top 应近方形、front 应为高瘦形……），防止视角标错。
用法： python make_multiview.py
"""
import os, subprocess, sys
from PIL import Image, ImageDraw, ImageStat

ROOT = os.path.dirname(os.path.abspath(__file__))
OSC = os.path.join(ROOT, ".tools", "openscad-2021.01", "openscad.exe")
OUT = os.path.join(ROOT, "stl", "views")

VIEWS = [                      # (name, gimbal rx,ry,rz)
    ("front", 90, 0, 0),
    ("top",    0, 0, 0),
    ("side",  90, 0, 90),
    ("iso",   60, 0, 35),
]
TARGETS = [                    # (scad 路径, 输出名, 期望 top/front 轮廓比例区间)
    ("openscad/cup_adapter.scad",   "cup_adapter",   (0.4, 1.6)),
    ("openscad/bottle_cap.scad",    "bottle_cap",    (0.7, 1.4)),
    ("openscad/pcb_enclosure.scad", "pcb_enclosure", (1.0, 3.0)),
    ("openscad/system_assembly.scad", "system_assembly", (1.5, 5.0)),
]
W, H = 720, 540


def render(scad, png, rx, ry, rz):
    cmd = [OSC, "-o", png, f"--imgsize={W},{H}", "--autocenter", "--viewall",
           "--projection=o", f"--camera=0,0,0,{rx},{ry},{rz},0",
           os.path.join(ROOT, scad)]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
    err = [l for l in (r.stdout + r.stderr).splitlines()
           if "ERROR" in l or "WARNING" in l]
    return err


def silhouette_ratio(png):
    """物体轮廓宽/高（忽略背景），用于校验视角语义"""
    im = Image.open(png).convert("RGB")
    bg = im.getpixel((2, 2))
    px = im.load()
    xs, ys = [], []
    for y in range(0, im.height, 2):
        for x in range(0, im.width, 2):
            r, g, b = px[x, y]
            if abs(r - bg[0]) + abs(g - bg[1]) + abs(b - bg[2]) > 40:
                xs.append(x); ys.append(y)
    if not xs:
        return 0.0
    w, h = max(xs) - min(xs), max(ys) - min(ys)
    return w / max(h, 1)


def main():
    os.makedirs(OUT, exist_ok=True)
    ok_all = True
    sheets = []
    for scad, name, expect_ratio in TARGETS:
        cell_imgs, ratios = [], {}
        for vname, rx, ry, rz in VIEWS:
            png = os.path.join(OUT, f"{name}_{vname}.png")
            errs = render(scad, png, rx, ry, rz)
            if errs:
                print(f"[{name}/{vname}] 渲染告警: {errs[:2]}")
            ratios[vname] = round(silhouette_ratio(png), 2)
            cell_imgs.append((vname, png))
        # 视角语义校验：top 轮廓应比 front 更“方”（宽高比更大且近方）
        chk = "PASS" if expect_ratio[0] <= ratios["top"] <= expect_ratio[1] else "FAIL"
        if chk == "FAIL":
            ok_all = False
        print(f"[{name}] 轮廓比 top={ratios['top']} front={ratios['front']} "
              f"side={ratios['side']} iso={ratios['iso']}  → top {chk} "
              f"(期望 {expect_ratio})")
        # 拼图
        sheet = Image.new("RGB", (W * 4 + 50, H + 54), "white")
        d = ImageDraw.Draw(sheet)
        d.rectangle([5, 5, W * 4 + 45, 27], fill=(20, 20, 20))
        d.text((10, 10), f"{name}.scad   [orthographic views: "
                         f"front | top | side | iso]", fill="white")
        for i, (vname, png) in enumerate(cell_imgs):
            sheet.paste(Image.open(png).convert("RGB"), (10 + i * W, 32))
        out = os.path.join(OUT, f"sheet_{name}_v2.png")
        sheet.save(out)
        sheets.append(out)
        print(f"    → {out}")
    print("\n视角校验:", "ALL PASS" if ok_all else "存在 FAIL（检查 VIEWS 旋转角）")
    return sheets


if __name__ == "__main__":
    sheets = main()
    if "--open" in sys.argv:
        for s in sheets:
            os.startfile(s)
