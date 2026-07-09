"""
plot_evacuacion.py — gráficos del sub-escenario Evacuación (Task 2 del plan
de entrega, ver .claude/PLAN_ENTREGA.md).

Observable primario : distribución de tiempos de evacuación (histograma).
Observable escalar   : tiempo de evacuación promedio y máximo vs N.

**Agrega MÚLTIPLES realizaciones (semillas)**, como pide la cátedra (>=5
realizaciones por punto, barras de error siempre visibles, indicando el número
de realizaciones). Lee los outputs que deja tools/sweep_run.py con el layout:
    out/sweeps/evacuacion/v<N>/seed<seed>/output.csv

Funciona con cualquier subconjunto de seeds presente (incluida 1 sola: en ese
caso sin barras de error, sin romperse).

Método de agregación (por cada N):
  - Histograma: se POOLEAN los tiempos de evacuación de TODAS las seeds (una
    sola distribución por panel, con n_seeds y n_evac total anotados).
  - Escalar: se calcula la métrica POR SEMILLA (promedio y máximo de los tiempos
    de esa corrida) y luego media ± desvío estándar muestral (ddof=1) ENTRE
    semillas -> promedio-de-promedios y promedio-de-máximos con su σ.

Reusa tools/sweep_lib.py (evac_times, discover_values, resolve_seed_csvs,
mean_std): evac_time por agente que desaparece antes del último frame del
output = t_ultimo - t_primero (en evacuación t_primero ~ 0, así que es ~tiempo
hasta salir).

Uso:
    python tools/plot_evacuacion.py \\
        [--sweep-dir out/sweeps/evacuacion] [--seeds all] [--out-prefix out/evac]

Genera:
    <out-prefix>_hist.png    — histograma de tiempos de evacuación por N (pool de seeds)
    <out-prefix>_scalar.png  — promedio y máximo de tiempo de evacuación vs N (media±σ)
"""

from __future__ import annotations

import argparse
import math
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
#  - Escalar: dos series de DISTINTA NATURALEZA (promedio vs máximo) -> leyenda
#    categórica (permitida por la convención de la cátedra para este caso).
#  - Histograma: barras teñidas por N (variable NUMÉRICA) con cmap gradual
#    (viridis) + colorbar etiquetada, en vez de leyenda categórica.
_COLOR_PROM = "#2a78d6"   # slot 1 (blue) — serie "promedio"
_COLOR_MAX = "#1baf7a"    # slot 2 (aqua) — serie "máximo"
_COLOR_GRID = "#e1e0d9"
_COLOR_MUTED = "#898781"
_COLOR_INK = "#0b0b0b"
_CMAP = "viridis"


