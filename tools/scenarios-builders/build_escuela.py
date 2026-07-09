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
    PB y P1; cada una es un SWITCHBACK con descanso (dos tramos en L unidos por
    un landing a z=1.5, ver ``_switchback_ends``/``build_stairs``/
    ``build_stair_walls`` y D19 en .claude/DECISIONES.md), con barandas que
    confinan cada tramo y el landing. La PB se comunica con el recreo por las
    dos esquinas del edificio.

Salidas/entradas: una en la PB del edificio (borde derecho) y otra en el recreo
(borde izquierdo). La P1 no tiene salida propia: se evacúa bajando por escaleras.

Genera (Formato B): WALLS/EXITS/GENERATORS/TARGETS/SERVERS/STAIRS .csv + parameters.json.

Uso:
    python tools/scenarios-builders/build_escuela.py            # -> scenarios/escuela
    python tools/scenarios-builders/build_escuela.py --out scenarios/escuela

CLI de barrido (para tools/sweep_run.py; --mode baseline es idéntico al uso
de siempre, --value se ignora en baseline):
    python tools/scenarios-builders/build_escuela.py --mode baseline --out DIR
    python tools/scenarios-builders/build_escuela.py --mode evacuacion --value 100 --out DIR  # N agentes ya adentro (Task 2)
    python tools/scenarios-builders/build_escuela.py --mode ingreso --value 5 --out DIR        # ventana de llegada en minutos, Nmax=120 fijo (Task 4)
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
DOOR = 2.4                          # ancho de puerta de aula (holgado: evita que el
                                    # agente se trabe en la jamba al girar hacia el pasillo)
OPEN = 6.0                          # ancho de aberturas (esquinas / salidas)

LEFT_ROOM_CX = sum(LEFT_ROOMS_X) / 2
RIGHT_ROOM_CX = sum(RIGHT_ROOMS_X) / 2

# ── Escaleras: switchback con descanso (Task 3, ver D19 en .claude/DECISIONES.md) ──
# Cada punta del edificio (SUR e_NORTE) aloja un switchback en L: dos tramos
# paralelos (A y B) que NO se solapan (separados por un gap central/median),
# unidos por un landing (piso de descanso) a mitad de altura, z = FLOOR_H/2 =
# 1.5. El agente sube el tramo A alejándose de la banda de aulas (BAND_Y),
# cruza el landing, y sube el tramo B volviendo hacia la banda de aulas ya
# en la planta siguiente. El core Java modela esto como DOS `Stairs` (D19):
# no hace falta tocar Java, sólo emitir la geometría correcta acá.
LANDING_Z = FLOOR_H / 2               # 1.5 — altura del piso de descanso
STAIR_HALF_WIDTH = 1.3                # medio ancho de cada tramo (ancho total 2.6 m). >=2.5 para
                                       # que enganche el bias de carril del CPM (contraflujo, D19):
                                       # el gate STAIR_LANE_MIN_WIDTH=2.5 sólo da dos carriles a
                                       # tramos anchos. Con 2.4 no activaba; 2.6 entra en el corredor
                                       # (2*2.6 + gap 0.6 = 5.8 <= ancho 6.0 de CORRIDOR_X).
STAIR_WIDTH = 2 * STAIR_HALF_WIDTH    # 2.6
STAIR_MEDIAN_GAP = 0.6                # separación libre entre las huellas de A y B
STAIR_PIE_MARGIN = 1.0                # distancia del pie/tope de cada tramo al borde de BAND_Y
STAIR_LANDING_MARGIN = 3.0            # distancia del pie/tope de cada tramo a la pared lejana
STAIR_LANDING_DEPTH = 2.5             # profundidad (eje y) del landing, del lado de la pared
                                       # lejana respecto de la línea de pies/topes. El landing NO
                                       # straddlea la línea de pies (si lo hiciera, las barandas de
                                       # los tramos —que arrancan ahí— tapiarían el cruce A->B).
STAIR_MOUTH_STUB = 0.4                # paredes que enmarcan la boca en el piso de llegada del tramo B
STAIR_SPEED_FACTOR = 0.38             # factor de velocidad en los tramos (calibración elemento 5:
                                       # con vd~1.4 m/s del modelo da v efectiva ~0.55 m/s medida)


