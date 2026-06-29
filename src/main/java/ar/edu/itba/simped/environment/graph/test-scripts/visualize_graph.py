"""
Visualizador del grafo de navegación (grupo Graph).

Uso (desde la raíz del repo):
    python src/main/java/ar/edu/itba/simped/environment/graph/test-scripts/visualize_graph.py

Requiere export previo:
    java -cp target/classes ar.edu.itba.simped.environment.graph.StubGraph
"""

from __future__ import annotations

import csv
import os
import argparse
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_GRAPH_PKG = os.path.dirname(_SCRIPT_DIR)
_OUTPUT_DIR = os.path.join(_GRAPH_PKG, "output")
_REPO_ROOT = os.path.abspath(os.path.join(_GRAPH_PKG, *([os.pardir] * 9)))


def parse_walls(path: str) -> list[tuple[float, float, float, float]]:
    walls = []
    with open(path, newline="") as f:
        reader = csv.reader(f)
        next(reader)
        for row in reader:
            if not row or not row[0].strip():
                continue
            walls.append((float(row[0]), float(row[1]), float(row[3]), float(row[4])))
    return walls


def parse_nodes(path: str) -> dict[int, tuple[float, float, int]]:
    nodes = {}
    with open(path, newline="") as f:
        reader = csv.reader(f)
        next(reader)
        for row in reader:
            if not row or not row[0].strip():
                continue
            ntype = int(row[3]) if len(row) > 3 and row[3].strip() else 0
            nodes[int(row[0])] = (float(row[1]), float(row[2]), ntype)
    return nodes


def parse_servers(path: str) -> list[tuple[str, float, float, float, float]]:
    servers = []
    if not path or not os.path.isfile(path):
        return servers
    with open(path, newline="") as f:
        reader = csv.reader(f)
        next(reader)
        for row in reader:
            if not row or not row[0].strip():
                continue
            name = row[0].strip()
            if not name.endswith("_SERVER"):
                continue
            servers.append((name, float(row[1]), float(row[2]), float(row[4]), float(row[5])))
    return servers


def parse_edges(path: str) -> list[tuple[int, int]]:
    edges = []
    with open(path, newline="") as f:
        reader = csv.reader(f)
        next(reader)
        for row in reader:
            if not row or not row[0].strip():
                continue
            edges.append((int(row[0]), int(row[1])))
    return edges


def plot_graph(walls_path: str, nodes_path: str, edges_path: str, output_png: str,
               servers_path: str | None = None, title: str = "Grafo de Navegación"):
    walls = parse_walls(walls_path)
    nodes = parse_nodes(nodes_path)
    edges = parse_edges(edges_path)
    servers = parse_servers(servers_path) if servers_path else []

    fig, ax = plt.subplots(1, 1, figsize=(14, 7))
    ax.set_aspect("equal")
    ax.set_facecolor("#f5f5f5")

    for x1, y1, x2, y2 in walls:
        ax.plot([x1, x2], [y1, y2], color="black", linewidth=2.5, solid_capstyle="round")

    for name, x1, y1, x2, y2 in servers:
        rx, ry = min(x1, x2), min(y1, y2)
        ax.add_patch(plt.Rectangle((rx, ry), abs(x2 - x1), abs(y2 - y1), fill=True,
                                   facecolor="#4a90d9", edgecolor="#1a4a7a", linewidth=1.5,
                                   alpha=0.35, zorder=2))
        ax.text(rx + abs(x2 - x1) / 2, ry + abs(y2 - y1) / 2, name.replace("_SERVER", ""),
                ha="center", va="center", fontsize=6, color="#0a2a4a", zorder=3)

    for i, j in edges:
        if i in nodes and j in nodes:
            ax.plot([nodes[i][0], nodes[j][0]], [nodes[i][1], nodes[j][1]],
                    color="#8b0000", linewidth=1.2, alpha=0.7, zorder=4)

    # Tipos: 0 = área (bordó), 1 = conector (violeta), 2 = servidor (amarillo borde rojo)
    type_style = {
        0: dict(color="#8b0000", s=90, edgecolors="#4a0000", label="Nodos de área"),
        1: dict(color="#7b3fa0", s=90, edgecolors="#3a1a55", label="Conectores"),
        2: dict(color="#ffd400", s=90, edgecolors="red", label="Servidores"),
    }
    counts = {0: 0, 1: 0, 2: 0}
    for _, _, t in nodes.values():
        counts[t] = counts.get(t, 0) + 1
    for t, style in type_style.items():
        xs = [p[0] for p in nodes.values() if p[2] == t]
        ys = [p[1] for p in nodes.values() if p[2] == t]
        ax.scatter(xs, ys, color=style["color"], s=style["s"], zorder=5,
                   edgecolors=style["edgecolors"], linewidths=1.2)

    ax.legend(handles=[
        mpatches.Patch(color="black", label=f"Paredes ({len(walls)})"),
        mpatches.Patch(color="#4a90d9", alpha=0.5, label=f"Servidores ({len(servers)})"),
        mpatches.Patch(color="#8b0000", label=f"Nodos de área ({counts.get(0, 0)})"),
        mpatches.Patch(color="#7b3fa0", label=f"Conectores ({counts.get(1, 0)})"),
        mpatches.Patch(color="#ffd400", label=f"Nodos servidor ({counts.get(2, 0)})"),
        mpatches.Patch(color="#8b0000", alpha=0.7, label=f"Aristas ({len(edges)})"),
    ], loc="upper right", fontsize=8)
    ax.set_title(title, fontsize=13, fontweight="bold")
    ax.set_xlabel("x (m)")
    ax.set_ylabel("y (m)")
    ax.grid(True, alpha=0.2)
    plt.tight_layout()
    os.makedirs(os.path.dirname(output_png), exist_ok=True)
    plt.savefig(output_png, dpi=150)
    print(f"Saved to {output_png}")


def main():
    parser = argparse.ArgumentParser(description="Visualiza el grafo de navegación")
    parser.add_argument("--walls", default=os.path.join(_REPO_ROOT, "scenarios", "example", "WALLS.csv"))
    parser.add_argument("--nodes", default=os.path.join(_OUTPUT_DIR, "graph_nodes.csv"))
    parser.add_argument("--edges", default=os.path.join(_OUTPUT_DIR, "graph_edges.csv"))
    parser.add_argument("--servers", default=os.path.join(_REPO_ROOT, "scenarios", "example", "SERVERS.csv"))
    parser.add_argument("--out", default=os.path.join(_OUTPUT_DIR, "navigation_graph.png"))
    parser.add_argument("--title", default="Grafo de Navegación")
    args = parser.parse_args()
    plot_graph(args.walls, args.nodes, args.edges, args.out, args.servers, args.title)


if __name__ == "__main__":
    main()
