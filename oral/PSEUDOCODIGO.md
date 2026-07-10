**Grupo 5 · Simulación de Sistemas · ITBA.** El simulador era 2D porque el tipo base de posición era
`Vec2 (x,y)`, usado en cascada por todos los módulos. Ampliarlo a 3D es representar la coordenada `z`
(planta + altura en escalera) y que física, grafo, vecinos y salida la respeten. Abajo, los cambios
importantes con pseudocódigo y una explicación breve de cada uno.

---

## 1. Tipos base: `Vec3` híbrido, `z` interpolada (D1, D2)

**Qué:** la posición pasa a `Vec3 (x,y,z)`, pero la dinámica sigue operando en el plano (`Vec2`). La
`z` **no** es un grado de libertad: no hay `vz`; sobre una escalera surge de **interpolar** el avance.
**Por qué:** la física peatonal es planar; migrar todo a 3D arriesgaba mover la `z` por fuerzas
planares y obligaba a reescribir la geometría. El híbrido deja el núcleo del CPM intacto.

```
Vec3 = (x, y, z)

distanceTo(a, b) = sqrt((a.x-b.x)^2 + (a.y-b.y)^2 + (a.z-b.z)^2)   // heurística del A* 3D
xy(v)            = Vec2(v.x, v.y)                                  // proyección para la física

// Altura sobre una escalera: interpolación lineal del avance (0 = pie, 1 = tope).
Stairs.zAt(x, y):
    t <- progreso de (x,y) proyectado sobre el eje pie->tope, recortado a [0,1]
    return lerp(foot.z, top.z, t)
```

---

## 2. Física (CPM): el wrapper `integrate` (D9)

**Qué:** el CPM se conserva planar. Un wrapper detecta si el agente está sobre una escalera, **reduce
la velocidad deseada** por un factor, corre la dinámica en el plano y **actualiza la `z`** por
interpolación. **Por qué:** subir/bajar es más lento y la altura debe seguir al avance sin volver
vertical la dinámica.

```
integrate(agent, footTarget, dt):
    stair      <- locateStair(agent, footTarget)          // null si no está en escalera
    speedScale <- stair ? stair.speedFactor : 1.0         // p.ej. 0.38 en el tramo
    walls      <- wallsForFloor(agent, stair)             // sólo las de su planta (ver 4)

    integratePlanar(agent, footTarget.xy, dt, speedScale, walls)   // CPM clásico, en el plano

    if stair != null:
        agent.z <- stair.zAt(agent.x, agent.y)            // engancha la altura al avance
```

---

## 3. Anti-tunneling con las paredes de la planta actual (D9)

**Qué:** el chequeo que impide atravesar paredes usa **sólo las paredes de la planta del agente** (o la
unión de las dos plantas si está sobre una escalera). **Por qué:** las aulas de planta baja y planta
alta comparten `(x,y)`; que un muro de arriba frenara a un agente de abajo (misma proyección) sería un
error.

```
wallsForFloor(agent, stair):
    if stair != null: return wallsOfBothFloors(stair)     // pie + tope, en los descansos
    else:             return wallsOfFloor(nearestFloor(agent.z))

moveWithWallCheck(agent, from, to, walls):
    if segment from->to no cruza ninguna wall de `walls`: mover a `to`
    else: deslizar paralelo a la pared; si no, despegar un poco   // nunca atraviesa
```

---

## 4. El fix del contacto de pared: núcleo duro `rmin`, no `rmax` (D17)

**Qué (el cambio más importante de fuerzas):** una pared cuenta como **contacto** (dispara el escape
perpendicular) **sólo** si el cuerpo físico la tocaría, `d <= rmin`. En la franja `rmin..rmax` actúa
una **repulsión suave**. **Antes:** la pared contaba como contacto en todo el espacio personal
(`rmax`), y el escape perpendicular peleaba con la atracción al objetivo -> el agente **oscilaba
pegado a la jamba** de una puerta (livelock). **Resultado:** oscilación en esquina 20.1% -> 5.5%,
agentes atascados 2/30 -> 0/30. Es coherente con el contacto anisotrópico que el A-CPM ya usa entre
agentes.

```
for each pared vecina w a distancia d del agente:
    if d <= rmin:                                  // contacto duro: el cuerpo toca la pared
        v_escape <- perpendicular alejándose de w  // domina el paso
    else if d <= rmax:                             // espacio personal: repulsión SUAVE
        push <- Aw * exp(-d / Bw) en dirección contraria a w   // aparta sin anular el pull al target
    // d > rmax: la pared no influye
```

---

## 5. Grafo de navegación 3D: una malla por planta unida por escaleras (D6)

**Qué:** se genera el grafo de cada planta por separado (con el generador de grilla existente) y se
**cosen** las plantas con aristas de escalera. El costo de la arista de escalera es el **largo real
del tramo inclinado** (distancia 3D). El A* usa **heurística euclídea 3D**. **Por qué:** el enunciado
lo pide; el costo 3D evita que el A* prefiera una escalera por su proyección planar corta, y la
heurística 3D es admisible (nunca sobreestima).

