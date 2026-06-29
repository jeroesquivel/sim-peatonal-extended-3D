package ar.edu.itba.simped.agent.om;

import ar.edu.itba.simped.core.AgentProfile;

/**
 * Presets de parámetros CPM de Baglietto & Parisi.
 * Implementación original del Grupo 7.
 */
public final class CpmParameters {

    private CpmParameters() {}

    public static AgentProfile baglietoParisiSet1() {
        return new AgentProfile(1.55, 0.5, 0.15, 0.32, 0.9, 1.55);
    }

    public static AgentProfile baglietoParisiSet2() {
        return new AgentProfile(0.95, 0.5, 0.10, 0.37, 0.9, 0.95);
    }

    /**
     * Experimento Grupo 7 — "espacio personal amplio": Set1 con {@code rmin}
     * elevado hacia {@code rmax} (0.15 → 0.28), dejando {@code rmax}, {@code vd}
     * y {@code ve} iguales. Hipótesis: con rmin chico los centros se acercan
     * hasta {@code 2*rmin} antes de detectar contacto, lo que permite clusters
     * muy densos que se traban (gridlock) en cuellos de botella; al acercar rmin
     * a rmax el empaquetamiento es más ralo y la velocidad de escape actúa antes.
     */
    public static AgentProfile rminCercaRmax() {
        return new AgentProfile(1.55, 0.5, 0.28, 0.32, 0.9, 1.55);
    }

    /** Punto intermedio para barrer el efecto de rmin (rmin = 0.22). */
    public static AgentProfile rminMedio() {
        return new AgentProfile(1.55, 0.5, 0.22, 0.32, 0.9, 1.55);
    }

    /**
     * Selecciona un preset por nombre (usado por {@code App} vía la env var
     * {@code SIMPED_CPM_SET}). Desconocido/nulo → {@link #baglietoParisiSet1()}.
     */
    public static AgentProfile bySet(String name) {
        if (name == null) {
            return baglietoParisiSet1();
        }
        return switch (name.trim().toLowerCase()) {
            case "set2" -> baglietoParisiSet2();
            case "tight", "rminmax", "rmincercarmax" -> rminCercaRmax();
            case "mid", "rminmedio" -> rminMedio();
            default -> baglietoParisiSet1();
        };
    }

    public static double recommendedDt(AgentProfile profile) {
        return profile.rmin() / (2.0 * Math.max(profile.vd(), profile.ve()));
    }
}
