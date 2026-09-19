package github.nighter.smartspawner.hooks.items;

import github.nighter.smartspawner.hooks.items.providers.CustomItemProvider;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomItemRegistry {

    private static final Map<String, CustomItemProvider> PROVIDERS = new ConcurrentHashMap<>();

    private CustomItemRegistry() {
    }

    public static void register(CustomItemProvider provider) {
        PROVIDERS.put(provider.getNamespace().toLowerCase(Locale.ROOT), provider);
    }

    public static void clear() {
        PROVIDERS.clear();
    }

    public static CustomItemProvider find(String namespace) {
        return PROVIDERS.get(namespace.toLowerCase(Locale.ROOT));
    }

    public static String describe(ItemStack item) {
        for (CustomItemProvider provider : PROVIDERS.values()) {
            try {
                String value = provider.describe(item);
                if (value != null) {
                    return provider.getNamespace() + ":" + value;
                }
            } catch (RuntimeException e) {
                continue;
            }
        }
        return null;
    }
}
