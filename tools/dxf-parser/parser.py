import argparse
import math
import ezdxf
import re as regex
from pathlib import Path

WALLS_LAYER = "WALLS"
EXITS_LAYER = "EXITS"
GENERATORS_LAYER = "GENERATORS"
TARGETS_LAYER = "TARGETS"
SERVERS_LAYER = "SERVERS"

# TARGETs de zona — gridificación.
# Un TARGET con área > umbral se expande a N puntos (figure_type=CIRCLE, radius=0)
# en grilla cartesiana centrada con paso `spacing`. TARGETs sub-umbral conservan
# su fila tal cual (1 fila, RECTANGLE o CIRCLE original).
DEFAULT_TARGET_ZONE_SPACING_M = 1.0
DEFAULT_TARGET_ZONE_THRESHOLD_M2 = 2.0


def is_rectangle(entity):
    if entity.dxftype() == 'POLYLINE':
        # In a rectangle of polyline there are 4 vertices + 1 closing vertex (that is the same as the first one)
        return len(entity) == 5 and entity[0].dxf.location == entity[-1].dxf.location
    elif entity.dxftype() == 'LWPOLYLINE':
        if len(entity) == 4 and entity.dxf.flags == ezdxf.const.LWPOLYLINE_CLOSED:
            return True
        elif len(entity) == 5:
            first_vertex = None
            last_vertex = None
            for i, vertex in enumerate(entity.vertices()):
                if i == 0:
                    first_vertex = vertex
                elif i == 4:
                    last_vertex = vertex
            return first_vertex[0] - last_vertex[0] + first_vertex[1] - last_vertex[1] < 0.0001
    
    return False

def get_rectangle_figure(entity):
    # If we know we are getting a rectangle, we can save just top left and bottom right vertices
    if entity.dxftype() == 'POLYLINE':
        return [entity[0].dxf.location[0], entity[0].dxf.location[1], entity[0].dxf.location[2],
                entity[2].dxf.location[0], entity[2].dxf.location[1], entity[2].dxf.location[2]]
    elif entity.dxftype() == 'LWPOLYLINE':
        first_vertex = None
        third_vertex = None
        for i, vertex in enumerate(entity.vertices()):
            if i == 0:
                first_vertex = [*vertex, 0]
            elif i == 2:
                third_vertex = [*vertex, 0]
        
        return [*first_vertex, *third_vertex]
    else: ValueError(f'Entity type {entity.dxftype()} is not supported as a rectangle.')

def get_figures(entity, layer_prefix):
    figures = []
    if entity.dxftype() == 'LINE':
        figures.append([entity.dxf.start[0], entity.dxf.start[1], entity.dxf.start[2],
                        entity.dxf.end[0], entity.dxf.end[1], entity.dxf.end[2]])
        
    elif entity.dxftype() == 'CIRCLE':
        figures.append([entity.dxf.radius, entity.dxf.center[0], entity.dxf.center[1],
                       entity.dxf.center[2]])
        
    elif entity.dxftype() == 'POLYLINE':
        lines_qty = len(entity)
        if not entity.is_closed:
            lines_qty -= 1  # because the last vertex does not have to connect to the first one

        for i in range(lines_qty):
            current_vertex_location = entity[(i) % len(entity)].dxf.location
            next_vertex_location = entity[(i+1) % len(entity)].dxf.location
            figures.append([current_vertex_location[0], current_vertex_location[1], current_vertex_location[2],
                            next_vertex_location[0], next_vertex_location[1], next_vertex_location[2]])
            
    elif entity.dxftype() == 'LWPOLYLINE':
        iterator = entity.vertices()
        first_vertex_location = next(iterator)
        previous_vertex_location = first_vertex_location
        for current_vertex_location in iterator:
            figures.append([previous_vertex_location[0], previous_vertex_location[1], 0,
                            current_vertex_location[0], current_vertex_location[1], 0])
            previous_vertex_location = current_vertex_location
        
        if entity.dxf.flags == ezdxf.const.LWPOLYLINE_CLOSED:
            figures.append([previous_vertex_location[0], previous_vertex_location[1], 0,
                            first_vertex_location[0], first_vertex_location[1], 0])
            
    else:
        raise ValueError(
            f'Layer {layer_prefix} contains {entity.dxftype()} entities which is not supported.')

    return figures


def parse_entities(entities, layer_prefix, expected_types, name=None, figures_can_be_rectangles=False):
    figures = []
    for entity in entities:
        if entity.dxftype() not in expected_types:
            raise ValueError(
                f'Layer {layer_prefix} contains {entity.dxftype()} entities which is not in the expected: {expected_types}.')

        if figures_can_be_rectangles and is_rectangle(entity):
            new_figures = [get_rectangle_figure(entity)]
        else:
            new_figures = get_figures(entity, layer_prefix)

        for new_figure in new_figures:
            if name is not None:
                new_figure.insert(0, name)

            figures.append(new_figure)

    if len(figures) == 0:
        raise ValueError(f'Layer {WALLS_LAYER} is empty.')
    return figures

