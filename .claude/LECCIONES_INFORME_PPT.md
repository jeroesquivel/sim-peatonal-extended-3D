# Lecciones — convenciones para el informe y la presentación (TP Final SdS)

> Handoff de correcciones. Un review encontró 17 problemas de estilo/consistencia/correctitud
> en `informe/informe.tex` y la presentación dual (`SdS_TPFinal_2026Q1G05_Presentacion.tex`
> entregable + `tpfinal_presentable.tex` oral). Ya están corregidos (2026-07-09). Estas son las
> reglas que se violaron y que hay que respetar para no reintroducirlos.

## Contexto estructural (no re-descubrir)
- **Dos entregables comparten figuras y números.** Las figuras están **duplicadas** en
  `informe/figuras/` y `presentacion/figuras/`: si tocás una, actualizá **las dos copias**
  (deben quedar md5-idénticas).
- La presentación tiene **dos versiones** que comparten cuerpo: no desincronizarlas.
- Tras compilar LaTeX, **borrar todos los auxiliares** (`.aux/.log/.nav/.snm/.toc/.out`) —
  dejar solo `.tex` + `.pdf`.

## Presentación: una slide ≠ una página de informe
- **Captions = etiqueta corta** ("Capacidad baja: $N=40$"), NO una oración que explique el
  fenómeno. Nada de prosa tipo informe debajo de una figura.
- **Bullets = frases cortas**, no oraciones completas con paréntesis y subordinadas. Si un
  frame tiene un párrafo, aligeralo.
- **Estructurar por secciones reales**: Introducción (motivación + modelo, ≤3 slides) vs
  Implementación (qué cambió por módulo). No un cajón único "Ampliación 3D".
- **No mencionar "el enunciado"** en el contenido (es material de cátedra, no parte del relato).

## Figuras (matplotlib)
- **Sin título matplotlib** cuando la figura va bajo un título de frame o un `\caption`:
  redunda. (Los identificadores de panel o de piso — "N=…", "PB (z=0)" — sí se quedan, no son
  título.)
- **Escalares vs N / Nmax / Ta: marcador + barra de error + línea recta "guía para el ojo"**
  uniendo los puntos. La guía §2.4.6 la permite EXPLÍCITAMENTE ("pueden ser opcionalmente
  unidos por líneas rectas como 'guía para el ojo'") siempre que los puntos estén marcados
  (símbolo + barra de error). Usar `errorbar(..., linestyle="-", capsize=4)`. Lo único
  prohibido es interpolar con **splines/polinomios** o unir sin marcar los puntos.
  (Decisión 2026-07-09: se había quitado la línea de más; se revirtió a `linestyle="-"` en los
  3 escalares — evac, ingreso, ingreso_nmax — a pedido del usuario.)

## Informe (estilo cátedra)
- Sub-secciones **numeradas** (`\subsection`, no `\subsection*`).
- `babel` con opción **`es-tabla`** → epígrafes dicen "Tabla" (el texto ya referencia "Tabla N").
- Títulos de referencias entre **comillas dobles**, no itálica.
- **No narrar el debugging en Conclusiones** ("el artefacto se diagnosticó y resolvió", "da un
  comportamiento coherente"): enunciar el resultado/comportamiento, no la historia.
- **Sin anglicismos evitables en prosa**: servidor (no *server*), valor por defecto (no
  *default*), salto de ruteo (no *hop*), fachada (no *facade*), sesgo (no *bias*), salida (no
  *output*). Código/CLI/claves siunitx quedan como están.
- **No repetir valores de parámetros en la prosa** si ya están en la tabla de parámetros:
  descripción cualitativa + remisión a la tabla.
- Unidades con siunitx (`\si{\minute}`, no "min" a mano).
- **Sin comentarios/valores stale** (ej. un rango "{40,80,120}" cuando el barrido real es
  {40..500}).
- Media±σ con **cifras significativas coherentes** (244±103 → 240±100).

## ⚠️ Gotcha de datos/números (casi rompe la consistencia)
- **La data cruda de los sweeps extendidos NO persiste** (evac N≥200, nmax≥240 estaban
  borrados; solo quedaban las figuras). `out/` se limpia. **Si tocás una figura de
  escalar/curvas, hay que re-correr el sweep.** Es reproducible por semilla: mismas seeds 1–5 +
  mismo código ⇒ mismos números.
- **Los números del escalar de evac estaban desincronizados** entre informe (108.2/301.4/×5.3)
  y presentación (108.7/305.2/×5.4). La verdad reproducible es **108.7 / 305.2 / ×5.4**.
  **Lección: cuando cambia un número de resultado, actualizarlo en LOS DOS docs y verificar
  contra la figura regenerada** (misma fuente de verdad).

## Cómo regenerar (referencia)
```bash
# Evacuación
python tools/sweep_run.py --mode evacuacion --values 40,80,120,200,300,400,500 --seeds 1,2,3,4,5
python tools/plot_evacuacion.py --sweep-dir out/sweeps/evacuacion --seeds all --out-prefix out/evac
# Ingreso
python tools/sweep_run.py --mode ingreso --values 1,5,10 --seeds 1,2,3,4,5
python tools/plot_ingreso.py --sweep-dir out/sweeps/ingreso --seeds all --out-prefix out/ingreso
# Complementario Nmax (--plot-only si la data ya está en out/ingreso_nmax/nN/seedS/)
python tools/run_ingreso_nmax.py --nmax 60,120,180,240,300 --seeds 1,2,3,4,5
# luego: copiar out/*.png a AMBOS informe/figuras/ y presentacion/figuras/,
# recompilar los 3 PDFs (×2 c/u) y borrar todos los auxiliares.
```

## Números de referencia (fuente de verdad, re-run 2026-07-09, 5 seeds)
- **Evacuación** (prom / máx en s): N=40 → 38.9/57.0 · N=500 → 108.7/305.2 (×2.8 / ×5.4).
  Brecha prom–máx casi constante desde N≈200 (~40–45 s cada 100 agentes). Máx en N=300 con σ
  grande (±103) por una seed con atasco transitorio.
- **Ingreso** (pico): Ta=1→53.0±2.1 · 5→21.0±3.6 · 10→4.8±0.8.
- **Complementario Nmax** (pico): 60→4.6 · 120→21.0 · 180→51.4 · 240→78.8 · 300→104.0.
</content>
