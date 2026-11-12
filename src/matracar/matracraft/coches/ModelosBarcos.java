package matracraft.coches;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Embarcaciones de MATRAMOTORS: geometría (mismo formato de piezas que los coches),
 * skins (estilo + colores + detalles) y posiciones de asientos y matrículas.
 *
 * Piezas: (lateral, frente, alto, ancho, largo, alto_caja, material). Alto 0 = línea de flotación.
 * Aspecto.extras: bits 0-1 estilo (+ bit 16 para los estilos 4-7); La Goma usa además 2-3 bandera (+ bit 19),
 * 4 lona, 5 refuerzo, 6-7 nº de parches, 8-15 semilla.
 */
final class ModelosBarcos {

    private ModelosBarcos() {}

    static final List<String> BARCOS = List.of("pedalo", "motoagua", "barco", "taxiboat", "goma", "patrullero", "hipersonica");

    static boolean esBarco(String modelo) { return BARCOS.contains(modelo); }

    private static void add(List<Modelos.Pieza> p, double x, double y, double z, double w, double l, double h, String c) {
        p.add(new Modelos.Pieza(x, y, z, w, l, h, c));
    }

    private static void par(List<Modelos.Pieza> p, double x, double y, double z, double w, double l, double h, String c) {
        add(p, x, y, z, w, l, h, c);
        add(p, -x, y, z, w, l, h, c);
    }

    private static void g(List<Modelos.Pieza> p, double x, double y, double z, double w, double l, double h, String c,
                          double rx, double ry, double rz) {
        p.add(new Modelos.Pieza(x, y, z, w, l, h, c, rx, ry, rz));
    }

    private static void parG(List<Modelos.Pieza> p, double x, double y, double z, double w, double l, double h, String c,
                             double rx, double ry, double rz) {
        g(p, x, y, z, w, l, h, c, rx, ry, rz);
        g(p, -x, y, z, w, l, h, c, rx, -ry, -rz);
    }

    // ---------- estilos ----------

    static final String[][] ESTILOS = {
            {"pedalo", "Toldo", "Tobogán", "Al sol", "Cisne", "Toldo a rayas"},
            {"motoagua", "Lisa", "Franja", "Dos tonos", "Carreras", "Cebra"},
            {"barco", "Franja", "Casco bicolor", "Sin toldo", "Pesquero", "Lujo"},
            {"taxiboat", "Cuadros", "Clásico", "Nocturno"},
            {"goma", "2 motores", "3 motores", "4 motores", "Salvamento", "Camuflaje"},
            {"patrullero", "Cuadros", "Nocturno", "Costera", "Rescate"},
            {"hipersonica", "Franja", "Morro pintado", "Dos tonos", "Carreras", "Cromada"},
    };
    private static final double[][] PESOS_ESTILO = {
            {5, 3, 2, 3, 2}, {4, 4, 2, 3, 2}, {5, 3, 2, 3, 2}, {3, 2, 2}, {3, 2, 1, 2, 2}, {3, 1, 2, 2}, {3, 2, 2, 2, 2}};
    static final String[] BANDERAS = {"", "Calavera", "Bandera del Faro", "Bandera Matraca", "Bandera de Israel", "Bandera de Marruecos"};

    /** Estilo guardado en Aspecto.extras: bits 0-1 y, para los estilos 4-7, el bit 16. */
    static int estilo(Modelos.Aspecto a) {
        return (a.extras() & 3) | ((a.extras() >> 16) & 1) << 2;
    }

    static int codificarEstilo(int estilo) {
        return (estilo & 3) | ((estilo >> 2) & 1) << 16;
    }

    /** Bandera de La Goma: bits 2-3 y, para las banderas 4-7, el bit 19. */
    static int bandera(Modelos.Aspecto a) {
        return (a.extras() >> 2 & 3) | ((a.extras() >> 19) & 1) << 2;
    }

    private static int indice(String modelo) {
        for (int i = 0; i < ESTILOS.length; i++) if (ESTILOS[i][0].equals(modelo)) return i;
        return -1;
    }

    static String nombreEstilo(String modelo, Modelos.Aspecto a) {
        int i = indice(modelo);
        if (i < 0) return "";
        int e = Math.min(estilo(a), ESTILOS[i].length - 2);
        String txt = ESTILOS[i][e + 1];
        if (modelo.equals("goma")) {
            int b = bandera(a);
            if (b > 0 && b < BANDERAS.length) txt += " · " + BANDERAS[b];
        }
        return txt;
    }

    private static int estiloAzar(String modelo) {
        double[] pesos = PESOS_ESTILO[indice(modelo)];
        double total = 0;
        for (double p : pesos) total += p;
        double t = ThreadLocalRandom.current().nextDouble(total);
        for (int i = 0; i < pesos.length; i++) { t -= pesos[i]; if (t < 0) return i; }
        return 0;
    }

    private static String azar(String... opciones) {
        double total = 0;
        for (String o : opciones) total += Double.parseDouble(o.split(":")[1]);
        double t = ThreadLocalRandom.current().nextDouble(total);
        for (String o : opciones) { String[] s = o.split(":"); t -= Double.parseDouble(s[1]); if (t < 0) return s[0]; }
        return opciones[0].split(":")[0];
    }

    /** Elige dos colores distintos (para que la franja se vea). */
    private static String distinto(String de, String... opciones) {
        for (int i = 0; i < 20; i++) { String c = azar(opciones); if (!c.equals(de)) return c; }
        return de.equals("BLACK") ? "WHITE" : "BLACK";
    }

