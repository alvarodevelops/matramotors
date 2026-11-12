package matracraft.matrains;

import org.bukkit.Material;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modelos de locomotoras y vagones hechos con bloques (BlockDisplay), sin paquete de recursos.
 *
 * Pieza: lateral (izquierda +), frente, alto (0 = carril), ancho, largo, alto de la caja, color y giro
 * opcional sobre su centro (rx levanta el frente, ry gira, rz alabea). Colores de la pieza:
 * C1 principal, C2 segundo color, C3 franja/detalle, C4 techo, V cristal; el resto son fijos.
 */
final class Modelos {

    private Modelos() {}

    record Pieza(double x, double y, double z, double w, double l, double h, String c, double rx, double ry, double rz) {
        Pieza(double x, double y, double z, double w, double l, double h, String c) { this(x, y, z, w, l, h, c, 0, 0, 0); }

        boolean girada() { return rx != 0 || ry != 0 || rz != 0; }

        Transformation transformacion() {
            Vector3f escala = new Vector3f((float) w, (float) h, (float) l);
            if (!girada())
                return new Transformation(new Vector3f((float) (x - w / 2), (float) z, (float) (y - l / 2)),
                        new Quaternionf(), escala, new Quaternionf());
            Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(ry))
                    .rotateX((float) Math.toRadians(-rx)).rotateZ((float) Math.toRadians(rz));
            Vector3f centro = new Vector3f((float) x, (float) (z + h / 2), (float) y);
            Vector3f medio = q.transform(new Vector3f(escala).mul(0.5f));
            return new Transformation(centro.sub(medio), q, escala, new Quaternionf());
        }
    }

    /** Aspecto (skin): nombre, colores C1-C4, cristal y variante de decoración. */
    record Aspecto(String nombre, String c1, String c2, String c3, String c4, String v, int variante) {}

    // ------------------------------------------------------------------ aspectos

    static final Aspecto[] VAPOR = {
            new Aspecto("Azul de gala", "LIGHT_BLUE", "RED", "BLUE", "BLACK", "TINTED", 0),
            new Aspecto("Verde británica", "GREEN", "BLACK", "GOLD_BLOCK", "BLACK", "TINTED", 1),
            new Aspecto("Negra minera", "BLACK", "RED", "RED", "GRAY", "TINTED", 0),
            new Aspecto("Granate y cobre", "RED", "BLACK", "WAXED_COPPER_BLOCK", "BLACK", "ORANGE", 1),
            new Aspecto("Negra y oro", "BLACK", "BLACK", "GOLD_BLOCK", "BLACK", "YELLOW", 2),
            new Aspecto("Vieja oxidada", "BROWN", "GRAY", "ORANGE", "GRAY", "BROWN", 3),
    };
    static final Aspecto[] DIESEL = {
            new Aspecto("Amarilla y plata", "YELLOW", "LIGHT_GRAY", "BLACK", "GRAY", "TINTED", 0),
            new Aspecto("Amarilla morro gris", "YELLOW", "GRAY", "RED", "GRAY", "TINTED", 1),
            new Aspecto("Roja y plata", "RED", "LIGHT_GRAY", "YELLOW", "LIGHT_GRAY", "LIGHT_BLUE", 2),
            new Aspecto("Azul y amarilla", "BLUE", "YELLOW", "YELLOW", "GRAY", "TINTED", 0),
            new Aspecto("Verde y crema", "GREEN", "WHITE", "YELLOW", "GRAY", "BROWN", 1),
            new Aspecto("Negra de obras", "BLACK", "ORANGE", "ORANGE", "GRAY", "ORANGE", 3),
    };
    static final Aspecto[] ELECTRICA = {
            new Aspecto("Roja y gris", "RED", "ORANGE", "LIGHT_GRAY", "GRAY", "TINTED", 0),
            new Aspecto("Agua azul", "WHITE", "LIGHT_BLUE", "BLUE", "LIGHT_GRAY", "LIGHT_BLUE", 1),
            new Aspecto("Gris y verde", "LIGHT_GRAY", "GREEN", "LIME", "GRAY", "GREEN", 0),
            new Aspecto("Azul noche", "BLUE", "YELLOW", "YELLOW", "GRAY", "TINTED", 2),
            new Aspecto("Cercanías", "WHITE", "RED", "RED", "LIGHT_GRAY", "GRAY", 1),
            new Aspecto("Mercancías", "ORANGE", "BLACK", "BLACK", "GRAY", "TINTED", 3),
    };
    static final Aspecto[] AVE = {
            new Aspecto("Blanca y morada", "WHITE", "PURPLE", "GRAY", "WHITE", "TINTED", 0),
            new Aspecto("Blanca y roja", "WHITE", "RED", "GRAY", "WHITE", "GRAY", 1),
            new Aspecto("Plata y azul", "LIGHT_GRAY", "BLUE", "WHITE", "LIGHT_GRAY", "LIGHT_BLUE", 0),
            new Aspecto("Morro negro", "WHITE", "BLACK", "LIGHT_BLUE", "WHITE", "BLUE", 2),
            new Aspecto("Matraca dorada", "WHITE", "GOLD_BLOCK", "BLACK", "WHITE", "YELLOW", 1),
            new Aspecto("Grafito", "GRAY", "CYAN", "BLACK", "GRAY", "CYAN", 3),
    };
    static final Aspecto[] MAGLEV = {
            new Aspecto("Verde y rosa", "CYAN", "WHITE", "PINK", "CYAN", "TINTED", 0),
            new Aspecto("Blanca y azul", "WHITE", "BLUE", "BLUE", "WHITE", "BLUE", 1),
            new Aspecto("Morro azul", "WHITE", "LIGHT_BLUE", "GRAY", "WHITE", "ORANGE", 2),
            new Aspecto("Negra y cian", "BLACK", "CYAN", "CYAN", "BLACK", "CYAN", 1),
            new Aspecto("Plata y roja", "LIGHT_GRAY", "RED", "RED", "LIGHT_GRAY", "TINTED", 0),
            new Aspecto("Blanca y violeta", "WHITE", "PURPLE", "MAGENTA", "WHITE", "PURPLE", 3),
    };
    // vagones de mercancías
    static final Aspecto[] ABIERTO = {
            new Aspecto("Marrón", "BROWN", "BLACK", "WHITE", "BROWN", "TINTED", 0),
            new Aspecto("Negro", "BLACK", "GRAY", "YELLOW", "BLACK", "TINTED", 0),
            new Aspecto("Verde", "GREEN", "BLACK", "WHITE", "GREEN", "TINTED", 1),
    };
    static final Aspecto[] CERRADO = {
            new Aspecto("Marrón", "BROWN", "LIGHT_GRAY", "WHITE", "LIGHT_GRAY", "TINTED", 0),
            new Aspecto("Rojo óxido", "RED", "GRAY", "WHITE", "GRAY", "TINTED", 1),
            new Aspecto("Gris", "LIGHT_GRAY", "GRAY", "BLUE", "GRAY", "TINTED", 0),
            new Aspecto("Azul de reparto", "BLUE", "WHITE", "YELLOW", "WHITE", "TINTED", 2),
    };
    static final Aspecto[] TOLVA = {
            new Aspecto("Cervezas Matraca", "RED", "WHITE", "YELLOW", "LIGHT_GRAY", "TINTED", 1),
            new Aspecto("Cementos del Faro", "LIGHT_GRAY", "GRAY", "BLUE", "LIGHT_GRAY", "TINTED", 2),
            new Aspecto("Cereales Villarroble", "YELLOW", "BROWN", "GREEN", "YELLOW", "TINTED", 1),
            new Aspecto("Granate", "RED", "BLACK", "BLACK", "RED", "TINTED", 0),
    };
    static final Aspecto[] PLATAFORMA = {
            new Aspecto("Gris", "LIGHT_GRAY", "BLACK", "YELLOW", "GRAY", "TINTED", 0),
            new Aspecto("Negra", "BLACK", "GRAY", "WHITE", "GRAY", "TINTED", 0),
            new Aspecto("Roja", "RED", "BLACK", "WHITE", "GRAY", "TINTED", 1),
    };
    static final Aspecto[] CISTERNA = {
            new Aspecto("Petróleos del Faro", "LIGHT_GRAY", "BLUE", "RED", "GRAY", "TINTED", 1),
            new Aspecto("Blanca", "WHITE", "RED", "RED", "GRAY", "TINTED", 0),
            new Aspecto("Negra", "BLACK", "YELLOW", "YELLOW", "GRAY", "TINTED", 2),
    };
    static final Aspecto[] FRIGORIFICO = {
            new Aspecto("Blanco", "WHITE", "LIGHT_GRAY", "BLUE", "WHITE", "TINTED", 0),
            new Aspecto("Crema", "YELLOW_TERRACOTTA", "WHITE", "BROWN", "WHITE", "TINTED", 1),
            new Aspecto("Azul hielo", "LIGHT_BLUE", "WHITE", "WHITE", "WHITE", "TINTED", 0),
    };
    static final Aspecto[] GARITA = {
            new Aspecto("Verde", "GREEN", "YELLOW", "BLACK", "LIGHT_GRAY", "TINTED", 0),
            new Aspecto("Roja", "RED", "YELLOW", "WHITE", "LIGHT_GRAY", "TINTED", 0),
            new Aspecto("Marrón", "BROWN", "WHITE", "BLACK", "GRAY", "TINTED", 1),
    };
    // vagones de pasajeros
    static final Aspecto[] BASICO = {
            new Aspecto("Madera barnizada", "SPRUCE_PLANKS", "DARK_OAK_PLANKS", "GOLD_BLOCK", "GRAY", "TINTED", 0),
            new Aspecto("Verde y crema", "GREEN", "YELLOW_TERRACOTTA", "YELLOW", "GRAY", "TINTED", 1),
            new Aspecto("Granate", "RED", "BLACK", "YELLOW", "GRAY", "TINTED", 0),
    };
    static final Aspecto[] CERCANIAS = {
            new Aspecto("Blanco y verde", "WHITE", "GREEN", "LIME", "LIGHT_GRAY", "GRAY", 0),
            new Aspecto("Blanco y rojo", "WHITE", "RED", "RED", "LIGHT_GRAY", "GRAY", 1),
            new Aspecto("Azul y amarillo", "BLUE", "YELLOW", "YELLOW", "LIGHT_GRAY", "LIGHT_BLUE", 0),
    };
    static final Aspecto[] PREMIUM = {
            new Aspecto("Azul noche", "BLUE", "GOLD_BLOCK", "GOLD_BLOCK", "GRAY", "TINTED", 0),
            new Aspecto("Granate y crema", "RED", "YELLOW_TERRACOTTA", "GOLD_BLOCK", "GRAY", "BROWN", 1),
            new Aspecto("Plata y morada", "WHITE", "PURPLE", "GRAY", "LIGHT_GRAY", "TINTED", 2),
    };
    static final Aspecto[] RESTAURANTE = {
            new Aspecto("Granate y oro", "RED", "GOLD_BLOCK", "GOLD_BLOCK", "GRAY", "ORANGE", 0),
            new Aspecto("Azul y crema", "BLUE", "YELLOW_TERRACOTTA", "WHITE", "GRAY", "YELLOW", 1),
    };
    static final Aspecto[] REGALO = {
            new Aspecto("Pullman azul", "BLUE", "YELLOW_TERRACOTTA", "GOLD_BLOCK", "GRAY", "ORANGE", 0),
            new Aspecto("Negro y oro", "BLACK", "GOLD_BLOCK", "GOLD_BLOCK", "GRAY", "YELLOW", 1),
    };
    static final Aspecto[] MIRADOR = {
            new Aspecto("Blanco y cian", "WHITE", "CYAN", "CYAN", "WHITE", "LIGHT_BLUE", 0),
            new Aspecto("Plata y azul", "LIGHT_GRAY", "BLUE", "BLUE", "LIGHT_GRAY", "BLUE", 1),
    };

    static Aspecto[] aspectos(String tipo) {
        return switch (tipo) {
            case "vapor" -> VAPOR; case "diesel" -> DIESEL; case "electrica" -> ELECTRICA; case "ave" -> AVE; case "maglev" -> MAGLEV;
            case "abierto" -> ABIERTO; case "cerrado" -> CERRADO; case "tolva" -> TOLVA; case "plataforma" -> PLATAFORMA;
            case "cisterna" -> CISTERNA; case "frigorifico" -> FRIGORIFICO; case "garita" -> GARITA;
            case "basico" -> BASICO; case "cercanias" -> CERCANIAS; case "premium" -> PREMIUM; case "restaurante" -> RESTAURANTE;
            case "regalo" -> REGALO; case "mirador" -> MIRADOR;
            default -> CERRADO;
        };
    }

    static Aspecto aspecto(String tipo, int i) {
        Aspecto[] a = aspectos(tipo);
        return a[Math.floorMod(i, a.length)];
    }

    /** Material de un color de pieza con el aspecto dado. */
    static Material material(String c, Aspecto a) {
        String n = switch (c) {
            case "C1" -> a.c1(); case "C2" -> a.c2(); case "C3" -> a.c3(); case "C4" -> a.c4();
            case "V" -> a.v().equals("TINTED") ? "TINTED_GLASS" : a.v() + "_STAINED_GLASS";
            case "negro" -> "BLACK_CONCRETE"; case "gris" -> "GRAY_CONCRETE"; case "grisc" -> "LIGHT_GRAY_CONCRETE";
            case "hierro" -> "IRON_BLOCK"; case "faro" -> "PEARLESCENT_FROGLIGHT"; case "piloto" -> "REDSTONE_BLOCK";
            case "rojo" -> "RED_CONCRETE"; case "madera" -> "SPRUCE_PLANKS"; case "carbon" -> "COAL_BLOCK";
            case "cristal" -> "GLASS"; case "luzazul" -> "LIGHT_BLUE_STAINED_GLASS"; case "lona" -> "WHITE_WOOL";
            case "cobre" -> "WAXED_COPPER_BLOCK"; case "oro" -> "GOLD_BLOCK"; case "blanco" -> "WHITE_CONCRETE";
            case "amarillo" -> "YELLOW_CONCRETE"; case "troncos" -> "SPRUCE_LOG"; case "grano" -> "HAY_BLOCK";
            default -> c;
        };
        if (TINTES.contains(n)) n = n + "_CONCRETE";
        Material m = Material.matchMaterial(n);
        return m == null ? Material.GRAY_CONCRETE : m;
    }

    private static final java.util.Set<String> TINTES = java.util.Set.of("WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME",
            "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK");

    static boolean luz(String c) {
        return c.equals("faro") || c.equals("piloto") || c.equals("luzazul");
    }

    // ------------------------------------------------------------------ utilidades de geometría

    private static void add(List<Pieza> p, double x, double y, double z, double w, double l, double h, String c) {
        p.add(new Pieza(x, y, z, w, l, h, c));
    }

    private static void g(List<Pieza> p, double x, double y, double z, double w, double l, double h, String c, double rx, double ry, double rz) {
        p.add(new Pieza(x, y, z, w, l, h, c, rx, ry, rz));
    }

    private static void par(List<Pieza> p, double x, double y, double z, double w, double l, double h, String c) {
        add(p, x, y, z, w, l, h, c);
        add(p, -x, y, z, w, l, h, c);
    }

    private static void parG(List<Pieza> p, double x, double y, double z, double w, double l, double h, String c, double rx, double ry, double rz) {
        g(p, x, y, z, w, l, h, c, rx, ry, rz);
        g(p, -x, y, z, w, l, h, c, rx, -ry, -rz);
    }

    /** Cilindro tumbado a lo largo (sección octogonal): centro (x, y, zc), diámetro d, largo l. */
    private static void cilindro(List<Pieza> p, double x, double y, double zc, double d, double l, String c) {
        double s = d * 0.71;
        add(p, x, y, zc - d / 2 + (d - s) / 2, d, l, s, c);
        add(p, x, y, zc - d / 2, s, l, d, c);
        g(p, x, y, zc - s / 2, s, l, s, c, 0, 0, 45);
    }

    /** Rueda de perfil (en el lateral): centro (y, zc), diámetro d. */
    private static void rueda(List<Pieza> p, double x, double y, double d, double grosor, String c) {
        double s = d * 0.71;
        add(p, x, y, (d - s) / 2, grosor, d, s, c);
        add(p, x, y, 0, grosor, s, d, c);
        g(p, x, y, d / 2 - s / 2, grosor, s, s, c, 45, 0, 0);
    }

    /** Bogie de dos ejes (ruedas a los dos lados, bastidor negro). */
    private static void bogie(List<Pieza> p, double y, double d, String llanta) {
        for (int lado = -1; lado <= 1; lado += 2) {
            rueda(p, lado * 0.55, y + d * 0.6, d, 0.08, "negro");
            rueda(p, lado * 0.55, y - d * 0.6, d, 0.08, "negro");
            add(p, lado * 0.6, y + d * 0.6, d * 0.35, 0.02, d * 0.3, d * 0.3, llanta);
            add(p, lado * 0.6, y - d * 0.6, d * 0.35, 0.02, d * 0.3, d * 0.3, llanta);
            add(p, lado * 0.6, y, d * 0.45, 0.06, d * 2.3, 0.1, "negro");
        }
    }

    /** Topes (buffers) y enganche en un extremo (sentido +1 delante, -1 detrás). */
    private static void topes(List<Pieza> p, double extremo, int sentido, double z) {
        par(p, 0.42, extremo + sentido * 0.08, z, 0.14, 0.16, 0.14, "hierro");
        add(p, 0, extremo + sentido * 0.06, z - 0.02, 0.14, 0.12, 0.08, "negro");
    }

    // ------------------------------------------------------------------ modelos

    static List<Pieza> piezas(String tipo, int aspecto) {
        Aspecto a = aspecto(tipo, aspecto);
        int v = a.variante();
        List<Pieza> p = new ArrayList<>();
        switch (tipo) {
            case "vapor" -> vapor(p, v);
            case "diesel" -> diesel(p, v);
            case "electrica" -> electrica(p, v);
            case "ave" -> ave(p, v);
            case "maglev" -> maglev(p, v);
            case "abierto" -> abierto(p, v);
            case "tolva" -> tolva(p, v);
            case "plataforma" -> plataforma(p, v);
            case "cisterna" -> cisterna(p, v);
            case "frigorifico" -> cerrado(p, v, true);
            case "garita" -> garita(p, v);
            case "basico" -> basico(p, v);
            case "cercanias" -> cercanias(p, v);
            case "premium", "restaurante", "regalo" -> lujo(p, v, tipo);
            case "mirador" -> mirador(p, v);
            default -> cerrado(p, v, false);
        }
        return p;
    }

    /** Largo de cada tipo (de tope a tope). */
    static double largo(String tipo) {
        return switch (tipo) {
            case "vapor" -> 5.2; case "diesel" -> 4.6; case "electrica" -> 4.4; case "ave" -> 5.0; case "maglev" -> 5.2;
            case "garita" -> 2.8; case "basico" -> 3.8; case "cercanias", "premium", "restaurante", "regalo", "mirador" -> 4.2;
            default -> 3.6;
        };
    }

    private static void vapor(List<Pieza> p, int v) {
        // bastidor, estribo y faldón de color
        add(p, 0, 0.35, 0.42, 1.3, 4.3, 0.1, "negro");
        par(p, 0.655, 0.6, 0.38, 0.02, 3.7, 0.1, "C2");
        // caldera, caja de humos y chimenea
        cilindro(p, 0, 0.75, 1.0, 0.92, 1.7, "C1");
        cilindro(p, 0, 1.95, 1.0, 0.96, 0.75, "negro");
        add(p, 0, 2.34, 0.6, 0.7, 0.04, 0.75, "negro");
        add(p, 0, 2.36, 0.85, 0.22, 0.02, 0.22, "gris");
        cilindro(p, 0, 2.0, 1.55, 0.26, 0.26, "negro");
        add(p, 0, 2.0, 1.45, 0.26, 0.26, 0.5, "negro");
        add(p, 0, 2.0, 1.92, 0.34, 0.34, 0.06, "negro");
        // cúpula, silbato y aros de la caldera
        add(p, 0, 1.0, 1.4, 0.36, 0.42, 0.22, "C1");
        add(p, 0, 1.0, 1.6, 0.26, 0.32, 0.06, v == 1 || v == 2 ? "C3" : "C1");
        add(p, 0, 0.35, 1.45, 0.08, 0.08, 0.18, "oro");
        for (double y : new double[]{0.15, 0.95, 1.55}) add(p, 0, y, 0.52, 0.96, 0.03, 0.96, v == 3 ? "gris" : "C3");
        // cabina
        add(p, 0, -0.55, 0.52, 1.3, 0.9, 1.3, "C1");
        par(p, 0.656, -0.5, 1.15, 0.02, 0.42, 0.42, "V");
        add(p, 0, -0.1, 1.2, 0.9, 0.02, 0.4, "V");
        g(p, 0, -0.55, 1.83, 1.45, 1.0, 0.07, "C4", 0, 0, 0);
        add(p, 0, -0.55, 1.89, 1.2, 0.8, 0.05, "C4");
        par(p, 0.657, -0.55, 0.6, 0.02, 0.86, 0.06, "C3");
        // cilindros, bielas y ruedas motrices grandes
        par(p, 0.6, 1.95, 0.45, 0.24, 0.55, 0.3, "C1");
        for (int lado = -1; lado <= 1; lado += 2) {
            rueda(p, lado * 0.58, 0.95, 0.82, 0.1, "C3".equals("C3") && v == 3 ? "negro" : "C3");
            rueda(p, lado * 0.58, 0.05, 0.82, 0.1, v == 3 ? "negro" : "C3");
            rueda(p, lado * 0.55, 1.95, 0.45, 0.08, "negro");
            add(p, lado * 0.66, 0.5, 0.36, 0.04, 1.2, 0.06, "hierro");
            add(p, lado * 0.64, 0.95, 0.36, 0.03, 0.08, 0.08, "hierro");
            add(p, lado * 0.64, 0.05, 0.36, 0.03, 0.08, 0.08, "hierro");
        }
        // viga delantera, topes y faro
        add(p, 0, 2.5, 0.22, 1.3, 0.12, 0.3, "C2");
        topes(p, 2.56, 1, 0.3);
        add(p, 0.4, 2.42, 0.55, 0.14, 0.1, 0.14, "faro");
        add(p, -0.4, 2.42, 0.55, 0.14, 0.1, 0.14, "faro");
        // ténder con carbón
        add(p, 0, -1.85, 0.42, 1.3, 1.5, 0.08, "negro");
        add(p, 0, -1.85, 0.5, 1.3, 1.5, 0.85, "C1");
        par(p, 0.656, -1.85, 0.55, 0.02, 1.46, 0.07, "C3");
        add(p, 0, -1.8, 1.25, 1.12, 1.3, 0.16, "carbon");
        add(p, 0, -1.8, 1.38, 0.8, 0.9, 0.08, "carbon");
        for (int lado = -1; lado <= 1; lado += 2)
            for (double y : new double[]{-1.35, -1.85, -2.35}) rueda(p, lado * 0.55, y, 0.42, 0.08, "negro");
        topes(p, -2.6, -1, 0.3);
        add(p, 0.4, -2.62, 0.62, 0.1, 0.03, 0.1, "piloto");
        add(p, -0.4, -2.62, 0.62, 0.1, 0.03, 0.1, "piloto");
        if (v == 3) { add(p, 0.2, 0.6, 1.47, 0.3, 0.4, 0.02, "gris"); add(p, -0.35, -1.6, 0.9, 0.62, 0.3, 0.02, "C3"); }
    }

    private static void diesel(List<Pieza> p, int v) {
        add(p, 0, 0, 0.42, 1.3, 4.5, 0.12, "negro");
        // capó largo
        add(p, 0, -0.55, 0.54, 1.12, 3.3, 1.18, "C1");
        add(p, 0, -0.55, 1.72, 0.9, 3.2, 0.08, "C4");
        par(p, 0.565, -0.9, 0.95, 0.02, 0.5, 0.35, "gris");
        par(p, 0.565, 0.2, 1.1, 0.02, 0.4, 0.25, "gris");
        add(p, 0, -0.9, 1.8, 0.3, 0.6, 0.12, "negro");
        // cabina y morro
        add(p, 0, 1.35, 0.54, 1.3, 0.7, 1.45, "C1");
        add(p, 0, 1.35, 1.99, 1.2, 0.66, 0.08, "C4");
        g(p, 0, 1.72, 1.45, 1.16, 0.05, 0.42, "V", 20, 0, 0);
        par(p, 0.656, 1.4, 1.42, 0.02, 0.4, 0.38, "V");
        add(p, 0, 1.95, 0.54, 1.2, 0.5, 0.75, v == 1 ? "C2" : "C1");
        g(p, 0, 2.05, 1.18, 1.18, 0.4, 0.06, v == 1 ? "C2" : "C1", -18, 0, 0);
        add(p, 0, 2.21, 0.95, 0.2, 0.03, 0.12, "faro");
        par(p, 0.38, 2.21, 0.62, 0.12, 0.03, 0.1, "faro");
        // franjas
        par(p, 0.567, -0.4, 0.58, 0.012, 3.6, 0.22, "C2");
        par(p, 0.568, -0.4, 0.8, 0.012, 3.6, 0.05, "C3");
        if (v == 2) { par(p, 0.657, 1.9, 0.9, 0.012, 0.6, 0.3, "C2"); add(p, 0, 2.21, 0.75, 0.9, 0.012, 0.15, "C3"); }
        if (v == 3) for (double y = -2.0; y < 1.8; y += 0.5) par(p, 0.568, y, 0.6, 0.012, 0.18, 0.4, "C2");
        // barandillas y faldón delantero
        par(p, 0.62, -0.55, 1.0, 0.03, 3.3, 0.03, "hierro");
        g(p, 0, 2.28, 0.18, 1.2, 0.18, 0.3, v == 0 ? "C3" : "negro", 25, 0, 0);
        // depósito y bogies
        add(p, 0, -0.35, 0.18, 0.9, 1.3, 0.24, "gris");
        bogie(p, 1.4, 0.42, "gris");
        bogie(p, -1.55, 0.42, "gris");
        topes(p, 2.3, 1, 0.28);
        topes(p, -2.3, -1, 0.28);
        par(p, 0.4, -2.22, 0.95, 0.1, 0.03, 0.1, "piloto");
    }

    private static void electrica(List<Pieza> p, int v) {
        add(p, 0, 0, 0.42, 1.3, 4.3, 0.12, "negro");
        add(p, 0, 0, 0.54, 1.3, 3.5, 1.45, "C1");
        add(p, 0, 0, 1.99, 1.16, 3.6, 0.1, "C4");
        // cabinas en los dos extremos con frente inclinado
        for (int s = -1; s <= 1; s += 2) {
            add(p, 0, s * 1.92, 0.54, 1.28, 0.34, 0.95, v == 0 || v == 3 ? "C2" : "C1");
            g(p, 0, s * 2.02, 1.42, 1.22, 0.06, 0.55, "V", s * 22, 0, 0);
            g(p, 0, s * 1.92, 1.82, 1.22, 0.3, 0.12, "C4", s * -14, 0, 0);
            par(p, 0.4, s * 2.1, 0.7, 0.16, 0.03, 0.12, s > 0 ? "faro" : "piloto");
            add(p, 0, s * 2.12, 0.42, 1.2, 0.12, 0.14, "C3");
            par(p, 0.656, s * 1.45, 1.3, 0.02, 0.38, 0.42, "V");
            par(p, 0.657, s * 1.15, 0.7, 0.012, 0.06, 1.1, "negro");
        }
        // ventanillas de la sala de máquinas y franjas
        for (double y = -0.8; y <= 0.81; y += 0.4) par(p, 0.656, y, 1.55, 0.02, 0.22, 0.2, "V");
        par(p, 0.657, 0, 0.62, 0.012, 3.4, 0.22, "C3");
        if (v == 1) par(p, 0.658, 0, 0.9, 0.012, 3.4, 0.38, "C2");
        if (v == 2) par(p, 0.658, 0.3, 1.05, 0.012, 2.4, 0.08, "C2");
        if (v == 3) for (double y = -1.6; y <= 1.61; y += 0.4) par(p, 0.658, y, 0.95, 0.012, 0.16, 0.16, "C2");
        // pantógrafo
        add(p, 0, -0.6, 2.09, 0.5, 0.5, 0.06, "gris");
        g(p, 0, -0.45, 2.3, 0.05, 0.6, 0.05, "hierro", 40, 0, 0);
        g(p, 0, -0.2, 2.5, 0.05, 0.6, 0.05, "hierro", -40, 0, 0);
        add(p, 0, -0.05, 2.7, 0.9, 0.08, 0.04, "hierro");
        bogie(p, 1.3, 0.44, "gris");
        bogie(p, -1.3, 0.44, "gris");
        topes(p, 2.18, 1, 0.3);
        topes(p, -2.18, -1, 0.3);
    }

    private static void ave(List<Pieza> p, int v) {
        // cuerpo
        add(p, 0, -1.0, 0.36, 1.3, 3.0, 1.45, "C1");
        add(p, 0, -1.0, 1.81, 1.1, 3.0, 0.12, "C4");
        parG(p, 0.6, -1.0, 1.72, 0.14, 3.0, 0.14, "C4", 0, 0, -40);
        // morro largo y bajo
        add(p, 0, 0.9, 0.36, 1.3, 0.8, 1.15, "C1");
        g(p, 0, 0.95, 1.55, 1.22, 0.95, 0.12, "C1", -24, 0, 0);
        g(p, 0, 0.9, 1.42, 1.0, 0.7, 0.06, "V", -24, 0, 0);
        add(p, 0, 1.6, 0.36, 1.26, 0.6, 0.9, "C1");
        g(p, 0, 1.75, 1.08, 1.2, 0.7, 0.1, v == 2 ? "C2" : "C1", -18, 0, 0);
        add(p, 0, 2.12, 0.36, 1.1, 0.46, 0.65, v == 2 ? "C2" : "C1");
        g(p, 0, 2.28, 0.9, 1.0, 0.45, 0.1, v == 2 ? "C2" : "C1", -32, 0, 0);
        add(p, 0, 2.42, 0.36, 0.8, 0.18, 0.45, v == 2 ? "C2" : "C1");
        parG(p, 0.42, 2.1, 0.75, 0.3, 0.06, 0.12, "faro", 0, -20, 0);
        // franja que sube por el morro
        par(p, 0.656, -1.0, 0.75, 0.012, 3.0, 0.14, "C2");
        parG(p, 0.636, 1.0, 0.86, 0.012, 1.0, 0.14, "C2", 12, 0, 0);
        if (v == 1 || v == 3) par(p, 0.657, -1.0, 0.55, 0.012, 3.0, 0.05, "C3");
        // ventanillas, puerta y faldón
        for (double y = -2.2; y <= 0.2; y += 0.5) par(p, 0.656, y, 1.15, 0.02, 0.36, 0.3, "V");
        par(p, 0.657, 0.55, 0.6, 0.012, 0.3, 0.95, v == 3 ? "C3" : "grisc");
        add(p, 0, -0.2, 0.18, 1.2, 4.6, 0.18, "C3");
        bogie(p, 1.5, 0.38, "gris");
        bogie(p, -1.9, 0.38, "gris");
        // pantógrafo pequeño
        g(p, 0, -1.8, 2.05, 0.04, 0.45, 0.04, "hierro", 35, 0, 0);
        add(p, 0, -1.65, 2.18, 0.7, 0.06, 0.03, "hierro");
        par(p, 0.35, -2.52, 0.9, 0.1, 0.03, 0.1, "piloto");
    }

    private static void maglev(List<Pieza> p, int v) {
        add(p, 0, -1.2, 0.3, 1.3, 2.8, 1.45, "C1");
        add(p, 0, -1.2, 1.75, 1.0, 2.8, 0.14, "C4");
        parG(p, 0.58, -1.2, 1.66, 0.16, 2.8, 0.16, "C4", 0, 0, -38);
        // morro muy largo y redondeado
        add(p, 0, 0.6, 0.3, 1.3, 0.8, 1.25, v == 2 ? "C2" : "C1");
        g(p, 0, 0.75, 1.5, 1.1, 0.9, 0.14, v == 2 ? "C2" : "C1", -18, 0, 0);
        g(p, 0, 0.72, 1.4, 0.8, 0.6, 0.06, "V", -18, 0, 0);
        add(p, 0, 1.4, 0.3, 1.24, 0.8, 0.95, v == 2 ? "C2" : "C1");
        g(p, 0, 1.55, 1.12, 1.14, 0.95, 0.12, v == 2 ? "C2" : "C1", -20, 0, 0);
        add(p, 0, 2.1, 0.3, 1.04, 0.6, 0.65, v == 2 ? "C2" : "C1");
        g(p, 0, 2.25, 0.82, 0.92, 0.6, 0.1, v == 2 ? "C2" : "C1", -30, 0, 0);
        add(p, 0, 2.5, 0.3, 0.7, 0.2, 0.4, v == 2 ? "C2" : "C1");
        parG(p, 0.36, 2.3, 0.6, 0.26, 0.06, 0.1, "faro", 0, -24, 0);
        // franjas barridas
        par(p, 0.656, -1.2, 0.95, 0.012, 2.8, 0.12, "C2");
        parG(p, 0.62, 0.9, 0.95, 0.012, 1.7, 0.1, "C2", -6, 0, 0);
        par(p, 0.657, -1.2, 0.72, 0.012, 2.8, 0.05, "C3");
        if (v == 1 || v == 3) parG(p, 0.6, 0.5, 1.25, 0.012, 1.2, 0.08, "C3", 10, 0, 0);
        for (double y = -2.3; y <= -0.3; y += 0.5) par(p, 0.656, y, 1.15, 0.02, 0.3, 0.26, "V");
        // faldón cerrado hasta abajo y brillo de levitación (sin ruedas a la vista)
        add(p, 0, -0.2, 0.05, 1.3, 4.6, 0.25, v == 0 ? "C2" : "C1");
        add(p, 0, -0.2, 0.0, 1.0, 4.4, 0.05, "luzazul");
        par(p, 0.3, -2.62, 0.8, 0.1, 0.03, 0.1, "piloto");
    }

    // ---------- mercancías (largo 3.6, plataforma a 0.45)

    private static void base(List<Pieza> p, double largo) {
        add(p, 0, 0, 0.36, 1.3, largo - 0.2, 0.1, "negro");
        bogie(p, largo / 2 - 0.75, 0.38, "gris");
        bogie(p, -largo / 2 + 0.75, 0.38, "gris");
        topes(p, largo / 2 - 0.1, 1, 0.3);
        topes(p, -largo / 2 + 0.1, -1, 0.3);
    }

    private static void abierto(List<Pieza> p, int v) {
        base(p, 3.6);
        add(p, 0, 0, 0.46, 1.3, 3.4, 0.06, "C1");
        par(p, 0.62, 0, 0.52, 0.06, 3.4, 0.85, "C1");
        add(p, 0, 1.67, 0.52, 1.3, 0.06, 0.85, "C1");
        add(p, 0, -1.67, 0.52, 1.3, 0.06, 0.85, "C1");
        for (double y = -1.35; y <= 1.36; y += 0.9) par(p, 0.656, y, 0.52, 0.03, 0.08, 0.85, "C2");
        par(p, 0.657, 0, 0.62, 0.012, 0.5, 0.18, "C3");
        if (v == 1) for (int s = -1; s <= 1; s += 2) parG(p, 0.656, s * 0.45, 0.6, 0.02, 0.06, 0.8, "C2", s * 40, 0, 0);
    }

    private static void cerrado(List<Pieza> p, int v, boolean frio) {
        base(p, 3.6);
        add(p, 0, 0, 0.46, 1.3, 3.4, 1.25, "C1");
        // techo curvo
        add(p, 0, 0, 1.71, 1.2, 3.5, 0.1, "C4");
        parG(p, 0.6, 0, 1.64, 0.18, 3.5, 0.1, "C4", 0, 0, -28);
        // puertas correderas y nervios
        par(p, 0.656, 0, 0.5, 0.02, 0.9, 1.15, "C2");
        par(p, 0.66, 0, 1.05, 0.01, 0.04, 0.04, "negro");
        for (double y = -1.4; y <= 1.41; y += 0.7) if (Math.abs(y) > 0.3) par(p, 0.655, y, 0.46, 0.02, 0.06, 1.25, frio ? "C2" : "negro");
        par(p, 0.657, 1.2, 1.35, 0.012, 0.3, 0.2, "C3");
        if (frio) {
            for (double y = -1.2; y <= 1.21; y += 0.6) add(p, 0, y, 1.8, 0.14, 0.14, 0.1, "grisc");
            par(p, 0.657, -1.2, 0.8, 0.012, 0.6, 0.45, "C3");
        }
        if (v == 1) par(p, 0.657, 0, 0.9, 0.012, 3.3, 0.06, "C3");
        if (v == 2) { par(p, 0.658, -1.0, 0.75, 0.012, 1.0, 0.6, "C3"); par(p, 0.659, -1.0, 0.95, 0.01, 0.8, 0.2, "C2"); }
    }

    private static void tolva(List<Pieza> p, int v) {
        base(p, 3.6);
        // cuerpo trapezoidal con tolvas abajo
        add(p, 0, 0, 0.75, 1.3, 3.3, 1.0, "C1");
        for (int s = -1; s <= 1; s += 2) g(p, 0, s * 1.0, 0.5, 1.1, 0.8, 0.35, "C1", s * 30, 0, 0);
        add(p, 0, 0, 1.75, 1.15, 3.2, 0.08, "C4");
        add(p, 0, 0, 1.83, 0.5, 2.8, 0.06, "gris");
        for (double y = -1.0; y <= 1.01; y += 1.0) add(p, 0, y, 1.89, 0.3, 0.3, 0.05, "grisc");
        // rótulo
        par(p, 0.656, 0.1, 1.0, 0.012, 2.2, 0.5, v == 0 ? "C1" : "C2");
        if (v == 1) for (double y = -0.6; y <= 0.81; y += 0.35) par(p, 0.66, y, 1.12, 0.012, 0.18, 0.18, "C3");
        if (v == 2) par(p, 0.66, 0.1, 1.15, 0.012, 1.8, 0.1, "C3");
        // escalerilla y barandilla
        par(p, 0.6, 1.62, 0.6, 0.04, 0.03, 1.25, "hierro");
        add(p, 0, 1.68, 1.95, 1.0, 0.03, 0.03, "hierro");
    }

    private static void plataforma(List<Pieza> p, int v) {
        base(p, 3.6);
        add(p, 0, 0, 0.46, 1.3, 3.4, 0.08, "C1");
        for (double y = -1.5; y <= 1.51; y += 0.75)
            par(p, 0.62, y, 0.54, 0.06, 0.06, 0.9, "C2");
        par(p, 0.656, 0, 0.47, 0.012, 3.3, 0.05, "C3");
        if (v == 1) add(p, 0, 0, 0.54, 1.1, 3.2, 0.4, "troncos");
    }

    private static void cisterna(List<Pieza> p, int v) {
        base(p, 3.6);
        add(p, 0, 0, 0.46, 1.1, 3.2, 0.12, "negro");
        cilindro(p, 0, 0, 1.15, 1.2, 3.3, "C1");
        // tapas redondeadas
        for (int s = -1; s <= 1; s += 2) cilindro(p, 0, s * 1.7, 1.15, 0.95, 0.12, "C1");
        // franja y logotipo
        par(p, 0.606, 0, 1.12, 0.012, 3.2, 0.1, "C2");
        if (v == 1) { par(p, 0.61, -0.9, 1.0, 0.012, 0.4, 0.4, "blanco"); par(p, 0.615, -0.9, 1.08, 0.012, 0.26, 0.26, "C3"); }
        if (v == 2) par(p, 0.607, 0, 1.3, 0.012, 3.2, 0.05, "C3");
        // boca de carga, pasarela y escalerilla
        add(p, 0, 0, 1.75, 0.4, 0.4, 0.14, "gris");
        add(p, 0, 0, 1.78, 1.0, 1.2, 0.03, "hierro");
        par(p, 0.6, 1.5, 0.55, 0.04, 0.03, 1.1, "hierro");
    }

    private static void garita(List<Pieza> p, int v) {
        base(p, 2.8);
        add(p, 0, 0, 0.46, 1.3, 2.6, 0.06, "C3");
        // caseta con techo curvo
        add(p, 0, -0.3, 0.52, 1.0, 1.4, 1.2, "C1");
        add(p, 0, -0.3, 1.72, 1.1, 1.5, 0.08, "C4");
        parG(p, 0.5, -0.3, 1.66, 0.14, 1.5, 0.08, "C4", 0, 0, -30);
        par(p, 0.506, -0.3, 1.1, 0.02, 0.5, 0.4, "V");
        add(p, 0, 0.41, 1.05, 0.5, 0.02, 0.45, "V");
        add(p, 0.35, 0.41, 0.55, 0.3, 0.02, 0.9, "C1");
        // barandillas amarillas del balconcillo
        par(p, 0.6, 0.85, 0.52, 0.04, 0.04, 0.7, "C2");
        par(p, 0.6, 0.85, 1.2, 0.04, 0.8, 0.04, "C2");
        add(p, 0, 1.25, 1.2, 1.2, 0.04, 0.04, "C2");
        add(p, 0.25, -0.2, 1.8, 0.12, 0.12, 0.18, "gris");
        add(p, -0.25, -0.2, 1.8, 0.12, 0.12, 0.18, "gris");
        par(p, 0.4, -1.42, 0.7, 0.1, 0.03, 0.1, "piloto");
    }

    // ---------- pasajeros

    private static void caja(List<Pieza> p, double largo, double alto, boolean ventanas, double paso) {
        add(p, 0, 0, 0.46, 1.3, largo - 0.2, alto, "C1");
        add(p, 0, 0, 0.46 + alto, 1.18, largo - 0.1, 0.1, "C4");
        parG(p, 0.6, 0, 0.42 + alto, 0.16, largo - 0.1, 0.1, "C4", 0, 0, -30);
        if (ventanas) for (double y = -largo / 2 + 0.55; y <= largo / 2 - 0.5; y += paso)
            par(p, 0.656, y, 0.46 + alto * 0.5, 0.02, paso * 0.62, alto * 0.32, "V");
    }

    private static void basico(List<Pieza> p, int v) {
        base(p, 3.8);
        caja(p, 3.8, 1.15, true, 0.6);
        par(p, 0.657, 0, 0.6, 0.012, 3.5, 0.05, "C3");
        par(p, 0.657, 0, 1.45, 0.012, 3.5, 0.04, "C3");
        if (v == 1) par(p, 0.656, 0, 0.47, 0.012, 3.55, 0.3, "C2");
        // plataformas abiertas con barandilla
        for (int s = -1; s <= 1; s += 2) par(p, 0.55, s * 1.75, 0.5, 0.03, 0.03, 0.7, "hierro");
        if (v == 0) for (double y = -1.6; y <= 1.61; y += 0.4) par(p, 0.655, y, 0.5, 0.012, 0.02, 0.4, "C2");
    }

    private static void cercanias(List<Pieza> p, int v) {
        base(p, 4.2);
        caja(p, 4.2, 1.75, false, 0);
        // dos pisos de ventanas y puertas
        for (double y = -1.5; y <= 1.51; y += 0.5) if (Math.abs(y) > 0.3) {
            par(p, 0.656, y, 0.75, 0.02, 0.36, 0.3, "V");
            par(p, 0.656, y, 1.35, 0.02, 0.36, 0.32, "V");
        }
        par(p, 0.657, 0, 0.5, 0.012, 0.5, 1.4, v == 1 ? "C2" : "grisc");
        par(p, 0.66, 0, 0.9, 0.01, 0.02, 0.8, "negro");
        par(p, 0.657, 0, 1.15, 0.012, 3.9, 0.12, "C2");
        par(p, 0.657, 0, 0.55, 0.012, 3.9, 0.1, "C3");
    }

    private static void lujo(List<Pieza> p, int v, String tipo) {
        base(p, 4.2);
        caja(p, 4.2, 1.3, true, tipo.equals("restaurante") ? 0.9 : 0.55);
        par(p, 0.657, 0, 0.48, 0.012, 3.95, 0.3, "C2");
        par(p, 0.658, 0, 0.82, 0.012, 3.95, 0.03, "C3");
        par(p, 0.658, 0, 1.55, 0.012, 3.95, 0.03, "C3");
        // puertas en los extremos
        for (int s = -1; s <= 1; s += 2) par(p, 0.657, s * 1.85, 0.5, 0.012, 0.3, 1.15, "C2");
        if (tipo.equals("restaurante")) {
            par(p, 0.659, 1.2, 1.62, 0.012, 1.0, 0.1, "C3");
            add(p, 0.3, -1.2, 1.86, 0.12, 0.12, 0.3, "gris");
        }
        if (tipo.equals("regalo")) {
            par(p, 0.659, 0, 1.62, 0.012, 1.6, 0.12, "C3");
            for (double y = -1.5; y <= 1.51; y += 0.75) par(p, 0.66, y, 0.6, 0.01, 0.12, 0.12, "C3");
        }
        if (v == 2) par(p, 0.659, 0, 1.0, 0.012, 3.9, 0.06, "C2");
    }

    private static void mirador(List<Pieza> p, int v) {
        base(p, 4.2);
        add(p, 0, 0, 0.46, 1.3, 4.0, 0.7, "C1");
        // cúpula de cristal
        add(p, 0, 0, 1.16, 1.3, 3.6, 0.55, "V");
        add(p, 0, 0, 1.71, 1.0, 3.6, 0.1, "V");
        parG(p, 0.56, 0, 1.62, 0.2, 3.6, 0.1, "V", 0, 0, -35);
        for (double y = -1.6; y <= 1.61; y += 0.8) add(p, 0, y, 1.16, 1.32, 0.06, 0.6, "C1");
        for (int s = -1; s <= 1; s += 2) add(p, 0, s * 1.9, 0.46, 1.3, 0.2, 1.4, "C1");
        par(p, 0.657, 0, 0.75, 0.012, 3.9, 0.1, "C2");
        if (v == 1) par(p, 0.657, 0, 0.55, 0.012, 3.9, 0.06, "C3");
    }

    // ------------------------------------------------------------------ asientos

    /** Asientos: el primero es la base (maquinista en las locomotoras). {lateral, frente, alto}. */
    static double[][] asientos(String tipo, int plazas) {
        switch (tipo) {
            case "vapor": return new double[][]{{0, -0.55, 0.62}};
            case "diesel": return new double[][]{{0, 1.35, 0.62}};
            case "electrica": return new double[][]{{0, 1.6, 0.62}};
            case "ave": return new double[][]{{0, 0.45, 0.5}};
            case "maglev": return new double[][]{{0, 0.25, 0.45}};
            default: break;
        }
        if (plazas <= 0) return new double[][]{{0, 0, 0.6}};
        double[][] r = new double[plazas][];
        int filas = (plazas + 1) / 2;
        double largo = largo(tipo) - 1.2;
        for (int i = 0; i < plazas; i++) {
            int fila = i / 2;
            double y = filas == 1 ? 0 : largo / 2 - largo * fila / (filas - 1);
            boolean alto = tipo.equals("cercanias") && fila % 2 == 1;
            r[i] = new double[]{plazas % 2 == 1 && i == plazas - 1 ? 0 : (i % 2 == 0 ? 0.3 : -0.3), y, alto ? 1.15 : 0.62};
        }
        return r;
    }

    static String nombre(String tipo, int aspecto) {
        return aspecto(tipo, aspecto).nombre();
    }

    static String clave(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
