"""Diagnóstico: ¿quién atraviesa paredes, la trayectoria real o los hops del grafo?

Replica EXACTAMENTE VisibilityUtils.segmentsIntersect de Java (incluye contacto en
extremos / colinealidad => cuenta como intersección).
"""
import csv
import os

EPS = 1e-9
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir))


def load_walls(path):
    walls = []
    with open(path) as f:
        r = csv.reader(f)
        next(r)
        for row in r:
            if row and row[0].strip():
                walls.append((float(row[0]), float(row[1]), float(row[3]), float(row[4])))
    return walls


def cross(o, a, b):
    return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])


def on_segment(p, a, b):
    return (min(a[0], b[0]) - EPS <= p[0] <= max(a[0], b[0]) + EPS
            and min(a[1], b[1]) - EPS <= p[1] <= max(a[1], b[1]) + EPS)


def seg_int(a1, a2, b1, b2):
    d1 = cross(b1, b2, a1)
    d2 = cross(b1, b2, a2)
    d3 = cross(a1, a2, b1)
    d4 = cross(a1, a2, b2)
    if (((d1 > EPS and d2 < -EPS) or (d1 < -EPS and d2 > EPS))
            and ((d3 > EPS and d4 < -EPS) or (d3 < -EPS and d4 > EPS))):
        return True
    if abs(d1) <= EPS and on_segment(a1, b1, b2):
        return True
    if abs(d2) <= EPS and on_segment(a2, b1, b2):
        return True
    if abs(d3) <= EPS and on_segment(b1, a1, a2):
        return True
    if abs(d4) <= EPS and on_segment(b2, a1, a2):
        return True
    return False


def blocking_walls(a, b, walls):
    return [i for i, w in enumerate(walls) if seg_int(a, b, (w[0], w[1]), (w[2], w[3]))]


def analyze_trajectory(output_csv, walls):
    """Agrupa por agente (track por cercanía) y mira si pos[t]->pos[t+1] cruza pared."""
    frames = {}
    with open(output_csv) as f:
        for line in f:
            p = [x.strip() for x in line.strip().split(";")]
            if len(p) < 6:
                continue
            t = float(p[0])
            frames.setdefault(t, []).append((float(p[1]), float(p[2]), p[5]))
    times = sorted(frames)
    # tracking por cercanía
    tracks = {}
    prev = None
    for t in times:
        cur = frames[t]
        if prev is None:
            assign = list(range(len(cur)))
        else:
            assign = []
            used = set()
            for (x, y, _s) in cur:
                best, bd = None, 1e9
                for j, (px, py, _ps, _pt) in enumerate(prev):
                    if j in used:
                        continue
                    d = (x - px) ** 2 + (y - py) ** 2
                    if d < bd:
                        bd, best = d, j
                if best is None:
                    best = len(used)
                used.add(best)
                assign.append(prev[best][3] if best < len(prev) else best)
        prev = [(cur[k][0], cur[k][1], cur[k][2], assign[k]) for k in range(len(cur))]
        for k in range(len(cur)):
            tracks.setdefault(assign[k], []).append((t, cur[k][0], cur[k][1], cur[k][2]))

    total_cross = 0
    per_track = {}
    for tid, pts in tracks.items():
        pts.sort()
        c = 0
        examples = []
        for i in range(len(pts) - 1):
            a = (pts[i][1], pts[i][2])
            b = (pts[i + 1][1], pts[i + 1][2])
            bw = blocking_walls(a, b, walls)
            if bw:
                c += 1
                if len(examples) < 3:
                    examples.append((pts[i][0], a, b, pts[i][3], bw))
        per_track[tid] = (c, len(pts), examples)
        total_cross += c
    return total_cross, per_track


def analyze_hops(hops_csv, walls):
    total = blocked = direct_blocked = 0
    blocked_java = 0
    examples = []
    with open(hops_csv) as f:
        for row in csv.DictReader(f):
            total += 1
            p = (float(row["px"]), float(row["py"]))
            h = (float(row["hx"]), float(row["hy"]))
            if "visJava" in row and row["visJava"] != "":
                if int(row["visJava"]) == 0:
                    blocked_java += 1
            bw = blocking_walls(p, h, walls)
            if bw:
                blocked += 1
                if int(row.get("direct", 0)) == 1:
                    direct_blocked += 1
                if len(examples) < 8:
                    examples.append((p, h, int(row["direct"]), bw))
    return total, blocked, direct_blocked, examples, blocked_java


def main():
    walls = load_walls(os.path.join(REPO, "scenarios", "example", "WALLS.csv"))
    print(f"paredes: {len(walls)}")

    print("\n=== TRAYECTORIA REAL (output.csv) ===")
    tc, per = analyze_trajectory(os.path.join(REPO, "out", "output.csv"), walls)
    print(f"pasos que cruzan pared: {tc}")
    for tid, (c, n, ex) in sorted(per.items()):
        print(f"  track {tid}: {c}/{n-1} pasos cruzan pared")
        for (t, a, b, st, bw) in ex:
            print(f"    t={t:.2f} {st} ({a[0]:.2f},{a[1]:.2f})->({b[0]:.2f},{b[1]:.2f}) paredes={bw}")

    print("\n=== HOPS (hops.csv: px,py -> hx,hy) ===")
    total, blocked, direct_blocked, ex, blocked_java = analyze_hops(
        os.path.join(REPO, "out", "hops.csv"), walls)
    print(f"total={total}")
    print(f"  bloqueados segun JAVA (verdad, sin redondeo): {blocked_java}")
    print(f"  bloqueados segun Python (CSV redondeado 6 dec): {blocked} (direct=1: {direct_blocked})")
    for (p, h, d, bw) in ex:
        print(f"    ({p[0]:.4f},{p[1]:.4f})->({h[0]:.4f},{h[1]:.4f}) direct={d} paredes={bw}")


if __name__ == "__main__":
    main()
