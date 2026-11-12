package matracraft.coches;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Material;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Gasolineras: bloques-surtidor donde los coches repostan pagando con Vault. */
final class Gasolineras implements Listener {

    private static final class Sesion {
        final BossBar barra;
        double litros = 0, pagado = 0, pendiente = 0;
        int ticks = 0;
        boolean gratis = false;
        String nombre = "Gasolinera", clave = "";
        Sesion(BossBar barra) { this.barra = barra; }
    }

    private final Coches pl;
    private final File archivo;
    private final Map<String, String> surtidores = new TreeMap<>();
    // claves de los bloques de gasolineras náuticas (en el fondo, bajo el agua)
    private final java.util.Set<String> nauticas = new java.util.HashSet<>();

    private static final List<String> NAUTICOS = List.of("Surtidor del Faro", "Marina Matraca", "Varadero Norte", "Muelle Viejo",
            "Gasóleos La Tartana", "Puerto Chatarrero", "Combustibles El Faro", "Náutica Ⓜ");

    private static final List<String> MARCAS = List.of("Repsol", "Cepsa", "Moeve", "BP", "Galp", "Shell", "Petroprix",
            "Ballenoil", "Plenoil", "Petronor", "Avia", "Esclatoil", "Disa", "GasExpress", "Carrefour", "Alcampo");
    private static final List<String> PUEBLOS = List.of("Villarroble", "Valdecastro", "Fuentebella", "Castilmora",
            "Sotoalto", "La Ribera", "Riofrío", "Cantalobos", "Valdeolmos", "San Millán", "El Vado");

    private final Map<UUID, Sesion> sesiones = new HashMap<>();

