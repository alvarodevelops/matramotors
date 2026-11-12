package matracraft.matrains;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Menús de Matrains: /tren (concesionario, mis trenes, estaciones y líneas), panel del tren y su ruta. */
final class Menus {

    private final Matrains pl;

    Menus(Matrains pl) { this.pl = pl; }

    private static String m(double x) { return Auto.fmt(x) + " Ⓜ"; }

    // ------------------------------------------------------------------ /tren

    void principal(Player p) {
        Gui g = new Gui(3, "Panel principal");
        g.poner(10, Gui.boton(Material.FURNACE_MINECART, ChatColor.GOLD + "Concesionario ferroviario",
                ChatColor.GRAY + "Locomotoras, vagones, campanas de estación", ChatColor.GRAY + "y subestaciones eléctricas."), (j, c) -> concesionario(j, 0));
        g.poner(11, Gui.boton(Material.MINECART, ChatColor.YELLOW + "Mis trenes", ChatColor.GRAY + "Dónde están y cómo van."), (j, c) -> misTrenes(j));
        g.poner(13, Gui.boton(Material.COMPASS, ChatColor.YELLOW + "Mis estaciones", ChatColor.GRAY + "Lista, nombre y quién para en ellas."), (j, c) -> misEstaciones(j));
        g.poner(15, Gui.boton(Material.REDSTONE, ChatColor.YELLOW + "Mis líneas eléctricas", ChatColor.GRAY + "Encender y apagar la luz desde aquí."), (j, c) -> misLineas(j));
        g.poner(16, Gui.boton(Material.BOOK, ChatColor.YELLOW + "Precios y facturas",
                ChatColor.GRAY + "Luz: " + ChatColor.WHITE + String.format("%.3f €/kWh", pl.energia.precioLuz) + ChatColor.GRAY + " (PVPC real)",
                ChatColor.GRAY + "Luz pagada por tus trenes: " + ChatColor.WHITE + m(factura(j0(p)))), null);
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("MATRAMOTORS"))
            g.poner(18, Gui.boton(Material.ARROW, ChatColor.YELLOW + "← Menú de vehículos"), (j, c) -> j.performCommand("vehiculos"));
        g.poner(22, Gui.boton(Material.BARRIER, ChatColor.RED + "Cerrar"), (j, c) -> j.closeInventory());
        g.abrir(p);
    }

    private static java.util.UUID j0(Player p) { return p.getUniqueId(); }

    private double factura(java.util.UUID u) {
        double f = 0;
        for (Tren t : pl.trenes.values()) if (t.dueno.equals(u)) f += t.facturaLuz;
        return f;
    }

    void concesionario(Player p, int pestana) {
        Gui g = new Gui(6, "Concesionario ferroviario");
        g.poner(1, Gui.boton(pestana == 0 ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE, ChatColor.GOLD + "Locomotoras"), (j, c) -> concesionario(j, 0));
        g.poner(3, Gui.boton(pestana == 1 ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE, ChatColor.GOLD + "Vagones de mercancías"), (j, c) -> concesionario(j, 1));
        g.poner(5, Gui.boton(pestana == 2 ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE, ChatColor.GOLD + "Coches de pasajeros"), (j, c) -> concesionario(j, 2));
        g.poner(7, Gui.boton(pestana == 3 ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE, ChatColor.GOLD + "Infraestructura"), (j, c) -> concesionario(j, 3));
        int[] huecos = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int i = 0;
        double carne = pl.getConfig().getDouble("precios.carne-tren", 500);
        String aviso = pl.tieneCarne(p.getUniqueId()) ? "" : ChatColor.GOLD + "La primera compra incluye el carné de tren (" + m(carne) + ")";
        if (pestana == 0) {
            for (Tipos.Loco l : pl.tipos.locos.values()) {
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Velocidad: " + ChatColor.WHITE + (int) l.velocidad() + " bloques/s");
                lore.add(ChatColor.GRAY + "Arrastra: " + ChatColor.WHITE + (int) l.traccion() + ChatColor.GRAY + " · hasta " + ChatColor.WHITE + l.vagones() + ChatColor.GRAY + " vagones");
                lore.add(ChatColor.GRAY + "Funciona con: " + ChatColor.WHITE + switch (l.combustible()) { case "carbon" -> "carbón"; case "gasoleo" -> "gasóleo (precio real)"; default -> "luz de la red (PVPC real)"; });
                if (l.soloPasajeros()) lore.add(ChatColor.GRAY + "Solo coches de pasajeros modernos");
                lore.add(ChatColor.GRAY + "Aspecto al azar entre " + Modelos.aspectos(l.id()).length);
                lore.add("");
                lore.add(ChatColor.GREEN + "Precio: " + m(l.precio()));
                if (!aviso.isEmpty()) lore.add(aviso);
                lore.add(ChatColor.YELLOW + "Clic para comprar");
                g.poner(huecos[i++], Gui.boton(Material.FURNACE_MINECART, ChatColor.GOLD + l.nombre(), lore), (j, c) -> comprar(j, l.precio(), () -> {
                    int a = Objetos.aspectoAzar(l.id());
                    double comb = l.combustible().equals("carbon") ? 16 : l.combustible().equals("gasoleo") ? 60 : 0;
                    return pl.objetos.loco(l.id(), a, comb);
                }, () -> concesionario(j, 0)));
            }
        } else if (pestana == 1 || pestana == 2) {
            for (Tipos.Vagon v : pl.tipos.vagones.values()) {
                if (v.pasajeros() != (pestana == 2)) continue;
                List<String> lore = new ArrayList<>();
                if (v.pasajeros()) {
                    lore.add(ChatColor.GRAY + "Asientos: " + ChatColor.WHITE + v.asientos());
                    if (v.moderno()) lore.add(ChatColor.GRAY + "Moderno: también para alta velocidad y maglev");
                    switch (v.id()) {
                        case "premium" -> lore.add(ChatColor.AQUA + "Regeneración mientras viajas");
                        case "restaurante" -> lore.add(ChatColor.AQUA + "Comida gratis en viajes largos (1 vez por hora)");
                        case "regalo" -> lore.add(ChatColor.AQUA + "Regalo al llegar en viajes largos (1 vez por hora)");
                        case "mirador" -> lore.add(ChatColor.AQUA + "Techo de cristal y visión nocturna en túneles");
                        default -> { }
                    }
                } else {
                    lore.add(ChatColor.GRAY + "Carga: " + ChatColor.WHITE + Tipos.nombreAcepta(v.acepta()));
                    lore.add(ChatColor.GRAY + "Capacidad: " + ChatColor.WHITE + v.huecos() + (v.cisterna() ? " cubos" : " huecos"));
                }
                lore.add(ChatColor.GRAY + "Peso: " + ChatColor.WHITE + v.pesoVacio() + (v.pasajeros() ? "" : " vacío, " + v.pesoLleno() + " lleno"));
                lore.add("");
                lore.add(ChatColor.GREEN + "Precio: " + m(v.precio()));
                if (!aviso.isEmpty()) lore.add(aviso);
                lore.add(ChatColor.YELLOW + "Clic para comprar");
                int pest = pestana;
                g.poner(huecos[i++], Gui.boton(v.pasajeros() ? Material.MINECART : Material.CHEST_MINECART, ChatColor.GOLD + v.nombre(), lore),
                        (j, c) -> comprar(j, v.precio(), () -> pl.objetos.vagon(v.id(), Objetos.aspectoAzar(v.id())), () -> concesionario(j, pest)));
            }
        } else {
            double campana = pl.getConfig().getDouble("precios.campana-estacion", 2500), sub = pl.getConfig().getDouble("precios.subestacion", 1500);
            g.poner(20, Gui.boton(Material.BELL, ChatColor.GOLD + "Campana de estación", ChatColor.GRAY + "Ponla junto a la vía y elige el tamaño",
                    ChatColor.GRAY + "(8x8 a 32x32). Protege la estación.", "", ChatColor.GREEN + "Precio: " + m(campana), ChatColor.YELLOW + "Clic para comprar"),
                    (j, c) -> comprar(j, campana, () -> pl.estaciones.objeto(1), () -> concesionario(j, 3)));
            g.poner(22, Gui.boton(Material.WAXED_COPPER_BLOCK, ChatColor.GOLD + "Subestación eléctrica", ChatColor.GRAY + "Tocando el netherrack bajo la vía y con",
                    ChatColor.GRAY + "una palanca encendida, la línea tiene luz.", ChatColor.GRAY + "También protege la vía (y 1 bloque alrededor).",
                    "", ChatColor.GREEN + "Precio: " + m(sub), ChatColor.YELLOW + "Clic para comprar"),
                    (j, c) -> comprar(j, sub, () -> pl.red.objeto(1), () -> concesionario(j, 3)));
            g.poner(24, Gui.boton(Material.IRON_CHAIN, ChatColor.GOLD + "Cómo se engancha", ChatColor.GRAY + "Con una cadena de hierro en la mano:",
                    ChatColor.GRAY + "clic derecho en el tren lo engancha a lo que", ChatColor.GRAY + "haya a menos de 3 bloques; mayúsculas + clic",
                    ChatColor.GRAY + "suelta ese vagón (solo dentro de una estación tuya)."), null);
        }
        g.poner(49, Gui.boton(Material.ARROW, ChatColor.YELLOW + "Volver"), (j, c) -> principal(j));
        g.abrir(p);
    }

    private void comprar(Player p, double precio, java.util.function.Supplier<ItemStack> que, Runnable volver) {
        double carne = pl.tieneCarne(p.getUniqueId()) ? 0 : pl.getConfig().getDouble("precios.carne-tren", 500);
        double total = precio + carne;
        if (pl.eco != null && !pl.eco.has(p, total)) { p.sendMessage(ChatColor.RED + "Te faltan " + m(total - pl.eco.getBalance(p)) + "."); return; }
        if (pl.eco != null) pl.eco.withdrawPlayer(p, total);
        if (carne > 0) { pl.darCarne(p.getUniqueId()); p.sendMessage(ChatColor.GREEN + "🪪 Carné de tren expedido (" + m(carne) + ")."); }
        ItemStack it = que.get();
        p.getInventory().addItem(it).values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        p.sendMessage(ChatColor.GREEN + "Comprado por " + m(precio) + ": " + it.getItemMeta().getDisplayName());
        volver.run();
    }

    /** Resumen de un tren para los menús (también lo usa el menú de vehículos de MATRAMOTORS). */
    List<String> loreTren(Tren t) {
        Via.Pos q = t.coches.get(0).delante;
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Coches: " + ChatColor.WHITE + t.coches.size());
        lore.add(ChatColor.GRAY + "Dónde: " + ChatColor.WHITE + q.mundo + " " + q.x + ", " + q.y + ", " + q.z);
        if (t.conLoco()) {
            Tipos.Loco l = pl.tipos.loco(t.tipo);
            lore.add(ChatColor.GRAY + "Energía: " + ChatColor.WHITE + pl.energia.indicador(t, l));
            lore.add(ChatColor.GRAY + "Automático: " + (t.auto ? ChatColor.GREEN + "sí" : ChatColor.RED + "no")
                    + (t.auto && t.viajando ? ChatColor.GRAY + " · hacia " + Auto.nombre(pl, t.destino) : ""));
            lore.add(ChatColor.GRAY + "Luz pagada: " + ChatColor.WHITE + m(t.facturaLuz));
            lore.add(ChatColor.YELLOW + "Clic: panel del tren");
        }
        return lore;
    }

    void misTrenes(Player p) {
        Gui g = new Gui(6, "Mis trenes");
        int i = 10;
        for (Tren t : pl.trenes.values()) {
            if (!t.dueno.equals(p.getUniqueId()) || i > 43) continue;
            List<String> lore = loreTren(t);
            g.poner(i, Gui.boton(t.conLoco() ? Material.FURNACE_MINECART : Material.CHEST_MINECART, ChatColor.GOLD + t.nombre, lore),
                    t.conLoco() ? (j, c) -> panel(j, t) : null);
            i += (i % 9 == 7) ? 3 : 1;
        }
        if (i == 10) g.poner(22, Gui.boton(Material.PAPER, ChatColor.GRAY + "No tienes trenes en las vías."), null);
        g.poner(49, Gui.boton(Material.ARROW, ChatColor.YELLOW + "Volver"), (j, c) -> principal(j));
        g.abrir(p);
    }

    void misEstaciones(Player p) {
        Gui g = new Gui(6, "Mis estaciones");
        int i = 10;
        for (Estaciones.Estacion e : pl.estaciones.todas.values()) {
            if (!e.dueno.equals(p.getUniqueId()) || i > 43) continue;
            g.poner(i, Gui.boton(Material.BELL, ChatColor.YELLOW + e.nombre, ChatColor.GRAY + e.codigo() + " · " + e.mundo + " " + e.bx + ", " + e.by + ", " + e.bz,
                    ChatColor.GRAY + "Tamaño " + e.lado + "x" + e.lado, ChatColor.YELLOW + "Clic: su menú"), (j, c) -> pl.estaciones.menu(j, e));
            i += (i % 9 == 7) ? 3 : 1;
        }
        if (i == 10) g.poner(22, Gui.boton(Material.PAPER, ChatColor.GRAY + "No tienes estaciones.", ChatColor.GRAY + "Compra una campana en el concesionario."), null);
        g.poner(49, Gui.boton(Material.ARROW, ChatColor.YELLOW + "Volver"), (j, c) -> principal(j));
        g.abrir(p);
    }

    void misLineas(Player p) {
        Gui g = new Gui(6, "Mis líneas eléctricas");
        int i = 10;
        for (Red.Linea l : pl.red.lineas) {
            if (!l.esDueno(p.getUniqueId()) || i > 43) continue;
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Cable: " + ChatColor.WHITE + l.cable.size() + " bloques de netherrack");
            for (Red.Sub s : l.subs) lore.add(ChatColor.GRAY + "· " + s.nombre + " (" + s.x + ", " + s.y + ", " + s.z + ")");
            lore.add(l.luz ? ChatColor.GREEN + "⚡ Con luz" : ChatColor.RED + "Sin luz");
            lore.add(ChatColor.YELLOW + "Clic: " + (l.luz ? "apagar" : "encender"));
            g.poner(i, Gui.boton(l.luz ? Material.REDSTONE_BLOCK : Material.REDSTONE, ChatColor.GOLD + l.subs.get(0).nombre, lore), (j, c) -> {
                conmutar(j, l, !l.luz);
                misLineas(j);
            });
            i += (i % 9 == 7) ? 3 : 1;
        }
        if (i == 10) g.poner(22, Gui.boton(Material.PAPER, ChatColor.GRAY + "No tienes líneas eléctricas.",
                ChatColor.GRAY + "Netherrack bajo la vía + una subestación tocándolo."), null);
        g.poner(49, Gui.boton(Material.ARROW, ChatColor.YELLOW + "Volver"), (j, c) -> principal(j));
        g.abrir(p);
    }

    /** Enciende o apaga una línea moviendo la palanca de su primera subestación (o poniéndole una). */
    private void conmutar(Player p, Red.Linea l, boolean encender) {
        boolean hecho = false;
        for (Red.Sub s : l.subs) {
            if (!s.dueno.equals(p.getUniqueId()) && !p.hasPermission("matrains.admin")) continue;
            Block b = s.bloque();
            if (b == null) continue;
            for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN}) {
                Block x = b.getRelative(f);
                if (x.getType() == Material.LEVER && x.getBlockData() instanceof Switch sw) {
                    sw.setPowered(encender);
                    x.setBlockData(sw);
                    hecho = true;
                }
            }
            if (!encender) continue;
            if (hecho) break;
        }
        if (!hecho) { p.sendMessage(ChatColor.RED + "Tus subestaciones no tienen palanca: pégale una a alguna."); return; }
        pl.red.recalcular(l.mundo, new java.util.HashSet<>(l.subs));
        p.sendMessage(encender ? ChatColor.GREEN + "⚡ Línea encendida." : ChatColor.YELLOW + "Línea apagada.");
    }

    // ------------------------------------------------------------------ panel del tren

    void panel(Player p, Tren t) {
        Tipos.Loco l = pl.tipos.loco(t.tipo);
        if (l == null) return;
        Gui g = new Gui(3, t.nombre);
        List<String> info = new ArrayList<>();
        info.add(ChatColor.GRAY + l.nombre() + " · " + Modelos.nombre(t.tipo, t.locomotora().aspecto));
        info.add(ChatColor.GRAY + "Energía: " + ChatColor.WHITE + pl.energia.indicador(t, l));
        info.add(ChatColor.GRAY + "Vagones: " + ChatColor.WHITE + (t.coches.size() - 1) + " / " + l.vagones());
        info.add(ChatColor.GRAY + String.format("Arrastra: %.1f de %.0f", t.peso(), l.traccion()));
        info.add(ChatColor.GRAY + "Luz pagada: " + ChatColor.WHITE + m(t.facturaLuz));
        info.add(ChatColor.GRAY + String.format("Recorrido: %.0f bloques", t.recorrido));
        g.poner(4, Gui.boton(Material.FURNACE_MINECART, ChatColor.GOLD + t.nombre, info), null);
        g.poner(10, Gui.boton(t.auto ? Material.LIME_CONCRETE : Material.RED_CONCRETE, ChatColor.YELLOW + "Automático: " + (t.auto ? ChatColor.GREEN + "sí" : ChatColor.RED + "no"),
                ChatColor.GRAY + "Recorre la ruta solo. Anda si estás conectado",
                ChatColor.GRAY + "o si lleva a alguien; si no, espera en la estación."), (j, c) -> {
            if (!t.auto && t.ruta.size() < 1) { j.sendMessage(ChatColor.RED + "Primero ponle una ruta."); return; }
            t.auto = !t.auto;
            if (t.auto) { t.viajando = false; t.paradoHasta = 0; t.idx = Math.min(t.idx, t.ruta.size() - 1); }
            else { t.viajando = false; t.virtual = false; t.plan = null; }
            pl.guardar();
            panel(j, t);
        });
        List<String> ruta = new ArrayList<>();
        for (int id : t.ruta) ruta.add(ChatColor.GRAY + "· " + Auto.nombre(pl, id));
        if (ruta.isEmpty()) ruta.add(ChatColor.GRAY + "Sin paradas.");
        ruta.add(ChatColor.YELLOW + "Clic para elegir paradas");
        g.poner(11, Gui.boton(Material.WRITABLE_BOOK, ChatColor.YELLOW + "Ruta", ruta), (j, c) -> ruta(j, t));
        g.poner(12, Gui.boton(Material.COMPASS, ChatColor.YELLOW + "Modo: " + ChatColor.WHITE + nombreModo(t.modo),
                ChatColor.GRAY + "Ida y vuelta: A→B→C→B→A", ChatColor.GRAY + "Bucle: A→B→C→A", ChatColor.GRAY + "Solo ida: A→B→C y se queda",
                ChatColor.YELLOW + "Clic para cambiar"), (j, c) -> {
            t.modo = switch (t.modo) { case "ida-vuelta" -> "bucle"; case "bucle" -> "solo-ida"; default -> "ida-vuelta"; };
            pl.guardar();
            panel(j, t);
        });
        g.poner(13, Gui.boton(Material.CLOCK, ChatColor.YELLOW + "Parada: " + ChatColor.WHITE + t.espera + " s", ChatColor.YELLOW + "Clic para cambiar (15/30/60/120)"), (j, c) -> {
            t.espera = switch (t.espera) { case 15 -> 30; case 30 -> 60; case 60 -> 120; default -> 15; };
            pl.guardar();
            panel(j, t);
        });
        boolean dueno = pl.esDueno(p, t);
        if (dueno) g.poner(14, Gui.boton(Material.GOLD_NUGGET, ChatColor.YELLOW + "Billete: " + ChatColor.WHITE + m(t.tarifa),
                ChatColor.GRAY + "Lo pagan los demás al subir (tú no).", ChatColor.GRAY + "70 % para ti, 30 % para la estación si es de otro.",
                ChatColor.YELLOW + "Clic izq. +5 · der. -5 · mayúsculas ±50"), (j, c) -> {
            double paso = c.isShiftClick() ? 50 : 5;
            t.tarifa = Math.max(0, Math.min(pl.getConfig().getDouble("automatico.tarifa-max", 500), t.tarifa + (c.isRightClick() ? -paso : paso)));
            pl.guardar();
            panel(j, t);
        });
        if (dueno) g.poner(15, Gui.boton(Material.NAME_TAG, ChatColor.YELLOW + "Cambiar el nombre", ChatColor.GRAY + "Sale en los paneles y billetes."), (j, c) -> {
            j.closeInventory();
            pl.pedirTexto(j, "Escribe el nombre del tren (o «cancelar»):", texto -> {
                t.nombre = texto.length() > 24 ? texto.substring(0, 24) : texto;
                pl.guardar();
                j.sendMessage(ChatColor.GREEN + "El tren se llama ahora " + t.nombre + ".");
            });
        });
        if (l.combustible().equals("gasoleo"))
            g.poner(16, Gui.boton(Material.LAVA_BUCKET, ChatColor.YELLOW + "Repostar gasóleo", ChatColor.GRAY + "A menos de 10 bloques de un surtidor.",
                    ChatColor.GRAY + "Precio real del gasóleo."), (j, c) -> { pl.energia.repostar(j, t, l, true); panel(j, t); });
        else if (l.combustible().equals("carbon"))
            g.poner(16, Gui.boton(Material.COAL, ChatColor.YELLOW + "Carbón", ChatColor.GRAY + "Clic derecho en la locomotora con carbón,",
                    ChatColor.GRAY + "carbón vegetal o bloques de carbón."), null);
        else g.poner(16, Gui.boton(Material.REDSTONE_TORCH, ChatColor.YELLOW + "Luz", ChatColor.GRAY + "Necesita vía con netherrack debajo,",
                    ChatColor.GRAY + "una subestación y su palanca encendida.", ChatColor.GRAY + String.format("Ahora: %.3f €/kWh", pl.energia.precioLuz)), null);
        if (dueno) {
            double copia = pl.getConfig().getDouble("llaves.precio-copia", 500);
            g.poner(19, Gui.boton(Material.TRIPWIRE_HOOK, ChatColor.YELLOW + "🔑 Hacer copia de la llave",
                    ChatColor.GRAY + "Llave " + ChatColor.WHITE + t.locomotora().llave + ChatColor.GRAY + " · " + m(copia),
                    ChatColor.GRAY + "Quien la lleve encima podrá conducir, repostar,",
                    ChatColor.GRAY + "abrir la carga y manejar la ruta.",
                    ChatColor.GRAY + "No podrá guardarlo, enganchar ni hacer copias."), (j, c) -> { pl.llaves.copiar(j, t); panel(j, t); });
            g.poner(20, Gui.boton(Material.IRON_TRAPDOOR, ChatColor.RED + "Cambiar la cerradura",
                    ChatColor.GRAY + "Todas las copias que hayas dado", ChatColor.GRAY + "dejan de valer. Gratis.",
                    ChatColor.YELLOW + "Mayúsculas + clic para confirmar"), (j, c) -> {
                if (!c.isShiftClick()) { j.sendMessage(ChatColor.GRAY + "Mayúsculas + clic para cambiar la cerradura."); return; }
                pl.llaves.cambiarCerradura(j, t); panel(j, t);
            });
        } else g.poner(19, Gui.boton(Material.TRIPWIRE_HOOK, ChatColor.AQUA + "Tren prestado",
                ChatColor.GRAY + "Dueño: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(t.dueno).getName()), null);
        g.poner(22, Gui.boton(Material.BARRIER, ChatColor.RED + "Cerrar", ChatColor.GRAY + "Mayúsculas + golpe en el tren, dentro de",
                ChatColor.GRAY + "una estación tuya, lo guarda como objetos."), (j, c) -> j.closeInventory());
        g.abrir(p);
    }

    static String nombreModo(String m) {
        return switch (m) { case "bucle" -> "bucle"; case "solo-ida" -> "solo ida"; default -> "ida y vuelta"; };
    }

    void ruta(Player p, Tren t) {
        Gui g = new Gui(6, "Ruta de " + t.nombre);
        String mundo = t.coches.get(0).delante.mundo;
        int i = 0;
        for (Estaciones.Estacion e : pl.estaciones.todas.values()) {
            if (!e.mundo.equals(mundo) || i >= 45) continue;
            int veces = 0;
            for (int x : t.ruta) if (x == e.id) veces++;
            g.poner(i++, Gui.boton(veces > 0 ? Material.LIME_STAINED_GLASS : Material.BELL, ChatColor.YELLOW + e.nombre,
                    ChatColor.GRAY + e.codigo() + " · " + e.bx + ", " + e.by + ", " + e.bz,
                    ChatColor.GRAY + "Dueño: " + Bukkit.getOfflinePlayer(e.dueno).getName(),
                    veces > 0 ? ChatColor.GREEN + "En la ruta" : ChatColor.GRAY + "No está en la ruta",
                    ChatColor.YELLOW + "Clic izq.: añadir al final · der.: quitar"), (j, c) -> {
                if (c.isRightClick()) t.ruta.removeIf(x -> x == e.id);
                else if (t.ruta.size() < 20) t.ruta.add(e.id);
                t.idx = -1; t.viajando = false; t.plan = null;
                pl.guardar();
                ruta(j, t);
            });
        }
        List<String> orden = new ArrayList<>();
        for (int k = 0; k < t.ruta.size(); k++) orden.add(ChatColor.GRAY + "" + (k + 1) + ". " + Auto.nombre(pl, t.ruta.get(k)));
        if (orden.isEmpty()) orden.add(ChatColor.GRAY + "Sin paradas: elige estaciones arriba.");
        g.poner(45, Gui.boton(Material.WRITABLE_BOOK, ChatColor.GOLD + "Paradas en orden", orden), null);
        g.poner(47, Gui.boton(Material.TNT, ChatColor.RED + "Borrar la ruta"), (j, c) -> { t.ruta.clear(); t.auto = false; t.viajando = false; pl.guardar(); ruta(j, t); });
        g.poner(53, Gui.boton(Material.ARROW, ChatColor.YELLOW + "Volver"), (j, c) -> panel(j, t));
        g.abrir(p);
    }
}
