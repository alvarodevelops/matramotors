package matracraft.coches;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Seguros de coche: pólizas por matrícula (seguros.yml), malus/bonus del conductor,
 * atropellos, reparaciones, siniestros, grúa y asistencia en carretera.
 */
final class Seguros implements Listener {

    static final long SEMANA = 7L * 24 * 60 * 60 * 1000;
    static final List<String> TIPOS = List.of("terceros", "ampliado", "todoriesgo");
    static final List<String> MODOS = List.of("semanal", "mensual");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    // Valor por defecto de cada animal atropellado (configurable en seguros.valores.<tipo>)
    private static final Map<String, Double> VALORES = Map.ofEntries(
            Map.entry("cow", 150.0), Map.entry("mooshroom", 300.0), Map.entry("sheep", 120.0),
            Map.entry("pig", 120.0), Map.entry("chicken", 40.0), Map.entry("rabbit", 60.0),
            Map.entry("goat", 200.0), Map.entry("horse", 800.0), Map.entry("donkey", 500.0),
            Map.entry("mule", 600.0), Map.entry("camel", 700.0), Map.entry("llama", 400.0),
            Map.entry("wolf", 500.0), Map.entry("cat", 400.0), Map.entry("parrot", 300.0),
            Map.entry("fox", 300.0), Map.entry("villager", 1500.0), Map.entry("iron_golem", 800.0),
            Map.entry("axolotl", 200.0), Map.entry("turtle", 250.0), Map.entry("bee", 80.0),
            Map.entry("armadillo", 200.0), Map.entry("sniffer", 900.0));

    private final Coches pl;
    private final File archivo;
    private final YamlConfiguration y;

    Seguros(Coches pl) {
        this.pl = pl;
        this.archivo = new File(pl.getDataFolder(), "seguros.yml");
        this.y = YamlConfiguration.loadConfiguration(archivo);
        Bukkit.getScheduler().runTaskTimer(pl, this::renovar, 600L, 6000L);
    }

    // ---------- fondo de las aseguradoras ----------
    // Las primas van a un fondo común y las indemnizaciones salen de él: el seguro no crea dinero.
    private final Map<String, Long> ultimaIndemnizacion = new HashMap<>();

    double fondo() { return y.getDouble("fondo.saldo", 0); }

    void aportarFondo(double cantidad) {
        if (cantidad <= 0) return;
        y.set("fondo.saldo", Math.round((fondo() + cantidad) * 100.0) / 100.0);
        y.set("fondo.primas-totales", y.getDouble("fondo.primas-totales", 0) + cantidad);
        guardar();
    }

    /** Saca hasta 'cantidad' del fondo y devuelve lo que ha podido sacar. */
    private double sacarFondo(double cantidad) {
        double sacado = Math.min(cantidad, fondo());
        if (sacado <= 0) return 0;
        y.set("fondo.saldo", Math.round((fondo() - sacado) * 100.0) / 100.0);
        y.set("fondo.indemnizado-total", y.getDouble("fondo.indemnizado-total", 0) + sacado);
        return sacado;
    }

    void guardar() {
        try { y.save(archivo); } catch (IOException e) { pl.getLogger().warning("No pude guardar seguros.yml"); }
    }

    // Las matrículas llevan espacio; en el yml se guardan con guion bajo
    private static String k(String matricula) { return matricula.replace(' ', '_'); }

    // ---------- Compañías y coberturas ----------

    static String compania(String tipo) {
        return switch (tipo) {
            case "terceros" -> "Mutua Central";
            case "ampliado" -> "La Previsora";
            default -> "Aseguradora Norte";
        };
    }

    static String nombreTipo(String tipo) {
        return switch (tipo) { case "terceros" -> "Terceros"; case "ampliado" -> "Terceros Ampliado"; default -> "Todo Riesgo"; };
    }

    static String cobertura(String tipo) {
        return switch (tipo) {
            case "terceros" -> "Paga los atropellos a otros. Grúa 300 Ⓜ, reparaciones a tu cargo.";
            case "ampliado" -> "Atropellos + 1 grúa gratis/semana + 5 L de emergencia + 50 % de las reparaciones.";
            default -> "Atropellos + grúa gratis + 10 L de emergencia + reparaciones gratis + coche nuevo si hay siniestro total (franquicia 20 %).";
        };
    }

    // ---------- Malus del conductor: +1 por parte (máx. 4), -1 por semana limpia (mín. -2) ----------

    int malus(UUID conductor) {
        return y.getInt("conductores." + conductor + ".nivel", 0);
    }

    double factorMalus(UUID conductor) {
        int nivel = malus(conductor);
        return nivel >= 0 ? 1 + 0.25 * nivel : 1 + 0.1 * nivel;
    }