def write_headers_to_file(file, layer):
    headers = ''
    if layer == WALLS_LAYER:
        headers = f'x1, y1, z1, x2, y2, z2\n'
    elif layer == EXITS_LAYER or layer == GENERATORS_LAYER or layer == SERVERS_LAYER:
        headers = f'block_name, x1, y1, z1, x2, y2, z2\n'
    elif layer == TARGETS_LAYER:
        headers = f'block_name, figure_type, radius, x1, y1, z1, x2, y2, z2\n'
    else: ValueError(f'Layer {layer} is not supported.')

    file.write(headers)

def write_with_figure_type_to_file(file, array):
    DECIMALS = 6  # round to avoid minimal inaccuracies from autocad
    for value in array:
        with_name = type(value[0]) is str
        start = 1 if with_name else 0
        nums = [round(value[i], DECIMALS) for i in range(start, len(value))]

        if len(value) - with_name == 4:
            # CIRCLE: x1, y1, z1, radius. Format expects 9 cols total:
            # block_name, figure_type, radius, x1, y1, z1, x2, y2, z2.
            row = nums + [0.0, 0.0, 0.0]
            figure_type = 'CIRCLE'
        else:
            # RECTANGLE: -1 as radius placeholder.
            row = [-1] + nums
            figure_type = 'RECTANGLE'

        prefix = f'{value[0]}, ' if with_name else ''
        file.write(prefix + figure_type + ', ' + ', '.join(str(n) for n in row) + '\n')


def write_to_file(file, array):
    for value in array:
        with_name = False
        if type(value[0]) is str:
            with_name = True
            file.write(f'{value[0]}, ')

        # round to 6 decimals to avoid minimal innacuracies from autocad
        DECIMALS = 6
        for i in range(with_name == True, len(value) - 1):
            file.write(f'{round(value[i], DECIMALS)}, ')

        file.write(f'{round(value[len(value)-1], DECIMALS)}\n')


def get_blocks_figures(msp, layer, expected_types, figures_can_be_rectangles=False):
    figures = []
    for block in msp.query('INSERT').filter(lambda block: regex.match(r"{}*".format(layer), block.dxfattribs()['layer'])):
        # Blocks que aparecen en mas de una layer: el caso lo detecta el
        # loader Java vía BlockInMultipleLayersValidator (V12).
        figures += parse_entities(block.virtual_entities(), layer, expected_types,
                                  block.dxf.name, figures_can_be_rectangles)

    return figures


def get_walls(msp, expected_types):
    figures = parse_entities(msp.query().filter(lambda entity: regex.match(r"{}*".format(WALLS_LAYER), entity.dxf.layer)),
                             WALLS_LAYER,
                             expected_types=expected_types,
                             name=None,
                             figures_can_be_rectangles=False)

    return figures


def get_servers(msp):
    servers_map = {}
    # retrieve all blocks in the servers layer, grouping by name
    for server in msp.query('INSERT').filter(lambda block: regex.match(r"{}*".format(SERVERS_LAYER), block.dxfattribs()['layer'])):
        if server.dxf.name not in servers_map:
            servers_map[server.dxf.name] = []

        servers_map[server.dxf.name].append(server)

    figures = []
    # retrieve queue and server for each server group
    # assign an id to each instance so we can identify the server and its queue on the csv
    for key in servers_map:
        id = 0
        for server in servers_map[key]:
            id += 1
            # queue_id corre por SERVER instance (no por POLYLINE entity)
            # para evitar QUEUEnnn duplicados cuando hay varias POLYLINEs/LINEs.
            queue_id = 0
            for entity in server.virtual_entities():
                if entity.dxftype() == 'POLYLINE' or entity.dxftype() == 'LWPOLYLINE':
                    if is_rectangle(entity):
                        # its a server
                        figures.append(
                            [f'{key}_{id}_SERVER', *get_rectangle_figure(entity)])
                    else:
                        # its a queue
                        for line in get_figures(entity, SERVERS_LAYER):
                            figures.append(
                                [f'{key}_{id}_QUEUE{queue_id:03d}', *line])
                            queue_id += 1

                elif entity.dxftype() == 'LINE':
                    # has to be a queue
                    figures.append([f'{key}_{id}_QUEUE{queue_id:03d}',
                                    entity.dxf.start[0], entity.dxf.start[1], entity.dxf.start[2],
                                    entity.dxf.end[0], entity.dxf.end[1], entity.dxf.end[2]])
                    queue_id += 1

    return figures


