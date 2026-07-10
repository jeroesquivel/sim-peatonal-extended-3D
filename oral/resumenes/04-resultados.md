# Resultados y estadística

§Resultados y §Conclusiones del [informe](../../informe/informe.tex): las tres tablas tal cual + interpretación para el oral. Barrido extendido en [D25](../../.claude/DECISIONES.md). Definiciones de observables (`tevac`, `nzona`, `caudal`): [03-simulaciones.md](03-simulaciones.md).

---

## 1. Evacuación

**Input:** `N ∈ {40, 80, 120, 200, 300, 400, 500}` (alumnos ya adentro, ambas plantas), modo crisis (`vd = ve = 2.0 m/s`). **Observable:** distribución de `t_evac` (Fig. `evac-hist`). **Escalar:** promedio y máximo vs `N` (Fig. `evac-scalar`). 5 realizaciones (semillas 1–5).

### Tabla de Evacuación (informe, Tabla `tab:evac`)

| N   | evacuados | t_evac promedio [s] | t_evac máximo [s] |
|----:|----------:|:--------------------|:------------------|
| 40  | 39.0      | 38.9 ± 0.6          | 57.0 ± 1.5        |
| 80  | 78.8      | 46.2 ± 0.9          | 73.7 ± 4.3        |
| 120 | 118.8     | 50.8 ± 0.9          | 91.5 ± 3.1        |
| 200 | 199.0     | 63.2 ± 1.6          | 129.3 ± 8.5       |
| 300 | 298.8     | 88.7 ± 20.0         | 243.5 ± 102.8     |
| 400 | 399.0     | 94.5 ± 1.5          | 251.4 ± 3.6       |
| 500 | 499.0     | 108.7 ± 3.2         | 305.2 ± 17.9      |

### Los dos regímenes

Vaciado **completo** (evacuados `≥ N−1` hasta 500). Dos regímenes:

- **Suave (hasta N ≈ 200):** escaleras absorben. ×5 capacidad (40→200) = ×1.6 promedio (38.9→63.2 s). Manda la **longitud del recorrido**.
- **Saturado (desde N ≈ 300):** cola sostenida en la boca. Máximo salta de 129.3 a 243.5 s. Manda el **drenaje de cola**; el máximo se **despega del promedio** (N=500: prom 108.7, máx 305.2).

**Codo** en `N ≈ 200–300`. Cuello de botella = **boca de escalera**, sólo visible al saturarla.

### Distribución bimodal

**Bimodal** (Fig. `evac-hist`): **lóbulo corto** = PB (salida directa); **lóbulo lento** = P1 (bajan switchback, más largo, `≈0.59 m/s`). Al crecer `N` el lento **se desplaza y ensancha** hasta **dominar**.

### Dispersión máxima en el cruce (N=300)

Dispersión máxima **en el cruce**, no en los extremos: N=300 da máx `243.5 ± 102.8`, pero N=400 baja a `251.4 ± 3.6`. En N=300, **4/5** corridas terminan en **190–206 s**; **una** forma un **atasco transitorio** que **se disuelve solo** y estira el máximo a **427 s**. Cerca del régimen crítico manda la realización; pasado el cruce la cola saturada domina estable.

### El remanente ≈ 1 agente que NO crece con N

**≈1 sin evacuar/corrida**, **no crece con `N`** (1.0–1.2 en todo el barrido; N=500 → 1.0). Dos causas:

1. **Caso borde del criterio (`tevac`):** el **último en tránsito** al terminar; el output deja de escribir cuadros al vaciarse el edificio. No es evacuación fallida.
2. **Livelock esporádico de jamba en PB:** un agente aislado cuya dirección deseada queda casi paralela a la jamba alterna escape/atracción (`v ∼ vd`, desplazamiento nulo). Siempre aislado, no congestión. Límite conocido del CPM ([D17](../../.claude/DECISIONES.md)).

---

## 2. Ingreso

**Input:** `Ta ∈ {1, 5, 10} min`, `Nmax = 120` fijo (`Q = Nmax/Ta ∈ {120, 24, 12}` ag./min). **Observable:** población en el kiosco vs tiempo (Fig. `ingreso-pob`). **Escalar:** pico y promedio vs `Ta` (Fig. `ingreso-scalar`). Zona = `R = [2,14] × [42,52]` en `z=0`.

### Tabla de Ingreso (informe, Tabla `tab:ingreso`)

| Ta [min] | Q [ag./min] | Ocupación pico | Ocupación promedio |
|---------:|------------:|:---------------|:-------------------|
| 1        | 120         | 53.0 ± 2.1     | 36.6 ± 3.6         |
| 5        | 24          | 21.0 ± 3.6     | 8.9 ± 2.3          |
| 10       | 12          | 4.8 ± 0.8      | 1.5 ± 0.1          |

### Narrativa

