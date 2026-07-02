"""
plot_evacuacion.py — gráficos del sub-escenario Evacuación (Task 2 del plan
de entrega, ver .claude/PLAN_ENTREGA.md).

Observable primario : distribución de tiempos de evacuación (histograma).
Observable escalar   : tiempo de evacuación promedio y máximo vs N.

Lee los outputs que deja tools/sweep_run.py con el layout:
    out/sweeps/evacuacion/v<N>/seed<seed>/output.csv

Reusa tools/sweep_lib.py (evac_times) para el cálculo de tiempos de
evacuación: por agente que desaparece antes del último frame del output,
evac_time = t_ultimo - t_primero (en evacuación t_primero ~ 0, así que es
~tiempo hasta salir).

Uso:
    python tools/plot_evacuacion.py \\
        [--sweep-dir out/sweeps/evacuacion] [--seed 1] [--out-prefix out/evac]

Genera:
    <out-prefix>_hist.png    — histograma de tiempos de evacuación por N
    <out-prefix>_scalar.png  — promedio y máximo de tiempo de evacuación vs N
"""

from __future__ import annotations

import argparse
import glob
import math
import os
import re
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

sys.path.insert(0, os.path.dirname(__file__))
import sweep_lib  # noqa: E402  (import tras el sys.path.insert)

# Paleta (dataviz skill, references/palette.md): categórica en orden fijo.
_COLOR_PROM = "#2a78d6"   # slot 1 (blue) — serie "promedio"
_COLOR_MAX = "#1baf7a"    # slot 2 (aqua) — serie "máximo"
_COLOR_HIST = "#2a78d6"   # sequential blue, magnitud (un solo hue)
_COLOR_GRID = "#e1e0d9"
_COLOR_MUTED = "#898781"
_COLOR_INK = "#0b0b0b"


