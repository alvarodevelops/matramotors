package matracraft.matrains;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un tren: la locomotora (coche 0) y sus vagones. Cada coche se apoya en dos ejes (delante y detrás)
 * que avanzan por la vía la misma distancia cada tick, así el tren se dobla en las curvas sin que los
 * vagones se monten unos en otros.
 */
final class Tren {

    /** Distancia del eje al extremo del coche. */
    static final double EJE = 0.6;

    static final class Coche {
        final String tipo;
        final boolean locomotora;
        final double largo;
        final int asientos;
        final int aspecto;
        Via.Pos delante, detras;
        /** Carga de los vagones de mercancías (null si no lleva) y, en la cisterna, líquido y cubos. */
        org.bukkit.inventory.Inventory carga;
        String fluido;
        int cubos;
        /** Combustible de la locomotora: piezas de carbón o litros de gasóleo. */
        double combustible;
        /** Locomotora: código de su llave y número de cerradura (al cambiarla, las copias viejas dejan de valer). */
        String llave;
        int cerradura;
        // lo que se ve (solo existe con jugadores cerca)
        ArmorStand base;
        final List<ArmorStand> sillas = new ArrayList<>();
        final List<Interaction> cajas = new ArrayList<>();
        final List<BlockDisplay> piezas = new ArrayList<>();

        Coche(String tipo, boolean locomotora, double largo, int asientos, int aspecto) {
            this.tipo = tipo; this.locomotora = locomotora; this.largo = largo; this.asientos = asientos; this.aspecto = aspecto;
        }
    }

