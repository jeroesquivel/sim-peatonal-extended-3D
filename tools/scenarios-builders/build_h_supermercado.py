"""Builder del escenario h — Supermercado (G3).

Layout (30×20m):
- Contorno con gaps al sur (ENTRADA) y norte (SALIDA).
- 1 SPAWN_ENTRADA junto a la entrada.
- 5 GONDOLA en 2 filas paralelas.
- 3 CAJA con cola hacia el sur (primer vértice a 0.3m del server).

Uso:
    python3 tools/scenarios-builders/build_h_supermercado.py

Output:
    scenarios/h-supermercado/plano.dxf
"""

from pathlib import Path

import ezdxf

OUTPUT_DIR = Path(__file__).resolve().parents[2] / "scenarios" / "h-supermercado"
OUTPUT_FILE = OUTPUT_DIR / "plano.dxf"

WALLS_LAYER = "WALLS"
EXITS_LAYER = "EXITS"
TARGETS_LAYER = "TARGETS"
GENERATORS_LAYER = "GENERATORS"
SERVERS_LAYER = "SERVERS"


def closed_rectangle(block, w, h):
    """Agrega un LWPOLYLINE cerrado con esquinas (0,0) y (w,h)."""
    block.add_lwpolyline(
        [(0, 0), (w, 0), (w, h), (0, h)],
        format="xy",
        close=True,
    )


def build():
    doc = ezdxf.new(dxfversion="R2018", setup=True)
    for layer in (WALLS_LAYER, EXITS_LAYER, TARGETS_LAYER, GENERATORS_LAYER, SERVERS_LAYER):
        if layer not in doc.layers:
            doc.layers.add(layer)
    msp = doc.modelspace()

    # ---- Walls (contorno con gaps de 2m en ENTRADA y SALIDA) ----
    walls = [
        ((0, 0), (14, 0)),    # sur izquierdo
        ((16, 0), (30, 0)),   # sur derecho
        ((30, 0), (30, 20)),  # este
        ((30, 20), (16, 20)), # norte derecho
        ((14, 20), (0, 20)),  # norte izquierdo
        ((0, 20), (0, 0)),    # oeste
    ]
    for p1, p2 in walls:
        msp.add_line(p1, p2, dxfattribs={"layer": WALLS_LAYER})

    # ---- EXITS (blocks con LINE adentro) ----
    entrada = doc.blocks.new("ENTRADA")
    entrada.add_line((0, 0), (2, 0))
    msp.add_blockref("ENTRADA", insert=(14, 0), dxfattribs={"layer": EXITS_LAYER})

    salida = doc.blocks.new("SALIDA")
    salida.add_line((0, 0), (2, 0))
    msp.add_blockref("SALIDA", insert=(14, 20), dxfattribs={"layer": EXITS_LAYER})

    # ---- GENERATORS (block con rectángulo cerrado adentro) ----
    spawn_block = doc.blocks.new("SPAWN_ENTRADA")
    closed_rectangle(spawn_block, 3, 2)
    msp.add_blockref("SPAWN_ENTRADA", insert=(13.5, 1), dxfattribs={"layer": GENERATORS_LAYER})

    # ---- TARGETS (block GONDOLA rectangular, insertado 5 veces) ----
    gondola = doc.blocks.new("GONDOLA")
    closed_rectangle(gondola, 4, 0.8)
    gondola_positions = [
        (3, 7),    # fila 1, oeste
        (11, 7),   # fila 1, centro
        (19, 7),   # fila 1, este
        (7, 10),   # fila 2, oeste-centro (offset)
        (15, 10),  # fila 2, este-centro (offset)
    ]
    for pos in gondola_positions:
        msp.add_blockref("GONDOLA", insert=pos, dxfattribs={"layer": TARGETS_LAYER})

    # ---- SERVERS (block CAJA con rectángulo SERVER + cola, insertado 3 veces) ----
    # Server rect (0,0)-(1.5,1). Cola LWPOLYLINE de (0.75,-0.3) a (0.75,-4.3).
    # Primer vértice a 0.3m del rectángulo (cumple V22, <1m).
    caja = doc.blocks.new("CAJA")
    closed_rectangle(caja, 1.5, 1)
    caja.add_lwpolyline([(0.75, -0.3), (0.75, -4.3)], format="xy")
    caja_positions = [(6, 15.5), (14, 15.5), (22, 15.5)]
    for pos in caja_positions:
        msp.add_blockref("CAJA", insert=pos, dxfattribs={"layer": SERVERS_LAYER})

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    doc.saveas(OUTPUT_FILE)
    print(f"Wrote {OUTPUT_FILE}")


if __name__ == "__main__":
    build()
