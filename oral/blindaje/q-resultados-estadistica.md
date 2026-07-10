# Blindaje — Resultados y estadística

Preguntas de defensa oral sobre los **resultados** y su **interpretación estadística**. Cada
respuesta cita al [informe (LaTeX)](../../informe/informe.tex) (tablas y figuras) y a las decisiones
de arquitectura. Los `⚠️ Punto débil` marcan las limitaciones conocidas y cómo defenderlas
honestamente. El resumen con las tres tablas está en
[../resumenes/04-resultados.md](../resumenes/04-resultados.md).

---

### P: ¿Por qué corrieron 5 realizaciones y qué reporta el `media ± σ`?

Porque el simulador es **estocástico**: la semilla global gobierna todos los procesos aleatorios
—spawn, ruteo, selección de salida y de aula, tiempos de servicio y de permanencia
([D23](../../.claude/DECISIONES.md))—, de modo que cada realización es genuinamente independiente.
Reportamos **5 realizaciones (semillas 1–5)** por punto: la **media entre realizaciones** y el `± σ`
el **desvío estándar muestral entre ellas** (Fig. `evac-scalar` con errorbars, banda `± σ` en las
curvas). El `σ` no es error de medición: mide cuánto depende el resultado de la realización particular
—un observable en sí, como en `N=300` donde explota (Informe §Realizaciones).

### P: ¿Qué son los dos regímenes de la evacuación y dónde está el codo?

El `t_evac` crece con `N` en dos regímenes (Tabla `tab:evac`, Fig. `evac-scalar`):

- **Suave (hasta N ≈ 200):** las escaleras **absorben** el flujo. Multiplicar la capacidad ×5 (40 a
  200) apenas da ×1.6 en el promedio (38.9 → 63.2 s). Manda la **longitud del recorrido**.
- **Saturado (desde N ≈ 300):** cola sostenida en la boca. El máximo **salta** de `129.3 ± 8.5`
  (N=200) a `243.5 ± 102.8 s` (N=300) y se despega del promedio. Manda el **drenaje de la cola**.

El **codo está en N ≈ 200–300**: el cuello de botella es la **boca de escalera** y sólo se revela al
saturarla, lo que motivó extender el barrido de `N ≤ 120` a `N ≤ 500`
([D25](../../.claude/DECISIONES.md)).

### P: ¿Por qué la distribución de tiempos de evacuación es bimodal?

Porque hay **dos poblaciones geométricamente distintas** (Fig. `evac-hist`): el **lóbulo corto** son
los de **planta baja**, que salen directo, y el **lóbulo lento** los de **planta alta**, que bajan la
escalera *switchback* a **velocidad reducida** (`≈ 0.59 m/s`, `speed_factor = 0.38`). Al crecer `N` el
segundo lóbulo se desplaza a la derecha y domina; el heatmap por origen (Fig. `tevac`) lo confirma:
PB mediana **37 s**, P1 mediana **63 s**.

### P: ¿Por qué la dispersión explota en N=300? ¿Qué pasó con la corrida de 427 s?

Porque `N=300` cae justo en el cruce de regímenes, al borde de saturar, donde una fluctuación decide
si se forma un atasco: **cuatro de las cinco** realizaciones terminan con máximos de **190–206 s**,
pero en **una** un **atasco transitorio que se disuelve solo** estira el máximo a **427 s** (informe
§Evacuación), disparando el `σ` del máximo a `± 102.8 s` sobre media `243.5 s`. Que a `N=400` la
dispersión vuelva a bajar (`251.4 ± 3.6 s`) prueba que es efecto de cruce y no sistemático —el valor
de correr 5 semillas: con una sola podríamos haber caído en la de 427 s o en las de ~200 y sacado
conclusiones opuestas.

⚠️ **Punto débil (el atasco de 427 s):** es **transitorio y se disuelve solo** —el edificio se vacía
(evacuados 298.8 de 300)—, no un deadlock permanente; ese existía **antes** y se arregló
([D24](../../.claude/DECISIONES.md)). Lo defendemos mostrándolo: es *por qué* reportamos `media ± σ` y
no un solo número.

### P: ¿Por qué ≈ 1 agente por corrida no evacúa, y por qué eso NO empeora con N?

El remanente es `≈ 1` agente/corrida y se mantiene entre **1.0 y 1.2** en todo el barrido, incluso con
`N=500` (evacuados 499.0 → 1.0; Tabla `tab:evac`). Que **no escale con `N`** es la clave —si fuera
congestión de la escalera crecería con la capacidad—: el descenso fluye sin atascos permanentes. Sus
dos causas son de **borde**, no de escala:

