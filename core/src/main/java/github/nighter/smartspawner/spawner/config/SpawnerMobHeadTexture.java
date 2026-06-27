package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.profile.PlayerProfile;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class SpawnerMobHeadTexture {
    private static final Map<EntityType, ItemStack> HEAD_CACHE = new EnumMap<>(EntityType.class);
    private static final Map<EntityType, SkullMeta> BASE_META_CACHE = new EnumMap<>(EntityType.class);
    private static final Map<Material, ItemStack> ITEM_HEAD_CACHE = new EnumMap<>(Material.class);
    private static final Map<String, SkullMeta> CUSTOM_TEXTURE_META_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ItemStack DEFAULT_SPAWNER_BLOCK = new ItemStack(Material.SPAWNER);

    private static boolean isBedrockPlayer(Player player) {
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null || plugin.getIntegrationManager() == null || 
            plugin.getIntegrationManager().getFloodgateHook() == null) {
            return false;
        }
        return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
    }

    /**
     * Optimized version that accepts a Consumer to modify the ItemMeta directly,
     * avoiding an extra getItemMeta() and setItemMeta() cycle.
     *
     * @param entityType The entity type for the head
     * @param player The player requesting the head
     * @param metaModifier Consumer to modify the ItemMeta (can be null)
     * @return The configured ItemStack
     */
    public static ItemStack getCustomHead(EntityType entityType, Player player, Consumer<ItemMeta> metaModifier) {
        if (entityType == null) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }

        if (isBedrockPlayer(player)) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }

        return getCustomHead(entityType, metaModifier);
    }

    public static ItemStack getCustomHead(EntityType entityType) {
        return getCustomHead(entityType, (Consumer<ItemMeta>) null);
    }

    /**
     * Optimized version that accepts a Consumer to modify the ItemMeta directly,
     * avoiding an extra getItemMeta() and setItemMeta() cycle.
     *
     * @param entityType The entity type for the head
     * @param metaModifier Consumer to modify the ItemMeta (can be null)
     * @return The configured ItemStack
     */
    public static ItemStack getCustomHead(EntityType entityType, Consumer<ItemMeta> metaModifier) {
        if (entityType == null) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }
        
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }
        
        SpawnerSettingsConfig settingsConfig = plugin.getSpawnerSettingsConfig();
        if (settingsConfig == null) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }
        
        // Get material from config
        Material material = settingsConfig.getMaterial(entityType);
        
        // If it's not a player head, return the vanilla head
        if (material != Material.PLAYER_HEAD) {
            ItemStack item = new ItemStack(material);
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }

        // Check if we have a custom texture
        if (!settingsConfig.hasCustomTexture(entityType)) {
            ItemStack item = new ItemStack(material);
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }

        // Check if we have cached base meta with texture already applied
        SkullMeta baseMeta = BASE_META_CACHE.get(entityType);

        if (baseMeta == null) {
            // Create and cache the base meta with texture
            try {
                String texture = settingsConfig.getCustomTexture(entityType);
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                PlayerTextures textures = profile.getTextures();
                URL url = new URL("http://textures.minecraft.net/texture/" + texture);
                textures.setSkin(url);
                profile.setTextures(textures);

                ItemStack tempHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta tempMeta = (SkullMeta) tempHead.getItemMeta();
                tempMeta.setOwnerProfile(profile);

                // Cache the base meta (clone to ensure immutability)
                baseMeta = (SkullMeta) tempMeta.clone();
                BASE_META_CACHE.put(entityType, baseMeta);
            } catch (Exception e) {
                e.printStackTrace();
                ItemStack item = new ItemStack(material);
                if (metaModifier != null) {
                    item.editMeta(metaModifier);
                }
                return item;
            }
        }

        // Create head using cached base meta
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) baseMeta.clone();

        // Apply custom modifications if provided
        if (metaModifier != null) {
            metaModifier.accept(meta);
        }

        head.setItemMeta(meta);

        // Cache the unmodified version for reuse
        if (metaModifier == null && !HEAD_CACHE.containsKey(entityType)) {
            HEAD_CACHE.put(entityType, head.clone());
        }

        return head;
    }

    /**
     * Get a custom head for an item spawner material
     * 
     * @param itemMaterial The material for the item spawner
     * @param player The player requesting the head
     * @param metaModifier Consumer to modify the ItemMeta (can be null)
     * @return The configured ItemStack
     */
    public static ItemStack getItemSpawnerHead(Material itemMaterial, Player player, Consumer<ItemMeta> metaModifier) {
        if (itemMaterial == null) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }

        if (isBedrockPlayer(player)) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }

        return getItemSpawnerHead(itemMaterial, metaModifier);
    }

    /**
     * Get a custom head for an item spawner material
     * 
     * @param itemMaterial The material for the item spawner
     * @param metaModifier Consumer to modify the ItemMeta (can be null)
     * @return The configured ItemStack
     */
    public static ItemStack getItemSpawnerHead(Material itemMaterial, Consumer<ItemMeta> metaModifier) {
        if (itemMaterial == null) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }

        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null || plugin.getItemSpawnerSettingsConfig() == null) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }

        // Get head data from item spawner config
        ItemSpawnerSettingsConfig.ItemHeadData headData = plugin.getItemSpawnerSettingsConfig().getHeadData(itemMaterial);
        Material headMaterial = headData.getMaterial();

        // For item spawners, we just use the item material as the head (no custom textures)
        ItemStack item = new ItemStack(headMaterial);
        if (metaModifier != null) {
            item.editMeta(metaModifier);
        }

        // Cache the unmodified version for reuse
        if (metaModifier == null && !ITEM_HEAD_CACHE.containsKey(itemMaterial)) {
            ITEM_HEAD_CACHE.put(itemMaterial, item.clone());
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
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
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
                ItemStack item = new ItemStack(Material.PLAYER_HEAD);
                if (metaModifier != null) {
                    item.editMeta(metaModifier);
                }
                return item;
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
        HEAD_CACHE.clear();
        BASE_META_CACHE.clear();
        ITEM_HEAD_CACHE.clear();
        CUSTOM_TEXTURE_META_CACHE.clear();
    }

    /**
     * Pre-warms the head texture cache with common entity types.
     * This should be called during plugin initialization to reduce latency when opening GUIs.
     * Runs asynchronously to avoid blocking plugin startup.
     */
    public static void prewarmCache() {
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null) return;

        // Run asynchronously to avoid blocking the main thread during startup
        Scheduler.runTaskAsync(() -> {
            SpawnerSettingsConfig settingsConfig = plugin.getSpawnerSettingsConfig();
            if (settingsConfig == null) return;

            // List of common spawner types to pre-cache
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
                    // Only pre-cache if it's a player head with custom texture
                    if (settingsConfig.getMaterial(type) == Material.PLAYER_HEAD
                            && settingsConfig.hasCustomTexture(type)) {
                        // Create base meta cache entry (this is the expensive operation)
                        getCustomHead(type, (Consumer<ItemMeta>) null);
                    }
                } catch (Exception e) {
                    // Silently ignore errors during pre-warming
                }
            }
        });
    }
}