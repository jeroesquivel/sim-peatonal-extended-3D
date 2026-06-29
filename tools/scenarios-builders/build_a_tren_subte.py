"""Builder del escenario a — Andén de tren / subte (G7).

Layout (40×25 m). Topología (consensuada G7):

- Oeste: UN generador `ACCESO` (los que suben) **del tamaño de la salida de calle y
  encima de ella** (sobre `EXIT_CALLE`, y=10..15). Aparecen donde salen los de la calle
  y se abren hacia los molinetes.
- Divisor x=20: **8 molinetes** bajo un único server base `MOL_ENT` (el parser numera por
  INSERT → `MOL_ENT_1..8`), repartidos en **2 grupos** (abajo y arriba) dejando libre el
  corredor central para los que bajan. Cada molinete tiene su **cola hacia el oeste**
  (izquierda) y su hueco en el muro divisor.
- Este x=36..40: **4 puertas** `PUERTA` (SEMÁFORO) = puertas del tren, **agrandadas**
  (recinto de espera más grande que el gap de embarque `EXIT_TREN`).
- Sobre cada puerta: generador `BAJADA` (los que bajan) **del mismo tamaño que la puerta**,
  insertado 4× (4 filas `BAJADA` → 1 sola entrada en parameters.json). Ráfaga "en batch"
  al ponerse verde se logra vía timing en parameters.json.

Salidas: `EXIT_TREN` (4 gaps este, abordar) y `EXIT_CALLE` (oeste, salir a la calle).
Target `ANDEN_OESTE` (rutea a los que bajan hacia la calle).

Uso:
    python tools/scenarios-builders/build_a_tren_subte.py
    # luego regenerar los CSV con tools/dxf-parser/parser.py
"""

from pathlib import Path

import ezdxf

OUTPUT_DIR = Path(__file__).resolve().parents[2] / "scenarios" / "a-tren-subte"
OUTPUT_FILE = OUTPUT_DIR / "plano.dxf"

WALLS_LAYER = "WALLS"
EXITS_LAYER = "EXITS"
TARGETS_LAYER = "TARGETS"
GENERATORS_LAYER = "GENERATORS"
SERVERS_LAYER = "SERVERS"

# ---- Dimensiones del recinto -------------------------------------------------
W = 40.0   # ancho total (x)
H = 25.0   # alto total (y)
DIV_X = 20.0  # x del muro divisor (molinetes)

# ---- Salida de calle (oeste) + ACCESO ---------------------------------------
CALLE_Y0, CALLE_Y1 = 10.0, 15.0   # gap oeste = EXIT_CALLE
ACCESO_W = 2.0                    # ACCESO encima de la salida, mismo alto que ella

# ---- Molinetes (8, en 2 grupos, server base único MOL_ENT) ------------------
# Centros y de cada molinete. Hueco del muro divisor y caja del server se alinean.
MOL_GROUP_BOTTOM = [2.5, 3.7, 4.9, 6.1]
MOL_GROUP_TOP = [17.9, 19.1, 20.3, 21.5]
MOL_CENTERS = MOL_GROUP_BOTTOM + MOL_GROUP_TOP
MOL_W = 0.8    # ancho de la caja del molinete (x) — angosto (turnstile)  [CALIBRAR]
MOL_H = 1.2    # alto/gap por donde pasan los agentes (y)                  [CALIBRAR]
QUEUE_LEN = 6.0  # largo de la cola hacia el OESTE (izquierda)
# Paso de SALIDA central: los que bajan salen al oeste por acá (subte real: entrás
# por molinete, salís libre). Sin esto, bajan y suben se cruzan de frente en el hueco
# del molinete y se bloquean (verificado: embarque cae a ~11%).
EXIT_DIV_GAP = (7.5, 17.5)

# ---- Puertas del tren (4 semáforos) + BAJADA --------------------------------
DOOR_CENTERS = [4.0, 10.0, 15.0, 21.0]
PUERTA_W = 4.0     # profundidad del recinto de espera (x)
PUERTA_H = 3.0     # alto del recinto — MÁS GRANDE que el gap de embarque   [CALIBRAR]
DOOR_GAP = 2.0     # alto del gap de embarque (EXIT_TREN) en la pared este

# ---- Target ------------------------------------------------------------------
ANDEN_CENTER = (14.0, 12.5)
ANDEN_R = 1.5


def closed_rectangle(block, w, h):
    block.add_lwpolyline([(0, 0), (w, 0), (w, h), (0, h)], format="xy", close=True)


def _solid_segments(span0, span1, gaps):
    """Devuelve los segmentos sólidos de [span0, span1] tras restar los `gaps`
    (lista de (a, b)). Sirve para muros con aberturas (divisor, pared este/oeste)."""
    segs = []
    cursor = span0
    for a, b in sorted(gaps):
        if a > cursor:
            segs.append((cursor, a))
        cursor = max(cursor, b)
    if cursor < span1:
        segs.append((cursor, span1))
    return segs


