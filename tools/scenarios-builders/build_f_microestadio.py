"""Builder del escenario f — Microestadio (G2).

Layout (50×40m):
- HALL_RECITAL target al sur.
- 3 CONTROL_CAMPO server entre hall y campo (cola hacia el sur).
- CAMPO_RECITAL target grande al centro-norte.
- ESCENARIO server classroom al norte.
- 2 COMIDA_RECITAL en los costados (con cola).
- 4 SALIDA_ESTADIO en las 4 paredes (egreso masivo).
- INGRESO_ESTADIO generator al sur.
"""

from pathlib import Path

import ezdxf

OUTPUT_DIR = Path(__file__).resolve().parents[2] / "scenarios" / "f-microestadio"
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
        ((0, 0), (6, 0)),         # sur izquierdo (gap SALIDA_S en x=6-9)
        ((9, 0), (50, 0)),        # sur derecho
        ((50, 0), (50, 23.5)),    # este inferior (gap SALIDA_E en y=23.5-26.5)
        ((50, 26.5), (50, 40)),   # este superior
        ((50, 40), (26.5, 40)),   # norte derecho (gap SALIDA_N en x=23.5-26.5)
        ((23.5, 40), (0, 40)),    # norte izquierdo
        ((0, 40), (0, 26.5)),     # oeste superior (gap SALIDA_W en y=23.5-26.5)
        ((0, 23.5), (0, 0)),      # oeste inferior
    ]
    for p1, p2 in walls:
        msp.add_line(p1, p2, dxfattribs={"layer": WALLS_LAYER})

    salida = doc.blocks.new("SALIDA_ESTADIO")
    salida.add_line((0, 0), (3, 0))
    msp.add_blockref("SALIDA_ESTADIO", insert=(6, 0), dxfattribs={"layer": EXITS_LAYER})
    msp.add_blockref("SALIDA_ESTADIO", insert=(23.5, 40), dxfattribs={"layer": EXITS_LAYER})
    salida_v = doc.blocks.new("SALIDA_ESTADIO_V")
    salida_v.add_line((0, 0), (0, 3))
    msp.add_blockref("SALIDA_ESTADIO_V", insert=(50, 23.5), dxfattribs={"layer": EXITS_LAYER})
    msp.add_blockref("SALIDA_ESTADIO_V", insert=(0, 23.5), dxfattribs={"layer": EXITS_LAYER})

    ingreso = doc.blocks.new("INGRESO_ESTADIO")
    closed_rectangle(ingreso, 4, 3)
    msp.add_blockref("INGRESO_ESTADIO", insert=(23, 1), dxfattribs={"layer": GENERATORS_LAYER})

    hall = doc.blocks.new("HALL_RECITAL")
    closed_rectangle(hall, 10, 4)
    msp.add_blockref("HALL_RECITAL", insert=(20, 5), dxfattribs={"layer": TARGETS_LAYER})

    campo = doc.blocks.new("CAMPO_RECITAL")
    closed_rectangle(campo, 25, 15)
    msp.add_blockref("CAMPO_RECITAL", insert=(12, 18), dxfattribs={"layer": TARGETS_LAYER})

    # CONTROL_CAMPO × 3 (rect 1.5×1, cola 5m al sur a 0.3m).
    control = doc.blocks.new("CONTROL_CAMPO")
    closed_rectangle(control, 1.5, 1)
    control.add_lwpolyline([(0.75, -0.3), (0.75, -5.3)], format="xy")
    for pos in [(15, 15), (23, 15), (31, 15)]:
        msp.add_blockref("CONTROL_CAMPO", insert=pos, dxfattribs={"layer": SERVERS_LAYER})

    # COMIDA_RECITAL × 2 (rect 1.5×1, cola 4m horizontal a 0.3m).
    comida_w = doc.blocks.new("COMIDA_RECITAL_W")
    closed_rectangle(comida_w, 1.5, 1)
    comida_w.add_lwpolyline([(1.8, 0.5), (5.8, 0.5)], format="xy")
    msp.add_blockref("COMIDA_RECITAL_W", insert=(3, 12), dxfattribs={"layer": SERVERS_LAYER})

    comida_e = doc.blocks.new("COMIDA_RECITAL_E")
    closed_rectangle(comida_e, 1.5, 1)
    comida_e.add_lwpolyline([(-0.3, 0.5), (-4.3, 0.5)], format="xy")
    msp.add_blockref("COMIDA_RECITAL_E", insert=(45, 12), dxfattribs={"layer": SERVERS_LAYER})

    # ESCENARIO (rect 8×4, classroom sin cola).
    escenario = doc.blocks.new("ESCENARIO")
    closed_rectangle(escenario, 8, 4)
    msp.add_blockref("ESCENARIO", insert=(21, 34), dxfattribs={"layer": SERVERS_LAYER})

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    doc.saveas(OUTPUT_FILE)
    print(f"Wrote {OUTPUT_FILE}")


if __name__ == "__main__":
    build()