1. **Caso borde del criterio de evacuado (ec. `tevac`).** Casi siempre es el **último agente en
   tránsito** cuando termina la corrida: el output deja de escribir cuadros al vaciarse el edificio y
   el último en camino no alcanza a "cruzar" antes del último cuadro. No es evacuación fallida.
2. **Livelock esporádico de jamba en PB** (aislado): un agente **aislado** con dirección deseada casi
   paralela a la jamba alterna escape de contacto ↔ atracción al objetivo (velocidad `∼ vd` pero
   desplazamiento neto **nulo**). Siempre un agente por punto, no congestión.

### P: ¿El remanente es un problema del modelo o del criterio de evacuado? Defiendan el criterio.

**Ambas cosas, y las separamos honestamente.** El criterio (ec. `tevac`, §Introducción) es
**operativo**: evacuado = deja de figurar en el archivo de salida antes del último cuadro registrado;
es **objetivo y reproducible**, y su costo conocido es el **caso borde** —como el output no escribe
cuadros vacíos, el último agente en tránsito puede quedar sin registrarse aunque estuviera a segundos.
Eso es **del criterio**, no del modelo, y afecta a lo sumo un agente. El **livelock** sí es **del
modelo** (límite del CPM, [D17](../../.claude/DECISIONES.md)), pero esporádico y aislado; ninguno
empeora con `N`, así que no invalida la conclusión de vaciado completo.

⚠️ **Punto débil (livelock residual):** es un límite real del CPM en jambas, mitigado llevando el
contacto de pared al núcleo duro (`d ≤ rmin`) —la oscilación-en-esquina cayó de **20.1% a 5.5%** y los
atascados de **2/30 a 0/30** ([D17](../../.claude/DECISIONES.md))—, pero no desaparece por completo.
Ver [q-fisica-cpm.md](q-fisica-cpm.md).

### P: ¿Cómo defienden que el edificio se vacía completo si siempre queda ~1?

Con el número: **evacuados `≥ N − 1` en todo el rango hasta `N=500`** (Tabla `tab:evac`;
39.0/78.8/118.8/199.0/298.8/399.0/499.0), con remanente **constante en ≈1**, no proporcional a `N`
—`N=40` deja 1.0 y `N=500` también—. Si el edificio no se vaciara el faltante crecería con la
capacidad; además ningún agente queda retenido en la escalera ni en su boca (el remanente es el último
en tránsito o un livelock aislado, ambos acotados). Conclusión: *el edificio se vacía completo en todo
el rango, con un residuo de borde de un agente que no escala*.

### P: ¿Por qué el pico de ingreso baja ~11× de 1 a 10 min si entra la misma cantidad de gente?

Porque lo que congestiona **no es el total sino el caudal**. Con `Nmax = 120` fijo el input es la
ventana `Ta` y el caudal `Q = Nmax/Ta` (ec. `caudal`): 120 / 24 / 12 ag./min para 1 / 5 / 10 min.
Concentrar 120 en **1 min** los agolpa en el frente del kiosco → pico **53.0 ± 2.1**; repartirlos en
**10 min** los procesa casi al ritmo que llegan → pico **4.8 ± 0.8**, una reducción **∼11×**
(53.0/4.8; Tabla `tab:ingreso`), visible como pico agudo y temprano vs meseta baja y extendida (Fig.
`ingreso-pob`).

### P: ¿Qué significa que el kiosco "satura" y de dónde sale la pendiente 0.44?

El kiosco es un **servidor con cola** de tasa de atención fija: "satura" = llega gente más rápido de
lo que atiende y el excedente se acumula (estudio complementario `Nmax ∈ {60..300}` a `Ta = 5 min`;
Tabla `tab:nmax`, Fig. `nmax-scalar`):

- **Antes de saturar (Nmax ≲ 180): supralineal.** Triplicar la población (`60 → 180`) multiplica el
  pico **×11** (`4.6 → 51.4`), porque cada agente que llega con el server ya sin abasto se suma a una
  cola que crece.
- **Después de saturar (Nmax ≳ 180): ∼lineal.** La **pendiente ≈ 0.44** sale de la tabla:
  `(104.0 − 51.4) / (300 − 180) = 52.6 / 120 ≈ 0.44` —la "fracción que ve la cola" ya saturado.

### P: ¿Qué muestran los heatmaps que las curvas no?

