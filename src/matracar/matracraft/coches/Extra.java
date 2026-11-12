package matracraft.coches;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/** Añadidos varios: sonido del motor, aviso de reserva, portales, dueño del coche... */
final class Extra implements Listener {

    private static Coches pl;
    static Prestados prestados;

    static void alAbrirPrincipal(Menus menus, Player jugador) { if (prestados != null) prestados.botonEnPrincipal(jugador); }

    private final Map<UUID, Integer> reserva = new HashMap<>();
    private long t;

    static void iniciar(Coches plugin) {
        pl = plugin;
        Extra extra = new Extra();
        Bukkit.getPluginManager().registerEvents(extra, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, extra::tick, 20L, 1L);
        prestados = new Prestados(plugin);
        Bukkit.getPluginManager().registerEvents(prestados, plugin);
        if (Bukkit.getPluginManager().isPluginEnabled("Magic") || Bukkit.getPluginManager().getPlugin("Magic") != null) {
            try {
                Bukkit.getPluginManager().registerEvents(new ExtraMagia(), plugin);
            } catch (Throwable e) { plugin.getLogger().warning("No se pudo enganchar con Magic: " + e); }
        }
    }

    // al salir del servidor conduciendo, se baja del coche
    @EventHandler(priority = EventPriority.MONITOR)
    public void alSalir(PlayerQuitEvent e) {
        Player jugador = e.getPlayer();
        Entity vehiculo = jugador.getVehicle();
        if (vehiculo != null && pl.conduccion != null && pl.conduccion.esCoche(vehiculo)) jugador.leaveVehicle();
    }

    static void visibilidadCoche(ArmorStand cart, boolean visible) {
        cart.setVisibleByDefault(true);
        cart.setInvisible(true);
    }

    static void ocultar(Player jugador, Plugin plugin, Entity entidad) {
        if (entidad instanceof ArmorStand && pl != null && pl.conduccion != null && pl.conduccion.esCoche(entidad)) {
            jugador.showEntity(plugin, entidad);
            return;
        }
        jugador.hideEntity(plugin, entidad);
    }

    /** Tiene la llave y además es el dueño (o el coche no tiene dueño registrado). */
    static boolean llaveYDueno(Coches plugin, Player jugador, String matricula) {
        if (!plugin.tieneLlave(jugador, matricula)) return false;
        String dueno = dueno(plugin, matricula);
        if (dueno == null || dueno.equalsIgnoreCase(jugador.getName())) return true;
        jugador.sendMessage("§c✋ Este coche está a nombre de §f" + dueno + "§c: solo su dueño puede hacer eso.");
        return false;
    }

    static boolean llaveYDuenoMenu(Coches plugin, Player jugador, String matricula) {
        if (!plugin.tieneLlave(jugador, matricula)) return false;
        String dueno = dueno(plugin, matricula);
        return dueno == null || dueno.equalsIgnoreCase(jugador.getName());
    }

    static Tier tierDuenoLlave(Seguros seguros, Player jugador, String matricula) {
        Tier tier = seguros.tierDeLlave(jugador, matricula);
        if (tier == null) return null;
        String dueno = dueno(pl, matricula);
        if (dueno == null || dueno.equalsIgnoreCase(jugador.getName())) return tier;
        jugador.sendMessage("§c✋ Este coche está a nombre de §f" + dueno + "§c: solo su dueño puede hacer eso.");
        return null;
    }

    static String dueno(Coches plugin, String matricula) {
        try {
            Field campo = Registro.class.getDeclaredField("y");
            campo.setAccessible(true);
            YamlConfiguration yaml = (YamlConfiguration) campo.get(plugin.registro);
            return yaml.getString("matriculas." + matricula.replace(' ', '_') + ".dueno");
        } catch (Throwable e) { return null; }
    }

