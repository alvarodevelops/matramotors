package matracraft.matrains;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/** Partículas de las locomotoras y ventajas de los coches de pasajeros. */
final class Efectos {

    private Efectos() {}

    private static final Particle.DustOptions CIAN = new Particle.DustOptions(Color.fromRGB(80, 220, 255), 0.8f);

    static void particulas(Tren t, Tipos.Loco loco, boolean acelera) {
        Location l = t.lugar(t.locomotora());
        if (l == null) return;
        World w = l.getWorld();
        int tk = t.ticks;
        boolean anda = Math.abs(t.v) > 0.01;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (loco.id()) {
            case "vapor" -> {
                if (tk % (acelera ? 2 : 5) == 0)
                    w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, Tren.relativo(l, 0, 2.0, 2.05), 0, 0, 0.12, 0, 1);
                if (anda && tk % 8 == 0) for (int s = -1; s <= 1; s += 2)
                    w.spawnParticle(Particle.CLOUD, Tren.relativo(l, s * 0.7, 1.95, 0.35), 2, 0.05, 0.02, 0.05, 0.01);
            }
            case "diesel" -> {
                if (tk % (acelera ? 2 : 6) == 0)
                    w.spawnParticle(acelera ? Particle.LARGE_SMOKE : Particle.SMOKE, Tren.relativo(l, 0, -0.9, 1.95), acelera ? 2 : 1, 0.08, 0.05, 0.08, 0.01);
            }
            case "electrica", "ave" -> {
                double frente = loco.id().equals("ave") ? -1.65 : -0.05, alto = loco.id().equals("ave") ? 2.22 : 2.75;
                if (anda && tk % 7 == 0 && r.nextDouble() < 0.6)
                    w.spawnParticle(Particle.ELECTRIC_SPARK, Tren.relativo(l, r.nextDouble(-0.3, 0.3), frente, alto), 3, 0.05, 0.02, 0.05, 0.05);
                if (anda && tk % 15 == 0 && r.nextDouble() < 0.4)
                    w.spawnParticle(Particle.ELECTRIC_SPARK, Tren.relativo(l, r.nextBoolean() ? 0.55 : -0.55, r.nextDouble(-1.5, 1.5), 0.05), 2, 0.03, 0.02, 0.03, 0.03);
            }
            case "maglev" -> {
                if (tk % 4 == 0)
                    w.spawnParticle(Particle.DUST, Tren.relativo(l, r.nextDouble(-0.45, 0.45), r.nextDouble(-2.4, 2.0), 0.0), 2, 0.05, 0.01, 0.05, 0, CIAN);
            }
            default -> { }
        }
    }

    /** Cada 5 segundos: los de primera se recuperan y el mirador da visión nocturna en túneles. */
    static void pasajeros(Tren t) {
        for (Tren.Coche c : t.coches) {
            if (c.locomotora || c.base == null) continue;
            boolean premium = c.tipo.equals("premium"), mirador = c.tipo.equals("mirador");
            if (!premium && !mirador) continue;
            for (Player p : viajeros(c)) {
                if (premium) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0, true, false, true));
                if (mirador && p.getLocation().getBlock().getLightFromSky() < 8)
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, true, false, true));
            }
        }
    }

    static java.util.List<Player> viajeros(Tren.Coche c) {
        java.util.List<Player> r = new java.util.ArrayList<>();
        if (c.base != null) for (Entity e : c.base.getPassengers()) if (e instanceof Player p) r.add(p);
        for (ArmorStand s : c.sillas) for (Entity e : s.getPassengers()) if (e instanceof Player p) r.add(p);
        return r;
    }
}