    static Modelos.Aspecto azar(String modelo) {
        int estilo = codificarEstilo(estiloAzar(modelo));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (modelo) {
            case "pedalo" -> {
                String c1 = azar("WHITE:3", "LIME:3", "LIGHT_BLUE:3", "YELLOW:3", "ORANGE:2", "PINK:2", "MAGENTA:1.5", "CYAN:1.5", "RED:1");
                String c2 = distinto(c1, "YELLOW:3", "LIGHT_BLUE:3", "ORANGE:3", "LIME:2", "PINK:2", "BLUE:2", "WHITE:2", "RED:1", "PURPLE:0.5");
                return new Modelos.Aspecto(c1, c2, azar("LIGHT_BLUE:2", "RED:2", "YELLOW:2", "WHITE:2", "ORANGE:1", "LIME:1", "MAGENTA:1"), estilo);
            }
            case "motoagua" -> {
                String c1 = azar("WHITE:4", "BLACK:3", "GRAY:1.5", "BLUE:1", "RED:1", "YELLOW:1", "LIME:0.5");
                String c2 = distinto(c1, "ORANGE:3", "CYAN:3", "LIME:2", "RED:2", "YELLOW:2", "MAGENTA:1", "LIGHT_BLUE:1", "PURPLE:0.5");
                return new Modelos.Aspecto(c1, c2, azar("BLACK:3", "GRAY:2", "WHITE:1"), estilo);
            }
            case "barco" -> {
                String c1 = azar("WHITE:6", "BLACK:2", "BLUE:2", "LIGHT_GRAY:1", "RED:1", "CYAN:1", "GREEN:1", "YELLOW:0.5");
                String c2 = distinto(c1, "BLUE:3", "RED:2", "YELLOW:2", "CYAN:2", "ORANGE:1", "LIME:1", "WHITE:1", "BLACK:1");
                return new Modelos.Aspecto(c1, c2, azar("ORANGE:2", "WHITE:2", "BROWN:2", "RED:1", "LIGHT_BLUE:1", "BLACK:1", "YELLOW:0.5"), estilo);
            }
            case "taxiboat" -> {
                return new Modelos.Aspecto("YELLOW", "BLACK", azar("LIGHT_GRAY:3", "BLACK:1", "BLUE:1"), estilo);
            }
            case "goma" -> {
                String c1 = azar("GRAY:4", "BLACK:3", "BLUE:1", "GREEN:1", "ORANGE:1", "RED:0.7", "LIGHT_GRAY:0.5");
                // sin bandera el 40 %; si no, una de las cinco (Israel y Marruecos, las de los amigos de Tel Aviv)
                int bandera = r.nextDouble() < 0.4 ? 0 : 1 + r.nextInt(5);
                int extras = estilo | (bandera & 3) << 2 | ((bandera >> 2) & 1) << 19 | (r.nextDouble() < 0.5 ? 1 << 4 : 0) | (r.nextDouble() < 0.3 ? 1 << 5 : 0)
                        | r.nextInt(4) << 6 | r.nextInt(256) << 8;
                return new Modelos.Aspecto(c1, azar("BLACK:3", "GRAY:2", "WHITE:1"), azar("LIGHT_GRAY:3", "WHITE:2", "GRAY:2", "BLACK:1"), extras);
            }
            case "patrullero" -> {
                String casco = azar("WHITE:3", "BLUE:2", "BLACK:2", "GRAY:1");
                String[] par = casco.equals("WHITE")
                        ? azar("BLUE-YELLOW:4", "GREEN-YELLOW:2", "ORANGE-BLUE:1", "RED-YELLOW:1").split("-")
                        : azar("YELLOW-WHITE:3", "ORANGE-WHITE:1", "LIGHT_BLUE-WHITE:1", "LIME-YELLOW:1").split("-");
                return new Modelos.Aspecto(casco, par[0], par[1], estilo);
            }
            default -> {
                String c1 = azar("CYAN:3", "RED:2", "BLACK:2", "WHITE:2", "YELLOW:1.5", "ORANGE:1.5", "LIME:1", "MAGENTA:1", "BLUE:1", "PURPLE:1");
                String c2 = distinto(c1, "GRAY:2", "WHITE:2", "BLACK:2", "ORANGE:1.5", "YELLOW:1.5", "LIGHT_BLUE:1", "LIME:1", "RED:1");
                return new Modelos.Aspecto(c1, c2, azar("BLACK:3", "GRAY:1", "WHITE:1"), estilo);
            }
        }
    }

    // ---------- geometría ----------

    private static final String[][] BANDERA_PIXELES = {
            {},
            {"NNNNN", "NBBBN", "NBNBN", "NNBNN"},
            {"MMMMM", "BBBBB", "NNNNN", "NNNNN"},
            {"ANNNA", "AANAA", "ANANA", "ANNNA"},
            // Israel: blanca, dos franjas azules y la estrella de David
            {"BBBBBBBBBBBBBBB", "ZZZZZZZZZZZZZZZ", "BBBBBBBBBBBBBBB", "BBBBBBBZBBBBBBB", "BBBBZZZZZZZBBBB", "BBBBBZBBBZBBBBB", "BBBBBBZBZBBBBBB", "BBBBBZBBBZBBBBB", "BBBBZZZZZZZBBBB", "BBBBBBBZBBBBBBB", "BBBBBBBBBBBBBBB", "ZZZZZZZZZZZZZZZ", "BBBBBBBBBBBBBBB"},
            // Marruecos: roja con la estrella verde de cinco puntas
            {"RRRRRRRRRRRRRRR", "RRRRRRRRRRRRRRR", "RRRRRRRGRRRRRRR", "RRRRRRGGGRRRRRR", "RRRRGGGGGGGRRRR", "RRRRRGGGGGRRRRR", "RRRRRRGGGRRRRRR", "RRRRRGGRGGRRRRR", "RRRRRGRRRGRRRRR", "RRRRRRRRRRRRRRR", "RRRRRRRRRRRRRRR"},
    };

    private static String colorBandera(char c) {
        return switch (c) {
            case 'B' -> "blanco"; case 'M' -> "morado"; case 'A' -> "amarillo"; case 'Z' -> "azul";
            case 'R' -> "rojo"; case 'G' -> "verdeo"; default -> "negro";
        };
    }

    /** Mástil y bandera: cada fila de la bandera se pinta en tramos del mismo color (menos piezas). */
    private static void bandera(List<Modelos.Pieza> p, int tipo) {
        if (tipo <= 0 || tipo >= BANDERA_PIXELES.length) return;
        double x = -0.72, z = -1.95, alto = 1.75, largo = 0.7;
        add(p, x, z, 0, 0.05, 0.05, alto, "hierro");
        String[] filas = BANDERA_PIXELES[tipo];
        double px = largo / filas[0].length();
        double py = Math.min(px, 0.56 / filas.length);
        for (int f = 0; f < filas.length; f++) {
            String fila = filas[f];
            int c = 0;
            while (c < fila.length()) {
                int fin = c;
                while (fin + 1 < fila.length() && fila.charAt(fin + 1) == fila.charAt(c)) fin++;
                int n = fin - c + 1;
                add(p, x, z - 0.05 - px * c - px * n / 2, alto - (f + 1) * py, 0.02, px * n, py, colorBandera(fila.charAt(c)));
                c = fin + 1;
            }
        }
    }

