"""
Animación de la salida de simulación (out/output.csv) sobre el escenario.

Opcionalmente superpone target final y hops de nextVisibleHop (out/hops.csv generado con
-Dsimped.hopLog=out/hops.csv).

Uso (desde la raíz del repo):
    mvn package -DskipTests
    java "-Dsimped.hopLog=out/hops.csv" -cp "target/simped-1.0.0-SNAPSHOT.jar;target/lib/*" ar.edu.itba.simped.App
    python tools/visualize_simulation.py --hops out/hops.csv --dt 0.05

Requisitos: pip install matplotlib pillow
"""

from __future__ import annotations

import argparse
import csv
import os
from collections import defaultdict

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.animation as animation
import matplotlib.patches as mpatches

STATE_COLORS = {
    "IDLE": "#95a5a6",
    "WALKING": "#27ae60",
    "APPROACHING": "#2980b9",
    "ARRIVED": "#8e44ad",
    "OCCUPYING": "#e67e22",
    "LEAVING": "#d35400",
    "QUEUEING": "#c0392b",
}

TRACK_COLORS = ["#e74c3c", "#9b59b6", "#1abc9c", "#f39c12", "#34495e", "#16a085"]

STAIR_FOOTPRINT_COLOR = "#b9770e"  # distinto del naranja de hops (#e67e22) para no confundir


FLOOR_EPS = 1e-6


def _floor_match(z: float, floor: float | None) -> bool:
    return floor is None or abs(z - floor) <= 1e-6


def parse_walls(path: str, floor: float | None = None) -> list[tuple[float, float, float, float]]:
    # WALLS.csv: x1, y1, z1, x2, y2, z2. Con `floor` se filtran las paredes de esa planta.
    walls = []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader, None)
        for row in reader:
            if not row or not row[0].strip():
                continue
            z = float(row[2])
            if not _floor_match(z, floor):
                continue
            walls.append((float(row[0]), float(row[1]), float(row[3]), float(row[4])))
    return walls


def parse_servers(path: str, floor: float | None = None) -> list[tuple[str, float, float, float, float]]:
    # SERVERS.csv: block_name, x1, y1, z1, x2, y2, z2. `floor` filtra por planta (z1).
    if not os.path.isfile(path):
        return []
    servers = []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader, None)
        for row in reader:
            if not row or not row[0].strip():
                continue
            name = row[0].strip()
            if not name.endswith("_SERVER"):
                continue
            if not _floor_match(float(row[3]), floor):
                continue
            servers.append((name, float(row[1]), float(row[2]), float(row[4]), float(row[5])))
    return servers


def parse_stairs_2d(path: str, floor: float | None = None) -> list[tuple[float, float, float, float, float]]:
    """STAIRS.csv: block,x1,y1,z1,x2,y2,z2,width[,speed] -> (x1,y1,x2,y2,width).

    Con `floor` se quedan sólo los tramos cuyo pie o tope estén en esa planta
    (para marcar la huella de la escalera al animar esa planta en 2D). Sin
    `floor` devuelve todos los tramos.
    """
    stairs = []
    if not os.path.isfile(path):
        return stairs
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader, None)
        for row in reader:
            if not row or not row[0].strip():
                continue
            z1, z2 = float(row[3]), float(row[6])
            if floor is not None and not (_floor_match(z1, floor) or _floor_match(z2, floor)):
                continue
            width = float(row[7]) if len(row) > 7 and row[7].strip() else 1.2
            stairs.append((float(row[1]), float(row[2]), float(row[4]), float(row[5]), width))
    return stairs


def stair_footprint_corners(
    x1: float, y1: float, x2: float, y2: float, width: float
) -> list[tuple[float, float]] | None:
    """Los 4 vértices del rectángulo de huella de un tramo (eje pie->tope, ancho `width`)."""
    dx, dy = x2 - x1, y2 - y1
    length = (dx * dx + dy * dy) ** 0.5
    if length < 1e-9:
        return None
    nx, ny = -dy / length, dx / length  # versor perpendicular al eje
    hw = width / 2.0
    return [
        (x1 + nx * hw, y1 + ny * hw),
        (x2 + nx * hw, y2 + ny * hw),
        (x2 - nx * hw, y2 - ny * hw),
        (x1 - nx * hw, y1 - ny * hw),
    ]


def _cross(o: tuple[float, float], a: tuple[float, float], b: tuple[float, float]) -> float:
    return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])


