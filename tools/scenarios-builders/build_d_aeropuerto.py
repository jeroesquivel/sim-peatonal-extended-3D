"""Builder del escenario d — Aeropuerto (G5).

Layout (50×40m):
- Hall principal con flujo oeste→este.
- INGRESO_AERO al oeste.
- 3 CHECKIN en segunda columna (x=15).
- 2 RX en tercera columna (x=25).
- SALA_EMBARQUE (rect grande) al este.
- KIOSCO_AERO al norte-oeste (opcional).
- AVION exit al este.
- Wall divisor en x=30 con gap para flujo hacia la sala.
"""

from pathlib import Path

import ezdxf

OUTPUT_DIR = Path(__file__).resolve().parents[2] / "scenarios" / "d-aeropuerto"
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

    walls = [
        ((0, 0), (50, 0)),     # sur
        ((50, 0), (50, 18)),   # este inferior
        ((50, 21), (50, 40)),  # este superior (gap AVION en y=18-21)
        ((50, 40), (0, 40)),   # norte
        ((0, 40), (0, 0)),     # oeste
        ((30, 0), (30, 14)),   # divisor hall/sala — abajo
        ((30, 22), (30, 40)),  # divisor hall/sala — arriba (gap y=14-22 hacia sala)
    ]
    for p1, p2 in walls:
        msp.add_line(p1, p2, dxfattribs={"layer": WALLS_LAYER})

    avion = doc.blocks.new("AVION")
    avion.add_line((0, 0), (0, 3))
    msp.add_blockref("AVION", insert=(50, 18), dxfattribs={"layer": EXITS_LAYER})

    ingreso = doc.blocks.new("INGRESO_AERO")
    closed_rectangle(ingreso, 4, 3)
    msp.add_blockref("INGRESO_AERO", insert=(1, 18), dxfattribs={"layer": GENERATORS_LAYER})

    kiosco = doc.blocks.new("KIOSCO_AERO")
    closed_rectangle(kiosco, 3, 3)
    msp.add_blockref("KIOSCO_AERO", insert=(3, 33), dxfattribs={"layer": TARGETS_LAYER})

    sala = doc.blocks.new("SALA_EMBARQUE")
    closed_rectangle(sala, 12, 8)
    msp.add_blockref("SALA_EMBARQUE", insert=(32, 14), dxfattribs={"layer": TARGETS_LAYER})

    # CHECKIN × 3 (rect 1×1.5, cola 5m al oeste a 0.3m).
    checkin = doc.blocks.new("CHECKIN")
    closed_rectangle(checkin, 1, 1.5)
    checkin.add_lwpolyline([(-0.3, 0.75), (-5.3, 0.75)], format="xy")
    for pos in [(15, 10), (15, 18), (15, 26)]:
        msp.add_blockref("CHECKIN", insert=pos, dxfattribs={"layer": SERVERS_LAYER})

    # RX × 2 (rect 1×1.5, cola 5m al oeste a 0.3m).
    rx = doc.blocks.new("RX")
    closed_rectangle(rx, 1, 1.5)
    rx.add_lwpolyline([(-0.3, 0.75), (-5.3, 0.75)], format="xy")
    for pos in [(25, 15), (25, 22)]:
        msp.add_blockref("RX", insert=pos, dxfattribs={"layer": SERVERS_LAYER})

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    doc.saveas(OUTPUT_FILE)
    print(f"Wrote {OUTPUT_FILE}")


if __name__ == "__main__":
    build()
