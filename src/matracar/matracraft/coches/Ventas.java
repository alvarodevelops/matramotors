package matracraft.coches;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Venta de coches entre jugadores (/coche vender, aceptar, rechazar) y desguace. */
final class Ventas {

    private record Oferta(UUID vendedor, String matricula, double precio, long caduca) {}

    private static final long VALIDEZ_MS = 60_000L;

    private final Coches pl;
    private final Map<UUID, Oferta> ofertas = new HashMap<>();
    private final Map<UUID, Oferta> desguaces = new HashMap<>();

    Ventas(Coches pl) { this.pl = pl; }

    void vender(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(ChatColor.GOLD + "Vender un coche: " + ChatColor.GRAY + "guárdalo, ponlo en la mano, ten su llave y escribe "
                    + ChatColor.WHITE + "/coche vender <jugador> <precio>" + ChatColor.GRAY + " (precio 0 = regalarlo).");
            p.sendMessage(ChatColor.GRAY + "El comprador tiene que estar cerca. Tus llaves y copias dejan de valer y tu seguro de ese coche se anula.");
            return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        PersistentDataContainer pdc = item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer() : null;
        String matricula = pdc == null ? null : pdc.get(pl.kMatricula, PersistentDataType.STRING);
        if (item.getType() != Material.MINECART || matricula == null) { p.sendMessage(ChatColor.RED + "Pon el coche (guardado) en la mano."); return; }
        if (!pl.deEstaTanda(pdc)) { p.sendMessage(ChatColor.RED + "Ese coche ya no vale."); return; }
        if (!Extra.llaveYDueno(pl, p, matricula)) { p.sendMessage(ChatColor.RED + "Necesitas la llave de " + matricula + " para venderlo."); return; }
        Player comprador = Bukkit.getPlayerExact(args[1]);
        if (comprador == null || comprador.equals(p)) { p.sendMessage(ChatColor.RED + "Ese jugador no está conectado."); return; }
        if (comprador.getWorld() != p.getWorld() || comprador.getLocation().distance(p.getLocation()) > 10) {
            p.sendMessage(ChatColor.RED + comprador.getName() + " tiene que estar a tu lado (menos de 10 bloques).");
            return;
        }
        double precio;
        try {
            precio = Math.round(Double.parseDouble(args[2].replace(",", ".")));
        } catch (NumberFormatException e) { p.sendMessage(ChatColor.RED + "Precio no válido."); return; }
        if (precio < 0) { p.sendMessage(ChatColor.RED + "Precio no válido."); return; }
        Tier tier = pl.tiers.get(pdc.get(pl.kTier, PersistentDataType.STRING));
        String nombre = tier == null ? "coche" : tier.nombre();
        ofertas.put(comprador.getUniqueId(), new Oferta(p.getUniqueId(), matricula, precio, System.currentTimeMillis() + VALIDEZ_MS));
        String texto = precio == 0 ? "te lo regala" : String.format(Locale.ROOT, "te lo vende por %,.2f Ⓜ", precio);
        comprador.sendMessage(Component.text("🚗 " + p.getName() + " " + texto + ": " + nombre + " " + matricula + ". ", NamedTextColor.GOLD)
                .append(Component.text("[Aceptar]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/coche aceptar"))
                        .hoverEvent(HoverEvent.showText(Component.text("Pagar y quedarte el coche con llave nueva"))))
                .append(Component.text(" "))
                .append(Component.text("[Rechazar]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/coche rechazar"))));
        comprador.sendMessage(ChatColor.GRAY + "Tienes 60 segundos. Luego tendrás que contratarle un seguro para circular.");
        pl.menus.oferta(comprador, p.getName(), nombre, matricula, precio);
        comprador.playSound(comprador.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1, 1.2f);
        p.sendMessage(ChatColor.GREEN + "Oferta enviada a " + comprador.getName() + ". Mantén el coche y la llave en el inventario hasta que acepte.");
    }

    /** Lo que paga el desguace: hasta desguace.maximo del precio nuevo, según el estado (0-100). */
    double tasacion(Tier tier, double estado) {
        double maximo = pl.getConfig().getDouble("desguace.maximo", 0.5);
        return Math.round(tier.precio() * maximo * Math.max(0, Math.min(100, estado)) / 100);
    }

    void desguace(Player p, String[] args) {
        ItemStack item = p.getInventory().getItemInMainHand();
        PersistentDataContainer pdc = item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer() : null;
        String matricula = pdc == null ? null : pdc.get(pl.kMatricula, PersistentDataType.STRING);
        if (item.getType() != Material.MINECART || matricula == null) {
            p.sendMessage(ChatColor.GOLD + "Desguace: " + ChatColor.GRAY + "guarda el coche, ponlo en la mano, lleva su llave y escribe "
                    + ChatColor.WHITE + "/coche desguace" + ChatColor.GRAY
                    + ". Te pagamos hasta la mitad de lo que vale nuevo, según su estado (repáralo antes y te darán más).");
            return;
        }
        if (!pl.deEstaTanda(pdc)) { p.sendMessage(ChatColor.RED + "Ese coche ya no vale."); return; }
        if (!Extra.llaveYDueno(pl, p, matricula)) { p.sendMessage(ChatColor.RED + "Necesitas la llave de " + matricula + "."); return; }
        Tier tier = pl.tiers.get(pdc.get(pl.kTier, PersistentDataType.STRING));
        if (tier == null) return;
        Double guardado = pdc.get(pl.kEstado, PersistentDataType.DOUBLE);
        double estado = guardado == null ? 100.0 : guardado;
        double pago = tasacion(tier, estado);
        boolean confirmar = args.length > 1 && args[1].equalsIgnoreCase("confirmar");
        Oferta previa = desguaces.get(p.getUniqueId());
        // primera vez (o caducado): se pide confirmación durante 30 s
        if (!confirmar || previa == null || !previa.matricula().equals(matricula) || previa.caduca() < System.currentTimeMillis()) {
            desguaces.put(p.getUniqueId(), new Oferta(p.getUniqueId(), matricula, pago, System.currentTimeMillis() + 30_000L));
            p.sendMessage(Component.text(String.format(Locale.ROOT, "🔩 Desguace: por tu %s %s (estado %.0f %%) te damos %,.2f Ⓜ. ",
                            tier.nombre(), matricula, estado, pago), NamedTextColor.GOLD)
                    .append(Component.text("[Confirmar]", NamedTextColor.RED, TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/coche desguace confirmar"))
                            .hoverEvent(HoverEvent.showText(Component.text("El coche desaparece para siempre y sus llaves dejan de valer")))));
            if (estado < 99.5)
                p.sendMessage(ChatColor.GRAY + String.format(Locale.ROOT, "Si lo reparas antes (/coche reparar) te darían %,.2f Ⓜ.", tasacion(tier, 100)));
            return;
        }
        desguaces.remove(p.getUniqueId());
        p.getInventory().setItemInMainHand(null);
        ItemStack[] contenido = p.getInventory().getContents();
        for (int i = 0; i < contenido.length; i++) {
            ItemStack it = contenido[i];
            if (it != null && it.hasItemMeta()
                    && matricula.equals(it.getItemMeta().getPersistentDataContainer().get(pl.kLlave, PersistentDataType.STRING)))
                p.getInventory().setItem(i, null);
        }
        pl.registro.baja(matricula);
        pl.seguros.anular(matricula);
        if (pago > 0 && pl.eco != null) pl.eco.depositPlayer(p, pago);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_DESTROY, SoundCategory.PLAYERS, 0.7f, 0.8f);
        p.sendMessage(ChatColor.GREEN + String.format(Locale.ROOT, "Coche %s desguazado. Te hemos pagado %,.2f Ⓜ. Su seguro queda anulado.", matricula, pago));
        pl.carnet.sumar(p, "desguazados", 1);
        pl.getLogger().info("Desguace: " + p.getName() + " " + matricula + " por " + pago + " Ⓜ");
    }

    void rechazar(Player p) {
        Oferta oferta = ofertas.remove(p.getUniqueId());
        if (oferta == null) { p.sendMessage(ChatColor.YELLOW + "No tienes ninguna oferta pendiente."); return; }
        p.sendMessage(ChatColor.GRAY + "Oferta rechazada.");
        Player vendedor = Bukkit.getPlayer(oferta.vendedor());
        if (vendedor != null) vendedor.sendMessage(ChatColor.YELLOW + p.getName() + " ha rechazado comprar el " + oferta.matricula() + ".");
    }

    void aceptar(Player p) {
        Oferta oferta = ofertas.remove(p.getUniqueId());
        if (oferta == null || oferta.caduca() < System.currentTimeMillis()) {
            p.sendMessage(ChatColor.YELLOW + "No tienes ninguna oferta pendiente (o ha caducado).");
            return;
        }
        Player vendedor = Bukkit.getPlayer(oferta.vendedor());
        if (vendedor == null) { p.sendMessage(ChatColor.RED + "El vendedor se ha desconectado."); return; }
        String matricula = oferta.matricula();
        // el coche tiene que seguir en el inventario del vendedor
        int hueco = -1;
        ItemStack[] contenido = vendedor.getInventory().getContents();
        for (int i = 0; i < contenido.length; i++) {
            ItemStack it = contenido[i];
            if (it != null && it.getType() == Material.MINECART && it.hasItemMeta()
                    && matricula.equals(it.getItemMeta().getPersistentDataContainer().get(pl.kMatricula, PersistentDataType.STRING))) {
                hueco = i;
                break;
            }
        }
        if (hueco < 0 || !pl.tieneLlave(vendedor, matricula)) {
            p.sendMessage(ChatColor.RED + "El vendedor ya no tiene el coche o su llave.");
            vendedor.sendMessage(ChatColor.RED + "Venta cancelada: no tienes el coche o la llave.");
            return;
        }
        if (oferta.precio() > 0) {
            if (pl.eco == null || !pl.eco.has(p, oferta.precio())) {
                p.sendMessage(ChatColor.RED + String.format(Locale.ROOT, "No te llega: cuesta %,.2f Ⓜ.", oferta.precio()));
                vendedor.sendMessage(ChatColor.RED + p.getName() + " no tiene dinero suficiente.");
                return;
            }
            if (!pl.eco.withdrawPlayer(p, oferta.precio()).transactionSuccess()) { p.sendMessage(ChatColor.RED + "No se pudo cobrar."); return; }
            pl.eco.depositPlayer(vendedor, oferta.precio());
        }
        ItemStack coche = contenido[hueco];
        vendedor.getInventory().setItem(hueco, null);
        // fuera las llaves y copias del vendedor
        for (int i = 0; i < contenido.length; i++) {
            ItemStack it = vendedor.getInventory().getItem(i);
            if (it != null && it.hasItemMeta()
                    && matricula.equals(it.getItemMeta().getPersistentDataContainer().get(pl.kLlave, PersistentDataType.STRING)))
                vendedor.getInventory().setItem(i, null);
        }
        Tier tier = pl.tiers.get(coche.getItemMeta().getPersistentDataContainer().get(pl.kTier, PersistentDataType.STRING));
        pl.registro.nuevaSerie(matricula);
        if (tier != null) pl.registro.apuntar(matricula, tier, p.getName());
        pl.seguros.anular(matricula);
        pl.dar(p, coche);
        if (tier != null) pl.dar(p, pl.itemLlave(tier, matricula));
        String precioTxt = oferta.precio() == 0 ? "regalado" : String.format(Locale.ROOT, "%,.2f Ⓜ", oferta.precio());
        p.sendMessage(ChatColor.GREEN + "¡Coche comprado! " + ChatColor.WHITE + matricula + ChatColor.GREEN + " (" + precioTxt
                + "). Aquí tienes su llave nueva; las antiguas ya no valen.");
        p.sendMessage(ChatColor.YELLOW + "Contrata un seguro antes de circular: /coche seguro " + matricula.replace(" ", ""));
        vendedor.sendMessage(ChatColor.GREEN + "Has vendido el " + matricula + " a " + p.getName() + " (" + precioTxt
                + "). Tu seguro de ese coche queda anulado.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
        vendedor.playSound(vendedor.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 1, 1.2f);
        pl.carnet.sumar(vendedor, "vendidos", 1);
        pl.getLogger().info("Venta: " + vendedor.getName() + " -> " + p.getName() + " " + matricula + " por " + oferta.precio() + " Ⓜ");
    }
}