def _on_segment(p: tuple[float, float], a: tuple[float, float], b: tuple[float, float], eps: float = 1e-9) -> bool:
    return (
        min(a[0], b[0]) - eps <= p[0] <= max(a[0], b[0]) + eps
        and min(a[1], b[1]) - eps <= p[1] <= max(a[1], b[1]) + eps
    )


def segments_intersect(
    a1: tuple[float, float], a2: tuple[float, float], b1: tuple[float, float], b2: tuple[float, float]
) -> bool:
    eps = 1e-9
    d1 = _cross(b1, b2, a1)
    d2 = _cross(b1, b2, a2)
    d3 = _cross(a1, a2, b1)
    d4 = _cross(a1, a2, b2)
    if ((d1 > eps and d2 < -eps) or (d1 < -eps and d2 > eps)) and (
        (d3 > eps and d4 < -eps) or (d3 < -eps and d4 > eps)
    ):
        return True
    if abs(d1) <= eps and _on_segment(a1, b1, b2):
        return True
    if abs(d2) <= eps and _on_segment(a2, b1, b2):
        return True
    if abs(d3) <= eps and _on_segment(b1, a1, a2):
        return True
    if abs(d4) <= eps and _on_segment(b2, a1, a2):
        return True
    return False


def is_visible(a: tuple[float, float], b: tuple[float, float], walls: list[tuple[float, float, float, float]]) -> bool:
    for x1, y1, x2, y2 in walls:
        if segments_intersect(a, b, (x1, y1), (x2, y2)):
            return False
    return True


def parse_sim_output(path: str, floor: float | None = None) -> dict[float, list[dict]]:
    # Formato D10: tout; x; y; z; vx; vy; state; id. Con `floor` se quedan sólo
    # los agentes de esa planta (z ≈ floor).
    frames: dict[float, list[dict]] = defaultdict(list)
    with open(path, newline="", encoding="utf-8-sig") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = [p.strip() for p in line.split(";")]
            if len(parts) < 7:
                continue
            tout = float(parts[0])
            z = float(parts[3])
            if not _floor_match(z, floor):
                continue
            frames[tout].append({
                "x": float(parts[1]),
                "y": float(parts[2]),
                "z": z,
                "vx": float(parts[4]),
                "vy": float(parts[5]),
                "state": parts[6],
            })
    return dict(sorted(frames.items()))


def parse_hops(path: str) -> list[dict]:
    hops: list[dict] = []
    if not path or not os.path.isfile(path):
        return hops
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            seq = int(row["seq"]) if "seq" in row else len(hops)
            vis_java = int(row["visJava"]) if row.get("visJava", "") != "" else None
            hops.append({
                "seq": seq,
                "px": float(row["px"]), "py": float(row["py"]),
                "tx": float(row["tx"]), "ty": float(row["ty"]),
                "hx": float(row["hx"]), "hy": float(row["hy"]),
                "direct": int(row.get("direct", 0)),
                "vis_java": vis_java,
            })
    return hops


def track_agents(frames: dict[float, list[dict]]) -> list[list[dict]]:
    times = sorted(frames.keys())
    if not times:
        return []

    tracked: list[list[dict]] = []
    prev: list[dict] = []
    for t in times:
        agents = [dict(a) for a in frames[t]]
        if not prev:
            for i, a in enumerate(agents):
                a["track_id"] = i
        else:
            used: set[int] = set()
            for a in agents:
                best_j = None
                best_d2 = float("inf")
                for j, p in enumerate(prev):
                    if j in used:
                        continue
                    dx = a["x"] - p["x"]
                    dy = a["y"] - p["y"]
                    d2 = dx * dx + dy * dy
                    if d2 < best_d2:
                        best_d2 = d2
                        best_j = j
                if best_j is None:
                    a["track_id"] = max((p.get("track_id", 0) for p in prev), default=-1) + 1
                else:
                    used.add(best_j)
                    a["track_id"] = prev[best_j]["track_id"]
                if best_d2 > 4.0:
                    a["track_id"] = max((p.get("track_id", 0) for p in prev), default=-1) + len(used)
        tracked.append(agents)
        prev = agents
    return tracked


