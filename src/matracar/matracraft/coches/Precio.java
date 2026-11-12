package matracraft.coches;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.net.ssl.SSLParameters;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Precio real de la gasolina 95 (Ministerio), guardado en precio.yml. */
final class Precio {

    private final Coches plugin;
    private final File archivo;
    private volatile double euros;
    private volatile String fecha = "sin datos";
    private volatile int estaciones = 0;
    private volatile boolean real = false;
    private volatile List<String> nombresCercanos = List.of();
    private volatile long actualizado = 0L;

    Precio(Coches plugin) {
        this.plugin = plugin;
        this.archivo = new File(plugin.getDataFolder(), "precio.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(archivo);
        this.euros = yml.getDouble("euros", plugin.getConfig().getDouble("gasolina.precio-por-defecto-euros", 1.55));
        this.fecha = yml.getString("fecha", "sin datos");
        this.estaciones = yml.getInt("estaciones", 0);
        this.real = yml.getBoolean("real", false);
        this.nombresCercanos = yml.getStringList("nombres-cercanos");
        this.actualizado = yml.getLong("actualizado", 0L);
    }

    boolean tocaActualizar() {
        return System.currentTimeMillis() - actualizado > 6 * 60 * 60 * 1000L;
    }

    boolean esDeHoy() {
        return LocalDate.now().toString().equals(fecha);
    }

    List<String> nombresCercanos() { return nombresCercanos; }

    double euros() { return euros; }
    String fecha() { return fecha; }
    int estaciones() { return estaciones; }
    boolean esReal() { return real; }

    /** Precio en la moneda del servidor, redondeado a céntimos. */
    double dolares() {
        double d = euros * plugin.getConfig().getDouble("gasolina.euros-a-dolares", 2.0);
        return Math.round(d * 100.0) / 100.0;
    }

    /** Descarga y recalcula el precio. Se llama fuera del hilo principal. */
    boolean actualizar() {
        String provincia = plugin.getConfig().getString("gasolina.provincia", "41");
        String url = "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/FiltroProvincia/" + provincia;
        String json = null;
        Exception ultimoError = null;
        // dos intentos con HttpClient y el último con HttpURLConnection
        for (int intento = 1; intento <= 3 && json == null; intento++) {
            try {
                json = intento < 3 ? descargarHttpClient(url) : descargarUrlConnection(url);
            } catch (Exception e) {
                ultimoError = e;
                try { Thread.sleep(intento * 5000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        if (json == null) {
            plugin.getLogger().warning("No he podido leer el precio real de la gasolina (" + detalle(ultimoError)
                    + "). Sigo con el ultimo conocido: " + euros + " EUR/L del " + fecha + ". Lo vuelvo a intentar en 30 min.");
            return false;
        }
        try {
            double lat = plugin.getConfig().getDouble("gasolina.latitud", 37.586);
            double lon = plugin.getConfig().getDouble("gasolina.longitud", -5.966);
            double[] r = calcular(json, lat, lon, plugin.getConfig().getDouble("gasolina.radio-km", 12.0));
            if (r == null) throw new RuntimeException("no hay precios de gasolina 95 en la respuesta");
            List<String> nombres = nombres(json, lat, lon, plugin.getConfig().getDouble("gasolina.radio-nombres-km", 20.0));
            if (!nombres.isEmpty()) nombresCercanos = nombres;
            euros = Math.round(r[0] * 1000.0) / 1000.0;
            estaciones = (int) r[1];
            fecha = LocalDate.now().toString();
            real = true;
            actualizado = System.currentTimeMillis();
            YamlConfiguration yml = new YamlConfiguration();
            yml.set("euros", euros);
            yml.set("fecha", fecha);
            yml.set("estaciones", estaciones);
            yml.set("real", true);
            yml.set("actualizado", actualizado);
            yml.set("nombres-cercanos", nombresCercanos);
            yml.save(archivo);
            plugin.getLogger().info(String.format(Locale.ROOT,
                    "Precio real de la gasolina 95: %.3f EUR/L (media de %d gasolineras) -> %.2f Ⓜ/L",
                    euros, estaciones, dolares()));
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("La respuesta del Ministerio no se ha podido leer (" + detalle(e) + "). Sigo con " + euros + " EUR/L.");
            return false;
        }
    }

    private static String descargarHttpClient(String url) throws Exception {
        SSLParameters ssl = new SSLParameters();
        ssl.setProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        HttpClient cliente = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .sslParameters(ssl)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json").header("User-Agent", "Mozilla/5.0 (Matracar)").GET().build();
        HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (respuesta.statusCode() != 200) throw new RuntimeException("HTTP " + respuesta.statusCode());
        return respuesta.body();
    }

    private static String descargarUrlConnection(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(60000);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("Accept-Encoding", "gzip");
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Matracar)");
        try {
            if (con.getResponseCode() != 200) throw new RuntimeException("HTTP " + con.getResponseCode());
            InputStream in = con.getInputStream();
            if ("gzip".equalsIgnoreCase(con.getContentEncoding())) in = new GZIPInputStream(in);
            try (InputStream entrada = in) { return new String(entrada.readAllBytes(), StandardCharsets.UTF_8); }
        } finally { con.disconnect(); }
    }

    private static String detalle(Throwable t) {
        if (t == null) return "sin detalle";
        StringBuilder sb = new StringBuilder(t.getClass().getSimpleName() + ": " + t.getMessage());
        Throwable causa = t.getCause();
        if (causa != null && causa != t) sb.append(" / ").append(causa.getClass().getSimpleName()).append(": ").append(causa.getMessage());
        return sb.toString();
    }

    /** {media, número de gasolineras}: primero las del radio; si no hay, la media de la provincia. */
    static double[] calcular(String json, double lat, double lon, double radioKm) {
        JsonObject raiz = JsonParser.parseString(json).getAsJsonObject();
        JsonArray lista = raiz.getAsJsonArray("ListaEESSPrecio");
        if (lista == null) return null;
        double suma = 0, sumaTodas = 0;
        int n = 0, nTodas = 0;
        for (JsonElement el : lista) {
            JsonObject o = el.getAsJsonObject();
            Double precio = null, la = null, lo = null;
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                String k = e.getKey();
                if (k.contains("Gasolina 95") && k.contains("E5") && !k.contains("Premium")) precio = num(e.getValue());
                else if (k.equals("Latitud")) la = num(e.getValue());
                else if (k.startsWith("Longitud")) lo = num(e.getValue());
            }
            if (precio == null || precio <= 0.3 || precio > 5.0) continue;
            sumaTodas += precio; nTodas++;
            if (la != null && lo != null && distanciaKm(lat, lon, la, lo) <= radioKm) { suma += precio; n++; }
        }
        if (n > 0) return new double[]{suma / n, n};
        if (nTodas > 0) return new double[]{sumaTodas / nTodas, nTodas};
        return null;
    }

    /** Nombres "Rótulo · Municipio" de las gasolineras dentro del radio. */
    static List<String> nombres(String json, double lat, double lon, double radioKm) {
        LinkedHashSet<String> salida = new LinkedHashSet<>();
        JsonArray lista = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("ListaEESSPrecio");
        if (lista == null) return new ArrayList<>();
        for (JsonElement el : lista) {
            JsonObject o = el.getAsJsonObject();
            String rotulo = null, municipio = null; Double la = null, lo = null;
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                String k = e.getKey();
                if (k.contains("tulo")) rotulo = texto(e.getValue());
                else if (k.equals("Municipio")) municipio = texto(e.getValue());
                else if (k.equals("Latitud")) la = num(e.getValue());
                else if (k.startsWith("Longitud")) lo = num(e.getValue());
            }
            if (rotulo == null || rotulo.isBlank() || municipio == null || la == null || lo == null) continue;
            if (distanciaKm(lat, lon, la, lo) > radioKm) continue;
            salida.add(bonito(rotulo) + " · " + bonito(municipio));
        }
        return new ArrayList<>(salida);
    }

    private static String texto(JsonElement e) {
        return e == null || e.isJsonNull() ? null : e.getAsString().trim();
    }

    /** "ALCALA DEL RIO" -> "Alcala del Rio"; las siglas cortas (BP) se quedan igual. */
    static String bonito(String s) {
        StringBuilder sb = new StringBuilder();
        for (String palabra : s.trim().split("\\s+")) {
            if (palabra.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            if (palabra.length() <= 3 && palabra.chars().allMatch(Character::isLetter) && palabra.equals(palabra.toUpperCase(Locale.ROOT))
                    && !List.of("DE", "DEL", "LA", "LAS", "LOS", "EL", "Y").contains(palabra)) { sb.append(palabra); continue; }
            if (List.of("DE", "DEL", "LA", "LAS", "LOS", "EL", "Y").contains(palabra.toUpperCase(Locale.ROOT)) && sb.length() > 0) {
                sb.append(palabra.toLowerCase(Locale.ROOT));
            } else {
                sb.append(palabra.substring(0, 1).toUpperCase(Locale.ROOT)).append(palabra.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    private static Double num(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        String s = e.getAsString().trim().replace(',', '.');
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return null; }
    }

    /** Distancia en km (haversine). */
    static double distanciaKm(double lat1, double lon1, double lat2, double lon2) {
        double radioTierra = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * radioTierra * Math.asin(Math.sqrt(a));
    }
}
