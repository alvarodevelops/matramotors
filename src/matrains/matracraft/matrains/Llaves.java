package matracraft.matrains;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Llaves de tren: el dueño hace copias en el panel de la locomotora y se las da a quien quiera. Con la
 * llave encima se conduce, se reposta, se abre la carga y se maneja la ruta; guardar, enganchar,
 * cambiar el nombre, el billete y las llaves sigue siendo cosa del dueño. Cambiar la cerradura anula
 * todas las copias.
 */
final class Llaves {

    private final Matrains pl;
    private final NamespacedKey kLlave, kCerradura;

    Llaves(Matrains pl) {
        this.pl = pl;
        kLlave = new NamespacedKey(pl, "llave");
        kCerradura = new NamespacedKey(pl, "cerradura");
    }

    static String codigoNuevo() {
        String letras = "BCDFGHJKLMNPRSTVWXYZ";
        ThreadLocalRandom r = ThreadLocalRandom.current();
        StringBuilder s = new StringBuilder("T-");
        for (int i = 0; i < 3; i++) s.append(r.nextInt(10));
        for (int i = 0; i < 2; i++) s.append(letras.charAt(r.nextInt(letras.length())));
        return s.toString();
    }

    /** ¿Lleva encima una llave buena de este tren? */
    boolean tiene(Player p, Tren t) {
        Tren.Coche loco = t.conLoco() ? t.locomotora() : null;
        if (loco == null || loco.llave == null) return false;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType() != Material.TRIPWIRE_HOOK || !it.hasItemMeta()) continue;
            PersistentDataContainer d = it.getItemMeta().getPersistentDataContainer();
            if (loco.llave.equals(d.get(kLlave, PersistentDataType.STRING))
                    && d.getOrDefault(kCerradura, PersistentDataType.INTEGER, -1) == loco.cerradura) return true;
        }
        return false;
    }

    ItemStack objeto(Tren t) {
        Tren.Coche loco = t.locomotora();
        ItemStack it = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.YELLOW + "Llave de tren · " + ChatColor.WHITE + loco.llave);
        m.setLore(List.of(ChatColor.GRAY + "Tren: " + ChatColor.WHITE + t.nombre,
                ChatColor.GRAY + "Dueño: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(t.dueno).getName(),
                "",
                ChatColor.GRAY + "Llévala encima para conducirlo, repostarlo,",
                ChatColor.GRAY + "abrir su carga y manejar su ruta."));
        m.getPersistentDataContainer().set(kLlave, PersistentDataType.STRING, loco.llave);
        m.getPersistentDataContainer().set(kCerradura, PersistentDataType.INTEGER, loco.cerradura);
        m.setEnchantmentGlintOverride(true);
        m.setMaxStackSize(1);
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    void copiar(Player p, Tren t) {
        if (!pl.esDueno(p, t) || !t.conLoco()) return;
        double precio = pl.getConfig().getDouble("llaves.precio-copia", 500);
        if (pl.eco != null && precio > 0) {
            if (!pl.eco.has(p, precio)) { p.sendMessage(ChatColor.RED + String.format("Una copia cuesta %.2f Ⓜ y no te llega.", precio)); return; }
            pl.eco.withdrawPlayer(p, precio);
        }
        p.getInventory().addItem(objeto(t)).values().forEach(x -> p.getWorld().dropItem(p.getLocation(), x));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.5f, 1.6f);
        p.sendMessage(ChatColor.GREEN + "Copia de la llave hecha" + (precio > 0 ? String.format(" (%.2f Ⓜ)", precio) : "")
                + ". Dásela a quien quieras: con ella encima podrá usar el tren.");
    }

    void cambiarCerradura(Player p, Tren t) {
        if (!pl.esDueno(p, t) || !t.conLoco()) return;
        t.locomotora().cerradura++;
        pl.guardar();
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, 1f, 0.8f);
        p.sendMessage(ChatColor.YELLOW + "Cerradura cambiada: todas las copias de la llave dejan de valer.");
        // quien vaya conduciendo con una llave vieja se baja
        Tren.Coche loco = t.locomotora();
        if (loco.base != null) for (org.bukkit.entity.Entity e : loco.base.getPassengers())
            if (e instanceof Player q && !pl.puedeUsar(q, t)) { loco.base.removePassenger(q); q.sendMessage(ChatColor.RED + "Han cambiado la cerradura del tren."); }
    }
}
