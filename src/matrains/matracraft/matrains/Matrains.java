package matracraft.matrains;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Matrains — trenes de Matracraft. Parte 1: vías, física propia y conducción manual.
 * Los trenes se guardan en trenes.yml y solo se dibujan con algún jugador cerca.
 */
public final class Matrains implements Listener {

    /** Plugin anfitrión (MatraMotors): trenes ya no es un plugin aparte. */
    final org.bukkit.plugin.java.JavaPlugin host;
    private org.bukkit.configuration.file.YamlConfiguration cfg = new org.bukkit.configuration.file.YamlConfiguration();

    public Matrains(org.bukkit.plugin.java.JavaPlugin host) { this.host = host; }

    org.bukkit.configuration.file.YamlConfiguration getConfig() { return cfg; }
    File getDataFolder() { return host.getDataFolder(); }
    java.util.logging.Logger getLogger() { return host.getLogger(); }
    org.bukkit.Server getServer() { return host.getServer(); }

    /** Copia los datos de la carpeta vieja plugins/Matrains la primera vez (no borra nada). */
    private void migrar() {
        File vieja = new File(host.getDataFolder().getParentFile(), "Matrains");
        if (!vieja.isDirectory()) return;
        String[][] archivos = {{"config.yml", "config-trenes.yml"}, {"trenes.yml", "trenes.yml"}, {"datos.yml", "datos.yml"},
                {"luz.yml", "luz.yml"}, {"estaciones.yml", "estaciones.yml"}, {"redes.yml", "redes.yml"}};
        for (String[] a : archivos) {
            File o = new File(vieja, a[0]), n = new File(host.getDataFolder(), a[1]);
            if (!o.isFile() || n.exists()) continue;
            try {
                host.getDataFolder().mkdirs();
                java.nio.file.Files.copy(o.toPath(), n.toPath());
                host.getLogger().info("Trenes: copiado " + a[0] + " de plugins/Matrains. La carpeta vieja se puede borrar cuando compruebes que todo va bien.");
            } catch (IOException ex) { host.getLogger().warning("Trenes: no he podido copiar " + a[0] + ": " + ex.getMessage()); }
        }
    }

    final Tipos tipos = new Tipos();
    Carga carga;
    Red red;
    Energia energia;
    Llaves llaves;
    net.milkbowl.vault.economy.Economy eco;
    Estaciones estaciones;
    Menus menus;
    Objetos objetos;
    /** Esperas de premios (clave jugador:tipo → hora). */
    final Map<String, Long> esperas = new HashMap<>();
    private final java.util.Set<UUID> carnes = new java.util.HashSet<>();
    private final Map<UUID, java.util.function.Consumer<String>> textos = new java.util.concurrent.ConcurrentHashMap<>();
    /** Bloques ocupados por trenes visibles (mundo → bloque → tren), para no chocar. */
    private final Map<String, Map<Long, Tren>> ocupados = new HashMap<>();
    final Map<String, Tren> trenes = new LinkedHashMap<>();
    final Map<UUID, Tren.Coche> porEntidad = new HashMap<>();
    private final Map<Tren.Coche, Tren> trenDe = new HashMap<>();
    private File archivo;
    private int ticks;

    public void activar() {
        migrar();
        File fc = new File(host.getDataFolder(), "config-trenes.yml");
        if (!fc.exists()) host.saveResource("config-trenes.yml", false);
        cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(fc);
        tipos.leer(getConfig());
        archivo = new File(getDataFolder(), "trenes.yml");
        carga = new Carga(this);
        red = new Red(this);
        red.cargar();
        estaciones = new Estaciones(this);
        estaciones.cargar();
        menus = new Menus(this);
        objetos = new Objetos(this);
        cargarDatos();
        energia = new Energia(this);
        llaves = new Llaves(this);
        energia.iniciar();
        var rsp = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (rsp != null) eco = rsp.getProvider();
        else getLogger().warning("Sin Vault: la luz y el gasóleo no se cobran.");
        cargar();
        getServer().getPluginManager().registerEvents(this, host);
        getServer().getPluginManager().registerEvents(carga, host);
        getServer().getPluginManager().registerEvents(red, host);
        getServer().getPluginManager().registerEvents(estaciones, host);
        getServer().getPluginManager().registerEvents(objetos, host);
        getServer().getPluginManager().registerEvents(new Gui(), host);
        Bukkit.getScheduler().runTaskTimer(host, this::tick, 1L, 1L);
        getLogger().info("Trenes: " + trenes.size() + " cargados.");
    }

