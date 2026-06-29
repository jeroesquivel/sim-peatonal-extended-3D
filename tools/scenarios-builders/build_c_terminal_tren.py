"""Builder del escenario c — Terminal de Tren (G9).

Layout (45×30m):
- Hall central.
- KIOSCO al norte (1 target rectangular).
- 2 MOLINETE_T al este con dividir entre ellos (canalizan flujo).
- 1 BANO al oeste con cola corta.
- INGRESO_TERMINAL al sur (generator).
- ANDEN exit al este (vertical 4m).
"""

from pathlib import Path

import ezdxf

OUTPUT_DIR = Path(__file__).resolve().parents[2] / "scenarios" / "c-terminal-tren"
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
        ((0, 0), (45, 0)),     # sur
        ((45, 0), (45, 13)),   # este inferior (debajo de ANDEN)
        ((45, 17), (45, 30)),  # este superior
        ((45, 30), (0, 30)),   # norte
        ((0, 30), (0, 0)),     # oeste
        ((30, 13), (30, 17)),  # divisor entre MOLINETE_T_1 y MOLINETE_T_2
    ]
    for p1, p2 in walls:
        msp.add_line(p1, p2, dxfattribs={"layer": WALLS_LAYER})

    # ANDEN (4m vertical al este)
    anden = doc.blocks.new("ANDEN")
    anden.add_line((0, 0), (0, 4))
    msp.add_blockref("ANDEN", insert=(45, 13), dxfattribs={"layer": EXITS_LAYER})

    # INGRESO_TERMINAL (3×3m al sur)
    ingreso = doc.blocks.new("INGRESO_TERMINAL")
    closed_rectangle(ingreso, 3, 3)
    msp.add_blockref("INGRESO_TERMINAL", insert=(21, 1), dxfattribs={"layer": GENERATORS_LAYER})

    # KIOSCO (4×3m al norte)
    kiosco = doc.blocks.new("KIOSCO")
    closed_rectangle(kiosco, 4, 3)
    msp.add_blockref("KIOSCO", insert=(10, 25), dxfattribs={"layer": TARGETS_LAYER})

    # MOLINETE_T × 2 (rect 0.8×1, cola 3m al oeste a 0.3m)
    molinete = doc.blocks.new("MOLINETE_T")
    closed_rectangle(molinete, 0.8, 1)
    molinete.add_lwpolyline([(-0.3, 0.5), (-3.3, 0.5)], format="xy")
    for pos in [(30, 12), (30, 17)]:
        msp.add_blockref("MOLINETE_T", insert=pos, dxfattribs={"layer": SERVERS_LAYER})

    # BANO × 1 (rect 1×1, cola 2m al este a 0.3m)
    bano = doc.blocks.new("BANO")
    closed_rectangle(bano, 1, 1)
    bano.add_lwpolyline([(1.3, 0.5), (3.3, 0.5)], format="xy")
    msp.add_blockref("BANO", insert=(5, 12), dxfattribs={"layer": SERVERS_LAYER})

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    doc.saveas(OUTPUT_FILE)
    print(f"Wrote {OUTPUT_FILE}")


if __name__ == "__main__":
    build()
