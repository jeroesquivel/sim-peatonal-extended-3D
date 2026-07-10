# Resumen §Introducción — objetivo y observables

## El problema

Simulador de partida: **2D**, con `Vec2` `(x,y)` en cascada por todos los módulos. El TP lo amplía a **3D** vía la coordenada `z` = planta del agente (y su altura sobre una escalera). La escalera une plantas.

## El modelo físico: CPM / A-CPM

**CPM** (Baglietto & Parisi, 2011), variante **anisotrópica con evitación (A-CPM)** (Martin & Parisi, 2024). Radio variable: contrae ante contacto, expande al avanzar; evitación angular. Planar por planta; la `z` no es grado de libertad, se interpola sobre la escalera. → [física en detalle](../codigo/02-fisica-cpm.md)

## El escenario: Escuela

**Escuela**: dos plantas, aulas, pasillos, escaleras, recreo con kiosco. Sub-escenarios: **Evacuación** (varía capacidad total) e **Ingreso** (varía caudal). → [escenario en detalle](03-simulaciones.md)

## Los observables (definidos por ecuación)

Se varía un input y se mide un observable, definido *antes* de los resultados.

**1. Tiempo de evacuación** (agente *i*):

```text
t_evac,i = t_salida,i − t_inicio,i          (informe, ec. tevac)
```

`t_inicio,i`: primer instante en la salida; `t_salida,i`: cuando la abandona. *Evacuado* (criterio operativo): deja de figurar en el output antes del último cuadro. Presente en el último cuadro = no evacuado.

**2. Población en una zona** (`R ⊂ ℝ²` en planta baja, `z≈0`):

```text
n_zona(t) = |{ i : (x_i(t), y_i(t)) ∈ R,  z_i(t) ≈ 0 }|          (informe, ec. nzona)
```

Agentes cuya proyección `(x,y)` cae en `R` en el instante `t`.

**3. Caudal de ingreso** (input del Ingreso: ventana `Ta`):

```text
Q = N_max / Ta          (informe, ec. caudal)
```

Los `N_max` alumnos se reparten uniformemente en `Ta ∈ {1,5,10}` min. Ventana corta = caudal alto.

## Qué se mide y qué escalar sale de cada estudio

| Estudio | Input | Observable | Escalar |
|---|---|---|---|
| **Evacuación** | Capacidad total `N` | Distribución de `t_evac` (ec. tevac) | Tiempo promedio y máximo |
| **Ingreso** | Ventana `Ta` (⇔ caudal `Q`) | Población `n_zona(t)` en el kiosco (ec. nzona) | Ocupación máxima y promedio |
| **Complementario** | Cantidad `N_max` (a `Ta=5` min) | Población `n_zona(t)` en el kiosco | Ocupación máxima y promedio |

→ [resultados](04-resultados.md) · [blindaje de resultados](../blindaje/q-resultados-estadistica.md)

## Referencias

- **Baglietto & Parisi (2011)** — *Continuous-space automaton model for pedestrian dynamics*, Phys. Rev. E **83**, 056117. (CPM canónico; citado en `CpmParameters.java`.)
- **Martin & Parisi (2024)** — *Anisotropic Contractile Particle Model with Avoidance (A-CPM)*. (Variante usada en `CpmOperationalModel.java`.)
- **Helbing, Farkas & Vicsek (2000)** — *Simulating dynamical features of escape panic*, Nature **407**, 487–490. (Velocidades deseadas altas en crisis; justifica el modo crisis.)

---

### Navegación

- ➡ [Resumen §Implementación](02-implementacion.md) · [§Simulaciones](03-simulaciones.md) · [§Resultados](04-resultados.md) · [Conclusiones](05-conclusiones.md)
- [Resumen ejecutivo (pitch)](../00-resumen-ejecutivo.md) · [Índice](../README.md)
- [informe](../../informe/informe.tex) · [`DECISIONES.md`](../../.claude/DECISIONES.md) · [enunciado](../../.claude/ENUNCIADO.md)