def _switchback_ends() -> list[dict]:
    """Geometría de los dos switchbacks (SUR y NORTE), fuente única de verdad
    compartida por ``build_stairs`` y ``build_stair_walls``.

    Con las constantes de arriba, para este layout (``CORRIDOR_X=(42,48)``,
    ``BAND_Y=(8,52)``, ``END_BOTTOM=(0,8)``, ``END_TOP=(52,60)``, ``FLOOR_H=3``)
    da exactamente:

        SUR:   tramo A (43.5, 7.0, 0.0) -> (43.5, 2.5, 1.5)
               tramo B (46.5, 2.5, 1.5) -> (46.5, 7.0, 3.0)
               landing: x en [42.3, 47.7], y en [1.9, 3.1] (boca en y=3.1,
               tabique medio x en [44.7, 45.3])
        NORTE: tramo A (43.5, 53.0, 0.0) -> (43.5, 57.5, 1.5)
               tramo B (46.5, 57.5, 1.5) -> (46.5, 53.0, 3.0)
               landing: x en [42.3, 47.7], y en [56.9, 58.1] (boca en y=56.9,
               tabique medio x en [44.7, 45.3])

    Ambos tramos con ancho 2.4 m (``STAIR_WIDTH``), separados por un gap
    central de 0.6 m: sus huellas no se solapan.
    """
    cx = sum(CORRIDOR_X) / 2                                  # 45.0 (centro del pasillo)
    x_a = cx - STAIR_HALF_WIDTH - STAIR_MEDIAN_GAP / 2         # 43.5
    x_b = cx + STAIR_HALF_WIDTH + STAIR_MEDIAN_GAP / 2         # 46.5
    ends = []
    for name, band_edge, far_wall in (
        ("SUR", BAND_Y[0], END_BOTTOM[0]),      # banda en y=8, pared lejana y=0
        ("NORTE", BAND_Y[1], END_TOP[1]),       # banda en y=52, pared lejana y=60
    ):
        # sgn: signo de "band_edge -> far_wall" (hacia dónde queda la pared
        # lejana respecto de la banda de aulas). SUR: +1 (far < band).
        # NORTE: -1 (far > band).
        sgn = 1.0 if far_wall < band_edge else -1.0
        y_pie = band_edge - sgn * STAIR_PIE_MARGIN          # pie/tope, cerca de la banda
        y_landing = far_wall + sgn * STAIR_LANDING_MARGIN   # línea de pies/topes de A y B
        # El landing es una plataforma abierta que arranca EN la línea de
        # pies/topes (``mouth_y == y_landing``, donde abren las dos bocas) y se
        # extiende ``STAIR_LANDING_DEPTH`` hacia la pared lejana (``far_y``). Así
        # queda enteramente del lado opuesto a la banda de aulas, sin cruzarse
        # con las barandas de los tramos (que van de y_landing hacia y_pie) →
        # el agente gira libre en el landing para pasar del tramo A al tramo B.
        mouth_y = y_landing
        far_y = y_landing - sgn * STAIR_LANDING_DEPTH
        ends.append(dict(
            name=name, x_a=x_a, x_b=x_b, y_pie=y_pie, y_landing=y_landing,
            mouth_y=mouth_y, far_y=far_y,
            stub_y1=y_pie, stub_y2=y_pie + sgn * STAIR_MOUTH_STUB,
        ))
    return ends


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

    build_stair_walls(walls)
    return walls


def build_stair_walls(walls: list[tuple]) -> None:
    """Confinamiento del switchback (elemento 2 del contrato): barandas de
    cada tramo + perímetro del landing + marco de la boca de llegada del
    tramo B. Muta ``walls`` in-place (mismo estilo que el resto del builder).

    Convención de plantas (D19 en .claude/DECISIONES.md, "no cambiar"):
      - Barandas del tramo A (huella z=0..1.5): tag z=0 -> obstáculo de la
        grilla de piso0 y quedan en ``stairWalls`` del tramo A (piso0 ∪ landing).
      - Barandas del tramo B (huella z=1.5..3): tag z=1.5 -> obstáculo de la
        grilla del landing y quedan en ``stairWalls`` del tramo B (landing ∪ piso1).
      - Perímetro del landing: tag z=1.5, con huecos exactos en las dos bocas
        (sólo el tabique medio, entre las huellas de A y B, queda sólido).
      - Marco de la boca de llegada del tramo B: tag z=FLOOR_H (piso1/piso3),
        NO encierra el tope (deja el paso libre hacia el pasillo/banda de aulas).
        (La boca de partida del tramo A en piso0 ya queda enmarcada por sus
        propias barandas, que están tageadas z=0 = la misma z de piso0.)
    """
    for e in _switchback_ends():
        xa, xb = e["x_a"], e["x_b"]
        y_lo, y_hi = sorted((e["y_pie"], e["y_landing"]))

        # Barandas tramo A (z=0): borde exterior (xa-half) y borde del median (xa+half).
        vseg(walls, xa - STAIR_HALF_WIDTH, y_lo, y_hi, 0.0)
        vseg(walls, xa + STAIR_HALF_WIDTH, y_lo, y_hi, 0.0)
        # Las mismas barandas del tramo A, además, tageadas a z=1.5: en el piso de
        # descanso (landing floor) confinan la huella del tramo A para que un agente
        # que llega arriba (z=1.5) NO se escape hacia el norte por encima del tramo
        # (sin estas paredes la grilla del landing deja abierto el pasillo del tramo A
        # a z=1.5). Simétricas a las del tramo B. No afectan al agente que baja el
        # tramo A (z<1.5 ahí) porque su anti-tunneling ya incluye estas paredes.
        vseg(walls, xa - STAIR_HALF_WIDTH, y_lo, y_hi, LANDING_Z)
        vseg(walls, xa + STAIR_HALF_WIDTH, y_lo, y_hi, LANDING_Z)
        # Barandas tramo B (z=1.5): borde del median (xb-half) y borde exterior (xb+half).
        vseg(walls, xb - STAIR_HALF_WIDTH, y_lo, y_hi, LANDING_Z)
        vseg(walls, xb + STAIR_HALF_WIDTH, y_lo, y_hi, LANDING_Z)

        # Perímetro del landing (z=1.5): lados oeste/este compactos + pared
        # lejana sólida + tabique medio en la boca (deja las dos bocas abiertas).
        land_lo, land_hi = sorted((e["far_y"], e["mouth_y"]))
        vseg(walls, xa - STAIR_HALF_WIDTH, land_lo, land_hi, LANDING_Z)   # lado oeste
        vseg(walls, xb + STAIR_HALF_WIDTH, land_lo, land_hi, LANDING_Z)   # lado este
        hseg(walls, e["far_y"], xa - STAIR_HALF_WIDTH, xb + STAIR_HALF_WIDTH, LANDING_Z)  # pared lejana (sólida)
        hseg(walls, e["mouth_y"], xa + STAIR_HALF_WIDTH, xb - STAIR_HALF_WIDTH, LANDING_Z)  # tabique medio (boca)

        # Marco de la boca de llegada del tramo B en su piso de destino
        # (z=FLOOR_H): dos paredes cortas laterales, el punto de llegada
        # (xb, y_pie) queda libre entre ellas -> accesible desde el pasillo.
        stub_lo, stub_hi = sorted((e["stub_y1"], e["stub_y2"]))
        vseg(walls, xb - STAIR_HALF_WIDTH, stub_lo, stub_hi, FLOOR_H)
        vseg(walls, xb + STAIR_HALF_WIDTH, stub_lo, stub_hi, FLOOR_H)


