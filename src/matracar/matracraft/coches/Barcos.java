package matracraft.coches;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;

/**
 * Navegación de las embarcaciones: flotan a la altura del agua (sin gravedad), no salen a tierra,
 * no corren sobre hielo, remontan corrientes y cascadas despacio, la Moto de agua salta en rampas
 * y La Goma vuelca si se gira a tope a mucha velocidad.
 */
final class Barcos {

    private Barcos() {}

    static final NamespacedKey VOLCADA = NamespacedKey.fromString("coches:volcada");

    static boolean volcada(Coches pl, Entity e) {
        return e.getPersistentDataContainer().has(VOLCADA, PersistentDataType.BYTE);
    }

    /** Mueve la embarcación este tick (ajusta st.velocidad) y devuelve la velocidad vertical. */
    static double mover(Coches pl, Conduccion.Estado st, Player p, Vector dir, int giro) {
        ArmorStand cart = st.cart;
        Tier tier = st.tier;
        World mundo = cart.getWorld();
        Location l = cart.getLocation();
        double max = tier.maxPorTick();
        double vy = cart.getVelocity().getY();
        double aqui = Agua.superficie(mundo, l.getX(), l.getY(), l.getZ());

        if (Double.isNaN(aqui)) {
            // fuera del agua: o cae (cascada abajo, salto) o está varada en tierra
            if (!cart.isOnGround()) {
                st.alturaCaida = Double.isNaN(st.alturaCaida) ? l.getY() : Math.max(st.alturaCaida, l.getY());
                return Math.max(-2.0, vy - 0.08);
            }
            Danos.caida(pl, st, p, true, false);
            if (!cart.isValid()) return 0;
            // varado: solo se puede mover si hay agua hacia donde va
            Location hacia = l.clone().add(dir.clone().multiply(Math.signum(st.velocidad) * 1.2));
            boolean aguaDelante = !Double.isNaN(Agua.superficie(mundo, hacia.getX(), l.getY(), hacia.getZ()));
            st.velocidad = aguaDelante ? Math.max(-0.02, Math.min(0.02, st.velocidad)) : 0;
            if (st.ticks % 60 == 0) p.sendMessage(ChatColor.GRAY + "Estás varado. Bájate y guárdalo, o empuja marcha atrás hacia el agua.");
            return vy;
        }
        // caer al agua no hace daño
        st.alturaCaida = Double.NaN;
        double objetivo = aqui;

        double signo = Math.signum(st.velocidad);
        double rapidez = Math.abs(st.velocidad);
        if (rapidez > 0.001) {
            Location delante = l.clone().add(dir.clone().multiply(signo * (1.3 + rapidez * 2)));
            double alla = Agua.superficie(mundo, delante.getX(), aqui + 0.5, delante.getZ());
            Block frente = mundo.getBlockAt(delante.getBlockX(), (int) Math.floor(aqui), delante.getBlockZ());
            boolean rampa = Danos.esRampa(frente) || Danos.esRampa(frente.getRelative(0, -1, 0));
            if (tier.id().equals("motoagua") && rampa && rapidez > max * 0.6 && st.saltoCd == 0) {
                // la moto de agua salta las rampas del borde
                st.saltoCd = 14;
                mundo.spawnParticle(Particle.SPLASH, l, 20, 0.4, 0.1, 0.4, 0.1);
                mundo.playSound(l, Sound.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 0.8f, 1.3f);
                return 0.55;
            }
            if (Double.isNaN(alla)) {
                // orilla o hielo delante: se para (si llega rápido, golpe proporcional)
                if (st.saltoCd == 0) Danos.choque(pl, st, p, rapidez);
                st.velocidad = 0;
            } else if (alla > aqui + 0.5) {
                // remontando una cascada o corriente: despacio
                st.velocidad = signo * Math.min(rapidez, max * 0.4);
                objetivo = alla;
            }
        }
        // La Goma: girar a tope por encima del 80 % de su velocidad durante más de un segundo la vuelca
        if (tier.id().equals("goma")) {
            if (giro != 0 && rapidez > max * 0.8) st.giroFuerte++;
            else st.giroFuerte = Math.max(0, st.giroFuerte - 2);
            // con tormenta vuelca antes
            if (st.giroFuerte > (Firme.tormenta(mundo) ? 12 : 20)) { volcar(pl, cart, p); return 0; }
            // con poca vida se desinfla: pierde velocidad
            if (st.salud < 25) st.velocidad = Math.signum(st.velocidad) * Math.min(Math.abs(st.velocidad), max * 0.6);
        }
        if (st.saltoCd > 0 && l.getY() > aqui + 0.3) return Math.max(-0.5, vy - 0.06); // en el aire tras un salto
        return Math.max(-0.4, Math.min(0.45, (objetivo - l.getY()) * 0.6));
    }

    static void volcar(Coches pl, ArmorStand cart, Player conductor) {
        Conduccion.Estado st = pl.conduccion.conduciendo.remove(cart.getUniqueId());
        if (st != null) pl.conduccion.guardar(st);
        for (Entity e : new ArrayList<>(cart.getPassengers())) cart.removePassenger(e);
        pl.taximetro.alBajarseTodos(cart);
        pl.modelos.vaciarAsientos(cart);
        cart.getPersistentDataContainer().set(VOLCADA, PersistentDataType.BYTE, (byte) 1);
        pl.conduccion.frenoDeMano(cart);
        pl.conduccion.danarCoche(cart, 10, "el vuelco");
        cart.getWorld().spawnParticle(Particle.SPLASH, cart.getLocation(), 60, 1, 0.2, 1, 0.2);
        cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.2f, 0.7f);
        if (conductor != null) conductor.sendMessage(ChatColor.RED + "🌊 ¡La Goma ha volcado! Mayúsculas + clic derecho sobre ella para enderezarla.");
        pl.modelos.rehacer(cart);
    }

    static void enderezar(Coches pl, Player p, ArmorStand cart) {
        if (!pl.tieneLlave(p, pl.conduccion.matriculaDe(cart))) { p.sendMessage(ChatColor.RED + "Necesitas su llave para enderezarla."); return; }
        p.sendMessage(ChatColor.YELLOW + "Enderezando… quédate cerca 3 segundos.");
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!cart.isValid() || !p.isOnline() || p.getLocation().distanceSquared(cart.getLocation()) > 64) {
                if (p.isOnline()) p.sendMessage(ChatColor.RED + "Te has alejado: no se ha enderezado.");
                return;
            }
            cart.getPersistentDataContainer().remove(VOLCADA);
            pl.modelos.rehacer(cart);
            cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1f, 1.2f);
            p.sendMessage(ChatColor.GREEN + "Enderezada. Ya puedes subirte.");
        }, 60L);
    }
}
