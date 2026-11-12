package matracraft.coches;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Ventana con los coches de otros jugadores de los que llevas la llave. */
final class Prestados implements Listener {

    static final class Ventana implements InventoryHolder {
        final Map<Integer, String> coches = new HashMap<>();
        Inventory inv;
        ItemStack[] base;

        public Inventory getInventory() { return inv; }
    }

    private final Coches pl;
    private final NamespacedKey kExtra;

    Prestados(Coches pl) { this.pl = pl; this.kExtra = new NamespacedKey(pl, "boton_extra"); }

    /** Matrícula -> dueño de cada llave ajena que lleva el jugador. */
    Map<String, String> de(Player p) {
        Map<String, String> coches = new LinkedHashMap<>();
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || !it.hasItemMeta()) continue;
            String matricula = it.getItemMeta().getPersistentDataContainer().get(pl.kLlave, PersistentDataType.STRING);
            if (matricula == null || coches.containsKey(matricula) || !pl.tieneLlave(p, matricula)) continue;
            String dueno = Extra.dueno(pl, matricula);
            if (dueno != null && !dueno.equalsIgnoreCase(p.getName())) coches.put(matricula, dueno);
        }
        return coches;
    }

    Location donde(String matricula) {
        for (World mundo : Bukkit.getWorlds())
            for (ArmorStand m : mundo.getEntitiesByClass(ArmorStand.class))
                if (pl.conduccion.esCoche(m) && matricula.equals(pl.conduccion.matriculaDe(m))) return m.getLocation();
        return pl.seguros.ubicacion(matricula);
    }

    private static ItemStack item(Material mat, String nombre, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(nombre);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    void abrir(Player p) {
        Map<String, String> coches = de(p);
        int filas = Math.max(3, Math.min(6, 2 + (coches.size() + 6) / 7));
        Ventana v = new Ventana();
        v.inv = Bukkit.createInventory(v, filas * 9, "Coches prestados");
        ItemStack fondo = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < v.inv.getSize(); i++) v.inv.setItem(i, fondo);
        if (coches.isEmpty())
            v.inv.setItem(13, item(Material.PAPER, "§7No llevas llaves de coches de otros",
                    List.of("§8Si alguien te presta su coche,", "§8con su llave lo verás aquí.")));

        int slot = 10;
        for (Map.Entry<String, String> e : coches.entrySet()) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= v.inv.getSize() - 9) break;
            String matricula = e.getKey();
            String nombre = "Coche";
            Material tipo = Material.MINECART;
            try {
                YamlConfiguration y = registro();
                String c = y.getString("matriculas." + matricula.replace(' ', '_') + ".coche");
                if (c != null) nombre = c;
            } catch (Throwable ignorado) {}
            Location donde = donde(matricula);
            List<String> lore = new ArrayList<>();
            lore.add("§7Dueño: §f" + e.getValue());
            lore.add(donde == null ? "§7No sé dónde está"
                    : "§7Está en " + donde.getWorld().getName() + " " + donde.getBlockX() + ", " + donde.getBlockY() + ", " + donde.getBlockZ());
            lore.add("");
            lore.add("§7Puedes conducirlo y pedir la grúa.");
            lore.add("§8No puedes venderlo, desguazarlo, cambiarle");
            lore.add("§8la matrícula, copiar la llave ni asegurarlo.");
            if (donde != null) { lore.add(""); lore.add("§eClic para que el GPS te lleve"); }
            v.inv.setItem(slot, item(tipo, "§f§l" + nombre + " §8" + matricula, lore));
            v.coches.put(slot, matricula);
            slot++;
        }
        v.inv.setItem(v.inv.getSize() - 9, item(Material.ARROW, "§7« Volver", List.of()));
        v.inv.setItem(v.inv.getSize() - 1, item(Material.BARRIER, "§cCerrar", List.of()));
        v.base = v.inv.getContents().clone();
        p.openInventory(v.inv);
        Bukkit.getScheduler().runTask(pl, () -> restaurar(v));
    }

    // El YamlConfiguration de Registro es privado: se lee por reflexión
    private YamlConfiguration registro() throws Exception {
        Field f = Registro.class.getDeclaredField("y");
        f.setAccessible(true);
        return (YamlConfiguration) f.get(pl.registro);
    }

    private static void restaurar(Ventana v) {
        for (int i = 0; i < v.base.length; i++)
            if (!Objects.equals(v.inv.getItem(i), v.base[i])) v.inv.setItem(i, v.base[i]);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void alClicar(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Ventana v)) return;
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(pl, () -> restaurar(v));
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == v.inv.getSize() - 1) { p.closeInventory(); return; }
        if (slot == v.inv.getSize() - 9) { Bukkit.getScheduler().runTask(pl, () -> pl.menus.principal(p)); return; }
        String matricula = v.coches.get(slot);
        if (matricula == null) return;
        Location donde = donde(matricula);
        if (donde == null) { p.sendMessage("§cNo sé dónde está ese coche."); return; }
        p.closeInventory();
        pl.gps.guiar(p, donde, "Coche " + matricula);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void alArrastrar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Ventana) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void alComando(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().toLowerCase(Locale.ROOT).trim();
        if (msg.equals("/coche prestados") || msg.equals("/coches prestados") || msg.equals("/matracar:coche prestados")) {
            e.setCancelled(true);
            abrir(e.getPlayer());
        }
    }

    /** Añade al menú principal (si está abierto) el botón de coches prestados, en el hueco libre más cercano a "Mis coches". */
    void botonEnPrincipal(Player p) {
        Bukkit.getScheduler().runTask(pl, () -> {
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof Menus.Menu m) || m.base == null) return;
            if (m.acciones.containsKey("prestados")) return;
            int n = de(p).size();
            NamespacedKey kBoton;
            try {
                Field f = Menus.class.getDeclaredField("kBoton");
                f.setAccessible(true);
                kBoton = (NamespacedKey) f.get(pl.menus);
            } catch (Throwable t) { return; }

            int ancla = -1;
            for (int i = 0; i < m.inv.getSize(); i++) {
                ItemStack it = m.base[i];
                if (it != null && it.hasItemMeta() && it.getItemMeta().hasDisplayName()
                        && it.getItemMeta().getDisplayName().toLowerCase(Locale.ROOT).contains("mis coches")) { ancla = i; break; }
            }
            int mejor = -1, menor = Integer.MAX_VALUE, filas = m.inv.getSize() / 9;
            for (int i = 9; i < m.inv.getSize() - 9; i++) {
                ItemStack it = m.base[i];
                boolean libre = it == null || (it.getType().name().endsWith("STAINED_GLASS_PANE")
                        && (!it.hasItemMeta() || !it.getItemMeta().getPersistentDataContainer().has(kBoton, PersistentDataType.STRING)));
                if (!libre || i % 9 == 0 || i % 9 == 8) continue;
                int d = ancla < 0 ? i : Math.abs(i / 9 - ancla / 9) * 9 + Math.abs(i % 9 - ancla % 9);
                if (d < menor) { menor = d; mejor = i; }
            }
            if (mejor < 0 || filas < 3) return;
            ItemStack boton = item(Material.NAME_TAG, "§b§lCoches prestados", List.of("§7Coches de otros de los que",
                    "§7llevas la llave: " + (n == 0 ? "§8ninguno" : "§f" + n), "", "§eClic para verlos"));
            ItemMeta meta = boton.getItemMeta();
            meta.getPersistentDataContainer().set(kBoton, PersistentDataType.STRING, "prestados");
            boton.setItemMeta(meta);
            m.acciones.put("prestados", this::abrir);
            m.inv.setItem(mejor, boton);
            if (mejor < m.base.length) m.base[mejor] = boton.clone();
        });
    }
}
