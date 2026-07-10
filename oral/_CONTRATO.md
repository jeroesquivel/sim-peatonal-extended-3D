<!-- ARCHIVO INTERNO DE COORDINACIÓN — no forma parte del entregable final.
     Define el contrato que TODOS los agentes deben respetar al escribir sus .md. -->

# Contrato de la carpeta `oral/` — blindaje para el oral

Estás construyendo, junto con otros agentes en paralelo, una **carpeta de preparación para el
oral** del TP Final de Simulación de Sistemas: *"Ampliación a 3D de un simulador peatonal:
multiplanta y escaleras"*. El objetivo es que los tres integrantes puedan defender el trabajo
respondiendo cualquier pregunta, con **cada afirmación técnica respaldada por el código real
citado**.

## Fuentes de verdad (leerlas, NO inventar)

- **Informe**: `informe/informe.tex` — la fuente de todos los números, ecuaciones, tablas y
  afirmaciones. NADA que contradiga el informe.
- **Decisiones de arquitectura**: `.claude/DECISIONES.md` — entradas `D1`..`D25`. Cada decisión
  tiene contexto, alternativas descartadas y motivo. Es la fuente de los "por qué".
- **Enunciado**: `.claude/ENUNCIADO.md` — lo que pedía la cátedra.
- **Código**: `src/main/java/...` y `tools/...` — la fuente de todos los snippets.
- **Guion oral existente**: `presentacion/GUION.md` — tono y foco de la defensa.

## Regla de oro: CADA snippet de código citado y verbatim

Todo bloque de código DEBE:
1. Ir en un fence con lenguaje (` ```java `, ` ```python `, ` ```csv `, ` ```bash `).
2. Tener como **primera línea** un comentario con la ruta y el rango de líneas **reales**:
   `// src/main/java/ar/edu/itba/simped/core/Vec3.java:40-43`
   (para python usar `#`, para csv usar `#`).
3. Contener el código **textual** del archivo en esas líneas (copiá y pegá; NO parafrasees, NO
   reescribas, NO inventes líneas). Si recortás, usá `// ...` en una línea propia y mantené los
   números de línea del rango citado coherentes con lo que mostrás.
4. En la prosa, referí al código como `` `Vec3.java:41` `` (archivo:línea) para que sea rastreable.

**Antes de citar, LEÉ el archivo con la tool Read** y verificá los números de línea. Un número de
línea equivocado o un snippet que no matchea el archivo es un error grave (rompe la confianza del
oral). Preferí citar de más y correcto, que de más y falso.

## Formato de cada archivo `.md`

- Encabezado `# Título` al inicio.
- Un bloque de **navegación** al pie: enlaces relativos a los archivos hermanos relevantes
  (ver "Cross-linking").
- Español rioplatense técnico, tono de preparación de defensa oral (claro, directo, sin relleno).
- Para los archivos de **blindaje** (Q&A): formato
  ```
  ### P: <pregunta que podría hacer un profesor>
  <respuesta concisa y correcta, con cita al código/informe/decisión cuando aplique>
  ```
  Cubrí tanto preguntas "fáciles" (qué es X) como "trampa" (por qué no hiciste Y, qué pasa si Z,
  cuál es la limitación). Marcá con **⚠️ Punto débil** las respuestas donde el trabajo tiene una
  limitación conocida, y explicá cómo defenderla honestamente.

## Cross-linking (rutas relativas)

Desde `oral/codigo/*.md` o `oral/blindaje/*.md` o `oral/resumenes/*.md`:
- A un hermano de la misma subcarpeta: `[texto](otro-archivo.md)`
- A otra subcarpeta: `[texto](../codigo/02-fisica-cpm.md)`
- Al informe: `[informe §Implementación](../../informe/informe.tex)`
- A una decisión: `[D17](../../.claude/DECISIONES.md)` (mencionando el número `D17` en el texto)
- Al código: cita `archivo:línea` en prosa (el HTML la convertirá en ancla).

Usá **anclas de sección** con títulos `##`/`###` claros para poder linkearlos
(`../codigo/02-fisica-cpm.md#contacto-de-pared-al-nucleo-duro`).

## Números canónicos (del informe — NO cambiarlos)

- Suite de tests: **143 tests, 0 fallos, 0 omitidos**.
- Perfil CPM (Baglietto–Parisi set 1): `vd=1.55` m/s (normal) / `2.0` m/s (evacuación, modo
  crisis), `τ=0.5` s, `rmin=0.15` m, `rmax=0.32` m, `β=0.9`, `ve=vd`.
- `Δt ≈ 0.048` s (normal) / `0.0375` s (evac); `Δt_out = 0.2` s.
- Escalera: ancho `2.6` m, `speed_factor=0.38`, v efectiva `≈0.59` m/s.
- Mapa: 60×60 m; recreo (x∈[0,30], z=0) + edificio (x∈[30,60], z=0 y z=3), 16 aulas, 2 escaleras
  switchback (descanso z=1.5), zona observable del kiosco R=[2,14]×[42,52].
- Evacuación N∈{40,80,120,200,300,400,500}; Ingreso Ta∈{1,5,10} min con Nmax=120
  (Q∈{120,24,12}); complementario Nmax∈{60,120,180,240,300} a Ta=5 min.
- Livelock jamba: oscilación-en-esquina 20.1%→5.5%, agentes atascados 2/30→0/30 (D17).
- 5 realizaciones (semillas 1–5) por punto.

## Convención de nombres de archivos (NO pisar los de otros agentes)

Cada agente escribe SÓLO los archivos que se le asignan. El índice `README.md`, el resumen
ejecutivo, los resúmenes de intro/implementación/conclusiones y el `index.html` los integra el
overseer. No toques `_CONTRATO.md`.
