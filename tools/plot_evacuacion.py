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
        [--sweep-dir out/sweeps/evacuacion] [--seeds all] [--out-prefix out/evac] \\
        [--curve-values 40,120,500] [--band-values 80,200,300,400,500]

Genera:
    <out-prefix>_hist.png           — histograma de tiempos de evacuación por N
                                       (pool de seeds), UN panel por N con escala
                                       propia (autoscale independiente por panel).
    <out-prefix>_hist_samescale.png — lo mismo, pero con bins comunes y ejes
                                       x/y compartidos entre paneles (misma escala
                                       en todos), para comparar directamente la
                                       forma/altura de las distribuciones entre N.
    <out-prefix>_curves.png         — las mismas distribuciones como curvas de
                                       densidad superpuestas en un único gráfico
                                       (una curva por N, coloreadas por gradiente
                                       "colograma" viridis), en vez de paneles de
                                       histograma. Subconjunto de N seleccionable
                                       con --curve-values (default: todos). SIN
                                       banda de error (densidad sobre el pool de
                                       todas las seeds).
    <out-prefix>_curves_band.png    — igual concepto que _curves.png, pero (a)
                                       subconjunto de N por defecto acotado a
                                       --band-values (default: 80,200,300,400,500,
                                       para no saturar el gráfico) y (b) con una
                                       banda sombreada ±σ por curva: la densidad
                                       se calcula POR SEMILLA y se agrega
                                       media±desvío estándar muestral (ddof=1)
                                       entre semillas en cada bin (mismo criterio
                                       que el resto del script). Se genera aparte
                                       de _curves.png (que no se toca) para poder
                                       comparar ambas versiones.
    <out-prefix>_scalar.png         — promedio y máximo de tiempo de evacuación
                                       vs N (media±σ)
