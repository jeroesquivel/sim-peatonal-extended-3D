# Presentación oral — TP Final (mockup)

Beamer 16:9, mismo mecanismo dual que TP5 (una sola fuente de contenido, dos salidas):

| Versión | Comando | Qué produce |
|---|---|---|
| **Entregable** | `pdflatex SdS_TPFinal_2026Q1G05_Presentacion.tex` | Fotograma clickeable + link de YouTube debajo (si el link está vacío muestra `[link pendiente]`). Liviano, es el PDF que se entrega. |
| **Presentable** | `pdflatex tpfinal_presentable.tex` | Los `.mp4` **embebidos** (reproducen inline en Adobe Acrobat/Reader), sin URLs en pantalla. Es el PDF para la oral. |

Compilar **dos veces** (número total de páginas + textpos).

## Estructura por estudio (regla de la cátedra)

Cada estudio sigue el orden **animación → serie temporal del observable → input vs escalar**,
con máximo 2 animaciones por estudio, sin índice y sin slide de referencias (citas inline con
`\fuente{...}` en la esquina inferior izquierda).

## Pendientes antes de la oral

1. **Subir las 4 animaciones a YouTube/Vimeo** y pegar los links en los macros
   `\videolinkEvacBaja`, `\videolinkEvacAlta`, `\videolinkIngresoUno`, `\videolinkIngresoDiez`
   (cabecera del `.tex`). Revisar que no se peguen caracteres extraños.
2. **Ensayar y cronometrar** (~13 min) con el guion por diapositiva de [`GUION.md`](./GUION.md)
   (tiempos, checkpoints por sección, plan de recortes y preguntas esperables). Si sobra
   contenido, los candidatos a recortar son las dos slides de "huella espacial" (heatmaps) y
   la del estudio complementario de N_máx.

## Assets

- `figuras/` — copiadas de `informe/figuras/` (misma fuente de verdad que el informe).
- `diagrams/` — diagramas TikZ axonométricos (plantas apiladas y escalera switchback), `\input`
  desde el cuerpo. Comparten proyección (`x={(0.90,-0.30)}, y={(0.90,0.30)}, z={(0,0.95)}`) y
  paleta; solo requieren las tikzlibs ya cargadas en el preámbulo.
- `videos/anim_*.{png,mp4}` — pósters y animaciones renderizados de **corridas reales** de los
  barridos (seed 1). Regenerarlos:

```bash
# desde la raíz del repo
python3 tools/visualize_simulation_3d.py --scenario out/sweeps/evacuacion/v40/scenario \
  --output out/sweeps/evacuacion/v40/seed1/output.csv \
  --out presentacion/videos/anim_evac_n40.mp4 --stride 4 --dpi 110
python3 tools/visualize_simulation_3d.py --scenario out/sweeps/evacuacion/v40/scenario \
  --output out/sweeps/evacuacion/v40/seed1/output.csv \
  --out presentacion/videos/anim_evac_n40.png --snapshot 25 --dpi 150
# ídem: evacuacion/v120 (snapshot t=40), ingreso/v1 (t=90), ingreso/v10 (t=300)
```
