# Resumen §Simulaciones — el mapa Escuela, observables, barridos y modo crisis

Resumen de **Simulaciones** del [informe](../../informe/informe.tex) (líneas 305–413).
Fuente builder: `build_escuela.py`; barrido: `tools/sweep_run.py`. Porqués:
[D12](../../.claude/DECISIONES.md) · [D18](../../.claude/DECISIONES.md) ·
[D20](../../.claude/DECISIONES.md) · [D22](../../.claude/DECISIONES.md) ·
[D25](../../.claude/DECISIONES.md).

---

## 1. El mapa: escenario Escuela

Mapa cuadrado **60×60 m**, dos zonas:

- **Recreo** (izquierda, `x∈[0,30]`, planta única `z=0`): patio con **kiosco** (server con cola) y salida propia.
- **Edificio** (derecha, `x∈[30,60]`, dos plantas: PB `z=0`, P1 `z=3 m`): pasillo vertical central + **16 aulas** (4 por lado y planta). **Dos escaleras switchback** en las puntas, descanso a `z=1.5 m`. PB con salida propia y conexión al recreo por esquinas; **P1 sin salida** (se evacúa bajando).

**Escaleras.** Cada una es un **switchback** (L con descanso), modelado como dos `Stairs` encadenados por el landing. Ancho **2.6 m**, `speed_factor=0.38`; velocidad efectiva medida **≈0.59 m/s** (`0.38 × 1.55`), con `z` monótona respecto del avance.

> ⚠️ El comentario `build_escuela.py:86-87` quedó viejo; el valor real es `0.38 × 1.55 ≈ 0.59 m/s`. La constante `speed_factor = 0.38` sí es la vigente.

Aulas = **servers `CLASSROOM`** ([D15](../../.claude/DECISIONES.md)), no TARGETs capacidad-1: un rectángulo por aula (`AULA_PB_*` en `z=0`, `AULA_P1_*` en `z=3`) más el kiosco.

---

## 2. Los observables, definidos por ecuación

Tres observables por ecuación (informe §Introducción, líneas 86–114), verbatim:

**Tiempo de evacuación** (ec. `tevac`), agente *i*:

```text
t_evac,i = t_salida,i − t_inicio,i          (ec. tevac)
```

`t_inicio,i` = primer instante en la salida; `t_salida,i` = instante en que la abandona. **Criterio operativo:** evacuado = deja de figurar en el output antes del último cuadro. El que sigue presente en el último cuadro **no** evacúa ni aporta a la distribución (esto explica el "≈1 agente/corrida que no evacúa": el último en tránsito).

**Población en una zona** (ec. `nzona`), región `R ⊂ ℝ²` en PB (`z≈0`):

```text
n_zona(t) = |{ i : (x_i(t), y_i(t)) ∈ R,  z_i(t) ≈ 0 }|          (ec. nzona)
```

Agentes cuya proyección `(x,y)` cae en `R` en `t`. Implementada literal en `sweep_lib.py:zone_population` (filtra por rectángulo y por planta, `|z − zlevel| < 0.4`).

**Caudal de ingreso** (ec. `caudal`). Input = ventana `T_a`: los `N_max` alumnos uniformes sobre `T_a ∈ {1,5,10}` min, `N_max` fijo. Caudal medio:

```text
Q = N_max / T_a          (ec. caudal)
```

Ventanas cortas ⇒ caudales altos (`Q ∈ {120,24,12}` ag./min para `N_max=120`).

---

## 3. Parámetros físicos (Tabla del informe)

Perfil físico de **Baglietto y Parisi** (set 1). En Evacuación, **modo crisis**: `v_d = v_e = 2.0 m/s` (vs `1.55` normal). Tabla (informe §Simulaciones, Tabla `tab:params`):

| Parámetro | Símbolo | Valor |
|---|---|---|
| Velocidad deseada (normal) | `v_d` | 1.55 m/s |
| Velocidad deseada (evacuación) | `v_d` | 2.0 m/s |
| Tiempo de relajación | `τ` | 0.5 s |
| Radio mínimo | `r_min` | 0.15 m |
| Radio máximo | `r_max` | 0.32 m |
| Coeficiente anisotrópico | `β` | 0.9 |
| Velocidad de escape | `v_e` | `= v_d` |
| Paso de integración | `Δt` | 0.048 s / 0.0375 s (evac.) |
| Paso de salida | `Δt_out` | 0.2 s |
| Factor de velocidad en escalera | `speed_factor` | 0.38 |
| Ancho de escalera | — | 2.6 m |

`Δt` efectivo se acota con el perfil más rápido:

```text
Δt = min( Δt_esc ,  r_min / (2·max(v_d, v_e)) )
```

→ `≈0.048 s` normal, `0.0375 s` en evacuación (por `v_d=2.0`). (Nota: `r_max` es **constante** `0.32 m` en el código; el informe dice "se muestrea en `[0.30,0.32]`" pero no ocurre — ver [física](../codigo/02-fisica-cpm.md).)

---

## 4. Los tres barridos

### 4.1 Evacuación (input: capacidad total N)

