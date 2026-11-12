package matracraft.coches;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;

/** Utilidades de agua para las embarcaciones. */
final class Agua {

    private Agua() {}

    /** Bloques sobre los que una embarcación no avanza (el hielo de vanilla las haría volar). */
    static boolean hielo(Material m) {
        return m == Material.ICE || m == Material.PACKED_ICE || m == Material.BLUE_ICE || m == Material.FROSTED_ICE;
    }

    /** Agua navegable: agua, columnas de burbujas, algas y bloques anegados sin colisión. */
    static boolean esAgua(Block b) {
        Material m = b.getType();
        if (m == Material.WATER || m == Material.BUBBLE_COLUMN || m == Material.KELP || m == Material.KELP_PLANT
                || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS) return true;
        return b.getBlockData() instanceof org.bukkit.block.data.Waterlogged w && w.isWaterlogged() && b.isPassable();
    }

    /**
     * Altura de la superficie del agua en esa columna, buscando desde un bloque por encima de y
     * hasta tres por debajo. NaN si no hay agua o si está helada por arriba.
     */
    static double superficie(World mundo, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y);
        for (int yy = by + 1; yy >= by - 3; yy--) {
            if (yy < mundo.getMinHeight() || yy >= mundo.getMaxHeight()) continue;
            Block b = mundo.getBlockAt(bx, yy, bz);
            if (!esAgua(b)) continue;
            // si está sumergido, se sube hasta la superficie de verdad
            for (int i = 0; i < 64 && esAgua(b.getRelative(0, 1, 0)) && b.getY() + 1 < mundo.getMaxHeight(); i++) b = b.getRelative(0, 1, 0);
            yy = b.getY();
            Block arriba = b.getRelative(0, 1, 0);
            if (hielo(arriba.getType())) return Double.NaN;
            double alto = 8.0 / 9.0;
            if (b.getBlockData() instanceof Levelled nivel && nivel.getLevel() > 0 && nivel.getLevel() < 8)
                alto = (8 - nivel.getLevel()) / 9.0;
            return yy + alto;
        }
        return Double.NaN;
    }
}
