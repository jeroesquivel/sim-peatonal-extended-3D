# Tipos base + Input/Output + Reproducibilidad

Raíz del cambio a 3D: tipos de posición/velocidad, cómo entra la `z` por el input, cómo
sale por el output, cómo se sembró la aleatoriedad. Snippets citados verbatim.

Decisiones: **D1** (híbrido `Vec3`+`Vec2`), **D2** (sin `vz`, `z` interpolada), **D3** (`z`
por elemento), **D4** (`STAIRS.csv`), **D10** (columna `z`), **D23** (semilla opt-in) y la
lección de los defaults **`z=0`** silenciosos (D11/D16/D18).

---

## 1. El híbrido `Vec3` + `Vec2` planar (D1)

Todo era 2D porque el tipo base era `core/Vec2`. En vez de migrar todo, se creó `Vec3` para
posición/velocidad y se **conservó** `Vec2` para la dinámica horizontal (planar). La clave del
híbrido: **dos** distancias — la euclídea 3D es la heurística admisible del A* multiplanta; la
horizontal proyecta a `xy()` y razona dentro de una misma planta.

```java
// src/main/java/ar/edu/itba/simped/core/Vec3.java:40-48
    /** Distancia euclídea 3D (heurística admisible del A* multiplanta). */
    public double distanceTo(Vec3 other) {
        return sub(other).norm();
    }

    /** Distancia planar (ignora {@code z}) — útil dentro de una misma planta. */
    public double horizontalDistanceTo(Vec3 other) {
        return xy().distanceTo(other.xy());
    }
```

`xy()` es el puente al mundo planar (la física trabaja ahí); `Vec2.withZ` eleva a 3D cuando
hace falta. **Por qué no migrar todo a `Vec3`** (descartado en D1): arrastraría una `z` en cada
fuerza planar, con riesgo de que la altura se mueva por error; el híbrido la aísla a la
escalera. Ver [informe §Implementación](../../informe/informe.tex) y [D1](../../.claude/DECISIONES.md).

---

## 2. Sin `vz`: la `z` se interpola, no se integra (D2)

La `z` no es grado de libertad dinámico: no hay `vz`. `AgentState` guarda sólo velocidad planar
(`vx, vy`); la `z` cambia por interpolación del avance en escalera.

```java
// src/main/java/ar/edu/itba/simped/core/AgentState.java:18-25
    private double x;
    private double y;
    private double z;
    private double vx;
    private double vy;
    private double radius;
    private BehaviorState state;
    private AgentProfile profile;
```

Detalle fino: el `setPosition(x, y)` de 2 args —el que llama el CPM cada `dt`— **conserva la
`z` a propósito**; sólo la tocan `setZ` o el `setPosition` de 3 args. La regla vive en
`core/Stairs`: `z = lerp(foot.z, top.z, progreso_planar)`, con `progressAt` = proyección
recortada a `[0,1]` (pie=0, tope=1).

```java
// src/main/java/ar/edu/itba/simped/core/Stairs.java:90-92
    public double zAt(double px, double py) {
        return foot.z() + (top.z() - foot.z()) * progressAt(px, py);
    }
```

> La **física** que consume esto (velocidad reducida, detección "estoy en escalera") va en
> [`02-fisica-cpm.md`](02-fisica-cpm.md); acá `Stairs` como **tipo** geométrico.

Ver [informe §Implementación](../../informe/informe.tex) y [D2](../../.claude/DECISIONES.md).

---

## 3. Geometría: una planta `z` por elemento, no `z` por extremo (D3)

Los CSV traen `z1`/`z2` por extremo; antes se descartaban. D3: cada elemento plano lleva **una
única** planta `z` (`double`) y conserva su forma en `Vec2`. Si las dos `z` difieren es error de
datos: warning y se toma `z1`. `parseFloorZ` colapsa `z1`/`z2` (las escaleras, único `z1≠z2`
legítimo, van en `STAIRS.csv`); los readers (p.ej. `WallsCsvReader`) ahora **honran** la `z`.

```java
// src/main/java/ar/edu/itba/simped/input/csv/CsvParse.java:24-34
    static double parseFloorZ(String z1Token, String z2Token, Path path, int lineNumber, String layer) {
        double z1 = parseDouble(z1Token, path, lineNumber, layer);
        double z2 = parseDouble(z2Token, path, lineNumber, layer);
        if (Math.abs(z1 - z2) > 1e-9) {
            LOG.warning(String.format(
                    "layer %s %s:%d: elemento planar con z1=%.3f != z2=%.3f; usando z1. "
                            + "(Las escaleras se declaran en STAIRS.csv)",
                    layer, path.getFileName(), lineNumber, z1, z2));
        }
        return z1;
    }
```

