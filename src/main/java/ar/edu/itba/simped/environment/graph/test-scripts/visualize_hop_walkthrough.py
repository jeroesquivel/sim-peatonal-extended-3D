"""
Visualiza un recorrido con nextVisibleHop cada N metros.

Lee tests/output/hop_walkthrough.csv generado por HopWalkthrough.java.
"""

import csv
import os
import argparse
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_GRAPH_PKG = os.path.dirname(_SCRIPT_DIR)
_GRAPH_OUTPUT = os.path.join(_GRAPH_PKG, "output")
_TESTS_OUTPUT = os.path.join(_GRAPH_PKG, "tests", "output")
_REPO_ROOT = os.path.abspath(os.path.join(_GRAPH_PKG, *([os.pardir] * 9)))


def load_walkthrough(path: str) -> list[tuple[int, str, float, float, list[int]]]:
    events = []
    with open(path, newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            astar = []
            ap = row.get("astar_path", "") or ""
            if ap.strip():
                astar = [int(x) for x in ap.split(";") if x.strip()]
            events.append((
                int(row["seq"]),
                row["kind"],
                float(row["x"]),
                float(row["y"]),
                astar,
            ))
    return events


def parse_walls(path: str):
    walls = []
    with open(path, newline="") as f:
        reader = csv.reader(f)
        next(reader)
        for row in reader:
            if row and row[0].strip():
                walls.append((float(row[0]), float(row[1]), float(row[3]), float(row[4])))
    return walls


def parse_nodes(path: str) -> dict[int, tuple[float, float]]:
    nodes = {}
    with open(path, newline="") as f:
        reader = csv.reader(f)
        next(reader)
        for row in reader:
            if row and row[0].strip():
                nodes[int(row[0])] = (float(row[1]), float(row[2]))
    return nodes


def parse_edges(path: str) -> list[tuple[int, int]]:
    edges = []
    with open(path, newline="") as f:
        reader = csv.reader(f)
        next(reader)
        for row in reader:
            if row and row[0].strip():
                edges.append((int(row[0]), int(row[1])))
    return edges


def parse_servers(path: str):
    servers = []
    if not os.path.isfile(path):
        return servers
    with open(path, newline="") as f:
        reader = csv.reader(f)
        next(reader)
        for row in reader:
            if not row or not row[0].strip() or not row[0].strip().endswith("_SERVER"):
                continue
            servers.append((row[0], float(row[1]), float(row[2]), float(row[4]), float(row[5])))
    return servers


def plot_hop_walkthrough(events, walls, nodes, edges, servers, out_png: str):
    fig, ax = plt.subplots(figsize=(14, 7))
    ax.set_aspect("equal")
    ax.set_facecolor("#f5f5f5")

    for x1, y1, x2, y2 in walls:
        ax.plot([x1, x2], [y1, y2], color="black", linewidth=2.5, zorder=1)

    for name, x1, y1, x2, y2 in servers:
        rx, ry = min(x1, x2), min(y1, y2)
        ax.add_patch(plt.Rectangle((rx, ry), abs(x2 - x1), abs(y2 - y1), facecolor="#4a90d9",
                                   edgecolor="#1a4a7a", alpha=0.35, zorder=2))

    for i, j in edges:
        if i in nodes and j in nodes:
            ax.plot([nodes[i][0], nodes[j][0]], [nodes[i][1], nodes[j][1]],
                    color="red", linewidth=0.4, alpha=0.35, zorder=3)

    moves = [(x, y) for _, k, x, y, _ in events if k == "MOVE"]
    start = next(((x, y) for _, k, x, y, _ in events if k == "START"), None)
    target = next(((x, y) for _, k, x, y, _ in events if k == "TARGET"), None)

    hop_queries: list[tuple[tuple[float, float], tuple[float, float], list[int]]] = []
    last_pos = start
    for _, kind, x, y, astar in events:
        if kind == "MOVE":
            last_pos = (x, y)
        elif kind == "HOP" and last_pos is not None:
            hop_queries.append((last_pos, (x, y), astar))

    # Subgrafo A*: una vez por hop distinto (primera consulta de cada fase)
    astar_colors = ["#8e44ad", "#2980b9"]
    seen_hop_pos: set[tuple] = set()
    astar_phase = 0
    for agent_pos, hop_pos, astar in hop_queries:
        key = (round(hop_pos[0], 4), round(hop_pos[1], 4))
        if key in seen_hop_pos or len(astar) < 2:
            continue
        seen_hop_pos.add(key)
        color = astar_colors[astar_phase % len(astar_colors)]
        astar_phase += 1
        for i in range(len(astar) - 1):
            a, b = astar[i], astar[i + 1]
            if a in nodes and b in nodes:
                ax.plot([nodes[a][0], nodes[b][0]], [nodes[a][1], nodes[b][1]],
                        color=color, linewidth=1.0, alpha=0.25, zorder=4)

    if len(moves) >= 1:
        path = [start] + moves if start else moves
        ax.plot([p[0] for p in path], [p[1] for p in path], color="#27ae60", linewidth=2.8,
                marker="o", markersize=4, zorder=10)

    # líneas tenues agente → hop de cada consulta (muestran la dispersión del hop aleatorio)
    for agent_pos, hop_pos, _ in hop_queries:
        ax.plot([agent_pos[0], hop_pos[0]], [agent_pos[1], hop_pos[1]], color="#e67e22",
                linewidth=0.7, linestyle="--", alpha=0.3, zorder=5)

    # hops distintos (solo marcadores, sin carteles)
    unique_hops: dict[tuple[float, float], int] = {}
    ordered_hops: list[tuple[float, float]] = []
    for _, hop_pos, _ in hop_queries:
        key = (round(hop_pos[0], 4), round(hop_pos[1], 4))
        unique_hops[key] = unique_hops.get(key, 0) + 1
        if key not in ordered_hops:
            ordered_hops.append(key)
    if ordered_hops:
        ax.scatter([h[0] for h in ordered_hops], [h[1] for h in ordered_hops],
                   marker="*", s=90, color="#f39c12", edgecolors="#a04000",
                   linewidths=0.8, zorder=11)

    n_queries = len(hop_queries)

    if start:
        ax.plot(start[0], start[1], marker="^", color="#3498db", markersize=14, zorder=12)
    if target:
        ax.plot(target[0], target[1], marker="X", color="#9b59b6", markersize=14, zorder=12)
    title_extra = ""
    if start and target:
        title_extra = f" — ({start[0]:g}, {start[1]:g}) → ({target[0]:g}, {target[1]:g})"
    ax.set_title("nextVisibleHop — consulta cada 2 m" + title_extra, fontsize=12, fontweight="bold")
    ax.set_xlabel("x (m)")
    ax.set_ylabel("y (m)")
    ax.grid(True, alpha=0.2)
    plt.tight_layout()
    os.makedirs(os.path.dirname(out_png), exist_ok=True)
    plt.savefig(out_png, dpi=130)
    print(f"Saved to {out_png}")
    print(f"  Consultas: {n_queries}, hops distintos: {len(ordered_hops)}, "
          f"fases A*: {astar_phase}, pasos MOVE: {len(moves)}")
    for hi, (hx, hy) in enumerate(ordered_hops, start=1):
        print(f"    H{hi}: ({hx}, {hy}) ×{unique_hops[(hx, hy)]}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--walkthrough", default=os.path.join(_TESTS_OUTPUT, "hop_walkthrough.csv"))
    parser.add_argument("--walls", default=os.path.join(_REPO_ROOT, "scenarios", "example", "WALLS.csv"))
    parser.add_argument("--servers", default=os.path.join(_REPO_ROOT, "scenarios", "example", "SERVERS.csv"))
    parser.add_argument("--nodes", default=os.path.join(_GRAPH_OUTPUT, "graph_nodes.csv"))
    parser.add_argument("--edges", default=os.path.join(_GRAPH_OUTPUT, "graph_edges.csv"))
    parser.add_argument("--out", default=os.path.join(_TESTS_OUTPUT, "hop_walkthrough.png"))
    args = parser.parse_args()

    plot_hop_walkthrough(
        load_walkthrough(args.walkthrough),
        parse_walls(args.walls),
        parse_nodes(args.nodes),
        parse_edges(args.edges),
        parse_servers(args.servers),
        args.out,
    )


if __name__ == "__main__":
    main()
