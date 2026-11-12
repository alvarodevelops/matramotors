package matracraft.coches;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Tickets de gasolinera: cada repostaje deja un ticket que se puede imprimir en papel.
 * Juntando suficientes tickets válidos se canjean por un lleno gratis.
 */
final class Tickets {

    record Ticket(String gasolinera, String claveSurtidor, int numero, ZonedDateTime fecha, String coche, String matricula,
                  double litros, double precioL, double total, boolean gratis, boolean valido) {
    }

    /** Aspecto del ticket de cada gasolinera; sale siempre igual para el mismo nombre. */
    private record Estilo(ChatColor principal, ChatColor secundario, String borde, String eslogan, String empleado,
                          int numEstacion, int disposicion, String icono) {
    }

    private static final ChatColor[][] PALETAS = {
            {ChatColor.GOLD, ChatColor.RED}, {ChatColor.GREEN, ChatColor.YELLOW}, {ChatColor.BLUE, ChatColor.AQUA},
            {ChatColor.RED, ChatColor.WHITE}, {ChatColor.DARK_GREEN, ChatColor.GREEN}, {ChatColor.YELLOW, ChatColor.RED},
            {ChatColor.DARK_AQUA, ChatColor.AQUA}, {ChatColor.LIGHT_PURPLE, ChatColor.DARK_PURPLE}, {ChatColor.DARK_RED, ChatColor.GOLD}};
    private static final String[] BORDES = {"════════════════", "────────────────", "- - - - - - - - -", "~~~~~~~~~~~~~~~~",
            "✦ ✦ ✦ ✦ ✦ ✦ ✦ ✦", "▪▫▪▫▪▫▪▫▪▫▪▫▪▫▪▫", "≈≈≈≈≈≈≈≈≈≈≈≈≈≈≈≈", "• • • • • • • • •"};
    private static final String[] ESLOGANES = {"¡Gracias por su visita!", "Aquí se reposta a gusto", "Vuelva pronto, que el campo espera",
            "Su coche se lo agradecerá", "Gasolina con arte", "Donde para el que sabe", "Lleno de sur", "Calidad de pueblo",
            "Ni el AVE corre tanto", "Echamos gasolina y salero", "Cerramos la siesta, ¡no se olvide!", "Más fresquita que el gazpacho",
            "Su gasolinera de confianza", "Hoy no llueve, ¡aproveche!", "Con cariño desde el surtidor"};
    private static final String[] EMPLEADOS = {"Manoli", "Paco", "Juani", "Rafi", "Loli", "Pepe", "Charo", "Curro",
            "Mari Carmen", "Antonio", "Reme", "Manolo", "Puri", "Fali", "Encarni"};
    private static final String[] ICONOS = {"⛽", "★", "♦", "☀", "✿", "⚑", "❖", "☘"};
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm");

    private final Coches pl;
    private final File archivo;
    private final YamlConfiguration datos;
    private final Map<UUID, Ticket> ultimo = new HashMap<>();
    private final Map<UUID, Boolean> impreso = new HashMap<>();

    Tickets(Coches pl) {
        this.pl = pl;
        this.archivo = new File(pl.getDataFolder(), "clientes.yml");
        this.datos = YamlConfiguration.loadConfiguration(archivo);
    }

    private void guardar() {
        try { datos.save(archivo); } catch (IOException e) { pl.getLogger().warning("No pude guardar clientes.yml"); }
    }

    int necesarios() { return pl.getConfig().getInt("promocion.tickets-necesarios", 10); }
    double importeMinimo() { return pl.getConfig().getDouble("promocion.importe-minimo", 5.0); }

