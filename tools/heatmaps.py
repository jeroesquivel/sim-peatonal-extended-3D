#!/usr/bin/env python3
"""Mapas de calor (Task 5 — opcional del enunciado).

Dos tipos, ambos por planta (PB z=0 / P1 z=3), con las paredes superpuestas:

  --kind densidad : ocupación media por celda = agentes dentro de la celda
                    promediados sobre todos los frames del output. Muestra dónde
                    se aglomera la gente (pasillos, escaleras, kiosco, entradas).

  --kind tevac    : para cada celda, el tiempo de evacuación MEDIO de los agentes
                    cuyo ORIGEN (primer frame) cae en esa celda. Muestra qué zonas
                    de origen (aulas) tardan más en evacuar. Sólo tiene sentido
                    sobre un output de evacuación.

Colormaps SECUENCIALES de un solo hue (guía dataviz: magnitud = un hue claro→oscuro,
nunca rainbow): ``Blues`` para densidad, ``Reds`` para tiempos de evacuación (más
rojo = más lento). Las celdas sin dato quedan en gris. La barra de color es la
leyenda de magnitud.

Uso:
  python tools/heatmaps.py --scenario scenarios/escuela \
      --output out/sweeps/evacuacion/v120/seed1/output.csv \
      --kind densidad --out out/heatmap_densidad_evac.png --title "Evacuación (N=120)"
"""
from __future__ import annotations

import argparse
import csv
import os
import sys

import numpy as np
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

sys.path.insert(0, os.path.dirname(__file__))
import sweep_lib  # noqa: E402

FLOOR_EPS = 0.4          # tolerancia en z para asignar un agente a una planta
_COLOR_WALL = "#2c3e50"
_COLOR_EMPTY = "#e8e8e6"  # celdas sin dato (gris neutro, coherente con el grid del informe)


def parse_walls(scenario: str, floor: float) -> list[tuple]:
    """Paredes (x1,y1,x2,y2) de la planta ``floor`` desde WALLS.csv."""
    path = os.path.join(scenario, "WALLS.csv")
    walls: list[tuple] = []
    if not os.path.isfile(path):
        return walls
    with open(path, encoding="utf-8-sig") as f:
        r = csv.reader(f)
        next(r, None)
        for row in r:
            if len(row) < 6:
                continue
            try:
                x1, y1, z1 = float(row[0]), float(row[1]), float(row[2])
                x2, y2 = float(row[3]), float(row[4])
            except ValueError:
                continue
            if abs(z1 - floor) <= 1e-6:
                walls.append((x1, y1, x2, y2))
    return walls


def density_grid(csv_path, zlevel, cell, x0, y0, x1, y1):
    nx = max(1, int(round((x1 - x0) / cell)))
    ny = max(1, int(round((y1 - y0) / cell)))
    grid = np.zeros((ny, nx))
    frames = sweep_lib.load_frames(csv_path)
    nfr = len(frames) or 1
    for _t, agents in frames.items():
        for a in agents:
            if abs(a["z"] - zlevel) >= FLOOR_EPS:
                continue
            ix = int((a["x"] - x0) / cell)
            iy = int((a["y"] - y0) / cell)
            if 0 <= ix < nx and 0 <= iy < ny:
                grid[iy, ix] += 1
    return grid / nfr


def evac_time_grid(csv_path, zlevel, cell, x0, y0, x1, y1):
    nx = max(1, int(round((x1 - x0) / cell)))
    ny = max(1, int(round((y1 - y0) / cell)))
    ssum = np.zeros((ny, nx))
    scnt = np.zeros((ny, nx))
    agents = sweep_lib.load_agents(csv_path)
    if not agents:
        return np.full((ny, nx), np.nan)
    tmax = max(tr[-1][0] for tr in agents.values())
    for tr in agents.values():
        t0, ox, oy, oz = tr[0][0], tr[0][1], tr[0][2], tr[0][3]
        if abs(oz - zlevel) >= FLOOR_EPS:
            continue
        tlast = tr[-1][0]
        if tlast >= tmax - 1e-9:      # sigue presente al final -> no evacuó
            continue
        tev = tlast - t0
        ix = int((ox - x0) / cell)
        iy = int((oy - y0) / cell)
        if 0 <= ix < nx and 0 <= iy < ny:
            ssum[iy, ix] += tev
            scnt[iy, ix] += 1
    grid = np.full((ny, nx), np.nan)
    m = scnt > 0
    grid[m] = ssum[m] / scnt[m]
    return grid