    /**
     * Piezas del barco. lateral = {x, frente, alto} de la matrícula del costado de estribor
     * (la de babor es simétrica); NaN si no lleva.
     */
    static List<Modelos.Pieza> piezas(String modelo, Modelos.Aspecto a, double[] lateral) {
        List<Modelos.Pieza> p = new ArrayList<>();
        int estilo = estilo(a);
        switch (modelo) {
            case "pedalo" -> {
                add(p, 0.55, 0, -0.25, 0.45, 2.2, 0.4, "C1");
                add(p, -0.55, 0, -0.25, 0.45, 2.2, 0.4, "C1");
                add(p, 0, 0.9, -0.1, 1.55, 0.45, 0.25, "C1");
                add(p, 0, -0.05, 0.12, 1.55, 1.8, 0.08, "C1");
                add(p, 0.8, 0, -0.02, 0.08, 2.15, 0.12, "C2");
                add(p, -0.8, 0, -0.02, 0.08, 2.15, 0.12, "C2");
                add(p, 0, -1.12, -0.02, 1.55, 0.08, 0.12, "C2");
                add(p, 0, -0.25, 0.2, 0.6, 0.55, 0.18, "C2");
                add(p, 0, -0.55, 0.2, 0.6, 0.08, 0.6, "C2");
                add(p, 0, 0.35, 0.18, 0.4, 0.4, 0.12, "grisc");
                add(p, 0.12, 0.35, 0.3, 0.06, 0.12, 0.06, "negro");
                add(p, -0.12, 0.35, 0.3, 0.06, 0.12, 0.06, "negro");
                add(p, 0, 0.95, 0.15, 0.22, 0.28, 0.16, "amarillo");
                add(p, 0, 1.05, 0.31, 0.15, 0.15, 0.15, "amarillo");
                add(p, 0, 1.15, 0.36, 0.08, 0.08, 0.04, "naranja");
                add(p, 0.62, -0.75, 0.2, 0.04, 0.04, 1.3, "hierro");
                add(p, -0.62, -0.75, 0.2, 0.04, 0.04, 1.3, "hierro");
                add(p, 0.62, 0.55, 0.2, 0.04, 0.04, 1.2, "hierro");
                add(p, -0.62, 0.55, 0.2, 0.04, 0.04, 1.2, "hierro");
                add(p, 0, -0.1, 1.45, 1.4, 1.45, 0.05, "C3");
                add(p, 0.62, -0.1, 1.38, 0.04, 1.4, 0.07, "C3");
                add(p, -0.62, -0.1, 1.38, 0.04, 1.4, 0.07, "C3");
            }
            case "motoagua" -> {
                // casco (bajo el agua) con proa escalonada en punta
                add(p, 0, -0.1, -0.32, 0.92, 2.2, 0.22, "C1");
                add(p, 0, 1.15, -0.27, 0.7, 0.4, 0.22, "C1");
                add(p, 0, 1.45, -0.2, 0.42, 0.3, 0.2, "C1");
                // cubierta, defensas y alfombrillas de los pies
                add(p, 0, -0.1, -0.1, 0.9, 2.1, 0.18, "C1");
                par(p, 0.465, -0.05, -0.14, 0.04, 2.0, 0.07, "C2");
                par(p, 0.33, -0.3, 0.08, 0.2, 1.2, 0.012, "negro");
                // sillín largo con respaldo
                add(p, 0, -0.35, 0.08, 0.42, 1.15, 0.12, "C3");
                add(p, 0, -0.86, 0.2, 0.42, 0.16, 0.07, "C3");
                // capó en dos alturas, franja, parabrisas y faro
                add(p, 0, 0.55, 0.08, 0.7, 0.45, 0.2, "C1");
                add(p, 0, 0.95, 0.08, 0.62, 0.45, 0.14, "C1");
                add(p, 0, 1.25, 0.04, 0.38, 0.25, 0.1, "C1");
                add(p, 0, 0.62, 0.28, 0.16, 0.3, 0.015, "C2");
                add(p, 0, 1.0, 0.22, 0.16, 0.35, 0.015, "C2");
                add(p, 0, 0.75, 0.28, 0.5, 0.05, 0.12, "cristal");
                add(p, 0, 1.18, 0.12, 0.2, 0.03, 0.06, "faro");
                // columna de dirección y manillar
                add(p, 0, 0.42, 0.28, 0.12, 0.12, 0.2, "negro");
                add(p, 0, 0.4, 0.48, 0.7, 0.06, 0.06, "negro");
                par(p, 0.33, 0.4, 0.47, 0.09, 0.09, 0.08, "grisc");
                // plataforma de popa y tobera
                add(p, 0, -1.27, -0.06, 0.84, 0.3, 0.1, "C3");
                add(p, 0, -1.43, -0.22, 0.18, 0.1, 0.14, "hierro");
            }
            case "barco" -> {
                add(p, 0, -0.1, -0.3, 1.8, 3.6, 0.72, "C1");
                add(p, 0, 1.9, -0.22, 1.45, 0.4, 0.62, "C1");
                add(p, 0, 2.22, -0.12, 0.85, 0.3, 0.5, "C1");
                add(p, 0.905, 0, -0.05, 0.02, 3.55, 0.12, "C2");
                add(p, -0.905, 0, -0.05, 0.02, 3.55, 0.12, "C2");
                add(p, 0, -0.25, 0.42, 1.6, 3.1, 0.04, "madera");
                add(p, 0.86, 0.4, 0.62, 0.04, 2.8, 0.04, "hierro");
                add(p, -0.86, 0.4, 0.62, 0.04, 2.8, 0.04, "hierro");
                add(p, 0.86, -0.9, 0.42, 0.04, 0.04, 0.22, "hierro");
                add(p, -0.86, -0.9, 0.42, 0.04, 0.04, 0.22, "hierro");
                add(p, 0.86, 0.4, 0.42, 0.04, 0.04, 0.22, "hierro");
                add(p, -0.86, 0.4, 0.42, 0.04, 0.04, 0.22, "hierro");
                add(p, 0.86, 1.6, 0.42, 0.04, 0.04, 0.22, "hierro");
                add(p, -0.86, 1.6, 0.42, 0.04, 0.04, 0.22, "hierro");
                add(p, 0, 0.35, 0.42, 0.62, 0.5, 0.55, "C1");
                add(p, 0, 0.58, 0.97, 0.62, 0.04, 0.3, "cristal");
                add(p, 0, 0.3, 0.97, 0.18, 0.06, 0.18, "negro");
                add(p, 0, -0.2, 0.42, 0.5, 0.45, 0.28, "C3");
                add(p, 0, -1.3, 0.42, 1.55, 0.5, 0.28, "C3");
                add(p, 0, -1.52, 0.42, 1.55, 0.08, 0.5, "C3");
                add(p, -0.82, 1.8, 0.42, 0.06, 0.12, 0.08, "piloto");
                add(p, 0.82, 1.8, 0.42, 0.06, 0.12, 0.08, "verde");
                add(p, 0.72, 0.05, 0.42, 0.04, 0.04, 1.45, "hierro");
                add(p, -0.72, 0.05, 0.42, 0.04, 0.04, 1.45, "hierro");
                add(p, 0.72, -0.95, 0.42, 0.04, 0.04, 1.45, "hierro");
                add(p, -0.72, -0.95, 0.42, 0.04, 0.04, 1.45, "hierro");
                add(p, 0, -0.45, 1.87, 1.55, 1.4, 0.05, "lona");
                add(p, 0, -1.98, 0.12, 0.36, 0.32, 0.6, "negro");
                add(p, 0, -1.98, 0.72, 0.42, 0.42, 0.3, "negro");
                add(p, 0, -1.98, -0.62, 0.12, 0.12, 0.74, "gris");
            }
            case "taxiboat" -> {
                add(p, 0, -0.05, -0.3, 2.1, 4.2, 0.62, "amarillo");
                add(p, 0, 2.2, -0.2, 1.6, 0.4, 0.5, "amarillo");
                add(p, 0, 2.48, -0.1, 0.9, 0.3, 0.38, "amarillo");
                add(p, 1.08, 0, 0, 0.14, 4.2, 0.26, "negro");
                add(p, -1.08, 0, 0, 0.14, 4.2, 0.26, "negro");
                add(p, 0, -2.17, 0, 2.3, 0.14, 0.26, "negro");
                add(p, 1.155, -1.9, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, -1.9, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, -1.9, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, -1.9, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, -1.68, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, -1.68, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, -1.68, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, -1.68, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, -1.46, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, -1.46, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, -1.46, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, -1.46, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, -1.24, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, -1.24, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, -1.24, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, -1.24, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, -1.02, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, -1.02, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, -1.02, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, -1.02, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, -0.8, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, -0.8, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, -0.8, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, -0.8, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, -0.58, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, -0.58, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, -0.58, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, -0.58, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, -0.36, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, -0.36, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, -0.36, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, -0.36, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, -0.14, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, -0.14, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, -0.14, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, -0.14, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, 0.08, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, 0.08, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, 0.08, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, 0.08, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, 0.3, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, 0.3, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, 0.3, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, 0.3, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, 0.52, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, 0.52, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, 0.52, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, 0.52, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, 0.74, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, 0.74, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, 0.74, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, 0.74, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, 0.96, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, 0.96, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, 0.96, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, 0.96, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, 1.18, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, 1.18, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, 1.18, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, 1.18, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, 1.4, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, 1.4, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, 1.4, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, 1.4, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, 1.62, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, 1.62, 0.3, 0.02, 0.22, 0.1, "lona");
                add(p, 1.155, 1.62, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, 1.62, 0.4, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, 1.84, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, -1.155, 1.84, 0.3, 0.02, 0.22, 0.1, "negro");
                add(p, 1.155, 1.84, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, -1.155, 1.84, 0.4, 0.02, 0.22, 0.1, "lona");
                add(p, 0, -0.1, 0.32, 1.9, 3.6, 0.04, "madera");
                add(p, 0.72, -0.6, 0.32, 0.45, 2.4, 0.28, "C3");
                add(p, -0.72, -0.6, 0.32, 0.45, 2.4, 0.28, "C3");
                add(p, 0.93, -0.6, 0.32, 0.06, 2.4, 0.6, "C3");
                add(p, -0.93, -0.6, 0.32, 0.06, 2.4, 0.6, "C3");
                add(p, 0, 1.15, 0.32, 0.62, 0.5, 0.6, "amarillo");
                add(p, 0, 1.4, 0.92, 0.62, 0.04, 0.3, "cristal");
                add(p, 0.88, 1.3, 0.32, 0.05, 0.05, 1.6, "negro");
                add(p, -0.88, 1.3, 0.32, 0.05, 0.05, 1.6, "negro");
                add(p, 0.88, -1.8, 0.32, 0.05, 0.05, 1.6, "negro");
                add(p, -0.88, -1.8, 0.32, 0.05, 0.05, 1.6, "negro");
                add(p, 0, -0.25, 1.92, 2, 3.4, 0.08, "amarillo");
                add(p, 0, 0.6, 2, 0.7, 0.14, 0.28, "negro");
                add(p, 0, 0.6, 2.06, 0.6, 0.15, 0.16, "faro");
                add(p, 0.9, -1.4, 0.8, 0.05, 0.4, 0.4, "naranja");
                add(p, 0, -2.3, 0.1, 0.38, 0.32, 0.6, "negro");
                add(p, 0, -2.3, 0.7, 0.44, 0.42, 0.3, "negro");
                add(p, 0, -2.3, -0.6, 0.12, 0.12, 0.72, "gris");
            }
            case "goma" -> {
                add(p, 0.78, -0.05, -0.18, 0.46, 4.4, 0.46, "C1");
                add(p, -0.78, -0.05, -0.18, 0.46, 4.4, 0.46, "C1");
                add(p, 0, 2.25, -0.16, 1.5, 0.42, 0.44, "C1");
                add(p, 0, 2.58, -0.1, 0.95, 0.3, 0.36, "C1");
                add(p, 0, 0, -0.3, 1.15, 4.3, 0.3, "C2");
                add(p, 0, -0.1, 0, 1.12, 4.1, 0.04, "grisc");
                add(p, 0, 0.3, 0.04, 0.72, 0.62, 0.72, "grisc");
                add(p, 0, 0.55, 0.76, 0.6, 0.04, 0.22, "cristal");
                add(p, 0, -0.3, 0.04, 0.55, 0.42, 0.5, "negro");
                add(p, 0.42, 0.25, 0.76, 0.04, 0.04, 0.95, "hierro");
                add(p, -0.42, 0.25, 0.76, 0.04, 0.04, 0.95, "hierro");
                add(p, 0.42, -0.45, 0.76, 0.04, 0.04, 0.95, "hierro");
                add(p, -0.42, -0.45, 0.76, 0.04, 0.04, 0.95, "hierro");
                add(p, 0, -0.1, 1.7, 1, 1.05, 0.05, "negro");
                add(p, 0, -0.1, 1.75, 0.45, 0.12, 0.08, "grisc");
                add(p, 0.25, 1.4, 0.04, 0.42, 0.62, 0.32, "fardo");
                add(p, -0.22, 1.5, 0.04, 0.4, 0.55, 0.3, "fardo");
                add(p, 0, -1.3, 0.04, 0.5, 0.6, 0.32, "fardo");
                add(p, -0.45, -2.33, 0, 0.25, 0.3, 0.6, "negro");
                add(p, -0.45, -2.33, 0.6, 0.29, 0.36, 0.3, "grisc");
                add(p, -0.45, -2.33, -0.55, 0.1, 0.1, 0.6, "gris");
                add(p, -0.15, -2.33, 0, 0.25, 0.3, 0.6, "negro");
                add(p, -0.15, -2.33, 0.6, 0.29, 0.36, 0.3, "grisc");
                add(p, -0.15, -2.33, -0.55, 0.1, 0.1, 0.6, "gris");
                add(p, 0.15, -2.33, 0, 0.25, 0.3, 0.6, "negro");
                add(p, 0.15, -2.33, 0.6, 0.29, 0.36, 0.3, "grisc");
                add(p, 0.15, -2.33, -0.55, 0.1, 0.1, 0.6, "gris");
                add(p, 0.45, -2.33, 0, 0.25, 0.3, 0.6, "negro");
                add(p, 0.45, -2.33, 0.6, 0.29, 0.36, 0.3, "grisc");
                add(p, 0.45, -2.33, -0.55, 0.1, 0.1, 0.6, "gris");
            }
            case "patrullero" -> {
                add(p, 0, -0.1, -0.3, 2, 4.4, 0.78, "C1");
                add(p, 0, 2.2, -0.2, 1.6, 0.4, 0.66, "C1");
                add(p, 0, 2.5, -0.1, 0.9, 0.3, 0.54, "C1");
                add(p, 1.01, -2.2, 0.04, 0.02, 0.42, 0.34, "C2");
                add(p, -1.01, -2.2, 0.04, 0.02, 0.42, 0.34, "C2");
                add(p, 1.01, -1.78, 0.04, 0.02, 0.42, 0.34, "C3");
                add(p, -1.01, -1.78, 0.04, 0.02, 0.42, 0.34, "C3");
                add(p, 1.01, -1.36, 0.04, 0.02, 0.42, 0.34, "C2");
                add(p, -1.01, -1.36, 0.04, 0.02, 0.42, 0.34, "C2");
                add(p, 1.01, -0.94, 0.04, 0.02, 0.42, 0.34, "C3");
                add(p, -1.01, -0.94, 0.04, 0.02, 0.42, 0.34, "C3");
                add(p, 1.01, -0.52, 0.04, 0.02, 0.42, 0.34, "C2");
                add(p, -1.01, -0.52, 0.04, 0.02, 0.42, 0.34, "C2");
                add(p, 1.01, -0.1, 0.04, 0.02, 0.42, 0.34, "C3");
                add(p, -1.01, -0.1, 0.04, 0.02, 0.42, 0.34, "C3");
                add(p, 1.01, 0.32, 0.04, 0.02, 0.42, 0.34, "C2");
                add(p, -1.01, 0.32, 0.04, 0.02, 0.42, 0.34, "C2");
                add(p, 1.01, 0.74, 0.04, 0.02, 0.42, 0.34, "C3");
                add(p, -1.01, 0.74, 0.04, 0.02, 0.42, 0.34, "C3");
                add(p, 1.01, 1.16, 0.04, 0.02, 0.42, 0.34, "C2");
                add(p, -1.01, 1.16, 0.04, 0.02, 0.42, 0.34, "C2");
                add(p, 1.01, 1.58, 0.04, 0.02, 0.42, 0.34, "C3");
                add(p, -1.01, 1.58, 0.04, 0.02, 0.42, 0.34, "C3");
                add(p, 1.01, 2, 0.04, 0.02, 0.42, 0.34, "C2");
                add(p, -1.01, 2, 0.04, 0.02, 0.42, 0.34, "C2");
                add(p, 0, -0.3, 0.48, 1.85, 3.9, 0.04, "grisc");
                add(p, 0, 0.55, 0.48, 1.6, 1.6, 1.1, "C1");
                add(p, 0, 1.36, 0.98, 1.5, 0.03, 0.5, "cristal");
                add(p, 0.805, 0.55, 0.98, 0.03, 1.4, 0.45, "cristal");
                add(p, -0.805, 0.55, 0.98, 0.03, 1.4, 0.45, "cristal");
                add(p, 0, 0.55, 1.58, 1.72, 1.75, 0.08, "C1");
                add(p, 0.35, 0.8, 1.66, 0.42, 0.16, 0.12, "luzazul");
                add(p, -0.35, 0.8, 1.66, 0.42, 0.16, 0.12, "luzazul");
                add(p, 0, 0.15, 1.66, 0.04, 0.04, 0.85, "hierro");
                add(p, 0, -0.25, 0.9, 0.5, 0.04, 0.5, "amarillo");
                add(p, 0.95, -1.15, 0.72, 0.04, 2.1, 0.04, "hierro");
                add(p, -0.95, -1.15, 0.72, 0.04, 2.1, 0.04, "hierro");
                add(p, 0.95, -2.1, 0.48, 0.04, 0.04, 0.26, "hierro");
                add(p, -0.95, -2.1, 0.48, 0.04, 0.04, 0.26, "hierro");
                add(p, 0.95, -1.15, 0.48, 0.04, 0.04, 0.26, "hierro");
                add(p, -0.95, -1.15, 0.48, 0.04, 0.04, 0.26, "hierro");
                add(p, 0.95, -0.2, 0.48, 0.04, 0.04, 0.26, "hierro");
                add(p, -0.95, -0.2, 0.48, 0.04, 0.04, 0.26, "hierro");
                add(p, -0.38, -2.38, 0.1, 0.32, 0.32, 0.65, "negro");
                add(p, -0.38, -2.38, 0.75, 0.38, 0.42, 0.32, "negro");
                add(p, -0.38, -2.38, -0.55, 0.12, 0.12, 0.66, "gris");
                add(p, 0.38, -2.38, 0.1, 0.32, 0.32, 0.65, "negro");
                add(p, 0.38, -2.38, 0.75, 0.38, 0.42, 0.32, "negro");
                add(p, 0.38, -2.38, -0.55, 0.12, 0.12, 0.66, "gris");
            }
            case "hipersonica" -> {
                add(p, 0.55, -0.1, -0.3, 0.5, 5.2, 0.5, "C1");
                add(p, -0.55, -0.1, -0.3, 0.5, 5.2, 0.5, "C1");
                add(p, 0.55, 2.7, -0.25, 0.38, 0.6, 0.38, "C1");
                add(p, -0.55, 2.7, -0.25, 0.38, 0.6, 0.38, "C1");
                add(p, 0.55, 3.08, -0.2, 0.22, 0.3, 0.28, "C1");
                add(p, -0.55, 3.08, -0.2, 0.22, 0.3, 0.28, "C1");
                add(p, 0, -0.15, 0, 1.6, 4.9, 0.25, "C1");
                add(p, 0.805, -0.2, -0.05, 0.02, 3.6, 0.1, "C2");
                add(p, -0.805, -0.2, -0.05, 0.02, 3.6, 0.1, "C2");
                add(p, 0, 1.2, 0.25, 1.62, 0.6, 0.03, "C2");
                add(p, 0, -0.1, 0.25, 1.62, 0.25, 0.03, "C2");
                add(p, 0, -0.3, 0.25, 0.9, 1.5, 0.36, "cristal");
                add(p, 0, -1.1, 0.25, 1, 0.2, 0.48, "C2");
                add(p, 0, -1.95, 0.25, 1.45, 1.1, 0.14, "C3");
                add(p, 0, -2.55, 0.39, 1.4, 0.1, 0.3, "C2");
                add(p, 0.4, -2.85, 0, 0.4, 0.42, 0.9, "negro");
                add(p, -0.4, -2.85, 0, 0.4, 0.42, 0.9, "negro");
                add(p, 0.4, -2.85, 0.9, 0.46, 0.52, 0.38, "C2");
                add(p, -0.4, -2.85, 0.9, 0.46, 0.52, 0.38, "C2");
                add(p, 0.4, -2.85, -0.6, 0.12, 0.12, 0.62, "gris");
                add(p, -0.4, -2.85, -0.6, 0.12, 0.12, 0.62, "gris");
                add(p, 0, 2.2, 0.25, 0.4, 0.03, 0.08, "faro");
            }
            default -> { }
        }
        Predicate<Modelos.Pieza> quitar = null;
        switch (modelo) {
            case "pedalo" -> {
                if (estilo >= 1 && estilo <= 3) quitar = q -> q.c().equals("hierro") || (q.c().equals("C3") && q.z() > 1.3);
                if (estilo == 3) {
                    // Cisne: cuello, cabeza y alas blancas en lugar del patito
                    Predicate<Modelos.Pieza> sinToldo = quitar;
                    quitar = q -> sinToldo.test(q) || q.c().equals("amarillo") || q.c().equals("naranja");
                    add(p, 0, 0.98, 0.18, 0.2, 0.22, 0.25, "blanco");
                    g(p, 0, 1.06, 0.36, 0.16, 0.16, 0.6, "blanco", -14, 0, 0);
                    add(p, 0, 1.2, 0.9, 0.2, 0.34, 0.18, "blanco");
                    add(p, 0, 1.42, 0.92, 0.1, 0.14, 0.08, "naranja");
                    par(p, 0.105, 1.26, 0.97, 0.01, 0.05, 0.05, "negro");
                    parG(p, 0.62, -0.15, 0.16, 0.12, 1.3, 0.34, "blanco", -12, 0, 8);
                }
                if (estilo == 4) {
                    // Toldo a rayas: el toldo de siempre con franjas del color 2
                    for (int k = 0; k < 4; k++) add(p, 0, -0.65 + k * 0.36, 1.505, 1.41, 0.17, 0.01, "C2");
                    par(p, 0.55, 0, 0.0, 0.46, 0.25, 0.16, "C3");
                    par(p, 0.55, -0.8, 0.0, 0.46, 0.25, 0.16, "C3");
                    par(p, 0.55, 0.8, 0.0, 0.46, 0.25, 0.16, "C3");
                }
                if (estilo == 1) {
                    add(p, 0, -0.85, 0.2, 0.7, 0.45, 0.9, "C2");
                    for (int k = 0; k < 4; k++) add(p, 0, -0.62, 0.3 + k * 0.22, 0.6, 0.04, 0.04, "C3");
                    for (int k = 0; k < 5; k++) add(p, 0, -0.3 + k * 0.32, 1.0 - k * 0.2, 0.5, 0.34, 0.1, "C3");
                    par(p, 0.27, 0.35, 0.25, 0.04, 1.6, 0.12, "C3");
                }
                lateral[0] = Double.NaN;
            }
            case "motoagua" -> {
                // Franja: banda del color 2 a lo largo de los costados
                if (estilo == 1) par(p, 0.452, -0.1, -0.02, 0.012, 1.9, 0.06, "C2");
                // Dos tonos: el casco de abajo en el color 2
                if (estilo == 2) p.replaceAll(q -> q.c().equals("C1") && q.z() < -0.15
                        ? q.color("C2") : q);
                // Carreras: capó del color 2 y dorsales blancos en los costados
                if (estilo == 3) {
                    p.replaceAll(q -> q.c().equals("C1") && q.z() >= 0.03 ? q.color("C2") : q);
                    par(p, 0.466, -0.45, -0.08, 0.012, 0.45, 0.17, "blanco");
                    par(p, 0.468, -0.45, -0.03, 0.012, 0.08, 0.08, "negro");
                }
                // Cebra: rayas inclinadas del color 2
                if (estilo == 4) for (int k = 0; k < 5; k++) parG(p, 0.454, -0.95 + k * 0.42, -0.14, 0.012, 0.14, 0.24, "C2", 30, 0, 0);
                lateral[0] = 0.465; lateral[1] = 0.55; lateral[2] = -0.04;
            }
            case "barco" -> {
                if (estilo == 1) { add(p, 0, -0.1, -0.31, 1.82, 3.62, 0.32, "C2"); add(p, 0, 1.9, -0.23, 1.47, 0.42, 0.3, "C2"); }
                if (estilo == 2) {
                    quitar = q -> q.c().equals("lona") || (q.c().equals("hierro") && q.h() > 1);
                    par(p, 0.905, 0, 0.12, 0.02, 3.55, 0.06, "C2");
                }
                if (estilo == 3) {
                    // Pesquero: sin toldo, cañas a popa, nevera y franja
                    quitar = q -> q.c().equals("lona") || (q.c().equals("hierro") && q.h() > 1);
                    parG(p, 0.62, -1.45, 0.42, 0.03, 0.03, 1.7, "hierro", 28, 0, -10);
                    par(p, 0.62, -1.3, 0.42, 0.06, 0.06, 0.25, "negro");
                    add(p, 0.45, -0.75, 0.46, 0.42, 0.3, 0.3, "blanco");
                    add(p, 0.45, -0.75, 0.76, 0.44, 0.32, 0.04, "azul");
                    par(p, 0.905, 0, 0.2, 0.02, 3.55, 0.08, "C2");
                }
                if (estilo == 4) {
                    // Lujo: filete dorado, asientos blancos y plataforma de baño de madera
                    p.replaceAll(q -> q.c().equals("C3") ? q.color("blanco") : q);
                    par(p, 0.906, 0, 0.25, 0.02, 3.5, 0.04, "amarillo");
                    add(p, 0, -1.98, 0.08, 1.6, 0.3, 0.06, "madera");
                    add(p, 0, -0.45, 1.86, 1.6, 1.45, 0.02, "C2");
                }
                lateral[0] = 0.915; lateral[1] = 1.0; lateral[2] = 0.15;
            }
            case "taxiboat" -> {
                // Clásico: casco de madera barnizada con toldo amarillo
                if (estilo == 1) p.replaceAll(q -> q.c().equals("amarillo") && q.z() < 0 ? q.color("madera") : q);
                // Nocturno: casco negro con luces a lo largo de la borda
                if (estilo == 2) {
                    p.replaceAll(q -> q.c().equals("amarillo") && q.z() < 0 ? q.color("negro") : q);
                    for (int k = 0; k < 5; k++) par(p, 1.16, -1.8 + k * 0.85, 0.2, 0.02, 0.1, 0.06, "faro");
                }
                lateral[0] = 1.16; lateral[1] = 1.3; lateral[2] = 0.05;
            }
            case "goma" -> {
                int n = estilo <= 2 ? estilo + 2 : estilo == 3 ? 2 : 3;
                quitar = q -> Math.abs(q.y() + 2.33) < 0.01;
                // Salvamento: flotadores naranjas con banda blanca; Camuflaje: verde con manchas
                if (estilo == 3) {
                    p.replaceAll(q -> q.c().equals("C1") ? q.color("naranja") : q);
                    par(p, 0.78, -0.05, 0.27, 0.47, 3.6, 0.03, "blanco");
                }
                if (estilo == 4) {
                    p.replaceAll(q -> q.c().equals("C1") ? q.color("verdeo") : q);
                    double[][] manchas = {{0.78, 1.4}, {-0.78, 0.6}, {0.78, -0.4}, {-0.78, -1.3}, {0.78, -1.9}, {-0.78, 1.8}};
                    for (int k = 0; k < manchas.length; k++)
                        add(p, manchas[k][0], manchas[k][1], -0.02 + (k % 2) * 0.06, 0.48, 0.35 + (k % 3) * 0.1, 0.32 - (k % 2) * 0.06, k % 2 == 0 ? "madera" : "negro");
                }
                double[] xs = n == 2 ? new double[]{-0.33, 0.33} : n == 3 ? new double[]{-0.4, 0, 0.4} : new double[]{-0.45, -0.15, 0.15, 0.45};
                List<Modelos.Pieza> motores = new ArrayList<>();
                for (double x : xs) {
                    add(motores, x, -2.33, 0, 0.25, 0.3, 0.6, "negro");
                    add(motores, x, -2.33, 0.6, 0.29, 0.36, 0.3, "grisc");
                    add(motores, x, -2.33, -0.55, 0.1, 0.1, 0.6, "gris");
                }
                p.removeIf(quitar);
                quitar = null;
                p.addAll(motores);
                int ex = a.extras();
                boolean conCarga = true; // los fardos solo se ven con carga; Modelos los quita si el maletero va por debajo del 50 %
                if ((ex >> 4 & 1) == 1 && conCarga)
                    for (Modelos.Pieza f : new ArrayList<>(p))
                        if (f.c().equals("fardo")) add(p, f.x(), f.y(), f.z() + f.h(), f.w() + 0.06, f.l() + 0.06, 0.04, "lonaazul");
                if ((ex >> 5 & 1) == 1) par(p, 0.78, -0.05, 0.1, 0.48, 4.3, 0.06, "naranja");
                int parches = ex >> 6 & 3;
                long semilla = ex >> 8 & 255;
                for (int k = 0; k < parches; k++) {
                    double lado = ((semilla >> k) & 1) == 0 ? -0.8 : 0.8;
                    double z = -1.8 + ((semilla * (k + 3) * 37) % 100) / 100.0 * 3.6;
                    add(p, lado, z, 0.12, 0.44, 0.35, 0.18, "grisc");
                }
                bandera(p, bandera(a));
                lateral[0] = 1.015; lateral[1] = 1.2; lateral[2] = 0.05;
            }
            case "patrullero" -> {
                if (estilo == 1) {
                    List<Modelos.Pieza> nuevas = new ArrayList<>();
                    for (Modelos.Pieza q : p)
                        nuevas.add((q.c().equals("C2") || q.c().equals("C3")) && Math.abs(Math.abs(q.x()) - 1.01) < 0.01
                                ? q.color("negro") : q);
                    p = nuevas;
                    par(p, 1.012, -0.1, 0.18, 0.02, 4.2, 0.06, "amarillo");
                    par(p, 0.7, 1.4, 0.48, 0.12, 0.06, 0.08, "luzazul");
                }
                if (estilo == 2) {
                    // Costera: casco blanco con banda diagonal roja
                    p.replaceAll(q -> q.c().equals("C1") && q.z() < 0 ? q.color("blanco") : q);
                    parG(p, 1.012, 1.2, -0.15, 0.02, 0.35, 0.95, "rojo", 30, 0, 0);
                    parG(p, 1.012, 0.92, -0.15, 0.02, 0.12, 0.95, "azul", 30, 0, 0);
                }
                if (estilo == 3) {
                    // Rescate: todo naranja con franjas blancas
                    p.replaceAll(q -> q.c().equals("C1") ? q.color("naranja") : q);
                    p.replaceAll(q -> (q.c().equals("C2") || q.c().equals("C3")) && Math.abs(Math.abs(q.x()) - 1.01) < 0.01 ? q.color("blanco") : q);
                }
                lateral[0] = 1.025; lateral[1] = 1.9; lateral[2] = 0.42;
            }
            case "hipersonica" -> {
                if (estilo == 1) {
                    List<Modelos.Pieza> nuevas = new ArrayList<>();
                    for (Modelos.Pieza q : p)
                        nuevas.add(q.y() > 2.5 && q.c().equals("C1") ? q.color("C2") : q);
                    p = nuevas;
                    add(p, 0, 1.9, 0.25, 1.62, 0.9, 0.02, "C2");
                }
                if (estilo == 2) { par(p, 0.55, 1.2, -0.31, 0.52, 2.6, 0.34, "C2"); add(p, 0, 1.2, 0, 1.62, 2.5, 0.12, "C2"); }
                if (estilo == 3) {
                    // Carreras: damero blanco y negro a lo ancho de la cubierta
                    for (int k = 0; k < 8; k++)
                        for (int f = 0; f < 2; f++)
                            add(p, -0.7 + k * 0.2, 0.75 + f * 0.2, 0.25, 0.2, 0.2, 0.035, (k + f) % 2 == 0 ? "blanco" : "negro");
                }
                if (estilo == 4) {
                    // Cromada: remates de acero y franjas plateadas
                    p.replaceAll(q -> q.c().equals("C2") ? q.color("grisc") : q);
                    par(p, 0.81, -0.2, 0.08, 0.02, 3.6, 0.04, "hierro");
                }
                lateral[0] = 0.815; lateral[1] = 1.6; lateral[2] = 0.05;
            }
            default -> { }
        }
        if (quitar != null) p.removeIf(quitar);
        return p;
    }

