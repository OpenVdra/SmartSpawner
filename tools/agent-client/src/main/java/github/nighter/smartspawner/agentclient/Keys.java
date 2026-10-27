package github.nighter.smartspawner.agentclient;

import github.nighter.smartspawner.agentclient.core.AgentException;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The keys the agent may hold down, by short name. Input goes through the game's own key state, never
 * the operating system, so a key held for thirty ticks stays held for thirty ticks.
 */
final class Keys {
    private Keys() { }

    static Map<String, KeyMapping> all(Minecraft minecraft) {
        Map<String, KeyMapping> keys = new LinkedHashMap<>();
        keys.put("forward", minecraft.options.keyUp);
        keys.put("back", minecraft.options.keyDown);
        keys.put("left", minecraft.options.keyLeft);
        keys.put("right", minecraft.options.keyRight);
        keys.put("jump", minecraft.options.keyJump);
        keys.put("sneak", minecraft.options.keyShift);
        keys.put("sprint", minecraft.options.keySprint);
        keys.put("use", minecraft.options.keyUse);
        keys.put("attack", minecraft.options.keyAttack);
        keys.put("drop", minecraft.options.keyDrop);
        keys.put("inventory", minecraft.options.keyInventory);
        keys.put("swapHands", minecraft.options.keySwapOffhand);
        keys.put("pickItem", minecraft.options.keyPickItem);
        keys.put("playerList", minecraft.options.keyPlayerList);
        return keys;
    }

    static List<String> names(Minecraft minecraft) {
        return List.copyOf(all(minecraft).keySet());
    }

    static KeyMapping of(Minecraft minecraft, String name) {
        KeyMapping key = all(minecraft).get(name);
        if (key == null) {
            throw new AgentException("no key called '" + name + "'; the ones the agent holds are "
                    + String.join(", ", names(minecraft)));
        }
        return key;
    }
}
