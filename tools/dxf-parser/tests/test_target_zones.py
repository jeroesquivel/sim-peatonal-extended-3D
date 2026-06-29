"""Tests para la gridificación de TARGETs de zona (iter 8, spec 12-15).

Tests independientes de ezdxf: invocan directamente las funciones del parser
con figuras sintéticas que respetan el formato interno de `get_blocks_figures`:
- RECTANGLE: [name, x1, y1, z1, x3, y3, z3]
- CIRCLE:    [name, radius, cx, cy, cz]
"""

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from parser import (
    DEFAULT_TARGET_ZONE_SPACING_M,
    DEFAULT_TARGET_ZONE_THRESHOLD_M2,
    _expand_target_zones,
    _gridify_circle,
    _gridify_rectangle,
    _should_gridify,
)


def _rect(name, x1, y1, x2, y2):
    return [name, x1, y1, 0, x2, y2, 0]


def _circle(name, radius, cx, cy):
    return [name, radius, cx, cy, 0]


def test_rectangle_above_threshold_gridifies():
    figures = [_rect("PISTA", 0, 0, 15, 10)]
    expanded = _expand_target_zones(figures, spacing=1.0, threshold=2.0)
    assert len(expanded) == 150
    for row in expanded:
        assert row[0] == "PISTA"
        assert row[1] == 0.0   # radius placeholder
        assert row[4] == 0.0   # z


def test_rectangle_below_threshold_passes_through():
    # 1x1 = 1 m² < 2 m² → no gridificar
    figures = [_rect("GONDOLA", 0, 0, 1, 1)]
    expanded = _expand_target_zones(figures, spacing=1.0, threshold=2.0)
    assert expanded == figures


def test_rectangle_exactly_at_threshold_passes_through():
    # área = 2 m² == umbral → comparación es estricta (>) → pasa tal cual
    figures = [_rect("BORDERLINE", 0, 0, 2, 1)]
    expanded = _expand_target_zones(figures, spacing=1.0, threshold=2.0)
    assert expanded == figures


def test_circle_above_threshold_gridifies():
    figures = [_circle("DOMO", 3.0, 5.0, 5.0)]   # area ≈ 28.27 m²
    expanded = _expand_target_zones(figures, spacing=1.0, threshold=2.0)
    # Esperado: ~π·R²/s² puntos, clippeados al disco.
    # AABB 6x6 con s=1.0 → 36 puntos en el AABB; clip al disco filtra esquinas.
    # Cota: 28 ± algunos puntos por la grilla discreta.
    assert 20 <= len(expanded) <= 36
    for row in expanded:
        assert row[0] == "DOMO"
        px, py = row[2], row[3]
        # confirmar que todos los puntos están dentro del disco
        assert (px - 5.0) ** 2 + (py - 5.0) ** 2 <= 9.0 + 1e-9


def test_circle_below_threshold_passes_through():
    figures = [_circle("WAYPOINT", 0.5, 0, 0)]   # area ≈ 0.785 m² < 2
    expanded = _expand_target_zones(figures, spacing=1.0, threshold=2.0)
    assert expanded == figures


def test_gridified_points_share_block_name():
    figures = [_rect("BARRA", 0, 0, 6, 1.5)]
    expanded = _expand_target_zones(figures, spacing=1.0, threshold=2.0)
    assert len(expanded) > 1
    assert all(row[0] == "BARRA" for row in expanded)


def test_gridified_points_are_circles_radius_zero():
    figures = [_rect("PISTA", 0, 0, 5, 5)]
    expanded = _expand_target_zones(figures, spacing=1.0, threshold=2.0)
    # cada fila debe tener 5 elementos = [name, radius, cx, cy, cz]
    for row in expanded:
        assert len(row) == 5
        assert row[1] == 0.0


def test_rectangle_smaller_than_spacing_passes_through():
    # 0.5 × 3 = 1.5 m² < 2 → no entra al gridify (pasa tal cual)
    figures = [_rect("THIN", 0, 0, 0.5, 3)]
    expanded = _expand_target_zones(figures, spacing=1.0, threshold=2.0)
    assert expanded == figures