def _draw(ax, grid, walls, extent, cmap_name, vmax, mask_zero):
    if mask_zero:
        masked = np.ma.masked_less_equal(np.ma.masked_invalid(grid), 0.0)
    else:
        masked = np.ma.masked_invalid(grid)
    cm = plt.get_cmap(cmap_name).copy()
    cm.set_bad(_COLOR_EMPTY)
    im = ax.imshow(masked, origin="lower", extent=extent, cmap=cm, vmin=0.0,
                   vmax=vmax, aspect="equal", interpolation="nearest", zorder=1)
    for wx1, wy1, wx2, wy2 in walls:
        ax.plot([wx1, wx2], [wy1, wy2], color=_COLOR_WALL, lw=0.8, zorder=3)
    ax.set_xlim(extent[0], extent[1])
    ax.set_ylim(extent[2], extent[3])
    ax.set_xlabel("x [m]", fontsize=9)
    ax.set_ylabel("y [m]", fontsize=9)
    return im


def main() -> None:
    p = argparse.ArgumentParser(description="Mapas de calor de densidad y de tiempos de evacuación.")
    p.add_argument("--scenario", default="scenarios/escuela")
    p.add_argument("--output", required=True, help="output.csv de la corrida")
    p.add_argument("--kind", choices=["densidad", "tevac"], required=True)
    p.add_argument("--out", required=True, help="PNG de salida")
    p.add_argument("--floors", default="0,3", help="niveles z separados por coma (default 0,3)")
    p.add_argument("--cell", type=float, default=1.0, help="tamaño de celda [m] (default 1.0)")
    p.add_argument("--bounds", type=float, nargs=4, default=[0.0, 0.0, 60.0, 60.0],
                   metavar=("X0", "Y0", "X1", "Y1"))
    p.add_argument("--title", default="")
    args = p.parse_args()

    x0, y0, x1, y1 = args.bounds
    floors = [float(z) for z in args.floors.split(",") if z.strip()]
    extent = [x0, x1, y0, y1]

    cmap = "Blues" if args.kind == "densidad" else "Reds"
    mask_zero = args.kind == "densidad"
    cbar_label = ("ocupación media [agentes/celda]" if args.kind == "densidad"
                  else "tiempo de evacuación medio [s]")

    # Grillas por planta + vmax común (misma escala de color en ambas plantas).
    grids, wallsets = [], []
    for z in floors:
        if args.kind == "densidad":
            g = density_grid(args.output, z, args.cell, x0, y0, x1, y1)
        else:
            g = evac_time_grid(args.output, z, args.cell, x0, y0, x1, y1)
        grids.append(g)
        wallsets.append(parse_walls(args.scenario, z))
    allvals = np.concatenate([
        (np.ma.masked_less_equal(np.ma.masked_invalid(g), 0.0) if mask_zero
         else np.ma.masked_invalid(g)).compressed()
        for g in grids]) if grids else np.array([])
    vmax = float(np.percentile(allvals, 99)) if allvals.size else 1.0
    if vmax <= 0:
        vmax = 1.0

    ncol = len(floors)
    fig, axes = plt.subplots(1, ncol, figsize=(5.4 * ncol, 5.2), squeeze=False)
    names = {0.0: "PB (z=0)", 3.0: "P1 (z=3)"}
    im = None
    for ax, z, g, w in zip(axes[0], floors, grids, wallsets):
        im = _draw(ax, g, w, extent, cmap, vmax, mask_zero)
        ax.set_title(names.get(z, f"z={z:g}"), fontsize=11)
    cbar = fig.colorbar(im, ax=axes[0].tolist(), shrink=0.85, pad=0.02)
    cbar.set_label(cbar_label, fontsize=10)
    if args.title:
        fig.suptitle(args.title, fontsize=13)
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    fig.savefig(args.out, dpi=200, bbox_inches="tight")
    plt.close(fig)
    print(f"Guardado: {args.out}  (vmax={vmax:.2f}, celda={args.cell} m)")


if __name__ == "__main__":
    main()