    // Precio de la póliza: % del precio del coche, por el malus; el mensual son 4 semanas con descuento
    double precio(Tier tier, String tipo, String modo, UUID conductor) {
        double tasa = switch (tipo) {
            case "terceros" -> pl.getConfig().getDouble("seguros.terceros", 0.015);
            case "ampliado" -> pl.getConfig().getDouble("seguros.ampliado", 0.03);
            default -> pl.getConfig().getDouble("seguros.todoriesgo", 0.05);
        };
        double semanal = tier.precio() * tasa * factorMalus(conductor);
        double total = modo.equals("mensual") ? semanal * 4 * (1 - pl.getConfig().getDouble("seguros.descuento-mensual", 0.15)) : semanal;
        return Math.round(total);
    }

    // ---------- Pólizas ----------

    String tipo(String matricula) {
        return y.getString("polizas." + k(matricula) + ".tipo");
    }

    boolean vigente(String matricula) {
        return y.isSet("polizas." + k(matricula)) && y.getLong("polizas." + k(matricula) + ".vence", 0) > System.currentTimeMillis();
    }

    String tipoVigente(String matricula) {
        return vigente(matricula) ? tipo(matricula) : null;
    }

    boolean contratar(Player jugador, String matricula, Tier tier, String tipo, String modo, boolean cobrar) {
        double precio = precio(tier, tipo, modo, jugador.getUniqueId());
        if (cobrar) {
            if (pl.eco == null || !pl.eco.has(jugador, precio)) {
                jugador.sendMessage(ChatColor.RED + String.format(Locale.ROOT, "Ese seguro cuesta %,.2f Ⓜ y no te llega.", precio));
                return false;
            }
            if (!pl.eco.withdrawPlayer(jugador, precio).transactionSuccess()) return false;
            aportarFondo(precio);
        }
        String base = "polizas." + k(matricula);
        long ahora = System.currentTimeMillis();
        long desde = Math.max(ahora, y.getLong(base + ".vence", 0));
        // Si renueva el mismo tipo y aún está en vigor, se suma al tiempo que le quedaba
        boolean mismo = tipo.equals(y.getString(base + ".tipo")) && vigente(matricula);
        y.set(base + ".tipo", tipo);
        y.set(base + ".modo", modo);
        y.set(base + ".tier", tier.id());
        y.set(base + ".titular", jugador.getUniqueId().toString());
        y.set(base + ".nombre", jugador.getName());
        y.set(base + ".vence", (mismo ? desde : ahora) + (modo.equals("mensual") ? 4 * SEMANA : SEMANA));
        guardar();
        jugador.sendMessage(ChatColor.GREEN + "Póliza " + ChatColor.WHITE + nombreTipo(tipo) + ChatColor.GREEN + " de " + ChatColor.GOLD + compania(tipo)
                + ChatColor.GREEN + " para " + matricula + String.format(Locale.ROOT, " (%s, %,.2f Ⓜ). ", modo, precio) + ChatColor.GRAY + "Se renueva sola mientras tengas saldo.");
        return true;
    }

    // Menú clicable con los tipos de seguro y sus precios
    void ofrecer(Player jugador, Tier tier, String comandoBase) {
        jugador.sendMessage(ChatColor.GOLD + "Elige el seguro (obligatorio para conducir):");
        for (String tipo : TIPOS) {
            Component linea = Component.text(" " + nombreTipo(tipo), NamedTextColor.YELLOW)
                    .append(Component.text(" · " + compania(tipo) + "  ", NamedTextColor.GRAY));
            for (String modo : MODOS) {
                double precio = precio(tier, tipo, modo, jugador.getUniqueId());
                linea = linea.append(Component.text(String.format(Locale.ROOT, "[%s %,.2f Ⓜ] ", modo, precio), modo.equals("semanal") ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand(comandoBase + " " + tipo + " " + modo))
                        .hoverEvent(HoverEvent.showText(Component.text(cobertura(tipo)
                                + (modo.equals("mensual") ? "\nPagas 4 semanas de golpe con descuento." : "\nSe cobra cada semana automáticamente.")))));
            }
            jugador.sendMessage(linea);
        }
        int nivel = malus(jugador.getUniqueId());
        if (nivel != 0) jugador.sendMessage(ChatColor.GRAY + (nivel > 0 ? "Tu historial tiene " + nivel + " parte(s): pagas un " + 25 * nivel + " % más."
                : "Buen conductor: tienes un " + -10 * nivel + " % de descuento."));
    }

