"""Builder del escenario i — Vía pública con semáforo (G0).

Layout (40×40m):
- Esquina con dos calles cruzadas (10 m de ancho cada una).
- 8 segmentos de cordón en L con huecos de 3 m en cada esquina
  (sendas peatonales hacia el cruce, ver fix 24ad799 + doc
  DECISIONES_DRENAJE_2026-06-10.md §5).
- 4 SEMAFORO en los 4 corners interiores (server con cola corta).
- 4 SPAWN_{SW,SE,NW,NE} en las veredas (generators).
- 4 EXIT_{S,N,E,W} en los 4 bordes exteriores.
"""

from pathlib import Path

import ezdxf

OUTPUT_DIR = Path(__file__).resolve().parents[2] / "scenarios" / "i-via-publica"
OUTPUT_FILE = OUTPUT_DIR / "plano.dxf"

WALLS_LAYER = "WALLS"
EXITS_LAYER = "EXITS"
TARGETS_LAYER = "TARGETS"
GENERATORS_LAYER = "GENERATORS"
SERVERS_LAYER = "SERVERS"


def closed_rectangle(block, w, h):
    block.add_lwpolyline([(0, 0), (w, 0), (w, h), (0, h)], format="xy", close=True)


def build():
    doc = ezdxf.new(dxfversion="R2018", setup=True)
    for layer in (WALLS_LAYER, EXITS_LAYER, TARGETS_LAYER, GENERATORS_LAYER, SERVERS_LAYER):
        if layer not in doc.layers:
            doc.layers.add(layer)
    msp = doc.modelspace()

    # 8 segmentos de cordón con huecos de 3 m en cada esquina (senda peatonal).
    # Las calles ocupan x∈[15,25] (vertical) y y∈[15,25] (horizontal); las
    # paredes se acortan 3 m hacia adentro del cruce para dejar pasar al
    # peatón (donde espera el semáforo). Replica el WALLS.csv del fix
    # 24ad799 — si no, el peatón nace dentro de la manzana sin senda y
    # queda contra la pared (síntoma del bug original).
    walls = [
        # SW corner
        ((15, 0), (15, 12)),
        ((0, 15), (12, 15)),
        # SE corner
        ((25, 0), (25, 12)),
        ((28, 15), (40, 15)),
        # NW corner
        ((15, 28), (15, 40)),
        ((0, 25), (12, 25)),
        # NE corner
        ((25, 28), (25, 40)),
        ((28, 25), (40, 25)),
    ]
    for p1, p2 in walls:
        msp.add_line(p1, p2, dxfattribs={"layer": WALLS_LAYER})

    # 4 exits en los bordes exteriores (gaps "naturales" entre cordones).
    exit_h = doc.blocks.new("EXIT_H")
    exit_h.add_line((0, 0), (3, 0))
    msp.add_blockref("EXIT_H", insert=(10, 0), dxfattribs={"layer": EXITS_LAYER})    # EXIT_S
    msp.add_blockref("EXIT_H", insert=(27, 40), dxfattribs={"layer": EXITS_LAYER})   # EXIT_N
    exit_v = doc.blocks.new("EXIT_V")
    exit_v.add_line((0, 0), (0, 3))
    msp.add_blockref("EXIT_V", insert=(40, 10), dxfattribs={"layer": EXITS_LAYER})   # EXIT_E
    msp.add_blockref("EXIT_V", insert=(0, 27), dxfattribs={"layer": EXITS_LAYER})    # EXIT_W

    # 4 spawns (4×2m) en las veredas, cerca del cruce.
    spawn = doc.blocks.new("SPAWN")
    closed_rectangle(spawn, 4, 2)
    msp.add_blockref("SPAWN", insert=(10, 11), dxfattribs={"layer": GENERATORS_LAYER})  # SW
    msp.add_blockref("SPAWN", insert=(26, 11), dxfattribs={"layer": GENERATORS_LAYER})  # SE
    msp.add_blockref("SPAWN", insert=(10, 27), dxfattribs={"layer": GENERATORS_LAYER})  # NW
    msp.add_blockref("SPAWN", insert=(26, 27), dxfattribs={"layer": GENERATORS_LAYER})  # NE

    # 4 SEMAFORO (1×1m, cola corta hacia adentro de la vereda).
    # Las posiciones se eligieron para que cada queue quede a 0.3m del rect.
    # SW: rect (14,14)-(15,15), cola hacia el sur.
    semaforo_sw = doc.blocks.new("SEMAFORO_SW")
    closed_rectangle(semaforo_sw, 1, 1)
    semaforo_sw.add_lwpolyline([(0.5, -0.3), (0.5, -2.3)], format="xy")
    msp.add_blockref("SEMAFORO_SW", insert=(14, 14), dxfattribs={"layer": SERVERS_LAYER})

    # SE: rect (25,14)-(26,15), cola hacia el sur.
    semaforo_se = doc.blocks.new("SEMAFORO_SE")
    closed_rectangle(semaforo_se, 1, 1)
    semaforo_se.add_lwpolyline([(0.5, -0.3), (0.5, -2.3)], format="xy")
    msp.add_blockref("SEMAFORO_SE", insert=(25, 14), dxfattribs={"layer": SERVERS_LAYER})

    # NW: rect (14,25)-(15,26), cola hacia el norte.
    semaforo_nw = doc.blocks.new("SEMAFORO_NW")
    closed_rectangle(semaforo_nw, 1, 1)
    semaforo_nw.add_lwpolyline([(0.5, 1.3), (0.5, 3.3)], format="xy")
    msp.add_blockref("SEMAFORO_NW", insert=(14, 25), dxfattribs={"layer": SERVERS_LAYER})

    # NE: rect (25,25)-(26,26), cola hacia el norte.
    semaforo_ne = doc.blocks.new("SEMAFORO_NE")
    closed_rectangle(semaforo_ne, 1, 1)
    semaforo_ne.add_lwpolyline([(0.5, 1.3), (0.5, 3.3)], format="xy")
    msp.add_blockref("SEMAFORO_NE", insert=(25, 25), dxfattribs={"layer": SERVERS_LAYER})

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    doc.saveas(OUTPUT_FILE)
    print(f"Wrote {OUTPUT_FILE}")


if __name__ == "__main__":
    build()
