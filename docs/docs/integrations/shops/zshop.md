---
title: zShop
---

# ![](/icons/integrations/zshop.png){.plugin-icon} zShop

**Download:** [SpigotMC](https://www.spigotmc.org/resources/126561/)

::: warning Experimental
The zShop provider exists but is not chosen by the current automatic-detection path. Treat it as experimental and select it explicitly.
:::

## Identifier

- `zShop`

## Configuration

```yaml
sell_integration:
  enabled: true
  currency: VAULT
  shop_integration:
    enabled: true
    preferred_plugin: zShop
```

Confirm the startup log reports an active zShop provider before allowing players to sell. If it does not, use [custom prices](/docs/integrations/shops/#price-source-modes) until compatibility is restored.
