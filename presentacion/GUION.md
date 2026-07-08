# Guion de la presentación oral — TP Final (≈13 min)

Para la oral usar **`tpfinal_presentable.pdf`** (los MP4 están embebidos: **click sobre el
fotograma para reproducir**; abrirlo en Adobe Acrobat/Reader). El entregable con links de
YouTube es el otro PDF y no se usa para hablar.

- **Presupuesto total: ~13:15** con las 26 diapositivas. Si en el ensayo se pasa de 13:00,
  el plan de recortes está al final (recupera hasta ~1:20).
- Los tiempos por diapositiva son orientativos; los **checkpoints acumulados** al final de
  cada sección son lo que hay que respetar.
- Convención: **Decir** = idea hablada (no leer los bullets textuales); *[acotación]* =
  acción en escena.

---

## Sección 1 — Ampliación 3D *(checkpoint: terminar ~4:05)*

### 1. Portada — 0:15
**Decir:** Buenas, somos el grupo 5. Nuestro TP final fue ampliar a 3D un simulador peatonal
que era 2D: soportar edificios de varias plantas conectadas por escaleras, y usarlo para
estudiar una escuela en dos situaciones, la evacuación y el ingreso de los alumnos.

### 2. Separador "La ampliación a 3D" — 0:05
**Decir:** Primero, qué hubo que cambiarle al simulador y por qué.

### 3. El objetivo: mapas con más de una planta — 0:45
**Decir:** El simulador de partida era enteramente 2D: la posición y la velocidad eran un par
(x, y) usado en cascada por todos los módulos. El objetivo fue que los agentes tengan una
coordenada z —su planta, o su altura intermedia mientras recorren una escalera— y que la
vecindad, la física, el grafo de navegación y la salida la respeten. La decisión central fue
un enfoque **híbrido**: las posiciones y velocidades pasan a 3D, pero la dinámica sigue
siendo **planar** en cada planta. ¿Por qué? La física peatonal ocurre en el plano: así no
reescribimos el modelo, y evitamos que la z se mueva por fuerzas del plano por error. La z
no es un grado de libertad dinámico: se **interpola** con el avance sobre la escalera, no
hay velocidad vertical. *[señalar el diagrama de plantas apiladas]*

### 4. Modelo físico: partículas contráctiles (CPM) — 0:40
**Decir:** El modelo físico es el CPM de Baglietto y Parisi, en la variante anisotrópica con
evitación. Cada agente es una partícula de radio variable: se contrae al radio mínimo cuando
hay contacto y se re-expande al avanzar libre; la velocidad deseada apunta al objetivo con
maniobras de evitación. Para el 3D lo importante es que la dinámica ocurre en el plano de la
planta del agente —solo interactúa con las paredes de *su* planta— y que sobre la escalera la
velocidad se reduce por un factor configurable mientras la z se interpola con el avance.

### 5. Qué cambió en cada módulo, y por qué — 0:50
**Decir:** Este es el resumen del trabajo de ingeniería, módulo por módulo, con el porqué de
cada decisión. *[recorrer filas de arriba hacia abajo, sin leer textual]* Tipos base a 3D,
pero la z sin dinámica propia, porque la física del CPM es planar. Cada elemento de geometría
lleva su planta, porque un muro pertenece a una planta; la escalera es un tipo nuevo. El grafo
de navegación se genera **por planta** y se une con **aristas de escalera**, con A* de
heurística euclídea 3D; la visibilidad es por planta porque entre plantas hay una losa en el
medio. La detección de vecinos: una grilla por planta más un índice "puente" sobre la
escalera —el enunciado pedía exactamente eso—, preservando paredes y vértices como vecinos.
La física, planar en la planta actual, con el caso especial de la escalera. Y la salida gana
la columna z, para poder ubicar a cada agente en su planta y animar en 3D.

