package matracraft.coches;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

/**
 * Daños proporcionales (MATRAMOTORS 2.0):
 *  - choque: vida × máximo × ((v − v0) / (vmax − v0))², solo por encima de v0, un golpe cada 'espera' ticks
 *  - caída: por cada bloque por encima de 'caida-desde'; al agua no hace daño
 *  - escaleras y losas son rampas: ni choque ni caída
 * Todo se multiplica por la fragilidad del vehículo (resistencia y vida de su ficha).
 */
final class Danos {

    private Danos() {}

    static boolean esRampa(Block b) {
        if (Tag.STAIRS.isTagged(b.getType())) return true;
        return Tag.SLABS.isTagged(b.getType()) && b.getBlockData() instanceof Slab losa && losa.getType() == Slab.Type.BOTTOM;
    }

    /** Golpe de frente a 'velocidad' bloques/tick. */
    static void choque(Coches pl, Conduccion.Estado st, Player p, double velocidad) {
        ArmorStand cart = st.cart;
        if (st.golpeCd > 0) return;
        double max = st.tier.maxPorTick();
        double v0 = max * pl.getConfig().getDouble("danos.choque-desde", 0.35);
        if (velocidad <= v0 * 0.5) return; // un roce: ni ruido ni daño
        cart.getWorld().playSound(cart.getLocation(), st.tier.agua() ? Sound.BLOCK_WOOD_HIT : Sound.ENTITY_IRON_GOLEM_HURT, SoundCategory.PLAYERS, 0.6f, 1.2f);
        if (velocidad <= v0) { st.golpeCd = 10; return; }
        double maximo = pl.getConfig().getDouble("danos.choque-maximo", 40);
        double fraccion = Math.min(1, (velocidad - v0) / Math.max(1e-6, max - v0));
        double dano = Math.min(maximo, maximo * fraccion * fraccion * st.tier.fragilidad());
        st.golpeCd = pl.getConfig().getInt("danos.espera-entre-golpes", 20);
        pl.conduccion.danarCoche(cart, dano, st.tier.agua() ? "un golpe contra la orilla" : "un choque");
        if (dano > 0.5) pl.carnet.sumar(p, "choques", 1);
    }

    /** Lleva la cuenta de la altura de caída y aplica el daño al tocar suelo. */
    static void caida(Coches pl, Conduccion.Estado st, Player p, boolean enSuelo, boolean enAgua) {
        double y = st.cart.getLocation().getY();
        if (!enSuelo && !enAgua) {
            st.alturaCaida = Double.isNaN(st.alturaCaida) ? y : Math.max(st.alturaCaida, y);
            return;
        }
        if (Double.isNaN(st.alturaCaida)) return;
        double h = st.alturaCaida - y;
        st.alturaCaida = Double.NaN;
        double desde = pl.getConfig().getDouble("danos.caida-desde", 3);
        if (enAgua || h <= desde) return;
        double dano = pl.getConfig().getDouble("danos.caida-por-bloque", 6) * (h - desde) * st.tier.fragilidad();
        if (dano >= 100) {
            p.sendMessage(ChatColor.DARK_RED + "💥 Caída de " + Math.round(h) + " bloques: siniestro total.");
            pl.seguros.siniestro(st.cart, st.tier, st.matricula, p);
            return;
        }
        pl.conduccion.danarCoche(st.cart, dano, "una caída de " + Math.round(h) + " bloques");
    }
}
