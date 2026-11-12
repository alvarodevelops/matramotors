package matracraft.coches;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Lo que el terreno y el tiempo le hacen al vehículo:
 *  - el firme: por asfalto se va a tope, por tierra y hierba más despacio, por arena y nieve mucho más
 *    (el todoterreno sufre menos);
 *  - el clima real (el del mundo, que sincroniza ClimaReal): con lluvia los coches frenan y giran peor,
 *    con tormenta las embarcaciones van más lentas y La Goma vuelca antes;
 *  - los chunks sin generar: si a dos segundos por delante el mundo aún no existe, se limita la velocidad
 *    para que el servidor no se ahogue generando terreno.
 */
final class Firme {

    private Firme() {}

    /** Multiplicador de la velocidad máxima según el bloque que pisa el coche. */
    static double factorSuelo(Coches pl, Tier tier, Location loc) {
        if (tier.agua() || !pl.getConfig().getBoolean("suelo.activado", true)) return 1;
        Block b = loc.getBlock();
        if (b.isPassable() || b.isLiquid()) b = b.getRelative(BlockFace.DOWN);
        Material m = b.getType();
        String tipo = tipo(m);
        double f = pl.getConfig().getDouble("suelo." + tipo, switch (tipo) {
            case "asfalto" -> 1.0;
            case "tierra" -> 0.6;
            case "arena" -> 0.4;
            default -> 0.85;
        });
        if (tier.todoterreno()) f = 1 - (1 - f) * pl.getConfig().getDouble("suelo.todoterreno-sufre", 0.35);
        return Math.max(0.1, Math.min(1, f));
    }

    static String tipo(Material m) {
        String n = m.name();
        if (n.endsWith("_CONCRETE") || n.endsWith("_TERRACOTTA") || m == Material.DIRT_PATH || n.contains("STONE_BRICK")
                || n.startsWith("POLISHED_") || n.contains("SMOOTH_") || n.endsWith("_TILES") || n.equals("BRICKS")
                || n.contains("BRICK_SLAB") || n.contains("BRICK_STAIRS") || m == Material.STONE || m == Material.COBBLESTONE
                || m == Material.BLACKSTONE || m == Material.TUFF || n.startsWith("CUT_")
                || Tag.SLABS.isTagged(m) || Tag.STAIRS.isTagged(m) || m == Material.IRON_BLOCK)
            return "asfalto";
        if (Tag.SAND.isTagged(m) || m == Material.GRAVEL || m == Material.SNOW || m == Material.SNOW_BLOCK || m == Material.POWDER_SNOW
                || m == Material.MUD || m == Material.SOUL_SAND || m == Material.SOUL_SOIL || m == Material.CLAY || n.endsWith("_CONCRETE_POWDER"))
            return "arena";
        if (Tag.DIRT.isTagged(m) || m == Material.FARMLAND || m == Material.MOSS_BLOCK || Tag.LEAVES.isTagged(m))
            return "tierra";
        return "otro";
    }

    static boolean lluvia(World w) { return w.hasStorm(); }

    static boolean tormenta(World w) { return w.hasStorm() && w.isThundering(); }

    /** Multiplicador de la velocidad máxima por el tiempo. */
    static double factorClima(Coches pl, Tier tier, World w) {
        if (!pl.getConfig().getBoolean("clima.activado", true)) return 1;
        if (tier.agua()) return tormenta(w) ? pl.getConfig().getDouble("clima.barcos-tormenta", 0.8) : 1;
        return 1;
    }

    /** Multiplicador de la frenada y del giro de los coches (con lluvia se frena y se gira peor). */
    static double agarre(Coches pl, Tier tier, World w) {
        if (tier.agua() || !pl.getConfig().getBoolean("clima.activado", true) || !lluvia(w)) return 1;
        return pl.getConfig().getDouble("clima.agarre-lluvia", 0.5);
    }

    /** Velocidad máxima (bloques/tick) si delante hay chunks sin generar; si no, Double.MAX_VALUE. */
    static double topeChunks(Coches pl, Location loc, Vector dir, double velocidad) {
        double tope = pl.getConfig().getDouble("chunks.velocidad-sin-generar", 10) / 20.0;
        if (Math.abs(velocidad) <= tope) return Double.MAX_VALUE;
        double distancia = Math.abs(velocidad) * 40 + 16; // unos dos segundos por delante
        Location delante = loc.clone().add(dir.clone().multiply(Math.signum(velocidad) * distancia));
        World w = loc.getWorld();
        return w.isChunkGenerated(delante.getBlockX() >> 4, delante.getBlockZ() >> 4) ? Double.MAX_VALUE : tope;
    }
}
