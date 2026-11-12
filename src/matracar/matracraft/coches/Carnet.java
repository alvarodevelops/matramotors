package matracraft.coches;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Estadísticas del carnet de cada jugador (carnet.yml). */
final class Carnet {
    private final Coches pl;
    private final File archivo;
    private final YamlConfiguration y;
    private boolean cambios = false;

    Carnet(Coches pl) {
        this.pl = pl;
        this.archivo = new File(pl.getDataFolder(), "carnet.yml");
        this.y = YamlConfiguration.loadConfiguration(archivo);
        Bukkit.getScheduler().runTaskTimer(pl, this::guardar, 6000L, 6000L);
    }

    void guardar() {
        if (!cambios) return;
        try { y.save(archivo); cambios = false; }
        catch (IOException e) { pl.getLogger().warning("No pude guardar carnet.yml"); }
    }

    void sumar(UUID id, String clave, double cantidad) {
        if (cantidad == 0) return;
        y.set(id + "." + clave, y.getDouble(id + "." + clave, 0) + cantidad);
        cambios = true;
    }

    void sumar(Player jugador, String clave, double cantidad) { sumar(jugador.getUniqueId(), clave, cantidad); }

    void maximo(UUID id, String clave, double valor) {
        if (valor > y.getDouble(id + "." + clave, 0)) { y.set(id + "." + clave, valor); cambios = true; }
    }

    void nombre(UUID id, String clave, String texto) { y.set(id + "." + clave, texto); cambios = true; }

    double valor(UUID id, String clave) { return y.getDouble(id + "." + clave, 0); }

    String texto(UUID id, String clave) { return y.getString(id + "." + clave, "-"); }
}