def discover_ns(sweep_dir: str, seed: int) -> list[tuple[int, str]]:
    """Escanea ``sweep_dir/v*/seed<seed>/output.csv`` y devuelve una lista
    ``[(N, csv_path), ...]`` ordenada por N ascendente."""
    pattern = os.path.join(sweep_dir, "v*", f"seed{seed}", "output.csv")
    found: list[tuple[int, str]] = []
    for path in sorted(glob.glob(pattern)):
        v_dir = os.path.basename(os.path.dirname(os.path.dirname(path)))
        m = re.match(r"^v(\d+)$", v_dir)
        if not m:
            print(f"[WARN] directorio inesperado (no matchea 'vN'): '{v_dir}', se ignora {path}")
            continue
        found.append((int(m.group(1)), path))
    found.sort(key=lambda t: t[0])
    return found


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Gráficos del sub-escenario Evacuación: histograma de "
                     "tiempos de evacuación y curva escalar prom/máx vs N."
    )
    parser.add_argument(
        "--sweep-dir", default="out/sweeps/evacuacion",
        help="Directorio raíz del barrido de evacuación (default: %(default)s)",
    )
    parser.add_argument(
        "--seed", type=int, default=1,
        help="Semilla/réplica a graficar, dentro de cada v<N>/seed<seed>/ (default: %(default)s)",
    )
    parser.add_argument(
        "--out-prefix", default="out/evac",
        help="Prefijo de los PNG de salida (default: %(default)s)",
    )
    args = parser.parse_args()

    ns = discover_ns(args.sweep_dir, args.seed)
    if not ns:
        print(
            f"[ERROR] no se encontró ningún output.csv en "
            f"'{args.sweep_dir}/v*/seed{args.seed}/output.csv'. "
            f"¿Corriste tools/sweep_run.py para el escenario evacuación?"
        )
        sys.exit(1)

    results: dict[int, list[float]] = {}
    for n, csv_path in ns:
        if not os.path.isfile(csv_path):
            print(f"[WARN] N={n}: no existe '{csv_path}'. Se omite.")
            continue
        try:
            times = sweep_lib.evac_times(csv_path)
        except Exception as exc:
            print(f"[WARN] N={n}: no se pudo leer/parsear '{csv_path}' ({exc}). Se omite.")
            continue
        if not times:
            print(f"[WARN] N={n}: 0 agentes evacuados en '{csv_path}' (o CSV vacío).")
        results[n] = times

    if not results:
        print("[ERROR] ningún N pudo procesarse (todos fallaron o no evacuaron). Abortando.")
        sys.exit(1)

    ns_ok = sorted(results.keys())

    # ---- 1) Histograma de tiempos de evacuación, un subplot por N --------
    ncols = min(3, len(ns_ok))
    nrows = math.ceil(len(ns_ok) / ncols)
    fig, axes = plt.subplots(nrows, ncols, figsize=(4.6 * ncols, 3.6 * nrows), squeeze=False)
    axes_flat = axes.flatten()
    for ax, n in zip(axes_flat, ns_ok):
        times = results[n]
        if times:
            nbins = min(20, max(5, len(times) // 2))
            ax.hist(times, bins=nbins, color=_COLOR_HIST, edgecolor="white", linewidth=0.6)
        else:
            ax.text(0.5, 0.5, "sin evacuados", ha="center", va="center",
                    color=_COLOR_MUTED, transform=ax.transAxes)
        ax.set_title(f"N = {n}  (n_evac = {len(times)})", color=_COLOR_INK, fontsize=10)
        ax.set_xlabel("tiempo de evacuación [s]", fontsize=9)
        ax.set_ylabel("frecuencia", fontsize=9)
        ax.grid(axis="y", color=_COLOR_GRID, linewidth=0.8, zorder=0)
        ax.set_axisbelow(True)
        for spine in ("top", "right"):
            ax.spines[spine].set_visible(False)
    for ax in axes_flat[len(ns_ok):]:
        ax.axis("off")
    fig.suptitle(f"Distribución de tiempos de evacuación (seed {args.seed})", fontsize=13)
    fig.tight_layout(rect=(0, 0, 1, 0.96))
    hist_path = f"{args.out_prefix}_hist.png"
    os.makedirs(os.path.dirname(hist_path) or ".", exist_ok=True)
    fig.savefig(hist_path, dpi=130)
    plt.close(fig)

    # ---- 2) Escalar: tiempo de evacuación promedio y máximo vs N ---------
    ns_scalar: list[int] = []
    means: list[float] = []
    maxs: list[float] = []
    for n in ns_ok:
        times = results[n]
        if not times:
            continue
        ns_scalar.append(n)
        means.append(sum(times) / len(times))
        maxs.append(max(times))

    fig2, ax2 = plt.subplots(figsize=(6.5, 4.5))
    if ns_scalar:
        ax2.plot(ns_scalar, means, marker="o", markersize=7, linewidth=2,
                  color=_COLOR_PROM, label="promedio")
        ax2.plot(ns_scalar, maxs, marker="s", markersize=7, linewidth=2,
                  color=_COLOR_MAX, label="máximo")
    else:
        ax2.text(0.5, 0.5, "sin datos de evacuación", ha="center", va="center",
                  color=_COLOR_MUTED, transform=ax2.transAxes)
    ax2.set_xlabel("N (agentes)")
    ax2.set_ylabel("tiempo de evacuación [s]")
    ax2.set_title(f"Tiempo de evacuación promedio y máximo vs N (seed {args.seed})")
    ax2.grid(color=_COLOR_GRID, linewidth=0.8, zorder=0)
    ax2.set_axisbelow(True)
    for spine in ("top", "right"):
        ax2.spines[spine].set_visible(False)
    if ns_scalar:
        ax2.legend(frameon=False)
    fig2.tight_layout()
    scalar_path = f"{args.out_prefix}_scalar.png"
    fig2.savefig(scalar_path, dpi=130)
    plt.close(fig2)

    # ---- 3) Tabla a stdout -------------------------------------------------
    print()
    header = f"{'N':>8} | {'n_evac':>8} | {'prom [s]':>10} | {'max [s]':>10}"
    print(header)
    print("-" * len(header))
    for n in ns_ok:
        times = results[n]
        if times:
            prom = sum(times) / len(times)
            print(f"{n:>8} | {len(times):>8} | {prom:>10.2f} | {max(times):>10.2f}")
        else:
            print(f"{n:>8} | {len(times):>8} | {'--':>10} | {'--':>10}")

    print()
    print(f"Guardado: {hist_path}")
    print(f"Guardado: {scalar_path}")


if __name__ == "__main__":
    main()