def build():
    doc = ezdxf.new(dxfversion="R2018", setup=True)
    for layer in (WALLS_LAYER, EXITS_LAYER, TARGETS_LAYER, GENERATORS_LAYER, SERVERS_LAYER):
        if layer not in doc.layers:
            doc.layers.add(layer)
    msp = doc.modelspace()

    def wall(p1, p2):
        msp.add_line(p1, p2, dxfattribs={"layer": WALLS_LAYER})

    # ---- Walls: contorno con aberturas ----
    wall((0, 0), (W, 0))   # sur
    wall((W, H), (0, H))   # norte

    # Pared ESTE (x=W) con gaps de embarque en cada puerta (EXIT_TREN).
    door_gaps = [(c - DOOR_GAP / 2.0, c + DOOR_GAP / 2.0) for c in DOOR_CENTERS]
    for y0, y1 in _solid_segments(0.0, H, door_gaps):
        wall((W, y0), (W, y1))

    # Pared OESTE (x=0) con el gap de la calle (EXIT_CALLE).
    for y0, y1 in _solid_segments(0.0, H, [(CALLE_Y0, CALLE_Y1)]):
        wall((0, y0), (0, y1))

    # Muro DIVISOR (x=DIV_X): hueco por molinete (entrada, suben) + paso de salida
    # central (bajan) para no cruzarse de frente con la subida.
    mol_gaps = [(c - MOL_H / 2.0, c + MOL_H / 2.0) for c in MOL_CENTERS]
    for y0, y1 in _solid_segments(0.0, H, mol_gaps + [EXIT_DIV_GAP]):
        wall((DIV_X, y0), (DIV_X, y1))

    # ---- EXITS ----
    # EXIT_TREN: un segmento vertical en cada gap de puerta (este).
    tren = doc.blocks.new("EXIT_TREN")
    tren.add_line((0, 0), (0, DOOR_GAP))
    for c in DOOR_CENTERS:
        msp.add_blockref("EXIT_TREN", insert=(W, c - DOOR_GAP / 2.0),
                         dxfattribs={"layer": EXITS_LAYER})
    # EXIT_CALLE: segmento oeste (y=10..15).
    calle = doc.blocks.new("EXIT_CALLE")
    calle.add_line((0, 0), (0, CALLE_Y1 - CALLE_Y0))
    msp.add_blockref("EXIT_CALLE", insert=(0, CALLE_Y0), dxfattribs={"layer": EXITS_LAYER})

    # ---- GENERATORS ----
    # ACCESO (suben): un rect del tamaño de EXIT_CALLE, encima de ella.
    acc = doc.blocks.new("ACCESO")
    closed_rectangle(acc, ACCESO_W, CALLE_Y1 - CALLE_Y0)
    msp.add_blockref("ACCESO", insert=(0, CALLE_Y0), dxfattribs={"layer": GENERATORS_LAYER})

    # BAJADA (bajan): un bloque del tamaño de la puerta, insertado sobre cada puerta.
    baj = doc.blocks.new("BAJADA")
    closed_rectangle(baj, PUERTA_W, PUERTA_H)
    for c in DOOR_CENTERS:
        msp.add_blockref("BAJADA", insert=(W - PUERTA_W, c - PUERTA_H / 2.0),
                         dxfattribs={"layer": GENERATORS_LAYER})

    # ---- SERVERS ----
    # MOL_ENT (QUEUE): un único bloque insertado 8× (el parser numera por INSERT).
    # Caja angosta al este del divisor + cola 6m hacia el OESTE (izquierda).
    mol = doc.blocks.new("MOL_ENT")
    closed_rectangle(mol, MOL_W, MOL_H)
    mol.add_lwpolyline([(-0.3, MOL_H / 2.0), (-0.3 - QUEUE_LEN, MOL_H / 2.0)], format="xy")
    for c in MOL_CENTERS:
        msp.add_blockref("MOL_ENT", insert=(DIV_X, c - MOL_H / 2.0),
                         dxfattribs={"layer": SERVERS_LAYER})

    # PUERTA (SEMÁFORO): un único bloque insertado 4×, recinto agrandado.
    puerta = doc.blocks.new("PUERTA")
    closed_rectangle(puerta, PUERTA_W, PUERTA_H)
    for c in DOOR_CENTERS:
        msp.add_blockref("PUERTA", insert=(W - PUERTA_W, c - PUERTA_H / 2.0),
                         dxfattribs={"layer": SERVERS_LAYER})

    # ---- TARGETS ----
    # ANDEN_OESTE: rutea a los que bajan hacia EXIT_CALLE.
    anden = doc.blocks.new("ANDEN_OESTE")
    anden.add_circle((0, 0), radius=ANDEN_R)
    msp.add_blockref("ANDEN_OESTE", insert=ANDEN_CENTER, dxfattribs={"layer": TARGETS_LAYER})

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    doc.saveas(OUTPUT_FILE)
    print(f"Wrote {OUTPUT_FILE}")


if __name__ == "__main__":
    build()
