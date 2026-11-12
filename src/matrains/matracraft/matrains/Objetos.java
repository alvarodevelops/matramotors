package matracraft.matrains;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Locomotoras y vagones como objeto: se compran en el concesionario y se ponen con clic derecho en un
 * raíl (mirando hacia donde irá el tren). Con mayúsculas + golpe, dentro de una estación tuya, el tren
 * se guarda otra vez como objetos (con su aspecto, combustible y carga).
 */
final class Objetos implements Listener {

    private final Matrains pl;
    private final NamespacedKey kTipo, kAspecto, kLoco, kComb, kCarga, kFluido, kCubos, kLlaveId, kCerr;

    Objetos(Matrains pl) {
        this.pl = pl;
        kTipo = NamespacedKey.fromString("matrains:tipo");
        kAspecto = NamespacedKey.fromString("matrains:aspecto");
        kLoco = NamespacedKey.fromString("matrains:loco");
        kComb = NamespacedKey.fromString("matrains:combustible");
        kCarga = NamespacedKey.fromString("matrains:carga");
        kFluido = NamespacedKey.fromString("matrains:fluido");
        kCubos = NamespacedKey.fromString("matrains:cubos");
        kLlaveId = NamespacedKey.fromString("matrains:llave-tren");
        kCerr = NamespacedKey.fromString("matrains:cerradura-tren");
    }

    /** Aspecto al azar: el primero (el de siempre) sale más a menudo. */
    static int aspectoAzar(String tipo) {
        int n = Modelos.aspectos(tipo).length;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return r.nextDouble() < 0.35 ? 0 : r.nextInt(n);
    }

