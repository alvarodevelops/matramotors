package matracraft.coches;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Modelos 3D de los coches hechos con BlockDisplay que siguen al minecart. */
final class Modelos implements Listener {

    /** Una pieza del modelo: posición (x, y, z), tamaño (w, l, h) y color o material (c). */
    record Pieza(double x, double y, double z, double w, double l, double h, String c, double rx, double ry, double rz) {
        Pieza(double x, double y, double z, double w, double l, double h, String c) { this(x, y, z, w, l, h, c, 0, 0, 0); }

        /** La misma pieza con otro material (conserva el giro). */
        Pieza color(String nuevo) { return new Pieza(x, y, z, w, l, h, nuevo, rx, ry, rz); }

        /**
         * Giro de la pieza sobre su propio centro, en grados: rx inclina el frente hacia arriba (+) o abajo (-),
         * ry gira hacia la izquierda (+), rz alabea (+ baja el lado derecho).
         */
        Pieza giro(double rx, double ry, double rz) { return new Pieza(x, y, z, w, l, h, c, rx, ry, rz); }

        boolean girada() { return rx != 0 || ry != 0 || rz != 0; }
    }

    /** Transformación del BlockDisplay de una pieza (con su giro alrededor del centro). */
    static Transformation transformacion(Pieza p) {
        Vector3f escala = new Vector3f((float) p.w(), (float) p.h(), (float) p.l());
        if (!p.girada())
            return new Transformation(new Vector3f((float) (p.x() - p.w() / 2), (float) p.z(), (float) (p.y() - p.l() / 2)),
                    new Quaternionf(), escala, new Quaternionf());
        Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(p.ry()))
                .rotateX((float) Math.toRadians(-p.rx())).rotateZ((float) Math.toRadians(p.rz()));
        Vector3f centro = new Vector3f((float) p.x(), (float) (p.z() + p.h() / 2), (float) p.y());
        Vector3f medio = q.transform(new Vector3f(escala).mul(0.5f));
        return new Transformation(centro.sub(medio), q, escala, new Quaternionf());
    }

    /** Colores elegidos para un coche (C1, C2, C3) y bits de extras. */
    record Aspecto(String c1, String c2, String c3, int extras) {
        String guardar() { return c1 + "," + c2 + "," + c3 + "," + extras; }

        static Aspecto leer(String texto) {
            if (texto == null) return null;
            String[] partes = texto.split(",");
            if (partes.length < 4) return null;
            try {
                return new Aspecto(partes[0], partes[1], partes[2], Integer.parseInt(partes[3]));
            } catch (NumberFormatException e) { return null; }
        }

        boolean extra(int bit) { return (extras & 1 << bit) != 0; }
    }

    static final class Vista {
        final ArmorStand cart;
        final List<Entity> piezas = new ArrayList<>();
        // piezas que no van en el centro (cajas extra para clicar barcos largos): {lateral, frente, alto}
        final Map<Entity, double[]> desplazadas = new HashMap<>();
        // asientos de los pasajeros (soportes invisibles) y su posición
        final Entity[] asientos = new Entity[8];
        double[][] sitios = new double[0][];
        Location ultima;
        float ultimoYaw;

        Vista(ArmorStand cart) { this.cart = cart; }
    }

    private final Coches pl;
    private final Map<UUID, Vista> vistas = new HashMap<>();
    private final Map<UUID, ArmorStand> cajas = new HashMap<>();

    Modelos(Coches pl) { this.pl = pl; }

    private static final Map<String, Material> FIJOS = Map.ofEntries(
            Map.entry("cristal", Material.TINTED_GLASS),
            Map.entry("faro", Material.PEARLESCENT_FROGLIGHT),
            Map.entry("piloto", Material.REDSTONE_BLOCK),
            Map.entry("gris", Material.GRAY_CONCRETE),
            Map.entry("grisc", Material.LIGHT_GRAY_CONCRETE),
            Map.entry("negro", Material.BLACK_CONCRETE),
            Map.entry("goma", Material.BLACK_CONCRETE),
            Map.entry("llanta", Material.IRON_BLOCK),
            Map.entry("hierro", Material.IRON_BLOCK),
            Map.entry("barril", Material.BARREL),
            Map.entry("lona", Material.WHITE_WOOL),
            Map.entry("lonaazul", Material.BLUE_WOOL),
            Map.entry("amarillo", Material.YELLOW_CONCRETE),
            Map.entry("azul", Material.BLUE_CONCRETE),
            Map.entry("blanco", Material.WHITE_CONCRETE),
            Map.entry("morado", Material.PURPLE_CONCRETE),
            Map.entry("naranja", Material.ORANGE_CONCRETE),
            Map.entry("luzazul", Material.LIGHT_BLUE_STAINED_GLASS),
            Map.entry("verde", Material.LIME_CONCRETE),
            Map.entry("fardo", Material.HAY_BLOCK),
            Map.entry("madera", Material.SPRUCE_PLANKS),
            Map.entry("rojo", Material.RED_CONCRETE),
            Map.entry("verdeo", Material.GREEN_CONCRETE));

    private static Material material(String c, Aspecto aspecto) {
        String color = switch (c) {
            case "C1" -> aspecto.c1();
            case "C2" -> aspecto.c2();
            case "C3" -> aspecto.c3();
            default -> null;
        };
        if (color != null) {
            Material m = Material.matchMaterial(color + "_CONCRETE");
            return m == null ? Material.WHITE_CONCRETE : m;
        }
        return FIJOS.getOrDefault(c, Material.GRAY_CONCRETE);
    }

    /** Elige al azar entre opciones "COLOR:peso". */
    private static String azar(String... opciones) {
        double total = 0;
        for (String o : opciones) total += Double.parseDouble(o.split(":")[1]);
        double tirada = ThreadLocalRandom.current().nextDouble(total);
        for (String o : opciones) {
            String[] partes = o.split(":");
            tirada -= Double.parseDouble(partes[1]);
            if (tirada < 0) return partes[0];
        }
        return opciones[0].split(":")[0];
    }

    private static int bit(double prob, int n) {
        return ThreadLocalRandom.current().nextDouble() < prob ? 1 << n : 0;
    }

    static String modelo(Tier tier) {
        if (ModelosBarcos.esBarco(tier.id())) return tier.id();
        if (tier.agua()) return "barco";
        if (tier.taxi()) return "taxi";
        return switch (tier.id()) { case "tartana", "turismo", "deportivo", "furgoneta", "quad", "motocarro" -> tier.id(); default -> "turismo"; };
    }

    static Aspecto azar(Tier tier) {
        String m = modelo(tier);
        if (ModelosBarcos.esBarco(m)) return ModelosBarcos.azar(m);
        // estilo (skin) en los bits 16-18: 0 el de siempre (el más común), 1 a 4 los demás
        int estilo = azarEstilo() << 16;
        if (m.equals("taxi")) return new Aspecto("WHITE", "WHITE", azar("BLACK:3", "GRAY:1", "LIGHT_BLUE:1", "RED:1"), bit(0.5, 0) | estilo);
        return switch (m) {
            case "tartana" -> {
                String c1 = azar("ORANGE:3", "LIME:2", "WHITE:2", "BROWN:2", "GREEN:1.5", "LIGHT_BLUE:1", "RED:1", "BLACK:0.3", "PINK:0.15");
                yield new Aspecto(c1, distinto(c1, "WHITE:4", "BLACK:2", "LIGHT_GRAY:1", "YELLOW:1"), "GRAY", bit(0.4, 0) | bit(0.5, 1) | estilo);
            }
            case "deportivo" -> {
                String c1 = azar("WHITE:3", "YELLOW:2", "BLUE:2", "RED:2", "GRAY:1.5", "ORANGE:1", "LIME:0.3", "PINK:0.2", "PURPLE:0.2");
                String c2 = c1.equals("BLACK") || c1.equals("GRAY") ? "BLACK" : azar("BLACK:4", "WHITE:1");
                if (c2.equals(c1)) c2 = "BLACK";
                String c3 = distinto(c1, "WHITE:3", "BLACK:2", "RED:2", "YELLOW:1");
                yield new Aspecto(c1, c2, c3, bit(0.85, 0) | estilo);
            }
            case "furgoneta" -> new Aspecto(azar("WHITE:5", "LIGHT_GRAY:2", "YELLOW:1.5", "GREEN:1", "LIGHT_BLUE:1", "ORANGE:0.4", "RED:0.3"),
                    azar("BLACK:3", "GRAY:2", "BLUE:1.5", "RED:1"), "GRAY", bit(0.3, 0) | estilo);
            case "motocarro" -> {
                String c1 = azar("RED:3", "WHITE:2", "BLUE:2", "GREEN:1.5", "ORANGE:1", "YELLOW:1", "LIGHT_BLUE:1", "BLACK:0.3");
                yield new Aspecto(c1, distinto(c1, "WHITE:4", "YELLOW:1", "LIGHT_GRAY:1"), "GRAY", bit(0.5, 0) | estilo);
            }
            case "quad" -> {
                String c1 = azar("LIME:3", "ORANGE:2", "LIGHT_BLUE:2", "RED:2", "YELLOW:1.5", "PINK:0.4", "PURPLE:0.3");
                yield new Aspecto(c1, azar("BLACK:4", "WHITE:1"), azar("BLACK:3", "GRAY:1", "WHITE:0.5"), estilo);
            }
            default -> {
                String c1 = azar("CYAN:3", "RED:2", "LIGHT_GRAY:2", "LIGHT_BLUE:2", "WHITE:2", "BLACK:1", "BLUE:1", "GREEN:1", "PURPLE:0.3", "MAGENTA:0.2");
                yield new Aspecto(c1, distinto(c1, "WHITE:3", "BLACK:2", "YELLOW:1", "RED:1"), "GRAY", bit(0.5, 0) | estilo);
            }
        };
    }

    private static int azarEstilo() {
        double t = ThreadLocalRandom.current().nextDouble();
        return t < 0.4 ? 0 : t < 0.58 ? 1 : t < 0.75 ? 2 : t < 0.88 ? 3 : 4;
    }

    private static String distinto(String de, String... opciones) {
        for (int i = 0; i < 20; i++) { String c = azar(opciones); if (!c.equals(de)) return c; }
        return de.equals("WHITE") ? "BLACK" : "WHITE";
    }

    /** Altura a la que va sentado el conductor (0 = la normal, la de las vagonetas de antes). */
    static double alturaAsiento(String modelo) {
        return switch (modelo) { case "quad" -> 0.62; case "motocarro" -> 0.5; default -> 0; };
    }

    /** Nombre del estilo (skin) para la descripción del objeto. */
    static String nombreEstilo(Tier tier, Aspecto a) {
        String m = modelo(tier);
        return ModelosBarcos.esBarco(m) ? ModelosBarcos.nombreEstilo(m, a) : ModelosExtra.nombreEstilo(m, a);
    }

    static String nombreColor(String color) {
        return switch (color) {
            case "WHITE" -> "Blanco"; case "ORANGE" -> "Naranja"; case "MAGENTA" -> "Magenta"; case "LIGHT_BLUE" -> "Azul cielo";
            case "YELLOW" -> "Amarillo"; case "LIME" -> "Verde lima"; case "PINK" -> "Rosa"; case "GRAY" -> "Gris";
            case "LIGHT_GRAY" -> "Gris claro"; case "CYAN" -> "Turquesa"; case "PURPLE" -> "Morado"; case "BLUE" -> "Azul";
            case "BROWN" -> "Marrón"; case "GREEN" -> "Verde"; case "RED" -> "Rojo"; case "BLACK" -> "Negro";
            default -> color.toLowerCase(Locale.ROOT);
        };
    }

    private static void ruedas(List<Pieza> lista, double ancho, double eje, double alto, double grosor, String llanta) {
        for (int i = -1; i <= 1; i += 2) {
            for (int j = -1; j <= 1; j += 2) {
                lista.add(new Pieza(i * ancho / 2, j * eje, 0, grosor, alto, alto, "goma"));
                lista.add(new Pieza(i * (ancho / 2 + grosor / 2 + 0.01), j * eje, alto * 0.25, 0.02, alto * 0.5, alto * 0.5, llanta));
            }
        }
    }

    private static void par(List<Pieza> lista, double x, double y, double z, double w, double l, double h, String c) {
        lista.add(new Pieza(-x, y, z, w, l, h, c));
        lista.add(new Pieza(x, y, z, w, l, h, c));
    }

    static List<Pieza> piezas(String modelo, Aspecto aspecto, double[] placa) {
        if (ModelosBarcos.esBarco(modelo)) {
            placa[0] = Double.NaN; placa[1] = Double.NaN; placa[2] = Double.NaN;
            return ModelosBarcos.piezas(modelo, aspecto, new double[3]);
        }
        if (modelo.equals("taxi")) return ModelosBarcos.piezasTaxi(aspecto, placa);
        return ModelosExtra.piezas(modelo, aspecto, placa);
    }

    Aspecto aspectoDe(Entity coche) {
        Aspecto aspecto = Aspecto.leer(coche.getPersistentDataContainer().get(pl.kAspecto, PersistentDataType.STRING));
        if (aspecto == null) {
            Tier tier = pl.conduccion.tierDe(coche);
            if (tier == null) return null;
            aspecto = azar(tier);
            coche.getPersistentDataContainer().set(pl.kAspecto, PersistentDataType.STRING, aspecto.guardar());
        }
        return aspecto;
    }

    private float yawDe(ArmorStand cart) {
        Conduccion.Estado estado = pl.conduccion.conduciendo.get(cart.getUniqueId());
        if (estado != null) return estado.yaw;
        Float guardado = cart.getPersistentDataContainer().get(pl.kYaw, PersistentDataType.FLOAT);
        return guardado == null ? cart.getLocation().getYaw() : guardado;
    }

    void montar(ArmorStand cart) {
        if (!cart.isValid() || vistas.containsKey(cart.getUniqueId())) return;
        if (!pl.deEstaTanda(cart.getPersistentDataContainer())) { cart.eject(); cart.remove(); return; }
        Tier tier = pl.conduccion.tierDe(cart);
        Aspecto aspecto = aspectoDe(cart);
        if (tier == null || aspecto == null) return;
        cart.setCustomNameVisible(false);
        pl.conduccion.gravedad(cart, tier);
        pl.conduccion.asiento(cart, alturaAsiento(modelo(tier)));
        Extra.visibilidadCoche(cart, false);
        for (Entity pasajero : cart.getPassengers()) if (pasajero instanceof Player jugador) jugador.showEntity(pl, cart);
        Vista vista = new Vista(cart);
        Location loc = cart.getLocation();
        float yaw = yawDe(cart) + (float) pl.getConfig().getDouble("modelos.giro-extra", 0);
        loc.setYaw(yaw);
        loc.setPitch(0);
        double[] placa = new double[3];
        String modelo = modelo(tier);
        List<Pieza> lista = piezas(modelo, aspecto, placa);
        double[] lateral = new double[3];
        if (ModelosBarcos.esBarco(modelo)) ModelosBarcos.piezas(modelo, aspecto, lateral);
        else lateral[0] = Double.NaN;
        // La Goma: los fardos solo se ven con la bodega por encima de la mitad
        if (modelo.equals("goma") && pl.carga.ocupacion(cart, tier) < 0.5)
            lista.removeIf(q -> q.c().equals("fardo") || q.c().equals("lonaazul"));
        // volcada: la embarcación boca abajo
        boolean volcada = Barcos.volcada(pl, cart);
        if (volcada) {
            List<Pieza> girada = new ArrayList<>();
            for (Pieza q : lista) girada.add(new Pieza(-q.x(), q.y(), -(q.z() + q.h()) + 0.25, q.w(), q.l(), q.h(), q.c(), -q.rx(), -q.ry(), q.rz()));
            lista = girada;
            lateral[0] = Double.NaN;
        }
        Material luzTaxi = pl.taximetro.luz(pl.conduccion.matriculaDe(cart));
        for (Pieza pieza : lista) {
            Material material = pieza.c().equals("luztaxi") ? luzTaxi : material(pieza.c(), aspecto);
            boolean luz = pieza.c().equals("faro") || pieza.c().equals("piloto") || pieza.c().equals("luzazul") || pieza.c().equals("luztaxi");
            vista.piezas.add(cart.getWorld().spawn(loc, BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock(material.createBlockData());
                d.setTransformation(transformacion(pieza));
                d.setTeleportDuration(2);
                if (luz) d.setBrightness(new Display.Brightness(15, 15));
            }));
        }
        // matrículas: placa[0] delantera, placa[1] trasera, placa[2] altura (NaN = sin matrícula)
        String matricula = pl.conduccion.matriculaDe(cart);
        boolean alReves = pl.getConfig().getBoolean("modelos.matricula-al-reves", false);
        for (int i = 0; i < 2; i++) {
            if (Double.isNaN(placa[i])) continue;
            boolean trasera = i == 1;
            float giro = (float) Math.toRadians(trasera ^ alReves ? 180.0 : 0.0);
            double z = placa[i];
            double alto = placa[2];
            vista.piezas.add(cart.getWorld().spawn(loc, TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setText(matricula);
                t.setBackgroundColor(Color.fromARGB(255, 235, 235, 235));
                t.setShadowed(false);
                t.setLineWidth(200);
                t.setTransformation(new Transformation(new Vector3f(0, (float) alto, (float) z),
                        new Quaternionf().rotateY(giro), new Vector3f(0.3f, 0.3f, 0.3f), new Quaternionf()));
                t.setTeleportDuration(2);
            }));
        }
        // embarcaciones: matrícula en los dos costados
        if (!Double.isNaN(lateral[0])) {
            for (int lado = -1; lado <= 1; lado += 2) {
                float giro = (float) Math.toRadians(lado > 0 ? 90.0 : -90.0);
                double x = lado * lateral[0];
                vista.piezas.add(cart.getWorld().spawn(loc, TextDisplay.class, t -> {
                    t.setPersistent(false);
                    t.setText(matricula);
                    t.setBackgroundColor(Color.fromARGB(255, 235, 235, 235));
                    t.setShadowed(false);
                    t.setLineWidth(200);
                    t.setTransformation(new Transformation(new Vector3f((float) x, (float) lateral[2], (float) lateral[1]),
                            new Quaternionf().rotateY(giro), new Vector3f(0.3f, 0.3f, 0.3f), new Quaternionf()));
                    t.setTeleportDuration(2);
                }));
            }
        }
        // caja de interacción para clicar el coche
        double[] caja = ModelosBarcos.esBarco(modelo) ? ModelosBarcos.caja(modelo) : switch (modelo(tier)) {
            case "tartana" -> new double[]{1.6, 1.4};
            case "deportivo" -> new double[]{1.7, 0.85};
            case "furgoneta" -> new double[]{1.6, 1.6};
            case "quad" -> new double[]{1.3, 1.05};
            case "motocarro" -> new double[]{1.5, 1.1};
            default -> new double[]{1.6, 1.1};
        };
        Interaction hit = cart.getWorld().spawn(loc, Interaction.class, h -> {
            h.setPersistent(false);
            h.setInteractionWidth((float) caja[0]);
            h.setInteractionHeight((float) caja[1]);
            h.setResponsive(true);
        });
        vista.piezas.add(hit);
        cajas.put(hit.getUniqueId(), cart);
        // barcos largos: cajas extra a proa y a popa
        for (int i = 2; i < caja.length; i++) {
            double frente = caja[i];
            Interaction extra = cart.getWorld().spawn(posicion(loc, yaw, 0, frente, 0), Interaction.class, h -> {
                h.setPersistent(false);
                h.setInteractionWidth((float) Math.min(caja[0], 1.6));
                h.setInteractionHeight((float) caja[1]);
                h.setResponsive(true);
            });
            vista.piezas.add(extra);
            vista.desplazadas.put(extra, new double[]{0, frente, 0});
            cajas.put(extra.getUniqueId(), cart);
        }
        vista.sitios = ModelosBarcos.asientos(modelo);
        vista.ultima = loc;
        vista.ultimoYaw = yaw;
        vistas.put(cart.getUniqueId(), vista);
        if (cart.getPassengers().isEmpty()) pl.conduccion.frenoDeMano(cart);
    }

    void desmontar(UUID id) {
        Vista vista = vistas.remove(id);
        if (vista != null) {
            for (Entity pieza : vista.piezas) { cajas.remove(pieza.getUniqueId()); pieza.remove(); }
            quitarAsientos(vista);
        }
    }

    /** Posición de un punto del vehículo (lateral, frente, alto) en el mundo. */
    static Location posicion(Location centro, float yaw, double lateral, double frente, double alto) {
        double rad = Math.toRadians(yaw);
        double x = lateral * Math.cos(rad) - frente * Math.sin(rad);
        double z = lateral * Math.sin(rad) + frente * Math.cos(rad);
        Location l = centro.clone().add(x, alto, z);
        l.setYaw(yaw);
        l.setPitch(0);
        return l;
    }

    private void quitarAsientos(Vista vista) {
        for (int i = 0; i < vista.asientos.length; i++) {
            Entity asiento = vista.asientos[i];
            if (asiento == null) continue;
            for (Entity p : new ArrayList<>(asiento.getPassengers())) asiento.removePassenger(p);
            asiento.remove();
            vista.asientos[i] = null;
        }
    }

    /** Baja a todos los pasajeros de los asientos (al volcar, guardar o desguazar). */
    void vaciarAsientos(ArmorStand cart) {
        Vista vista = vistas.get(cart.getUniqueId());
        if (vista != null) quitarAsientos(vista);
    }

    /** Pasajeros sentados en los asientos de este vehículo (sin contar al conductor). */
    List<Player> pasajeros(ArmorStand cart) {
        List<Player> lista = new ArrayList<>();
        Vista vista = vistas.get(cart.getUniqueId());
        if (vista == null) return lista;
        for (Entity asiento : vista.asientos)
            if (asiento != null) for (Entity p : asiento.getPassengers()) if (p instanceof Player j) lista.add(j);
        return lista;
    }

    /** Vehículo en cuyo asiento de pasajero va sentado el jugador, o null. */
    ArmorStand vehiculoDePasajero(Player jugador) {
        Entity v = jugador.getVehicle();
        if (!(v instanceof ArmorStand)) return null;
        for (Vista vista : vistas.values()) for (Entity asiento : vista.asientos) if (v.equals(asiento)) return vista.cart;
        return null;
    }

    private boolean sentar(ArmorStand cart, Player jugador) {
        Vista vista = vistas.get(cart.getUniqueId());
        if (vista == null) return false;
        for (int i = 0; i < vista.sitios.length && i < vista.asientos.length; i++) {
            if (vista.asientos[i] != null && vista.asientos[i].isValid()) continue;
            double[] d = vista.sitios[i];
            Location sitio = posicion(cart.getLocation(), yawDe(cart), d[0], d[1], d[2]);
            ArmorStand asiento = cart.getWorld().spawn(sitio, ArmorStand.class, a -> {
                a.setPersistent(false);
                a.setInvisible(true);
                a.setMarker(true);
                a.setGravity(false);
                a.setInvulnerable(true);
                a.setSilent(true);
                a.setSmall(true);
            });
            if (!asiento.addPassenger(jugador)) { asiento.remove(); return false; }
            vista.asientos[i] = asiento;
            return true;
        }
        return false;
    }

    @EventHandler
    public void alBajarseDelAsiento(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player jugador) || !(e.getDismounted() instanceof ArmorStand asiento)) return;
        for (Vista vista : vistas.values()) {
            for (int i = 0; i < vista.asientos.length; i++) {
                if (!asiento.equals(vista.asientos[i])) continue;
                vista.asientos[i] = null;
                ArmorStand cart = vista.cart;
                Bukkit.getScheduler().runTask(pl, asiento::remove);
                pl.taximetro.alBajarse(jugador, cart);
                return;
            }
        }
    }

    void rehacer(ArmorStand cart) {
        List<Player> sentados = pasajeros(cart);
        Vista vieja = vistas.remove(cart.getUniqueId());
        if (vieja != null) {
            for (Entity pieza : vieja.piezas) { cajas.remove(pieza.getUniqueId()); pieza.remove(); }
            for (int i = 0; i < vieja.asientos.length; i++) if (vieja.asientos[i] != null) vieja.asientos[i].remove();
        }
        montar(cart);
        for (Player j : sentados) if (j.isOnline() && j.getVehicle() == null) sentar(cart, j);
    }

    void tick() {
        double giroExtra = pl.getConfig().getDouble("modelos.giro-extra", 0);
        Iterator<Vista> it = vistas.values().iterator();
        while (it.hasNext()) {
            Vista vista = it.next();
            ArmorStand cart = vista.cart;
            if (!cart.isValid()) {
                for (Entity pieza : vista.piezas) { cajas.remove(pieza.getUniqueId()); pieza.remove(); }
                quitarAsientos(vista);
                it.remove();
                continue;
            }
            Location loc = cart.getLocation();
            float yaw = yawDe(cart) + (float) giroExtra;
            if (loc.getWorld() != vista.ultima.getWorld()) {
                for (Entity pieza : vista.piezas) { cajas.remove(pieza.getUniqueId()); pieza.remove(); }
                quitarAsientos(vista);
                it.remove();
                Bukkit.getScheduler().runTask(pl, () -> montar(cart));
                continue;
            }
            if (loc.distanceSquared(vista.ultima) < 1.0E-4 && Math.abs(yaw - vista.ultimoYaw) < 0.05) continue;
            loc.setYaw(yaw);
            loc.setPitch(0);
            for (Entity pieza : vista.piezas) {
                double[] d = vista.desplazadas.get(pieza);
                pieza.teleport(d == null ? loc : posicion(loc, yaw, d[0], d[1], d[2]));
            }
            for (int i = 0; i < vista.asientos.length; i++) {
                Entity asiento = vista.asientos[i];
                if (asiento == null) continue;
                if (!asiento.isValid() || asiento.getPassengers().isEmpty()) { asiento.remove(); vista.asientos[i] = null; continue; }
                double[] d = vista.sitios[i];
                asiento.teleport(posicion(loc, yaw, d[0], d[1], d[2]), TeleportFlag.EntityState.RETAIN_PASSENGERS);
            }
            vista.ultima = loc;
            vista.ultimoYaw = yaw;
        }
    }

    void montarTodo() {
        for (World mundo : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Minecart viejo : mundo.getEntitiesByClass(org.bukkit.entity.Minecart.class))
                if (esViejo(viejo)) pl.conduccion.convertir(viejo);
            for (ArmorStand cart : mundo.getEntitiesByClass(ArmorStand.class)) {
                if (pl.conduccion.esCoche(cart)) montar(cart);
            }
        }
    }

    /** Vehículo de antes de la 2.2.0 (vagoneta con matrícula). */
    private boolean esViejo(Entity ent) {
        return ent instanceof org.bukkit.entity.Minecart && ent.getPersistentDataContainer().has(pl.kMatricula, org.bukkit.persistence.PersistentDataType.STRING);
    }

    @EventHandler
    public void alClicarCaja(PlayerInteractEntityEvent e) {
        ArmorStand cart = cajas.get(e.getRightClicked().getUniqueId());
        if (cart != null) clicar(e, cart);
    }

    /** Clic derecho sobre la carrocería o la base: conducir, ir de pasajero o (con mayúsculas) maletero/guardar. */
    void clicar(PlayerInteractEntityEvent e, ArmorStand cart) {
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || !cart.isValid()) return;
        Player jugador = e.getPlayer();
        if (jugador.getVehicle() != null) return;
        if (pl.bidones.es(jugador.getInventory().getItemInMainHand())) { pl.bidones.echar(jugador, cart); return; }
        if (jugador.isSneaking()) { pl.conduccion.clicConMayusculas(jugador, cart); return; }
        if (!cart.getPassengers().isEmpty()) {
            Tier tier = pl.conduccion.tierDe(cart);
            if (tier == null || tier.plazas() <= 1) { jugador.sendMessage(ChatColor.RED + "Ya hay alguien conduciendo y no caben más."); return; }
            if (Barcos.volcada(pl, cart)) return;
            if (!sentar(cart, jugador)) { jugador.sendMessage(ChatColor.RED + "No quedan plazas libres."); return; }
            jugador.sendMessage(ChatColor.GRAY + "Vas de pasajero en el " + tier.nombre() + ". Mayúsculas para bajarte.");
            pl.taximetro.alSubirPasajero(jugador, cart);
            return;
        }
        jugador.showEntity(pl, cart);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!cart.isValid() || !cart.getPassengers().isEmpty() || !jugador.isOnline()) return;
            if (!cart.addPassenger(jugador)) Extra.ocultar(jugador, pl, cart);
        }, 2L);
    }

    @EventHandler
    public void alGolpearCaja(PrePlayerAttackEntityEvent e) {
        ArmorStand cart = cajas.get(e.getAttacked().getUniqueId());
        if (cart == null) return;
        e.setCancelled(true);
        if (e.getPlayer().isSneaking() && cart.isValid() && e.getPlayer().getVehicle() == null)
            pl.conduccion.guardarCoche(e.getPlayer(), cart);
    }

    void quitarTodo() {
        for (Vista vista : vistas.values()) {
            for (Entity pieza : vista.piezas) { cajas.remove(pieza.getUniqueId()); pieza.remove(); }
            quitarAsientos(vista);
        }
        vistas.clear();
        cajas.clear();
    }

    @EventHandler
    public void alCargar(EntitiesLoadEvent e) {
        for (Entity ent : e.getEntities()) {
            if (ent instanceof ArmorStand cart && pl.conduccion.esCoche(cart)) Bukkit.getScheduler().runTask(pl, () -> montar(cart));
            else if (esViejo(ent)) Bukkit.getScheduler().runTask(pl, () -> {
                if (!ent.isValid()) return;
                ArmorStand nuevo = pl.conduccion.convertir((org.bukkit.entity.Minecart) ent);
                montar(nuevo);
            });
        }
    }

    @EventHandler
    public void alDescargar(EntitiesUnloadEvent e) {
        for (Entity ent : e.getEntities()) if (ent instanceof ArmorStand && pl.conduccion.esCoche(ent)) desmontar(ent.getUniqueId());
    }
}
