---
title: ShopGUI+
---

# ![](/icons/integrations/shopguiplus.png){.plugin-icon} ShopGUI+

**Download:** [SpigotMC](https://www.spigotmc.org/resources/6515/)

SmartSpawner reads ShopGUI+ sell prices and registers its spawner compatibility listener when the plugin is present.

## Identifier

- `ShopGUIPlus`

## Configuration

```yaml
sell_integration:
  enabled: true
  currency: VAULT
  shop_integration:
    enabled: true
    preferred_plugin: ShopGUIPlus
```

Sell values are taken from your ShopGUI+ shop definitions. Items missing a ShopGUI+ price fall back according to the [price source mode](/docs/integrations/shops/#price-source-modes).
