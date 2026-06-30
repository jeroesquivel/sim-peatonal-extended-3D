"""
Builder paramétrico del escenario ESCUELA (paso 8, ampliación 3D).

Mapa cuadrado 60x60 (metros):
  - Mitad izquierda (x 0..30): RECREO, una sola planta (z=0), con un kiosco
    (server tipo queue) en una esquina y una salida al exterior por el borde
    izquierdo.
  - Mitad derecha (x 30..60): EDIFICIO, dos plantas (PB z=0, P1 z=3). El edificio
    se alarga en y; un pasillo vertical central (x 42..48) separa las aulas en
    izquierda (x 30..42) y derecha (x 48..60). 4 aulas por lado y por planta
    (8 por planta, 16 totales). Dos escaleras en las puntas (y~0 e y~60) conectan
    PB y P1. La PB se comunica con el recreo por las dos esquinas del edificio.

Salidas/entradas: una en la PB del edificio (borde derecho) y otra en el recreo
(borde izquierdo). La P1 no tiene salida propia: se evacúa bajando por escaleras.

Genera (Formato B): WALLS/EXITS/GENERATORS/TARGETS/SERVERS/STAIRS .csv + parameters.json.

Uso:
    python tools/scenarios-builders/build_escuela.py            # -> scenarios/escuela
    python tools/scenarios-builders/build_escuela.py --out scenarios/escuela
"""

from __future__ import annotations

import argparse
import json
import os

# ── Geometría (constantes del layout) ────────────────────────────────────────
MAP = 60.0
FLOOR_H = 3.0
FLOORS = [0.0, FLOOR_H]

RECREO_X = (0.0, 30.0)
BUILD_X = (30.0, 60.0)

CORRIDOR_X = (42.0, 48.0)           # pasillo vertical
LEFT_ROOMS_X = (30.0, 42.0)
RIGHT_ROOMS_X = (48.0, 60.0)

BAND_Y = (8.0, 52.0)                # franja de aulas en y (entre las dos puntas)
END_BOTTOM = (0.0, 8.0)            # punta sur (escalera)
END_TOP = (52.0, 60.0)             # punta norte (escalera)
N_ROOMS = 4                         # aulas por lado y planta
DOOR = 1.6                          # ancho de puerta de aula
OPEN = 6.0                          # ancho de aberturas (esquinas / salidas)

LEFT_ROOM_CX = sum(LEFT_ROOMS_X) / 2
RIGHT_ROOM_CX = sum(RIGHT_ROOMS_X) / 2


def room_bounds() -> list[tuple[float, float]]:
    y0, y1 = BAND_Y
    h = (y1 - y0) / N_ROOMS
    return [(y0 + i * h, y0 + (i + 1) * h) for i in range(N_ROOMS)]


def room_centers_y() -> list[float]:
    return [(a + b) / 2 for a, b in room_bounds()]


# ── Helpers de paredes ───────────────────────────────────────────────────────
def vseg(walls, x, ya, yb, z):
    """Pared vertical en x de ya a yb (ordena), si tiene largo."""
    if abs(yb - ya) > 1e-9:
        walls.append((x, min(ya, yb), x, max(ya, yb), z))


def hseg(walls, y, xa, xb, z):
    if abs(xb - xa) > 1e-9:
        walls.append((min(xa, xb), y, max(xa, xb), y, z))


def vwall_with_gaps(walls, x, ya, yb, gaps, z):
    """Pared vertical con huecos (lista de (g0,g1)); emite los tramos sólidos."""
    cuts = sorted(gaps)
    cur = ya
    for g0, g1 in cuts:
        vseg(walls, x, cur, g0, z)
        cur = g1
    vseg(walls, x, cur, yb, z)


def hwall_with_gaps(walls, y, xa, xb, gaps, z):
    cuts = sorted(gaps)
    cur = xa
    for g0, g1 in cuts:
        hseg(walls, y, cur, g0, z)
        cur = g1
    hseg(walls, y, cur, xb, z)


