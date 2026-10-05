package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Builds the menu icon of a spawner from its configured {@link SpawnerHead}. */
public class SpawnerMobHeadTexture {
    /** Skull metas with a texture already applied, keyed by texture hash. Building one is the slow part. */
    private static final Map<String, SkullMeta> CUSTOM_TEXTURE_META_CACHE = new ConcurrentHashMap<>();
    private static final ItemStack DEFAULT_SPAWNER_BLOCK = new ItemStack(Material.SPAWNER);

    /** The icon of this spawner: its own head, else its base spawner's, else a spawner block. */
    public static ItemStack getSpawnerHead(SpawnerData spawner, Consumer<ItemMeta> metaModifier) {
        return getHead(spawner == null ? null : spawner.getHead(), metaModifier);
    }

    /** The icon of a mob's base spawner. */
    public static ItemStack getCustomHead(EntityType entityType, Player player, Consumer<ItemMeta> metaModifier) {
        return getCustomHead(entityType, metaModifier);
    }

    public static ItemStack getCustomHead(EntityType entityType) {
        return getCustomHead(entityType, (Consumer<ItemMeta>) null);
    }

    public static ItemStack getCustomHead(EntityType entityType, Consumer<ItemMeta> metaModifier) {
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (entityType == null || plugin == null || plugin.getSpawnerSettingsConfig() == null) {
            return getHead(null, metaModifier);
        }
        return getHead(plugin.getSpawnerSettingsConfig().getBaseHead(entityType), metaModifier);
    }

    /** The icon of an item's base spawner. */
    public static ItemStack getItemSpawnerHead(Material itemMaterial, Player player, Consumer<ItemMeta> metaModifier) {
        return getItemSpawnerHead(itemMaterial, metaModifier);
    }

    public static ItemStack getItemSpawnerHead(Material itemMaterial, Consumer<ItemMeta> metaModifier) {
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (itemMaterial == null || plugin == null || plugin.getItemSpawnerSettingsConfig() == null) {
            return getHead(null, metaModifier);
        }
        return getHead(plugin.getItemSpawnerSettingsConfig().getBaseHead(itemMaterial), metaModifier);
    }

    public static ItemStack getHead(SpawnerHead head, Consumer<ItemMeta> metaModifier) {
        if (head == null) {
            return modified(DEFAULT_SPAWNER_BLOCK.clone(), metaModifier);
        }
        if (head.hasTexture()) {
            return getCustomHeadFromTexture(head.texture(), metaModifier);
        }
        return modified(new ItemStack(head.material()), metaModifier);
    }

    private static ItemStack modified(ItemStack item, Consumer<ItemMeta> metaModifier) {
        if (metaModifier != null) {
            item.editMeta(metaModifier);
        }
        return item;
    }

    /**
     * Get a custom head with a specific texture string.
     * Optimized with caching to avoid repeated PlayerProfile and URL creation.
     *
     * @param texture The custom texture string (without URL prefix)
     * @param metaModifier Consumer to modify the ItemMeta (can be null)
     * @return The configured ItemStack
     */
    public static ItemStack getCustomHeadFromTexture(String texture, Consumer<ItemMeta> metaModifier) {
        if (texture == null || texture.trim().isEmpty()) {
            return modified(new ItemStack(Material.PLAYER_HEAD), metaModifier);
        }

        String trimmedTexture = texture.trim();
        SkullMeta baseMeta = CUSTOM_TEXTURE_META_CACHE.get(trimmedTexture);

        if (baseMeta == null) {
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                PlayerTextures textures = profile.getTextures();
                URL url = new URL("http://textures.minecraft.net/texture/" + trimmedTexture);
                textures.setSkin(url);
                profile.setTextures(textures);

                ItemStack tempHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta tempMeta = (SkullMeta) tempHead.getItemMeta();
                tempMeta.setOwnerProfile(profile);

                // Cache the base meta (clone to ensure immutability)
                baseMeta = (SkullMeta) tempMeta.clone();
                CUSTOM_TEXTURE_META_CACHE.put(trimmedTexture, baseMeta);
            } catch (Exception e) {
                e.printStackTrace();
                return modified(new ItemStack(Material.PLAYER_HEAD), metaModifier);
            }
        }

        // Create head using cached base meta
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) baseMeta.clone();

        if (metaModifier != null) {
            metaModifier.accept(meta);
        }
        head.setItemMeta(meta);
        return head;
    }

    public static void clearCache() {
        CUSTOM_TEXTURE_META_CACHE.clear();
    }

    /**
     * Pre-warms the texture cache with common mobs, so the first menu opened does not stall on
     * building player profiles. Runs asynchronously to avoid blocking plugin startup.
     */
    public static void prewarmCache() {
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null) return;

        Scheduler.runTaskAsync(() -> {
            SpawnerSettingsConfig settingsConfig = plugin.getSpawnerSettingsConfig();
            if (settingsConfig == null) return;

            EntityType[] commonTypes = {
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
                EntityType.SPIDER, EntityType.ENDERMAN, EntityType.BLAZE,
                EntityType.SLIME, EntityType.MAGMA_CUBE, EntityType.GHAST,
                EntityType.PIG, EntityType.COW, EntityType.CHICKEN,
                EntityType.SHEEP, EntityType.IRON_GOLEM, EntityType.WITHER_SKELETON,
                EntityType.ZOGLIN, EntityType.HOGLIN, EntityType.CAVE_SPIDER
            };

            for (EntityType type : commonTypes) {
                try {
                    SpawnerHead head = settingsConfig.getBaseHead(type);
                    if (head.hasTexture()) {
                        getCustomHeadFromTexture(head.texture(), null);
                    }
                } catch (Exception e) {
                    // Silently ignore errors during pre-warming
                }
            }
        });
    }
}
