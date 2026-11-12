package matracraft.coches;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;

import java.util.UUID;

/** Acceso a GriefPrevention aislado para que el plugin funcione sin él. */
final class GpHook {
    private GpHook() {}

    // Dueño de la parcela en esa posición, o null si no hay parcela (o falla GP)
    static UUID dueno(Location sitio) {
        try {
            Claim parcela = GriefPrevention.instance.dataStore.getClaimAt(sitio, true, null);
            return parcela == null ? null : parcela.getOwnerID();
        } catch (Throwable e) {
            return null;
        }
    }
}
