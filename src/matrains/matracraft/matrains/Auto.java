package matracraft.matrains;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Trenes automáticos: recorren su ruta de estación en estación (ida y vuelta, solo ida o en bucle),
 * buscan solos el camino en los desvíos, frenan para parar junto a la campana, esperan el tiempo
 * configurado y siguen. Solo andan si el dueño está conectado o lleva a alguien; si no, se quedan en la
 * estación. Sin nadie cerca no se dibujan: el viaje se "salta" y el tren aparece en destino a su hora
 * (cuando ese tramo ya se ha hecho una vez a la vista).
 */
final class Auto {

    private Auto() {}

    /** Tramos aprendidos: "origen>destino" → largo y foto de los ejes al llegar. */
    static final Map<String, Tramo> TRAMOS = new HashMap<>();

    record Tramo(double largo, List<String> ejes) {}

    static boolean permitido(Matrains pl, Tren t) {
        return Bukkit.getPlayer(t.dueno) != null || t.ocupado();
    }

    // ------------------------------------------------------------------ conducción

    /** Mando automático de este tick: ajusta t.v. */
    static void paso(Matrains pl, Tren t, double vmax, double acel, double fren, boolean tira) {
        long ahora = System.currentTimeMillis();
        if (!t.viajando) {
            t.v = t.v > 0 ? Math.max(0, t.v - fren) : Math.min(0, t.v + fren);
            if (t.v == 0 && ahora >= t.paradoHasta && !t.ruta.isEmpty() && permitido(pl, t)) partir(pl, t);
            return;
        }
        if (t.plan == null || t.ticks % 40 == 0) planificar(pl, t, false);
        double meta = t.sentido * vmax;
        if (!Double.isNaN(t.restante)) {
            double tope = Math.sqrt(2 * fren * Math.max(0, t.restante - 0.3));
            meta = t.sentido * Math.min(vmax, tope);
            if (t.restante <= 0.35 && Math.abs(t.v) < 0.03) { t.v = 0; llegar(pl, t); return; }
        }
        double a = tira ? acel : 0;
        if (t.sentido > 0) t.v = t.v < meta ? Math.min(meta, t.v + a) : Math.max(meta, t.v - fren);
        else t.v = t.v > meta ? Math.max(meta, t.v - a) : Math.min(meta, t.v + fren);
        // llegada por si acaso: la locomotora ya está en la estación y el tren parado
        Estaciones.Estacion d = pl.estaciones.todas.get(t.destino);
        if (d != null && Math.abs(t.v) < 0.005 && Double.isNaN(t.restante)) {
            Location l = t.lugar(t.locomotora());
            if (l != null && d.dentro(l.getWorld().getName(), l.getX(), l.getY(), l.getZ())) llegar(pl, t);
        }
    }

