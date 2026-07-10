# Blindaje — Escenario y modelado (Escuela, sub-escenarios, modo crisis)

Q&A de defensa oral sobre el escenario **Escuela**, cómo se modelaron las aulas, qué se varió en cada
estudio, la elección de la zona observable y la reproducibilidad. Fuentes: [informe
§Simulaciones](../../informe/informe.tex), [D12](../../.claude/DECISIONES.md),
[D15](../../.claude/DECISIONES.md), [D20](../../.claude/DECISIONES.md),
[D22](../../.claude/DECISIONES.md), [D25](../../.claude/DECISIONES.md) y el builder
`tools/scenarios-builders/build_escuela.py`.

---

### P: ¿Por qué la Escuela y no otro mapa?
Porque el enunciado pide **≥2 plantas, aulas, pasillos, escaleras y recreo con kiosco**, y la Escuela
los reúne en una geometría simple: las dos plantas ejercitan todo el soporte 3D nuevo (grafo por
planta, CIM por planta, `z` interpolada en la escalera) y el kiosco da el cuello de botella tipo cola
para Ingreso. Además es **paramétrica** ([D12](../../.claude/DECISIONES.md)): el mismo
`build_escuela.py` genera baseline y los dos sub-escenarios variando sólo `--mode`/`--value`, así la
geometría es idéntica entre estudios y sólo cambia el input.

