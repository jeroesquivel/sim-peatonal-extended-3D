"""
plot_ingreso.py — gráficos del sub-escenario Ingreso (Task 4 del plan de
entrega, ver D20 en .claude/DECISIONES.md).

Observable primario : población vs. tiempo en la zona de congestión del
                       ingreso (frente del kiosco del recreo, donde se agolpa la
                       cola matinal antes de clase; ver ZONA / D20), una curva
                       por caudal (ventana de llegada en minutos).
Observable escalar   : ocupación máxima y promedio en esa zona vs. caudal.

Lee los outputs que deja tools/sweep_run.py --mode ingreso con el layout:
    out/sweeps/ingreso/v<window_min>/seed<seed>/output.csv

Reusa tools/sweep_lib.py (zone_population) para contar agentes dentro del
rectángulo de contrato por frame de output, filtrando por planta (z=0, PB).

Uso:
    python tools/plot_ingreso.py \\
        [--sweep-dir out/sweeps/ingreso] [--seed 1] [--out-prefix out/ingreso] \\
        [--zone X0 Y0 X1 Y1]

Genera:
    <out-prefix>_poblacion.png — población vs. tiempo, una curva por caudal
    <out-prefix>_scalar.png    — ocupación máxima y promedio vs. caudal
"""

from __future__ import annotations

import argparse
import glob
import os
import re
import statistics
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

sys.path.insert(0, os.path.dirname(__file__))
import sweep_lib  # noqa: E402  (import tras el sys.path.insert)

# Paleta (dataviz skill, references/palette.md): categórica en orden fijo.
_COLOR_PROM = "#2a78d6"    # slot 1 (blue) — serie "promedio"
_COLOR_MAX = "#1baf7a"     # slot 2 (aqua) — serie "máximo"
_COLOR_CAUDAL_3 = "#eda100"  # slot 3 (yellow) — tercera curva de población (10 min)
_COLOR_GRID = "#e1e0d9"
_COLOR_MUTED = "#898781"
_COLOR_INK = "#0b0b0b"

# Colores de las curvas de población, en el orden en que aparecen los
# caudales (1 min, 5 min, 10 min, ...). Si hubiera más de 3 caudales, el
# ciclo se repite (no se espera con el barrido mínimo de 3 puntos).
_CURVE_COLORS = [_COLOR_PROM, _COLOR_MAX, _COLOR_CAUDAL_3]

# Zona observable (D20): rectángulo x0,y0,x1,y1 en z=0 (PB).
#
# El contrato proponía el corredor antes de la escalera SUR ``(42,8,48,14)``,
# pero al medir con Nmax=120 fijo en las 3 ventanas la congestión NO se forma
# ahí: la escalera switchback (Task 3) es ancha (2 carriles) y sólo ~la mitad de
# los alumnos suben a P1, repartidos entre las dos escaleras (SUR y NORTE) y a lo
# largo de la ventana, así que el pie de la escalera queda casi vacío
# (pico 2-3, sin tendencia clara). La congestión real del INGRESO se forma en el
# KIOSCO del recreo: los ~60 alumnos que entran por el recreo se agolpan frente
# al kiosco antes de ir a clase (el "recreo con kiosco" del enunciado). Esa zona
# da la señal fuerte y monótona que pide el observable (pico 51/16/6 para
# 1/5/10 min). Ver D20 en .claude/DECISIONES.md para el barrido de zonas
# probadas. La zona del corredor sigue disponible por ``--zone`` para inspección.
ZONA = (2.0, 42.0, 14.0, 52.0)     # frente del kiosco del recreo (cola matinal)
ZONA_ESCALERA_SUR = (42.0, 8.0, 48.0, 14.0)   # zona del contrato (corredor pre-escalera), para --zone
ZLEVEL = 0.0