    /** Sale hacia la siguiente estación de la ruta. */
    static void partir(Matrains pl, Tren t) {
        int n = t.ruta.size();
        if (n == 0) return;
        // sin tocar bloques (puede estar en una zona sin cargar): con las coordenadas guardadas
        Via.Pos q = t.locomotora().delante;
        Estaciones.Estacion aqui = null;
        for (Estaciones.Estacion e : pl.estaciones.todas.values()) if (e.dentro(q.mundo, q.x + 0.5, q.y + 0.5, q.z + 0.5)) { aqui = e; break; }
        int sig;
        switch (t.modo) {
            case "bucle" -> sig = (t.idx + 1) % n;
            case "solo-ida" -> {
                if (t.idx >= n - 1) { t.paradoHasta = Long.MAX_VALUE; return; }
                sig = t.idx + 1;
            }
            default -> {
                if (n == 1) sig = 0;
                else {
                    if (t.idx + t.dir >= n || t.idx + t.dir < 0) t.dir = -t.dir;
                    sig = Math.max(0, Math.min(n - 1, t.idx + t.dir));
                }
            }
        }
        // si ya está en esa estación, a la siguiente
        if (aqui != null && t.ruta.get(sig) == aqui.id && n > 1) { t.idx = sig; partir(pl, t); return; }
        t.destino = t.ruta.get(sig);
        t.idxDestino = sig;
        t.origen = aqui == null ? -1 : aqui.id;
        t.recorridoTramo = 0;
        t.plan = null;
        t.restante = Double.NaN;
        t.viajando = true;
        if (t.visible) {
            if (!planificar(pl, t, true)) {
                t.viajando = false;
                t.paradoHasta = System.currentTimeMillis() + 30_000;
                avisar(pl, t, "no encuentra vía hasta " + nombre(pl, t.destino) + "; lo vuelve a intentar en 30 s.");
                return;
            }
            silbar(t);
        } else {
            Tramo tr = TRAMOS.get(t.origen + ">" + t.destino);
            if (tr == null || tr.ejes.size() != t.coches.size() * 2) {
                t.viajando = false;
                t.paradoHasta = System.currentTimeMillis() + 60_000;
                avisar(pl, t, "aún no conoce el tramo hasta " + nombre(pl, t.destino) + ": la primera vez tiene que hacerlo con alguien cerca.");
                return;
            }
            Tipos.Loco loco = pl.tipos.loco(t.tipo);
            double vel = Math.max(2, loco.velocidad() * 0.8);
            t.virtual = true;
            t.llegadaVirtual = System.currentTimeMillis() + (long) (tr.largo / vel * 1000) + 3000;
        }
    }

    /** Viaje sin nadie cerca: a su hora, el tren aparece en destino. */
    static void virtual(Matrains pl, Tren t) {
        if (!t.virtual || System.currentTimeMillis() < t.llegadaVirtual) return;
        Tramo tr = TRAMOS.get(t.origen + ">" + t.destino);
        t.virtual = false;
        if (tr == null || tr.ejes.size() != t.coches.size() * 2) { t.viajando = false; return; }
        Tipos.Loco loco = pl.tipos.loco(t.tipo);
        if (loco != null) pl.energia.consumir(t, loco, tr.largo);
        for (int i = 0; i < t.coches.size(); i++) {
            t.coches.get(i).delante = Via.Pos.leer(tr.ejes.get(i * 2));
            t.coches.get(i).detras = Via.Pos.leer(tr.ejes.get(i * 2 + 1));
        }
        t.recorrido += tr.largo;
        llegar(pl, t);
    }