    public void desactivar() {
        for (Tren t : trenes.values()) {
            t.v = 0;
            t.desaparecer();
        }
        guardar();
        guardarDatos();
    }

    // ------------------------------------------------------------------ bucle

    private void tick() {
        ticks++;
        ocupados.clear();
        for (Tren t : trenes.values()) {
            if (!t.visible) continue;
            Map<Long, Tren> m = ocupados.computeIfAbsent(t.coches.get(0).delante.mundo, k -> new HashMap<>());
            for (Tren.Coche c : t.coches) {
                m.put(Red.clave(c.delante.x, c.delante.y, c.delante.z), t);
                m.put(Red.clave(c.detras.x, c.detras.y, c.detras.z), t);
                Location l = t.lugar(c);
                if (l != null) m.put(Red.clave(l.getBlockX(), l.getBlockY(), l.getBlockZ()), t);
            }
        }
        double aparecer = getConfig().getDouble("fisica.distancia-aparecer", 96);
        double quitar = getConfig().getDouble("fisica.distancia-desaparecer", 128);
        for (Tren t : trenes.values()) {
            if (ticks % 20 == 0) {
                double cerca = distanciaJugador(t);
                if (!t.visible && cerca <= aparecer && t.cargado()) {
                    if (t.recolocar) recolocar(t);
                    t.aparecer();
                }
                else if (t.visible && (!t.cargado() || cerca > quitar && !t.ocupado())) t.desaparecer();
            }
            if (t.visible) t.tick();
            else {
                t.v = 0;
                if (t.auto && t.conLoco()) sinVer(t);
            }
        }
        if (ticks % 100 == 0) estaciones.paneles();
        if (ticks % 600 == 0) { guardar(); guardarDatos(); }
    }

    /** Tren automático sin nadie cerca: viaje "saltado" con los tramos ya conocidos. */
    private void sinVer(Tren t) {
        if (t.viajando && t.virtual) { Auto.virtual(this, t); return; }
        if (t.viajando) {
            // iba a la vista y se ha quedado solo: si conoce el tramo, termina el viaje sin dibujarse
            Auto.Tramo tr = Auto.TRAMOS.get(t.origen + ">" + t.destino);
            if (tr != null && tr.ejes().size() == t.coches.size() * 2) {
                Tipos.Loco l = tipos.loco(t.tipo);
                double falta = Double.isNaN(t.restante) ? Math.max(0, tr.largo() - t.recorridoTramo) : t.restante;
                t.virtual = true;
                t.llegadaVirtual = System.currentTimeMillis() + (long) (falta / Math.max(2, l.velocidad() * 0.8) * 1000);
            }
            return;
        }
        if (System.currentTimeMillis() >= t.paradoHasta && !t.ruta.isEmpty() && Auto.permitido(this, t)) Auto.partir(this, t);
    }

    boolean ocupadoPorOtro(Via.Pos p, Tren yo) {
        Map<Long, Tren> m = ocupados.get(p.mundo);
        if (m == null) return false;
        Tren t = m.get(Red.clave(p.x, p.y, p.z));
        return t != null && t != yo;
    }

    // ------------------------------------------------------------------ datos sueltos (carnés, tramos)

    boolean tieneCarne(UUID u) { return carnes.contains(u); }

    void darCarne(UUID u) { carnes.add(u); guardarDatos(); }