    /** Caja de interacción {ancho, alto} y desplazamientos (a lo largo) de las cajas extra para clicar barcos largos. */
    static double[] caja(String modelo) {
        return switch (modelo) {
            case "pedalo" -> new double[]{1.6, 1.0};
            case "motoagua" -> new double[]{1.0, 0.9, 0.9, -0.9};
            case "barco" -> new double[]{1.8, 1.1, 1.3, -1.3};
            case "taxiboat" -> new double[]{2.1, 1.1, 1.4, -1.4};
            case "goma" -> new double[]{2.0, 1.0, 1.4, -1.4};
            case "patrullero" -> new double[]{2.0, 1.4, 1.4, -1.4};
            case "hipersonica" -> new double[]{1.6, 0.9, 1.6, -1.6};
            default -> new double[]{1.6, 1.1};
        };
    }

    /** Asientos de los pasajeros {lateral, frente, alto} (el conductor va en el centro). */
    static double[][] asientos(String modelo) {
        return switch (modelo) {
            case "motoagua" -> new double[][]{{0, -0.7, 0.19}};
            case "barco" -> new double[][]{{-0.45, -1.3, 0}, {0.45, -1.3, 0}, {0, 1.2, 0}};
            case "taxiboat" -> new double[][]{{-0.72, -1.5, 0}, {0.72, -1.5, 0}, {-0.72, -0.7, 0}, {0.72, -0.7, 0},
                    {-0.72, 0.1, 0}, {0.72, 0.1, 0}, {0, -1.8, 0}};
            case "goma" -> new double[][]{{0, -0.9, 0}, {-0.4, 0.9, 0}, {0.4, 0.9, 0}};
            case "patrullero" -> new double[][]{{-0.5, -1.2, 0}, {0.5, -1.2, 0}, {0.45, 0.9, 0}};
            case "hipersonica" -> new double[][]{{0.4, 0, 0}};
            case "taxi" -> new double[][]{{-0.33, -0.8, 0}, {0.33, -0.8, 0}};
            default -> new double[0][];
        };
    }

