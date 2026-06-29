El generator recibe:
-tiempo que dura la creacion de agentes
-tiempo de apagado (osea que no creamos agentes)
-donde estara posicionado el generador (puede ser más de una posición)
-caudal (persona por minuto, por cada puerta que haya)
-tipo de entrada (calm, o crear todos de una (NO ejemplo de la oveja))

+El generador en sí tiene un area, es un pequeño bloque donde se pueden crear agentes. Existe un caso, que solo se puede dar al inicio de la simulación en el que esa area es todo el mapa (exceptuando paredes/servicio u otros agentes), sino esa area son las entradas/salidas.  En dichas areas que son puertas/salidas entran 3 personas por cada metro de puerta, si desde input nos dan un caudal que es mayor a 3p/m debemos recortarlo a 3p/m y tirar un warning avisando que hicimos eso.
+Hay que tener en cuenta que estas areas además de entradas también son salidas, hay que chequear que al momento de querer crear a uno no haya otro saliendo y se superpongan, en el caso de que se de algo así y no podamos crear el agente simplemnte hay que ciclar hasta que si podamos, no importa cuanto tiempo tarde (si tarda más de X segundos podemos tirar un warning). Para evitar esto, el team 7 nos debe dar la información de la ubicación de los agentes ya existentes. 
+No usamos ningun tipo de función wait o sleep porque no somos una función que es llamada en momento X, sino una maquina que una vez que es activada no para. Sabemos siempre en que tiempo nos ubicamos (por eso no necesitamos wait/sleep como sabemos cuanto va el ticker le decimos okay hasta q no llegue a 'tiempo actual + tiempo de apagado' no hacer nada, es un sleep pero casero), y a partir de eso y los dos primeros parametros que recibimos nos manejamos. 
+Hay un par de opciones a como inicializar los agentes pero en todas se cumple que NO se superponen (usando codigo de anteriores TP's). Entre esas opciones tenemos por un lado la variación de que entren de a uno de forma ordenada (calm), o en 'batch', eso es defindo por el parametro 'tipo de entrada'. O también cada cuanto entran y de a cuantos, definidos por los parametro 'caudal', 'tiempo de apagado' y     'tiempo de creacion'.
+En algun punto de la creación del agente nosotros le damos, a cada uno, un id, un plan, (algo más?). Grupo 2 nos pasa los X cantidd de planes que le piden desde input. En el caso de que haya menos planes que agentes en el caudal habra agentes que hagan los mismo. En el caso que haya más planes que agentes (medio ridiculo) habra planes que ni se cumplen. No asignamos ninguna task, solo el plan. 
+Creamos una función que frena, y otra que vuelve a activar el generador, por q si, yo q se suena útil. 

Duda:
-En el caso del agente q no puede entrar porq hay otros saliendo, q pasa si se termina el tiempo de spawn?? ya esta quedo out?
+CREO: al tener el caudal y tiempo de spawn, sabemos la cantidad total final de agentes que hay q crear, si por diferentes razones esta creación tarda y no llega a darse dentro del tiempo de spawn NO importa, los seguimos creando, seria como creaciones atrasadas. 
-Puede haber multiples generadores?? en tal caso, cada uno deberia tener su propio id para q al tirar warnings poder avisar quien fue
-todo lo del ticker es re mistico, realmente existe?