    /** Tickets en papel válidos que lleva el jugador. */
    int validos(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (esValido(it)) n += it.getAmount();
        }
        return n;
    }

    private boolean esValido(ItemStack it) {
        return it != null && it.getType() == Material.PAPER && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(pl.kTicket, PersistentDataType.BYTE);
    }

    /** Si tiene tickets de sobra, se los quita y el repostaje sale gratis. */
    boolean canjear(Player p, Conduccion.Estado st) {
        if (st.gasolina >= st.tier.deposito() - 0.05) return false;
        if (validos(p) < necesarios()) return false;
        int quedan = necesarios();
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length && quedan > 0; i++) {
            if (!esValido(inv[i])) continue;
            int quito = Math.min(quedan, inv[i].getAmount());
            quedan -= quito;
            if (quito == inv[i].getAmount()) p.getInventory().setItem(i, null);
            else inv[i].setAmount(inv[i].getAmount() - quito);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        return true;
    }

    /** Al terminar de repostar: apunta el ticket y ofrece imprimirlo. */
    void cerrar(Player p, String gasolinera, String clave, Conduccion.Estado st, double litros, double precioL, double total, boolean gratis) {
        boolean valido = !gratis && total >= importeMinimo();
        int numero = datos.getInt("contador-tickets", 0) + 1;
        datos.set("contador-tickets", numero);
        guardar();
        ZonedDateTime ahora = ZonedDateTime.now(ZoneId.of(pl.getConfig().getString("zona-horaria", "Europe/Madrid")));
        Ticket t = new Ticket(gasolinera, clave, numero, ahora, st.tier.nombre(), st.matricula, litros, precioL, total, gratis, valido);
        ultimo.put(p.getUniqueId(), t);
        impreso.put(p.getUniqueId(), false);
        Estilo e = estilo(gasolinera);
        Component msg = Component.text(e.icono() + " " + ChatColor.stripColor(gasolinera) + ": ", color(e.principal()), TextDecoration.BOLD)
                .append(Component.text(String.format(Locale.ROOT, "%.1f L · ", litros), NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                .append(Component.text(gratis ? "GRATIS" : String.format(Locale.ROOT, "%.2f Ⓜ", total),
                        gratis ? NamedTextColor.GREEN : NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.text("  "))
                .append(Component.text("[Imprimir ticket]", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, false)
                        .clickEvent(ClickEvent.runCommand("/coche ticket"))
                        .hoverEvent(HoverEvent.showText(Component.text("Te da el ticket en papel"))));
        p.sendMessage(msg);
        if (valido) {
            p.sendMessage(ChatColor.GRAY + "Imprímelo y guárdalo: " + ChatColor.WHITE + necesarios() + " tickets" + ChatColor.GRAY
                    + " = 1 repostaje gratis (llevas " + validos(p) + ").");
        } else if (!gratis) {
            p.sendMessage(ChatColor.DARK_GRAY + String.format(Locale.ROOT, "Este ticket no entra en la promoción (mínimo %.2f Ⓜ).", importeMinimo()));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    private static NamedTextColor color(ChatColor c) {
        return switch (c) {
            case GOLD -> NamedTextColor.GOLD; case RED -> NamedTextColor.RED; case GREEN -> NamedTextColor.GREEN;
            case YELLOW -> NamedTextColor.YELLOW; case BLUE -> NamedTextColor.BLUE; case AQUA -> NamedTextColor.AQUA;
            case WHITE -> NamedTextColor.WHITE; case DARK_GREEN -> NamedTextColor.DARK_GREEN; case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE; case DARK_PURPLE -> NamedTextColor.DARK_PURPLE; case DARK_RED -> NamedTextColor.DARK_RED;
            default -> NamedTextColor.GOLD;
        };
    }

    /** /coche ticket: da en papel el último ticket (una sola vez). */
    void imprimir(Player p) {
        Ticket t = ultimo.get(p.getUniqueId());
        if (t == null) { p.sendMessage(ChatColor.YELLOW + "No tienes ningún repostaje reciente que imprimir."); return; }
        if (Boolean.TRUE.equals(impreso.get(p.getUniqueId()))) { p.sendMessage(ChatColor.YELLOW + "Ese ticket ya lo has impreso."); return; }
        impreso.put(p.getUniqueId(), true);
        pl.dar(p, papel(t));
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1f, 1.1f);
    }

    private ItemStack papel(Ticket t) {
        Estilo e = estilo(t.gasolinera());
        ChatColor c1 = e.principal();
        ChatColor c2 = e.secundario();
        ChatColor gris = ChatColor.GRAY;
        ChatColor blanco = ChatColor.WHITE;
        String nombre = ChatColor.stripColor(t.gasolinera());
        String borde = c2 + e.borde();
        List<String> cabecera = List.of(borde,
                c1 + "" + ChatColor.BOLD + " " + e.icono() + " " + nombre.toUpperCase(Locale.ROOT),
                gris + " Estación nº " + String.format(Locale.ROOT, "%04d", e.numEstacion()),
                borde);
        List<String> datosTicket = new ArrayList<>(List.of(
                gris + "Ticket " + blanco + "#" + String.format(Locale.ROOT, "%06d", t.numero()),
                gris + "Fecha   " + blanco + t.fecha().format(FECHA),
                gris + "Atiende " + blanco + e.empleado(),
                gris + "Coche   " + blanco + t.coche() + " " + t.matricula()));
        List<String> importes = List.of(
                gris + "Gasolina 95 ...... " + blanco + String.format(Locale.ROOT, "%.2f L", t.litros()),
                gris + "Precio ........... " + blanco + String.format(Locale.ROOT, "%.2f Ⓜ/L", t.precioL()),
                c1 + "" + ChatColor.BOLD + "TOTAL ............ " + (t.gratis() ? ChatColor.GREEN + "GRATIS" : blanco + String.format(Locale.ROOT, "%.2f Ⓜ", t.total())));
        List<String> pie = new ArrayList<>(List.of(
                t.valido() ? ChatColor.GREEN + "✔ Válido promoción: " + necesarios() + " tickets = 1 lleno gratis"
                        : ChatColor.DARK_GRAY + "✘ No válido para la promoción",
                borde,
                c2 + " " + ChatColor.ITALIC + e.eslogan()));
        if (t.gratis()) pie.add(0, ChatColor.GREEN + "★ Repostaje gratis canjeando tickets ★");

        // cada gasolinera ordena el ticket a su manera
        List<String> lore = new ArrayList<>(cabecera);
        switch (e.disposicion()) {
            case 0 -> { lore.addAll(datosTicket); lore.add(" "); lore.addAll(importes); }
            case 1 -> { lore.addAll(importes); lore.add(c2 + e.borde()); lore.addAll(datosTicket); }
            default -> {
                lore.add(datosTicket.get(1)); lore.add(datosTicket.get(3)); lore.add(" ");
                lore.addAll(importes); lore.add(" ");
                lore.add(datosTicket.get(0)); lore.add(datosTicket.get(2));
            }
        }
        lore.add(" ");
        lore.addAll(pie);

        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(c1 + "Ticket · " + nombre);
        m.setLore(lore);
        if (t.valido()) m.getPersistentDataContainer().set(pl.kTicket, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private static Estilo estilo(String gasolinera) {
        String limpio = ChatColor.stripColor(gasolinera);
        Random r = new Random(limpio.toLowerCase(Locale.ROOT).hashCode());
        ChatColor[] paleta = PALETAS[r.nextInt(PALETAS.length)];
        String n = limpio.toLowerCase(Locale.ROOT);
        // las marcas conocidas llevan sus colores
        if (n.contains("repsol")) paleta = new ChatColor[]{ChatColor.GOLD, ChatColor.RED};
        else if (n.contains("cepsa")) paleta = new ChatColor[]{ChatColor.RED, ChatColor.WHITE};
        else if (n.contains("moeve")) paleta = new ChatColor[]{ChatColor.DARK_AQUA, ChatColor.AQUA};
        else if (n.startsWith("bp") || n.contains(" bp")) paleta = new ChatColor[]{ChatColor.DARK_GREEN, ChatColor.YELLOW};
        else if (n.contains("galp")) paleta = new ChatColor[]{ChatColor.GOLD, ChatColor.YELLOW};
        else if (n.contains("shell")) paleta = new ChatColor[]{ChatColor.YELLOW, ChatColor.RED};
        else if (n.contains("ballenoil")) paleta = new ChatColor[]{ChatColor.BLUE, ChatColor.AQUA};
        else if (n.contains("plenoil")) paleta = new ChatColor[]{ChatColor.GREEN, ChatColor.DARK_GREEN};
        else if (n.contains("petroprix")) paleta = new ChatColor[]{ChatColor.RED, ChatColor.YELLOW};
        else if (n.contains("petronor")) paleta = new ChatColor[]{ChatColor.DARK_GREEN, ChatColor.GREEN};
        return new Estilo(paleta[0], paleta[1], BORDES[r.nextInt(BORDES.length)], ESLOGANES[r.nextInt(ESLOGANES.length)],
                EMPLEADOS[r.nextInt(EMPLEADOS.length)], 1 + r.nextInt(9999), r.nextInt(3), ICONOS[r.nextInt(ICONOS.length)]);
    }
}