def build_stairs() -> list[tuple]:
    """Switchback con descanso en cada punta (SUR y NORTE): dos filas por
    punta (tramo A: piso0 -> landing; tramo B: landing -> piso1), unidas por
    el landing a z=LANDING_Z=1.5. Ver docstring de ``_switchback_ends`` para
    las coordenadas exactas. ``speed_factor=STAIR_SPEED_FACTOR`` calibra la
    velocidad efectiva en la escalera (elemento 5 del contrato)."""
    rows = []
    for e in _switchback_ends():
        rows.append((
            f"ESC_{e['name']}_A", e["x_a"], e["y_pie"], 0.0,
            e["x_a"], e["y_landing"], LANDING_Z, STAIR_WIDTH, STAIR_SPEED_FACTOR,
        ))
        rows.append((
            f"ESC_{e['name']}_B", e["x_b"], e["y_landing"], LANDING_Z,
            e["x_b"], e["y_pie"], FLOOR_H, STAIR_WIDTH, STAIR_SPEED_FACTOR,
        ))
    return rows


AULA_MARGIN = 1.0                   # inset del recinto del aula respecto de sus paredes


def build_aula_rooms() -> list[tuple]:
    """Recinto (rectángulo) de cada aula: (base, id, x0, y0, x1, y1, z).

    8 aulas por planta (4 bandas × {izquierda, derecha}); base ``AULA_PB`` en
    PB (z=0) y ``AULA_P1`` en P1 (z=3). El rectángulo va inset ``AULA_MARGIN``
    de las paredes del cuarto para que los alumnos se acomoden adentro."""
    rooms = []
    m = AULA_MARGIN
    for z in FLOORS:
        base = "AULA_PB" if abs(z) < 1e-9 else "AULA_P1"
        n = 0
        for (y0, y1) in room_bounds():
            for (rx0, rx1) in (LEFT_ROOMS_X, RIGHT_ROOMS_X):
                n += 1
                rooms.append((base, n, rx0 + m, y0 + m, rx1 - m, y1 - m, z))
    return rooms


def build_targets() -> list[tuple]:
    # Las aulas ya NO son TARGETs (capacidad-1): pasaron a servers CLASSROOM
    # (recinto colectivo con timbre; ver build_servers / build_parameters). El
    # baseline no tiene targets puntuales.
    return []


def build_servers() -> list[tuple]:
    # Aulas como servers CLASSROOM: un rectángulo por aula, SIN filas _QUEUE
    # (la ausencia de cola + type CLASSROOM en el JSON las hace "recinto con
    # sesión"). 8 en PB (base AULA_PB, z=0) y 8 en P1 (base AULA_P1, z=3).
    rows = [
        (f"{base}_{n}_SERVER", x0, y0, z, x1, y1, z)
        for (base, n, x0, y0, x1, y1, z) in build_aula_rooms()
    ]
    # Kiosco en el recreo (server QUEUE + línea de cola); queda fuera del plan
    # baseline (se usará en el sub-escenario de Ingreso/recreo).
    rows.append(("KIOSCO_1_SERVER", 5.0, 50.0, 0.0, 8.0, 52.0, 0.0))
    rows.append(("KIOSCO_1_QUEUE000", 5.0, 49.5, 0.0, 5.0, 44.5, 0.0))
    return rows


def build_exits() -> list[tuple]:
    return [
        ("RECREO", 0.0, 27.0, 0.0, 0.0, 27.0 + OPEN, 0.0),
        ("EDIFICIO_PB", 60.0, 2.0, 0.0, 60.0, 6.0, 0.0),
    ]


EVAC_ZONE_MARGIN = 0.3       # inset extra de la zona EVAC_i respecto del recinto
                              # del aula (que ya trae AULA_MARGIN de las paredes)


