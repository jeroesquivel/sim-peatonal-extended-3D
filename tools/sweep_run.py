"""
sweep_run.py — runner de barridos reproducibles sobre el escenario Escuela.

Para cada valor de ``--values`` genera un escenario (Formato B) con
``build_escuela.py --mode <mode> --value <v>`` y, para cada combinación
``(value, seed)`` de ``--values`` x ``--seeds``, corre la simulación
(``ar.edu.itba.simped.App``, ``om=cpm`` por default) pasando
``-Dsimped.seed=<seed>`` para que la corrida sea reproducible.

Usado por los sub-escenarios Evacuación (``--mode evacuacion``, value =
capacidad N) e Ingreso (``--mode ingreso``, value = ventana de llegada en
minutos); ``--mode baseline`` sirve de smoke test. Los tres modos están
implementados en ``build_escuela.py``.

Requisitos:
    - Java 21 + Maven 3.9+ en PATH.
    - ``mvn -q compile`` corrido al menos una vez (si no, este script lo corre).
    - Formato B (Escuela) necesita Jackson en el classpath: por eso se arma el
      classpath completo con maven-dependency-plugin en vez de correr el jar
      (que no es fat-jar).

Layout de salida:
    out/sweeps/<mode>/v<value>/scenario/            CSVs + parameters.json
    out/sweeps/<mode>/v<value>/seed<seed>/output.csv

Uso:
    python tools/sweep_run.py --mode baseline --values 0 --seeds 1        # smoke test
    python tools/sweep_run.py --mode evacuacion --values 50,100,150 --seeds 1,2,3
    python tools/sweep_run.py --mode ingreso --values 1,5,10 --seeds 1,2,3 --om cpm
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir))
BUILD_SCRIPT = os.path.join(REPO_ROOT, "tools", "scenarios-builders", "build_escuela.py")
OUT_DIR = os.path.join(REPO_ROOT, "out")
CP_FILE = os.path.join(OUT_DIR, ".cp.txt")
CLASSES_DIR = os.path.join(REPO_ROOT, "target", "classes")
MAIN_CLASS = "ar.edu.itba.simped.App"


def _which_or_raise(cmd: str) -> str:
    """Resuelve la ruta real del ejecutable (necesario en Windows: ``mvn`` es
    en realidad ``mvn.cmd`` y ``subprocess`` sin ``shell=True`` no resuelve
    extensiones de PATHEXT por sí solo)."""
    path = shutil.which(cmd)
    if path is None:
        raise FileNotFoundError(f"no se encontró '{cmd}' en PATH")
    return path


MVN = _which_or_raise("mvn")
JAVA = _which_or_raise("java")


def _fmt_value(v: float) -> str:
    """Formatea ``value`` para usarlo en un path (``v100``, ``v0_5``)."""
    if float(v).is_integer():
        return str(int(v))
    return str(v).replace(".", "_").replace("-", "neg")


def build_classpath() -> str:
    """Arma (UNA vez por corrida del runner) ``target/classes:<deps>`` vía
    maven-dependency-plugin. Si ``target/classes`` no existe corre ``mvn -q
    compile`` primero."""
    if not os.path.isdir(CLASSES_DIR):
        print("[sweep] target/classes no existe: corriendo 'mvn -q compile'...")
        subprocess.run([MVN, "-q", "compile"], cwd=REPO_ROOT, check=True)

    os.makedirs(OUT_DIR, exist_ok=True)
    print("[sweep] armando classpath (mvn dependency:build-classpath)...")
    subprocess.run(
        [
            MVN, "-q", "dependency:build-classpath",
            f"-Dmdep.outputFile={CP_FILE}",
        ],
        cwd=REPO_ROOT,
        check=True,
    )
    with open(CP_FILE, "r", encoding="utf-8") as f:
        deps_cp = f.read().strip()
    return CLASSES_DIR + os.pathsep + deps_cp


def gen_scenario(mode: str, value: float, scenario_dir: str) -> None:
    os.makedirs(scenario_dir, exist_ok=True)
    cmd = [
        sys.executable, BUILD_SCRIPT,
        "--mode", mode,
        "--value", str(value),
        "--out", scenario_dir,
    ]
    print(f"[sweep] generando escenario: mode={mode} value={value} -> {scenario_dir}")
    subprocess.run(cmd, check=True)


def run_simulation(classpath: str, scenario_dir: str, output_csv: str, seed: int, om: str) -> None:
    os.makedirs(os.path.dirname(output_csv), exist_ok=True)
    cmd = [
        JAVA, f"-Dsimped.seed={seed}",
        "-cp", classpath,
        MAIN_CLASS,
        scenario_dir, output_csv, om,
    ]
    print(f"[sweep] corriendo: seed={seed} scenario={scenario_dir} -> {output_csv}")
    subprocess.run(cmd, cwd=REPO_ROOT, check=True)


def main() -> int:
    p = argparse.ArgumentParser(description="Barrido reproducible sobre el escenario Escuela")
    p.add_argument("--mode", required=True, choices=["baseline", "evacuacion", "ingreso"])
    p.add_argument("--values", required=True,
                    help="valores del barrido separados por coma, p.ej. 50,100,150")
    p.add_argument("--seeds", required=True,
                    help="seeds separadas por coma, p.ej. 1,2,3")
    p.add_argument("--om", default="cpm", help="modelo operacional (default: cpm)")
    p.add_argument("--tmax-note", action="store_true",
                    help="sólo informativo: recuerda revisar max_time del escenario para "
                         "el value más grande del barrido")
    args = p.parse_args()

    try:
        values = [float(v) for v in args.values.split(",") if v.strip() != ""]
        seeds = [int(s) for s in args.seeds.split(",") if s.strip() != ""]
    except ValueError as e:
        print(f"[sweep] --values/--seeds inválidos: {e}", file=sys.stderr)
        return 1
    if not values or not seeds:
        print("[sweep] --values y --seeds no pueden ser vacíos", file=sys.stderr)
        return 1

    if args.tmax_note:
        print("[sweep] recordatorio: confirmar que max_time del escenario alcanza para "
              "el value más grande del barrido (evacuación completa / ventana de ingreso).")

    classpath = build_classpath()

    sweep_root = os.path.join(OUT_DIR, "sweeps", args.mode)
    outputs: list[str] = []
    failures: list[str] = []

    for value in values:
        vtag = _fmt_value(value)
        scenario_dir = os.path.join(sweep_root, f"v{vtag}", "scenario")
        try:
            gen_scenario(args.mode, value, scenario_dir)
        except subprocess.CalledProcessError as e:
            print(f"[sweep] FALLÓ generación de escenario value={value}: {e}", file=sys.stderr)
            failures.append(f"gen-scenario value={value}")
            continue

        for seed in seeds:
            output_csv = os.path.join(sweep_root, f"v{vtag}", f"seed{seed}", "output.csv")
            try:
                run_simulation(classpath, scenario_dir, output_csv, seed, args.om)
                outputs.append(output_csv)
            except subprocess.CalledProcessError as e:
                print(f"[sweep] FALLÓ corrida value={value} seed={seed}: {e}", file=sys.stderr)
                failures.append(f"run value={value} seed={seed}")

    print("\n[sweep] resumen")
    print(f"  corridas OK       : {len(outputs)}")
    for o in outputs:
        print(f"    {o}")
    if failures:
        print(f"  corridas FALLIDAS : {len(failures)}")
        for f_ in failures:
            print(f"    {f_}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
