package github.nighter.smartspawner.commands.hologram;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.format.TextFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class SpawnerHologram {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final AtomicReference<TextDisplay> textDisplay = new AtomicReference<>(null);
    private final Location spawnerLocation;
    private int stackSize;
    private EntityType entityType;
    private Material spawnedItemMaterial;
    private String displayName;
    private long currentExp;
    private long maxExp;
    private int currentItems;
    private int maxSlots;
    private static final String HOLOGRAM_IDENTIFIER = "SmartSpawner-Holo";
    private final String uniqueIdentifier;

    private static final Vector3f SCALE = new Vector3f(1.0f, 1.0f, 1.0f);
    private static final Vector3f TRANSLATION = new Vector3f(0.0f, 0.0f, 0.0f);
    private static final AxisAngle4f ROTATION = new AxisAngle4f(0, 0, 0, 0);

    // Cached color-translated template (static part; recomputed after reload)
    private Component cachedProcessedTemplate = null;

    // Cached small caps of the display name (recomputed only when the name changes)
    private String cachedEntityName = null;
    private String cachedEntitySmallCaps = null;

    public SpawnerHologram(Location location) {
        this.plugin = SmartSpawner.getInstance();
        this.spawnerLocation = location;
        this.languageManager = plugin.getLanguageManager();
        this.uniqueIdentifier = generateUniqueIdentifier(location);
    }

    private String generateUniqueIdentifier(Location location) {
        return HOLOGRAM_IDENTIFIER + "-" +
                location.getWorld().getName() + "-" +
                location.getBlockX() + "-" +
                location.getBlockY() + "-" +
                location.getBlockZ();
    }

    public void createHologram() {
        if (spawnerLocation == null || spawnerLocation.getWorld() == null) return;

        // Clean up any existing hologram for this spawner first
        cleanupExistingHologram();

        double offsetX = plugin.getConfig().getDouble("hologram.offset_x", 0.5);
        double offsetY = plugin.getConfig().getDouble("hologram.offset_y", 0.5);
        double offsetZ = plugin.getConfig().getDouble("hologram.offset_z", 0.5);

        Location holoLoc = spawnerLocation.clone().add(offsetX, offsetY, offsetZ);

        // Use the location scheduler to spawn the entity in the correct region
        Scheduler.runLocationTask(holoLoc, () -> {
            try {
                TextDisplay display = spawnerLocation.getWorld().spawn(holoLoc, TextDisplay.class, td -> {
                    td.setBillboard(Display.Billboard.CENTER);
                    // Get alignment from config with CENTER as default
                    String alignmentStr = plugin.getConfig().getString("hologram.alignment", "CENTER");
                    TextDisplay.TextAlignment alignment;
                    try {
                        alignment = TextDisplay.TextAlignment.valueOf(alignmentStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        alignment = TextDisplay.TextAlignment.CENTER;
                        plugin.getLogger().warning("Invalid hologram alignment in config: " + alignmentStr + ". Using CENTER as default.");
                    }
                    td.setAlignment(alignment);
                    td.setViewRange(16.0f);
                    td.setShadowed(plugin.getConfig().getBoolean("hologram.shadowed_text", true));
                    td.setDefaultBackground(false);
                    td.setTransformation(new Transformation(TRANSLATION, ROTATION, SCALE, ROTATION));
                    td.setSeeThrough(plugin.getConfig().getBoolean("hologram.see_through", false));
                    // Set background transparency based on config
                    boolean transparentBg = plugin.getConfig().getBoolean("hologram.transparent_background", false);
                    if (transparentBg) {
                        td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    }
                    // Add custom name for identification
                    td.setCustomName(uniqueIdentifier);
                    td.setCustomNameVisible(false);
                    // Set persistent to false to prevent hologram from being saved and potentially getting stuck
                    td.setPersistent(false);
                });

                textDisplay.set(display);
                updateText();
            } catch (Exception e) {
                plugin.getLogger().severe("Error creating hologram: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Template & text helpers
    // -------------------------------------------------------------------------

    /** The placeholders a hologram line can use, in the order {@link #computeText()} fills them. */
    private static final String[] PLACEHOLDERS = {
            "entity", "entity_small", "stack_size", "current_exp", "max_exp", "used_slots", "max_slots",
            "percent_storage_decimal", "percent_storage_rounded", "percent_exp_decimal", "percent_exp_rounded"
    };
    /** A placeholder's slot in the parsed template: private-use characters around its index. */
    private static final Pattern SLOT = Pattern.compile("\uE000(\\d+)\uE001");

    /**
     * Returns the hologram template, parsed once with every placeholder left as a slot. Updates then
     * only drop values into the slots, as plain text, instead of parsing MiniMessage again. Cached
     * until {@link #invalidateTemplateCache()} is called.
     */
    private Component getProcessedTemplate() {
        if (cachedProcessedTemplate == null) {
            String template = languageManager.getHologramText().replace("{ᴇɴᴛɪᴛʏ}", "{entity_small}");
            for (int i = 0; i < PLACEHOLDERS.length; i++) {
                template = template.replace("{" + PLACEHOLDERS[i] + "}", "\uE000" + i + "\uE001");
            }
            cachedProcessedTemplate = TextFormatter.parse(template, Map.of());
        }
        return cachedProcessedTemplate;
    }

    /** Call this after a language/config reload so the next update re-fetches the template. */
    public void invalidateTemplateCache() {
        cachedProcessedTemplate = null;
    }

    /** Builds the final display text from cached data. Must be called on the owning region thread. */
    private Component computeText() {
        // Refresh the small caps name only when the display name changes
        if (cachedEntityName == null || !cachedEntityName.equals(displayName)) {
            cachedEntityName = displayName != null ? displayName : languageManager.getFormattedMobName(entityType);
            cachedEntitySmallCaps = languageManager.getSmallCaps(cachedEntityName);
        }

        double pctStorage = maxSlots > 0 ? (double) currentItems / maxSlots * 100 : 0;
        double pctExp = maxExp > 0 ? (double) currentExp / maxExp * 100 : 0;

        String[] values = {
                cachedEntityName,
                cachedEntitySmallCaps,
                String.valueOf(stackSize),
                languageManager.formatNumber(currentExp),
                languageManager.formatNumber(maxExp),
                languageManager.formatNumber(currentItems),
                languageManager.formatNumber(maxSlots),
                formatOneDecimal(pctStorage),
                String.valueOf((int) Math.round(pctStorage)),
                formatOneDecimal(pctExp),
                String.valueOf((int) Math.round(pctExp))
        };
        // A mob name may carry legacy colours; legacyValue keeps those and never parses anything else.
        return getProcessedTemplate().replaceText(TextReplacementConfig.builder()
                .match(SLOT)
                .replacement((match, builder) -> TextFormatter.legacyValue(values[Integer.parseInt(match.group(1))]))
                .build());
    }

    /** Faster substitute for {@code String.format("%.1f", value)}. */
    private static String formatOneDecimal(double value) {
        long scaled = Math.round(value * 10);
        return (scaled / 10) + "." + (scaled % 10);
    }

    // -------------------------------------------------------------------------
    // Public update API
    // -------------------------------------------------------------------------

    public void updateText() {
        TextDisplay display = textDisplay.get();
        if (display == null || entityType == null) return;

        // Compute the text on the calling (region) thread – avoids doing string work
        // inside the entity-thread lambda and keeps the lambda allocation tiny.
        final Component finalText = computeText();

        Scheduler.runEntityTask(display, () -> {
            if (display.isValid()) {
                display.text(finalText);
            }
        });
    }

    public void updateData(int stackSize, EntityType entityType, Material spawnedItemMaterial, String displayName,
                           long currentExp, long maxExp, int currentItems, int maxSlots) {
        TextDisplay display = textDisplay.get();

        // Skip entirely when nothing has changed and the hologram already exists.
        if (display != null
                && this.stackSize == stackSize
                && this.entityType == entityType
                && this.spawnedItemMaterial == spawnedItemMaterial
                && java.util.Objects.equals(this.displayName, displayName)
                && this.currentExp == currentExp
                && this.maxExp == maxExp
                && this.currentItems == currentItems
                && this.maxSlots == maxSlots) {
            return;
        }

        this.stackSize = stackSize;
        this.entityType = entityType;
        this.spawnedItemMaterial = spawnedItemMaterial;
        this.displayName = displayName;
        this.currentExp = currentExp;
        this.maxExp = maxExp;
        this.currentItems = currentItems;
        this.maxSlots = maxSlots;

        if (display == null) {
            createHologram();
        } else {
            // Pre-compute text here (region thread) so the entity-thread lambda
            // only needs to call display.setText() – no extra task dispatch.
            final Component finalText = computeText();
            Scheduler.runEntityTask(display, () -> {
                if (!display.isValid()) {
                    textDisplay.set(null);
                    createHologram();
                } else {
                    display.text(finalText);
                }
            });
        }
    }

    public void remove() {
        TextDisplay display = textDisplay.get();
        if (display != null) {
            // Run on the entity's thread to ensure safe removal
            Scheduler.runEntityTask(display, () -> {
                if (display.isValid()) {
                    display.remove();
                }
            });
            textDisplay.set(null);
        }
        // Also clean up any stuck holograms
        cleanupExistingHologram();
    }

    public void cleanupExistingHologram() {
        if (spawnerLocation == null || spawnerLocation.getWorld() == null) return;

        // First, check if our tracked hologram is still valid
        TextDisplay display = textDisplay.get();
        if (display != null) {
            // Always remove the tracked display, even if it appears invalid
            Scheduler.runEntityTask(display, () -> {
                if (display.isValid()) {
                    display.remove();
                }
            });
            textDisplay.set(null);
        }

        // Use async task to avoid blocking
        Scheduler.runLocationTask(spawnerLocation, () -> {
            // Define a tighter search radius just to catch any potentially duplicated holograms
            // with the same identifier (which shouldn't happen but being safe)
            double searchRadius = 2.0;

            // Look for any entity with our specific unique identifier
            spawnerLocation.getWorld().getNearbyEntities(spawnerLocation, searchRadius, searchRadius, searchRadius)
                    .stream()
                    .filter(entity -> entity instanceof TextDisplay && entity.getCustomName() != null)
                    .filter(entity -> entity.getCustomName().equals(uniqueIdentifier))
                    .forEach(entity -> {
                        Scheduler.runEntityTask(entity, entity::remove);
                    });
        });
    }
}