**Por qué no `z` por extremo** (descartado): endpoints en `Vec3` permitirían muros inclinados
pero romperían la planaridad de la que dependen el CIM por planta y el anti-tunneling. Un muro
pertenece a una planta. Ver [D3](../../.claude/DECISIONES.md).

---

## 4. `STAIRS.csv`: la escalera como eje con `z` por extremo (D4)

Elemento nuevo que conecta plantas, en archivo **nuevo** `STAIRS.csv` (deja intactos los CSV
existentes). Cada fila es el **eje**: extremo 1 = pie `(x1,y1,z1)`, extremo 2 = tope
`(x2,y2,z2)`, con `z1≠z2` (invariante del record; distingue escalera de elemento plano). Formato
`block_name, x1,y1,z1, x2,y2,z2, width[, speed_factor]` (default `DEFAULT_SPEED_FACTOR = 0.5`);
si la validación falla se re-emite como error de escenario con línea. `STAIRS.csv` es layer
**opcional**: escenarios de una planta no la tienen.

```java
// src/main/java/ar/edu/itba/simped/input/csv/StairsCsvReader.java:25-55
    protected Stairs parseRow(List<String> tokens, int lineNumber, Path path) {
        if (tokens.size() != 8 && tokens.size() != 9) {
            throw unsupportedEntityException(path, lineNumber,
                    "expected 8 or 9 columns (block_name, x1, y1, z1, x2, y2, z2, width[, speed_factor]), got "
                            + tokens.size());
        }
        String blockName = tokens.get(0);
        if (blockName.isBlank()) {
            throw unsupportedEntityException(path, lineNumber, "blank block_name");
        }
        double x1 = CsvParse.parseDouble(tokens.get(1), path, lineNumber, layerName());
        double y1 = CsvParse.parseDouble(tokens.get(2), path, lineNumber, layerName());
        double z1 = CsvParse.parseDouble(tokens.get(3), path, lineNumber, layerName());
        double x2 = CsvParse.parseDouble(tokens.get(4), path, lineNumber, layerName());
        double y2 = CsvParse.parseDouble(tokens.get(5), path, lineNumber, layerName());
        double z2 = CsvParse.parseDouble(tokens.get(6), path, lineNumber, layerName());
        double width = CsvParse.parseDouble(tokens.get(7), path, lineNumber, layerName());
        double speedFactor = tokens.size() == 9
                ? CsvParse.parseDouble(tokens.get(8), path, lineNumber, layerName())
                : Stairs.DEFAULT_SPEED_FACTOR;

        try {
            return new Stairs(blockName,
                    new Vec3(x1, y1, z1),
                    new Vec3(x2, y2, z2),
                    width,
                    speedFactor);
        } catch (IllegalArgumentException e) {
            throw unsupportedEntityException(path, lineNumber, e.getMessage());
        }
    }
```

El tipo expone los métodos geométricos que consume el motor: `axisXy` (eje planar, grafo/CIM),
`pointAt` (punto 3D con `z` interpolada) y `containsXy` — "¿este punto planar cae en la huella
del tramo?" (proyección sobre el eje en `[0,1]` **y** distancia perpendicular `≤ width/2`), que
usan el CIM (clasificación por planta) y el CPM (confinamiento).

```java
// src/main/java/ar/edu/itba/simped/core/Stairs.java:99-110
    public boolean containsXy(double px, double py) {
        double ax = foot.x(), ay = foot.y();
        double dx = top.x() - ax, dy = top.y() - ay;
        double len2 = dx * dx + dy * dy;
        if (len2 == 0.0) {
            return Math.hypot(px - ax, py - ay) <= width / 2.0;
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / len2;
        if (t < 0.0 || t > 1.0) return false;
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy) <= width / 2.0;
    }
```

**Por qué archivo nuevo y no atributo en `WALLS`** (descartado): un rectángulo con `z_from`/`z_to`
es ambiguo sobre qué borde es el pie; dos landings son redundantes con el eje. El eje pie→tope es
la representación mínima e inequívoca. Ver [D4](../../.claude/DECISIONES.md) y el switchback en
[`02-fisica-cpm.md`](02-fisica-cpm.md).

---

## 5. Output con columna `z` (D10)

El formato gana una columna `z` **inmediatamente después de `y`**: `tout; x; y; z; vx; vy;
state; id`. Agrupa la posición 3D `(x,y,z)` y deja la velocidad planar junta; `id` último para
trazar trayectorias. Separador `; `, `Locale.US`, sin header.

```java
// src/main/java/ar/edu/itba/simped/simulation/OutputSinkImpl.java:59-67
    private static String formatRow(double tout, AgentState agent) {
        // Formato D10: tout; x; y; z; vx; vy; state; id. La z agrupada con x,y
        // (posición 3D); id al final para trazar trayectorias por agente.
        return String.format(Locale.US,
                "%.4f" + SEPARATOR + "%.6f" + SEPARATOR + "%.6f" + SEPARATOR + "%.6f" + SEPARATOR
                        + "%.6f" + SEPARATOR + "%.6f" + SEPARATOR + "%s" + SEPARATOR + "%d",
                tout, agent.x(), agent.y(), agent.z(), agent.vx(), agent.vy(),
                agent.state().name(), agent.id());
    }
```

