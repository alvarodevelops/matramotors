package matracraft.coches;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Menús de cofre del plugin (/coche): principal, concesionario, seguros, GPS, venta... */
final class Menus implements Listener {

    static final class Menu implements InventoryHolder {
        final Map<String, Consumer<Player>> acciones = new HashMap<>();
        Inventory inv;
        ItemStack[] base;

        public Inventory getInventory() { return inv; }
    }

    private final Coches pl;
    private final NamespacedKey kMenu;
    private final NamespacedKey kBoton;

    Menus(Coches pl) {
        this.pl = pl;
        this.kMenu = new NamespacedKey(pl, "menu");
        this.kBoton = new NamespacedKey(pl, "boton");
    }

    /** Objeto de menú marcado con kMenu (para poder limpiarlo si acaba en el inventario del jugador). */
    private ItemStack item(Material mat, String nombre, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + nombre);
        if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(l -> l.isEmpty() ? "" : ChatColor.GRAY + l).toList());
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(kMenu, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private Menu nuevo(int filas, String titulo) {
        Menu m = new Menu();
        m.inv = Bukkit.createInventory(m, filas * 9, ChatColor.DARK_GRAY + titulo);
        ItemStack negro = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack naranja = item(Material.ORANGE_STAINED_GLASS_PANE, " ");
        for (int tam = m.inv.getSize(), i = 0; i < tam; i++) {
            int fila = i / 9, col = i % 9;
            boolean borde = fila == 0 || fila == filas - 1 || col == 0 || col == 8;
            boolean esquina = (fila == 0 || fila == filas - 1) && (col == 0 || col == 8);
            if (borde) m.inv.setItem(i, esquina ? naranja : negro);
        }
        return m;
    }

    private void poner(Menu m, int slot, ItemStack it, Consumer<Player> accion) {
        if (accion != null) {
            String id = "b" + slot;
            ItemMeta meta = it.getItemMeta();
            meta.getPersistentDataContainer().set(kBoton, PersistentDataType.STRING, id);
            it.setItemMeta(meta);
            m.acciones.put(id, accion);
        }
        m.inv.setItem(slot, it);
    }

    private void abrir(Player p, Menu m) {
        m.base = m.inv.getContents().clone();
        for (int i = 0; i < m.base.length; i++) if (m.base[i] != null) m.base[i] = m.base[i].clone();
        p.openInventory(m.inv);
        // Otros plugins a veces tocan el inventario al abrirlo: se repone varias veces
        for (long t : new long[]{1, 3, 10})
            Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.getOpenInventory().getTopInventory() == m.inv) recolocar(p, m); }, t);
    }

    private void recolocar(Player p, Menu m) {
        if (m.base != null) {
            ItemStack[] copia = new ItemStack[m.base.length];
            for (int i = 0; i < copia.length; i++) copia[i] = m.base[i] == null ? null : m.base[i].clone();
            m.inv.setContents(copia);
        }
        limpiar(p);
    }

    private void limpiar(Player p) {
        ItemStack[] cont = p.getInventory().getContents();
        for (int i = 0; i < cont.length; i++)
            if (cont[i] != null && cont[i].hasItemMeta() && cont[i].getItemMeta().getPersistentDataContainer().has(kMenu, PersistentDataType.BYTE))
                p.getInventory().setItem(i, null);
        ItemStack cursor = p.getItemOnCursor();
        if (cursor.hasItemMeta() && cursor.getItemMeta().getPersistentDataContainer().has(kMenu, PersistentDataType.BYTE))
            p.setItemOnCursor(null);
    }

    private void volver(Menu m, int slot, Consumer<Player> accion) {
        poner(m, slot, item(Material.ARROW, ChatColor.YELLOW + "← Volver"), accion);
    }

    private static Consumer<Player> comando(String cmd) { return q -> { q.closeInventory(); q.performCommand(cmd); }; }

    private static String sinEspacio(String s) { return s.replace(" ", ""); }

    /** Matrículas de las llaves que lleva el jugador y que puede gestionar desde el menú. */
    private Set<String> llaves(Player p) {
        Set<String> llaves = new LinkedHashSet<>();
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || !it.hasItemMeta()) continue;
            String matricula = it.getItemMeta().getPersistentDataContainer().get(pl.kLlave, PersistentDataType.STRING);
            if (matricula != null && Extra.llaveYDuenoMenu(pl, p, matricula)) llaves.add(matricula);
        }
        return llaves;
    }

    /** Clic derecho a un surtidor a pie: comprar o rellenar bidones de gasolina. */
    void bidones(Player p, org.bukkit.block.Block surtidor) {
        double litro = pl.gasolineras.precioEn(surtidor);
        double uno = litro * Bidones.LITROS;
        Menu m = nuevo(3, "⛽ " + ChatColor.stripColor(pl.gasolineras.nombre(surtidor)));
        poner(m, 10, item(Material.LAVA_BUCKET, ChatColor.GOLD + "Precio de hoy",
                String.format(Locale.ROOT, "%.2f Ⓜ/L", litro), "", "Para repostar del surtidor, para", "el vehículo encima y espera"), null);
        int[] slots = {12, 13, 14};
        int[] cantidades = {1, 4, 16};
        for (int i = 0; i < 3; i++) {
            int n = cantidades[i];
            ItemStack it = pl.bidones.nuevo(Bidones.LITROS);
            it.setAmount(n);
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(ChatColor.RESET + "" + ChatColor.GOLD + "Comprar " + n + (n == 1 ? " bidón" : " bidones") + " de 10 L");
            meta.setLore(java.util.List.of(ChatColor.WHITE + String.format(Locale.ROOT, "%.2f Ⓜ", uno * n), "",
                    ChatColor.GRAY + "Clic derecho a un coche, embarcación", ChatColor.GRAY + "o locomotora diésel para echarla."));
            meta.getPersistentDataContainer().set(kMenu, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().remove(pl.bidones.kBidon);
            it.setItemMeta(meta);
            poner(m, slots[i], it, q -> { q.closeInventory(); pl.bidones.comprar(q, surtidor, n); });
        }
        double falta = pl.bidones.faltaRellenar(p);
        poner(m, 16, item(Material.BUCKET, ChatColor.YELLOW + "Rellenar mis bidones",
                falta < 0.05 ? "No llevas bidones empezados" : String.format(Locale.ROOT, "Faltan %.1f L → %.2f Ⓜ", falta, falta * litro)),
                falta < 0.05 ? null : q -> { q.closeInventory(); pl.bidones.rellenar(q, surtidor); });
        abrir(p, m);
    }

    void principal(Player p) {
        Menu m = nuevo(4, "MATRAMOTORS");
        poner(m, 10, item(Material.MINECART, ChatColor.GOLD + "" + ChatColor.BOLD + "Comprar vehículo",
                "Concesionario: coches y embarcaciones"), this::concesionario);
        poner(m, 11, item(Material.TRIPWIRE_HOOK, ChatColor.YELLOW + "" + ChatColor.BOLD + "Mis vehículos",
                "Coches, barcos y trenes:", "los tuyos y los prestados", "", "Seguros, grúa, copias de llave y GPS"), this::misCoches);
        poner(m, 12, item(Material.CHEST_MINECART, ChatColor.AQUA + "" + ChatColor.BOLD + "Vehículo en la mano",
                "Taller, desguace y vender a otro jugador", "(ponlo guardado en la mano)"), this::enMano);
        poner(m, 13, item(Material.COMPASS, ChatColor.GREEN + "" + ChatColor.BOLD + "🧭 GPS",
                "¿Dónde está mi coche?", "Gasolinera más cercana",
                pl.gps.activo(p) ? ChatColor.YELLOW + "(GPS en marcha)" : ""), this::gps);
        poner(m, 22, carnet(p), null);
        poner(m, 14, item(Material.LAVA_BUCKET, ChatColor.GOLD + "⛽ Gasolina",
                String.format(Locale.ROOT, "Hoy: %.3f €/L → %.2f Ⓜ/L", pl.precio.euros(), pl.precio.dolares()),
                String.format(Locale.ROOT, "Náutica: %.2f Ⓜ/L", pl.gasolineras.precioEn(null) + pl.getConfig().getDouble("gasolina.suplemento-nautico-euros", 0.15) * pl.getConfig().getDouble("gasolina.euros-a-dolares", 2.0)),
                "Para el vehículo encima de un surtidor y espera", "",
                "⛽ Bidón de 10 L: clic derecho al surtidor", "(para llevar gasolina a cualquier sitio)"), null);
        poner(m, 15, item(Material.PAPER, ChatColor.WHITE + "🎫 Tickets",
                "Llevas " + pl.tickets.validos(p) + "/" + pl.tickets.necesarios() + " tickets válidos",
                "Con " + pl.tickets.necesarios() + ", el siguiente repostaje es gratis"), null);
        poner(m, 16, item(Material.BOOK, ChatColor.GREEN + "Ayuda", "Cómo se conduce y todos los comandos"), comando("coche ayuda"));
        if (Bukkit.getPluginManager().isPluginEnabled("Matrains"))
            poner(m, 20, item(Material.FURNACE_MINECART, ChatColor.GOLD + "" + ChatColor.BOLD + "🚆 Trenes",
                    "Concesionario ferroviario, mis trenes,", "estaciones y líneas eléctricas"), q -> { q.closeInventory(); q.performCommand("tren menu"); });
        if (p.getVehicle() != null && pl.conduccion.esCoche(p.getVehicle()))
            poner(m, 19, item(Material.HONEY_BOTTLE, ChatColor.YELLOW + "🆘 Asistencia en carretera",
                    "Gasolina de emergencia si te has quedado tirado"), comando("coche asistencia"));
        abrir(p, m);
    }

    private ItemStack carnet(Player p) {
        UUID id = p.getUniqueId();
        Carnet c = pl.carnet;
        double km = c.valor(id, "bloques") / 1000.0;
        double horas = c.valor(id, "ticks") / 20.0 / 3600.0;
        int malus = pl.seguros.malus(id);
        String seguro = malus > 0 ? ChatColor.RED + "+" + (25 * malus) + " % (" + malus + " parte" + (malus > 1 ? "s" : "") + ")"
                : malus < 0 ? ChatColor.GREEN + "-" + (-10 * malus) + " % de descuento"
                : ChatColor.WHITE + "normal";
        ItemStack it = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + "" + ChatColor.AQUA + ChatColor.BOLD + "🪪 Carnet de " + p.getName());
        meta.setLore(List.of(
                ChatColor.GRAY + "Recorrido: " + ChatColor.WHITE + String.format(Locale.ROOT, "%,.1f km", km)
                        + ChatColor.GRAY + " · al volante: " + ChatColor.WHITE + String.format(Locale.ROOT, "%.1f h", horas),
                ChatColor.GRAY + "Choques: " + ChatColor.WHITE + (long) c.valor(id, "choques")
                        + ChatColor.GRAY + " · atropellos: " + ChatColor.WHITE + (long) c.valor(id, "atropellos"),
                ChatColor.GRAY + "Seguro: " + seguro,
                ChatColor.GRAY + "Gasolina: " + ChatColor.WHITE + String.format(Locale.ROOT, "%,.0f L", c.valor(id, "litros"))
                        + ChatColor.GRAY + " en " + ChatColor.WHITE + (long) c.valor(id, "repostajes")
                        + ChatColor.GRAY + " repostajes (" + ChatColor.WHITE + String.format(Locale.ROOT, "%,.2f Ⓜ", c.valor(id, "gasto-gasolina"))
                        + ChatColor.GRAY + ")",
                ChatColor.GRAY + "Coches comprados: " + ChatColor.WHITE + (long) c.valor(id, "comprados")
                        + ChatColor.GRAY + " · vendidos: " + ChatColor.WHITE + (long) c.valor(id, "vendidos")
                        + ChatColor.GRAY + " · desguazados: " + ChatColor.WHITE + (long) c.valor(id, "desguazados"),
                ChatColor.GRAY + "El más caro: " + ChatColor.WHITE + c.texto(id, "coche-mas-caro")));
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(kMenu, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private void gps(Player p) {
        Menu m = nuevo(3, "GPS");
        int n = 0;
        for (String matricula : llaves(p)) {
            if (n >= 5) break;
            String texto;
            Location donde = null;
            boolean encima = false;
            for (ItemStack it : p.getInventory().getContents())
                if (it != null && it.getType() == Material.MINECART && it.hasItemMeta()
                        && matricula.equals(it.getItemMeta().getPersistentDataContainer().get(pl.kMatricula, PersistentDataType.STRING))) encima = true;
            if (!encima) {
                for (World mundo : Bukkit.getWorlds())
                    for (ArmorStand c : mundo.getEntitiesByClass(ArmorStand.class))
                        if (matricula.equals(pl.conduccion.matriculaDe(c))) donde = c.getLocation();
                if (donde == null) donde = pl.seguros.ubicacion(matricula);
            }
            if (encima) texto = "Lo llevas guardado encima";
            else if (donde == null) texto = "No sé dónde está (usa la grúa)";
            else if (donde.getWorld() != p.getWorld()) texto = "Está en otro mundo";
            else texto = String.format(Locale.ROOT, "A %,.0f m · %d %d %d", donde.distance(p.getLocation()),
                        donde.getBlockX(), donde.getBlockY(), donde.getBlockZ());
            Location destino = donde;
            boolean guiable = !encima && donde != null && donde.getWorld() == p.getWorld();
            poner(m, 10 + n++, item(Material.TRIPWIRE_HOOK, ChatColor.WHITE + "" + ChatColor.BOLD + matricula, texto,
                            guiable ? ChatColor.YELLOW + "Clic para que el GPS te lleve" : ""),
                    guiable ? q -> { q.closeInventory(); pl.gps.guiar(q, destino, "Coche " + matricula); } : null);
        }
        if (n == 0)
            poner(m, 11, item(Material.BARRIER, ChatColor.RED + "No llevas llaves encima", "El GPS busca los coches de tus llaves"), null);

        gasolineraCercana(p, m, 16, false);
        gasolineraCercana(p, m, 25, true);
        if (pl.gps.activo(p)) poner(m, 22, item(Material.RED_CONCRETE, ChatColor.RED + "Apagar el GPS"), q -> { q.closeInventory(); pl.gps.parar(q); });
        volver(m, 18, this::principal);
        abrir(p, m);
    }

    /** Botón con la gasolinera (de tierra o náutica) más cercana y su precio. */
    private void gasolineraCercana(Player p, Menu m, int hueco, boolean nautica) {
        Location gasolinera = null;
        String nombre = null;
        double distancia = Double.MAX_VALUE;
        for (Map.Entry<String, String> e : pl.gasolineras.lista().entrySet()) {
            String[] partes = e.getKey().split(":");
            World mundo = Bukkit.getWorld(partes[0]);
            if (mundo != p.getWorld()) continue;
            if (pl.gasolineras.esNautica(mundo.getBlockAt(Integer.parseInt(partes[1]), Integer.parseInt(partes[2]), Integer.parseInt(partes[3]))) != nautica) continue;
            Location l = new Location(mundo, Integer.parseInt(partes[1]) + 0.5, Integer.parseInt(partes[2]) + 1, Integer.parseInt(partes[3]) + 0.5);
            double d = l.distance(p.getLocation());
            if (d < distancia) { distancia = d; gasolinera = l; nombre = e.getValue(); }
        }
        Location g = gasolinera; String nombreG = nombre;
        double precio = g == null ? 0 : pl.gasolineras.precioEn(g.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN));
        poner(m, hueco, item(nautica ? Material.WATER_BUCKET : Material.LAVA_BUCKET,
                        ChatColor.GOLD + "" + ChatColor.BOLD + (nautica ? "⚓ Gasolinera náutica más cercana" : "⛽ Gasolinera más cercana"),
                        g == null ? "No hay ninguna en este mundo" : ChatColor.stripColor(nombreG),
                        g == null ? "" : String.format(Locale.ROOT, "A %,.0f m · %.2f Ⓜ/L", distancia, precio),
                        g == null ? "" : ChatColor.YELLOW + "Clic para que el GPS te lleve"),
                g == null ? null : q -> { q.closeInventory(); pl.gps.guiar(q, g, ChatColor.stripColor(nombreG)); });
    }

    private void concesionario(Player p) {
        Menu m = nuevo(5, "Concesionario");
        poner(m, 4, item(Material.MINECART, ChatColor.GOLD + "" + ChatColor.BOLD + "Coches", ChatColor.GRAY + "Fila de abajo"), null);
        poner(m, 22, item(Material.OAK_BOAT, ChatColor.AQUA + "" + ChatColor.BOLD + "Embarcaciones", ChatColor.GRAY + "Solo navegan por agua · fila de abajo"), null);
        int[] huecosCoches = {10, 11, 12, 13, 14, 15, 16};
        int[] huecosBarcos = {28, 29, 30, 31, 32, 33, 34};
        int ic = 0, ib = 0;
        for (Tier t : pl.tiers.values()) {
            int[] huecos = t.agua() ? huecosBarcos : huecosCoches;
            int i = t.agua() ? ib : ic;
            if (i >= huecos.length) continue;
            double autonomia = t.sinMotor() ? 0 : t.deposito() / t.consumo();
            List<String> lore = new ArrayList<>(List.of(
                    ChatColor.GOLD + String.format(Locale.ROOT, "%,.2f Ⓜ", t.precio()) + ChatColor.GRAY + " (+ seguro)", "",
                    "Velocidad   " + barras(t, Tier::velocidad) + ChatColor.GRAY + String.format(Locale.ROOT, "  %.1f bl/s", t.velocidad()),
                    "Aceleración " + barras(t, Tier::aceleracion),
                    "Frenada     " + barras(t, Tier::frenada),
                    "Giro        " + barras(t, Tier::giro),
                    t.sinMotor() ? "Autonomía   " + ChatColor.GREEN + "sin motor: a pedales"
                            : "Autonomía   " + barras(t, x -> x.sinMotor() ? 0 : x.deposito() / x.consumo()) + ChatColor.GRAY + String.format(Locale.ROOT, "  ~%,.0f bloques", autonomia),
                    (t.agua() ? "Bodega      " : "Maletero    ") + (t.carga() > 0 ? ChatColor.WHITE + "" + t.carga() + " huecos" : ChatColor.DARK_GRAY + "no tiene"),
                    "Plazas      " + ChatColor.WHITE + t.plazas(),
                    "Resistencia " + barras(t, x -> 1 / x.fragilidad())));
            if (t.todoterreno()) lore.add(ChatColor.GREEN + "Todoterreno: sube escalones de tierra y piedra");
            if (t.taxi()) lore.add(ChatColor.YELLOW + "Taxi: lleva taxímetro (/taximetro)");
            if (t.id().equals("goma")) lore.add(ChatColor.RED + "Frágil y vuelca si giras a tope muy rápido");
            if (t.id().equals("motoagua")) lore.add(ChatColor.GREEN + "Salta en las rampas del borde");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Clic para elegir el seguro y comprar");
            Material icono = t.agua() ? Material.OAK_BOAT : t.carga() > 0 ? Material.CHEST_MINECART : Material.MINECART;
            ItemStack it = item(icono, ChatColor.GOLD + "" + ChatColor.BOLD + t.nombre());
            ItemMeta meta = it.getItemMeta(); meta.setLore(lore.stream().map(l -> ChatColor.GRAY + l).toList()); it.setItemMeta(meta);
            poner(m, huecos[i], it, q -> seguro(q, t, "coche comprar " + t.id(), this::concesionario,
                    String.format(Locale.ROOT, "Comprar %s · %,.2f Ⓜ +", t.nombre(), t.precio())));
            if (t.agua()) ib++; else ic++;
        }
        volver(m, 36, this::principal);
        abrir(p, m);
    }

    /** Cinco puntos que comparan este modelo con el resto. */
    private String barras(Tier t, ToDoubleFunction<Tier> f) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        // se compara con los de su clase: coches con coches, barcos con barcos
        for (Tier x : pl.tiers.values()) {
            if (x.agua() != t.agua()) continue;
            min = Math.min(min, f.applyAsDouble(x)); max = Math.max(max, f.applyAsDouble(x));
        }
        int n = max - min < 1e-9 ? 3 : (int) Math.round(1 + 4 * (f.applyAsDouble(t) - min) / (max - min));
        return ChatColor.GOLD + "●".repeat(n) + ChatColor.DARK_GRAY + "●".repeat(5 - n);
    }

    private void seguro(Player p, Tier t, String prefijo, Consumer<Player> atras, String titulo) {
        Menu m = nuevo(4, titulo.length() > 30 ? "Elige el seguro" : titulo);
        Material[] colores = {Material.LIGHT_GRAY_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE};
        for (int i = 0; i < Seguros.TIPOS.size(); i++) {
            String tipo = Seguros.TIPOS.get(i);
            poner(m, 1 + i * 3 + 9 - 9, item(Material.SHIELD, ChatColor.GOLD + Seguros.nombreTipo(tipo),
                    Seguros.compania(tipo), "", Seguros.cobertura(tipo)), null);
            for (int j = 0; j < Seguros.MODOS.size(); j++) {
                String modo = Seguros.MODOS.get(j);
                double precio = pl.seguros.precio(t, tipo, modo, p.getUniqueId());
                poner(m, 10 + i * 3 + j * 9, item(colores[i],
                                (modo.equals("semanal") ? ChatColor.GREEN : ChatColor.AQUA) + Seguros.nombreTipo(tipo) + " · " + modo,
                                String.format(Locale.ROOT, "%,.2f Ⓜ %s", precio, modo.equals("semanal") ? "cada semana" : "por 4 semanas (con descuento)"),
                                "", ChatColor.YELLOW + "Clic para elegir"),
                        comando(prefijo + " " + tipo + " " + modo));
            }
        }
        volver(m, 27, atras);
        abrir(p, m);
    }

    /** Mis vehículos: lo tuyo (coches, barcos y trenes) y lo que te han prestado, todo en una ventana. */
    void misCoches(Player p) {
        Menu m = nuevo(6, "Mis vehículos");
        Set<String> llaves = llaves(p);
        // fila 1: coches y barcos a tu nombre (aunque no lleves la llave encima)
        Map<String, String> mios = new java.util.LinkedHashMap<>();
        for (String mat : llaves) mios.put(mat, null);
        mios.putAll(pl.registro.de(p.getName()));
        poner(m, 9, item(Material.NAME_TAG, ChatColor.GOLD + "" + ChatColor.BOLD + "Tuyos", "Coches y embarcaciones", "a tu nombre"), null);
        int n = 0;
        for (Map.Entry<String, String> e : mios.entrySet()) {
            if (n >= 7) break;
            String matricula = e.getKey();
            Tier t = tierPorNombre(e.getValue());
            if (t == null) t = pl.seguros.tierDeLlave(p, matricula);
            boolean llave = llaves.contains(matricula);
            boolean vigente = pl.seguros.vigente(matricula);
            List<String> lore = new java.util.ArrayList<>();
            lore.add(vigente ? ChatColor.GREEN + "Seguro en vigor (" + Seguros.nombreTipo(pl.seguros.tipo(matricula)) + ")" : ChatColor.RED + "SIN SEGURO: no puede circular");
            Location donde = pl.extraPrestados() == null ? pl.seguros.ubicacion(matricula) : pl.extraPrestados().donde(matricula);
            lore.add(donde == null ? "No sé dónde está (¿guardado?)" : "Está en " + donde.getWorld().getName() + " " + donde.getBlockX() + ", " + donde.getBlockY() + ", " + donde.getBlockZ());
            lore.add("");
            lore.add(llave ? ChatColor.YELLOW + "Clic: seguro, grúa y copia de llave" : ChatColor.RED + "No llevas su llave encima"
                    + (donde != null ? ChatColor.YELLOW + " · clic: GPS" : ""));
            Material icono = t != null && t.agua() ? Material.OAK_BOAT : Material.MINECART;
            Consumer<Player> accion = llave ? q -> unCoche(q, matricula)
                    : donde == null ? null : q -> { q.closeInventory(); pl.gps.guiar(q, donde, "Coche " + matricula); };
            poner(m, 10 + n++, item(icono, ChatColor.WHITE + "" + ChatColor.BOLD + (t != null ? t.nombre() + " " : "") + matricula,
                    lore.toArray(new String[0])), accion);
        }
        if (n == 0) poner(m, 10, item(Material.PAPER, ChatColor.GRAY + "No tienes coches ni barcos", "Cómpralos en el concesionario"), null);
        if (mios.size() > 7) poner(m, 17, item(Material.PAPER, ChatColor.GRAY + "y " + (mios.size() - 7) + " más…"), null);

        // fila 2: prestados (llaves de coches de otros)
        poner(m, 18, item(Material.NAME_TAG, ChatColor.AQUA + "" + ChatColor.BOLD + "Prestados", "Coches de otros de los", "que llevas la llave"), null);
        Map<String, String> prest = pl.extraPrestados() == null ? Map.of() : pl.extraPrestados().de(p);
        n = 0;
        for (Map.Entry<String, String> e : prest.entrySet()) {
            if (n >= 7) break;
            String matricula = e.getKey();
            Location donde = pl.extraPrestados().donde(matricula);
            poner(m, 19 + n++, item(Material.MINECART, ChatColor.AQUA + "" + ChatColor.BOLD + matricula,
                    "Dueño: " + ChatColor.WHITE + e.getValue(),
                    donde == null ? "No sé dónde está" : "Está en " + donde.getWorld().getName() + " " + donde.getBlockX() + ", " + donde.getBlockY() + ", " + donde.getBlockZ(),
                    "", "Puedes conducirlo y pedir la grúa.", donde == null ? "" : ChatColor.YELLOW + "Clic: GPS"),
                    donde == null ? null : q -> { q.closeInventory(); pl.gps.guiar(q, donde, "Coche " + matricula); });
        }
        if (n == 0) poner(m, 19, item(Material.PAPER, ChatColor.GRAY + "Nadie te ha prestado nada", "Si te dan la copia de una llave,", "el coche sale aquí"), null);

        // fila 3: trenes (Matrains)
        org.bukkit.plugin.Plugin mt = Bukkit.getPluginManager().getPlugin("Matrains");
        if (mt != null && mt.isEnabled()) {
            poner(m, 27, item(Material.NAME_TAG, ChatColor.GOLD + "" + ChatColor.BOLD + "Trenes", "Tuyos y prestados (con llave)"), null);
            n = 0;
            try {
                @SuppressWarnings("unchecked")
                List<Object[]> trenes = (List<Object[]>) mt.getClass().getMethod("resumenTrenes", Player.class).invoke(mt, p);
                for (Object[] t : trenes) {
                    if (n >= 7) break;
                    @SuppressWarnings("unchecked") List<String> lore = (List<String>) t[1];
                    @SuppressWarnings("unchecked") Consumer<Player> abrir = (Consumer<Player>) t[3];
                    ItemStack it = item((Material) t[2], ChatColor.GOLD + "" + ChatColor.BOLD + t[0]);
                    ItemMeta meta = it.getItemMeta();
                    meta.setLore(lore);
                    it.setItemMeta(meta);
                    poner(m, 28 + n++, it, q -> { q.closeInventory(); abrir.accept(q); });
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                pl.getLogger().warning("No he podido leer los trenes de Matrains: " + ex);
            }
            if (n == 0) poner(m, 28, item(Material.PAPER, ChatColor.GRAY + "No tienes trenes en las vías", "Cómpralo en el menú de trenes →"), null);
            poner(m, 34, item(Material.FURNACE_MINECART, ChatColor.YELLOW + "Menú de trenes", "Concesionario, estaciones y líneas"),
                    q -> { q.closeInventory(); q.performCommand("tren menu"); });
        }
        volver(m, 45, this::principal);
        abrir(p, m);
    }

    private Tier tierPorNombre(String nombre) {
        if (nombre == null) return null;
        for (Tier t : pl.tiers.values()) if (t.nombre().equalsIgnoreCase(nombre)) return t;
        return null;
    }

    private void unCoche(Player p, String matricula) {
        Tier t = pl.seguros.tierDeLlave(p, matricula);
        Menu m = nuevo(3, "Coche " + matricula);
        boolean vigente = pl.seguros.vigente(matricula);
        if (t != null) poner(m, 11, item(Material.SHIELD, ChatColor.GOLD + (vigente ? "Cambiar seguro" : "Contratar seguro"),
                        vigente ? "Ahora: " + Seguros.nombreTipo(pl.seguros.tipo(matricula)) : "Obligatorio para conducir",
                        "", ChatColor.YELLOW + "Clic para elegir"),
                q -> seguro(q, t, "coche seguro " + sinEspacio(matricula), r -> unCoche(r, matricula), "Seguro " + matricula));
        poner(m, 13, item(Material.LEAD, ChatColor.GOLD + "🚚 Llamar a la grúa", "Te trae el coche esté donde esté",
                        String.format(Locale.ROOT, "%,.2f Ⓜ (gratis o más barato según tu seguro)", pl.getConfig().getDouble("seguros.precio-grua", 300))),
                comando("coche grua " + sinEspacio(matricula)));
        poner(m, 15, item(Material.TRIPWIRE_HOOK, ChatColor.GOLD + "🔑 Hacer copia de la llave",
                        String.format(Locale.ROOT, "%,.2f Ⓜ", pl.getConfig().getDouble("llaves.precio-copia", 500)),
                        "Para prestar el coche a otro jugador"),
                comando("coche copiallave " + sinEspacio(matricula)));
        volver(m, 18, this::misCoches);
        abrir(p, m);
    }

    private String cocheEnMano(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType() != Material.MINECART || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(pl.kMatricula, PersistentDataType.STRING);
    }

    private void enMano(Player p) {
        String matricula = cocheEnMano(p);
        if (matricula == null) { p.closeInventory(); p.sendMessage(ChatColor.YELLOW + "Guarda el coche y ponlo en la mano, luego abre otra vez /coche."); return; }
        PersistentDataContainer datos = p.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer();
        Tier t = pl.tiers.get(datos.get(pl.kTier, PersistentDataType.STRING));
        Double guardado = datos.get(pl.kEstado, PersistentDataType.DOUBLE);
        double estado = guardado == null ? 100.0 : guardado;
        if (t == null) return;
        Menu m = nuevo(3, t.nombre() + " " + matricula);
        double reparar = (100 - estado) / 100 * t.precio() * pl.getConfig().getDouble("averias.coste-reparacion", 0.15);
        poner(m, 11, item(Material.ANVIL, ChatColor.GOLD + "🔧 Taller", String.format(Locale.ROOT, "Estado: %.0f %%", estado),
                        estado >= 99.5 ? "Está perfecto" : String.format(Locale.ROOT, "Reparar: %,.2f Ⓜ (tu seguro puede pagar parte)", reparar)),
                estado >= 99.5 ? null : comando("coche reparar"));
        poner(m, 13, item(Material.PLAYER_HEAD, ChatColor.GOLD + "🤝 Vender a un jugador", "Tiene que estar a tu lado"),
                q -> elegirComprador(q, matricula));
        poner(m, 15, item(Material.IRON_BARS, ChatColor.RED + "🔩 Desguace",
                        String.format(Locale.ROOT, "Te damos %,.2f Ⓜ", pl.ventas.tasacion(t, estado)),
                        estado < 99.5 ? String.format(Locale.ROOT, "(reparado te darían %,.2f Ⓜ)", pl.ventas.tasacion(t, 100)) : "",
                        "El coche desaparece para siempre"),
                q -> confirmarDesguace(q, matricula, pl.ventas.tasacion(t, estado)));
        volver(m, 18, this::principal);
        abrir(p, m);
    }

    private void confirmarDesguace(Player p, String matricula, double precio) {
        Menu m = nuevo(3, "¿Desguazar " + matricula + "?");
        poner(m, 11, item(Material.LIME_CONCRETE, ChatColor.GREEN + "" + ChatColor.BOLD + "SÍ, desguazar",
                String.format(Locale.ROOT, "Cobras %,.2f Ⓜ", precio), "El coche y sus llaves desaparecen"), q -> {
            q.closeInventory(); pl.ventas.desguace(q, new String[]{"desguace"}); pl.ventas.desguace(q, new String[]{"desguace", "confirmar"}); });
        poner(m, 15, item(Material.RED_CONCRETE, ChatColor.RED + "" + ChatColor.BOLD + "No, me lo quedo"), this::enMano);
        abrir(p, m);
    }

    private void elegirComprador(Player p, String matricula) {
        Menu m = nuevo(3, "¿A quién se lo vendes?");
        int n = 0;
        for (Player otro : p.getWorld().getPlayers()) {
            if (otro.equals(p) || otro.getLocation().distance(p.getLocation()) > 10 || n >= 7) continue;
            ItemStack cabeza = item(Material.PLAYER_HEAD, ChatColor.YELLOW + otro.getName(), "", ChatColor.YELLOW + "Clic para poner el precio");
            SkullMeta meta = (SkullMeta) cabeza.getItemMeta(); meta.setOwningPlayer(otro); cabeza.setItemMeta(meta);
            poner(m, 10 + n++, cabeza, q -> precioVenta(q, matricula, otro.getName(), 0));
        }
        if (n == 0) poner(m, 13, item(Material.BARRIER, ChatColor.RED + "No hay nadie cerca", "El comprador tiene que estar a menos de 10 bloques"), null);
        volver(m, 18, this::enMano);
        abrir(p, m);
    }

    private void precioVenta(Player p, String matricula, String comprador, double precio) {
        Menu m = nuevo(3, "Precio para " + comprador);
        int[] pasos = {-5000, -1000, -100};
        for (int i = 0; i < 3; i++) {
            int paso = pasos[i];
            poner(m, 9 + i + 1, item(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + String.format(Locale.ROOT, "%,d Ⓜ", paso)),
                    q -> precioVenta(q, matricula, comprador, Math.max(0, precio + paso)));
            poner(m, 17 - i - 1, item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + String.format(Locale.ROOT, "+%,d Ⓜ", -paso)),
                    q -> precioVenta(q, matricula, comprador, precio - paso));
        }
        poner(m, 13, item(Material.GOLD_INGOT, ChatColor.GOLD + "" + ChatColor.BOLD + (precio == 0 ? "Regalar" : String.format(Locale.ROOT, "%,.2f Ⓜ", precio)),
                "Coche " + matricula + " para " + comprador), null);
        poner(m, 22, item(Material.LIME_CONCRETE, ChatColor.GREEN + "" + ChatColor.BOLD + "Enviar oferta", comprador + " tendrá 60 s para aceptar"), q -> {
            q.closeInventory(); pl.ventas.vender(q, new String[]{"vender", comprador, String.valueOf((long) precio)}); });
        volver(m, 18, q -> elegirComprador(q, matricula));
        abrir(p, m);
    }

    /** Menú que ve el comprador cuando le ofrecen un coche. */
    void oferta(Player p, String vendedor, String modelo, String matricula, double precio) {
        Menu m = nuevo(3, vendedor + " te ofrece un coche");
        poner(m, 13, item(Material.MINECART, ChatColor.GOLD + "" + ChatColor.BOLD + modelo + " " + matricula,
                precio == 0 ? "Te lo regala" : String.format(Locale.ROOT, "Precio: %,.2f Ⓜ", precio),
                "Recibes el coche y una llave nueva", "Luego tendrás que contratarle un seguro"), null);
        poner(m, 11, item(Material.LIME_CONCRETE, ChatColor.GREEN + "" + ChatColor.BOLD + "Aceptar"), q -> {
            q.closeInventory(); pl.ventas.aceptar(q); });
        poner(m, 15, item(Material.RED_CONCRETE, ChatColor.RED + "" + ChatColor.BOLD + "Rechazar"), q -> {
            q.closeInventory(); pl.ventas.rechazar(q); });
        abrir(p, m);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void alClicar(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Bukkit.getScheduler().runTask(pl, () -> { if (p.getOpenInventory().getTopInventory() == m.inv) recolocar(p, m); else limpiar(p); });
        if (e.getClickedInventory() != e.getView().getTopInventory() || (e.getClick().isShiftClick() && e.getCurrentItem() == null)) return;
        ItemStack it = e.getCurrentItem();
        String id = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(kBoton, PersistentDataType.STRING);
        Consumer<Player> accion = id == null ? null : m.acciones.get(id);
        if (accion == null) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.5f, 1.2f);
        Bukkit.getScheduler().runTask(pl, () -> accion.accept(p));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alClicarFinal(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void alArrastrar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void alCerrar(InventoryCloseEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu && e.getPlayer() instanceof Player p)
            Bukkit.getScheduler().runTask(pl, () -> limpiar(p));
    }
}