    // ---------- coche Taxi ----------

    /** El Taxi es un Turismo blanco con franja diagonal amarilla y el cartel TAXI en el techo. */
    static List<Modelos.Pieza> piezasTaxi(Modelos.Aspecto a, double[] placa) {
        List<Modelos.Pieza> p = ModelosExtra.piezas("taxi", new Modelos.Aspecto("WHITE", "WHITE", a.c3(), a.extras()), placa);
        int estilo = ModelosExtra.estilo(a);
        // franja diagonal en las puertas delanteras (doble en el estilo 2)
        for (int lado = -1; lado <= 1; lado += 2) {
            if (estilo == 3) { p.add(new Modelos.Pieza(lado * 0.684, 0, 0.34, 0.012, 2.1, 0.1, "amarillo")); continue; }
            p.add(new Modelos.Pieza(lado * 0.684, 0.2, 0.26, 0.012, 0.16, 0.46, "amarillo", 35, 0, 0));
            if (estilo == 2) p.add(new Modelos.Pieza(lado * 0.684, -0.05, 0.26, 0.012, 0.1, 0.46, "amarillo", 35, 0, 0));
        }
        add(p, 0, -0.15, 1.03, 0.52, 0.2, 0.18, "negro");
        add(p, 0, -0.15, 1.07, 0.46, 0.21, 0.12, "luztaxi");
        if (estilo == 4) {
            // Clásico: techo negro y paragolpes cromados
            p.replaceAll(q -> q.c().equals("C1") && q.z() >= 0.95 ? q.color("negro") : q);
            p.replaceAll(q -> q.c().equals("gris") && Math.abs(q.y()) > 1.25 ? q.color("hierro") : q);
        }
        if (estilo == 1) {
            // anuncio en el techo, detrás del cartel
            add(p, 0, -0.55, 1.03, 0.9, 0.06, 0.04, "negro");
            add(p, 0, -0.55, 1.07, 0.86, 0.08, 0.26, "C3");
            add(p, 0, -0.55, 1.12, 0.6, 0.09, 0.14, "blanco");
        }
        return p;
    }
}
