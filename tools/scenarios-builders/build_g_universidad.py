"""Builder del escenario g — Universidad (G4).

Layout (45×30m):
- Hall central con kiosko al medio.
- 2 AULA (servers classroom 8×6m) a los costados.
- 2 BANO_UNI al fondo (con cola).
- INGRESO_UNI al sur, EGRESO_NORMAL al sur, EGRESO_MASIVO al norte.
"""

from pathlib import Path

import ezdxf

OUTPUT_DIR = Path(__file__).resolve().parents[2] / "scenarios" / "g-universidad"
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
        ((0, 0), (5, 0)),         # sur izquierdo (gap EGRESO_NORMAL en x=5-7)
        ((7, 0), (45, 0)),        # sur derecho
        ((45, 0), (45, 30)),      # este
        ((45, 30), (24.5, 30)),   # norte derecho (gap EGRESO_MASIVO en x=20.5-24.5)
        ((20.5, 30), (0, 30)),    # norte izquierdo
        ((0, 30), (0, 0)),        # oeste
    ]
    for p1, p2 in walls:
        msp.add_line(p1, p2, dxfattribs={"layer": WALLS_LAYER})

    egreso_n = doc.blocks.new("EGRESO_NORMAL")
    egreso_n.add_line((0, 0), (2, 0))
    msp.add_blockref("EGRESO_NORMAL", insert=(5, 0), dxfattribs={"layer": EXITS_LAYER})

    egreso_m = doc.blocks.new("EGRESO_MASIVO")
    egreso_m.add_line((0, 0), (4, 0))
    msp.add_blockref("EGRESO_MASIVO", insert=(20.5, 30), dxfattribs={"layer": EXITS_LAYER})

    ingreso = doc.blocks.new("INGRESO_UNI")
    closed_rectangle(ingreso, 3, 3)
    msp.add_blockref("INGRESO_UNI", insert=(21, 1), dxfattribs={"layer": GENERATORS_LAYER})

    kiosco = doc.blocks.new("KIOSCO_UNI")
    closed_rectangle(kiosco, 3, 2)
    msp.add_blockref("KIOSCO_UNI", insert=(21, 14), dxfattribs={"layer": TARGETS_LAYER})

    # AULA × 2 (rect 8×6, classroom sin cola).
    aula = doc.blocks.new("AULA")
    closed_rectangle(aula, 8, 6)
    for pos in [(3, 12), (34, 12)]:
        msp.add_blockref("AULA", insert=pos, dxfattribs={"layer": SERVERS_LAYER})

    # BANO_UNI × 2 (rect 1×1, cola 2m al sur a 0.3m).
    bano = doc.blocks.new("BANO_UNI")
    closed_rectangle(bano, 1, 1)
    bano.add_lwpolyline([(0.5, -0.3), (0.5, -2.3)], format="xy")
    for pos in [(15, 24), (29, 24)]:
        msp.add_blockref("BANO_UNI", insert=pos, dxfattribs={"layer": SERVERS_LAYER})

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    doc.saveas(OUTPUT_FILE)
    print(f"Wrote {OUTPUT_FILE}")


if __name__ == "__main__":
    build()
