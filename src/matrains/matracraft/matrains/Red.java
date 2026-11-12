package matracraft.matrains;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Red eléctrica: subestaciones (bloque de cobre especial con palanca) unidas por netherrack. Todo lo
 * conectado forma una red; con una palanca encendida, toda la red tiene luz. La red protege el cable,
 * las vías que van encima y un espacio de 3x3 alrededor: nadie ajeno rompe ni pone nada, y nada de
 * explosiones, pistones, fuego, líquidos, arena que cae, endermans ni árboles que crecen.
 */
final class Red implements Listener {

    static final class Sub {
        final String mundo;
        final int x, y, z;
        UUID dueno;
        String nombre;
        Sub(String mundo, int x, int y, int z, UUID dueno, String nombre) {
            this.mundo = mundo; this.x = x; this.y = y; this.z = z; this.dueno = dueno; this.nombre = nombre;
        }
        long clave() { return Red.clave(x, y, z); }
        Block bloque() { World w = Bukkit.getWorld(mundo); return w == null ? null : w.getBlockAt(x, y, z); }
    }

    static final class Linea {
        final String mundo;
        final Set<Long> cable = new HashSet<>();
        final List<Sub> subs = new ArrayList<>();
        boolean luz;
        Linea(String mundo) { this.mundo = mundo; }
        UUID dueno() { return subs.isEmpty() ? null : subs.get(0).dueno; }
        boolean esDueno(UUID u) { for (Sub s : subs) if (s.dueno.equals(u)) return true; return false; }
    }

    static final String[] NOMBRES = {"Subestación del Faro", "Central de la Matraca", "Subestación de Villarroble",
            "Transformador del Cerro", "Central de los Matracas", "Subestación del Molino", "Central de la Vega",
            "Subestación de la Ermita", "Central del Pantano", "Subestación de las Eras", "Central del Olivar",
            "Subestación del Cortijo", "Central de la Dehesa", "Subestación del Arroyo", "Central de la Cantera"};

    private final Matrains pl;
    private final NamespacedKey kSub;
    final Map<String, Map<Long, Sub>> subs = new HashMap<>();
    final List<Linea> lineas = new ArrayList<>();
    /** Por mundo: bloque de cable → línea, y bloque protegido → línea. */
    private final Map<String, Map<Long, Linea>> porCable = new HashMap<>();
    private final Map<String, Map<Long, Linea>> porZona = new HashMap<>();
    private File archivo;

    Red(Matrains pl) {
        this.pl = pl;
        this.kSub = new NamespacedKey(pl, "subestacion");
    }