    // Cobra las pólizas vencidas; si no hay saldo, la póliza caduca
    private void renovar() {
        ConfigurationSection polizas = y.getConfigurationSection("polizas");
        if (polizas == null) return;
        long ahora = System.currentTimeMillis();
        boolean cambios = false;
        for (String clave : polizas.getKeys(false)) {
            String base = "polizas." + clave;
            long vence = y.getLong(base + ".vence", 0);
            if (vence > ahora || y.getBoolean(base + ".caducada", false)) continue;
            UUID titular = UUID.fromString(y.getString(base + ".titular"));
            Tier tier = pl.tiers.get(y.getString(base + ".tier"));
            if (tier == null) continue;
            // Una semana sin partes rebaja el malus
            long ultimoParte = y.getLong("conductores." + titular + ".ultimo-parte", 0);
            int nivel = malus(titular);
            if (ahora - ultimoParte > SEMANA && nivel > -2) y.set("conductores." + titular + ".nivel", nivel - 1);
            String tipo = y.getString(base + ".tipo");
            String modo = y.getString(base + ".modo", "semanal");
            double precio = precio(tier, tipo, modo, titular);
            OfflinePlayer titularOff = Bukkit.getOfflinePlayer(titular);
            String matricula = clave.replace('_', ' ');
            if (pl.eco != null && pl.eco.has(titularOff, precio) && pl.eco.withdrawPlayer(titularOff, precio).transactionSuccess()) {
                aportarFondo(precio);
                y.set(base + ".vence", vence + (modo.equals("mensual") ? 4 * SEMANA : SEMANA));
                avisar(titular, ChatColor.GREEN + "Seguro de " + matricula + " renovado (" + String.format(Locale.ROOT, "%,.2f Ⓜ", precio) + ").");
            } else {
                y.set(base + ".caducada", true);
                avisar(titular, ChatColor.RED + "El seguro de " + matricula + " ha CADUCADO por falta de saldo. No podrás conducirlo hasta renovarlo con /coche seguro.");
            }
            cambios = true;
        }
        if (cambios) guardar();
    }

