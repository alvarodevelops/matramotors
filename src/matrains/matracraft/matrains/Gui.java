package matracraft.matrains;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Menús de cofre con botones (estilo MatraPanel: borde de cristal gris y prefijo [Matrains]). */
final class Gui implements InventoryHolder, Listener {

    static final String PREFIJO = ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "Matrains" + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;

    private Inventory inv;
    private final Map<Integer, BiConsumer<Player, ClickType>> acciones = new HashMap<>();

    Gui() {}

    Gui(int filas, String titulo) {
        inv = Bukkit.createInventory(this, filas * 9, PREFIJO + titulo);
        ItemStack borde = boton(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, borde);
    }

    @Override public Inventory getInventory() { return inv; }

    Gui poner(int hueco, ItemStack it, BiConsumer<Player, ClickType> accion) {
        inv.setItem(hueco, it);
        if (accion != null) acciones.put(hueco, accion);
        else acciones.remove(hueco);
        return this;
    }

    Gui vaciar(int hueco) {
        inv.setItem(hueco, null);
        acciones.remove(hueco);
        return this;
    }

    void abrir(Player p) { p.openInventory(inv); }

    static ItemStack boton(Material m, String nombre, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(nombre);
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(s);
        meta.setLore(l);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    static ItemStack boton(Material m, String nombre, List<String> lore) {
        return boton(m, nombre, lore.toArray(new String[0]));
    }

    @EventHandler
    public void alClicar(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Gui g)) return;
        e.setCancelled(true);
        if (e.getRawSlot() >= e.getView().getTopInventory().getSize() || !(e.getWhoClicked() instanceof Player p)) return;
        BiConsumer<Player, ClickType> a = g.acciones.get(e.getRawSlot());
        if (a == null) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.5f, 1.2f);
        a.accept(p, e.getClick());
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Gui) e.setCancelled(true);
    }
}
