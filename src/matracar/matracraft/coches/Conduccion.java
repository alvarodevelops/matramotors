package matracraft.coches;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Minecart;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Conducción de los coches (vagonetas con matrícula): sacarlos, montarse, moverlos con
 * las teclas, gasolina, averías, choques, atropellos, claxon y el HUD.
 */
final class Conduccion implements Listener {

    // Lo que se sabe del coche mientras alguien lo conduce
    static final class Estado {
        final ArmorStand cart; final Tier tier; final String matricula;
        double velocidad = 0; // bloques por tick; negativa = marcha atrás
        float yaw;
        double gasolina, salud;
        int saltoCd = 0, claxonCd = 0, atascado = 0, ticks = 0;
        boolean avisoReserva = false, avisoAveria = false;
        int golpeCd = 0, giroFuerte = 0;
        double alturaCaida = Double.NaN; // altura desde la que empezó a caer (NaN = no está cayendo)
        Location ultima;
        final Map<UUID, Integer> golpeados = new HashMap<>();
        Estado(ArmorStand cart, Tier tier, String matricula, float yaw, double gasolina, double salud) {
            this.cart = cart; this.tier = tier; this.matricula = matricula; this.yaw = yaw; this.gasolina = gasolina; this.salud = salud; this.ultima = cart.getLocation();
        }
    }

    private final Coches pl;
    final Map<UUID, Estado> conduciendo = new HashMap<>();

    Conduccion(Coches pl) { this.pl = pl; }

    // ---------- utilidades ----------

    boolean esCoche(Entity e) {
        return e instanceof ArmorStand && e.getPersistentDataContainer().has(pl.kMatricula, PersistentDataType.STRING);
    }

    Tier tierDe(Entity e) {
        String id = e.getPersistentDataContainer().get(pl.kTier, PersistentDataType.STRING);
        return id == null ? null : pl.tiers.get(id);
    }

    String matriculaDe(Entity e) {
        return e.getPersistentDataContainer().get(pl.kMatricula, PersistentDataType.STRING);
    }

    double gasolinaDe(Entity e) {
        Double g = e.getPersistentDataContainer().get(pl.kGasolina, PersistentDataType.DOUBLE);
        return g == null ? 0 : g;
    }

    /** Estado del coche (0-100). Si alguien lo conduce manda el valor en memoria. */
    double estadoDe(Entity e) {
        Estado st = conduciendo.get(e.getUniqueId());
        if (st != null) return st.salud;
        Double s = e.getPersistentDataContainer().get(pl.kEstado, PersistentDataType.DOUBLE);
        return s == null ? 100 : s;
    }

