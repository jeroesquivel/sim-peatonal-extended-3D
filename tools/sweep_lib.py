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

import sys
from collections import defaultdict
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
