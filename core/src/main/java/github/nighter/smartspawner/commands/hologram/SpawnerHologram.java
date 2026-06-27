package github.nighter.smartspawner.commands.hologram;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.format.ColorUtil;
import github.nighter.smartspawner.language.LanguageManager;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicReference;

public class SpawnerHologram {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final AtomicReference<TextDisplay> textDisplay = new AtomicReference<>(null);
    private final Location spawnerLocation;
    private int stackSize;
    private EntityType entityType;
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
    private String cachedProcessedTemplate = null;

    // Cached entity display names (recomputed only when entityType changes)
    private EntityType cachedEntityType = null;
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

    /** Returns the hologram template with colour codes already translated.
     *  Result is cached until {@link #invalidateTemplateCache()} is called. */
    private String getProcessedTemplate() {
        if (cachedProcessedTemplate == null) {
            cachedProcessedTemplate = ColorUtil.translateHexColorCodes(languageManager.getHologramText());
        }
        return cachedProcessedTemplate;
    }

    /** Call this after a language/config reload so the next update re-fetches the template. */
    public void invalidateTemplateCache() {
        cachedProcessedTemplate = null;
    }

    /** Builds the final display string from cached data. Must be called on the owning region thread. */
    private String computeText() {
        // Refresh entity name cache only when the entity type changes
        if (cachedEntityType != entityType) {
            cachedEntityType = entityType;
            cachedEntityName = languageManager.getFormattedMobName(entityType);
            cachedEntitySmallCaps = languageManager.getSmallCaps(cachedEntityName);
        }

        double pctStorage = maxSlots > 0 ? (double) currentItems / maxSlots * 100 : 0;
        double pctExp = maxExp > 0 ? (double) currentExp / maxExp * 100 : 0;

        return getProcessedTemplate()
                .replace("{entity}", cachedEntityName)
                .replace("{ᴇɴᴛɪᴛʏ}", cachedEntitySmallCaps)
                .replace("{stack_size}", String.valueOf(stackSize))
                .replace("{current_exp}", languageManager.formatNumber(currentExp))
                .replace("{max_exp}", languageManager.formatNumber(maxExp))
                .replace("{used_slots}", languageManager.formatNumber(currentItems))
                .replace("{max_slots}", languageManager.formatNumber(maxSlots))
                .replace("{percent_storage_decimal}", formatOneDecimal(pctStorage))
                .replace("{percent_storage_rounded}", String.valueOf((int) Math.round(pctStorage)))
                .replace("{percent_exp_decimal}", formatOneDecimal(pctExp))
                .replace("{percent_exp_rounded}", String.valueOf((int) Math.round(pctExp)));
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
        final String finalText = computeText();

        Scheduler.runEntityTask(display, () -> {
            if (display.isValid()) {
                display.setText(finalText);
            }
        });
    }

    public void updateData(int stackSize, EntityType entityType, long currentExp, long maxExp, int currentItems, int maxSlots) {
        TextDisplay display = textDisplay.get();

        // Skip entirely when nothing has changed and the hologram already exists.
        if (display != null
                && this.stackSize == stackSize
                && this.entityType == entityType
                && this.currentExp == currentExp
                && this.maxExp == maxExp
                && this.currentItems == currentItems
                && this.maxSlots == maxSlots) {
            return;
        }

        this.stackSize = stackSize;
        this.entityType = entityType;
        this.currentExp = currentExp;
        this.maxExp = maxExp;
        this.currentItems = currentItems;
        this.maxSlots = maxSlots;

        if (display == null) {
            createHologram();
        } else {
            // Pre-compute text here (region thread) so the entity-thread lambda
            // only needs to call display.setText() – no extra task dispatch.
            final String finalText = computeText();
            Scheduler.runEntityTask(display, () -> {
                if (!display.isValid()) {
                    textDisplay.set(null);
                    createHologram();
                } else {
                    display.setText(finalText);
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
