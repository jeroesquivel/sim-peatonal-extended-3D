#!/usr/bin/env python3
"""Anima un output del simulador sobre la geometría del escenario.

Uso:
    python3 tools/animate_run.py <scenario_dir> <output.csv> <anim.mp4> [stride]

El output.csv es el que escribe App.java (t; x; y; z; vx; vy; state; id).
`stride` submuestrea frames (default 5 → un frame cada 0.5 s con dt_out=0.1).
"""

import csv
import sys
from collections import defaultdict
from pathlib import Path

import matplotlib.animation as animation
import matplotlib.patches as patches
import matplotlib.pyplot as plt
from matplotlib.collections import EllipseCollection

# Radio físico del agente [m]. El motor spawnea con AGENT_RADIUS = 0.25 m; con
# SFM queda fijo, con CPM varía ~0.15-0.32 según velocidad (el output no exporta
# el radio, así que dibujamos el de spawn — exacto para SFM, promedio para CPM).
AGENT_RADIUS = 0.25

STATE_COLORS = {
    "WALKING": "#1f77b4",      # azul
    "APPROACHING": "#ff7f0e",  # naranja
    "ARRIVED": "#17becf",      # cian
    "QUEUEING": "#d62728",     # rojo
    "OCCUPYING": "#9467bd",    # violeta
    "LEAVING": "#2ca02c",      # verde
    "IDLE": "#7f7f7f",         # gris
}
FALLBACK_COLOR = "#000000"


def read_blocks(path):
    """CSV de bloques rectangulares (block_name, x1, y1, z1, x2, y2, z2)."""
    blocks = []
    if not path.exists():
        return blocks
    with open(path) as f:
        for row in csv.reader(f):
            row = [c.strip() for c in row]
            if not row or row[0] == "block_name" or len(row) < 7:
                continue
            name = row[0]
            x1, y1, x2, y2 = float(row[1]), float(row[2]), float(row[4]), float(row[5])
            blocks.append((name, min(x1, x2), min(y1, y2), abs(x2 - x1), abs(y2 - y1)))
    return blocks


def read_walls(path):
    walls = []
    with open(path) as f:
        for row in csv.reader(f):
            row = [c.strip() for c in row]
            if not row or row[0] in ("x1", "block_name") or len(row) < 6:
                continue
            walls.append((float(row[0]), float(row[1]), float(row[3]), float(row[4])))
    return walls


def read_semaphores(scenario):
    """Devuelve {block_name: (period, green, offset)} de SERVER_PARAMS.csv."""
    semaphores = {}
    path = scenario / "SERVER_PARAMS.csv"
    if not path.exists():
        return semaphores
    with open(path) as f:
        for row in csv.reader(f):
            row = [c.strip() for c in row]
            if len(row) < 3 or row[1].lower() != "semaphore":
                continue
            period = float(row[2])
            green = float(row[3]) if len(row) > 3 and row[3] else period
            offset = float(row[4]) if len(row) > 4 and row[4] else 0.0
            semaphores[row[0]] = (period, green, offset)
    return semaphores


def is_green(t, period, green, offset):
    phase = (t - offset) % period
    return 0.0 <= phase < green


def read_run(path):
    frames = defaultdict(list)
    with open(path, encoding="utf-8-sig") as f:
        for line in f:
            parts = [p.strip() for p in line.split(";")]
            if len(parts) < 7:
                continue
            try:
                t = float(parts[0])
            except ValueError:
                continue
            # Formato D10: tout; x; y; z; vx; vy; state; id
            frames[t].append((float(parts[1]), float(parts[2]), parts[6]))
    return [frames[t] for t in sorted(frames)], sorted(frames)


