#!/usr/bin/env python3
"""
Compila (si hace falta), ejecuta HopWalkthrough y genera hop_walkthrough.png.

Uso (desde la raíz del repo):
    python src/main/java/ar/edu/itba/simped/environment/graph/test-scripts/run_hop_test.py

Argumentos opcionales se reenvían a Java:
    startX startY targetX targetY stepMeters
"""

import os
import subprocess
import sys

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_GRAPH_PKG = os.path.dirname(_SCRIPT_DIR)
_REPO_ROOT = os.path.abspath(os.path.join(_GRAPH_PKG, *([os.pardir] * 9)))
_TARGET = os.path.join(_REPO_ROOT, "target", "classes")
_GRAPH_JAVA = os.path.join(_REPO_ROOT, "src", "main", "java", "ar", "edu", "itba", "simped", "environment", "graph")
_TESTS_JAVA = os.path.join(_GRAPH_JAVA, "tests")


def compile_java():
    sources = []
    for root, _, files in os.walk(_GRAPH_JAVA):
        for f in files:
            if f.endswith(".java"):
                sources.append(os.path.join(root, f))
    print("Compilando", len(sources), "archivos Java...")
    subprocess.run(
        ["javac", "-d", _TARGET, "-sourcepath", os.path.join(_REPO_ROOT, "src", "main", "java")] + sources,
        check=True,
        cwd=_REPO_ROOT,
    )


def run_hop_walkthrough(extra_args: list[str]):
    cmd = [
        "java", "-cp", _TARGET,
        "ar.edu.itba.simped.environment.graph.tests.HopWalkthrough",
        *extra_args,
    ]
    print("Ejecutando:", " ".join(cmd))
    subprocess.run(cmd, check=True, cwd=_REPO_ROOT)


def visualize():
    viz = os.path.join(_SCRIPT_DIR, "visualize_hop_walkthrough.py")
    subprocess.run([sys.executable, viz], check=True, cwd=_REPO_ROOT)


def main():
    os.makedirs(_TARGET, exist_ok=True)
    try:
        compile_java()
    except subprocess.CalledProcessError:
        sys.exit(1)

    extra = sys.argv[1:]
    if not extra:
        extra = ["5", "1", "40", "18", "2"]

    run_hop_walkthrough(extra)
    visualize()


if __name__ == "__main__":
    main()
