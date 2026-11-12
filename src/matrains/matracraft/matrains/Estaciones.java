package matracraft.matrains;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Estaciones: se compra una campana de estación, se coloca junto a la vía y su menú marca el área
 * (8x8 a 32x32) con partículas antes de confirmar. Dentro solo construye el dueño; los demás entran,
 * suben y bajan del tren y usan puertas y asientos. Un cartel con [Salidas] en la primera línea se
 * convierte en panel de salidas.
 */
final class Estaciones implements Listener {

    static final class Estacion {
        final int id;
        String nombre;
        UUID dueno;
        final String mundo;
        final int bx, by, bz;
        int lado;
        final List<String> carteles = new ArrayList<>();

        Estacion(int id, String nombre, UUID dueno, String mundo, int bx, int by, int bz, int lado) {
            this.id = id; this.nombre = nombre; this.dueno = dueno; this.mundo = mundo; this.bx = bx; this.by = by; this.bz = bz; this.lado = lado;
        }

        String codigo() { return String.format("EST-%04d", id); }
        int x1() { return bx - lado / 2; }
        int x2() { return bx - lado / 2 + lado - 1; }
        int z1() { return bz - lado / 2; }
        int z2() { return bz - lado / 2 + lado - 1; }
        int y1() { return by - 4; }
        int y2() { return by + 12; }

        boolean dentro(String m, double x, double y, double z) {
            return m.equals(mundo) && x >= x1() && x < x2() + 1 && z >= z1() && z < z2() + 1 && y >= y1() && y < y2() + 1;
        }

        boolean dentro(Block b) { return dentro(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }

        Location campana() { World w = Bukkit.getWorld(mundo); return w == null ? null : new Location(w, bx + 0.5, by + 0.5, bz + 0.5); }
    }

    static final String[] NOMBRES = {"Estación del Faro", "Apeadero de la Matraca", "Estación de Villarroble", "Estación del Cerro",
            "Apeadero del Molino", "Estación de la Vega", "Estación de la Ermita", "Apeadero del Pantano", "Estación de las Eras",
            "Estación del Olivar", "Apeadero del Cortijo", "Estación de la Dehesa", "Estación del Arroyo", "Apeadero de la Cantera",
            "Estación Central Matraca", "Estación de la Mina", "Apeadero de los Naranjos", "Estación del Puerto"};

    private final Matrains pl;
    private final NamespacedKey kCampana;
    final Map<Integer, Estacion> todas = new LinkedHashMap<>();
    private int siguiente = 1;
    /** Campanas puestas cuya estación aún no se ha confirmado (mundo,x,y,z). */
    private final Map<String, UUID> pendientes = new java.util.HashMap<>();

    private static String k(Block b) { return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ(); }
    private File archivo;

    Estaciones(Matrains pl) {
        this.pl = pl;
        this.kCampana = new NamespacedKey(pl, "campana");
    }

    // ------------------------------------------------------------------ consultas

    Estacion en(Block b) {
        for (Estacion e : todas.values()) if (e.dentro(b)) return e;
        return null;
    }

    Estacion en(Location l) {
        for (Estacion e : todas.values()) if (e.dentro(l.getWorld().getName(), l.getX(), l.getY(), l.getZ())) return e;
        return null;
    }

    Estacion porCampana(Block b) {
        for (Estacion e : todas.values())
            if (e.mundo.equals(b.getWorld().getName()) && e.bx == b.getX() && e.by == b.getY() && e.bz == b.getZ()) return e;
        return null;
    }

    int de(UUID u) {
        int n = 0;
        for (Estacion e : todas.values()) if (e.dueno.equals(u)) n++;
        return n;
    }

    // ------------------------------------------------------------------ objeto