def build_generators(mode: str = "baseline", value: float | None = None) -> list[tuple]:
    """Zonas de spawn (GENERATORS.csv), mode-aware.

    - ``baseline`` (default): las 2 entradas de siempre (recreo + edificio
      PB). ``value`` se ignora.
    - ``evacuacion``: 16 zonas ``EVAC_1``..``EVAC_16``, una por aula (interior
      del recinto de ``build_aula_rooms``, con un inset extra
      ``EVAC_ZONE_MARGIN``), en la planta de esa aula (z=0 las 8 de PB, z=3
      las 8 de P1). ``value`` no afecta la geometría de las zonas — sólo
      cuántos agentes se reparten en cada una (ver ``_evac_room_counts`` en
      ``build_parameters``); se acepta el parámetro por uniformidad de firma
      con el resto de los builders mode-aware.
    - ``ingreso``: zonas DEDICADAS y grandes (NO las del baseline, que son
      chicas). Ver ``INGRESO_ZONES`` y su docstring: el sub-escenario Ingreso
      necesita meter ``INGRESO_NMAX=120`` alumnos en ventanas de 1/5/10 min, y
      con las zonas chicas del baseline el tope de densidad de puerta del
      generador (``MAX_PEOPLE_PER_METER=3 p/min/m`` en
      ``ConfigurablePedestrianGenerator``) recorta el caudal del caso de 1 min
      a ~30 agentes (D20). Zonas más anchas suben ese tope. ``value`` (ventana
      en minutos) no afecta la geometría; se acepta por uniformidad de firma.
    """
    if mode == "baseline":
        return [
            ("INGRESO_RECREO", 2.0, 27.0, 0.0, 5.0, 33.0, 0.0),
            ("INGRESO_EDIFICIO", 55.0, 2.0, 0.0, 58.0, 6.0, 0.0),
        ]
    if mode == "evacuacion":
        m = EVAC_ZONE_MARGIN
        return [
            (f"EVAC_{i}", x0 + m, y0 + m, z, x1 - m, y1 - m, z)
            for i, (base, n, x0, y0, x1, y1, z) in enumerate(build_aula_rooms(), start=1)
        ]
    if mode == "ingreso":
        return list(INGRESO_ZONES)
    raise ValueError(f"mode desconocido para build_generators: {mode!r}")


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


# ── Parámetros de la simulación baseline (día escolar) ───────────────────────
# Ventana de llegada FINITA: los alumnos ingresan durante los primeros
# ARRIVAL_WINDOW segundos y después el generador se apaga (inactive_time enorme
# = un único ciclo activo, no un goteo perpetuo). Así el edificio se llena y
# luego se vacía, mostrando el día completo (ingreso → clase → evacuación).
#
# Nota de semántica del generador (ver ConfigurablePedestrianGenerator): el par
# (active_time, inactive_time) define un CICLO que se repite; con
# inactive_time=0 el generador RE-ARRANCA indefinidamente (nunca deja de
# spawnear). Para un único burst finito se pone inactive_time >> max_time.
ARRIVAL_WINDOW = 60.0        # s de ingreso continuo (luego el generador se apaga)
NEVER_REACTIVATE = 1.0e6     # inactive_time centinela: no vuelve a generar
MAX_TIME = 250.0             # s totales (deja vaciar el edificio tras el timbre)
GEN_PERIOD = 3.0             # cada cuánto llega un lote a cada entrada
GEN_QTY = (1.0, 2.0)         # tamaño del lote (uniforme) → caudal ≈ 30 p/min/entrada
# Aulas = servers CLASSROOM con UNA sesión (limitación de Formato B): liberan a
# TODOS los alumnos de una en t_init + CLASS_SESSION (el "timbre"). Como el aula
# es el primer paso del plan, los alumnos se delegan al spawnear (dentro de la
# ventana de ingreso), así que con el timbre DESPUÉS de esa ventana ninguno
# queda atrapado. Modelo: llegan → clase → timbre → todos evacúan.
CLASS_START = 0.0            # t_init de la sesión de clase
CLASS_SESSION = 140.0        # duración → timbre (dismissal) en CLASS_START + CLASS_SESSION


def _classroom(block_name: str) -> dict:
    # Aula como server CLASSROOM: type EXPLÍCITO (no depender de la inferencia),
    # UNA sola sesión (start_time = CLASS_START, duración = CLASS_SESSION →
    # dismissal en su suma). attending_time_distribution determinística porque
    # el CLASSROOM no muestrea: usa el valor representativo como t_mean.
    return {
        "block_name": block_name,
        "type": "CLASSROOM",
        "attending_time_distribution": {"type": "UNIFORM", "min": CLASS_SESSION, "max": CLASS_SESSION},
        "start_time": CLASS_START,
        "max_capacity": 40,
    }


