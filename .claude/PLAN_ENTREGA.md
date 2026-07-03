# PLAN_ENTREGA.md — Plan para dejar el TP entregable y aprobable

Ampliación a 3D del simulador peatonal (escenario **Escuela**). Este archivo lista **todo lo que
falta** para una entrega completa y aprobable, ordenado y con criterios de "hecho".

> Reglas del proyecto (recordatorio): toda decisión de arquitectura se registra en
> [`DECISIONES.md`](./DECISIONES.md) (hoy D1–D18); Claude **no commitea** (lo hace el usuario);
> modelo físico siempre **CPM**. Enunciado completo en [`ENUNCIADO.md`](./ENUNCIADO.md).

## Progreso (orquesta, 2026-07-02)
- **Task 1 — Infra de barrido: ✅ COMPLETA** (semilla `-Dsimped.seed`, `tools/sweep_run.py` + `tools/sweep_lib.py`).
- **Task 2 — Evacuación: ✅ COMPLETA** (modo `evacuacion` en el builder, `tools/plot_evacuacion.py`,
  barrido N∈{40,80,120}, figuras `out/evac_hist.png` + `out/evac_scalar.png`). Al hacerla se encontró y
  arregló un bug de migración 3D: la `z` del spawn de generadores se perdía → **D18**.
- **Task 3 — Escalera "Completo": ✅ COMPLETA** (switchback + descanso z=1.5 + confinamiento + carriles
  de contraflujo + peldaños + velocidad 0.59 m/s → **D19**). Se encontró y arregló el "salto de z" al
  pisar la escalera (excluir la huella de la grilla del grafo) → **D21**.
- **Task 4 — Ingreso: ✅ COMPLETA** (caudal 1/5/10 min con Nmax=120, kiosco en el plan, observable
  población-vs-tiempo en el kiosco, `tools/plot_ingreso.py` → **D20**; extra `tools/run_ingreso_nmax.py`
  para variar Nmax).
- **Task 5 — Mapas de calor: ✅ COMPLETA** (`tools/heatmaps.py`: densidad por planta + t_evac por origen).
- **Task 6 — Informe: ✅ COMPLETA** (`informe/informe.pdf` en LaTeX, 9 páginas, con todos los observables).
- **Task 7 — Higiene y cierre: ✅** artefactos de grupos A–I eliminados (builders, smoke tests,
  g4-visualizer, dxf legacy, .md de coordinación), `pom.xml`/`README.md` despersonalizados,
  `mvn clean test` **141 verdes** (0 skipped). Falta sólo el **commit** (lo hace el usuario).

---

## Estado actual (ya hecho)

- **Núcleo 3D (pasos 1–7):** `Vec3` + `AgentState.z`, input propaga `z` + escaleras, `Geometry` por
  planta, **grafo 3D** por planta unido por escaleras (A* con heurística euclídea 3D), **CIM por planta**,
  **CPM 3D** (z en escaleras + velocidad reducida + paredes de la planta actual), output con `z`.
- **Escenario Escuela (paso 8.1):** 2 plantas, 16 aulas, pasillos, **2 escaleras**, recreo con kiosco.
  Aulas como **servers `CLASSROOM`** con **planes diferenciados** PB/P1 y **timbre único** (D15/D16).
- **CPM afinado:** contacto de pared al núcleo duro (rmin) → sin atascos en esquinas/jambas (D17).
- **Animaciones:** 2D por planta + **vista 3D 45°** apilada (MP4, `tools/`), gráfico de población.
- **Suite: 149 tests verdes.** Decisiones registradas D1–D17.

---

## Checklist de requisitos del enunciado (para "aprobable")

| Requisito del enunciado | Estado |
|---|---|
| Agentes con `z ≠ 0` (distintas plantas / recorriendo la escalera) | ✅ |
| Velocidad menor en las escaleras | ✅ |
| Detección de vecinos independiente por planta (+ paredes y vértices como vecinos) | ✅ |
| Grafo de navegación 3D: generador por planta + unión por escaleras | ✅ |
| Usar siempre CPM | ✅ |
| A* con heurística = distancia euclídea 3D (x,y,z) | ✅ |
| Animación 2D por planta (círculos) + vista 3D a 45° | ✅ |
| Escenario Escuela (≥2 plantas, aulas, pasillos, escaleras, recreo + kiosco) | ✅ |
| **Sub-escenario Evacuación** (input N; observable: distribución de t_evac; escalar: prom/máx) | ⬜ Task 2 |
| **Sub-escenario Ingreso** (input caudal 1/5/10 min; observable: población vs t; escalar: ocupación) | ⬜ Task 4 |
| **Scripts para variar el input y generar los gráficos** de los observables | ⬜ Task 1 |
| (Opcional) Mapas de calor de densidad / de tiempos de evacuación | ⬜ Task 5 |
| Informe / presentación de la entrega | ⬜ Task 6 |

