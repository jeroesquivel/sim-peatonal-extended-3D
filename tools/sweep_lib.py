"""
sweep_lib.py — librería PURA de carga y métricas sobre el output.csv del
simulador (D10: ``tout; x; y; z; vx; vy; state; id``, separador '; ', sin
header, coordenadas con punto decimal Locale.US). Sin matplotlib ni ninguna
dependencia de graficado: sólo parsing/numérico con stdlib, para que la
puedan importar tanto scripts de barrido (tools/sweep_run.py) como los
scripts de gráficos de los sub-escenarios Evacuación (Task 2) e Ingreso
(Task 4) sin arrastrar el backend de plots.

Uso como script (resumen rápido de un output.csv):
    python tools/sweep_lib.py out/sweeps/evacuacion/v100/seed1/output.csv
"""

from __future__ import annotations

import glob
import os
import re
import sys
from collections import defaultdict
from statistics import mean, stdev
from typing import Iterator


def _iter_rows(csv_path: str) -> Iterator[tuple[float, float, float, float, float, float, str, str]]:
    """Parsea el output D10 línea a línea.

    Yields (tout, x, y, z, vx, vy, state, agent_id). ``agent_id`` se normaliza
    a ``str(int(...))`` (la columna se escribe como entero, ``%d``).
    """
    with open(csv_path, "r", encoding="utf-8-sig") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = [p.strip() for p in line.split(";")]
            if len(parts) < 8:
                continue
            tout = float(parts[0])
            x, y, z = float(parts[1]), float(parts[2]), float(parts[3])
            vx, vy = float(parts[4]), float(parts[5])
            state = parts[6]
            agent_id = str(int(parts[7]))
            yield tout, x, y, z, vx, vy, state, agent_id


def load_agents(csv_path: str) -> dict[str, list[tuple]]:
    """id -> lista de ``(t, x, y, z, vx, vy, state)`` ordenada por ``t``."""
    agents: dict[str, list[tuple]] = defaultdict(list)
    for tout, x, y, z, vx, vy, state, agent_id in _iter_rows(csv_path):
        agents[agent_id].append((tout, x, y, z, vx, vy, state))
    for traj in agents.values():
        traj.sort(key=lambda row: row[0])
    return dict(agents)


def load_frames(csv_path: str) -> dict[float, list[dict]]:
    """``t`` -> lista de ``{x,y,z,vx,vy,state,id}`` (una entrada por agente
    presente en ese frame de output)."""
    frames: dict[float, list[dict]] = defaultdict(list)
    for tout, x, y, z, vx, vy, state, agent_id in _iter_rows(csv_path):
        frames[tout].append({
            "x": x, "y": y, "z": z, "vx": vx, "vy": vy, "state": state, "id": agent_id,
        })
    return dict(sorted(frames.items()))


def evac_times(csv_path: str) -> list[float]:
    """Tiempo de evacuación (``t_ultimo - t_primero``) de cada agente que
    DESAPARECE antes del último ``t`` global del output (es decir, que evacuó
    dentro de la ventana simulada). Los agentes que siguen presentes en el
    último frame (no evacuaron a tiempo) NO cuentan."""
    agents = load_agents(csv_path)
    if not agents:
        return []
    t_last_global = max(traj[-1][0] for traj in agents.values())
    times: list[float] = []
    for traj in agents.values():
        t_first = traj[0][0]
        t_last = traj[-1][0]
        if t_last < t_last_global - 1e-9:
            times.append(t_last - t_first)
    return times


def zone_population(
    csv_path: str,
    x0: float,
    y0: float,
    x1: float,
    y1: float,
    zlevel: float | None = None,
) -> tuple[list[float], list[int]]:
    """(tiempos ordenados, conteo por frame) de agentes dentro del rectángulo
    ``[x0,x1] x [y0,y1]``. Si ``zlevel`` no es None, además filtra por
    ``abs(z - zlevel) < 0.4`` (agentes en esa planta / escalón)."""
    xlo, xhi = min(x0, x1), max(x0, x1)
    ylo, yhi = min(y0, y1), max(y0, y1)
    frames = load_frames(csv_path)
    times = sorted(frames.keys())
    counts: list[int] = []
    for t in times:
        n = 0
        for a in frames[t]:
            if not (xlo <= a["x"] <= xhi and ylo <= a["y"] <= yhi):
                continue
            if zlevel is not None and abs(a["z"] - zlevel) >= 0.4:
                continue
            n += 1
        counts.append(n)
    return times, counts


# ---------------------------------------------------------------------------
# Agregación multi-semilla (realizaciones)
#
# La cátedra exige >=5 realizaciones por punto, con barras de error siempre
# visibles y explicitando el método al promediar evoluciones temporales. Estos
# helpers son PUROS (stdlib) para que los importen los scripts de graficado sin
# arrastrar matplotlib: descubren las seeds presentes en el layout de
# tools/sweep_run.py (``<sweep>/v<value>/seed<S>/output.csv``), resuelven la
# selección de seeds pedida por CLI, y agregan métricas entre semillas.
# ---------------------------------------------------------------------------


def parse_seeds_arg(spec: str | None) -> list[int] | None:
    """Traduce el argumento CLI ``--seeds`` a una selección de semillas.

    - ``None`` / ``""`` / ``"all"`` -> ``None`` (autodescubrir todas las
      presentes en el layout, resuelto luego por :func:`resolve_seed_csvs`).
    - lista separada por comas (o espacios), p.ej. ``"1,2,3"`` -> ``[1, 2, 3]``
      (ordenada y sin duplicados).
    """
    if spec is None:
        return None
    s = spec.strip().lower()
    if s == "" or s == "all":
        return None
    seeds: list[int] = []
    for tok in spec.replace(" ", ",").split(","):
        tok = tok.strip()
        if not tok:
            continue
        seeds.append(int(tok))
    return sorted(set(seeds))