Con `Nmax` fijo, ventana más corta (mayor `Q`) ⇒ más congestión: 120 en **1 min** → pico **53.0 ± 2.1**; en **10 min** → **4.8 ± 0.8** (**∼11×** menos). **Pico agudo y temprano** (1 min) vs **meseta baja y extendida** (10 min): mismo total, distinta *intensidad*. El problema no es cuánta gente sino **cuán rápido**. En **Ta=1 la cola no drena** (`∼29 agentes` al final, servidor **saturado**).

### Por qué el kiosco y no el pie de escalera

El pie **no muestra congestión** (switchback ancho, sólo la mitad sube, repartidos entre dos escaleras). La congestión está en el **frente del kiosco**. El "por ejemplo" del enunciado habilita medir donde la hay.

---

## 3. Estudio complementario: cantidad de agentes (Nmax)

**Input:** `Nmax ∈ {60, 120, 180, 240, 300}` a **ventana fija `Ta = 5 min`** (eje ortogonal al caudal). **Observable:** población vs tiempo (Fig. `nmax-pob`). **Escalar:** pico y promedio vs `Nmax` (Fig. `nmax-scalar`). A 5 min las puertas entregan el caudal completo (más corto, límite físico de ∼3 personas/min·m).

### Tabla del complementario Nmax (informe, Tabla `tab:nmax`)

| Nmax | Ocupación pico | Ocupación promedio |
|-----:|:---------------|:-------------------|
| 60   | 4.6 ± 0.9      | 1.1 ± 0.2          |
| 120  | 21.0 ± 3.6     | 8.9 ± 2.3          |
| 180  | 51.4 ± 1.8     | 30.6 ± 2.1         |
| 240  | 78.8 ± 2.3     | 51.4 ± 1.0         |
| 300  | 104.0 ± 3.7    | 70.8 ± 3.3         |

### Dos regímenes: supralineal → lineal

Gobernados por el kiosco:

- **Supralineal hasta saturar (Nmax ≈ 180):** ×3 población (60→180) = ×11 pico (4.6→51.4); se forma cola. Crece **más que proporcional**.
- **∼Lineal después:** kiosco saturado a **tasa fija**, cada extra se suma a la cola. Pico con **pendiente ≈ 0.44** (`(104.0 − 51.4)/(300 − 180) = 52.6/120 ≈ 0.44`).

En `Nmax = 300`, pico `104.0 ± 3.7` en `120 m²` **bordea 1 persona/m²** promedio, con densidades locales mayores en la cola (Fig. `dens-ing`).

---

## 4. Mapas de calor (lo que las curvas no muestran)

Observable **espacial** (colormaps de un tono, paredes superpuestas, celdas nunca ocupadas en gris). Las curvas dan *cuánto/cuándo*; los heatmaps, **dónde**.

### Densidad de ocupación

Ocupación media por celda `1×1 m` sobre todos los cuadros.

- **Evacuación** (Fig. `dens-evac`, N=120): calientes el **pasillo central y los pies de escalera** + rutas a salidas. Confirma el cuello.
- **Ingreso** (Fig. `dens-ing`, 1 min): caliente la **cola del kiosco**. Confirma la zona elegida.

### Tiempos de evacuación por origen

Cada celda con el `t_evac` medio de los que arrancan ahí (Fig. `tevac`, N=120):

- **Aulas PB:** `∼14–66 s` (**mediana 37 s**).
- **Aulas P1:** `∼29–91 s` (**mediana 63 s**), por bajar escalera.

Misma bimodalidad **anclada a la geometría**: *qué aulas* forman cada lóbulo.

---

## Síntesis para el oral

1. **Evacuación → dos regímenes** (codo `N ≈ 200–300`): suave → saturado, máximo despegado del promedio.
2. **Bimodal** PB (corto) + P1 (lento, crece y domina).
3. **Dispersión máxima en el cruce** (N=300): 4/5 ~190–206 s, 1 atasco de 427 s.
4. **Vaciado completo** (`≥ N−1` hasta 500); remanente `≈ 1` **no crece con N**.
5. **Ingreso → la ventana, no el total:** pico ∼11× menor de 1 a 10 min; en `Ta=1` la cola no drena.
6. **Complementario → kiosco satura** en `Nmax ≈ 180`: supralineal → lineal (`≈ 0.44`).
7. **Heatmaps:** pasillo + pies de escalera (evac), cola del kiosco (ingreso), PB vs P1 en `t_evac`.

---

## Navegación

- Q&A de defensa de estos resultados: [../blindaje/q-resultados-estadistica.md](../blindaje/q-resultados-estadistica.md)
- Cómo se corren los barridos y se computan los observables: [03-simulaciones.md](03-simulaciones.md)
- Fuentes: [informe (LaTeX)](../../informe/informe.tex) ·
  [DECISIONES.md](../../.claude/DECISIONES.md) (D25 — barrido extendido, dos regímenes)
