package matracraft.coches;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareInventoryResultEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/**
 * Bidón de gasolina: 10 L que se compran en cualquier gasolinera (clic derecho en el surtidor) y se
 * echan con clic derecho a un coche, una embarcación o una locomotora diésel (Matrains lee la misma
 * marca coches:bidon). Por dentro es papel con la pinta del cubo de lava, para que no haga nada más.
 */
final class Bidones implements Listener {

    static final double LITROS = 10.0;

    private final Coches pl;
    final NamespacedKey kBidon = NamespacedKey.fromString("coches:bidon");

    Bidones(Coches pl) { this.pl = pl; }

    // ---------- el objeto ----------

    ItemStack nuevo(double litros) {
        ItemStack it = new ItemStack(Material.PAPER);
        escribir(it, litros);
        return it;
    }

    /** Litros que le quedan, o -1 si no es un bidón. */
    double litros(ItemStack it) {
        if (it == null || it.getType() != Material.PAPER || !it.hasItemMeta()) return -1;
        Double l = it.getItemMeta().getPersistentDataContainer().get(kBidon, PersistentDataType.DOUBLE);
        return l == null ? -1 : l;
    }

    boolean es(ItemStack it) { return litros(it) >= 0; }

    void escribir(ItemStack it, double litros) {
        litros = Math.max(0, Math.round(litros * 10) / 10.0);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(NamespacedKey.minecraft("lava_bucket"));
        m.setDisplayName(ChatColor.RESET + "" + ChatColor.GOLD + "⛽ Bidón de gasolina");
        int celdas = (int) Math.round(litros / LITROS * 10);
        m.setLore(List.of(
                ChatColor.YELLOW + "■".repeat(celdas) + ChatColor.DARK_GRAY + "■".repeat(10 - celdas) + " "
                        + ChatColor.WHITE + String.format(Locale.ROOT, "%.1f / %.0f L", litros, LITROS),
                "",
                ChatColor.GRAY + "Clic derecho a un coche, una embarcación",
                ChatColor.GRAY + "o una locomotora diésel para echarle gasolina.",
                ChatColor.GRAY + "Se rellena en cualquier gasolinera."));
        m.addItemFlags(ItemFlag.values());
        m.getPersistentDataContainer().set(kBidon, PersistentDataType.DOUBLE, litros);
        // los llenos se apilan entre sí; los empezados no se mezclan con nada
        m.setMaxStackSize(litros >= LITROS - 1e-6 ? 16 : 1);
        it.setItemMeta(m);
    }

    // ---------- echar gasolina ----------

    /** Clic con el bidón en la mano sobre un vehículo de MATRAMOTORS. */
    void echar(Player p, ArmorStand cart) {
        ItemStack mano = p.getInventory().getItemInMainHand();
        double tengo = litros(mano);
        if (tengo < 0) return;
        Tier tier = pl.conduccion.tierDe(cart);
        if (tier == null) return;
        if (tier.sinMotor()) { p.sendMessage(ChatColor.GRAY + "Este vehículo no gasta gasolina."); return; }
        if (tengo < 0.05) { p.sendMessage(ChatColor.RED + "El bidón está vacío. Rellénalo en una gasolinera."); return; }
        Conduccion.Estado st = pl.conduccion.conduciendo.get(cart.getUniqueId());
        double actual = st != null ? st.gasolina : pl.conduccion.gasolinaDe(cart);
        double falta = tier.deposito() - actual;
        if (falta < 0.05) { p.sendMessage(ChatColor.GRAY + "El depósito ya está lleno."); return; }
        double echa = Math.min(falta, tengo);
        if (st != null) { st.gasolina += echa; pl.conduccion.guardar(st); }
        else cart.getPersistentDataContainer().set(pl.kGasolina, PersistentDataType.DOUBLE, actual + echa);
        gastar(p, mano, tengo - echa);
        p.playSound(cart.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, SoundCategory.PLAYERS, 0.8f, 1.3f);
        cart.getWorld().spawnParticle(Particle.FALLING_DRIPSTONE_LAVA, cart.getLocation().add(0, 1.0, 0), 6, 0.2, 0.1, 0.2, 0);
        pl.carnet.sumar(p, "litros", echa);
        p.sendMessage(ChatColor.GREEN + String.format(Locale.ROOT, "⛽ Echados %.1f L al %s. Depósito: %.1f / %.0f L.",
                echa, tier.nombre(), actual + echa, tier.deposito())
                + (tengo - echa >= 0.05 ? ChatColor.GRAY + String.format(Locale.ROOT, " (quedan %.1f L en el bidón)", tengo - echa) : ""));
    }

