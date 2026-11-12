package matracraft.matrains;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

/**
 * Combustible de las locomotoras: carbón en el ténder (vapor), gasóleo a precio real de las
 * gasolineras de MATRAMOTORS (diésel) y luz de la red a precio real del PVPC de Red Eléctrica
 * (eléctrica, alta velocidad y maglev). La luz la paga el dueño del tren según los bloques que recorre.
 */
final class Energia {

    private final Matrains pl;
    /** Precio de la luz en euros por kWh (PVPC de esta hora). */
    double precioLuz;
    String fechaLuz = "";
    private double gasoleoEuros = 1.55;
    private final Set<String> surtidores = new HashSet<>();
    private long leidoGasolina;
    private File archivo;

    Energia(Matrains pl) { this.pl = pl; }

    void iniciar() {
        archivo = new File(pl.getDataFolder(), "luz.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        precioLuz = y.getDouble("euros-kwh", pl.getConfig().getDouble("combustible.luz-precio-defecto", 0.15));
        fechaLuz = y.getString("fecha", "");
        Bukkit.getScheduler().runTaskTimerAsynchronously(pl, this::consultarLuz, 40L, 20L * 60 * 60);
    }

    // ------------------------------------------------------------------ precios

    /** PVPC de Red Eléctrica (apidatos.ree.es, sin registro), precio de la hora en curso. */
    private void consultarLuz() {
        try {
            LocalDate hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));
            String url = "https://apidatos.ree.es/es/datos/mercados/precios-mercados-tiempo-real?start_date=" + hoy + "T00:00&end_date="
                    + hoy + "T23:59&time_trunc=hour";
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> r = c.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) throw new IOException("respuesta " + r.statusCode());
            JsonArray incluidos = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("included");
            JsonArray valores = null;
            for (JsonElement e : incluidos) {
                JsonObject o = e.getAsJsonObject();
                String titulo = o.getAsJsonObject("attributes").get("title").getAsString();
                if (titulo.toUpperCase().contains("PVPC")) { valores = o.getAsJsonObject("attributes").getAsJsonArray("values"); break; }
            }
            if (valores == null && !incluidos.isEmpty()) valores = incluidos.get(0).getAsJsonObject().getAsJsonObject("attributes").getAsJsonArray("values");
            if (valores == null || valores.isEmpty()) throw new IOException("sin datos");
            int hora = LocalDateTime.now(ZoneId.of("Europe/Madrid")).getHour();
            double mwh = valores.get(Math.min(hora, valores.size() - 1)).getAsJsonObject().get("value").getAsDouble();
            double kwh = mwh / 1000.0;
            if (kwh <= 0 || kwh > 2) throw new IOException("precio raro " + kwh);
            Bukkit.getScheduler().runTask(pl, () -> {
                precioLuz = kwh;
                fechaLuz = hoy + " " + hora + ":00";
                YamlConfiguration y = new YamlConfiguration();
                y.set("euros-kwh", kwh);
                y.set("fecha", fechaLuz);
                try { y.save(archivo); } catch (IOException ignorado) { }
            });
        } catch (Exception ex) {
            pl.getLogger().info("No se pudo leer el precio de la luz (" + ex.getMessage() + "); sigue el último: " + String.format("%.3f €/kWh", precioLuz));
        }
    }

    /** Gasóleo: el precio y los surtidores de MATRAMOTORS (se releen cada 5 minutos). */
    private void leerGasolina() {
        if (System.currentTimeMillis() - leidoGasolina < 300_000) return;
        leidoGasolina = System.currentTimeMillis();
        var mm = Bukkit.getPluginManager().getPlugin("MATRAMOTORS");
        if (mm == null) return;
        YamlConfiguration precio = YamlConfiguration.loadConfiguration(new File(mm.getDataFolder(), "precio.yml"));
        gasoleoEuros = precio.getDouble("euros", gasoleoEuros);
        YamlConfiguration g = YamlConfiguration.loadConfiguration(new File(mm.getDataFolder(), "gasolineras.yml"));
        surtidores.clear();
        ConfigurationSection s = g.getConfigurationSection("surtidores");
        if (s != null) surtidores.addAll(s.getKeys(false));
    }

    double cambio() { return pl.getConfig().getDouble("combustible.euros-a-dolares", 2.0); }

    double maximo(Tipos.Loco loco) {
        return switch (loco.combustible()) {
            case "carbon" -> pl.getConfig().getDouble("combustible.carbon-max", 64);
            case "gasoleo" -> pl.getConfig().getDouble("combustible.gasoleo-litros", 300);
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ en marcha

    /** ¿Puede tirar la locomotora? (con combustible, o con luz en la vía y saldo para pagarla) */
    boolean puedeTirar(Tren t, Tipos.Loco loco) {
        Tren.Coche c = t.locomotora();
        return switch (loco.combustible()) {
            case "carbon", "gasoleo" -> c.combustible > 0;
            default -> {
                Block b = c.delante.bloque();
                yield b != null && pl.red.hayLuz(b) && !t.sinSaldo;
            }
        };
    }

    String motivo(Tren t, Tipos.Loco loco) {
        return switch (loco.combustible()) {
            case "carbon" -> "Sin carbón: clic derecho en la locomotora con carbón";
            case "gasoleo" -> "Sin gasóleo: mayúsculas + clic en la locomotora junto a una gasolinera";
            default -> t.sinSaldo ? "Sin dinero para pagar la luz" : "Esta vía no tiene luz";
        };
    }

    String indicador(Tren t, Tipos.Loco loco) {
        Tren.Coche c = t.locomotora();
        return switch (loco.combustible()) {
            case "carbon" -> String.format("carbón %.0f", Math.ceil(c.combustible));
            case "gasoleo" -> String.format("gasóleo %.0f L", c.combustible);
            default -> puedeTirar(t, loco) ? "⚡ con luz" : "sin luz";
        };
    }

    void consumir(Tren t, Tipos.Loco loco, double bloques) {
        Tren.Coche c = t.locomotora();
        switch (loco.combustible()) {
            case "carbon" -> c.combustible = Math.max(0, c.combustible - bloques / pl.getConfig().getDouble("combustible.carbon-bloques", 40));
            case "gasoleo" -> c.combustible = Math.max(0, c.combustible - bloques * pl.getConfig().getDouble("combustible.gasoleo-por-bloque", 0.02));
            default -> {
                double kwh = pl.getConfig().getDouble("combustible.luz-kwh-por-bloque." + loco.id(), 0.05);
                t.deudaLuz += bloques * kwh * precioLuz * cambio();
                if (t.deudaLuz >= 1) cobrarLuz(t);
            }
        }
    }

    private void cobrarLuz(Tren t) {
        if (pl.eco == null) { t.deudaLuz = 0; return; }
        OfflinePlayer d = Bukkit.getOfflinePlayer(t.dueno);
        double cuanto = Math.floor(t.deudaLuz * 100) / 100;
        if (pl.eco.has(d, cuanto)) {
            pl.eco.withdrawPlayer(d, cuanto);
            t.deudaLuz -= cuanto;
            t.facturaLuz += cuanto;
            t.sinSaldo = false;
        } else t.sinSaldo = true;
    }

    // ------------------------------------------------------------------ repostar

    /** Clic con carbón en una de vapor, o mayúsculas + clic en una diésel junto a una gasolinera. */
    boolean repostar(Player p, Tren t, Tipos.Loco loco, boolean agachado) {
        Tren.Coche c = t.locomotora();
        if (loco.combustible().equals("carbon")) {
            ItemStack mano = p.getInventory().getItemInMainHand();
            int vale = switch (mano.getType()) { case COAL, CHARCOAL -> 1; case COAL_BLOCK -> 9; default -> 0; };
            if (vale == 0) return false;
            double max = maximo(loco);
            if (c.combustible >= max - 0.5) { p.sendMessage(ChatColor.GRAY + "El ténder está lleno."); return true; }
            int piezas = 0;
            while (piezas < mano.getAmount() && c.combustible + vale <= max + 0.01) { c.combustible += vale; piezas++; }
            if (piezas == 0) { c.combustible = max; piezas = 1; }
            mano.setAmount(mano.getAmount() - piezas);
            p.playSound(p.getLocation(), Sound.BLOCK_GRAVEL_PLACE, SoundCategory.PLAYERS, 1f, 0.8f);
            p.sendMessage(ChatColor.GREEN + "Ténder: " + (int) Math.ceil(c.combustible) + " / " + (int) max + " de carbón.");
            return true;
        }
        if (loco.combustible().equals("gasoleo")) {
            // bidón de gasolina de MATRAMOTORS (papel marcado coches:bidon con los litros que le quedan)
            ItemStack mano = p.getInventory().getItemInMainHand();
            Double tengo = mano.getType() == org.bukkit.Material.PAPER && mano.hasItemMeta()
                    ? mano.getItemMeta().getPersistentDataContainer().get(K_BIDON, org.bukkit.persistence.PersistentDataType.DOUBLE) : null;
            if (tengo != null) {
                if (tengo < 0.05) { p.sendMessage(ChatColor.RED + "El bidón está vacío. Rellénalo en una gasolinera."); return true; }
                double max = maximo(loco), faltan = max - c.combustible;
                if (faltan < 0.05) { p.sendMessage(ChatColor.GRAY + "El depósito está lleno."); return true; }
                double echa = Math.min(faltan, tengo);
                c.combustible += echa;
                vaciarBidon(p, mano, tengo - echa);
                p.playSound(p.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, SoundCategory.PLAYERS, 0.8f, 1.2f);
                p.sendMessage(ChatColor.GREEN + String.format("⛽ Echados %.1f L. Depósito: %.0f / %.0f L.", echa, c.combustible, max));
                return true;
            }
        }
        if (loco.combustible().equals("gasoleo") && agachado) {
            leerGasolina();
            double radio = pl.getConfig().getDouble("combustible.distancia-surtidor", 10);
            if (!cercaDeSurtidor(p, radio)) { p.sendMessage(ChatColor.RED + "Hace falta una gasolinera a menos de " + (int) radio + " bloques de la vía."); return true; }
            double max = maximo(loco), faltan = max - c.combustible;
            if (faltan < 1) { p.sendMessage(ChatColor.GRAY + "El depósito está lleno."); return true; }
            double litro = gasoleoEuros * cambio();
            OfflinePlayer d = p;
            double saldo = pl.eco == null ? Double.MAX_VALUE : pl.eco.getBalance(d);
            double litros = Math.min(faltan, Math.floor(saldo / litro));
            if (litros < 1) { p.sendMessage(ChatColor.RED + "No te llega para el gasóleo (" + String.format("%.2f", litro) + " Ⓜ/L)."); return true; }
            double coste = Math.round(litros * litro * 100) / 100.0;
            if (pl.eco != null) pl.eco.withdrawPlayer(d, coste);
            c.combustible += litros;
            p.playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL, SoundCategory.PLAYERS, 1f, 0.6f);
            p.sendMessage(ChatColor.GREEN + String.format("Repostados %.0f L de gasóleo por %.2f Ⓜ (%.3f €/L). Depósito: %.0f / %.0f L.",
                    litros, coste, gasoleoEuros, c.combustible, max));
            return true;
        }
        return false;
    }

    private static final org.bukkit.NamespacedKey K_BIDON = org.bukkit.NamespacedKey.fromString("coches:bidon");

    /** Deja el bidón con lo que le queda. Lo de verdad (lore, apilado) lo rehace MATRAMOTORS si está. */
    private void vaciarBidon(Player p, ItemStack mano, double queda) {
        ItemStack uno = mano.clone();
        uno.setAmount(1);
        org.bukkit.plugin.Plugin mm = org.bukkit.Bukkit.getPluginManager().getPlugin("MATRAMOTORS");
        boolean hecho = false;
        if (mm != null && mm.isEnabled()) {
            try {
                java.lang.reflect.Field f = mm.getClass().getDeclaredField("bidones");
                f.setAccessible(true);
                Object bid = f.get(mm);
                java.lang.reflect.Method m = bid.getClass().getDeclaredMethod("escribir", ItemStack.class, double.class);
                m.setAccessible(true);
                m.invoke(bid, uno, queda);
                hecho = true;
            } catch (ReflectiveOperationException | RuntimeException ignorado) { }
        }
        if (!hecho) {
            org.bukkit.inventory.meta.ItemMeta meta = uno.getItemMeta();
            meta.getPersistentDataContainer().set(K_BIDON, org.bukkit.persistence.PersistentDataType.DOUBLE, Math.round(queda * 10) / 10.0);
            meta.setLore(java.util.List.of(ChatColor.WHITE + String.format("%.1f / 10 L", queda)));
            meta.setMaxStackSize(1);
            uno.setItemMeta(meta);
        }
        if (mano.getAmount() > 1) {
            mano.setAmount(mano.getAmount() - 1);
            p.getInventory().addItem(uno).values().forEach(x -> p.getWorld().dropItem(p.getLocation(), x));
        } else p.getInventory().setItemInMainHand(uno);
    }

    private boolean cercaDeSurtidor(Player p, double radio) {
        String mundo = p.getWorld().getName();
        double r2 = radio * radio;
        for (String s : surtidores) {
            String[] k = s.split(";");
            if (k.length < 4 || !k[0].equals(mundo)) continue;
            double dx = Integer.parseInt(k[1]) + 0.5 - p.getLocation().getX(), dz = Integer.parseInt(k[3]) + 0.5 - p.getLocation().getZ();
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }
}
