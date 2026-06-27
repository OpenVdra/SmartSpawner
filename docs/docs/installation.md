# Installation

## Requirements

Before installing SmartSpawner, make sure your server meets these requirements:

| Requirement | Specification |
|-------------|---------------|
| **Minecraft Version** | 1.21.5+ |
| **Server Software** | [Paper](https://papermc.io/downloads/paper), [Folia](https://papermc.io/downloads/folia), [Purpur](https://purpurmc.org/) or compatible forks |
| **Java Version** | Java 25+ |

## Download

Choose your preferred download source:

<div style="display: flex; gap: 12px; flex-wrap: wrap; margin: 1.5rem 0;">
  <a href="https://modrinth.com/plugin/smartspawner" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg" alt="Modrinth" style="height: 24px;">
    Modrinth
  </a>
  <a href="https://www.spigotmc.org/resources/120743/" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg" alt="Spigot" style="height: 24px;">
    SpigotMC
  </a>
  <a href="https://hangar.papermc.io/Nighter/SmartSpawner" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg" alt="Hangar" style="height: 24px;">
    Hangar
  </a>
</div>

## Installation Steps

### 1. Install the Plugin

1. **Stop your server** completely
2. Download the latest `.jar` file from any source above
3. Place it in your server's `plugins/` folder
4. **Start your server** (avoid using `/reload`, it can cause issues)

### 2. Verify Installation

Run the following in your server console or in-game to check that the plugin loaded:

```
/plugins
```

SmartSpawner should appear in the list with a green status.

### 3. Configuration Files

The plugin automatically creates its configuration files in `plugins/SmartSpawner/`:

| File | Description |
|------|-------------|
| `config.yml` | Main configuration: spawner behavior, economy, visuals |
| `spawners_settings.yml` | Drop tables and XP values for each mob type |
| `item_spawners_settings.yml` | Drop tables for item spawners |
| `item_prices.yml` | Custom sell prices for items |
| `spawners_data.yml` | Persistent spawner data (YAML mode) |
| `language/` | Language folder with translatable message files |
| `gui_layouts/` | GUI layout folder |
| `auraskills.yml` | AuraSkills integration settings (if AuraSkills is installed) |

## Updating

1. **Download** the new version
2. **Stop** your server
3. **Replace** the old `.jar` file with the new one
4. **Start** your server

::: info Automatic Migration
SmartSpawner automatically migrates your configuration files to the latest format on startup and creates backups of your old files.
:::

## Getting Help

If you run into issues:

1. Check your **console logs** for error messages
2. Join the **[Discord server](https://discord.gg/zrnyG4CuuT)** for community support
3. Report bugs on **[GitHub Issues](https://github.com/OpenVdra/SmartSpawner/issues)**
