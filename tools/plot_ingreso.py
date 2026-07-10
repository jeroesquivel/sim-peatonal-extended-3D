"""
plot_ingreso.py — gráficos del sub-escenario Ingreso (Task 4 del plan de
entrega, ver D20 en .claude/DECISIONES.md).

Observable primario : población vs. tiempo en la zona de congestión del
                       ingreso (frente del kiosco del recreo, donde se agolpa la
                       cola matinal antes de clase; ver ZONA / D20), una curva
                       por caudal (ventana de llegada en minutos).
Observable escalar   : ocupación máxima y promedio en esa zona vs. caudal.

**Agrega MÚLTIPLES realizaciones (semillas)**, como pide la cátedra (>=5
realizaciones por punto, barras de error / bandas visibles, indicando el número
de realizaciones y explicitando el método al promediar evoluciones temporales).
Lee los outputs que deja tools/sweep_run.py --mode ingreso con el layout:
    out/sweeps/ingreso/v<window_min>/seed<seed>/output.csv

Funciona con cualquier subconjunto de seeds presente (incluida 1 sola: en ese
caso sin banda ni barras de error, sin romperse).

Método de agregación (por cada ventana/caudal):
  - Población vs. t: se cuenta la población en la zona por frame en cada seed y
    se PROMEDIA la curva ENTRE seeds sobre la grilla temporal común. Todas las
    corridas de un mismo caudal comparten la misma dt_out ⇒ la misma grilla de
    tout (se verifica con assert en sweep_lib.align_population_curves). Si una
    corrida terminara con menos frames, su curva se rellena con 0 hasta el
    horizonte común (la zona queda vacía al terminar). Se dibuja la curva media
    + banda ±σ (fill_between).
  - Escalar: ocupación máxima por seed -> media±σ entre seeds; ocupación
    promedio por seed (sobre el horizonte común, con padding 0) -> media±σ.

Reusa tools/sweep_lib.py (zone_population, discover_values, resolve_seed_csvs,
align_population_curves, column_mean_std, mean_std).

Uso:
    python tools/plot_ingreso.py \\
        [--sweep-dir out/sweeps/ingreso] [--seeds all] [--out-prefix out/ingreso] \\
        [--zone X0 Y0 X1 Y1]

Genera:
    <out-prefix>_poblacion.png — población media±σ vs. tiempo, una curva por caudal
    <out-prefix>_scalar.png    — ocupación máxima y promedio vs. caudal (media±σ)
"""

from __future__ import annotations

import argparse
import os
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.cm import ScalarMappable
from matplotlib.colors import Normalize

sys.path.insert(0, os.path.dirname(__file__))
import sweep_lib  # noqa: E402  (import tras el sys.path.insert)

# Paleta.
#  - Población vs. t: una curva por VARIABLE NUMÉRICA (ventana de llegada) ->
#    cmap gradual (viridis) + colorbar etiquetada, NO leyenda categórica.
#  - Escalar: dos series de DISTINTA NATURALEZA (promedio vs máximo) -> leyenda.
_COLOR_PROM = "#2a78d6"    # slot 1 (blue) — serie "promedio"
_COLOR_MAX = "#1baf7a"     # slot 2 (aqua) — serie "máximo"
_COLOR_GRID = "#e1e0d9"
_COLOR_MUTED = "#898781"
_COLOR_INK = "#0b0b0b"
_CMAP = "viridis"

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


