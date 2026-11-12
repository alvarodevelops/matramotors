package matracraft.coches;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Taxímetro del coche Taxi y del Taxi Boat.
 *
 * El taxista lo enciende con /taximetro. Cuando se sube alguien, elige destino en una ventana,
 * ve el precio cerrado (tarifa de ejemplo de un taxi de gran ciudad) y al aceptar se le retiene el dinero.
 * Al llegar cobra el dueño del taxi; si se baja antes, paga lo recorrido. Bajarse y volver a
 * subirse al mismo taxi en unos minutos sigue la misma carrera. Las retenciones se guardan en
 * disco: si el servidor se cae, se devuelven al arrancar.
 */
final class Taximetro implements Listener {

    private enum Fase { ELIGIENDO, EN_CURSO, PAUSADA }

    private static final class Carrera {
        final UUID pasajero; final String nombre; final String matricula; final UUID cart;
        Fase fase = Fase.ELIGIENDO;
        double distancia = 500;      // bloques elegidos
        Location destino;            // null = "más o menos"
        String destinoNombre = "más o menos";
        double precio, retenido, recorrido;
        boolean avisoExceso, avisoTope;
        long desde = System.currentTimeMillis(), pausadaDesde;
        Location ultima;
        Carrera(Player p, String matricula, ArmorStand cart) {
            this.pasajero = p.getUniqueId(); this.nombre = p.getName(); this.matricula = matricula; this.cart = cart.getUniqueId();
            this.ultima = cart.getLocation();
        }
    }

    // Ventana de destino
    private static final class Ventana implements InventoryHolder {
        final Carrera carrera; Inventory inv;
        final Map<Integer, Object> destinos = new HashMap<>(); // hueco -> Location o Double (bloques)
        Ventana(Carrera c) { this.carrera = c; }
        public Inventory getInventory() { return inv; }
    }

    private final Coches pl;
    private final File archivo;
    private final Map<String, Boolean> encendidos = new HashMap<>();   // matrícula -> taxímetro encendido
    private final Map<String, Double> tarifas = new HashMap<>();       // matrícula -> % de la tarifa oficial
    private final Map<UUID, Carrera> carreras = new HashMap<>();       // por pasajero
    private final Map<UUID, Long> sinTaxista = new HashMap<>();        // cart -> desde cuándo no hay conductor
    private final Map<UUID, Double> pendientes = new HashMap<>();      // dinero retenido de carreras sin cerrar, a devolver
    private final Map<String, Long> rechazos = new HashMap<>();        // pasajero+taxi -> hasta cuándo no puede volver a subir
    private int ticks;