---

## Plan de tareas (ordenado)

### Task 1 — Infraestructura de barrido y agregación *(base de los sub-escenarios)*
- **Objetivo:** poder correr la sim variando un input, con varias réplicas (semillas), y agregar
  resultados de forma reproducible. El enunciado lo pide explícito ("scripts para ejecutar variando el
  input y generar los gráficos").
- **Subtareas:**
  - Parametrizar `build_escuela.py` para recibir el input del barrido (N, caudal/ventana, semilla) por
    CLI y emitir un escenario por combinación.
  - **Semilla reproducible:** hoy el generador y varios módulos usan `new Random()` sin seed → cada
    corrida difiere. Exponer una semilla (env/prop) para réplicas controladas. *(Registrar en DECISIONES.)*
  - Runner en `tools/` que compile una vez y ejecute la grilla de (input × réplicas), guardando cada
    `output.csv` en `out/sweeps/<escenario>/<input>/<seed>.csv`.
  - Módulo Python de carga/agregación de métricas por corrida (reutilizable por Tasks 2 y 4).
- **Hecho cuando:** un comando corre el barrido completo y deja los CSV organizados + un CSV/JSON de
  métricas agregadas.
- **Artefactos:** `tools/sweep_run.py`, `tools/sweep_lib.py` (o similar). **Esfuerzo:** M.

### Task 2 — Sub-escenario **Evacuación**
- **Objetivo:** alumnos ya dentro de las aulas (ambas plantas) que evacúan a las salidas.
- **Subtareas:**
  - Variante del builder con **ocupación inicial**: N alumnos repartidos en aulas de PB y P1 desde t=0
    (sin ventana de ingreso). Opciones: generadores `instant_occupation` en cada aula, o timbre en t=0.
    Las dos salidas con `exit_selection` (los de P1 bajan por escalera).
  - **Input:** capacidad total `N` (barrer, p. ej., 20 / 40 / 60 / 80… hasta saturar). Define el `Nmax`
    que usa Ingreso.
  - **Observable primario:** distribución de tiempos de evacuación `t_evac = t_salida − t_inicio` por
    agente → **histograma**.
  - **Escalar:** t_evac promedio y máximo vs `N` → **curva**.
  - Extraer `t_evac` por agente del output (última fila antes de desaparecer − primera fila).
- **Hecho cuando:** para cada `N` hay histograma de t_evac y una curva prom/máx vs `N`, reproducibles vía Task 1.
- **Artefactos:** escenario(s) `scenarios/escuela_evac_*`, `tools/plot_evacuacion.py`, figuras en `out/`.
- **Esfuerzo:** M. **Depende de:** Task 1.

### Task 3 — **Mejorar el diseño de la escalera (más realista)**
- **Objetivo:** que la escalera sea más creíble físicamente, no un simple segmento recto de eje. Hoy
  (D4/D9) es un eje `(pie→tope)` con `width` y `speed_factor`; el agente proyecta a `xy` e interpola `z`.
- **Subtareas (a evaluar, elegir alcance):**
  - **Geometría más realista:** descanso/landing intermedio o tramo en **L (switchback)** en vez de un
    único tramo recto; revisar la pendiente (hoy ~5 m en `xy` para 3 m de subida) y el ancho.
  - **Confinamiento:** paredes/baranda que mantengan al agente dentro de la huella de la escalera (hoy
    puede desviarse del eje); revisar el comportamiento en **contraflujo** (subida y bajada a la vez) y,
    si hace falta, separar carriles de subida/bajada.
  - **Velocidad:** calibrar `speed_factor` a un valor realista de escalera (efectiva ~0.5–0.6 m/s) y
    verificar que la interpolación de `z` sea monótona con el avance.
  - **Visualización:** dibujar la escalera como **peldaños/rampa** (no una línea punteada) en la vista 3D
    y marcarla en el 2D de cada planta.
  - Actualizar el builder y `STAIRS.csv` si cambia el formato; **registrar la decisión en DECISIONES.md**
    (nueva D) y verificar que grafo (unión por escaleras), CIM (puente) y CPM (interpolación) sigan bien.
- **Hecho cuando:** la escalera se ve y se comporta de forma más realista (geometría + animación) sin
  romper el ruteo multiplanta ni los tests; decisión registrada.
- **Artefactos:** `core/Stairs`, `STAIRS.csv`, `build_escuela.py`, scripts de viz; nueva entrada en DECISIONES.
- **Esfuerzo:** M–L. **Depende de:** nada duro (se hace después de Tasks 1–2 para no frenar los observables).

### Task 4 — Sub-escenario **Ingreso**
- **Objetivo:** los `Nmax` alumnos llegan y se dirigen a sus aulas; medir la congestión.
- **Subtareas:**
  - **Input:** caudal — los `Nmax` distribuidos en **1 / 5 / 10 min** (variar `ARRIVAL_WINDOW`,
    manteniendo `Nmax`).
  - **Kiosco en el plan** (recreo antes de clase): variante de plan que pasa por el server `KIOSCO`
    (QUEUE) antes del aula — acá el kiosco entra naturalmente.
  - **Observable primario:** curva de **población vs tiempo** en una zona de interés (p. ej. el área
    antes de la escalera principal / el pasillo). Requiere contar agentes dentro de un rectángulo por frame.
  - **Escalar:** ocupación **máxima** y **promedio** en esa zona vs caudal → curva/barras.
- **Hecho cuando:** para cada caudal hay curva de población vs t en la zona y su ocupación máx/prom.
- **Artefactos:** escenario(s) `scenarios/escuela_ingreso_*`, `tools/plot_ingreso.py`, figuras.
- **Esfuerzo:** M. **Depende de:** Tasks 1 y 2 (Nmax).

### Task 5 — (Opcional) Mapas de calor
- **Objetivo:** los extras del enunciado, suman a la nota.
- **Subtareas:** mapa de **densidad** (grilla, promedio de ocupación por celda) por planta; para
  Evacuación, mapa de **tiempos de evacuación** (colorear el origen de cada agente por su `t_evac`, o
  promediar por celda).
- **Hecho cuando:** hay al menos un heatmap de densidad + el mapa de t_evac.
- **Artefactos:** `tools/heatmaps.py`, figuras. **Esfuerzo:** M. **Depende de:** Tasks 2/4.

### Task 6 — Informe / presentación de la entrega
- **Objetivo:** documento entregable que explique qué se hizo y muestre los resultados (lo que vuelve
  "aprobable" el TP más allá del código).
- **Subtareas:** modelo y decisiones (resumen de D1–D17 + escalera), descripción de la Escuela, resultados
  de Evacuación e Ingreso (con las figuras), conclusiones. Reusar `DECISIONES.md` como fuente.
- **Hecho cuando:** informe con las figuras de Tasks 2/4/5 y la explicación del modelo 3D.
- **Artefactos:** `informe/` (PDF/MD) o presentación. **Esfuerzo:** M–L. **Depende de:** Tasks 2, 4, (5).

### Task 7 — Higiene y cierre
- **Objetivo:** dejar el repo limpio y reproducible.
- **Subtareas:**
  - Limpiar rastros del trabajo por grupos (`.claude/LIMPIEZA_RASTROS_GRUPOS.md`): tests/builders viejos
    de escenarios A–I.
  - Verificación final: `mvn clean test` verde; README con los comandos de reproducción de cada figura.
  - **Commit** de todo (lo hace el usuario) — hoy hay ~11 archivos sin commitear de las sesiones previas.
- **Hecho cuando:** build limpio, README reproducible, repo commiteado.
- **Esfuerzo:** S. **Depende de:** todo lo anterior.

---

## Orden sugerido y dependencias

```
Task 1 (barrido)  →  Task 2 (Evacuación)  →  Task 3 (escalera realista)  →  Task 4 (Ingreso)
                                                                              →  Task 5 (heatmaps, opc.)
                                                                              →  Task 6 (informe)
                                                                              →  Task 7 (cierre + commit)
```

La **escalera realista (Task 3)** va después de tener el barrido y Evacuación andando: así no frena la
producción de observables y, cuando se toque la geometría de la escalera, ya hay métricas para verificar
que no se rompe nada.

## Riesgos / notas
- **Reproducibilidad (semillas):** varios módulos usan `new Random()` sin seed; sin exponer semillas, las
  réplicas del barrido no son comparables. Resolver en Task 1 (decisión en DECISIONES.md).
- **Timbre único (Formato B):** las aulas `CLASSROOM` liberan una sola vez (D15); alcanza para Evacuación
  (un release) e Ingreso (el interés está antes del timbre), pero un "día con varias clases" pediría
  Formato A.
- **Formato B necesita Jackson** → correr con `mvn exec:java` (no el `-jar`). El build del grafo de la
  Escuela tarda ~1 min por corrida: tenerlo en cuenta al dimensionar los barridos.