    /** Deja en la mano lo que sobra: si había un montón, se separa el bidón empezado. */
    private void gastar(Player p, ItemStack mano, double queda) {
        if (mano.getAmount() > 1) {
            mano.setAmount(mano.getAmount() - 1);
            ItemStack uno = nuevo(queda);
            p.getInventory().addItem(uno).values().forEach(x -> p.getWorld().dropItem(p.getLocation(), x));
        } else escribir(mano, queda);
    }

    // ---------- comprar y rellenar en el surtidor ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void alClicarSurtidor(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (!pl.gasolineras.esSurtidor(b) || e.getPlayer().getVehicle() != null || e.getPlayer().isSneaking()) return;
        e.setCancelled(true);
        pl.menus.bidones(e.getPlayer(), b);
    }

    /** Litros que faltan para llenar todos los bidones empezados del inventario. */
    double faltaRellenar(Player p) {
        double falta = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            double l = litros(it);
            if (l >= 0) falta += (LITROS - l) * it.getAmount();
        }
        return falta;
    }

    void comprar(Player p, Block surtidor, int cuantos) {
        double precio = Math.round(pl.gasolineras.precioEn(surtidor) * LITROS * cuantos * 100) / 100.0;
        if (!cobrar(p, precio)) return;
        ItemStack it = nuevo(LITROS);
        it.setAmount(cuantos);
        p.getInventory().addItem(it).values().forEach(x -> p.getWorld().dropItem(p.getLocation(), x));
        p.playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, SoundCategory.PLAYERS, 0.8f, 1.2f);
        pl.carnet.sumar(p, "gasto-gasolina", precio);
        p.sendMessage(ChatColor.GREEN + "Has comprado " + cuantos + (cuantos == 1 ? " bidón" : " bidones") + " de gasolina por "
                + String.format(Locale.ROOT, "%.2f Ⓜ", precio) + ".");
    }

    void rellenar(Player p, Block surtidor) {
        double falta = faltaRellenar(p);
        if (falta < 0.05) { p.sendMessage(ChatColor.GRAY + "No llevas bidones que rellenar."); return; }
        double precio = Math.round(pl.gasolineras.precioEn(surtidor) * falta * 100) / 100.0;
        if (!cobrar(p, precio)) return;
        Inventory inv = p.getInventory();
        for (ItemStack it : inv.getContents()) if (litros(it) >= 0) escribir(it, LITROS);
        p.playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, SoundCategory.PLAYERS, 0.8f, 1.2f);
        pl.carnet.sumar(p, "gasto-gasolina", precio);
        p.sendMessage(ChatColor.GREEN + String.format(Locale.ROOT, "Bidones rellenados: %.1f L por %.2f Ⓜ.", falta, precio));
    }

    private boolean cobrar(Player p, double precio) {
        if (pl.eco == null) { p.sendMessage(ChatColor.RED + "La economía no está disponible."); return false; }
        if (!pl.eco.has(p, precio) || !pl.eco.withdrawPlayer(p, precio).transactionSuccess()) {
            p.sendMessage(ChatColor.RED + "No te llega: cuesta " + String.format(Locale.ROOT, "%.2f Ⓜ", precio) + ".");
            return false;
        }
        return true;
    }

    // ---------- que el papel no se use como papel ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alFabricar(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) if (es(it)) { e.getInventory().setResult(null); return; }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alPrepararResultado(PrepareInventoryResultEvent e) {
        for (ItemStack it : e.getInventory().getContents()) if (es(it)) { e.setResult(null); return; }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void alComerciar(InventoryClickEvent e) {
        InventoryType tipo = e.getView().getTopInventory().getType();
        if (tipo != InventoryType.MERCHANT && tipo != InventoryType.CARTOGRAPHY && tipo != InventoryType.LOOM
                && tipo != InventoryType.CRAFTER) return;
        if (es(e.getCurrentItem()) || es(e.getCursor())
                || (e.getHotbarButton() >= 0 && es(e.getWhoClicked().getInventory().getItem(e.getHotbarButton())))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void alPonerBloque(BlockPlaceEvent e) {
        if (es(e.getItemInHand())) e.setCancelled(true);
    }

}
