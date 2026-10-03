# -*- coding: utf-8 -*-
"""STL 几何体检：连通分量数 + 每条边是否恰好被两个三角形共享（水密/流形）。
CGAL 报告的 Volumes 含外部空间与内部气泡，此脚本给出可打印性判据：
  components == 期望件数 且 open_edges == 0  → 干净。"""
import struct, sys, collections

def load_stl(path):
    with open(path, "rb") as f:
        data = f.read()
    if data[:5] == b"solid" and b"facet" in data[:2000]:   # ASCII STL
        tris, cur = [], []
        for line in data.decode("utf-8", "ignore").splitlines():
            s = line.strip().split()
            if s[:1] == ["vertex"]:
                cur.append((float(s[1]), float(s[2]), float(s[3])))
                if len(cur) == 3:
                    tris.append(tuple(cur)); cur = []
        return tris
    n = struct.unpack("<I", data[80:84])[0]
    tris = []
    off = 84
    for _ in range(n):
        v = struct.unpack("<12fH", data[off:off + 50])
        tris.append(((v[3], v[4], v[5]), (v[6], v[7], v[8]), (v[9], v[10], v[11])))
        off += 50
    return tris

def analyze(path):
    tris = load_stl(path)
    vid, verts = {}, []
    def q(v):                       # 量化去重（1e-4 mm 容差）
        k = (round(v[0], 4), round(v[1], 4), round(v[2], 4))
        if k not in vid:
            vid[k] = len(verts); verts.append(k)
        return vid[k]
    edges = collections.Counter()
    parent = list(range(len(tris) * 3))
    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]; x = parent[x]
        return x
    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb: parent[ra] = rb
    ids = []
    for t in tris:
        a, b, c = (q(v) for v in t)
        ids.append((a, b, c))
        union(a, b); union(b, c)
    for a, b, c in ids:             # 有向边计数：水密流形 = 每条边正反各一次
        edges[(a, b)] += 1; edges[(b, c)] += 1; edges[(c, a)] += 1
    open_e = sum(1 for e, k in edges.items() if edges.get((e[1], e[0]), 0) != 1 or k != 1)
    comps = collections.defaultdict(list)
    for i, (a, b, c) in enumerate(ids):
        comps[find(a)].append(i)
    sizes = sorted((len(v) for v in comps.values()), reverse=True)
    boxes = []
    for root, tr_idx in comps.items():
        pts = [verts[ids[i][j]] for i in tr_idx for j in range(3)]
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]; zs = [p[2] for p in pts]
        boxes.append((len(tr_idx), min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)))
    boxes.sort(reverse=True)
    print(f"\n{path}")
    print(f"  三角形 {len(tris)}  连通分量 {len(comps)}  非流形/开口边 {open_e}")
    for b in boxes[:6]:
        print(f"    分量 {b[0]:6d} 三角  x[{b[1]:.1f},{b[2]:.1f}] "
              f"y[{b[3]:.1f},{b[4]:.1f}] z[{b[5]:.1f},{b[6]:.1f}]")
    return len(comps), open_e

if __name__ == "__main__":
    ok = True
    for p in sys.argv[1:]:
        n, e = analyze(p)
        ok &= (e == 0)
    print("\n水密性:", "PASS（所有边两两配对）" if ok else "FAIL")