**Por qué `z` tras `y` y no al final** (descartado en D10): ponerla tras `id` partiría la
posición 3D y rompería igual el invariante "id al final"; como controlamos todos los consumidores
se prefirió el orden canónico `(x,y,z)`. No se emite `vz` (no se modela, D2). Ver
[D10](../../.claude/DECISIONES.md).

---

## 6. Reproducibilidad opt-in con `Seeds` (D23)

La semilla global (`-Dsimped.seed`) es **opt-in**: sin configuración el comportamiento es el
histórico (preserva tests y corridas sueltas); con semilla, todos los streams quedan
determinísticos. El problema que resolvió D23: `rng` sólo alimentaba spawn y ruteo; el resto
(tiempos de servicio, selección de salida/aula/dwell) usaba constantes `new Random(0)`, así que
entre réplicas cada agente repetía elecciones — las "5 realizaciones" eran menos independientes.
`mixOr` cierra el hueco: con semilla devuelve `seed ^ salt.hashCode()`; **sin** semilla, el
`fallback` histórico.

```java
// src/main/java/ar/edu/itba/simped/core/Seeds.java:62-72
    public static long mixOr(long fallback, String salt) {
        String prop = System.getProperty("simped.seed");
        if (prop != null && !prop.isBlank()) {
            try {
                return Long.parseLong(prop.trim()) ^ (long) salt.hashCode();
            } catch (NumberFormatException ignored) {
                // No parseable: cae al fallback determinista.
            }
        }
        return fallback;
    }
```

El `^ salt.hashCode()` garantiza independencia: streams con salts distintos no comparten
secuencia aunque compartan semilla base. **Suite: 143 tests, 0 fallos, 0 omitidos** sin tocar
tests (el fallback preserva las secuencias históricas). Ver [D23](../../.claude/DECISIONES.md) e
[informe §Implementación, "Reproducibilidad"](../../informe/informe.tex).

---

## 7. La lección: los defaults `z=0` silenciosos (D11/D16/D18)

Cada módulo quedó verde en aislamiento, pero al correr el **primer** escenario real de dos
plantas **ningún agente subía** — sin ningún error en runtime. La causa: caminos de datos donde
la `z` se perdía en silencio con un default `z=0`, produciendo una simulación plausible pero
errónea. En IO, dos casos eran los constructores de conveniencia de servers y generadores. El fix
propaga `serverRow.z()` (que el reader **sí** parseaba) al `ServerZone`; el gemelo (D18) arma el
`GeneratorZone` con `row.z()`.

```java
// src/main/java/ar/edu/itba/simped/input/GeometryAssembler.java:153-158
            try {
                // Propagar la planta del server (serverRow.z()): sin esto se usaba
                // el overload que fija z=0 y los servers de plantas altas (aulas de
                // P1) quedaban en la planta baja (bug de migración 3D).
                out.add(new ServerZone(serverRow.base(), serverRow.id(),
                        serverRow.area(), serverRow.z(), queues, type, sParams));
```

Mismo patrón de gap: **constructor de conveniencia que defaultea `z=0` + wiring 2D**, no cubierto
porque no había servers ni generadores en plantas altas hasta el escenario multiplanta. Lección:
al agregar una coordenada hay que auditar **todos** los caminos que copian estado; los defaults
silenciosos corren y "se ven bien" pero son erróneos, y los tests por módulo no reemplazan un
escenario de integración real. Ver [informe §Implementación, "La integración multiplanta"](../../informe/informe.tex),
[D11](../../.claude/DECISIONES.md), [D16](../../.claude/DECISIONES.md) y
[D18](../../.claude/DECISIONES.md).

> Los otros dos fixes de D11 (Task location-group multiplanta, selección por índice) y el del
> ruteo (cruce de la arista de escalera) viven en el dominio de ruteo/agente, no de IO.

---

## Navegación

- Q&A de defensa de este tema: [`../blindaje/q-tipos-io.md`](../blindaje/q-tipos-io.md)
- Física del CPM y uso de `Stairs`: [`02-fisica-cpm.md`](02-fisica-cpm.md)
- Resumen de la implementación: [`../resumenes/02-implementacion.md`](../resumenes/02-implementacion.md)
- Decisiones de arquitectura: [`../../.claude/DECISIONES.md`](../../.claude/DECISIONES.md) (D1, D2, D3, D4, D10, D11, D16, D18, D23)
- Informe: [`../../informe/informe.tex`](../../informe/informe.tex)