    Gasolineras(Coches pl) {
        this.pl = pl;
        this.archivo = new File(pl.getDataFolder(), "gasolineras.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(archivo);
        ConfigurationSection sec = yml.getConfigurationSection("surtidores");
        if (sec != null) for (String k : sec.getKeys(false)) surtidores.put(k.replace(";", ":"), sec.getString(k, "Gasolinera"));
        else for (String k : yml.getStringList("surtidores")) surtidores.put(k, "Gasolinera"); // formato antiguo: solo claves
        for (String k : yml.getStringList("nauticas")) nauticas.add(k.replace(";", ":"));
    }

    private void guardar() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, String> e : surtidores.entrySet()) yml.set("surtidores." + e.getKey().replace(":", ";"), e.getValue());
        List<String> lista = new ArrayList<>();
        for (String k : nauticas) lista.add(k.replace(":", ";"));
        yml.set("nauticas", lista);
        try { yml.save(archivo); } catch (IOException ex) { pl.getLogger().warning("No pude guardar gasolineras.yml"); }
    }

    Map<String, String> lista() { return Collections.unmodifiableMap(surtidores); }

    boolean esSurtidor(Block b) { return surtidores.containsKey(Coches.clave(b)); }

    String nombre(Block b) { return surtidores.getOrDefault(Coches.clave(b), "Gasolinera"); }

    boolean esNautica(Block b) { return nauticas.contains(Coches.clave(b)); }

    /**
     * Surtidor sobre el que está parado el vehículo, o null. Coches: el bloque de debajo.
     * Embarcaciones: el fondo, hasta 3 bloques por debajo de la superficie (así valen las de 1 de profundidad).
     */
    Block surtidorBajo(Location loc, boolean agua) {
        Block b = loc.getBlock().getRelative(BlockFace.DOWN);
        if (!agua) return esSurtidor(b) && !esNautica(b) ? b : null;
        Block aqui = loc.getBlock();
        for (int i = 0; i <= 3; i++) {
            Block f = aqui.getRelative(0, -i, 0);
            if (esSurtidor(f) && esNautica(f)) return f;
            if (!Agua.esAgua(f) && i > 0) break;
        }
        return null;
    }

    /** Precio del litro en Ⓜ en ese surtidor (la náutica, un poco más cara). */
    double precioEn(Block surtidor) {
        double precio = pl.precio.dolares();
        if (surtidor != null && esNautica(surtidor))
            precio += pl.getConfig().getDouble("gasolina.suplemento-nautico-euros", 0.15) * pl.getConfig().getDouble("gasolina.euros-a-dolares", 2.0);
        return precio;
    }

    /** Bloques de la misma gasolinera que 'clave': los contiguos (a la misma altura) con el mismo nombre. */
    private List<String> estacion(String clave) {
        List<String> claves = new ArrayList<>();
        String nombre = surtidores.get(clave);
        if (nombre == null) return claves;
        java.util.ArrayDeque<String> cola = new java.util.ArrayDeque<>(List.of(clave));
        java.util.Set<String> vistos = new java.util.HashSet<>(cola);
        while (!cola.isEmpty() && claves.size() < 25) {
            String k = cola.poll();
            claves.add(k);
            String[] c = k.split(":");
            int x = Integer.parseInt(c[1]), y = Integer.parseInt(c[2]), z = Integer.parseInt(c[3]);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                String vecino = c[0] + ":" + (x + d[0]) + ":" + y + ":" + (z + d[1]);
                if (vistos.add(vecino) && nombre.equals(surtidores.get(vecino))) cola.add(vecino);
            }
        }
        return claves;
    }

    private boolean nombreUsado(String nombre) {
        for (String n : surtidores.values()) if (ChatColor.stripColor(n).equalsIgnoreCase(ChatColor.stripColor(nombre))) return true;
        return false;
    }

    /** Borra la gasolinera a la que pertenece ese bloque (solo esa, aunque otra se llame igual). */
    private void borrarEstacion(String clave) {
        for (String k : estacion(clave)) {
            surtidores.remove(k);
            nauticas.remove(k);
            Block b = bloqueDe(k);
            if (b != null) quitarCartel(b);
        }
        guardar();
    }

    /**
     * Rectángulo del mismo bloque alrededor de b (en la misma altura). Devuelve {x0, z0, x1, z1},
     * o null si no es un rectángulo macizo.
     */
    private static int[] rectangulo(Block b) {
        Material m = b.getType();
        int x0 = b.getX(), x1 = b.getX(), z0 = b.getZ(), z1 = b.getZ();
        while (x0 - 1 > b.getX() - 6 && b.getWorld().getBlockAt(x0 - 1, b.getY(), b.getZ()).getType() == m) x0--;
        while (x1 + 1 < b.getX() + 6 && b.getWorld().getBlockAt(x1 + 1, b.getY(), b.getZ()).getType() == m) x1++;
        while (z0 - 1 > b.getZ() - 6 && b.getWorld().getBlockAt(b.getX(), b.getY(), z0 - 1).getType() == m) z0--;
        while (z1 + 1 < b.getZ() + 6 && b.getWorld().getBlockAt(b.getX(), b.getY(), z1 + 1).getType() == m) z1++;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                if (b.getWorld().getBlockAt(x, b.getY(), z).getType() != m) return null;
        return new int[]{x0, z0, x1, z1};
    }

    private String nombreNautico() {
        List<String> libres = new ArrayList<>(NAUTICOS);
        libres.removeAll(surtidores.values());
        if (!libres.isEmpty()) return libres.get(ThreadLocalRandom.current().nextInt(libres.size()));
        return "Gasolinera náutica " + (nauticas.size() + 1);
    }

    /** Un nombre real de la zona que no esté usado; si no, marca + pueblo inventados. */
    private String nombreAleatorio() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<String> reales = new ArrayList<>(pl.precio.nombresCercanos());
        reales.removeAll(surtidores.values());
        if (!reales.isEmpty()) return reales.get(r.nextInt(reales.size()));
        for (int i = 0; i < 50; i++) {
            String n = MARCAS.get(r.nextInt(MARCAS.size())) + " · " + PUEBLOS.get(r.nextInt(PUEBLOS.size()));
            if (!surtidores.containsValue(n)) return n;
        }
        return "Gasolinera " + (surtidores.size() + 1);
    }

    /** Clave del surtidor cuyo nombre (sin colores) coincide, o null. */
    private String buscarPorNombre(String nombre) {
        for (Map.Entry<String, String> e : surtidores.entrySet()) {
            if (ChatColor.stripColor(e.getValue()).equalsIgnoreCase(nombre)) return e.getKey();
        }
        return null;
    }

    private static Block bloqueDe(String clave) {
        String[] p = clave.split(":");
        World w = Bukkit.getWorld(p[0]);
        return w == null ? null : w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    /** /gasolinera crear|borrar|renombrar|lista|ir|precio */
    void comando(CommandSender sender, String[] args) {
        if (!sender.hasPermission("coches.admin")) { sender.sendMessage(ChatColor.RED + "No tienes permiso."); return; }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String nombre = args.length > 1 ? ChatColor.translateAlternateColorCodes('&', String.join(" ", Arrays.copyOfRange(args, 1, args.length))) : null;
        switch (sub) {
            case "lista": {
                Map<String, List<String>> estaciones = new java.util.LinkedHashMap<>();
                java.util.Set<String> hechas = new java.util.HashSet<>();
                for (String k : surtidores.keySet()) {
                    if (hechas.contains(k)) continue;
                    List<String> grupo = estacion(k);
                    hechas.addAll(grupo);
                    String n = surtidores.get(k);
                    while (estaciones.containsKey(n)) n = n + " ";
                    estaciones.put(n, grupo);
                }
                sender.sendMessage(ChatColor.GOLD + "⛽ Gasolineras (" + estaciones.size() + "):");
                for (Map.Entry<String, List<String>> e : estaciones.entrySet()) {
                    String[] c = e.getValue().get(0).split(":");
                    boolean nautica = nauticas.contains(e.getValue().get(0));
                    sender.sendMessage(ChatColor.YELLOW + " " + e.getKey() + ChatColor.GRAY + "  (" + c[0] + " " + c[1] + " " + c[2] + " " + c[3] + ") · "
                            + e.getValue().size() + " bloques" + (nautica ? ChatColor.AQUA + " · náutica" : "")
                            + (e.getValue().size() < 4 ? ChatColor.RED + " · antigua, rehazla de 2x2 a 5x5" : ""));
                }
                return;
            }
            case "ir": {
                if (!(sender instanceof Player p) || nombre == null) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /gasolinera ir <nombre>"); return;
                }
                String clave = buscarPorNombre(ChatColor.stripColor(nombre));
                Block b = clave == null ? null : bloqueDe(clave);
                if (b == null) { p.sendMessage(ChatColor.RED + "No hay ninguna gasolinera con ese nombre. Mira /gasolinera lista"); return; }
                p.teleport(b.getLocation().add(0.5, 1, 0.5));
                return;
            }
            case "precio": {
                sender.sendMessage(ChatColor.GRAY + "Preguntando al Ministerio el precio de hoy...");
                Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
                    boolean ok = pl.precio.actualizar();
                    Bukkit.getScheduler().runTask(pl, () -> {
                        if (ok) actualizarCarteles();
                        sender.sendMessage(ok ? ChatColor.GREEN + "Precio actualizado: " + pl.precio.euros() + " €/L → " + pl.precio.dolares() + " Ⓜ/L"
                                : ChatColor.RED + "El Ministerio no responde ahora. Mira la consola; se reintenta solo cada 30 min.");
                    });
                });
                return;
            }
            case "crear": case "borrar": case "renombrar":
                break;
            default: {
                sender.sendMessage(ChatColor.YELLOW + "/gasolinera crear [nombre]" + ChatColor.GRAY + " (mirando a un suelo de 2x2 a 5x5 del mismo bloque; si está bajo el agua, es náutica)");
                sender.sendMessage(ChatColor.YELLOW + "/gasolinera renombrar <nombre>" + ChatColor.GRAY + " (mirando al bloque)");
                sender.sendMessage(ChatColor.YELLOW + "/gasolinera borrar [nombre]" + ChatColor.GRAY + " (por nombre o mirando al bloque)");
                sender.sendMessage(ChatColor.YELLOW + "/gasolinera lista" + ChatColor.GRAY + " · " + ChatColor.YELLOW + "/gasolinera ir <nombre>"
                        + ChatColor.GRAY + " · " + ChatColor.YELLOW + "/gasolinera precio" + ChatColor.GRAY + " (forzar precio de hoy)");
                return;
            }
        }

        // borrar por nombre (también desde consola)
        if (sub.equals("borrar") && nombre != null) {
            String clave = buscarPorNombre(ChatColor.stripColor(nombre));
            if (clave == null) { sender.sendMessage(ChatColor.RED + "No hay ninguna gasolinera con ese nombre. Mira /gasolinera lista"); return; }
            String viejo = surtidores.get(clave);
            borrarEstacion(clave);
            sender.sendMessage(ChatColor.GREEN + "Gasolinera " + viejo + ChatColor.GREEN + " borrada.");
            return;
        }

        if (!(sender instanceof Player p)) { sender.sendMessage("Hazlo en el juego, mirando al bloque."); return; }
        Block b = p.getTargetBlockExact(6, org.bukkit.FluidCollisionMode.NEVER);
        if (b == null) { p.sendMessage(ChatColor.RED + "Mira al bloque del suelo donde se para el vehículo (a menos de 6 bloques)."); return; }
        String clave = Coches.clave(b);
        switch (sub) {
            case "crear": {
                if (surtidores.containsKey(clave)) { p.sendMessage(ChatColor.YELLOW + "Ese bloque ya es la gasolinera " + surtidores.get(clave) + "."); return; }
                int[] r = rectangulo(b);
                int ancho = r == null ? 1 : r[2] - r[0] + 1, largo = r == null ? 1 : r[3] - r[1] + 1;
                if (r == null || ancho < 2 || largo < 2 || ancho > 5 || largo > 5) {
                    p.sendMessage(ChatColor.RED + "El suelo de la gasolinera tiene que ser un rectángulo del mismo bloque de 2x2 a 5x5"
                            + (r == null ? "." : " (ahora mide " + ancho + "x" + largo + ")."));
                    return;
                }
                boolean nautica = Agua.esAgua(b.getRelative(BlockFace.UP));
                for (int x = r[0]; x <= r[2]; x++)
                    for (int z = r[1]; z <= r[3]; z++)
                        if (surtidores.containsKey(Coches.clave(b.getWorld().getBlockAt(x, b.getY(), z)))) {
                            p.sendMessage(ChatColor.RED + "Parte de ese suelo ya es otra gasolinera."); return;
                        }
                if (nautica) {
                    // encima de cada bloque, de 1 a 2 bloques de agua limpia y luego aire
                    for (int x = r[0]; x <= r[2]; x++)
                        for (int z = r[1]; z <= r[3]; z++) {
                            int agua = 0;
                            Block arriba = b.getWorld().getBlockAt(x, b.getY() + 1, z);
                            while (agua < 4 && arriba.getType() == Material.WATER) { agua++; arriba = arriba.getRelative(BlockFace.UP); }
                            if (agua < 1 || agua > 2 || !arriba.getType().isAir()) {
                                p.sendMessage(ChatColor.RED + "Encima de todo el surtidor tiene que haber 1 o 2 bloques de agua limpia (sin algas ni bloques) y luego aire.");
                                return;
                            }
                        }
                }
                if (nombre != null && nombreUsado(nombre)) { p.sendMessage(ChatColor.RED + "Ya hay una gasolinera con ese nombre."); return; }
                String nuevo = nombre != null ? nombre : nautica ? nombreNautico() : nombreAleatorio();
                for (int x = r[0]; x <= r[2]; x++)
                    for (int z = r[1]; z <= r[3]; z++) {
                        String k = Coches.clave(b.getWorld().getBlockAt(x, b.getY(), z));
                        surtidores.put(k, nuevo);
                        if (nautica) nauticas.add(k);
                    }
                guardar();
                ponerCartel(b.getWorld().getBlockAt((r[0] + r[2]) / 2, b.getY(), (r[1] + r[3]) / 2));
                p.sendMessage(ChatColor.GREEN + "Gasolinera " + (nautica ? "náutica " : "") + ChatColor.GOLD + nuevo + ChatColor.GREEN + " abierta ("
                        + ancho + "x" + largo + "). " + (nautica ? "Las embarcaciones que se paren encima repostarán." : "Los coches que se paren encima repostarán."));
                if (nautica) p.sendMessage(ChatColor.GRAY + "Recuerda poner un warp cerca para quien se quede sin gasolina.");
                break;
            }
            case "renombrar": {
                if (!surtidores.containsKey(clave)) { p.sendMessage(ChatColor.YELLOW + "Ese bloque no es una gasolinera."); return; }
                if (nombre == null) { p.sendMessage(ChatColor.YELLOW + "Uso: /gasolinera renombrar <nombre>"); return; }
                if (nombreUsado(nombre)) { p.sendMessage(ChatColor.RED + "Ya hay una gasolinera con ese nombre."); return; }
                for (String k : estacion(clave)) {
                    surtidores.put(k, nombre);
                    Block bk = bloqueDe(k);
                    if (bk != null) quitarCartel(bk);
                }
                guardar();
                ponerCartel(b);
                p.sendMessage(ChatColor.GREEN + "Ahora se llama " + ChatColor.GOLD + nombre + ChatColor.GREEN + ".");
                break;
            }
            default: {
                String viejo = surtidores.get(clave);
                if (viejo == null) { p.sendMessage(ChatColor.YELLOW + "Ese bloque no es una gasolinera."); return; }
                borrarEstacion(clave);
                p.sendMessage(ChatColor.GREEN + "Gasolinera " + viejo + ChatColor.GREEN + " borrada.");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void alRomper(BlockBreakEvent e) {
        if (!esSurtidor(e.getBlock())) return;
        if (!e.getPlayer().hasPermission("coches.admin")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Eso es un surtidor de gasolina.");
            return;
        }
        String viejo = surtidores.get(Coches.clave(e.getBlock()));
        borrarEstacion(Coches.clave(e.getBlock()));
        e.getPlayer().sendMessage(ChatColor.YELLOW + "Has cerrado la gasolinera " + viejo + ChatColor.YELLOW + ".");
    }

    // ---------- protecciones de las gasolineras náuticas ----------

    /** ¿Está b justo encima (1-2 bloques) de un surtidor náutico? */
    private boolean sobreNautica(Block b) {
        for (int i = 1; i <= 3; i++) if (nauticas.contains(Coches.clave(b.getRelative(0, -i, 0)))) return true;
        return false;
    }

    /** ¿Está b debajo o dentro del suelo de un surtidor náutico (donde haría columnas de burbujas)? */
    private boolean bajoNautica(Block b) {
        for (int i = 0; i <= 3; i++) if (nauticas.contains(Coches.clave(b.getRelative(0, i, 0)))) return true;
        return false;
    }

    /** ¿Está b a menos del radio sin hielo de alguna gasolinera náutica? */
    private boolean cercaDeNautica(Block b) {
        int radio = pl.getConfig().getInt("gasolina.radio-sin-hielo", 5);
        for (String k : nauticas) {
            String[] c = k.split(":");
            if (!c[0].equals(b.getWorld().getName())) continue;
            int x = Integer.parseInt(c[1]), y = Integer.parseInt(c[2]), z = Integer.parseInt(c[3]);
            if (Math.abs(b.getX() - x) <= radio && Math.abs(b.getZ() - z) <= radio && Math.abs(b.getY() - y) <= radio + 3) return true;
        }
        return false;
    }

    private static boolean esHielo(Material m) { return Agua.hielo(m) || m == Material.SNOW || m == Material.POWDER_SNOW; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alPoner(BlockPlaceEvent e) {
        if (nauticas.isEmpty()) return;
        Block b = e.getBlockPlaced();
        Player p = e.getPlayer();
        Material m = b.getType();
        if (sobreNautica(b)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "No se puede construir encima de una gasolinera náutica.");
        } else if ((m == Material.SOUL_SAND || m == Material.MAGMA_BLOCK) && bajoNautica(b)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Nada de columnas de burbujas en una gasolinera náutica.");
        } else if (esHielo(m) && cercaDeNautica(b)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Aquí no se puede poner hielo: está junto a una gasolinera náutica.");
        }
    }

    // hielo que se forma solo (frío o botas con Paso helado) y columnas de burbujas
    @EventHandler(ignoreCancelled = true)
    public void alFormarse(BlockFormEvent e) {
        if (nauticas.isEmpty()) return;
        Material m = e.getNewState().getType();
        if ((esHielo(m) && cercaDeNautica(e.getBlock())) || (m == Material.BUBBLE_COLUMN && (bajoNautica(e.getBlock()) || sobreNautica(e.getBlock()))))
            e.setCancelled(true);
    }

    // quitar el agua de encima (cubos, esponjas, pistones) o llenarla de algas con polvo de hueso
    @EventHandler(ignoreCancelled = true)
    public void alLlenarCubo(org.bukkit.event.player.PlayerBucketFillEvent e) {
        if (!nauticas.isEmpty() && sobreNautica(e.getBlockClicked())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "No puedes quitar el agua de una gasolinera náutica.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void alVaciarCubo(org.bukkit.event.player.PlayerBucketEmptyEvent e) {
        if (!nauticas.isEmpty() && sobreNautica(e.getBlock()) && e.getBucket() != Material.WATER_BUCKET) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void alAbsorber(org.bukkit.event.block.SpongeAbsorbEvent e) {
        if (!nauticas.isEmpty()) e.getBlocks().removeIf(estado -> sobreNautica(estado.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void alAbonar(org.bukkit.event.block.BlockFertilizeEvent e) {
        if (!nauticas.isEmpty()) e.getBlocks().removeIf(estado -> sobreNautica(estado.getBlock()) || bajoNautica(estado.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void alEmpujarPiston(org.bukkit.event.block.BlockPistonExtendEvent e) {
        if (nauticas.isEmpty()) return;
        for (Block b : e.getBlocks()) if (sobreNautica(b) || sobreNautica(b.getRelative(e.getDirection())) || esNautica(b)) { e.setCancelled(true); return; }
        if (sobreNautica(e.getBlock().getRelative(e.getDirection()))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void alTirarPiston(org.bukkit.event.block.BlockPistonRetractEvent e) {
        if (nauticas.isEmpty()) return;
        for (Block b : e.getBlocks()) if (sobreNautica(b) || esNautica(b)) { e.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void alExtenderse(BlockSpreadEvent e) {
        if (nauticas.isEmpty()) return;
        Material m = e.getNewState().getType();
        if (m == Material.BUBBLE_COLUMN && (bajoNautica(e.getBlock()) || sobreNautica(e.getBlock()))) e.setCancelled(true);
    }

    // ---------- cartel flotante ----------

    private String textoCartel(String nombre) {
        String clave = null;
        for (Map.Entry<String, String> e : surtidores.entrySet()) if (e.getValue().equals(nombre)) { clave = e.getKey(); break; }
        boolean nautica = clave != null && nauticas.contains(clave);
        double precio = nautica ? precioEn(bloqueDe(clave)) : pl.precio.dolares();
        return ChatColor.GOLD + (nautica ? "⚓ " : "⛽ ") + nombre + "\n" + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f Ⓜ/L", precio) + "\n"
                + ChatColor.GRAY + (nautica ? "Para la embarcación encima" : "Para el coche encima");
    }

    private void ponerCartel(Block b) {
        double alto = 3.2;
        if (nauticas.contains(Coches.clave(b))) {
            int i = 1;
            while (i < 6 && Agua.esAgua(b.getRelative(0, i, 0))) i++;
            alto = i + 2.2;
        }
        Location loc = b.getLocation().add(0.5, alto, 0.5);
        b.getWorld().spawn(loc, TextDisplay.class, t -> {
            t.setText(textoCartel(nombre(b)));
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(true);
            t.getPersistentDataContainer().set(pl.kCartel, PersistentDataType.STRING, Coches.clave(b));
        });
    }

    private void quitarCartel(Block b) {
        String clave = Coches.clave(b);
        for (Entity e : b.getWorld().getNearbyEntities(b.getLocation().add(0.5, 3.2, 0.5), 4, 6, 4)) {
            if (clave.equals(e.getPersistentDataContainer().get(pl.kCartel, PersistentDataType.STRING))) e.remove();
        }
    }

    /** Refresca el precio de todos los carteles cargados. */
    void actualizarCarteles() {
        for (World w : Bukkit.getWorlds()) {
            for (TextDisplay t : w.getEntitiesByClass(TextDisplay.class)) actualizar(t);
        }
    }

    private void actualizar(TextDisplay t) {
        String clave = t.getPersistentDataContainer().get(pl.kCartel, PersistentDataType.STRING);
        if (clave != null) t.setText(textoCartel(surtidores.getOrDefault(clave, "Gasolinera")));
    }

    @EventHandler
    public void alCargar(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) if (en instanceof TextDisplay t) actualizar(t);
    }

    // ---------- repostaje ----------

    /** Se llama cada tick mientras el coche está parado encima de un surtidor. */
    void repostar(Conduccion.Estado st, Player p) {
        UUID id = st.cart.getUniqueId();
        Tier tier = st.tier;
        Sesion s = sesiones.get(id);
        Block suelo = surtidorBajo(st.cart.getLocation(), tier.agua());
        double precio = precioEn(suelo);
        if (s == null) {
            if (st.gasolina >= tier.deposito() - 0.05) {
                if (st.ticks % 40 == 0) p.sendActionBar(ChatColor.GREEN + "Depósito lleno.");
                return;
            }
            if (pl.eco == null) {
                if (st.ticks % 40 == 0) p.sendActionBar(ChatColor.RED + "La economía no está disponible.");
                return;
            }
            s = new Sesion(BossBar.bossBar(Component.text("⛽ Repostando…", NamedTextColor.GOLD), progreso(st), BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10));
            if (suelo == null) return;
            s.nombre = nombre(suelo);
            s.clave = Coches.clave(suelo);
            s.gratis = pl.tickets.canjear(p, st);
            sesiones.put(id, s);
            p.showBossBar(s.barra);
            p.sendMessage(ChatColor.GOLD + "⛽ Bienvenido a " + s.nombre + ChatColor.GOLD + ". Repostando a "
                    + String.format(Locale.ROOT, "%.2f Ⓜ/L", precio) + ChatColor.GRAY + ". Pulsa W o S para parar cuando quieras.");
            if (s.gratis) {
                p.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "★ Has canjeado " + pl.tickets.necesarios() + " tickets: " + ChatColor.GREEN + "este repostaje es GRATIS hasta llenar.");
            }
        }
        s.ticks++;
        double porTick = pl.getConfig().getDouble("gasolina.litros-por-segundo", 2.0) / 20.0;
        double falta = tier.deposito() - st.gasolina;
        double litros = Math.min(porTick, falta);
        if (!s.gratis) s.pendiente += litros * precio;
        // se cobra cada vez que se junta 1 Ⓜ, y al final lo que quede
        if (!s.gratis && (s.pendiente >= 1.0 || falta - litros <= 1e-4)) {
            double cobro = Math.round(s.pendiente * 100.0) / 100.0;
            if (!pl.eco.has(p, cobro) || !pl.eco.withdrawPlayer(p, cobro).transactionSuccess()) {
                p.sendMessage(ChatColor.RED + "No te llega el dinero para seguir repostando.");
                terminar(id, p, st);
                return;
            }
            s.pagado += cobro;
            s.pendiente = 0;
        }
        st.gasolina += litros;
        s.litros += litros;
        s.barra.progress(progreso(st));
        s.barra.name(Component.text(String.format(Locale.ROOT, "⛽ %s  %.1f / %.0f L  ·  ", ChatColor.stripColor(s.nombre), st.gasolina, tier.deposito())
                + (s.gratis ? "GRATIS" : String.format(Locale.ROOT, "%.2f Ⓜ", s.pagado + s.pendiente)),
                s.gratis ? NamedTextColor.GREEN : NamedTextColor.GOLD));
        if (s.ticks % 20 == 1) p.playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL, SoundCategory.PLAYERS, 0.6f, 0.7f);
        if (s.ticks % 5 == 0) st.cart.getWorld().spawnParticle(Particle.FALLING_DRIPSTONE_LAVA, st.cart.getLocation().add(0, 1.2, 0), 1, 0.15, 0.1, 0.15, 0);
        if (st.gasolina >= tier.deposito() - 1e-4) terminar(id, p, st);
    }

    private static float progreso(Conduccion.Estado st) {
        return (float) Math.max(0, Math.min(1, st.gasolina / st.tier.deposito()));
    }

    private void terminar(UUID id, Player p, Conduccion.Estado st) {
        Sesion s = sesiones.remove(id);
        if (s == null) return;
        if (p != null) {
            p.hideBossBar(s.barra);
            if (s.pendiente > 0 && pl.eco != null && pl.eco.has(p, s.pendiente)) { pl.eco.withdrawPlayer(p, s.pendiente); s.pagado += s.pendiente; }
            if (s.litros > 0.05) {
                pl.carnet.sumar(p, "litros", s.litros);
                pl.carnet.sumar(p, "gasto-gasolina", s.pagado);
                pl.carnet.sumar(p, "repostajes", 1);
                pl.tickets.cerrar(p, s.nombre, s.clave, st, s.litros, precioEn(bloqueDe(s.clave)), s.pagado, s.gratis);
            }
        }
        pl.conduccion.guardar(st);
    }

    /** El jugador ha pulsado W/S o se ha bajado: se corta el repostaje. */
    void parar(UUID id, Player p) {
        Sesion s = sesiones.get(id);
        if (s == null) return;
        Conduccion.Estado st = pl.conduccion.conduciendo.get(id);
        if (st != null) { terminar(id, p, st); return; }
        sesiones.remove(id);
        if (p != null) p.hideBossBar(s.barra);
    }
}