def build_parameters(mode: str = "baseline", value: float | None = None) -> dict:
    """Despacha la construcción de ``parameters.json`` según el sub-escenario.

    - ``baseline`` (default): día escolar completo (ingreso → clase → timbre →
      evacuación). ``value`` se IGNORA. Comportamiento IDÉNTICO al builder
      original (sin barrido).
    - ``evacuacion`` (Task 2 del plan de entrega): ``value`` es la capacidad
      total N de agentes ya adentro (repartidos en las 16 aulas de ambas
      plantas, ver ``_evac_room_counts``); parten ya sentados y evacúan
      directo a una salida (plan ``EVACUAR``, sin aula/clase intermedia).
    - ``ingreso`` (Task 4 del plan de entrega): ``value`` es la ventana de
      llegada en MINUTOS (1, 5 o 10); ``INGRESO_NMAX=120`` alumnos entran en
      esa ventana por las 2 puertas de siempre, algunos pasando antes por el
      kiosco del recreo (ver ``_build_parameters_ingreso``).

    ``baseline``, ``evacuacion`` e ``ingreso`` están implementados.
    """
    if mode == "baseline":
        return _build_parameters_baseline()
    if mode == "evacuacion":
        if value is None:
            raise ValueError(
                "build_parameters(mode='evacuacion') requiere --value N "
                "(capacidad total de agentes ya adentro)"
            )
        n_agents = int(round(value))
        if n_agents < 0:
            raise ValueError(f"value (N) debe ser >= 0, recibido {value!r}")
        return _build_parameters_evacuacion(n_agents)
    if mode == "ingreso":
        if value is None:
            raise ValueError(
                "build_parameters(mode='ingreso') requiere --value <minutos> "
                "(ventana de llegada: 1, 5 o 10)"
            )
        window_min = value
        if window_min <= 0:
            raise ValueError(f"value (ventana en minutos) debe ser > 0, recibido {value!r}")
        return _build_parameters_ingreso(window_min)
    raise ValueError(f"mode desconocido: {mode!r} (esperado: baseline, evacuacion, ingreso)")


def _evac_room_counts(n_rooms: int, total: int) -> list[int]:
    """Reparte ``total`` agentes entre ``n_rooms`` lo más parejo posible.

    Los primeros ``total % n_rooms`` reciben ``ceil(total/n_rooms)``, el
    resto ``floor(total/n_rooms)``. La suma da EXACTO ``total``."""
    base, extra = divmod(total, n_rooms)
    return [base + 1] * extra + [base] * (n_rooms - extra)


EVAC_MAX_TIME = 400.0        # s totales: amplio para que alcancen a evacuar del todo
EVAC_GEN_ACTIVE_TIME = 1.0   # activeTime del generador BATCH (irrelevante para
                              # instant_occupation, que coloca todo en t=0 vía
                              # spawnInitial; sólo debe ser > 0 por validación)


def _build_parameters_evacuacion(n_agents: int) -> dict:
    """Sub-escenario Evacuación: ``n_agents`` alumnos ya sentados en las 16
    aulas (repartidos ~parejo entre PB y P1) que evacúan directo a una salida
    al iniciar la simulación (t=0), sin pasar por clase.

    Cada aula es una zona ``EVAC_i`` con generador ``instant_occupation``
    (coloca su lote completo en t=0, ver ``ConfigurablePedestrianGenerator
    .spawnInitial``) y ``quantity_distribution`` UNIFORM con min=max=c_i (el
    JSON de Formato B no soporta el tipo ``DETERMINISTIC`` — sólo
    ``UNIFORM``/``GAUSSIAN``, ver ``DistributionResolver``; UNIFORM con
    min==max siempre samplea ese valor exacto, mismo truco que ``_classroom``
    usa para ``attending_time_distribution``). El plan ``EVACUAR`` no tiene
    aula/clase (ya están adentro): sólo ``exit_selection`` sin
    ``objective_groups`` (una salida al azar, ver ``FormatBLoader
    .buildPlanTemplates`` — el paso EXIT final se agrega siempre, aún con
    ``objective_groups: []``)."""
    zones = build_generators("evacuacion")   # 16 (block_name, x0,y0,z, x1,y1,z)
    counts = _evac_room_counts(len(zones), n_agents)

    gen_agents = {
        "min_radius_distribution": {"type": "UNIFORM", "min": 0.15, "max": 0.15},
        "max_radius_distribution": {"type": "UNIFORM", "min": 0.30, "max": 0.32},
        # Modo crisis (D22): en evacuación los agentes caminan con vd de
        # emergencia (2.0 m/s > 1.55 del perfil normal). App deriva de este
        # max_velocity un AgentProfile propio (vd=ve=2.0) para estas zonas.
        "max_velocity": 2.0,
    }

    generators = []
    for (block_name, _x0, _y0, _z0, _x1, _y1, _z1), c in zip(zones, counts):
        generators.append({
            "block_name": block_name,
            "plan": "EVACUAR",
            "mode": "instant_occupation",
            "agents": gen_agents,
            "active_time": EVAC_GEN_ACTIVE_TIME,
            "inactive_time": NEVER_REACTIVATE,
            "generation": {
                "period": 60.0,
                "quantity_distribution": {"type": "UNIFORM", "min": float(c), "max": float(c)},
            },
        })

    # D25: el tiempo total escala con N para capacidades grandes (post-D24 el
    # simulador sostiene evacuaciones completas hasta N~500). Para N<=120 es
    # EXACTAMENTE EVAC_MAX_TIME=400 s (los puntos históricos no cambian); por
    # encima crece con margen ~2x sobre el tiempo de vaciado medido.
    max_time = max(EVAC_MAX_TIME, EVAC_MAX_TIME + (n_agents - 120) * 1.2)
    return {
        "max_time": max_time,
        "output_delta_time": 0.2,
        "blueprint_name": "escuela_evacuacion",
        "agents_generators": generators,
        "targets": [],
        # Los servers de aula/kiosco quedan declarados (geometría/servers.csv
        # comparte block_names con el baseline) pero ningún plan los
        # referencia: inocuo, no rompen nada.
        "servers": [
            _classroom("AULA_PB"),
            _classroom("AULA_P1"),
            {"block_name": "KIOSCO",
             "attending_time_distribution": {"type": "GAUSSIAN", "mean": 8.0, "std": 2.0},
             "max_capacity": 1},
        ],
        "plans": [
            {"name": "EVACUAR", "exit_selection": "RANDOM", "objective_groups": []},
        ],
    }


