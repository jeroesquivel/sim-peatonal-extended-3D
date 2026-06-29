# Módulo Graph (grupo navegación)

Paquete: `ar.edu.itba.simped.environment.graph`

| Clase | Rol |
|-------|-----|
| **`StubGraph`** | Punto de entrada — `Graph.nextVisibleHop` |
| `NavigationGraph` | Malla interna (A*, FVP) |
| `GraphBuilder` | Construcción del grafo |

## Estructura

```
environment/graph/
├── *.java
├── README.md, GEOMETRY_INTEGRATION.md
├── output/                 # Grafo exportado (nodes/edges CSV)
├── tests/                  # Simulaciones Java
│   ├── README.md
│   ├── HopWalkthrough.java
│   └── output/             # hop_walkthrough.csv, .png
└── test-scripts/           # Python
    ├── visualize_graph.py
    ├── visualize_hop_walkthrough.py
    └── run_hop_test.py       # compila + simula + visualiza hops
```

## Grafo base

```bash
javac -d target/classes -sourcepath src/main/java \
  src/main/java/ar/edu/itba/simped/environment/graph/*.java

java -cp target/classes ar.edu.itba.simped.environment.graph.StubGraph
python src/main/java/ar/edu/itba/simped/environment/graph/test-scripts/visualize_graph.py
```

## Test de hops (cada 2 m)

```bash
python src/main/java/ar/edu/itba/simped/environment/graph/test-scripts/run_hop_test.py
```

Ver [tests/README.md](tests/README.md).