Alumnos **arrancan dentro de las aulas** de ambas plantas; los de P1 bajan por escaleras. **Modo crisis** (`v_d=2.0`). Barrido **`N ∈ {40,80,120,200,300,400,500}`** (≈0.5 agentes/m² inicial; [D25](../../.claude/DECISIONES.md)).

Colocados **ya sentados en t=0** con generadores `instant_occupation` (uno por aula, `EVAC_i`, reparto por `_evac_room_counts`). El modo crisis se declara por el `max_velocity=2.0` del generador ([D22](../../.claude/DECISIONES.md)).

**Tiempo total escala con N** ([D25](../../.claude/DECISIONES.md)): 400 s para `N≤120`, luego `400 + 1.2·(N−120)` s (`EVAC_MAX_TIME=400.0`). Plan `EVACUAR` = sólo `exit_selection: RANDOM` (ya están adentro).

- **Observable:** distribución de tiempos de evacuación (ec. `tevac`).
- **Escalar:** promedio y máximo por N.

### 4.2 Ingreso (input: ventana de llegada Ta)

Los `N_max=120` alumnos **llegan por las entradas** y van a sus aulas; los del recreo pasan antes por el kiosco. Input = `T_a ∈ {1,5,10}` min a `N_max` fijo (`Q ∈ {120,24,12}` ag./min). Tiempo total `T_a + 250 s`.

Caudal calibrado **por acceso** para que el cupo salga exacto en cualquier ventana `W`: `period = quantity · W / cupo` ([D20](../../.claude/DECISIONES.md)). Los 120 se reparten en **3 accesos dedicados** (anchos, para no toparse con el límite de 3 personas/min·m): recreo (cupo 60, pasa por kiosco), edificio sur y norte (cupo 30 c/u, directo).

- **Observable:** población vs. tiempo en la zona del kiosco (ec. `nzona`).
- **Escalar:** ocupación máxima y promedio vs. `T_a`.

### 4.3 Estudio complementario (input: cantidad Nmax)

Mismo sub-escenario de Ingreso, **`N_max ∈ {60,120,180,240,300}`** a ventana **fija `T_a=5 min`** (eje ortogonal al caudal). Lo corre `tools/run_ingreso_nmax.py`: reescala el `period` de cada generador por `base/Nmax`, sin tocar el builder.

Por qué `T_a=5 min`: a esa ventana las puertas entregan el caudal completo; más corto, el generador se topa con su **límite físico de ~3 personas/min por metro** y el input dejaría de ser alcanzable ([D25](../../.claude/DECISIONES.md)).

---

## 5. La zona observable del Ingreso: el kiosco, no el pie de la escalera

El enunciado sugiere observar "una zona, **por ejemplo antes de la escalera**". El pie de la escalera **no muestra congestión** (switchback ancho, sólo la mitad sube, repartidos entre dos escaleras y a lo largo de la ventana → casi vacío, pico 2–3). La congestión se forma en el **frente del kiosco** ([D20](../../.claude/DECISIONES.md) punto 4).

Zona observable adoptada: **rectángulo del kiosco `R = [2,14]×[42,52]` en `z=0`** (`ZONA` en `plot_ingreso.py`). El "por ejemplo" habilita medir **donde hay congestión**. La zona del corredor pre-escalera queda disponible por `--zone`.

---

## 6. Modo crisis

Implementado como **perfil físico por generador** ([D22](../../.claude/DECISIONES.md)): cada generador declara `max_velocity` y `App` deriva un `AgentProfile` con `v_d = v_e = max_velocity`. Evacuación = **2.0 m/s** (emergencia, Helbing 2000); baseline e Ingreso = 1.55 (default). El `dt` efectivo baja a 0.0375 s en evacuación por el perfil más rápido.

---

## 7. Las 5 realizaciones (semillas)

Cada punto = **5 realizaciones (semillas 1–5)**; la semilla gobierna **todos** los procesos aleatorios (spawn, ruteo, selección de salida/aula, tiempos de servicio y permanencia; [D23](../../.claude/DECISIONES.md)) → realizaciones independientes. Se reporta media ± σ muestral.

Motor: `tools/sweep_run.py`; corre cada `(value, seed)` con `-Dsimped.seed=<seed>`. `--mode` elige sub-escenario (`evacuacion`/`ingreso`/`baseline`), `--values` el barrido, `--seeds` las semillas. Salida: `out/sweeps/<mode>/v<value>/seed<seed>/output.csv`.

---

## Navegación

- Resultados numéricos y su análisis: [../resumenes/04-resultados.md](04-resultados.md)
- Implementación (Vec3, grafo 3D, CPM en escaleras): [../resumenes/02-implementacion.md](02-implementacion.md)
- Q&A de escenario y modelado: [../blindaje/q-escenario-modelado.md](../blindaje/q-escenario-modelado.md)
- Q&A de resultados y estadística: [../blindaje/q-resultados-estadistica.md](../blindaje/q-resultados-estadistica.md)
- Informe: [informe.tex §Simulaciones](../../informe/informe.tex)
- Decisiones: [D12/D18/D20/D22/D25 en DECISIONES.md](../../.claude/DECISIONES.md)
