package matracraft.matrains;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fichas de locomotoras y vagones leídas de config.yml. */
final class Tipos {

    record Loco(String id, String nombre, double velocidad, double aceleracion, double frenada, double traccion,
                int vagones, String combustible, boolean soloPasajeros, double precio, Sound bocina, float tono) {
        double largo() { return Modelos.largo(id); }
    }

    record Vagon(String id, String nombre, boolean pasajeros, int huecos, String acepta, double pesoVacio, double pesoLleno,
                 int asientos, boolean moderno, double precio) {
        double largo() { return Modelos.largo(id); }
        boolean cisterna() { return acepta.equals("liquidos"); }
    }

    final Map<String, Loco> locos = new LinkedHashMap<>();
    final Map<String, Vagon> vagones = new LinkedHashMap<>();

    void leer(ConfigurationSection cfg) {
        locos.clear();
        vagones.clear();
        ConfigurationSection l = cfg.getConfigurationSection("locomotoras");
        if (l != null) for (String id : l.getKeys(false)) {
            ConfigurationSection s = l.getConfigurationSection(id);
            boolean vapor = id.equals("vapor"), rapido = id.equals("ave") || id.equals("maglev");
            locos.put(id, new Loco(id, s.getString("nombre", id), s.getDouble("velocidad", 12), s.getDouble("aceleracion", 1),
                    s.getDouble("frenada", 3), s.getDouble("traccion", 6), s.getInt("vagones", 4), s.getString("combustible", "luz"),
                    s.getBoolean("solo-pasajeros", false), s.getDouble("precio", 5000),
                    vapor ? Sound.ITEM_GOAT_HORN_SOUND_1 : rapido ? Sound.ITEM_GOAT_HORN_SOUND_5 : Sound.ITEM_GOAT_HORN_SOUND_2,
                    vapor ? 1.4f : rapido ? 1.6f : 0.8f));
        }
        ConfigurationSection v = cfg.getConfigurationSection("vagones");
        if (v != null) for (String id : v.getKeys(false)) {
            ConfigurationSection s = v.getConfigurationSection(id);
            boolean pas = s.getString("clase", "mercancias").equals("pasajeros");
            double vacio = s.getDouble("peso-vacio", 1);
            vagones.put(id, new Vagon(id, s.getString("nombre", id), pas, pas ? 0 : s.getInt("huecos", 27), s.getString("acepta", "todo"),
                    vacio, s.getDouble("peso-lleno", vacio), pas ? s.getInt("asientos", 2) : 0, s.getBoolean("moderno", false),
                    s.getDouble("precio", 1000)));
        }
    }

    Loco loco(String id) { return locos.get(id); }

    Vagon vagon(String id) { return vagones.get(id); }

    /** Lo que admite cada vagón de mercancías. */
    static boolean admite(String acepta, ItemStack it) {
        if (it == null || it.getType().isAir()) return true;
        Material m = it.getType();
        return switch (acepta) {
            case "comida" -> m.isEdible();
            case "herramientas" -> m.getMaxDurability() > 0;
            case "construccion" -> m.isBlock() && m.isSolid() && !Tag.SHULKER_BOXES.isTagged(m) && m != Material.CHEST
                    && m != Material.BARREL && m != Material.TRAPPED_CHEST && m != Material.ENDER_CHEST;
            case "granel" -> esGranel(m);
            case "liquidos" -> false;
            default -> true;
        };
    }

    /** Materiales a granel de la tolva: tierra, arenas, gravas, piedras, minerales, cosechas. */
    static boolean esGranel(Material m) {
        String n = m.name();
        return n.endsWith("_ORE") || n.startsWith("RAW_") || n.endsWith("_INGOT") || n.endsWith("DIRT") || n.endsWith("SAND")
                || n.equals("GRAVEL") || n.equals("COBBLESTONE") || n.equals("COBBLED_DEEPSLATE") || n.equals("STONE") || n.equals("DEEPSLATE")
                || n.equals("NETHERRACK") || n.equals("CLAY_BALL") || n.equals("CLAY") || n.equals("COAL") || n.equals("CHARCOAL")
                || n.equals("REDSTONE") || n.equals("LAPIS_LAZULI") || n.equals("DIAMOND") || n.equals("EMERALD") || n.equals("QUARTZ")
                || n.equals("WHEAT") || n.equals("WHEAT_SEEDS") || n.equals("CARROT") || n.equals("POTATO") || n.equals("BEETROOT")
                || n.equals("SUGAR_CANE") || n.equals("COCOA_BEANS") || n.equals("FLINT") || n.equals("BONE_MEAL") || n.equals("GUNPOWDER")
                || n.equals("ANDESITE") || n.equals("DIORITE") || n.equals("GRANITE") || n.equals("TUFF") || n.equals("CALCITE")
                || n.equals("BASALT") || n.equals("BLACKSTONE") || n.equals("SOUL_SAND") || n.equals("SOUL_SOIL") || n.equals("MUD");
    }

    static String nombreAcepta(String acepta) {
        return switch (acepta) {
            case "comida" -> "solo comida";
            case "herramientas" -> "solo herramientas";
            case "construccion" -> "troncos, tablones y bloques de construcción";
            case "granel" -> "un solo material a granel (el primero que metas)";
            case "liquidos" -> "agua, lava o leche con cubos";
            default -> "de todo";
        };
    }
}