def assign_hop_times(hops: list[dict], n_agents: int, dt: float) -> None:
    n = max(1, n_agents)
    for h in hops:
        h["t"] = (h["seq"] // n) * dt


def match_hops_to_tracks(
    hops: list[dict],
    frames: list[list[dict]],
    walls: list[tuple[float, float, float, float]],
) -> dict[int, list[dict]]:
    """Asigna cada hop a un track_id emparejando (px,py) con el agente más cercano en el frame de t similar."""
    if not hops or not frames:
        return {}

    times = [f[0]["tout"] if f else 0.0 for f in frames]
    by_track: dict[int, list[dict]] = defaultdict(list)
    used: set[int] = set()

    for hop in hops:
        t = hop["t"]
        best_i = min(range(len(times)), key=lambda i: abs(times[i] - t))
        frame = frames[best_i]
        if not frame:
            continue
        best_agent = None
        best_d2 = float("inf")
        for a in frame:
            dx = hop["px"] - a["x"]
            dy = hop["py"] - a["y"]
            d2 = dx * dx + dy * dy
            if d2 < best_d2:
                best_d2 = d2
                best_agent = a
        if best_agent is None or best_d2 > 2.0:
            continue
        tid = best_agent["track_id"]
        hop = dict(hop)
        hop["track_id"] = tid
        # Preferir la verdad de Java (visJava) cuando esté; el cálculo Python usa el CSV
        # redondeado a 6 decimales y marca falsos positivos en hops rasantes a esquinas.
        if hop.get("vis_java") is not None:
            hop["visible_hop"] = bool(hop["vis_java"])
        else:
            hop["visible_hop"] = is_visible((hop["px"], hop["py"]), (hop["hx"], hop["hy"]), walls)
        hop["visible_target"] = is_visible((hop["px"], hop["py"]), (hop["tx"], hop["ty"]), walls)
        by_track[tid].append(hop)

    for tid in by_track:
        by_track[tid].sort(key=lambda h: h["t"])
    return dict(by_track)


def hops_at_second(by_track: dict[int, list[dict]], second: int) -> dict[int, dict]:
    """Último hop registrado en el intervalo [second, second+1) por track."""
    out: dict[int, dict] = {}
    for tid, lst in by_track.items():
        candidates = [h for h in lst if int(h["t"]) == second]
        if candidates:
            out[tid] = candidates[-1]
    return out


def bounds_from_walls(walls: list[tuple[float, float, float, float]], margin: float = 1.0):
    xs, ys = [], []
    for x1, y1, x2, y2 in walls:
        xs.extend([x1, x2])
        ys.extend([y1, y2])
    if not xs:
        return 0.0, 50.0, 0.0, 25.0
    return min(xs) - margin, max(xs) + margin, min(ys) - margin, max(ys) + margin


def render_video(
    frames: list[list[dict]],
    walls: list[tuple[float, float, float, float]],
    servers: list[tuple[str, float, float, float, float]],
    out_path: str,
    fps: int,
    fmt: str,
    dpi: int,
    hops_by_track: dict[int, list[dict]] | None,
    hop_sample_sec: float,
    stairs: list[tuple[float, float, float, float, float]] | None = None,
):
    if not frames:
        raise SystemExit("No hay frames en el archivo de salida.")

    xmin, xmax, ymin, ymax = bounds_from_walls(walls)
    fig, ax = plt.subplots(figsize=(14, 8))
    ax.set_aspect("equal")
    ax.set_facecolor("#f5f5f5")
    ax.set_xlim(xmin, xmax)
    ax.set_ylim(ymin, ymax)

    for x1, y1, x2, y2 in walls:
        ax.plot([x1, x2], [y1, y2], color="black", linewidth=2.5, solid_capstyle="round", zorder=1)

    # Huella de escalera: rectángulo tenue (sólo referencia visual, no es una pared).
    for x1, y1, x2, y2, width in stairs or []:
        corners = stair_footprint_corners(x1, y1, x2, y2, width)
        if corners is None:
            continue
        ax.add_patch(
            plt.Polygon(
                corners,
                closed=True,
                fill=True,
                facecolor=STAIR_FOOTPRINT_COLOR,
                edgecolor=STAIR_FOOTPRINT_COLOR,
                linewidth=0.8,
                alpha=0.18,
                zorder=1.5,
            )
        )

    for _name, x1, y1, x2, y2 in servers:
        rx, ry = min(x1, x2), min(y1, y2)
        ax.add_patch(
            plt.Rectangle(
                (rx, ry),
                abs(x2 - x1),
                abs(y2 - y1),
                fill=True,
                facecolor="#4a90d9",
                edgecolor="#1a4a7a",
                linewidth=1.2,
                alpha=0.25,
                zorder=2,
            )
        )

    scatter = ax.scatter([], [], s=120, zorder=12, edgecolors="white", linewidths=0.8)
    time_text = ax.text(
        0.02, 0.98, "", transform=ax.transAxes, va="top", fontsize=11,
        bbox=dict(boxstyle="round", facecolor="white", alpha=0.85),
    )

    # Líneas agente→hop / agente→target y marcadores de hop (se recrean cada frame)
    hop_artists: list = []
    target_artists: list = []
    trail_artists: list = []

    show_hops = hops_by_track is not None and len(hops_by_track) > 0

    # Leyenda: los ESTADOS de agente que realmente aparecen en la corrida (en
    # orden de ciclo de vida) + las entradas de hops sólo si se muestran hops.
    # Se ubica FUERA del área del plot (margen derecho) para no solaparse nunca
    # con el recuadro de tiempo (arriba-izquierda).
    present_states = {a["state"] for frame in frames for a in frame}
    state_order = ["IDLE", "WALKING", "APPROACHING", "ARRIVED", "OCCUPYING", "LEAVING", "QUEUEING"]
    legend_handles = [
        mpatches.Patch(color=STATE_COLORS[s], label=s)
        for s in state_order if s in present_states
    ]
    if show_hops:
        legend_handles += [
            mpatches.Patch(color="#e67e22", label="Hop (nextVisibleHop)"),
            mpatches.Patch(color="#8e44ad", label="Target final"),
            mpatches.Patch(color="#c0392b", label="Hop NO visible (pared)"),
        ]
    fig.subplots_adjust(right=0.80)
    ax.legend(handles=legend_handles, loc="upper left", bbox_to_anchor=(1.02, 1.0),
              fontsize=8, title="Estado del agente", framealpha=0.95)
    ax.set_xlabel("x (m)")
    ax.set_ylabel("y (m)")
    ax.set_title("Simulación peatonal" + (" + hops del grafo" if show_hops else ""))
    ax.grid(True, alpha=0.2)

    def clear_overlay():
        for art in hop_artists + target_artists + trail_artists:
            art.remove()
        hop_artists.clear()
        target_artists.clear()
        trail_artists.clear()

    def update_with_time(i: int):
        agents = frames[i]
        xs = [a["x"] for a in agents]
        ys = [a["y"] for a in agents]
        colors = [STATE_COLORS.get(a["state"], "#333333") for a in agents]
        if xs:
            scatter.set_offsets(list(zip(xs, ys)))
            scatter.set_facecolors(colors)
            scatter.set_sizes([120] * len(agents))
        tout = agents[0].get("tout", i * 0.1) if agents else i * 0.1

        clear_overlay()

        if show_hops:
            max_sec = int(tout)
            for sec in range(0, max_sec + 1):
                if sec % int(hop_sample_sec) != 0 and sec != max_sec:
                    continue
                snap = hops_at_second(hops_by_track, sec)
                for tid, h in snap.items():
                    color = TRACK_COLORS[tid % len(TRACK_COLORS)]
                    # Líneas desde (px,py): posición en la que el grafo evaluó visibilidad,
                    # NO desde la posición actual del agente (que ya se movió).
                    query_xy = (h["px"], h["py"])

                    hop_color = "#e67e22" if h.get("visible_hop", True) else "#c0392b"
                    trail_artists.append(
                        ax.scatter(
                            [h["hx"]], [h["hy"]], s=90, marker="*",
                            c=hop_color, edgecolors="black", linewidths=0.5, zorder=9, alpha=0.85,
                        )
                    )
                    target_artists.append(
                        ax.scatter(
                            [h["tx"]], [h["ty"]], s=140, marker="X",
                            c="#8e44ad", edgecolors=color, linewidths=1.2, zorder=8, alpha=0.9,
                        )
                    )
                    trail_artists.append(
                        ax.scatter(
                            [query_xy[0]], [query_xy[1]], s=35, marker="o",
                            facecolors="none", edgecolors=color, linewidths=1.2, zorder=10,
                        )
                    )
                    hop_artists.append(
                        ax.plot(
                            [query_xy[0], h["hx"]], [query_xy[1], h["hy"]],
                            color=hop_color, linestyle="--", linewidth=1.0, alpha=0.75, zorder=7,
                        )[0]
                    )
                    target_artists.append(
                        ax.plot(
                            [query_xy[0], h["tx"]], [query_xy[1], h["ty"]],
                            color="#8e44ad", linestyle=":", linewidth=0.8, alpha=0.45, zorder=6,
                        )[0]
                    )

            bad = sum(
                1 for lst in hops_by_track.values() for h in lst
                if h["t"] <= tout and not h.get("visible_hop", True)
            )
            time_text.set_text(
                f"t = {tout:.1f} s  |  agentes = {len(agents)}  |  hops no visibles (hasta t): {bad}"
            )
        else:
            time_text.set_text(f"t = {tout:.1f} s  |  agentes = {len(agents)}")

        return scatter, time_text

    anim = animation.FuncAnimation(fig, update_with_time, frames=len(frames), interval=1000 / fps, blit=False)

    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or ".", exist_ok=True)
    if fmt == "gif":
        writer = animation.PillowWriter(fps=fps)
        anim.save(out_path, writer=writer, dpi=dpi)
    else:
        writer = animation.FFMpegWriter(fps=fps)
        anim.save(out_path, writer=writer, dpi=dpi)
    plt.close(fig)
    print(f"Guardado: {out_path}")


