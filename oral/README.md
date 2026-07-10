# Blindaje para el oral — TP Final SdS (Grupo 5)

Preparación para la defensa del TP *"Ampliación a 3D de un simulador peatonal: multiplanta y
escaleras"*: **resúmenes** del informe, **documentación de código** con cada snippet citado y
verificado, y **blindaje** (Q&A).

> 🌐 **Web:** abrí [`index.html`](index.html) — todo en una página, indexado. Se genera con
> `python3 oral/build_html.py`.

## Por dónde empezar

1. **[Resumen breve](RESUMEN-BREVE.md)** — todo en una hoja. Empezá acá.
2. **[Resumen ejecutivo](00-resumen-ejecutivo.md)** — el pitch narrativo de 2 minutos.
3. **Resúmenes** en orden (siguen el informe) y **blindaje** del área más floja.

## Resúmenes del informe (`resumenes/`)

| # | Archivo | Contenido |
|---|---|---|
| 1 | [Introducción y observables](resumenes/01-introduccion-observables.md) | Objetivo, CPM/A-CPM, los 3 observables por ecuación |
| 2 | [Implementación](resumenes/02-implementacion.md) | **Hub técnico**: cambios por módulo, D1–D25, integración multiplanta |
| 3 | [Simulaciones](resumenes/03-simulaciones.md) | Mapa Escuela, parámetros, barridos, modo crisis |
| 4 | [Resultados](resumenes/04-resultados.md) | Las tres tablas, dos regímenes, bimodalidad, heatmaps |
| 5 | [Conclusiones](resumenes/05-conclusiones.md) | Las 5 conclusiones + frase de cierre |

## Documentación de código (`codigo/`)

Cada afirmación con su **snippet citado verbatim** (`archivo:línea`), verificado mecánicamente.

| # | Archivo | Módulos / decisiones |
|---|---|---|
| 1 | [Tipos base + I/O + Reproducibilidad](codigo/01-tipos-base-io.md) | `Vec3`/`Vec2`/`AgentState`/`Stairs`, output `z`, `Seeds` (D1–D4, D10, D23) |
| 2 | [Física: CPM / A-CPM](codigo/02-fisica-cpm.md) | `CpmOperationalModel`: escalera, `rmin` fix, anti-tunneling, carriles (D9, D17, D19, D22) |
| 3 | [Grafo de navegación](codigo/03-grafo.md) | A* 3D, FVP por planta, salto de `z`, deadlock de boca (D6, D11, D21, D24) |
| 4 | [Vecinos (CIM) + Geometría](codigo/04-vecinos-cim-geometria.md) | Grilla por planta + puente, paredes/vértices, Geometry por planta (D5, D8) |

## Blindaje / Q&A (`blindaje/`)

Preguntas fáciles y "trampa"; los **⚠️ Punto débil** marcan las limitaciones y cómo defenderlas.

| Archivo | Cubre | # |
|---|---|---|
| [Tipos base, I/O y reproducibilidad](blindaje/q-tipos-io.md) | `Vec3`, sin `vz`, `STAIRS.csv`, semilla, defaults `z=0` | 17 |
| [Física: CPM / A-CPM](blindaje/q-fisica-cpm.md) | CPM, A-CPM, escalera, livelock, modo crisis, `Δt` | 15 |
| [Grafo de navegación](blindaje/q-grafo.md) | unión de plantas, heurística admisible, salto de `z`, deadlock | 17 |
| [Vecinos (CIM) y Geometría](blindaje/q-vecinos-cim.md) | grilla por planta, puente, vértice por clamp, ids globales | 15 |
| [Escenario y modelado](blindaje/q-escenario-modelado.md) | Escuela, aulas CLASSROOM, zona del kiosco, modo crisis, barridos | 16 |
| [Resultados y estadística](blindaje/q-resultados-estadistica.md) | 5 realizaciones, dos regímenes, bimodalidad, remanente ≈1 | 15 |

## Citas de código y verificación

Cada bloque empieza con `// ruta:inicio-fin` (archivo fuente + rango exacto). Todos se verifican
mecánicamente con [`_verify_citations.py`](_verify_citations.py) — estado: **103 snippets OK, 0
mismatches**. El contenido pasó además una ronda de **verificación adversarial** contra el informe,
`DECISIONES.md` y el código.

```java
// src/main/java/ar/edu/itba/simped/core/Vec3.java:40-43
/** Distancia euclídea 3D (heurística admisible del A* multiplanta). */
public double distanceTo(Vec3 other) {
    return sub(other).norm();
}
```

## Fuentes

- [informe/informe.tex](../informe/informe.tex) — el informe (fuente de todos los números).
- [.claude/DECISIONES.md](../.claude/DECISIONES.md) — decisiones D1–D25.
- [.claude/ENUNCIADO.md](../.claude/ENUNCIADO.md) — el enunciado.
- [presentacion/GUION.md](../presentacion/GUION.md) — guion de la presentación.
