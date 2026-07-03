#!/usr/bin/env python3
"""Runner FÁCIL del sub-escenario Ingreso variando la CANTIDAD DE AGENTES (Nmax).

El sub-escenario Ingreso (Task 4) tiene como input el *caudal* (la ventana de
llegada 1/5/10 min) con Nmax=120 FIJO. Este script agrega el eje ortogonal
"cuánta gente entra": corre Ingreso para varios Nmax a una ventana FIJA y grafica
cómo escala la congestión (ocupación en la zona de interés — por default el frente
del kiosco del recreo, donde se forma la congestión del Ingreso; ver --zone) con Nmax.

Es autocontenido y NO modifica ``build_escuela.py``: genera el escenario normal
(Nmax=120) y luego **reescala el caudal** multiplicando el ``period`` de cada
generador en el ``parameters.json`` por ``base/Nmax`` (el total de spawns es
lineal en ``1/period``, así que escalar todos los períodos por igual lleva el
total de ``base`` a ``Nmax``). No hace falta tocar el builder.

Uso típico (una sola línea):

    python tools/run_ingreso_nmax.py --nmax 60,120,180

Otras opciones:

    python tools/run_ingreso_nmax.py --nmax 40,80,120,160 --window 5 --seed 1

Salidas:
  - out/ingreso_nmax/n<Nmax>/{scenario/, output.csv}   (uno por Nmax)
  - out/ingreso_nmax_poblacion.png   (población vs tiempo en la zona, una curva por Nmax)
  - out/ingreso_nmax_scalar.png      (ocupación pico/promedio vs Nmax)
  - tabla a stdout con N efectivo, pico, promedio y t_pico por Nmax.

Ojo: el generador topa el spawn en ~3 personas/min por metro del lado mayor de la
zona de entrada. A ventana chica (1 min) y Nmax grande podés chocar ese techo y el
N EFECTIVO quedar por debajo del pedido — el script reporta el N efectivo real para
que se vea. La ventana default (5 min) deja mucho margen.
"""
from __future__ import annotations

import argparse
import json
import math
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

# Paleta (misma que plot_evacuacion.py / plot_ingreso.py).
_COLOR_PROM = "#2a78d6"
_COLOR_MAX = "#1baf7a"
_COLOR_GRID = "#e1e0d9"
_COLOR_MUTED = "#898781"
_CURVE_COLORS = ["#2a78d6", "#1baf7a", "#eda100", "#d1495b", "#8a5cd1", "#00857a"]


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
        description="Corre el Ingreso variando la cantidad de agentes (Nmax) a ventana fija.")
    p.add_argument("--nmax", default="60,120,180",
                   help="valores de Nmax separados por coma (default: %(default)s)")
    p.add_argument("--window", type=float, default=5.0,
                   help="ventana de llegada en MINUTOS, fija en todo el barrido (default: %(default)s)")
    p.add_argument("--seed", type=int, default=1, help="semilla (default: %(default)s)")
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

    classpath = build_classpath()

    results = []  # (nmax, csv_path, n_efectivo)
    for nmax in nmaxes:
        sdir = os.path.join(args.out_dir, f"n{nmax}", "scenario")
        gen_scenario(args.window, sdir)
        base = rescale_nmax(sdir, nmax)
        print(f"[ingreso-nmax] Nmax={nmax} (base={base}, factor period=×{base/nmax:.3f})")
        out_csv = os.path.join(args.out_dir, f"n{nmax}", "output.csv")
        run_simulation(classpath, sdir, out_csv, args.seed, args.om)
        n_real = len(sweep_lib.load_agents(out_csv))
        results.append((nmax, out_csv, n_real))

    # ── Análisis del observable (ocupación en la zona antes de la escalera) ──
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import statistics

    print(f"\nZona observable: x∈[{x0},{x1}]  y∈[{y0},{y1}]  z={ZLEVEL}")
    print(f"{'Nmax':>6} | {'N_efec':>7} | {'pico':>5} | {'prom':>6} | {'t_pico':>7}")
    print("-" * 44)

    fig1, ax1 = plt.subplots(figsize=(8.5, 5.0))
    scal_nmax, scal_max, scal_prom = [], [], []
    for i, (nmax, csv_path, n_real) in enumerate(results):
        times, counts = sweep_lib.zone_population(csv_path, x0, y0, x1, y1, zlevel=ZLEVEL)
        if not counts:
            print(f"{nmax:>6} | {n_real:>7} | {'-':>5} | {'-':>6} | {'-':>7}")
            continue
        pico = max(counts)
        prom = statistics.mean(counts)
        t_pico = times[counts.index(pico)]
        print(f"{nmax:>6} | {n_real:>7} | {pico:>5} | {prom:>6.2f} | {t_pico:>7.1f}")
        color = _CURVE_COLORS[i % len(_CURVE_COLORS)]
        ax1.plot(times, counts, color=color, linewidth=1.8,
                 label=f"Nmax={nmax} (N={n_real})")
        scal_nmax.append(nmax)
        scal_max.append(pico)
        scal_prom.append(prom)

    ax1.set_xlabel("tiempo [s]")
    ax1.set_ylabel("agentes en la zona")
    ax1.set_title(f"Ingreso — población en la zona observada "
                  f"(ventana {args.window:g} min, seed {args.seed})")
    ax1.grid(color=_COLOR_GRID, linewidth=0.8, zorder=0)
    ax1.set_axisbelow(True)
    for s in ("top", "right"):
        ax1.spines[s].set_visible(False)
    ax1.legend(frameon=False)
    fig1.tight_layout()
    pob_path = f"{args.out_prefix}_poblacion.png"
    os.makedirs(os.path.dirname(pob_path) or ".", exist_ok=True)
    fig1.savefig(pob_path, dpi=130)
    plt.close(fig1)

    fig2, ax2 = plt.subplots(figsize=(6.5, 4.5))
    if scal_nmax:
        ax2.plot(scal_nmax, scal_prom, marker="o", markersize=7, linewidth=2,
                 color=_COLOR_PROM, label="promedio")
        ax2.plot(scal_nmax, scal_max, marker="s", markersize=7, linewidth=2,
                 color=_COLOR_MAX, label="máximo")
        ax2.legend(frameon=False)
    else:
        ax2.text(0.5, 0.5, "sin datos", ha="center", va="center",
                 color=_COLOR_MUTED, transform=ax2.transAxes)
    ax2.set_xlabel("Nmax (agentes)")
    ax2.set_ylabel("ocupación en la zona [agentes]")
    ax2.set_title(f"Ocupación en la zona vs Nmax (ventana {args.window:g} min, seed {args.seed})")
    ax2.grid(color=_COLOR_GRID, linewidth=0.8, zorder=0)
    ax2.set_axisbelow(True)
    for s in ("top", "right"):
        ax2.spines[s].set_visible(False)
    fig2.tight_layout()
    scal_path = f"{args.out_prefix}_scalar.png"
    fig2.savefig(scal_path, dpi=130)
    plt.close(fig2)

    print(f"\nGuardado: {pob_path}")
    print(f"Guardado: {scal_path}")


if __name__ == "__main__":
    main()
