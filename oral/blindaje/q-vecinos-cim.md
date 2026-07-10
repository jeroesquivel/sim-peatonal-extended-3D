# Blindaje — Vecinos (CIM por planta) y Geometría

Preguntas que un profesor podría hacer sobre la detección de vecinos (CIM por planta,
[D8](../../.claude/DECISIONES.md)) y la geometría por planta ([D5](../../.claude/DECISIONES.md)).
Cada respuesta con su cita al código o al informe. Los snippets verbatim están en
[código: Vecinos y geometría](../codigo/04-vecinos-cim-geometria.md).

---

### P: ¿Qué es el CIM y por qué lo usan?

El **Cell Index Method** consulta vecinos en tiempo casi constante en vez de O(N²): divide el
espacio en celdas de lado `≥ rmax` y cada agente compara sólo contra su celda y las 8 adyacentes
(barrido 3×3, `CimNeighborsIndex.java:129-161`). Las paredes estáticas se indexan una vez en el
constructor y los agentes se re-indexan de forma perezosa tras cada `update`
(`CimNeighborsIndex.java:18-29`). Lo consumen la física (CPM) y los sensores.

---

### P: ¿Por qué una grilla por planta y no una sola?

Porque el CIM indexa en 2D `(x,y)` y en la Escuela las aulas de PB y P1 comparten `(x,y)`: con una
sola grilla, un alumno de la planta baja "vería" y esquivaría al de la planta alta a través de la
losa. [D8](../../.claude/DECISIONES.md) mantiene **una `CimNeighborsIndex` por planta** (cada una con
`wallsOn(z)`), orquestadas por `FloorAwareNeighborsIndex`, cumpliendo el enunciado (*"la detección de
vecinos es independiente por planta"*, [informe §Implementación](../../informe/informe.tex)).

---

### P: ¿Cómo se ven dos agentes que están sobre la misma escalera?

Ambos viven en el mismo **puente** (`StairBridge`): un agente cuya `z` está entre plantas se
clasifica en el puente de la escalera cuya huella contiene su `(x,y)`
(`FloorAwareNeighborsIndex.java:242-260`). Al pedir vecinos consulta su puente más las dos grillas de
planta de los extremos (`FloorAwareNeighborsIndex.java:187-193`), así dos agentes del mismo tramo se
detectan estén donde estén.

---

### P: ¿Y un agente que está en la boca de la escalera, todavía en la planta?

Lo cubre el acople simétrico: un agente **sobre una planta** consulta su grilla más los puentes de
todas las escaleras que aterrizan en ella (`stairsByFloor.get(fi)`,
`FloorAwareNeighborsIndex.java:183-186`). El acople es bidireccional en los descansos, así que no hay
punto ciego en la transición planta↔escalera.

---

### P: ¿Por qué no una grilla 3D con celdas también en `z`?

Porque las celdas verticales sólo agregan overhead: los pisos son **planos** y su separación
**discreta** (0 y 3 m), así que casi todas quedarían vacías, y la única `z` continua —la escalera— ya
la maneja el puente. Fue alternativa descartada en [D8](../../.claude/DECISIONES.md): *"las celdas
verticales no aportan (los pisos son planos y la separación entre plantas es discreta); complica sin
beneficio"* ([informe §Implementación](../../informe/informe.tex)).

---

### P: ¿Por qué no asignan al agente de escalera simplemente "la planta más cercana"?

Porque el snap produce una **discontinuidad en el medio del tramo**: dos agentes casi tocándose a
ambos lados del punto medio caerían en grillas distintas y no se detectarían, cuando el enunciado
pide explícito que en la escalera sí se vean. Por eso se eligió el índice puente
([D8](../../.claude/DECISIONES.md), alternativa descartada "Snap a la planta más cercana").

---

### P: El puente es fuerza bruta (O(k²)). ¿No es lento?

⚠️ **Punto débil (defendible).** Sí, el `StairBridge` hace un barrido lineal sin grilla
(`FloorAwareNeighborsIndex.java:277-305`), pero **k es chico** (el tramo es angosto y serializa el
flujo), así que O(k²) es más barato y simple que montar una grilla completa, con semántica de
distancia idéntica al CIM (distancia planar, umbral `rmax`). Si un escenario metiera cientos de
agentes en un tramo se le pondría una grilla sin tocar el resto; en los barridos (hasta N=500) el
puente nunca fue cuello de botella medible ([informe §Resultados](../../informe/informe.tex)).

---

### P: ¿Cómo se detecta una pared como vecino?

Por su **punto más cercano**: `w.distanceTo(selfPos)` (`CimNeighborsIndex.java:153-156`) usa
`Wall.distanceTo`, que da la distancia al punto más cercano del segmento (`Wall.java:33-35`); si es
`≤ rmax` entra como vecino de tipo `WALL`. El vecino no lleva la geometría sino un `id` (su índice)
que el CPM resuelve después contra la lista de paredes.

---

### P: ¿Y un vértice? ¿Cómo se detecta la esquina de una pared?

Con el **clamp** del parámetro de proyección: `closestPointTo` proyecta el agente sobre la recta del
muro y clampea `t` a `[0,1]` (`Wall.java:22-31`), de modo que cuando la proyección cae más allá de un
extremo el punto más cercano pasa a ser el endpoint `p1`/`p2` —el vértice—. Así un agente que rodea
una esquina la detecta como vecino, comportamiento del `Wall` original que la ampliación 3D preserva
sin cambios.

---

### P: Si los vecinos vienen filtrados por planta, ¿por qué los ids de pared son globales?

Porque el `Neighbor` de pared no lleva geometría (lleva `null`) sino un `id` = **índice**, y el
`CpmOperationalModel` hace `walls.get(id)` contra su propia lista; ids locales por planta romperían
esa resolución o lanzarían `IllegalStateException`. Por eso el facade mantiene una lista **global**
(concatenación de `wallsOn(z)` sobre `floors()`) y **reescribe** cada id `WALL` de local a global
(`remapWalls`, `FloorAwareNeighborsIndex.java:225-237`), la misma lista que App le pasa al OM vía
`globalWalls(Geometry)` (`FloorAwareNeighborsIndex.java:138-146`) en el orden de
`CpmOperationalModel.fromGeometry`. El id global **no** reintroduce paredes de otra planta (los
vecinos ya vienen filtrados); ver [D8](../../.claude/DECISIONES.md) y la
[física CPM §5](../codigo/02-fisica-cpm.md).

---

### P: ¿Qué pasa exactamente en el descanso (media altura)?

El descanso del switchback está a `z = 1.5` m: un agente ahí puede clasificarse en la grilla de ese
nivel o en el puente según su `z` y la huella que pisa (`classify`,
`FloorAwareNeighborsIndex.java:242-260`), pero como el acople es bidireccional (`neighborsOf`,
`FloorAwareNeighborsIndex.java:173-201`) ve tanto a los que suben por un tramo como a los que bajan
por el otro. El switchback se modela como **dos** `Stairs` encadenados por el descanso, reutilizando
la maquinaria multiplanta sin casos especiales
([informe §Implementación](../../informe/informe.tex)).

---

### P: ¿Qué es una "planta" para el código? ¿Cómo la identifican?

Por su **valor `z` (double)**, con tolerancia `FLOOR_EPS = 1e-6` para comparar y deduplicar
(`Geometry.java:54-55`); no hay índice entero. `floors()` deriva los niveles distintos de todos los
elementos de la geometría, ordenados y deduplicados (`Geometry.java:62-81`), y `wallsOn(z)`,
`exitsOn(z)`, etc. filtran por planta, como métodos `default` del puerto `Geometry`
([D5](../../.claude/DECISIONES.md)); se usó `z` porque los datos ya viven en `z`.

---

### P: ¿No sería más robusto un índice entero de planta (0, 1, 2…) en vez de un `double`?

Alternativa consultada y descartada en [D5](../../.claude/DECISIONES.md): agrega una capa de mapeo
`índice↔z` cuando los CSV ya traen la `z` real. El único riesgo (comparar flotantes) lo resuelve la
tolerancia `FLOOR_EPS` (`Geometry.java:76-77`, `84-89`); en la Escuela las plantas son
`z ∈ {0, 1.5, 3}`, valores exactos del builder, así que `FLOOR_EPS` nunca tuvo que desambiguar.

---

### P: ¿Cómo garantizan que un agente no aparezca en dos contenedores a la vez?

Cada agente vive en **exactamente un** contenedor, fijado por `classify` en cada `update`
(`FloorAwareNeighborsIndex.java:155-171`): si cambió, se lo saca del anterior y se lo agrega al nuevo.
Como los conjuntos son **disjuntos**, acoplar varios contenedores no puede devolver el mismo agente
dos veces y no hace falta deduplicar; la lista final se ordena por distancia
(`FloorAwareNeighborsIndex.java:196-199`), igual que en el CIM base.

---

### P: ¿Verificaron todo esto con tests? ¿Qué cubren?

Sí: la suite queda en **143 tests, 0 fallos, 0 omitidos**
([informe §Implementación](../../informe/informe.tex)) e incluye el **CIM por planta**: paridad con el
caso de una sola planta, detección de paredes por punto más cercano, vértices como vecinos y el
acople en las escaleras. La detección de vértices —lo que el enunciado pide explícito— queda cubierta
por el clamp de `closestPointTo` y su test.

---

### P: Limitación conocida — ¿qué pasa si dos huellas de escalera se solapan?

⚠️ **Punto débil (defendible).** `classify` asume que las huellas **no se solapan** en `(x,y)` y toma
la **primera** escalera cuyo rango `z` y huella contienen al agente
(`FloorAwareNeighborsIndex.java:247-258`); en la Escuela las dos escaleras están en puntas opuestas,
bien separadas, así que la condición se cumple siempre. Si un escenario declarara huellas
superpuestas, un agente en la zona común se asignaría a una de forma determinística pero arbitraria:
es una restricción razonable del modelo (dos escaleras físicas no ocupan el mismo lugar), no un bug,
documentada como supuesto del builder.

---

## Navegación

- Código de esta área: [Vecinos (CIM) y geometría](../codigo/04-vecinos-cim-geometria.md)
- Código hermano: [Física CPM/A-CPM](../codigo/02-fisica-cpm.md) ·
  [Grafo de navegación (A* 3D)](../codigo/03-grafo.md)
- Resumen: [Implementación](../resumenes/02-implementacion.md)
- Fuentes: [informe (LaTeX)](../../informe/informe.tex) ·
  [DECISIONES.md](../../.claude/DECISIONES.md) (D5, D8, D9)