    void danarCoche(ArmorStand cart, double dano, String causa) {
        if (dano <= 0.5) return;
        Estado st = conduciendo.get(cart.getUniqueId());
        double antes = estadoDe(cart);
        double ahora = Math.max(0, antes - dano);
        if (st != null) st.salud = ahora;
        cart.getPersistentDataContainer().set(pl.kEstado, PersistentDataType.DOUBLE, ahora);
        if (!cart.getPassengers().isEmpty() && cart.getPassengers().get(0) instanceof Player p)
            p.sendMessage(ChatColor.RED + "💥 Daños por " + causa + ": " + ChatColor.WHITE + String.format(Locale.ROOT, "-%.0f %%", dano) + ChatColor.GRAY + String.format(Locale.ROOT, " (estado %.0f %%)",
                    ahora));
        // se acaba de romper
        if (ahora <= 0 && antes > 0) {
            cart.getWorld().spawnParticle(Particle.LARGE_SMOKE, cart.getLocation().add(0, 0.8, 0), 20, 0.3, 0.3, 0.3, 0.02);
            cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, SoundCategory.PLAYERS, 0.7f, 1.5f);
        }
    }

    // ---------- sacar el coche ----------

    // ignoreCancelled = false: Bukkit manda los clics al aire ya "cancelados" y hacen falta para echar barcos
    @EventHandler(priority = EventPriority.HIGH)
    public void alUsarItem(PlayerInteractEvent e) {
        if (e.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        if ((e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.MINECART || !item.hasItemMeta()) return;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String matricula = pdc.get(pl.kMatricula, PersistentDataType.STRING);
        if (matricula == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!pl.deEstaTanda(pdc)) {
            item.setAmount(0);
            p.sendMessage(ChatColor.RED + "Este coche es de antes del reinicio del parque de coches y ya no vale.");
            return;
        }
        if (pl.mundoProhibido(p.getWorld())) { p.sendMessage(ChatColor.RED + "Aquí no se pueden sacar coches."); return; }
        Tier tier = pl.tiers.get(pdc.get(pl.kTier, PersistentDataType.STRING));
        if (tier == null) { p.sendMessage(ChatColor.RED + "Este tipo de coche ya no existe en la configuración."); return; }
        Block donde;
        double altura = 0.05;
        if (tier.agua()) {
            // las embarcaciones se echan al agua: se busca la superficie a la que apunta
            org.bukkit.util.RayTraceResult rayo = p.rayTraceBlocks(6, org.bukkit.FluidCollisionMode.ALWAYS);
            Block agua = rayo == null ? null : rayo.getHitBlock();
            double nivel = agua == null ? Double.NaN : Agua.superficie(agua.getWorld(), agua.getX() + 0.5, agua.getY() + 0.5, agua.getZ() + 0.5);
            if (Double.isNaN(nivel)) { p.sendMessage(ChatColor.RED + "Apunta al agua para echar la " + tier.nombre() + "."); return; }
            donde = agua.getWorld().getBlockAt(agua.getX(), (int) Math.floor(nivel), agua.getZ());
            altura = nivel - Math.floor(nivel);
        } else {
            if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            donde = e.getClickedBlock().getRelative(e.getBlockFace());
        }
        if (!tier.agua() && (!donde.isPassable() || !donde.getRelative(BlockFace.UP).isPassable())) { p.sendMessage(ChatColor.RED + "No cabe aquí."); return; }
        Double gas = pdc.get(pl.kGasolina, PersistentDataType.DOUBLE);
        Double est = pdc.get(pl.kEstado, PersistentDataType.DOUBLE);
        double estado = est == null ? 100 : est;
        Location loc = donde.getLocation().add(0.5, altura, 0.5);
        loc.setYaw(p.getLocation().getYaw());
        double gasolina = gas == null ? 0 : gas;
        String asp = pdc.get(pl.kAspecto, PersistentDataType.STRING);
        if (Modelos.Aspecto.leer(asp) == null) asp = Modelos.azar(tier).guardar();
        String aspecto = asp;
        float yaw = p.getLocation().getYaw();
        ArmorStand cart = nuevaBase(loc, c -> {
            PersistentDataContainer d = c.getPersistentDataContainer();
            d.set(pl.kTier, PersistentDataType.STRING, tier.id());
            d.set(pl.kMatricula, PersistentDataType.STRING, matricula);
            d.set(pl.kGasolina, PersistentDataType.DOUBLE, gasolina);
            d.set(pl.kEstado, PersistentDataType.DOUBLE, estado);
            d.set(pl.kAspecto, PersistentDataType.STRING, aspecto);
            d.set(pl.kYaw, PersistentDataType.FLOAT, yaw);
            d.set(pl.kTanda, PersistentDataType.INTEGER, pl.registro.tanda());
            c.setCustomName(ChatColor.WHITE + "[" + matricula + "]");
            c.setCustomNameVisible(false);
        });
        frenoDeMano(cart);
        pl.modelos.montar(cart);
        pl.seguros.apuntarUbicacion(matricula, cart.getLocation());
        pl.registro.asegurar(matricula, tier, p.getName());
        item.setAmount(item.getAmount() - 1);
        p.playSound(loc, Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    private void preparar(ArmorStand cart, Tier tier) {
        gravedad(cart, tier);
    }

    /**
     * Base invisible de los vehículos: un soporte de armadura pequeño, invisible y escalado para que
     * el conductor quede a la misma altura que en las vagonetas de antes (las vagonetas no se pueden
     * hacer invisibles: el juego las dibuja igual).
     */
    ArmorStand nuevaBase(Location loc, java.util.function.Consumer<ArmorStand> datos) {
        double escala = pl.getConfig().getDouble("base.escala", 0.19);
        double gravedad = pl.getConfig().getDouble("base.gravedad", 0.04);
        return loc.getWorld().spawn(loc, ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setSmall(true);
            a.setBasePlate(false);
            a.setArms(false);
            a.setSilent(true);
            a.setInvulnerable(true);
            a.setCanPickupItems(false);
            a.setCollidable(false);
            a.setPersistent(true);
            a.setRemoveWhenFarAway(false);
            for (EquipmentSlot hueco : EquipmentSlot.values())
                for (ArmorStand.LockType tipo : ArmorStand.LockType.values()) {
                    try { a.addEquipmentLock(hueco, tipo); } catch (IllegalArgumentException ignorado) { }
                }
            atributo(a, Attribute.SCALE, escala);
            atributo(a, Attribute.STEP_HEIGHT, 0);
            atributo(a, Attribute.GRAVITY, gravedad);
            datos.accept(a);
        });
    }

    /**
     * Las embarcaciones no caen (flotan a la altura del agua), pero la base tiene que conservar la
     * gravedad "encendida": un soporte de armadura sin gravedad no se mueve en absoluto. Por eso se
     * pone a cero el atributo de gravedad en vez de quitarla.
     */
    void gravedad(ArmorStand cart, Tier tier) {
        cart.setGravity(true);
        atributo(cart, Attribute.GRAVITY, tier != null && tier.agua() ? 0 : pl.getConfig().getDouble("base.gravedad", 0.04));
    }

    /**
     * Altura del asiento del conductor: la base se escala para que el jugador quede sentado a esa altura
     * (un soporte pequeño mide 0,9875 a escala 1 y el pasajero se sienta encima).
     */
    void asiento(ArmorStand cart, double altura) {
        double escala = altura > 0 ? altura / 0.9875 : pl.getConfig().getDouble("base.escala", 0.19);
        atributo(cart, Attribute.SCALE, Math.max(0.0625, Math.min(2, escala)));
    }

    static boolean flota(ArmorStand cart) {
        AttributeInstance g = cart.getAttribute(Attribute.GRAVITY);
        return g != null && g.getBaseValue() <= 0;
    }

    private static void atributo(ArmorStand a, Attribute atributo, double valor) {
        AttributeInstance inst = a.getAttribute(atributo);
        if (inst != null) inst.setBaseValue(valor);
    }

    /** Cambia un vehículo antiguo (vagoneta) por la base invisible, con los mismos datos y pasajeros. */
    ArmorStand convertir(Minecart viejo) {
        List<Entity> pasajeros = new ArrayList<>(viejo.getPassengers());
        viejo.eject();
        Location loc = viejo.getLocation();
        PersistentDataContainer antes = viejo.getPersistentDataContainer();
        String nombre = viejo.getCustomName();
        ArmorStand base = nuevaBase(loc, a -> {
            antes.copyTo(a.getPersistentDataContainer(), true);
            a.setCustomName(nombre);
            a.setCustomNameVisible(false);
        });
        viejo.remove();
        for (Entity p : pasajeros) base.addPassenger(p);
        return base;
    }

    /** Deja el coche quieto: sin deslizarse en horizontal. */
    void frenoDeMano(ArmorStand cart) {
        // las embarcaciones no tienen gravedad: quietas del todo para que no se hundan
        cart.setVelocity(new Vector(0, flota(cart) ? 0 : Math.min(0, cart.getVelocity().getY()), 0));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alClicarCoche(PlayerInteractEntityEvent e) {
        // la base es invisible: un clic sobre ella vale lo mismo que sobre la carrocería
        if (esCoche(e.getRightClicked())) pl.modelos.clicar(e, (ArmorStand) e.getRightClicked());
    }

    // los soportes de armadura solo mandan este clic (con el punto exacto), no el normal
    @EventHandler(priority = EventPriority.HIGH)
    public void alClicarCocheEnPunto(org.bukkit.event.player.PlayerInteractAtEntityEvent e) {
        if (esCoche(e.getRightClicked())) pl.modelos.clicar(e, (ArmorStand) e.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alManipular(PlayerArmorStandManipulateEvent e) {
        if (esCoche(e.getRightClicked())) e.setCancelled(true);
    }

    // Mayúsculas + clic derecho: abre el maletero si lo tiene; si no, guarda el coche
    void clicConMayusculas(Player p, Entity ent) {
        Tier tier = tierDe(ent);
        if (tier == null) return;
        if (Barcos.volcada(pl, ent)) { Barcos.enderezar(pl, p, (ArmorStand) ent); return; }
        if (tier.carga() > 0) {
            String matricula = matriculaDe(ent);
            if (!pl.tieneLlave(p, matricula)) { p.sendMessage(ChatColor.RED + "El maletero está cerrado. Necesitas la llave de " + matricula + "."); return; }
            pl.carga.abrir(p, (ArmorStand) ent, tier);
            return;
        }
        guardarCoche(p, ent);
    }

    void guardarCoche(Player p, Entity ent) {
        String matricula = matriculaDe(ent);
        Tier tier = tierDe(ent);
        if (tier == null) return;
        if (!pl.tieneLlave(p, matricula)) { p.sendMessage(ChatColor.RED + "Necesitas la llave de " + matricula + " para guardar este vehículo."); return; }
        if (!ent.getPassengers().isEmpty()) { p.sendMessage(ChatColor.RED + "Hay alguien dentro."); return; }
        if (tier.carga() > 0 && !pl.carga.vacia(ent)) {
            p.sendMessage(ChatColor.RED + "Vacía el maletero antes de guardar el " + tier.nombre() + " (mayúsculas + clic derecho lo abre).");
            return;
        }
        conduciendo.remove(ent.getUniqueId());
        pl.gasolineras.parar(ent.getUniqueId(), null);
        pl.dar(p, pl.conAspecto(pl.itemCoche(tier, matricula, gasolinaDe(ent), estadoDe(ent)), ent.getPersistentDataContainer().get(pl.kAspecto, PersistentDataType.STRING)));
        pl.modelos.desmontar(ent.getUniqueId());
        ent.remove();
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1f, 0.8f);
        p.sendMessage(ChatColor.GRAY + "Has guardado el " + tier.nombre() + " " + matricula + ".");
    }

    // ---------- subir y bajar ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alMontar(EntityMountEvent e) {
        if (!esCoche(e.getMount())) return;
        if (!(e.getEntity() instanceof Player p)) { e.setCancelled(true); return; }
        String matricula = matriculaDe(e.getMount());
        if (!pl.tieneLlave(p, matricula)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Este coche está cerrado. Necesitas la llave de " + matricula + ".");
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.PLAYERS, 0.6f, 1.6f);
            return;
        }
        if (pl.mundoProhibido(p.getWorld())) { e.setCancelled(true); p.sendMessage(ChatColor.RED + "Aquí no se puede conducir."); return; }
        if (!pl.seguros.vigente(matricula)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Este coche no tiene seguro en vigor y no puede circular. " + ChatColor.YELLOW + "/coche seguro " + matricula.replace(" ", ""));
            return;
        }
        ArmorStand cart = (ArmorStand) e.getMount();
        Tier tier = tierDe(cart);
        if (tier == null) { e.setCancelled(true); return; }
        preparar(cart, tier);
        Float yawGuardado = cart.getPersistentDataContainer().get(pl.kYaw, PersistentDataType.FLOAT);
        float yaw = yawGuardado == null ? p.getLocation().getYaw() : yawGuardado;
        conduciendo.put(cart.getUniqueId(), new Estado(cart, tier, matricula, yaw, gasolinaDe(cart), estadoDe(cart)));
        if (estadoDe(cart) <= 0) p.sendMessage(ChatColor.DARK_RED + "El vehículo está AVERIADO. Guárdalo y usa /coche reparar.");
        if (Barcos.volcada(pl, cart)) { conduciendo.remove(cart.getUniqueId()); e.setCancelled(true); p.sendMessage(ChatColor.RED + "Está volcada. Mayúsculas + clic derecho para enderezarla."); return; }
        p.sendMessage(ChatColor.GRAY + "W acelerar · S frenar/atrás · A/D girar · espacio " + (tier.agua() ? "bocina" : "claxon") + " · mayúsculas bajarse");
        if (tier.taxi()) p.sendMessage(ChatColor.YELLOW + "🚕 Taxi: " + ChatColor.GRAY + "/taximetro para encenderlo o apagarlo.");
        if (tier.todoterreno()) p.sendMessage(ChatColor.GRAY + "Todoterreno: sube escalones de 1 bloque de tierra, arena, grava, nieve o piedra.");
    }

    @EventHandler(ignoreCancelled = true)
    public void alBajar(EntityDismountEvent e) {
        if (!esCoche(e.getDismounted())) return;
        ArmorStand cart = (ArmorStand) e.getDismounted();
        pl.gasolineras.parar(cart.getUniqueId(), e.getEntity() instanceof Player p ? p : null);
        Estado st = conduciendo.remove(cart.getUniqueId());
        if (st != null) guardar(st);
        frenoDeMano(cart);
        pl.seguros.apuntarUbicacion(matriculaDe(cart), cart.getLocation());
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        Entity veh = e.getPlayer().getVehicle();
        if (veh != null && esCoche(veh)) {
            pl.gasolineras.parar(veh.getUniqueId(), e.getPlayer());
            Estado st = conduciendo.remove(veh.getUniqueId());
            if (st != null) guardar(st);
            frenoDeMano((ArmorStand) veh);
        }
    }

    // la base no se rompe nunca (mayúsculas + golpe la guarda: Extra.alGolpear); la lava o el vacío son siniestro
    @EventHandler(priority = EventPriority.LOWEST)
    public void alDanar(EntityDamageEvent e) {
        if (!esCoche(e.getEntity())) return;
        e.setCancelled(true);
        ArmorStand cart = (ArmorStand) e.getEntity();
        cart.setFireTicks(0);
        if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent golpe && golpe.getDamager() instanceof Player) return;
        EntityDamageEvent.DamageCause causa = e.getCause();
        boolean lava = cart.getLocation().getBlock().getType() == Material.LAVA;
        if (lava || causa == EntityDamageEvent.DamageCause.LAVA || causa == EntityDamageEvent.DamageCause.VOID) {
            Tier tier = tierDe(cart);
            if (tier != null) pl.seguros.siniestro(cart, tier, matriculaDe(cart),
                    cart.getPassengers().isEmpty() ? null : cart.getPassengers().get(0) instanceof Player p ? p : null);
        }
    }

    // ---------- bucle de conducción ----------

    /**
     * Partículas: los coches echan humo por el escape (poco al ralentí, más al acelerar y negro si están
     * muy dañados); las embarcaciones salpican en la proa y dejan estela según la velocidad.
     */
    private void particulas(Estado st, ArmorStand cart, Tier tier, Location ahora, Vector dir, double rapidez, boolean acelera) {
        World w = cart.getWorld();
        double fr = rapidez / Math.max(0.01, tier.maxPorTick());
        if (tier.agua()) {
            if (rapidez < 0.02 || st.ticks % 2 != 0) return;
            Location proa = ahora.clone().add(dir.clone().multiply(1.2)).add(0, 0.05, 0);
            Location popa = ahora.clone().add(dir.clone().multiply(-1.6)).add(0, 0.02, 0);
            w.spawnParticle(Particle.SPLASH, proa, 2 + (int) (fr * 8), 0.35, 0.05, 0.35, 0.05);
            w.spawnParticle(Particle.FISHING, popa, 1 + (int) (fr * 3), 0.25, 0.01, 0.25, 0.01);
            if (fr > 0.6 && !tier.sinMotor()) w.spawnParticle(Particle.BUBBLE_POP, popa, 2, 0.2, 0.05, 0.2, 0.02);
            return;
        }
        if (tier.sinMotor() || st.gasolina <= 0) return;
        int cada = acelera ? 3 : 10;
        if (st.ticks % cada != 0) return;
        Location escape = ahora.clone().add(dir.clone().multiply(-1.3)).add(0, 0.25, 0);
        w.spawnParticle(Particle.SMOKE, escape, acelera ? 3 : 1, 0.05, 0.03, 0.05, 0.01);
        if (st.salud < 30 && st.ticks % 6 == 0)
            w.spawnParticle(Particle.LARGE_SMOKE, ahora.clone().add(dir.clone().multiply(0.9)).add(0, 0.7, 0), 2, 0.15, 0.1, 0.15, 0.01);
    }

    void tick() {
        for (Estado st : new ArrayList<>(conduciendo.values())) {
            ArmorStand cart = st.cart;
            if (!cart.isValid() || cart.getPassengers().isEmpty() || !(cart.getPassengers().get(0) instanceof Player p)) {
                conduciendo.remove(cart.getUniqueId());
                if (cart.isValid()) { guardar(st); frenoDeMano(cart); }
                continue;
            }
            conducir(st, p);
        }
    }

    private void conducir(Estado st, Player p) {
        ArmorStand cart = st.cart;
        Tier tier = st.tier;
        Input in = p.getCurrentInput();
        double max = tier.maxPorTick();
        boolean averiado = st.salud <= 0;
        boolean motor = (tier.sinMotor() || st.gasolina > 0.0001) && !averiado;

        if (cart.getLocation().getBlock().getType() == Material.LAVA || cart.getLocation().getY() < cart.getWorld().getMinHeight()) {
            pl.seguros.siniestro(cart, tier, st.matricula, p);
            return;
        }
        if (pl.mundoProhibido(cart.getWorld())) {
            st.velocidad = 0;
            cart.setVelocity(new Vector());
            if (st.ticks % 40 == 0) p.sendMessage(ChatColor.RED + "Aquí no se puede conducir.");
            st.ticks++;
            return;
        }
        if (averiado && !st.avisoAveria) {
            st.avisoAveria = true;
            p.sendMessage(ChatColor.DARK_RED + "🔧 ¡Avería! No anda. Bájate, guárdalo y usa /coche reparar" + (tier.agua() ? "." : " (o /coche grua)."));
        }
        if (!motor && !averiado && !tier.sinMotor() && st.ticks % 100 == 0 && tier.agua())
            p.sendMessage(ChatColor.GOLD + "⛽ Sin gasolina. Bájate, guárdala (mayúsculas + golpe) y ve a una gasolinera náutica.");
        st.ticks++;
        if (st.saltoCd > 0) st.saltoCd--;
        if (st.claxonCd > 0) st.claxonCd--;
        if (st.golpeCd > 0) st.golpeCd--;

        // firme, clima y chunks sin generar: cambian la velocidad máxima, la frenada y el giro
        World mundo = cart.getWorld();
        max *= Firme.factorSuelo(pl, tier, cart.getLocation()) * Firme.factorClima(pl, tier, mundo);
        double agarre = Firme.agarre(pl, tier, mundo);
        double frenada = tier.frenada() * agarre;
        {
            double radAntes = Math.toRadians(st.yaw);
            max = Math.min(max, Firme.topeChunks(pl, cart.getLocation(), new Vector(-Math.sin(radAntes), 0, Math.cos(radAntes)), st.velocidad));
        }
        // si va más rápido que lo que permite el firme, pierde velocidad poco a poco (no de golpe)
        if (st.velocidad > max) st.velocidad = Math.max(max, st.velocidad - Math.max(tier.frenada() * 0.5, 0.01));
        if (st.velocidad < -max * 0.35) st.velocidad = Math.min(-max * 0.35, st.velocidad + Math.max(tier.frenada() * 0.5, 0.01));

        // acelerar, frenar y marcha atrás
        if (in.isForward() && motor) {
            st.velocidad = st.velocidad < 0 ? Math.min(0, st.velocidad + frenada) : st.velocidad >= max ? st.velocidad : Math.min(max, st.velocidad + tier.aceleracion());
        } else if (in.isBackward()) {
            if (st.velocidad > 0) st.velocidad = Math.max(0, st.velocidad - frenada);
            else if (motor) st.velocidad = Math.max(-max * 0.35, st.velocidad - tier.aceleracion() * 0.6);
        } else {
            double roce = tier.agua() ? (motor ? 0.003 : 0.006) : (motor ? 0.004 : 0.008);
            st.velocidad = st.velocidad > 0 ? Math.max(0, st.velocidad - roce) : Math.min(0, st.velocidad + roce);
        }
        Block bloque = cart.getLocation().getBlock();
        if (!tier.agua() && (bloque.getType() == Material.WATER || bloque.isLiquid())) st.velocidad = Math.max(-0.05, Math.min(0.05, st.velocidad));

        // girar (más despacio cuanto más lento va)
        double rapidez = Math.abs(st.velocidad);
        int giro = in.isLeft() == in.isRight() ? 0 : in.isLeft() ? -1 : 1;
        if (rapidez > 0.005) {
            double factor = 0.35 + 0.65 * Math.min(1, rapidez / max);
            // el Taxi Boat prioriza la suavidad: a mucha velocidad gira menos
            if (tier.taxi() && tier.agua()) factor *= 1 - 0.35 * Math.min(1, rapidez / max);
            st.yaw += (float) (giro * tier.giro() * factor * (agarre < 1 ? 0.85 : 1) * (st.velocidad < 0 ? -1 : 1));
        }
        double rad = Math.toRadians(st.yaw);
        Vector dir = new Vector(-Math.sin(rad), 0, Math.cos(rad));

        Vector vel = cart.getVelocity();
        double vy = vel.getY();
        if (tier.agua()) {
            vy = Barcos.mover(pl, st, p, dir, giro);
            rapidez = Math.abs(st.velocidad);
            if (!cart.isValid() || Barcos.volcada(pl, cart)) return;
        } else {
            // escalones, escaleras y losas: son rampas, no choques
            if (rapidez > 0.02 && st.saltoCd == 0 && cart.isOnGround()) {
                Vector delante = dir.clone().multiply(Math.signum(st.velocidad) * 0.9);
                Block frente = cart.getLocation().add(delante).getBlock();
                boolean hueco = frente.getRelative(BlockFace.UP).isPassable() && frente.getRelative(BlockFace.UP, 2).isPassable();
                if (Danos.esRampa(frente) && hueco) {
                    vy = 0.3;
                    st.saltoCd = 4;
                    st.alturaCaida = Double.NaN;
                } else if (tier.todoterreno() && hueco && esTerreno(frente)) {
                    vy = 0.36;
                    st.saltoCd = 6;
                    st.velocidad *= 0.8;
                }
            }
        }
        cart.setVelocity(dir.clone().multiply(st.velocidad).setY(vy));

        // choques: quería avanzar y casi no se ha movido
        Location ahora = cart.getLocation();
        double recorrido = ahora.getWorld() == st.ultima.getWorld()
                ? Math.hypot(ahora.getX() - st.ultima.getX(), ahora.getZ() - st.ultima.getZ()) : 0;
        if (rapidez > 0.12 && recorrido < rapidez * 0.2 && st.saltoCd == 0) {
            if (++st.atascado >= 2) {
                Danos.choque(pl, st, p, rapidez - recorrido);
                st.velocidad *= 0.15;
                st.atascado = 0;
            }
        } else st.atascado = 0;
        // caídas
        if (!tier.agua()) Danos.caida(pl, st, p, cart.isOnGround(), bloque.isLiquid() || bloque.getRelative(BlockFace.DOWN).isLiquid());
        if (!cart.isValid()) return; // siniestro por la caída
        st.ultima = ahora;
        pl.carnet.sumar(p, "bloques", recorrido);
        pl.carnet.sumar(p, "ticks", 1);
        // desgaste
        st.salud = Math.max(0, st.salud - recorrido * pl.getConfig().getDouble("averias.desgaste-por-bloque", 0.0025));

        // atropellos (solo jugadores y bichos: los barcos no atropellan a nadie fuera del agua)
        double bps = rapidez * 20;
        if (bps >= pl.getConfig().getDouble("atropellos.desde-velocidad", 4)) atropellar(st, p, dir, bps);

        // gasolina
        if (!tier.sinMotor()) {
            st.gasolina = Math.max(0, st.gasolina - recorrido * tier.consumo());
            double nivel = st.gasolina / tier.deposito();
            if (nivel < 0.15 && !st.avisoReserva && st.gasolina > 0) {
                st.avisoReserva = true;
                p.sendMessage(ChatColor.GOLD + "⛽ Vas en reserva. Busca una gasolinera" + (tier.agua() ? " náutica." : "."));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 1f, 0.6f);
            }
            if (nivel >= 0.15) st.avisoReserva = false;
        }
        if (st.ticks % 20 == 0) guardar(st);

        particulas(st, cart, tier, ahora, dir, rapidez, in.isForward() && motor);
        if (in.isJump() && st.claxonCd == 0) st.claxonCd = claxon(cart, tier);

        // parado encima de un surtidor: reposta (en el agua, el surtidor está en el fondo)
        boolean parado = rapidez < 0.005 && !in.isForward() && !in.isBackward();
        Block surtidor = parado ? pl.gasolineras.surtidorBajo(ahora, tier.agua()) : null;
        if (surtidor != null && !tier.sinMotor()) pl.gasolineras.repostar(st, p);
        else {
            pl.gasolineras.parar(cart.getUniqueId(), p);
            if (st.ticks % 4 == 0) hud(st, p);
        }
    }

    // ---------- claxon ----------

    private record Toque(long retraso, Sound sonido, float tono) {}

    /** Toca el claxon del modelo y devuelve los ticks de espera hasta poder volver a tocarlo. */
    private int claxon(ArmorStand cart, Tier tier) {
        List<Toque> toques = switch (Modelos.modelo(tier)) {
            case "tartana" -> List.of(new Toque(0, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.75f), new Toque(0, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f),
                    new Toque(7, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.7f), new Toque(7, Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f));
            case "deportivo" -> List.of(new Toque(0, Sound.BLOCK_NOTE_BLOCK_BIT, 1.19f), new Toque(0, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.5f),
                    new Toque(3, Sound.BLOCK_NOTE_BLOCK_BIT, 1.5f), new Toque(3, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.9f));
            case "furgoneta" -> List.of(new Toque(0, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.55f), new Toque(0, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f),
                    new Toque(3, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.55f),
                    new Toque(6, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.55f), new Toque(6, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f),
                    new Toque(9, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.53f));
            case "motocarro" -> List.of(new Toque(0, Sound.BLOCK_NOTE_BLOCK_COW_BELL, 1.1f), new Toque(0, Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f),
                    new Toque(4, Sound.BLOCK_NOTE_BLOCK_COW_BELL, 1.1f), new Toque(4, Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f));
            case "quad" -> List.of(new Toque(0, Sound.BLOCK_NOTE_BLOCK_BANJO, 1.7f), new Toque(0, Sound.BLOCK_NOTE_BLOCK_BIT, 1.7f),
                    new Toque(3, Sound.BLOCK_NOTE_BLOCK_BANJO, 1.7f), new Toque(3, Sound.BLOCK_NOTE_BLOCK_BIT, 1.7f));
            case "pedalo" -> List.of(new Toque(0, Sound.BLOCK_NOTE_BLOCK_BIT, 2.0f), new Toque(2, Sound.BLOCK_NOTE_BLOCK_BIT, 1.8f));
            case "motoagua" -> List.of(new Toque(0, Sound.BLOCK_NOTE_BLOCK_BIT, 1.4f), new Toque(3, Sound.BLOCK_NOTE_BLOCK_BIT, 1.4f));
            case "barco" -> List.of(new Toque(0, Sound.ITEM_GOAT_HORN_SOUND_0, 0.8f));
            case "taxiboat", "taxi" -> List.of(new Toque(0, Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f), new Toque(4, Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f));
            case "goma" -> List.of(new Toque(0, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f), new Toque(0, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f));
            case "patrullero" -> List.of(new Toque(0, Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f), new Toque(6, Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f),
                    new Toque(12, Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f), new Toque(18, Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f));
            case "hipersonica" -> List.of(new Toque(0, Sound.BLOCK_NOTE_BLOCK_BASS, 1.5f), new Toque(0, Sound.BLOCK_NOTE_BLOCK_BIT, 1.9f));
            default -> List.of(new Toque(0, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.2f), new Toque(0, Sound.BLOCK_NOTE_BLOCK_BIT, 1f),
                    new Toque(5, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.2f), new Toque(5, Sound.BLOCK_NOTE_BLOCK_BIT, 1f));
        };
        // el volumen hace de alcance; la fuerza repite el sonido para que se oiga más
        float alcance = (float) pl.getConfig().getDouble("claxon.alcance", 1.5);
        int fuerza = Math.max(1, Math.min(6, pl.getConfig().getInt("claxon.fuerza", 3)));
        long ultimo = 0;
        for (Toque t : toques) {
            ultimo = Math.max(ultimo, t.retraso());
            Runnable sonar = () -> { if (cart.isValid()) for (int i = 0; i < fuerza; i++) cart.getWorld().playSound(cart.getLocation(), t.sonido(), SoundCategory.PLAYERS, alcance, t.tono()); };
            if (t.retraso() == 0) sonar.run();
            else Bukkit.getScheduler().runTaskLater(pl, sonar, t.retraso());
        }
        return (int) ultimo + 12;
    }

    // bloques que el todoterreno puede subir de un salto
    private static boolean esTerreno(Block b) {
        Material m = b.getType();
        return Tag.DIRT.isTagged(m) || Tag.SAND.isTagged(m) || Tag.BASE_STONE_OVERWORLD.isTagged(m)
                || m == Material.GRAVEL || m == Material.SNOW_BLOCK || m == Material.SNOW || m == Material.CLAY
                || m == Material.DIRT_PATH || m == Material.SANDSTONE || m == Material.RED_SANDSTONE
                || m == Material.TERRACOTTA || m == Material.COBBLESTONE || m == Material.MOSSY_COBBLESTONE;
    }

    // ---------- HUD ----------

    private void hud(Estado st, Player p) {
        Tier tier = st.tier;
        int celdas = 10;
        int llenas = tier.sinMotor() ? 0 : (int) Math.round(celdas * st.gasolina / tier.deposito());
        StringBuilder barra = new StringBuilder();
        ChatColor color = !tier.sinMotor() && st.gasolina / tier.deposito() < 0.15 ? ChatColor.RED : ChatColor.YELLOW;
        for (int i = 0; i < celdas; i++) barra.append(i < llenas ? color + "■" : ChatColor.DARK_GRAY + "■");
        if (tier.sinMotor()) llenas = 0;
        String vel = String.format(Locale.ROOT, "%.1f", Math.abs(st.velocidad) * 20);
        String marcha = st.velocidad < -0.001 ? ChatColor.RED + " R" : "";
        ChatColor colorEstado = st.salud <= 0 ? ChatColor.DARK_RED : st.salud < 30 ? ChatColor.RED : ChatColor.GREEN;
        String gasolina = tier.sinMotor() ? ChatColor.GRAY + " · 🦆 a pedales" : ChatColor.GRAY + " · ⛽ " + barra + ChatColor.GRAY + String.format(Locale.ROOT, " %.1f/%.0f L", st.gasolina, tier.deposito());
        p.sendActionBar(ChatColor.GOLD + tier.nombre() + ChatColor.GRAY + " · " + ChatColor.WHITE + vel + " bl/s" + marcha
                + gasolina
                + ChatColor.GRAY + " · 🔧 " + colorEstado + (st.salud <= 0 ? "AVERIADO" : String.format(Locale.ROOT, "%.0f %%", st.salud)));
    }

    // ---------- atropellos ----------

    private void atropellar(Estado st, Player p, Vector dir, double bps) {
        ArmorStand cart = st.cart;
        BoundingBox zona = cart.getBoundingBox().expand(0.25, 0.4, 0.25)
                .shift(dir.clone().multiply(Math.signum(st.velocidad) * 0.5));
        for (Entity ent : cart.getWorld().getNearbyEntities(zona)) {
            if (!(ent instanceof LivingEntity vivo) || ent.equals(p) || ent instanceof ArmorStand) continue;
            if (vivo instanceof Player otro && (otro.getGameMode() == GameMode.CREATIVE || otro.getGameMode() == GameMode.SPECTATOR)) continue;
            if (cart.getPassengers().contains(ent) || vivo.isInsideVehicle()) continue;
            Integer ultimo = st.golpeados.get(vivo.getUniqueId());
            if (ultimo != null && st.ticks - ultimo < 20) continue; // a cada uno, como mucho una vez por segundo
            st.golpeados.put(vivo.getUniqueId(), st.ticks);
            double dano = (bps - 3) * pl.getConfig().getDouble("atropellos.dano-por-velocidad", 0.8);
            vivo.damage(dano);
            vivo.setVelocity(dir.clone().multiply(Math.signum(st.velocidad) * Math.min(1.6, 0.3 + bps * 0.08)).setY(0.35 + bps * 0.02));
            cart.getWorld().playSound(vivo.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1f, 0.7f);
            boolean muerto = vivo.isDead() || vivo.getHealth() <= 0;
            st.velocidad *= 0.6;
            danarCoche(cart, 2, "un atropello");
            pl.seguros.atropello(p, st, vivo, dano, muerto);
            pl.carnet.sumar(p, "atropellos", 1);
        }
    }

    // ---------- guardar en el coche ----------

    void guardar(Estado st) {
        if (!st.cart.isValid()) return;
        st.cart.getPersistentDataContainer().set(pl.kGasolina, PersistentDataType.DOUBLE, st.gasolina);
        st.cart.getPersistentDataContainer().set(pl.kEstado, PersistentDataType.DOUBLE, st.salud);
        st.cart.getPersistentDataContainer().set(pl.kYaw, PersistentDataType.FLOAT, st.yaw);
    }

    void pararTodo() {
        for (Estado st : conduciendo.values()) guardar(st);
        conduciendo.clear();
    }
}
