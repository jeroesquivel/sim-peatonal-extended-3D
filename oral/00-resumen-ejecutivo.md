# Resumen ejecutivo — el TP en 2 minutos

> **Título:** Ampliación a 3D de un simulador peatonal: multiplanta y escaleras.
> **Grupo 5:** Sebastián Caules · Andrés Cortese · Tomás J. Esquivel — Simulación de Sistemas, ITBA.

*El pitch narrativo. Para números, resultados y Q&A en formato tabla → [Resumen breve](RESUMEN-BREVE.md).*

## El pitch (leélo de corrido)

Partimos de un simulador peatonal **2D** (posición `Vec2 (x,y)` en cascada por todos los módulos). Lo
**ampliamos a 3D** para soportar **plantas unidas por escaleras**, con enfoque **híbrido**: `Vec3` para
la posición, pero `Vec2` para la dinámica planar. La física (CPM) es planar por planta; la `z` no es
grado de libertad, se **interpola** sobre la escalera. El núcleo del CPM quedó intacto.

Tocó todos los módulos: **grafo** (malla por planta unida por escaleras, A* euclídeo 3D, visibilidad
por planta), **vecinos** (grilla CIM por planta + puente por escalera), **física** (velocidad reducida
+ `z` interpolada, anti-tunneling de la planta actual) y **salida** (columna `z`).

El desafío real **no fueron los tipos sino la integración**: al correr el primer escenario de dos
plantas, *nadie subía* — la `z` se perdía en silencio (default `z=0`), diagnosticado y corregido (D11).
Igual el **salto de `z`** (artefacto de ruteo, D21) y el **livelock de jamba** (error de fuerzas, D17).

Sobre esa base, el escenario **Escuela** (2 plantas, 16 aulas, recreo con kiosco, escaleras
*switchback*): **Evacuación** (varía `N`) e **Ingreso** (varía el caudal). Dos fenómenos de manual:
las escaleras saturan (dos regímenes, distribución bimodal PB/P1) y el kiosco satura. Todo con
**5 realizaciones** por punto.

→ Números canónicos, resultados y Q&A: **[Resumen breve](RESUMEN-BREVE.md)**.

---

### Navegación

- ➡ [Índice completo](README.md) · [Resumen breve](RESUMEN-BREVE.md)
- Resúmenes: [Intro](resumenes/01-introduccion-observables.md) · [Implementación](resumenes/02-implementacion.md) · [Simulaciones](resumenes/03-simulaciones.md) · [Resultados](resumenes/04-resultados.md) · [Conclusiones](resumenes/05-conclusiones.md)
- [informe](../informe/informe.tex) · [`DECISIONES.md`](../.claude/DECISIONES.md)