def discover_caudales(sweep_dir: str, seed: int) -> list[tuple[int, str]]:
    """Escanea ``sweep_dir/v*/seed<seed>/output.csv`` y devuelve una lista
    ``[(window_min, csv_path), ...]`` ordenada por ventana ascendente."""
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
        description="Gráficos del sub-escenario Ingreso: población vs. tiempo "
                     "en la zona antes de la escalera principal, y curva "
                     "escalar de ocupación máxima/promedio vs. caudal."
    )
    parser.add_argument(
        "--sweep-dir", default="out/sweeps/ingreso",
        help="Directorio raíz del barrido de ingreso (default: %(default)s)",
    )
    parser.add_argument(
        "--seed", type=int, default=1,
        help="Semilla/réplica a graficar, dentro de cada v<window_min>/seed<seed>/ (default: %(default)s)",
    )
    parser.add_argument(
        "--out-prefix", default="out/ingreso",
        help="Prefijo de los PNG de salida (default: %(default)s)",
    )
    parser.add_argument(
        "--zone", type=float, nargs=4, default=None, metavar=("X0", "Y0", "X1", "Y1"),
        help=f"Rectángulo de la zona observable x0 y0 x1 y1 (default: {ZONA}, "
             f"la zona de contrato antes de la escalera SUR)",
    )
    args = parser.parse_args()

    zona = tuple(args.zone) if args.zone is not None else ZONA
    x0, y0, x1, y1 = zona

    caudales = discover_caudales(args.sweep_dir, args.seed)
    if not caudales:
        print(
            f"[ERROR] no se encontró ningún output.csv en "
            f"'{args.sweep_dir}/v*/seed{args.seed}/output.csv'. "
            f"¿Corriste tools/sweep_run.py --mode ingreso?"
        )
        sys.exit(1)

    results: dict[int, tuple[list[float], list[int]]] = {}
    for window_min, csv_path in caudales:
        if not os.path.isfile(csv_path):
            print(f"[WARN] ventana={window_min} min: no existe '{csv_path}'. Se omite.")
            continue
        try:
            times, counts = sweep_lib.zone_population(csv_path, x0, y0, x1, y1, zlevel=ZLEVEL)
        except Exception as exc:
            print(f"[WARN] ventana={window_min} min: no se pudo leer/parsear '{csv_path}' ({exc}). Se omite.")
            continue
        if not counts:
            print(f"[WARN] ventana={window_min} min: 0 frames en '{csv_path}' (o CSV vacío).")
        results[window_min] = (times, counts)

    if not results:
        print("[ERROR] ninguna ventana pudo procesarse (todas fallaron o sin datos). Abortando.")
        sys.exit(1)

    windows_ok = sorted(results.keys())

    # ---- 1) Población vs. tiempo, todas las curvas superpuestas ----------
    fig, ax = plt.subplots(figsize=(8, 5))
    any_curve = False
    for i, window_min in enumerate(windows_ok):
        times, counts = results[window_min]
        if not times:
            continue
        color = _CURVE_COLORS[i % len(_CURVE_COLORS)]
        ax.plot(times, counts, linewidth=2, color=color, label=f"{window_min} min")
        any_curve = True
    if not any_curve:
        ax.text(0.5, 0.5, "sin datos de población", ha="center", va="center",
                color=_COLOR_MUTED, transform=ax.transAxes)
    ax.set_xlabel("tiempo [s]")
    ax.set_ylabel("agentes en la zona")
    ax.set_title(f"Población vs. tiempo en la zona de congestión del ingreso (seed {args.seed})")
    ax.grid(color=_COLOR_GRID, linewidth=0.8, zorder=0)
    ax.set_axisbelow(True)
    for spine in ("top", "right"):
        ax.spines[spine].set_visible(False)
    if any_curve:
        ax.legend(frameon=False)
    fig.tight_layout()
    poblacion_path = f"{args.out_prefix}_poblacion.png"
    os.makedirs(os.path.dirname(poblacion_path) or ".", exist_ok=True)
    fig.savefig(poblacion_path, dpi=130)
    plt.close(fig)

    # ---- 2) Escalar: ocupación máxima y promedio vs. caudal --------------
    windows_scalar: list[int] = []
    means: list[float] = []
    maxs: list[float] = []
    peak_times: dict[int, float] = {}
    for window_min in windows_ok:
        times, counts = results[window_min]
        if not counts:
            continue
        windows_scalar.append(window_min)
        means.append(statistics.mean(counts))
        maxs.append(max(counts))
        peak_idx = counts.index(max(counts))
        peak_times[window_min] = times[peak_idx]

    fig2, ax2 = plt.subplots(figsize=(6.5, 4.5))
    if windows_scalar:
        ax2.plot(windows_scalar, means, marker="o", markersize=7, linewidth=2,
                  color=_COLOR_PROM, label="promedio")
        ax2.plot(windows_scalar, maxs, marker="s", markersize=7, linewidth=2,
                  color=_COLOR_MAX, label="máximo")
    else:
        ax2.text(0.5, 0.5, "sin datos de ocupación", ha="center", va="center",
                  color=_COLOR_MUTED, transform=ax2.transAxes)
    ax2.set_xlabel("ventana de llegada [min]")
    ax2.set_ylabel("ocupación de la zona [agentes]")
    ax2.set_title(f"Ocupación máxima y promedio vs. caudal (seed {args.seed})")
    ax2.grid(color=_COLOR_GRID, linewidth=0.8, zorder=0)
    ax2.set_axisbelow(True)
    for spine in ("top", "right"):
        ax2.spines[spine].set_visible(False)
    if windows_scalar:
        ax2.legend(frameon=False)
    fig2.tight_layout()
    scalar_path = f"{args.out_prefix}_scalar.png"
    fig2.savefig(scalar_path, dpi=130)
    plt.close(fig2)

    # ---- 3) Tabla a stdout -------------------------------------------------
    print()
    header = f"{'caudal':>8} | {'pico':>8} | {'prom':>10} | {'t_pico':>10}"
    print(header)
    print("-" * len(header))
    for window_min in windows_ok:
        times, counts = results[window_min]
        if counts:
            prom = statistics.mean(counts)
            pico = max(counts)
            t_pico = peak_times.get(window_min, float("nan"))
            print(f"{window_min:>8} | {pico:>8} | {prom:>10.2f} | {t_pico:>10.2f}")
        else:
            print(f"{window_min:>8} | {'--':>8} | {'--':>10} | {'--':>10}")

    print()
    print(f"Guardado: {poblacion_path}")
    print(f"Guardado: {scalar_path}")


if __name__ == "__main__":
    main()
