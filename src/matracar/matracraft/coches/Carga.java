package matracraft.coches;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maletero de los coches: el contenido se guarda en la propia vagoneta (PDC, kCarga).
 */
final class Carga implements Listener {

    // Dueño del inventario abierto: así sabemos a qué coche pertenece al cerrarlo
    static final class Hueco implements InventoryHolder {
        final UUID cart; Inventory inv;
        Hueco(UUID cart) { this.cart = cart; }
        public Inventory getInventory() { return inv; }
    }

    private final Coches pl;
    private final Map<UUID, Inventory> abiertas = new HashMap<>();

    Carga(Coches pl) { this.pl = pl; }

    private ItemStack[] leer(Entity coche, int huecos) {
        byte[] datos = coche.getPersistentDataContainer().get(pl.kCarga, PersistentDataType.BYTE_ARRAY);
        ItemStack[] items = new ItemStack[huecos];
        if (datos == null) return items;
        ItemStack[] guardados = ItemStack.deserializeItemsFromBytes(datos);
        for (int i = 0; i < Math.min(huecos, guardados.length); i++) items[i] = guardados[i];
        return items;
    }

    private void escribir(Entity coche, ItemStack[] items) {
        boolean vacio = true;
        for (ItemStack it : items) if (it != null && !it.isEmpty()) { vacio = false; break; }
        if (vacio) coche.getPersistentDataContainer().remove(pl.kCarga);
        else coche.getPersistentDataContainer().set(pl.kCarga, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(items));
    }

    void abrir(Player p, ArmorStand coche, Tier tier) {
        Inventory inv = abiertas.get(coche.getUniqueId());
        if (inv == null) {
            Hueco hueco = new Hueco(coche.getUniqueId());
            String titulo = ChatColor.DARK_GRAY + (tier.agua() ? "Bodega · " : "Maletero · ") + tier.nombre() + " " + pl.conduccion.matriculaDe(coche);
            // menos de 9 huecos: guantera de 5 (inventario de tolva)
            inv = tier.carga() < 9 ? Bukkit.createInventory(hueco, org.bukkit.event.inventory.InventoryType.HOPPER, titulo)
                    : Bukkit.createInventory(hueco, tier.carga(), titulo);
            hueco.inv = inv;
            inv.setContents(leer(coche, tier.carga()));
            abiertas.put(coche.getUniqueId(), inv);
        }
        p.openInventory(inv);
        p.playSound(coche.getLocation(), Sound.BLOCK_BARREL_OPEN, SoundCategory.PLAYERS, 0.8f, 0.9f);
    }

    void cerrar(Entity coche) {
        Inventory inv = abiertas.remove(coche.getUniqueId());
        if (inv == null) return;
        escribir(coche, inv.getContents());
        for (HumanEntity h : new ArrayList<>(inv.getViewers())) h.closeInventory();
    }

    /** Fracción de huecos ocupados del maletero (0-1). */
    double ocupacion(Entity coche, Tier tier) {
        if (tier.carga() <= 0) return 0;
        Inventory abierta = abiertas.get(coche.getUniqueId());
        ItemStack[] items = abierta != null ? abierta.getContents() : leer(coche, tier.carga());
        int llenos = 0;
        for (ItemStack it : items) if (it != null && !it.isEmpty()) llenos++;
        return llenos / (double) items.length;
    }

    // Cierra el maletero y dice si se ha quedado sin nada guardado
    boolean vacia(Entity coche) {
        cerrar(coche);
        return !coche.getPersistentDataContainer().has(pl.kCarga, PersistentDataType.BYTE_ARRAY);
    }

    /** Tira al suelo todo lo que lleve el maletero. */
    void soltar(Entity coche, Location donde) {
        cerrar(coche);
        byte[] datos = coche.getPersistentDataContainer().get(pl.kCarga, PersistentDataType.BYTE_ARRAY);
        if (datos == null) return;
        for (ItemStack it : ItemStack.deserializeItemsFromBytes(datos))
            if (it != null && !it.isEmpty()) donde.getWorld().dropItemNaturally(donde, it);
        coche.getPersistentDataContainer().remove(pl.kCarga);
    }

    void cerrarTodo() {
        for (UUID id : new ArrayList<>(abiertas.keySet())) {
            Entity coche = Bukkit.getEntity(id);
            if (coche != null) cerrar(coche);
        }
        abiertas.clear();
    }

    @EventHandler
    public void alDescargar(EntitiesUnloadEvent e) {
        for (Entity ent : e.getEntities()) if (abiertas.containsKey(ent.getUniqueId())) cerrar(ent);
    }

    @EventHandler
    public void alCerrar(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Hueco hueco)) return;
        Inventory inv = abiertas.get(hueco.cart);
        if (inv == null || inv.getViewers().size() > 1) return;
        Entity coche = Bukkit.getEntity(hueco.cart);
        if (coche != null && coche.isValid()) {
            escribir(coche, inv.getContents());
            // La Goma enseña los fardos según lo cargada que vaya
            Tier tier = pl.conduccion.tierDe(coche);
            if (tier != null && tier.id().equals("goma") && coche instanceof ArmorStand cart) pl.modelos.rehacer(cart);
        }
        else for (ItemStack it : inv.getContents())
            if (it != null && !it.isEmpty()) e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), it);
        abiertas.remove(hueco.cart);
        if (e.getPlayer() instanceof Player p) p.playSound(p.getLocation(), Sound.BLOCK_BARREL_CLOSE, SoundCategory.PLAYERS, 0.8f, 0.9f);
    }
}
