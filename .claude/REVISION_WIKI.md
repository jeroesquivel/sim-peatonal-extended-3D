# REVISION_WIKI.md — Auditoría del TP contra la wiki de SDS

**Fecha:** 2026-07-03. Comparación sistemática del estado del TP (código, metodología,
informe) contra la wiki de la materia (`~/Desktop/ITBA/26-1C/SDS/SDS_Obsidian/`), en
particular contra `conceptos/lecciones_correcciones.md` (la guía implícita de la cátedra,
extraída de las correcciones de TP2 nota 6 y TP3 nota 4.5) y `tps/TP_FINAL.md`.

Cada hallazgo indica su estado: ✅ corregido en esta sesión · 📋 planificado (ver
`PLAN_ENTREGA.md`, Fase 2) · ℹ️ nota.

---

## 1. Hallazgos CRÍTICOS (patrones que ya costaron nota en TP2/TP3)

### 1.1 Una sola realización por punto, sin barras de error — ✅ corregido
- **Regla de la wiki:** "Barras de error siempre visibles, ≥ 5 realizaciones por punto.
  Indicar número de realizaciones." (lecciones_correcciones; además es el estándar que el
  propio grupo aplicó en TP5: "5 realizaciones por punto").
- **Estado previo:** TODOS los resultados del informe (evacuación, ingreso, Nmax) salían de
  **una única corrida con semilla 1** ("Todos los resultados son con semilla 1"), sin σ.
- **Fix aplicado:** barridos re-corridos con **semillas 1–5** (5 realizaciones por punto);
  `plot_evacuacion.py` / `plot_ingreso.py` / `run_ingreso_nmax.py` agregan cross-seed
  (media ± desvío muestral, errorbars con capsize; banda ±σ en población-vs-t); el informe
  declara el número de realizaciones en *Simulaciones*.

### 1.2 Leyenda categórica para variable numérica — ✅ corregido
- **Regla:** "Para variables numéricas (N, η) usar paleta gradual + colorbar, NO leyenda
  categórica" (corrección explícita de TP3; plantilla en `herramientas/matplotlib.md`).
- **Estado previo:** curvas de población por ventana (1/5/10 min) y por Nmax con colores
  categóricos y leyenda; histogramas por N monocolor.
- **Fix aplicado:** viridis + `Normalize` + colorbar para los valores numéricos del input;
  la leyenda queda solo para series de distinta naturaleza (promedio vs máximo).

### 1.3 Afirmación de forma funcional sin análisis de escala — ✅ corregido
- **Regla:** antes de afirmar exponencial/ley de potencia/supralineal, mirar semi-log o
  log-log (corrección explícita de TP3).
- **Estado previo:** el informe afirmaba que la ocupación "crece de forma marcadamente
  **supralineal** con Nmax" a partir de 3 puntos y 1 semilla, sin gráfico de escala.
- **Fix aplicado:** redacción sin claim de forma funcional (descripción de la tendencia
  soportada por la tabla). Si se quisiera afirmar forma: agregar más puntos de Nmax y un
  panel log-log (queda como opcional en Fase 2).

---

## 2. Hallazgos ALTOS (estructura y soporte del informe)

### 2.1 Estructura del informe no seguía la de la cátedra — ✅ corregido
- **Regla (corrección TP2):** Introducción (modelo + definiciones con ecuaciones) /
  **Implementación** (arquitectura del código, SIN valores numéricos de parámetros) /
  **Simulaciones** (acá van los valores, y el número de realizaciones; la semilla y el
  tiempo de simulación NO son parámetros físicos) / **Resultados** (introducir cada figura
  antes de analizarla) / Conclusiones.
- **Estado previo:** secciones ad-hoc (Objetivo / El modelo 3D / La escalera realista / …)
  con valores numéricos mezclados en las secciones de implementación (speed_factor=0.38,
  60×60 m, z=1.5 m) y "Todos los resultados son con semilla 1" como si fuera un parámetro.
- **Fix aplicado:** informe reestructurado a Introducción / Implementación / Simulaciones /
  Resultados / Conclusiones / Referencias; valores numéricos movidos a Simulaciones.