    ItemStack objeto(int n) {
        ItemStack it = new ItemStack(Material.BELL, n);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.GOLD + "Campana de estación");
        m.setLore(List.of(ChatColor.GRAY + "Colócala junto a la vía y elige", ChatColor.GRAY + "el tamaño de la estación en su menú."));
        m.getPersistentDataContainer().set(kCampana, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean esObjeto(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kCampana, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ crear

    /** ¿Se puede poner una estación con esta campana y este lado? Devuelve el motivo si no. */
    private String problema(Player p, Block campana, int lado, Estacion ignorar) {
        Estacion prueba = new Estacion(0, "", p.getUniqueId(), campana.getWorld().getName(), campana.getX(), campana.getY(), campana.getZ(), lado);
        for (Estacion e : todas.values()) {
            if (e == ignorar || !e.mundo.equals(prueba.mundo)) continue;
            if (prueba.x1() <= e.x2() && prueba.x2() >= e.x1() && prueba.z1() <= e.z2() && prueba.z2() >= e.z1()
                    && prueba.y1() <= e.y2() && prueba.y2() >= e.y1()) return "Se solapa con " + e.nombre + ".";
        }
        if (p.hasPermission("matrains.admin")) return null;
        try {
            var gp = me.ryanhamshire.GriefPrevention.GriefPrevention.instance;
            if (gp != null) for (var c : gp.dataStore.getClaims()) {
                Location a = c.getLesserBoundaryCorner(), b = c.getGreaterBoundaryCorner();
                if (a.getWorld() == null || !a.getWorld().getName().equals(prueba.mundo)) continue;
                if (prueba.x1() <= b.getBlockX() && prueba.x2() >= a.getBlockX() && prueba.z1() <= b.getBlockZ() && prueba.z2() >= a.getBlockZ()
                        && c.checkPermission(p, me.ryanhamshire.GriefPrevention.ClaimPermission.Build, null) != null)
                    return "Pisa un terreno protegido de otro jugador.";
            }
        } catch (NoClassDefFoundError ignorado) { }
        return null;
    }

    private void bordes(Player p, Block campana, int lado) {
        Estacion e = new Estacion(0, "", p.getUniqueId(), campana.getWorld().getName(), campana.getX(), campana.getY(), campana.getZ(), lado);
        Particle.DustOptions d = new Particle.DustOptions(Color.fromRGB(255, 190, 40), 1.4f);
        World w = campana.getWorld();
        double y = campana.getY() + 0.5;
        for (int i = 0; i <= lado; i += 1) {
            w.spawnParticle(Particle.DUST, e.x1() + i, y, e.z1(), 1, d);
            w.spawnParticle(Particle.DUST, e.x1() + i, y, e.z2() + 1, 1, d);
            w.spawnParticle(Particle.DUST, e.x1(), y, e.z1() + i, 1, d);
            w.spawnParticle(Particle.DUST, e.x2() + 1, y, e.z1() + i, 1, d);
        }
    }

    private void menuCrear(Player p, Block campana, int lado) {
        String prob = problema(p, campana, lado, null);
        Gui g = new Gui(3, "Nueva estación");
        g.poner(10, Gui.boton(Material.RED_CONCRETE, ChatColor.RED + "Más pequeña", ChatColor.GRAY + "Ahora " + lado + "x" + lado), (j, c) -> {
            if (lado > 8) menuCrear(j, campana, lado - 4);
        });
        g.poner(13, Gui.boton(Material.BELL, ChatColor.GOLD + "Estación de " + lado + "x" + lado,
                ChatColor.GRAY + "Desde 4 bloques bajo la campana hasta 12 por encima.",
                ChatColor.GRAY + "Las partículas marcan el borde.",
                prob == null ? ChatColor.GREEN + "Se puede crear." : ChatColor.RED + prob), null);
        g.poner(16, Gui.boton(Material.LIME_CONCRETE, ChatColor.GREEN + "Más grande", ChatColor.GRAY + "Ahora " + lado + "x" + lado), (j, c) -> {
            if (lado < 32) menuCrear(j, campana, lado + 4);
        });
        g.poner(21, Gui.boton(Material.BARRIER, ChatColor.RED + "Cancelar", ChatColor.GRAY + "Quita la campana y te la devuelve."), (j, c) -> {
            j.closeInventory();
            pendientes.remove(k(campana));
            if (campana.getType() == Material.BELL && porCampana(campana) == null) {
                campana.setType(Material.AIR);
                j.getInventory().addItem(objeto(1)).values().forEach(r -> j.getWorld().dropItem(j.getLocation(), r));
            }
        });
        g.poner(23, Gui.boton(prob == null ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE, prob == null ? ChatColor.GREEN + "Crear estación" : ChatColor.GRAY + "No se puede crear",
                prob == null ? ChatColor.GRAY + "Le pondré un nombre de Matracraft (luego lo cambias)." : ChatColor.RED + prob), (j, c) -> {
            String pr = problema(j, campana, lado, null);
            if (pr != null) { j.sendMessage(ChatColor.RED + pr); return; }
            if (campana.getType() != Material.BELL) { j.closeInventory(); return; }
            Estacion e = new Estacion(siguiente++, nombreLibre(), j.getUniqueId(), campana.getWorld().getName(),
                    campana.getX(), campana.getY(), campana.getZ(), lado);
            todas.put(e.id, e);
            pendientes.remove(k(campana));
            guardar();
            j.closeInventory();
            j.playSound(j.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1f, 1f);
            j.sendMessage(ChatColor.GREEN + "🚉 " + e.nombre + " (" + e.codigo() + ") creada. " + ChatColor.GRAY
                    + "Clic derecho en la campana para su menú. Un cartel con [Salidas] en la primera línea hace de panel.");
        });
        g.abrir(p);
        bordes(p, campana, lado);
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) bordes(p, campana, lado); }, 20L);
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) bordes(p, campana, lado); }, 40L);
    }

    private String nombreLibre() {
        Set<String> usados = new HashSet<>();
        for (Estacion e : todas.values()) usados.add(e.nombre);
        List<String> libres = new ArrayList<>();
        for (String n : NOMBRES) if (!usados.contains(n)) libres.add(n);
        if (!libres.isEmpty()) return libres.get(ThreadLocalRandom.current().nextInt(libres.size()));
        return "Estación " + siguiente;
    }

    /** Menú de una estación ya creada (el dueño la gestiona; los demás ven la información). */
    void menu(Player p, Estacion e) {
        boolean dueno = e.dueno.equals(p.getUniqueId()) || p.hasPermission("matrains.admin");
        Gui g = new Gui(3, e.nombre);
        List<String> trenes = new ArrayList<>();
        for (Tren t : pl.trenes.values()) if (t.ruta.contains(e.id)) trenes.add(ChatColor.GRAY + "· " + t.nombre + (t.auto ? ChatColor.GREEN + " (automático)" : ""));
        if (trenes.isEmpty()) trenes.add(ChatColor.GRAY + "Ningún tren tiene ruta por aquí.");
        g.poner(4, Gui.boton(Material.BELL, ChatColor.YELLOW + e.nombre, ChatColor.GRAY + "Código: " + ChatColor.WHITE + e.codigo(),
                ChatColor.GRAY + "Dueño: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(e.dueno).getName(),
                ChatColor.GRAY + "Coordenadas: " + ChatColor.WHITE + e.bx + ", " + e.by + ", " + e.bz,
                ChatColor.GRAY + "Tamaño: " + ChatColor.WHITE + e.lado + "x" + e.lado), null);
        g.poner(11, Gui.boton(Material.MINECART, ChatColor.YELLOW + "Trenes que paran aquí", trenes), null);
        Red.Linea l = null;
        for (Red.Linea x : pl.red.lineas) if (x.mundo.equals(e.mundo)) for (long k : x.cable) {
            int cx = (int) (k >> 38), cz = (int) (k << 26 >> 38);
            if (cx >= e.x1() && cx <= e.x2() && cz >= e.z1() && cz <= e.z2()) { l = x; break; }
        }
        g.poner(13, Gui.boton(Material.REDSTONE, ChatColor.YELLOW + "Estado eléctrico",
                l == null ? ChatColor.GRAY + "Sin línea eléctrica." : (l.luz ? ChatColor.GREEN + "Conectada a red con luz." : ChatColor.RED + "Conectada a red sin luz."),
                l == null ? "" : ChatColor.GRAY + "Subestación: " + l.subs.get(0).nombre), null);
        if (dueno) {
            g.poner(15, Gui.boton(Material.NAME_TAG, ChatColor.YELLOW + "Cambiar el nombre", ChatColor.GRAY + "Escribe el nombre nuevo en el chat."), (j, c) -> {
                j.closeInventory();
                pl.pedirTexto(j, "Escribe el nombre de la estación (o «cancelar»):", texto -> {
                    e.nombre = texto.length() > 32 ? texto.substring(0, 32) : texto;
                    guardar();
                    j.sendMessage(ChatColor.GREEN + "La estación se llama ahora " + e.nombre + ".");
                });
            });
            g.poner(22, Gui.boton(Material.BARRIER, ChatColor.RED + "Quitar la estación", ChatColor.GRAY + "Mayúsculas + clic para confirmar.",
                    ChatColor.GRAY + "Te devuelve la campana."), (j, c) -> {
                if (c != org.bukkit.event.inventory.ClickType.SHIFT_LEFT && c != org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                    j.sendMessage(ChatColor.GRAY + "Mayúsculas + clic para confirmar."); return;
                }
                quitar(e);
                j.closeInventory();
                Location cl = e.campana();
                if (cl != null && cl.getBlock().getType() == Material.BELL) cl.getBlock().setType(Material.AIR);
                j.getInventory().addItem(objeto(1)).values().forEach(r -> j.getWorld().dropItem(j.getLocation(), r));
                j.sendMessage(ChatColor.YELLOW + e.nombre + " quitada.");
            });
        }
        g.abrir(p);
    }

    void quitar(Estacion e) {
        todas.remove(e.id);
        for (Tren t : pl.trenes.values()) t.ruta.removeIf(x -> x == e.id);
        guardar();
        pl.guardar();
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alPoner(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();
        Estacion est = en(b);
        if (est != null && !est.dueno.equals(p.getUniqueId()) && !p.hasPermission("matrains.admin")) {
            e.setCancelled(true); p.sendMessage(ChatColor.RED + est.nombre + " es de otro jugador."); return;
        }
        if (!esObjeto(e.getItemInHand())) return;
        if (!pl.getConfig().getStringList("mundos-permitidos").contains(b.getWorld().getName())) {
            e.setCancelled(true); p.sendMessage(ChatColor.RED + "En este mundo no hay trenes."); return;
        }
        int max = pl.getConfig().getInt("estaciones.por-jugador", 5);
        if (de(p.getUniqueId()) >= max && !p.hasPermission("matrains.admin")) {
            e.setCancelled(true); p.sendMessage(ChatColor.RED + "Ya tienes " + max + " estaciones."); return;
        }
        pendientes.put(k(b), p.getUniqueId());
        guardar();
        Bukkit.getScheduler().runTask(pl, () -> menuCrear(p, b, pl.getConfig().getInt("estaciones.lado", 16)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alRomper(BlockBreakEvent e) {
        Player p = e.getPlayer();
        UUID pendiente = pendientes.get(k(e.getBlock()));
        if (pendiente != null && !pendiente.equals(p.getUniqueId()) && !p.hasPermission("matrains.admin")) {
            e.setCancelled(true); p.sendMessage(ChatColor.RED + "Esa campana es de otro jugador."); return;
        }
        if (pendientes.remove(k(e.getBlock())) != null) {
            guardar();
            // campana sin confirmar: se devuelve la de estación
            e.setDropItems(false);
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), objeto(1));
            return;
        }
        Estacion est = en(e.getBlock());
        if (est == null) return;
        boolean dueno = est.dueno.equals(p.getUniqueId()) || p.hasPermission("matrains.admin");
        if (!dueno) { e.setCancelled(true); p.sendMessage(ChatColor.RED + est.nombre + " es de otro jugador."); return; }
        if (porCampana(e.getBlock()) == est) { e.setCancelled(true); p.sendMessage(ChatColor.GRAY + "Para quitar la estación usa el menú de la campana."); }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alTocar(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (b.getType() != Material.BELL) return;
        Estacion est = porCampana(b);
        if (est == null && e.getPlayer().getUniqueId().equals(pendientes.get(k(b))) && !e.getPlayer().isSneaking()) {
            e.setCancelled(true);
            menuCrear(e.getPlayer(), b, pl.getConfig().getInt("estaciones.lado", 16));
            return;
        }
        if (est == null) return;
        if (e.getPlayer().isSneaking()) return; // con mayúsculas suena la campana
        e.setCancelled(true);
        menu(e.getPlayer(), est);
    }

    @EventHandler(ignoreCancelled = true)
    public void alVaciarCubo(PlayerBucketEmptyEvent e) {
        Estacion est = en(e.getBlock());
        if (est != null && !est.dueno.equals(e.getPlayer().getUniqueId()) && !e.getPlayer().hasPermission("matrains.admin")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void alExplotar(EntityExplodeEvent e) { e.blockList().removeIf(b -> en(b) != null); }

    @EventHandler(ignoreCancelled = true)
    public void alExplotarBloque(BlockExplodeEvent e) { e.blockList().removeIf(b -> en(b) != null); }

    @EventHandler(ignoreCancelled = true)
    public void alPiston(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (en(b) != null || en(b.getRelative(e.getDirection())) != null) { e.setCancelled(true); return; }
        if (en(e.getBlock().getRelative(e.getDirection())) != null && en(e.getBlock()) == null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void alPistonTira(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (en(b) != null && en(e.getBlock()) == null) { e.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void alCartel(SignChangeEvent e) {
        String l = e.line(0) == null ? "" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.line(0));
        if (!l.trim().equalsIgnoreCase("[Salidas]")) return;
        Estacion est = en(e.getBlock());
        if (est == null) { e.getPlayer().sendMessage(ChatColor.RED + "El panel de salidas tiene que estar dentro de una estación."); return; }
        String k = e.getBlock().getX() + "," + e.getBlock().getY() + "," + e.getBlock().getZ();
        if (!est.carteles.contains(k)) est.carteles.add(k);
        guardar();
        e.getPlayer().sendMessage(ChatColor.GREEN + "Panel de salidas de " + est.nombre + ".");
        Bukkit.getScheduler().runTaskLater(pl, () -> paneles(est), 2L);
    }

    /** Actualiza los carteles [Salidas] de las estaciones con jugadores cerca. */
    void paneles() {
        for (Estacion e : todas.values()) {
            if (e.carteles.isEmpty()) continue;
            World w = Bukkit.getWorld(e.mundo);
            if (w == null || !w.isChunkLoaded(e.bx >> 4, e.bz >> 4)) continue;
            paneles(e);
        }
    }

    private void paneles(Estacion e) {
        World w = Bukkit.getWorld(e.mundo);
        if (w == null) return;
        List<String> lineas = new ArrayList<>();
        for (Tren t : pl.trenes.values()) {
            if (!t.auto || !t.ruta.contains(e.id)) continue;
            String cuando = t.cuandoLlega(e.id);
            lineas.add(recortar(t.nombre, 9) + " " + cuando);
        }
        for (java.util.Iterator<String> it = e.carteles.iterator(); it.hasNext(); ) {
            String[] k = it.next().split(",");
            Block b = w.getBlockAt(Integer.parseInt(k[0]), Integer.parseInt(k[1]), Integer.parseInt(k[2]));
            if (!(b.getState() instanceof Sign s)) { it.remove(); continue; }
            var lado = s.getSide(Side.FRONT);
            lado.setLine(0, ChatColor.DARK_BLUE + "" + ChatColor.BOLD + recortar(e.nombre, 15));
            for (int i = 0; i < 3; i++) lado.setLine(i + 1, i < lineas.size() ? lineas.get(i) : (i == 0 && lineas.isEmpty() ? "Sin servicio" : ""));
            s.update(false, false);
        }
    }

    private static String recortar(String s, int n) { return s.length() <= n ? s : s.substring(0, n); }

    // ------------------------------------------------------------------ guardado

    void cargar() {
        archivo = new File(pl.getDataFolder(), "estaciones.yml");
        todas.clear();
        if (!archivo.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        siguiente = y.getInt("siguiente", 1);
        ConfigurationSection pe = y.getConfigurationSection("pendientes");
        if (pe != null) for (String x : pe.getKeys(false)) pendientes.put(x.replace(';', ','), UUID.fromString(pe.getString(x)));
        ConfigurationSection s = y.getConfigurationSection("estaciones");
        if (s != null) for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            Estacion e = new Estacion(Integer.parseInt(k), c.getString("nombre"), UUID.fromString(c.getString("dueno")), c.getString("mundo"),
                    c.getInt("x"), c.getInt("y"), c.getInt("z"), c.getInt("lado", 16));
            e.carteles.addAll(c.getStringList("carteles"));
            todas.put(e.id, e);
            siguiente = Math.max(siguiente, e.id + 1);
        }
    }

    void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("siguiente", siguiente);
        for (Map.Entry<String, UUID> en : pendientes.entrySet()) y.set("pendientes." + en.getKey().replace(',', ';'), en.getValue().toString());
        for (Estacion e : todas.values()) {
            String b = "estaciones." + e.id + ".";
            y.set(b + "nombre", e.nombre);
            y.set(b + "dueno", e.dueno.toString());
            y.set(b + "mundo", e.mundo);
            y.set(b + "x", e.bx);
            y.set(b + "y", e.by);
            y.set(b + "z", e.bz);
            y.set(b + "lado", e.lado);
            y.set(b + "carteles", e.carteles);
        }
        try { y.save(archivo); } catch (IOException ex) { pl.getLogger().warning("No se pudo guardar estaciones.yml: " + ex.getMessage()); }
    }
}
