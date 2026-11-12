package matracraft.coches;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Tipo de vehículo (gama) definido en config.yml: coches en "coches", embarcaciones en "barcos".
 * agua = embarcación (solo navega por agua); plazas = conductor + pasajeros;
 * resistencia multiplica los daños (más alta = más frágil); vida es la vida de referencia (100 = normal).
 */
record Tier(String id, String nombre, Material bloque, double precio, double velocidad,
            double aceleracion, double frenada, double giro, double deposito, double consumo,
            boolean todoterreno, int carga, boolean agua, int plazas, double resistencia, double vida, boolean taxi) {

    // velocidad en bloques/segundo -> bloques/tick
    double maxPorTick() {
        return velocidad / 20.0;
    }

    /** Multiplicador de daño: la resistencia de la ficha corregida por su vida (180 de vida = aguanta 1,8 veces más). */
    double fragilidad() {
        return resistencia * 100.0 / Math.max(10, vida);
    }

    /** Los vehículos sin depósito (el pedalo) no gastan gasolina. */
    boolean sinMotor() {
        return deposito <= 0;
    }

    private static int huecos(int pedidos) {
        if (pedidos <= 0) return 0;
        if (pedidos < 9) return 5;
        return Math.min(54, pedidos / 9 * 9);
    }

    String tipo() {
        return agua ? "embarcación" : "coche";
    }

    static Tier leer(String id, ConfigurationSection s) {
        return leer(id, s, false);
    }

    static Tier leer(String id, ConfigurationSection s, boolean agua) {
        Material bloque = Material.matchMaterial(s.getString("bloque", "BARREL"));
        return new Tier(id, s.getString("nombre", id), bloque == null ? Material.BARREL : bloque,
                s.getDouble("precio", 5000.0), s.getDouble("velocidad", 8.0), s.getDouble("aceleracion", 0.01),
                s.getDouble("frenada", 0.02), s.getDouble("giro", 4.0), s.getDouble("deposito", 30.0),
                s.getDouble("consumo", 0.008), s.getBoolean("todoterreno", false),
                // filas completas de 9 huecos (máximo 54); menos de 9 = guantera de 5 huecos
                huecos(s.getInt("carga", 0)),
                agua, Math.max(1, s.getInt("plazas", 1)), s.getDouble("resistencia", 1.0), s.getDouble("vida", 100.0),
                s.getBoolean("taxi", false));
    }
}
