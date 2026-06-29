# Enunciado Trabajo Final Simulación de Sistemas

El trabajo final de la materia consiste en ampliar un simulador peatonal desarrollado durante la cursada para soportar mapas con más de una planta, es decir, mapas en 3D. El simulador original solo soporta mapas en 2D, donde todos los elementos (paredes, salidas, ubicaciones de servidores y generadores) se encuentran en el mismo plano.

## Nuevas consideraciones para desarrollar la ampliación

* Ahora los agentes pueden tener una coordenada z diferente a 0.0, lo que significa que pueden estar en diferentes plantas del edificio o recorriendo la escalera.
* La velocidad en las escaleras es menor que en el plano.
* La deteccion de vecinos es independiente por cada planta, salvo en las escaleras. Verificar que las paredes (su punto mas cercano) tambien sean detectadas como vecinos y los vertices de las mismas cuando el vertice sea el punto mas cercano.
* El grafo de navegación ahora debe ser 3D. Dejar el generador automatico de grafos en cada planta de forma independiente y luego unir estos grafos a través de las escaleras.
* El simulador puede elegir entre dos modelos físicos: Social Force Model (SFM) y Contractile Particle Model (CPM). Para este trabajo, usar siempre CPM.
* El grafo de navegación utiliza el algoritmo A*, hay que actualizarlo para que funcione en 3D. La heurística de A* debe ser la distancia euclídea entre los nodos, considerando las tres coordenadas (x, y, z).
* La Animacion de cada planta que siga siendo 2D, es decir circulos coloreados. Pero agregaremos otro tipo de animación en el que estas dos plantas se puedan visualizar en un espacio 3D, una arriba de otra, con una vista a 45º, En esta vista los circulos podrian verse como cilindros.

Estas son algunos detalles que anticipamos, sin embargo la ampliación puede impactar más módulos.

## Escenario a simular

El trabajo incluye simular un escenario para observar los nuevos cambios.

### Escuela

El sistema a simular es una escuela de dos o más plantas, con aulas, pasillos, escaleras y una zona de recreo con un kiosco.
Cada escenario del sistema debe ser estudiado mediante una variación de un input y un observable elegido. Se debe contar con scripts para ejecutar la simulación variando el input y generar los gráficos de los observables elegidos.

#### Evacuación

El primer escenario a simular es la evacuación de los alumnos que se encuentran en sus aulas y deben dirigirse a la salida del edificio.
El simulador ya cuenta con un modo para representar el comportamiento de agentes en situación de crisis.

**Inputs**: Capacidad Total (Nro total de agentes)
**Observable primario**: Distribución de tiempos de evacuación.
**Observable escalar**: Tiempo de evacuación promedio o máximo.

#### Ingreso

El otro escenario a simular es el ingreso de los alumnos que se dirigen a sus aulas.

**Inputs**: Tomar la Capacidad maxima usada en el punto anterior (Nmax) y variar el caudal (Si todas esos Nmax agentes llegan distribuidos en 1 minuto, en 5 minutos, o en 10 por decir algo, Uds usen los caudales de ingreso que mejor les resulten).
**Observable primario**: Curva de poblacion vs tiempo en alguna zona de interes, puede ser la escalera principal o un area antes de la escalera
**Observable escalar**: Máxima ocupación o promedio en el area elegida


*Opcional* en ambos casos pueden hacer mapas de calor de densidad y en el caso de evacuacion, mapa de tiempos de evacuacion (se toma el punto de origen de cada agente y se lo colorea a ese punto segun el tiempo que ese agente tardo en evacuar. O se toma una grilla y se promedian los tiempos de evacuacion los agentes de cada celda.)
