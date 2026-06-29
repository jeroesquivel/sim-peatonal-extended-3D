# Tests — módulo Graph

Simulaciones y visualizaciones de prueba (no son tests JUnit del CI por ahora).

## Hop walkthrough (`nextVisibleHop` cada N metros)

Simula un agente que cada **2 m** consulta de nuevo `nextVisibleHop` y camina hacia el hop actual.

### 1. Compilar y correr simulación (Java)

Desde la raíz del repo:

```bash
javac -d target/classes -sourcepath src/main/java \
  src/main/java/ar/edu/itba/simped/environment/graph/*.java \
  src/main/java/ar/edu/itba/simped/environment/graph/tests/*.java

java -cp target/classes ar.edu.itba.simped.environment.graph.tests.HopWalkthrough
```

Argumentos opcionales: `startX startY targetX targetY stepMeters`

Ejemplo esquina inferior izquierda → sala superior derecha:

```bash
java -cp target/classes ar.edu.itba.simped.environment.graph.tests.HopWalkthrough \
  5 1 40 18 2
```

Genera:

- `tests/output/hop_walkthrough.csv` — eventos `START`, `HOP`, `MOVE`, `TARGET`
- `output/graph_nodes.csv` y `graph_edges.csv` (grafo base)

### 2. Visualizar

```bash
python src/main/java/ar/edu/itba/simped/environment/graph/test-scripts/visualize_hop_walkthrough.py
```

Produce `tests/output/hop_walkthrough.png` con:

- Grafo y paredes
- Servidores (azul)
- Línea verde: recorrido del agente
- Líneas naranjas punteadas: agente → cada hop asignado
- Marcadores: inicio (triángulo), hops (estrella), target (cruz)

### Todo en un comando

```bash
python src/main/java/ar/edu/itba/simped/environment/graph/test-scripts/run_hop_test.py
```

Compila si hace falta, ejecuta Java y genera el PNG.
