package github.nighter.smartspawner.hooks;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationManagerTest {

    @Test
    @DisplayName("checkPlugin safely catches NoClassDefFoundError and returns false")
    void checkPluginSwallowsNoClassDefFoundError() {
        IntegrationManager manager = new IntegrationManager(null);

        boolean result = assertDoesNotThrow(() ->
                manager.checkPlugin("WorldGuard", () -> {
                    throw new NoClassDefFoundError("com/sk89q/worldedit/extent/Extent");
                }, true)
        );

        assertFalse(result, "Integration check must return false when a class is missing");
    }

    @Test
    @DisplayName("checkPlugin safely catches generic LinkageError and returns false")
    void checkPluginSwallowsGenericLinkageError() {
        IntegrationManager manager = new IntegrationManager(null);

        boolean result = assertDoesNotThrow(() ->
                manager.checkPlugin("TestPlugin", () -> {
                    throw new LinkageError("Incompatible class change");
                }, true)
        );

        assertFalse(result, "Integration check must return false when linkage fails");
    }

    @Test
    @DisplayName("checkPlugin returns true when check succeeds")
    void checkPluginReturnsTrueOnSuccess() {
        IntegrationManager manager = new IntegrationManager(null);

        boolean result = manager.checkPlugin("TestPlugin", () -> true, true);

        assertTrue(result, "Integration check must return true when check succeeds");
    }

    @Test
    @DisplayName("paper-plugin.yml declares WorldEdit and FastAsyncWorldEdit with join-classpath: true")
    void paperPluginYmlDeclaresWorldEditClasspath() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("paper-plugin.yml");
        assertNotNull(stream, "paper-plugin.yml must exist on classpath");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        assertTrue(config.getBoolean("dependencies.server.WorldEdit.join-classpath"),
                "WorldEdit must have join-classpath: true in paper-plugin.yml");
        assertTrue(config.getBoolean("dependencies.server.FastAsyncWorldEdit.join-classpath"),
                "FastAsyncWorldEdit must have join-classpath: true in paper-plugin.yml");
        assertTrue(config.getBoolean("dependencies.server.WorldGuard.join-classpath"),
                "WorldGuard must have join-classpath: true in paper-plugin.yml");
    }

    @Test
    @DisplayName("plugin.yml declares WorldEdit and FastAsyncWorldEdit in softdepend")
    void pluginYmlDeclaresWorldEditSoftDepend() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream, "plugin.yml must exist on classpath");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        List<String> softdepend = config.getStringList("softdepend");
        assertTrue(softdepend.contains("WorldEdit"),
                "WorldEdit must be in plugin.yml softdepend list");
        assertTrue(softdepend.contains("FastAsyncWorldEdit"),
                "FastAsyncWorldEdit must be in plugin.yml softdepend list");
        assertTrue(softdepend.contains("WorldGuard"),
                "WorldGuard must be in plugin.yml softdepend list");
    }
}
