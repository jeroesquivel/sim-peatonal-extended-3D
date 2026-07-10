# Resumen §Conclusiones

Cinco conclusiones del informe, con su enlace.

## 1. El simulador quedó ampliado a 3D de punta a punta

Agentes con `z ≠ 0` por escaleras a velocidad reducida, vecinos y física **por planta**, grafo 3D (A* euclídeo unido por escaleras), salida con `z`, animación 2D + vista 3D apilada. → [implementación](02-implementacion.md)

## 2. La escalera switchback es coherente; el artefacto del salto de `z` se resolvió

Switchback con descanso, confinamiento y contraflujo = comportamiento coherente. El "salto de `z`" se **diagnosticó** (ruteo, no render) **y resolvió** (D21). → [grafo: salto de z](../codigo/03-grafo.md#salto-de-z)

## 3. Evacuación: vaciado completo y dos regímenes

Vaciado **completo** (`N` hasta 500). Dos regímenes: **suave** (escaleras absorben, `N ≲ 200`) → **drenaje de cola** en bocas saturadas (`N ≳ 300`, máximo despegado del promedio). En el cruce, sensibilidad máxima a la realización (N=300: una corrida **duplicó** el máximo a 427 s). **Bimodal**: PB rápido, P1 lento que crece y domina. → [resultados: evacuación](04-resultados.md#1-evacuación)

## 4. Ingreso: congestión que sube al acortar la ventana, y saturación del kiosco

Ocupación crece al **acortar la ventana** (mayor caudal). Complementario: **más que proporcional** con `N_max` hasta saturar el kiosco (`N_max ≈ 180`), luego **≈lineal** (server a tasa fija, excedente en cola). → [resultados: ingreso](04-resultados.md#2-ingreso)

## 5. El remanente ≈1 agente no evacuado NO crece con `N`

≈1/corrida no evacúa y **no crece con `N`**: casi siempre el **último en tránsito** al terminar (caso borde de `tevac`) y, esporádico, el *livelock* de jamba en PB. Sin atascos permanentes; **límite conocido del CPM**, un agente aislado. → [blindaje: el remanente](../blindaje/q-resultados-estadistica.md) · [física: livelock](../codigo/02-fisica-cpm.md#5-fix-del-contacto-de-pared-al-núcleo-duro-rmin-d17)

---

## Cómo cerrar el oral (frase de síntesis)

> "Ampliamos el simulador a 3D con un enfoque híbrido —`Vec3` para la posición, `Vec2` para la dinámica planar— que mantuvo intacto el núcleo del CPM. El desafío real no fueron los tipos sino la **integración**: la `z` que se perdía, el salto de `z` y el *livelock* de jamba, diagnosticados con datos y corregidos. Sobre eso, la Escuela muestra dos fenómenos de manual —saturación de escaleras (evacuación) y del kiosco (ingreso)— cuantificados con 5 realizaciones."

---

### Navegación

- ⬅ [Resumen §Resultados](04-resultados.md) · [§Simulaciones](03-simulaciones.md) · [§Implementación](02-implementacion.md)
- [Resumen ejecutivo (pitch)](../00-resumen-ejecutivo.md) · [Índice](../README.md)
- [informe](../../informe/informe.tex) · [`DECISIONES.md`](../../.claude/DECISIONES.md)