def _build_parameters_baseline() -> dict:
    # Baseline (día escolar, 1 período): los alumnos entran por ambas entradas
    # durante la ventana de llegada. Según el plan que les toca (CLASE_PB o
    # CLASE_P1, ~50/50), van a un aula de PB o de P1 (estas últimas subiendo por
    # una escalera), asisten la clase (server CLASSROOM), y al TIMBRE (dismissal
    # sincronizado) todos evacúan por una salida al azar. Los dos planes
    # diferenciados reparten la población entre plantas SIN el sesgo que producía
    # el modelo viejo de aulas capacidad-1. El kiosco (server QUEUE) queda en la
    # geometría del recreo pero fuera del plan baseline (sub-escenario Ingreso).
    gen_agents = {
        "min_radius_distribution": {"type": "UNIFORM", "min": 0.15, "max": 0.15},
        "max_radius_distribution": {"type": "UNIFORM", "min": 0.30, "max": 0.32},
        # 1.55 = vd del perfil default (baglietoParisiSet1). Desde D22 App honra
        # max_velocity; se alinea al default para no cambiar el baseline validado.
        "max_velocity": 1.55,
    }
    generation = {"period": GEN_PERIOD,
                  "quantity_distribution": {"type": "UNIFORM", "min": GEN_QTY[0], "max": GEN_QTY[1]}}
    # Pool de planes por generador: el ConfigurablePedestrianGenerator elige uno
    # al azar por agente entre los separados por '|' → ~50/50 PB/P1.
    generators = [
        {"block_name": b, "plan": "CLASE_PB|CLASE_P1", "agents": gen_agents,
         "active_time": ARRIVAL_WINDOW, "inactive_time": NEVER_REACTIVATE, "generation": generation}
        for b in ("INGRESO_RECREO", "INGRESO_EDIFICIO")
    ]

    def clase_plan(name: str, aula_group: str) -> dict:
        # Un aula (server del grupo) y después una salida al azar (exit_selection).
        return {
            "name": name,
            "exit_selection": "RANDOM",
            "objective_groups": [
                {"block_name": aula_group, "layer": "SERVERS", "objective_selection": "RANDOM"},
            ],
        }

    return {
        "max_time": MAX_TIME,
        "output_delta_time": 0.2,
        "blueprint_name": "escuela",
        "agents_generators": generators,
        "targets": [],
        "servers": [
            _classroom("AULA_PB"),
            _classroom("AULA_P1"),
            {"block_name": "KIOSCO",
             "attending_time_distribution": {"type": "GAUSSIAN", "mean": 8.0, "std": 2.0},
             "max_capacity": 1},
        ],
        "plans": [
            clase_plan("CLASE_PB", "AULA_PB"),
            clase_plan("CLASE_P1", "AULA_P1"),
        ],
    }


# ── Parámetros del sub-escenario Ingreso (Task 4) ─────────────────────────────
# A diferencia del baseline (un burst que llena el edificio y después lo vacía
# con el timbre) acá el foco es la LLEGADA: cuánto tarda en congestionarse la
# zona previa a la escalera principal según el caudal (Nmax=120 alumnos
# repartidos en 1/5/10 minutos). No hay timbre dentro de la ventana simulada:
# las aulas sólo reciben, no sueltan.
INGRESO_NMAX = 120                # total de alumnos que ingresan, FIJO en los
                                   # 3 puntos del barrido (1/5/10 min)
#
# Semántica del caudal y por qué 3 zonas dedicadas (D20).
# El generador (``ConfigurablePedestrianGenerator``, modo CALM) convierte el
# par (period, quantity) del JSON en un caudal p/min = quantity/period*60 y lo
# RECORTA al tope de densidad de puerta MAX_PEOPLE_PER_METER = 3 p/min por metro
# de ancho de zona (``doorWidth = max(ancho, alto)`` del rectángulo). Con las 2
# zonas chicas del baseline (3x6 y 3x4 => doorWidth 6 y 4 => topes 18 y 12
# p/min) el caso de 1 min (que necesita 120 p/min) quedaba recortado a ~30
# agentes: el barrido salía INVERTIDO (más caudal => menos agentes). Se usan 3
# zonas DEDICADAS más anchas, dimensionadas para que su tope de densidad supere
# el caudal pedido en la ventana más corta (1 min), y con la suma de sus cupos =
# INGRESO_NMAX. Por generador el cupo por ciclo es round(quantity/period * W),
# así que fijando period = quantity * W / cupo se obtiene el cupo exacto para
# CUALQUIER ventana W.
#   - RECREO (kiosco): cupo 60. Rect 10x30 => doorWidth 30 => tope 90 p/min >= 60
#     (caudal de 1 min). period = 2*W/60 = W/30.
#   - EDIF_SUR y EDIF_NORTE (directo): cupo 30 c/u. Rect 12x7 => doorWidth 12 =>
#     tope 36 p/min >= 30 (caudal de 1 min). period = 2*W/30 = W/15.
#   Total 60+30+30 = 120 en las 3 ventanas.
INGRESO_GEN_QTY = 2.0             # lote FIJO por tick (UNIFORM min==max, mismo
                                   # truco "determinístico" del resto del archivo)
