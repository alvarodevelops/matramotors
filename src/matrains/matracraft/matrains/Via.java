package matracraft.matrains;

import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometría de los raíles: por dónde entra y sale un tren de cada bloque de vía, qué bloque viene
 * después (también en cuestas) y el punto exacto del recorrido. El tren no usa la física de las
 * vagonetas: avanza por esta "cinta" a la velocidad que diga su locomotora.
 */
final class Via {

    private Via() {}

    static final BlockFace[] LADOS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    static boolean esVia(Block b) {
        return b != null && Tag.RAILS.isTagged(b.getType());
    }

    static Rail.Shape forma(Block b) {
        return b.getBlockData() instanceof Rail r ? r.getShape() : null;
    }

    /** Los dos lados que une un raíl según su forma. */
    static BlockFace[] caras(Rail.Shape s) {
        return switch (s) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new BlockFace[]{BlockFace.EAST, BlockFace.WEST};
            case SOUTH_EAST -> new BlockFace[]{BlockFace.SOUTH, BlockFace.EAST};
            case SOUTH_WEST -> new BlockFace[]{BlockFace.SOUTH, BlockFace.WEST};
            case NORTH_WEST -> new BlockFace[]{BlockFace.NORTH, BlockFace.WEST};
            case NORTH_EAST -> new BlockFace[]{BlockFace.NORTH, BlockFace.EAST};
        };
    }

    /** Lado hacia el que sube el raíl (null si es llano). */
    static BlockFace sube(Rail.Shape s) {
        if (s == null) return null;
        return switch (s) {
            case ASCENDING_NORTH -> BlockFace.NORTH;
            case ASCENDING_SOUTH -> BlockFace.SOUTH;
            case ASCENDING_EAST -> BlockFace.EAST;
            case ASCENDING_WEST -> BlockFace.WEST;
            default -> null;
        };
    }

    static boolean une(Block b, BlockFace cara) {
        Rail.Shape s = forma(b);
        if (s == null) return false;
        BlockFace[] c = caras(s);
        return c[0] == cara || c[1] == cara;
    }

    /** Bloque de vía al que se llega saliendo de b por el lado d (mismo nivel, subiendo o bajando); null si no hay. */
    /** Lo pone vecino() cuando no sigue porque el bloque de al lado está en una zona sin cargar. */
    static boolean sinCargar;

    private static boolean cargado(Block b, BlockFace d) {
        int x = b.getX() + d.getModX(), z = b.getZ() + d.getModZ();
        return b.getWorld().isChunkLoaded(x >> 4, z >> 4);
    }

    static Block vecino(Block b, BlockFace d) {
        if (!cargado(b, d)) { sinCargar = true; return null; }
        Rail.Shape s = forma(b);
        if (sube(s) == d) {
            Block arriba = b.getRelative(d).getRelative(BlockFace.UP);
            return esVia(arriba) ? arriba : null;
        }
        Block igual = b.getRelative(d);
        if (esVia(igual)) return igual;
        Block abajo = igual.getRelative(BlockFace.DOWN);
        if (esVia(abajo) && sube(forma(abajo)) == d.getOppositeFace()) return abajo;
        return null;
    }

    /** Lados por los que se puede salir de b habiendo entrado por 'entrada' (los de su forma y los desvíos reales). */
    static List<BlockFace> salidas(Block b, BlockFace entrada) {
        List<BlockFace> r = new ArrayList<>(3);
        Rail.Shape s = forma(b);
        if (s == null) return r;
        for (BlockFace c : caras(s)) if (c != entrada && vecino(b, c) != null) r.add(c);
        for (BlockFace c : LADOS) {
            if (c == entrada || r.contains(c)) continue;
            Block v = vecino(b, c);
            if (v != null && une(v, c.getOppositeFace())) r.add(c);
        }
        return r;
    }

    static BlockFace izquierda(BlockFace marcha) {
        return switch (marcha) { case NORTH -> BlockFace.WEST; case WEST -> BlockFace.SOUTH; case SOUTH -> BlockFace.EAST; default -> BlockFace.NORTH; };
    }

    /**
     * Elige la salida. preferencia: -1 izquierda, 1 derecha, 0 lo que marque la vía (la aguja vanilla).
     * Devuelve null si la vía se acaba.
     */
    static BlockFace elegir(Block b, BlockFace entrada, int preferencia) {
        List<BlockFace> op = salidas(b, entrada);
        if (op.isEmpty()) return null;
        BlockFace marcha = entrada.getOppositeFace();
        if (preferencia != 0 && op.size() > 1) {
            BlockFace lado = preferencia < 0 ? izquierda(marcha) : izquierda(marcha).getOppositeFace();
            if (op.contains(lado)) return lado;
        }
        Rail.Shape s = forma(b);
        BlockFace[] c = caras(s);
        if (c[0] == entrada && op.contains(c[1])) return c[1];
        if (c[1] == entrada && op.contains(c[0])) return c[0];
        if (op.contains(marcha)) return marcha;
        return op.get(0);
    }

    private static double alto(Rail.Shape s, BlockFace cara) {
        return sube(s) == cara ? 1.0 : 0.0;
    }

    /** Punto del recorrido dentro del bloque: t = 0 en el lado 'desde', t = 1 en el lado 'hacia'. */
    static Vector punto(Block b, BlockFace desde, BlockFace hacia, double t) {
        Rail.Shape s = forma(b);
        double cx = b.getX() + 0.5, cz = b.getZ() + 0.5, y = b.getY();
        double hd = s == null ? 0 : alto(s, desde), hh = s == null ? 0 : alto(s, hacia);
        Vector pd = new Vector(cx + 0.5 * desde.getModX(), y + hd, cz + 0.5 * desde.getModZ());
        Vector ph = new Vector(cx + 0.5 * hacia.getModX(), y + hh, cz + 0.5 * hacia.getModZ());
        if (desde == hacia.getOppositeFace()) return pd.add(ph.subtract(pd).multiply(t));
        Vector c = new Vector(cx, y + (hd + hh) / 2, cz);
        return t < 0.5 ? pd.add(c.clone().subtract(pd).multiply(t * 2)) : c.add(ph.subtract(c).multiply(t * 2 - 1));
    }

    /** Posición sobre la vía: bloque, lado de entrada, lado de salida y avance (0-1). */
    static final class Pos {
        String mundo;
        int x, y, z;
        BlockFace desde, hacia;
        double t;

        Pos(String mundo, int x, int y, int z, BlockFace desde, BlockFace hacia, double t) {
            this.mundo = mundo; this.x = x; this.y = y; this.z = z; this.desde = desde; this.hacia = hacia; this.t = t;
        }

        Pos copia() { return new Pos(mundo, x, y, z, desde, hacia, t); }

        World world() { return Bukkit.getWorld(mundo); }

        Block bloque() {
            World w = world();
            return w == null ? null : w.getBlockAt(x, y, z);
        }

        boolean cargado() {
            World w = world();
            return w != null && w.isChunkLoaded(x >> 4, z >> 4);
        }

        void poner(Block b, BlockFace desde, BlockFace hacia, double t) {
            x = b.getX(); y = b.getY(); z = b.getZ(); this.desde = desde; this.hacia = hacia; this.t = t;
        }

        Vector punto() { return Via.punto(bloque(), desde, hacia, t); }

        boolean curva() { return desde != hacia.getOppositeFace(); }

        long clave() { return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF); }

        String guardar() { return mundo + "," + x + "," + y + "," + z + "," + desde.name() + "," + hacia.name() + "," + t; }

        static Pos leer(String s) {
            String[] p = s.split(",");
            return new Pos(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                    BlockFace.valueOf(p[4]), BlockFace.valueOf(p[5]), Double.parseDouble(p[6]));
        }
    }
}