    static long clave(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) (y + 2048) & 0xFFF);
    }

    static long clave(Block b) { return clave(b.getX(), b.getY(), b.getZ()); }

    private static int cx(long k) { return (int) (k >> 38); }
    private static int cz(long k) { return (int) (k << 26 >> 38); }
    private static int cy(long k) { return (int) (k & 0xFFF) - 2048; }

    // ------------------------------------------------------------------ consultas

    Linea lineaDeCable(Block b) {
        Map<Long, Linea> m = porCable.get(b.getWorld().getName());
        return m == null ? null : m.get(clave(b));
    }

    Linea zona(Block b) {
        Map<Long, Linea> m = porZona.get(b.getWorld().getName());
        return m == null ? null : m.get(clave(b));
    }

    /** ¿Tiene luz la vía de este bloque de raíl? (el netherrack va justo debajo) */
    boolean hayLuz(Block rail) {
        Linea l = lineaDeCable(rail.getRelative(BlockFace.DOWN));
        if (l == null && Via.sube(Via.forma(rail)) != null) l = lineaDeCable(rail.getRelative(BlockFace.DOWN, 2));
        return l != null && l.luz;
    }

    Sub sub(Block b) {
        Map<Long, Sub> m = subs.get(b.getWorld().getName());
        return m == null ? null : m.get(clave(b));
    }

    /** Subestación a la que va pegada esta palanca. */
    private Sub subDePalanca(Block palanca) {
        if (!(palanca.getBlockData() instanceof Switch sw)) return null;
        BlockFace pegada = switch (sw.getAttachedFace()) {
            case FLOOR -> BlockFace.DOWN;
            case CEILING -> BlockFace.UP;
            default -> sw.getFacing().getOppositeFace();
        };
        return sub(palanca.getRelative(pegada));
    }

    private boolean encendida(Sub s) {
        Block b = s.bloque();
        if (b == null) return false;
        for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block p = b.getRelative(f);
            if (p.getType() == Material.LEVER && p.getBlockData() instanceof Switch sw && sw.isPowered() && subDePalanca(p) == s) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ cálculo de la red

    /** Vuelve a calcular las líneas de estas subestaciones (y quita las viejas que las tocaban). */
    void recalcular(String mundo, Set<Sub> desde) {
        World w = Bukkit.getWorld(mundo);
        if (w == null) return;
        // fuera las líneas viejas de esas subestaciones
        for (Iterator<Linea> it = lineas.iterator(); it.hasNext(); ) {
            Linea l = it.next();
            if (!l.mundo.equals(mundo)) continue;
            boolean toca = false;
            for (Sub s : l.subs) if (desde.contains(s)) { toca = true; break; }
            if (toca) { desde.addAll(l.subs); quitarIndices(l); it.remove(); }
        }
        int max = pl.getConfig().getInt("red.max-cable", 1000);
        Set<Sub> hechas = new HashSet<>();
        for (Sub s : desde) {
            if (hechas.contains(s) || !subs.getOrDefault(mundo, Map.of()).containsKey(s.clave())) continue;
            Linea l = new Linea(mundo);
            ArrayDeque<long[]> cola = new ArrayDeque<>();
            Set<Long> vistos = new HashSet<>();
            cola.add(new long[]{s.x, s.y, s.z});
            vistos.add(s.clave());
            while (!cola.isEmpty()) {
                long[] p = cola.poll();
                int x = (int) p[0], y = (int) p[1], z = (int) p[2];
                Sub aqui = subs.get(mundo).get(clave(x, y, z));
                if (aqui != null) { l.subs.add(aqui); hechas.add(aqui); }
                else l.cable.add(clave(x, y, z));
                if (l.cable.size() >= max) break;
                for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                    int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                    long k = clave(nx, ny, nz);
                    if (vistos.contains(k) || !w.isChunkLoaded(nx >> 4, nz >> 4)) continue;
                    Block n = w.getBlockAt(nx, ny, nz);
                    if (n.getType() == Material.NETHERRACK || subs.get(mundo).containsKey(k)) { vistos.add(k); cola.add(new long[]{nx, ny, nz}); }
                }
            }
            l.luz = false;
            for (Sub x : l.subs) if (encendida(x)) { l.luz = true; break; }
            lineas.add(l);
            ponerIndices(l);
        }
        guardar();
    }

    private void ponerIndices(Linea l) {
        Map<Long, Linea> c = porCable.computeIfAbsent(l.mundo, k -> new HashMap<>());
        Map<Long, Linea> z = porZona.computeIfAbsent(l.mundo, k -> new HashMap<>());
        List<Long> todo = new ArrayList<>(l.cable);
        for (Sub s : l.subs) todo.add(s.clave());
        for (long k : l.cable) c.put(k, l);
        for (long k : todo) {
            int x = cx(k), y = cy(k), zz = cz(k);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++)
                z.putIfAbsent(clave(x + dx, y + dy, zz + dz), l);
        }
    }

    private void quitarIndices(Linea l) {
        Map<Long, Linea> c = porCable.get(l.mundo), z = porZona.get(l.mundo);
        if (c != null) c.values().removeIf(x -> x == l);
        if (z != null) z.values().removeIf(x -> x == l);
    }

    /** Subestaciones de las líneas que tocan este bloque (o sus vecinos). */
    private Set<Sub> afectadas(Block b) {
        Set<Sub> r = new HashSet<>();
        for (BlockFace f : new BlockFace[]{BlockFace.SELF, BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block n = b.getRelative(f);
            Linea l = lineaDeCable(n);
            if (l != null) r.addAll(l.subs);
            Sub s = sub(n);
            if (s != null) r.add(s);
        }
        return r;
    }

    // ------------------------------------------------------------------ subestaciones

    ItemStack objeto(int n) {
        ItemStack it = new ItemStack(Material.WAXED_COPPER_BLOCK, n);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.GOLD + "Subestación eléctrica");
        m.setLore(List.of(ChatColor.GRAY + "Ponla tocando el netherrack de tu línea", ChatColor.GRAY + "y pégale una palanca: encendida, la línea tiene luz."));
        m.getPersistentDataContainer().set(kSub, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean esObjeto(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kSub, PersistentDataType.BYTE);
    }

    private String nombreLibre() {
        Set<String> usados = new HashSet<>();
        for (Map<Long, Sub> m : subs.values()) for (Sub s : m.values()) usados.add(s.nombre);
        List<String> libres = new ArrayList<>();
        for (String n : NOMBRES) if (!usados.contains(n)) libres.add(n);
        if (!libres.isEmpty()) return libres.get(ThreadLocalRandom.current().nextInt(libres.size()));
        return NOMBRES[ThreadLocalRandom.current().nextInt(NOMBRES.length)] + " " + (usados.size() + 1);
    }

    int subsDe(UUID u) {
        int n = 0;
        for (Map<Long, Sub> m : subs.values()) for (Sub s : m.values()) if (s.dueno.equals(u)) n++;
        return n;
    }

    // ------------------------------------------------------------------ eventos: colocar y quitar

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alPoner(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();
        Linea z = zona(b);
        if (z != null && !p.hasPermission("matrains.admin")) {
            if (!z.esDueno(p.getUniqueId())) { e.setCancelled(true); p.sendMessage(ChatColor.RED + "Esta vía es de otro jugador."); return; }
            if (b.getState() instanceof org.bukkit.inventory.InventoryHolder || b.getType() == Material.SPAWNER) {
                e.setCancelled(true); p.sendMessage(ChatColor.RED + "Junto a la vía solo se decora: nada de cofres, hornos ni tolvas."); return;
            }
        }
        if (esObjeto(e.getItemInHand())) {
            if (!pl.getConfig().getStringList("mundos-permitidos").contains(b.getWorld().getName())) {
                e.setCancelled(true); p.sendMessage(ChatColor.RED + "En este mundo no hay trenes."); return;
            }
            int max = pl.getConfig().getInt("red.subestaciones-por-jugador", 8);
            if (subsDe(p.getUniqueId()) >= max && !p.hasPermission("matrains.admin")) {
                e.setCancelled(true); p.sendMessage(ChatColor.RED + "Ya tienes " + max + " subestaciones."); return;
            }
            Sub s = new Sub(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), p.getUniqueId(), nombreLibre());
            subs.computeIfAbsent(s.mundo, k -> new HashMap<>()).put(s.clave(), s);
            Set<Sub> d = afectadas(b);
            d.add(s);
            recalcular(s.mundo, d);
            Linea l = lineaDeCable(b) != null ? lineaDeCable(b) : lineaDe(s);
            p.sendMessage(ChatColor.GREEN + s.nombre + " instalada." + ChatColor.GRAY + " Cable conectado: "
                    + (l == null ? 0 : l.cable.size()) + " bloques de netherrack. Pégale una palanca para dar luz.");
            return;
        }
        if (b.getType() == Material.NETHERRACK) {
            Set<Sub> d = afectadas(b);
            if (!d.isEmpty()) Bukkit.getScheduler().runTask(pl, () -> recalcular(b.getWorld().getName(), d));
        }
    }

    Linea lineaDe(Sub s) {
        for (Linea l : lineas) if (l.subs.contains(s)) return l;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alRomper(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        Linea z = zona(b);
        boolean admin = p.hasPermission("matrains.admin");
        Sub s = sub(b);
        if (s != null) {
            if (!s.dueno.equals(p.getUniqueId()) && !admin) { e.setCancelled(true); p.sendMessage(ChatColor.RED + "Esta subestación es de otro jugador."); return; }
            e.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), objeto(1));
            subs.get(s.mundo).remove(s.clave());
            Set<Sub> d = new HashSet<>();
            Linea l = lineaDe(s);
            if (l != null) d.addAll(l.subs);
            d.remove(s);
            if (l != null) { quitarIndices(l); lineas.remove(l); }
            recalcular(s.mundo, d);
            p.sendMessage(ChatColor.YELLOW + s.nombre + " quitada.");
            return;
        }
        if (z == null) return;
        if (!z.esDueno(p.getUniqueId()) && !admin) { e.setCancelled(true); p.sendMessage(ChatColor.RED + "Esta vía es de otro jugador."); return; }
        if (b.getType() == Material.NETHERRACK && lineaDeCable(b) != null) {
            Set<Sub> d = new HashSet<>(z.subs);
            Bukkit.getScheduler().runTask(pl, () -> recalcular(b.getWorld().getName(), d));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alPalanca(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.LEVER) return;
        Sub s = subDePalanca(e.getClickedBlock());
        Player p = e.getPlayer();
        if (s == null) {
            // las palancas de los desvíos de una vía protegida solo las mueve su dueño
            Linea z = zona(e.getClickedBlock());
            if (z != null && !z.esDueno(p.getUniqueId()) && !p.hasPermission("matrains.admin")) {
                e.setCancelled(true); p.sendMessage(ChatColor.RED + "Las agujas de esta vía las mueve su dueño.");
            }
            return;
        }
        if (!s.dueno.equals(p.getUniqueId()) && !p.hasPermission("matrains.admin")) {
            e.setCancelled(true); p.sendMessage(ChatColor.RED + "Esta palanca es de " + s.nombre + ", de otro jugador."); return;
        }
        Bukkit.getScheduler().runTask(pl, () -> {
            Linea l = lineaDe(s);
            boolean antes = l != null && l.luz;
            Set<Sub> d = new HashSet<>();
            d.add(s);
            recalcular(s.mundo, d);
            Linea n = lineaDe(s);
            boolean ahora = n != null && n.luz;
            if (antes != ahora || n == null)
                p.sendMessage(ahora ? ChatColor.GREEN + "⚡ Línea con luz (" + n.cable.size() + " bloques de cable, " + n.subs.size() + " subestaciones)."
                        : ChatColor.YELLOW + "Línea sin luz.");
        });
    }

    // ------------------------------------------------------------------ eventos: protección del mundo

    @EventHandler(ignoreCancelled = true)
    public void alExplotar(EntityExplodeEvent e) { e.blockList().removeIf(b -> zona(b) != null || sub(b) != null); }

    @EventHandler(ignoreCancelled = true)
    public void alExplotarBloque(BlockExplodeEvent e) { e.blockList().removeIf(b -> zona(b) != null || sub(b) != null); }

    @EventHandler(ignoreCancelled = true)
    public void alPistonEmpuja(BlockPistonExtendEvent e) {
        if (zona(e.getBlock().getRelative(e.getDirection())) != null) { e.setCancelled(true); return; }
        for (Block b : e.getBlocks()) if (zona(b) != null || zona(b.getRelative(e.getDirection())) != null) { e.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void alPistonTira(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (zona(b) != null || zona(b.getRelative(e.getDirection())) != null) { e.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void alFluir(BlockFromToEvent e) { if (zona(e.getToBlock()) != null) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void alVaciarCubo(PlayerBucketEmptyEvent e) {
        if (zona(e.getBlock()) != null) { e.setCancelled(true); e.getPlayer().sendMessage(ChatColor.RED + "Nada de líquidos junto a la vía."); }
    }

    @EventHandler(ignoreCancelled = true)
    public void alLlenarCubo(PlayerBucketFillEvent e) {
        Linea z = zona(e.getBlock());
        if (z != null && !z.esDueno(e.getPlayer().getUniqueId()) && !e.getPlayer().hasPermission("matrains.admin")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void alPrender(BlockIgniteEvent e) {
        if (zona(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void alQuemar(BlockBurnEvent e) { if (zona(e.getBlock()) != null) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void alExtender(BlockSpreadEvent e) { if (zona(e.getBlock()) != null) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void alFormar(BlockFormEvent e) { if (zona(e.getBlock()) != null && e.getNewState().getType() != Material.SNOW) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void alDesvanecer(BlockFadeEvent e) { if (lineaDeCable(e.getBlock()) != null || sub(e.getBlock()) != null) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void alCambiarBloque(EntityChangeBlockEvent e) {
        // arena y grava que caen, endermans, ovejas, aldeanos...
        if (zona(e.getBlock()) != null && !(e.getEntity() instanceof Player)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void alCrecer(StructureGrowEvent e) { e.getBlocks().removeIf(s -> zona(s.getBlock()) != null); }

    @EventHandler(ignoreCancelled = true)
    public void alColocarEntidad(EntityPlaceEvent e) {
        if (zona(e.getBlock()) == null) return;
        EntityType t = e.getEntityType();
        String n = t.name();
        if (n.contains("MINECART") || n.contains("BOAT") || n.contains("RAFT") || t == EntityType.ARMOR_STAND || t == EntityType.END_CRYSTAL) {
            e.setCancelled(true);
            if (e.getPlayer() != null) e.getPlayer().sendMessage(ChatColor.RED + "En esta vía solo circulan trenes.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void alCrearVehiculo(VehicleCreateEvent e) {
        if (zona(e.getVehicle().getLocation().getBlock()) != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ guardado

    void cargar() {
        archivo = new File(pl.getDataFolder(), "redes.yml");
        subs.clear();
        lineas.clear();
        porCable.clear();
        porZona.clear();
        if (!archivo.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        ConfigurationSection ss = y.getConfigurationSection("subestaciones");
        Map<String, Sub> porId = new HashMap<>();
        if (ss != null) for (String id : ss.getKeys(false)) {
            ConfigurationSection s = ss.getConfigurationSection(id);
            Sub sub = new Sub(s.getString("mundo"), s.getInt("x"), s.getInt("y"), s.getInt("z"), UUID.fromString(s.getString("dueno")), s.getString("nombre"));
            subs.computeIfAbsent(sub.mundo, k -> new HashMap<>()).put(sub.clave(), sub);
            porId.put(id, sub);
        }
        for (Map<?, ?> m : y.getMapList("lineas")) {
            Linea l = new Linea(String.valueOf(m.get("mundo")));
            l.luz = Boolean.TRUE.equals(m.get("luz"));
            for (Object o : (List<?>) m.get("subs")) { Sub s = porId.get(String.valueOf(o)); if (s != null) l.subs.add(s); }
            String cable = String.valueOf(m.get("cable"));
            if (!cable.isEmpty()) for (String k : cable.split(",")) l.cable.add(Long.parseLong(k, 36));
            if (l.subs.isEmpty()) continue;
            lineas.add(l);
            ponerIndices(l);
        }
    }

    void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        Map<Sub, String> ids = new HashMap<>();
        int i = 0;
        for (Map<Long, Sub> m : subs.values()) for (Sub s : m.values()) {
            String id = "s" + (i++);
            ids.put(s, id);
            y.set("subestaciones." + id + ".mundo", s.mundo);
            y.set("subestaciones." + id + ".x", s.x);
            y.set("subestaciones." + id + ".y", s.y);
            y.set("subestaciones." + id + ".z", s.z);
            y.set("subestaciones." + id + ".dueno", s.dueno.toString());
            y.set("subestaciones." + id + ".nombre", s.nombre);
        }
        List<Map<String, Object>> ls = new ArrayList<>();
        for (Linea l : lineas) {
            Map<String, Object> m = new HashMap<>();
            m.put("mundo", l.mundo);
            m.put("luz", l.luz);
            List<String> s = new ArrayList<>();
            for (Sub x : l.subs) if (ids.containsKey(x)) s.add(ids.get(x));
            m.put("subs", s);
            StringBuilder sb = new StringBuilder();
            for (long k : l.cable) { if (sb.length() > 0) sb.append(','); sb.append(Long.toString(k, 36)); }
            m.put("cable", sb.toString());
            ls.add(m);
        }
        y.set("lineas", ls);
        try { y.save(archivo); } catch (IOException ex) { pl.getLogger().warning("No se pudo guardar redes.yml: " + ex.getMessage()); }
    }
}
