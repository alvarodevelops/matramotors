package matracraft.coches;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** Clase principal del plugin Matracar: coches, llaves, matrículas y comandos /coche, /gasolina y /gasolinera. */
public final class Coches extends JavaPlugin {
    NamespacedKey kTier, kMatricula, kGasolina, kLlave, kCartel, kEstado, kTicket, kAspecto, kYaw, kCarga, kTanda, kSerie;
    final Map<String, Tier> tiers = new LinkedHashMap<>();
    Economy eco;
    Precio precio;
    Gasolineras gasolineras;
    Conduccion conduccion;
    Tickets tickets;
    Registro registro;
    Seguros seguros;
    Modelos modelos;
    Carga carga;
    Ventas ventas;
    Menus menus;
    Carnet carnet;
    Bidones bidones;
    Gps gps;
    Taximetro taximetro;

    @Override
    public void onEnable() {
        migrarCarpetaVieja();
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadConfig();
        kTier = NamespacedKey.fromString("coches:tier");
        kMatricula = NamespacedKey.fromString("coches:matricula");
        kGasolina = NamespacedKey.fromString("coches:gasolina");
        kLlave = NamespacedKey.fromString("coches:llave");
        kCartel = NamespacedKey.fromString("coches:cartel");
        kEstado = NamespacedKey.fromString("coches:estado");
        kTicket = NamespacedKey.fromString("coches:ticket");
        kAspecto = NamespacedKey.fromString("coches:aspecto");
        kYaw = NamespacedKey.fromString("coches:yaw");
        kCarga = NamespacedKey.fromString("coches:carga");
        kTanda = NamespacedKey.fromString("coches:tanda");
        kSerie = NamespacedKey.fromString("coches:serie");
        cargarTiers();

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) eco = rsp.getProvider();
        else getLogger().warning("No encuentro la economia (Vault). No se podra comprar ni repostar.");

        precio = new Precio(this);
        gasolineras = new Gasolineras(this);
        tickets = new Tickets(this);
        registro = new Registro(this);
        seguros = new Seguros(this);
        conduccion = new Conduccion(this);
        modelos = new Modelos(this);
        carga = new Carga(this);
        ventas = new Ventas(this);
        menus = new Menus(this);
        carnet = new Carnet(this);
        gps = new Gps(this);
        bidones = new Bidones(this);
        getServer().getPluginManager().registerEvents(bidones, this);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(new Proteccion(this), this);
        getServer().getPluginManager().registerEvents(modelos, this);
        getServer().getPluginManager().registerEvents(carga, this);
        getServer().getPluginManager().registerEvents(conduccion, this);
        getServer().getPluginManager().registerEvents(gasolineras, this);
        getServer().getPluginManager().registerEvents(seguros, this);
        taximetro = new Taximetro(this);
        getServer().getPluginManager().registerEvents(taximetro, this);
        Extra.iniciar(this);
        Bukkit.getScheduler().runTaskTimer(this, () -> { conduccion.tick(); modelos.tick(); taximetro.tick(); }, 1L, 1L);
        Bukkit.getScheduler().runTask(this, modelos::montarTodo);