def build_walls() -> list[tuple[float, float, float, float, float]]:
    walls: list[tuple] = []

    # ── Recreo (z=0): perímetro, salida en el borde izquierdo ──
    rec_exit = (27.0, 27.0 + OPEN)
    vwall_with_gaps(walls, 0.0, 0.0, MAP, [rec_exit], 0.0)   # borde izquierdo con salida
    hseg(walls, 0.0, RECREO_X[0], RECREO_X[1], 0.0)          # borde inferior
    hseg(walls, MAP, RECREO_X[0], RECREO_X[1], 0.0)          # borde superior

    # ── Edificio (cada planta) ──
    corner_bottom = (0.5, 0.5 + OPEN)
    corner_top = (MAP - 0.5 - OPEN, MAP - 0.5)
    build_exit = (2.0, 2.0 + (OPEN - 2.0))                    # salida PB borde derecho (y 2..6)
    centers = room_centers_y()

    for z in FLOORS:
        is_pb = abs(z) < 1e-9
        # Pared izquierda x=30: en PB se abre en las dos esquinas (al recreo); en P1 es sólida.
        if is_pb:
            vwall_with_gaps(walls, BUILD_X[0], 0.0, MAP, [corner_bottom, corner_top], z)
        else:
            vseg(walls, BUILD_X[0], 0.0, MAP, z)
        # Pared derecha x=60: en PB se abre en la salida del edificio; en P1 sólida.
        if is_pb:
            vwall_with_gaps(walls, BUILD_X[1], 0.0, MAP, [build_exit], z)
        else:
            vseg(walls, BUILD_X[1], 0.0, MAP, z)
        # Bordes inferior/superior del edificio.
        hseg(walls, 0.0, BUILD_X[0], BUILD_X[1], z)
        hseg(walls, MAP, BUILD_X[0], BUILD_X[1], z)

        # Límites de la franja de aulas con las puntas (pasillo abierto en x 42..48).
        for y in (BAND_Y[0], BAND_Y[1]):
            hseg(walls, y, LEFT_ROOMS_X[0], LEFT_ROOMS_X[1], z)
            hseg(walls, y, RIGHT_ROOMS_X[0], RIGHT_ROOMS_X[1], z)
        # Tabiques horizontales entre aulas (a cada lado).
        for a, b in room_bounds()[1:]:
            hseg(walls, a, LEFT_ROOMS_X[0], LEFT_ROOMS_X[1], z)
            hseg(walls, a, RIGHT_ROOMS_X[0], RIGHT_ROOMS_X[1], z)
        # Paredes del pasillo con puertas a cada aula.
        door_gaps = [(cy - DOOR / 2, cy + DOOR / 2) for cy in centers]
        vwall_with_gaps(walls, CORRIDOR_X[0], BAND_Y[0], BAND_Y[1], door_gaps, z)  # lado aulas izq
        vwall_with_gaps(walls, CORRIDOR_X[1], BAND_Y[0], BAND_Y[1], door_gaps, z)  # lado aulas der

    return walls


def build_stairs() -> list[tuple]:
    # Escaleras en las puntas, dentro del pasillo (x 43..47, width=4). Eje en y,
    # del pie (PB) al tope (P1). speed_factor default (0.5) en el reader.
    cx = sum(CORRIDOR_X) / 2
    return [
        # block, x1,y1,z1, x2,y2,z2, width
        ("ESC_SUR", cx, 7.0, 0.0, cx, 2.0, FLOOR_H, 4.0),
        ("ESC_NORTE", cx, 53.0, 0.0, cx, 58.0, FLOOR_H, 4.0),
    ]


def build_targets() -> list[tuple]:
    # AULA: un punto CIRCLE en el centro de cada aula, en ambas plantas (16).
    out = []
    for z in FLOORS:
        for cy in room_centers_y():
            out.append(("AULA", 0.5, LEFT_ROOM_CX, cy, z))
            out.append(("AULA", 0.5, RIGHT_ROOM_CX, cy, z))
    return out


def build_servers() -> list[tuple]:
    # Kiosco en una esquina del recreo (server queue + línea de cola).
    return [
        ("KIOSCO_1_SERVER", 5.0, 50.0, 0.0, 8.0, 52.0, 0.0),
        ("KIOSCO_1_QUEUE000", 5.0, 49.5, 0.0, 5.0, 44.5, 0.0),
    ]


def build_exits() -> list[tuple]:
    return [
        ("RECREO", 0.0, 27.0, 0.0, 0.0, 27.0 + OPEN, 0.0),
        ("EDIFICIO_PB", 60.0, 2.0, 0.0, 60.0, 6.0, 0.0),
    ]