    static void llegar(Matrains pl, Tren t) {
        t.viajando = false;
        t.virtual = false;
        t.v = 0;
        t.plan = null;
        t.restante = Double.NaN;
        t.idx = t.idxDestino;
        t.paradoHasta = System.currentTimeMillis() + t.espera * 1000L;
        // aprende el tramo (largo y dónde quedan los ejes) para hacerlo sin nadie cerca
        if (t.origen >= 0 && t.recorridoTramo > 1) {
            List<String> ejes = new ArrayList<>();
            for (Tren.Coche c : t.coches) { ejes.add(c.delante.guardar()); ejes.add(c.detras.guardar()); }
            TRAMOS.put(t.origen + ">" + t.destino, new Tramo(t.recorridoTramo, ejes));
        }
        Location l = t.visible ? t.lugar(t.locomotora()) : null;
        if (l != null) l.getWorld().playSound(l, Sound.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 1.2f, 1.1f);
        for (Tren.Coche c : t.coches) for (Player p : Efectos.viajeros(c)) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("🚉 " + nombre(pl, t.destino)));
            premios(pl, t, c, p);
        }
        pl.guardar();
    }

    private static void silbar(Tren t) {
        Location l = t.lugar(t.locomotora());
        if (l == null) return;
        Tipos.Loco loco = t.pl.tipos.loco(t.tipo);
        if (loco != null) l.getWorld().playSound(l, loco.bocina(), SoundCategory.NEUTRAL, 2.5f, loco.tono());
    }

    static String nombre(Matrains pl, int id) {
        Estaciones.Estacion e = pl.estaciones.todas.get(id);
        return e == null ? "?" : e.nombre;
    }

    private static void avisar(Matrains pl, Tren t, String que) {
        Player d = Bukkit.getPlayer(t.dueno);
        if (d != null && System.currentTimeMillis() - t.ultimoAviso > 60_000) {
            t.ultimoAviso = System.currentTimeMillis();
            d.sendMessage(ChatColor.GOLD + "🚆 " + t.nombre + ChatColor.GRAY + " " + que);
        }
    }

    // ------------------------------------------------------------------ buscar camino

    private record Nodo(Block b, BlockFace entrada, double dist, long padre, BlockFace salidaPadre) {}

    private static long clave(Block b, BlockFace e) { return Tren.clave(b, e); }

    /**
     * Busca el camino hasta la estación destino por la vía cargada. Si la estación está más lejos de lo
     * cargado, va hacia el borde que quede más cerca de ella (se vuelve a calcular cada 2 segundos).
     * Con 'elegirSentido' prueba hacia delante y hacia atrás.
     */
    static boolean planificar(Matrains pl, Tren t, boolean elegirSentido) {
        Estaciones.Estacion d = pl.estaciones.todas.get(t.destino);
        if (d == null) return false;
        Resultado mejor = null;
        int[] sentidos = elegirSentido ? new int[]{1, -1} : new int[]{t.sentido};
        for (int s : sentidos) {
            Resultado r = buscar(pl, t, s, d);
            if (r == null) continue;
            if (mejor == null || (r.llega && !mejor.llega) || (r.llega == mejor.llega && r.coste < mejor.coste)) mejor = r;
        }
        if (mejor == null) return false;
        t.sentido = mejor.sentido;
        t.plan = mejor.plan;
        t.restante = mejor.llega ? mejor.dist : Double.NaN;
        return true;
    }

    private static final class Resultado {
        int sentido; boolean llega; double dist, coste; Map<Long, BlockFace> plan = new HashMap<>();
    }

    private static Resultado buscar(Matrains pl, Tren t, int sentido, Estaciones.Estacion d) {
        Via.Pos p = (sentido > 0 ? t.locomotora().delante : t.coches.get(t.coches.size() - 1).detras).copia();
        Block b0 = p.bloque();
        if (b0 == null) return null;
        BlockFace sal0 = sentido > 0 ? p.hacia : p.desde;
        double resto0 = sentido > 0 ? 1 - p.t : p.t;
        Location campana = d.campana();
        if (campana == null) return null;
        // ya dentro de la estación destino
        if (d.dentro(b0)) {
            Resultado r = new Resultado();
            r.sentido = sentido; r.llega = true; r.dist = parada(b0, sal0.getOppositeFace(), d, campana, 0, r.plan) + resto0;
            return r;
        }
        Map<Long, Nodo> vistos = new HashMap<>();
        ArrayDeque<Long> cola = new ArrayDeque<>();
        Block n0 = Via.vecino(b0, sal0);
        if (n0 == null) return null;
        long k0 = clave(n0, sal0.getOppositeFace());
        vistos.put(k0, new Nodo(n0, sal0.getOppositeFace(), resto0, -1, null));
        cola.add(k0);
        long meta = -1, borde = -1;
        double mejorBorde = Double.MAX_VALUE;
        int max = pl.getConfig().getInt("automatico.max-busqueda", 8000);
        while (!cola.isEmpty() && vistos.size() < max) {
            long k = cola.poll();
            Nodo n = vistos.get(k);
            if (d.dentro(n.b)) { meta = k; break; }
            List<BlockFace> sal = Via.salidas(n.b, n.entrada);
            boolean finCargado = false;
            for (BlockFace f : sal) {
                Via.sinCargar = false;
                Block sig = Via.vecino(n.b, f);
                if (sig == null) { if (Via.sinCargar) finCargado = true; continue; }
                long ks = clave(sig, f.getOppositeFace());
                if (vistos.containsKey(ks)) continue;
                vistos.put(ks, new Nodo(sig, f.getOppositeFace(), n.dist + 1, k, f));
                cola.add(ks);
            }
            if (finCargado) {
                double e = n.b.getLocation().distanceSquared(campana);
                if (e < mejorBorde) { mejorBorde = e; borde = k; }
            }
        }
        long fin = meta >= 0 ? meta : borde;
        if (fin < 0) return null;
        Resultado r = new Resultado();
        r.sentido = sentido;
        r.llega = meta >= 0;
        // reconstruir: en cada desvío, la salida que lleva al destino
        long k = fin;
        while (true) {
            Nodo n = vistos.get(k);
            if (n.padre < 0) break;
            Nodo pa = vistos.get(n.padre);
            if (Via.salidas(pa.b, pa.entrada).size() > 1) r.plan.put(n.padre, n.salidaPadre);
            k = n.padre;
        }
        Nodo f = vistos.get(fin);
        if (r.llega) r.dist = f.dist + parada(f.b, f.entrada, d, campana, 0, r.plan);
        else { r.dist = f.dist; r.coste = Math.sqrt(mejorBorde); }
        if (r.llega) r.coste = r.dist;
        return r;
    }

    /** Dentro de la estación, sigue la vía hasta el punto más cercano a la campana; devuelve lo que falta. */
    private static double parada(Block b, BlockFace entrada, Estaciones.Estacion d, Location campana, double base, Map<Long, BlockFace> plan) {
        double mejor = b.getLocation().add(0.5, 0, 0.5).distanceSquared(campana), en = 0.5, recorrido = 0;
        Block act = b;
        BlockFace ent = entrada;
        for (int i = 0; i < d.lado * 2; i++) {
            List<BlockFace> op = Via.salidas(act, ent);
            if (op.isEmpty()) break;
            // en la estación, la salida que más se acerca a la campana
            BlockFace elegida = op.get(0);
            double mejorOp = Double.MAX_VALUE;
            for (BlockFace f : op) {
                Block s = Via.vecino(act, f);
                if (s == null) continue;
                double x = s.getLocation().add(0.5, 0, 0.5).distanceSquared(campana);
                if (x < mejorOp) { mejorOp = x; elegida = f; }
            }
            if (op.size() > 1) plan.put(clave(act, ent), elegida);
            Block s = Via.vecino(act, elegida);
            if (s == null || !d.dentro(s)) break;
            recorrido += 1;
            double x = s.getLocation().add(0.5, 0, 0.5).distanceSquared(campana);
            if (x < mejor) { mejor = x; en = recorrido + 0.5; }
            ent = elegida.getOppositeFace();
            act = s;
        }
        return base + en;
    }

    // ------------------------------------------------------------------ billetes y premios

    /** Cobra el billete al subir a un tren automático ajeno: 70 % al dueño del tren y 30 % a la estación (si es de otro). */
    static boolean cobrar(Matrains pl, Tren t, Player p) {
        if (!t.auto || t.tarifa <= 0 || p.getUniqueId().equals(t.dueno) || pl.eco == null) return true;
        if (!pl.eco.has(p, t.tarifa)) { p.sendMessage(ChatColor.RED + "El billete cuesta " + fmt(t.tarifa) + " Ⓜ y no te llega."); return false; }
        pl.eco.withdrawPlayer(p, t.tarifa);
        Location l = t.lugar(t.locomotora());
        Estaciones.Estacion est = l == null ? null : pl.estaciones.en(l);
        double aEstacion = est != null && !est.dueno.equals(t.dueno) ? t.tarifa * pl.getConfig().getDouble("automatico.parte-estacion", 0.3) : 0;
        pl.eco.depositPlayer(Bukkit.getOfflinePlayer(t.dueno), t.tarifa - aEstacion);
        if (aEstacion > 0) pl.eco.depositPlayer(Bukkit.getOfflinePlayer(est.dueno), aEstacion);
        ItemStack billete = new ItemStack(Material.PAPER);
        ItemMeta m = billete.getItemMeta();
        m.setDisplayName(ChatColor.GOLD + "Billete · " + t.nombre);
        m.setLore(List.of(ChatColor.GRAY + "Desde: " + ChatColor.WHITE + (est == null ? "vía" : est.nombre),
                ChatColor.GRAY + "Hacia: " + ChatColor.WHITE + (t.viajando || t.destino > 0 ? nombre(pl, t.destino) : "próxima parada"),
                ChatColor.GRAY + "Precio: " + ChatColor.WHITE + fmt(t.tarifa) + " Ⓜ",
                ChatColor.DARK_GRAY + java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Madrid")).withNano(0).toString().replace('T', ' ')));
        billete.setItemMeta(m);
        p.getInventory().addItem(billete).values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
        p.sendMessage(ChatColor.GREEN + "Billete pagado: " + fmt(t.tarifa) + " Ⓜ.");
        return true;
    }

    static String fmt(double x) { return x == Math.floor(x) ? String.valueOf((long) x) : String.format("%.2f", x); }

    /** Coche restaurante y Pullman: premio al llegar, con trayecto mínimo y una vez por hora. */
    private static void premios(Matrains pl, Tren t, Tren.Coche c, Player p) {
        boolean regalo = c.tipo.equals("regalo"), restaurante = c.tipo.equals("restaurante");
        if (!regalo && !restaurante) return;
        Double desde = t.subidos.get(p.getUniqueId());
        if (desde == null) return;
        double hecho = t.recorrido - desde;
        double minimo = pl.getConfig().getDouble(regalo ? "automatico.regalo-minimo" : "automatico.restaurante-minimo", regalo ? 500 : 300);
        if (hecho < minimo) return;
        String clave = p.getUniqueId() + (regalo ? ":regalo" : ":restaurante");
        Long ult = pl.esperas.get(clave);
        if (ult != null && System.currentTimeMillis() - ult < 3_600_000L) return;
        pl.esperas.put(clave, System.currentTimeMillis());
        t.subidos.put(p.getUniqueId(), t.recorrido);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ItemStack premio;
        if (restaurante) {
            Material[] platos = {Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.BAKED_POTATO, Material.PUMPKIN_PIE, Material.COOKED_SALMON, Material.RABBIT_STEW};
            premio = new ItemStack(platos[r.nextInt(platos.length)], platos.length > 0 ? 3 : 1);
            p.sendMessage(ChatColor.GOLD + "🍽 El coche restaurante te invita a comer.");
        } else {
            premio = new ItemStack(Material.POTION);
            PotionMeta pm = (PotionMeta) premio.getItemMeta();
            String[][] bebidas = {{"Anís del Faro", "SPEED"}, {"Licor de la Matraca", "STRENGTH"}, {"Orujo de Villarroble", "REGENERATION"},
                    {"Vino de la Vega", "HASTE"}, {"Mistela del Cortijo", "LUCK"}};
            String[] b = bebidas[r.nextInt(bebidas.length)];
            pm.setDisplayName(ChatColor.LIGHT_PURPLE + b[0]);
            PotionEffectType tipo = org.bukkit.Registry.EFFECT.get(org.bukkit.NamespacedKey.minecraft(b[1].toLowerCase()));
            if (tipo != null) pm.addCustomEffect(new PotionEffect(tipo, 20 * 180, 0), true);
            pm.addCustomEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 8, 0), true);
            pm.setColor(org.bukkit.Color.fromRGB(r.nextInt(80, 255), r.nextInt(40, 200), r.nextInt(80, 255)));
            premio.setItemMeta(pm);
            p.sendMessage(ChatColor.LIGHT_PURPLE + "🎁 Regalo del coche Pullman: " + b[0] + ".");
        }
        p.getInventory().addItem(premio).values().forEach(x -> p.getWorld().dropItem(p.getLocation(), x));
    }
}
