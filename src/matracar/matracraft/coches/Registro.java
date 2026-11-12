package matracraft.coches;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Registro de matrículas (registro.yml): formato "0000 BCD", contador y bajas. */
final class Registro {
    static final String LETRAS = "BCDFGHJKLMNPRSTVWXYZ";
    private static final Pattern FORMATO = Pattern.compile("^\\d{4} [" + LETRAS + "]{3}$");

    private final Coches pl;
    private final File archivo;
    private final YamlConfiguration y;

    Registro(Coches pl) {
        this.pl = pl;
        this.archivo = new File(pl.getDataFolder(), "registro.yml");
        this.y = YamlConfiguration.loadConfiguration(archivo);
    }

    private void guardar() {
        try { y.save(archivo); } catch (IOException e) { pl.getLogger().warning("No pude guardar registro.yml"); }
    }

    private static String clave(String matricula) { return "matriculas." + matricula.replace(' ', '_'); }

    /** Devuelve la matrícula en formato "0000 BCD" o null si no es válida. */
    static String normalizar(String texto) {
        String limpio = texto.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-Z]", "");
        if (limpio.length() != 7) return null;
        String matricula = limpio.substring(0, 4) + " " + limpio.substring(4);
        return FORMATO.matcher(matricula).matches() ? matricula : null;
    }

    static String formato(long n) {
        long numero = n % 10000, resto = n / 10000;
        char[] letras = new char[3];
        for (int i = 2; i >= 0; i--) { letras[i] = LETRAS.charAt((int) (resto % LETRAS.length())); resto /= LETRAS.length(); }
        return String.format(Locale.ROOT, "%04d %s", numero, new String(letras));
    }

    boolean libre(String matricula) {
        return !y.isSet(clave(matricula));
    }

    String siguiente(Tier tier, String dueno) {
        long contador = y.getLong("contador", 0);
        String matricula;
        do { contador++; matricula = formato(contador); } while (!libre(matricula));
        y.set("contador", contador);
        apuntar(matricula, tier, dueno);
        return matricula;
    }

    void apuntar(String matricula, Tier tier, String dueno) {
        y.set(clave(matricula) + ".coche", tier.nombre());
        y.set(clave(matricula) + ".dueno", dueno);
        y.set(clave(matricula) + ".fecha", LocalDate.now().toString());
        guardar();
    }

    // para coches antiguos que aún no estaban en el registro
    void asegurar(String matricula, Tier tier, String dueno) {
        if (libre(matricula)) apuntar(matricula, tier, dueno);
    }

    void cambiar(String vieja, String nueva, Tier tier, String dueno) {
        y.set(clave(vieja), null);
        apuntar(nueva, tier, dueno);
    }

    int serie(String matricula) {
        return y.getInt(clave(matricula) + ".serie", 0);
    }

    void nuevaSerie(String matricula) {
        y.set(clave(matricula) + ".serie", serie(matricula) + 1);
        guardar();
    }

    // la matrícula queda ocupada (no se reutiliza) y se invalidan las llaves
    void baja(String matricula) {
        y.set(clave(matricula) + ".dueno", "desguace");
        y.set(clave(matricula) + ".fecha", LocalDate.now().toString());
        y.set(clave(matricula) + ".serie", serie(matricula) + 1);
        guardar();
    }

    int tanda() {
        return y.getInt("tanda", 0);
    }

    void reiniciar() {
        int tanda = tanda() + 1;
        for (String clave : y.getKeys(false)) y.set(clave, null);
        y.set("tanda", tanda);
        y.set("contador", 0);
        guardar();
    }

    Map<String, String> todas() {
        Map<String, String> mapa = new TreeMap<>();
        ConfigurationSection sec = y.getConfigurationSection("matriculas");
        if (sec == null) return mapa;
        for (String k : sec.getKeys(false))
            mapa.put(k.replace('_', ' '), sec.getString(k + ".coche", "?") + " · " + sec.getString(k + ".dueno", "?") + " · " + sec.getString(k + ".fecha", "?"));
        return mapa;
    }

    /** Matrícula -> nombre del vehículo de todo lo que está a nombre de ese jugador. */
    Map<String, String> de(String dueno) {
        Map<String, String> mapa = new TreeMap<>();
        ConfigurationSection sec = y.getConfigurationSection("matriculas");
        if (sec == null) return mapa;
        for (String k : sec.getKeys(false))
            if (dueno.equalsIgnoreCase(sec.getString(k + ".dueno", ""))) mapa.put(k.replace('_', ' '), sec.getString(k + ".coche", "Vehículo"));
        return mapa;
    }
}