    // Mensaje al jugador; si no está conectado se le guarda para cuando entre
    private void avisar(UUID id, String mensaje) {
        Player jugador = Bukkit.getPlayer(id);
        if (jugador != null) jugador.sendMessage(mensaje);
        else { List<String> avisos = new ArrayList<>(y.getStringList("avisos." + id)); avisos.add(mensaje); y.set("avisos." + id, avisos); }
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        int retirados = pl.limpiarInventario(e.getPlayer().getInventory()) + pl.limpiarInventario(e.getPlayer().getEnderChest());
        if (retirados > 0) Bukkit.getScheduler().runTaskLater(pl, () -> e.getPlayer().sendMessage(ChatColor.GRAY + "Se han retirado " + retirados + " coches/llaves de antes del reinicio del parque de coches."), 60L);

        List<String> avisos = y.getStringList("avisos." + e.getPlayer().getUniqueId());
        if (avisos.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(pl, () -> avisos.forEach(e.getPlayer()::sendMessage), 60L);
        y.set("avisos." + e.getPlayer().getUniqueId(), null);
        guardar();
    }

    // ---------- /coche seguro ----------

    void comando(Player jugador, String[] args) {
        if (args.length < 2) {
            jugador.sendMessage(ChatColor.GOLD + "— Tus seguros —");
            boolean alguna = false;
            for (ItemStack item : jugador.getInventory().getContents()) {
                if (item == null || !item.hasItemMeta()) continue;
                String matricula = item.getItemMeta().getPersistentDataContainer().get(pl.kLlave, PersistentDataType.STRING);
                if (matricula == null) continue;
                alguna = true;
                String base = "polizas." + k(matricula);
                if (!y.isSet(base)) { jugador.sendMessage(ChatColor.RED + " " + matricula + ": SIN SEGURO" + ChatColor.GRAY + "  → /coche seguro " + matricula.replace(" ", "")); continue; }
                String estado = vigente(matricula) ? ChatColor.GREEN + "en vigor hasta " + FECHA.format(Instant.ofEpochMilli(y.getLong(base + ".vence")).atZone(ZoneId.of("Europe/Madrid")))
                        : ChatColor.RED + "CADUCADO";
                jugador.sendMessage(ChatColor.WHITE + " " + matricula + ChatColor.GRAY + " · " + nombreTipo(tipo(matricula)) + " (" + y.getString(base + ".modo") + ") · " + estado);
            }
            if (!alguna) jugador.sendMessage(ChatColor.GRAY + " No llevas ninguna llave encima.");
            jugador.sendMessage(ChatColor.GRAY + "Para contratar o cambiar: /coche seguro <matrícula>  (con la llave encima)");
            return;
        }
        // La matrícula puede venir en dos trozos ("1234 ABC"): si el segundo tiene 3 letras y no es un tipo, es parte de ella
        String matricula = Registro.normalizar(args[1] + (args.length > 2 && args[2].length() == 3 && !TIPOS.contains(args[2].toLowerCase(Locale.ROOT)) ? args[2] : ""));
        int i = args.length > 2 && args[2].length() == 3 && !TIPOS.contains(args[2].toLowerCase(Locale.ROOT)) ? 3 : 2;
        if (matricula == null) { jugador.sendMessage(ChatColor.RED + "Matrícula no válida."); return; }
        if (!Extra.llaveYDueno(pl, jugador, matricula)) { jugador.sendMessage(ChatColor.RED + "Necesitas la llave de " + matricula + " encima."); return; }
        Tier tier = Extra.tierDuenoLlave(this, jugador, matricula);
        if (tier == null) { jugador.sendMessage(ChatColor.RED + "No sé qué coche es ese."); return; }
        if (args.length < i + 2) { ofrecer(jugador, tier, "/coche seguro " + matricula.replace(" ", "")); return; }
        String tipo = args[i].toLowerCase(Locale.ROOT), modo = args[i + 1].toLowerCase(Locale.ROOT);
        if (!TIPOS.contains(tipo) || !MODOS.contains(modo)) { ofrecer(jugador, tier, "/coche seguro " + matricula.replace(" ", "")); return; }
        if (contratar(jugador, matricula, tier, tipo, modo, true)) y.set("polizas." + k(matricula) + ".caducada", null);
        guardar();
    }

    // Tier del coche de esa matrícula: el de la póliza o el de la llave/coche que lleve encima
    Tier tierDeLlave(Player jugador, String matricula) {
        String id = y.getString("polizas." + k(matricula) + ".tier");
        if (id != null && pl.tiers.containsKey(id)) return pl.tiers.get(id);

        for (ItemStack item : jugador.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            PersistentDataContainer datos = item.getItemMeta().getPersistentDataContainer();
            if (matricula.equals(datos.get(pl.kLlave, PersistentDataType.STRING)) || matricula.equals(datos.get(pl.kMatricula, PersistentDataType.STRING))) {
                String idTier = datos.get(pl.kTier, PersistentDataType.STRING);
                if (idTier != null) return pl.tiers.get(idTier);
                String nombre = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                for (Tier t : pl.tiers.values()) if (nombre.contains(t.nombre())) return t;
            }
        }
        return null;
    }

    // ---------- Atropellos ----------

    void atropello(Player conductor, Conduccion.Estado estado, LivingEntity victima, double dano, boolean mortal) {
        if (Extra.sinSeguro(victima)) return;
        UUID dueno = null;
        String nombreDueno;
        double importe;
        if (victima instanceof Player p) {
            dueno = p.getUniqueId(); nombreDueno = p.getName();
            importe = dano * pl.getConfig().getDouble("seguros.indemnizacion.por-vida-jugador", 40) + (mortal ? pl.getConfig().getDouble("seguros.indemnizacion.muerte-jugador", 800) : 0);
        } else {
            if (victima instanceof Monster)
                return;
            // Animal: de su dueño si está domesticado, o del dueño de la parcela donde está
            if (victima instanceof Tameable t && t.isTamed()) {
                AnimalTamer amo = t.getOwner();
                if (amo != null) dueno = amo.getUniqueId();
            }
            if (dueno == null && Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) dueno = GpHook.dueno(victima.getLocation());
            if (dueno == null) return;
            OfflinePlayer duenoOff = Bukkit.getOfflinePlayer(dueno);
            nombreDueno = duenoOff.getName() == null ? "alguien" : duenoOff.getName();
            String tipo = victima.getType().name().toLowerCase(Locale.ROOT);
            double valor = pl.getConfig().getDouble("seguros.valores." + tipo, VALORES.getOrDefault(tipo, 100.0));
            AttributeInstance vidaMax = victima.getAttribute(Attribute.MAX_HEALTH);
            double vida = vidaMax == null ? 20 : vidaMax.getValue();
            importe = mortal ? valor : valor * Math.min(1, dano / vida);
        }
        if (dueno.equals(conductor.getUniqueId())) return;
        importe = Math.round(importe);
        if (importe <= 0) return;

        String aQuien = victima instanceof Player ? "a " + nombreDueno : "un/a " + nombreBicho(victima) + " de " + nombreDueno;
        boolean asegurado = tipoVigente(estado.matricula) != null;
        OfflinePlayer perjudicado = Bukkit.getOfflinePlayer(dueno);
        // como mucho una indemnización por pareja conductor-perjudicado cada X minutos, y un tope diario por perjudicado
        long ahora = System.currentTimeMillis();
        String pareja = conductor.getUniqueId() + ">" + dueno;
        long espera = pl.getConfig().getLong("seguros.indemnizacion.espera-minutos", 10) * 60_000L;
        Long ultima = ultimaIndemnizacion.get(pareja);
        if (ultima != null && ahora - ultima < espera) {
            conductor.sendMessage(ChatColor.RED + "¡Has atropellado " + aQuien + "! " + ChatColor.GRAY + "(Ya hubo un parte entre vosotros hace poco: este no se indemniza.)");
            return;
        }
        String dia = java.time.LocalDate.now(java.time.ZoneId.of(pl.getConfig().getString("zona-horaria", "Europe/Madrid"))).toString();
        String claveDia = "indemnizado-hoy." + dueno;
        if (!dia.equals(y.getString(claveDia + ".dia"))) { y.set(claveDia + ".dia", dia); y.set(claveDia + ".importe", 0.0); }
        double tope = pl.getConfig().getDouble("seguros.indemnizacion.tope-diario", 2000);
        importe = Math.min(importe, Math.max(0, tope - y.getDouble(claveDia + ".importe", 0)));
        if (importe <= 0) {
            conductor.sendMessage(ChatColor.RED + "¡Has atropellado " + aQuien + "! " + ChatColor.GRAY + "(" + nombreDueno + " ya ha cobrado hoy todo lo que cubre el seguro.)");
            return;
        }
        ultimaIndemnizacion.put(pareja, ahora);
        if (asegurado) {
            // primero paga el fondo de la aseguradora; si no llega, el titular de la póliza pone el resto (lo que pueda)
            double delFondo = pl.eco == null ? 0 : sacarFondo(importe);
            double resto = importe - delFondo;
            double delTitular = 0;
            String titularTxt = y.getString("polizas." + k(estado.matricula) + ".titular");
            if (resto > 0 && pl.eco != null && titularTxt != null) {
                OfflinePlayer titular = Bukkit.getOfflinePlayer(UUID.fromString(titularTxt));
                delTitular = Math.min(resto, Math.max(0, pl.eco.getBalance(titular)));
                if (delTitular > 0) pl.eco.withdrawPlayer(titular, delTitular);
            }
            importe = Math.round((delFondo + delTitular) * 100.0) / 100.0;
            if (pl.eco != null && importe > 0) pl.eco.depositPlayer(perjudicado, importe);
            conductor.sendMessage(ChatColor.RED + "¡Has atropellado " + aQuien + (mortal ? " (mortal)" : "") + "! " + ChatColor.GRAY
                    + compania(tipo(estado.matricula)) + String.format(Locale.ROOT, " indemniza con %,.2f Ⓜ. Tu seguro subirá.", importe)
                    + (delTitular > 0 ? String.format(Locale.ROOT, " (%,.2f Ⓜ los pone el titular: el fondo de la aseguradora no llegaba)", delTitular) : ""));
        } else {
            // Sin seguro paga el conductor lo que pueda
            double pagado = pl.eco == null ? 0 : Math.min(importe, Math.max(0, pl.eco.getBalance(conductor)));
            if (pl.eco != null && pagado > 0) { pl.eco.withdrawPlayer(conductor, pagado); pl.eco.depositPlayer(perjudicado, pagado); }
            conductor.sendMessage(ChatColor.DARK_RED + "¡Has atropellado " + aQuien + " SIN SEGURO EN VIGOR! " + ChatColor.RED
                    + String.format(Locale.ROOT, "Pagas tú %,.2f Ⓜ.", pagado));
            importe = pagado;
        }
        y.set(claveDia + ".importe", y.getDouble(claveDia + ".importe", 0) + importe);
        avisar(dueno, ChatColor.GOLD + conductor.getName() + " (" + estado.matricula + ") ha atropellado "
                + (victima instanceof Player ? "te" : "a tu " + nombreBicho(victima)) + ". "
                + ChatColor.GREEN + String.format(Locale.ROOT, "Recibes %,.2f Ⓜ de indemnización.", importe));

        String base = "conductores." + conductor.getUniqueId();
        y.set(base + ".nivel", Math.min(4, malus(conductor.getUniqueId()) + 1));
        y.set(base + ".ultimo-parte", System.currentTimeMillis());
        List<String> partes = new ArrayList<>(y.getStringList("partes"));
        partes.add(FECHA.format(Instant.now().atZone(ZoneId.of("Europe/Madrid"))) + " · " + conductor.getName() + " (" + estado.matricula + ") → "
                + (victima instanceof Player ? nombreDueno : nombreBicho(victima) + " de " + nombreDueno) + (mortal ? " [mortal]" : "")
                + String.format(Locale.ROOT, " · %,.2f Ⓜ", importe) + (asegurado ? "" : " · sin seguro"));
        if (partes.size() > 200) partes = partes.subList(partes.size() - 200, partes.size());
        y.set("partes", partes);
        guardar();
    }

    private static String nombreBicho(LivingEntity bicho) {
        return switch (bicho.getType()) {
            case COW -> "vaca"; case SHEEP -> "oveja"; case PIG -> "cerdo"; case CHICKEN -> "gallina"; case HORSE -> "caballo";
            case DONKEY -> "burro"; case MULE -> "mula"; case WOLF -> "perro"; case CAT -> "gato"; case VILLAGER -> "aldeano";
            case LLAMA -> "llama"; case GOAT -> "cabra"; case RABBIT -> "conejo"; case PARROT -> "loro"; case CAMEL -> "camello";
            case IRON_GOLEM -> "gólem"; case FOX -> "zorro"; default -> bicho.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    // Últimos partes de atropello (para /coche partes)
    List<String> partes() {
        return y.getStringList("partes");
    }

    // ---------- Averías ----------

    public void alExplotar(EntityExplodeEvent e) {
        Location centro = e.getLocation();
        for (Entity ent : centro.getWorld().getNearbyEntities(centro, 5, 5, 5)) {
            if (!pl.conduccion.esCoche(ent)) continue;
            double cerca = Math.max(0, 1 - ent.getLocation().distance(centro) / 5);
            pl.conduccion.danarCoche((ArmorStand) ent, 35 * cerca, "una explosión");
        }
    }

    // Repara el coche guardado que lleva en la mano; el seguro paga una parte
    void reparar(Player jugador) {
        ItemStack mano = jugador.getInventory().getItemInMainHand();
        PersistentDataContainer datos = mano.hasItemMeta() ? mano.getItemMeta().getPersistentDataContainer() : null;
        String matricula = datos == null ? null : datos.get(pl.kMatricula, PersistentDataType.STRING);
        if (mano.getType() != Material.MINECART || matricula == null) { jugador.sendMessage(ChatColor.RED + "Guarda el coche y ponlo en la mano para llevarlo al taller."); return; }
        Tier tier = pl.tiers.get(datos.get(pl.kTier, PersistentDataType.STRING));
        if (tier == null) return;
        Double guardado = datos.get(pl.kEstado, PersistentDataType.DOUBLE);
        double estado = guardado == null ? 100 : guardado;
        if (estado >= 99.5) { jugador.sendMessage(ChatColor.GREEN + "El coche está perfecto."); return; }
        double coste = (100 - estado) / 100 * tier.precio() * pl.getConfig().getDouble("averias.coste-reparacion", 0.15);
        String tipo = tipoVigente(matricula);
        double cubre = tipo == null ? 0 : switch (tipo) { case "ampliado" -> 0.5; case "todoriesgo" -> 1; default -> 0; };
        double pagas = Math.round(coste * (1 - cubre));
        if (pagas > 0 && (pl.eco == null || !pl.eco.has(jugador, pagas))) {
            jugador.sendMessage(ChatColor.RED + String.format(Locale.ROOT, "La reparación te cuesta %,.2f Ⓜ y no te llega.", pagas));
            return;
        }
        if (pagas > 0) pl.eco.withdrawPlayer(jugador, pagas);
        Double gasolina = datos.get(pl.kGasolina, PersistentDataType.DOUBLE);
        jugador.getInventory().setItemInMainHand(pl.copiarAspecto(mano, pl.itemCoche(tier, matricula, gasolina == null ? 0 : gasolina, 100)));
        jugador.playSound(jugador.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        jugador.sendMessage(ChatColor.GREEN + "🔧 Coche reparado al 100 %. " + ChatColor.GRAY + String.format(Locale.ROOT, "Coste %,.2f Ⓜ", (double) Math.round(coste))
                + (cubre > 0 ? String.format(Locale.ROOT, ", el seguro paga %.0f %%: pagas %,.2f Ⓜ.", cubre * 100, pagas) : String.format(Locale.ROOT, ": pagas %,.2f Ⓜ.", pagas)));
    }

    // ---------- Siniestro total ----------

    void siniestro(ArmorStand coche, Tier tier, String matricula, Player conductor) {
        String tipo = tipoVigente(matricula);
        UUID titular = conductor != null ? conductor.getUniqueId() : titularDe(matricula);
        coche.getWorld().spawnParticle(Particle.LARGE_SMOKE, coche.getLocation().add(0, 0.5, 0), 30, 0.4, 0.4, 0.4, 0.02);
        coche.getWorld().playSound(coche.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8f, 1.4f);
        coche.eject();
        pl.carga.soltar(coche, coche.getLocation().add(0, 0.5, 0));
        pl.conduccion.conduciendo.remove(coche.getUniqueId());
        pl.modelos.desmontar(coche.getUniqueId());
        coche.remove();
        if (titular == null) return;
        if ("todoriesgo".equals(tipo)) {
            y.set("siniestros." + k(matricula), tier.id());
            guardar();
            avisar(titular, ChatColor.RED + "Siniestro total del " + tier.nombre() + " " + matricula + ". " + ChatColor.GREEN + compania(tipo)
                    + String.format(Locale.ROOT, " te da uno nuevo pagando la franquicia (%,.2f Ⓜ): /coche siniestro %s", tier.precio() * 0.2, matricula.replace(" ", "")));
        } else {
            avisar(titular, ChatColor.DARK_RED + "Siniestro total del " + tier.nombre() + " " + matricula + ". Tu seguro no lo cubre: has perdido el coche.");
        }
    }

    private UUID titularDe(String matricula) {
        String id = y.getString("polizas." + k(matricula) + ".titular");
        return id == null ? null : UUID.fromString(id);
    }

    void reclamarSiniestro(Player jugador, String[] args) {
        String matricula = args.length > 1 ? Registro.normalizar(String.join("", Arrays.copyOfRange(args, 1, args.length))) : null;
        if (matricula == null || !y.isSet("siniestros." + k(matricula))) { jugador.sendMessage(ChatColor.YELLOW + "No tienes ningún siniestro pendiente con esa matrícula."); return; }
        if (!Extra.llaveYDueno(pl, jugador, matricula)) { jugador.sendMessage(ChatColor.RED + "Necesitas la llave de " + matricula + "."); return; }
        Tier tier = pl.tiers.get(y.getString("siniestros." + k(matricula)));
        if (tier == null) return;
        double franquicia = Math.round(tier.precio() * pl.getConfig().getDouble("seguros.franquicia-siniestro", 0.2));
        if (pl.eco == null || !pl.eco.has(jugador, franquicia)) {
            jugador.sendMessage(ChatColor.RED + String.format(Locale.ROOT, "La franquicia es de %,.2f Ⓜ y no te llega.", franquicia));
            return;
        }
        pl.eco.withdrawPlayer(jugador, franquicia);
        y.set("siniestros." + k(matricula), null);
        guardar();
        pl.dar(jugador, pl.itemCoche(tier, matricula, 10, 100));
        jugador.sendMessage(ChatColor.GREEN + "Aquí tienes tu " + tier.nombre() + " nuevo (" + matricula + "), con 10 L. Tu llave de siempre sigue valiendo.");
    }

    // ---------- Grúa ----------

    void grua(Player jugador, String[] args) {
        String matricula = args.length > 1 ? Registro.normalizar(String.join("", Arrays.copyOfRange(args, 1, args.length))) : null;
        if (matricula == null) { jugador.sendMessage(ChatColor.YELLOW + "Uso: /coche grua <matrícula>  (te trae el coche guardado)"); return; }
        if (!pl.tieneLlave(jugador, matricula)) { jugador.sendMessage(ChatColor.RED + "Necesitas la llave de " + matricula + "."); return; }
        Tier delSeguro = pl.tiers.get(y.getString("polizas." + k(matricula) + ".tier", ""));
        if (delSeguro != null && delSeguro.agua()) {
            jugador.sendMessage(ChatColor.YELLOW + "Las embarcaciones no tienen grúa: ve hasta ella, guárdala (mayúsculas + golpe) y llévatela.");
            return;
        }
        String tipo = tipoVigente(matricula);
        long semana = System.currentTimeMillis() / SEMANA;
        // Todo riesgo: siempre gratis; ampliado: una gratis por semana
        boolean gratis = "todoriesgo".equals(tipo) || "ampliado".equals(tipo) && y.getLong("grua." + k(matricula), -1) != semana;
        double precio = gratis ? 0 : pl.getConfig().getDouble("seguros.precio-grua", 300);
        if (precio > 0 && (pl.eco == null || !pl.eco.has(jugador, precio))) {
            jugador.sendMessage(ChatColor.RED + String.format(Locale.ROOT, "La grúa cuesta %,.2f Ⓜ y no te llega.", precio));
            return;
        }
        String ubicacion = y.getString("ubicacion." + k(matricula));
        ArmorStand cargado = buscarCargado(matricula);
        if (cargado != null) { entregarGrua(jugador, cargado, matricula, tipo, gratis, precio, semana); return; }
        if (ubicacion == null) { jugador.sendMessage(ChatColor.RED + "No sé dónde está ese coche."); return; }
        String[] partes = ubicacion.split(";");
        World mundo = Bukkit.getWorld(partes[0]);
        if (mundo == null) { jugador.sendMessage(ChatColor.RED + "No encuentro el mundo del coche."); return; }
        Location sitio = new Location(mundo, Double.parseDouble(partes[1]), Double.parseDouble(partes[2]), Double.parseDouble(partes[3]));
        jugador.sendMessage(ChatColor.GRAY + "Llamando a la grúa…");
        mundo.getChunkAtAsync(sitio).thenAccept(chunk -> {
            chunk.addPluginChunkTicket(pl);
            buscarEnChunk(jugador, chunk, matricula, tipo, gratis, precio, semana, 0);
        });
    }

    // Espera a que el coche aparezca en el chunk cargado (hasta 5 intentos de 1 s)
    private void buscarEnChunk(Player jugador, Chunk chunk, String matricula, String tipo, boolean gratis, double precio, long semana, int intento) {
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            ArmorStand coche = buscarCargado(matricula);
            if (coche != null) { chunk.removePluginChunkTicket(pl); entregarGrua(jugador, coche, matricula, tipo, gratis, precio, semana); return; }
            if (intento < 5) { buscarEnChunk(jugador, chunk, matricula, tipo, gratis, precio, semana, intento + 1); return; }
            chunk.removePluginChunkTicket(pl);
            jugador.sendMessage(ChatColor.RED + "La grúa no ha encontrado el coche donde lo dejaste.");
        }, 20L);
    }

    private ArmorStand buscarCargado(String matricula) {
        for (World mundo : Bukkit.getWorlds())
            for (ArmorStand coche : mundo.getEntitiesByClass(ArmorStand.class))
                if (matricula.equals(coche.getPersistentDataContainer().get(pl.kMatricula, PersistentDataType.STRING))) return coche;
        return null;
    }

    private void entregarGrua(Player jugador, ArmorStand coche, String matricula, String tipo, boolean gratis, double precio, long semana) {
        if (!jugador.isOnline()) return;
        if (!coche.getPassengers().isEmpty()) { jugador.sendMessage(ChatColor.RED + "Hay alguien dentro del coche."); return; }
        if (precio > 0) { if (!pl.eco.has(jugador, precio)) return; pl.eco.withdrawPlayer(jugador, precio); }
        if ("ampliado".equals(tipo) && gratis) { y.set("grua." + k(matricula), semana); guardar(); }
        Tier tier = pl.conduccion.tierDe(coche);
        if (tier == null) { jugador.sendMessage(ChatColor.RED + "Ese tipo de coche ya no existe en la configuración."); return; }
        // Con carga dentro no se puede guardar: la grúa deja el coche al lado
        if (tier.carga() > 0 && !pl.carga.vacia(coche)) {
            pl.modelos.desmontar(coche.getUniqueId());
            coche.teleport(jugador.getLocation());
            pl.modelos.montar(coche);
            apuntarUbicacion(matricula, coche.getLocation());
            jugador.sendMessage(ChatColor.GREEN + "🚚 La grúa te ha dejado la " + tier.nombre() + " " + matricula + " al lado, con la carga dentro" + ChatColor.GRAY
                    + (gratis ? " (gratis con tu seguro)." : String.format(Locale.ROOT, " (%,.2f Ⓜ).", precio)));
            return;
        }
        pl.dar(jugador, pl.conAspecto(pl.itemCoche(tier, matricula, pl.conduccion.gasolinaDe(coche), pl.conduccion.estadoDe(coche)),
                coche.getPersistentDataContainer().get(pl.kAspecto, PersistentDataType.STRING)));
        pl.modelos.desmontar(coche.getUniqueId());
        coche.remove();
        jugador.playSound(jugador.getLocation(), Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1, 0.8f);
        jugador.sendMessage(ChatColor.GREEN + "🚚 La grúa te ha traído el " + tier.nombre() + " " + matricula + ChatColor.GRAY
                + (gratis ? " (gratis con tu seguro)." : String.format(Locale.ROOT, " (%,.2f Ⓜ).", precio)));
    }

    // ---------- Mantenimiento de datos ----------

    void anular(String matricula) {
        for (String seccion : List.of("polizas", "grua", "asistencia", "siniestros")) y.set(seccion + "." + k(matricula), null);
        guardar();
    }

    void reiniciar() {
        for (String seccion : List.of("polizas", "ubicacion", "grua", "asistencia", "siniestros", "partes", "avisos", "conductores")) y.set(seccion, null);
        guardar();
    }

    // Pasa todo lo de una matrícula a otra (cambio de matrícula)
    void moverPoliza(String vieja, String nueva) {
        for (String seccion : List.of("polizas", "ubicacion", "grua", "asistencia", "siniestros")) {
            Object valor = y.get(seccion + "." + k(vieja));
            if (valor == null) continue;
            if (valor instanceof ConfigurationSection sub) y.createSection(seccion + "." + k(nueva), sub.getValues(true));
            else y.set(seccion + "." + k(nueva), valor);
            y.set(seccion + "." + k(vieja), null);
        }
        guardar();
    }

    // Última posición conocida de un coche aparcado ("mundo;x;y;z")
    Location ubicacion(String matricula) {
        String texto = y.getString("ubicacion." + k(matricula));
        if (texto == null) return null;
        String[] partes = texto.split(";");
        World mundo = Bukkit.getWorld(partes[0]);
        return mundo == null ? null : new Location(mundo, Double.parseDouble(partes[1]), Double.parseDouble(partes[2]), Double.parseDouble(partes[3]));
    }

    void apuntarUbicacion(String matricula, Location sitio) {
        y.set("ubicacion." + k(matricula), sitio.getWorld().getName() + ";" + sitio.getX() + ";" + sitio.getY() + ";" + sitio.getZ());
        guardar();
    }

    // ---------- Asistencia en carretera: unos litros gratis una vez al día ----------

    void asistencia(Player jugador) {
        Entity vehiculo = jugador.getVehicle();
        Conduccion.Estado estado = vehiculo == null ? null : pl.conduccion.conduciendo.get(vehiculo.getUniqueId());
        if (estado == null) { jugador.sendMessage(ChatColor.YELLOW + "Tienes que estar dentro del coche."); return; }
        String tipo = tipoVigente(estado.matricula);
        double litros = tipo == null ? 0 : switch (tipo) { case "ampliado" -> 5; case "todoriesgo" -> 10; default -> 0; };
        if (litros == 0) { jugador.sendMessage(ChatColor.RED + "Tu seguro no incluye asistencia en carretera."); return; }
        if (estado.gasolina > 1) { jugador.sendMessage(ChatColor.YELLOW + "Todavía te queda gasolina."); return; }
        long dia = System.currentTimeMillis() / 86_400_000L;
        if (y.getLong("asistencia." + k(estado.matricula), -1) == dia) { jugador.sendMessage(ChatColor.RED + "Ya has usado la asistencia hoy."); return; }
        y.set("asistencia." + k(estado.matricula), dia);
        guardar();
        estado.gasolina = Math.min(estado.tier.deposito(), estado.gasolina + litros);
        pl.conduccion.guardar(estado);
        jugador.sendMessage(ChatColor.GREEN + "🛠 Asistencia de " + compania(tipo) + ": te echan " + (int) litros + " L para llegar a una gasolinera.");
    }
}