### 2.2 Sin citas bibliográficas — ✅ corregido
- **Regla:** citas inline; justificar el modelo con la fuente ("Justificar cada decisión de
  modelo con cita a la fuente", `tps/TP_FINAL.md` §6).
- **Estado previo:** el informe nombraba "Baglietto–Parisi / Martin–Parisi" sin ninguna
  referencia formal.
- **Fix aplicado:** `thebibliography` con las referencias del CPM extraídas de los javadocs
  del código, citadas con `\cite` donde se presenta el modelo.

### 2.3 Observables/variables usados sin definir — ✅ corregido
- **Regla:** "Definir las variables antes de usarlas" + explicitar casos borde.
- **Estado previo:** *caudal* usado de forma ambigua ("cuanto más corto el caudal…" —
  el caudal no es corto: la ventana es corta y el caudal alto); t_evac definido al pasar;
  criterio de "evacuado" (agente que desaparece antes del último frame) implícito; los
  38/78/118 evacuados de 40/80/120 sin explicación de los no-evacuados.
- **Fix aplicado:** definiciones con ecuaciones en la Introducción (t_evac, n_zona(t),
  Q = Nmax/T_a), criterio de evacuado explícito, y mención honesta de los agentes no
  evacuados (livelock de jamba, D14).

### 2.4 Método de promediado de evoluciones temporales sin explicitar — ✅ corregido
- **Regla (corrección TP3):** al promediar evoluciones temporales de varias realizaciones,
  **explicitar el método** (binning/interpolación/grilla común).
- **Fix aplicado:** las curvas de población-vs-t se promedian sobre la grilla temporal común
  de dt_out (con padding 0 tras el fin de cada corrida — la zona queda vacía), y el método
  queda documentado en el script y en el informe.

---

## 3. Hallazgos MEDIOS (calidad de figuras y datos)

| # | Hallazgo | Regla de la wiki | Estado |
|---|---|---|---|
| 3.1 | Títulos de figuras con "(seed 1)" | "Las condiciones/parámetros NO van en el título" | ✅ quitados de los PNG |
| 3.2 | PNG a dpi=130 | `matplotlib.md`: "dpi=200 o más para entregar" | ✅ dpi=200 |
| 3.3 | "149 tests verdes (8 skipped)" en el informe | — (fidelidad) | ✅ real: **141 verdes, 0 skipped** (verificado con `mvn test` hoy) |
| 3.4 | Figuras sin `\label`/`\ref` (solo heatmaps) | "Introducir las figuras antes de hablar de sus conclusiones" | ✅ labels + refs en todas |
| 3.5 | Autores ausentes en el informe | — | ✅ Grupo 5 (Caules, Cortese, Esquivel) |
| 3.6 | Ventana de promediado de "ocupación promedio" sin definir | "Mostrar la ventana usada para promediar" | ✅ horizonte común explícito |

---

## 4. Verificación código vs enunciado (adversarial, 2026-07-03/04)

Un verificador independiente revisó los 14 requisitos del enunciado contra el código real y
sus tests. Resultado: **11/14 CONFIRMADOS** con evidencia file:line (z en agentes/output,
velocidad reducida en escaleras leída de STAIRS.csv, CIM por planta + puente de escalera,
paredes Y vértices como vecinos con test explícito, grafo por planta unido por escaleras,
A* euclídeo 3D con costo de arista inclinado real, visibilidad/FVP por planta, CPM único
(0 archivos SFM), interpolación de z monótona con test, animaciones 2D+3D, scripts de
barrido, escenario Escuela completo). Dos debilidades reales encontradas — **ambas
corregidas** el 2026-07-04:

### 4.1 REFUTADO → ✅ corregido (D22): no existía el "modo crisis"
El enunciado da por hecho "un modo para representar el comportamiento de agentes en
situación de crisis"; el código asignaba a todos los agentes el perfil hardcodeado
(vd=1.55) y el `max_velocity` del Formato B era **código muerto** (parseado, nunca
consumido). Fix: perfil por generador derivado de `max_velocity` (`vd=ve`), evacuación con
**vd=2.0 m/s** (crisis), baseline/ingreso en 1.55 (sin cambios), `dt` acotado por el perfil
más rápido. Verificado en corrida real: v mediana en el plano = 2.00 m/s, 119/120 evacuados.

### 4.2 PARCIAL → ✅ corregido (D23): la semilla no propagaba a todos los streams
`-Dsimped.seed` solo alimentaba generador de peatones + hops del navgraph; servers
(tiempos de servicio, softmax) y selección de plan/salida/aula/dwell usaban constantes
fijas ⇒ entre réplicas cada agente elegía la misma salida y los mismos tiempos. Fix:
`Seeds.mixOr(fallback, salt)` — con semilla global seteada cada stream varía por réplica;
sin setear, cae a la constante histórica (tests intactos). Las 5 realizaciones ahora son
genuinamente independientes.

Suite tras ambos fixes: **143 tests, 0 fallos** (2 tests nuevos). Los tres barridos
(evacuación, ingreso, Nmax) se re-corrieron completos con la física/semillas nuevas.

## 5. Wiki desactualizada — ℹ️ nota para el usuario

`wiki/tps/TP_FINAL.md` (actualizado 2026-06-20) todavía describe la **propuesta original
del grupo** (evacuación por incendio con humo, roles, follow-the-crowd, líderes), que NO es
el enunciado final: el TP real es la **ampliación a 3D** (multiplanta + escaleras + escenario
Escuela con Evacuación/Ingreso, ver `.claude/ENUNCIADO.md`). Conviene actualizar esa página
de la wiki para que refleje el TP real y su estado.

## 6. Pendientes que la wiki exige y no dependen del informe — 📋 Fase 2

1. **Presentación oral** (el final es el 10/07): armar Beamer siguiendo las reglas duras de
   `lecciones_correcciones.md` — animaciones EMBEBIDAS (no fotos; PDF con fotograma + link),
   por estudio *(animación → serie temporal → input vs escalar)* en ese orden, sin índice,
   sin slide final de referencias (citas inline en esquina), títulos descriptivos sin
   parámetros, 2 animaciones por estudio máximo, practicar.
2. **Empaquetado del código a entregar**: "solo el motor de simulación", sin postproc ni
   visualizadores ni outputs ni figuras (en TP3/TP4 pidieron zip < 100 KB — confirmar límite
   para el final): script de export que tome `src/main` + `pom.xml` + escenarios mínimos.
3. (Opcional) Más puntos en los barridos (N y Nmax) si se quiere afirmar tendencia/forma,
   con panel log-log.