def corridors(servers, semaphores):
    """Infiere las sendas peatonales a partir de pares de semáforos enfrentados.

    Agrupa los semáforos por "lado" (el nombre sin el token direccional
    N/S/E/W, p.ej. SEM_TOP_W y SEM_TOP_E → SEM_TOP) y, para cada par, toma el
    hueco entre sus regiones como la senda que cruza la calle. Devuelve una
    lista de (x, y, w, h, horizontal) — horizontal=True si el cruce corre en x.
    """
    groups = defaultdict(list)
    for name, x, y, w, h in servers:
        if name not in semaphores:
            continue
        toks = [t for t in name.replace("_SERVER", "").split("_")
                if t not in ("N", "S", "E", "W")]
        groups["_".join(toks)].append((x, y, w, h))

    out = []
    for members in groups.values():
        if len(members) < 2:
            continue
        members.sort(key=lambda m: (m[0], m[1]))
        a, b = members[0], members[-1]
        ax0, ay0, aw, ah = a
        bx0, by0, bw, bh = b
        # Separación en x vs y para decidir orientación del cruce.
        gap_x = bx0 - (ax0 + aw)
        gap_y = by0 - (ay0 + ah)
        if gap_x >= gap_y:  # cruce horizontal: hueco en x, alto = solape en y
            y0 = max(ay0, by0)
            y1 = min(ay0 + ah, by0 + bh)
            out.append((ax0 + aw, y0, gap_x, y1 - y0, True))
        else:               # cruce vertical
            x0 = max(ax0, bx0)
            x1 = min(ax0 + aw, bx0 + bw)
            out.append((x0, ay0 + ah, x1 - x0, gap_y, False))
    return out


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    scenario = Path(sys.argv[1])
    run_csv = Path(sys.argv[2])
    out_path = Path(sys.argv[3])
    stride = int(sys.argv[4]) if len(sys.argv) > 4 else 5

    walls = read_walls(scenario / "WALLS.csv")
    servers = read_blocks(scenario / "SERVERS.csv")
    exits = read_blocks(scenario / "EXITS.csv")
    generators = read_blocks(scenario / "GENERATORS.csv")
    semaphores = read_semaphores(scenario)
    frames, times = read_run(run_csv)
    frames = frames[::stride]
    times = times[::stride]

    fig, ax = plt.subplots(figsize=(12, 5.5))
    ax.set_aspect("equal")

    # Fondo "calle" gris sobre todo el dominio (bounding box de las paredes).
    if walls:
        xs = [c for x1, y1, x2, y2 in walls for c in (x1, x2)]
        ys = [c for x1, y1, x2, y2 in walls for c in (y1, y2)]
        ax.add_patch(patches.Rectangle(
            (min(xs), min(ys)), max(xs) - min(xs), max(ys) - min(ys),
            facecolor="#d9d9d9", edgecolor="none", zorder=-2))

    # Veredas (zonas de aparición/caminables) en verde claro, alrededor de los
    # generadores: damos vuelta la franja del generador hacia adentro 1.5x.
    for name, x, y, w, h in generators:
        pad = 0.0
        ax.add_patch(patches.Rectangle((x - pad, y - pad), w + 2 * pad, h + 2 * pad,
                     facecolor="#cdebc5", edgecolor="none", zorder=-1))

    # Sendas peatonales: rectángulo blanco + rayas de cebra, inferidas de los
    # pares de semáforos enfrentados.
    STRIPE = 0.6
    for cx, cy, cw, ch, horiz in corridors(servers, semaphores):
        ax.add_patch(patches.Rectangle((cx, cy), cw, ch, facecolor="white",
                     edgecolor="none", zorder=-1))
        if horiz:
            n = max(1, int(cw / (2 * STRIPE)))
            step = cw / n
            for k in range(n):
                ax.add_patch(patches.Rectangle((cx + k * step + step * 0.25, cy),
                             step * 0.5, ch, facecolor="#9a9a9a", edgecolor="none",
                             zorder=-1))
        else:
            n = max(1, int(ch / (2 * STRIPE)))
            step = ch / n
            for k in range(n):
                ax.add_patch(patches.Rectangle((cx, cy + k * step + step * 0.25),
                             cw, step * 0.5, facecolor="#9a9a9a", edgecolor="none",
                             zorder=-1))

    for x1, y1, x2, y2 in walls:
        ax.plot([x1, x2], [y1, y2], color="black", lw=2.5, zorder=1)

    semaphore_patches = {}
    for name, x, y, w, h in servers:
        if name in semaphores:
            p = patches.Rectangle((x, y), w, h, facecolor="#2ca02c",
                                  alpha=0.5, edgecolor="green", zorder=2)
            semaphore_patches[name] = p
            ax.add_patch(p)
        else:
            # Servidores no-semáforo (cajas/aulas): violeta tenue.
            ax.add_patch(patches.Rectangle((x, y), w, h, facecolor="#9467bd",
                         alpha=0.15, edgecolor="#9467bd", zorder=0))

    for name, x, y, w, h in exits:
        ax.plot([x, x + w], [y, y + h] if h else [y, y], color="red", lw=4, zorder=2)

    # Agentes como círculos de radio físico real (en metros, escalan con los
    # ejes), no markers de tamaño fijo: así la superposición refleja la del motor.
    agents = EllipseCollection(
        widths=2 * AGENT_RADIUS, heights=2 * AGENT_RADIUS, angles=0, units="xy",
        offsets=[(0, 0)], offset_transform=ax.transData,
        facecolors=[FALLBACK_COLOR], edgecolors="black", linewidths=0.5, zorder=3)
    ax.add_collection(agents)
    time_text = ax.text(0.5, 1.02, "", transform=ax.transAxes, va="bottom",
                        ha="center", fontsize=13, fontweight="bold")

    # Leyenda solo con los estados que realmente aparecen en este run.
    present = {s for pts in frames for _, _, s in pts}
    handles = [plt.Line2D([], [], marker="o", ls="", color=c, label=s)
               for s, c in STATE_COLORS.items() if s in present]
    ax.legend(handles=handles, loc="center left", bbox_to_anchor=(1.0, 0.5),
              fontsize=8, frameon=False)
    ax.margins(0.05)
    fig.tight_layout(rect=[0, 0, 1, 0.94])

    def update(i):
        pts = frames[i]
        t = times[i]
        agents.set_offsets([(x, y) for x, y, _ in pts] or [(0, 0)])
        agents.set_facecolors([STATE_COLORS.get(s, FALLBACK_COLOR) for _, _, s in pts]
                              or [FALLBACK_COLOR])
        for name, p in semaphore_patches.items():
            period, green, offset = semaphores[name]
            g = is_green(t, period, green, offset)
            p.set_facecolor("#2ca02c" if g else "#d62728")
            p.set_edgecolor("green" if g else "red")
        time_text.set_text(f"t = {t:.1f} s")
        return [agents, time_text, *semaphore_patches.values()]

    anim = animation.FuncAnimation(fig, update, frames=len(frames), blit=True)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    anim.save(out_path, writer=animation.FFMpegWriter(fps=15, bitrate=2000))
    print(f"guardado: {out_path}  ({len(frames)} frames, t final {times[-1]:.1f} s)")


if __name__ == "__main__":
    main()
