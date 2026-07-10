#!/usr/bin/env python3
"""Runner FÁCIL del sub-escenario Ingreso variando la CANTIDAD DE AGENTES (Nmax).

El sub-escenario Ingreso (Task 4) tiene como input el *caudal* (la ventana de
llegada 1/5/10 min) con Nmax=120 FIJO. Este script agrega el eje ortogonal
"cuánta gente entra": corre Ingreso para varios Nmax a una ventana FIJA y grafica
cómo escala la congestión (ocupación en la zona de interés — por default el frente
del kiosco del recreo, donde se forma la congestión del Ingreso; ver --zone) con Nmax.

**Agrega MÚLTIPLES realizaciones (semillas)** por Nmax, como pide la cátedra:
para cada Nmax corre todas las seeds de ``--seeds`` y agrega población media±banda
y escalares media±σ entre seeds (mismo método que plot_ingreso.py).

Es autocontenido y NO modifica ``build_escuela.py``: genera el escenario normal
(Nmax=120) y luego **reescala el caudal** multiplicando el ``period`` de cada
generador en el ``parameters.json`` por ``base/Nmax`` (el total de spawns es
lineal en ``1/period``, así que escalar todos los períodos por igual lleva el
total de ``base`` a ``Nmax``). No hace falta tocar el builder.

Uso típico (una sola línea):

    python tools/run_ingreso_nmax.py --nmax 60,120,180 --seeds 1,2,3,4,5

Sólo graficar lo ya corrido (sin volver a simular):

    python tools/run_ingreso_nmax.py --nmax 60,120,180 --plot-only

Otras opciones:

    python tools/run_ingreso_nmax.py --nmax 40,80,120,160 --window 5 --seeds 1,2,3

Salidas:
  - out/ingreso_nmax/n<Nmax>/scenario/                 (escenario, uno por Nmax)
  - out/ingreso_nmax/n<Nmax>/seed<S>/output.csv        (uno por Nmax y seed)
  - out/ingreso_nmax_poblacion.png   (población media±banda vs tiempo, una curva por Nmax)
  - out/ingreso_nmax_scalar.png      (ocupación pico/promedio vs Nmax, media±σ)
  - tabla a stdout con N efectivo, pico±σ, promedio±σ y t_pico por Nmax.

Ojo: el generador topa el spawn en ~3 personas/min por metro del lado mayor de la
zona de entrada. A ventana chica (1 min) y Nmax grande podés chocar ese techo y el
N EFECTIVO quedar por debajo del pedido — el script reporta el N efectivo real para
que se vea. La ventana default (5 min) deja mucho margen.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir))
BUILD_SCRIPT = os.path.join(REPO_ROOT, "tools", "scenarios-builders", "build_escuela.py")
OUT_DIR = os.path.join(REPO_ROOT, "out")
CP_FILE = os.path.join(OUT_DIR, ".cp.txt")
CLASSES_DIR = os.path.join(REPO_ROOT, "target", "classes")
MAIN_CLASS = "ar.edu.itba.simped.App"

sys.path.insert(0, os.path.join(REPO_ROOT, "tools"))
import sweep_lib  # noqa: E402  (tras el sys.path.insert)

# Zona observable. Default = frente del kiosco del recreo, la MISMA que quedó en
# plot_ingreso.py (D20): al medir con Nmax fijo la congestión del Ingreso NO se
# forma en la escalera (switchback ancho de 2 carriles, el pie queda casi vacío)
# sino en el kiosco, donde se agolpan los que entran por el recreo antes de clase.
# La zona del corredor antes de la escalera sur queda disponible por --zone
# (ZONA_ESCALERA_SUR) para inspección.
ZONA_DEFAULT = (2.0, 42.0, 14.0, 52.0)          # frente del kiosco (recreo)
ZONA_ESCALERA_SUR = (42.0, 8.0, 48.0, 14.0)     # corredor antes de la escalera sur (alternativa)
ZLEVEL = 0.0

# Paleta.
#  - Población vs. t: curva por VARIABLE NUMÉRICA (Nmax) -> viridis + colorbar.
#  - Escalar: dos series de DISTINTA NATURALEZA (promedio vs máximo) -> leyenda.
_COLOR_PROM = "#2a78d6"
_COLOR_MAX = "#1baf7a"
_COLOR_GRID = "#e1e0d9"
_COLOR_MUTED = "#898781"
_CMAP = "viridis"


def build_classpath() -> str:
    """Compila una vez y arma el classpath (classes + deps), igual que sweep_run.py."""
    if not os.path.isdir(CLASSES_DIR):
        print("[ingreso-nmax] target/classes no existe: 'mvn -q compile'...")
        subprocess.run(["mvn", "-q", "compile"], cwd=REPO_ROOT, check=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    print("[ingreso-nmax] classpath (mvn dependency:build-classpath)...")
    subprocess.run(
        ["mvn", "-q", "dependency:build-classpath", f"-Dmdep.outputFile={CP_FILE}"],
        cwd=REPO_ROOT, check=True,
    )
    with open(CP_FILE, "r", encoding="utf-8") as f:
        deps_cp = f.read().strip()
    return CLASSES_DIR + os.pathsep + deps_cp


def gen_scenario(window_min: float, scenario_dir: str) -> None:
    """Genera el escenario Ingreso base (Nmax=120) para la ventana dada."""
    os.makedirs(scenario_dir, exist_ok=True)
    subprocess.run(
        [sys.executable, BUILD_SCRIPT, "--mode", "ingreso",
         "--value", str(window_min), "--out", scenario_dir],
        check=True,
    )


def _base_count(gens: list[dict]) -> int:
    """Total de spawns teórico del parameters.json (suma sobre generadores de
    round(quantity_media / period * active_time))."""
    tot = 0
    for g in gens:
        gen = g["generation"]
        q = gen["quantity_distribution"]
        qty = (q["min"] + q["max"]) / 2.0
        tot += round(qty / gen["period"] * g["active_time"])
    return tot


def rescale_nmax(scenario_dir: str, nmax: int) -> int:
    """Reescala el caudal del parameters.json para que el total ≈ nmax
    (multiplica cada period por base/nmax). Devuelve el base original."""
    path = os.path.join(scenario_dir, "parameters.json")
    with open(path, "r", encoding="utf-8") as f:
        params = json.load(f)
    gens = params["agents_generators"]
    base = _base_count(gens)
    if base <= 0:
        raise RuntimeError(f"parameters.json sin spawns en {scenario_dir}")
    factor = base / float(nmax)
    for g in gens:
        g["generation"]["period"] = g["generation"]["period"] * factor
    with open(path, "w", encoding="utf-8") as f:
        json.dump(params, f, ensure_ascii=False, indent=2)
    return base


def run_simulation(classpath: str, scenario_dir: str, output_csv: str, seed: int, om: str) -> None:
    os.makedirs(os.path.dirname(output_csv), exist_ok=True)
    cmd = ["java", f"-Dsimped.seed={seed}", "-cp", classpath, MAIN_CLASS,
           scenario_dir, output_csv, om]
    print(f"[ingreso-nmax] sim seed={seed} -> {output_csv}")
    subprocess.run(cmd, cwd=REPO_ROOT, check=True)


def main() -> None:
    p = argparse.ArgumentParser(
        description="Corre el Ingreso variando la cantidad de agentes (Nmax) a "
                    "ventana fija, agregando varias semillas por Nmax.")
    p.add_argument("--nmax", default="60,120,180",
                   help="valores de Nmax separados por coma (default: %(default)s)")
    p.add_argument("--window", type=float, default=5.0,
                   help="ventana de llegada en MINUTOS, fija en todo el barrido (default: %(default)s)")
    p.add_argument("--seeds", default="1,2,3,4,5",
                   help="semillas separadas por coma (default: %(default)s). En "
                        "--plot-only, 'all' autodescubre las presentes.")
    p.add_argument("--plot-only", action="store_true",
                   help="no corre simulaciones: sólo grafica los output.csv ya "
                        "presentes en el layout n<Nmax>/seed<S>/.")
    p.add_argument("--om", default="cpm")
    p.add_argument("--zone", type=float, nargs=4, default=None,
                   metavar=("X0", "Y0", "X1", "Y1"),
                   help=f"zona observable x0 y0 x1 y1 (default: kiosco {ZONA_DEFAULT}; "
                        f"para la escalera sur usar {ZONA_ESCALERA_SUR})")
    p.add_argument("--out-dir", default=os.path.join(OUT_DIR, "ingreso_nmax"),
                   help="raíz de escenarios/outputs (default: %(default)s)")
    p.add_argument("--out-prefix", default=os.path.join(OUT_DIR, "ingreso_nmax"),
                   help="prefijo de los PNG (default: %(default)s)")
    args = p.parse_args()

    nmaxes = [int(round(float(v))) for v in args.nmax.split(",") if v.strip()]
    zona = tuple(args.zone) if args.zone is not None else ZONA_DEFAULT
    x0, y0, x1, y1 = zona
    seeds_sel = sweep_lib.parse_seeds_arg(args.seeds)   # None = 'all'

    # ── Corridas (salvo --plot-only) ──────────────────────────────────────────
    if not args.plot_only:
        if seeds_sel is None:
            print("[ERROR] --seeds all no es válido para correr (no se sabe qué seeds "
                  "simular). Pasá una lista explícita, p.ej. --seeds 1,2,3,4,5, o usá "
                  "--plot-only para graficar lo existente.", file=sys.stderr)
            sys.exit(2)
        classpath = build_classpath()
        for nmax in nmaxes:
            sdir = os.path.join(args.out_dir, f"n{nmax}", "scenario")
            gen_scenario(args.window, sdir)
            base = rescale_nmax(sdir, nmax)
            print(f"[ingreso-nmax] Nmax={nmax} (base={base}, factor period=×{base/nmax:.3f})")
            for seed in seeds_sel:
                out_csv = os.path.join(args.out_dir, f"n{nmax}", f"seed{seed}", "output.csv")
                run_simulation(classpath, sdir, out_csv, seed, args.om)

    # ── Agregación multi-seed + gráficos ─────────────────────────────────────
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from matplotlib.cm import ScalarMappable
    from matplotlib.colors import Normalize

    print(f"\nZona observable: x∈[{x0},{x1}]  y∈[{y0},{y1}]  z={ZLEVEL}")
    print(f"Realizaciones (seeds) por Nmax — selección: "
          f"{'all' if seeds_sel is None else seeds_sel}")
    print("Método población vs. t: promedio entre seeds sobre grilla común "
          "(misma dt_out; padding 0 si una corrida termina antes).")

    # Por cada Nmax: descubrir seeds en disco y agregar.
    curve_mean: dict[int, tuple[list[float], list[float], list[float] | None]] = {}
    per_seed_peak: dict[int, list[float]] = {}
    per_seed_mean: dict[int, list[float]] = {}
    per_seed_nreal: dict[int, list[int]] = {}
    peak_time_mean: dict[int, float] = {}
    n_seeds: dict[int, int] = {}

    for nmax in nmaxes:
        ndir = os.path.join(args.out_dir, f"n{nmax}")
        seed_csvs = sweep_lib.resolve_seed_csvs(ndir, seeds_sel)
        if seeds_sel is not None:
            faltan = [s for s in seeds_sel if s not in {s2 for s2, _ in seed_csvs}]
            if faltan:
                print(f"[WARN] Nmax={nmax}: seeds ausentes {faltan} "
                      f"(se usan {[s for s, _ in seed_csvs]}).")
        if not seed_csvs:
            print(f"[WARN] Nmax={nmax}: sin output.csv en {ndir}/seed*/. Se omite.")
            continue
        curves: list[tuple[list[float], list[int]]] = []
        nreals: list[int] = []
        for seed, csv_path in seed_csvs:
            times, counts = sweep_lib.zone_population(csv_path, x0, y0, x1, y1, zlevel=ZLEVEL)
            if not counts:
                print(f"[WARN] Nmax={nmax} seed={seed}: 0 frames.")
                continue
            curves.append((times, counts))
            nreals.append(len(sweep_lib.load_agents(csv_path)))
        if not curves:
            print(f"[WARN] Nmax={nmax}: ninguna seed con datos. Se omite.")
            continue
        times_ref, matrix, n = sweep_lib.align_population_curves(curves)
        means, stds = sweep_lib.column_mean_std(matrix)
        curve_mean[nmax] = (times_ref, means, stds)
        per_seed_peak[nmax] = [max(row) for row in matrix]
        per_seed_mean[nmax] = [sum(row) / len(row) for row in matrix]
        per_seed_nreal[nmax] = nreals
        n_seeds[nmax] = n
        if means:
            peak_time_mean[nmax] = times_ref[means.index(max(means))]

    if not curve_mean:
        print("[ERROR] no hay datos para graficar (¿corriste sin --plot-only?).", file=sys.stderr)
        sys.exit(1)

    nmaxes_ok = sorted(curve_mean.keys())
    norm = Normalize(vmin=min(nmaxes_ok),
                     vmax=max(nmaxes_ok) if max(nmaxes_ok) != min(nmaxes_ok) else min(nmaxes_ok) + 1)
    cmap = plt.get_cmap(_CMAP)

    # Tabla stdout.
    print(f"{'Nmax':>6} | {'n_seeds':>7} | {'N_efec(med)':>11} | {'pico±σ':>15} | "
          f"{'prom±σ':>15} | {'t_pico[s]':>10}")
    print("-" * 78)
    for nmax in nmaxes_ok:
        mp, sp = sweep_lib.mean_std(per_seed_peak[nmax])
        mm, smn = sweep_lib.mean_std(per_seed_mean[nmax])
        nreal_med = sum(per_seed_nreal[nmax]) / len(per_seed_nreal[nmax])
        pico_s = f"{mp:5.2f} ± {(sp if sp is not None else 0.0):4.2f}"
        prom_s = f"{mm:5.2f} ± {(smn if smn is not None else 0.0):4.2f}"
        t_pico = peak_time_mean.get(nmax, float("nan"))
        print(f"{nmax:>6} | {n_seeds[nmax]:>7} | {nreal_med:>11.1f} | {pico_s:>15} | "
              f"{prom_s:>15} | {t_pico:>10.1f}")

    # ---- Población vs. tiempo: curva media + banda ±σ, una por Nmax ----------
    fig1, ax1 = plt.subplots(figsize=(8.5, 5.0))
    single_seed_pts: list[int] = []
    for nmax in nmaxes_ok:
        times_ref, means, stds = curve_mean[nmax]
        color = cmap(norm(nmax))
        ax1.plot(times_ref, means, color=color, linewidth=1.8)
        if stds is not None:
            lo = [m - s for m, s in zip(means, stds)]
            hi = [m + s for m, s in zip(means, stds)]
            ax1.fill_between(times_ref, lo, hi, color=color, alpha=0.18, linewidth=0)
        else:
            single_seed_pts.append(nmax)
    ax1.set_xlabel("tiempo [s]")
    ax1.set_ylabel("agentes en la zona [agentes]")
    ax1.grid(color=_COLOR_GRID, linewidth=0.8, zorder=0)
    ax1.set_axisbelow(True)
    for s in ("top", "right"):
        ax1.spines[s].set_visible(False)
    sm1 = ScalarMappable(norm=norm, cmap=_CMAP)
    sm1.set_array([])
    cbar1 = fig1.colorbar(sm1, ax=ax1, fraction=0.046, pad=0.02)
    cbar1.set_label("Nmax (agentes)")
    fig1.tight_layout()
    pob_path = f"{args.out_prefix}_poblacion.png"
    os.makedirs(os.path.dirname(pob_path) or ".", exist_ok=True)
    fig1.savefig(pob_path, dpi=200, bbox_inches="tight")
    plt.close(fig1)

    # ---- Escalar: ocupación pico/promedio vs Nmax (media±σ) ------------------
    scal_nmax = list(nmaxes_ok)
    mean_peak = [sweep_lib.mean_std(per_seed_peak[n])[0] for n in scal_nmax]
    std_peak = [(sweep_lib.mean_std(per_seed_peak[n])[1] or 0.0) for n in scal_nmax]
    mean_mean = [sweep_lib.mean_std(per_seed_mean[n])[0] for n in scal_nmax]
    std_mean = [(sweep_lib.mean_std(per_seed_mean[n])[1] or 0.0) for n in scal_nmax]
    any_err = any(s > 0 for s in std_peak + std_mean)

    fig2, ax2 = plt.subplots(figsize=(6.5, 4.5))
    if any_err:
        ax2.errorbar(scal_nmax, mean_mean, yerr=std_mean, marker="o", markersize=7,
                     linestyle="none", capsize=4, color=_COLOR_PROM, label="promedio")
        ax2.errorbar(scal_nmax, mean_peak, yerr=std_peak, marker="s", markersize=7,
                     linestyle="none", capsize=4, color=_COLOR_MAX, label="máximo")
    else:
        ax2.plot(scal_nmax, mean_mean, marker="o", markersize=7, linestyle="none",
                 color=_COLOR_PROM, label="promedio")
        ax2.plot(scal_nmax, mean_peak, marker="s", markersize=7, linestyle="none",
                 color=_COLOR_MAX, label="máximo")
    ax2.legend(frameon=False)
    ax2.set_xlabel("Nmax (agentes)")
    ax2.set_ylabel("ocupación de la zona [agentes]")
    ax2.grid(color=_COLOR_GRID, linewidth=0.8, zorder=0)
    ax2.set_axisbelow(True)
    for s in ("top", "right"):
        ax2.spines[s].set_visible(False)
    fig2.tight_layout()
    scal_path = f"{args.out_prefix}_scalar.png"
    fig2.savefig(scal_path, dpi=200, bbox_inches="tight")
    plt.close(fig2)

    if single_seed_pts:
        print(f"\n[nota] Nmax={single_seed_pts} con 1 sola semilla: sin banda ni "
              f"barra de error (σ no estimable con <2 realizaciones).")
    if any_err:
        print("[nota] barras/banda de error = σ muestral (ddof=1) entre semillas; si "
              "quedan más chicas que el marker (capsize=4) la dispersión es baja.")
    print(f"\nGuardado: {pob_path}")
    print(f"Guardado: {scal_path}")


if __name__ == "__main__":
    main()
