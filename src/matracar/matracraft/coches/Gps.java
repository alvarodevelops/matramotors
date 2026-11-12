package matracraft.coches;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * GPS sencillo: una barra de jefe con una flecha que apunta al destino.
 */
final class Gps {

    private static final class Ruta {
        final Location destino; final String nombre; final BossBar barra; final double inicial; int ticks = 0;
        Ruta(Location destino, String nombre, BossBar barra, double inicial) { this.destino = destino; this.nombre = nombre; this.barra = barra; this.inicial = inicial; }
    }

    private static final String[] FLECHAS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};
    private final Coches pl;
    private final Map<UUID, Ruta> rutas = new HashMap<>();

    Gps(Coches pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 10L, 5L);
    }

    void guiar(Player p, Location destino, String nombre) {
        parar(p);
        if (destino.getWorld() != p.getWorld()) {
            p.sendMessage(ChatColor.YELLOW + "🧭 " + nombre + " está en otro mundo (" + destino.getWorld().getName() + ").");
            return;
        }
        double distancia = p.getLocation().distance(destino);
        BossBar barra = BossBar.bossBar(Component.text("🧭 " + nombre), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        p.showBossBar(barra);
        rutas.put(p.getUniqueId(), new Ruta(destino, nombre, barra, Math.max(1, distancia)));
        p.sendMessage(ChatColor.GREEN + "🧭 GPS activado hacia " + ChatColor.WHITE + nombre + ChatColor.GREEN + String.format(Locale.ROOT, " (%,.0f m). ",
                distancia) + ChatColor.GRAY + "Sigue la flecha de arriba. Se quita solo al llegar.");
    }

    void parar(Player p) {
        Ruta r = rutas.remove(p.getUniqueId());
        if (r != null) p.hideBossBar(r.barra);
    }

    boolean activo(Player p) { return rutas.containsKey(p.getUniqueId()); }

    private void tick() {
        for (UUID id : rutas.keySet().toArray(new UUID[0])) {
            Player p = Bukkit.getPlayer(id);
            Ruta r = rutas.get(id);
            if (p == null) rutas.remove(id);
            else if (p.getWorld() != r.destino.getWorld() || ++r.ticks > 2400) parar(p); // 10 min como mucho
            else {
                Location loc = p.getLocation();
                double dx = r.destino.getX() - loc.getX();
                double dz = r.destino.getZ() - loc.getZ();
                double distancia = Math.hypot(dx, dz);
                if (distancia < 6) {
                    parar(p);
                    p.sendMessage(ChatColor.GREEN + "🧭 Has llegado: " + ChatColor.WHITE + r.nombre);
                    p.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 1f, 1.5f);
                    continue;
                }
                // si va en coche, manda la orientación del coche y no la de la cabeza
                float yaw = loc.getYaw();
                if (p.getVehicle() != null) {
                    Conduccion.Estado estado = pl.conduccion.conduciendo.get(p.getVehicle().getUniqueId());
                    if (estado != null) yaw = estado.yaw;
                }
                double rumbo = Math.toDegrees(Math.atan2(-dx, dz));
                double relativo = ((rumbo - yaw) % 360 + 540) % 360 - 180;
                int flecha = (int) Math.round((relativo + 360) % 360 / 45) % 8;
                double dy = r.destino.getY() - loc.getY();
                String altura = Math.abs(dy) > 8 ? (dy > 0 ? "  ↑ " : "  ↓ ") + Math.round(Math.abs(dy)) + " m" : "";
                r.barra.name(Component.text("🧭 " + r.nombre + "   ", NamedTextColor.WHITE).append(Component.text(FLECHAS[flecha] + " ",
                        NamedTextColor.GOLD)).append(Component.text(String.format(Locale.ROOT, "%,.0f m",
                        distancia) + altura, NamedTextColor.YELLOW)));
                r.barra.progress((float) Math.max(0, Math.min(1, 1 - distancia / r.inicial)));
            }
        }
    }

    void pararTodo() {
        for (UUID id : rutas.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(rutas.get(id).barra);
        }
        rutas.clear();
    }
}
