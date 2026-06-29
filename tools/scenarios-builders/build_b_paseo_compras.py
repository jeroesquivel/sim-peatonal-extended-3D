"""Builder del escenario b — Paseo de compras (G8).

Layout (50×30 m), pensado como deambular de baja velocidad / baja urgencia:

    Entrada (OESTE) ───────────────► Salida (ESTE)

- ENTRADA_PASEO: abertura en la pared oeste (y 14..16.5). Solo es un hueco
  de pared + la zona generadora SPAWN_OESTE adentro; NO es un EXIT, para que
  nadie "salga" por donde entró.
- EXIT_ESTE: única salida, abertura en la pared este (y 14..16.5). Al ser la
  única salida, todos los agentes egresan por la derecha.
- VITRINA × 8: dos filas (norte y sur) a lo largo del corredor (TARGETS).
- PATIO_COMIDAS × 12: grilla de mesas (TARGETs de estadía) en el centro; la
  gente se reparte y se sienta en mesas distintas.
- BANO × 2: SERVERS tipo queue (con cola), sobre el sector derecho (antes de
  la salida) — la cola del baño es la parte realista del modelo.

Ningún objeto (paredes, servers, targets, generadores, salidas) se solapa
con otro: las vitrinas viven en las franjas y∈[3,4] (sur) e y∈[26,27] (norte),
la comida en el centro (x∈[20,28], y∈[12,18]) y los baños a la derecha
(x∈[42,43]), todos con separación entre sí.
"""

from pathlib import Path

import ezdxf

OUTPUT_DIR = Path(__file__).resolve().parents[2] / "scenarios" / "b-paseo-compras"
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

    # Perímetro 50×30 con dos aberturas enfrentadas a media altura (y 14..16.5):
    # entrada al oeste, salida al este.
    walls = [
        ((0, 0), (50, 0)),        # sur
        ((50, 0), (50, 14)),      # este inferior (hasta EXIT_ESTE)
        ((50, 16.5), (50, 30)),   # este superior
        ((50, 30), (0, 30)),      # norte
        ((0, 30), (0, 16.5)),     # oeste superior (hasta ENTRADA_PASEO)
        ((0, 14), (0, 0)),        # oeste inferior
    ]
    for p1, p2 in walls:
        msp.add_line(p1, p2, dxfattribs={"layer": WALLS_LAYER})

    # Única salida: al este. (La entrada oeste es solo hueco de pared + spawn,
    # no se declara como EXIT para que todos egresen por la derecha.)
    exit_este = doc.blocks.new("EXIT_ESTE")
    exit_este.add_line((0, 0), (0, 2.5))
    msp.add_blockref("EXIT_ESTE", insert=(50, 14), dxfattribs={"layer": EXITS_LAYER})

    # Zona generadora, apenas adentro de la abertura oeste.
    spawn = doc.blocks.new("SPAWN_OESTE")
    closed_rectangle(spawn, 3, 2)
    msp.add_blockref("SPAWN_OESTE", insert=(1, 14), dxfattribs={"layer": GENERATORS_LAYER})

    # VITRINA × 8 (3×1 m): 4 al norte (y=26) + 4 al sur (y=3).
    vitrina = doc.blocks.new("VITRINA")
    closed_rectangle(vitrina, 3, 1)
    vitrina_positions = [
        (6, 26), (15, 26), (24, 26), (33, 26),  # fila norte
        (6, 3), (15, 3), (24, 3), (33, 3),       # fila sur
    ]
    for pos in vitrina_positions:
        msp.add_blockref("VITRINA", insert=pos, dxfattribs={"layer": TARGETS_LAYER})

    # PATIO_COMIDAS: grilla de MESAS (12 LOCATIONs de 0.8×0.8 m) en el centro.
    mesa = doc.blocks.new("PATIO_COMIDAS")
    closed_rectangle(mesa, 0.8, 0.8)
    mesa_xs = [21.0, 23.0, 25.0, 27.0]   # 4 columnas
    mesa_ys = [13.5, 15.0, 16.5]         # 3 filas  → 12 mesas
    for cy in mesa_ys:
        for cx in mesa_xs:
            msp.add_blockref("PATIO_COMIDAS", insert=(cx - 0.4, cy - 0.4),
                             dxfattribs={"layer": TARGETS_LAYER})

    # BANO × 2 (1×1 m, cola 2 m hacia el oeste a 0.3 m), sector derecho.
    bano = doc.blocks.new("BANO")
    closed_rectangle(bano, 1, 1)
    bano.add_lwpolyline([(-0.3, 0.5), (-2.3, 0.5)], format="xy")
    for pos in [(42, 7), (42, 22)]:
        msp.add_blockref("BANO", insert=pos, dxfattribs={"layer": SERVERS_LAYER})

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    doc.saveas(OUTPUT_FILE)
    print(f"Wrote {OUTPUT_FILE}")


if __name__ == "__main__":
    build()
