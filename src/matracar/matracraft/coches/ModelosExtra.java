package matracraft.coches;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometría de los coches: lista de piezas de cada modelo.
 *
 * Piezas: (lateral, frente, alto, ancho, largo, alto_caja, material) con giro opcional sobre su centro
 * (rx inclina el frente arriba/abajo, ry gira, rz alabea). Lateral positivo = izquierda. Alto 0 = suelo.
 * Desde la 2.3.0 los coches llevan esquinas achaflanadas, parabrisas inclinados y ruedas redondeadas.
 * Estilo (skin) en los bits 16-18 de Aspecto.extras; los bits 0-1 son los extras de siempre.
 */
final class ModelosExtra {

    private ModelosExtra() {}

    static final String[][] ESTILOS = {
            {"tartana", "Lisa", "Bicolor", "Rally", "Retro", "Safari"},
            {"turismo", "Lisa", "Franja", "Techo negro", "Rally", "Tuning"},
            {"deportivo", "Lisa", "Franjas de carreras", "Bitono", "Carbono", "Le Mans"},
            {"furgoneta", "Lisa", "Reparto", "Camper", "Food truck", "Mudanzas"},
            {"motocarro", "Lisa", "Toldo", "Bitono", "Frutero", "Butano"},
            {"quad", "Lisa", "Carreras", "Campo", "Desierto", "Cross"},
            {"taxi", "Franja", "Anuncio", "Doble franja", "Franja larga", "Clásico"},
    };

    static int estilo(Modelos.Aspecto a) {
        return (a.extras() >> 16) & 7;
    }

    static String nombreEstilo(String modelo, Modelos.Aspecto a) {
        for (String[] e : ESTILOS)
            if (e[0].equals(modelo)) return e[Math.min(estilo(a), e.length - 2) + 1];
        return "";
    }

    private static void add(List<Modelos.Pieza> lista, double x, double y, double z, double w, double l, double h, String c) {
        lista.add(new Modelos.Pieza(x, y, z, w, l, h, c));
    }

    /** Pieza girada sobre su centro (grados). */
    private static void g(List<Modelos.Pieza> lista, double x, double y, double z, double w, double l, double h, String c,
                          double rx, double ry, double rz) {
        lista.add(new Modelos.Pieza(x, y, z, w, l, h, c, rx, ry, rz));
    }

    private static void par(List<Modelos.Pieza> lista, double x, double y, double z, double w, double l, double h, String c) {
        add(lista, -x, y, z, w, l, h, c);
        add(lista, x, y, z, w, l, h, c);
    }

    /** Par simétrico girado: el giro (ry, rz) se refleja en el lado derecho. */
    private static void parG(List<Modelos.Pieza> lista, double x, double y, double z, double w, double l, double h, String c,
                             double rx, double ry, double rz) {
        g(lista, x, y, z, w, l, h, c, rx, ry, rz);
        g(lista, -x, y, z, w, l, h, c, rx, -ry, -rz);
    }

    /**
     * Rueda redondeada: cubierta en cruz + rombo a 45° (octógono aproximado) y llanta en rombo.
     * x, y = centro; d = diámetro; t = ancho de la cubierta.
     */
    private static void rueda(List<Modelos.Pieza> lista, double x, double y, double d, double t, String llanta) {
        add(lista, x, y, d * 0.2, t, d, d * 0.6, "goma");
        add(lista, x, y, 0, t, d * 0.6, d, "goma");
        double s = 0.72 * d;
        g(lista, x, y, d / 2 - s / 2, t, s, s, "goma", 45, 0, 0);
        double r = 0.4 * d;
        double fuera = Math.signum(x) * (t / 2 + 0.005);
        g(lista, x + fuera, y, d / 2 - r / 2, 0.02, r, r, llanta, 45, 0, 0);
    }

    private static void ruedas(List<Modelos.Pieza> lista, double x, double delante, double detras, double d, double t, String llanta) {
        for (int i = -1; i <= 1; i += 2) {
            rueda(lista, i * x, delante, d, t, llanta);
            rueda(lista, i * x, detras, d, t, llanta);
        }
    }

    /** Pieza de color C1 recoloreada si cumple la condición. */
    private static void recolorar(List<Modelos.Pieza> lista, java.util.function.Predicate<Modelos.Pieza> si, String color) {
        lista.replaceAll(q -> si.test(q) ? q.color(color) : q);
    }