    final Matrains pl;
    final String id;
    UUID dueno;
    String nombre;
    /** Tipo de locomotora; null si son vagones sueltos. */
    String tipo;
    final List<Coche> coches = new ArrayList<>();
    /** Velocidad en bloques por tick; positiva = hacia delante. */
    double v;
    boolean visible, recolocar, sinSaldo;
    // ---- automático (parte 4)
    boolean auto, viajando, virtual;
    final List<Integer> ruta = new ArrayList<>();
    String modo = "ida-vuelta";
    int espera = 30;
    double tarifa;
    int idx = -1, dir = 1, idxDestino, sentido = 1, destino = -1, origen = -1;
    long paradoHasta, llegadaVirtual, ultimoAviso;
    Map<Long, BlockFace> plan;
    double restante = Double.NaN, recorrido, recorridoTramo;
    /** Recorrido del tren cuando subió cada pasajero (para los premios). */
    final Map<UUID, Double> subidos = new java.util.HashMap<>();
    /** Luz gastada y aún sin cobrar, y lo pagado en total (Ⓜ). */
    double deudaLuz, facturaLuz;
    int ticks, bocina;
    private double limite = Double.MAX_VALUE;
    /** Agujas que ya ha tomado el primer eje: el resto del tren va por el mismo sitio. */
    private final LinkedHashMap<Long, BlockFace> agujas = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, BlockFace> e) { return size() > 256; }
    };

    Tren(Matrains pl, String id, UUID dueno, String tipo, String nombre) {
        this.pl = pl; this.id = id; this.dueno = dueno; this.tipo = tipo; this.nombre = nombre;
    }

    Coche locomotora() { return coches.get(0); }

    boolean conLoco() { return tipo != null && !coches.isEmpty() && coches.get(0).locomotora; }

    /** Lo que pesa lo que arrastra la locomotora (vagones según lo llenos que vayan). */
    double peso() {
        double total = 0;
        for (Coche c : coches) {
            if (c.locomotora) continue;
            Tipos.Vagon tv = pl.tipos.vagon(c.tipo);
            if (tv == null) continue;
            total += tv.pesoVacio() + (tv.pesoLleno() - tv.pesoVacio()) * ocupacion(c, tv);
        }
        return total;
    }

    static double ocupacion(Coche c, Tipos.Vagon tv) {
        if (tv.cisterna()) return tv.huecos() <= 0 ? 0 : Math.min(1, c.cubos / (double) tv.huecos());
        if (c.carga == null) return 0;
        double lleno = 0;
        for (org.bukkit.inventory.ItemStack it : c.carga.getContents())
            if (it != null && !it.getType().isAir()) lleno += it.getAmount() / (double) it.getMaxStackSize();
        return Math.min(1, lleno / Math.max(1, c.carga.getSize()));
    }

    // ------------------------------------------------------------------ movimiento por la vía

    static long clave(Block b, BlockFace entrada) {
        return ((long) b.getX() & 0x3FFFFFF) << 38 | ((long) b.getZ() & 0x3FFFFFF) << 12 | ((long) (b.getY() + 64) & 0x3FF) << 2
                | (entrada.ordinal() & 3);
    }

    /** Salida de un bloque: el primer eje decide (y lo apunta); los demás repiten lo apuntado. */
    private BlockFace salida(Block b, BlockFace entrada, boolean primero, int preferencia) {
        long k = clave(b, entrada);
        if (primero && plan != null) {
            BlockFace pl0 = plan.get(k);
            if (pl0 != null && Via.vecino(b, pl0) != null) { agujas.put(k, pl0); return pl0; }
        }
        if (!primero) {
            BlockFace ya = agujas.get(k);
            if (ya != null && Via.vecino(b, ya) != null) return ya;
        }
        BlockFace s = Via.elegir(b, entrada, primero ? preferencia : 0);
        if (s != null && primero) agujas.put(k, s);
        return s;
    }

    /** Avanza una posición d bloques por la vía (negativo = hacia atrás). Devuelve lo que ha podido avanzar. */
    double avanzar(Via.Pos p, double d, boolean primero, int preferencia) {
        double pedido = d;
        for (int seguro = 0; seguro < 400; seguro++) {
            Block b = p.bloque();
            if (b == null) return pedido - d;
            if (d >= 0) {
                double resto = 1 - p.t;
                if (d < resto) { p.t += d; return pedido; }
                Block n = Via.vecino(b, p.hacia);
                BlockFace entra = p.hacia.getOppositeFace();
                BlockFace sale = n == null ? null : salida(n, entra, primero, preferencia);
                if (sale == null) { p.t = 1 - 1e-6; return pedido - d + resto; }
                d -= resto;
                p.poner(n, entra, sale, 0);
            } else {
                if (-d <= p.t) { p.t += d; return pedido; }
                Block n = Via.vecino(b, p.desde);
                BlockFace entra = p.desde.getOppositeFace();
                BlockFace sale = n == null ? null : salida(n, entra, primero, preferencia);
                if (sale == null) { double hecho = p.t; p.t = 0; return pedido - d - hecho; }
                d += p.t;
                p.poner(n, sale, entra, 1 - 1e-9);
            }
        }
        return pedido - d;
    }

    /** Mueve todo el tren; devuelve lo que se ha movido de verdad (menos si se acaba la vía). */
    private double mover(double d, int preferencia) {
        Via.Pos primero = d >= 0 ? locomotora().delante : coches.get(coches.size() - 1).detras;
        double hecho = avanzar(primero, d, true, preferencia);
        for (Coche c : coches) {
            if (c.delante != primero) avanzar(c.delante, hecho, false, 0);
            if (c.detras != primero) avanzar(c.detras, hecho, false, 0);
        }
        return hecho;
    }

    /**
     * Mira la vía por delante y calcula la velocidad máxima ahora mismo para llegar bien a las curvas
     * y al final de la vía frenando sin tirones.
     */
    private double mirarDelante(double sentido, double vmax, double fren) {
        double necesita = v * v / (2 * fren) + 6;
        int pasos = (int) Math.min(140, Math.ceil(necesita));
        Via.Pos p = (sentido >= 0 ? locomotora().delante : coches.get(coches.size() - 1).detras).copia();
        double cerrada = pl.getConfig().getDouble("fisica.curva-cerrada", 7.0) / 20.0;
        double diagonal = pl.getConfig().getDouble("fisica.curva-diagonal", 0.75) * vmax;
        double lim = vmax;
        double[] dist = new double[pasos + 1];
        int[] giro = new int[pasos + 1];
        int n = 0;
        double recorrido = 0;
        for (int i = 0; i < pasos; i++) {
            Via.sinCargar = false;
            double hecho = Math.abs(avanzar(p, sentido, false, 0));
            recorrido += hecho;
            if (pl.ocupadoPorOtro(p, this)) {
                // otro tren delante: parar a 2 bloques
                lim = Math.min(lim, Math.sqrt(2 * fren * Math.max(0, recorrido - 2.5)));
                break;
            }
            if (hecho < 0.999 && Via.sinCargar) break; // más allá aún no está cargado: no se sabe, no se frena
            if (hecho < 0.999) {
                // fin de la vía: hay que llegar parado
                lim = Math.min(lim, Math.sqrt(2 * fren * Math.max(0, recorrido - 0.3)));
                break;
            }
            if (p.curva()) {
                BlockFace entra = sentido >= 0 ? p.desde : p.hacia, sale = sentido >= 0 ? p.hacia : p.desde;
                dist[n] = recorrido;
                giro[n++] = sale == Via.izquierda(entra.getOppositeFace()) ? -1 : 1;
            }
        }
        for (int i = 0; i < n; i++) {
            boolean suave = (i > 0 && giro[i - 1] != giro[i] && dist[i] - dist[i - 1] <= 1.6)
                    || (i + 1 < n && giro[i + 1] != giro[i] && dist[i + 1] - dist[i] <= 1.6);
            double tope = suave ? diagonal : Math.min(cerrada, vmax);
            lim = Math.min(lim, Math.sqrt(tope * tope + 2 * fren * Math.max(0, dist[i] - 1.0)));
        }
        return lim;
    }

    Player conductor() {
        Coche l = locomotora();
        if (l.base == null || !l.base.isValid()) return null;
        for (Entity e : l.base.getPassengers()) if (e instanceof Player p) return p;
        return null;
    }

    boolean ocupado() {
        for (Coche c : coches) {
            if (c.base != null && !c.base.getPassengers().isEmpty()) return true;
            for (ArmorStand s : c.sillas) if (!s.getPassengers().isEmpty()) return true;
        }
        return false;
    }

    /** Un tick de física y de dibujo. */
    void tick() {
        ticks++;
        if (bocina > 0) bocina--;
        Tipos.Loco t = conLoco() ? pl.tipos.loco(tipo) : null;
        if (t == null) {
            // vagones sueltos: quietos
            v = 0;
            if (visible) dibujar();
            return;
        }
        double vmax = t.velocidad() / 20.0, acel = t.aceleracion() / 400.0, fren = t.frenada() / 400.0;
        // la carga: por encima de la tracción va más lento y acelera peor
        double r = t.traccion() / Math.max(0.01, peso());
        boolean noPuede = r < 0.4;
        if (r < 1) { vmax *= Math.sqrt(r); acel *= r; }
        if (noPuede) acel = 0;
        if (ticks % 200 == 0) sinSaldo = false;
        boolean tira = pl.energia.puedeTirar(this, t);
        if (!tira) acel = 0;
        double roz = pl.getConfig().getDouble("fisica.rozamiento", 0.6) / 400.0;
        Player p = visible ? conductor() : null;
        int preferencia = 0;
        if (p != null) {
            org.bukkit.Input in = p.getCurrentInput();
            if (in.isLeft()) preferencia = -1; else if (in.isRight()) preferencia = 1;
            // cuesta arriba se corre menos
            Via.Pos guia = v >= 0 ? locomotora().delante : coches.get(coches.size() - 1).detras;
            Block b = guia.bloque();
            if (b != null && Via.sube(Via.forma(b)) == (v >= 0 ? guia.hacia : guia.desde)) vmax *= pl.getConfig().getDouble("fisica.cuesta", 0.8);
            if (in.isForward()) v = v < 0 ? Math.min(0, v + fren) : Math.min(vmax, v + acel);
            else if (in.isBackward()) v = v > 0 ? Math.max(0, v - fren)
                    : Math.max(-vmax * pl.getConfig().getDouble("fisica.marcha-atras", 0.3), v - acel);
            else v = v > 0 ? Math.max(0, v - roz) : Math.min(0, v + roz);
            if (in.isJump() && bocina == 0) {
                bocina = 50;
                p.getWorld().playSound(p.getLocation(), t.bocina(), SoundCategory.PLAYERS, 3f, t.tono());
            }
            if (ticks % 5 == 0) p.sendActionBar(net.kyori.adventure.text.Component.text(noPuede
                    ? "Demasiada carga: suelta vagones o vacíalos"
                    : !tira && (in.isForward() || in.isBackward()) ? pl.energia.motivo(this, t)
                    : String.format("%s  ·  %.0f bloques/s  ·  %s%s%s", nombre, Math.abs(v) * 20, pl.energia.indicador(this, t), v < 0 ? "  (marcha atrás)" : "",
                    coches.size() > 1 ? String.format("  ·  arrastra %.1f de %.0f%s", t.traccion() / r, t.traccion(), r < 1 ? " (va más lento)" : "") : "")));
        } else if (auto) {
            Auto.paso(pl, this, vmax, acel, fren, tira);
        } else {
            // sin maquinista el tren frena solo
            v = v > 0 ? Math.max(0, v - fren) : Math.min(0, v + fren);
        }
        if (v != 0) {
            if (ticks % 3 == 0 || limite == Double.MAX_VALUE) limite = mirarDelante(Math.signum(v), vmax, fren);
            if (Math.abs(v) > limite) v = Math.signum(v) * Math.max(limite, Math.abs(v) - fren * 1.5);
            double hecho = mover(v, preferencia);
            pl.energia.consumir(this, t, Math.abs(hecho));
            recorrido += Math.abs(hecho);
            if (viajando) {
                recorridoTramo += Math.abs(hecho);
                if (!Double.isNaN(restante) && Math.signum(hecho) == sentido) restante -= Math.abs(hecho);
            }
            if (Math.abs(hecho) < Math.abs(v) - 1e-6) {
                v = 0;
                if (p != null) p.sendMessage(org.bukkit.ChatColor.GRAY + "Fin de la vía.");
                if (viajando && p == null) plan = null;
            }
            if (visible && ticks % 30 == 0) {
                Location l = lugar(locomotora());
                if (l != null) l.getWorld().playSound(l, Sound.ENTITY_MINECART_RIDING, SoundCategory.NEUTRAL, 0.5f, 0.7f + (float) (Math.abs(v) / vmax) * 0.6f);
            }
        } else limite = Double.MAX_VALUE;
        if (visible) {
            dibujar();
            if (tira) Efectos.particulas(this, t, p != null && p.getCurrentInput().isForward());
            if (ticks % 100 == 0) Efectos.pasajeros(this);
        }
    }

    /** Para los paneles de salidas: cuándo pasa este tren por la estación. */
    String cuandoLlega(int est) {
        if (!viajando) {
            if (idx >= 0 && idx < ruta.size() && ruta.get(idx) == est) {
                long s = Math.max(0, (paradoHasta - System.currentTimeMillis()) / 1000);
                return paradoHasta == Long.MAX_VALUE ? "fin" : "andén " + s + "s";
            }
            return "parado";
        }
        if (destino != est) return "luego";
        if (virtual) return Math.max(0, (llegadaVirtual - System.currentTimeMillis()) / 1000) + "s";
        if (!Double.isNaN(restante) && Math.abs(v) > 0.02) return (int) (restante / (Math.abs(v) * 20)) + "s";
        return "en camino";
    }

    // ------------------------------------------------------------------ lo que se ve

    /** Centro del coche con su orientación (yaw y pitch), o null si la vía ya no está. */
    Location lugar(Coche c) {
        World w = c.delante.world();
        if (w == null || c.delante.bloque() == null || c.detras.bloque() == null) return null;
        Vector a = c.delante.punto(), b = c.detras.punto();
        Vector d = a.clone().subtract(b);
        double horiz = Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ());
        float yaw = horiz < 1e-6 ? 0 : (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.getY(), Math.max(horiz, 1e-6)));
        Vector m = a.add(b).multiply(0.5);
        return new Location(w, m.getX(), m.getY() + 0.1, m.getZ(), yaw, pitch);
    }

    /** Punto del coche: lateral (izquierda +), frente y alto, girado con el coche. */
    static Location relativo(Location centro, double lateral, double frente, double alto) {
        double r = Math.toRadians(centro.getYaw());
        double fx = -Math.sin(r), fz = Math.cos(r);
        return new Location(centro.getWorld(), centro.getX() + fx * frente + fz * lateral, centro.getY() + alto,
                centro.getZ() + fz * frente - fx * lateral, centro.getYaw(), 0);
    }

    /** Puntos a lo largo del coche donde van las cajas de clic (cada una de 1,5 bloques). */
    private static double[] tramos(Coche c) {
        int n = Math.max(1, (int) Math.ceil(c.largo / 1.5));
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = -c.largo / 2 + c.largo * (i + 0.5) / n;
        return r;
    }

    boolean cargado() {
        for (Coche c : coches) if (!c.delante.cargado() || !c.detras.cargado()) return false;
        return true;
    }

    void aparecer() {
        if (visible) return;
        for (Coche c : coches) {
            Location l = lugar(c);
            if (l == null) continue;
            double[][] sitios = Modelos.asientos(c.tipo, c.asientos);
            c.base = silla(relativo(l, sitios[0][0], sitios[0][1], sitios[0][2]));
            pl.porEntidad.put(c.base.getUniqueId(), c);
            for (int i = 1; i < sitios.length; i++) c.sillas.add(silla(relativo(l, sitios[i][0], sitios[i][1], sitios[i][2])));
            for (double f : tramos(c)) {
                Interaction caja = l.getWorld().spawn(relativo(l, 0, f, 0), Interaction.class, in -> {
                    in.setPersistent(false);
                    in.setInteractionWidth(1.5f);
                    in.setInteractionHeight(2.2f);
                    in.setResponsive(true);
                });
                c.cajas.add(caja);
                pl.porEntidad.put(caja.getUniqueId(), c);
            }
            Modelos.Aspecto asp = Modelos.aspecto(c.tipo, c.aspecto);
            for (Modelos.Pieza q : Modelos.piezas(c.tipo, c.aspecto)) {
                c.piezas.add(l.getWorld().spawn(l, BlockDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setBlock(Modelos.material(q.c(), asp).createBlockData());
                    d.setTransformation(q.transformacion());
                    d.setTeleportDuration(2);
                    d.setViewRange(1.5f);
                    if (Modelos.luz(q.c())) d.setBrightness(new Display.Brightness(15, 15));
                }));
            }
        }
        visible = true;
        dibujar();
    }

    private ArmorStand silla(Location l) {
        return l.getWorld().spawn(l, ArmorStand.class, a -> {
            a.setPersistent(false);
            a.setInvisible(true);
            a.setMarker(true);
            a.setSmall(true);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.setBasePlate(false);
        });
    }

    void desaparecer() {
        for (Coche c : coches) {
            if (c.base != null) { pl.porEntidad.remove(c.base.getUniqueId()); c.base.remove(); c.base = null; }
            for (Interaction caja : c.cajas) { pl.porEntidad.remove(caja.getUniqueId()); caja.remove(); }
            c.cajas.clear();
            for (ArmorStand s : c.sillas) s.remove();
            c.sillas.clear();
            for (BlockDisplay d : c.piezas) d.remove();
            c.piezas.clear();
        }
        visible = false;
    }

    private void dibujar() {
        for (Coche c : coches) {
            if (c.base == null || !c.base.isValid() || c.cajas.isEmpty() || !c.cajas.get(0).isValid()) {
                // se descargó la zona: se quita lo que quede y ya volverá a aparecer
                desaparecer();
                return;
            }
            Location l = lugar(c);
            if (l == null) continue;
            double[][] sitios = Modelos.asientos(c.tipo, c.asientos);
            llevar(c.base, relativo(l, sitios[0][0], sitios[0][1], sitios[0][2]));
            for (int i = 0; i < c.sillas.size(); i++) llevar(c.sillas.get(i), relativo(l, sitios[i + 1][0], sitios[i + 1][1], sitios[i + 1][2]));
            double[] fs = tramos(c);
            for (int i = 0; i < c.cajas.size() && i < fs.length; i++) c.cajas.get(i).teleport(relativo(l, 0, fs[i], 0));
            for (BlockDisplay d : c.piezas) d.teleport(l);
        }
    }

    private static void llevar(Entity e, Location l) {
        if (e.getPassengers().isEmpty()) e.teleport(l);
        else e.teleport(l, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    /** Sube a un jugador al coche: en la locomotora conduce (si puede); en un vagón, al primer asiento libre. */
    void subir(Player p, Coche c) {
        if (p.getVehicle() != null) return;
        if (c.locomotora) {
            if (!pl.puedeUsar(p, this)) {
                p.sendMessage(org.bukkit.ChatColor.RED + "Este tren es de otro jugador (necesitas su llave)."); return;
            }
            if (!c.base.getPassengers().isEmpty()) { p.sendMessage(org.bukkit.ChatColor.RED + "Ya hay un maquinista."); return; }
            c.base.addPassenger(p);
            p.sendMessage(org.bukkit.ChatColor.GREEN + "W acelera · S frena / marcha atrás · A y D eligen vía en los desvíos · Espacio pita · Mayúsculas para bajarte");
            return;
        }
        if (c.asientos <= 0) { p.sendMessage(org.bukkit.ChatColor.GRAY + "Este vagón no lleva pasajeros."); return; }
        if (Math.abs(v) > 0.1) { p.sendMessage(org.bukkit.ChatColor.RED + "Espera a que el tren pare (o vaya muy despacio)."); return; }
        ArmorStand libre = c.base.getPassengers().isEmpty() ? c.base : null;
        if (libre == null) for (ArmorStand s : c.sillas) if (s.getPassengers().isEmpty()) { libre = s; break; }
        if (libre == null) { p.sendMessage(org.bukkit.ChatColor.RED + "El vagón va lleno."); return; }
        if (!Auto.cobrar(pl, this, p)) return;
        libre.addPassenger(p);
        subidos.put(p.getUniqueId(), recorrido);
    }
}