# Zonas de entrada dedicadas: (block_name, rect (x0,y0,x1,y1) en z=0, plan pool,
# cupo de agentes por ventana). El kiosco está en el recreo => sólo el que entra
# por el recreo pasa por el kiosco; los dos accesos del edificio (punta SUR y
# punta NORTE, las zonas abiertas de planta baja a cada extremo del pasillo) van
# directo al aula. ~50/50 PB/P1 en cada acceso vía el pool de planes.
INGRESO_ENTRADAS = [
    {"block_name": "INGRESO_RECREO",     "rect": (3.0, 12.0, 13.0, 42.0),
     "plan": "ING_KIOSCO_PB|ING_KIOSCO_P1", "cupo": 60},
    {"block_name": "INGRESO_EDIF_SUR",   "rect": (48.0, 0.5, 60.0, 7.5),
     "plan": "ING_DIR_PB|ING_DIR_P1",       "cupo": 30},
    {"block_name": "INGRESO_EDIF_NORTE", "rect": (48.0, 52.5, 60.0, 59.5),
     "plan": "ING_DIR_PB|ING_DIR_P1",       "cupo": 30},
]
# Vista geométrica (para GENERATORS.csv): (block_name, x0,y0,z, x1,y1,z).
INGRESO_ZONES = [
    (e["block_name"], e["rect"][0], e["rect"][1], 0.0, e["rect"][2], e["rect"][3], 0.0)
    for e in INGRESO_ENTRADAS
]
INGRESO_MAX_TIME_MARGIN = 250.0   # margen tras la ventana de llegada para que
                                   # la última tanda cruce la zona observada
                                   # (antes de la escalera) y, si le tocó,
                                   # también el kiosco
INGRESO_KIOSCO_CAPACITY = 30      # el kiosco NO debe ser el cuello de botella:
                                   # el observable de este sub-escenario es la
                                   # escalera, no la cola del kiosco
INGRESO_KIOSCO_MEAN = 4.0         # servicio corto: un paso de recreo fluido,
INGRESO_KIOSCO_STD = 1.0          # no un trámite (baseline/evacuación usan
                                   # capacidad 1 y mean=8, pensado para otra cosa)


def _classroom_ingreso(block_name: str, max_time: float) -> dict:
    """Aula CLASSROOM para Ingreso: acá interesa la LLEGADA, no la salida por
    timbre (a diferencia de ``_classroom``, atado a las constantes del
    baseline con dismissal en t=140). El dismissal se fija DESPUÉS de
    ``max_time`` (sesión = max_time + margen) para que, dentro de toda la
    ventana simulada, el aula sólo absorba alumnos y ninguno sea expulsado de
    vuelta a la zona observada por un timbre que no viene al caso acá."""
    session = max_time + 50.0   # dismissal en start_time+session > max_time: no se dispara
    return {
        "block_name": block_name,
        "type": "CLASSROOM",
        "attending_time_distribution": {"type": "UNIFORM", "min": session, "max": session},
        "start_time": 0.0,
        "max_capacity": 40,
    }