def load_frames_with_times(path: str, floor: float | None = None) -> list[list[dict]]:
    raw = parse_sim_output(path, floor)
    times = sorted(raw.keys())
    tracked = track_agents(raw)
    for t, frame in zip(times, tracked):
        for a in frame:
            a["tout"] = t
    return tracked


def main():
    repo = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir))
    parser = argparse.ArgumentParser(description="Genera video/GIF desde out/output.csv")
    parser.add_argument("--output", default=os.path.join(repo, "out", "output.csv"))
    parser.add_argument("--scenario", default=os.path.join(repo, "scenarios", "example"))
    parser.add_argument("--out", default=os.path.join(repo, "out", "simulation.gif"))
    parser.add_argument("--hops", default=os.path.join(repo, "out", "hops.csv"),
                        help="CSV de LoggingGraph (-Dsimped.hopLog=...); vacío para omitir")
    parser.add_argument("--dt", type=float, default=0.05, help="dt de simulación para estimar t del hop")
    parser.add_argument("--hop-sample-sec", type=float, default=1.0,
                        help="Mostrar hops cada N segundos de simulación")
    parser.add_argument("--fps", type=int, default=10, help="Frames por segundo del video")
    parser.add_argument("--dpi", type=int, default=120)
    parser.add_argument("--format", choices=("gif", "mp4"), default=None)
    parser.add_argument("--floor", type=float, default=None,
                        help="Planta (z) a animar en 2D; omitir para mostrar todas superpuestas")
    parser.add_argument("--stride", type=int, default=1,
                        help="Submuestrear 1 de cada N frames (outputs largos)")
    args = parser.parse_args()

    fmt = args.format or ("mp4" if args.out.lower().endswith(".mp4") else "gif")
    walls_path = os.path.join(args.scenario, "WALLS.csv")
    servers_path = os.path.join(args.scenario, "SERVERS.csv")
    stairs_path = os.path.join(args.scenario, "STAIRS.csv")

    frames = load_frames_with_times(args.output, args.floor)
    if args.stride > 1:
        frames = frames[::args.stride]  # submuestreo para outputs largos
    walls = parse_walls(walls_path, args.floor)
    servers = parse_servers(servers_path, args.floor)
    stairs = parse_stairs_2d(stairs_path, args.floor)

    hops_by_track: dict[int, list[dict]] | None = None
    hop_list = parse_hops(args.hops) if args.hops else []
    if hop_list:
        n_agents = max(len(f) for f in frames) if frames else 1
        assign_hop_times(hop_list, n_agents, args.dt)
        hops_by_track = match_hops_to_tracks(hop_list, frames, walls)
        if any(h.get("vis_java") is not None for h in hop_list):
            invisible = sum(1 for h in hop_list if h.get("vis_java") == 0)
            src = "Java"
        else:
            invisible = sum(1 for h in hop_list if not is_visible((h["px"], h["py"]), (h["hx"], h["hy"]), walls))
            src = "Python/CSV"
        print(f"Hops: {len(hop_list)}, tracks={len(hops_by_track)}, no visibles ({src}): {invisible}")

    print(f"Frames: {len(frames)}, escenario: {args.scenario}")
    render_video(
        frames, walls, servers, args.out, args.fps, fmt, args.dpi,
        hops_by_track, args.hop_sample_sec, stairs,
    )


if __name__ == "__main__":
    main()