    private void cargarDatos() {
        File f = new File(getDataFolder(), "datos.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String s : y.getStringList("carnes")) carnes.add(UUID.fromString(s));
        ConfigurationSection tr = y.getConfigurationSection("tramos");
        if (tr != null) for (String k : tr.getKeys(false))
            Auto.TRAMOS.put(k.replace('_', '>'), new Auto.Tramo(tr.getDouble(k + ".largo"), tr.getStringList(k + ".ejes")));
    }

    void guardarDatos() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> c = new ArrayList<>();
        for (UUID u : carnes) c.add(u.toString());
        y.set("carnes", c);
        for (Map.Entry<String, Auto.Tramo> e : Auto.TRAMOS.entrySet()) {
            String k = "tramos." + e.getKey().replace('>', '_');
            y.set(k + ".largo", e.getValue().largo());
            y.set(k + ".ejes", e.getValue().ejes());
        }
        try { y.save(new File(getDataFolder(), "datos.yml")); } catch (IOException ex) { getLogger().warning("No se pudo guardar datos.yml"); }
    }

    // ------------------------------------------------------------------ texto por el chat

    void pedirTexto(Player p, String pregunta, java.util.function.Consumer<String> luego) {
        textos.put(p.getUniqueId(), luego);
        p.sendMessage(ChatColor.GOLD + pregunta);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void alChat(io.papermc.paper.event.player.AsyncChatEvent e) {
        java.util.function.Consumer<String> c = textos.remove(e.getPlayer().getUniqueId());
        if (c == null) return;
        e.setCancelled(true);
        String texto = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(host, () -> {
            if (texto.isEmpty() || texto.equalsIgnoreCase("cancelar")) { p.sendMessage(ChatColor.GRAY + "Cancelado."); return; }
            c.accept(ChatColor.stripColor(texto));
        });
    }

    private static double distanciaJugador(Tren t) {
        Via.Pos p = t.locomotora().delante;
        double mejor = Double.MAX_VALUE;
        for (Player j : Bukkit.getOnlinePlayers()) {
            if (!j.getWorld().getName().equals(p.mundo)) continue;
            Location l = j.getLocation();
            double dx = l.getX() - p.x, dz = l.getZ() - p.z;
            mejor = Math.min(mejor, Math.sqrt(dx * dx + dz * dz));
        }
        return mejor;
    }

    /** Dueño del tren o administrador (las llaves prestadas llegan con MATRAMOTORS en la parte 5). */
    /**
     * Para el menú «Mis vehículos» de MATRAMOTORS (lo llama por reflexión): un Object[] por tren del jugador
     * con {nombre, lore (List<String>), icono (Material), abrir (Consumer<Player>)}.
     */
    public List<Object[]> resumenTrenes(Player p) {
        List<Object[]> lista = new ArrayList<>();
        for (Tren t : trenes.values()) {
            boolean mio = t.dueno.equals(p.getUniqueId());
            if (!mio && !llaves.tiene(p, t)) continue;
            java.util.function.Consumer<Player> abrir = t.conLoco() ? q -> menus.panel(q, t) : menus::misTrenes;
            List<String> lore = menus.loreTren(t);
            if (!mio) lore.add(0, ChatColor.AQUA + "Prestado · dueño: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(t.dueno).getName());
            lista.add(new Object[]{(mio ? "" : "🔑 ") + t.nombre, lore,
                    t.conLoco() ? org.bukkit.Material.FURNACE_MINECART : org.bukkit.Material.CHEST_MINECART, abrir});
        }
        return lista;
    }

    boolean puedeUsar(Player p, Tren t) {
        return esDueno(p, t) || llaves.tiene(p, t);
    }

    /** Lo que solo hace el dueño (guardar, enganchar, llaves, nombre, billete). */
    boolean esDueno(Player p, Tren t) {
        return p.getUniqueId().equals(t.dueno) || p.hasPermission("matrains.admin");
    }

    void reindexar() { trenDe.clear(); }

    Tren tren(Tren.Coche c) {
        Tren t = trenDe.get(c);
        if (t != null) return t;
        for (Tren x : trenes.values()) for (Tren.Coche y : x.coches) trenDe.put(y, x);
        return trenDe.get(c);
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.HIGH)
    public void alClicar(PlayerInteractEntityEvent e) {
        Tren.Coche c = porEntidad.get(e.getRightClicked().getUniqueId());
        if (c == null) return;
        e.setCancelled(true);
        Tren t = tren(c);
        if (t == null || !t.visible) return;
        Player p = e.getPlayer();
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        boolean cadena = p.getInventory().getItemInMainHand().getType() == org.bukkit.Material.IRON_CHAIN;
        boolean agachado = p.isSneaking();
        Bukkit.getScheduler().runTask(host, () -> {
            if (!t.visible || c.base == null) return;
            if (cadena) {
                if (!esDueno(p, t)) { p.sendMessage(ChatColor.RED + "Solo el dueño engancha y suelta vagones."); return; }
                if (agachado) Enganche.soltar(this, p, t, c); else Enganche.enganchar(this, p, t);
                return;
            }
            if (c.locomotora && t.conLoco() && puedeUsar(p, t)) {
                if (energia.repostar(p, t, tipos.loco(t.tipo), false)) return;
                if (agachado) { menus.panel(p, t); return; }
            }
            Tipos.Vagon tv = c.locomotora ? null : tipos.vagon(c.tipo);
            if (tv != null && !tv.pasajeros()) carga.abrir(p, t, c);
            else t.subir(p, c);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alClicarEnPunto(PlayerInteractAtEntityEvent e) {
        if (porEntidad.containsKey(e.getRightClicked().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alManipular(PlayerArmorStandManipulateEvent e) {
        if (porEntidad.containsKey(e.getRightClicked().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alGolpear(PrePlayerAttackEntityEvent e) {
        Tren.Coche c = porEntidad.get(e.getAttacked().getUniqueId());
        if (c == null) return;
        e.setCancelled(true);
        Tren t = tren(c);
        Player p = e.getPlayer();
        if (t != null && p.isSneaking() && p.getVehicle() == null) Bukkit.getScheduler().runTask(host, () -> objetos.guardar(p, t));
    }

    // ------------------------------------------------------------------ comandos (pruebas del administrador)

    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Solo en el juego."); return true; }
        if (cmd.getName().equalsIgnoreCase("tren")) {
            // /tren a secas lleva al menú único de vehículos; /tren menu, al de trenes
            boolean unico = true;
            if (a.length == 0 && unico) p.performCommand("vehiculos");
            else menus.principal(p);
            return true;
        }
        if (!p.hasPermission("matrains.admin")) { p.sendMessage(ChatColor.RED + "Usa /tren."); return true; }
        String sub = a.length > 0 ? a[0].toLowerCase() : "ayuda";
        switch (sub) {
            case "probar" -> probar(p, a);
            case "vagon" -> vagon(p, a);
            case "subestacion" -> {
                p.getInventory().addItem(red.objeto(Math.max(1, Math.min(64, numero(a, 1, 1))))).values()
                        .forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
                p.sendMessage(ChatColor.GREEN + "Subestaciones en tu inventario.");
            }
            case "luz" -> p.sendMessage(ChatColor.GOLD + "Luz: " + String.format("%.3f €/kWh", energia.precioLuz)
                    + ChatColor.GRAY + " (PVPC " + (energia.fechaLuz.isEmpty() ? "sin consultar aún" : energia.fechaLuz) + ")");
            case "quitar" -> {
                Tren t = cercano(p, 12);
                if (t == null) { p.sendMessage(ChatColor.RED + "No hay ningún tren a menos de 12 bloques."); return true; }
                t.desaparecer();
                trenes.remove(t.id);
                trenDe.clear();
                guardar();
                p.sendMessage(ChatColor.YELLOW + "Tren " + t.nombre + " quitado.");
            }
            case "lista" -> {
                if (trenes.isEmpty()) p.sendMessage(ChatColor.GRAY + "No hay trenes.");
                for (Tren t : trenes.values()) {
                    Via.Pos q = t.locomotora().delante;
                    p.sendMessage(ChatColor.GOLD + t.nombre + ChatColor.GRAY + " (" + t.tipo + ", " + t.coches.size() + " coches) en "
                            + q.mundo + " " + q.x + " " + q.y + " " + q.z + (t.visible ? ChatColor.GREEN + " · visible" : ""));
                }
            }
            default -> {
                p.sendMessage(ChatColor.GOLD + "Matrains (pruebas): " + ChatColor.GRAY + "mira a un raíl y escribe");
                p.sendMessage(ChatColor.YELLOW + "/matrains probar <" + String.join("|", tipos.locos.keySet()) + "> [aspecto 1-6]");
                p.sendMessage(ChatColor.YELLOW + "/matrains vagon <" + String.join("|", tipos.vagones.keySet()) + "> [aspecto]");
                p.sendMessage(ChatColor.GRAY + "Con una cadena: clic derecho engancha a lo que haya a menos de 3 bloques; mayúsculas + clic suelta.");
                p.sendMessage(ChatColor.YELLOW + "/matrains subestacion [n]" + ChatColor.GRAY + " · " + ChatColor.YELLOW + "/matrains luz");
                p.sendMessage(ChatColor.YELLOW + "/matrains quitar" + ChatColor.GRAY + " (el más cercano) · " + ChatColor.YELLOW + "/matrains lista");
            }
        }
        return true;
    }

    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 1) return List.of("probar", "vagon", "subestacion", "luz", "quitar", "lista");
        if (a.length == 2 && a[0].equalsIgnoreCase("probar")) return new ArrayList<>(tipos.locos.keySet());
        if (a.length == 2 && a[0].equalsIgnoreCase("vagon")) return new ArrayList<>(tipos.vagones.keySet());
        if (a.length == 3) {
            List<String> r = new ArrayList<>();
            int n = Modelos.aspectos(a[1].toLowerCase()).length;
            for (int i = 1; i <= n; i++) r.add(String.valueOf(i));
            return r;
        }
        return List.of();
    }

    private Tren cercano(Player p, double max) {
        Tren mejor = null;
        double d = max;
        for (Tren t : trenes.values()) {
            if (!t.visible) continue;
            for (Tren.Coche c : t.coches) {
                Location l = t.lugar(c);
                if (l == null || l.getWorld() != p.getWorld()) continue;
                double x = l.distance(p.getLocation());
                if (x < d) { d = x; mejor = t; }
            }
        }
        return mejor;
    }

    private static int numero(String[] a, int i, int def) {
        try { return a.length > i ? Integer.parseInt(a[i]) : def; } catch (NumberFormatException e) { return def; }
    }

    /** Raíl al que mira el jugador y sentido de la marcha (el de su mirada). */
    /** Posición de un tren nuevo en este raíl, mirando hacia donde mira el jugador. */
    Via.Pos posicion(Player p, Block b) {
        BlockFace[] s = Objetos.sentido(p, b);
        return new Via.Pos(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), s[0], s[1], 0.5);
    }

    private Via.Pos railMirado(Player p) {
        if (!getConfig().getStringList("mundos-permitidos").contains(p.getWorld().getName())) {
            p.sendMessage(ChatColor.RED + "En este mundo no hay trenes."); return null;
        }
        Block b = p.getTargetBlockExact(6);
        if (!Via.esVia(b)) { p.sendMessage(ChatColor.RED + "Mira a un raíl (a menos de 6 bloques)."); return null; }
        BlockFace[] caras = Via.caras(Via.forma(b));
        double r = Math.toRadians(p.getLocation().getYaw());
        double fx = -Math.sin(r), fz = Math.cos(r);
        BlockFace hacia = caras[0].getModX() * fx + caras[0].getModZ() * fz >= caras[1].getModX() * fx + caras[1].getModZ() * fz ? caras[0] : caras[1];
        BlockFace desde = hacia == caras[0] ? caras[1] : caras[0];
        return new Via.Pos(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), desde, hacia, 0.5);
    }

    /** Coloca los coches uno detrás de otro desde la posición dada; false si no caben. */
    boolean colocar(Tren t, Via.Pos cabeza, List<Tren.Coche> coches) {
        double hueco = getConfig().getDouble("fisica.hueco-entre-vagones", 0.5);
        Via.Pos cursor = cabeza.copia();
        for (int i = 0; i < coches.size(); i++) {
            Tren.Coche c = coches.get(i);
            if (i > 0 && Math.abs(t.avanzar(cursor, -(Tren.EJE * 2 + hueco), true, 0)) < Tren.EJE * 2 + hueco - 1e-6) return false;
            c.delante = cursor.copia();
            double ejes = c.largo - 2 * Tren.EJE;
            if (Math.abs(t.avanzar(cursor, -ejes, true, 0)) < ejes - 1e-6) return false;
            c.detras = cursor.copia();
            t.coches.add(c);
        }
        return true;
    }

    /** Trenes guardados por la 0.1 (otros largos): se vuelven a poner en fila desde la cabeza. */
    private void recolocar(Tren t) {
        t.recolocar = false;
        List<Tren.Coche> coches = new ArrayList<>(t.coches);
        Via.Pos cabeza = coches.get(0).delante.copia();
        t.coches.clear();
        if (!colocar(t, cabeza, coches)) {
            // si no caben, se dejan como estaban
            t.coches.clear();
            t.coches.addAll(coches);
        }
        reindexar();
        guardar();
    }

    Tren.Coche nuevoVagon(String tipo, int aspecto) {
        Tipos.Vagon tv = tipos.vagon(tipo);
        return new Tren.Coche(tipo, false, tv.largo(), tv.asientos(), Math.floorMod(aspecto, Modelos.aspectos(tipo).length));
    }

    private void probar(Player p, String[] a) {
        String tipo = a.length > 1 ? a[1].toLowerCase() : "vapor";
        Tipos.Loco loco = tipos.loco(tipo);
        if (loco == null) { p.sendMessage(ChatColor.RED + "Locomotoras: " + String.join(", ", tipos.locos.keySet())); return; }
        int aspecto = Math.floorMod(numero(a, 2, 1) - 1, Modelos.aspectos(tipo).length);
        Via.Pos cabeza = railMirado(p);
        if (cabeza == null) return;
        Tren t = new Tren(this, UUID.randomUUID().toString().substring(0, 8), p.getUniqueId(), tipo, loco.nombre());
        List<Tren.Coche> coches = new ArrayList<>();
        Tren.Coche lc = new Tren.Coche(tipo, true, loco.largo(), 1, aspecto);
        lc.combustible = energia.maximo(loco);
        coches.add(lc);
        if (!colocar(t, cabeza, coches)) { p.sendMessage(ChatColor.RED + "No cabe: hace falta más vía detrás."); return; }
        trenes.put(t.id, t);
        reindexar();
        t.aparecer();
        guardar();
        p.sendMessage(ChatColor.GREEN + loco.nombre() + " (" + Modelos.nombre(tipo, aspecto) + ") puesta. Clic derecho para conducir; "
                + "pon vagones detrás con /matrains vagon y engánchalos con una cadena.");
    }

    private void vagon(Player p, String[] a) {
        String tipo = a.length > 1 ? a[1].toLowerCase() : "cerrado";
        if (tipos.vagon(tipo) == null) { p.sendMessage(ChatColor.RED + "Vagones: " + String.join(", ", tipos.vagones.keySet())); return; }
        int aspecto = numero(a, 2, 1) - 1;
        Via.Pos cabeza = railMirado(p);
        if (cabeza == null) return;
        Tren t = new Tren(this, UUID.randomUUID().toString().substring(0, 8), p.getUniqueId(), null, tipos.vagon(tipo).nombre());
        Tren.Coche c = nuevoVagon(tipo, aspecto);
        if (!colocar(t, cabeza, List.of(c))) { p.sendMessage(ChatColor.RED + "No cabe: hace falta más vía."); return; }
        trenes.put(t.id, t);
        reindexar();
        t.aparecer();
        guardar();
        Tipos.Vagon tv = tipos.vagon(tipo);
        p.sendMessage(ChatColor.GREEN + tv.nombre() + " (" + Modelos.nombre(tipo, c.aspecto) + ") puesto. "
                + (tv.pasajeros() ? tv.asientos() + " asientos." : "Carga: " + Tipos.nombreAcepta(tv.acepta()) + "."));
    }

    // ------------------------------------------------------------------ guardado

    private void cargar() {
        trenes.clear();
        if (!archivo.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            try {
                String tt = s.getString("tipo", "");
                Tren t = new Tren(this, id, UUID.fromString(s.getString("dueno")), tt.isEmpty() || tipos.loco(tt) == null ? null : tt, s.getString("nombre"));
                for (String linea : s.getStringList("coches")) {
                    String[] p = linea.split("\\|", -1);
                    boolean esLoco = Boolean.parseBoolean(p[1]);
                    if (esLoco ? tipos.loco(p[0]) == null : tipos.vagon(p[0]) == null) continue;
                    int asp = p.length > 6 ? Integer.parseInt(p[6]) : 0;
                    double largo = Modelos.largo(p[0]);
                    int asientos = esLoco ? 1 : tipos.vagon(p[0]).asientos();
                    Tren.Coche c = new Tren.Coche(p[0], esLoco, largo, asientos, asp);
                    c.delante = Via.Pos.leer(p[4]);
                    c.detras = Via.Pos.leer(p[5]);
                    if (p.length <= 6) t.recolocar = true;
                    if (p.length > 9) {
                        c.fluido = p[7].isEmpty() ? null : p[7];
                        c.cubos = Integer.parseInt(p[8]);
                        carga.leer(c, p[9]);
                    }
                    if (p.length > 10 && !p[10].isEmpty()) c.combustible = Double.parseDouble(p[10]);
                    else if (esLoco) c.combustible = energia.maximo(tipos.loco(p[0]));
                    if (p.length > 12) { c.llave = p[11].isEmpty() ? null : p[11]; c.cerradura = Integer.parseInt(p[12]); }
                    if (esLoco && c.llave == null) c.llave = Llaves.codigoNuevo();
                    t.coches.add(c);
                }
                t.auto = s.getBoolean("auto", false);
                t.ruta.addAll(s.getIntegerList("ruta"));
                t.modo = s.getString("modo", "ida-vuelta");
                t.espera = s.getInt("espera", 30);
                t.tarifa = s.getDouble("tarifa", 0);
                t.idx = s.getInt("idx", -1);
                t.dir = s.getInt("dir", 1);
                t.facturaLuz = s.getDouble("luz-pagada", 0);
                t.recorrido = s.getDouble("recorrido", 0);
                if (!t.coches.isEmpty()) trenes.put(id, t);
            } catch (RuntimeException ex) {
                getLogger().warning("Tren " + id + " dañado en trenes.yml: " + ex.getMessage());
            }
        }
    }

    void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Tren t : trenes.values()) {
            y.set(t.id + ".dueno", t.dueno.toString());
            y.set(t.id + ".tipo", t.tipo == null ? "" : t.tipo);
            y.set(t.id + ".nombre", t.nombre);
            y.set(t.id + ".auto", t.auto);
            y.set(t.id + ".ruta", new ArrayList<>(t.ruta));
            y.set(t.id + ".modo", t.modo);
            y.set(t.id + ".espera", t.espera);
            y.set(t.id + ".tarifa", t.tarifa);
            y.set(t.id + ".idx", t.idx);
            y.set(t.id + ".dir", t.dir);
            y.set(t.id + ".luz-pagada", t.facturaLuz);
            y.set(t.id + ".recorrido", t.recorrido);
            List<String> coches = new ArrayList<>();
            for (Tren.Coche c : t.coches)
                coches.add(c.tipo + "|" + c.locomotora + "|" + c.largo + "|" + c.asientos + "|" + c.delante.guardar() + "|" + c.detras.guardar()
                        + "|" + c.aspecto + "|" + (c.fluido == null ? "" : c.fluido) + "|" + c.cubos + "|" + Carga.guardar(c) + "|" + c.combustible
                        + "|" + (c.llave == null ? "" : c.llave) + "|" + c.cerradura);
            y.set(t.id + ".coches", coches);
        }
        try { y.save(archivo); } catch (IOException ex) { getLogger().warning("No se pudo guardar trenes.yml: " + ex.getMessage()); }
    }

    // ganchos para que el jugador no quede subido a algo que ya no existe
    static void bajar(Entity e) { e.eject(); }
}