```
buildGraph(geometry):
    nodes, edges <- {}
    for each planta z in geometry.floors:
        g <- reduceGrid(wallsOn(z))                       // malla 2D de esa planta
        subir cada nodo de g a altura z, agregarlo a nodes
        conectar nodos visibles dentro de la planta (costo = distancia planar)
    for each escalera s:
        agregar nodos s.foot y s.top
        conectar cada uno al nodo visible más cercano de SU planta
        agregar arista (s.foot <-> s.top, costo = distanceTo(s.foot, s.top))   // largo 3D
    return graph(nodes, edges)

heuristic(n, goal) = distanceTo(n, goal)                  // euclídea 3D -> admisible
```

---

## 6. Visibilidad y ruteo por planta, y los fixes de escalera (D11, D21, D24)

**Qué:** la visibilidad es **por planta** (hay una losa entre plantas); el cruce ocurre sólo por la
arista de escalera. El agente sigue un *furthest visible point* (FVP) como objetivo intermedio.
**Fixes que sólo aparecieron con dos plantas reales:**

- **D11 — "nadie subía":** el FVP ruteaba hasta el **pie** y se quedaba (el tope, en otra planta, "no
  es visible"). Fix: al alcanzar el pie, avanzar el hop al tope; y un agente ya sobre la escalera
  recibe como hop el extremo hacia la planta del destino.
- **D21 — "salto de `z`":** agentes que saltaban a media escalera en un cuadro. Dos causas: la grilla
  ponía nodos **dentro de la huella** de la escalera, y el cambio de hop del pie al tope ocurría lejos
  del pie. Fix: excluir esos nodos + comprometer el salto **pegado al pie** (la `z` engancha suave).
- **D24 — deadlock de boca:** con muchos agentes se formaba un arco atascado en la boca. Fix: boca
  **ancha** (cada agente apunta a su proyección, no a un punto único) + descansos bien mallados.

```
isVisible(a, b):
    if floor(a.z) != floor(b.z): return false             // losa entre plantas
    return segmento a->b no cruza ninguna pared de esa planta

nextHop(agent, target):
    if agent está sobre una escalera (z entre plantas):
        return extremo de la escalera hacia la planta de `target`     // D11: sigue subiendo/bajando
    path <- A*(agent, target)
    return punto más lejano de `path` aún visible desde `agent`       // FVP
```

---

## 7. Vecinos (CIM) por planta + puente por escalera (D8)

**Qué:** una grilla CIM (Cell Index Method) **por planta**; un agente ve sólo vecinos de su planta,
**salvo sobre las escaleras**, donde un índice "puente" acopla las dos plantas que conecta.
**Por qué:** el enunciado pide vecindad independiente por planta salvo en escaleras; una grilla 3D o
"asignar la planta más cercana" romperían la continuidad justo en el tramo.

```
neighborsOf(agent):
    loc <- classify(agent)
    if loc es una planta f:
        result <- grid[f].within(agent, rmax)                         // su grilla
        for each escalera que aterriza en f: result += bridge[.].within(agent)  // acople
    else:  // sobre una escalera
        result <- bridge[loc.stair].within(agent)
        result += grid[pieFloor].within(agent) + grid[topeFloor].within(agent)
    return ordenados por distancia

classify(agent):
    if agent.z ~= algún nivel de planta: return esa planta
    else return la escalera cuya huella contiene (x,y)                // z entre plantas
```

**Paredes y vértices como vecinos:** la distancia a una pared usa su **punto más cercano**; al
proyectar el agente sobre la recta del muro se **recorta** el parámetro `t` a `[0,1]`, de modo que
cuando el más cercano es un extremo, se detecta el **vértice**.

```
Wall.closestPointTo(p):
    t <- proyección de p sobre la recta del muro
    t <- clamp(t, 0, 1)               // recorte -> el extremo (vértice) cuando corresponde
    return foot + t * (top - foot)
```

---

## 8. Salida y reproducibilidad (D10, D23)

**Salida:** se agrega la columna `z` inmediatamente después de `y`, para ubicar al agente en su
planta: `tout; x; y; z; vx; vy; state; id`.

**Reproducibilidad (opt-in):** la semilla se propaga a **todos** los procesos aleatorios (spawn,
ruteo, selección de salida/aula, tiempos de servicio). Sin semilla el comportamiento es el histórico.

```
rng(salt):
    if hay semilla configurada: return Random(seed XOR hash(salt))    // determinístico por stream
    else:                       return Random()                        // comportamiento histórico
```

---

## 9. Escenario y qué se midió (contexto)

Sobre la ampliación se estudia la **Escuela** (2 plantas, 16 aulas, recreo con kiosco, dos escaleras
*switchback*). **Modo crisis:** cada generador puede declarar su `max_velocity`; en la evacuación los
agentes usan `vd = 2.0 m/s` (emergencia). Dos estudios:

- **Evacuación** (input: capacidad `N`): distribución de tiempos de evacuación. El edificio se vacía
  completo hasta `N=500`; dos regímenes (suave hasta `N ~ 200`, escaleras saturadas desde `N ~ 300`);
  distribución bimodal (planta baja rápida, planta alta lenta por bajar la escalera).
- **Ingreso** (input: ventana `Ta`): población en la zona del kiosco. Concentrar 120 alumnos en 1 min
  da un pico ~11x mayor que repartirlos en 10 min.

Cada punto es la media de **5 realizaciones**. Suite de tests: **143, 0 fallos**.
