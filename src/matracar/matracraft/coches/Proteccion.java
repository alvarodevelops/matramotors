package matracraft.coches;

import org.bukkit.block.BlockState;
import org.bukkit.block.Crafter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Impide usar los objetos del plugin (llaves, matrículas, tickets) en dispensadores y crafteos. */
final class Proteccion implements Listener {
    private final Coches pl;

    Proteccion(Coches pl) { this.pl = pl; }

    private boolean nuestro(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer datos = item.getItemMeta().getPersistentDataContainer();
        return datos.has(pl.kMatricula, PersistentDataType.STRING) || datos.has(pl.kLlave, PersistentDataType.STRING)
                || datos.has(pl.kTicket, PersistentDataType.BYTE) || datos.has(pl.kTicket, PersistentDataType.INTEGER)
                || datos.has(pl.kTicket, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alDispensar(BlockDispenseEvent e) {
        if (nuestro(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alCraftear(PrepareItemCraftEvent e) {
        for (ItemStack item : e.getInventory().getMatrix()) if (nuestro(item)) { e.getInventory().setResult(null); return; }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alCraftearSolo(CrafterCraftEvent e) {
        if (e.getBlock().getState() instanceof Crafter crafter) {
            for (ItemStack item : crafter.getInventory().getContents()) if (nuestro(item)) { e.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void alMorir(PlayerDeathEvent e) {
        Player jugador = e.getEntity();
        if (jugador.getVehicle() instanceof ArmorStand coche && pl.conduccion.esCoche(coche)) {
            Conduccion.Estado estado = pl.conduccion.conduciendo.remove(coche.getUniqueId());
            if (estado != null) pl.conduccion.guardar(estado);
            pl.conduccion.frenoDeMano(coche);
            pl.gasolineras.parar(coche.getUniqueId(), jugador);
        }
    }
}