def _color_for(value: float, norm: Normalize) -> tuple:
    return plt.get_cmap(_CMAP)(norm(value))


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Gráficos del sub-escenario Ingreso: población media±σ vs. "
                     "tiempo en la zona de congestión, y curva escalar de "
                     "ocupación máxima/promedio vs. caudal, agregando seeds."
    )
    parser.add_argument(
        "--sweep-dir", default="out/sweeps/ingreso",
        help="Directorio raíz del barrido de ingreso (default: %(default)s)",
    )
    parser.add_argument(
        "--seeds", default="all",
        help="Semillas a agregar: lista separada por comas (p.ej. '1,2,3') o "
             "'all' para autodescubrir todas las presentes (default: %(default)s)",
    )
    parser.add_argument(
        "--seed", type=int, default=None,
        help="[DEPRECADO] alias de --seeds <N> (una sola semilla).",
    )
    parser.add_argument(
        "--out-prefix", default="out/ingreso",
        help="Prefijo de los PNG de salida (default: %(default)s)",
    )
    parser.add_argument(
        "--zone", type=float, nargs=4, default=None, metavar=("X0", "Y0", "X1", "Y1"),
        help=f"Rectángulo de la zona observable x0 y0 x1 y1 (default: {ZONA}, "
             f"el frente del kiosco del recreo)",
    )
    args = parser.parse_args()

    if args.seed is not None:
        print(f"[WARN] --seed {args.seed} está DEPRECADO: usá --seeds {args.seed}. "
              f"Interpretado como --seeds {args.seed}.")
        seeds_sel = [args.seed]
    else:
        seeds_sel = sweep_lib.parse_seeds_arg(args.seeds)

    zona = tuple(args.zone) if args.zone is not None else ZONA
    x0, y0, x1, y1 = zona

    values = sweep_lib.discover_values(args.sweep_dir)
    if not values:
        print(
            f"[ERROR] no se encontró ningún v<win>/seed<S>/output.csv bajo "
            f"'{args.sweep_dir}'. ¿Corriste tools/sweep_run.py --mode ingreso?"
        )
        sys.exit(1)

    # Por cada ventana: curva media±σ (población) y escalares por semilla.
    curve_mean: dict[int, tuple[list[float], list[float], list[float] | None]] = {}
    per_seed_peak: dict[int, list[float]] = {}   # ocupación máxima por seed
    per_seed_mean: dict[int, list[float]] = {}   # ocupación promedio (horizonte común) por seed
    peak_time_mean: dict[int, float] = {}        # t del pico de la curva media
    n_seeds: dict[int, int] = {}

    for window_min, vdir in values:
        seed_csvs = sweep_lib.resolve_seed_csvs(vdir, seeds_sel)
        if seeds_sel is not None:
            faltan = [s for s in seeds_sel if s not in {s2 for s2, _ in seed_csvs}]
            if faltan:
                print(f"[WARN] ventana={window_min} min: seeds pedidas ausentes {faltan} "
                      f"(se usan {[s for s, _ in seed_csvs]}).")
        if not seed_csvs:
            print(f"[WARN] ventana={window_min} min: sin seeds procesables. Se omite.")
            continue
        curves: list[tuple[list[float], list[int]]] = []
        for seed, csv_path in seed_csvs:
            try:
                times, counts = sweep_lib.zone_population(csv_path, x0, y0, x1, y1, zlevel=ZLEVEL)
            except Exception as exc:
                print(f"[WARN] ventana={window_min} min seed={seed}: no se pudo parsear "
                      f"'{csv_path}' ({exc}). Se omite esa seed.")
                continue
            if not counts:
                print(f"[WARN] ventana={window_min} min seed={seed}: 0 frames.")
                continue
            curves.append((times, counts))
        if not curves:
            print(f"[WARN] ventana={window_min} min: ninguna seed con datos. Se omite.")
            continue
        times_ref, matrix, n = sweep_lib.align_population_curves(curves)
        means, stds = sweep_lib.column_mean_std(matrix)
        curve_mean[window_min] = (times_ref, means, stds)
        # escalares por semilla, sobre la matriz alineada (padding 0 al final)
        per_seed_peak[window_min] = [max(row) for row in matrix]
        per_seed_mean[window_min] = [sum(row) / len(row) for row in matrix]
        n_seeds[window_min] = n
        if means:
            peak_time_mean[window_min] = times_ref[means.index(max(means))]

    if not curve_mean:
        print("[ERROR] ninguna ventana pudo procesarse. Abortando.")
        sys.exit(1)

    windows_ok = sorted(curve_mean.keys())
    norm = Normalize(vmin=min(windows_ok),
                     vmax=max(windows_ok) if max(windows_ok) != min(windows_ok) else min(windows_ok) + 1)

    # ---- 1) Población vs. tiempo: curva media + banda ±σ por caudal ----------
    fig, ax = plt.subplots(figsize=(8, 5))
    for window_min in windows_ok:
        times_ref, means, stds = curve_mean[window_min]
        if not times_ref:
            continue
        color = _color_for(window_min, norm)
        ax.plot(times_ref, means, linewidth=2, color=color)
        if stds is not None:
            lo = [m - s for m, s in zip(means, stds)]
            hi = [m + s for m, s in zip(means, stds)]
            ax.fill_between(times_ref, lo, hi, color=color, alpha=0.18, linewidth=0)
    ax.set_xlabel("tiempo [s]")
    ax.set_ylabel("agentes en la zona [agentes]")
    ax.grid(color=_COLOR_GRID, linewidth=0.8, zorder=0)
    ax.set_axisbelow(True)
    for spine in ("top", "right"):
        ax.spines[spine].set_visible(False)
    sm = ScalarMappable(norm=norm, cmap=_CMAP)
    sm.set_array([])
    cbar = fig.colorbar(sm, ax=ax, fraction=0.046, pad=0.02)
    cbar.set_label("ventana de llegada [min]")
    fig.tight_layout()
    poblacion_path = f"{args.out_prefix}_poblacion.png"
    os.makedirs(os.path.dirname(poblacion_path) or ".", exist_ok=True)
    fig.savefig(poblacion_path, dpi=200, bbox_inches="tight")
    plt.close(fig)

    # ---- 2) Escalar: ocupación máxima y promedio vs. caudal (media±σ) --------
    win_scalar: list[int] = []
    mean_of_peak: list[float] = []
    std_of_peak: list[float] = []
    mean_of_mean: list[float] = []
    std_of_mean: list[float] = []
    single_seed_pts: list[int] = []
    for window_min in windows_ok:
        win_scalar.append(window_min)
        mp, sp = sweep_lib.mean_std(per_seed_peak[window_min])
        mm, smn = sweep_lib.mean_std(per_seed_mean[window_min])
        mean_of_peak.append(mp)
        mean_of_mean.append(mm)
        std_of_peak.append(sp if sp is not None else 0.0)
        std_of_mean.append(smn if smn is not None else 0.0)
        if sp is None:
            single_seed_pts.append(window_min)

    fig2, ax2 = plt.subplots(figsize=(6.5, 4.5))
    any_err = any(s > 0 for s in std_of_peak + std_of_mean)
    if any_err:
        ax2.errorbar(win_scalar, mean_of_mean, yerr=std_of_mean, marker="o",
                     markersize=7, linestyle="-", capsize=4, color=_COLOR_PROM,
                     label="promedio")
        ax2.errorbar(win_scalar, mean_of_peak, yerr=std_of_peak, marker="s",
                     markersize=7, linestyle="-", capsize=4, color=_COLOR_MAX,
                     label="máximo")
    else:
        ax2.plot(win_scalar, mean_of_mean, marker="o", markersize=7, linestyle="-",
                 color=_COLOR_PROM, label="promedio")
        ax2.plot(win_scalar, mean_of_peak, marker="s", markersize=7, linestyle="-",
                 color=_COLOR_MAX, label="máximo")
    ax2.set_xlabel("ventana de llegada [min]")
    ax2.set_ylabel("ocupación de la zona [agentes]")
    ax2.grid(color=_COLOR_GRID, linewidth=0.8, zorder=0)
    ax2.set_axisbelow(True)
    for spine in ("top", "right"):
        ax2.spines[spine].set_visible(False)
    ax2.legend(frameon=False)
    fig2.tight_layout()
    scalar_path = f"{args.out_prefix}_scalar.png"
    fig2.savefig(scalar_path, dpi=200, bbox_inches="tight")
    plt.close(fig2)

    # ---- 3) Tabla a stdout ---------------------------------------------------
    print()
    print(f"Zona observable: x∈[{x0},{x1}]  y∈[{y0},{y1}]  z={ZLEVEL}")
    print(f"Realizaciones (seeds) por ventana — selección: "
          f"{'all' if seeds_sel is None else seeds_sel}")
    print("Método población vs. t: promedio entre seeds sobre grilla común "
          "(misma dt_out; padding 0 si una corrida termina antes).")
    header = (f"{'caudal[min]':>11} | {'n_seeds':>7} | {'pico±σ':>16} | "
              f"{'prom±σ':>16} | {'t_pico[s]':>10}")
    print(header)
    print("-" * len(header))
    for window_min in windows_ok:
        mp, sp = sweep_lib.mean_std(per_seed_peak[window_min])
        mm, smn = sweep_lib.mean_std(per_seed_mean[window_min])
        pico_s = f"{mp:6.2f} ± {(sp if sp is not None else 0.0):5.2f}"
        prom_s = f"{mm:6.2f} ± {(smn if smn is not None else 0.0):5.2f}"
        t_pico = peak_time_mean.get(window_min, float("nan"))
        print(f"{window_min:>11} | {n_seeds[window_min]:>7} | {pico_s:>16} | "
              f"{prom_s:>16} | {t_pico:>10.2f}")

    if single_seed_pts:
        print(f"\n[nota] ventana={single_seed_pts} con 1 sola semilla: sin banda ni "
              f"barra de error (σ no estimable con <2 realizaciones).")
    if any_err:
        print("[nota] barras/banda de error = σ muestral (ddof=1) entre semillas; si "
              "quedan más chicas que el marker (capsize=4) la dispersión entre "
              "realizaciones es baja.")
    print()
    print(f"Guardado: {poblacion_path}")
    print(f"Guardado: {scalar_path}")


if __name__ == "__main__":
    main()