    /** Piezas del modelo; rellena placa con la posición de las matrículas (delantera, trasera, altura; NaN = no hay). */
    static List<Modelos.Pieza> piezas(String modelo, Modelos.Aspecto aspecto, double[] placa) {
        List<Modelos.Pieza> l = new ArrayList<>();
        int estilo = estilo(aspecto);
        switch (modelo) {
            case "tartana" -> {
                // utilitario de toda la vida: alto, cuadradote pero con las esquinas comidas
                add(l, 0, 0, 0.22, 1.3, 2.0, 0.4, "C1");
                add(l, 0, 1.08, 0.22, 1.18, 0.16, 0.36, "C1");
                parG(l, 0.55, 1.0, 0.22, 0.22, 0.22, 0.36, "C1", 0, 45, 0);
                add(l, 0, -1.06, 0.22, 1.24, 0.12, 0.4, "C1");
                g(l, 0, 0.75, 0.6, 1.24, 0.55, 0.06, "C1", -5, 0, 0);
                add(l, 0, 1.165, 0.28, 0.8, 0.02, 0.2, "gris");
                for (int k = 0; k < 3; k++) add(l, 0, 1.17, 0.31 + k * 0.06, 0.76, 0.01, 0.02, "negro");
                par(l, 0.45, 1.165, 0.33, 0.15, 0.03, 0.15, "faro");
                parG(l, 0.45, 1.165, 0.335, 0.13, 0.03, 0.13, "faro", 0, 0, 45);
                // cabina alta con parabrisas casi vertical
                add(l, 0, -0.35, 0.62, 1.22, 1.25, 0.52, "cristal");
                g(l, 0, 0.33, 0.6, 1.2, 0.05, 0.56, "cristal", 18, 0, 0);
                parG(l, 0.61, 0.33, 0.6, 0.05, 0.05, 0.56, "C1", 18, 0, 0);
                par(l, 0.625, -0.2, 0.62, 0.04, 0.08, 0.52, "C1");
                par(l, 0.615, -0.95, 0.62, 0.06, 0.14, 0.52, "C1");
                add(l, 0, -0.4, 1.14, 1.26, 1.35, 0.07, "C1");
                add(l, 0, -1.0, 0.62, 1.24, 0.06, 0.52, "C1");
                add(l, 0, -1.035, 0.72, 0.96, 0.02, 0.34, "cristal");
                // paragolpes, pilotos, moldura y bajos
                add(l, 0, 1.19, 0.15, 1.22, 0.08, 0.13, "gris");
                add(l, 0, -1.15, 0.15, 1.26, 0.08, 0.13, "gris");
                par(l, 0.5, -1.125, 0.46, 0.14, 0.03, 0.2, "piloto");
                par(l, 0.652, 0, 0.36, 0.02, 1.9, 0.04, "gris");
                par(l, 0.69, 0.3, 0.76, 0.1, 0.05, 0.07, "negro");
                add(l, 0, 0, 0.1, 1.06, 2.0, 0.12, "negro");
                ruedas(l, 0.57, 0.72, -0.72, 0.52, 0.24, "llanta");
                if (aspecto.extra(0)) {
                    for (int k = -1; k <= 1; k += 2) add(l, k * 0.45, -0.4, 1.21, 0.05, 1.1, 0.05, "hierro");
                    add(l, 0, -0.4, 1.26, 0.95, 0.05, 0.04, "hierro");
                    add(l, 0, -0.4, 1.27, 0.5, 0.5, 0.4, "barril");
                }
                if (aspecto.extra(1)) {
                    add(l, 0, -1.2, 0.49, 0.46, 0.12, 0.28, "goma");
                    add(l, 0, -1.2, 0.4, 0.28, 0.12, 0.46, "goma");
                    g(l, 0, -1.2, 0.465, 0.33, 0.12, 0.33, "goma", 0, 0, 45);
                    add(l, 0, -1.27, 0.53, 0.2, 0.02, 0.2, "llanta");
                }
                if (estilo == 1) {
                    recolorar(l, q -> q.c().equals("C1") && q.z() >= 0.59, "C2");
                }
                if (estilo == 2) {
                    par(l, 0.655, 0.1, 0.42, 0.012, 1.7, 0.1, "C2");
                    add(l, 0, -0.1, 1.21, 0.9, 0.12, 0.08, "negro");
                    for (int k = -1; k <= 1; k++) add(l, k * 0.3, 0.04, 1.22, 0.16, 0.03, 0.06, "faro");
                    add(l, 0, 1.25, 0.18, 0.9, 0.04, 0.06, "negro");
                    par(l, 0.3, 1.25, 0.18, 0.05, 0.04, 0.34, "negro");
                    add(l, 0, 1.25, 0.47, 0.65, 0.04, 0.05, "negro");
                }
                if (estilo == 3) {
                    // Retro: paragolpes y molduras cromados, techo blanco
                    recolorar(l, q -> q.c().equals("gris") && (Math.abs(q.y()) > 1.1 || q.w() < 0.05), "hierro");
                    recolorar(l, q -> q.c().equals("C1") && q.z() >= 1.1, "blanco");
                }
                if (estilo == 4) {
                    // Safari: defensa delantera, snorkel, focos en el techo y bidones atrás
                    par(l, 0.4, 1.28, 0.2, 0.05, 0.05, 0.5, "negro");
                    add(l, 0, 1.28, 0.66, 0.86, 0.05, 0.05, "negro");
                    add(l, 0, 1.28, 0.42, 0.86, 0.05, 0.05, "negro");
                    add(l, 0.69, 0.75, 0.62, 0.08, 0.08, 0.68, "negro");
                    add(l, 0.69, 0.79, 1.26, 0.08, 0.16, 0.06, "negro");
                    add(l, 0, 0.45, 1.21, 1.0, 0.08, 0.06, "negro");
                    for (int k = -1; k <= 1; k++) add(l, k * 0.3, 0.495, 1.215, 0.16, 0.02, 0.06, "faro");
                    par(l, 0.33, -1.19, 0.3, 0.2, 0.1, 0.28, "verde");
                    par(l, 0.33, -1.19, 0.58, 0.06, 0.06, 0.04, "negro");
                }
                placa[0] = 1.235;
                placa[1] = -1.195;
                placa[2] = 0.17;
            }
            case "deportivo" -> {
                // cuña baja: morro afilado, cabina adelantada y motor detrás
                add(l, 0, 0, 0.14, 1.5, 2.4, 0.3, "C1");
                add(l, 0, 1.3, 0.14, 1.36, 0.22, 0.2, "C1");
                parG(l, 0.62, 1.2, 0.14, 0.3, 0.3, 0.22, "C1", 0, 45, 0);
                g(l, 0, 0.98, 0.33, 1.42, 0.95, 0.07, "C1", -9, 0, 0);
                add(l, 0, -1.29, 0.14, 1.44, 0.2, 0.32, "C1");
                parG(l, 0.64, -1.2, 0.14, 0.28, 0.28, 0.32, "C1", 0, 45, 0);
                par(l, 0.755, -0.45, 0.24, 0.02, 0.45, 0.12, "negro");
                par(l, 0.756, 0.05, 0.37, 0.01, 2.2, 0.03, "C3");
                add(l, 0, -0.28, 0.44, 1.2, 0.86, 0.26, "cristal");
                g(l, 0, 0.36, 0.42, 1.2, 0.05, 0.44, "cristal", 55, 0, 0);
                g(l, 0, -0.78, 0.42, 1.16, 0.05, 0.4, "cristal", -50, 0, 0);
                add(l, 0, -0.3, 0.7, 1.18, 0.7, 0.06, "C2");
                // aletas sobre las ruedas
                par(l, 0.62, 0.92, 0.4, 0.3, 0.62, 0.1, "C1");
                par(l, 0.62, -0.92, 0.4, 0.3, 0.62, 0.12, "C1");
                g(l, 0, -0.98, 0.44, 1.24, 0.62, 0.06, "C1", -14, 0, 0);
                for (int k = 0; k < 3; k++) g(l, 0, -0.85 - k * 0.14, 0.47 - k * 0.03, 0.8, 0.04, 0.03, "negro", -14, 0, 0);
                add(l, 0, 1.43, 0.11, 1.2, 0.1, 0.04, "negro");
                add(l, 0, 1.415, 0.18, 0.6, 0.02, 0.1, "negro");
                parG(l, 0.5, 1.4, 0.31, 0.32, 0.04, 0.05, "faro", 0, -8, 0);
                add(l, 0, -1.395, 0.36, 1.1, 0.02, 0.05, "piloto");
                add(l, 0, -1.4, 0.14, 1.0, 0.04, 0.1, "negro");
                par(l, 0.25, -1.41, 0.17, 0.1, 0.05, 0.08, "grisc");
                par(l, 0.7, 0.22, 0.5, 0.12, 0.06, 0.06, "C1");
                ruedas(l, 0.63, 0.92, -0.92, 0.48, 0.3, "gris");
                if (aspecto.extra(0)) {
                    add(l, 0, -1.32, 0.74, 1.45, 0.22, 0.05, "C2");
                    par(l, 0.5, -1.32, 0.44, 0.05, 0.1, 0.3, "negro");
                }
                if (estilo == 1) {
                    for (int k = -1; k <= 1; k += 2) {
                        g(l, k * 0.15, 0.98, 0.335, 0.14, 0.95, 0.07, "C3", -9, 0, 0);
                        add(l, k * 0.15, -0.32, 0.705, 0.14, 0.72, 0.06, "C3");
                        g(l, k * 0.15, -0.98, 0.445, 0.14, 0.62, 0.06, "C3", -14, 0, 0);
                        add(l, k * 0.15, 1.3, 0.145, 0.14, 0.22, 0.2, "C3");
                    }
                }
                if (estilo == 2) {
                    par(l, 0.756, 0, 0.15, 0.012, 2.4, 0.15, "C2");
                    add(l, 0, 1.415, 0.15, 1.2, 0.012, 0.03, "C2");
                    recolorar(l, q -> q.c().equals("C1") && q.y() > 0.8 && q.z() > 0.35 && q.w() < 0.35, "C2");
                }
                if (estilo == 3) {
                    // Carbono: capós, techo, retrovisores y faldones en negro
                    recolorar(l, q -> q.c().equals("C1") && (q.girada() && q.w() > 1 || q.w() == 0.12), "negro");
                    recolorar(l, q -> q.c().equals("C2") && q.z() > 0.65, "negro");
                    par(l, 0.757, 0, 0.12, 0.02, 2.0, 0.06, "negro");
                }
                if (estilo == 4) {
                    // Le Mans: dorsales blancos con un 7, alerón grande y labio delantero
                    par(l, 0.758, -0.1, 0.17, 0.012, 0.42, 0.3, "blanco");
                    par(l, 0.765, -0.06, 0.2, 0.01, 0.05, 0.24, "negro");
                    par(l, 0.765, -0.14, 0.4, 0.01, 0.16, 0.04, "negro");
                    if (!aspecto.extra(0)) {
                        add(l, 0, -1.32, 0.74, 1.45, 0.22, 0.05, "C3");
                        par(l, 0.5, -1.32, 0.44, 0.05, 0.1, 0.3, "negro");
                    }
                    add(l, 0, 1.47, 0.08, 1.3, 0.08, 0.04, "C3");
                }
                placa[0] = 1.555;
                placa[1] = -1.425;
                placa[2] = 0.2;
            }
            case "furgoneta" -> {
                // furgoneta de carga: caja alta, morro corto y parabrisas tumbado
                add(l, 0, -0.35, 0.22, 1.34, 1.8, 1.3, "C1");
                add(l, 0, -0.35, 1.52, 1.24, 1.7, 0.05, "C1");
                add(l, 0, 0.8, 0.22, 1.3, 0.5, 0.42, "C1");
                add(l, 0, 1.1, 0.22, 1.18, 0.12, 0.36, "C1");
                parG(l, 0.55, 1.03, 0.22, 0.24, 0.24, 0.38, "C1", 0, 45, 0);
                g(l, 0, 0.82, 0.62, 1.26, 0.52, 0.06, "C1", -10, 0, 0);
                g(l, 0, 0.62, 0.64, 1.22, 0.05, 0.72, "cristal", 30, 0, 0);
                parG(l, 0.625, 0.62, 0.64, 0.06, 0.06, 0.72, "C1", 30, 0, 0);
                par(l, 0.675, 0.28, 0.82, 0.02, 0.42, 0.42, "cristal");
                add(l, 0, -1.255, 0.95, 1.1, 0.02, 0.42, "cristal");
                add(l, 0, -1.255, 0.25, 0.02, 0.02, 1.2, "negro");
                par(l, 0.672, -0.1, 0.28, 0.01, 0.03, 1.05, "negro");
                par(l, 0.675, -0.3, 0.5, 0.02, 1.9, 0.1, "C2");
                add(l, 0, 1.18, 0.15, 1.22, 0.1, 0.16, "gris");
                add(l, 0, -1.29, 0.15, 1.3, 0.08, 0.16, "gris");
                add(l, 0, 1.165, 0.4, 0.6, 0.02, 0.12, "negro");
                par(l, 0.45, 1.165, 0.42, 0.24, 0.03, 0.12, "faro");
                par(l, 0.6, -1.265, 0.5, 0.1, 0.03, 0.3, "piloto");
                par(l, 0.72, 0.5, 0.82, 0.07, 0.06, 0.18, "negro");
                add(l, 0, -0.1, 0.1, 1.16, 2.2, 0.12, "negro");
                ruedas(l, 0.6, 0.78, -0.82, 0.5, 0.26, "llanta");
                if (aspecto.extra(0)) {
                    par(l, 0.45, -0.4, 1.58, 0.05, 1.5, 0.05, "hierro");
                    for (double v = -1.0; v < 0.3; v += 0.32) add(l, 0, v, 1.58, 0.95, 0.04, 0.04, "hierro");
                }
                if (estilo == 1) {
                    par(l, 0.677, -0.4, 0.75, 0.012, 1.5, 0.55, "C2");
                    par(l, 0.68, -0.4, 0.88, 0.01, 1.2, 0.08, "blanco");
                    add(l, 0, -0.35, 1.53, 1.24, 1.7, 0.05, "C2");
                }
                if (estilo == 2) {
                    recolorar(l, q -> q.c().equals("C1") && q.z() >= 1.4, "C2");
                    par(l, 0.676, -0.35, 0.9, 0.012, 1.82, 0.62, "C2");
                    add(l, 0, -0.45, 1.57, 1.0, 1.1, 0.12, "C2");
                    par(l, 0.676, -0.55, 0.95, 0.014, 0.6, 0.3, "cristal");
                }
                if (estilo == 3) {
                    // Food truck: ventanilla abierta con toldo, mostrador y cartel en el techo
                    add(l, 0.677, -0.4, 0.8, 0.012, 1.0, 0.45, "negro");
                    add(l, 0.75, -0.4, 0.78, 0.15, 1.0, 0.04, "hierro");
                    g(l, 0.83, -0.4, 1.28, 0.34, 1.1, 0.03, "C2", 0, 0, -20);
                    add(l, 0, -0.35, 1.57, 0.06, 1.3, 0.32, "C2");
                    add(l, 0, -0.35, 1.62, 0.07, 1.1, 0.18, "blanco");
                }
                if (estilo == 4) {
                    // Mudanzas: faldón de color, baca larga y escalera atrás
                    par(l, 0.677, -0.35, 0.24, 0.012, 1.82, 0.32, "C2");
                    par(l, 0.45, -0.35, 1.58, 0.05, 1.7, 0.05, "hierro");
                    for (double v = -1.15; v < 0.5; v += 0.4) add(l, 0, v, 1.58, 0.95, 0.04, 0.04, "hierro");
                    par(l, 0.4, -1.28, 0.3, 0.04, 0.03, 1.3, "hierro");
                    for (double h = 0.45; h < 1.6; h += 0.25) add(l, 0, -1.28, h, 0.8, 0.03, 0.03, "hierro");
                }
                placa[0] = 1.235;
                placa[1] = -1.335;
                placa[2] = 0.18;
            }
            case "motocarro" -> {
                // motocarro de tres ruedas: cabina redondeada delante y caja detrás
                add(l, 0, 0.27, 0.3, 1.0, 0.66, 0.36, "C1");
                add(l, 0, 0.05, 0.66, 1.0, 0.22, 0.54, "C1");
                add(l, 0, 0.66, 0.3, 0.78, 0.16, 0.36, "C1");
                parG(l, 0.38, 0.58, 0.3, 0.24, 0.24, 0.36, "C1", 0, 45, 0);
                g(l, 0, 0.64, 0.66, 0.86, 0.05, 0.5, "cristal", 12, 0, 0);
                parG(l, 0.45, 0.64, 0.66, 0.06, 0.06, 0.5, "C1", 12, 0, 0);
                add(l, 0, 0.3, 1.2, 1.04, 0.84, 0.06, "C1");
                g(l, 0, 0.72, 1.16, 0.8, 0.12, 0.06, "C1", -20, 0, 0);
                par(l, 0.48, 0.38, 0.7, 0.02, 0.42, 0.4, "cristal");
                par(l, 0.48, 0.38, 1.1, 0.04, 0.42, 0.1, "C1");
                par(l, 0.505, -0.05, 0.3, 0.02, 0.08, 0.9, "C1");
                add(l, 0, 0.745, 0.52, 0.18, 0.03, 0.12, "faro");
                par(l, 0.32, 0.745, 0.42, 0.06, 0.03, 0.05, "naranja");
                g(l, 0, 0.82, 0.32, 0.3, 0.4, 0.08, "C1", -12, 0, 0);
                add(l, 0, 0.4, 0.75, 0.6, 0.05, 0.05, "negro");
                add(l, 0, -0.12, 0.3, 0.62, 0.08, 0.75, "negro");
                add(l, 0, 0.05, 0.3, 0.6, 0.32, 0.2, "negro");
                add(l, 0, -0.55, 0.22, 0.9, 1.6, 0.1, "negro");
                add(l, 0, -0.95, 0.45, 1.3, 1.1, 0.06, "C1");
                par(l, 0.62, -0.95, 0.51, 0.06, 1.1, 0.3, "C1");
                add(l, 0, -1.48, 0.51, 1.3, 0.06, 0.3, "C1");
                add(l, 0, -0.42, 0.51, 1.3, 0.06, 0.5, "C1");
                par(l, 0.625, -0.95, 0.8, 0.07, 1.12, 0.03, "C2");
                add(l, 0, -1.485, 0.8, 1.32, 0.07, 0.03, "C2");
                par(l, 0.5, -1.515, 0.6, 0.1, 0.03, 0.1, "piloto");
                par(l, 0.6, -0.95, 0.42, 0.22, 0.6, 0.05, "negro");
                rueda(l, 0, 0.78, 0.42, 0.14, "llanta");
                rueda(l, 0.58, -0.95, 0.46, 0.2, "llanta");
                rueda(l, -0.58, -0.95, 0.46, 0.2, "llanta");
                if (aspecto.extra(0)) {
                    par(l, 0.62, -0.95, 0.98, 0.04, 1.1, 0.04, "hierro");
                    add(l, 0, -1.48, 0.98, 1.3, 0.04, 0.04, "hierro");
                    for (double v = -1.4; v < -0.5; v += 0.45) par(l, 0.62, v, 0.82, 0.04, 0.04, 0.16, "hierro");
                }
                if (estilo == 1) {
                    for (double v = -1.4; v < -0.4; v += 0.45) {
                        par(l, 0.6, v, 0.8, 0.04, 0.04, 0.45, "hierro");
                        add(l, 0, v, 1.25, 1.2, 0.04, 0.04, "hierro");
                    }
                    add(l, 0, -0.95, 1.27, 1.3, 1.12, 0.04, "lona");
                    par(l, 0.64, -0.95, 0.85, 0.02, 1.12, 0.42, "lona");
                }
                if (estilo == 2) {
                    recolorar(l, q -> q.c().equals("C1") && q.z() >= 1.1 && q.y() > 0, "C2");
                    par(l, 0.505, 0.25, 0.55, 0.012, 0.6, 0.1, "C2");
                }
                if (estilo == 3) {
                    // Frutero: cajas de madera con fruta
                    String[] fruta = {"naranja", "verde", "rojo", "amarillo"};
                    int k = 0;
                    for (double v = -1.22; v < -0.5; v += 0.5)
                        for (int lado = -1; lado <= 1; lado += 2) {
                            add(l, lado * 0.3, v, 0.51, 0.5, 0.42, 0.25, "madera");
                            add(l, lado * 0.3, v, 0.76, 0.42, 0.34, 0.05, fruta[k++ % 4]);
                        }
                }
                if (estilo == 4) {
                    // Butano: bombonas naranjas en la caja
                    for (double v = -1.25; v < -0.5; v += 0.42)
                        for (int k = -1; k <= 1; k++) {
                            add(l, k * 0.34, v, 0.51, 0.22, 0.22, 0.4, "naranja");
                            g(l, k * 0.34, v, 0.53, 0.22, 0.22, 0.36, "naranja", 0, 45, 0);
                            add(l, k * 0.34, v, 0.91, 0.09, 0.09, 0.06, "gris");
                        }
                }
                placa[0] = Double.NaN;
                placa[1] = -1.53;
                placa[2] = 0.58;
            }
            case "quad" -> {
                // quad: guardabarros curvados, depósito, sillín alto y manillar
                add(l, 0, 0, 0.24, 0.62, 1.5, 0.24, "negro");
                g(l, 0, 0.58, 0.54, 1.06, 0.55, 0.1, "C1", -12, 0, 0);
                g(l, 0, 0.92, 0.36, 0.56, 0.14, 0.26, "C1", 10, 0, 0);
                add(l, 0, 0.985, 0.4, 0.38, 0.02, 0.14, "negro");
                par(l, 0.18, 0.99, 0.5, 0.14, 0.03, 0.07, "faro");
                g(l, 0, -0.58, 0.56, 1.06, 0.5, 0.1, "C1", -6, 0, 0);
                parG(l, 0.48, 0.58, 0.36, 0.08, 0.42, 0.22, "C1", -12, 0, 0);
                parG(l, 0.48, -0.58, 0.38, 0.08, 0.4, 0.22, "C1", -6, 0, 0);
                add(l, 0, 0.18, 0.46, 0.42, 0.42, 0.24, "C1");
                g(l, 0, 0.3, 0.6, 0.36, 0.2, 0.08, "C1", -20, 0, 0);
                add(l, 0, -0.22, 0.5, 0.38, 0.72, 0.12, "C3");
                g(l, 0, -0.62, 0.55, 0.38, 0.12, 0.12, "C3", 25, 0, 0);
                par(l, 0.36, -0.02, 0.27, 0.24, 0.42, 0.03, "negro");
                g(l, 0, 0.42, 0.56, 0.07, 0.07, 0.36, "gris", 20, 0, 0);
                add(l, 0, 0.36, 0.9, 0.82, 0.06, 0.06, "negro");
                par(l, 0.38, 0.36, 0.88, 0.1, 0.08, 0.09, "grisc");
                add(l, 0.3, -0.82, 0.32, 0.08, 0.32, 0.08, "grisc");
                add(l, 0, -0.84, 0.32, 0.5, 0.03, 0.12, "piloto");
                ruedas(l, 0.5, 0.6, -0.6, 0.6, 0.34, "llanta");
                if (estilo == 1) {
                    add(l, 0, 0.995, 0.36, 0.32, 0.015, 0.22, "blanco");
                    par(l, 0.528, -0.58, 0.4, 0.015, 0.3, 0.16, "blanco");
                    recolorar(l, q -> q.c().equals("C1") && q.girada() && q.w() > 1, "C2");
                    par(l, 0.22, 0.18, 0.47, 0.02, 0.43, 0.24, "C2");
                }
                if (estilo == 2) {
                    add(l, 0, 0.62, 0.68, 0.9, 0.42, 0.03, "hierro");
                    par(l, 0.45, 0.62, 0.62, 0.03, 0.42, 0.08, "hierro");
                    add(l, 0, -0.68, 0.7, 0.9, 0.42, 0.03, "hierro");
                    par(l, 0.45, -0.68, 0.64, 0.03, 0.42, 0.08, "hierro");
                    add(l, 0, -0.68, 0.73, 0.4, 0.34, 0.3, "barril");
                }
                if (estilo == 3) {
                    // Desierto: barra de focos y banderín en un mástil
                    add(l, 0, 0.42, 0.98, 0.6, 0.06, 0.08, "negro");
                    for (int k = -1; k <= 1; k++) add(l, k * 0.17, 0.455, 0.99, 0.12, 0.02, 0.06, "faro");
                    add(l, -0.3, -0.76, 0.6, 0.03, 0.03, 1.0, "hierro");
                    add(l, -0.3, -0.64, 1.42, 0.015, 0.22, 0.16, "naranja");
                }
                if (estilo == 4) {
                    // Cross: guardabarros laterales y llantas en el segundo color, dorsal en el manillar
                    recolorar(l, q -> q.c().equals("C1") && q.girada() && q.w() < 0.1, "C2");
                    recolorar(l, q -> q.c().equals("llanta"), "C2");
                    add(l, 0, 0.4, 0.97, 0.3, 0.02, 0.2, "blanco");
                    add(l, 0, 0.385, 1.0, 0.05, 0.01, 0.14, "negro");
                }
                placa[0] = Double.NaN;
                placa[1] = -0.865;
                placa[2] = 0.47;
            }
            default -> {
                // turismo (y base del taxi): compacto de cinco puertas
                add(l, 0, 0, 0.2, 1.36, 2.3, 0.38, "C1");
                add(l, 0, 1.2, 0.2, 1.2, 0.2, 0.34, "C1");
                parG(l, 0.56, 1.12, 0.2, 0.25, 0.25, 0.34, "C1", 0, 45, 0);
                add(l, 0, -1.2, 0.2, 1.24, 0.2, 0.36, "C1");
                parG(l, 0.57, -1.12, 0.2, 0.25, 0.25, 0.36, "C1", 0, 45, 0);
                g(l, 0, 0.84, 0.52, 1.26, 0.66, 0.08, "C1", -7, 0, 0);
                add(l, 0, 1.31, 0.13, 1.1, 0.08, 0.14, "gris");
                add(l, 0, -1.31, 0.13, 1.14, 0.08, 0.14, "gris");
                add(l, 0, 1.305, 0.3, 0.56, 0.02, 0.1, "negro");
                parG(l, 0.43, 1.295, 0.38, 0.28, 0.03, 0.09, "faro", 0, -10, 0);
                parG(l, 0.46, -1.3, 0.4, 0.28, 0.03, 0.1, "piloto", 0, 10, 0);
                add(l, 0, -0.22, 0.58, 1.22, 1.1, 0.38, "cristal");
                g(l, 0, 0.47, 0.56, 1.16, 0.05, 0.52, "cristal", 38, 0, 0);
                g(l, 0, -0.88, 0.56, 1.16, 0.05, 0.48, "cristal", -35, 0, 0);
                add(l, 0, -0.22, 0.96, 1.2, 1.0, 0.07, "C1");
                parG(l, 0.6, 0.47, 0.56, 0.05, 0.05, 0.52, "C1", 38, 0, 0);
                par(l, 0.615, -0.15, 0.58, 0.04, 0.08, 0.38, "C1");
                parG(l, 0.6, -0.88, 0.56, 0.05, 0.05, 0.48, "C1", -35, 0, 0);
                add(l, 0, -1.1, 0.52, 1.24, 0.32, 0.06, "C1");
                par(l, 0.71, 0.36, 0.64, 0.12, 0.06, 0.07, "C1");
                par(l, 0.682, 0.1, 0.46, 0.01, 0.12, 0.02, "grisc");
                par(l, 0.682, -0.45, 0.46, 0.01, 0.12, 0.02, "grisc");
                add(l, 0, 0, 0.08, 1.16, 2.2, 0.14, "negro");
                ruedas(l, 0.58, 0.85, -0.85, 0.5, 0.26, "llanta");
                if (!modelo.equals("taxi")) {
                    if (aspecto.extra(0)) {
                        add(l, 0, -1.15, 0.6, 1.1, 0.16, 0.05, "C1");
                        par(l, 0.4, -1.15, 0.56, 0.05, 0.08, 0.05, "gris");
                    }
                    if (estilo == 1) par(l, 0.683, 0, 0.36, 0.012, 2.2, 0.08, "C2");
                    if (estilo == 2) {
                        recolorar(l, q -> q.c().equals("C1") && q.z() >= 0.55 && q.z() < 1.2, "negro");
                        recolorar(l, q -> q.c().equals("llanta"), "gris");
                    }
                    if (estilo == 3) {
                        // Rally: dorsales con un 1, franjas en el capó y barra de focos
                        par(l, 0.684, -0.15, 0.26, 0.012, 0.36, 0.3, "blanco");
                        par(l, 0.69, -0.15, 0.3, 0.01, 0.06, 0.22, "negro");
                        for (int k = -1; k <= 1; k += 2) g(l, k * 0.2, 0.84, 0.525, 0.16, 0.66, 0.08, "C2", -7, 0, 0);
                        add(l, 0, 0.2, 1.03, 0.9, 0.1, 0.06, "negro");
                        for (int k = -1; k <= 1; k++) add(l, k * 0.28, 0.255, 1.035, 0.14, 0.02, 0.05, "faro");
                    }
                    if (estilo == 4) {
                        // Tuning: faldones, labio, alerón, llantas negras y escape
                        par(l, 0.69, 0, 0.12, 0.04, 1.9, 0.12, "C2");
                        add(l, 0, 1.36, 0.08, 1.2, 0.06, 0.06, "C2");
                        par(l, 0.45, -1.15, 0.58, 0.05, 0.08, 0.14, "negro");
                        add(l, 0, -1.18, 0.72, 1.3, 0.22, 0.04, "C2");
                        recolorar(l, q -> q.c().equals("llanta"), "negro");
                        add(l, 0.35, -1.37, 0.16, 0.12, 0.06, 0.12, "hierro");
                    }
                }
                placa[0] = 1.355;
                placa[1] = -1.355;
                placa[2] = 0.2;
            }
        }
        return l;
    }
}