### 6. Escaleras *switchback* con descanso — 0:40
**Decir:** La escalera no es un tramo recto: es un *switchback*, dos tramos en L unidos por
un descanso a media altura, como en una escuela real. El truco de diseño: se modela como
**dos escaleras encadenadas** por el descanso, así reutilizamos todo el mecanismo multiplanta
—grafo, vecinos y física— sin reescribir el núcleo. Barandas y el perímetro del descanso
confinan al agente dentro de la huella; en tramos anchos un sesgo lateral separa carriles de
subida y de bajada, lo que da contraflujo ordenado. En la vista 3D se dibuja con peldaños.
*[señalar el diagrama]*

### 7. Lo que sólo aparece con dos plantas reales — 0:50
**Decir:** Esta lámina es lo que más nos enseñó el TP. Cada módulo pasaba sus tests en
aislamiento, pero al correr el primer escenario real de dos plantas **nadie subía a la planta
alta** — y sin un solo error: la simulación corría y "se veía bien". El problema eran caminos
de datos donde la z se perdía en silencio con un default cero: las tareas, los servers y los
generadores nacían o apuntaban a la planta baja. Además, el ruteo se quedaba parado al pie de
la escalera, porque el tope, en otra planta, "no es visible": hubo que cruzar explícitamente
la arista de escalera. Encontramos y arreglamos también un salto de z al pie —se excluye la
huella de la grilla del grafo y el cambio de objetivo se hace pegado al extremo— y un
*livelock* en jambas de puerta, que se resolvió endureciendo el criterio de contacto con la
pared al núcleo duro del agente. La lección: cuando se agrega una coordenada hay que auditar
**todos** los caminos de datos; los defaults silenciosos dan simulaciones plausibles pero
erróneas.

---

## Sección 2 — Escenario: Escuela *(checkpoint: terminar ~5:30)*

### 8. Separador "Escenario: Escuela" — 0:05
**Decir:** Con el simulador ampliado, el escenario: una escuela.

### 9. El mapa: recreo y edificio de dos plantas — 0:45
**Decir:** El mapa es de 60 por 60 metros con dos zonas. A la izquierda el **recreo**, de una
sola planta: patio abierto, un kiosco con cola y una salida propia. A la derecha el
**edificio** de dos plantas: un pasillo central, 16 aulas —cuatro por lado y por planta— y
las dos escaleras *switchback* en las puntas. Dato clave: la planta alta **no tiene salida**,
se evacúa bajando por las escaleras. Las aulas son recintos colectivos con una sesión de
clase y un timbre único de salida. *[señalar en la vista 3D: planta baja, descanso, planta
alta]*

### 10. Parámetros de las corridas — 0:35
**Decir:** El perfil físico es el de Baglietto y Parisi; en caminata normal la velocidad
deseada es 1.55 m/s, y en la evacuación usamos el **modo crisis**: 2.0 m/s, del orden de las
velocidades de escape de la literatura. El paso de integración queda acotado por el perfil
más rápido presente. La escalera, de 2.6 m de ancho, da una velocidad efectiva de ~0.6 m/s en
el tramo. Y todo lo que sigue son **5 realizaciones** por punto: la semilla gobierna todos
los procesos aleatorios —spawn, ruteo, elección de salida y de aula, tiempos de servicio—,
así que las realizaciones son independientes y reportamos media más/menos desvío.

---

## Sección 3 — Estudio 1: Evacuación *(checkpoint: terminar ~8:55)*

### 11. Separador "Estudio 1: Evacuación" — 0:05

### 12. Qué medimos en la evacuación — 0:30
**Decir:** Los N alumnos empiezan **dentro de las aulas de ambas plantas** y evacúan en modo
crisis hacia las dos salidas; los de la planta alta tienen que bajar por las escaleras. El
input es la capacidad total, 40, 80 y 120. El observable es la distribución de los tiempos de
evacuación de cada agente, y los escalares, el tiempo promedio y el máximo en función de N.

### 13. La evacuación desde ambas plantas — 1:00
*[▶ click en el video de la izquierda, N=40; dejarlo correr ~20 s mientras se habla; después
click en el de la derecha, N=120]*
**Decir:** A la izquierda, capacidad baja: se ve a los de la planta alta bajar por los dos
*switchback* —miren el giro en el descanso— y salir sin interferirse. A la derecha, con 120,
la dinámica es la misma pero aparece la congestión: colas en las puertas de las aulas y en
los pies de escalera. Esto es lo que después se ve en los números.