El **dónde**: las curvas dan cuánto y cuándo, los mapas anclan eso a la **geometría** (§Mapas de
calor). **Densidad de evacuación** (Fig. `dens-evac`, `N=120`): puntos calientes en el **pasillo
central y los pies de escalera** —el cuello del régimen saturado—; **densidad de ingreso** (Fig.
`dens-ing`, 1 min): la **cola del kiosco**, justificando la zona observable; **`t_evac` por origen**
(Fig. `tevac`, `N=120`): descompone la bimodalidad por *aula* —PB `∼14–66 s` (mediana 37) vs P1
`∼29–91 s` (mediana 63).

### P: ¿Por qué eligieron el kiosco como zona observable y no "antes de la escalera" como sugería el enunciado?

Porque el enunciado dice "una zona, **por ejemplo** antes de la escalera", y ahí **no hay congestión
apreciable** (§Zona de interés del Ingreso): la *switchback* es ancha, sólo la mitad sube, repartida
entre dos escaleras, así que el pie queda casi vacío. La congestión **real** se forma en el **frente
del kiosco**; medir donde no pasa nada daría un observable plano, y el "por ejemplo" habilita medir
donde efectivamente hay congestión (zona `R = [2,14] × [42,52]` en `z=0`, ec. `nzona`).

### P: En Ta=1 dicen que "la cola no drena". ¿Es un problema?

No, es **el resultado**: para la ventana de 1 min, `∼29 agentes siguen en la zona al final` (§Ingreso)
—el servidor queda **saturado** dentro del horizonte simulado—. Un caudal de 120 ag./min sobre un
kiosco de tasa fija **no se puede procesar** en el tiempo de la ventana; la conclusión operativa es
justamente ésa: si todos entran de golpe el kiosco no da abasto. El tiempo total (`Ta + 250 s`) es
amplio, así que el no-drenaje es físico, no numérico.

### P: ¿No es sospechoso que el ingreso principal (Ta) y el complementario (Nmax) den lo mismo en el punto común?

Al contrario, es **consistencia**: el punto `Ta=5, Nmax=120` aparece en **ambas** tablas y da idéntico
(`21.0 ± 3.6` pico, `8.9 ± 2.3` promedio; Tablas `tab:ingreso` y `tab:nmax`). Es el mismo experimento
compartido entre dos barridos ortogonales (uno varía la **ventana**, el otro el **`Nmax`**) y sirve de
**check de reproducibilidad**: si no coincidiera, algo estaría mal en el pipeline.

### P: ¿El barrido llega hasta N=500 y no más? ¿Por qué se corta ahí?

Porque `N=500` es el borde donde **las corridas todavía completan la evacuación** con `t_evac` bien
definido ([D25](../../.claude/DECISIONES.md)). Se descartó `N=600–800`: a `N=800` la boca de escalera
**se arrastra sin completar el vaciado** (límite documentado en [D24](../../.claude/DECISIONES.md)) y
`t_evac` queda mal definido para los no evacuados. El `max_time` del builder **escala con `N`** por
encima de 120 (`max(400, 400 + (N−120)·1.2) s`) para dar margen; los puntos `N ≤ 120` usan exactamente
400 s y no cambiaron.

### P: El modo crisis usa vd = 2.0 m/s. ¿Cambia eso las conclusiones sobre los regímenes?

El modo crisis (`vd = ve = 2.0 m/s` frente a 1.55 normal) es la velocidad deseada de emergencia, del
orden de los modelos de escape (Helbing 2000; §Perfil físico por generador,
[D22](../../.claude/DECISIONES.md)). Sube la velocidad **libre** —así que en el régimen suave los
tiempos son más cortos—, pero **no cambia el cuello de botella**: la escalera tiene `speed_factor =
0.38` y ancho fijo, de modo que su capacidad de drenaje es la misma; por eso los dos regímenes
persisten (el codo lo pone la saturación de la boca, no la velocidad). El `Δt` se acota al perfil más
rápido (`0.0375 s`) para mantener estable la integración.

---

## Navegación

- Resumen de resultados con las tres tablas: [../resumenes/04-resultados.md](../resumenes/04-resultados.md)
- Cómo se corren los barridos y se computan los observables: [../resumenes/03-simulaciones.md](../resumenes/03-simulaciones.md)
- Modelado del escenario (aulas, kiosco, escaleras, salidas): [q-escenario-modelado.md](q-escenario-modelado.md)
- Física del remanente / livelock / escalera: [q-fisica-cpm.md](q-fisica-cpm.md)
- Fuentes: [informe (LaTeX)](../../informe/informe.tex) ·
  [DECISIONES.md](../../.claude/DECISIONES.md) (D17 livelock, D22 modo crisis, D23 semilla, D24
  deadlock, D25 barrido extendido)