"""

from __future__ import annotations

import argparse
import math
import os
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
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


def gather_evac_data(
    sweep_dir: str,
    seeds_sel: list[int] | None,
    values_filter: set[int] | None = None,
) -> tuple[
    dict[int, list[float]],
    dict[int, list[float]],
    dict[int, list[float]],
    dict[int, list[int]],
    dict[int, int],
    dict[int, list[list[float]]],
]:
    """Recorre ``sweep_dir`` y arma, por N, los datos que consumen todas las
    funciones de ploteo de este módulo: tiempos de evacuación pooled (todas las
    seeds juntas), medias/máximos por seed, #evacuados por seed, #seeds usadas y
    los tiempos CRUDOS por seed (para curvas con banda de error entre seeds).

    Devuelve ``(pooled, per_seed_mean, per_seed_max, per_seed_nevac, n_seeds,
    per_seed_times)``, cada uno indexado por N (``per_seed_times[n]`` es una
    lista con una lista de tiempos por seed procesada, en el mismo orden que
    ``per_seed_mean[n]``/``per_seed_max[n]``). Sólo incluye los N con al menos
    una seed procesable."""
    values = sweep_lib.discover_values(sweep_dir)
    if values_filter is not None:
        values = [(n, d) for n, d in values if n in values_filter]
    if not values:
        print(
            f"[ERROR] no se encontró ningún v<N>/seed<S>/output.csv bajo "
            f"'{sweep_dir}'. ¿Corriste tools/sweep_run.py --mode evacuacion?"
        )
        sys.exit(1)

    pooled: dict[int, list[float]] = {}
    per_seed_mean: dict[int, list[float]] = {}
    per_seed_max: dict[int, list[float]] = {}
    per_seed_nevac: dict[int, list[int]] = {}
    n_seeds: dict[int, int] = {}
    per_seed_times: dict[int, list[list[float]]] = {}

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
        times_per_seed: list[list[float]] = []
        for seed, csv_path in seed_csvs:
            try:
                times = sweep_lib.evac_times(csv_path)
            except Exception as exc:
                print(f"[WARN] N={n} seed={seed}: no se pudo parsear '{csv_path}' ({exc}). Se omite esa seed.")
                continue
            nevacs.append(len(times))
            times_per_seed.append(times)
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
        per_seed_times[n] = times_per_seed

    if not pooled:
        print("[ERROR] ningún N pudo procesarse. Abortando.")
        sys.exit(1)

    return pooled, per_seed_mean, per_seed_max, per_seed_nevac, n_seeds, per_seed_times


def plot_hist(
    pooled: dict[int, list[float]],
    n_seeds: dict[int, int],
    ns_ok: list[int],
    norm: Normalize,
    out_path: str,
) -> None:
    """Histograma de tiempos de evacuación (pool de seeds), un panel por N.
    Cada panel autoescala sus propios ejes (bins y alturas independientes) —
    función ORIGINAL, sin tocar. Para comparar formas/alturas entre N en una
    misma escala usar :func:`plot_hist_same_scale`."""
    ncols = min(3, len(ns_ok))
    nrows = math.ceil(len(ns_ok) / ncols)
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
    sm = ScalarMappable(norm=norm, cmap=_CMAP)
    sm.set_array([])
    cbar = fig.colorbar(sm, ax=axes.ravel().tolist(), fraction=0.025, pad=0.02)
    cbar.set_label("N (agentes)")
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    fig.savefig(out_path, dpi=200, bbox_inches="tight")
    plt.close(fig)


def plot_hist_same_scale(
    pooled: dict[int, list[float]],
    n_seeds: dict[int, int],
    ns_ok: list[int],
    norm: Normalize,
    out_path: str,
    n_bins: int = 20,
) -> None:
    """Igual que :func:`plot_hist` pero con bins COMUNES (mismo ancho y mismo
    rango en [0, t_max_global]) y ejes x/y COMPARTIDOS entre paneles (misma
    escala en todos), para que la altura y el ancho de las barras sean
    directamente comparables entre distintos N (en :func:`plot_hist` cada panel
    autoescala su propio eje, lo que puede confundir al comparar formas)."""
    all_times = [t for n in ns_ok for t in pooled[n]]
    if not all_times:
        print(f"[WARN] plot_hist_same_scale: sin tiempos de evacuación en ningún N, se omite '{out_path}'.")
        return
    t_max = max(all_times)
    bin_edges = np.linspace(0.0, t_max if t_max > 0 else 1.0, n_bins + 1)

    ncols = min(3, len(ns_ok))
    nrows = math.ceil(len(ns_ok) / ncols)
    fig, axes = plt.subplots(nrows, ncols, figsize=(4.6 * ncols, 3.6 * nrows), squeeze=False,
                             sharex=True, sharey=True,
                             gridspec_kw={"hspace": 0.5, "wspace": 0.3})
    axes_flat = axes.flatten()
    for ax, n in zip(axes_flat, ns_ok):
        times = pooled[n]
        color = _color_for(n, norm)
        if times:
            ax.hist(times, bins=bin_edges, color=color, edgecolor="white", linewidth=0.6)
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
    fig.suptitle("Distribución de tiempos de evacuación (escala común entre paneles)", fontsize=13)
    sm = ScalarMappable(norm=norm, cmap=_CMAP)
    sm.set_array([])
    cbar = fig.colorbar(sm, ax=axes.ravel().tolist(), fraction=0.025, pad=0.02)
    cbar.set_label("N (agentes)")
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    fig.savefig(out_path, dpi=200, bbox_inches="tight")
    plt.close(fig)


def plot_curves_overlay(
    pooled: dict[int, list[float]],
    ns_ok: list[int],
    out_path: str,
    curve_ns: list[int] | None = None,
    n_bins: int = 30,
) -> None:
    """Distribución de tiempos de evacuación como CURVAS de densidad
    superpuestas (una por N) en un único gráfico, en vez de paneles de
    histograma. Coloreadas por gradiente ("colograma") viridis según N, con
    colorbar. Todas las curvas comparten los mismos bins (rango [0, t_max] del
    subconjunto graficado) para que sean comparables entre sí.

    ``curve_ns`` filtra qué N se dibujan (default ``None`` = todos los de
    ``ns_ok``); pensado para poder recortar a pocos N cuando graficar todos
    genera demasiado ruido visual."""
    selected = [n for n in ns_ok if (curve_ns is None or n in curve_ns)]
    if curve_ns is not None:
        faltan = [n for n in curve_ns if n not in ns_ok]
        if faltan:
            print(f"[WARN] plot_curves_overlay: --curve-values pide N ausentes {faltan} (se ignoran).")
    selected = [n for n in selected if pooled.get(n)]
    if not selected:
        print(f"[WARN] plot_curves_overlay: sin N con datos para graficar, se omite '{out_path}'.")
        return

    all_times = [t for n in selected for t in pooled[n]]
    t_max = max(all_times) if all_times else 1.0
    bin_edges = np.linspace(0.0, t_max if t_max > 0 else 1.0, n_bins + 1)
    bin_centers = 0.5 * (bin_edges[:-1] + bin_edges[1:])

    norm_sel = Normalize(vmin=min(selected), vmax=max(selected) if max(selected) != min(selected) else min(selected) + 1)

    fig, ax = plt.subplots(figsize=(7.5, 4.8))
    for n in selected:
        times = pooled[n]
        density, _ = np.histogram(times, bins=bin_edges, density=True)
        ax.plot(bin_centers, density, color=_color_for(n, norm_sel), linewidth=2.2,
                label=f"N={n}")
    ax.set_xlabel("tiempo de evacuación [s]")
    ax.set_ylabel("densidad de probabilidad")
    ax.set_title("Distribución de tiempos de evacuación por N (curvas superpuestas)")
    ax.grid(color=_COLOR_GRID, linewidth=0.8, zorder=0)
    ax.set_axisbelow(True)
    for spine in ("top", "right"):
        ax.spines[spine].set_visible(False)
    sm = ScalarMappable(norm=norm_sel, cmap=_CMAP)
    sm.set_array([])
    cbar = fig.colorbar(sm, ax=ax, fraction=0.05, pad=0.02)
    cbar.set_label("N (agentes)")
    fig.tight_layout()
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    fig.savefig(out_path, dpi=200, bbox_inches="tight")
    plt.close(fig)


def _seed_density(times: list[float], bin_edges: np.ndarray) -> np.ndarray:
    """``np.histogram(..., density=True)`` de una seed, pero sin el NaN/división
    por cero cuando esa seed no tuvo agentes evacuados (histograma vacío ->
    densidad 0 en todos los bins, en vez de indeterminada)."""
    if not times:
        return np.zeros(len(bin_edges) - 1)
    density, _ = np.histogram(times, bins=bin_edges, density=True)
    return density


def plot_curves_band(
    per_seed_times: dict[int, list[list[float]]],
    ns_ok: list[int],
    out_path: str,
    curve_ns: list[int] | None = None,
    n_bins: int = 30,
) -> None:
    """Igual concepto que :func:`plot_curves_overlay` (curvas de densidad
    superpuestas por N, coloreadas por gradiente "colograma" viridis), pero:

    1. Cada curva es la MEDIA entre seeds de la densidad calculada POR SEMILLA
       (en vez de la densidad del pool de todas las seeds juntas), con una
       banda sombreada ±σ (desvío estándar muestral, ddof=1) que representa la
       dispersión entre las realizaciones en cada bin de tiempo. Con <2 seeds
       para un N no hay banda (σ no estimable), sólo la curva.
    2. ``curve_ns`` default ``[80, 200, 300, 400, 500]`` (no ``None``=todos)
       para no saturar el gráfico; se puede pasar otra lista para elegir otro
       subconjunto.

    No modifica ni reemplaza :func:`plot_curves_overlay` — están pensadas para
    generarse ambas y compararse (``_curves.png`` vs ``_curves_band.png``)."""
    if curve_ns is None:
        curve_ns = [80, 200, 300, 400, 500]
    selected = [n for n in ns_ok if n in curve_ns]
    faltan = [n for n in curve_ns if n not in ns_ok]
    if faltan:
        print(f"[WARN] plot_curves_band: --band-values pide N ausentes {faltan} (se ignoran).")
    selected = [n for n in selected if per_seed_times.get(n)]
    if not selected:
        print(f"[WARN] plot_curves_band: sin N con datos para graficar, se omite '{out_path}'.")
        return

    all_times = [t for n in selected for times in per_seed_times[n] for t in times]
    t_max = max(all_times) if all_times else 1.0
    bin_edges = np.linspace(0.0, t_max if t_max > 0 else 1.0, n_bins + 1)
    bin_centers = 0.5 * (bin_edges[:-1] + bin_edges[1:])

    norm_sel = Normalize(vmin=min(selected), vmax=max(selected) if max(selected) != min(selected) else min(selected) + 1)

    fig, ax = plt.subplots(figsize=(7.5, 4.8))
    single_seed_ns: list[int] = []
    for n in selected:
        seed_lists = per_seed_times[n]
        densities = np.array([_seed_density(times, bin_edges) for times in seed_lists])
        mean_density = densities.mean(axis=0)
        color = _color_for(n, norm_sel)
        ax.plot(bin_centers, mean_density, color=color, linewidth=2.2, label=f"N={n}")
        if densities.shape[0] >= 2:
            std_density = densities.std(axis=0, ddof=1)
            ax.fill_between(bin_centers, np.maximum(mean_density - std_density, 0.0),
                             mean_density + std_density, color=color, alpha=0.22, linewidth=0)
        else:
            single_seed_ns.append(n)
    ax.set_xlabel("tiempo de evacuación [s]")
    ax.set_ylabel("densidad de probabilidad")
    ax.set_title("Distribución de tiempos de evacuación por N (curvas + banda ±σ entre seeds)")
    ax.grid(color=_COLOR_GRID, linewidth=0.8, zorder=0)
    ax.set_axisbelow(True)
    for spine in ("top", "right"):
        ax.spines[spine].set_visible(False)
    sm = ScalarMappable(norm=norm_sel, cmap=_CMAP)
    sm.set_array([])
    cbar = fig.colorbar(sm, ax=ax, fraction=0.05, pad=0.02)
    cbar.set_label("N (agentes)")
    fig.tight_layout()
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    fig.savefig(out_path, dpi=200, bbox_inches="tight")
    plt.close(fig)
    if single_seed_ns:
        print(f"[nota] plot_curves_band: N={single_seed_ns} con <2 seeds procesables: sin banda de error.")


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
             "para un histograma legible en un slide). Default: todos los del sweep-dir. "
             "Aplica a los histogramas y al escalar; para las curvas superpuestas ver --curve-values.",
    )
    parser.add_argument(
        "--curve-values", default=None,
        help="Subconjunto de N a incluir en el gráfico de curvas superpuestas "
             "(<out-prefix>_curves.png), separado por comas (p.ej. '40,120,500'). "
             "Default: los mismos N que --values (todos si no se filtró). Útil "
             "para recortar cuando superponer todos los N genera mucho ruido visual.",
    )
    parser.add_argument(
        "--band-values", default="80,200,300,400,500",
        help="Subconjunto de N a incluir en el gráfico de curvas + banda de error "
             "entre seeds (<out-prefix>_curves_band.png), separado por comas. "
             "Default: %(default)s.",
    )
    args = parser.parse_args()

    if args.seed is not None:
        print(f"[WARN] --seed {args.seed} está DEPRECADO: usá --seeds {args.seed}. "
              f"Interpretado como --seeds {args.seed}.")
        seeds_sel = [args.seed]
    else:
        seeds_sel = sweep_lib.parse_seeds_arg(args.seeds)

    values_filter = {int(v) for v in args.values.split(",")} if args.values is not None else None
    pooled, per_seed_mean, per_seed_max, per_seed_nevac, n_seeds, per_seed_times = gather_evac_data(
        args.sweep_dir, seeds_sel, values_filter
    )

    ns_ok = sorted(pooled.keys())
    norm = Normalize(vmin=min(ns_ok), vmax=max(ns_ok) if max(ns_ok) != min(ns_ok) else min(ns_ok) + 1)

    # ---- 1) Histograma de tiempos de evacuación (pool de seeds), un panel por N,
    #         cada uno con su propia escala (función original).
    hist_path = f"{args.out_prefix}_hist.png"
    plot_hist(pooled, n_seeds, ns_ok, norm, hist_path)

    # ---- 1b) Igual, pero con bins y ejes x/y comunes entre paneles (misma escala).
    hist_samescale_path = f"{args.out_prefix}_hist_samescale.png"
    plot_hist_same_scale(pooled, n_seeds, ns_ok, norm, hist_samescale_path)

    # ---- 1c) Curvas de densidad superpuestas (un único gráfico, subset elegible).
    curve_ns = [int(v) for v in args.curve_values.split(",")] if args.curve_values is not None else None
    curves_path = f"{args.out_prefix}_curves.png"
    plot_curves_overlay(pooled, ns_ok, curves_path, curve_ns=curve_ns)

    # ---- 1d) Igual concepto, subset acotado por defecto + banda ±σ entre seeds.
    #          Se guarda en un archivo aparte (no pisa curves_path) para comparar.
    band_ns = [int(v) for v in args.band_values.split(",")] if args.band_values is not None else None
    curves_band_path = f"{args.out_prefix}_curves_band.png"
    plot_curves_band(per_seed_times, ns_ok, curves_band_path, curve_ns=band_ns)

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
    print(f"Guardado: {hist_samescale_path}")
    print(f"Guardado: {curves_path}")
    print(f"Guardado: {curves_band_path}")
    print(f"Guardado: {scalar_path}")


if __name__ == "__main__":
    main()
