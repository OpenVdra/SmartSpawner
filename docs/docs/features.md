# Features ✨

Here is an overview of everything **SmartSpawner** brings to your Minecraft server.

## 🎮 Spawner Types

SmartSpawner introduces three distinct spawner types you can give to players.

<CardGrid>

<FeatureCard icon="🧟" title="Smart Spawner">

The main spawner type. Generates drops and XP from a mob without actually spawning it. Fully stackable and GUI-controlled.

- **Right-click** to open the spawner GUI
- **No mobs** are ever spawned. Zero entity lag.
- Supports stacking up to your configured limit
- Stores drops in internal paged storage

</FeatureCard>

<FeatureCard icon="💎" title="Item Spawner">

Generates raw items (e.g. diamonds, emeralds, netherite ingots) instead of mob drops. The spinning entity inside is replaced with a floating item model.

- Configured in `item_spawners_settings.yml`
- Same GUI and stacking system as Smart Spawners
- Use `/ss give item_spawner <player> <MATERIAL>`

</FeatureCard>

<FeatureCard icon="🌿" title="Vanilla Spawner">

A normal Minecraft spawner given via the plugin command. Spawns actual mobs with standard behavior.

- No GUI, no stacking
- Use `/ss give vanilla_spawner <player> <type>`
- Useful for hybrid setups

</FeatureCard>

</CardGrid>

---

## 📦 Stacking System

Reduce server clutter with **seamless spawner stacking**. Multiple spawners can be combined into a single block.

<CardGrid>

<FeatureCard icon="🖱️" title="Stack by Hand">
Right-click a placed spawner while holding a spawner of the same type to add one to the stack. Shift + Right-click to bulk-stack all of them at once.
<img src="https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExcnp1YjBpa2Iwbjh4OGhwdDNxamtpd2hhZDd2bzAwZGxlNDJ2MjIwdyZlcD12MV9pbnRlcm5naWZfYnlfaWQmY3Q9Zw/cIHKpupJheWzhAvBPz/giphy.gif" alt="Stacking a Spawner">
</FeatureCard>

<FeatureCard icon="🗂️" title="Stacker GUI">
Open the dedicated stacker GUI from the main spawner interface to precisely control how many spawners to add or remove.
<img src="https://cdn.modrinth.com/data/cached_images/487f0ba815827dab3ab0a8978023d44ac379ffc6.png" alt="Stacker GUI">
</FeatureCard>

<FeatureCard icon="📈" title="Proportional Scaling">
Drop rates and XP scale proportionally with stack size. A stack of 100 spawners generates 100× the drops of a single spawner.
</FeatureCard>

</CardGrid>

---

## 🖥️ GUI System

<CardGrid>

<FeatureCard icon="🏠" title="Main Spawner GUI">
View spawner stats, configure range and delay, claim XP, and access storage or the stacker from a single interface.
<img src="https://cdn.modrinth.com/data/cached_images/e04521147d7feb847e42fb560db80070ade7c9ae.png" alt="Main GUI">
</FeatureCard>

<FeatureCard icon="📬" title="Storage GUI">
Browse and manage items stored by the spawner across multiple pages. Sort items, filter specific drops, sell everything at once, or take items directly.
<img src="https://cdn.modrinth.com/data/cached_images/ca850c8bda2b9adf89dfeb073ca5b81f437fa7b7.png" alt="Storage GUI">
</FeatureCard>

<FeatureCard icon="🎨" title="Custom GUI Layouts">
Choose from built-in layouts (`default`, `DonutSMP`, `DonutSMP_v2`) or create your own in `gui_layouts/`. Fully configurable button slots, materials, custom heads, sounds, and cooldowns.
</FeatureCard>

</CardGrid>

---

## ⛏️ Mineable Spawners

Players can break and collect spawners using the correct tools and enchantments. Every aspect is configurable:

```yaml
spawner_break:
  enabled: true
  direct_to_inventory: false
  required_tools:
    - IRON_PICKAXE
    - GOLDEN_PICKAXE
    - DIAMOND_PICKAXE
    - NETHERITE_PICKAXE
  durability_loss: 1
  sneak_break: true
  silk_touch:
    required: true
    level: 1
```

- **`sneak_break`**: Sneak while breaking to remove up to 64 spawners from a stack at once
- **`silk_touch.required`**: Require Silk Touch to collect the spawner item
- **`drop_chance`**: Optional per-mob chance that a spawner item drops when broken (configured per entity in `spawners_settings.yml`)

---

## 🛒 Shop Integration

Sell items directly from the spawner storage GUI with full economy support.

<CardGrid>

<FeatureCard icon="💰" title="Economy Backends">

- **Vault**: Universal economy API
- **ExcellentEconomy**: Multi-currency support

</FeatureCard>

<FeatureCard icon="🏪" title="Shop Plugins">

- EconomyShopGUI / EconomyShopGUI Premium
- ShopGUI+
- zShop

Prices can also be defined manually in `item_prices.yml`.

</FeatureCard>

</CardGrid>

---

## 🌐 Plugin Compatibility

<CardGrid>

<FeatureCard icon="🛡️" title="Protections">

- WorldGuard
- GriefPrevention
- Lands
- Towny Advanced
- SimpleClaimSystem
- MinePlots

</FeatureCard>

<FeatureCard icon="🌍" title="World Management">

- Multiverse-Core
- Multiworld
- SuperiorSkyblock2
- BentoBox *(requires setup, see [BentoBox docs](https://docs.bentobox.world))*
- IridiumSkyblock

</FeatureCard>

<FeatureCard icon="⚔️" title="RPG & Mobs">

- **AuraSkills**: XP from spawners counts toward skills
- **MythicMobs**: Custom mob drop tables

</FeatureCard>

<FeatureCard icon="📱" title="Bedrock Support">

Floodgate/Geyser players see a mobile-friendly form UI instead of chest GUIs when `bedrock_support.enable_formui` is `true`.

</FeatureCard>

</CardGrid>

---

## ✨ Visual Effects

<CardGrid>

<FeatureCard icon="💬" title="Holograms">
Floating text displays above spawners showing their type and stack size. Toggle with `/ss hologram`. Fully configurable offset, alignment, and shadow.
</FeatureCard>

<FeatureCard icon="🎆" title="Particles">
Optional particle effects when spawners are stacked, activated, or generate loot. Each effect can be toggled independently.
</FeatureCard>

<FeatureCard icon="🔍" title="Nearby Scanner">
Use `/ss near [radius]` to scan and highlight all nearby spawners through walls using glowing BlockDisplay outlines, visible only to the player who ran the command.
</FeatureCard>

</CardGrid>

---

## 🗄️ Database Support

Choose your storage backend based on your server setup:

| Mode | Use Case |
|------|----------|
| `YAML` | Single server, simple setup |
| `SQLite` | Single server, better performance than YAML |
| `MySQL` | Multi-server or high-traffic environments |

Cross-server spawner listing is available in `MYSQL` mode via `/ss list`.

---

## 📋 Action Logging

Every important spawner action can be logged to rotating log files (human-readable or JSON). Configurable per-event with console output option.
