package matracraft.matrains;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enganchar y soltar vagones con una cadena: clic derecho engancha el tren a la cola (o frente) más
 * cercana a menos de 3 bloques; mayúsculas + clic suelta ese vagón y los que van detrás.
 */
final class Enganche {

    private Enganche() {}

    private static Vector frente(Tren t) { return t.coches.get(0).delante.punto(); }

    private static Vector cola(Tren t) { return t.coches.get(t.coches.size() - 1).detras.punto(); }

    static void enganchar(Matrains pl, Player p, Tren a) {
        if (Math.abs(a.v) > 0.01) { p.sendMessage(ChatColor.RED + "El tren tiene que estar parado."); return; }
        double max = 3.0;
        Tren mejor = null;
        boolean aCola = true, bFrente = true;
        double mejorD = max;
        for (Tren b : pl.trenes.values()) {
            if (b == a || !b.visible || !b.coches.get(0).delante.mundo.equals(a.coches.get(0).delante.mundo)) continue;
            if (Math.abs(b.v) > 0.01) continue;
            Vector[] ea = {cola(a), frente(a)}, eb = {frente(b), cola(b)};
            for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
                double d = ea[i].distance(eb[j]);
                if (d < mejorD) { mejorD = d; mejor = b; aCola = i == 0; bFrente = j == 0; }
            }
        }
        if (mejor == null) { p.sendMessage(ChatColor.RED + "No hay otro vagón o tren a menos de 3 bloques de este extremo."); return; }
        Tren b = mejor;
        if (!pl.esDueno(p, b)) { p.sendMessage(ChatColor.RED + "El otro tren no es tuyo."); return; }
        if (a.conLoco() && b.conLoco()) { p.sendMessage(ChatColor.RED + "No se pueden juntar dos locomotoras."); return; }
        // el que tiene locomotora manda y los vagones van siempre detrás de ella
        Tren principal = a, otro = b;
        boolean enCola = aCola, otroFrente = bFrente;
        if (b.conLoco()) { principal = b; otro = a; enCola = !bFrente; otroFrente = aCola; }
        if (principal.conLoco() && !enCola) { p.sendMessage(ChatColor.RED + "Los vagones se enganchan detrás de la locomotora."); return; }
        if (otro.ocupado() || principal.ocupado()) { p.sendMessage(ChatColor.RED + "Que se baje todo el mundo antes de enganchar."); return; }
        // límites de la locomotora
        if (principal.conLoco()) {
            Tipos.Loco loco = pl.tipos.loco(principal.tipo);
            int vagones = principal.coches.size() - 1 + otro.coches.size();
            if (vagones > loco.vagones()) { p.sendMessage(ChatColor.RED + loco.nombre() + " arrastra como mucho " + loco.vagones() + " vagones."); return; }
            if (loco.soloPasajeros()) for (Tren.Coche c : otro.coches) {
                Tipos.Vagon tv = pl.tipos.vagon(c.tipo);
                if (tv == null || !tv.pasajeros() || !tv.moderno()) {
                    p.sendMessage(ChatColor.RED + loco.nombre() + " solo lleva coches de pasajeros modernos (primera, restaurante, Pullman y mirador).");
                    return;
                }
            }
        }
        // colocar los coches del otro pegados al extremo, sobre la misma vía
        List<Tren.Coche> orden = new ArrayList<>(otro.coches);
        // el coche más cercano al punto de unión va primero
        if (!otroFrente) java.util.Collections.reverse(orden);
        double hueco = pl.getConfig().getDouble("fisica.hueco-entre-vagones", 0.5);
        int sentido = enCola ? -1 : 1;
        Via.Pos cursor = (enCola ? principal.coches.get(principal.coches.size() - 1).detras : principal.coches.get(0).delante).copia();
        List<Via.Pos[]> nuevas = new ArrayList<>();
        for (Tren.Coche c : orden) {
            double paso = 2 * Tren.EJE + hueco, ejes = c.largo - 2 * Tren.EJE;
            if (Math.abs(principal.avanzar(cursor, sentido * paso, true, 0)) < paso - 1e-6) { p.sendMessage(ChatColor.RED + "No hay vía para enganchar."); return; }
            Via.Pos cerca = cursor.copia();
            if (Math.abs(principal.avanzar(cursor, sentido * ejes, true, 0)) < ejes - 1e-6) { p.sendMessage(ChatColor.RED + "No hay vía para enganchar."); return; }
            Via.Pos lejos = cursor.copia();
            nuevas.add(enCola ? new Via.Pos[]{cerca, lejos} : new Via.Pos[]{lejos, cerca});
        }
        // tienen que estar de verdad en la misma vía (no en una paralela)
        Via.Pos[] primera = nuevas.get(0);
        Vector antes = otroFrente ? orden.get(0).delante.punto() : orden.get(0).detras.punto();
        Vector ahora = (enCola ? primera[0] : primera[1]).punto();
        if (antes.distance(ahora) > 2.0) { p.sendMessage(ChatColor.RED + "No están en la misma vía."); return; }
        otro.desaparecer();
        pl.trenes.remove(otro.id);
        principal.desaparecer();
        List<Tren.Coche> colocados = new ArrayList<>();
        for (int i = 0; i < orden.size(); i++) {
            Tren.Coche c = orden.get(i);
            c.delante = nuevas.get(i)[0];
            c.detras = nuevas.get(i)[1];
            colocados.add(c);
        }
        if (enCola) principal.coches.addAll(colocados);
        else { java.util.Collections.reverse(colocados); principal.coches.addAll(0, colocados); }
        pl.reindexar();
        principal.aparecer();
        pl.guardar();
        Location l = principal.lugar(principal.coches.get(0));
        if (l != null) l.getWorld().playSound(l, Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1f, 0.8f);
        p.sendMessage(ChatColor.GREEN + "Enganchado. El tren lleva " + (principal.coches.size() - (principal.conLoco() ? 1 : 0)) + " vagones.");
    }

    static void soltar(Matrains pl, Player p, Tren t, Tren.Coche c) {
        int i = t.coches.indexOf(c);
        if (c.locomotora || i <= 0) { p.sendMessage(ChatColor.GRAY + "Con mayúsculas + clic en un vagón lo sueltas (a él y a los de detrás)."); return; }
        if (Math.abs(t.v) > 0.01) { p.sendMessage(ChatColor.RED + "El tren tiene que estar parado."); return; }
        if (t.ocupado()) { p.sendMessage(ChatColor.RED + "Que se baje todo el mundo antes de soltar vagones."); return; }
        Location dondeL = t.lugar(c);
        Estaciones.Estacion est = dondeL == null ? null : pl.estaciones.en(dondeL);
        if (!p.hasPermission("matrains.admin") && (est == null || !est.dueno.equals(p.getUniqueId()))) {
            p.sendMessage(ChatColor.RED + "Los vagones solo se sueltan dentro de una estación tuya."); return;
        }
        t.desaparecer();
        Tren n = new Tren(pl, UUID.randomUUID().toString().substring(0, 8), t.dueno, null, "Vagones de " + t.nombre);
        n.coches.addAll(new ArrayList<>(t.coches.subList(i, t.coches.size())));
        t.coches.subList(i, t.coches.size()).clear();
        pl.trenes.put(n.id, n);
        pl.reindexar();
        t.aparecer();
        n.aparecer();
        pl.guardar();
        Location l = t.lugar(c);
        if (l != null) l.getWorld().playSound(l, Sound.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1f, 0.8f);
        p.sendMessage(ChatColor.YELLOW + "Soltados " + n.coches.size() + " vagones.");
    }
}
