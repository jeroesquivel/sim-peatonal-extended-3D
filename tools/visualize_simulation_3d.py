"""
Vista 3D a 45° de la salida de simulación multiplanta (out/output.csv, formato D10:
tout; x; y; z; vx; vy; state; id).

Apila las plantas una arriba de otra (cada pared se dibuja a su z) y anima los
agentes como puntos en (x, y, z), coloreados por estado. Las escaleras (STAIRS.csv,
opcional) se dibujan como un segmento inclinado del pie al tope.

Uso (desde la raíz del repo):
    python tools/visualize_simulation_3d.py --scenario scenarios/escuela --out out/sim3d.gif

Requisitos: pip install matplotlib pillow
"""

from __future__ import annotations

import argparse
import csv
import os
from collections import defaultdict

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import matplotlib.animation as animation  # noqa: E402
from mpl_toolkits.mplot3d import Axes3D  # noqa: F401,E402  (registra la proyección 3d)

STATE_COLORS = {
    "IDLE": "#95a5a6",
    "WALKING": "#27ae60",
    "APPROACHING": "#2980b9",
    "ARRIVED": "#8e44ad",
    "OCCUPYING": "#e67e22",
    "LEAVING": "#d35400",
    "QUEUEING": "#c0392b",
    "DEAD": "#000000",
}


def parse_walls_3d(path: str) -> list[tuple[float, float, float, float, float]]:
    """WALLS.csv: x1,y1,z1,x2,y2,z2 -> (x1,y1,x2,y2,z) por planta."""
    walls = []
    if not os.path.isfile(path):
        return walls
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader, None)
        for row in reader:
            if not row or not row[0].strip():
                continue
            walls.append((float(row[0]), float(row[1]), float(row[3]), float(row[4]), float(row[2])))
    return walls


def parse_stairs(path: str) -> list[tuple[float, float, float, float, float, float]]:
    """STAIRS.csv: block,x1,y1,z1,x2,y2,z2,width[,speed] -> (x1,y1,z1,x2,y2,z2)."""
    stairs = []
    if not os.path.isfile(path):
        return stairs
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader, None)
        for row in reader:
            if not row or not row[0].strip():
                continue
            stairs.append((float(row[1]), float(row[2]), float(row[3]),
                           float(row[4]), float(row[5]), float(row[6])))
    return stairs


def parse_sim_output_3d(path: str) -> list[tuple[float, list[dict]]]:
    frames: dict[float, list[dict]] = defaultdict(list)
    with open(path, newline="", encoding="utf-8-sig") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = [p.strip() for p in line.split(";")]
            if len(parts) < 7:
                continue
            tout = float(parts[0])
            frames[tout].append({
                "x": float(parts[1]),
                "y": float(parts[2]),
                "z": float(parts[3]),
                "state": parts[6],
            })
    return sorted(frames.items())


def render(scenario: str, output: str, out: str, fps: int, dpi: int, elev: float, azim: float,
           stride: int = 1):
    walls = parse_walls_3d(os.path.join(scenario, "WALLS.csv"))
    stairs = parse_stairs(os.path.join(scenario, "STAIRS.csv"))
    frames = parse_sim_output_3d(output)
    if stride > 1:
        frames = frames[::stride]  # submuestreo para outputs largos
    if not frames:
        raise SystemExit(f"Sin datos en {output}")

    floors = sorted({round(w[4], 6) for w in walls})
    print(f"Plantas: {floors}, paredes: {len(walls)}, escaleras: {len(stairs)}, frames: {len(frames)}")

    fig = plt.figure(figsize=(10, 8))
    ax = fig.add_subplot(111, projection="3d")
    ax.view_init(elev=elev, azim=azim)

    # Paredes estáticas, cada una a su z (plantas apiladas).
    for x1, y1, x2, y2, z in walls:
        ax.plot([x1, x2], [y1, y2], [z, z], color="#2c3e50", linewidth=1.0)
    # Escaleras: segmento inclinado pie -> tope.
    for x1, y1, z1, x2, y2, z2 in stairs:
        ax.plot([x1, x2], [y1, y2], [z1, z2], color="#e67e22", linewidth=2.5, linestyle="--")

    xs = [w[0] for w in walls] + [w[2] for w in walls]
    ys = [w[1] for w in walls] + [w[3] for w in walls]
    if xs and ys:
        ax.set_xlim(min(xs), max(xs))
        ax.set_ylim(min(ys), max(ys))
    if floors:
        ax.set_zlim(min(floors) - 0.5, max(floors) + 1.0)
    ax.set_xlabel("x")
    ax.set_ylabel("y")
    ax.set_zlabel("z (planta)")

    scatter = ax.scatter([], [], [], s=25, depthshade=True)
    title = ax.set_title("")

    def update(i):
        nonlocal scatter
        tout, agents = frames[i]
        scatter.remove()
        x = [a["x"] for a in agents]
        y = [a["y"] for a in agents]
        z = [a["z"] for a in agents]
        c = [STATE_COLORS.get(a["state"], "#7f8c8d") for a in agents]
        scatter = ax.scatter(x, y, z, s=25, c=c, depthshade=True)
        title.set_text(f"t = {tout:.1f} s  |  agentes = {len(agents)}")
        return scatter, title

    anim = animation.FuncAnimation(fig, update, frames=len(frames), interval=1000 / fps, blit=False)

    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    if out.lower().endswith(".mp4"):
        anim.save(out, writer=animation.FFMpegWriter(fps=fps), dpi=dpi)
    else:
        anim.save(out, writer=animation.PillowWriter(fps=fps), dpi=dpi)
    print(f"Guardado: {out}")


def main():
    repo = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir))
    p = argparse.ArgumentParser(description="Vista 3D 45° de out/output.csv (plantas apiladas)")
    p.add_argument("--output", default=os.path.join(repo, "out", "output.csv"))
    p.add_argument("--scenario", default=os.path.join(repo, "scenarios", "example"))
    p.add_argument("--out", default=os.path.join(repo, "out", "simulation_3d.gif"))
    p.add_argument("--fps", type=int, default=10)
    p.add_argument("--dpi", type=int, default=120)
    p.add_argument("--elev", type=float, default=25.0, help="Elevación de cámara (grados)")
    p.add_argument("--azim", type=float, default=-60.0, help="Azimut de cámara (grados, ~45°)")
    p.add_argument("--stride", type=int, default=1, help="Submuestrear 1 de cada N frames (outputs largos)")
    args = p.parse_args()
    render(args.scenario, args.output, args.out, args.fps, args.dpi, args.elev, args.azim, args.stride)


if __name__ == "__main__":
    main()
