# Instructivo de entrega — TP Final SdS · Grupo 05

## Contenido de esta carpeta

```
entregables/
├── SdS_TPFinal_2026Q1G05_Informe.pdf         ← ENTREGAR
├── SdS_TPFinal_2026Q1G05_Presentacion.pdf     ← ENTREGAR (fotograma + link YouTube)
├── SdS_TPFinal_2026Q1G05_Codigo.zip           ← ENTREGAR (motor + escenarios + scripts)
└── presentacion_oral/                          ← SOLO para exponer, NO se entrega
    ├── tpfinal_presentable.pdf                 (videos embebidos)
    └── videos/                                 (los .mp4 que reproduce pympress)
```

## Qué entregar y cómo

Subir al campus/formato que indique la cátedra, **respetando los nombres tal cual**:

1. `SdS_TPFinal_2026Q1G05_Informe.pdf`
2. `SdS_TPFinal_2026Q1G05_Presentacion.pdf` — es la versión **entregable**: cada animación
   aparece como fotograma fijo con su link a YouTube debajo (regla de la cátedra).
3. `SdS_TPFinal_2026Q1G05_Codigo.zip` — código del simulador ampliado a 3D.

**No entregar** la carpeta `presentacion_oral/` ni los `.mp4` (son solo para la exposición).

## Cómo presentar en vivo (con los videos reproduciéndose)

Desde una terminal, parado en la carpeta del PDF presentable:

```bash
cd presentacion_oral
pympress tpfinal_presentable.pdf
```

- pympress reproduce los videos embebidos (autoplay + loop) sin salir de la presentación.
- La carpeta `videos/` **debe quedar al lado** de `tpfinal_presentable.pdf` (ya está así).
- Si no está instalado: `pip install pympress` (o `brew install pympress`) — requiere VLC.

## Antes de exponer

- Chequear que los 4 videos de YouTube estén **públicos/no listados** (los links del PDF
  entregable apuntan a ellos).
- Ensayar tiempos: la presentación oral del TP final es de **10 a 15 minutos**.
