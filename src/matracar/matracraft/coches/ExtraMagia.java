package matracraft.coches;

import com.elmakers.mine.bukkit.api.event.PreCastEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ExtraMagia implements Listener {
    private final Map<UUID, Long> avisos = new HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void alLanzar(PreCastEvent e) {
        Player jugador = e.getMage() == null ? null : e.getMage().getPlayer();
        if (jugador == null || !Extra.conduce(jugador)) return;
        e.setCancelled(true);
        long ahora = System.currentTimeMillis();
        Long ultimo = avisos.get(jugador.getUniqueId());
        if (ultimo == null || ahora - ultimo > 3000L) {
            avisos.put(jugador.getUniqueId(), ahora);
            jugador.sendMessage("§c✋ No puedes usar magia mientras conduces.");
        }
    }
}