        // precio real de la gasolina: se mira cada 30 min (fuera del hilo principal)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (!precio.tocaActualizar()) return;
            if (precio.actualizar()) Bukkit.getScheduler().runTask(this, gasolineras::actualizarCarteles);
        }, 40L, 36000L);
    }

    // el plugin se llamó "Coches" y luego "Matracar": copia los datos a la carpeta nueva la primera vez
    private void migrarCarpetaVieja() {
        for (String nombre : List.of("Matracar", "Coches")) {
            File vieja = new File(getDataFolder().getParentFile(), nombre);
            if (!vieja.isDirectory() || vieja.equals(getDataFolder()) || new File(getDataFolder(), "config.yml").exists()) continue;
            try {
                Path origen = vieja.toPath(), destino = getDataFolder().toPath();
                try (Stream<Path> rutas = Files.walk(origen)) {
                    for (Path ruta : (Iterable<Path>) rutas::iterator) {
                        Path nueva = destino.resolve(origen.relativize(ruta).toString());
                        if (Files.isDirectory(ruta)) Files.createDirectories(nueva);
                        else if (!Files.exists(nueva) && !ruta.getFileName().toString().equals("CAMBIOS.md")) {
                            Files.createDirectories(nueva.getParent());
                            Files.copy(ruta, nueva);
                        }
                    }
                }
                getLogger().info("Datos copiados de plugins/" + nombre + " a plugins/" + getDataFolder().getName() + ". La carpeta vieja se puede borrar cuando compruebes que todo va bien.");
            } catch (IOException e) {
                getLogger().warning("No pude copiar los datos de plugins/" + nombre + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (carga != null) carga.cerrarTodo();
        if (carnet != null) carnet.guardar();
        if (gps != null) gps.pararTodo();
        if (conduccion != null) conduccion.pararTodo();
        if (modelos != null) modelos.quitarTodo();
    }

    private void cargarTiers() {
        tiers.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("coches");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s != null) tiers.put(id.toLowerCase(Locale.ROOT), Tier.leer(id.toLowerCase(Locale.ROOT), s));
        }
        ConfigurationSection barcos = getConfig().getConfigurationSection("barcos");
        if (barcos != null) for (String id : barcos.getKeys(false)) {
            ConfigurationSection s = barcos.getConfigurationSection(id);
            if (s != null) tiers.put(id.toLowerCase(Locale.ROOT), Tier.leer(id.toLowerCase(Locale.ROOT), s, true));
        }
        // ordenados de más barato a más caro
        List<Tier> lista = new ArrayList<>(tiers.values());
        lista.sort(Comparator.comparing(Tier::agua).thenComparingDouble(Tier::precio));
        tiers.clear();
        for (Tier t : lista) tiers.put(t.id(), t);
    }

    boolean mundoProhibido(World mundo) {
        List<String> permitidos = getConfig().getStringList("mundos-permitidos");
        if (!permitidos.isEmpty() && !permitidos.contains(mundo.getName())) return true;
        return getConfig().getStringList("mundos-prohibidos").contains(mundo.getName());
    }

    // ---------- objetos ----------

    ItemStack itemCoche(Tier tier, String matricula, double litros) {
        return itemCoche(tier, matricula, litros, 100.0);
    }

    ItemStack itemCoche(Tier tier, String matricula, double litros, double estado) {
        ItemStack item = new ItemStack(Material.MINECART);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + tier.nombre() + ChatColor.GRAY + " · " + ChatColor.WHITE + matricula);
        meta.setLore(List.of(
                ChatColor.GRAY + "Gasolina: " + ChatColor.YELLOW + String.format(Locale.ROOT, "%.1f / %.0f L", litros, tier.deposito()),
                ChatColor.GRAY + "Estado: " + (estado <= 0 ? ChatColor.DARK_RED + "AVERIADO"
                        : (estado < 30 ? ChatColor.RED : ChatColor.GREEN) + String.format(Locale.ROOT, "%.0f %%", estado)),
                ChatColor.GRAY + "Velocidad máx: " + ChatColor.WHITE + String.format(Locale.ROOT, "%.0f bloques/s", tier.velocidad()),
                ChatColor.DARK_GRAY + (tier.agua() ? "Clic derecho apuntando al agua para echarla." : "Clic derecho en el suelo para sacarlo."),
                ChatColor.DARK_GRAY + "Necesitas su llave para conducirlo."));
        if (tier.agua()) meta.setItemModel(NamespacedKey.minecraft("oak_boat"));
        if (tier.sinMotor()) {
            List<String> lore = new ArrayList<>(meta.getLore());
            lore.set(0, ChatColor.GRAY + "Sin motor: no gasta gasolina");
            meta.setLore(lore);
        }
        PersistentDataContainer datos = meta.getPersistentDataContainer();
        datos.set(kTier, PersistentDataType.STRING, tier.id());
        datos.set(kMatricula, PersistentDataType.STRING, matricula);
        datos.set(kGasolina, PersistentDataType.DOUBLE, litros);
        datos.set(kEstado, PersistentDataType.DOUBLE, estado);
        datos.set(kTanda, PersistentDataType.INTEGER, registro.tanda());
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    /** Pone el aspecto (colores) al coche y lo apunta en la descripción. */
    ItemStack conAspecto(ItemStack item, String aspecto) {
        Modelos.Aspecto a = Modelos.Aspecto.leer(aspecto);
        if (a == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(kAspecto, PersistentDataType.STRING, aspecto);
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.add(2, ChatColor.GRAY + "Color: " + ChatColor.WHITE + Modelos.nombreColor(a.c1()));
        Tier tier = tiers.get(meta.getPersistentDataContainer().get(kTier, PersistentDataType.STRING));
        String estilo = tier == null ? "" : Modelos.nombreEstilo(tier, a);
        if (!estilo.isEmpty()) lore.add(3, ChatColor.GRAY + "Estilo: " + ChatColor.WHITE + estilo);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack copiarAspecto(ItemStack origen, ItemStack destino) {
        String aspecto = origen.hasItemMeta()
                ? origen.getItemMeta().getPersistentDataContainer().get(kAspecto, PersistentDataType.STRING) : null;
        return aspecto == null ? destino : conAspecto(destino, aspecto);
    }

    ItemStack itemLlave(Tier tier, String matricula) {
        ItemStack llave = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = llave.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Llave · " + tier.nombre() + " " + ChatColor.WHITE + matricula);
        meta.setLore(List.of(ChatColor.GRAY + "Llévala en el inventario para conducir",
                ChatColor.GRAY + "el coche con matrícula " + ChatColor.WHITE + matricula + ChatColor.GRAY + "."));

        meta.getPersistentDataContainer().set(kLlave, PersistentDataType.STRING, matricula);
        meta.getPersistentDataContainer().set(kTanda, PersistentDataType.INTEGER, registro.tanda());
        meta.getPersistentDataContainer().set(kSerie, PersistentDataType.INTEGER, registro.serie(matricula));
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        llave.setItemMeta(meta);
        return llave;
    }

    boolean tieneLlave(Player p, String matricula) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            PersistentDataContainer datos = item.getItemMeta().getPersistentDataContainer();
            if (!matricula.equals(datos.get(kLlave, PersistentDataType.STRING)) || !deEstaTanda(datos)) continue;
            Integer serie = datos.get(kSerie, PersistentDataType.INTEGER);
            if ((serie == null ? 0 : serie) == registro.serie(matricula)) return true;
        }
        return false;
    }

    /** Los objetos de una tanda anterior (antes de /coche reiniciar) ya no valen. */
    boolean deEstaTanda(PersistentDataContainer datos) {
        Integer tanda = datos.get(kTanda, PersistentDataType.INTEGER);
        return (tanda == null ? 0 : tanda) == registro.tanda();
    }

    /** Quita coches guardados y llaves de tandas anteriores. Devuelve cuántos ha quitado. */
    int limpiarInventario(Inventory inv) {
        int quitados = 0;
        ItemStack[] contenido = inv.getContents();
        for (int i = 0; i < contenido.length; i++) {
            ItemStack item = contenido[i];
            if (item == null || !item.hasItemMeta()) continue;
            PersistentDataContainer datos = item.getItemMeta().getPersistentDataContainer();
            boolean nuestro = datos.has(kLlave, PersistentDataType.STRING) || item.getType() == Material.MINECART && datos.has(kMatricula, PersistentDataType.STRING);
            if (nuestro && !deEstaTanda(datos)) { inv.setItem(i, null); quitados++; }
        }
        return quitados;
    }

    private void reiniciarParque(CommandSender s, String[] args) {
        if (!s.hasPermission("coches.admin")) { s.sendMessage(ChatColor.RED + "No tienes permiso."); return; }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirmar")) {
            s.sendMessage(ChatColor.RED + "⚠ Esto borra TODOS los coches del server (aparcados, guardados y llaves), todas las pólizas y el registro.");
            s.sendMessage(ChatColor.RED + "Las matrículas vuelven a empezar en 0001 BBB. Para hacerlo: " + ChatColor.WHITE + "/coche reiniciar confirmar");
            return;
        }
        carga.cerrarTodo();
        registro.reiniciar();
        seguros.reiniciar();
        int coches = 0, objetos = 0;
        for (World mundo : Bukkit.getWorlds()) {
            for (ArmorStand m : mundo.getEntitiesByClass(ArmorStand.class)) {
                if (conduccion.esCoche(m)) { m.eject(); modelos.desmontar(m.getUniqueId()); conduccion.conduciendo.remove(m.getUniqueId()); m.remove(); coches++; }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            objetos += limpiarInventario(p.getInventory()) + limpiarInventario(p.getEnderChest());
        }
        getLogger().info("Parque de coches reiniciado (tanda " + registro.tanda() + ").");
        s.sendMessage(ChatColor.GREEN + "Hecho: " + coches + " coches retirados del mundo y " + objetos + " objetos/llaves quitados a los conectados.");
        s.sendMessage(ChatColor.GRAY + "Lo que quede en cofres o en zonas sin cargar desaparece solo en cuanto alguien lo toque o se cargue.");
    }

    /** Da los objetos al jugador; lo que no quepa cae al suelo. */
    void dar(Player p, ItemStack... items) {
        for (ItemStack item : items) {
            for (ItemStack sobra : p.getInventory().addItem(item).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        }
    }

    Prestados extraPrestados() { return Extra.prestados; }

    // ---------- comandos ----------

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String nombre = cmd.getName().toLowerCase(Locale.ROOT);
        if (nombre.equals("gasolina")) { infoGasolina(s); return true; }
        if (nombre.equals("gasolinera")) { gasolineras.comando(s, args); return true; }
        if (nombre.equals("vehiculos")) {
            if (!(s instanceof Player p)) { ayuda(s); return true; }
            if (args.length > 0 && args[0].toLowerCase(Locale.ROOT).startsWith("mis")) menus.misCoches(p); else menus.principal(p);
            return true;
        }
        if (nombre.equals("taximetro")) { if (s instanceof Player p) taximetro.comando(p, args); return true; }

        if (args.length == 0) {
            if (s instanceof Player p) menus.principal(p); else ayuda(s);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "comprar" -> comprar(s, args);
            case "menu", "menú" -> { if (s instanceof Player p) menus.principal(p); }
            case "ayuda", "help" -> ayuda(s);
            case "seguro", "seguros" -> { if (s instanceof Player p) seguros.comando(p, args); }
            case "reparar", "taller" -> { if (s instanceof Player p) seguros.reparar(p); }
            case "grua", "grúa" -> { if (s instanceof Player p) seguros.grua(p, args); }
            case "asistencia" -> { if (s instanceof Player p) seguros.asistencia(p); }
            case "siniestro" -> { if (s instanceof Player p) seguros.reclamarSiniestro(p, args); }
            case "partes" -> {
                if (!s.hasPermission("coches.admin")) { s.sendMessage(ChatColor.RED + "No tienes permiso."); return true; }
                List<String> partes = seguros.partes();
                s.sendMessage(ChatColor.GOLD + "— Partes de accidente (" + partes.size() + ") —");
                for (String linea : partes.subList(Math.max(0, partes.size() - 20), partes.size())) s.sendMessage(ChatColor.GRAY + " " + linea);
            }
            case "ticket" -> { if (s instanceof Player p) tickets.imprimir(p); }
            case "matricula", "matrícula" -> cambiarMatricula(s, args);
            case "matriculas", "registro" -> {
                if (!s.hasPermission("coches.admin")) { s.sendMessage(ChatColor.RED + "No tienes permiso."); return true; }
                Map<String, String> todas = registro.todas();
                s.sendMessage(ChatColor.GOLD + "— Registro de matrículas (" + todas.size() + ") —");
                for (Map.Entry<String, String> e : todas.entrySet()) s.sendMessage(ChatColor.WHITE + " " + e.getKey() + ChatColor.GRAY + "  " + e.getValue());
            }
            case "tarjeta", "tickets" -> {
                if (s instanceof Player p) {
                    p.sendMessage(ChatColor.GOLD + "Tickets válidos que llevas: " + ChatColor.WHITE + tickets.validos(p) + "/" + tickets.necesarios()
                            + ChatColor.GRAY + String.format(Locale.ROOT, " (repostajes de %.2f Ⓜ o más). Con %d, el siguiente repostaje es gratis.", tickets.importeMinimo(), tickets.necesarios()));
                }
            }
            case "copiallave", "copia", "duplicado" -> copiarLlave(s, args);
            case "reiniciar" -> reiniciarParque(s, args);
            case "vender" -> { if (s instanceof Player p) ventas.vender(p, args); }
            case "desguace", "desguazar" -> { if (s instanceof Player p) ventas.desguace(p, args); }
            case "aceptar" -> { if (s instanceof Player p) ventas.aceptar(p); }
            case "rechazar" -> { if (s instanceof Player p) ventas.rechazar(p); }
            case "dar" -> {
                if (!s.hasPermission("coches.admin")) { s.sendMessage(ChatColor.RED + "No tienes permiso."); return true; }
                if (args.length < 3) { s.sendMessage(ChatColor.YELLOW + "Uso: /coche dar <jugador> <" + String.join("|", tiers.keySet()) + ">"); return true; }
                Player destino = Bukkit.getPlayerExact(args[1]);
                Tier tier = tiers.get(args[2].toLowerCase(Locale.ROOT));
                if (destino == null || tier == null) { s.sendMessage(ChatColor.RED + "Jugador o coche no encontrado."); return true; }
                String matricula = registro.siguiente(tier, destino.getName());
                dar(destino, itemCoche(tier, matricula, tier.deposito()), itemLlave(tier, matricula));
                destino.sendMessage(ChatColor.GREEN + "¡Te han regalado un " + tier.nombre() + " (" + matricula + ") con el depósito lleno!");
                destino.sendMessage(ChatColor.YELLOW + "Antes de conducirlo contrata un seguro: /coche seguro " + matricula.replace(" ", ""));
                s.sendMessage(ChatColor.GREEN + "Dado un " + tier.nombre() + " a " + destino.getName() + ".");
            }
            case "llave" -> {
                if (!s.hasPermission("coches.admin")) { s.sendMessage(ChatColor.RED + "No tienes permiso."); return true; }
                if (!(s instanceof Player p) || args.length < 3) {
                    s.sendMessage(ChatColor.YELLOW + "Uso (en el juego): /coche llave <tier> <matrícula>  (ej: /coche llave turismo 1234 BCD)");
                    return true;
                }
                Tier tier = tiers.get(args[1].toLowerCase(Locale.ROOT));
                if (tier == null) { s.sendMessage(ChatColor.RED + "Tier desconocido."); return true; }
                String matricula = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).toUpperCase(Locale.ROOT);
                dar(p, itemLlave(tier, matricula));
                s.sendMessage(ChatColor.GREEN + "Copia de llave creada para " + matricula + ".");
            }
            default -> ayuda(s);
        }
        return true;
    }

    private void cambiarMatricula(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("Solo jugadores."); return; }
        double coste = getConfig().getDouble("matricula.precio-cambio", 2500.0);
        if (args.length < 2) {
            p.sendMessage(ChatColor.GOLD + "Cambiar matrícula: " + ChatColor.GRAY + "guarda el coche, ponlo en la mano y escribe " + ChatColor.WHITE + "/coche matricula 0007 PKY"
                    + ChatColor.GRAY + String.format(Locale.ROOT, " (%,.2f Ⓜ).", coste));
            p.sendMessage(ChatColor.GRAY + "Formato español: 4 números y 3 consonantes (" + Registro.LETRAS + "). Te damos llave nueva; las viejas dejan de valer.");
            return;
        }
        ItemStack mano = p.getInventory().getItemInMainHand();
        String vieja = mano.hasItemMeta() ? mano.getItemMeta().getPersistentDataContainer().get(kMatricula, PersistentDataType.STRING) : null;
        if (mano.getType() != Material.MINECART || vieja == null) { p.sendMessage(ChatColor.RED + "Pon el coche (guardado) en la mano."); return; }
        if (!Extra.llaveYDueno(this, p, vieja)) { p.sendMessage(ChatColor.RED + "Necesitas la llave de " + vieja + " encima."); return; }
        PersistentDataContainer datos = mano.getItemMeta().getPersistentDataContainer();
        Tier tier = tiers.get(datos.get(kTier, PersistentDataType.STRING));
        if (tier == null) return;
        String nueva = Registro.normalizar(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        if (nueva == null) { p.sendMessage(ChatColor.RED + "Matrícula no válida. Ejemplo: 0007 PKY (solo consonantes " + Registro.LETRAS + ")."); return; }
        registro.asegurar(vieja, tier, p.getName());
        if (nueva.equals(vieja)) { p.sendMessage(ChatColor.YELLOW + "Ya tiene esa matrícula."); return; }
        if (!registro.libre(nueva)) { p.sendMessage(ChatColor.RED + "La matrícula " + nueva + " ya está cogida."); return; }
        if (eco == null || !eco.has(p, coste)) { p.sendMessage(ChatColor.RED + String.format(Locale.ROOT, "Cambiar la matrícula cuesta %,.2f Ⓜ.", coste)); return; }
        if (!eco.withdrawPlayer(p, coste).transactionSuccess()) { p.sendMessage(ChatColor.RED + "No se pudo cobrar."); return; }
        Double gasolina = datos.get(kGasolina, PersistentDataType.DOUBLE);
        Double estado = datos.get(kEstado, PersistentDataType.DOUBLE);
        p.getInventory().setItemInMainHand(copiarAspecto(mano, itemCoche(tier, nueva, gasolina == null ? 0.0 : gasolina, estado == null ? 100.0 : estado)));
        seguros.moverPoliza(vieja, nueva);

        // fuera las llaves viejas que lleve encima
        ItemStack[] contenido = p.getInventory().getContents();
        for (int i = 0; i < contenido.length; i++) {
            ItemStack item = contenido[i];
            if (item != null && item.hasItemMeta()
                    && vieja.equals(item.getItemMeta().getPersistentDataContainer().get(kLlave, PersistentDataType.STRING))) {
                p.getInventory().setItem(i, null);
            }
        }
        dar(p, itemLlave(tier, nueva));
        registro.cambiar(vieja, nueva, tier, p.getName());
        p.sendMessage(ChatColor.GREEN + "Matrícula cambiada: " + ChatColor.GRAY + vieja + ChatColor.GREEN + " → " + ChatColor.WHITE + ChatColor.BOLD + nueva
                + ChatColor.GREEN + String.format(Locale.ROOT, " (%,.2f Ⓜ). Aquí tienes la llave nueva.", coste));
    }

    private void copiarLlave(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("Solo jugadores."); return; }
        double coste = getConfig().getDouble("llaves.precio-copia", 500.0);
        ItemStack mano = p.getInventory().getItemInMainHand();
        String matricula = mano.hasItemMeta() ? mano.getItemMeta().getPersistentDataContainer().get(kLlave, PersistentDataType.STRING) : null;
        if (matricula == null && args.length > 1) {
            // también vale "/coche copiallave 1234 BCD" con la llave en cualquier hueco
            String buscada = Registro.normalizar(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            for (ItemStack item : p.getInventory().getContents()) {
                if (buscada == null || item == null || !item.hasItemMeta()) continue;
                if (buscada.equals(item.getItemMeta().getPersistentDataContainer().get(kLlave, PersistentDataType.STRING)) && Extra.llaveYDueno(this, p, buscada)) {
                    mano = item; matricula = buscada; break;
                }
            }
        }
        if (matricula != null && !Extra.llaveYDueno(this, p, matricula)) { p.sendMessage(ChatColor.RED + "Esa llave ya no vale."); return; }
        if (matricula == null) {
            p.sendMessage(ChatColor.YELLOW + "Pon la llave en la mano y escribe " + ChatColor.WHITE + "/coche copiallave" + ChatColor.YELLOW
                    + String.format(Locale.ROOT, " (%,.2f Ⓜ). La copia abre y arranca igual que la original.", coste));
            return;
        }
        if (eco == null || !eco.has(p, coste)) { p.sendMessage(ChatColor.RED + String.format(Locale.ROOT, "Una copia cuesta %,.2f Ⓜ.", coste)); return; }
        if (!eco.withdrawPlayer(p, coste).transactionSuccess()) { p.sendMessage(ChatColor.RED + "No se pudo cobrar."); return; }
        ItemStack copia = mano.clone();
        copia.setAmount(1);
        ItemMeta meta = copia.getItemMeta();
        meta.getPersistentDataContainer().set(kSerie, PersistentDataType.INTEGER, registro.serie(matricula));
        meta.getPersistentDataContainer().set(kTanda, PersistentDataType.INTEGER, registro.tanda());
        copia.setItemMeta(meta);
        dar(p, copia);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.5f, 1.8f);
        p.sendMessage(ChatColor.GREEN + "🔑 Copia de la llave de " + ChatColor.WHITE + matricula + ChatColor.GREEN
                + String.format(Locale.ROOT, " hecha (%,.2f Ⓜ). ", coste) + ChatColor.GRAY + "Si cambias la matrícula, todas las copias dejan de valer.");
    }

    private void ayuda(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "— MATRAMOTORS —" + ChatColor.GRAY + " (escribe /vehiculos o /coche para abrir el menú de todo: coches, barcos y trenes)");
        s.sendMessage(ChatColor.YELLOW + "/coche comprar" + ChatColor.GRAY + " ver los coches y sus precios");
        s.sendMessage(ChatColor.YELLOW + "/gasolina" + ChatColor.GRAY + " precio de hoy de la gasolina");
        s.sendMessage(ChatColor.YELLOW + "/coche tickets" + ChatColor.GRAY + " cuántos tickets válidos llevas (10 = un repostaje gratis)");
        s.sendMessage(ChatColor.YELLOW + "/coche matricula" + ChatColor.GRAY + " cambiar la matrícula de tu coche (pagando)");
        s.sendMessage(ChatColor.YELLOW + "/coche copiallave" + ChatColor.GRAY + " duplicado de la llave que llevas en la mano (pagando)");
        s.sendMessage(ChatColor.YELLOW + "/coche vender <jugador> <precio>" + ChatColor.GRAY + " vender el coche que llevas en la mano a alguien que esté a tu lado");
        s.sendMessage(ChatColor.YELLOW + "/coche desguace" + ChatColor.GRAY + " vender al desguace el coche que llevas en la mano (hasta la mitad de su precio, según su estado)");
        s.sendMessage(ChatColor.YELLOW + "/coche seguro" + ChatColor.GRAY + " tus pólizas · " + ChatColor.YELLOW + "/coche reparar" + ChatColor.GRAY + " (coche en la mano) · "
                + ChatColor.YELLOW + "/coche grua <matrícula>" + ChatColor.GRAY + " · " + ChatColor.YELLOW + "/coche asistencia" + ChatColor.GRAY + " (sin gasolina)");

        s.sendMessage(ChatColor.GRAY + "Conducir: W acelera, S frena/marcha atrás, A/D giran, espacio = claxon, mayúsculas = bajarse.");
        s.sendMessage(ChatColor.GRAY + "Solo se sube de altura por escaleras (el quad también por escalones de tierra o piedra).");
        s.sendMessage(ChatColor.GRAY + "Guardar: mayúsculas + clic derecho, o mayúsculas + golpe. Con maletero: mayúsculas + clic derecho lo abre; se guarda con mayúsculas + golpe y vacío.");
        s.sendMessage(ChatColor.GRAY + "Para repostar, para el coche encima de un surtidor y espera.");
        if (s.hasPermission("coches.admin"))
            s.sendMessage(ChatColor.DARK_AQUA + "Admin: /coche dar <jugador> <tier> · /coche llave <tier> <matrícula> · /coche matriculas · /coche reiniciar · /gasolinera crear|renombrar|borrar|lista|ir");
    }

    private void comprar(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("Solo jugadores."); return; }
        String id = args.length > 1 ? args[1] : null;
        if (id == null) {
            p.sendMessage(ChatColor.GOLD + "— Concesionario —");
            for (Tier t : tiers.values()) {
                p.sendMessage(ChatColor.YELLOW + t.nombre() + ChatColor.GRAY + String.format(Locale.ROOT,
                        ": %,.2f Ⓜ · %.0f bl/s · depósito %.0f L · autonomía ~%,.0f bloques  ", t.precio(), t.velocidad(), t.deposito(), t.sinMotor() ? 0 : t.deposito() / t.consumo())
                        + ChatColor.WHITE + "/coche comprar " + t.id());
            }
            p.sendMessage(ChatColor.GRAY + "Todos los coches necesitan seguro para circular.");
            return;
        }
        Tier tier = tiers.get(id.toLowerCase(Locale.ROOT));
        if (tier == null) { p.sendMessage(ChatColor.RED + "Ese coche no existe. Mira /coche comprar"); return; }
        if (args.length < 4 || !Seguros.TIPOS.contains(args[2].toLowerCase(Locale.ROOT)) || !Seguros.MODOS.contains(args[3].toLowerCase(Locale.ROOT))) {
            p.sendMessage(ChatColor.GOLD + tier.nombre() + ChatColor.GRAY + String.format(Locale.ROOT, " — %,.2f Ⓜ + el seguro que elijas:", tier.precio()));
            seguros.ofrecer(p, tier, "/coche comprar " + tier.id());
            return;
        }
        String tipo = args[2].toLowerCase(Locale.ROOT);
        String modo = args[3].toLowerCase(Locale.ROOT);
        if (eco == null) { p.sendMessage(ChatColor.RED + "La economía no está disponible."); return; }
        double seguro = seguros.precio(tier, tipo, modo, p.getUniqueId());
        double total = tier.precio() + seguro;
        if (!eco.has(p, total)) {
            p.sendMessage(ChatColor.RED + String.format(Locale.ROOT, "Coche + seguro son %,.2f Ⓜ. Te faltan %,.2f Ⓜ.", total, total - eco.getBalance(p)));
            return;
        }
        if (!eco.withdrawPlayer(p, total).transactionSuccess()) { p.sendMessage(ChatColor.RED + "No se pudo cobrar."); return; }
        seguros.aportarFondo(seguro);
        String matricula = registro.siguiente(tier, p.getName());
        seguros.contratar(p, matricula, tier, tipo, modo, false);
        double litros = Math.min(tier.deposito(), getConfig().getDouble("litros-al-comprar", 5.0));
        Modelos.Aspecto aspecto = Modelos.azar(tier);
        carnet.sumar(p, "comprados", 1);
        if (tier.precio() > carnet.valor(p.getUniqueId(), "mas-caro")) {
            carnet.maximo(p.getUniqueId(), "mas-caro", tier.precio());
            carnet.nombre(p.getUniqueId(), "coche-mas-caro", tier.nombre());
        }
        dar(p, conAspecto(itemCoche(tier, matricula, litros, 100.0), aspecto.guardar()), itemLlave(tier, matricula));
        p.sendMessage(ChatColor.GREEN + "¡Enhorabuena! Has comprado un " + ChatColor.GOLD + tier.nombre() + ChatColor.GREEN + " con matrícula " + ChatColor.WHITE + matricula
                + ChatColor.GREEN + ". Viene con " + String.format(Locale.ROOT, "%.0f", litros) + " L de regalo.");
        p.sendMessage(ChatColor.GRAY + "Te ha tocado en color " + ChatColor.WHITE + Modelos.nombreColor(aspecto.c1()).toLowerCase(Locale.ROOT) + ChatColor.GRAY
                + ". No pierdas la llave: sin ella no se puede conducir.");
    }

    private void infoGasolina(CommandSender s) {
        String fuente = precio.esReal()
                ? String.format(Locale.ROOT, "media de %d gasolineras reales cerca de %s (%s)", precio.estaciones(),
                        getConfig().getString("gasolina.lugar", "Madrid"), fechaBonita(precio.fecha()))
                : "precio por defecto (todavía sin datos reales)";
        String cuando = precio.esReal() && precio.esDeHoy() ? "hoy" : "(último conocido)";
        s.sendMessage(ChatColor.GOLD + "⛽ Gasolina 95 " + cuando + ": " + ChatColor.WHITE + String.format(Locale.ROOT, "%.3f €/L", precio.euros())
                + ChatColor.GRAY + " → " + ChatColor.YELLOW + String.format(Locale.ROOT, "%.2f Ⓜ/L", precio.dolares()));
        s.sendMessage(ChatColor.GRAY + fuente);
    }

    // "2026-03-07" -> "07/03/2026"
    private static String fechaBonita(String fecha) {
        String[] partes = fecha.split("-");
        return partes.length == 3 ? partes[2] + "/" + partes[1] + "/" + partes[0] : fecha;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> res = new ArrayList<>();
        String nombre = cmd.getName().toLowerCase(Locale.ROOT);
        if (nombre.equals("gasolinera")) {
            if (args.length == 1) for (String o : List.of("crear", "renombrar", "borrar", "lista", "ir", "precio")) if (o.startsWith(args[0])) res.add(o);
            return res;
        }
        if (!nombre.equals("coche")) return res;
        if (args.length == 1) {
            for (String o : s.hasPermission("coches.admin")
                    ? List.of("comprar", "seguro", "reparar", "grua", "asistencia", "siniestro", "tickets", "ticket", "matricula", "copiallave", "vender", "desguace", "reiniciar", "dar", "llave", "matriculas", "partes")
                    : List.of("comprar", "seguro", "reparar", "grua", "asistencia", "siniestro", "tickets", "ticket", "matricula", "copiallave", "vender", "desguace"))
                if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) res.add(o);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("comprar") || args[0].equalsIgnoreCase("llave"))) {
            for (String t : tiers.keySet()) if (t.startsWith(args[1].toLowerCase(Locale.ROOT))) res.add(t);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("comprar")) {
            for (String t : Seguros.TIPOS) if (t.startsWith(args[2].toLowerCase(Locale.ROOT))) res.add(t);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("comprar")) {
            for (String m : Seguros.MODOS) if (m.startsWith(args[3].toLowerCase(Locale.ROOT))) res.add(m);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("dar") || args[0].equalsIgnoreCase("vender"))) {
            for (Player p : Bukkit.getOnlinePlayers()) res.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("dar")) {
            res.addAll(tiers.keySet());
        }
        return res;
    }

    /** Clave de un bloque: "mundo:x:y:z". */
    static String clave(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }
}
