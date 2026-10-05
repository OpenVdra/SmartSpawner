---
title: Spawner Types
---

# Spawner Types

SmartSpawner introduces three distinct spawner types you can give to players.

<CardGrid>

<FeatureCard icon="Box" title="Smart Spawner">

The main spawner type. Generates drops and XP from a mob without actually spawning it. Fully stackable and GUI-controlled.

- **Right-click** to open the spawner GUI
- **No mobs** are ever spawned. Zero mob lag.
- Supports stacking up to your configured limit
- Stores drops in internal paged storage

</FeatureCard>

<FeatureCard icon="Package" title="Item Spawner">

Generates raw items such as diamonds, emeralds, and netherite ingots instead of mob drops. The spinning mob preview inside is replaced with a floating item model.

- Configured in `spawner_items.yml`
- Uses the same GUI and stacking system as Smart Spawners
- Give one with `/ss give <player> <name>`

</FeatureCard>

<FeatureCard icon="Sparkles" title="Vanilla Spawner">

A normal Minecraft spawner given through the plugin command. It spawns actual mobs with standard behavior.

- No GUI or stacking
- Give one with `/ss give <player> vanilla <type>`
- Useful for hybrid setups

</FeatureCard>

</CardGrid>