def discover_seeds(value_dir: str) -> list[tuple[int, str]]:
    """Escanea ``value_dir/seed<S>/output.csv`` y devuelve ``[(seed, csv), ...]``
    ordenado por seed ascendente. Sólo cuenta los que realmente tienen el CSV."""
    found: list[tuple[int, str]] = []
    for path in glob.glob(os.path.join(value_dir, "seed*", "output.csv")):
        d = os.path.basename(os.path.dirname(path))
        m = re.match(r"^seed(\d+)$", d)
        if not m:
            continue
        found.append((int(m.group(1)), path))
    found.sort(key=lambda t: t[0])
    return found


def resolve_seed_csvs(value_dir: str, seeds: list[int] | None) -> list[tuple[int, str]]:
    """Resuelve las seeds a graficar dentro de un ``value_dir``.

    ``seeds is None`` -> todas las presentes (autodescubrimiento). Si es una
    lista, se devuelven sólo las pedidas que EXISTEN (las que falten se ignoran
    silenciosamente acá; el caller es responsable de avisar si quiere). Siempre
    ordenado por seed."""
    present = dict(discover_seeds(value_dir))
    if seeds is None:
        return sorted(present.items())
    return [(s, present[s]) for s in seeds if s in present]


def discover_values(sweep_dir: str) -> list[tuple[int, str]]:
    """Escanea ``sweep_dir/v<N>/`` y devuelve ``[(N, value_dir), ...]`` ordenado
    por N ascendente. Sólo incluye los ``v<N>`` que tengan al menos una
    ``seed<S>/output.csv`` adentro."""
    found: list[tuple[int, str]] = []
    for vdir in glob.glob(os.path.join(sweep_dir, "v*")):
        if not os.path.isdir(vdir):
            continue
        m = re.match(r"^v(\d+)$", os.path.basename(vdir))
        if not m:
            continue
        if not discover_seeds(vdir):
            continue
        found.append((int(m.group(1)), vdir))
    found.sort(key=lambda t: t[0])
    return found


def mean_std(values: list[float]) -> tuple[float, float | None]:
    """Estadística cross-seed de una lista de valores (uno por semilla):
    ``(media, desvío estándar muestral ddof=1)``. Con <2 valores el desvío es
    ``None`` (no hay dispersión estimable -> el caller omite la barra de error).
    Lista vacía -> ``(nan, None)``."""
    vals = list(values)
    if not vals:
        return (float("nan"), None)
    m = mean(vals)
    sd = stdev(vals) if len(vals) >= 2 else None
    return (m, sd)


def align_population_curves(
    curves: list[tuple[list[float], list[int]]],
) -> tuple[list[float], list[list[float]], int]:
    """Alinea curvas de conteo (población vs. tiempo) de varias realizaciones a
    la grilla temporal COMÚN, para poder promediarlas entre semillas.

    Método (explicitado también por stdout en los scripts): todas las corridas
    de un mismo punto del barrido comparten la misma ``dt_out`` ⇒ la misma
    grilla de ``tout``. Si una corrida termina antes (menos frames), su curva es
    un PREFIJO de la más larga y se rellena con **0** hasta el horizonte común
    (la zona observada queda vacía cuando la corrida ya terminó). Se *asserta*
    que las grillas coinciden en su parte común (tolerancia 1e-6); si no,
    aborta con AssertionError en vez de promediar peras con manzanas.

    Devuelve ``(times_ref, matrix, n)`` donde ``times_ref`` es la grilla de
    referencia (la más larga), ``matrix[s][j]`` el conteo de la semilla ``s`` en
    el índice temporal ``j`` (padded con 0), y ``n`` la cantidad de semillas.
    """
    curves = [c for c in curves if c[0]]
    if not curves:
        return ([], [], 0)
    ref_times = max((t for t, _ in curves), key=len)
    L = len(ref_times)
    matrix: list[list[float]] = []
    for times, counts in curves:
        k = len(times)
        for i in range(k):
            assert abs(times[i] - ref_times[i]) < 1e-6, (
                f"grillas temporales desalineadas en i={i}: "
                f"{times[i]} != {ref_times[i]} (¿dt_out distinto entre seeds?)"
            )
        matrix.append([float(v) for v in counts] + [0.0] * (L - k))
    return (list(ref_times), matrix, len(matrix))


def column_mean_std(matrix: list[list[float]]) -> tuple[list[float], list[float] | None]:
    """Media y desvío muestral (ddof=1) por columna (por instante temporal) de
    una matriz ``[seed][j]`` ya alineada. El desvío es ``None`` si hay <2 filas
    (una sola semilla -> banda no estimable)."""
    if not matrix:
        return ([], None)
    n = len(matrix)
    L = len(matrix[0])
    means = [mean([matrix[s][j] for s in range(n)]) for j in range(L)]
    if n < 2:
        return (means, None)
    stds = [stdev([matrix[s][j] for s in range(n)]) for j in range(L)]
    return (means, stds)


def _print_summary(csv_path: str) -> None:
    agents = load_agents(csv_path)
    n = len(agents)
    tmax = max((traj[-1][0] for traj in agents.values()), default=0.0)
    times = evac_times(csv_path)
    print(f"csv             : {csv_path}")
    print(f"agentes totales : {n}")
    print(f"t_max           : {tmax:.2f}")
    print(f"evacuados       : {len(times)} / {n}")
    if times:
        print(f"evac_time avg   : {sum(times) / len(times):.2f}")
        print(f"evac_time max   : {max(times):.2f}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: python tools/sweep_lib.py <output.csv>")
        raise SystemExit(1)
    _print_summary(sys.argv[1])
