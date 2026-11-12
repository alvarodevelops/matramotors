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
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/** Carga de los vagones de mercancías: inventario con filtro según el vagón y depósito de la cisterna. */
final class Carga implements Listener {

    private final Matrains pl;

    Carga(Matrains pl) { this.pl = pl; }

    static final class Bodega implements InventoryHolder {
        final Tren.Coche coche;
        Inventory inv;
        Bodega(Tren.Coche coche) { this.coche = coche; }
        @Override public Inventory getInventory() { return inv; }
    }

    Inventory inventario(Tren.Coche c, Tipos.Vagon tv) {
        if (c.carga == null) {
            Bodega b = new Bodega(c);
            int huecos = Math.max(9, Math.min(54, (tv.huecos() + 8) / 9 * 9));
            b.inv = Bukkit.createInventory(b, huecos, tv.nombre() + " (" + Tipos.nombreAcepta(tv.acepta()) + ")");
            c.carga = b.inv;
        }
        return c.carga;
    }

    void abrir(Player p, Tren t, Tren.Coche c) {
        Tipos.Vagon tv = pl.tipos.vagon(c.tipo);
        if (tv == null || tv.pasajeros()) return;
        if (!pl.puedeUsar(p, t)) { p.sendMessage(ChatColor.RED + "Solo el dueño (o quien tenga su llave) abre este vagón."); return; }
        if (tv.cisterna()) { cisterna(p, t, c, tv); return; }
        p.openInventory(inventario(c, tv));
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, SoundCategory.PLAYERS, 0.8f, 0.9f);
    }

    /** La cisterna se llena y se vacía con cubos (agua, lava o leche; un solo líquido a la vez). */
    private void cisterna(Player p, Tren t, Tren.Coche c, Tipos.Vagon tv) {
        ItemStack mano = p.getInventory().getItemInMainHand();
        Material m = mano.getType();
        String liquido = switch (m) { case WATER_BUCKET -> "agua"; case LAVA_BUCKET -> "lava"; case MILK_BUCKET -> "leche"; default -> null; };
        if (liquido != null) {
            if (c.cubos > 0 && !liquido.equals(c.fluido)) { p.sendMessage(ChatColor.RED + "Esta cisterna lleva " + c.fluido + ". Vacíala antes."); return; }
            if (c.cubos >= tv.huecos()) { p.sendMessage(ChatColor.RED + "La cisterna está llena."); return; }
            c.fluido = liquido;
            c.cubos++;
            mano.setAmount(mano.getAmount() - 1);
            p.getInventory().addItem(new ItemStack(Material.BUCKET)).values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
            p.playSound(p.getLocation(), Sound.ITEM_BUCKET_EMPTY, SoundCategory.PLAYERS, 0.8f, 1f);
        } else if (m == Material.BUCKET) {
            if (c.cubos <= 0) { p.sendMessage(ChatColor.GRAY + "La cisterna está vacía."); return; }
            Material lleno = switch (c.fluido) { case "lava" -> Material.LAVA_BUCKET; case "leche" -> Material.MILK_BUCKET; default -> Material.WATER_BUCKET; };
            c.cubos--;
            mano.setAmount(mano.getAmount() - 1);
            p.getInventory().addItem(new ItemStack(lleno)).values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
            if (c.cubos == 0) c.fluido = null;
            p.playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL, SoundCategory.PLAYERS, 0.8f, 1f);
        }
        p.sendActionBar(net.kyori.adventure.text.Component.text("Cisterna: " + c.cubos + " / " + tv.huecos() + " cubos"
                + (c.fluido == null ? "" : " de " + c.fluido) + " · con un cubo lleno la llenas, con uno vacío la vacías"));
    }

    /** ¿Puede entrar este objeto en la bodega? */
    private boolean cabe(Bodega b, ItemStack it) {
        if (it == null || it.getType().isAir()) return true;
        Tipos.Vagon tv = pl.tipos.vagon(b.coche.tipo);
        if (tv == null || !Tipos.admite(tv.acepta(), it)) return false;
        if (tv.acepta().equals("granel")) {
            for (ItemStack x : b.inv.getContents())
                if (x != null && !x.getType().isAir() && x.getType() != it.getType()) return false;
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void alClicar(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Bodega b)) return;
        boolean arriba = e.getRawSlot() < e.getView().getTopInventory().getSize();
        ItemStack entra = null;
        if (arriba) {
            if (e.getClick() == ClickType.NUMBER_KEY) entra = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
            else if (e.getClick() == ClickType.SWAP_OFFHAND) entra = e.getWhoClicked().getInventory().getItem(EquipmentSlot.OFF_HAND);
            else if (e.getAction() != InventoryAction.PICKUP_ALL && e.getAction() != InventoryAction.PICKUP_HALF
                    && e.getAction() != InventoryAction.PICKUP_ONE && e.getAction() != InventoryAction.PICKUP_SOME
                    && e.getAction() != InventoryAction.DROP_ONE_SLOT && e.getAction() != InventoryAction.DROP_ALL_SLOT
                    && e.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) entra = e.getCursor();
        } else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) entra = e.getCurrentItem();
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) return;
        if (!cabe(b, entra)) {
            e.setCancelled(true);
            Tipos.Vagon tv = pl.tipos.vagon(b.coche.tipo);
            e.getWhoClicked().sendMessage(ChatColor.RED + "Este vagón admite " + (tv == null ? "otra cosa" : Tipos.nombreAcepta(tv.acepta())) + ".");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void alArrastrar(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Bodega b)) return;
        int top = e.getView().getTopInventory().getSize();
        boolean toca = e.getRawSlots().stream().anyMatch(s -> s < top);
        if (toca && !cabe(b, e.getOldCursor())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ guardado

    static String guardar(Tren.Coche c) {
        if (c.carga == null) return "";
        ItemStack[] it = c.carga.getContents().clone();
        boolean vacio = true;
        for (int i = 0; i < it.length; i++) {
            if (it[i] == null) it[i] = ItemStack.empty();
            else if (!it[i].getType().isAir()) vacio = false;
        }
        return vacio ? "" : Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(it));
    }

    void leer(Tren.Coche c, String texto) {
        if (texto == null || texto.isEmpty()) return;
        Tipos.Vagon tv = pl.tipos.vagon(c.tipo);
        if (tv == null) return;
        ItemStack[] it = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(texto));
        Inventory inv = inventario(c, tv);
        for (int i = 0; i < it.length && i < inv.getSize(); i++) inv.setItem(i, it[i]);
        // lo que no quepa (si se cambió el tamaño) no se pierde: se guarda en los huecos libres
        for (int i = inv.getSize(); i < it.length; i++) if (it[i] != null && !it[i].getType().isAir()) inv.addItem(it[i]);
    }
}