    ItemStack loco(String tipo, int aspecto, double combustible) {
        Tipos.Loco l = pl.tipos.loco(tipo);
        ItemStack it = new ItemStack(Material.FURNACE_MINECART);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.GOLD + l.nombre() + ChatColor.GRAY + " · " + Modelos.nombre(tipo, aspecto));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Velocidad: " + ChatColor.WHITE + (int) l.velocidad() + " bloques/s");
        lore.add(ChatColor.GRAY + "Arrastra: " + ChatColor.WHITE + (int) l.traccion() + ChatColor.GRAY + " · vagones: " + ChatColor.WHITE + l.vagones());
        lore.add(ChatColor.GRAY + "Funciona con: " + ChatColor.WHITE + switch (l.combustible()) { case "carbon" -> "carbón"; case "gasoleo" -> "gasóleo"; default -> "luz de la red"; });
        if (l.soloPasajeros()) lore.add(ChatColor.GRAY + "Solo coches de pasajeros modernos");
        lore.add(ChatColor.YELLOW + "Clic derecho en un raíl para ponerla");
        m.setLore(lore);
        PersistentDataContainer d = m.getPersistentDataContainer();
        d.set(kTipo, PersistentDataType.STRING, tipo);
        d.set(kAspecto, PersistentDataType.INTEGER, aspecto);
        d.set(kLoco, PersistentDataType.BYTE, (byte) 1);
        d.set(kComb, PersistentDataType.DOUBLE, combustible);
        it.setItemMeta(m);
        return it;
    }

    ItemStack vagon(String tipo, int aspecto) {
        Tipos.Vagon v = pl.tipos.vagon(tipo);
        ItemStack it = new ItemStack(v.pasajeros() ? Material.MINECART : Material.CHEST_MINECART);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.GOLD + v.nombre() + ChatColor.GRAY + " · " + Modelos.nombre(tipo, aspecto));
        List<String> lore = new ArrayList<>();
        if (v.pasajeros()) lore.add(ChatColor.GRAY + "Asientos: " + ChatColor.WHITE + v.asientos() + (v.moderno() ? ChatColor.GRAY + " · moderno" : ""));
        else lore.add(ChatColor.GRAY + "Carga: " + ChatColor.WHITE + Tipos.nombreAcepta(v.acepta()) + ChatColor.GRAY + " (" + v.huecos() + (v.cisterna() ? " cubos)" : " huecos)"));
        lore.add(ChatColor.GRAY + "Peso: " + ChatColor.WHITE + v.pesoVacio() + (v.pasajeros() ? "" : " – " + v.pesoLleno()));
        lore.add(ChatColor.YELLOW + "Clic derecho en un raíl para ponerlo");
        m.setLore(lore);
        PersistentDataContainer d = m.getPersistentDataContainer();
        d.set(kTipo, PersistentDataType.STRING, tipo);
        d.set(kAspecto, PersistentDataType.INTEGER, aspecto);
        it.setItemMeta(m);
        return it;
    }

    private boolean esNuestro(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kTipo, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alUsar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack it = e.getItem();
        if (!esNuestro(it)) return;
        e.setCancelled(true);
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (!Via.esVia(b)) { p.sendMessage(ChatColor.RED + "Ponlo en un raíl."); return; }
        if (!pl.getConfig().getStringList("mundos-permitidos").contains(b.getWorld().getName())) { p.sendMessage(ChatColor.RED + "En este mundo no hay trenes."); return; }
        Red.Linea z = pl.red.zona(b);
        if (z != null && !z.esDueno(p.getUniqueId()) && !p.hasPermission("matrains.admin")) { p.sendMessage(ChatColor.RED + "Esta vía es de otro jugador."); return; }
        PersistentDataContainer d = it.getItemMeta().getPersistentDataContainer();
        String tipo = d.get(kTipo, PersistentDataType.STRING);
        int aspecto = d.getOrDefault(kAspecto, PersistentDataType.INTEGER, 0);
        boolean esLoco = d.has(kLoco, PersistentDataType.BYTE);
        if (esLoco ? pl.tipos.loco(tipo) == null : pl.tipos.vagon(tipo) == null) { p.sendMessage(ChatColor.RED + "Ese tipo ya no existe."); return; }
        if (esLoco) {
            int max = pl.getConfig().getInt("limites.trenes-por-jugador", 3), n = 0;
            for (Tren t : pl.trenes.values()) if (t.conLoco() && t.dueno.equals(p.getUniqueId())) n++;
            if (n >= max && !p.hasPermission("matrains.admin")) { p.sendMessage(ChatColor.RED + "Ya tienes " + max + " trenes en las vías."); return; }
        }
        Via.Pos cabeza = pl.posicion(p, b);
        Tren.Coche c;
        Tren t;
        if (esLoco) {
            Tipos.Loco l = pl.tipos.loco(tipo);
            c = new Tren.Coche(tipo, true, l.largo(), 1, aspecto);
            c.combustible = d.getOrDefault(kComb, PersistentDataType.DOUBLE, 0.0);
            c.llave = d.getOrDefault(kLlaveId, PersistentDataType.STRING, Llaves.codigoNuevo());
            c.cerradura = d.getOrDefault(kCerr, PersistentDataType.INTEGER, 0);
            t = new Tren(pl, UUID.randomUUID().toString().substring(0, 8), p.getUniqueId(), tipo, l.nombre());
        } else {
            c = pl.nuevoVagon(tipo, aspecto);
            t = new Tren(pl, UUID.randomUUID().toString().substring(0, 8), p.getUniqueId(), null, pl.tipos.vagon(tipo).nombre());
        }
        String carga = d.get(kCarga, PersistentDataType.STRING);
        if (carga != null) pl.carga.leer(c, carga);
        c.fluido = d.get(kFluido, PersistentDataType.STRING);
        c.cubos = d.getOrDefault(kCubos, PersistentDataType.INTEGER, 0);
        if (!pl.colocar(t, cabeza, List.of(c))) { p.sendMessage(ChatColor.RED + "No cabe: hace falta más vía."); return; }
        pl.trenes.put(t.id, t);
        pl.reindexar();
        t.aparecer();
        pl.guardar();
        it.setAmount(it.getAmount() - 1);
        p.sendMessage(ChatColor.GREEN + (esLoco ? "Locomotora puesta. Clic derecho para conducir; mayúsculas + clic para su panel."
                : "Vagón puesto. Engánchalo con una cadena (clic derecho) a la cola del tren."));
    }

    /** Mayúsculas + golpe: guarda el tren entero (o los vagones sueltos) como objetos. */
    void guardar(Player p, Tren t) {
        if (!pl.esDueno(p, t)) { p.sendMessage(ChatColor.RED + "Solo el dueño puede guardar el tren."); return; }
        if (Math.abs(t.v) > 0.01 || t.viajando) { p.sendMessage(ChatColor.RED + "El tren tiene que estar parado (y sin ruta en marcha)."); return; }
        if (t.ocupado()) { p.sendMessage(ChatColor.RED + "Que se baje todo el mundo antes."); return; }
        var l = t.lugar(t.coches.get(0));
        Estaciones.Estacion est = l == null ? null : pl.estaciones.en(l);
        if (!p.hasPermission("matrains.admin") && (est == null || !est.dueno.equals(p.getUniqueId()))) {
            p.sendMessage(ChatColor.RED + "Los trenes se guardan dentro de una estación tuya."); return;
        }
        List<ItemStack> objetos = new ArrayList<>();
        for (Tren.Coche c : t.coches) {
            ItemStack it = c.locomotora ? loco(c.tipo, c.aspecto, c.combustible) : vagon(c.tipo, c.aspecto);
            ItemMeta m = it.getItemMeta();
            if (c.locomotora && c.llave != null) {
                m.getPersistentDataContainer().set(kLlaveId, PersistentDataType.STRING, c.llave);
                m.getPersistentDataContainer().set(kCerr, PersistentDataType.INTEGER, c.cerradura);
            }
            String carga = Carga.guardar(c);
            if (!carga.isEmpty()) {
                m.getPersistentDataContainer().set(kCarga, PersistentDataType.STRING, carga);
                List<String> lore = new ArrayList<>(m.getLore());
                lore.add(0, ChatColor.AQUA + "Lleva carga");
                m.setLore(lore);
            }
            if (c.cubos > 0 && c.fluido != null) {
                m.getPersistentDataContainer().set(kFluido, PersistentDataType.STRING, c.fluido);
                m.getPersistentDataContainer().set(kCubos, PersistentDataType.INTEGER, c.cubos);
            }
            it.setItemMeta(m);
            objetos.add(it);
            if (c.carga != null) for (org.bukkit.entity.HumanEntity h : new ArrayList<>(c.carga.getViewers())) h.closeInventory();
        }
        t.desaparecer();
        pl.trenes.remove(t.id);
        pl.reindexar();
        pl.guardar();
        for (ItemStack it : objetos) p.getInventory().addItem(it).values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
        p.sendMessage(ChatColor.YELLOW + "Guardado: " + objetos.size() + (objetos.size() == 1 ? " pieza." : " piezas."));
        Bukkit.getScheduler().runTask(pl.host, p::updateInventory);
    }

    /** Dirección del raíl según hacia dónde mira el jugador. */
    static BlockFace[] sentido(Player p, Block b) {
        BlockFace[] caras = Via.caras(Via.forma(b));
        double r = Math.toRadians(p.getLocation().getYaw());
        double fx = -Math.sin(r), fz = Math.cos(r);
        BlockFace hacia = caras[0].getModX() * fx + caras[0].getModZ() * fz >= caras[1].getModX() * fx + caras[1].getModZ() * fz ? caras[0] : caras[1];
        return new BlockFace[]{hacia == caras[0] ? caras[1] : caras[0], hacia};
    }
}