    // no se puede romper un coche a golpes (salvo con mayúsculas)
    @EventHandler(priority = EventPriority.LOWEST)
    public void alGolpear(PrePlayerAttackEntityEvent e) {
        Entity golpeado = e.getAttacked();
        if (!(golpeado instanceof ArmorStand) || pl.conduccion == null || !pl.conduccion.esCoche(golpeado)) return;
        e.setCancelled(true);
        // la base es invisible e invulnerable: mayúsculas + golpe la guarda igual que sobre la carrocería
        if (e.getPlayer().isSneaking() && e.getPlayer().getVehicle() == null) pl.conduccion.guardarCoche(e.getPlayer(), golpeado);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alPortal(EntityPortalEvent e) {
        Entity ent = e.getEntity();
        if (ent instanceof ArmorStand && pl.conduccion != null && pl.conduccion.esCoche(ent)) {
            e.setCancelled(true);
            for (Entity pasajero : ent.getPassengers())
                if (pasajero instanceof Player jugador) jugador.sendMessage("§c✋ Los coches no pueden cruzar portales. Bájate y pasa andando.");
        }
    }

    /** El jugador va conduciendo (es el primer pasajero de un coche). */
    static boolean conduce(Player jugador) {
        if (pl == null || pl.conduccion == null) return false;
        Entity vehiculo = jugador.getVehicle();
        if (vehiculo == null || !pl.conduccion.esCoche(vehiculo)) return false;
        List<Entity> pasajeros = vehiculo.getPassengers();
        return !pasajeros.isEmpty() && pasajeros.get(0).getUniqueId().equals(jugador.getUniqueId());
    }

    /** Atropellar a este bicho no necesita seguro. */
    static boolean sinSeguro(LivingEntity bicho) {
        if (bicho instanceof Player) return false;
        if (bicho instanceof Enemy) return true;
        if (bicho instanceof Tameable domable && domable.isTamed()) return false;
        if (bicho instanceof Animals || bicho instanceof WaterMob || bicho instanceof Ambient) {
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("GriefPrevention") && GpHook.dueno(bicho.getLocation()) != null) return false;
            } catch (Throwable ignored) {}
            return true;
        }
        return false;
    }

    private record Motor(String sonido, float volumen, int cada, int cadaParado, float tonoMin, float tonoMax) {}

    private static final Map<String, Motor> MOTORES = Map.ofEntries(
            Map.entry("motocarro", new Motor("block.note_block.basedrum", 0.5f, 3, 7, 0.6f, 1.4f)),
            Map.entry("tartana", new Motor("block.note_block.didgeridoo", 0.35f, 4, 8, 0.5f, 1f)),
            Map.entry("quad", new Motor("block.note_block.basedrum", 0.55f, 2, 5, 0.9f, 1.8f)),
            Map.entry("turismo", new Motor("block.note_block.bass", 0.35f, 3, 7, 0.5f, 1.2f)),
            Map.entry("furgoneta", new Motor("block.note_block.didgeridoo", 0.4f, 4, 8, 0.45f, 0.9f)),
            Map.entry("deportivo", new Motor("block.note_block.didgeridoo", 0.45f, 2, 5, 0.8f, 2f)),
            Map.entry("pedalo", new Motor("entity.generic.swim", 0.15f, 8, 0, 1.2f, 1.6f)),
            Map.entry("motoagua", new Motor("entity.minecart.riding", 0.35f, 3, 9, 1.2f, 2f)),
            Map.entry("barco", new Motor("entity.minecart.riding", 0.4f, 4, 10, 0.5f, 0.9f)),
            Map.entry("taxiboat", new Motor("entity.minecart.riding", 0.25f, 4, 10, 0.8f, 1.2f)),
            Map.entry("goma", new Motor("entity.minecart.riding", 0.55f, 2, 6, 1f, 1.6f)),
            Map.entry("patrullero", new Motor("entity.minecart.riding", 0.45f, 3, 8, 0.4f, 0.8f)),
            Map.entry("hipersonica", new Motor("entity.minecart.riding", 0.5f, 2, 5, 1.4f, 2f)),
            Map.entry("taxi", new Motor("block.note_block.bass", 0.35f, 3, 7, 0.5f, 1.2f)));

    private Motor motor(String modelo) {
        Motor base = MOTORES.getOrDefault(modelo, MOTORES.get("turismo"));
        String ruta = "sonidos.motor." + modelo + ".";
        FileConfiguration cfg = pl.getConfig();
        return new Motor(cfg.getString(ruta + "sonido", base.sonido()), (float) cfg.getDouble(ruta + "volumen", base.volumen()),
                cfg.getInt(ruta + "cada", base.cada()), cfg.getInt(ruta + "cada-parado", base.cadaParado()),
                (float) cfg.getDouble(ruta + "tono-min", base.tonoMin()), (float) cfg.getDouble(ruta + "tono-max", base.tonoMax()));
    }

    private void tick() {
        t++;
        if (pl.conduccion == null) return;
        boolean conSonido = pl.getConfig().getBoolean("sonidos.motor.activado", true);
        Set<UUID> vivos = new HashSet<>();
        for (Conduccion.Estado estado : new ArrayList<>(pl.conduccion.conduciendo.values())) {
            ArmorStand cart = estado.cart;
            if (cart == null || !cart.isValid()) continue;
            UUID id = cart.getUniqueId();
            vivos.add(id);
            Location loc = cart.getLocation();
            World mundo = loc.getWorld();
            String modelo = Modelos.modelo(estado.tier);
            double max = Math.max(1.0E-4, estado.tier.maxPorTick());
            double ritmo = Math.min(1, Math.abs(estado.velocidad) / max);

            // ruido del motor, más agudo cuanto más rápido
            if (conSonido && (estado.gasolina > 0 || estado.tier.sinMotor())) {
                Motor motor = motor(modelo);
                int cada = ritmo < 0.05 ? motor.cadaParado() : motor.cada();
                if (cada > 0 && t % cada == 0) {
                    float tono = (float) (motor.tonoMin() + (motor.tonoMax() - motor.tonoMin()) * ritmo);
                    mundo.playSound(loc, motor.sonido(), motor.volumen(), tono);
                }
            }

            // pitido de marcha atrás de la furgoneta
            if (modelo.equals("furgoneta") && estado.velocidad < -0.002 && t % 12 == 0)
                mundo.playSound(loc, "block.note_block.bit", 0.9f, 1.6f);

            // aviso de reserva: cada 30 s
            if (estado.avisoReserva && estado.gasolina > 0) {
                Integer ultimo = reserva.get(id);
                if (ultimo == null || t - ultimo >= 600) {
                    reserva.put(id, (int) t);
                    pitarReserva(cart);
                }
            } else reserva.remove(id);
        }
        reserva.keySet().retainAll(vivos);
    }

    private static void pitarReserva(ArmorStand cart) {
        for (int i = 0; i < 3; i++) {
            int n = i;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!cart.isValid()) return;
                for (Entity pasajero : cart.getPassengers())
                    if (pasajero instanceof Player jugador)
                        jugador.playSound(jugador.getLocation(), "block.note_block.chime", 0.9f, n == 2 ? 0.8f : 1.2f);
            }, i * 5L);
        }
    }
}