### 14. La distribución de tiempos de evacuación es bimodal — 0:40
**Decir:** La distribución de tiempos es **bimodal**, y la separación es física: el lóbulo
rápido es la planta baja, que sale directo; el lóbulo lento es la planta alta, que tiene que
bajar la escalera a velocidad reducida. Al crecer N el lóbulo lento se desplaza a tiempos
mayores y produce la cola larga. *[señalar los dos lóbulos y el corrimiento]*

### 15. El tiempo de evacuación crece con la capacidad — 0:40
**Decir:** Los escalares: al triplicar la capacidad, el promedio pasa de 48 a 67 segundos, y
el máximo de 93 a 151. La dispersión entre realizaciones también crece con N: más congestión,
más sensibilidad a la realización; donde las barras no se ven es porque quedan dentro del
marcador. Un detalle de honestidad: quedan ~1.4 agentes por corrida sin evacuar, por un
*livelock* contra muros —en la boca de la escalera de la planta alta o en el portón de
salida—, un límite conocido del modelo de fuerzas.

### 16. La huella espacial de la evacuación — 0:30 *(recortable)*
**Decir:** Los mapas de calor lo resumen espacialmente. La densidad muestra que el pasillo
central y las escaleras concentran el flujo. Y el mapa de tiempos por origen cuantifica el
costo de estar arriba: las aulas de la planta baja evacúan en 15 a 60 segundos; las de la
planta alta, en 60 a 140, porque tienen que bajar.

---

## Sección 4 — Estudio 2: Ingreso *(checkpoint: terminar ~12:25)*

### 17. Separador "Estudio 2: Ingreso" — 0:05

### 18. Qué medimos en el ingreso — 0:35
**Decir:** El segundo estudio invierte la situación: los 120 alumnos **llegan** repartidos
uniformemente en una ventana de 1, 5 o 10 minutos —o sea, caudales de 120, 24 y 12 agentes
por minuto— y van a sus aulas; los que entran por el recreo pasan antes por el kiosco. El
observable es la población en una zona de interés. Elegimos el frente del kiosco, y no el pie
de la escalera que sugiere el enunciado, por un motivo medido: la *switchback* es ancha, hay
dos, y sólo la mitad de los alumnos sube, así que el pie queda casi vacío; la congestión real
del ingreso se forma **frente al kiosco**.

### 19. La congestión se forma frente al kiosco — 0:50
*[▶ video de la izquierda, ventana de 1 minuto; luego el de la derecha, 10 minutos]*
**Decir:** Con la ventana de 1 minuto, la ola de llegada se apila frente al kiosco casi de
inmediato. Con 10 minutos entra la misma cantidad de gente, pero goteando: nunca se forma la
aglomeración. Es el mismo total, distinta intensidad.

### 20. Población en el kiosco durante el ingreso — 0:35
**Decir:** La serie temporal lo cuantifica: la ventana corta produce un pico **agudo y
temprano**; la larga, una meseta **baja y extendida**. La banda sombreada es el desvío entre
las 5 realizaciones. *[señalar pico vs meseta]*

### 21. Repartir la llegada descongestiona la zona — 0:35
**Decir:** El escalar: concentrar la llegada en 1 minuto da un pico de 53 agentes en la zona;
repartirla en 10 minutos lo baja a 5 — once veces menos. Y con la ventana de 1 minuto el
kiosco queda saturado: la cola no llega a drenarse dentro de la corrida. Conclusión
operativa: escalonar el ingreso descongestiona.

### 22. Estudio complementario: la congestión crece con la cantidad de alumnos — 0:30 *(recortable)*
**Decir:** Como eje ortogonal, fijamos la ventana en 5 minutos y variamos la cantidad total:
60, 120 y 180. Triplicar los alumnos multiplica el pico por once —de 4.8 a 51.6—: la
ocupación crece **más que proporcionalmente** en el rango explorado; la zona se satura.