def test_linear_zone_above_threshold_gridifies_to_single_row():
    # GONDOLA 4×0.8 = 3.2 m² > 2 m² → gridificar.
    # height=0.8 < spacing=1.0 → forzar ny=1 → línea de 4 puntos en y media.
    figures = [_rect("GONDOLA", 0, 0, 4, 0.8)]
    expanded = _expand_target_zones(figures, spacing=1.0, threshold=2.0)
    assert len(expanded) == 4
    for row in expanded:
        assert row[0] == "GONDOLA"
        # todos los puntos en la mitad de la altura
        assert math.isclose(row[3], 0.4)


def test_linear_zone_y_above_threshold():
    # Misma cosa pero rectángulo orientado en y: 0.8 × 4 → 4 puntos verticales
    figures = [_rect("WALL_TARGET", 0, 0, 0.8, 4)]
    expanded = _expand_target_zones(figures, spacing=1.0, threshold=2.0)
    assert len(expanded) == 4
    for row in expanded:
        assert math.isclose(row[2], 0.4)


def test_zero_padding_when_perfect_fit():
    # 3x3 con s=1.0 → 9 puntos en (0.5,0.5), (1.5,0.5), ..., (2.5,2.5)
    figures = [_rect("ZONA", 0, 0, 3, 3)]
    expanded = _expand_target_zones(figures, spacing=1.0, threshold=2.0)
    assert len(expanded) == 9
    coords = sorted((round(row[2], 6), round(row[3], 6)) for row in expanded)
    expected = sorted(
        (i + 0.5, j + 0.5) for i in range(3) for j in range(3)
    )
    assert coords == expected


def test_e_discoteca_barra_with_default_spacing():
    # BARRA 6×1.5 con s=1.0 → nx=6, ny=1 → 6 puntos (caso documentado en spec 12)
    figures = [_rect("BARRA", 3.0, 22.0, 9.0, 23.5)]
    expanded = _expand_target_zones(figures, spacing=1.0, threshold=2.0)
    assert len(expanded) == 6


def test_e_discoteca_barra_with_finer_spacing():
    # BARRA 6×1.5 con s=0.75 → nx=8, ny=2 → 16 puntos (ejemplo del prompt)
    figures = [_rect("BARRA", 3.0, 22.0, 9.0, 23.5)]
    expanded = _expand_target_zones(figures, spacing=0.75, threshold=2.0)
    assert len(expanded) == 16


def test_grid_is_centered_in_rectangle():
    # 5×1 con s=1.0 → nx=5, ny=1, sin padding en x; padding y=0 (height=1, ny=1)
    # Puntos: (0.5,0.5), (1.5,0.5), ..., (4.5,0.5)
    points = _gridify_rectangle(0, 0, 5, 1, spacing=1.0)
    assert len(points) == 5
    for i, (px, py) in enumerate(points):
        assert math.isclose(px, i + 0.5)
        assert math.isclose(py, 0.5)


def test_grid_centered_with_non_integer_width():
    # 7.5×1 con s=1.0 → nx=7, ny=1. pad_x = (7.5 - 7)/2 = 0.25
    # Primer punto: 0 + 0.25 + 0.5 = 0.75. Último: 0.25 + 6.5 = 6.75.
    points = _gridify_rectangle(0, 0, 7.5, 1, spacing=1.0)
    assert len(points) == 7
    assert math.isclose(points[0][0], 0.75)
    assert math.isclose(points[-1][0], 6.75)


def test_circle_grid_clipped_to_disc():
    # Círculo radio 2 en origen: AABB 4×4 con s=1.0 → 16 puntos en AABB.
    # Clip al disco r²=4: filtra esquinas (1.5, 1.5) que dan d²=4.5>4.
    points = _gridify_circle(0, 0, 2.0, spacing=1.0)
    # los 4 puntos esquina (±1.5, ±1.5) tienen d²=4.5 → fuera. 16-4=12.
    assert len(points) == 12
    for (px, py) in points:
        assert px * px + py * py <= 4.0 + 1e-9


def test_should_gridify_rectangle():
    assert _should_gridify(_rect("BIG", 0, 0, 10, 10), threshold=2.0)
    assert not _should_gridify(_rect("SMALL", 0, 0, 1, 1), threshold=2.0)


def test_should_gridify_circle():
    assert _should_gridify(_circle("DOMO", 2.0, 0, 0), threshold=2.0)
    assert not _should_gridify(_circle("DOT", 0.5, 0, 0), threshold=2.0)


def test_defaults_documented():
    assert DEFAULT_TARGET_ZONE_SPACING_M == 1.0
    assert DEFAULT_TARGET_ZONE_THRESHOLD_M2 == 2.0