def _color_for(value: float, norm: Normalize) -> tuple:
    return plt.get_cmap(_CMAP)(norm(value))


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Gráficos del sub-escenario Evacuación: histograma de "
                     "tiempos de evacuación (pool de seeds) y curva escalar "
                     "prom/máx vs N con barras de error entre realizaciones."
    )
    parser.add_argument(
        "--sweep-dir", default="out/sweeps/evacuacion",
        help="Directorio raíz del barrido de evacuación (default: %(default)s)",
    )
    parser.add_argument(
        "--seeds", default="all",
        help="Semillas a agregar: lista separada por comas (p.ej. '1,2,3') o "
             "'all' para autodescubrir todas las presentes (default: %(default)s)",
    )
    parser.add_argument(
        "--seed", type=int, default=None,
        help="[DEPRECADO] alias de --seeds <N> (una sola semilla). Se mantiene "
             "por compatibilidad con comandos viejos.",
    )
    parser.add_argument(
        "--out-prefix", default="out/evac",
        help="Prefijo de los PNG de salida (default: %(default)s)",
    )
    parser.add_argument(
        "--values", default=None,
        help="Subconjunto de N a graficar, separado por comas (p.ej. '40,120,500' "
             "para un histograma legible en un slide). Default: todos los del sweep-dir.",
    )
    args = parser.parse_args()

    if args.seed is not None:
        print(f"[WARN] --seed {args.seed} está DEPRECADO: usá --seeds {args.seed}. "
              f"Interpretado como --seeds {args.seed}.")
        seeds_sel = [args.seed]
    else:
        seeds_sel = sweep_lib.parse_seeds_arg(args.seeds)

    values = sweep_lib.discover_values(args.sweep_dir)
    if args.values is not None:
        wanted = {int(v) for v in args.values.split(",")}
        values = [(n, d) for n, d in values if n in wanted]
    if not values:
        print(
            f"[ERROR] no se encontró ningún v<N>/seed<S>/output.csv bajo "
            f"'{args.sweep_dir}'. ¿Corriste tools/sweep_run.py --mode evacuacion?"
        )
        sys.exit(1)

    # Por cada N: tiempos de evacuación pooled y las métricas por semilla.
    pooled: dict[int, list[float]] = {}          # todos los evac_times de todas las seeds
    per_seed_mean: dict[int, list[float]] = {}   # media de tiempos por seed
    per_seed_max: dict[int, list[float]] = {}    # máximo de tiempos por seed
    per_seed_nevac: dict[int, list[int]] = {}    # #evacuados por seed
    n_seeds: dict[int, int] = {}

    for n, vdir in values:
        seed_csvs = sweep_lib.resolve_seed_csvs(vdir, seeds_sel)
        if seeds_sel is not None:
            faltan = [s for s in seeds_sel if s not in {s2 for s2, _ in seed_csvs}]
            if faltan:
                print(f"[WARN] N={n}: seeds pedidas ausentes {faltan} (se usan {[s for s,_ in seed_csvs]}).")
        if not seed_csvs:
            print(f"[WARN] N={n}: sin seeds procesables. Se omite.")
            continue
        pool: list[float] = []
        means: list[float] = []
        maxs: list[float] = []
        nevacs: list[int] = []
        for seed, csv_path in seed_csvs:
            try:
                times = sweep_lib.evac_times(csv_path)
            except Exception as exc:
                print(f"[WARN] N={n} seed={seed}: no se pudo parsear '{csv_path}' ({exc}). Se omite esa seed.")
                continue
            nevacs.append(len(times))
            if times:
                pool.extend(times)
                means.append(sum(times) / len(times))
                maxs.append(max(times))
            else:
                print(f"[WARN] N={n} seed={seed}: 0 agentes evacuados.")
        if not nevacs:
            print(f"[WARN] N={n}: ninguna seed procesable. Se omite.")
            continue
        pooled[n] = pool
        per_seed_mean[n] = means
        per_seed_max[n] = maxs
        per_seed_nevac[n] = nevacs
        n_seeds[n] = len(nevacs)

    if not pooled:
        print("[ERROR] ningún N pudo procesarse. Abortando.")
        sys.exit(1)

    ns_ok = sorted(pooled.keys())
    norm = Normalize(vmin=min(ns_ok), vmax=max(ns_ok) if max(ns_ok) != min(ns_ok) else min(ns_ok) + 1)

    # ---- 1) Histograma de tiempos de evacuación (pool de seeds), un panel por N
    ncols = min(3, len(ns_ok))
    nrows = math.ceil(len(ns_ok) / ncols)
    # hspace amplio: con 2+ filas, los títulos de cada fila no deben pisarse con
    # los xlabel de la fila de arriba.
    fig, axes = plt.subplots(nrows, ncols, figsize=(4.6 * ncols, 3.6 * nrows), squeeze=False,
                             gridspec_kw={"hspace": 0.5, "wspace": 0.3})
    axes_flat = axes.flatten()
    for ax, n in zip(axes_flat, ns_ok):
        times = pooled[n]
        color = _color_for(n, norm)
        if times:
            nbins = min(24, max(6, len(times) // 3))
            ax.hist(times, bins=nbins, color=color, edgecolor="white", linewidth=0.6)
        else:
            ax.text(0.5, 0.5, "sin evacuados", ha="center", va="center",
                    color=_COLOR_MUTED, transform=ax.transAxes)
        ax.set_title(f"N = {n}   (n_seeds = {n_seeds[n]},  n_evac = {len(times)})",
                     color=_COLOR_INK, fontsize=10)
        ax.set_xlabel("tiempo de evacuación [s]", fontsize=9)
        ax.set_ylabel("frecuencia [agentes]", fontsize=9)
        ax.grid(axis="y", color=_COLOR_GRID, linewidth=0.8, zorder=0)
        ax.set_axisbelow(True)
        for spine in ("top", "right"):
            ax.spines[spine].set_visible(False)
    for ax in axes_flat[len(ns_ok):]:
        ax.axis("off")
    fig.suptitle("Distribución de tiempos de evacuación", fontsize=13)
    # Colorbar compartido (variable numérica N), en vez de leyenda categórica.
    sm = ScalarMappable(norm=norm, cmap=_CMAP)
    sm.set_array([])
    cbar = fig.colorbar(sm, ax=axes.ravel().tolist(), fraction=0.025, pad=0.02)
    cbar.set_label("N (agentes)")
    hist_path = f"{args.out_prefix}_hist.png"
    os.makedirs(os.path.dirname(hist_path) or ".", exist_ok=True)
    fig.savefig(hist_path, dpi=200, bbox_inches="tight")
    plt.close(fig)

    # ---- 2) Escalar: prom y máx del tiempo de evacuación vs N (media±σ) -------
    ns_scalar: list[int] = []
    mean_of_means: list[float] = []
    std_of_means: list[float] = []
    mean_of_maxs: list[float] = []
    std_of_maxs: list[float] = []
    single_seed_pts: list[int] = []
    for n in ns_ok:
        if not per_seed_mean[n]:
            continue
        ns_scalar.append(n)
        mm, sm_ = sweep_lib.mean_std(per_seed_mean[n])
        xm, sx_ = sweep_lib.mean_std(per_seed_max[n])
        mean_of_means.append(mm)
        mean_of_maxs.append(xm)
        std_of_means.append(sm_ if sm_ is not None else 0.0)
        std_of_maxs.append(sx_ if sx_ is not None else 0.0)
        if sm_ is None:
            single_seed_pts.append(n)

    fig2, ax2 = plt.subplots(figsize=(6.5, 4.5))
    any_err = any(s > 0 for s in std_of_means + std_of_maxs)
    if ns_scalar:
        if any_err:
            ax2.errorbar(ns_scalar, mean_of_means, yerr=std_of_means, marker="o",
                         markersize=7, linewidth=2, capsize=4, color=_COLOR_PROM,
                         label="promedio")
            ax2.errorbar(ns_scalar, mean_of_maxs, yerr=std_of_maxs, marker="s",
                         markersize=7, linewidth=2, capsize=4, color=_COLOR_MAX,
                         label="máximo")
        else:
            # Todos los puntos con 1 sola semilla: sin yerr (convención cátedra).
            ax2.plot(ns_scalar, mean_of_means, marker="o", markersize=7, linewidth=2,
                     color=_COLOR_PROM, label="promedio")
            ax2.plot(ns_scalar, mean_of_maxs, marker="s", markersize=7, linewidth=2,
                     color=_COLOR_MAX, label="máximo")
    else:
        ax2.text(0.5, 0.5, "sin datos de evacuación", ha="center", va="center",
                 color=_COLOR_MUTED, transform=ax2.transAxes)
    ax2.set_xlabel("N (agentes)")
    ax2.set_ylabel("tiempo de evacuación [s]")
    ax2.set_title("Tiempo de evacuación promedio y máximo vs. N")
    ax2.grid(color=_COLOR_GRID, linewidth=0.8, zorder=0)
    ax2.set_axisbelow(True)
    for spine in ("top", "right"):
        ax2.spines[spine].set_visible(False)
    if ns_scalar:
        ax2.legend(frameon=False)
    fig2.tight_layout()
    scalar_path = f"{args.out_prefix}_scalar.png"
    fig2.savefig(scalar_path, dpi=200, bbox_inches="tight")
    plt.close(fig2)

    # ---- 3) Tabla a stdout ---------------------------------------------------
    print()
    print(f"Realizaciones (seeds) por N — selección: "
          f"{'all' if seeds_sel is None else seeds_sel}")
    header = (f"{'N':>6} | {'n_seeds':>7} | {'evac(media/seed)':>16} | "
              f"{'prom±σ [s]':>18} | {'max±σ [s]':>18}")
    print(header)
    print("-" * len(header))
    for n in ns_ok:
        nseed = n_seeds[n]
        evac_media = sum(per_seed_nevac[n]) / len(per_seed_nevac[n]) if per_seed_nevac[n] else 0.0
        if per_seed_mean[n]:
            mm, sm_ = sweep_lib.mean_std(per_seed_mean[n])
            xm, sx_ = sweep_lib.mean_std(per_seed_max[n])
            prom_s = f"{mm:6.2f} ± {(sm_ if sm_ is not None else 0.0):5.2f}"
            max_s = f"{xm:6.2f} ± {(sx_ if sx_ is not None else 0.0):5.2f}"
        else:
            prom_s = max_s = "        --        "
        print(f"{n:>6} | {nseed:>7} | {evac_media:>16.1f} | {prom_s:>18} | {max_s:>18}")

    if single_seed_pts:
        print(f"\n[nota] N={single_seed_pts} con 1 sola semilla: sin barra de error "
              f"(σ no estimable con <2 realizaciones).")
    if any_err:
        print("[nota] barras de error = σ muestral (ddof=1) entre semillas; si "
              "quedan más chicas que el marker (capsize=4) es porque la dispersión "
              "entre realizaciones es baja.")
    print()
    print(f"Guardado: {hist_path}")
    print(f"Guardado: {scalar_path}")


if __name__ == "__main__":
    main()