### 23. La huella espacial del ingreso — 0:20 *(recortable)*
**Decir:** El mapa de calor confirma dónde ocurre todo: la cola del kiosco es el único punto
caliente; los pasillos y las entradas quedan tenues.

---

## Sección 5 — Conclusiones *(checkpoint: terminar ~13:15)*

### 24. Separador "Conclusiones" — 0:05

### 25. Conclusiones — 0:45
**Decir:** Cerrando. Uno: el simulador quedó ampliado a 3D de punta a punta — agentes que
recorren escaleras a velocidad reducida, vecinos y física por planta, grafo 3D, y animación
por planta más la vista apilada. Dos: la escalera *switchback* con descanso, confinamiento y
carriles de contraflujo se comporta de forma coherente. Tres: el tiempo de evacuación crece
con la capacidad y su distribución es bimodal — planta baja rápida, planta alta lenta.
Cuatro: la ocupación del kiosco crece al acortar la ventana de llegada, y a ventana fija
crece más que proporcionalmente con la cantidad de alumnos. Y cinco, el límite conocido: ~1.4
agentes por corrida no evacúan por el *livelock* de contacto con muros, que redujimos con el
contacto al núcleo duro pero no eliminamos.

### 26. Muchas gracias
**Decir:** Muchas gracias. Preguntas.

---

## Plan de recortes (si el ensayo pasa de 13:00)

En orden: **saltear la 23** (heatmap del ingreso, −0:20), **saltear la 16** (heatmaps de la
evacuación, −0:30), **saltear la 22** (complementario de N_max, −0:30). Con los tres recortes
se baja a ~11:55. Si se saltea la 22, en Conclusiones decir "crece más que proporcionalmente"
sin detenerse en los números.

## Preguntas esperables (respuestas cortas)

- **¿Por qué no hay velocidad vertical?** La z no es un grado de libertad: sobre la escalera
  queda definida por el avance planar (interpolación). Integrar fuerzas verticales no aporta
  al modelo peatonal y complica el CPM.
- **¿La heurística 3D del A* es admisible?** Sí: la distancia euclídea 3D nunca supera el
  costo real del camino (las aristas de escalera cuestan el largo del tramo inclinado), así
  que A* sigue siendo óptimo.
- **¿Por qué midieron el kiosco y no la escalera?** Lo probamos: en el pie de la escalera la
  señal es plana (escalera ancha, dos escaleras, sólo la mitad sube, llegada repartida). El
  enunciado dice "una zona, por ejemplo antes de la escalera": medimos donde efectivamente
  hay congestión.
- **¿Qué les pasa a los ~1.4 que no evacúan?** Es un *livelock* del modelo de fuerzas: la
  velocidad de escape del contacto con el muro pelea con la atracción al objetivo y el agente
  oscila en el lugar (velocidad instantánea alta, desplazamiento neto nulo). Con el contacto
  al núcleo duro lo eliminamos de las puertas de aula; persiste en la boca de la escalera de
  la planta alta y en el portón de salida. Es un agente aislado por punto, no congestión.
- **¿Cómo detecta vecinos un agente en la escalera?** La vecindad es por planta, salvo en la
  escalera: ahí un índice "puente" propio se acopla con las grillas de las dos plantas que
  conecta, así dos agentes sobre el mismo tramo siempre se ven. Las paredes se detectan por
  su punto más cercano, y sus vértices cuando el vértice es lo más cercano (está testeado).
- **¿Por qué CPM y no SFM?** Lo fija el enunciado. Eliminamos el SFM del código para no
  mantener dos modelos con cada cambio de interfaz.
- **¿Las 5 realizaciones son realmente independientes?** Sí: la semilla global se mezcla en
  todos los procesos aleatorios (spawn, ruteo, elección de salida y de aula, tiempos de
  servicio); entre semillas cambian todas las decisiones, no sólo el spawn.
- **¿De dónde sale la velocidad en la escalera?** De un factor por escalera aplicado a la
  velocidad deseada; quedó calibrado para una velocidad efectiva medida de ~0.6 m/s en el
  tramo, con z monótona con el avance.