    Taximetro(Coches pl) {
        this.pl = pl;
        this.archivo = new File(pl.getDataFolder(), "taximetro.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        if (y.isConfigurationSection("tarifas"))
            for (String k : y.getConfigurationSection("tarifas").getKeys(false)) tarifas.put(k.replace('_', ' '), y.getDouble("tarifas." + k));
        // dinero retenido de carreras que no se cerraron (servidor caído): se devuelve
        if (y.isConfigurationSection("retenido"))
            for (String k : y.getConfigurationSection("retenido").getKeys(false)) {
                try { pendientes.merge(UUID.fromString(k), y.getDouble("retenido." + k), Double::sum); } catch (IllegalArgumentException ignorada) { }
            }
        devolverPendientes();
    }

    /** Devuelve el dinero retenido de carreras que no se cerraron (servidor caído). Si no hay economía, espera. */
    private void devolverPendientes() {
        if (pl.eco != null) {
            for (Map.Entry<UUID, Double> e : pendientes.entrySet()) {
                if (e.getValue() <= 0) continue;
                OfflinePlayer p = Bukkit.getOfflinePlayer(e.getKey());
                pl.eco.depositPlayer(p, e.getValue());
                pl.getLogger().info(String.format(Locale.ROOT, "Taxímetro: devueltos %.2f Ⓜ retenidos a %s (carrera sin cerrar).", e.getValue(), p.getName()));
            }
            pendientes.clear();
        }
        guardar();
    }

    private void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Double> e : tarifas.entrySet()) y.set("tarifas." + e.getKey().replace(' ', '_'), e.getValue());
        Map<UUID, Double> retenido = new HashMap<>(pendientes);
        for (Carrera c : carreras.values()) if (c.retenido > 0) retenido.merge(c.pasajero, c.retenido, Double::sum);
        for (Map.Entry<UUID, Double> e : retenido.entrySet()) y.set("retenido." + e.getKey(), e.getValue());
        try { y.save(archivo); } catch (IOException e) { pl.getLogger().warning("No pude guardar taximetro.yml"); }
    }

    // ---------- tarifa ----------

    private double conversion() { return pl.getConfig().getDouble("gasolina.euros-a-dolares", 2.0); }

    /** Tarifa 2 por la noche (21-7), sábados y domingos; si no, tarifa 1. */
    private String tarifaActual() {
        ZonedDateTime ahora = ZonedDateTime.now(ZoneId.of(pl.getConfig().getString("zona-horaria", "Europe/Madrid")));
        boolean finde = ahora.getDayOfWeek() == DayOfWeek.SATURDAY || ahora.getDayOfWeek() == DayOfWeek.SUNDAY;
        boolean noche = ahora.getHour() < 7 || ahora.getHour() >= 21;
        return finde || noche ? "tarifa2" : "tarifa1";
    }

    private double factor(String matricula, Tier tier) {
        double propio = tarifas.getOrDefault(matricula, 1.0);
        double nautico = tier != null && tier.agua() ? 1 + pl.getConfig().getDouble("taximetro.suplemento-nautico", 0.25) : 1;
        return propio * nautico * conversion();
    }

    private double bajada(String t) { return pl.getConfig().getDouble("taximetro." + t + ".bajada", t.equals("tarifa1") ? 1.70 : 2.02); }
    private double km(String t) { return pl.getConfig().getDouble("taximetro." + t + ".km", t.equals("tarifa1") ? 1.16 : 1.43); }
    private double minima(String t) { return pl.getConfig().getDouble("taximetro." + t + ".minima", t.equals("tarifa1") ? 4.62 : 5.66); }
    private double bloquesPorKm() { return Math.max(1, pl.getConfig().getDouble("taximetro.bloques-por-km", 10)); }

    /** Precio en Ⓜ de un trayecto de tantos bloques en ese taxi, ahora. */
    double precio(String matricula, Tier tier, double bloques) {
        String t = tarifaActual();
        double euros = Math.max(minima(t), bajada(t) + km(t) * bloques / bloquesPorKm());
        return Math.round(euros * factor(matricula, tier) * 100.0) / 100.0;
    }

    private double porBloque(String matricula, Tier tier) {
        return km(tarifaActual()) / bloquesPorKm() * factor(matricula, tier);
    }

    private double minimaEn(String matricula, Tier tier) {
        return Math.round(minima(tarifaActual()) * factor(matricula, tier) * 100.0) / 100.0;
    }

    // ---------- estado del taxi ----------

    boolean encendido(String matricula) { return matricula != null && encendidos.getOrDefault(matricula, false); }

    private boolean conCliente(String matricula) {
        for (Carrera c : carreras.values()) if (c.matricula.equals(matricula)) return true;
        return false;
    }

    /** Material de la luz del techo del taxi: verde libre, roja ocupado, apagada. */
    Material luz(String matricula) {
        if (!encendido(matricula)) return Material.WHITE_CONCRETE;
        return conCliente(matricula) ? Material.RED_STAINED_GLASS : Material.LIME_STAINED_GLASS;
    }

    private void refrescarLuz(String matricula, UUID cartId) {
        Entity e = Bukkit.getEntity(cartId);
        if (e instanceof ArmorStand cart && cart.isValid()) pl.modelos.rehacer(cart);
    }

    private static Player conductor(ArmorStand cart) {
        return !cart.getPassengers().isEmpty() && cart.getPassengers().get(0) instanceof Player p ? p : null;
    }

    // ---------- comando ----------

    void comando(Player p, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("ampliar")) { ampliar(p); return; }
        Entity v = p.getVehicle();
        ArmorStand cart = v instanceof ArmorStand m && pl.conduccion.esCoche(m) ? m : null;
        Tier tier = cart == null ? null : pl.conduccion.tierDe(cart);
        if (cart == null || tier == null || !tier.taxi() || conductor(cart) != p) {
            p.sendMessage(ChatColor.YELLOW + "Tienes que ir conduciendo un Taxi o un Taxi Boat. Los pasajeros pueden usar " + ChatColor.WHITE + "/taximetro ampliar");
            return;
        }
        String matricula = pl.conduccion.matriculaDe(cart);
        switch (sub) {
            case "tarifa" -> {
                if (args.length < 2) {
                    p.sendMessage(ChatColor.GRAY + String.format(Locale.ROOT, "Tu tarifa es el %.0f %% de la oficial. Cámbiala con /taximetro tarifa <50-200>.",
                            tarifas.getOrDefault(matricula, 1.0) * 100));
                    return;
                }
                if (conCliente(matricula)) { p.sendMessage(ChatColor.RED + "No puedes cambiar la tarifa con un cliente dentro."); return; }
                double pct;
                try { pct = Double.parseDouble(args[1].replace("%", "").replace(',', '.')); } catch (NumberFormatException e) { p.sendMessage(ChatColor.RED + "Pon un número, p. ej. /taximetro tarifa 120"); return; }
                double min = pl.getConfig().getDouble("taximetro.libre-minimo", 0.5) * 100, max = pl.getConfig().getDouble("taximetro.libre-maximo", 2.0) * 100;
                pct = Math.max(min, Math.min(max, pct));
                tarifas.put(matricula, pct / 100.0);
                guardar();
                p.sendMessage(ChatColor.GREEN + String.format(Locale.ROOT, "Tarifa del taxi %s: %.0f %% de la oficial.", matricula, pct));
            }
            case "terminar" -> {
                int n = 0;
                for (Carrera c : new ArrayList<>(carreras.values())) if (c.matricula.equals(matricula)) { cerrar(c, false, "el taxista ha terminado la carrera"); n++; }
                p.sendMessage(n == 0 ? ChatColor.GRAY + "No llevas ninguna carrera." : ChatColor.GREEN + "Carreras terminadas: " + n + " (cobrado lo recorrido).");
            }
            default -> {
                boolean ahora = !encendido(matricula);
                if (!ahora && conCliente(matricula)) {
                    p.sendMessage(ChatColor.RED + "No puedes apagar el taxímetro con un cliente dentro. Usa " + ChatColor.WHITE + "/taximetro terminar"
                            + ChatColor.RED + " para cobrar lo recorrido.");
                    return;
                }
                encendidos.put(matricula, ahora);
                p.sendMessage(ahora ? ChatColor.GREEN + "🚕 Taxímetro ENCENDIDO. " + ChatColor.GRAY + String.format(Locale.ROOT,
                        "Bajada %.2f Ⓜ · %.3f Ⓜ/bloque · mínimo %.2f Ⓜ (%s).", precio(matricula, tier, 0) - 0, porBloque(matricula, tier),
                        minimaEn(matricula, tier), tarifaActual().equals("tarifa1") ? "tarifa 1, día laborable" : "tarifa 2, noche o fin de semana")
                        : ChatColor.YELLOW + "🚕 Taxímetro apagado: quien se suba va gratis.");
                refrescarLuz(matricula, cart.getUniqueId());
            }
        }
    }

    // ---------- subir y bajar ----------

    void alSubirPasajero(Player p, ArmorStand cart) {
        Tier tier = pl.conduccion.tierDe(cart);
        String matricula = pl.conduccion.matriculaDe(cart);
        if (tier == null || !tier.taxi() || !encendido(matricula)) return;
        Carrera c = carreras.get(p.getUniqueId());
        if (c != null && c.matricula.equals(matricula) && c.fase == Fase.PAUSADA) {
            // vuelve al mismo taxi antes de que pase el plazo: sigue la misma carrera
            c.fase = Fase.EN_CURSO;
            c.ultima = cart.getLocation();
            p.sendMessage(ChatColor.GRAY + "Sigues con tu carrera a " + c.destinoNombre + ".");
            return;
        }
        if (c != null) cerrar(c, false, "te has subido a otro taxi");
        Long hasta = rechazos.get(p.getUniqueId() + matricula);
        if (hasta != null && hasta > System.currentTimeMillis()) {
            Bukkit.getScheduler().runTask(pl, () -> { if (pl.modelos.vehiculoDePasajero(p) != null) p.leaveVehicle(); });
            p.sendMessage(ChatColor.RED + "Espera un poco antes de volver a subirte a este taxi.");
            return;
        }
        Player taxista = conductor(cart);
        if (taxista != null && taxista.getUniqueId().equals(p.getUniqueId())) return;
        if (pl.eco == null) return;
        c = new Carrera(p, matricula, cart);
        carreras.put(p.getUniqueId(), c);
        abrirVentana(p, c, tier);
        refrescarLuz(matricula, cart.getUniqueId());
        Carrera esta = c;
        long segundos = pl.getConfig().getLong("taximetro.segundos-para-aceptar", 30);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (carreras.get(p.getUniqueId()) != esta || esta.fase != Fase.ELIGIENDO) return;
            carreras.remove(p.getUniqueId());
            rechazos.put(p.getUniqueId() + esta.matricula, System.currentTimeMillis() + 60_000);
            if (p.isOnline()) {
                p.closeInventory();
                ArmorStand m = pl.modelos.vehiculoDePasajero(p);
                if (m != null && m.getUniqueId().equals(esta.cart)) p.leaveVehicle();
                p.sendMessage(ChatColor.YELLOW + "No has elegido destino a tiempo: te has bajado del taxi sin pagar nada.");
            }
            refrescarLuz(esta.matricula, esta.cart);
        }, segundos * 20L);
    }

    void alBajarse(Player p, ArmorStand cart) {
        Carrera c = carreras.get(p.getUniqueId());
        if (c == null || !c.cart.equals(cart.getUniqueId())) return;
        bajada(c, p.getLocation());
    }

    void alBajarseTodos(ArmorStand cart) {
        for (Carrera c : new ArrayList<>(carreras.values())) {
            if (!c.cart.equals(cart.getUniqueId())) continue;
            Player p = Bukkit.getPlayer(c.pasajero);
            bajada(c, p != null ? p.getLocation() : cart.getLocation());
        }
    }

    private void bajada(Carrera c, Location donde) {
        if (c.fase == Fase.ELIGIENDO) {
            carreras.remove(c.pasajero);
            refrescarLuz(c.matricula, c.cart);
            return;
        }
        if (c.fase == Fase.PAUSADA) return;
        if (llegado(c, donde)) { cerrar(c, true, null); return; }
        c.fase = Fase.PAUSADA;
        c.pausadaDesde = System.currentTimeMillis();
        Player p = Bukkit.getPlayer(c.pasajero);
        if (p != null) p.sendMessage(ChatColor.YELLOW + "Te has bajado antes de llegar. Si vuelves a este taxi en "
                + pl.getConfig().getLong("taximetro.segundos-para-volver", 180) / 60 + " min sigues con la carrera; si no, pagas lo recorrido.");
    }

    private boolean llegado(Carrera c, Location donde) {
        double radio = pl.getConfig().getDouble("taximetro.radio-llegada", 30);
        if (c.destino != null && donde != null && donde.getWorld() == c.destino.getWorld() && donde.distance(c.destino) <= radio) return true;
        return c.recorrido >= c.distancia * pl.getConfig().getDouble("taximetro.porcentaje-llegada", 0.9);
    }

    // ---------- cobro ----------

    /** Cierra la carrera: completa = precio cerrado (+ exceso); si no, lo recorrido (mínimo la carrera mínima). */
    private void cerrar(Carrera c, boolean completa, String motivo) {
        carreras.remove(c.pasajero);
        if (c.fase == Fase.ELIGIENDO || c.retenido <= 0) { refrescarLuz(c.matricula, c.cart); guardar(); return; }
        Tier tier = tierDe(c);
        double cobro;
        if (completa) {
            double exceso = Math.max(0, c.recorrido - c.distancia) * porBloque(c.matricula, tier);
            cobro = Math.min(c.retenido, c.precio + exceso);
        } else if (c.recorrido < 20) {
            cobro = 0; // sin haber andado nada no se cobra (ni la mínima)
        } else {
            double parte = c.distancia <= 0 ? 1 : Math.min(1, c.recorrido / c.distancia);
            cobro = Math.min(c.retenido, Math.max(minimaEn(c.matricula, tier), c.precio * parte));
        }
        // cobra el dueño del taxi (el que figura en el registro); si no se sabe quién es, el taxista
        String dueno = Extra.dueno(pl, c.matricula);
        OfflinePlayer cobrador = dueno == null ? null : Bukkit.getOfflinePlayerIfCached(dueno);
        if (cobrador == null) {
            Entity e = Bukkit.getEntity(c.cart);
            cobrador = e instanceof ArmorStand m ? conductor(m) : null;
        }
        if (cobrador == null || cobrador.getUniqueId().equals(c.pasajero)) cobro = 0; // nadie a quien pagar: se devuelve todo
        cobro = Math.round(cobro * 100.0) / 100.0;
        double devolver = Math.round((c.retenido - cobro) * 100.0) / 100.0;
        OfflinePlayer pasajero = Bukkit.getOfflinePlayer(c.pasajero);
        if (devolver > 0) pl.eco.depositPlayer(pasajero, devolver);
        if (cobrador != null && cobro > 0) pl.eco.depositPlayer(cobrador, cobro);
        c.retenido = 0;
        guardar();
        Player p = pasajero.getPlayer();
        String km = String.format(Locale.ROOT, "%.0f bloques", c.recorrido);
        if (p != null) {
            p.sendMessage(ChatColor.GOLD + "🚕 Carrera " + (completa ? "terminada" : "cerrada") + ChatColor.GRAY + " (" + km + (motivo != null ? ", " + motivo : "") + "): "
                    + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f Ⓜ", cobro) + (devolver > 0 ? ChatColor.GRAY + String.format(Locale.ROOT, " · te devolvemos %.2f Ⓜ", devolver) : ""));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.2f);
        }
        if (cobro > 0 && cobrador != null && cobrador.getPlayer() != null)
            cobrador.getPlayer().sendMessage(ChatColor.GREEN + "🚕 " + c.nombre + " te paga " + String.format(Locale.ROOT, "%.2f Ⓜ", cobro)
                    + ChatColor.GRAY + " por la carrera (" + km + ").");
        if (cobro > 0 && cobrador != null && cobrador.getPlayer() != null) pl.carnet.sumar(cobrador.getPlayer(), "taxi-carreras", 1);
        refrescarLuz(c.matricula, c.cart);
    }

    private Tier tierDe(Carrera c) {
        Entity e = Bukkit.getEntity(c.cart);
        Tier t = e == null ? null : pl.conduccion.tierDe(e);
        if (t != null) return t;
        for (Tier x : pl.tiers.values()) if (x.taxi()) return x;
        return null;
    }

    // ---------- bucle ----------

    void tick() {
        if (++ticks % 10 != 0) return;
        if (!pendientes.isEmpty() && pl.eco != null) devolverPendientes();
        if (ticks % 1200 == 0) rechazos.values().removeIf(t -> t < System.currentTimeMillis());
        if (carreras.isEmpty() && sinTaxista.isEmpty()) return;
        long ahora = System.currentTimeMillis();
        long volver = pl.getConfig().getLong("taximetro.segundos-para-volver", 180) * 1000;
        long sinConductor = pl.getConfig().getLong("taximetro.segundos-sin-taxista", 120) * 1000;
        double margen = pl.getConfig().getDouble("taximetro.margen", 0.2);
        for (Carrera c : new ArrayList<>(carreras.values())) {
            Entity e = Bukkit.getEntity(c.cart);
            Player p = Bukkit.getPlayer(c.pasajero);
            if (!(e instanceof ArmorStand cart) || !cart.isValid()) { cerrar(c, false, "el taxi ya no está"); continue; }
            if (c.fase == Fase.PAUSADA) {
                if (ahora - c.pausadaDesde > volver) cerrar(c, false, null);
                continue;
            }
            if (c.fase != Fase.EN_CURSO) continue;
            // ¿sigue sentado? (teletransportes, muertes y desconexiones también cuentan como bajarse)
            boolean dentro = p != null && p.isOnline() && cart.equals(pl.modelos.vehiculoDePasajero(p));
            if (!dentro) { bajada(c, p != null ? p.getLocation() : null); continue; }
            Location l = cart.getLocation();
            if (c.ultima != null && l.getWorld() == c.ultima.getWorld()) c.recorrido += Math.hypot(l.getX() - c.ultima.getX(), l.getZ() - c.ultima.getZ());
            c.ultima = l;
            Tier tier = tierDe(c);
            if (c.recorrido > c.distancia && !c.avisoExceso) {
                c.avisoExceso = true;
                p.sendMessage(ChatColor.YELLOW + "🚕 Ya habéis pasado la distancia elegida: lo que sobre se cobra por bloque. "
                        + ChatColor.WHITE + "/taximetro ampliar" + ChatColor.YELLOW + " para cambiar el destino.");
            }
            double exceso = Math.max(0, c.recorrido - c.distancia) * porBloque(c.matricula, tier);
            if (exceso > c.precio * margen && !c.avisoTope) {
                c.avisoTope = true;
                p.sendMessage(ChatColor.RED + "🚕 El taxímetro ha llegado al tope: amplía el destino con /taximetro ampliar o bájate.");
                Player taxista = conductor(cart);
                if (taxista != null) taxista.sendMessage(ChatColor.RED + "🚕 " + p.getName() + " ha llegado al tope de su carrera: ya no se le cobra más.");
            }
            if (c.destino != null && llegado(c, l) && c.recorrido > 5 && ticks % 40 == 0)
                p.sendActionBar(ChatColor.GREEN + "🚕 Has llegado: bájate (mayúsculas) para pagar.");
        }
        // taxis con clientes y sin taxista
        for (Carrera c : carreras.values()) {
            Entity e = Bukkit.getEntity(c.cart);
            if (e instanceof ArmorStand cart && conductor(cart) == null) sinTaxista.putIfAbsent(c.cart, ahora);
            else sinTaxista.remove(c.cart);
        }
        Iterator<Map.Entry<UUID, Long>> it = sinTaxista.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            boolean hay = false;
            for (Carrera c : carreras.values()) if (c.cart.equals(e.getKey())) { hay = true; break; }
            if (!hay) { it.remove(); continue; }
            if (ahora - e.getValue() < sinConductor) continue;
            it.remove();
            for (Carrera c : new ArrayList<>(carreras.values())) {
                if (!c.cart.equals(e.getKey())) continue;
                Player p = Bukkit.getPlayer(c.pasajero);
                cerrar(c, false, "el taxista se ha ido");
                if (p != null && p.isOnline() && pl.modelos.vehiculoDePasajero(p) != null) p.leaveVehicle();
            }
        }
    }

    // ---------- ventana de destino ----------

    private void abrirVentana(Player p, Carrera c, Tier tier) {
        Ventana v = new Ventana(c);
        v.inv = Bukkit.createInventory(v, 54, ChatColor.DARK_GRAY + "🚕 ¿A dónde vamos?");
        pintar(p, v, tier);
        p.openInventory(v.inv);
    }

    private static ItemStack boton(Material m, String nombre, String... lineas) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(nombre);
        meta.setLore(List.of(lineas));
        it.setItemMeta(meta);
        return it;
    }

    private void pintar(Player p, Ventana v, Tier tier) {
        Carrera c = v.carrera;
        Inventory inv = v.inv;
        inv.clear();
        v.destinos.clear();
        ItemStack fondo = boton(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, fondo);
        String tarifa = tarifaActual().equals("tarifa1") ? "Tarifa 1 (día laborable)" : "Tarifa 2 (noche o fin de semana)";
        inv.setItem(4, boton(Material.CLOCK, ChatColor.GOLD + "Taxímetro · " + c.matricula,
                ChatColor.GRAY + tarifa + (tarifas.getOrDefault(c.matricula, 1.0) != 1.0
                        ? String.format(Locale.ROOT, " · tarifa del taxista %.0f %%", tarifas.get(c.matricula) * 100) : ""),
                ChatColor.GRAY + String.format(Locale.ROOT, "%.3f Ⓜ por bloque · mínimo %.2f Ⓜ", porBloque(c.matricula, tier), minimaEn(c.matricula, tier)),
                ChatColor.DARK_GRAY + "Precio cerrado: se cobra al llegar."));
        int[] distancias = {100, 250, 500, 1000, 2000};
        for (int i = 0; i < distancias.length; i++) {
            int d = distancias[i];
            inv.setItem(10 + i, boton(Material.MAP, ChatColor.YELLOW + "Unos " + d + " bloques",
                    ChatColor.GRAY + String.format(Locale.ROOT, "%.2f Ⓜ", precio(c.matricula, tier, d))));
            v.destinos.put(10 + i, (double) d);
        }
        inv.setItem(16, boton(Material.RED_DYE, ChatColor.RED + "− 50 bloques"));
        v.destinos.put(16, -50.0);
        inv.setItem(17, boton(Material.LIME_DYE, ChatColor.GREEN + "+ 50 bloques"));
        v.destinos.put(17, 50.0);
        Location aqui = p.getLocation();
        int hueco = 18;
        for (Map.Entry<String, Location> e : Esencial.casas(p).entrySet()) {
            if (hueco > 26) break;
            Location l = e.getValue();
            if (l.getWorld() != aqui.getWorld()) continue;
            double d = l.distance(aqui) * 1.2;
            inv.setItem(hueco, boton(Material.RED_BED, ChatColor.AQUA + "Mi casa: " + e.getKey(),
                    ChatColor.GRAY + String.format(Locale.ROOT, "~%.0f bloques · %.2f Ⓜ", d, precio(c.matricula, tier, d))));
            v.destinos.put(hueco++, l);
        }
        hueco = 27;
        for (Map.Entry<String, Location> e : Esencial.warps().entrySet()) {
            if (hueco > 35) break;
            Location l = e.getValue();
            if (l.getWorld() != aqui.getWorld()) continue;
            double d = l.distance(aqui) * 1.2;
            inv.setItem(hueco, boton(Material.ENDER_PEARL, ChatColor.LIGHT_PURPLE + "Warp: " + e.getKey(),
                    ChatColor.GRAY + String.format(Locale.ROOT, "~%.0f bloques · %.2f Ⓜ", d, precio(c.matricula, tier, d))));
            v.destinos.put(hueco++, l);
        }
        c.precio = precio(c.matricula, tier, c.distancia);
        double retener = Math.round(c.precio * (1 + pl.getConfig().getDouble("taximetro.margen", 0.2)) * 100.0) / 100.0;
        inv.setItem(47, boton(Material.BARRIER, ChatColor.RED + "Bajarme", ChatColor.GRAY + "No pagas nada."));
        inv.setItem(49, boton(Material.PAPER, ChatColor.GOLD + "Destino: " + c.destinoNombre,
                ChatColor.WHITE + String.format(Locale.ROOT, "%.0f bloques · %.2f Ⓜ", c.distancia, c.precio),
                ChatColor.GRAY + String.format(Locale.ROOT, "Se te retienen %.2f Ⓜ por si el viaje se alarga;", retener),
                ChatColor.GRAY + "lo que no se use se te devuelve al bajar."));
        inv.setItem(51, boton(Material.LIME_CONCRETE, ChatColor.GREEN + "" + ChatColor.BOLD + "Aceptar",
                ChatColor.GRAY + String.format(Locale.ROOT, "%.2f Ⓜ", c.precio)));
    }

    @EventHandler
    public void alClicar(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Ventana v)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        Carrera c = v.carrera;
        if (carreras.get(p.getUniqueId()) != c) { p.closeInventory(); return; }
        Tier tier = tierDe(c);
        int hueco = e.getRawSlot();
        if (hueco == 47) {
            p.closeInventory();
            carreras.remove(p.getUniqueId());
            ArmorStand m = pl.modelos.vehiculoDePasajero(p);
            if (m != null) p.leaveVehicle();
            refrescarLuz(c.matricula, c.cart);
            return;
        }
        if (hueco == 51) { aceptar(p, c, tier); return; }
        Object d = v.destinos.get(hueco);
        if (d instanceof Double bloques) {
            if (hueco == 16 || hueco == 17) c.distancia = Math.max(50, c.distancia + bloques);
            else { c.distancia = bloques; c.destino = null; c.destinoNombre = "más o menos"; }
            if (hueco == 16 || hueco == 17) { c.destino = null; c.destinoNombre = "más o menos"; }
        } else if (d instanceof Location l) {
            c.destino = l.clone();
            c.distancia = Math.max(50, l.distance(p.getLocation()) * 1.2);
            String n = e.getCurrentItem() == null ? "" : ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            c.destinoNombre = n.replace("Mi casa: ", "tu casa ").replace("Warp: ", "");
        } else return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.5f, 1.2f);
        pintar(p, v, tier);
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof Ventana) e.setCancelled(true);
    }

    private void aceptar(Player p, Carrera c, Tier tier) {
        boolean ampliando = c.fase == Fase.EN_CURSO;
        double precio = precio(c.matricula, tier, c.distancia);
        double retener = Math.round(precio * (1 + pl.getConfig().getDouble("taximetro.margen", 0.2)) * 100.0) / 100.0;
        double falta = Math.round((retener - c.retenido) * 100.0) / 100.0;
        if (falta > 0) {
            if (!pl.eco.has(p, falta)) { p.sendMessage(ChatColor.RED + String.format(Locale.ROOT, "No te llega: hacen falta %.2f Ⓜ.", falta)); return; }
            if (!pl.eco.withdrawPlayer(p, falta).transactionSuccess()) { p.sendMessage(ChatColor.RED + "No se pudo cobrar."); return; }
            c.retenido += falta;
        }
        c.precio = precio;
        c.fase = Fase.EN_CURSO;
        c.avisoExceso = false;
        c.avisoTope = false;
        Entity e = Bukkit.getEntity(c.cart);
        if (e != null && !ampliando) c.ultima = e.getLocation();
        guardar();
        p.closeInventory();
        p.sendMessage(ChatColor.GREEN + "🚕 " + (ampliando ? "Destino cambiado" : "Vamos") + " a " + c.destinoNombre + ChatColor.GRAY
                + String.format(Locale.ROOT, " (%.0f bloques, %.2f Ⓜ). Retenido: %.2f Ⓜ.", c.distancia, c.precio, c.retenido));
        if (e instanceof ArmorStand cart) {
            Player taxista = conductor(cart);
            if (taxista != null) taxista.sendMessage(ChatColor.YELLOW + "🚕 " + p.getName() + " va a " + c.destinoNombre
                    + ChatColor.GRAY + String.format(Locale.ROOT, " (%.0f bloques, %.2f Ⓜ).", c.distancia, c.precio)
                    + (c.destino != null ? String.format(Locale.ROOT, " Coordenadas: %d, %d.", c.destino.getBlockX(), c.destino.getBlockZ()) : ""));
        }
        refrescarLuz(c.matricula, c.cart);
    }

    private void ampliar(Player p) {
        Carrera c = carreras.get(p.getUniqueId());
        if (c == null || c.fase != Fase.EN_CURSO) { p.sendMessage(ChatColor.GRAY + "No vas en ninguna carrera de taxi."); return; }
        Tier tier = tierDe(c);
        c.distancia = Math.max(c.distancia, c.recorrido + 100);
        c.destino = null;
        c.destinoNombre = "más o menos";
        abrirVentana(p, c, tier);
    }

    // ---------- Essentials (casas y warps), si está ----------

    private static final class Esencial {
        static Map<String, Location> casas(Player p) {
            Map<String, Location> mapa = new java.util.LinkedHashMap<>();
            try {
                if (Bukkit.getPluginManager().getPlugin("Essentials") instanceof com.earth2me.essentials.IEssentials ess) {
                    com.earth2me.essentials.User u = ess.getUser(p);
                    for (String n : u.getHomes()) { try { mapa.put(n, u.getHome(n)); } catch (Exception ignorada) { } }
                }
            } catch (Throwable ignorado) { }
            return mapa;
        }

        static Map<String, Location> warps() {
            Map<String, Location> mapa = new java.util.LinkedHashMap<>();
            try {
                if (Bukkit.getPluginManager().getPlugin("Essentials") instanceof com.earth2me.essentials.IEssentials ess) {
                    for (String n : ess.getWarps().getList()) { try { mapa.put(n, ess.getWarps().getWarp(n)); } catch (Exception ignorada) { } }
                }
            } catch (Throwable ignorado) { }
            return mapa;
        }
    }
}