### P: ¿Por qué dos plantas y no una sola más grande?
Porque el TP **es** la ampliación a 3D: sin dos plantas reales nada del trabajo nuevo se ejercita —de
hecho todos los módulos quedaban verdes en aislamiento con una planta y **recién con dos aparecieron
los cuatro bugs de integración** donde la `z` se perdía en silencio (informe §Implementación, "La
integración multiplanta"; [D11](../../.claude/DECISIONES.md)). La segunda planta además da la
**distribución bimodal** de la Evacuación (PB sale rápido, P1 es el lóbulo lento que baja por la
escalera a velocidad reducida).

### P: ¿Cuáles son las medidas del mapa?
Mapa cuadrado **60×60 m** (`build_escuela.py:41`): recreo `x∈[0,30]` (`z=0`) y edificio `x∈[30,60]`
(PB `z=0`, P1 `z=3 m`), pasillo central `x∈[42,48]` y aulas `y∈[8,52]` (`build_escuela.py:45-58`),
con **16 aulas** (4 por lado y planta). Dos escaleras switchback en las puntas (`y≈0`, `y≈60`), ancho
**2.6 m**, descanso a **`z=1.5 m`**, `speed_factor=0.38` (`build_escuela.py:71-87`), velocidad
efectiva medida ≈0.59 m/s. Ver [03-simulaciones.md §1](../resumenes/03-simulaciones.md#1-el-mapa-escenario-escuela).

### P: ¿Cómo modelaron las aulas?
Como **servers de tipo `CLASSROOM`** (recinto colectivo con una sesión), no TARGETs de capacidad 1
([D15](../../.claude/DECISIONES.md)): el builder emite un rectángulo por aula con `type:"CLASSROOM"`
(`build_escuela.py:328-340`, helper `_classroom` en `build_escuela.py:429-440`) que libera a todos en
`t_init + t_mean` —el **timbre** sincronizado—. Los TARGETs capacidad-1 eran irreales y daban un
**sesgo del ~77% hacia P1**; con `CLASSROOM` + planes `CLASE_PB`/`CLASE_P1` (~50/50 por el pool del
generador) el sesgo desaparece. Detalle físico en [q-fisica-cpm.md](q-fisica-cpm.md).

### P: ¿Qué input variaron en cada estudio y cuál es el observable/escalar?
| Estudio | Input | Observable | Escalar |
|---|---|---|---|
| Evacuación | capacidad total `N ∈ {40,80,120,200,300,400,500}` | distribución de tiempos de evacuación (ec. `tevac`) | `t_evac` promedio y máximo |
| Ingreso | ventana de llegada `T_a ∈ {1,5,10}` min, `N_max=120` fijo (`Q=N_max/T_a`) | población vs. tiempo en la zona del kiosco (ec. `nzona`) | ocupación máx. y promedio |
| Complementario | cantidad `N_max ∈ {60,120,180,240,300}`, `T_a=5 min` fijo | población vs. tiempo en la zona | ocupación máx. y promedio |

Ver las tres ecuaciones en [03-simulaciones.md §2](../resumenes/03-simulaciones.md#2-los-observables-definidos-por-ecuación).

### P: En Evacuación, ¿cómo entran los agentes "ya adentro"?
Con generadores en modo **`instant_occupation`**, uno por aula (`EVAC_1..EVAC_16`), que colocan su
lote en `t=0` (informe §Simulaciones; `build_escuela.py:530-541`), repartidos lo más parejo posible
entre las 16 aulas (`_evac_room_counts`, `build_escuela.py:485-491`). El plan `EVACUAR` sólo tiene
`exit_selection: RANDOM` porque ya están sentados y evacúan directo.

### P: ¿Por qué el observable del Ingreso es el kiosco y no el pie de la escalera? El enunciado decía "por ejemplo antes de la escalera".
**⚠️ Punto débil (nos desviamos del ejemplo literal del enunciado).** Probamos el pie de la escalera
principal y **no congestiona**: el switchback es ancho, sólo ~la mitad de los alumnos suben a P1,
repartidos entre las dos escaleras y a lo largo de la ventana, así que queda casi vacío (pico 2–3,
señal plana; [D20](../../.claude/DECISIONES.md) punto 4). La congestión **real** está en el **frente
del kiosco** (~60 alumnos, pico 53/21/4.8 para 1/5/10 min), y el "p. ej." habilita medir donde
efectivamente hay congestión (zona adoptada `R=[2,14]×[42,52]` en `z=0`, `plot_ingreso.py:84`; la del
corredor pre-escalera sigue en `--zone`, `plot_ingreso.py:85`, `ZONA_ESCALERA_SUR`).

### P: ¿Qué es exactamente el modo crisis y cómo se implementa?
Es una **velocidad deseada de emergencia** más alta que la de caminata, como en los modelos de escape
(Helbing 2000), implementada como **perfil físico por generador** ([D22](../../.claude/DECISIONES.md)):
cada generador declara `max_velocity` y `App` deriva `v_d = v_e = max_velocity`, **2.0 m/s** en
Evacuación (`build_escuela.py:519-526`, `"max_velocity": 2.0`) vs **1.55 m/s** en baseline/Ingreso, lo
que baja el paso de integración a **0.0375 s** (vs. 0.048 s). **⚠️ Matiz honesto:** antes de D22 el
`max_velocity` se parseaba pero **nunca se consumía** (código muerto) y la Evacuación corría con
física idéntica al baseline; lo detectó la verificación código-vs-enunciado y se re-corrieron todos
los barridos.

### P: ¿Por qué el barrido de N llega hasta 500?
Porque post-fix del deadlock de boca de escalera ([D24](../../.claude/DECISIONES.md)) el edificio se
**vacía completo hasta N≈500** (evacuados ≥N−1) y el rango viejo 40–120 no mostraba lo interesante: la
**saturación de las escaleras** como cuello de botella ([D25](../../.claude/DECISIONES.md)), con **dos
regímenes** (suave `N≲200`, saturado `N≳300` con el máximo despegándose: 129→244 s). **⚠️ Por qué no
más de 500:** a N≈600–800 la boca "se arrastra" sin completar el vaciado (límite documentado en
[D24](../../.claude/DECISIONES.md)) y `t_evac` queda mal definido para los no evacuados.

### P: ¿Por qué el `max_time` de la simulación escala con N?
Para que **toda capacidad tenga margen de vaciarse** ([D25](../../.claude/DECISIONES.md)): con un
`max_time` fijo, N=400/500 no llegarían a evacuar y `t_evac` quedaría truncado. La regla
`max(400, 400 + 1.2·(N−120))` s da exactamente **400 s** para `N≤120` (los puntos históricos no
cambian) y crece linealmente por encima, con margen ~2× sobre el tiempo de vaciado medido:

```python
# tools/scenarios-builders/build_escuela.py:547
    max_time = max(EVAC_MAX_TIME, EVAC_MAX_TIME + (n_agents - 120) * 1.2)
```

### P: ¿Por qué Ta llega sólo hasta 10 min y no probaron ventanas más largas o más cortas?
El input es el **caudal** `Q=N_max/T_a`: ventanas más cortas = más congestión (lo interesante), y con
`T_a` larga el caudal se diluye (a 10 min el pico ya es ~5 agentes). No se puede bajar `T_a`
indefinidamente: el generador topa el spawn en su **límite físico de ~3 personas/min por metro de
puerta** (`MAX_PEOPLE_PER_METER=3`), así que por debajo de cierta ventana el input dejaría de ser
entregable y el barrido saldría invertido; por eso las zonas se dimensionaron anchas
([D20](../../.claude/DECISIONES.md) puntos 1–2) y el complementario se hizo a `T_a=5 min`, donde el
tope no se activa ([D25](../../.claude/DECISIONES.md)).

### P: ¿Por qué 3 accesos de entrada y no 2, y por qué zonas grandes?
Por el mismo tope de densidad de puerta: con las 2 zonas chicas del baseline (topes 18 y 12 p/min) la
ventana de 1 min (que necesita 120 p/min) se recortaba a ~30 agentes y el barrido salía **invertido**
([D20](../../.claude/DECISIONES.md) punto 2). Se usan 3 accesos anchos con cupos 60/30/30 = 120,
calibrados por acceso con `period = quantity · W / cupo` para que el cupo salga exacto en cualquier
ventana (`build_escuela.py:730-732`).

### P: ¿Cómo garantizan la reproducibilidad? ¿Qué hace la semilla?
La semilla global (`-Dsimped.seed`) se propaga a **todos** los procesos aleatorios —spawn, ruteo,
selección de salida y de aula, y tiempos de servicio y de permanencia
([D23](../../.claude/DECISIONES.md))—; antes sólo alimentaba spawn y ruteo, así que entre réplicas cada
agente repetía las mismas elecciones. El motor de barrido pasa una semilla distinta por corrida:

```python
# tools/sweep_run.py:91-96
    cmd = [
        "java", f"-Dsimped.seed={seed}",
        "-cp", classpath,
        MAIN_CLASS,
        scenario_dir, output_csv, om,
    ]
```

**Sin** semilla el comportamiento es idéntico al histórico (preserva tests y corridas sueltas);
**con** semilla las realizaciones son genuinamente independientes. Cada punto corre con 5 semillas
(1 a 5) y se reporta media ± σ muestral. Ver [q-resultados-estadistica.md](q-resultados-estadistica.md).

### P: ¿Por qué 5 realizaciones y no una sola corrida?
Porque el sistema es estocástico (spawn, selección de salida/aula, tiempos de servicio) y una corrida
no dice nada de la dispersión; la cátedra pide ≥5 realizaciones con barras de error. Con 5 semillas
reportamos media y `±σ`, lo que además **captura el cruce de regímenes** en Evacuación: la dispersión
es máxima justo en `N=300` (una corrida formó un atasco transitorio que duplicó el máximo a 427 s),
invisible con una sola corrida. Ver informe §Resultados.

### P: ¿El kiosco no debería ser el cuello de botella también en Evacuación?
No, porque en Evacuación el kiosco **queda fuera del plan** (`EVACUAR` sólo tiene `exit_selection`):
los servers de aula/kiosco están en la geometría pero ningún plan los referencia, es inocuo
(`build_escuela.py:553-555`, "ningún plan los referencia: inocuo, no rompen nada"). En el Ingreso el
kiosco **sí** entra en el plan (sólo para quien llega por el recreo), con `max_capacity=30` y servicio
corto para que la señal observable sea la aglomeración frente al kiosco y no un embudo artificial
([D20](../../.claude/DECISIONES.md) punto 3).

### P: ¿Qué limitación de modelado admitirían del escenario?
**⚠️** Tres: (1) el Ingreso modela **una sola sesión** de clase (Formato B admite un solo
`sessionStarts`), sin recreos múltiples —en Ingreso el timbre se corre más allá de `max_time` para que
el aula sólo absorba ([D20](../../.claude/DECISIONES.md) punto 3); (2) la zona observable del Ingreso
se desvió del ejemplo literal (kiosco en vez de escalera), justificado con datos y el "p. ej."; (3) en
Evacuación queda **≈1 agente por corrida sin evacuar** (no crece con N): casi siempre el último en
tránsito al terminar la corrida —borde del criterio, ec. `tevac`— y, esporádicamente, el livelock de
contacto con muros del CPM ([D17](../../.claude/DECISIONES.md), ver [q-fisica-cpm.md](q-fisica-cpm.md)).

---

## Navegación

- Resumen de Simulaciones (mapa, ecuaciones, barridos): [../resumenes/03-simulaciones.md](../resumenes/03-simulaciones.md)
- Resumen de Resultados: [../resumenes/04-resultados.md](../resumenes/04-resultados.md)
- Resumen de Implementación: [../resumenes/02-implementacion.md](../resumenes/02-implementacion.md)
- Q&A de resultados y estadística: [q-resultados-estadistica.md](q-resultados-estadistica.md)
- Decisiones: [DECISIONES.md](../../.claude/DECISIONES.md) (D12, D15, D20, D22, D25)