def build_generators() -> list[tuple]:
    return [
        ("INGRESO_RECREO", 2.0, 27.0, 0.0, 5.0, 33.0, 0.0),
        ("INGRESO_EDIFICIO", 55.0, 2.0, 0.0, 58.0, 6.0, 0.0),
    ]


# ── Escritura de CSV ─────────────────────────────────────────────────────────
def write_csv(path, header, rows):
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(header + "\n")
        for r in rows:
            f.write(", ".join(_fmt(v) for v in r) + "\n")


def _fmt(v):
    if isinstance(v, float):
        return f"{v:g}"
    return str(v)


def build_parameters() -> dict:
    # Baseline (paso 8.1): los agentes entran por ambas entradas y van a un aula al
    # azar (puede estar en P1 → suben por una escalera), asisten la clase y salen
    # por una salida al azar. Demuestra el ruteo multiplanta de punta a punta.
    # El kiosco queda definido en la geometría (recreo) para los sub-escenarios
    # (8.2): no se incluye en el plan baseline para no crear un cuello de botella
    # de un único server con ~todos los agentes.
    gen_agents = {
        "min_radius_distribution": {"type": "UNIFORM", "min": 0.15, "max": 0.15},
        "max_radius_distribution": {"type": "UNIFORM", "min": 0.30, "max": 0.32},
        "max_velocity": 1.4,
    }
    generation = {"period": 3.0, "quantity_distribution": {"type": "UNIFORM", "min": 1.0, "max": 2.0}}
    generators = [
        {"block_name": b, "plan": "DIA_ESCOLAR", "agents": gen_agents,
         "active_time": 40.0, "inactive_time": 0.0, "generation": generation}
        for b in ("INGRESO_RECREO", "INGRESO_EDIFICIO")
    ]
    return {
        "max_time": 200.0,
        "output_delta_time": 0.2,
        "blueprint_name": "escuela",
        "agents_generators": generators,
        "targets": [
            {"block_name": "AULA",
             "attending_time_distribution": {"type": "UNIFORM", "min": 15.0, "max": 30.0}}
        ],
        "servers": [
            {"block_name": "KIOSCO",
             "attending_time_distribution": {"type": "GAUSSIAN", "mean": 8.0, "std": 2.0},
             "max_capacity": 1}
        ],
        "plans": [
            {"name": "DIA_ESCOLAR", "exit_selection": "RANDOM", "objective_groups": [
                {"block_name": "AULA", "layer": "TARGETS", "objective_selection": "RANDOM",
                 "quantity_distribution": {"type": "UNIFORM", "min": 1.0, "max": 1.0}},
            ]},
        ],
    }


def main():
    repo = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir, os.pardir))
    p = argparse.ArgumentParser(description="Genera el escenario ESCUELA (3D, Formato B)")
    p.add_argument("--out", default=os.path.join(repo, "scenarios", "escuela"))
    args = p.parse_args()
    out = args.out
    os.makedirs(out, exist_ok=True)

    write_csv(os.path.join(out, "WALLS.csv"), "x1, y1, z1, x2, y2, z2",
              [(x1, y1, z, x2, y2, z) for (x1, y1, x2, y2, z) in build_walls()])
    write_csv(os.path.join(out, "EXITS.csv"), "block_name, x1, y1, z1, x2, y2, z2", build_exits())
    write_csv(os.path.join(out, "GENERATORS.csv"), "block_name, x1, y1, z1, x2, y2, z2", build_generators())
    write_csv(os.path.join(out, "TARGETS.csv"),
              "block_name, figure_type, radius, x1, y1, z1, x2, y2, z2",
              [(b, "CIRCLE", r, x, y, z, x, y, z) for (b, r, x, y, z) in build_targets()])
    write_csv(os.path.join(out, "SERVERS.csv"), "block_name, x1, y1, z1, x2, y2, z2", build_servers())
    write_csv(os.path.join(out, "STAIRS.csv"),
              "block_name, x1, y1, z1, x2, y2, z2, width", build_stairs())
    with open(os.path.join(out, "parameters.json"), "w", encoding="utf-8") as f:
        json.dump(build_parameters(), f, indent=4, ensure_ascii=False)

    nwalls = len(build_walls())
    print(f"Escenario ESCUELA generado en {out}")
    print(f"  plantas={FLOORS}  paredes={nwalls}  aulas={len(build_targets())}  escaleras={len(build_stairs())}")


if __name__ == "__main__":
    main()