def _build_parameters_ingreso(window_min: float) -> dict:
    """Sub-escenario Ingreso: ``INGRESO_NMAX`` alumnos llegan durante una
    ventana de ``window_min`` minutos por 3 accesos dedicados (ver
    ``INGRESO_ENTRADAS``), ~50/50 hacia PB/P1 en cada uno. El kiosco está en el
    recreo, así que sólo el que entra por el recreo pasa antes por él (realista:
    el que entra directo por el edificio no da la vuelta a buscarlo):

    - ``INGRESO_RECREO``     -> pool ``"ING_KIOSCO_PB|ING_KIOSCO_P1"`` (kiosco -> aula), cupo 60
    - ``INGRESO_EDIF_SUR``   -> pool ``"ING_DIR_PB|ING_DIR_P1"``       (directo a aula),  cupo 30
    - ``INGRESO_EDIF_NORTE`` -> pool ``"ING_DIR_PB|ING_DIR_P1"``       (directo a aula),  cupo 30

    El caudal se calibra POR acceso para que su cupo salga EXACTO en cualquier
    ventana ``W``: el cupo por ciclo del generador es ``round(quantity/period *
    W)``, así que ``period = quantity * W / cupo`` da ese cupo. Las zonas son lo
    bastante anchas para que el tope de densidad de puerta del generador
    (3 p/min/m) NO recorte el caudal ni siquiera en la ventana de 1 min (ver
    ``INGRESO_ENTRADAS`` / D20). Suma de cupos = ``INGRESO_NMAX`` = 120."""
    w = window_min * 60.0        # ventana de llegada, en segundos
    max_time = w + INGRESO_MAX_TIME_MARGIN

    gen_agents = {
        "min_radius_distribution": {"type": "UNIFORM", "min": 0.15, "max": 0.15},
        "max_radius_distribution": {"type": "UNIFORM", "min": 0.30, "max": 0.32},
        # Ingreso NO es crisis: caminata normal (1.55 = vd del perfil default;
        # ver D22 — App honra max_velocity desde entonces).
        "max_velocity": 1.55,
    }
    generators = []
    for e in INGRESO_ENTRADAS:
        # period tal que round(quantity/period * W) == cupo, para toda ventana W.
        period = INGRESO_GEN_QTY * w / e["cupo"]
        generators.append({
            "block_name": e["block_name"],
            "plan": e["plan"],
            "agents": gen_agents,
            "active_time": w,
            "inactive_time": NEVER_REACTIVATE,
            "generation": {
                "period": period,
                "quantity_distribution": {"type": "UNIFORM",
                                           "min": INGRESO_GEN_QTY, "max": INGRESO_GEN_QTY},
            },
        })

    def plan_con_kiosco(name: str, aula_group: str) -> dict:
        # KIOSCO (única instancia -> CLOSEST) y después el aula (varias
        # instancias del grupo -> RANDOM, igual criterio que clase_plan).
        return {
            "name": name,
            "exit_selection": "RANDOM",
            "objective_groups": [
                {"block_name": "KIOSCO", "layer": "SERVERS", "objective_selection": "CLOSEST"},
                {"block_name": aula_group, "layer": "SERVERS", "objective_selection": "RANDOM"},
            ],
        }

    def plan_directo(name: str, aula_group: str) -> dict:
        return {
            "name": name,
            "exit_selection": "RANDOM",
            "objective_groups": [
                {"block_name": aula_group, "layer": "SERVERS", "objective_selection": "RANDOM"},
            ],
        }

    return {
        "max_time": max_time,
        "output_delta_time": 0.2,
        "blueprint_name": "escuela_ingreso",
        "agents_generators": generators,
        "targets": [],
        "servers": [
            _classroom_ingreso("AULA_PB", max_time),
            _classroom_ingreso("AULA_P1", max_time),
            {"block_name": "KIOSCO",
             "attending_time_distribution": {"type": "GAUSSIAN", "mean": INGRESO_KIOSCO_MEAN,
                                              "std": INGRESO_KIOSCO_STD},
             "max_capacity": INGRESO_KIOSCO_CAPACITY},
        ],
        "plans": [
            plan_con_kiosco("ING_KIOSCO_PB", "AULA_PB"),
            plan_con_kiosco("ING_KIOSCO_P1", "AULA_P1"),
            plan_directo("ING_DIR_PB", "AULA_PB"),
            plan_directo("ING_DIR_P1", "AULA_P1"),
        ],
    }


def main():
    repo = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir, os.pardir))
    p = argparse.ArgumentParser(description="Genera el escenario ESCUELA (3D, Formato B)")
    p.add_argument("--out", default=os.path.join(repo, "scenarios", "escuela"))
    p.add_argument("--mode", choices=["baseline", "evacuacion", "ingreso"], default="baseline",
                   help="sub-escenario a generar. 'baseline' (día escolar completo), "
                        "'evacuacion' (Task 2) y 'ingreso' (Task 4) implementados.")
    p.add_argument("--value", type=float, default=None,
                   help="input numérico del barrido (capacidad N para 'evacuacion', "
                        "ventana de llegada en minutos para 'ingreso'). Ignorado en 'baseline'.")
    args = p.parse_args()
    out = args.out

    # Se calcula ANTES de escribir ningún CSV: si el mode requiere --value y no
    # se lo pasaron, falla rápido (ValueError) sin dejar un directorio de
    # escenario a medio escribir.
    parameters = build_parameters(args.mode, args.value)

    os.makedirs(out, exist_ok=True)

    write_csv(os.path.join(out, "WALLS.csv"), "x1, y1, z1, x2, y2, z2",
              [(x1, y1, z, x2, y2, z) for (x1, y1, x2, y2, z) in build_walls()])
    write_csv(os.path.join(out, "EXITS.csv"), "block_name, x1, y1, z1, x2, y2, z2", build_exits())
    write_csv(os.path.join(out, "GENERATORS.csv"), "block_name, x1, y1, z1, x2, y2, z2",
              build_generators(args.mode, args.value))
    write_csv(os.path.join(out, "TARGETS.csv"),
              "block_name, figure_type, radius, x1, y1, z1, x2, y2, z2",
              [(b, "CIRCLE", r, x, y, z, x, y, z) for (b, r, x, y, z) in build_targets()])
    write_csv(os.path.join(out, "SERVERS.csv"), "block_name, x1, y1, z1, x2, y2, z2", build_servers())
    write_csv(os.path.join(out, "STAIRS.csv"),
              "block_name, x1, y1, z1, x2, y2, z2, width, speed_factor", build_stairs())
    with open(os.path.join(out, "parameters.json"), "w", encoding="utf-8") as f:
        json.dump(parameters, f, indent=4, ensure_ascii=False)

    nwalls = len(build_walls())
    print(f"Escenario ESCUELA generado en {out}")
    print(f"  plantas={FLOORS}  paredes={nwalls}  aulas={len(build_targets())}  escaleras={len(build_stairs())}")


if __name__ == "__main__":
    main()
