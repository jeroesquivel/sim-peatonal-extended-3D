"""
Vista 3D a 45° de la salida de simulación multiplanta (out/output.csv, formato D10:
tout; x; y; z; vx; vy; state; id).

Apila las plantas una arriba de otra (cada pared se dibuja a su z) y anima los
agentes como puntos en (x, y, z), coloreados por estado. Las escaleras (STAIRS.csv,
opcional) se dibujan como un perfil de peldaños reales (huella + contrahuella) del
pie al tope; cada tramo de STAIRS.csv (p. ej. los dos tramos de un switchback con
landing intermedio) se dibuja de forma independiente.

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

STAIR_COLOR = "#e67e22"
STAIR_RISER_H = 0.18  # alzada objetivo por escalón (m), típico de escalera real


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


def draw_stair_steps(ax, x1: float, y1: float, z1: float,
                      x2: float, y2: float, z2: float,
                      color: str = STAIR_COLOR, linewidth: float = 2.0) -> None:
    """Dibuja un tramo de escalera (pie -> tope) como perfil de peldaños reales
    (huella/tread horizontal + contrahuella/riser vertical), en vez de una única
    línea inclinada punteada.

    N = round(|z2-z1| / STAIR_RISER_H) escalones (mínimo 1). La huella de cada
    escalón sale de repartir el largo horizontal del tramo en N partes iguales
    (así el perfil cierra exacto en el tope, sin restos, sea cual sea el largo
    real del tramo). Cada escalón queda a una z constante en su huella y sube
    de golpe en la contrahuella (escalonado, no interpolado suave), como una
    escalera real vista de perfil. Reutiliza `ax.plot(xs, ys, zs)`: la misma
    llamada de matplotlib (proyección 3D + `view_init`) que ya usa el resto del
    script para dibujar paredes y agentes; no hay una función de proyección propia.

    Funciona igual para cualquier tramo independiente (p. ej. los dos tramos de
    un switchback con landing intermedio): cada fila de STAIRS.csv es un tramo
    recto propio, así que basta llamar esta función una vez por fila.
    """
    dz = z2 - z1
    if abs(dz) < 1e-6:
        # Tramo sin desnivel (no debería darse en una escalera real): fallback a línea recta.
        ax.plot([x1, x2], [y1, y2], [z1, z2], color=color, linewidth=linewidth, linestyle="--")
        return
    n = max(1, round(abs(dz) / STAIR_RISER_H))
    dx = x2 - x1
    dy = y2 - y1
    for i in range(n):
        t0 = i / n
        t1 = (i + 1) / n
        xa, ya, za = x1 + dx * t0, y1 + dy * t0, z1 + dz * t0
        xb, yb, zb = x1 + dx * t1, y1 + dy * t1, z1 + dz * t1
        # Huella (tread): segmento horizontal a la altura za, antes de subir el escalón.
        ax.plot([xa, xb], [ya, yb], [za, za], color=color, linewidth=linewidth)
        # Contrahuella (riser): segmento vertical al final de la huella, sube de za a zb.
        ax.plot([xb, xb], [yb, yb], [za, zb], color=color, linewidth=linewidth)


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
           stride: int = 1, snapshot_t: float | None = None):
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
    # Escaleras: perfil de peldaños real (huella + contrahuella) por tramo, pie -> tope.
    # Cada fila de STAIRS.csv es un tramo independiente (p. ej. los dos tramos de un
    # switchback con landing intermedio), así que no hace falta lógica especial acá.
    for x1, y1, z1, x2, y2, z2 in stairs:
        draw_stair_steps(ax, x1, y1, z1, x2, y2, z2)

    xs = [w[0] for w in walls] + [w[2] for w in walls]
    ys = [w[1] for w in walls] + [w[3] for w in walls]
    if xs and ys:
        ax.set_xlim(min(xs), max(xs))
        ax.set_ylim(min(ys), max(ys))
    if floors:
        ax.set_zlim(min(floors) - 0.5, max(floors) + 1.0)
    ax.set_xlabel("x [m]")
    ax.set_ylabel("y [m]")
    ax.set_zlabel("z [m] (planta)")

    scatter = ax.scatter([], [], [], s=25, depthshade=True)
    title = ax.set_title("")

    # Modo snapshot: un único frame (el más cercano a snapshot_t) a PNG, SIN el
    # título t/agentes (las condiciones van en el caption del informe, no en la
    # figura — regla de la cátedra).
    if snapshot_t is not None:
        i = min(range(len(frames)), key=lambda k: abs(frames[k][0] - snapshot_t))
        tout, agents = frames[i]
        scatter.remove()
        ax.scatter([a["x"] for a in agents], [a["y"] for a in agents],
                   [a["z"] for a in agents],
                   s=25, c=[STATE_COLORS.get(a["state"], "#7f8c8d") for a in agents],
                   depthshade=True)
        os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
        fig.savefig(out, dpi=dpi, bbox_inches="tight", pad_inches=0.4)
        print(f"Guardado: {out}  (snapshot t={tout:.1f} s, {len(agents)} agentes)")
        return

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
    p.add_argument("--snapshot", type=float, default=None, metavar="T",
                   help="En vez de animar, guarda un PNG del frame más cercano a t=T (sin título)")
    args = p.parse_args()
    render(args.scenario, args.output, args.out, args.fps, args.dpi, args.elev, args.azim,
           args.stride, args.snapshot)


if __name__ == "__main__":
    main()