def parse_layer_and_write_to_file(msp, layer, expected_types, out_file_path, figures_can_be_rectangles=False,
                                  target_zone_spacing=DEFAULT_TARGET_ZONE_SPACING_M,
                                  target_zone_threshold=DEFAULT_TARGET_ZONE_THRESHOLD_M2):
    if layer == WALLS_LAYER:
        array = get_walls(msp, expected_types)
    elif layer == SERVERS_LAYER:
        array = get_servers(msp)
    else:
        array = get_blocks_figures(
            msp, layer, expected_types, figures_can_be_rectangles)

    if layer == TARGETS_LAYER:
        array = _expand_target_zones(array, target_zone_spacing, target_zone_threshold)

    # create parent directory if it doesn't exist
    Path(out_file_path).parent.mkdir(parents=True, exist_ok=True)
    file = open(out_file_path, "w")
    write_headers_to_file(file, layer)
    if layer == TARGETS_LAYER:
        write_with_figure_type_to_file(file, array)
    else:
        write_to_file(file, array)
    file.close()


def _gridify_rectangle(x1, y1, x2, y2, spacing):
    """Grilla cartesiana centrada dentro del rectángulo axis-aligned definido
    por (x1,y1)-(x2,y2). Devuelve lista de centros de celda (px, py).

    Zonas lineales (una dimensión < spacing): se emite al menos 1 fila de
    puntos en esa dimensión (centrada) — ej. góndola 4×0.8 con s=1.0 da 4
    puntos a lo largo de la góndola en su línea media.
    """
    xmin, xmax = (x1, x2) if x1 <= x2 else (x2, x1)
    ymin, ymax = (y1, y2) if y1 <= y2 else (y2, y1)
    width = xmax - xmin
    height = ymax - ymin
    if width <= 0 or height <= 0:
        return []
    nx = max(1, int(width // spacing))
    ny = max(1, int(height // spacing))
    pad_x = (width - nx * spacing) / 2.0
    pad_y = (height - ny * spacing) / 2.0
    points = []
    for j in range(ny):
        for i in range(nx):
            px = xmin + pad_x + (i + 0.5) * spacing
            py = ymin + pad_y + (j + 0.5) * spacing
            points.append((px, py))
    return points


def _gridify_circle(cx, cy, radius, spacing):
    """Grilla cartesiana sobre el AABB del círculo, clippeada al disco."""
    aabb_points = _gridify_rectangle(cx - radius, cy - radius,
                                     cx + radius, cy + radius, spacing)
    r2 = radius * radius
    return [(px, py) for (px, py) in aabb_points
            if (px - cx) ** 2 + (py - cy) ** 2 <= r2]


def _should_gridify(figure_row, threshold):
    """True si el TARGET tiene área > umbral y debe gridificarse.

    `figure_row` viene de `get_blocks_figures` con el name al inicio:
    - RECTANGLE: [name, x1, y1, z1, x3, y3, z3]  (7 elementos)
    - CIRCLE:    [name, radius, cx, cy, cz]      (5 elementos)
    """
    payload_len = len(figure_row) - 1
    if payload_len == 4:  # CIRCLE
        radius = figure_row[1]
        return math.pi * radius * radius > threshold
    if payload_len == 6:  # RECTANGLE
        x1, y1, _, x2, y2, _ = figure_row[1:]
        return abs(x2 - x1) * abs(y2 - y1) > threshold
    # Cualquier otra forma (polígono libre, segmentos sueltos) queda como está.
    return False


def _expand_target_zones(figures, spacing, threshold):
    """Expande TARGETs de zona a N puntos en grilla; los sub-umbral pasan tal cual.

    Cada punto se emite como CIRCLE radius=0 (formato interno antes de
    `write_with_figure_type_to_file`): [name, 0.0, px, py, 0.0].
    """
    expanded = []
    for figure in figures:
        if not _should_gridify(figure, threshold):
            expanded.append(figure)
            continue
        name = figure[0]
        payload_len = len(figure) - 1
        if payload_len == 4:  # CIRCLE
            radius, cx, cy, _ = figure[1:]
            points = _gridify_circle(cx, cy, radius, spacing)
        else:  # RECTANGLE (payload_len == 6)
            x1, y1, _, x2, y2, _ = figure[1:]
            points = _gridify_rectangle(x1, y1, x2, y2, spacing)
        for (px, py) in points:
            expanded.append([name, 0.0, px, py, 0.0])
    return expanded


def parse_dxf(in_file_path, walls_out_path, exits_out_path, generators_out_path, targets_out_path, servers_out_path,
              target_zone_spacing=DEFAULT_TARGET_ZONE_SPACING_M,
              target_zone_threshold=DEFAULT_TARGET_ZONE_THRESHOLD_M2):
    print("Initializing parsing over the dxf file...")
    doc = ezdxf.readfile(in_file_path)
    msp = doc.modelspace()

    print("\tParsing walls...")
    parse_layer_and_write_to_file(
        msp, WALLS_LAYER, ['LINE', 'POLYLINE', 'LWPOLYLINE'], walls_out_path)

    print("\tParsing exits...")
    parse_layer_and_write_to_file(
        msp, EXITS_LAYER, ['LINE', 'POLYLINE', 'LWPOLYLINE'], exits_out_path)

    print("\tParsing generators...")
    parse_layer_and_write_to_file(msp, GENERATORS_LAYER, [
                                  'POLYLINE', 'LWPOLYLINE'], generators_out_path, figures_can_be_rectangles=True)

    print("\tParsing targets...")
    parse_layer_and_write_to_file(
        msp, TARGETS_LAYER, ['POLYLINE', 'LWPOLYLINE', 'CIRCLE'], targets_out_path, figures_can_be_rectangles=True,
        target_zone_spacing=target_zone_spacing, target_zone_threshold=target_zone_threshold)

    print("\tParsing servers...")
    parse_layer_and_write_to_file(msp, SERVERS_LAYER, [
                                  'LINE', 'POLYLINE', 'LWPOLYLINE'], servers_out_path, figures_can_be_rectangles=True)

    print("Parsing of dxf file finished...")


DXF_PATH_EXAMPLE = "data/Plano-prueba-simulacion-V05.02.dxf"
DXF_PATH_EXAMPLE2 = "data/Plano-SREC-PB-simulacion-V04.01.dxf"

WALLS_PATH_EXAMPLE = "tmp/simulation_input/WALLS.csv"
EXITS_PATH_EXAMPLE = "tmp/simulation_input/EXITS.csv"
GENERATORS_PATH_EXAMPLE = "tmp/simulation_input/GENERATORS.csv"
TARGETS_PATH_EXAMPLE = "tmp/simulation_input/TARGETS.csv"
SERVERS_PATH_EXAMPLE = "tmp/simulation_input/SERVERS.csv"


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description="Parse a .dxf file to a the .csv files necessary for the program.")

    parser.add_argument("--dxf-path",
                        help="Path to the .dxf file to be used by the program to define the environment of the simulation. \
This file has to follow the requirements indicated on the README. \
Defaults to: " + DXF_PATH_EXAMPLE2,
                        type=str, default=DXF_PATH_EXAMPLE2, required=False)
    
    parser.add_argument("--output-walls-path",
                        help="Path where the walls data for the simulation will be saved in csv format. \
Defaults to: " + WALLS_PATH_EXAMPLE,
                        type=str, default=WALLS_PATH_EXAMPLE, required=False)
        
    parser.add_argument("--output-exits-path",
                        help="Path where the exits data for the simulation will be saved in csv format. \
Defaults to: " + EXITS_PATH_EXAMPLE,
                        type=str, default=EXITS_PATH_EXAMPLE, required=False)

    parser.add_argument("--output-generators-path",
                        help="Path where the generators data for the simulation will be saved in csv format. \
Defaults to: " + GENERATORS_PATH_EXAMPLE,
                        type=str, default=GENERATORS_PATH_EXAMPLE, required=False)
    
    parser.add_argument("--output-targets-path",
                        help="Path where the targets data for the simulation will be saved in csv format. \
Defaults to: " + TARGETS_PATH_EXAMPLE,
                        type=str, default=TARGETS_PATH_EXAMPLE, required=False)
    
    parser.add_argument("--output-servers-path",
                        help="Path where the servers data for the simulation will be saved in csv format. \
Defaults to: " + SERVERS_PATH_EXAMPLE,
                        type=str, default=SERVERS_PATH_EXAMPLE, required=False)

    parser.add_argument("--target-zone-spacing",
                        help=f"Spacing (m) between grid points for zone-like TARGETs (area > threshold). \
Default: {DEFAULT_TARGET_ZONE_SPACING_M} (≈ 1 person/m²).",
                        type=float, default=DEFAULT_TARGET_ZONE_SPACING_M, required=False)

    parser.add_argument("--target-zone-threshold",
                        help=f"Area threshold (m²) above which a TARGET is gridified. \
Default: {DEFAULT_TARGET_ZONE_THRESHOLD_M2} (gondolas ~1m² stay as single points; \
zones like dance floors, bars get gridified).",
                        type=float, default=DEFAULT_TARGET_ZONE_THRESHOLD_M2, required=False)

    args = parser.parse_args()
    parse_dxf(args.dxf_path, args.output_walls_path, args.output_exits_path, args.output_generators_path,
              args.output_targets_path, args.output_servers_path,
              target_zone_spacing=args.target_zone_spacing,
              target_zone_threshold=args.target_zone_threshold)
