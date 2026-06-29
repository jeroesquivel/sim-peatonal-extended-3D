# Integración con Geometry (I17)

Hoy el grafo se construye leyendo CSV como **mock** de `Geometry` (5.1, grupo T4).

## Flujo actual (mock)

```java
StubGraph graph = StubGraph.fromScenarioFiles(
    "scenarios/example/WALLS.csv",
    "scenarios/example/SERVERS.csv");
```

## Flujo objetivo (I17)

```java
StubGraph graph = StubGraph.fromGeometry(geometry);
```

`Geometry` debería exponer:

1. **Paredes** — segmentos `(x1,y1)-(x2,y2)`.
2. **Zonas de servidor** — rectángulos `*_SERVER` (excluir nodos dentro; nodo de aproximación afuera).

### API propuesta 

```java
public interface Geometry {
    List<WallSegment> walls();
    List<ServerZone> serverZones();
}
```

### Implementación futura en GraphBuilder

```java
public static NavigationGraph fromGeometry(Geometry geometry) {
    List<Wall> walls = geometry.walls().stream()
        .map(w -> new Wall(new Vec2(w.x1(), w.y1()), new Vec2(w.x2(), w.y2())))
        .toList();
    List<ServerRect> servers = geometry.serverZones().stream()
        .map(z -> new GraphBuilder.ServerRect(z.name(), z.p1(), z.p2()))
        .toList();
    return build(walls, servers, DEFAULT_GRID_SPACING);
}
```

Hoy `fromGeometry` lanza `UnsupportedOperationException` hasta que exista esa API.

## Salidas de debug

CSV/PNG en `environment/graph/output/` (ver `StubGraph.OUTPUT_DIR`).