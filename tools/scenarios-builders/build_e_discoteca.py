"""Builder del escenario e — Discoteca (G6).

Layout (40×30m):
- INGRESO_DISCO al sur-oeste.
- CONTROL_ACCESO (server con cola hacia el sur).
- BARRA target al norte-oeste, PISTA target grande al centro-norte.
- 2 BANO_DISCO al norte-este (con cola).
- SALIDA_NORMAL al sur, SALIDA_EMERGENCIA al oeste.
"""

from pathlib import Path

import ezdxf

OUTPUT_DIR = Path(__file__).resolve().parents[2] / "scenarios" / "e-discoteca"
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
        ((0, 0), (19, 0)),     # sur izquierdo
        ((21, 0), (40, 0)),    # sur derecho (gap SALIDA_NORMAL en x=19-21)
        ((40, 0), (40, 30)),   # este
        ((40, 30), (0, 30)),   # norte
        ((0, 30), (0, 16)),    # oeste superior
        ((0, 14), (0, 0)),     # oeste inferior (gap SALIDA_EMERGENCIA en y=14-16)
    ]
    for p1, p2 in walls:
        msp.add_line(p1, p2, dxfattribs={"layer": WALLS_LAYER})

    sn = doc.blocks.new("SALIDA_NORMAL")
    sn.add_line((0, 0), (2, 0))
    msp.add_blockref("SALIDA_NORMAL", insert=(19, 0), dxfattribs={"layer": EXITS_LAYER})

    se = doc.blocks.new("SALIDA_EMERGENCIA")
    se.add_line((0, 0), (0, 2))
    msp.add_blockref("SALIDA_EMERGENCIA", insert=(0, 14), dxfattribs={"layer": EXITS_LAYER})

    ingreso = doc.blocks.new("INGRESO_DISCO")
    closed_rectangle(ingreso, 3, 2)
    msp.add_blockref("INGRESO_DISCO", insert=(5, 1), dxfattribs={"layer": GENERATORS_LAYER})

    barra = doc.blocks.new("BARRA")
    closed_rectangle(barra, 6, 1.5)
    msp.add_blockref("BARRA", insert=(3, 22), dxfattribs={"layer": TARGETS_LAYER})

    pista = doc.blocks.new("PISTA")
    closed_rectangle(pista, 15, 10)
    msp.add_blockref("PISTA", insert=(12, 17), dxfattribs={"layer": TARGETS_LAYER})

    # CONTROL_ACCESO (rect 2×1, cola 6m al sur a 0.3m).
    control = doc.blocks.new("CONTROL_ACCESO")
    closed_rectangle(control, 2, 1)
    control.add_lwpolyline([(1, -0.3), (1, -6.3)], format="xy")
    msp.add_blockref("CONTROL_ACCESO", insert=(15, 7), dxfattribs={"layer": SERVERS_LAYER})

    # BANO_DISCO × 2 (rect 1×1, cola 2m al sur a 0.3m).
    bano = doc.blocks.new("BANO_DISCO")
    closed_rectangle(bano, 1, 1)
    bano.add_lwpolyline([(0.5, -0.3), (0.5, -2.3)], format="xy")
    for pos in [(35, 25), (35, 20)]:
        msp.add_blockref("BANO_DISCO", insert=pos, dxfattribs={"layer": SERVERS_LAYER})

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    doc.saveas(OUTPUT_FILE)
    print(f"Wrote {OUTPUT_FILE}")


if __name__ == "__main__":
    build()
