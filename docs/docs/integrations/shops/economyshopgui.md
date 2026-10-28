---
title: EconomyShopGUI
---

# ![](/icons/integrations/economyshopgui.png){.plugin-icon} EconomyShopGUI

**Download:** [SpigotMC](https://www.spigotmc.org/resources/69927/)

SmartSpawner reads EconomyShopGUI sell prices for the storage sell action.

## Identifiers

- `EconomyShopGUI`
- `EconomyShopGUI-Premium`

## Configuration

With `preferred_plugin: auto`, SmartSpawner checks EconomyShopGUI first. To select it explicitly:

```yaml
sell_integration:
  enabled: true
  currency: VAULT
  shop_integration:
    enabled: true
    preferred_plugin: EconomyShopGUI
```

Prices come from your EconomyShopGUI shop configuration. Items with no configured sell price fall back according to the [price source mode](/docs/integrations/shops/#price-source-modes).
